package com.app.mediaplayer

import android.Manifest
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.Locale
import kotlin.concurrent.thread

class VideoFragment : Fragment() {

    private lateinit var rvMainVideos: RecyclerView
    private lateinit var rvFolders: RecyclerView
    private lateinit var llPlaylistsEmpty: LinearLayout

    private lateinit var tvTabVideo: TextView
    private lateinit var tvTabFolder: TextView
    private lateinit var tvTabPlaylist: TextView

    private var llVideoRootContainer: LinearLayout? = null
    private var llPermissionBanner: LinearLayout? = null
    private var btnGrantVideoPermission: TextView? = null
    private var llHistoryContainer: LinearLayout? = null
    private var rvHistory: RecyclerView? = null
    private var tvClearHistory: TextView? = null
    private var llMainVideoTabs: LinearLayout? = null
    private var hsvFilterChips: HorizontalScrollView? = null

    private var btnSortVideos: TextView? = null
    private var btnToggleGridView: TextView? = null
    private var btnCustomizeVideoLayout: TextView? = null

    private val filterChipsList = mutableListOf<TextView>()

    private lateinit var videoAdapter: VideoAdapter
    private lateinit var folderAdapter: FolderAdapter

    private val allVideosList = mutableListOf<VideoModel>()
    private val filteredVideosList = mutableListOf<VideoModel>()
    private val allFoldersList = mutableListOf<FolderModel>()

    private var isScanning = false
    private var currentTab = 0
    private var activeFilterIndex = 0
    private var sortModeIndex = 0
    private var isGridView = false

    private val supportedVideoExtensions = setOf(
        "mp4", "mkv", "avi", "mov", "flv", "wmv", "3gp", "webm", "ts", "m4v", "mpeg", "mpg"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_video, container, false)

        rvMainVideos = view.findViewById(R.id.rvMainVideos)
        rvFolders = view.findViewById(R.id.rvFolders)
        llPlaylistsEmpty = view.findViewById(R.id.llPlaylistsEmpty)

        tvTabVideo = view.findViewById(R.id.tvTabVideo)
        tvTabFolder = view.findViewById(R.id.tvTabFolder)
        tvTabPlaylist = view.findViewById(R.id.tvTabPlaylist)

        llVideoRootContainer = view.findViewById(R.id.llVideoRootContainer)
        llPermissionBanner = view.findViewById(R.id.llPermissionBanner)
        btnGrantVideoPermission = view.findViewById(R.id.btnGrantVideoPermission)
        llHistoryContainer = view.findViewById(R.id.llHistoryContainer)
        rvHistory = view.findViewById(R.id.rvHistory)
        tvClearHistory = view.findViewById(R.id.tvClearHistory)
        llMainVideoTabs = view.findViewById(R.id.llMainVideoTabs)
        hsvFilterChips = view.findViewById(R.id.hsvFilterChips)

        btnSortVideos = view.findViewById(R.id.btnSortVideos)
        btnToggleGridView = view.findViewById(R.id.btnToggleGridView)
        btnCustomizeVideoLayout = view.findViewById(R.id.btnCustomizeVideoLayout)

        filterChipsList.clear()
        listOf(
            R.id.chipAllVideos,
            R.id.chipRecentVideos,
            R.id.chipHdLarge,
            R.id.chipMovies,
            R.id.chipShortClips,
            R.id.chipFavorites
        ).forEach { id ->
            view.findViewById<TextView>(id)?.let { filterChipsList.add(it) }
        }

        rvHistory?.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvFolders.layoutManager = LinearLayoutManager(requireContext())

        val prefs = requireContext().getSharedPreferences("MAX_LAYOUT_PREFS", Context.MODE_PRIVATE)
        isGridView = prefs.getBoolean("video_grid_mode", false)
        sortModeIndex = prefs.getInt("video_sort_mode", 0)

        applyVideoLayoutManager()
        applySavedSectionPositions()
        setupAllClickListeners()
        reloadVideosNow()

