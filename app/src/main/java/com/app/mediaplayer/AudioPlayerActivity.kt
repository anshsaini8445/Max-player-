package com.app.mediaplayer

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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.TextUtils
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.sin

class AudioPlayerActivity : AppCompatActivity() {

    companion object {
        private const val REQ_PICK_CUSTOM_PHOTO = 7201
        private const val NOTIF_CHANNEL_ID = "max_music_playback_channel"
        private const val NOTIF_ID = 4402
        private const val ACTION_NOTIF_PREV = "com.app.mediaplayer.ACTION_NOTIF_PREV"
        private const val ACTION_NOTIF_PLAY_PAUSE = "com.app.mediaplayer.ACTION_NOTIF_PLAY_PAUSE"
        private const val ACTION_NOTIF_NEXT = "com.app.mediaplayer.ACTION_NOTIF_NEXT"

        private var sharedExoPlayer: ExoPlayer? = null
        private val sharedPaths = mutableListOf<String>()
        private val sharedTitles = mutableListOf<String>()
        private val sharedArtists = mutableListOf<String>()
        private var sharedIndex = 0

        fun getSharedAudioSessionId(): Int {
            return sharedExoPlayer?.audioSessionId ?: 0
        }

        fun getCurrentPlayingPath(): String {
            return if (sharedIndex in sharedPaths.indices) sharedPaths[sharedIndex] else ""
        }

        fun getCurrentPlayingTitle(): String {
            return if (sharedIndex in sharedTitles.indices) sharedTitles[sharedIndex] else ""
        }

        fun getCurrentPlayingArtist(): String {
            return if (sharedIndex in sharedArtists.indices) sharedArtists[sharedIndex] else "Unknown Artist"
        }

        fun isSharedPlayerPlaying(): Boolean {
            return sharedExoPlayer?.isPlaying == true
        }

        fun toggleSharedPlaybackFromMiniPlayer() {
            sharedExoPlayer?.let { player ->
                if (player.isPlaying) player.pause() else player.play()
            }
        }
    }

    private lateinit var pocketShakeDetector: PocketShakeDetector

    private lateinit var rootAudioPlayer: RelativeLayout
    private lateinit var tvAudioPlayerTitle: TextView
    private lateinit var tvAudioPlayerArtist: TextView
    private lateinit var btnAudioShareTop: ImageView

    private lateinit var centerStageContainer: FrameLayout
    private lateinit var pageVinylAndArt: FrameLayout
    private lateinit var layoutVinylMode: FrameLayout
    private lateinit var cardVinylOuterDisc: CardView
    private lateinit var imgVinylGrooves: ImageView
    private lateinit var imgVinylCenterArt: ImageView
    private lateinit var imgVinylTonearm: ImageView
    private lateinit var cardSquareArtBox: CardView
    private lateinit var imgSquareArt: ImageView
    private lateinit var btnSideQueueTab: TextView

    private lateinit var pageLyricsContainer: LinearLayout
    private lateinit var tvAudioLyricsText: TextView
    private lateinit var btnLyricsAddToFavorite: TextView
    private lateinit var btnSearchLyricsOnline: TextView

    private lateinit var viewDashIndicatorLeft: View
    private lateinit var viewDashIndicatorRight: View

    private lateinit var btnToolSlot1: TextView
    private lateinit var btnToolSlot2: TextView
    private lateinit var btnToolSlot3: TextView
    private lateinit var btnToolSlot4: TextView
    private lateinit var btnAudioThreeDots: ImageView

    private lateinit var tvAudioCurrentTime: TextView
    private lateinit var tvAudioTotalTime: TextView
    private lateinit var frameScrubberContainer: FrameLayout
    private lateinit var seekBarAudioStraight: SeekBar
    private lateinit var waveformBarView: WaveformScrubberView

    private lateinit var btnAudioShuffle: TextView
    private lateinit var btnAudioPrev: ImageView
    private lateinit var btnAudioPlayPause: ImageView
    private lateinit var btnAudioNext: ImageView
    private lateinit var btnAudioLoopMode: TextView

    private lateinit var btnBottomLeftPocketShake: TextView
    private lateinit var btnBottomRightPlayingQueue: TextView

    private lateinit var scrimQueueOutside: View
    private lateinit var drawerPlayingQueueRight: LinearLayout
    private lateinit var tvDrawerQueueHeader: TextView
    private lateinit var btnCloseQueueDrawer: TextView
    private lateinit var rvSlidingAudioQueue: RecyclerView

    private var currentAudioPath = ""
    private var currentAudioTitle = "Audio Track"
    private var currentAudioArtist = "Unknown Artist"

    private var isLyricsPageVisible = false
    private var isSquareAlbumStyle = false
    private var isWaveformMode = true
    private var isPocketShakeEnabled = false
    private var currentPlaybackSpeed = 1.0f
    private var discRotationAngle = 0f
    private var pointA = -1L
    private var pointB = -1L
    private var sleepTimer: CountDownTimer? = null
    private var activeAccentColor = Color.parseColor("#00FFFF")
    private var isNotifReceiverRegistered = false

    // Order of the 4 customizable tool slots: 0=Equalizer, 1=AB Repeat, 2=Speed, 3=Sleep Timer
    private val toolSlotMapping = intArrayOf(0, 1, 2, 3)

    private val handler = Handler(Looper.getMainLooper())

