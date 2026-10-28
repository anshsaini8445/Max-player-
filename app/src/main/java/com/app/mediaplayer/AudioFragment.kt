package com.app.mediaplayer

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
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.TextUtils
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.ArrayList
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.random.Random

class AudioFragment : Fragment() {

    companion object {
        private const val SNAPSHOT_AUDIO_PREF = "HYBRID_INSTANT_AUDIO_SNAPSHOT"
        private const val KEY_AUDIO_SNAPSHOT = "instant_audio_rows_v3"

        @Volatile
        private var memoryAudioCache: List<AudioModel> = emptyList()

        private var sharedAudioSessionIdVal: Int = 0
        private var currentPlayingPathVal: String = ""
        private var currentPlayingTitleVal: String = ""
        private var currentPlayingArtistVal: String = ""
        private var isSharedPlayingVal: Boolean = false

        fun getSharedAudioSessionId(): Int = sharedAudioSessionIdVal
        fun getCurrentPlayingPath(): String = currentPlayingPathVal
        fun getCurrentPlayingTitle(): String = currentPlayingTitleVal
        fun getCurrentPlayingArtist(): String = currentPlayingArtistVal
        fun isSharedPlayerPlaying(): Boolean = isSharedPlayingVal

        fun updateSharedAudioState(
            sessionId: Int,
            path: String,
            title: String,
            artist: String,
            isPlaying: Boolean
        ) {
            if (sessionId > 0) sharedAudioSessionIdVal = sessionId
            currentPlayingPathVal = path
            currentPlayingTitleVal = title
            currentPlayingArtistVal = artist
            isSharedPlayingVal = isPlaying
        }

        fun toggleSharedPlaybackFromMiniPlayer(context: Context) {
            isSharedPlayingVal = !isSharedPlayingVal
            context.sendBroadcast(
                Intent(BackgroundAudioService.ACTION_NOTIF_PLAY_PAUSE)
                    .setPackage(context.packageName)
            )
        }
    }

    private lateinit var masterHostFrame: FrameLayout
    private lateinit var rootAudioLayout: LinearLayout
    private lateinit var pullRefreshCard: CardView

    private lateinit var topTabsScroll: HorizontalScrollView
    private lateinit var tabSongs: TextView
    private lateinit var tabFolders: TextView
    private lateinit var tabAlbums: TextView
    private lateinit var tabArtists: TextView
    private lateinit var tabFavorites: TextView
    private lateinit var tvSongsTotalCount: TextView

    private lateinit var multiSelectTopBar: LinearLayout
    private lateinit var tvMultiSelectCount: TextView
    private lateinit var btnSelectAllToggle: TextView
    private lateinit var btnCancelMultiSelect: TextView
    private lateinit var multiSelectBottomDock: LinearLayout

    private lateinit var groupBackBar: LinearLayout
    private lateinit var tvGroupHeaderTitle: TextView
    private lateinit var rvAudioList: RecyclerView
    private lateinit var tvEmptyAudio: TextView

    private lateinit var miniPlayerCard: CardView
    private lateinit var imgMiniCover: ImageView
    private lateinit var tvMiniTitle: TextView
    private lateinit var tvMiniArtist: TextView
    private lateinit var btnMiniPrev: ImageView
    private lateinit var btnMiniPlayPause: ImageView
    private lateinit var btnMiniNext: ImageView
    private lateinit var btnMiniThreeDots: ImageView

    private val allSongsList = mutableListOf<AudioModel>()
    private val displayedSongsList = mutableListOf<AudioModel>()
    private lateinit var audioAdapter: AudioAdapter

    private var activeTab = 0
    private var selectedGroupKey: String? = null
    private var sortOrder = 0
    private var currentMiniIndex = 0
    private var isReceiverRegistered = false

