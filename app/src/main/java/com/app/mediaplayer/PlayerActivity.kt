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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
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
import android.provider.Settings
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
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.Collections
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    companion object {
        private const val REQ_PICK_SUBTITLE = 6101
        private const val ACTION_PIP_MUTE = "com.app.mediaplayer.ACTION_PIP_MUTE"
        private const val ACTION_PIP_CLOSE = "com.app.mediaplayer.ACTION_PIP_CLOSE"
    }

    private var exoPlayer: ExoPlayer? = null
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

    private lateinit var panelOverlayScrim: View
    private lateinit var panelVisualEnhancer: CardView
    private lateinit var panelSubtitles: CardView
    private lateinit var panelPlayingQueue: CardView
    private lateinit var panelSpeedRuler: CardView
    private lateinit var panelBgAudioScreen: RelativeLayout

    private lateinit var tvQueueHeaderCount: TextView
    private lateinit var btnQueueLoopModeToggle: TextView
    private lateinit var rvQueueList: RecyclerView
    private lateinit var tvSpeedRulerValue: TextView
    private lateinit var speedRulerView: SpeedTickRulerViewInternal

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
    private var useSoftwareDecoderPrefer = false

    private var currentSpeed = 1.0f
    private var longPressSpeedTarget = 4.0f
    private var isLongPressSpeedActive = false
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
                    exoPlayer?.pause()
                    finish()
                }
                BackgroundAudioService.ACTION_NOTIF_PREV -> playPreviousVideo()
                BackgroundAudioService.ACTION_NOTIF_PLAY_PAUSE -> {
                    exoPlayer?.let { p ->
                        if (p.isPlaying) p.pause() else p.play()
                    }
                    mediaSessionManager?.updatePlaybackState()
                }
                BackgroundAudioService.ACTION_NOTIF_NEXT -> playNextVideo()
            }
        }
    }

    private val updateProgressAction = object : Runnable {
        override fun run() {
            try {
                exoPlayer?.let { player ->
                    if (player.isPlaying && lockedSwipeDirection != 1) {
                        val currentPos = player.currentPosition.coerceAtLeast(0L)
                        val duration = player.duration.coerceAtLeast(0L)
                        seekBarPlayer.max = duration.toInt()
                        seekBarPlayer.progress = currentPos.toInt()
                        tvPlayerCurrentTime.text = formatDuration(currentPos)
                        tvPlayerTotalTime.text = formatDuration(duration)

                        if (videoPath.isNotEmpty() && currentPos > 1000L) {
                            getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
                                .edit()
                                .putLong("watched_pos_$videoPath", currentPos)
                                .apply()
                        }

                        if (pointA != -1L && pointB != -1L && currentPos >= pointB) {
                            player.seekTo(pointA)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            handler.postDelayed(this, 400)
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

        setupCrispPlayerIcons()
        restorePersistentBrightnessAndVisuals()
        buildModularOverlayPanels()
        upgradeAdvancedSettingsVisuals()
        applyPlayerCustomizationPreferences()
        extractIntentData(intent)
        initializePlayer()
        setupTopAndSideControls()
        setupBottomControls()
        setupAdvancedSettingsMenu()
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
        } catch (e: Exception) {
            e.printStackTrace()
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
                initializePlayer()
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

    private fun setupCrispPlayerIcons() {
        val d = resources.displayMetrics.density
        val cyanGlow = Color.parseColor("#00FFFF")

        btnBgAudio.setImageDrawable(PlayerVectorIconInternal("HEADPHONES", d, cyanGlow))
        btnVideoQueue.setImageDrawable(PlayerVectorIconInternal("QUEUE_LIST", d, Color.WHITE))
        btnMoreMenu.setImageDrawable(PlayerVectorIconInternal("THREE_DOTS", d, cyanGlow))
        btnMuteToggle.setImageDrawable(PlayerVectorIconInternal("SPEAKER_ON", d, Color.WHITE))
        btnLockScreen.setImageDrawable(PlayerVectorIconInternal("LOCK_OPEN", d, cyanGlow))
        btnUnlockOverlay.setImageDrawable(PlayerVectorIconInternal("LOCK_CLOSED", d, cyanGlow))
        btnSideCut.setImageDrawable(PlayerVectorIconInternal("SCREENSHOT_CAM", d, Color.WHITE))
        btnRotateScreen.setImageDrawable(PlayerVectorIconInternal("ROTATE", d, cyanGlow))
        btnAspectRatio.setImageDrawable(PlayerVectorIconInternal("ASPECT_FIT", d, Color.WHITE))
        btnPipMode.setImageDrawable(PlayerVectorIconInternal("PIP_WINDOW", d, cyanGlow))
    }

    private fun upgradeAdvancedSettingsVisuals() {
        val d = resources.displayMetrics.density
        layoutAdvancedSettings.background = GradientDrawable().apply {
            setColor(Color.parseColor("#F50A0A10"))
            cornerRadius = 22f * d
            setStroke((1.5f * d).toInt(), Color.parseColor("#00FFFF"))
        }

        findViewById<TextView>(R.id.btnCloseSettings)?.apply {
            text = "✕"
            setTextColor(Color.parseColor("#00FFFF"))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((12 * d).toInt(), (6 * d).toInt(), (12 * d).toInt(), (6 * d).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#2200FFFF"))
                setStroke((1.2f * d).toInt(), Color.parseColor("#00FFFF"))
            }
        }
    }

    private fun restorePersistentBrightnessAndVisuals() {
        val prefs = getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        if (prefs.getBoolean("hdr_new_badge_dismissed", false) && frameHdrContainer.childCount > 1) {
            frameHdrContainer.getChildAt(1).visibility = View.GONE
        }

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
            visibility = View.GONE
            setOnClickListener { closeAllModularPanels() }
        }
        rootPlayerFrame.addView(panelOverlayScrim)

        panelVisualEnhancer = CardView(this).apply {
            setCardBackgroundColor(Color.parseColor("#F50A0A10"))
            radius = 22f * d
            cardElevation = 24f * d
            visibility = View.GONE
        }
        val veScroll = ScrollView(this)
        val veContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt())
        }
        val tvVeTitle = TextView(this).apply {
            text = "←   8K AI Upscaler & Visual Enhancer"
            setTextColor(Color.parseColor("#00FFFF"))
            textSize = 16f
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
                text = if (currentVisualMode == index) "✓  $name" else name
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
                    label.text = if (idx == index) "✓  ${modeNames[idx]}" else modeNames[idx]
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
            cardElevation = 24f * d
            visibility = View.GONE
        }
        rootPlayerFrame.addView(panelSubtitles)

        panelPlayingQueue = CardView(this).apply {
            setCardBackgroundColor(Color.parseColor("#F50A0A10"))
            radius = 22f * d
            cardElevation = 24f * d
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
            text = "←   Playing Queue (1/1)"
            setTextColor(Color.parseColor("#00FFFF"))
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { closeAllModularPanels() }
        }
        btnQueueLoopModeToggle = TextView(this).apply {
            text = "Order"
            setTextColor(Color.parseColor("#00FFFF"))
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((12 * d).toInt(), (6 * d).toInt(), (12 * d).toInt(), (6 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2400FFFF"))
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
            setCardBackgroundColor(Color.parseColor("#E60A0A10"))
            radius = 22f * d
            cardElevation = 26f * d
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                (330 * d).toInt(),
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
            text = "−"
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((36 * d).toInt(), (36 * d).toInt())
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
            setTextColor(Color.parseColor("#00FFFF"))
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        }
        val btnSpeedReset = TextView(this).apply {
            text = "1x"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((30 * d).toInt(), (30 * d).toInt()).apply {
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
            layoutParams = LinearLayout.LayoutParams((36 * d).toInt(), (36 * d).toInt())
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

        speedRulerView = SpeedTickRulerViewInternal(this, d) { slidSpeed ->
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
            setBackgroundColor(Color.parseColor("#F50A0A10"))
            visibility = View.GONE
            setOnClickListener {
                visibility = View.GONE
                layoutAllControls.visibility = View.VISIBLE
            }
        }
        val bgCenterCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { addRule(RelativeLayout.CENTER_IN_PARENT) }
            setPadding((24 * d).toInt(), (24 * d).toInt(), (24 * d).toInt(), (24 * d).toInt())
        }
        val discView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((164 * d).toInt(), (164 * d).toInt())
            setImageDrawable(PlayerVectorIconInternal("BG_VINYL_DISC", d, Color.parseColor("#00FFFF")))
        }
        val tvBgHint = TextView(this).apply {
            text = "Background Audio Active\nPress Back to return to video, or Minimize to keep listening while using other apps"
            setTextColor(Color.parseColor("#B0C4DE"))
            textSize = 13.5f
            gravity = Gravity.CENTER
            setPadding(0, (16 * d).toInt(), 0, (18 * d).toInt())
        }
        val btnBackToVideo = TextView(this).apply {
            text = "▶   Return to Video Screen"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((28 * d).toInt(), (11 * d).toInt(), (28 * d).toInt(), (11 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#00FFFF"))
                cornerRadius = 24f * d
            }
            setOnClickListener {
                panelBgAudioScreen.visibility = View.GONE
                layoutAllControls.visibility = View.VISIBLE
            }
        }
        val btnMinimizeToHome = TextView(this).apply {
            text = "🎧   Minimize & Keep Playing in Background"
            setTextColor(Color.WHITE)
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((24 * d).toInt(), (10 * d).toInt(), (24 * d).toInt(), (10 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (10 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#182232"))
                cornerRadius = 24f * d
                setStroke((1.3f * d).toInt(), Color.parseColor("#00FFFF"))
            }
            setOnClickListener {
                isBgAudioEnabled = true
                moveTaskToBack(true)
            }
        }
        val btnSaveToMp3 = TextView(this).apply {
            text = "🎵   Convert Video to MP3"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((26 * d).toInt(), (10 * d).toInt(), (26 * d).toInt(), (10 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (10 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#00E676"))
                cornerRadius = 24f * d
            }
            setOnClickListener { openMp3Converter() }
        }
        bgCenterCol.addView(discView)
        bgCenterCol.addView(tvBgHint)
        bgCenterCol.addView(btnBackToVideo)
        bgCenterCol.addView(btnMinimizeToHome)
        bgCenterCol.addView(btnSaveToMp3)
        panelBgAudioScreen.addView(bgCenterCol)
        rootPlayerFrame.addView(panelBgAudioScreen)
    }

    private fun openHalfScreenPanel(targetPanel: View) {
        closeAllModularPanels()
        layoutAllControls.visibility = View.GONE
        panelOverlayScrim.visibility = View.VISIBLE

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels

        val lp = if (isLandscape) {
            FrameLayout.LayoutParams(
                (screenW * 0.50f).toInt(),
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END
            )
        } else {
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (screenH * 0.56f).toInt(),
                Gravity.BOTTOM
            )
        }
        targetPanel.layoutParams = lp
        targetPanel.alpha = 0f
        targetPanel.visibility = View.VISIBLE
        targetPanel.animate().alpha(1f).setDuration(220L).start()
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
                btnHdrMode.setTextColor(Color.WHITE)
                viewVisualOverlay.setBackgroundColor(
                    if (isNightMode) Color.parseColor("#88000000") else Color.TRANSPARENT
                )
            }
            1 -> {
                btnHdrMode.setTextColor(Color.parseColor("#00FFFF"))
                viewVisualOverlay.setBackgroundColor(Color.parseColor("#22FF9800"))
            }
            2 -> {
                btnHdrMode.setTextColor(Color.parseColor("#00FFFF"))
                viewVisualOverlay.setBackgroundColor(Color.parseColor("#1800E5FF"))
            }
            3 -> {
                btnHdrMode.setTextColor(Color.parseColor("#00FFFF"))
                viewVisualOverlay.setBackgroundColor(Color.parseColor("#24FF4081"))
            }
            4 -> {
                btnHdrMode.setTextColor(Color.parseColor("#00FFFF"))
                viewVisualOverlay.setBackgroundColor(Color.parseColor("#2800B0FF"))
            }
            5 -> {
                btnHdrMode.setTextColor(Color.parseColor("#00FFFF"))
                viewVisualOverlay.setBackgroundColor(Color.parseColor("#30FFAB00"))
            }
            6 -> {
                btnHdrMode.setTextColor(Color.parseColor("#00FFFF"))
                viewVisualOverlay.setBackgroundColor(Color.parseColor("#2CD4AF37"))
            }
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
            "Screenshot" to { captureVideoScreenshotNow() },
            "Rotate" to { toggleRotationNow() },
            "Equalizer" to { openEqualizerNow() },
            "Sound Lab" to { AudioPitchHelper.showSoundAndPitchLabDialog(this, exoPlayer) },
            "MP3 Cut" to { openMp3Converter() }
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
            videoList.clear()
            if (!listExtra.isNullOrEmpty()) {
                videoList.addAll(listExtra)
                currentPositionIndex = videoList.indexOf(videoPath).coerceAtLeast(0)
            } else if (videoPath.isNotEmpty()) {
                videoList.add(videoPath)
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

    private fun initializePlayer(startPositionOverrideMs: Long = -1L) {
        try {
            val resumePos = if (startPositionOverrideMs >= 0L) {
                startPositionOverrideMs
            } else {
                getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
                    .getLong("watched_pos_$videoPath", 0L)
            }

            exoPlayer?.release()

            val renderersFactory = DefaultRenderersFactory(this).apply {
                setExtensionRendererMode(
                    if (useSoftwareDecoderPrefer) DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                    else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                )
                setEnableDecoderFallback(true)
            }

            exoPlayer = ExoPlayer.Builder(this, renderersFactory).build().also { player ->
                player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
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
                        val uri = if (path.startsWith("content://") || path.startsWith("http")) {
                            Uri.parse(path)
                        } else {
                            Uri.fromFile(File(path))
                        }
                        MediaItem.fromUri(uri)
                    }
                    player.setMediaItems(mediaItems, currentPositionIndex, resumePos.coerceAtLeast(0L))
                } else if (videoPath.isNotEmpty()) {
                    val uri = if (videoPath.startsWith("content://") || videoPath.startsWith("http")) {
                        Uri.parse(videoPath)
                    } else {
                        Uri.fromFile(File(videoPath))
                    }
                    player.setMediaItem(MediaItem.fromUri(uri), resumePos.coerceAtLeast(0L))
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
                        btnPlayerPlayPause.setImageResource(
                            if (isPlaying) android.R.drawable.ic_media_pause
                            else android.R.drawable.ic_media_play
                        )
                        syncVideoNotificationState(isPlaying)
                    }
                ).also { it.requestAudioFocusAndRegister() }

                player.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        btnPlayerPlayPause.setImageResource(
                            if (isPlaying) android.R.drawable.ic_media_pause
                            else android.R.drawable.ic_media_play
                        )
                        mediaSessionManager?.updatePlaybackState()
                        syncVideoNotificationState(isPlaying)
                    }

                    override fun onAudioSessionIdChanged(audioSessionId: Int) {
                        setupLoudnessEnhancer(audioSessionId)
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
                            getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
                                .edit()
                                .putLong("history_time_$videoPath", System.currentTimeMillis())
                                .apply()
                            rvQueueList.adapter?.notifyDataSetChanged()
                            updateQueueHeaderTitle()
                            mediaSessionManager?.updateMetadataAndState(
                                title = videoTitle,
                                artist = "OG 8K Video Player",
                                durationMs = player.duration.coerceAtLeast(1000L)
                            )
                            syncVideoNotificationState(player.isPlaying)
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            val duration = player.duration.coerceAtLeast(0L)
                            seekBarPlayer.max = duration.toInt()
                            tvPlayerTotalTime.text = formatDuration(duration)
                            setupLoudnessEnhancer(player.audioSessionId)
                            mediaSessionManager?.updateMetadataAndState(
                                title = videoTitle,
                                artist = "OG 8K Video Player",
                                durationMs = duration
                            )
                        }
                    }
                })
            }
            handler.post(updateProgressAction)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to play video", Toast.LENGTH_SHORT).show()
        }
    }

    private fun syncVideoNotificationState(isPlaying: Boolean) {
        if (isBgAudioEnabled) {
            BackgroundAudioService.updateForegroundNotification(
                context = this,
                title = videoTitle,
                artist = "OG 8K Video Player",
                isPlaying = isPlaying,
                isVideoBackground = true,
                videoPath = videoPath
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
                .getBoolean("super_loud_200_boost", false)
            if (boost200) {
                loudnessEnhancer?.setTargetGain(1400)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun activateBackgroundAudioMode() {
        isBgAudioEnabled = true
        layoutAllControls.visibility = View.GONE
        panelBgAudioScreen.visibility = View.VISIBLE
        mediaSessionManager?.updateMetadataAndState(
            title = videoTitle,
            artist = "OG 8K Background Audio",
            durationMs = (exoPlayer?.duration ?: 1000L).coerceAtLeast(1000L)
        )
        BackgroundAudioService.updateForegroundNotification(
            this,
            videoTitle,
            "OG 8K Background Audio",
            exoPlayer?.isPlaying == true,
            isVideoBackground = true,
            videoPath = videoPath
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            isScreenLocked -> {
                btnUnlockOverlay.visibility = View.VISIBLE
                Toast.makeText(this, "Tap the Lock icon to unlock screen", Toast.LENGTH_SHORT).show()
            }
            panelOverlayScrim.visibility == View.VISIBLE -> {
                closeAllModularPanels()
                layoutAllControls.visibility = View.VISIBLE
            }
            panelBgAudioScreen.visibility == View.VISIBLE -> {
                panelBgAudioScreen.visibility = View.GONE
                layoutAllControls.visibility = View.VISIBLE
            }
            isBgAudioEnabled && exoPlayer?.isPlaying == true -> {
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
            if (frameHdrContainer.childCount > 1) {
                frameHdrContainer.getChildAt(1).visibility = View.GONE
            }
            getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("hdr_new_badge_dismissed", true)
                .apply()
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
            isScreenLocked = false
            btnUnlockOverlay.visibility = View.GONE
            layoutAllControls.visibility = View.VISIBLE
            enterFullImmersiveMode()
            Toast.makeText(this, "Screen Unlocked", Toast.LENGTH_SHORT).show()
        }

        btnSideCut.setOnClickListener { captureVideoScreenshotNow() }
        btnRotateScreen.setOnClickListener { toggleRotationNow() }
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
            text = "←   Subtitles & Auto-Sync Timing"
            setTextColor(Color.parseColor("#00FFFF"))
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (12 * d).toInt())
            setOnClickListener { closeAllModularPanels() }
        }
        col.addView(tvHeader)

        val player = exoPlayer
        val textGroups = player?.currentTracks?.groups?.filter { it.type == C.TRACK_TYPE_TEXT } ?: emptyList()

        col.addView(createSubtitleActionRow("🚫   None (Turn Off Subtitles)", d) {
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
            val row = createSubtitleActionRow("💬   Track: $name", d) {
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

        col.addView(createSubtitleActionRow("📂   Select Local Subtitle File (.srt / .ass / .vtt)", d) {
            try {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                startActivityForResult(intent, REQ_PICK_SUBTITLE)
            } catch (e: Exception) {
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
            setTextColor(Color.parseColor("#00FFFF"))
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

        val tvSizeLabel = TextView(this).apply {
            text = "Subtitle Text Size (${subtitleTextSizeSp.toInt()}sp)"
            setTextColor(Color.parseColor("#00FFFF"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, (12 * d).toInt(), 0, (4 * d).toInt())
        }
        val sbSubSize = SeekBar(this).apply {
            max = 24
            progress = (subtitleTextSizeSp.toInt() - 12).coerceIn(0, 24)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    subtitleTextSizeSp = (12 + p).toFloat()
                    tvSizeLabel.text = "Subtitle Text Size (${subtitleTextSizeSp.toInt()}sp)"
                    playerView.subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, subtitleTextSizeSp)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        col.addView(tvSizeLabel)
        col.addView(sbSubSize)

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
        tvQueueHeaderCount.text = "←   Playing Queue ($cur/${videoList.size})"
    }

    private fun setupQueueRecyclerViewAndDragHelper() {
        val d = resources.displayMetrics.density
        val resPrefs = getSharedPreferences("MAX_VIDEO_RES_PREFS", Context.MODE_PRIVATE)
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
                    progressDrawable.setColorFilter(
                        Color.parseColor("#00FFFF"),
                        PorterDuff.Mode.SRC_IN
                    )
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
                    textSize = 12.5f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                }
                val tvMeta = TextView(this@PlayerActivity).apply {
                    textSize = 10.5f
                    setPadding(0, (3 * d).toInt(), 0, 0)
                }
                textCol.addView(tvTitle)
                textCol.addView(tvMeta)

                val btnDrag = TextView(this@PlayerActivity).apply {
                    text = "≡"
                    setTextColor(Color.parseColor("#B0C4DE"))
                    textSize = 20f
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams((32 * d).toInt(), (40 * d).toInt())
                }

                val btnRemove = TextView(this@PlayerActivity).apply {
                    text = "✕"
                    setTextColor(Color.parseColor("#B0C4DE"))
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

                holder.tvTitle.text = name
                holder.tvTitle.setTextColor(
                    if (isPlayingNow) Color.parseColor("#00FFFF") else Color.WHITE
                )

                val sizeBytes = if (file.exists()) file.length() else 0L
                val sizeMb = sizeBytes / (1024.0 * 1024.0)
                val sizeLabel = if (sizeMb >= 1024.0) {
                    String.format(Locale.US, "%.2fGB", sizeMb / 1024.0)
                } else {
                    String.format(Locale.US, "%.2fMB", sizeMb)
                }
                val resBadge = resPrefs.getString("res_$path", null)
                    ?: computeResolutionBadgeLocal(name, sizeBytes)

                holder.tvMeta.text = "$resBadge  •  $sizeLabel"
                holder.tvMeta.setTextColor(
                    if (isPlayingNow) Color.parseColor("#00FFFF") else Color.parseColor("#9999AA")
                )

                val watched = watchedPrefs.getLong("watched_pos_$path", 0L)
                holder.pb.progress = if (watched > 0L) 650 else 0

                val target: Any = if (path.startsWith("content://")) Uri.parse(path) else file
                Glide.with(this@PlayerActivity)
                    .load(target)
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .override(160, 90)
                    .dontAnimate()
                    .placeholder(android.R.color.darker_gray)
                    .centerCrop()
                    .into(holder.img)

                holder.itemView.setOnClickListener {
                    val pos = holder.adapterPosition
                    if (pos in videoList.indices) {
                        currentPositionIndex = pos
                        exoPlayer?.seekToDefaultPosition(pos)
                        exoPlayer?.play()
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

    private fun computeResolutionBadgeLocal(title: String, sizeBytes: Long): String {
        val upper = title.uppercase(Locale.US)
        return when {
            upper.contains("8K") || upper.contains("4320P") -> "8K"
            upper.contains("4K") || upper.contains("2160P") -> "4K"
            upper.contains("2K") || upper.contains("1440P") -> "2K"
            upper.contains("1080P") || upper.contains("FHD") -> "1080p"
            upper.contains("720P") || upper.contains("HD") -> "720p"
            sizeBytes > 120L * 1024L * 1024L -> "1080p"
            sizeBytes > 30L * 1024L * 1024L -> "720p"
            else -> "480p"
        }
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
            PlayerVectorIconInternal(
                if (isMuted) "SPEAKER_MUTE" else "SPEAKER_ON",
                d,
                if (isMuted) Color.parseColor("#FF4081") else Color.WHITE
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
        btnLockScreen.setImageDrawable(PlayerVectorIconInternal("LOCK_CLOSED", d, Color.parseColor("#00FFFF")))
        btnUnlockOverlay.visibility = View.VISIBLE
        enterFullImmersiveMode()
        Toast.makeText(this, "Screen Locked", Toast.LENGTH_SHORT).show()
    }

    private fun captureVideoScreenshotNow() {
        val posMs = exoPlayer?.currentPosition ?: 0L
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
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    retriever.release()
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }

            var savedLocation = ""
            if (bmp != null) {
                try {
                    val fileName = "OG_Shot_${System.currentTimeMillis()}.jpg"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            put(
                                MediaStore.Images.Media.RELATIVE_PATH,
                                "${Environment.DIRECTORY_PICTURES}/OGPlayerScreenshots"
                            )
                        }
                        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        if (uri != null) {
                            contentResolver.openOutputStream(uri)?.use { out ->
                                bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
                            }
                            savedLocation = "Pictures/OGPlayerScreenshots/$fileName"
                        }
                    } else {
                        val dir = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                            "OGPlayerScreenshots"
                        )
                        if (!dir.exists()) dir.mkdirs()
                        val outFile = File(dir, fileName)
                        FileOutputStream(outFile).use { out ->
                            bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
                        }
                        savedLocation = outFile.absolutePath
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
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
            exoPlayer?.let { player ->
                if (player.isPlaying) player.pause() else player.play()
            }
        }

        findViewById<ImageView>(R.id.btnPlayerPrev).setOnClickListener { playPreviousVideo() }
        findViewById<ImageView>(R.id.btnPlayerNext).setOnClickListener { playNextVideo() }

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
            closeAllModularPanels()
            layoutAllControls.visibility = View.GONE
            panelOverlayScrim.visibility = View.VISIBLE
            panelSpeedRuler.visibility = View.VISIBLE
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
                    btnAspectRatio.setImageDrawable(PlayerVectorIconInternal("ASPECT_FIT", d, Color.WHITE))
                    Toast.makeText(this, "Aspect Ratio: Original Fit", Toast.LENGTH_SHORT).show()
                }
                1 -> {
                    playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                    surface.scaleX = 1.0f * mirror
                    surface.scaleY = 1.0f
                    btnAspectRatio.setImageDrawable(PlayerVectorIconInternal("ASPECT_STRETCH", d, Color.parseColor("#00FFFF")))
                    Toast.makeText(this, "Aspect Ratio: Full Stretch", Toast.LENGTH_SHORT).show()
                }
                2 -> {
                    playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    surface.scaleX = 1.0f * mirror
                    surface.scaleY = 1.0f
                    btnAspectRatio.setImageDrawable(PlayerVectorIconInternal("ASPECT_CROP", d, Color.parseColor("#00E5FF")))
                    Toast.makeText(this, "Aspect Ratio: Center Zoom Crop", Toast.LENGTH_SHORT).show()
                }
                3 -> {
                    playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    surface.scaleX = 1.16f * mirror
                    surface.scaleY = 0.94f
                    btnAspectRatio.setImageDrawable(PlayerVectorIconInternal("ASPECT_16_9", d, Color.parseColor("#FFAB00")))
                    Toast.makeText(this, "Aspect Ratio: 16:9 Cinema", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnPipMode.setOnClickListener {
            enterPipModeSafely()
        }
    }

    private fun playPreviousVideo() {
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
        btnPlaybackSpeed.text = if (abs(currentSpeed - 1.0f) < 0.03f) {
            "Speed"
        } else {
            String.format(Locale.US, "%.1fx", currentSpeed)
        }
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

    private fun setupAdvancedSettingsMenu() {
        findViewById<TextView>(R.id.btnCloseSettings).setOnClickListener {
            closeAllModularPanels()
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
                Toast.makeText(this, "Cast Settings Unavailable", Toast.LENGTH_SHORT).show()
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
                if (isFavorite) "Added to Favorites ♥" else "Removed from Favorites",
                Toast.LENGTH_SHORT
            ).show()
        }

        findViewById<LinearLayout>(R.id.optBookmark).setOnClickListener {
            val prefs = getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
            val savedBookmark = prefs.getLong("bookmark_$videoPath", -1L)
            val currentPos = exoPlayer?.currentPosition ?: 0L
            val options = if (savedBookmark != -1L) {
                arrayOf(
                    "🔖   Save Bookmark at ${formatDuration(currentPos)}",
                    "⏩   Jump to Saved Bookmark (${formatDuration(savedBookmark)})",
                    "🧹   Clear Bookmark",
                    "🎛️   Open Sound & Pitch Lab (Lofi / 200% Boost)",
                    "🗑️   Delete Video Permanently (From Phone)"
                )
            } else {
                arrayOf(
                    "🔖   Save Bookmark at ${formatDuration(currentPos)}",
                    "🎛️   Open Sound & Pitch Lab (Lofi / 200% Boost)",
                    "🔒   Lock Video into Secret Privacy Vault",
                    "🗑️   Delete Video Permanently (From Phone)"
                )
            }
            showBrightMenuDialogInternal("Video Bookmarks & Pro Tools", options) { which ->
                if (savedBookmark != -1L) {
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
                        3 -> AudioPitchHelper.showSoundAndPitchLabDialog(this, exoPlayer)
                        4 -> deleteCurrentVideoPermanently()
                    }
                } else {
                    when (which) {
                        0 -> {
                            prefs.edit().putLong("bookmark_$videoPath", currentPos).apply()
                            Toast.makeText(this, "Bookmark Saved!", Toast.LENGTH_SHORT).show()
                        }
                        1 -> AudioPitchHelper.showSoundAndPitchLabDialog(this, exoPlayer)
                        2 -> FakeVaultHelper.lockMediaIntoVault(this, videoPath, videoTitle)
                        3 -> deleteCurrentVideoPermanently()
                    }
                }
            }
        }

        findViewById<LinearLayout>(R.id.optVrMode).setOnClickListener {
            isVrMode = !isVrMode
            applyVideoZoom(if (isVrMode) 1.25f else 1.0f)
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
            SmartSleepTimerDialog.showSleepTimerPicker(
                context = this,
                onStopTimer = { smartIdlePauseHelper?.cancelTimer() },
                onStartTimerMinutes = { mins, motion ->
                    smartIdlePauseHelper?.startSmoothFadeSleepTimer(mins, motion)
                }
            )
        }

        btnLoopShuffle.setOnClickListener { updateLoopSelection(0) }
        btnLoopOne.setOnClickListener { updateLoopSelection(1) }
        btnLoopAll.setOnClickListener { updateLoopSelection(2) }
        btnLoopOff.setOnClickListener { updateLoopSelection(3) }

        findViewById<SeekBar>(R.id.seekBarBrightness).setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentBrightnessPercent = progress.coerceIn(5, 100).toFloat()
                    val lp = window.attributes
                    lp.screenBrightness = currentBrightnessPercent / 100f
                    window.attributes = lp
                    getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
                        .edit()
                        .putFloat("persistent_brightness_pct", currentBrightnessPercent)
                        .apply()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnHwDecoder.setOnClickListener {
            if (useSoftwareDecoderPrefer) {
                useSoftwareDecoderPrefer = false
                val pos = exoPlayer?.currentPosition ?: 0L
                initializePlayer(startPositionOverrideMs = pos)
            }
            btnHwDecoder.setTextColor(Color.parseColor("#00FFFF"))
            btnSwDecoder.setTextColor(Color.WHITE)
            Toast.makeText(this, "Active: Hardware (HW) 8K Decoder Engine", Toast.LENGTH_SHORT).show()
        }

        btnSwDecoder.setOnClickListener {
            if (!useSoftwareDecoderPrefer) {
                useSoftwareDecoderPrefer = true
                val pos = exoPlayer?.currentPosition ?: 0L
                initializePlayer(startPositionOverrideMs = pos)
            }
            btnSwDecoder.setTextColor(Color.parseColor("#00FFFF"))
            btnHwDecoder.setTextColor(Color.WHITE)
            Toast.makeText(this, "Active: Software (SW) Fallback Decoder Engine", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.optEditPlayer).setOnClickListener {
            showCustomizePlayerLayoutDialog()
        }

        findViewById<LinearLayout>(R.id.optTutorials).setOnClickListener {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Player Gestures Guide")
                .setMessage(
                    "1. Right Vertical Swipe: 0% to 200% Volume HUD Boost\n" +
                        "2. Left Vertical Swipe: Direction-Locked Smooth Brightness\n" +
                        "3. Horizontal Slide: Slow Precision Frame Seek\n" +
                        "4. Double Tap Left / Right: Instant -10s / +10s Seek\n" +
                        "5. Two-Finger Pinch: Full-Screen 50% to 600% Video Zoom"
                )
                .setPositiveButton("Got It", null)
                .show()
        }

        findViewById<LinearLayout>(R.id.optFileInfo).setOnClickListener {
            val file = File(videoPath)
            val sizeMb = if (file.exists()) String.format(Locale.US, "%.2f MB", file.length() / (1024.0 * 1024.0)) else "Unknown"
            val durationStr = formatDuration(exoPlayer?.duration ?: 0L)
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("File Information")
                .setMessage("Title: $videoTitle\n\nPath: $videoPath\n\nSize: $sizeMb\n\nDuration: $durationStr")
                .setPositiveButton("Close", null)
                .setNeutralButton("Delete Permanently") { _, _ ->
                    deleteCurrentVideoPermanently()
                }
                .show()
        }

        findViewById<LinearLayout>(R.id.optFeedback).setOnClickListener {
            AudioPitchHelper.showSoundAndPitchLabDialog(this, exoPlayer)
        }
    }

    private fun deleteCurrentVideoPermanently() {
        closeAllModularPanels()
        exoPlayer?.pause()
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

    private fun openEqualizerNow() {
        closeAllModularPanels()
        val sessionId = exoPlayer?.audioSessionId ?: 0
        EqualizerHelper.showEqualizerDialog(this, sessionId)
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
        applyVisualEnhancerFilter(currentVisualMode)
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
            if (dockSide) "↔️   Move Side Icons Back to Left/Right" else "⬇️   Move Side Icons to Bottom Bar",
            if (showSide) "👁️   Hide Side Controls" else "👁️   Show Side Controls",
            if (showHdrCc) "✨   Hide Top HDR & CC Badges" else "✨   Show Top HDR & CC Badges",
            "⚡   Set Press & Hold Speed Boost to 4.0x",
            "⚡   Set Press & Hold Speed Boost to 6.0x",
            "⚡   Set Press & Hold Speed Boost to 8.0x",
            "🔄   Reset Player Layout & Zoom (100%)"
        )

        showBrightMenuDialogInternal("Edit Player Layout", options) { which ->
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
                }
                4 -> {
                    prefs.edit().putFloat("player_long_press_speed", 6.0f).apply()
                    longPressSpeedTarget = 6.0f
                }
                5 -> {
                    prefs.edit().putFloat("player_long_press_speed", 8.0f).apply()
                    longPressSpeedTarget = 8.0f
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
            trackNames.add("🔊   $lang $label".trim())
        }
        showBrightMenuDialogInternal(title, trackNames.toTypedArray()) { which ->
            val selectedGroup = trackGroups[which].mediaTrackGroup
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(TrackSelectionOverride(selectedGroup, 0))
                .build()
            Toast.makeText(this, "Selected: ${trackNames[which]}", Toast.LENGTH_SHORT).show()
        }
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
                    btnUnlockOverlay.visibility =
                        if (btnUnlockOverlay.visibility == View.VISIBLE) View.GONE else View.VISIBLE
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
                exoPlayer?.let { player ->
                    if (e.x < screenWidth / 2f) {
                        player.seekTo((player.currentPosition - 10000L).coerceAtLeast(0L))
                        showGestureFeedback(android.R.drawable.ic_media_rew, "-10s", -1)
                    } else {
                        player.seekTo((player.currentPosition + 10000L).coerceAtMost(player.duration.coerceAtLeast(0L)))
                        showGestureFeedback(android.R.drawable.ic_media_ff, "+10s", -1)
                    }
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (isScreenLocked || isPinchZooming || lockedSwipeDirection != 0) return
                exoPlayer?.let { player ->
                    if (player.isPlaying) {
                        isLongPressSpeedActive = true
                        player.playbackParameters = PlaybackParameters(longPressSpeedTarget)
                        tvSpeedBoostBadge.text = "${longPressSpeedTarget}x Fast Play >>"
                        cardSpeedBoostBadge.visibility = View.VISIBLE
                    }
                }
            }
        })

        rootPlayerFrame.setOnTouchListener { _, event ->
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
                    if (isScreenLocked || isPinchZooming || isLongPressSpeedActive || event.pointerCount > 1) {
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
                        } else if (abs(totalDx) > 32f && abs(totalDx) > abs(totalDy) * 1.5f) {
                            lockedSwipeDirection = 1
                            scrubTargetPositionMs = exoPlayer?.currentPosition ?: 0L
                        }
                    }

                    if (lockedSwipeDirection == 2) {
                        if (touchStartX < screenW / 2f) {
                            val deltaBrightness = (-stepDy / screenH) * 90f
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

                            showGestureFeedback(android.R.drawable.ic_menu_view, "$pctInt%", pctInt)
                        } else {
                            val currentVol = volumeHudHelper?.getCurrentVolumePercent() ?: 80
                            val deltaVolPct = ((-stepDy / screenH) * 140f).roundToInt()
                            val newVol = (currentVol + deltaVolPct).coerceIn(0, 200)
                            volumeHudHelper?.applyVolumePercent(newVol, showHud = true)
                        }
                    } else if (lockedSwipeDirection == 1) {
                        val player = exoPlayer ?: return@setOnTouchListener true
                        val dur = player.duration.coerceAtLeast(1L)
                        val fullWidthSeekWindowMs = if (touchStartY > screenH * 0.6f) 20000f else 42000f
                        val deltaSeekMs = ((stepDx / screenW) * fullWidthSeekWindowMs).toLong()
                        scrubTargetPositionMs = (scrubTargetPositionMs + deltaSeekMs).coerceIn(0L, dur)
                        player.seekTo(scrubTargetPositionMs)

                        seekBarPlayer.progress = scrubTargetPositionMs.toInt()
                        val stamp = formatPreciseTimestampWithCentiseconds(scrubTargetPositionMs)
                        tvPlayerCurrentTime.text = formatDuration(scrubTargetPositionMs)
                        val pct = ((scrubTargetPositionMs.toFloat() / dur.toFloat()) * 100f).toInt().coerceIn(0, 100)
                        showGestureFeedback(android.R.drawable.ic_media_ff, stamp, pct)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    lockedSwipeDirection = 0
                    if (isLongPressSpeedActive) {
                        isLongPressSpeedActive = false
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

    private fun showBrightMenuDialogInternal(
        headerTitle: String,
        items: Array<String>,
        onSelect: (Int) -> Unit
    ) {
        val d = resources.displayMetrics.density
        val rootCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * d).toInt(), (18 * d).toInt(), (18 * d).toInt(), (14 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0A0A10"))
                cornerRadius = 22f * d
                setStroke((1.5f * d).toInt(), Color.parseColor("#00FFFF"))
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(rootCard)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((8 * d).toInt(), (2 * d).toInt(), (4 * d).toInt(), (12 * d).toInt())
        }

        val tvHeader = TextView(this).apply {
            text = headerTitle
            setTextColor(Color.parseColor("#00FFFF"))
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnClose = TextView(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((30 * d).toInt(), (30 * d).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#26FFFFFF"))
            }
            setOnClickListener { dialog.dismiss() }
        }

        headerRow.addView(tvHeader)
        headerRow.addView(btnClose)
        rootCard.addView(headerRow)

        val topDivider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (1 * d).toInt()
            )
            setBackgroundColor(Color.parseColor("#3300FFFF"))
        }
        rootCard.addView(topDivider)

        val scroll = ScrollView(this)
        val listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        items.forEachIndexed { idx, label ->
            val tvItem = TextView(this).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 14.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER_VERTICAL
                setPadding((12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt())
                setOnClickListener {
                    dialog.dismiss()
                    onSelect(idx)
                }
            }
            listLayout.addView(tvItem)
            if (idx < items.size - 1) {
                val line = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (1 * d).toInt()
                    )
                    setBackgroundColor(Color.parseColor("#1AFFFFFF"))
                }
                listLayout.addView(line)
            }
        }

        scroll.addView(listLayout)
        rootCard.addView(scroll)
        dialog.show()
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
            closeAllModularPanels()
            layoutAllControls.visibility = View.GONE
            cardZoomResetPill.visibility = View.GONE
        } else if (!isScreenLocked) {
            layoutAllControls.visibility = View.VISIBLE
            exoPlayer?.play()
            enterFullImmersiveMode()
        }
    }

    private fun formatPreciseTimestampWithCentiseconds(millis: Long): String {
        val safe = millis.coerceAtLeast(0L)
        val hours = TimeUnit.MILLISECONDS.toHours(safe)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(safe) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(safe) % 60
        val centis = (safe % 1000L) / 10L
        return String.format(Locale.US, "%02d:%02d:%02d.%02d", hours, minutes, seconds, centis)
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
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        smartIdlePauseHelper?.cancelTimer()
        mediaSessionManager?.release()
        handler.removeCallbacks(updateProgressAction)
        handler.removeCallbacks(hideGestureRunnable)
        handler.removeCallbacks(autoHideZoomPillRunnable)
        try {
            if (isPipReceiverRegistered) {
                unregisterReceiver(pipAndNotifReceiver)
                isPipReceiverRegistered = false
            }
            loudnessEnhancer?.release()
            loudnessEnhancer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        exoPlayer?.release()
        exoPlayer = null
    }

    private class SpeedTickRulerViewInternal(
        context: Context,
        private val density: Float,
        private val onSpeedChanged: (Float) -> Unit
    ) : View(context) {

        private var speed = 1.0f

        private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#99FFFFFF")
            strokeWidth = 1.6f * density
            strokeCap = Paint.Cap.ROUND
        }

        private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00FFFF")
            strokeWidth = 2.6f * density
            strokeCap = Paint.Cap.ROUND
        }

        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 10.5f * density
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        fun setSpeedValue(newSpeed: Float) {
            speed = newSpeed.coerceIn(0.25f, 8.0f)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val padX = 16f * density
            val usableW = (w - 2 * padX).coerceAtLeast(1f)
            val totalTicks = 40
            val topY = 8f * density
            val bottomMajor = h - 22f * density
            val bottomMinor = h - 30f * density

            for (i in 0..totalTicks) {
                val frac = i.toFloat() / totalTicks.toFloat()
                val x = padX + frac * usableW
                val isMajor = i % 5 == 0
                canvas.drawLine(
                    x,
                    if (isMajor) topY else topY + 6f * density,
                    x,
                    if (isMajor) bottomMajor else bottomMinor,
                    tickPaint
                )
            }

            val milestones = listOf(
                0.5f to "0.5X",
                1.0f to "1.0X",
                2.0f to "2.0X",
                4.0f to "4.0X",
                8.0f to "8.0X"
            )
            for ((msVal, text) in milestones) {
                val f = ((msVal - 0.25f) / 7.75f).coerceIn(0f, 1f)
                val x = padX + f * usableW
                canvas.drawText(text, x, h - 4f * density, labelPaint)
            }

            val curFrac = ((speed - 0.25f) / 7.75f).coerceIn(0f, 1f)
            val curX = padX + curFrac * usableW
            canvas.drawLine(curX, 2f * density, curX, bottomMajor + 4f * density, activePaint)
            canvas.drawCircle(curX, 5f * density, 4f * density, activePaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                    val padX = 16f * density
                    val usableW = (width.toFloat() - 2 * padX).coerceAtLeast(1f)
                    val frac = ((event.x - padX) / usableW).coerceIn(0f, 1f)
                    val rawSpeed = 0.25f + frac * 7.75f
                    val rounded = (rawSpeed * 10f).roundToInt() / 10f
                    speed = rounded.coerceIn(0.25f, 8.0f)
                    invalidate()
                    onSpeedChanged(speed)
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }

    private class PlayerVectorIconInternal(
        private val type: String,
        private val density: Float,
        private val iconColor: Int
    ) : Drawable() {

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = iconColor
            style = Paint.Style.STROKE
            strokeWidth = 2.4f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = iconColor
            style = Paint.Style.FILL
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 0f || h <= 0f) return
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val r = w.coerceAtMost(h) * 0.36f
            val path = Path()

            when (type) {
                "HEADPHONES" -> {
                    val arc = RectF(cx - r * 0.85f, cy - r * 0.85f, cx + r * 0.85f, cy + r * 0.65f)
                    canvas.drawArc(arc, 180f, 180f, false, strokePaint)
                    val leftCup = RectF(cx - r * 0.92f, cy - r * 0.1f, cx - r * 0.48f, cy + r * 0.72f)
                    val rightCup = RectF(cx + r * 0.48f, cy - r * 0.1f, cx + r * 0.92f, cy + r * 0.72f)
                    canvas.drawRoundRect(leftCup, 3f * density, 3f * density, fillPaint)
                    canvas.drawRoundRect(rightCup, 3f * density, 3f * density, fillPaint)
                }
                "QUEUE_LIST" -> {
                    canvas.drawLine(cx - r * 0.75f, cy - r * 0.55f, cx + r * 0.75f, cy - r * 0.55f, strokePaint)
                    canvas.drawLine(cx - r * 0.75f, cy, cx + r * 0.75f, cy, strokePaint)
                    canvas.drawLine(cx - r * 0.75f, cy + r * 0.55f, cx + r * 0.35f, cy + r * 0.55f, strokePaint)
                }
                "THREE_DOTS" -> {
                    canvas.drawCircle(cx, cy - r * 0.65f, 2.4f * density, fillPaint)
                    canvas.drawCircle(cx, cy, 2.4f * density, fillPaint)
                    canvas.drawCircle(cx, cy + r * 0.65f, 2.4f * density, fillPaint)
                }
                "SPEAKER_ON", "SPEAKER_MUTE" -> {
                    path.moveTo(cx - r * 0.75f, cy - r * 0.32f)
                    path.lineTo(cx - r * 0.3f, cy - r * 0.32f)
                    path.lineTo(cx + r * 0.18f, cy - r * 0.75f)
                    path.lineTo(cx + r * 0.18f, cy + r * 0.75f)
                    path.lineTo(cx - r * 0.3f, cy + r * 0.32f)
                    path.lineTo(cx - r * 0.75f, cy + r * 0.32f)
                    path.close()
                    canvas.drawPath(path, strokePaint)
                    if (type == "SPEAKER_MUTE") {
                        canvas.drawLine(cx + r * 0.38f, cy - r * 0.35f, cx + r * 0.85f, cy + r * 0.35f, strokePaint)
                        canvas.drawLine(cx + r * 0.85f, cy - r * 0.35f, cx + r * 0.38f, cy + r * 0.35f, strokePaint)
                    } else {
                        val arc = RectF(cx - r * 0.1f, cy - r * 0.55f, cx + r * 0.78f, cy + r * 0.55f)
                        canvas.drawArc(arc, -45f, 90f, false, strokePaint)
                    }
                }
                "LOCK_OPEN", "LOCK_CLOSED" -> {
                    val body = RectF(cx - r * 0.68f, cy - r * 0.1f, cx + r * 0.68f, cy + r * 0.82f)
                    canvas.drawRoundRect(body, 3.5f * density, 3.5f * density, strokePaint)
                    val shackle = RectF(cx - r * 0.45f, cy - r * 0.78f, cx + r * 0.45f, cy - r * 0.05f)
                    canvas.drawArc(shackle, 180f, if (type == "LOCK_OPEN") 135f else 180f, false, strokePaint)
                    canvas.drawCircle(cx, cy + r * 0.35f, 2.2f * density, fillPaint)
                }
                "SCREENSHOT_CAM" -> {
                    val cam = RectF(cx - r * 0.82f, cy - r * 0.52f, cx + r * 0.82f, cy + r * 0.68f)
                    canvas.drawRoundRect(cam, 4f * density, 4f * density, strokePaint)
                    canvas.drawCircle(cx, cy + r * 0.08f, r * 0.34f, strokePaint)
                }
                "ROTATE" -> {
                    val box = RectF(cx - r * 0.75f, cy - r * 0.5f, cx + r * 0.75f, cy + r * 0.5f)
                    canvas.drawRoundRect(box, 3f * density, 3f * density, strokePaint)
                }
                "ASPECT_FIT", "ASPECT_STRETCH", "ASPECT_CROP", "ASPECT_16_9" -> {
                    val frame = RectF(cx - r * 0.85f, cy - r * 0.58f, cx + r * 0.85f, cy + r * 0.58f)
                    canvas.drawRoundRect(frame, 3.5f * density, 3.5f * density, strokePaint)
                    if (type == "ASPECT_STRETCH") {
                        canvas.drawLine(cx - r * 0.45f, cy, cx + r * 0.45f, cy, strokePaint)
                    } else if (type == "ASPECT_CROP") {
                        canvas.drawCircle(cx, cy, r * 0.26f, fillPaint)
                    }
                }
                "PIP_WINDOW" -> {
                    val outer = RectF(cx - r * 0.85f, cy - r * 0.6f, cx + r * 0.85f, cy + r * 0.6f)
                    canvas.drawRoundRect(outer, 3.5f * density, 3.5f * density, strokePaint)
                    val inner = RectF(cx + r * 0.05f, cy - r * 0.05f, cx + r * 0.68f, cy + r * 0.45f)
                    canvas.drawRoundRect(inner, 2f * density, 2f * density, fillPaint)
                }
                "BG_VINYL_DISC" -> {
                    fillPaint.color = Color.parseColor("#141824")
                    canvas.drawCircle(cx, cy, w * 0.46f, fillPaint)
                    strokePaint.color = Color.parseColor("#00FFFF")
                    canvas.drawCircle(cx, cy, w * 0.36f, strokePaint)
                    canvas.drawCircle(cx, cy, w * 0.26f, strokePaint)
                    fillPaint.color = iconColor
                    canvas.drawCircle(cx, cy, w * 0.16f, fillPaint)
                    fillPaint.color = Color.BLACK
                    canvas.drawCircle(cx, cy, w * 0.04f, fillPaint)
                }
            }
        }

        override fun setAlpha(alpha: Int) {
            strokePaint.alpha = alpha
            fillPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            strokePaint.colorFilter = colorFilter
            fillPaint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
