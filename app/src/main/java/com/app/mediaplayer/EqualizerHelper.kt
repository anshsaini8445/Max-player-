package com.app.mediaplayer

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog

class EqualizerHelper(private val context: Context, private val audioSessionId: Int) {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    init {
        try {
            equalizer = Equalizer(0, audioSessionId)
            equalizer?.enabled = true

            bassBoost = BassBoost(0, audioSessionId)
            bassBoost?.enabled = true

            virtualizer = Virtualizer(0, audioSessionId)
            virtualizer?.enabled = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun showEqualizerDialog() {
        if (equalizer == null) return

        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_equalizer, null)
        dialog.setContentView(view)

        val seekBarBass = view.findViewById<SeekBar>(R.id.seekBarBass)
        val seekBarVirtualizer = view.findViewById<SeekBar>(R.id.seekBarVirtualizer)
        val layoutEqualizerBands = view.findViewById<LinearLayout>(R.id.layoutEqualizerBands)
        val btnCloseEqualizer = view.findViewById<Button>(R.id.btnCloseEqualizer)

        // Setup Bass Boost
        if (bassBoost?.strengthSupported == true) {
            seekBarBass.progress = bassBoost?.roundedStrength?.toInt() ?: 0
            seekBarBass.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    try {
                        bassBoost?.setStrength(progress.toShort())
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        } else {
            seekBarBass.isEnabled = false
        }

        // Setup Virtualizer (3D Effect)
        if (virtualizer?.strengthSupported == true) {
            seekBarVirtualizer.progress = virtualizer?.roundedStrength?.toInt() ?: 0
            seekBarVirtualizer.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    try {
                        virtualizer?.setStrength(progress.toShort())
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        } else {
            seekBarVirtualizer.isEnabled = false
        }

        // Setup Dynamic Equalizer Bands
        val numberOfBands = equalizer?.numberOfBands ?: 0
        val minEQLevel = equalizer?.bandLevelRange?.get(0) ?: 0
        val maxEQLevel = equalizer?.bandLevelRange?.get(1) ?: 0

        for (i in 0 until numberOfBands) {
            val bandIndex = i.toShort()
            val freq = (equalizer?.getCenterFreq(bandIndex) ?: 0) / 1000
            
            val bandLayout = LinearLayout(context)
            bandLayout.orientation = LinearLayout.HORIZONTAL
            bandLayout.setPadding(0, 16, 0, 16)

            val freqTextView = TextView(context)
            freqTextView.text = "${freq}Hz"
            freqTextView.width = 150
            freqTextView.setTextColor(android.graphics.Color.WHITE)

            val bandSeekBar = SeekBar(context)
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            bandSeekBar.layoutParams = layoutParams
            bandSeekBar.max = maxEQLevel - minEQLevel
            bandSeekBar.progress = (equalizer?.getBandLevel(bandIndex) ?: 0) - minEQLevel

            bandSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        try {
                            equalizer?.setBandLevel(bandIndex, (progress + minEQLevel).toShort())
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            bandLayout.addView(freqTextView)
            bandLayout.addView(bandSeekBar)
            layoutEqualizerBands.addView(bandLayout)
        }

        btnCloseEqualizer.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    fun release() {
        try {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
