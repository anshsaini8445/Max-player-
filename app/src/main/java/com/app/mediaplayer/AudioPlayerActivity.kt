package com.app.mediaplayer

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class AudioPlayerActivity : AppCompatActivity(), SensorEventListener {

    companion object {
        private var sharedPlayer: ExoPlayer? = null
        private var sharedEqualizer: Equalizer? = null
        private var sharedBassBoost: BassBoost? = null

        private var activeSongTitle: String? = null
        private var activeSongArtist: String? = null
        private var activeSongPath: String? = null

        private const val NOTIF_CHANNEL_ID = "max_player_audio_channel"
        private const val NOTIF_ID = 8801
        private const val REQ_PICK_SONG_PHOTO = 5501
        private const val REQ_POST_NOTIF = 5502

        private const val ACTION_PREV = "com.app.mediaplayer.ACTION_AUDIO_PREV"
        private const val ACTION_PLAY_PAUSE = "com.app.mediaplayer.ACTION_AUDIO_PLAY_PAUSE"
        private const val ACTION_NEXT = "com.app.mediaplayer.ACTION_AUDIO_NEXT"
        private const val ACTION_SHUFFLE = "com.app.mediaplayer.ACTION_AUDIO_SHUFFLE"
        private const val ACTION_CLOSE = "com.app.mediaplayer.ACTION_AUDIO_CLOSE"

        fun getCurrentPlayingTitle(): String? = activeSongTitle
        fun getCurrentPlayingArtist(): String? = activeSongArtist
        fun getCurrentPlayingPath(): String? = activeSongPath
        fun isSharedPlayerPlaying(): Boolean = sharedPlayer?.isPlaying == true

        fun toggleSharedPlaybackFromMiniPlayer(context: Context): Boolean {
            val player = sharedPlayer ?: return false
            if (player.mediaItemCount == 0) return false
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
            context.sendBroadcast(Intent("com.app.mediaplayer.SYNC_NOTIFICATION").setPackage(context.packageName))
            return true
        }
    }

    private var exoPlayer: ExoPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var rootAudioPlayerFrame: FrameLayout
    private lateinit var imgAudioThemeWallpaper: ImageView
    private lateinit var viewAudioThemeDimmer: View

    private lateinit var btnAudioMinimize: ImageView
    private lateinit var tvAudioPlayerTitle: TextView
    private lateinit var tvAudioPlayerArtist: TextView
    private lateinit var btnAudioShareTop: ImageView

    private lateinit var tvLiveTimerCountdown: TextView

    private lateinit var llCenterArtContainer: LinearLayout
    private lateinit var imgVinylTonearm: ImageView
    private lateinit var cardCenterVinylDisc: CardView
    private lateinit var imgSquareFullArt: ImageView
    private lateinit var imgVinylSurface: ImageView
    private lateinit var cardInnerDiscPhoto: CardView
    private lateinit var imgCenterMusicNote: ImageView
    private lateinit var llSquarePagerDots: LinearLayout
    private lateinit var dotFlatPageArt: View
    private lateinit var dotFlatPageLyrics: View

    private lateinit var cardInlineLyricsStage: CardView
    private lateinit var btnFindLyricsOnline: TextView
    private lateinit var btnEditLyricsInline: TextView
    private lateinit var tvInlineLyricsContent: TextView

    private lateinit var cardSlideOutTabHandle: CardView
    private lateinit var imgSlideHandleArrow: ImageView

    private lateinit var llReorderableMiddleSlots: LinearLayout
    private lateinit var btnAudioEqualizer: LinearLayout
    private lateinit var imgBtnEqualizer: ImageView
    private lateinit var btnAudioAbRepeat: LinearLayout
    private lateinit var tvBtnAbRepeat: TextView
    private lateinit var btnAudioTimerAndSpeed: LinearLayout
    private lateinit var imgBtnTimerGauge: ImageView
    private lateinit var btnAudioPocketShake: LinearLayout
    private lateinit var imgBtnPocketShake: ImageView
    private lateinit var btnAudioFarRightDots: LinearLayout
    private lateinit var imgBtnMoreDots: ImageView

    private lateinit var tvAudioCurrentTime: TextView
    private lateinit var tvAudioTotalTime: TextView
    private lateinit var imgStraightWaveformBar: ImageView

    private lateinit var btnAudioShuffle: ImageView
    private lateinit var btnAudioPrev: ImageView
    private lateinit var btnAudioPlayPause: ImageView
    private lateinit var btnAudioNext: ImageView
    private lateinit var btnAudioRepeat: ImageView

    private lateinit var btnAudioFavoriteBottom: LinearLayout
    private lateinit var imgBtnFavoriteHeart: ImageView
    private lateinit var tvBottomFavoriteLabel: TextView
    private lateinit var btnAudioPlayingQueue: LinearLayout
    private lateinit var imgQueueIcon: ImageView
    private lateinit var tvPlayingQueueCount: TextView

    private lateinit var viewQueueOutsideScrim: View
    private lateinit var cardSlideSongDrawer: CardView
    private lateinit var tvDrawerHeaderTitle: TextView
    private lateinit var btnCloseSongDrawer: TextView
    private lateinit var rvSlideDrawerSongs: RecyclerView

    private lateinit var cardTopVolumeHud: CardView
    private lateinit var imgTopVolumeSpeaker: ImageView
    private lateinit var tvTopVolumePercent: TextView
    private lateinit var imgTopVolumeBarWithDots: ImageView

    private var songPaths = ArrayList<String>()
    private var songTitles = ArrayList<String>()
    private var songArtists = ArrayList<String>()
    private var songAlbumIds = ArrayList<Long>()
    private var currentTrackIndex = 0

    private var isShuffleEnabled = false
    private var repeatModeState = Player.REPEAT_MODE_ALL
    private var isFavorite = false
    private var isSquareAlbumStyle = false
    private var isInlineLyricsVisible = false
    private var isSimpleLineSeekBar = false
    private var currentSpeed = 1.0f
    private var accentColorInt = Color.parseColor("#00FFFF")

    private var pointA: Long = -1L
    private var pointB: Long = -1L

    private var sleepTimer: CountDownTimer? = null
    private var vinylAnimator: ObjectAnimator? = null
    private var tonearmAnimator: ObjectAnimator? = null

    private lateinit var audioManager: AudioManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var gestureDetector: GestureDetector
    private lateinit var waveformDrawable: StraightWaveformDrawable
    private lateinit var topVolumeBarDrawable: TopVolumeBarCleanDrawable

    // Pocket Shake Sensor Variables (Strictly Default OFF, No 2x Speed)
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var isDoubleShakeEnabled = false
    private var firstShakeTimestamp = 0L
    private var lastTriggerTimestamp = 0L
    private var shakeCount = 0
    private var isReceiverRegistered = false

    private val handler = Handler(Looper.getMainLooper())

    private val hideVolumeHudRunnable = Runnable {
        cardTopVolumeHud.visibility = View.GONE
    }

    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            try {
                exoPlayer?.let { player ->
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration.coerceAtLeast(1L)
                    tvAudioCurrentTime.text = formatDuration(pos)
                    tvAudioTotalTime.text = formatDuration(player.duration.coerceAtLeast(0L))

                    val fraction = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                    waveformDrawable.updateProgress(fraction, pointA, pointB, dur)

                    if (pointA != -1L && pointB != -1L && pos >= pointB) {
                        player.seekTo(pointA)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            handler.postDelayed(this, 150)
        }
    }

    private val mediaNotificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PREV -> playPreviousTrack()
                ACTION_PLAY_PAUSE -> {
                    exoPlayer?.let { player ->
                        if (player.isPlaying) player.pause() else player.play()
                        updateMediaNotification()
                    }
                }
                ACTION_NEXT -> playNextTrack()
                ACTION_SHUFFLE -> {
                    isShuffleEnabled = !isShuffleEnabled
                    exoPlayer?.shuffleModeEnabled = isShuffleEnabled
                    updateShuffleAndRepeatIcons()
                    updateMediaNotification()
                }
                ACTION_CLOSE -> {
                    exoPlayer?.pause()
                    notificationManager.cancel(NOTIF_ID)
                }
                "com.app.mediaplayer.SYNC_NOTIFICATION" -> {
                    updatePlayPauseIcon(exoPlayer?.isPlaying == true)
                    updateMediaNotification()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_player)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        requestNotificationPermissionIfNeeded()
        createNotificationChannel()
        registerMediaControlReceiver()

        bindViews()
        applySavedGlobalTheme()
        setupSleekPlayerIcons()
        readIncomingIntent(intent)
        applyMiddleButtonsOrder()
        setupDiscAndTonearmAnimations()
        initializeAudioPlayer(intent.getBooleanExtra("RESUME_IF_SAME", false))
        setupWaveformSeekTouch()
        setupScreenVolumeAndDrawerGestures()
        setupAllButtonClicks()
        setupDoubleShakeSensor()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasExtra("AUDIO_PATH") || intent.hasExtra("AUDIO_LIST_PATHS")) {
            readIncomingIntent(intent)
            initializeAudioPlayer(intent.getBooleanExtra("RESUME_IF_SAME", false))
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQ_POST_NOTIF
                )
            }
        }
    }

    private fun bindViews() {
        rootAudioPlayerFrame = findViewById(R.id.rootAudioPlayerFrame)
        imgAudioThemeWallpaper = findViewById(R.id.imgAudioThemeWallpaper)
        viewAudioThemeDimmer = findViewById(R.id.viewAudioThemeDimmer)

        btnAudioMinimize = findViewById(R.id.btnAudioMinimize)
        tvAudioPlayerTitle = findViewById(R.id.tvAudioPlayerTitle)
        tvAudioPlayerArtist = findViewById(R.id.tvAudioPlayerArtist)
        btnAudioShareTop = findViewById(R.id.btnAudioShareTop)

        tvLiveTimerCountdown = findViewById(R.id.tvLiveTimerCountdown)

        llCenterArtContainer = findViewById(R.id.llCenterArtContainer)
        imgVinylTonearm = findViewById(R.id.imgVinylTonearm)
        cardCenterVinylDisc = findViewById(R.id.cardCenterVinylDisc)
        imgSquareFullArt = findViewById(R.id.imgSquareFullArt)
        imgVinylSurface = findViewById(R.id.imgVinylSurface)
        cardInnerDiscPhoto = findViewById(R.id.cardInnerDiscPhoto)
        imgCenterMusicNote = findViewById(R.id.imgCenterMusicNote)
        llSquarePagerDots = findViewById(R.id.llSquarePagerDots)
        dotFlatPageArt = findViewById(R.id.dotFlatPageArt)
        dotFlatPageLyrics = findViewById(R.id.dotFlatPageLyrics)

        cardInlineLyricsStage = findViewById(R.id.cardInlineLyricsStage)
        btnFindLyricsOnline = findViewById(R.id.btnFindLyricsOnline)
        btnEditLyricsInline = findViewById(R.id.btnEditLyricsInline)
        tvInlineLyricsContent = findViewById(R.id.tvInlineLyricsContent)

        cardSlideOutTabHandle = findViewById(R.id.cardSlideOutTabHandle)
        imgSlideHandleArrow = findViewById(R.id.imgSlideHandleArrow)

        llReorderableMiddleSlots = findViewById(R.id.llReorderableMiddleSlots)
        btnAudioEqualizer = findViewById(R.id.btnAudioEqualizer)
        imgBtnEqualizer = findViewById(R.id.imgBtnEqualizer)
        btnAudioAbRepeat = findViewById(R.id.btnAudioAbRepeat)
        tvBtnAbRepeat = findViewById(R.id.tvBtnAbRepeat)
        btnAudioTimerAndSpeed = findViewById(R.id.btnAudioTimerAndSpeed)
        imgBtnTimerGauge = findViewById(R.id.imgBtnTimerGauge)
        btnAudioPocketShake = findViewById(R.id.btnAudioPocketShake)
        imgBtnPocketShake = findViewById(R.id.imgBtnPocketShake)
        btnAudioFarRightDots = findViewById(R.id.btnAudioFarRightDots)
        imgBtnMoreDots = findViewById(R.id.imgBtnMoreDots)

        tvAudioCurrentTime = findViewById(R.id.tvAudioCurrentTime)
        tvAudioTotalTime = findViewById(R.id.tvAudioTotalTime)
        imgStraightWaveformBar = findViewById(R.id.imgStraightWaveformBar)

        btnAudioShuffle = findViewById(R.id.btnAudioShuffle)
        btnAudioPrev = findViewById(R.id.btnAudioPrev)
        btnAudioPlayPause = findViewById(R.id.btnAudioPlayPause)
        btnAudioNext = findViewById(R.id.btnAudioNext)
        btnAudioRepeat = findViewById(R.id.btnAudioRepeat)

        btnAudioFavoriteBottom = findViewById(R.id.btnAudioFavoriteBottom)
        imgBtnFavoriteHeart = findViewById(R.id.imgBtnFavoriteHeart)
        tvBottomFavoriteLabel = findViewById(R.id.tvBottomFavoriteLabel)
        btnAudioPlayingQueue = findViewById(R.id.btnAudioPlayingQueue)
        imgQueueIcon = findViewById(R.id.imgQueueIcon)
        tvPlayingQueueCount = findViewById(R.id.tvPlayingQueueCount)

        viewQueueOutsideScrim = findViewById(R.id.viewQueueOutsideScrim)
        cardSlideSongDrawer = findViewById(R.id.cardSlideSongDrawer)
        tvDrawerHeaderTitle = findViewById(R.id.tvDrawerHeaderTitle)
        btnCloseSongDrawer = findViewById(R.id.btnCloseSongDrawer)
        rvSlideDrawerSongs = findViewById(R.id.rvSlideDrawerSongs)

        cardTopVolumeHud = findViewById(R.id.cardTopVolumeHud)
        imgTopVolumeSpeaker = findViewById(R.id.imgTopVolumeSpeaker)
        tvTopVolumePercent = findViewById(R.id.tvTopVolumePercent)
        imgTopVolumeBarWithDots = findViewById(R.id.imgTopVolumeBarWithDots)

        tvAudioPlayerTitle.isSelected = true
    }

    private fun applySavedGlobalTheme() {
        val themePrefs = getSharedPreferences("MAX_THEME_PREFS", Context.MODE_PRIVATE)
        val audioPrefs = getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)

        val bgHex = themePrefs.getString("app_bg_color", null)
            ?: audioPrefs.getString("app_bg_color", "#0A0A10") ?: "#0A0A10"
        val accentHex = themePrefs.getString("app_accent_color", null)
            ?: audioPrefs.getString("app_accent_color", "#00FFFF") ?: "#00FFFF"
        val wallpaperUri = themePrefs.getString("app_custom_wallpaper", null)
            ?: audioPrefs.getString("app_custom_wallpaper", null)

        try {
            rootAudioPlayerFrame.setBackgroundColor(Color.parseColor(bgHex))
            accentColorInt = Color.parseColor(accentHex)
        } catch (e: Exception) {
            accentColorInt = Color.parseColor("#00FFFF")
        }

        if (!wallpaperUri.isNullOrEmpty()) {
            imgAudioThemeWallpaper.visibility = View.VISIBLE
            viewAudioThemeDimmer.visibility = View.VISIBLE
            Glide.with(this)
                .load(Uri.parse(wallpaperUri))
                .centerCrop()
                .into(imgAudioThemeWallpaper)
        } else {
            imgAudioThemeWallpaper.visibility = View.GONE
            viewAudioThemeDimmer.visibility = View.GONE
        }

        tvDrawerHeaderTitle.setTextColor(accentColorInt)
        tvLiveTimerCountdown.setTextColor(accentColorInt)
        dotFlatPageArt.setBackgroundColor(accentColorInt)
    }

    private fun setupSleekPlayerIcons() {
        val d = resources.displayMetrics.density
        imgVinylTonearm.setImageDrawable(AudioVectorDrawable("TONEARM", d, Color.WHITE))
        imgVinylSurface.setImageDrawable(AudioVectorDrawable("VINYL_GROOVES", d, Color.WHITE))
        imgSlideHandleArrow.setImageDrawable(AudioVectorDrawable("SLIDE_HANDLE", d, accentColorInt))

        imgBtnEqualizer.setImageDrawable(AudioVectorDrawable("EQUALIZER", d, Color.WHITE))
        imgBtnTimerGauge.setImageDrawable(AudioVectorDrawable("GAUGE_TIMER", d, Color.WHITE))
        imgBtnPocketShake.setImageDrawable(AudioVectorDrawable("SHAKE_PHONE", d, Color.WHITE))
        imgBtnMoreDots.setImageDrawable(AudioVectorDrawable("THREE_DOTS", d, Color.WHITE))

        btnAudioShuffle.setImageDrawable(AudioVectorDrawable("SHUFFLE_OFF", d, Color.WHITE))
        btnAudioPrev.setImageDrawable(AudioVectorDrawable("PREV", d, Color.WHITE))
        btnAudioPlayPause.setImageDrawable(AudioVectorDrawable("PAUSE_CIRCLE", d, accentColorInt))
        btnAudioNext.setImageDrawable(AudioVectorDrawable("NEXT", d, Color.WHITE))
        btnAudioRepeat.setImageDrawable(AudioVectorDrawable("REPEAT_ALL", d, accentColorInt))

        imgBtnFavoriteHeart.setImageDrawable(AudioVectorDrawable("HEART_OUTLINE", d, Color.WHITE))
        imgQueueIcon.setImageDrawable(AudioVectorDrawable("QUEUE", d, Color.WHITE))

        imgTopVolumeSpeaker.setImageDrawable(AudioVectorDrawable("SPEAKER", d, accentColorInt))
        topVolumeBarDrawable = TopVolumeBarCleanDrawable(d, accentColorInt)
        imgTopVolumeBarWithDots.setImageDrawable(topVolumeBarDrawable)

        waveformDrawable = StraightWaveformDrawable(d, accentColorInt)
        imgStraightWaveformBar.setImageDrawable(waveformDrawable)
    }

    private fun readIncomingIntent(incomingIntent: Intent?) {
        if (incomingIntent == null) return
        val singlePath = incomingIntent.getStringExtra("AUDIO_PATH") ?: ""
        val singleTitle = incomingIntent.getStringExtra("AUDIO_TITLE") ?: File(singlePath).name
        val singleArtist = incomingIntent.getStringExtra("AUDIO_ARTIST") ?: "Unknown Singer"
        val singleAlbumId = incomingIntent.getLongExtra("AUDIO_ALBUM_ID", 0L)

        val listPaths = incomingIntent.getStringArrayListExtra("AUDIO_LIST_PATHS")
        val listTitles = incomingIntent.getStringArrayListExtra("AUDIO_LIST_TITLES")
        val listArtists = incomingIntent.getStringArrayListExtra("AUDIO_LIST_ARTISTS")
        val listAlbumIds = incomingIntent.getStringArrayListExtra("AUDIO_LIST_ALBUM_IDS")

        if (!listPaths.isNullOrEmpty()) {
            songPaths = listPaths
            songTitles = if (listTitles != null && listTitles.size == listPaths.size) {
                listTitles
            } else {
                ArrayList(listPaths.map { File(it).name })
            }
            songArtists = if (listArtists != null && listArtists.size == listPaths.size) {
                listArtists
            } else {
                ArrayList(listPaths.map { "Unknown Singer" })
            }
            songAlbumIds = if (listAlbumIds != null && listAlbumIds.size == listPaths.size) {
                ArrayList(listAlbumIds.map { it.toLongOrNull() ?: 0L })
            } else {
                ArrayList(listPaths.map { 0L })
            }
            currentTrackIndex = incomingIntent.getIntExtra("AUDIO_POSITION", 0)
                .coerceIn(0, songPaths.size - 1)
        } else if (singlePath.isNotEmpty()) {
            songPaths = arrayListOf(singlePath)
            songTitles = arrayListOf(singleTitle)
            songArtists = arrayListOf(singleArtist)
            songAlbumIds = arrayListOf(singleAlbumId)
            currentTrackIndex = 0
        }

        isShuffleEnabled = incomingIntent.getBooleanExtra("AUDIO_SHUFFLE_START", false)

        val prefs = getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
        // Pocket Shake MUST be default OFF every time the app opens so users are never annoyed
        isDoubleShakeEnabled = false
        // Default art style is Rotating Disc with Tonearm ("Danda")
        isSquareAlbumStyle = prefs.getBoolean("square_album_style", false)
        isSimpleLineSeekBar = prefs.getBoolean("simple_line_seekbar", false)
        waveformDrawable.setSimpleLineMode(isSimpleLineSeekBar)

        updatePocketShakeIcon()
        applyVinylOrSquareBoxStyle()
        setupSlideDrawerRecyclerView()
    }

    private fun setupDiscAndTonearmAnimations() {
        vinylAnimator = ObjectAnimator.ofFloat(cardCenterVinylDisc, View.ROTATION, 0f, 360f).apply {
            duration = 14000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
        }

        // Tonearm ("Danda") gentle sway animation over the rotating disc while music plays
        imgVinylTonearm.pivotX = 24f * resources.displayMetrics.density
        imgVinylTonearm.pivotY = 16f * resources.displayMetrics.density
        tonearmAnimator = ObjectAnimator.ofFloat(imgVinylTonearm, View.ROTATION, 0f, 7f, 0f).apply {
            duration = 3600L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
        }
    }

    private fun applyVinylOrSquareBoxStyle() {
        val d = resources.displayMetrics.density
        isInlineLyricsVisible = false
        cardInlineLyricsStage.visibility = View.GONE
        llCenterArtContainer.visibility = View.VISIBLE

        if (isSquareAlbumStyle) {
            imgVinylTonearm.visibility = View.GONE
            imgVinylSurface.visibility = View.GONE
            cardInnerDiscPhoto.visibility = View.GONE
            imgSquareFullArt.visibility = View.VISIBLE
            llSquarePagerDots.visibility = View.VISIBLE

            cardCenterVinylDisc.radius = 24f * d
            cardCenterVinylDisc.rotation = 0f
            vinylAnimator?.cancel()
            tonearmAnimator?.cancel()
            updateSquarePagerDots(false)
        } else {
            imgVinylTonearm.visibility = View.VISIBLE
            imgVinylSurface.visibility = View.VISIBLE
            cardInnerDiscPhoto.visibility = View.VISIBLE
            imgSquareFullArt.visibility = View.GONE
            llSquarePagerDots.visibility = View.GONE

            cardCenterVinylDisc.radius = 126f * d
            if (exoPlayer?.isPlaying == true) {
                vinylAnimator?.start()
                tonearmAnimator?.start()
            }
        }
        loadSongPhotoIntoDiscAndSquare(currentTrackIndex)
    }

    private fun updateSquarePagerDots(showingLyrics: Boolean) {
        dotFlatPageArt.setBackgroundColor(if (!showingLyrics) accentColorInt else Color.parseColor("#44FFFFFF"))
        dotFlatPageLyrics.setBackgroundColor(if (showingLyrics) accentColorInt else Color.parseColor("#44FFFFFF"))
    }

    private fun loadSongPhotoIntoDiscAndSquare(index: Int) {
        if (index !in songPaths.indices) return
        val path = songPaths[index]
        val albumId = songAlbumIds.getOrElse(index) { 0L }
        val prefs = getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
        val customPhotoStr = prefs.getString("custom_art_$path", null)

        val photoUri: Uri? = when {
            !customPhotoStr.isNullOrEmpty() -> Uri.parse(customPhotoStr)
            albumId > 0L -> ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                albumId
            )
            else -> null
        }

        if (photoUri != null) {
            imgCenterMusicNote.setPadding(0, 0, 0, 0)
            imgCenterMusicNote.clearColorFilter()
            Glide.with(this)
                .load(photoUri)
                .placeholder(android.R.drawable.ic_lock_silent_mode_off)
                .error(android.R.drawable.ic_lock_silent_mode_off)
                .centerCrop()
                .into(imgCenterMusicNote)

            Glide.with(this)
                .load(photoUri)
                .placeholder(android.R.drawable.ic_lock_silent_mode_off)
                .error(android.R.drawable.ic_lock_silent_mode_off)
                .centerCrop()
                .into(imgSquareFullArt)
        } else {
            val pad = (26 * resources.displayMetrics.density).toInt()
            imgCenterMusicNote.setPadding(pad, pad, pad, pad)
            imgCenterMusicNote.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
            imgCenterMusicNote.setColorFilter(accentColorInt)

            imgSquareFullArt.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
            imgSquareFullArt.setColorFilter(accentColorInt)
        }
    }

    private fun initializeAudioPlayer(resumeIfSame: Boolean) {
        try {
            if (sharedPlayer == null) {
                val audioAttrs = androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build()

                sharedPlayer = ExoPlayer.Builder(applicationContext)
                    .setAudioAttributes(audioAttrs, true)
                    .setHandleAudioBecomingNoisy(true)
                    .build()
            }
            exoPlayer = sharedPlayer

            exoPlayer?.let { player ->
                val targetPath = songPaths.getOrElse(currentTrackIndex) { "" }
                val alreadyPlayingSame = resumeIfSame && targetPath.isNotEmpty() && targetPath == activeSongPath && player.mediaItemCount > 0

                if (!alreadyPlayingSame && songPaths.isNotEmpty()) {
                    val mediaItems = songPaths.mapIndexed { idx, path ->
                        val uri = if (path.startsWith("content://") || path.startsWith("http")) {
                            Uri.parse(path)
                        } else {
                            Uri.fromFile(File(path))
                        }
                        val title = songTitles.getOrElse(idx) { File(path).name }
                        val artist = songArtists.getOrElse(idx) { "Unknown Singer" }
                        MediaItem.Builder()
                            .setUri(uri)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(title)
                                    .setArtist(artist)
                                    .build()
                            )
                            .build()
                    }
                    player.setMediaItems(mediaItems, currentTrackIndex, 0L)
                    player.shuffleModeEnabled = isShuffleEnabled
                    player.repeatMode = repeatModeState
                    player.prepare()
                    player.playWhenReady = true
                }

                updateCurrentSongMetadata(currentTrackIndex)
                updateShuffleAndRepeatIcons()
                updatePlayPauseIcon(player.isPlaying)
                syncDiscAndTonearmAnimationState(player.isPlaying)
                updateMediaNotification()

                player.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        updatePlayPauseIcon(isPlaying)
                        syncDiscAndTonearmAnimationState(isPlaying)
                        updateMediaNotification()
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val newIdx = player.currentMediaItemIndex
                        if (newIdx in songPaths.indices) {
                            currentTrackIndex = newIdx
                            pointA = -1L
                            pointB = -1L
                            tvBtnAbRepeat.text = "A⇄B"
                            tvBtnAbRepeat.setTextColor(Color.WHITE)
                            updateCurrentSongMetadata(currentTrackIndex)
                            rvSlideDrawerSongs.adapter?.notifyDataSetChanged()
                            updateMediaNotification()
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            tvAudioTotalTime.text = formatDuration(player.duration.coerceAtLeast(0L))
                            updateMediaNotification()
                        }
                    }
                })
            }

            handler.removeCallbacks(progressUpdateRunnable)
            handler.post(progressUpdateRunnable)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to play audio file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun syncDiscAndTonearmAnimationState(isPlaying: Boolean) {
        if (!isSquareAlbumStyle) {
            if (isPlaying) {
                if (vinylAnimator?.isStarted == true) vinylAnimator?.resume() else vinylAnimator?.start()
                if (tonearmAnimator?.isStarted == true) tonearmAnimator?.resume() else tonearmAnimator?.start()
            } else {
                vinylAnimator?.pause()
                tonearmAnimator?.pause()
                imgVinylTonearm.rotation = -14f
            }
        }
    }

    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        val d = resources.displayMetrics.density
        btnAudioPlayPause.setImageDrawable(
            AudioVectorDrawable(
                if (isPlaying) "PAUSE_CIRCLE" else "PLAY_CIRCLE",
                d,
                if (isPlaying) accentColorInt else Color.WHITE
            )
        )
    }

    private fun updateCurrentSongMetadata(index: Int) {
        if (index !in songPaths.indices) return
        val path = songPaths[index]
        val title = songTitles.getOrElse(index) { File(path).name }
        val artist = songArtists.getOrElse(index) { "Unknown Singer" }

        activeSongPath = path
        activeSongTitle = title
        activeSongArtist = artist

        tvAudioPlayerTitle.text = title
        tvAudioPlayerArtist.text = artist

        val currentNumber = index + 1
        val totalSongs = songPaths.size
        tvPlayingQueueCount.text = "Playing Queue ($currentNumber/$totalSongs)"
        tvDrawerHeaderTitle.text = "Playing Queue ($currentNumber/$totalSongs)"

        waveformDrawable.setSeedFromSong(title.hashCode())
        loadSongPhotoIntoDiscAndSquare(index)
        refreshInlineLyricsText()

        val prefs = getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("last_audio_path", path)
            .putString("last_audio_title", title)
            .putString("last_audio_artist", artist)
            .apply()

        isFavorite = prefs.getBoolean("fav_audio_$path", false)
        updateFavoriteIcon()
    }

    private fun updateFavoriteIcon() {
        val d = resources.displayMetrics.density
        imgBtnFavoriteHeart.setImageDrawable(
            AudioVectorDrawable(
                if (isFavorite) "HEART_FILLED" else "HEART_OUTLINE",
                d,
                if (isFavorite) Color.parseColor("#FF2E63") else Color.WHITE
            )
        )
        tvBottomFavoriteLabel.text = if (isFavorite) "Favorited ♥" else "Add to Favorite"
        tvBottomFavoriteLabel.setTextColor(
            if (isFavorite) Color.parseColor("#FF2E63") else Color.WHITE
        )
    }

    private fun updatePocketShakeIcon() {
        val d = resources.displayMetrics.density
        imgBtnPocketShake.setImageDrawable(
            AudioVectorDrawable(
                "SHAKE_PHONE",
                d,
                if (isDoubleShakeEnabled) accentColorInt else Color.WHITE
            )
        )
    }

    private fun setupWaveformSeekTouch() {
        imgStraightWaveformBar.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                    val width = view.width.toFloat().coerceAtLeast(1f)
                    val fraction = (event.x / width).coerceIn(0f, 1f)
                    val dur = exoPlayer?.duration?.coerceAtLeast(0L) ?: 0L
                    val seekTarget = (dur * fraction).toLong()
                    waveformDrawable.updateProgress(fraction, pointA, pointB, dur)
                    tvAudioCurrentTime.text = formatDuration(seekTarget)
                    if (event.action == MotionEvent.ACTION_UP) {
                        exoPlayer?.seekTo(seekTarget)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupScreenVolumeAndDrawerGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (e1 == null) return false
                if (abs(distanceY) > abs(distanceX)) {
                    adjustVolumeWithTopCameraHud(distanceY)
                    return true
                }
                return false
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y

                if (abs(diffX) > abs(diffY) && abs(diffX) > 100) {
                    val screenWidth = resources.displayMetrics.widthPixels
                    // Right-to-Left swipe from the right edge slides out the Playing Queue
                    if (diffX < 0 && e1.x > screenWidth * 0.72f) {
                        openSongDrawerBoxSmooth()
                        return true
                    }
                    // If Square Album Mode is active, swiping Right-to-Left opens Lyrics automatically!
                    if (isSquareAlbumStyle) {
                        if (diffX < 0 && !isInlineLyricsVisible) {
                            showInlineLyricsPanel(true)
                            return true
                        } else if (diffX > 0 && isInlineLyricsVisible) {
                            showInlineLyricsPanel(false)
                            return true
                        }
                    }
                    if (diffX < 0) {
                        playNextTrack()
                    } else {
                        playPreviousTrack()
                    }
                    return true
                }
                return false
            }
        })

        rootAudioPlayerFrame.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        findViewById<View>(R.id.flCenterVinylStage).setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun showInlineLyricsPanel(showLyrics: Boolean) {
        isInlineLyricsVisible = showLyrics
        if (showLyrics) {
            llCenterArtContainer.visibility = View.GONE
            cardInlineLyricsStage.visibility = View.VISIBLE
            refreshInlineLyricsText()
            // Auto-search online if no local lyrics exist yet
            val path = songPaths.getOrElse(currentTrackIndex) { "" }
            val saved = getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
                .getString("lyrics_$path", null)
            if (saved.isNullOrEmpty()) {
                fetchLyricsOnlineAutomatically()
            }
        } else {
            cardInlineLyricsStage.visibility = View.GONE
            llCenterArtContainer.visibility = View.VISIBLE
            updateSquarePagerDots(false)
        }
    }

    private fun refreshInlineLyricsText() {
        val path = songPaths.getOrElse(currentTrackIndex) { "" }
        val title = songTitles.getOrElse(currentTrackIndex) { "Song" }
        val prefs = getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
        val savedLyrics = prefs.getString("lyrics_$path", null)

        val lrcFile = if (path.isNotEmpty() && !path.startsWith("content://")) {
            File("${path.substringBeforeLast(".")}.lrc")
        } else {
            null
        }

        val textToShow = when {
            lrcFile != null && lrcFile.exists() -> lrcFile.readText()
            !savedLyrics.isNullOrEmpty() -> savedLyrics
            else -> "No saved lyrics for '$title'.\n\nTap 'Find Online' above to search lyrics automatically over the internet, or tap 'Edit' to paste your own."
        }
        tvInlineLyricsContent.text = textToShow
    }

    private fun fetchLyricsOnlineAutomatically() {
        val path = songPaths.getOrElse(currentTrackIndex) { "" }
        val rawTitle = songTitles.getOrElse(currentTrackIndex) { "" }
            .substringBeforeLast(".")
            .replace("_", " ")
            .trim()
        val rawArtist = songArtists.getOrElse(currentTrackIndex) { "" }.trim()

        if (rawTitle.isEmpty()) return
        tvInlineLyricsContent.text = "Searching lyrics online for '$rawTitle'..."

        thread {
            var foundLyrics: String? = null
            try {
                val cleanArtist = if (rawArtist.equals("Unknown Singer", true) || rawArtist.equals("Unknown", true)) {
                    ""
                } else {
                    rawArtist
                }
                val queryUrl = if (cleanArtist.isNotEmpty()) {
                    "https://lrclib.net/api/search?q=${URLEncoder.encode("$rawTitle $cleanArtist", "UTF-8")}"
                } else {
                    "https://lrclib.net/api/search?q=${URLEncoder.encode(rawTitle, "UTF-8")}"
                }
                val conn = (URL(queryUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                    requestMethod = "GET"
                }
                if (conn.responseCode == 200) {
                    val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonArr = org.json.JSONArray(responseStr)
                    if (jsonArr.length() > 0) {
                        val firstObj: JSONObject = jsonArr.getJSONObject(0)
                        val plain = firstObj.optString("plainLyrics", "")
                        if (plain.isNotEmpty()) {
                            foundLyrics = plain
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (!foundLyrics.isNullOrEmpty()) {
                    getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
                        .edit()
                        .putString("lyrics_$path", foundLyrics)
                        .apply()
                    tvInlineLyricsContent.text = foundLyrics
                    Toast.makeText(this, "Online Lyrics Loaded!", Toast.LENGTH_SHORT).show()
                } else {
                    tvInlineLyricsContent.text = "Could not auto-fetch exact lyrics for '$rawTitle'.\nTap 'Find Online' again to open Google Lyrics Search or tap 'Edit' to paste."
                }
            }
        }
    }

    private fun adjustVolumeWithTopCameraHud(distanceY: Float) {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val step = if (distanceY > 0) 1 else -1
        val newVol = (curVol + step).coerceIn(0, maxVol)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
        showTopCameraVolumeHud(newVol, maxVol)
    }

    private fun showTopCameraVolumeHud(currentVol: Int, maxVol: Int) {
        val percent = ((currentVol.toFloat() / maxVol.toFloat()) * 100).toInt().coerceIn(0, 100)
        tvTopVolumePercent.text = "$percent%"
        topVolumeBarDrawable.setProgressPercent(percent)
        cardTopVolumeHud.visibility = View.VISIBLE
        handler.removeCallbacks(hideVolumeHudRunnable)
        handler.postDelayed(hideVolumeHudRunnable, 1200)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
            showTopCameraVolumeHud(
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
                audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            )
            return true
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
            showTopCameraVolumeHud(
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
                audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            )
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun setupAllButtonClicks() {
        btnAudioMinimize.setOnClickListener {
            animateIconTap(it)
            finish()
        }

        btnAudioShareTop.setOnClickListener {
            animateIconTap(it)
            shareCurrentSong()
        }

        cardSlideOutTabHandle.setOnClickListener {
            animateIconTap(it)
            openSongDrawerBoxSmooth()
        }

        // Clicking X button OR clicking anywhere outside on the display closes Playing Queue
        btnCloseSongDrawer.setOnClickListener {
            closeSongDrawerBoxSmooth()
        }
        viewQueueOutsideScrim.setOnClickListener {
            closeSongDrawerBoxSmooth()
        }

        // Built-In Own Equalizer (Strictly never launches external phone equalizer)
        btnAudioEqualizer.setOnClickListener {
            animateIconTap(imgBtnEqualizer)
            showBuiltInFiveBandEqualizerDialog()
        }

        btnAudioAbRepeat.setOnClickListener {
            animateIconTap(tvBtnAbRepeat)
            cycleAbRepeatMode()
        }

        btnAudioTimerAndSpeed.setOnClickListener {
            animateIconTap(imgBtnTimerGauge)
            showLiveSleepTimerAndSpeedDialog()
        }

        tvLiveTimerCountdown.setOnClickListener {
            showLiveSleepTimerAndSpeedDialog()
        }

        // Middle Row Pocket Shake Toggle (Default OFF, toggles ON/OFF cleanly)
        btnAudioPocketShake.setOnClickListener {
            animateIconTap(imgBtnPocketShake)
            toggleDoubleShakeMode()
        }

        // Bottom-Left Favorite Button (Replaced Lyrics at Bottom-Left)
        btnAudioFavoriteBottom.setOnClickListener {
            animateIconTap(imgBtnFavoriteHeart)
            if (currentTrackIndex !in songPaths.indices) return@setOnClickListener
            val path = songPaths[currentTrackIndex]
            isFavorite = !isFavorite
            getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("fav_audio_$path", isFavorite)
                .apply()
            updateFavoriteIcon()
            Toast.makeText(
                this,
                if (isFavorite) "Added to Favorites ♥" else "Removed from Favorites",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnAudioFarRightDots.setOnClickListener {
            animateIconTap(imgBtnMoreDots)
            showPremiumWhiteThreeDotsDialog()
        }

        btnAudioPlayPause.setOnClickListener {
            animateIconTap(it)
            exoPlayer?.let { player ->
                if (player.isPlaying) player.pause() else player.play()
                updateMediaNotification()
            }
        }

        btnAudioPrev.setOnClickListener {
            animateIconTap(it)
            playPreviousTrack()
        }

        btnAudioNext.setOnClickListener {
            animateIconTap(it)
            playNextTrack()
        }

        btnAudioShuffle.setOnClickListener {
            animateIconTap(it)
            isShuffleEnabled = !isShuffleEnabled
            exoPlayer?.shuffleModeEnabled = isShuffleEnabled
            updateShuffleAndRepeatIcons()
            updateMediaNotification()
            Toast.makeText(
                this,
                if (isShuffleEnabled) "Shuffle Play: ON" else "Shuffle Play: OFF",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnAudioRepeat.setOnClickListener {
            animateIconTap(it)
            repeatModeState = when (repeatModeState) {
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
                else -> Player.REPEAT_MODE_ALL
            }
            exoPlayer?.repeatMode = repeatModeState
            updateShuffleAndRepeatIcons()
            val modeText = when (repeatModeState) {
                Player.REPEAT_MODE_ONE -> "Repeat Current Song (1)"
                Player.REPEAT_MODE_OFF -> "Repeat OFF"
                else -> "Loop All Songs"
            }
            Toast.makeText(this, modeText, Toast.LENGTH_SHORT).show()
        }

        btnAudioPlayingQueue.setOnClickListener {
            animateIconTap(it)
            openSongDrawerBoxSmooth()
        }

        btnFindLyricsOnline.setOnClickListener {
            val title = songTitles.getOrElse(currentTrackIndex) { "" }.substringBeforeLast(".")
            val artist = songArtists.getOrElse(currentTrackIndex) { "" }
            fetchLyricsOnlineAutomatically()
            try {
                val q = URLEncoder.encode("$title $artist lyrics", "UTF-8")
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$q")))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        btnEditLyricsInline.setOnClickListener {
            showEditLyricsDialog()
        }

        dotFlatPageArt.setOnClickListener {
            if (isSquareAlbumStyle) showInlineLyricsPanel(false)
        }
        dotFlatPageLyrics.setOnClickListener {
            if (isSquareAlbumStyle) showInlineLyricsPanel(true)
        }
    }

    private fun animateIconTap(view: View) {
        view.animate()
            .scaleX(0.84f)
            .scaleY(0.84f)
            .setDuration(80)
            .withEndAction {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    // Smooth Right-to-Left Slide Animation for Playing Queue
    private fun openSongDrawerBoxSmooth() {
        val currentNum = (currentTrackIndex + 1).coerceAtMost(songPaths.size.coerceAtLeast(1))
        tvDrawerHeaderTitle.text = "Playing Queue ($currentNum/${songPaths.size})"
        viewQueueOutsideScrim.visibility = View.VISIBLE
        cardSlideSongDrawer.visibility = View.VISIBLE
        cardSlideSongDrawer.translationX = 340f * resources.displayMetrics.density
        cardSlideSongDrawer.animate()
            .translationX(0f)
            .setDuration(240)
            .start()
        rvSlideDrawerSongs.scrollToPosition(currentTrackIndex.coerceAtLeast(0))
    }

    private fun closeSongDrawerBoxSmooth() {
        cardSlideSongDrawer.animate()
            .translationX(340f * resources.displayMetrics.density)
            .setDuration(200)
            .withEndAction {
                cardSlideSongDrawer.visibility = View.GONE
                viewQueueOutsideScrim.visibility = View.GONE
            }
            .start()
    }

    // Playing Queue RecyclerView: Compact 44dp rows so 10-11 songs fit cleanly + Singer Name below each song
    private fun setupSlideDrawerRecyclerView() {
        val d = resources.displayMetrics.density
        rvSlideDrawerSongs.layoutManager = LinearLayoutManager(this)
        rvSlideDrawerSongs.adapter = object : RecyclerView.Adapter<DrawerSongVH>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DrawerSongVH {
                val row = LinearLayout(this@AudioPlayerActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((8 * d).toInt(), (5 * d).toInt(), (8 * d).toInt(), (5 * d).toInt())
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (44 * d).toInt()
                    )
                }
                val tvIndex = TextView(this@AudioPlayerActivity).apply {
                    textSize = 11.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, 0, (10 * d).toInt(), 0)
                }
                val textCol = LinearLayout(this@AudioPlayerActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                val tvName = TextView(this@AudioPlayerActivity).apply {
                    textSize = 12.5f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                }
                val tvSinger = TextView(this@AudioPlayerActivity).apply {
                    textSize = 10f
                    maxLines = 1
                }
                textCol.addView(tvName)
                textCol.addView(tvSinger)
                row.addView(tvIndex)
                row.addView(textCol)
                return DrawerSongVH(row, tvIndex, tvName, tvSinger)
            }

            override fun onBindViewHolder(holder: DrawerSongVH, position: Int) {
                val title = songTitles.getOrElse(position) { "Track ${position + 1}" }
                val singer = songArtists.getOrElse(position) { "Unknown Singer" }
                val isCurrent = position == currentTrackIndex

                holder.tvIndex.text = "${position + 1}."
                holder.tvName.text = title
                holder.tvSinger.text = singer

                holder.tvIndex.setTextColor(if (isCurrent) accentColorInt else Color.parseColor("#9999AA"))
                holder.tvName.setTextColor(if (isCurrent) accentColorInt else Color.WHITE)
                holder.tvSinger.setTextColor(if (isCurrent) accentColorInt else Color.parseColor("#AAAA88"))

                holder.itemView.setOnClickListener {
                    currentTrackIndex = position
                    exoPlayer?.seekToDefaultPosition(position)
                    exoPlayer?.play()
                    updateCurrentSongMetadata(position)
                    updateMediaNotification()
                    notifyDataSetChanged()
                }
            }

            override fun getItemCount(): Int = songPaths.size
        }
    }

    private class DrawerSongVH(
        view: View,
        val tvIndex: TextView,
        val tvName: TextView,
        val tvSinger: TextView
    ) : RecyclerView.ViewHolder(view)

    private fun playNextTrack() {
        exoPlayer?.let { player ->
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.play()
            } else if (songPaths.isNotEmpty()) {
                player.seekToDefaultPosition(0)
                player.play()
            }
            updateMediaNotification()
        }
    }

    private fun playPreviousTrack() {
        exoPlayer?.let { player ->
            if (player.hasPreviousMediaItem()) {
                player.seekToPreviousMediaItem()
                player.play()
            } else {
                player.seekTo(0)
                player.play()
            }
            updateMediaNotification()
        }
    }

    private fun updateShuffleAndRepeatIcons() {
        val d = resources.displayMetrics.density
        btnAudioShuffle.setImageDrawable(
            AudioVectorDrawable(
                if (isShuffleEnabled) "SHUFFLE_ON" else "SHUFFLE_OFF",
                d,
                if (isShuffleEnabled) accentColorInt else Color.WHITE
            )
        )
        val repeatColor = if (repeatModeState == Player.REPEAT_MODE_OFF) {
            Color.WHITE
        } else {
            accentColorInt
        }
        val repeatType = when (repeatModeState) {
            Player.REPEAT_MODE_ONE -> "REPEAT_ONE"
            Player.REPEAT_MODE_OFF -> "REPEAT_OFF"
            else -> "REPEAT_ALL"
        }
        btnAudioRepeat.setImageDrawable(AudioVectorDrawable(repeatType, d, repeatColor))
    }

    private fun cycleAbRepeatMode() {
        val currentPos = exoPlayer?.currentPosition ?: 0L
        when {
            pointA == -1L -> {
                pointA = currentPos
                tvBtnAbRepeat.text = "A•"
                tvBtnAbRepeat.setTextColor(accentColorInt)
                Toast.makeText(this, "Point A set at ${formatDuration(pointA)}", Toast.LENGTH_SHORT).show()
            }
            pointB == -1L -> {
                if (currentPos > pointA + 1000L) {
                    pointB = currentPos
                    tvBtnAbRepeat.text = "A⇄B"
                    tvBtnAbRepeat.setTextColor(Color.parseColor("#00E676"))
                    Toast.makeText(this, "Looping A to B", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Point B must be after Point A", Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                pointA = -1L
                pointB = -1L
                tvBtnAbRepeat.text = "A⇄B"
                tvBtnAbRepeat.setTextColor(Color.WHITE)
                Toast.makeText(this, "A-B Repeat Cleared", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showLiveSleepTimerAndSpeedDialog() {
        val options = arrayOf(
            "Sleep Timer: OFF",
            "Sleep Timer: 10 Minutes (Live Clock)",
            "Sleep Timer: 15 Minutes (Live Clock)",
            "Sleep Timer: 30 Minutes (Live Clock)",
            "Sleep Timer: 45 Minutes (Live Clock)",
            "Sleep Timer: 60 Minutes (Live Clock)",
            "Custom Minutes Sleep Timer...",
            "Playback Speed: 0.75x",
            "Playback Speed: 1.0x (Normal)",
            "Playback Speed: 1.25x",
            "Playback Speed: 1.5x",
            "Playback Speed: 2.0x"
        )

        MusicFragment.showBrightStyledMenuDialog(this, "Live Sleep Timer & Audio Speed", options) { which ->
            when (which) {
                0 -> cancelSleepTimer()
                1 -> startLiveSleepTimer(10)
                2 -> startLiveSleepTimer(15)
                3 -> startLiveSleepTimer(30)
                4 -> startLiveSleepTimer(45)
                5 -> startLiveSleepTimer(60)
                6 -> promptCustomSleepMinutes()
                7 -> applyAudioSpeed(0.75f)
                8 -> applyAudioSpeed(1.0f)
                9 -> applyAudioSpeed(1.25f)
                10 -> applyAudioSpeed(1.5f)
                11 -> applyAudioSpeed(2.0f)
            }
        }
    }

    private fun promptCustomSleepMinutes() {
        val input = EditText(this).apply {
            hint = "Enter minutes (e.g. 30)"
            setHintTextColor(Color.parseColor("#888899"))
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE)
            setPadding(45, 35, 45, 35)
        }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Set Custom Timer Minutes")
            .setView(input)
            .setPositiveButton("Start Timer") { _, _ ->
                val mins = input.text.toString().trim().toLongOrNull() ?: 0L
                if (mins > 0L) {
                    startLiveSleepTimer(mins)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startLiveSleepTimer(minutes: Long) {
        sleepTimer?.cancel()
        val totalMillis = minutes * 60L * 1000L
        tvLiveTimerCountdown.visibility = View.VISIBLE
        imgBtnTimerGauge.setImageDrawable(
            AudioVectorDrawable("GAUGE_TIMER", resources.displayMetrics.density, accentColorInt)
        )

        sleepTimer = object : CountDownTimer(totalMillis, 40L) {
            override fun onTick(millisUntilFinished: Long) {
                val mins = millisUntilFinished / 60000L
                val secs = (millisUntilFinished % 60000L) / 1000L
                val hundredths = (millisUntilFinished % 1000L) / 10L
                tvLiveTimerCountdown.text = String.format(
                    Locale.US,
                    "⏱ %02d:%02d.%02d",
                    mins,
                    secs,
                    hundredths
                )
            }

            override fun onFinish() {
                exoPlayer?.pause()
                tvLiveTimerCountdown.visibility = View.GONE
                imgBtnTimerGauge.setImageDrawable(
                    AudioVectorDrawable("GAUGE_TIMER", resources.displayMetrics.density, Color.WHITE)
                )
                updateMediaNotification()
                Toast.makeText(
                    this@AudioPlayerActivity,
                    "Sleep Timer Finished • Music Paused",
                    Toast.LENGTH_LONG
                ).show()
            }
        }.start()

        Toast.makeText(this, "Live Timer started for $minutes min", Toast.LENGTH_SHORT).show()
    }

    private fun cancelSleepTimer() {
        sleepTimer?.cancel()
        tvLiveTimerCountdown.visibility = View.GONE
        imgBtnTimerGauge.setImageDrawable(
            AudioVectorDrawable("GAUGE_TIMER", resources.displayMetrics.density, Color.WHITE)
        )
        Toast.makeText(this, "Sleep Timer turned OFF", Toast.LENGTH_SHORT).show()
    }

    private fun applyAudioSpeed(speed: Float) {
        currentSpeed = speed
        exoPlayer?.playbackParameters = PlaybackParameters(currentSpeed)
        Toast.makeText(this, "Playback Speed: ${currentSpeed}x", Toast.LENGTH_SHORT).show()
    }

    // 3-Dots Menu with Permanent Song Photo Picker + Bottom Reorder Icons & Seekbar Style Switch
    private fun showPremiumWhiteThreeDotsDialog() {
        val path = songPaths.getOrElse(currentTrackIndex) { "" }
        val title = songTitles.getOrElse(currentTrackIndex) { "Song" }

        val menuItems = arrayOf(
            "🖼 Set Custom Photo on This Song (From Gallery)",
            if (isSquareAlbumStyle) "💿 Switch to Rotating Disc with Tonearm" else "🟦 Switch to Square Album Box (With Swipe Lyrics)",
            "📝 View / Search Lyrics Online",
            "➕ Add Song to Playlist",
            "🎛 Built-in 5-Band Equalizer & Bass Boost",
            "⏱ Live Sleep Timer & Playback Speed (${currentSpeed}x)",
            if (isDoubleShakeEnabled) "📳 Pocket Shake Song Change: ON (Tap to Turn OFF)" else "📳 Pocket Shake Song Change: OFF (Tap to Turn ON)",
            "📤 Share Current Song",
            "ℹ️ Song Details",
            "🔄 Change / Swap Middle 4 Icon Positions",
            if (isSimpleLineSeekBar) "📊 Switch SeekBar to Vertical Waveform Bars" else "➖ Switch SeekBar to Simple Straight Line"
        )

        MusicFragment.showBrightStyledMenuDialog(this, "Audio Player Options", menuItems) { which ->
            when (which) {
                0 -> pickCustomPhotoFromLibrary()
                1 -> {
                    isSquareAlbumStyle = !isSquareAlbumStyle
                    getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("square_album_style", isSquareAlbumStyle)
                        .apply()
                    applyVinylOrSquareBoxStyle()
                }
                2 -> {
                    if (isSquareAlbumStyle) {
                        showInlineLyricsPanel(true)
                    } else {
                        showEditLyricsDialog()
                    }
                }
                3 -> {
                    if (path.isNotEmpty()) {
                        AudioAdapter.showAddToPlaylistDialog(this, path, title)
                    }
                }
                4 -> showBuiltInFiveBandEqualizerDialog()
                5 -> showLiveSleepTimerAndSpeedDialog()
                6 -> toggleDoubleShakeMode()
                7 -> shareCurrentSong()
                8 -> {
                    val f = File(path)
                    val sizeMb = if (f.exists()) f.length() / (1024.0 * 1024.0) else 0.0
                    AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Song Details")
                        .setMessage(
                            "Title: $title\n\n" +
                                "Singer: ${tvAudioPlayerArtist.text}\n\n" +
                                "Duration: ${tvAudioTotalTime.text}\n\n" +
                                "Size: ${String.format(Locale.US, "%.2f MB", sizeMb)}\n\n" +
                                "Path: $path"
                        )
                        .setPositiveButton("Close", null)
                        .show()
                }
                9 -> showReorderMiddleIconsDialog()
                10 -> {
                    isSimpleLineSeekBar = !isSimpleLineSeekBar
                    getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("simple_line_seekbar", isSimpleLineSeekBar)
                        .apply()
                    waveformDrawable.setSimpleLineMode(isSimpleLineSeekBar)
                    Toast.makeText(
                        this,
                        if (isSimpleLineSeekBar) "Simple Straight Line SeekBar Active" else "Waveform Bars SeekBar Active",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // Pick Photo from Library and Save Permanently for the Current Song
    private fun pickCustomPhotoFromLibrary() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
            }
            startActivityForResult(intent, REQ_PICK_SONG_PHOTO)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open photo library", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_SONG_PHOTO && resultCode == Activity.RESULT_OK) {
            val pickedUri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(
                    pickedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val currentPath = songPaths.getOrElse(currentTrackIndex) { "" }
            if (currentPath.isNotEmpty()) {
                getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
                    .edit()
                    .putString("custom_art_$currentPath", pickedUri.toString())
                    .apply()
                loadSongPhotoIntoDiscAndSquare(currentTrackIndex)
                Toast.makeText(this, "Photo saved permanently for this song!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Reorder / Replace Positions of the 4 Middle Icons (Equalizer, A-B, Timer, Pocket Shake)
    private fun showReorderMiddleIconsDialog() {
        val layouts = arrayOf(
            "1. Equalizer  |  A-B  |  Timer  |  Shake (Default)",
            "2. Shake  |  Timer  |  A-B  |  Equalizer (Reversed)",
            "3. Timer  |  Equalizer  |  Shake  |  A-B",
            "4. A-B  |  Shake  |  Equalizer  |  Timer"
        )
        val orderCodes = arrayOf("0,1,2,3", "3,2,1,0", "2,0,3,1", "1,3,0,2")

        MusicFragment.showBrightStyledMenuDialog(
            this,
            "Swap / Reorder Middle 4 Icons",
            layouts
        ) { which ->
            getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
                .edit()
                .putString("middle_icons_order", orderCodes[which])
                .apply()
            applyMiddleButtonsOrder()
            Toast.makeText(this, "Icon positions updated!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyMiddleButtonsOrder() {
        val orderStr = getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
            .getString("middle_icons_order", "0,1,2,3") ?: "0,1,2,3"
        val indices = orderStr.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (indices.size != 4) return

        val mapViews = mapOf(
            0 to btnAudioEqualizer,
            1 to btnAudioAbRepeat,
            2 to btnAudioTimerAndSpeed,
            3 to btnAudioPocketShake
        )
        llReorderableMiddleSlots.removeAllViews()
        indices.forEach { idx ->
            mapViews[idx]?.let { view ->
                llReorderableMiddleSlots.addView(view)
            }
        }
    }

    // 100% Built-in Custom 5-Band Equalizer + Bass Boost (Never opens external system app)
    private fun showBuiltInFiveBandEqualizerDialog() {
        val sessionId = exoPlayer?.audioSessionId ?: 0
        try {
            if (sharedEqualizer == null && sessionId != 0) {
                sharedEqualizer = Equalizer(0, sessionId).apply { enabled = true }
            }
            if (sharedBassBoost == null && sessionId != 0) {
                sharedBassBoost = BassBoost(0, sessionId).apply { enabled = true }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val d = resources.displayMetrics.density
        val rootCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * d).toInt(), (18 * d).toInt(), (18 * d).toInt(), (16 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141424"))
                cornerRadius = 22f * d
                setStroke((1.5f * d).toInt(), accentColorInt)
            }
        }

        val tvHeader = TextView(this).apply {
            text = "MAX Built-in 5-Band Equalizer"
            setTextColor(accentColorInt)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (10 * d).toInt())
        }
        rootCard.addView(tvHeader)

        val eq = sharedEqualizer
        if (eq != null) {
            val numPresets = eq.numberOfPresets.toInt()
            if (numPresets > 0) {
                val presetNames = Array(numPresets) { i -> eq.getPresetName(i.toShort()) }
                val btnPreset = TextView(this).apply {
                    text = "🎵 Tap to Select Preset (Pop / Rock / Heavy Bass)"
                    setTextColor(Color.WHITE)
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#24243A"))
                        cornerRadius = 12f * d
                    }
                }
                rootCard.addView(btnPreset)

                val bandsCount = eq.numberOfBands.toInt().coerceAtMost(5)
                val range = eq.bandLevelRange
                val minLevel = range[0].toInt()
                val maxLevel = range[1].toInt()
                val bandSeekBars = mutableListOf<SeekBar>()

                for (b in 0 until bandsCount) {
                    val bandIndex = b.toShort()
                    val freqHz = eq.getCenterFreq(bandIndex) / 1000
                    val freqLabel = if (freqHz >= 1000) "${freqHz / 1000} kHz" else "$freqHz Hz"

                    val tvBand = TextView(this).apply {
                        text = "Band ${b + 1} ($freqLabel)"
                        setTextColor(Color.parseColor("#CCCCDD"))
                        textSize = 12f
                        setPadding(0, (8 * d).toInt(), 0, (2 * d).toInt())
                    }
                    val sbBand = SeekBar(this).apply {
                        max = (maxLevel - minLevel).coerceAtLeast(100)
                        progress = (eq.getBandLevel(bandIndex).toInt() - minLevel).coerceIn(0, max)
                        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                                if (fromUser) {
                                    try {
                                        eq.setBandLevel(bandIndex, (minLevel + p).toShort())
                                    } catch (ex: Exception) {
                                        ex.printStackTrace()
                                    }
                                }
                            }
                            override fun onStartTrackingTouch(s: SeekBar?) {}
                            override fun onStopTrackingTouch(s: SeekBar?) {}
                        })
                    }
                    bandSeekBars.add(sbBand)
                    rootCard.addView(tvBand)
                    rootCard.addView(sbBand)
                }

                btnPreset.setOnClickListener {
                    MusicFragment.showBrightStyledMenuDialog(this, "Choose Sound Preset", presetNames) { idx ->
                        try {
                            eq.usePreset(idx.toShort())
                            for (b in 0 until bandSeekBars.size) {
                                val lvl = eq.getBandLevel(b.toShort()).toInt()
                                bandSeekBars[b].progress = (lvl - minLevel).coerceIn(0, bandSeekBars[b].max)
                            }
                            Toast.makeText(this, "Preset: ${presetNames[idx]}", Toast.LENGTH_SHORT).show()
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                        }
                    }
                }
            }
        }

        val tvBass = TextView(this).apply {
            text = "Sub-Woofer Bass Boost"
            setTextColor(accentColorInt)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, (12 * d).toInt(), 0, (4 * d).toInt())
        }
        val sbBass = SeekBar(this).apply {
            max = 1000
            progress = try {
                sharedBassBoost?.roundedStrength?.toInt() ?: 500
            } catch (e: Exception) {
                500
            }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        try {
                            sharedBassBoost?.setStrength(progress.toShort())
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        rootCard.addView(tvBass)
        rootCard.addView(sbBass)

        val dialog = AlertDialog.Builder(this)
            .setView(rootCard)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val btnCloseEq = TextView(this).apply {
            text = "DONE"
            setTextColor(Color.BLACK)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, (10 * d).toInt(), 0, (10 * d).toInt())
            background = GradientDrawable().apply {
                setColor(accentColorInt)
                cornerRadius = 14f * d
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (14 * d).toInt()
            layoutParams = lp
            setOnClickListener { dialog.dismiss() }
        }
        rootCard.addView(btnCloseEq)
        dialog.show()
    }

    private fun shareCurrentSong() {
        val path = songPaths.getOrElse(currentTrackIndex) { "" }
        if (path.isEmpty()) return
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, Uri.parse(path))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Song"))
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to share song", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEditLyricsDialog() {
        val path = songPaths.getOrElse(currentTrackIndex) { "" }
        val title = songTitles.getOrElse(currentTrackIndex) { "Song" }
        val prefs = getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
        val savedLyrics = prefs.getString("lyrics_$path", null)

        val input = EditText(this).apply {
            setText(savedLyrics ?: "")
            hint = "Paste or type song lyrics here..."
            setHintTextColor(Color.parseColor("#888899"))
            setTextColor(Color.WHITE)
            setPadding(45, 35, 45, 35)
        }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Lyrics • $title")
            .setView(input)
            .setPositiveButton("Save Lyrics") { _, _ ->
                prefs.edit().putString("lyrics_$path", input.text.toString()).apply()
                refreshInlineLyricsText()
                Toast.makeText(this, "Lyrics Saved!", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Find Online") { _, _ ->
                fetchLyricsOnlineAutomatically()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    // NOTIFICATION PANEL & LOCK SCREEN MEDIA CONTROLS
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                "MAX Audio Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Lock screen and notification panel music controls"
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun registerMediaControlReceiver() {
        if (isReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(ACTION_PREV)
            addAction(ACTION_PLAY_PAUSE)
            addAction(ACTION_NEXT)
            addAction(ACTION_SHUFFLE)
            addAction(ACTION_CLOSE)
            addAction("com.app.mediaplayer.SYNC_NOTIFICATION")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mediaNotificationReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(mediaNotificationReceiver, filter)
        }
        isReceiverRegistered = true
    }

    private fun buildActionPendingIntent(action: String, reqCode: Int): PendingIntent {
        val intent = Intent(action).setPackage(packageName)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(this, reqCode, intent, flags)
    }

    private fun updateMediaNotification() {
        try {
            val player = exoPlayer ?: return
            val title = songTitles.getOrElse(currentTrackIndex) { "Playing Audio" }
            val artist = songArtists.getOrElse(currentTrackIndex) { "MAX Player Pro" }
            val isPlaying = player.isPlaying

            val openIntent = Intent(this, AudioPlayerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val contentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val contentPi = PendingIntent.getActivity(this, 90, openIntent, contentFlags)

            val shuffleLabel = if (isShuffleEnabled) "Shuffle: ON" else "Shuffle"
            val playPauseIcon = if (isPlaying) {
                android.R.drawable.ic_media_pause
            } else {
                android.R.drawable.ic_media_play
            }

            val builder = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
                .setSmallIcon(playPauseIcon)
                .setContentTitle(title)
                .setContentText("$artist • (${currentTrackIndex + 1}/${songPaths.size})")
                .setSubText("MAX Player Pro")
                .setContentIntent(contentPi)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(isPlaying)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .addAction(
                    android.R.drawable.ic_menu_rotate,
                    shuffleLabel,
                    buildActionPendingIntent(ACTION_SHUFFLE, 1)
                )
                .addAction(
                    android.R.drawable.ic_media_previous,
                    "Prev",
                    buildActionPendingIntent(ACTION_PREV, 2)
                )
                .addAction(
                    playPauseIcon,
                    if (isPlaying) "Pause" else "Play",
                    buildActionPendingIntent(ACTION_PLAY_PAUSE, 3)
                )
                .addAction(
                    android.R.drawable.ic_media_next,
                    "Next",
                    buildActionPendingIntent(ACTION_NEXT, 4)
                )
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Close",
                    buildActionPendingIntent(ACTION_CLOSE, 5)
                )

            notificationManager.notify(NOTIF_ID, builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // POCKET SHAKE SENSOR (Strictly OFF by default; only active when user turns it ON)
    private fun setupDoubleShakeSensor() {
        try {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            sensorManager?.unregisterListener(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun toggleDoubleShakeMode() {
        isDoubleShakeEnabled = !isDoubleShakeEnabled
        updatePocketShakeIcon()

        if (isDoubleShakeEnabled) {
            try {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                if (wakeLock == null) {
                    wakeLock = pm.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "MAXPlayerPro::PocketAudioShakeLock"
                    ).apply { setReferenceCounted(false) }
                }
                if (wakeLock?.isHeld != true) {
                    wakeLock?.acquire(4 * 60 * 60 * 1000L)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            accelerometer?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
            Toast.makeText(
                this,
                "Pocket Shake ON • Shake phone twice to change song",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            sensorManager?.unregisterListener(this)
            try {
                if (wakeLock?.isHeld == true) wakeLock?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            Toast.makeText(this, "Pocket Shake OFF", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isDoubleShakeEnabled || event == null) return
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val gX = event.values[0] / SensorManager.GRAVITY_EARTH
            val gY = event.values[1] / SensorManager.GRAVITY_EARTH
            val gZ = event.values[2] / SensorManager.GRAVITY_EARTH

            val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)
            if (gForce > 2.45f) {
                val now = System.currentTimeMillis()
                if (now - lastTriggerTimestamp < 1300L) return

                if (now - firstShakeTimestamp > 900L) {
                    shakeCount = 1
                    firstShakeTimestamp = now
                } else {
                    shakeCount++
                    if (shakeCount >= 2 && now - firstShakeTimestamp >= 180L) {
                        shakeCount = 0
                        lastTriggerTimestamp = now
                        playNextTrack()
                        Toast.makeText(
                            this,
                            "Shake Detected • Playing Next Song",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun formatDuration(millis: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis.coerceAtLeast(0L))
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        vinylAnimator?.cancel()
        tonearmAnimator?.cancel()
        handler.removeCallbacks(progressUpdateRunnable)
        handler.removeCallbacks(hideVolumeHudRunnable)
        if (!isDoubleShakeEnabled) {
            try {
                sensorManager?.unregisterListener(this)
                if (wakeLock?.isHeld == true) wakeLock?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (exoPlayer?.isPlaying != true) {
            try {
                if (isReceiverRegistered) {
                    unregisterReceiver(mediaNotificationReceiver)
                    isReceiverRegistered = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 1. Custom Straight Vertical-Bars Waveform AND Simple Straight Line SeekBar Drawable
    private class StraightWaveformDrawable(
        private val density: Float,
        accentColor: Int
    ) : Drawable() {

        private var progressFraction = 0f
        private var barHeights = FloatArray(56)
        private var pointAFraction = -1f
        private var pointBFraction = -1f
        private var simpleLineMode = false

        private val playedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setColor(accentColor)
            style = Paint.Style.FILL
        }

        private val unplayedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setColor(Color.parseColor("#4A4A62"))
            style = Paint.Style.FILL
        }

        private val abPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setColor(Color.parseColor("#00E676"))
            style = Paint.Style.FILL
        }

        private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setColor(Color.WHITE)
            style = Paint.Style.FILL
        }

        init {
            setSeedFromSong(101)
        }

        fun setSimpleLineMode(enabled: Boolean) {
            simpleLineMode = enabled
            invalidateSelf()
        }

        fun setSeedFromSong(seed: Int) {
            for (i in barHeights.indices) {
                val wave1 = abs(sin((i * 0.37) + seed))
                val wave2 = abs(sin((i * 0.83) - seed * 0.5))
                val combined = ((wave1 * 0.65) + (wave2 * 0.35)).toFloat()
                barHeights[i] = combined.coerceIn(0.18f, 0.96f)
            }
            invalidateSelf()
        }

        fun updateProgress(fraction: Float, pointAMs: Long, pointBMs: Long, durationMs: Long) {
            progressFraction = fraction.coerceIn(0f, 1f)
            pointAFraction = if (durationMs > 0L && pointAMs >= 0L) {
                (pointAMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                -1f
            }
            pointBFraction = if (durationMs > 0L && pointBMs >= 0L) {
                (pointBMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                -1f
            }
            invalidateSelf()
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 0f || h <= 0f) return
            val centerY = h / 2f

            if (simpleLineMode) {
                val lineH = 5f * density
                val trackRect = RectF(0f, centerY - lineH / 2f, w, centerY + lineH / 2f)
                canvas.drawRoundRect(trackRect, lineH / 2f, lineH / 2f, unplayedPaint)

                val fillW = (w * progressFraction).coerceIn(0f, w)
                val fillRect = RectF(0f, centerY - lineH / 2f, fillW, centerY + lineH / 2f)
                canvas.drawRoundRect(fillRect, lineH / 2f, lineH / 2f, playedPaint)

                val thumbX = fillW.coerceIn(6f * density, w - 6f * density)
                canvas.drawCircle(thumbX, centerY, 6.5f * density, thumbPaint)
                return
            }

            val count = barHeights.size
            val totalStep = w / count
            val barWidth = (totalStep * 0.54f).coerceAtLeast(2f * density)
            val cornerRadius = barWidth / 2f

            for (i in 0 until count) {
                val barFraction = i.toFloat() / count.toFloat()
                val barHeight = (barHeights[i] * h * 0.88f).coerceAtLeast(6f * density)
                val left = i * totalStep + (totalStep - barWidth) / 2f
                val top = centerY - barHeight / 2f
                val right = left + barWidth
                val bottom = centerY + barHeight / 2f

                val paintToUse = when {
                    pointAFraction >= 0f && pointBFraction > pointAFraction &&
                        barFraction in pointAFraction..pointBFraction -> abPaint
                    barFraction <= progressFraction -> playedPaint
                    else -> unplayedPaint
                }

                canvas.drawRoundRect(RectF(left, top, right, bottom), cornerRadius, cornerRadius, paintToUse)
            }
        }

        override fun setAlpha(alpha: Int) {
            playedPaint.alpha = alpha
            unplayedPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            playedPaint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    // 2. Clean Full-Width Top Camera Volume Bar
    private class TopVolumeBarCleanDrawable(
        private val density: Float,
        accentColor: Int
    ) : Drawable() {

        private var percent = 80

        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setColor(Color.parseColor("#38384A"))
            style = Paint.Style.FILL
        }

        private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setColor(accentColor)
            style = Paint.Style.FILL
        }

        private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setColor(Color.WHITE)
            style = Paint.Style.FILL
        }

        fun setProgressPercent(value: Int) {
            percent = value.coerceIn(0, 100)
            invalidateSelf()
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 0f || h <= 0f) return

            val cy = h / 2f
            val barHeight = 8.5f * density

            val trackRect = RectF(0f, cy - barHeight / 2f, w, cy + barHeight / 2f)
            canvas.drawRoundRect(trackRect, barHeight / 2f, barHeight / 2f, trackPaint)

            val fillWidth = (w * (percent / 100f)).coerceAtLeast(barHeight)
            val fillRect = RectF(0f, cy - barHeight / 2f, fillWidth, cy + barHeight / 2f)
            canvas.drawRoundRect(fillRect, barHeight / 2f, barHeight / 2f, activePaint)

            val thumbX = fillWidth.coerceIn(barHeight / 2f, w - barHeight / 2f)
            canvas.drawCircle(thumbX, cy, 5.5f * density, thumbPaint)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    // 3. Pure White & Accent Vector Icon Drawable
    private class AudioVectorDrawable(
        private val type: String,
        private val density: Float,
        private val iconColor: Int = Color.WHITE
    ) : Drawable() {

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setColor(iconColor)
            style = Paint.Style.STROKE
            strokeWidth = 2.2f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setColor(iconColor)
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

            strokePaint.setColor(iconColor)
            fillPaint.setColor(iconColor)

            when (type) {
                "TONEARM" -> {
                    fillPaint.setColor(Color.parseColor("#E0E0E0"))
                    canvas.drawCircle(w * 0.18f, h * 0.45f, 9f * density, fillPaint)
                    strokePaint.setColor(Color.WHITE)
                    strokePaint.strokeWidth = 3f * density
                    path.moveTo(w * 0.18f, h * 0.45f)
                    path.quadTo(w * 0.55f, h * 0.50f, w * 0.86f, h * 0.85f)
                    canvas.drawPath(path, strokePaint)
                }
                "VINYL_GROOVES" -> {
                    strokePaint.setColor(Color.parseColor("#2A2A40"))
                    strokePaint.strokeWidth = 1.2f * density
                    val maxR = w.coerceAtMost(h) * 0.46f
                    var curR = maxR * 0.52f
                    while (curR < maxR) {
                        canvas.drawCircle(cx, cy, curR, strokePaint)
                        curR += 6f * density
                    }
                }
                "SLIDE_HANDLE" -> {
                    path.moveTo(cx + r * 0.35f, cy - r * 0.65f)
                    path.lineTo(cx - r * 0.35f, cy)
                    path.lineTo(cx + r * 0.35f, cy + r * 0.65f)
                    canvas.drawPath(path, strokePaint)
                }
                "EQUALIZER" -> {
                    canvas.drawLine(cx - r * 0.65f, cy - r * 0.8f, cx - r * 0.65f, cy + r * 0.8f, strokePaint)
                    canvas.drawLine(cx, cy - r * 0.8f, cx, cy + r * 0.8f, strokePaint)
                    canvas.drawLine(cx + r * 0.65f, cy - r * 0.8f, cx + r * 0.65f, cy + r * 0.8f, strokePaint)
                    canvas.drawCircle(cx - r * 0.65f, cy - r * 0.25f, 2.8f * density, fillPaint)
                    canvas.drawCircle(cx, cy + r * 0.3f, 2.8f * density, fillPaint)
                    canvas.drawCircle(cx + r * 0.65f, cy - r * 0.15f, 2.8f * density, fillPaint)
                }
                "GAUGE_TIMER" -> {
                    val arcRect = RectF(cx - r * 0.85f, cy - r * 0.75f, cx + r * 0.85f, cy + r * 0.85f)
                    canvas.drawArc(arcRect, 165f, 210f, false, strokePaint)
                    canvas.drawLine(cx, cy + r * 0.15f, cx + r * 0.42f, cy - r * 0.35f, strokePaint)
                    canvas.drawCircle(cx, cy + r * 0.15f, 2.4f * density, fillPaint)
                }
                "SHAKE_PHONE" -> {
                    val phoneRect = RectF(cx - r * 0.45f, cy - r * 0.72f, cx + r * 0.45f, cy + r * 0.72f)
                    canvas.drawRoundRect(phoneRect, 3f * density, 3f * density, strokePaint)
                    canvas.drawLine(cx - r * 0.82f, cy - r * 0.35f, cx - r * 0.65f, cy, strokePaint)
                    canvas.drawLine(cx - r * 0.65f, cy, cx - r * 0.82f, cy + r * 0.35f, strokePaint)
                    canvas.drawLine(cx + r * 0.82f, cy - r * 0.35f, cx + r * 0.65f, cy, strokePaint)
                    canvas.drawLine(cx + r * 0.65f, cy, cx + r * 0.82f, cy + r * 0.35f, strokePaint)
                }
                "HEART_OUTLINE", "HEART_FILLED" -> {
                    path.moveTo(cx, cy + r * 0.75f)
                    path.cubicTo(cx - r * 1.1f, cy + r * 0.1f, cx - r * 0.9f, cy - r * 0.85f, cx, cy - r * 0.3f)
                    path.cubicTo(cx + r * 0.9f, cy - r * 0.85f, cx + r * 1.1f, cy + r * 0.1f, cx, cy + r * 0.75f)
                    if (type == "HEART_FILLED") {
                        canvas.drawPath(path, fillPaint)
                    } else {
                        canvas.drawPath(path, strokePaint)
                    }
                }
                "THREE_DOTS" -> {
                    canvas.drawCircle(cx, cy - r * 0.68f, 2.5f * density, fillPaint)
                    canvas.drawCircle(cx, cy, 2.5f * density, fillPaint)
                    canvas.drawCircle(cx, cy + r * 0.68f, 2.5f * density, fillPaint)
                }
                "PLAY_CIRCLE" -> {
                    canvas.drawCircle(cx, cy, w * 0.44f, strokePaint)
                    path.moveTo(cx - r * 0.3f, cy - r * 0.58f)
                    path.lineTo(cx + r * 0.65f, cy)
                    path.lineTo(cx - r * 0.3f, cy + r * 0.58f)
                    path.close()
                    canvas.drawPath(path, fillPaint)
                }
                "PAUSE_CIRCLE" -> {
                    canvas.drawCircle(cx, cy, w * 0.44f, strokePaint)
                    canvas.drawLine(cx - r * 0.35f, cy - r * 0.55f, cx - r * 0.35f, cy + r * 0.55f, strokePaint)
                    canvas.drawLine(cx + r * 0.35f, cy - r * 0.55f, cx + r * 0.35f, cy + r * 0.55f, strokePaint)
                }
                "PREV" -> {
                    canvas.drawLine(cx - r * 0.65f, cy - r * 0.65f, cx - r * 0.65f, cy + r * 0.65f, strokePaint)
                    path.moveTo(cx + r * 0.65f, cy - r * 0.65f)
                    path.lineTo(cx - r * 0.35f, cy)
                    path.lineTo(cx + r * 0.65f, cy + r * 0.65f)
                    path.close()
                    canvas.drawPath(path, fillPaint)
                }
                "NEXT" -> {
                    canvas.drawLine(cx + r * 0.65f, cy - r * 0.65f, cx + r * 0.65f, cy + r * 0.65f, strokePaint)
                    path.moveTo(cx - r * 0.65f, cy - r * 0.65f)
                    path.lineTo(cx + r * 0.35f, cy)
                    path.lineTo(cx - r * 0.65f, cy + r * 0.65f)
                    path.close()
                    canvas.drawPath(path, fillPaint)
                }
                "SHUFFLE_OFF", "SHUFFLE_ON" -> {
                    canvas.drawLine(cx - r * 0.75f, cy - r * 0.45f, cx + r * 0.75f, cy + r * 0.45f, strokePaint)
                    canvas.drawLine(cx - r * 0.75f, cy + r * 0.45f, cx + r * 0.75f, cy - r * 0.45f, strokePaint)
                    canvas.drawLine(cx + r * 0.45f, cy - r * 0.45f, cx + r * 0.75f, cy - r * 0.45f, strokePaint)
                    canvas.drawLine(cx + r * 0.45f, cy + r * 0.45f, cx + r * 0.75f, cy + r * 0.45f, strokePaint)
                    if (type == "SHUFFLE_ON") {
                        canvas.drawCircle(cx, cy + r * 0.82f, 2.5f * density, fillPaint)
                    }
                }
                "REPEAT_ALL", "REPEAT_ONE", "REPEAT_OFF" -> {
                    val rect = RectF(cx - r * 0.75f, cy - r * 0.48f, cx + r * 0.75f, cy + r * 0.48f)
                    canvas.drawRoundRect(rect, 4f * density, 4f * density, strokePaint)
                    if (type == "REPEAT_ONE") {
                        canvas.drawLine(cx, cy - r * 0.25f, cx, cy + r * 0.25f, strokePaint)
                        canvas.drawCircle(cx, cy + r * 0.82f, 2.5f * density, fillPaint)
                    } else if (type == "REPEAT_OFF") {
                        canvas.drawLine(cx - r * 0.85f, cy + r * 0.65f, cx + r * 0.85f, cy - r * 0.65f, strokePaint)
                    }
                }
                "QUEUE" -> {
                    canvas.drawLine(cx - r * 0.8f, cy - r * 0.55f, cx + r * 0.8f, cy - r * 0.55f, strokePaint)
                    canvas.drawLine(cx - r * 0.8f, cy, cx + r * 0.8f, cy, strokePaint)
                    canvas.drawLine(cx - r * 0.8f, cy + r * 0.55f, cx + r * 0.35f, cy + r * 0.55f, strokePaint)
                }
                "SPEAKER" -> {
                    path.moveTo(cx - r * 0.75f, cy - r * 0.32f)
                    path.lineTo(cx - r * 0.3f, cy - r * 0.32f)
                    path.lineTo(cx + r * 0.2f, cy - r * 0.75f)
                    path.lineTo(cx + r * 0.2f, cy + r * 0.75f)
                    path.lineTo(cx - r * 0.3f, cy + r * 0.32f)
                    path.lineTo(cx - r * 0.75f, cy + r * 0.32f)
                    path.close()
                    canvas.drawPath(path, fillPaint)
                    val arc = RectF(cx - r * 0.1f, cy - r * 0.55f, cx + r * 0.75f, cy + r * 0.55f)
                    canvas.drawArc(arc, -45f, 90f, false, strokePaint)
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
