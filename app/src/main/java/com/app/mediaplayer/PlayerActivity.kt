package com.app.mediaplayer

import android.app.PictureInPictureParams
import android.content.Context
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : AppCompatActivity() {

    private var exoPlayer: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var btnPipMode: ImageView
    
    // Gesture UI Components
    private lateinit var layoutGestureFeedback: LinearLayout
    private lateinit var imgGestureIcon: ImageView
    private lateinit var tvGestureValue: TextView
    
    // Gesture Detection
    private lateinit var gestureDetector: GestureDetectorCompat
    private lateinit var audioManager: AudioManager
    private var maxVolume = 0
    private var currentVolume = 0
    private var currentBrightness = 0.5f
    
    private val gestureFeedbackHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        hideSystemUI()

        playerView = findViewById(R.id.playerView)
        btnPipMode = findViewById(R.id.btnPipMode)
        layoutGestureFeedback = findViewById(R.id.layoutGestureFeedback)
        imgGestureIcon = findViewById(R.id.imgGestureIcon)
        tvGestureValue = findViewById(R.id.tvGestureValue)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        val videoPath = intent.getStringExtra("VIDEO_PATH")
        if (videoPath != null) {
            initializePlayer(videoPath)
        }

        setupGestures()
        setupPiPMode()
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            window.insetsController?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun initializePlayer(videoPath: String) {
        exoPlayer = ExoPlayer.Builder(this).build()
        playerView.player = exoPlayer

        val mediaItem = MediaItem.fromUri(Uri.parse(videoPath))
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
        exoPlayer?.playWhenReady = true
        exoPlayer?.repeatMode = Player.REPEAT_MODE_OFF
    }

    private fun setupPiPMode() {
        btnPipMode.setOnClickListener {
            enterPiP()
        }
    }

    private fun enterPiP() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val aspectRatio = Rational(16, 9)
            val pipBuilder = PictureInPictureParams.Builder()
            pipBuilder.setAspectRatio(aspectRatio)
            enterPictureInPictureMode(pipBuilder.build())
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Automatically enter PiP when pressing the home button
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && exoPlayer?.isPlaying == true) {
            enterPiP()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            btnPipMode.visibility = View.GONE
            playerView.useController = false
        } else {
            btnPipMode.visibility = View.VISIBLE
            playerView.useController = true
        }
    }

    private fun setupGestures() {
        gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                e1: MotionEvent?, e2: MotionEvent,
                distanceX: Float, distanceY: Float
            ): Boolean {
                if (e1 == null) return false

                val displayWidth = resources.displayMetrics.widthPixels
                val isRightSide = e1.x > (displayWidth / 2)

                if (isRightSide) {
                    adjustVolume(distanceY)
                } else {
                    adjustBrightness(distanceY)
                }
                return true
            }
        })

        playerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false 
        }
    }

    private fun adjustVolume(distanceY: Float) {
        val displayHeight = resources.displayMetrics.heightPixels
        val volumeChange = (distanceY / displayHeight) * maxVolume * 2
        
        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        var newVolume = currentVolume + volumeChange.toInt()
        
        if (newVolume > maxVolume) newVolume = maxVolume
        if (newVolume < 0) newVolume = 0

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
        
        val volumePercentage = ((newVolume.toFloat() / maxVolume) * 100).toInt()
        showGestureFeedback(android.R.drawable.ic_lock_silent_mode_off, "Vol: $volumePercentage%")
    }

    private fun adjustBrightness(distanceY: Float) {
        val displayHeight = resources.displayMetrics.heightPixels
        val brightnessChange = (distanceY / displayHeight) * 2
        
        currentBrightness += brightnessChange
        if (currentBrightness > 1.0f) currentBrightness = 1.0f
        if (currentBrightness < 0.0f) currentBrightness = 0.0f

        val layoutParams = window.attributes
        layoutParams.screenBrightness = currentBrightness
        window.attributes = layoutParams

        val brightnessPercentage = (currentBrightness * 100).toInt()
        showGestureFeedback(android.R.drawable.ic_menu_day, "Sun: $brightnessPercentage%")
    }

    private fun showGestureFeedback(iconResource: Int, text: String) {
        imgGestureIcon.setImageResource(iconResource)
        tvGestureValue.text = text
        layoutGestureFeedback.visibility = View.VISIBLE

        gestureFeedbackHandler.removeCallbacksAndMessages(null)
        gestureFeedbackHandler.postDelayed({
            layoutGestureFeedback.visibility = View.GONE
        }, 1000)
    }

    override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !isInPictureInPictureMode) {
            exoPlayer?.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
        gestureFeedbackHandler.removeCallbacksAndMessages(null)
    }
}
