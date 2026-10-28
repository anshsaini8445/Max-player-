package com.app.mediaplayer

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.LruCache
import android.util.Size
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import java.io.File
import java.util.ArrayList
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class VideoAdapter(
    private val context: Context,
    private val videoList: MutableList<VideoModel>,
    private val onSelectionChanged: ((Boolean, Int) -> Unit)? = null
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    companion object {
        private const val VIEW_TYPE_LIST = 0
        private const val VIEW_TYPE_GRID = 1

        private val frameMemoryCache = object : LruCache<String, Bitmap>(180) {}
        private val frameExtractorPool = Executors.newFixedThreadPool(4)

        fun loadGuaranteedVideoThumbnail(
            context: Context,
            path: String,
            imageView: ImageView
        ) {
            val safeKey = path.ifEmpty { "default_video" }
            imageView.tag = safeKey
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP

            val cached = frameMemoryCache.get(safeKey)
            if (cached != null && !cached.isRecycled) {
                imageView.setImageBitmap(cached)
                return
            }

            val fallbackBmp = createCinemaPosterFallbackBitmap(File(path).name.ifEmpty { "Video" })
            imageView.setImageBitmap(fallbackBmp)

            if (path.isEmpty()) return

            val model: Any = if (path.startsWith("content://") || path.startsWith("http")) {
                Uri.parse(path)
            } else {
                File(path)
            }

            val options = RequestOptions()
                .frame(1500000L)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(360, 202)
                .centerCrop()
                .dontAnimate()

            try {
                Glide.with(context.applicationContext)
                    .asBitmap()
                    .load(model)
                    .apply(options)
                    .listener(object : RequestListener<Bitmap> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Bitmap>,
                            isFirstResource: Boolean
                        ): Boolean {
                            extractFallbackFrameInBackground(context, path, fallbackBmp, imageView)
                            return true
                        }

                        override fun onResourceReady(
                            resource: Bitmap,
                            model: Any,
                            target: Target<Bitmap>?,
                            dataSource: DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
                            frameMemoryCache.put(safeKey, resource)
                            (context as? Activity)?.runOnUiThread {
                                if (imageView.tag == safeKey) {
                                    imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                                    imageView.setImageBitmap(resource)
                                }
                            }
                            return true
                        }
                    })
                    .preload()
            } catch (_: Exception) {
                extractFallbackFrameInBackground(context, path, fallbackBmp, imageView)
            }
        }

        private fun extractFallbackFrameInBackground(
            context: Context,
            path: String,
            fallbackBmp: Bitmap,
            targetView: ImageView
        ) {
            frameExtractorPool.execute {
                var bmp: Bitmap? = null

                val retriever = MediaMetadataRetriever()
                try {
                    if (path.startsWith("content://")) {
                        retriever.setDataSource(context, Uri.parse(path))
                    } else {
                        retriever.setDataSource(path)
                    }
                    bmp = retriever.getFrameAtTime(2000000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.getFrameAtTime(500000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.frameAtTime
                } catch (_: Exception) {
                } finally {
                    try {
                        retriever.release()
                    } catch (_: Exception) {
                    }
                }

                if (bmp == null && !path.startsWith("content://")) {
                    try {
                        val file = File(path)
                        if (file.exists()) {
                            bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                ThumbnailUtils.createVideoThumbnail(file, Size(360, 202), null)
                            } else {
                                @Suppress("DEPRECATION")
                                ThumbnailUtils.createVideoThumbnail(path, MediaStore.Video.Thumbnails.MINI_KIND)
                            }
                        }
                    } catch (_: Exception) {
                    }
                }

                val finalBitmap = if (bmp != null) {
                    try {
                        Bitmap.createScaledBitmap(bmp, 360, 202, true)
                    } catch (_: Exception) {
                        bmp
                    }
                } else {
                    fallbackBmp
                }

                frameMemoryCache.put(path, finalBitmap)
                (context as? Activity)?.runOnUiThread {
                    if (targetView.tag == path) {
                        targetView.scaleType = ImageView.ScaleType.CENTER_CROP
                        targetView.setImageBitmap(finalBitmap)
                    }
                }
            }
        }

        private fun createCinemaPosterFallbackBitmap(title: String): Bitmap {
            val w = 320
            val h = 180
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            val palettes = arrayOf(
                intArrayOf(Color.parseColor("#0C2434"), Color.parseColor("#133B54"), Color.parseColor("#0A1924")),
                intArrayOf(Color.parseColor("#182232"), Color.parseColor("#243B55"), Color.parseColor("#101622")),
                intArrayOf(Color.parseColor("#1D1B2C"), Color.parseColor("#352F44"), Color.parseColor("#12101B")),
                intArrayOf(Color.parseColor("#0D2822"), Color.parseColor("#1B4332"), Color.parseColor("#081C15"))
            )
            val colors = palettes[abs(title.hashCode()) % palettes.size]

            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, w.toFloat(), h.toFloat(),
                    colors, null, Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

            val cx = w / 2f
            val cy = h / 2f
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 6f
                color = Color.parseColor("#3300E5FF")
            }
            canvas.drawCircle(cx, cy, 44f, ringPaint)
            return bmp
        }

        fun formatVideoDurationAny(durationVal: Any?): String {
            return when (durationVal) {
                is Long -> {
                    val safeMs = if (durationVal in 1L..3600L) durationVal * 1000L else durationVal
                    val totalSecs = TimeUnit.MILLISECONDS.toSeconds(safeMs.coerceAtLeast(0L))
                    val hrs = totalSecs / 3600
                    val mins = (totalSecs % 3600) / 60
                    val secs = totalSecs % 60
                    if (hrs > 0) {
                        String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
                    } else {
                        String.format(Locale.US, "%02d:%02d", mins, secs)
                    }
                }
                is String -> durationVal
                else -> "00:00"
            }
        }
    }

    private var isGridMode = false
    private var isMultiSelectMode = false
    private val selectedPaths = LinkedHashSet<String>()
    private var highlightQuery: String = ""

    fun setGridMode(grid: Boolean) {
        isGridMode = grid
    }

    fun setSearchHighlightQuery(query: String) {
        highlightQuery = query.trim()
    }

    fun isSelectionModeActive(): Boolean = isMultiSelectMode

    fun getSelectedVideos(): List<VideoModel> {
        return videoList.filter { selectedPaths.contains(it.path) }
    }

    fun getSelectedCount(): Int = selectedPaths.size

    fun selectAllVideos() {
        isMultiSelectMode = true
        if (selectedPaths.size == videoList.size) {
            selectedPaths.clear()
        } else {
            videoList.forEach { selectedPaths.add(it.path) }
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

    override fun getItemViewType(position: Int): Int {
        return if (isGridMode) VIEW_TYPE_GRID else VIEW_TYPE_LIST
    }

    class VideoViewHolder(
        view: View,
        val imgThumbnail: ImageView,
        val tvDurationPill: TextView,
        val tvUltraBadge: TextView,
        val imgCornerFolderBadge: ImageView,
        val progressLineView: View,
        val tvTitle: TextView,
        val tvMetaSub: TextView?,
        val folderChipLayout: LinearLayout?,
        val imgFolderChipIcon: ImageView?,
        val tvFolderChipName: TextView?,
        val btnMore: ImageView
    ) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val d = context.resources.displayMetrics.density
        return if (viewType == VIEW_TYPE_GRID) {
            buildPlayItGridViewHolder(d)
        } else {
            buildPlayItListViewHolder(d)
        }
    }

    private fun buildPlayItListViewHolder(d: Float): VideoViewHolder {
        val rootRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * d).toInt(), (8 * d).toInt(), (6 * d).toInt(), (8 * d).toInt())
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val thumbCard = CardView(context).apply {
            radius = 6f * d
            cardElevation = 0f
            setCardBackgroundColor(Color.parseColor("#161B22"))
            layoutParams = LinearLayout.LayoutParams((146 * d).toInt(), (84 * d).toInt()).apply {
                marginEnd = (12 * d).toInt()
            }
        }

        val thumbFrame = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val imgThumb = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val tvUltraBadge = TextView(context).apply {
            text = "8k"
            setTextColor(Color.parseColor("#FFD54F"))
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            visibility = View.GONE
            setPadding((5 * d).toInt(), (1 * d).toInt(), (5 * d).toInt(), (1 * d).toInt())
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.START
            ).apply {
                marginStart = (5 * d).toInt()
                bottomMargin = (5 * d).toInt()
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#BB1A160B"))
                cornerRadius = 3f * d
                setStroke((1 * d).toInt(), Color.parseColor("#FFD54F"))
            }
        }

        val imgCornerFolderBadge = ImageView(context).apply {
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams((16 * d).toInt(), (16 * d).toInt(), Gravity.BOTTOM or Gravity.START).apply {
                marginStart = (5 * d).toInt()
                bottomMargin = (5 * d).toInt()
            }
        }

        val tvDurationPill = TextView(context).apply {
            text = "▶ 00:00"
            setTextColor(Color.WHITE)
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((6 * d).toInt(), (2 * d).toInt(), (6 * d).toInt(), (2 * d).toInt())
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END
            ).apply {
                marginEnd = (5 * d).toInt()
                bottomMargin = (5 * d).toInt()
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#B3000000"))
                cornerRadius = 4f * d
            }
        }

        val progressLineView = View(context).apply {
            visibility = View.GONE
            setBackgroundColor(Color.parseColor("#2ECC71"))
            layoutParams = FrameLayout.LayoutParams(
                0,
                (2.5f * d).toInt(),
                Gravity.BOTTOM or Gravity.START
            )
        }

        thumbFrame.addView(imgThumb)
        thumbFrame.addView(tvUltraBadge)
        thumbFrame.addView(imgCornerFolderBadge)
        thumbFrame.addView(tvDurationPill)
        thumbFrame.addView(progressLineView)
        thumbCard.addView(thumbFrame)

        val centerCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvTitle = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 14.5f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }

        val tvMetaSub = TextView(context).apply {
            setTextColor(Color.parseColor("#8A929A"))
            textSize = 12f
            maxLines = 1
            setPadding(0, (4 * d).toInt(), 0, (4 * d).toInt())
        }

        val folderChipLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((6 * d).toInt(), (2.5f * d).toInt(), (8 * d).toInt(), (2.5f * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#181C20"))
                cornerRadius = 4f * d
            }
        }

        val imgFolderChipIcon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams((13 * d).toInt(), (13 * d).toInt()).apply {
                marginEnd = (5 * d).toInt()
            }
        }

        val tvFolderChipName = TextView(context).apply {
            setTextColor(Color.parseColor("#9BA2AA"))
            textSize = 11f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        folderChipLayout.addView(imgFolderChipIcon)
        folderChipLayout.addView(tvFolderChipName)

        centerCol.addView(tvTitle)
        centerCol.addView(tvMetaSub)
        centerCol.addView(folderChipLayout)

        val btnMore = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams((34 * d).toInt(), (44 * d).toInt())
            setPadding((7 * d).toInt(), (10 * d).toInt(), (7 * d).toInt(), (10 * d).toInt())
        }

        rootRow.addView(thumbCard)
        rootRow.addView(centerCol)
        rootRow.addView(btnMore)

        return VideoViewHolder(
            view = rootRow,
            imgThumbnail = imgThumb,
            tvDurationPill = tvDurationPill,
            tvUltraBadge = tvUltraBadge,
            imgCornerFolderBadge = imgCornerFolderBadge,
            progressLineView = progressLineView,
            tvTitle = tvTitle,
            tvMetaSub = tvMetaSub,
            folderChipLayout = folderChipLayout,
            imgFolderChipIcon = imgFolderChipIcon,
            tvFolderChipName = tvFolderChipName,
            btnMore = btnMore
        )
    }

    private fun buildPlayItGridViewHolder(d: Float): VideoViewHolder {
        val rootCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((5 * d).toInt(), (6 * d).toInt(), (5 * d).toInt(), (10 * d).toInt())
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val thumbCard = CardView(context).apply {
            radius = 6f * d
            cardElevation = 0f
            setCardBackgroundColor(Color.parseColor("#161B22"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (100 * d).toInt()
            )
        }

        val thumbFrame = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val imgThumb = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val imgCornerFolderBadge = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams((16 * d).toInt(), (16 * d).toInt(), Gravity.BOTTOM or Gravity.START).apply {
                marginStart = (5 * d).toInt()
                bottomMargin = (5 * d).toInt()
            }
        }

        val tvUltraBadge = TextView(context).apply {
            text = "8k"
            setTextColor(Color.parseColor("#FFD54F"))
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            visibility = View.GONE
            setPadding((5 * d).toInt(), (1 * d).toInt(), (5 * d).toInt(), (1 * d).toInt())
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.START
            ).apply {
                marginStart = (24 * d).toInt()
                bottomMargin = (5 * d).toInt()
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#BB1A160B"))
                cornerRadius = 3f * d
                setStroke((1 * d).toInt(), Color.parseColor("#FFD54F"))
            }
        }

        val tvDurationPill = TextView(context).apply {
            text = "▶ 00:00"
            setTextColor(Color.WHITE)
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((6 * d).toInt(), (2 * d).toInt(), (6 * d).toInt(), (2 * d).toInt())
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END
            ).apply {
                marginEnd = (5 * d).toInt()
                bottomMargin = (5 * d).toInt()
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#B3000000"))
                cornerRadius = 4f * d
            }
        }

        val progressLineView = View(context).apply {
            visibility = View.GONE
            setBackgroundColor(Color.parseColor("#2ECC71"))
            layoutParams = FrameLayout.LayoutParams(
                0,
                (2.5f * d).toInt(),
                Gravity.BOTTOM or Gravity.START
            )
        }

        thumbFrame.addView(imgThumb)
        thumbFrame.addView(imgCornerFolderBadge)
        thumbFrame.addView(tvUltraBadge)
        thumbFrame.addView(tvDurationPill)
        thumbFrame.addView(progressLineView)
        thumbCard.addView(thumbFrame)

        val bottomTitleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(0, (6 * d).toInt(), 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val tvTitle = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 13.5f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnMore = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams((26 * d).toInt(), (32 * d).toInt())
            setPadding((4 * d).toInt(), (4 * d).toInt(), (2 * d).toInt(), (4 * d).toInt())
        }

        bottomTitleRow.addView(tvTitle)
        bottomTitleRow.addView(btnMore)

        rootCol.addView(thumbCard)
        rootCol.addView(bottomTitleRow)

        return VideoViewHolder(
            view = rootCol,
            imgThumbnail = imgThumb,
            tvDurationPill = tvDurationPill,
            tvUltraBadge = tvUltraBadge,
            imgCornerFolderBadge = imgCornerFolderBadge,
            progressLineView = progressLineView,
            tvTitle = tvTitle,
            tvMetaSub = null,
            folderChipLayout = null,
            imgFolderChipIcon = null,
            tvFolderChipName = null,
            btnMore = btnMore
        )
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val d = context.resources.displayMetrics.density
        val video = videoList[position]
        val isSelected = selectedPaths.contains(video.path)

        val displayTitle = video.title.substringBeforeLast('.')
        if (highlightQuery.isNotEmpty()) {
            val span = SpannableString(displayTitle)
            val lowerTitle = displayTitle.lowercase(Locale.US)
            val lowerQ = highlightQuery.lowercase(Locale.US)
            var startIdx = lowerTitle.indexOf(lowerQ)
            while (startIdx >= 0) {
                val endIdx = (startIdx + lowerQ.length).coerceAtMost(displayTitle.length)
                span.setSpan(
                    ForegroundColorSpan(Color.parseColor("#2ECC71")),
                    startIdx,
                    endIdx,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                startIdx = lowerTitle.indexOf(lowerQ, endIdx)
            }
            holder.tvTitle.text = span
            holder.tvTitle.setTextColor(Color.WHITE)
        } else {
            holder.tvTitle.text = displayTitle
            holder.tvTitle.setTextColor(if (isSelected) Color.parseColor("#2ECC71") else Color.WHITE)
        }

        val durStr = formatVideoDurationAny(video.duration)
        holder.tvDurationPill.text = "▶ $durStr"

        val resTag = detectPlayItResolutionTag(video.title, video.size)
        val sizeStr = formatPlayItVideoSize(video.size)
        holder.tvMetaSub?.text = "$resTag | $sizeStr"

        // Show Golden 8k badge on thumbnail when 8K
        if (resTag.equals("8k", ignoreCase = true)) {
            holder.tvUltraBadge.text = "8k"
            holder.tvUltraBadge.visibility = View.VISIBLE
        } else {
            holder.tvUltraBadge.visibility = View.GONE
        }

        val folderName = File(video.path).parentFile?.name?.ifEmpty { "Videos" } ?: "Videos"
        val isBlueFolder = abs(folderName.hashCode()) % 2 == 1
        val folderIconDrawable = MiniSourceFolderIconDrawable(
            d = d,
            bgColor = if (isBlueFolder) Color.parseColor("#1E88E5") else Color.parseColor("#FBC02D"),
            isCircle = isBlueFolder
        )

        holder.tvFolderChipName?.text = folderName
        holder.imgFolderChipIcon?.setImageDrawable(folderIconDrawable)

        if (isGridMode) {
            holder.imgCornerFolderBadge.visibility = View.VISIBLE
            holder.imgCornerFolderBadge.setImageDrawable(folderIconDrawable)
        } else {
            holder.imgCornerFolderBadge.visibility = View.GONE
        }

        // Watched progress bar at bottom of thumbnail
        val prefs = context.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        val watchedMs = prefs.getLong("watched_pos_${video.path}", 0L)
        val totalDurMs = if (video.duration in 1L..3600L) video.duration * 1000L else video.duration.coerceAtLeast(1L)
        if (watchedMs > 3000L && totalDurMs > 0L) {
            val ratio = (watchedMs.toFloat() / totalDurMs.toFloat()).coerceIn(0.08f, 1.0f)
            holder.progressLineView.visibility = View.VISIBLE
            holder.progressLineView.post {
                val parentW = (holder.progressLineView.parent as? View)?.width ?: 0
                holder.progressLineView.layoutParams = holder.progressLineView.layoutParams.apply {
                    width = (parentW * ratio).toInt()
                }
                holder.progressLineView.requestLayout()
            }
        } else {
            holder.progressLineView.visibility = View.GONE
        }

        loadGuaranteedVideoThumbnail(context, video.path, holder.imgThumbnail)

        holder.btnMore.clearColorFilter()
        if (isMultiSelectMode) {
            holder.btnMore.setImageDrawable(SelectionCheckCircleDrawable(d, isSelected, Color.parseColor("#2ECC71")))
            holder.btnMore.setOnClickListener {
                toggleItemSelection(video.path, holder.adapterPosition)
            }
        } else {
            holder.btnMore.setImageDrawable(ThreeDotsMenuDrawable(d))
            holder.btnMore.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                val pos = holder.adapterPosition
                if (pos in videoList.indices) {
                    showVideoItemMenu(pos)
                }
            }
        }

        holder.itemView.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            val pos = holder.adapterPosition
            if (pos in videoList.indices) {
                val item = videoList[pos]
                if (!isMultiSelectMode) {
                    isMultiSelectMode = true
                    selectedPaths.clear()
                    selectedPaths.add(item.path)
                    notifyDataSetChanged()
                    onSelectionChanged?.invoke(true, selectedPaths.size)
                } else {
                    toggleItemSelection(item.path, pos)
                }
            }
            true
        }

        holder.itemView.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos !in videoList.indices) return@setOnClickListener
            if (isMultiSelectMode) {
                toggleItemSelection(videoList[pos].path, pos)
            } else {
                openVideoInPlayer(pos, startInBgAudio = false)
            }
        }
    }

    private fun toggleItemSelection(path: String, adapterPos: Int) {
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

    private fun openVideoInPlayer(index: Int, startInBgAudio: Boolean) {
        if (index !in videoList.indices) return
        val selected = videoList[index]
        val allPaths = ArrayList<String>(videoList.size)
        for (v in videoList) {
            allPaths.add(v.path)
        }
        val intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra("VIDEO_PATH", selected.path)
            putExtra("VIDEO_TITLE", selected.title)
            putStringArrayListExtra("VIDEO_LIST", allPaths)
            putExtra("START_BG_AUDIO", startInBgAudio)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        context.startActivity(intent)
    }

    private fun showVideoItemMenu(position: Int) {
        if (position !in videoList.indices) return
        val item = videoList[position]
        val prefs = context.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        val isFav = prefs.getBoolean("fav_${item.path}", false)

        val options = arrayOf(
            "Play Video Now",
            "Select Multiple Videos (Batch Share / Delete)",
            "Play in Background Audio",
            "Convert to MP3 Audio",
            if (isFav) "Remove from Favorite Videos" else "Add to Favorite Videos",
            "Lock in Privacy Vault",
            "Share via Turbo Share",
            "Share via Other Apps",
            "Properties & File Path",
            "Delete / Move to Recycle Bin"
        )

        VideoFragment.showBrightVideoMenuDialog(context, item.title, options) { which ->
            when (which) {
                0 -> openVideoInPlayer(position, startInBgAudio = false)
                1 -> {
                    isMultiSelectMode = true
                    selectedPaths.clear()
                    selectedPaths.add(item.path)
                    notifyDataSetChanged()
                    onSelectionChanged?.invoke(true, selectedPaths.size)
                }
                2 -> openVideoInPlayer(position, startInBgAudio = true)
                3 -> {
                    try {
                        val intent = Intent(context, Mp3ConverterActivity::class.java).apply {
                            putExtra("VIDEO_PATH", item.path)
                            putExtra("VIDEO_TITLE", item.title)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                    }
                }
                4 -> {
                    prefs.edit().putBoolean("fav_${item.path}", !isFav).apply()
                    Toast.makeText(
                        context,
                        if (!isFav) "Added to Favorite Videos" else "Removed from Favorite Videos",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                5 -> FakeVaultHelper.lockMediaIntoVault(context, item.path, item.title)
                6 -> {
                    try {
                        val intent = Intent(context, FileTransferActivity::class.java).apply {
                            putStringArrayListExtra("PRESELECTED_PATHS", arrayListOf(item.path))
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                    }
                }
                7 -> {
                    try {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "video/*"
                            putExtra(Intent.EXTRA_STREAM, Uri.parse(item.path))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
                    } catch (_: Exception) {
                        Toast.makeText(context, "Unable to share video", Toast.LENGTH_SHORT).show()
                    }
                }
                8 -> {
                    val res = detectPlayItResolutionTag(item.title, item.size)
                    val durStr = formatVideoDurationAny(item.duration)
                    AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Video Properties")
                        .setMessage(
                            "Title: ${item.title}\n\n" +
                                "Quality: $res\n\n" +
                                "Duration: $durStr\n\n" +
                                "Size: ${formatPlayItVideoSize(item.size)}\n\n" +
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
                        isVideo = true
                    ) {
                        if (position in videoList.indices) {
                            videoList.removeAt(position)
                            notifyItemRemoved(position)
                            notifyItemRangeChanged(position, videoList.size)
                        }
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int = videoList.size

    private fun detectPlayItResolutionTag(title: String, sizeBytes: Long): String {
        val u = title.uppercase(Locale.US)
        return when {
            u.contains("8K") || u.contains("4320P") -> "8k"
            u.contains("4K") || u.contains("2160P") -> "4k"
            u.contains("2K") || u.contains("1440P") -> "2k"
            u.contains("1080P") || u.contains("FHD") -> "1080p"
            u.contains("720P") || u.contains("HD") -> "720p"
            u.contains("480P") -> "480p"
            u.contains("360P") -> "360p"
            sizeBytes > 250L * 1024L * 1024L -> "4k"
            sizeBytes > 90L * 1024L * 1024L -> "2k"
            sizeBytes > 35L * 1024L * 1024L -> "1080p"
            else -> "720p"
        }
    }

    private fun formatPlayItVideoSize(bytes: Long): String {
        val mb = bytes.coerceAtLeast(0L) / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format(Locale.US, "%.2fGB", mb / 1024.0)
        } else {
            String.format(Locale.US, "%.2fMB", mb)
        }
    }

    private class MiniSourceFolderIconDrawable(
        private val d: Float,
        private val bgColor: Int,
        private val isCircle: Boolean
    ) : Drawable() {
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = bgColor
        }
        private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            if (isCircle) {
                canvas.drawCircle(cx, cy, w.coerceAtMost(h) * 0.48f, bgPaint)
            } else {
                canvas.drawRoundRect(RectF(0f, 0f, w, h), 3f * d, 3f * d, bgPaint)
            }
            canvas.drawCircle(cx, cy, w.coerceAtMost(h) * 0.20f, centerPaint)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private class SelectionCheckCircleDrawable(
        private val d: Float,
        private val isChecked: Boolean,
        private val accentColor: Int
    ) : Drawable() {
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (isChecked) accentColor else Color.parseColor("#22FFFFFF")
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * d
            color = Color.WHITE
        }
        private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * d
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

    private class ThreeDotsMenuDrawable(private val d: Float) : Drawable() {
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#8E959E")
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val r = b.width().coerceAtMost(b.height()) * 0.42f
            val dotR = 1.6f * d
            val gap = r * 0.52f
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
