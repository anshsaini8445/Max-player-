package com.app.mediaplayer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
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

    // Directly reference the mutable list so notifyDataSetChanged() reflects scanned videos immediately
    private val videoList: MutableList<VideoModel> =
        if (videos is MutableList<VideoModel>) videos else videos.toMutableList()

    class VideoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgThumbnail: ImageView = view.findViewById(R.id.imgThumbnail)
        val tvDuration: TextView = view.findViewById(R.id.tvDuration)
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

        val sizeInMb = video.size / (1024.0 * 1024.0)
        val extension = File(video.path).extension.lowercase(Locale.getDefault()).ifEmpty { "video" }
        holder.tvSizeFormat.text = String.format(Locale.US, "%.2f MB • (%s)", sizeInMb, extension)

        Glide.with(context)
            .load(File(video.path))
            .placeholder(android.R.color.darker_gray)
            .centerCrop()
            .into(holder.imgThumbnail)

        holder.itemView.setOnClickListener {
            playVideo(video)
        }

        holder.btnOptions.setOnClickListener { view ->
            val prefs = context.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
            val isFav = prefs.getBoolean("fav_${video.path}", false)

            val popup = PopupMenu(context, view)
            popup.menu.add(0, 1, 0, "Play Video")
            popup.menu.add(0, 2, 1, "Convert to MP3")
            popup.menu.add(0, 3, 2, "Move to Vault (Hide)")
            popup.menu.add(0, 4, 3, if (isFav) "Remove from Favorites" else "Add to Favorites")
            popup.menu.add(0, 5, 4, "Share Video")

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        playVideo(video)
                        true
                    }
                    2 -> {
                        val intent = Intent(context, Mp3ConverterActivity::class.java)
                        intent.putExtra("VIDEO_PATH", video.path)
                        intent.putExtra("VIDEO_TITLE", video.title)
                        context.startActivity(intent)
                        true
                    }
                    3 -> {
                        hideVideoToVault(video, holder.adapterPosition)
                        true
                    }
                    4 -> {
                        prefs.edit().putBoolean("fav_${video.path}", !isFav).apply()
                        Toast.makeText(
                            context,
                            if (!isFav) "Added to Favorites" else "Removed from Favorites",
                            Toast.LENGTH_SHORT
                        ).show()
                        true
                    }
                    5 -> {
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
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun hideVideoToVault(video: VideoModel, currentPosition: Int) {
        if (currentPosition == RecyclerView.NO_POSITION) return

        Toast.makeText(context, "Encrypting and Moving to Vault...", Toast.LENGTH_SHORT).show()

        thread {
            val success = VaultHelper.hideVideo(context, video)
            Handler(Looper.getMainLooper()).post {
                if (success) {
                    Toast.makeText(context, "Video hidden successfully!", Toast.LENGTH_SHORT).show()
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
