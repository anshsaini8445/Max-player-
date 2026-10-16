package com.app.mediaplayer

import android.Manifest
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
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

    private var rvHistoryOptional: RecyclerView? = null
    private var rootLinearContainer: LinearLayout? = null
    private var dynamicControlHeader: LinearLayout? = null
    private var permissionBanner: LinearLayout? = null
    private var tvVideoSummaryCount: TextView? = null

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

        val historyId = resources.getIdentifier("rvHistory", "id", requireContext().packageName)
        if (historyId != 0) {
            rvHistoryOptional = view.findViewById(historyId)
            rvHistoryOptional?.layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        }

        val prefs = requireContext().getSharedPreferences("MAX_LAYOUT_PREFS", Context.MODE_PRIVATE)
        isGridView = prefs.getBoolean("video_grid_mode", false)
        sortModeIndex = prefs.getInt("video_sort_mode", 0)

        applyVideoLayoutManager()
        rvFolders.layoutManager = LinearLayoutManager(requireContext())

        injectDynamicToolbarAndCustomizer(view)
        applyCustomSectionOrder()
        setupTabClicks()
        reloadVideosNow()

        return view
    }

    override fun onResume() {
        super.onResume()
        reloadVideosNow()
    }

    fun reloadVideosNow() {
        updatePermissionBannerVisibility()
        loadMediaDataSafely()
    }

    private fun applyVideoLayoutManager() {
        val ctx = context ?: return
        if (isGridView) {
            rvMainVideos.layoutManager = GridLayoutManager(ctx, 2)
        } else {
            rvMainVideos.layoutManager = LinearLayoutManager(ctx)
        }
    }

    private fun injectDynamicToolbarAndCustomizer(rootView: View) {
        val ctx = context ?: return
        val parentLinear = rootView as? LinearLayout ?: return
        rootLinearContainer = parentLinear

        // Permission Helper Banner (Shown only if permission is missing)
        val banner = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10))
            background = createRoundedBg("#1F2833", "#00FFFF", 10)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(4))
            layoutParams = lp
            visibility = View.GONE
        }

        val tvPermMsg = TextView(ctx).apply {
            text = "Allow Video Storage Permission to load all device videos"
            setTextColor(Color.WHITE)
            textSize = 12.5f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnGrantNow = TextView(ctx).apply {
            text = "ALLOW"
            setTextColor(Color.BLACK)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
            background = createRoundedBg("#00FFFF", "#00FFFF", 16)
            setOnClickListener {
                (activity as? MainActivity)?.checkAndRequestPermissions()
                requestPermissionsFromFragment()
            }
        }

        banner.addView(tvPermMsg)
        banner.addView(btnGrantNow)
        permissionBanner = banner
        parentLinear.addView(banner, 0)

        // Dynamic PLAYit-style Filter Chips + Sort + Layout Mode + Customize UI Bar
        val controlContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val topActionRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val tvCount = TextView(ctx).apply {
            text = "All Videos (0)"
            setTextColor(Color.parseColor("#CCCCCC"))
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvVideoSummaryCount = tvCount

        val btnSort = createSleekPillButton(ctx, "Sort ⇅") {
            showSortDialog()
        }
        val btnViewToggle = createSleekPillButton(ctx, if (isGridView) "List ☰" else "Grid ⊞") { btn ->
            isGridView = !isGridView
            ctx.getSharedPreferences("MAX_LAYOUT_PREFS", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("video_grid_mode", isGridView)
                .apply()
            btn.text = if (isGridView) "List ☰" else "Grid ⊞"
            applyVideoLayoutManager()
            refreshDisplayedVideos()
        }
        val btnCustomizeUi = createSleekPillButton(ctx, "Customize UI ⚙") {
            showCustomizeVideoLayoutDialog()
        }

        topActionRow.addView(tvCount)
        topActionRow.addView(btnSort)
        topActionRow.addView(btnViewToggle)
        topActionRow.addView(btnCustomizeUi)

        // Filter Chips Horizontal ScrollView
        val chipScroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dpToPx(6)
            layoutParams = lp
        }

        val chipRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val chipNames = listOf("All Videos", "Recent", "HD / Large", "Movies", "WhatsApp / Clips", "Favorites")
        val chipViews = mutableListOf<TextView>()

        chipNames.forEachIndexed { index, title ->
            val chip = TextView(ctx).apply {
                text = title
                textSize = 12f
                setPadding(dpToPx(14), dpToPx(6), dpToPx(14), dpToPx(6))
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.marginEnd = dpToPx(8)
                layoutParams = lp
                setOnClickListener {
                    activeFilterIndex = index
                    updateChipStyles(chipViews)
                    refreshDisplayedVideos()
                }
            }
            chipViews.add(chip)
            chipRow.addView(chip)
        }
        updateChipStyles(chipViews)

        chipScroll.addView(chipRow)
        controlContainer.addView(topActionRow)
        controlContainer.addView(chipScroll)

        dynamicControlHeader = controlContainer
        val insertIndex = (parentLinear.childCount - 1).coerceAtLeast(0)
        parentLinear.addView(controlContainer, insertIndex)
    }

    private fun updateChipStyles(chips: List<TextView>) {
        chips.forEachIndexed { i, tv ->
            if (i == activeFilterIndex) {
                tv.setTextColor(Color.BLACK)
                tv.typeface = Typeface.DEFAULT_BOLD
                tv.background = createRoundedBg("#00FFFF", "#00FFFF", 18)
            } else {
                tv.setTextColor(Color.parseColor("#BBBBBB"))
                tv.typeface = Typeface.DEFAULT
                tv.background = createRoundedBg("#161622", "#2A2A3A", 18)
            }
        }
    }

    private fun createSleekPillButton(
        ctx: Context,
        label: String,
        onClick: (TextView) -> Unit
    ): TextView {
        return TextView(ctx).apply {
            text = label
            setTextColor(Color.parseColor("#00FFFF"))
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dpToPx(10), dpToPx(5), dpToPx(10), dpToPx(5))
            background = createRoundedBg("#14141F", "#00FFFF", 14)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.marginStart = dpToPx(6)
            layoutParams = lp
            setOnClickListener { onClick(this) }
        }
    }

    private fun showSortDialog() {
        val ctx = context ?: return
        val options = arrayOf(
            "Newest First (Date Added)",
            "Title (A to Z)",
            "Size (Largest First)",
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
        val showFilterBar = prefs.getBoolean("show_filter_bar", true)
        val tabsAtBottom = prefs.getBoolean("tabs_at_bottom", false)

        val items = arrayOf(
            if (showHistory) "✓ Show History Bar at Top" else "✗ Hide History Bar at Top",
            if (showFilterBar) "✓ Show Quick Filter & Sort Bar" else "✗ Hide Quick Filter & Sort Bar",
            if (tabsAtBottom) "Move [Video | Folder | Playlist] Tabs to Top" else "Move [Video | Folder | Playlist] Tabs to Bottom",
            "Switch Video List / Grid Mode",
            "Deep Scan All Folders & Unindexed Videos",
            "Reset Video Layout to Default"
        )

        AlertDialog.Builder(ctx)
            .setTitle("Customize Video Section Layout")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        prefs.edit().putBoolean("show_history_section", !showHistory).apply()
                        applyCustomSectionOrder()
                    }
                    1 -> {
                        prefs.edit().putBoolean("show_filter_bar", !showFilterBar).apply()
                        applyCustomSectionOrder()
                    }
                    2 -> {
                        prefs.edit().putBoolean("tabs_at_bottom", !tabsAtBottom).apply()
                        applyCustomSectionOrder()
                    }
                    3 -> {
                        isGridView = !isGridView
                        prefs.edit().putBoolean("video_grid_mode", isGridView).apply()
                        applyVideoLayoutManager()
                        refreshDisplayedVideos()
                    }
                    4 -> {
                        Toast.makeText(ctx, "Deep Scanning Storage...", Toast.LENGTH_SHORT).show()
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
                        applyCustomSectionOrder()
                        refreshDisplayedVideos()
                    }
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun applyCustomSectionOrder() {
        val ctx = context ?: return
        val parent = rootLinearContainer ?: return
        val prefs = ctx.getSharedPreferences("MAX_LAYOUT_PREFS", Context.MODE_PRIVATE)

        val showHistory = prefs.getBoolean("show_history_section", true)
        val showFilterBar = prefs.getBoolean("show_filter_bar", true)
        val tabsAtBottom = prefs.getBoolean("tabs_at_bottom", false)

        val historyContainerId = resources.getIdentifier("llHistoryContainer", "id", ctx.packageName)
        val historyContainer = if (historyContainerId != 0) parent.findViewById<View>(historyContainerId) else null
        historyContainer?.visibility = if (showHistory) View.VISIBLE else View.GONE
        rvHistoryOptional?.visibility = if (showHistory) View.VISIBLE else View.GONE

        dynamicControlHeader?.visibility = if (showFilterBar) View.VISIBLE else View.GONE

        val tabsRow = tvTabVideo.parent as? View
        if (tabsRow != null && tabsRow.parent == parent) {
            parent.removeView(tabsRow)
            if (tabsAtBottom) {
                parent.addView(tabsRow)
            } else {
                val targetIdx = if (historyContainer != null && historyContainer.parent == parent) {
                    parent.indexOfChild(historyContainer) + 1
                } else {
                    1.coerceAtMost(parent.childCount)
                }
                parent.addView(tabsRow, targetIdx)
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

    private fun updatePermissionBannerVisibility() {
        permissionBanner?.visibility = if (hasVideoPermission()) View.GONE else View.VISIBLE
    }

    private fun requestPermissionsFromFragment() {
        val ctx = context ?: return
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        try {
            requestPermissions(perms, 2002)
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
        updatePermissionBannerVisibility()
        loadMediaDataSafely()
    }

    private fun setupTabClicks() {
        val activeColor = Color.parseColor("#00FFFF")
        val inactiveColor = Color.parseColor("#888888")

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
            llPlaylistsEmpty.visibility = View.GONE
        }

        tvTabPlaylist.setOnClickListener {
            currentTab = 2
            tvTabVideo.setTextColor(inactiveColor)
            tvTabFolder.setTextColor(inactiveColor)
            tvTabPlaylist.setTextColor(activeColor)

            showFavoritesAndBookmarksPlaylist()
        }
    }

    private fun refreshDisplayedVideos() {
        if (!isAdded || context == null) return
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)

        val baseFiltered = when (activeFilterIndex) {
            1 -> allVideosList.take(25)
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

        tvVideoSummaryCount?.text = "Videos (${filteredVideosList.size})"

        if (currentTab == 0) {
            videoAdapter = VideoAdapter(ctx, filteredVideosList)
            rvMainVideos.adapter = videoAdapter
            rvMainVideos.visibility = View.VISIBLE
            rvFolders.visibility = View.GONE
            llPlaylistsEmpty.visibility = View.GONE
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

                    updatePermissionBannerVisibility()

                    allVideosList.clear()
                    allVideosList.addAll(fetchedVideos)

                    allFoldersList.clear()
                    allFoldersList.addAll(fetchedFolders)

                    folderAdapter = FolderAdapter(requireContext(), allFoldersList)
                    rvFolders.adapter = folderAdapter

                    rvHistoryOptional?.let { historyRv ->
                        val prefs = requireContext().getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
                        val recentList = allVideosList.sortedByDescending {
                            prefs.getLong("history_time_${it.path}", 0L)
                        }.take(15)
                        historyRv.adapter = VideoAdapter(requireContext(), recentList)
                    }

                    when (currentTab) {
                        0 -> refreshDisplayedVideos()
                        1 -> {
                            rvMainVideos.visibility = View.GONE
                            rvFolders.visibility = View.VISIBLE
                            llPlaylistsEmpty.visibility = View.GONE
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
        val urisToQuery = listOf(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.INTERNAL_CONTENT_URI
        )

        // Stage 1: MediaStore Video External + Internal
        for (uri in urisToQuery) {
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

        // Stage 2: MediaStore.Files Query for MKV / AVI / TS / WebM / Unindexed Video MIME types
        try {
            val filesUri = MediaStore.Files.getContentUri("external")
            val fileProj = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.MIME_TYPE
            )
            val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?"
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

                    val title = if (nameCol != -1) cursor.getString(nameCol) ?: File(path).name else File(path).name
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

        // Stage 3: Direct File System Scan for All Common Video Folders
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

    private fun createRoundedBg(fillHex: String, strokeHex: String, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(radiusDp).toFloat()
            setColor(Color.parseColor(fillHex))
            setStroke(dpToPx(1), Color.parseColor(strokeHex))
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }
}
