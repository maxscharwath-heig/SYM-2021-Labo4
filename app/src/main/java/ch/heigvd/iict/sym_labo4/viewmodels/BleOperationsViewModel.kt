package ch.heigvd.iict.sym_labo4.viewmodels

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.data.Data
import no.nordicsemi.android.ble.observer.ConnectionObserver
import java.util.*

/**
 * Project: Labo4
 * Created by fabien.dutoit on 11.05.2019
 * Updated by fabien.dutoit on 18.10.2021
 * (C) 2019 - HEIG-VD, IICT
 */
class BleOperationsViewModel(application: Application) : AndroidViewModel(application) {

    private var ble = SYMBleManager(application.applicationContext)
    private var mConnection: BluetoothGatt? = null

    //UUID of the services and characteristics
    private val TIME_SERVICE   = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb")
    private val CUSTOM_SERVICE = UUID.fromString("3c0a1000-281d-4b48-b2a7-f15579a1c38f")
    private val INT_CHARACTERISTIC = UUID.fromString("3c0a1001-281d-4b48-b2a7-f15579a1c38f")
    private val TEMPERATURE_CHARACTERISTIC = UUID.fromString("3c0a1002-281d-4b48-b2a7-f15579a1c38f")
    private val BUTTON_CHARACTERISTIC = UUID.fromString("3c0a1003-281d-4b48-b2a7-f15579a1c38f")
    private val CURRENT_TIME_CHARACTERISTIC = UUID.fromString("00002A2B-0000-1000-8000-00805f9b34fb")

    //live data - observer
    val isConnected = MutableLiveData(false)
    val temperature = MutableLiveData(0.0)
    val currentTime = MutableLiveData(Calendar.getInstance())
    val buttonClicked = MutableLiveData(0)

