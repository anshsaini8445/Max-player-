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
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.ArrayList
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.abs

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
                    setColor(Color.parseColor("#0A0A10"))
                    cornerRadius = 24f * d
                    setStroke((1.6f * d).toInt(), Color.parseColor("#00FFFF"))
                }
            }

            val dialog = AlertDialog.Builder(context)
                .setView(rootCard)
                .create()
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            val headerRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((8 * d).toInt(), (2 * d).toInt(), (4 * d).toInt(), (12 * d).toInt())
            }

            val tvHeader = TextView(context).apply {
                text = titleText
                setTextColor(Color.parseColor("#00FFFF"))
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val btnClose = TextView(context).apply {
                text = "✕"
                setTextColor(Color.WHITE)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams((30 * d).toInt(), (30 * d).toInt()).apply {
                    marginStart = (8 * d).toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#26FFFFFF"))
                }
                setOnClickListener { dialog.dismiss() }
            }

            headerRow.addView(tvHeader)
            headerRow.addView(btnClose)
            rootCard.addView(headerRow)

            val topDivider = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (1 * d).toInt()
                )
                setBackgroundColor(Color.parseColor("#3300FFFF"))
            }
            rootCard.addView(topDivider)

            val scroll = ScrollView(context)
            val listLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            items.forEachIndexed { idx, label ->
                val isDelete = label.contains("Delete", ignoreCase = true)
                val isActive = label.contains("[Active]", ignoreCase = true)

                val tvItem = TextView(context).apply {
                    text = label
                    setTextColor(
                        when {
                            isDelete -> Color.parseColor("#FF5252")
                            isActive -> Color.parseColor("#00FFFF")
                            else -> Color.WHITE
                        }
                    )
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
                        setBackgroundColor(Color.parseColor("#18FFFFFF"))
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
    private lateinit var topPillsScrollView: HorizontalScrollView
    private lateinit var folderBackHeaderBar: LinearLayout
    private lateinit var tvOpenedFolderTitle: TextView
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

    private var activeCategory = 0 // 0=All Videos, 1=Shorts & Status, 2=Folders (Full Screen), 3=Favorites
    private var currentlyOpenedFolderPath: String? = null
    private var sortMode = 0
    private var isGridView = false
    private var isScanning = false
    private var accentColor = Color.parseColor("#00FFFF")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val d = ctx.resources.displayMetrics.density

        val themePrefs = ctx.getSharedPreferences("MAX_THEME_PREFS", Context.MODE_PRIVATE)
        val bgHex = themePrefs.getString("app_bg_color", "#0A0A10") ?: "#0A0A10"
        val accentHex = themePrefs.getString("app_accent_color", "#00FFFF") ?: "#00FFFF"
        accentColor = try {
            Color.parseColor(accentHex)
        } catch (_: Exception) {
            Color.parseColor("#00FFFF")
        }

        rootVideoLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(
                try {
                    Color.parseColor(bgHex)
                } catch (_: Exception) {
                    Color.parseColor("#0A0A10")
                }
            )
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val topBarRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (8 * d).toInt())
        }

        topPillsScrollView = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val pillsRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        pillAllVideos = createCategoryPill("All Videos", true, d) {
            switchCategoryTab(0)
        }

        pillShortsReels = createCategoryPill("Shorts & Status", false, d) {
            if (activeCategory == 1) {
                val shortsList = DeepMediaScanner.filterShortsAndStatusVideos(allScannedVideos)
                if (shortsList.isNotEmpty()) {
                    val paths = ArrayList(shortsList.map { it.path })
                    startActivity(Intent(ctx, ShortsPlayerActivity::class.java).apply {
                        putStringArrayListExtra("SHORTS_LIST", paths)
                    })
                }
            } else {
                switchCategoryTab(1)
            }
        }

        pillFolders = createCategoryPill("Folders", false, d) {
            currentlyOpenedFolderPath = null
            switchCategoryTab(2)
        }

        pillFavorites = createCategoryPill("Favorites ♥", false, d) {
            switchCategoryTab(3)
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
        topPillsScrollView.addView(pillsRow)

        btnSortAndViewMode = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((36 * d).toInt(), (36 * d).toInt()).apply {
                marginStart = (8 * d).toInt()
            }
            setPadding((7 * d).toInt(), (7 * d).toInt(), (7 * d).toInt(), (7 * d).toInt())
            setImageResource(android.R.drawable.ic_menu_sort_by_size)
            setColorFilter(Color.WHITE)
            setOnClickListener { showSortAndToolsDialog() }
        }

        topBarRow.addView(topPillsScrollView)
        topBarRow.addView(btnSortAndViewMode)
        rootVideoLayout.addView(topBarRow)

        // Full-Screen Folder Navigation Sub-Header (Visible when inside a specific folder)
        folderBackHeaderBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding((14 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141C2B"))
            }
        }
        val btnBackToFolders = TextView(ctx).apply {
            text = "←  All Folders"
            setTextColor(accentColor)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((8 * d).toInt(), (4 * d).toInt(), (14 * d).toInt(), (4 * d).toInt())
            setOnClickListener {
                currentlyOpenedFolderPath = null
                refreshVideoList()
            }
        }
        tvOpenedFolderTitle = TextView(ctx).apply {
            text = ""
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        folderBackHeaderBar.addView(btnBackToFolders)
        folderBackHeaderBar.addView(tvOpenedFolderTitle)
        rootVideoLayout.addView(folderBackHeaderBar)

        rvVideos = RecyclerView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setPadding((10 * d).toInt(), (4 * d).toInt(), (10 * d).toInt(), (14 * d).toInt())
            clipToPadding = false
            setHasFixedSize(true)
            setItemViewCacheSize(24)
        }
        rvVideos.layoutManager = LinearLayoutManager(ctx)
        videoAdapter = VideoAdapter(ctx, displayedVideos)
        rvVideos.adapter = videoAdapter
        InShotScrollAnimator.attachToRecyclerView(rvVideos)
        attachLeftRightSwipeGestureToSwitchTabs(ctx)
        rootVideoLayout.addView(rvVideos)

        tvEmptyVideos = TextView(ctx).apply {
            text = "No videos found in this category"
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

        // Instant zero-lag load from memory cache if available, then background sync
        val cached = DeepMediaScanner.getCachedVideosInstant()
        if (cached.isNotEmpty()) {
            allScannedVideos.clear()
            allScannedVideos.addAll(cached)
            refreshVideoList()
        }
        scanAllVideosInBackground(forceRefresh = false)

        return rootVideoLayout
    }

    override fun onResume() {
        super.onResume()
        scanAllVideosInBackground(forceRefresh = false)
    }

    // Horizontal Left/Right Swipe Gesture to smoothly switch between All Videos, Shorts & Status, Folders, and Favorites
    private fun attachLeftRightSwipeGestureToSwitchTabs(context: Context) {
        val swipeDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (abs(dx) > abs(dy) * 1.65f && abs(dx) > 120f && abs(velocityX) > 250f) {
                    if (dx < 0) {
                        // Swipe Right-to-Left -> Next Category Tab
                        val nextTab = (activeCategory + 1).coerceAtMost(3)
                        if (nextTab != activeCategory) {
                            currentlyOpenedFolderPath = null
                            switchCategoryTab(nextTab)
                            return true
                        }
                    } else {
                        // Swipe Left-to-Right -> Previous Category Tab
                        if (activeCategory == 2 && currentlyOpenedFolderPath != null) {
                            currentlyOpenedFolderPath = null
                            refreshVideoList()
                            return true
                        }
                        val prevTab = (activeCategory - 1).coerceAtLeast(0)
                        if (prevTab != activeCategory) {
                            currentlyOpenedFolderPath = null
                            switchCategoryTab(prevTab)
                            return true
                        }
                    }
                }
                return false
            }
        })

        rvVideos.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                swipeDetector.onTouchEvent(e)
                return false
            }
        })

        rootVideoLayout.setOnTouchListener { _, event ->
            swipeDetector.onTouchEvent(event)
            true
        }
    }

    private fun switchCategoryTab(targetCategory: Int) {
        activeCategory = targetCategory.coerceIn(0, 3)
        updatePillsVisual()
        refreshVideoList()
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
            setTextColor(if (selected) Color.parseColor("#0A0A10") else Color.WHITE)
            background = GradientDrawable().apply {
                setColor(if (selected) accentColor else Color.parseColor("#182232"))
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
            tv.setTextColor(if (isSel) Color.parseColor("#0A0A10") else Color.WHITE)
            tv.background = GradientDrawable().apply {
                setColor(if (isSel) accentColor else Color.parseColor("#182232"))
                cornerRadius = 18f * d
            }
            if (isSel) {
                topPillsScrollView.post {
                    topPillsScrollView.smoothScrollTo(tv.left - (40 * d).toInt(), 0)
                }
            }
        }
    }

    private fun scanAllVideosInBackground(forceRefresh: Boolean) {
        if (isScanning) return
        isScanning = true
        val ctx = context ?: return

        thread {
            val scanned = DeepMediaScanner.scanAllVideosEverywhere(ctx, forceRefresh)
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

        // Full-Screen Folders Mode (Replaces the entire background screen instead of showing a popup dialog)
        if (activeCategory == 2 && currentlyOpenedFolderPath == null) {
            folderBackHeaderBar.visibility = View.GONE
            showFullScreenFoldersInRecyclerView(ctx)
            return
        }

        val base = when (activeCategory) {
            1 -> {
                folderBackHeaderBar.visibility = View.GONE
                DeepMediaScanner.filterShortsAndStatusVideos(allScannedVideos)
            }
            2 -> {
                val targetParent = currentlyOpenedFolderPath ?: ""
                folderBackHeaderBar.visibility = View.VISIBLE
                tvOpenedFolderTitle.text = File(targetParent).name.ifEmpty { "Folder Videos" }
                allScannedVideos.filter { (File(it.path).parent ?: "") == targetParent }
            }
            3 -> {
                folderBackHeaderBar.visibility = View.GONE
                allScannedVideos.filter { prefs.getBoolean("fav_${it.path}", false) }
            }
            else -> {
                folderBackHeaderBar.visibility = View.GONE
                allScannedVideos
            }
        }

        val sorted = when (sortMode) {
            1 -> base.sortedBy { it.title.lowercase(Locale.US) }
            2 -> base.sortedByDescending { it.size }
            3 -> base.sortedByDescending { it.duration }
            else -> base
        }

        rvVideos.layoutManager = if (isGridView) {
            GridLayoutManager(ctx, 2)
        } else {
            LinearLayoutManager(ctx)
        }
        if (rvVideos.adapter !== videoAdapter) {
            rvVideos.adapter = videoAdapter
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

    // Full-Screen Folder Cards Adapter inside the main screen
    private fun showFullScreenFoldersInRecyclerView(context: Context) {
        val d = context.resources.displayMetrics.density
        val folderGroups = allScannedVideos.groupBy {
            File(it.path).parent ?: "Internal Storage"
        }.entries.sortedByDescending { it.value.size }

        if (folderGroups.isEmpty()) {
            rvVideos.visibility = View.GONE
            tvEmptyVideos.visibility = View.VISIBLE
            tvEmptyVideos.text = "No video folders found"
            return
        }

        tvEmptyVideos.visibility = View.GONE
        rvVideos.visibility = View.VISIBLE
        rvVideos.layoutManager = LinearLayoutManager(context)

        rvVideos.adapter = object : RecyclerView.Adapter<FolderCardVH>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderCardVH {
                val card = CardView(context).apply {
                    radius = 16f * d
                    cardElevation = 4f * d
                    setCardBackgroundColor(Color.parseColor("#142538"))
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins((6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt())
                    }
                }

                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((16 * d).toInt(), (14 * d).toInt(), (16 * d).toInt(), (14 * d).toInt())
                }

                val tvFolderIcon = TextView(context).apply {
                    text = "📁"
                    textSize = 28f
                    setPadding(0, 0, (14 * d).toInt(), 0)
                }

                val textCol = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                val tvName = TextView(context).apply {
                    setTextColor(Color.WHITE)
                    textSize = 15.5f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }

                val tvCountAndSize = TextView(context).apply {
                    setTextColor(accentColor)
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, (3 * d).toInt(), 0, 0)
                }

                textCol.addView(tvName)
                textCol.addView(tvCountAndSize)

                val tvArrow = TextView(context).apply {
                    text = "❯"
                    setTextColor(accentColor)
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                }

                row.addView(tvFolderIcon)
                row.addView(textCol)
                row.addView(tvArrow)
                card.addView(row)
                return FolderCardVH(card, tvName, tvCountAndSize)
            }

            override fun onBindViewHolder(holder: FolderCardVH, position: Int) {
                InShotScrollAnimator.animateRowOnBind(holder.itemView)
                val entry = folderGroups[position]
                val parentPath = entry.key
                val videosInFolder = entry.value
                val folderTitle = File(parentPath).name.ifEmpty { "Internal Storage" }
                val totalBytes = videosInFolder.sumOf { it.size }
                val mb = totalBytes / (1024.0 * 1024.0)
                val sizeText = if (mb >= 1024.0) {
                    String.format(Locale.US, "%.2f GB", mb / 1024.0)
                } else {
                    String.format(Locale.US, "%.1f MB", mb)
                }

                holder.tvName.text = folderTitle
                holder.tvCountAndSize.text = "${videosInFolder.size} Videos  •  $sizeText"

                holder.itemView.setOnClickListener {
                    currentlyOpenedFolderPath = parentPath
                    refreshVideoList()
                }
            }

            override fun getItemCount(): Int = folderGroups.size
        }
    }

    private class FolderCardVH(
        view: View,
        val tvName: TextView,
        val tvCountAndSize: TextView
    ) : RecyclerView.ViewHolder(view)

    private fun showSortAndToolsDialog() {
        val ctx = context ?: return
        val items = arrayOf(
            if (sortMode == 0) "🕒   [Active] Sort: Newest First" else "🕒   Sort: Newest First",
            if (sortMode == 1) "🔤   [Active] Sort: Name (A to Z)" else "🔤   Sort: Name (A to Z)",
            if (sortMode == 2) "📦   [Active] Sort: Largest Size First" else "📦   Sort: Largest Size First",
            if (sortMode == 3) "⏱️   [Active] Sort: Longest Duration" else "⏱️   Sort: Longest Duration",
            if (isGridView) "📋   Layout: Switch to List View" else "🔲   Layout: Switch to 2-Column Grid View",
            "📱   Open Vertical Shorts & Reels Player",
            "💚   Open WhatsApp Status Saver",
            "🌐   Stream Video from URL / Link"
        )
        showBrightVideoMenuDialog(ctx, "Video Library Options", items) { which ->
            when (which) {
                0, 1, 2, 3 -> {
                    sortMode = which
                    refreshVideoList()
                }
                4 -> {
                    isGridView = !isGridView
                    refreshVideoList()
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
