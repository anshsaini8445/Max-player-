package com.app.mediaplayer

import android.content.Context
import android.provider.MediaStore
import java.io.File
import java.util.Locale

object DeepMediaScanner {

    private const val SNAPSHOT_PREF = "HYBRID_INSTANT_VIDEO_SNAPSHOT"
    private const val KEY_SNAPSHOT_DATA = "instant_video_rows_v4"

    @Volatile
    private var memoryCache: List<VideoModel> = emptyList()

    @Volatile
    private var lastScanTimestampMs: Long = 0L

    fun getCachedVideosInstant(context: Context? = null): List<VideoModel> {
        if (memoryCache.isNotEmpty()) {
            return memoryCache
        }
        if (context != null) {
            val restored = loadInstantSnapshotFromDisk(context)
            if (restored.isNotEmpty()) {
                memoryCache = restored
                return restored
            }
        }
        return memoryCache
    }

    fun invalidateCache() {
        memoryCache = emptyList()
        lastScanTimestampMs = 0L
    }

    fun removeDeletedPathsFromCache(context: Context, deletedPaths: Collection<String>) {
        val deleteSet = deletedPaths.toHashSet()
        memoryCache = memoryCache.filterNot { deleteSet.contains(it.path) }
        saveInstantSnapshotToDisk(context, memoryCache)
    }

    fun filterShortsAndStatusVideos(videos: List<VideoModel>): List<VideoModel> {
        val filtered = videos.filter { video ->
            val durationMs = if (video.duration in 1L..3600L) video.duration * 1000L else video.duration
            val lowerPath = video.path.lowercase(Locale.US)
            val lowerTitle = video.title.lowercase(Locale.US)
            (durationMs in 1000L..180_000L) ||
                lowerPath.contains(".statuses") ||
                lowerPath.contains("whatsapp") ||
                lowerPath.contains("status") ||
                lowerPath.contains("shorts") ||
                lowerPath.contains("reels") ||
                lowerPath.contains("instagram") ||
                lowerPath.contains("snapchat") ||
                lowerTitle.contains("shorts") ||
                lowerTitle.contains("reel") ||
                lowerTitle.contains("status")
        }
        return if (filtered.isNotEmpty()) filtered else videos
    }

    fun filterShortsAndStatusVideos(context: Context): List<VideoModel> {
        val allVideos = scanAllVideosEverywhere(context, forceRefresh = false)
        return filterShortsAndStatusVideos(allVideos)
    }

    fun filterShortsAndStatusVideos(context: Context, videos: List<VideoModel>): List<VideoModel> {
        val source = videos.ifEmpty { scanAllVideosEverywhere(context, forceRefresh = false) }
        return filterShortsAndStatusVideos(source)
    }

    fun scanAllVideosEverywhere(
        context: Context,
        forceRefresh: Boolean = false
    ): List<VideoModel> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && memoryCache.isNotEmpty() && (now - lastScanTimestampMs) < 15000L) {
            return memoryCache
        }

        if (!forceRefresh && memoryCache.isEmpty()) {
            val diskSnapshot = loadInstantSnapshotFromDisk(context)
            if (diskSnapshot.isNotEmpty()) {
                memoryCache = diskSnapshot
            }
        }

        val results = ArrayList<VideoModel>(256)
        val seenPaths = HashSet<String>(256)

        try {
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE
            )

            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val titleCol = cursor.getColumnIndex(MediaStore.Video.Media.TITLE)
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val path = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                    if (path.isEmpty() || !seenPaths.add(path)) continue
                    if (PermanentDeleteHelper.isMarkedDeleted(context, path)) continue

                    val fileObj = File(path)
                    if (!fileObj.exists() || fileObj.length() < 1024L) continue

                    val rawTitle = (if (nameCol != -1) cursor.getString(nameCol) else null)
                        ?: (if (titleCol != -1) cursor.getString(titleCol) else null)
                        ?: fileObj.name

                    val durationMs = if (durCol != -1) cursor.getLong(durCol).coerceAtLeast(0L) else 0L
                    val sizeBytes = if (sizeCol != -1) {
                        val s = cursor.getLong(sizeCol)
                        if (s > 0L) s else fileObj.length()
                    } else {
                        fileObj.length()
                    }

                    results.add(
                        VideoModel(
                            id = id,
                            title = rawTitle,
                            duration = durationMs,
                            size = sizeBytes,
                            path = path
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        memoryCache = results
        lastScanTimestampMs = System.currentTimeMillis()
        saveInstantSnapshotToDisk(context, results)
        return results
    }

    private fun saveInstantSnapshotToDisk(context: Context, list: List<VideoModel>) {
        try {
            val sb = StringBuilder()
            val limit = list.size.coerceAtMost(400)
            for (i in 0 until limit) {
                val v = list[i]
                val cleanTitle = v.title.replace("|", " ").replace("\n", " ")
                sb.append(v.id).append('|')
                    .append(cleanTitle).append('|')
                    .append(v.duration).append('|')
                    .append(v.size).append('|')
                    .append(v.path).append('\n')
            }
            context.getSharedPreferences(SNAPSHOT_PREF, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SNAPSHOT_DATA, sb.toString())
                .apply()
        } catch (_: Exception) {
        }
    }

    private fun loadInstantSnapshotFromDisk(context: Context): List<VideoModel> {
        val out = ArrayList<VideoModel>()
        try {
            val raw = context.getSharedPreferences(SNAPSHOT_PREF, Context.MODE_PRIVATE)
                .getString(KEY_SNAPSHOT_DATA, null) ?: return emptyList()
            val lines = raw.split('\n')
            for (line in lines) {
                if (line.isBlank()) continue
                val parts = line.split('|')
                if (parts.size >= 5) {
                    val path = parts[4]
                    if (PermanentDeleteHelper.isMarkedDeleted(context, path)) continue
                    val id = parts[0].toLongOrNull() ?: 0L
                    val title = parts[1]
                    val durMs = parts[2].toLongOrNull() ?: 0L
                    val size = parts[3].toLongOrNull() ?: 0L
                    out.add(
                        VideoModel(
                            id = id,
                            title = title,
                            duration = durMs,
                            size = size,
                            path = path
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return out
    }
}
