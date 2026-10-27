package com.app.mediaplayer

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
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
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import java.util.ArrayList
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

@OptIn(UnstableApi::class)
class AudioPlayerActivity : AppCompatActivity(), AudioThreeDotMenuHelper.AudioPlayerCallbacks {

    companion object {
        private var sharedExoPlayer: ExoPlayer? = null
        private var sharedLoudnessEnhancer: LoudnessEnhancer? = null
    }

    private lateinit var rootAudioPlayerFrame: FrameLayout
    private lateinit var tvHeaderSubtitle: TextView
    private lateinit var btnTopEqualizer: ImageView
    private lateinit var btnTopThreeDots: ImageView

    private lateinit var coverArtOuterCard: CardView
    private lateinit var imgMainSongCover: ImageView
    private lateinit var vinylCenterHoleView: View

    private lateinit var tvSongTitleMain: TextView
    private lateinit var tvSongArtistMain: TextView
    private lateinit var btnFavoriteHeart: ImageView

    private lateinit var pillSpeedQuick: TextView
    private lateinit var pillBoostQuick: TextView
    private lateinit var pillAbRepeatQuick: TextView
    private lateinit var pillTimerQuick: TextView

    private lateinit var waveformVisualizerView: PureWhiteWaveformView
    private lateinit var seekBarAudio: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView

    private lateinit var btnLoopMode: ImageView
    private lateinit var btnPrevSong: ImageView
    private lateinit var btnPlayPauseSong: ImageView
    private lateinit var btnNextSong: ImageView
    private lateinit var btnOpenQueue: ImageView

    private lateinit var imgUpNextCover: ImageView
    private lateinit var tvUpNextTitle: TextView
    private lateinit var btnBottomThreeDots: ImageView

    private var songPaths = ArrayList<String>()
    private var songTitles = ArrayList<String>()
    private var songArtists = ArrayList<String>()
    private var currentIndex = 0

    private var currentPath = ""
    private var currentTitle = "Hi-Res Audio"
    private var currentArtist = "Studio Artist"

    private var isVinylCircleMode = false
    private var isFavorite = false
    private var isSuperLoud200Boost = true
    private var currentSpeed = 1.0f
    private var loopMode = 2
    private var stopAfterCurrentTrack = false

    private var pointAMs = -1L
    private var pointBMs = -1L

    private var discRotationAnimator: ObjectAnimator? = null
    private var mediaSessionManager: MediaSessionManager? = null
    private var smartIdlePauseHelper: SmartIdlePauseHelper? = null
    private var volumeHudHelper: VolumeHudOverlayHelper? = null
    private lateinit var audioManager: AudioManager
    private var isNotifReceiverRegistered = false

