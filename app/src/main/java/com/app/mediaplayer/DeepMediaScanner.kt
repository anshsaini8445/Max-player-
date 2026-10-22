package com.app.mediaplayer

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.util.Locale

object DeepMediaScanner {

    private val videoExtensions = setOf(
        "mp4", "mkv", "webm", "avi", "mov", "flv", "3gp", "ts", "m4v",
        "crdownload", "part", "download"
    )

    // Scans MediaStore + All Folders + In-Progress Browser Downloads (e.g., 10MB downloaded of 1000MB file)
    fun scanAllVideosEverywhere(context: Context): List<VideoModel> {
        val results = mutableListOf<VideoModel>()
        val seenPaths = HashSet<String>()

        try {
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DURATION
            )
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
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

                    if (PermanentDeleteHelper.isMarkedDeleted(context, path)) continue
                    val file = File(path)
                    val diskSize = if (file.exists()) file.length() else 0L
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol).coerceAtLeast(diskSize) else diskSize
                    val duration = if (durCol != -1) cursor.getLong(durCol).coerceAtLeast(0L) else 0L
                    val title = if (nameCol != -1) cursor.getString(nameCol) ?: file.name else file.name

                    if (size > 64 * 1024L) {
                        seenPaths.add(path)
                        results.add(VideoModel(id, title, path, size, duration))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Deep folder crawl for unindexed folders, WhatsApp statuses, Telegram & Chrome partial downloads
        try {
            val rootsToScan = mutableListOf<File>()
            val extRoot = Environment.getExternalStorageDirectory()
            if (extRoot != null && extRoot.exists()) {
                rootsToScan.add(File(extRoot, "Download"))
                rootsToScan.add(File(extRoot, "Movies"))
                rootsToScan.add(File(extRoot, "DCIM"))
                rootsToScan.add(File(extRoot, "WhatsApp/Media/.Statuses"))
                rootsToScan.add(File(extRoot, "Android/media/com.whatsapp/WhatsApp/Media/.Statuses"))
                rootsToScan.add(File(extRoot, "Pictures"))
                rootsToScan.add(File(extRoot, "Telegram"))
            }

            for (dir in rootsToScan) {
                scanDirectoryRecursively(context, dir, seenPaths, results, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return results
    }

    private fun scanDirectoryRecursively(
        context: Context,
        dir: File,
        seenPaths: HashSet<String>,
        results: MutableList<VideoModel>,
        depth: Int
    ) {
        if (depth > 4 || !dir.exists() || !dir.isDirectory) return
        val files = dir.listFiles() ?: return

        for (file in files) {
            if (file.isDirectory) {
                scanDirectoryRecursively(context, file, seenPaths, results, depth + 1)
            } else {
                val path = file.absolutePath
                if (seenPaths.contains(path) || PermanentDeleteHelper.isMarkedDeleted(context, path)) continue

                val ext = file.extension.lowercase(Locale.US)
                if (!videoExtensions.contains(ext)) continue

                val length = file.length()
                // Include partial downloads (>= 1 MB) or complete short clips (>= 100 KB)
                if (length < 100 * 1024L) continue

                val isPartial = ext == "crdownload" || ext == "part" || ext == "download"
                if (isPartial && length < 1024 * 1024L) continue

                var durMs = 0L
                if (!isPartial) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(path)
                        durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    } catch (_: Exception) {
                    } finally {
                        try {
                            retriever.release()
                        } catch (_: Exception) {
                        }
                    }
                }

                val displayTitle = if (isPartial) "[Downloading] ${file.name}" else file.name
                seenPaths.add(path)
                results.add(
                    VideoModel(
                        id = path.hashCode().toLong(),
                        title = displayTitle,
                        path = path,
                        size = length,
                        duration = durMs
                    )
                )
            }
        }
    }

    // Filters all Shorts, Reels, and Status videos between 1 second and 90 seconds (1:30 min)
    fun filterShortsAndStatusVideos(allVideos: List<VideoModel>): List<VideoModel> {
        return allVideos.filter { video ->
            val durSec = video.duration / 1000L
            (durSec in 1L..90L) || video.path.contains(".Statuses", ignoreCase = true)
        }
    }
}
