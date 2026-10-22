package com.app.mediaplayer

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import java.util.Locale

object AudioPitchHelper {

    fun showSoundAndPitchLabDialog(context: Context, player: Player?) {
        val d = context.resources.displayMetrics.density
        val prefs = context.getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)

        var currentSpeed = prefs.getFloat("lab_speed", 1.0f)
        var currentPitch = prefs.getFloat("lab_pitch", 1.0f)
        var superLoudBoost = prefs.getBoolean("super_loud_200_boost", false)
        var earSafetyAlert = prefs.getBoolean("ear_safety_80_alert", true)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * d).toInt(), (18 * d).toInt(), (18 * d).toInt(), (16 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#13263B"))
                cornerRadius = 22f * d
                setStroke((1.5f * d).toInt(), Color.parseColor("#5CE1E6"))
            }
        }

        val title = TextView(context).apply {
            text = "OG Sound Lab • Lofi, Pitch & 200% Booster"
            setTextColor(Color.parseColor("#5CE1E6"))
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (12 * d).toInt())
        }
        root.addView(title)

        // One-Click Lofi / Slowed + Reverb / Nightcore Presets Row
        val presetRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            weightSum = 3f
        }

        val tvPitchLabel = TextView(context).apply {
            text = String.format(Locale.US, "Vocal Pitch: %.2fx | Speed: %.2fx", currentPitch, currentSpeed)
            setTextColor(Color.WHITE)
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, (12 * d).toInt(), 0, (6 * d).toInt())
        }

        fun applySpeedAndPitch(speed: Float, pitch: Float, label: String) {
            currentSpeed = speed.coerceIn(0.5f, 2.0f)
            currentPitch = pitch.coerceIn(0.5f, 2.0f)
            prefs.edit().putFloat("lab_speed", currentSpeed).putFloat("lab_pitch", currentPitch).apply()
            player?.playbackParameters = PlaybackParameters(currentSpeed, currentPitch)
            tvPitchLabel.text = String.format(Locale.US, "Vocal Pitch: %.2fx | Speed: %.2fx", currentPitch, currentSpeed)
            Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
        }

        val btnNormal = createPresetButton(context, "Normal 1.0x", d) {
            applySpeedAndPitch(1.0f, 1.0f, "Normal Studio Sound")
        }
        val btnLofi = createPresetButton(context, "Slowed + Lofi", d) {
            applySpeedAndPitch(0.86f, 0.88f, "Slowed + Reverb Lofi Mode Active!")
        }
        val btnChipmunk = createPresetButton(context, "Nightcore", d) {
            applySpeedAndPitch(1.18f, 1.22f, "Nightcore High-Pitch Active!")
        }

        presetRow.addView(btnNormal)
        presetRow.addView(btnLofi)
        presetRow.addView(btnChipmunk)
        root.addView(presetRow)
        root.addView(tvPitchLabel)

        val sbPitch = SeekBar(context).apply {
            max = 150
            progress = ((currentPitch - 0.5f) * 100f).toInt().coerceIn(0, 150)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val newPitch = 0.5f + (p / 100f)
                        applySpeedAndPitch(currentSpeed, newPitch, "Pitch: ${String.format(Locale.US, "%.2fx", newPitch)}")
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        root.addView(sbPitch)

        // 200% Super Loud Volume Booster Toggle
        val rowBoost = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (14 * d).toInt(), 0, (6 * d).toInt())
        }
        val tvBoost = TextView(context).apply {
            text = "200% Super Loud Volume Booster"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val swBoost = Switch(context).apply {
            isChecked = superLoudBoost
            setOnCheckedChangeListener { _, checked ->
                superLoudBoost = checked
                prefs.edit().putBoolean("super_loud_200_boost", checked).apply()
                Toast.makeText(
                    context,
                    if (checked) "200% Volume Booster Enabled" else "Volume Booster Off",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        rowBoost.addView(tvBoost)
        rowBoost.addView(swBoost)
        root.addView(rowBoost)

        // Ear Protection (>80% Volume Safety Alert) Toggle
        val rowEar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
        }
        val tvEar = TextView(context).apply {
            text = "Ear Protection Safety Alert (>80% Vol)"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val swEar = Switch(context).apply {
            isChecked = earSafetyAlert
            setOnCheckedChangeListener { _, checked ->
                earSafetyAlert = checked
                prefs.edit().putBoolean("ear_safety_80_alert", checked).apply()
            }
        }
        rowEar.addView(tvEar)
        rowEar.addView(swEar)
        root.addView(rowEar)

        val dialog = AlertDialog.Builder(context)
            .setView(root)
            .setPositiveButton("Done", null)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun createPresetButton(
        context: Context,
        label: String,
        d: Float,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(Color.BLACK)
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (6 * d).toInt()
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#5CE1E6"))
                cornerRadius = 14f * d
            }
            setOnClickListener { onClick() }
        }
    }
}
