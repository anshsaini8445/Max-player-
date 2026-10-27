package com.app.mediaplayer

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Rational
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity(), PlayerThreeDotMenuHelper.PlayerMenuCallbacks {

    companion object {
        private const val REQ_PICK_SUBTITLE = 6101
        private const val ACTION_PIP_MUTE = "com.app.mediaplayer.ACTION_PIP_MUTE"
        private const val ACTION_PIP_CLOSE = "com.app.mediaplayer.ACTION_PIP_CLOSE"
    }

    private var exoPlayer: ExoPlayer? = null
    private var nativeFallbackVideoView: VideoView? = null
    private var isUsingNativeFallback = false

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var mediaSessionManager: MediaSessionManager? = null
    private var smartIdlePauseHelper: SmartIdlePauseHelper? = null
    private var volumeHudHelper: VolumeHudOverlayHelper? = null

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
    private lateinit var btnPlayerPrev: ImageView
    private lateinit var btnPlayerPlayPause: ImageView
    private lateinit var btnPlayerNext: ImageView
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

    private lateinit var panelOverlayScrim: View
    private lateinit var panelVisualEnhancer: CardView
    private lateinit var panelSubtitles: CardView
    private lateinit var panelPlayingQueue: CardView
    private lateinit var panelSpeedRuler: CardView
    private lateinit var panelBgAudioScreen: RelativeLayout

    private lateinit var tvBgBottomTitle: TextView
    private lateinit var tvBgBottomTime: TextView
    private lateinit var sbBgBottomSeek: SeekBar
    private lateinit var btnBgBottomPlayPause: ImageView
    private lateinit var btnBgCenterBackToVideo: TextView
    private lateinit var btnBgFastMp3Convert: TextView

    private lateinit var tvQueueHeaderCount: TextView
    private lateinit var btnQueueLoopModeToggle: TextView
    private lateinit var rvQueueList: RecyclerView
    private lateinit var tvSpeedRulerValue: TextView
    private lateinit var speedRulerView: PlayerThreeDotMenuHelper.SpeedTickRulerView

    private var videoPath: String = ""
    private var videoTitle: String = "Video"
    private var videoList: MutableList<String> = mutableListOf()
    private var currentPositionIndex: Int = 0

    private var isScreenLocked = false
    private var isMuted = false
    private var isNightMode = false
    private var currentVisualMode = 0
    private var applyVisualToAll = false
    private var isMirrorMode = false
    private var isVrMode = false
    private var isBgAudioEnabled = false
    private var isFavorite = false
    private var stopAfterCurrent = false
    private var fallbackStage = 0

    private var currentSpeed = 1.0f
    private var isLongPressSpeedActive = false
    private var dynamicHoldSpeed = 0.25f
    private var longPressStartX = 0f

    private var currentZoomScale = 1.0f
    private var isPinchZooming = false
    private var currentBrightnessPercent = 75f

    private var lockedSwipeDirection = 0
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var lastMoveX = 0f
    private var lastMoveY = 0f
    private var scrubTargetPositionMs = 0L

    private var resizeModeIndex = 0
    private var queueLoopMode = 0
    private var subtitleTextSizeSp = 18f
    private var subtitleSyncOffsetMs = 0L
    private var pointA: Long = -1L
    private var pointB: Long = -1L
    private var isPipReceiverRegistered = false

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var audioManager: AudioManager
    private lateinit var gestureDetector: GestureDetector
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    private val hideGestureRunnable = Runnable {
        cardGestureFeedback.visibility = View.GONE
    }

    private val autoHideZoomPillRunnable = Runnable {
        cardZoomResetPill.visibility = View.GONE
    }

    private val autoHideLockIconRunnable = Runnable {
        if (isScreenLocked) {
            btnUnlockOverlay.animate()
                .alpha(0f)
                .setDuration(200L)
                .withEndAction { btnUnlockOverlay.visibility = View.GONE }
                .start()
        }
    }

    private val dynamicSpeedRampRunnable = object : Runnable {
        override fun run() {
            if (!isLongPressSpeedActive) return
            val player = exoPlayer ?: return
            if (player.isPlaying) {
                player.playbackParameters = PlaybackParameters(dynamicHoldSpeed)
                tvSpeedBoostBadge.text = String.format(
                    Locale.US,
                    "%.2fx Speed (Slide Left 0.25x <-> Right 8.0x)",
                    dynamicHoldSpeed
                )
                cardSpeedBoostBadge.visibility = View.VISIBLE
            }
        }
    }

    private val pipAndNotifReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PIP_MUTE -> {
                    toggleMuteNow()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) {
                        updatePipParams()
                    }
                }
                ACTION_PIP_CLOSE -> {
                    isBgAudioEnabled = false
                    VideoFragment.updateBackgroundBarState(false, "", "", false, 0L, 1000L)
                    exoPlayer?.pause()
                    nativeFallbackVideoView?.pause()
                    finish()
                }
                BackgroundAudioService.ACTION_NOTIF_PREV -> playPreviousVideo()
                BackgroundAudioService.ACTION_NOTIF_PLAY_PAUSE -> togglePlayPauseUnified()
                BackgroundAudioService.ACTION_NOTIF_NEXT -> playNextVideo()
                BackgroundAudioService.ACTION_NOTIF_SEEK -> {
                    val targetMs = intent.getLongExtra("SEEK_POS_MS", 0L)
                    seekToUnified(targetMs)
                    syncBackgroundAudioUiAndService()
                }
            }
        }
    }

    private val updateProgressAction = object : Runnable {
        override fun run() {
            try {
                if (lockedSwipeDirection != 1) {
                    val currentPos = getCurrentPositionUnified()
                    val duration = getDurationUnified().coerceAtLeast(1L)
                    if (isPlayingUnified()) {
                        seekBarPlayer.max = duration.toInt()
                        seekBarPlayer.progress = currentPos.toInt()
                        tvPlayerCurrentTime.text = formatDuration(currentPos)
                        tvPlayerTotalTime.text = formatDuration(duration)

                        if (panelBgAudioScreen.visibility == View.VISIBLE) {
                            sbBgBottomSeek.max = duration.toInt()
                            sbBgBottomSeek.progress = currentPos.toInt()
                            tvBgBottomTime.text = "${formatDuration(currentPos)} / ${formatDuration(duration)}"
                        }

                        if (isBgAudioEnabled) {
                            VideoFragment.updateBackgroundBarState(
                                visible = true,
                                title = videoTitle,
                                path = videoPath,
                                isPlaying = true,
                                positionMs = currentPos,
                                durationMs = duration
                            )
                        }

                        if (videoPath.isNotEmpty() && currentPos > 1000L) {
                            getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
                                .edit()
                                .putLong("watched_pos_$videoPath", currentPos)
                                .apply()
                        }

                        if (pointA != -1L && pointB != -1L && currentPos >= pointB) {
                            seekToUnified(pointA)
                        }
                    }
                }
            } catch (_: Exception) {
            }
            handler.postDelayed(this, 300L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        setContentView(R.layout.activity_player)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        registerPipAndNotifReceiver()
        bindViews()
        disableClippingForFullScreenZoom()

        volumeHudHelper = VolumeHudOverlayHelper(
            context = this,
            rootContainer = rootPlayerFrame,
            audioManager = audioManager,
            loudnessEnhancerProvider = { loudnessEnhancer },
            playerVolumeSetter = { vol -> exoPlayer?.volume = vol }
        )

        setupUnifiedAllWhiteModernIcons()
        restorePersistentBrightnessAndVisuals()
        buildModularOverlayPanels()

        val sbBrightness = findViewById<SeekBar>(R.id.seekBarBrightness)
        PlayerThreeDotMenuHelper.styleAndBindThreeDotMenu(
            context = this,
            layoutAdvancedSettings = layoutAdvancedSettings,
            imgFavoriteIcon = imgFavoriteIcon,
            btnLoopShuffle = btnLoopShuffle,
            btnLoopOne = btnLoopOne,
            btnLoopAll = btnLoopAll,
            btnLoopOff = btnLoopOff,
            btnHwDecoder = btnHwDecoder,
            btnSwDecoder = btnSwDecoder,
            seekBarBrightness = sbBrightness,
            callbacks = this
        )

        // Ensure 3-Dot menu is elevated above dynamically added scrims
        layoutAdvancedSettings.bringToFront()

        applyPlayerCustomizationPreferences()
        extractIntentData(intent)
        initializePlayer(startPositionOverrideMs = -1L)
        setupTopAndSideControls()
        setupBottomControls()
        setupDirectionLockedTouchAndGestures()
        enterFullImmersiveMode()

        smartIdlePauseHelper = SmartIdlePauseHelper(
            context = this,
            playerProvider = { exoPlayer },
            onTickLabelUpdate = { label -> tvTimerLabel.text = label }
        )

        if (intent?.getBooleanExtra("START_BG_AUDIO", false) == true) {
            activateBackgroundAudioMode()
        }
    }

    override fun getExoPlayer(): ExoPlayer? = exoPlayer
    override fun getVideoPath(): String = videoPath
    override fun getVideoTitle(): String = videoTitle
    override fun getCurrentPositionMs(): Long = getCurrentPositionUnified()
    override fun getDurationMs(): Long = getDurationUnified()
    override fun seekToPositionMs(posMs: Long) = seekToUnified(posMs)
    override fun closeAllPanels() = closeAllModularPanels()

    override fun onFavoriteToggled(isFav: Boolean) {
        isFavorite = isFav
        updateFavoriteUi()
    }

    override fun onVrModeToggled() {
        isVrMode = !isVrMode
        applyVideoZoom(if (isVrMode) 1.25f else 1.0f)
        Toast.makeText(this, if (isVrMode) "VR 360 View Enabled" else "VR 360 View Disabled", Toast.LENGTH_SHORT).show()
    }

    override fun onAbRepeatClicked() = triggerAbRepeatStep()
    override fun onNightModeToggled() = toggleNightModeNow()
    override fun onMirrorModeToggled() = toggleMirrorModeNow()

    override fun onOpenSleepTimer() {
        SmartSleepTimerDialog.showSleepTimerPicker(
            context = this,
            onStopTimer = { smartIdlePauseHelper?.cancelTimer() },
            onStartTimerMinutes = { mins, motion ->
                smartIdlePauseHelper?.startSmoothFadeSleepTimer(mins, motion)
            }
        )
    }

    override fun onLoopModeSelected(mode: Int) = updateLoopSelection(mode)

    override fun onBrightnessChanged(percent: Float) {
        currentBrightnessPercent = percent.coerceIn(5f, 100f)
        val lp = window.attributes
        lp.screenBrightness = currentBrightnessPercent / 100f
        window.attributes = lp
        getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
            .edit()
            .putFloat("persistent_brightness_pct", currentBrightnessPercent)
            .apply()
    }

    override fun onDecoderModeChanged(stage: Int) {
        fallbackStage = stage
        val pos = getCurrentPositionUnified()
        initializePlayer(startPositionOverrideMs = pos)
    }

    override fun onLayoutPreferencesChanged() = applyPlayerCustomizationPreferences()
    override fun onResetVideoZoom() = applyVideoZoom(1.0f)
    override fun onDeleteCurrentVideoPermanently() = deleteCurrentVideoPermanently()

    private fun isPlayingUnified(): Boolean {
        return if (isUsingNativeFallback) {
            nativeFallbackVideoView?.isPlaying == true
        } else {
            exoPlayer?.isPlaying == true
        }
    }

    private fun getCurrentPositionUnified(): Long {
        return if (isUsingNativeFallback) {
            (nativeFallbackVideoView?.currentPosition ?: 0).toLong().coerceAtLeast(0L)
        } else {
            (exoPlayer?.currentPosition ?: 0L).coerceAtLeast(0L)
        }
    }

    private fun getDurationUnified(): Long {
        return if (isUsingNativeFallback) {
            (nativeFallbackVideoView?.duration ?: 1000).toLong().coerceAtLeast(1000L)
        } else {
            (exoPlayer?.duration ?: 1000L).coerceAtLeast(1000L)
        }
    }

    private fun seekToUnified(posMs: Long) {
        if (isUsingNativeFallback) {
            nativeFallbackVideoView?.seekTo(posMs.toInt())
        } else {
            exoPlayer?.seekTo(posMs)
        }
    }

    private fun togglePlayPauseUnified() {
        val d = resources.displayMetrics.density
        if (isUsingNativeFallback) {
            val vv = nativeFallbackVideoView ?: return
            if (vv.isPlaying) vv.pause() else vv.start()
        } else {
            val p = exoPlayer ?: return
            if (p.isPlaying) p.pause() else p.play()
        }
        val playing = isPlayingUnified()
        btnPlayerPlayPause.setImageDrawable(
            PlayerThreeDotMenuHelper.PlayerVectorIconDrawable(if (playing) "PAUSE" else "PLAY", d, Color.WHITE)
        )
        btnBgBottomPlayPause.setImageDrawable(
            PlayerThreeDotMenuHelper.PlayerVectorIconDrawable(if (playing) "PAUSE" else "PLAY", d, Color.WHITE)
        )
        mediaSessionManager?.updatePlaybackState()
        syncBackgroundAudioUiAndService()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterFullImmersiveMode()
        }
    }

    private fun enterFullImmersiveMode() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                    )
            }
        } catch (_: Exception) {
        }
    }

    private fun disableClippingForFullScreenZoom() {
        rootPlayerFrame.clipChildren = false
        rootPlayerFrame.clipToPadding = false
        playerView.clipChildren = false
        playerView.clipToPadding = false
        for (i in 0 until playerView.childCount) {
            val child = playerView.getChildAt(i)
            if (child is ViewGroup) {
                child.clipChildren = false
                child.clipToPadding = false
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            val newPath = intent.getStringExtra("VIDEO_PATH") ?: ""
            if (newPath.isNotEmpty() && newPath != videoPath) {
                extractIntentData(intent)
                initializePlayer(startPositionOverrideMs = -1L)
            } else {
                panelBgAudioScreen.visibility = View.GONE
                layoutAllControls.visibility = View.VISIBLE
                enterFullImmersiveMode()
            }
        }
    }

    private fun registerPipAndNotifReceiver() {
        if (isPipReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(ACTION_PIP_MUTE)
            addAction(ACTION_PIP_CLOSE)
            addAction(BackgroundAudioService.ACTION_NOTIF_PREV)
            addAction(BackgroundAudioService.ACTION_NOTIF_PLAY_PAUSE)
            addAction(BackgroundAudioService.ACTION_NOTIF_NEXT)
            addAction(BackgroundAudioService.ACTION_NOTIF_SEEK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipAndNotifReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(pipAndNotifReceiver, filter)
        }
        isPipReceiverRegistered = true
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
        btnPlayerPrev = findViewById(R.id.btnPlayerPrev)
        btnPlayerPlayPause = findViewById(R.id.btnPlayerPlayPause)
        btnPlayerNext = findViewById(R.id.btnPlayerNext)
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

    private fun setupUnifiedAllWhiteModernIcons() {
        val d = resources.displayMetrics.density
        val pureWhite = Color.WHITE

        val allIconViews = listOf(
            btnBgAudio, btnVideoQueue, btnMoreMenu, btnMuteToggle,
            btnLockScreen, btnUnlockOverlay, btnSideCut, btnRotateScreen,
            btnAspectRatio, btnPipMode, btnPlayerPrev, btnPlayerPlayPause, btnPlayerNext
        )
        allIconViews.forEach {
            it.clearColorFilter()
            it.background = PlayerThreeDotMenuHelper.makeFrostedCircle(d)
        }

        findViewById<ImageView>(R.id.btnPlayerBack)?.apply {
            clearColorFilter()
            background = PlayerThreeDotMenuHelper.makeFrostedCircle(d)
            setImageDrawable(PlayerThreeDotMenuHelper.PlayerVectorIconDrawable("BACK_ARROW", d, pureWhite))
        }

        btnHdrMode.setTextColor(pureWhite)
        btnHdrMode.background = PlayerThreeDotMenuHelper.makeFrostedPill(d)
        if (frameHdrContainer.childCount > 1) {
            frameHdrContainer.getChildAt(1).visibility = View.GONE
        }

        cardSubtitlesCc.setCardBackgroundColor(Color.TRANSPARENT)
        cardSubtitlesCc.cardElevation = 0f
        btnSubtitlesCc.setTextColor(pureWhite)
        btnSubtitlesCc.background = PlayerThreeDotMenuHelper.makeFrostedPill(d)

        btnPlaybackSpeed.setTextColor(pureWhite)
        btnPlaybackSpeed.background = PlayerThreeDotMenuHelper.makeFrostedPill(d)

        btnBgAudio.setImageDrawable(PlayerThreeDotMenuHelper.PlayerVectorIconDrawable("HEADPHONES", d, pureWhite))
        btnVideoQueue.setImageDrawable(PlayerThreeDotMenuHelper.PlayerVectorIconDrawable("QUEUE_LIST", d, pureWhite))
        btnMoreMenu.setImageDrawable(PlayerThreeDotMenuHelper.PlayerVectorIconDrawable("THREE_DOTS", d, pureWhite))
        btnMuteToggle.setImageDrawable(PlayerThreeDotMenuHelper.PlayerVectorIconDrawable("SPEAKER_ON", d, pureWhite))
        btnLockScreen.setImageDrawable(PlayerThreeDotMenuHelper.PlayerVectorIconDrawable("LOCK_OPEN", d, pureWhite))
        btnSideCut.setImageDrawable(PlayerThreeDotMenuHelper.PlayerVectorIconDrawable("SCREENSHOT_CAM", d, pureWhite))
        btnRotateScreen.setImageDrawable(PlayerThreeDotMenuHelper.PlayerVectorIconDrawable("ROTATE", d, pureWhite))
        btnAspectRatio.setImageDrawable(PlayerThreeDotMenuHelper.PlayerVectorIconDrawable("ASPECT_FIT", d, pureWhite))
        btnPipMode.setImageDrawable(PlayerThreeDotMenuHelper.PlayerVectorIconDrawable("PIP_WINDOW", d, pureWhite))

        btnPlayerPrev.setImageDrawable(PlayerThreeDotMenuHelper.PlayerVectorIconDrawable("PREV", d, pureWhite))
        btnPlayerPlayPause.setImageDrawable(PlayerThreeDotMenuHelper.PlayerVectorIconDrawable("PAUSE", d, pureWhite))
        btnPlayerNext.setImageDrawable(PlayerThreeDotMenuHelper.PlayerVectorIconDrawable("NEXT", d, pureWhite))

        btnUnlockOverlay.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#E610141E"))
            setStroke((1.8f * d).toInt(), pureWhite)
        }
        btnUnlockOverlay.setImageDrawable(PlayerThreeDotMenuHelper.PlayerVectorIconDrawable("LOCK_CLOSED", d, pureWhite))
    }

    private fun restorePersistentBrightnessAndVisuals() {
        val prefs = getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        currentBrightnessPercent = prefs.getFloat("persistent_brightness_pct", 75f).coerceIn(5f, 100f)
        val lp = window.attributes
        lp.screenBrightness = currentBrightnessPercent / 100f
        window.attributes = lp
        findViewById<SeekBar>(R.id.seekBarBrightness)?.progress = currentBrightnessPercent.toInt()

        applyVisualToAll = prefs.getBoolean("visual_apply_all", false)
        if (applyVisualToAll) {
            currentVisualMode = prefs.getInt("saved_visual_mode", 0)
            applyVisualEnhancerFilter(currentVisualMode)
        }
    }

    private fun buildModularOverlayPanels() {
        val d = resources.displayMetrics.density

        panelOverlayScrim = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#66000000"))
            elevation = 12f * d
            visibility = View.GONE
            setOnClickListener { closeAllModularPanels() }
        }
        rootPlayerFrame.addView(panelOverlayScrim)

        panelVisualEnhancer = CardView(this).apply {
            setCardBackgroundColor(Color.parseColor("#F50A0A10"))
            radius = 22f * d
            cardElevation = 40f * d
            visibility = View.GONE
        }
        val veScroll = ScrollView(this)
        val veContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt())
        }
        val tvVeTitle = TextView(this).apply {
            text = "<-   8K AI Upscaler & Visual Enhancer"
            setTextColor(Color.WHITE)
            textSize = 16.5f
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { closeAllModularPanels() }
        }
        veContainer.addView(tvVeTitle)

        val modesGrid = GridLayout(this).apply {
            columnCount = 2
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (12 * d).toInt() }
        }
        val modeNames = arrayOf(
            "Original",
            "8K AI Upscale HDR",
            "Clear Sharp",
            "Ultra Clear",
            "Crystal Ice Blue",
            "Warm Cinema",
            "AMOLED Contrast"
        )
        val modePreviewColors = arrayOf("#2A2A34", "#5E3524", "#225529", "#602825", "#154C79", "#7A4914", "#4A3728")
        val modeCardViews = mutableListOf<CardView>()

        modeNames.forEachIndexed { index, name ->
            val card = CardView(this).apply {
                radius = 12f * d
                cardElevation = 2f * d
                setCardBackgroundColor(Color.parseColor(modePreviewColors[index]))
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = (62 * d).toInt()
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins((5 * d).toInt(), (5 * d).toInt(), (5 * d).toInt(), (5 * d).toInt())
                }
            }
            val box = FrameLayout(this)
            val tvName = TextView(this).apply {
                text = if (currentVisualMode == index) "[Active] $name" else name
                setTextColor(Color.WHITE)
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            box.addView(tvName)
            card.addView(box)
            card.setOnClickListener {
                currentVisualMode = index
                applyVisualEnhancerFilter(index)
                modeCardViews.forEachIndexed { idx, cv ->
                    val label = (cv.getChildAt(0) as FrameLayout).getChildAt(0) as TextView
                    label.text = if (idx == index) "[Active] ${modeNames[idx]}" else modeNames[idx]
                }
            }
            modeCardViews.add(card)
            modesGrid.addView(card)
        }
        veContainer.addView(modesGrid)

        val applyAllRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((6 * d).toInt(), (14 * d).toInt(), (6 * d).toInt(), (4 * d).toInt())
        }
        val tvApplyAll = TextView(this).apply {
            text = "Apply enhancer to all videos"
            setTextColor(Color.WHITE)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val swApplyAll = Switch(this).apply {
            isChecked = applyVisualToAll
            setOnCheckedChangeListener { _, isChecked ->
                applyVisualToAll = isChecked
                getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("visual_apply_all", isChecked)
                    .putInt("saved_visual_mode", currentVisualMode)
                    .apply()
            }
        }
        applyAllRow.addView(tvApplyAll)
        applyAllRow.addView(swApplyAll)
        veContainer.addView(applyAllRow)
        veScroll.addView(veContainer)
        panelVisualEnhancer.addView(veScroll)
        rootPlayerFrame.addView(panelVisualEnhancer)

        panelSubtitles = CardView(this).apply {
            setCardBackgroundColor(Color.parseColor("#F50A0A10"))
            radius = 22f * d
            cardElevation = 40f * d
            visibility = View.GONE
        }
        rootPlayerFrame.addView(panelSubtitles)

        panelPlayingQueue = CardView(this).apply {
            setCardBackgroundColor(Color.parseColor("#F50A0A10"))
            radius = 22f * d
            cardElevation = 40f * d
            visibility = View.GONE
        }
        val queueRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((14 * d).toInt(), (14 * d).toInt(), (14 * d).toInt(), (14 * d).toInt())
        }
        val queueTopBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (10 * d).toInt())
        }
        tvQueueHeaderCount = TextView(this).apply {
            text = "<-   Playing Queue (1/1)"
            setTextColor(Color.WHITE)
            textSize = 16.5f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { closeAllModularPanels() }
        }
        btnQueueLoopModeToggle = TextView(this).apply {
            text = "Order"
            setTextColor(Color.WHITE)
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((14 * d).toInt(), (6 * d).toInt(), (14 * d).toInt(), (6 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#28FFFFFF"))
                cornerRadius = 16f * d
            }
            setOnClickListener {
                queueLoopMode = (queueLoopMode + 1) % 3
                syncQueueLoopModeButton()
            }
        }
        queueTopBar.addView(tvQueueHeaderCount)
        queueTopBar.addView(btnQueueLoopModeToggle)
        queueRoot.addView(queueTopBar)

        rvQueueList = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            layoutManager = LinearLayoutManager(this@PlayerActivity)
            setHasFixedSize(true)
            setItemViewCacheSize(20)
        }
        setupQueueRecyclerViewAndDragHelper()
        queueRoot.addView(rvQueueList)
        panelPlayingQueue.addView(queueRoot)
        rootPlayerFrame.addView(panelPlayingQueue)

        panelSpeedRuler = CardView(this).apply {
            setCardBackgroundColor(Color.parseColor("#F210141E"))
            radius = 22f * d
            cardElevation = 42f * d
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                (340 * d).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }
        val speedCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((18 * d).toInt(), (16 * d).toInt(), (18 * d).toInt(), (18 * d).toInt())
        }
        val speedTopRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val btnSpeedMinus = TextView(this).apply {
            text = "-"
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((38 * d).toInt(), (38 * d).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#33FFFFFF"))
            }
            setOnClickListener {
                val next = ((currentSpeed - 0.1f) * 10f).roundToInt() / 10f
                applySpeedDirect(next.coerceIn(0.25f, 8.0f))
            }
        }
        val centerSpeedBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvSpeedRulerValue = TextView(this).apply {
            text = "1.00 X"
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        }
        val btnSpeedReset = TextView(this).apply {
            text = "1x"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((32 * d).toInt(), (32 * d).toInt()).apply {
                marginStart = (10 * d).toInt()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#33FFFFFF"))
            }
            setOnClickListener { applySpeedDirect(1.0f) }
        }
        centerSpeedBox.addView(tvSpeedRulerValue)
        centerSpeedBox.addView(btnSpeedReset)

        val btnSpeedPlus = TextView(this).apply {
            text = "+"
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((38 * d).toInt(), (38 * d).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#33FFFFFF"))
            }
            setOnClickListener {
                val next = ((currentSpeed + 0.1f) * 10f).roundToInt() / 10f
                applySpeedDirect(next.coerceIn(0.25f, 8.0f))
            }
        }
        speedTopRow.addView(btnSpeedMinus)
        speedTopRow.addView(centerSpeedBox)
        speedTopRow.addView(btnSpeedPlus)
        speedCol.addView(speedTopRow)

        speedRulerView = PlayerThreeDotMenuHelper.SpeedTickRulerView(this, d) { slidSpeed ->
            applySpeedDirect(slidSpeed)
        }
        speedRulerView.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (64 * d).toInt()
        ).apply { topMargin = (10 * d).toInt() }
        speedCol.addView(speedRulerView)
        panelSpeedRuler.addView(speedCol)
        rootPlayerFrame.addView(panelSpeedRuler)

        panelBgAudioScreen = RelativeLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#78000000"))
            elevation = 45f * d
            visibility = View.GONE
        }

        btnBgCenterBackToVideo = TextView(this).apply {
            text = "BACKGROUND PLAY ACTIVE\nTap Here to Return to Video"
            setTextColor(Color.WHITE)
            textSize = 15.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((28 * d).toInt(), (18 * d).toInt(), (28 * d).toInt(), (18 * d).toInt())
            layoutParams = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.CENTER_IN_PARENT)
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E6141E30"))
                cornerRadius = 26f * d
                setStroke((1.8f * d).toInt(), Color.WHITE)
            }
            setOnClickListener {
                panelBgAudioScreen.visibility = View.GONE
                layoutAllControls.visibility = View.VISIBLE
            }
        }
        panelBgAudioScreen.addView(btnBgCenterBackToVideo)

        val bottomBgCard = CardView(this).apply {
            radius = 24f * d
            cardElevation = 24f * d
            setCardBackgroundColor(Color.parseColor("#F50D131F"))
            layoutParams = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                setMargins((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (20 * d).toInt())
            }
        }

        val bgDockInner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * d).toInt(), (16 * d).toInt(), (18 * d).toInt(), (18 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F50D131F"))
                cornerRadius = 24f * d
                setStroke((1.5f * d).toInt(), Color.parseColor("#4DFFFFFF"))
            }
        }

        val bgTopRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val bgTitleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (10 * d).toInt()
            }
            setOnClickListener {
                panelBgAudioScreen.visibility = View.GONE
                layoutAllControls.visibility = View.VISIBLE
            }
        }

        tvBgBottomTitle = TextView(this).apply {
            text = "Background Audio"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        tvBgBottomTime = TextView(this).apply {
            text = "00:00 / 00:00"
            setTextColor(Color.parseColor("#D0D8E8"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, (3 * d).toInt(), 0, 0)
        }

        bgTitleCol.addView(tvBgBottomTitle)
        bgTitleCol.addView(tvBgBottomTime)

        btnBgFastMp3Convert = TextView(this).apply {
            text = "Convert to MP3"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((14 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 16f * d
            }
            setOnClickListener {
                btnBgFastMp3Convert.isEnabled = false
                btnBgFastMp3Convert.text = "Converting..."
                Mp3ConverterActivity.convertVideoToAudioInstant(
                    context = this@PlayerActivity,
                    videoPath = videoPath,
                    videoTitle = videoTitle,
                    onProgress = { pct ->
                        btnBgFastMp3Convert.text = "MP3 $pct%"
                    },
                    onFinished = { success, outPathOrMsg ->
                        btnBgFastMp3Convert.isEnabled = true
                        btnBgFastMp3Convert.text = if (success) "MP3 Saved!" else "Convert to MP3"
                        Toast.makeText(
                            this@PlayerActivity,
                            if (success) "Instant Audio Saved: $outPathOrMsg" else outPathOrMsg,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }

        bgTopRow.addView(bgTitleCol)
        bgTopRow.addView(btnBgFastMp3Convert)
        bgDockInner.addView(bgTopRow)

        sbBgBottomSeek = SeekBar(this).apply {
            max = 1000
            progressDrawable.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            thumb.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (10 * d).toInt()
                bottomMargin = (10 * d).toInt()
            }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) {
                        seekToUnified(p.toLong())
                        tvBgBottomTime.text = "${formatDuration(p.toLong())} / ${formatDuration(getDurationUnified())}"
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {
                    syncBackgroundAudioUiAndService()
                }
            })
        }
        bgDockInner.addView(sbBgBottomSeek)

        val bgTransportRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, (4 * d).toInt(), 0, 0)
        }

        val btnBgPrev = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((48 * d).toInt(), (48 * d).toInt()).apply {
                marginEnd = (16 * d).toInt()
            }
            setPadding((11 * d).toInt(), (11 * d).toInt(), (11 * d).toInt(), (11 * d).toInt())
            setImageDrawable(PlayerThreeDotMenuHelper.PlayerVectorIconDrawable("PREV", d, Color.WHITE))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#24FFFFFF"))
            }
            setOnClickListener { playPreviousVideo() }
        }

        btnBgBottomPlayPause = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((58 * d).toInt(), (58 * d).toInt()).apply {
                marginEnd = (16 * d).toInt()
            }
            setPadding((13 * d).toInt(), (13 * d).toInt(), (13 * d).toInt(), (13 * d).toInt())
            setImageDrawable(PlayerThreeDotMenuHelper.PlayerVectorIconDrawable("PAUSE", d, Color.WHITE))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#33FFFFFF"))
                setStroke((1.5f * d).toInt(), Color.WHITE)
            }
            setOnClickListener {
                togglePlayPauseUnified()
            }
        }

        val btnBgNext = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((48 * d).toInt(), (48 * d).toInt()).apply {
                marginEnd = (16 * d).toInt()
            }
            setPadding((11 * d).toInt(), (11 * d).toInt(), (11 * d).toInt(), (11 * d).toInt())
            setImageDrawable(PlayerThreeDotMenuHelper.PlayerVectorIconDrawable("NEXT", d, Color.WHITE))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#24FFFFFF"))
            }
            setOnClickListener { playNextVideo() }
        }

        val btnBgMinimize = TextView(this).apply {
            text = "Minimize"
            setTextColor(Color.WHITE)
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#24FFFFFF"))
                cornerRadius = 16f * d
                setStroke((1.2f * d).toInt(), Color.WHITE)
            }
            setOnClickListener {
                isBgAudioEnabled = true
                syncBackgroundAudioUiAndService()
                moveTaskToBack(true)
            }
        }

        bgTransportRow.addView(btnBgPrev)
        bgTransportRow.addView(btnBgBottomPlayPause)
        bgTransportRow.addView(btnBgNext)
        bgTransportRow.addView(btnBgMinimize)
        bgDockInner.addView(bgTransportRow)

        bottomBgCard.addView(bgDockInner)
        panelBgAudioScreen.addView(bottomBgCard)
        rootPlayerFrame.addView(panelBgAudioScreen)
    }

    /**
     * Brings `panelOverlayScrim` to the front first and then brings `targetPanel` (such as `layoutAdvancedSettings`)
     * directly above it with 48dp elevation so that all scrolling and child button clicks work 100% reliably.
     */
    private fun openHalfScreenPanel(targetPanel: View) {
        val d = resources.displayMetrics.density
        closeAllModularPanels()
        layoutAllControls.visibility = View.GONE

        panelOverlayScrim.elevation = 12f * d
        panelOverlayScrim.translationZ = 12f * d
        panelOverlayScrim.visibility = View.VISIBLE
        panelOverlayScrim.bringToFront()

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels

        val lp = if (isLandscape) {
            FrameLayout.LayoutParams(
                (screenW * 0.56f).toInt(),
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END
            )
        } else {
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (screenH * 0.64f).toInt(),
                Gravity.BOTTOM
            )
        }
        targetPanel.layoutParams = lp
        targetPanel.elevation = 48f * d
        targetPanel.translationZ = 48f * d
        targetPanel.alpha = 1f
        targetPanel.visibility = View.VISIBLE
        targetPanel.bringToFront()
        rootPlayerFrame.requestLayout()
        rootPlayerFrame.invalidate()
    }

    private fun closeAllModularPanels() {
        panelOverlayScrim.visibility = View.GONE
        panelVisualEnhancer.visibility = View.GONE
        panelSubtitles.visibility = View.GONE
        panelPlayingQueue.visibility = View.GONE
        panelSpeedRuler.visibility = View.GONE
        layoutAdvancedSettings.visibility = View.GONE
        enterFullImmersiveMode()
    }

    private fun applyVisualEnhancerFilter(modeIndex: Int) {
        when (modeIndex) {
            0 -> {
                viewVisualOverlay.setBackgroundColor(
                    if (isNightMode) Color.parseColor("#88000000") else Color.TRANSPARENT
                )
            }
            1 -> viewVisualOverlay.setBackgroundColor(Color.parseColor("#22FF9800"))
            2 -> viewVisualOverlay.setBackgroundColor(Color.parseColor("#1800E5FF"))
            3 -> viewVisualOverlay.setBackgroundColor(Color.parseColor("#24FF4081"))
            4 -> viewVisualOverlay.setBackgroundColor(Color.parseColor("#2800B0FF"))
            5 -> viewVisualOverlay.setBackgroundColor(Color.parseColor("#30FFAB00"))
            6 -> viewVisualOverlay.setBackgroundColor(Color.parseColor("#2CD4AF37"))
        }
        if (applyVisualToAll) {
            getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
                .edit()
                .putInt("saved_visual_mode", modeIndex)
                .apply()
        }
    }

    private fun applyPlayerCustomizationPreferences() {
        val prefs = getSharedPreferences("MAX_LAYOUT_PREFS", Context.MODE_PRIVATE)
        val dockSideIconsToBottom = prefs.getBoolean("player_dock_side_to_bottom", false)
        val showSideControls = prefs.getBoolean("player_show_side_controls", true)
        val showTopHdrAndCc = prefs.getBoolean("player_show_hdr_cc", true)

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
            "Audio Lang" to { PlayerThreeDotMenuHelper.showMultiLanguageAudioTrackDialog(this, exoPlayer) },
            "Lock" to { lockScreenNow() },
            "Mute" to { toggleMuteNow() },
            "Screenshot" to { captureVideoScreenshotNow() },
            "Rotate" to { toggleRotationNow() },
            "Equalizer" to {
                closeAllModularPanels()
                EqualizerHelper.showEqualizerDialog(this, exoPlayer?.audioSessionId ?: 0)
            },
            "MP3 Convert" to {
                Mp3ConverterActivity.convertVideoToAudioInstant(this, videoPath, videoTitle) { ok, msg ->
                    Toast.makeText(this, if (ok) "Saved: $msg" else msg, Toast.LENGTH_LONG).show()
                }
            },
            "Delete" to { deleteCurrentVideoPermanently() }
        )

        for ((label, action) in tools) {
            val pill = TextView(this).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dpToPx(14), dpToPx(6), dpToPx(14), dpToPx(6))
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
            videoList.clear()
            if (!listExtra.isNullOrEmpty()) {
                videoList.addAll(listExtra)
                currentPositionIndex = videoList.indexOf(videoPath).coerceAtLeast(0)
            } else if (videoPath.isNotEmpty()) {
                videoList.add(videoPath)
                currentPositionIndex = 0
            }

            tvPlayerTitle.text = videoTitle
            fallbackStage = 0
            isUsingNativeFallback = false

            val prefs = getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
            if (videoPath.isNotEmpty()) {
                prefs.edit().putLong("history_time_$videoPath", System.currentTimeMillis()).apply()
            }
            isFavorite = prefs.getBoolean("fav_$videoPath", false)
            updateFavoriteUi()
        } catch (_: Exception) {
        }
    }

    private fun resolveSafePlayableUri(rawPath: String): Uri {
        return try {
            if (rawPath.startsWith("content://") || rawPath.startsWith("http://") || rawPath.startsWith("https://")) {
                Uri.parse(rawPath)
            } else {
                val f = File(rawPath)
                if (f.exists()) Uri.fromFile(f) else Uri.parse(rawPath)
            }
        } catch (_: Exception) {
            Uri.fromFile(File(rawPath))
        }
    }

    private fun initializePlayer(startPositionOverrideMs: Long = -1L) {
        try {
            val d = resources.displayMetrics.density
            val resumePos = if (startPositionOverrideMs >= 0L) {
                startPositionOverrideMs
            } else {
                getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
                    .getLong("watched_pos_$videoPath", 0L)
            }

            nativeFallbackVideoView?.let {
                it.stopPlayback()
                rootPlayerFrame.removeView(it)
            }
            nativeFallbackVideoView = null
            isUsingNativeFallback = false
            playerView.visibility = View.VISIBLE

            exoPlayer?.release()

            val renderersFactory = DefaultRenderersFactory(this).apply {
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                setEnableDecoderFallback(true)
            }

            val fastLoadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(2000, 35000, 150, 300)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            exoPlayer = ExoPlayer.Builder(this, renderersFactory)
                .setLoadControl(fastLoadControl)
                .build().also { player ->
                    player.setSeekParameters(SeekParameters.CLOSEST_SYNC)

                    if (fallbackStage >= 1) {
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .setMaxVideoSize(1920, 1080)
                            .build()
                    }

                    playerView.player = player
                    disableClippingForFullScreenZoom()

                    playerView.subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, subtitleTextSizeSp)
                    playerView.subtitleView?.setStyle(
                        CaptionStyleCompat(
                            Color.WHITE,
                            Color.parseColor("#66000000"),
                            Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                            Color.BLACK,
                            Typeface.DEFAULT_BOLD
                        )
                    )

                    if (videoList.isNotEmpty()) {
                        val mediaItems = videoList.map { path ->
                            MediaItem.fromUri(resolveSafePlayableUri(path))
                        }
                        player.setMediaItems(mediaItems, currentPositionIndex, resumePos.coerceAtLeast(0L))
                    } else if (videoPath.isNotEmpty()) {
                        player.setMediaItem(MediaItem.fromUri(resolveSafePlayableUri(videoPath)), resumePos.coerceAtLeast(0L))
                    }

                    player.repeatMode = Player.REPEAT_MODE_ALL
                    player.prepare()
                    player.playWhenReady = true

                    mediaSessionManager?.release()
                    mediaSessionManager = MediaSessionManager(
                        context = this,
                        playerProvider = { exoPlayer },
                        onNextTrack = { playNextVideo() },
                        onPrevTrack = { playPreviousVideo() },
                        onPlayStateChanged = { isPlaying ->
                            btnPlayerPlayPause.setImageDrawable(
                                PlayerThreeDotMenuHelper.PlayerVectorIconDrawable(
                                    if (isPlaying) "PAUSE" else "PLAY",
                                    d,
                                    Color.WHITE
                                )
                            )
                            btnBgBottomPlayPause.setImageDrawable(
                                PlayerThreeDotMenuHelper.PlayerVectorIconDrawable(
                                    if (isPlaying) "PAUSE" else "PLAY",
                                    d,
                                    Color.WHITE
                                )
                            )
                            syncBackgroundAudioUiAndService()
                        }
                    ).also { it.requestAudioFocusAndRegister() }

                    player.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            btnPlayerPlayPause.setImageDrawable(
                                PlayerThreeDotMenuHelper.PlayerVectorIconDrawable(
                                    if (isPlaying) "PAUSE" else "PLAY",
                                    d,
                                    Color.WHITE
                                )
                            )
                            btnBgBottomPlayPause.setImageDrawable(
                                PlayerThreeDotMenuHelper.PlayerVectorIconDrawable(
                                    if (isPlaying) "PAUSE" else "PLAY",
                                    d,
                                    Color.WHITE
                                )
                            )
                            mediaSessionManager?.updatePlaybackState()
                            syncBackgroundAudioUiAndService()
                        }

                        override fun onAudioSessionIdChanged(audioSessionId: Int) {
                            setupLoudnessEnhancer(audioSessionId)
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            val savedPos = player.currentPosition.coerceAtLeast(0L)
                            if (fallbackStage == 0) {
                                fallbackStage = 1
                                initializePlayer(startPositionOverrideMs = savedPos)
                            } else if (fallbackStage == 1) {
                                fallbackStage = 2
                                activateNativeVideoViewFallback(savedPos)
                            }
                        }

                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            if (stopAfterCurrent && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                                player.pause()
                            }
                            val index = player.currentMediaItemIndex
                            if (index in videoList.indices) {
                                currentPositionIndex = index
                                videoPath = videoList[index]
                                videoTitle = File(videoPath).name
                                tvPlayerTitle.text = videoTitle
                                tvBgBottomTitle.text = videoTitle
                                fallbackStage = 0
                                getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
                                    .edit()
                                    .putLong("history_time_$videoPath", System.currentTimeMillis())
                                    .apply()
                                rvQueueList.adapter?.notifyDataSetChanged()
                                updateQueueHeaderTitle()
                                mediaSessionManager?.updateMetadataAndState(
                                    title = videoTitle,
                                    artist = "Hybrid Player 8K",
                                    durationMs = player.duration.coerceAtLeast(1000L)
                                )
                                syncBackgroundAudioUiAndService()
                            }
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_READY) {
                                val duration = player.duration.coerceAtLeast(1L)
                                seekBarPlayer.max = duration.toInt()
                                sbBgBottomSeek.max = duration.toInt()
                                tvPlayerTotalTime.text = formatDuration(duration)
                                setupLoudnessEnhancer(player.audioSessionId)
                                mediaSessionManager?.updateMetadataAndState(
                                    title = videoTitle,
                                    artist = "Hybrid Player 8K",
                                    durationMs = duration
                                )
                                RateUsDialogHelper.recordVideoPlayedAndCheckPrompt(this@PlayerActivity)
                            }
                        }
                    })
                }
            handler.removeCallbacks(updateProgressAction)
            handler.post(updateProgressAction)
        } catch (_: Exception) {
            activateNativeVideoViewFallback(0L)
        }
    }

    private fun activateNativeVideoViewFallback(resumePosMs: Long) {
        try {
            val d = resources.displayMetrics.density
            exoPlayer?.release()
            exoPlayer = null
            isUsingNativeFallback = true
            playerView.visibility = View.GONE

            val vv = VideoView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
            }
            nativeFallbackVideoView = vv
            rootPlayerFrame.addView(vv, 0)

            vv.setVideoURI(resolveSafePlayableUri(videoPath))
            vv.setOnPreparedListener { mp ->
                mp.isLooping = true
                if (resumePosMs > 0L) {
                    vv.seekTo(resumePosMs.toInt())
                }
                vv.start()
                val dur = vv.duration.coerceAtLeast(1000)
                seekBarPlayer.max = dur
                sbBgBottomSeek.max = dur
                tvPlayerTotalTime.text = formatDuration(dur.toLong())
                btnPlayerPlayPause.setImageDrawable(
                    PlayerThreeDotMenuHelper.PlayerVectorIconDrawable("PAUSE", d, Color.WHITE)
                )
                btnBgBottomPlayPause.setImageDrawable(
                    PlayerThreeDotMenuHelper.PlayerVectorIconDrawable("PAUSE", d, Color.WHITE)
                )
            }
            vv.setOnErrorListener { _, _, _ -> true }
        } catch (_: Exception) {
        }
    }

    private fun syncBackgroundAudioUiAndService() {
        val d = resources.displayMetrics.density
        val playing = isPlayingUnified()
        val curPos = getCurrentPositionUnified()
        val dur = getDurationUnified()

        tvBgBottomTitle.text = videoTitle
        tvBgBottomTime.text = "${formatDuration(curPos)} / ${formatDuration(dur)}"
        btnBgBottomPlayPause.setImageDrawable(
            PlayerThreeDotMenuHelper.PlayerVectorIconDrawable(if (playing) "PAUSE" else "PLAY", d, Color.WHITE)
        )

        if (isBgAudioEnabled) {
            VideoFragment.updateBackgroundBarState(
                visible = true,
                title = videoTitle,
                path = videoPath,
                isPlaying = playing,
                positionMs = curPos,
                durationMs = dur
            )
            BackgroundAudioService.updateForegroundNotification(
                context = this,
                title = videoTitle,
                artist = "Hybrid Player Background Audio",
                isPlaying = playing,
                isVideoBackground = true,
                videoPath = videoPath,
                positionMs = curPos,
                durationMs = dur
            )
        }
    }

    private fun setupLoudnessEnhancer(sessionId: Int) {
        if (sessionId == 0 || sessionId == C.AUDIO_SESSION_ID_UNSET) return
        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                enabled = true
            }
            val boost200 = getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
                .getBoolean("super_loud_200_boost", true)
            if (boost200) {
                loudnessEnhancer?.setTargetGain(1400)
            }
        } catch (_: Exception) {
        }
    }

    private fun activateBackgroundAudioMode() {
        val d = resources.displayMetrics.density
        isBgAudioEnabled = true
        layoutAllControls.visibility = View.GONE
        panelBgAudioScreen.elevation = 48f * d
        panelBgAudioScreen.visibility = View.VISIBLE
        panelBgAudioScreen.bringToFront()
        syncBackgroundAudioUiAndService()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            isScreenLocked -> {
                showAutoHidingLockIconFor3Seconds()
                Toast.makeText(this, "Tap the Lock icon to unlock screen", Toast.LENGTH_SHORT).show()
            }
            panelOverlayScrim.visibility == View.VISIBLE || layoutAdvancedSettings.visibility == View.VISIBLE -> {
                closeAllModularPanels()
                layoutAllControls.visibility = View.VISIBLE
            }
            panelBgAudioScreen.visibility == View.VISIBLE -> {
                panelBgAudioScreen.visibility = View.GONE
                layoutAllControls.visibility = View.VISIBLE
            }
            isBgAudioEnabled && isPlayingUnified() -> {
                syncBackgroundAudioUiAndService()
                moveTaskToBack(true)
            }
            else -> {
                super.onBackPressed()
            }
        }
    }

    private fun setupTopAndSideControls() {
        findViewById<ImageView>(R.id.btnPlayerBack).setOnClickListener {
            onBackPressed()
        }

        val hdrClick = View.OnClickListener {
            openHalfScreenPanel(panelVisualEnhancer)
        }
        btnHdrMode.setOnClickListener(hdrClick)
        frameHdrContainer.setOnClickListener(hdrClick)

        val ccClick = View.OnClickListener {
            populateAndOpenSubtitlePanel()
        }
        btnSubtitlesCc.setOnClickListener(ccClick)
        cardSubtitlesCc.setOnClickListener(ccClick)

        btnBgAudio.setOnClickListener {
            activateBackgroundAudioMode()
        }

        btnVideoQueue.setOnClickListener {
            updateQueueHeaderTitle()
            rvQueueList.adapter?.notifyDataSetChanged()
            openHalfScreenPanel(panelPlayingQueue)
            rvQueueList.scrollToPosition(currentPositionIndex.coerceAtLeast(0))
        }

        btnMoreMenu.setOnClickListener {
            openHalfScreenPanel(layoutAdvancedSettings)
        }

        btnMuteToggle.setOnClickListener { toggleMuteNow() }
        btnLockScreen.setOnClickListener { lockScreenNow() }

        btnUnlockOverlay.setOnClickListener {
            val d = resources.displayMetrics.density
            isScreenLocked = false
            handler.removeCallbacks(autoHideLockIconRunnable)
            btnUnlockOverlay.visibility = View.GONE
            btnLockScreen.setImageDrawable(
                PlayerThreeDotMenuHelper.PlayerVectorIconDrawable("LOCK_OPEN", d, Color.WHITE)
            )
            layoutAllControls.visibility = View.VISIBLE
            enterFullImmersiveMode()
            Toast.makeText(this, "Screen Unlocked", Toast.LENGTH_SHORT).show()
        }

        btnSideCut.setOnClickListener { captureVideoScreenshotNow() }
        btnRotateScreen.setOnClickListener { toggleRotationNow() }
    }

    private fun showAutoHidingLockIconFor3Seconds() {
        btnUnlockOverlay.animate().cancel()
        btnUnlockOverlay.alpha = 1f
        btnUnlockOverlay.visibility = View.VISIBLE
        btnUnlockOverlay.bringToFront()
        handler.removeCallbacks(autoHideLockIconRunnable)
        handler.postDelayed(autoHideLockIconRunnable, 3000L)
    }

    private fun populateAndOpenSubtitlePanel() {
        val d = resources.displayMetrics.density
        panelSubtitles.removeAllViews()

        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt())
        }

        val tvHeader = TextView(this).apply {
            text = "<-   Subtitles & Auto-Sync Timing"
            setTextColor(Color.WHITE)
            textSize = 16.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (12 * d).toInt())
            setOnClickListener { closeAllModularPanels() }
        }
        col.addView(tvHeader)

        val player = exoPlayer
        val textGroups = player?.currentTracks?.groups?.filter { it.type == C.TRACK_TYPE_TEXT } ?: emptyList()

        col.addView(createSubtitleActionRow("None (Turn Off Subtitles)", d) {
            player?.let { p ->
                p.trackSelectionParameters = p.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
            }
            Toast.makeText(this, "Subtitles Off", Toast.LENGTH_SHORT).show()
            closeAllModularPanels()
        })

        textGroups.forEachIndexed { index, group ->
            val fmt = group.getTrackFormat(0)
            val name = fmt.label ?: fmt.language ?: "Embedded Subtitle ${index + 1}"
            val row = createSubtitleActionRow("Track: $name", d) {
                player?.let { p ->
                    p.trackSelectionParameters = p.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                        .build()
                }
                Toast.makeText(this, "Subtitle Active: $name", Toast.LENGTH_SHORT).show()
                closeAllModularPanels()
            }
            col.addView(row)
        }

        col.addView(createSubtitleActionRow("Select Local Subtitle File (.srt / .ass / .vtt)", d) {
            try {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                startActivityForResult(intent, REQ_PICK_SUBTITLE)
            } catch (_: Exception) {
                Toast.makeText(this, "Unable to open file picker", Toast.LENGTH_SHORT).show()
            }
        })

        val syncRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (12 * d).toInt(), 0, (8 * d).toInt())
        }
        val tvSyncStatus = TextView(this).apply {
            text = "Subtitle Sync Delay: ${subtitleSyncOffsetMs}ms"
            setTextColor(Color.WHITE)
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnSyncMinus = TextView(this).apply {
            text = "-100ms"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((10 * d).toInt(), (6 * d).toInt(), (10 * d).toInt(), (6 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#33FFFFFF"))
                cornerRadius = 12f * d
            }
            setOnClickListener {
                subtitleSyncOffsetMs -= 100L
                tvSyncStatus.text = "Subtitle Sync Delay: ${subtitleSyncOffsetMs}ms"
                playerView.subtitleView?.translationY = (subtitleSyncOffsetMs / 100f).coerceIn(-40f, 40f)
            }
        }
        val btnSyncPlus = TextView(this).apply {
            text = "+100ms"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((10 * d).toInt(), (6 * d).toInt(), (10 * d).toInt(), (6 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = (8 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#33FFFFFF"))
                cornerRadius = 12f * d
            }
            setOnClickListener {
                subtitleSyncOffsetMs += 100L
                tvSyncStatus.text = "Subtitle Sync Delay: ${subtitleSyncOffsetMs}ms"
                playerView.subtitleView?.translationY = (subtitleSyncOffsetMs / 100f).coerceIn(-40f, 40f)
            }
        }
        syncRow.addView(tvSyncStatus)
        syncRow.addView(btnSyncMinus)
        syncRow.addView(btnSyncPlus)
        col.addView(syncRow)

        scroll.addView(col)
        panelSubtitles.addView(scroll)
        openHalfScreenPanel(panelSubtitles)
    }

    private fun createSubtitleActionRow(label: String, d: Float, onClick: () -> Unit): View {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val tv = TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, (14 * d).toInt(), 0, (14 * d).toInt())
            setOnClickListener { onClick() }
        }
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (1 * d).toInt()
            )
            setBackgroundColor(Color.parseColor("#22FFFFFF"))
        }
        wrapper.addView(tv)
        wrapper.addView(divider)
        return wrapper
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_SUBTITLE && resultCode == Activity.RESULT_OK) {
            val subUri = data?.data ?: return
            Toast.makeText(this, "Subtitle Loaded: ${subUri.lastPathSegment}", Toast.LENGTH_SHORT).show()
            closeAllModularPanels()
        }
    }

    private fun syncQueueLoopModeButton() {
        when (queueLoopMode) {
            0 -> {
                btnQueueLoopModeToggle.text = "Order"
                updateLoopSelection(2)
            }
            1 -> {
                btnQueueLoopModeToggle.text = "Repeat 1"
                updateLoopSelection(1)
            }
            2 -> {
                btnQueueLoopModeToggle.text = "Shuffle"
                updateLoopSelection(0)
            }
        }
    }

    private fun updateQueueHeaderTitle() {
        val cur = (currentPositionIndex + 1).coerceAtMost(videoList.size.coerceAtLeast(1))
        tvQueueHeaderCount.text = "<-   Playing Queue ($cur/${videoList.size})"
    }

    private fun setupQueueRecyclerViewAndDragHelper() {
        val d = resources.displayMetrics.density
        val watchedPrefs = getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)

        lateinit var touchHelper: ItemTouchHelper

        val adapter = object : RecyclerView.Adapter<QueueItemVH>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueItemVH {
                val row = LinearLayout(this@PlayerActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, (6 * d).toInt(), 0, (6 * d).toInt())
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }

                val thumbCard = CardView(this@PlayerActivity).apply {
                    radius = 8f * d
                    setCardBackgroundColor(Color.parseColor("#182232"))
                    layoutParams = LinearLayout.LayoutParams((100 * d).toInt(), (56 * d).toInt())
                }
                val frame = FrameLayout(this@PlayerActivity)
                val img = ImageView(this@PlayerActivity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                val pb = ProgressBar(
                    this@PlayerActivity,
                    null,
                    android.R.attr.progressBarStyleHorizontal
                ).apply {
                    max = 1000
                    progressDrawable.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (3 * d).toInt(),
                        Gravity.BOTTOM
                    )
                }
                frame.addView(img)
                frame.addView(pb)
                thumbCard.addView(frame)

                val textCol = LinearLayout(this@PlayerActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = (10 * d).toInt()
                        marginEnd = (6 * d).toInt()
                    }
                }
                val tvTitle = TextView(this@PlayerActivity).apply {
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                }
                val tvMeta = TextView(this@PlayerActivity).apply {
                    textSize = 11f
                    setPadding(0, (3 * d).toInt(), 0, 0)
                }
                textCol.addView(tvTitle)
                textCol.addView(tvMeta)

                val btnDrag = TextView(this@PlayerActivity).apply {
                    text = "="
                    setTextColor(Color.WHITE)
                    textSize = 20f
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams((32 * d).toInt(), (40 * d).toInt())
                }

                val btnRemove = TextView(this@PlayerActivity).apply {
                    text = "X"
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams((32 * d).toInt(), (40 * d).toInt())
                }

                row.addView(thumbCard)
                row.addView(textCol)
                row.addView(btnDrag)
                row.addView(btnRemove)
                return QueueItemVH(row, img, pb, tvTitle, tvMeta, btnDrag, btnRemove)
            }

            override fun onBindViewHolder(holder: QueueItemVH, position: Int) {
                val path = videoList[position]
                val file = File(path)
                val name = file.name.ifEmpty { "Video ${position + 1}" }
                val isPlayingNow = position == currentPositionIndex

                holder.tvTitle.text = if (isPlayingNow) "Playing: $name" else name
                holder.tvTitle.setTextColor(Color.WHITE)

                val sizeBytes = if (file.exists()) file.length() else 0L
                val sizeMb = sizeBytes / (1024.0 * 1024.0)
                val sizeLabel = if (sizeMb >= 1024.0) {
                    String.format(Locale.US, "%.2fGB", sizeMb / 1024.0)
                } else {
                    String.format(Locale.US, "%.1fMB", sizeMb)
                }

                holder.tvMeta.text = sizeLabel
                holder.tvMeta.setTextColor(Color.parseColor("#B0C4DE"))

                val watched = watchedPrefs.getLong("watched_pos_$path", 0L)
                holder.pb.progress = if (watched > 0L) 650 else 0

                VideoAdapter.loadGuaranteedVideoThumbnail(this@PlayerActivity, path, holder.img)

                holder.itemView.setOnClickListener {
                    val pos = holder.adapterPosition
                    if (pos in videoList.indices) {
                        currentPositionIndex = pos
                        videoPath = videoList[pos]
                        videoTitle = File(videoPath).name
                        if (isUsingNativeFallback) {
                            initializePlayer(startPositionOverrideMs = 0L)
                        } else {
                            exoPlayer?.seekToDefaultPosition(pos)
                            exoPlayer?.play()
                        }
                        notifyDataSetChanged()
                        updateQueueHeaderTitle()
                    }
                }

                holder.btnDrag.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        touchHelper.startDrag(holder)
                    }
                    false
                }

                holder.btnRemove.setOnClickListener {
                    val pos = holder.adapterPosition
                    if (pos !in videoList.indices) return@setOnClickListener
                    if (videoList.size <= 1) {
                        Toast.makeText(this@PlayerActivity, "Cannot remove last video in queue", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    videoList.removeAt(pos)
                    exoPlayer?.removeMediaItem(pos)
                    if (pos < currentPositionIndex) {
                        currentPositionIndex = (currentPositionIndex - 1).coerceAtLeast(0)
                    } else if (pos == currentPositionIndex) {
                        currentPositionIndex = exoPlayer?.currentMediaItemIndex?.coerceAtLeast(0) ?: 0
                    }
                    notifyItemRemoved(pos)
                    notifyItemRangeChanged(pos, videoList.size)
                    updateQueueHeaderTitle()
                }
            }

            override fun getItemCount(): Int = videoList.size
        }

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                if (fromPos !in videoList.indices || toPos !in videoList.indices) return false

                Collections.swap(videoList, fromPos, toPos)
                exoPlayer?.moveMediaItem(fromPos, toPos)
                currentPositionIndex = exoPlayer?.currentMediaItemIndex ?: currentPositionIndex
                adapter.notifyItemMoved(fromPos, toPos)
                updateQueueHeaderTitle()
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        }

        touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(rvQueueList)
        rvQueueList.adapter = adapter
    }

    private class QueueItemVH(
        view: View,
        val img: ImageView,
        val pb: ProgressBar,
        val tvTitle: TextView,
        val tvMeta: TextView,
        val btnDrag: TextView,
        val btnRemove: TextView
    ) : RecyclerView.ViewHolder(view)

    private fun toggleMuteNow() {
        val d = resources.displayMetrics.density
        isMuted = !isMuted
        exoPlayer?.volume = if (isMuted) 0f else 1f
        btnMuteToggle.setImageDrawable(
            PlayerThreeDotMenuHelper.PlayerVectorIconDrawable(
                if (isMuted) "SPEAKER_MUTE" else "SPEAKER_ON",
                d,
                Color.WHITE
            )
        )
        Toast.makeText(this, if (isMuted) "Audio Muted" else "Audio Unmuted", Toast.LENGTH_SHORT).show()
    }

    private fun lockScreenNow() {
        val d = resources.displayMetrics.density
        isScreenLocked = true
        closeAllModularPanels()
        layoutAllControls.visibility = View.GONE
        cardZoomResetPill.visibility = View.GONE
        cardGestureFeedback.visibility = View.GONE
        btnLockScreen.setImageDrawable(
            PlayerThreeDotMenuHelper.PlayerVectorIconDrawable("LOCK_CLOSED", d, Color.WHITE)
        )
        showAutoHidingLockIconFor3Seconds()
        enterFullImmersiveMode()
        Toast.makeText(this, "Screen Locked (Auto-hides in 3s)", Toast.LENGTH_SHORT).show()
    }

    private fun captureVideoScreenshotNow() {
        val posMs = getCurrentPositionUnified()
        Toast.makeText(this, "Capturing Screenshot...", Toast.LENGTH_SHORT).show()

        thread {
            var bmp: Bitmap? = null
            val retriever = MediaMetadataRetriever()
            try {
                if (videoPath.startsWith("content://")) {
                    retriever.setDataSource(this, Uri.parse(videoPath))
                } else {
                    retriever.setDataSource(videoPath)
                }
                bmp = retriever.getFrameAtTime(posMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
            } catch (_: Exception) {
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }

            var savedLocation = ""
            if (bmp != null) {
                try {
                    val fileName = "Hybrid_Shot_${System.currentTimeMillis()}.jpg"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            put(
                                MediaStore.Images.Media.RELATIVE_PATH,
                                "${Environment.DIRECTORY_PICTURES}/HybridPlayerScreenshots"
                            )
                        }
                        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        if (uri != null) {
                            contentResolver.openOutputStream(uri)?.use { out ->
                                bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
                            }
                            savedLocation = "Pictures/HybridPlayerScreenshots/$fileName"
                        }
                    } else {
                        val dir = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                            "HybridPlayerScreenshots"
                        )
                        if (!dir.exists()) dir.mkdirs()
                        val outFile = File(dir, fileName)
                        FileOutputStream(outFile).use { out ->
                            bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
                        }
                        savedLocation = outFile.absolutePath
                    }
                } catch (_: Exception) {
                }
            }

            runOnUiThread {
                if (savedLocation.isNotEmpty()) {
                    Toast.makeText(this, "Screenshot Saved: $savedLocation", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Unable to save screenshot", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun toggleRotationNow() {
        requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        enterFullImmersiveMode()
    }

    private fun setupBottomControls() {
        btnPlayerPlayPause.setOnClickListener {
            togglePlayPauseUnified()
        }

        btnPlayerPrev.setOnClickListener { playPreviousVideo() }
        btnPlayerNext.setOnClickListener { playNextVideo() }

        seekBarPlayer.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    seekToUnified(progress.toLong())
                    tvPlayerCurrentTime.text = formatDuration(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnPlaybackSpeed.setOnClickListener {
            val d = resources.displayMetrics.density
            closeAllModularPanels()
            layoutAllControls.visibility = View.GONE
            panelOverlayScrim.elevation = 12f * d
            panelOverlayScrim.visibility = View.VISIBLE
            panelOverlayScrim.bringToFront()

            panelSpeedRuler.elevation = 48f * d
            panelSpeedRuler.translationZ = 48f * d
            panelSpeedRuler.visibility = View.VISIBLE
            panelSpeedRuler.bringToFront()
            speedRulerView.setSpeedValue(currentSpeed)
            tvSpeedRulerValue.text = String.format(Locale.US, "%.2f X", currentSpeed)
        }

        btnAspectRatio.setOnClickListener {
            val d = resources.displayMetrics.density
            resizeModeIndex = (resizeModeIndex + 1) % 4
            val mirror = if (isMirrorMode) -1f else 1f
            val surface = playerView.videoSurfaceView ?: playerView

            when (resizeModeIndex) {
                0 -> {
                    playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    surface.scaleX = 1.0f * mirror
                    surface.scaleY = 1.0f
                    btnAspectRatio.setImageDrawable(
                        PlayerThreeDotMenuHelper.PlayerVectorIconDrawable("ASPECT_FIT", d, Color.WHITE)
                    )
                    Toast.makeText(this, "Aspect Ratio: Original Fit", Toast.LENGTH_SHORT).show()
                }
                1 -> {
                    playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                    surface.scaleX = 1.0f * mirror
                    surface.scaleY = 1.0f
                    btnAspectRatio.setImageDrawable(
                        PlayerThreeDotMenuHelper.PlayerVectorIconDrawable("ASPECT_STRETCH", d, Color.WHITE)
                    )
                    Toast.makeText(this, "Aspect Ratio: Full Stretch", Toast.LENGTH_SHORT).show()
                }
                2 -> {
                    playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    surface.scaleX = 1.0f * mirror
                    surface.scaleY = 1.0f
                    btnAspectRatio.setImageDrawable(
                        PlayerThreeDotMenuHelper.PlayerVectorIconDrawable("ASPECT_CROP", d, Color.WHITE)
                    )
                    Toast.makeText(this, "Aspect Ratio: Center Zoom Crop", Toast.LENGTH_SHORT).show()
                }
                3 -> {
                    playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    surface.scaleX = 1.16f * mirror
                    surface.scaleY = 0.94f
                    btnAspectRatio.setImageDrawable(
                        PlayerThreeDotMenuHelper.PlayerVectorIconDrawable("ASPECT_16_9", d, Color.WHITE)
                    )
                    Toast.makeText(this, "Aspect Ratio: 16:9 Cinema", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnPipMode.setOnClickListener {
            enterPipModeSafely()
        }
    }

    private fun playPreviousVideo() {
        if (isUsingNativeFallback && videoList.isNotEmpty()) {
            currentPositionIndex = (currentPositionIndex - 1 + videoList.size) % videoList.size
            videoPath = videoList[currentPositionIndex]
            videoTitle = File(videoPath).name
            tvPlayerTitle.text = videoTitle
            initializePlayer(startPositionOverrideMs = 0L)
            return
        }
        exoPlayer?.let { player ->
            if (player.hasPreviousMediaItem()) {
                player.seekToPreviousMediaItem()
                player.play()
            } else {
                player.seekTo(0)
            }
        }
    }

    private fun playNextVideo() {
        if (isUsingNativeFallback && videoList.isNotEmpty()) {
            currentPositionIndex = (currentPositionIndex + 1) % videoList.size
            videoPath = videoList[currentPositionIndex]
            videoTitle = File(videoPath).name
            tvPlayerTitle.text = videoTitle
            initializePlayer(startPositionOverrideMs = 0L)
            return
        }
        exoPlayer?.let { player ->
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.play()
            } else if (videoList.isNotEmpty()) {
                player.seekToDefaultPosition(0)
                player.play()
            }
        }
    }

    private fun applySpeedDirect(targetSpeed: Float) {
        currentSpeed = targetSpeed.coerceIn(0.25f, 8.0f)
        exoPlayer?.playbackParameters = PlaybackParameters(currentSpeed)
        btnPlaybackSpeed.text = String.format(Locale.US, "%.1fx Speed", currentSpeed)
        tvSpeedRulerValue.text = String.format(Locale.US, "%.2f X", currentSpeed)
        speedRulerView.setSpeedValue(currentSpeed)
    }

    private fun applyVideoZoom(scale: Float) {
        disableClippingForFullScreenZoom()
        currentZoomScale = scale.coerceIn(0.5f, 6.0f)
        val mirrorFactor = if (isMirrorMode) -1f else 1f

        val videoSurface = playerView.videoSurfaceView
        if (videoSurface != null) {
            (videoSurface.parent as? ViewGroup)?.let { parentGroup ->
                parentGroup.clipChildren = false
                parentGroup.clipToPadding = false
                parentGroup.scaleX = currentZoomScale * mirrorFactor
                parentGroup.scaleY = currentZoomScale
            }
            videoSurface.scaleX = 1.0f
            videoSurface.scaleY = 1.0f
        } else {
            playerView.scaleX = currentZoomScale * mirrorFactor
            playerView.scaleY = currentZoomScale
        }

        val zoomPercent = (currentZoomScale * 100).toInt()
        handler.removeCallbacks(autoHideZoomPillRunnable)
        if (zoomPercent != 100 && !isScreenLocked) {
            tvZoomResetPill.text = "Zoom: $zoomPercent%  |  Reset"
            cardZoomResetPill.visibility = View.VISIBLE
            handler.postDelayed(autoHideZoomPillRunnable, 2800L)
        } else {
            cardZoomResetPill.visibility = View.GONE
        }
    }

    private fun deleteCurrentVideoPermanently() {
        closeAllModularPanels()
        exoPlayer?.pause()
        nativeFallbackVideoView?.pause()
        PermanentDeleteHelper.confirmAndDeletePermanently(
            context = this,
            title = videoTitle,
            path = videoPath,
            isVideo = true
        ) {
            DeepMediaScanner.invalidateCache()
            finish()
        }
    }

    private fun triggerAbRepeatStep() {
        val currentPos = getCurrentPositionUnified()
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
        applyVisualEnhancerFilter(currentVisualMode)
        Toast.makeText(this, if (isNightMode) "Night Mode ON" else "Night Mode OFF", Toast.LENGTH_SHORT).show()
    }

    private fun toggleMirrorModeNow() {
        isMirrorMode = !isMirrorMode
        applyVideoZoom(currentZoomScale)
        Toast.makeText(this, if (isMirrorMode) "Mirror Mode ON" else "Mirror Mode OFF", Toast.LENGTH_SHORT).show()
    }

    private fun updateLoopSelection(mode: Int) {
        val d = resources.displayMetrics.density
        val allButtons = listOf(btnLoopShuffle, btnLoopOne, btnLoopAll, btnLoopOff)
        allButtons.forEach { btn ->
            btn.setTextColor(Color.WHITE)
            btn.background = GradientDrawable().apply {
                setColor(Color.parseColor("#18FFFFFF"))
                cornerRadius = 12f * d
                setStroke((1 * d).toInt(), Color.parseColor("#38FFFFFF"))
            }
        }
        val activeBtn = allButtons[mode]
        activeBtn.setTextColor(Color.parseColor("#0A0A10"))
        activeBtn.background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = 12f * d
        }

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
        val d = resources.displayMetrics.density
        imgFavoriteIcon.setImageDrawable(
            PlayerThreeDotMenuHelper.PlayerVectorIconDrawable("STAR_FAV", d, Color.WHITE)
        )
    }

    private fun setupDirectionLockedTouchAndGestures() {
        scaleGestureDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    if (isScreenLocked) return false
                    isPinchZooming = true
                    lockedSwipeDirection = 0
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

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                enterFullImmersiveMode()
                if (isScreenLocked) {
                    if (btnUnlockOverlay.visibility == View.VISIBLE) {
                        handler.removeCallbacks(autoHideLockIconRunnable)
                        btnUnlockOverlay.visibility = View.GONE
                    } else {
                        showAutoHidingLockIconFor3Seconds()
                    }
                    return true
                }
                if (panelOverlayScrim.visibility == View.VISIBLE) {
                    closeAllModularPanels()
                    return true
                }
                layoutAllControls.visibility =
                    if (layoutAllControls.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isScreenLocked) return true
                val screenWidth = resources.displayMetrics.widthPixels
                val cur = getCurrentPositionUnified()
                val dur = getDurationUnified()
                if (e.x < screenWidth / 2f) {
                    seekToUnified((cur - 10000L).coerceAtLeast(0L))
                    showGestureFeedback(android.R.drawable.ic_media_rew, "-10s", -1)
                } else {
                    seekToUnified((cur + 10000L).coerceAtMost(dur))
                    showGestureFeedback(android.R.drawable.ic_media_ff, "+10s", -1)
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (isScreenLocked || isPinchZooming || lockedSwipeDirection != 0) return
                if (isPlayingUnified()) {
                    isLongPressSpeedActive = true
                    longPressStartX = e.x
                    dynamicHoldSpeed = 0.25f
                    handler.post(dynamicSpeedRampRunnable)
                }
            }
        })

        rootPlayerFrame.setOnTouchListener { _, event ->
            // Do not intercept or block touches when any modular panel or 3-dot menu is open
            if (layoutAdvancedSettings.visibility == View.VISIBLE ||
                panelOverlayScrim.visibility == View.VISIBLE ||
                panelBgAudioScreen.visibility == View.VISIBLE
            ) {
                return@setOnTouchListener false
            }

            scaleGestureDetector.onTouchEvent(event)
            if (!scaleGestureDetector.isInProgress) {
                gestureDetector.onTouchEvent(event)
            }

            val screenW = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
            val screenH = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.x
                    touchStartY = event.y
                    lastMoveX = event.x
                    lastMoveY = event.y
                    lockedSwipeDirection = 0
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isScreenLocked || isPinchZooming || event.pointerCount > 1) {
                        return@setOnTouchListener true
                    }

                    if (isLongPressSpeedActive) {
                        val deltaHoldX = event.x - longPressStartX
                        val normSlide = (deltaHoldX / (screenW * 0.65f)).coerceIn(-0.5f, 1.0f)
                        dynamicHoldSpeed = when {
                            normSlide <= 0.05f -> 0.25f
                            else -> (0.25f + normSlide * 7.75f).coerceIn(0.25f, 8.0f)
                        }
                        val roundedSpeed = (dynamicHoldSpeed * 4f).roundToInt() / 4f
                        dynamicHoldSpeed = roundedSpeed.coerceIn(0.25f, 8.0f)
                        exoPlayer?.playbackParameters = PlaybackParameters(dynamicHoldSpeed)
                        tvSpeedBoostBadge.text = String.format(
                            Locale.US,
                            "%.2fx Speed (Slide Left 0.25x <-> Right 8.0x)",
                            dynamicHoldSpeed
                        )
                        cardSpeedBoostBadge.visibility = View.VISIBLE
                        return@setOnTouchListener true
                    }

                    val totalDx = event.x - touchStartX
                    val totalDy = event.y - touchStartY
                    val stepDx = event.x - lastMoveX
                    val stepDy = event.y - lastMoveY
                    lastMoveX = event.x
                    lastMoveY = event.y

                    if (lockedSwipeDirection == 0) {
                        if (abs(totalDy) > 24f && abs(totalDy) > abs(totalDx) * 1.2f) {
                            lockedSwipeDirection = 2
                        } else if (abs(totalDx) > 28f && abs(totalDx) > abs(totalDy) * 1.4f) {
                            lockedSwipeDirection = 1
                            scrubTargetPositionMs = getCurrentPositionUnified()
                        }
                    }

                    if (lockedSwipeDirection == 2) {
                        if (touchStartX < screenW / 2f) {
                            val deltaBrightness = (-stepDy / screenH) * 100f
                            currentBrightnessPercent = (currentBrightnessPercent + deltaBrightness).coerceIn(2f, 100f)
                            val lp = window.attributes
                            lp.screenBrightness = currentBrightnessPercent / 100f
                            window.attributes = lp

                            val pctInt = currentBrightnessPercent.roundToInt()
                            findViewById<SeekBar>(R.id.seekBarBrightness)?.progress = pctInt
                            getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
                                .edit()
                                .putFloat("persistent_brightness_pct", currentBrightnessPercent)
                                .apply()

                            cardGestureFeedback.visibility = View.GONE
                            volumeHudHelper?.showBrightnessHud(pctInt)
                        } else {
                            val currentVol = volumeHudHelper?.getCurrentVolumePercent() ?: 80
                            val deltaVolPct = ((-stepDy / screenH) * 150f).roundToInt()
                            val newVol = (currentVol + deltaVolPct).coerceIn(0, 200)
                            cardGestureFeedback.visibility = View.GONE
                            volumeHudHelper?.applyVolumePercent(newVol, showHud = true)
                        }
                    } else if (lockedSwipeDirection == 1) {
                        val dur = getDurationUnified()
                        val fullWidthSeekWindowMs = (dur * 0.24f).coerceIn(120000f, 1200000f)
                        val deltaSeekMs = ((stepDx / screenW) * fullWidthSeekWindowMs).toLong()
                        scrubTargetPositionMs = (scrubTargetPositionMs + deltaSeekMs).coerceIn(0L, dur)
                        seekToUnified(scrubTargetPositionMs)

                        seekBarPlayer.progress = scrubTargetPositionMs.toInt()
                        val stamp = "${formatDuration(scrubTargetPositionMs)} / ${formatDuration(dur)}"
                        tvPlayerCurrentTime.text = formatDuration(scrubTargetPositionMs)
                        val pct = ((scrubTargetPositionMs.toFloat() / dur.toFloat()) * 100f).toInt().coerceIn(0, 100)
                        showGestureFeedback(android.R.drawable.ic_media_ff, stamp, pct)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    lockedSwipeDirection = 0
                    if (isLongPressSpeedActive) {
                        isLongPressSpeedActive = false
                        handler.removeCallbacks(dynamicSpeedRampRunnable)
                        exoPlayer?.playbackParameters = PlaybackParameters(currentSpeed)
                        cardSpeedBoostBadge.visibility = View.GONE
                    }
                }
            }
            true
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (volumeHudHelper?.onHardwareVolumeKeyPressed(isVolumeUp = true) == true) return true
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (volumeHudHelper?.onHardwareVolumeKeyPressed(isVolumeUp = false) == true) return true
        }
        if (mediaSessionManager?.handleMediaKeyEvent(keyCode) == true) {
            return true
        }
        return super.onKeyDown(keyCode, event)
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
        handler.postDelayed(hideGestureRunnable, 850)
    }

    private fun updatePipParams(): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val mutePi = PendingIntent.getBroadcast(
                this,
                701,
                Intent(ACTION_PIP_MUTE).setPackage(packageName),
                flags
            )
            val closePi = PendingIntent.getBroadcast(
                this,
                702,
                Intent(ACTION_PIP_CLOSE).setPackage(packageName),
                flags
            )
            val muteAction = RemoteAction(
                Icon.createWithResource(
                    this,
                    if (isMuted) android.R.drawable.ic_lock_silent_mode
                    else android.R.drawable.ic_lock_silent_mode_off
                ),
                if (isMuted) "Unmute" else "Mute",
                "Toggle Mute",
                mutePi
            )
            val closeAction = RemoteAction(
                Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                "Close",
                "Close Video",
                closePi
            )
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .setActions(listOf(muteAction, closeAction))
                .build()
            setPictureInPictureParams(params)
            return params
        }
        return null
    }

    private fun enterPipModeSafely() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = updatePipParams()
                if (params != null) {
                    enterPictureInPictureMode(params)
                }
            } catch (_: Exception) {
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isPlayingUnified() && !isBgAudioEnabled) {
            enterPipModeSafely()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            closeAllModularPanels()
            layoutAllControls.visibility = View.GONE
            cardZoomResetPill.visibility = View.GONE
        } else if (!isScreenLocked) {
            layoutAllControls.visibility = View.VISIBLE
            exoPlayer?.play()
            nativeFallbackVideoView?.start()
            enterFullImmersiveMode()
        }
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis.coerceAtLeast(0L))
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
            nativeFallbackVideoView?.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        smartIdlePauseHelper?.cancelTimer()
        mediaSessionManager?.release()
        handler.removeCallbacks(updateProgressAction)
        handler.removeCallbacks(hideGestureRunnable)
        handler.removeCallbacks(autoHideZoomPillRunnable)
        handler.removeCallbacks(autoHideLockIconRunnable)
        handler.removeCallbacks(dynamicSpeedRampRunnable)
        try {
            if (isPipReceiverRegistered) {
                unregisterReceiver(pipAndNotifReceiver)
                isPipReceiverRegistered = false
            }
            loudnessEnhancer?.release()
            loudnessEnhancer = null
        } catch (_: Exception) {
        }
        nativeFallbackVideoView?.stopPlayback()
        nativeFallbackVideoView = null
        exoPlayer?.release()
        exoPlayer = null
    }
}
