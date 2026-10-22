package com.app.mediaplayer

import android.content.ContentUris
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.Locale
import kotlin.concurrent.thread

class SearchFragment : Fragment() {

    private lateinit var rootSearchLayout: LinearLayout
    private lateinit var tvTabVideosLeft: TextView
    private lateinit var tvTabAudioRight: TextView
    private lateinit var rvHorizontalSplitPager: RecyclerView
    private lateinit var etSearchInputBottom: EditText

    private var rvVideoPage: RecyclerView? = null
    private var rvAudioPage: RecyclerView? = null
    private var tvEmptyVideoPage: TextView? = null
    private var tvEmptyAudioPage: TextView? = null

    private val allVideos = mutableListOf<VideoModel>()
    private val allAudios = mutableListOf<AudioTrackModel>()
    private val filteredVideos = mutableListOf<VideoModel>()
    private val filteredAudios = mutableListOf<AudioTrackModel>()

    private var videoAdapter: VideoAdapter? = null
    private var audioAdapter: AudioAdapter? = null

    private var activePageIndex = 0
    private var accentColor = Color.parseColor("#5CE1E6")
    private var isIndexing = false

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
        accentColor = try {
            Color.parseColor(accentHex)
        } catch (_: Exception) {
            Color.parseColor("#5CE1E6")
        }

        rootSearchLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(
                try {
                    Color.parseColor(bgHex)
                } catch (_: Exception) {
                    Color.parseColor("#0D1B2A")
                }
            )
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding((12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
        }

        // Top Split Tabs: Left = Videos | Right = Music / Audio
        val topSplitTabsRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            weightSum = 2f
            setPadding(0, (4 * d).toInt(), 0, (10 * d).toInt())
        }

        tvTabVideosLeft = createSplitTabButton("Videos (0)", true, d) {
            scrollToPage(0)
        }
        tvTabAudioRight = createSplitTabButton("Music / Audio (0)", false, d) {
            scrollToPage(1)
        }

        topSplitTabsRow.addView(tvTabVideosLeft)
        topSplitTabsRow.addView(tvTabAudioRight)
        rootSearchLayout.addView(topSplitTabsRow)

