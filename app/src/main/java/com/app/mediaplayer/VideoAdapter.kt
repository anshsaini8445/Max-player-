package com.app.mediaplayer

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import android.view.Gravity
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
import com.bumptech.glide.request.target.Target
import java.io.File
import java.util.ArrayList
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class VideoAdapter(
    private val context: Context,
    private val videoList: MutableList<VideoModel>,
    private val onSelectionChanged: ((Boolean, Int) -> Unit)? = null
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    companion object {
        private val frameMemoryCache = object : LruCache<String, Bitmap>(120) {}
        private val frameExtractorPool = Executors.newFixedThreadPool(4)

        /**
         * Ultra-Fast Guaranteed Thumbnail Loader for 360p, 720p, 1080p, 2K, 4K, and 8K videos:
         * 1. Instant RAM hit from LruCache (< 1ms).
         * 2. Hardware-decoded Glide thumbnail with ALL disk caching so subsequent app opens are instant.
         * 3. Multi-threaded MediaMetadataRetriever fallback for 8K/4K/MKV files.
         */
        fun loadGuaranteedVideoThumbnail(
            context: Context,
            path: String,
            imageView: ImageView
        ) {
            imageView.tag = path
            val cached = frameMemoryCache.get(path)
            if (cached != null && !cached.isRecycled) {
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                imageView.setImageBitmap(cached)
                return
            }

            val model: Any = if (path.startsWith("content://") || path.startsWith("http")) {
                Uri.parse(path)
            } else {
                File(path)
            }

            Glide.with(context)
                .load(model)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(320, 180)
                .dontAnimate()
                .centerCrop()
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        extractFallbackFrameInBackground(context, path, imageView)
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        return false
                    }
                })
                .into(imageView)
        }

        private fun extractFallbackFrameInBackground(
            context: Context,
            path: String,
            targetView: ImageView
        ) {
            frameExtractorPool.execute {
                var bmp: Bitmap? = null
                val retriever = MediaMetadataRetriever()
                try {
                    if (path.startsWith("content://")) {
                        retriever.setDataSource(context, Uri.parse(path))
                    } else {
                        retriever.setDataSource(path)
                    }
                    bmp = retriever.getFrameAtTime(1000000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.getFrameAtTime(0L)
                    if (bmp != null) {
                        val scaled = Bitmap.createScaledBitmap(bmp, 320, 180, true)
                        frameMemoryCache.put(path, scaled)
                        bmp = scaled
                    }
                } catch (_: Exception) {
                } finally {
                    try {
                        retriever.release()
                    } catch (_: Exception) {
                    }
                }

                if (bmp != null) {
                    (context as? Activity)?.runOnUiThread {
                        if (targetView.tag == path) {
                            targetView.scaleType = ImageView.ScaleType.CENTER_CROP
                            targetView.setImageBitmap(bmp)
                        }
                    }
                }
            }
        }

        fun formatVideoDurationAny(durationVal: Any?): String {
            return when (durationVal) {
                is Long -> {
                    val safeMs = if (durationVal < 36000L) durationVal * 1000L else durationVal
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

        holder.tvTitle.text = video.title
        holder.tvTitle.setTextColor(if (isSelected) accentColor else Color.WHITE)

        val durStr = formatVideoDurationAny(video.duration)
        val resBadge = detectResolutionLabel(video.title, video.size)
        val sizeStr = formatVideoSize(video.size)
        val ext = File(video.path).extension.uppercase(Locale.US).ifEmpty { "MP4" }

        if (holder.tvMetaSub != null) {
            holder.tvDuration?.text = durStr
            holder.tvMetaSub.text = "$resBadge  |  $sizeStr  |  $ext"
            holder.tvMetaSub.setTextColor(accentColor)
        } else {
            holder.tvDuration?.text = "$durStr  •  $resBadge  •  $sizeStr"
            holder.tvDuration?.setTextColor(Color.parseColor("#B0C4DE"))
        }

        loadGuaranteedVideoThumbnail(context, video.path, holder.imgThumbnail)

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
                    val pos = holder.adapterPosition
                    if (pos in videoList.indices) {
                        showVideoItemMenu(pos)
                    }
                }
            }
        }

        // Holding a video row activates Multi-Select Mode (Select All / Delete / Hybrid Share / Other Apps Share)
        holder.itemView.setOnLongClickListener {
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
            "Play Video Now (Instant 8K Engine)",
            "Select Multiple Videos (Batch Delete / Share)",
            "Play in Background Audio Bar",
            "Extract / Convert Video to MP3",
            if (isFav) "Remove from Favorites" else "Add to Favorites",
            "Lock Video into Secret Privacy Vault",
            "Share via Hybrid Turbo Share",
            "Share via Other Apps (WhatsApp / Bluetooth)",
            "Video Resolution & File Path",
            "Delete Video Permanently (From Phone)"
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
                        Toast.makeText(context, "Opening MP3 Extractor...", Toast.LENGTH_SHORT).show()
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
            u.contains("8K") || u.contains("4320P") -> "8K ULTRA"
            u.contains("4K") || u.contains("2160P") -> "4K UHD"
            u.contains("2K") || u.contains("1440P") -> "2K QHD"
            u.contains("1080P") || u.contains("FHD") -> "1080p"
            u.contains("720P") || u.contains("HD") -> "720p"
            u.contains("360P") -> "360p"
            sizeBytes > 250L * 1024L * 1024L -> "4K / 1080p"
            sizeBytes > 50L * 1024L * 1024L -> "1080p"
            sizeBytes > 12L * 1024L * 1024L -> "720p"
            else -> "480p / 360p"
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
            color = Color.WHITE
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
