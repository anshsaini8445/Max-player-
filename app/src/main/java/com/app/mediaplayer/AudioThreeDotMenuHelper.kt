package com.app.mediaplayer

import android.app.AlertDialog
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
import java.util.ArrayList
import java.util.Locale
import java.util.concurrent.TimeUnit
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
            setColor(Color.parseColor("#28FFFFFF"))
            setStroke((1.4f * d).toInt(), Color.parseColor("#66FFFFFF"))
        }
    }

    fun makeFrostedWhitePill(d: Float): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = 18f * d
            setColor(Color.parseColor("#28FFFFFF"))
            setStroke((1.4f * d).toInt(), Color.parseColor("#66FFFFFF"))
        }
    }

    /**
     * Opens the 100% Pure-White (#FFFFFF) 3-Dot Studio Menu for AudioPlayerActivity:
     * - Touch-isolated ScrollView so all rows scroll smoothly.
     * - All 12 Studio Tiles, Speed Slider, and Loop Mode Pills are strictly Pure White regardless of theme.
     * - Uses a 3-Dot ("...") button in the header instead of any cross ("X") icon.
     */
    fun showAudioThreeDotStudioDialog(
        context: Context,
        callbacks: AudioPlayerCallbacks
    ) {
        val d = context.resources.displayMetrics.density

        val rootCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * d).toInt(), (18 * d).toInt(), (18 * d).toInt(), (16 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F50A0A10"))
                cornerRadius = 26f * d
                setStroke((1.6f * d).toInt(), Color.parseColor("#55FFFFFF"))
            }
        }

        val dialog = AlertDialog.Builder(context).setView(rootCard).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((4 * d).toInt(), 0, (4 * d).toInt(), (12 * d).toInt())
        }

        val titleCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvHeader = TextView(context).apply {
            text = "Audio Studio & 3-Dot Controls"
            setTextColor(Color.WHITE)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
        }

        val tvSubSong = TextView(context).apply {
            text = callbacks.getCurrentSongTitle().substringBeforeLast('.')
            setTextColor(Color.parseColor("#D8E2F0"))
            textSize = 12f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, (2 * d).toInt(), 0, 0)
        }

        titleCol.addView(tvHeader)
        titleCol.addView(tvSubSong)

        val btnThreeDotDismiss = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams((40 * d).toInt(), (40 * d).toInt())
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

        // 12 Larger Pure-White Modern Studio Tiles (3 Rows x 4 Columns)
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
            StudioTileSpec("Disc / Box", "VINYL_DISC") {
                callbacks.onToggleVinylPhotoMode()
                dialog.dismiss()
            },
            StudioTileSpec("Hybrid Share", "SHARE") {
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
            StudioTileSpec("Delete Song", "DELETE") {
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
                    height = (82 * d).toInt()
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins((4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt())
                }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1AFFFFFF"))
                    cornerRadius = 16f * d
                    setStroke((1.1f * d).toInt(), Color.parseColor("#40FFFFFF"))
                }
                setOnClickListener { spec.onClick() }
            }

            val iconView = ImageView(context).apply {
                isClickable = false
                isFocusable = false
                layoutParams = LinearLayout.LayoutParams((32 * d).toInt(), (32 * d).toInt())
                setImageDrawable(PureWhiteAudioVectorDrawable(spec.iconType, d))
            }

            val tvLabel = TextView(context).apply {
                isClickable = false
                isFocusable = false
                text = spec.label
                setTextColor(Color.WHITE)
                textSize = 11.5f
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

        // Pure-White Playback Speed Control Row
        val speedRow = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt(), (10 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#14FFFFFF"))
                cornerRadius = 16f * d
                setStroke((1 * d).toInt(), Color.parseColor("#33FFFFFF"))
            }
        }

        val speedHeader = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((6 * d).toInt(), 0, (6 * d).toInt(), (4 * d).toInt())
        }

        val tvSpeedTitle = TextView(context).apply {
            text = String.format(Locale.US, "Playback Speed: %.2fx", callbacks.getPlaybackSpeed())
            setTextColor(Color.WHITE)
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnResetSpeed = TextView(context).apply {
            text = "Reset 1.0x"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((12 * d).toInt(), (5 * d).toInt(), (12 * d).toInt(), (5 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 12f * d
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

        // Pure-White Loop / Shuffle Mode Selector Row
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
                    setColor(if (isSel) Color.WHITE else Color.parseColor("#18FFFFFF"))
                    cornerRadius = 14f * d
                    setStroke((1 * d).toInt(), Color.WHITE)
                }
            }
        }

        loopLabels.forEachIndexed { idx, text ->
            val btn = TextView(context).apply {
                this.text = text
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, (42 * d).toInt(), 1f).apply {
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

    /**
     * Opens the Pure-White Playing Queue Dialog with song cover photos and instant track switching.
     */
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
                setColor(Color.parseColor("#F50A0A10"))
                cornerRadius = 24f * d
                setStroke((1.5f * d).toInt(), Color.parseColor("#55FFFFFF"))
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
            text = "Playing Queue (${(currentIndex + 1).coerceAtMost(songPaths.size)}/${songPaths.size})"
            setTextColor(Color.WHITE)
            textSize = 16.5f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnThreeDotClose = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams((38 * d).toInt(), (38 * d).toInt())
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
                    radius = 14f * d
                    cardElevation = 2f * d
                    setCardBackgroundColor(Color.parseColor("#162234"))
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins((3 * d).toInt(), (4 * d).toInt(), (3 * d).toInt(), (4 * d).toInt())
                    }
                }

                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((10 * d).toInt(), (8 * d).toInt(), (10 * d).toInt(), (8 * d).toInt())
                }

                val img = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams((46 * d).toInt(), (46 * d).toInt()).apply {
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
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }

                val tvSub = TextView(context).apply {
                    setTextColor(Color.parseColor("#D8E2F0"))
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
                    setPadding((10 * d).toInt(), (6 * d).toInt(), (10 * d).toInt(), (6 * d).toInt())
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#24FFFFFF"))
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
                val title = songTitles.getOrElse(position) { File(path).name }
                val artist = songArtists.getOrElse(position) { "Studio Audio" }
                val isPlayingNow = position == currentIndex

                holder.tvTitle.text = if (isPlayingNow) "Playing: ${title.substringBeforeLast('.')}" else title.substringBeforeLast('.')
                holder.tvSub.text = artist
                AudioAdapter.loadAlbumArtOrFallbackInto(context, path, title, holder.img)

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
                        tvHeader.text = "Playing Queue (${songPaths.size} Songs)"
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
                Toast.makeText(context, "Phone Ringtone Set: $title", Toast.LENGTH_LONG).show()
            }
        } catch (_: Exception) {
            Toast.makeText(context, "Unable to set ringtone", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Unified Pure-White (#FFFFFF) Vector Icon Drawable for the entire Audio Player & 3-Dot Studio.
     */
    class PureWhiteAudioVectorDrawable(
        private val type: String,
        private val d: Float
    ) : Drawable() {

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * d
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 0f || h <= 0f) return
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val r = w.coerceAtMost(h) * 0.38f
            val path = Path()

            when (type) {
                "BACK_ARROW" -> {
                    canvas.drawLine(cx + r * 0.72f, cy, cx - r * 0.65f, cy, strokePaint)
                    canvas.drawLine(cx - r * 0.65f, cy, cx - r * 0.05f, cy - r * 0.58f, strokePaint)
                    canvas.drawLine(cx - r * 0.65f, cy, cx - r * 0.05f, cy + r * 0.58f, strokePaint)
                }
                "THREE_DOTS" -> {
                    canvas.drawCircle(cx, cy - r * 0.66f, 2.6f * d, fillPaint)
                    canvas.drawCircle(cx, cy, 2.6f * d, fillPaint)
                    canvas.drawCircle(cx, cy + r * 0.66f, 2.6f * d, fillPaint)
                }
                "PLAY" -> {
                    path.moveTo(cx - r * 0.32f, cy - r * 0.58f)
                    path.lineTo(cx + r * 0.64f, cy)
                    path.lineTo(cx - r * 0.32f, cy + r * 0.58f)
                    path.close()
                    canvas.drawPath(path, fillPaint)
                }
                "PAUSE" -> {
                    val bw = r * 0.32f
                    val bh = r * 1.12f
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
                    canvas.drawLine(cx - r * 0.55f, cy - r * 0.52f, cx - r * 0.55f, cy + r * 0.52f, strokePaint)
                    path.moveTo(cx + r * 0.55f, cy - r * 0.52f)
                    path.lineTo(cx - r * 0.38f, cy)
                    path.lineTo(cx + r * 0.55f, cy + r * 0.52f)
                    path.close()
                    canvas.drawPath(path, fillPaint)
                }
                "NEXT" -> {
                    canvas.drawLine(cx + r * 0.55f, cy - r * 0.52f, cx + r * 0.55f, cy + r * 0.52f, strokePaint)
                    path.moveTo(cx - r * 0.55f, cy - r * 0.52f)
                    path.lineTo(cx + r * 0.38f, cy)
                    path.lineTo(cx - r * 0.55f, cy + r * 0.52f)
                    path.close()
                    canvas.drawPath(path, fillPaint)
                }
                "SHUFFLE" -> {
                    canvas.drawLine(cx - r * 0.72f, cy - r * 0.45f, cx + r * 0.72f, cy + r * 0.45f, strokePaint)
                    canvas.drawLine(cx - r * 0.72f, cy + r * 0.45f, cx + r * 0.72f, cy - r * 0.45f, strokePaint)
                    canvas.drawLine(cx + r * 0.35f, cy - r * 0.45f, cx + r * 0.72f, cy - r * 0.45f, strokePaint)
                    canvas.drawLine(cx + r * 0.35f, cy + r * 0.45f, cx + r * 0.72f, cy + r * 0.45f, strokePaint)
                }
                "REPEAT_ALL", "REPEAT_ONE", "AB_REPEAT" -> {
                    val rect = RectF(cx - r * 0.75f, cy - r * 0.52f, cx + r * 0.75f, cy + r * 0.52f)
                    canvas.drawRoundRect(rect, 5f * d, 5f * d, strokePaint)
                    if (type == "REPEAT_ONE") {
                        canvas.drawLine(cx, cy - r * 0.25f, cx, cy + r * 0.25f, strokePaint)
                    }
                }
                "EQUALIZER" -> {
                    canvas.drawLine(cx - r * 0.58f, cy - r * 0.72f, cx - r * 0.58f, cy + r * 0.72f, strokePaint)
                    canvas.drawLine(cx, cy - r * 0.72f, cx, cy + r * 0.72f, strokePaint)
                    canvas.drawLine(cx + r * 0.58f, cy - r * 0.72f, cx + r * 0.58f, cy + r * 0.72f, strokePaint)
                    canvas.drawCircle(cx - r * 0.58f, cy - r * 0.2f, 2.8f * d, fillPaint)
                    canvas.drawCircle(cx, cy + r * 0.25f, 2.8f * d, fillPaint)
                    canvas.drawCircle(cx + r * 0.58f, cy - r * 0.25f, 2.8f * d, fillPaint)
                }
                "QUEUE_LIST" -> {
                    canvas.drawLine(cx - r * 0.75f, cy - r * 0.55f, cx + r * 0.75f, cy - r * 0.55f, strokePaint)
                    canvas.drawLine(cx - r * 0.75f, cy, cx + r * 0.75f, cy, strokePaint)
                    canvas.drawLine(cx - r * 0.75f, cy + r * 0.55f, cx + r * 0.35f, cy + r * 0.55f, strokePaint)
                }
                "HEART_ON", "HEART_OFF" -> {
                    canvas.drawCircle(cx, cy, r * 0.76f, strokePaint)
                    if (type == "HEART_ON") {
                        canvas.drawCircle(cx, cy, r * 0.42f, fillPaint)
                    }
                }
                "TIMER" -> {
                    canvas.drawCircle(cx, cy, r * 0.78f, strokePaint)
                    canvas.drawLine(cx, cy, cx, cy - r * 0.45f, strokePaint)
                    canvas.drawLine(cx, cy, cx + r * 0.35f, cy, strokePaint)
                }
                "PITCH_LAB", "BOOST" -> {
                    canvas.drawLine(cx - r * 0.6f, cy - r * 0.35f, cx - r * 0.6f, cy + r * 0.35f, strokePaint)
                    canvas.drawLine(cx - r * 0.2f, cy - r * 0.75f, cx - r * 0.2f, cy + r * 0.75f, strokePaint)
                    canvas.drawLine(cx + r * 0.2f, cy - r * 0.55f, cx + r * 0.2f, cy + r * 0.55f, strokePaint)
                    canvas.drawLine(cx + r * 0.6f, cy - r * 0.25f, cx + r * 0.6f, cy + r * 0.25f, strokePaint)
                }
                "RINGTONE" -> {
                    val bell = RectF(cx - r * 0.62f, cy - r * 0.65f, cx + r * 0.62f, cy + r * 0.45f)
                    canvas.drawRoundRect(bell, 6f * d, 6f * d, strokePaint)
                    canvas.drawLine(cx - r * 0.78f, cy + r * 0.45f, cx + r * 0.78f, cy + r * 0.45f, strokePaint)
                    canvas.drawCircle(cx, cy + r * 0.68f, 2.5f * d, fillPaint)
                }
                "VINYL_DISC" -> {
                    canvas.drawCircle(cx, cy, r * 0.8f, strokePaint)
                    canvas.drawCircle(cx, cy, r * 0.26f, fillPaint)
                }
                "SHARE" -> {
                    canvas.drawLine(cx - r * 0.5f, cy, cx + r * 0.5f, cy - r * 0.5f, strokePaint)
                    canvas.drawLine(cx - r * 0.5f, cy, cx + r * 0.5f, cy + r * 0.5f, strokePaint)
                    canvas.drawCircle(cx - r * 0.5f, cy, 3f * d, fillPaint)
                    canvas.drawCircle(cx + r * 0.5f, cy - r * 0.5f, 3f * d, fillPaint)
                    canvas.drawCircle(cx + r * 0.5f, cy + r * 0.5f, 3f * d, fillPaint)
                }
                "VAULT" -> {
                    val body = RectF(cx - r * 0.68f, cy - r * 0.1f, cx + r * 0.68f, cy + r * 0.82f)
                    canvas.drawRoundRect(body, 3.5f * d, 3.5f * d, strokePaint)
                    val shackle = RectF(cx - r * 0.45f, cy - r * 0.78f, cx + r * 0.45f, cy - r * 0.05f)
                    canvas.drawArc(shackle, 180f, 180f, false, strokePaint)
                }
                "DELETE" -> {
                    canvas.drawLine(cx - r * 0.75f, cy - r * 0.52f, cx + r * 0.75f, cy - r * 0.52f, strokePaint)
                    val bin = RectF(cx - r * 0.55f, cy - r * 0.52f, cx + r * 0.55f, cy + r * 0.78f)
                    canvas.drawRoundRect(bin, 3f * d, 3f * d, strokePaint)
                }
                else -> {
                    canvas.drawCircle(cx, cy, r * 0.72f, strokePaint)
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
