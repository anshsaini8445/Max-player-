package com.app.mediaplayer

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

object ThemeStudioHelper {

    private const val PREF_NAME = "MAX_THEME_STUDIO_PREFS"
    private const val KEY_THEME_ID = "selected_theme_id"
    private const val KEY_CUSTOM_BG_URI = "custom_gallery_theme_uri"
    private const val KEY_ONE_HAND_MODE = "one_hand_reachability_enabled"

    data class AppThemeConfig(
        val id: String,
        val name: String,
        val bgHex: String,
        val cardHex: String,
        val accentHex: String
    )

    // 18 Curated Pro Themes
    private val themes = listOf(
        AppThemeConfig("cyber_cyan", "1. Cyber Neon Cyan (Default)", "#0A0A10", "#141D2B", "#00FFFF"),
        AppThemeConfig("amoled_black", "2. AMOLED Pure Pitch Black", "#000000", "#0D0D12", "#00E5FF"),
        AppThemeConfig("royal_gold", "3. Royal Gold Luxury", "#0B0906", "#1C160D", "#FFD700"),
        AppThemeConfig("emerald_matrix", "4. Emerald Matrix Neon", "#060F0A", "#102419", "#00E676"),
        AppThemeConfig("sunset_purple", "5. Sunset Insta Gradient", "#12081C", "#241236", "#FF4081"),
        AppThemeConfig("ocean_blue", "6. Deep Ocean Sapphire", "#06101E", "#10233D", "#40C4FF"),
        AppThemeConfig("crimson_velvet", "7. Crimson Velvet Red", "#140608", "#280E14", "#FF5252"),
        AppThemeConfig("sakura_pink", "8. Sakura Blossom Pink", "#140912", "#261222", "#FF80AB"),
        AppThemeConfig("midnight_indigo", "9. Midnight Starlight Indigo", "#08091A", "#141736", "#8C9EFF"),
        AppThemeConfig("arctic_frost", "10. Arctic Ice Frost", "#091318", "#142730", "#84FFFF"),
        AppThemeConfig("toxic_lime", "11. Gamer Toxic Lime", "#091006", "#15240E", "#B2FF59"),
        AppThemeConfig("lava_magma", "12. Volcanic Lava Orange", "#140A05", "#29150B", "#FF6E40"),
        AppThemeConfig("galaxy_violet", "13. Andromeda Galaxy Violet", "#0E071B", "#1D1036", "#E040FB"),
        AppThemeConfig("coffee_espresso", "14. Warm Mocha Espresso", "#120D0A", "#241A14", "#FFAB40"),
        AppThemeConfig("titanium_slate", "15. Titanium Gunmetal Pro", "#0E1114", "#1C2228", "#64FFDA"),
        AppThemeConfig("solar_amber", "16. Solar Flare Amber", "#120E04", "#261E0A", "#FFC400"),
        AppThemeConfig("mint_breeze", "17. Nordic Mint Breeze", "#061311", "#102824", "#1DE9B6"),
        AppThemeConfig("neon_magenta", "18. Cyberpunk Hot Magenta", "#130614", "#260E29", "#FF1744")
    )

    fun getCurrentTheme(context: Context): AppThemeConfig {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val savedId = prefs.getString(KEY_THEME_ID, "cyber_cyan") ?: "cyber_cyan"
        return themes.firstOrNull { it.id == savedId } ?: themes[0]
    }

