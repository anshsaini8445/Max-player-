package com.app.mediaplayer

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

object ThemeStudioHelper {

    data class AppThemeOption(
        val id: Int,
        val name: String,
        val subtitle: String,
        val bgHex: String,
        val cardHex: String,
        val accentHex: String
    )

    val themes = listOf(
        AppThemeOption(
            0,
            "OG Cyber Cyan",
            "Official Deep Pitch Dark & Electric Cyan",
            "#0A0A10",
            "#121824",
            "#00FFFF"
        ),
        AppThemeOption(
            1,
            "Aqua Frosted Glass",
            "Ocean Midnight & Neon Aqua Glow",
            "#07131E",
            "#102436",
            "#00E5FF"
        ),
        AppThemeOption(
            2,
            "Emerald Matrix Pro",
            "Dark Obsidian & Matrix Cyber Green",
            "#08120E",
            "#11241B",
            "#00E676"
        ),
        AppThemeOption(
            3,
            "Royal Amethyst 8K",
            "Velvet Night & Neon Lavender Gold",
            "#110B1A",
            "#1E142E",
            "#E040FB"
        ),
        AppThemeOption(
            4,
            "Crimson AMOLED",
            "True Black & Ultra Ruby Accent",
            "#060608",
            "#181014",
            "#FF4081"
        ),
        AppThemeOption(
            5,
            "Gold VIP Luxury",
            "Carbon Black & Royal Champagne Gold",
            "#0A0908",
            "#1A1712",
            "#FFD700"
        )
    )

    fun getCurrentTheme(context: Context): AppThemeOption {
        val prefs = context.getSharedPreferences("MAX_THEME_PREFS", Context.MODE_PRIVATE)
        val idx = prefs.getInt("theme_index", 0).coerceIn(0, themes.size - 1)
        return themes[idx]
    }

    fun applySelectedTheme(
        context: Context,
        themeIndex: Int,
        onThemeApplied: (() -> Unit)? = null
    ) {
        val chosen = themes[themeIndex.coerceIn(0, themes.size - 1)]
        context.getSharedPreferences("MAX_THEME_PREFS", Context.MODE_PRIVATE)
            .edit()
            .putInt("theme_index", chosen.id)
            .putString("theme_name", chosen.name)
            .putString("app_bg_color", chosen.bgHex)
            .putString("app_card_color", chosen.cardHex)
            .putString("app_accent_color", chosen.accentHex)
            .apply()

        (context as? Activity)?.window?.let { win ->
            try {
                win.statusBarColor = Color.parseColor(chosen.bgHex)
                win.navigationBarColor = Color.parseColor(chosen.bgHex)
            } catch (_: Exception) {
            }
        }
        onThemeApplied?.invoke()
    }

    fun showVisualThemeStudioSheet(
        context: Context,
        onThemeChanged: () -> Unit
    ) {
        val d = context.resources.displayMetrics.density
        val current = getCurrentTheme(context)

        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.parseColor("#F50A0A10")))
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(current.bgHex))
            setPadding((18 * d).toInt(), (22 * d).toInt(), (18 * d).toInt(), (20 * d).toInt())
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (16 * d).toInt())
        }

        val tvTitle = TextView(context).apply {
            text = "OG Theme & Wallpaper Studio"
            setTextColor(Color.parseColor(current.accentHex))
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnClose = TextView(context).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((36 * d).toInt(), (36 * d).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#26FFFFFF"))
            }
            setOnClickListener { dialog.dismiss() }
        }

        headerRow.addView(tvTitle)
        headerRow.addView(btnClose)
        root.addView(headerRow)

        val tvHint = TextView(context).apply {
            text = "Tap any theme card below to instantly transform Video, Music, Search, and Control Center screens:"
            setTextColor(Color.parseColor("#B0C4DE"))
            textSize = 13f
            setPadding(0, 0, 0, (16 * d).toInt())
        }
        root.addView(tvHint)

        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val grid = GridLayout(context).apply {
            columnCount = 2
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        themes.forEach { item ->
            val isSelected = item.id == current.id
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((14 * d).toInt(), (14 * d).toInt(), (14 * d).toInt(), (14 * d).toInt())
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins((6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt())
                }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(item.cardHex))
                    cornerRadius = 18f * d
                    setStroke(
                        if (isSelected) (2.4f * d).toInt() else (1.2f * d).toInt(),
                        Color.parseColor(item.accentHex)
                    )
                }
            }

            // Visual Color Swatch Bar inside Card
            val swatchBar = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (46 * d).toInt()
                ).apply { bottomMargin = (10 * d).toInt() }
                background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(
                        Color.parseColor(item.bgHex),
                        Color.parseColor(item.accentHex)
                    )
                ).apply {
                    cornerRadius = 12f * d
                }
            }

            val tvName = TextView(context).apply {
                text = if (isSelected) "✓  ${item.name}" else item.name
                setTextColor(Color.WHITE)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
            }

            val tvSub = TextView(context).apply {
                text = item.subtitle
                setTextColor(Color.parseColor(item.accentHex))
                textSize = 11f
                setPadding(0, (4 * d).toInt(), 0, (10 * d).toInt())
            }

            val btnApply = TextView(context).apply {
                text = if (isSelected) "ACTIVE THEME" else "APPLY THEME"
                setTextColor(Color.parseColor("#0A0A10"))
                textSize = 11.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(0, (7 * d).toInt(), 0, (7 * d).toInt())
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(item.accentHex))
                    cornerRadius = 12f * d
                }
            }

            card.addView(swatchBar)
            card.addView(tvName)
            card.addView(tvSub)
            card.addView(btnApply)

            card.setOnClickListener {
                applySelectedTheme(context, item.id) {
                    Toast.makeText(context, "Theme Applied: ${item.name}", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    onThemeChanged()
                }
            }

            grid.addView(card)
        }

        scroll.addView(grid)
        root.addView(scroll)
        dialog.setContentView(root)
        dialog.show()
    }
}