    private val notifReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_NOTIF_PREV -> playPreviousTrack()
                ACTION_NOTIF_PLAY_PAUSE -> {
                    sharedExoPlayer?.let { p ->
                        if (p.isPlaying) p.pause() else p.play()
                    }
                    showOrUpdateMediaNotification()
                }
                ACTION_NOTIF_NEXT -> playNextTrack()
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayPauseButtonVisual(isPlaying)
            animateTonearm(isPlaying)
            showOrUpdateMediaNotification()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val player = sharedExoPlayer ?: return
            val newIdx = player.currentMediaItemIndex
            if (newIdx in sharedPaths.indices) {
                sharedIndex = newIdx
                pointA = -1L
                pointB = -1L
                refreshCurrentSongMetadataAndArt()
                rvSlidingAudioQueue.adapter?.notifyDataSetChanged()
                showOrUpdateMediaNotification()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                val dur = (sharedExoPlayer?.duration ?: 1L).coerceAtLeast(1L)
                seekBarAudioStraight.max = dur.toInt()
                tvAudioTotalTime.text = formatTime(dur)
            }
        }
    }

    private val spinAndProgressRunnable = object : Runnable {
        override fun run() {
            try {
                sharedExoPlayer?.let { player ->
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration.coerceAtLeast(1L)
                    seekBarAudioStraight.max = dur.toInt()
                    seekBarAudioStraight.progress = pos.toInt()
                    val ratio = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                    waveformBarView.setProgressRatio(ratio)

                    tvAudioCurrentTime.text = formatTime(pos)
                    tvAudioTotalTime.text = formatTime(dur)

                    if (pointA != -1L && pointB != -1L && pos >= pointB) {
                        player.seekTo(pointA)
                    }

                    if (player.isPlaying && !isSquareAlbumStyle && !isLyricsPageVisible) {
                        discRotationAngle = (discRotationAngle + 1.35f) % 360f
                        cardVinylOuterDisc.rotation = discRotationAngle
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            handler.postDelayed(this, 30)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_player)

        registerNotificationReceiver()
        bindViews()
        applySavedThemeColors()
        setupVinylAndTonearmDrawables()
        restoreUserAudioPreferences()
        handleIncomingIntent(intent)
        setupSwipeBetweenVinylAndLyrics()
        setupAllControlClicks()
        setupSlidingQueueRecyclerView()
    }

    override fun onResume() {
        super.onResume()
        applySavedThemeColors()
        handler.post(spinAndProgressRunnable)
    }

    private fun registerNotificationReceiver() {
        if (isNotifReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(ACTION_NOTIF_PREV)
            addAction(ACTION_NOTIF_PLAY_PAUSE)
            addAction(ACTION_NOTIF_NEXT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notifReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(notifReceiver, filter)
        }
        isNotifReceiverRegistered = true
    }

    private fun bindViews() {
        rootAudioPlayer = findViewById(R.id.rootAudioPlayer)
        tvAudioPlayerTitle = findViewById(R.id.tvAudioPlayerTitle)
        tvAudioPlayerArtist = findViewById(R.id.tvAudioPlayerArtist)
        btnAudioShareTop = findViewById(R.id.btnAudioShareTop)
        tvAudioPlayerTitle.isSelected = true

        centerStageContainer = findViewById(R.id.centerStageContainer)
        pageVinylAndArt = findViewById(R.id.pageVinylAndArt)
        layoutVinylMode = findViewById(R.id.layoutVinylMode)
        cardVinylOuterDisc = findViewById(R.id.cardVinylOuterDisc)
        imgVinylGrooves = findViewById(R.id.imgVinylGrooves)
        imgVinylCenterArt = findViewById(R.id.imgVinylCenterArt)
        imgVinylTonearm = findViewById(R.id.imgVinylTonearm)
        cardSquareArtBox = findViewById(R.id.cardSquareArtBox)
        imgSquareArt = findViewById(R.id.imgSquareArt)
        btnSideQueueTab = findViewById(R.id.btnSideQueueTab)

        pageLyricsContainer = findViewById(R.id.pageLyricsContainer)
        tvAudioLyricsText = findViewById(R.id.tvAudioLyricsText)
        btnLyricsAddToFavorite = findViewById(R.id.btnLyricsAddToFavorite)
        btnSearchLyricsOnline = findViewById(R.id.btnSearchLyricsOnline)

        viewDashIndicatorLeft = findViewById(R.id.viewDashIndicatorLeft)
        viewDashIndicatorRight = findViewById(R.id.viewDashIndicatorRight)

        btnToolSlot1 = findViewById(R.id.btnToolSlot1)
        btnToolSlot2 = findViewById(R.id.btnToolSlot2)
        btnToolSlot3 = findViewById(R.id.btnToolSlot3)
        btnToolSlot4 = findViewById(R.id.btnToolSlot4)
        btnAudioThreeDots = findViewById(R.id.btnAudioThreeDots)

        tvAudioCurrentTime = findViewById(R.id.tvAudioCurrentTime)
        tvAudioTotalTime = findViewById(R.id.tvAudioTotalTime)
        frameScrubberContainer = findViewById(R.id.frameScrubberContainer)
        seekBarAudioStraight = findViewById(R.id.seekBarAudioStraight)

        waveformBarView = WaveformScrubberView(this) { seekRatio ->
            val dur = (sharedExoPlayer?.duration ?: 1L).coerceAtLeast(1L)
            val targetMs = (dur * seekRatio).toLong()
            sharedExoPlayer?.seekTo(targetMs)
            tvAudioCurrentTime.text = formatTime(targetMs)
        }
        frameScrubberContainer.addView(
            waveformBarView,
            0,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        btnAudioShuffle = findViewById(R.id.btnAudioShuffle)
        btnAudioPrev = findViewById(R.id.btnAudioPrev)
        btnAudioPlayPause = findViewById(R.id.btnAudioPlayPause)
        btnAudioNext = findViewById(R.id.btnAudioNext)
        btnAudioLoopMode = findViewById(R.id.btnAudioLoopMode)

        btnBottomLeftPocketShake = findViewById(R.id.btnBottomLeftPocketShake)
        btnBottomRightPlayingQueue = findViewById(R.id.btnBottomRightPlayingQueue)

        scrimQueueOutside = findViewById(R.id.scrimQueueOutside)
        drawerPlayingQueueRight = findViewById(R.id.drawerPlayingQueueRight)
        tvDrawerQueueHeader = findViewById(R.id.tvDrawerQueueHeader)
        btnCloseQueueDrawer = findViewById(R.id.btnCloseQueueDrawer)
        rvSlidingAudioQueue = findViewById(R.id.rvSlidingAudioQueue)
    }

    private fun applySavedThemeColors() {
        val prefs = getSharedPreferences("MAX_THEME_PREFS", Context.MODE_PRIVATE)
        val bgHex = prefs.getString("app_bg_color", "#08080E") ?: "#08080E"
        val accentHex = prefs.getString("app_accent_color", "#00FFFF") ?: "#00FFFF"
        try {
            val bgColor = Color.parseColor(bgHex)
            activeAccentColor = Color.parseColor(accentHex)
            rootAudioPlayer.setBackgroundColor(bgColor)
            window.statusBarColor = bgColor
            window.navigationBarColor = bgColor
        } catch (e: Exception) {
            activeAccentColor = Color.parseColor("#00FFFF")
        }

        val d = resources.displayMetrics.density
        btnAudioPlayPause.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke((2.5f * d).toInt(), activeAccentColor)
        }
        btnSideQueueTab.setTextColor(activeAccentColor)
        btnSearchLyricsOnline.setTextColor(activeAccentColor)
        tvDrawerQueueHeader.setTextColor(activeAccentColor)
        waveformBarView.setActiveColor(activeAccentColor)
        updateFlatDashIndicators()
    }

    private fun setupVinylAndTonearmDrawables() {
        val d = resources.displayMetrics.density
        imgVinylGrooves.setImageDrawable(VinylGroovesDrawable(d))
        imgVinylTonearm.setImageDrawable(WhiteStylusTonearmDrawable(d))
    }

    private fun restoreUserAudioPreferences() {
        val prefs = getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
        // Pocket Shake MUST be OFF by default when app is installed/opened unless enabled by user
        isPocketShakeEnabled = prefs.getBoolean("double_shake_enabled", false)
        isSquareAlbumStyle = prefs.getBoolean("square_album_style", false)
        isWaveformMode = prefs.getBoolean("waveform_scrubber_mode", true)

        val savedOrder = prefs.getString("tool_slot_order", "0,1,2,3") ?: "0,1,2,3"
        val parts = savedOrder.split(",")
        if (parts.size == 4) {
            for (i in 0..3) {
                toolSlotMapping[i] = parts[i].toIntOrNull() ?: i
            }
        }

        pocketShakeDetector = PocketShakeDetector(this) {
            runOnUiThread {
                if (isPocketShakeEnabled) {
                    playNextTrack()
                    Toast.makeText(this, "Pocket Shake: Next Song", Toast.LENGTH_SHORT).show()
                }
            }
        }

        updatePocketShakeVisual()
        updateCenterArtStyleVisual()
        updateScrubberModeVisual()
        refreshCustomizableToolSlotsText()
    }

    private fun updatePocketShakeVisual() {
        if (isPocketShakeEnabled) {
            btnBottomLeftPocketShake.text = "📳  Pocket Shake: ON"
            btnBottomLeftPocketShake.setTextColor(activeAccentColor)
            pocketShakeDetector.startListening()
        } else {
            btnBottomLeftPocketShake.text = "📳  Pocket Shake: OFF"
            btnBottomLeftPocketShake.setTextColor(Color.parseColor("#B0B0C0"))
            pocketShakeDetector.stopListening()
        }
    }

    private fun updateCenterArtStyleVisual() {
        if (isSquareAlbumStyle) {
            layoutVinylMode.visibility = View.GONE
            cardSquareArtBox.visibility = View.VISIBLE
        } else {
            cardSquareArtBox.visibility = View.GONE
            layoutVinylMode.visibility = View.VISIBLE
        }
    }

    private fun updateScrubberModeVisual() {
        if (isWaveformMode) {
            waveformBarView.visibility = View.VISIBLE
            seekBarAudioStraight.visibility = View.GONE
        } else {
            waveformBarView.visibility = View.GONE
            seekBarAudioStraight.visibility = View.VISIBLE
        }
    }

    private fun handleIncomingIntent(incomingIntent: Intent?) {
        val resumeExisting = incomingIntent?.getBooleanExtra("RESUME_EXISTING", false) == true
        if (resumeExisting && sharedExoPlayer != null && sharedPaths.isNotEmpty()) {
            sharedExoPlayer?.removeListener(playerListener)
            sharedExoPlayer?.addListener(playerListener)
            refreshCurrentSongMetadataAndArt()
            updatePlayPauseButtonVisual(sharedExoPlayer?.isPlaying == true)
            animateTonearm(sharedExoPlayer?.isPlaying == true)
            return
        }

        if (incomingIntent != null) {
            val pathExtra = incomingIntent.getStringExtra("AUDIO_PATH") ?: ""
            val titleExtra = incomingIntent.getStringExtra("AUDIO_TITLE") ?: File(pathExtra).name
            val artistExtra = incomingIntent.getStringExtra("AUDIO_ARTIST") ?: "Unknown Artist"
            val idxExtra = incomingIntent.getIntExtra("AUDIO_INDEX", 0)

            val pathsList = incomingIntent.getStringArrayListExtra("AUDIO_LIST")
            val titlesList = incomingIntent.getStringArrayListExtra("AUDIO_TITLES")
            val artistsList = incomingIntent.getStringArrayListExtra("AUDIO_ARTISTS")

            sharedPaths.clear()
            sharedTitles.clear()
            sharedArtists.clear()

            if (!pathsList.isNullOrEmpty()) {
                sharedPaths.addAll(pathsList)
                if (!titlesList.isNullOrEmpty() && titlesList.size == pathsList.size) {
                    sharedTitles.addAll(titlesList)
                } else {
                    pathsList.forEach { sharedTitles.add(File(it).name) }
                }
                if (!artistsList.isNullOrEmpty() && artistsList.size == pathsList.size) {
                    sharedArtists.addAll(artistsList)
                } else {
                    pathsList.forEach { _ -> sharedArtists.add(artistExtra) }
                }
                sharedIndex = idxExtra.coerceIn(0, sharedPaths.size - 1)
            } else if (pathExtra.isNotEmpty()) {
                sharedPaths.add(pathExtra)
                sharedTitles.add(titleExtra)
                sharedArtists.add(artistExtra)
                sharedIndex = 0
            }
        }

        startSharedExoPlayer()
    }

    private fun startSharedExoPlayer() {
        try {
            if (sharedExoPlayer == null) {
                sharedExoPlayer = ExoPlayer.Builder(applicationContext).build()
            }
            val player = sharedExoPlayer ?: return
            player.removeListener(playerListener)
            player.addListener(playerListener)

            val mediaItems = sharedPaths.map { path ->
                val uri = if (path.startsWith("content://") || path.startsWith("http")) {
                    Uri.parse(path)
                } else {
                    Uri.fromFile(File(path))
                }
                MediaItem.fromUri(uri)
            }
            player.setMediaItems(mediaItems, sharedIndex, 0L)
            player.repeatMode = Player.REPEAT_MODE_ALL
            player.prepare()
            player.playWhenReady = true

            refreshCurrentSongMetadataAndArt()
            showOrUpdateMediaNotification()
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to play audio track", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshCurrentSongMetadataAndArt() {
        if (sharedIndex in sharedPaths.indices) {
            currentAudioPath = sharedPaths[sharedIndex]
            currentAudioTitle = sharedTitles.getOrElse(sharedIndex) { File(currentAudioPath).name }
            currentAudioArtist = sharedArtists.getOrElse(sharedIndex) { "Unknown Artist" }
        }

        tvAudioPlayerTitle.text = currentAudioTitle
        tvAudioPlayerArtist.text = currentAudioArtist
        tvAudioPlayerTitle.isSelected = true

        val total = sharedPaths.size.coerceAtLeast(1)
        val curNum = (sharedIndex + 1).coerceIn(1, total)
        btnBottomRightPlayingQueue.text = "≡  Playing Queue ($curNum/$total)"
        tvDrawerQueueHeader.text = "Playing Queue ($curNum/$total)"

        val prefs = getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
        val isFav = prefs.getBoolean("fav_audio_$currentAudioPath", false)
        if (isFav) {
            btnLyricsAddToFavorite.text = "♥  Added to Favorite"
            btnLyricsAddToFavorite.setTextColor(Color.parseColor("#FF4081"))
        } else {
            btnLyricsAddToFavorite.text = "♡  Add to Favorite"
            btnLyricsAddToFavorite.setTextColor(Color.WHITE)
        }

        loadCoverPhotoIntoVinylAndSquare(prefs.getString("custom_art_$currentAudioPath", null))

        AudioLyricsHelper.loadLyricsForSong(
            this,
            currentAudioPath,
            currentAudioTitle,
            currentAudioArtist
        ) { lyricsText ->
            runOnUiThread {
                tvAudioLyricsText.text = lyricsText
            }
        }
    }

    private fun loadCoverPhotoIntoVinylAndSquare(customPhotoUri: String?) {
        imgVinylCenterArt.clearColorFilter()
        imgSquareArt.clearColorFilter()

        if (!customPhotoUri.isNullOrEmpty()) {
            val uri = Uri.parse(customPhotoUri)
            Glide.with(this).load(uri).centerCrop().into(imgVinylCenterArt)
            Glide.with(this).load(uri).centerCrop().into(imgSquareArt)
            return
        }

        // Try loading embedded album art via MediaStore album ID lookup
        var albumId = -1L
        try {
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media.ALBUM_ID),
                "${MediaStore.Audio.Media.DATA} = ?",
                arrayOf(currentAudioPath),
                null
            )?.use { c ->
                if (c.moveToFirst()) {
                    albumId = c.getLong(0)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val targetModel: Any = if (albumId > 0L) {
            ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
        } else {
            File(currentAudioPath)
        }

        Glide.with(this)
            .load(targetModel)
            .placeholder(android.R.drawable.ic_lock_silent_mode_off)
            .error(android.R.drawable.ic_lock_silent_mode_off)
            .centerCrop()
            .into(imgVinylCenterArt)

        Glide.with(this)
            .load(targetModel)
            .placeholder(android.R.drawable.ic_lock_silent_mode_off)
            .error(android.R.drawable.ic_lock_silent_mode_off)
            .centerCrop()
            .into(imgSquareArt)
    }

    private fun updatePlayPauseButtonVisual(isPlaying: Boolean) {
        btnAudioPlayPause.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
    }

    private fun animateTonearm(isPlaying: Boolean) {
        imgVinylTonearm.animate()
            .rotation(if (isPlaying) 4f else -18f)
            .setDuration(400)
            .start()
    }

    private fun setupSwipeBetweenVinylAndLyrics() {
        val swipeDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                if (abs(diffX) > abs(diffY) && abs(diffX) > 85) {
                    if (diffX < 0) {
                        switchCenterPage(showLyrics = true)
                    } else {
                        switchCenterPage(showLyrics = false)
                    }
                    return true
                }
                return false
            }
        })

        centerStageContainer.setOnTouchListener { _, event ->
            swipeDetector.onTouchEvent(event)
        }

        findViewById<LinearLayout>(R.id.llFlatPageIndicators).setOnClickListener {
            switchCenterPage(!isLyricsPageVisible)
        }
    }

    private fun switchCenterPage(showLyrics: Boolean) {
        isLyricsPageVisible = showLyrics
        if (showLyrics) {
            pageVinylAndArt.visibility = View.GONE
            pageLyricsContainer.visibility = View.VISIBLE
        } else {
            pageLyricsContainer.visibility = View.GONE
            pageVinylAndArt.visibility = View.VISIBLE
        }
        updateFlatDashIndicators()
    }

    private fun updateFlatDashIndicators() {
        if (isLyricsPageVisible) {
            viewDashIndicatorLeft.setBackgroundColor(Color.parseColor("#44FFFFFF"))
            viewDashIndicatorRight.setBackgroundColor(activeAccentColor)
        } else {
            viewDashIndicatorLeft.setBackgroundColor(activeAccentColor)
            viewDashIndicatorRight.setBackgroundColor(Color.parseColor("#44FFFFFF"))
        }
    }

    private fun refreshCustomizableToolSlotsText() {
        val slots = arrayOf(btnToolSlot1, btnToolSlot2, btnToolSlot3, btnToolSlot4)
        for (i in 0..3) {
            val toolType = toolSlotMapping[i]
            val tv = slots[i]
            when (toolType) {
                0 -> {
                    tv.text = "ttt"
                    tv.setTextColor(Color.WHITE)
                }
                1 -> {
                    tv.text = when {
                        pointA != -1L && pointB != -1L -> "A⇌B ON"
                        pointA != -1L -> "A Set"
                        else -> "A⇌B"
                    }
                    tv.setTextColor(if (pointA != -1L) activeAccentColor else Color.WHITE)
                }
                2 -> {
                    tv.text = String.format(Locale.US, "%.1fx", currentPlaybackSpeed)
                    tv.setTextColor(if (abs(currentPlaybackSpeed - 1.0f) > 0.05f) activeAccentColor else Color.WHITE)
                }
                3 -> {
                    if (sleepTimer == null) {
                        tv.text = "Timer"
                        tv.setTextColor(Color.WHITE)
                    }
                }
            }
            tv.setOnClickListener {
                triggerToolAction(toolSlotMapping[i], tv)
            }
        }
    }

    private fun triggerToolAction(toolType: Int, targetView: TextView) {
        when (toolType) {
            0 -> EqualizerHelper.showEqualizerDialog(this, getSharedAudioSessionId())
            1 -> {
                val pos = sharedExoPlayer?.currentPosition ?: 0L
                when {
                    pointA == -1L -> {
                        pointA = pos
                        Toast.makeText(this, "Point A set at ${formatTime(pointA)}", Toast.LENGTH_SHORT).show()
                    }
                    pointB == -1L -> {
                        if (pos > pointA) {
                            pointB = pos
                            Toast.makeText(this, "Looping A to B", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Point B must be after Point A", Toast.LENGTH_SHORT).show()
                        }
                    }
                    else -> {
                        pointA = -1L
                        pointB = -1L
                        Toast.makeText(this, "A-B Repeat Cleared", Toast.LENGTH_SHORT).show()
                    }
                }
                refreshCustomizableToolSlotsText()
            }
            2 -> {
                val speeds = floatArrayOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                val nextIdx = (speeds.indexOfFirst { abs(it - currentPlaybackSpeed) < 0.05f } + 1) % speeds.size
                currentPlaybackSpeed = speeds[nextIdx]
                sharedExoPlayer?.playbackParameters = PlaybackParameters(currentPlaybackSpeed)
                refreshCustomizableToolSlotsText()
            }
            3 -> {
                val options = arrayOf("Timer Off", "15 Minutes", "30 Minutes", "60 Minutes")
                VideoFragment.showBrightVideoMenuDialog(this, "Sleep Timer", options) { which ->
                    sleepTimer?.cancel()
                    sleepTimer = null
                    if (which == 0) {
                        refreshCustomizableToolSlotsText()
                        Toast.makeText(this, "Sleep Timer Off", Toast.LENGTH_SHORT).show()
                    } else {
                        val mins = when (which) {
                            1 -> 15L
                            2 -> 30L
                            else -> 60L
                        }
                        sleepTimer = object : CountDownTimer(mins * 60 * 1000L, 1000L) {
                            override fun onTick(millisUntilFinished: Long) {
                                val m = millisUntilFinished / 60000L
                                val s = (millisUntilFinished % 60000L) / 1000L
                                targetView.text = String.format(Locale.US, "%02d:%02d", m, s)
                                targetView.setTextColor(activeAccentColor)
                            }
                            override fun onFinish() {
                                sharedExoPlayer?.pause()
                                sleepTimer = null
                                refreshCustomizableToolSlotsText()
                            }
                        }.start()
                        Toast.makeText(this, "Music will stop in $mins minutes", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setupAllControlClicks() {
        findViewById<ImageView>(R.id.btnAudioBack).setOnClickListener { finish() }

        btnAudioShareTop.setOnClickListener {
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/*"
                    putExtra(Intent.EXTRA_STREAM, Uri.parse(currentAudioPath))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Share Song"))
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to share song", Toast.LENGTH_SHORT).show()
            }
        }

        // 3-Dots Menu with Custom Photo, Vinyl/Square Switch, Waveform/Line Switch, Song Details & Bottom Replace/Swap Tool Icons
        btnAudioThreeDots.setOnClickListener {
            val prefs = getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
            val hasCustomArt = !prefs.getString("custom_art_$currentAudioPath", null).isNullOrEmpty()
            val menuItems = arrayOf(
                "Set Custom Photo from Gallery (Save Permanently)",
                if (hasCustomArt) "Remove Custom Photo (Reset Default)" else "Switch Rotating Vinyl Disc / Square Box",
                if (isSquareAlbumStyle) "Center Style: Switch to Rotating Vinyl Disc" else "Center Style: Switch to Square Album Box",
                if (isWaveformMode) "Seekbar Style: Switch to Simple Straight Line" else "Seekbar Style: Switch to Waveform Bars (|||||)",
                "Song Details & File Info",
                "Replace / Swap Tool Icons Position (Equalizer, A-B, Speed, Timer)"
            )

            VideoFragment.showBrightVideoMenuDialog(this, "Audio Player Options", menuItems) { which ->
                when (which) {
                    0 -> pickCustomCoverPhotoFromGallery()
                    1 -> {
                        if (hasCustomArt) {
                            prefs.edit().remove("custom_art_$currentAudioPath").apply()
                            refreshCurrentSongMetadataAndArt()
                            Toast.makeText(this, "Custom Photo Removed", Toast.LENGTH_SHORT).show()
                        } else {
                            toggleSquareVsVinylStyle()
                        }
                    }
                    2 -> toggleSquareVsVinylStyle()
                    3 -> {
                        isWaveformMode = !isWaveformMode
                        prefs.edit().putBoolean("waveform_scrubber_mode", isWaveformMode).apply()
                        updateScrubberModeVisual()
                    }
                    4 -> showSongDetailsDialog()
                    5 -> showReplaceToolIconsOrderDialog()
                }
            }
        }

        // Add to Favorite Button inside Lyrics Page
        btnLyricsAddToFavorite.setOnClickListener {
            val prefs = getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
            val curFav = prefs.getBoolean("fav_audio_$currentAudioPath", false)
            prefs.edit().putBoolean("fav_audio_$currentAudioPath", !curFav).apply()
            refreshCurrentSongMetadataAndArt()
            Toast.makeText(
                this,
                if (!curFav) "Added to Favorite Songs ♥" else "Removed from Favorite Songs",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnSearchLyricsOnline.setOnClickListener {
            AudioLyricsHelper.openOnlineLyricsSearch(this, currentAudioTitle, currentAudioArtist)
        }

        seekBarAudioStraight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    sharedExoPlayer?.seekTo(progress.toLong())
                    tvAudioCurrentTime.text = formatTime(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        btnAudioShuffle.setOnClickListener {
            val player = sharedExoPlayer ?: return@setOnClickListener
            val nextShuffle = !player.shuffleModeEnabled
            player.shuffleModeEnabled = nextShuffle
            btnAudioShuffle.setTextColor(if (nextShuffle) activeAccentColor else Color.WHITE)
            Toast.makeText(this, if (nextShuffle) "Shuffle ON" else "Shuffle OFF", Toast.LENGTH_SHORT).show()
        }

        btnAudioPrev.setOnClickListener { playPreviousTrack() }

        btnAudioPlayPause.setOnClickListener {
            sharedExoPlayer?.let { player ->
                if (player.isPlaying) player.pause() else player.play()
            }
        }

        btnAudioNext.setOnClickListener { playNextTrack() }

        btnAudioLoopMode.setOnClickListener {
            val player = sharedExoPlayer ?: return@setOnClickListener
            if (player.repeatMode == Player.REPEAT_MODE_ONE) {
                player.repeatMode = Player.REPEAT_MODE_ALL
                btnAudioLoopMode.text = "🔁"
                Toast.makeText(this, "Loop All Songs", Toast.LENGTH_SHORT).show()
            } else {
                player.repeatMode = Player.REPEAT_MODE_ONE
                btnAudioLoopMode.text = "🔂"
                Toast.makeText(this, "Repeat Current Song", Toast.LENGTH_SHORT).show()
            }
        }

        // Bottom-Left Corner: Pocket Shake Toggle (Default OFF)
        btnBottomLeftPocketShake.setOnClickListener {
            isPocketShakeEnabled = !isPocketShakeEnabled
            getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("double_shake_enabled", isPocketShakeEnabled)
                .apply()
            updatePocketShakeVisual()
            Toast.makeText(
                this,
                if (isPocketShakeEnabled) "Pocket Shake ON (Shake 2x for Next Song)" else "Pocket Shake OFF",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Bottom-Right Corner AND Right-Edge Tab (<): Slide Out Playing Queue from Right to Left
        btnBottomRightPlayingQueue.setOnClickListener { openRightSlidingQueueDrawer() }
        btnSideQueueTab.setOnClickListener { openRightSlidingQueueDrawer() }

        // Close Queue by clicking Top-Right X OR tapping anywhere outside on the display
        btnCloseQueueDrawer.setOnClickListener { closeRightSlidingQueueDrawer() }
        scrimQueueOutside.setOnClickListener { closeRightSlidingQueueDrawer() }
    }

    private fun openRightSlidingQueueDrawer() {
        rvSlidingAudioQueue.adapter?.notifyDataSetChanged()
        scrimQueueOutside.visibility = View.VISIBLE
        drawerPlayingQueueRight.visibility = View.VISIBLE
        drawerPlayingQueueRight.translationX = 900f
        drawerPlayingQueueRight.animate()
            .translationX(0f)
            .setDuration(240)
            .start()
        rvSlidingAudioQueue.scrollToPosition(sharedIndex.coerceAtLeast(0))
    }

    private fun closeRightSlidingQueueDrawer() {
        drawerPlayingQueueRight.animate()
            .translationX(drawerPlayingQueueRight.width.toFloat().coerceAtLeast(800f))
            .setDuration(220)
            .withEndAction {
                drawerPlayingQueueRight.visibility = View.GONE
                scrimQueueOutside.visibility = View.GONE
            }
            .start()
    }

    private fun setupSlidingQueueRecyclerView() {
        val d = resources.displayMetrics.density
        rvSlidingAudioQueue.layoutManager = LinearLayoutManager(this)
        rvSlidingAudioQueue.adapter = object : RecyclerView.Adapter<QueueSongVH>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueSongVH {
                // Compact 48dp height so 10 to 11 songs fit cleanly in the sliding queue
                val row = LinearLayout(this@AudioPlayerActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (48 * d).toInt()
                    )
                    setPadding((6 * d).toInt(), (3 * d).toInt(), (4 * d).toInt(), (3 * d).toInt())
                }

                val textCol = LinearLayout(this@AudioPlayerActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                val tvTitle = TextView(this@AudioPlayerActivity).apply {
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }

                val tvSinger = TextView(this@AudioPlayerActivity).apply {
                    textSize = 10.5f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }

                textCol.addView(tvTitle)
                textCol.addView(tvSinger)

                val btnRemoveSong = TextView(this@AudioPlayerActivity).apply {
                    text = "✕"
                    setTextColor(Color.parseColor("#888899"))
                    textSize = 13f
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams((30 * d).toInt(), (36 * d).toInt())
                }

                row.addView(textCol)
                row.addView(btnRemoveSong)
                return QueueSongVH(row, tvTitle, tvSinger, btnRemoveSong)
            }

            override fun onBindViewHolder(holder: QueueSongVH, position: Int) {
                val title = sharedTitles.getOrElse(position) { "Song ${position + 1}" }
                val singer = sharedArtists.getOrElse(position) { "Unknown Artist" }
                val isCurrent = position == sharedIndex

                holder.tvTitle.text = "${position + 1}. $title"
                holder.tvTitle.setTextColor(if (isCurrent) activeAccentColor else Color.WHITE)

                holder.tvSinger.text = singer
                holder.tvSinger.setTextColor(
                    if (isCurrent) activeAccentColor else Color.parseColor("#AAAA99")
                )

                holder.itemView.setOnClickListener {
                    val idx = holder.adapterPosition
                    if (idx in sharedPaths.indices) {
                        sharedIndex = idx
                        sharedExoPlayer?.seekToDefaultPosition(idx)
                        sharedExoPlayer?.play()
                        refreshCurrentSongMetadataAndArt()
                        notifyDataSetChanged()
                    }
                }

                holder.btnRemoveSong.setOnClickListener {
                    val idx = holder.adapterPosition
                    if (sharedPaths.size > 1 && idx in sharedPaths.indices) {
                        sharedPaths.removeAt(idx)
                        if (idx in sharedTitles.indices) sharedTitles.removeAt(idx)
                        if (idx in sharedArtists.indices) sharedArtists.removeAt(idx)
                        sharedExoPlayer?.removeMediaItem(idx)
                        sharedIndex = sharedExoPlayer?.currentMediaItemIndex?.coerceAtLeast(0) ?: 0
                        refreshCurrentSongMetadataAndArt()
                        notifyDataSetChanged()
                    }
                }
            }

            override fun getItemCount(): Int = sharedPaths.size
        }
    }

    private class QueueSongVH(
        view: View,
        val tvTitle: TextView,
        val tvSinger: TextView,
        val btnRemoveSong: TextView
    ) : RecyclerView.ViewHolder(view)

    private fun showSongDetailsDialog() {
        val file = File(currentAudioPath)
        val sizeMb = if (file.exists()) {
            String.format(Locale.US, "%.2f MB", file.length() / (1024.0 * 1024.0))
        } else {
            "Unknown"
        }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Song Details")
            .setMessage(
                "Title: $currentAudioTitle\n\n" +
                    "Singer / Artist: $currentAudioArtist\n\n" +
                    "Duration: ${formatTime(sharedExoPlayer?.duration ?: 0L)}\n\n" +
                    "Size: $sizeMb\n\n" +
                    "Location: $currentAudioPath"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showReplaceToolIconsOrderDialog() {
        val presets = arrayOf(
            "Default: Equalizer | A⇌B | Speed | Timer",
            "Layout 2: Timer | Speed | A⇌B | Equalizer",
            "Layout 3: Speed | Equalizer | Timer | A⇌B",
            "Layout 4: A⇌B | Timer | Equalizer | Speed"
        )
        val orders = arrayOf("0,1,2,3", "3,2,1,0", "2,0,3,1", "1,3,0,2")

        VideoFragment.showBrightVideoMenuDialog(
            this,
            "Replace / Swap Tool Icons Order",
            presets
        ) { which ->
            val chosen = orders[which]
            getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
                .edit()
                .putString("tool_slot_order", chosen)
                .apply()
            val parts = chosen.split(",")
            for (i in 0..3) {
                toolSlotMapping[i] = parts[i].toInt()
            }
            refreshCustomizableToolSlotsText()
            Toast.makeText(this, "Tool Icons Order Updated!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleSquareVsVinylStyle() {
        isSquareAlbumStyle = !isSquareAlbumStyle
        getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("square_album_style", isSquareAlbumStyle)
            .apply()
        updateCenterArtStyleVisual()
    }

    private fun pickCustomCoverPhotoFromGallery() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            startActivityForResult(intent, REQ_PICK_CUSTOM_PHOTO)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open gallery", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_CUSTOM_PHOTO && resultCode == Activity.RESULT_OK) {
            val imageUri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(
                    imageUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
                .edit()
                .putString("custom_art_$currentAudioPath", imageUri.toString())
                .apply()
            refreshCurrentSongMetadataAndArt()
            Toast.makeText(this, "Custom Song Photo Saved Permanently!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playPreviousTrack() {
        sharedExoPlayer?.let { player ->
            if (player.hasPreviousMediaItem()) {
                player.seekToPreviousMediaItem()
                player.play()
            } else {
                player.seekTo(0)
            }
        }
    }

    private fun playNextTrack() {
        sharedExoPlayer?.let { player ->
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.play()
            } else if (sharedPaths.isNotEmpty()) {
                player.seekToDefaultPosition(0)
                player.play()
            }
        }
    }

    // Media Notification with Play/Pause, Previous & Next Controls
    private fun showOrUpdateMediaNotification() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    NOTIF_CHANNEL_ID,
                    "MAX Music Playback",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows currently playing song controls"
                    setSound(null, null)
                }
                nm.createNotificationChannel(channel)
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val openIntent = Intent(this, AudioPlayerActivity::class.java).apply {
                putExtra("RESUME_EXISTING", true)
            }
            val contentPi = PendingIntent.getActivity(this, 801, openIntent, flags)
            val prevPi = PendingIntent.getBroadcast(this, 802, Intent(ACTION_NOTIF_PREV).setPackage(packageName), flags)
            val playPausePi = PendingIntent.getBroadcast(this, 803, Intent(ACTION_NOTIF_PLAY_PAUSE).setPackage(packageName), flags)
            val nextPi = PendingIntent.getBroadcast(this, 804, Intent(ACTION_NOTIF_NEXT).setPackage(packageName), flags)

            val isPlaying = sharedExoPlayer?.isPlaying == true
            val notif = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(currentAudioTitle)
                .setContentText(currentAudioArtist)
                .setContentIntent(contentPi)
                .setOngoing(isPlaying)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(android.R.drawable.ic_media_previous, "Prev", prevPi)
                .addAction(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (isPlaying) "Pause" else "Play",
                    playPausePi
                )
                .addAction(android.R.drawable.ic_media_next, "Next", nextPi)
                .build()

            nm.notify(NOTIF_ID, notif)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun formatTime(millis: Long): String {
        val safe = millis.coerceAtLeast(0L)
        val mins = TimeUnit.MILLISECONDS.toMinutes(safe)
        val secs = TimeUnit.MILLISECONDS.toSeconds(safe) % 60
        return String.format(Locale.US, "%d:%02d", mins, secs)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(spinAndProgressRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        pocketShakeDetector.stopListening()
        handler.removeCallbacks(spinAndProgressRunnable)
        try {
            if (isNotifReceiverRegistered) {
                unregisterReceiver(notifReceiver)
                isNotifReceiverRegistered = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Custom Waveform Bar Scrubber matching Screenshot 1 (|||||vertical bars|||||)
    private class WaveformScrubberView(
        context: Context,
        private val onSeekRatio: (Float) -> Unit
    ) : View(context) {

        private var progressRatio = 0f
        private val d = context.resources.displayMetrics.density
        private val playedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00FFFF")
            strokeWidth = 3f * d
            strokeCap = Paint.Cap.ROUND
        }
        private val unplayedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3D3D56")
            strokeWidth = 3f * d
            strokeCap = Paint.Cap.ROUND
        }

        fun setActiveColor(color: Int) {
            playedPaint.color = color
            invalidate()
        }

        fun setProgressRatio(ratio: Float) {
            progressRatio = ratio.coerceIn(0f, 1f)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val barCount = 58
            val spacing = w / barCount.toFloat()
            val centerY = h / 2f

            for (i in 0 until barCount) {
                val barFrac = i.toFloat() / barCount.toFloat()
                val x = (i + 0.5f) * spacing
                val waveFactor = (0.22f + 0.72f * abs(sin(i * 0.48))).toFloat()
                val halfBarH = (h * 0.44f) * waveFactor
                val paint = if (barFrac <= progressRatio) playedPaint else unplayedPaint
                canvas.drawLine(x, centerY - halfBarH, x, centerY + halfBarH, paint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                    val ratio = (event.x / width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                    progressRatio = ratio
                    invalidate()
                    onSeekRatio(ratio)
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }

    // Subtle concentric grooves on the dark vinyl platter
    private class VinylGroovesDrawable(private val d: Float) : Drawable() {
        private val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#252538")
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * d
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val maxR = b.width().coerceAtMost(b.height()) / 2f
            var r = maxR * 0.58f
            while (r < maxR * 0.95f) {
                canvas.drawCircle(cx, cy, r, groovePaint)
                r += 6.5f * d
            }
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    // Crisp White Stylus Tonearm (Danda) positioned above the Vinyl Disc as in Screenshot 1
    private class WhiteStylusTonearmDrawable(private val d: Float) : Drawable() {
        private val armPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3.8f * d
            strokeCap = Paint.Cap.ROUND
        }
        private val pivotOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E8E8F2")
            style = Paint.Style.FILL
        }

        override fun draw(canvas: Canvas) {
            val px = 18f * d
            val py = 18f * d
            canvas.drawCircle(px, py, 9.5f * d, pivotOuterPaint)
            val arcRect = RectF(px, py - 4f * d, px + 115f * d, py + 45f * d)
            canvas.drawArc(arcRect, 200f, 52f, false, armPaint)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
