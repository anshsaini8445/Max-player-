package com.app.mediaplayer

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.bumptech.glide.Glide
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class AudioPlayerActivity : AppCompatActivity() {

    companion object {
        private const val REQ_PICK_CUSTOM_PHOTO = 7201
    }

    private var exoPlayer: ExoPlayer? = null
    private lateinit var pocketShakeDetector: PocketShakeDetector

    private lateinit var tvAudioPlayerTitle: TextView
    private lateinit var tvAudioPlayerArtist: TextView
    private lateinit var btnAudioTopEqualizer: ImageView
    private lateinit var btnAudioTopMenu: ImageView

    private lateinit var centerStageContainer: FrameLayout
    private lateinit var pageVinylAndArt: FrameLayout
    private lateinit var layoutVinylMode: FrameLayout
    private lateinit var cardVinylOuterDisc: CardView
    private lateinit var imgVinylCenterArt: ImageView
    private lateinit var imgVinylTonearm: ImageView
    private lateinit var cardSquareArtBox: CardView
    private lateinit var imgSquareArt: ImageView

    private lateinit var pageLyricsContainer: LinearLayout
    private lateinit var tvAudioLyricsText: TextView
    private lateinit var btnSearchLyricsOnline: TextView

    private lateinit var viewDashIndicatorLeft: View
    private lateinit var viewDashIndicatorRight: View

    private lateinit var btnAudioPocketShake: TextView
    private lateinit var btnAudioSpeed: TextView
    private lateinit var btnAudioSleepTimer: TextView
    private lateinit var btnAudioLoopToggle: TextView

    private lateinit var tvAudioCurrentTime: TextView
    private lateinit var tvAudioTotalTime: TextView
    private lateinit var seekBarAudio: SeekBar

    private lateinit var btnAudioFavoriteCorner: ImageView
    private lateinit var btnAudioPrev: ImageView
    private lateinit var btnAudioPlayPause: ImageView
    private lateinit var btnAudioNext: ImageView
    private lateinit var btnAudioQueueCorner: ImageView

    private val audioPaths = mutableListOf<String>()
    private val audioTitles = mutableListOf<String>()
    private val audioArtists = mutableListOf<String>()
    private var currentTrackIndex = 0

    private var currentAudioPath = ""
    private var currentAudioTitle = "Audio Track"
    private var currentAudioArtist = "Unknown Artist"

    private var isLyricsPageVisible = false
    private var isSquareAlbumStyle = false
    private var isPocketShakeEnabled = false
    private var currentPlaybackSpeed = 1.0f
    private var loopModeState = 0 // 0: Loop All, 1: Repeat One, 2: Shuffle
    private var discRotationAngle = 0f
    private var sleepTimer: CountDownTimer? = null

    private val handler = Handler(Looper.getMainLooper())

    private val vinylSpinAndProgressRunnable = object : Runnable {
        override fun run() {
            try {
                exoPlayer?.let { player ->
                    if (player.isPlaying) {
                        val pos = player.currentPosition.coerceAtLeast(0L)
                        val dur = player.duration.coerceAtLeast(1L)
                        seekBarAudio.max = dur.toInt()
                        seekBarAudio.progress = pos.toInt()
                        tvAudioCurrentTime.text = formatTime(pos)
                        tvAudioTotalTime.text = formatTime(dur)

                        if (!isSquareAlbumStyle && !isLyricsPageVisible) {
                            discRotationAngle = (discRotationAngle + 1.4f) % 360f
                            cardVinylOuterDisc.rotation = discRotationAngle
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            handler.postDelayed(this, 32)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_player)

        window.statusBarColor = Color.parseColor("#0A0A10")
        window.navigationBarColor = Color.parseColor("#0A0A10")

        bindViews()
        setupTonearmAndQueueIcons()
        restorePreferences()
        extractIntentExtras(intent)
        initializeAudioExoPlayer()
        setupSwipeBetweenVinylAndLyrics()
        setupControlClicks()
    }

    private fun bindViews() {
        tvAudioPlayerTitle = findViewById(R.id.tvAudioPlayerTitle)
        tvAudioPlayerArtist = findViewById(R.id.tvAudioPlayerArtist)
        btnAudioTopEqualizer = findViewById(R.id.btnAudioTopEqualizer)
        btnAudioTopMenu = findViewById(R.id.btnAudioTopMenu)

        centerStageContainer = findViewById(R.id.centerStageContainer)
        pageVinylAndArt = findViewById(R.id.pageVinylAndArt)
        layoutVinylMode = findViewById(R.id.layoutVinylMode)
        cardVinylOuterDisc = findViewById(R.id.cardVinylOuterDisc)
        imgVinylCenterArt = findViewById(R.id.imgVinylCenterArt)
        imgVinylTonearm = findViewById(R.id.imgVinylTonearm)
        cardSquareArtBox = findViewById(R.id.cardSquareArtBox)
        imgSquareArt = findViewById(R.id.imgSquareArt)

        pageLyricsContainer = findViewById(R.id.pageLyricsContainer)
        tvAudioLyricsText = findViewById(R.id.tvAudioLyricsText)
        btnSearchLyricsOnline = findViewById(R.id.btnSearchLyricsOnline)

        viewDashIndicatorLeft = findViewById(R.id.viewDashIndicatorLeft)
        viewDashIndicatorRight = findViewById(R.id.viewDashIndicatorRight)

        btnAudioPocketShake = findViewById(R.id.btnAudioPocketShake)
        btnAudioSpeed = findViewById(R.id.btnAudioSpeed)
        btnAudioSleepTimer = findViewById(R.id.btnAudioSleepTimer)
        btnAudioLoopToggle = findViewById(R.id.btnAudioLoopToggle)

        tvAudioCurrentTime = findViewById(R.id.tvAudioCurrentTime)
        tvAudioTotalTime = findViewById(R.id.tvAudioTotalTime)
        seekBarAudio = findViewById(R.id.seekBarAudio)

        btnAudioFavoriteCorner = findViewById(R.id.btnAudioFavoriteCorner)
        btnAudioPrev = findViewById(R.id.btnAudioPrev)
        btnAudioPlayPause = findViewById(R.id.btnAudioPlayPause)
        btnAudioNext = findViewById(R.id.btnAudioNext)
        btnAudioQueueCorner = findViewById(R.id.btnAudioQueueCorner)
    }

    private fun setupTonearmAndQueueIcons() {
        val d = resources.displayMetrics.density
        btnAudioQueueCorner.setImageDrawable(PlayerVectorIcon("QUEUE_LIST", d, Color.parseColor("#00FFFF")))
        imgVinylTonearm.setImageDrawable(TonearmVectorDrawable(d))
    }

    private fun restorePreferences() {
        val prefs = getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
        // Pocket Shake is OFF by default so songs never skip accidentally unless turned ON by user
        isPocketShakeEnabled = prefs.getBoolean("double_shake_enabled", false)
        isSquareAlbumStyle = prefs.getBoolean("square_album_style", false)

        pocketShakeDetector = PocketShakeDetector(this) {
            runOnUiThread {
                if (isPocketShakeEnabled) {
                    playNextTrack()
                    Toast.makeText(this, "Pocket Shake: Next Track", Toast.LENGTH_SHORT).show()
                }
            }
        }
        updatePocketShakeUi()
        updateCenterArtStyleUi()
    }

    private fun updatePocketShakeUi() {
        if (isPocketShakeEnabled) {
            btnAudioPocketShake.text = "Shake: ON"
            btnAudioPocketShake.setTextColor(Color.parseColor("#00E676"))
            pocketShakeDetector.startListening()
        } else {
            btnAudioPocketShake.text = "Shake: OFF"
            btnAudioPocketShake.setTextColor(Color.parseColor("#B0B0C0"))
            pocketShakeDetector.stopListening()
        }
    }

    private fun updateCenterArtStyleUi() {
        if (isSquareAlbumStyle) {
            layoutVinylMode.visibility = View.GONE
            cardSquareArtBox.visibility = View.VISIBLE
        } else {
            cardSquareArtBox.visibility = View.GONE
            layoutVinylMode.visibility = View.VISIBLE
        }
    }

    private fun extractIntentExtras(incomingIntent: Intent?) {
        if (incomingIntent == null) return
        currentAudioPath = incomingIntent.getStringExtra("AUDIO_PATH") ?: ""
        currentAudioTitle = incomingIntent.getStringExtra("AUDIO_TITLE") ?: File(currentAudioPath).name
        currentAudioArtist = incomingIntent.getStringExtra("AUDIO_ARTIST") ?: "Unknown Artist"
        currentTrackIndex = incomingIntent.getIntExtra("AUDIO_INDEX", 0)

        val pathsExtra = incomingIntent.getStringArrayListExtra("AUDIO_LIST")
        val titlesExtra = incomingIntent.getStringArrayListExtra("AUDIO_TITLES")
        val artistsExtra = incomingIntent.getStringArrayListExtra("AUDIO_ARTISTS")

        audioPaths.clear()
        audioTitles.clear()
        audioArtists.clear()

        if (!pathsExtra.isNullOrEmpty()) {
            audioPaths.addAll(pathsExtra)
            if (!titlesExtra.isNullOrEmpty() && titlesExtra.size == pathsExtra.size) {
                audioTitles.addAll(titlesExtra)
            } else {
                pathsExtra.forEach { audioTitles.add(File(it).name) }
            }
            if (!artistsExtra.isNullOrEmpty() && artistsExtra.size == pathsExtra.size) {
                audioArtists.addAll(artistsExtra)
            } else {
                pathsExtra.forEach { _ -> audioArtists.add(currentAudioArtist) }
            }
            currentTrackIndex = currentTrackIndex.coerceIn(0, audioPaths.size - 1)
        } else if (currentAudioPath.isNotEmpty()) {
            audioPaths.add(currentAudioPath)
            audioTitles.add(currentAudioTitle)
            audioArtists.add(currentAudioArtist)
            currentTrackIndex = 0
        }

        refreshCurrentSongMetadataAndArt()
    }

    private fun refreshCurrentSongMetadataAndArt() {
        if (currentTrackIndex in audioPaths.indices) {
            currentAudioPath = audioPaths[currentTrackIndex]
            currentAudioTitle = audioTitles.getOrElse(currentTrackIndex) { File(currentAudioPath).name }
            currentAudioArtist = audioArtists.getOrElse(currentTrackIndex) { "Unknown Artist" }
        }

        tvAudioPlayerTitle.text = currentAudioTitle
        tvAudioPlayerArtist.text = currentAudioArtist

        val prefs = getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
        val isFav = prefs.getBoolean("fav_audio_$currentAudioPath", false)
        btnAudioFavoriteCorner.setImageResource(
            if (isFav) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off
        )
        btnAudioFavoriteCorner.setColorFilter(
            if (isFav) Color.parseColor("#FF4081") else Color.WHITE
        )

        val customPhotoUri = prefs.getString("custom_art_$currentAudioPath", null)
        if (!customPhotoUri.isNullOrEmpty()) {
            imgVinylCenterArt.clearColorFilter()
            imgSquareArt.clearColorFilter()
            Glide.with(this)
                .load(Uri.parse(customPhotoUri))
                .placeholder(android.R.drawable.ic_lock_silent_mode_off)
                .centerCrop()
                .into(imgVinylCenterArt)
            Glide.with(this)
                .load(Uri.parse(customPhotoUri))
                .placeholder(android.R.drawable.ic_lock_silent_mode_off)
                .centerCrop()
                .into(imgSquareArt)
        } else {
            imgVinylCenterArt.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
            imgVinylCenterArt.setColorFilter(Color.parseColor("#00FFFF"))
            imgSquareArt.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
            imgSquareArt.setColorFilter(Color.parseColor("#00FFFF"))
        }

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

    private fun initializeAudioExoPlayer() {
        try {
            exoPlayer?.release()
            exoPlayer = ExoPlayer.Builder(this).build().also { player ->
                val mediaItems = audioPaths.map { path ->
                    val uri = if (path.startsWith("content://") || path.startsWith("http")) {
                        Uri.parse(path)
                    } else {
                        Uri.fromFile(File(path))
                    }
                    MediaItem.fromUri(uri)
                }
                player.setMediaItems(mediaItems, currentTrackIndex, 0L)
                player.repeatMode = Player.REPEAT_MODE_ALL
                player.prepare()
                player.playWhenReady = true

                player.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        btnAudioPlayPause.setImageResource(
                            if (isPlaying) android.R.drawable.ic_media_pause
                            else android.R.drawable.ic_media_play
                        )
                        animateTonearm(isPlaying)
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val newIdx = player.currentMediaItemIndex
                        if (newIdx in audioPaths.indices) {
                            currentTrackIndex = newIdx
                            refreshCurrentSongMetadataAndArt()
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            val dur = player.duration.coerceAtLeast(1L)
                            seekBarAudio.max = dur.toInt()
                            tvAudioTotalTime.text = formatTime(dur)
                        }
                    }
                })
            }
            handler.post(vinylSpinAndProgressRunnable)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to play audio track", Toast.LENGTH_SHORT).show()
        }
    }

    private fun animateTonearm(isPlaying: Boolean) {
        imgVinylTonearm.animate()
            .rotation(if (isPlaying) 2f else -24f)
            .setDuration(420)
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
                if (abs(diffX) > abs(diffY) && abs(diffX) > 90) {
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
            viewDashIndicatorLeft.setBackgroundColor(Color.parseColor("#44FFFFFF"))
            viewDashIndicatorRight.setBackgroundColor(Color.parseColor("#00FFFF"))
        } else {
            pageLyricsContainer.visibility = View.GONE
            pageVinylAndArt.visibility = View.VISIBLE
            viewDashIndicatorLeft.setBackgroundColor(Color.parseColor("#00FFFF"))
            viewDashIndicatorRight.setBackgroundColor(Color.parseColor("#44FFFFFF"))
        }
    }

    private fun setupControlClicks() {
        findViewById<ImageView>(R.id.btnAudioBack).setOnClickListener { finish() }

        btnAudioTopEqualizer.setOnClickListener {
            EqualizerHelper.showEqualizerDialog(this, exoPlayer?.audioSessionId ?: 0)
        }

        // 3-Dots Top Menu: Custom Photo from Gallery, Center Style Toggle, Pocket Shake, Equalizer
        btnAudioTopMenu.setOnClickListener {
            val prefs = getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
            val hasCustomArt = !prefs.getString("custom_art_$currentAudioPath", null).isNullOrEmpty()
            val menuItems = arrayOf(
                "Set Custom Photo from Gallery",
                if (hasCustomArt) "Remove Custom Photo (Reset Default)" else "Switch Vinyl Disc / Square Box Style",
                if (isSquareAlbumStyle) "Switch to Rotating Vinyl Disc" else "Switch to Square Album Art Box",
                if (isPocketShakeEnabled) "Pocket 2x Shake Sensor: ON (Tap to Disable)" else "Pocket 2x Shake Sensor: OFF (Tap to Enable)",
                if (isLyricsPageVisible) "Show Vinyl Disc Page" else "Show Song Lyrics Page",
                "Open 5-Band Dolby Equalizer"
            )

            VideoFragment.showBrightVideoMenuDialog(this, "Music Player Options", menuItems) { which ->
                when (which) {
                    0 -> pickCustomCoverPhotoFromGallery()
                    1 -> {
                        if (hasCustomArt) {
                            prefs.edit().remove("custom_art_$currentAudioPath").apply()
                            refreshCurrentSongMetadataAndArt()
                            Toast.makeText(this, "Custom Photo Reset", Toast.LENGTH_SHORT).show()
                        } else {
                            toggleSquareVsVinylStyle()
                        }
                    }
                    2 -> toggleSquareVsVinylStyle()
                    3 -> togglePocketShakeSensor()
                    4 -> switchCenterPage(!isLyricsPageVisible)
                    5 -> EqualizerHelper.showEqualizerDialog(this, exoPlayer?.audioSessionId ?: 0)
                }
            }
        }

        btnSearchLyricsOnline.setOnClickListener {
            AudioLyricsHelper.openOnlineLyricsSearch(this, currentAudioTitle, currentAudioArtist)
        }

        btnAudioPocketShake.setOnClickListener {
            togglePocketShakeSensor()
        }

        btnAudioSpeed.setOnClickListener {
            val speeds = floatArrayOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
            val nextIdx = (speeds.indexOfFirst { abs(it - currentPlaybackSpeed) < 0.05f } + 1) % speeds.size
            currentPlaybackSpeed = speeds[nextIdx]
            exoPlayer?.playbackParameters = PlaybackParameters(currentPlaybackSpeed)
            btnAudioSpeed.text = "${currentPlaybackSpeed}x"
        }

        btnAudioSleepTimer.setOnClickListener {
            val options = arrayOf("Timer Off", "15 Minutes", "30 Minutes", "60 Minutes")
            VideoFragment.showBrightVideoMenuDialog(this, "Music Sleep Timer", options) { which ->
                sleepTimer?.cancel()
                if (which == 0) {
                    btnAudioSleepTimer.text = "Timer"
                    Toast.makeText(this, "Sleep Timer Disabled", Toast.LENGTH_SHORT).show()
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
                            btnAudioSleepTimer.text = String.format(Locale.US, "%02d:%02d", m, s)
                        }
                        override fun onFinish() {
                            exoPlayer?.pause()
                            btnAudioSleepTimer.text = "Timer"
                        }
                    }.start()
                    Toast.makeText(this, "Music will stop in $mins minutes", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnAudioLoopToggle.setOnClickListener {
            loopModeState = (loopModeState + 1) % 3
            exoPlayer?.let { player ->
                when (loopModeState) {
                    0 -> {
                        player.shuffleModeEnabled = false
                        player.repeatMode = Player.REPEAT_MODE_ALL
                        btnAudioLoopToggle.text = "Loop All"
                    }
                    1 -> {
                        player.shuffleModeEnabled = false
                        player.repeatMode = Player.REPEAT_MODE_ONE
                        btnAudioLoopToggle.text = "Repeat 1"
                    }
                    2 -> {
                        player.shuffleModeEnabled = true
                        player.repeatMode = Player.REPEAT_MODE_ALL
                        btnAudioLoopToggle.text = "Shuffle"
                    }
                }
            }
        }

        seekBarAudio.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    exoPlayer?.seekTo(progress.toLong())
                    tvAudioCurrentTime.text = formatTime(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        // Bottom Left Corner: Favorite Toggle
        btnAudioFavoriteCorner.setOnClickListener {
            val prefs = getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
            val curFav = prefs.getBoolean("fav_audio_$currentAudioPath", false)
            prefs.edit().putBoolean("fav_audio_$currentAudioPath", !curFav).apply()
            refreshCurrentSongMetadataAndArt()
            Toast.makeText(
                this,
                if (!curFav) "Added to Favorite Songs" else "Removed from Favorite Songs",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnAudioPrev.setOnClickListener {
            exoPlayer?.let { player ->
                if (player.hasPreviousMediaItem()) {
                    player.seekToPreviousMediaItem()
                    player.play()
                } else {
                    player.seekTo(0)
                }
            }
        }

        btnAudioPlayPause.setOnClickListener {
            exoPlayer?.let { player ->
                if (player.isPlaying) player.pause() else player.play()
            }
        }

        btnAudioNext.setOnClickListener {
            playNextTrack()
        }

        // Bottom Right Corner: Sliding Playing Queue (Shows 10-11 songs cleanly)
        btnAudioQueueCorner.setOnClickListener {
            showAudioPlayingQueueSheet()
        }
    }

    private fun playNextTrack() {
        exoPlayer?.let { player ->
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.play()
            } else if (audioPaths.isNotEmpty()) {
                player.seekToDefaultPosition(0)
                player.play()
            }
        }
    }

    private fun togglePocketShakeSensor() {
        isPocketShakeEnabled = !isPocketShakeEnabled
        getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("double_shake_enabled", isPocketShakeEnabled)
            .apply()
        updatePocketShakeUi()
        Toast.makeText(
            this,
            if (isPocketShakeEnabled) "Pocket 2x Shake Enabled" else "Pocket 2x Shake Disabled",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun toggleSquareVsVinylStyle() {
        isSquareAlbumStyle = !isSquareAlbumStyle
        getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("square_album_style", isSquareAlbumStyle)
            .apply()
        updateCenterArtStyleUi()
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
            Toast.makeText(this, "Custom Cover Photo Saved Permanently!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAudioPlayingQueueSheet() {
        val d = resources.displayMetrics.density
        val rootCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt(), (12 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141422"))
                cornerRadius = 22f * d
                setStroke((1.5f * d).toInt(), Color.parseColor("#00FFFF"))
            }
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((6 * d).toInt(), 0, (6 * d).toInt(), (10 * d).toInt())
        }
        val tvHeader = TextView(this).apply {
            text = "Playing Queue (${currentTrackIndex + 1}/${audioPaths.size})"
            setTextColor(Color.parseColor("#00FFFF"))
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerRow.addView(tvHeader)
        rootCard.addView(headerRow)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (390 * d).toInt()
            )
        }
        val listCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val dialog = AlertDialog.Builder(this)
            .setView(rootCard)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        audioPaths.forEachIndexed { idx, _ ->
            val title = audioTitles.getOrElse(idx) { "Song ${idx + 1}" }
            val isCurrent = idx == currentTrackIndex
            val itemRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt())
            }
            val tvSong = TextView(this).apply {
                text = if (isCurrent) "▶  ${idx + 1}. $title" else "${idx + 1}. $title"
                setTextColor(if (isCurrent) Color.parseColor("#00E676") else Color.WHITE)
                textSize = 13.5f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    currentTrackIndex = idx
                    exoPlayer?.seekToDefaultPosition(idx)
                    exoPlayer?.play()
                    refreshCurrentSongMetadataAndArt()
                    dialog.dismiss()
                }
            }
            val btnRemove = TextView(this).apply {
                text = "✕"
                setTextColor(Color.parseColor("#AAAA99"))
                textSize = 14f
                setPadding((10 * d).toInt(), (2 * d).toInt(), (6 * d).toInt(), (2 * d).toInt())
                setOnClickListener {
                    if (audioPaths.size > 1 && idx in audioPaths.indices) {
                        audioPaths.removeAt(idx)
                        if (idx in audioTitles.indices) audioTitles.removeAt(idx)
                        if (idx in audioArtists.indices) audioArtists.removeAt(idx)
                        exoPlayer?.removeMediaItem(idx)
                        currentTrackIndex = exoPlayer?.currentMediaItemIndex?.coerceAtLeast(0) ?: 0
                        dialog.dismiss()
                        showAudioPlayingQueueSheet()
                    }
                }
            }
            itemRow.addView(tvSong)
            itemRow.addView(btnRemove)
            listCol.addView(itemRow)
        }

        scroll.addView(listCol)
        rootCard.addView(scroll)
        dialog.show()
    }

    private fun formatTime(millis: Long): String {
        val safe = millis.coerceAtLeast(0L)
        val mins = TimeUnit.MILLISECONDS.toMinutes(safe)
        val secs = TimeUnit.MILLISECONDS.toSeconds(safe) % 60
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    override fun onDestroy() {
        super.onDestroy()
        sleepTimer?.cancel()
        pocketShakeDetector.stopListening()
        handler.removeCallbacks(vinylSpinAndProgressRunnable)
        exoPlayer?.release()
        exoPlayer = null
    }

    // Crisp Custom Drawable for the Moving Vinyl Stylus Tonearm
    private class TonearmVectorDrawable(private val d: Float) : Drawable() {
        private val armPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E0E0EC")
            style = Paint.Style.STROKE
            strokeWidth = 4.2f * d
            strokeCap = Paint.Cap.ROUND
        }
        private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00FFFF")
            style = Paint.Style.FILL
        }
        private val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#252538")
            style = Paint.Style.FILL
        }

        override fun draw(canvas: Canvas) {
            val pivotX = 72f * d
            val pivotY = 14f * d
            canvas.drawCircle(pivotX, pivotY, 12f * d, darkPaint)
            canvas.drawCircle(pivotX, pivotY, 6f * d, basePaint)
            canvas.drawLine(pivotX, pivotY, 44f * d, 84f * d, armPaint)
            canvas.drawLine(44f * d, 84f * d, 24f * d, 112f * d, armPaint)
            canvas.drawCircle(22f * d, 114f * d, 5.5f * d, basePaint)
        }

        override fun setAlpha(alpha: Int) {
            armPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            armPaint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
