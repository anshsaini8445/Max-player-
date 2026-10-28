package com.app.mediaplayer

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

object AudioThreeDotMenuHelper {

    interface AudioPlayerCallbacks {
        fun getExoPlayer(): ExoPlayer?
        fun getAudioSessionId(): Int
        fun getCurrentSongPath(): String
        fun getCurrentSongTitle(): String
        fun getCurrentSongArtist(): String
        fun getCurrentPositionMs(): Long
        fun getDurationMs(): Long
        fun isFavoriteSong(): Boolean
        fun onToggleFavorite()
        fun isSuperLoudBoostActive(): Boolean
        fun onToggleSuperLoudBoost()
        fun getPlaybackSpeed(): Float
        fun onChangePlaybackSpeed(newSpeed: Float)
        fun getLoopMode(): Int
        fun onChangeLoopMode(newMode: Int)
        fun onTriggerAbRepeat()
        fun getAbRepeatStatusText(): String
        fun onOpenSleepTimer()
        fun onToggleVinylPhotoMode()
        fun onDeleteCurrentSongPermanently()
    }

    fun makeFrostedWhiteCircle(d: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#18FFFFFF"))
        }
    }

    fun makeFrostedWhitePill(d: Float): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = 18f * d
            setColor(Color.parseColor("#1AFFFFFF"))
        }
    }

    fun formatCleanSongTitle(rawTitle: String): String {
        val withoutExt = rawTitle.substringBeforeLast('.')
        return withoutExt
            .replace("__", " - ")
            .replace("_", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifEmpty { rawTitle }
    }

    fun showAudioThreeDotStudioDialog(
        context: Context,
        callbacks: AudioPlayerCallbacks
    ) {
        val d = context.resources.displayMetrics.density

        val rootCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * d).toInt(), (18 * d).toInt(), (18 * d).toInt(), (16 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F810141E"))
                cornerRadius = 24f * d
                setStroke((1 * d).toInt(), Color.parseColor("#26FFFFFF"))
            }
        }

        val dialog = AlertDialog.Builder(context).setView(rootCard).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((4 * d).toInt(), 0, (4 * d).toInt(), (14 * d).toInt())
        }

        val titleCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvHeader = TextView(context).apply {
            text = "Audio Settings & Tools"
            setTextColor(Color.WHITE)
            textSize = 16.5f
            typeface = Typeface.DEFAULT_BOLD
        }

        val tvSubSong = TextView(context).apply {
            text = formatCleanSongTitle(callbacks.getCurrentSongTitle())
            setTextColor(Color.parseColor("#9FB4CE"))
            textSize = 12f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, (2 * d).toInt(), 0, 0)
        }

        titleCol.addView(tvHeader)
        titleCol.addView(tvSubSong)

        val btnThreeDotDismiss = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams((38 * d).toInt(), (38 * d).toInt())
            setPadding((9 * d).toInt(), (9 * d).toInt(), (9 * d).toInt(), (9 * d).toInt())
            setImageDrawable(PureWhiteAudioVectorDrawable("THREE_DOTS", d))
            background = makeFrostedWhiteCircle(d)
            setOnClickListener { dialog.dismiss() }
        }

        headerRow.addView(titleCol)
        headerRow.addView(btnThreeDotDismiss)
        rootCard.addView(headerRow)

        val scroll = ScrollView(context).apply {
            isFillViewport = true
            isSmoothScrollingEnabled = true
            setOnTouchListener { v, _ ->
                v.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
        }

        val contentCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val grid = GridLayout(context).apply {
            columnCount = 4
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (14 * d).toInt() }
        }

        data class StudioTileSpec(
            val label: String,
            val iconType: String,
            val onClick: () -> Unit
        )

        val tiles = listOf(
            StudioTileSpec("Equalizer", "EQUALIZER") {
                dialog.dismiss()
                EqualizerHelper.showEqualizerDialog(context, callbacks.getAudioSessionId())
            },
            StudioTileSpec("Sound Lab", "PITCH_LAB") {
                dialog.dismiss()
                AudioPitchHelper.showSoundAndPitchLabDialog(context, callbacks.getExoPlayer())
            },
            StudioTileSpec("Sleep Timer", "TIMER") {
                dialog.dismiss()
                callbacks.onOpenSleepTimer()
            },
            StudioTileSpec(
                if (callbacks.isSuperLoudBoostActive()) "200% ON" else "200% Boost",
                "BOOST"
            ) {
                callbacks.onToggleSuperLoudBoost()
                dialog.dismiss()
            },
            StudioTileSpec(
                if (callbacks.isFavoriteSong()) "Favorited" else "Favorite",
                if (callbacks.isFavoriteSong()) "HEART_ON" else "HEART_OFF"
            ) {
                callbacks.onToggleFavorite()
                dialog.dismiss()
            },
            StudioTileSpec("Ringtone", "RINGTONE") {
                dialog.dismiss()
                setAudioAsRingtone(context, callbacks.getCurrentSongPath(), callbacks.getCurrentSongTitle())
            },
            StudioTileSpec(callbacks.getAbRepeatStatusText(), "AB_REPEAT") {
                callbacks.onTriggerAbRepeat()
                dialog.dismiss()
            },
            StudioTileSpec("Disc / Cover", "VINYL_DISC") {
                callbacks.onToggleVinylPhotoMode()
                dialog.dismiss()
            },
            StudioTileSpec("Turbo Share", "SHARE") {
                dialog.dismiss()
                try {
                    val intent = Intent(context, FileTransferActivity::class.java).apply {
                        putStringArrayListExtra("PRESELECTED_PATHS", arrayListOf(callbacks.getCurrentSongPath()))
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                }
            },
            StudioTileSpec("Share Apps", "SHARE") {
                dialog.dismiss()
                try {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "audio/*"
                        putExtra(Intent.EXTRA_STREAM, Uri.parse(callbacks.getCurrentSongPath()))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Song"))
                } catch (_: Exception) {
                }
            },
            StudioTileSpec("Lock Vault", "VAULT") {
                dialog.dismiss()
                FakeVaultHelper.lockMediaIntoVault(
                    context,
                    callbacks.getCurrentSongPath(),
                    callbacks.getCurrentSongTitle()
                )
            },
            StudioTileSpec("Delete", "DELETE") {
                dialog.dismiss()
                callbacks.onDeleteCurrentSongPermanently()
            }
        )

        tiles.forEach { spec ->
            val tileLayout = LinearLayout(context).apply {
                isClickable = true
                isFocusable = true
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding((6 * d).toInt(), (10 * d).toInt(), (6 * d).toInt(), (10 * d).toInt())
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = (78 * d).toInt()
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins((4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt())
                }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#14FFFFFF"))
                    cornerRadius = 14f * d
                }
                setOnClickListener { spec.onClick() }
            }

            val iconView = ImageView(context).apply {
                isClickable = false
                isFocusable = false
                layoutParams = LinearLayout.LayoutParams((26 * d).toInt(), (26 * d).toInt())
                setImageDrawable(PureWhiteAudioVectorDrawable(spec.iconType, d))
            }

            val tvLabel = TextView(context).apply {
                isClickable = false
                isFocusable = false
                text = spec.label
                setTextColor(Color.WHITE)
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, (6 * d).toInt(), 0, 0)
            }

            tileLayout.addView(iconView)
            tileLayout.addView(tvLabel)
            grid.addView(tileLayout)
        }

        contentCol.addView(grid)

        val speedRow = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((10 * d).toInt(), (8 * d).toInt(), (10 * d).toInt(), (10 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#12FFFFFF"))
                cornerRadius = 14f * d
            }
        }

        val speedHeader = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((4 * d).toInt(), 0, (4 * d).toInt(), (4 * d).toInt())
        }

        val tvSpeedTitle = TextView(context).apply {
            text = String.format(Locale.US, "Playback Speed: %.2fx", callbacks.getPlaybackSpeed())
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnResetSpeed = TextView(context).apply {
            text = "Reset 1.0x"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((10 * d).toInt(), (4 * d).toInt(), (10 * d).toInt(), (4 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 10f * d
            }
        }

        val sbSpeed = SeekBar(context).apply {
            max = 375
            progress = ((callbacks.getPlaybackSpeed() - 0.25f) * 100f).roundToInt().coerceIn(0, 375)
            progressDrawable.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            thumb.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            setOnTouchListener { v, _ ->
                v.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val sp = ((0.25f + p / 100f) * 20f).roundToInt() / 20f
                        val clamped = sp.coerceIn(0.25f, 4.0f)
                        tvSpeedTitle.text = String.format(Locale.US, "Playback Speed: %.2fx", clamped)
                        callbacks.onChangePlaybackSpeed(clamped)
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }

        btnResetSpeed.setOnClickListener {
            sbSpeed.progress = 75
            tvSpeedTitle.text = "Playback Speed: 1.00x"
            callbacks.onChangePlaybackSpeed(1.0f)
        }

        speedHeader.addView(tvSpeedTitle)
        speedHeader.addView(btnResetSpeed)
        speedRow.addView(speedHeader)
        speedRow.addView(sbSpeed)
        contentCol.addView(speedRow)

        val loopRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 4f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (12 * d).toInt() }
        }

        val loopLabels = listOf("Shuffle", "Repeat 1", "Loop All", "Play 1")
        val loopButtons = mutableListOf<TextView>()

        fun styleLoopButtons(activeMode: Int) {
            loopButtons.forEachIndexed { idx, btn ->
                val isSel = idx == activeMode
                btn.setTextColor(if (isSel) Color.parseColor("#0A0A10") else Color.WHITE)
                btn.background = GradientDrawable().apply {
                    setColor(if (isSel) Color.WHITE else Color.parseColor("#14FFFFFF"))
                    cornerRadius = 12f * d
                }
            }
        }

        loopLabels.forEachIndexed { idx, text ->
            val btn = TextView(context).apply {
                this.text = text
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, (40 * d).toInt(), 1f).apply {
                    setMargins((3 * d).toInt(), 0, (3 * d).toInt(), 0)
                }
                setOnClickListener {
                    callbacks.onChangeLoopMode(idx)
                    styleLoopButtons(idx)
                }
            }
            loopButtons.add(btn)
            loopRow.addView(btn)
        }

        styleLoopButtons(callbacks.getLoopMode())
        contentCol.addView(loopRow)

        scroll.addView(contentCol)
        rootCard.addView(scroll)
        dialog.show()
    }

    fun showPlayingQueueDialog(
        context: Context,
        songPaths: MutableList<String>,
        songTitles: MutableList<String>,
        songArtists: MutableList<String>,
        currentIndex: Int,
        onSelectTrack: (Int) -> Unit,
        onRemoveTrack: (Int) -> Unit
    ) {
        val d = context.resources.displayMetrics.density

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt(), (14 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F810141E"))
                cornerRadius = 24f * d
                setStroke((1 * d).toInt(), Color.parseColor("#26FFFFFF"))
            }
        }

        val dialog = AlertDialog.Builder(context).setView(root).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (12 * d).toInt())
        }

        val tvHeader = TextView(context).apply {
            text = "Up Next Queue (${(currentIndex + 1).coerceAtMost(songPaths.size)}/${songPaths.size})"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnThreeDotClose = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams((36 * d).toInt(), (36 * d).toInt())
            setPadding((8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
            setImageDrawable(PureWhiteAudioVectorDrawable("THREE_DOTS", d))
            background = makeFrostedWhiteCircle(d)
            setOnClickListener { dialog.dismiss() }
        }

        headerRow.addView(tvHeader)
        headerRow.addView(btnThreeDotClose)
        root.addView(headerRow)

        val rvQueue = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (380 * d).toInt()
            )
            layoutManager = LinearLayoutManager(context)
            setHasFixedSize(true)
        }

        rvQueue.adapter = object : RecyclerView.Adapter<QueueSongVH>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueSongVH {
                val card = CardView(context).apply {
                    radius = 12f * d
                    cardElevation = 0f
                    setCardBackgroundColor(Color.parseColor("#14FFFFFF"))
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins((2 * d).toInt(), (4 * d).toInt(), (2 * d).toInt(), (4 * d).toInt())
                    }
                }

                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((10 * d).toInt(), (8 * d).toInt(), (10 * d).toInt(), (8 * d).toInt())
                }

                val img = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams((44 * d).toInt(), (44 * d).toInt()).apply {
                        marginEnd = (12 * d).toInt()
                    }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }

                val col = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                val tvTitle = TextView(context).apply {
                    setTextColor(Color.WHITE)
                    textSize = 13.5f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }

                val tvSub = TextView(context).apply {
                    setTextColor(Color.parseColor("#9FB4CE"))
                    textSize = 11.5f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, (2 * d).toInt(), 0, 0)
                }

                val btnRemove = TextView(context).apply {
                    text = "Remove"
                    setTextColor(Color.WHITE)
                    textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding((10 * d).toInt(), (5 * d).toInt(), (10 * d).toInt(), (5 * d).toInt())
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#22FFFFFF"))
                        cornerRadius = 10f * d
                    }
                }

                col.addView(tvTitle)
                col.addView(tvSub)
                row.addView(img)
                row.addView(col)
                row.addView(btnRemove)
                card.addView(row)
                return QueueSongVH(card, img, tvTitle, tvSub, btnRemove)
            }

            override fun onBindViewHolder(holder: QueueSongVH, position: Int) {
                val path = songPaths[position]
                val rawTitle = songTitles.getOrElse(position) { File(path).name }
                val cleanTitle = formatCleanSongTitle(rawTitle)
                val artist = songArtists.getOrElse(position) { "Unknown Artist" }
                val isPlayingNow = position == currentIndex

                holder.tvTitle.text = if (isPlayingNow) "Now Playing: $cleanTitle" else cleanTitle
                holder.tvTitle.setTextColor(if (isPlayingNow) Color.parseColor("#00E5FF") else Color.WHITE)
                holder.tvSub.text = artist
                AudioAdapter.loadAlbumArtOrFallbackInto(context, path, rawTitle, holder.img)

                holder.itemView.setOnClickListener {
                    val pos = holder.adapterPosition
                    if (pos in songPaths.indices) {
                        dialog.dismiss()
                        onSelectTrack(pos)
                    }
                }

                holder.btnRemove.setOnClickListener {
                    val pos = holder.adapterPosition
                    if (pos in songPaths.indices && songPaths.size > 1) {
                        onRemoveTrack(pos)
                        notifyItemRemoved(pos)
                        notifyItemRangeChanged(pos, songPaths.size)
                        tvHeader.text = "Up Next Queue (${songPaths.size} Songs)"
                    }
                }
            }

            override fun getItemCount(): Int = songPaths.size
        }

        root.addView(rvQueue)
        rvQueue.scrollToPosition(currentIndex.coerceAtLeast(0))
        dialog.show()
    }

    private class QueueSongVH(
        view: View,
        val img: ImageView,
        val tvTitle: TextView,
        val tvSub: TextView,
        val btnRemove: TextView
    ) : RecyclerView.ViewHolder(view)

    private fun setAudioAsRingtone(context: Context, path: String, title: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Toast.makeText(context, "Allow Modify System Settings to set Ringtone", Toast.LENGTH_LONG).show()
                return
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DATA, path)
                put(MediaStore.MediaColumns.TITLE, title)
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp3")
                put(MediaStore.Audio.Media.IS_RINGTONE, true)
            }
            val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, uri)
                Toast.makeText(context, "Phone Ringtone Set: ${formatCleanSongTitle(title)}", Toast.LENGTH_LONG).show()
            }
        } catch (_: Exception) {
            Toast.makeText(context, "Unable to set ringtone", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Precision Vector Icon Drawable Engine:
     * Renders true Bezier Hearts, crisp Loop Arrows, Shuffle Arrows, and Studio Transport Icons.
     */
    class PureWhiteAudioVectorDrawable(
        private val type: String,
        private val d: Float
    ) : Drawable() {

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.2f * d
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        private val heartActiveFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF2E63")
            style = Paint.Style.FILL
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 0f || h <= 0f) return
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val r = w.coerceAtMost(h) * 0.40f
            val path = Path()

            when (type) {
                "BACK_ARROW" -> {
                    canvas.drawLine(cx + r * 0.65f, cy, cx - r * 0.65f, cy, strokePaint)
                    canvas.drawLine(cx - r * 0.65f, cy, cx - r * 0.08f, cy - r * 0.56f, strokePaint)
                    canvas.drawLine(cx - r * 0.65f, cy, cx - r * 0.08f, cy + r * 0.56f, strokePaint)
                }
                "THREE_DOTS" -> {
                    canvas.drawCircle(cx, cy - r * 0.65f, 2.3f * d, fillPaint)
                    canvas.drawCircle(cx, cy, 2.3f * d, fillPaint)
                    canvas.drawCircle(cx, cy + r * 0.65f, 2.3f * d, fillPaint)
                }
                "PLAY" -> {
                    path.moveTo(cx - r * 0.30f, cy - r * 0.58f)
                    path.lineTo(cx + r * 0.64f, cy)
                    path.lineTo(cx - r * 0.30f, cy + r * 0.58f)
                    path.close()
                    canvas.drawPath(path, fillPaint)
                }
                "PAUSE" -> {
                    val bw = r * 0.32f
                    val bh = r * 1.10f
                    canvas.drawRoundRect(
                        RectF(cx - r * 0.48f, cy - bh / 2f, cx - r * 0.48f + bw, cy + bh / 2f),
                        2.5f * d,
                        2.5f * d,
                        fillPaint
                    )
                    canvas.drawRoundRect(
                        RectF(cx + r * 0.16f, cy - bh / 2f, cx + r * 0.16f + bw, cy + bh / 2f),
                        2.5f * d,
                        2.5f * d,
                        fillPaint
                    )
                }
                "PREV" -> {
                    canvas.drawLine(cx - r * 0.56f, cy - r * 0.52f, cx - r * 0.56f, cy + r * 0.52f, strokePaint)
                    path.moveTo(cx + r * 0.54f, cy - r * 0.52f)
                    path.lineTo(cx - r * 0.36f, cy)
                    path.lineTo(cx + r * 0.54f, cy + r * 0.52f)
                    path.close()
                    canvas.drawPath(path, fillPaint)
                }
                "NEXT" -> {
                    canvas.drawLine(cx + r * 0.56f, cy - r * 0.52f, cx + r * 0.56f, cy + r * 0.52f, strokePaint)
                    path.moveTo(cx - r * 0.54f, cy - r * 0.52f)
                    path.lineTo(cx + r * 0.36f, cy)
                    path.lineTo(cx - r * 0.54f, cy + r * 0.52f)
                    path.close()
                    canvas.drawPath(path, fillPaint)
                }
                "SHUFFLE" -> {
                    canvas.drawLine(cx - r * 0.72f, cy - r * 0.44f, cx + r * 0.68f, cy + r * 0.44f, strokePaint)
                    canvas.drawLine(cx - r * 0.72f, cy + r * 0.44f, cx + r * 0.68f, cy - r * 0.44f, strokePaint)
                    canvas.drawLine(cx + r * 0.34f, cy - r * 0.46f, cx + r * 0.72f, cy - r * 0.44f, strokePaint)
                    canvas.drawLine(cx + r * 0.72f, cy - r * 0.44f, cx + r * 0.52f, cy - r * 0.12f, strokePaint)
                    canvas.drawLine(cx + r * 0.34f, cy + r * 0.46f, cx + r * 0.72f, cy + r * 0.44f, strokePaint)
                    canvas.drawLine(cx + r * 0.72f, cy + r * 0.44f, cx + r * 0.52f, cy + r * 0.12f, strokePaint)
                }
                "REPEAT_ALL", "REPEAT_ONE", "AB_REPEAT" -> {
                    val topY = cy - r * 0.45f
                    val botY = cy + r * 0.45f
                    val leftX = cx - r * 0.72f
                    val rightX = cx + r * 0.72f
                    canvas.drawLine(leftX + r * 0.2f, topY, rightX, topY, strokePaint)
                    canvas.drawLine(rightX, topY, rightX, cy + r * 0.05f, strokePaint)
                    canvas.drawLine(rightX - r * 0.25f, topY - r * 0.22f, rightX, topY, strokePaint)
                    canvas.drawLine(rightX - r * 0.25f, topY + r * 0.22f, rightX, topY, strokePaint)

                    canvas.drawLine(rightX - r * 0.2f, botY, leftX, botY, strokePaint)
                    canvas.drawLine(leftX, botY, leftX, cy - r * 0.05f, strokePaint)
                    canvas.drawLine(leftX + r * 0.25f, botY - r * 0.22f, leftX, botY, strokePaint)
                    canvas.drawLine(leftX + r * 0.25f, botY + r * 0.22f, leftX, botY, strokePaint)

                    if (type == "REPEAT_ONE") {
                        canvas.drawLine(cx, cy - r * 0.22f, cx, cy + r * 0.22f, strokePaint)
                    }
                }
                "EQUALIZER" -> {
                    canvas.drawLine(cx - r * 0.58f, cy - r * 0.70f, cx - r * 0.58f, cy + r * 0.70f, strokePaint)
                    canvas.drawLine(cx, cy - r * 0.70f, cx, cy + r * 0.70f, strokePaint)
                    canvas.drawLine(cx + r * 0.58f, cy - r * 0.70f, cx + r * 0.58f, cy + r * 0.70f, strokePaint)
                    canvas.drawCircle(cx - r * 0.58f, cy - r * 0.20f, 2.7f * d, fillPaint)
                    canvas.drawCircle(cx, cy + r * 0.24f, 2.7f * d, fillPaint)
                    canvas.drawCircle(cx + r * 0.58f, cy - r * 0.24f, 2.7f * d, fillPaint)
                }
                "QUEUE_LIST" -> {
                    canvas.drawLine(cx - r * 0.72f, cy - r * 0.52f, cx + r * 0.72f, cy - r * 0.52f, strokePaint)
                    canvas.drawLine(cx - r * 0.72f, cy, cx + r * 0.72f, cy, strokePaint)
                    canvas.drawLine(cx - r * 0.72f, cy + r * 0.52f, cx + r * 0.32f, cy + r * 0.52f, strokePaint)
                }
                "HEART_ON", "HEART_OFF" -> {
                    val hr = r * 0.85f
                    path.moveTo(cx, cy + hr * 0.72f)
                    path.cubicTo(
                        cx - hr * 1.25f, cy + hr * 0.08f,
                        cx - hr * 0.95f, cy - hr * 0.85f,
                        cx, cy - hr * 0.30f
                    )
                    path.cubicTo(
                        cx + hr * 0.95f, cy - hr * 0.85f,
                        cx + hr * 1.25f, cy + hr * 0.08f,
                        cx, cy + hr * 0.72f
                    )
                    path.close()
                    if (type == "HEART_ON") {
                        canvas.drawPath(path, heartActiveFill)
                    } else {
                        canvas.drawPath(path, strokePaint)
                    }
                }
                "TIMER" -> {
                    canvas.drawCircle(cx, cy, r * 0.78f, strokePaint)
                    canvas.drawLine(cx, cy, cx, cy - r * 0.44f, strokePaint)
                    canvas.drawLine(cx, cy, cx + r * 0.34f, cy, strokePaint)
                }
                "PITCH_LAB", "BOOST" -> {
                    canvas.drawLine(cx - r * 0.6f, cy - r * 0.35f, cx - r * 0.6f, cy + r * 0.35f, strokePaint)
                    canvas.drawLine(cx - r * 0.2f, cy - r * 0.75f, cx - r * 0.2f, cy + r * 0.75f, strokePaint)
                    canvas.drawLine(cx + r * 0.2f, cy - r * 0.55f, cx + r * 0.2f, cy + r * 0.55f, strokePaint)
                    canvas.drawLine(cx + r * 0.6f, cy - r * 0.25f, cx + r * 0.6f, cy + r * 0.25f, strokePaint)
                }
                "RINGTONE" -> {
                    val bell = RectF(cx - r * 0.58f, cy - r * 0.62f, cx + r * 0.58f, cy + r * 0.42f)
                    canvas.drawRoundRect(bell, 6f * d, 6f * d, strokePaint)
                    canvas.drawLine(cx - r * 0.75f, cy + r * 0.42f, cx + r * 0.75f, cy + r * 0.42f, strokePaint)
                    canvas.drawCircle(cx, cy + r * 0.66f, 2.4f * d, fillPaint)
                }
                "VINYL_DISC" -> {
                    canvas.drawCircle(cx, cy, r * 0.80f, strokePaint)
                    canvas.drawCircle(cx, cy, r * 0.25f, fillPaint)
                }
                "SHARE" -> {
                    canvas.drawLine(cx - r * 0.48f, cy, cx + r * 0.48f, cy - r * 0.48f, strokePaint)
                    canvas.drawLine(cx - r * 0.48f, cy, cx + r * 0.48f, cy + r * 0.48f, strokePaint)
                    canvas.drawCircle(cx - r * 0.48f, cy, 2.8f * d, fillPaint)
                    canvas.drawCircle(cx + r * 0.48f, cy - r * 0.48f, 2.8f * d, fillPaint)
                    canvas.drawCircle(cx + r * 0.48f, cy + r * 0.48f, 2.8f * d, fillPaint)
                }
                "VAULT" -> {
                    val body = RectF(cx - r * 0.65f, cy - r * 0.08f, cx + r * 0.65f, cy + r * 0.80f)
                    canvas.drawRoundRect(body, 3.5f * d, 3.5f * d, strokePaint)
                    val shackle = RectF(cx - r * 0.42f, cy - r * 0.76f, cx + r * 0.42f, cy - r * 0.04f)
                    canvas.drawArc(shackle, 180f, 180f, false, strokePaint)
                }
                "DELETE" -> {
                    canvas.drawLine(cx - r * 0.72f, cy - r * 0.50f, cx + r * 0.72f, cy - r * 0.50f, strokePaint)
                    val bin = RectF(cx - r * 0.52f, cy - r * 0.50f, cx + r * 0.52f, cy + r * 0.76f)
                    canvas.drawRoundRect(bin, 3f * d, 3f * d, strokePaint)
                }
                else -> {
                    canvas.drawCircle(cx, cy, r * 0.70f, strokePaint)
                }
            }
        }

        override fun setAlpha(alpha: Int) {
            strokePaint.alpha = alpha
            fillPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            strokePaint.colorFilter = colorFilter
            fillPaint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
