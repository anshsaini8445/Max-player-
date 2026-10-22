package com.app.mediaplayer

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.media3.common.Player

class MediaSessionManager(
    private val context: Context,
    private val playerProvider: () -> Player?,
    private val onNextTrack: () -> Unit,
    private val onPrevTrack: () -> Unit,
    private val onPlayStateChanged: (Boolean) -> Unit
) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var wasPlayingBeforeTransientLoss = false
    private var isReceiverRegistered = false

    private var clickCount = 0
    private val clickHandler = Handler(Looper.getMainLooper())
    private val multiClickRunnable = Runnable {
        when (clickCount) {
            1 -> togglePlayPause()
            2 -> onNextTrack()
            3 -> onPrevTrack()
        }
        clickCount = 0
    }

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        val player = playerProvider() ?: return@OnAudioFocusChangeListener
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                wasPlayingBeforeTransientLoss = false
                if (player.isPlaying) {
                    player.pause()
                    onPlayStateChanged(false)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                wasPlayingBeforeTransientLoss = player.isPlaying
                if (player.isPlaying) {
                    player.pause()
                    onPlayStateChanged(false)
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
                }
            }
        }
    }

    // Instant 1ms pause when wired earphones are unplugged or Bluetooth headset disconnects
    private val noisyAndBluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val player = playerProvider() ?: return

            if (action == AudioManager.ACTION_AUDIO_BECOMING_NOISY ||
                action == AudioManager.ACTION_HEADSET_PLUG && intent.getIntExtra("state", -1) == 0 ||
                action == BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED &&
                intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, -1) == BluetoothAdapter.STATE_DISCONNECTED ||
                action == BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED &&
                intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, -1) == BluetoothHeadset.STATE_DISCONNECTED
            ) {
                if (player.isPlaying) {
                    player.pause()
                    onPlayStateChanged(false)
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

    fun requestAudioFocusAndRegister(): Boolean {
        registerNoisyReceiver()
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
                clickHandler.removeCallbacks(multiClickRunnable)
                clickHandler.postDelayed(multiClickRunnable, 320L)
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                requestAudioFocusAndRegister()
                player.play()
                onPlayStateChanged(true)
                true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                player.pause()
                onPlayStateChanged(false)
                true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                onNextTrack()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                onPrevTrack()
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
        clickHandler.removeCallbacks(multiClickRunnable)
        try {
            if (isReceiverRegistered) {
                context.unregisterReceiver(noisyAndBluetoothReceiver)
                isReceiverRegistered = false
            }
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
