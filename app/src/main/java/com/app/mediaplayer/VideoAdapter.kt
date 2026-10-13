package com.app.mediaplayer

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

class VideoAdapter(private val context: Context, private val videoList: List<VideoModel>) :
    RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

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
        val extension = File(video.path).extension.lowercase(Locale.getDefault())
        holder.tvSizeFormat.text = String.format(Locale.US, "%.2f MB • (%s)", sizeInMb, extension)

        Glide.with(context)
            .load(video.path)
            .placeholder(android.R.color.darker_gray)
            .centerCrop()
            .into(holder.imgThumbnail)

        // Single Click -> Play Video
        holder.itemView.setOnClickListener {
            playVideo(video.path)
        }

        // Options Button (3 Dots) -> Popup Menu
        holder.btnOptions.setOnClickListener { view ->
            val popup = PopupMenu(context, view)
            popup.menu.add(0, 1, 0, "Play Video")
            popup.menu.add(0, 2, 0, "Convert to MP3")
            
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        playVideo(video.path)
                        true
                    }
                    2 -> {
                        val intent = Intent(context, Mp3ConverterActivity::class.java)
                        intent.putExtra("VIDEO_PATH", video.path)
                        intent.putExtra("VIDEO_TITLE", video.title)
                        context.startActivity(intent)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun playVideo(path: String) {
        val intent = Intent(context, PlayerActivity::class.java)
        intent.putExtra("VIDEO_PATH", path)
        context.startActivity(intent)
    }

    override fun getItemCount(): Int = videoList.size

    private fun formatDuration(durationMillis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(durationMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis) - TimeUnit.HOURS.toMinutes(hours)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(durationMillis))
        
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}
