package com.app.mediaplayer

import android.content.ContentUris
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.Locale
import kotlin.concurrent.thread

class FolderVideosActivity : AppCompatActivity() {

    private lateinit var rvFolderSpecificVideos: RecyclerView
    private lateinit var videoAdapter: VideoAdapter
    private val folderVideosList = mutableListOf<VideoModel>()

    private val supportedVideoExtensions = setOf(
        "mp4", "mkv", "avi", "mov", "flv", "wmv", "3gp", "webm", "ts", "m4v", "mpeg", "mpg"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folder_videos)

        val bucketId = intent.getStringExtra("BUCKET_ID") ?: ""
        val folderName = intent.getStringExtra("FOLDER_NAME") ?: "Folder"
        val folderPath = intent.getStringExtra("FOLDER_PATH") ?: ""

        findViewById<TextView>(R.id.tvFolderTitleTop).text = folderName
        findViewById<ImageView>(R.id.btnBackFromFolder).setOnClickListener { finish() }

        rvFolderSpecificVideos = findViewById(R.id.rvFolderSpecificVideos)
        rvFolderSpecificVideos.layoutManager = LinearLayoutManager(this)
        videoAdapter = VideoAdapter(this, folderVideosList)
        rvFolderSpecificVideos.adapter = videoAdapter

        loadVideosForFolder(bucketId, folderPath)
    }

    private fun loadVideosForFolder(bucketId: String, folderPath: String) {
        thread {
            try {
                val videos = fetchVideosByBucketOrPath(bucketId, folderPath)
                runOnUiThread {
                    folderVideosList.clear()
                    folderVideosList.addAll(videos)
                    videoAdapter.notifyDataSetChanged()

                    if (folderVideosList.isEmpty()) {
                        Toast.makeText(this, "No videos found in this folder", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun fetchVideosByBucketOrPath(bucketId: String, folderPath: String): List<VideoModel> {
        val videos = mutableListOf<VideoModel>()
        val seenPaths = HashSet<String>()
        var detectedFolderDir: File? = if (folderPath.isNotEmpty()) {
            val f = File(folderPath)
            if (f.isDirectory) f else f.parentFile
        } else {
            null
        }

        if (detectedFolderDir == null && bucketId.startsWith("/")) {
            val possibleDir = File(bucketId)
            if (possibleDir.exists()) {
                detectedFolderDir = if (possibleDir.isDirectory) possibleDir else possibleDir.parentFile
            }
        }

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

        try {
            contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = if (idCol != -1) cursor.getLong(idCol) else 0L
                    val rawPath = if (dataCol != -1) cursor.getString(dataCol) else null
                    val path = if (!rawPath.isNullOrEmpty()) {
                        rawPath
                    } else {
                        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id).toString()
                    }

                    if (detectedFolderDir == null && !rawPath.isNullOrEmpty()) {
                        detectedFolderDir = File(rawPath).parentFile
                    }

                    val title = if (nameCol != -1) cursor.getString(nameCol) ?: "Video_$id" else "Video_$id"
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L
                    val duration = if (durCol != -1) cursor.getLong(durCol).coerceAtLeast(0L) else 0L

                    if (seenPaths.add(path)) {
                        videos.add(VideoModel(id, title, path, size, duration))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val dir = detectedFolderDir
            if (dir != null && dir.exists() && dir.isDirectory) {
                val files = dir.listFiles()
                if (files != null) {
                    for (file in files) {
                        if (file.isFile && !file.name.startsWith(".") && file.length() > 10240L) {
                            val ext = file.extension.lowercase(Locale.getDefault())
                            if (supportedVideoExtensions.contains(ext)) {
                                val path = file.absolutePath
                                if (seenPaths.add(path)) {
                                    videos.add(
                                        VideoModel(
                                            path.hashCode().toLong(),
                                            file.name,
                                            path,
                                            file.length(),
                                            0L
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return videos
    }
}
