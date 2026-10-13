package com.app.mediaplayer

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object VaultHelper {

    private const val PREFS_NAME = "VaultMetadata"
    private const val VAULT_FOLDER = ".MAX_Vault"
    private const val EXTENSION = ".enc"

    fun hideVideo(context: Context, video: VideoModel): Boolean {
        try {
            val vaultDir = File(context.getExternalFilesDir(null), VAULT_FOLDER)
            if (!vaultDir.exists()) {
                vaultDir.mkdirs()
            }

            val sourceFile = File(video.path)
            val safeFileName = sourceFile.name.replace(" ", "_") + EXTENSION
            val destFile = File(vaultDir, safeFileName)

            val inputStream = FileInputStream(sourceFile)
            val outputStream = FileOutputStream(destFile)
            
            val buffer = ByteArray(1024)
            var length: Int
            while (inputStream.read(buffer).also { length = it } > 0) {
                outputStream.write(buffer, 0, length)
            }
            
            inputStream.close()
            outputStream.close()

            if (sourceFile.exists()) {
                val deleted = sourceFile.delete()
                if (!deleted) {
                    try {
                        val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, video.id)
                        context.contentResolver.delete(uri, null, null)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(destFile.name, "${video.path}<SPLIT>${video.title}").apply()

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun restoreVideo(context: Context, vaultVideo: VaultModel): Boolean {
        try {
            val sourceFile = File(vaultVideo.vaultPath)
            val destFile = File(vaultVideo.originalPath)

            val destDir = destFile.parentFile
            if (destDir != null && !destDir.exists()) {
                destDir.mkdirs()
            }

            val inputStream = FileInputStream(sourceFile)
            val outputStream = FileOutputStream(destFile)
            
            val buffer = ByteArray(1024)
            var length: Int
            while (inputStream.read(buffer).also { length = it } > 0) {
                outputStream.write(buffer, 0, length)
            }
            
            inputStream.close()
            outputStream.close()

            if (sourceFile.exists()) {
                sourceFile.delete()
            }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(vaultVideo.fileName).apply()

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun getVaultVideos(context: Context): List<VaultModel> {
        val vaultList = mutableListOf<VaultModel>()
        try {
            val vaultDir = File(context.getExternalFilesDir(null), VAULT_FOLDER)
            if (!vaultDir.exists()) return vaultList

            val files = vaultDir.listFiles() ?: return vaultList
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            for (file in files) {
                if (file.name.endsWith(EXTENSION)) {
                    val metadata = prefs.getString(file.name, "")
                    if (!metadata.isNullOrEmpty() && metadata.contains("<SPLIT>")) {
                        val parts = metadata.split("<SPLIT>")
                        val originalPath = parts[0]
                        val title = parts[1]
                        vaultList.add(VaultModel(file.name, title, file.absolutePath, originalPath))
                    } else {
                        vaultList.add(VaultModel(file.name, "Unknown Video", file.absolutePath, ""))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return vaultList
    }
}
