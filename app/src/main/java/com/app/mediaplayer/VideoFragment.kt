package com.app.mediaplayer

import android.Manifest
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File
import java.util.ArrayList
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class VideoFragment : Fragment() {

    private lateinit var rootVideoFragment: LinearLayout
    private lateinit var rvMainVideos: RecyclerView
    private lateinit var rvFolders: RecyclerView
    private lateinit var llPlaylistsEmpty: LinearLayout

    private lateinit var tvTabVideo: TextView
    private lateinit var tvTabFolder: TextView
    private lateinit var tvTabPlaylist: TextView
    private lateinit var btnVideoMoreMenu: ImageView

    private lateinit var llHistoryContainer: LinearLayout
    private lateinit var rvHistory: RecyclerView
    private lateinit var tvClearHistory: TextView

    private lateinit var videoAdapter: VideoAdapter
    private lateinit var folderAdapter: FolderAdapter

    private val allVideosList = mutableListOf<VideoModel>()
    private val displayedVideosList = mutableListOf<VideoModel>()
    private val allFoldersList = mutableListOf<FolderModel>()

    private var isScanning = false
    private var currentTab = 0
    private var sortMode = 0
    private var isGridMode = false
    private var accentColorInt = Color.parseColor("#00FFFF")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_video, container, false)

        rootVideoFragment = view.findViewById(R.id.rootVideoFragment)
        rvMainVideos = view.findViewById(R.id.rvMainVideos)
        rvFolders = view.findViewById(R.id.rvFolders)
        llPlaylistsEmpty = view.findViewById(R.id.llPlaylistsEmpty)

        tvTabVideo = view.findViewById(R.id.tvTabVideo)
        tvTabFolder = view.findViewById(R.id.tvTabFolder)
        tvTabPlaylist = view.findViewById(R.id.tvTabPlaylist)
        btnVideoMoreMenu = view.findViewById(R.id.btnVideoMoreMenu)

        llHistoryContainer = view.findViewById(R.id.llHistoryContainer)
        rvHistory = view.findViewById(R.id.rvHistory)
        tvClearHistory = view.findViewById(R.id.tvClearHistory)

        val prefs = requireContext().getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        isGridMode = prefs.getBoolean("video_grid_mode", false)
        sortMode = prefs.getInt("video_sort_mode", 0)

        applyLayoutManager()
        rvFolders.layoutManager = LinearLayoutManager(requireContext())
        rvHistory.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        videoAdapter = VideoAdapter(requireContext(), displayedVideosList)
        rvMainVideos.adapter = videoAdapter

        folderAdapter = FolderAdapter(requireContext(), allFoldersList)
        rvFolders.adapter = folderAdapter

        applyGlobalThemeColors()
        setupClicks()
        checkPermissionAndLoad()

        return view
    }

    override fun onResume() {
        super.onResume()
        applyGlobalThemeColors()
        loadMediaFast()
    }

    private fun applyGlobalThemeColors() {
        val ctx = context ?: return
        val themePrefs = ctx.getSharedPreferences("MAX_THEME_PREFS", Context.MODE_PRIVATE)
        val bgHex = themePrefs.getString("app_bg_color", "#0A0A10") ?: "#0A0A10"
        val accentHex = themePrefs.getString("app_accent_color", "#00FFFF") ?: "#00FFFF"
        try {
            rootVideoFragment.setBackgroundColor(Color.parseColor(bgHex))
            accentColorInt = Color.parseColor(accentHex)
        } catch (e: Exception) {
            accentColorInt = Color.parseColor("#00FFFF")
        }
        tvClearHistory.setTextColor(accentColorInt)
        updateTabColors()
    }

    private fun updateTabColors() {
        val inactiveColor = Color.parseColor("#B0B0C0")
        tvTabVideo.setTextColor(if (currentTab == 0) accentColorInt else inactiveColor)
        tvTabFolder.setTextColor(if (currentTab == 1) accentColorInt else inactiveColor)
        tvTabPlaylist.setTextColor(if (currentTab == 2) accentColorInt else inactiveColor)
    }

    private fun applyLayoutManager() {
        val ctx = context ?: return
        if (isGridMode) {
            rvMainVideos.layoutManager = GridLayoutManager(ctx, 2)
        } else {
            rvMainVideos.layoutManager = LinearLayoutManager(ctx)
        }
    }

    private fun checkPermissionAndLoad() {
        val ctx = context ?: return
        val hasPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasPerm) {
            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            requestPermissions(perms, 2001)
        }
        loadMediaFast()
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        loadMediaFast()
    }

    private fun setupClicks() {
        tvTabVideo.setOnClickListener {
            currentTab = 0
            updateTabColors()
            refreshUI()
        }

        tvTabFolder.setOnClickListener {
            currentTab = 1
            updateTabColors()
            refreshUI()
        }

        tvTabPlaylist.setOnClickListener {
            currentTab = 2
            updateTabColors()
            refreshUI()
        }

        tvClearHistory.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            val prefs = ctx.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            for (video in allVideosList) {
                editor.remove("history_time_${video.path}")
                editor.remove("watched_pos_${video.path}")
            }
            editor.apply()
            llHistoryContainer.visibility = View.GONE
            videoAdapter.notifyDataSetChanged()
            Toast.makeText(ctx, "History Cleared", Toast.LENGTH_SHORT).show()
        }

        btnVideoMoreMenu.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            val prefs = ctx.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
            val menuItems = arrayOf(
                if (isGridMode) "Switch to List View" else "Switch to Grid View",
                if (sortMode == 0) "[Active] Sort: Newest First" else "Sort: Newest First",
                if (sortMode == 1) "[Active] Sort: Name (A to Z)" else "Sort: Name (A to Z)",
                if (sortMode == 2) "[Active] Sort: Largest Size (HD / 4K)" else "Sort: Largest Size (HD / 4K)",
                if (sortMode == 3) "[Active] Sort: Longest Duration" else "Sort: Longest Duration",
                "Refresh Video Library"
            )

            showBrightVideoMenuDialog(ctx, "Video Library Options", menuItems) { which ->
                when (which) {
                    0 -> {
                        isGridMode = !isGridMode
                        prefs.edit().putBoolean("video_grid_mode", isGridMode).apply()
                        applyLayoutManager()
                        videoAdapter.notifyDataSetChanged()
                    }
                    1, 2, 3, 4 -> {
                        sortMode = which - 1
                        prefs.edit().putInt("video_sort_mode", sortMode).apply()
                        refreshUI()
                    }
                    5 -> {
                        loadMediaFast()
                        Toast.makeText(ctx, "Refreshing...", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun loadMediaFast() {
        if (isScanning) return
        isScanning = true

        thread {
            try {
                val videos = mutableListOf<VideoModel>()
                val folders = mutableListOf<FolderModel>()
                queryMediaStoreInstant(videos, folders)

                activity?.runOnUiThread {
                    isScanning = false
                    if (!isAdded || context == null) return@runOnUiThread

                    allVideosList.clear()
                    allVideosList.addAll(videos)

                    allFoldersList.clear()
                    allFoldersList.addAll(folders)

                    refreshHistorySection()
                    refreshUI()
                }
            } catch (e: Exception) {
                isScanning = false
                e.printStackTrace()
            }
        }
    }

    private fun refreshHistorySection() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        val watchedVideos = allVideosList.filter {
            prefs.getLong("history_time_${it.path}", 0L) > 0L
        }.sortedByDescending {
            prefs.getLong("history_time_${it.path}", 0L)
        }.take(20)

        if (watchedVideos.isEmpty()) {
            llHistoryContainer.visibility = View.GONE
        } else {
            llHistoryContainer.visibility = View.VISIBLE
            rvHistory.adapter = HistoryStoryAdapter(ctx, watchedVideos)
        }
    }

    private inner class HistoryStoryAdapter(
        private val ctx: Context,
        private val items: List<VideoModel>
    ) : RecyclerView.Adapter<HistoryStoryAdapter.HistoryVH>() {

        inner class HistoryVH(
            view: View,
            val imgThumb: ImageView,
            val tvDur: TextView,
            val pbProgress: ProgressBar,
            val tvName: TextView
        ) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryVH {
            val d = ctx.resources.displayMetrics.density
            val root = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = RecyclerView.LayoutParams(
                    (144 * d).toInt(),
                    ViewGroup.LayoutParams.MATCH_PARENT
                ).apply {
                    marginEnd = (10 * d).toInt()
                }
            }

            val card = CardView(ctx).apply {
                radius = 10f * d
                cardElevation = 3f * d
                setCardBackgroundColor(Color.parseColor("#1C1C2A"))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (82 * d).toInt()
                )
            }

            val frame = FrameLayout(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            val img = ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            val tvDur = TextView(ctx).apply {
                setTextColor(Color.WHITE)
                textSize = 9.5f
                typeface = Typeface.DEFAULT_BOLD
                setBackgroundColor(Color.parseColor("#CC000000"))
                setPadding((5 * d).toInt(), (1 * d).toInt(), (5 * d).toInt(), (1 * d).toInt())
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.END
                ).apply {
                    rightMargin = (4 * d).toInt()
                    bottomMargin = (6 * d).toInt()
                }
            }

            val pb = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 1000
                progressDrawable.setColorFilter(
                    accentColorInt,
                    PorterDuff.Mode.SRC_IN
                )
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (3.5f * d).toInt(),
                    Gravity.BOTTOM
                )
            }

            frame.addView(img)
            frame.addView(tvDur)
            frame.addView(pb)
            card.addView(frame)

            val tvName = TextView(ctx).apply {
                setTextColor(Color.WHITE)
                textSize = 11.5f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding((2 * d).toInt(), (4 * d).toInt(), (2 * d).toInt(), 0)
            }

            root.addView(card)
            root.addView(tvName)
            return HistoryVH(root, img, tvDur, pb, tvName)
        }

        override fun onBindViewHolder(holder: HistoryVH, position: Int) {
            val video = items[position]
            holder.tvName.text = video.title

            val prefs = ctx.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
            val watchedPos = prefs.getLong("watched_pos_${video.path}", 0L)
            val totalDur = video.duration.coerceAtLeast(1L)
            val ratio = if (watchedPos > 0L) {
                ((watchedPos.toFloat() / totalDur.toFloat()) * 1000).toInt().coerceIn(40, 1000)
            } else {
                1000
            }
            holder.pbProgress.progress = ratio

            if (watchedPos > 0L && watchedPos < totalDur) {
                holder.tvDur.text = "${formatDuration(watchedPos)} / ${formatDuration(video.duration)}"
            } else {
                holder.tvDur.text = formatDuration(video.duration)
            }

            val target: Any = if (video.path.startsWith("content://")) {
                Uri.parse(video.path)
            } else {
                File(video.path)
            }

            Glide.with(ctx)
                .load(target)
                .placeholder(android.R.color.darker_gray)
                .centerCrop()
                .into(holder.imgThumb)

            holder.itemView.setOnClickListener {
                val allPaths = ArrayList<String>(allVideosList.size)
                allVideosList.forEach { allPaths.add(it.path) }
                val intent = Intent(ctx, PlayerActivity::class.java).apply {
                    putExtra("VIDEO_PATH", video.path)
                    putExtra("VIDEO_TITLE", video.title)
                    putStringArrayListExtra("VIDEO_LIST", allPaths)
                }
                ctx.startActivity(intent)
            }
        }

        override fun getItemCount(): Int = items.size
    }

    private fun refreshUI() {
        if (!isAdded || context == null) return
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)

        when (currentTab) {
            0 -> {
                val sorted = when (sortMode) {
                    1 -> allVideosList.sortedBy { it.title.lowercase(Locale.getDefault()) }
                    2 -> allVideosList.sortedByDescending { it.size }
                    3 -> allVideosList.sortedByDescending { it.duration }
                    else -> allVideosList
                }
                displayedVideosList.clear()
                displayedVideosList.addAll(sorted)
                videoAdapter.notifyDataSetChanged()

                rvFolders.visibility = View.GONE
                if (displayedVideosList.isEmpty()) {
                    rvMainVideos.visibility = View.GONE
                    llPlaylistsEmpty.visibility = View.VISIBLE
                } else {
                    llPlaylistsEmpty.visibility = View.GONE
                    rvMainVideos.visibility = View.VISIBLE
                }
            }
            1 -> {
                folderAdapter.notifyDataSetChanged()
                rvMainVideos.visibility = View.GONE
                if (allFoldersList.isEmpty()) {
                    rvFolders.visibility = View.GONE
                    llPlaylistsEmpty.visibility = View.VISIBLE
                } else {
                    llPlaylistsEmpty.visibility = View.GONE
                    rvFolders.visibility = View.VISIBLE
                }
            }
            2 -> {
                val favVideos = allVideosList.filter { video ->
                    prefs.getBoolean("fav_${video.path}", false) ||
                        prefs.getLong("bookmark_${video.path}", -1L) != -1L
                }
                displayedVideosList.clear()
                displayedVideosList.addAll(favVideos)
                videoAdapter.notifyDataSetChanged()

                rvFolders.visibility = View.GONE
                if (displayedVideosList.isEmpty()) {
                    rvMainVideos.visibility = View.GONE
                    llPlaylistsEmpty.visibility = View.VISIBLE
                } else {
                    llPlaylistsEmpty.visibility = View.GONE
                    rvMainVideos.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun queryMediaStoreInstant(
        videoOutput: MutableList<VideoModel>,
        folderOutput: MutableList<FolderModel>
    ) {
        val ctx = context ?: return
        val resolver = ctx.contentResolver ?: return
        val prefsEditor = ctx.getSharedPreferences("MAX_VIDEO_RES_PREFS", Context.MODE_PRIVATE).edit()
        val folderMap = mutableMapOf<String, FolderModel>()
        val seenPaths = HashSet<String>()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            resolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                val bucketIdCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = if (idCol != -1) cursor.getLong(idCol) else 0L
                    val rawPath = if (dataCol != -1) cursor.getString(dataCol) else null
                    val path = if (!rawPath.isNullOrEmpty()) {
                        rawPath
                    } else {
                        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id).toString()
                    }

                    val title = if (nameCol != -1) {
                        cursor.getString(nameCol) ?: File(path).name
                    } else {
                        File(path).name
                    }
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L
                    val duration = if (durCol != -1) cursor.getLong(durCol).coerceAtLeast(0L) else 0L
                    val width = if (widthCol != -1) cursor.getInt(widthCol) else 0
                    val height = if (heightCol != -1) cursor.getInt(heightCol) else 0
                    val bucketId = if (bucketIdCol != -1) cursor.getString(bucketIdCol) ?: "0" else "0"
                    val bucketName = if (bucketNameCol != -1) {
                        cursor.getString(bucketNameCol) ?: "Videos"
                    } else {
                        File(path).parentFile?.name ?: "Videos"
                    }

                    if (seenPaths.add(path)) {
                        val qualityBadge = computeResolutionLabel(width, height, title, size, duration)
                        prefsEditor.putString("res_$path", qualityBadge)

                        videoOutput.add(VideoModel(id, title, path, size, duration))
                        if (folderMap.containsKey(bucketId)) {
                            folderMap[bucketId]!!.videoCount++
                        } else {
                            folderMap[bucketId] = FolderModel(bucketId, bucketName, path, 1)
                        }
                    }
                }
            }
            prefsEditor.apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        folderOutput.addAll(folderMap.values.sortedBy { it.folderName.lowercase(Locale.getDefault()) })
    }

    private fun formatDuration(durationMillis: Long): String {
        val safe = durationMillis.coerceAtLeast(0L)
        val hours = TimeUnit.MILLISECONDS.toHours(safe)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(safe) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(safe) % 60
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    companion object {
        fun computeResolutionLabel(
            width: Int,
            height: Int,
            title: String,
            sizeBytes: Long,
            durationMs: Long
        ): String {
            val maxDim = width.coerceAtLeast(height)
            val minDim = width.coerceAtMost(height)
            if (maxDim > 0 && minDim > 0) {
                return when {
                    maxDim >= 7600 || minDim >= 4300 -> "8K"
                    maxDim >= 3800 || minDim >= 2100 -> "4K"
                    maxDim >= 2500 || minDim >= 1400 -> "2K"
                    maxDim >= 1900 || minDim >= 1000 -> "1080p"
                    maxDim >= 1200 || minDim >= 700 -> "720p"
                    maxDim >= 800 || minDim >= 460 -> "480p"
                    else -> "360p"
                }
            }
            val upper = title.uppercase(Locale.US)
            return when {
                upper.contains("8K") || upper.contains("4320P") -> "8K"
                upper.contains("4K") || upper.contains("2160P") -> "4K"
                upper.contains("2K") || upper.contains("1440P") -> "2K"
                upper.contains("1080P") || upper.contains("FHD") -> "1080p"
                upper.contains("720P") || upper.contains("HD") -> "720p"
                upper.contains("480P") -> "480p"
                upper.contains("360P") -> "360p"
                else -> {
                    val durSec = (durationMs / 1000L).coerceAtLeast(1L)
                    val kbps = (sizeBytes * 8L) / (durSec * 1024L)
                    when {
                        kbps > 12000 -> "4K"
                        kbps > 6500 -> "2K"
                        kbps > 2500 -> "1080p"
                        kbps > 1000 -> "720p"
                        kbps > 500 -> "480p"
                        else -> "360p"
                    }
                }
            }
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
                    setColor(Color.parseColor("#181828"))
                    cornerRadius = 22f * d
                    setStroke((1.5f * d).toInt(), Color.parseColor("#00FFFF"))
                }
            }

            val tvHeader = TextView(context).apply {
                text = headerTitle
                setTextColor(Color.parseColor("#00FFFF"))
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

            val scroll = ScrollView(context)
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
}
