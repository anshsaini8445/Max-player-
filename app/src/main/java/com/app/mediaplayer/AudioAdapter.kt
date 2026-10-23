package com.app.mediaplayer

import android.app.Activity
import android.app.AlertDialog
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
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.ArrayList
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AudioAdapter(
    private val context: Context,
    private val audioList: MutableList<AudioModel>,
    private val onSongClicked: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<AudioAdapter.AudioViewHolder>() {

    companion object {
        private val artCache = object : LruCache<String, Bitmap>(80) {}
        private val noArtSet = HashSet<String>()
        private val thumbExecutor = Executors.newFixedThreadPool(3)

        fun loadAlbumArtOrFallbackInto(
            context: Context,
            path: String,
            seedTitle: String,
            targetView: ImageView
        ) {
            val d = context.resources.displayMetrics.density
            targetView.tag = path
            val cachedBmp = artCache.get(path)
            if (cachedBmp != null && !cachedBmp.isRecycled) {
                targetView.scaleType = ImageView.ScaleType.CENTER_CROP
                targetView.setImageBitmap(cachedBmp)
                return
            }

            // Show rich stylized Album Art Emblem immediately so it NEVER shows a blank cyan box
            targetView.scaleType = ImageView.ScaleType.FIT_XY
            targetView.setImageDrawable(StylizedSongArtFallbackDrawable(seedTitle, d))

            if (noArtSet.contains(path)) return

            thumbExecutor.execute {
                var extracted: Bitmap? = null
                val retriever = MediaMetadataRetriever()
                try {
                    if (path.startsWith("content://")) {
                        retriever.setDataSource(context, Uri.parse(path))
                    } else {
                        retriever.setDataSource(path)
                    }
                    val bytes = retriever.embeddedPicture
                    if (bytes != null && bytes.isNotEmpty()) {
                        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                        extracted = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    }
                } catch (_: Exception) {
                } finally {
                    try {
                        retriever.release()
                    } catch (_: Exception) {
                    }
                }

                if (extracted != null) {
                    artCache.put(path, extracted)
                    (context as? Activity)?.runOnUiThread {
                        if (targetView.tag == path) {
                            targetView.scaleType = ImageView.ScaleType.CENTER_CROP
                            targetView.setImageBitmap(extracted)
                        }
                    }
                } else {
                    noArtSet.add(path)
                }
            }
        }
    }

    class AudioViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAudioTitle: TextView = view.findViewById(R.id.tvAudioTitle)
        val tvAudioArtistAndDuration: TextView = view.findViewById(R.id.tvAudioArtistAndDuration)
        val imgCover: ImageView? = findFirstImageView(view, isFirst = true)
        val btnMore: ImageView? = findFirstImageView(view, isFirst = false)

        companion object {
            private fun findFirstImageView(root: View, isFirst: Boolean): ImageView? {
                val found = mutableListOf<ImageView>()
                collectImageViews(root, found)
                return if (isFirst) found.firstOrNull() else found.lastOrNull()
            }

            private fun collectImageViews(v: View, out: MutableList<ImageView>) {
                if (v is ImageView) {
                    out.add(v)
                } else if (v is ViewGroup) {
                    for (i in 0 until v.childCount) {
                        collectImageViews(v.getChildAt(i), out)
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
        InShotScrollAnimator.animateRowOnBind(holder.itemView)

        val d = context.resources.displayMetrics.density
        val audio = audioList[position]
        val prefs = context.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        val theme = ThemeStudioHelper.getCurrentTheme(context)
        val accentColor = Color.parseColor(theme.accentHex)

        val isFav = prefs.getBoolean("fav_audio_${audio.path}", false)
        val favSymbol = if (isFav) "  ❤️" else ""

        holder.tvAudioTitle.text = audio.title.substringBeforeLast('.')
        holder.tvAudioTitle.setTextColor(Color.WHITE)

        val durStr = formatDuration(audio.duration)
        val sizeStr = formatSize(audio.size)
        val artistStr = audio.artist.ifEmpty { "OG 8K Music" }
        holder.tvAudioArtistAndDuration.text = "$artistStr  •  $durStr  •  $sizeStr$favSymbol"
        holder.tvAudioArtistAndDuration.setTextColor(Color.parseColor("#B0C4DE"))

        // 1. Load real embedded Album Art or Stylized Music Cover into the left thumbnail box
        holder.imgCover?.let { img ->
            img.background = GradientDrawable().apply {
                setColor(Color.parseColor("#141E30"))
                cornerRadius = 12f * d
            }
            img.setPadding(0, 0, 0, 0)
            img.clearColorFilter()
            loadAlbumArtOrFallbackInto(context, audio.path, audio.title, img)
        }

        // 2. Replace the old bold down-arrow with a sleek 3-Vertical-Dots Vector Icon on the right
        holder.btnMore?.let { moreBtn ->
            if (moreBtn !== holder.imgCover) {
                moreBtn.clearColorFilter()
                moreBtn.setPadding((6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt())
                moreBtn.setImageDrawable(ThreeDotsCircleDrawable(d, accentColor))
                moreBtn.setOnClickListener {
                    val pos = holder.adapterPosition
                    if (pos in audioList.indices) {
                        showRichAudioOptionsMenu(pos)
                    }
                }
            }
        }

        holder.itemView.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos !in audioList.indices) return@setOnClickListener
            if (onSongClicked != null) {
                onSongClicked.invoke(pos)
            } else {
                openInAudioPlayer(pos)
            }
        }
    }

    private fun openInAudioPlayer(selectedIndex: Int) {
        if (selectedIndex !in audioList.indices) return
        val selected = audioList[selectedIndex]
        val allPaths = ArrayList<String>(audioList.size)
        val allTitles = ArrayList<String>(audioList.size)
        val allArtists = ArrayList<String>(audioList.size)
        for (item in audioList) {
            allPaths.add(item.path)
            allTitles.add(item.title)
            allArtists.add(item.artist)
        }

        val intent = Intent(context, AudioPlayerActivity::class.java).apply {
            putExtra("AUDIO_PATH", selected.path)
            putExtra("AUDIO_TITLE", selected.title)
            putExtra("AUDIO_ARTIST", selected.artist)
            putExtra("START_INDEX", selectedIndex)
            putStringArrayListExtra("AUDIO_LIST", allPaths)
            putStringArrayListExtra("AUDIO_TITLES", allTitles)
            putStringArrayListExtra("AUDIO_ARTISTS", allArtists)
        }
        context.startActivity(intent)
    }

    private fun showRichAudioOptionsMenu(position: Int) {
        if (position !in audioList.indices) return
        val item = audioList[position]
        val prefs = context.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        val isFav = prefs.getBoolean("fav_audio_${item.path}", false)

        val options = arrayOf(
            "▶️   Play Song Now",
            "⏭️   Play Next (After Current Song)",
            "📋   Add to Playing Queue",
            if (isFav) "💔   Remove from Favorites" else "❤️   Add to Favorites",
            "🎛️   Open Sound & Pitch Lab (Lofi / Slowed + Reverb)",
            "🎚️   Open 5-Band Dolby Equalizer",
            "🔒   Lock Song into Secret Privacy Vault",
            "📤   Share Audio File (WhatsApp / Bluetooth)",
            "ℹ️   Song Details & File Path",
            "🗑️   Delete Song Permanently (From Phone)"
        )

        VideoFragment.showBrightVideoMenuDialog(context, item.title, options) { which ->
            when (which) {
                0 -> openInAudioPlayer(position)
                1 -> {
                    prefs.edit().putString("play_next_audio_path", item.path).apply()
                    Toast.makeText(context, "Scheduled to Play Next: ${item.title}", Toast.LENGTH_SHORT).show()
                }
                2 -> {
                    Toast.makeText(context, "Added to Playing Queue: ${item.title}", Toast.LENGTH_SHORT).show()
                }
                3 -> {
                    prefs.edit().putBoolean("fav_audio_${item.path}", !isFav).apply()
                    notifyItemChanged(position)
                    Toast.makeText(
                        context,
                        if (!isFav) "Added to Favorites ❤️" else "Removed from Favorites",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                4 -> AudioPitchHelper.showSoundAndPitchLabDialog(context, null)
                5 -> EqualizerHelper.showEqualizerDialog(context, 0)
                6 -> FakeVaultHelper.lockMediaIntoVault(context, item.path, item.title)
                7 -> {
                    try {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "audio/*"
                            putExtra(Intent.EXTRA_STREAM, Uri.parse(item.path))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Song"))
                    } catch (_: Exception) {
                        Toast.makeText(context, "Unable to share song", Toast.LENGTH_SHORT).show()
                    }
                }
                8 -> {
                    AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Song Details & Path")
                        .setMessage(
                            "Title: ${item.title}\n\n" +
                                "Artist: ${item.artist}\n\n" +
                                "Duration: ${formatDuration(item.duration)}\n\n" +
                                "Size: ${formatSize(item.size)}\n\n" +
                                "Location: ${item.path}"
                        )
                        .setPositiveButton("OK", null)
                        .show()
                }
                9 -> {
                    PermanentDeleteHelper.confirmAndDeletePermanently(
                        context = context,
                        title = item.title,
                        path = item.path,
                        mediaId = item.id,
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

    override fun getItemCount(): Int = audioList.size

    private fun formatDuration(millis: Long): String {
        val safe = millis.coerceAtLeast(0L)
        val mins = TimeUnit.MILLISECONDS.toMinutes(safe)
        val secs = TimeUnit.MILLISECONDS.toSeconds(safe) % 60
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    private fun formatSize(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f MB", mb)
    }

    private class StylizedSongArtFallbackDrawable(
        private val seedTitle: String,
        private val d: Float
    ) : Drawable() {

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f * d
            color = Color.parseColor("#8800FFFF")
        }
        private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 0f || h <= 0f) return

            val hash = kotlin.math.abs(seedTitle.hashCode())
            val hue1 = (hash % 360).toFloat()
            val hue2 = ((hash / 7) % 360).toFloat()
            val c1 = Color.HSVToColor(floatArrayOf(hue1, 0.72f, 0.52f))
            val c2 = Color.HSVToColor(floatArrayOf(hue2, 0.80f, 0.25f))

            bgPaint.shader = LinearGradient(0f, 0f, w, h, c1, c2, Shader.TileMode.CLAMP)
            val rect = RectF(0f, 0f, w, h)
            canvas.drawRoundRect(rect, 10f * d, 10f * d, bgPaint)
            canvas.drawRoundRect(
                RectF(1f * d, 1f * d, w - 1f * d, h - 1f * d),
                10f * d,
                10f * d,
                ringPaint
            )

            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val r = w.coerceAtMost(h) * 0.34f
            canvas.drawCircle(cx, cy, r, ringPaint)

            notePaint.textSize = w.coerceAtMost(h) * 0.42f
            canvas.drawText("♪", cx, cy + notePaint.textSize * 0.34f, notePaint)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private class ThreeDotsCircleDrawable(
        private val d: Float,
        private val accentColor: Int
    ) : Drawable() {

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#182436")
        }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * d
            color = Color.parseColor("#4400FFFF")
        }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = accentColor
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val r = b.width().coerceAtMost(b.height()) * 0.44f

            canvas.drawCircle(cx, cy, r, bgPaint)
            canvas.drawCircle(cx, cy, r, borderPaint)

            val dotR = 1.8f * d
            val gap = r * 0.42f
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
