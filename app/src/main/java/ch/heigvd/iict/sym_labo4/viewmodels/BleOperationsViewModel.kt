package ch.heigvd.iict.sym_labo4.viewmodels

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
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

data class DateTime(val year: Int, val month: Int, val day: Int, val hour: Int, val minute: Int, val second: Int){
    companion object{
        fun now():DateTime{
            val calendar = Calendar.getInstance()
            return DateTime(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                calendar.get(Calendar.SECOND)
            )
        }
    }
}

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

    @SuppressLint("MissingPermission")
    fun disconnect() {
        Log.d(TAG, "User request disconnection")
        ble.disconnect()
        mConnection?.disconnect()
    }

    /* TODO
        vous pouvez placer ici les différentes méthodes permettant à l'utilisateur
        d'interagir avec le périphérique depuis l'activité
     */

    fun readTemperature(): Boolean {
        if (!isConnected.value!! || temperatureChar == null)
            return false
        else
            return ble.readTemperature()
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
                        /*  TODO
                            Ici nous somme sûr que le périphérique possède bien tous les services et caractéristiques
                            attendus et que nous y sommes connectés. Nous pouvous effectuer les premiers échanges BLE:
                            Dans notre cas il s'agit de s'enregistrer pour recevoir les notifications proposées par certaines
                            caractéristiques, on en profitera aussi pour mettre en place les callbacks correspondants.
                         */
                        setNotificationCallback(buttonClickChar).with{_: BluetoothDevice, data: Data ->
                            Log.d(TAG, "buttonClickChar - data: $data")
                        }
                        setNotificationCallback(currentTimeChar).with{_: BluetoothDevice, data: Data ->
                            Log.d(TAG, "currentTimeChar - data: $data")
                            val year = data.getIntValue(Data.FORMAT_UINT16, 0)
                            val month = data.getIntValue(Data.FORMAT_UINT8, 2)
                            val day = data.getIntValue(Data.FORMAT_UINT8, 3)
                            val hour = data.getIntValue(Data.FORMAT_UINT8, 4)
                            val minute = data.getIntValue(Data.FORMAT_UINT8, 5)
                            val second = data.getIntValue(Data.FORMAT_UINT8, 6)
                            //format date
                            val date = String.format("%02d/%02d/%04d %02d:%02d:%02d", day, month, year, hour, minute, second)
                            Log.d(TAG, "currentTimeChar - date: $date")
                        }

                        enableNotifications(buttonClickChar!!).enqueue()
                        enableNotifications(currentTimeChar!!).enqueue()
                        updateDate(DateTime.now())
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
            /*  TODO
                on peut effectuer ici la lecture de la caractéristique température
                la valeur récupérée sera envoyée à l'activité en utilisant le mécanisme
                des MutableLiveData
                On placera des méthodes similaires pour les autres opérations
            */
            readCharacteristic(temperatureChar!!).with{_: BluetoothDevice, data: Data ->
                Log.d(TAG, "temperatureChar - data: $data")
                val temperature = data.getIntValue(Data.FORMAT_SINT16, 0)
                Log.d(TAG, "temperatureChar - temperature: $temperature")
            }.enqueue()
            return false //FIXME
        }

        fun updateDate(dateTime: DateTime){
            if(currentTimeChar == null){
                return
            }
            Log.d(TAG, "updateDate - dateTime: $dateTime")
            currentTimeChar!!.setValue(dateTime.year, Data.FORMAT_UINT16, 0)
            currentTimeChar!!.setValue(dateTime.month, Data.FORMAT_UINT8, 2)
            currentTimeChar!!.setValue(dateTime.day, Data.FORMAT_UINT8, 3)
            currentTimeChar!!.setValue(dateTime.hour, Data.FORMAT_UINT8, 4)
            currentTimeChar!!.setValue(dateTime.minute, Data.FORMAT_UINT8, 5)
            currentTimeChar!!.setValue(dateTime.second, Data.FORMAT_UINT8, 6)
            writeCharacteristic(currentTimeChar!!, currentTimeChar!!.value).enqueue()
        }
    }

    companion object {
        private val TAG = BleOperationsViewModel::class.java.simpleName
    }

    init {
        ble.setConnectionObserver(bleConnectionObserver)
    }

}