package com.app.mediaplayer

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.ArrayList
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
class VideoFragment : Fragment() {

    companion object {
        private var bgBarVisible = false
        private var bgBarTitle = ""
        private var bgBarPath = ""
        private var bgBarPlaying = false
        private var bgBarPositionMs = 0L
        private var bgBarDurationMs = 100000L

        fun updateBackgroundBarState(
            visible: Boolean,
            title: String,
            path: String,
            isPlaying: Boolean,
            positionMs: Long,
            durationMs: Long
        ) {
            bgBarVisible = visible
            bgBarTitle = title
            bgBarPath = path
            bgBarPlaying = isPlaying
            bgBarPositionMs = positionMs.coerceAtLeast(0L)
            bgBarDurationMs = durationMs.coerceAtLeast(1000L)
        }

        fun showBrightVideoMenuDialog(
            context: Context,
            headerTitle: String,
            items: Array<String>,
            onSelect: (Int) -> Unit
        ) {
            val d = context.resources.displayMetrics.density
            val rootCard = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((18 * d).toInt(), (18 * d).toInt(), (18 * d).toInt(), (14 * d).toInt())
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#22262A"))
                    cornerRadius = 16f * d
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
                text = headerTitle
                setTextColor(Color.WHITE)
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val btnClose = TextView(context).apply {
                text = "Close"
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding((12 * d).toInt(), (6 * d).toInt(), (12 * d).toInt(), (6 * d).toInt())
                background = GradientDrawable().apply {
                    cornerRadius = 12f * d
                    setColor(Color.parseColor("#33FFFFFF"))
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
                setBackgroundColor(Color.parseColor("#22FFFFFF"))
            }
            rootCard.addView(topDivider)

            val scroll = ScrollView(context)
            val listLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, (6 * d).toInt(), 0, (4 * d).toInt())
            }

            items.forEachIndexed { idx, label ->
                val tvItem = TextView(context).apply {
                    text = label
                    setTextColor(Color.WHITE)
                    textSize = 14.5f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((12 * d).toInt(), (13 * d).toInt(), (12 * d).toInt(), (13 * d).toInt())
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
                        setBackgroundColor(Color.parseColor("#14FFFFFF"))
                    }
                    listLayout.addView(line)
                }
            }

