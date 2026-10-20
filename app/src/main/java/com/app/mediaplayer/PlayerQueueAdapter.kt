package com.app.mediaplayer

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.net.Uri
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File
import java.util.Locale

class PlayerQueueAdapter(
    private val context: Context,
    private val videoList: MutableList<String>,
    private val getCurrentIndex: () -> Int,
    private val onSelectVideo: (Int) -> Unit,
    private val onRemoveVideo: (Int) -> Unit
) : RecyclerView.Adapter<PlayerQueueAdapter.QueueVH>() {

    var itemTouchHelper: ItemTouchHelper? = null

    private val density = context.resources.displayMetrics.density
    private val resPrefs = context.getSharedPreferences("MAX_VIDEO_RES_PREFS", Context.MODE_PRIVATE)
    private val watchedPrefs = context.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)

    class QueueVH(
        view: View,
        val img: ImageView,
        val pb: ProgressBar,
        val tvTitle: TextView,
        val tvMeta: TextView,
        val btnDrag: TextView,
        val btnRemove: TextView
    ) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueVH {
        val d = density
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (6 * d).toInt(), 0, (6 * d).toInt())
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val thumbCard = CardView(context).apply {
            radius = 6f * d
            setCardBackgroundColor(Color.parseColor("#222230"))
            layoutParams = LinearLayout.LayoutParams((100 * d).toInt(), (56 * d).toInt())
        }
        val frame = FrameLayout(context)
        val img = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val pb = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressDrawable.setColorFilter(
                Color.parseColor("#00E676"),
                PorterDuff.Mode.SRC_IN
            )
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (3 * d).toInt(),
                Gravity.BOTTOM
            )
        }
        frame.addView(img)
        frame.addView(pb)
        thumbCard.addView(frame)

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (10 * d).toInt()
                marginEnd = (6 * d).toInt()
            }
        }
        val tvTitle = TextView(context).apply {
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        val tvMeta = TextView(context).apply {
            textSize = 10.5f
            setPadding(0, (3 * d).toInt(), 0, 0)
        }
        textCol.addView(tvTitle)
        textCol.addView(tvMeta)

        val btnDrag = TextView(context).apply {
            text = "≡"
            setTextColor(Color.parseColor("#BBBBCC"))
            textSize = 20f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((32 * d).toInt(), (40 * d).toInt())
        }

        val btnRemove = TextView(context).apply {
            text = "✕"
            setTextColor(Color.parseColor("#BBBBCC"))
            textSize = 15f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((32 * d).toInt(), (40 * d).toInt())
        }

        row.addView(thumbCard)
        row.addView(textCol)
        row.addView(btnDrag)
        row.addView(btnRemove)
        return QueueVH(row, img, pb, tvTitle, tvMeta, btnDrag, btnRemove)
    }

    override fun onBindViewHolder(holder: QueueVH, position: Int) {
        val path = videoList[position]
        val file = File(path)
        val name = file.name.ifEmpty { "Video ${position + 1}" }
        val isPlayingNow = position == getCurrentIndex()

        holder.tvTitle.text = name
        holder.tvTitle.setTextColor(
            if (isPlayingNow) Color.parseColor("#00E676") else Color.WHITE
        )

        val sizeBytes = if (file.exists()) file.length() else 0L
        val sizeMb = sizeBytes / (1024.0 * 1024.0)
        val sizeLabel = if (sizeMb >= 1024.0) {
            String.format(Locale.US, "%.2fGB", sizeMb / 1024.0)
        } else {
            String.format(Locale.US, "%.2fMB", sizeMb)
        }
        val resBadge = resPrefs.getString("res_$path", null)
            ?: computeResolutionBadge(name, sizeBytes)

        holder.tvMeta.text = "$resBadge | $sizeLabel"
        holder.tvMeta.setTextColor(
            if (isPlayingNow) Color.parseColor("#00E676") else Color.parseColor("#9999AA")
        )

        val watched = watchedPrefs.getLong("watched_pos_$path", 0L)
        holder.pb.progress = if (watched > 0L) 650 else 0

        val target: Any = if (path.startsWith("content://")) Uri.parse(path) else file
        Glide.with(context)
            .load(target)
            .placeholder(android.R.color.darker_gray)
            .centerCrop()
            .into(holder.img)

        holder.itemView.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos in videoList.indices) {
                onSelectVideo(pos)
            }
        }

        holder.btnDrag.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                itemTouchHelper?.startDrag(holder)
            }
            false
        }

        holder.btnRemove.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos in videoList.indices) {
                onRemoveVideo(pos)
            }
        }
    }

    override fun getItemCount(): Int = videoList.size

    private fun computeResolutionBadge(title: String, sizeBytes: Long): String {
        val upper = title.uppercase(Locale.US)
        return when {
            upper.contains("8K") || upper.contains("4320P") -> "8K"
            upper.contains("4K") || upper.contains("2160P") -> "4K"
            upper.contains("2K") || upper.contains("1440P") -> "2K"
            upper.contains("1080P") || upper.contains("FHD") -> "1080p"
            upper.contains("720P") || upper.contains("HD") -> "720p"
            upper.contains("480P") -> "480p"
            upper.contains("360P") -> "360p"
            sizeBytes > 120L * 1024L * 1024L -> "1080p"
            sizeBytes > 30L * 1024L * 1024L -> "720p"
            else -> "480p"
        }
    }
}
