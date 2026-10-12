package com.app.mediaplayer

data class VideoModel(
    val id: Long,
    val title: String,
    val path: String,
    val size: Long,
    val duration: Long
)

data class AudioModel(
    val id: Long,
    val title: String,
    val path: String,
    val artist: String,
    val duration: Long
)
