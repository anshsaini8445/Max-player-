package com.app.mediaplayer

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.TextUtils
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.ArrayList
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(UnstableApi::class)
class AudioPlayerActivity : AppCompatActivity() {

    companion object {
        private const val REQ_PICK_CUSTOM_PHOTO = 7201
        private var sharedAudioPlayer: ExoPlayer? = null
        private var currentPlayingPath: String = ""
    }

    private lateinit var rootFrame: FrameLayout
    private lateinit var tvTopSongTitle: TextView
    private lateinit var tvTopArtistName: TextView

    private lateinit var centerArtworkWrapper: FrameLayout
    private lateinit var cardSquarePhotoBox: CardView
    private lateinit var imgSquareArtPhoto: ImageView
    private lateinit var vinylDiscContainer: FrameLayout
    private lateinit var imgVinylCenterArt: ImageView
    private lateinit var toneArmView: View

    private lateinit var btnToolEqualizer: LinearLayout
    private lateinit var btnToolAbRepeat: LinearLayout
    private lateinit var tvToolAbLabel: TextView
    private lateinit var btnToolSpeed: LinearLayout
    private lateinit var tvToolSpeedLabel: TextView
    private lateinit var btnToolTimer: LinearLayout
    private lateinit var tvToolTimerLabel: TextView
    private lateinit var btnToolThreeDots: ImageView

    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var waveformSeekView: WaveformSeekViewInternal
    private lateinit var simpleSeekBar: SeekBar

    private lateinit var btnShuffleMode: ImageView
    private lateinit var btnPrevSong: ImageView
    private lateinit var btnPlayPauseCircle: ImageView
    private lateinit var btnNextSong: ImageView
    private lateinit var btnRepeatMode: ImageView

    private lateinit var tvPocketShakeToggle: TextView
    private lateinit var tvPlayingQueueTrigger: TextView

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var mediaSessionManager: MediaSessionManager? = null
    private var smartIdlePauseHelper: SmartIdlePauseHelper? = null
    private var pocketShakeDetector: PocketShakeDetector? = null
    private var volumeHudHelper: VolumeHudOverlayHelper? = null
    private lateinit var audioManager: AudioManager

    private val songPaths = mutableListOf<String>()
    private val songTitles = mutableListOf<String>()
    private val songArtists = mutableListOf<String>()
    private var currentIndex = 0

    private var useVinylDiscMode = false
    private var useWaveformSeek = true
    private var isShuffleEnabled = false
    private var repeatModeState = Player.REPEAT_MODE_ALL
    private var currentPlaybackSpeed = 1.0f
    private var pointAMs = -1L
    private var pointBMs = -1L
    private var isReceiverRegistered = false

    private var vinylAnimator: ObjectAnimator? = null
    private val handler = Handler(Looper.getMainLooper())

    private val progressRunnable = object : Runnable {
        override fun run() {
            try {
                sharedAudioPlayer?.let { p ->
                    val cur = p.currentPosition.coerceAtLeast(0L)
                    val dur = p.duration.coerceAtLeast(1L)
                    tvCurrentTime.text = formatTimeMs(cur)
                    tvTotalTime.text = formatTimeMs(dur)

                    val progressFraction = (cur.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                    waveformSeekView.setProgressFraction(progressFraction)
                    simpleSeekBar.max = dur.toInt()
                    simpleSeekBar.progress = cur.toInt()

                    if (pointAMs != -1L && pointBMs != -1L && cur >= pointBMs) {
                        p.seekTo(pointAMs)
                    }
                }
            } catch (_: Exception) {
            }
            handler.postDelayed(this, 250L)
        }
    }

    private val notifCommandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BackgroundAudioService.ACTION_NOTIF_PREV -> playPreviousSong()
                BackgroundAudioService.ACTION_NOTIF_PLAY_PAUSE -> togglePlayPause()
                BackgroundAudioService.ACTION_NOTIF_NEXT -> playNextSong()
                BackgroundAudioService.ACTION_NOTIF_SEEK -> {
                    val seekTarget = intent.getLongExtra("SEEK_POS_MS", 0L)
                    sharedAudioPlayer?.seekTo(seekTarget)
                    syncNotificationAndLockScreen()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val prefs = getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
        useVinylDiscMode = prefs.getBoolean("audio_use_vinyl_disc", false)
        useWaveformSeek = prefs.getBoolean("audio_use_waveform_seek", true)

        ThemeStudioHelper.applyThemeAndOneHandModeToRoot(this, rootFrame)
        buildCompleteAudioPlayerUi()
        registerNotifReceiver()
        extractIntentPlaylist(intent)
        setupPocketShakeSensor()
        setupSmartSleepTimerHelper()
        initOrAttachAudioPlayer()

        volumeHudHelper = VolumeHudOverlayHelper(
            context = this,
            rootContainer = rootFrame,
            audioManager = audioManager,
            loudnessEnhancerProvider = { loudnessEnhancer },
            playerVolumeSetter = { vol -> sharedAudioPlayer?.volume = vol }
        )
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            val resumeOnly = intent.getBooleanExtra("RESUME_EXISTING", false)
            val incomingPath = intent.getStringExtra("AUDIO_PATH") ?: ""
            if (!resumeOnly && incomingPath.isNotEmpty() && incomingPath != currentPlayingPath) {
                extractIntentPlaylist(intent)
                initOrAttachAudioPlayer(forceReload = true)
            } else {
                refreshUiForCurrentSong()
            }
        }
    }

