package com.app.mediaplayer

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.ArrayList
import java.util.Locale
import kotlin.concurrent.thread

class MusicFragment : Fragment() {

    private lateinit var rvMusicList: RecyclerView
    private lateinit var llMusicEmptyState: LinearLayout
    private lateinit var tvMusicTabSongs: TextView
    private lateinit var tvMusicTabFolders: TextView
    private lateinit var tvMusicTabPlaylists: TextView
    private lateinit var btnToggleMusicLayout: ImageView
    private lateinit var llMusicQuickActions: LinearLayout
    private lateinit var tvTotalSongsCount: TextView

    private val allSongsList = mutableListOf<AudioItemModel>()
    private val displayedSongsList = mutableListOf<AudioItemModel>()
    private lateinit var audioAdapter: AudioAdapter

    private var isGridMode = false
    private var currentTab = 0
    private var isScanning = false

    private val supportedAudioExtensions = setOf(
        "mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "wma"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_music, container, false)

        rvMusicList = view.findViewById(R.id.rvMusicList)
        llMusicEmptyState = view.findViewById(R.id.llMusicEmptyState)
        tvMusicTabSongs = view.findViewById(R.id.tvMusicTabSongs)
        tvMusicTabFolders = view.findViewById(R.id.tvMusicTabFolders)
        tvMusicTabPlaylists = view.findViewById(R.id.tvMusicTabPlaylists)
        btnToggleMusicLayout = view.findViewById(R.id.btnToggleMusicLayout)
        llMusicQuickActions = view.findViewById(R.id.llMusicQuickActions)
        tvTotalSongsCount = view.findViewById(R.id.tvTotalSongsCount)

        val prefs = requireContext().getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        isGridMode = prefs.getBoolean("music_grid_mode", false)
        applyLayoutManager()

        audioAdapter = AudioAdapter(requireContext(), displayedSongsList)
        rvMusicList.adapter = audioAdapter

        setupClicks()
        ensurePermissionsAndLoadAudio()

        return view
    }

    override fun onResume() {
        super.onResume()
        loadAudioFilesSafely()
    }

    private fun applyLayoutManager() {
        val ctx = context ?: return
        if (isGridMode) {
            rvMusicList.layoutManager = GridLayoutManager(ctx, 2)
            btnToggleMusicLayout.setImageResource(android.R.drawable.ic_menu_sort_by_size)
        } else {
            rvMusicList.layoutManager = LinearLayoutManager(ctx)
            btnToggleMusicLayout.setImageResource(android.R.drawable.ic_dialog_dialer)
        }
    }

    private fun setupClicks() {
        val activeColor = Color.parseColor("#00FFFF")
        val inactiveColor = Color.parseColor("#888888")

        btnToggleMusicLayout.setOnClickListener {
            isGridMode = !isGridMode
            requireContext().getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("music_grid_mode", isGridMode)
                .apply()
            applyLayoutManager()
            audioAdapter.notifyDataSetChanged()
            Toast.makeText(
                requireContext(),
                if (isGridMode) "Switched to Grid Layout" else "Switched to List Layout",
                Toast.LENGTH_SHORT
            ).show()
        }

        tvMusicTabSongs.setOnClickListener {
            currentTab = 0
            tvMusicTabSongs.setTextColor(activeColor)
            tvMusicTabFolders.setTextColor(inactiveColor)
            tvMusicTabPlaylists.setTextColor(inactiveColor)
            refreshDisplayedSongs()
        }

        tvMusicTabFolders.setOnClickListener {
            currentTab = 1
            tvMusicTabSongs.setTextColor(inactiveColor)
            tvMusicTabFolders.setTextColor(activeColor)
            tvMusicTabPlaylists.setTextColor(inactiveColor)
            refreshDisplayedSongs()
        }

        tvMusicTabPlaylists.setOnClickListener {
            currentTab = 2
            tvMusicTabSongs.setTextColor(inactiveColor)
            tvMusicTabFolders.setTextColor(inactiveColor)
            tvMusicTabPlaylists.setTextColor(activeColor)
            refreshDisplayedSongs()
        }

        llMusicQuickActions.setOnClickListener {
            if (displayedSongsList.isEmpty()) {
                Toast.makeText(requireContext(), "No songs available to play", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val first = displayedSongsList[0]
            val paths = ArrayList<String>(displayedSongsList.size)
            val titles = ArrayList<String>(displayedSongsList.size)
            val artists = ArrayList<String>(displayedSongsList.size)

            for (song in displayedSongsList) {
                paths.add(song.path)
                titles.add(song.title)
                artists.add(song.artist)
            }

            val intent = Intent(requireContext(), AudioPlayerActivity::class.java).apply {
                putExtra("AUDIO_PATH", first.path)
                putExtra("AUDIO_TITLE", first.title)
                putExtra("AUDIO_ARTIST", first.artist)
                putExtra("AUDIO_INDEX", 0)
                putStringArrayListExtra("AUDIO_LIST", paths)
                putStringArrayListExtra("AUDIO_TITLES", titles)
                putStringArrayListExtra("AUDIO_ARTISTS", artists)
            }
            startActivity(intent)
        }
    }

    private fun refreshDisplayedSongs() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)

        displayedSongsList.clear()
        when (currentTab) {
            0 -> displayedSongsList.addAll(allSongsList)
            1 -> displayedSongsList.addAll(allSongsList.sortedBy { it.folderName.lowercase(Locale.getDefault()) })
            2 -> {
                val favs = allSongsList.filter { prefs.getBoolean("fav_audio_${it.path}", false) }
                displayedSongsList.addAll(favs)
            }
        }

        tvTotalSongsCount.text = "${displayedSongsList.size} Songs"
        audioAdapter.notifyDataSetChanged()

        if (displayedSongsList.isEmpty()) {
            rvMusicList.visibility = View.GONE
            llMusicEmptyState.visibility = View.VISIBLE
        } else {
            llMusicEmptyState.visibility = View.GONE
            rvMusicList.visibility = View.VISIBLE
        }
    }

