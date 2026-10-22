package com.app.mediaplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class BackgroundAudioService : Service() {

    companion object {
        const val CHANNEL_ID = "og_8k_background_media_channel"
        const val NOTIF_ID = 8801

        const val ACTION_START_OR_UPDATE = "com.app.mediaplayer.ACTION_BG_START_OR_UPDATE"
        const val ACTION_STOP_SERVICE = "com.app.mediaplayer.ACTION_BG_STOP_SERVICE"
        const val ACTION_NOTIF_PREV = "com.app.mediaplayer.ACTION_NOTIF_PREV"
        const val ACTION_NOTIF_PLAY_PAUSE = "com.app.mediaplayer.ACTION_NOTIF_PLAY_PAUSE"
        const val ACTION_NOTIF_NEXT = "com.app.mediaplayer.ACTION_NOTIF_NEXT"

        fun updateForegroundNotification(
            context: Context,
            title: String,
            artist: String,
            isPlaying: Boolean,
            isVideoBackground: Boolean = false,
            videoPath: String = ""
        ) {
            try {
                val intent = Intent(context, BackgroundAudioService::class.java).apply {
                    action = ACTION_START_OR_UPDATE
                    putExtra("TITLE", title)
                    putExtra("ARTIST", artist)
                    putExtra("IS_PLAYING", isPlaying)
                    putExtra("IS_VIDEO_BG", isVideoBackground)
                    putExtra("VIDEO_PATH", videoPath)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_OR_UPDATE -> {
                val title = intent.getStringExtra("TITLE") ?: "OG 8K Player"
                val artist = intent.getStringExtra("ARTIST") ?: "Playing in Background"
                val isPlaying = intent.getBooleanExtra("IS_PLAYING", true)
                val isVideoBg = intent.getBooleanExtra("IS_VIDEO_BG", false)
                val videoPath = intent.getStringExtra("VIDEO_PATH") ?: ""

                val notif = buildModernMediaNotification(title, artist, isPlaying, isVideoBg, videoPath)
                startForeground(NOTIF_ID, notif)
            }
        }
        // START_STICKY guarantees playback continues even if user swipes the app from Recent Apps
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep playing in background when user removes app from Recent Tasks
        super.onTaskRemoved(rootIntent)
    }

    private fun buildModernMediaNotification(
        title: String,
        artist: String,
        isPlaying: Boolean,
        isVideoBg: Boolean,
        videoPath: String
    ): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OG Player Playback Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Lock-screen & background media player controls"
                setShowBadge(false)
                setSound(null, null)
            }
            nm.createNotificationChannel(channel)
        }

        val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        // Single tap on Notification opens full Music Player or Video Player screen immediately
        val openPlayerIntent = if (isVideoBg && videoPath.isNotEmpty()) {
            Intent(this, PlayerActivity::class.java).apply {
                putExtra("VIDEO_PATH", videoPath)
                putExtra("VIDEO_TITLE", title)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        } else {
            Intent(this, AudioPlayerActivity::class.java).apply {
                putExtra("RESUME_EXISTING", true)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }

        val contentPi = PendingIntent.getActivity(this, 901, openPlayerIntent, piFlags)
        val prevPi = PendingIntent.getBroadcast(
            this,
            902,
            Intent(ACTION_NOTIF_PREV).setPackage(packageName),
            piFlags
        )
        val playPausePi = PendingIntent.getBroadcast(
            this,
            903,
            Intent(ACTION_NOTIF_PLAY_PAUSE).setPackage(packageName),
            piFlags
        )
        val nextPi = PendingIntent.getBroadcast(
            this,
            904,
            Intent(ACTION_NOTIF_NEXT).setPackage(packageName),
            piFlags
        )
        val stopPi = PendingIntent.getService(
            this,
            905,
            Intent(this, BackgroundAudioService::class.java).setAction(ACTION_STOP_SERVICE),
            piFlags
        )

        val largeCoverBmp = generateStylizedBlurCoverBitmap(title)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setLargeIcon(largeCoverBmp)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText("OG Player")
            .setContentIntent(contentPi)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setColorized(true)
            .setColor(Color.parseColor("#0D1B2A"))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPi)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                playPausePi
            )
            .addAction(android.R.drawable.ic_media_next, "Next", nextPi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", stopPi)
            .build()
    }

    private fun generateStylizedBlurCoverBitmap(seedTitle: String): Bitmap {
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val hash = seedTitle.hashCode()
        val c1 = Color.HSVToColor(floatArrayOf(kotlin.math.abs(hash % 360).toFloat(), 0.65f, 0.45f))
        val c2 = Color.parseColor("#0D1B2A")

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), c1, c2, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#5CE1E6")
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawCircle(size / 2f, size / 2f, size * 0.32f, ringPaint)
        return bmp
    }
}
