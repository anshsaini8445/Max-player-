package com.app.mediaplayer

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

object StatusSaverHelper {

    fun openWhatsAppStatusSaverDialog(context: Context) {
        val statusFiles = scanWhatsAppStatuses()
        if (statusFiles.isEmpty()) {
            val allShorts = DeepMediaScanner.filterShortsAndStatusVideos(
                DeepMediaScanner.scanAllVideosEverywhere(context)
            )
            if (allShorts.isNotEmpty()) {
                val paths = ArrayList<String>(allShorts.map { it.path })
                context.startActivity(Intent(context, ShortsPlayerActivity::class.java).apply {
                    putStringArrayListExtra("SHORTS_LIST", paths)
                })
            } else {
                Toast.makeText(
                    context,
                    "No recent WhatsApp Statuses found. View statuses in WhatsApp first!",
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }

        val d = context.resources.displayMetrics.density
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * d).toInt(), (18 * d).toInt(), (18 * d).toInt(), (16 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#13263B"))
                cornerRadius = 22f * d
                setStroke((1.5f * d).toInt(), Color.parseColor("#00E676"))
            }
        }

        val header = TextView(context).apply {
            text = "WhatsApp Status Saver (${statusFiles.size} Found)"
            setTextColor(Color.parseColor("#00E676"))
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (10 * d).toInt())
        }
        root.addView(header)

        val btnPlayAll = TextView(context).apply {
            text = "Play All Statuses in Vertical Reels Player"
            setTextColor(Color.BLACK)
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#5CE1E6"))
                cornerRadius = 18f * d
            }
            setOnClickListener {
                val videoPaths = ArrayList(statusFiles.filter {
                    it.extension.lowercase(Locale.US) == "mp4"
                }.map { it.absolutePath })
                if (videoPaths.isNotEmpty()) {
                    context.startActivity(Intent(context, ShortsPlayerActivity::class.java).apply {
                        putStringArrayListExtra("SHORTS_LIST", videoPaths)
                    })
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
                setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
            }
            val tvName = TextView(context).apply {
                val kb = file.length() / 1024L
                text = "${file.name} (${kb} KB)"
                setTextColor(Color.WHITE)
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val btnSave = TextView(context).apply {
                text = "Save to Gallery"
                setTextColor(Color.BLACK)
                textSize = 11.5f
                typeface = Typeface.DEFAULT_BOLD
                setPadding((12 * d).toInt(), (6 * d).toInt(), (12 * d).toInt(), (6 * d).toInt())
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
            .setNegativeButton("Close", null)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun scanWhatsAppStatuses(): List<File> {
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
        try {
            val srcFile = File(sourcePath)
            if (!srcFile.exists()) {
                Toast.makeText(context, "Source file not found", Toast.LENGTH_SHORT).show()
                return
            }
            val isVideo = srcFile.extension.lowercase(Locale.US) == "mp4"
            val outName = "OG_Status_${System.currentTimeMillis()}.${srcFile.extension}"

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
                    Toast.makeText(context, "Saved to Gallery: OG_Saved_Statuses/$outName", Toast.LENGTH_LONG).show()
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
                Toast.makeText(context, "Saved to Gallery: ${destFile.absolutePath}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to save status: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
