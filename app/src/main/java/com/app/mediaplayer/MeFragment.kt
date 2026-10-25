package com.app.mediaplayer

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
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
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import java.util.Locale

class MeFragment : Fragment() {

    companion object {
        private const val REQ_PICK_THEME_WALLPAPER = 9401
    }

    private lateinit var rootScroll: ScrollView
    private lateinit var mainContainer: LinearLayout
    private lateinit var tvVipBadgeStatus: TextView
    private lateinit var tvVipMemberTitle: TextView
    private lateinit var tvThemeActiveSubtitle: TextView
    private lateinit var tvCleanerSubtitle: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        rootScroll = ScrollView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }

        mainContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        rootScroll.addView(mainContainer)
        rebuildControlCenterUi()
        return rootScroll
    }

    override fun onResume() {
        super.onResume()
        if (isAdded) {
            rebuildControlCenterUi()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_THEME_WALLPAPER && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            val ctx = context ?: return
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            ThemeStudioHelper.saveCustomGalleryPhotoUri(ctx, uri.toString())
            rebuildControlCenterUi()
            Toast.makeText(ctx, "Custom Gallery Theme Photo Applied!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun rebuildControlCenterUi() {
        val ctx = context ?: return
        val d = ctx.resources.displayMetrics.density
        val theme = ThemeStudioHelper.getCurrentTheme(ctx)
        val cardColor = Color.parseColor(theme.cardHex)
        val accentColor = Color.parseColor(theme.accentHex)

        ThemeStudioHelper.applyThemeAndOneHandModeToRoot(ctx, rootScroll)
        mainContainer.removeAllViews()
        mainContainer.setPadding((16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt(), (28 * d).toInt())

        // 1. Top Control Center Header Row
        val headerRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((2 * d).toInt(), (4 * d).toInt(), (2 * d).toInt(), (14 * d).toInt())
        }

        val headerIcon = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((34 * d).toInt(), (34 * d).toInt()).apply {
                marginEnd = (10 * d).toInt()
            }
            setImageDrawable(MeVectorIconDrawable("SHIELD_CROWN", d, accentColor))
        }

        val tvHeaderTitle = TextView(ctx).apply {
            text = "Hybrid Player Control Center"
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val activePlanName = VipBillingManager.getActivePlanLabel(ctx)
        tvVipBadgeStatus = TextView(ctx).apply {
            text = activePlanName
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((14 * d).toInt(), (6 * d).toInt(), (14 * d).toInt(), (6 * d).toInt())
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#FFD700"), accentColor)
            ).apply {
                cornerRadius = 16f * d
            }
            setOnClickListener {
                VipBillingManager.showInternationalVipPaywall(ctx) {
                    rebuildControlCenterUi()
                }
            }
        }

        headerRow.addView(headerIcon)
        headerRow.addView(tvHeaderTitle)
        headerRow.addView(tvVipBadgeStatus)
        mainContainer.addView(headerRow)

        // 2. Clean International VIP Membership Card
        val vipCard = CardView(ctx).apply {
            radius = 20f * d
            cardElevation = 8f * d
            setCardBackgroundColor(cardColor)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (16 * d).toInt() }
        }

        val vipInner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * d).toInt(), (18 * d).toInt(), (18 * d).toInt(), (18 * d).toInt())
            background = GradientDrawable().apply {
                setColor(cardColor)
                cornerRadius = 20f * d
                setStroke((1.5f * d).toInt(), accentColor)
            }
        }

        val vipTopRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val vipCrownImg = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((46 * d).toInt(), (46 * d).toInt()).apply {
                marginEnd = (14 * d).toInt()
            }
            setImageDrawable(MeVectorIconDrawable("VIP_CROWN", d, Color.parseColor("#FFD700")))
        }

        val vipTextCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        tvVipMemberTitle = TextView(ctx).apply {
            text = "Hybrid Player Member ($activePlanName)"
            setTextColor(Color.WHITE)
            textSize = 16.5f
            typeface = Typeface.DEFAULT_BOLD
        }

        val tvVipDesc = TextView(ctx).apply {
            text = "Unlock 8K HDR AI Upscaler, 200% Dolby Audio Boost, Private Vault & Global Ad-Free Pass"
            setTextColor(Color.parseColor("#B0C4DE"))
            textSize = 12f
            setPadding(0, (4 * d).toInt(), 0, 0)
        }

        vipTextCol.addView(tvVipMemberTitle)
        vipTextCol.addView(tvVipDesc)
        vipTopRow.addView(vipCrownImg)
        vipTopRow.addView(vipTextCol)
        vipInner.addView(vipTopRow)

        val btnGetVip = TextView(ctx).apply {
            text = "GET VIP / CHOOSE PLAN (WEEKLY • MONTHLY • YEARLY)"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((16 * d).toInt(), (13 * d).toInt(), (16 * d).toInt(), (13 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (14 * d).toInt() }
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(accentColor, Color.parseColor("#00E676"))
            ).apply {
                cornerRadius = 18f * d
            }
            setOnClickListener {
                VipBillingManager.showInternationalVipPaywall(ctx) {
                    rebuildControlCenterUi()
                }
            }
        }
        vipInner.addView(btnGetVip)
        vipCard.addView(vipInner)
        mainContainer.addView(vipCard)

        // 3. Symmetrical 6-Box Quick Tools Grid (2 Columns x 3 Rows)
        val tvGridSectionTitle = TextView(ctx).apply {
            text = "PRO MEDIA TOOLKIT (6-CORE MODULES)"
            setTextColor(accentColor)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((4 * d).toInt(), 0, 0, (8 * d).toInt())
        }
        mainContainer.addView(tvGridSectionTitle)

        val toolsGrid = GridLayout(ctx).apply {
            columnCount = 2
            rowCount = 3
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (14 * d).toInt() }
        }

        toolsGrid.addView(
            createSymmetricalToolBox(
                ctx = ctx,
                iconType = "MP3_WAVE",
                title = "Video to MP3",
                subtitle = "Fast Audio Extractor",
                cardColor = cardColor,
                accentColor = accentColor,
                d = d
            ) {
                try {
                    startActivity(Intent(ctx, Mp3ConverterActivity::class.java))
                } catch (_: Exception) {
                    Toast.makeText(ctx, "Select any video to convert to MP3", Toast.LENGTH_SHORT).show()
                }
            }.first
        )

        toolsGrid.addView(
            createSymmetricalToolBox(
                ctx = ctx,
                iconType = "WIFI_SHARE",
                title = "Offline Share",
                subtitle = "2.4G / 5G / 6GHz Fast",
                cardColor = cardColor,
                accentColor = accentColor,
                d = d
            ) {
                try {
                    startActivity(Intent(ctx, FileTransferActivity::class.java))
                } catch (_: Exception) {
                    Toast.makeText(ctx, "Opening High-Speed Share...", Toast.LENGTH_SHORT).show()
                }
            }.first
        )

        toolsGrid.addView(
            createSymmetricalToolBox(
                ctx = ctx,
                iconType = "VAULT_LOCK",
                title = "Privacy Vault",
                subtitle = "Real & Fake PIN Lock",
                cardColor = cardColor,
                accentColor = accentColor,
                d = d
            ) {
                openPrivacyVaultSafely(ctx)
            }.first
        )

        toolsGrid.addView(
            createSymmetricalToolBox(
                ctx = ctx,
                iconType = "SOUND_LAB",
                title = "Sound & Pitch Lab",
                subtitle = "Lofi & 200% Booster",
                cardColor = cardColor,
                accentColor = accentColor,
                d = d
            ) {
                try {
                    AudioPitchHelper.showSoundAndPitchLabDialog(ctx, null)
                } catch (_: Exception) {
                    EqualizerHelper.showEqualizerDialog(ctx, 0)
                }
            }.first
        )

        val cleanerBoxPair = createSymmetricalToolBox(
            ctx = ctx,
            iconType = "CLEANER_BROOM",
            title = "Storage Cleaner",
            subtitle = "Clean Cache & Duplicates",
            cardColor = cardColor,
            accentColor = Color.parseColor("#00E676"),
            d = d
        ) {
            StorageAndAboutHelper.showFullScreenStorageCleaner(ctx) { freedBytes ->
                val mb = String.format(Locale.US, "%.1f MB Cleaned", freedBytes / (1024.0 * 1024.0))
                tvCleanerSubtitle.text = mb
            }
        }
        tvCleanerSubtitle = cleanerBoxPair.second
        toolsGrid.addView(cleanerBoxPair.first)

        val customWallUri = ThemeStudioHelper.getCustomGalleryPhotoUri(ctx)
        val themeSubLabel = if (!customWallUri.isNullOrEmpty()) "Custom Gallery Photo" else theme.name
        val themeBoxPair = createSymmetricalToolBox(
            ctx = ctx,
            iconType = "THEME_PALETTE",
            title = "18+ Theme Studio",
            subtitle = themeSubLabel,
            cardColor = cardColor,
            accentColor = Color.parseColor("#FFD700"),
            d = d
        ) {
            ThemeStudioHelper.showVisualThemeStudioSheet(
                context = ctx,
                onPickGalleryPhotoRequested = {
                    try {
                        val pickIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "image/*"
                        }
                        startActivityForResult(pickIntent, REQ_PICK_THEME_WALLPAPER)
                    } catch (_: Exception) {
                        Toast.makeText(ctx, "Unable to open gallery", Toast.LENGTH_SHORT).show()
                    }
                },
                onThemeChanged = {
                    rebuildControlCenterUi()
                }
            )
        }
        tvThemeActiveSubtitle = themeBoxPair.second
        toolsGrid.addView(themeBoxPair.first)

        mainContainer.addView(toolsGrid)

        // 4. Dedicated Folders Below the 6-Box Grid: Dancing Mascot Studio + 35+ Languages + One-Hand Mode
        val tvCompanionHeader = TextView(ctx).apply {
            text = "MASCOT COMPANION, 35+ LANGUAGES & ONE-HAND MODE"
            setTextColor(accentColor)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((4 * d).toInt(), (2 * d).toInt(), 0, (8 * d).toInt())
        }
        mainContainer.addView(tvCompanionHeader)

        val extraFoldersGrid = GridLayout(ctx).apply {
            columnCount = 2
            rowCount = 1
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * d).toInt() }
        }

        val mascotStatus = if (MascotCompanionHelper.isMascotEnabled(ctx)) {
            "${MascotCompanionHelper.getSelectedCharacter(ctx).name} (${MascotCompanionHelper.getSelectedColor(ctx).name})"
        } else {
            "Disabled (Tap to Enable)"
        }

        // Folder 7: Dancing Mascot & Puppy Studio
        extraFoldersGrid.addView(
            createSymmetricalToolBox(
                ctx = ctx,
                iconType = "MASCOT_PAW",
                title = "Dancing Mascot",
                subtitle = mascotStatus,
                cardColor = cardColor,
                accentColor = Color.parseColor("#FF4081"),
                d = d
            ) {
                MascotCompanionHelper.showMascotCustomizationDialog(ctx) {
                    rebuildControlCenterUi()
                }
            }.first
        )

        // Folder 8: 35+ Multi-Language Engine
        val currentLangDisplay = MultiLanguageHelper.getCurrentLanguageDisplayName(ctx)
        extraFoldersGrid.addView(
            createSymmetricalToolBox(
                ctx = ctx,
                iconType = "GLOBE_LANG",
                title = "35+ Languages",
                subtitle = currentLangDisplay,
                cardColor = cardColor,
                accentColor = Color.parseColor("#00E5FF"),
                d = d
            ) {
                MultiLanguageHelper.showLanguageSelectorDialog(ctx) {
                    rebuildControlCenterUi()
                }
            }.first
        )

        mainContainer.addView(extraFoldersGrid)

        // Prominent One-Hand Mode (Single-Hand Reachability) Card
        val oneHandEnabled = ThemeStudioHelper.isOneHandModeEnabled(ctx)
        val oneHandCard = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * d).toInt(), (13 * d).toInt(), (16 * d).toInt(), (13 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (14 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(if (oneHandEnabled) Color.parseColor("#1A2E44") else cardColor)
                cornerRadius = 18f * d
                setStroke(
                    ((if (oneHandEnabled) 2.0f else 1.2f) * d).toInt(),
                    if (oneHandEnabled) Color.parseColor("#00E676") else accentColor
                )
            }
            setOnClickListener {
                val next = !ThemeStudioHelper.isOneHandModeEnabled(ctx)
                ThemeStudioHelper.setOneHandModeEnabled(ctx, next)
                rebuildControlCenterUi()
                Toast.makeText(
                    ctx,
                    if (next) "One-Hand Reachability Mode ON" else "One-Hand Mode OFF (Full Screen)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val oneHandIcon = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((36 * d).toInt(), (36 * d).toInt()).apply {
                marginEnd = (12 * d).toInt()
            }
            setImageDrawable(MeVectorIconDrawable("ONE_HAND", d, accentColor))
        }

        val oneHandTextCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvOneHandTitle = TextView(ctx).apply {
            text = "ONE-HAND MODE (SINGLE-HAND REACHABILITY)"
            setTextColor(Color.WHITE)
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
        }

        val tvOneHandSub = TextView(ctx).apply {
            text = if (oneHandEnabled) {
                "Active: Screen shifted down for easy thumb control"
            } else {
                "Tap to shift controls down for easy single-hand thumb use"
            }
            setTextColor(if (oneHandEnabled) Color.parseColor("#00E676") else accentColor)
            textSize = 11.5f
            setPadding(0, (2 * d).toInt(), 0, 0)
        }

        val btnOneHandPill = TextView(ctx).apply {
            text = if (oneHandEnabled) "ON" else "OFF"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((14 * d).toInt(), (6 * d).toInt(), (14 * d).toInt(), (6 * d).toInt())
            background = GradientDrawable().apply {
                setColor(if (oneHandEnabled) Color.parseColor("#00E676") else Color.parseColor("#99B0C4DE"))
                cornerRadius = 14f * d
            }
        }

        oneHandTextCol.addView(tvOneHandTitle)
        oneHandTextCol.addView(tvOneHandSub)
        oneHandCard.addView(oneHandIcon)
        oneHandCard.addView(oneHandTextCol)
        oneHandCard.addView(btnOneHandPill)
        mainContainer.addView(oneHandCard)

        // 5. Quick Action Pills Row (WhatsApp Status Saver & 8K Network Stream)
        val quickRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (16 * d).toInt() }
        }

        val btnQuickStatusSaver = TextView(ctx).apply {
            text = "WhatsApp Status Saver"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (6 * d).toInt()
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#00E676"))
                cornerRadius = 16f * d
            }
            setOnClickListener {
                StatusSaverHelper.openWhatsAppStatusSaverDialog(ctx)
            }
        }

        val btnQuickStreamUrl = TextView(ctx).apply {
            text = "8K Network Stream URL"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (6 * d).toInt()
            }
            background = GradientDrawable().apply {
                setColor(cardColor)
                cornerRadius = 16f * d
                setStroke((1.3f * d).toInt(), accentColor)
            }
            setOnClickListener {
                NetworkStreamDialog.showNetworkStreamInputDialog(ctx)
            }
        }

        quickRow.addView(btnQuickStatusSaver)
        quickRow.addView(btnQuickStreamUrl)
        mainContainer.addView(quickRow)

        // 6. Clean Interactive Video & Audio Engine Settings Card
        val settingsCard = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * d).toInt(), (14 * d).toInt(), (16 * d).toInt(), (14 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (16 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(cardColor)
                cornerRadius = 18f * d
                setStroke((1 * d).toInt(), Color.parseColor("#22FFFFFF"))
            }
        }

        val tvSettingsHeader = TextView(ctx).apply {
            text = "VIDEO & AUDIO ENGINE SETTINGS"
            setTextColor(accentColor)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (8 * d).toInt())
        }
        settingsCard.addView(tvSettingsHeader)

        val layoutPrefs = ctx.getSharedPreferences("MAX_LAYOUT_PREFS", Context.MODE_PRIVATE)
        val audioPrefs = ctx.getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)

        settingsCard.addView(
            createSettingSwitchRow(
                ctx = ctx,
                title = "8K Hardware + Software Hybrid Fallback",
                subtitle = "Smoothly play 2K, 4K & 8K videos on all phones without freezing",
                initialChecked = layoutPrefs.getBoolean("player_8k_hybrid_fallback", true),
                d = d
            ) { checked ->
                layoutPrefs.edit().putBoolean("player_8k_hybrid_fallback", checked).apply()
            }
        )

        settingsCard.addView(createThinDivider(ctx, d))

        settingsCard.addView(
            createSettingSwitchRow(
                ctx = ctx,
                title = "Super Loud 200% Audio Booster",
                subtitle = "Enable 200% volume HUD boost with 4-dot gain enhancer",
                initialChecked = audioPrefs.getBoolean("super_loud_200_boost", true),
                d = d
            ) { checked ->
                audioPrefs.edit().putBoolean("super_loud_200_boost", checked).apply()
            }
        )

        settingsCard.addView(createThinDivider(ctx, d))

        settingsCard.addView(
            createSettingSwitchRow(
                ctx = ctx,
                title = "Smart In-Hand Double-Shake Sensor",
                subtitle = "Change songs on 2 hand shakes (Auto-sleeps in pocket, walking or bike)",
                initialChecked = audioPrefs.getBoolean("pocket_shake_enabled", false),
                d = d
            ) { checked ->
                audioPrefs.edit().putBoolean("pocket_shake_enabled", checked).apply()
            }
        )

        settingsCard.addView(createThinDivider(ctx, d))

        settingsCard.addView(
            createSettingSwitchRow(
                ctx = ctx,
                title = "8K Hardware HDR & CC Badges",
                subtitle = "Show quick HDR enhancer & Subtitle buttons on video player",
                initialChecked = layoutPrefs.getBoolean("player_show_hdr_cc", true),
                d = d
            ) { checked ->
                layoutPrefs.edit().putBoolean("player_show_hdr_cc", checked).apply()
            }
        )

        mainContainer.addView(settingsCard)

        // 7. Bottom Support, Rating, Share & Full-Screen About Card
        val supportCard = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * d).toInt(), (10 * d).toInt(), (16 * d).toInt(), (10 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            background = GradientDrawable().apply {
                setColor(cardColor)
                cornerRadius = 18f * d
                setStroke((1 * d).toInt(), Color.parseColor("#22FFFFFF"))
            }
        }

        supportCard.addView(
            createActionRowItem(
                ctx = ctx,
                title = "Rate Us 5 Stars on Google Play",
                rightLabel = "*****",
                rightColor = Color.parseColor("#FFD700"),
                d = d
            ) {
                RateUsDialogHelper.showPlayItStyleRateUsPopup(ctx, playedCount = 5)
            }
        )
        supportCard.addView(createThinDivider(ctx, d))

        supportCard.addView(
            createActionRowItem(
                ctx = ctx,
                title = "Help & Feedback / Report Issue",
                rightLabel = ">",
                rightColor = accentColor,
                d = d
            ) {
                showCleanFeedbackSheet(ctx)
            }
        )
        supportCard.addView(createThinDivider(ctx, d))

        supportCard.addView(
            createActionRowItem(
                ctx = ctx,
                title = "Share Hybrid Player with Friends",
                rightLabel = "Share",
                rightColor = accentColor,
                d = d
            ) {
                shareAppWithFriends(ctx)
            }
        )
        supportCard.addView(createThinDivider(ctx, d))

        supportCard.addView(
            createActionRowItem(
                ctx = ctx,
                title = "About Hybrid Player v3.5",
                rightLabel = "Full Info >",
                rightColor = accentColor,
                d = d
            ) {
                StorageAndAboutHelper.showFullScreenAboutPage(ctx)
            }
        )

        mainContainer.addView(supportCard)
    }

    private fun createSymmetricalToolBox(
        ctx: Context,
        iconType: String,
        title: String,
        subtitle: String,
        cardColor: Int,
        accentColor: Int,
        d: Float,
        onClick: () -> Unit
    ): Pair<LinearLayout, TextView> {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * d).toInt(), (14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt())
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = (82 * d).toInt()
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins((5 * d).toInt(), (5 * d).toInt(), (5 * d).toInt(), (5 * d).toInt())
            }
            background = GradientDrawable().apply {
                setColor(cardColor)
                cornerRadius = 16f * d
                setStroke((1.2f * d).toInt(), Color.parseColor("#2A00FFFF"))
            }
            setOnClickListener { onClick() }
        }

        val iconView = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((36 * d).toInt(), (36 * d).toInt()).apply {
                marginEnd = (10 * d).toInt()
            }
            setImageDrawable(MeVectorIconDrawable(iconType, d, accentColor))
        }

        val textCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvTitle = TextView(ctx).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        }

        val tvSub = TextView(ctx).apply {
            text = subtitle
            setTextColor(accentColor)
            textSize = 11f
            maxLines = 1
            setPadding(0, (3 * d).toInt(), 0, 0)
        }

        textCol.addView(tvTitle)
        textCol.addView(tvSub)
        box.addView(iconView)
        box.addView(textCol)
        return Pair(box, tvSub)
    }

    private fun createSettingSwitchRow(
        ctx: Context,
        title: String,
        subtitle: String,
        initialChecked: Boolean,
        d: Float,
        onToggle: (Boolean) -> Unit
    ): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (10 * d).toInt(), 0, (10 * d).toInt())
        }

        val textCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (10 * d).toInt()
            }
        }

        val tvTitle = TextView(ctx).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }

        val tvSub = TextView(ctx).apply {
            text = subtitle
            setTextColor(Color.parseColor("#99B0C4DE"))
            textSize = 11.5f
            setPadding(0, (2 * d).toInt(), 0, 0)
        }

        val sw = Switch(ctx).apply {
            isChecked = initialChecked
            setOnCheckedChangeListener { _, isChecked ->
                onToggle(isChecked)
            }
        }

        textCol.addView(tvTitle)
        textCol.addView(tvSub)
        row.addView(textCol)
        row.addView(sw)
        return row
    }

    private fun createActionRowItem(
        ctx: Context,
        title: String,
        rightLabel: String,
        rightColor: Int,
        d: Float,
        onClick: () -> Unit
    ): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (13 * d).toInt(), 0, (13 * d).toInt())
            setOnClickListener { onClick() }
        }

        val tvTitle = TextView(ctx).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvRight = TextView(ctx).apply {
            text = rightLabel
            setTextColor(rightColor)
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
        }

        row.addView(tvTitle)
        row.addView(tvRight)
        return row
    }

    private fun createThinDivider(ctx: Context, d: Float): View {
        return View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (1 * d).toInt()
            )
            setBackgroundColor(Color.parseColor("#18FFFFFF"))
        }
    }

    private fun openPrivacyVaultSafely(ctx: Context) {
        val d = ctx.resources.displayMetrics.density
        val prefs = ctx.getSharedPreferences("MAX_VAULT_PREFS", Context.MODE_PRIVATE)
        val savedPin = prefs.getString("vault_real_pin", null)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * d).toInt(), (20 * d).toInt(), (20 * d).toInt(), (18 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0A0A10"))
                cornerRadius = 22f * d
                setStroke((1.5f * d).toInt(), Color.parseColor("#00FFFF"))
            }
        }

        val dialog = AlertDialog.Builder(ctx).setView(root).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvTitle = TextView(ctx).apply {
            text = if (savedPin == null) "Set 4-Digit Privacy Vault PIN" else "Enter Privacy Vault PIN"
            setTextColor(Color.parseColor("#00FFFF"))
            textSize = 16.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (8 * d).toInt())
        }

        val tvSub = TextView(ctx).apply {
            text = if (savedPin == null) {
                "Create a secret 4-digit PIN to lock and hide private videos safely:"
            } else {
                "Enter your 4-digit PIN to access hidden videos in your Private Vault:"
            }
            setTextColor(Color.parseColor("#B0C4DE"))
            textSize = 12.5f
            setPadding(0, 0, 0, (12 * d).toInt())
        }

        val etPin = EditText(ctx).apply {
            hint = "****"
            setHintTextColor(Color.parseColor("#6C7A89"))
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141C2B"))
                cornerRadius = 14f * d
                setStroke((1.2f * d).toInt(), Color.parseColor("#00FFFF"))
            }
        }

        val btnUnlock = TextView(ctx).apply {
            text = if (savedPin == null) "SAVE PIN & OPEN VAULT" else "UNLOCK VAULT"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((18 * d).toInt(), (12 * d).toInt(), (18 * d).toInt(), (12 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (14 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#00FFFF"))
                cornerRadius = 16f * d
            }
            setOnClickListener {
                val entered = etPin.text.toString().trim()
                if (entered.length < 4) {
                    Toast.makeText(ctx, "Please enter at least 4 digits", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (savedPin == null) {
                    prefs.edit().putString("vault_real_pin", entered).apply()
                    dialog.dismiss()
                    Toast.makeText(ctx, "Vault PIN Saved! Use video menu to lock files.", Toast.LENGTH_LONG).show()
                } else if (entered == savedPin) {
                    dialog.dismiss()
                    Toast.makeText(ctx, "Privacy Vault Unlocked!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(ctx, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                }
            }
        }

        root.addView(tvTitle)
        root.addView(tvSub)
        root.addView(etPin)
        root.addView(btnUnlock)
        dialog.show()
    }

    private fun showCleanFeedbackSheet(ctx: Context) {
        val options = arrayOf(
            "Video Playback / 8K Codec Issue",
            "Music Player / Background Audio Issue",
            "Equalizer / Volume Boost Issue",
            "VIP Subscription / Billing Inquiry",
            "Suggest a New Feature"
        )
        VideoFragment.showBrightVideoMenuDialog(ctx, "Help & Feedback", options) { which ->
            val subject = "Hybrid Player Feedback: ${options[which]}"
            try {
                val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:support.hybridplayer@gmail.com")
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                }
                startActivity(Intent.createChooser(emailIntent, "Send Feedback"))
            } catch (_: Exception) {
                Toast.makeText(ctx, "Thank you! Feedback logged: ${options[which]}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareAppWithFriends(ctx: Context) {
        try {
            val pkg = ctx.packageName
            val shareText = "Download Hybrid Player - Ultra HD 8K Video Player, Shorts Reels, Built-in Dolby Equalizer & MP3 Converter:\nhttps://play.google.com/store/apps/details?id=$pkg"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Hybrid Player Ultra Media Player")
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(intent, "Share Hybrid Player"))
        } catch (_: Exception) {
            Toast.makeText(ctx, "Unable to open share menu", Toast.LENGTH_SHORT).show()
        }
    }

    private class MeVectorIconDrawable(
        private val type: String,
        private val d: Float,
        private val accentColor: Int
    ) : Drawable() {

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#1A00FFFF")
        }

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.2f * d
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = accentColor
        }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = accentColor
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 0f || h <= 0f) return
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val r = w.coerceAtMost(h) * 0.44f

            canvas.drawCircle(cx, cy, r, bgPaint)

            when (type) {
                "SHIELD_CROWN", "VIP_CROWN" -> {
                    val path = Path().apply {
                        moveTo(cx - r * 0.52f, cy + r * 0.34f)
                        lineTo(cx - r * 0.58f, cy - r * 0.24f)
                        lineTo(cx - r * 0.22f, cy + r * 0.04f)
                        lineTo(cx, cy - r * 0.42f)
                        lineTo(cx + r * 0.22f, cy + r * 0.04f)
                        lineTo(cx + r * 0.58f, cy - r * 0.24f)
                        lineTo(cx + r * 0.52f, cy + r * 0.34f)
                        close()
                    }
                    canvas.drawPath(path, fillPaint)
                }
                "MP3_WAVE" -> {
                    canvas.drawLine(cx - r * 0.45f, cy - r * 0.20f, cx - r * 0.45f, cy + r * 0.20f, strokePaint)
                    canvas.drawLine(cx - r * 0.15f, cy - r * 0.48f, cx - r * 0.15f, cy + r * 0.48f, strokePaint)
                    canvas.drawLine(cx + r * 0.15f, cy - r * 0.32f, cx + r * 0.15f, cy + r * 0.32f, strokePaint)
                    canvas.drawLine(cx + r * 0.45f, cy - r * 0.15f, cx + r * 0.45f, cy + r * 0.15f, strokePaint)
                }
                "WIFI_SHARE" -> {
                    val arc1 = RectF(cx - r * 0.55f, cy - r * 0.45f, cx + r * 0.55f, cy + r * 0.65f)
                    val arc2 = RectF(cx - r * 0.32f, cy - r * 0.20f, cx + r * 0.32f, cy + r * 0.45f)
                    canvas.drawArc(arc1, 210f, 120f, false, strokePaint)
                    canvas.drawArc(arc2, 210f, 120f, false, strokePaint)
                    canvas.drawCircle(cx, cy + r * 0.30f, 2.5f * d, fillPaint)
                }
                "VAULT_LOCK" -> {
                    val body = RectF(cx - r * 0.42f, cy - r * 0.05f, cx + r * 0.42f, cy + r * 0.52f)
                    canvas.drawRoundRect(body, 3f * d, 3f * d, strokePaint)
                    val shackle = RectF(cx - r * 0.28f, cy - r * 0.50f, cx + r * 0.28f, cy - r * 0.02f)
                    canvas.drawArc(shackle, 180f, 180f, false, strokePaint)
                }
                "SOUND_LAB" -> {
                    canvas.drawLine(cx - r * 0.40f, cy - r * 0.45f, cx - r * 0.40f, cy + r * 0.45f, strokePaint)
                    canvas.drawLine(cx, cy - r * 0.45f, cx, cy + r * 0.45f, strokePaint)
                    canvas.drawLine(cx + r * 0.40f, cy - r * 0.45f, cx + r * 0.40f, cy + r * 0.45f, strokePaint)
                    canvas.drawCircle(cx - r * 0.40f, cy - r * 0.15f, 3.2f * d, fillPaint)
                    canvas.drawCircle(cx, cy + r * 0.18f, 3.2f * d, fillPaint)
                    canvas.drawCircle(cx + r * 0.40f, cy - r * 0.22f, 3.2f * d, fillPaint)
                }
                "CLEANER_BROOM" -> {
                    canvas.drawCircle(cx, cy, r * 0.45f, strokePaint)
                    canvas.drawLine(cx - r * 0.20f, cy, cx - r * 0.04f, cy + r * 0.18f, strokePaint)
                    canvas.drawLine(cx - r * 0.04f, cy + r * 0.18f, cx + r * 0.25f, cy - r * 0.18f, strokePaint)
                }
                "THEME_PALETTE" -> {
                    canvas.drawCircle(cx, cy, r * 0.48f, strokePaint)
                    canvas.drawCircle(cx - r * 0.18f, cy - r * 0.15f, 2.5f * d, fillPaint)
                    canvas.drawCircle(cx + r * 0.18f, cy - r * 0.15f, 2.5f * d, fillPaint)
                    canvas.drawCircle(cx, cy + r * 0.18f, 2.5f * d, fillPaint)
                }
                "MASCOT_PAW" -> {
                    canvas.drawCircle(cx, cy + r * 0.14f, r * 0.28f, fillPaint)
                    canvas.drawCircle(cx - r * 0.34f, cy - r * 0.22f, r * 0.13f, fillPaint)
                    canvas.drawCircle(cx, cy - r * 0.36f, r * 0.13f, fillPaint)
                    canvas.drawCircle(cx + r * 0.34f, cy - r * 0.22f, r * 0.13f, fillPaint)
                }
                "GLOBE_LANG" -> {
                    canvas.drawCircle(cx, cy, r * 0.48f, strokePaint)
                    canvas.drawLine(cx - r * 0.48f, cy, cx + r * 0.48f, cy, strokePaint)
                    val oval = RectF(cx - r * 0.24f, cy - r * 0.48f, cx + r * 0.24f, cy + r * 0.48f)
                    canvas.drawOval(oval, strokePaint)
                }
                "ONE_HAND" -> {
                    val outerPhone = RectF(cx - r * 0.36f, cy - r * 0.52f, cx + r * 0.36f, cy + r * 0.52f)
                    canvas.drawRoundRect(outerPhone, 4f * d, 4f * d, strokePaint)
                    val innerReach = RectF(cx - r * 0.24f, cy - r * 0.05f, cx + r * 0.24f, cy + r * 0.42f)
                    canvas.drawRoundRect(innerReach, 2f * d, 2f * d, fillPaint)
                }
            }
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
