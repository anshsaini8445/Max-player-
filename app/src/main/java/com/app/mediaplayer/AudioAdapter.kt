package com.app.mediaplayer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.ArrayList
import java.util.Locale
import java.util.concurrent.TimeUnit

data class AudioItemModel(
    val id: Long,
    val title: String,
    val artist: String,
    val path: String,
    val size: Long,
    val duration: Long,
    val folderName: String
)

class AudioAdapter(
    private val context: Context,
    audioItems: List<AudioItemModel>
) : RecyclerView.Adapter<AudioAdapter.AudioViewHolder>() {

    private val audioList: MutableList<AudioItemModel> =
        if (audioItems is MutableList<AudioItemModel>) audioItems else audioItems.toMutableList()

    class AudioViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgAudioArt: ImageView = view.findViewById(R.id.imgAudioArt)
        val tvAudioTitle: TextView = view.findViewById(R.id.tvAudioTitle)
        val tvAudioArtist: TextView = view.findViewById(R.id.tvAudioArtist)
        val tvAudioDuration: TextView = view.findViewById(R.id.tvAudioDuration)
        val btnAudioMore: ImageView = view.findViewById(R.id.btnAudioMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudioViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_audio, parent, false)
        return AudioViewHolder(view)
    }

    override fun onBindViewHolder(holder: AudioViewHolder, position: Int) {
        val item = audioList[position]

        holder.tvAudioTitle.text = item.title
        val sizeMb = item.size / (1024.0 * 1024.0)
        holder.tvAudioArtist.text = String.format(Locale.US, "%s • %.2f MB", item.artist, sizeMb)
        holder.tvAudioDuration.text = formatDuration(item.duration)

        holder.itemView.setOnClickListener {
            openAudioPlayer(position)
        }

        holder.btnAudioMore.setOnClickListener { view ->
            val prefs = context.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
            val isFav = prefs.getBoolean("fav_audio_${item.path}", false)

            val popup = PopupMenu(context, view)
            popup.menu.add(0, 1, 0, "Play Song")
            popup.menu.add(0, 2, 1, if (isFav) "Remove from Favorites" else "Add to Favorites")
            popup.menu.add(0, 3, 2, "Share Audio")
            popup.menu.add(0, 4, 3, "File Details")

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    1 -> {
                        openAudioPlayer(position)
                        true
                    }
                    2 -> {
                        prefs.edit().putBoolean("fav_audio_${item.path}", !isFav).apply()
                        Toast.makeText(
                            context,
                            if (!isFav) "Added to Favorites" else "Removed from Favorites",
                            Toast.LENGTH_SHORT
                        ).show()
                        true
                    }
                    3 -> {
                        try {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/*"
                                putExtra(Intent.EXTRA_STREAM, Uri.parse(item.path))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Audio"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Unable to share audio", Toast.LENGTH_SHORT).show()
                        }
                        true
                    }
                    4 -> {
                        Toast.makeText(context, item.path, Toast.LENGTH_LONG).show()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun openAudioPlayer(selectedIndex: Int) {
        if (selectedIndex !in audioList.indices) return
        val selected = audioList[selectedIndex]
        val paths = ArrayList<String>(audioList.size)
        val titles = ArrayList<String>(audioList.size)
        val artists = ArrayList<String>(audioList.size)

        for (song in audioList) {
            paths.add(song.path)
            titles.add(song.title)
            artists.add(song.artist)
        }

        val intent = Intent(context, AudioPlayerActivity::class.java).apply {
            putExtra("AUDIO_PATH", selected.path)
            putExtra("AUDIO_TITLE", selected.title)
            putExtra("AUDIO_ARTIST", selected.artist)
            putExtra("AUDIO_INDEX", selectedIndex)
            putStringArrayListExtra("AUDIO_LIST", paths)
            putStringArrayListExtra("AUDIO_TITLES", titles)
            putStringArrayListExtra("AUDIO_ARTISTS", artists)
        }
        context.startActivity(intent)
    }

    override fun getItemCount(): Int = audioList.size

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
