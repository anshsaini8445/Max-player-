package com.app.mediaplayer

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.audiofx.AudioEffect
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
import androidx.core.app.NotificationCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class AudioPlayerActivity : AppCompatActivity(), SensorEventListener {

    companion object {
        private var sharedPlayer: ExoPlayer? = null
        private var sharedEqualizer: Equalizer? = null
        private var sharedBassBoost: BassBoost? = null

        private const val NOTIF_CHANNEL_ID = "max_player_audio_channel"
        private const val NOTIF_ID = 8801

        private const val ACTION_PREV = "com.app.mediaplayer.ACTION_AUDIO_PREV"
        private const val ACTION_PLAY_PAUSE = "com.app.mediaplayer.ACTION_AUDIO_PLAY_PAUSE"
        private const val ACTION_NEXT = "com.app.mediaplayer.ACTION_AUDIO_NEXT"
        private const val ACTION_SHUFFLE = "com.app.mediaplayer.ACTION_AUDIO_SHUFFLE"
        private const val ACTION_CLOSE = "com.app.mediaplayer.ACTION_AUDIO_CLOSE"
    }

    private var exoPlayer: ExoPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var rootAudioPlayerFrame: FrameLayout
    private lateinit var btnAudioMinimize: ImageView
    private lateinit var tvAudioPlayerTitle: TextView
    private lateinit var tvAudioPlayerArtist: TextView
    private lateinit var btnAudioShareTop: ImageView
    private lateinit var tvLiveTimerCountdown: TextView

    private lateinit var imgVinylTonearm: ImageView
    private lateinit var cardCenterVinylDisc: CardView
    private lateinit var imgVinylSurface: ImageView
    private lateinit var imgCenterMusicNote: ImageView
    private lateinit var cardSlideOutTabHandle: CardView
    private lateinit var imgSlideHandleArrow: ImageView

    private lateinit var btnAudioEqualizer: LinearLayout
    private lateinit var imgBtnEqualizer: ImageView
    private lateinit var btnAudioAbRepeat: LinearLayout
    private lateinit var tvBtnAbRepeat: TextView
    private lateinit var btnAudioTimerAndSpeed: LinearLayout
    private lateinit var imgBtnTimerGauge: ImageView
    private lateinit var btnAudioFavorite: LinearLayout
    private lateinit var imgBtnFavoriteHeart: ImageView
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

    private lateinit var btnAudioLyrics: LinearLayout
    private lateinit var imgLyricsIcon: ImageView
    private lateinit var btnAudioPlayingQueue: LinearLayout
    private lateinit var imgQueueIcon: ImageView
    private lateinit var tvPlayingQueueCount: TextView

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
    private var currentTrackIndex = 0

    private var isShuffleEnabled = false
    private var repeatModeState = Player.REPEAT_MODE_ALL
    private var isFavorite = false
    private var isSquareAlbumStyle = false
    private var currentSpeed = 1.0f

    private var pointA: Long = -1L
    private var pointB: Long = -1L

    private var sleepTimer: CountDownTimer? = null
    private var vinylAnimator: ObjectAnimator? = null

    private lateinit var audioManager: AudioManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var gestureDetector: GestureDetector
    private lateinit var waveformDrawable: StraightWaveformDrawable
    private lateinit var topVolumeBarDrawable: TopVolumeBarCleanDrawable

    // Double-Shake Pocket Sensor Variables
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var isDoubleShakeEnabled = true
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

    // Lock Screen & Notification Panel Action Receiver
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
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_player)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannel()
        registerMediaControlReceiver()
        acquirePocketWakeLock()
        bindViews()
        setupSleekPlayerIcons()
        readIncomingIntent(intent)
        setupRotationAnimation()
        initializeAudioPlayer()
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
            initializeAudioPlayer()
        }
    }

    private fun acquirePocketWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MAXPlayerPro::PocketAudioShakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(6 * 60 * 60 * 1000L)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun bindViews() {
        rootAudioPlayerFrame = findViewById(R.id.rootAudioPlayerFrame)
        btnAudioMinimize = findViewById(R.id.btnAudioMinimize)
        tvAudioPlayerTitle = findViewById(R.id.tvAudioPlayerTitle)
        tvAudioPlayerArtist = findViewById(R.id.tvAudioPlayerArtist)
        btnAudioShareTop = findViewById(R.id.btnAudioShareTop)
        tvLiveTimerCountdown = findViewById(R.id.tvLiveTimerCountdown)

        imgVinylTonearm = findViewById(R.id.imgVinylTonearm)
        cardCenterVinylDisc = findViewById(R.id.cardCenterVinylDisc)
        imgVinylSurface = findViewById(R.id.imgVinylSurface)
        imgCenterMusicNote = findViewById(R.id.imgCenterMusicNote)
        cardSlideOutTabHandle = findViewById(R.id.cardSlideOutTabHandle)
        imgSlideHandleArrow = findViewById(R.id.imgSlideHandleArrow)

        btnAudioEqualizer = findViewById(R.id.btnAudioEqualizer)
        imgBtnEqualizer = findViewById(R.id.imgBtnEqualizer)
        btnAudioAbRepeat = findViewById(R.id.btnAudioAbRepeat)
        tvBtnAbRepeat = findViewById(R.id.tvBtnAbRepeat)
        btnAudioTimerAndSpeed = findViewById(R.id.btnAudioTimerAndSpeed)
        imgBtnTimerGauge = findViewById(R.id.imgBtnTimerGauge)
        btnAudioFavorite = findViewById(R.id.btnAudioFavorite)
        imgBtnFavoriteHeart = findViewById(R.id.imgBtnFavoriteHeart)
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

        btnAudioLyrics = findViewById(R.id.btnAudioLyrics)
        imgLyricsIcon = findViewById(R.id.imgLyricsIcon)
        btnAudioPlayingQueue = findViewById(R.id.btnAudioPlayingQueue)
        imgQueueIcon = findViewById(R.id.imgQueueIcon)
        tvPlayingQueueCount = findViewById(R.id.tvPlayingQueueCount)

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

    private fun setupSleekPlayerIcons() {
        val d = resources.displayMetrics.density
        imgVinylTonearm.setImageDrawable(AudioVectorDrawable("TONEARM", d, Color.WHITE))
        imgVinylSurface.setImageDrawable(AudioVectorDrawable("VINYL_GROOVES", d, Color.WHITE))
        imgSlideHandleArrow.setImageDrawable(AudioVectorDrawable("SLIDE_HANDLE", d, Color.parseColor("#00FFFF")))

        imgBtnEqualizer.setImageDrawable(AudioVectorDrawable("EQUALIZER", d, Color.WHITE))
        imgBtnTimerGauge.setImageDrawable(AudioVectorDrawable("GAUGE_TIMER", d, Color.WHITE))
        imgBtnFavoriteHeart.setImageDrawable(AudioVectorDrawable("HEART_OUTLINE", d, Color.WHITE))
        imgBtnMoreDots.setImageDrawable(AudioVectorDrawable("THREE_DOTS", d, Color.WHITE))

        btnAudioShuffle.setImageDrawable(AudioVectorDrawable("SHUFFLE_OFF", d, Color.WHITE))
        btnAudioPrev.setImageDrawable(AudioVectorDrawable("PREV", d, Color.WHITE))
        btnAudioPlayPause.setImageDrawable(AudioVectorDrawable("PAUSE_CIRCLE", d, Color.WHITE))
        btnAudioNext.setImageDrawable(AudioVectorDrawable("NEXT", d, Color.WHITE))
        btnAudioRepeat.setImageDrawable(AudioVectorDrawable("REPEAT_ALL", d, Color.parseColor("#00FFFF")))

        imgLyricsIcon.setImageDrawable(AudioVectorDrawable("LYRICS", d, Color.WHITE))
        imgQueueIcon.setImageDrawable(AudioVectorDrawable("QUEUE", d, Color.WHITE))

        imgTopVolumeSpeaker.setImageDrawable(AudioVectorDrawable("SPEAKER", d, Color.parseColor("#00FFFF")))
        topVolumeBarDrawable = TopVolumeBarCleanDrawable(d)
        imgTopVolumeBarWithDots.setImageDrawable(topVolumeBarDrawable)

        waveformDrawable = StraightWaveformDrawable(d)
        imgStraightWaveformBar.setImageDrawable(waveformDrawable)
    }

    private fun animateIconTap(view: View) {
        view.animate()
            .scaleX(0.82f)
            .scaleY(0.82f)
            .setDuration(85)
            .withEndAction {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(110)
                    .start()
            }
            .start()
    }

    private fun readIncomingIntent(incomingIntent: Intent?) {
        if (incomingIntent == null) return
        val singlePath = incomingIntent.getStringExtra("AUDIO_PATH") ?: ""
        val singleTitle = incomingIntent.getStringExtra("AUDIO_TITLE") ?: File(singlePath).name
        val singleArtist = incomingIntent.getStringExtra("AUDIO_ARTIST") ?: "Unknown"

        val listPaths = incomingIntent.getStringArrayListExtra("AUDIO_LIST_PATHS")
        val listTitles = incomingIntent.getStringArrayListExtra("AUDIO_LIST_TITLES")
        val listArtists = incomingIntent.getStringArrayListExtra("AUDIO_LIST_ARTISTS")

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
                ArrayList(listPaths.map { "Unknown" })
            }
            currentTrackIndex = incomingIntent.getIntExtra("AUDIO_POSITION", 0)
                .coerceIn(0, songPaths.size - 1)
        } else if (singlePath.isNotEmpty()) {
            songPaths = arrayListOf(singlePath)
            songTitles = arrayListOf(singleTitle)
            songArtists = arrayListOf(singleArtist)
            currentTrackIndex = 0
        }

        isShuffleEnabled = incomingIntent.getBooleanExtra("AUDIO_SHUFFLE_START", false)

        val prefs = getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
        isDoubleShakeEnabled = prefs.getBoolean("double_shake_enabled", true)
        isSquareAlbumStyle = prefs.getBoolean("square_album_style", false)
        applyVinylOrSquareBoxStyle()
        setupSlideDrawerRecyclerView()
    }

    private fun setupRotationAnimation() {
        vinylAnimator = ObjectAnimator.ofFloat(cardCenterVinylDisc, View.ROTATION, 0f, 360f).apply {
            duration = 14000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
        }
    }

    private fun applyVinylOrSquareBoxStyle() {
        val d = resources.displayMetrics.density
        if (isSquareAlbumStyle) {
            imgVinylTonearm.visibility = View.GONE
            cardCenterVinylDisc.radius = 24f * d
            cardCenterVinylDisc.rotation = 0f
            vinylAnimator?.cancel()
        } else {
            imgVinylTonearm.visibility = View.VISIBLE
            cardCenterVinylDisc.radius = 128f * d
            if (exoPlayer?.isPlaying == true) {
                vinylAnimator?.start()
            }
        }
    }

    private fun initializeAudioPlayer() {
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
                if (songPaths.isNotEmpty()) {
                    val mediaItems = songPaths.mapIndexed { idx, path ->
                        val uri = if (path.startsWith("content://") || path.startsWith("http")) {
                            Uri.parse(path)
                        } else {
                            Uri.fromFile(File(path))
                        }
                        val title = songTitles.getOrElse(idx) { File(path).name }
                        val artist = songArtists.getOrElse(idx) { "Unknown" }

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
                updateMediaNotification()

                player.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        updatePlayPauseIcon(isPlaying)
                        updateMediaNotification()
                        if (!isSquareAlbumStyle) {
                            if (isPlaying) {
                                if (vinylAnimator?.isStarted == true) vinylAnimator?.resume() else vinylAnimator?.start()
                            } else {
                                vinylAnimator?.pause()
                            }
                        }
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

    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        val d = resources.displayMetrics.density
        btnAudioPlayPause.setImageDrawable(
            AudioVectorDrawable(
                if (isPlaying) "PAUSE_CIRCLE" else "PLAY_CIRCLE",
                d,
                if (isPlaying) Color.parseColor("#00FFFF") else Color.WHITE
            )
        )
    }

    private fun updateCurrentSongMetadata(index: Int) {
        if (index !in songPaths.indices) return
        val path = songPaths[index]
        val title = songTitles.getOrElse(index) { File(path).name }
        val artist = songArtists.getOrElse(index) { "Unknown" }

        tvAudioPlayerTitle.text = title
        tvAudioPlayerArtist.text = artist
        tvPlayingQueueCount.text = "Playing Queue (${songPaths.size})"
        tvDrawerHeaderTitle.text = "Playing Queue (${songPaths.size})"

        waveformDrawable.setSeedFromSong(title.hashCode())

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

                if (abs(diffX) > abs(diffY) && abs(diffX) > 110) {
                    val screenWidth = resources.displayMetrics.widthPixels
                    if (diffX < 0 && e1.x > screenWidth * 0.65f) {
                        openSongDrawerBox()
                    } else if (diffX < 0) {
                        playNextTrack()
                    } else if (diffX > 0) {
                        if (cardSlideSongDrawer.visibility == View.VISIBLE) {
                            cardSlideSongDrawer.visibility = View.GONE
                        } else {
                            playPreviousTrack()
                        }
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
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_RAISE,
                0
            )
            val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            showTopCameraVolumeHud(cur, max)
            return true
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_LOWER,
                0
            )
            val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            showTopCameraVolumeHud(cur, max)
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
            openSongDrawerBox()
        }

        btnCloseSongDrawer.setOnClickListener {
            cardSlideSongDrawer.visibility = View.GONE
        }

        btnAudioEqualizer.setOnClickListener {
            animateIconTap(imgBtnEqualizer)
            openSystemOrCustomEqualizer()
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

        btnAudioFavorite.setOnClickListener {
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
                if (isShuffleEnabled) "Shuffle / Random Play: ON" else "Shuffle: OFF",
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

        btnAudioLyrics.setOnClickListener {
            animateIconTap(it)
            showLyricsDialog()
        }

        btnAudioPlayingQueue.setOnClickListener {
            animateIconTap(it)
            openSongDrawerBox()
        }
    }

    private fun openSongDrawerBox() {
        cardSlideSongDrawer.visibility = View.VISIBLE
        rvSlideDrawerSongs.scrollToPosition(currentTrackIndex.coerceAtLeast(0))
    }

    private fun setupSlideDrawerRecyclerView() {
        rvSlideDrawerSongs.layoutManager = LinearLayoutManager(this)
        rvSlideDrawerSongs.adapter = object : RecyclerView.Adapter<DrawerSongVH>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DrawerSongVH {
                val row = LinearLayout(this@AudioPlayerActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(20, 22, 20, 22)
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                val tvIndex = TextView(this@AudioPlayerActivity).apply {
                    textSize = 12f
                    setPadding(0, 0, 20, 0)
                }
                val tvName = TextView(this@AudioPlayerActivity).apply {
                    textSize = 13.5f
                    maxLines = 1
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                row.addView(tvIndex)
                row.addView(tvName)
                return DrawerSongVH(row, tvIndex, tvName)
            }

            override fun onBindViewHolder(holder: DrawerSongVH, position: Int) {
                val title = songTitles.getOrElse(position) { "Track ${position + 1}" }
                val isCurrent = position == currentTrackIndex
                holder.tvIndex.text = "${position + 1}."
                holder.tvName.text = title
                holder.tvIndex.setTextColor(
                    if (isCurrent) Color.parseColor("#00FFFF") else Color.parseColor("#888899")
                )
                holder.tvName.setTextColor(
                    if (isCurrent) Color.parseColor("#00FFFF") else Color.WHITE
                )
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
        val tvName: TextView
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
                if (isShuffleEnabled) Color.parseColor("#00FFFF") else Color.WHITE
            )
        )
        val repeatColor = if (repeatModeState == Player.REPEAT_MODE_OFF) {
            Color.WHITE
        } else {
            Color.parseColor("#00FFFF")
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
                tvBtnAbRepeat.setTextColor(Color.parseColor("#00FFFF"))
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

    // Live High-Speed Millisecond Reverse Countdown Clock (e.g., 14:18.84) + Speed Control
    private fun showLiveSleepTimerAndSpeedDialog() {
        val options = arrayOf(
            "Sleep Timer: OFF",
            "Sleep Timer: 10 Minutes (Live Millisecond Clock)",
            "Sleep Timer: 15 Minutes (Live Millisecond Clock)",
            "Sleep Timer: 30 Minutes (Live Millisecond Clock)",
            "Sleep Timer: 45 Minutes (Live Millisecond Clock)",
            "Sleep Timer: 60 Minutes (Live Millisecond Clock)",
            "Custom Minutes Sleep Timer...",
            "Playback Speed: 0.75x",
            "Playback Speed: 1.0x (Normal)",
            "Playback Speed: 1.25x",
            "Playback Speed: 1.5x",
            "Playback Speed: 2.0x (Fast)",
            "Playback Speed: 3.0x (Super Fast)"
        )

        showStyledWhiteListDialog("Live Sleep Timer & Audio Speed", options) { which ->
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
                12 -> applyAudioSpeed(3.0f)
            }
        }
    }

    private fun promptCustomSleepMinutes() {
        val input = EditText(this).apply {
            hint = "Enter minutes (e.g. 15)"
            setHintTextColor(Color.parseColor("#888899"))
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE)
            setPadding(45, 35, 45, 35)
        }
        AlertDialog.Builder(this)
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
            AudioVectorDrawable("GAUGE_TIMER", resources.displayMetrics.density, Color.parseColor("#00FFFF"))
        )

        // Updates every 40ms so milliseconds/hundredths visibly tick down (e.g. 14:18.84)
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

    // Custom Crystal-Clear White-on-Dark 3-Dots Advanced Menu (No Black-on-Black Popup Issues)
    private fun showPremiumWhiteThreeDotsDialog() {
        val path = songPaths.getOrElse(currentTrackIndex) { "" }
        val title = songTitles.getOrElse(currentTrackIndex) { "Song" }
        val is2xActive = abs(currentSpeed - 2.0f) < 0.05f

        val menuItems = arrayOf(
            if (is2xActive) "⚡ 2x Fast Speed: ON (Tap for 1.0x Normal)" else "⚡ Instant 2x Fast Speed Boost",
            "🎚️ Playback Speed (${currentSpeed}x) - All Speeds",
            if (isDoubleShakeEnabled) "📳 Pocket 2x Shake Next Song: ON ✓" else "📳 Pocket 2x Shake Next Song: OFF",
            "⏱️ Live Sleep Timer (Millisecond Countdown)",
            "➕ Add Song to Playlist",
            "🎛️ Equalizer & Dolby Bass Boost",
            "🔁 A-B Repeat Scene Loop",
            if (isSquareAlbumStyle) "💿 Switch to Rotating Vinyl Disc" else "🖼️ Switch to Square Album Art Box",
            "📂 Open Slide-Out Song Queue",
            "📤 Share Current Audio",
            "ℹ️ Song File Information"
        )

        showStyledWhiteListDialog("Audio Player Options", menuItems) { which ->
            when (which) {
                0 -> applyAudioSpeed(if (is2xActive) 1.0f else 2.0f)
                1 -> showSpeedSelectorDialog()
                2 -> toggleDoubleShakeMode()
                3 -> showLiveSleepTimerAndSpeedDialog()
                4 -> if (path.isNotEmpty()) showAddToPlaylistDialogInternal(path, title)
                5 -> openSystemOrCustomEqualizer()
                6 -> cycleAbRepeatMode()
                7 -> {
                    isSquareAlbumStyle = !isSquareAlbumStyle
                    getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("square_album_style", isSquareAlbumStyle)
                        .apply()
                    applyVinylOrSquareBoxStyle()
                }
                8 -> openSongDrawerBox()
                9 -> shareCurrentSong()
                10 -> {
                    val f = File(path)
                    val sizeMb = if (f.exists()) f.length() / (1024.0 * 1024.0) else 0.0
                    AlertDialog.Builder(this)
                        .setTitle("Song Information")
                        .setMessage(
                            "Title: $title\n\n" +
                                "Artist: ${tvAudioPlayerArtist.text}\n\n" +
                                "Duration: ${tvAudioTotalTime.text}\n\n" +
                                "Speed: ${currentSpeed}x\n\n" +
                                "Size: ${String.format(Locale.US, "%.2f MB", sizeMb)}\n\n" +
                                "Path: $path"
                        )
                        .setPositiveButton("Close", null)
                        .show()
                }
            }
        }
    }

    private fun showSpeedSelectorDialog() {
        val labels = arrayOf(
            "0.5x (Slow)",
            "0.75x",
            "1.0x (Normal)",
            "1.25x",
            "1.5x",
            "2.0x (Fast)",
            "2.5x",
            "3.0x (Super Fast)",
            "4.0x (Ultra Fast)"
        )
        val speeds = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f)
        showStyledWhiteListDialog("Select Playback Speed", labels) { idx ->
            applyAudioSpeed(speeds[idx])
        }
    }

    private fun showStyledWhiteListDialog(
        headerTitle: String,
        items: Array<String>,
        onSelect: (Int) -> Unit
    ) {
        val d = resources.displayMetrics.density
        val rootCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * d).toInt(), (18 * d).toInt(), (18 * d).toInt(), (14 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141422"))
                cornerRadius = 22f * d
                setStroke((1 * d).toInt(), Color.parseColor("#3300FFFF"))
            }
        }

        val tvHeader = TextView(this).apply {
            text = headerTitle
            setTextColor(Color.parseColor("#00FFFF"))
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((10 * d).toInt(), (4 * d).toInt(), (10 * d).toInt(), (12 * d).toInt())
        }
        rootCard.addView(tvHeader)

        val dividerTop = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (1 * d).toInt()
            )
            setBackgroundColor(Color.parseColor("#26FFFFFF"))
        }
        rootCard.addView(dividerTop)

        val scrollView = ScrollView(this)
        val listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val dialog = AlertDialog.Builder(this)
            .setView(rootCard)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        items.forEachIndexed { index, textLabel ->
            val itemTv = TextView(this).apply {
                text = textLabel
                setTextColor(Color.WHITE)
                textSize = 14.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER_VERTICAL
                setPadding((12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt())
                setOnClickListener {
                    dialog.dismiss()
                    onSelect(index)
                }
            }
            listLayout.addView(itemTv)

            if (index < items.size - 1) {
                val line = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (1 * d).toInt()
                    )
                    setBackgroundColor(Color.parseColor("#15FFFFFF"))
                }
                listLayout.addView(line)
            }
        }

        scrollView.addView(listLayout)
        rootCard.addView(scrollView)
        dialog.show()
    }

    private fun showAddToPlaylistDialogInternal(songPath: String, songTitle: String) {
        val prefs = getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
        val existingSet = prefs.getStringSet("custom_audio_playlists", emptySet()) ?: emptySet()
        val existingList = existingSet.toList()
        val options = mutableListOf("+ Create New Playlist")
        options.addAll(existingList)

        showStyledWhiteListDialog("Add '$songTitle' to Playlist", options.toTypedArray()) { which ->
            if (which == 0) {
                val input = EditText(this).apply {
                    hint = "Playlist Name (e.g. Favorites, Gym)"
                    setHintTextColor(Color.parseColor("#888899"))
                    setTextColor(Color.BLACK)
                    setPadding(40, 30, 40, 30)
                }
                AlertDialog.Builder(this)
                    .setTitle("Create & Add to Playlist")
                    .setView(input)
                    .setPositiveButton("Save") { _, _ ->
                        val name = input.text.toString().trim()
                        if (name.isNotEmpty()) {
                            saveSongToCustomPlaylist(name, songPath)
                            Toast.makeText(this, "Added to '$name'", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                val chosenPlaylist = existingList[which - 1]
                saveSongToCustomPlaylist(chosenPlaylist, songPath)
                Toast.makeText(this, "Added to '$chosenPlaylist'", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveSongToCustomPlaylist(playlistName: String, songPath: String) {
        val prefs = getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
        val playlists = (prefs.getStringSet("custom_audio_playlists", emptySet()) ?: emptySet()).toMutableSet()
        playlists.add(playlistName)
        val songs = (prefs.getStringSet("playlist_songs_$playlistName", emptySet()) ?: emptySet()).toMutableSet()
        songs.add(songPath)
        prefs.edit()
            .putStringSet("custom_audio_playlists", playlists)
            .putStringSet("playlist_songs_$playlistName", songs)
            .apply()
    }

    private fun openSystemOrCustomEqualizer() {
        val sessionId = exoPlayer?.audioSessionId ?: 0
        try {
            val eqIntent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            }
            if (eqIntent.resolveActivity(packageManager) != null) {
                startActivityForResult(eqIntent, 202)
                return
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        showBuiltInEqualizerDialog(sessionId)
    }

    private fun showBuiltInEqualizerDialog(sessionId: Int) {
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

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 24)
        }

        val eq = sharedEqualizer
        if (eq != null) {
            val numPresets = eq.numberOfPresets.toInt()
            if (numPresets > 0) {
                val presetNames = Array(numPresets) { i -> eq.getPresetName(i.toShort()) }
                val tvPreset = TextView(this).apply {
                    text = "Tap to Choose Sound Preset (Pop / Rock / Bass)"
                    setTextColor(Color.parseColor("#0088CC"))
                    textSize = 14f
                    setPadding(0, 0, 0, 24)
                    setOnClickListener {
                        showStyledWhiteListDialog("Select Equalizer Preset", presetNames) { idx ->
                            try {
                                eq.usePreset(idx.toShort())
                                Toast.makeText(
                                    this@AudioPlayerActivity,
                                    "Preset: ${presetNames[idx]}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (ex: Exception) {
                                ex.printStackTrace()
                            }
                        }
                    }
                }
                container.addView(tvPreset)
            }
        }

        val tvBass = TextView(this).apply {
            text = "Bass Boost Level"
            textSize = 13f
            setTextColor(Color.DKGRAY)
        }
        val sbBass = SeekBar(this).apply {
            max = 1000
            progress = try {
                sharedBassBoost?.roundedStrength?.toInt() ?: 400
            } catch (e: Exception) {
                400
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
        container.addView(tvBass)
        container.addView(sbBass)

        AlertDialog.Builder(this)
            .setTitle("Equalizer & Bass Boost")
            .setView(container)
            .setPositiveButton("Done", null)
            .show()
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

    private fun showLyricsDialog() {
        val path = songPaths.getOrElse(currentTrackIndex) { "" }
        val title = songTitles.getOrElse(currentTrackIndex) { "Song" }
        val prefs = getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
        val savedLyrics = prefs.getString("lyrics_$path", null)

        val lrcFile = if (path.isNotEmpty() && !path.startsWith("content://")) {
            val base = path.substringBeforeLast(".")
            File("$base.lrc")
        } else {
            null
        }

        val displayLyrics = when {
            lrcFile != null && lrcFile.exists() -> lrcFile.readText()
            !savedLyrics.isNullOrEmpty() -> savedLyrics
            else -> "No synced lyrics found for '$title'.\n\nTap 'Add / Edit Lyrics' below to paste or save lyrics for this song."
        }

        AlertDialog.Builder(this)
            .setTitle("Lyrics • $title")
            .setMessage(displayLyrics)
            .setPositiveButton("Add / Edit Lyrics") { _, _ ->
                val input = EditText(this).apply {
                    setText(savedLyrics ?: "")
                    hint = "Paste or type song lyrics here..."
                    setTextColor(Color.BLACK)
                    setPadding(40, 30, 40, 30)
                }
                AlertDialog.Builder(this)
                    .setTitle("Save Lyrics")
                    .setView(input)
                    .setPositiveButton("Save") { _, _ ->
                        prefs.edit().putString("lyrics_$path", input.text.toString()).apply()
                        Toast.makeText(this, "Lyrics Saved!", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
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
                .setContentText("$artist • ${if (isShuffleEnabled) "Shuffle ON" else "Sequential"}")
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
                    "Stop",
                    buildActionPendingIntent(ACTION_CLOSE, 5)
                )

            notificationManager.notify(NOTIF_ID, builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // DOUBLE-SHAKE POCKET SENSOR
    private fun setupDoubleShakeSensor() {
        try {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (isDoubleShakeEnabled && accelerometer != null) {
                sensorManager?.registerListener(
                    this,
                    accelerometer,
                    SensorManager.SENSOR_DELAY_GAME
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun toggleDoubleShakeMode() {
        isDoubleShakeEnabled = !isDoubleShakeEnabled
        getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("double_shake_enabled", isDoubleShakeEnabled)
            .apply()

        if (isDoubleShakeEnabled) {
            accelerometer?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
            Toast.makeText(
                this,
                "Pocket Double-Shake ON (Shake twice to play next song)",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            sensorManager?.unregisterListener(this)
            Toast.makeText(this, "Pocket Double-Shake OFF", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isDoubleShakeEnabled || event == null) return
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val gX = x / SensorManager.GRAVITY_EARTH
            val gY = y / SensorManager.GRAVITY_EARTH
            val gZ = z / SensorManager.GRAVITY_EARTH

            val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)
            if (gForce > 2.35f) {
                val now = System.currentTimeMillis()
                if (now - lastTriggerTimestamp < 1200L) return

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
                            "Double-Shake Detected • Playing Next Song",
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
        handler.removeCallbacks(progressUpdateRunnable)
        handler.removeCallbacks(hideVolumeHudRunnable)
        // Note: sharedPlayer is intentionally NOT released here so background audio continues seamlessly!
        if (exoPlayer?.isPlaying != true) {
            try {
                sensorManager?.unregisterListener(this)
                if (isReceiverRegistered) {
                    unregisterReceiver(mediaNotificationReceiver)
                    isReceiverRegistered = false
                }
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 1. Custom Straight Vertical-Bars Waveform Drawable
    private class StraightWaveformDrawable(private val density: Float) : Drawable() {

        private var progressFraction = 0f
        private var barHeights = FloatArray(56)
        private var pointAFraction = -1f
        private var pointBFraction = -1f

        private val playedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setColor(Color.parseColor("#00E5FF"))
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

        init {
            setSeedFromSong(101)
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
            if (durationMs > 0L && pointAMs >= 0L) {
                pointAFraction = (pointAMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                pointAFraction = -1f
            }
            if (durationMs > 0L && pointBMs >= 0L) {
                pointBFraction = (pointBMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                pointBFraction = -1f
            }
            invalidateSelf()
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 0f || h <= 0f) return

            val count = barHeights.size
            val totalStep = w / count
            val barWidth = (totalStep * 0.54f).coerceAtLeast(2f * density)
            val cornerRadius = barWidth / 2f
            val centerY = h / 2f

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

    // 2. Clean Full-Width Top Camera Volume Bar (4 Dots Removed as requested)
    private class TopVolumeBarCleanDrawable(private val density: Float) : Drawable() {

        private var percent = 80

        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setColor(Color.parseColor("#38384A"))
            style = Paint.Style.FILL
        }

        private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setColor(Color.parseColor("#00FFFF"))
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

            // Full Smooth Background Track
            val trackRect = RectF(0f, cy - barHeight / 2f, w, cy + barHeight / 2f)
            canvas.drawRoundRect(trackRect, barHeight / 2f, barHeight / 2f, trackPaint)

            // Active Cyan/White Fill Pill
            val fillWidth = (w * (percent / 100f)).coerceAtLeast(barHeight)
            val fillRect = RectF(0f, cy - barHeight / 2f, fillWidth, cy + barHeight / 2f)
            canvas.drawRoundRect(fillRect, barHeight / 2f, barHeight / 2f, activePaint)

            // Crisp White Thumb Indicator
            val thumbX = fillWidth.coerceIn(barHeight / 2f, w - barHeight / 2f)
            canvas.drawCircle(thumbX, cy, 5.5f * density, thumbPaint)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    // 3. Pure White & Cyan Vector Icon Drawable (Fixed setColor so icons are never black)
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
                    // Arrowheads
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
                "LYRICS" -> {
                    val doc = RectF(cx - r * 0.75f, cy - r * 0.8f, cx + r * 0.75f, cy + r * 0.8f)
                    canvas.drawRoundRect(doc, 3f * density, 3f * density, strokePaint)
                    canvas.drawLine(cx - r * 0.4f, cy - r * 0.3f, cx + r * 0.4f, cy - r * 0.3f, strokePaint)
                    canvas.drawLine(cx - r * 0.4f, cy + r * 0.1f, cx + r * 0.2f, cy + r * 0.1f, strokePaint)
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
