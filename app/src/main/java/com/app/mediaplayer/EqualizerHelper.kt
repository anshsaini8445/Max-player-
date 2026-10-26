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
import android.media.audiofx.LoudnessEnhancer
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

object EqualizerHelper {

    private const val PREF_EQ = "HYBRID_EQUALIZER_PREFS"
    private var activeEqualizer: Equalizer? = null
    private var activeBassBoost: BassBoost? = null
    private var activeVirtualizer: Virtualizer? = null
    private var activeLoudness: LoudnessEnhancer? = null

    fun showEqualizerDialog(context: Context, audioSessionId: Int) {
        val d = context.resources.displayMetrics.density
        val prefs = context.getSharedPreferences(PREF_EQ, Context.MODE_PRIVATE)
        val safeSession = if (audioSessionId > 0) audioSessionId else 0

        try {
            activeEqualizer?.release()
            activeEqualizer = Equalizer(0, safeSession).apply {
                enabled = prefs.getBoolean("eq_enabled", true)
            }
        } catch (_: Exception) {
        }

        try {
            activeBassBoost?.release()
            activeBassBoost = BassBoost(0, safeSession).apply {
                enabled = true
            }
        } catch (_: Exception) {
        }

        try {
            activeVirtualizer?.release()
            activeVirtualizer = Virtualizer(0, safeSession).apply {
                enabled = true
            }
        } catch (_: Exception) {
        }

        try {
            activeLoudness?.release()
            activeLoudness = LoudnessEnhancer(safeSession).apply {
                enabled = true
            }
        } catch (_: Exception) {
        }

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

        val tvTitle = TextView(context).apply {
            text = "5-Band Dolby Equalizer & Bass"
            setTextColor(Color.WHITE)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val swMaster = Switch(context).apply {
            isChecked = prefs.getBoolean("eq_enabled", true)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("eq_enabled", isChecked).apply()
                try {
                    activeEqualizer?.enabled = isChecked
                } catch (_: Exception) {
                }
            }
        }

        val btnClose = TextView(context).apply {
            text = "X"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((32 * d).toInt(), (32 * d).toInt()).apply {
                marginStart = (10 * d).toInt()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#28FFFFFF"))
            }
            setOnClickListener { dialog.dismiss() }
        }

        headerRow.addView(tvTitle)
        headerRow.addView(swMaster)
        headerRow.addView(btnClose)
        rootCard.addView(headerRow)

        val scroll = ScrollView(context)
        val bodyCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Preset Chips Row (Pure White Modern Pills)
        val presetsScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (14 * d).toInt() }
        }

        val presetsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val bandSeekBars = mutableListOf<SeekBar>()
        val presets = listOf(
            "Dolby Cinema" to intArrayOf(80, 65, 55, 70, 85),
            "Sub-Woofer Bass" to intArrayOf(95, 80, 50, 55, 65),
            "Clear Vocal" to intArrayOf(45, 55, 85, 80, 60),
            "Rock" to intArrayOf(85, 65, 45, 70, 90),
            "Pop" to intArrayOf(60, 75, 80, 70, 60),
            "Flat / Normal" to intArrayOf(50, 50, 50, 50, 50)
        )

        for ((presetName, levels) in presets) {
            val pill = TextView(context).apply {
                text = presetName
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setPadding((14 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = (8 * d).toInt() }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#22FFFFFF"))
                    cornerRadius = 16f * d
                    setStroke((1 * d).toInt(), Color.WHITE)
                }
                setOnClickListener {
                    bandSeekBars.forEachIndexed { idx, sb ->
                        sb.progress = levels.getOrElse(idx) { 50 }
                    }
                    Toast.makeText(context, "Preset Applied: $presetName", Toast.LENGTH_SHORT).show()
                }
            }
            presetsRow.addView(pill)
        }
        presetsScroll.addView(presetsRow)
        bodyCol.addView(presetsScroll)

        // 5-Band Sliders
        val freqLabels = arrayOf("60 Hz (Sub-Bass)", "230 Hz (Bass)", "910 Hz (Mid)", "3.6 kHz (Presence)", "14 kHz (Brilliance)")
        for (i in 0 until 5) {
            val savedProgress = prefs.getInt("eq_band_$i", 60)
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, (4 * d).toInt(), 0, (8 * d).toInt())
            }

            val labelRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            val tvFreq = TextView(context).apply {
                text = freqLabels[i]
                setTextColor(Color.WHITE)
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val tvDb = TextView(context).apply {
                text = "${(savedProgress - 50) / 3} dB"
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
            }
            labelRow.addView(tvFreq)
            labelRow.addView(tvDb)

            val sb = SeekBar(context).apply {
                max = 100
                progress = savedProgress
                progressDrawable.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                thumb.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                        tvDb.text = "${(p - 50) / 3} dB"
                        prefs.edit().putInt("eq_band_$i", p).apply()
                        applyBandToHardwareEqualizer(i, p)
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            }
            applyBandToHardwareEqualizer(i, savedProgress)
            bandSeekBars.add(sb)
            row.addView(labelRow)
            row.addView(sb)
            bodyCol.addView(row)
        }

        // Sub-Woofer Bass Boost Slider
        val savedBass = prefs.getInt("eq_bass_strength", 55)
        bodyCol.addView(
            createEnhancerSliderRow(
                context = context,
                title = "Sub-Woofer Bass Boost",
                initialPct = savedBass,
                d = d
            ) { pct ->
                prefs.edit().putInt("eq_bass_strength", pct).apply()
                try {
                    activeBassBoost?.setStrength((pct * 10).toShort())
                } catch (_: Exception) {
                }
            }
        )

        // 3D Surround Virtualizer Slider
        val savedVirt = prefs.getInt("eq_virtualizer_strength", 50)
        bodyCol.addView(
            createEnhancerSliderRow(
                context = context,
                title = "3D Surround Sound Virtualizer",
                initialPct = savedVirt,
                d = d
            ) { pct ->
                prefs.edit().putInt("eq_virtualizer_strength", pct).apply()
                try {
                    activeVirtualizer?.setStrength((pct * 10).toShort())
                } catch (_: Exception) {
                }
            }
        )

        // 200% Loudness Pre-Amp Gain Slider
        val savedGain = prefs.getInt("eq_loudness_gain_pct", 40)
        bodyCol.addView(
            createEnhancerSliderRow(
                context = context,
                title = "200% Pre-Amp Dialogue & Loudness Boost",
                initialPct = savedGain,
                d = d
            ) { pct ->
                prefs.edit().putInt("eq_loudness_gain_pct", pct).apply()
                try {
                    activeLoudness?.setTargetGain(pct * 24)
                } catch (_: Exception) {
                }
            }
        )

        scroll.addView(bodyCol)
        rootCard.addView(scroll)
        dialog.show()
    }

    private fun applyBandToHardwareEqualizer(bandIndex: Int, percent: Int) {
        try {
            val eq = activeEqualizer ?: return
            val numBands = eq.numberOfBands.toInt()
            if (bandIndex >= numBands) return
            val range = eq.bandLevelRange
            val minMb = range[0].toInt()
            val maxMb = range[1].toInt()
            val targetMb = (minMb + ((maxMb - minMb) * (percent / 100f))).toInt().toShort()
            eq.setBandLevel(bandIndex.toShort(), targetMb)
        } catch (_: Exception) {
        }
    }

    private fun createEnhancerSliderRow(
        context: Context,
        title: String,
        initialPct: Int,
        d: Float,
        onChanged: (Int) -> Unit
    ): LinearLayout {
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (8 * d).toInt(), 0, (6 * d).toInt())
        }
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val tvTitle = TextView(context).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvPct = TextView(context).apply {
            text = "$initialPct%"
            setTextColor(Color.WHITE)
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
        }
        topRow.addView(tvTitle)
        topRow.addView(tvPct)

        val sb = SeekBar(context).apply {
            max = 100
            progress = initialPct
            progressDrawable.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            thumb.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    tvPct.text = "$p%"
                    onChanged(p)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        onChanged(initialPct)
        col.addView(topRow)
        col.addView(sb)
        return col
    }
}
