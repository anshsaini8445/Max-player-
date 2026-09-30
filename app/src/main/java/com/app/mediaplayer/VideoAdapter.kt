package com.app.mediaplayer
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class VideoAdapter(private val videoList: List<Video>) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {
    class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false)
        return VideoViewHolder(view)
    }
    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val video = videoList[position]
        holder.tvTitle.text = video.title
        val minutes = (video.duration / 1000) / 60
        val seconds = (video.duration / 1000) % 60
        holder.tvDuration.text = String.format("%02d:%02d", minutes, seconds)
        
        holder.itemView.setOnClickListener {
            val intent = Intent(it.context, PlayerActivity::class.java)
            intent.putExtra("VIDEO_PATH", video.path)
            it.context.startActivity(intent)
        }
    }
    override fun getItemCount() = videoList.size
}
