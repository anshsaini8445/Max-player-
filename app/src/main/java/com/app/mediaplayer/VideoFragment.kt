package com.app.mediaplayer

import android.Manifest
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

    private lateinit var rvHistory: RecyclerView
    private lateinit var rvMainVideos: RecyclerView
    private lateinit var rvFolders: RecyclerView
    private lateinit var llPlaylistsEmpty: LinearLayout

    private lateinit var tvTabVideo: TextView
    private lateinit var tvTabFolder: TextView
    private lateinit var tvTabPlaylist: TextView

    private lateinit var videoAdapter: VideoAdapter
    private lateinit var historyAdapter: VideoAdapter
    private lateinit var folderAdapter: FolderAdapter

    private val allVideosList = mutableListOf<VideoModel>()
    private val historyVideosList = mutableListOf<VideoModel>()
    private val allFoldersList = mutableListOf<FolderModel>()

    private var isScanning = false

    private val supportedVideoExtensions = setOf(
        "mp4", "mkv", "avi", "mov", "flv", "wmv", "3gp", "webm", "ts", "m4v", "mpeg", "mpg"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_video, container, false)

        rvHistory = view.findViewById(R.id.rvHistory)
        rvMainVideos = view.findViewById(R.id.rvMainVideos)
        rvFolders = view.findViewById(R.id.rvFolders)
        llPlaylistsEmpty = view.findViewById(R.id.llPlaylistsEmpty)

        tvTabVideo = view.findViewById(R.id.tvTabVideo)
        tvTabFolder = view.findViewById(R.id.tvTabFolder)
        tvTabPlaylist = view.findViewById(R.id.tvTabPlaylist)

        rvHistory.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvMainVideos.layoutManager = LinearLayoutManager(requireContext())
        rvFolders.layoutManager = LinearLayoutManager(requireContext())

        videoAdapter = VideoAdapter(requireContext(), allVideosList)
        historyAdapter = VideoAdapter(requireContext(), historyVideosList)
        folderAdapter = FolderAdapter(requireContext(), allFoldersList)

        rvMainVideos.adapter = videoAdapter
        rvHistory.adapter = historyAdapter
        rvFolders.adapter = folderAdapter

        setupTabClicks()
        loadMediaDataSafely()

        return view
    }

    override fun onResume() {
        super.onResume()
        if (hasStoragePermission()) {
            loadMediaDataSafely()
        }
    }

    private fun hasStoragePermission(): Boolean {
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

    private fun setupTabClicks() {
        val activeColor = Color.parseColor("#00FFFF")
        val inactiveColor = Color.parseColor("#888888")

        tvTabVideo.setOnClickListener {
            tvTabVideo.setTextColor(activeColor)
            tvTabFolder.setTextColor(inactiveColor)
            tvTabPlaylist.setTextColor(inactiveColor)

            rvMainVideos.visibility = View.VISIBLE
            rvFolders.visibility = View.GONE
            llPlaylistsEmpty.visibility = View.GONE
        }

        tvTabFolder.setOnClickListener {
            tvTabVideo.setTextColor(inactiveColor)
            tvTabFolder.setTextColor(activeColor)
            tvTabPlaylist.setTextColor(inactiveColor)

            rvMainVideos.visibility = View.GONE
            rvFolders.visibility = View.VISIBLE
            llPlaylistsEmpty.visibility = View.GONE
        }

        tvTabPlaylist.setOnClickListener {
            tvTabVideo.setTextColor(inactiveColor)
            tvTabFolder.setTextColor(inactiveColor)
            tvTabPlaylist.setTextColor(activeColor)

            rvMainVideos.visibility = View.GONE
            rvFolders.visibility = View.GONE
            llPlaylistsEmpty.visibility = View.VISIBLE
        }
    }

    private fun loadMediaDataSafely() {
        if (isScanning || !hasStoragePermission()) return
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

                    historyVideosList.clear()
                    historyVideosList.addAll(fetchedVideos.take(15))

                    allFoldersList.clear()
                    allFoldersList.addAll(fetchedFolders)

                    videoAdapter.notifyDataSetChanged()
                    historyAdapter.notifyDataSetChanged()
                    folderAdapter.notifyDataSetChanged()

                    if (allVideosList.isEmpty()) {
                        Toast.makeText(requireContext(), "No videos found on device", Toast.LENGTH_SHORT).show()
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
        val contentResolver = context?.contentResolver ?: return

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
                    val path = cursor.getString(dataCol) ?: continue
                    val file = File(path)
                    if (!file.exists() || file.length() <= 0L) continue

                    val title = cursor.getString(nameCol) ?: file.name
                    val size = cursor.getLong(sizeCol).coerceAtLeast(file.length())
                    val duration = cursor.getLong(durCol).coerceAtLeast(0L)

                    val parentFile = file.parentFile
                    val bucketId = cursor.getString(bucketIdCol) ?: (parentFile?.absolutePath ?: "0")
                    val bucketName = cursor.getString(bucketNameCol) ?: (parentFile?.name ?: "Internal Storage")

                    if (seenPaths.add(path)) {
                        videoOutput.add(VideoModel(id, title, path, size, duration))
                        if (folderMap.containsKey(bucketId)) {
                            folderMap[bucketId]!!.videoCount++
                        } else {
                            folderMap[bucketId] = FolderModel(bucketId, bucketName, parentFile?.absolutePath ?: path, 1)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

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
