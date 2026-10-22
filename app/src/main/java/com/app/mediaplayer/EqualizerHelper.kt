package com.app.mediaplayer

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.view.Gravity
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

object EqualizerHelper {

    private var activeEqualizer: Equalizer? = null
    private var activeBassBoost: BassBoost? = null
    private var activeVirtualizer: Virtualizer? = null
    private var boundSessionId: Int = -1

    fun showEqualizerDialog(context: Context, audioSessionId: Int) {
        val d = context.resources.displayMetrics.density
        val prefs = context.getSharedPreferences("MAX_EQ_PREFS", Context.MODE_PRIVATE)
        var isEqEnabled = prefs.getBoolean("eq_enabled", true)

        initAudioEffectsSafely(audioSessionId, isEqEnabled)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * d).toInt(), (18 * d).toInt(), (18 * d).toInt(), (16 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0A0A10"))
                cornerRadius = 24f * d
                setStroke((1.6f * d).toInt(), Color.parseColor("#00FFFF"))
            }
        }

        val dialog = AlertDialog.Builder(context).setView(root).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (12 * d).toInt())
        }

        val tvTitle = TextView(context).apply {
            text = "OG Built-In Dolby Equalizer"
            setTextColor(Color.parseColor("#00FFFF"))
            textSize = 16.5f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val swEnable = Switch(context).apply {
            isChecked = isEqEnabled
            setOnCheckedChangeListener { _, checked ->
                isEqEnabled = checked
                prefs.edit().putBoolean("eq_enabled", checked).apply()
                try {
                    activeEqualizer?.enabled = checked
                    activeBassBoost?.enabled = checked
                    activeVirtualizer?.enabled = checked
                } catch (_: Exception) {
                }
            }
        }

        val btnClose = TextView(context).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((32 * d).toInt(), (32 * d).toInt()).apply {
                marginStart = (8 * d).toInt()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#26FFFFFF"))
            }
            setOnClickListener { dialog.dismiss() }
        }

        headerRow.addView(tvTitle)
        headerRow.addView(swEnable)
        headerRow.addView(btnClose)
        root.addView(headerRow)

        val bandSeekBars = mutableListOf<SeekBar>()
        val bandValueLabels = mutableListOf<TextView>()

        val presetNames = listOf(
            "Flat Reset",
            "8K Cinema Surround",
            "Extreme Bass",
            "Clear Vocals",
            "Dolby Pop",
            "Rock & Metal"
        )
        val presetGains = listOf(
            intArrayOf(50, 50, 50, 50, 50),
            intArrayOf(78, 65, 52, 70, 82),
            intArrayOf(92, 80, 50, 48, 55),
            intArrayOf(42, 58, 84, 78, 60),
            intArrayOf(65, 74, 60, 72, 76),
            intArrayOf(84, 68, 45, 74, 86)
        )

        val hsvPresets = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, 0, 0, (12 * d).toInt())
        }
        val presetRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        presetNames.forEachIndexed { idx, name ->
            val chip = TextView(context).apply {
                text = name
                setTextColor(Color.parseColor("#0A0A10"))
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setPadding((14 * d).toInt(), (7 * d).toInt(), (14 * d).toInt(), (7 * d).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = (8 * d).toInt() }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#00FFFF"))
                    cornerRadius = 16f * d
                }
                setOnClickListener {
                    val gains = presetGains[idx]
                    for (b in 0 until gains.size.coerceAtMost(bandSeekBars.size)) {
                        bandSeekBars[b].progress = gains[b]
                    }
                    Toast.makeText(context, "Preset Applied: $name", Toast.LENGTH_SHORT).show()
                }
            }
            presetRow.addView(chip)
        }
        hsvPresets.addView(presetRow)
        root.addView(hsvPresets)

        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (310 * d).toInt()
            )
        }
        val bandsCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val defaultFreqLabels = arrayOf("60 Hz (Sub-Bass)", "230 Hz (Bass)", "910 Hz (Mid)", "3.6 kHz (Vocal)", "14 kHz (Treble)")

        for (bandIdx in 0 until 5) {
            val savedProg = prefs.getInt("eq_band_$bandIdx", 50).coerceIn(0, 100)

            val rowHeader = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding((4 * d).toInt(), (6 * d).toInt(), (4 * d).toInt(), (2 * d).toInt())
            }
            val tvFreq = TextView(context).apply {
                text = defaultFreqLabels[bandIdx]
                setTextColor(Color.WHITE)
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val dbVal = ((savedProg - 50) * 0.3f)
            val tvDb = TextView(context).apply {
                text = String.format(Locale.US, "%+.1f dB", dbVal)
                setTextColor(Color.parseColor("#00FFFF"))
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
            }
            bandValueLabels.add(tvDb)
            rowHeader.addView(tvFreq)
            rowHeader.addView(tvDb)
            bandsCol.addView(rowHeader)

            val sb = SeekBar(context).apply {
                max = 100
                progress = savedProg
                progressDrawable.setColorFilter(Color.parseColor("#00FFFF"), PorterDuff.Mode.SRC_IN)
                thumb.setColorFilter(Color.parseColor("#00FFFF"), PorterDuff.Mode.SRC_IN)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                        prefs.edit().putInt("eq_band_$bandIdx", p).apply()
                        val db = (p - 50) * 0.3f
                        tvDb.text = String.format(Locale.US, "%+.1f dB", db)
                        applyBandGainToHardware(bandIdx, p)
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            }
            bandSeekBars.add(sb)
            bandsCol.addView(sb)
            applyBandGainToHardware(bandIdx, savedProg)
        }

        // Dedicated Deep Bass Boost Slider
        val savedBass = prefs.getInt("eq_bass_boost", 40).coerceIn(0, 100)
        val tvBassHeader = TextView(context).apply {
            text = "Sub-Woofer Bass Boost ($savedBass%)"
            setTextColor(Color.parseColor("#00E676"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((4 * d).toInt(), (12 * d).toInt(), (4 * d).toInt(), (2 * d).toInt())
        }
        val sbBass = SeekBar(context).apply {
            max = 100
            progress = savedBass
            progressDrawable.setColorFilter(Color.parseColor("#00E676"), PorterDuff.Mode.SRC_IN)
            thumb.setColorFilter(Color.parseColor("#00E676"), PorterDuff.Mode.SRC_IN)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    prefs.edit().putInt("eq_bass_boost", p).apply()
                    tvBassHeader.text = "Sub-Woofer Bass Boost ($p%)"
                    try {
                        activeBassBoost?.setStrength((p * 10).toShort())
                    } catch (_: Exception) {
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        bandsCol.addView(tvBassHeader)
        bandsCol.addView(sbBass)

        // Dedicated 3D Surround Virtualizer Slider
        val savedVirt = prefs.getInt("eq_virtualizer", 35).coerceIn(0, 100)
        val tvVirtHeader = TextView(context).apply {
            text = "3D Surround Spatial Audio ($savedVirt%)"
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((4 * d).toInt(), (10 * d).toInt(), (4 * d).toInt(), (2 * d).toInt())
        }
        val sbVirt = SeekBar(context).apply {
            max = 100
            progress = savedVirt
            progressDrawable.setColorFilter(Color.parseColor("#FFD700"), PorterDuff.Mode.SRC_IN)
            thumb.setColorFilter(Color.parseColor("#FFD700"), PorterDuff.Mode.SRC_IN)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    prefs.edit().putInt("eq_virtualizer", p).apply()
                    tvVirtHeader.text = "3D Surround Spatial Audio ($p%)"
                    try {
                        activeVirtualizer?.setStrength((p * 10).toShort())
                    } catch (_: Exception) {
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        bandsCol.addView(tvVirtHeader)
        bandsCol.addView(sbVirt)

        scroll.addView(bandsCol)
        root.addView(scroll)
        dialog.show()
    }

    private fun initAudioEffectsSafely(sessionId: Int, enabled: Boolean) {
        if (sessionId <= 0) return
        try {
            if (boundSessionId != sessionId || activeEqualizer == null) {
                activeEqualizer?.release()
                activeBassBoost?.release()
                activeVirtualizer?.release()

                activeEqualizer = Equalizer(0, sessionId).apply { this.enabled = enabled }
                activeBassBoost = BassBoost(0, sessionId).apply { this.enabled = enabled }
                activeVirtualizer = Virtualizer(0, sessionId).apply { this.enabled = enabled }
                boundSessionId = sessionId
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyBandGainToHardware(bandIdx: Int, progress0to100: Int) {
        try {
            val eq = activeEqualizer ?: return
            val numBands = eq.numberOfBands.toInt()
            if (bandIdx >= numBands) return
            val range = eq.bandLevelRange
            val minMb = range[0].toInt()
            val maxMb = range[1].toInt()
            val targetMb = minMb + ((maxMb - minMb) * (progress0to100 / 100f)).toInt()
            eq.setBandLevel(bandIdx.toShort(), targetMb.toShort())
        } catch (_: Exception) {
        }
    }
}
