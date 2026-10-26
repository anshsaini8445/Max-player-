package com.app.mediaplayer

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File

object PermanentDeleteHelper {

    private const val PREF_DELETED = "HYBRID_PERMANENT_DELETED_REGISTRY"
    private const val KEY_PATHS = "deleted_paths_set"
    const val REQ_CODE_SYSTEM_DELETE = 8842

    fun isMarkedDeleted(context: Context, path: String): Boolean {
        if (path.isEmpty()) return false
        val set = context.getSharedPreferences(PREF_DELETED, Context.MODE_PRIVATE)
            .getStringSet(KEY_PATHS, emptySet()) ?: emptySet()
        return set.contains(path)
    }

    private fun markPathsDeletedInRegistry(context: Context, paths: Collection<String>) {
        val prefs = context.getSharedPreferences(PREF_DELETED, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(KEY_PATHS, emptySet())?.toMutableSet() ?: mutableSetOf()
        existing.addAll(paths)
        prefs.edit().putStringSet(KEY_PATHS, existing).apply()
        DeepMediaScanner.removeDeletedPathsFromCache(context, paths)
    }

    fun confirmAndDeletePermanently(
        context: Context,
        title: String,
        path: String,
        mediaId: Long = 0L,
        isVideo: Boolean = true,
        onDeleted: () -> Unit
    ) {
        showModernDeleteConfirmationDialog(
            context = context,
            heading = "Delete Permanently from Phone?",
            message = "\"$title\"\n\nThis file will be permanently erased from internal storage and cannot be recovered.",
            confirmText = "DELETE PERMANENTLY"
        ) {
            executeHardStorageDelete(
                context = context,
                paths = listOf(path),
                mediaIds = if (mediaId > 0L) listOf(mediaId) else emptyList(),
                isVideo = isVideo
            )
            onDeleted()
            Toast.makeText(context, "Deleted permanently from storage", Toast.LENGTH_SHORT).show()
        }
    }

    fun confirmAndDeleteMultipleVideosPermanently(
        context: Context,
        items: List<VideoModel>,
        onDeletedAll: () -> Unit
    ) {
        if (items.isEmpty()) return
        val count = items.size
        showModernDeleteConfirmationDialog(
            context = context,
            heading = "Delete $count Videos Permanently?",
            message = "All $count selected videos will be permanently erased from your phone's internal storage.",
            confirmText = "DELETE $count VIDEOS"
        ) {
            val paths = items.map { it.path }
            val ids = items.mapNotNull { if (it.id > 0L) it.id else null }
            executeHardStorageDelete(
                context = context,
                paths = paths,
                mediaIds = ids,
                isVideo = true
            )
            onDeletedAll()
            Toast.makeText(
                context,
                "$count videos permanently deleted from phone",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun executeHardStorageDelete(
        context: Context,
        paths: List<String>,
        mediaIds: List<Long>,
        isVideo: Boolean
    ) {
        markPathsDeletedInRegistry(context, paths)

        val resolver = context.contentResolver
        val baseUri: Uri = if (isVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val failedUrisForAndroid11 = ArrayList<Uri>()

        // 1. Delete by MediaStore ID
        for (id in mediaIds) {
            if (id <= 0L) continue
            val itemUri = ContentUris.withAppendedId(baseUri, id)
            try {
                val rows = resolver.delete(itemUri, null, null)
                if (rows == 0) {
                    failedUrisForAndroid11.add(itemUri)
                }
            } catch (_: SecurityException) {
                failedUrisForAndroid11.add(itemUri)
            } catch (_: Exception) {
            }
        }

        // 2. Delete by File Path from MediaStore & Physical File System
        for (rawPath in paths) {
            if (rawPath.isEmpty()) continue
            try {
                val f = File(rawPath)
                if (f.exists()) {
                    f.delete()
                }
            } catch (_: Exception) {
            }

            try {
                val where = "${MediaStore.MediaColumns.DATA} = ?"
                val args = arrayOf(rawPath)
                resolver.delete(baseUri, where, args)
            } catch (_: SecurityException) {
                // Lookup URI if security exception occurs on scoped storage
                try {
                    val proj = arrayOf(MediaStore.MediaColumns._ID)
                    resolver.query(
                        baseUri,
                        proj,
                        "${MediaStore.MediaColumns.DATA} = ?",
                        arrayOf(rawPath),
                        null
                    )?.use { c ->
                        if (c.moveToFirst()) {
                            val foundId = c.getLong(0)
                            val uri = ContentUris.withAppendedId(baseUri, foundId)
                            if (!failedUrisForAndroid11.contains(uri)) {
                                failedUrisForAndroid11.add(uri)
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            } catch (_: Exception) {
            }
        }

        // 3. Notify MediaScanner so Gallery & File Managers immediately remove the deleted files
        try {
            MediaScannerConnection.scanFile(
                context.applicationContext,
                paths.toTypedArray(),
                null,
                null
            )
        } catch (_: Exception) {
        }

        // 4. On Android 11+ (API 30+), if any file is owned by another app and requires system trash/delete confirmation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            failedUrisForAndroid11.isNotEmpty() &&
            context is Activity
        ) {
            try {
                val pendingIntent = MediaStore.createDeleteRequest(resolver, failedUrisForAndroid11)
                context.startIntentSenderForResult(
                    pendingIntent.intentSender,
                    REQ_CODE_SYSTEM_DELETE,
                    null,
                    0,
                    0,
                    0
                )
            } catch (_: Exception) {
            }
        }
    }

    private fun showModernDeleteConfirmationDialog(
        context: Context,
        heading: String,
        message: String,
        confirmText: String,
        onConfirm: () -> Unit
    ) {
        val d = context.resources.displayMetrics.density
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * d).toInt(), (20 * d).toInt(), (20 * d).toInt(), (18 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0D0D14"))
                cornerRadius = 22f * d
                setStroke((1.5f * d).toInt(), Color.parseColor("#FF5252"))
            }
        }

        val dialog = AlertDialog.Builder(context).setView(card).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvHeading = TextView(context).apply {
            text = heading
            setTextColor(Color.WHITE)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
        }

        val tvMessage = TextView(context).apply {
            text = message
            setTextColor(Color.parseColor("#D0D8E8"))
            textSize = 13.5f
            setPadding(0, (10 * d).toInt(), 0, (18 * d).toInt())
        }

        val buttonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val btnCancel = TextView(context).apply {
            text = "CANCEL"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((18 * d).toInt(), (10 * d).toInt(), (18 * d).toInt(), (10 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (10 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#24FFFFFF"))
                cornerRadius = 14f * d
            }
            setOnClickListener { dialog.dismiss() }
        }

        val btnDelete = TextView(context).apply {
            text = confirmText
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((18 * d).toInt(), (10 * d).toInt(), (18 * d).toInt(), (10 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FF1744"))
                cornerRadius = 14f * d
            }
            setOnClickListener {
                dialog.dismiss()
                onConfirm()
            }
        }

        buttonsRow.addView(btnCancel)
        buttonsRow.addView(btnDelete)
        card.addView(tvHeading)
        card.addView(tvMessage)
        card.addView(buttonsRow)
        dialog.show()
    }
}
