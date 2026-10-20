package com.app.mediaplayer

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class PocketShakeDetector(
    context: Context,
    private val onDoubleShakeDetected: () -> Unit
) : SensorEventListener {

    private val sensorManager: SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var isListening = false
    private var firstShakeTime = 0L
    private var lastTriggerTime = 0L
    private var shakeCount = 0

    fun startListening() {
        if (isListening || accelerometer == null) return
        try {
            sensorManager?.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_UI
            )
            isListening = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopListening() {
        if (!isListening) return
        try {
            sensorManager?.unregisterListener(this)
            isListening = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH

        val gForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()

        if (gForce > 2.35f) {
            val now = System.currentTimeMillis()
            if (now - lastTriggerTime < 1200L) return

            if (now - firstShakeTime > 850L) {
                shakeCount = 1
                firstShakeTime = now
            } else if (now - firstShakeTime > 160L) {
                shakeCount++
                if (shakeCount >= 2) {
                    shakeCount = 0
                    lastTriggerTime = now
                    onDoubleShakeDetected()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
