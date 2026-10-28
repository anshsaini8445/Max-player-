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
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.HorizontalScrollView
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

    companion object {
        private const val PREF_SEARCH_HISTORY = "HYBRID_RECENT_SEARCHES_PREFS"
        private const val KEY_RECENT_LIST = "recent_queries_v1"
    }

    private lateinit var rootSearchLayout: LinearLayout
    private lateinit var searchBoxRow: LinearLayout
    private lateinit var etSearchInput: EditText
    private lateinit var btnClearSearch: TextView

    private lateinit var recentSearchesContainer: LinearLayout
    private lateinit var recentChipsRow: LinearLayout
    private lateinit var pillSearchVideos: TextView
    private lateinit var pillSearchAudio: TextView

    private lateinit var rvSearchResults: RecyclerView
    private lateinit var tvEmptySearch: TextView

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
            setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
        }
        ThemeStudioHelper.applyThemeAndOneHandModeToRoot(ctx, rootSearchLayout)

        // 1. Sleek Top Search Input Bar
        searchBoxRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * d).toInt(), (4 * d).toInt(), (12 * d).toInt(), (4 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (48 * d).toInt()
            ).apply { bottomMargin = (10 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141E30"))
                cornerRadius = 24f * d
                setStroke((1.2f * d).toInt(), Color.parseColor("#38FFFFFF"))
            }
        }

        etSearchInput = EditText(ctx).apply {
            hint = "Search videos, movies, songs, artists..."
            setHintTextColor(Color.parseColor("#7F92A8"))
            setTextColor(Color.WHITE)
            textSize = 14f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = null
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)
                ) {
                    saveRecentSearchQuery(ctx, currentQuery)
                    refreshRecentSearchesUi(ctx, d)
                    true
                } else {
                    false
                }
            }
        }

        btnClearSearch = TextView(ctx).apply {
            text = "Clear"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((10 * d).toInt(), (5 * d).toInt(), (10 * d).toInt(), (5 * d).toInt())
            visibility = View.GONE
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 12f * d
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                saveRecentSearchQuery(ctx, currentQuery)
                etSearchInput.setText("")
                refreshRecentSearchesUi(ctx, d)
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

        searchBoxRow.addView(etSearchInput)
        searchBoxRow.addView(btnClearSearch)
        rootSearchLayout.addView(searchBoxRow)

        // 2. Clean Recent Searches History Section with 1-Tap Clear
        recentSearchesContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * d).toInt() }
        }

        val recentHeaderRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((4 * d).toInt(), 0, (4 * d).toInt(), (6 * d).toInt())
        }

        val tvRecentLabel = TextView(ctx).apply {
            text = "RECENT SEARCHES"
            setTextColor(Color.parseColor("#9FB4CE"))
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnClearAllRecent = TextView(ctx).apply {
            text = "Clear History"
            setTextColor(Color.parseColor("#FF5252"))
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((8 * d).toInt(), (3 * d).toInt(), (8 * d).toInt(), (3 * d).toInt())
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                clearRecentSearchHistory(ctx)
                refreshRecentSearchesUi(ctx, d)
            }
        }

        recentHeaderRow.addView(tvRecentLabel)
        recentHeaderRow.addView(btnClearAllRecent)
        recentSearchesContainer.addView(recentHeaderRow)

        val recentScroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
        }
        recentChipsRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        recentScroll.addView(recentChipsRow)
        recentSearchesContainer.addView(recentScroll)
        rootSearchLayout.addView(recentSearchesContainer)

        // 3. Clean Category Tabs Row (Videos vs Music/Audio)
        val pillsRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * d).toInt() }
        }

        pillSearchVideos = TextView(ctx).apply {
            text = "Videos (0)"
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((14 * d).toInt(), (9 * d).toInt(), (14 * d).toInt(), (9 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (6 * d).toInt()
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                if (currentQuery.length >= 2) saveRecentSearchQuery(ctx, currentQuery)
                isVideoTabActive = true
                updateTabPillVisuals(d)
                applyInstantFilterNow()
            }
        }

        pillSearchAudio = TextView(ctx).apply {
            text = "Music / Audio (0)"
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((14 * d).toInt(), (9 * d).toInt(), (14 * d).toInt(), (9 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (6 * d).toInt()
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                if (currentQuery.length >= 2) saveRecentSearchQuery(ctx, currentQuery)
                isVideoTabActive = false
                updateTabPillVisuals(d)
                applyInstantFilterNow()
            }
        }

        pillsRow.addView(pillSearchVideos)
        pillsRow.addView(pillSearchAudio)
        rootSearchLayout.addView(pillsRow)

        // 4. Instant Highlighting Results RecyclerView
        rvSearchResults = RecyclerView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            layoutManager = LinearLayoutManager(ctx)
            setHasFixedSize(true)
            setItemViewCacheSize(28)
            itemAnimator = null
        }
        videoAdapter = VideoAdapter(ctx, filteredVideos)
        audioAdapter = AudioAdapter(ctx, filteredSongs)
        rvSearchResults.adapter = videoAdapter
        attachHorizontalSwipeToSwitchSearchTabs(ctx)
        rootSearchLayout.addView(rvSearchResults)

        tvEmptySearch = TextView(ctx).apply {
            text = "No matching media found"
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
        rootSearchLayout.addView(tvEmptySearch)

        updateTabPillVisuals(d)
        refreshRecentSearchesUi(ctx, d)

        val cachedVids = DeepMediaScanner.getCachedVideosInstant(ctx)
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
        val ctx = context ?: return
        ThemeStudioHelper.applyThemeAndOneHandModeToRoot(ctx, rootSearchLayout)
        refreshRecentSearchesUi(ctx, resources.displayMetrics.density)
        loadVideosAndAudioInBackground()
    }

    override fun onPause() {
        super.onPause()
        val ctx = context ?: return
        if (currentQuery.length >= 2) {
            saveRecentSearchQuery(ctx, currentQuery)
        }
    }

    private fun getRecentSearchQueries(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREF_SEARCH_HISTORY, Context.MODE_PRIVATE)
            .getString(KEY_RECENT_LIST, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("||").filter { it.isNotBlank() }.take(12)
    }

    private fun saveRecentSearchQuery(context: Context, query: String) {
        val clean = query.trim().replace("||", " ")
        if (clean.length < 2) return
        val list = getRecentSearchQueries(context).toMutableList()
        list.removeAll { it.equals(clean, ignoreCase = true) }
        list.add(0, clean)
        val joined = list.take(12).joinToString("||")
        context.getSharedPreferences(PREF_SEARCH_HISTORY, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECENT_LIST, joined)
            .apply()
    }

    private fun clearRecentSearchHistory(context: Context) {
        context.getSharedPreferences(PREF_SEARCH_HISTORY, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_RECENT_LIST)
            .apply()
    }

    private fun refreshRecentSearchesUi(context: Context, d: Float) {
        val recents = getRecentSearchQueries(context)
        if (recents.isEmpty()) {
            recentSearchesContainer.visibility = View.GONE
            return
        }
        recentSearchesContainer.visibility = View.VISIBLE
        recentChipsRow.removeAllViews()

        for (q in recents) {
            val chip = TextView(context).apply {
                text = q
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setPadding((12 * d).toInt(), (6 * d).toInt(), (12 * d).toInt(), (6 * d).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = (6 * d).toInt() }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#18FFFFFF"))
                    cornerRadius = 14f * d
                }
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    etSearchInput.setText(q)
                    etSearchInput.setSelection(q.length)
                }
            }
            recentChipsRow.addView(chip)
        }
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
            setColor(if (isVideoTabActive) Color.WHITE else Color.parseColor("#16FFFFFF"))
            cornerRadius = 18f * d
        }

        pillSearchAudio.setTextColor(
            if (!isVideoTabActive) Color.parseColor("#0A0A10") else Color.WHITE
        )
        pillSearchAudio.background = GradientDrawable().apply {
            setColor(if (!isVideoTabActive) Color.WHITE else Color.parseColor("#16FFFFFF"))
            cornerRadius = 18f * d
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

        videoAdapter.setSearchHighlightQuery(currentQuery)

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

        pillSearchVideos.text = "Videos (${matchedVids.size})"
        pillSearchAudio.text = "Music / Audio (${matchedAudio.size})"

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
                val durCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = if (idCol != -1) cursor.getLong(idCol) else 0L
                    val path = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                    if (path.isEmpty() || PermanentDeleteHelper.isMarkedDeleted(context, path)) continue

                    val dur = if (durCol != -1) cursor.getLong(durCol) else 0L
                    if (dur < 1000L) continue

                    val title = (if (nameCol != -1) cursor.getString(nameCol) else null)
                        ?: (if (titleCol != -1) cursor.getString(titleCol) else null)
                        ?: File(path).name
                    val artist = (if (artistCol != -1) cursor.getString(artistCol) else null)
                        ?.takeIf { it != "<unknown>" } ?: "Unknown Artist"

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
        return list
    }
}
