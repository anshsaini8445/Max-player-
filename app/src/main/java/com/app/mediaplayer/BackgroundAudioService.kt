package com.app.mediaplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class BackgroundAudioService : Service() {

    companion object {
        private const val CHANNEL_ID = "hybrid_player_bg_media_channel"
        private const val NOTIFICATION_ID = 4401

        const val ACTION_NOTIF_PREV = "com.app.mediaplayer.ACTION_NOTIF_PREV"
        const val ACTION_NOTIF_PLAY_PAUSE = "com.app.mediaplayer.ACTION_NOTIF_PLAY_PAUSE"
        const val ACTION_NOTIF_NEXT = "com.app.mediaplayer.ACTION_NOTIF_NEXT"
        const val ACTION_NOTIF_SEEK = "com.app.mediaplayer.ACTION_NOTIF_SEEK"
        const val ACTION_STOP_SERVICE = "com.app.mediaplayer.ACTION_STOP_SERVICE"

        fun updateForegroundNotification(
            context: Context,
            title: String,
            artist: String,
            isPlaying: Boolean,
            isVideoBackground: Boolean,
            videoPath: String = "",
            positionMs: Long = 0L,
            durationMs: Long = 100000L
        ) {
            try {
                val intent = Intent(context, BackgroundAudioService::class.java).apply {
                    putExtra("TITLE", title)
                    putExtra("ARTIST", artist)
                    putExtra("IS_PLAYING", isPlaying)
                    putExtra("IS_VIDEO_BG", isVideoBackground)
                    putExtra("MEDIA_PATH", videoPath)
                    putExtra("POS_MS", positionMs)
                    putExtra("DUR_MS", durationMs)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            VideoFragment.updateBackgroundBarState(false, "", "", false, 0L, 1000L)
            try {
                stopForeground(true)
            } catch (_: Exception) {
            }
            stopSelf()
            return START_NOT_STICKY
        }

        val title = intent?.getStringExtra("TITLE") ?: "Hybrid Player"
        val artist = intent?.getStringExtra("ARTIST") ?: "Background Audio"
        val isPlaying = intent?.getBooleanExtra("IS_PLAYING", true) ?: true
        val isVideoBg = intent?.getBooleanExtra("IS_VIDEO_BG", false) ?: false
        val mediaPath = intent?.getStringExtra("MEDIA_PATH") ?: ""
        val posMs = intent?.getLongExtra("POS_MS", 0L) ?: 0L
        val durMs = (intent?.getLongExtra("DUR_MS", 100000L) ?: 100000L).coerceAtLeast(1000L)

        val notification = buildMediaNotification(
            title = title,
            artist = artist,
            isPlaying = isPlaying,
            isVideoBg = isVideoBg,
            mediaPath = mediaPath,
            posMs = posMs,
            durMs = durMs
        )

        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
        }

        return START_STICKY
    }

    private fun buildMediaNotification(
        title: String,
        artist: String,
        isPlaying: Boolean,
        isVideoBg: Boolean,
        mediaPath: String,
        posMs: Long,
        durMs: Long
    ): Notification {
        val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val openPlayerIntent = if (isVideoBg) {
            Intent(this, PlayerActivity::class.java).apply {
                putExtra("VIDEO_PATH", mediaPath)
                putExtra("VIDEO_TITLE", title)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
        } else {
            Intent(this, AudioPlayerActivity::class.java).apply {
                putExtra("AUDIO_PATH", mediaPath)
                putExtra("AUDIO_TITLE", title)
                putExtra("AUDIO_ARTIST", artist)
                putExtra("RESUME_EXISTING", true)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }

        val contentPendingIntent = PendingIntent.getActivity(
            this,
            101,
            openPlayerIntent,
            piFlags
        )

        val prevPendingIntent = PendingIntent.getBroadcast(
            this,
            102,
            Intent(ACTION_NOTIF_PREV).setPackage(packageName),
            piFlags
        )

        val playPausePendingIntent = PendingIntent.getBroadcast(
            this,
            103,
            Intent(ACTION_NOTIF_PLAY_PAUSE).setPackage(packageName),
            piFlags
        )

        val nextPendingIntent = PendingIntent.getBroadcast(
            this,
            104,
            Intent(ACTION_NOTIF_NEXT).setPackage(packageName),
            piFlags
        )

        val stopServicePendingIntent = PendingIntent.getService(
            this,
            105,
            Intent(this, BackgroundAudioService::class.java).setAction(ACTION_STOP_SERVICE),
            piFlags
        )

        val progressPermille = ((posMs.toDouble() / durMs.toDouble()) * 1000.0).toInt().coerceIn(0, 1000)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText(if (isVideoBg) "Video Background Play" else "Hi-Res Music")
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(stopServicePendingIntent)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(1000, progressPermille, false)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                playPausePendingIntent
            )
            .addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopServicePendingIntent)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Hybrid Player Background Audio",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Controls for background video & music playback"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
