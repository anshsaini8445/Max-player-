package com.app.mediaplayer

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.ArrayList
import java.util.Locale
import kotlin.concurrent.thread

class SearchFragment : Fragment() {

    private lateinit var rootSearchLayout: LinearLayout
    private lateinit var pillSearchVideos: TextView
    private lateinit var pillSearchAudio: TextView
    private lateinit var etSearchInput: EditText
    private lateinit var rvSearchResults: RecyclerView
    private lateinit var tvEmptySearch: TextView

    private val allVideos = mutableListOf<VideoModel>()
    private val filteredVideos = mutableListOf<VideoModel>()

    private val allSongs = mutableListOf<AudioItem>()
    private val filteredSongs = mutableListOf<AudioItem>()

    private lateinit var videoAdapter: VideoAdapter
    private var isVideoTabActive = true
    private var currentQuery = ""
    private var accentColor = Color.parseColor("#00FFFF")

    data class AudioItem(
        val id: Long,
        val title: String,
        val artist: String,
        val path: String,
        val durationMs: Long
    )

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

        // Top Search Input Bar with Cyan Border
        val searchBoxRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * d).toInt(), (4 * d).toInt(), (10 * d).toInt(), (4 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (48 * d).toInt()
            ).apply { bottomMargin = (12 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(Color.parseColor(theme.cardHex))
                cornerRadius = 24f * d
                setStroke((1.5f * d).toInt(), accentColor)
            }
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
            text = "Clear"
            setTextColor(accentColor)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((10 * d).toInt(), (6 * d).toInt(), (10 * d).toInt(), (6 * d).toInt())
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
                applyFilterNow()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        searchBoxRow.addView(etSearchInput)
        searchBoxRow.addView(btnClearSearch)
        rootSearchLayout.addView(searchBoxRow)

        // Category Toggle Pills Row (Videos vs Music / Audio with Instant Counts)
        val pillsRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * d).toInt() }
        }

        pillSearchVideos = TextView(ctx).apply {
            text = "Videos (0)"
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
                applyFilterNow()
            }
        }

        pillSearchAudio = TextView(ctx).apply {
            text = "Music / Audio (0)"
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
                applyFilterNow()
            }
        }

        pillsRow.addView(pillSearchVideos)
        pillsRow.addView(pillSearchAudio)
        rootSearchLayout.addView(pillsRow)

        rvSearchResults = RecyclerView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            layoutManager = LinearLayoutManager(ctx)
            setHasFixedSize(true)
        }
        videoAdapter = VideoAdapter(ctx, filteredVideos)
        rvSearchResults.adapter = videoAdapter
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

        updateTabPillVisuals(d)

        // Instant Memory Cache Population so Videos (count) is never stuck at 0
        val cachedVids = DeepMediaScanner.getCachedVideosInstant()
        if (cachedVids.isNotEmpty()) {
            allVideos.clear()
            allVideos.addAll(cachedVids)
            applyFilterNow()
        }
        loadVideosAndAudioInBackground()

        return rootSearchLayout
    }

    override fun onResume() {
        super.onResume()
        loadVideosAndAudioInBackground()
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
            val scannedAudio = queryAllAudioFilesFast(ctx)

            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                allVideos.clear()
                allVideos.addAll(scannedVideos)
                allSongs.clear()
                allSongs.addAll(scannedAudio)
                applyFilterNow()
            }
        }
    }

    private fun applyFilterNow() {
        val ctx = context ?: return
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
            bindAudioSearchResultsAdapter(ctx)

            if (filteredSongs.isEmpty()) {
                rvSearchResults.visibility = View.GONE
                tvEmptySearch.visibility = View.VISIBLE
            } else {
                tvEmptySearch.visibility = View.GONE
                rvSearchResults.visibility = View.VISIBLE
            }
        }
    }

    private fun bindAudioSearchResultsAdapter(ctx: Context) {
        val d = ctx.resources.displayMetrics.density
        val theme = ThemeStudioHelper.getCurrentTheme(ctx)

        rvSearchResults.adapter = object : RecyclerView.Adapter<AudioSearchVH>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudioSearchVH {
                val card = CardView(ctx).apply {
                    radius = 14f * d
                    cardElevation = 3f * d
                    setCardBackgroundColor(Color.parseColor(theme.cardHex))
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins((4 * d).toInt(), (5 * d).toInt(), (4 * d).toInt(), (5 * d).toInt())
                    }
                }

                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
                }

                val badge = TextView(ctx).apply {
                    text = "MP3"
                    setTextColor(Color.parseColor("#0A0A10"))
                    textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams((42 * d).toInt(), (42 * d).toInt()).apply {
                        marginEnd = (12 * d).toInt()
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(accentColor)
                    }
                }

                val textCol = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                val tvTitle = TextView(ctx).apply {
                    setTextColor(Color.WHITE)
                    textSize = 14.5f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }

                val tvSub = TextView(ctx).apply {
                    setTextColor(accentColor)
                    textSize = 12f
                    maxLines = 1
                    setPadding(0, (3 * d).toInt(), 0, 0)
                }

                textCol.addView(tvTitle)
                textCol.addView(tvSub)
                row.addView(badge)
                row.addView(textCol)
                card.addView(row)
                return AudioSearchVH(card, tvTitle, tvSub)
            }

            override fun onBindViewHolder(holder: AudioSearchVH, position: Int) {
                val song = filteredSongs[position]
                holder.tvTitle.text = song.title
                holder.tvSub.text = song.artist

                holder.itemView.setOnClickListener {
                    try {
                        val allPaths = ArrayList(filteredSongs.map { it.path })
                        val allTitles = ArrayList(filteredSongs.map { it.title })
                        val intent = Intent(ctx, AudioPlayerActivity::class.java).apply {
                            putExtra("AUDIO_PATH", song.path)
                            putExtra("AUDIO_TITLE", song.title)
                            putExtra("AUDIO_ARTIST", song.artist)
                            putStringArrayListExtra("AUDIO_LIST", allPaths)
                            putStringArrayListExtra("AUDIO_TITLES", allTitles)
                            putExtra("START_INDEX", position)
                        }
                        startActivity(intent)
                    } catch (_: Exception) {
                        val fallbackIntent = Intent(ctx, PlayerActivity::class.java).apply {
                            putExtra("VIDEO_PATH", song.path)
                            putExtra("VIDEO_TITLE", song.title)
                        }
                        startActivity(fallbackIntent)
                    }
                }
            }

            override fun getItemCount(): Int = filteredSongs.size
        }
    }

    private class AudioSearchVH(
        view: View,
        val tvTitle: TextView,
        val tvSub: TextView
    ) : RecyclerView.ViewHolder(view)

    private fun queryAllAudioFilesFast(context: Context): List<AudioItem> {
        val list = mutableListOf<AudioItem>()
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

                    val title = (if (titleCol != -1) cursor.getString(titleCol) else null)
                        ?: (if (nameCol != -1) cursor.getString(nameCol) else null)
                        ?: File(path).name
                    val artist = (if (artistCol != -1) cursor.getString(artistCol) else null)
                        ?.takeIf { it != "<unknown>" } ?: "OG 8K Audio"

                    list.add(AudioItem(id, title, artist, path, dur))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
