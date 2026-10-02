package com.app.mediaplayer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AudioAdapter(private val audioList: List<Audio>) : RecyclerView.Adapter<AudioAdapter.AudioViewHolder>() {
    class AudioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivAlbumArt: ImageView = itemView.findViewById(R.id.ivAlbumArt)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvArtist: TextView = itemView.findViewById(R.id.tvArtist)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudioViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_audio, parent, false)
        return AudioViewHolder(view)
    }
    override fun onBindViewHolder(holder: AudioViewHolder, position: Int) {
        val audio = audioList[position]
        holder.tvTitle.text = audio.title
        holder.tvArtist.text = audio.artist
        // No click listener for playing yet
    }
    override fun getItemCount() = audioList.size
}