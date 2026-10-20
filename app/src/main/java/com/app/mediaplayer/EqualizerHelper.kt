package com.app.mediaplayer

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

object EqualizerHelper {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var lastSessionId: Int = -1

    fun showEqualizerDialog(context: Context, audioSessionId: Int) {
        val d = context.resources.displayMetrics.density
        val prefs = context.getSharedPreferences("MAX_EQ_PREFS", Context.MODE_PRIVATE)

        try {
            if (audioSessionId != 0 && audioSessionId != lastSessionId) {
                equalizer?.release()
                bassBoost?.release()
                equalizer = Equalizer(0, audioSessionId).apply { enabled = true }
                bassBoost = BassBoost(0, audioSessionId).apply { enabled = true }
                lastSessionId = audioSessionId
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * d).toInt(), (18 * d).toInt(), (18 * d).toInt(), (16 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#161624"))
                cornerRadius = 22f * d
                setStroke((1.5f * d).toInt(), Color.parseColor("#00FFFF"))
            }
        }

        val tvTitle = TextView(context).apply {
            text = "5-Band Dolby Equalizer & Bass"
            setTextColor(Color.parseColor("#00FFFF"))
            textSize = 16.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (12 * d).toInt())
        }
        root.addView(tvTitle)

        // Preset Row
        val presetRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (12 * d).toInt())
        }
        val presetNames = listOf("Normal", "Bass", "Vocal", "Rock")
        val bandSeekBars = mutableListOf<SeekBar>()

        for ((idx, pName) in presetNames.withIndex()) {
            val pill = TextView(context).apply {
                text = pName
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setPadding((12 * d).toInt(), (6 * d).toInt(), (12 * d).toInt(), (6 * d).toInt())
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#28FFFFFF"))
                    cornerRadius = 14f * d
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = (6 * d).toInt() }
                setOnClickListener {
                    val targetValues = when (idx) {
                        1 -> intArrayOf(85, 75, 50, 60, 70)
                        2 -> intArrayOf(40, 60, 85, 80, 65)
                        3 -> intArrayOf(80, 65, 45, 75, 85)
                        else -> intArrayOf(50, 50, 50, 50, 50)
                    }
                    bandSeekBars.forEachIndexed { bIdx, sb ->
                        sb.progress = targetValues.getOrElse(bIdx) { 50 }
                    }
                    Toast.makeText(context, "Preset: $pName", Toast.LENGTH_SHORT).show()
                }
            }
            presetRow.addView(pill)
        }
        root.addView(presetRow)

        // 5 Frequency Bands
        val freqLabels = arrayOf("60Hz (Sub-Bass)", "230Hz (Bass)", "910Hz (Mid)", "3.6kHz (Treble)", "14kHz (Air)")
        for (bandIndex in 0 until 5) {
            val savedProg = prefs.getInt("eq_band_$bandIndex", 50)
            val tvBand = TextView(context).apply {
                text = freqLabels[bandIndex]
                setTextColor(Color.WHITE)
                textSize = 12f
                setPadding(0, (6 * d).toInt(), 0, (2 * d).toInt())
            }
            val sb = SeekBar(context).apply {
                max = 100
                progress = savedProg
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                        prefs.edit().putInt("eq_band_$bandIndex", p).apply()
                        applyBandLevel(bandIndex, p)
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            }
            bandSeekBars.add(sb)
            root.addView(tvBand)
            root.addView(sb)
        }

        // Sub-Woofer Bass Boost Slider
        val tvBass = TextView(context).apply {
            text = "Sub-Woofer Bass Boost"
            setTextColor(Color.parseColor("#00E676"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, (10 * d).toInt(), 0, (4 * d).toInt())
        }
        val sbBass = SeekBar(context).apply {
            max = 1000
            progress = prefs.getInt("eq_bass_strength", 350)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    prefs.edit().putInt("eq_bass_strength", p).apply()
                    try {
                        bassBoost?.setStrength(p.toShort())
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        root.addView(tvBass)
        root.addView(sbBass)

        val dialog = AlertDialog.Builder(context)
            .setView(root)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val btnClose = TextView(context).apply {
            text = "DONE"
            setTextColor(Color.BLACK)
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, (10 * d).toInt(), 0, (10 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (14 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#00FFFF"))
                cornerRadius = 16f * d
            }
            setOnClickListener { dialog.dismiss() }
        }
        root.addView(btnClose)
        dialog.show()
    }

    private fun applyBandLevel(bandIndex: Int, progress: Int) {
        try {
            val eq = equalizer ?: return
            val numBands = eq.numberOfBands.toInt()
            if (bandIndex < numBands) {
                val range = eq.bandLevelRange
                val minLvl = range[0].toInt()
                val maxLvl = range[1].toInt()
                val targetLvl = minLvl + ((maxLvl - minLvl) * progress) / 100
                eq.setBandLevel(bandIndex.toShort(), targetLvl.toShort())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
