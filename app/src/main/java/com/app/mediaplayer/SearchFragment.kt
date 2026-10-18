package com.app.mediaplayer

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.Locale
import kotlin.concurrent.thread

data class SearchItemModel(
    val id: Long,
    val title: String,
    val path: String,
    val size: Long,
    val duration: Long,
    val isVideo: Boolean
)

class SearchFragment : Fragment() {

    private lateinit var etBottomSearchBox: EditText
    private lateinit var btnClearSearchText: ImageView
    private lateinit var rvSearchResults: RecyclerView
    private lateinit var llSearchInitialState: LinearLayout

    private lateinit var tagAllMedia: TextView
    private lateinit var tagVideos: TextView
    private lateinit var tagAudios: TextView
    private lateinit var tagLargeFiles: TextView

    private val masterMediaList = mutableListOf<SearchItemModel>()
    private val filteredMediaList = mutableListOf<SearchItemModel>()
    private lateinit var searchAdapter: VideoAdapter

    private var selectedCategory = 0 // 0: All, 1: Videos, 2: Audios, 3: Large

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

        tagAllMedia = view.findViewById(R.id.tagAllMedia)
        tagVideos = view.findViewById(R.id.tagVideos)
        tagAudios = view.findViewById(R.id.tagAudios)
        tagLargeFiles = view.findViewById(R.id.tagLargeFiles)

        rvSearchResults.layoutManager = LinearLayoutManager(requireContext())

        // Map SearchItemModel list to VideoAdapter safely for playback & actions
        searchAdapter = VideoAdapter(requireContext(), emptyList())
        rvSearchResults.adapter = searchAdapter

        setupCategoryClicks()
        setupSearchListeners()
        loadAllDeviceMediaAsync()

        return view
    }

    private fun setupCategoryClicks() {
        val activeBg = Color.parseColor("#00FFFF")
        val inactiveBg = Color.parseColor("#161622")
        val activeText = Color.parseColor("#000000")
        val inactiveText = Color.parseColor("#CCCCCC")

        val tags = listOf(tagAllMedia, tagVideos, tagAudios, tagLargeFiles)

        tags.forEachIndexed { index, textView ->
            textView.setOnClickListener {
                selectedCategory = index
                tags.forEachIndexed { i, tv ->
                    if (i == selectedCategory) {
                        tv.setBackgroundColor(activeBg)
                        tv.setTextColor(activeText)
                        tv.typeface = Typeface.DEFAULT_BOLD
                    } else {
                        tv.setBackgroundColor(inactiveBg)
                        tv.setTextColor(inactiveText)
                        tv.typeface = Typeface.DEFAULT
                    }
                }
                filterMedia(etBottomSearchBox.text.toString())
            }
        }
    }

    private fun setupSearchListeners() {
        etBottomSearchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after] -> {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString()
                btnClearSearchText.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                filterMedia(query)
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
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etBottomSearchBox.windowToken, 0)
    }

    private fun filterMedia(query: String) {
        val trimmed = query.trim().lowercase(Locale.getDefault())

        val categoryFiltered = when (selectedCategory) {
            1 -> masterMediaList.filter { it.isVideo }
            2 -> masterMediaList.filter { !it.isVideo }
            3 -> masterMediaList.filter { it.size >= 50L * 1024L * 1024L }
            else -> masterMediaList
        }

        val finalResult = if (trimmed.isEmpty()) {
            emptyList()
        } else {
            categoryFiltered.filter { it.title.lowercase(Locale.getDefault()).contains(trimmed) }
        }

        filteredMediaList.clear()
        filteredMediaList.addAll(finalResult)

        // Convert SearchItemModel to VideoModel list for VideoAdapter
        val videoModelList = filteredMediaList.map { item ->
            VideoModel(item.id, item.title, item.path, item.size, item.duration)
        }

        rvSearchResults.adapter = VideoAdapter(requireContext(), videoModelList)

        if (trimmed.isEmpty()) {
            llSearchInitialState.visibility = View.VISIBLE
            rvSearchResults.visibility = View.GONE
        } else {
            llSearchInitialState.visibility = View.GONE
            rvSearchResults.visibility = View.VISIBLE
        }
    }

    private fun loadAllDeviceMediaAsync() {
        thread {
            try {
                val list = mutableListOf<SearchItemModel>()
                val resolver = context?.contentResolver ?: return@thread

                // 1. Fetch Videos
                val vidProj = arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.DATA,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.DURATION
                )
                resolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, vidProj, null, null, null)?.use { cursor ->
                    val idC = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                    val nameC = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                    val dataC = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                    val sizeC = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                    val durC = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)

                    while (cursor.moveToNext()) {
                        val id = if (idC != -1) cursor.getLong(idC) else 0L
                        val rawPath = if (dataC != -1) cursor.getString(dataC) else null
                        val path = if (!rawPath.isNullOrEmpty()) rawPath else ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id).toString()
                        val title = if (nameC != -1) cursor.getString(nameC) ?: File(path).name else File(path).name
                        val size = if (sizeC != -1) cursor.getLong(sizeC) else 0L
                        val dur = if (durC != -1) cursor.getLong(durC) else 0L

                        list.add(SearchItemModel(id, title, path, size, dur, true))
                    }
                }

                // 2. Fetch Audio / Music
                val audProj = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.SIZE,
                    MediaStore.Audio.Media.DURATION
                )
                resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audProj, null, null, null)?.use { cursor ->
                    val idC = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                    val nameC = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                    val titleC = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                    val dataC = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    val sizeC = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                    val durC = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)

                    while (cursor.moveToNext()) {
                        val id = if (idC != -1) cursor.getLong(idC) else 0L
                        val rawPath = if (dataC != -1) cursor.getString(dataC) else null
                        val path = if (!rawPath.isNullOrEmpty()) rawPath else ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
                        val title = if (titleC != -1) cursor.getString(titleC) else null
                        val finalTitle = if (!title.isNullOrEmpty()) title else if (nameC != -1) cursor.getString(nameC) ?: File(path).name else File(path).name
                        val size = if (sizeC != -1) cursor.getLong(sizeC) else 0L
                        val dur = if (durC != -1) cursor.getLong(durC) else 0L

                        list.add(SearchItemModel(id, finalTitle, path, size, dur, false))
                    }
                }

                activity?.runOnUiThread {
                    if (!isAdded || context == null) return@runOnUiThread
                    masterMediaList.clear()
                    masterMediaList.addAll(list)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
