package com.app.mediaplayer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

class VolumeHudOverlayHelper(
    private val context: Context,
    private val rootContainer: FrameLayout,
    private val audioManager: AudioManager,
    private val loudnessEnhancerProvider: () -> LoudnessEnhancer?,
    private val playerVolumeSetter: (Float) -> Unit
) {

    private val density = context.resources.displayMetrics.density
    private lateinit var hudCard: LinearLayout
    private lateinit var speakerIconView: SpeakerVectorView
    private lateinit var tvVolumePercent: TextView
    private lateinit var boostBarWithDotsView: VolumeBarWithBoostDotsView

    private val handler = Handler(Looper.getMainLooper())
    private val hideHudRunnable = Runnable {
        if (::hudCard.isInitialized) {
            hudCard.animate()
                .alpha(0f)
                .setDuration(180L)
                .withEndAction { hudCard.visibility = View.GONE }
                .start()
        }
    }

    private var currentVolumePct: Int = 80

    init {
        val maxSys = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curSys = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        currentVolumePct = ((curSys.toFloat() / maxSys.toFloat()) * 100f).roundToInt().coerceIn(0, 100)

        hudCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(
                (22 * density).toInt(),
                (16 * density).toInt(),
                (22 * density).toInt(),
                (18 * density).toInt()
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EA050508"))
                cornerRadius = 20f * density
                setStroke((1 * density).toInt(), Color.parseColor("#28FFFFFF"))
            }
            layoutParams = FrameLayout.LayoutParams(
                (290 * density).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply {
                topMargin = (34 * density).toInt()
            }
            visibility = View.GONE
            elevation = 30f * density
        }

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (14 * density).toInt() }
        }

        speakerIconView = SpeakerVectorView(context, density).apply {
            layoutParams = LinearLayout.LayoutParams(
                (30 * density).toInt(),
                (28 * density).toInt()
            ).apply { marginEnd = (10 * density).toInt() }
        }

        tvVolumePercent = TextView(context).apply {
            text = "$currentVolumePct%"
            setTextColor(Color.WHITE)
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
        }

        topRow.addView(speakerIconView)
        topRow.addView(tvVolumePercent)

        boostBarWithDotsView = VolumeBarWithBoostDotsView(context, density).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (14 * density).toInt()
            )
        }

        hudCard.addView(topRow)
        hudCard.addView(boostBarWithDotsView)
        rootContainer.addView(hudCard)
    }

    fun getCurrentVolumePercent(): Int = currentVolumePct

    fun onHardwareVolumeKeyPressed(isVolumeUp: Boolean): Boolean {
        val maxSys = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curSys = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        if (currentVolumePct <= 100 && !isVolumeUp) {
            val step = (100f / maxSys.toFloat()).roundToInt().coerceAtLeast(5)
            val nextPct = (currentVolumePct - step).coerceIn(0, 100)
            applyVolumePercent(nextPct, showHud = true)
            return true
        } else if (currentVolumePct < 100 && isVolumeUp) {
            val step = (100f / maxSys.toFloat()).roundToInt().coerceAtLeast(5)
            val nextPct = (currentVolumePct + step).coerceAtMost(100)
            applyVolumePercent(nextPct, showHud = true)
            return true
        } else if (currentVolumePct >= 100 && isVolumeUp) {
            if (curSys < maxSys) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxSys, 0)
            }
            val nextBoostPct = (currentVolumePct + 10).coerceAtMost(200)
            applyVolumePercent(nextBoostPct, showHud = true)
            return true
        } else {
            val nextBoostPct = (currentVolumePct - 10).coerceAtLeast(0)
            applyVolumePercent(nextBoostPct, showHud = true)
            return true
        }
    }

    fun applyVolumePercent(targetPercent: Int, showHud: Boolean = true) {
        currentVolumePct = targetPercent.coerceIn(0, 200)
        val maxSys = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

        if (currentVolumePct == 0) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            playerVolumeSetter(0f)
            try {
                loudnessEnhancerProvider()?.setTargetGain(0)
            } catch (_: Exception) {
            }
        } else if (currentVolumePct <= 100) {
            val sysLevel = ((currentVolumePct / 100f) * maxSys).roundToInt().coerceIn(1, maxSys)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, sysLevel, 0)
            playerVolumeSetter(currentVolumePct / 100f)
            try {
                loudnessEnhancerProvider()?.setTargetGain(0)
            } catch (_: Exception) {
            }
        } else {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxSys, 0)
            playerVolumeSetter(1.0f)
            val boostFraction = (currentVolumePct - 100) / 100f
            val targetGainMb = (boostFraction * 2600f).roundToInt()
            try {
                val enhancer = loudnessEnhancerProvider()
                if (enhancer != null) {
                    if (!enhancer.enabled) enhancer.enabled = true
                    enhancer.setTargetGain(targetGainMb)
                }
            } catch (_: Exception) {
            }
        }

        if (showHud) {
            showVolumeHud(currentVolumePct)
        }
    }

    fun showVolumeHud(percent: Int) {
        tvVolumePercent.text = "$percent%"
        tvVolumePercent.setTextColor(
            if (percent > 100) Color.parseColor("#00FFFF") else Color.WHITE
        )
        speakerIconView.setMuted(percent == 0)
        boostBarWithDotsView.setVolumePercent(percent)

        hudCard.animate().cancel()
        hudCard.alpha = 1f
        hudCard.visibility = View.VISIBLE

        handler.removeCallbacks(hideHudRunnable)
        handler.postDelayed(hideHudRunnable, 1100L)
    }

    private class SpeakerVectorView(
        context: Context,
        private val d: Float
    ) : View(context) {

        private var isMuted = false
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D6D6D6")
            style = Paint.Style.FILL
        }
        private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D6D6D6")
            style = Paint.Style.STROKE
            strokeWidth = 2.4f * d
            strokeCap = Paint.Cap.ROUND
        }

        fun setMuted(muted: Boolean) {
            isMuted = muted
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val cy = h / 2f

            val path = Path().apply {
                moveTo(w * 0.10f, cy - h * 0.18f)
                lineTo(w * 0.28f, cy - h * 0.18f)
                lineTo(w * 0.52f, cy - h * 0.42f)
                lineTo(w * 0.52f, cy + h * 0.42f)
                lineTo(w * 0.28f, cy + h * 0.18f)
                lineTo(w * 0.10f, cy + h * 0.18f)
                close()
            }
            canvas.drawPath(path, paint)

            if (isMuted) {
                canvas.drawLine(w * 0.64f, cy - h * 0.24f, w * 0.92f, cy + h * 0.24f, wavePaint)
                canvas.drawLine(w * 0.92f, cy - h * 0.24f, w * 0.64f, cy + h * 0.24f, wavePaint)
            } else {
                val innerArc = RectF(w * 0.42f, cy - h * 0.26f, w * 0.74f, cy + h * 0.26f)
                val outerArc = RectF(w * 0.34f, cy - h * 0.42f, w * 0.90f, cy + h * 0.42f)
                canvas.drawArc(innerArc, -45f, 90f, false, wavePaint)
                canvas.drawArc(outerArc, -45f, 90f, false, wavePaint)
            }
        }
    }

    private class VolumeBarWithBoostDotsView(
        context: Context,
        private val d: Float
    ) : View(context) {

        private var volumePct = 80

        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4DFFFFFF")
            style = Paint.Style.FILL
        }

        private val activeWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        private val activeBoostDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00FFFF")
            style = Paint.Style.FILL
        }

        fun setVolumePercent(pct: Int) {
            volumePct = pct.coerceIn(0, 200)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val barHeight = 9.5f * d
            val topY = (h - barHeight) / 2f
            val bottomY = topY + barHeight
            val radius = barHeight / 2f

            val barMaxW = w * 0.72f
            val trackRect = RectF(0f, topY, barMaxW, bottomY)
            canvas.drawRoundRect(trackRect, radius, radius, trackPaint)

            val normalProgress = (volumePct.coerceIn(0, 100) / 100f)
            if (normalProgress > 0f) {
                val fillW = (barMaxW * normalProgress).coerceAtLeast(barHeight)
                val fillRect = RectF(0f, topY, fillW, bottomY)
                canvas.drawRoundRect(fillRect, radius, radius, activeWhitePaint)
            }

            val dotsRegionStart = barMaxW + 10f * d
            val dotsRegionWidth = (w - dotsRegionStart).coerceAtLeast(1f)
            val dotSpacing = dotsRegionWidth / 4f
            val dotRadius = 4.6f * d
            val cy = h / 2f

            for (i in 0 until 4) {
                val dotCenterX = dotsRegionStart + (i + 0.5f) * dotSpacing
                val threshold = 100 + ((i + 1) * 25)
                val isLit = volumePct >= threshold - 12
                canvas.drawCircle(
                    dotCenterX,
                    cy,
                    dotRadius,
                    if (isLit) activeBoostDotPaint else trackPaint
                )
            }
        }
    }
}