    private fun ensurePermissionsAndLoadAudio() {
        val ctx = context ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.READ_MEDIA_AUDIO), 3001)
            }
        } else {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 3001)
            }
        }
        loadAudioFilesSafely()
    }

    private fun loadAudioFilesSafely() {
        if (isScanning) return
        isScanning = true

        thread {
            try {
                val fetched = fetchAllAudioFromDevice()
                activity?.runOnUiThread {
                    isScanning = false
                    if (!isAdded || context == null) return@runOnUiThread

                    allSongsList.clear()
                    allSongsList.addAll(fetched)
                    refreshDisplayedSongs()
                }
            } catch (e: Exception) {
                isScanning = false
                e.printStackTrace()
            }
        }
    }

    private fun fetchAllAudioFromDevice(): List<AudioItemModel> {
        val output = mutableListOf<AudioItemModel>()
        val seenPaths = HashSet<String>()
        val resolver = context?.contentResolver ?: return output

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DURATION
        )

        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        try {
            resolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val rawPath = cursor.getString(dataCol)
                    val path = if (!rawPath.isNullOrEmpty()) {
                        rawPath
                    } else {
                        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
                    }

                    val title = cursor.getString(titleCol)
                        ?: cursor.getString(nameCol)
                        ?: "Audio_$id"
                    val rawArtist = cursor.getString(artistCol) ?: "Unknown Artist"
                    val cleanArtist = if (rawArtist.contains("<unknown>", ignoreCase = true)) {
                        "MAX Audio"
                    } else {
                        rawArtist
                    }

                    val size = cursor.getLong(sizeCol).coerceAtLeast(0L)
                    val duration = cursor.getLong(durCol).coerceAtLeast(0L)
                    val folderName = File(path).parentFile?.name ?: "Music"

                    if (seenPaths.add(path)) {
                        output.add(AudioItemModel(id, title, cleanArtist, path, size, duration, folderName))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val rootDir = Environment.getExternalStorageDirectory()
            if (rootDir != null && rootDir.exists()) {
                scanDirectoryForUnindexedAudio(rootDir, seenPaths, output)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return output
    }

    private fun scanDirectoryForUnindexedAudio(
        dir: File,
        seenPaths: HashSet<String>,
        output: MutableList<AudioItemModel>
    ) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.name.startsWith(".")) continue
            if (file.isDirectory) {
                val lower = file.name.lowercase(Locale.getDefault())
                if (lower == "android" || lower == "data" || lower == "obb") continue
                scanDirectoryForUnindexedAudio(file, seenPaths, output)
            } else if (file.isFile && file.length() > 10240L) {
                val ext = file.extension.lowercase(Locale.getDefault())
                if (supportedAudioExtensions.contains(ext)) {
                    val path = file.absolutePath
                    if (seenPaths.add(path)) {
                        val folderName = file.parentFile?.name ?: "Music"
                        output.add(
                            AudioItemModel(
                                path.hashCode().toLong(),
                                file.nameWithoutExtension,
                                folderName,
                                path,
                                file.length(),
                                0L,
                                folderName
                            )
                        )
                    }
                }
            }
        }
    }
}
