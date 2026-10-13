package com.app.mediaplayer

import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.concurrent.thread

class FolderVideosActivity : AppCompatActivity() {

    private lateinit var rvFolderSpecificVideos: RecyclerView
    private lateinit var videoAdapter: VideoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folder_videos)

        val bucketId = intent.getStringExtra("BUCKET_ID") ?: return
        val folderName = intent.getStringExtra("FOLDER_NAME") ?: "Folder"

        findViewById<TextView>(R.id.tvFolderTitleTop).text = folderName
        findViewById<ImageView>(R.id.btnBackFromFolder).setOnClickListener { finish() }

        rvFolderSpecificVideos = findViewById(R.id.rvFolderSpecificVideos)
        rvFolderSpecificVideos.layoutManager = LinearLayoutManager(this)

        loadVideosForFolder(bucketId)
    }

    private fun loadVideosForFolder(bucketId: String) {
        thread {
            try {
                val videos = fetchVideosByBucketId(bucketId)
                runOnUiThread {
                    if (videos.isEmpty()) {
                        Toast.makeText(this, "No videos found in this folder", Toast.LENGTH_SHORT).show()
                    } else {
                        videoAdapter = VideoAdapter(this, videos)
                        rvFolderSpecificVideos.adapter = videoAdapter
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun fetchVideosByBucketId(bucketId: String): List<VideoModel> {
        val videos = mutableListOf<VideoModel>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION
        )
        val selection = "${MediaStore.Video.Media.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(bucketId)
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
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
