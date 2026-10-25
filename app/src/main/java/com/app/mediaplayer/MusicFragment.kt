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
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
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
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.ArrayList
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.abs

open class MusicFragment : Fragment() {

    private lateinit var rootMusicLayout: LinearLayout
    private lateinit var topTabsScroll: HorizontalScrollView
    private lateinit var tabSongs: TextView
    private lateinit var tabFolders: TextView
    private lateinit var tabAlbums: TextView
    private lateinit var tabArtists: TextView
    private lateinit var tabFavorites: TextView
    private lateinit var tvSongsTotalCount: TextView

    private lateinit var groupBackBar: LinearLayout
    private lateinit var tvGroupHeaderTitle: TextView
    private lateinit var rvMusicList: RecyclerView
    private lateinit var tvEmptyMusic: TextView
    private lateinit var pullRefreshIndicator: ProgressBar

    private lateinit var miniPlayerCard: CardView
    private lateinit var imgMiniCover: ImageView
    private lateinit var tvMiniTitle: TextView
    private lateinit var tvMiniArtist: TextView
    private lateinit var btnMiniPlayPause: ImageView
    private lateinit var btnMiniNext: ImageView

    private val allSongsList = mutableListOf<AudioModel>()
    private val displayedSongsList = mutableListOf<AudioModel>()
    private lateinit var audioAdapter: AudioAdapter

    private var activeTab = 0
    private var selectedGroupKey: String? = null
    private var sortOrder = 0
    private var currentMiniIndex = 0
    private var isMiniPlaying = false
    private var accentColor = Color.parseColor("#00FFFF")
    private var isReceiverRegistered = false
    private var mascotHostLayout: FrameLayout? = null

