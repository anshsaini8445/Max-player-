package com.app.mediaplayer

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaScannerConnection
import android.os.Environment
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import kotlin.concurrent.thread

object StatusSaverHelper {

    fun openWhatsAppStatusSaverDialog(context: Context) {
        val d = context.resources.displayMetrics.density

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt(), (14 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0A0A10"))
                cornerRadius = 24f * d
                setStroke((1.5f * d).toInt(), Color.WHITE)
            }
        }

        val dialog = AlertDialog.Builder(context).setView(root).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (12 * d).toInt())
        }

        val tvHeader = TextView(context).apply {
            text = "WhatsApp & Business Status Saver"
            setTextColor(Color.WHITE)
            textSize = 16.5f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnClose = TextView(context).apply {
            text = "X"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((32 * d).toInt(), (32 * d).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#28FFFFFF"))
            }
            setOnClickListener { dialog.dismiss() }
        }
        headerRow.addView(tvHeader)
        headerRow.addView(btnClose)
        root.addView(headerRow)

        val tvEmpty = TextView(context).apply {
            text = "Scanning WhatsApp Statuses..."
            setTextColor(Color.parseColor("#B0C4DE"))
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((14 * d).toInt(), (40 * d).toInt(), (14 * d).toInt(), (40 * d).toInt())
        }
        root.addView(tvEmpty)

        val rvStatuses = RecyclerView(context).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (390 * d).toInt()
            )
            layoutManager = GridLayoutManager(context, 2)
            setHasFixedSize(true)
        }
        root.addView(rvStatuses)

        dialog.show()

        thread {
            val statusFiles = discoverWhatsAppStatusFiles()
            (context as? Activity)?.runOnUiThread {
                if (statusFiles.isEmpty()) {
                    tvEmpty.text = "No recent WhatsApp video statuses found.\nWatch any status in WhatsApp first and open this tool to save it permanently!"
                } else {
                    tvEmpty.visibility = View.GONE
                    rvStatuses.visibility = View.VISIBLE
                    rvStatuses.adapter = object : RecyclerView.Adapter<StatusCardVH>() {
                        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatusCardVH {
                            val card = CardView(context).apply {
                                radius = 14f * d
                                cardElevation = 4f * d
                                setCardBackgroundColor(Color.parseColor("#141E30"))
                                layoutParams = RecyclerView.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    setMargins((5 * d).toInt(), (5 * d).toInt(), (5 * d).toInt(), (5 * d).toInt())
                                }
                            }

                            val col = LinearLayout(context).apply {
                                orientation = LinearLayout.VERTICAL
                                setPadding((8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
                            }

                            val img = ImageView(context).apply {
                                layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    (105 * d).toInt()
                                )
                                scaleType = ImageView.ScaleType.CENTER_CROP
                            }

                            val tvName = TextView(context).apply {
                                setTextColor(Color.WHITE)
                                textSize = 11.5f
                                typeface = Typeface.DEFAULT_BOLD
                                maxLines = 1
                                ellipsize = TextUtils.TruncateAt.END
                                setPadding(0, (6 * d).toInt(), 0, (6 * d).toInt())
                            }

                            val actionsRow = LinearLayout(context).apply {
                                orientation = LinearLayout.HORIZONTAL
                            }

                            val btnPlay = TextView(context).apply {
                                text = "Play"
                                setTextColor(Color.WHITE)
                                textSize = 11.5f
                                typeface = Typeface.DEFAULT_BOLD
                                gravity = Gravity.CENTER
                                setPadding((8 * d).toInt(), (6 * d).toInt(), (8 * d).toInt(), (6 * d).toInt())
                                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                                    marginEnd = (4 * d).toInt()
                                }
                                background = GradientDrawable().apply {
                                    setColor(Color.parseColor("#24FFFFFF"))
                                    cornerRadius = 10f * d
                                }
                            }

                            val btnSave = TextView(context).apply {
                                text = "Save"
                                setTextColor(Color.parseColor("#0A0A10"))
                                textSize = 11.5f
                                typeface = Typeface.DEFAULT_BOLD
                                gravity = Gravity.CENTER
                                setPadding((8 * d).toInt(), (6 * d).toInt(), (8 * d).toInt(), (6 * d).toInt())
                                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                                    marginStart = (4 * d).toInt()
                                }
                                background = GradientDrawable().apply {
                                    setColor(Color.WHITE)
                                    cornerRadius = 10f * d
                                }
                            }

                            actionsRow.addView(btnPlay)
                            actionsRow.addView(btnSave)
                            col.addView(img)
                            col.addView(tvName)
                            col.addView(actionsRow)
                            card.addView(col)
                            return StatusCardVH(card, img, tvName, btnPlay, btnSave)
                        }

                        override fun onBindViewHolder(holder: StatusCardVH, position: Int) {
                            val file = statusFiles[position]
                            holder.tvName.text = file.name
                            VideoAdapter.loadGuaranteedVideoThumbnail(context, file.absolutePath, holder.img)

                            holder.btnPlay.setOnClickListener {
                                val intent = Intent(context, PlayerActivity::class.java).apply {
                                    putExtra("VIDEO_PATH", file.absolutePath)
                                    putExtra("VIDEO_TITLE", file.name)
                                    addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                                }
                                context.startActivity(intent)
                            }

                            holder.btnSave.setOnClickListener {
                                saveStatusFileToGallery(context, file)
                            }
                        }

                        override fun getItemCount(): Int = statusFiles.size
                    }
                }
            }
        }
    }

    private fun discoverWhatsAppStatusFiles(): List<File> {
        val rootPath = Environment.getExternalStorageDirectory().absolutePath
        val candidateDirs = listOf(
            File("$rootPath/Android/media/com.whatsapp/WhatsApp/Media/.Statuses"),
            File("$rootPath/WhatsApp/Media/.Statuses"),
            File("$rootPath/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses"),
            File("$rootPath/WhatsApp Business/Media/.Statuses")
        )

        val results = mutableListOf<File>()
        for (dir in candidateDirs) {
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    val name = file.name.lowercase(Locale.US)
                    if (file.isFile && (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".3gp"))) {
                        results.add(file)
                    }
                }
            }
        }
        return results.sortedByDescending { it.lastModified() }
    }

    private fun saveStatusFileToGallery(context: Context, sourceFile: File) {
        thread {
            try {
                val destDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "HybridStatusSaver"
                )
                if (!destDir.exists()) destDir.mkdirs()
                val destFile = File(destDir, "Status_${sourceFile.name}")

                FileInputStream(sourceFile).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }

                MediaScannerConnection.scanFile(
                    context.applicationContext,
                    arrayOf(destFile.absolutePath),
                    arrayOf("video/mp4"),
                    null
                )
                DeepMediaScanner.invalidateCache()

                (context as? Activity)?.runOnUiThread {
                    Toast.makeText(
                        context,
                        "Status Saved to Gallery: Movies/HybridStatusSaver/${destFile.name}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                (context as? Activity)?.runOnUiThread {
                    Toast.makeText(context, "Unable to save status: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private class StatusCardVH(
        view: View,
        val img: ImageView,
        val tvName: TextView,
        val btnPlay: TextView,
        val btnSave: TextView
    ) : RecyclerView.ViewHolder(view)
}
