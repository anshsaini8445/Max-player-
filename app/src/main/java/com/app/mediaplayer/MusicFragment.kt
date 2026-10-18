package com.app.mediaplayer

import android.Manifest
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.ArrayList
import java.util.Locale
import kotlin.concurrent.thread

class MusicFragment : Fragment() {

    private lateinit var tvMusicTabSongs: TextView
    private lateinit var tvMusicTabPlaylists: TextView
    private lateinit var tvMusicTabArtists: TextView
    private lateinit var tvMusicTabFavorites: TextView
    private lateinit var btnMusicMoreMenu: ImageView

    private lateinit var btnShufflePlayAll: LinearLayout
    private lateinit var tvShuffleAllCount: TextView
    private lateinit var btnNewPlaylist: TextView

    private lateinit var rvMusicTracks: RecyclerView
    private lateinit var llMusicEmptyState: LinearLayout
    private lateinit var tvMusicEmptyTitle: TextView
    private lateinit var tvMusicEmptyDesc: TextView

    private lateinit var cardMiniAudioPlayer: CardView
    private lateinit var tvMiniPlayerTitle: TextView
    private lateinit var tvMiniPlayerArtist: TextView
    private lateinit var btnMiniPlayOpen: ImageView

    private val allTracksList = mutableListOf<AudioAdapter.MusicTrack>()
    private val displayedTracksList = mutableListOf<AudioAdapter.MusicTrack>()
    private lateinit var audioAdapter: AudioAdapter