    private val miniPlayerSyncReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!isAdded) return
            when (intent?.action) {
                BackgroundAudioService.ACTION_NOTIF_PLAY_PAUSE -> {
                    updateMiniPlayerPlayIcon()
                }
                BackgroundAudioService.ACTION_NOTIF_NEXT -> {
                    if (allSongsList.isNotEmpty()) {
                        currentMiniIndex = (currentMiniIndex + 1) % allSongsList.size
                        syncMiniBarWithSong(allSongsList[currentMiniIndex])
                    }
                }
                BackgroundAudioService.ACTION_NOTIF_PREV -> {
                    if (allSongsList.isNotEmpty()) {
                        currentMiniIndex = (currentMiniIndex - 1 + allSongsList.size) % allSongsList.size
                        syncMiniBarWithSong(allSongsList[currentMiniIndex])
                    }
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

        masterHostFrame = FrameLayout(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor(theme.bgHex))
        }

        ThemeStudioHelper.applyThemeAndOneHandModeToRoot(ctx, masterHostFrame)

        rootAudioLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 1. Pull-to-Refresh Circular Spinner Card
        pullRefreshCard = CardView(ctx).apply {
            radius = 22f * d
            cardElevation = 10f * d
            setCardBackgroundColor(Color.parseColor("#182234"))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                (42 * d).toInt(),
                (42 * d).toInt()
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = (8 * d).toInt()
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
        rootAudioLayout.addView(pullRefreshCard)

        // 2. Multi-Selection Top Header Bar
        buildMultiSelectTopHeaderBar(ctx, d)
        rootAudioLayout.addView(multiSelectTopBar)

        // 3. Sleek Category Tabs with Horizontal Fade Edge + Borderless Equalizer & 3-Dot Icons
        val topHeaderRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * d).toInt(), (10 * d).toInt(), (10 * d).toInt(), (6 * d).toInt())
        }

        topTabsScroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            isHorizontalFadingEdgeEnabled = true
            setFadingEdgeLength((28 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tabsRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        tabSongs = createTopTabItem("Songs", 0, d)
        tabFolders = createTopTabItem("Folders", 1, d)
        tabAlbums = createTopTabItem("Albums", 2, d)
        tabArtists = createTopTabItem("Artists", 3, d)
        tabFavorites = createTopTabItem("Favorites", 4, d)

        tabsRow.addView(tabSongs)
        tabsRow.addView(tabFolders)
        tabsRow.addView(tabAlbums)
        tabsRow.addView(tabArtists)
        tabsRow.addView(tabFavorites)
        topTabsScroll.addView(tabsRow)

        val btnTopEqualizer = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((40 * d).toInt(), (40 * d).toInt()).apply {
                marginStart = (6 * d).toInt()
            }
            setPadding((9 * d).toInt(), (9 * d).toInt(), (9 * d).toInt(), (9 * d).toInt())
            setImageDrawable(PureWhiteAudioStudioIcon("EQUALIZER", d))
            background = makeSubtleIconRippleCircle()
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                EqualizerHelper.showEqualizerDialog(ctx, sharedAudioSessionIdVal)
            }
        }

        val btnTopThreeDotMenu = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((40 * d).toInt(), (40 * d).toInt()).apply {
                marginStart = (4 * d).toInt()
            }
            setPadding((9 * d).toInt(), (9 * d).toInt(), (9 * d).toInt(), (9 * d).toInt())
            setImageDrawable(PureWhiteAudioStudioIcon("THREE_DOTS", d))
            background = makeSubtleIconRippleCircle()
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                showAudioSortAndToolsMenu()
            }
        }

        topHeaderRow.addView(topTabsScroll)
        topHeaderRow.addView(btnTopEqualizer)
        topHeaderRow.addView(btnTopThreeDotMenu)
        rootAudioLayout.addView(topHeaderRow)

        // 4. Minimal Action Bar: Single Unified "Shuffle Play" Button + Total Song Counter
        val studioActionRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * d).toInt(), (4 * d).toInt(), (14 * d).toInt(), (6 * d).toInt())
        }

        val btnShuffleAll = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * d).toInt(), (8 * d).toInt(), (16 * d).toInt(), (8 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 18f * d
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                val list = if (displayedSongsList.isNotEmpty()) displayedSongsList else allSongsList
                if (list.isNotEmpty()) {
                    val randIdx = Random.nextInt(list.size)
                    currentMiniIndex = randIdx
                    isSharedPlayingVal = true
                    syncMiniBarWithSong(list[randIdx])
                    launchFullAudioPlayer(randIdx, list)
                }
            }
        }

        val iconShuffle = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((18 * d).toInt(), (18 * d).toInt()).apply {
                marginEnd = (8 * d).toInt()
            }
            setImageDrawable(PureWhiteAudioStudioIcon("SHUFFLE_DARK", d))
        }

        val tvShuffleText = TextView(ctx).apply {
            text = "Shuffle Play"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }

        btnShuffleAll.addView(iconShuffle)
        btnShuffleAll.addView(tvShuffleText)

        tvSongsTotalCount = TextView(ctx).apply {
            text = "0 Songs"
            setTextColor(Color.parseColor("#9FB4CE"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        studioActionRow.addView(btnShuffleAll)
        studioActionRow.addView(tvSongsTotalCount)
        rootAudioLayout.addView(studioActionRow)

        // 5. Group Back Bar
        groupBackBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding((14 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
            setBackgroundColor(Color.parseColor("#141E30"))
        }

        val btnBackGroup = TextView(ctx).apply {
            text = "<-  Back"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((6 * d).toInt(), (4 * d).toInt(), (16 * d).toInt(), (4 * d).toInt())
            setOnClickListener {
                selectedGroupKey = null
                refreshAudioDisplay()
            }
        }

        tvGroupHeaderTitle = TextView(ctx).apply {
            text = ""
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        groupBackBar.addView(btnBackGroup)
        groupBackBar.addView(tvGroupHeaderTitle)
        rootAudioLayout.addView(groupBackBar)

        // 6. High-Speed Audio RecyclerView
        rvAudioList = RecyclerView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setPadding((10 * d).toInt(), (4 * d).toInt(), (10 * d).toInt(), (8 * d).toInt())
            clipToPadding = false
            setHasFixedSize(true)
            setItemViewCacheSize(32)
            itemAnimator = null
        }
        rvAudioList.layoutManager = LinearLayoutManager(ctx)

        audioAdapter = AudioAdapter(
            context = ctx,
            audioList = displayedSongsList,
            onSelectionChanged = { isSelecting, count ->
                updateMultiSelectBarUi(isSelecting, count)
            },
            onItemClick = { clickedPos ->
                if (clickedPos in displayedSongsList.indices) {
                    currentMiniIndex = clickedPos
                    isSharedPlayingVal = true
                    syncMiniBarWithSong(displayedSongsList[clickedPos])
                    launchFullAudioPlayer(clickedPos, displayedSongsList)
                }
            }
        )
        rvAudioList.adapter = audioAdapter
        attachPullToRefreshAndSwipeTabs(ctx)
        rootAudioLayout.addView(rvAudioList)

        tvEmptyAudio = TextView(ctx).apply {
            text = "No songs found in this category"
            setTextColor(Color.parseColor("#9FB4CE"))
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
        rootAudioLayout.addView(tvEmptyAudio)

        // 7. Multi-Select Bottom Action Dock
        buildMultiSelectBottomActionDock(ctx, d)
        rootAudioLayout.addView(multiSelectBottomDock)

        // 8. Sleek Floating Bottom Mini-Player Dock
        buildUpgradedBottomMiniPlayer(ctx, d)
        rootAudioLayout.addView(miniPlayerCard)

        masterHostFrame.addView(rootAudioLayout)
        updateTabVisuals(d)
        registerMiniReceiver(ctx)

        val cachedSongs = getCachedSongsInstant(ctx)
        if (cachedSongs.isNotEmpty()) {
            allSongsList.clear()
            allSongsList.addAll(cachedSongs)
            syncMiniBarWithSong(allSongsList[0])
            refreshAudioDisplay()
        }
        scanAllSongsInBackground(showRefreshToast = false)

        return masterHostFrame
    }

    private fun buildUpgradedBottomMiniPlayer(ctx: Context, d: Float) {
        miniPlayerCard = CardView(ctx).apply {
            radius = 16f * d
            cardElevation = 10f * d
            setCardBackgroundColor(Color.parseColor("#141E30"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins((10 * d).toInt(), (2 * d).toInt(), (10 * d).toInt(), (6 * d).toInt())
            }
        }

        val miniRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((10 * d).toInt(), (8 * d).toInt(), (10 * d).toInt(), (8 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141E30"))
                cornerRadius = 16f * d
                setStroke((1 * d).toInt(), Color.parseColor("#22FFFFFF"))
            }
        }

        imgMiniCover = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((44 * d).toInt(), (44 * d).toInt()).apply {
                marginEnd = (10 * d).toInt()
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        AudioAdapter.loadAlbumArtOrFallbackInto(ctx, "", "Studio Music", imgMiniCover)

        val miniTextCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (6 * d).toInt()
            }
        }

        tvMiniTitle = TextView(ctx).apply {
            text = "Tap any song to play"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        tvMiniArtist = TextView(ctx).apply {
            text = "Ready to Play"
            setTextColor(Color.parseColor("#9FB4CE"))
            textSize = 11.5f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, (2 * d).toInt(), 0, 0)
        }

        miniTextCol.addView(tvMiniTitle)
        miniTextCol.addView(tvMiniArtist)

        btnMiniPrev = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((36 * d).toInt(), (36 * d).toInt()).apply {
                marginEnd = (4 * d).toInt()
            }
            setPadding((8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
            setImageDrawable(PureWhiteAudioStudioIcon("PREV", d))
            background = makeSubtleIconRippleCircle()
            setOnClickListener {
                if (allSongsList.isEmpty()) return@setOnClickListener
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                currentMiniIndex = (currentMiniIndex - 1 + allSongsList.size) % allSongsList.size
                isSharedPlayingVal = true
                syncMiniBarWithSong(allSongsList[currentMiniIndex])
                launchFullAudioPlayer(currentMiniIndex, allSongsList)
            }
        }

        btnMiniPlayPause = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((42 * d).toInt(), (42 * d).toInt()).apply {
                marginEnd = (4 * d).toInt()
            }
            setPadding((10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt())
            setImageDrawable(PureWhiteAudioStudioIcon("PLAY", d))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#24FFFFFF"))
            }
            setOnClickListener {
                if (allSongsList.isEmpty()) return@setOnClickListener
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                if (currentPlayingPathVal.isEmpty()) {
                    isSharedPlayingVal = true
                    syncMiniBarWithSong(allSongsList[currentMiniIndex.coerceIn(0, allSongsList.size - 1)])
                    launchFullAudioPlayer(currentMiniIndex.coerceIn(0, allSongsList.size - 1), allSongsList)
                } else {
                    toggleSharedPlaybackFromMiniPlayer(ctx)
                    updateMiniPlayerPlayIcon()
                }
            }
        }

        btnMiniNext = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((36 * d).toInt(), (36 * d).toInt()).apply {
                marginEnd = (4 * d).toInt()
            }
            setPadding((8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
            setImageDrawable(PureWhiteAudioStudioIcon("NEXT", d))
            background = makeSubtleIconRippleCircle()
            setOnClickListener {
                if (allSongsList.isEmpty()) return@setOnClickListener
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                currentMiniIndex = (currentMiniIndex + 1) % allSongsList.size
                isSharedPlayingVal = true
                syncMiniBarWithSong(allSongsList[currentMiniIndex])
                launchFullAudioPlayer(currentMiniIndex, allSongsList)
            }
        }

        btnMiniThreeDots = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((36 * d).toInt(), (36 * d).toInt())
            setPadding((8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
            setImageDrawable(PureWhiteAudioStudioIcon("THREE_DOTS", d))
            background = makeSubtleIconRippleCircle()
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                if (displayedSongsList.isNotEmpty()) {
                    val safeIdx = currentMiniIndex.coerceIn(0, displayedSongsList.size - 1)
                    audioAdapter.showSongThreeDotMenu(safeIdx)
                } else {
                    showAudioSortAndToolsMenu()
                }
            }
        }

        miniRow.addView(imgMiniCover)
        miniRow.addView(miniTextCol)
        miniRow.addView(btnMiniPrev)
        miniRow.addView(btnMiniPlayPause)
        miniRow.addView(btnMiniNext)
        miniRow.addView(btnMiniThreeDots)

        miniRow.setOnClickListener {
            if (allSongsList.isNotEmpty()) {
                launchFullAudioPlayer(currentMiniIndex.coerceIn(0, allSongsList.size - 1), allSongsList)
            }
        }

        miniPlayerCard.addView(miniRow)
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
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        btnSelectAllToggle = TextView(ctx).apply {
            text = "Select All"
            setTextColor(Color.WHITE)
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((14 * d).toInt(), (7 * d).toInt(), (14 * d).toInt(), (7 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (8 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#24FFFFFF"))
                cornerRadius = 14f * d
            }
            setOnClickListener {
                audioAdapter.selectAllSongs()
            }
        }

        btnCancelMultiSelect = TextView(ctx).apply {
            text = "Done"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((14 * d).toInt(), (7 * d).toInt(), (14 * d).toInt(), (7 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 14f * d
            }
            setOnClickListener {
                audioAdapter.exitMultiSelectMode()
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
                setMargins((10 * d).toInt(), (4 * d).toInt(), (10 * d).toInt(), (6 * d).toInt())
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141E30"))
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
            setPadding((10 * d).toInt(), (11 * d).toInt(), (10 * d).toInt(), (11 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (6 * d).toInt()
            }
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 12f * d
            }
            setOnClickListener {
                val selected = audioAdapter.getSelectedSongs()
                if (selected.isEmpty()) {
                    Toast.makeText(ctx, "Select at least 1 song", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val paths = ArrayList(selected.map { it.path })
                audioAdapter.exitMultiSelectMode()
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
            setPadding((10 * d).toInt(), (11 * d).toInt(), (10 * d).toInt(), (11 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (6 * d).toInt()
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#22FFFFFF"))
                cornerRadius = 12f * d
            }
            setOnClickListener {
                val selected = audioAdapter.getSelectedSongs()
                if (selected.isEmpty()) {
                    Toast.makeText(ctx, "Select at least 1 song", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val uris = ArrayList<Uri>()
                selected.forEach { uris.add(Uri.parse(it.path)) }
                audioAdapter.exitMultiSelectMode()
                try {
                    val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "audio/*"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share ${uris.size} Songs"))
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
            setPadding((10 * d).toInt(), (11 * d).toInt(), (10 * d).toInt(), (11 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FF1744"))
                cornerRadius = 12f * d
            }
            setOnClickListener {
                val selected = audioAdapter.getSelectedSongs()
                if (selected.isEmpty()) return@setOnClickListener
                val first = selected.first()
                PermanentDeleteHelper.confirmAndDeletePermanently(
                    context = ctx,
                    title = "${selected.size} Selected Songs (${first.title})",
                    path = first.path,
                    mediaId = first.id,
                    isVideo = false
                ) {
                    val delSet = selected.map { it.path }.toHashSet()
                    selected.drop(1).forEach { s ->
                        try {
                            File(s.path).delete()
                        } catch (_: Exception) {
                        }
                    }
                    allSongsList.removeAll { delSet.contains(it.path) }
                    displayedSongsList.removeAll { delSet.contains(it.path) }
                    audioAdapter.exitMultiSelectMode()
                    refreshAudioDisplay()
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
                if (count == displayedSongsList.size && count > 0) "Unselect All" else "Select All"
        }
    }

    override fun onResume() {
        super.onResume()
        val ctx = context ?: return
        ThemeStudioHelper.applyThemeAndOneHandModeToRoot(ctx, masterHostFrame)

        if (currentPlayingPathVal.isNotEmpty()) {
            tvMiniTitle.text = AudioThreeDotMenuHelper.formatCleanSongTitle(currentPlayingTitleVal)
            tvMiniArtist.text = currentPlayingArtistVal.ifEmpty { "Unknown Artist" }
            AudioAdapter.loadAlbumArtOrFallbackInto(ctx, currentPlayingPathVal, currentPlayingTitleVal, imgMiniCover)
            updateMiniPlayerPlayIcon()
        }

        audioAdapter.notifyDataSetChanged()
        scanAllSongsInBackground(showRefreshToast = false)

        MascotCompanionHelper.attachInteractiveMascot(
            context = ctx,
            hostContainer = masterHostFrame,
            recyclerView = rvAudioList,
            isVideoScreen = false,
            isDancingProvider = { isSharedPlayingVal },
            onHoldItemTriggered = { idx ->
                if (idx in displayedSongsList.indices) {
                    currentMiniIndex = idx
                    isSharedPlayingVal = true
                    syncMiniBarWithSong(displayedSongsList[idx])
                    launchFullAudioPlayer(idx, displayedSongsList)
                }
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isReceiverRegistered) {
            try {
                requireContext().unregisterReceiver(miniPlayerSyncReceiver)
            } catch (_: Exception) {
            }
            isReceiverRegistered = false
        }
    }

    private fun registerMiniReceiver(ctx: Context) {
        if (isReceiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(BackgroundAudioService.ACTION_NOTIF_PLAY_PAUSE)
                addAction(BackgroundAudioService.ACTION_NOTIF_NEXT)
                addAction(BackgroundAudioService.ACTION_NOTIF_PREV)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.registerReceiver(miniPlayerSyncReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                ctx.registerReceiver(miniPlayerSyncReceiver, filter)
            }
            isReceiverRegistered = true
        } catch (_: Exception) {
        }
    }

    private fun updateMiniPlayerPlayIcon() {
        if (!isAdded) return
        val d = resources.displayMetrics.density
        btnMiniPlayPause.setImageDrawable(
            PureWhiteAudioStudioIcon(if (isSharedPlayingVal) "PAUSE" else "PLAY", d)
        )
    }

    private fun syncMiniBarWithSong(song: AudioModel) {
        if (!isAdded) return
        currentPlayingPathVal = song.path
        currentPlayingTitleVal = song.title
        currentPlayingArtistVal = song.artist

        tvMiniTitle.text = AudioThreeDotMenuHelper.formatCleanSongTitle(song.title)
        tvMiniArtist.text = song.artist.ifEmpty { "Unknown Artist" }
        AudioAdapter.loadAlbumArtOrFallbackInto(requireContext(), song.path, song.title, imgMiniCover)
        updateMiniPlayerPlayIcon()
    }

    private fun makeSubtleIconRippleCircle(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#14FFFFFF"))
        }
    }

    private fun createTopTabItem(title: String, index: Int, d: Float): TextView {
        return TextView(requireContext()).apply {
            text = title
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((15 * d).toInt(), (7 * d).toInt(), (15 * d).toInt(), (7 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (6 * d).toInt() }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                switchAudioTab(index)
            }
        }
    }

    private fun switchAudioTab(targetTab: Int) {
        if (!isAdded) return
        val d = resources.displayMetrics.density
        activeTab = targetTab.coerceIn(0, 4)
        selectedGroupKey = null
        audioAdapter.exitMultiSelectMode()
        updateTabVisuals(d)
        refreshAudioDisplay()
    }

    private fun updateTabVisuals(d: Float) {
        val tabs = listOf(tabSongs, tabFolders, tabAlbums, tabArtists, tabFavorites)
        tabs.forEachIndexed { idx, tv ->
            val isSel = idx == activeTab
            tv.setTextColor(if (isSel) Color.parseColor("#0A0A10") else Color.WHITE)
            tv.background = GradientDrawable().apply {
                setColor(if (isSel) Color.WHITE else Color.parseColor("#18FFFFFF"))
                cornerRadius = 16f * d
            }
            if (isSel) {
                topTabsScroll.post {
                    topTabsScroll.smoothScrollTo(tv.left - (40 * d).toInt(), 0)
                }
            }
        }
    }

    private fun attachPullToRefreshAndSwipeTabs(context: Context) {
        var touchStartY = 0f
        var canPullRefresh = false

        rvAudioList.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        touchStartY = e.y
                        canPullRefresh = !rv.canScrollVertically(-1)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (canPullRefresh && (e.y - touchStartY) > 95f) {
                            pullRefreshCard.visibility = View.VISIBLE
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (pullRefreshCard.visibility == View.VISIBLE) {
                            scanAllSongsInBackground(showRefreshToast = true)
                        }
                        canPullRefresh = false
                    }
                }
                return false
            }
        })

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
                if (abs(dx) > abs(dy) * 1.6f && abs(dx) > 110f && abs(velocityX) > 220f) {
                    if (dx < 0) {
                        val nextTab = (activeTab + 1).coerceAtMost(4)
                        if (nextTab != activeTab) {
                            switchAudioTab(nextTab)
                            return true
                        }
                    } else {
                        if (selectedGroupKey != null) {
                            selectedGroupKey = null
                            refreshAudioDisplay()
                            return true
                        }
                        val prevTab = (activeTab - 1).coerceAtLeast(0)
                        if (prevTab != activeTab) {
                            switchAudioTab(prevTab)
                            return true
                        }
                    }
                }
                return false
            }
        })

        rvAudioList.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                swipeDetector.onTouchEvent(e)
                return false
            }
        })
    }

    private fun getCachedSongsInstant(context: Context): List<AudioModel> {
        if (memoryAudioCache.isNotEmpty()) return memoryAudioCache
        val out = ArrayList<AudioModel>()
        try {
            val raw = context.getSharedPreferences(SNAPSHOT_AUDIO_PREF, Context.MODE_PRIVATE)
                .getString(KEY_AUDIO_SNAPSHOT, null) ?: return emptyList()
            for (line in raw.split('\n')) {
                if (line.isBlank()) continue
                val parts = line.split('|')
                if (parts.size >= 5) {
                    val path = parts[2]
                    if (PermanentDeleteHelper.isMarkedDeleted(context, path)) continue
                    out.add(
                        AudioModel(
                            id = parts[0].toLongOrNull() ?: 0L,
                            title = parts[1],
                            path = path,
                            artist = parts[3],
                            duration = parts[4].toLongOrNull() ?: 0L
                        )
                    )
                }
            }
            if (out.isNotEmpty()) {
                memoryAudioCache = out
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun saveSongsSnapshotToDisk(context: Context, list: List<AudioModel>) {
        try {
            val sb = StringBuilder()
            val limit = list.size.coerceAtMost(400)
            for (i in 0 until limit) {
                val s = list[i]
                sb.append(s.id).append('|')
                    .append(s.title.replace("|", " ").replace("\n", " ")).append('|')
                    .append(s.path).append('|')
                    .append(s.artist.replace("|", " ").replace("\n", " ")).append('|')
                    .append(s.duration).append('\n')
            }
            context.getSharedPreferences(SNAPSHOT_AUDIO_PREF, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_AUDIO_SNAPSHOT, sb.toString())
                .apply()
        } catch (_: Exception) {
        }
    }

    private fun scanAllSongsInBackground(showRefreshToast: Boolean) {
        val ctx = context ?: return
        thread {
            val list = mutableListOf<AudioModel>()
            val seenPaths = HashSet<String>()
            try {
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.DURATION
                )
                ctx.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${MediaStore.Audio.Media.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                    val nameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                    val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                    val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    val durCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)

                    while (cursor.moveToNext()) {
                        val id = if (idCol != -1) cursor.getLong(idCol) else 0L
                        val path = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                        if (path.isEmpty() || !seenPaths.add(path)) continue
                        if (PermanentDeleteHelper.isMarkedDeleted(ctx, path)) continue
                        val f = File(path)
                        if (!f.exists() || f.length() < 1024L) continue
                        val dur = if (durCol != -1) cursor.getLong(durCol).coerceAtLeast(0L) else 0L
                        val title = (if (nameCol != -1) cursor.getString(nameCol) else null)
                            ?: (if (titleCol != -1) cursor.getString(titleCol) else null)
                            ?: f.name
                        val artist = (if (artistCol != -1) cursor.getString(artistCol) else null)
                            ?.takeIf { it != "<unknown>" && it.isNotBlank() } ?: "Unknown Artist"

                        list.add(
                            AudioModel(
                                id = id,
                                title = title,
                                path = path,
                                artist = artist,
                                duration = dur
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            memoryAudioCache = list
            saveSongsSnapshotToDisk(ctx, list)

            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                pullRefreshCard.visibility = View.GONE
                allSongsList.clear()
                allSongsList.addAll(list)
                if (allSongsList.isNotEmpty() && currentPlayingPathVal.isEmpty()) {
                    syncMiniBarWithSong(allSongsList[0])
                }
                refreshAudioDisplay()
                if (showRefreshToast) {
                    Toast.makeText(ctx, "Refreshed! ${list.size} Songs Loaded.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun refreshAudioDisplay() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)

        if (activeTab in 1..3 && selectedGroupKey == null) {
            groupBackBar.visibility = View.GONE
            showGroupedCardsInRecyclerView(ctx, activeTab)
            return
        }

        val baseList = when (activeTab) {
            1 -> {
                val parentKey = selectedGroupKey ?: ""
                groupBackBar.visibility = View.VISIBLE
                tvGroupHeaderTitle.text = File(parentKey).name.ifEmpty { "Folder Songs" }
                allSongsList.filter { (File(it.path).parent ?: "") == parentKey }
            }
            2 -> {
                val albumKey = selectedGroupKey ?: ""
                groupBackBar.visibility = View.VISIBLE
                tvGroupHeaderTitle.text = albumKey
                allSongsList.filter { File(it.path).parentFile?.name.orEmpty().ifEmpty { "Singles & Albums" } == albumKey }
            }
            3 -> {
                val artistKey = selectedGroupKey ?: ""
                groupBackBar.visibility = View.VISIBLE
                tvGroupHeaderTitle.text = artistKey
                allSongsList.filter { it.artist == artistKey }
            }
            4 -> {
                groupBackBar.visibility = View.GONE
                allSongsList.filter { prefs.getBoolean("fav_audio_${it.path}", false) }
            }
            else -> {
                groupBackBar.visibility = View.GONE
                allSongsList
            }
        }

        val sorted = when (sortOrder) {
            1 -> baseList.sortedBy { it.title.lowercase(Locale.US) }
            2 -> baseList.sortedBy { it.artist.lowercase(Locale.US) }
            3 -> baseList.sortedByDescending { it.duration }
            4 -> baseList.sortedByDescending { File(it.path).length() }
            else -> baseList
        }

        if (rvAudioList.adapter !== audioAdapter) {
            rvAudioList.adapter = audioAdapter
        }

        displayedSongsList.clear()
        displayedSongsList.addAll(sorted)
        audioAdapter.notifyDataSetChanged()
        tvSongsTotalCount.text = "${displayedSongsList.size} Songs"

        if (displayedSongsList.isEmpty()) {
            rvAudioList.visibility = View.GONE
            tvEmptyAudio.visibility = View.VISIBLE
        } else {
            tvEmptyAudio.visibility = View.GONE
            rvAudioList.visibility = View.VISIBLE
        }
    }

    private fun showGroupedCardsInRecyclerView(ctx: Context, tabType: Int) {
        val d = ctx.resources.displayMetrics.density
        val groups = when (tabType) {
            1 -> allSongsList.groupBy { File(it.path).parent ?: "Internal Storage" }
            2 -> allSongsList.groupBy { File(it.path).parentFile?.name.orEmpty().ifEmpty { "Singles & Albums" } }
            else -> allSongsList.groupBy { it.artist.ifEmpty { "Unknown Artist" } }
        }.entries.sortedByDescending { it.value.size }

        tvSongsTotalCount.text =
            "${groups.size} ${if (tabType == 1) "Folders" else if (tabType == 2) "Albums" else "Artists"}"

        if (groups.isEmpty()) {
            rvAudioList.visibility = View.GONE
            tvEmptyAudio.visibility = View.VISIBLE
            return
        }
        tvEmptyAudio.visibility = View.GONE
        rvAudioList.visibility = View.VISIBLE

        rvAudioList.adapter = object : RecyclerView.Adapter<AudioGroupVH>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudioGroupVH {
                val card = CardView(ctx).apply {
                    radius = 14f * d
                    cardElevation = 2f * d
                    setCardBackgroundColor(Color.parseColor("#141E30"))
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins((4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt())
                    }
                }
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
                }
                val imgThumb = ImageView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams((50 * d).toInt(), (50 * d).toInt()).apply {
                        marginEnd = (12 * d).toInt()
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
                    setTextColor(Color.parseColor("#9FB4CE"))
                    textSize = 12f
                    setPadding(0, (2 * d).toInt(), 0, 0)
                }
                val tvArrow = TextView(ctx).apply {
                    text = ">"
                    setTextColor(Color.parseColor("#9FB4CE"))
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                }
                col.addView(tvName)
                col.addView(tvCount)
                row.addView(imgThumb)
                row.addView(col)
                row.addView(tvArrow)
                card.addView(row)
                return AudioGroupVH(card, imgThumb, tvName, tvCount)
            }

            override fun onBindViewHolder(holder: AudioGroupVH, position: Int) {
                val entry = groups[position]
                val rawKey = entry.key
                val songs = entry.value
                val displayName = if (tabType == 1) File(rawKey).name.ifEmpty { "Storage" } else rawKey
                holder.tvName.text = displayName
                holder.tvCount.text = "${songs.size} Songs"
                val firstSong = songs.first()
                AudioAdapter.loadAlbumArtOrFallbackInto(ctx, firstSong.path, displayName, holder.imgThumb)
                holder.itemView.setOnClickListener {
                    selectedGroupKey = rawKey
                    refreshAudioDisplay()
                }
            }

            override fun getItemCount(): Int = groups.size
        }
    }

    private class AudioGroupVH(
        view: View,
        val imgThumb: ImageView,
        val tvName: TextView,
        val tvCount: TextView
    ) : RecyclerView.ViewHolder(view)

    private fun launchFullAudioPlayer(index: Int, sourceList: List<AudioModel>) {
        val ctx = context ?: return
        if (index !in sourceList.indices) return
        val selected = sourceList[index]

        val paths = ArrayList<String>(sourceList.size)
        val titles = ArrayList<String>(sourceList.size)
        val artists = ArrayList<String>(sourceList.size)

        for (s in sourceList) {
            paths.add(s.path)
            titles.add(s.title)
            artists.add(s.artist)
        }

        val intent = Intent(ctx, AudioPlayerActivity::class.java).apply {
            putExtra("AUDIO_PATH", selected.path)
            putExtra("AUDIO_TITLE", selected.title)
            putExtra("AUDIO_ARTIST", selected.artist)
            putExtra("START_INDEX", index)
            putStringArrayListExtra("AUDIO_LIST", paths)
            putStringArrayListExtra("AUDIO_TITLES", titles)
            putStringArrayListExtra("AUDIO_ARTISTS", artists)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        startActivity(intent)
    }

    private fun showAudioSortAndToolsMenu() {
        val ctx = context ?: return
        val items = arrayOf(
            "Select Multiple Songs (Batch Delete / Share)",
            if (sortOrder == 0) "[Active] Sort: Newest Added" else "Sort: Newest Added",
            if (sortOrder == 1) "[Active] Sort: Song Name (A to Z)" else "Sort: Song Name (A to Z)",
            if (sortOrder == 2) "[Active] Sort: Artist Name" else "Sort: Artist Name",
            if (sortOrder == 3) "[Active] Sort: Longest Duration" else "Sort: Longest Duration",
            if (sortOrder == 4) "[Active] Sort: Largest File Size" else "Sort: Largest File Size",
            "5-Band Dolby Equalizer & Bass Boost",
            "Sound & Pitch Lab (Lofi / Slowed + Reverb)",
            "Convert Video to MP3 Audio"
        )

        AudioAdapter.showPureWhiteAudioMenuDialog(ctx, "Audio Tools & Sort", items) { which ->
            when (which) {
                0 -> audioAdapter.selectAllSongs()
                1, 2, 3, 4, 5 -> {
                    sortOrder = which - 1
                    refreshAudioDisplay()
                }
                6 -> EqualizerHelper.showEqualizerDialog(ctx, sharedAudioSessionIdVal)
                7 -> AudioPitchHelper.showSoundAndPitchLabDialog(ctx, null)
                8 -> {
                    try {
                        startActivity(Intent(ctx, Mp3ConverterActivity::class.java))
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private class PureWhiteAudioStudioIcon(
        private val type: String,
        private val d: Float
    ) : Drawable() {

        private val whiteStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.2f * d
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.WHITE
        }

        private val whiteFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }

        private val darkStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.2f * d
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.parseColor("#0A0A10")
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val r = b.width().coerceAtMost(b.height()) * 0.40f
            val path = Path()

            when (type) {
                "PLAY" -> {
                    path.moveTo(cx - r * 0.30f, cy - r * 0.56f)
                    path.lineTo(cx + r * 0.62f, cy)
                    path.lineTo(cx - r * 0.30f, cy + r * 0.56f)
                    path.close()
                    canvas.drawPath(path, whiteFill)
                }
                "PAUSE" -> {
                    val bw = r * 0.32f
                    val bh = r * 1.08f
                    canvas.drawRoundRect(
                        RectF(cx - r * 0.48f, cy - bh / 2f, cx - r * 0.48f + bw, cy + bh / 2f),
                        2.5f * d,
                        2.5f * d,
                        whiteFill
                    )
                    canvas.drawRoundRect(
                        RectF(cx + r * 0.16f, cy - bh / 2f, cx + r * 0.16f + bw, cy + bh / 2f),
                        2.5f * d,
                        2.5f * d,
                        whiteFill
                    )
                }
                "PREV" -> {
                    canvas.drawLine(cx - r * 0.55f, cy - r * 0.52f, cx - r * 0.55f, cy + r * 0.52f, whiteStroke)
                    path.moveTo(cx + r * 0.55f, cy - r * 0.52f)
                    path.lineTo(cx - r * 0.38f, cy)
                    path.lineTo(cx + r * 0.55f, cy + r * 0.52f)
                    path.close()
                    canvas.drawPath(path, whiteFill)
                }
                "NEXT" -> {
                    canvas.drawLine(cx + r * 0.55f, cy - r * 0.52f, cx + r * 0.55f, cy + r * 0.52f, whiteStroke)
                    path.moveTo(cx - r * 0.55f, cy - r * 0.52f)
                    path.lineTo(cx + r * 0.38f, cy)
                    path.lineTo(cx - r * 0.55f, cy + r * 0.52f)
                    path.close()
                    canvas.drawPath(path, whiteFill)
                }
                "EQUALIZER" -> {
                    canvas.drawLine(cx - r * 0.58f, cy - r * 0.72f, cx - r * 0.58f, cy + r * 0.72f, whiteStroke)
                    canvas.drawLine(cx, cy - r * 0.72f, cx, cy + r * 0.72f, whiteStroke)
                    canvas.drawLine(cx + r * 0.58f, cy - r * 0.72f, cx + r * 0.58f, cy + r * 0.72f, whiteStroke)
                    canvas.drawCircle(cx - r * 0.58f, cy - r * 0.2f, 2.7f * d, whiteFill)
                    canvas.drawCircle(cx, cy + r * 0.25f, 2.7f * d, whiteFill)
                    canvas.drawCircle(cx + r * 0.58f, cy - r * 0.25f, 2.7f * d, whiteFill)
                }
                "SHUFFLE", "SHUFFLE_DARK" -> {
                    val p = if (type == "SHUFFLE_DARK") darkStroke else whiteStroke
                    canvas.drawLine(cx - r * 0.7f, cy - r * 0.45f, cx + r * 0.7f, cy + r * 0.45f, p)
                    canvas.drawLine(cx - r * 0.7f, cy + r * 0.45f, cx + r * 0.7f, cy - r * 0.45f, p)
                    canvas.drawLine(cx + r * 0.35f, cy - r * 0.45f, cx + r * 0.7f, cy - r * 0.45f, p)
                    canvas.drawLine(cx + r * 0.35f, cy + r * 0.45f, cx + r * 0.7f, cy + r * 0.45f, p)
                }
                "THREE_DOTS" -> {
                    canvas.drawCircle(cx, cy - r * 0.65f, 2.4f * d, whiteFill)
                    canvas.drawCircle(cx, cy, 2.4f * d, whiteFill)
                    canvas.drawCircle(cx, cy + r * 0.65f, 2.4f * d, whiteFill)
                }
            }
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
