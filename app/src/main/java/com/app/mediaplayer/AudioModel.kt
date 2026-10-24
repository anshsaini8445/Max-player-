package com.app.mediaplayer

import java.io.File

// Alias for legacy references so AudioTrackModel and AudioModel work interchangeably
typealias AudioTrackModel = AudioModel

// Extension property on the existing AudioModel (defined in Models.kt) to avoid Redeclaration errors
val AudioModel.size: Long
    get() = try {
        if (path.isNotEmpty() && !path.startsWith("content://")) {
            val f = File(path)
            if (f.exists()) f.length() else 0L
        } else {
            0L
        }
    } catch (_: Exception) {
        0L
    }
