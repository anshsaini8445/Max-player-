package com.app.mediaplayer

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
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
    private lateinit var btnVideoMoreMenu: ImageView

    private lateinit var llHistoryContainer: LinearLayout
    private lateinit var rvHistory: RecyclerView
    private lateinit var tvClearHistory: TextView

    private lateinit var videoAdapter: VideoAdapter
    private lateinit var folderAdapter: FolderAdapter

    private val allVideosList = mutableListOf<VideoModel>()
    private val displayedVideosList = mutableListOf<VideoModel>()
    private val allFoldersList = mutableListOf<FolderModel>()

    private var isScanning = false
    private var currentTab = 0
    private var sortMode = 0
    private var isGridMode = false

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
        btnVideoMoreMenu = view.findViewById(R.id.btnVideoMoreMenu)

        llHistoryContainer = view.findViewById(R.id.llHistoryContainer)
        rvHistory = view.findViewById(R.id.rvHistory)
        tvClearHistory = view.findViewById(R.id.tvClearHistory)

        val prefs = requireContext().getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        isGridMode = prefs.getBoolean("video_grid_mode", false)
        sortMode = prefs.getInt("video_sort_mode", 0)

        applyLayoutManager()
        rvFolders.layoutManager = LinearLayoutManager(requireContext())
        rvHistory.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        videoAdapter = VideoAdapter(requireContext(), displayedVideosList)
        rvMainVideos.adapter = videoAdapter

        folderAdapter = FolderAdapter(requireContext(), allFoldersList)
        rvFolders.adapter = folderAdapter

        setupClicks()
        checkPermissionAndLoad()

        return view
    }

    override fun onResume() {
        super.onResume()
        loadMediaFast()
    }

    private fun applyLayoutManager() {
        val ctx = context ?: return
        if (isGridMode) {
            rvMainVideos.layoutManager = GridLayoutManager(ctx, 2)
        } else {
            rvMainVideos.layoutManager = LinearLayoutManager(ctx)
        }
    }

    private fun checkPermissionAndLoad() {
        val ctx = context ?: return
        val hasPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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

        if (!hasPerm) {
            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            requestPermissions(perms, 2001)
        }
        loadMediaFast()
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        loadMediaFast()
    }

    private fun setupClicks() {
        val activeColor = Color.parseColor("#00FFFF")
        val inactiveColor = Color.parseColor("#888888")

        tvTabVideo.setOnClickListener {
            currentTab = 0
            tvTabVideo.setTextColor(activeColor)
            tvTabFolder.setTextColor(inactiveColor)
            tvTabPlaylist.setTextColor(inactiveColor)
            refreshUI()
        }

        tvTabFolder.setOnClickListener {
            currentTab = 1
            tvTabVideo.setTextColor(inactiveColor)
            tvTabFolder.setTextColor(activeColor)
            tvTabPlaylist.setTextColor(inactiveColor)
            refreshUI()
        }

        tvTabPlaylist.setOnClickListener {
            currentTab = 2
            tvTabVideo.setTextColor(inactiveColor)
            tvTabFolder.setTextColor(inactiveColor)
            tvTabPlaylist.setTextColor(activeColor)
            refreshUI()
        }

        tvClearHistory.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            val prefs = ctx.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            for (video in allVideosList) {
                editor.remove("history_time_${video.path}")
            }
            editor.apply()
            llHistoryContainer.visibility = View.GONE
            Toast.makeText(ctx, "History Cleared", Toast.LENGTH_SHORT).show()
        }

        btnVideoMoreMenu.setOnClickListener { anchor ->
            val ctx = context ?: return@setOnClickListener
            val popup = PopupMenu(ctx, anchor)
            popup.menu.add(0, 1, 0, if (isGridMode) "Switch to List View" else "Switch to Grid View")
            popup.menu.add(0, 2, 1, "Sort: Newest First")
            popup.menu.add(0, 3, 2, "Sort: Name (A to Z)")
            popup.menu.add(0, 4, 3, "Sort: Largest Size")
            popup.menu.add(0, 5, 4, "Sort: Longest Duration")
            popup.menu.add(0, 6, 5, "Refresh Videos")

            popup.setOnMenuItemClickListener { item ->
                val prefs = ctx.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
                when (item.itemId) {
                    1 -> {
                        isGridMode = !isGridMode
                        prefs.edit().putBoolean("video_grid_mode", isGridMode).apply()
                        applyLayoutManager()
                        videoAdapter.notifyDataSetChanged()
                        true
                    }
                    2, 3, 4, 5 -> {
                        sortMode = item.itemId - 2
                        prefs.edit().putInt("video_sort_mode", sortMode).apply()
                        refreshUI()
                        true
                    }
                    6 -> {
                        loadMediaFast()
                        Toast.makeText(ctx, "Refreshing...", Toast.LENGTH_SHORT).show()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    fun loadMediaFast() {
        if (isScanning) return
        isScanning = true

        thread {
            try {
                val videos = mutableListOf<VideoModel>()
                val folders = mutableListOf<FolderModel>()
                queryMediaStoreInstant(videos, folders)

                activity?.runOnUiThread {
                    isScanning = false
                    if (!isAdded || context == null) return@runOnUiThread

                    allVideosList.clear()
                    allVideosList.addAll(videos)

                    allFoldersList.clear()
                    allFoldersList.addAll(folders)

                    refreshHistorySection()
                    refreshUI()
                }
            } catch (e: Exception) {
                isScanning = false
                e.printStackTrace()
            }
        }
    }

    private fun refreshHistorySection() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        val watchedVideos = allVideosList.filter {
            prefs.getLong("history_time_${it.path}", 0L) > 0L
        }.sortedByDescending {
            prefs.getLong("history_time_${it.path}", 0L)
        }.take(12)

        if (watchedVideos.isEmpty()) {
            llHistoryContainer.visibility = View.GONE
        } else {
            llHistoryContainer.visibility = View.VISIBLE
            rvHistory.adapter = VideoAdapter(ctx, watchedVideos)
        }
    }

    private fun refreshUI() {
        if (!isAdded || context == null) return
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)

        when (currentTab) {
            0 -> {
                val sorted = when (sortMode) {
                    1 -> allVideosList.sortedBy { it.title.lowercase(Locale.getDefault()) }
                    2 -> allVideosList.sortedByDescending { it.size }
                    3 -> allVideosList.sortedByDescending { it.duration }
                    else -> allVideosList
                }
                displayedVideosList.clear()
                displayedVideosList.addAll(sorted)
                videoAdapter.notifyDataSetChanged()

                rvFolders.visibility = View.GONE
                if (displayedVideosList.isEmpty()) {
                    rvMainVideos.visibility = View.GONE
                    llPlaylistsEmpty.visibility = View.VISIBLE
                } else {
                    llPlaylistsEmpty.visibility = View.GONE
                    rvMainVideos.visibility = View.VISIBLE
                }
            }
            1 -> {
                folderAdapter.notifyDataSetChanged()
                rvMainVideos.visibility = View.GONE
                if (allFoldersList.isEmpty()) {
                    rvFolders.visibility = View.GONE
                    llPlaylistsEmpty.visibility = View.VISIBLE
                } else {
                    llPlaylistsEmpty.visibility = View.GONE
                    rvFolders.visibility = View.VISIBLE
                }
            }
            2 -> {
                val favVideos = allVideosList.filter { video ->
                    prefs.getBoolean("fav_${video.path}", false) ||
                        prefs.getLong("bookmark_${video.path}", -1L) != -1L
                }
                displayedVideosList.clear()
                displayedVideosList.addAll(favVideos)
                videoAdapter.notifyDataSetChanged()

                rvFolders.visibility = View.GONE
                if (displayedVideosList.isEmpty()) {
                    rvMainVideos.visibility = View.GONE
                    llPlaylistsEmpty.visibility = View.VISIBLE
                } else {
                    llPlaylistsEmpty.visibility = View.GONE
                    rvMainVideos.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun queryMediaStoreInstant(
        videoOutput: MutableList<VideoModel>,
        folderOutput: MutableList<FolderModel>
    ) {
        val resolver = context?.contentResolver ?: return
        val folderMap = mutableMapOf<String, FolderModel>()
        val seenPaths = HashSet<String>()

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

        try {
            resolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
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
                    val path = if (!rawPath.isNullOrEmpty()) {
                        rawPath
                    } else {
                        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id).toString()
                    }

                    val title = if (nameCol != -1) {
                        cursor.getString(nameCol) ?: File(path).name
                    } else {
                        File(path).name
                    }
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L
                    val duration = if (durCol != -1) cursor.getLong(durCol).coerceAtLeast(0L) else 0L
                    val bucketId = if (bucketIdCol != -1) cursor.getString(bucketIdCol) ?: "0" else "0"
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

        folderOutput.addAll(folderMap.values.sortedBy { it.folderName.lowercase(Locale.getDefault()) })
    }
}
