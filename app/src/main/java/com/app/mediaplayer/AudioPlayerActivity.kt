package com.app.mediaplayer

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PorterDuff
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
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import kotlin.random.Random

@OptIn(UnstableApi::class)
class AudioPlayerActivity : AppCompatActivity(), AudioThreeDotMenuHelper.AudioPlayerCallbacks {

    companion object {
        private var sharedExoPlayer: ExoPlayer? = null
        private var sharedLoudnessEnhancer: LoudnessEnhancer? = null
    }

    private lateinit var rootAudioPlayerFrame: FrameLayout
    private lateinit var tvHeaderCounter: TextView
    private lateinit var btnTopEqualizer: ImageView
    private lateinit var btnTopThreeDots: ImageView

    private lateinit var coverArtOuterCard: CardView
    private lateinit var imgMainSongCover: ImageView
    private lateinit var vinylCenterHoleView: View
    private lateinit var dotIndicatorLeft: View
    private lateinit var dotIndicatorCenter: View
    private lateinit var dotIndicatorRight: View

    private lateinit var tvSongTitleMain: TextView
    private lateinit var tvSongArtistMain: TextView

    private lateinit var btnFavoriteHeart: ImageView
    private lateinit var pillBoostBadge: TextView
    private lateinit var pillSpeedBadge: TextView
    private lateinit var btnQuickShare: ImageView

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
    private var sleepTimerBadgeText: String = "Sleep Timer"

    private var songPaths = ArrayList<String>()
    private var songTitles = ArrayList<String>()
    private var songArtists = ArrayList<String>()
    private var currentIndex = 0
    private var currentPath = ""
    private var currentTitle = "Music Track"
    private var currentArtist = "Unknown Artist"

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
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.parseColor("#0A0A10")

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        buildMinimalPremiumAudioUi()
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
                sleepTimerBadgeText = if (label == "Timer") "Sleep Timer" else label
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

    private fun buildMinimalPremiumAudioUi() {
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
            setPadding((22 * d).toInt(), (16 * d).toInt(), (22 * d).toInt(), (16 * d).toInt())
        }