    private val miniPlayerSyncReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!isAdded) return
            when (intent?.action) {
                BackgroundAudioService.ACTION_NOTIF_PLAY_PAUSE -> {
                    isMiniPlaying = !isMiniPlaying
                    updateMiniPlayerPlayIcon()
                }
                BackgroundAudioService.ACTION_NOTIF_NEXT -> {
                    if (allSongsList.isNotEmpty()) {
                        currentMiniIndex = (currentMiniIndex + 1) % allSongsList.size
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
        accentColor = Color.parseColor(theme.accentHex)

        val masterFrame = FrameLayout(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor(theme.bgHex))
        }
        mascotHostLayout = masterFrame

        rootMusicLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Pull-to-Refresh rotating circular indicator on top
        pullRefreshIndicator = ProgressBar(ctx, null, android.R.attr.progressBarStyleSmall).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(accentColor)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                (28 * d).toInt(),
                (28 * d).toInt()
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = (4 * d).toInt()
            }
        }
        rootMusicLayout.addView(pullRefreshIndicator)

        val topHeaderRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * d).toInt(), (6 * d).toInt(), (12 * d).toInt(), (6 * d).toInt())
        }

        topTabsScroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
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
        tabFavorites = createTopTabItem("Favorites ♥", 4, d)

        tabsRow.addView(tabSongs)
        tabsRow.addView(tabFolders)
        tabsRow.addView(tabAlbums)
        tabsRow.addView(tabArtists)
        tabsRow.addView(tabFavorites)
        topTabsScroll.addView(tabsRow)

        val btnSortSongs = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((36 * d).toInt(), (36 * d).toInt()).apply {
                marginStart = (8 * d).toInt()
            }
            setPadding((7 * d).toInt(), (7 * d).toInt(), (7 * d).toInt(), (7 * d).toInt())
            setImageResource(android.R.drawable.ic_menu_sort_by_size)
            setColorFilter(Color.WHITE)
            setOnClickListener { showMusicSortAndToolsMenu() }
        }

        topHeaderRow.addView(topTabsScroll)
        topHeaderRow.addView(btnSortSongs)
        rootMusicLayout.addView(topHeaderRow)

        val shuffleRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * d).toInt(), (2 * d).toInt(), (14 * d).toInt(), (6 * d).toInt())
        }

        val btnPlayShuffleAll = TextView(ctx).apply {
            text = "▶  Play / Shuffle All"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((16 * d).toInt(), (7 * d).toInt(), (16 * d).toInt(), (7 * d).toInt())
            background = GradientDrawable().apply {
                setColor(accentColor)
                cornerRadius = 18f * d
            }
            setOnClickListener {
                if (displayedSongsList.isNotEmpty()) {
                    launchFullAudioPlayer(0, displayedSongsList)
                } else if (allSongsList.isNotEmpty()) {
                    launchFullAudioPlayer(0, allSongsList)
                }
            }
        }

        tvSongsTotalCount = TextView(ctx).apply {
            text = "0 Songs"
            setTextColor(Color.parseColor("#B0C4DE"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((12 * d).toInt(), 0, 0, 0)
        }

        shuffleRow.addView(btnPlayShuffleAll)
        shuffleRow.addView(tvSongsTotalCount)
        rootMusicLayout.addView(shuffleRow)

        groupBackBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding((14 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
            setBackgroundColor(Color.parseColor("#141E30"))
        }

        val btnBackGroup = TextView(ctx).apply {
            text = "←  Back"
            setTextColor(accentColor)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((6 * d).toInt(), (4 * d).toInt(), (16 * d).toInt(), (4 * d).toInt())
            setOnClickListener {
                selectedGroupKey = null
                refreshMusicDisplay()
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
        rootMusicLayout.addView(groupBackBar)

        rvMusicList = RecyclerView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setPadding((10 * d).toInt(), (4 * d).toInt(), (10 * d).toInt(), (8 * d).toInt())
            clipToPadding = false
            setHasFixedSize(true)
            setItemViewCacheSize(24)
        }
        rvMusicList.layoutManager = LinearLayoutManager(ctx)
        audioAdapter = AudioAdapter(ctx, displayedSongsList) { clickedPos ->
            currentMiniIndex = clickedPos
            isMiniPlaying = true
            syncMiniBarWithSong(displayedSongsList[clickedPos])
            launchFullAudioPlayer(clickedPos, displayedSongsList)
        }
        rvMusicList.adapter = audioAdapter
        attachSwipeToRefreshAndTabs(ctx)
        rootMusicLayout.addView(rvMusicList)

        tvEmptyMusic = TextView(ctx).apply {
            text = "No songs found in this category"
            setTextColor(Color.parseColor("#B0C4DE"))
            textSize = 14.5f
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        rootMusicLayout.addView(tvEmptyMusic)

        miniPlayerCard = CardView(ctx).apply {
            radius = 18f * d
            cardElevation = 10f * d
            setCardBackgroundColor(Color.parseColor("#121C2C"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins((10 * d).toInt(), (4 * d).toInt(), (10 * d).toInt(), (8 * d).toInt())
            }
        }

        val miniRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#121C2C"))
                cornerRadius = 18f * d
                setStroke((1.3f * d).toInt(), accentColor)
            }
        }

        imgMiniCover = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((44 * d).toInt(), (44 * d).toInt()).apply {
                marginEnd = (12 * d).toInt()
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        val miniTextCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (8 * d).toInt()
            }
        }

        tvMiniTitle = TextView(ctx).apply {
            text = "Select a song to play"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        tvMiniArtist = TextView(ctx).apply {
            text = "Hybrid Player Hi-Res Audio"
            setTextColor(accentColor)
            textSize = 11.5f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, (2 * d).toInt(), 0, 0)
        }

        miniTextCol.addView(tvMiniTitle)
        miniTextCol.addView(tvMiniArtist)

        btnMiniPlayProxyView() // setup

        val btnMiniPlayPause = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((42 * d).toInt(), (42 * d).toInt()).apply {
                marginEnd = (6 * d).toInt()
            }
            setImageDrawable(MiniPlayerControlIconDrawable("PLAY", d, accentColor))
            setOnClickListener {
                if (allSongsList.isEmpty()) return@setOnClickListener
                isMiniPlaying = !isMiniPlaying
                updateMiniPlayerPlayIcon()
                val song = allSongsList[currentMiniIndex.coerceIn(0, allSongsList.size - 1)]
                BackgroundAudioService.updateForegroundNotification(
                    context = ctx,
                    title = song.title,
                    artist = song.artist,
                    isPlaying = isMiniPlaying,
                    isVideoBackground = false,
                    videoPath = song.path,
                    durationMs = song.duration
                )
                ctx.sendBroadcast(Intent(BackgroundAudioService.ACTION_NOTIF_PLAY_PAUSE).setPackage(ctx.packageName))
            }
        }
        this.btnMiniPlayPause = btnMiniPlayPause

        btnMiniNext = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((38 * d).toInt(), (38 * d).toInt())
            setImageDrawable(MiniPlayerControlIconDrawable("NEXT", d, Color.WHITE))
            setOnClickListener {
                if (allSongsList.isEmpty()) return@setOnClickListener
                currentMiniIndex = (currentMiniIndex + 1) % allSongsList.size
                isMiniPlaying = true
                syncMiniBarWithSong(allSongsList[currentMiniIndex])
                launchFullAudioPlayer(currentMiniIndex, allSongsList)
            }
        }

        miniRow.addView(imgMiniCover)
        miniRow.addView(miniTextCol)
        miniRow.addView(btnMiniPlayPause)
        miniRow.addView(btnMiniNext)
        miniRow.setOnClickListener {
            if (allSongsList.isNotEmpty()) {
                launchFullAudioPlayer(currentMiniIndex.coerceIn(0, allSongsList.size - 1), allSongsList)
            }
        }
        miniPlayerCard.addView(miniRow)
        rootMusicLayout.addView(miniPlayerCard)

        masterFrame.addView(rootMusicLayout)

        updateTabVisuals(d)
        registerMiniReceiver(ctx)
        scanAllSongsInBackground()

        return masterFrame
    }

    private fun btnMiniPlayProxyView() {}

    override fun onResume() {
        super.onResume()
        scanAllSongsInBackground()
        attachInteractiveMascotCompanion()
    }

    private fun attachInteractiveMascotCompanion() {
        val host = mascotHostLayout ?: return
        val ctx = context ?: return
        MascotCompanionHelper.attachInteractiveMascot(
            context = ctx,
            hostContainer = host,
            recyclerView = rvMusicList,
            isVideoScreen = false,
            isDancingProvider = { isMiniPlaying },
            onHoldItemTriggered = { index ->
                if (index in displayedSongsList.indices) {
                    currentMiniIndex = index
                    isMiniPlaying = true
                    syncMiniBarWithSong(displayedSongsList[index])
                    launchFullAudioPlayer(index, displayedSongsList)
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
            MiniPlayerControlIconDrawable(
                if (isMiniPlaying) "PAUSE" else "PLAY",
                d,
                accentColor
            )
        )
    }

    private fun syncMiniBarWithSong(song: AudioModel) {
        if (!isAdded) return
        tvMiniTitle.text = song.title.substringBeforeLast('.')
        tvMiniArtist.text = song.artist.ifEmpty { "Hybrid Player Audio" }
        AudioAdapter.loadAlbumArtOrFallbackInto(requireContext(), song.path, song.title, imgMiniCover)
        updateMiniPlayerPlayIcon()
    }

    private fun createTopTabItem(title: String, index: Int, d: Float): TextView {
        return TextView(requireContext()).apply {
            text = title
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((14 * d).toInt(), (7 * d).toInt(), (14 * d).toInt(), (7 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (6 * d).toInt() }
            setOnClickListener {
                switchMusicTab(index)
            }
        }
    }

    private fun switchMusicTab(targetTab: Int) {
        if (!isAdded) return
        val d = resources.displayMetrics.density
        activeTab = targetTab.coerceIn(0, 4)
        selectedGroupKey = null
        updateTabVisuals(d)
        refreshMusicDisplay()
    }

    private fun updateTabVisuals(d: Float) {
        val tabs = listOf(tabSongs, tabFolders, tabAlbums, tabArtists, tabFavorites)
        tabs.forEachIndexed { idx, tv ->
            val isSel = idx == activeTab
            tv.setTextColor(if (isSel) Color.parseColor("#0A0A10") else Color.WHITE)
            tv.background = GradientDrawable().apply {
                setColor(if (isSel) accentColor else Color.parseColor("#182232"))
                cornerRadius = 18f * d
            }
            if (isSel) {
                topTabsScroll.post {
                    topTabsScroll.smoothScrollTo(tv.left - (40 * d).toInt(), 0)
                }
            }
        }
    }

    private fun attachSwipeToRefreshAndTabs(context: Context) {
        var startY = 0f
        var isPullingDown = false

        rvMusicList.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startY = e.y
                        isPullingDown = !rv.canScrollVertically(-1)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isPullingDown && (e.y - startY) > 90f) {
                            pullRefreshIndicator.visibility = View.VISIBLE
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (pullRefreshIndicator.visibility == View.VISIBLE) {
                            pullRefreshIndicator.visibility = View.GONE
                            scanAllSongsInBackground()
                            Toast.makeText(context, "Music Library Refreshed!", Toast.LENGTH_SHORT).show()
                        }
                        isPullingDown = false
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
                            switchMusicTab(nextTab)
                            return true
                        }
                    } else {
                        if (selectedGroupKey != null) {
                            selectedGroupKey = null
                            refreshMusicDisplay()
                            return true
                        }
                        val prevTab = (activeTab - 1).coerceAtLeast(0)
                        if (prevTab != activeTab) {
                            switchMusicTab(prevTab)
                            return true
                        }
                    }
                }
                return false
            }
        })

        rvMusicList.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                swipeDetector.onTouchEvent(e)
                return false
            }
        })
    }

    private fun scanAllSongsInBackground() {
        val ctx = context ?: return
        thread {
            val list = mutableListOf<AudioModel>()
            try {
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.SIZE,
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
                    val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                    val durCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)

                    while (cursor.moveToNext()) {
                        val id = if (idCol != -1) cursor.getLong(idCol) else 0L
                        val path = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                        if (path.isEmpty() || PermanentDeleteHelper.isMarkedDeleted(ctx, path)) continue

                        val dur = if (durCol != -1) cursor.getLong(durCol) else 0L
                        if (dur < 1000L) continue
                        val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L

                        val title = (if (nameCol != -1) cursor.getString(nameCol) else null)
                            ?: (if (titleCol != -1) cursor.getString(titleCol) else null)
                            ?: File(path).name
                        val artist = (if (artistCol != -1) cursor.getString(artistCol) else null)
                            ?.takeIf { it != "<unknown>" } ?: "Hybrid Player Audio"

                        list.add(
                            AudioModel(
                                id = id,
                                title = title,
                                path = path,
                                artist = artist,
                                duration = dur,
                                size = size
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                allSongsList.clear()
                allSongsList.addAll(list)
                if (allSongsList.isNotEmpty() && tvMiniTitle.text == "Select a song to play") {
                    syncMiniBarWithSong(allSongsList[0])
                }
                refreshMusicDisplay()
            }
        }
    }

    private fun refreshMusicDisplay() {
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
                allSongsList.filter { File(it.path).parentFile?.name.orEmpty().ifEmpty { "Unknown Album" } == albumKey }
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
            4 -> baseList.sortedByDescending { it.size }
            else -> baseList
        }

        if (rvMusicList.adapter !== audioAdapter) {
            rvMusicList.adapter = audioAdapter
        }

        displayedSongsList.clear()
        displayedSongsList.addAll(sorted)
        audioAdapter.notifyDataSetChanged()
        tvSongsTotalCount.text = "${displayedSongsList.size} Songs"

        if (displayedSongsList.isEmpty()) {
            rvMusicList.visibility = View.GONE
            tvEmptyMusic.visibility = View.VISIBLE
        } else {
            tvEmptyMusic.visibility = View.GONE
            rvMusicList.visibility = View.VISIBLE
        }
    }

    private fun showGroupedCardsInRecyclerView(ctx: Context, tabType: Int) {
        val d = ctx.resources.displayMetrics.density
        val groups = when (tabType) {
            1 -> allSongsList.groupBy { File(it.path).parent ?: "Internal Storage" }
            2 -> allSongsList.groupBy { File(it.path).parentFile?.name.orEmpty().ifEmpty { "Singles & Albums" } }
            else -> allSongsList.groupBy { it.artist.ifEmpty { "Unknown Artist" } }
        }.entries.sortedByDescending { it.value.size }

        tvSongsTotalCount.text = "${groups.size} ${if (tabType == 1) "Folders" else if (tabType == 2) "Albums" else "Artists"}"

        if (groups.isEmpty()) {
            rvMusicList.visibility = View.GONE
            tvEmptyMusic.visibility = View.VISIBLE
            return
        }

        tvEmptyMusic.visibility = View.GONE
        rvMusicList.visibility = View.VISIBLE

        rvMusicList.adapter = object : RecyclerView.Adapter<MusicGroupVH>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicGroupVH {
                val card = CardView(ctx).apply {
                    radius = 16f * d
                    cardElevation = 4f * d
                    setCardBackgroundColor(Color.parseColor("#142234"))
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins((5 * d).toInt(), (5 * d).toInt(), (5 * d).toInt(), (5 * d).toInt())
                    }
                }

                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
                }

                val imgThumb = ImageView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams((48 * d).toInt(), (48 * d).toInt()).apply {
                        marginEnd = (14 * d).toInt()
                    }
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
                    text = "❯"
                    setTextColor(accentColor)
                    textSize = 17f
                    typeface = Typeface.DEFAULT_BOLD
                }

                col.addView(tvName)
                col.addView(tvCount)
                row.addView(imgThumb)
                row.addView(col)
                row.addView(tvArrow)
                card.addView(row)
                return MusicGroupVH(card, imgThumb, tvName, tvCount)
            }

            override fun onBindViewHolder(holder: MusicGroupVH, position: Int) {
                InShotScrollAnimator.animateRowOnBind(holder.itemView)
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
                    refreshMusicDisplay()
                }
            }

            override fun getItemCount(): Int = groups.size
        }
    }

    private class MusicGroupVH(
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
        }
        startActivity(intent)
    }

    private fun showMusicSortAndToolsMenu() {
        val ctx = context ?: return
        val items = arrayOf(
            if (sortOrder == 0) "🕒   [Active] Sort: Newest Downloaded Songs" else "🕒   Sort: Newest Downloaded Songs",
            if (sortOrder == 1) "🔤   [Active] Sort: Song Name (A to Z)" else "🔤   Sort: Song Name (A to Z)",
            if (sortOrder == 2) "🎤   [Active] Sort: Artist / Singer Name" else "🎤   Sort: Artist / Singer Name",
            if (sortOrder == 3) "⏱️   [Active] Sort: Longest Duration Songs" else "⏱️   Sort: Longest Duration Songs",
            if (sortOrder == 4) "📦   [Active] Sort: Largest File Size" else "📦   Sort: Largest File Size",
            "🎛️   Open Sound & Pitch Lab (Lofi / Slowed + Reverb)",
            "🎚️   Open Built-In 5-Band Dolby Equalizer"
        )
        VideoFragment.showBrightVideoMenuDialog(ctx, "Sort & Filter Songs", items) { which ->
            when (which) {
                0, 1, 2, 3, 4 -> {
                    sortOrder = which
                    refreshMusicDisplay()
                }
                5 -> AudioPitchHelper.showSoundAndPitchLabDialog(ctx, null)
                6 -> EqualizerHelper.showEqualizerDialog(ctx, 0)
            }
        }
    }

    private class MiniPlayerControlIconDrawable(
        private val type: String,
        private val d: Float,
        private val accentColor: Int
    ) : Drawable() {

        private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = accentColor
        }

        private val iconDarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#0A0A10")
        }

        private val nextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val r = b.width().coerceAtMost(b.height()) * 0.45f

            when (type) {
                "PLAY" -> {
                    canvas.drawCircle(cx, cy, r, circlePaint)
                    val path = Path().apply {
                        moveTo(cx - r * 0.25f, cy - r * 0.38f)
                        lineTo(cx + r * 0.42f, cy)
                        lineTo(cx - r * 0.25f, cy + r * 0.38f)
                        close()
                    }
                    canvas.drawPath(path, iconDarkPaint)
                }
                "PAUSE" -> {
                    canvas.drawCircle(cx, cy, r, circlePaint)
                    val barW = r * 0.22f
                    val barH = r * 0.68f
                    canvas.drawRoundRect(
                        RectF(cx - r * 0.34f, cy - barH / 2f, cx - r * 0.34f + barW, cy + barH / 2f),
                        2f * d,
                        2f * d,
                        iconDarkPaint
                    )
                    canvas.drawRoundRect(
                        RectF(cx + r * 0.12f, cy - barH / 2f, cx + r * 0.12f + barW, cy + barH / 2f),
                        2f * d,
                        2f * d,
                        iconDarkPaint
                    )
                }
                "NEXT" -> {
                    val path = Path().apply {
                        moveTo(cx - r * 0.45f, cy - r * 0.40f)
                        lineTo(cx + r * 0.18f, cy)
                        lineTo(cx - r * 0.45f, cy + r * 0.40f)
                        close()
                    }
                    canvas.drawPath(path, nextPaint)
                    canvas.drawRoundRect(
                        RectF(cx + r * 0.22f, cy - r * 0.40f, cx + r * 0.38f, cy + r * 0.40f),
                        2f * d,
                        2f * d,
                        nextPaint
                    )
                }
            }
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