    //Services and Characteristics of the SYM Pixl
    private var timeService: BluetoothGattService? = null
    private var symService: BluetoothGattService? = null
    private var currentTimeChar: BluetoothGattCharacteristic? = null
    private var integerChar: BluetoothGattCharacteristic? = null
    private var temperatureChar: BluetoothGattCharacteristic? = null
    private var buttonClickChar: BluetoothGattCharacteristic? = null

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "onCleared")
        ble.disconnect()
    }

    fun connect(device: BluetoothDevice) {
        Log.d(TAG, "User request connection to: $device")
        if (!isConnected.value!!) {
            ble.connect(device)
                    .retry(1, 100)
                    .useAutoConnect(false)
                    .enqueue()
        }
    }

    fun disconnect() {
        Log.d(TAG, "User request disconnection")
        ble.disconnect()
        mConnection?.disconnect()
    }

    fun readTemperature(): Boolean {
        return if (!isConnected.value!! || temperatureChar == null)
            false
        else
            ble.readTemperature()
    }

    fun sendInteger(value: Int):Boolean{
        return if (!isConnected.value!! || integerChar == null)
            false
        else
            ble.sendInteger(value)
    }

    fun updateDate(calendar: Calendar): Boolean {
        return if (!isConnected.value!! || currentTimeChar == null)
            false
        else
            ble.updateDate(calendar)
    }

    private val bleConnectionObserver: ConnectionObserver = object : ConnectionObserver {
        override fun onDeviceConnecting(device: BluetoothDevice) {
            Log.d(TAG, "onDeviceConnecting")
            isConnected.value = false
        }

        override fun onDeviceConnected(device: BluetoothDevice) {
            Log.d(TAG, "onDeviceConnected")
            isConnected.value = true
        }

        override fun onDeviceDisconnecting(device: BluetoothDevice) {
            Log.d(TAG, "onDeviceDisconnecting")
            isConnected.value = false
        }

        override fun onDeviceReady(device: BluetoothDevice) {
            Log.d(TAG, "onDeviceReady")
        }

        override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
            Log.d(TAG, "onDeviceFailedToConnect")
        }

        override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
            if(reason == ConnectionObserver.REASON_NOT_SUPPORTED) {
                Log.d(TAG, "onDeviceDisconnected - not supported")
                Toast.makeText(getApplication(), "Device not supported - implement method isRequiredServiceSupported()", Toast.LENGTH_LONG).show()
            }
            else
                Log.d(TAG, "onDeviceDisconnected")
            isConnected.value = false
        }

    }

    private inner class SYMBleManager(applicationContext: Context) : BleManager(applicationContext) {
        /**
         * BluetoothGatt callbacks object.
         */
        private var mGattCallback: BleManagerGattCallback? = null

        public override fun getGattCallback(): BleManagerGattCallback {
            //we initiate the mGattCallback on first call, singleton
            if (mGattCallback == null) {
                mGattCallback = object : BleManagerGattCallback() {

                    public override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
                        mConnection = gatt //trick to force disconnection
                        //check if all required services and characteristics are available
                        timeService = gatt.getService(TIME_SERVICE)
                        symService = gatt.getService(CUSTOM_SERVICE)
                        if (timeService != null && symService != null) {
                            integerChar = symService!!.getCharacteristic(INT_CHARACTERISTIC)
                            temperatureChar = symService!!.getCharacteristic(TEMPERATURE_CHARACTERISTIC)
                            buttonClickChar = symService!!.getCharacteristic(BUTTON_CHARACTERISTIC)
                            currentTimeChar = timeService!!.getCharacteristic(CURRENT_TIME_CHARACTERISTIC)
                            if (integerChar != null && temperatureChar != null && buttonClickChar != null && currentTimeChar != null) {
                                Log.d(TAG, "isRequiredServiceSupported - true")
                                return true
                            }
                        }
                        Log.d(TAG, "isRequiredServiceSupported - false")
                        return false
                    }

                    override fun initialize() {
                        Log.d(TAG, "initialize")

                        setNotificationCallback(buttonClickChar).with{_: BluetoothDevice, data: Data ->
                            val buttonClick = data.getIntValue(Data.FORMAT_UINT8, 0)
                            buttonClicked.postValue(buttonClick)
                            Log.d(TAG, "buttonClickChar - data: $buttonClick")
                        }
                        setNotificationCallback(currentTimeChar).with{_: BluetoothDevice, data: Data ->
                            val year = data.getIntValue(Data.FORMAT_UINT16, 0)!!
                            val month = data.getIntValue(Data.FORMAT_UINT8, 2)!!
                            val day = data.getIntValue(Data.FORMAT_UINT8, 3)!!
                            val hour = data.getIntValue(Data.FORMAT_UINT8, 4)!!
                            val minute = data.getIntValue(Data.FORMAT_UINT8, 5)!!
                            val second = data.getIntValue(Data.FORMAT_UINT8, 6)!!
                            val date = Calendar.getInstance()
                            date.set(year, month, day, hour, minute, second)
                            currentTime.postValue(date)
                            Log.d(TAG, "currentTimeChar - date: $date")
                        }

                        enableNotifications(buttonClickChar!!).enqueue()
                        enableNotifications(currentTimeChar!!).enqueue()
                    }

                    override fun onServicesInvalidated() {
                        //we reset services and characteristics
                        timeService = null
                        currentTimeChar = null
                        symService = null
                        integerChar = null
                        temperatureChar = null
                        buttonClickChar = null
                    }
                }
            }
            return mGattCallback!!
        }

        fun readTemperature(): Boolean {
            if(temperatureChar == null) {
                return false
            }
            readCharacteristic(temperatureChar!!).with{_: BluetoothDevice, data: Data ->
                Log.d(TAG, "temperatureChar - data: $data")
                val tempData = data.getIntValue(Data.FORMAT_UINT16, 0)?.div(10.0)
                Log.d(TAG, "temperatureChar - tempData: $tempData")
                temperature.postValue(tempData)
            }.enqueue()
            return true
        }

        fun sendInteger(value: Int):Boolean{
            if(integerChar == null) {
                return false
            }
            writeCharacteristic(integerChar!!, byteArrayOf(value.toByte()), WRITE_TYPE_DEFAULT).enqueue()
            return true
        }

        fun updateDate(calendar: Calendar):Boolean {
            if(currentTimeChar == null){
                return false
            }
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)+1
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            val hours = calendar.get(Calendar.HOUR_OF_DAY)
            val minutes = calendar.get(Calendar.MINUTE)
            val seconds = calendar.get(Calendar.SECOND)

            val bytes = ByteArray(10)
            bytes[0] = (year and 0xFF).toByte()
            bytes[1] = (year shr 8 and 0xFF).toByte()
            bytes[2] = month.toByte()
            bytes[3] = day.toByte()
            bytes[4] = hours.toByte()
            bytes[5] = minutes.toByte()
            bytes[6] = seconds.toByte()
            writeCharacteristic(currentTimeChar!!, bytes, WRITE_TYPE_DEFAULT).enqueue()
            return true
        }
    }

    companion object {
        private val TAG = BleOperationsViewModel::class.java.simpleName
    }

    init {
        ble.setConnectionObserver(bleConnectionObserver)
    }

}