package com.app.mediaplayer

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import java.io.File
import java.util.ArrayList
import java.util.Locale
import java.util.concurrent.TimeUnit

class VideoAdapter(
    private val context: Context,
    private val videoList: MutableList<VideoModel>
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    class VideoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgThumbnail: ImageView = view.findViewById(R.id.imgThumbnail)
        val tvDuration: TextView = view.findViewById(R.id.tvDuration)
        val pbWatchedProgress: ProgressBar = view.findViewById(R.id.pbWatchedProgress)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvSizeAndFormat: TextView = view.findViewById(R.id.tvSizeAndFormat)
        val btnMoreOptions: ImageView = view.findViewById(R.id.btnMoreOptions)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_video, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        InShotScrollAnimator.animateRowOnBind(holder.itemView)

        val video = videoList[position]
        val prefs = context.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        val themePrefs = context.getSharedPreferences("MAX_THEME_PREFS", Context.MODE_PRIVATE)
        val accentHex = themePrefs.getString("app_accent_color", "#00FFFF") ?: "#00FFFF"
        val accentColor = try {
            Color.parseColor(accentHex)
        } catch (_: Exception) {
            Color.parseColor("#00FFFF")
        }

        val isFav = prefs.getBoolean("fav_${video.path}", false)
        val favMark = if (isFav) "  ♥" else ""

        holder.tvTitle.text = video.title
        holder.tvTitle.setTextColor(Color.WHITE)
        holder.tvDuration.text = formatDuration(video.duration)

        val resBadge = computeResolutionBadge(video.title, video.size)
        val ext = File(video.path).extension.uppercase(Locale.US).ifEmpty { "MP4" }
        holder.tvSizeAndFormat.text = "$resBadge  •  ${formatSize(video.size)}  •  $ext$favMark"
        holder.tvSizeAndFormat.setTextColor(accentColor)

        val watchedPos = prefs.getLong("watched_pos_${video.path}", 0L)
        if (watchedPos > 1000L && video.duration > 0L) {
            holder.pbWatchedProgress.visibility = View.VISIBLE
            holder.pbWatchedProgress.max = 1000
            holder.pbWatchedProgress.progress =
                ((watchedPos.toFloat() / video.duration.toFloat()) * 1000f).toInt().coerceIn(40, 1000)
        } else {
            holder.pbWatchedProgress.visibility = View.GONE
        }

        val modelTarget: Any = if (video.path.startsWith("content://")) {
            Uri.parse(video.path)
        } else {
            File(video.path)
        }

        Glide.with(context)
            .load(modelTarget)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .dontAnimate()
            .placeholder(android.R.color.darker_gray)
            .centerCrop()
            .into(holder.imgThumbnail)

        holder.itemView.setOnClickListener {
            openVideoInPlayer(holder.adapterPosition)
        }

        holder.btnMoreOptions.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos !in videoList.indices) return@setOnClickListener
            val item = videoList[pos]
            val curFav = prefs.getBoolean("fav_${item.path}", false)

            val menuOptions = arrayOf(
                "▶   Play in 8K Ultra Player",
                "📱   Play in Vertical Shorts / Reels Mode",
                "🎧   Play in Background Audio Mode",
                if (curFav) "💔   Remove from Favorites" else "❤️   Add to Favorites",
                "🎵   Convert Video to MP3 Audio",
                "🔒   Lock Video into Secret Privacy Vault",
                "📤   Share Video (WhatsApp / Wi-Fi Direct)",
                "ℹ️   Video Details & File Path",
                "🗑️   Delete Video Permanently (From Phone)"
            )

            VideoFragment.showBrightVideoMenuDialog(context, item.title, menuOptions) { which ->
                when (which) {
                    0 -> openVideoInPlayer(pos)
                    1 -> {
                        val allPaths = ArrayList(videoList.map { it.path })
                        context.startActivity(Intent(context, ShortsPlayerActivity::class.java).apply {
                            putStringArrayListExtra("SHORTS_LIST", allPaths)
                            putExtra("START_INDEX", pos)
                        })
                    }
                    2 -> openVideoInPlayer(pos, startInBgAudio = true)
                    3 -> {
                        prefs.edit().putBoolean("fav_${item.path}", !curFav).apply()
                        notifyItemChanged(pos)
                        Toast.makeText(
                            context,
                            if (!curFav) "Added to Favorites ♥" else "Removed from Favorites",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    4 -> {
                        context.startActivity(Intent(context, Mp3ConverterActivity::class.java).apply {
                            putExtra("VIDEO_PATH", item.path)
                            putExtra("VIDEO_TITLE", item.title)
                        })
                    }
                    5 -> FakeVaultHelper.lockMediaIntoVault(context, item.path, item.title)
                    6 -> {
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
                    7 -> {
                        AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                            .setTitle("Video Properties")
                            .setMessage(
                                "Title: ${item.title}\n\n" +
                                    "Resolution: $resBadge\n\n" +
                                    "Duration: ${formatDuration(item.duration)}\n\n" +
                                    "Size: ${formatSize(item.size)}\n\n" +
                                    "Location: ${item.path}"
                            )
                            .setPositiveButton("OK", null)
                            .show()
                    }
                    8 -> {
                        PermanentDeleteHelper.confirmAndDeletePermanently(
                            context = context,
                            title = item.title,
                            path = item.path,
                            mediaId = item.id,
                            isVideo = true
                        ) {
                            DeepMediaScanner.invalidateCache()
                            if (pos in videoList.indices) {
                                videoList.removeAt(pos)
                                notifyItemRemoved(pos)
                                notifyItemRangeChanged(pos, videoList.size)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun openVideoInPlayer(selectedIndex: Int, startInBgAudio: Boolean = false) {
        if (selectedIndex !in videoList.indices) return
        val selected = videoList[selectedIndex]
        val allPaths = ArrayList<String>(videoList.size)
        for (v in videoList) {
            allPaths.add(v.path)
        }

        val intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra("VIDEO_PATH", selected.path)
            putExtra("VIDEO_TITLE", selected.title)
            putExtra("START_BG_AUDIO", startInBgAudio)
            putStringArrayListExtra("VIDEO_LIST", allPaths)
        }
        context.startActivity(intent)
    }

    override fun getItemCount(): Int = videoList.size

    private fun computeResolutionBadge(title: String, sizeBytes: Long): String {
        val upper = title.uppercase(Locale.US)
        return when {
            upper.contains("8K") || upper.contains("4320P") -> "8K"
            upper.contains("4K") || upper.contains("2160P") -> "4K"
            upper.contains("2K") || upper.contains("1440P") -> "2K"
            upper.contains("1080P") || upper.contains("FHD") -> "1080p"
            upper.contains("720P") || upper.contains("HD") -> "720p"
            sizeBytes > 120L * 1024L * 1024L -> "1080p"
            sizeBytes > 25L * 1024L * 1024L -> "720p"
            else -> "480p"
        }
    }

    private fun formatSize(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format(Locale.US, "%.2f GB", mb / 1024.0)
        } else {
            String.format(Locale.US, "%.1f MB", mb)
        }
    }

    private fun formatDuration(millis: Long): String {
        val safe = millis.coerceAtLeast(0L)
        val hours = TimeUnit.MILLISECONDS.toHours(safe)
        val mins = TimeUnit.MILLISECONDS.toMinutes(safe) % 60
        val secs = TimeUnit.MILLISECONDS.toSeconds(safe) % 60
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }
}
