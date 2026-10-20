package com.app.mediaplayer

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File
import java.util.ArrayList
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class VideoAdapter(private val context: Context, videos: List<VideoModel>) :
    RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    private val videoList: MutableList<VideoModel> =
        if (videos is MutableList<VideoModel>) videos else videos.toMutableList()

    class VideoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgThumbnail: ImageView = view.findViewById(R.id.imgThumbnail)
        val tvDuration: TextView = view.findViewById(R.id.tvDuration)
        val pbVideoWatchedProgress: ProgressBar = view.findViewById(R.id.pbVideoWatchedProgress)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvSizeFormat: TextView = view.findViewById(R.id.tvSizeFormat)
        val btnOptions: ImageView = view.findViewById(R.id.btnOptions)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_video, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val video = videoList[position]

        holder.tvTitle.text = video.title
        holder.tvDuration.text = formatDuration(video.duration)

        val prefs = context.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        val resPrefs = context.getSharedPreferences("MAX_VIDEO_RES_PREFS", Context.MODE_PRIVATE)

        // Show Watched Progress Bar at the bottom of the video thumbnail
        val watchedPos = prefs.getLong("watched_pos_${video.path}", 0L)
        val historyTime = prefs.getLong("history_time_${video.path}", 0L)
        val totalDur = video.duration.coerceAtLeast(1L)

        if (watchedPos > 0L) {
            holder.pbVideoWatchedProgress.visibility = View.VISIBLE
            val progressVal = ((watchedPos.toFloat() / totalDur.toFloat()) * 1000).toInt().coerceIn(50, 1000)
            holder.pbVideoWatchedProgress.progress = progressVal
        } else if (historyTime > 0L) {
            holder.pbVideoWatchedProgress.visibility = View.VISIBLE
            holder.pbVideoWatchedProgress.progress = 1000
        } else {
            holder.pbVideoWatchedProgress.visibility = View.GONE
        }

        // Resolution Badge (360p / 720p / 1080p / 2K / 4K / 8K) + Size + Format
        val resolutionLabel = resPrefs.getString("res_${video.path}", null)
            ?: VideoFragment.computeResolutionLabel(0, 0, video.title, video.size, video.duration)

        val sizeStr = formatFileSize(video.size)
        val extension = File(video.path).extension.uppercase(Locale.getDefault()).ifEmpty { "MP4" }
        val isFav = prefs.getBoolean("fav_${video.path}", false)
        val favTag = if (isFav) " | FAV" else ""

        holder.tvSizeFormat.text = "$resolutionLabel | $sizeStr | $extension$favTag"

        val glideTarget: Any = if (video.path.startsWith("content://")) {
            Uri.parse(video.path)
        } else {
            File(video.path)
        }

        Glide.with(context)
            .load(glideTarget)
            .placeholder(android.R.color.darker_gray)
            .centerCrop()
            .into(holder.imgThumbnail)

        holder.itemView.setOnClickListener {
            playVideo(video)
        }

        // High-Contrast White Text 3-Dots Dialog
        holder.btnOptions.setOnClickListener {
            val currentFav = prefs.getBoolean("fav_${video.path}", false)
            val menuItems = arrayOf(
                "Play Video",
                "Convert to MP3",
                "Move to Privacy Vault",
                if (currentFav) "Remove from Favorites" else "Add to Favorites",
                "Share Video",
                "Properties ($resolutionLabel | $sizeStr)"
            )

            VideoFragment.showBrightVideoMenuDialog(context, video.title, menuItems) { which ->
                when (which) {
                    0 -> playVideo(video)
                    1 -> {
                        val intent = Intent(context, Mp3ConverterActivity::class.java).apply {
                            putExtra("VIDEO_PATH", video.path)
                            putExtra("VIDEO_TITLE", video.title)
                        }
                        context.startActivity(intent)
                    }
                    2 -> hideVideoToVault(video, holder.adapterPosition)
                    3 -> {
                        prefs.edit().putBoolean("fav_${video.path}", !currentFav).apply()
                        notifyItemChanged(holder.adapterPosition)
                        Toast.makeText(
                            context,
                            if (!currentFav) "Added to Favorites" else "Removed from Favorites",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    4 -> {
                        try {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "video/*"
                                putExtra(Intent.EXTRA_STREAM, Uri.parse(video.path))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Unable to share video", Toast.LENGTH_SHORT).show()
                        }
                    }
                    5 -> {
                        AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                            .setTitle("Video Properties")
                            .setMessage(
                                "Name: ${video.title}\n\n" +
                                    "Quality: $resolutionLabel\n\n" +
                                    "Format: $extension\n\n" +
                                    "Size: $sizeStr\n\n" +
                                    "Duration: ${formatDuration(video.duration)}\n\n" +
                                    "Location: ${video.path}"
                            )
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
        }
    }

    private fun hideVideoToVault(video: VideoModel, currentPosition: Int) {
        if (currentPosition == RecyclerView.NO_POSITION) return
        Toast.makeText(context, "Moving to Vault...", Toast.LENGTH_SHORT).show()

        thread {
            val success = VaultHelper.hideVideo(context, video)
            Handler(Looper.getMainLooper()).post {
                if (success) {
                    Toast.makeText(context, "Moved to Privacy Vault!", Toast.LENGTH_SHORT).show()
                    if (currentPosition >= 0 && currentPosition < videoList.size) {
                        videoList.removeAt(currentPosition)
                        notifyItemRemoved(currentPosition)
                        notifyItemRangeChanged(currentPosition, videoList.size)
                    }
                } else {
                    Toast.makeText(context, "Failed to hide video.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun playVideo(video: VideoModel) {
        val allPaths = ArrayList<String>(videoList.size)
        for (item in videoList) {
            allPaths.add(item.path)
        }
        val intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra("VIDEO_PATH", video.path)
            putExtra("VIDEO_TITLE", video.title)
            putStringArrayListExtra("VIDEO_LIST", allPaths)
        }
        context.startActivity(intent)
    }

    override fun getItemCount(): Int = videoList.size

    private fun formatFileSize(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format(Locale.US, "%.2f GB", mb / 1024.0)
        } else {
            String.format(Locale.US, "%.1f MB", mb)
        }
    }

    private fun formatDuration(durationMillis: Long): String {
        val safeDuration = durationMillis.coerceAtLeast(0L)
        val hours = TimeUnit.MILLISECONDS.toHours(safeDuration)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(safeDuration) - TimeUnit.HOURS.toMinutes(hours)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(safeDuration) -
            TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(safeDuration))

        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}