    fun getCustomGalleryPhotoUri(context: Context): String? {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM_BG_URI, null)
    }

    fun saveCustomGalleryPhotoUri(context: Context, uriString: String?) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_BG_URI, uriString)
            .apply()
    }

    fun isOneHandModeEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ONE_HAND_MODE, false)
    }

    fun setOneHandModeEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ONE_HAND_MODE, enabled)
            .apply()
    }

    /**
     * Applies the active theme background (or the user's custom gallery wallpaper with a dark readable scrim)
     * plus One-Hand Mode reachability padding if enabled.
     */
    fun applyThemeAndOneHandModeToRoot(context: Context, rootView: View) {
        try {
            val theme = getCurrentTheme(context)
            val customUri = getCustomGalleryPhotoUri(context)
            var appliedCustomPhoto = false

            if (!customUri.isNullOrEmpty()) {
                try {
                    val uri = Uri.parse(customUri)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val bmp = BitmapFactory.decodeStream(input)
                        if (bmp != null) {
                            val bmpDrawable = BitmapDrawable(context.resources, bmp)
                            val darkScrim = ColorDrawable(Color.parseColor("#B80A0A10"))
                            rootView.background = LayerDrawable(arrayOf(bmpDrawable, darkScrim))
                            appliedCustomPhoto = true
                        }
                    }
                } catch (_: Exception) {
                }
            }

            if (!appliedCustomPhoto) {
                rootView.setBackgroundColor(Color.parseColor(theme.bgHex))
            }

            val d = context.resources.displayMetrics.density
            if (isOneHandModeEnabled(context)) {
                val topReachPad = (context.resources.displayMetrics.heightPixels * 0.18f).toInt()
                rootView.setPadding(
                    (8 * d).toInt(),
                    topReachPad,
                    (8 * d).toInt(),
                    0
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun showVisualThemeStudioSheet(
        context: Context,
        onPickGalleryPhotoRequested: (() -> Unit)? = null,
        onThemeChanged: () -> Unit
    ) {
        val d = context.resources.displayMetrics.density
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val current = getCurrentTheme(context)
        val accentColor = Color.parseColor(current.accentHex)

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
            text = "18+ Ultra Themes & Custom Wallpaper Studio"
            setTextColor(accentColor)
            textSize = 16f
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

        // Custom Gallery Photo Wallpaper Button Row
        val customPhotoRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (10 * d).toInt())
        }

        val btnPickPhoto = TextView(context).apply {
            text = "Pick Custom Photo from Gallery"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (6 * d).toInt()
            }
            background = GradientDrawable().apply {
                setColor(accentColor)
                cornerRadius = 14f * d
            }
            setOnClickListener {
                dialog.dismiss()
                if (onPickGalleryPhotoRequested != null) {
                    onPickGalleryPhotoRequested.invoke()
                } else if (context is Activity) {
                    try {
                        val pickIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "image/*"
                        }
                        context.startActivityForResult(pickIntent, 9401)
                    } catch (_: Exception) {
                        Toast.makeText(context, "Unable to open photo gallery", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val btnClearPhoto = TextView(context).apply {
            text = "Reset Photo"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 14f * d
            }
            setOnClickListener {
                saveCustomGalleryPhotoUri(context, null)
                dialog.dismiss()
                onThemeChanged()
                Toast.makeText(context, "Custom Wallpaper Cleared", Toast.LENGTH_SHORT).show()
            }
        }

        customPhotoRow.addView(btnPickPhoto)
        customPhotoRow.addView(btnClearPhoto)
        root.addView(customPhotoRow)

        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (390 * d).toInt()
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
                setPadding((12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt())
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = (76 * d).toInt()
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins((4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt())
                }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(item.cardHex))
                    cornerRadius = 14f * d
                    setStroke(
                        ((if (isSelected) 2.2f else 1.0f) * d).toInt(),
                        Color.parseColor(item.accentHex)
                    )
                }
                setOnClickListener {
                    prefs.edit().putString(KEY_THEME_ID, item.id).apply()
                    dialog.dismiss()
                    onThemeChanged()
                    Toast.makeText(context, "Theme Applied: ${item.name}", Toast.LENGTH_SHORT).show()
                }
            }

            val topDotRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val colorDot = View(context).apply {
                layoutParams = LinearLayout.LayoutParams((14 * d).toInt(), (14 * d).toInt()).apply {
                    marginEnd = (6 * d).toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(item.accentHex))
                }
            }

            val tvActiveMark = TextView(context).apply {
                text = if (isSelected) "ACTIVE" else "TAP TO APPLY"
                setTextColor(Color.parseColor(item.accentHex))
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
            }

            topDotRow.addView(colorDot)
            topDotRow.addView(tvActiveMark)

            val tvThemeName = TextView(context).apply {
                text = item.name
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 2
                setPadding(0, (6 * d).toInt(), 0, 0)
            }

            card.addView(topDotRow)
            card.addView(tvThemeName)
            grid.addView(card)
        }

        scroll.addView(grid)
        root.addView(scroll)
        dialog.show()
    }
}
