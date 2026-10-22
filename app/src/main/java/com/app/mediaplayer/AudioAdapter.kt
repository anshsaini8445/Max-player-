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
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import java.io.File
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
        // Apply InShot Music Player style bottom-to-top smooth expand animation
        InShotScrollAnimator.animateRowOnBind(holder.itemView)

        val song = audioList[position]
        val audioPrefs = context.getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
        val themePrefs = context.getSharedPreferences("MAX_THEME_PREFS", Context.MODE_PRIVATE)
        val accentHex = themePrefs.getString("app_accent_color", "#5CE1E6") ?: "#5CE1E6"
        val accentColor = try { Color.parseColor(accentHex) } catch (_: Exception) { Color.parseColor("#5CE1E6") }

        val isFav = audioPrefs.getBoolean("fav_audio_${song.path}", false)
        val favTag = if (isFav) " *" else ""

        holder.tvAudioTitle.text = song.title
        holder.tvAudioTitle.setTextColor(Color.WHITE)

        holder.tvAudioArtistDuration.text =
            "${song.artist} | ${formatDuration(song.duration)} | ${formatSize(song.size)}$favTag"
        holder.tvAudioArtistDuration.setTextColor(accentColor)
        holder.btnAudioMore.setColorFilter(Color.WHITE)

        val customArtUri = audioPrefs.getString("custom_art_${song.path}", null)
        holder.imgAudioArt.setPadding(0, 0, 0, 0)
        holder.imgAudioArt.clearColorFilter()

        val artModel: Any = when {
            !customArtUri.isNullOrEmpty() -> Uri.parse(customArtUri)
            song.id > 0L -> ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                song.id
            )
            else -> File(song.path)
        }

        Glide.with(context)
            .load(artModel)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(android.R.drawable.ic_lock_silent_mode_off)
            .error(android.R.drawable.ic_lock_silent_mode_off)
            .centerCrop()
            .into(holder.imgAudioArt)

        holder.itemView.setOnClickListener {
            launchAudioPlayer(holder.adapterPosition)
        }

        holder.btnAudioMore.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos !in audioList.indices) return@setOnClickListener
            val item = audioList[pos]
            val curFav = audioPrefs.getBoolean("fav_audio_${item.path}", false)

            val options = arrayOf(
                "Play Song Now",
                "Play Next (After Current Song)",
                "Add to Playing Queue",
                if (curFav) "Remove from Favorites" else "Add to Favorites *",
                "Open Sound & Pitch Lab (Lofi / Slowed + Reverb)",
                "Open 5-Band Dolby Equalizer",
                "Lock Song into Secret Privacy Vault",
                "Share Audio File",
                "Song Details & Path",
                "Delete Song Permanently (From Phone)"
            )

            VideoFragment.showBrightVideoMenuDialog(context, item.title, options) { which ->
                when (which) {
                    0 -> launchAudioPlayer(pos)
                    1 -> {
                        enqueueSongBroadcast(item, playNextImmediate = true)
                        Toast.makeText(context, "Playing Next: ${item.title}", Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        enqueueSongBroadcast(item, playNextImmediate = false)
                        Toast.makeText(context, "Added to Queue: ${item.title}", Toast.LENGTH_SHORT).show()
                    }
                    3 -> {
                        audioPrefs.edit().putBoolean("fav_audio_${item.path}", !curFav).apply()
                        notifyItemChanged(pos)
                        Toast.makeText(
                            context,
                            if (!curFav) "Added to Favorites" else "Removed from Favorites",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    4 -> AudioPitchHelper.showSoundAndPitchLabDialog(context, null)
                    5 -> EqualizerHelper.showEqualizerDialog(context, AudioPlayerActivity.getSharedAudioSessionId())
                    6 -> FakeVaultHelper.lockMediaIntoVault(context, item.path, item.title)
                    7 -> {
                        try {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/*"
                                putExtra(Intent.EXTRA_STREAM, Uri.parse(item.path))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Audio"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Unable to share song", Toast.LENGTH_SHORT).show()
                        }
                    }
                    8 -> {
                        AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                            .setTitle("Audio Properties")
                            .setMessage(
                                "Title: ${item.title}\n\n" +
                                    "Artist: ${item.artist}\n\n" +
                                    "Album: ${item.album}\n\n" +
                                    "Duration: ${formatDuration(item.duration)}\n\n" +
                                    "Size: ${formatSize(item.size)}\n\n" +
                                    "Path: ${item.path}"
                            )
                            .setPositiveButton("OK", null)
                            .show()
                    }
                    9 -> {
                        PermanentDeleteHelper.confirmAndDeletePermanently(
                            context = context,
                            title = item.title,
                            path = item.path,
                            mediaId = item.id,
                            isVideo = false
                        ) {
                            if (pos in audioList.indices) {
                                audioList.removeAt(pos)
                                notifyItemRemoved(pos)
                                notifyItemRangeChanged(pos, audioList.size)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun enqueueSongBroadcast(song: AudioTrackModel, playNextImmediate: Boolean) {
        val intent = Intent("com.app.mediaplayer.ACTION_QUEUE_ENQUEUE").apply {
            setPackage(context.packageName)
            putExtra("SONG_PATH", song.path)
            putExtra("SONG_TITLE", song.title)
            putExtra("SONG_ARTIST", song.artist)
            putExtra("PLAY_NEXT", playNextImmediate)
        }
        context.sendBroadcast(intent)
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
