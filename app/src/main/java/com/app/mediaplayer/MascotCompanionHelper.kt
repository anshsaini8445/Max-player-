package com.app.mediaplayer

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
        MascotCharacter("PUPPY_GOLD", "Golden Puppy", "Real Walking Puppy"),
        MascotCharacter("HUSKY_DOG", "Siberian Husky", "Snow Wolf Dog"),
        MascotCharacter("POM_PUPPY", "Fluffy Pomeranian", "Cute Pocket Dog"),
        MascotCharacter("TEDDY", "Dancing Teddy", "Full-Body Bear"),
        MascotCharacter("BULL", "Power Bull", "Strong Bhaisa"),
        MascotCharacter("CAT", "Neon Kitty", "Agile Walking Cat"),
        MascotCharacter("PANDA", "Groove Panda", "Bamboo Panda"),
        MascotCharacter("ROBOT", "Cyber Bot", "8K Mecha Companion")
    )

    val colorOptions = listOf(
        MascotColorOption("Golden Honey", "#FFB300", "#8D4E00", "#FFF8E1"),
        MascotColorOption("Cyber Cyan", "#00E5FF", "#006064", "#E0F7FA"),
        MascotColorOption("Snow White & Blue", "#ECEFF1", "#37474F", "#FFFFFF"),
        MascotColorOption("Sakura Pink", "#FF4081", "#880E4F", "#FCE4EC"),
        MascotColorOption("Emerald Neon", "#00E676", "#1B5E20", "#E8F5E9"),
        MascotColorOption("Royal Purple", "#B388FF", "#4A148C", "#F3E5F5"),
        MascotColorOption("Chocolate Brown", "#8D6E63", "#3E2723", "#D7CCC8"),
        MascotColorOption("Crimson Flame", "#FF5252", "#B71C1C", "#FFEBEE")
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

    /**
     * Full-Body Physics & Autonomous Walking Mascot Engine:
     * 1. Has a complete articulated body: Head (Sar), Belly/Torso (Pet), 2 Arms (Do Hath), 2 Legs (Do Tang), and Tail.
     * 2. Real Gravity & Fling Physics: Falls down to the bottom floor, can be thrown up/down/left/right and bounces off walls.
     * 3. Autonomous Walking: Walks left and right along the floor on its two legs when idle.
     * 4. Beat Dancing: Dances with arms, legs, and head when music or background video audio plays.
     * 5. 2-Second Hold: Holding for 2 seconds over a song/video plays it, or holding stationary for 2 seconds dismisses it.
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
            val widthPx = (86 * d).toInt()
            val heightPx = (104 * d).toInt()
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

            // Physics & Autonomous Walking state
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
                        // Apply gravity when above the floor or moving vertically
                        if (posY < floorY - 1f || abs(velY) > 0.5f) {
                            velY += gravityAccel
                            posY += velY
                            posX += velX
                            mascotView.setAirborneState(true)

                            // Bounce off left/right screen edges
                            if (posX <= 0f) {
                                posX = 0f
                                velX = abs(velX) * 0.7f
                                walkDir = 1f
                            } else if (posX >= maxX) {
                                posX = maxX
                                velX = -abs(velX) * 0.7f
                                walkDir = -1f
                            }

                            // Bounce off top ceiling
                            if (posY <= 0f) {
                                posY = 0f
                                velY = abs(velY) * 0.6f
                            }

                            // Land on bottom floor
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
                            // Standing on the bottom floor
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
                                // Autonomous floor walking when not dancing
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
                        // Currently being held/dragged by user
                        if (recyclerView != null && autoScrollVelocityPx != 0) {
                            recyclerView.scrollBy(0, autoScrollVelocityPx)
                        }

                        // Check if held stationary to either trigger item playback or dismiss after 2.0 seconds
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
                                // Stationary 2-second hold on empty space removes the mascot for the session
                                stationaryHoldMs += 20L
                                mascotView.setHoldProgress((stationaryHoldMs / 2000f).coerceIn(0f, 1f))
                                if (stationaryHoldMs >= 2000L) {
                                    isDragging = false
                                    mascotView.setHoldProgress(0f)
                                    hiddenOnVideoScreenForSession = true
                                    hostContainer.removeView(mascotView)
                                    Toast.makeText(
                                        context,
                                        "Puppy hidden for this session (Re-enable anytime in Me -> Dancing Mascot)",
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

                        // Convert finger fling velocity into physics throw velocity
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
            text = "Real Walking & Dancing Puppy Studio"
            setTextColor(Color.WHITE)
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
            text = "Enable Walking & Gravity Puppy Companion\n(Walks on floor • Throw anywhere • Hold 2s to play or hide)"
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
            text = "CHOOSE FULL-BODY PUPPY / COMPANION"
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
                    height = (78 * d).toInt()
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
                layoutParams = LinearLayout.LayoutParams((48 * d).toInt(), (58 * d).toInt()).apply {
                    marginEnd = (6 * d).toInt()
                }
            }

            val txtCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            val tvName = TextView(context).apply {
                text = character.name
                setTextColor(Color.WHITE)
                textSize = 12f
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
            text = "CHOOSE PUPPY COAT & COLOR PALETTE"
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
     * Full-Body Articulated Mascot View with:
     * - Head (Sar), Torso/Belly (Pet), 2 Swingable Arms (Do Hath), 2 Walking/Dancing Legs (Do Tang), and Wagging Tail.
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

        private val secondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = secondaryColor
        }

        private val bellyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = bellyColor
        }

        private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f * d
            color = Color.parseColor("#22000000")
        }

        private val darkFeaturePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#14141F")
        }

        private val tonguePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#FF5252")
        }

        private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }

        private val collarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#FF1744")
        }

        private val bellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#FFD700")
        }

        private val holdRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.5f * d
            strokeCap = Paint.Cap.ROUND
            color = Color.parseColor("#00E676")
        }

        private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#00FFFF")
            textSize = 12f * d
            typeface = Typeface.DEFAULT_BOLD
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
                isDancing -> abs(sin(phase * 1.5f)) * (-5.5f * d)
                isWalking -> abs(sin(phase)) * (-2.5f * d)
                else -> sin(phase * 0.4f) * (-1.2f * d)
            }

            // 1. Ground Shadow under the two feet
            if (!isAirborne) {
                val shadowRect = RectF(cx - w * 0.28f, h - 7f * d, cx + w * 0.28f, h - 2f * d)
                canvas.drawOval(shadowRect, shadowPaint)
            }

            canvas.save()
            if (facingRight) {
                canvas.scale(-1f, 1f, cx, h / 2f)
            }

            val headCenterY = h * 0.30f + bounceY
            val headR = w * 0.27f
            val torsoTopY = headCenterY + headR * 0.72f
            val torsoBottomY = h * 0.77f + bounceY
            val torsoHalfW = w * 0.22f

            // 2. Wagging Tail behind the torso
            val tailAngle = sin(phase * 2.2f) * 28f
            canvas.save()
            canvas.rotate(tailAngle, cx + torsoHalfW * 0.8f, torsoBottomY - 6f * d)
            val tailRect = RectF(
                cx + torsoHalfW * 0.5f,
                torsoBottomY - 16f * d,
                cx + torsoHalfW * 1.55f,
                torsoBottomY - 6f * d
            )
            canvas.drawRoundRect(tailRect, 6f * d, 6f * d, secondaryPaint)
            canvas.restore()

            // 3. TWO LEGS (Do Tang) with Walking / Dancing / Jumping articulation
            val leftLegAngle = when {
                isAirborne -> 24f
                isDancing -> sin(phase * 1.5f) * 26f
                isWalking -> sin(phase) * 30f
                else -> 0f
            }
            val rightLegAngle = when {
                isAirborne -> -24f
                isDancing -> -sin(phase * 1.5f) * 26f
                isWalking -> -sin(phase) * 30f
                else -> 0f
            }

            val legWidth = 10.5f * d
            val legHeight = 20f * d
            val leftHipX = cx - torsoHalfW * 0.48f
            val rightHipX = cx + torsoHalfW * 0.48f
            val hipY = torsoBottomY - 4f * d

            // Left Leg + Paw
            canvas.save()
            canvas.rotate(leftLegAngle, leftHipX, hipY)
            val leftLegRect = RectF(leftHipX - legWidth / 2f, hipY, leftHipX + legWidth / 2f, hipY + legHeight)
            canvas.drawRoundRect(leftLegRect, 5f * d, 5f * d, bodyPaint)
            canvas.drawRoundRect(leftLegRect, 5f * d, 5f * d, outlinePaint)
            val leftPawRect = RectF(leftHipX - legWidth * 0.7f, hipY + legHeight - 5f * d, leftHipX + legWidth * 0.6f, hipY + legHeight + 2f * d)
            canvas.drawRoundRect(leftPawRect, 4f * d, 4f * d, secondaryPaint)
            canvas.restore()

            // Right Leg + Paw
            canvas.save()
            canvas.rotate(rightLegAngle, rightHipX, hipY)
            val rightLegRect = RectF(rightHipX - legWidth / 2f, hipY, rightHipX + legWidth / 2f, hipY + legHeight)
            canvas.drawRoundRect(rightLegRect, 5f * d, 5f * d, bodyPaint)
            canvas.drawRoundRect(rightLegRect, 5f * d, 5f * d, outlinePaint)
            val rightPawRect = RectF(rightHipX - legWidth * 0.7f, hipY + legHeight - 5f * d, rightHipX + legWidth * 0.6f, hipY + legHeight + 2f * d)
            canvas.drawRoundRect(rightPawRect, 4f * d, 4f * d, secondaryPaint)
            canvas.restore()

            // 4. TORSO / BELLY (Pet)
            val torsoRect = RectF(cx - torsoHalfW, torsoTopY, cx + torsoHalfW, torsoBottomY)
            canvas.drawRoundRect(torsoRect, 14f * d, 14f * d, bodyPaint)
            canvas.drawRoundRect(torsoRect, 14f * d, 14f * d, outlinePaint)

            // Cute soft belly patch (Pet)
            val bellyRect = RectF(
                cx - torsoHalfW * 0.65f,
                torsoTopY + 5f * d,
                cx + torsoHalfW * 0.65f,
                torsoBottomY - 4f * d
            )
            canvas.drawRoundRect(bellyRect, 10f * d, 10f * d, bellyPaint)

            // 5. TWO ARMS (Do Hath) with Dancing / Walking / Airborne wave
            val leftArmAngle = when {
                isAirborne -> -125f
                isDancing -> -110f + sin(phase * 1.8f) * 35f
                isWalking -> -sin(phase) * 28f
                else -> 14f + sin(phase * 0.5f) * 6f
            }
            val rightArmAngle = when {
                isAirborne -> 125f
                isDancing -> 110f - cos(phase * 1.8f) * 35f
                isWalking -> sin(phase) * 28f
                else -> -14f - sin(phase * 0.5f) * 6f
            }

            val armW = 9f * d
            val armH = 19f * d
            val shoulderY = torsoTopY + 5f * d
            val leftShoulderX = cx - torsoHalfW * 0.92f
            val rightShoulderX = cx + torsoHalfW * 0.92f

            // Left Arm + Hand Paw
            canvas.save()
            canvas.rotate(leftArmAngle, leftShoulderX, shoulderY)
            val leftArmRect = RectF(leftShoulderX - armW / 2f, shoulderY, leftShoulderX + armW / 2f, shoulderY + armH)
            canvas.drawRoundRect(leftArmRect, 5f * d, 5f * d, bodyPaint)
            canvas.drawRoundRect(leftArmRect, 5f * d, 5f * d, outlinePaint)
            canvas.drawCircle(leftShoulderX, shoulderY + armH - 2f * d, armW * 0.52f, secondaryPaint)
            canvas.restore()

            // Right Arm + Hand Paw
            canvas.save()
            canvas.rotate(rightArmAngle, rightShoulderX, shoulderY)
            val rightArmRect = RectF(rightShoulderX - armW / 2f, shoulderY, rightShoulderX + armW / 2f, shoulderY + armH)
            canvas.drawRoundRect(rightArmRect, 5f * d, 5f * d, bodyPaint)
            canvas.drawRoundRect(rightArmRect, 5f * d, 5f * d, outlinePaint)
            canvas.drawCircle(rightShoulderX, shoulderY + armH - 2f * d, armW * 0.52f, secondaryPaint)
            canvas.restore()

            // Collar & Golden Bell around neck
            val collarRect = RectF(cx - torsoHalfW * 0.78f, torsoTopY - 2f * d, cx + torsoHalfW * 0.78f, torsoTopY + 4.5f * d)
            canvas.drawRoundRect(collarRect, 3f * d, 3f * d, collarPaint)
            canvas.drawCircle(cx, torsoTopY + 4f * d, 3.8f * d, bellPaint)

            // 6. HEAD (Sar) & Breed Ears
            val headTilt = if (isDancing) sin(phase * 1.5f) * 8f else 0f
            canvas.save()
            canvas.rotate(headTilt, cx, headCenterY)

            when (characterId) {
                "PUPPY_GOLD", "POM_PUPPY" -> {
                    // Floppy realistic puppy ears that bounce when walking/dancing
                    val earBounce = sin(phase * 1.5f) * 6f
                    canvas.save()
                    canvas.rotate(18f + earBounce, cx - headR * 0.8f, headCenterY - headR * 0.3f)
                    canvas.drawRoundRect(
                        RectF(cx - headR * 1.22f, headCenterY - headR * 0.45f, cx - headR * 0.68f, headCenterY + headR * 0.55f),
                        8f * d,
                        8f * d,
                        secondaryPaint
                    )
                    canvas.restore()

                    canvas.save()
                    canvas.rotate(-18f - earBounce, cx + headR * 0.8f, headCenterY - headR * 0.3f)
                    canvas.drawRoundRect(
                        RectF(cx + headR * 0.68f, headCenterY - headR * 0.45f, cx + headR * 1.22f, headCenterY + headR * 0.55f),
                        8f * d,
                        8f * d,
                        secondaryPaint
                    )
                    canvas.restore()
                }
                "HUSKY_DOG", "CAT" -> {
                    // Pointed upright wolf/husky/cat ears
                    val earPath = Path().apply {
                        moveTo(cx - headR * 0.82f, headCenterY - headR * 0.25f)
                        lineTo(cx - headR * 0.75f, headCenterY - headR * 1.22f)
                        lineTo(cx - headR * 0.18f, headCenterY - headR * 0.75f)
                        close()
                        moveTo(cx + headR * 0.82f, headCenterY - headR * 0.25f)
                        lineTo(cx + headR * 0.75f, headCenterY - headR * 1.22f)
                        lineTo(cx + headR * 0.18f, headCenterY - headR * 0.75f)
                        close()
                    }
                    canvas.drawPath(earPath, secondaryPaint)
                }
                "BULL" -> {
                    // Curved Bull Horns (Bhaisa)
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
                }
                else -> {
                    // Round Bear / Panda ears
                    canvas.drawCircle(cx - headR * 0.76f, headCenterY - headR * 0.72f, headR * 0.34f, secondaryPaint)
                    canvas.drawCircle(cx + headR * 0.76f, headCenterY - headR * 0.72f, headR * 0.34f, secondaryPaint)
                }
            }

            // Head circle (Sar)
            val headOval = RectF(cx - headR * 1.06f, headCenterY - headR * 0.95f, cx + headR * 1.06f, headCenterY + headR * 0.95f)
            canvas.drawOval(headOval, bodyPaint)
            canvas.drawOval(headOval, outlinePaint)

            // Puppy Muzzle / Snout patch
            val muzzleRect = RectF(cx - headR * 0.52f, headCenterY + headR * 0.02f, cx + headR * 0.52f, headCenterY + headR * 0.68f)
            canvas.drawOval(muzzleRect, bellyPaint)

            // Expressive Eyes with white sparkle
            val eyeY = headCenterY - headR * 0.14f
            val leftEyeX = cx - headR * 0.38f
            val rightEyeX = cx + headR * 0.38f
            canvas.drawCircle(leftEyeX, eyeY, headR * 0.17f, darkFeaturePaint)
            canvas.drawCircle(rightEyeX, eyeY, headR * 0.17f, darkFeaturePaint)
            canvas.drawCircle(leftEyeX - headR * 0.05f, eyeY - headR * 0.05f, headR * 0.06f, whitePaint)
            canvas.drawCircle(rightEyeX - headR * 0.05f, eyeY - headR * 0.05f, headR * 0.06f, whitePaint)

            // Cute Nose & Happy Puppy Tongue
            canvas.drawOval(
                RectF(cx - headR * 0.18f, headCenterY + headR * 0.10f, cx + headR * 0.18f, headCenterY + headR * 0.30f),
                darkFeaturePaint
            )
            val tongueRect = RectF(cx - headR * 0.14f, headCenterY + headR * 0.32f, cx + headR * 0.14f, headCenterY + headR * 0.62f)
            canvas.drawRoundRect(tongueRect, 4f * d, 4f * d, tonguePaint)

            canvas.restore()
            canvas.restore()

            // Floating Music Notes when Dancing
            if (isDancing) {
                val noteOffset = sin(phase) * 4f * d
                canvas.drawText("♪", w * 0.78f, h * 0.22f + noteOffset, notePaint)
                canvas.drawText("♫", w * 0.10f, h * 0.18f - noteOffset, notePaint)
            }

            // 2-Second Hold Circular Progress Ring
            if (holdProgress > 0.02f) {
                val arcRect = RectF(3f * d, 3f * d, w - 3f * d, h - 3f * d)
                canvas.drawArc(arcRect, -90f, 360f * holdProgress, false, holdRingPaint)
            }
        }
    }
}