        // Two-Page Horizontal Swipe Pager: Page 0 = All Videos (Left), Page 1 = All Audio (Right)
        val pagerLayoutManager = LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
        rvHorizontalSplitPager = RecyclerView(ctx).apply {
            layoutManager = pagerLayoutManager
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        PagerSnapHelper().attachToRecyclerView(rvHorizontalSplitPager)

        rvHorizontalSplitPager.adapter = object : RecyclerView.Adapter<SplitPageVH>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SplitPageVH {
                val frame = FrameLayout(ctx).apply {
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                val rv = RecyclerView(ctx).apply {
                    layoutManager = LinearLayoutManager(ctx)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    clipToPadding = false
                }
                InShotScrollAnimator.attachToRecyclerView(rv)

                val tvEmpty = TextView(ctx).apply {
                    text = "No matching media found"
                    setTextColor(Color.parseColor("#B0C4DE"))
                    textSize = 14f
                    gravity = Gravity.CENTER
                    visibility = View.GONE
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                frame.addView(rv)
                frame.addView(tvEmpty)
                return SplitPageVH(frame, rv, tvEmpty)
            }

            override fun onBindViewHolder(holder: SplitPageVH, position: Int) {
                if (position == 0) {
                    rvVideoPage = holder.rv
                    tvEmptyVideoPage = holder.tvEmpty
                    if (videoAdapter == null) {
                        videoAdapter = VideoAdapter(ctx, filteredVideos)
                    }
                    holder.rv.adapter = videoAdapter
                    updateEmptyStateForPage(0)
                } else {
                    rvAudioPage = holder.rv
                    tvEmptyAudioPage = holder.tvEmpty
                    if (audioAdapter == null) {
                        audioAdapter = AudioAdapter(ctx, filteredAudios)
                    }
                    holder.rv.adapter = audioAdapter
                    updateEmptyStateForPage(1)
                }
            }

            override fun getItemCount(): Int = 2
        }

        rvHorizontalSplitPager.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val pos = pagerLayoutManager.findFirstCompletelyVisibleItemPosition()
                        .takeIf { it != RecyclerView.NO_POSITION }
                        ?: pagerLayoutManager.findFirstVisibleItemPosition()
                    if (pos in 0..1 && pos != activePageIndex) {
                        activePageIndex = pos
                        updateSplitTabVisuals()
                    }
                }
            }
        })

        rootSearchLayout.addView(rvHorizontalSplitPager)

        // Bottom Search Bar (Strictly at the bottom of the Search screen)
        etSearchInputBottom = EditText(ctx).apply {
            hint = "Search videos, movies, songs, artists..."
            setHintTextColor(Color.parseColor("#99AACC"))
            setTextColor(Color.WHITE)
            textSize = 14.5f
            setSingleLine(true)
            setPadding((18 * d).toInt(), (12 * d).toInt(), (18 * d).toInt(), (12 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (8 * d).toInt()
                bottomMargin = (4 * d).toInt()
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#142538"))
                cornerRadius = 26f * d
                setStroke((1.5f * d).toInt(), accentColor)
            }
        }
        rootSearchLayout.addView(etSearchInputBottom)

        etSearchInputBottom.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterBothVideoAndAudioPages(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        loadSearchableMediaInBackground()
        return rootSearchLayout
    }

    private class SplitPageVH(
        view: View,
        val rv: RecyclerView,
        val tvEmpty: TextView
    ) : RecyclerView.ViewHolder(view)

    private fun createSplitTabButton(
        label: String,
        selected: Boolean,
        d: Float,
        onClick: () -> Unit
    ): TextView {
        return TextView(requireContext()).apply {
            text = label
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((12 * d).toInt(), (9 * d).toInt(), (12 * d).toInt(), (9 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (6 * d).toInt()
                marginStart = (6 * d).toInt()
            }
            setTextColor(if (selected) Color.BLACK else Color.WHITE)
            background = GradientDrawable().apply {
                setColor(if (selected) accentColor else Color.parseColor("#162A40"))
                cornerRadius = 20f * d
            }
            setOnClickListener { onClick() }
        }
    }

    private fun scrollToPage(pageIdx: Int) {
        activePageIndex = pageIdx.coerceIn(0, 1)
        rvHorizontalSplitPager.smoothScrollToPosition(activePageIndex)
        updateSplitTabVisuals()
    }

    private fun updateSplitTabVisuals() {
        val d = resources.displayMetrics.density
        val isVideoActive = activePageIndex == 0

        tvTabVideosLeft.text = "Videos (${filteredVideos.size})"
        tvTabVideosLeft.setTextColor(if (isVideoActive) Color.BLACK else Color.WHITE)
        tvTabVideosLeft.background = GradientDrawable().apply {
            setColor(if (isVideoActive) accentColor else Color.parseColor("#162A40"))
            cornerRadius = 20f * d
        }

        tvTabAudioRight.text = "Music / Audio (${filteredAudios.size})"
        tvTabAudioRight.setTextColor(if (!isVideoActive) Color.BLACK else Color.WHITE)
        tvTabAudioRight.background = GradientDrawable().apply {
            setColor(if (!isVideoActive) accentColor else Color.parseColor("#162A40"))
            cornerRadius = 20f * d
        }
    }

    private fun loadSearchableMediaInBackground() {
        if (isIndexing) return
        isIndexing = true
        val ctx = context ?: return

        thread {
            val vList = DeepMediaScanner.scanAllVideosEverywhere(ctx)
            val aList = mutableListOf<AudioTrackModel>()

            try {
                val aProj = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.SIZE,
                    MediaStore.Audio.Media.DURATION
                )
                ctx.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    aProj,
                    null,
                    null,
                    "${MediaStore.Audio.Media.DATE_ADDED} DESC"
                )?.use { c ->
                    val idC = c.getColumnIndex(MediaStore.Audio.Media._ID)
                    val albIdC = c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                    val nameC = c.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                    val artC = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                    val albC = c.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                    val dataC = c.getColumnIndex(MediaStore.Audio.Media.DATA)
                    val sizeC = c.getColumnIndex(MediaStore.Audio.Media.SIZE)
                    val durC = c.getColumnIndex(MediaStore.Audio.Media.DURATION)

                    while (c.moveToNext()) {
                        val id = if (idC != -1) c.getLong(idC) else 0L
                        val albumId = if (albIdC != -1) c.getLong(albIdC) else id
                        val raw = if (dataC != -1) c.getString(dataC) else null
                        val path = if (!raw.isNullOrEmpty()) {
                            raw
                        } else {
                            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
                        }
                        if (PermanentDeleteHelper.isMarkedDeleted(ctx, path)) continue

                        val title = if (nameC != -1) c.getString(nameC) ?: File(path).name else File(path).name
                        val artistRaw = if (artC != -1) c.getString(artC) else null
                        val artist = if (artistRaw.isNullOrEmpty() || artistRaw == "<unknown>") "Unknown Artist" else artistRaw
                        val album = if (albC != -1) c.getString(albC) ?: "Music" else "Music"
                        val size = if (sizeC != -1) c.getLong(sizeC) else 0L
                        val dur = if (durC != -1) c.getLong(durC) else 0L
                        if (dur >= 1000L) {
                            aList.add(AudioTrackModel(albumId, title, artist, album, path, size, dur))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            activity?.runOnUiThread {
                isIndexing = false
                if (!isAdded) return@runOnUiThread
                allVideos.clear()
                allVideos.addAll(vList)
                allAudios.clear()
                allAudios.addAll(aList)
                filterBothVideoAndAudioPages(etSearchInputBottom.text.toString())
            }
        }
    }

    private fun filterBothVideoAndAudioPages(queryText: String) {
        val q = queryText.trim().lowercase(Locale.getDefault())

        filteredVideos.clear()
        if (q.isEmpty()) {
            filteredVideos.addAll(allVideos)
        } else {
            filteredVideos.addAll(
                allVideos.filter { it.title.lowercase(Locale.getDefault()).contains(q) }
            )
        }
        videoAdapter?.notifyDataSetChanged()
        updateEmptyStateForPage(0)

        filteredAudios.clear()
        if (q.isEmpty()) {
            filteredAudios.addAll(allAudios)
        } else {
            filteredAudios.addAll(
                allAudios.filter {
                    it.title.lowercase(Locale.getDefault()).contains(q) ||
                        it.artist.lowercase(Locale.getDefault()).contains(q)
                }
            )
        }
        audioAdapter?.notifyDataSetChanged()
        updateEmptyStateForPage(1)

        updateSplitTabVisuals()
    }

    private fun updateEmptyStateForPage(page: Int) {
        if (page == 0) {
            val empty = filteredVideos.isEmpty()
            rvVideoPage?.visibility = if (empty) View.GONE else View.VISIBLE
            tvEmptyVideoPage?.visibility = if (empty) View.VISIBLE else View.GONE
        } else {
            val empty = filteredAudios.isEmpty()
            rvAudioPage?.visibility = if (empty) View.GONE else View.VISIBLE
            tvEmptyAudioPage?.visibility = if (empty) View.VISIBLE else View.GONE
        }
    }
}
