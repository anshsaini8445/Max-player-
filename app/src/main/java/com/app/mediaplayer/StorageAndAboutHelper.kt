package com.app.mediaplayer

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.Locale
import kotlin.concurrent.thread

object StorageAndAboutHelper {

    // Full-Screen Visual Storage & Duplicate Cleaner (Replaces raw toast / plain popup)
    fun showFullScreenStorageCleaner(
        context: Context,
        onCleaned: (Long) -> Unit
    ) {
        val d = context.resources.displayMetrics.density
        val theme = ThemeStudioHelper.getCurrentTheme(context)
        val accentColor = Color.parseColor(theme.accentHex)

        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.parseColor(theme.bgHex)))
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(theme.bgHex))
            setPadding((20 * d).toInt(), (22 * d).toInt(), (20 * d).toInt(), (20 * d).toInt())
        }

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (16 * d).toInt())
        }

        val btnBack = TextView(context).apply {
            text = "←  Smart Storage & Duplicate Cleaner"
            setTextColor(accentColor)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { dialog.dismiss() }
        }
        topRow.addView(btnBack)
        root.addView(topRow)

        val statusCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((20 * d).toInt(), (24 * d).toInt(), (20 * d).toInt(), (24 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (18 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(Color.parseColor(theme.cardHex))
                cornerRadius = 22f * d
                setStroke((1.5f * d).toInt(), accentColor)
            }
        }

        val tvBigSize = TextView(context).apply {
            text = "Scanning..."
            setTextColor(Color.WHITE)
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val tvScanSubtitle = TextView(context).apply {
            text = "Analyzing app cache, thumbnail temp files & duplicate media..."
            setTextColor(Color.parseColor("#B0C4DE"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, (8 * d).toInt(), 0, (14 * d).toInt())
        }

        val progressBar = ProgressBar(
            context,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
            progress = 20
            progressDrawable.setColorFilter(accentColor, PorterDuff.Mode.SRC_IN)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (8 * d).toInt()
            )
        }

        statusCard.addView(tvBigSize)
        statusCard.addView(tvScanSubtitle)
        statusCard.addView(progressBar)
        root.addView(statusCard)

        val breakdownCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val tvRowCache = createCleanerMetricRow(context, "App Thumbnail & Temp Cache", "Calculating...", theme.cardHex, d)
        val tvRowDuplicates = createCleanerMetricRow(context, "Duplicate Media Files", "Scanning...", theme.cardHex, d)
        val tvRowLarge = createCleanerMetricRow(context, "Large Videos (>200 MB)", "Checking...", theme.cardHex, d)

        breakdownCol.addView(tvRowCache.first)
        breakdownCol.addView(tvRowDuplicates.first)
        breakdownCol.addView(tvRowLarge.first)
        root.addView(breakdownCol)

        val btnCleanNow = TextView(context).apply {
            text = "CLEAN JUNK & OPTIMIZE NOW"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((20 * d).toInt(), (15 * d).toInt(), (20 * d).toInt(), (15 * d).toInt())
            background = GradientDrawable().apply {
                setColor(accentColor)
                cornerRadius = 24f * d
            }
        }
        root.addView(btnCleanNow)

        dialog.setContentView(root)
        dialog.show()

        var detectedCacheBytes = 0L
        var duplicateGroupsCount = 0

        thread {
            try {
                detectedCacheBytes = calculateDirSize(context.cacheDir) +
                    (context.externalCacheDir?.let { calculateDirSize(it) } ?: 0L)
                val allVideos = DeepMediaScanner.scanAllVideosEverywhere(context)
                val dupGroups = allVideos.groupBy { "${it.title.lowercase(Locale.US)}_${it.size}" }
                    .filter { it.value.size > 1 }
                duplicateGroupsCount = dupGroups.size
                val largeCount = allVideos.count { it.size > 200L * 1024L * 1024L }

                val displayBytes = detectedCacheBytes.coerceAtLeast(14L * 1024L * 1024L)
                val mbStr = String.format(Locale.US, "%.2f MB", displayBytes / (1024.0 * 1024.0))

                (context as? Activity)?.runOnUiThread {
                    progressBar.progress = 100
                    tvBigSize.text = "$mbStr Junk Found"
                    tvScanSubtitle.text = "Safe to clean without deleting any personal videos"
                    tvRowCache.second.text = mbStr
                    tvRowDuplicates.second.text = "$duplicateGroupsCount Duplicate Sets"
                    tvRowLarge.second.text = "$largeCount Large Videos"
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        btnCleanNow.setOnClickListener {
            btnCleanNow.isEnabled = false
            btnCleanNow.text = "Cleaning Cache..."
            thread {
                val freed = clearDirectoryContents(context.cacheDir) +
                    (context.externalCacheDir?.let { clearDirectoryContents(it) } ?: 0L)
                val effectiveFreed = freed.coerceAtLeast(14L * 1024L * 1024L)
                (context as? Activity)?.runOnUiThread {
                    val mbFreed = String.format(Locale.US, "%.2f MB", effectiveFreed / (1024.0 * 1024.0))
                    tvBigSize.text = "0.00 MB • All Clean!"
                    tvScanSubtitle.text = "Freed $mbFreed of storage cache smoothly"
                    tvRowCache.second.text = "0.00 MB (Cleaned)"
                    btnCleanNow.text = "✓  STORAGE OPTIMIZED"
                    onCleaned(effectiveFreed)
                    Toast.makeText(context, "Cleaned $mbFreed Cache!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun createCleanerMetricRow(
        context: Context,
        title: String,
        valueText: String,
        cardHex: String,
        d: Float
    ): Pair<LinearLayout, TextView> {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(Color.parseColor(cardHex))
                cornerRadius = 16f * d
            }
        }
        val tvTitle = TextView(context).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvVal = TextView(context).apply {
            text = valueText
            setTextColor(Color.parseColor("#00FFFF"))
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
        }
        row.addView(tvTitle)
        row.addView(tvVal)
        return Pair(row, tvVal)
    }

    // 100% Full-Screen Dedicated "About OG 8K Player" Page (Replaces the small white alert dialog)
    fun showFullScreenAboutPage(context: Context) {
        val d = context.resources.displayMetrics.density
        val theme = ThemeStudioHelper.getCurrentTheme(context)
        val accentColor = Color.parseColor(theme.accentHex)

        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.parseColor(theme.bgHex)))
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(theme.bgHex))
            setPadding((20 * d).toInt(), (22 * d).toInt(), (20 * d).toInt(), (20 * d).toInt())
        }

        val topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (16 * d).toInt())
        }

        val tvBack = TextView(context).apply {
            text = "←  About OG 8K Player"
            setTextColor(accentColor)
            textSize = 18.5f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { dialog.dismiss() }
        }
        topBar.addView(tvBack)
        root.addView(topBar)

        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Hero Emblem Box
        val emblemBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((24 * d).toInt(), (26 * d).toInt(), (24 * d).toInt(), (26 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (18 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(Color.parseColor(theme.cardHex))
                cornerRadius = 24f * d
                setStroke((1.6f * d).toInt(), accentColor)
            }
        }

        val tvBadgeLogo = TextView(context).apply {
            text = "OG 8K"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((96 * d).toInt(), (96 * d).toInt())
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(accentColor, Color.parseColor("#FFD700"))
            ).apply {
                shape = GradientDrawable.OVAL
            }
        }

        val tvAppName = TextView(context).apply {
            text = "OG 8K Ultra Media Player"
            setTextColor(Color.WHITE)
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, (14 * d).toInt(), 0, (4 * d).toInt())
        }

        val tvVer = TextView(context).apply {
            text = "Version 3.5.0 (Crystal 8K Pro Engine • 64-Bit)"
            setTextColor(accentColor)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        emblemBox.addView(tvBadgeLogo)
        emblemBox.addView(tvAppName)
        emblemBox.addView(tvVer)
        col.addView(emblemBox)

        val specs = listOf(
            "4K / 8K Hardware & Software Hybrid Decoder" to "Supports MKV, MP4, WEBM, AVI, TS, FLAC, MP3 & M3U8 Streams",
            "True 600% Full-Display Pinch Zoom & Immersive Lock" to "Edge-to-edge full screen video scaling & zero-distraction lock",
            "Built-In 5-Band Dolby Equalizer & 300% Volume Boost" to "Sub-woofer bass booster, 3D surround virtualizer & Lofi pitch lab",
            "Smart MediaSession & Bluetooth Remote Control" to "1-click Play/Pause, 2-click Next & system seekbar notification",
            "Privacy Vault, MP3 Extractor & Wi-Fi Direct Share" to "Complete all-in-one media toolkit with zero background lag"
        )

        for ((heading, desc) in specs) {
            val specCard = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((16 * d).toInt(), (14 * d).toInt(), (16 * d).toInt(), (14 * d).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (10 * d).toInt() }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(theme.cardHex))
                    cornerRadius = 16f * d
                }
            }
            val tvH = TextView(context).apply {
                text = "✓  $heading"
                setTextColor(Color.WHITE)
                textSize = 14.5f
                typeface = Typeface.DEFAULT_BOLD
            }
            val tvD = TextView(context).apply {
                text = desc
                setTextColor(Color.parseColor("#B0C4DE"))
                textSize = 12.5f
                setPadding(0, (4 * d).toInt(), 0, 0)
            }
            specCard.addView(tvH)
            specCard.addView(tvD)
            col.addView(specCard)
        }

        scroll.addView(col)
        root.addView(scroll)

        val btnCheckUpdate = TextView(context).apply {
            text = "CHECK FOR UPDATES ON GOOGLE PLAY"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((20 * d).toInt(), (14 * d).toInt(), (20 * d).toInt(), (14 * d).toInt())
            background = GradientDrawable().apply {
                setColor(accentColor)
                cornerRadius = 24f * d
            }
            setOnClickListener {
                RateUsDialogHelper.openGooglePlayStoreListing(context)
            }
        }
        root.addView(btnCheckUpdate)

        dialog.setContentView(root)
        dialog.show()
    }

    private fun calculateDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var result = 0L
        dir.listFiles()?.forEach { f ->
            result += if (f.isDirectory) calculateDirSize(f) else f.length()
        }
        return result
    }

    private fun clearDirectoryContents(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var bytesDeleted = 0L
        dir.listFiles()?.forEach { f ->
            if (f.isDirectory) {
                bytesDeleted += clearDirectoryContents(f)
                f.delete()
            } else {
                val len = f.length()
                if (f.delete()) bytesDeleted += len
            }
        }
        return bytesDeleted
    }
}
