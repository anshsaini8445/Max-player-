package com.app.mediaplayer

import android.content.ContentUris
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
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.Locale
import kotlin.concurrent.thread

class SearchFragment : Fragment() {

    private lateinit var etSearchInput: EditText
    private lateinit var tvFilterAll: TextView
    private lateinit var tvFilterVideos: TextView
    private lateinit var tvFilterAudio: TextView
    private lateinit var rvSearchResults: RecyclerView
    private lateinit var tvEmptySearch: TextView

    private val allVideos = mutableListOf<VideoModel>()
    private val allAudios = mutableListOf<AudioTrackModel>()
    private val filteredVideos = mutableListOf<VideoModel>()
    private val filteredAudios = mutableListOf<AudioTrackModel>()

    private var activeFilter = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val d = ctx.resources.displayMetrics.density

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0A10"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), 0)
        }

        etSearchInput = EditText(ctx).apply {
            hint = "Search videos, movies, songs, artists..."
            setHintTextColor(Color.parseColor("#888899"))
            setTextColor(Color.WHITE)
            textSize = 14.5f
            setSingleLine(true)
            setPadding((16 * d).toInt(), (12 * d).toInt(), (16 * d).toInt(), (12 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#181826"))
                cornerRadius = 24f * d
                setStroke((1 * d).toInt(), Color.parseColor("#00FFFF"))
            }
        }
        root.addView(etSearchInput)

        val filterRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (12 * d).toInt(), 0, (10 * d).toInt())
        }

        tvFilterVideos = createFilterPill("Videos", true, d) {
            activeFilter = 0
            updateFilterPills()
            runSearchQuery(etSearchInput.text.toString())
        }
        tvFilterAudio = createFilterPill("Music / Audio", false, d) {
            activeFilter = 1
            updateFilterPills()
            runSearchQuery(etSearchInput.text.toString())
        }
        tvFilterAll = tvFilterVideos

        filterRow.addView(tvFilterVideos)
        filterRow.addView(tvFilterAudio)
        root.addView(filterRow)

        rvSearchResults = RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        root.addView(rvSearchResults)

        tvEmptySearch = TextView(ctx).apply {
            text = "Type above to search your media library"
            setTextColor(Color.parseColor("#888899"))
            textSize = 14f
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        root.addView(tvEmptySearch)

        etSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                runSearchQuery(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        loadSearchableMediaIndex()
        return root
    }

    private fun createFilterPill(
        label: String,
        selected: Boolean,
        d: Float,
        onClick: () -> Unit
    ): TextView {
        return TextView(requireContext()).apply {
            text = label
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((16 * d).toInt(), (7 * d).toInt(), (16 * d).toInt(), (7 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (10 * d).toInt() }
            setTextColor(if (selected) Color.BLACK else Color.WHITE)
            background = GradientDrawable().apply {
                setColor(if (selected) Color.parseColor("#00FFFF") else Color.parseColor("#1E1E2E"))
                cornerRadius = 18f * d
            }
            setOnClickListener { onClick() }
        }
    }

    private fun updateFilterPills() {
        val d = resources.displayMetrics.density
        val isVideo = activeFilter == 0
        tvFilterVideos.setTextColor(if (isVideo) Color.BLACK else Color.WHITE)
        tvFilterVideos.background = GradientDrawable().apply {
            setColor(if (isVideo) Color.parseColor("#00FFFF") else Color.parseColor("#1E1E2E"))
            cornerRadius = 18f * d
        }
        tvFilterAudio.setTextColor(if (!isVideo) Color.BLACK else Color.WHITE)
        tvFilterAudio.background = GradientDrawable().apply {
            setColor(if (!isVideo) Color.parseColor("#00FFFF") else Color.parseColor("#1E1E2E"))
            cornerRadius = 18f * d
        }
    }

    private fun loadSearchableMediaIndex() {
        val ctx = context ?: return
        thread {
            val vList = mutableListOf<VideoModel>()
            val aList = mutableListOf<AudioTrackModel>()
            try {
                val vProj = arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.DATA,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.DURATION
                )
                ctx.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    vProj,
                    null,
                    null,
                    "${MediaStore.Video.Media.DATE_ADDED} DESC"
                )?.use { c ->
                    val idC = c.getColumnIndex(MediaStore.Video.Media._ID)
                    val nameC = c.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                    val dataC = c.getColumnIndex(MediaStore.Video.Media.DATA)
                    val sizeC = c.getColumnIndex(MediaStore.Video.Media.SIZE)
                    val durC = c.getColumnIndex(MediaStore.Video.Media.DURATION)
                    while (c.moveToNext()) {
                        val id = if (idC != -1) c.getLong(idC) else 0L
                        val raw = if (dataC != -1) c.getString(dataC) else null
                        val path = if (!raw.isNullOrEmpty()) raw else ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            id
                        ).toString()
                        val title = if (nameC != -1) c.getString(nameC) ?: File(path).name else File(path).name
                        val size = if (sizeC != -1) c.getLong(sizeC) else 0L
                        val dur = if (durC != -1) c.getLong(durC) else 0L
                        vList.add(VideoModel(id, title, path, size, dur))
                    }
                }

                val aProj = arrayOf(
                    MediaStore.Audio.Media._ID,
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
                    val nameC = c.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                    val artC = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                    val albC = c.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                    val dataC = c.getColumnIndex(MediaStore.Audio.Media.DATA)
                    val sizeC = c.getColumnIndex(MediaStore.Audio.Media.SIZE)
                    val durC = c.getColumnIndex(MediaStore.Audio.Media.DURATION)
                    while (c.moveToNext()) {
                        val id = if (idC != -1) c.getLong(idC) else 0L
                        val raw = if (dataC != -1) c.getString(dataC) else null
                        val path = if (!raw.isNullOrEmpty()) raw else ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            id
                        ).toString()
                        val title = if (nameC != -1) c.getString(nameC) ?: File(path).name else File(path).name
                        val artist = if (artC != -1) c.getString(artC) ?: "Artist" else "Artist"
                        val album = if (albC != -1) c.getString(albC) ?: "Album" else "Album"
                        val size = if (sizeC != -1) c.getLong(sizeC) else 0L
                        val dur = if (durC != -1) c.getLong(durC) else 0L
                        if (dur >= 1000L) {
                            aList.add(AudioTrackModel(id, title, artist, album, path, size, dur))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                allVideos.clear()
                allVideos.addAll(vList)
                allAudios.clear()
                allAudios.addAll(aList)
                runSearchQuery(etSearchInput.text.toString())
            }
        }
    }

    private fun runSearchQuery(queryText: String) {
        val ctx = context ?: return
        val q = queryText.trim().lowercase(Locale.getDefault())

        if (activeFilter == 0) {
            filteredVideos.clear()
            if (q.isEmpty()) {
                filteredVideos.addAll(allVideos)
            } else {
                filteredVideos.addAll(allVideos.filter { it.title.lowercase(Locale.getDefault()).contains(q) })
            }
            rvSearchResults.adapter = VideoAdapter(ctx, filteredVideos)
            val empty = filteredVideos.isEmpty()
            rvSearchResults.visibility = if (empty) View.GONE else View.VISIBLE
            tvEmptySearch.visibility = if (empty) View.VISIBLE else View.GONE
        } else {
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
            rvSearchResults.adapter = AudioAdapter(ctx, filteredAudios)
            val empty = filteredAudios.isEmpty()
            rvSearchResults.visibility = if (empty) View.GONE else View.VISIBLE
            tvEmptySearch.visibility = if (empty) View.VISIBLE else View.GONE
        }
    }
}
