package com.app.mediaplayer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

class SpeedTickRulerView(
    context: Context,
    private val density: Float,
    private val onSpeedChanged: (Float) -> Unit
) : View(context) {

    private var speed = 1.0f

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99FFFFFF")
        strokeWidth = 1.6f * density
        strokeCap = Paint.Cap.ROUND
    }

    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676")
        strokeWidth = 2.6f * density
        strokeCap = Paint.Cap.ROUND
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 10.5f * density
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    fun setSpeedValue(newSpeed: Float) {
        speed = newSpeed.coerceIn(0.25f, 8.0f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val padX = 16f * density
        val usableW = (w - 2 * padX).coerceAtLeast(1f)
        val totalTicks = 40
        val topY = 8f * density
        val bottomMajor = h - 22f * density
        val bottomMinor = h - 30f * density

        for (i in 0..totalTicks) {
            val frac = i.toFloat() / totalTicks.toFloat()
            val x = padX + frac * usableW
            val isMajor = i % 5 == 0
            canvas.drawLine(
                x,
                if (isMajor) topY else topY + 6f * density,
                x,
                if (isMajor) bottomMajor else bottomMinor,
                tickPaint
            )
        }

        val milestones = listOf(
            0.5f to "0.5X",
            1.0f to "1.0X",
            2.0f to "2.0X",
            3.0f to "3.0X",
            4.0f to "4.0X",
            8.0f to "8.0X"
        )
        for ((msVal, text) in milestones) {
            val f = ((msVal - 0.25f) / 7.75f).coerceIn(0f, 1f)
            val x = padX + f * usableW
            canvas.drawText(text, x, h - 4f * density, labelPaint)
        }

        val curFrac = ((speed - 0.25f) / 7.75f).coerceIn(0f, 1f)
        val curX = padX + curFrac * usableW
        canvas.drawLine(curX, 2f * density, curX, bottomMajor + 4f * density, activePaint)
        canvas.drawCircle(curX, 5f * density, 4f * density, activePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                val padX = 16f * density
                val usableW = (width.toFloat() - 2 * padX).coerceAtLeast(1f)
                val frac = ((event.x - padX) / usableW).coerceIn(0f, 1f)
                val rawSpeed = 0.25f + frac * 7.75f
                val rounded = (rawSpeed * 10f).roundToInt() / 10f
                speed = rounded.coerceIn(0.25f, 8.0f)
                invalidate()
                onSpeedChanged(speed)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
