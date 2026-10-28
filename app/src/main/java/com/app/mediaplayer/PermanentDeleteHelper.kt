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
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

object PermanentDeleteHelper {

    private const val PREF_DELETED = "HYBRID_PERMANENT_DELETED_REGISTRY"
    private const val KEY_PATHS = "deleted_paths_set"
    private const val KEY_TRASH_ENTRIES = "recycle_bin_entries_v1"
    private const val TRASH_RETENTION_MS = 30L * 24L * 60L * 60L * 1000L
    const val REQ_CODE_SYSTEM_DELETE = 8842

    data class TrashItem(
        val path: String,
        val title: String,
        val mediaId: Long,
        val isVideo: Boolean,
        val deletedAtMs: Long
    )

    fun isMarkedDeleted(context: Context, path: String): Boolean {
        if (path.isEmpty()) return false
        val prefs = context.getSharedPreferences(PREF_DELETED, Context.MODE_PRIVATE)
        val permanentSet = prefs.getStringSet(KEY_PATHS, emptySet()) ?: emptySet()
        if (permanentSet.contains(path)) return true
        return getRecycleBinItems(context).any { it.path == path }
    }

    fun getRecycleBinCount(context: Context): Int {
        return getRecycleBinItems(context).size
    }

    fun getRecycleBinItems(context: Context): List<TrashItem> {
        val prefs = context.getSharedPreferences(PREF_DELETED, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_TRASH_ENTRIES, "") ?: ""
        if (raw.isBlank()) return emptyList()

        val now = System.currentTimeMillis()
        val validItems = mutableListOf<TrashItem>()
        val expiredItems = mutableListOf<TrashItem>()

        for (line in raw.split("\n")) {
            if (line.isBlank()) continue
            val parts = line.split("||")
            if (parts.size >= 5) {
                val path = parts[0]
                val title = parts[1]
                val id = parts[2].toLongOrNull() ?: 0L
                val isVid = parts[3] == "1"
                val ts = parts[4].toLongOrNull() ?: now
                val item = TrashItem(path, title, id, isVid, ts)
                if (now - ts > TRASH_RETENTION_MS) {
                    expiredItems.add(item)
                } else {
                    validItems.add(item)
                }
            }
        }

        if (expiredItems.isNotEmpty()) {
            saveRecycleBinItems(context, validItems)
            expiredItems.forEach {
                executeHardStorageDelete(context, listOf(it.path), listOf(it.mediaId), it.isVideo)
            }
        }

        return validItems.sortedByDescending { it.deletedAtMs }
    }

    private fun saveRecycleBinItems(context: Context, list: List<TrashItem>) {
        val serialized = list.joinToString("\n") {
            "${it.path}||${it.title.replace("||", " ").replace("\n", " ")}||${it.mediaId}||${if (it.isVideo) "1" else "0"}||${it.deletedAtMs}"
        }
        context.getSharedPreferences(PREF_DELETED, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TRASH_ENTRIES, serialized)
            .apply()
    }

    private fun moveItemsToRecycleBin(context: Context, newItems: List<TrashItem>) {
        val current = getRecycleBinItems(context).toMutableList()
        val newPaths = newItems.map { it.path }.toHashSet()
        current.removeAll { newPaths.contains(it.path) }
        current.addAll(0, newItems)
        saveRecycleBinItems(context, current)
        DeepMediaScanner.removeDeletedPathsFromCache(context, newPaths)
    }

