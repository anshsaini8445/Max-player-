package com.app.mediaplayer

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
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
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object HybridVideoSectionHelper {

    private const val PREF_PLAYLISTS = "HYBRID_CUSTOM_PLAYLISTS_PREFS"
    private const val KEY_PLAYLIST_NAMES = "custom_playlist_names_v1"

    fun getMonthHeaderLabel(videos: List<VideoModel>): String {
        val latestMs = videos.firstOrNull()?.let { File(it.path).lastModified() } ?: System.currentTimeMillis()
        val safeMs = if (latestMs > 100000000000L) latestMs else System.currentTimeMillis()
        return SimpleDateFormat("MMM", Locale.US).format(Date(safeMs))
    }

    /**
     * Exact "Sort by" Dialog matching Screenshot 1000069511_2.jpg:
     * - Top group: Date, Name, Size, Length
     * - Divider line
     * - Bottom group: From new to old, From old to new
     * - Bottom right actions: Cancel | OK (in green)
     */
    fun showExactPlayItSortDialog(
        context: Context,
        currentSortField: Int,
        currentAscending: Boolean,
        onApplySort: (sortField: Int, isAscending: Boolean) -> Unit
    ) {
        val d = context.resources.displayMetrics.density
        var selectedField = currentSortField.coerceIn(0, 3)
        var selectedAscending = currentAscending

        val rootCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((22 * d).toInt(), (20 * d).toInt(), (22 * d).toInt(), (16 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2A2D2E"))
                cornerRadius = 8f * d
            }
        }

        val dialog = AlertDialog.Builder(context).setView(rootCard).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvTitle = TextView(context).apply {
            text = "Sort by"
            setTextColor(Color.parseColor("#2ECC71"))
            textSize = 18.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (12 * d).toInt())
        }
        rootCard.addView(tvTitle)

        val fieldLabels = listOf("Date", "Name", "Size", "Length")
        val fieldRadioIcons = mutableListOf<ImageView>()

        val orderLabels = listOf("From new to old", "From old to new")
        val orderRadioIcons = mutableListOf<ImageView>()

        fun refreshRadioVisuals() {
            fieldRadioIcons.forEachIndexed { idx, img ->
                img.setImageDrawable(PlayItRadioButtonDrawable(d, idx == selectedField))
            }
            orderRadioIcons.forEachIndexed { idx, img ->
                val isChecked = (idx == 0 && !selectedAscending) || (idx == 1 && selectedAscending)
                img.setImageDrawable(PlayItRadioButtonDrawable(d, isChecked))
            }
        }

        fieldLabels.forEachIndexed { index, label ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (11 * d).toInt(), 0, (11 * d).toInt())
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    selectedField = index
                    refreshRadioVisuals()
                }
            }
            val tv = TextView(context).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 15.5f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val radioImg = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams((22 * d).toInt(), (22 * d).toInt())
            }
            fieldRadioIcons.add(radioImg)
            row.addView(tv)
            row.addView(radioImg)
            rootCard.addView(row)
        }

        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (1 * d).toInt()
            ).apply {
                topMargin = (10 * d).toInt()
                bottomMargin = (10 * d).toInt()
            }
            setBackgroundColor(Color.parseColor("#44FFFFFF"))
        }
        rootCard.addView(divider)

        orderLabels.forEachIndexed { index, label ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (11 * d).toInt(), 0, (11 * d).toInt())
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    selectedAscending = (index == 1)
                    refreshRadioVisuals()
                }
            }
            val tv = TextView(context).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 15.5f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val radioImg = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams((22 * d).toInt(), (22 * d).toInt())
            }
            orderRadioIcons.add(radioImg)
            row.addView(tv)
            row.addView(radioImg)
            rootCard.addView(row)
        }

        refreshRadioVisuals()

        val bottomActionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(0, (18 * d).toInt(), 0, (4 * d).toInt())
        }

        val btnCancel = TextView(context).apply {
            text = "Cancel"
            setTextColor(Color.parseColor("#CFD8DC"))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((16 * d).toInt(), (8 * d).toInt(), (16 * d).toInt(), (8 * d).toInt())
            setOnClickListener { dialog.dismiss() }
        }

        val btnOk = TextView(context).apply {
            text = "OK"
            setTextColor(Color.parseColor("#2ECC71"))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((18 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (8 * d).toInt())
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                dialog.dismiss()
                onApplySort(selectedField, selectedAscending)
            }
        }

        bottomActionsRow.addView(btnCancel)
        bottomActionsRow.addView(btnOk)
        rootCard.addView(bottomActionsRow)
        dialog.show()
    }

    /**
     * Builds the Folder RecyclerView Adapter matching Screenshot 1000069509_2.jpg:
     * - Colored circular folder badge + Folder Name + Video count + '>' arrow
     * - Horizontal 4-Thumbnail Preview Row directly underneath each folder!
     */
    fun createPlayItFolderAdapter(
        context: Context,
        allVideos: List<VideoModel>,
        showPreviewStrip: Boolean,
        onFolderClicked: (String) -> Unit
    ): RecyclerView.Adapter<RecyclerView.ViewHolder> {
        val d = context.resources.displayMetrics.density
        val folderGroups = allVideos.groupBy { File(it.path).parent ?: "Internal Storage" }
            .entries
            .sortedByDescending { it.value.size }

        return object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val container = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (12 * d).toInt())
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                return object : RecyclerView.ViewHolder(container) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val root = holder.itemView as LinearLayout
                root.removeAllViews()

                val entry = folderGroups[position]
                val folderPath = entry.key
                val videos = entry.value
                val folderName = File(folderPath).name.ifEmpty { "Videos" }

                val topHeaderRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, (2 * d).toInt(), 0, (10 * d).toInt())
                    setOnClickListener {
                        it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onFolderClicked(folderPath)
                    }
                }

                val badgeColors = intArrayOf(
                    Color.parseColor("#1E88E5"),
                    Color.parseColor("#E53935"),
                    Color.parseColor("#FB8C00"),
                    Color.parseColor("#8E24AA"),
                    Color.parseColor("#00ACC1")
                )
                val badgeColor = badgeColors[abs(folderName.hashCode()) % badgeColors.size]

                val folderBadge = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams((38 * d).toInt(), (38 * d).toInt()).apply {
                        marginEnd = (12 * d).toInt()
                    }
                    setImageDrawable(FolderCircleBadgeDrawable(d, badgeColor))
                }

                val textCol = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                val tvName = TextView(context).apply {
                    text = folderName
                    setTextColor(Color.WHITE)
                    textSize = 15.5f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }

                val tvCount = TextView(context).apply {
                    text = "${videos.size} videos"
                    setTextColor(Color.parseColor("#8E959E"))
                    textSize = 12.5f
                    setPadding(0, (2 * d).toInt(), 0, 0)
                }

                val tvArrow = TextView(context).apply {
                    text = ">"
                    setTextColor(Color.parseColor("#8E959E"))
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                }

                textCol.addView(tvName)
                textCol.addView(tvCount)
                topHeaderRow.addView(folderBadge)
                topHeaderRow.addView(textCol)
                topHeaderRow.addView(tvArrow)
                root.addView(topHeaderRow)

                // 4-Thumbnail Preview Row underneath folder header (Screenshot 1000069509_2.jpg)
                if (showPreviewStrip) {
                    val stripRow = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        weightSum = 4f
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            (84 * d).toInt()
                        )
                    }

                    for (i in 0 until 4) {
                        val thumbWrapper = FrameLayout(context).apply {
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                                marginStart = if (i == 0) 0 else (3 * d).toInt()
                                marginEnd = if (i == 3) 0 else (3 * d).toInt()
                            }
                        }

                        if (i < videos.size) {
                            val vid = videos[i]
                            val card = CardView(context).apply {
                                radius = 6f * d
                                cardElevation = 0f
                                setCardBackgroundColor(Color.parseColor("#181D24"))
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setOnClickListener {
                                    val allPaths = ArrayList(videos.map { v -> v.path })
                                    val intent = Intent(context, PlayerActivity::class.java).apply {
                                        putExtra("VIDEO_PATH", vid.path)
                                        putExtra("VIDEO_TITLE", vid.title)
                                        putStringArrayListExtra("VIDEO_LIST", allPaths)
                                        addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                                    }
                                    context.startActivity(intent)
                                }
                            }

                            val box = FrameLayout(context)
                            val img = ImageView(context).apply {
                                scaleType = ImageView.ScaleType.CENTER_CROP
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                            VideoAdapter.loadGuaranteedVideoThumbnail(context, vid.path, img)

                            val tvDur = TextView(context).apply {
                                text = "▶ ${VideoAdapter.formatVideoDurationAny(vid.duration)}"
                                setTextColor(Color.WHITE)
                                textSize = 9.5f
                                typeface = Typeface.DEFAULT_BOLD
                                setPadding((5 * d).toInt(), (1.5f * d).toInt(), (5 * d).toInt(), (1.5f * d).toInt())
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    Gravity.BOTTOM or Gravity.END
                                ).apply {
                                    marginEnd = (4 * d).toInt()
                                    bottomMargin = (4 * d).toInt()
                                }
                                background = GradientDrawable().apply {
                                    setColor(Color.parseColor("#AA000000"))
                                    cornerRadius = 4f * d
                                }
                            }

                            box.addView(img)
                            box.addView(tvDur)
                            card.addView(box)
                            thumbWrapper.addView(card)
                        }
                        stripRow.addView(thumbWrapper)
                    }
                    root.addView(stripRow)
                }
            }

            override fun getItemCount(): Int = folderGroups.size
        }
    }

    /**
     * Builds the Playlist RecyclerView Adapter matching Screenshot 1000069510_2.jpg:
     * - Clapperboard "Favorite Videos" card + Custom Playlists
     */
    fun createPlayItPlaylistAdapter(
        context: Context,
        allVideos: List<VideoModel>,
        onOpenFavorites: () -> Unit
    ): RecyclerView.Adapter<RecyclerView.ViewHolder> {
        val d = context.resources.displayMetrics.density
        val prefs = context.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        val favCount = allVideos.count { prefs.getBoolean("fav_${it.path}", false) }

        val customRaw = context.getSharedPreferences(PREF_PLAYLISTS, Context.MODE_PRIVATE)
            .getString(KEY_PLAYLIST_NAMES, "") ?: ""
        val customNames = customRaw.split("||").filter { it.isNotBlank() }

        val rows = mutableListOf(Pair("Favorite Videos", "$favCount Files"))
        customNames.forEach { name ->
            rows.add(Pair(name, "Custom Playlist"))
        }

        return object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                return object : RecyclerView.ViewHolder(row) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val row = holder.itemView as LinearLayout
                row.removeAllViews()
                val (title, subtitle) = rows[position]

                val clapperView = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams((142 * d).toInt(), (86 * d).toInt()).apply {
                        marginEnd = (16 * d).toInt()
                    }
                    setImageDrawable(ClapperboardPlaylistCardDrawable(d))
                }

                val textCol = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                val tvTitle = TextView(context).apply {
                    text = title
                    setTextColor(Color.WHITE)
                    textSize = 15.5f
                    typeface = Typeface.DEFAULT_BOLD
                }

                val tvSub = TextView(context).apply {
                    text = subtitle
                    setTextColor(Color.parseColor("#8E959E"))
                    textSize = 13f
                    setPadding(0, (6 * d).toInt(), 0, 0)
                }

                val tvArrow = TextView(context).apply {
                    text = ">"
                    setTextColor(Color.parseColor("#B0B8C0"))
                    textSize = 18f
                }

                textCol.addView(tvTitle)
                textCol.addView(tvSub)
                row.addView(clapperView)
                row.addView(textCol)
                row.addView(tvArrow)

                row.setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onOpenFavorites()
                }
            }

            override fun getItemCount(): Int = rows.size
        }
    }

    fun showCreatePlaylistDialog(context: Context, onCreated: () -> Unit) {
        val d = context.resources.displayMetrics.density
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * d).toInt(), (18 * d).toInt(), (20 * d).toInt(), (14 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2A2D2E"))
                cornerRadius = 12f * d
            }
        }
        val dialog = AlertDialog.Builder(context).setView(card).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvTitle = TextView(context).apply {
            text = "Create New Playlist"
            setTextColor(Color.parseColor("#2ECC71"))
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (12 * d).toInt())
        }

        val etName = EditText(context).apply {
            hint = "Enter playlist name..."
            setHintTextColor(Color.parseColor("#8E959E"))
            setTextColor(Color.WHITE)
            textSize = 14.5f
            inputType = InputType.TYPE_CLASS_TEXT
        }

        val btnSave = TextView(context).apply {
            text = "CREATE"
            setTextColor(Color.parseColor("#2ECC71"))
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.END
            setPadding(0, (14 * d).toInt(), (6 * d).toInt(), (4 * d).toInt())
            setOnClickListener {
                val name = etName.text.toString().trim()
                if (name.isNotEmpty()) {
                    val prefs = context.getSharedPreferences(PREF_PLAYLISTS, Context.MODE_PRIVATE)
                    val prev = prefs.getString(KEY_PLAYLIST_NAMES, "") ?: ""
                    val updated = if (prev.isBlank()) name else "$prev||$name"
                    prefs.edit().putString(KEY_PLAYLIST_NAMES, updated).apply()
                    dialog.dismiss()
                    onCreated()
                    Toast.makeText(context, "Playlist Created: $name", Toast.LENGTH_SHORT).show()
                }
            }
        }

        card.addView(tvTitle)
        card.addView(etName)
        card.addView(btnSave)
        dialog.show()
    }

    /**
     * Custom Vector Drawable for Header & Action Bar Icons matching PLAYit Screenshots.
     */
    class PlayItHeaderVectorDrawable(
        private val type: String,
        private val d: Float
    ) : Drawable() {

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.9f * d
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.WHITE
        }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }

        private val redDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#FF3B30")
        }

        private val greyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f * d
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.parseColor("#9AA0A6")
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val r = b.width().coerceAtMost(b.height()) * 0.42f

            when (type) {
                "CLEANER_BUCKET_RED_DOT" -> {
                    val bucket = RectF(cx - r * 0.58f, cy - r * 0.32f, cx + r * 0.58f, cy + r * 0.68f)
                    canvas.drawRoundRect(bucket, 3f * d, 3f * d, strokePaint)
                    canvas.drawLine(cx - r * 0.72f, cy - r * 0.32f, cx + r * 0.72f, cy - r * 0.32f, strokePaint)
                    canvas.drawLine(cx - r * 0.25f, cy - r * 0.52f, cx + r * 0.25f, cy - r * 0.52f, strokePaint)
                    canvas.drawLine(cx - r * 0.18f, cy - r * 0.02f, cx - r * 0.18f, cy + r * 0.38f, strokePaint)
                    canvas.drawLine(cx + r * 0.18f, cy - r * 0.02f, cx + r * 0.18f, cy + r * 0.38f, strokePaint)
                    canvas.drawCircle(cx + r * 0.55f, cy - r * 0.42f, 3.2f * d, redDotPaint)
                }
                "HISTORY_TRASH" -> {
                    val bin = RectF(cx - r * 0.52f, cy - r * 0.35f, cx + r * 0.52f, cy + r * 0.68f)
                    canvas.drawRoundRect(bin, 2.5f * d, 2.5f * d, greyStrokePaint)
                    canvas.drawLine(cx - r * 0.68f, cy - r * 0.35f, cx + r * 0.68f, cy - r * 0.35f, greyStrokePaint)
                    canvas.drawLine(cx - r * 0.22f, cy - r * 0.55f, cx + r * 0.22f, cy - r * 0.55f, greyStrokePaint)
                    canvas.drawLine(cx - r * 0.16f, cy - r * 0.08f, cx - r * 0.16f, cy + r * 0.40f, greyStrokePaint)
                    canvas.drawLine(cx + r * 0.16f, cy - r * 0.08f, cx + r * 0.16f, cy + r * 0.40f, greyStrokePaint)
                }
                "SORT_ARROWS" -> {
                    // Left arrow pointing up, right arrow pointing down (Screenshot 1000069507_2.jpg)
                    val lx = cx - r * 0.30f
                    val rx = cx + r * 0.30f
                    canvas.drawLine(lx, cy - r * 0.62f, lx, cy + r * 0.62f, greyStrokePaint)
                    canvas.drawLine(lx - r * 0.28f, cy - r * 0.28f, lx, cy - r * 0.62f, greyStrokePaint)
                    canvas.drawLine(rx, cy - r * 0.62f, rx, cy + r * 0.62f, greyStrokePaint)
                    canvas.drawLine(rx + r * 0.28f, cy + r * 0.28f, rx, cy + r * 0.62f, greyStrokePaint)
                }
                "VIEW_LIST_ICON" -> {
                    // Two small squares on left + horizontal lines on right (Screenshot 1000069507_2.jpg)
                    val s1 = RectF(cx - r * 0.68f, cy - r * 0.58f, cx - r * 0.18f, cy - r * 0.10f)
                    val s2 = RectF(cx - r * 0.68f, cy + r * 0.10f, cx - r * 0.18f, cy + r * 0.58f)
                    canvas.drawRoundRect(s1, 1.5f * d, 1.5f * d, greyStrokePaint)
                    canvas.drawRoundRect(s2, 1.5f * d, 1.5f * d, greyStrokePaint)
                    canvas.drawLine(cx + r * 0.05f, cy - r * 0.45f, cx + r * 0.68f, cy - r * 0.45f, greyStrokePaint)
                    canvas.drawLine(cx + r * 0.05f, cy - r * 0.20f, cx + r * 0.68f, cy - r * 0.20f, greyStrokePaint)
                    canvas.drawLine(cx + r * 0.05f, cy + r * 0.20f, cx + r * 0.68f, cy + r * 0.20f, greyStrokePaint)
                    canvas.drawLine(cx + r * 0.05f, cy + r * 0.45f, cx + r * 0.68f, cy + r * 0.45f, greyStrokePaint)
                }
                "VIEW_GRID_ICON" -> {
                    // 4 small squares (Screenshot 1000069508_2.jpg)
                    val tl = RectF(cx - r * 0.62f, cy - r * 0.62f, cx - r * 0.12f, cy - r * 0.12f)
                    val tr = RectF(cx + r * 0.12f, cy - r * 0.62f, cx + r * 0.62f, cy - r * 0.12f)
                    val bl = RectF(cx - r * 0.62f, cy + r * 0.12f, cx - r * 0.12f, cy + r * 0.62f)
                    val br = RectF(cx + r * 0.12f, cy + r * 0.12f, cx + r * 0.62f, cy + r * 0.62f)
                    canvas.drawRoundRect(tl, 1.5f * d, 1.5f * d, greyStrokePaint)
                    canvas.drawRoundRect(tr, 1.5f * d, 1.5f * d, greyStrokePaint)
                    canvas.drawRoundRect(bl, 1.5f * d, 1.5f * d, greyStrokePaint)
                    canvas.drawRoundRect(br, 1.5f * d, 1.5f * d, greyStrokePaint)
                }
                "PLUS_ADD" -> {
                    canvas.drawLine(cx - r * 0.62f, cy, cx + r * 0.62f, cy, greyStrokePaint)
                    canvas.drawLine(cx, cy - r * 0.62f, cx, cy + r * 0.62f, greyStrokePaint)
                }
            }
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private class PlayItRadioButtonDrawable(
        private val d: Float,
        private val isChecked: Boolean
    ) : Drawable() {
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * d
            color = if (isChecked) Color.parseColor("#2ECC71") else Color.parseColor("#9AA0A6")
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#2ECC71")
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val r = b.width().coerceAtMost(b.height()) * 0.42f
            canvas.drawCircle(cx, cy, r, strokePaint)
            if (isChecked) {
                canvas.drawCircle(cx, cy, r * 0.52f, fillPaint)
            }
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private class FolderCircleBadgeDrawable(
        private val d: Float,
        private val badgeColor: Int
    ) : Drawable() {
        private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(55, Color.red(badgeColor), Color.green(badgeColor), Color.blue(badgeColor))
        }
        private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = badgeColor
        }
        private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val r = b.width().coerceAtMost(b.height()) * 0.48f
            canvas.drawCircle(cx, cy, r, outerPaint)
            val box = RectF(cx - r * 0.48f, cy - r * 0.48f, cx + r * 0.48f, cy + r * 0.48f)
            canvas.drawRoundRect(box, 5f * d, 5f * d, innerPaint)
            canvas.drawCircle(cx, cy, r * 0.16f, whitePaint)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private class ClapperboardPlaylistCardDrawable(private val d: Float) : Drawable() {
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val slatePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#B0B5BA")
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()

            bgPaint.shader = LinearGradient(
                0f, 0f, w, h,
                Color.parseColor("#6E7175"),
                Color.parseColor("#44474A"),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(RectF(0f, 0f, w, h), 6f * d, 6f * d, bgPaint)

            slatePaint.shader = LinearGradient(
                w * 0.22f, h * 0.45f, w * 0.78f, h,
                Color.parseColor("#CFD3D7"),
                Color.parseColor("#7D8287"),
                Shader.TileMode.CLAMP
            )
            val bodyRect = RectF(w * 0.22f, h * 0.46f, w * 0.72f, h * 0.98f)
            canvas.drawRoundRect(bodyRect, 5f * d, 5f * d, slatePaint)

            canvas.save()
            canvas.rotate(-12f, w * 0.24f, h * 0.40f)
            val barRect = RectF(w * 0.22f, h * 0.24f, w * 0.70f, h * 0.38f)
            canvas.drawRoundRect(barRect, 3f * d, 3f * d, stripePaint)
            canvas.restore()
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
