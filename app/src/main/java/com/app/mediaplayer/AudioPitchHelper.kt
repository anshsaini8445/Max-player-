package com.app.mediaplayer

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import java.util.Locale
import kotlin.math.roundToInt

object AudioPitchHelper {

    private const val PREF_PITCH = "HYBRID_PITCH_LAB_PREFS"

    fun showSoundAndPitchLabDialog(context: Context, player: ExoPlayer?) {
        val d = context.resources.displayMetrics.density
        val prefs = context.getSharedPreferences(PREF_PITCH, Context.MODE_PRIVATE)

        var currentSpeed = player?.playbackParameters?.speed ?: prefs.getFloat("saved_speed", 1.0f)
        var currentPitch = player?.playbackParameters?.pitch ?: prefs.getFloat("saved_pitch", 1.0f)

        val rootCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * d).toInt(), (18 * d).toInt(), (18 * d).toInt(), (16 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F50A0A10"))
                cornerRadius = 24f * d
                setStroke((1.5f * d).toInt(), Color.parseColor("#4DFFFFFF"))
            }
        }

        val dialog = AlertDialog.Builder(context).setView(rootCard).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (12 * d).toInt())
        }

        val tvHeader = TextView(context).apply {
            text = "Sound & Pitch Lab (Lofi / Reverb)"
            setTextColor(Color.WHITE)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnClose = TextView(context).apply {
            text = "X"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((32 * d).toInt(), (32 * d).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#28FFFFFF"))
            }
            setOnClickListener { dialog.dismiss() }
        }

        headerRow.addView(tvHeader)
        headerRow.addView(btnClose)
        rootCard.addView(headerRow)

        lateinit var tvSpeedLabel: TextView
        lateinit var tvPitchLabel: TextView
        lateinit var sbSpeed: SeekBar
        lateinit var sbPitch: SeekBar

        fun applySpeedAndPitch(sp: Float, pt: Float) {
            currentSpeed = sp.coerceIn(0.25f, 3.0f)
            currentPitch = pt.coerceIn(0.5f, 2.0f)
            prefs.edit()
                .putFloat("saved_speed", currentSpeed)
                .putFloat("saved_pitch", currentPitch)
                .apply()
            try {
                player?.playbackParameters = PlaybackParameters(currentSpeed, currentPitch)
            } catch (_: Exception) {
            }
            tvSpeedLabel.text = String.format(Locale.US, "Tempo / Speed: %.2fx", currentSpeed)
            tvPitchLabel.text = String.format(Locale.US, "Voice / Music Pitch: %.2fx", currentPitch)
        }

        // Studio Presets Grid (All White Modern Tiles)
        val presetsGrid = GridLayout(context).apply {
            columnCount = 2
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (14 * d).toInt() }
        }

        val studioModes = listOf(
            Triple("Normal (1.0x)", 1.0f, 1.0f),
            Triple("Lofi Slowed + Reverb", 0.84f, 0.86f),
            Triple("Nightcore Aesthetic", 1.24f, 1.22f),
            Triple("Deep Cinema Bass Voice", 1.0f, 0.78f),
            Triple("Soft Acoustic Warm", 0.94f, 0.95f),
            Triple("Fast Podcast (1.5x)", 1.5f, 1.0f)
        )

        for ((label, sp, pt) in studioModes) {
            val btn = TextView(context).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding((10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt())
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins((4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt())
                }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1EFFFFFF"))
                    cornerRadius = 14f * d
                    setStroke((1 * d).toInt(), Color.WHITE)
                }
                setOnClickListener {
                    sbSpeed.progress = ((sp - 0.25f) * 100f).roundToInt().coerceIn(0, 275)
                    sbPitch.progress = ((pt - 0.5f) * 100f).roundToInt().coerceIn(0, 150)
                    applySpeedAndPitch(sp, pt)
                    Toast.makeText(context, "Studio Mode: $label", Toast.LENGTH_SHORT).show()
                }
            }
            presetsGrid.addView(btn)
        }
        rootCard.addView(presetsGrid)

        tvSpeedLabel = TextView(context).apply {
            text = String.format(Locale.US, "Tempo / Speed: %.2fx", currentSpeed)
            setTextColor(Color.WHITE)
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, (4 * d).toInt(), 0, (4 * d).toInt())
        }
        rootCard.addView(tvSpeedLabel)

        sbSpeed = SeekBar(context).apply {
            max = 275
            progress = ((currentSpeed - 0.25f) * 100f).roundToInt().coerceIn(0, 275)
            progressDrawable.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            thumb.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val sp = 0.25f + (p / 100f)
                        applySpeedAndPitch(sp, currentPitch)
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        rootCard.addView(sbSpeed)

        tvPitchLabel = TextView(context).apply {
            text = String.format(Locale.US, "Voice / Music Pitch: %.2fx", currentPitch)
            setTextColor(Color.WHITE)
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, (12 * d).toInt(), 0, (4 * d).toInt())
        }
        rootCard.addView(tvPitchLabel)

        sbPitch = SeekBar(context).apply {
            max = 150
            progress = ((currentPitch - 0.5f) * 100f).roundToInt().coerceIn(0, 150)
            progressDrawable.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            thumb.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val pt = 0.5f + (p / 100f)
                        applySpeedAndPitch(currentSpeed, pt)
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        rootCard.addView(sbPitch)

        val btnReset = TextView(context).apply {
            text = "RESET TO NORMAL (1.0x)"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((16 * d).toInt(), (12 * d).toInt(), (16 * d).toInt(), (12 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (16 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 16f * d
            }
            setOnClickListener {
                sbSpeed.progress = 75
                sbPitch.progress = 50
                applySpeedAndPitch(1.0f, 1.0f)
            }
        }
        rootCard.addView(btnReset)

        dialog.show()
    }
}
