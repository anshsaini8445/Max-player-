package com.app.mediaplayer

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.util.Locale
import java.util.concurrent.TimeUnit

class AudioPlayerActivity : AppCompatActivity() {

    private var exoPlayer: ExoPlayer? = null
    private lateinit var btnPlayPause: ImageView
    private lateinit var btnEqualizer: ImageView
    private lateinit var seekBarAudio: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    
    private var equalizerHelper: EqualizerHelper? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isUserSeeking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_player)

        val audioPath = intent.getStringExtra("AUDIO_PATH") ?: return
        val audioTitle = intent.getStringExtra("AUDIO_TITLE") ?: "Unknown Song"
        val audioArtist = intent.getStringExtra("AUDIO_ARTIST") ?: "Unknown Artist"

        findViewById<TextView>(R.id.tvNowPlayingTitle).text = audioTitle
        findViewById<TextView>(R.id.tvNowPlayingArtist).text = audioArtist
        
        findViewById<ImageView>(R.id.btnBackAudio).setOnClickListener {
            finish()
        }

        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnEqualizer = findViewById(R.id.btnEqualizer)
        seekBarAudio = findViewById(R.id.seekBarAudio)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)

        initializeAudioPlayer(audioPath)
        setupControls()
    }

    private fun initializeAudioPlayer(audioPath: String) {
        exoPlayer = ExoPlayer.Builder(this).build()
        val mediaItem = MediaItem.fromUri(Uri.parse(audioPath))
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
        exoPlayer?.playWhenReady = true

        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val duration = exoPlayer?.duration ?: 0L
                    seekBarAudio.max = duration.toInt()
                    tvTotalTime.text = formatTime(duration)
                    
                    // Initialize Equalizer once audio session is ready
                    val sessionId = exoPlayer?.audioSessionId ?: 0
                    if (sessionId != 0 && equalizerHelper == null) {
                        equalizerHelper = EqualizerHelper(this@AudioPlayerActivity, sessionId)
                    }
                    
                    updateSeekBar()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                } else {
                    btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                }
            }
        })
    }

    private fun setupControls() {
        btnPlayPause.setOnClickListener {
            if (exoPlayer?.isPlaying == true) {
                exoPlayer?.pause()
            } else {
                exoPlayer?.play()
            }
        }
        
        btnEqualizer.setOnClickListener {
            equalizerHelper?.showEqualizerDialog()
        }

        seekBarAudio.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    tvCurrentTime.text = formatTime(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = false
                exoPlayer?.seekTo(seekBar?.progress?.toLong() ?: 0L)
            }
        })
    }

    private fun updateSeekBar() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (!isUserSeeking && exoPlayer != null) {
                    val currentPosition = exoPlayer?.currentPosition ?: 0L
                    seekBarAudio.progress = currentPosition.toInt()
                    tvCurrentTime.text = formatTime(currentPosition)
                }
                handler.postDelayed(this, 1000)
            }
        }, 1000)
    }

    private fun formatTime(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes)
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        equalizerHelper?.release()
        exoPlayer?.release()
        exoPlayer = null
    }
}
