package com.app.mediaplayer

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.cos
import kotlin.math.sin

object RateUsDialogHelper {

    private const val PREF_NAME = "OG_RATE_US_PREFS"
    private const val KEY_PLAY_COUNT = "videos_played_counter"
    private const val KEY_HAS_RATED = "user_has_rated_app"

    // Call this when a video finishes or starts; automatically pops up every 5 videos if not yet rated
    fun recordVideoPlayedAndCheckPrompt(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_HAS_RATED, false)) return
            val count = prefs.getInt(KEY_PLAY_COUNT, 0) + 1
            prefs.edit().putInt(KEY_PLAY_COUNT, count).apply()
            if (count == 5 || count == 15 || count == 30) {
                showPlayItStyleRateUsPopup(context, playedCount = count)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Exact PLAYit-style Rate Us popup with overlapping Pleading Emoji, 5 Interactive Stars, Submit & Exit
    fun showPlayItStyleRateUsPopup(context: Context, playedCount: Int = 5) {
        val d = context.resources.displayMetrics.density
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        var selectedStars = 0

        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val outerWrapper = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                (320 * d).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            clipChildren = false
            clipToPadding = false
        }

        // Cream / Warm Card matching the screenshot
        val cardBody = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((22 * d).toInt(), (48 * d).toInt(), (22 * d).toInt(), (20 * d).toInt())
            layoutParams = FrameLayout.LayoutParams(
                (310 * d).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL
            ).apply {
                topMargin = (38 * d).toInt()
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FFF6DF"))
                cornerRadius = 16f * d
            }
        }

        // Top-Right Close 'X' inside Card
        val closeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (-34 * d).toInt() }
        }

        val btnCloseX = TextView(context).apply {
            text = "✕"
            setTextColor(Color.parseColor("#7A7A7A"))
            textSize = 18f
            setPadding((8 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt())
            setOnClickListener { dialog.dismiss() }
        }
        closeRow.addView(btnCloseX)
        cardBody.addView(closeRow)

        val tvTitle = TextView(context).apply {
            text = "Rate us"
            setTextColor(Color.parseColor("#6E3219"))
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, (6 * d).toInt(), 0, (10 * d).toInt())
        }
        cardBody.addView(tvTitle)

        val tvDesc = TextView(context).apply {
            text = "You have played $playedCount videos with OG 8K Player!\nWe hope that you have been having a good time! If you like us, please give us 5 stars on Google Play."
            setTextColor(Color.parseColor("#8D6E63"))
            textSize = 13.5f
            gravity = Gravity.CENTER
            setLineSpacing(3f * d, 1f)
            setPadding((4 * d).toInt(), 0, (4 * d).toInt(), (16 * d).toInt())
        }
        cardBody.addView(tvDesc)

        // 5 Interactive Clickable Stars Row
        val starsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (20 * d).toInt() }
        }

        val starViews = mutableListOf<ImageView>()
        lateinit var btnSubmit: TextView

        fun refreshStarSelection(stars: Int) {
            selectedStars = stars
            starViews.forEachIndexed { idx, img ->
                img.setImageDrawable(StarVectorDrawable(d, isFilled = idx < selectedStars))
            }
            btnSubmit.background = GradientDrawable().apply {
                setColor(
                    if (selectedStars > 0) Color.parseColor("#FF8F00")
                    else Color.parseColor("#9E9E9E")
                )
                cornerRadius = 24f * d
            }
        }

        for (i in 1..5) {
            val starImg = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams((38 * d).toInt(), (38 * d).toInt()).apply {
                    setMargins((5 * d).toInt(), 0, (5 * d).toInt(), 0)
                }
                setImageDrawable(StarVectorDrawable(d, isFilled = false))
                setOnClickListener {
                    refreshStarSelection(i)
                }
            }
            starViews.add(starImg)
            starsRow.addView(starImg)
        }
        cardBody.addView(starsRow)

        // Pill Submit Button
        btnSubmit = TextView(context).apply {
            text = "Submit"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((24 * d).toInt(), (11 * d).toInt(), (24 * d).toInt(), (11 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                (190 * d).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#9E9E9E"))
                cornerRadius = 24f * d
            }
            setOnClickListener {
                if (selectedStars == 0) {
                    Toast.makeText(context, "Please tap a star to rate!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                prefs.edit().putBoolean(KEY_HAS_RATED, true).apply()
                dialog.dismiss()
                if (selectedStars >= 4) {
                    openGooglePlayStoreListing(context)
                } else {
                    Toast.makeText(
                        context,
                        "Thank you for your feedback! We will improve OG 8K Player.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        cardBody.addView(btnSubmit)

        // Exit Button below Submit
        val btnExit = TextView(context).apply {
            text = "Exit"
            setTextColor(Color.parseColor("#795548"))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((20 * d).toInt(), (12 * d).toInt(), (20 * d).toInt(), (4 * d).toInt())
            setOnClickListener { dialog.dismiss() }
        }
        cardBody.addView(btnExit)

        // Overlapping Pleading Emoji Graphic at Top Center (Matches Screenshot)
        val emojiIcon = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                (76 * d).toInt(),
                (76 * d).toInt(),
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            )
            setImageDrawable(PleadingEmojiDrawable(d))
        }

        outerWrapper.addView(cardBody)
        outerWrapper.addView(emojiIcon)

        dialog.setContentView(outerWrapper)
        dialog.show()
    }

    fun openGooglePlayStoreListing(context: Context) {
        val pkg = context.packageName
        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(marketIntent)
        } catch (e: ActivityNotFoundException) {
            try {
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$pkg")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            } catch (_: Exception) {
                Toast.makeText(context, "Thank you for rating OG 8K Player 5 Stars!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private class StarVectorDrawable(
        private val d: Float,
        private val isFilled: Boolean
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = if (isFilled) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
            strokeWidth = 2.0f * d
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            color = if (isFilled) Color.parseColor("#FFA000") else Color.parseColor("#8D5B4C")
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val outerR = b.width().coerceAtMost(b.height()) * 0.42f
            val innerR = outerR * 0.44f

            val path = Path()
            for (i in 0 until 10) {
                val angleDeg = -90.0 + i * 36.0
                val rad = Math.toRadians(angleDeg)
                val r = if (i % 2 == 0) outerR else innerR
                val x = (cx + cos(rad) * r).toFloat()
                val y = (cy + sin(rad) * r).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            canvas.drawPath(path, paint)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private class PleadingEmojiDrawable(private val d: Float) : Drawable() {
        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val r = b.width().coerceAtMost(b.height()) * 0.46f

            // Yellow Emoji Face
            val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#FFCA28")
            }
            canvas.drawCircle(cx, cy, r, facePaint)

            // Warm Blush Cheeks
            val blushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#44FF5252")
            }
            canvas.drawCircle(cx - r * 0.52f, cy + r * 0.18f, r * 0.22f, blushPaint)
            canvas.drawCircle(cx + r * 0.52f, cy + r * 0.18f, r * 0.22f, blushPaint)

            // Big Glossy Pleading Eyes
            val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#3E2723")
            }
            val leftEyeX = cx - r * 0.34f
            val rightEyeX = cx + r * 0.34f
            val eyeY = cy - r * 0.05f
            val eyeR = r * 0.23f
            canvas.drawCircle(leftEyeX, eyeY, eyeR, eyePaint)
            canvas.drawCircle(rightEyeX, eyeY, eyeR, eyePaint)

            // White Shine Reflections in Eyes
            val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.WHITE
            }
            canvas.drawCircle(leftEyeX - eyeR * 0.28f, eyeY - eyeR * 0.28f, eyeR * 0.38f, shinePaint)
            canvas.drawCircle(rightEyeX - eyeR * 0.28f, eyeY - eyeR * 0.28f, eyeR * 0.38f, shinePaint)
            canvas.drawCircle(leftEyeX + eyeR * 0.32f, eyeY + eyeR * 0.32f, eyeR * 0.16f, shinePaint)
            canvas.drawCircle(rightEyeX + eyeR * 0.32f, eyeY + eyeR * 0.32f, eyeR * 0.16f, shinePaint)

            // Raised Pleading Eyebrows & Small Mouth
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.6f * d
                strokeCap = Paint.Cap.ROUND
                color = Color.parseColor("#5D4037")
            }
            canvas.drawLine(cx - r * 0.55f, cy - r * 0.36f, cx - r * 0.18f, cy - r * 0.46f, linePaint)
            canvas.drawLine(cx + r * 0.18f, cy - r * 0.46f, cx + r * 0.55f, cy - r * 0.36f, linePaint)

            val mouthRect = RectF(cx - r * 0.18f, cy + r * 0.36f, cx + r * 0.18f, cy + r * 0.52f)
            canvas.drawArc(mouthRect, 200f, 140f, false, linePaint)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
