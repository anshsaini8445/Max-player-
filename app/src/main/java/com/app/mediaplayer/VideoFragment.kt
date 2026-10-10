package com.app.mediaplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale
import java.util.concurrent.TimeUnit

class VideoFragment : Fragment() {

    private lateinit
     var rvHistory: RecyclerView
    private lateinit var rvVideo: RecyclerView
    private val videoList = mutableListOf<Video>()
    private lateinit var videoAdapter: VideoAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?):
             View? {
        return inflater.inflate(R.layout.fragment_video, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvHistory = view.findViewById(R.id.rvHistory)
        rvVideo = view.findViewById(R.id.rvVideo)

        rvHistory.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvVideo.layoutManager = LinearLayoutManager(requireContext())

        videoAdapter = VideoAdapter(videoList) { video ->
            val intent = Intent(requireContext(), PlayerActivity::class.java)
            intent.putExtra("VIDEO_PATH", video.uri.toString())
            startActivity(intent)
        }
        rvVideo.adapter = videoAdapter

        // Set history adapter with same list for UI completeness
        rvHistory.adapter = HistoryAdapter(videoList) { video ->
            val intent = Intent(requireContext(), PlayerActivity::class.java)
            intent.putExtra("VIDEO_PATH", video.uri.toString())
            startActivity(intent)
        }

        checkPermissionAndLoadVideos()
    }

    private fun checkPermissionAndLoadVideos() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
            loadVideos()
        } else {
            requestPermissions(arrayOf(permission), 102)
        }
    }

    private fun loadVideos() {
        videoList.clear()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE
        )

        val cursor = requireContext().contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            MediaStore.Video.Media.DATE_ADDED + " DESC"
        )

        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val titleCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val dataCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val durationCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val title = it.getString(titleCol) ?: "Unknown"
                val path = it.getString(dataCol) ?: ""
                val uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())
                val duration = it.getLong(durationCol)
                val size = it.getLong(sizeCol)

                videoList.add(Video(id, title, path, uri, duration, size))
            }
        }
        videoAdapter.notifyDataSetChanged()
        rvHistory.adapter?.notifyDataSetChanged()
    }

    class HistoryAdapter(private val list: List<Video>, private val onClick: (Video) -> Unit) :
        RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

        class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivThumb: ImageView = view.findViewById(R.id.ivHistoryThumb)
            val tvTitle: TextView = view.findViewById(R.id.tvHistoryTitle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int):
                 HistoryViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
            return HistoryViewHolder(v)
        }

        override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
            val video = list[position]
            holder.tvTitle.text = video.title
            holder.ivThumb.setImageURI(video.uri)
            holder.itemView.setOnClickListener { onClick(video) }
        }

        override fun getItemCount() = if (list.size > 5) 5 else list.size
    }
}