package com.app.mediaplayer

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
class ShortsPlayerActivity : AppCompatActivity() {

    private lateinit var rvShortsPager: RecyclerView
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var bottomSeekBarStrip: LinearLayout
    private lateinit var sbShortsProgress: SeekBar
    private lateinit var tvShortsTimeStamp: TextView

    private var shortsPlayer: ExoPlayer? = null

    private val shortsPaths = mutableListOf<String>()
    private val shortsTitles = mutableListOf<String>()
    private var currentActivePos = -1
    private var isAutoScrollEnabled = true
    private var currentShortsSpeed = 1.0f

    private val progressHandler = Handler(Looper.getMainLooper())
    private val updateShortsBarRunnable = object : Runnable {
        override fun run() {
            try {
                shortsPlayer?.let { p ->
                    val cur = p.currentPosition.coerceAtLeast(0L)
                    val dur = p.duration.coerceAtLeast(1L)
                    sbShortsProgress.max = dur.toInt()
                    sbShortsProgress.progress = cur.toInt()
                    tvShortsTimeStamp.text = "${formatShortTime(cur)} / ${formatShortTime(dur)}"
                }
            } catch (_: Exception) {
            }
            progressHandler.postDelayed(this, 200L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val d = resources.displayMetrics.density
        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
        }

        rvShortsPager = RecyclerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        rvShortsPager.layoutManager = layoutManager
        PagerSnapHelper().attachToRecyclerView(rvShortsPager)
        root.addView(rvShortsPager)

        // Crisp Top Bar with Clean Back Arrow Icon
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * d).toInt(), (16 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        }

        val btnBack = TextView(this).apply {
            text = "←"
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((40 * d).toInt(), (40 * d).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#880A0A10"))
                setStroke((1.3f * d).toInt(), Color.parseColor("#00FFFF"))
            }
            setOnClickListener { finish() }
        }

        val tvHeader = TextView(this).apply {
            text = "Shorts & Status Reels"
            setTextColor(Color.WHITE)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((12 * d).toInt(), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        topBar.addView(btnBack)
        topBar.addView(tvHeader)
        root.addView(topBar)

        // Glowing Bottom Seekbar Strip (Shines clearly at the bottom of the screen)
        bottomSeekBarStrip = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((14 * d).toInt(), (6 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(Color.parseColor("#CC0A0A10"), Color.TRANSPARENT)
            )
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        }

        tvShortsTimeStamp = TextView(this).apply {
            text = "00:00 / 00:00"
            setTextColor(Color.parseColor("#00FFFF"))
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((4 * d).toInt(), 0, 0, (2 * d).toInt())
        }

        sbShortsProgress = SeekBar(this).apply {
            max = 1000
            progressDrawable.setColorFilter(Color.parseColor("#00FFFF"), PorterDuff.Mode.SRC_IN)
            thumb.setColorFilter(Color.parseColor("#00FFFF"), PorterDuff.Mode.SRC_IN)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) {
                        shortsPlayer?.seekTo(p.toLong())
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }

        bottomSeekBarStrip.addView(tvShortsTimeStamp)
        bottomSeekBarStrip.addView(sbShortsProgress)
        root.addView(bottomSeekBarStrip)

        setContentView(root)

        loadShortsData()
        initSharedShortsExoPlayer()
        setupShortsPagerAdapter()
        progressHandler.post(updateShortsBarRunnable)
    }

    private fun loadShortsData() {
        val passedList = intent.getStringArrayListExtra("SHORTS_LIST")
        val startIndex = intent.getIntExtra("START_INDEX", 0)

        if (!passedList.isNullOrEmpty()) {
            shortsPaths.addAll(passedList)
            passedList.forEach { shortsTitles.add(File(it).name) }
        } else {
            val all = DeepMediaScanner.scanAllVideosEverywhere(this)
            val filtered = DeepMediaScanner.filterShortsAndStatusVideos(all)
            val targetList = if (filtered.isNotEmpty()) filtered else all
            targetList.forEach {
                shortsPaths.add(it.path)
                shortsTitles.add(it.title)
            }
        }

        currentActivePos = startIndex.coerceIn(0, (shortsPaths.size - 1).coerceAtLeast(0))
        isAutoScrollEnabled = getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
            .getBoolean("shorts_auto_scroll", true)
    }

    private fun initSharedShortsExoPlayer() {
        shortsPlayer = ExoPlayer.Builder(this).build().apply {
            repeatMode = if (isAutoScrollEnabled) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED && isAutoScrollEnabled) {
                        val nextPos = currentActivePos + 1
                        if (nextPos in shortsPaths.indices) {
                            rvShortsPager.smoothScrollToPosition(nextPos)
                        }
                    }
                }
            })
        }
    }

    private fun setupShortsPagerAdapter() {
        val d = resources.displayMetrics.density
        val prefs = getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)

        rvShortsPager.adapter = object : RecyclerView.Adapter<ShortPageVH>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShortPageVH {
                val pageFrame = FrameLayout(this@ShortsPlayerActivity).apply {
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(Color.BLACK)
                }

                val pv = PlayerView(this@ShortsPlayerActivity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
                pageFrame.addView(pv)

                val tvCenterPauseBadge = TextView(this@ShortsPlayerActivity).apply {
                    text = "▶"
                    setTextColor(Color.parseColor("#00FFFF"))
                    textSize = 36f
                    gravity = Gravity.CENTER
                    visibility = View.GONE
                    layoutParams = FrameLayout.LayoutParams(
                        (76 * d).toInt(),
                        (76 * d).toInt(),
                        Gravity.CENTER
                    )
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#990A0A10"))
                        setStroke((2f * d).toInt(), Color.parseColor("#00FFFF"))
                    }
                }
                pageFrame.addView(tvCenterPauseBadge)

                // Right Side Vertical Actions Column (Heart Like Icon, Share, Speed, Menu)
                val rightCol = LinearLayout(this@ShortsPlayerActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding((10 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (110 * d).toInt())
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM or Gravity.END
                    )
                }

                val btnLike = createCircleActionButton("🤍\nLike", d)
                val btnShare = createCircleActionButton("📤\nShare", d)
                val btnSpeed = createCircleActionButton("⚡\n1.0x", d)
                val btnMore = createCircleActionButton("⋮\nMenu", d)

                rightCol.addView(btnLike)
                rightCol.addView(btnShare)
                rightCol.addView(btnSpeed)
                rightCol.addView(btnMore)
                pageFrame.addView(rightCol)

                val tvTitle = TextView(this@ShortsPlayerActivity).apply {
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 2
                    setPadding((16 * d).toInt(), (10 * d).toInt(), (88 * d).toInt(), (56 * d).toInt())
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM or Gravity.START
                    )
                }
                pageFrame.addView(tvTitle)

                return ShortPageVH(pageFrame, pv, tvCenterPauseBadge, tvTitle, btnLike, btnShare, btnSpeed, btnMore)
            }

            override fun onBindViewHolder(holder: ShortPageVH, position: Int) {
                val path = shortsPaths[position]
                val title = shortsTitles.getOrElse(position) { File(path).name }
                holder.tvTitle.text = title
                holder.tvCenterPauseBadge.visibility = View.GONE

                val isFav = prefs.getBoolean("fav_$path", false)
                updateHeartButtonVisual(holder.btnLike, isFav)
                holder.btnSpeed.text = String.format(Locale.US, "⚡\n%.1fx", currentShortsSpeed)

                holder.pv.setOnClickListener {
                    shortsPlayer?.let { p ->
                        if (p.isPlaying) {
                            p.pause()
                            holder.tvCenterPauseBadge.visibility = View.VISIBLE
                        } else {
                            p.play()
                            holder.tvCenterPauseBadge.visibility = View.GONE
                        }
                    }
                }

                holder.tvCenterPauseBadge.setOnClickListener {
                    shortsPlayer?.play()
                    holder.tvCenterPauseBadge.visibility = View.GONE
                }

                holder.btnLike.setOnClickListener {
                    val curFav = prefs.getBoolean("fav_$path", false)
                    val newFav = !curFav
                    prefs.edit().putBoolean("fav_$path", newFav).apply()
                    updateHeartButtonVisual(holder.btnLike, newFav)
                    Toast.makeText(
                        this@ShortsPlayerActivity,
                        if (newFav) "Added to Favorites ❤️" else "Removed from Favorites",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                holder.btnShare.setOnClickListener {
                    showMultiPlatformShareMenu(path, title)
                }

                holder.btnSpeed.setOnClickListener {
                    val speeds = floatArrayOf(0.5f, 1.0f, 1.5f, 2.0f)
                    val nextIdx = (speeds.indexOfFirst { it == currentShortsSpeed } + 1) % speeds.size
                    currentShortsSpeed = speeds[nextIdx]
                    shortsPlayer?.playbackParameters = PlaybackParameters(currentShortsSpeed)
                    holder.btnSpeed.text = String.format(Locale.US, "⚡\n%.1fx", currentShortsSpeed)
                }

                holder.btnMore.setOnClickListener {
                    showShortsThreeDotsMenu(path, title, holder.adapterPosition)
                }
            }

            override fun getItemCount(): Int = shortsPaths.size
        }

        rvShortsPager.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val snapPos = layoutManager.findFirstCompletelyVisibleItemPosition()
                        .takeIf { it != RecyclerView.NO_POSITION }
                        ?: layoutManager.findFirstVisibleItemPosition()
                    if (snapPos != RecyclerView.NO_POSITION && snapPos != currentActivePos) {
                        playShortAtPosition(snapPos)
                    }
                }
            }
        })

        rvShortsPager.post {
            if (shortsPaths.isNotEmpty()) {
                rvShortsPager.scrollToPosition(currentActivePos)
                playShortAtPosition(currentActivePos)
            }
        }
    }

    private fun updateHeartButtonVisual(btn: TextView, isLiked: Boolean) {
        if (isLiked) {
            btn.text = "❤️\nLiked"
            btn.setTextColor(Color.parseColor("#FF4081"))
        } else {
            btn.text = "🤍\nLike"
            btn.setTextColor(Color.WHITE)
        }
    }

    private fun playShortAtPosition(pos: Int) {
        if (pos !in shortsPaths.indices) return
        currentActivePos = pos
        val path = shortsPaths[pos]

        for (i in 0 until rvShortsPager.childCount) {
            val child = rvShortsPager.getChildAt(i)
            val vh = rvShortsPager.getChildViewHolder(child) as? ShortPageVH
            vh?.pv?.player = null
            vh?.tvCenterPauseBadge?.visibility = View.GONE
        }

        val activeVh = rvShortsPager.findViewHolderForAdapterPosition(pos) as? ShortPageVH
        val player = shortsPlayer ?: return
        activeVh?.pv?.player = player

        val uri = if (path.startsWith("content://")) Uri.parse(path) else Uri.fromFile(File(path))
        player.setMediaItem(MediaItem.fromUri(uri))
        player.repeatMode = if (isAutoScrollEnabled) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
        player.playbackParameters = PlaybackParameters(currentShortsSpeed)
        player.prepare()
        player.playWhenReady = true
    }

    private fun showMultiPlatformShareMenu(path: String, title: String) {
        val options = arrayOf(
            "💚   Share to WhatsApp Status / Chat",
            "📸   Share to Instagram Reels / Story",
            "✈️   Share to Telegram",
            "⚡   Send Phone-to-Phone (Wi-Fi Direct Fast Transfer)",
            "📤   Share to Other Apps (System Chooser)"
        )
        VideoFragment.showBrightVideoMenuDialog(this, "Multi-Share Short Video", options) { which ->
            val fileUri = if (path.startsWith("content://")) Uri.parse(path) else Uri.parse(path)
            when (which) {
                0 -> shareToSpecificPackage(fileUri, "com.whatsapp")
                1 -> shareToSpecificPackage(fileUri, "com.instagram.android")
                2 -> shareToSpecificPackage(fileUri, "org.telegram.messenger")
                3 -> {
                    try {
                        startActivity(Intent(this, FileTransferActivity::class.java).apply {
                            putExtra("SEND_FILE_PATH", path)
                        })
                    } catch (_: Exception) {
                        shareGeneric(fileUri, title)
                    }
                }
                4 -> shareGeneric(fileUri, title)
            }
        }
    }

    private fun shareToSpecificPackage(uri: Uri, pkg: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                setPackage(pkg)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (_: Exception) {
            shareGeneric(uri, "Share Video")
        }
    }

    private fun shareGeneric(uri: Uri, title: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share $title"))
        } catch (_: Exception) {
            Toast.makeText(this, "Unable to share video", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showShortsThreeDotsMenu(path: String, title: String, position: Int) {
        val prefs = getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        val options = arrayOf(
            if (isAutoScrollEnabled) "🔄   [Active] Auto-Scroll Next Short: ON" else "🔂   Auto-Scroll Next Short: OFF (Loop 1)",
            "💾   Save Status / Short to Gallery Permanently",
            "🎬   Open in Full 8K Landscape Player",
            "🎵   Convert Short Video to MP3 Ringtone",
            "🗑️   Delete Short Video Permanently"
        )
        VideoFragment.showBrightVideoMenuDialog(this, title, options) { which ->
            when (which) {
                0 -> {
                    isAutoScrollEnabled = !isAutoScrollEnabled
                    prefs.edit().putBoolean("shorts_auto_scroll", isAutoScrollEnabled).apply()
                    shortsPlayer?.repeatMode =
                        if (isAutoScrollEnabled) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
                    Toast.makeText(
                        this,
                        if (isAutoScrollEnabled) "Auto-Scroll Enabled" else "Loop Current Short Enabled",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                1 -> StatusSaverHelper.saveVideoStatusToGallery(this, path)
                2 -> {
                    shortsPlayer?.pause()
                    startActivity(Intent(this, PlayerActivity::class.java).apply {
                        putExtra("VIDEO_PATH", path)
                        putExtra("VIDEO_TITLE", title)
                    })
                }
                3 -> {
                    startActivity(Intent(this, Mp3ConverterActivity::class.java).apply {
                        putExtra("VIDEO_PATH", path)
                        putExtra("VIDEO_TITLE", title)
                    })
                }
                4 -> {
                    PermanentDeleteHelper.confirmAndDeletePermanently(
                        context = this,
                        title = title,
                        path = path,
                        isVideo = true
                    ) {
                        DeepMediaScanner.invalidateCache()
                        if (position in shortsPaths.indices) {
                            shortsPaths.removeAt(position)
                            shortsTitles.removeAt(position)
                            rvShortsPager.adapter?.notifyItemRemoved(position)
                            if (shortsPaths.isEmpty()) finish()
                            else playShortAtPosition(position.coerceAtMost(shortsPaths.size - 1))
                        }
                    }
                }
            }
        }
    }

    private fun createCircleActionButton(label: String, d: Float): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                (58 * d).toInt(),
                (54 * d).toInt()
            ).apply { bottomMargin = (14 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#990A0A10"))
                cornerRadius = 27f * d
                setStroke((1.3f * d).toInt(), Color.parseColor("#00FFFF"))
            }
        }
    }

    private fun formatShortTime(millis: Long): String {
        val safe = millis.coerceAtLeast(0L)
        val mins = TimeUnit.MILLISECONDS.toMinutes(safe)
        val secs = TimeUnit.MILLISECONDS.toSeconds(safe) % 60
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    private class ShortPageVH(
        view: View,
        val pv: PlayerView,
        val tvCenterPauseBadge: TextView,
        val tvTitle: TextView,
        val btnLike: TextView,
        val btnShare: TextView,
        val btnSpeed: TextView,
        val btnMore: TextView
    ) : RecyclerView.ViewHolder(view)

    override fun onPause() {
        super.onPause()
        shortsPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        progressHandler.removeCallbacks(updateShortsBarRunnable)
        shortsPlayer?.release()
        shortsPlayer = null
    }
}