    private var currentTab = 0 // 0: Songs, 1: Playlists, 2: Artists, 3: Favorites
    private var sortMode = 0
    private var selectedPlaylistFilter: String? = null
    private var isScanning = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_music, container, false)

        tvMusicTabSongs = view.findViewById(R.id.tvMusicTabSongs)
        tvMusicTabPlaylists = view.findViewById(R.id.tvMusicTabPlaylists)
        tvMusicTabArtists = view.findViewById(R.id.tvMusicTabArtists)
        tvMusicTabFavorites = view.findViewById(R.id.tvMusicTabFavorites)
        btnMusicMoreMenu = view.findViewById(R.id.btnMusicMoreMenu)

        btnShufflePlayAll = view.findViewById(R.id.btnShufflePlayAll)
        tvShuffleAllCount = view.findViewById(R.id.tvShuffleAllCount)
        btnNewPlaylist = view.findViewById(R.id.btnNewPlaylist)

        rvMusicTracks = view.findViewById(R.id.rvMusicTracks)
        llMusicEmptyState = view.findViewById(R.id.llMusicEmptyState)
        tvMusicEmptyTitle = view.findViewById(R.id.tvMusicEmptyTitle)
        tvMusicEmptyDesc = view.findViewById(R.id.tvMusicEmptyDesc)

        cardMiniAudioPlayer = view.findViewById(R.id.cardMiniAudioPlayer)
        tvMiniPlayerTitle = view.findViewById(R.id.tvMiniPlayerTitle)
        tvMiniPlayerArtist = view.findViewById(R.id.tvMiniPlayerArtist)
        btnMiniPlayOpen = view.findViewById(R.id.btnMiniPlayOpen)

        rvMusicTracks.layoutManager = LinearLayoutManager(requireContext())
        audioAdapter = AudioAdapter(requireContext(), displayedTracksList) {
            refreshMusicTabsUI()
        }
        rvMusicTracks.adapter = audioAdapter

        setupClicks()
        checkAudioPermissionAndLoad()

        return view
    }

    override fun onResume() {
        super.onResume()
        updateMiniPlayerBar()
        loadAllAudioFast()
    }

    private fun checkAudioPermissionAndLoad() {
        val ctx = context ?: return
        val hasPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasPerm) {
            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            requestPermissions(perms, 3001)
        }
        loadAllAudioFast()
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        loadAllAudioFast()
    }

    private fun setupClicks() {
        val tabs = listOf(tvMusicTabSongs, tvMusicTabPlaylists, tvMusicTabArtists, tvMusicTabFavorites)

        tabs.forEachIndexed { index, tabView ->
            tabView.setOnClickListener {
                currentTab = index
                selectedPlaylistFilter = null
                tabs.forEachIndexed { i, tv ->
                    tv.setTextColor(
                        if (i == currentTab) Color.parseColor("#00FFFF") else Color.parseColor("#888888")
                    )
                }
                if (currentTab == 1) {
                    showPlaylistPickerOrSongs()
                } else {
                    refreshMusicTabsUI()
                }
            }
        }

        btnShufflePlayAll.setOnClickListener {
            if (displayedTracksList.isEmpty()) {
                Toast.makeText(requireContext(), "No songs available to play", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val shuffled = displayedTracksList.shuffled()
            launchAudioPlayerWithList(shuffled, 0, true)
        }

        btnNewPlaylist.setOnClickListener {
            showCreateNewPlaylistDialog()
        }

        btnMusicMoreMenu.setOnClickListener { anchor ->
            val ctx = context ?: return@setOnClickListener
            val popup = PopupMenu(ctx, anchor)
            popup.menu.add(0, 1, 0, "Sort: Newest Added")
            popup.menu.add(0, 2, 1, "Sort: Title (A to Z)")
            popup.menu.add(0, 3, 2, "Sort: Artist Name")
            popup.menu.add(0, 4, 3, "Sort: Longest Duration")
            popup.menu.add(0, 5, 4, "Create New Playlist")
            popup.menu.add(0, 6, 5, "Rescan Music Library")

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1, 2, 3, 4 -> {
                        sortMode = item.itemId - 1
                        refreshMusicTabsUI()
                        true
                    }
                    5 -> {
                        showCreateNewPlaylistDialog()
                        true
                    }
                    6 -> {
                        loadAllAudioFast()
                        Toast.makeText(ctx, "Music library refreshed", Toast.LENGTH_SHORT).show()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        val openLastPlayed = View.OnClickListener {
            val ctx = context ?: return@OnClickListener
            val prefs = ctx.getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
            val lastPath = prefs.getString("last_audio_path", null) ?: return@OnClickListener
            val targetIdx = allTracksList.indexOfFirst { it.path == lastPath }.coerceAtLeast(0)
            if (allTracksList.isNotEmpty()) {
                launchAudioPlayerWithList(allTracksList, targetIdx, false)
            }
        }
        cardMiniAudioPlayer.setOnClickListener(openLastPlayed)
        btnMiniPlayOpen.setOnClickListener(openLastPlayed)
    }

    private fun showCreateNewPlaylistDialog() {
        val ctx = context ?: return
        val input = EditText(ctx).apply {
            hint = "Enter playlist name (e.g. Gym, Bass Boost)"
            setTextColor(Color.BLACK)
            setPadding(40, 30, 40, 30)
        }
        AlertDialog.Builder(ctx)
            .setTitle("Create New Playlist")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    AudioAdapter.savePlaylistName(ctx, name)
                    Toast.makeText(ctx, "Playlist '$name' created!", Toast.LENGTH_SHORT).show()
                    if (currentTab == 1) {
                        showPlaylistPickerOrSongs()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPlaylistPickerOrSongs() {
        val ctx = context ?: return
        val playlists = AudioAdapter.getAllPlaylists(ctx).toList()
        if (playlists.isEmpty()) {
            displayedTracksList.clear()
            audioAdapter.notifyDataSetChanged()
            rvMusicTracks.visibility = View.GONE
            llMusicEmptyState.visibility = View.VISIBLE
            tvMusicEmptyTitle.text = "No Custom Playlists Yet"
            tvMusicEmptyDesc.text = "Tap '+ New Playlist' above or use the 3-dots menu on any song to add to a playlist."
            return
        }

        val items = playlists.map { plName ->
            val count = AudioAdapter.getPlaylistSongs(ctx, plName).size
            "$plName ($count songs)"
        }.toTypedArray()

        AlertDialog.Builder(ctx)
            .setTitle("Select Playlist to Open")
            .setItems(items) { _, which ->
                selectedPlaylistFilter = playlists[which]
                refreshMusicTabsUI()
            }
            .setPositiveButton("+ New Playlist") { _, _ ->
                showCreateNewPlaylistDialog()
            }
            .setNegativeButton("Show All Playlist Songs") { _, _ ->
                selectedPlaylistFilter = null
                refreshMusicTabsUI()
            }
            .show()
    }

    private fun updateMiniPlayerBar() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
        val lastTitle = prefs.getString("last_audio_title", null)
        val lastArtist = prefs.getString("last_audio_artist", null)
        if (!lastTitle.isNullOrEmpty()) {
            cardMiniAudioPlayer.visibility = View.VISIBLE
            tvMiniPlayerTitle.text = lastTitle
            tvMiniPlayerArtist.text = lastArtist ?: "Unknown Artist"
        } else {
            cardMiniAudioPlayer.visibility = View.GONE
        }
    }

    private fun launchAudioPlayerWithList(
        list: List<AudioAdapter.MusicTrack>,
        startIndex: Int,
        shuffleStart: Boolean
    ) {
        val ctx = context ?: return
        val chosen = list[startIndex.coerceIn(0, list.size - 1)]
        val paths = ArrayList<String>(list.size)
        val titles = ArrayList<String>(list.size)
        val artists = ArrayList<String>(list.size)
        list.forEach {
            paths.add(it.path)
            titles.add(it.title)
            artists.add(it.artist)
        }
        val intent = Intent(ctx, AudioPlayerActivity::class.java).apply {
            putExtra("AUDIO_PATH", chosen.path)
            putExtra("AUDIO_TITLE", chosen.title)
            putExtra("AUDIO_ARTIST", chosen.artist)
            putExtra("AUDIO_DURATION", chosen.duration)
            putExtra("AUDIO_ID", chosen.id)
            putExtra("AUDIO_POSITION", startIndex)
            putExtra("AUDIO_SHUFFLE_START", shuffleStart)
            putStringArrayListExtra("AUDIO_LIST_PATHS", paths)
            putStringArrayListExtra("AUDIO_LIST_TITLES", titles)
            putStringArrayListExtra("AUDIO_LIST_ARTISTS", artists)
        }
        ctx.startActivity(intent)
    }

    private fun refreshMusicTabsUI() {
        if (!isAdded || context == null) return
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)

        val baseList = when (currentTab) {
            1 -> {
                val plFilter = selectedPlaylistFilter
                if (plFilter != null) {
                    val savedPaths = AudioAdapter.getPlaylistSongs(ctx, plFilter)
                    allTracksList.filter { savedPaths.contains(it.path) }
                } else {
                    val allPlPaths = mutableSetOf<String>()
                    AudioAdapter.getAllPlaylists(ctx).forEach { pl ->
                        allPlPaths.addAll(AudioAdapter.getPlaylistSongs(ctx, pl))
                    }
                    allTracksList.filter { allPlPaths.contains(it.path) }
                }
            }
            2 -> allTracksList.sortedBy { it.artist.lowercase(Locale.getDefault()) }
            3 -> allTracksList.filter { prefs.getBoolean("fav_audio_${it.path}", false) }
            else -> allTracksList
        }

        val sorted = if (currentTab == 2) {
            baseList
        } else {
            when (sortMode) {
                1 -> baseList.sortedBy { it.title.lowercase(Locale.getDefault()) }
                2 -> baseList.sortedBy { it.artist.lowercase(Locale.getDefault()) }
                3 -> baseList.sortedByDescending { it.duration }
                else -> baseList
            }
        }

        displayedTracksList.clear()
        displayedTracksList.addAll(sorted)
        audioAdapter.notifyDataSetChanged()

        tvShuffleAllCount.text = "Shuffle All (${displayedTracksList.size})"

        if (displayedTracksList.isEmpty()) {
            rvMusicTracks.visibility = View.GONE
            llMusicEmptyState.visibility = View.VISIBLE
            when (currentTab) {
                1 -> {
                    tvMusicEmptyTitle.text = "Playlist is Empty"
                    tvMusicEmptyDesc.text = "Tap the 3-dots on any song and choose 'Add to Playlist'."
                }
                3 -> {
                    tvMusicEmptyTitle.text = "No Favorite Songs Yet"
                    tvMusicEmptyDesc.text = "Tap the heart icon in the player or 3-dots on any song to mark as Favorite."
                }
                else -> {
                    tvMusicEmptyTitle.text = "No Audio Files Found"
                    tvMusicEmptyDesc.text = "Allow audio storage permission to load your songs."
                }
            }
        } else {
            llMusicEmptyState.visibility = View.GONE
            rvMusicTracks.visibility = View.VISIBLE
        }
    }

    private fun loadAllAudioFast() {
        if (isScanning) return
        isScanning = true

        thread {
            try {
                val loaded = mutableListOf<AudioAdapter.MusicTrack>()
                val resolver = context?.contentResolver
                if (resolver != null) {
                    val proj = arrayOf(
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.DISPLAY_NAME,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.DATA,
                        MediaStore.Audio.Media.SIZE,
                        MediaStore.Audio.Media.DURATION
                    )
                    val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"
                    resolver.query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        proj,
                        null,
                        null,
                        sortOrder
                    )?.use { cursor ->
                        val idC = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                        val titleC = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                        val nameC = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                        val artistC = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                        val dataC = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                        val sizeC = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                        val durC = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)

                        while (cursor.moveToNext()) {
                            val id = if (idC != -1) cursor.getLong(idC) else 0L
                            val rawPath = if (dataC != -1) cursor.getString(dataC) else null
                            val path = if (!rawPath.isNullOrEmpty()) {
                                rawPath
                            } else {
                                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
                            }
                            val rawTitle = if (titleC != -1) cursor.getString(titleC) else null
                            val displayName = if (nameC != -1) cursor.getString(nameC) else null
                            val title = when {
                                !rawTitle.isNullOrEmpty() -> rawTitle
                                !displayName.isNullOrEmpty() -> displayName
                                else -> File(path).name
                            }
                            val rawArtist = if (artistC != -1) cursor.getString(artistC) else null
                            val artist = if (rawArtist.isNullOrEmpty() || rawArtist == "<unknown>") {
                                "Unknown"
                            } else {
                                rawArtist
                            }
                            val size = if (sizeC != -1) cursor.getLong(sizeC).coerceAtLeast(0L) else 0L
                            val duration = if (durC != -1) cursor.getLong(durC).coerceAtLeast(0L) else 0L

                            if (duration >= 1000L || size > 10240L) {
                                loaded.add(
                                    AudioAdapter.MusicTrack(
                                        id = id,
                                        title = title,
                                        artist = artist,
                                        path = path,
                                        size = size,
                                        duration = duration
                                    )
                                )
                            }
                        }
                    }
                }

                activity?.runOnUiThread {
                    isScanning = false
                    if (!isAdded || context == null) return@runOnUiThread
                    allTracksList.clear()
                    allTracksList.addAll(loaded)
                    updateMiniPlayerBar()
                    refreshMusicTabsUI()
                }
            } catch (e: Exception) {
                isScanning = false
                e.printStackTrace()
            }
        }
    }
}
