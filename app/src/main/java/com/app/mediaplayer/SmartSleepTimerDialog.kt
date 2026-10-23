package com.app.mediaplayer

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

object SmartSleepTimerDialog {

    fun showSleepTimerPicker(
        context: Context,
        onStopTimer: () -> Unit,
        onStartTimerMinutes: (minutes: Long, enableMotionIdleReset: Boolean) -> Unit
    ) {
        val d = context.resources.displayMetrics.density
        val rootCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * d).toInt(), (18 * d).toInt(), (18 * d).toInt(), (16 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0A0A10"))
                cornerRadius = 22f * d
                setStroke((1.6f * d).toInt(), Color.parseColor("#00FFFF"))
            }
        }

        val dialog = AlertDialog.Builder(context).setView(rootCard).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((6 * d).toInt(), 0, (4 * d).toInt(), (12 * d).toInt())
        }

        val tvHeader = TextView(context).apply {
            text = "⏰  Smart Sleep Timer"
            setTextColor(Color.parseColor("#00FFFF"))
            textSize = 16.5f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnClose = TextView(context).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((30 * d).toInt(), (30 * d).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#26FFFFFF"))
            }
            setOnClickListener { dialog.dismiss() }
        }

        headerRow.addView(tvHeader)
        headerRow.addView(btnClose)
        rootCard.addView(headerRow)

        val scroll = ScrollView(context)
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val presets = listOf(
            Triple("🚫  Timer Off", 0L, false),
            Triple("🌙  15 Minutes (Smooth 15s Fade-Out)", 15L, false),
            Triple("🛌  25 Minutes (Smart Motion Idle Sleep)", 25L, true),
            Triple("🛌  30 Minutes (Smart Motion Idle Sleep)", 30L, true),
            Triple("🌙  60 Minutes (Smooth 15s Fade-Out)", 60L, false),
            Triple("🌙  90 Minutes (Cinema & Long Playlist)", 90L, false)
        )

        presets.forEach { (label, mins, motionMode) ->
            val tvRow = TextView(context).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 14.5f
                typeface = Typeface.DEFAULT_BOLD
                setPadding((10 * d).toInt(), (13 * d).toInt(), (10 * d).toInt(), (13 * d).toInt())
                setOnClickListener {
                    dialog.dismiss()
                    if (mins <= 0L) {
                        onStopTimer()
                        Toast.makeText(context, "Sleep Timer Off", Toast.LENGTH_SHORT).show()
                    } else {
                        onStartTimerMinutes(mins, motionMode)
                        Toast.makeText(context, "Sleep Timer set for $mins minutes", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            col.addView(tvRow)
            col.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (1 * d).toInt()
                )
                setBackgroundColor(Color.parseColor("#18FFFFFF"))
            })
        }

        // Custom Minute Input Box (Allows 2m, 5m, 75m, 80m, or any custom duration)
        val customBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt(), (6 * d).toInt())
        }

        val tvCustomLabel = TextView(context).apply {
            text = "⌨️  Set Custom Minutes (e.g. 2, 5, 75, 80 mins):"
            setTextColor(Color.parseColor("#00FFFF"))
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (8 * d).toInt())
        }

        val inputRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val etMinutes = EditText(context).apply {
            hint = "Enter minutes (1 - 480)"
            setHintTextColor(Color.parseColor("#778899"))
            setTextColor(Color.WHITE)
            textSize = 14f
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (10 * d).toInt()
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141C2B"))
                cornerRadius = 14f * d
                setStroke((1.2f * d).toInt(), Color.parseColor("#00FFFF"))
            }
        }

        val btnStartCustom = TextView(context).apply {
            text = "Start"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((18 * d).toInt(), (11 * d).toInt(), (18 * d).toInt(), (11 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#00FFFF"))
                cornerRadius = 14f * d
            }
            setOnClickListener {
                val mins = etMinutes.text.toString().trim().toLongOrNull()
                if (mins == null || mins < 1L) {
                    Toast.makeText(context, "Please enter valid minutes (e.g. 5 or 75)", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val safeMins = mins.coerceAtMost(720L)
                dialog.dismiss()
                onStartTimerMinutes(safeMins, false)
                Toast.makeText(context, "Custom Sleep Timer set for $safeMins minutes", Toast.LENGTH_SHORT).show()
            }
        }

        inputRow.addView(etMinutes)
        inputRow.addView(btnStartCustom)
        customBox.addView(tvCustomLabel)
        customBox.addView(inputRow)
        col.addView(customBox)

        scroll.addView(col)
        rootCard.addView(scroll)
        dialog.show()
    }
}
