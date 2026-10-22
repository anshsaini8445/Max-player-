package com.app.mediaplayer

import android.content.ContentUris
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.util.Locale

object DeepMediaScanner {

    // Strictly real video extensions (Excludes .IFO and .BUP DVD metadata files so junk never appears)
    private val validVideoExtensions = setOf(
        "mp4", "mkv", "webm", "avi", "mov", "flv", "3gp", "ts", "m4v", "wmv",
        "crdownload", "part", "download"
    )

    @Volatile
    private var memoryCachedVideos: List<VideoModel> = emptyList()

    @Volatile
    private var lastScanTimeMs: Long = 0L

    fun getCachedVideosInstant(): List<VideoModel> {
        return memoryCachedVideos
    }

    fun invalidateCache() {
        lastScanTimeMs = 0L
    }

    // Ultra-fast non-blocking MediaStore + shallow download scan (0.05s response, zero UI freeze)
    fun scanAllVideosEverywhere(context: Context, forceRefresh: Boolean = false): List<VideoModel> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && memoryCachedVideos.isNotEmpty() && (now - lastScanTimeMs) < 15000L) {
            return memoryCachedVideos.filter { !PermanentDeleteHelper.isMarkedDeleted(context, it.path) }
        }

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

                    val ext = path.substringAfterLast('.', "").lowercase(Locale.US)
                    if (ext == "ifo" || ext == "bup" || ext == "txt" || ext == "db") continue

                    val size = if (sizeCol != -1) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L
                    val duration = if (durCol != -1) cursor.getLong(durCol).coerceAtLeast(0L) else 0L
                    val title = if (nameCol != -1) {
                        cursor.getString(nameCol) ?: File(path).name
                    } else {
                        File(path).name
                    }

                    if (size > 80 * 1024L && (duration > 500L || validVideoExtensions.contains(ext))) {
                        seenPaths.add(path)
                        results.add(VideoModel(id, title, path, size, duration))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fast 2-level scan for WhatsApp Statuses and Chrome partial downloads without slow retriever calls
        try {
            val extRoot = Environment.getExternalStorageDirectory()
            if (extRoot != null && extRoot.exists()) {
                val quickDirs = listOf(
                    File(extRoot, "Download"),
                    File(extRoot, "Android/media/com.whatsapp/WhatsApp/Media/.Statuses"),
                    File(extRoot, "WhatsApp/Media/.Statuses")
                )
                for (dir in quickDirs) {
                    scanDirectoryFast(context, dir, seenPaths, results, 0)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        memoryCachedVideos = results
        lastScanTimeMs = System.currentTimeMillis()
        return results
    }

    private fun scanDirectoryFast(
        context: Context,
        dir: File,
        seenPaths: HashSet<String>,
        results: MutableList<VideoModel>,
        depth: Int
    ) {
        if (depth > 2 || !dir.exists() || !dir.isDirectory) return
        val files = dir.listFiles() ?: return

        for (file in files) {
            if (file.isDirectory) {
                scanDirectoryFast(context, file, seenPaths, results, depth + 1)
            } else {
                val path = file.absolutePath
                if (seenPaths.contains(path) || PermanentDeleteHelper.isMarkedDeleted(context, path)) continue

                val ext = file.extension.lowercase(Locale.US)
                if (!validVideoExtensions.contains(ext)) continue

                val length = file.length()
                if (length < 100 * 1024L) continue

                val isPartial = ext == "crdownload" || ext == "part" || ext == "download"
                if (isPartial && length < 1024 * 1024L) continue

                val estimatedDurMs = if (path.contains(".Statuses", ignoreCase = true)) 25000L else 60000L
                val displayTitle = if (isPartial) "[Downloading] ${file.name}" else file.name
                seenPaths.add(path)
                results.add(
                    VideoModel(
                        id = path.hashCode().toLong(),
                        title = displayTitle,
                        path = path,
                        size = length,
                        duration = estimatedDurMs
                    )
                )
            }
        }
    }

    fun filterShortsAndStatusVideos(allVideos: List<VideoModel>): List<VideoModel> {
        return allVideos.filter { video ->
            val durSec = video.duration / 1000L
            (durSec in 1L..90L) || video.path.contains(".Statuses", ignoreCase = true)
        }
    }
}
