package com.app.mediaplayer

import android.Manifest
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File
import java.util.ArrayList
import java.util.Locale
import kotlin.concurrent.thread

class MusicFragment : Fragment() {

    private lateinit var rootMusicFragmentFrame: FrameLayout
    private lateinit var imgMusicThemeWallpaper: ImageView
    private lateinit var viewMusicThemeDimmer: View

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
    private lateinit var imgMiniPlayerArt: ImageView
    private lateinit var llMiniPlayerTextZone: LinearLayout
    private lateinit var tvMiniPlayerTitle: TextView
    private lateinit var tvMiniPlayerArtist: TextView
    private lateinit var btnMiniPlayOpen: ImageView

    private val allTracksList = mutableListOf<AudioAdapter.MusicTrack>()
    private val displayedTracksList = mutableListOf<AudioAdapter.MusicTrack>()
    private lateinit var audioAdapter: AudioAdapter

    private var currentTab = 0 // 0: Songs, 1: Playlists, 2: Artists, 3: Favorites
    private var sortMode = 0 // 0: Newest Downloaded, 1: A to Z, 2: Long Duration Songs, 3: Largest Size, 4: Artist
    private var selectedPlaylistFilter: String? = null
    private var isScanning = false
    private var accentColorInt = Color.parseColor("#00FFFF")

    private val uiHandler = Handler(Looper.getMainLooper())
    private val miniPlayerSyncRunnable = object : Runnable {
        override fun run() {
            if (isAdded && context != null) {
                syncMiniPlayerState()
                uiHandler.postDelayed(this, 600)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_music, container, false)

        rootMusicFragmentFrame = view.findViewById(R.id.rootMusicFragmentFrame)
        imgMusicThemeWallpaper = view.findViewById(R.id.imgMusicThemeWallpaper)
        viewMusicThemeDimmer = view.findViewById(R.id.viewMusicThemeDimmer)

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
        imgMiniPlayerArt = view.findViewById(R.id.imgMiniPlayerArt)
        llMiniPlayerTextZone = view.findViewById(R.id.llMiniPlayerTextZone)
        tvMiniPlayerTitle = view.findViewById(R.id.tvMiniPlayerTitle)
        tvMiniPlayerArtist = view.findViewById(R.id.tvMiniPlayerArtist)
        btnMiniPlayOpen = view.findViewById(R.id.btnMiniPlayOpen)

        tvMiniPlayerTitle.isSelected = true

        val prefs = requireContext().getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
        sortMode = prefs.getInt("music_sort_mode", 0)

        rvMusicTracks.layoutManager = LinearLayoutManager(requireContext())
        audioAdapter = AudioAdapter(requireContext(), displayedTracksList) {
            refreshMusicTabsUI()
        }
        rvMusicTracks.adapter = audioAdapter

        applyGlobalThemeToMusicHome()
        setupClicks()
        checkAudioAndNotificationPermissions()

        return view
    }

    override fun onResume() {
        super.onResume()
        applyGlobalThemeToMusicHome()
        syncMiniPlayerState()
        uiHandler.removeCallbacks(miniPlayerSyncRunnable)
        uiHandler.post(miniPlayerSyncRunnable)
        loadAllAudioFast()
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(miniPlayerSyncRunnable)
    }

    private fun applyGlobalThemeToMusicHome() {
        val ctx = context ?: return
        val themePrefs = ctx.getSharedPreferences("MAX_THEME_PREFS", Context.MODE_PRIVATE)
        val audioPrefs = ctx.getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)

        val bgHex = themePrefs.getString("app_bg_color", null)
            ?: audioPrefs.getString("app_bg_color", "#0A0A10") ?: "#0A0A10"
        val accentHex = themePrefs.getString("app_accent_color", null)
            ?: audioPrefs.getString("app_accent_color", "#00FFFF") ?: "#00FFFF"
        val customBgPhoto = themePrefs.getString("app_custom_wallpaper", null)
            ?: audioPrefs.getString("app_custom_wallpaper", null)

        try {
            rootMusicFragmentFrame.setBackgroundColor(Color.parseColor(bgHex))
            accentColorInt = Color.parseColor(accentHex)
        } catch (e: Exception) {
            accentColorInt = Color.parseColor("#00FFFF")
        }

        if (!customBgPhoto.isNullOrEmpty()) {
            imgMusicThemeWallpaper.visibility = View.VISIBLE
            viewMusicThemeDimmer.visibility = View.VISIBLE
            Glide.with(ctx)
                .load(Uri.parse(customBgPhoto))
                .centerCrop()
                .into(imgMusicThemeWallpaper)
        } else {
            imgMusicThemeWallpaper.visibility = View.GONE
            viewMusicThemeDimmer.visibility = View.GONE
        }

        btnNewPlaylist.setTextColor(accentColorInt)
        tvMiniPlayerArtist.setTextColor(accentColorInt)
        updateTabTextColors()
    }

