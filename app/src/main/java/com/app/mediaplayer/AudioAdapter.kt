package com.app.mediaplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class AudioAdapter(
    private val audioList: List<Audio>,
    private val onAudioClick: (Audio) -> Unit = {}
) : RecyclerView.Adapter<AudioAdapter.AudioViewHolder>() {

    class AudioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivAlbumArt: ImageView = itemView.findViewById(R.id.ivAlbumArt)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvArtist: TextView = itemView.findViewById(R.id.tvArtist)
        val tvDuration: TextView? = itemView.findViewById(R.id.tvDuration)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudioViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_audio, parent, false)
        return AudioViewHolder(view)
    }

    override fun onBindViewHolder(holder: AudioViewHolder, position: Int) {
        val audio = audioList[position]
        holder.tvTitle.text = audio.title
        holder.tvArtist.text = audio.artist

        if (holder.tvDuration != null && audio.duration > 0) {
            val minutes = (audio.duration / 1000) / 60
            val seconds = (audio.duration / 1000) % 60
            holder.tvDuration.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }

        holder.itemView.setOnClickListener {
            onAudioClick(audio)
        }
    }

    override fun getItemCount() = audioList.size
}