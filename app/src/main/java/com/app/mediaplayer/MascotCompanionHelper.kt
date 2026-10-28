package com.app.mediaplayer

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
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

    private var hiddenOnVideoScreenForSession = false

    data class MascotCharacter(
        val id: String,
        val name: String,
        val badge: String
    )

    data class MascotColorOption(
        val name: String,
        val primaryHex: String,
        val secondaryHex: String,
        val bellyHex: String
    )

    val characters = listOf(
        MascotCharacter("PUPPY_GOLD", "Golden Retriever Puppy", "Lifelike Fluffy Puppy"),
        MascotCharacter("HUSKY_DOG", "Siberian Husky Puppy", "Blue-Eyed Snow Puppy"),
        MascotCharacter("POM_PUPPY", "Pomeranian Teddy Puppy", "Fluffy Cloud Coat"),
        MascotCharacter("TEDDY", "Plush Cuddle Teddy", "Realistic Velvet Bear"),
        MascotCharacter("BULL", "Royal Power Bull", "Muscular Horned Bull"),
        MascotCharacter("CAT", "Persian Fur Kitten", "Whiskered Soft Kitty"),
        MascotCharacter("PANDA", "Bamboo Baby Panda", "3D Plush Panda"),
        MascotCharacter("ROBOT", "Cyber Mecha Puppy", "Glowing Visor Bot")
    )

    val colorOptions = listOf(
        MascotColorOption("Golden Honey Fur", "#F5A623", "#8B4513", "#FFF5E4"),
        MascotColorOption("Creamy Biscuit", "#E6C29F", "#7E5232", "#FFFDF9"),
        MascotColorOption("Arctic Husky Silver", "#CFD8DC", "#37474F", "#FFFFFF"),
        MascotColorOption("Chocolate Velvet", "#8D6E63", "#3E2723", "#EFEBE9"),
        MascotColorOption("Sakura Plush Pink", "#F48FB1", "#880E4F", "#FCE4EC"),
        MascotColorOption("Cyber Neon Cyan", "#00E5FF", "#006064", "#E0F7FA"),
        MascotColorOption("Emerald Mint", "#00E676", "#1B5E20", "#E8F5E9"),
        MascotColorOption("Royal Lavender", "#B388FF", "#4A148C", "#F3E5F5")
    )

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
            .getString(KEY_CHARACTER, "PUPPY_GOLD") ?: "PUPPY_GOLD"
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
            val widthPx = (90 * d).toInt()
            val heightPx = (108 * d).toInt()
            val colorOpt = getSelectedColor(context)

            val mascotView = FullBodyArticulatedMascotView(
                context = context,
                d = d,
                characterId = getSelectedCharacter(context).id,
                primaryColor = Color.parseColor(colorOpt.primaryHex),
                secondaryColor = Color.parseColor(colorOpt.secondaryHex),
                bellyColor = Color.parseColor(colorOpt.bellyHex)
            ).apply {
                tag = "HYBRID_MASCOT_OVERLAY_TAG"
                elevation = 32f * d
            }

            val lp = when (hostContainer) {
                is FrameLayout -> FrameLayout.LayoutParams(
                    widthPx,
                    heightPx,
                    Gravity.TOP or Gravity.START
                )
                else -> ViewGroup.MarginLayoutParams(widthPx, heightPx)
            }
            hostContainer.addView(mascotView, lp)

            val mainHandler = Handler(Looper.getMainLooper())
            var velocityTracker: VelocityTracker? = null

            var isDragging = false
            var dX = 0f
            var dY = 0f
            var touchDownRawX = 0f
            var touchDownRawY = 0f
            var lastMoveRawY = 0f

            var posX = -1f
            var posY = -1f
            var velX = 0f
            var velY = 0f
            val gravityAccel = 1.65f * d
            val bounceDamping = 0.52f
            val floorFriction = 0.88f
            var walkDir = -1f
            var walkPauseTicks = 0
            var animPhase = 0f

            var lastHoveredPosition = RecyclerView.NO_POSITION
            var holdElapsedMs = 0L
            var stationaryHoldMs = 0L
            var autoScrollVelocityPx = 0

            val physicsAndWalkTicker = object : Runnable {
                override fun run() {
                    if (!mascotView.isAttachedToWindow) return

                    val parentView = mascotView.parent as? View
                    val parentW = (parentView?.width ?: 1080).toFloat()
                    val parentH = (parentView?.height ?: 1920).toFloat()
                    val floorY = (parentH - mascotView.height - (12 * d)).coerceAtLeast(0f)
                    val maxX = (parentW - mascotView.width).coerceAtLeast(0f)

                    if (posX < 0f || posY < 0f) {
                        posX = (maxX - 16f * d).coerceAtLeast(0f)
                        posY = floorY
                        mascotView.x = posX
                        mascotView.y = posY
                    }

                    val dancingNow = isDancingProvider()
                    mascotView.setDancingState(dancingNow)

                    if (!isDragging) {
                        if (posY < floorY - 1f || abs(velY) > 0.5f) {
                            velY += gravityAccel
                            posY += velY
                            posX += velX
                            mascotView.setAirborneState(true)

                            if (posX <= 0f) {
                                posX = 0f
                                velX = abs(velX) * 0.7f
                                walkDir = 1f
                            } else if (posX >= maxX) {
                                posX = maxX
                                velX = -abs(velX) * 0.7f
                                walkDir = -1f
                            }

                            if (posY <= 0f) {
                                posY = 0f
                                velY = abs(velY) * 0.6f
                            }

                            if (posY >= floorY) {
                                posY = floorY
                                if (abs(velY) > 6f * d) {
                                    velY = -velY * bounceDamping
                                    velX *= floorFriction
                                } else {
                                    velY = 0f
                                    mascotView.setAirborneState(false)
                                }
                            }
                        } else {
                            posY = floorY
                            velY = 0f
                            mascotView.setAirborneState(false)

                            if (abs(velX) > 0.8f * d) {
                                posX = (posX + velX).coerceIn(0f, maxX)
                                velX *= floorFriction
                                mascotView.setFacingRight(velX > 0f)
                                mascotView.setWalkingState(true)
                            } else {
                                velX = 0f
                                if (dancingNow) {
                                    mascotView.setWalkingState(false)
                                } else {
                                    if (walkPauseTicks > 0) {
                                        walkPauseTicks--
                                        mascotView.setWalkingState(false)
                                        if (walkPauseTicks == 1 && Math.random() < 0.35) {
                                            walkDir = -walkDir
                                        }
                                    } else {
                                        val stepSpeed = 1.35f * d
                                        posX += walkDir * stepSpeed
                                        mascotView.setFacingRight(walkDir > 0f)
                                        mascotView.setWalkingState(true)

                                        if (posX <= 6f * d) {
                                            posX = 6f * d
                                            walkDir = 1f
                                            walkPauseTicks = 45
                                        } else if (posX >= maxX - 6f * d) {
                                            posX = maxX - 6f * d
                                            walkDir = -1f
                                            walkPauseTicks = 45
                                        } else if (Math.random() < 0.008) {
                                            walkPauseTicks = 55
                                        }
                                    }
                                }
                            }
                        }
                        mascotView.x = posX
                        mascotView.y = posY
                    } else {
                        if (recyclerView != null && autoScrollVelocityPx != 0) {
                            recyclerView.scrollBy(0, autoScrollVelocityPx)
                        }

                        val movedDist = abs(mascotView.x - (touchDownRawX + dX)) + abs(mascotView.y - (touchDownRawY + dY))
                        if (movedDist < 14f * d && abs(autoScrollVelocityPx) < (6 * d).toInt()) {
                            var hoveredIndex = RecyclerView.NO_POSITION
                            if (recyclerView != null && recyclerView.isShown) {
                                val rvLoc = IntArray(2)
                                val mLoc = IntArray(2)
                                recyclerView.getLocationOnScreen(rvLoc)
                                mascotView.getLocationOnScreen(mLoc)
                                val cxOnRv = (mLoc[0] + mascotView.width / 2f) - rvLoc[0]
                                val cyOnRv = (mLoc[1] + mascotView.height / 2f) - rvLoc[1]
                                val child = recyclerView.findChildViewUnder(cxOnRv, cyOnRv)
                                if (child != null) {
                                    hoveredIndex = recyclerView.getChildAdapterPosition(child)
                                }
                            }

                            if (hoveredIndex != RecyclerView.NO_POSITION) {
                                if (hoveredIndex == lastHoveredPosition) {
                                    holdElapsedMs += 20L
                                    mascotView.setHoldProgress((holdElapsedMs / 2000f).coerceIn(0f, 1f))
                                    if (holdElapsedMs >= 2000L) {
                                        isDragging = false
                                        mascotView.setHoldProgress(0f)
                                        holdElapsedMs = 0L
                                        if (isVideoScreen) {
                                            hiddenOnVideoScreenForSession = true
                                            hostContainer.removeView(mascotView)
                                        }
                                        onHoldItemTriggered(hoveredIndex)
                                        return
                                    }
                                } else {
                                    lastHoveredPosition = hoveredIndex
                                    holdElapsedMs = 0L
                                    mascotView.setHoldProgress(0f)
                                }
                            } else {
                                stationaryHoldMs += 20L
                                mascotView.setHoldProgress((stationaryHoldMs / 2000f).coerceIn(0f, 1f))
                                if (stationaryHoldMs >= 2000L) {
                                    isDragging = false
                                    mascotView.setHoldProgress(0f)
                                    hiddenOnVideoScreenForSession = true
                                    hostContainer.removeView(mascotView)
                                    Toast.makeText(
                                        context,
                                        "Puppy hidden for this session",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return
                                }
                            }
                        } else {
                            holdElapsedMs = 0L
                            stationaryHoldMs = 0L
                            mascotView.setHoldProgress(0f)
                        }
                    }

                    animPhase += if (dancingNow) 0.24f else 0.16f
                    mascotView.stepAnimationPhase(animPhase)
                    mainHandler.postDelayed(this, 20L)
                }
            }
            mainHandler.post(physicsAndWalkTicker)

            mascotView.setOnTouchListener { v, event ->
                val parentW = (v.parent as? View)?.width?.toFloat() ?: 1080f
                val parentH = (v.parent as? View)?.height?.toFloat() ?: 1920f

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        isDragging = true
                        velX = 0f
                        velY = 0f
                        dX = v.x - event.rawX
                        dY = v.y - event.rawY
                        touchDownRawX = event.rawX
                        touchDownRawY = event.rawY
                        lastMoveRawY = event.rawY
                        lastHoveredPosition = RecyclerView.NO_POSITION
                        holdElapsedMs = 0L
                        stationaryHoldMs = 0L
                        autoScrollVelocityPx = 0
                        mascotView.setHoldProgress(0f)
                        mascotView.setAirborneState(true)

                        velocityTracker?.recycle()
                        velocityTracker = VelocityTracker.obtain()
                        velocityTracker?.addMovement(event)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        velocityTracker?.addMovement(event)
                        val newX = (event.rawX + dX).coerceIn(0f, (parentW - v.width).coerceAtLeast(0f))
                        val newY = (event.rawY + dY).coerceIn(0f, (parentH - v.height).coerceAtLeast(0f))
                        val deltaY = event.rawY - lastMoveRawY
                        lastMoveRawY = event.rawY

                        if (abs(event.rawX - touchDownRawX) > 6f) {
                            mascotView.setFacingRight(event.rawX > touchDownRawX)
                        }

                        posX = newX
                        posY = newY
                        v.x = newX
                        v.y = newY

                        autoScrollVelocityPx = when {
                            newY < parentH * 0.20f -> -(18 * d).toInt()
                            newY > parentH * 0.78f -> (18 * d).toInt()
                            abs(deltaY) > 4f -> (deltaY * 1.7f).toInt()
                            else -> 0
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        velocityTracker?.addMovement(event)
                        velocityTracker?.computeCurrentVelocity(1000)
                        val vx = velocityTracker?.xVelocity ?: 0f
                        val vy = velocityTracker?.yVelocity ?: 0f
                        velocityTracker?.recycle()
                        velocityTracker = null

                        velX = (vx / 48f).coerceIn(-34f * d, 34f * d)
                        velY = (vy / 48f).coerceIn(-40f * d, 36f * d)

                        isDragging = false
                        autoScrollVelocityPx = 0
                        holdElapsedMs = 0L
                        stationaryHoldMs = 0L
                        mascotView.setHoldProgress(0f)
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
                setStroke((1.4f * d).toInt(), accentColor)
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
            text = "Realistic Puppy & Companion Studio"
            setTextColor(Color.WHITE)
            textSize = 16.5f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnClose = TextView(context).apply {
            text = "Done"
            setTextColor(Color.WHITE)
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((12 * d).toInt(), (6 * d).toInt(), (12 * d).toInt(), (6 * d).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 14f * d
                setColor(Color.parseColor("#22FFFFFF"))
            }
            setOnClickListener { dialog.dismiss() }
        }

        headerRow.addView(tvHeader)
        headerRow.addView(btnClose)
        root.addView(headerRow)

        val scroll = ScrollView(context)
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

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
            text = "Enable Realistic Walking Puppy\n(Walks on floor • Throw anywhere • Hold 2s)"
            setTextColor(Color.WHITE)
            textSize = 12f
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

        val tvCharTitle = TextView(context).apply {
            text = "SELECT REALISTIC PUPPY BREED"
            setTextColor(Color.WHITE)
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
                setPadding((8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = (82 * d).toInt()
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
                    Toast.makeText(context, "Companion Active: ${character.name}", Toast.LENGTH_SHORT).show()
                }
            }

            val previewView = FullBodyArticulatedMascotView(
                context = context,
                d = d,
                characterId = character.id,
                primaryColor = Color.parseColor(currentColor.primaryHex),
                secondaryColor = Color.parseColor(currentColor.secondaryHex),
                bellyColor = Color.parseColor(currentColor.bellyHex)
            ).apply {
                layoutParams = LinearLayout.LayoutParams((52 * d).toInt(), (64 * d).toInt()).apply {
                    marginEnd = (6 * d).toInt()
                }
            }

            val txtCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            val tvName = TextView(context).apply {
                text = character.name
                setTextColor(Color.WHITE)
                textSize = 11.5f
                typeface = Typeface.DEFAULT_BOLD
            }
            val tvBadge = TextView(context).apply {
                text = character.badge
                setTextColor(Color.parseColor("#B0C4DE"))
                textSize = 10f
            }
            txtCol.addView(tvName)
            txtCol.addView(tvBadge)

            card.addView(previewView)
            card.addView(txtCol)
            charGrid.addView(card)
        }
        col.addView(charGrid)

        val tvColorTitle = TextView(context).apply {
            text = "CHOOSE FUR COAT SHADING"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((4 * d).toInt(), (14 * d).toInt(), 0, (8 * d).toInt())
        }
        col.addView(tvColorTitle)

        val colorGrid = GridLayout(context).apply {
            columnCount = 4
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val savedColorIdx = prefs.getInt(KEY_COLOR_INDEX, 0)
        colorOptions.forEachIndexed { index, colorOpt ->
            val swatch = TextView(context).apply {
                text = if (index == savedColorIdx) "✓" else ""
                setTextColor(Color.parseColor("#0A0A10"))
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                layoutParams = GridLayout.LayoutParams().apply {
                    width = (44 * d).toInt()
                    height = (44 * d).toInt()
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins((6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt())
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
                    Toast.makeText(context, "Coat Color: ${colorOpt.name}", Toast.LENGTH_SHORT).show()
                }
            }
            colorGrid.addView(swatch)
        }
        col.addView(colorGrid)

        scroll.addView(col)
        root.addView(scroll)
        dialog.show()
    }

    /**
     * Realistic 3D-Shaded Plush & Fur Puppy Renderer:
     * Uses RadialGradient 3D fur shading, glossy multi-catchlight eyes, shaded 3D snout,
     * realistic nose shine, soft blush cheeks, paw pads, and smooth articulated limbs.
     */
    class FullBodyArticulatedMascotView(
        context: Context,
        private val d: Float,
        private val characterId: String,
        private val primaryColor: Int,
        private val secondaryColor: Int,
        private val bellyColor: Int
    ) : View(context) {

        private var holdProgress = 0f
        private var isDancing = false
        private var isWalking = false
        private var isAirborne = false
        private var facingRight = false
        private var phase = 0f

        private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#55000000")
        }

        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = primaryColor
        }

        private val head3dPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = primaryColor
        }

        private val secondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = secondaryColor
        }

        private val bellyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = bellyColor
        }

        private val furOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.4f * d
            color = Color.parseColor("#2A000000")
        }

        private val darkEyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#11131A")
        }

        private val huskyIrisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#29B6F6")
        }

        private val nosePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#1B1412")
        }

        private val tonguePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#FF6B81")
        }

        private val cheekBlushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#44FF5252")
        }

        private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }

        private val collarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#D32F2F")
        }

        private val bellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#FFD700")
        }

        private val holdRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.2f * d
            strokeCap = Paint.Cap.ROUND
            color = Color.parseColor("#00E676")
        }

        private val mouthStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f * d
            strokeCap = Paint.Cap.ROUND
            color = Color.parseColor("#2B1D19")
        }

        fun setHoldProgress(progress: Float) {
            holdProgress = progress.coerceIn(0f, 1f)
            invalidate()
        }

        fun setDancingState(dancing: Boolean) {
            isDancing = dancing
        }

        fun setWalkingState(walking: Boolean) {
            isWalking = walking
        }

        fun setAirborneState(airborne: Boolean) {
            isAirborne = airborne
        }

        fun setFacingRight(right: Boolean) {
            facingRight = right
        }

        fun stepAnimationPhase(newPhase: Float) {
            phase = newPhase
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val cx = w / 2f
            val bounceY = when {
                isDancing -> abs(sin(phase * 1.5f)) * (-5.2f * d)
                isWalking -> abs(sin(phase)) * (-2.4f * d)
                else -> sin(phase * 0.45f) * (-1.2f * d)
            }

            if (!isAirborne) {
                val shadowRect = RectF(cx - w * 0.28f, h - 7f * d, cx + w * 0.28f, h - 2f * d)
                canvas.drawOval(shadowRect, shadowPaint)
            }

            canvas.save()
            if (facingRight) {
                canvas.scale(-1f, 1f, cx, h / 2f)
            }

            val headCenterY = h * 0.31f + bounceY
            val headR = w * 0.28f
            val torsoTopY = headCenterY + headR * 0.68f
            val torsoBottomY = h * 0.77f + bounceY
            val torsoHalfW = w * 0.22f

            bodyPaint.shader = LinearGradient(
                cx - torsoHalfW,
                torsoTopY,
                cx + torsoHalfW,
                torsoBottomY,
                primaryColor,
                secondaryColor,
                Shader.TileMode.CLAMP
            )

            // 1. Fluffy Wagging Tail
            val tailAngle = sin(phase * 2.4f) * 30f
            canvas.save()
            canvas.rotate(tailAngle, cx + torsoHalfW * 0.75f, torsoBottomY - 7f * d)
            val tailPath = Path().apply {
                moveTo(cx + torsoHalfW * 0.55f, torsoBottomY - 6f * d)
                quadTo(
                    cx + torsoHalfW * 1.65f,
                    torsoBottomY - 22f * d,
                    cx + torsoHalfW * 1.45f,
                    torsoBottomY - 5f * d
                )
                close()
            }
            canvas.drawPath(tailPath, bodyPaint)
            canvas.drawPath(tailPath, furOutlinePaint)
            canvas.restore()

            // 2. Articulated Hind Legs & Soft Paw Pads
            val leftLegAngle = when {
                isAirborne -> 22f
                isDancing -> sin(phase * 1.5f) * 25f
                isWalking -> sin(phase) * 28f
                else -> 0f
            }
            val rightLegAngle = when {
                isAirborne -> -22f
                isDancing -> -sin(phase * 1.5f) * 25f
                isWalking -> -sin(phase) * 28f
                else -> 0f
            }

            val legWidth = 11f * d
            val legHeight = 19f * d
            val leftHipX = cx - torsoHalfW * 0.48f
            val rightHipX = cx + torsoHalfW * 0.48f
            val hipY = torsoBottomY - 4f * d

            // Left Leg
            canvas.save()
            canvas.rotate(leftLegAngle, leftHipX, hipY)
            val leftLegRect = RectF(leftHipX - legWidth / 2f, hipY, leftHipX + legWidth / 2f, hipY + legHeight)
            canvas.drawRoundRect(leftLegRect, 6f * d, 6f * d, bodyPaint)
            canvas.drawRoundRect(leftLegRect, 6f * d, 6f * d, furOutlinePaint)
            val leftPawRect = RectF(
                leftHipX - legWidth * 0.68f,
                hipY + legHeight - 5.5f * d,
                leftHipX + legWidth * 0.62f,
                hipY + legHeight + 2f * d
            )
            canvas.drawOval(leftPawRect, bellyPaint)
            canvas.drawOval(leftPawRect, furOutlinePaint)
            canvas.restore()

            // Right Leg
            canvas.save()
            canvas.rotate(rightLegAngle, rightHipX, hipY)
            val rightLegRect = RectF(rightHipX - legWidth / 2f, hipY, rightHipX + legWidth / 2f, hipY + legHeight)
            canvas.drawRoundRect(rightLegRect, 6f * d, 6f * d, bodyPaint)
            canvas.drawRoundRect(rightLegRect, 6f * d, 6f * d, furOutlinePaint)
            val rightPawRect = RectF(
                rightHipX - legWidth * 0.68f,
                hipY + legHeight - 5.5f * d,
                rightHipX + legWidth * 0.62f,
                hipY + legHeight + 2f * d
            )
            canvas.drawOval(rightPawRect, bellyPaint)
            canvas.drawOval(rightPawRect, furOutlinePaint)
            canvas.restore()

            // 3. Plush Torso & Soft Cream Chest/Belly Fur
            val torsoRect = RectF(cx - torsoHalfW, torsoTopY, cx + torsoHalfW, torsoBottomY)
            canvas.drawRoundRect(torsoRect, 15f * d, 15f * d, bodyPaint)
            canvas.drawRoundRect(torsoRect, 15f * d, 15f * d, furOutlinePaint)

            val bellyRect = RectF(
                cx - torsoHalfW * 0.68f,
                torsoTopY + 4f * d,
                cx + torsoHalfW * 0.68f,
                torsoBottomY - 3.5f * d
            )
            canvas.drawOval(bellyRect, bellyPaint)

            // 4. Articulated Front Paws / Arms
            val leftArmAngle = when {
                isAirborne -> -120f
                isDancing -> -105f + sin(phase * 1.8f) * 34f
                isWalking -> -sin(phase) * 26f
                else -> 14f + sin(phase * 0.5f) * 5f
            }
            val rightArmAngle = when {
                isAirborne -> 120f
                isDancing -> 105f - cos(phase * 1.8f) * 34f
                isWalking -> sin(phase) * 26f
                else -> -14f - sin(phase * 0.5f) * 5f
            }

            val armW = 9.5f * d
            val armH = 18.5f * d
            val shoulderY = torsoTopY + 5f * d
            val leftShoulderX = cx - torsoHalfW * 0.90f
            val rightShoulderX = cx + torsoHalfW * 0.90f

            canvas.save()
            canvas.rotate(leftArmAngle, leftShoulderX, shoulderY)
            val leftArmRect = RectF(leftShoulderX - armW / 2f, shoulderY, leftShoulderX + armW / 2f, shoulderY + armH)
            canvas.drawRoundRect(leftArmRect, 5f * d, 5f * d, bodyPaint)
            canvas.drawRoundRect(leftArmRect, 5f * d, 5f * d, furOutlinePaint)
            canvas.drawCircle(leftShoulderX, shoulderY + armH - 2f * d, armW * 0.52f, bellyPaint)
            canvas.restore()

            canvas.save()
            canvas.rotate(rightArmAngle, rightShoulderX, shoulderY)
            val rightArmRect = RectF(rightShoulderX - armW / 2f, shoulderY, rightShoulderX + armW / 2f, shoulderY + armH)
            canvas.drawRoundRect(rightArmRect, 5f * d, 5f * d, bodyPaint)
            canvas.drawRoundRect(rightArmRect, 5f * d, 5f * d, furOutlinePaint)
            canvas.drawCircle(rightShoulderX, shoulderY + armH - 2f * d, armW * 0.52f, bellyPaint)
            canvas.restore()

            // 5. Leather Red Collar & Metallic Gold Bell
            val collarRect = RectF(
                cx - torsoHalfW * 0.78f,
                torsoTopY - 2.5f * d,
                cx + torsoHalfW * 0.78f,
                torsoTopY + 4.2f * d
            )
            canvas.drawRoundRect(collarRect, 3.5f * d, 3.5f * d, collarPaint)
            canvas.drawCircle(cx, torsoTopY + 4.2f * d, 4.2f * d, bellPaint)
            canvas.drawCircle(cx - 1f * d, torsoTopY + 3.2f * d, 1.2f * d, whitePaint)

            // 6. 3D-Shaded Puppy Head, Breed Ears, Muzzle, Glossy Eyes & Tongue
            val headTilt = if (isDancing) sin(phase * 1.5f) * 8f else sin(phase * 0.6f) * 2.5f
            canvas.save()
            canvas.rotate(headTilt, cx, headCenterY)

            head3dPaint.shader = RadialGradient(
                cx - headR * 0.25f,
                headCenterY - headR * 0.28f,
                headR * 1.35f,
                bellyColor,
                primaryColor,
                Shader.TileMode.CLAMP
            )

            // Breed-Specific Realistic Ears (Behind Head)
            when (characterId) {
                "PUPPY_GOLD", "POM_PUPPY" -> {
                    val earBounce = sin(phase * 1.5f) * 6f
                    canvas.save()
                    canvas.rotate(22f + earBounce, cx - headR * 0.78f, headCenterY - headR * 0.35f)
                    val leftEar = RectF(
                        cx - headR * 1.28f,
                        headCenterY - headR * 0.45f,
                        cx - headR * 0.62f,
                        headCenterY + headR * 0.68f
                    )
                    canvas.drawOval(leftEar, secondaryPaint)
                    canvas.drawOval(leftEar, furOutlinePaint)
                    canvas.restore()

                    canvas.save()
                    canvas.rotate(-22f - earBounce, cx + headR * 0.78f, headCenterY - headR * 0.35f)
                    val rightEar = RectF(
                        cx + headR * 0.62f,
                        headCenterY - headR * 0.45f,
                        cx + headR * 1.28f,
                        headCenterY + headR * 0.68f
                    )
                    canvas.drawOval(rightEar, secondaryPaint)
                    canvas.drawOval(rightEar, furOutlinePaint)
                    canvas.restore()
                }
                "HUSKY_DOG", "CAT" -> {
                    val outerEarPath = Path().apply {
                        moveTo(cx - headR * 0.82f, headCenterY - headR * 0.22f)
                        lineTo(cx - headR * 0.78f, headCenterY - headR * 1.28f)
                        lineTo(cx - headR * 0.16f, headCenterY - headR * 0.76f)
                        close()
                        moveTo(cx + headR * 0.82f, headCenterY - headR * 0.22f)
                        lineTo(cx + headR * 0.78f, headCenterY - headR * 1.28f)
                        lineTo(cx + headR * 0.16f, headCenterY - headR * 0.76f)
                        close()
                    }
                    canvas.drawPath(outerEarPath, secondaryPaint)
                    canvas.drawPath(outerEarPath, furOutlinePaint)

                    val innerPinkEar = Path().apply {
                        moveTo(cx - headR * 0.70f, headCenterY - headR * 0.36f)
                        lineTo(cx - headR * 0.68f, headCenterY - headR * 1.05f)
                        lineTo(cx - headR * 0.30f, headCenterY - headR * 0.72f)
                        close()
                        moveTo(cx + headR * 0.70f, headCenterY - headR * 0.36f)
                        lineTo(cx + headR * 0.68f, headCenterY - headR * 1.05f)
                        lineTo(cx + headR * 0.30f, headCenterY - headR * 0.72f)
                        close()
                    }
                    canvas.drawPath(innerPinkEar, tonguePaint)
                }
                "BULL" -> {
                    val hornPath = Path().apply {
                        moveTo(cx - headR * 0.6f, headCenterY - headR * 0.55f)
                        quadTo(cx - headR * 1.3f, headCenterY - headR * 0.95f, cx - headR * 0.92f, headCenterY - headR * 1.3f)
                        lineTo(cx - headR * 0.4f, headCenterY - headR * 0.78f)
                        close()
                        moveTo(cx + headR * 0.6f, headCenterY - headR * 0.55f)
                        quadTo(cx + headR * 1.3f, headCenterY - headR * 0.95f, cx + headR * 0.92f, headCenterY - headR * 1.3f)
                        lineTo(cx + headR * 0.4f, headCenterY - headR * 0.78f)
                        close()
                    }
                    canvas.drawPath(hornPath, whitePaint)
                    canvas.drawPath(hornPath, furOutlinePaint)
                }
                else -> {
                    canvas.drawCircle(cx - headR * 0.76f, headCenterY - headR * 0.72f, headR * 0.34f, secondaryPaint)
                    canvas.drawCircle(cx + headR * 0.76f, headCenterY - headR * 0.72f, headR * 0.34f, secondaryPaint)
                    canvas.drawCircle(cx - headR * 0.76f, headCenterY - headR * 0.72f, headR * 0.18f, bellyPaint)
                    canvas.drawCircle(cx + headR * 0.76f, headCenterY - headR * 0.72f, headR * 0.18f, bellyPaint)
                }
            }

            // Rounded Fluffy Cheek Silhouette
            val headRect = RectF(
                cx - headR * 1.06f,
                headCenterY - headR * 0.95f,
                cx + headR * 1.06f,
                headCenterY + headR * 0.95f
            )
            canvas.drawOval(headRect, head3dPaint)
            canvas.drawOval(headRect, furOutlinePaint)

            // Husky Forehead Fur Mask or Panda Eye Patches
            if (characterId == "HUSKY_DOG") {
                val maskRect = RectF(
                    cx - headR * 0.85f,
                    headCenterY - headR * 0.85f,
                    cx + headR * 0.85f,
                    headCenterY + headR * 0.15f
                )
                canvas.drawArc(maskRect, 180f, 180f, true, secondaryPaint)
            } else if (characterId == "PANDA") {
                canvas.drawOval(
                    RectF(cx - headR * 0.68f, headCenterY - headR * 0.32f, cx - headR * 0.18f, headCenterY + headR * 0.16f),
                    nosePaint
                )
                canvas.drawOval(
                    RectF(cx + headR * 0.18f, headCenterY - headR * 0.32f, cx + headR * 0.68f, headCenterY + headR * 0.16f),
                    nosePaint
                )
            }

            // Soft Rosy Cheek Blush
            canvas.drawOval(
                RectF(cx - headR * 0.82f, headCenterY + headR * 0.04f, cx - headR * 0.44f, headCenterY + headR * 0.30f),
                cheekBlushPaint
            )
            canvas.drawOval(
                RectF(cx + headR * 0.44f, headCenterY + headR * 0.04f, cx + headR * 0.82f, headCenterY + headR * 0.30f),
                cheekBlushPaint
            )

            // 3D Raised Snout / Muzzle
            val snoutRect = RectF(
                cx - headR * 0.46f,
                headCenterY - headR * 0.02f,
                cx + headR * 0.46f,
                headCenterY + headR * 0.58f
            )
            canvas.drawOval(snoutRect, bellyPaint)
            canvas.drawOval(snoutRect, furOutlinePaint)

            // Expressive Glossy Puppy Eyes with Dual Specular Catchlights
            val leftEyeX = cx - headR * 0.42f
            val rightEyeX = cx + headR * 0.42f
            val eyeY = headCenterY - headR * 0.14f
            val eyeR = headR * 0.18f

            canvas.drawCircle(leftEyeX, eyeY, eyeR, darkEyePaint)
            canvas.drawCircle(rightEyeX, eyeY, eyeR, darkEyePaint)

            if (characterId == "HUSKY_DOG") {
                canvas.drawCircle(leftEyeX, eyeY, eyeR * 0.65f, huskyIrisPaint)
                canvas.drawCircle(rightEyeX, eyeY, eyeR * 0.65f, huskyIrisPaint)
                canvas.drawCircle(leftEyeX, eyeY, eyeR * 0.35f, darkEyePaint)
                canvas.drawCircle(rightEyeX, eyeY, eyeR * 0.35f, darkEyePaint)
            }

            // Primary & Secondary Eye Shine Catchlights
            canvas.drawCircle(leftEyeX - eyeR * 0.30f, eyeY - eyeR * 0.30f, eyeR * 0.34f, whitePaint)
            canvas.drawCircle(rightEyeX - eyeR * 0.30f, eyeY - eyeR * 0.30f, eyeR * 0.34f, whitePaint)
            canvas.drawCircle(leftEyeX + eyeR * 0.25f, eyeY + eyeR * 0.25f, eyeR * 0.16f, whitePaint)
            canvas.drawCircle(rightEyeX + eyeR * 0.25f, eyeY + eyeR * 0.25f, eyeR * 0.16f, whitePaint)

            // Cute Pink Puppy Tongue (Pops out happily when walking or dancing)
            if (isDancing || isWalking) {
                val tongueLen = headR * 0.34f + abs(sin(phase * 2f)) * (2.5f * d)
                val tongueRect = RectF(
                    cx - headR * 0.16f,
                    headCenterY + headR * 0.30f,
                    cx + headR * 0.16f,
                    headCenterY + headR * 0.30f + tongueLen
                )
                canvas.drawRoundRect(tongueRect, 5f * d, 5f * d, tonguePaint)
            }

            // Realistic Curved W-Smile Mouth
            val mouthY = headCenterY + headR * 0.26f
            canvas.drawArc(
                RectF(cx - headR * 0.26f, mouthY - headR * 0.08f, cx, mouthY + headR * 0.14f),
                0f,
                180f,
                false,
                mouthStrokePaint
            )
            canvas.drawArc(
                RectF(cx, mouthY - headR * 0.08f, cx + headR * 0.26f, mouthY + headR * 0.14f),
                0f,
                180f,
                false,
                mouthStrokePaint
            )

            // Glossy Triangular Puppy Nose with Highlight
            val noseW = headR * 0.22f
            val noseH = headR * 0.16f
            val noseTopY = headCenterY + headR * 0.06f
            val noseRect = RectF(cx - noseW, noseTopY, cx + noseW, noseTopY + noseH)
            canvas.drawRoundRect(noseRect, 5f * d, 5f * d, nosePaint)
            canvas.drawOval(
                RectF(cx - noseW * 0.5f, noseTopY + 1.5f * d, cx + noseW * 0.2f, noseTopY + noseH * 0.45f),
                whitePaint
            )

            canvas.restore()
            canvas.restore()

            // 7. 2-Second Hold Progress Ring
            if (holdProgress > 0.02f) {
                val ringRect = RectF(4f * d, 4f * d, w - 4f * d, h - 4f * d)
                canvas.drawArc(ringRect, -90f, 360f * holdProgress, false, holdRingPaint)
            }
        }
    }
}
