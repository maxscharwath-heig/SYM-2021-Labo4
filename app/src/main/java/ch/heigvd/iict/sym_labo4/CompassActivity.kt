package ch.heigvd.iict.sym_labo4

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import ch.heigvd.iict.sym_labo4.gl.OpenGLRenderer

/**
 * Project: Labo4
 * Created by fabien.dutoit on 21.11.2016
 * Updated by fabien.dutoit on 06.11.2020
 * (C) 2016 - HEIG-VD, IICT
 */
class CompassActivity : AppCompatActivity(), SensorEventListener {

    //opengl
    private lateinit var opglr: OpenGLRenderer
    private lateinit var m3DView: GLSurfaceView
    private lateinit var sensorManager: SensorManager
    private lateinit var accelerometerSensor: Sensor
    private lateinit var magnetometerSensor: Sensor

    private lateinit var accData: FloatArray
    private lateinit var magnData: FloatArray
    private lateinit var rotMatrix: FloatArray

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // we need fullscreen
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // we initiate the view
        setContentView(R.layout.activity_compass)

        //we create the renderer
        opglr = OpenGLRenderer(applicationContext)

        // link to GUI
        m3DView = findViewById(R.id.compass_opengl)

        //init opengl surface view
        m3DView.setRenderer(opglr)

        // Register to sensors
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        magnetometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Init arrays
        accData = FloatArray(3)
        magnData = FloatArray(3)
        rotMatrix = FloatArray(16)

    }

    override fun onSensorChanged(event: SensorEvent) {

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            accData = event.values
        }
        else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            magnData = event.values
        }

        rotMatrix = opglr.swapRotMatrix(rotMatrix)
        SensorManager.getRotationMatrix(rotMatrix, null, accData, magnData);
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {}

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, magnetometerSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    /*
        TODO
        your activity need to register to accelerometer and magnetometer sensors' updates
        then you may want to call
        opglr.swapRotMatrix()
        with the 4x4 rotation matrix, every time a new matrix is computed
        more information on rotation matrix can be found online:
        https://developer.android.com/reference/android/hardware/SensorManager.html#getRotationMatrix(float[],%20float[],%20float[],%20float[])
    */

}