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
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
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
        val uniformCardColor = Color.parseColor("#141E30")
        val accentColor = Color.parseColor(theme.accentHex)

        ThemeStudioHelper.applyThemeAndOneHandModeToRoot(ctx, rootScroll)
        mainContainer.removeAllViews()
        mainContainer.setPadding((16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt(), (28 * d).toInt())

        // 1. Minimal Top Header Row
        val headerRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((2 * d).toInt(), (4 * d).toInt(), (2 * d).toInt(), (12 * d).toInt())
        }

        val tvHeaderTitle = TextView(ctx).apply {
            text = "Toolkit & Settings"
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
            setPadding((12 * d).toInt(), (5 * d).toInt(), (12 * d).toInt(), (5 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FFD700"))
                cornerRadius = 14f * d
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                VipBillingManager.showInternationalVipPaywall(ctx) {
                    rebuildControlCenterUi()
                }
            }
        }

        headerRow.addView(tvHeaderTitle)
        headerRow.addView(tvVipBadgeStatus)
        mainContainer.addView(headerRow)

        // 2. Sleek Compact VIP Strip (Single-Row Clean Banner instead of Bulky Multi-Card)
        val vipStrip = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (18 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(uniformCardColor)
                cornerRadius = 16f * d
                setStroke((1 * d).toInt(), Color.parseColor("#33FFD700"))
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                VipBillingManager.showInternationalVipPaywall(ctx) {
                    rebuildControlCenterUi()
                }
            }
        }

        val vipCrownImg = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((36 * d).toInt(), (36 * d).toInt()).apply {
                marginEnd = (12 * d).toInt()
            }
            setImageDrawable(MeVectorIconDrawable("VIP_CROWN", d, Color.parseColor("#FFD700")))
        }

        val vipTextCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (10 * d).toInt()
            }
        }

        tvVipMemberTitle = TextView(ctx).apply {
            text = "8K Studio Pass ($activePlanName)"
            setTextColor(Color.WHITE)
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val tvVipDesc = TextView(ctx).apply {
            text = "8K Upscaler • 200% Boost • Private Vault"
            setTextColor(Color.parseColor("#9FB4CE"))
            textSize = 11.5f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, (2 * d).toInt(), 0, 0)
        }

        val btnUpgradePill = TextView(ctx).apply {
            text = "Manage"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((12 * d).toInt(), (6 * d).toInt(), (12 * d).toInt(), (6 * d).toInt())
            background = GradientDrawable().apply {
                setColor(accentColor)
                cornerRadius = 12f * d
            }
        }

        vipTextCol.addView(tvVipMemberTitle)
        vipTextCol.addView(tvVipDesc)
        vipStrip.addView(vipCrownImg)
        vipStrip.addView(vipTextCol)
        vipStrip.addView(btnUpgradePill)
        mainContainer.addView(vipStrip)

        // 3. Clean 2x3 Symmetrical Media Tools Grid
        val tvGridSectionTitle = TextView(ctx).apply {
            text = "MEDIA TOOLS"
            setTextColor(Color.parseColor("#9FB4CE"))
            textSize = 11.5f
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
            ).apply { bottomMargin = (18 * d).toInt() }
        }

        toolsGrid.addView(
            createSymmetricalToolBox(
                ctx = ctx,
                iconType = "VAULT_LOCK",
                title = "Privacy Vault",
                subtitle = "PIN Protected Space",
                cardColor = uniformCardColor,
                accentColor = accentColor,
                d = d
            ) {
                openPrivacyVaultSafely(ctx)
            }.first
        )

        toolsGrid.addView(
            createSymmetricalToolBox(
                ctx = ctx,
                iconType = "WIFI_SHARE",
                title = "Turbo Share",
                subtitle = "5GHz Offline Transfer",
                cardColor = uniformCardColor,
                accentColor = accentColor,
                d = d
            ) {
                try {
                    startActivity(Intent(ctx, FileTransferActivity::class.java))
                } catch (_: Exception) {
                }
            }.first
        )

        toolsGrid.addView(
            createSymmetricalToolBox(
                ctx = ctx,
                iconType = "MP3_WAVE",
                title = "Video to MP3",
                subtitle = "Instant Audio Extractor",
                cardColor = uniformCardColor,
                accentColor = accentColor,
                d = d
            ) {
                try {
                    startActivity(Intent(ctx, Mp3ConverterActivity::class.java))
                } catch (_: Exception) {
                }
            }.first
        )

        toolsGrid.addView(
            createSymmetricalToolBox(
                ctx = ctx,
                iconType = "STATUS_SAVER",
                title = "Status Saver",
                subtitle = "Save WhatsApp Status",
                cardColor = uniformCardColor,
                accentColor = Color.parseColor("#00E676"),
                d = d
            ) {
                StatusSaverHelper.openWhatsAppStatusSaverDialog(ctx)
            }.first
        )

        val cleanerBoxPair = createSymmetricalToolBox(
            ctx = ctx,
            iconType = "CLEANER_BROOM",
            title = "Storage Cleaner",
            subtitle = "Clean Cache & Duplicates",
            cardColor = uniformCardColor,
            accentColor = accentColor,
            d = d
        ) {
            StorageAndAboutHelper.showFullScreenStorageCleaner(ctx) { freedBytes ->
                val mb = String.format(Locale.US, "%.1f MB Cleaned", freedBytes / (1024.0 * 1024.0))
                tvCleanerSubtitle.text = mb
            }
        }
        tvCleanerSubtitle = cleanerBoxPair.second
        toolsGrid.addView(cleanerBoxPair.first)

        val trashCount = PermanentDeleteHelper.getRecycleBinCount(ctx)
        toolsGrid.addView(
            createSymmetricalToolBox(
                ctx = ctx,
                iconType = "RECYCLE_BIN",
                title = "Recycle Bin",
                subtitle = if (trashCount > 0) "$trashCount Items (30d Safe)" else "Recently Deleted",
                cardColor = uniformCardColor,
                accentColor = Color.parseColor("#FF5252"),
                d = d
            ) {
                PermanentDeleteHelper.openRecycleBinDialog(ctx) {
                    rebuildControlCenterUi()
                }
            }.first
        )

        mainContainer.addView(toolsGrid)

        // 4. Unified Personalization & Tools Card (Clean Rows instead of Scattered Boxes)
        val tvPersonalizeTitle = TextView(ctx).apply {
            text = "PERSONALIZATION & COMPANION"
            setTextColor(Color.parseColor("#9FB4CE"))
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((4 * d).toInt(), 0, 0, (8 * d).toInt())
        }
        mainContainer.addView(tvPersonalizeTitle)

        val personalizeCard = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * d).toInt(), (6 * d).toInt(), (16 * d).toInt(), (6 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (18 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(uniformCardColor)
                cornerRadius = 16f * d
                setStroke((1 * d).toInt(), Color.parseColor("#18FFFFFF"))
            }
        }

        val customWallUri = ThemeStudioHelper.getCustomGalleryPhotoUri(ctx)
        val themeSubLabel = if (!customWallUri.isNullOrEmpty()) "Custom Wallpaper" else theme.name
        val themeRowPair = createCleanNavigationRow(
            ctx = ctx,
            iconType = "THEME_PALETTE",
            title = "Theme & Wallpaper Studio",
            valueLabel = themeSubLabel,
            accentColor = accentColor,
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
        tvThemeActiveSubtitle = themeRowPair.second
        personalizeCard.addView(themeRowPair.first)
        personalizeCard.addView(createThinDivider(ctx, d))

        val mascotStatus = if (MascotCompanionHelper.isMascotEnabled(ctx)) {
            MascotCompanionHelper.getSelectedCharacter(ctx).name
        } else {
            "Off"
        }
        personalizeCard.addView(
            createCleanNavigationRow(
                ctx = ctx,
                iconType = "MASCOT_PAW",
                title = "Realistic Puppy Companion",
                valueLabel = mascotStatus,
                accentColor = accentColor,
                d = d
            ) {
                MascotCompanionHelper.showMascotCustomizationDialog(ctx) {
                    rebuildControlCenterUi()
                }
            }.first
        )
        personalizeCard.addView(createThinDivider(ctx, d))

        val currentLangDisplay = MultiLanguageHelper.getCurrentLanguageDisplayName(ctx)
        personalizeCard.addView(
            createCleanNavigationRow(
                ctx = ctx,
                iconType = "GLOBE_LANG",
                title = "App Language",
                valueLabel = currentLangDisplay,
                accentColor = accentColor,
                d = d
            ) {
                MultiLanguageHelper.showLanguageSelectorDialog(ctx) {
                    rebuildControlCenterUi()
                }
            }.first
        )
        personalizeCard.addView(createThinDivider(ctx, d))

        personalizeCard.addView(
            createCleanNavigationRow(
                ctx = ctx,
                iconType = "SOUND_LAB",
                title = "Sound & Pitch Lab (Lofi / Reverb)",
                valueLabel = "Open",
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
        personalizeCard.addView(createThinDivider(ctx, d))

        personalizeCard.addView(
            createCleanNavigationRow(
                ctx = ctx,
                iconType = "STREAM_NET",
                title = "Network Stream URL (HTTP / M3U8)",
                valueLabel = "Play",
                accentColor = accentColor,
                d = d
            ) {
                NetworkStreamDialog.showNetworkStreamInputDialog(ctx)
            }.first
        )
        personalizeCard.addView(createThinDivider(ctx, d))

        val oneHandEnabled = ThemeStudioHelper.isOneHandModeEnabled(ctx)
        personalizeCard.addView(
            createSettingSwitchRow(
                ctx = ctx,
                title = "One-Hand Reachability Mode",
                subtitle = "Shift screen controls down for easy single-hand use",
                initialChecked = oneHandEnabled,
                d = d
            ) { checked ->
                ThemeStudioHelper.setOneHandModeEnabled(ctx, checked)
                rebuildControlCenterUi()
            }
        )

        mainContainer.addView(personalizeCard)

        // 5. Playback & Audio Engine Preferences Card
        val tvEngineTitle = TextView(ctx).apply {
            text = "PLAYBACK & ENGINE"
            setTextColor(Color.parseColor("#9FB4CE"))
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((4 * d).toInt(), 0, 0, (8 * d).toInt())
        }
        mainContainer.addView(tvEngineTitle)

        val settingsCard = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * d).toInt(), (6 * d).toInt(), (16 * d).toInt(), (6 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (18 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(uniformCardColor)
                cornerRadius = 16f * d
                setStroke((1 * d).toInt(), Color.parseColor("#18FFFFFF"))
            }
        }

        val layoutPrefs = ctx.getSharedPreferences("MAX_LAYOUT_PREFS", Context.MODE_PRIVATE)
        val audioPrefs = ctx.getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)

        settingsCard.addView(
            createSettingSwitchRow(
                ctx = ctx,
                title = "8K Hardware + Software Hybrid Decoder",
                subtitle = "Smooth playback for 4K, 8K & HEVC MKV files",
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
                title = "Super Loud 200% Volume Booster",
                subtitle = "Allow hardware audio gain up to 200%",
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
                title = "Show HDR & Subtitle Quick Badges",
                subtitle = "Display quick visual enhancer & CC controls on player",
                initialChecked = layoutPrefs.getBoolean("player_show_hdr_cc", true),
                d = d
            ) { checked ->
                layoutPrefs.edit().putBoolean("player_show_hdr_cc", checked).apply()
            }
        )

        mainContainer.addView(settingsCard)

        // 6. Support & About Card
        val supportCard = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * d).toInt(), (6 * d).toInt(), (16 * d).toInt(), (6 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            background = GradientDrawable().apply {
                setColor(uniformCardColor)
                cornerRadius = 16f * d
                setStroke((1 * d).toInt(), Color.parseColor("#18FFFFFF"))
            }
        }

        supportCard.addView(
            createActionRowItem(
                ctx = ctx,
                title = "Rate Us 5 Stars",
                rightLabel = "★★★★★",
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
                title = "Help & Feedback",
                rightLabel = ">",
                rightColor = Color.parseColor("#9FB4CE"),
                d = d
            ) {
                showCleanFeedbackSheet(ctx)
            }
        )
        supportCard.addView(createThinDivider(ctx, d))

        supportCard.addView(
            createActionRowItem(
                ctx = ctx,
                title = "Share App with Friends",
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
                title = "About OG Player v3.5",
                rightLabel = "Info >",
                rightColor = Color.parseColor("#9FB4CE"),
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
            setPadding((12 * d).toInt(), (12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt())
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = (74 * d).toInt()
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins((4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt())
            }
            background = GradientDrawable().apply {
                setColor(cardColor)
                cornerRadius = 16f * d
                setStroke((1 * d).toInt(), Color.parseColor("#18FFFFFF"))
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
        }

        val iconView = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((34 * d).toInt(), (34 * d).toInt()).apply {
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
            ellipsize = TextUtils.TruncateAt.END
        }

        val tvSub = TextView(ctx).apply {
            text = subtitle
            setTextColor(Color.parseColor("#9FB4CE"))
            textSize = 11f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, (2 * d).toInt(), 0, 0)
        }

        textCol.addView(tvTitle)
        textCol.addView(tvSub)
        box.addView(iconView)
        box.addView(textCol)
        return Pair(box, tvSub)
    }

    private fun createCleanNavigationRow(
        ctx: Context,
        iconType: String,
        title: String,
        valueLabel: String,
        accentColor: Int,
        d: Float,
        onClick: () -> Unit
    ): Pair<LinearLayout, TextView> {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (11 * d).toInt(), 0, (11 * d).toInt())
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
        }

        val iconView = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((30 * d).toInt(), (30 * d).toInt()).apply {
                marginEnd = (12 * d).toInt()
            }
            setImageDrawable(MeVectorIconDrawable(iconType, d, accentColor))
        }

        val tvTitle = TextView(ctx).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (8 * d).toInt()
            }
        }

        val tvValue = TextView(ctx).apply {
            text = "$valueLabel  >"
            setTextColor(Color.parseColor("#9FB4CE"))
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        }

        row.addView(iconView)
        row.addView(tvTitle)
        row.addView(tvValue)
        return Pair(row, tvValue)
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
            setTextColor(Color.parseColor("#9FB4CE"))
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
            setPadding(0, (12 * d).toInt(), 0, (12 * d).toInt())
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
        }

        val tvTitle = TextView(ctx).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvRight = TextView(ctx).apply {
            text = rightLabel
            setTextColor(rightColor)
            textSize = 13f
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
            setBackgroundColor(Color.parseColor("#14FFFFFF"))
        }
    }

    private fun openPrivacyVaultSafely(ctx: Context) {
        val act = activity
        if (act != null) {
            FakeVaultHelper.openSmartDualPinVaultDialog(act)
            return
        }
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
            val shareText = "Download OG Player - Ultra HD 8K Video Player, Shorts Reels, Built-in Dolby Equalizer & MP3 Converter:\nhttps://play.google.com/store/apps/details?id=$pkg"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "OG Player Ultra Media Player")
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(intent, "Share OG Player"))
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
            color = Color.parseColor("#18FFFFFF")
        }

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.0f * d
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
                    canvas.drawCircle(cx - r * 0.40f, cy - r * 0.15f, 3.0f * d, fillPaint)
                    canvas.drawCircle(cx, cy + r * 0.18f, 3.0f * d, fillPaint)
                    canvas.drawCircle(cx + r * 0.40f, cy - r * 0.22f, 3.0f * d, fillPaint)
                }
                "CLEANER_BROOM", "STATUS_SAVER" -> {
                    canvas.drawCircle(cx, cy, r * 0.45f, strokePaint)
                    canvas.drawLine(cx - r * 0.20f, cy, cx - r * 0.04f, cy + r * 0.18f, strokePaint)
                    canvas.drawLine(cx - r * 0.04f, cy + r * 0.18f, cx + r * 0.25f, cy - r * 0.18f, strokePaint)
                }
                "RECYCLE_BIN" -> {
                    canvas.drawLine(cx - r * 0.46f, cy - r * 0.34f, cx + r * 0.46f, cy - r * 0.34f, strokePaint)
                    val bin = RectF(cx - r * 0.36f, cy - r * 0.34f, cx + r * 0.36f, cy + r * 0.48f)
                    canvas.drawRoundRect(bin, 3f * d, 3f * d, strokePaint)
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
                "GLOBE_LANG", "STREAM_NET" -> {
                    canvas.drawCircle(cx, cy, r * 0.48f, strokePaint)
                    canvas.drawLine(cx - r * 0.48f, cy, cx + r * 0.48f, cy, strokePaint)
                    val oval = RectF(cx - r * 0.24f, cy - r * 0.48f, cx + r * 0.24f, cy + r * 0.48f)
                    canvas.drawOval(oval, strokePaint)
                }
                else -> {
                    canvas.drawCircle(cx, cy, r * 0.44f, strokePaint)
                }
            }
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
