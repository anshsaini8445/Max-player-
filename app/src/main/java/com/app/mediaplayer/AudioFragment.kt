package com.app.mediaplayer

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.ArrayList
import java.util.Locale
import kotlin.concurrent.thread

class AudioFragment : Fragment() {

    private lateinit var rootAudioFragment: LinearLayout
    private lateinit var tvAudioTabSongs: TextView
    private lateinit var tvAudioTabFolders: TextView
    private lateinit var tvAudioTabAlbums: TextView
    private lateinit var tvAudioTabArtists: TextView
    private lateinit var tvAudioTabPlaylists: TextView

    private lateinit var btnShuffleAllAudio: TextView
    private lateinit var tvAudioTotalCount: TextView
    private lateinit var btnAudioSortMenu: ImageView
    private lateinit var rvAudioList: RecyclerView
    private lateinit var llAudioEmptyState: LinearLayout

    private val allSongsList = mutableListOf<AudioTrackModel>()
    private val displayedSongsList = mutableListOf<AudioTrackModel>()
    private lateinit var audioAdapter: AudioAdapter

    private var activeSubTab = 0
    private var sortOrderMode = 0
    private var isScanningAudio = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_audio, container, false)
        bindViews(view)
        setupClicks()
        loadAllAudioFast()
        return view
    }

    override fun onResume() {
        super.onResume()
        loadAllAudioFast()
    }

    private fun bindViews(view: View) {
        rootAudioFragment = view.findViewById(R.id.rootAudioFragment)
        tvAudioTabSongs = view.findViewById(R.id.tvAudioTabSongs)
        tvAudioTabFolders = view.findViewById(R.id.tvAudioTabFolders)
        tvAudioTabAlbums = view.findViewById(R.id.tvAudioTabAlbums)
        tvAudioTabArtists = view.findViewById(R.id.tvAudioTabArtists)
        tvAudioTabPlaylists = view.findViewById(R.id.tvAudioTabPlaylists)

        btnShuffleAllAudio = view.findViewById(R.id.btnShuffleAllAudio)
        tvAudioTotalCount = view.findViewById(R.id.tvAudioTotalCount)
        btnAudioSortMenu = view.findViewById(R.id.btnAudioSortMenu)
        rvAudioList = view.findViewById(R.id.rvAudioList)
        llAudioEmptyState = view.findViewById(R.id.llAudioEmptyState)

        rvAudioList.layoutManager = LinearLayoutManager(requireContext())
        audioAdapter = AudioAdapter(requireContext(), displayedSongsList)
        rvAudioList.adapter = audioAdapter
    }

    private fun setupClicks() {
        val tabs = listOf(
            tvAudioTabSongs,
            tvAudioTabFolders,
            tvAudioTabAlbums,
            tvAudioTabArtists,
            tvAudioTabPlaylists
        )

        tabs.forEachIndexed { index, tabView ->
            tabView.setOnClickListener {
                activeSubTab = index
                tabs.forEachIndexed { idx, tv ->
                    tv.setTextColor(
                        if (idx == activeSubTab) Color.parseColor("#00FFFF")
                        else Color.parseColor("#B0B0C0")
                    )
                }
                refreshDisplayedSongs()
            }
        }

        btnShuffleAllAudio.setOnClickListener {
            if (displayedSongsList.isEmpty()) {
                Toast.makeText(requireContext(), "No songs available to play", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val shuffled = displayedSongsList.shuffled()
            val paths = ArrayList<String>(shuffled.size)
            val titles = ArrayList<String>(shuffled.size)
            val artists = ArrayList<String>(shuffled.size)
            shuffled.forEach {
                paths.add(it.path)
                titles.add(it.title)
                artists.add(it.artist)
            }
            val intent = Intent(requireContext(), AudioPlayerActivity::class.java).apply {
                putExtra("AUDIO_PATH", paths[0])
                putExtra("AUDIO_TITLE", titles[0])
                putExtra("AUDIO_ARTIST", artists[0])
                putExtra("AUDIO_INDEX", 0)
                putStringArrayListExtra("AUDIO_LIST", paths)
                putStringArrayListExtra("AUDIO_TITLES", titles)
                putStringArrayListExtra("AUDIO_ARTISTS", artists)
            }
            startActivity(intent)
        }

        btnAudioSortMenu.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            val items = arrayOf(
                if (sortOrderMode == 0) "[Active] Sort: Newest First" else "Sort: Newest First",
                if (sortOrderMode == 1) "[Active] Sort: Title (A to Z)" else "Sort: Title (A to Z)",
                if (sortOrderMode == 2) "[Active] Sort: Artist Name" else "Sort: Artist Name",
                if (sortOrderMode == 3) "[Active] Sort: Longest Duration" else "Sort: Longest Duration",
                "Open 5-Band Dolby Equalizer"
            )
            VideoFragment.showBrightVideoMenuDialog(ctx, "Music Sort & Tools", items) { which ->
                if (which == 4) {
                    EqualizerHelper.showEqualizerDialog(ctx, 0)
                } else {
                    sortOrderMode = which
                    refreshDisplayedSongs()
                }
            }
        }
    }

    private fun loadAllAudioFast() {
        if (isScanningAudio) return
        isScanningAudio = true

        thread {
            val tempSongs = mutableListOf<AudioTrackModel>()
            try {
                val ctx = context ?: return@thread
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.SIZE,
                    MediaStore.Audio.Media.DURATION
                )
                val sort = "${MediaStore.Audio.Media.DATE_ADDED} DESC"
                ctx.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    sort
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                    val dispCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                    val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                    val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                    val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                    val durCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)

                    while (cursor.moveToNext()) {
                        val id = if (idCol != -1) cursor.getLong(idCol) else 0L
                        val rawPath = if (dataCol != -1) cursor.getString(dataCol) else null
                        val path = if (!rawPath.isNullOrEmpty()) {
                            rawPath
                        } else {
                            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
                        }
                        val name = if (dispCol != -1) cursor.getString(dispCol) else null
                        val title = name ?: (if (titleCol != -1) cursor.getString(titleCol) else null) ?: File(path).name
                        val artistRaw = if (artistCol != -1) cursor.getString(artistCol) else null
                        val artist = if (artistRaw.isNullOrEmpty() || artistRaw == "<unknown>") "Unknown Artist" else artistRaw
                        val albumRaw = if (albumCol != -1) cursor.getString(albumCol) else null
                        val album = if (albumRaw.isNullOrEmpty() || albumRaw == "<unknown>") "Music" else albumRaw
                        val size = if (sizeCol != -1) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L
                        val duration = if (durCol != -1) cursor.getLong(durCol).coerceAtLeast(0L) else 0L
                        val folder = File(path).parentFile?.name ?: "Music"

                        if (duration >= 1000L) {
                            tempSongs.add(AudioTrackModel(id, title, artist, album, path, size, duration, folder))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            activity?.runOnUiThread {
                isScanningAudio = false
                if (!isAdded || context == null) return@runOnUiThread
                allSongsList.clear()
                allSongsList.addAll(tempSongs)
                refreshDisplayedSongs()
            }
        }
    }

    private fun refreshDisplayedSongs() {
        val ctx = context ?: return
        val audioPrefs = ctx.getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)

        val baseList = when (activeSubTab) {
            1 -> allSongsList.sortedBy { it.folderName.lowercase(Locale.getDefault()) }
            2 -> allSongsList.sortedBy { it.album.lowercase(Locale.getDefault()) }
            3 -> allSongsList.sortedBy { it.artist.lowercase(Locale.getDefault()) }
            4 -> allSongsList.filter { audioPrefs.getBoolean("fav_audio_${it.path}", false) }
            else -> allSongsList
        }

        val sortedList = when (sortOrderMode) {
            1 -> baseList.sortedBy { it.title.lowercase(Locale.getDefault()) }
            2 -> baseList.sortedBy { it.artist.lowercase(Locale.getDefault()) }
            3 -> baseList.sortedByDescending { it.duration }
            else -> baseList
        }

        displayedSongsList.clear()
        displayedSongsList.addAll(sortedList)
        audioAdapter.notifyDataSetChanged()

        tvAudioTotalCount.text = "${displayedSongsList.size} Songs"
        if (displayedSongsList.isEmpty()) {
            rvAudioList.visibility = View.GONE
            llAudioEmptyState.visibility = View.VISIBLE
        } else {
            llAudioEmptyState.visibility = View.GONE
            rvAudioList.visibility = View.VISIBLE
        }
    }
}
