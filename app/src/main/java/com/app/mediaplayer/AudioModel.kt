package com.app.mediaplayer

import java.io.File

data class AudioModel(
    val id: Long,
    val title: String,
    val path: String,
    val artist: String,
    val duration: Long,
    val size: Long = resolveFileSizeSafely(path)
) {
    // Secondary constructor to support (id, title, artist, path, size, duration) order
    constructor(
        id: Long,
        title: String,
        artist: String,
        path: String,
        size: Long,
        duration: Long
    ) : this(
        id = id,
        title = title,
        path = path,
        artist = artist,
        duration = duration,
        size = if (size > 0L) size else resolveFileSizeSafely(path)
    )

    companion object {
        private fun resolveFileSizeSafely(filePath: String): Long {
            return try {
                if (filePath.isNotEmpty() && !filePath.startsWith("content://")) {
                    val f = File(filePath)
                    if (f.exists()) f.length() else 0L
                } else {
                    0L
                }
            } catch (_: Exception) {
                0L
            }
        }
    }
}
