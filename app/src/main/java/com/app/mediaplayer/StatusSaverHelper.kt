package com.app.mediaplayer

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.ArrayList
import java.util.Locale
import kotlin.concurrent.thread

object StatusSaverHelper {

    // 100% Background-Threaded Status Scanner: Never freezes or hangs the UI thread!
    fun openWhatsAppStatusSaverDialog(context: Context) {
        thread {
            val statusFiles = scanWhatsAppStatusesBackground()
            val fallbackShorts = if (statusFiles.isEmpty()) {
                DeepMediaScanner.filterShortsAndStatusVideos(
                    DeepMediaScanner.scanAllVideosEverywhere(context)
                )
            } else {
                emptyList()
            }

            (context as? Activity)?.runOnUiThread {
                if (statusFiles.isNotEmpty()) {
                    showFastStatusListDialog(context, statusFiles)
                } else if (fallbackShorts.isNotEmpty()) {
                    showShortVideosStatusDialog(context, fallbackShorts)
                } else {
                    Toast.makeText(
                        context,
                        "No recent WhatsApp Statuses found. View a status in WhatsApp first!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showFastStatusListDialog(context: Context, statusFiles: List<File>) {
        val d = context.resources.displayMetrics.density
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * d).toInt(), (18 * d).toInt(), (18 * d).toInt(), (16 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0A0A10"))
                cornerRadius = 22f * d
                setStroke((1.5f * d).toInt(), Color.parseColor("#00FFFF"))
            }
        }

        val header = TextView(context).apply {
            text = "WhatsApp Status Saver (${statusFiles.size} Ready)"
            setTextColor(Color.parseColor("#00FFFF"))
            textSize = 16.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (12 * d).toInt())
        }
        root.addView(header)

        val btnPlayAll = TextView(context).apply {
            text = "▶  Watch All Statuses in Reels Player"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((14 * d).toInt(), (11 * d).toInt(), (14 * d).toInt(), (11 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#00FFFF"))
                cornerRadius = 18f * d
            }
            setOnClickListener {
                val videoPaths = ArrayList(
                    statusFiles.filter { it.extension.lowercase(Locale.US) == "mp4" }
                        .map { it.absolutePath }
                )
                if (videoPaths.isNotEmpty()) {
                    context.startActivity(Intent(context, ShortsPlayerActivity::class.java).apply {
                        putStringArrayListExtra("SHORTS_LIST", videoPaths)
                    })
                } else {
                    Toast.makeText(context, "No video statuses to play", Toast.LENGTH_SHORT).show()
                }
            }
        }
        root.addView(btnPlayAll)

        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (300 * d).toInt()
            ).apply { topMargin = (12 * d).toInt() }
        }
        val listCol = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        for (file in statusFiles) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (9 * d).toInt(), 0, (9 * d).toInt())
            }
            val tvName = TextView(context).apply {
                val kb = (file.length() / 1024L).coerceAtLeast(1L)
                text = "🎬  ${file.name} (${kb} KB)"
                setTextColor(Color.WHITE)
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val btnSave = TextView(context).apply {
                text = "Save"
                setTextColor(Color.parseColor("#0A0A10"))
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setPadding((14 * d).toInt(), (7 * d).toInt(), (14 * d).toInt(), (7 * d).toInt())
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#00E676"))
                    cornerRadius = 14f * d
                }
                setOnClickListener {
                    saveVideoStatusToGallery(context, file.absolutePath)
                }
            }
            row.addView(tvName)
            row.addView(btnSave)
            listCol.addView(row)
        }

        scroll.addView(listCol)
        root.addView(scroll)

        val dialog = AlertDialog.Builder(context)
            .setView(root)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun showShortVideosStatusDialog(context: Context, shorts: List<VideoModel>) {
        val d = context.resources.displayMetrics.density
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * d).toInt(), (18 * d).toInt(), (18 * d).toInt(), (16 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0A0A10"))
                cornerRadius = 22f * d
                setStroke((1.5f * d).toInt(), Color.parseColor("#00FFFF"))
            }
        }

        val dialog = AlertDialog.Builder(context).setView(root).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (12 * d).toInt())
        }
        val header = TextView(context).apply {
            text = "Status & Short Clips (${shorts.size} Found)"
            setTextColor(Color.parseColor("#00FFFF"))
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnClose = TextView(context).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((32 * d).toInt(), (32 * d).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#26FFFFFF"))
            }
            setOnClickListener { dialog.dismiss() }
        }
        topRow.addView(header)
        topRow.addView(btnClose)
        root.addView(topRow)

        val btnPlayReels = TextView(context).apply {
            text = "▶  Open Vertical Shorts & Status Player"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((14 * d).toInt(), (11 * d).toInt(), (14 * d).toInt(), (11 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#00FFFF"))
                cornerRadius = 18f * d
            }
            setOnClickListener {
                dialog.dismiss()
                val paths = ArrayList(shorts.map { it.path })
                context.startActivity(Intent(context, ShortsPlayerActivity::class.java).apply {
                    putStringArrayListExtra("SHORTS_LIST", paths)
                })
            }
        }
        root.addView(btnPlayReels)

        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (260 * d).toInt()
            ).apply { topMargin = (12 * d).toInt() }
        }
        val listCol = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        shorts.take(30).forEachIndexed { index, item ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (9 * d).toInt(), 0, (9 * d).toInt())
            }
            val tvTitle = TextView(context).apply {
                text = "▶  ${item.title}"
                setTextColor(Color.WHITE)
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    dialog.dismiss()
                    val paths = ArrayList(shorts.map { it.path })
                    context.startActivity(Intent(context, ShortsPlayerActivity::class.java).apply {
                        putStringArrayListExtra("SHORTS_LIST", paths)
                        putExtra("START_INDEX", index)
                    })
                }
            }
            val btnSave = TextView(context).apply {
                text = "Save Copy"
                setTextColor(Color.parseColor("#0A0A10"))
                textSize = 11.5f
                typeface = Typeface.DEFAULT_BOLD
                setPadding((12 * d).toInt(), (6 * d).toInt(), (12 * d).toInt(), (6 * d).toInt())
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#00E676"))
                    cornerRadius = 14f * d
                }
                setOnClickListener {
                    saveVideoStatusToGallery(context, item.path)
                }
            }
            row.addView(tvTitle)
            row.addView(btnSave)
            listCol.addView(row)
        }

        scroll.addView(listCol)
        root.addView(scroll)
        dialog.show()
    }

    private fun scanWhatsAppStatusesBackground(): List<File> {
        val found = mutableListOf<File>()
        try {
            val root = Environment.getExternalStorageDirectory() ?: return emptyList()
            val dirs = listOf(
                File(root, "Android/media/com.whatsapp/WhatsApp/Media/.Statuses"),
                File(root, "WhatsApp/Media/.Statuses"),
                File(root, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses")
            )
            for (d in dirs) {
                if (d.exists() && d.isDirectory) {
                    d.listFiles()?.forEach { f ->
                        val ext = f.extension.lowercase(Locale.US)
                        if (ext == "mp4" || ext == "jpg" || ext == "jpeg" || ext == "png") {
                            found.add(f)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return found.sortedByDescending { it.lastModified() }
    }

    fun saveVideoStatusToGallery(context: Context, sourcePath: String) {
        thread {
            try {
                val srcFile = File(sourcePath)
                if (!srcFile.exists()) {
                    (context as? Activity)?.runOnUiThread {
                        Toast.makeText(context, "Source file not found", Toast.LENGTH_SHORT).show()
                    }
                    return@thread
                }
                val isVideo = srcFile.extension.lowercase(Locale.US) == "mp4"
                val outName = "OG_Status_${System.currentTimeMillis()}.${srcFile.extension.ifEmpty { "mp4" }}"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, outName)
                        put(MediaStore.MediaColumns.MIME_TYPE, if (isVideo) "video/mp4" else "image/jpeg")
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            if (isVideo) "${Environment.DIRECTORY_MOVIES}/OG_Saved_Statuses"
                            else "${Environment.DIRECTORY_PICTURES}/OG_Saved_Statuses"
                        )
                    }
                    val colUri = if (isVideo) {
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    } else {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                    val targetUri = resolver.insert(colUri, values)
                    if (targetUri != null) {
                        resolver.openOutputStream(targetUri)?.use { out ->
                            FileInputStream(srcFile).use { input ->
                                input.copyTo(out)
                            }
                        }
                    }
                } else {
                    val destDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                        "OG_Saved_Statuses"
                    )
                    if (!destDir.exists()) destDir.mkdirs()
                    val destFile = File(destDir, outName)
                    FileInputStream(srcFile).use { input ->
                        FileOutputStream(destFile).use { out ->
                            input.copyTo(out)
                        }
                    }
                }
                DeepMediaScanner.invalidateCache()
                (context as? Activity)?.runOnUiThread {
                    Toast.makeText(
                        context,
                        "Saved to Gallery: OG_Saved_Statuses/$outName",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                (context as? Activity)?.runOnUiThread {
                    Toast.makeText(context, "Unable to save status: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
