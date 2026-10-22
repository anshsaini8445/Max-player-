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
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.IBinder

class BackgroundAudioService : Service() {

    companion object {
        const val CHANNEL_ID = "og_8k_system_media_channel"
        const val NOTIF_ID = 8801

        const val ACTION_START_OR_UPDATE = "com.app.mediaplayer.ACTION_BG_START_OR_UPDATE"
        const val ACTION_STOP_SERVICE = "com.app.mediaplayer.ACTION_BG_STOP_SERVICE"
        const val ACTION_NOTIF_PREV = "com.app.mediaplayer.ACTION_NOTIF_PREV"
        const val ACTION_NOTIF_PLAY_PAUSE = "com.app.mediaplayer.ACTION_NOTIF_PLAY_PAUSE"
        const val ACTION_NOTIF_NEXT = "com.app.mediaplayer.ACTION_NOTIF_NEXT"

        private var cachedPath: String = ""
        private var cachedCoverBitmap: Bitmap? = null

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
                val title = intent.getStringExtra("TITLE")?.replace("_", " ") ?: "OG 8K Player"
                val artist = intent.getStringExtra("ARTIST") ?: "OG 8K Studio"
                val isPlaying = intent.getBooleanExtra("IS_PLAYING", true)
                val isVideoBg = intent.getBooleanExtra("IS_VIDEO_BG", false)
                val mediaPath = intent.getStringExtra("VIDEO_PATH") ?: ""

                val notif = buildSystemMediaStyleNotification(
                    title = title,
                    artist = artist,
                    isPlaying = isPlaying,
                    isVideoBg = isVideoBg,
                    mediaPath = mediaPath
                )
                startForeground(NOTIF_ID, notif)
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    private fun buildSystemMediaStyleNotification(
        title: String,
        artist: String,
        isPlaying: Boolean,
        isVideoBg: Boolean,
        mediaPath: String
    ): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OG 8K Media Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "System Media Controls with Seekbar and Bluetooth Sync"
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

        val openPlayerIntent = if (isVideoBg && mediaPath.isNotEmpty()) {
            Intent(this, PlayerActivity::class.java).apply {
                putExtra("VIDEO_PATH", mediaPath)
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

        val largeCoverBmp = resolveCoverArtBitmap(mediaPath, title)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val mediaStyle = Notification.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)

        MediaSessionManager.activeSessionToken?.let { token ->
            mediaStyle.setMediaSession(token)
        }

        builder.setSmallIcon(android.R.drawable.ic_media_play)
            .setLargeIcon(largeCoverBmp)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText("OG 8K Player")
            .setContentIntent(contentPi)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(mediaStyle)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_previous,
                    "Previous",
                    prevPi
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (isPlaying) "Pause" else "Play",
                    playPausePi
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_next,
                    "Next",
                    nextPi
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Close",
                    stopPi
                ).build()
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setColorized(true)
            builder.setColor(Color.parseColor("#0A0A10"))
        }

        return builder.build()
    }

    private fun resolveCoverArtBitmap(mediaPath: String, seedTitle: String): Bitmap {
        if (mediaPath.isNotEmpty() && mediaPath == cachedPath && cachedCoverBitmap != null) {
            return cachedCoverBitmap!!
        }
        if (mediaPath.isNotEmpty()) {
            val retriever = MediaMetadataRetriever()
            try {
                if (mediaPath.startsWith("content://")) {
                    retriever.setDataSource(this, Uri.parse(mediaPath))
                } else {
                    retriever.setDataSource(mediaPath)
                }
                val embedded = retriever.embeddedPicture
                if (embedded != null) {
                    val bmp = android.graphics.BitmapFactory.decodeByteArray(embedded, 0, embedded.size)
                    if (bmp != null) {
                        cachedPath = mediaPath
                        cachedCoverBitmap = bmp
                        return bmp
                    }
                }
                val frame = retriever.getFrameAtTime(1000000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    cachedPath = mediaPath
                    cachedCoverBitmap = frame
                    return frame
                }
            } catch (_: Exception) {
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }
        }
        return generateStylizedBlurCoverBitmap(seedTitle)
    }

    private fun generateStylizedBlurCoverBitmap(seedTitle: String): Bitmap {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val hash = seedTitle.hashCode()
        val c1 = Color.HSVToColor(floatArrayOf(kotlin.math.abs(hash % 360).toFloat(), 0.70f, 0.55f))
        val c2 = Color.parseColor("#0A0A10")

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), c1, c2, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00FFFF")
            style = Paint.Style.STROKE
            strokeWidth = 7f
        }
        canvas.drawCircle(size / 2f, size / 2f, size * 0.30f, ringPaint)
        return bmp
    }
}
