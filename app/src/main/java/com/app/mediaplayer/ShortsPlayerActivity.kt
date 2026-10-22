package com.app.mediaplayer

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
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

@OptIn(UnstableApi::class)
class ShortsPlayerActivity : AppCompatActivity() {

    private lateinit var rvShortsPager: RecyclerView
    private lateinit var layoutManager: LinearLayoutManager
    private var shortsPlayer: ExoPlayer? = null

    private val shortsPaths = mutableListOf<String>()
    private val shortsTitles = mutableListOf<String>()
    private var currentActivePos = -1
    private var isAutoScrollEnabled = true
    private var currentShortsSpeed = 1.0f

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

        // Top Back & Mode Badge Row
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

        val btnBack = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((38 * d).toInt(), (38 * d).toInt())
            setPadding((8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
            setImageResource(android.R.drawable.ic_menu_revert)
            setColorFilter(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#66000000"))
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

        setContentView(root)

        loadShortsData()
        initSharedShortsExoPlayer()
        setupShortsPagerAdapter()
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

                // Right Side Vertical Actions Column (Like, Share, Speed, 3-Dots)
                val rightCol = LinearLayout(this@ShortsPlayerActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding((10 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (90 * d).toInt())
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM or Gravity.END
                    )
                }

                val btnLike = createCircleActionButton("Like", d)
                val btnShare = createCircleActionButton("Share", d)
                val btnSpeed = createCircleActionButton("1.0x", d)
                val btnMore = createCircleActionButton("Menu", d)

                rightCol.addView(btnLike)
                rightCol.addView(btnShare)
                rightCol.addView(btnSpeed)
                rightCol.addView(btnMore)
                pageFrame.addView(rightCol)

                // Bottom Left Title & Status Info
                val tvTitle = TextView(this@ShortsPlayerActivity).apply {
                    setTextColor(Color.WHITE)
                    textSize = 14.5f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 2
                    setPadding((16 * d).toInt(), (10 * d).toInt(), (84 * d).toInt(), (28 * d).toInt())
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM or Gravity.START
                    )
                }
                pageFrame.addView(tvTitle)

                return ShortPageVH(pageFrame, pv, tvTitle, btnLike, btnShare, btnSpeed, btnMore)
            }

            override fun onBindViewHolder(holder: ShortPageVH, position: Int) {
                val path = shortsPaths[position]
                val title = shortsTitles.getOrElse(position) { File(path).name }
                holder.tvTitle.text = title

                val isFav = prefs.getBoolean("fav_$path", false)
                holder.btnLike.text = if (isFav) "Liked *" else "Like"
                holder.btnLike.setTextColor(if (isFav) Color.parseColor("#FF4081") else Color.WHITE)
                holder.btnSpeed.text = String.format(Locale.US, "%.1fx", currentShortsSpeed)

                holder.pv.setOnClickListener {
                    shortsPlayer?.let { p ->
                        if (p.isPlaying) p.pause() else p.play()
                    }
                }

                holder.btnLike.setOnClickListener {
                    val curFav = prefs.getBoolean("fav_$path", false)
                    prefs.edit().putBoolean("fav_$path", !curFav).apply()
                    holder.btnLike.text = if (!curFav) "Liked *" else "Like"
                    holder.btnLike.setTextColor(if (!curFav) Color.parseColor("#FF4081") else Color.WHITE)
                    Toast.makeText(
                        this@ShortsPlayerActivity,
                        if (!curFav) "Added to Favorite Videos" else "Removed from Favorites",
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
                    holder.btnSpeed.text = String.format(Locale.US, "%.1fx", currentShortsSpeed)
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

    private fun playShortAtPosition(pos: Int) {
        if (pos !in shortsPaths.indices) return
        currentActivePos = pos
        val path = shortsPaths[pos]

        for (i in 0 until rvShortsPager.childCount) {
            val child = rvShortsPager.getChildAt(i)
            val vh = rvShortsPager.getChildViewHolder(child) as? ShortPageVH
            vh?.pv?.player = null
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
            "Share to WhatsApp Status / Chat",
            "Share to Instagram Reels / Story",
            "Share to Telegram",
            "Send Phone-to-Phone (Wi-Fi Direct Fast Transfer)",
            "Share to Other Apps (System Chooser)"
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
                    } catch (e: Exception) {
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
        } catch (e: Exception) {
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
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to share video", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showShortsThreeDotsMenu(path: String, title: String, position: Int) {
        val prefs = getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        val options = arrayOf(
            if (isAutoScrollEnabled) "[Active] Auto-Scroll Next Short: ON" else "Auto-Scroll Next Short: OFF (Loop 1)",
            "Save Status / Short to Gallery Permanently",
            "Open in Full 8K Landscape Player",
            "Convert Short Video to MP3 Ringtone",
            "Delete Short Video Permanently"
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
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                (62 * d).toInt(),
                (44 * d).toInt()
            ).apply { bottomMargin = (14 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#8012121E"))
                cornerRadius = 22f * d
                setStroke((1.2f * d).toInt(), Color.parseColor("#5CE1E6"))
            }
        }
    }

    private class ShortPageVH(
        view: View,
        val pv: PlayerView,
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
        shortsPlayer?.release()
        shortsPlayer = null
    }
}
