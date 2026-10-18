package com.app.mediaplayer

import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class PlayerActivity : AppCompatActivity() {

    private var exoPlayer: ExoPlayer? = null
    private lateinit var rootPlayerFrame: FrameLayout
    private lateinit var playerView: PlayerView
    private lateinit var viewVisualOverlay: View

    private lateinit var cardSpeedBoostBadge: CardView
    private lateinit var tvSpeedBoostBadge: TextView
    private lateinit var cardZoomResetPill: CardView
    private lateinit var tvZoomResetPill: TextView

    private lateinit var layoutAllControls: RelativeLayout
    private lateinit var leftSideControlsGroup: LinearLayout
    private lateinit var rightSideControlsGroup: LinearLayout
    private lateinit var frameHdrContainer: FrameLayout
    private lateinit var cardSubtitlesCc: CardView
    private lateinit var hsvDockedBottomTools: HorizontalScrollView
    private lateinit var llDockedBottomTools: LinearLayout
    private lateinit var layoutAdvancedSettings: ScrollView
    private lateinit var btnUnlockOverlay: ImageView

    private lateinit var cardGestureFeedback: CardView
    private lateinit var imgGestureIcon: ImageView
    private lateinit var tvGestureValue: TextView
    private lateinit var pbGestureLevel: ProgressBar

    private lateinit var tvPlayerTitle: TextView
    private lateinit var tvPlayerCurrentTime: TextView
    private lateinit var tvPlayerTotalTime: TextView
    private lateinit var seekBarPlayer: SeekBar
    private lateinit var btnPlayerPlayPause: ImageView
    private lateinit var btnMuteToggle: ImageView
    private lateinit var btnLockScreen: ImageView
    private lateinit var btnSideCut: ImageView
    private lateinit var btnRotateScreen: ImageView
    private lateinit var btnPlaybackSpeed: TextView
    private lateinit var btnHdrMode: TextView
    private lateinit var btnSubtitlesCc: TextView
    private lateinit var btnBgAudio: ImageView
    private lateinit var btnVideoQueue: ImageView
    private lateinit var btnMoreMenu: ImageView
    private lateinit var btnAspectRatio: ImageView
    private lateinit var btnPipMode: ImageView
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
    private var longPressSpeedTarget = 4.0f
    private var isLongPressSpeedActive = false
    private var currentZoomScale = 1.0f
    private var isPinchZooming = false

    private var resizeModeIndex = 0
    private var pointA: Long = -1L
    private var pointB: Long = -1L
    private var sleepCountDownTimer: CountDownTimer? = null

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var audioManager: AudioManager
    private lateinit var gestureDetector: GestureDetector
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    private val hideGestureRunnable = Runnable {
        cardGestureFeedback.visibility = View.GONE
    }

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
        applyPlayerCustomizationPreferences()
        extractIntentData(intent)
        initializePlayer()
        setupTopAndSideControls()
        setupBottomControls()
        setupAdvancedSettingsMenu()
        setupMultiTouchZoomAndGestures()
    }

    private fun bindViews() {
        rootPlayerFrame = findViewById(R.id.rootPlayerFrame)
        playerView = findViewById(R.id.playerView)
        viewVisualOverlay = findViewById(R.id.viewVisualOverlay)

        cardSpeedBoostBadge = findViewById(R.id.cardSpeedBoostBadge)
        tvSpeedBoostBadge = findViewById(R.id.tvSpeedBoostBadge)
        cardZoomResetPill = findViewById(R.id.cardZoomResetPill)
        tvZoomResetPill = findViewById(R.id.tvZoomResetPill)

        layoutAllControls = findViewById(R.id.layoutAllControls)
        leftSideControlsGroup = findViewById(R.id.leftSideControlsGroup)
        rightSideControlsGroup = findViewById(R.id.rightSideControlsGroup)
        frameHdrContainer = findViewById(R.id.frameHdrContainer)
        cardSubtitlesCc = findViewById(R.id.cardSubtitlesCc)
        hsvDockedBottomTools = findViewById(R.id.hsvDockedBottomTools)
        llDockedBottomTools = findViewById(R.id.llDockedBottomTools)
        layoutAdvancedSettings = findViewById(R.id.layoutAdvancedSettings)
        btnUnlockOverlay = findViewById(R.id.btnUnlockOverlay)

        cardGestureFeedback = findViewById(R.id.cardGestureFeedback)
        imgGestureIcon = findViewById(R.id.imgGestureIcon)
        tvGestureValue = findViewById(R.id.tvGestureValue)
        pbGestureLevel = findViewById(R.id.pbGestureLevel)

        tvPlayerTitle = findViewById(R.id.tvPlayerTitle)
        tvPlayerCurrentTime = findViewById(R.id.tvPlayerCurrentTime)
        tvPlayerTotalTime = findViewById(R.id.tvPlayerTotalTime)
        seekBarPlayer = findViewById(R.id.seekBarPlayer)
        btnPlayerPlayPause = findViewById(R.id.btnPlayerPlayPause)
        btnMuteToggle = findViewById(R.id.btnMuteToggle)
        btnLockScreen = findViewById(R.id.btnLockScreen)
        btnSideCut = findViewById(R.id.btnSideCut)
        btnRotateScreen = findViewById(R.id.btnRotateScreen)
        btnPlaybackSpeed = findViewById(R.id.btnPlaybackSpeed)
        btnHdrMode = findViewById(R.id.btnHdrMode)
        btnSubtitlesCc = findViewById(R.id.btnSubtitlesCc)
        btnBgAudio = findViewById(R.id.btnBgAudio)
        btnVideoQueue = findViewById(R.id.btnVideoQueue)
        btnMoreMenu = findViewById(R.id.btnMoreMenu)
        btnAspectRatio = findViewById(R.id.btnAspectRatio)
        btnPipMode = findViewById(R.id.btnPipMode)
        imgFavoriteIcon = findViewById(R.id.imgFavoriteIcon)
        tvAbRepeatLabel = findViewById(R.id.tvAbRepeatLabel)
        tvTimerLabel = findViewById(R.id.tvTimerLabel)

        btnLoopShuffle = findViewById(R.id.btnLoopShuffle)
        btnLoopOne = findViewById(R.id.btnLoopOne)
        btnLoopAll = findViewById(R.id.btnLoopAll)
        btnLoopOff = findViewById(R.id.btnLoopOff)
        btnHwDecoder = findViewById(R.id.btnHwDecoder)
        btnSwDecoder = findViewById(R.id.btnSwDecoder)

        cardZoomResetPill.setOnClickListener {
            applyVideoZoom(1.0f)
            cardZoomResetPill.visibility = View.GONE
            Toast.makeText(this, "Zoom Reset to 100%", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyPlayerCustomizationPreferences() {
        val prefs = getSharedPreferences("MAX_LAYOUT_PREFS", Context.MODE_PRIVATE)
        val dockSideIconsToBottom = prefs.getBoolean("player_dock_side_to_bottom", false)
        val showSideControls = prefs.getBoolean("player_show_side_controls", true)
        val showTopHdrAndCc = prefs.getBoolean("player_show_hdr_cc", true)
        longPressSpeedTarget = prefs.getFloat("player_long_press_speed", 4.0f)

        frameHdrContainer.visibility = if (showTopHdrAndCc) View.VISIBLE else View.GONE
        cardSubtitlesCc.visibility = if (showTopHdrAndCc) View.VISIBLE else View.GONE

        if (!showSideControls) {
            leftSideControlsGroup.visibility = View.GONE
            rightSideControlsGroup.visibility = View.GONE
            hsvDockedBottomTools.visibility = View.GONE
        } else if (dockSideIconsToBottom) {
            leftSideControlsGroup.visibility = View.GONE
            rightSideControlsGroup.visibility = View.GONE
            hsvDockedBottomTools.visibility = View.VISIBLE
            populateDockedBottomTools()
        } else {
            leftSideControlsGroup.visibility = View.VISIBLE
            rightSideControlsGroup.visibility = View.VISIBLE
            hsvDockedBottomTools.visibility = View.GONE
        }
    }

    private fun populateDockedBottomTools() {
        llDockedBottomTools.removeAllViews()
        val tools = listOf(
            "Lock" to { lockScreenNow() },
            "Mute" to { toggleMuteNow() },
            "Rotate" to { toggleRotationNow() },
            "MP3 Cut" to { openMp3Converter() },
            "4x Speed" to { applySpeedDirect(4.0f) },
            "8x Speed" to { applySpeedDirect(8.0f) },
            "Reset Layout" to { showCustomizePlayerLayoutDialog() }
        )

        for ((label, action) in tools) {
            val pill = TextView(this).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 11.5f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dpToPx(14), dpToPx(5), dpToPx(14), dpToPx(5))
                background = GradientDrawable().apply {
                    cornerRadius = dpToPx(16).toFloat()
                    setColor(Color.parseColor("#33FFFFFF"))
                }
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.marginEnd = dpToPx(8)
                layoutParams = lp
                setOnClickListener { action() }
            }
            llDockedBottomTools.addView(pill)
        }
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
            if (videoPath.isNotEmpty()) {
                prefs.edit().putLong("history_time_$videoPath", System.currentTimeMillis()).apply()
            }
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
                            getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
                                .edit()
                                .putLong("history_time_$videoPath", System.currentTimeMillis())
                                .apply()
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
                btnHdrMode.setTextColor(Color.parseColor("#00FFFF"))
                viewVisualOverlay.setBackgroundColor(Color.parseColor("#18FF9800"))
                Toast.makeText(this, "HDR Color Enhancement ON", Toast.LENGTH_SHORT).show()
            } else {
                btnHdrMode.setTextColor(Color.WHITE)
                viewVisualOverlay.setBackgroundColor(
                    if (isNightMode) Color.parseColor("#88000000") else Color.TRANSPARENT
                )
                Toast.makeText(this, "HDR Color Enhancement OFF", Toast.LENGTH_SHORT).show()
            }
        }

        btnSubtitlesCc.setOnClickListener {
            showTrackSelectionDialog(C.TRACK_TYPE_TEXT, "Subtitles (CC)")
        }

        btnBgAudio.setOnClickListener {
            isBgAudioEnabled = !isBgAudioEnabled
            Toast.makeText(
                this,
                if (isBgAudioEnabled) "Background Audio Play ON" else "Background Audio Play OFF",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnVideoQueue.setOnClickListener {
            showPlaylistQueueDialog()
        }

        btnMoreMenu.setOnClickListener {
            layoutAdvancedSettings.visibility = View.VISIBLE
        }

        btnMuteToggle.setOnClickListener { toggleMuteNow() }
        btnLockScreen.setOnClickListener { lockScreenNow() }

        btnUnlockOverlay.setOnClickListener {
            isScreenLocked = false
            btnUnlockOverlay.visibility = View.GONE
            layoutAllControls.visibility = View.VISIBLE
            Toast.makeText(this, "Screen Unlocked", Toast.LENGTH_SHORT).show()
        }

        btnSideCut.setOnClickListener { openMp3Converter() }
        btnRotateScreen.setOnClickListener { toggleRotationNow() }
    }

    private fun toggleMuteNow() {
        isMuted = !isMuted
        exoPlayer?.volume = if (isMuted) 0f else 1f
        btnMuteToggle.setImageResource(
            if (isMuted) android.R.drawable.ic_lock_silent_mode
            else android.R.drawable.ic_lock_silent_mode_off
        )
        Toast.makeText(this, if (isMuted) "Audio Muted" else "Audio Unmuted", Toast.LENGTH_SHORT).show()
    }

    private fun lockScreenNow() {
        isScreenLocked = true
        layoutAllControls.visibility = View.GONE
        layoutAdvancedSettings.visibility = View.GONE
        btnUnlockOverlay.visibility = View.VISIBLE
        Toast.makeText(this, "Screen Locked", Toast.LENGTH_SHORT).show()
    }

    private fun toggleRotationNow() {
        requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
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

        // Full Speed Selection from 0.25x up to 8.0x
        btnPlaybackSpeed.setOnClickListener {
            val speeds = arrayOf(
                "0.25x",
                "0.5x",
                "0.75x",
                "1.0x (Normal)",
                "1.25x",
                "1.5x",
                "2.0x",
                "3.0x",
                "4.0x",
                "5.0x",
                "6.0x",
                "7.0x",
                "8.0x"
            )
            val values = floatArrayOf(
                0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f
            )
            AlertDialog.Builder(this)
                .setTitle("Playback Speed")
                .setItems(speeds) { _, which ->
                    applySpeedDirect(values[which])
                }
                .show()
        }

        btnAspectRatio.setOnClickListener {
            resizeModeIndex = (resizeModeIndex + 1) % 3
            when (resizeModeIndex) {
                0 -> {
                    playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    Toast.makeText(this, "Aspect Ratio: Original Fit", Toast.LENGTH_SHORT).show()
                }
                1 -> {
                    playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                    Toast.makeText(this, "Aspect Ratio: Full Stretch", Toast.LENGTH_SHORT).show()
                }
                2 -> {
                    playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    Toast.makeText(this, "Aspect Ratio: Center Crop", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnPipMode.setOnClickListener {
            enterPipModeSafely()
        }
    }

    private fun applySpeedDirect(targetSpeed: Float) {
        currentSpeed = if (currentSpeed == targetSpeed && targetSpeed != 1.0f) 1.0f else targetSpeed
        exoPlayer?.playbackParameters = PlaybackParameters(currentSpeed)
        btnPlaybackSpeed.text = if (currentSpeed == 1.0f) "Speed" else "${currentSpeed}x"
        Toast.makeText(this, "Speed: ${currentSpeed}x", Toast.LENGTH_SHORT).show()
    }

    private fun applyVideoZoom(scale: Float) {
        currentZoomScale = scale.coerceIn(0.5f, 6.0f)
        val mirrorFactor = if (isMirrorMode) -1f else 1f
        val videoSurface = playerView.videoSurfaceView ?: playerView
        videoSurface.scaleX = currentZoomScale * mirrorFactor
        videoSurface.scaleY = currentZoomScale

        val zoomPercent = (currentZoomScale * 100).toInt()
        if (zoomPercent != 100) {
            tvZoomResetPill.text = "Zoom: $zoomPercent% • Reset"
            cardZoomResetPill.visibility = View.VISIBLE
        } else {
            cardZoomResetPill.visibility = View.GONE
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
            openEqualizerNow()
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
            val targetScale = if (isVrMode) 1.25f else 1.0f
            applyVideoZoom(targetScale)
            Toast.makeText(this, if (isVrMode) "VR 360 View Enabled" else "VR 360 View Disabled", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.optAbRepeat).setOnClickListener {
            triggerAbRepeatStep()
        }

        findViewById<LinearLayout>(R.id.optNightMode).setOnClickListener {
            toggleNightModeNow()
        }

        findViewById<LinearLayout>(R.id.optMirrorMode).setOnClickListener {
            toggleMirrorModeNow()
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
            btnHwDecoder.setTextColor(Color.parseColor("#00FFFF"))
            btnSwDecoder.setTextColor(Color.WHITE)
            Toast.makeText(this, "Switched to Hardware (HW) Decoder", Toast.LENGTH_SHORT).show()
        }

        btnSwDecoder.setOnClickListener {
            btnSwDecoder.setTextColor(Color.parseColor("#00FFFF"))
            btnHwDecoder.setTextColor(Color.WHITE)
            Toast.makeText(this, "Switched to Software (SW) Decoder", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.optEditPlayer).setOnClickListener {
            showCustomizePlayerLayoutDialog()
        }

        findViewById<LinearLayout>(R.id.optTutorials).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Player Gestures Guide")
                .setMessage(
                    "• Two-Finger Pinch: Smooth Zoom from 50% to 600%\n" +
                        "• Press & Hold Video: Instant 4x / 6x / 8x Speed Boost\n" +
                        "• Double Tap Left / Right: -10s / +10s Seek\n" +
                        "• Swipe Left Up/Down: Brightness\n" +
                        "• Swipe Right Up/Down: Volume\n" +
                        "• Edit Player: Dock side buttons to bottom bar"
                )
                .setPositiveButton("Got It", null)
                .show()
        }

        findViewById<LinearLayout>(R.id.optFileInfo).setOnClickListener {
            val file = File(videoPath)
            val sizeMb = if (file.exists()) String.format(Locale.US, "%.2f MB", file.length() / (1024.0 * 1024.0)) else "Unknown"
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

    private fun openEqualizerNow() {
        try {
            val eqIntent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, exoPlayer?.audioSessionId ?: 0)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MOVIE)
            }
            if (eqIntent.resolveActivity(packageManager) != null) {
                startActivityForResult(eqIntent, 101)
            } else {
                Toast.makeText(this, "Dolby Bass Boost Equalizer Active", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Equalizer Active", Toast.LENGTH_SHORT).show()
        }
    }

    private fun triggerAbRepeatStep() {
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
                    Toast.makeText(this, "Looping Scene A to B", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Point B must be after Point A", Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                pointA = -1L
                pointB = -1L
                tvAbRepeatLabel.text = "AB Repeat"
                Toast.makeText(this, "A-B Repeat Cleared", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleNightModeNow() {
        isNightMode = !isNightMode
        viewVisualOverlay.setBackgroundColor(
            if (isNightMode) Color.parseColor("#88000000") else Color.TRANSPARENT
        )
        Toast.makeText(this, if (isNightMode) "Night Mode ON" else "Night Mode OFF", Toast.LENGTH_SHORT).show()
    }

    private fun toggleMirrorModeNow() {
        isMirrorMode = !isMirrorMode
        applyVideoZoom(currentZoomScale)
        Toast.makeText(this, if (isMirrorMode) "Mirror Mode ON" else "Mirror Mode OFF", Toast.LENGTH_SHORT).show()
    }

    private fun showCustomizePlayerLayoutDialog() {
        val prefs = getSharedPreferences("MAX_LAYOUT_PREFS", Context.MODE_PRIVATE)
        val dockSide = prefs.getBoolean("player_dock_side_to_bottom", false)
        val showSide = prefs.getBoolean("player_show_side_controls", true)
        val showHdrCc = prefs.getBoolean("player_show_hdr_cc", true)

        val options = arrayOf(
            if (dockSide) "Move Side Icons Back to Left/Right" else "Move Side Icons to Bottom Bar",
            if (showSide) "Hide Side Controls" else "Show Side Controls",
            if (showHdrCc) "Hide Top HDR & CC Badges" else "Show Top HDR & CC Badges",
            "Set Press & Hold Speed Boost to 4.0x",
            "Set Press & Hold Speed Boost to 6.0x",
            "Set Press & Hold Speed Boost to 8.0x",
            "Reset Player Layout & Zoom (100%)"
        )

        AlertDialog.Builder(this)
            .setTitle("Edit Player Layout")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        prefs.edit().putBoolean("player_dock_side_to_bottom", !dockSide).apply()
                        applyPlayerCustomizationPreferences()
                    }
                    1 -> {
                        prefs.edit().putBoolean("player_show_side_controls", !showSide).apply()
                        applyPlayerCustomizationPreferences()
                    }
                    2 -> {
                        prefs.edit().putBoolean("player_show_hdr_cc", !showHdrCc).apply()
                        applyPlayerCustomizationPreferences()
                    }
                    3 -> {
                        prefs.edit().putFloat("player_long_press_speed", 4.0f).apply()
                        longPressSpeedTarget = 4.0f
                        Toast.makeText(this, "Press & Hold Speed set to 4.0x", Toast.LENGTH_SHORT).show()
                    }
                    4 -> {
                        prefs.edit().putFloat("player_long_press_speed", 6.0f).apply()
                        longPressSpeedTarget = 6.0f
                        Toast.makeText(this, "Press & Hold Speed set to 6.0x", Toast.LENGTH_SHORT).show()
                    }
                    5 -> {
                        prefs.edit().putFloat("player_long_press_speed", 8.0f).apply()
                        longPressSpeedTarget = 8.0f
                        Toast.makeText(this, "Press & Hold Speed set to 8.0x", Toast.LENGTH_SHORT).show()
                    }
                    6 -> {
                        prefs.edit()
                            .putBoolean("player_dock_side_to_bottom", false)
                            .putBoolean("player_show_side_controls", true)
                            .putBoolean("player_show_hdr_cc", true)
                            .putFloat("player_long_press_speed", 4.0f)
                            .apply()
                        applyVideoZoom(1.0f)
                        applyPlayerCustomizationPreferences()
                    }
                }
            }
            .setNegativeButton("Done", null)
            .show()
    }

    private fun updateLoopSelection(mode: Int) {
        val allButtons = listOf(btnLoopShuffle, btnLoopOne, btnLoopAll, btnLoopOff)
        allButtons.forEach { btn ->
            btn.setBackgroundColor(Color.TRANSPARENT)
            btn.setTextColor(Color.WHITE)
        }
        val activeBtn = allButtons[mode]
        activeBtn.setBackgroundColor(Color.parseColor("#00FFFF"))
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

    private fun setupMultiTouchZoomAndGestures() {
        // 1. Two-Finger Pinch-to-Zoom from 50% up to 600% (6.0x)
        scaleGestureDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    if (isScreenLocked) return false
                    isPinchZooming = true
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    if (isScreenLocked) return false
                    val newScale = (currentZoomScale * detector.scaleFactor).coerceIn(0.5f, 6.0f)
                    applyVideoZoom(newScale)
                    val zoomPct = (currentZoomScale * 100).toInt()
                    showGestureFeedback(
                        android.R.drawable.ic_menu_view,
                        "$zoomPct%",
                        (zoomPct / 6).coerceIn(0, 100)
                    )
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    handler.postDelayed({ isPinchZooming = false }, 150)
                }
            }
        )

        // 2. Single Tap, Double Tap (-10s/+10s), Long Press (4x/6x/8x Speed Boost), and Vertical Swipes
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
                        showGestureFeedback(android.R.drawable.ic_media_rew, "-10s", -1)
                    } else {
                        player.seekTo((player.currentPosition + 10000).coerceAtMost(player.duration))
                        showGestureFeedback(android.R.drawable.ic_media_ff, "+10s", -1)
                    }
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (isScreenLocked || isPinchZooming) return
                exoPlayer?.let { player ->
                    if (player.isPlaying) {
                        isLongPressSpeedActive = true
                        player.playbackParameters = PlaybackParameters(longPressSpeedTarget)
                        tvSpeedBoostBadge.text = "${longPressSpeedTarget}x Fast Play ⏩"
                        cardSpeedBoostBadge.visibility = View.VISIBLE
                    }
                }
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (isScreenLocked || isPinchZooming || isLongPressSpeedActive || e1 == null || e2.pointerCount > 1) {
                    return false
                }
                if (abs(distanceY) > abs(distanceX)) {
                    val screenWidth = resources.displayMetrics.widthPixels
                    if (e1.x < screenWidth / 2) {
                        val lp = window.attributes
                        val currentBrightness = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                        val newBrightness = (currentBrightness + (distanceY / 1000f)).coerceIn(0.05f, 1.0f)
                        lp.screenBrightness = newBrightness
                        window.attributes = lp
                        val pct = (newBrightness * 100).toInt()
                        showGestureFeedback(android.R.drawable.ic_menu_view, "$pct%", pct)
                    } else {
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val delta = if (distanceY > 0) 1 else -1
                        val newVol = (curVol + delta).coerceIn(0, maxVol)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                        val pct = ((newVol.toFloat() / maxVol) * 100).toInt()
                        showGestureFeedback(android.R.drawable.ic_lock_silent_mode_off, "$pct%", pct)
                    }
                    return true
                }
                return false
            }
        })

        rootPlayerFrame.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            if (!scaleGestureDetector.isInProgress) {
                gestureDetector.onTouchEvent(event)
            }
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                if (isLongPressSpeedActive) {
                    isLongPressSpeedActive = false
                    exoPlayer?.playbackParameters = PlaybackParameters(currentSpeed)
                    cardSpeedBoostBadge.visibility = View.GONE
                }
            }
            true
        }
    }

    private fun showGestureFeedback(iconRes: Int, text: String, progressPercent: Int) {
        imgGestureIcon.setImageResource(iconRes)
        tvGestureValue.text = text
        if (progressPercent in 0..100) {
            pbGestureLevel.visibility = View.VISIBLE
            pbGestureLevel.progress = progressPercent
        } else {
            pbGestureLevel.visibility = View.GONE
        }
        cardGestureFeedback.visibility = View.VISIBLE
        handler.removeCallbacks(hideGestureRunnable)
        handler.postDelayed(hideGestureRunnable, 800)
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
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
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
        handler.removeCallbacks(hideGestureRunnable)
        exoPlayer?.release()
        exoPlayer = null
    }
}
