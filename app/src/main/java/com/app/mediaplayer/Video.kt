package com.app.mediaplayer

import android.net.Uri

data class Video(
    val id: Long,
    val title: String,
    val path: String,
    val uri: Uri,
    val duration: Long,
    val size: Long
)