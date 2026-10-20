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
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.util.ArrayList
import java.util.Locale
import java.util.concurrent.TimeUnit

class AudioAdapter(
    private val context: Context,
    private val audioList: MutableList<AudioTrackModel>
) : RecyclerView.Adapter<AudioAdapter.AudioVH>() {

    class AudioVH(view: View) : RecyclerView.ViewHolder(view) {
        val imgAudioArt: ImageView = view.findViewById(R.id.imgAudioArt)
        val tvAudioTitle: TextView = view.findViewById(R.id.tvAudioTitle)
        val tvAudioArtistDuration: TextView = view.findViewById(R.id.tvAudioArtistDuration)
        val btnAudioMore: ImageView = view.findViewById(R.id.btnAudioMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudioVH {
        val view = LayoutInflater.from(context).inflate(R.layout.item_audio, parent, false)
        return AudioVH(view)
    }

    override fun onBindViewHolder(holder: AudioVH, position: Int) {
        val song = audioList[position]
        val audioPrefs = context.getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
        val isFav = audioPrefs.getBoolean("fav_audio_${song.path}", false)
        val favTag = if (isFav) " *" else ""

        holder.tvAudioTitle.text = song.title
        holder.tvAudioArtistDuration.text =
            "${song.artist} | ${formatDuration(song.duration)} | ${formatSize(song.size)}$favTag"

        val customArtUri = audioPrefs.getString("custom_art_${song.path}", null)
        if (!customArtUri.isNullOrEmpty()) {
            holder.imgAudioArt.setPadding(0, 0, 0, 0)
            holder.imgAudioArt.clearColorFilter()
            Glide.with(context)
                .load(Uri.parse(customArtUri))
                .placeholder(android.R.drawable.ic_lock_silent_mode_off)
                .centerCrop()
                .into(holder.imgAudioArt)
        } else {
            val d = (10 * context.resources.displayMetrics.density).toInt()
            holder.imgAudioArt.setPadding(d, d, d, d)
            holder.imgAudioArt.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
            holder.imgAudioArt.setColorFilter(Color.parseColor("#00FFFF"))
        }

        holder.itemView.setOnClickListener {
            launchAudioPlayer(position)
        }

        holder.btnAudioMore.setOnClickListener {
            val curFav = audioPrefs.getBoolean("fav_audio_${song.path}", false)
            val options = arrayOf(
                "Play Song",
                if (curFav) "Remove from Favorites" else "Add to Favorites",
                "Open 5-Band Equalizer",
                "Share Audio File",
                "Song Details & Path"
            )
            VideoFragment.showBrightVideoMenuDialog(context, song.title, options) { which ->
                when (which) {
                    0 -> launchAudioPlayer(position)
                    1 -> {
                        audioPrefs.edit().putBoolean("fav_audio_${song.path}", !curFav).apply()
                        notifyItemChanged(holder.adapterPosition)
                        Toast.makeText(
                            context,
                            if (!curFav) "Added to Favorites" else "Removed from Favorites",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    2 -> EqualizerHelper.showEqualizerDialog(context, 0)
                    3 -> {
                        try {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/*"
                                putExtra(Intent.EXTRA_STREAM, Uri.parse(song.path))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Audio"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Unable to share song", Toast.LENGTH_SHORT).show()
                        }
                    }
                    4 -> {
                        AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                            .setTitle("Audio Properties")
                            .setMessage(
                                "Title: ${song.title}\n\n" +
                                    "Artist: ${song.artist}\n\n" +
                                    "Album: ${song.album}\n\n" +
                                    "Duration: ${formatDuration(song.duration)}\n\n" +
                                    "Size: ${formatSize(song.size)}\n\n" +
                                    "Path: ${song.path}"
                            )
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
        }
    }

    private fun launchAudioPlayer(selectedIndex: Int) {
        if (selectedIndex !in audioList.indices) return
        val song = audioList[selectedIndex]
        val allPaths = ArrayList<String>(audioList.size)
        val allTitles = ArrayList<String>(audioList.size)
        val allArtists = ArrayList<String>(audioList.size)

        for (item in audioList) {
            allPaths.add(item.path)
            allTitles.add(item.title)
            allArtists.add(item.artist)
        }

        val intent = Intent(context, AudioPlayerActivity::class.java).apply {
            putExtra("AUDIO_PATH", song.path)
            putExtra("AUDIO_TITLE", song.title)
            putExtra("AUDIO_ARTIST", song.artist)
            putExtra("AUDIO_INDEX", selectedIndex)
            putStringArrayListExtra("AUDIO_LIST", allPaths)
            putStringArrayListExtra("AUDIO_TITLES", allTitles)
            putStringArrayListExtra("AUDIO_ARTISTS", allArtists)
        }
        context.startActivity(intent)
    }

    override fun getItemCount(): Int = audioList.size

    private fun formatSize(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f MB", mb)
    }

    private fun formatDuration(millis: Long): String {
        val safe = millis.coerceAtLeast(0L)
        val mins = TimeUnit.MILLISECONDS.toMinutes(safe)
        val secs = TimeUnit.MILLISECONDS.toSeconds(safe) % 60
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}