    private fun updateTabTextColors() {
        val tabs = listOf(tvMusicTabSongs, tvMusicTabPlaylists, tvMusicTabArtists, tvMusicTabFavorites)
        tabs.forEachIndexed { i, tv ->
            tv.setTextColor(if (i == currentTab) accentColorInt else Color.parseColor("#B0B0C4"))
        }
    }

    private fun checkAudioAndNotificationPermissions() {
        val ctx = context ?: return
        val needed = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (needed.isNotEmpty()) {
            requestPermissions(needed.toTypedArray(), 3001)
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
                updateTabTextColors()
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

        // High-Contrast Crystal-Clear Custom 3-Dots Dialog (Never dark/invisible in any theme)
        btnMusicMoreMenu.setOnClickListener {
            showBrightMusicThreeDotsDialog()
        }

        // Clicking the Mini Player text/art opens the full AudioPlayerActivity
        val openFullPlayerListener = View.OnClickListener {
            val ctx = context ?: return@OnClickListener
            val prefs = ctx.getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
            val lastPath = prefs.getString("last_audio_path", null) ?: return@OnClickListener
            val targetIdx = allTracksList.indexOfFirst { it.path == lastPath }.coerceAtLeast(0)
            if (allTracksList.isNotEmpty()) {
                launchAudioPlayerWithList(allTracksList, targetIdx, false, resumeExistingIfSame = true)
            }
        }
        cardMiniAudioPlayer.setOnClickListener(openFullPlayerListener)
        llMiniPlayerTextZone.setOnClickListener(openFullPlayerListener)

        // Clicking Play/Pause button on Mini Player directly toggles Play (▶) and Pause (|| two straight bars)
        btnMiniPlayOpen.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            val toggled = AudioPlayerActivity.toggleSharedPlaybackFromMiniPlayer(ctx)
            if (!toggled) {
                openFullPlayerListener.onClick(it)
            } else {
                syncMiniPlayerState()
            }
        }
    }

    private fun showBrightMusicThreeDotsDialog() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
        val options = arrayOf(
            if (sortMode == 0) "✓ New Downloaded Songs (Newest First)" else "• New Downloaded Songs (Newest First)",
            if (sortMode == 1) "✓ A to Z Songs (Alphabetical)" else "• A to Z Songs (Alphabetical)",
            if (sortMode == 2) "✓ Long Duration Songs (Longest First)" else "• Long Duration Songs (Longest First)",
            if (sortMode == 3) "✓ Large Size Audio Files" else "• Large Size Audio Files",
            if (sortMode == 4) "✓ Sort by Singer / Artist Name" else "• Sort by Singer / Artist Name",
            "Create New Custom Playlist",
            "Change Music Section Theme Color",
            "Rescan & Refresh All Songs"
        )