        ViewCompat.setOnApplyWindowInsetsListener(mainColumn) { v, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                (22 * d).toInt(),
                sysBars.top + (10 * d).toInt(),
                (22 * d).toInt(),
                sysBars.bottom + (12 * d).toInt()
            )
            insets
        }

        // 1. Minimal Top Bar: Back Button, Subtle Track Counter, Borderless Equalizer & 3-Dot Icons
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val btnBack = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((42 * d).toInt(), (42 * d).toInt())
            setPadding((9 * d).toInt(), (9 * d).toInt(), (9 * d).toInt(), (9 * d).toInt())
            setImageDrawable(AudioThreeDotMenuHelper.PureWhiteAudioVectorDrawable("BACK_ARROW", d))
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                finish()
            }
        }

        tvHeaderCounter = TextView(this).apply {
            text = "Now Playing"
            setTextColor(Color.parseColor("#A8B8D0"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        btnTopEqualizer = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((42 * d).toInt(), (42 * d).toInt()).apply {
                marginEnd = (4 * d).toInt()
            }
            setPadding((9 * d).toInt(), (9 * d).toInt(), (9 * d).toInt(), (9 * d).toInt())
            setImageDrawable(AudioThreeDotMenuHelper.PureWhiteAudioVectorDrawable("EQUALIZER", d))
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                EqualizerHelper.showEqualizerDialog(this@AudioPlayerActivity, getAudioSessionId())
            }
        }

        btnTopThreeDots = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((42 * d).toInt(), (42 * d).toInt())
            setPadding((9 * d).toInt(), (9 * d).toInt(), (9 * d).toInt(), (9 * d).toInt())
            setImageDrawable(AudioThreeDotMenuHelper.PureWhiteAudioVectorDrawable("THREE_DOTS", d))
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                AudioThreeDotMenuHelper.showAudioThreeDotStudioDialog(
                    this@AudioPlayerActivity,
                    this@AudioPlayerActivity
                )
            }
        }

        topBar.addView(btnBack)
        topBar.addView(tvHeaderCounter)
        topBar.addView(btnTopEqualizer)
        topBar.addView(btnTopThreeDots)
        mainColumn.addView(topBar)

        // 2. Centered Album Art with Balanced Breathing Room & Subtle Swipe Carousel Dots
        val coverSectionCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val coverSizePx = (268 * d).toInt()
        coverArtOuterCard = CardView(this).apply {
            radius = 26f * d
            cardElevation = 18f * d
            setCardBackgroundColor(Color.parseColor("#141E30"))
            layoutParams = LinearLayout.LayoutParams(coverSizePx, coverSizePx)
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
            layoutParams = FrameLayout.LayoutParams((52 * d).toInt(), (52 * d).toInt(), Gravity.CENTER)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#0A0A10"))
                setStroke((3.5f * d).toInt(), Color.WHITE)
            }
        }

        coverInnerFrame.addView(imgMainSongCover)
        coverInnerFrame.addView(vinylCenterHoleView)
        coverArtOuterCard.addView(coverInnerFrame)
        coverSectionCol.addView(coverArtOuterCard)

        val dotsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (14 * d).toInt() }
        }

        dotIndicatorLeft = View(this).apply {
            layoutParams = LinearLayout.LayoutParams((6 * d).toInt(), (6 * d).toInt()).apply {
                marginEnd = (6 * d).toInt()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#44FFFFFF"))
            }
        }

        dotIndicatorCenter = View(this).apply {
            layoutParams = LinearLayout.LayoutParams((18 * d).toInt(), (6 * d).toInt()).apply {
                marginEnd = (6 * d).toInt()
            }
            background = GradientDrawable().apply {
                cornerRadius = 3f * d
                setColor(Color.WHITE)
            }
        }

        dotIndicatorRight = View(this).apply {
            layoutParams = LinearLayout.LayoutParams((6 * d).toInt(), (6 * d).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#44FFFFFF"))
            }
        }

        dotsRow.addView(dotIndicatorLeft)
        dotsRow.addView(dotIndicatorCenter)
        dotsRow.addView(dotIndicatorRight)
        coverSectionCol.addView(dotsRow)

        attachCoverSwipeAndTapGestures(coverSectionCol)
        mainColumn.addView(coverSectionCol)

        // 3. Full-Width Song Title & Artist (Never Cramped by Icons)
        val songTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (12 * d).toInt() }
        }

        tvSongTitleMain = TextView(this).apply {
            text = "Loading Track..."
            setTextColor(Color.WHITE)
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }

        tvSongArtistMain = TextView(this).apply {
            text = "Unknown Artist"
            setTextColor(Color.parseColor("#9FB4CE"))
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, (4 * d).toInt(), 0, 0)
        }

        songTextCol.addView(tvSongTitleMain)
        songTextCol.addView(tvSongArtistMain)
        mainColumn.addView(songTextCol)

        // 4. Clean Action Strip Above SeekBar: Favorite Heart, Subtle Boost Pill, Speed Pill, Share
        val secondaryActionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * d).toInt() }
        }

        btnFavoriteHeart = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((38 * d).toInt(), (38 * d).toInt()).apply {
                marginEnd = (10 * d).toInt()
            }
            setPadding((7 * d).toInt(), (7 * d).toInt(), (7 * d).toInt(), (7 * d).toInt())
            setImageDrawable(AudioThreeDotMenuHelper.PureWhiteAudioVectorDrawable("HEART_OFF", d))
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onToggleFavorite()
            }
        }

        pillBoostBadge = TextView(this).apply {
            text = "200% Boost"
            setTextColor(Color.WHITE)
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((12 * d).toInt(), (5 * d).toInt(), (12 * d).toInt(), (5 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (8 * d).toInt() }
            background = AudioThreeDotMenuHelper.makeFrostedWhitePill(d)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onToggleSuperLoudBoost()
            }
        }

        pillSpeedBadge = TextView(this).apply {
            text = "1.0x"
            setTextColor(Color.WHITE)
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((12 * d).toInt(), (5 * d).toInt(), (12 * d).toInt(), (5 * d).toInt())
            background = AudioThreeDotMenuHelper.makeFrostedWhitePill(d)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                val nextSpeed = when (currentSpeed) {
                    1.0f -> 1.25f
                    1.25f -> 1.5f
                    1.5f -> 2.0f
                    2.0f -> 0.8f
                    else -> 1.0f
                }
                onChangePlaybackSpeed(nextSpeed)
            }
        }

        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }

        btnQuickShare = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((38 * d).toInt(), (38 * d).toInt())
            setPadding((8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
            setImageDrawable(AudioThreeDotMenuHelper.PureWhiteAudioVectorDrawable("SHARE", d))
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                try {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "audio/*"
                        putExtra(Intent.EXTRA_STREAM, Uri.parse(currentPath))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share Song"))
                } catch (_: Exception) {
                }
            }
        }

        secondaryActionsRow.addView(btnFavoriteHeart)
        secondaryActionsRow.addView(pillBoostBadge)
        secondaryActionsRow.addView(pillSpeedBadge)
        secondaryActionsRow.addView(spacer)
        secondaryActionsRow.addView(btnQuickShare)
        mainColumn.addView(secondaryActionsRow)

        // 5. Single Sleek Timeline SeekBar + Below Timestamps
        val timelineSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (14 * d).toInt() }
        }

        seekBarAudio = SeekBar(this).apply {
            max = 1000
            progressDrawable.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            thumb.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            setPadding((4 * d).toInt(), (6 * d).toInt(), (4 * d).toInt(), (6 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
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

        val timeLabelsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        tvCurrentTime = TextView(this).apply {
            text = "00:00"
            setTextColor(Color.parseColor("#9FB4CE"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        tvTotalTime = TextView(this).apply {
            text = "00:00"
            setTextColor(Color.parseColor("#9FB4CE"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        timeLabelsRow.addView(tvCurrentTime)
        timeLabelsRow.addView(tvTotalTime)
        timelineSection.addView(seekBarAudio)
        timelineSection.addView(timeLabelsRow)
        mainColumn.addView(timelineSection)

        // 6. Balanced Borderless Transport Controls with Prominent Center Play/Pause
        val transportRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (18 * d).toInt() }
        }

        btnLoopMode = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((44 * d).toInt(), (44 * d).toInt())
            setPadding((10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt())
            setImageDrawable(AudioThreeDotMenuHelper.PureWhiteAudioVectorDrawable("REPEAT_ALL", d))
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onChangeLoopMode((loopMode + 1) % 4)
            }
        }

        val space1 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }

        btnPrevSong = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((52 * d).toInt(), (52 * d).toInt())
            setPadding((12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt())
            setImageDrawable(AudioThreeDotMenuHelper.PureWhiteAudioVectorDrawable("PREV", d))
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                playPreviousSong()
            }
        }

        val space2 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams((22 * d).toInt(), 1)
        }

        btnPlayPauseSong = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((68 * d).toInt(), (68 * d).toInt())
            setPadding((18 * d).toInt(), (18 * d).toInt(), (18 * d).toInt(), (18 * d).toInt())
            setImageDrawable(AudioThreeDotMenuHelper.PureWhiteAudioVectorDrawable("PAUSE", d))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#24FFFFFF"))
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                togglePlayPauseNow()
            }
        }

        val space3 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams((22 * d).toInt(), 1)
        }

        btnNextSong = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((52 * d).toInt(), (52 * d).toInt())
            setPadding((12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt())
            setImageDrawable(AudioThreeDotMenuHelper.PureWhiteAudioVectorDrawable("NEXT", d))
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                playNextSong()
            }
        }

        val space4 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }

        btnOpenQueue = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((44 * d).toInt(), (44 * d).toInt())
            setPadding((10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt())
            setImageDrawable(AudioThreeDotMenuHelper.PureWhiteAudioVectorDrawable("QUEUE_LIST", d))
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                openPlayingQueueSheet()
            }
        }

        transportRow.addView(btnLoopMode)
        transportRow.addView(space1)
        transportRow.addView(btnPrevSong)
        transportRow.addView(space2)
        transportRow.addView(btnPlayPauseSong)
        transportRow.addView(space3)
        transportRow.addView(btnNextSong)
        transportRow.addView(space4)
        transportRow.addView(btnOpenQueue)
        mainColumn.addView(transportRow)

        // 7. Sleek Minimal Floating "Up Next" Strip
        val bottomDockCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#12FFFFFF"))
                cornerRadius = 16f * d
            }
            setOnClickListener {
                openPlayingQueueSheet()
            }
        }

        imgUpNextCover = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((36 * d).toInt(), (36 * d).toInt()).apply {
                marginEnd = (12 * d).toInt()
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        tvUpNextTitle = TextView(this).apply {
            text = "Up Next"
            setTextColor(Color.parseColor("#D8E2F0"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvQueueHint = TextView(this).apply {
            text = "Queue  ^"
            setTextColor(Color.parseColor("#9FB4CE"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        }

        bottomDockCard.addView(imgUpNextCover)
        bottomDockCard.addView(tvUpNextTitle)
        bottomDockCard.addView(tvQueueHint)
        mainColumn.addView(bottomDockCard)

        rootAudioPlayerFrame.addView(mainColumn)
        setContentView(rootAudioPlayerFrame)
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
                    coverArtOuterCard.animate()
                        .translationX(if (dx < 0) -36f else 36f)
                        .setDuration(90L)
                        .withEndAction {
                            coverArtOuterCard.animate().translationX(0f).setDuration(140L).start()
                        }
                        .start()
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
            ?: File(pathExtra).name.ifEmpty { "Audio Track" }

        val artistExtra = incoming.getStringExtra("AUDIO_ARTIST")
            ?: incoming.getStringExtra("audio_artist")
            ?: "Unknown Artist"

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
                            ?.takeIf { it != "<unknown>" && it.isNotBlank() } ?: "Unknown Artist"
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
                        currentArtist = songArtists.getOrElse(newIdx) { "Unknown Artist" }
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
        tvSongTitleMain.text = AudioThreeDotMenuHelper.formatCleanSongTitle(currentTitle)
        val cleanArtist = currentArtist.takeIf {
            it.isNotBlank() && !it.equals("Hi-Res Studio Audio", ignoreCase = true)
        } ?: "Unknown Artist"
        tvSongArtistMain.text = cleanArtist

        if (songPaths.isNotEmpty()) {
            tvHeaderCounter.text = "Now Playing (${(currentIndex + 1).coerceAtMost(songPaths.size)}/${songPaths.size})"
        } else {
            tvHeaderCounter.text = "Now Playing"
        }

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
        pillBoostBadge.text = if (isSuperLoud200Boost) "200% Boost: ON" else "100% Normal"

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
        val nextTitle = AudioThreeDotMenuHelper.formatCleanSongTitle(
            songTitles.getOrElse(nextIdx) { File(nextPath).name }
        )
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
            title = AudioThreeDotMenuHelper.formatCleanSongTitle(currentTitle),
            artist = currentArtist,
            durationMs = dur
        )

        BackgroundAudioService.updateForegroundNotification(
            context = this,
            title = AudioThreeDotMenuHelper.formatCleanSongTitle(currentTitle),
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
        currentArtist = songArtists.getOrElse(currentIndex) { "Unknown Artist" }

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
        currentArtist = songArtists.getOrElse(currentIndex) { "Unknown Artist" }

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
                    currentArtist = songArtists.getOrElse(chosenIdx) { "Unknown Artist" }
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
        pillBoostBadge.text = if (isSuperLoud200Boost) "200% Boost: ON" else "100% Normal"
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
        pillSpeedBadge.text = String.format(Locale.US, "%.2fx", currentSpeed)
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
                Toast.makeText(this, "Point A set at ${formatDurationMs(pointAMs)}", Toast.LENGTH_SHORT).show()
            }
            pointBMs == -1L -> {
                if (pos > pointAMs) {
                    pointBMs = pos
                    Toast.makeText(this, "Looping A to B", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Point B must be after Point A", Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                pointAMs = -1L
                pointBMs = -1L
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
            coverArtOuterCard.radius = 134f * d
            vinylCenterHoleView.visibility = View.VISIBLE
            updateDiscRotationAnimation(sharedExoPlayer?.isPlaying == true)
        } else {
            discRotationAnimator?.cancel()
            coverArtOuterCard.rotation = 0f
            coverArtOuterCard.radius = 26f * d
            vinylCenterHoleView.visibility = View.GONE
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
                currentArtist = songArtists.getOrElse(currentIndex) { "Unknown Artist" }
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
}
