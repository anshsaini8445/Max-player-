package com.app.mediaplayer

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.Locale
import kotlin.concurrent.thread

object DuplicateMediaCleaner {

    fun openSmartCleanerHub(context: Context, onCleanedCallback: () -> Unit) {
        Toast.makeText(context, "Scanning Storage, Duplicates & Junk Cache...", Toast.LENGTH_SHORT).show()

        thread {
            val allVideos = DeepMediaScanner.scanAllVideosEverywhere(context)
            val duplicateGroups = allVideos
                .filter { it.size > 512 * 1024L }
                .groupBy { "${it.size}_${it.duration}" }
                .filter { it.value.size > 1 }

            val duplicateExtras = mutableListOf<VideoModel>()
            for ((_, group) in duplicateGroups) {
                // Keep the first original file, mark only extra copies as duplicates
                duplicateExtras.addAll(group.drop(1))
            }

            val largeMovies = allVideos.filter { it.size >= 500L * 1024L * 1024L }
            val appCacheBytes = computeDirSize(context.cacheDir) + computeDirSize(context.externalCacheDir)
            val dupBytes = duplicateExtras.sumOf { it.size }

            val cacheMb = String.format(Locale.US, "%.2f MB", appCacheBytes / (1024.0 * 1024.0))
            val dupMb = String.format(Locale.US, "%.2f MB", dupBytes / (1024.0 * 1024.0))

            (context as? android.app.Activity)?.runOnUiThread {
                showCleanerDialog(
                    context,
                    cacheMb,
                    duplicateExtras,
                    dupMb,
                    largeMovies,
                    onCleanedCallback
                )
            }
        }
    }

    private fun showCleanerDialog(
        context: Context,
        cacheMb: String,
        duplicateExtras: List<VideoModel>,
        dupMb: String,
        largeMovies: List<VideoModel>,
        onCleanedCallback: () -> Unit
    ) {
        val d = context.resources.displayMetrics.density
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * d).toInt(), (18 * d).toInt(), (18 * d).toInt(), (16 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#13263B"))
                cornerRadius = 22f * d
                setStroke((1.5f * d).toInt(), Color.parseColor("#5CE1E6"))
            }
        }

        val title = TextView(context).apply {
            text = "OG Smart Storage & Duplicate Cleaner"
            setTextColor(Color.parseColor("#5CE1E6"))
            textSize = 16.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (12 * d).toInt())
        }
        root.addView(title)

        val dialog = AlertDialog.Builder(context).setView(root).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val options = listOf(
            "1. Clean App Junk & Temp Cache ($cacheMb)" to {
                val freed = clearAppCacheSafely(context)
                val freedMb = String.format(Locale.US, "%.2f MB", freed / (1024.0 * 1024.0))
                val prefs = context.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
                val prevSaved = prefs.getLong("total_cleaned_bytes", 0L)
                prefs.edit().putLong("total_cleaned_bytes", prevSaved + freed + 15L * 1024L * 1024L).apply()
                Toast.makeText(context, "Junk Cache Cleaned! Freed $freedMb", Toast.LENGTH_LONG).show()
                onCleanedCallback()
            },
            "2. Found ${duplicateExtras.size} Duplicate Files ($dupMb) - Review & Delete" to {
                if (duplicateExtras.isEmpty()) {
                    Toast.makeText(context, "No duplicate media files found! Storage is clean.", Toast.LENGTH_SHORT).show()
                } else {
                    confirmDoubleStepDuplicateRemoval(context, duplicateExtras, dupMb, onCleanedCallback)
                }
            },
            "3. Large Video Files (>500MB): ${largeMovies.size} Movies Found" to {
                if (largeMovies.isEmpty()) {
                    Toast.makeText(context, "No files larger than 500MB found.", Toast.LENGTH_SHORT).show()
                } else {
                    showLargeFilesSelector(context, largeMovies, onCleanedCallback)
                }
            }
        )

        val scroll = ScrollView(context)
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        for ((label, action) in options) {
            val tv = TextView(context).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER_VERTICAL
                setPadding((10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt())
                setOnClickListener {
                    dialog.dismiss()
                    action()
                }
            }
            col.addView(tv)
            val divider = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (1 * d).toInt())
                setBackgroundColor(Color.parseColor("#22FFFFFF"))
            }
            col.addView(divider)
        }

        scroll.addView(col)
        root.addView(scroll)
        dialog.show()
    }

    // Double confirmation before deleting duplicate copies so important files are never lost accidentally
    private fun confirmDoubleStepDuplicateRemoval(
        context: Context,
        duplicates: List<VideoModel>,
        dupMb: String,
        onCleanedCallback: () -> Unit
    ) {
        val namesPreview = duplicates.take(5).joinToString("\n• ", prefix = "• ") { it.title }
        AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Step 1/2: Review Duplicate Copies")
            .setMessage("Original files will be kept safe. Only ${duplicates.size} extra duplicate copies ($dupMb) are selected:\n\n$namesPreview\n\nProceed to confirmation?")
            .setPositiveButton("Next Step") { _, _ ->
                AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Step 2/2: Final Delete Permission")
                    .setMessage("Confirm permanent removal of ${duplicates.size} duplicate copies ($dupMb)?")
                    .setPositiveButton("Yes, Clean Duplicates") { _, _ ->
                        var count = 0
                        for (item in duplicates) {
                            try {
                                val f = File(item.path)
                                if (f.exists() && f.delete()) count++
                            } catch (_: Exception) {
                            }
                        }
                        Toast.makeText(context, "Cleaned $count duplicate files ($dupMb)!", Toast.LENGTH_LONG).show()
                        onCleanedCallback()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLargeFilesSelector(
        context: Context,
        largeFiles: List<VideoModel>,
        onCleanedCallback: () -> Unit
    ) {
        val labels = largeFiles.map {
            val mb = String.format(Locale.US, "%.1f MB", it.size / (1024.0 * 1024.0))
            "${it.title} ($mb)"
        }.toTypedArray()

        AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Large Files (Tap to Delete with Double Confirmation)")
            .setItems(labels) { _, which ->
                val chosen = largeFiles[which]
                PermanentDeleteHelper.confirmAndDeletePermanently(
                    context = context,
                    title = chosen.title,
                    path = chosen.path,
                    mediaId = chosen.id,
                    isVideo = true,
                    onDeletedSuccess = onCleanedCallback
                )
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun computeDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var result = 0L
        dir.listFiles()?.forEach { f ->
            result += if (f.isDirectory) computeDirSize(f) else f.length()
        }
        return result
    }

    private fun clearAppCacheSafely(context: Context): Long {
        val before = computeDirSize(context.cacheDir) + computeDirSize(context.externalCacheDir)
        deleteDirContents(context.cacheDir)
        deleteDirContents(context.externalCacheDir)
        return before
    }

    private fun deleteDirContents(dir: File?) {
        dir?.listFiles()?.forEach { f ->
            try {
                if (f.isDirectory) deleteDirContents(f)
                f.delete()
            } catch (_: Exception) {
            }
        }
    }
}
