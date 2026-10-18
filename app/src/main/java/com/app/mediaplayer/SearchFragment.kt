package com.app.mediaplayer

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.ArrayList
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class SearchFragment : Fragment() {

    data class SearchMediaEntry(
        val id: Long,
        val title: String,
        val artist: String,
        val path: String,
        val size: Long,
        val duration: Long,
        val isVideo: Boolean
    )

    private lateinit var etBottomSearchBox: EditText
    private lateinit var btnClearSearchText: ImageView
    private lateinit var rvSearchResults: RecyclerView
    private lateinit var llSearchInitialState: LinearLayout
    private lateinit var tvSearchEmptyTitle: TextView
    private lateinit var tvSearchEmptySubtitle: TextView

    private lateinit var tagAllMedia: TextView
    private lateinit var tagVideos: TextView
    private lateinit var tagAudios: TextView
    private lateinit var tagLargeFiles: TextView

    private val masterList = mutableListOf<SearchMediaEntry>()
    private val displayedList = mutableListOf<SearchMediaEntry>()
    private lateinit var resultsAdapter: SearchResultsAdapter

    private var selectedCategory = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_search, container, false)

        etBottomSearchBox = view.findViewById(R.id.etBottomSearchBox)
        btnClearSearchText = view.findViewById(R.id.btnClearSearchText)
        rvSearchResults = view.findViewById(R.id.rvSearchResults)
        llSearchInitialState = view.findViewById(R.id.llSearchInitialState)
        tvSearchEmptyTitle = view.findViewById(R.id.tvSearchEmptyTitle)
        tvSearchEmptySubtitle = view.findViewById(R.id.tvSearchEmptySubtitle)

        tagAllMedia = view.findViewById(R.id.tagAllMedia)
        tagVideos = view.findViewById(R.id.tagVideos)
        tagAudios = view.findViewById(R.id.tagAudios)
        tagLargeFiles = view.findViewById(R.id.tagLargeFiles)

        rvSearchResults.layoutManager = LinearLayoutManager(requireContext())
        resultsAdapter = SearchResultsAdapter(requireContext(), displayedList)
        rvSearchResults.adapter = resultsAdapter

        styleCategoryTags()
        setupCategoryClicks()
        setupSearchInput()
        loadAllDeviceMediaAsync()

        return view
    }

    private fun styleCategoryTags() {
        val tags = listOf(tagAllMedia, tagVideos, tagAudios, tagLargeFiles)
        tags.forEachIndexed { index, tv ->
            val isSelected = index == selectedCategory
            tv.background = GradientDrawable().apply {
                cornerRadius = 40f
                setColor(if (isSelected) Color.parseColor("#00FFFF") else Color.parseColor("#161622"))
            }
            tv.setTextColor(if (isSelected) Color.BLACK else Color.parseColor("#CCCCCC"))
            tv.typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    private fun setupCategoryClicks() {
        val tags = listOf(tagAllMedia, tagVideos, tagAudios, tagLargeFiles)
        tags.forEachIndexed { index, textView ->
            textView.setOnClickListener {
                selectedCategory = index
                styleCategoryTags()
                filterResults(etBottomSearchBox.text.toString())
            }
        }
    }

    private fun setupSearchInput() {
        etBottomSearchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                btnClearSearchText.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                filterResults(query)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        etBottomSearchBox.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                true
            } else {
                false
            }
        }

        btnClearSearchText.setOnClickListener {
            etBottomSearchBox.setText("")
            hideKeyboard()
        }
    }

    private fun hideKeyboard() {
        try {
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(etBottomSearchBox.windowToken, 0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun filterResults(rawQuery: String) {
        if (!isAdded || context == null) return
        val query = rawQuery.trim().lowercase(Locale.getDefault())

        val categoryFiltered = when (selectedCategory) {
            1 -> masterList.filter { it.isVideo }
            2 -> masterList.filter { !it.isVideo }
            3 -> masterList.filter { it.size >= 25L * 1024L * 1024L }
            else -> masterList
        }

        val matched = if (query.isEmpty()) {
            categoryFiltered.take(40)
        } else {
            categoryFiltered.filter {
                it.title.lowercase(Locale.getDefault()).contains(query) ||
                    it.artist.lowercase(Locale.getDefault()).contains(query)
            }
        }

        displayedList.clear()
        displayedList.addAll(matched)
        resultsAdapter.notifyDataSetChanged()

        if (displayedList.isEmpty()) {
            rvSearchResults.visibility = View.GONE
            llSearchInitialState.visibility = View.VISIBLE
            if (query.isNotEmpty()) {
                tvSearchEmptyTitle.text = "No results found for \"$rawQuery\""
                tvSearchEmptySubtitle.text = "Try searching with another song or video keyword."
            } else {
                tvSearchEmptyTitle.text = "Instant Media Search"
                tvSearchEmptySubtitle.text = "Use the bottom search bar below to find any song, video, or artist."
            }
        } else {
            llSearchInitialState.visibility = View.GONE
            rvSearchResults.visibility = View.VISIBLE
        }
    }

    private fun loadAllDeviceMediaAsync() {
        thread {
            try {
                val loaded = mutableListOf<SearchMediaEntry>()
                val resolver = context?.contentResolver ?: return@thread

                val vidProj = arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.DATA,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.DURATION
                )
                resolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    vidProj,
                    null,
                    null,
                    "${MediaStore.Video.Media.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idC = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                    val nameC = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                    val dataC = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                    val sizeC = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                    val durC = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)

                    while (cursor.moveToNext()) {
                        val id = if (idC != -1) cursor.getLong(idC) else 0L
                        val rawPath = if (dataC != -1) cursor.getString(dataC) else null
                        val path = if (!rawPath.isNullOrEmpty()) {
                            rawPath
                        } else {
                            ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id).toString()
                        }
                        val title = if (nameC != -1) cursor.getString(nameC) ?: File(path).name else File(path).name
                        val size = if (sizeC != -1) cursor.getLong(sizeC).coerceAtLeast(0L) else 0L
                        val dur = if (durC != -1) cursor.getLong(durC).coerceAtLeast(0L) else 0L
                        loaded.add(SearchMediaEntry(id, title, "Video", path, size, dur, true))
                    }
                }

                val audProj = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.SIZE,
                    MediaStore.Audio.Media.DURATION
                )
                resolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    audProj,
                    null,
                    null,
                    "${MediaStore.Audio.Media.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idC = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                    val nameC = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                    val titleC = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                    val artistC = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                    val dataC = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    val sizeC = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                    val durC = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)

                    while (cursor.moveToNext()) {
                        val id = if (idC != -1) cursor.getLong(idC) else 0L
                        val rawPath = if (dataC != -1) cursor.getString(dataC) else null
                        val path = if (!rawPath.isNullOrEmpty()) {
                            rawPath
                        } else {
                            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
                        }
                        val rawTitle = if (titleC != -1) cursor.getString(titleC) else null
                        val fallbackName = if (nameC != -1) cursor.getString(nameC) else null
                        val title = when {
                            !rawTitle.isNullOrEmpty() -> rawTitle
                            !fallbackName.isNullOrEmpty() -> fallbackName
                            else -> File(path).name
                        }
                        val rawArtist = if (artistC != -1) cursor.getString(artistC) else null
                        val artist = if (rawArtist.isNullOrEmpty() || rawArtist == "<unknown>") "Unknown Artist" else rawArtist
                        val size = if (sizeC != -1) cursor.getLong(sizeC).coerceAtLeast(0L) else 0L
                        val dur = if (durC != -1) cursor.getLong(durC).coerceAtLeast(0L) else 0L

                        if (dur >= 1000L || size > 10240L) {
                            loaded.add(SearchMediaEntry(id, title, artist, path, size, dur, false))
                        }
                    }
                }

                activity?.runOnUiThread {
                    if (!isAdded || context == null) return@runOnUiThread
                    masterList.clear()
                    masterList.addAll(loaded)
                    filterResults(etBottomSearchBox.text.toString())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private inner class SearchResultsAdapter(
        private val ctx: Context,
        private val items: List<SearchMediaEntry>
    ) : RecyclerView.Adapter<SearchResultsAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val imgThumb: ImageView = view.findViewById(R.id.imgThumbnail)
            val tvDuration: TextView = view.findViewById(R.id.tvDuration)
            val tvTitle: TextView = view.findViewById(R.id.tvTitle)
            val tvSizeFormat: TextView = view.findViewById(R.id.tvSizeFormat)
            val btnOptions: ImageView = view.findViewById(R.id.btnOptions)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(ctx).inflate(R.layout.item_video, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvTitle.text = item.title
            holder.tvDuration.text = formatTime(item.duration)
            val sizeMb = item.size / (1024.0 * 1024.0)
            val badge = if (item.isVideo) "VIDEO" else "MUSIC • ${item.artist}"
            holder.tvSizeFormat.text = String.format(Locale.US, "%.1f MB • %s", sizeMb, badge)

            if (item.isVideo) {
                holder.imgThumb.setImageResource(android.R.drawable.ic_media_play)
            } else {
                holder.imgThumb.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
            }

            holder.btnOptions.visibility = View.GONE

            holder.itemView.setOnClickListener {
                if (item.isVideo) {
                    val videoPaths = ArrayList<String>()
                    items.filter { it.isVideo }.forEach { videoPaths.add(it.path) }
                    val intent = Intent(ctx, PlayerActivity::class.java).apply {
                        putExtra("VIDEO_PATH", item.path)
                        putExtra("VIDEO_TITLE", item.title)
                        putStringArrayListExtra("VIDEO_LIST", videoPaths)
                    }
                    ctx.startActivity(intent)
                } else {
                    val audioItems = items.filter { !it.isVideo }
                    val paths = ArrayList<String>(audioItems.size)
                    val titles = ArrayList<String>(audioItems.size)
                    val artists = ArrayList<String>(audioItems.size)
                    audioItems.forEach {
                        paths.add(it.path)
                        titles.add(it.title)
                        artists.add(it.artist)
                    }
                    val targetIdx = audioItems.indexOfFirst { it.path == item.path }.coerceAtLeast(0)
                    val intent = Intent(ctx, AudioPlayerActivity::class.java).apply {
                        putExtra("AUDIO_PATH", item.path)
                        putExtra("AUDIO_TITLE", item.title)
                        putExtra("AUDIO_ARTIST", item.artist)
                        putExtra("AUDIO_DURATION", item.duration)
                        putExtra("AUDIO_ID", item.id)
                        putExtra("AUDIO_POSITION", targetIdx)
                        putStringArrayListExtra("AUDIO_LIST_PATHS", paths)
                        putStringArrayListExtra("AUDIO_LIST_TITLES", titles)
                        putStringArrayListExtra("AUDIO_LIST_ARTISTS", artists)
                    }
                    ctx.startActivity(intent)
                }
            }
        }

        override fun getItemCount(): Int = items.size

        private fun formatTime(ms: Long): String {
            val s = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0L))
            val m = s / 60
            val sec = s % 60
            return String.format(Locale.US, "%02d:%02d", m, sec)
        }
    }
}
