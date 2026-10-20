package com.app.mediaplayer

import android.app.Activity
import android.content.ContentValues
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread

object PlayerScreenshotHelper {

    fun captureAndSave(activity: Activity, videoPath: String, currentPositionMs: Long) {
        Toast.makeText(activity, "Capturing Screenshot...", Toast.LENGTH_SHORT).show()

        thread {
            var bmp: Bitmap? = null
            val retriever = MediaMetadataRetriever()
            try {
                if (videoPath.startsWith("content://")) {
                    retriever.setDataSource(activity, Uri.parse(videoPath))
                } else {
                    retriever.setDataSource(videoPath)
                }
                bmp = retriever.getFrameAtTime(
                    currentPositionMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    retriever.release()
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }

            var savedLocation = ""
            if (bmp != null) {
                try {
                    val fileName = "MAX_Shot_${System.currentTimeMillis()}.jpg"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            put(
                                MediaStore.Images.Media.RELATIVE_PATH,
                                "${Environment.DIRECTORY_PICTURES}/MAXPlayerScreenshots"
                            )
                        }
                        val uri = activity.contentResolver.insert(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            values
                        )
                        if (uri != null) {
                            activity.contentResolver.openOutputStream(uri)?.use { out ->
                                bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
                            }
                            savedLocation = "Pictures/MAXPlayerScreenshots/$fileName"
                        }
                    } else {
                        val dir = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                            "MAXPlayerScreenshots"
                        )
                        if (!dir.exists()) dir.mkdirs()
                        val outFile = File(dir, fileName)
                        FileOutputStream(outFile).use { out ->
                            bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
                        }
                        savedLocation = outFile.absolutePath
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            activity.runOnUiThread {
                if (savedLocation.isNotEmpty()) {
                    Toast.makeText(activity, "Screenshot Saved: $savedLocation", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(activity, "Unable to save screenshot", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
