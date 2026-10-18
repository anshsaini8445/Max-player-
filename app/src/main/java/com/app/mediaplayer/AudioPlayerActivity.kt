package com.app.mediaplayer

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.media3.common.MediaItem
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

    private var exoPlayer: ExoPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var customEqualizer: Equalizer? = null
    private var customBassBoost: BassBoost? = null

    private lateinit var rootAudioPlayerFrame: FrameLayout
    private lateinit var btnAudioMinimize: ImageView
    private lateinit var tvAudioPlayerTitle: TextView
    private lateinit var tvAudioPlayerArtist: TextView
    private lateinit var btnAudioShareTop: ImageView

    private lateinit var tvLiveTimerCountdown: TextView
    private lateinit var tvShakeModeBadge: TextView

    private lateinit var imgVinylTonearm: ImageView
    private lateinit var cardCenterVinylDisc: CardView
    private lateinit var imgVinylSurface: ImageView
    private lateinit var imgCenterMusicNote: ImageView
    private lateinit var cardSlideOutTabHandle: CardView

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
    private lateinit var gestureDetector: GestureDetector
    private lateinit var waveformDrawable: StraightWaveformDrawable
    private lateinit var topVolumeBarDrawable: TopVolumeBarDotsDrawable

    // Double-Shake Pocket Sensor Variables
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var isDoubleShakeEnabled = true
    private var firstShakeTimestamp = 0L
    private var lastTriggerTimestamp = 0L
    private var shakeCount = 0

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
            handler.postDelayed(this, 200)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_player)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
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

    private fun acquirePocketWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MAXPlayerPro::PocketAudioShakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(4 * 60 * 60 * 1000L)
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
        tvShakeModeBadge = findViewById(R.id.tvShakeModeBadge)

        imgVinylTonearm = findViewById(R.id.imgVinylTonearm)
        cardCenterVinylDisc = findViewById(R.id.cardCenterVinylDisc)
        imgVinylSurface = findViewById(R.id.imgVinylSurface)
        imgCenterMusicNote = findViewById(R.id.imgCenterMusicNote)
        cardSlideOutTabHandle = findViewById(R.id.cardSlideOutTabHandle)

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
        imgVinylTonearm.setImageDrawable(AudioVectorDrawable("TONEARM", d))
        imgVinylSurface.setImageDrawable(AudioVectorDrawable("VINYL_GROOVES", d))

        imgBtnEqualizer.setImageDrawable(AudioVectorDrawable("EQUALIZER", d))
        imgBtnTimerGauge.setImageDrawable(AudioVectorDrawable("GAUGE_TIMER", d))
        imgBtnFavoriteHeart.setImageDrawable(AudioVectorDrawable("HEART_OUTLINE", d))
        imgBtnMoreDots.setImageDrawable(AudioVectorDrawable("THREE_DOTS", d))

        btnAudioShuffle.setImageDrawable(AudioVectorDrawable("SHUFFLE", d))
        btnAudioPrev.setImageDrawable(AudioVectorDrawable("PREV", d))
        btnAudioPlayPause.setImageDrawable(AudioVectorDrawable("PAUSE_CIRCLE", d))
        btnAudioNext.setImageDrawable(AudioVectorDrawable("NEXT", d))
        btnAudioRepeat.setImageDrawable(AudioVectorDrawable("REPEAT", d))

        imgLyricsIcon.setImageDrawable(AudioVectorDrawable("LYRICS", d))
        imgQueueIcon.setImageDrawable(AudioVectorDrawable("QUEUE", d))

        imgTopVolumeSpeaker.setImageDrawable(AudioVectorDrawable("SPEAKER", d))
        topVolumeBarDrawable = TopVolumeBarDotsDrawable(d)
        imgTopVolumeBarWithDots.setImageDrawable(topVolumeBarDrawable)

        waveformDrawable = StraightWaveformDrawable(d)
        imgStraightWaveformBar.setImageDrawable(waveformDrawable)
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
        updateShakeBadgeUi()
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
            cardCenterVinylDisc.radius = 22f * d
            cardCenterVinylDisc.rotation = 0f
            vinylAnimator?.cancel()
        } else {
            imgVinylTonearm.visibility = View.VISIBLE
            cardCenterVinylDisc.radius = 125f * d
            if (exoPlayer?.isPlaying == true) {
                vinylAnimator?.start()
            }
        }
    }

    private fun initializeAudioPlayer() {
        try {
            exoPlayer?.release()
            exoPlayer = ExoPlayer.Builder(this).build().also { player ->
                val mediaItems = songPaths.map { path ->
                    val uri = if (path.startsWith("content://") || path.startsWith("http")) {
                        Uri.parse(path)
                    } else {
                        Uri.fromFile(File(path))
                    }
                    MediaItem.fromUri(uri)
                }

                if (mediaItems.isNotEmpty()) {
                    player.setMediaItems(mediaItems, currentTrackIndex, 0L)
                }
                player.shuffleModeEnabled = isShuffleEnabled
                player.repeatMode = repeatModeState
                player.prepare()
                player.playWhenReady = true

                updateCurrentSongMetadata(currentTrackIndex)
                updateShuffleAndRepeatIcons()

                val d = resources.displayMetrics.density
                player.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        btnAudioPlayPause.setImageDrawable(
                            AudioVectorDrawable(if (isPlaying) "PAUSE_CIRCLE" else "PLAY_CIRCLE", d)
                        )
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
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            tvAudioTotalTime.text = formatDuration(player.duration.coerceAtLeast(0L))
                        }
                    }
                })
            }
            handler.post(progressUpdateRunnable)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to play audio file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateCurrentSongMetadata(index: Int) {
        if (index !in songPaths.indices) return
        val path = songPaths[index]
        val title = songTitles.getOrElse(index) { File(path).name }
        val artist = songArtists.getOrElse(index) { "Unknown" }

        tvAudioPlayerTitle.text = title
        tvAudioPlayerArtist.text = artist
        tvPlayingQueueCount.text = "Playing Queue (${songPaths.size})"
        tvDrawerHeaderTitle.text = "Quick Song Box (${songPaths.size})"

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

            // Strictly Volume + Song Swipe/Drawer (No Double-Tap 10s seek and No Brightness gesture)
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
                        // Slide out Song Drawer from right side
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
        btnAudioMinimize.setOnClickListener { finish() }

        btnAudioShareTop.setOnClickListener {
            shareCurrentSong()
        }

        tvShakeModeBadge.setOnClickListener {
            toggleDoubleShakeMode()
        }

        cardSlideOutTabHandle.setOnClickListener {
            openSongDrawerBox()
        }

        btnCloseSongDrawer.setOnClickListener {
            cardSlideSongDrawer.visibility = View.GONE
        }

        btnAudioEqualizer.setOnClickListener {
            openSystemOrCustomEqualizer()
        }

        btnAudioAbRepeat.setOnClickListener {
            cycleAbRepeatMode()
        }

        btnAudioTimerAndSpeed.setOnClickListener {
            showLiveSleepTimerAndSpeedDialog()
        }

        tvLiveTimerCountdown.setOnClickListener {
            showLiveSleepTimerAndSpeedDialog()
        }

        btnAudioFavorite.setOnClickListener {
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

        btnAudioFarRightDots.setOnClickListener { anchor ->
            showFarRightThreeDotsMenu(anchor)
        }

        btnAudioPlayPause.setOnClickListener {
            exoPlayer?.let { player ->
                if (player.isPlaying) player.pause() else player.play()
            }
        }

        btnAudioPrev.setOnClickListener {
            playPreviousTrack()
        }

        btnAudioNext.setOnClickListener {
            playNextTrack()
        }

        btnAudioShuffle.setOnClickListener {
            isShuffleEnabled = !isShuffleEnabled
            exoPlayer?.shuffleModeEnabled = isShuffleEnabled
            updateShuffleAndRepeatIcons()
            Toast.makeText(
                this,
                if (isShuffleEnabled) "Shuffle ON" else "Shuffle OFF",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnAudioRepeat.setOnClickListener {
            repeatModeState = when (repeatModeState) {
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
                else -> Player.REPEAT_MODE_ALL
            }
            exoPlayer?.repeatMode = repeatModeState
            updateShuffleAndRepeatIcons()
            val modeText = when (repeatModeState) {
                Player.REPEAT_MODE_ONE -> "Repeat One Song"
                Player.REPEAT_MODE_OFF -> "Repeat OFF"
                else -> "Loop All Songs"
            }
            Toast.makeText(this, modeText, Toast.LENGTH_SHORT).show()
        }

        btnAudioLyrics.setOnClickListener {
            showLyricsDialog()
        }

        btnAudioPlayingQueue.setOnClickListener {
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
        }
    }

    private fun updateShuffleAndRepeatIcons() {
        val d = resources.displayMetrics.density
        btnAudioShuffle.setImageDrawable(
            AudioVectorDrawable(
                "SHUFFLE",
                d,
                if (isShuffleEnabled) Color.parseColor("#00FFFF") else Color.WHITE
            )
        )
        val repeatColor = if (repeatModeState == Player.REPEAT_MODE_OFF) {
            Color.parseColor("#777788")
        } else {
            Color.parseColor("#00FFFF")
        }
        val repeatType = if (repeatModeState == Player.REPEAT_MODE_ONE) "REPEAT_ONE" else "REPEAT"
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

    // Live Ticking Countdown Clock (e.g. 29:59, 29:58...) + Speed Control
    private fun showLiveSleepTimerAndSpeedDialog() {
        val options = arrayOf(
            "Sleep Timer: OFF",
            "Sleep Timer: 10 Minutes (Live Clock)",
            "Sleep Timer: 20 Minutes (Live Clock)",
            "Sleep Timer: 30 Minutes (Live Clock)",
            "Sleep Timer: 40 Minutes (Live Clock)",
            "Sleep Timer: 60 Minutes (Live Clock)",
            "Custom Minutes Sleep Timer...",
            "Playback Speed: 0.75x",
            "Playback Speed: 1.0x (Normal)",
            "Playback Speed: 1.25x",
            "Playback Speed: 1.5x",
            "Playback Speed: 2.0x"
        )

        AlertDialog.Builder(this)
            .setTitle("Live Sleep Timer & Audio Speed")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> cancelSleepTimer()
                    1 -> startLiveSleepTimer(10)
                    2 -> startLiveSleepTimer(20)
                    3 -> startLiveSleepTimer(30)
                    4 -> startLiveSleepTimer(40)
                    5 -> startLiveSleepTimer(60)
                    6 -> promptCustomSleepMinutes()
                    7 -> applyAudioSpeed(0.75f)
                    8 -> applyAudioSpeed(1.0f)
                    9 -> applyAudioSpeed(1.25f)
                    10 -> applyAudioSpeed(1.5f)
                    11 -> applyAudioSpeed(2.0f)
                }
            }
            .show()
    }

    private fun promptCustomSleepMinutes() {
        val input = EditText(this).apply {
            hint = "Enter minutes (e.g. 45)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.BLACK)
            setPadding(40, 30, 40, 30)
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

        sleepTimer = object : CountDownTimer(totalMillis, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val totalSecs = millisUntilFinished / 1000L
                val m = totalSecs / 60L
                val s = totalSecs % 60L
                tvLiveTimerCountdown.text = String.format(Locale.US, "⏱ %02d:%02d", m, s)
            }

            override fun onFinish() {
                exoPlayer?.pause()
                tvLiveTimerCountdown.visibility = View.GONE
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
        Toast.makeText(this, "Sleep Timer turned OFF", Toast.LENGTH_SHORT).show()
    }

    private fun applyAudioSpeed(speed: Float) {
        currentSpeed = speed
        exoPlayer?.playbackParameters = PlaybackParameters(currentSpeed)
        Toast.makeText(this, "Audio Speed: ${currentSpeed}x", Toast.LENGTH_SHORT).show()
    }

    private fun showFarRightThreeDotsMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        val path = songPaths.getOrElse(currentTrackIndex) { "" }
        val title = songTitles.getOrElse(currentTrackIndex) { "Song" }

        popup.menu.add(0, 1, 0, "Add to Playlist")
        popup.menu.add(
            0,
            2,
            1,
            if (isDoubleShakeEnabled) "Double-Shake to Play Next: ON ✓" else "Double-Shake to Play Next: OFF"
        )
        popup.menu.add(0, 3, 2, "Sleep Timer (Live Countdown)")
        popup.menu.add(
            0,
            4,
            3,
            if (isSquareAlbumStyle) "Switch to Rotating Vinyl Disc" else "Switch to Square Album Art Box"
        )
        popup.menu.add(0, 5, 4, "Open Slide-Out Song Box")
        popup.menu.add(0, 6, 5, "Equalizer & Bass Boost")
        popup.menu.add(0, 7, 6, "Share Song")
        popup.menu.add(0, 8, 7, "Song Details")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    if (path.isNotEmpty()) {
                        showAddToPlaylistDialogInternal(path, title)
                    }
                    true
                }
                2 -> {
                    toggleDoubleShakeMode()
                    true
                }
                3 -> {
                    showLiveSleepTimerAndSpeedDialog()
                    true
                }
                4 -> {
                    isSquareAlbumStyle = !isSquareAlbumStyle
                    getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("square_album_style", isSquareAlbumStyle)
                        .apply()
                    applyVinylOrSquareBoxStyle()
                    true
                }
                5 -> {
                    openSongDrawerBox()
                    true
                }
                6 -> {
                    openSystemOrCustomEqualizer()
                    true
                }
                7 -> {
                    shareCurrentSong()
                    true
                }
                8 -> {
                    val f = File(path)
                    val sizeMb = if (f.exists()) f.length() / (1024.0 * 1024.0) else 0.0
                    AlertDialog.Builder(this)
                        .setTitle("Song Information")
                        .setMessage(
                            "Title: $title\n\n" +
                                "Artist: ${tvAudioPlayerArtist.text}\n\n" +
                                "Duration: ${tvAudioTotalTime.text}\n\n" +
                                "Size: ${String.format(Locale.US, "%.2f MB", sizeMb)}\n\n" +
                                "Path: $path"
                        )
                        .setPositiveButton("Close", null)
                        .show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showAddToPlaylistDialogInternal(songPath: String, songTitle: String) {
        val prefs = getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
        val existingSet = prefs.getStringSet("custom_audio_playlists", emptySet()) ?: emptySet()
        val existingList = existingSet.toList()
        val options = mutableListOf("+ Create New Playlist")
        options.addAll(existingList)

        AlertDialog.Builder(this)
            .setTitle("Add '$songTitle' to Playlist")
            .setItems(options.toTypedArray()) { _, which ->
                if (which == 0) {
                    val input = EditText(this).apply {
                        hint = "Playlist Name (e.g. Favorites, Gym)"
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
            .show()
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
            if (customEqualizer == null && sessionId != 0) {
                customEqualizer = Equalizer(0, sessionId).apply { enabled = true }
            }
            if (customBassBoost == null && sessionId != 0) {
                customBassBoost = BassBoost(0, sessionId).apply { enabled = true }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 24)
        }

        val eq = customEqualizer
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
                        AlertDialog.Builder(this@AudioPlayerActivity)
                            .setTitle("Select Equalizer Preset")
                            .setItems(presetNames) { _, idx ->
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
                            .show()
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
                customBassBoost?.roundedStrength?.toInt() ?: 400
            } catch (e: Exception) {
                400
            }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        try {
                            customBassBoost?.setStrength(progress.toShort())
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

    // DOUBLE-SHAKE POCKET SENSOR (Shake phone twice to play/switch song even in pocket)
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
        updateShakeBadgeUi()

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

    private fun updateShakeBadgeUi() {
        if (isDoubleShakeEnabled) {
            tvShakeModeBadge.text = "2x Shake: ON"
            tvShakeModeBadge.setTextColor(Color.parseColor("#00FFFF"))
        } else {
            tvShakeModeBadge.text = "2x Shake: OFF"
            tvShakeModeBadge.setTextColor(Color.parseColor("#777788"))
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
        sleepTimer?.cancel()
        vinylAnimator?.cancel()
        handler.removeCallbacks(progressUpdateRunnable)
        handler.removeCallbacks(hideVolumeHudRunnable)
        try {
            sensorManager?.unregisterListener(this)
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            customEqualizer?.release()
            customEqualizer = null
            customBassBoost?.release()
            customBassBoost = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        exoPlayer?.release()
        exoPlayer = null
    }

    // 1. Custom Straight Vertical-Bars Waveform Drawable
    private class StraightWaveformDrawable(private val density: Float) : Drawable() {

        private var progressFraction = 0f
        private var barHeights = FloatArray(56)
        private var pointAFraction = -1f
        private var pointBFraction = -1f

        private val playedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00A8FF")
            style = Paint.Style.FILL
        }

        private val unplayedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3A3A4E")
            style = Paint.Style.FILL
        }

        private val abPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E676")
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

    // 2. Top Camera Volume Bar with 4 Trailing Dots
    private class TopVolumeBarDotsDrawable(private val density: Float) : Drawable() {

        private var percent = 80

        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#44444E")
            style = Paint.Style.FILL
        }

        private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
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
            val dotRadius = 4.2f * density
            val dotSpacing = 14f * density

            // 4 Trailing Dots on Right Side
            val dotsTotalWidth = 4 * dotSpacing
            val barMaxRight = (w - dotsTotalWidth - 6f * density).coerceAtLeast(w * 0.65f)

            // Background Track
            val trackRect = RectF(0f, cy - barHeight / 2f, barMaxRight, cy + barHeight / 2f)
            canvas.drawRoundRect(trackRect, barHeight / 2f, barHeight / 2f, trackPaint)

            // Active White Fill Pill
            val fillWidth = (barMaxRight * (percent / 100f)).coerceAtLeast(barHeight)
            val fillRect = RectF(0f, cy - barHeight / 2f, fillWidth, cy + barHeight / 2f)
            canvas.drawRoundRect(fillRect, barHeight / 2f, barHeight / 2f, activePaint)

            // Draw the 4 Dots on the right
            for (i in 0 until 4) {
                val dotCenterX = barMaxRight + 10f * density + (i * dotSpacing)
                val threshold = 80 + (i + 1) * 5
                val dotPaint = if (percent >= threshold) activePaint else trackPaint
                canvas.drawCircle(dotCenterX, cy, dotRadius, dotPaint)
            }
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    // 3. Sleek Vector Icon Drawable for Audio Player Controls
    private class AudioVectorDrawable(
        private val type: String,
        private val density: Float,
        private val color: Int = Color.WHITE
    ) : Drawable() {

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 1.9f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
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
                "TONEARM" -> {
                    fillPaint.color = Color.parseColor("#CCCCCC")
                    canvas.drawCircle(w * 0.18f, h * 0.45f, 9f * density, fillPaint)
                    strokePaint.color = Color.parseColor("#DDDDDD")
                    strokePaint.strokeWidth = 3f * density
                    path.moveTo(w * 0.18f, h * 0.45f)
                    path.quadTo(w * 0.55f, h * 0.50f, w * 0.86f, h * 0.85f)
                    canvas.drawPath(path, strokePaint)
                }
                "VINYL_GROOVES" -> {
                    strokePaint.color = Color.parseColor("#252538")
                    strokePaint.strokeWidth = 1.2f * density
                    val maxR = w.coerceAtMost(h) * 0.46f
                    var curR = maxR * 0.52f
                    while (curR < maxR) {
                        canvas.drawCircle(cx, cy, curR, strokePaint)
                        curR += 6f * density
                    }
                }
                "EQUALIZER" -> {
                    canvas.drawLine(cx - r * 0.65f, cy - r * 0.8f, cx - r * 0.65f, cy + r * 0.8f, strokePaint)
                    canvas.drawLine(cx, cy - r * 0.8f, cx, cy + r * 0.8f, strokePaint)
                    canvas.drawLine(cx + r * 0.65f, cy - r * 0.8f, cx + r * 0.65f, cy + r * 0.8f, strokePaint)
                    canvas.drawCircle(cx - r * 0.65f, cy - r * 0.25f, 2.5f * density, fillPaint)
                    canvas.drawCircle(cx, cy + r * 0.3f, 2.5f * density, fillPaint)
                    canvas.drawCircle(cx + r * 0.65f, cy - r * 0.15f, 2.5f * density, fillPaint)
                }
                "GAUGE_TIMER" -> {
                    val arcRect = RectF(cx - r * 0.85f, cy - r * 0.75f, cx + r * 0.85f, cy + r * 0.85f)
                    canvas.drawArc(arcRect, 165f, 210f, false, strokePaint)
                    canvas.drawLine(cx, cy + r * 0.15f, cx + r * 0.42f, cy - r * 0.35f, strokePaint)
                    canvas.drawCircle(cx, cy + r * 0.15f, 2f * density, fillPaint)
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
                    canvas.drawCircle(cx, cy - r * 0.65f, 2.1f * density, fillPaint)
                    canvas.drawCircle(cx, cy, 2.1f * density, fillPaint)
                    canvas.drawCircle(cx, cy + r * 0.65f, 2.1f * density, fillPaint)
                }
                "PLAY_CIRCLE" -> {
                    canvas.drawCircle(cx, cy, w * 0.45f, strokePaint)
                    path.moveTo(cx - r * 0.35f, cy - r * 0.6f)
                    path.lineTo(cx + r * 0.65f, cy)
                    path.lineTo(cx - r * 0.35f, cy + r * 0.6f)
                    path.close()
                    canvas.drawPath(path, fillPaint)
                }
                "PAUSE_CIRCLE" -> {
                    canvas.drawCircle(cx, cy, w * 0.45f, strokePaint)
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
                "SHUFFLE" -> {
                    canvas.drawLine(cx - r * 0.75f, cy - r * 0.45f, cx + r * 0.75f, cy + r * 0.45f, strokePaint)
                    canvas.drawLine(cx - r * 0.75f, cy + r * 0.45f, cx + r * 0.75f, cy - r * 0.45f, strokePaint)
                }
                "REPEAT", "REPEAT_ONE" -> {
                    val rect = RectF(cx - r * 0.75f, cy - r * 0.48f, cx + r * 0.75f, cy + r * 0.48f)
                    canvas.drawRoundRect(rect, 4f * density, 4f * density, strokePaint)
                    if (type == "REPEAT_ONE") {
                        canvas.drawLine(cx, cy - r * 0.25f, cx, cy + r * 0.25f, strokePaint)
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
