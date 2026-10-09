package com.app.mediaplayer

import android.Manifest
import android.app.PictureInPictureParams
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var playerView: PlayerView
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var layoutMe: View
    private lateinit var layoutMusic: View
    private lateinit var musicRecycler: RecyclerView
    private lateinit var tvMiniTitle: TextView
    private lateinit var tvMiniArtist: TextView
    private lateinit var btnMiniPlayPause: ImageView
    private lateinit var ivMiniVinyl: ImageView

    private val videoList = mutableListOf<Video>()
    private val audioList = mutableListOf<Audio>()
    private var player: ExoPlayer? = null
    private var currentAudio: Audio? = null

    private var touchStartY = 0f
    private var brightnessValue = 0f
    private var audioManager: AudioManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        playerView = findViewById(R.id.playerView)
        bottomNav = findViewById(R.id.bottomNav)
        layoutMe = findViewById(R.id.layoutMe)
        layoutMusic = findViewById(R.id.layoutMusic)

        musicRecycler = layoutMusic.findViewById(R.id.music_recycler)
        tvMiniTitle = layoutMusic.findViewById(R.id.tvMiniTitle)
        tvMiniArtist = layoutMusic.findViewById(R.id.tvMiniArtist)
        btnMiniPlayPause = layoutMusic.findViewById(R.id.btnMiniPlayPause)
        ivMiniVinyl = layoutMusic.findViewById(R.id.ivMiniVinyl)

        recyclerView.layoutManager = LinearLayoutManager(this)
        musicRecycler.layoutManager = LinearLayoutManager(this)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        btnMiniPlayPause.setOnClickListener {
            player?.let {
                if (it.isPlaying) {
                    it.pause()
                    btnMiniPlayPause.setImageResource(android.R.drawable.ic_media_play)
                } else {
                    it.play()
                    btnMiniPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                }
            }
        }

        setupBottomNav()
        checkPermissions()
    }

    private fun setupBottomNav() {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_video -> {
                    layoutMe.visibility = View.GONE
                    layoutMusic.visibility = View.GONE
                    playerView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    true
                }
                R.id.nav_music -> {
                    layoutMe.visibility = View.GONE
                    recyclerView.visibility = View.GONE
                    playerView.visibility = View.GONE
                    layoutMusic.visibility = View.VISIBLE
                    loadLocalAudio()
                    true
                }
                R.id.nav_me -> {
                    recyclerView.visibility = View.GONE
                    layoutMusic.visibility = View.GONE
                    playerView.visibility = View.GONE
                    layoutMe.visibility = View.VISIBLE
                    true
                }
                R.id.nav_effects, R.id.nav_game -> {
                    layoutMe.visibility = View.GONE
                    layoutMusic.visibility = View.GONE
                    recyclerView.visibility = View.GONE
                    playerView.visibility = View.GONE
                    Toast.makeText(this, "Coming Soon", Toast.LENGTH_SHORT).show()
                    false
                }
                else -> false
            }
        }
    }

    private fun checkPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 100)
        } else {
            loadLocalVideos()
            loadLocalAudio()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadLocalVideos()
            loadLocalAudio()
        } else {
            Toast.makeText(this, "Permission Denied!", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadLocalVideos() {
        videoList.clear()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE
        )

        val cursor = contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            null
        )

        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val titleCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val pathCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val durationCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val title = it.getString(titleCol) ?: "Unknown"
                val path = it.getString(pathCol) ?: ""
                val duration = it.getLong(durationCol)
                val size = it.getLong(sizeCol)
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                videoList.add(Video(id, title, path, uri, duration, size))
            }
        }

        recyclerView.adapter = VideoAdapter(videoList) {
            playVideo(it)
        }
    }

    private fun loadLocalAudio() {
        audioList.clear()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        val cursor = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )

        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val pathCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val title = it.getString(titleCol) ?: "Unknown Title"
                val path = it.getString(pathCol) ?: ""
                val artist = it.getString(artistCol) ?: "Unknown Artist"
                val duration = it.getLong(durationCol)

                audioList.add(Audio(id, title, path, artist, duration))
            }
        }

        musicRecycler.adapter = AudioAdapter(audioList) {
            playAudio(it)
        }
    }

    private fun playVideo(video: Video) {
        layoutMe.visibility = View.GONE
        layoutMusic.visibility = View.GONE
        recyclerView.visibility = View.GONE
        playerView.visibility = View.VISIBLE

        player?.release()
        player = ExoPlayer.Builder(this).build().apply {
            playerView.player = this
            setMediaItem(MediaItem.fromUri(video.uri))
            prepare()
            play()
        }

        // Safely map custom buttons from playerView after it's visible and inflated
        playerView.post {
            val btnLock = playerView.findViewById<ImageView>(R.id.btn_lock)
            val btnCut = playerView.findViewById<ImageView>(R.id.btn_cut)
            val btnPip = playerView.findViewById<ImageView>(R.id.btn_pip)
            val btnMute = playerView.findViewById<ImageView>(R.id.btn_mute)
            val btnBack = playerView.findViewById<ImageView>(R.id.btn_back)
            val exoTitle = playerView.findViewById<TextView>(R.id.exo_title)
            val gestureView = playerView.findViewById<View>(R.id.gesture_view)
            val gestureText = playerView.findViewById<TextView>(R.id.gesture_text)

            exoTitle?.text = video.title

            btnLock?.setOnClickListener {
                Toast.makeText(this, "Feature Coming Soon", Toast.LENGTH_SHORT).show()
            }

            btnCut?.setOnClickListener {
                Toast.makeText(this, "Feature Coming Soon", Toast.LENGTH_SHORT).show()
            }

            btnPip?.setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    enterPictureInPictureMode(PictureInPictureParams.Builder().build())
                }
            }

            btnMute?.setOnClickListener {
                Toast.makeText(this, "Feature Coming Soon", Toast.LENGTH_SHORT).show()
            }

            btnBack?.setOnClickListener {
                onBackPressed()
            }

            // Gesture Handling
            try {
                brightnessValue = window.attributes.screenBrightness
                if (brightnessValue < 0) brightnessValue = 0.5f
            } catch (e: Exception) {
                brightnessValue = 0.5f
            }

            gestureView?.setOnTouchListener { v, event ->
                val screenWidth = v.width
                val screenHeight = v.height

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        touchStartY = event.y
                        try {
                            brightnessValue = window.attributes.screenBrightness
                            if (brightnessValue < 0) brightnessValue = 0.5f
                        } catch (e: Exception) {
                            brightnessValue = 0.5f
                        }
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaY = touchStartY - event.y
                        val changeFactor = deltaY / screenHeight

                        if (event.x < screenWidth / 2) {
                            // Left half: Brightness
                            var newBrightness = brightnessValue + changeFactor
                            if (newBrightness > 1.0f) newBrightness = 1.0f
                            if (newBrightness < 0.0f) newBrightness = 0.0f

                            val layoutParams = window.attributes
                            layoutParams.screenBrightness = newBrightness
                            window.attributes = layoutParams

                            val percent = (newBrightness * 100).toInt()
                            gestureText?.text = "Brightness: $percent%"
                            gestureText?.visibility = View.VISIBLE
                        } else {
                            // Right half: Volume
                            audioManager?.let {
                                val maxVolume = it.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val currentVolume = it.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val volumeDelta = (changeFactor * maxVolume).toInt()
                                var newVolume = currentVolume + volumeDelta
                                if (newVolume > maxVolume) newVolume = maxVolume
                                if (newVolume < 0) newVolume = 0

                                it.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                                val percent = (newVolume * 100) / maxVolume
                                gestureText?.text = "Volume: $percent%"
                                gestureText?.visibility = View.VISIBLE
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        gestureText?.visibility = View.GONE
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun playAudio(audio: Audio) {
        currentAudio = audio
        tvMiniTitle.text = audio.title
        tvMiniArtist.text = audio.artist
        btnMiniPlayPause.setImageResource(android.R.drawable.ic_media_pause)

        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audio.id)
        player?.release()
        player = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            play()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (playerView.visibility == View.VISIBLE && player?.isPlaying == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                enterPictureInPictureMode(PictureInPictureParams.Builder().build())
            }
        }
    }

    override fun onBackPressed() {
        if (playerView.visibility == View.VISIBLE) {
            player?.stop()
            player?.release()
            player = null
            playerView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}