package com.app.mediaplayer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

class PlayerVectorIcon(
    private val type: String,
    private val density: Float,
    private val iconColor: Int
) : Drawable() {

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = iconColor
        style = Paint.Style.STROKE
        strokeWidth = 2.0f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = iconColor
        style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val r = w.coerceAtMost(h) * 0.36f
        val path = Path()

        when (type) {
            "HEADPHONES" -> {
                val arc = RectF(cx - r * 0.85f, cy - r * 0.85f, cx + r * 0.85f, cy + r * 0.65f)
                canvas.drawArc(arc, 180f, 180f, false, strokePaint)
                val leftCup = RectF(cx - r * 0.92f, cy - r * 0.1f, cx - r * 0.48f, cy + r * 0.72f)
                val rightCup = RectF(cx + r * 0.48f, cy - r * 0.1f, cx + r * 0.92f, cy + r * 0.72f)
                canvas.drawRoundRect(leftCup, 3f * density, 3f * density, fillPaint)
                canvas.drawRoundRect(rightCup, 3f * density, 3f * density, fillPaint)
            }
            "QUEUE_LIST" -> {
                canvas.drawLine(cx - r * 0.75f, cy - r * 0.55f, cx + r * 0.75f, cy - r * 0.55f, strokePaint)
                canvas.drawLine(cx - r * 0.75f, cy, cx + r * 0.75f, cy, strokePaint)
                canvas.drawLine(cx - r * 0.75f, cy + r * 0.55f, cx + r * 0.35f, cy + r * 0.55f, strokePaint)
            }
            "THREE_DOTS" -> {
                canvas.drawCircle(cx, cy - r * 0.65f, 2.2f * density, fillPaint)
                canvas.drawCircle(cx, cy, 2.2f * density, fillPaint)
                canvas.drawCircle(cx, cy + r * 0.65f, 2.2f * density, fillPaint)
            }
            "SPEAKER_ON", "SPEAKER_MUTE" -> {
                path.moveTo(cx - r * 0.75f, cy - r * 0.32f)
                path.lineTo(cx - r * 0.3f, cy - r * 0.32f)
                path.lineTo(cx + r * 0.18f, cy - r * 0.75f)
                path.lineTo(cx + r * 0.18f, cy + r * 0.75f)
                path.lineTo(cx - r * 0.3f, cy + r * 0.32f)
                path.lineTo(cx - r * 0.75f, cy + r * 0.32f)
                path.close()
                canvas.drawPath(path, strokePaint)
                if (type == "SPEAKER_MUTE") {
                    canvas.drawLine(cx + r * 0.38f, cy - r * 0.35f, cx + r * 0.85f, cy + r * 0.35f, strokePaint)
                    canvas.drawLine(cx + r * 0.85f, cy - r * 0.35f, cx + r * 0.38f, cy + r * 0.35f, strokePaint)
                } else {
                    val arc = RectF(cx - r * 0.1f, cy - r * 0.55f, cx + r * 0.78f, cy + r * 0.55f)
                    canvas.drawArc(arc, -45f, 90f, false, strokePaint)
                }
            }
            "LOCK_OPEN", "LOCK_CLOSED" -> {
                val body = RectF(cx - r * 0.68f, cy - r * 0.1f, cx + r * 0.68f, cy + r * 0.82f)
                canvas.drawRoundRect(body, 3.5f * density, 3.5f * density, strokePaint)
                val shackle = RectF(cx - r * 0.45f, cy - r * 0.78f, cx + r * 0.45f, cy - r * 0.05f)
                canvas.drawArc(shackle, 180f, if (type == "LOCK_OPEN") 135f else 180f, false, strokePaint)
                canvas.drawCircle(cx, cy + r * 0.35f, 2f * density, fillPaint)
            }
            "SCREENSHOT_CAM" -> {
                val cam = RectF(cx - r * 0.82f, cy - r * 0.52f, cx + r * 0.82f, cy + r * 0.68f)
                canvas.drawRoundRect(cam, 4f * density, 4f * density, strokePaint)
                canvas.drawCircle(cx, cy + r * 0.08f, r * 0.34f, strokePaint)
            }
            "ROTATE" -> {
                val box = RectF(cx - r * 0.75f, cy - r * 0.5f, cx + r * 0.75f, cy + r * 0.5f)
                canvas.drawRoundRect(box, 3f * density, 3f * density, strokePaint)
            }
            "ASPECT_FIT", "ASPECT_STRETCH", "ASPECT_CROP", "ASPECT_16_9" -> {
                val frame = RectF(cx - r * 0.85f, cy - r * 0.58f, cx + r * 0.85f, cy + r * 0.58f)
                canvas.drawRoundRect(frame, 3.5f * density, 3.5f * density, strokePaint)
                if (type == "ASPECT_STRETCH") {
                    canvas.drawLine(cx - r * 0.45f, cy, cx + r * 0.45f, cy, strokePaint)
                } else if (type == "ASPECT_CROP") {
                    canvas.drawCircle(cx, cy, r * 0.26f, fillPaint)
                }
            }
            "PIP_WINDOW" -> {
                val outer = RectF(cx - r * 0.85f, cy - r * 0.6f, cx + r * 0.85f, cy + r * 0.6f)
                canvas.drawRoundRect(outer, 3.5f * density, 3.5f * density, strokePaint)
                val inner = RectF(cx + r * 0.05f, cy - r * 0.05f, cx + r * 0.68f, cy + r * 0.45f)
                canvas.drawRoundRect(inner, 2f * density, 2f * density, fillPaint)
            }
            "BG_VINYL_DISC" -> {
                fillPaint.color = Color.parseColor("#1E1E2A")
                canvas.drawCircle(cx, cy, w * 0.46f, fillPaint)
                strokePaint.color = Color.parseColor("#343446")
                canvas.drawCircle(cx, cy, w * 0.36f, strokePaint)
                canvas.drawCircle(cx, cy, w * 0.28f, strokePaint)
                fillPaint.color = iconColor
                canvas.drawCircle(cx, cy, w * 0.16f, fillPaint)
                fillPaint.color = Color.BLACK
                canvas.drawCircle(cx, cy, w * 0.04f, fillPaint)
            }
        }
    }

    override fun setAlpha(alpha: Int) {
        strokePaint.alpha = alpha
        fillPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        strokePaint.colorFilter = colorFilter
        fillPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