    private fun buildCompleteAudioPlayerUi() {
        val d = resources.displayMetrics.density
        val theme = ThemeStudioHelper.getCurrentTheme(this)
        val accentColor = Color.parseColor(theme.accentHex)

        rootFrame = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.parseColor("#0D1B2A"),
                    Color.parseColor(theme.bgHex),
                    Color.parseColor("#06080E")
                )
            )
        }

        val mainCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding((18 * d).toInt(), (16 * d).toInt(), (18 * d).toInt(), (18 * d).toInt())
        }

        // Top Bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val btnMinimize = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((42 * d).toInt(), (42 * d).toInt())
            setPadding((10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt())
            setImageDrawable(AudioVectorIconDrawable("CHEVRON_DOWN", d, Color.WHITE))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1FFFFFFF"))
            }
            setOnClickListener { finish() }
        }

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (12 * d).toInt()
                marginEnd = (12 * d).toInt()
            }
        }

        tvTopSongTitle = TextView(this).apply {
            text = "Playing Song"
            setTextColor(Color.WHITE)
            textSize = 17.5f
            typeface = Typeface.DEFAULT_BOLD
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
            gravity = Gravity.CENTER
        }

        tvTopArtistName = TextView(this).apply {
            text = "Hybrid Player Studio"
            setTextColor(accentColor)
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(0, (3 * d).toInt(), 0, 0)
        }

        titleCol.addView(tvTopSongTitle)
        titleCol.addView(tvTopArtistName)

        val btnShareSong = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((42 * d).toInt(), (42 * d).toInt())
            setPadding((10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt())
            setImageDrawable(AudioVectorIconDrawable("SHARE_NODES", d, Color.WHITE))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1FFFFFFF"))
            }
            setOnClickListener { shareCurrentSong() }
        }

        topBar.addView(btnMinimize)
        topBar.addView(titleCol)
        topBar.addView(btnShareSong)
        mainCol.addView(topBar)

        // Center Artwork with Left/Right Swipe Gesture to change songs
        centerArtworkWrapper = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val boxSize = (268 * d).toInt()
        cardSquarePhotoBox = CardView(this).apply {
            radius = 26f * d
            cardElevation = 18f * d
            setCardBackgroundColor(Color.parseColor("#121C2D"))
            layoutParams = FrameLayout.LayoutParams(boxSize, boxSize, Gravity.CENTER)
        }

        val squareBorderFrame = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#121C2D"))
                cornerRadius = 26f * d
                setStroke((2f * d).toInt(), accentColor)
            }
        }

        imgSquareArtPhoto = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        squareBorderFrame.addView(imgSquareArtPhoto)
        cardSquarePhotoBox.addView(squareBorderFrame)
        centerArtworkWrapper.addView(cardSquarePhotoBox)

        vinylDiscContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(boxSize, boxSize, Gravity.CENTER)
            visibility = View.GONE
        }

        val vinylPlatterView = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            background = VinylGroovesDrawable(d, accentColor)
        }

        val innerCircleSize = (138 * d).toInt()
        val innerArtCard = CardView(this).apply {
            radius = (innerCircleSize / 2f)
            cardElevation = 4f * d
            layoutParams = FrameLayout.LayoutParams(innerCircleSize, innerCircleSize, Gravity.CENTER)
        }
        imgVinylCenterArt = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        innerArtCard.addView(imgVinylCenterArt)
        vinylDiscContainer.addView(vinylPlatterView)
        vinylDiscContainer.addView(innerArtCard)

        toneArmView = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                (90 * d).toInt(),
                (40 * d).toInt(),
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply { topMargin = (10 * d).toInt() }
            background = AudioVectorIconDrawable("TONE_ARM", d, Color.WHITE)
            visibility = View.GONE
        }

        centerArtworkWrapper.addView(vinylDiscContainer)
        centerArtworkWrapper.addView(toneArmView)

        val swipeDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                if (abs(dx) > 120f && abs(velocityX) > 250f) {
                    if (dx < 0) playNextSong() else playPreviousSong()
                    return true
                }
                return false
            }
        })
        centerArtworkWrapper.setOnTouchListener { _, event ->
            swipeDetector.onTouchEvent(event)
            true
        }

        mainCol.addView(centerArtworkWrapper)

        vinylAnimator = ObjectAnimator.ofFloat(vinylDiscContainer, View.ROTATION, 0f, 360f).apply {
            duration = 14000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
        }

        updateCenterArtworkStyleVisibility()

        // Tools Strip
        val toolsStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((4 * d).toInt(), (8 * d).toInt(), (4 * d).toInt(), (10 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val eqPair = createToolStripButton("EQ_SLIDERS", "Equalizer", d, accentColor) {
            val session = sharedAudioPlayer?.audioSessionId ?: 0
            EqualizerHelper.showEqualizerDialog(this, session)
        }
        btnToolEqualizer = eqPair.first

        val abPair = createToolStripButton("AB_LOOP", "A-B", d, Color.WHITE) {
            cycleAbRepeatPoints()
        }
        btnToolAbRepeat = abPair.first
        tvToolAbLabel = abPair.second

        val speedPair = createToolStripButton("SPEED_GAUGE", "1.0x", d, Color.WHITE) {
            showSwipeableSpeedRulerDialog()
        }
        btnToolSpeed = speedPair.first
        tvToolSpeedLabel = speedPair.second

        val timerPair = createToolStripButton("TIMER_CLOCK", "Timer", d, Color.WHITE) {
            openSmartSleepTimerDialog()
        }
        btnToolTimer = timerPair.first
        tvToolTimerLabel = timerPair.second

        btnToolThreeDots = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((38 * d).toInt(), (38 * d).toInt())
            setPadding((7 * d).toInt(), (7 * d).toInt(), (7 * d).toInt(), (7 * d).toInt())
            setImageDrawable(AudioVectorIconDrawable("THREE_DOTS_CIRCLE", d, accentColor))
            setOnClickListener { showAudioThreeDotsOptionsDialog() }
        }

        toolsStrip.addView(btnToolEqualizer)
        toolsStrip.addView(btnToolAbRepeat)
        toolsStrip.addView(btnToolSpeed)
        toolsStrip.addView(btnToolTimer)
        toolsStrip.addView(btnToolThreeDots)
        mainCol.addView(toolsStrip)

        // Timestamps & Waveform Seek
        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt())
        }

        tvCurrentTime = TextView(this).apply {
            text = "00:00"
            setTextColor(Color.WHITE)
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        tvTotalTime = TextView(this).apply {
            text = "00:00"
            setTextColor(Color.WHITE)
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
        }

        timeRow.addView(tvCurrentTime)
        timeRow.addView(tvTotalTime)
        mainCol.addView(timeRow)

        waveformSeekView = WaveformSeekViewInternal(this, d, accentColor) { seekFraction ->
            sharedAudioPlayer?.let { p ->
                val target = (p.duration.coerceAtLeast(1L) * seekFraction).toLong()
                p.seekTo(target)
                tvCurrentTime.text = formatTimeMs(target)
                syncNotificationAndLockScreen()
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (44 * d).toInt()
            ).apply { bottomMargin = (10 * d).toInt() }
        }
        mainCol.addView(waveformSeekView)

        simpleSeekBar = SeekBar(this).apply {
            max = 1000
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * d).toInt() }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) {
                        sharedAudioPlayer?.seekTo(p.toLong())
                        tvCurrentTime.text = formatTimeMs(p.toLong())
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {
                    syncNotificationAndLockScreen()
                }
            })
        }
        mainCol.addView(simpleSeekBar)
        updateSeekbarStyleVisibility()

        // Transport Row
        val transportRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt(), (14 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        btnShuffleMode = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((42 * d).toInt(), (42 * d).toInt())
            setPadding((9 * d).toInt(), (9 * d).toInt(), (9 * d).toInt(), (9 * d).toInt())
            setOnClickListener {
                isShuffleEnabled = !isShuffleEnabled
                sharedAudioPlayer?.shuffleModeEnabled = isShuffleEnabled
                updateShuffleAndRepeatIcons()
                Toast.makeText(this@AudioPlayerActivity, if (isShuffleEnabled) "Shuffle ON" else "Shuffle OFF", Toast.LENGTH_SHORT).show()
            }
        }

        val spacer1 = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }

        btnPrevSong = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((46 * d).toInt(), (46 * d).toInt())
            setPadding((10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt())
            setImageDrawable(AudioVectorIconDrawable("PREV_TRACK", d, Color.WHITE))
            setOnClickListener { playPreviousSong() }
        }

        val spacer2 = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }

        btnPlayPauseCircle = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((68 * d).toInt(), (68 * d).toInt())
            setImageDrawable(AudioVectorIconDrawable("PLAY_CIRCLE", d, accentColor))
            setOnClickListener { togglePlayPause() }
        }

        val spacer3 = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }

        btnNextSong = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((46 * d).toInt(), (46 * d).toInt())
            setPadding((10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt())
            setImageDrawable(AudioVectorIconDrawable("NEXT_TRACK", d, Color.WHITE))
            setOnClickListener { playNextSong() }
        }

        val spacer4 = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }

        btnRepeatMode = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((42 * d).toInt(), (42 * d).toInt())
            setPadding((8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
            setOnClickListener {
                repeatModeState = when (repeatModeState) {
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
                    else -> Player.REPEAT_MODE_ALL
                }
                sharedAudioPlayer?.repeatMode = repeatModeState
                updateShuffleAndRepeatIcons()
                val msg = when (repeatModeState) {
                    Player.REPEAT_MODE_ONE -> "Repeat Current Song (1)"
                    Player.REPEAT_MODE_OFF -> "Repeat OFF (Play in Order)"
                    else -> "Loop All Songs"
                }
                Toast.makeText(this@AudioPlayerActivity, msg, Toast.LENGTH_SHORT).show()
            }
        }

        transportRow.addView(btnShuffleMode)
        transportRow.addView(spacer1)
        transportRow.addView(btnPrevSong)
        transportRow.addView(spacer2)
        transportRow.addView(btnPlayPauseCircle)
        transportRow.addView(spacer3)
        transportRow.addView(btnNextSong)
        transportRow.addView(spacer4)
        transportRow.addView(btnRepeatMode)
        mainCol.addView(transportRow)

        updateShuffleAndRepeatIcons()

        // Bottom Status Row
        val bottomFooterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((6 * d).toInt(), (4 * d).toInt(), (6 * d).toInt(), (4 * d).toInt())
        }

        tvPocketShakeToggle = TextView(this).apply {
            text = "📳  Pocket Shake: OFF"
            setTextColor(Color.parseColor("#B0C4DE"))
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((10 * d).toInt(), (8 * d).toInt(), (10 * d).toInt(), (8 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                val cur = pocketShakeDetector?.isShakeEnabled() == true
                val next = !cur
                pocketShakeDetector?.setEnabled(next)
                getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("pocket_shake_enabled", next)
                    .apply()
                updatePocketShakeUi(next)
                Toast.makeText(
                    this@AudioPlayerActivity,
                    if (next) "Smart In-Hand Shake ON (Auto-sleeps in pocket/walking/bike)" else "Pocket Shake OFF",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        tvPlayingQueueTrigger = TextView(this).apply {
            text = "🎵  Playing Queue (1/1)"
            setTextColor(accentColor)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (8 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1800FFFF"))
                cornerRadius = 16f * d
                setStroke((1 * d).toInt(), Color.parseColor("#4400FFFF"))
            }
            setOnClickListener {
                showClean7ItemPlayingQueueSheet()
            }
        }

        bottomFooterRow.addView(tvPocketShakeToggle)
        bottomFooterRow.addView(tvPlayingQueueTrigger)
        mainCol.addView(bottomFooterRow)

        rootFrame.addView(mainCol)
        setContentView(rootFrame)
    }

    override fun onResume() {
        super.onResume()
        MascotCompanionHelper.attachInteractiveMascot(
            context = this,
            hostContainer = rootFrame,
            recyclerView = null,
            isVideoScreen = false,
            isDancingProvider = { sharedAudioPlayer?.isPlaying == true },
            onHoldItemTriggered = {}
        )
    }

    private fun createToolStripButton(
        iconType: String,
        label: String,
        d: Float,
        tintColor: Int,
        onClick: () -> Unit
    ): Pair<LinearLayout, TextView> {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding((8 * d).toInt(), (6 * d).toInt(), (8 * d).toInt(), (6 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { onClick() }
        }

        val icon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((18 * d).toInt(), (18 * d).toInt()).apply {
                marginEnd = (5 * d).toInt()
            }
            setImageDrawable(AudioVectorIconDrawable(iconType, d, tintColor))
        }

        val tv = TextView(this).apply {
            text = label
            setTextColor(tintColor)
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        }

        box.addView(icon)
        box.addView(tv)
        return Pair(box, tv)
    }

    private fun updateCenterArtworkStyleVisibility() {
        if (useVinylDiscMode) {
            cardSquarePhotoBox.visibility = View.GONE
            vinylDiscContainer.visibility = View.VISIBLE
            toneArmView.visibility = View.VISIBLE
            if (sharedAudioPlayer?.isPlaying == true) {
                if (vinylAnimator?.isStarted == true) vinylAnimator?.resume() else vinylAnimator?.start()
            }
        } else {
            vinylAnimator?.pause()
            vinylDiscContainer.visibility = View.GONE
            toneArmView.visibility = View.GONE
            cardSquarePhotoBox.visibility = View.VISIBLE
        }
    }

    private fun updateSeekbarStyleVisibility() {
        if (useWaveformSeek) {
            waveformSeekView.visibility = View.VISIBLE
            simpleSeekBar.visibility = View.GONE
        } else {
            waveformSeekView.visibility = View.GONE
            simpleSeekBar.visibility = View.VISIBLE
        }
    }

    private fun updateShuffleAndRepeatIcons() {
        val d = resources.displayMetrics.density
        val activeCyan = Color.parseColor("#00FFFF")
        val inactiveGray = Color.parseColor("#7F92A8")

        btnShuffleMode.setImageDrawable(
            AudioVectorIconDrawable(
                "SHUFFLE_ARROWS",
                d,
                if (isShuffleEnabled) activeCyan else inactiveGray
            )
        )

        val repeatIconType = when (repeatModeState) {
            Player.REPEAT_MODE_ONE -> "REPEAT_ONE_ICON"
            Player.REPEAT_MODE_OFF -> "REPEAT_OFF_ICON"
            else -> "REPEAT_ALL_ICON"
        }
        val repeatColor = if (repeatModeState == Player.REPEAT_MODE_OFF) inactiveGray else activeCyan
        btnRepeatMode.setImageDrawable(AudioVectorIconDrawable(repeatIconType, d, repeatColor))
    }

    private fun updatePocketShakeUi(enabled: Boolean) {
        tvPocketShakeToggle.text = if (enabled) "📳  Pocket Shake: ON" else "📳  Pocket Shake: OFF"
        tvPocketShakeToggle.setTextColor(
            if (enabled) Color.parseColor("#00FFFF") else Color.parseColor("#B0C4DE")
        )
    }

    private fun extractIntentPlaylist(incoming: Intent?) {
        if (incoming == null) return
        val path = incoming.getStringExtra("AUDIO_PATH") ?: ""
        val title = incoming.getStringExtra("AUDIO_TITLE") ?: File(path).name.ifEmpty { "Audio Track" }
        val artist = incoming.getStringExtra("AUDIO_ARTIST") ?: "Hybrid Player Music"

        val passedPaths = incoming.getStringArrayListExtra("AUDIO_LIST")
        val passedTitles = incoming.getStringArrayListExtra("AUDIO_TITLES")
        val passedArtists = incoming.getStringArrayListExtra("AUDIO_ARTISTS")

        if (!passedPaths.isNullOrEmpty()) {
            songPaths.clear()
            songTitles.clear()
            songArtists.clear()
            songPaths.addAll(passedPaths)

            for (i in passedPaths.indices) {
                songTitles.add(passedTitles?.getOrNull(i) ?: File(passedPaths[i]).name)
                songArtists.add(passedArtists?.getOrNull(i) ?: artist)
            }
            currentIndex = incoming.getIntExtra("START_INDEX", songPaths.indexOf(path).coerceAtLeast(0))
                .coerceIn(0, (songPaths.size - 1).coerceAtLeast(0))
        } else if (path.isNotEmpty() && songPaths.isEmpty()) {
            songPaths.add(path)
            songTitles.add(title)
            songArtists.add(artist)
            currentIndex = 0
        }
    }

    private fun setupPocketShakeSensor() {
        val prefs = getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("pocket_shake_enabled", false)
        pocketShakeDetector = PocketShakeDetector(this) {
            runOnUiThread {
                playNextSong()
                Toast.makeText(this, "Double-Shake -> Next Song ⏭️", Toast.LENGTH_SHORT).show()
            }
        }
        pocketShakeDetector?.setEnabled(enabled)
        updatePocketShakeUi(enabled)
    }

    private fun setupSmartSleepTimerHelper() {
        smartIdlePauseHelper = SmartIdlePauseHelper(
            context = this,
            playerProvider = { sharedAudioPlayer },
            onTickLabelUpdate = { label ->
                tvToolTimerLabel.text = label
                tvToolTimerLabel.setTextColor(
                    if (label == "Timer") Color.WHITE else Color.parseColor("#00FFFF")
                )
            }
        )
    }

    private fun initOrAttachAudioPlayer(forceReload: Boolean = false) {
        if (songPaths.isEmpty()) return
        val targetPath = songPaths[currentIndex.coerceIn(0, songPaths.size - 1)]

        if (sharedAudioPlayer == null) {
            sharedAudioPlayer = ExoPlayer.Builder(this).build()
        }

        val player = sharedAudioPlayer!!
        setupLoudnessEnhancer(player.audioSessionId)

        mediaSessionManager?.release()
        mediaSessionManager = MediaSessionManager(
            context = this,
            playerProvider = { sharedAudioPlayer },
            onNextTrack = { playNextSong() },
            onPrevTrack = { playPreviousSong() },
            onPlayStateChanged = {
                refreshPlayPauseIcon()
                syncNotificationAndLockScreen()
            }
        ).also { it.requestAudioFocusAndRegister() }

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                refreshPlayPauseIcon()
                if (useVinylDiscMode) {
                    if (isPlaying) {
                        if (vinylAnimator?.isStarted == true) vinylAnimator?.resume() else vinylAnimator?.start()
                    } else {
                        vinylAnimator?.pause()
                    }
                }
                mediaSessionManager?.updatePlaybackState()
                syncNotificationAndLockScreen()
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                setupLoudnessEnhancer(audioSessionId)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val idx = player.currentMediaItemIndex
                if (idx in songPaths.indices) {
                    currentIndex = idx
                    currentPlayingPath = songPaths[idx]
                    pointAMs = -1L
                    pointBMs = -1L
                    tvToolAbLabel.text = "A-B"
                    refreshUiForCurrentSong()
                    syncNotificationAndLockScreen()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    tvTotalTime.text = formatTimeMs(player.duration.coerceAtLeast(1L))
                    syncNotificationAndLockScreen()
                }
            }
        })

        if (forceReload || currentPlayingPath != targetPath || player.mediaItemCount != songPaths.size) {
            val items = songPaths.map { p ->
                val uri = if (p.startsWith("content://") || p.startsWith("http")) Uri.parse(p) else Uri.fromFile(File(p))
                MediaItem.fromUri(uri)
            }
            player.setMediaItems(items, currentIndex, 0L)
            player.repeatMode = repeatModeState
            player.shuffleModeEnabled = isShuffleEnabled
            player.prepare()
            player.playWhenReady = true
            currentPlayingPath = targetPath
        }

        refreshUiForCurrentSong()
        handler.removeCallbacks(progressRunnable)
        handler.post(progressRunnable)
    }

    private fun refreshUiForCurrentSong() {
        if (songPaths.isEmpty()) return
        val idx = currentIndex.coerceIn(0, songPaths.size - 1)
        val path = songPaths[idx]
        val title = songTitles.getOrElse(idx) { File(path).name }.substringBeforeLast('.')
        val artist = songArtists.getOrElse(idx) { "Hybrid Player Audio" }

        tvTopSongTitle.text = title
        tvTopArtistName.text = artist
        tvPlayingQueueTrigger.text = "🎵  Playing Queue (${idx + 1}/${songPaths.size})"

        loadSongPhotoIntoCenterArtwork(path, title)
        refreshPlayPauseIcon()
    }

    private fun loadSongPhotoIntoCenterArtwork(songPath: String, songTitle: String) {
        val prefs = getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
        val customUriStr = prefs.getString("custom_photo_$songPath", null)
            ?: prefs.getString("custom_global_audio_photo", null)

        if (!customUriStr.isNullOrEmpty()) {
            try {
                val uri = Uri.parse(customUriStr)
                contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        imgSquareArtPhoto.scaleType = ImageView.ScaleType.CENTER_CROP
                        imgSquareArtPhoto.setImageBitmap(bmp)
                        imgVinylCenterArt.scaleType = ImageView.ScaleType.CENTER_CROP
                        imgVinylCenterArt.setImageBitmap(bmp)
                        return
                    }
                }
            } catch (_: Exception) {
            }
        }

        AudioAdapter.loadAlbumArtOrFallbackInto(this, songPath, songTitle, imgSquareArtPhoto)
        AudioAdapter.loadAlbumArtOrFallbackInto(this, songPath, songTitle, imgVinylCenterArt)
    }

    private fun refreshPlayPauseIcon() {
        val d = resources.displayMetrics.density
        val accentColor = Color.parseColor(ThemeStudioHelper.getCurrentTheme(this).accentHex)
        val isPlaying = sharedAudioPlayer?.isPlaying == true
        btnPlayPauseCircle.setImageDrawable(
            AudioVectorIconDrawable(
                if (isPlaying) "PAUSE_CIRCLE" else "PLAY_CIRCLE",
                d,
                accentColor
            )
        )
    }

    private fun togglePlayPause() {
        sharedAudioPlayer?.let { p ->
            if (p.isPlaying) p.pause() else p.play()
        }
        refreshPlayPauseIcon()
        syncNotificationAndLockScreen()
    }

    private fun playPreviousSong() {
        sharedAudioPlayer?.let { p ->
            if (p.hasPreviousMediaItem()) {
                p.seekToPreviousMediaItem()
                p.play()
            } else {
                p.seekTo(0L)
                p.play()
            }
        }
    }

    private fun playNextSong() {
        sharedAudioPlayer?.let { p ->
            val nextOverride = getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
                .getString("play_next_audio_path", null)
            if (!nextOverride.isNullOrEmpty()) {
                getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
                    .edit()
                    .remove("play_next_audio_path")
                    .apply()
                val targetIdx = songPaths.indexOf(nextOverride)
                if (targetIdx != -1) {
                    p.seekToDefaultPosition(targetIdx)
                    p.play()
                    return
                }
            }

            if (p.hasNextMediaItem()) {
                p.seekToNextMediaItem()
                p.play()
            } else if (songPaths.isNotEmpty()) {
                p.seekToDefaultPosition(0)
                p.play()
            }
        }
    }

    private fun syncNotificationAndLockScreen() {
        if (songPaths.isEmpty()) return
        val idx = currentIndex.coerceIn(0, songPaths.size - 1)
        val path = songPaths[idx]
        val title = songTitles.getOrElse(idx) { File(path).name }.substringBeforeLast('.')
        val artist = songArtists.getOrElse(idx) { "Hybrid Player Audio" }
        val player = sharedAudioPlayer ?: return
        val dur = player.duration.coerceAtLeast(1000L)
        val pos = player.currentPosition.coerceAtLeast(0L)

        mediaSessionManager?.updateMetadataAndState(
            title = title,
            artist = artist,
            durationMs = dur
        )

        BackgroundAudioService.updateForegroundNotification(
            context = this,
            title = title,
            artist = artist,
            isPlaying = player.isPlaying,
            isVideoBackground = false,
            videoPath = path,
            positionMs = pos,
            durationMs = dur
        )
    }

    private fun setupLoudnessEnhancer(sessionId: Int) {
        if (sessionId == 0 || sessionId == C.AUDIO_SESSION_ID_UNSET) return
        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = LoudnessEnhancer(sessionId).apply { enabled = true }
        } catch (_: Exception) {
        }
    }

    private fun showClean7ItemPlayingQueueSheet() {
        val d = resources.displayMetrics.density
        val theme = ThemeStudioHelper.getCurrentTheme(this)
        val accentColor = Color.parseColor(theme.accentHex)

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt(), (14 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F50A0A10"))
                cornerRadius = 24f * d
                setStroke((1.5f * d).toInt(), accentColor)
            }
            layoutParams = ViewGroup.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.94f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((4 * d).toInt(), 0, (4 * d).toInt(), (12 * d).toInt())
        }

        val tvHeader = TextView(this).apply {
            text = "🎵  Playing Queue (${currentIndex + 1}/${songPaths.size})"
            setTextColor(accentColor)
            textSize = 16.5f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnClose = TextView(this).apply {
            text = "X"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((32 * d).toInt(), (32 * d).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#26FFFFFF"))
            }
            setOnClickListener { dialog.dismiss() }
        }

        headerRow.addView(tvHeader)
        headerRow.addView(btnClose)
        card.addView(headerRow)

        val rvQueue = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (435 * d).toInt()
            )
            layoutManager = LinearLayoutManager(this@AudioPlayerActivity)
            setHasFixedSize(true)
        }

        rvQueue.adapter = object : RecyclerView.Adapter<QueueSongVH>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueSongVH {
                val row = LinearLayout(this@AudioPlayerActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((8 * d).toInt(), (7 * d).toInt(), (8 * d).toInt(), (7 * d).toInt())
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (58 * d).toInt()
                    )
                }

                val imgArt = ImageView(this@AudioPlayerActivity).apply {
                    layoutParams = LinearLayout.LayoutParams((42 * d).toInt(), (42 * d).toInt()).apply {
                        marginEnd = (12 * d).toInt()
                    }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }

                val textCol = LinearLayout(this@AudioPlayerActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                val tvTitle = TextView(this@AudioPlayerActivity).apply {
                    textSize = 13.5f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }

                val tvSub = TextView(this@AudioPlayerActivity).apply {
                    textSize = 11.5f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, (2 * d).toInt(), 0, 0)
                }

                val btnRemove = TextView(this@AudioPlayerActivity).apply {
                    text = "X"
                    setTextColor(Color.parseColor("#99B0C4DE"))
                    textSize = 13f
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams((32 * d).toInt(), (32 * d).toInt())
                }

                textCol.addView(tvTitle)
                textCol.addView(tvSub)
                row.addView(imgArt)
                row.addView(textCol)
                row.addView(btnRemove)
                return QueueSongVH(row, imgArt, tvTitle, tvSub, btnRemove)
            }

            override fun onBindViewHolder(holder: QueueSongVH, position: Int) {
                val path = songPaths[position]
                val rawTitle = songTitles.getOrElse(position) { File(path).name }.substringBeforeLast('.')
                val artist = songArtists.getOrElse(position) { "Hybrid Player Audio" }
                val isCurrent = position == currentIndex

                holder.tvTitle.text = "${position + 1}. $rawTitle"
                holder.tvTitle.setTextColor(if (isCurrent) accentColor else Color.WHITE)
                holder.tvSub.text = if (isCurrent) "▶ Now Playing • $artist" else artist
                holder.tvSub.setTextColor(if (isCurrent) accentColor else Color.parseColor("#99B0C4DE"))

                AudioAdapter.loadAlbumArtOrFallbackInto(
                    this@AudioPlayerActivity,
                    path,
                    rawTitle,
                    holder.imgArt
                )

                holder.itemView.setOnClickListener {
                    val pos = holder.adapterPosition
                    if (pos in songPaths.indices) {
                        currentIndex = pos
                        sharedAudioPlayer?.seekToDefaultPosition(pos)
                        sharedAudioPlayer?.play()
                        notifyDataSetChanged()
                        tvHeader.text = "🎵  Playing Queue (${currentIndex + 1}/${songPaths.size})"
                        refreshUiForCurrentSong()
                    }
                }

                holder.btnRemove.setOnClickListener {
                    val pos = holder.adapterPosition
                    if (pos !in songPaths.indices || songPaths.size <= 1) return@setOnClickListener
                    songPaths.removeAt(pos)
                    songTitles.removeAt(pos)
                    songArtists.removeAt(pos)
                    sharedAudioPlayer?.removeMediaItem(pos)
                    if (pos < currentIndex) currentIndex = (currentIndex - 1).coerceAtLeast(0)
                    notifyItemRemoved(pos)
                    notifyItemRangeChanged(pos, songPaths.size)
                    tvHeader.text = "🎵  Playing Queue (${currentIndex + 1}/${songPaths.size})"
                    refreshUiForCurrentSong()
                }
            }

            override fun getItemCount(): Int = songPaths.size
        }

        card.addView(rvQueue)
        rvQueue.scrollToPosition((currentIndex - 2).coerceAtLeast(0))

        dialog.setContentView(card)
        dialog.show()
    }

    private class QueueSongVH(
        view: View,
        val imgArt: ImageView,
        val tvTitle: TextView,
        val tvSub: TextView,
        val btnRemove: TextView
    ) : RecyclerView.ViewHolder(view)

    private fun showSwipeableSpeedRulerDialog() {
        val d = resources.displayMetrics.density
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((20 * d).toInt(), (18 * d).toInt(), (20 * d).toInt(), (18 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F50A0A10"))
                cornerRadius = 22f * d
                setStroke((1.5f * d).toInt(), Color.parseColor("#00FFFF"))
            }
            layoutParams = ViewGroup.LayoutParams((320 * d).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val tvSpeedHeader = TextView(this).apply {
            text = String.format(Locale.US, "⚡  Playback Speed: %.2fx", currentPlaybackSpeed)
            setTextColor(Color.parseColor("#00FFFF"))
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (10 * d).toInt())
        }
        card.addView(tvSpeedHeader)

        val rulerView = AudioSpeedSwipeRulerView(this, d, currentPlaybackSpeed) { newSpeed ->
            currentPlaybackSpeed = newSpeed
            sharedAudioPlayer?.playbackParameters = PlaybackParameters(currentPlaybackSpeed)
            tvSpeedHeader.text = String.format(Locale.US, "⚡  Playback Speed: %.2fx", currentPlaybackSpeed)
            tvToolSpeedLabel.text = String.format(Locale.US, "%.1fx", currentPlaybackSpeed)
            tvToolSpeedLabel.setTextColor(
                if (abs(currentPlaybackSpeed - 1.0f) < 0.04f) Color.WHITE else Color.parseColor("#00FFFF")
            )
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (64 * d).toInt()
            )
        }
        card.addView(rulerView)

        val quickChipsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, (12 * d).toInt(), 0, (4 * d).toInt())
        }

        val presets = floatArrayOf(0.5f, 0.8f, 1.0f, 1.25f, 1.5f, 2.0f)
        for (sp in presets) {
            val chip = TextView(this).apply {
                text = "${sp}x"
                setTextColor(Color.parseColor("#0A0A10"))
                textSize = 11.5f
                typeface = Typeface.DEFAULT_BOLD
                setPadding((10 * d).toInt(), (6 * d).toInt(), (10 * d).toInt(), (6 * d).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins((3 * d).toInt(), 0, (3 * d).toInt(), 0) }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#00FFFF"))
                    cornerRadius = 12f * d
                }
                setOnClickListener {
                    rulerView.updateSpeed(sp)
                }
            }
            quickChipsRow.addView(chip)
        }
        card.addView(quickChipsRow)

        dialog.setContentView(card)
        dialog.show()
    }

    private fun openSmartSleepTimerDialog() {
        SmartSleepTimerDialog.showSleepTimerPicker(
            context = this,
            onStopTimer = {
                smartIdlePauseHelper?.cancelTimer()
                tvToolTimerLabel.text = "Timer"
                tvToolTimerLabel.setTextColor(Color.WHITE)
            },
            onStartTimerMinutes = { mins, motionMode ->
                smartIdlePauseHelper?.startSmoothFadeSleepTimer(mins, motionMode)
            }
        )
    }

    private fun cycleAbRepeatPoints() {
        val cur = sharedAudioPlayer?.currentPosition ?: 0L
        when {
            pointAMs == -1L -> {
                pointAMs = cur
                tvToolAbLabel.text = "A Set"
                tvToolAbLabel.setTextColor(Color.parseColor("#00FFFF"))
                Toast.makeText(this, "Point A set at ${formatTimeMs(pointAMs)}", Toast.LENGTH_SHORT).show()
            }
            pointBMs == -1L -> {
                if (cur > pointAMs) {
                    pointBMs = cur
                    tvToolAbLabel.text = "A-B ON"
                    tvToolAbLabel.setTextColor(Color.parseColor("#00E676"))
                    Toast.makeText(this, "Looping A to B", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Point B must be after Point A", Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                pointAMs = -1L
                pointBMs = -1L
                tvToolAbLabel.text = "A-B"
                tvToolAbLabel.setTextColor(Color.WHITE)
                Toast.makeText(this, "A-B Repeat Cleared", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAudioThreeDotsOptionsDialog() {
        val prefs = getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
        val idx = currentIndex.coerceIn(0, (songPaths.size - 1).coerceAtLeast(0))
        val curPath = songPaths.getOrElse(idx) { "" }
        val curTitle = songTitles.getOrElse(idx) { "Song" }

        val options = arrayOf(
            "🖼️   Set Custom Photo from Gallery (Save Permanently)",
            if (useVinylDiscMode) "🟩   Center Style: Switch to Square Album Photo Box (Default)"
            else "💿   Center Style: Switch to Rotating Vinyl Disc",
            if (useWaveformSeek) "📏   Seekbar Style: Switch to Simple Straight Line"
            else "📊   Seekbar Style: Switch to Animated Waveform Bars",
            "🎛️   Open Sound & Pitch Lab (Lofi / Slowed + Reverb)",
            "🎚️   Open 5-Band Dolby Equalizer & Bass Boost",
            "ℹ️   Song Details & File Path",
            "🗑️   Delete Song Permanently (From Phone)"
        )

        VideoFragment.showBrightVideoMenuDialog(this, "Audio Player Options", options) { which ->
            when (which) {
                0 -> {
                    try {
                        val pickIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "image/*"
                        }
                        startActivityForResult(pickIntent, REQ_PICK_CUSTOM_PHOTO)
                    } catch (_: Exception) {
                        Toast.makeText(this, "Unable to open gallery", Toast.LENGTH_SHORT).show()
                    }
                }
                1 -> {
                    useVinylDiscMode = !useVinylDiscMode
                    prefs.edit().putBoolean("audio_use_vinyl_disc", useVinylDiscMode).apply()
                    updateCenterArtworkStyleVisibility()
                }
                2 -> {
                    useWaveformSeek = !useWaveformSeek
                    prefs.edit().putBoolean("audio_use_waveform_seek", useWaveformSeek).apply()
                    updateSeekbarStyleVisibility()
                }
                3 -> AudioPitchHelper.showSoundAndPitchLabDialog(this, sharedAudioPlayer)
                4 -> EqualizerHelper.showEqualizerDialog(this, sharedAudioPlayer?.audioSessionId ?: 0)
                5 -> {
                    AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Song Details & Info")
                        .setMessage("Title: $curTitle\n\nLocation: $curPath")
                        .setPositiveButton("OK", null)
                        .show()
                }
                6 -> {
                    if (curPath.isNotEmpty()) {
                        PermanentDeleteHelper.confirmAndDeletePermanently(
                            context = this,
                            title = curTitle,
                            path = curPath,
                            isVideo = false
                        ) {
                            finish()
                        }
                    }
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_CUSTOM_PHOTO && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            val curPath = songPaths.getOrElse(currentIndex) { "" }
            getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
                .edit()
                .putString("custom_photo_$curPath", uri.toString())
                .apply()
            refreshUiForCurrentSong()
            Toast.makeText(this, "Custom Album Photo Saved!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareCurrentSong() {
        val curPath = songPaths.getOrElse(currentIndex) { "" }
        if (curPath.isEmpty()) return
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, Uri.parse(curPath))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Song"))
        } catch (_: Exception) {
            Toast.makeText(this, "Unable to share song", Toast.LENGTH_SHORT).show()
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

    private fun registerNotifReceiver() {
        if (isReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BackgroundAudioService.ACTION_NOTIF_PREV)
            addAction(BackgroundAudioService.ACTION_NOTIF_PLAY_PAUSE)
            addAction(BackgroundAudioService.ACTION_NOTIF_NEXT)
            addAction(BackgroundAudioService.ACTION_NOTIF_SEEK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notifCommandReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(notifCommandReceiver, filter)
        }
        isReceiverRegistered = true
    }

    private fun formatTimeMs(millis: Long): String {
        val safe = millis.coerceAtLeast(0L)
        val mins = TimeUnit.MILLISECONDS.toMinutes(safe)
        val secs = TimeUnit.MILLISECONDS.toSeconds(safe) % 60
        return String.format(Locale.US, "%d:%02d", mins, secs)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(progressRunnable)
        vinylAnimator?.cancel()
        pocketShakeDetector?.release()
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(notifCommandReceiver)
            } catch (_: Exception) {
            }
            isReceiverRegistered = false
        }
    }

    private class WaveformSeekViewInternal(
        context: Context,
        private val d: Float,
        private val activeColor: Int,
        private val onUserSeek: (Float) -> Unit
    ) : View(context) {

        private var progress = 0f
        private val playedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = activeColor
            strokeWidth = 3.2f * d
            strokeCap = Paint.Cap.ROUND
        }
        private val unplayedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38FFFFFF")
            strokeWidth = 3.2f * d
            strokeCap = Paint.Cap.ROUND
        }

        fun setProgressFraction(fraction: Float) {
            progress = fraction.coerceIn(0f, 1f)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val barsCount = 46
            val stepX = w / barsCount.toFloat()
            val cy = h / 2f

            for (i in 0 until barsCount) {
                val frac = i.toFloat() / barsCount.toFloat()
                val x = (i + 0.5f) * stepX
                val waveFactor = 0.25f + 0.70f * abs(sin(i * 0.45f))
                val barHalfH = (h * 0.44f * waveFactor).coerceAtLeast(4f * d)
                val paint = if (frac <= progress) playedPaint else unplayedPaint
                canvas.drawLine(x, cy - barHalfH, x, cy + barHalfH, paint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                    val frac = (event.x / width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                    progress = frac
                    invalidate()
                    onUserSeek(frac)
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }

    private class AudioSpeedSwipeRulerView(
        context: Context,
        private val d: Float,
        initialSpeed: Float,
        private val onSpeedChanged: (Float) -> Unit
    ) : View(context) {

        private var speed = initialSpeed.coerceIn(0.25f, 4.0f)
        private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#88FFFFFF")
            strokeWidth = 1.6f * d
            strokeCap = Paint.Cap.ROUND
        }
        private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00FFFF")
            strokeWidth = 2.8f * d
            strokeCap = Paint.Cap.ROUND
        }
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 10.5f * d
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        fun updateSpeed(newVal: Float) {
            speed = newVal.coerceIn(0.25f, 4.0f)
            invalidate()
            onSpeedChanged(speed)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val pad = 16f * d
            val usableW = (w - 2 * pad).coerceAtLeast(1f)
            val ticks = 30

            for (i in 0..ticks) {
                val f = i.toFloat() / ticks.toFloat()
                val x = pad + f * usableW
                val major = i % 5 == 0
                canvas.drawLine(
                    x,
                    if (major) 6f * d else 12f * d,
                    x,
                    h - 22f * d,
                    tickPaint
                )
            }

            val marks = listOf(0.5f to "0.5x", 1.0f to "1.0x", 2.0f to "2.0x", 3.0f to "3.0x", 4.0f to "4.0x")
            for ((v, lbl) in marks) {
                val f = ((v - 0.25f) / 3.75f).coerceIn(0f, 1f)
                canvas.drawText(lbl, pad + f * usableW, h - 4f * d, labelPaint)
            }

            val curF = ((speed - 0.25f) / 3.75f).coerceIn(0f, 1f)
            val curX = pad + curF * usableW
            canvas.drawLine(curX, 2f * d, curX, h - 18f * d, needlePaint)
            canvas.drawCircle(curX, 5f * d, 4f * d, needlePaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                    val pad = 16f * d
                    val usableW = (width.toFloat() - 2 * pad).coerceAtLeast(1f)
                    val f = ((event.x - pad) / usableW).coerceIn(0f, 1f)
                    val raw = 0.25f + f * 3.75f
                    val rounded = (raw * 20f).roundToInt() / 20f
                    updateSpeed(rounded)
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }

    private class VinylGroovesDrawable(
        private val d: Float,
        private val accentColor: Int
    ) : Drawable() {
        private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#14121D")
        }
        private val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.1f * d
            color = Color.parseColor("#28FFFFFF")
        }
        private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.0f * d
            color = accentColor
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val r = b.width().coerceAtMost(b.height()) * 0.48f
            canvas.drawCircle(cx, cy, r, discPaint)
            canvas.drawCircle(cx, cy, r, rimPaint)
            for (i in 1..6) {
                canvas.drawCircle(cx, cy, r * (0.52f + i * 0.07f), groovePaint)
            }
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private class AudioVectorIconDrawable(
        private val type: String,
        private val d: Float,
        private val iconColor: Int
    ) : Drawable() {

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.1f * d
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = iconColor
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = iconColor
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = iconColor
            textSize = 9.5f * d
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val r = b.width().coerceAtMost(b.height()) * 0.42f

            when (type) {
                "CHEVRON_DOWN" -> {
                    val p = Path().apply {
                        moveTo(cx - r * 0.55f, cy - r * 0.22f)
                        lineTo(cx, cy + r * 0.32f)
                        lineTo(cx + r * 0.55f, cy - r * 0.22f)
                    }
                    canvas.drawPath(p, strokePaint)
                }
                "SHARE_NODES" -> {
                    val leftX = cx - r * 0.45f
                    val rightX = cx + r * 0.45f
                    val topY = cy - r * 0.45f
                    val botY = cy + r * 0.45f
                    canvas.drawLine(leftX, cy, rightX, topY, strokePaint)
                    canvas.drawLine(leftX, cy, rightX, botY, strokePaint)
                    canvas.drawCircle(leftX, cy, 3f * d, fillPaint)
                    canvas.drawCircle(rightX, topY, 3f * d, fillPaint)
                    canvas.drawCircle(rightX, botY, 3f * d, fillPaint)
                }
                "EQ_SLIDERS" -> {
                    canvas.drawLine(cx - r * 0.55f, cy - r * 0.65f, cx - r * 0.55f, cy + r * 0.65f, strokePaint)
                    canvas.drawLine(cx, cy - r * 0.65f, cx, cy + r * 0.65f, strokePaint)
                    canvas.drawLine(cx + r * 0.55f, cy - r * 0.65f, cx + r * 0.55f, cy + r * 0.65f, strokePaint)
                    canvas.drawCircle(cx - r * 0.55f, cy - r * 0.20f, 2.5f * d, fillPaint)
                    canvas.drawCircle(cx, cy + r * 0.25f, 2.5f * d, fillPaint)
                    canvas.drawCircle(cx + r * 0.55f, cy - r * 0.30f, 2.5f * d, fillPaint)
                }
                "AB_LOOP" -> {
                    val rect = RectF(cx - r * 0.75f, cy - r * 0.48f, cx + r * 0.75f, cy + r * 0.48f)
                    canvas.drawRoundRect(rect, 4f * d, 4f * d, strokePaint)
                }
                "SPEED_GAUGE" -> {
                    val arc = RectF(cx - r * 0.75f, cy - r * 0.65f, cx + r * 0.75f, cy + r * 0.75f)
                    canvas.drawArc(arc, 160f, 220f, false, strokePaint)
                    canvas.drawLine(cx, cy + r * 0.1f, cx + r * 0.42f, cy - r * 0.35f, strokePaint)
                }
                "TIMER_CLOCK" -> {
                    canvas.drawCircle(cx, cy, r * 0.78f, strokePaint)
                    canvas.drawLine(cx, cy, cx, cy - r * 0.45f, strokePaint)
                    canvas.drawLine(cx, cy, cx + r * 0.35f, cy, strokePaint)
                }
                "THREE_DOTS_CIRCLE" -> {
                    canvas.drawCircle(cx, cy, r * 0.95f, strokePaint)
                    canvas.drawCircle(cx, cy - r * 0.42f, 2.0f * d, fillPaint)
                    canvas.drawCircle(cx, cy, 2.0f * d, fillPaint)
                    canvas.drawCircle(cx, cy + r * 0.42f, 2.0f * d, fillPaint)
                }
                "SHUFFLE_ARROWS" -> {
                    canvas.drawLine(cx - r * 0.65f, cy - r * 0.40f, cx + r * 0.65f, cy + r * 0.40f, strokePaint)
                    canvas.drawLine(cx - r * 0.65f, cy + r * 0.40f, cx + r * 0.65f, cy - r * 0.40f, strokePaint)
                }
                "REPEAT_ALL_ICON", "REPEAT_ONE_ICON", "REPEAT_OFF_ICON" -> {
                    val rect = RectF(cx - r * 0.70f, cy - r * 0.48f, cx + r * 0.70f, cy + r * 0.48f)
                    canvas.drawRoundRect(rect, 6f * d, 6f * d, strokePaint)
                    if (type == "REPEAT_ONE_ICON") {
                        canvas.drawText("1", cx, cy + 3.5f * d, textPaint)
                    } else if (type == "REPEAT_OFF_ICON") {
                        canvas.drawLine(cx - r * 0.65f, cy + r * 0.65f, cx + r * 0.65f, cy - r * 0.65f, strokePaint)
                    }
                }
                "PREV_TRACK" -> {
                    canvas.drawLine(cx - r * 0.50f, cy - r * 0.48f, cx - r * 0.50f, cy + r * 0.48f, strokePaint)
                    val p = Path().apply {
                        moveTo(cx + r * 0.50f, cy - r * 0.48f)
                        lineTo(cx - r * 0.35f, cy)
                        lineTo(cx + r * 0.50f, cy + r * 0.48f)
                        close()
                    }
                    canvas.drawPath(p, fillPaint)
                }
                "NEXT_TRACK" -> {
                    canvas.drawLine(cx + r * 0.50f, cy - r * 0.48f, cx + r * 0.50f, cy + r * 0.48f, strokePaint)
                    val p = Path().apply {
                        moveTo(cx - r * 0.50f, cy - r * 0.48f)
                        lineTo(cx + r * 0.35f, cy)
                        lineTo(cx - r * 0.50f, cy + r * 0.48f)
                        close()
                    }
                    canvas.drawPath(p, fillPaint)
                }
                "PLAY_CIRCLE" -> {
                    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        color = Color.parseColor("#2200FFFF")
                    }
                    canvas.drawCircle(cx, cy, r * 1.08f, glowPaint)
                    canvas.drawCircle(cx, cy, r * 0.96f, strokePaint)
                    val p = Path().apply {
                        moveTo(cx - r * 0.25f, cy - r * 0.40f)
                        lineTo(cx + r * 0.45f, cy)
                        lineTo(cx - r * 0.25f, cy + r * 0.40f)
                        close()
                    }
                    val whiteFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        color = Color.WHITE
                    }
                    canvas.drawPath(p, whiteFill)
                }
                "PAUSE_CIRCLE" -> {
                    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        color = Color.parseColor("#2200FFFF")
                    }
                    canvas.drawCircle(cx, cy, r * 1.08f, glowPaint)
                    canvas.drawCircle(cx, cy, r * 0.96f, strokePaint)
                    val whiteFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        color = Color.WHITE
                    }
                    val bw = r * 0.22f
                    val bh = r * 0.72f
                    canvas.drawRoundRect(
                        RectF(cx - r * 0.32f, cy - bh / 2f, cx - r * 0.32f + bw, cy + bh / 2f),
                        2f * d,
                        2f * d,
                        whiteFill
                    )
                    canvas.drawRoundRect(
                        RectF(cx + r * 0.10f, cy - bh / 2f, cx + r * 0.10f + bw, cy + bh / 2f),
                        2f * d,
                        2f * d,
                        whiteFill
                    )
                }
                "TONE_ARM" -> {
                    canvas.drawCircle(cx - r * 0.45f, cy, 6f * d, fillPaint)
                    canvas.drawLine(cx - r * 0.45f, cy, cx + r * 0.65f, cy - r * 0.25f, strokePaint)
                }
            }
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
