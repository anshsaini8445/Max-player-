package com.app.mediaplayer

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.CountDownTimer
import android.widget.Toast
import androidx.media3.common.Player
import kotlin.math.abs

class SmartIdlePauseHelper(
    private val context: Context,
    private val playerProvider: () -> Player?,
    private val onTickLabelUpdate: (String) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var countDownTimer: CountDownTimer? = null
    private var lastMotionTimestamp = System.currentTimeMillis()
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var isSensorActive = false

    // Starts sleep timer with 15-second gradual audio fade-out at the end so user's sleep is never disturbed
    fun startSmoothFadeSleepTimer(minutes: Long, enableMotionAutoReset: Boolean = false) {
        cancelTimer()
        if (minutes <= 0L) {
            onTickLabelUpdate("Timer")
            return
        }

        if (enableMotionAutoReset) {
            val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (accel != null) {
                sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL)
                isSensorActive = true
            }
        }

        val totalMs = minutes * 60L * 1000L
        lastMotionTimestamp = System.currentTimeMillis()

        countDownTimer = object : CountDownTimer(totalMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val mins = millisUntilFinished / 60000L
                val secs = (millisUntilFinished % 60000L) / 1000L
                onTickLabelUpdate(String.format("%02d:%02d", mins, secs))

                // Smooth 15-second volume fade-out during the final 15 seconds
                if (millisUntilFinished <= 15000L) {
                    val fadeVolume = (millisUntilFinished.toFloat() / 15000f).coerceIn(0.05f, 1.0f)
                    playerProvider()?.volume = fadeVolume
                }
            }

            override fun onFinish() {
                playerProvider()?.let { p ->
                    p.pause()
                    p.volume = 1.0f
                }
                stopSensor()
                onTickLabelUpdate("Timer")
                Toast.makeText(
                    context,
                    "Smart Sleep Timer: Media faded out & paused",
                    Toast.LENGTH_LONG
                ).show()
            }
        }.start()
    }

    fun cancelTimer() {
        countDownTimer?.cancel()
        countDownTimer = null
        playerProvider()?.volume = 1.0f
        stopSensor()
    }

    private fun stopSensor() {
        if (isSensorActive) {
            try {
                sensorManager.unregisterListener(this)
            } catch (_: Exception) {
            }
            isSensorActive = false
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val delta = abs(x - lastX) + abs(y - lastY) + abs(z - lastZ)
        lastX = x
        lastY = y
        lastZ = z

        if (delta > 3.2f) {
            lastMotionTimestamp = System.currentTimeMillis()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
