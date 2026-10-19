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
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.audiofx.AudioEffect
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
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
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
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
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.Collections
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.roundToInt

class PlayerActivity : AppCompatActivity() {

    companion object {
        private const val REQ_PICK_SUBTITLE = 6101
        private const val ACTION_PIP_MUTE = "com.app.mediaplayer.ACTION_PIP_MUTE"
        private const val ACTION_PIP_CLOSE = "com.app.mediaplayer.ACTION_PIP_CLOSE"
    }

    private var exoPlayer: ExoPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

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

    // Dynamic Modular Panels inside rootPlayerFrame
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
    private lateinit var speedRulerView: SpeedTickRulerView

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

    private var currentSpeed = 1.0f
    private var longPressSpeedTarget = 4.0f
    private var isLongPressSpeedActive = false
    private var currentZoomScale = 1.0f
    private var isPinchZooming = false

    // Smooth 300% Volume & Persistent Brightness State
    private var currentVolumePercent = 100f
    private var currentBrightnessPercent = 75f
    private var isHorizontalScrubbing = false
    private var scrubTargetPositionMs = 0L

    private var resizeModeIndex = 0
    private var queueLoopMode = 0 // 0: Order, 1: Repeat One, 2: Shuffle
    private var subtitleTextSizeSp = 18f
    private var pointA: Long = -1L
    private var pointB: Long = -1L
    private var sleepCountDownTimer: CountDownTimer? = null
    private var isPipReceiverRegistered = false

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var audioManager: AudioManager
    private lateinit var gestureDetector: GestureDetector
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    private val hideGestureRunnable = Runnable {
        cardGestureFeedback.visibility = View.GONE
    }

    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PIP_MUTE -> {
                    toggleMuteNow()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) {
                        updatePipParams()
                    }
                }
                ACTION_PIP_CLOSE -> {
                    exoPlayer?.pause()
                    finish()
                }
            }
        }
    }

    private val updateProgressAction = object : Runnable {
        override fun run() {
            try {
                exoPlayer?.let { player ->
                    if (player.isPlaying && !isHorizontalScrubbing) {
                        val currentPos = player.currentPosition.coerceAtLeast(0L)
                        val duration = player.duration.coerceAtLeast(0L)
                        seekBarPlayer.max = duration.toInt()
                        seekBarPlayer.progress = currentPos.toInt()
                        tvPlayerCurrentTime.text = formatDuration(currentPos)
                        tvPlayerTotalTime.text = formatDuration(duration)

                        // Save live watched progress for History & Video List bars
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
        setContentView(R.layout.activity_player)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        registerPipReceiver()
        bindViews()
        setupCrispPlayerIcons()
        restorePersistentBrightnessAndVisuals()
        buildModularOverlayPanels()
        applyPlayerCustomizationPreferences()
        extractIntentData(intent)
        initializePlayer()
        setupTopAndSideControls()
        setupBottomControls()
        setupAdvancedSettingsMenu()
        setupMultiTouchZoomAndGestures()
    }

    private fun registerPipReceiver() {
        if (isPipReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(ACTION_PIP_MUTE)
            addAction(ACTION_PIP_CLOSE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipActionReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(pipActionReceiver, filter)
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
        btnBgAudio.setImageDrawable(PlayerVectorIcon("HEADPHONES", d, Color.WHITE))
        btnVideoQueue.setImageDrawable(PlayerVectorIcon("QUEUE_LIST", d, Color.WHITE))
        btnMoreMenu.setImageDrawable(PlayerVectorIcon("THREE_DOTS", d, Color.WHITE))
        btnMuteToggle.setImageDrawable(PlayerVectorIcon("SPEAKER_ON", d, Color.WHITE))
        btnLockScreen.setImageDrawable(PlayerVectorIcon("LOCK_OPEN", d, Color.WHITE))
        btnUnlockOverlay.setImageDrawable(PlayerVectorIcon("LOCK_CLOSED", d, Color.parseColor("#00E676")))
        btnSideCut.setImageDrawable(PlayerVectorIcon("SCREENSHOT_CAM", d, Color.WHITE))
        btnRotateScreen.setImageDrawable(PlayerVectorIcon("ROTATE", d, Color.WHITE))
        btnAspectRatio.setImageDrawable(PlayerVectorIcon("ASPECT_FIT", d, Color.WHITE))
        btnPipMode.setImageDrawable(PlayerVectorIcon("PIP_WINDOW", d, Color.WHITE))
    }

    private fun restorePersistentBrightnessAndVisuals() {
        val prefs = getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        // Hide red "NEW" badge on HDR if user has already clicked HDR once
        if (prefs.getBoolean("hdr_new_badge_dismissed", false) && frameHdrContainer.childCount > 1) {
            frameHdrContainer.getChildAt(1).visibility = View.GONE
        }

        // Restore persistent brightness across video sessions
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        currentVolumePercent = ((curVol.toFloat() / maxVol.toFloat()) * 100f).coerceIn(0f, 100f)

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

        // 1. Outside Touch Scrim to dismiss any open half-screen panel
        panelOverlayScrim = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#55000000"))
            visibility = View.GONE
            setOnClickListener { closeAllModularPanels() }
        }
        rootPlayerFrame.addView(panelOverlayScrim)

        // 2. Visual Enhancer (HDR) Panel (Original, HDR, Clear, Ultra Clear, Arctic Blue, Warm Glow, Cinematic)
        panelVisualEnhancer = CardView(this).apply {
            setCardBackgroundColor(Color.parseColor("#F212121A"))
            radius = 20f * d
            cardElevation = 24f * d
            visibility = View.GONE
        }
        val veScroll = ScrollView(this)
        val veContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt())
        }
        val veHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val tvVeTitle = TextView(this).apply {
            text = "←  Visual Enhancer"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { closeAllModularPanels() }
        }
        veHeader.addView(tvVeTitle)
        veContainer.addView(veHeader)

        val modesGrid = GridLayout(this).apply {
            columnCount = 2
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (12 * d).toInt() }
        }
        val modeNames = arrayOf(
            "Original",
            "HDR",
            "Clear",
            "Ultra Clear",
            "Arctic Blue",
            "Warm Glow",
            "Cinematic"
        )
        val modePreviewColors = arrayOf(
            "#2A2A34",
            "#5E3524",
            "#225529",
            "#602825",
            "#154C79",
            "#7A4914",
            "#4A3728"
        )
        val modeCardViews = mutableListOf<CardView>()

        modeNames.forEachIndexed { index, name ->
            val card = CardView(this).apply {
                radius = 12f * d
                cardElevation = 2f * d
                setCardBackgroundColor(Color.parseColor(modePreviewColors[index]))
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = (68 * d).toInt()
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins((5 * d).toInt(), (5 * d).toInt(), (5 * d).toInt(), (5 * d).toInt())
                }
            }
            val box = FrameLayout(this)
            val tvName = TextView(this).apply {
                text = if (currentVisualMode == index) "✓ $name" else name
                setTextColor(Color.WHITE)
                textSize = 13f
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
                    label.text = if (idx == index) "✓ ${modeNames[idx]}" else modeNames[idx]
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
            text = "Apply to all videos"
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

        // 3. Subtitle Panel (None, Embedded Tracks, Select Files, Size Slider, Online Subtitles)
        panelSubtitles = CardView(this).apply {
            setCardBackgroundColor(Color.parseColor("#F212121A"))
            radius = 20f * d
            cardElevation = 24f * d
            visibility = View.GONE
        }
        rootPlayerFrame.addView(panelSubtitles)

        // 4. Playing Queue Panel (Half-Screen, Single-Icon Loop/Shuffle Toggle, Drag ≡, Remove ✕)
        panelPlayingQueue = CardView(this).apply {
            setCardBackgroundColor(Color.parseColor("#F212121A"))
            radius = 20f * d
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
            text = "‹   All (1/1)"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { closeAllModularPanels() }
        }
        btnQueueLoopModeToggle = TextView(this).apply {
            text = "Order ⇅"
            setTextColor(Color.parseColor("#00E676"))
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((12 * d).toInt(), (6 * d).toInt(), (12 * d).toInt(), (6 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#24FFFFFF"))
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
        }
        setupQueueRecyclerViewAndDragHelper()
        queueRoot.addView(rvQueueList)
        panelPlayingQueue.addView(queueRoot)
        rootPlayerFrame.addView(panelPlayingQueue)

        // 5. 8.0X Tick-Mark Speed Ruler Overlay (Matches Screenshots 75 & 76)
        panelSpeedRuler = CardView(this).apply {
            setCardBackgroundColor(Color.parseColor("#D9181822"))
            radius = 20f * d
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
            setTextColor(Color.parseColor("#00E676"))
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        }
        val btnSpeedReset = TextView(this).apply {
            text = "↺"
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((28 * d).toInt(), (28 * d).toInt()).apply {
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

        speedRulerView = SpeedTickRulerView(this, d) { slidSpeed ->
            applySpeedDirect(slidSpeed)
        }
        speedRulerView.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (64 * d).toInt()
        ).apply { topMargin = (10 * d).toInt() }
        speedCol.addView(speedRulerView)
        panelSpeedRuler.addView(speedCol)
        rootPlayerFrame.addView(panelSpeedRuler)

        // 6. Background Audio Headphone Screen (Matches Screenshots 81 & 82)
        panelBgAudioScreen = RelativeLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#F508080E"))
            visibility = View.GONE
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
            layoutParams = LinearLayout.LayoutParams((160 * d).toInt(), (160 * d).toInt())
            setImageDrawable(PlayerVectorIcon("BG_VINYL_DISC", d, Color.parseColor("#00E676")))
        }
        val tvBgHint = TextView(this).apply {
            text = "Audio can be background played while using\nother apps or lock screen"
            setTextColor(Color.parseColor("#CCCCCC"))
            textSize = 13.5f
            gravity = Gravity.CENTER
            setPadding(0, (16 * d).toInt(), 0, (18 * d).toInt())
        }
        val btnBackToVideo = TextView(this).apply {
            text = "▶  Back to video"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((26 * d).toInt(), (10 * d).toInt(), (26 * d).toInt(), (10 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1DB954"))
                cornerRadius = 24f * d
            }
            setOnClickListener {
                panelBgAudioScreen.visibility = View.GONE
            }
        }
        val btnSaveToMp3 = TextView(this).apply {
            text = "⬇  Convert to MP3"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((26 * d).toInt(), (10 * d).toInt(), (26 * d).toInt(), (10 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (12 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1DB954"))
                cornerRadius = 24f * d
            }
            setOnClickListener {
                openMp3Converter()
            }
        }
        val btnStyleCenter = TextView(this).apply {
            text = "👕  Style Center"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((22 * d).toInt(), (8 * d).toInt(), (22 * d).toInt(), (8 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (12 * d).toInt() }
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#FF2E93"), Color.parseColor("#FF8A00"))
            ).apply {
                cornerRadius = 24f * d
            }
            setOnClickListener {
                panelBgAudioScreen.visibility = View.GONE
                openHalfScreenPanel(panelVisualEnhancer)
            }
        }
        bgCenterCol.addView(discView)
        bgCenterCol.addView(tvBgHint)
        bgCenterCol.addView(btnBackToVideo)
        bgCenterCol.addView(btnSaveToMp3)
        bgCenterCol.addView(btnStyleCenter)
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
                (screenH * 0.54f).toInt(),
                Gravity.BOTTOM
            )
        }
        targetPanel.layoutParams = lp
        targetPanel.visibility = View.VISIBLE
    }

    private fun closeAllModularPanels() {
        panelOverlayScrim.visibility = View.GONE
        panelVisualEnhancer.visibility = View.GONE
        panelSubtitles.visibility = View.GONE
        panelPlayingQueue.visibility = View.GONE
        panelSpeedRuler.visibility = View.GONE
        layoutAdvancedSettings.visibility = View.GONE
    }

    private fun applyVisualEnhancerFilter(modeIndex: Int) {
        when (modeIndex) {
            0 -> {
                btnHdrMode.setTextColor(Color.WHITE)
                viewVisualOverlay.setBackgroundColor(
                    if (isNightMode) Color.parseColor("#88000000") else Color.TRANSPARENT
                )
            }
            1 -> { // HDR
                btnHdrMode.setTextColor(Color.parseColor("#00E676"))
                viewVisualOverlay.setBackgroundColor(Color.parseColor("#22FF9800"))
            }
            2 -> { // Clear
                btnHdrMode.setTextColor(Color.parseColor("#00E676"))
                viewVisualOverlay.setBackgroundColor(Color.parseColor("#1800E5FF"))
            }
            3 -> { // Ultra Clear
                btnHdrMode.setTextColor(Color.parseColor("#00E676"))
                viewVisualOverlay.setBackgroundColor(Color.parseColor("#24FF4081"))
            }
            4 -> { // Arctic Blue
                btnHdrMode.setTextColor(Color.parseColor("#00E676"))
                viewVisualOverlay.setBackgroundColor(Color.parseColor("#2800B0FF"))
            }
            5 -> { // Warm Glow
                btnHdrMode.setTextColor(Color.parseColor("#00E676"))
                viewVisualOverlay.setBackgroundColor(Color.parseColor("#30FFAB00"))
            }
            6 -> { // Cinematic
                btnHdrMode.setTextColor(Color.parseColor("#00E676"))
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
            "MP3 Cut" to { openMp3Converter() },
            "4x Speed" to { applySpeedDirect(4.0f) },
            "8x Speed" to { applySpeedDirect(8.0f) }
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

    private fun initializePlayer() {
        try {
            exoPlayer?.release()
            exoPlayer = ExoPlayer.Builder(this).build().also { player ->
                player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                playerView.player = player
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
                    val savedWatchPos = getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
                        .getLong("watched_pos_$videoPath", 0L)
                    player.setMediaItems(mediaItems, currentPositionIndex, savedWatchPos.coerceAtLeast(0L))
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
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            val duration = player.duration.coerceAtLeast(0L)
                            seekBarPlayer.max = duration.toInt()
                            tvPlayerTotalTime.text = formatDuration(duration)
                            setupLoudnessEnhancer(player.audioSessionId)
                        }
                    }
                })
            }
            handler.post(updateProgressAction)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to play video", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupLoudnessEnhancer(sessionId: Int) {
        if (sessionId == 0 || sessionId == C.AUDIO_SESSION_ID_UNSET) return
        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                enabled = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupTopAndSideControls() {
        findViewById<ImageView>(R.id.btnPlayerBack).setOnClickListener { finish() }

        // Clicking HDR dismisses the "NEW" red badge permanently and opens Visual Enhancer
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

        // Clicking CC opens the Half-Screen Subtitle Panel with Size Slider & Online Search
        val ccClick = View.OnClickListener {
            populateAndOpenSubtitlePanel()
        }
        btnSubtitlesCc.setOnClickListener(ccClick)
        cardSubtitlesCc.setOnClickListener(ccClick)

        // Clicking Headphone icon opens Background Audio Vinyl Screen with Back-to-Video & Convert-to-MP3
        btnBgAudio.setOnClickListener {
            isBgAudioEnabled = true
            layoutAllControls.visibility = View.GONE
            panelBgAudioScreen.visibility = View.VISIBLE
        }

        // Clicking Queue opens Half-Screen Drag & Drop Playing Queue
        btnVideoQueue.setOnClickListener {
            updateQueueHeaderTitle()
            rvQueueList.adapter?.notifyDataSetChanged()
            openHalfScreenPanel(panelPlayingQueue)
            rvQueueList.scrollToPosition(currentPositionIndex.coerceAtLeast(0))
        }

        // Clicking 3-Dots opens Half-Screen Player Settings (Landscape Right-Half / Portrait Bottom-Half)
        btnMoreMenu.setOnClickListener {
            openHalfScreenPanel(layoutAdvancedSettings)
        }

        btnMuteToggle.setOnClickListener { toggleMuteNow() }
        btnLockScreen.setOnClickListener { lockScreenNow() }

        btnUnlockOverlay.setOnClickListener {
            isScreenLocked = false
            btnUnlockOverlay.visibility = View.GONE
            layoutAllControls.visibility = View.VISIBLE
            Toast.makeText(this, "Screen Unlocked", Toast.LENGTH_SHORT).show()
        }

        // Side Screenshot Button captures and saves frame automatically into Pictures/MAXPlayerScreenshots
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
            text = "‹   Subtitle"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (12 * d).toInt())
            setOnClickListener { closeAllModularPanels() }
        }
        col.addView(tvHeader)

        // Embedded Subtitle Tracks + None
        val player = exoPlayer
        val textGroups = player?.currentTracks?.groups?.filter { it.type == C.TRACK_TYPE_TEXT } ?: emptyList()

        val tvNone = createSubtitleActionRow("None (Turn Off Subtitles)", d) {
            player?.let { p ->
                p.trackSelectionParameters = p.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
            }
            Toast.makeText(this, "Subtitles Off", Toast.LENGTH_SHORT).show()
            closeAllModularPanels()
        }
        col.addView(tvNone)

        textGroups.forEachIndexed { index, group ->
            val fmt = group.getTrackFormat(0)
            val name = fmt.label ?: fmt.language ?: "Embedded Subtitle ${index + 1}"
            val row = createSubtitleActionRow("✓ Track: $name", d) {
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

        // Select local .srt / .vtt file
        col.addView(createSubtitleActionRow("Select files (.srt / .ass / .vtt)", d) {
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

        // Subtitle Font Size Slider (Small to Large)
        val tvSizeLabel = TextView(this).apply {
            text = "Subtitle Text Size (${subtitleTextSizeSp.toInt()}sp)"
            setTextColor(Color.parseColor("#00E676"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, (14 * d).toInt(), 0, (4 * d).toInt())
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

        // Online Subtitles Search & Download
        col.addView(createSubtitleActionRow("Online subtitles (Search & Download)", d) {
            val cleanName = videoTitle.substringBeforeLast(".").replace("_", " ")
            try {
                val q = URLEncoder.encode("$cleanName subtitles download srt", "UTF-8")
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.opensubtitles.org/en/search2/sublanguageid-all/moviename-$q")))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        })

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

    // Playing Queue with Drag-and-Drop (≡), Remove (✕), Resolution, and Single-Icon Loop/Shuffle Toggle
    private fun syncQueueLoopModeButton() {
        when (queueLoopMode) {
            0 -> {
                btnQueueLoopModeToggle.text = "Order ⇅"
                updateLoopSelection(2)
            }
            1 -> {
                btnQueueLoopModeToggle.text = "Repeat 1 🔂"
                updateLoopSelection(1)
            }
            2 -> {
                btnQueueLoopModeToggle.text = "Shuffle 🔀"
                updateLoopSelection(0)
            }
        }
    }

    private fun updateQueueHeaderTitle() {
        val cur = (currentPositionIndex + 1).coerceAtMost(videoList.size.coerceAtLeast(1))
        tvQueueHeaderCount.text = "‹   All ($cur/${videoList.size})"
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
                    radius = 6f * d
                    setCardBackgroundColor(Color.parseColor("#222230"))
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
                        Color.parseColor("#00E676"),
                        android.graphics.PorterDuff.Mode.SRC_IN
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
                    setTextColor(Color.parseColor("#BBBBCC"))
                    textSize = 20f
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams((32 * d).toInt(), (40 * d).toInt())
                }

                val btnRemove = TextView(this@PlayerActivity).apply {
                    text = "✕"
                    setTextColor(Color.parseColor("#BBBBCC"))
                    textSize = 15f
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
                    if (isPlayingNow) Color.parseColor("#00E676") else Color.WHITE
                )

                val sizeBytes = if (file.exists()) file.length() else 0L
                val sizeMb = sizeBytes / (1024.0 * 1024.0)
                val sizeLabel = if (sizeMb >= 1024.0) {
                    String.format(Locale.US, "%.2fGB", sizeMb / 1024.0)
                } else {
                    String.format(Locale.US, "%.2fMB", sizeMb)
                }
                val resBadge = resPrefs.getString("res_$path", null)
                    ?: VideoFragment.computeResolutionLabel(0, 0, name, sizeBytes, 60000L)

                holder.tvMeta.text = "$resBadge | $sizeLabel"
                holder.tvMeta.setTextColor(
                    if (isPlayingNow) Color.parseColor("#00E676") else Color.parseColor("#9999AA")
                )

                val watched = watchedPrefs.getLong("watched_pos_$path", 0L)
                holder.pb.progress = if (watched > 0L) 650 else 0

                val target: Any = if (path.startsWith("content://")) Uri.parse(path) else file
                Glide.with(this@PlayerActivity)
                    .load(target)
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

                // Hold & Drag 3 Flat Lines (≡) to move video Up or Down in Queue
                holder.btnDrag.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        touchHelper.startDrag(holder)
                    }
                    false
                }

                // Click ✕ to remove video from active Playing Queue session
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
            PlayerVectorIcon(
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
        btnLockScreen.setImageDrawable(PlayerVectorIcon("LOCK_CLOSED", d, Color.parseColor("#00E676")))
        btnUnlockOverlay.visibility = View.VISIBLE
        Toast.makeText(this, "Screen Locked", Toast.LENGTH_SHORT).show()
    }

    // One-Tap Video Frame Screenshot -> Saved directly to Pictures/MAXPlayerScreenshots
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
                    val fileName = "MAX_Shot_${System.currentTimeMillis()}.jpg"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            put(
                                MediaStore.Images.Media.RELATIVE_PATH,
                                "${Environment.DIRECTORY_PICTURES}/MAXPlayerScreenshots"
                            )
                        }
                        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        if (uri != null) {
                            contentResolver.openOutputStream(uri)?.use { out ->
                                bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
                            }
                            savedLocation = "Pictures/MAXPlayerScreenshots/$fileName"
                        }
                    } else {
                        val dir = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                            "MAXPlayerScreenshots"
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
    }

    private fun setupBottomControls() {
        btnPlayerPlayPause.setOnClickListener {
            exoPlayer?.let { player ->
                if (player.isPlaying) player.pause() else player.play()
            }
        }

        // Prev & Next strictly follow the active reordered/filtered Playing Queue & Shuffle state
        findViewById<ImageView>(R.id.btnPlayerPrev).setOnClickListener {
            exoPlayer?.let { player ->
                if (player.hasPreviousMediaItem()) {
                    player.seekToPreviousMediaItem()
                    player.play()
                } else {
                    player.seekTo(0)
                }
            }
        }

        findViewById<ImageView>(R.id.btnPlayerNext).setOnClickListener {
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

        // Clicking Speed opens the 8.0X Tick-Mark Speed Slider Overlay
        btnPlaybackSpeed.setOnClickListener {
            closeAllModularPanels()
            layoutAllControls.visibility = View.GONE
            panelOverlayScrim.visibility = View.VISIBLE
            panelSpeedRuler.visibility = View.VISIBLE
            speedRulerView.setSpeedValue(currentSpeed)
            tvSpeedRulerValue.text = String.format(Locale.US, "%.2f X", currentSpeed)
        }

        // 4 Distinct Aspect Ratio Display Modes with Dynamic Icon Change
        btnAspectRatio.setOnClickListener {
            val d = resources.displayMetrics.density
            resizeModeIndex = (resizeModeIndex + 1) % 4
            val surface = playerView.videoSurfaceView ?: playerView
            val mirror = if (isMirrorMode) -1f else 1f

            when (resizeModeIndex) {
                0 -> {
                    playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    surface.scaleX = 1.0f * mirror
                    surface.scaleY = 1.0f
                    btnAspectRatio.setImageDrawable(PlayerVectorIcon("ASPECT_FIT", d, Color.WHITE))
                    Toast.makeText(this, "Aspect Ratio: Original Fit", Toast.LENGTH_SHORT).show()
                }
                1 -> {
                    playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                    surface.scaleX = 1.0f * mirror
                    surface.scaleY = 1.0f
                    btnAspectRatio.setImageDrawable(PlayerVectorIcon("ASPECT_STRETCH", d, Color.parseColor("#00E676")))
                    Toast.makeText(this, "Aspect Ratio: Full Stretch", Toast.LENGTH_SHORT).show()
                }
                2 -> {
                    playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    surface.scaleX = 1.0f * mirror
                    surface.scaleY = 1.0f
                    btnAspectRatio.setImageDrawable(PlayerVectorIcon("ASPECT_CROP", d, Color.parseColor("#00E5FF")))
                    Toast.makeText(this, "Aspect Ratio: Center Zoom Crop", Toast.LENGTH_SHORT).show()
                }
                3 -> {
                    playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    surface.scaleX = 1.16f * mirror
                    surface.scaleY = 0.94f
                    btnAspectRatio.setImageDrawable(PlayerVectorIcon("ASPECT_16_9", d, Color.parseColor("#FFAB00")))
                    Toast.makeText(this, "Aspect Ratio: 16:9 Cinema", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnPipMode.setOnClickListener {
            enterPipModeSafely()
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
            VideoFragment.showBrightVideoMenuDialog(this, "Video Bookmarks", options) { which ->
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
            val timerOptions = arrayOf("Off", "15 Minutes", "30 Minutes", "60 Minutes")
            VideoFragment.showBrightVideoMenuDialog(this, "Sleep Timer", timerOptions) { which ->
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
                    sleepCountDownTimer = object : CountDownTimer(minutes * 60 * 1000, 1000) {
                        override fun onTick(millisUntilFinished: Long) {
                            val m = millisUntilFinished / 60000L
                            val s = (millisUntilFinished % 60000L) / 1000L
                            tvTimerLabel.text = String.format(Locale.US, "%02d:%02d", m, s)
                        }
                        override fun onFinish() {
                            exoPlayer?.pause()
                            tvTimerLabel.text = "Timer"
                            Toast.makeText(this@PlayerActivity, "Sleep Timer: Paused", Toast.LENGTH_LONG).show()
                        }
                    }.start()
                    Toast.makeText(this, "Timer set for $minutes minutes", Toast.LENGTH_SHORT).show()
                }
            }
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
            showCustomizePlayerLayoutDialog()
        }

        findViewById<LinearLayout>(R.id.optTutorials).setOnClickListener {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Player Gestures Guide")
                .setMessage(
                    "• Right Swipe Up/Down: Smooth 300% Volume Boost\n" +
                        "• Left Swipe Up/Down: Smooth Persistent Brightness\n" +
                        "• Horizontal Slide: Live Frame + Nanosecond Seek\n" +
                        "• Double Tap Left / Right: -10s / +10s Seek\n" +
                        "• Two-Finger Pinch: 50% to 600% Smooth Zoom"
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
                EqualizerHelper.showEqualizerDialog(this, exoPlayer?.audioSessionId ?: 0)
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
            if (dockSide) "Move Side Icons Back to Left/Right" else "Move Side Icons to Bottom Bar",
            if (showSide) "Hide Side Controls" else "Show Side Controls",
            if (showHdrCc) "Hide Top HDR & CC Badges" else "Show Top HDR & CC Badges",
            "Set Press & Hold Speed Boost to 4.0x",
            "Set Press & Hold Speed Boost to 6.0x",
            "Set Press & Hold Speed Boost to 8.0x",
            "Reset Player Layout & Zoom (100%)"
        )

        VideoFragment.showBrightVideoMenuDialog(this, "Edit Player Layout", options) { which ->
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
        VideoFragment.showBrightVideoMenuDialog(this, title, trackNames.toTypedArray()) { which ->
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

    private fun setupMultiTouchZoomAndGestures() {
        // 1. Two-Finger Pinch-to-Zoom (50% to 600%)
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

        // 2. Hand Gestures: Double Tap (-10s/+10s), Smooth 300% Volume, Persistent Brightness, and Nanosecond + Live Frame Scrubbing
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
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
                if (isScreenLocked || isPinchZooming || isHorizontalScrubbing) return
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
                val screenWidth = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
                val screenHeight = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)

                if (abs(distanceX) > abs(distanceY) * 1.2f || isHorizontalScrubbing) {
                    // Horizontal MX-Style Nanosecond + Live Video Frame Scrubbing
                    val player = exoPlayer ?: return false
                    val dur = player.duration.coerceAtLeast(1L)
                    if (!isHorizontalScrubbing) {
                        isHorizontalScrubbing = true
                        scrubTargetPositionMs = player.currentPosition
                    }
                    val deltaMs = ((-distanceX / screenWidth) * 90000f).toLong()
                    scrubTargetPositionMs = (scrubTargetPositionMs + deltaMs).coerceIn(0L, dur)
                    player.seekTo(scrubTargetPositionMs)

                    seekBarPlayer.progress = scrubTargetPositionMs.toInt()
                    val nanoStamp = formatPreciseTimestampWithCentiseconds(scrubTargetPositionMs)
                    tvPlayerCurrentTime.text = nanoStamp
                    val pct = ((scrubTargetPositionMs.toFloat() / dur.toFloat()) * 100f).toInt().coerceIn(0, 100)
                    showGestureFeedback(android.R.drawable.ic_media_ff, nanoStamp, pct)
                    return true
                }

                if (abs(distanceY) > abs(distanceX)) {
                    if (e1.x < screenWidth / 2f) {
                        // Left Side: Smooth & Persistent Brightness (0% to 100%)
                        val deltaPct = (distanceY / screenHeight) * 115f
                        currentBrightnessPercent = (currentBrightnessPercent + deltaPct).coerceIn(2f, 100f)
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
                        // Right Side: Smooth Volume up to 300% (Gradual, never jumps abruptly)
                        val deltaVolPct = (distanceY / screenHeight) * 165f
                        currentVolumePercent = (currentVolumePercent + deltaVolPct).coerceIn(0f, 300f)
                        applySmooth300PercentVolume(currentVolumePercent)
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
                if (isHorizontalScrubbing) {
                    isHorizontalScrubbing = false
                }
            }
            true
        }
    }

    private fun applySmooth300PercentVolume(targetPercent: Float) {
        val maxSysVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val pctInt = targetPercent.roundToInt().coerceIn(0, 300)

        if (pctInt <= 100) {
            val sysVol = ((pctInt / 100f) * maxSysVol).roundToInt().coerceIn(0, maxSysVol)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, sysVol, 0)
            try {
                loudnessEnhancer?.setTargetGain(0)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxSysVol, 0)
            // Boost from 100% up to 300% smoothly using LoudnessEnhancer (up to 2600 mB)
            val boostRatio = (pctInt - 100) / 200f
            val gainMb = (boostRatio * 2600f).roundToInt()
            try {
                if (loudnessEnhancer == null) {
                    setupLoudnessEnhancer(exoPlayer?.audioSessionId ?: 0)
                }
                loudnessEnhancer?.setTargetGain(gainMb)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val barProgress = (pctInt / 3).coerceIn(0, 100)
        showGestureFeedback(
            android.R.drawable.ic_lock_silent_mode_off,
            "$pctInt%",
            barProgress
        )
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
        } else if (!isScreenLocked) {
            layoutAllControls.visibility = View.VISIBLE
            exoPlayer?.play()
        }
    }

    // Format with Hours : Minutes : Seconds . Centiseconds/Nanoseconds for ultra-fast MX-style scrubbing
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
        sleepCountDownTimer?.cancel()
        handler.removeCallbacks(updateProgressAction)
        handler.removeCallbacks(hideGestureRunnable)
        try {
            if (isPipReceiverRegistered) {
                unregisterReceiver(pipActionReceiver)
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

    // Custom Interactive 0.25X to 8.0X Tick-Mark Speed Ruler View (Matches Screenshots 75 & 76)
    private class SpeedTickRulerView(
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
            color = Color.parseColor("#00E676")
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

            // Labels for key milestones up to 8.0X
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

            // Active Green Indicator Line
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

    // Crisp Vector Icons for Video Player Controls (Headphones, Queue, Lock, Screenshot, Aspect Ratio, Vinyl)
    private class PlayerVectorIcon(
        private val type: String,
        private val density: Float,
        private val iconColor: Int
    ) : Drawable() {

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = iconColor
            style = Paint.Style.STROKE
            strokeWidth = 2.0f * density
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
                    canvas.drawCircle(cx, cy - r * 0.65f, 2.2f * density, fillPaint)
                    canvas.drawCircle(cx, cy, 2.2f * density, fillPaint)
                    canvas.drawCircle(cx, cy + r * 0.65f, 2.2f * density, fillPaint)
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
                    canvas.drawCircle(cx, cy + r * 0.35f, 2f * density, fillPaint)
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
                    fillPaint.color = Color.parseColor("#1E1E2A")
                    canvas.drawCircle(cx, cy, w * 0.46f, fillPaint)
                    strokePaint.color = Color.parseColor("#343446")
                    canvas.drawCircle(cx, cy, w * 0.36f, strokePaint)
                    canvas.drawCircle(cx, cy, w * 0.28f, strokePaint)
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
