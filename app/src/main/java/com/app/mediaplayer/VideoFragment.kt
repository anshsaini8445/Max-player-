package com.app.mediaplayer

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
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

    private lateinit var videoAdapter: VideoAdapter
    private lateinit var folderAdapter: FolderAdapter

    private val allVideosList = mutableListOf<VideoModel>()
    private val allFoldersList = mutableListOf<FolderModel>()

    private var isScanning = false
    private var currentTab = 0

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

        rvMainVideos.layoutManager = LinearLayoutManager(requireContext())
        rvFolders.layoutManager = LinearLayoutManager(requireContext())

        val historyId = resources.getIdentifier("rvHistory", "id", requireContext().packageName)
        if (historyId != 0) {
            rvHistoryOptional = view.findViewById(historyId)
            rvHistoryOptional?.layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        }

        setupTabClicks()
        ensurePermissionsAndLoad()

        return view
    }

    override fun onResume() {
        super.onResume()
        loadMediaDataSafely()
    }

    private fun ensurePermissionsAndLoad() {
        val ctx = context ?: return
        val missingPermissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_MEDIA_VIDEO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                missingPermissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                missingPermissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                missingPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissions(missingPermissions.toTypedArray(), 2001)
        }
        loadMediaDataSafely()
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2001) {
            loadMediaDataSafely()
        }
    }

    private fun setupTabClicks() {
        val activeColor = Color.parseColor("#00FFFF")
        val inactiveColor = Color.parseColor("#888888")

        tvTabVideo.setOnClickListener {
            currentTab = 0
            tvTabVideo.setTextColor(activeColor)
            tvTabFolder.setTextColor(inactiveColor)
            tvTabPlaylist.setTextColor(inactiveColor)

            if (isAdded && context != null) {
                videoAdapter = VideoAdapter(requireContext(), allVideosList)
                rvMainVideos.adapter = videoAdapter
            }

            rvMainVideos.visibility = View.VISIBLE
            rvFolders.visibility = View.GONE
            llPlaylistsEmpty.visibility = View.GONE
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
                fetchVideosAndFolders(fetchedVideos, fetchedFolders)

                activity?.runOnUiThread {
                    isScanning = false
                    if (!isAdded || context == null) return@runOnUiThread

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
                        0 -> {
                            videoAdapter = VideoAdapter(requireContext(), allVideosList)
                            rvMainVideos.adapter = videoAdapter
                            rvMainVideos.visibility = View.VISIBLE
                            rvFolders.visibility = View.GONE
                            llPlaylistsEmpty.visibility = View.GONE
                        }
                        1 -> {
                            rvMainVideos.visibility = View.GONE
                            rvFolders.visibility = View.VISIBLE
                            llPlaylistsEmpty.visibility = View.GONE
                        }
                        2 -> {
                            showFavoritesAndBookmarksPlaylist()
                        }
                    }

                    if (allVideosList.isEmpty()) {
                        Toast.makeText(requireContext(), "No videos found", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                isScanning = false
                e.printStackTrace()
            }
        }
    }

    private fun fetchVideosAndFolders(
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

        // 1. Standard MediaStore Video Query (Exactly like the original working query)
        try {
            contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(nameCol) ?: "Unknown"
                    val rawPath = cursor.getString(dataCol)
                    val path = if (!rawPath.isNullOrEmpty()) {
                        rawPath
                    } else {
                        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id).toString()
                    }
                    val size = cursor.getLong(sizeCol)
                    val duration = cursor.getLong(durCol)

                    val bucketId = cursor.getString(bucketIdCol) ?: "0"
                    val bucketName = cursor.getString(bucketNameCol) ?: "Internal Storage"

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

        // 2. Fallback: Direct File System Scan for unindexed MKV, AVI, TS, MP4 files
        try {
            val rootDir = Environment.getExternalStorageDirectory()
            if (rootDir != null && rootDir.exists()) {
                scanDirectoryForUnindexedVideos(rootDir, seenPaths, videoOutput, folderMap)
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
        folderMap: MutableMap<String, FolderModel>
    ) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.name.startsWith(".")) continue
            if (file.isDirectory) {
                val lowerName = file.name.lowercase(Locale.getDefault())
                if (lowerName == "android" || lowerName == "data" || lowerName == "obb") continue
                scanDirectoryForUnindexedVideos(file, seenPaths, videoOutput, folderMap)
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
