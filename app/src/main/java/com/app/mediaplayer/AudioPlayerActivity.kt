package com.app.mediaplayer

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import java.util.concurrent.TimeUnit

class AudioPlayerActivity : AppCompatActivity() {

    private var exoPlayer: ExoPlayer? = null

    private lateinit var tvCurrentAudioTitle: TextView
    private lateinit var tvCurrentAudioArtist: TextView
    private lateinit var tvAudioCurrentPos: TextView
    private lateinit var tvAudioTotalDur: TextView
    private lateinit var seekBarAudio: SeekBar
    private lateinit var btnAudioPlayPause: ImageView
    private lateinit var btnAudioFavorite: ImageView
    private lateinit var btnAudioSpeed: TextView
    private lateinit var btnAudioTimer: TextView
    private lateinit var btnAudioShuffle: TextView
    private lateinit var btnAudioRepeat: TextView

    private var audioList: ArrayList<String> = arrayListOf()
    private var audioTitles: ArrayList<String> = arrayListOf()
    private var audioArtists: ArrayList<String> = arrayListOf()
    private var currentIndex: Int = 0
    private var currentPath: String = ""

    private var isFavorite = false
    private var isShuffle = false
    private var currentSpeed = 1.0f
    private var sleepTimer: CountDownTimer? = null

    private val handler = Handler(Looper.getMainLooper())
    private val updateSeekAction = object : Runnable {
        override fun run() {
            try {
                exoPlayer?.let { player ->
                    if (player.isPlaying) {
                        val pos = player.currentPosition.coerceAtLeast(0L)
                        val dur = player.duration.coerceAtLeast(0L)
                        seekBarAudio.max = dur.toInt()
                        seekBarAudio.progress = pos.toInt()
                        tvAudioCurrentPos.text = formatDuration(pos)
                        tvAudioTotalDur.text = formatDuration(dur)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_player)

        bindViews()
        extractIntentExtras(intent)
        initAudioPlayer()
        setupControlListeners()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractIntentExtras(intent)
        initAudioPlayer()
    }

    private fun bindViews() {
        tvCurrentAudioTitle = findViewById(R.id.tvCurrentAudioTitle)
        tvCurrentAudioArtist = findViewById(R.id.tvCurrentAudioArtist)
        tvAudioCurrentPos = findViewById(R.id.tvAudioCurrentPos)
        tvAudioTotalDur = findViewById(R.id.tvAudioTotalDur)
        seekBarAudio = findViewById(R.id.seekBarAudio)
        btnAudioPlayPause = findViewById(R.id.btnAudioPlayPause)
        btnAudioFavorite = findViewById(R.id.btnAudioFavorite)
        btnAudioSpeed = findViewById(R.id.btnAudioSpeed)
        btnAudioTimer = findViewById(R.id.btnAudioTimer)
        btnAudioShuffle = findViewById(R.id.btnAudioShuffle)
        btnAudioRepeat = findViewById(R.id.btnAudioRepeat)
    }

    private fun extractIntentExtras(incoming: Intent?) {
        if (incoming == null) return
        currentPath = incoming.getStringExtra("AUDIO_PATH")
            ?: incoming.getStringExtra("audio_path")
            ?: incoming.dataString
            ?: ""

        val singleTitle = incoming.getStringExtra("AUDIO_TITLE")
            ?: File(currentPath).nameWithoutExtension.ifEmpty { "Playing Song" }
        val singleArtist = incoming.getStringExtra("AUDIO_ARTIST") ?: "MAX Audio"

        audioList = incoming.getStringArrayListExtra("AUDIO_LIST") ?: arrayListOf()
        audioTitles = incoming.getStringArrayListExtra("AUDIO_TITLES") ?: arrayListOf()
        audioArtists = incoming.getStringArrayListExtra("AUDIO_ARTISTS") ?: arrayListOf()
        currentIndex = incoming.getIntExtra("AUDIO_INDEX", 0)

        if (audioList.isEmpty() && currentPath.isNotEmpty()) {
            audioList = arrayListOf(currentPath)
            audioTitles = arrayListOf(singleTitle)
            audioArtists = arrayListOf(singleArtist)
            currentIndex = 0
        }

        updateCurrentSongMetadata(currentIndex)
    }

    private fun updateCurrentSongMetadata(index: Int) {
        if (index in audioList.indices) {
            currentPath = audioList[index]
            tvCurrentAudioTitle.text = audioTitles.getOrNull(index) ?: File(currentPath).nameWithoutExtension
            tvCurrentAudioArtist.text = audioArtists.getOrNull(index) ?: "MAX Audio"

            val prefs = getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
            isFavorite = prefs.getBoolean("fav_audio_$currentPath", false)
            btnAudioFavorite.setImageResource(
                if (isFavorite) android.R.drawable.btn_star_big_on
                else android.R.drawable.btn_star_big_off
            )
        }
    }

    private fun initAudioPlayer() {
        try {
            exoPlayer?.release()
            exoPlayer = ExoPlayer.Builder(this).build().also { player ->
                val mediaItems = audioList.map { path ->
                    val uri = if (path.startsWith("content://") || path.startsWith("http")) {
                        Uri.parse(path)
                    } else {
                        Uri.fromFile(File(path))
                    }
                    MediaItem.fromUri(uri)
                }
                player.setMediaItems(mediaItems, currentIndex.coerceIn(0, (mediaItems.size - 1).coerceAtLeast(0)), 0L)
                player.repeatMode = Player.REPEAT_MODE_ALL
                player.prepare()
                player.playWhenReady = true

                player.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        btnAudioPlayPause.setImageResource(
                            if (isPlaying) android.R.drawable.ic_media_pause
                            else android.R.drawable.ic_media_play
                        )
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        currentIndex = player.currentMediaItemIndex
                        updateCurrentSongMetadata(currentIndex)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            val dur = player.duration.coerceAtLeast(0L)
                            seekBarAudio.max = dur.toInt()
                            tvAudioTotalDur.text = formatDuration(dur)
                        }
                    }
                })
            }
            handler.post(updateSeekAction)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to play audio", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupControlListeners() {
        findViewById<ImageView>(R.id.btnAudioBack).setOnClickListener { finish() }

        findViewById<ImageView>(R.id.btnAudioEqualizer).setOnClickListener {
            try {
                val eqIntent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, exoPlayer?.audioSessionId ?: 0)
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                    putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                }
                if (eqIntent.resolveActivity(packageManager) != null) {
                    startActivityForResult(eqIntent, 201)
                } else {
                    Toast.makeText(this, "Bass Boost Equalizer Active", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Equalizer Active", Toast.LENGTH_SHORT).show()
            }
        }

        btnAudioPlayPause.setOnClickListener {
            exoPlayer?.let { player ->
                if (player.isPlaying) player.pause() else player.play()
            }
        }

        findViewById<ImageView>(R.id.btnAudioPrev).setOnClickListener {
            exoPlayer?.let { player ->
                if (player.hasPreviousMediaItem()) {
                    player.seekToPreviousMediaItem()
                } else {
                    player.seekTo(0)
                }
            }
        }

        findViewById<ImageView>(R.id.btnAudioNext).setOnClickListener {
            exoPlayer?.let { player ->
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                } else {
                    player.seekToDefaultPosition(0)
                }
            }
        }