        showBrightStyledMenuDialog(ctx, "Music Sort & Library Options", options) { which ->
            when (which) {
                0, 1, 2, 3, 4 -> {
                    sortMode = which
                    prefs.edit().putInt("music_sort_mode", sortMode).apply()
                    refreshMusicTabsUI()
                }
                5 -> showCreateNewPlaylistDialog()
                6 -> showQuickThemeSelectorDialog()
                7 -> {
                    loadAllAudioFast()
                    Toast.makeText(ctx, "Music library refreshed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showQuickThemeSelectorDialog() {
        val ctx = context ?: return
        val themes = arrayOf(
            "Default Cyber Cyan (#0A0A10 + #00FFFF)",
            "Royal Neon Purple (#0D0818 + #FF40FF)",
            "Emerald Matrix Green (#07140E + #00E676)",
            "Sunset Gold Pro (#161008 + #FFB300)",
            "Crimson Red Dark (#18080C + #FF2E63)"
        )
        val bgColors = arrayOf("#0A0A10", "#0D0818", "#07140E", "#161008", "#18080C")
        val accents = arrayOf("#00FFFF", "#FF40FF", "#00E676", "#FFB300", "#FF2E63")

        showBrightStyledMenuDialog(ctx, "Select App & Player Theme", themes) { idx ->
            ctx.getSharedPreferences("MAX_THEME_PREFS", Context.MODE_PRIVATE).edit()
                .putString("app_bg_color", bgColors[idx])
                .putString("app_accent_color", accents[idx])
                .apply()
            ctx.getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE).edit()
                .putString("app_bg_color", bgColors[idx])
                .putString("app_accent_color", accents[idx])
                .apply()
            applyGlobalThemeToMusicHome()
            audioAdapter.notifyDataSetChanged()
            Toast.makeText(ctx, "Theme Applied!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCreateNewPlaylistDialog() {
        val ctx = context ?: return
        val input = EditText(ctx).apply {
            hint = "Enter playlist name (e.g. Gym, Favorite Hits)"
            setHintTextColor(Color.parseColor("#888899"))
            setTextColor(Color.WHITE)
            setPadding(45, 35, 45, 35)
        }
        AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
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

        val items = mutableListOf("Show All Playlist Songs")
        items.addAll(playlists.map { plName ->
            val count = AudioAdapter.getPlaylistSongs(ctx, plName).size
            "$plName ($count songs)"
        })

        showBrightStyledMenuDialog(ctx, "Select Playlist", items.toTypedArray()) { which ->
            selectedPlaylistFilter = if (which == 0) null else playlists[which - 1]
            refreshMusicTabsUI()
        }
    }

    private fun syncMiniPlayerState() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
        val liveTitle = AudioPlayerActivity.getCurrentPlayingTitle() ?: prefs.getString("last_audio_title", null)
        val liveArtist = AudioPlayerActivity.getCurrentPlayingArtist() ?: prefs.getString("last_audio_artist", null)
        val livePath = AudioPlayerActivity.getCurrentPlayingPath() ?: prefs.getString("last_audio_path", null)
        val isPlayingNow = AudioPlayerActivity.isSharedPlayerPlaying()

        if (!liveTitle.isNullOrEmpty()) {
            cardMiniAudioPlayer.visibility = View.VISIBLE
            if (tvMiniPlayerTitle.text.toString() != liveTitle) {
                tvMiniPlayerTitle.text = liveTitle
                tvMiniPlayerTitle.isSelected = true
            }
            tvMiniPlayerArtist.text = if (!liveArtist.isNullOrEmpty()) liveArtist else "Unknown Artist"

            // Draw either Two Straight Bars (||) when playing or Play Triangle (▶) when paused
            val d = resources.displayMetrics.density
            btnMiniPlayOpen.setImageDrawable(
                MiniPlayPauseIconDrawable(isPlayingNow, d, accentColorInt)
            )

            // Load custom photo or album art into Mini Player thumbnail
            if (!livePath.isNullOrEmpty()) {
                val customPhoto = prefs.getString("custom_art_$livePath", null)
                val matchTrack = allTracksList.firstOrNull { it.path == livePath }
                val artUri = when {
                    !customPhoto.isNullOrEmpty() -> Uri.parse(customPhoto)
                    matchTrack != null && matchTrack.albumId > 0L -> ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        matchTrack.albumId
                    )
                    else -> null
                }
                if (artUri != null) {
                    Glide.with(ctx)
                        .load(artUri)
                        .placeholder(android.R.drawable.ic_lock_silent_mode_off)
                        .error(android.R.drawable.ic_lock_silent_mode_off)
                        .centerCrop()
                        .into(imgMiniPlayerArt)
                } else {
                    imgMiniPlayerArt.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
                }
            }
        } else {
            cardMiniAudioPlayer.visibility = View.GONE
        }
    }

    private fun launchAudioPlayerWithList(
        list: List<AudioAdapter.MusicTrack>,
        startIndex: Int,
        shuffleStart: Boolean,
        resumeExistingIfSame: Boolean = false
    ) {
        val ctx = context ?: return
        val chosen = list[startIndex.coerceIn(0, list.size - 1)]
        val paths = ArrayList<String>(list.size)
        val titles = ArrayList<String>(list.size)
        val artists = ArrayList<String>(list.size)
        val albumIds = ArrayList<String>(list.size)

        list.forEach {
            paths.add(it.path)
            titles.add(it.title)
            artists.add(it.artist)
            albumIds.add(it.albumId.toString())
        }

        val intent = Intent(ctx, AudioPlayerActivity::class.java).apply {
            putExtra("AUDIO_PATH", chosen.path)
            putExtra("AUDIO_TITLE", chosen.title)
            putExtra("AUDIO_ARTIST", chosen.artist)
            putExtra("AUDIO_DURATION", chosen.duration)
            putExtra("AUDIO_ID", chosen.id)
            putExtra("AUDIO_ALBUM_ID", chosen.albumId)
            putExtra("AUDIO_POSITION", startIndex)
            putExtra("AUDIO_SHUFFLE_START", shuffleStart)
            putExtra("RESUME_IF_SAME", resumeExistingIfSame)
            putStringArrayListExtra("AUDIO_LIST_PATHS", paths)
            putStringArrayListExtra("AUDIO_LIST_TITLES", titles)
            putStringArrayListExtra("AUDIO_LIST_ARTISTS", artists)
            putStringArrayListExtra("AUDIO_LIST_ALBUM_IDS", albumIds)
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
                2 -> baseList.sortedByDescending { it.duration }
                3 -> baseList.sortedByDescending { it.size }
                4 -> baseList.sortedBy { it.artist.lowercase(Locale.getDefault()) }
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
                    tvMusicEmptyDesc.text = "Tap Favorite in the player or 3-dots on any song to mark as Favorite."
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
                        MediaStore.Audio.Media.ALBUM_ID,
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
                        val albumIdC = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
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
                                "Unknown Singer"
                            } else {
                                rawArtist
                            }
                            val albumId = if (albumIdC != -1) cursor.getLong(albumIdC) else 0L
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
                                        duration = duration,
                                        albumId = albumId
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
                    syncMiniPlayerState()
                    refreshMusicTabsUI()
                }
            } catch (e: Exception) {
                isScanning = false
                e.printStackTrace()
            }
        }
    }

    // Custom Drawable for Mini Player Button: Two Straight Bars (||) when Playing, Triangle (▶) when Paused
    private class MiniPlayPauseIconDrawable(
        private val isPlaying: Boolean,
        private val density: Float,
        private val color: Int
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setColor(color)
            style = if (isPlaying) Paint.Style.STROKE else Paint.Style.FILL
            strokeWidth = 2.6f * density
            strokeCap = Paint.Cap.ROUND
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val r = b.width().coerceAtMost(b.height()) * 0.36f
            if (isPlaying) {
                // Two straight vertical bars (||)
                canvas.drawLine(cx - r * 0.42f, cy - r * 0.72f, cx - r * 0.42f, cy + r * 0.72f, paint)
                canvas.drawLine(cx + r * 0.42f, cy - r * 0.72f, cx + r * 0.42f, cy + r * 0.72f, paint)
            } else {
                // Play Triangle (▶)
                val path = Path().apply {
                    moveTo(cx - r * 0.45f, cy - r * 0.75f)
                    lineTo(cx + r * 0.75f, cy)
                    lineTo(cx - r * 0.45f, cy + r * 0.75f)
                    close()
                }
                canvas.drawPath(path, paint)
            }
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    companion object {
        fun showBrightStyledMenuDialog(
            context: Context,
            headerTitle: String,
            items: Array<String>,
            onSelect: (Int) -> Unit
        ) {
            val d = context.resources.displayMetrics.density
            val rootCard = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((18 * d).toInt(), (18 * d).toInt(), (18 * d).toInt(), (14 * d).toInt())
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#161626"))
                    cornerRadius = 22f * d
                    setStroke((1.5f * d).toInt(), Color.parseColor("#00FFFF"))
                }
            }

            val tvHeader = TextView(context).apply {
                text = headerTitle
                setTextColor(Color.parseColor("#00FFFF"))
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setPadding((10 * d).toInt(), (4 * d).toInt(), (10 * d).toInt(), (12 * d).toInt())
            }
            rootCard.addView(tvHeader)

            val topLine = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (1 * d).toInt()
                )
                setBackgroundColor(Color.parseColor("#33FFFFFF"))
            }
            rootCard.addView(topLine)

            val scroll = ScrollView(context)
            val listContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            val dialog = AlertDialog.Builder(context)
                .setView(rootCard)
                .create()
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            items.forEachIndexed { idx, label ->
                val tvItem = TextView(context).apply {
                    text = label
                    setTextColor(Color.WHITE)
                    textSize = 14.5f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt())
                    setOnClickListener {
                        dialog.dismiss()
                        onSelect(idx)
                    }
                }
                listContainer.addView(tvItem)
                if (idx < items.size - 1) {
                    val divider = View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            (1 * d).toInt()
                        )
                        setBackgroundColor(Color.parseColor("#1AFFFFFF"))
                    }
                    listContainer.addView(divider)
                }
            }

            scroll.addView(listContainer)
            rootCard.addView(scroll)
            dialog.show()
        }
    }
}
