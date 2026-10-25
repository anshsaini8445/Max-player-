package com.app.mediaplayer

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
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
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.ArrayList
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.abs
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
            val theme = ThemeStudioHelper.getCurrentTheme(context)
            val accentColor = Color.parseColor(theme.accentHex)

            val rootCard = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((18 * d).toInt(), (18 * d).toInt(), (18 * d).toInt(), (14 * d).toInt())
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#0A0A10"))
                    cornerRadius = 22f * d
                    setStroke((1.5f * d).toInt(), accentColor)
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
                setTextColor(accentColor)
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val btnClose = TextView(context).apply {
                text = "X"
                setTextColor(Color.WHITE)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams((30 * d).toInt(), (30 * d).toInt())
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

    private lateinit var masterFrame: FrameLayout
    private lateinit var rootVideoCol: LinearLayout
    private lateinit var pullRefreshCard: CardView
    private lateinit var topChipsScroll: HorizontalScrollView
    private lateinit var chipAllVideos: TextView
    private lateinit var chipShortsReels: TextView
    private lateinit var chipFolders: TextView
    private lateinit var chipFavorites: TextView
    private lateinit var chipHistory: TextView
    private lateinit var btnSortAndLayout: ImageView

    private lateinit var folderBackHeader: LinearLayout
    private lateinit var tvFolderTitle: TextView
    private lateinit var rvVideos: RecyclerView
    private lateinit var tvEmptyVideos: TextView

    // XPlayer-style Bottom Background Audio Controller Bar (Seekable + Prev/Play/Next/Close)
    private lateinit var bgAudioDockCard: CardView
    private lateinit var tvBgDockTitle: TextView
    private lateinit var tvBgDockTime: TextView
    private lateinit var sbBgDockSeek: SeekBar
    private lateinit var btnBgDockPrev: ImageView
    private lateinit var btnBgDockPlayPause: ImageView
    private lateinit var btnBgDockNext: ImageView
    private lateinit var btnBgDockClose: TextView

    private val allVideosList = mutableListOf<VideoModel>()
    private val displayedVideosList = mutableListOf<VideoModel>()
    private lateinit var videoAdapter: VideoAdapter

    private var activeTab = 0 // 0=All, 1=Shorts/Reels, 2=Folders, 3=Favorites, 4=History
    private var selectedFolderPath: String? = null
    private var sortOrder = 0 // 0=Newest First, 1=Oldest First (New Last), 2=Name A-Z, 3=Name Z-A, 4=Largest Size, 5=Longest Duration
    private var isGridView = false
    private var accentColor = Color.parseColor("#00FFFF")
    private var isReceiverRegistered = false
    private val uiHandler = Handler(Looper.getMainLooper())

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
        val theme = ThemeStudioHelper.getCurrentTheme(ctx)
        accentColor = Color.parseColor(theme.accentHex)

        val prefs = ctx.getSharedPreferences("HYBRID_VIDEO_UI_PREFS", Context.MODE_PRIVATE)
        sortOrder = prefs.getInt("video_sort_order", 0)
        isGridView = prefs.getBoolean("video_is_grid", false)

        masterFrame = FrameLayout(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        ThemeStudioHelper.applyThemeAndOneHandModeToRoot(ctx, masterFrame)

        rootVideoCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 1. Top Circular Pull-to-Refresh Spinner ("Gol-Gol Anda")
        pullRefreshCard = CardView(ctx).apply {
            radius = 22f * d
            cardElevation = 10f * d
            setCardBackgroundColor(Color.parseColor("#141E30"))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                (44 * d).toInt(),
                (44 * d).toInt()
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = (8 * d).toInt()
                bottomMargin = (4 * d).toInt()
            }
        }
        val spinner = ProgressBar(ctx, null, android.R.attr.progressBarStyleSmall).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(accentColor)
            layoutParams = FrameLayout.LayoutParams(
                (26 * d).toInt(),
                (26 * d).toInt(),
                Gravity.CENTER
            )
        }
        pullRefreshCard.addView(spinner)
        rootVideoCol.addView(pullRefreshCard)

        // 2. Top Category Pills Bar + Sort/Grid Icon Button
        val topBarRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (6 * d).toInt())
        }

        topChipsScroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val chipsContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        chipAllVideos = createCategoryChip("All Videos", 0, d)
        chipShortsReels = createCategoryChip("Shorts & Status", 1, d)
        chipFolders = createCategoryChip("Folders", 2, d)
        chipFavorites = createCategoryChip("Favorites", 3, d)
        chipHistory = createCategoryChip("History", 4, d)

        chipsContainer.addView(chipAllVideos)
        chipsContainer.addView(chipShortsReels)
        chipsContainer.addView(chipFolders)
        chipsContainer.addView(chipFavorites)
        chipsContainer.addView(chipHistory)
        topChipsScroll.addView(chipsContainer)

        btnSortAndLayout = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((38 * d).toInt(), (38 * d).toInt()).apply {
                marginStart = (8 * d).toInt()
            }
            setPadding((8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
            setImageResource(android.R.drawable.ic_menu_sort_by_size)
            setColorFilter(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#182234"))
                setStroke((1 * d).toInt(), accentColor)
            }
            setOnClickListener {
                showSortAndViewModeDialog()
            }
        }

        topBarRow.addView(topChipsScroll)
        topBarRow.addView(btnSortAndLayout)
        rootVideoCol.addView(topBarRow)

        // 3. Folder Back Navigation Header
        folderBackHeader = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding((14 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
            setBackgroundColor(Color.parseColor("#141E30"))
        }

        val btnBackFolder = TextView(ctx).apply {
            text = "<-  Back to Folders"
            setTextColor(accentColor)
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((6 * d).toInt(), (4 * d).toInt(), (14 * d).toInt(), (4 * d).toInt())
            setOnClickListener {
                selectedFolderPath = null
                refreshVideoDisplay()
            }
        }

        tvFolderTitle = TextView(ctx).apply {
            text = ""
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        folderBackHeader.addView(btnBackFolder)
        folderBackHeader.addView(tvFolderTitle)
        rootVideoCol.addView(folderBackHeader)

        // 4. Main Fast RecyclerView (Zero Hang, Supports 360p to 8K Thumbnails)
        rvVideos = RecyclerView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setPadding((8 * d).toInt(), (4 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
            clipToPadding = false
            setHasFixedSize(true)
            setItemViewCacheSize(28)
        }
        applyRecyclerViewLayoutManager(ctx)
        videoAdapter = VideoAdapter(ctx, displayedVideosList)
        rvVideos.adapter = videoAdapter
        attachPullToRefreshAndTabSwipe(ctx)
        rootVideoCol.addView(rvVideos)

        tvEmptyVideos = TextView(ctx).apply {
            text = "Scanning videos..."
            setTextColor(Color.parseColor("#B0C4DE"))
            textSize = 14.5f
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

        // 5. XPlayer-Style Bottom Background Audio Controller Bar with Live SeekBar
        buildXPlayerStyleBackgroundAudioBar(ctx, d)
        rootVideoCol.addView(bgAudioDockCard)

        masterFrame.addView(rootVideoCol)
        updateCategoryChipVisuals(d)
        registerBgReceiver(ctx)

        // Instant zero-lag load from memory cache first, then background scan
        val instantCache = DeepMediaScanner.getCachedVideosInstant()
        if (instantCache.isNotEmpty()) {
            allVideosList.clear()
            allVideosList.addAll(instantCache)
            refreshVideoDisplay()
        }
        triggerBackgroundVideoScan(showToastOnComplete = false)

        return masterFrame
    }

    override fun onResume() {
        super.onResume()
        val ctx = context ?: return
        ThemeStudioHelper.applyThemeAndOneHandModeToRoot(ctx, masterFrame)
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
            // Dances on video screen ONLY when background audio is actively playing
            isDancingProvider = { bgBarVisible && bgBarPlaying || AudioFragment.isSharedPlayerPlaying() },
            onHoldItemTriggered = { heldPosition ->
                if (heldPosition in displayedVideosList.indices) {
                    val chosen = displayedVideosList[heldPosition]
                    val allPaths = ArrayList(displayedVideosList.map { it.path })
                    val intent = Intent(ctx, PlayerActivity::class.java).apply {
                        putExtra("VIDEO_PATH", chosen.path)
                        putExtra("VIDEO_TITLE", chosen.title)
                        putStringArrayListExtra("VIDEO_LIST", allPaths)
                    }
                    Toast.makeText(ctx, "Mascot 3s Hold -> Playing: ${chosen.title}", Toast.LENGTH_SHORT).show()
                    startActivity(intent)
                }
            }
        )
    }

    private fun buildXPlayerStyleBackgroundAudioBar(ctx: Context, d: Float) {
        bgAudioDockCard = CardView(ctx).apply {
            radius = 18f * d
            cardElevation = 12f * d
            setCardBackgroundColor(Color.parseColor("#101928"))
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
            setPadding((12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#101928"))
                cornerRadius = 18f * d
                setStroke((1.4f * d).toInt(), accentColor)
            }
        }

        // Top SeekBar inside the mini-bar so user can scrub forward/backward in background play
        sbBgDockSeek = SeekBar(ctx).apply {
            max = 1000
            progressDrawable.setColorFilter(accentColor, PorterDuff.Mode.SRC_IN)
            thumb.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val targetMs = ((p / 1000f) * bgBarDurationMs).toLong()
                        tvBgDockTime.text = "${formatShortTime(targetMs)} / ${formatShortTime(bgBarDurationMs)}"
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

        val controlsRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((2 * d).toInt(), (4 * d).toInt(), (2 * d).toInt(), 0)
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
                    }
                    startActivity(intent)
                }
            }
        }

        tvBgDockTitle = TextView(ctx).apply {
            text = "Background Audio"
            setTextColor(Color.WHITE)
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        tvBgDockTime = TextView(ctx).apply {
            text = "00:00 / 00:00"
            setTextColor(accentColor)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
        }

        textCol.addView(tvBgDockTitle)
        textCol.addView(tvBgDockTime)

        btnBgDockPrev = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((34 * d).toInt(), (34 * d).toInt()).apply {
                marginEnd = (6 * d).toInt()
            }
            setPadding((6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt())
            setImageResource(android.R.drawable.ic_media_previous)
            setColorFilter(Color.WHITE)
            setOnClickListener {
                ctx.sendBroadcast(Intent(BackgroundAudioService.ACTION_NOTIF_PREV).setPackage(ctx.packageName))
            }
        }

        btnBgDockPlayPause = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((38 * d).toInt(), (38 * d).toInt()).apply {
                marginEnd = (6 * d).toInt()
            }
            setPadding((6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt())
            setImageResource(android.R.drawable.ic_media_pause)
            setColorFilter(accentColor)
            setOnClickListener {
                bgBarPlaying = !bgBarPlaying
                refreshBgDockUi()
                ctx.sendBroadcast(Intent(BackgroundAudioService.ACTION_NOTIF_PLAY_PAUSE).setPackage(ctx.packageName))
            }
        }

        btnBgDockNext = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((34 * d).toInt(), (34 * d).toInt()).apply {
                marginEnd = (8 * d).toInt()
            }
            setPadding((6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt())
            setImageResource(android.R.drawable.ic_media_next)
            setColorFilter(Color.WHITE)
            setOnClickListener {
                ctx.sendBroadcast(Intent(BackgroundAudioService.ACTION_NOTIF_NEXT).setPackage(ctx.packageName))
            }
        }

        btnBgDockClose = TextView(ctx).apply {
            text = "X"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((28 * d).toInt(), (28 * d).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#28FFFFFF"))
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

        controlsRow.addView(textCol)
        controlsRow.addView(btnBgDockPrev)
        controlsRow.addView(btnBgDockPlayPause)
        controlsRow.addView(btnBgDockNext)
        controlsRow.addView(btnBgDockClose)
        dockCol.addView(controlsRow)
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
        tvBgDockTime.text = "${formatShortTime(bgBarPositionMs)} / ${formatShortTime(bgBarDurationMs)}"
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
        rvVideos.layoutManager = if (isGridView && (activeTab != 2 || selectedFolderPath != null)) {
            GridLayoutManager(ctx, 2)
        } else {
            LinearLayoutManager(ctx)
        }
    }

    private fun createCategoryChip(label: String, tabIndex: Int, d: Float): TextView {
        return TextView(requireContext()).apply {
            text = label
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((14 * d).toInt(), (7 * d).toInt(), (14 * d).toInt(), (7 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (6 * d).toInt() }
            setOnClickListener {
                if (tabIndex == 1) {
                    // 2nd Tab ("Shorts & Status") opens the Instagram Reels-style vertical player!
                    openInstagramStyleReelsViewer()
                } else {
                    switchVideoTab(tabIndex)
                }
            }
        }
    }

    private fun switchVideoTab(targetTab: Int) {
        if (!isAdded) return
        val d = resources.displayMetrics.density
        activeTab = targetTab.coerceIn(0, 4)
        selectedFolderPath = null
        updateCategoryChipVisuals(d)
        applyRecyclerViewLayoutManager(requireContext())
        refreshVideoDisplay()
    }

    private fun updateCategoryChipVisuals(d: Float) {
        val chips = listOf(chipAllVideos, chipShortsReels, chipFolders, chipFavorites, chipHistory)
        chips.forEachIndexed { idx, tv ->
            val isSel = idx == activeTab
            tv.setTextColor(if (isSel) Color.parseColor("#0A0A10") else Color.WHITE)
            tv.background = GradientDrawable().apply {
                setColor(if (isSel) accentColor else Color.parseColor("#182234"))
                cornerRadius = 18f * d
            }
        }
    }

    private fun attachPullToRefreshAndTabSwipe(context: Context) {
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
                allVideosList.clear()
                allVideosList.addAll(scanned)
                refreshVideoDisplay()
                if (showToastOnComplete) {
                    Toast.makeText(ctx, "Refreshed! ${scanned.size} Videos Loaded.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun refreshVideoDisplay() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)

        if (activeTab == 2 && selectedFolderPath == null) {
            folderBackHeader.visibility = View.GONE
            showVideoFoldersInRecyclerView(ctx)
            return
        }

        val baseList = when (activeTab) {
            2 -> {
                val folderKey = selectedFolderPath ?: ""
                folderBackHeader.visibility = View.VISIBLE
                tvFolderTitle.text = File(folderKey).name.ifEmpty { "Folder Videos" }
                allVideosList.filter { (File(it.path).parent ?: "") == folderKey }
            }
            3 -> {
                folderBackHeader.visibility = View.GONE
                allVideosList.filter { prefs.getBoolean("fav_${it.path}", false) }
            }
            4 -> {
                folderBackHeader.visibility = View.GONE
                allVideosList.filter { prefs.getLong("history_time_${it.path}", 0L) > 0L }
                    .sortedByDescending { prefs.getLong("history_time_${it.path}", 0L) }
            }
            else -> {
                folderBackHeader.visibility = View.GONE
                allVideosList
            }
        }

        val sortedList = if (activeTab == 4) {
            baseList
        } else {
            when (sortOrder) {
                0 -> baseList // Newest First (Default from scanner)
                1 -> baseList.reversed() // Oldest First (New Last)
                2 -> baseList.sortedBy { it.title.lowercase(Locale.US) } // Name (A to Z)
                3 -> baseList.sortedByDescending { it.title.lowercase(Locale.US) } // Name (Z to A)
                4 -> baseList.sortedByDescending { it.size } // Largest File Size
                5 -> baseList.sortedByDescending { parseDurationToSeconds(it.duration) } // Longest Duration
                else -> baseList
            }
        }

        applyRecyclerViewLayoutManager(ctx)
        if (rvVideos.adapter !== videoAdapter) {
            rvVideos.adapter = videoAdapter
        }

        displayedVideosList.clear()
        displayedVideosList.addAll(sortedList)
        videoAdapter.notifyDataSetChanged()

        if (displayedVideosList.isEmpty()) {
            rvVideos.visibility = View.GONE
            tvEmptyVideos.text = "No videos found in this section"
            tvEmptyVideos.visibility = View.VISIBLE
        } else {
            tvEmptyVideos.visibility = View.GONE
            rvVideos.visibility = View.VISIBLE
        }
    }

    private fun showVideoFoldersInRecyclerView(ctx: Context) {
        val d = ctx.resources.displayMetrics.density
        val folderGroups = allVideosList.groupBy { File(it.path).parent ?: "Internal Storage" }
            .entries
            .sortedByDescending { it.value.size }

        rvVideos.layoutManager = LinearLayoutManager(ctx)
        if (folderGroups.isEmpty()) {
            rvVideos.visibility = View.GONE
            tvEmptyVideos.text = "No video folders found"
            tvEmptyVideos.visibility = View.VISIBLE
            return
        }

        tvEmptyVideos.visibility = View.GONE
        rvVideos.visibility = View.VISIBLE

        rvVideos.adapter = object : RecyclerView.Adapter<FolderCardVH>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderCardVH {
                val card = CardView(ctx).apply {
                    radius = 16f * d
                    cardElevation = 4f * d
                    setCardBackgroundColor(Color.parseColor("#141E30"))
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins((6 * d).toInt(), (5 * d).toInt(), (6 * d).toInt(), (5 * d).toInt())
                    }
                }

                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
                }

                val imgPreview = ImageView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams((72 * d).toInt(), (48 * d).toInt()).apply {
                        marginEnd = (14 * d).toInt()
                    }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }

                val col = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                val tvName = TextView(ctx).apply {
                    setTextColor(Color.WHITE)
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }

                val tvCount = TextView(ctx).apply {
                    setTextColor(accentColor)
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, (3 * d).toInt(), 0, 0)
                }

                val tvArrow = TextView(ctx).apply {
                    text = ">"
                    setTextColor(accentColor)
                    textSize = 17f
                    typeface = Typeface.DEFAULT_BOLD
                }

                col.addView(tvName)
                col.addView(tvCount)
                row.addView(imgPreview)
                row.addView(col)
                row.addView(tvArrow)
                card.addView(row)
                return FolderCardVH(card, imgPreview, tvName, tvCount)
            }

            override fun onBindViewHolder(holder: FolderCardVH, position: Int) {
                InShotScrollAnimator.animateRowOnBind(holder.itemView)
                val entry = folderGroups[position]
                val folderPath = entry.key
                val videos = entry.value
                val folderName = File(folderPath).name.ifEmpty { "Internal Storage" }

                holder.tvName.text = folderName
                holder.tvCount.text = "${videos.size} Videos"
                VideoAdapter.loadGuaranteedVideoThumbnail(ctx, videos.first().path, holder.imgPreview)

                holder.itemView.setOnClickListener {
                    selectedFolderPath = folderPath
                    refreshVideoDisplay()
                }
            }

            override fun getItemCount(): Int = folderGroups.size
        }
    }

    private class FolderCardVH(
        view: View,
        val imgPreview: ImageView,
        val tvName: TextView,
        val tvCount: TextView
    ) : RecyclerView.ViewHolder(view)

    // Sort & Layout Menu with Clear Visual Icons for Every Option
    private fun showSortAndViewModeDialog() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("HYBRID_VIDEO_UI_PREFS", Context.MODE_PRIVATE)
        val options = arrayOf(
            if (!isGridView) "⊞   Switch View: 2-Column Grid View" else "☰   Switch View: Vertical List View",
            if (sortOrder == 0) "🕒   [Active] Sort: Newest First (Latest Videos)" else "🕒   Sort: Newest First (Latest Videos)",
            if (sortOrder == 1) "⏳   [Active] Sort: New Last (Oldest Videos First)" else "⏳   Sort: New Last (Oldest Videos First)",
            if (sortOrder == 2) "🔤   [Active] Sort: Name (A to Z)" else "🔤   Sort: Name (A to Z)",
            if (sortOrder == 3) "🔠   [Active] Sort: Name (Z to A)" else "🔠   Sort: Name (Z to A)",
            if (sortOrder == 4) "📦   [Active] Sort: Largest File Size First" else "📦   Sort: Largest File Size First",
            if (sortOrder == 5) "⏱️   [Active] Sort: Longest Duration First" else "⏱️   Sort: Longest Duration First",
            "📲   Open WhatsApp Status Saver"
        )

        showBrightVideoMenuDialog(ctx, "View Mode & Sort Videos", options) { which ->
            when (which) {
                0 -> {
                    isGridView = !isGridView
                    prefs.edit().putBoolean("video_is_grid", isGridView).apply()
                    refreshVideoDisplay()
                }
                1, 2, 3, 4, 5, 6 -> {
                    sortOrder = which - 1
                    prefs.edit().putInt("video_sort_order", sortOrder).apply()
                    refreshVideoDisplay()
                }
                7 -> {
                    StatusSaverHelper.openWhatsAppStatusSaverDialog(ctx)
                }
            }
        }
    }

    /**
     * Instagram Reels Style Vertical Video Player for the 2nd Section ("Shorts & Status"):
     * - Full-screen vertical swipe feed (PagerSnapHelper)
     * - Right-side vertical action column:
     *   1. Like (Heart icon at top)
     *   2. Share (Middle)
     *   3. 3-Dot Menu (Bottom: Share, 2.0x Speed, Save Status to Phone, Lock to Vault)
     * - Press/Hold & Horizontal Swipe Speed Control:
     *   Starts at 2.0x on press/hold, swipe right -> 4.0x, swipe left -> 0.2x!
     */
    private fun openInstagramStyleReelsViewer() {
        val ctx = context ?: return
        if (allVideosList.isEmpty()) {
            Toast.makeText(ctx, "No videos available for Reels", Toast.LENGTH_SHORT).show()
            return
        }

        val d = ctx.resources.displayMetrics.density
        val prefs = ctx.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        val reelsVideos = allVideosList.toList()

        val dialog = Dialog(ctx, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )

        val rootReelsFrame = FrameLayout(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }

        var activeReelPlayer: ExoPlayer? = ExoPlayer.Builder(ctx).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
        }

        val rvReelsPager = RecyclerView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.VERTICAL, false)
        }
        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(rvReelsPager)

        var currentReelPosition = 0

        rvReelsPager.adapter = object : RecyclerView.Adapter<ReelPageVH>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReelPageVH {
                val pageFrame = FrameLayout(ctx).apply {
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(Color.BLACK)
                }

                val pv = PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                // Speed Badge at Top Center (Shows 2.0x / 4.0x / 0.2x during touch/swipe)
                val tvSpeedBadge = TextView(ctx).apply {
                    text = "2.0x Speed"
                    setTextColor(Color.parseColor("#0A0A10"))
                    textSize = 13.5f
                    typeface = Typeface.DEFAULT_BOLD
                    visibility = View.GONE
                    setPadding((16 * d).toInt(), (7 * d).toInt(), (16 * d).toInt(), (7 * d).toInt())
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    ).apply { topMargin = (56 * d).toInt() }
                    background = GradientDrawable().apply {
                        setColor(accentColor)
                        cornerRadius = 18f * d
                    }
                }

                // Right-Side Instagram Reels Action Column:
                // 1. Like (Top), 2. Share (Middle), 3. Three-Dots (Bottom)
                val rightCol = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.END or Gravity.BOTTOM
                    ).apply {
                        marginEnd = (16 * d).toInt()
                        bottomMargin = (96 * d).toInt()
                    }
                }

                val btnLike = TextView(ctx).apply {
                    text = "♥\nLike"
                    setTextColor(Color.WHITE)
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setPadding((10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt())
                    layoutParams = LinearLayout.LayoutParams((56 * d).toInt(), (58 * d).toInt()).apply {
                        bottomMargin = (18 * d).toInt()
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#66000000"))
                        setStroke((1.2f * d).toInt(), Color.parseColor("#44FFFFFF"))
                    }
                }

                val btnShare = TextView(ctx).apply {
                    text = "➤\nShare"
                    setTextColor(Color.WHITE)
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setPadding((10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt())
                    layoutParams = LinearLayout.LayoutParams((56 * d).toInt(), (58 * d).toInt()).apply {
                        bottomMargin = (18 * d).toInt()
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#66000000"))
                        setStroke((1.2f * d).toInt(), Color.parseColor("#44FFFFFF"))
                    }
                }

                val btnThreeDots = TextView(ctx).apply {
                    text = "⋮"
                    setTextColor(accentColor)
                    textSize = 22f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams((52 * d).toInt(), (52 * d).toInt())
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#66000000"))
                        setStroke((1.2f * d).toInt(), accentColor)
                    }
                }

                rightCol.addView(btnLike)
                rightCol.addView(btnShare)
                rightCol.addView(btnThreeDots)

                // Bottom-Left Reel Title & Resolution Info
                val tvReelTitle = TextView(ctx).apply {
                    setTextColor(Color.WHITE)
                    textSize = 14.5f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    layoutParams = FrameLayout.LayoutParams(
                        (250 * d).toInt(),
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM or Gravity.START
                    ).apply {
                        marginStart = (16 * d).toInt()
                        bottomMargin = (36 * d).toInt()
                    }
                }

                pageFrame.addView(pv)
                pageFrame.addView(tvSpeedBadge)
                pageFrame.addView(rightCol)
                pageFrame.addView(tvReelTitle)

                return ReelPageVH(pageFrame, pv, tvSpeedBadge, btnLike, btnShare, btnThreeDots, tvReelTitle)
            }

            override fun onBindViewHolder(holder: ReelPageVH, position: Int) {
                val item = reelsVideos[position]
                holder.tvTitle.text = item.title
                val isLiked = prefs.getBoolean("fav_${item.path}", false)
                holder.btnLike.setTextColor(if (isLiked) Color.parseColor("#FF4081") else Color.WHITE)

                holder.btnLike.setOnClickListener {
                    val nowLiked = !prefs.getBoolean("fav_${item.path}", false)
                    prefs.edit().putBoolean("fav_${item.path}", nowLiked).apply()
                    holder.btnLike.setTextColor(if (nowLiked) Color.parseColor("#FF4081") else Color.WHITE)
                    Toast.makeText(ctx, if (nowLiked) "Liked Reel ♥" else "Removed Like", Toast.LENGTH_SHORT).show()
                }

                holder.btnShare.setOnClickListener {
                    shareVideoItem(ctx, item.path)
                }

                holder.btnThreeDots.setOnClickListener {
                    val menuItems = arrayOf(
                        "📤   Share Reel Video",
                        "⚡   Toggle 2.0x Fast Speed",
                        "⚡   Set 4.0x Ultra Speed",
                        "🐢   Set 0.2x Slow-Mo Speed",
                        "⚡   Reset Normal 1.0x Speed",
                        "💾   Save / Status Saver",
                        "🔒   Lock into Secret Privacy Vault"
                    )
                    showBrightVideoMenuDialog(ctx, "Reel Options", menuItems) { which ->
                        when (which) {
                            0 -> shareVideoItem(ctx, item.path)
                            1 -> {
                                activeReelPlayer?.playbackParameters = PlaybackParameters(2.0f)
                                holder.tvSpeedBadge.text = "2.0x Fast Speed"
                                holder.tvSpeedBadge.visibility = View.VISIBLE
                            }
                            2 -> {
                                activeReelPlayer?.playbackParameters = PlaybackParameters(4.0f)
                                holder.tvSpeedBadge.text = "4.0x Ultra Speed"
                                holder.tvSpeedBadge.visibility = View.VISIBLE
                            }
                            3 -> {
                                activeReelPlayer?.playbackParameters = PlaybackParameters(0.2f)
                                holder.tvSpeedBadge.text = "0.2x Slow-Mo"
                                holder.tvSpeedBadge.visibility = View.VISIBLE
                            }
                            4 -> {
                                activeReelPlayer?.playbackParameters = PlaybackParameters(1.0f)
                                holder.tvSpeedBadge.visibility = View.GONE
                            }
                            5 -> StatusSaverHelper.openWhatsAppStatusSaverDialog(ctx)
                            6 -> FakeVaultHelper.lockMediaIntoVault(ctx, item.path, item.title)
                        }
                    }
                }

                // Touch & Swipe Speed Control on Reel Screen:
                // Hold/Click starts 2.0x, swipe right -> 4.0x, swipe left -> 0.2x
                var downX = 0f
                var isSpeedTouchActive = false
                val reelGesture = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        activeReelPlayer?.let { p ->
                            val curSp = p.playbackParameters.speed
                            if (abs(curSp - 1.0f) < 0.05f) {
                                p.playbackParameters = PlaybackParameters(2.0f)
                                holder.tvSpeedBadge.text = "2.0x Speed (Tap to Reset)"
                                holder.tvSpeedBadge.visibility = View.VISIBLE
                            } else {
                                p.playbackParameters = PlaybackParameters(1.0f)
                                holder.tvSpeedBadge.visibility = View.GONE
                            }
                        }
                        return true
                    }

                    override fun onLongPress(e: MotionEvent) {
                        isSpeedTouchActive = true
                        activeReelPlayer?.playbackParameters = PlaybackParameters(2.0f)
                        holder.tvSpeedBadge.text = "2.0x (Swipe Left 0.2x | Right 4.0x)"
                        holder.tvSpeedBadge.visibility = View.VISIBLE
                    }
                })

                holder.playerView.setOnTouchListener { _, ev ->
                    reelGesture.onTouchEvent(ev)
                    when (ev.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = ev.x
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = ev.x - downX
                            if (abs(dx) > 70f) {
                                isSpeedTouchActive = true
                                val targetSp = if (dx > 0) 4.0f else 0.2f
                                activeReelPlayer?.playbackParameters = PlaybackParameters(targetSp)
                                holder.tvSpeedBadge.text = "${targetSp}x Speed"
                                holder.tvSpeedBadge.visibility = View.VISIBLE
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            if (isSpeedTouchActive) {
                                isSpeedTouchActive = false
                                activeReelPlayer?.playbackParameters = PlaybackParameters(1.0f)
                                holder.tvSpeedBadge.visibility = View.GONE
                            }
                        }
                    }
                    true
                }

                if (position == currentReelPosition) {
                    bindReelPlayerToHolder(holder, item.path, activeReelPlayer)
                }
            }

            override fun getItemCount(): Int = reelsVideos.size
        }

        rvReelsPager.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                    val snappedView = snapHelper.findSnapView(lm) ?: return
                    val pos = lm.getPosition(snappedView)
                    if (pos != RecyclerView.NO_POSITION && pos != currentReelPosition) {
                        currentReelPosition = pos
                        val vh = recyclerView.findViewHolderForAdapterPosition(pos) as? ReelPageVH
                        if (vh != null && pos in reelsVideos.indices) {
                            bindReelPlayerToHolder(vh, reelsVideos[pos].path, activeReelPlayer)
                        }
                    }
                }
            }
        })

        // Top-Left Back Button on Reels Viewer
        val btnCloseReels = TextView(ctx).apply {
            text = "<-  Shorts & Reels"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((16 * d).toInt(), (10 * d).toInt(), (16 * d).toInt(), (10 * d).toInt())
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            ).apply {
                marginStart = (14 * d).toInt()
                topMargin = (18 * d).toInt()
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#88000000"))
                cornerRadius = 20f * d
                setStroke((1 * d).toInt(), accentColor)
            }
            setOnClickListener { dialog.dismiss() }
        }

        rootReelsFrame.addView(rvReelsPager)
        rootReelsFrame.addView(btnCloseReels)

        dialog.setOnDismissListener {
            activeReelPlayer?.release()
            activeReelPlayer = null
        }

        dialog.setContentView(rootReelsFrame)
        dialog.show()
    }

    private fun bindReelPlayerToHolder(holder: ReelPageVH, path: String, player: ExoPlayer?) {
        if (player == null) return
        holder.playerView.player = player
        val uri = if (path.startsWith("content://") || path.startsWith("http")) {
            Uri.parse(path)
        } else {
            Uri.fromFile(File(path))
        }
        player.setMediaItem(MediaItem.fromUri(uri))
        player.playbackParameters = PlaybackParameters(1.0f)
        holder.tvSpeedBadge.visibility = View.GONE
        player.prepare()
        player.playWhenReady = true
    }

    private class ReelPageVH(
        view: View,
        val playerView: PlayerView,
        val tvSpeedBadge: TextView,
        val btnLike: TextView,
        val btnShare: TextView,
        val btnThreeDots: TextView,
        val tvTitle: TextView
    ) : RecyclerView.ViewHolder(view)

    private fun shareVideoItem(ctx: Context, path: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/*"
                putExtra(Intent.EXTRA_STREAM, Uri.parse(path))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share Video"))
        } catch (_: Exception) {
            Toast.makeText(ctx, "Unable to share video", Toast.LENGTH_SHORT).show()
        }
    }

    private fun parseDurationToSeconds(dur: String): Long {
        val parts = dur.split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600L + parts[1] * 60L + parts[2]
            2 -> parts[0] * 60L + parts[1]
            1 -> parts[0]
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