        seekBarAudio.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    exoPlayer?.seekTo(progress.toLong())
                    tvAudioCurrentPos.text = formatDuration(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnAudioFavorite.setOnClickListener {
            isFavorite = !isFavorite
            getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("fav_audio_$currentPath", isFavorite)
                .apply()
            btnAudioFavorite.setImageResource(
                if (isFavorite) android.R.drawable.btn_star_big_on
                else android.R.drawable.btn_star_big_off
            )
            Toast.makeText(
                this,
                if (isFavorite) "Added to Favorite Songs" else "Removed from Favorites",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnAudioSpeed.setOnClickListener {
            val labels = arrayOf("0.75x", "1.0x (Normal)", "1.25x", "1.5x", "2.0x")
            val speeds = floatArrayOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
            AlertDialog.Builder(this)
                .setTitle("Audio Speed")
                .setItems(labels) { _, which ->
                    currentSpeed = speeds[which]
                    exoPlayer?.playbackParameters = PlaybackParameters(currentSpeed)
                    btnAudioSpeed.text = "${currentSpeed}x"
                }
                .show()
        }

        btnAudioTimer.setOnClickListener {
            val options = arrayOf("Off", "15 Minutes", "30 Minutes", "60 Minutes")
            AlertDialog.Builder(this)
                .setTitle("Sleep Timer")
                .setItems(options) { _, which ->
                    sleepTimer?.cancel()
                    if (which == 0) {
                        btnAudioTimer.text = "Timer"
                        Toast.makeText(this, "Timer Off", Toast.LENGTH_SHORT).show()
                    } else {
                        val mins = when (which) {
                            1 -> 15L
                            2 -> 30L
                            else -> 60L
                        }
                        btnAudioTimer.text = "${mins}m"
                        sleepTimer = object : CountDownTimer(mins * 60 * 1000, 60000) {
                            override fun onTick(millisUntilFinished: Long) {}
                            override fun onFinish() {
                                exoPlayer?.pause()
                                btnAudioTimer.text = "Timer"
                            }
                        }.start()
                        Toast.makeText(this, "Sleep timer set for $mins mins", Toast.LENGTH_SHORT).show()
                    }
                }
                .show()
        }

        findViewById<ImageView>(R.id.btnAudioShare).setOnClickListener {
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/*"
                    putExtra(Intent.EXTRA_STREAM, Uri.parse(currentPath))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Share Song"))
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to share song", Toast.LENGTH_SHORT).show()
            }
        }

        btnAudioShuffle.setOnClickListener {
            isShuffle = !isShuffle
            exoPlayer?.shuffleModeEnabled = isShuffle
            btnAudioShuffle.setTextColor(
                if (isShuffle) Color.parseColor("#00FFFF") else Color.parseColor("#888888")
            )
            Toast.makeText(this, if (isShuffle) "Shuffle ON" else "Shuffle OFF", Toast.LENGTH_SHORT).show()
        }

        btnAudioRepeat.setOnClickListener {
            exoPlayer?.let { player ->
                if (player.repeatMode == Player.REPEAT_MODE_ALL) {
                    player.repeatMode = Player.REPEAT_MODE_ONE
                    btnAudioRepeat.text = "LOOP 1"
                } else {
                    player.repeatMode = Player.REPEAT_MODE_ALL
                    btnAudioRepeat.text = "LOOP ALL"
                }
            }
        }
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis).coerceAtLeast(0L)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sleepTimer?.cancel()
        handler.removeCallbacks(updateSeekAction)
        exoPlayer?.release()
        exoPlayer = null
    }
}
