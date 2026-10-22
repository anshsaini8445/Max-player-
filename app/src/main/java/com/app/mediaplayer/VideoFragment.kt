package com.app.mediaplayer

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.ArrayList
import java.util.Locale
import kotlin.concurrent.thread

class VideoFragment : Fragment() {

    companion object {
        fun showBrightVideoMenuDialog(
            context: Context,
            titleText: String,
            items: Array<String>,
            onSelect: (Int) -> Unit
        ) {
            val d = context.resources.displayMetrics.density
            val rootCard = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((18 * d).toInt(), (18 * d).toInt(), (18 * d).toInt(), (14 * d).toInt())
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#13263B"))
                    cornerRadius = 22f * d
                    setStroke((1.5f * d).toInt(), Color.parseColor("#5CE1E6"))
                }
            }

            val tvHeader = TextView(context).apply {
                text = titleText
                setTextColor(Color.parseColor("#5CE1E6"))
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding((10 * d).toInt(), (4 * d).toInt(), (10 * d).toInt(), (12 * d).toInt())
            }
            rootCard.addView(tvHeader)

            val topDivider = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (1 * d).toInt()
                )
                setBackgroundColor(Color.parseColor("#33FFFFFF"))
            }
            rootCard.addView(topDivider)

            val scroll = android.widget.ScrollView(context)
            val listLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            val dialog = AlertDialog.Builder(context)
                .setView(rootCard)
                .create()
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            items.forEachIndexed { idx, label ->
                val tvItem = TextView(context).apply {
                    text = label
                    setTextColor(Color.WHITE)
                    textSize = 14.5f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt())
                    setOnClickListener {
                        dialog.dismiss()
                        onSelect(idx)
                    }
                }
                listLayout.addView(tvItem)
                if (idx < items.size - 1) {
                    val line = View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            (1 * d).toInt()
                        )
                        setBackgroundColor(Color.parseColor("#1AFFFFFF"))
                    }
                    listLayout.addView(line)
                }
            }

            scroll.addView(listLayout)
            rootCard.addView(scroll)
            dialog.show()
        }
    }

    private lateinit var rootVideoLayout: LinearLayout
    private lateinit var rvVideos: RecyclerView
    private lateinit var tvEmptyVideos: TextView
    private lateinit var pillAllVideos: TextView
    private lateinit var pillShortsReels: TextView
    private lateinit var pillFolders: TextView
    private lateinit var pillFavorites: TextView
    private lateinit var pillStatusSaver: TextView
    private lateinit var pillStreamUrl: TextView
    private lateinit var btnSortAndViewMode: ImageView

    private val allScannedVideos = mutableListOf<VideoModel>()
    private val displayedVideos = mutableListOf<VideoModel>()
    private lateinit var videoAdapter: VideoAdapter

    private var activeCategory = 0
    private var sortMode = 0
    private var isGridView = false
    private var isScanning = false
    private var accentColor = Color.parseColor("#5CE1E6")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val d = ctx.resources.displayMetrics.density

        val themePrefs = ctx.getSharedPreferences("MAX_THEME_PREFS", Context.MODE_PRIVATE)
        val bgHex = themePrefs.getString("app_bg_color", "#0D1B2A") ?: "#0D1B2A"
        val accentHex = themePrefs.getString("app_accent_color", "#5CE1E6") ?: "#5CE1E6"
        accentColor = try { Color.parseColor(accentHex) } catch (_: Exception) { Color.parseColor("#5CE1E6") }

        rootVideoLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(try { Color.parseColor(bgHex) } catch (_: Exception) { Color.parseColor("#0D1B2A") })
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Top Filter & Feature Bar: All Videos | Shorts & Reels (1s-90s) | Folders | Favorites | Status Saver | Stream URL
        val topBarRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (8 * d).toInt())
        }

        val hsv = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val pillsRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        pillAllVideos = createCategoryPill("All Videos", true, d) {
            activeCategory = 0
            updatePillsVisual()
            refreshVideoList()
        }

        pillShortsReels = createCategoryPill("Shorts & Status", false, d) {
            activeCategory = 1
            updatePillsVisual()
            refreshVideoList()
            // Also offer instant 1-tap vertical swipe launch if user taps again
            val shortsList = DeepMediaScanner.filterShortsAndStatusVideos(allScannedVideos)
            if (shortsList.isNotEmpty()) {
                val paths = ArrayList(shortsList.map { it.path })
                startActivity(Intent(ctx, ShortsPlayerActivity::class.java).apply {
                    putStringArrayListExtra("SHORTS_LIST", paths)
                })
            }
        }

        pillFolders = createCategoryPill("Folders", false, d) {
            activeCategory = 2
            updatePillsVisual()
            showFoldersDialog()
        }

        pillFavorites = createCategoryPill("Favorites *", false, d) {
            activeCategory = 3
            updatePillsVisual()
            refreshVideoList()
        }

        pillStatusSaver = createCategoryPill("Status Saver", false, d) {
            StatusSaverHelper.openWhatsAppStatusSaverDialog(ctx)
        }

        pillStreamUrl = createCategoryPill("Stream Link", false, d) {
            NetworkStreamDialog.showNetworkStreamInputDialog(ctx)
        }

        pillsRow.addView(pillAllVideos)
        pillsRow.addView(pillShortsReels)
        pillsRow.addView(pillFolders)
        pillsRow.addView(pillFavorites)
        pillsRow.addView(pillStatusSaver)
        pillsRow.addView(pillStreamUrl)
        hsv.addView(pillsRow)

        btnSortAndViewMode = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((36 * d).toInt(), (36 * d).toInt()).apply {
                marginStart = (8 * d).toInt()
            }
            setPadding((7 * d).toInt(), (7 * d).toInt(), (7 * d).toInt(), (7 * d).toInt())
            setImageResource(android.R.drawable.ic_menu_sort_by_size)
            setColorFilter(Color.WHITE)
            setOnClickListener { showSortAndToolsDialog() }
        }

        topBarRow.addView(hsv)
        topBarRow.addView(btnSortAndViewMode)
        rootVideoLayout.addView(topBarRow)

        rvVideos = RecyclerView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setPadding((10 * d).toInt(), (4 * d).toInt(), (10 * d).toInt(), (14 * d).toInt())
            clipToPadding = false
        }
        rvVideos.layoutManager = LinearLayoutManager(ctx)
        videoAdapter = VideoAdapter(ctx, displayedVideos)
        rvVideos.adapter = videoAdapter
        InShotScrollAnimator.attachToRecyclerView(rvVideos)
        rootVideoLayout.addView(rvVideos)

        tvEmptyVideos = TextView(ctx).apply {
            text = "Scanning device storage for videos..."
            setTextColor(Color.WHITE)
            textSize = 14.5f
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        rootVideoLayout.addView(tvEmptyVideos)

        scanAllVideosInBackground()
        return rootVideoLayout
    }

    override fun onResume() {
        super.onResume()
        scanAllVideosInBackground()
    }

    private fun createCategoryPill(
        label: String,
        selected: Boolean,
        d: Float,
        onClick: () -> Unit
    ): TextView {
        return TextView(requireContext()).apply {
            text = label
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((14 * d).toInt(), (7 * d).toInt(), (14 * d).toInt(), (7 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (8 * d).toInt() }
            setTextColor(if (selected) Color.BLACK else Color.WHITE)
            background = GradientDrawable().apply {
                setColor(if (selected) accentColor else Color.parseColor("#182B42"))
                cornerRadius = 18f * d
            }
            setOnClickListener { onClick() }
        }
    }

    private fun updatePillsVisual() {
        val d = resources.displayMetrics.density
        val pills = listOf(pillAllVideos, pillShortsReels, pillFolders, pillFavorites)
        pills.forEachIndexed { index, tv ->
            val isSel = index == activeCategory
            tv.setTextColor(if (isSel) Color.BLACK else Color.WHITE)
            tv.background = GradientDrawable().apply {
                setColor(if (isSel) accentColor else Color.parseColor("#182B42"))
                cornerRadius = 18f * d
            }
        }
    }

    private fun scanAllVideosInBackground() {
        if (isScanning) return
        isScanning = true
        val ctx = context ?: return

        thread {
            val scanned = DeepMediaScanner.scanAllVideosEverywhere(ctx)
            activity?.runOnUiThread {
                isScanning = false
                if (!isAdded) return@runOnUiThread
                allScannedVideos.clear()
                allScannedVideos.addAll(scanned)
                refreshVideoList()
            }
        }
    }

    private fun refreshVideoList() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)

        val base = when (activeCategory) {
            1 -> DeepMediaScanner.filterShortsAndStatusVideos(allScannedVideos)
            3 -> allScannedVideos.filter { prefs.getBoolean("fav_${it.path}", false) }
            else -> allScannedVideos
        }

        val sorted = when (sortMode) {
            1 -> base.sortedBy { it.title.lowercase(Locale.US) }
            2 -> base.sortedByDescending { it.size }
            3 -> base.sortedByDescending { it.duration }
            else -> base
        }

        displayedVideos.clear()
        displayedVideos.addAll(sorted)
        videoAdapter.notifyDataSetChanged()

        if (displayedVideos.isEmpty()) {
            rvVideos.visibility = View.GONE
            tvEmptyVideos.visibility = View.VISIBLE
            tvEmptyVideos.text = "No videos found in this category"
        } else {
            tvEmptyVideos.visibility = View.GONE
            rvVideos.visibility = View.VISIBLE
        }
    }

    private fun showFoldersDialog() {
        val ctx = context ?: return
        val folderMap = allScannedVideos.groupBy {
            File(it.path).parentFile?.name ?: "Internal Storage"
        }
        if (folderMap.isEmpty()) {
            Toast.makeText(ctx, "No video folders found", Toast.LENGTH_SHORT).show()
            return
        }
        val folderNames = folderMap.keys.toList()
        val labels = folderNames.map { name ->
            "$name (${folderMap[name]?.size ?: 0} Videos)"
        }.toTypedArray()

        showBrightVideoMenuDialog(ctx, "Select Video Folder", labels) { which ->
            val chosenFolder = folderNames[which]
            val list = folderMap[chosenFolder] ?: emptyList()
            displayedVideos.clear()
            displayedVideos.addAll(list)
            videoAdapter.notifyDataSetChanged()
        }
    }

    private fun showSortAndToolsDialog() {
        val ctx = context ?: return
        val items = arrayOf(
            if (sortMode == 0) "[Active] Sort: Newest First" else "Sort: Newest First",
            if (sortMode == 1) "[Active] Sort: Name (A to Z)" else "Sort: Name (A to Z)",
            if (sortMode == 2) "[Active] Sort: Largest Size First" else "Sort: Largest Size First",
            if (sortMode == 3) "[Active] Sort: Longest Duration" else "Sort: Longest Duration",
            if (isGridView) "Layout: Switch to List View" else "Layout: Switch to 2-Column Grid View",
            "Open Vertical Shorts & Reels Player",
            "Open WhatsApp Status Saver",
            "Stream Video from URL / Link"
        )
        showBrightVideoMenuDialog(ctx, "Video Library Options", items) { which ->
            when (which) {
                0, 1, 2, 3 -> {
                    sortMode = which
                    refreshVideoList()
                }
                4 -> {
                    isGridView = !isGridView
                    rvVideos.layoutManager = if (isGridView) {
                        GridLayoutManager(ctx, 2)
                    } else {
                        LinearLayoutManager(ctx)
                    }
                    videoAdapter.notifyDataSetChanged()
                }
                5 -> {
                    val shorts = DeepMediaScanner.filterShortsAndStatusVideos(allScannedVideos)
                    val paths = ArrayList((if (shorts.isNotEmpty()) shorts else allScannedVideos).map { it.path })
                    startActivity(Intent(ctx, ShortsPlayerActivity::class.java).apply {
                        putStringArrayListExtra("SHORTS_LIST", paths)
                    })
                }
                6 -> StatusSaverHelper.openWhatsAppStatusSaverDialog(ctx)
                7 -> NetworkStreamDialog.showNetworkStreamInputDialog(ctx)
            }
        }
    }
}