        return view
    }

    override fun onResume() {
        super.onResume()
        reloadVideosNow()
    }

    fun reloadVideosNow() {
        updatePermissionBanner()
        loadMediaDataSafely()
    }

    private fun applyVideoLayoutManager() {
        val ctx = context ?: return
        if (isGridView) {
            rvMainVideos.layoutManager = GridLayoutManager(ctx, 2)
            btnToggleGridView?.text = "List"
        } else {
            rvMainVideos.layoutManager = LinearLayoutManager(ctx)
            btnToggleGridView?.text = "Grid"
        }
    }

    private fun setupAllClickListeners() {
        val activeColor = Color.parseColor("#00FFFF")
        val inactiveColor = Color.parseColor("#888888")

        btnGrantVideoPermission?.setOnClickListener {
            (activity as? MainActivity)?.checkAndRequestPermissions()
            requestPermissionsFromFragment()
        }

        tvClearHistory?.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            val prefs = ctx.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            allVideosList.forEach { video ->
                editor.remove("history_time_${video.path}")
            }
            editor.apply()
            refreshHistoryList()
            Toast.makeText(ctx, "Playback history cleared", Toast.LENGTH_SHORT).show()
        }

        tvTabVideo.setOnClickListener {
            currentTab = 0
            tvTabVideo.setTextColor(activeColor)
            tvTabFolder.setTextColor(inactiveColor)
            tvTabPlaylist.setTextColor(inactiveColor)
            refreshDisplayedVideos()
        }

        tvTabFolder.setOnClickListener {
            currentTab = 1
            tvTabVideo.setTextColor(inactiveColor)
            tvTabFolder.setTextColor(activeColor)
            tvTabPlaylist.setTextColor(inactiveColor)

            if (isAdded && context != null) {
                folderAdapter = FolderAdapter(requireContext(), allFoldersList)
                rvFolders.adapter = folderAdapter
            }

            rvMainVideos.visibility = View.GONE
            rvFolders.visibility = View.VISIBLE
            llPlaylistsEmpty.visibility = if (allFoldersList.isEmpty()) View.VISIBLE else View.GONE
        }

        tvTabPlaylist.setOnClickListener {
            currentTab = 2
            tvTabVideo.setTextColor(inactiveColor)
            tvTabFolder.setTextColor(inactiveColor)
            tvTabPlaylist.setTextColor(activeColor)

            showFavoritesAndBookmarksPlaylist()
        }

        btnSortVideos?.setOnClickListener {
            showSortDialog()
        }

        btnToggleGridView?.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            isGridView = !isGridView
            ctx.getSharedPreferences("MAX_LAYOUT_PREFS", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("video_grid_mode", isGridView)
                .apply()
            applyVideoLayoutManager()
            refreshDisplayedVideos()
        }

        btnCustomizeVideoLayout?.setOnClickListener {
            showCustomizeVideoLayoutDialog()
        }

        filterChipsList.forEachIndexed { index, chip ->
            chip.setOnClickListener {
                activeFilterIndex = index
                updateFilterChipColors()
                if (currentTab != 0) {
                    tvTabVideo.performClick()
                } else {
                    refreshDisplayedVideos()
                }
            }
        }
    }

    private fun updateFilterChipColors() {
        filterChipsList.forEachIndexed { index, chip ->
            if (index == activeFilterIndex) {
                chip.setBackgroundColor(Color.parseColor("#00FFFF"))
                chip.setTextColor(Color.BLACK)
                chip.typeface = Typeface.DEFAULT_BOLD
            } else {
                chip.setBackgroundColor(Color.parseColor("#161622"))
                chip.setTextColor(Color.parseColor("#CCCCCC"))
                chip.typeface = Typeface.DEFAULT
            }
        }
    }

    private fun showSortDialog() {
        val ctx = context ?: return
        val options = arrayOf(
            "Date Added (Newest First)",
            "Title (A to Z)",
            "File Size (Largest First)",
            "Duration (Longest First)"
        )
        AlertDialog.Builder(ctx)
            .setTitle("Sort Videos By")
            .setSingleChoiceItems(options, sortModeIndex) { dialog, which ->
                sortModeIndex = which
                ctx.getSharedPreferences("MAX_LAYOUT_PREFS", Context.MODE_PRIVATE)
                    .edit()
                    .putInt("video_sort_mode", sortModeIndex)
                    .apply()
                refreshDisplayedVideos()
                dialog.dismiss()
            }
            .show()
    }

    private fun showCustomizeVideoLayoutDialog() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("MAX_LAYOUT_PREFS", Context.MODE_PRIVATE)
        val showHistory = prefs.getBoolean("show_history_section", true)
        val showFilters = prefs.getBoolean("show_filter_bar", true)
        val tabsAtBottom = prefs.getBoolean("tabs_at_bottom", false)

        val options = arrayOf(
            if (showHistory) "Hide Top History Section" else "Show Top History Section",
            if (showFilters) "Hide Filter Chips Bar" else "Show Filter Chips Bar",
            if (tabsAtBottom) "Move [Video | Folder | Playlist] Tabs to Top" else "Move [Video | Folder | Playlist] Tabs to Bottom",
            if (isGridView) "Switch to Classic List View" else "Switch to 2-Column Grid View",
            "Grant All-Files Access (For Hidden / Unindexed MKV & AVI)",
            "Reset Video Section Layout to Default"
        )

        AlertDialog.Builder(ctx)
            .setTitle("Customize Video Section Layout")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        prefs.edit().putBoolean("show_history_section", !showHistory).apply()
                        applySavedSectionPositions()
                    }
                    1 -> {
                        prefs.edit().putBoolean("show_filter_bar", !showFilters).apply()
                        applySavedSectionPositions()
                    }
                    2 -> {
                        prefs.edit().putBoolean("tabs_at_bottom", !tabsAtBottom).apply()
                        applySavedSectionPositions()
                    }
                    3 -> {
                        isGridView = !isGridView
                        prefs.edit().putBoolean("video_grid_mode", isGridView).apply()
                        applyVideoLayoutManager()
                        refreshDisplayedVideos()
                    }
                    4 -> {
                        (activity as? MainActivity)?.requestAllFilesAccessForDeepScan()
                        loadMediaDataSafely()
                    }
                    5 -> {
                        prefs.edit()
                            .putBoolean("show_history_section", true)
                            .putBoolean("show_filter_bar", true)
                            .putBoolean("tabs_at_bottom", false)
                            .putBoolean("video_grid_mode", false)
                            .apply()
                        isGridView = false
                        applyVideoLayoutManager()
                        applySavedSectionPositions()
                        refreshDisplayedVideos()
                    }
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun applySavedSectionPositions() {
        val ctx = context ?: return
        val root = llVideoRootContainer ?: return
        val prefs = ctx.getSharedPreferences("MAX_LAYOUT_PREFS", Context.MODE_PRIVATE)

        val showHistory = prefs.getBoolean("show_history_section", true)
        val showFilters = prefs.getBoolean("show_filter_bar", true)
        val tabsAtBottom = prefs.getBoolean("tabs_at_bottom", false)

        llHistoryContainer?.visibility = if (showHistory) View.VISIBLE else View.GONE
        hsvFilterChips?.visibility = if (showFilters) View.VISIBLE else View.GONE

        val tabsView = llMainVideoTabs ?: return
        if (tabsView.parent == root) {
            root.removeView(tabsView)
            if (tabsAtBottom) {
                root.addView(tabsView)
            } else {
                val historyIdx = if (llHistoryContainer != null) root.indexOfChild(llHistoryContainer) else 0
                val insertPos = (historyIdx + 1).coerceIn(0, root.childCount)
                root.addView(tabsView, insertPos)
            }
        }
    }

    private fun hasVideoPermission(): Boolean {
        val ctx = context ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun updatePermissionBanner() {
        llPermissionBanner?.visibility = if (hasVideoPermission()) View.GONE else View.VISIBLE
    }

    private fun requestPermissionsFromFragment() {
        val ctx = context ?: return
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        try {
            requestPermissions(perms, 2005)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", ctx.packageName, null)
                }
                startActivity(intent)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updatePermissionBanner()
        loadMediaDataSafely()
    }

    private fun refreshHistoryList() {
        val ctx = context ?: return
        val historyRecycler = rvHistory ?: return
        val prefs = ctx.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        val recentList = allVideosList.sortedByDescending {
            prefs.getLong("history_time_${it.path}", 0L)
        }.take(15)
        historyRecycler.adapter = VideoAdapter(ctx, recentList)
    }

    private fun refreshDisplayedVideos() {
        if (!isAdded || context == null) return
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)

        val baseFiltered = when (activeFilterIndex) {
            1 -> allVideosList.take(30)
            2 -> allVideosList.filter { it.size >= 50L * 1024L * 1024L }
            3 -> allVideosList.filter { it.duration >= 15L * 60L * 1000L || it.size >= 150L * 1024L * 1024L }
            4 -> allVideosList.filter {
                it.path.contains("WhatsApp", ignoreCase = true) ||
                    it.path.contains("Telegram", ignoreCase = true) ||
                    it.duration in 1L..(180L * 1000L)
            }
            5 -> allVideosList.filter {
                prefs.getBoolean("fav_${it.path}", false) ||
                    prefs.getLong("bookmark_${it.path}", -1L) != -1L
            }
            else -> allVideosList
        }

        val sorted = when (sortModeIndex) {
            1 -> baseFiltered.sortedBy { it.title.lowercase(Locale.getDefault()) }
            2 -> baseFiltered.sortedByDescending { it.size }
            3 -> baseFiltered.sortedByDescending { it.duration }
            else -> baseFiltered
        }

        filteredVideosList.clear()
        filteredVideosList.addAll(sorted)

        if (currentTab == 0) {
            videoAdapter = VideoAdapter(ctx, filteredVideosList)
            rvMainVideos.adapter = videoAdapter
            rvFolders.visibility = View.GONE
            if (filteredVideosList.isEmpty()) {
                rvMainVideos.visibility = View.GONE
                llPlaylistsEmpty.visibility = View.VISIBLE
            } else {
                llPlaylistsEmpty.visibility = View.GONE
                rvMainVideos.visibility = View.VISIBLE
            }
        } else if (currentTab == 2) {
            showFavoritesAndBookmarksPlaylist()
        }
    }

    private fun showFavoritesAndBookmarksPlaylist() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        val favAndBookmarked = allVideosList.filter { video ->
            prefs.getBoolean("fav_${video.path}", false) ||
                prefs.getLong("bookmark_${video.path}", -1L) != -1L
        }

        rvFolders.visibility = View.GONE
        if (favAndBookmarked.isEmpty()) {
            rvMainVideos.visibility = View.GONE
            llPlaylistsEmpty.visibility = View.VISIBLE
        } else {
            videoAdapter = VideoAdapter(ctx, favAndBookmarked)
            rvMainVideos.adapter = videoAdapter
            llPlaylistsEmpty.visibility = View.GONE
            rvMainVideos.visibility = View.VISIBLE
        }
    }

    private fun loadMediaDataSafely() {
        if (isScanning) return
        isScanning = true

        thread {
            try {
                val fetchedVideos = mutableListOf<VideoModel>()
                val fetchedFolders = mutableListOf<FolderModel>()
                fetchVideosAndFoldersThreeStage(fetchedVideos, fetchedFolders)

                activity?.runOnUiThread {
                    isScanning = false
                    if (!isAdded || context == null) return@runOnUiThread

                    updatePermissionBanner()

                    allVideosList.clear()
                    allVideosList.addAll(fetchedVideos)

                    allFoldersList.clear()
                    allFoldersList.addAll(fetchedFolders)

                    folderAdapter = FolderAdapter(requireContext(), allFoldersList)
                    rvFolders.adapter = folderAdapter

                    refreshHistoryList()

                    when (currentTab) {
                        0 -> refreshDisplayedVideos()
                        1 -> {
                            rvMainVideos.visibility = View.GONE
                            rvFolders.visibility = View.VISIBLE
                            llPlaylistsEmpty.visibility = if (allFoldersList.isEmpty()) View.VISIBLE else View.GONE
                        }
                        2 -> showFavoritesAndBookmarksPlaylist()
                    }
                }
            } catch (e: Exception) {
                isScanning = false
                e.printStackTrace()
            }
        }
    }

    private fun fetchVideosAndFoldersThreeStage(
        videoOutput: MutableList<VideoModel>,
        folderOutput: MutableList<FolderModel>
    ) {
        val folderMap = mutableMapOf<String, FolderModel>()
        val seenPaths = HashSet<String>()
        val contentResolver = context?.contentResolver ?: return

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"
        val uris = listOf(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.INTERNAL_CONTENT_URI
        )

        // Stage 1: Standard MediaStore.Video Query
        for (uri in uris) {
            try {
                contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                    val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                    val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                    val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                    val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                    val bucketIdCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_ID)
                    val bucketNameCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

                    while (cursor.moveToNext()) {
                        val id = if (idCol != -1) cursor.getLong(idCol) else 0L
                        val rawPath = if (dataCol != -1) cursor.getString(dataCol) else null
                        val contentUriStr = ContentUris.withAppendedId(uri, id).toString()
                        val path = if (!rawPath.isNullOrEmpty()) rawPath else contentUriStr

                        val title = if (nameCol != -1) {
                            cursor.getString(nameCol) ?: File(path).name
                        } else {
                            "Video_$id"
                        }
                        val size = if (sizeCol != -1) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L
                        val duration = if (durCol != -1) cursor.getLong(durCol).coerceAtLeast(0L) else 0L

                        val bucketId = if (bucketIdCol != -1) {
                            cursor.getString(bucketIdCol) ?: "0"
                        } else {
                            File(path).parent ?: "0"
                        }
                        val bucketName = if (bucketNameCol != -1) {
                            cursor.getString(bucketNameCol) ?: "Videos"
                        } else {
                            File(path).parentFile?.name ?: "Videos"
                        }

                        if (seenPaths.add(path)) {
                            videoOutput.add(VideoModel(id, title, path, size, duration))
                            if (folderMap.containsKey(bucketId)) {
                                folderMap[bucketId]!!.videoCount++
                            } else {
                                folderMap[bucketId] = FolderModel(bucketId, bucketName, path, 1)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Stage 2: MediaStore.Files Query for MKV, AVI, TS, WebM & Unindexed Videos
        try {
            val filesUri = MediaStore.Files.getContentUri("external")
            val fileProj = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.MIME_TYPE
            )
            val selection =
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?"
            val selectionArgs = arrayOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                "video/%"
            )
            contentResolver.query(filesUri, fileProj, selection, selectionArgs, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                val sizeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)

                while (cursor.moveToNext()) {
                    val id = if (idCol != -1) cursor.getLong(idCol) else 0L
                    val path = if (dataCol != -1) cursor.getString(dataCol) else null
                    if (path.isNullOrEmpty()) continue

                    val title = if (nameCol != -1) {
                        cursor.getString(nameCol) ?: File(path).name
                    } else {
                        File(path).name
                    }
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L
                    val parent = File(path).parentFile
                    val bucketId = parent?.absolutePath ?: "0"
                    val bucketName = parent?.name ?: "Videos"

                    if (seenPaths.add(path)) {
                        videoOutput.add(VideoModel(id, title, path, size, 0L))
                        if (folderMap.containsKey(bucketId)) {
                            folderMap[bucketId]!!.videoCount++
                        } else {
                            folderMap[bucketId] = FolderModel(bucketId, bucketName, path, 1)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Stage 3: Direct Storage Directory Scan (Android 5 to Android 16)
        try {
            val rootDir = Environment.getExternalStorageDirectory()
            if (rootDir != null && rootDir.exists()) {
                scanDirectoryForUnindexedVideos(rootDir, seenPaths, videoOutput, folderMap, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        folderOutput.addAll(folderMap.values.sortedBy { it.folderName.lowercase(Locale.getDefault()) })
    }

    private fun scanDirectoryForUnindexedVideos(
        dir: File,
        seenPaths: HashSet<String>,
        videoOutput: MutableList<VideoModel>,
        folderMap: MutableMap<String, FolderModel>,
        depth: Int
    ) {
        if (depth > 6) return
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.name.startsWith(".")) continue
            if (file.isDirectory) {
                val lowerName = file.name.lowercase(Locale.getDefault())
                if (lowerName == "data" || lowerName == "obb") continue
                scanDirectoryForUnindexedVideos(file, seenPaths, videoOutput, folderMap, depth + 1)
            } else if (file.isFile && file.length() > 10240L) {
                val ext = file.extension.lowercase(Locale.getDefault())
                if (supportedVideoExtensions.contains(ext)) {
                    val path = file.absolutePath
                    if (seenPaths.add(path)) {
                        val parent = file.parentFile
                        val bucketId = parent?.absolutePath ?: "0"
                        val bucketName = parent?.name ?: "Internal Storage"
                        val generatedId = path.hashCode().toLong()

                        videoOutput.add(VideoModel(generatedId, file.name, path, file.length(), 0L))
                        if (folderMap.containsKey(bucketId)) {
                            folderMap[bucketId]!!.videoCount++
                        } else {
                            folderMap[bucketId] = FolderModel(bucketId, bucketName, bucketId, 1)
                        }
                    }
                }
            }
        }
    }
}