            scroll.addView(listLayout)
            rootCard.addView(scroll)
            dialog.show()
        }
    }

    private lateinit var masterFrame: FrameLayout
    private lateinit var rootVideoCol: LinearLayout
    private lateinit var pullRefreshCard: CardView

    // Top Header: "Hybrid Player" on Left + Cleaner Bucket Icon with Red Dot & "4.43GB" on Right
    private lateinit var topHeaderRow: LinearLayout
    private lateinit var tvCleanerSizeBadge: TextView

    // History Section: "History" Header + Trash Icon + Horizontal Recent Videos Carousel
    private lateinit var historySectionContainer: LinearLayout
    private lateinit var rvHistoryHorizontal: RecyclerView

    // Sub-Tabs Row: Video | Folder | Playlist + Right Action Icons
    private lateinit var tabVideoCol: LinearLayout
    private lateinit var tvTabVideo: TextView
    private lateinit var indTabVideo: View

    private lateinit var tabFolderCol: LinearLayout
    private lateinit var tvTabFolder: TextView
    private lateinit var indTabFolder: View

    private lateinit var tabPlaylistCol: LinearLayout
    private lateinit var tvTabPlaylist: TextView
    private lateinit var indTabPlaylist: View

    private lateinit var btnRightPrimaryIcon: ImageView
    private lateinit var btnRightSecondaryIcon: ImageView

    // Month Header ("Oct")
    private lateinit var tvMonthSectionHeader: TextView

    // Multi-Select Top Header & Bottom Action Dock
    private lateinit var multiSelectTopBar: LinearLayout
    private lateinit var tvMultiSelectCount: TextView
    private lateinit var btnSelectAllToggle: TextView
    private lateinit var btnCancelMultiSelect: TextView
    private lateinit var multiSelectBottomDock: LinearLayout

    // Folder Back Bar
    private lateinit var folderBackHeader: LinearLayout
    private lateinit var tvFolderTitle: TextView

    private lateinit var rvVideos: RecyclerView
    private lateinit var tvEmptyVideos: TextView

    // Bottom Background Audio Bar
    private lateinit var bgAudioDockCard: CardView
    private lateinit var tvBgDockTitle: TextView
    private lateinit var tvBgDockTime: TextView
    private lateinit var sbBgDockSeek: SeekBar
    private lateinit var btnBgDockPrev: ImageView
    private lateinit var btnBgDockPlayPause: ImageView
    private lateinit var btnBgDockNext: ImageView
    private lateinit var btnBgDockConvertMp3: TextView
    private lateinit var btnBgDockClose: TextView

    private val allVideosList = mutableListOf<VideoModel>()
    private val displayedVideosList = mutableListOf<VideoModel>()
    private val historyVideosList = mutableListOf<VideoModel>()
    private lateinit var videoAdapter: VideoAdapter

    // 0 = Video, 1 = Folder, 2 = Playlist
    private var activeTab = 0
    private var selectedFolderPath: String? = null
    private var isViewingFavoritePlaylist = false

    // Sort Field: 0 = Date, 1 = Name, 2 = Size, 3 = Length
    private var sortField = 0
    private var sortAscending = false
    private var isGridView = false
    private var showFolderThumbStrip = true
    private var isReceiverRegistered = false

    private val bgSyncReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!isAdded) return
            when (intent?.action) {
                BackgroundAudioService.ACTION_NOTIF_PLAY_PAUSE -> {
                    bgBarPlaying = !bgBarPlaying
                    refreshBgDockUi()
                }
                BackgroundAudioService.ACTION_NOTIF_NEXT,
                BackgroundAudioService.ACTION_NOTIF_PREV -> {
                    refreshBgDockUi()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val d = ctx.resources.displayMetrics.density

        val prefs = ctx.getSharedPreferences("HYBRID_VIDEO_UI_PREFS", Context.MODE_PRIVATE)
        sortField = prefs.getInt("playit_sort_field", 0)
        sortAscending = prefs.getBoolean("playit_sort_asc", false)
        isGridView = prefs.getBoolean("video_is_grid", false)

        masterFrame = FrameLayout(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#0B0D0E"))
        }

        rootVideoCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 1. Pull-to-Refresh Circular Spinner
        pullRefreshCard = CardView(ctx).apply {
            radius = 20f * d
            cardElevation = 8f * d
            setCardBackgroundColor(Color.parseColor("#182026"))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                (40 * d).toInt(),
                (40 * d).toInt()
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = (6 * d).toInt()
                bottomMargin = (4 * d).toInt()
            }
        }
        val spinner = ProgressBar(ctx, null, android.R.attr.progressBarStyleSmall).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#2ECC71"))
            layoutParams = FrameLayout.LayoutParams(
                (22 * d).toInt(),
                (22 * d).toInt(),
                Gravity.CENTER
            )
        }
        pullRefreshCard.addView(spinner)
        rootVideoCol.addView(pullRefreshCard)

        // 2. Multi-Selection Top Header Bar
        buildMultiSelectTopHeaderBar(ctx, d)
        rootVideoCol.addView(multiSelectTopBar)

        // 3. Dedicated Top Header Bar: "Hybrid Player" on Left + Cleaner Bucket (Red Dot + 4.43GB) on Right
        // (Search & Download buttons omitted from top-right as requested)
        topHeaderRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * d).toInt(), (10 * d).toInt(), (16 * d).toInt(), (6 * d).toInt())
        }

        val tvBrandTitle = TextView(ctx).apply {
            text = "Hybrid Player"
            setTextColor(Color.WHITE)
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val cleanerBtnCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((8 * d).toInt(), (2 * d).toInt(), (4 * d).toInt(), (2 * d).toInt())
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                HybridCleanerHubHelper.openFullCleanJunkScreen(
                    context = ctx,
                    allVideosProvider = { allVideosList }
                ) {
                    updateCleanerBadgeText(ctx)
                    triggerBackgroundVideoScan(showToastOnComplete = false)
                }
            }
        }

        val imgCleanerBucket = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((28 * d).toInt(), (26 * d).toInt())
            setImageDrawable(HybridVideoSectionHelper.PlayItHeaderVectorDrawable("CLEANER_BUCKET_RED_DOT", d))
        }

        tvCleanerSizeBadge = TextView(ctx).apply {
            text = "4.43GB"
            setTextColor(Color.parseColor("#B0B8C0"))
            textSize = 10f
            gravity = Gravity.CENTER
        }

        cleanerBtnCol.addView(imgCleanerBucket)
        cleanerBtnCol.addView(tvCleanerSizeBadge)

        topHeaderRow.addView(tvBrandTitle)
        topHeaderRow.addView(cleanerBtnCol)
        rootVideoCol.addView(topHeaderRow)

        // 4. Horizontal "History" Section (Matches Screenshots 1000069507_2 - 1000069511_2)
        buildHistoryHorizontalSection(ctx, d)
        rootVideoCol.addView(historySectionContainer)

        // 5. Sub-Tabs Bar: Video | Folder | Playlist + Sort & View Mode Icons
        buildSubTabsRow(ctx, d)

        // 6. Month Section Label ("Oct")
        tvMonthSectionHeader = TextView(ctx).apply {
            text = "Oct"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((14 * d).toInt(), (6 * d).toInt(), (14 * d).toInt(), (4 * d).toInt())
        }
        rootVideoCol.addView(tvMonthSectionHeader)

        // 7. Folder / Playlist Back Header
        folderBackHeader = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding((14 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
            setBackgroundColor(Color.parseColor("#14191F"))
        }

        val btnBackFolder = TextView(ctx).apply {
            text = "<-  Back"
            setTextColor(Color.parseColor("#2ECC71"))
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((4 * d).toInt(), (4 * d).toInt(), (14 * d).toInt(), (4 * d).toInt())
            setOnClickListener {
                selectedFolderPath = null
                isViewingFavoritePlaylist = false
                refreshVideoDisplay()
            }
        }

        tvFolderTitle = TextView(ctx).apply {
            text = ""
            setTextColor(Color.WHITE)
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        folderBackHeader.addView(btnBackFolder)
        folderBackHeader.addView(tvFolderTitle)
        rootVideoCol.addView(folderBackHeader)

        // 8. Main Video / Folder / Playlist RecyclerView
        rvVideos = RecyclerView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setPadding(0, (2 * d).toInt(), 0, (8 * d).toInt())
            clipToPadding = false
            setHasFixedSize(true)
            setItemViewCacheSize(32)
            itemAnimator = null
        }

        videoAdapter = VideoAdapter(ctx, displayedVideosList) { isSelecting, selectedCount ->
            updateMultiSelectBarUi(isSelecting, selectedCount)
        }
        videoAdapter.setGridMode(isGridView)
        applyRecyclerViewLayoutManager(ctx)
        rvVideos.adapter = videoAdapter
        attachPullToRefresh(ctx)
        rootVideoCol.addView(rvVideos)

        tvEmptyVideos = TextView(ctx).apply {
            text = "No videos found"
            setTextColor(Color.parseColor("#8E959E"))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        rootVideoCol.addView(tvEmptyVideos)

        // 9. Multi-Select Bottom Action Dock
        buildMultiSelectBottomActionDock(ctx, d)
        rootVideoCol.addView(multiSelectBottomDock)

        // 10. Background Audio Dock Bar
        buildUpgradedBackgroundAudioBar(ctx, d)
        rootVideoCol.addView(bgAudioDockCard)

        masterFrame.addView(rootVideoCol)
        updateCleanerBadgeText(ctx)
        updateSubTabVisuals(d)
        registerBgReceiver(ctx)

        val instantCache = DeepMediaScanner.getCachedVideosInstant(ctx)
        if (instantCache.isNotEmpty()) {
            allVideosList.clear()
            allVideosList.addAll(instantCache)
            refreshVideoDisplay()
        }
        triggerBackgroundVideoScan(showToastOnComplete = false)

        return masterFrame
    }

    private fun buildHistoryHorizontalSection(ctx: Context, d: Float) {
        historySectionContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (6 * d).toInt() }
        }

        val headerRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * d).toInt(), (4 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
        }

        val tvHistoryLabel = TextView(ctx).apply {
            text = "History"
            setTextColor(Color.WHITE)
            textSize = 16.5f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnClearHistoryTrash = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((28 * d).toInt(), (28 * d).toInt())
            setPadding((4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt())
            setImageDrawable(HybridVideoSectionHelper.PlayItHeaderVectorDrawable("HISTORY_TRASH", d))
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                val prefs = ctx.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
                val editor = prefs.edit()
                allVideosList.forEach { v ->
                    editor.remove("history_time_${v.path}")
                    editor.remove("watched_pos_${v.path}")
                }
                editor.apply()
                refreshHistoryCarousel(ctx)
                videoAdapter.notifyDataSetChanged()
                Toast.makeText(ctx, "Watch History Cleared", Toast.LENGTH_SHORT).show()
            }
        }

        headerRow.addView(tvHistoryLabel)
        headerRow.addView(btnClearHistoryTrash)
        historySectionContainer.addView(headerRow)

        rvHistoryHorizontal = RecyclerView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (88 * d).toInt()
            )
            setPadding((10 * d).toInt(), 0, (10 * d).toInt(), 0)
            clipToPadding = false
            layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
            setHasFixedSize(true)
        }

        rvHistoryHorizontal.adapter = object : RecyclerView.Adapter<HistoryItemVH>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryItemVH {
                val card = CardView(ctx).apply {
                    radius = 6f * d
                    cardElevation = 0f
                    setCardBackgroundColor(Color.parseColor("#181D24"))
                    layoutParams = RecyclerView.LayoutParams((152 * d).toInt(), (86 * d).toInt()).apply {
                        marginStart = (4 * d).toInt()
                        marginEnd = (4 * d).toInt()
                    }
                }

                val frame = FrameLayout(ctx)
                val img = ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                val tv8kBadge = TextView(ctx).apply {
                    text = "8k"
                    setTextColor(Color.parseColor("#FFD54F"))
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    visibility = View.GONE
                    setPadding((5 * d).toInt(), (1 * d).toInt(), (5 * d).toInt(), (1 * d).toInt())
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM or Gravity.START
                    ).apply {
                        marginStart = (5 * d).toInt()
                        bottomMargin = (6 * d).toInt()
                    }
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#BB1A160B"))
                        cornerRadius = 3f * d
                        setStroke((1 * d).toInt(), Color.parseColor("#FFD54F"))
                    }
                }

                val tvDur = TextView(ctx).apply {
                    text = "▶ 00:00"
                    setTextColor(Color.WHITE)
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding((6 * d).toInt(), (2 * d).toInt(), (6 * d).toInt(), (2 * d).toInt())
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM or Gravity.END
                    ).apply {
                        marginEnd = (5 * d).toInt()
                        bottomMargin = (6 * d).toInt()
                    }
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#B3000000"))
                        cornerRadius = 4f * d
                    }
                }

                val greenProgress = View(ctx).apply {
                    setBackgroundColor(Color.parseColor("#2ECC71"))
                    layoutParams = FrameLayout.LayoutParams(
                        (90 * d).toInt(),
                        (2.5f * d).toInt(),
                        Gravity.BOTTOM or Gravity.START
                    )
                }

                frame.addView(img)
                frame.addView(tv8kBadge)
                frame.addView(tvDur)
                frame.addView(greenProgress)
                card.addView(frame)
                return HistoryItemVH(card, img, tv8kBadge, tvDur, greenProgress)
            }

            override fun onBindViewHolder(holder: HistoryItemVH, position: Int) {
                val vid = historyVideosList[position]
                VideoAdapter.loadGuaranteedVideoThumbnail(ctx, vid.path, holder.img)
                holder.tvDur.text = "▶ ${VideoAdapter.formatVideoDurationAny(vid.duration)}"
                holder.tv8kBadge.visibility =
                    if (vid.title.contains("8k", ignoreCase = true)) View.VISIBLE else View.GONE

                val prefs = ctx.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
                val watchedMs = prefs.getLong("watched_pos_${vid.path}", 0L)
                val durMs = if (vid.duration in 1L..3600L) vid.duration * 1000L else vid.duration.coerceAtLeast(1000L)
                val frac = if (watchedMs > 0L) (watchedMs.toFloat() / durMs.toFloat()).coerceIn(0.25f, 1.0f) else 0.55f
                holder.greenProgress.layoutParams = holder.greenProgress.layoutParams.apply {
                    width = ((152 * d) * frac).toInt()
                }

                holder.itemView.setOnClickListener {
                    val allPaths = ArrayList(allVideosList.map { it.path })
                    val intent = Intent(ctx, PlayerActivity::class.java).apply {
                        putExtra("VIDEO_PATH", vid.path)
                        putExtra("VIDEO_TITLE", vid.title)
                        putStringArrayListExtra("VIDEO_LIST", allPaths)
                        addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    }
                    startActivity(intent)
                }
            }

            override fun getItemCount(): Int = historyVideosList.size
        }

        historySectionContainer.addView(rvHistoryHorizontal)
    }

    private class HistoryItemVH(
        view: View,
        val img: ImageView,
        val tv8kBadge: TextView,
        val tvDur: TextView,
        val greenProgress: View
    ) : RecyclerView.ViewHolder(view)

    private fun refreshHistoryCarousel(ctx: Context) {
        val prefs = ctx.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        val watchedHistory = allVideosList
            .filter {
                prefs.getLong("history_time_${it.path}", 0L) > 0L ||
                    prefs.getLong("watched_pos_${it.path}", 0L) > 0L
            }
            .sortedByDescending { prefs.getLong("history_time_${it.path}", 0L) }

        val source = if (watchedHistory.isNotEmpty()) watchedHistory else allVideosList.take(8)
        historyVideosList.clear()
        historyVideosList.addAll(source.take(15))

        if (historyVideosList.isEmpty()) {
            historySectionContainer.visibility = View.GONE
        } else {
            historySectionContainer.visibility = View.VISIBLE
            rvHistoryHorizontal.adapter?.notifyDataSetChanged()
        }
    }

    private fun buildSubTabsRow(ctx: Context, d: Float) {
        val subTabsBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (6 * d).toInt())
        }

        fun makeTabColumn(label: String, tabIdx: Int): Triple<LinearLayout, TextView, View> {
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = (24 * d).toInt() }
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    switchVideoTab(tabIdx)
                }
            }
            val tv = TextView(ctx).apply {
                text = label
                textSize = 16.5f
                typeface = Typeface.DEFAULT_BOLD
            }
            val indicator = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams((22 * d).toInt(), (3 * d).toInt()).apply {
                    topMargin = (4 * d).toInt()
                }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#2ECC71"))
                    cornerRadius = 2f * d
                }
            }
            col.addView(tv)
            col.addView(indicator)
            return Triple(col, tv, indicator)
        }

        val t0 = makeTabColumn("Video", 0)
        tabVideoCol = t0.first
        tvTabVideo = t0.second
        indTabVideo = t0.third

        val t1 = makeTabColumn("Folder", 1)
        tabFolderCol = t1.first
        tvTabFolder = t1.second
        indTabFolder = t1.third

        val t2 = makeTabColumn("Playlist", 2)
        tabPlaylistCol = t2.first
        tvTabPlaylist = t2.second
        indTabPlaylist = t2.third

        val spacer = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }

        btnRightPrimaryIcon = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((34 * d).toInt(), (34 * d).toInt()).apply {
                marginEnd = (8 * d).toInt()
            }
            setPadding((6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt())
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                if (activeTab == 0 || selectedFolderPath != null) {
                    HybridVideoSectionHelper.showExactPlayItSortDialog(
                        context = ctx,
                        currentSortField = sortField,
                        currentAscending = sortAscending
                    ) { field, asc ->
                        sortField = field
                        sortAscending = asc
                        ctx.getSharedPreferences("HYBRID_VIDEO_UI_PREFS", Context.MODE_PRIVATE)
                            .edit()
                            .putInt("playit_sort_field", sortField)
                            .putBoolean("playit_sort_asc", sortAscending)
                            .apply()
                        refreshVideoDisplay()
                    }
                } else if (activeTab == 1) {
                    showFolderThumbStrip = !showFolderThumbStrip
                    refreshVideoDisplay()
                }
            }
        }

        btnRightSecondaryIcon = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((34 * d).toInt(), (34 * d).toInt())
            setPadding((6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt())
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                if (activeTab == 2 && !isViewingFavoritePlaylist) {
                    HybridVideoSectionHelper.showCreatePlaylistDialog(ctx) {
                        refreshVideoDisplay()
                    }
                } else {
                    isGridView = !isGridView
                    ctx.getSharedPreferences("HYBRID_VIDEO_UI_PREFS", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("video_is_grid", isGridView)
                        .apply()
                    videoAdapter.setGridMode(isGridView)
                    updateSubTabVisuals(d)
                    refreshVideoDisplay()
                }
            }
        }

        subTabsBar.addView(tabVideoCol)
        subTabsBar.addView(tabFolderCol)
        subTabsBar.addView(tabPlaylistCol)
        subTabsBar.addView(spacer)
        subTabsBar.addView(btnRightPrimaryIcon)
        subTabsBar.addView(btnRightSecondaryIcon)
        rootVideoCol.addView(subTabsBar)
    }

    private fun switchVideoTab(targetTab: Int) {
        if (!isAdded) return
        val d = resources.displayMetrics.density
        activeTab = targetTab.coerceIn(0, 2)
        selectedFolderPath = null
        isViewingFavoritePlaylist = false
        videoAdapter.exitMultiSelectMode()
        updateSubTabVisuals(d)
        refreshVideoDisplay()
    }

    private fun updateSubTabVisuals(d: Float) {
        val tabs = listOf(
            Pair(tvTabVideo, indTabVideo),
            Pair(tvTabFolder, indTabFolder),
            Pair(tvTabPlaylist, indTabPlaylist)
        )
        tabs.forEachIndexed { idx, (tv, ind) ->
            val isSel = idx == activeTab
            tv.setTextColor(if (isSel) Color.parseColor("#2ECC71") else Color.WHITE)
            ind.visibility = if (isSel) View.VISIBLE else View.INVISIBLE
        }

        when (activeTab) {
            0 -> {
                btnRightPrimaryIcon.visibility = View.VISIBLE
                btnRightPrimaryIcon.setImageDrawable(
                    HybridVideoSectionHelper.PlayItHeaderVectorDrawable("SORT_ARROWS", d)
                )
                btnRightSecondaryIcon.visibility = View.VISIBLE
                btnRightSecondaryIcon.setImageDrawable(
                    HybridVideoSectionHelper.PlayItHeaderVectorDrawable(
                        if (isGridView) "VIEW_GRID_ICON" else "VIEW_LIST_ICON",
                        d
                    )
                )
            }
            1 -> {
                btnRightPrimaryIcon.visibility = View.VISIBLE
                btnRightPrimaryIcon.setImageDrawable(
                    HybridVideoSectionHelper.PlayItHeaderVectorDrawable("SORT_ARROWS", d)
                )
                btnRightSecondaryIcon.visibility = View.VISIBLE
                btnRightSecondaryIcon.setImageDrawable(
                    HybridVideoSectionHelper.PlayItHeaderVectorDrawable("VIEW_LIST_ICON", d)
                )
            }
            2 -> {
                btnRightPrimaryIcon.visibility = View.GONE
                btnRightSecondaryIcon.visibility = View.VISIBLE
                btnRightSecondaryIcon.setImageDrawable(
                    HybridVideoSectionHelper.PlayItHeaderVectorDrawable("PLUS_ADD", d)
                )
            }
        }
    }

    private fun updateCleanerBadgeText(ctx: Context) {
        val metrics = HybridCleanerHubHelper.readDeviceStorageMetrics(ctx)
        tvCleanerSizeBadge.text = metrics.badgeShortText
    }

    override fun onResume() {
        super.onResume()
        val ctx = context ?: return
        // Hide MainActivity's legacy top bar on the Video tab so only the Hybrid Player PlayIt header shows
        activity?.findViewById<View>(R.id.topBrandBar)?.visibility = View.GONE
        updateCleanerBadgeText(ctx)
        refreshBgDockUi()
        triggerBackgroundVideoScan(showToastOnComplete = false)
        attachMascotToVideoScreen()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isReceiverRegistered) {
            try {
                requireContext().unregisterReceiver(bgSyncReceiver)
            } catch (_: Exception) {
            }
            isReceiverRegistered = false
        }
    }

    private fun attachMascotToVideoScreen() {
        val ctx = context ?: return
        MascotCompanionHelper.attachInteractiveMascot(
            context = ctx,
            hostContainer = masterFrame,
            recyclerView = rvVideos,
            isVideoScreen = true,
            isDancingProvider = { (bgBarVisible && bgBarPlaying) || AudioFragment.isSharedPlayerPlaying() },
            onHoldItemTriggered = { heldPosition ->
                if (heldPosition in displayedVideosList.indices) {
                    val chosen = displayedVideosList[heldPosition]
                    val allPaths = ArrayList(displayedVideosList.map { it.path })
                    val intent = Intent(ctx, PlayerActivity::class.java).apply {
                        putExtra("VIDEO_PATH", chosen.path)
                        putExtra("VIDEO_TITLE", chosen.title)
                        putStringArrayListExtra("VIDEO_LIST", allPaths)
                        addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    }
                    startActivity(intent)
                }
            }
        )
    }

    private fun buildMultiSelectTopHeaderBar(ctx: Context, d: Float) {
        multiSelectTopBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#162438"))
                setStroke((1 * d).toInt(), Color.parseColor("#33FFFFFF"))
            }
        }

        tvMultiSelectCount = TextView(ctx).apply {
            text = "1 Selected"
            setTextColor(Color.WHITE)
            textSize = 15.5f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        btnSelectAllToggle = TextView(ctx).apply {
            text = "Select All"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((14 * d).toInt(), (7 * d).toInt(), (14 * d).toInt(), (7 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (8 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#28FFFFFF"))
                cornerRadius = 14f * d
            }
            setOnClickListener {
                videoAdapter.selectAllVideos()
            }
        }

        btnCancelMultiSelect = TextView(ctx).apply {
            text = "Done"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((14 * d).toInt(), (7 * d).toInt(), (14 * d).toInt(), (7 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2ECC71"))
                cornerRadius = 14f * d
            }
            setOnClickListener {
                videoAdapter.exitMultiSelectMode()
            }
        }

        multiSelectTopBar.addView(tvMultiSelectCount)
        multiSelectTopBar.addView(btnSelectAllToggle)
        multiSelectTopBar.addView(btnCancelMultiSelect)
    }

    private fun buildMultiSelectBottomActionDock(ctx: Context, d: Float) {
        multiSelectBottomDock = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding((10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins((8 * d).toInt(), (4 * d).toInt(), (8 * d).toInt(), (6 * d).toInt())
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#14191F"))
                cornerRadius = 16f * d
                setStroke((1 * d).toInt(), Color.parseColor("#33FFFFFF"))
            }
        }

        val btnHybridShare = TextView(ctx).apply {
            text = "Turbo Share"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (6 * d).toInt()
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2ECC71"))
                cornerRadius = 12f * d
            }
            setOnClickListener {
                val selected = videoAdapter.getSelectedVideos()
                if (selected.isEmpty()) return@setOnClickListener
                val paths = ArrayList(selected.map { it.path })
                videoAdapter.exitMultiSelectMode()
                try {
                    val intent = Intent(ctx, FileTransferActivity::class.java).apply {
                        putStringArrayListExtra("PRESELECTED_PATHS", paths)
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                }
            }
        }

        val btnOtherAppsShare = TextView(ctx).apply {
            text = "Share Apps"
            setTextColor(Color.WHITE)
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (6 * d).toInt()
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#26FFFFFF"))
                cornerRadius = 12f * d
            }
            setOnClickListener {
                val selected = videoAdapter.getSelectedVideos()
                if (selected.isEmpty()) return@setOnClickListener
                val uris = ArrayList<Uri>()
                selected.forEach { uris.add(Uri.parse(it.path)) }
                videoAdapter.exitMultiSelectMode()
                try {
                    val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "video/*"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share ${uris.size} Videos"))
                } catch (_: Exception) {
                }
            }
        }

        val btnBatchDelete = TextView(ctx).apply {
            text = "Delete"
            setTextColor(Color.WHITE)
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FF5252"))
                cornerRadius = 12f * d
            }
            setOnClickListener {
                val selected = videoAdapter.getSelectedVideos()
                if (selected.isEmpty()) return@setOnClickListener
                PermanentDeleteHelper.confirmAndDeleteMultipleVideosPermanently(ctx, selected) {
                    val delSet = selected.map { it.path }.toHashSet()
                    allVideosList.removeAll { delSet.contains(it.path) }
                    displayedVideosList.removeAll { delSet.contains(it.path) }
                    videoAdapter.exitMultiSelectMode()
                    refreshVideoDisplay()
                }
            }
        }

        multiSelectBottomDock.addView(btnHybridShare)
        multiSelectBottomDock.addView(btnOtherAppsShare)
        multiSelectBottomDock.addView(btnBatchDelete)
    }

    private fun updateMultiSelectBarUi(isSelecting: Boolean, count: Int) {
        if (!isSelecting) {
            multiSelectTopBar.visibility = View.GONE
            multiSelectBottomDock.visibility = View.GONE
        } else {
            multiSelectTopBar.visibility = View.VISIBLE
            multiSelectBottomDock.visibility = View.VISIBLE
            tvMultiSelectCount.text = "$count Selected"
            btnSelectAllToggle.text =
                if (count == displayedVideosList.size && count > 0) "Unselect All" else "Select All"
        }
    }

    private fun buildUpgradedBackgroundAudioBar(ctx: Context, d: Float) {
        bgAudioDockCard = CardView(ctx).apply {
            radius = 16f * d
            cardElevation = 12f * d
            setCardBackgroundColor(Color.parseColor("#141C24"))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins((10 * d).toInt(), (4 * d).toInt(), (10 * d).toInt(), (8 * d).toInt())
            }
        }

        val dockCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141C24"))
                cornerRadius = 16f * d
                setStroke((1 * d).toInt(), Color.parseColor("#33FFFFFF"))
            }
        }

        val topInfoRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val textCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (8 * d).toInt()
            }
            setOnClickListener {
                if (bgBarPath.isNotEmpty()) {
                    val intent = Intent(ctx, PlayerActivity::class.java).apply {
                        putExtra("VIDEO_PATH", bgBarPath)
                        putExtra("VIDEO_TITLE", bgBarTitle)
                        addFlags(
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                Intent.FLAG_ACTIVITY_NO_ANIMATION
                        )
                    }
                    startActivity(intent)
                    (ctx as? Activity)?.overridePendingTransition(0, 0)
                }
            }
        }

        tvBgDockTitle = TextView(ctx).apply {
            text = "Background Play"
            setTextColor(Color.WHITE)
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        tvBgDockTime = TextView(ctx).apply {
            text = "00:00 / 00:00  •  Tap to Resume Video"
            setTextColor(Color.parseColor("#9FB4CE"))
            textSize = 11.5f
        }

        textCol.addView(tvBgDockTitle)
        textCol.addView(tvBgDockTime)

        btnBgDockConvertMp3 = TextView(ctx).apply {
            text = "Fast MP3"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((12 * d).toInt(), (6 * d).toInt(), (12 * d).toInt(), (6 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (8 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2ECC71"))
                cornerRadius = 14f * d
            }
            setOnClickListener {
                if (bgBarPath.isNotEmpty()) {
                    try {
                        val intent = Intent(ctx, Mp3ConverterActivity::class.java).apply {
                            putExtra("VIDEO_PATH", bgBarPath)
                            putExtra("VIDEO_TITLE", bgBarTitle)
                        }
                        startActivity(intent)
                    } catch (_: Exception) {
                    }
                }
            }
        }

        btnBgDockClose = TextView(ctx).apply {
            text = "X"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((28 * d).toInt(), (28 * d).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#24FFFFFF"))
            }
            setOnClickListener {
                bgBarVisible = false
                bgBarPlaying = false
                bgAudioDockCard.visibility = View.GONE
                ctx.startService(
                    Intent(ctx, BackgroundAudioService::class.java)
                        .setAction(BackgroundAudioService.ACTION_STOP_SERVICE)
                )
            }
        }

        topInfoRow.addView(textCol)
        topInfoRow.addView(btnBgDockConvertMp3)
        topInfoRow.addView(btnBgDockClose)
        dockCol.addView(topInfoRow)

        sbBgDockSeek = SeekBar(ctx).apply {
            max = 1000
            progressDrawable.setColorFilter(Color.parseColor("#2ECC71"), PorterDuff.Mode.SRC_IN)
            thumb.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (4 * d).toInt()
                bottomMargin = (4 * d).toInt()
            }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val targetMs = ((p / 1000f) * bgBarDurationMs).toLong()
                        tvBgDockTime.text = "${formatShortTime(targetMs)} / ${formatShortTime(bgBarDurationMs)}  •  Tap to Resume Video"
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {
                    val p = s?.progress ?: 0
                    val targetMs = ((p / 1000f) * bgBarDurationMs).toLong()
                    bgBarPositionMs = targetMs
                    ctx.sendBroadcast(
                        Intent(BackgroundAudioService.ACTION_NOTIF_SEEK)
                            .setPackage(ctx.packageName)
                            .putExtra("SEEK_POS_MS", targetMs)
                    )
                }
            })
        }
        dockCol.addView(sbBgDockSeek)

        val transportRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        btnBgDockPrev = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((36 * d).toInt(), (36 * d).toInt()).apply {
                marginEnd = (16 * d).toInt()
            }
            setPadding((7 * d).toInt(), (7 * d).toInt(), (7 * d).toInt(), (7 * d).toInt())
            setImageResource(android.R.drawable.ic_media_previous)
            setColorFilter(Color.WHITE)
            setOnClickListener {
                ctx.sendBroadcast(Intent(BackgroundAudioService.ACTION_NOTIF_PREV).setPackage(ctx.packageName))
            }
        }

        btnBgDockPlayPause = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((42 * d).toInt(), (42 * d).toInt()).apply {
                marginEnd = (16 * d).toInt()
            }
            setPadding((8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
            setImageResource(android.R.drawable.ic_media_pause)
            setColorFilter(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#24FFFFFF"))
            }
            setOnClickListener {
                bgBarPlaying = !bgBarPlaying
                refreshBgDockUi()
                ctx.sendBroadcast(Intent(BackgroundAudioService.ACTION_NOTIF_PLAY_PAUSE).setPackage(ctx.packageName))
            }
        }

        btnBgDockNext = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((36 * d).toInt(), (36 * d).toInt()).apply {
                marginEnd = (16 * d).toInt()
            }
            setPadding((7 * d).toInt(), (7 * d).toInt(), (7 * d).toInt(), (7 * d).toInt())
            setImageResource(android.R.drawable.ic_media_next)
            setColorFilter(Color.WHITE)
            setOnClickListener {
                ctx.sendBroadcast(Intent(BackgroundAudioService.ACTION_NOTIF_NEXT).setPackage(ctx.packageName))
            }
        }

        val btnBackToVideoPill = TextView(ctx).apply {
            text = "Instant Video"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((14 * d).toInt(), (7 * d).toInt(), (14 * d).toInt(), (7 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E2E46"))
                cornerRadius = 14f * d
            }
            setOnClickListener {
                if (bgBarPath.isNotEmpty()) {
                    val intent = Intent(ctx, PlayerActivity::class.java).apply {
                        putExtra("VIDEO_PATH", bgBarPath)
                        putExtra("VIDEO_TITLE", bgBarTitle)
                        addFlags(
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                Intent.FLAG_ACTIVITY_NO_ANIMATION
                        )
                    }
                    startActivity(intent)
                    (ctx as? Activity)?.overridePendingTransition(0, 0)
                }
            }
        }

        transportRow.addView(btnBgDockPrev)
        transportRow.addView(btnBgDockPlayPause)
        transportRow.addView(btnBgDockNext)
        transportRow.addView(btnBackToVideoPill)
        dockCol.addView(transportRow)

        bgAudioDockCard.addView(dockCol)
    }

    private fun refreshBgDockUi() {
        if (!isAdded) return
        if (!bgBarVisible || bgBarTitle.isEmpty()) {
            bgAudioDockCard.visibility = View.GONE
            return
        }
        bgAudioDockCard.visibility = View.VISIBLE
        tvBgDockTitle.text = bgBarTitle
        tvBgDockTime.text = "${formatShortTime(bgBarPositionMs)} / ${formatShortTime(bgBarDurationMs)}  •  Tap to Resume Video"
        val progress = ((bgBarPositionMs.toFloat() / bgBarDurationMs.toFloat()) * 1000f).roundToInt().coerceIn(0, 1000)
        sbBgDockSeek.progress = progress
        btnBgDockPlayPause.setImageResource(
            if (bgBarPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
    }

    private fun registerBgReceiver(ctx: Context) {
        if (isReceiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(BackgroundAudioService.ACTION_NOTIF_PLAY_PAUSE)
                addAction(BackgroundAudioService.ACTION_NOTIF_NEXT)
                addAction(BackgroundAudioService.ACTION_NOTIF_PREV)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.registerReceiver(bgSyncReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                ctx.registerReceiver(bgSyncReceiver, filter)
            }
            isReceiverRegistered = true
        } catch (_: Exception) {
        }
    }

    private fun applyRecyclerViewLayoutManager(ctx: Context) {
        val showingVideoItems = (activeTab == 0) ||
            (activeTab == 1 && selectedFolderPath != null) ||
            (activeTab == 2 && isViewingFavoritePlaylist)
        rvVideos.layoutManager = if (isGridView && showingVideoItems) {
            GridLayoutManager(ctx, 2)
        } else {
            LinearLayoutManager(ctx)
        }
    }

    private fun attachPullToRefresh(context: Context) {
        var touchDownY = 0f
        var canPullRefresh = false

        rvVideos.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        touchDownY = e.y
                        canPullRefresh = !rv.canScrollVertically(-1)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (canPullRefresh && (e.y - touchDownY) > 95f) {
                            pullRefreshCard.visibility = View.VISIBLE
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (pullRefreshCard.visibility == View.VISIBLE) {
                            triggerBackgroundVideoScan(showToastOnComplete = true)
                        }
                        canPullRefresh = false
                    }
                }
                return false
            }
        })
    }

    private fun triggerBackgroundVideoScan(showToastOnComplete: Boolean) {
        val ctx = context ?: return
        thread {
            val scanned = DeepMediaScanner.scanAllVideosEverywhere(ctx, forceRefresh = showToastOnComplete)
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                pullRefreshCard.visibility = View.GONE
                val changed = scanned.size != allVideosList.size || showToastOnComplete || allVideosList.isEmpty()
                allVideosList.clear()
                allVideosList.addAll(scanned)
                if (changed) {
                    refreshVideoDisplay()
                } else {
                    refreshHistoryCarousel(ctx)
                }
                if (showToastOnComplete) {
                    Toast.makeText(ctx, "Refreshed! ${scanned.size} Videos Loaded.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun refreshVideoDisplay() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)

        refreshHistoryCarousel(ctx)
        tvMonthSectionHeader.text = HybridVideoSectionHelper.getMonthHeaderLabel(allVideosList)

        // 1. Folder Tab (Root Folder List with 4-Thumbnail Preview Strip)
        if (activeTab == 1 && selectedFolderPath == null) {
            tvMonthSectionHeader.visibility = View.GONE
            folderBackHeader.visibility = View.GONE
            rvVideos.layoutManager = LinearLayoutManager(ctx)
            if (allVideosList.isEmpty()) {
                rvVideos.visibility = View.GONE
                tvEmptyVideos.text = "No video folders found"
                tvEmptyVideos.visibility = View.VISIBLE
            } else {
                tvEmptyVideos.visibility = View.GONE
                rvVideos.visibility = View.VISIBLE
                rvVideos.adapter = HybridVideoSectionHelper.createPlayItFolderAdapter(
                    context = ctx,
                    allVideos = allVideosList,
                    showPreviewStrip = showFolderThumbStrip
                ) { clickedFolderPath ->
                    selectedFolderPath = clickedFolderPath
                    refreshVideoDisplay()
                }
            }
            return
        }

        // 2. Playlist Tab (Clapperboard "Favorite Videos" Card)
        if (activeTab == 2 && !isViewingFavoritePlaylist) {
            tvMonthSectionHeader.visibility = View.GONE
            folderBackHeader.visibility = View.GONE
            rvVideos.layoutManager = LinearLayoutManager(ctx)
            tvEmptyVideos.visibility = View.GONE
            rvVideos.visibility = View.VISIBLE
            rvVideos.adapter = HybridVideoSectionHelper.createPlayItPlaylistAdapter(
                context = ctx,
                allVideos = allVideosList
            ) {
                isViewingFavoritePlaylist = true
                refreshVideoDisplay()
            }
            return
        }

        // 3. Video Items View (All Videos, Inside a Folder, or Inside Favorite Videos Playlist)
        val baseList = when {
            activeTab == 1 && selectedFolderPath != null -> {
                tvMonthSectionHeader.visibility = View.GONE
                folderBackHeader.visibility = View.VISIBLE
                tvFolderTitle.text = File(selectedFolderPath!!).name.ifEmpty { "Folder Videos" }
                allVideosList.filter { (File(it.path).parent ?: "") == selectedFolderPath }
            }
            activeTab == 2 && isViewingFavoritePlaylist -> {
                tvMonthSectionHeader.visibility = View.GONE
                folderBackHeader.visibility = View.VISIBLE
                tvFolderTitle.text = "Favorite Videos"
                allVideosList.filter { prefs.getBoolean("fav_${it.path}", false) }
            }
            else -> {
                tvMonthSectionHeader.visibility = View.VISIBLE
                folderBackHeader.visibility = View.GONE
                allVideosList
            }
        }

        val sortedList = when (sortField) {
            0 -> if (sortAscending) baseList.reversed() else baseList
            1 -> if (sortAscending) {
                baseList.sortedBy { it.title.lowercase(Locale.US) }
            } else {
                baseList.sortedByDescending { it.title.lowercase(Locale.US) }
            }
            2 -> if (sortAscending) {
                baseList.sortedBy { it.size }
            } else {
                baseList.sortedByDescending { it.size }
            }
            3 -> if (sortAscending) {
                baseList.sortedBy { parseDurationToSeconds(it.duration) }
            } else {
                baseList.sortedByDescending { parseDurationToSeconds(it.duration) }
            }
            else -> baseList
        }

        videoAdapter.setGridMode(isGridView)
        applyRecyclerViewLayoutManager(ctx)
        if (rvVideos.adapter !== videoAdapter) {
            rvVideos.adapter = videoAdapter
        }

        displayedVideosList.clear()
        displayedVideosList.addAll(sortedList)
        videoAdapter.notifyDataSetChanged()

        if (displayedVideosList.isEmpty()) {
            rvVideos.visibility = View.GONE
            tvEmptyVideos.text = "0 Files"
            tvEmptyVideos.visibility = View.VISIBLE
        } else {
            tvEmptyVideos.visibility = View.GONE
            rvVideos.visibility = View.VISIBLE
        }
    }

    private fun parseDurationToSeconds(dur: Any?): Long {
        return when (dur) {
            is Long -> if (dur < 36000L) dur else dur / 1000L
            is String -> {
                val parts = dur.split(":").mapNotNull { it.toLongOrNull() }
                when (parts.size) {
                    3 -> parts[0] * 3600L + parts[1] * 60L + parts[2]
                    2 -> parts[0] * 60L + parts[1]
                    1 -> parts[0]
                    else -> 0L
                }
            }
            else -> 0L
        }
    }

    private fun formatShortTime(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val m = TimeUnit.MILLISECONDS.toMinutes(safe)
        val s = TimeUnit.MILLISECONDS.toSeconds(safe) % 60
        return String.format(Locale.US, "%02d:%02d", m, s)
    }
}
