package com.app.mediaplayer

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.media3.common.Player

class MediaSessionManager(
    private val context: Context,
    private val playerProvider: () -> Player?,
    private val onNextTrack: () -> Unit,
    private val onPrevTrack: () -> Unit,
    private val onPlayStateChanged: (Boolean) -> Unit
) {

    companion object {
        @Volatile
        var activeSessionToken: MediaSession.Token? = null
            private set

        @Volatile
        var activeControllerCallback: MediaSession.Callback? = null
            private set
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var mediaSession: MediaSession? = null
    private var wasPlayingBeforeTransientLoss = false
    private var isReceiverRegistered = false

    private var clickCount = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private val multiClickRunnable = Runnable {
        when (clickCount) {
            1 -> togglePlayPause()
            2 -> onNextTrack()
            else -> if (clickCount >= 3) onPrevTrack()
        }
        clickCount = 0
    }

    private val sessionCallback = object : MediaSession.Callback() {
        override fun onPlay() {
            val player = playerProvider() ?: return
            requestAudioFocusAndRegister()
            player.play()
            onPlayStateChanged(true)
            updatePlaybackState()
        }

        override fun onPause() {
            val player = playerProvider() ?: return
            player.pause()
            onPlayStateChanged(false)
            updatePlaybackState()
        }

        override fun onSkipToNext() {
            onNextTrack()
            updatePlaybackState()
        }

        override fun onSkipToPrevious() {
            onPrevTrack()
            updatePlaybackState()
        }

        override fun onSeekTo(pos: Long) {
            val player = playerProvider() ?: return
            player.seekTo(pos.coerceAtLeast(0L))
            updatePlaybackState()
        }

        override fun onStop() {
            val player = playerProvider() ?: return
            player.pause()
            onPlayStateChanged(false)
            updatePlaybackState()
        }

        override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
            val keyEvent: KeyEvent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            }
            if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                if (handleMediaKeyEvent(keyEvent.keyCode)) {
                    return true
                }
            }
            return super.onMediaButtonEvent(mediaButtonIntent)
        }
    }

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        val player = playerProvider() ?: return@OnAudioFocusChangeListener
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                wasPlayingBeforeTransientLoss = false
                if (player.isPlaying) {
                    player.pause()
                    onPlayStateChanged(false)
                    updatePlaybackState()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                wasPlayingBeforeTransientLoss = player.isPlaying
                if (player.isPlaying) {
                    player.pause()
                    onPlayStateChanged(false)
                    updatePlaybackState()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                player.volume = 0.25f
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                player.volume = 1.0f
                if (wasPlayingBeforeTransientLoss) {
                    wasPlayingBeforeTransientLoss = false
                    player.play()
                    onPlayStateChanged(true)
                    updatePlaybackState()
                }
            }
        }
    }

    private val noisyAndBluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val player = playerProvider() ?: return

            if (action == AudioManager.ACTION_AUDIO_BECOMING_NOISY ||
                (action == AudioManager.ACTION_HEADSET_PLUG && intent.getIntExtra("state", -1) == 0) ||
                (action == BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED &&
                    intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, -1) == BluetoothAdapter.STATE_DISCONNECTED) ||
                (action == BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED &&
                    intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, -1) == BluetoothHeadset.STATE_DISCONNECTED)
            ) {
                if (player.isPlaying) {
                    player.pause()
                    onPlayStateChanged(false)
                    updatePlaybackState()
                }
            } else if (action == Intent.ACTION_MEDIA_BUTTON) {
                val keyEvent: KeyEvent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                }
                if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                    handleMediaKeyEvent(keyEvent.keyCode)
                }
            }
        }
    }

    init {
        initNativeMediaSession()
    }

    private fun initNativeMediaSession() {
        try {
            mediaSession = MediaSession(context.applicationContext, "OG8KPlayerMediaSession").apply {
                setCallback(sessionCallback, mainHandler)
                @Suppress("DEPRECATION")
                setFlags(
                    MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
                )
                isActive = true
            }
            activeSessionToken = mediaSession?.sessionToken
            activeControllerCallback = sessionCallback
            updatePlaybackState()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateMetadataAndState(
        title: String,
        artist: String,
        durationMs: Long,
        artBitmap: Bitmap? = null
    ) {
        try {
            val session = mediaSession ?: return
            val metaBuilder = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "OG 8K Player")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs.coerceAtLeast(1000L))

            if (artBitmap != null && !artBitmap.isRecycled) {
                metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artBitmap)
                metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, artBitmap)
            }
            session.setMetadata(metaBuilder.build())
            updatePlaybackState()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updatePlaybackState() {
        try {
            val session = mediaSession ?: return
            val player = playerProvider()
            val isPlaying = player?.isPlaying == true
            val position = (player?.currentPosition ?: 0L).coerceAtLeast(0L)
            val speed = if (isPlaying) (player?.playbackParameters?.speed ?: 1.0f) else 0f

            val actions = PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SEEK_TO or
                PlaybackState.ACTION_STOP

            val stateCode = if (isPlaying) {
                PlaybackState.STATE_PLAYING
            } else {
                PlaybackState.STATE_PAUSED
            }

            val pbState = PlaybackState.Builder()
                .setActions(actions)
                .setState(stateCode, position, speed, SystemClock.elapsedRealtime())
                .build()

            session.setPlaybackState(pbState)
            session.isActive = true
            activeSessionToken = session.sessionToken
            activeControllerCallback = sessionCallback
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun requestAudioFocusAndRegister(): Boolean {
        registerNoisyReceiver()
        mediaSession?.isActive = true
        updatePlaybackState()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
            focusRequest = req
            audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    fun handleMediaKeyEvent(keyCode: Int): Boolean {
        val player = playerProvider() ?: return false
        return when (keyCode) {
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                clickCount++
                mainHandler.removeCallbacks(multiClickRunnable)
                mainHandler.postDelayed(multiClickRunnable, 300L)
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                requestAudioFocusAndRegister()
                player.play()
                onPlayStateChanged(true)
                updatePlaybackState()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                player.pause()
                onPlayStateChanged(false)
                updatePlaybackState()
                true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                onNextTrack()
                updatePlaybackState()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                onPrevTrack()
                updatePlaybackState()
                true
            }
            else -> false
        }
    }

    private fun togglePlayPause() {
        val player = playerProvider() ?: return
        if (player.isPlaying) {
            player.pause()
            onPlayStateChanged(false)
        } else {
            requestAudioFocusAndRegister()
            player.play()
            onPlayStateChanged(true)
        }
        updatePlaybackState()
    }

    private fun registerNoisyReceiver() {
        if (isReceiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                addAction(AudioManager.ACTION_HEADSET_PLUG)
                addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                addAction(Intent.ACTION_MEDIA_BUTTON)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(noisyAndBluetoothReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(noisyAndBluetoothReceiver, filter)
            }
            isReceiverRegistered = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        mainHandler.removeCallbacks(multiClickRunnable)
        try {
            if (isReceiverRegistered) {
                context.unregisterReceiver(noisyAndBluetoothReceiver)
                isReceiverRegistered = false
            }
            mediaSession?.isActive = false
            mediaSession?.release()
            mediaSession = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(focusChangeListener)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
