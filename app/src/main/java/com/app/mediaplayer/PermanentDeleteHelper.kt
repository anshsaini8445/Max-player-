package com.app.mediaplayer

import android.app.AlertDialog
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import java.io.File

object PermanentDeleteHelper {

    // Double-confirmation permanent file deletion so user never deletes important media by mistake
    fun confirmAndDeletePermanently(
        context: Context,
        title: String,
        path: String,
        mediaId: Long = -1L,
        isVideo: Boolean = true,
        onDeletedSuccess: () -> Unit
    ) {
        AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Delete Permanently?")
            .setMessage("Are you sure you want to delete \"$title\" from your phone?")
            .setPositiveButton("Continue") { _, _ ->
                // Second Final Confirmation (Safety Step)
                AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Final Confirmation (Cannot Undo)")
                    .setMessage("\"$title\" will be permanently erased from device storage. Confirm permanent delete?")
                    .setPositiveButton("Yes, Delete Forever") { _, _ ->
                        val success = executePermanentFileDeletion(context, path, mediaId, isVideo)
                        if (success) {
                            Toast.makeText(
                                context,
                                "Permanently Deleted: $title",
                                Toast.LENGTH_SHORT
                            ).show()
                            onDeletedSuccess()
                        } else {
                            Toast.makeText(
                                context,
                                "Removed from library: $title",
                                Toast.LENGTH_SHORT
                            ).show()
                            onDeletedSuccess()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun executePermanentFileDeletion(
        context: Context,
        path: String,
        mediaId: Long,
        isVideo: Boolean
    ): Boolean {
        var deleted = false
        try {
            val file = File(path)
            if (file.exists() && file.delete()) {
                deleted = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val resolver = context.contentResolver
            if (path.startsWith("content://")) {
                val rows = resolver.delete(Uri.parse(path), null, null)
                if (rows > 0) deleted = true
            } else {
                val baseUri = if (isVideo) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                if (mediaId > 0L) {
                    val itemUri = ContentUris.withAppendedId(baseUri, mediaId)
                    if (resolver.delete(itemUri, null, null) > 0) {
                        deleted = true
                    }
                }
                val rowsByPath = resolver.delete(
                    baseUri,
                    "${MediaStore.MediaColumns.DATA} = ?",
                    arrayOf(path)
                )
                if (rowsByPath > 0) deleted = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Also record in hidden/deleted registry so it never reappears if OS caches MediaStore
        context.getSharedPreferences("OG_DELETED_REGISTRY", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("deleted_$path", true)
            .apply()

        return deleted
    }

    fun isMarkedDeleted(context: Context, path: String): Boolean {
        return context.getSharedPreferences("OG_DELETED_REGISTRY", Context.MODE_PRIVATE)
            .getBoolean("deleted_$path", false)
    }
}
