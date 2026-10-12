package com.app.mediaplayer

import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.concurrent.thread

class VideoFragment : Fragment() {

    private lateinit var rvMainVideos: RecyclerView
    private lateinit var videoAdapter: VideoAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_video, container, false)
        rvMainVideos = view.findViewById(R.id.rvMainVideos)
        rvMainVideos.layoutManager = LinearLayoutManager(requireContext())
        loadVideosSafely()
        return view
    }

    private fun loadVideosSafely() {
        thread {
            try {
                val videoList = fetchAllVideos()
                activity?.runOnUiThread {
                    if (!isAdded || context == null) return@runOnUiThread
                    
                    if (videoList.isEmpty()) {
                        Toast.makeText(requireContext(), "No videos found", Toast.LENGTH_SHORT).show()
                    } else {
                        videoAdapter = VideoAdapter(requireContext(), videoList)
                        rvMainVideos.adapter = videoAdapter
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun fetchAllVideos(): List<VideoModel> {
        val videos = mutableListOf<VideoModel>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION
        )
        
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"
        val contentResolver = context?.contentResolver ?: return videos
        
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

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(nameCol) ?: "Unknown"
                val path = cursor.getString(dataCol) ?: ""
                val size = cursor.getLong(sizeCol)
                val duration = cursor.getLong(durCol)
                videos.add(VideoModel(id, title, path, size, duration))
            }
        }
        return videos
    }
}
