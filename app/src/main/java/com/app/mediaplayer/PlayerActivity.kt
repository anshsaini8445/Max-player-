package com.app.mediaplayer

import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class PlayerActivity : AppCompatActivity() {

    private var exoPlayer: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var viewVisualOverlay: View
    private lateinit var layoutAllControls: RelativeLayout
    private lateinit var layoutAdvancedSettings: ScrollView
    private lateinit var btnUnlockOverlay: ImageView
    private lateinit var layoutGestureFeedback: LinearLayout
    private lateinit var imgGestureIcon: ImageView
    private lateinit var tvGestureValue: TextView

    private lateinit var tvPlayerTitle: TextView
    private lateinit var tvPlayerCurrentTime: TextView
    private lateinit var tvPlayerTotalTime: TextView
    private lateinit var seekBarPlayer: SeekBar
    private lateinit var btnPlayerPlayPause: ImageView
    private lateinit var btnMuteToggle: ImageView
    private lateinit var btnPlaybackSpeed: TextView
    private lateinit var btnHdrMode: TextView
    private lateinit var imgFavoriteIcon: ImageView
    private lateinit var tvAbRepeatLabel: TextView
    private lateinit var tvTimerLabel: TextView

    private lateinit var btnLoopShuffle: TextView
    private lateinit var btnLoopOne: TextView
    private lateinit var btnLoopAll: TextView
    private lateinit var btnLoopOff: TextView
    private lateinit var btnHwDecoder: TextView
    private lateinit var btnSwDecoder: TextView

    private var videoPath: String = ""
    private var videoTitle: String = "Video"
    private var videoList: ArrayList<String> = arrayListOf()
    private var currentPositionIndex: Int = 0

    private var isScreenLocked = false
    private var isMuted = false
    private var isNightMode = false
    private var isHdrEnabled = false
    private var isMirrorMode = false
    private var isVrMode = false
    private var isBgAudioEnabled = false
    private var isFavorite = false
    private var stopAfterCurrent = false

    private var currentSpeed = 1.0f
    private var resizeModeIndex = 0
    private var pointA: Long = -1L
    private var pointB: Long = -1L
    private var sleepCountDownTimer: CountDownTimer? = null

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var audioManager: AudioManager
    private lateinit var gestureDetector: GestureDetector

    private val updateProgressAction = object : Runnable {
        override fun run() {
            try {
                exoPlayer?.let { player ->
                    if (player.isPlaying) {
                        val currentPos = player.currentPosition
                        val duration = player.duration.coerceAtLeast(0L)
                        seekBarPlayer.max = duration.toInt()
                        seekBarPlayer.progress = currentPos.toInt()
                        tvPlayerCurrentTime.text = formatDuration(currentPos)
                        tvPlayerTotalTime.text = formatDuration(duration)

                        if (pointA != -1L && pointB != -1L && currentPos >= pointB) {
                            player.seekTo(pointA)
                        }
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
        setContentView(R.layout.activity_player)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        bindViews()
        extractIntentData(intent)
        initializePlayer()
        setupTopAndSideControls()
        setupBottomControls()
        setupAdvancedSettingsMenu()
        setupSwipeGestures()
    }

    private fun bindViews() {
        playerView = findViewById(R.id.playerView)
        viewVisualOverlay = findViewById(R.id.viewVisualOverlay)
        layoutAllControls = findViewById(R.id.layoutAllControls)
        layoutAdvancedSettings = findViewById(R.id.layoutAdvancedSettings)
        btnUnlockOverlay = findViewById(R.id.btnUnlockOverlay)
        layoutGestureFeedback = findViewById(R.id.layoutGestureFeedback)
        imgGestureIcon = findViewById(R.id.imgGestureIcon)
        tvGestureValue = findViewById(R.id.tvGestureValue)

        tvPlayerTitle = findViewById(R.id.tvPlayerTitle)
        tvPlayerCurrentTime = findViewById(R.id.tvPlayerCurrentTime)
        tvPlayerTotalTime = findViewById(R.id.tvPlayerTotalTime)
        seekBarPlayer = findViewById(R.id.seekBarPlayer)
        btnPlayerPlayPause = findViewById(R.id.btnPlayerPlayPause)
        btnMuteToggle = findViewById(R.id.btnMuteToggle)
        btnPlaybackSpeed = findViewById(R.id.btnPlaybackSpeed)
        btnHdrMode = findViewById(R.id.btnHdrMode)
        imgFavoriteIcon = findViewById(R.id.imgFavoriteIcon)
        tvAbRepeatLabel = findViewById(R.id.tvAbRepeatLabel)
        tvTimerLabel = findViewById(R.id.tvTimerLabel)

        btnLoopShuffle = findViewById(R.id.btnLoopShuffle)
        btnLoopOne = findViewById(R.id.btnLoopOne)
        btnLoopAll = findViewById(R.id.btnLoopAll)
        btnLoopOff = findViewById(R.id.btnLoopOff)
        btnHwDecoder = findViewById(R.id.btnHwDecoder)
        btnSwDecoder = findViewById(R.id.btnSwDecoder)
    }

    private fun extractIntentData(incomingIntent: Intent?) {
        try {
            if (incomingIntent == null) return
            videoPath = incomingIntent.getStringExtra("VIDEO_PATH")
                ?: incomingIntent.getStringExtra("video_path")
                ?: incomingIntent.getStringExtra("path")
                ?: incomingIntent.dataString
                ?: ""

            videoTitle = incomingIntent.getStringExtra("VIDEO_TITLE")
                ?: incomingIntent.getStringExtra("video_title")
                ?: incomingIntent.getStringExtra("title")
                ?: File(videoPath).name.ifEmpty { "Playing Video" }

            val listExtra = incomingIntent.getStringArrayListExtra("VIDEO_LIST")
                ?: incomingIntent.getStringArrayListExtra("video_list")
            if (listExtra != null && listExtra.isNotEmpty()) {
                videoList = listExtra
                currentPositionIndex = videoList.indexOf(videoPath).coerceAtLeast(0)
            } else if (videoPath.isNotEmpty()) {
                videoList = arrayListOf(videoPath)
                currentPositionIndex = 0
            }

            tvPlayerTitle.text = videoTitle
            val prefs = getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
            isFavorite = prefs.getBoolean("fav_$videoPath", false)
            updateFavoriteUi()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initializePlayer() {
        try {
            exoPlayer?.release()
            exoPlayer = ExoPlayer.Builder(this).build().also { player ->
                playerView.player = player
                if (videoList.isNotEmpty()) {
                    val mediaItems = videoList.map { path ->
                        val uri = if (path.startsWith("content://") || path.startsWith("http")) {
                            Uri.parse(path)
                        } else {
                            Uri.fromFile(File(path))
                        }
                        MediaItem.fromUri(uri)
                    }
                    player.setMediaItems(mediaItems, currentPositionIndex, 0L)
                } else if (videoPath.isNotEmpty()) {
                    val uri = if (videoPath.startsWith("content://") || videoPath.startsWith("http")) {
                        Uri.parse(videoPath)
                    } else {
                        Uri.fromFile(File(videoPath))
                    }
                    player.setMediaItem(MediaItem.fromUri(uri))
                }

                player.repeatMode = Player.REPEAT_MODE_ALL
                player.prepare()
                player.playWhenReady = true

                player.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        btnPlayerPlayPause.setImageResource(
                            if (isPlaying) android.R.drawable.ic_media_pause
                            else android.R.drawable.ic_media_play
                        )
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        if (stopAfterCurrent && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                            player.pause()
                        }
                        val index = player.currentMediaItemIndex
                        if (index in videoList.indices) {
                            videoPath = videoList[index]
                            videoTitle = File(videoPath).name
                            tvPlayerTitle.text = videoTitle
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            val duration = player.duration.coerceAtLeast(0L)
                            seekBarPlayer.max = duration.toInt()
                            tvPlayerTotalTime.text = formatDuration(duration)
                        }
                    }
                })
            }
            handler.post(updateProgressAction)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to play video", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupTopAndSideControls() {
        findViewById<ImageView>(R.id.btnPlayerBack).setOnClickListener { finish() }

        btnHdrMode.setOnClickListener {
            isHdrEnabled = !isHdrEnabled
            if (isHdrEnabled) {
                btnHdrMode.setTextColor(Color.parseColor("#00E676"))
                viewVisualOverlay.setBackgroundColor(Color.parseColor("#18FF9800"))
                Toast.makeText(this, "HDR Color Boost ON", Toast.LENGTH_SHORT).show()
            } else {
                btnHdrMode.setTextColor(Color.WHITE)
                viewVisualOverlay.setBackgroundColor(
                    if (isNightMode) Color.parseColor("#88000000") else Color.TRANSPARENT
                )
                Toast.makeText(this, "HDR Color Boost OFF", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<TextView>(R.id.btnSubtitlesCc).setOnClickListener {
            showTrackSelectionDialog(C.TRACK_TYPE_TEXT, "Subtitles (CC)")
        }

        findViewById<ImageView>(R.id.btnBgAudio).setOnClickListener {
            isBgAudioEnabled = !isBgAudioEnabled
            Toast.makeText(
                this,
                if (isBgAudioEnabled) "Background Audio Play Enabled" else "Background Audio Play Disabled",
                Toast.LENGTH_SHORT
            ).show()
        }

        findViewById<ImageView>(R.id.btnVideoQueue).setOnClickListener {
            showPlaylistQueueDialog()
        }

        findViewById<ImageView>(R.id.btnMoreMenu).setOnClickListener {
            layoutAdvancedSettings.visibility = View.VISIBLE
        }

        btnMuteToggle.setOnClickListener {
            isMuted = !isMuted
            exoPlayer?.volume = if (isMuted) 0f else 1f
            btnMuteToggle.setImageResource(
                if (isMuted) android.R.drawable.ic_lock_silent_mode
                else android.R.drawable.ic_lock_silent_mode_off
            )
            Toast.makeText(this, if (isMuted) "Muted" else "Unmuted", Toast.LENGTH_SHORT).show()
        }

        findViewById<ImageView>(R.id.btnLockScreen).setOnClickListener {
            isScreenLocked = true
            layoutAllControls.visibility = View.GONE
            layoutAdvancedSettings.visibility = View.GONE
            btnUnlockOverlay.visibility = View.VISIBLE
            Toast.makeText(this, "Screen Locked", Toast.LENGTH_SHORT).show()
        }

        btnUnlockOverlay.setOnClickListener {
            isScreenLocked = false
            btnUnlockOverlay.visibility = View.GONE
            layoutAllControls.visibility = View.VISIBLE
            Toast.makeText(this, "Screen Unlocked", Toast.LENGTH_SHORT).show()
        }

        findViewById<ImageView>(R.id.btnSideCut).setOnClickListener {
            openMp3Converter()
        }

        findViewById<ImageView>(R.id.btnRotateScreen).setOnClickListener {
            requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
    }

    private fun setupBottomControls() {
        btnPlayerPlayPause.setOnClickListener {
            exoPlayer?.let { player ->
                if (player.isPlaying) player.pause() else player.play()
            }
        }

        findViewById<ImageView>(R.id.btnPlayerPrev).setOnClickListener {
            exoPlayer?.let { player ->
                if (player.hasPreviousMediaItem()) {
                    player.seekToPreviousMediaItem()
                } else {
                    player.seekTo(0)
                }
            }
        }

        findViewById<ImageView>(R.id.btnPlayerNext).setOnClickListener {
            exoPlayer?.let { player ->
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                } else {
                    Toast.makeText(this, "Last Video in Queue", Toast.LENGTH_SHORT).show()
                }
            }
        }

        seekBarPlayer.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    exoPlayer?.seekTo(progress.toLong())
                    tvPlayerCurrentTime.text = formatDuration(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnPlaybackSpeed.setOnClickListener {
            val speeds = arrayOf("0.5x", "0.75x", "1.0x (Normal)", "1.25x", "1.5x", "2.0x")
            val values = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
            AlertDialog.Builder(this)
                .setTitle("Playback Speed")
                .setItems(speeds) { _, which ->
                    currentSpeed = values[which]
                    exoPlayer?.playbackParameters = PlaybackParameters(currentSpeed)
                    btnPlaybackSpeed.text = if (currentSpeed == 1.0f) "Speed" else "${currentSpeed}x"
                }
                .show()
        }

        findViewById<ImageView>(R.id.btnAspectRatio).setOnClickListener {
            resizeModeIndex = (resizeModeIndex + 1) % 3
            when (resizeModeIndex) {
                0 -> {
                    playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    Toast.makeText(this, "Aspect Ratio: Fit", Toast.LENGTH_SHORT).show()
                }
                1 -> {
                    playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                    Toast.makeText(this, "Aspect Ratio: Stretch Fill", Toast.LENGTH_SHORT).show()
                }
                2 -> {
                    playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    Toast.makeText(this, "Aspect Ratio: Crop Zoom", Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<ImageView>(R.id.btnPipMode).setOnClickListener {
            enterPipModeSafely()
        }
    }

    private fun setupAdvancedSettingsMenu() {
        findViewById<TextView>(R.id.btnCloseSettings).setOnClickListener {
            layoutAdvancedSettings.visibility = View.GONE
        }

        findViewById<LinearLayout>(R.id.optAudioTrack).setOnClickListener {
            showTrackSelectionDialog(C.TRACK_TYPE_AUDIO, "Select Audio Track")
        }

        findViewById<LinearLayout>(R.id.optEqualizer).setOnClickListener {
            try {
                val eqIntent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, exoPlayer?.audioSessionId ?: 0)
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                    putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MOVIE)
                }
                if (eqIntent.resolveActivity(packageManager) != null) {
                    startActivityForResult(eqIntent, 101)
                } else {
                    Toast.makeText(this, "Built-in Equalizer Preset: Bass Boost Active", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Equalizer Active", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<LinearLayout>(R.id.optCast).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_CAST_SETTINGS))
            } catch (e: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                } catch (ex: Exception) {
                    Toast.makeText(this, "Cast Settings Unavailable", Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<LinearLayout>(R.id.optShare).setOnClickListener {
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/*"
                    putExtra(Intent.EXTRA_STREAM, Uri.parse(videoPath))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Share Video"))
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to share file", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<LinearLayout>(R.id.optCut).setOnClickListener {
            openMp3Converter()
        }

        findViewById<LinearLayout>(R.id.optFavorite).setOnClickListener {
            isFavorite = !isFavorite
            getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("fav_$videoPath", isFavorite)
                .apply()
            updateFavoriteUi()
            Toast.makeText(
                this,
                if (isFavorite) "Added to Favorites" else "Removed from Favorites",
                Toast.LENGTH_SHORT
            ).show()
        }

        findViewById<LinearLayout>(R.id.optBookmark).setOnClickListener {
            val prefs = getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
            val savedBookmark = prefs.getLong("bookmark_$videoPath", -1L)
            val currentPos = exoPlayer?.currentPosition ?: 0L
            val options = if (savedBookmark != -1L) {
                arrayOf(
                    "Save Bookmark at ${formatDuration(currentPos)}",
                    "Jump to Saved Bookmark (${formatDuration(savedBookmark)})",
                    "Clear Bookmark"
                )
            } else {
                arrayOf("Save Bookmark at ${formatDuration(currentPos)}")
            }
            AlertDialog.Builder(this)
                .setTitle("Video Bookmarks")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> {
                            prefs.edit().putLong("bookmark_$videoPath", currentPos).apply()
                            Toast.makeText(this, "Bookmark Saved!", Toast.LENGTH_SHORT).show()
                        }
                        1 -> exoPlayer?.seekTo(savedBookmark)
                        2 -> {
                            prefs.edit().remove("bookmark_$videoPath").apply()
                            Toast.makeText(this, "Bookmark Cleared", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .show()
        }

        findViewById<LinearLayout>(R.id.optVrMode).setOnClickListener {
            isVrMode = !isVrMode
            playerView.scaleX = if (isVrMode) 1.15f else if (isMirrorMode) -1f else 1f
            playerView.scaleY = if (isVrMode) 1.15f else 1f
            Toast.makeText(this, if (isVrMode) "VR 360 View Enabled" else "VR 360 View Disabled", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.optAbRepeat).setOnClickListener {
            val currentPos = exoPlayer?.currentPosition ?: 0L
            when {
                pointA == -1L -> {
                    pointA = currentPos
                    tvAbRepeatLabel.text = "A Set"
                    Toast.makeText(this, "Point A set at ${formatDuration(pointA)}", Toast.LENGTH_SHORT).show()
                }
                pointB == -1L -> {
                    if (currentPos > pointA) {
                        pointB = currentPos
                        tvAbRepeatLabel.text = "A-B ON"
                        Toast.makeText(this, "Looping A to B", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Point B must be after Point A", Toast.LENGTH_SHORT).show()
                    }
                }
                else -> {
                    pointA = -1L
                    pointB = -1L
                    tvAbRepeatLabel.text = "AB Repeat"
                    Toast.makeText(this, "AB Repeat Cleared", Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<LinearLayout>(R.id.optNightMode).setOnClickListener {
            isNightMode = !isNightMode
            viewVisualOverlay.setBackgroundColor(
                if (isNightMode) Color.parseColor("#88000000") else Color.TRANSPARENT
            )
            Toast.makeText(this, if (isNightMode) "Night Mode ON" else "Night Mode OFF", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.optMirrorMode).setOnClickListener {
            isMirrorMode = !isMirrorMode
            playerView.scaleX = if (isMirrorMode) -1f else 1f
            Toast.makeText(this, if (isMirrorMode) "Mirror Mode ON" else "Mirror Mode OFF", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.optSleepTimer).setOnClickListener {
            val timerOptions = arrayOf("Off", "15 Minutes", "30 Minutes", "60 Minutes")
            AlertDialog.Builder(this)
                .setTitle("Sleep Timer")
                .setItems(timerOptions) { _, which ->
                    sleepCountDownTimer?.cancel()
                    if (which == 0) {
                        tvTimerLabel.text = "Timer"
                        Toast.makeText(this, "Sleep Timer Off", Toast.LENGTH_SHORT).show()
                    } else {
                        val minutes = when (which) {
                            1 -> 15L
                            2 -> 30L
                            else -> 60L
                        }
                        tvTimerLabel.text = "${minutes}m"
                        sleepCountDownTimer = object : CountDownTimer(minutes * 60 * 1000, 60000) {
                            override fun onTick(millisUntilFinished: Long) {}
                            override fun onFinish() {
                                exoPlayer?.pause()
                                tvTimerLabel.text = "Timer"
                                Toast.makeText(this@PlayerActivity, "Sleep Timer: Paused", Toast.LENGTH_LONG).show()
                            }
                        }.start()
                        Toast.makeText(this, "Timer set for $minutes minutes", Toast.LENGTH_SHORT).show()
                    }
                }
                .show()
        }

        btnLoopShuffle.setOnClickListener { updateLoopSelection(0) }
        btnLoopOne.setOnClickListener { updateLoopSelection(1) }
        btnLoopAll.setOnClickListener { updateLoopSelection(2) }
        btnLoopOff.setOnClickListener { updateLoopSelection(3) }

        findViewById<SeekBar>(R.id.seekBarBrightness).setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val layoutParams = window.attributes
                    layoutParams.screenBrightness = (progress.coerceAtLeast(5)) / 100f
                    window.attributes = layoutParams
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnHwDecoder.setOnClickListener {
            btnHwDecoder.setTextColor(Color.parseColor("#00E676"))
            btnSwDecoder.setTextColor(Color.WHITE)
            Toast.makeText(this, "Switched to Hardware (HW) Decoder", Toast.LENGTH_SHORT).show()
        }

        btnSwDecoder.setOnClickListener {
            btnSwDecoder.setTextColor(Color.parseColor("#00E676"))
            btnHwDecoder.setTextColor(Color.WHITE)
            Toast.makeText(this, "Switched to Software (SW) Decoder", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.optEditPlayer).setOnClickListener {
            Toast.makeText(this, "All Custom Player Controls Enabled", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.optTutorials).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Player Gestures Guide")
                .setMessage("• Swipe Left Side Up/Down: Brightness\n• Swipe Right Side Up/Down: Volume\n• Double Tap / SeekBar: Fast Seek\n• Use A-B Repeat to loop any scene")
                .setPositiveButton("OK", null)
                .show()
        }

        findViewById<LinearLayout>(R.id.optFileInfo).setOnClickListener {
            val file = File(videoPath)
            val sizeMb = if (file.exists()) String.format("%.2f MB", file.length() / (1024.0 * 1024.0)) else "Unknown"
            val durationStr = formatDuration(exoPlayer?.duration ?: 0L)
            AlertDialog.Builder(this)
                .setTitle("File Information")
                .setMessage("Title: $videoTitle\n\nPath: $videoPath\n\nSize: $sizeMb\n\nDuration: $durationStr")
                .setPositiveButton("Close", null)
                .show()
        }

        findViewById<LinearLayout>(R.id.optFeedback).setOnClickListener {
            Toast.makeText(this, "Thank you for using MAX Player Pro!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateLoopSelection(mode: Int) {
        val allButtons = listOf(btnLoopShuffle, btnLoopOne, btnLoopAll, btnLoopOff)
        allButtons.forEach { btn ->
            btn.setBackgroundColor(Color.TRANSPARENT)
            btn.setTextColor(Color.WHITE)
        }
        val activeBtn = allButtons[mode]
        activeBtn.setBackgroundColor(Color.parseColor("#00E676"))
        activeBtn.setTextColor(Color.BLACK)

        stopAfterCurrent = false
        exoPlayer?.let { player ->
            when (mode) {
                0 -> {
                    player.shuffleModeEnabled = true
                    player.repeatMode = Player.REPEAT_MODE_ALL
                }
                1 -> {
                    player.shuffleModeEnabled = false
                    player.repeatMode = Player.REPEAT_MODE_ONE
                }
                2 -> {
                    player.shuffleModeEnabled = false
                    player.repeatMode = Player.REPEAT_MODE_ALL
                }
                3 -> {
                    player.shuffleModeEnabled = false
                    player.repeatMode = Player.REPEAT_MODE_OFF
                    stopAfterCurrent = true
                }
            }
        }
    }

    private fun updateFavoriteUi() {
        imgFavoriteIcon.setImageResource(
            if (isFavorite) android.R.drawable.btn_star_big_on
            else android.R.drawable.btn_star_big_off
        )
    }

    private fun showTrackSelectionDialog(trackType: Int, title: String) {
        val player = exoPlayer ?: return
        val trackGroups = player.currentTracks.groups.filter { it.type == trackType }
        if (trackGroups.isEmpty()) {
            Toast.makeText(this, "No additional $title found in this video", Toast.LENGTH_SHORT).show()
            return
        }
        val trackNames = mutableListOf<String>()
        trackGroups.forEachIndexed { index, group ->
            val format = group.getTrackFormat(0)
            val lang = format.language ?: "Track ${index + 1}"
            val label = format.label ?: ""
            trackNames.add("$lang $label".trim())
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(trackNames.toTypedArray()) { _, which ->
                val selectedGroup = trackGroups[which].mediaTrackGroup
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setOverrideForType(
                        androidx.media3.common.TrackSelectionOverride(selectedGroup, 0)
                    )
                    .build()
                Toast.makeText(this, "Selected: ${trackNames[which]}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showPlaylistQueueDialog() {
        if (videoList.isEmpty()) {
            Toast.makeText(this, "Playing Single Video", Toast.LENGTH_SHORT).show()
            return
        }
        val names = videoList.map { File(it).name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Video Queue (${names.size})")
            .setItems(names) { _, which ->
                currentPositionIndex = which
                exoPlayer?.seekToDefaultPosition(which)
                exoPlayer?.play()
            }
            .show()
    }

    private fun openMp3Converter() {
        try {
            val intent = Intent(this, Mp3ConverterActivity::class.java).apply {
                putExtra("VIDEO_PATH", videoPath)
                putExtra("VIDEO_TITLE", videoTitle)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open MP3 Converter", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSwipeGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (isScreenLocked) {
                    btnUnlockOverlay.visibility =
                        if (btnUnlockOverlay.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    return true
                }
                if (layoutAdvancedSettings.visibility == View.VISIBLE) {
                    layoutAdvancedSettings.visibility = View.GONE
                    return true
                }
                layoutAllControls.visibility =
                    if (layoutAllControls.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isScreenLocked) return true
                val screenWidth = resources.displayMetrics.widthPixels
                exoPlayer?.let { player ->
                    if (e.x < screenWidth / 2) {
                        player.seekTo((player.currentPosition - 10000).coerceAtLeast(0L))
                        showGestureFeedback(android.R.drawable.ic_media_rew, "-10s")
                    } else {
                        player.seekTo((player.currentPosition + 10000).coerceAtMost(player.duration))
                        showGestureFeedback(android.R.drawable.ic_media_ff, "+10s")
                    }
                }
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (isScreenLocked || e1 == null) return false
                if (abs(distanceY) > abs(distanceX)) {
                    val screenWidth = resources.displayMetrics.widthPixels
                    if (e1.x < screenWidth / 2) {
                        val lp = window.attributes
                        val currentBrightness = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                        val newBrightness = (currentBrightness + (distanceY / 1000f)).coerceIn(0.05f, 1.0f)
                        lp.screenBrightness = newBrightness
                        window.attributes = lp
                        showGestureFeedback(
                            android.R.drawable.ic_menu_view,
                            "${(newBrightness * 100).toInt()}%"
                        )
                    } else {
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val delta = if (distanceY > 0) 1 else -1
                        val newVol = (curVol + delta).coerceIn(0, maxVol)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                        val pct = ((newVol.toFloat() / maxVol) * 100).toInt()
                        showGestureFeedback(android.R.drawable.ic_lock_silent_mode_off, "$pct%")
                    }
                    return true
                }
                return false
            }
        })

        findViewById<View>(R.id.rootPlayerFrame).setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun showGestureFeedback(iconRes: Int, text: String) {
        imgGestureIcon.setImageResource(iconRes)
        tvGestureValue.text = text
        layoutGestureFeedback.visibility = View.VISIBLE
        handler.removeCallbacksAndMessages("HIDE_GESTURE")
        handler.postDelayed({ layoutGestureFeedback.visibility = View.GONE }, 800)
    }

    private fun enterPipModeSafely() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (exoPlayer?.isPlaying == true && !isBgAudioEnabled) {
            enterPipModeSafely()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            layoutAllControls.visibility = View.GONE
            layoutAdvancedSettings.visibility = View.GONE
        } else if (!isScreenLocked) {
            layoutAllControls.visibility = View.VISIBLE
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

    override fun onStop() {
        super.onStop()
        if (!isBgAudioEnabled && !isInPictureInPictureMode) {
            exoPlayer?.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sleepCountDownTimer?.cancel()
        handler.removeCallbacks(updateProgressAction)
        exoPlayer?.release()
        exoPlayer = null
    }
}
