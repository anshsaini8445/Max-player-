package com.app.mediaplayer

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Ultra-Precision In-Hand Double-Shake Detector:
 * 1. Pocket Sleep Guard: Uses Proximity Sensor + Light Sensor + Upside-Down Pocket Tilt check.
 * 2. Walking Pedometer Guard: Uses Hardware Step Detector + Software Walking Cadence Filter.
 * 3. Bike / Vehicle Vibration Guard: Detects continuous high-frequency engine/road vibrations and sleeps.
 * 4. Strict 2-Shake Verification: Waits 380ms after the 2nd shake to verify the user did NOT shake 3 or 4+ times.
 */
class PocketShakeDetector(
    private val context: Context,
    private val onValidDoubleShake: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val proximitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val lightSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val stepDetectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    private val mainHandler = Handler(Looper.getMainLooper())

    private var isEnabled = false
    private var isProximityCovered = false
    private var isDarkPocketLight = false
    private var isInHandViewingAngle = true

    private var lastStepTimestampMs = 0L
    private var rhythmicCadenceCount = 0
    private var lastModerateMotionMs = 0L

    // Bike & Vehicle continuous vibration filter state
    private val microVibrationTimestamps = mutableListOf<Long>()
    private var vehicleVibrationLockUntilMs = 0L

    // Strict 2-shake state
    private val shakePeakTimestamps = mutableListOf<Long>()
    private var lastTriggeredSongChangeMs = 0L
    private var spamPenaltyUntilMs = 0L
    private var pendingTwoShakeCheckRunnable: Runnable? = null

    // Thresholds tuned for intentional hand double-jerk
    private val intentionalShakeThresholdG = 2.68f
    private val minDoubleShakeGapMs = 170L
    private val maxDoubleShakeWindowMs = 640L
    private val cooldownBetweenSkipsMs = 3000L

    fun setEnabled(enabled: Boolean) {
        if (this.isEnabled == enabled) return
        this.isEnabled = enabled
        shakePeakTimestamps.clear()
        microVibrationTimestamps.clear()
        rhythmicCadenceCount = 0
        cancelPendingTwoShakeExecution()

        if (enabled) {
            accelerometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
            proximitySensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            lightSensor?.let {
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

    private fun cancelPendingTwoShakeExecution() {
        pendingTwoShakeCheckRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingTwoShakeCheckRunnable = null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isEnabled || event == null) return
        val now = System.currentTimeMillis()

        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                val maxRange = event.sensor.maximumRange.coerceAtLeast(1f)
                val dist = event.values.firstOrNull() ?: maxRange
                isProximityCovered = dist < (maxRange * 0.45f).coerceAtMost(3.5f)
                if (isProximityCovered) {
                    shakePeakTimestamps.clear()
                    cancelPendingTwoShakeExecution()
                }
            }

            Sensor.TYPE_LIGHT -> {
                val lux = event.values.firstOrNull() ?: 50f
                // Pitch dark (< 1.5 lux) combined with covered proximity indicates inside pocket/bag
                isDarkPocketLight = lux < 1.5f
            }

            Sensor.TYPE_STEP_DETECTOR -> {
                // Walking step detected -> sleep for 4.5 seconds
                lastStepTimestampMs = now
                shakePeakTimestamps.clear()
                cancelPendingTwoShakeExecution()
            }

            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]

                // Check if phone is held in a normal hand orientation (not upside down in trouser pocket)
                // When upside down in pocket, ay is strongly negative (< -6.8 m/s^2)
                isInHandViewingAngle = ay > -6.2f

                // 1. Block if inside pocket, upside-down, walking, or under bike/spam lock
                if (isProximityCovered ||
                    (isDarkPocketLight && !isInHandViewingAngle) ||
                    !isInHandViewingAngle ||
                    (now - lastStepTimestampMs) < 4500L ||
                    now < vehicleVibrationLockUntilMs ||
                    now < spamPenaltyUntilMs
                ) {
                    shakePeakTimestamps.clear()
                    cancelPendingTwoShakeExecution()
                    return
                }

                val gForce = sqrt((ax * ax + ay * ay + az * az).toDouble()).toFloat() / SensorManager.GRAVITY_EARTH

                // 2. Bike / Vehicle Continuous Road Vibration Guard:
                // Bikes & vehicles create continuous 1.25G - 2.30G jitter > 12 times within 1.5 seconds
                if (gForce in 1.25f..2.35f) {
                    microVibrationTimestamps.add(now)
                    microVibrationTimestamps.removeAll { (now - it) > 1500L }
                    if (microVibrationTimestamps.size >= 14) {
                        // Continuous bike/vehicle vibration detected -> lock shake for 6 seconds
                        vehicleVibrationLockUntilMs = now + 6000L
                        microVibrationTimestamps.clear()
                        shakePeakTimestamps.clear()
                        cancelPendingTwoShakeExecution()
                        return
                    }
                }

                // 3. Software Walking Cadence Filter (1.38G - 2.15G every 340ms - 760ms)
                if (gForce in 1.38f..2.18f) {
                    val gap = now - lastModerateMotionMs
                    if (gap in 340L..760L) {
                        rhythmicCadenceCount++
                        if (rhythmicCadenceCount >= 4) {
                            lastStepTimestampMs = now
                            rhythmicCadenceCount = 0
                            shakePeakTimestamps.clear()
                            cancelPendingTwoShakeExecution()
                            return
                        }
                    } else if (gap > 1200L) {
                        rhythmicCadenceCount = 0
                    }
                    lastModerateMotionMs = now
                }

                // 4. Intentional Sharp Hand Shake Detection (> 2.68G)
                if (gForce >= intentionalShakeThresholdG) {
                    val lastPeak = shakePeakTimestamps.lastOrNull() ?: 0L
                    // Debounce single shake rebound within 150ms
                    if (now - lastPeak < 150L) return

                    shakePeakTimestamps.add(now)
                    shakePeakTimestamps.removeAll { (now - it) > 1200L }

                    // If user shakes 3, 4 or more times rapidly -> Cancel pending skip and apply anti-spam lock!
                    if (shakePeakTimestamps.size >= 3) {
                        cancelPendingTwoShakeExecution()
                        if (shakePeakTimestamps.size >= 4) {
                            shakePeakTimestamps.clear()
                            spamPenaltyUntilMs = now + 3200L
                        }
                        return
                    }

                    // Exactly 2 shakes detected: wait 360ms to ensure no 3rd/4th shake follows
                    if (shakePeakTimestamps.size == 2) {
                        val firstShake = shakePeakTimestamps[0]
                        val secondShake = shakePeakTimestamps[1]
                        val deltaMs = secondShake - firstShake

                        if (deltaMs in minDoubleShakeGapMs..maxDoubleShakeWindowMs) {
                            cancelPendingTwoShakeExecution()
                            val verifyRunnable = Runnable {
                                val currentNow = System.currentTimeMillis()
                                if (shakePeakTimestamps.size == 2 &&
                                    !isProximityCovered &&
                                    currentNow >= spamPenaltyUntilMs &&
                                    currentNow >= vehicleVibrationLockUntilMs &&
                                    (currentNow - lastTriggeredSongChangeMs) >= cooldownBetweenSkipsMs
                                ) {
                                    lastTriggeredSongChangeMs = currentNow
                                    shakePeakTimestamps.clear()
                                    triggerShortHapticFeedback()
                                    onValidDoubleShake()
                                }
                            }
                            pendingTwoShakeCheckRunnable = verifyRunnable
                            mainHandler.postDelayed(verifyRunnable, 360L)
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
