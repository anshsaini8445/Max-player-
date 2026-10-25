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
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class VolumeHudOverlayHelper(
    private val context: Context,
    private val rootContainer: FrameLayout,
    private val audioManager: AudioManager,
    private val loudnessEnhancerProvider: () -> LoudnessEnhancer?,
    private val playerVolumeSetter: (Float) -> Unit
) {

    private val density = context.resources.displayMetrics.density
    private val hudCard: LinearLayout
    private val hudIconView: HudVectorIconView
    private val tvHudPercent: TextView
    private val boostBarWithDotsView: HudBarWithBoostDotsView

    private val handler = Handler(Looper.getMainLooper())
    private val hideHudRunnable = Runnable {
        hideHudInternal()
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
                setStroke((1.2f * density).toInt(), Color.parseColor("#3300FFFF"))
            }
            layoutParams = FrameLayout.LayoutParams(
                (292 * density).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply {
                topMargin = (28 * density).toInt()
            }
            visibility = View.GONE
            elevation = 32f * density
        }

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (14 * density).toInt() }
        }

        hudIconView = HudVectorIconView(context, density).apply {
            layoutParams = LinearLayout.LayoutParams(
                (30 * density).toInt(),
                (28 * density).toInt()
            ).apply { marginEnd = (10 * density).toInt() }
        }

        tvHudPercent = TextView(context).apply {
            text = "$currentVolumePct%"
            setTextColor(Color.WHITE)
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
        }

        topRow.addView(hudIconView)
        topRow.addView(tvHudPercent)

        boostBarWithDotsView = HudBarWithBoostDotsView(context, density).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (14 * density).toInt()
            )
        }

        hudCard.addView(topRow)
        hudCard.addView(boostBarWithDotsView)
        rootContainer.addView(hudCard)
    }

    private fun hideHudInternal() {
        hudCard.animate()
            .alpha(0f)
            .setDuration(180L)
            .withEndAction { hudCard.visibility = View.GONE }
            .start()
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
        val safePct = percent.coerceIn(0, 200)
        tvHudPercent.text = "$safePct%"
        tvHudPercent.setTextColor(
            if (safePct > 100) Color.parseColor("#00FFFF") else Color.WHITE
        )
        hudIconView.setMode(isBrightness = false, isMuted = safePct == 0)
        boostBarWithDotsView.setHudState(percent = safePct, isBrightnessMode = false)

        hudCard.animate().cancel()
        hudCard.alpha = 1f
        hudCard.visibility = View.VISIBLE

        handler.removeCallbacks(hideHudRunnable)
        handler.postDelayed(hideHudRunnable, 1150L)
    }

    fun showBrightnessHud(percent: Int) {
        val safePct = percent.coerceIn(0, 100)
        tvHudPercent.text = "$safePct%"
        tvHudPercent.setTextColor(Color.WHITE)
        hudIconView.setMode(isBrightness = true, isMuted = false)
        boostBarWithDotsView.setHudState(percent = safePct, isBrightnessMode = true)

        hudCard.animate().cancel()
        hudCard.alpha = 1f
        hudCard.visibility = View.VISIBLE

        handler.removeCallbacks(hideHudRunnable)
        handler.postDelayed(hideHudRunnable, 1150L)
    }

    private class HudVectorIconView(
        context: Context,
        private val d: Float
    ) : View(context) {

        private var isBrightnessMode = false
        private var isMuted = false

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.4f * d
            strokeCap = Paint.Cap.ROUND
        }

        fun setMode(isBrightness: Boolean, isMuted: Boolean) {
            this.isBrightnessMode = isBrightness
            this.isMuted = isMuted
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2f
            val cy = h / 2f

            if (isBrightnessMode) {
                // Sun Brightness Vector Icon
                val sunR = w.coerceAtMost(h) * 0.22f
                fillPaint.color = Color.parseColor("#FFD700")
                strokePaint.color = Color.parseColor("#FFD700")
                canvas.drawCircle(cx, cy, sunR, fillPaint)

                val rayInner = sunR * 1.45f
                val rayOuter = sunR * 2.05f
                for (i in 0 until 8) {
                    val angle = Math.toRadians((i * 45).toDouble())
                    val x1 = (cx + cos(angle) * rayInner).toFloat()
                    val y1 = (cy + sin(angle) * rayInner).toFloat()
                    val x2 = (cx + cos(angle) * rayOuter).toFloat()
                    val y2 = (cy + sin(angle) * rayOuter).toFloat()
                    canvas.drawLine(x1, y1, x2, y2, strokePaint)
                }
            } else {
                // Speaker Volume Vector Icon
                fillPaint.color = Color.parseColor("#E0E0E0")
                strokePaint.color = Color.parseColor("#E0E0E0")
                val path = Path().apply {
                    moveTo(w * 0.10f, cy - h * 0.18f)
                    lineTo(w * 0.28f, cy - h * 0.18f)
                    lineTo(w * 0.52f, cy - h * 0.42f)
                    lineTo(w * 0.52f, cy + h * 0.42f)
                    lineTo(w * 0.28f, cy + h * 0.18f)
                    lineTo(w * 0.10f, cy + h * 0.18f)
                    close()
                }
                canvas.drawPath(path, fillPaint)

                if (isMuted) {
                    strokePaint.color = Color.parseColor("#FF5252")
                    canvas.drawLine(w * 0.64f, cy - h * 0.24f, w * 0.92f, cy + h * 0.24f, strokePaint)
                    canvas.drawLine(w * 0.92f, cy - h * 0.24f, w * 0.64f, cy + h * 0.24f, strokePaint)
                } else {
                    val innerArc = RectF(w * 0.42f, cy - h * 0.26f, w * 0.74f, cy + h * 0.26f)
                    val outerArc = RectF(w * 0.34f, cy - h * 0.42f, w * 0.90f, cy + h * 0.42f)
                    canvas.drawArc(innerArc, -45f, 90f, false, strokePaint)
                    canvas.drawArc(outerArc, -45f, 90f, false, strokePaint)
                }
            }
        }
    }

    private class HudBarWithBoostDotsView(
        context: Context,
        private val d: Float
    ) : View(context) {

        private var currentPct = 80
        private var isBrightness = false

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

        fun setHudState(percent: Int, isBrightnessMode: Boolean) {
            this.isBrightness = isBrightnessMode
            this.currentPct = if (isBrightnessMode) percent.coerceIn(0, 100) else percent.coerceIn(0, 200)
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

            val normalProgress = (currentPct.coerceIn(0, 100) / 100f)
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
                val isLit = if (isBrightness) {
                    currentPct >= ((i + 1) * 25)
                } else {
                    val threshold = 100 + ((i + 1) * 25)
                    currentPct >= threshold - 12
                }
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
