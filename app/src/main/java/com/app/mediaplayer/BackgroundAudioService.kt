package com.app.mediaplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.MediaMetadata
import android.media.MediaMetadataRetriever
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.SystemClock

class BackgroundAudioService : Service() {

    companion object {
        const val CHANNEL_ID = "og_8k_system_media_channel"
        const val NOTIF_ID = 8801

        const val ACTION_START_OR_UPDATE = "com.app.mediaplayer.ACTION_BG_START_OR_UPDATE"
        const val ACTION_STOP_SERVICE = "com.app.mediaplayer.ACTION_BG_STOP_SERVICE"
        const val ACTION_NOTIF_PREV = "com.app.mediaplayer.ACTION_NOTIF_PREV"
        const val ACTION_NOTIF_PLAY_PAUSE = "com.app.mediaplayer.ACTION_NOTIF_PLAY_PAUSE"
        const val ACTION_NOTIF_NEXT = "com.app.mediaplayer.ACTION_NOTIF_NEXT"
        const val ACTION_NOTIF_SEEK = "com.app.mediaplayer.ACTION_NOTIF_SEEK"

        private var cachedPath: String = ""
        private var cachedCoverBitmap: Bitmap? = null
        private var fallbackSession: MediaSession? = null

        fun updateForegroundNotification(
            context: Context,
            title: String,
            artist: String,
            isPlaying: Boolean,
            isVideoBackground: Boolean = false,
            videoPath: String = "",
            positionMs: Long = 0L,
            durationMs: Long = 0L
        ) {
            try {
                val intent = Intent(context, BackgroundAudioService::class.java).apply {
                    action = ACTION_START_OR_UPDATE
                    putExtra("TITLE", title)
                    putExtra("ARTIST", artist)
                    putExtra("IS_PLAYING", isPlaying)
                    putExtra("IS_VIDEO_BG", isVideoBackground)
                    putExtra("VIDEO_PATH", videoPath)
                    putExtra("POSITION_MS", positionMs)
                    putExtra("DURATION_MS", durationMs)
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
                sendBroadcast(Intent(ACTION_NOTIF_PLAY_PAUSE).setPackage(packageName))
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_OR_UPDATE -> {
                val rawTitle = intent.getStringExtra("TITLE") ?: "OG 8K Player"
                val cleanTitle = rawTitle.substringBeforeLast('.').replace("_", " ")
                val artist = intent.getStringExtra("ARTIST") ?: "OG 8K Studio"
                val isPlaying = intent.getBooleanExtra("IS_PLAYING", true)
                val isVideoBg = intent.getBooleanExtra("IS_VIDEO_BG", false)
                val mediaPath = intent.getStringExtra("VIDEO_PATH") ?: ""
                val positionMs = intent.getLongExtra("POSITION_MS", 0L).coerceAtLeast(0L)
                val durationMs = intent.getLongExtra("DURATION_MS", 0L).coerceAtLeast(0L)

                val notif = buildSystemMediaStyleNotification(
                    title = cleanTitle,
                    artist = artist,
                    isPlaying = isPlaying,
                    isVideoBg = isVideoBg,
                    mediaPath = mediaPath,
                    positionMs = positionMs,
                    durationMs = durationMs
                )
                startForeground(NOTIF_ID, notif)
            }
        }
        return START_STICKY
    }

    private fun ensureActiveMediaSessionToken(
        title: String,
        artist: String,
        artBitmap: Bitmap,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long
    ): MediaSession.Token {
        val activeToken = MediaSessionManager.activeSessionToken
        if (activeToken != null) {
            return activeToken
        }

        if (fallbackSession == null) {
            fallbackSession = MediaSession(applicationContext, "OG8KFallbackMediaSession").apply {
                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() {
                        sendBroadcast(Intent(ACTION_NOTIF_PLAY_PAUSE).setPackage(packageName))
                    }

                    override fun onPause() {
                        sendBroadcast(Intent(ACTION_NOTIF_PLAY_PAUSE).setPackage(packageName))
                    }

                    override fun onSkipToNext() {
                        sendBroadcast(Intent(ACTION_NOTIF_NEXT).setPackage(packageName))
                    }

                    override fun onSkipToPrevious() {
                        sendBroadcast(Intent(ACTION_NOTIF_PREV).setPackage(packageName))
                    }

                    override fun onSeekTo(pos: Long) {
                        sendBroadcast(
                            Intent(ACTION_NOTIF_SEEK)
                                .setPackage(packageName)
                                .putExtra("SEEK_POS_MS", pos.coerceAtLeast(0L))
                        )
                    }
                })
                isActive = true
            }
        }

        val session = fallbackSession!!
        val safeDuration = if (durationMs > 1000L) durationMs else 180000L
        val meta = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, "OG 8K Player")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, safeDuration)
            .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artBitmap)
            .putBitmap(MediaMetadata.METADATA_KEY_ART, artBitmap)
            .build()
        session.setMetadata(meta)

        val actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_SEEK_TO or
            PlaybackState.ACTION_STOP

        val stateCode = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val pbState = PlaybackState.Builder()
            .setActions(actions)
            .setState(
                stateCode,
                positionMs,
                if (isPlaying) 1.0f else 0f,
                SystemClock.elapsedRealtime()
            )
            .build()
        session.setPlaybackState(pbState)
        session.isActive = true
        return session.sessionToken
    }

    private fun buildSystemMediaStyleNotification(
        title: String,
        artist: String,
        isPlaying: Boolean,
        isVideoBg: Boolean,
        mediaPath: String,
        positionMs: Long,
        durationMs: Long
    ): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OG 8K Media Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Lock Screen & System Media Player with Seekbar and Bluetooth Sync"
                setShowBadge(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
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
                putExtra("AUDIO_PATH", mediaPath)
                putExtra("AUDIO_TITLE", title)
                putExtra("AUDIO_ARTIST", artist)
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
        val sessionToken = ensureActiveMediaSessionToken(
            title = title,
            artist = artist,
            artBitmap = largeCoverBmp,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val mediaStyle = Notification.MediaStyle()
            .setMediaSession(sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        builder.setSmallIcon(
            if (isPlaying) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
        )
            .setLargeIcon(largeCoverBmp)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText("OG 8K Player")
            .setContentIntent(contentPi)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
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
        if (mediaPath.isNotEmpty() && mediaPath == cachedPath && cachedCoverBitmap != null && !cachedCoverBitmap!!.isRecycled) {
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
                    val bmp = BitmapFactory.decodeByteArray(embedded, 0, embedded.size)
                    if (bmp != null) {
                        cachedPath = mediaPath
                        cachedCoverBitmap = bmp
                        return bmp
                    }
                }
                val frame = retriever.getFrameAtTime(1500000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
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
        val size = 320
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val hash = seedTitle.hashCode()
        val c1 = Color.HSVToColor(floatArrayOf(kotlin.math.abs(hash % 360).toFloat(), 0.72f, 0.58f))
        val c2 = Color.parseColor("#0A0A10")

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), c1, c2, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00FFFF")
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawCircle(size / 2f, size / 2f, size * 0.28f, ringPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 46f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("♪", size / 2f, (size / 2f) + 16f, textPaint)
        return bmp
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            fallbackSession?.isActive = false
            fallbackSession?.release()
            fallbackSession = null
        } catch (_: Exception) {
        }
    }
}
