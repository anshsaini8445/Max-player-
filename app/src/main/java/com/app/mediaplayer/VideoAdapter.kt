package com.app.mediaplayer

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.LruCache
import android.util.Size
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import java.io.File
import java.util.ArrayList
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class VideoAdapter(
    private val context: Context,
    private val videoList: MutableList<VideoModel>,
    private val onSelectionChanged: ((Boolean, Int) -> Unit)? = null
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    companion object {
        private val frameMemoryCache = object : LruCache<String, Bitmap>(160) {}
        private val frameExtractorPool = Executors.newFixedThreadPool(4)

        /**
         * 4-Stage Guaranteed Video Frame Photo Loader:
         * 1. Instant LruCache RAM hit (< 1ms).
         * 2. Immediate HD Cinema Placeholder so no card ever flashes dark/empty while scrolling.
         * 3. Glide Hardware Video Frame decoding at 1.5s offset.
         * 4. Multi-stage MediaMetadataRetriever + Android ThumbnailUtils fallback for MKV/HEVC/10-bit/8K videos.
         */
        fun loadGuaranteedVideoThumbnail(
            context: Context,
            path: String,
            imageView: ImageView
        ) {
            val safeKey = path.ifEmpty { "default_video" }
            imageView.tag = safeKey
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP

            val cached = frameMemoryCache.get(safeKey)
            if (cached != null && !cached.isRecycled) {
                imageView.setImageBitmap(cached)
                return
            }

            val fallbackBmp = createCinemaPosterFallbackBitmap(File(path).name.ifEmpty { "Video" })
            imageView.setImageBitmap(fallbackBmp)

            if (path.isEmpty()) return

            val model: Any = if (path.startsWith("content://") || path.startsWith("http")) {
                Uri.parse(path)
            } else {
                File(path)
            }

            val options = RequestOptions()
                .frame(1500000L)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(360, 202)
                .centerCrop()
                .dontAnimate()

            try {
                Glide.with(context.applicationContext)
                    .asBitmap()
                    .load(model)
                    .apply(options)
                    .listener(object : RequestListener<Bitmap> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Bitmap>,
                            isFirstResource: Boolean
                        ): Boolean {
                            extractFallbackFrameInBackground(context, path, fallbackBmp, imageView)
                            return true
                        }

                        override fun onResourceReady(
                            resource: Bitmap,
                            model: Any,
                            target: Target<Bitmap>?,
                            dataSource: DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
                            frameMemoryCache.put(safeKey, resource)
                            if (imageView.tag == safeKey) {
                                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                                imageView.setImageBitmap(resource)
                            }
                            return true
                        }
                    })
                    .preload()
            } catch (_: Exception) {
                extractFallbackFrameInBackground(context, path, fallbackBmp, imageView)
            }
        }

        private fun extractFallbackFrameInBackground(
            context: Context,
            path: String,
            fallbackBmp: Bitmap,
            targetView: ImageView
        ) {
            frameExtractorPool.execute {
                var bmp: Bitmap? = null

                // Stage A: MediaMetadataRetriever across multiple timestamps
                val retriever = MediaMetadataRetriever()
                try {
                    if (path.startsWith("content://")) {
                        retriever.setDataSource(context, Uri.parse(path))
                    } else {
                        retriever.setDataSource(path)
                    }
                    bmp = retriever.getFrameAtTime(2000000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.getFrameAtTime(500000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.frameAtTime
                } catch (_: Exception) {
                } finally {
                    try {
                        retriever.release()
                    } catch (_: Exception) {
                    }
                }

                // Stage B: Native OS ThumbnailUtils fallback for stubborn HEVC/MKV files
                if (bmp == null && !path.startsWith("content://")) {
                    try {
                        val file = File(path)
                        if (file.exists()) {
                            bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                ThumbnailUtils.createVideoThumbnail(file, Size(360, 202), null)
                            } else {
                                @Suppress("DEPRECATION")
                                ThumbnailUtils.createVideoThumbnail(path, MediaStore.Video.Thumbnails.MINI_KIND)
                            }
                        }
                    } catch (_: Exception) {
                    }
                }

                val finalBitmap = if (bmp != null) {
                    try {
                        Bitmap.createScaledBitmap(bmp, 360, 202, true)
                    } catch (_: Exception) {
                        bmp
                    }
                } else {
                    fallbackBmp
                }

                frameMemoryCache.put(path, finalBitmap)
                (context as? Activity)?.runOnUiThread {
                    if (targetView.tag == path) {
                        targetView.scaleType = ImageView.ScaleType.CENTER_CROP
                        targetView.setImageBitmap(finalBitmap)
                    }
                }
            }
        }

        private fun createCinemaPosterFallbackBitmap(title: String): Bitmap {
            val w = 320
            val h = 180
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            val palettes = arrayOf(
                intArrayOf(Color.parseColor("#0F2027"), Color.parseColor("#203A43"), Color.parseColor("#2C5364")),
                intArrayOf(Color.parseColor("#141E30"), Color.parseColor("#243B55"), Color.parseColor("#1B3B6F")),
                intArrayOf(Color.parseColor("#1F1C2C"), Color.parseColor("#4A4063"), Color.parseColor("#2C3E50")),
                intArrayOf(Color.parseColor("#093028"), Color.parseColor("#1B4332"), Color.parseColor("#2D6A4F"))
            )
            val colors = palettes[abs(title.hashCode()) % palettes.size]

            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, w.toFloat(), h.toFloat(),
                    colors, null, Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

            val cx = w / 2f
            val cy = h / 2f
            val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#33FFFFFF")
            }
            canvas.drawCircle(cx, cy, 30f, circlePaint)

            val playPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.WHITE
            }
            val path = Path().apply {
                moveTo(cx - 8f, cy - 13f)
                lineTo(cx + 15f, cy)
                lineTo(cx - 8f, cy + 13f)
                close()
            }
            canvas.drawPath(path, playPaint)
            return bmp
        }

        fun formatVideoDurationAny(durationVal: Any?): String {
            return when (durationVal) {
                is Long -> {
                    val safeMs = if (durationVal in 1L..3600L) durationVal * 1000L else durationVal
                    val totalSecs = TimeUnit.MILLISECONDS.toSeconds(safeMs.coerceAtLeast(0L))
                    val hrs = totalSecs / 3600
                    val mins = (totalSecs % 3600) / 60
                    val secs = totalSecs % 60
                    if (hrs > 0) {
                        String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
                    } else {
                        String.format(Locale.US, "%02d:%02d", mins, secs)
                    }
                }
                is String -> durationVal
                else -> "00:00"
            }
        }
    }

    private var isMultiSelectMode = false
    private val selectedPaths = LinkedHashSet<String>()
    private var highlightQuery: String = ""

    fun setSearchHighlightQuery(query: String) {
        highlightQuery = query.trim()
    }

    fun isSelectionModeActive(): Boolean = isMultiSelectMode

    fun getSelectedVideos(): List<VideoModel> {
        return videoList.filter { selectedPaths.contains(it.path) }
    }

    fun getSelectedCount(): Int = selectedPaths.size

    fun selectAllVideos() {
        isMultiSelectMode = true
        if (selectedPaths.size == videoList.size) {
            selectedPaths.clear()
        } else {
            videoList.forEach { selectedPaths.add(it.path) }
        }
        notifyDataSetChanged()
        onSelectionChanged?.invoke(isMultiSelectMode, selectedPaths.size)
    }

    fun exitMultiSelectMode() {
        isMultiSelectMode = false
        selectedPaths.clear()
        notifyDataSetChanged()
        onSelectionChanged?.invoke(false, 0)
    }

    class VideoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView
        val tvDuration: TextView?
        val tvMetaSub: TextView?
        val imgThumbnail: ImageView
        val btnMore: ImageView?

        init {
            val textViews = mutableListOf<TextView>()
            val imageViews = mutableListOf<ImageView>()
            collectViews(view, textViews, imageViews)

            val pkg = view.context.packageName
            val res = view.resources

            val titleId = res.getIdentifier("tvVideoTitle", "id", pkg)
            val durId = res.getIdentifier("tvVideoDuration", "id", pkg)
            val thumbId = res.getIdentifier("imgVideoThumbnail", "id", pkg)

            tvTitle = (if (titleId != 0) view.findViewById<TextView?>(titleId) else null)
                ?: textViews.firstOrNull()
                ?: TextView(view.context)

            tvDuration = (if (durId != 0) view.findViewById<TextView?>(durId) else null)
                ?: textViews.getOrNull(1)

            tvMetaSub = textViews.firstOrNull { it !== tvTitle && it !== tvDuration }

            imgThumbnail = (if (thumbId != 0) view.findViewById<ImageView?>(thumbId) else null)
                ?: imageViews.firstOrNull()
                ?: ImageView(view.context)

            btnMore = if (imageViews.size > 1) imageViews.lastOrNull { it !== imgThumbnail } else null
        }

        companion object {
            private fun collectViews(
                v: View,
                textOut: MutableList<TextView>,
                imgOut: MutableList<ImageView>
            ) {
                when (v) {
                    is TextView -> textOut.add(v)
                    is ImageView -> imgOut.add(v)
                    is ViewGroup -> {
                        for (i in 0 until v.childCount) {
                            collectViews(v.getChildAt(i), textOut, imgOut)
                        }
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_video, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val d = context.resources.displayMetrics.density
        val video = videoList[position]
        val theme = ThemeStudioHelper.getCurrentTheme(context)
        val accentColor = Color.parseColor(theme.accentHex)
        val isSelected = selectedPaths.contains(video.path)

        val cleanDisplayTitle = video.title.substringBeforeLast('.').replace("_", " ").trim().ifEmpty { video.title }
        holder.tvTitle.maxLines = 2
        holder.tvTitle.ellipsize = TextUtils.TruncateAt.END
        holder.tvTitle.textSize = 14.5f
        holder.tvTitle.typeface = Typeface.DEFAULT_BOLD

        if (highlightQuery.isNotEmpty()) {
            val span = SpannableString(cleanDisplayTitle)
            val lowerTitle = cleanDisplayTitle.lowercase(Locale.US)
            val lowerQ = highlightQuery.lowercase(Locale.US)
            var startIdx = lowerTitle.indexOf(lowerQ)
            while (startIdx >= 0) {
                val endIdx = (startIdx + lowerQ.length).coerceAtMost(cleanDisplayTitle.length)
                span.setSpan(
                    ForegroundColorSpan(Color.parseColor("#FFD700")),
                    startIdx,
                    endIdx,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                startIdx = lowerTitle.indexOf(lowerQ, endIdx)
            }
            holder.tvTitle.text = span
            holder.tvTitle.setTextColor(Color.WHITE)
        } else {
            holder.tvTitle.text = cleanDisplayTitle
            holder.tvTitle.setTextColor(if (isSelected) accentColor else Color.WHITE)
        }

        val prefs = context.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        val watchedMs = prefs.getLong("watched_pos_${video.path}", 0L)
        val totalDurMs = if (video.duration in 1L..3600L) video.duration * 1000L else video.duration.coerceAtLeast(1L)
        val watchedRatio = if (watchedMs > 3000L && totalDurMs > 0L) {
            (watchedMs.toFloat() / totalDurMs.toFloat()).coerceIn(0.04f, 1.0f)
        } else {
            0f
        }

        val durStr = formatVideoDurationAny(video.duration)
        val resBadge = detectResolutionLabel(video.title, video.size)
        val sizeStr = formatVideoSize(video.size)
        val resumeTag = when {
            watchedRatio in 0.04f..0.94f -> "  •  RESUME ${(watchedRatio * 100).toInt()}%"
            watchedRatio > 0.94f -> "  •  WATCHED"
            else -> ""
        }

        if (holder.tvMetaSub != null) {
            holder.tvDuration?.text = durStr
            holder.tvMetaSub.text = "$resBadge  •  $sizeStr$resumeTag"
            holder.tvMetaSub.setTextColor(if (watchedRatio in 0.04f..0.94f) Color.parseColor("#00E676") else Color.parseColor("#9FB4CE"))
        } else {
            holder.tvDuration?.text = "$durStr  •  $resBadge  •  $sizeStr$resumeTag"
            holder.tvDuration?.setTextColor(if (watchedRatio in 0.04f..0.94f) Color.parseColor("#00E676") else Color.parseColor("#9FB4CE"))
        }

        loadGuaranteedVideoThumbnail(context, video.path, holder.imgThumbnail)

        // Overlay sleek bottom Resume Progress Line on the video thumbnail
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            holder.imgThumbnail.foreground = if (watchedRatio > 0.03f) {
                ThumbnailResumeProgressOverlay(d, watchedRatio)
            } else {
                null
            }
        }

        holder.btnMore?.let { rightIcon ->
            rightIcon.clearColorFilter()
            if (isMultiSelectMode) {
                rightIcon.setImageDrawable(SelectionCheckCircleDrawable(d, isSelected, accentColor))
                rightIcon.setOnClickListener {
                    toggleItemSelection(video.path, holder.adapterPosition)
                }
            } else {
                rightIcon.setImageDrawable(ThreeDotsMenuDrawable(d))
                rightIcon.setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    val pos = holder.adapterPosition
                    if (pos in videoList.indices) {
                        showVideoItemMenu(pos)
                    }
                }
            }
        }

        holder.itemView.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            val pos = holder.adapterPosition
            if (pos in videoList.indices) {
                val item = videoList[pos]
                if (!isMultiSelectMode) {
                    isMultiSelectMode = true
                    selectedPaths.clear()
                    selectedPaths.add(item.path)
                    notifyDataSetChanged()
                    onSelectionChanged?.invoke(true, selectedPaths.size)
                } else {
                    toggleItemSelection(item.path, pos)
                }
            }
            true
        }

        holder.itemView.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos !in videoList.indices) return@setOnClickListener
            if (isMultiSelectMode) {
                toggleItemSelection(videoList[pos].path, pos)
            } else {
                openVideoInPlayer(pos, startInBgAudio = false)
            }
        }
    }

    private fun toggleItemSelection(path: String, adapterPos: Int) {
        if (selectedPaths.contains(path)) {
            selectedPaths.remove(path)
        } else {
            selectedPaths.add(path)
        }
        if (adapterPos != RecyclerView.NO_POSITION) {
            notifyItemChanged(adapterPos)
        }
        onSelectionChanged?.invoke(isMultiSelectMode, selectedPaths.size)
    }

    private fun openVideoInPlayer(index: Int, startInBgAudio: Boolean) {
        if (index !in videoList.indices) return
        val selected = videoList[index]
        val allPaths = ArrayList<String>(videoList.size)
        for (v in videoList) {
            allPaths.add(v.path)
        }
        val intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra("VIDEO_PATH", selected.path)
            putExtra("VIDEO_TITLE", selected.title)
            putStringArrayListExtra("VIDEO_LIST", allPaths)
            putExtra("START_BG_AUDIO", startInBgAudio)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        context.startActivity(intent)
    }

    private fun showVideoItemMenu(position: Int) {
        if (position !in videoList.indices) return
        val item = videoList[position]
        val prefs = context.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        val isFav = prefs.getBoolean("fav_${item.path}", false)

        val options = arrayOf(
            "Play Video Now",
            "Select Multiple Videos (Batch Share / Delete)",
            "Play in Background Audio Bar",
            "Convert Video to MP3 Audio",
            if (isFav) "Remove from Favorites" else "Add to Favorites",
            "Lock Video in Privacy Vault",
            "Share via Turbo Share",
            "Share via Other Apps",
            "Video Resolution & File Details",
            "Move to Recycle Bin / Delete"
        )

        VideoFragment.showBrightVideoMenuDialog(context, item.title, options) { which ->
            when (which) {
                0 -> openVideoInPlayer(position, startInBgAudio = false)
                1 -> {
                    isMultiSelectMode = true
                    selectedPaths.clear()
                    selectedPaths.add(item.path)
                    notifyDataSetChanged()
                    onSelectionChanged?.invoke(true, selectedPaths.size)
                }
                2 -> openVideoInPlayer(position, startInBgAudio = true)
                3 -> {
                    try {
                        val intent = Intent(context, Mp3ConverterActivity::class.java).apply {
                            putExtra("VIDEO_PATH", item.path)
                            putExtra("VIDEO_TITLE", item.title)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                    }
                }
                4 -> {
                    prefs.edit().putBoolean("fav_${item.path}", !isFav).apply()
                    Toast.makeText(
                        context,
                        if (!isFav) "Added to Favorites" else "Removed from Favorites",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                5 -> FakeVaultHelper.lockMediaIntoVault(context, item.path, item.title)
                6 -> {
                    try {
                        val intent = Intent(context, FileTransferActivity::class.java).apply {
                            putStringArrayListExtra("PRESELECTED_PATHS", arrayListOf(item.path))
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                    }
                }
                7 -> {
                    try {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "video/*"
                            putExtra(Intent.EXTRA_STREAM, Uri.parse(item.path))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
                    } catch (_: Exception) {
                        Toast.makeText(context, "Unable to share video", Toast.LENGTH_SHORT).show()
                    }
                }
                8 -> {
                    val res = detectResolutionLabel(item.title, item.size)
                    val durStr = formatVideoDurationAny(item.duration)
                    AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Video Details & Info")
                        .setMessage(
                            "Title: ${item.title}\n\n" +
                                "Quality: $res\n\n" +
                                "Duration: $durStr\n\n" +
                                "Size: ${formatVideoSize(item.size)}\n\n" +
                                "Location: ${item.path}"
                        )
                        .setPositiveButton("OK", null)
                        .show()
                }
                9 -> {
                    PermanentDeleteHelper.confirmAndDeletePermanently(
                        context = context,
                        title = item.title,
                        path = item.path,
                        mediaId = item.id,
                        isVideo = true
                    ) {
                        if (position in videoList.indices) {
                            videoList.removeAt(position)
                            notifyItemRemoved(position)
                            notifyItemRangeChanged(position, videoList.size)
                        }
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int = videoList.size

    private fun detectResolutionLabel(title: String, sizeBytes: Long): String {
        val u = title.uppercase(Locale.US)
        return when {
            u.contains("8K") || u.contains("4320P") -> "8K"
            u.contains("4K") || u.contains("2160P") -> "4K"
            u.contains("2K") || u.contains("1440P") -> "2K"
            u.contains("1080P") || u.contains("FHD") -> "1080p"
            u.contains("720P") || u.contains("HD") -> "720p"
            u.contains("360P") -> "360p"
            sizeBytes > 250L * 1024L * 1024L -> "4K / FHD"
            sizeBytes > 50L * 1024L * 1024L -> "1080p"
            sizeBytes > 12L * 1024L * 1024L -> "720p"
            else -> "HD"
        }
    }

    private fun formatVideoSize(bytes: Long): String {
        val mb = bytes.coerceAtLeast(0L) / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format(Locale.US, "%.2f GB", mb / 1024.0)
        } else {
            String.format(Locale.US, "%.1f MB", mb)
        }
    }

    private class ThumbnailResumeProgressOverlay(
        private val d: Float,
        private val progressFraction: Float
    ) : Drawable() {
        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#88000000")
        }
        private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#FF1744")
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            val barH = 3.2f * d
            canvas.drawRect(0f, h - barH, w, h, trackPaint)
            canvas.drawRect(0f, h - barH, w * progressFraction.coerceIn(0f, 1f), h, progressPaint)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private class SelectionCheckCircleDrawable(
        private val d: Float,
        private val isChecked: Boolean,
        private val accentColor: Int
    ) : Drawable() {
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (isChecked) accentColor else Color.parseColor("#22FFFFFF")
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * d
            color = Color.WHITE
        }
        private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * d
            strokeCap = Paint.Cap.ROUND
            color = Color.parseColor("#0A0A10")
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val r = b.width().coerceAtMost(b.height()) * 0.42f
            canvas.drawCircle(cx, cy, r, fillPaint)
            canvas.drawCircle(cx, cy, r, strokePaint)
            if (isChecked) {
                canvas.drawLine(cx - r * 0.42f, cy, cx - r * 0.1f, cy + r * 0.35f, checkPaint)
                canvas.drawLine(cx - r * 0.1f, cy + r * 0.35f, cx + r * 0.45f, cy - r * 0.32f, checkPaint)
            }
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private class ThreeDotsMenuDrawable(private val d: Float) : Drawable() {
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#B0C4DE")
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val r = b.width().coerceAtMost(b.height()) * 0.42f
            val dotR = 2.0f * d
            val gap = r * 0.45f
            canvas.drawCircle(cx, cy - gap, dotR, dotPaint)
            canvas.drawCircle(cx, cy, dotR, dotPaint)
            canvas.drawCircle(cx, cy + gap, dotR, dotPaint)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
