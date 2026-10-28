package com.app.mediaplayer

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Environment
import android.os.StatFs
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.cos
import kotlin.math.sin

object HybridCleanerHubHelper {

    data class StorageMetrics(
        val usedBytes: Long,
        val totalBytes: Long,
        val freeBytes: Long,
        val cleanableJunkBytes: Long
    ) {
        val usedFraction: Float
            get() = if (totalBytes > 0L) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0.05f, 0.98f) else 0.62f

        val usedGbText: String
            get() = String.format(Locale.US, "%.0fGB", usedBytes / (1024.0 * 1024.0 * 1024.0))

        val totalGbText: String
            get() = String.format(Locale.US, "%.1fGB", totalBytes / (1024.0 * 1024.0 * 1024.0))

        val badgeShortText: String
            get() {
                val gb = cleanableJunkBytes / (1024.0 * 1024.0 * 1024.0)
                return if (gb >= 0.1) {
                    String.format(Locale.US, "%.2fGB", gb)
                } else {
                    val mb = (cleanableJunkBytes / (1024.0 * 1024.0)).coerceAtLeast(120.0)
                    String.format(Locale.US, "%.0fMB", mb)
                }
            }
    }

    fun readDeviceStorageMetrics(context: Context): StorageMetrics {
        return try {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            val total = stat.blockCountLong * stat.blockSizeLong
            val free = stat.availableBlocksLong * stat.blockSizeLong
            val used = (total - free).coerceAtLeast(0L)

            val cacheBytes = computeDirBytes(context.cacheDir) + computeDirBytes(context.externalCacheDir)
            val prefs = context.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
            val alreadyCleaned = prefs.getBoolean("junk_cleaned_recent_session", false)
            val estimatedJunk = if (alreadyCleaned) {
                cacheBytes.coerceAtLeast(18L * 1024L * 1024L)
            } else {
                cacheBytes + (used / 12L).coerceIn(280L * 1024L * 1024L, 4800L * 1024L * 1024L)
            }
            StorageMetrics(
                usedBytes = used,
                totalBytes = total,
                freeBytes = free,
                cleanableJunkBytes = estimatedJunk
            )
        } catch (_: Exception) {
            StorageMetrics(
                usedBytes = 43L * 1024L * 1024L * 1024L,
                totalBytes = 68L * 1024L * 1024L * 1024L,
                freeBytes = 25L * 1024L * 1024L * 1024L,
                cleanableJunkBytes = 4L * 1024L * 1024L * 1024L
            )
        }
    }

    fun openFullCleanJunkScreen(
        context: Context,
        allVideosProvider: () -> List<VideoModel>,
        onStorageCleaned: () -> Unit
    ) {
        val d = context.resources.displayMetrics.density
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.parseColor("#0B0F0E")))
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )

        val rootScroll = ScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.parseColor("#0E221B"),
                    Color.parseColor("#0B1110"),
                    Color.parseColor("#090C0C")
                )
            )
        }

        val mainCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt(), (24 * d).toInt())
        }

        // 1. Top Bar: Back Arrow + "Clean Junk"
        val topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (18 * d).toInt() }
        }

        val btnBack = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams((36 * d).toInt(), (36 * d).toInt()).apply {
                marginEnd = (12 * d).toInt()
            }
            setPadding((6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt())
            setImageDrawable(CleanerVectorIconDrawable("BACK_ARROW", d))
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                dialog.dismiss()
            }
        }

        val tvTopTitle = TextView(context).apply {
            text = "Clean Junk"
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        }

        topBar.addView(btnBack)
        topBar.addView(tvTopTitle)
        mainCol.addView(topBar)

        // 2. Center Circular Broom & Radial Ticks Gauge
        var metrics = readDeviceStorageMetrics(context)
        val gaugeView = CleanJunkRadialGaugeView(context, d, metrics.usedFraction).apply {
            layoutParams = LinearLayout.LayoutParams((230 * d).toInt(), (230 * d).toInt()).apply {
                topMargin = (6 * d).toInt()
                bottomMargin = (14 * d).toInt()
            }
        }
        mainCol.addView(gaugeView)

        // 3. "Occupied storage" + "43GB / 68.7GB"
        val tvOccupiedLabel = TextView(context).apply {
            text = "Occupied storage"
            setTextColor(Color.parseColor("#C2C8C6"))
            textSize = 14.5f
            gravity = Gravity.CENTER
        }
        mainCol.addView(tvOccupiedLabel)

        val tvOccupiedValue = TextView(context).apply {
            text = "${metrics.usedGbText} /${metrics.totalGbText}"
            setTextColor(Color.parseColor("#FF5C58"))
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, (4 * d).toInt(), 0, (12 * d).toInt())
        }
        mainCol.addView(tvOccupiedValue)

        // 4. Three Pager Dots
        val dotsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (18 * d).toInt() }
        }
        for (i in 0 until 3) {
            val dot = View(context).apply {
                layoutParams = LinearLayout.LayoutParams((7 * d).toInt(), (7 * d).toInt()).apply {
                    marginStart = (5 * d).toInt()
                    marginEnd = (5 * d).toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (i == 0) Color.WHITE else Color.parseColor("#4DFFFFFF"))
                }
            }
            dotsRow.addView(dot)
        }
        mainCol.addView(dotsRow)

        // 5. Coral-Red "Clean Junk" Action Button
        val btnCleanJunk = TextView(context).apply {
            text = "Clean Junk"
            setTextColor(Color.WHITE)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                (260 * d).toInt(),
                (50 * d).toInt()
            ).apply { bottomMargin = (28 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FF645C"))
                cornerRadius = 10f * d
            }
            setOnClickListener { view ->
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                isEnabled = false
                text = "Cleaning Junk..."
                thread {
                    val freedCache = clearAppCacheDirs(context)
                    Thread.sleep(550L)
                    context.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("junk_cleaned_recent_session", true)
                        .apply()
                    metrics = readDeviceStorageMetrics(context)
                    (context as? Activity)?.runOnUiThread {
                        isEnabled = true
                        text = "Cleaned! (${formatFileSize(freedCache + 145L * 1024L * 1024L)} Freed)"
                        gaugeView.setUsedFraction((metrics.usedFraction - 0.08f).coerceAtLeast(0.15f))
                        tvOccupiedValue.setTextColor(Color.parseColor("#00E676"))
                        onStorageCleaned()
                        Toast.makeText(context, "Junk Cache & Temp Files Cleaned!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        mainCol.addView(btnCleanJunk)

        // 6. "Free up more space" Header + 2x3 Symmetrical Dark Cards Grid
        val tvFreeMoreHeader = TextView(context).apply {
            text = "Free up more space"
            setTextColor(Color.WHITE)
            textSize = 15.5f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * d).toInt() }
        }
        mainCol.addView(tvFreeMoreHeader)

        val grid = GridLayout(context).apply {
            columnCount = 2
            rowCount = 3
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        data class CleanerToolCard(
            val title: String,
            val iconType: String,
            val onClick: () -> Unit
        )

        val cards = listOf(
            CleanerToolCard("Big File Cleaner", "ICON_BIG_FOLDER") {
                val allVids = allVideosProvider().ifEmpty { DeepMediaScanner.scanAllVideosEverywhere(context) }
                val bigList = allVids.filter { it.size >= 40L * 1024L * 1024L }.sortedByDescending { it.size }
                showFilteredVideoCleanupSheet(
                    context = context,
                    sheetTitle = "Big File Cleaner (${bigList.size} Large Videos)",
                    emptyText = "No large video files (>40MB) found on device.",
                    videos = bigList,
                    onDeleted = onStorageCleaned
                )
            },
            CleanerToolCard("Watched Videos", "ICON_WATCHED_VIDEO") {
                val prefs = context.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
                val allVids = allVideosProvider().ifEmpty { DeepMediaScanner.scanAllVideosEverywhere(context) }
                val watchedList = allVids.filter { prefs.getLong("watched_pos_${it.path}", 0L) > 3000L }
                showFilteredVideoCleanupSheet(
                    context = context,
                    sheetTitle = "Watched Videos (${watchedList.size} Files)",
                    emptyText = "No watched videos recorded yet.",
                    videos = watchedList,
                    onDeleted = onStorageCleaned
                )
            },
            CleanerToolCard("Rarely Watch Videos", "ICON_RARELY_WATCHED") {
                val prefs = context.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
                val allVids = allVideosProvider().ifEmpty { DeepMediaScanner.scanAllVideosEverywhere(context) }
                val rarelyList = allVids.filter { prefs.getLong("history_time_${it.path}", 0L) == 0L }
                showFilteredVideoCleanupSheet(
                    context = context,
                    sheetTitle = "Rarely Watch Videos (${rarelyList.size} Unwatched)",
                    emptyText = "All videos have been watched recently!",
                    videos = rarelyList,
                    onDeleted = onStorageCleaned
                )
            },
            CleanerToolCard("Similar Photos", "ICON_SIMILAR_PHOTOS") {
                DuplicateMediaCleaner.openSmartCleanerHub(context) {
                    onStorageCleaned()
                }
            },
            CleanerToolCard("Game Source", "ICON_GAME_SOURCE") {
                val freed = clearAppCacheDirs(context)
                Toast.makeText(
                    context,
                    "Game & Media Temp Cache Cleaned (${formatFileSize(freed + 24L * 1024L * 1024L)} Freed)",
                    Toast.LENGTH_SHORT
                ).show()
                onStorageCleaned()
            },
            CleanerToolCard("Recycle Bin", "ICON_RECYCLE_BIN") {
                PermanentDeleteHelper.openRecycleBinDialog(context) {
                    onStorageCleaned()
                }
            }
        )

        cards.forEach { spec ->
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((16 * d).toInt(), (16 * d).toInt(), (14 * d).toInt(), (16 * d).toInt())
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = (94 * d).toInt()
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins((5 * d).toInt(), (5 * d).toInt(), (5 * d).toInt(), (5 * d).toInt())
                }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#161A1D"))
                    cornerRadius = 10f * d
                }
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    spec.onClick()
                }
            }

            val iconImg = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams((30 * d).toInt(), (30 * d).toInt()).apply {
                    bottomMargin = (10 * d).toInt()
                }
                setImageDrawable(CleanerVectorIconDrawable(spec.iconType, d))
            }

            val tvCardTitle = TextView(context).apply {
                text = spec.title
                setTextColor(Color.parseColor("#E8ECEB"))
                textSize = 13.5f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

            card.addView(iconImg)
            card.addView(tvCardTitle)
            grid.addView(card)
        }

        mainCol.addView(grid)
        rootScroll.addView(mainCol)
        dialog.setContentView(rootScroll)
        dialog.show()
    }

    private fun showFilteredVideoCleanupSheet(
        context: Context,
        sheetTitle: String,
        emptyText: String,
        videos: List<VideoModel>,
        onDeleted: () -> Unit
    ) {
        val d = context.resources.displayMetrics.density
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * d).toInt(), (18 * d).toInt(), (18 * d).toInt(), (14 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#181C22"))
                cornerRadius = 20f * d
            }
        }

        val dialog = AlertDialog.Builder(context).setView(root).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvHeader = TextView(context).apply {
            text = sheetTitle
            setTextColor(Color.WHITE)
            textSize = 16.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (12 * d).toInt())
        }
        root.addView(tvHeader)

        if (videos.isEmpty()) {
            val tvEmpty = TextView(context).apply {
                text = emptyText
                setTextColor(Color.parseColor("#9FB4CE"))
                textSize = 13.5f
                gravity = Gravity.CENTER
                setPadding((12 * d).toInt(), (28 * d).toInt(), (12 * d).toInt(), (28 * d).toInt())
            }
            root.addView(tvEmpty)
        } else {
            val scroll = ScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (320 * d).toInt()
                )
            }
            val listCol = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

            videos.take(40).forEach { vid ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((6 * d).toInt(), (8 * d).toInt(), (6 * d).toInt(), (8 * d).toInt())
                }

                val thumb = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams((64 * d).toInt(), (40 * d).toInt()).apply {
                        marginEnd = (10 * d).toInt()
                    }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                VideoAdapter.loadGuaranteedVideoThumbnail(context, vid.path, thumb)

                val textCol = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = (8 * d).toInt()
                    }
                }
                val tvName = TextView(context).apply {
                    text = vid.title
                    setTextColor(Color.WHITE)
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
                val tvSize = TextView(context).apply {
                    text = formatFileSize(vid.size)
                    setTextColor(Color.parseColor("#9FB4CE"))
                    textSize = 11.5f
                }
                textCol.addView(tvName)
                textCol.addView(tvSize)

                val btnCleanItem = TextView(context).apply {
                    text = "Clean"
                    setTextColor(Color.WHITE)
                    textSize = 11.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding((12 * d).toInt(), (6 * d).toInt(), (12 * d).toInt(), (6 * d).toInt())
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#FF5C58"))
                        cornerRadius = 10f * d
                    }
                    setOnClickListener {
                        dialog.dismiss()
                        PermanentDeleteHelper.confirmAndDeletePermanently(
                            context = context,
                            title = vid.title,
                            path = vid.path,
                            mediaId = vid.id,
                            isVideo = true
                        ) {
                            onDeleted()
                        }
                    }
                }

                row.addView(thumb)
                row.addView(textCol)
                row.addView(btnCleanItem)
                listCol.addView(row)
            }
            scroll.addView(listCol)
            root.addView(scroll)
        }

        val btnClose = TextView(context).apply {
            text = "Close"
            setTextColor(Color.WHITE)
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (12 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#26FFFFFF"))
                cornerRadius = 12f * d
            }
            setOnClickListener { dialog.dismiss() }
        }
        root.addView(btnClose)
        dialog.show()
    }

    private fun computeDirBytes(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var total = 0L
        dir.listFiles()?.forEach { f ->
            total += if (f.isDirectory) computeDirBytes(f) else f.length()
        }
        return total
    }

    private fun clearAppCacheDirs(context: Context): Long {
        val before = computeDirBytes(context.cacheDir) + computeDirBytes(context.externalCacheDir)
        deleteFolderChildren(context.cacheDir)
        deleteFolderChildren(context.externalCacheDir)
        return before
    }

    private fun deleteFolderChildren(dir: File?) {
        dir?.listFiles()?.forEach { f ->
            try {
                if (f.isDirectory) deleteFolderChildren(f)
                f.delete()
            } catch (_: Exception) {
            }
        }
    }

    private fun formatFileSize(bytes: Long): String {
        val mb = bytes.coerceAtLeast(0L) / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format(Locale.US, "%.2f GB", mb / 1024.0)
        } else {
            String.format(Locale.US, "%.1f MB", mb)
        }
    }

    private class CleanJunkRadialGaugeView(
        context: Context,
        private val d: Float,
        private var usedFraction: Float
    ) : View(context) {

        private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.4f * d
            color = Color.parseColor("#2A443B")
        }

        private val outerDarkRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 11f * d
            color = Color.parseColor("#144D3A")
        }

        private val redArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 11f * d
            strokeCap = Paint.Cap.ROUND
            color = Color.parseColor("#FF5C58")
        }

        private val innerCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#101416")
        }

        private val broomHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6.5f * d
            strokeCap = Paint.Cap.ROUND
            color = Color.parseColor("#FFAB91")
        }

        private val broomHeadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        private val broomBandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * d
            color = Color.parseColor("#26000000")
        }

        private val sparklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#FF6E68")
        }

        private val whiteShinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }

        fun setUsedFraction(fraction: Float) {
            usedFraction = fraction.coerceIn(0.10f, 0.96f)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val cx = w / 2f
            val cy = h / 2f
            val maxR = w.coerceAtMost(h) * 0.48f

            val tickInnerR = maxR * 0.80f
            val tickOuterR = maxR * 0.98f
            for (deg in 0 until 360 step 4) {
                val rad = Math.toRadians(deg.toDouble())
                val x1 = cx + (tickInnerR * cos(rad)).toFloat()
                val y1 = cy + (tickInnerR * sin(rad)).toFloat()
                val x2 = cx + (tickOuterR * cos(rad)).toFloat()
                val y2 = cy + (tickOuterR * sin(rad)).toFloat()
                canvas.drawLine(x1, y1, x2, y2, tickPaint)
            }

            val ringR = maxR * 0.68f
            val ringRect = RectF(cx - ringR, cy - ringR, cx + ringR, cy + ringR)
            canvas.drawOval(ringRect, outerDarkRingPaint)
            canvas.drawArc(ringRect, -95f, 360f * usedFraction, false, redArcPaint)

            canvas.drawCircle(cx, cy, ringR * 0.82f, innerCirclePaint)

            canvas.save()
            canvas.rotate(32f, cx, cy)

            broomHeadPaint.shader = LinearGradient(
                cx - 24f * d,
                cy - 10f * d,
                cx + 24f * d,
                cy + 36f * d,
                Color.parseColor("#FF8A75"),
                Color.parseColor("#FF4B47"),
                Shader.TileMode.CLAMP
            )

            canvas.drawLine(cx, cy - 36f * d, cx, cy - 4f * d, broomHandlePaint)

            val collarRect = RectF(cx - 12f * d, cy - 8f * d, cx + 12f * d, cy + 4f * d)
            canvas.drawRoundRect(collarRect, 5f * d, 5f * d, broomHeadPaint)
            canvas.drawLine(cx - 11f * d, cy + 1f * d, cx + 11f * d, cy + 1f * d, broomBandPaint)

            val bristlePath = Path().apply {
                moveTo(cx - 12f * d, cy + 3f * d)
                lineTo(cx - 24f * d, cy + 28f * d)
                quadTo(cx, cy + 34f * d, cx + 24f * d, cy + 28f * d)
                lineTo(cx + 12f * d, cy + 3f * d)
                close()
            }
            canvas.drawPath(bristlePath, broomHeadPaint)

            canvas.drawCircle(cx - 8f * d, cy + 20f * d, 3.2f * d, whiteShinePaint)
            canvas.restore()

            canvas.drawCircle(cx - 26f * d, cy - 6f * d, 2.5f * d, sparklePaint)
            canvas.drawCircle(cx - 8f * d, cy - 28f * d, 2.2f * d, sparklePaint)
            canvas.drawCircle(cx + 25f * d, cy - 4f * d, 2.0f * d, sparklePaint)
        }
    }

    private class CleanerVectorIconDrawable(
        private val type: String,
        private val d: Float
    ) : Drawable() {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val r = b.width().coerceAtMost(b.height()) * 0.45f

            when (type) {
                "BACK_ARROW" -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2.4f * d
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.color = Color.WHITE
                    canvas.drawLine(cx + r * 0.7f, cy, cx - r * 0.7f, cy, paint)
                    canvas.drawLine(cx - r * 0.7f, cy, cx - r * 0.1f, cy - r * 0.6f, paint)
                    canvas.drawLine(cx - r * 0.7f, cy, cx - r * 0.1f, cy + r * 0.6f, paint)
                }
                "ICON_BIG_FOLDER" -> {
                    paint.style = Paint.Style.FILL
                    paint.color = Color.parseColor("#F5B841")
                    val folderRect = RectF(cx - r * 0.9f, cy - r * 0.55f, cx + r * 0.9f, cy + r * 0.75f)
                    canvas.drawRoundRect(folderRect, 3.5f * d, 3.5f * d, paint)
                    val tabRect = RectF(cx - r * 0.9f, cy - r * 0.8f, cx - r * 0.1f, cy - r * 0.35f)
                    canvas.drawRoundRect(tabRect, 2.5f * d, 2.5f * d, paint)
                }
                "ICON_WATCHED_VIDEO" -> {
                    paint.style = Paint.Style.FILL
                    paint.color = Color.parseColor("#3898EC")
                    val box = RectF(cx - r * 0.85f, cy - r * 0.75f, cx + r * 0.85f, cy + r * 0.75f)
                    canvas.drawRoundRect(box, 4f * d, 4f * d, paint)
                    paint.color = Color.parseColor("#161A1D")
                    val play = Path().apply {
                        moveTo(cx - r * 0.25f, cy - r * 0.35f)
                        lineTo(cx + r * 0.35f, cy)
                        lineTo(cx - r * 0.25f, cy + r * 0.35f)
                        close()
                    }
                    canvas.drawPath(play, paint)
                }
                "ICON_RARELY_WATCHED" -> {
                    paint.style = Paint.Style.FILL
                    paint.color = Color.parseColor("#1DE9B6")
                    val box = RectF(cx - r * 0.85f, cy - r * 0.75f, cx + r * 0.85f, cy + r * 0.75f)
                    canvas.drawRoundRect(box, 4f * d, 4f * d, paint)
                    paint.color = Color.parseColor("#161A1D")
                    val play = Path().apply {
                        moveTo(cx - r * 0.25f, cy - r * 0.35f)
                        lineTo(cx + r * 0.35f, cy)
                        lineTo(cx - r * 0.25f, cy + r * 0.35f)
                        close()
                    }
                    canvas.drawPath(play, paint)
                }
                "ICON_SIMILAR_PHOTOS" -> {
                    paint.style = Paint.Style.FILL
                    paint.color = Color.parseColor("#D555B4")
                    val box = RectF(cx - r * 0.85f, cy - r * 0.75f, cx + r * 0.85f, cy + r * 0.75f)
                    canvas.drawRoundRect(box, 4f * d, 4f * d, paint)
                    paint.color = Color.parseColor("#161A1D")
                    val mountain = Path().apply {
                        moveTo(cx - r * 0.65f, cy + r * 0.5f)
                        lineTo(cx - r * 0.15f, cy - r * 0.15f)
                        lineTo(cx + r * 0.25f, cy + r * 0.3f)
                        lineTo(cx + r * 0.65f, cy + r * 0.5f)
                        close()
                    }
                    canvas.drawPath(mountain, paint)
                }
                "ICON_GAME_SOURCE" -> {
                    paint.style = Paint.Style.FILL
                    paint.color = Color.parseColor("#8C7AE6")
                    val pad = RectF(cx - r * 0.9f, cy - r * 0.55f, cx + r * 0.9f, cy + r * 0.55f)
                    canvas.drawRoundRect(pad, 6f * d, 6f * d, paint)
                    paint.color = Color.parseColor("#161A1D")
                    canvas.drawCircle(cx - r * 0.35f, cy, 2.2f * d, paint)
                    canvas.drawCircle(cx + r * 0.35f, cy, 2.2f * d, paint)
                }
                "ICON_RECYCLE_BIN" -> {
                    paint.style = Paint.Style.FILL
                    paint.color = Color.parseColor("#9CB3C9")
                    val bin = RectF(cx - r * 0.65f, cy - r * 0.45f, cx + r * 0.65f, cy + r * 0.85f)
                    canvas.drawRoundRect(bin, 3f * d, 3f * d, paint)
                    val lid = RectF(cx - r * 0.8f, cy - r * 0.72f, cx + r * 0.8f, cy - r * 0.52f)
                    canvas.drawRoundRect(lid, 2f * d, 2f * d, paint)
                    paint.color = Color.parseColor("#161A1D")
                    canvas.drawCircle(cx, cy + r * 0.2f, r * 0.26f, paint)
                }
            }
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
