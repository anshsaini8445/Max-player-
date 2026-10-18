package com.app.mediaplayer

import android.app.AlertDialog
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File
import java.util.ArrayList
import java.util.Locale
import java.util.concurrent.TimeUnit

class AudioAdapter(
    private val context: Context,
    private val tracks: List<MusicTrack>,
    private val onDataChanged: (() -> Unit)? = null
) : RecyclerView.Adapter<AudioAdapter.AudioViewHolder>() {

    data class MusicTrack(
        val id: Long,
        val title: String,
        val artist: String,
        val path: String,
        val size: Long,
        val duration: Long,
        val albumId: Long = 0L
    )

    class AudioViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgAudioThumb: ImageView = view.findViewById(R.id.imgAudioThumb)
        val tvAudioTitle: TextView = view.findViewById(R.id.tvAudioTitle)
        val tvAudioSubtitle: TextView = view.findViewById(R.id.tvAudioSubtitle)
        val btnAudioItemMore: ImageView = view.findViewById(R.id.btnAudioItemMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudioViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_audio, parent, false)
        return AudioViewHolder(view)
    }

    override fun onBindViewHolder(holder: AudioViewHolder, position: Int) {
        val track = tracks[position]
        val prefs = context.getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
        val isFav = prefs.getBoolean("fav_audio_${track.path}", false)
        val favBadge = if (isFav) " ♥" else ""

        holder.tvAudioTitle.text = track.title
        val sizeMb = track.size / (1024.0 * 1024.0)
        holder.tvAudioSubtitle.text = String.format(
            Locale.US,
            "%s • %s • %.1f MB%s",
            track.artist,
            formatDuration(track.duration),
            sizeMb,
            favBadge
        )

        // Load Custom Saved Song Photo OR Embedded Album Art at the start of each song item
        val customPhotoUri = prefs.getString("custom_art_${track.path}", null)
        val albumArtUri = when {
            !customPhotoUri.isNullOrEmpty() -> Uri.parse(customPhotoUri)
            track.albumId > 0L -> ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                track.albumId
            )
            else -> null
        }

        if (albumArtUri != null) {
            holder.imgAudioThumb.setPadding(0, 0, 0, 0)
            holder.imgAudioThumb.clearColorFilter()
            Glide.with(context)
                .load(albumArtUri)
                .placeholder(android.R.drawable.ic_lock_silent_mode_off)
                .error(android.R.drawable.ic_lock_silent_mode_off)
                .centerCrop()
                .into(holder.imgAudioThumb)
        } else {
            val pad = (10 * context.resources.displayMetrics.density).toInt()
            holder.imgAudioThumb.setPadding(pad, pad, pad, pad)
            holder.imgAudioThumb.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
            holder.imgAudioThumb.setColorFilter(Color.parseColor("#00FFFF"))
        }

        holder.itemView.setOnClickListener {
            openAudioPlayer(position)
        }

        // High-Contrast Crystal-Clear Custom 3-Dots Dialog for each Song Item
        holder.btnAudioItemMore.setOnClickListener {
            val currentFav = prefs.getBoolean("fav_audio_${track.path}", false)
            val menuItems = arrayOf(
                "▶ Play Song Now",
                "➕ Add to Playlist",
                if (currentFav) "💔 Remove from Favorites" else "❤️ Add to Favorites",
                "📤 Share Audio File",
                "ℹ️ Song Information"
            )

            MusicFragment.showBrightStyledMenuDialog(context, track.title, menuItems) { which ->
                when (which) {
                    0 -> openAudioPlayer(position)
                    1 -> {
                        showAddToPlaylistDialog(context, track.path, track.title) {
                            onDataChanged?.invoke()
                        }
                    }
                    2 -> {
                        prefs.edit().putBoolean("fav_audio_${track.path}", !currentFav).apply()
                        notifyItemChanged(position)
                        onDataChanged?.invoke()
                        Toast.makeText(
                            context,
                            if (!currentFav) "Added to Favorites" else "Removed from Favorites",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    3 -> {
                        try {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/*"
                                putExtra(Intent.EXTRA_STREAM, Uri.parse(track.path))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Audio"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Unable to share song", Toast.LENGTH_SHORT).show()
                        }
                    }
                    4 -> {
                        AlertDialog.Builder(context)
                            .setTitle("Song Details")
                            .setMessage(
                                "Title: ${track.title}\n\n" +
                                    "Singer / Artist: ${track.artist}\n\n" +
                                    "Duration: ${formatDuration(track.duration)}\n\n" +
                                    "Size: ${String.format(Locale.US, "%.2f MB", sizeMb)}\n\n" +
                                    "Location: ${track.path}"
                            )
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
        }
    }

    private fun openAudioPlayer(pos: Int) {
        val safePos = pos.coerceIn(0, tracks.size - 1)
        val chosen = tracks[safePos]
        val paths = ArrayList<String>(tracks.size)
        val titles = ArrayList<String>(tracks.size)
        val artists = ArrayList<String>(tracks.size)
        val albumIds = ArrayList<String>(tracks.size)

        for (item in tracks) {
            paths.add(item.path)
            titles.add(item.title)
            artists.add(item.artist)
            albumIds.add(item.albumId.toString())
        }

        val intent = Intent(context, AudioPlayerActivity::class.java).apply {
            putExtra("AUDIO_PATH", chosen.path)
            putExtra("AUDIO_TITLE", chosen.title)
            putExtra("AUDIO_ARTIST", chosen.artist)
            putExtra("AUDIO_DURATION", chosen.duration)
            putExtra("AUDIO_ID", chosen.id)
            putExtra("AUDIO_ALBUM_ID", chosen.albumId)
            putExtra("AUDIO_POSITION", safePos)
            putStringArrayListExtra("AUDIO_LIST_PATHS", paths)
            putStringArrayListExtra("AUDIO_LIST_TITLES", titles)
            putStringArrayListExtra("AUDIO_LIST_ARTISTS", artists)
            putStringArrayListExtra("AUDIO_LIST_ALBUM_IDS", albumIds)
        }
        context.startActivity(intent)
    }

    override fun getItemCount(): Int = tracks.size

    private fun formatDuration(durationMillis: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis.coerceAtLeast(0L))
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    companion object {
        fun getAllPlaylists(context: Context): Set<String> {
            val prefs = context.getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
            return prefs.getStringSet("custom_audio_playlists", emptySet()) ?: emptySet()
        }

        fun savePlaylistName(context: Context, name: String) {
            val prefs = context.getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
            val current = getAllPlaylists(context).toMutableSet()
            current.add(name)
            prefs.edit().putStringSet("custom_audio_playlists", current).apply()
        }

        fun getPlaylistSongs(context: Context, playlistName: String): Set<String> {
            val prefs = context.getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
            return prefs.getStringSet("playlist_songs_$playlistName", emptySet()) ?: emptySet()
        }

        fun addSongToPlaylist(context: Context, playlistName: String, songPath: String) {
            savePlaylistName(context, playlistName)
            val prefs = context.getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
            val songs = getPlaylistSongs(context, playlistName).toMutableSet()
            songs.add(songPath)
            prefs.edit().putStringSet("playlist_songs_$playlistName", songs).apply()
        }

        fun showAddToPlaylistDialog(
            context: Context,
            songPath: String,
            songTitle: String,
            onAdded: (() -> Unit)? = null
        ) {
            val existing = getAllPlaylists(context).toList()
            val options = mutableListOf("+ Create New Playlist")
            options.addAll(existing)

            MusicFragment.showBrightStyledMenuDialog(
                context,
                "Add '$songTitle' to Playlist",
                options.toTypedArray()
            ) { which ->
                if (which == 0) {
                    val input = EditText(context).apply {
                        hint = "Playlist Name (e.g. Favorites, Driving)"
                        setHintTextColor(Color.parseColor("#888899"))
                        setTextColor(Color.WHITE)
                        setPadding(45, 35, 45, 35)
                    }
                    AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Create & Add to Playlist")
                        .setView(input)
                        .setPositiveButton("Save") { _, _ ->
                            val name = input.text.toString().trim()
                            if (name.isNotEmpty()) {
                                addSongToPlaylist(context, name, songPath)
                                onAdded?.invoke()
                                Toast.makeText(context, "Added to '$name'", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    val chosenPlaylist = existing[which - 1]
                    addSongToPlaylist(context, chosenPlaylist, songPath)
                    onAdded?.invoke()
                    Toast.makeText(context, "Added to '$chosenPlaylist'", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
