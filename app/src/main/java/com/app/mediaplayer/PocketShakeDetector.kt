package com.app.mediaplayer

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.sqrt

class PocketShakeDetector(
    private val context: Context,
    private val onValidDoubleShake: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val proximitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val stepDetectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    private var isEnabled = false
    private var isPocketCovered = false
    private var lastStepTimestampMs = 0L
    private var rhythmicCadenceCount = 0
    private var lastModerateMotionMs = 0L

    private val shakePeakTimestamps = mutableListOf<Long>()
    private var lastTriggeredSongChangeMs = 0L
    private var spamPenaltyUntilMs = 0L

    // Calibrated thresholds so small vibrations or walking never trigger a track skip
    private val singleJerkThresholdG = 2.55f
    private val minDoubleShakeGapMs = 160L
    private val maxDoubleShakeWindowMs = 620L
    private val cooldownBetweenSkipsMs = 2600L

    fun setEnabled(enabled: Boolean) {
        if (this.isEnabled == enabled) return
        this.isEnabled = enabled
        shakePeakTimestamps.clear()
        rhythmicCadenceCount = 0

        if (enabled) {
            accelerometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
            proximitySensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            stepDetectorSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        } else {
            try {
                sensorManager.unregisterListener(this)
            } catch (_: Exception) {
            }
        }
    }

    fun isShakeEnabled(): Boolean = isEnabled

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isEnabled || event == null) return
        val now = System.currentTimeMillis()

        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                val maxRange = event.sensor.maximumRange.coerceAtLeast(1f)
                val distance = event.values.firstOrNull() ?: maxRange
                // If proximity sensor is covered (inside pocket or bag), sleep shake detection
                isPocketCovered = distance < (maxRange * 0.4f).coerceAtMost(3.0f)
            }

            Sensor.TYPE_STEP_DETECTOR -> {
                // Hardware pedometer detected walking step -> sleep shake for 4 seconds
                lastStepTimestampMs = now
                shakePeakTimestamps.clear()
            }

            Sensor.TYPE_ACCELEROMETER -> {
                // 1. Sleep if phone is inside pocket or user was walking in last 4 seconds
                if (isPocketCovered || (now - lastStepTimestampMs) < 4000L) {
                    shakePeakTimestamps.clear()
                    return
                }

                // 2. Sleep if under anti-spam penalty (user shook 4+ times rapidly)
                if (now < spamPenaltyUntilMs) {
                    return
                }

                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]
                val gForce = sqrt((ax * ax + ay * ay + az * az).toDouble()).toFloat() / SensorManager.GRAVITY_EARTH

                // Software walking cadence detector (1.35G - 2.15G repeated every 350ms - 750ms)
                if (gForce in 1.38f..2.15f) {
                    val gap = now - lastModerateMotionMs
                    if (gap in 330L..780L) {
                        rhythmicCadenceCount++
                        if (rhythmicCadenceCount >= 4) {
                            // Walking rhythm detected -> pause shake detector for 4 seconds
                            lastStepTimestampMs = now
                            rhythmicCadenceCount = 0
                            shakePeakTimestamps.clear()
                            return
                        }
                    } else if (gap > 1200L) {
                        rhythmicCadenceCount = 0
                    }
                    lastModerateMotionMs = now
                }

                // 3. Sharp intentional hand shake detection (> 2.55G)
                if (gForce >= singleJerkThresholdG) {
                    val lastPeak = shakePeakTimestamps.lastOrNull() ?: 0L
                    // Ignore bounce within 140ms of the same jerk
                    if (now - lastPeak < 140L) return

                    shakePeakTimestamps.add(now)
                    // Keep only peaks from the last 1100ms
                    shakePeakTimestamps.removeAll { (now - it) > 1100L }

                    // Anti-Spam Lock: If someone shakes 4 or more times rapidly, block and reset
                    if (shakePeakTimestamps.size >= 4) {
                        shakePeakTimestamps.clear()
                        spamPenaltyUntilMs = now + 3000L
                        return
                    }

                    // Check for a clean 2-shake gesture
                    if (shakePeakTimestamps.size == 2) {
                        val firstShake = shakePeakTimestamps[0]
                        val secondShake = shakePeakTimestamps[1]
                        val deltaMs = secondShake - firstShake

                        if (deltaMs in minDoubleShakeGapMs..maxDoubleShakeWindowMs) {
                            if (now - lastTriggeredSongChangeMs >= cooldownBetweenSkipsMs) {
                                lastTriggeredSongChangeMs = now
                                shakePeakTimestamps.clear()
                                triggerShortHapticFeedback()
                                onValidDoubleShake()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun triggerShortHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(45L, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib?.vibrate(VibrationEffect.createOneShot(45L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vib?.vibrate(45L)
                }
            }
        } catch (_: Exception) {
        }
    }

    fun release() {
        setEnabled(false)
    }
}
