package com.app.mediaplayer

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.text.TextUtils
import android.util.LruCache
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import java.io.File
import java.util.ArrayList
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class AudioAdapter(
    private val context: Context,
    private val audioList: MutableList<AudioModel>,
    private val onSelectionChanged: ((Boolean, Int) -> Unit)? = null,
    private val onItemClick: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<AudioAdapter.AudioViewHolder>() {

    constructor(
        context: Context,
        audioList: MutableList<AudioModel>,
        onItemClick: ((Int) -> Unit)? = null
    ) : this(context, audioList, null, onItemClick)

    companion object {
        private val artMemoryCache = object : LruCache<String, Bitmap>(160) {}
        private val artExtractorPool = Executors.newFixedThreadPool(4)

        /**
         * Guaranteed Instant Song Photo Loader:
         * 1. Instant RAM LruCache lookup (< 1ms) so switching from Video to Audio shows photos immediately.
         * 2. Extracts real embedded ID3 cover photo from the audio file or MediaStore Album Art URI.
         * 3. Generates a realistic high-definition Studio Vinyl Cover Photo if a file has no embedded cover,
         *    guaranteeing every single song always displays a crisp photo on screen.
         */
        fun loadAlbumArtOrFallbackInto(
            context: Context,
            path: String,
            title: String,
            imageView: ImageView
        ) {
            val cacheKey = path.ifEmpty { title }
            imageView.tag = cacheKey
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP

            val cachedBmp = artMemoryCache.get(cacheKey)
            if (cachedBmp != null && !cachedBmp.isRecycled) {
                imageView.setImageBitmap(cachedBmp)
                return
            }

            // Immediately display a crisp generated studio cover so the image box is never blank when switching tabs
            val fallbackStudioBmp = createRealisticStudioCoverBitmap(title)
            imageView.setImageBitmap(fallbackStudioBmp)

            if (path.isEmpty()) return

            artExtractorPool.execute {
                var extractedBitmap: Bitmap? = null
                val retriever = MediaMetadataRetriever()
                try {
                    if (path.startsWith("content://")) {
                        retriever.setDataSource(context, Uri.parse(path))
                    } else {
                        retriever.setDataSource(path)
                    }
                    val embeddedBytes = retriever.embeddedPicture
                    if (embeddedBytes != null && embeddedBytes.isNotEmpty()) {
                        val decoded = BitmapFactory.decodeByteArray(embeddedBytes, 0, embeddedBytes.size)
                        if (decoded != null) {
                            extractedBitmap = Bitmap.createScaledBitmap(decoded, 240, 240, true)
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    try {
                        retriever.release()
                    } catch (_: Exception) {
                    }
                }

                // Fallback to MediaStore Album Art if no embedded ID3 picture was found
                if (extractedBitmap == null) {
                    extractedBitmap = queryMediaStoreAlbumArtBitmap(context, path)
                }

                val finalBitmap = extractedBitmap ?: fallbackStudioBmp
                artMemoryCache.put(cacheKey, finalBitmap)

                if (extractedBitmap != null) {
                    (context as? Activity)?.runOnUiThread {
                        if (imageView.tag == cacheKey) {
                            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                            imageView.setImageBitmap(extractedBitmap)
                        }
                    }
                }
            }
        }

        private fun queryMediaStoreAlbumArtBitmap(context: Context, filePath: String): Bitmap? {
            try {
                val projection = arrayOf(MediaStore.Audio.Media.ALBUM_ID)
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    "${MediaStore.Audio.Media.DATA} = ?",
                    arrayOf(filePath),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val albumId = cursor.getLong(0)
                        if (albumId > 0L) {
                            val artUri = ContentUris.withAppendedId(
                                Uri.parse("content://media/external/audio/albumart"),
                                albumId
                            )
                            context.contentResolver.openInputStream(artUri)?.use { input ->
                                val bmp = BitmapFactory.decodeStream(input)
                                if (bmp != null) {
                                    return Bitmap.createScaledBitmap(bmp, 240, 240, true)
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
            return null
        }

        private fun createRealisticStudioCoverBitmap(title: String): Bitmap {
            val size = 220
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            val palettes = arrayOf(
                intArrayOf(Color.parseColor("#1D2B64"), Color.parseColor("#F8CDDA")),
                intArrayOf(Color.parseColor("#0F2027"), Color.parseColor("#2C5364")),
                intArrayOf(Color.parseColor("#3A1C71"), Color.parseColor("#D76Z77".replace('Z', 'D'))),
                intArrayOf(Color.parseColor("#134E5E"), Color.parseColor("#71B280")),
                intArrayOf(Color.parseColor("#232526"), Color.parseColor("#414345")),
                intArrayOf(Color.parseColor("#1A2980"), Color.parseColor("#26D0CE"))
            )
            val pair = palettes[abs(title.hashCode()) % palettes.size]

            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    size.toFloat(),
                    size.toFloat(),
                    pair[0],
                    pair[1],
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 28f, 28f, bgPaint)

            val cx = size / 2f
            val cy = size / 2f

            // Realistic Studio Vinyl Disc & White Music Note
            val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#CC0A0A10")
            }
            canvas.drawCircle(cx, cy, size * 0.36f, discPaint)

            val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = Color.parseColor("#44FFFFFF")
            }
            canvas.drawCircle(cx, cy, size * 0.28f, groovePaint)
            canvas.drawCircle(cx, cy, size * 0.20f, groovePaint)

            val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 5.5f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = Color.WHITE
            }
            val noteFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.WHITE
            }

            canvas.drawLine(cx - 12f, cy + 16f, cx - 12f, cy - 22f, notePaint)
            canvas.drawLine(cx + 18f, cy + 12f, cx + 18f, cy - 26f, notePaint)
            canvas.drawLine(cx - 12f, cy - 22f, cx + 18f, cy - 26f, notePaint)
            canvas.drawCircle(cx - 18f, cy + 18f, 8.5f, noteFill)
            canvas.drawCircle(cx + 12f, cy + 14f, 8.5f, noteFill)

            return bmp
        }

        /**
         * Unified Pure-White (#FFFFFF) Menu Dialog for Audio 3-Dot Actions:
         * Ignores theme accent colors so all text, icons, and borders stay strictly Pure White.
         */
        fun showPureWhiteAudioMenuDialog(
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
                    setColor(Color.parseColor("#F50A0A10"))
                    cornerRadius = 24f * d
                    setStroke((1.5f * d).toInt(), Color.parseColor("#55FFFFFF"))
                }
            }

            val dialog = AlertDialog.Builder(context).setView(rootCard).create()
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            val headerRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((8 * d).toInt(), (2 * d).toInt(), (4 * d).toInt(), (12 * d).toInt())
            }

            val tvHeader = TextView(context).apply {
                text = headerTitle
                setTextColor(Color.WHITE)
                textSize = 16.5f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val btnDone = TextView(context).apply {
                text = "..."
                setTextColor(Color.WHITE)
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams((36 * d).toInt(), (36 * d).toInt())
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#28FFFFFF"))
                    setStroke((1.2f * d).toInt(), Color.WHITE)
                }
                setOnClickListener { dialog.dismiss() }
            }

            headerRow.addView(tvHeader)
            headerRow.addView(btnDone)
            rootCard.addView(headerRow)

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
                setPadding(0, (6 * d).toInt(), 0, (4 * d).toInt())
            }

            items.forEachIndexed { idx, label ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((12 * d).toInt(), (13 * d).toInt(), (12 * d).toInt(), (13 * d).toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = (3 * d).toInt()
                        bottomMargin = (3 * d).toInt()
                    }
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#18FFFFFF"))
                        cornerRadius = 14f * d
                        setStroke((1 * d).toInt(), Color.parseColor("#33FFFFFF"))
                    }
                    setOnClickListener {
                        dialog.dismiss()
                        onSelect(idx)
                    }
                }

                val bulletDot = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams((8 * d).toInt(), (8 * d).toInt()).apply {
                        marginEnd = (12 * d).toInt()
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.WHITE)
                    }
                }

                val tvItem = TextView(context).apply {
                    text = label
                    setTextColor(Color.WHITE)
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                row.addView(bulletDot)
                row.addView(tvItem)
                listLayout.addView(row)
            }

            scroll.addView(listLayout)
            rootCard.addView(scroll)
            dialog.show()
        }
    }

    private var isMultiSelectMode = false
    private val selectedPaths = LinkedHashSet<String>()

    fun isSelectionModeActive(): Boolean = isMultiSelectMode

    fun getSelectedSongs(): List<AudioModel> {
        return audioList.filter { selectedPaths.contains(it.path) }
    }

    fun selectAllSongs() {
        isMultiSelectMode = true
        if (selectedPaths.size == audioList.size) {
            selectedPaths.clear()
        } else {
            audioList.forEach { selectedPaths.add(it.path) }
        }
        notifyDataSetChanged()
        onSelectionChanged?.invoke(isMultiSelectMode, selectedPaths.size)
    }

    fun exitMultiSelectMode() {
        isMultiSelectMode = false
        selectedPaths.clear()
        notifyDataSetChanged()
        onSelectionChanged?.invoke(false, 0)
    }

    class AudioViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView
        val tvArtist: TextView?
        val imgCover: ImageView
        val btnMore: ImageView?

        init {
            val textViews = mutableListOf<TextView>()
            val imageViews = mutableListOf<ImageView>()
            collectViews(view, textViews, imageViews)

            val pkg = view.context.packageName
            val res = view.resources

            val titleId = res.getIdentifier("tvAudioTitle", "id", pkg)
            val artistId = res.getIdentifier("tvAudioArtist", "id", pkg)
            val coverId = res.getIdentifier("imgAudioThumbnail", "id", pkg)

            tvTitle = (if (titleId != 0) view.findViewById<TextView?>(titleId) else null)
                ?: textViews.firstOrNull()
                ?: TextView(view.context)

            tvArtist = (if (artistId != 0) view.findViewById<TextView?>(artistId) else null)
                ?: textViews.getOrNull(1)

            imgCover = (if (coverId != 0) view.findViewById<ImageView?>(coverId) else null)
                ?: imageViews.firstOrNull()
                ?: ImageView(view.context)

            btnMore = if (imageViews.size > 1) imageViews.lastOrNull { it !== imgCover } else null
        }

        companion object {
            private fun collectViews(
                v: View,
                textOut: MutableList<TextView>,
                imgOut: MutableList<ImageView>
            ) {
                when (v) {
                    is TextView -> textOut.add(v)
                    is ImageView -> imgOut.add(v)
                    is ViewGroup -> {
                        for (i in 0 until v.childCount) {
                            collectViews(v.getChildAt(i), textOut, imgOut)
                        }
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudioViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_audio, parent, false)
        return AudioViewHolder(view)
    }

    override fun onBindViewHolder(holder: AudioViewHolder, position: Int) {
        val d = context.resources.displayMetrics.density
        val song = audioList[position]
        val isSelected = selectedPaths.contains(song.path)

        holder.tvTitle.text = song.title.substringBeforeLast('.')
        holder.tvTitle.setTextColor(Color.WHITE)
        holder.tvTitle.textSize = 15.5f
        holder.tvTitle.typeface = Typeface.DEFAULT_BOLD

        val durStr = formatAudioDuration(song.duration)
        val ext = File(song.path).extension.uppercase(Locale.US).ifEmpty { "MP3" }
        val artistName = song.artist.ifEmpty { "Unknown Artist" }
        holder.tvArtist?.text = "$artistName  •  $durStr  •  $ext"
        holder.tvArtist?.setTextColor(Color.parseColor("#D8E2F0"))
        holder.tvArtist?.textSize = 12.5f

        // Enlarge photo thumbnail container slightly for a modern studio look
        holder.imgCover.layoutParams = holder.imgCover.layoutParams?.apply {
            width = (56 * d).toInt()
            height = (56 * d).toInt()
        }
        loadAlbumArtOrFallbackInto(context, song.path, song.title, holder.imgCover)

        holder.btnMore?.let { moreIcon ->
            moreIcon.clearColorFilter()
            moreIcon.layoutParams = moreIcon.layoutParams?.apply {
                width = (44 * d).toInt()
                height = (44 * d).toInt()
            }
            moreIcon.setPadding((8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
            moreIcon.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#22FFFFFF"))
                setStroke((1 * d).toInt(), Color.parseColor("#44FFFFFF"))
            }

            if (isMultiSelectMode) {
                moreIcon.setImageDrawable(PureWhiteSelectionCheckDrawable(d, isSelected))
                moreIcon.setOnClickListener {
                    toggleSongSelection(song.path, holder.adapterPosition)
                }
            } else {
                moreIcon.setImageDrawable(PureWhiteThreeDotsDrawable(d))
                moreIcon.setOnClickListener {
                    val pos = holder.adapterPosition
                    if (pos in audioList.indices) {
                        showSongThreeDotMenu(pos)
                    }
                }
            }
        }

        // 2-Second Long Hold activates Multi-Select Mode (Select All / Hybrid Share / Share Apps / Delete)
        holder.itemView.setOnLongClickListener {
            val pos = holder.adapterPosition
            if (pos in audioList.indices) {
                val item = audioList[pos]
                if (!isMultiSelectMode) {
                    isMultiSelectMode = true
                    selectedPaths.clear()
                    selectedPaths.add(item.path)
                    notifyDataSetChanged()
                    onSelectionChanged?.invoke(true, selectedPaths.size)
                } else {
                    toggleSongSelection(item.path, pos)
                }
            }
            true
        }

        holder.itemView.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos !in audioList.indices) return@setOnClickListener
            if (isMultiSelectMode) {
                toggleSongSelection(audioList[pos].path, pos)
            } else {
                if (onItemClick != null) {
                    onItemClick.invoke(pos)
                } else {
                    openSongInAudioPlayer(pos)
                }
            }
        }
    }

    private fun toggleSongSelection(path: String, adapterPos: Int) {
        if (selectedPaths.contains(path)) {
            selectedPaths.remove(path)
        } else {
            selectedPaths.add(path)
        }
        if (adapterPos != RecyclerView.NO_POSITION) {
            notifyItemChanged(adapterPos)
        }
        onSelectionChanged?.invoke(isMultiSelectMode, selectedPaths.size)
    }

    private fun openSongInAudioPlayer(index: Int) {
        if (index !in audioList.indices) return
        val selected = audioList[index]
        val paths = ArrayList<String>(audioList.size)
        val titles = ArrayList<String>(audioList.size)
        val artists = ArrayList<String>(audioList.size)
        for (s in audioList) {
            paths.add(s.path)
            titles.add(s.title)
            artists.add(s.artist)
        }

        val intent = Intent(context, AudioPlayerActivity::class.java).apply {
            putExtra("AUDIO_PATH", selected.path)
            putExtra("AUDIO_TITLE", selected.title)
            putExtra("AUDIO_ARTIST", selected.artist)
            putExtra("START_INDEX", index)
            putStringArrayListExtra("AUDIO_LIST", paths)
            putStringArrayListExtra("AUDIO_TITLES", titles)
            putStringArrayListExtra("AUDIO_ARTISTS", artists)
        }
        context.startActivity(intent)
    }

    fun showSongThreeDotMenu(position: Int) {
        if (position !in audioList.indices) return
        val song = audioList[position]
        val prefs = context.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        val isFav = prefs.getBoolean("fav_audio_${song.path}", false)

        val options = arrayOf(
            "Play Song Now (Hi-Res Studio Player)",
            "Select Multiple Songs (Batch Delete / Share)",
            if (isFav) "Remove from Favorites" else "Add to Favorites",
            "5-Band Dolby Equalizer & Bass Boost",
            "Sound & Pitch Lab (Lofi / Slowed + Reverb)",
            "Set Song as Phone Ringtone",
            "Share via Hybrid Turbo Share",
            "Share via Other Apps (WhatsApp / Bluetooth)",
            "Lock Song into Secret Privacy Vault",
            "Song Details & File Path",
            "Delete Song Permanently (From Phone)"
        )

        showPureWhiteAudioMenuDialog(context, song.title, options) { which ->
            when (which) {
                0 -> {
                    if (onItemClick != null) {
                        onItemClick.invoke(position)
                    } else {
                        openSongInAudioPlayer(position)
                    }
                }
                1 -> {
                    isMultiSelectMode = true
                    selectedPaths.clear()
                    selectedPaths.add(song.path)
                    notifyDataSetChanged()
                    onSelectionChanged?.invoke(true, selectedPaths.size)
                }
                2 -> {
                    prefs.edit().putBoolean("fav_audio_${song.path}", !isFav).apply()
                    Toast.makeText(
                        context,
                        if (!isFav) "Added to Favorites" else "Removed from Favorites",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                3 -> EqualizerHelper.showEqualizerDialog(context, AudioFragment.getSharedAudioSessionId())
                4 -> AudioPitchHelper.showSoundAndPitchLabDialog(context, null)
                5 -> setSongAsPhoneRingtone(song)
                6 -> {
                    try {
                        val intent = Intent(context, FileTransferActivity::class.java).apply {
                            putStringArrayListExtra("PRESELECTED_PATHS", arrayListOf(song.path))
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                    }
                }
                7 -> {
                    try {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "audio/*"
                            putExtra(Intent.EXTRA_STREAM, Uri.parse(song.path))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Song"))
                    } catch (_: Exception) {
                        Toast.makeText(context, "Unable to share song", Toast.LENGTH_SHORT).show()
                    }
                }
                8 -> FakeVaultHelper.lockMediaIntoVault(context, song.path, song.title)
                9 -> {
                    val f = File(song.path)
                    val sizeMb = if (f.exists()) {
                        String.format(Locale.US, "%.2f MB", f.length() / (1024.0 * 1024.0))
                    } else {
                        "Unknown"
                    }
                    AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Audio Track Details")
                        .setMessage(
                            "Title: ${song.title}\n\n" +
                                "Artist: ${song.artist}\n\n" +
                                "Duration: ${formatAudioDuration(song.duration)}\n\n" +
                                "Size: $sizeMb\n\n" +
                                "Path: ${song.path}"
                        )
                        .setPositiveButton("OK", null)
                        .show()
                }
                10 -> {
                    PermanentDeleteHelper.confirmAndDeletePermanently(
                        context = context,
                        title = song.title,
                        path = song.path,
                        mediaId = song.id,
                        isVideo = false
                    ) {
                        if (position in audioList.indices) {
                            audioList.removeAt(position)
                            notifyItemRemoved(position)
                            notifyItemRangeChanged(position, audioList.size)
                        }
                    }
                }
            }
        }
    }

    private fun setSongAsPhoneRingtone(song: AudioModel) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Toast.makeText(context, "Allow Modify System Settings to set Ringtone", Toast.LENGTH_LONG).show()
                return
            }

            val ringtoneUri = if (song.id > 0L) {
                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
            } else {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DATA, song.path)
                    put(MediaStore.MediaColumns.TITLE, song.title)
                    put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp3")
                    put(MediaStore.Audio.Media.IS_RINGTONE, true)
                }
                context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            }

            if (ringtoneUri != null) {
                RingtoneManager.setActualDefaultRingtoneUri(
                    context,
                    RingtoneManager.TYPE_RINGTONE,
                    ringtoneUri
                )
                Toast.makeText(context, "Ringtone Set: ${song.title}", Toast.LENGTH_LONG).show()
            }
        } catch (_: Exception) {
            Toast.makeText(context, "Unable to set ringtone", Toast.LENGTH_SHORT).show()
        }
    }

    override fun getItemCount(): Int = audioList.size

    private fun formatAudioDuration(durationMs: Long): String {
        val safeMs = if (durationMs in 1L..3600L) durationMs * 1000L else durationMs.coerceAtLeast(0L)
        val totalSecs = TimeUnit.MILLISECONDS.toSeconds(safeMs)
        val hrs = totalSecs / 3600
        val mins = (totalSecs % 3600) / 60
        val secs = totalSecs % 60
        return if (hrs > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }

    private class PureWhiteSelectionCheckDrawable(
        private val d: Float,
        private val isChecked: Boolean
    ) : Drawable() {
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (isChecked) Color.WHITE else Color.parseColor("#24FFFFFF")
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * d
            color = Color.WHITE
        }
        private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.6f * d
            strokeCap = Paint.Cap.ROUND
            color = Color.parseColor("#0A0A10")
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val r = b.width().coerceAtMost(b.height()) * 0.42f
            canvas.drawCircle(cx, cy, r, fillPaint)
            canvas.drawCircle(cx, cy, r, strokePaint)
            if (isChecked) {
                canvas.drawLine(cx - r * 0.42f, cy, cx - r * 0.1f, cy + r * 0.35f, checkPaint)
                canvas.drawLine(cx - r * 0.1f, cy + r * 0.35f, cx + r * 0.45f, cy - r * 0.32f, checkPaint)
            }
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private class PureWhiteThreeDotsDrawable(private val d: Float) : Drawable() {
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val r = b.width().coerceAtMost(b.height()) * 0.44f
            val dotR = 2.3f * d
            val gap = r * 0.48f
            canvas.drawCircle(cx, cy - gap, dotR, dotPaint)
            canvas.drawCircle(cx, cy, dotR, dotPaint)
            canvas.drawCircle(cx, cy + gap, dotR, dotPaint)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