    private val handler = Handler(Looper.getMainLooper())

    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            try {
                val player = sharedExoPlayer
                if (player != null) {
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration.coerceAtLeast(1000L)
                    seekBarAudio.max = dur.toInt()
                    seekBarAudio.progress = pos.toInt()
                    tvCurrentTime.text = formatDurationMs(pos)
                    tvTotalTime.text = formatDurationMs(dur)

                    waveformVisualizerView.setPlayingState(
                        isPlaying = player.isPlaying,
                        progressFraction = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                    )

                    if (pointAMs != -1L && pointBMs != -1L && pos >= pointBMs) {
                        player.seekTo(pointAMs)
                    }
                }
            } catch (_: Exception) {
            }
            handler.postDelayed(this, 250L)
        }
    }

    private val notifSyncReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BackgroundAudioService.ACTION_NOTIF_PREV -> playPreviousSong()
                BackgroundAudioService.ACTION_NOTIF_PLAY_PAUSE -> togglePlayPauseNow()
                BackgroundAudioService.ACTION_NOTIF_NEXT -> playNextSong()
                BackgroundAudioService.ACTION_NOTIF_SEEK -> {
                    val seekMs = intent.getLongExtra("SEEK_POS_MS", 0L)
                    sharedExoPlayer?.seekTo(seekMs)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        buildPureWhiteStudioAudioUi()
        ThemeStudioHelper.applyThemeAndOneHandModeToRoot(this, rootAudioPlayerFrame)

        volumeHudHelper = VolumeHudOverlayHelper(
            context = this,
            rootContainer = rootAudioPlayerFrame,
            audioManager = audioManager,
            loudnessEnhancerProvider = { sharedLoudnessEnhancer },
            playerVolumeSetter = { vol -> sharedExoPlayer?.volume = vol }
        )

        smartIdlePauseHelper = SmartIdlePauseHelper(
            context = this,
            playerProvider = { sharedExoPlayer },
            onTickLabelUpdate = { label ->
                pillTimerQuick.text = if (label == "Timer") "Sleep Timer" else label
            }
        )

        registerNotificationReceiver()
        extractIncomingAudioIntent(intent)
        initializeOrAttachSharedExoPlayer()
        handler.post(progressUpdateRunnable)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            extractIncomingAudioIntent(intent)
            initializeOrAttachSharedExoPlayer()
        }
    }

    override fun onResume() {
        super.onResume()
        ThemeStudioHelper.applyThemeAndOneHandModeToRoot(this, rootAudioPlayerFrame)
        refreshCurrentSongUiAndArt()
    }

    private fun buildPureWhiteStudioAudioUi() {
        val d = resources.displayMetrics.density
        val theme = ThemeStudioHelper.getCurrentTheme(this)

        rootAudioPlayerFrame = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor(theme.bgHex))
        }

        val mainColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding((18 * d).toInt(), (16 * d).toInt(), (18 * d).toInt(), (16 * d).toInt())
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val btnBack = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((48 * d).toInt(), (48 * d).toInt())
            setPadding((11 * d).toInt(), (11 * d).toInt(), (11 * d).toInt(), (11 * d).toInt())
            setImageDrawable(AudioThreeDotMenuHelper.PureWhiteAudioVectorDrawable("BACK_ARROW", d))
            background = AudioThreeDotMenuHelper.makeFrostedWhiteCircle(d)
            setOnClickListener { finish() }
        }

        val headerTitleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (10 * d).toInt()
                marginEnd = (10 * d).toInt()
            }
        }

        val tvHeaderMain = TextView(this).apply {
            text = "HI-RES STUDIO AUDIO"
            setTextColor(Color.WHITE)
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        tvHeaderSubtitle = TextView(this).apply {
            text = "Dolby 200% Boost  •  Swipe Cover for Next"
            setTextColor(Color.parseColor("#D8E2F0"))
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        headerTitleCol.addView(tvHeaderMain)
        headerTitleCol.addView(tvHeaderSubtitle)

        btnTopEqualizer = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((48 * d).toInt(), (48 * d).toInt()).apply {
                marginEnd = (8 * d).toInt()
            }
            setPadding((11 * d).toInt(), (11 * d).toInt(), (11 * d).toInt(), (11 * d).toInt())
            setImageDrawable(AudioThreeDotMenuHelper.PureWhiteAudioVectorDrawable("EQUALIZER", d))
            background = AudioThreeDotMenuHelper.makeFrostedWhiteCircle(d)
            setOnClickListener {
                EqualizerHelper.showEqualizerDialog(this@AudioPlayerActivity, getAudioSessionId())
            }
        }

        btnTopThreeDots = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((48 * d).toInt(), (48 * d).toInt())
            setPadding((11 * d).toInt(), (11 * d).toInt(), (11 * d).toInt(), (11 * d).toInt())
            setImageDrawable(AudioThreeDotMenuHelper.PureWhiteAudioVectorDrawable("THREE_DOTS", d))
            background = AudioThreeDotMenuHelper.makeFrostedWhiteCircle(d)
            setOnClickListener {
                AudioThreeDotMenuHelper.showAudioThreeDotStudioDialog(this@AudioPlayerActivity, this@AudioPlayerActivity)
            }
        }

        topBar.addView(btnBack)
        topBar.addView(headerTitleCol)
        topBar.addView(btnTopEqualizer)
        topBar.addView(btnTopThreeDots)
        mainColumn.addView(topBar)

        val coverWrapperFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val coverSizePx = (256 * d).toInt()
        coverArtOuterCard = CardView(this).apply {
            radius = 28f * d
            cardElevation = 20f * d
            setCardBackgroundColor(Color.parseColor("#162234"))
            layoutParams = FrameLayout.LayoutParams(coverSizePx, coverSizePx, Gravity.CENTER)
        }

        val coverInnerFrame = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        imgMainSongCover = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        vinylCenterHoleView = View(this).apply {
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams((54 * d).toInt(), (54 * d).toInt(), Gravity.CENTER)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#0A0A10"))
                setStroke((4 * d).toInt(), Color.WHITE)
            }
        }

        coverInnerFrame.addView(imgMainSongCover)
        coverInnerFrame.addView(vinylCenterHoleView)
        coverArtOuterCard.addView(coverInnerFrame)
        coverWrapperFrame.addView(coverArtOuterCard)
        attachCoverSwipeAndTapGestures(coverWrapperFrame)
        mainColumn.addView(coverWrapperFrame)

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * d).toInt() }
        }

        val songTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (12 * d).toInt()
            }
        }

        tvSongTitleMain = TextView(this).apply {
            text = "Loading Song..."
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MARQUEE
            isSelected = true
        }

        tvSongArtistMain = TextView(this).apply {
            text = "Hi-Res Studio Audio"
            setTextColor(Color.parseColor("#D8E2F0"))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, (4 * d).toInt(), 0, 0)
        }

        songTextCol.addView(tvSongTitleMain)
        songTextCol.addView(tvSongArtistMain)

        btnFavoriteHeart = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((48 * d).toInt(), (48 * d).toInt())
            setPadding((11 * d).toInt(), (11 * d).toInt(), (11 * d).toInt(), (11 * d).toInt())
            setImageDrawable(AudioThreeDotMenuHelper.PureWhiteAudioVectorDrawable("HEART_OFF", d))
            background = AudioThreeDotMenuHelper.makeFrostedWhiteCircle(d)
            setOnClickListener {
                onToggleFavorite()
            }
        }

        titleRow.addView(songTextCol)
        titleRow.addView(btnFavoriteHeart)
        mainColumn.addView(titleRow)

        val quickPillsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * d).toInt() }
        }

        val quickPillsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        pillSpeedQuick = createPureWhiteQuickPill("1.0x Speed", d) {
            val nextSpeed = when (currentSpeed) {
                1.0f -> 1.25f
                1.25f -> 1.5f
                1.5f -> 2.0f
                2.0f -> 0.8f
                else -> 1.0f
            }
            onChangePlaybackSpeed(nextSpeed)
            Toast.makeText(this, "Speed: ${nextSpeed}x", Toast.LENGTH_SHORT).show()
        }

        pillBoostQuick = createPureWhiteQuickPill("200% Boost: ON", d) {
            onToggleSuperLoudBoost()
        }

        val pillPitchLab = createPureWhiteQuickPill("Lofi / Pitch Lab", d) {
            AudioPitchHelper.showSoundAndPitchLabDialog(this, sharedExoPlayer)
        }

        pillAbRepeatQuick = createPureWhiteQuickPill("AB Repeat", d) {
            onTriggerAbRepeat()
        }

        pillTimerQuick = createPureWhiteQuickPill("Sleep Timer", d) {
            onOpenSleepTimer()
        }

        quickPillsRow.addView(pillSpeedQuick)
        quickPillsRow.addView(pillBoostQuick)
        quickPillsRow.addView(pillPitchLab)
        quickPillsRow.addView(pillAbRepeatQuick)
        quickPillsRow.addView(pillTimerQuick)
        quickPillsScroll.addView(quickPillsRow)
        mainColumn.addView(quickPillsScroll)

        waveformVisualizerView = PureWhiteWaveformView(this, d).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (38 * d).toInt()
            ).apply { bottomMargin = (4 * d).toInt() }
        }
        mainColumn.addView(waveformVisualizerView)

        val seekRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (12 * d).toInt() }
        }

        tvCurrentTime = TextView(this).apply {
            text = "00:00"
            setTextColor(Color.WHITE)
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
        }

        seekBarAudio = SeekBar(this).apply {
            max = 1000
            progressDrawable.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            thumb.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (8 * d).toInt()
                marginEnd = (8 * d).toInt()
            }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) {
                        sharedExoPlayer?.seekTo(p.toLong())
                        tvCurrentTime.text = formatDurationMs(p.toLong())
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {
                    syncNotificationAndSharedState()
                }
            })
        }

        tvTotalTime = TextView(this).apply {
            text = "00:00"
            setTextColor(Color.WHITE)
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
        }

        seekRow.addView(tvCurrentTime)
        seekRow.addView(seekBarAudio)
        seekRow.addView(tvTotalTime)
        mainColumn.addView(seekRow)

        val transportRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (14 * d).toInt() }
        }

        btnLoopMode = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((50 * d).toInt(), (50 * d).toInt()).apply {
                marginEnd = (16 * d).toInt()
            }
            setPadding((12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt())
            setImageDrawable(AudioThreeDotMenuHelper.PureWhiteAudioVectorDrawable("REPEAT_ALL", d))
            background = AudioThreeDotMenuHelper.makeFrostedWhiteCircle(d)
            setOnClickListener {
                onChangeLoopMode((loopMode + 1) % 4)
            }
        }

        btnPrevSong = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((56 * d).toInt(), (56 * d).toInt()).apply {
                marginEnd = (18 * d).toInt()
            }
            setPadding((13 * d).toInt(), (13 * d).toInt(), (13 * d).toInt(), (13 * d).toInt())
            setImageDrawable(AudioThreeDotMenuHelper.PureWhiteAudioVectorDrawable("PREV", d))
            background = AudioThreeDotMenuHelper.makeFrostedWhiteCircle(d)
            setOnClickListener { playPreviousSong() }
        }

        btnPlayPauseSong = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((72 * d).toInt(), (72 * d).toInt()).apply {
                marginEnd = (18 * d).toInt()
            }
            setPadding((17 * d).toInt(), (17 * d).toInt(), (17 * d).toInt(), (17 * d).toInt())
            setImageDrawable(AudioThreeDotMenuHelper.PureWhiteAudioVectorDrawable("PAUSE", d))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#38FFFFFF"))
                setStroke((2 * d).toInt(), Color.WHITE)
            }
            setOnClickListener { togglePlayPauseNow() }
        }

        btnNextSong = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((56 * d).toInt(), (56 * d).toInt()).apply {
                marginEnd = (16 * d).toInt()
            }
            setPadding((13 * d).toInt(), (13 * d).toInt(), (13 * d).toInt(), (13 * d).toInt())
            setImageDrawable(AudioThreeDotMenuHelper.PureWhiteAudioVectorDrawable("NEXT", d))
            background = AudioThreeDotMenuHelper.makeFrostedWhiteCircle(d)
            setOnClickListener { playNextSong() }
        }

        btnOpenQueue = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((50 * d).toInt(), (50 * d).toInt())
            setPadding((12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt())
            setImageDrawable(AudioThreeDotMenuHelper.PureWhiteAudioVectorDrawable("QUEUE_LIST", d))
            background = AudioThreeDotMenuHelper.makeFrostedWhiteCircle(d)
            setOnClickListener { openPlayingQueueSheet() }
        }

        transportRow.addView(btnLoopMode)
        transportRow.addView(btnPrevSong)
        transportRow.addView(btnPlayPauseSong)
        transportRow.addView(btnNextSong)
        transportRow.addView(btnOpenQueue)
        mainColumn.addView(transportRow)

        val bottomDockCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1EFFFFFF"))
                cornerRadius = 20f * d
                setStroke((1.3f * d).toInt(), Color.parseColor("#55FFFFFF"))
            }
        }

        imgUpNextCover = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((42 * d).toInt(), (42 * d).toInt()).apply {
                marginEnd = (12 * d).toInt()
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        tvUpNextTitle = TextView(this).apply {
            text = "Up Next: Queue Ready"
            setTextColor(Color.WHITE)
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (10 * d).toInt()
            }
            setOnClickListener { openPlayingQueueSheet() }
        }

        btnBottomThreeDots = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((44 * d).toInt(), (44 * d).toInt())
            setPadding((10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt())
            setImageDrawable(AudioThreeDotMenuHelper.PureWhiteAudioVectorDrawable("THREE_DOTS", d))
            background = AudioThreeDotMenuHelper.makeFrostedWhiteCircle(d)
            setOnClickListener {
                AudioThreeDotMenuHelper.showAudioThreeDotStudioDialog(this@AudioPlayerActivity, this@AudioPlayerActivity)
            }
        }

        bottomDockCard.addView(imgUpNextCover)
        bottomDockCard.addView(tvUpNextTitle)
        bottomDockCard.addView(btnBottomThreeDots)
        mainColumn.addView(bottomDockCard)

        rootAudioPlayerFrame.addView(mainColumn)
        setContentView(rootAudioPlayerFrame)
    }

    private fun createPureWhiteQuickPill(label: String, d: Float, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((15 * d).toInt(), (8 * d).toInt(), (15 * d).toInt(), (8 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (8 * d).toInt() }
            background = AudioThreeDotMenuHelper.makeFrostedWhitePill(d)
            setOnClickListener { onClick() }
        }
    }

    private fun attachCoverSwipeAndTapGestures(targetView: View) {
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                onToggleVinylPhotoMode()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                togglePlayPauseNow()
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (abs(dx) > abs(dy) * 1.4f && abs(dx) > 90f && abs(velocityX) > 180f) {
                    if (dx < 0) {
                        playNextSong()
                    } else {
                        playPreviousSong()
                    }
                    return true
                }
                return false
            }
        })

        targetView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun extractIncomingAudioIntent(incoming: Intent?) {
        if (incoming == null) return
        val pathExtra = incoming.getStringExtra("AUDIO_PATH")
            ?: incoming.getStringExtra("audio_path")
            ?: incoming.dataString
            ?: ""
        val titleExtra = incoming.getStringExtra("AUDIO_TITLE")
            ?: incoming.getStringExtra("audio_title")
            ?: File(pathExtra).name.ifEmpty { "Studio Audio" }
        val artistExtra = incoming.getStringExtra("AUDIO_ARTIST")
            ?: incoming.getStringExtra("audio_artist")
            ?: "Hi-Res Studio Audio"

        val listPaths = incoming.getStringArrayListExtra("AUDIO_LIST")
        val listTitles = incoming.getStringArrayListExtra("AUDIO_TITLES")
        val listArtists = incoming.getStringArrayListExtra("AUDIO_ARTISTS")
        val passedIndex = incoming.getIntExtra("START_INDEX", -1)

        if (!listPaths.isNullOrEmpty()) {
            songPaths.clear()
            songPaths.addAll(listPaths)
            songTitles.clear()
            if (!listTitles.isNullOrEmpty() && listTitles.size == listPaths.size) {
                songTitles.addAll(listTitles)
            } else {
                songPaths.forEach { songTitles.add(File(it).name) }
            }
            songArtists.clear()
            if (!listArtists.isNullOrEmpty() && listArtists.size == listPaths.size) {
                songArtists.addAll(listArtists)
            } else {
                songPaths.forEach { _ -> songArtists.add(artistExtra) }
            }
            currentIndex = if (passedIndex in songPaths.indices) {
                passedIndex
            } else {
                songPaths.indexOf(pathExtra).coerceAtLeast(0)
            }
        } else if (pathExtra.isNotEmpty()) {
            if (songPaths.isEmpty()) {
                songPaths.add(pathExtra)
                songTitles.add(titleExtra)
                songArtists.add(artistExtra)
                currentIndex = 0
            }
            loadFullDeviceSongsIntoQueueAsync(pathExtra)
        }

        if (currentIndex in songPaths.indices) {
            currentPath = songPaths[currentIndex]
            currentTitle = songTitles.getOrElse(currentIndex) { titleExtra }
            currentArtist = songArtists.getOrElse(currentIndex) { artistExtra }
        } else {
            currentPath = pathExtra
            currentTitle = titleExtra
            currentArtist = artistExtra
        }
    }

    private fun loadFullDeviceSongsIntoQueueAsync(activePath: String) {
        thread {
            val paths = ArrayList<String>()
            val titles = ArrayList<String>()
            val artists = ArrayList<String>()
            try {
                val proj = arrayOf(
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DATA
                )
                contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    proj,
                    null,
                    null,
                    "${MediaStore.Audio.Media.DATE_ADDED} DESC"
                )?.use { c ->
                    val nameCol = c.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                    val titleCol = c.getColumnIndex(MediaStore.Audio.Media.TITLE)
                    val artistCol = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                    val dataCol = c.getColumnIndex(MediaStore.Audio.Media.DATA)
                    while (c.moveToNext()) {
                        val p = if (dataCol != -1) c.getString(dataCol) ?: "" else ""
                        if (p.isEmpty() || PermanentDeleteHelper.isMarkedDeleted(this, p)) continue
                        val f = File(p)
                        if (!f.exists() || f.length() < 1024L) continue
                        val t = (if (nameCol != -1) c.getString(nameCol) else null)
                            ?: (if (titleCol != -1) c.getString(titleCol) else null)
                            ?: f.name
                        val a = (if (artistCol != -1) c.getString(artistCol) else null)
                            ?.takeIf { it != "<unknown>" && it.isNotBlank() } ?: "Hi-Res Studio Audio"
                        paths.add(p)
                        titles.add(t)
                        artists.add(a)
                    }
                }
            } catch (_: Exception) {
            }

            if (paths.size > 1) {
                runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    songPaths.clear()
                    songPaths.addAll(paths)
                    songTitles.clear()
                    songTitles.addAll(titles)
                    songArtists.clear()
                    songArtists.addAll(artists)
                    currentIndex = songPaths.indexOf(activePath).coerceAtLeast(0)
                    updateUpNextBarUi()
                }
            }
        }
    }

    private fun resolveSafeAudioUri(rawPath: String): Uri {
        return try {
            if (rawPath.startsWith("content://") || rawPath.startsWith("http://") || rawPath.startsWith("https://")) {
                Uri.parse(rawPath)
            } else {
                Uri.fromFile(File(rawPath))
            }
        } catch (_: Exception) {
            Uri.fromFile(File(rawPath))
        }
    }

    private fun initializeOrAttachSharedExoPlayer() {
        try {
            val d = resources.displayMetrics.density
            if (sharedExoPlayer == null) {
                val renderersFactory = DefaultRenderersFactory(applicationContext).apply {
                    setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                    setEnableDecoderFallback(true)
                }
                val fastLoadControl = DefaultLoadControl.Builder()
                    .setBufferDurationsMs(1500, 30000, 120, 250)
                    .build()

                sharedExoPlayer = ExoPlayer.Builder(applicationContext, renderersFactory)
                    .setLoadControl(fastLoadControl)
                    .build()
            }

            val player = sharedExoPlayer ?: return
            val resumeExisting = intent?.getBooleanExtra("RESUME_EXISTING", false) == true

            if (!resumeExisting || player.mediaItemCount == 0) {
                val items = if (songPaths.isNotEmpty()) {
                    songPaths.map { MediaItem.fromUri(resolveSafeAudioUri(it)) }
                } else if (currentPath.isNotEmpty()) {
                    listOf(MediaItem.fromUri(resolveSafeAudioUri(currentPath)))
                } else {
                    emptyList()
                }

                if (items.isNotEmpty()) {
                    player.setMediaItems(items, currentIndex.coerceIn(0, items.size - 1), 0L)
                    player.repeatMode = Player.REPEAT_MODE_ALL
                    player.prepare()
                    player.playWhenReady = true
                }
            }

            setupLoudnessEnhancerForSession(player.audioSessionId)

            player.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    btnPlayPauseSong.setImageDrawable(
                        AudioThreeDotMenuHelper.PureWhiteAudioVectorDrawable(
                            if (isPlaying) "PAUSE" else "PLAY",
                            d
                        )
                    )
                    updateDiscRotationAnimation(isPlaying)
                    syncNotificationAndSharedState()
                }

                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    setupLoudnessEnhancerForSession(audioSessionId)
                    syncNotificationAndSharedState()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    if (stopAfterCurrentTrack && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                        player.pause()
                    }
                    val newIdx = player.currentMediaItemIndex
                    if (newIdx in songPaths.indices) {
                        currentIndex = newIdx
                        currentPath = songPaths[newIdx]
                        currentTitle = songTitles.getOrElse(newIdx) { File(currentPath).name }
                        currentArtist = songArtists.getOrElse(newIdx) { "Hi-Res Studio Audio" }
                        refreshCurrentSongUiAndArt()
                        syncNotificationAndSharedState()
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        val dur = player.duration.coerceAtLeast(1000L)
                        seekBarAudio.max = dur.toInt()
                        tvTotalTime.text = formatDurationMs(dur)
                        setupLoudnessEnhancerForSession(player.audioSessionId)
                        syncNotificationAndSharedState()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (songPaths.size > 1) {
                        playNextSong()
                    }
                }
            })

            mediaSessionManager?.release()
            mediaSessionManager = MediaSessionManager(
                context = this,
                playerProvider = { sharedExoPlayer },
                onNextTrack = { playNextSong() },
                onPrevTrack = { playPreviousSong() },
                onPlayStateChanged = { playing ->
                    btnPlayPauseSong.setImageDrawable(
                        AudioThreeDotMenuHelper.PureWhiteAudioVectorDrawable(
                            if (playing) "PAUSE" else "PLAY",
                            d
                        )
                    )
                    syncNotificationAndSharedState()
                }
            ).also { it.requestAudioFocusAndRegister() }

            refreshCurrentSongUiAndArt()
            syncNotificationAndSharedState()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun refreshCurrentSongUiAndArt() {
        val d = resources.displayMetrics.density
        tvSongTitleMain.text = currentTitle.substringBeforeLast('.')
        tvSongArtistMain.text = currentArtist.ifEmpty { "Hi-Res Studio Audio" }

        val prefs = getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        isFavorite = prefs.getBoolean("fav_audio_$currentPath", false)
        btnFavoriteHeart.setImageDrawable(
            AudioThreeDotMenuHelper.PureWhiteAudioVectorDrawable(
                if (isFavorite) "HEART_ON" else "HEART_OFF",
                d
            )
        )

        isSuperLoud200Boost = getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
            .getBoolean("super_loud_200_boost", true)
        pillBoostQuick.text = if (isSuperLoud200Boost) "200% Boost: ON" else "200% Boost: OFF"

        AudioAdapter.loadAlbumArtOrFallbackInto(this, currentPath, currentTitle, imgMainSongCover)
        updateUpNextBarUi()

        val playingNow = sharedExoPlayer?.isPlaying == true
        btnPlayPauseSong.setImageDrawable(
            AudioThreeDotMenuHelper.PureWhiteAudioVectorDrawable(
                if (playingNow) "PAUSE" else "PLAY",
                d
            )
        )
        updateDiscRotationAnimation(playingNow)
    }

    private fun updateUpNextBarUi() {
        if (songPaths.isEmpty()) {
            tvUpNextTitle.text = "Up Next: Queue Ready"
            AudioAdapter.loadAlbumArtOrFallbackInto(this, currentPath, currentTitle, imgUpNextCover)
            return
        }
        val nextIdx = (currentIndex + 1) % songPaths.size
        val nextPath = songPaths[nextIdx]
        val nextTitle = songTitles.getOrElse(nextIdx) { File(nextPath).name }.substringBeforeLast('.')
        tvUpNextTitle.text = "Up Next: $nextTitle"
        AudioAdapter.loadAlbumArtOrFallbackInto(this, nextPath, nextTitle, imgUpNextCover)
    }

    private fun setupLoudnessEnhancerForSession(sessionId: Int) {
        if (sessionId <= 0 || sessionId == C.AUDIO_SESSION_ID_UNSET) return
        try {
            sharedLoudnessEnhancer?.release()
            sharedLoudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                enabled = true
                setTargetGain(if (isSuperLoud200Boost) 1400 else 0)
            }
        } catch (_: Exception) {
        }
    }

    private fun syncNotificationAndSharedState() {
        val player = sharedExoPlayer ?: return
        val playing = player.isPlaying
        val sessionId = player.audioSessionId
        val pos = player.currentPosition.coerceAtLeast(0L)
        val dur = player.duration.coerceAtLeast(1000L)

        AudioFragment.updateSharedAudioState(
            sessionId = sessionId,
            path = currentPath,
            title = currentTitle,
            artist = currentArtist,
            isPlaying = playing
        )

        mediaSessionManager?.updateMetadataAndState(
            title = currentTitle,
            artist = currentArtist,
            durationMs = dur
        )

        BackgroundAudioService.updateForegroundNotification(
            context = this,
            title = currentTitle.substringBeforeLast('.'),
            artist = currentArtist,
            isPlaying = playing,
            isVideoBackground = false,
            videoPath = currentPath,
            positionMs = pos,
            durationMs = dur
        )
    }

    private fun togglePlayPauseNow() {
        val player = sharedExoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
        syncNotificationAndSharedState()
    }

    private fun playPreviousSong() {
        val player = sharedExoPlayer ?: return
        if (songPaths.isEmpty()) return
        currentIndex = if (loopMode == 0 && songPaths.size > 1) {
            Random.nextInt(songPaths.size)
        } else {
            (currentIndex - 1 + songPaths.size) % songPaths.size
        }
        currentPath = songPaths[currentIndex]
        currentTitle = songTitles.getOrElse(currentIndex) { File(currentPath).name }
        currentArtist = songArtists.getOrElse(currentIndex) { "Hi-Res Studio Audio" }

        if (currentIndex < player.mediaItemCount) {
            player.seekToDefaultPosition(currentIndex)
        } else {
            player.setMediaItem(MediaItem.fromUri(resolveSafeAudioUri(currentPath)))
            player.prepare()
        }
        player.play()
        refreshCurrentSongUiAndArt()
        syncNotificationAndSharedState()
    }

    private fun playNextSong() {
        val player = sharedExoPlayer ?: return
        if (songPaths.isEmpty()) return
        currentIndex = if (loopMode == 0 && songPaths.size > 1) {
            Random.nextInt(songPaths.size)
        } else {
            (currentIndex + 1) % songPaths.size
        }
        currentPath = songPaths[currentIndex]
        currentTitle = songTitles.getOrElse(currentIndex) { File(currentPath).name }
        currentArtist = songArtists.getOrElse(currentIndex) { "Hi-Res Studio Audio" }

        if (currentIndex < player.mediaItemCount) {
            player.seekToDefaultPosition(currentIndex)
        } else {
            player.setMediaItem(MediaItem.fromUri(resolveSafeAudioUri(currentPath)))
            player.prepare()
        }
        player.play()
        refreshCurrentSongUiAndArt()
        syncNotificationAndSharedState()
    }

    private fun openPlayingQueueSheet() {
        AudioThreeDotMenuHelper.showPlayingQueueDialog(
            context = this,
            songPaths = songPaths,
            songTitles = songTitles,
            songArtists = songArtists,
            currentIndex = currentIndex,
            onSelectTrack = { chosenIdx ->
                if (chosenIdx in songPaths.indices) {
                    currentIndex = chosenIdx
                    currentPath = songPaths[chosenIdx]
                    currentTitle = songTitles.getOrElse(chosenIdx) { File(currentPath).name }
                    currentArtist = songArtists.getOrElse(chosenIdx) { "Hi-Res Studio Audio" }
                    sharedExoPlayer?.let { p ->
                        if (chosenIdx < p.mediaItemCount) {
                            p.seekToDefaultPosition(chosenIdx)
                        } else {
                            p.setMediaItem(MediaItem.fromUri(resolveSafeAudioUri(currentPath)))
                            p.prepare()
                        }
                        p.play()
                    }
                    refreshCurrentSongUiAndArt()
                    syncNotificationAndSharedState()
                }
            },
            onRemoveTrack = { removeIdx ->
                if (removeIdx in songPaths.indices && songPaths.size > 1) {
                    songPaths.removeAt(removeIdx)
                    if (removeIdx in songTitles.indices) songTitles.removeAt(removeIdx)
                    if (removeIdx in songArtists.indices) songArtists.removeAt(removeIdx)
                    sharedExoPlayer?.let { p ->
                        if (removeIdx < p.mediaItemCount) {
                            p.removeMediaItem(removeIdx)
                        }
                    }
                    if (removeIdx < currentIndex) {
                        currentIndex = (currentIndex - 1).coerceAtLeast(0)
                    }
                    updateUpNextBarUi()
                }
            }
        )
    }

    private fun updateDiscRotationAnimation(isPlaying: Boolean) {
        if (!isVinylCircleMode) {
            discRotationAnimator?.cancel()
            coverArtOuterCard.rotation = 0f
            return
        }
        if (discRotationAnimator == null) {
            discRotationAnimator = ObjectAnimator.ofFloat(coverArtOuterCard, View.ROTATION, 0f, 360f).apply {
                duration = 14000L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
            }
        }
        if (isPlaying) {
            if (discRotationAnimator?.isStarted == true) {
                discRotationAnimator?.resume()
            } else {
                discRotationAnimator?.start()
            }
        } else {
            discRotationAnimator?.pause()
        }
    }

    override fun getExoPlayer(): ExoPlayer? = sharedExoPlayer
    override fun getAudioSessionId(): Int = sharedExoPlayer?.audioSessionId ?: 0
    override fun getCurrentSongPath(): String = currentPath
    override fun getCurrentSongTitle(): String = currentTitle
    override fun getCurrentSongArtist(): String = currentArtist
    override fun getCurrentPositionMs(): Long = (sharedExoPlayer?.currentPosition ?: 0L).coerceAtLeast(0L)
    override fun getDurationMs(): Long = (sharedExoPlayer?.duration ?: 1000L).coerceAtLeast(1000L)
    override fun isFavoriteSong(): Boolean = isFavorite

    override fun onToggleFavorite() {
        val d = resources.displayMetrics.density
        isFavorite = !isFavorite
        getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("fav_audio_$currentPath", isFavorite)
            .apply()
        btnFavoriteHeart.setImageDrawable(
            AudioThreeDotMenuHelper.PureWhiteAudioVectorDrawable(
                if (isFavorite) "HEART_ON" else "HEART_OFF",
                d
            )
        )
        Toast.makeText(
            this,
            if (isFavorite) "Added to Favorite Songs" else "Removed from Favorite Songs",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun isSuperLoudBoostActive(): Boolean = isSuperLoud200Boost

    override fun onToggleSuperLoudBoost() {
        isSuperLoud200Boost = !isSuperLoud200Boost
        getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("super_loud_200_boost", isSuperLoud200Boost)
            .apply()
        try {
            sharedLoudnessEnhancer?.setTargetGain(if (isSuperLoud200Boost) 1400 else 0)
        } catch (_: Exception) {
        }
        pillBoostQuick.text = if (isSuperLoud200Boost) "200% Boost: ON" else "200% Boost: OFF"
        Toast.makeText(
            this,
            if (isSuperLoud200Boost) "200% Super Loud Boost ON" else "Normal 100% Volume",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun getPlaybackSpeed(): Float = currentSpeed

    override fun onChangePlaybackSpeed(newSpeed: Float) {
        currentSpeed = newSpeed.coerceIn(0.25f, 4.0f)
        sharedExoPlayer?.playbackParameters = PlaybackParameters(currentSpeed)
        pillSpeedQuick.text = String.format(Locale.US, "%.2fx Speed", currentSpeed)
    }

    override fun getLoopMode(): Int = loopMode

    override fun onChangeLoopMode(newMode: Int) {
        val d = resources.displayMetrics.density
        loopMode = newMode.coerceIn(0, 3)
        stopAfterCurrentTrack = false

        when (loopMode) {
            0 -> {
                sharedExoPlayer?.shuffleModeEnabled = true
                sharedExoPlayer?.repeatMode = Player.REPEAT_MODE_ALL
                btnLoopMode.setImageDrawable(AudioThreeDotMenuHelper.PureWhiteAudioVectorDrawable("SHUFFLE", d))
                Toast.makeText(this, "Shuffle Play Active", Toast.LENGTH_SHORT).show()
            }
            1 -> {
                sharedExoPlayer?.shuffleModeEnabled = false
                sharedExoPlayer?.repeatMode = Player.REPEAT_MODE_ONE
                btnLoopMode.setImageDrawable(AudioThreeDotMenuHelper.PureWhiteAudioVectorDrawable("REPEAT_ONE", d))
                Toast.makeText(this, "Repeat Current Song Active", Toast.LENGTH_SHORT).show()
            }
            2 -> {
                sharedExoPlayer?.shuffleModeEnabled = false
                sharedExoPlayer?.repeatMode = Player.REPEAT_MODE_ALL
                btnLoopMode.setImageDrawable(AudioThreeDotMenuHelper.PureWhiteAudioVectorDrawable("REPEAT_ALL", d))
                Toast.makeText(this, "Loop All Songs Active", Toast.LENGTH_SHORT).show()
            }
            3 -> {
                sharedExoPlayer?.shuffleModeEnabled = false
                sharedExoPlayer?.repeatMode = Player.REPEAT_MODE_OFF
                stopAfterCurrentTrack = true
                btnLoopMode.setImageDrawable(AudioThreeDotMenuHelper.PureWhiteAudioVectorDrawable("REPEAT_ALL", d))
                Toast.makeText(this, "Play 1 Song (Stop After Current)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onTriggerAbRepeat() {
        val pos = getCurrentPositionMs()
        when {
            pointAMs == -1L -> {
                pointAMs = pos
                pillAbRepeatQuick.text = "AB: A Set"
                Toast.makeText(this, "Point A set at ${formatDurationMs(pointAMs)}", Toast.LENGTH_SHORT).show()
            }
            pointBMs == -1L -> {
                if (pos > pointAMs) {
                    pointBMs = pos
                    pillAbRepeatQuick.text = "AB: Looping"
                    Toast.makeText(this, "Looping A to B", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Point B must be after Point A", Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                pointAMs = -1L
                pointBMs = -1L
                pillAbRepeatQuick.text = "AB Repeat"
                Toast.makeText(this, "AB Repeat Cleared", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun getAbRepeatStatusText(): String {
        return when {
            pointAMs != -1L && pointBMs != -1L -> "AB: ON"
            pointAMs != -1L -> "AB: A Set"
            else -> "AB Repeat"
        }
    }

    override fun onOpenSleepTimer() {
        SmartSleepTimerDialog.showSleepTimerPicker(
            context = this,
            onStopTimer = { smartIdlePauseHelper?.cancelTimer() },
            onStartTimerMinutes = { mins, motion ->
                smartIdlePauseHelper?.startSmoothFadeSleepTimer(mins, motion)
            }
        )
    }

    override fun onToggleVinylPhotoMode() {
        val d = resources.displayMetrics.density
        isVinylCircleMode = !isVinylCircleMode
        if (isVinylCircleMode) {
            coverArtOuterCard.radius = 128f * d
            vinylCenterHoleView.visibility = View.VISIBLE
            updateDiscRotationAnimation(sharedExoPlayer?.isPlaying == true)
            Toast.makeText(this, "Rotating Vinyl Disc View", Toast.LENGTH_SHORT).show()
        } else {
            discRotationAnimator?.cancel()
            coverArtOuterCard.rotation = 0f
            coverArtOuterCard.radius = 28f * d
            vinylCenterHoleView.visibility = View.GONE
            Toast.makeText(this, "Studio Square Cover Photo View", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDeleteCurrentSongPermanently() {
        sharedExoPlayer?.pause()
        PermanentDeleteHelper.confirmAndDeletePermanently(
            context = this,
            title = currentTitle,
            path = currentPath,
            isVideo = false
        ) {
            if (songPaths.size > 1 && currentIndex in songPaths.indices) {
                val removedIdx = currentIndex
                songPaths.removeAt(removedIdx)
                if (removedIdx in songTitles.indices) songTitles.removeAt(removedIdx)
                if (removedIdx in songArtists.indices) songArtists.removeAt(removedIdx)
                sharedExoPlayer?.let { p ->
                    if (removedIdx < p.mediaItemCount) p.removeMediaItem(removedIdx)
                }
                currentIndex = currentIndex.coerceIn(0, songPaths.size - 1)
                currentPath = songPaths[currentIndex]
                currentTitle = songTitles.getOrElse(currentIndex) { File(currentPath).name }
                currentArtist = songArtists.getOrElse(currentIndex) { "Hi-Res Studio Audio" }
                sharedExoPlayer?.play()
                refreshCurrentSongUiAndArt()
            } else {
                finish()
            }
        }
    }

    private fun registerNotificationReceiver() {
        if (isNotifReceiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(BackgroundAudioService.ACTION_NOTIF_PREV)
                addAction(BackgroundAudioService.ACTION_NOTIF_PLAY_PAUSE)
                addAction(BackgroundAudioService.ACTION_NOTIF_NEXT)
                addAction(BackgroundAudioService.ACTION_NOTIF_SEEK)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(notifSyncReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(notifSyncReceiver, filter)
            }
            isNotifReceiverRegistered = true
        } catch (_: Exception) {
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (volumeHudHelper?.onHardwareVolumeKeyPressed(isVolumeUp = true) == true) return true
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (volumeHudHelper?.onHardwareVolumeKeyPressed(isVolumeUp = false) == true) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun formatDurationMs(millis: Long): String {
        val safe = millis.coerceAtLeast(0L)
        val totalSecs = TimeUnit.MILLISECONDS.toSeconds(safe)
        val hrs = totalSecs / 3600
        val mins = (totalSecs % 3600) / 60
        val secs = totalSecs % 60
        return if (hrs > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(progressUpdateRunnable)
        discRotationAnimator?.cancel()
        if (isNotifReceiverRegistered) {
            try {
                unregisterReceiver(notifSyncReceiver)
            } catch (_: Exception) {
            }
            isNotifReceiverRegistered = false
        }
    }

    private class PureWhiteWaveformView(
        context: Context,
        private val d: Float
    ) : View(context) {

        private var isPlaying = false
        private var progressFrac = 0f
        private var animPhase = 0f

        private val activeBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }

        private val inactiveBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#44FFFFFF")
        }

        fun setPlayingState(isPlaying: Boolean, progressFraction: Float) {
            this.isPlaying = isPlaying
            this.progressFrac = progressFraction.coerceIn(0f, 1f)
            if (isPlaying) {
                animPhase += 0.35f
            }
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val barCount = 36
            val gap = 3.5f * d
            val totalGaps = gap * (barCount - 1)
            val barW = ((w - totalGaps) / barCount).coerceAtLeast(2f * d)
            val centerY = h / 2f

            for (i in 0 until barCount) {
                val barFrac = i.toFloat() / barCount.toFloat()
                val waveFactor = if (isPlaying) {
                    0.30f + 0.65f * abs(sin(animPhase + i * 0.45f))
                } else {
                    0.25f + 0.35f * abs(sin(i * 0.5f))
                }
                val barH = (h * waveFactor).coerceIn(6f * d, h - 2f * d)
                val left = i * (barW + gap)
                val rect = RectF(left, centerY - barH / 2f, left + barW, centerY + barH / 2f)
                val paint = if (barFrac <= progressFrac) activeBarPaint else inactiveBarPaint
                canvas.drawRoundRect(rect, 3f * d, 3f * d, paint)
            }
        }
    }
}
