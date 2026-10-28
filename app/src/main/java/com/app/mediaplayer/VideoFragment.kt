package com.app.mediaplayer

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
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
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
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
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
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
            val rootCard = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((18 * d).toInt(), (18 * d).toInt(), (18 * d).toInt(), (14 * d).toInt())
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#F810141E"))
                    cornerRadius = 22f * d
                    setStroke((1 * d).toInt(), Color.parseColor("#26FFFFFF"))
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
                    setColor(Color.parseColor("#1EFFFFFF"))
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

    // Unique Cinema Spotlight Banner (Distinct from MX Player / XPlayer)
    private lateinit var spotlightHeroCard: CardView
    private lateinit var imgSpotlightPoster: ImageView
    private lateinit var tvSpotlightBadge: TextView
    private lateinit var tvSpotlightTitle: TextView
    private lateinit var tvSpotlightSub: TextView
    private lateinit var pbSpotlightProgress: ProgressBar

    private lateinit var topChipsScroll: HorizontalScrollView
    private lateinit var chipAllVideos: TextView
    private lateinit var chipShortsReels: TextView
    private lateinit var chipFolders: TextView
    private lateinit var chipFavorites: TextView
    private lateinit var chipHistory: TextView
    private lateinit var chipRecycleBin: TextView
    private lateinit var btnSortAndLayout: ImageView

    private lateinit var multiSelectTopBar: LinearLayout
    private lateinit var tvMultiSelectCount: TextView
    private lateinit var btnSelectAllToggle: TextView
    private lateinit var btnCancelMultiSelect: TextView
    private lateinit var multiSelectBottomDock: LinearLayout

    private lateinit var folderBackHeader: LinearLayout
    private lateinit var tvFolderTitle: TextView
    private lateinit var rvVideos: RecyclerView
    private lateinit var tvEmptyVideos: TextView

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
    private lateinit var videoAdapter: VideoAdapter

    private var activeTab = 0
    private var selectedFolderPath: String? = null
    private var sortOrder = 0
    private var isGridView = false
    private var accentColor = Color.parseColor("#00FFFF")
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

        // 1. Pull-to-Refresh Circular Spinner
        pullRefreshCard = CardView(ctx).apply {
            radius = 22f * d
            cardElevation = 10f * d
            setCardBackgroundColor(Color.parseColor("#141E30"))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                (42 * d).toInt(),
                (42 * d).toInt()
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = (6 * d).toInt()
                bottomMargin = (4 * d).toInt()
            }
        }
        val spinner = ProgressBar(ctx, null, android.R.attr.progressBarStyleSmall).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(
                (24 * d).toInt(),
                (24 * d).toInt(),
                Gravity.CENTER
            )
        }
        pullRefreshCard.addView(spinner)
        rootVideoCol.addView(pullRefreshCard)

        // 2. Multi-Selection Top Header Bar
        buildMultiSelectTopHeaderBar(ctx, d)
        rootVideoCol.addView(multiSelectTopBar)

        // 3. Unique Cinema Spotlight Hero Card (Distinct OTT-style experience, unlike MX/XPlayer)
        buildCinemaSpotlightHeroCard(ctx, d)
        rootVideoCol.addView(spotlightHeroCard)

        // 4. Sleek Category Navigation Strip with Horizontal Fade + Recycle Bin Access
        val topBarRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * d).toInt(), (6 * d).toInt(), (10 * d).toInt(), (6 * d).toInt())
        }

        topChipsScroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            isHorizontalFadingEdgeEnabled = true
            setFadingEdgeLength((24 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val chipsContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        chipAllVideos = createCategoryChip("Cinema Feed", 0, d)
        chipFolders = createCategoryChip("Folders", 2, d)
        chipShortsReels = createCategoryChip("Reels", 1, d)
        chipFavorites = createCategoryChip("Favorites", 3, d)
        chipHistory = createCategoryChip("Watched", 4, d)

        val trashCount = PermanentDeleteHelper.getRecycleBinCount(ctx)
        chipRecycleBin = TextView(ctx).apply {
            text = if (trashCount > 0) "Recycle Bin ($trashCount)" else "Recycle Bin"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding((14 * d).toInt(), (7 * d).toInt(), (14 * d).toInt(), (7 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (6 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#18FFFFFF"))
                cornerRadius = 16f * d
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                PermanentDeleteHelper.openRecycleBinDialog(ctx) {
                    triggerBackgroundVideoScan(showToastOnComplete = false)
                }
            }
        }

        chipsContainer.addView(chipAllVideos)
        chipsContainer.addView(chipFolders)
        chipsContainer.addView(chipShortsReels)
        chipsContainer.addView(chipFavorites)
        chipsContainer.addView(chipHistory)
        chipsContainer.addView(chipRecycleBin)
        topChipsScroll.addView(chipsContainer)

        btnSortAndLayout = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((38 * d).toInt(), (38 * d).toInt()).apply {
                marginStart = (6 * d).toInt()
            }
            setPadding((8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
            setImageResource(android.R.drawable.ic_menu_sort_by_size)
            setColorFilter(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#16FFFFFF"))
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                showSortAndViewModeDialog()
            }
        }

        topBarRow.addView(topChipsScroll)
        topBarRow.addView(btnSortAndLayout)
        rootVideoCol.addView(topBarRow)

        // 5. Folder Back Header
        folderBackHeader = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding((14 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
            setBackgroundColor(Color.parseColor("#141E30"))
        }

        val btnBackFolder = TextView(ctx).apply {
            text = "<-  All Folders"
            setTextColor(Color.WHITE)
            textSize = 14f
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

        // 6. Instant High-Performance Video RecyclerView
        rvVideos = RecyclerView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setPadding((8 * d).toInt(), (4 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
            clipToPadding = false
            setHasFixedSize(true)
            setItemViewCacheSize(32)
            itemAnimator = null
        }
        applyRecyclerViewLayoutManager(ctx)
        videoAdapter = VideoAdapter(ctx, displayedVideosList) { isSelecting, selectedCount ->
            updateMultiSelectBarUi(isSelecting, selectedCount)
        }
        rvVideos.adapter = videoAdapter
        attachPullToRefreshAndTabSwipe(ctx)
        rootVideoCol.addView(rvVideos)

        tvEmptyVideos = TextView(ctx).apply {
            text = "No videos found"
            setTextColor(Color.parseColor("#9FB4CE"))
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

        // 7. Multi-Select Bottom Action Dock
        buildMultiSelectBottomActionDock(ctx, d)
        rootVideoCol.addView(multiSelectBottomDock)

        // 8. Upgraded Background Audio Dock Bar
        buildUpgradedBackgroundAudioBar(ctx, d)
        rootVideoCol.addView(bgAudioDockCard)

        masterFrame.addView(rootVideoCol)
        updateCategoryChipVisuals(d)
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

    private fun buildCinemaSpotlightHeroCard(ctx: Context, d: Float) {
        spotlightHeroCard = CardView(ctx).apply {
            radius = 20f * d
            cardElevation = 8f * d
            setCardBackgroundColor(Color.parseColor("#141E30"))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (128 * d).toInt()
            ).apply {
                setMargins((12 * d).toInt(), (6 * d).toInt(), (12 * d).toInt(), (4 * d).toInt())
            }
        }

        val heroFrame = FrameLayout(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        imgSpotlightPoster = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.68f
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val gradientScrim = View(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#E60A0E17"), Color.parseColor("#990A0E17"), Color.parseColor("#330A0E17"))
            )
        }

        val textOverlayCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * d).toInt(), (12 * d).toInt(), (16 * d).toInt(), (14 * d).toInt())
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        tvSpotlightBadge = TextView(ctx).apply {
            text = "CONTINUE WATCHING"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((10 * d).toInt(), (3 * d).toInt(), (10 * d).toInt(), (3 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (6 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#00E676"))
                cornerRadius = 10f * d
            }
        }

        tvSpotlightTitle = TextView(ctx).apply {
            text = "Featured Cinema"
            setTextColor(Color.WHITE)
            textSize = 16.5f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        tvSpotlightSub = TextView(ctx).apply {
            text = "Tap to resume instant playback"
            setTextColor(Color.parseColor("#D0D8E8"))
            textSize = 12f
            setPadding(0, (3 * d).toInt(), 0, (8 * d).toInt())
        }

        pbSpotlightProgress = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 400
            progressDrawable.setColorFilter(Color.parseColor("#00E676"), PorterDuff.Mode.SRC_IN)
            layoutParams = LinearLayout.LayoutParams((180 * d).toInt(), (4 * d).toInt())
        }

        textOverlayCol.addView(tvSpotlightBadge)
        textOverlayCol.addView(tvSpotlightTitle)
        textOverlayCol.addView(tvSpotlightSub)
        textOverlayCol.addView(pbSpotlightProgress)

        heroFrame.addView(imgSpotlightPoster)
        heroFrame.addView(gradientScrim)
        heroFrame.addView(textOverlayCol)
        spotlightHeroCard.addView(heroFrame)
    }

    private fun refreshSpotlightHeroBanner(ctx: Context) {
        if (allVideosList.isEmpty() || activeTab != 0 || selectedFolderPath != null) {
            spotlightHeroCard.visibility = View.GONE
            return
        }

        val prefs = ctx.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        val lastWatchedVideo = allVideosList
            .filter { prefs.getLong("watched_pos_${it.path}", 0L) > 3000L }
            .maxByOrNull { prefs.getLong("history_time_${it.path}", 0L) }

        val featured = lastWatchedVideo ?: allVideosList.first()
        spotlightHeroCard.visibility = View.VISIBLE

        val cleanName = featured.title.substringBeforeLast('.').replace("_", " ").trim().ifEmpty { featured.title }
        tvSpotlightTitle.text = cleanName

        val watchedPos = prefs.getLong("watched_pos_${featured.path}", 0L)
        val durMs = if (featured.duration in 1L..3600L) featured.duration * 1000L else featured.duration.coerceAtLeast(1000L)

        if (lastWatchedVideo != null && watchedPos > 3000L) {
            tvSpotlightBadge.text = "CONTINUE WATCHING"
            tvSpotlightSub.text = "Resume at ${formatShortTime(watchedPos)} / ${formatShortTime(durMs)}"
            pbSpotlightProgress.visibility = View.VISIBLE
            pbSpotlightProgress.progress = ((watchedPos.toFloat() / durMs.toFloat()) * 1000f).roundToInt().coerceIn(40, 980)
        } else {
            tvSpotlightBadge.text = "SPOTLIGHT PREMIERE"
            tvSpotlightSub.text = "${VideoAdapter.formatVideoDurationAny(featured.duration)}  •  Instant 8K Cinema"
            pbSpotlightProgress.visibility = View.GONE
        }

        VideoAdapter.loadGuaranteedVideoThumbnail(ctx, featured.path, imgSpotlightPoster)

        spotlightHeroCard.setOnClickListener {
            val allPaths = ArrayList(allVideosList.map { it.path })
            val intent = Intent(ctx, PlayerActivity::class.java).apply {
                putExtra("VIDEO_PATH", featured.path)
                putExtra("VIDEO_TITLE", featured.title)
                putStringArrayListExtra("VIDEO_LIST", allPaths)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            startActivity(intent)
        }
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
                setColor(Color.WHITE)
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
                setColor(Color.parseColor("#121C2D"))
                cornerRadius = 20f * d
                setStroke((1.5f * d).toInt(), Color.parseColor("#44FFFFFF"))
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
                setColor(Color.parseColor("#00E676"))
                cornerRadius = 14f * d
            }
            setOnClickListener {
                val selected = videoAdapter.getSelectedVideos()
                if (selected.isEmpty()) {
                    Toast.makeText(ctx, "Select at least 1 video", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
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
                setColor(Color.parseColor("#1E2E46"))
                cornerRadius = 14f * d
                setStroke((1 * d).toInt(), Color.WHITE)
            }
            setOnClickListener {
                val selected = videoAdapter.getSelectedVideos()
                if (selected.isEmpty()) {
                    Toast.makeText(ctx, "Select at least 1 video", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
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
                    Toast.makeText(ctx, "Unable to share selected videos", Toast.LENGTH_SHORT).show()
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
                setColor(Color.parseColor("#FF1744"))
                cornerRadius = 14f * d
            }
            setOnClickListener {
                val selected = videoAdapter.getSelectedVideos()
                if (selected.isEmpty()) {
                    Toast.makeText(ctx, "Select at least 1 video to delete", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
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

    override fun onResume() {
        super.onResume()
        val ctx = context ?: return
        ThemeStudioHelper.applyThemeAndOneHandModeToRoot(ctx, masterFrame)
        val trashCount = PermanentDeleteHelper.getRecycleBinCount(ctx)
        chipRecycleBin.text = if (trashCount > 0) "Recycle Bin ($trashCount)" else "Recycle Bin"
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
                    Toast.makeText(ctx, "Playing: ${chosen.title}", Toast.LENGTH_SHORT).show()
                    startActivity(intent)
                }
            }
        )
    }

    private fun buildUpgradedBackgroundAudioBar(ctx: Context, d: Float) {
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
            setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#101928"))
                cornerRadius = 18f * d
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
                setColor(Color.WHITE)
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
            progressDrawable.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
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
        rvVideos.layoutManager = if (isGridView && (activeTab != 2 || selectedFolderPath != null)) {
            GridLayoutManager(ctx, 2)
        } else {
            LinearLayoutManager(ctx)
        }
    }

    private fun createCategoryChip(label: String, tabIndex: Int, d: Float): TextView {
        return TextView(requireContext()).apply {
            text = label
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((14 * d).toInt(), (7 * d).toInt(), (14 * d).toInt(), (7 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (6 * d).toInt() }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                if (tabIndex == 1) {
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
        videoAdapter.exitMultiSelectMode()
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
                setColor(if (isSel) Color.WHITE else Color.parseColor("#18FFFFFF"))
                cornerRadius = 16f * d
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
                val changed = scanned.size != allVideosList.size || showToastOnComplete || allVideosList.isEmpty()
                allVideosList.clear()
                allVideosList.addAll(scanned)
                if (changed) {
                    refreshVideoDisplay()
                } else {
                    refreshSpotlightHeroBanner(ctx)
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

        refreshSpotlightHeroBanner(ctx)

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
                0 -> baseList
                1 -> baseList.reversed()
                2 -> baseList.sortedBy { it.title.lowercase(Locale.US) }
                3 -> baseList.sortedByDescending { it.title.lowercase(Locale.US) }
                4 -> baseList.sortedByDescending { it.size }
                5 -> baseList.sortedByDescending { parseDurationToSeconds(it.duration) }
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
                    cardElevation = 3f * d
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
                    setPadding((12 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
                }

                val thumbCard = CardView(ctx).apply {
                    radius = 10f * d
                    cardElevation = 0f
                    setCardBackgroundColor(Color.parseColor("#1F2C40"))
                    layoutParams = LinearLayout.LayoutParams((76 * d).toInt(), (48 * d).toInt()).apply {
                        marginEnd = (14 * d).toInt()
                    }
                }

                val imgPreview = ImageView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                thumbCard.addView(imgPreview)

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
                    setTextColor(Color.parseColor("#9FB4CE"))
                    textSize = 12f
                    setPadding(0, (2 * d).toInt(), 0, 0)
                }

                val tvNewBadge = TextView(ctx).apply {
                    text = "NEW"
                    setTextColor(Color.parseColor("#0A0A10"))
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding((8 * d).toInt(), (3 * d).toInt(), (8 * d).toInt(), (3 * d).toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = (8 * d).toInt() }
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#00E676"))
                        cornerRadius = 8f * d
                    }
                }

                val tvArrow = TextView(ctx).apply {
                    text = ">"
                    setTextColor(Color.parseColor("#9FB4CE"))
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                }

                col.addView(tvName)
                col.addView(tvCount)
                row.addView(thumbCard)
                row.addView(col)
                row.addView(tvNewBadge)
                row.addView(tvArrow)
                card.addView(row)
                return FolderCardVH(card, imgPreview, tvName, tvCount, tvNewBadge)
            }

            override fun onBindViewHolder(holder: FolderCardVH, position: Int) {
                val entry = folderGroups[position]
                val folderPath = entry.key
                val videos = entry.value
                val folderName = File(folderPath).name.ifEmpty { "Internal Storage" }

                holder.tvName.text = "$folderName (${videos.size})"
                val totalFolderBytes = videos.sumOf { it.size }
                val totalMb = totalFolderBytes / (1024.0 * 1024.0)
                val sizeLabel = if (totalMb >= 1024.0) {
                    String.format(Locale.US, "%.2f GB", totalMb / 1024.0)
                } else {
                    String.format(Locale.US, "%.1f MB", totalMb)
                }
                holder.tvCount.text = "${videos.size} Videos  •  $sizeLabel"

                val hasRecent = videos.any { (System.currentTimeMillis() - File(it.path).lastModified()) < 7L * 24 * 3600 * 1000L }
                holder.tvNewBadge.visibility = if (hasRecent) View.VISIBLE else View.GONE

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
        val tvCount: TextView,
        val tvNewBadge: TextView
    ) : RecyclerView.ViewHolder(view)

    private fun showSortAndViewModeDialog() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("HYBRID_VIDEO_UI_PREFS", Context.MODE_PRIVATE)
        val trashCount = PermanentDeleteHelper.getRecycleBinCount(ctx)
        val options = arrayOf(
            if (!isGridView) "Switch View: 2-Column Cinema Grid" else "Switch View: Detailed List View",
            "Select Multiple Videos (Batch Share / Delete)",
            "Open Recycle Bin ($trashCount Items)",
            if (sortOrder == 0) "[Active] Sort: Newest First" else "Sort: Newest First",
            if (sortOrder == 1) "[Active] Sort: Oldest First" else "Sort: Oldest First",
            if (sortOrder == 2) "[Active] Sort: Name (A to Z)" else "Sort: Name (A to Z)",
            if (sortOrder == 3) "[Active] Sort: Name (Z to A)" else "Sort: Name (Z to A)",
            if (sortOrder == 4) "[Active] Sort: Largest File Size" else "Sort: Largest File Size",
            if (sortOrder == 5) "[Active] Sort: Longest Duration" else "Sort: Longest Duration",
            "Open WhatsApp Status Saver"
        )

        showBrightVideoMenuDialog(ctx, "View Mode, Sort & Tools", options) { which ->
            when (which) {
                0 -> {
                    isGridView = !isGridView
                    prefs.edit().putBoolean("video_is_grid", isGridView).apply()
                    refreshVideoDisplay()
                }
                1 -> videoAdapter.selectAllVideos()
                2 -> {
                    PermanentDeleteHelper.openRecycleBinDialog(ctx) {
                        triggerBackgroundVideoScan(showToastOnComplete = false)
                    }
                }
                3, 4, 5, 6, 7, 8 -> {
                    sortOrder = which - 3
                    prefs.edit().putInt("video_sort_order", sortOrder).apply()
                    refreshVideoDisplay()
                }
                9 -> StatusSaverHelper.openWhatsAppStatusSaverDialog(ctx)
            }
        }
    }

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

        val renderersFactory = DefaultRenderersFactory(ctx).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            setEnableDecoderFallback(true)
        }
        val fastLoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1500, 15000, 200, 500)
            .build()

        var activeReelPlayer: ExoPlayer? = ExoPlayer.Builder(ctx, renderersFactory)
            .setLoadControl(fastLoadControl)
            .build().apply {
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
                        setColor(Color.WHITE)
                        cornerRadius = 18f * d
                    }
                }

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
                    text = "Like"
                    setTextColor(Color.WHITE)
                    textSize = 12.5f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setPadding((10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt())
                    layoutParams = LinearLayout.LayoutParams((54 * d).toInt(), (54 * d).toInt()).apply {
                        bottomMargin = (16 * d).toInt()
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#55000000"))
                    }
                }

                val btnShare = TextView(ctx).apply {
                    text = "Share"
                    setTextColor(Color.WHITE)
                    textSize = 12.5f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setPadding((10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt())
                    layoutParams = LinearLayout.LayoutParams((54 * d).toInt(), (54 * d).toInt()).apply {
                        bottomMargin = (16 * d).toInt()
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#55000000"))
                    }
                }

                val btnThreeDots = TextView(ctx).apply {
                    text = "..."
                    setTextColor(Color.WHITE)
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams((50 * d).toInt(), (50 * d).toInt())
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#55000000"))
                    }
                }

                rightCol.addView(btnLike)
                rightCol.addView(btnShare)
                rightCol.addView(btnThreeDots)

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
                    Toast.makeText(ctx, if (nowLiked) "Liked Reel" else "Removed Like", Toast.LENGTH_SHORT).show()
                }

                holder.btnShare.setOnClickListener {
                    shareVideoItem(ctx, item.path)
                }

                holder.btnThreeDots.setOnClickListener {
                    val menuItems = arrayOf(
                        "Share Reel Video",
                        "Toggle 2.0x Fast Speed",
                        "Set 4.0x Ultra Speed",
                        "Set 0.2x Slow-Mo Speed",
                        "Reset Normal 1.0x Speed",
                        "Save / Status Saver",
                        "Move Reel to Recycle Bin / Delete"
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
                            6 -> {
                                PermanentDeleteHelper.confirmAndDeletePermanently(
                                    context = ctx,
                                    title = item.title,
                                    path = item.path,
                                    mediaId = item.id,
                                    isVideo = true
                                ) {
                                    dialog.dismiss()
                                    triggerBackgroundVideoScan(showToastOnComplete = false)
                                }
                            }
                        }
                    }
                }

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

        val btnCloseReels = TextView(ctx).apply {
            text = "<-  Reels"
            setTextColor(Color.WHITE)
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((16 * d).toInt(), (8 * d).toInt(), (16 * d).toInt(), (8 * d).toInt())
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            ).apply {
                marginStart = (14 * d).toInt()
                topMargin = (18 * d).toInt()
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#66000000"))
                cornerRadius = 18f * d
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
