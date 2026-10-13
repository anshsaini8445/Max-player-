package com.app.mediaplayer

import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.concurrent.thread

class VideoFragment : Fragment() {

    private lateinit var rvMainVideos: RecyclerView
    private lateinit var rvFolders: RecyclerView
    private lateinit var llPlaylistsEmpty: LinearLayout
    
    private lateinit var tvTabVideo: TextView
    private lateinit var tvTabFolder: TextView
    private lateinit var tvTabPlaylist: TextView
    
    private lateinit var videoAdapter: VideoAdapter
    private lateinit var folderAdapter: FolderAdapter
    
    private val allVideosList = mutableListOf<VideoModel>()
    private val allFoldersList = mutableListOf<FolderModel>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_video, container, false)
        
        rvMainVideos = view.findViewById(R.id.rvMainVideos)
        rvFolders = view.findViewById(R.id.rvFolders)
        llPlaylistsEmpty = view.findViewById(R.id.llPlaylistsEmpty)
        
        tvTabVideo = view.findViewById(R.id.tvTabVideo)
        tvTabFolder = view.findViewById(R.id.tvTabFolder)
        tvTabPlaylist = view.findViewById(R.id.tvTabPlaylist)

        rvMainVideos.layoutManager = LinearLayoutManager(requireContext())
        rvFolders.layoutManager = LinearLayoutManager(requireContext())

        setupTabClicks()
        loadMediaDataSafely()
        
        return view
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
        thread {
            try {
                fetchVideosAndFolders()
                activity?.runOnUiThread {
                    if (!isAdded || context == null) return@runOnUiThread
                    
                    if (allVideosList.isEmpty()) {
                        Toast.makeText(requireContext(), "No videos found", Toast.LENGTH_SHORT).show()
                    } else {
                        videoAdapter = VideoAdapter(requireContext(), allVideosList)
                        rvMainVideos.adapter = videoAdapter
                        
                        folderAdapter = FolderAdapter(requireContext(), allFoldersList)
                        rvFolders.adapter = folderAdapter
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun fetchVideosAndFolders() {
        allVideosList.clear()
        allFoldersList.clear()
        
        val folderMap = mutableMapOf<String, FolderModel>()
        
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
                val path = cursor.getString(dataCol) ?: ""
                val size = cursor.getLong(sizeCol)
                val duration = cursor.getLong(durCol)
                
                val bucketId = cursor.getString(bucketIdCol) ?: "0"
                val bucketName = cursor.getString(bucketNameCol) ?: "Internal Storage"

                allVideosList.add(VideoModel(id, title, path, size, duration))

                if (folderMap.containsKey(bucketId)) {
                    folderMap[bucketId]!!.videoCount++
                } else {
                    folderMap[bucketId] = FolderModel(bucketId, bucketName, path, 1)
                }
            }
        }
        allFoldersList.addAll(folderMap.values.sortedBy { it.folderName })
    }
}
