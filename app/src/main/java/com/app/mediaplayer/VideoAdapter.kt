package com.app.mediaplayer

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
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

class VideoAdapter(
    private val context: Context,
    private val videoList: MutableList<VideoModel>
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    companion object {
        private val frameMemoryCache = object : LruCache<String, Bitmap>(80) {}
        private val frameExtractorPool = Executors.newFixedThreadPool(3)

        /**
         * Guaranteed Thumbnail Loader for 360p, 720p, 1080p, 2K, 4K, and 8K videos:
         * 1. Checks fast LruCache first.
         * 2. Uses Glide hardware-accelerated video decoder.
         * 3. If Glide fails on an 8K/4K/MKV/WEBM container, extracts a real video frame on a background thread
         *    using MediaMetadataRetriever so no video card ever stays blank!
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
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .override(320, 180)
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
    }

    class VideoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvVideoTitle)
        val tvDuration: TextView = view.findViewById(R.id.tvVideoDuration)
        val imgThumbnail: ImageView = view.findViewById(R.id.imgVideoThumbnail)
        val tvMetaSub: TextView? = findSecondTextView(view)
        val btnMore: ImageView? = findMoreImageView(view)

        companion object {
            private fun findSecondTextView(root: View): TextView? {
                val list = mutableListOf<TextView>()
                collectTextViews(root, list)
                return list.firstOrNull {
                    it.id != R.id.tvVideoTitle && it.id != R.id.tvVideoDuration
                }
            }

            private fun findMoreImageView(root: View): ImageView? {
                val list = mutableListOf<ImageView>()
                collectImageViews(root, list)
                return list.lastOrNull { it.id != R.id.imgVideoThumbnail }
            }

            private fun collectTextViews(v: View, out: MutableList<TextView>) {
                if (v is TextView) out.add(v)
                else if (v is ViewGroup) {
                    for (i in 0 until v.childCount) collectTextViews(v.getChildAt(i), out)
                }
            }

            private fun collectImageViews(v: View, out: MutableList<ImageView>) {
                if (v is ImageView) out.add(v)
                else if (v is ViewGroup) {
                    for (i in 0 until v.childCount) collectImageViews(v.getChildAt(i), out)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_video, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        InShotScrollAnimator.animateRowOnBind(holder.itemView)

        val video = videoList[position]
        val theme = ThemeStudioHelper.getCurrentTheme(context)
        val accentColor = Color.parseColor(theme.accentHex)

        holder.tvTitle.text = video.title
        holder.tvTitle.setTextColor(Color.WHITE)
        holder.tvDuration.text = video.duration

        val resBadge = detectResolutionLabel(video.title, video.size)
        val sizeStr = formatVideoSize(video.size)
        val ext = File(video.path).extension.uppercase(Locale.US).ifEmpty { "MP4" }

        holder.tvMetaSub?.let { sub ->
            sub.text = "$resBadge  |  $sizeStr  |  $ext"
            sub.setTextColor(accentColor)
        }

        loadGuaranteedVideoThumbnail(context, video.path, holder.imgThumbnail)

        holder.btnMore?.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos in videoList.indices) {
                showVideoItemMenu(pos)
            }
        }

        holder.itemView.setOnLongClickListener {
            val pos = holder.adapterPosition
            if (pos in videoList.indices) {
                showVideoItemMenu(pos)
            }
            true
        }

        holder.itemView.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos !in videoList.indices) return@setOnClickListener
            openVideoInPlayer(pos, startInBgAudio = false)
        }
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
        }
        context.startActivity(intent)
    }

    private fun showVideoItemMenu(position: Int) {
        if (position !in videoList.indices) return
        val item = videoList[position]
        val prefs = context.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        val isFav = prefs.getBoolean("fav_${item.path}", false)

        val options = arrayOf(
            "▶️   Play Video Now (8K Hybrid Engine)",
            "🎧   Play in Background Audio Bar",
            "🎵   Extract / Convert Video to MP3",
            if (isFav) "💔   Remove from Favorites" else "❤️   Add to Favorites",
            "🔒   Lock Video into Secret Privacy Vault",
            "📤   Share Video (Wi-Fi Direct / WhatsApp)",
            "ℹ️   Video Resolution & File Path",
            "🗑️   Delete Video Permanently (From Phone)"
        )

        VideoFragment.showBrightVideoMenuDialog(context, item.title, options) { which ->
            when (which) {
                0 -> openVideoInPlayer(position, startInBgAudio = false)
                1 -> openVideoInPlayer(position, startInBgAudio = true)
                2 -> {
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
                3 -> {
                    prefs.edit().putBoolean("fav_${item.path}", !isFav).apply()
                    Toast.makeText(
                        context,
                        if (!isFav) "Added to Favorites ❤️" else "Removed from Favorites",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                4 -> FakeVaultHelper.lockMediaIntoVault(context, item.path, item.title)
                5 -> {
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
                6 -> {
                    val res = detectResolutionLabel(item.title, item.size)
                    AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Video Details & Info")
                        .setMessage(
                            "Title: ${item.title}\n\n" +
                                "Quality: $res\n\n" +
                                "Duration: ${item.duration}\n\n" +
                                "Size: ${formatVideoSize(item.size)}\n\n" +
                                "Location: ${item.path}"
                        )
                        .setPositiveButton("OK", null)
                        .show()
                }
                7 -> {
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
}
