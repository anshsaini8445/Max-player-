package com.app.mediaplayer

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.concurrent.thread

object SplashOverlayHelper {

    private var hasShownInProcess = false

    fun attachAnimatedSplashIfFirstLaunch(activity: Activity) {
        if (hasShownInProcess) return
        hasShownInProcess = true

        try {
            val d = activity.resources.displayMetrics.density
            val decorGroup = activity.window.decorView as? ViewGroup ?: return

            thread {
                try {
                    DeepMediaScanner.scanAllVideosEverywhere(activity.applicationContext, forceRefresh = false)
                } catch (_: Exception) {
                }
            }

            val splashOverlay = FrameLayout(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.parseColor("#0A0A10"))
                isClickable = true
                isFocusable = true
                elevation = 100f * d
            }

            val centerCol = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
                setPadding((24 * d).toInt(), (24 * d).toInt(), (24 * d).toInt(), (24 * d).toInt())
            }

            val logoEmblem = ImageView(activity).apply {
                layoutParams = LinearLayout.LayoutParams((116 * d).toInt(), (116 * d).toInt())
                setImageDrawable(HybridBrandLogoDrawable(d))
                scaleX = 0.75f
                scaleY = 0.75f
                alpha = 0f
            }

            val tvBrandName = TextView(activity).apply {
                text = "HYBRID PLAYER"
                setTextColor(Color.WHITE)
                textSize = 26f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                letterSpacing = 0.08f
                setPadding(0, (18 * d).toInt(), 0, (6 * d).toInt())
                alpha = 0f
            }

            val tvTagline = TextView(activity).apply {
                text = "ULTRA HD 8K CINEMA • DOLBY EQUALIZER • REELS"
                setTextColor(Color.parseColor("#00FFFF"))
                textSize = 11.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding((14 * d).toInt(), (6 * d).toInt(), (14 * d).toInt(), (6 * d).toInt())
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1800FFFF"))
                    cornerRadius = 16f * d
                    setStroke((1 * d).toInt(), Color.parseColor("#5500FFFF"))
                }
                alpha = 0f
            }

            centerCol.addView(logoEmblem)
            centerCol.addView(tvBrandName)
            centerCol.addView(tvTagline)
            splashOverlay.addView(centerCol)

            val tvBottomContext = TextView(activity).apply {
                text = "Powered by Hybrid Player 8K Hardware Engine"
                setTextColor(Color.parseColor("#88B0C4DE"))
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                ).apply { bottomMargin = (36 * d).toInt() }
            }
            splashOverlay.addView(tvBottomContext)

            decorGroup.addView(splashOverlay)

            logoEmblem.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(380L).start()
            tvBrandName.animate().alpha(1f).setStartDelay(120L).setDuration(350L).start()
            tvTagline.animate().alpha(1f).setStartDelay(200L).setDuration(350L).start()

            Handler(Looper.getMainLooper()).postDelayed({
                splashOverlay.animate()
                    .alpha(0f)
                    .setDuration(280L)
                    .withEndAction {
                        try {
                            decorGroup.removeView(splashOverlay)
                        } catch (_: Exception) {
                        }
                    }
                    .start()
            }, 1150L)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private class HybridBrandLogoDrawable(private val d: Float) : Drawable() {
        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 0f || h <= 0f) return
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val r = w.coerceAtMost(h) * 0.45f

            val outerGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#1800FFFF")
            }
            canvas.drawCircle(cx, cy, r * 1.08f, outerGlow)

            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3.5f * d
                shader = LinearGradient(
                    0f, 0f, w, h,
                    Color.parseColor("#00FFFF"),
                    Color.parseColor("#FFD700"),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(cx, cy, r * 0.92f, ringPaint)

            val playPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shader = LinearGradient(
                    cx - r * 0.3f, cy - r * 0.4f, cx + r * 0.4f, cy + r * 0.4f,
                    Color.parseColor("#00FFFF"),
                    Color.parseColor("#FFD700"),
                    Shader.TileMode.CLAMP
                )
            }
            val playPath = Path().apply {
                moveTo(cx - r * 0.24f, cy - r * 0.36f)
                lineTo(cx + r * 0.38f, cy)
                lineTo(cx - r * 0.24f, cy + r * 0.36f)
                close()
            }
            canvas.drawPath(playPath, playPaint)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
