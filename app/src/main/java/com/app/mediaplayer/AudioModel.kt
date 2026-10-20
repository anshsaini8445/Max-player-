package com.app.mediaplayer

data class AudioModel(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val path: String,
    val size: Long,
    val duration: Long,
    val folderName: String = "Music"
)
