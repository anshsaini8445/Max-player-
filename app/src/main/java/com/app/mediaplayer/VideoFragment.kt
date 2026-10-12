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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_video, container, false)
        
        val rvMainVideos = view.findViewById<RecyclerView>(R.id.rvMainVideos)
        rvMainVideos.layoutManager = LinearLayoutManager(requireContext())
        
        // Fetch videos in background to prevent UI freezing (Better than PLAYit)
        thread {
            val videoList = fetchAllVideos()
            requireActivity().runOnUiThread {
                if (videoList.isEmpty()) {
                    Toast.makeText(requireContext(), "No videos found", Toast.LENGTH_SHORT).show()
                } else {
                    // rvMainVideos.adapter = VideoAdapter(videoList) // Next step: Create VideoAdapter
                    Toast.makeText(requireContext(), "Found ${videoList.size} High-Res Videos", Toast.LENGTH_SHORT).show()
                }
            }
        }
        return view
    }

    private fun fetchAllVideos(): List<String> {
        val videos = mutableListOf<String>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION
        )
        
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"
        
        requireContext().contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameColumn)
                videos.add(name)
            }
        }
        return videos
    }
}
