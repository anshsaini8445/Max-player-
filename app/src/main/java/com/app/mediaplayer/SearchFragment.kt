package com.app.mediaplayer

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.abs

class SearchFragment : Fragment() {

    private lateinit var rootSearchLayout: LinearLayout
    private lateinit var rvSearchResults: RecyclerView
    private lateinit var tvEmptySearch: TextView
    private lateinit var pillSearchVideos: TextView
    private lateinit var pillSearchAudio: TextView
    private lateinit var etSearchInput: EditText

    private val allVideos = mutableListOf<VideoModel>()
    private val filteredVideos = mutableListOf<VideoModel>()

    private val allSongs = mutableListOf<AudioModel>()
    private val filteredSongs = mutableListOf<AudioModel>()

    private lateinit var videoAdapter: VideoAdapter
    private lateinit var audioAdapter: AudioAdapter

    private var isVideoTabActive = true
    private var currentQuery = ""
    private var accentColor = Color.parseColor("#00FFFF")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val d = ctx.resources.displayMetrics.density
        val theme = ThemeStudioHelper.getCurrentTheme(ctx)
        accentColor = Color.parseColor(theme.accentHex)

        rootSearchLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(theme.bgHex))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding((10 * d).toInt(), (6 * d).toInt(), (10 * d).toInt(), (10 * d).toInt())
        }

        rvSearchResults = RecyclerView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            layoutManager = LinearLayoutManager(ctx)
            setHasFixedSize(true)
            setItemViewCacheSize(24)
        }
        videoAdapter = VideoAdapter(ctx, filteredVideos)
        audioAdapter = AudioAdapter(ctx, filteredSongs)
        rvSearchResults.adapter = videoAdapter
        attachHorizontalSwipeToSwitchSearchTabs(ctx)
        rootSearchLayout.addView(rvSearchResults)

        tvEmptySearch = TextView(ctx).apply {
            text = "No matching media found"
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
        rootSearchLayout.addView(tvEmptySearch)

        val bottomDockContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((4 * d).toInt(), (8 * d).toInt(), (4 * d).toInt(), (2 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val pillsRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * d).toInt() }
        }

        pillSearchVideos = TextView(ctx).apply {
            text = "🎬  Videos (0)"
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (6 * d).toInt()
            }
            setOnClickListener {
                isVideoTabActive = true
                updateTabPillVisuals(d)
                applyInstantFilterNow()
            }
        }

        pillSearchAudio = TextView(ctx).apply {
            text = "🎵  Music / Audio (0)"
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (6 * d).toInt()
            }
            setOnClickListener {
                isVideoTabActive = false
                updateTabPillVisuals(d)
                applyInstantFilterNow()
            }
        }

        pillsRow.addView(pillSearchVideos)
        pillsRow.addView(pillSearchAudio)
        bottomDockContainer.addView(pillsRow)

        val searchBoxRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * d).toInt(), (4 * d).toInt(), (12 * d).toInt(), (4 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (48 * d).toInt()
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#101826"))
                cornerRadius = 24f * d
                setStroke((1.6f * d).toInt(), accentColor)
            }
        }

        val tvSearchLens = TextView(ctx).apply {
            text = "🔍"
            textSize = 15f
            setPadding(0, 0, (10 * d).toInt(), 0)
        }

        etSearchInput = EditText(ctx).apply {
            hint = "Search videos, movies, songs, artists..."
            setHintTextColor(Color.parseColor("#7F92A8"))
            setTextColor(Color.WHITE)
            textSize = 14f
            setSingleLine(true)
            background = null
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnClearSearch = TextView(ctx).apply {
            text = "✕"
            setTextColor(accentColor)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((10 * d).toInt(), (4 * d).toInt(), (6 * d).toInt(), (4 * d).toInt())
            visibility = View.GONE
            setOnClickListener {
                etSearchInput.setText("")
            }
        }

        etSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentQuery = s?.toString()?.trim() ?: ""
                btnClearSearch.visibility = if (currentQuery.isEmpty()) View.GONE else View.VISIBLE
                applyInstantFilterNow()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        searchBoxRow.addView(tvSearchLens)
        searchBoxRow.addView(etSearchInput)
        searchBoxRow.addView(btnClearSearch)
        bottomDockContainer.addView(searchBoxRow)

        rootSearchLayout.addView(bottomDockContainer)

        updateTabPillVisuals(d)

        val cachedVids = DeepMediaScanner.getCachedVideosInstant()
        if (cachedVids.isNotEmpty()) {
            allVideos.clear()
            allVideos.addAll(cachedVids)
            applyInstantFilterNow()
        }
        loadVideosAndAudioInBackground()

        return rootSearchLayout
    }

    override fun onResume() {
        super.onResume()
        loadVideosAndAudioInBackground()
    }

    private fun attachHorizontalSwipeToSwitchSearchTabs(context: Context) {
        val d = context.resources.displayMetrics.density
        val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
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
                    if (dx < 0 && isVideoTabActive) {
                        isVideoTabActive = false
                        updateTabPillVisuals(d)
                        applyInstantFilterNow()
                        return true
                    } else if (dx > 0 && !isVideoTabActive) {
                        isVideoTabActive = true
                        updateTabPillVisuals(d)
                        applyInstantFilterNow()
                        return true
                    }
                }
                return false
            }
        })

        rvSearchResults.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                detector.onTouchEvent(e)
                return false
            }
        })
    }

    private fun updateTabPillVisuals(d: Float) {
        pillSearchVideos.setTextColor(
            if (isVideoTabActive) Color.parseColor("#0A0A10") else Color.WHITE
        )
        pillSearchVideos.background = GradientDrawable().apply {
            setColor(if (isVideoTabActive) accentColor else Color.parseColor("#182232"))
            cornerRadius = 20f * d
        }

        pillSearchAudio.setTextColor(
            if (!isVideoTabActive) Color.parseColor("#0A0A10") else Color.WHITE
        )
        pillSearchAudio.background = GradientDrawable().apply {
            setColor(if (!isVideoTabActive) accentColor else Color.parseColor("#182232"))
            cornerRadius = 20f * d
        }
    }

    private fun loadVideosAndAudioInBackground() {
        val ctx = context ?: return
        thread {
            val scannedVideos = DeepMediaScanner.scanAllVideosEverywhere(ctx, forceRefresh = false)
            val scannedAudio = scanAllAudioFast(ctx)

            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                allVideos.clear()
                allVideos.addAll(scannedVideos)
                allSongs.clear()
                allSongs.addAll(scannedAudio)
                applyInstantFilterNow()
            }
        }
    }

    private fun applyInstantFilterNow() {
        if (!isAdded) return
        val d = resources.displayMetrics.density
        val q = currentQuery.lowercase(Locale.US)

        val matchedVids = if (q.isEmpty()) {
            allVideos
        } else {
            allVideos.filter { it.title.lowercase(Locale.US).contains(q) }
        }

        val matchedAudio = if (q.isEmpty()) {
            allSongs
        } else {
            allSongs.filter {
                it.title.lowercase(Locale.US).contains(q) ||
                    it.artist.lowercase(Locale.US).contains(q)
            }
        }

        if (q.isNotEmpty()) {
            if (isVideoTabActive && matchedVids.isEmpty() && matchedAudio.isNotEmpty()) {
                isVideoTabActive = false
                updateTabPillVisuals(d)
            } else if (!isVideoTabActive && matchedAudio.isEmpty() && matchedVids.isNotEmpty()) {
                isVideoTabActive = true
                updateTabPillVisuals(d)
            }
        }

        pillSearchVideos.text = "🎬  Videos (${matchedVids.size})"
        pillSearchAudio.text = "🎵  Music / Audio (${matchedAudio.size})"

        if (isVideoTabActive) {
            filteredVideos.clear()
            filteredVideos.addAll(matchedVids)
            if (rvSearchResults.adapter !== videoAdapter) {
                rvSearchResults.adapter = videoAdapter
            }
            videoAdapter.notifyDataSetChanged()

            if (filteredVideos.isEmpty()) {
                rvSearchResults.visibility = View.GONE
                tvEmptySearch.visibility = View.VISIBLE
            } else {
                tvEmptySearch.visibility = View.GONE
                rvSearchResults.visibility = View.VISIBLE
            }
        } else {
            filteredSongs.clear()
            filteredSongs.addAll(matchedAudio)
            if (rvSearchResults.adapter !== audioAdapter) {
                rvSearchResults.adapter = audioAdapter
            }
            audioAdapter.notifyDataSetChanged()

            if (filteredSongs.isEmpty()) {
                rvSearchResults.visibility = View.GONE
                tvEmptySearch.visibility = View.VISIBLE
            } else {
                tvEmptySearch.visibility = View.GONE
                rvSearchResults.visibility = View.VISIBLE
            }
        }
    }

    private fun scanAllAudioFast(context: Context): List<AudioModel> {
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
            context.contentResolver.query(
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
                    if (path.isEmpty() || PermanentDeleteHelper.isMarkedDeleted(context, path)) continue

                    val dur = if (durCol != -1) cursor.getLong(durCol) else 0L
                    if (dur < 1000L) continue
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L

                    val title = (if (nameCol != -1) cursor.getString(nameCol) else null)
                        ?: (if (titleCol != -1) cursor.getString(titleCol) else null)
                        ?: File(path).name
                    val artist = (if (artistCol != -1) cursor.getString(artistCol) else null)
                        ?.takeIf { it != "<unknown>" } ?: "Hybrid Player Music"

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
        return list
    }
}