    fun restoreFromRecycleBin(context: Context, path: String) {
        val current = getRecycleBinItems(context).toMutableList()
        current.removeAll { it.path == path }
        saveRecycleBinItems(context, current)

        val prefs = context.getSharedPreferences(PREF_DELETED, Context.MODE_PRIVATE)
        val permanentSet = prefs.getStringSet(KEY_PATHS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (permanentSet.remove(path)) {
            prefs.edit().putStringSet(KEY_PATHS, permanentSet).apply()
        }
        DeepMediaScanner.invalidateCache()
    }

    private fun markPathsDeletedInRegistry(context: Context, paths: Collection<String>) {
        val prefs = context.getSharedPreferences(PREF_DELETED, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(KEY_PATHS, emptySet())?.toMutableSet() ?: mutableSetOf()
        existing.addAll(paths)
        prefs.edit().putStringSet(KEY_PATHS, existing).apply()

        val currentTrash = getRecycleBinItems(context).toMutableList()
        val pathSet = paths.toHashSet()
        if (currentTrash.removeAll { pathSet.contains(it.path) }) {
            saveRecycleBinItems(context, currentTrash)
        }

        DeepMediaScanner.removeDeletedPathsFromCache(context, paths)
    }

    fun confirmAndDeletePermanently(
        context: Context,
        title: String,
        path: String,
        mediaId: Long = 0L,
        isVideo: Boolean = true,
        onDeletedSuccess: (() -> Unit)? = null,
        onDeleted: (() -> Unit)? = null
    ) {
        showSmartDeleteOrTrashDialog(
            context = context,
            heading = "Move to Recycle Bin or Delete?",
            message = "\"$title\"\n\n• Recycle Bin keeps your file safe for 30 days so you can restore it anytime.\n• Permanent Delete erases it from storage immediately.",
            onMoveToTrash = {
                moveItemsToRecycleBin(
                    context,
                    listOf(TrashItem(path, title, mediaId, isVideo, System.currentTimeMillis()))
                )
                onDeletedSuccess?.invoke()
                onDeleted?.invoke()
                Toast.makeText(context, "Moved to Recycle Bin (Restorable for 30 Days)", Toast.LENGTH_SHORT).show()
            },
            onPermanentDelete = {
                executeHardStorageDelete(
                    context = context,
                    paths = listOf(path),
                    mediaIds = if (mediaId > 0L) listOf(mediaId) else emptyList(),
                    isVideo = isVideo
                )
                onDeletedSuccess?.invoke()
                onDeleted?.invoke()
                Toast.makeText(context, "Permanently deleted from storage", Toast.LENGTH_SHORT).show()
            }
        )
    }

    fun confirmAndDeleteMultipleVideosPermanently(
        context: Context,
        items: List<VideoModel>,
        onDeletedAll: () -> Unit
    ) {
        if (items.isEmpty()) return
        val count = items.size
        showSmartDeleteOrTrashDialog(
            context = context,
            heading = "Delete $count Selected Videos?",
            message = "Choose whether to move these $count videos safely to the 30-Day Recycle Bin or erase them permanently.",
            onMoveToTrash = {
                val now = System.currentTimeMillis()
                val trashList = items.map { TrashItem(it.path, it.title, it.id, true, now) }
                moveItemsToRecycleBin(context, trashList)
                onDeletedAll()
                Toast.makeText(context, "$count videos moved to Recycle Bin", Toast.LENGTH_SHORT).show()
            },
            onPermanentDelete = {
                val paths = items.map { it.path }
                val ids = items.mapNotNull { if (it.id > 0L) it.id else null }
                executeHardStorageDelete(
                    context = context,
                    paths = paths,
                    mediaIds = ids,
                    isVideo = true
                )
                onDeletedAll()
                Toast.makeText(context, "$count videos permanently deleted", Toast.LENGTH_SHORT).show()
            }
        )
    }

    fun openRecycleBinDialog(context: Context, onChanged: (() -> Unit)? = null) {
        val d = context.resources.displayMetrics.density
        val items = getRecycleBinItems(context).toMutableList()

        val rootCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * d).toInt(), (18 * d).toInt(), (18 * d).toInt(), (16 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F810141E"))
                cornerRadius = 24f * d
                setStroke((1.2f * d).toInt(), Color.parseColor("#33FFFFFF"))
            }
        }

        val dialog = AlertDialog.Builder(context).setView(rootCard).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (12 * d).toInt())
        }

        val titleCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvHeader = TextView(context).apply {
            text = "Recycle Bin (${items.size})"
            setTextColor(Color.WHITE)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
        }

        val tvSub = TextView(context).apply {
            text = "Items are automatically erased after 30 days"
            setTextColor(Color.parseColor("#9FB4CE"))
            textSize = 11.5f
            setPadding(0, (2 * d).toInt(), 0, 0)
        }
        titleCol.addView(tvHeader)
        titleCol.addView(tvSub)

        val btnEmptyBin = TextView(context).apply {
            text = "Empty Bin"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            visibility = if (items.isNotEmpty()) View.VISIBLE else View.GONE
            setPadding((12 * d).toInt(), (6 * d).toInt(), (12 * d).toInt(), (6 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FF1744"))
                cornerRadius = 12f * d
            }
            setOnClickListener {
                items.forEach {
                    executeHardStorageDelete(context, listOf(it.path), listOf(it.mediaId), it.isVideo)
                }
                saveRecycleBinItems(context, emptyList())
                dialog.dismiss()
                onChanged?.invoke()
                Toast.makeText(context, "Recycle Bin Emptied", Toast.LENGTH_SHORT).show()
            }
        }

        headerRow.addView(titleCol)
        headerRow.addView(btnEmptyBin)
        rootCard.addView(headerRow)

        if (items.isEmpty()) {
            val tvEmpty = TextView(context).apply {
                text = "Recycle Bin is empty.\nDeleted videos and songs stay safe here for 30 days."
                setTextColor(Color.parseColor("#9FB4CE"))
                textSize = 13.5f
                gravity = Gravity.CENTER
                setPadding((16 * d).toInt(), (36 * d).toInt(), (16 * d).toInt(), (36 * d).toInt())
            }
            rootCard.addView(tvEmpty)
        } else {
            val scroll = ScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (340 * d).toInt()
                )
            }
            val listCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            items.forEach { item ->
                val card = CardView(context).apply {
                    radius = 14f * d
                    cardElevation = 0f
                    setCardBackgroundColor(Color.parseColor("#16FFFFFF"))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = (4 * d).toInt()
                        bottomMargin = (4 * d).toInt()
                    }
                }

                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((10 * d).toInt(), (8 * d).toInt(), (10 * d).toInt(), (8 * d).toInt())
                }

                val imgThumb = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams((48 * d).toInt(), (48 * d).toInt()).apply {
                        marginEnd = (10 * d).toInt()
                    }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                if (item.isVideo) {
                    VideoAdapter.loadGuaranteedVideoThumbnail(context, item.path, imgThumb)
                } else {
                    AudioAdapter.loadAlbumArtOrFallbackInto(context, item.path, item.title, imgThumb)
                }

                val infoCol = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = (8 * d).toInt()
                    }
                }

                val tvTitle = TextView(context).apply {
                    text = item.title
                    setTextColor(Color.WHITE)
                    textSize = 13.5f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }

                val daysLeft = (30L - TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - item.deletedAtMs)).coerceIn(1L, 30L)
                val tvMeta = TextView(context).apply {
                    text = "${if (item.isVideo) "Video" else "Audio"}  •  $daysLeft days left"
                    setTextColor(Color.parseColor("#9FB4CE"))
                    textSize = 11.5f
                    setPadding(0, (2 * d).toInt(), 0, 0)
                }
                infoCol.addView(tvTitle)
                infoCol.addView(tvMeta)

                val btnRestore = TextView(context).apply {
                    text = "Restore"
                    setTextColor(Color.parseColor("#0A0A10"))
                    textSize = 11.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding((12 * d).toInt(), (6 * d).toInt(), (12 * d).toInt(), (6 * d).toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = (6 * d).toInt() }
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#00E676"))
                        cornerRadius = 10f * d
                    }
                    setOnClickListener {
                        it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        restoreFromRecycleBin(context, item.path)
                        card.visibility = View.GONE
                        onChanged?.invoke()
                        Toast.makeText(context, "Restored: ${item.title}", Toast.LENGTH_SHORT).show()
                    }
                }

                val btnErase = TextView(context).apply {
                    text = "Delete"
                    setTextColor(Color.WHITE)
                    textSize = 11.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding((10 * d).toInt(), (6 * d).toInt(), (10 * d).toInt(), (6 * d).toInt())
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#33FF1744"))
                        cornerRadius = 10f * d
                        setStroke((1 * d).toInt(), Color.parseColor("#FF5252"))
                    }
                    setOnClickListener {
                        executeHardStorageDelete(context, listOf(item.path), listOf(item.mediaId), item.isVideo)
                        card.visibility = View.GONE
                        onChanged?.invoke()
                        Toast.makeText(context, "Erased permanently", Toast.LENGTH_SHORT).show()
                    }
                }

                row.addView(imgThumb)
                row.addView(infoCol)
                row.addView(btnRestore)
                row.addView(btnErase)
                card.addView(row)
                listCol.addView(card)
            }

            scroll.addView(listCol)
            rootCard.addView(scroll)
        }

        val btnClose = TextView(context).apply {
            text = "Close"
            setTextColor(Color.WHITE)
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((16 * d).toInt(), (10 * d).toInt(), (16 * d).toInt(), (10 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (12 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1EFFFFFF"))
                cornerRadius = 14f * d
            }
            setOnClickListener { dialog.dismiss() }
        }
        rootCard.addView(btnClose)
        dialog.show()
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

        try {
            MediaScannerConnection.scanFile(
                context.applicationContext,
                paths.toTypedArray(),
                null,
                null
            )
        } catch (_: Exception) {
        }

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

    private fun showSmartDeleteOrTrashDialog(
        context: Context,
        heading: String,
        message: String,
        onMoveToTrash: () -> Unit,
        onPermanentDelete: () -> Unit
    ) {
        val d = context.resources.displayMetrics.density
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * d).toInt(), (20 * d).toInt(), (20 * d).toInt(), (18 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F810141E"))
                cornerRadius = 22f * d
                setStroke((1.2f * d).toInt(), Color.parseColor("#33FFFFFF"))
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
            setTextColor(Color.parseColor("#B0C4DE"))
            textSize = 13.5f
            setPadding(0, (10 * d).toInt(), 0, (16 * d).toInt())
        }

        val btnMoveTrash = TextView(context).apply {
            text = "MOVE TO RECYCLE BIN (30 DAYS SAFE)"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((16 * d).toInt(), (12 * d).toInt(), (16 * d).toInt(), (12 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#00E676"))
                cornerRadius = 14f * d
            }
            setOnClickListener {
                dialog.dismiss()
                onMoveToTrash()
            }
        }

        val bottomRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val btnCancel = TextView(context).apply {
            text = "Cancel"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (8 * d).toInt()
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1EFFFFFF"))
                cornerRadius = 14f * d
            }
            setOnClickListener { dialog.dismiss() }
        }

        val btnPermanent = TextView(context).apply {
            text = "Delete Permanently"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FF1744"))
                cornerRadius = 14f * d
            }
            setOnClickListener {
                dialog.dismiss()
                onPermanentDelete()
            }
        }

        bottomRow.addView(btnCancel)
        bottomRow.addView(btnPermanent)

        card.addView(tvHeading)
        card.addView(tvMessage)
        card.addView(btnMoveTrash)
        card.addView(bottomRow)
        dialog.show()
    }
}
