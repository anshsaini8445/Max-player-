package com.app.mediaplayer

data class FolderModel(
    val bucketId: String,
    val folderName: String,
    val folderPath: String,
    var videoCount: Int = 0
)
