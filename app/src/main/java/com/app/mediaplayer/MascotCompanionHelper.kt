package com.app.mediaplayer

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object MascotCompanionHelper {

    private const val PREF_NAME = "HYBRID_MASCOT_PREFS"
    private const val KEY_ENABLED = "mascot_enabled"
    private const val KEY_CHARACTER = "mascot_character_type"
    private const val KEY_COLOR_INDEX = "mascot_color_index"

    // Dismissed state for the current video screen session after a 3-second hold triggers video playback
    private var hiddenOnVideoScreenForSession = false

    data class MascotCharacter(
        val id: String,
        val name: String,
        val badge: String
    )

    data class MascotColorOption(
        val name: String,
        val primaryHex: String,
        val secondaryHex: String
    )

    val characters = listOf(
        MascotCharacter("PUPPY", "Cute Puppy", "Dog / Puppy"),
        MascotCharacter("TEDDY", "Dancing Teddy", "Gudda / Bear"),
        MascotCharacter("BULL", "Power Bull", "Bhaisa / Bull"),
        MascotCharacter("CAT", "Neon Kitty", "Cute Cat"),
        MascotCharacter("PANDA", "Groove Panda", "Chubby Panda"),
        MascotCharacter("ROBOT", "Cyber Bot", "8K Mini Bot")
    )

    val colorOptions = listOf(
        MascotColorOption("Cyan Neon", "#00FFFF", "#0088AA"),
        MascotColorOption("Golden Glow", "#FFD700", "#CC8800"),
        MascotColorOption("Sakura Pink", "#FF4081", "#AA1155"),
        MascotColorOption("Emerald Green", "#00E676", "#008844"),
        MascotColorOption("Royal Purple", "#B388FF", "#6200EA")
    )

    // Enabled by default (true) on first install as requested
    fun isMascotEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)
    }

    fun setMascotEnabled(context: Context, enabled: Boolean) {
        if (enabled) {
            hiddenOnVideoScreenForSession = false
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun getSelectedCharacter(context: Context): MascotCharacter {
        val savedId = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CHARACTER, "PUPPY") ?: "PUPPY"
        return characters.firstOrNull { it.id == savedId } ?: characters[0]
    }

    fun getSelectedColor(context: Context): MascotColorOption {
        val idx = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_COLOR_INDEX, 0)
            .coerceIn(0, colorOptions.size - 1)
        return colorOptions[idx]
    }

    fun resetVideoSessionVisibility() {
        hiddenOnVideoScreenForSession = false
    }

    /**
     * Attaches the interactive Mascot onto a parent ViewGroup (FrameLayout / RelativeLayout / LinearLayout wrapper)
     * linked with a RecyclerView.
     * - Dragging near top/bottom scrolls the RecyclerView smoothly.
     * - Holding over a RecyclerView item for 3.0 seconds triggers playback of that item.
     * - On Video screen, dances ONLY when background audio is playing, and removes itself once 3-sec hold plays a video.
     * - On Music screen, dances whenever music is playing and stays available.
     */
    fun attachInteractiveMascot(
        context: Context,
        hostContainer: ViewGroup,
        recyclerView: RecyclerView?,
        isVideoScreen: Boolean,
        isDancingProvider: () -> Boolean,
        onHoldItemTriggered: (Int) -> Unit
    ): View? {
        try {
            val existing = hostContainer.findViewWithTag<View>("HYBRID_MASCOT_OVERLAY_TAG")
            if (existing != null) {
                hostContainer.removeView(existing)
            }

            if (!isMascotEnabled(context)) return null
            if (isVideoScreen && hiddenOnVideoScreenForSession) return null

            val d = context.resources.displayMetrics.density
            val sizePx = (74 * d).toInt()
            val mascotView = InteractiveMascotView(
                context = context,
                d = d,
                characterId = getSelectedCharacter(context).id,
                primaryColor = Color.parseColor(getSelectedColor(context).primaryHex),
                secondaryColor = Color.parseColor(getSelectedColor(context).secondaryHex)
            ).apply {
                tag = "HYBRID_MASCOT_OVERLAY_TAG"
                elevation = 28f * d
            }

            val lp = when (hostContainer) {
                is FrameLayout -> FrameLayout.LayoutParams(
                    sizePx,
                    sizePx,
                    Gravity.END or Gravity.CENTER_VERTICAL
                ).apply {
                    marginEnd = (12 * d).toInt()
                }
                else -> ViewGroup.MarginLayoutParams(sizePx, sizePx)
            }
            hostContainer.addView(mascotView, lp)

            val mainHandler = Handler(Looper.getMainLooper())
            var danceAnimator: ObjectAnimator? = null
            var isDragging = false
            var dX = 0f
            var dY = 0f
            var lastHoveredPosition = RecyclerView.NO_POSITION
            var holdElapsedMs = 0L
            var autoScrollVelocityPx = 0

            val danceStateChecker = object : Runnable {
                override fun run() {
                    if (!mascotView.isAttachedToWindow) return
                    val shouldDance = isDancingProvider()
                    mascotView.setDancingState(shouldDance)
                    if (shouldDance) {
                        if (danceAnimator == null) {
                            val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.12f, 0.94f, 1.08f, 1.0f)
                            val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 0.90f, 1.14f, 0.95f, 1.0f)
                            val rot = PropertyValuesHolder.ofFloat(View.ROTATION, 0f, -14f, 14f, -10f, 10f, 0f)
                            danceAnimator = ObjectAnimator.ofPropertyValuesHolder(mascotView, scaleX, scaleY, rot).apply {
                                duration = 850L
                                repeatCount = ValueAnimator.INFINITE
                                interpolator = LinearInterpolator()
                                start()
                            }
                        } else if (danceAnimator?.isPaused == true) {
                            danceAnimator?.resume()
                        }
                    } else {
                        danceAnimator?.pause()
                        mascotView.rotation = 0f
                        mascotView.scaleX = 1f
                        mascotView.scaleY = 1f
                    }
                    mainHandler.postDelayed(this, 600L)
                }
            }
            mainHandler.post(danceStateChecker)

            val holdAndScrollTicker = object : Runnable {
                override fun run() {
                    if (!isDragging || !mascotView.isAttachedToWindow) return

                    // 1. Auto-scroll RecyclerView when mascot is dragged vertically
                    if (recyclerView != null && autoScrollVelocityPx != 0) {
                        recyclerView.scrollBy(0, autoScrollVelocityPx)
                    }

                    // 2. Check which RecyclerView row is underneath the mascot's center
                    if (recyclerView != null && recyclerView.isShown) {
                        val rvLoc = IntArray(2)
                        val mascotLoc = IntArray(2)
                        recyclerView.getLocationOnScreen(rvLoc)
                        mascotView.getLocationOnScreen(mascotLoc)

                        val centerXOnRv = (mascotLoc[0] + mascotView.width / 2f) - rvLoc[0]
                        val centerYOnRv = (mascotLoc[1] + mascotView.height / 2f) - rvLoc[1]
                        val childUnder = recyclerView.findChildViewUnder(centerXOnRv, centerYOnRv)
                        val pos = if (childUnder != null) recyclerView.getChildAdapterPosition(childUnder) else RecyclerView.NO_POSITION

                        if (pos != RecyclerView.NO_POSITION && abs(autoScrollVelocityPx) < (8 * d).toInt()) {
                            if (pos == lastHoveredPosition) {
                                holdElapsedMs += 50L
                                val progress = (holdElapsedMs / 3000f).coerceIn(0f, 1f)
                                mascotView.setHoldProgress(progress)

                                if (holdElapsedMs >= 3000L) {
                                    isDragging = false
                                    mascotView.setHoldProgress(0f)
                                    holdElapsedMs = 0L

                                    if (isVideoScreen) {
                                        hiddenOnVideoScreenForSession = true
                                        hostContainer.removeView(mascotView)
                                    }
                                    onHoldItemTriggered(pos)
                                    return
                                }
                            } else {
                                lastHoveredPosition = pos
                                holdElapsedMs = 0L
                                mascotView.setHoldProgress(0f)
                            }
                        } else {
                            lastHoveredPosition = RecyclerView.NO_POSITION
                            holdElapsedMs = 0L
                            mascotView.setHoldProgress(0f)
                        }
                    }

                    mainHandler.postDelayed(this, 50L)
                }
            }

            var touchDownRawY = 0f
            mascotView.setOnTouchListener { v, event ->
                val parentW = (v.parent as? View)?.width?.toFloat() ?: 1080f
                val parentH = (v.parent as? View)?.height?.toFloat() ?: 1920f

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        isDragging = true
                        dX = v.x - event.rawX
                        dY = v.y - event.rawY
                        touchDownRawY = event.rawY
                        lastHoveredPosition = RecyclerView.NO_POSITION
                        holdElapsedMs = 0L
                        autoScrollVelocityPx = 0
                        mascotView.setHoldProgress(0f)
                        mainHandler.removeCallbacks(holdAndScrollTicker)
                        mainHandler.post(holdAndScrollTicker)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val newX = (event.rawX + dX).coerceIn(0f, (parentW - v.width).coerceAtLeast(0f))
                        val newY = (event.rawY + dY).coerceIn(0f, (parentH - v.height).coerceAtLeast(0f))

                        val deltaYFromLast = event.rawY - touchDownRawY
                        touchDownRawY = event.rawY

                        v.x = newX
                        v.y = newY

                        // Scroll list smoothly when dragging near top/bottom OR sliding mascot up/down
                        autoScrollVelocityPx = when {
                            newY < parentH * 0.22f -> -(18 * d).toInt()
                            newY > parentH * 0.76f -> (18 * d).toInt()
                            abs(deltaYFromLast) > 4f -> (deltaYFromLast * 1.8f).toInt()
                            else -> 0
                        }
                        if (abs(deltaYFromLast) > 10f) {
                            // Reset 3-second hold timer if user is actively sliding fast
                            holdElapsedMs = 0L
                            mascotView.setHoldProgress(0f)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isDragging = false
                        autoScrollVelocityPx = 0
                        holdElapsedMs = 0L
                        mascotView.setHoldProgress(0f)
                        mainHandler.removeCallbacks(holdAndScrollTicker)
                        true
                    }
                    else -> false
                }
            }

            return mascotView
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun showMascotCustomizationDialog(
        context: Context,
        onSettingsChanged: () -> Unit
    ) {
        val d = context.resources.displayMetrics.density
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val theme = ThemeStudioHelper.getCurrentTheme(context)
        val accentColor = Color.parseColor(theme.accentHex)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * d).toInt(), (18 * d).toInt(), (18 * d).toInt(), (16 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0A0A10"))
                cornerRadius = 24f * d
                setStroke((1.6f * d).toInt(), accentColor)
            }
        }

        val dialog = AlertDialog.Builder(context).setView(root).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (12 * d).toInt())
        }

        val tvHeader = TextView(context).apply {
            text = "Dancing Mascot & Companion Studio"
            setTextColor(accentColor)
            textSize = 16.5f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnClose = TextView(context).apply {
            text = "X"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((30 * d).toInt(), (30 * d).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#26FFFFFF"))
            }
            setOnClickListener { dialog.dismiss() }
        }
        headerRow.addView(tvHeader)
        headerRow.addView(btnClose)
        root.addView(headerRow)

        val scroll = ScrollView(context)
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        // 1. Permanent ON/OFF Switch
        val switchRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141E30"))
                cornerRadius = 16f * d
            }
        }

        val tvSwitchLabel = TextView(context).apply {
            text = "Show Interactive Mascot on Video & Music Screens\n(Drag to scroll • Hold 3s on any song/video to play)"
            setTextColor(Color.WHITE)
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val swEnable = Switch(context).apply {
            isChecked = isMascotEnabled(context)
            setOnCheckedChangeListener { _, isChecked ->
                setMascotEnabled(context, isChecked)
                onSettingsChanged()
            }
        }
        switchRow.addView(tvSwitchLabel)
        switchRow.addView(swEnable)
        col.addView(switchRow)

        // 2. Character Selection Grid (Puppy, Teddy, Bull/Bhaisa, Cat, Panda, Robot)
        val tvCharTitle = TextView(context).apply {
            text = "CHOOSE MASCOT CHARACTER"
            setTextColor(accentColor)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((4 * d).toInt(), (14 * d).toInt(), 0, (8 * d).toInt())
        }
        col.addView(tvCharTitle)

        val charGrid = GridLayout(context).apply {
            columnCount = 2
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val currentCharId = getSelectedCharacter(context).id
        val currentColor = getSelectedColor(context)

        characters.forEach { character ->
            val isSelected = character.id == currentCharId
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt())
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = (68 * d).toInt()
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins((4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt())
                }
                background = GradientDrawable().apply {
                    setColor(if (isSelected) Color.parseColor("#1E2E46") else Color.parseColor("#121A28"))
                    cornerRadius = 14f * d
                    setStroke(
                        ((if (isSelected) 1.8f else 1.0f) * d).toInt(),
                        if (isSelected) accentColor else Color.parseColor("#24FFFFFF")
                    )
                }
                setOnClickListener {
                    prefs.edit().putString(KEY_CHARACTER, character.id).apply()
                    setMascotEnabled(context, true)
                    dialog.dismiss()
                    onSettingsChanged()
                    Toast.makeText(context, "Mascot Changed: ${character.name}", Toast.LENGTH_SHORT).show()
                }
            }

            val previewView = InteractiveMascotView(
                context = context,
                d = d,
                characterId = character.id,
                primaryColor = Color.parseColor(currentColor.primaryHex),
                secondaryColor = Color.parseColor(currentColor.secondaryHex)
            ).apply {
                layoutParams = LinearLayout.LayoutParams((42 * d).toInt(), (42 * d).toInt()).apply {
                    marginEnd = (8 * d).toInt()
                }
            }

            val txtCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            val tvName = TextView(context).apply {
                text = character.name
                setTextColor(Color.WHITE)
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
            }
            val tvBadge = TextView(context).apply {
                text = character.badge
                setTextColor(accentColor)
                textSize = 10.5f
            }
            txtCol.addView(tvName)
            txtCol.addView(tvBadge)
            card.addView(previewView)
            card.addView(txtCol)
            charGrid.addView(card)
        }
        col.addView(charGrid)

        // 3. Mascot Color Picker
        val tvColorTitle = TextView(context).apply {
            text = "CHOOSE MASCOT COLOR GLOW"
            setTextColor(accentColor)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((4 * d).toInt(), (14 * d).toInt(), 0, (8 * d).toInt())
        }
        col.addView(tvColorTitle)

        val colorRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, (4 * d).toInt(), 0, (8 * d).toInt())
        }

        val savedColorIdx = prefs.getInt(KEY_COLOR_INDEX, 0)
        colorOptions.forEachIndexed { index, colorOpt ->
            val swatch = TextView(context).apply {
                text = if (index == savedColorIdx) "✓" else ""
                setTextColor(Color.parseColor("#0A0A10"))
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams((44 * d).toInt(), (44 * d).toInt()).apply {
                    setMargins((6 * d).toInt(), 0, (6 * d).toInt(), 0)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(colorOpt.primaryHex))
                    setStroke((2 * d).toInt(), Color.WHITE)
                }
                setOnClickListener {
                    prefs.edit().putInt(KEY_COLOR_INDEX, index).apply()
                    setMascotEnabled(context, true)
                    dialog.dismiss()
                    onSettingsChanged()
                    Toast.makeText(context, "Mascot Color: ${colorOpt.name}", Toast.LENGTH_SHORT).show()
                }
            }
            colorRow.addView(swatch)
        }
        col.addView(colorRow)

        scroll.addView(col)
        root.addView(scroll)
        dialog.show()
    }

    class InteractiveMascotView(
        context: Context,
        private val d: Float,
        private val characterId: String,
        private val primaryColor: Int,
        private val secondaryColor: Int
    ) : View(context) {

        private var holdProgress = 0f
        private var isDancing = false

        private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(48, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor))
        }

        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = primaryColor
        }

        private val earPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = secondaryColor
        }

        private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#0A0A10")
        }

        private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }

        private val holdRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.6f * d
            strokeCap = Paint.Cap.ROUND
            color = Color.parseColor("#00E676")
        }

        private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
            textSize = 11f * d
            typeface = Typeface.DEFAULT_BOLD
        }

        fun setHoldProgress(progress: Float) {
            holdProgress = progress.coerceIn(0f, 1f)
            invalidate()
        }

        fun setDancingState(dancing: Boolean) {
            isDancing = dancing
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val cx = w / 2f
            val cy = h / 2f
            val r = w.coerceAtMost(h) * 0.34f

            // Outer glowing aura
            canvas.drawCircle(cx, cy, r * 1.25f, auraPaint)

            // Draw character ears / horns / antenna
            when (characterId) {
                "PUPPY" -> {
                    // Floppy puppy ears
                    val leftEar = RectF(cx - r * 1.10f, cy - r * 0.75f, cx - r * 0.55f, cy + r * 0.25f)
                    val rightEar = RectF(cx + r * 0.55f, cy - r * 0.75f, cx + r * 1.10f, cy + r * 0.25f)
                    canvas.drawRoundRect(leftEar, 8f * d, 8f * d, earPaint)
                    canvas.drawRoundRect(rightEar, 8f * d, 8f * d, earPaint)
                }
                "TEDDY", "PANDA" -> {
                    // Round bear ears
                    canvas.drawCircle(cx - r * 0.72f, cy - r * 0.72f, r * 0.36f, earPaint)
                    canvas.drawCircle(cx + r * 0.72f, cy - r * 0.72f, r * 0.36f, earPaint)
                }
                "BULL" -> {
                    // Curved bull horns (Bhaisa)
                    val hornPath = Path().apply {
                        moveTo(cx - r * 0.55f, cy - r * 0.55f)
                        quadTo(cx - r * 1.25f, cy - r * 0.95f, cx - r * 0.90f, cy - r * 1.25f)
                        lineTo(cx - r * 0.40f, cy - r * 0.75f)
                        close()
                        moveTo(cx + r * 0.55f, cy - r * 0.55f)
                        quadTo(cx + r * 1.25f, cy - r * 0.95f, cx + r * 0.90f, cy - r * 1.25f)
                        lineTo(cx + r * 0.40f, cy - r * 0.75f)
                        close()
                    }
                    canvas.drawPath(hornPath, whitePaint)
                    canvas.drawCircle(cx - r * 0.82f, cy - r * 0.25f, r * 0.22f, earPaint)
                    canvas.drawCircle(cx + r * 0.82f, cy - r * 0.25f, r * 0.22f, earPaint)
                }
                "CAT" -> {
                    // Pointed kitty ears
                    val catEars = Path().apply {
                        moveTo(cx - r * 0.82f, cy - r * 0.30f)
                        lineTo(cx - r * 0.75f, cy - r * 1.12f)
                        lineTo(cx - r * 0.20f, cy - r * 0.72f)
                        close()
                        moveTo(cx + r * 0.82f, cy - r * 0.30f)
                        lineTo(cx + r * 0.75f, cy - r * 1.12f)
                        lineTo(cx + r * 0.20f, cy - r * 0.72f)
                        close()
                    }
                    canvas.drawPath(catEars, earPaint)
                }
                "ROBOT" -> {
                    // Cyber antenna
                    canvas.drawLine(cx, cy - r * 1.25f, cx, cy - r * 0.80f, holdRingPaint)
                    canvas.drawCircle(cx, cy - r * 1.28f, r * 0.16f, whitePaint)
                }
            }

            // Main head & little dancing feet
            canvas.drawCircle(cx - r * 0.42f, cy + r * 0.92f, r * 0.24f, earPaint)
            canvas.drawCircle(cx + r * 0.42f, cy + r * 0.92f, r * 0.24f, earPaint)
            canvas.drawCircle(cx, cy, r, bodyPaint)

            // Eyes & cute nose/snout
            if (characterId == "PANDA") {
                canvas.drawCircle(cx - r * 0.36f, cy - r * 0.12f, r * 0.24f, facePaint)
                canvas.drawCircle(cx + r * 0.36f, cy - r * 0.12f, r * 0.24f, facePaint)
                canvas.drawCircle(cx - r * 0.36f, cy - r * 0.14f, r * 0.10f, whitePaint)
                canvas.drawCircle(cx + r * 0.36f, cy - r * 0.14f, r * 0.10f, whitePaint)
            } else {
                canvas.drawCircle(cx - r * 0.34f, cy - r * 0.14f, r * 0.16f, facePaint)
                canvas.drawCircle(cx + r * 0.34f, cy - r * 0.14f, r * 0.16f, facePaint)
                canvas.drawCircle(cx - r * 0.37f, cy - r * 0.18f, r * 0.06f, whitePaint)
                canvas.drawCircle(cx + r * 0.37f, cy - r * 0.18f, r * 0.06f, whitePaint)
            }

            if (characterId == "BULL") {
                val snout = RectF(cx - r * 0.42f, cy + r * 0.08f, cx + r * 0.42f, cy + r * 0.52f)
                canvas.drawRoundRect(snout, 6f * d, 6f * d, earPaint)
                // Bull nose ring
                val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 2f * d
                    color = Color.parseColor("#FFD700")
                }
                canvas.drawCircle(cx, cy + r * 0.55f, r * 0.18f, ringPaint)
            } else {
                canvas.drawCircle(cx, cy + r * 0.16f, r * 0.14f, facePaint)
            }

            if (isDancing) {
                canvas.drawText("♪", cx + r * 0.72f, cy - r * 0.65f, notePaint)
            }

            // 3-Second Hold Circular Progress Indicator
            if (holdProgress > 0.02f) {
                val arcRect = RectF(3f * d, 3f * d, w - 3f * d, h - 3f * d)
                canvas.drawArc(arcRect, -90f, 360f * holdProgress, false, holdRingPaint)
            }
        }
    }
}
