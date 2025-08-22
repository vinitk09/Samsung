package com.example.multiagent.agents

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import org.greenrobot.eventbus.EventBus

class MovementAgent(context: Context) : SensorEventListener {
    private val tag = "MovementAgent"
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val sampleRateMillis = 500L
    private var lastPublishTime: Long = 0

    private val lastAccelData = FloatArray(3)
    private val lastGyroData = FloatArray(3)

    fun start() {
        Log.d(tag, "MovementAgent starting.")
        lastPublishTime = 0
        accelerometer?.also { accel ->
            sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL)
        }
        gyroscope?.also { gyro ->
            sensorManager.registerListener(this, gyro, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() {
        Log.d(tag, "MovementAgent stopping.")
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, lastAccelData, 0, 3)
            }
            Sensor.TYPE_GYROSCOPE -> {
                System.arraycopy(event.values, 0, lastGyroData, 0, 3)
            }
        }

        val currentTime = System.currentTimeMillis()

        if ((currentTime - lastPublishTime) > sampleRateMillis) {
            lastPublishTime = currentTime

            // --- FIX IS HERE ---
            // Use named arguments to explicitly pass each required value.
            val movementEvent = MovementEvent(
                timestamp = currentTime,
                accX = lastAccelData[0],
                accY = lastAccelData[1],
                accZ = lastAccelData[2],
                gyroX = lastGyroData[0],
                gyroY = lastGyroData[1],
                gyroZ = lastGyroData[2]
            )
            EventBus.getDefault().post(movementEvent)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Do nothing
    }
}