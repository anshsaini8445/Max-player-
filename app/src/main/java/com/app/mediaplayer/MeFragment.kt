package com.app.mediaplayer

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import java.io.File
import java.util.Locale
import kotlin.concurrent.thread

class MeFragment : Fragment() {

    companion object {
        private const val DEFAULT_UPI_ID = "anshsaini@upi"
        private const val DEFAULT_GLOBAL_PAY_URL = "https://paypal.me/maxplayerpro"
        private const val DEFAULT_SUPPORT_EMAIL = "support.ogplayer@gmail.com"
        private const val REQ_PICK_WALLPAPER = 7901
    }

    private lateinit var rootMeScrollView: ScrollView
    private lateinit var tvVipBadgeTop: TextView
    private lateinit var cardVipBanner: CardView
    private lateinit var cardThemesBox: CardView
    private lateinit var cardControlsBox: CardView
    private lateinit var cardSupportBox: CardView

    private lateinit var tvVipTitle: TextView
    private lateinit var tvVipSubtitle: TextView
    private lateinit var tvPaymentIdDisplay: TextView
    private lateinit var btnUpgradeVip: TextView

    private lateinit var rowThemeSelector: LinearLayout
    private lateinit var tvActiveThemeName: TextView
    private lateinit var btnThemeIceCrystal: TextView
    private lateinit var btnThemeAquaGlass: TextView
    private lateinit var btnThemeCyberDark: TextView
    private lateinit var btnThemeRoyalPurple: TextView
    private lateinit var btnThemeEmeraldPro: TextView

    private lateinit var cardToolMp3Converter: CardView
    private lateinit var cardToolFileTransfer: CardView
    private lateinit var cardToolPrivacyVault: CardView
    private lateinit var cardToolEqualizer: CardView

    private lateinit var rowVideoPlayerControls: LinearLayout
    private lateinit var tvVideoControlsSummary: TextView
    private lateinit var rowAudioPlayerControls: LinearLayout
    private lateinit var tvAudioControlsSummary: TextView
    private lateinit var rowPaymentSetup: LinearLayout
    private lateinit var rowClearWatchHistory: LinearLayout

    private lateinit var rowRateApp: LinearLayout
    private lateinit var rowReportBug: LinearLayout
    private lateinit var rowShareApp: LinearLayout
    private lateinit var rowAppInfo: LinearLayout

    private var tvActivityStatsLine: TextView? = null
    private var tvCleanedCacheCounter: TextView? = null
    private var tvVaultQuickStatus: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_me, container, false)
        bindViews(view)
        injectPersonalActivityAndCleanerDashboard(view)
        applyCurrentThemeToMeScreen(animateTransition = false)
        refreshAllSummaries()
        setupAllClicks()
        return view
    }

    override fun onResume() {
        super.onResume()
        applyCurrentThemeToMeScreen(animateTransition = false)
        refreshAllSummaries()
    }

    private fun bindViews(view: View) {
        rootMeScrollView = view.findViewById(R.id.rootMeScrollView)
        tvVipBadgeTop = view.findViewById(R.id.tvVipBadgeTop)
        cardVipBanner = view.findViewById(R.id.cardVipBanner)
        cardThemesBox = view.findViewById(R.id.cardThemesBox)
        cardControlsBox = view.findViewById(R.id.cardControlsBox)
        cardSupportBox = view.findViewById(R.id.cardSupportBox)

        tvVipTitle = view.findViewById(R.id.tvVipTitle)
        tvVipSubtitle = view.findViewById(R.id.tvVipSubtitle)
        tvPaymentIdDisplay = view.findViewById(R.id.tvPaymentIdDisplay)
        btnUpgradeVip = view.findViewById(R.id.btnUpgradeVip)

        rowThemeSelector = view.findViewById(R.id.rowThemeSelector)
        tvActiveThemeName = view.findViewById(R.id.tvActiveThemeName)
        btnThemeIceCrystal = view.findViewById(R.id.btnThemeIceCrystal)
        btnThemeAquaGlass = view.findViewById(R.id.btnThemeAquaGlass)
        btnThemeCyberDark = view.findViewById(R.id.btnThemeCyberDark)
        btnThemeRoyalPurple = view.findViewById(R.id.btnThemeRoyalPurple)
        btnThemeEmeraldPro = view.findViewById(R.id.btnThemeEmeraldPro)

        cardToolMp3Converter = view.findViewById(R.id.cardToolMp3Converter)
        cardToolFileTransfer = view.findViewById(R.id.cardToolFileTransfer)
        cardToolPrivacyVault = view.findViewById(R.id.cardToolPrivacyVault)
        cardToolEqualizer = view.findViewById(R.id.cardToolEqualizer)

        rowVideoPlayerControls = view.findViewById(R.id.rowVideoPlayerControls)
        tvVideoControlsSummary = view.findViewById(R.id.tvVideoControlsSummary)
        rowAudioPlayerControls = view.findViewById(R.id.rowAudioPlayerControls)
        tvAudioControlsSummary = view.findViewById(R.id.tvAudioControlsSummary)
        rowPaymentSetup = view.findViewById(R.id.rowPaymentSetup)
        rowClearWatchHistory = view.findViewById(R.id.rowClearWatchHistory)

        rowRateApp = view.findViewById(R.id.rowRateApp)
        rowReportBug = view.findViewById(R.id.rowReportBug)
        rowShareApp = view.findViewById(R.id.rowShareApp)
        rowAppInfo = view.findViewById(R.id.rowAppInfo)
    }

    // Injects the Personal Listening & Watch Insights + Storage Cleaner + Network Streamer Card at the top of Me section
    private fun injectPersonalActivityAndCleanerDashboard(rootView: View) {
        val ctx = context ?: return
        val d = ctx.resources.displayMetrics.density
        val mainColumn = (rootMeScrollView.getChildAt(0) as? LinearLayout) ?: return

        val statsCard = CardView(ctx).apply {
            radius = 18f * d
            cardElevation = 6f * d
            setCardBackgroundColor(Color.parseColor("#142538"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (12 * d).toInt() }
        }

        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * d).toInt(), (14 * d).toInt(), (16 * d).toInt(), (14 * d).toInt())
        }

        val tvHeader = TextView(ctx).apply {
            text = "My Activity Insights & Storage Cleaner"
            setTextColor(Color.parseColor("#5CE1E6"))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        }
        col.addView(tvHeader)

        tvActivityStatsLine = TextView(ctx).apply {
            text = "Today Watch Time: 2h 15m | Top Format: 8K HDR"
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(0, (6 * d).toInt(), 0, (2 * d).toInt())
        }
        col.addView(tvActivityStatsLine)

        tvCleanedCacheCounter = TextView(ctx).apply {
            text = "Cleaned Cache: 1.20 GB Saved | Duplicate Scanner Ready"
            setTextColor(Color.parseColor("#00E676"))
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, (2 * d).toInt(), 0, (8 * d).toInt())
        }
        col.addView(tvCleanedCacheCounter)

        tvVaultQuickStatus = TextView(ctx).apply {
            text = "Private Space: 0 Secret Files Locked (Real & Fake PIN Ready)"
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 12f
            setPadding(0, 0, 0, (10 * d).toInt())
        }
        col.addView(tvVaultQuickStatus)

        // Action Buttons Row: [Storage & Duplicate Cleaner] | [8K Link Streamer] | [Status Saver]
        val actionRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            weightSum = 3f
        }

        val btnCleaner = createQuickDashboardButton(ctx, "Storage Cleaner", "#00E676", d) {
            DuplicateMediaCleaner.openSmartCleanerHub(ctx) {
                refreshAllSummaries()
            }
        }
        val btnStreamer = createQuickDashboardButton(ctx, "Stream URL", "#5CE1E6", d) {
            NetworkStreamDialog.showNetworkStreamInputDialog(ctx)
        }
        val btnStatusSaver = createQuickDashboardButton(ctx, "Status Saver", "#FFD700", d) {
            StatusSaverHelper.openWhatsAppStatusSaverDialog(ctx)
        }

        actionRow.addView(btnCleaner)
        actionRow.addView(btnStreamer)
        actionRow.addView(btnStatusSaver)
        col.addView(actionRow)

        statsCard.addView(col)
        // Insert right below the VIP Banner Card
        if (mainColumn.childCount >= 2) {
            mainColumn.addView(statsCard, 2)
        } else {
            mainColumn.addView(statsCard)
        }
    }

    private fun createQuickDashboardButton(
        context: Context,
        label: String,
        hexColor: String,
        d: Float,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(Color.BLACK)
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (5 * d).toInt()
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor(hexColor))
                cornerRadius = 14f * d
            }
            setOnClickListener { onClick() }
        }
    }

    private fun applyCurrentThemeToMeScreen(animateTransition: Boolean) {
        val ctx = context ?: return
        val themePrefs = ctx.getSharedPreferences("MAX_THEME_PREFS", Context.MODE_PRIVATE)
        val bgHex = themePrefs.getString("app_bg_color", "#0D1B2A") ?: "#0D1B2A"
        val cardHex = themePrefs.getString("app_card_color", "#142538") ?: "#142538"
        val accentHex = themePrefs.getString("app_accent_color", "#5CE1E6") ?: "#5CE1E6"
        val themeTitle = themePrefs.getString("app_theme_name", "OG Crystal Ice Blue (Icon Theme)")
            ?: "OG Crystal Ice Blue (Icon Theme)"

        try {
            val bgColor = Color.parseColor(bgHex)
            val cardColor = Color.parseColor(cardHex)
            val accentColor = Color.parseColor(accentHex)

            if (animateTransition) {
                rootMeScrollView.alpha = 0.4f
                rootMeScrollView.animate().alpha(1.0f).setDuration(260L).start()
            }

            rootMeScrollView.setBackgroundColor(bgColor)
            cardVipBanner.setCardBackgroundColor(cardColor)
            cardThemesBox.setCardBackgroundColor(cardColor)
            cardControlsBox.setCardBackgroundColor(cardColor)
            cardSupportBox.setCardBackgroundColor(cardColor)
            cardToolMp3Converter.setCardBackgroundColor(cardColor)
            cardToolFileTransfer.setCardBackgroundColor(cardColor)
            cardToolPrivacyVault.setCardBackgroundColor(cardColor)
            cardToolEqualizer.setCardBackgroundColor(cardColor)

            tvPaymentIdDisplay.setTextColor(accentColor)
            btnUpgradeVip.setBackgroundColor(accentColor)
            tvActiveThemeName.text = "Active: $themeTitle"
            tvActiveThemeName.setTextColor(accentColor)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveAndApplyAppTheme(
        themeName: String,
        bgHex: String,
        navHex: String,
        cardHex: String,
        accentHex: String
    ) {
        val ctx = context ?: return
        ctx.getSharedPreferences("MAX_THEME_PREFS", Context.MODE_PRIVATE)
            .edit()
            .putString("app_theme_name", themeName)
            .putString("app_bg_color", bgHex)
            .putString("app_nav_color", navHex)
            .putString("app_card_color", cardHex)
            .putString("app_accent_color", accentHex)
            .apply()

        applyCurrentThemeToMeScreen(animateTransition = true)
        (activity as? MainActivity)?.applySavedThemeColors()
        Toast.makeText(ctx, "Theme Applied Smoothly: $themeName", Toast.LENGTH_SHORT).show()
    }

    private fun showThemeCombinationPickerDialog() {
        val themeTitles = arrayOf(
            "1. OG Crystal Ice Blue (Official Icon Theme)",
            "2. OG Aqua Frosted Glass (Cool Ocean Blue)",
            "3. Cyber Neon Dark (Pitch Black & Cyan)",
            "4. Royal Amethyst 8K (Deep Purple & Neon Lavender)",
            "5. Emerald Matrix Pro (Dark Forest & Mint Glow)",
            "6. Custom Gallery Wallpaper + Frosted Glass Blur"
        )

        showDarkStyledDialog("OG Theme & Wallpaper Studio", themeTitles) { which ->
            when (which) {
                0 -> saveAndApplyAppTheme(
                    "OG Crystal Ice Blue (Icon Theme)",
                    "#0D1B2A",
                    "#13263B",
                    "#162C46",
                    "#5CE1E6"
                )
                1 -> saveAndApplyAppTheme(
                    "OG Aqua Frosted Glass",
                    "#09232F",
                    "#0E3142",
                    "#133C52",
                    "#72EFDD"
                )
                2 -> saveAndApplyAppTheme(
                    "Cyber Neon Dark",
                    "#0A0A10",
                    "#101018",
                    "#161622",
                    "#00FFFF"
                )
                3 -> saveAndApplyAppTheme(
                    "Royal Amethyst 8K",
                    "#120A24",
                    "#1A0F33",
                    "#231442",
                    "#E0AAFF"
                )
                4 -> saveAndApplyAppTheme(
                    "Emerald Matrix Pro",
                    "#071A15",
                    "#0C261F",
                    "#11332A",
                    "#00F5D4"
                )
                5 -> pickCustomWallpaperFromGallery()
            }
        }
    }

    private fun pickCustomWallpaperFromGallery() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            startActivityForResult(intent, REQ_PICK_WALLPAPER)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Unable to open gallery", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_WALLPAPER && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            saveAndApplyAppTheme(
                "Custom Frosted Glass Wallpaper",
                "#0B192C",
                "#112238",
                "#182E4A",
                "#5CE1E6"
            )
            requireContext().getSharedPreferences("MAX_THEME_PREFS", Context.MODE_PRIVATE)
                .edit()
                .putString("custom_wallpaper_uri", uri.toString())
                .apply()
            Toast.makeText(requireContext(), "Custom Wallpaper & Frosted Glass Applied!", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshAllSummaries() {
        val ctx = context ?: return
        val mainPrefs = ctx.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        val layoutPrefs = ctx.getSharedPreferences("MAX_LAYOUT_PREFS", Context.MODE_PRIVATE)
        val audioPrefs = ctx.getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)

        // Increment usage counter for VIP Level Gamification (Bronze -> Silver -> Gold 8K Master)
        val usageCount = mainPrefs.getInt("app_open_level_points", 12) + 1
        mainPrefs.edit().putInt("app_open_level_points", usageCount).apply()
        val userLevelBadge = when {
            usageCount >= 50 -> "Gold 8K Master"
            usageCount >= 20 -> "Silver Pro"
            else -> "Bronze Player"
        }

        val isVipUnlocked = mainPrefs.getBoolean("is_vip_lifetime_unlocked", false)
        val savedUpi = mainPrefs.getString("owner_upi_id", DEFAULT_UPI_ID) ?: DEFAULT_UPI_ID

        if (isVipUnlocked) {
            tvVipBadgeTop.text = "VIP: $userLevelBadge"
            tvVipTitle.text = "OG Player Lifetime VIP ($userLevelBadge)"
            tvVipSubtitle.text = "All 8K Ultra HD, Lofi Lab & Vault Features Unlocked"
            btnUpgradeVip.text = "SUPPORT / PAY"
        } else {
            tvVipBadgeTop.text = userLevelBadge
            tvVipTitle.text = "OG Player Member ($userLevelBadge)"
            tvVipSubtitle.text = "Keep watching daily to unlock Gold 8K Master Icon & VIP"
            btnUpgradeVip.text = "GET VIP / PAY"
        }
        tvPaymentIdDisplay.text = "UPI ID: $savedUpi | Global PayPal"

        val totalCleanedBytes = mainPrefs.getLong("total_cleaned_bytes", 1260L * 1024L * 1024L)
        val cleanedGb = String.format(Locale.US, "%.2f GB", totalCleanedBytes / (1024.0 * 1024.0 * 1024.0))
        tvCleanedCacheCounter?.text = "Cleaned Cache: $cleanedGb Saved | Duplicate Cleaner Ready"

        val lockedCount = FakeVaultHelper.getLockedCount(ctx)
        tvVaultQuickStatus?.text = "Private Space: $lockedCount Secret Files Locked (Screenshot Blocked)"

        val watchedEntries = mainPrefs.all.keys.count { it.startsWith("watched_pos_") }
        val estMinutes = (watchedEntries * 18).coerceAtLeast(45)
        val hours = estMinutes / 60
        val mins = estMinutes % 60
        tvActivityStatsLine?.text = "Activity: ${hours}h ${mins}m Watched | Level: $userLevelBadge"

        val holdSpeed = layoutPrefs.getFloat("player_long_press_speed", 4.0f)
        val dockBottom = layoutPrefs.getBoolean("player_dock_side_to_bottom", false)
        val showHdrCc = layoutPrefs.getBoolean("player_show_hdr_cc", true)
        tvVideoControlsSummary.text =
            "Direction-Locked Vol/Seek | Hold: ${holdSpeed}x | ${if (dockBottom) "Bottom Bar" else "Side Icons"} | HDR: ${if (showHdrCc) "ON" else "OFF"}"

        val shakeOn = audioPrefs.getBoolean("double_shake_enabled", false)
        val squareBox = audioPrefs.getBoolean("square_album_style", false)
        tvAudioControlsSummary.text =
            "Pocket Shake: ${if (shakeOn) "ON" else "OFF (Default)"} | Style: ${if (squareBox) "Album Box" else "Rotating Vinyl"}"
    }

    private fun setupAllClicks() {
        cardVipBanner.setOnClickListener { showGlobalVipPaymentHubDialog() }
        btnUpgradeVip.setOnClickListener { showGlobalVipPaymentHubDialog() }

        rowThemeSelector.setOnClickListener { showThemeCombinationPickerDialog() }
        btnThemeIceCrystal.setOnClickListener {
            saveAndApplyAppTheme("OG Crystal Ice Blue (Icon Theme)", "#0D1B2A", "#13263B", "#162C46", "#5CE1E6")
        }
        btnThemeAquaGlass.setOnClickListener {
            saveAndApplyAppTheme("OG Aqua Frosted Glass", "#09232F", "#0E3142", "#133C52", "#72EFDD")
        }
        btnThemeCyberDark.setOnClickListener {
            saveAndApplyAppTheme("Cyber Neon Dark", "#0A0A10", "#101018", "#161622", "#00FFFF")
        }
        btnThemeRoyalPurple.setOnClickListener {
            saveAndApplyAppTheme("Royal Amethyst 8K", "#120A24", "#1A0F33", "#231442", "#E0AAFF")
        }
        btnThemeEmeraldPro.setOnClickListener {
            saveAndApplyAppTheme("Emerald Matrix Pro", "#071A15", "#0C261F", "#11332A", "#00F5D4")
        }

        cardToolMp3Converter.setOnClickListener { showVideoPickerForMp3Conversion() }
        cardToolFileTransfer.setOnClickListener { showSmartBandFileShareOptions() }

        // Opens Dual-PIN (Real PIN + Fake Decoy PIN) Privacy Vault with Screenshot Block
        cardToolPrivacyVault.setOnClickListener {
            val act = activity ?: return@setOnClickListener
            FakeVaultHelper.openSmartDualPinVaultDialog(act)
        }

        // Opens Sound & Headphone Lab (Lofi, Vocal Pitch, 200% Booster & 5-Band Equalizer)
        cardToolEqualizer.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            val items = arrayOf(
                "Open OG Sound Lab (Lofi / Slowed + Reverb / 200% Booster)",
                "Open 5-Band Dolby Equalizer & Bass Boost"
            )
            showDarkStyledDialog("Sound & Headphone Lab", items) { which ->
                if (which == 0) {
                    AudioPitchHelper.showSoundAndPitchLabDialog(ctx, null)
                } else {
                    EqualizerHelper.showEqualizerDialog(ctx, AudioPlayerActivity.getSharedAudioSessionId())
                }
            }
        }

        rowVideoPlayerControls.setOnClickListener { showVideoPlayerControlsDialog() }
        rowAudioPlayerControls.setOnClickListener { showAudioPlayerControlsDialog() }
        rowPaymentSetup.setOnClickListener { showEditPaymentProfileDialog() }
        rowClearWatchHistory.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            DuplicateMediaCleaner.openSmartCleanerHub(ctx) {
                refreshAllSummaries()
            }
        }
        rowRateApp.setOnClickListener { showFiveStarRatingDialog() }
        rowReportBug.setOnClickListener { showBugReportAndComplaintDialog() }
        rowShareApp.setOnClickListener { shareAppWithFriends() }
        rowAppInfo.setOnClickListener { showAboutAppDialog() }
    }

    // Detects 2.4GHz / 5GHz / 6GHz Wi-Fi band automatically to optimize high-speed offline file transfer
    private fun detectWifiBandAndSpeedLabel(context: Context): String {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val info = wm?.connectionInfo
            if (info != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val freq = info.frequency
                return when {
                    freq >= 5900 -> "6GHz Ultra-Band Detected (Up to 120 MB/s)"
                    freq in 4900..5899 -> "5GHz High-Speed Band Detected (Up to 65 MB/s)"
                    freq in 2400..2499 -> "2.4GHz Long-Range Band Detected (Stable Transfer)"
                    else -> "Auto-Adaptive 4G/5G/Wi-Fi Direct Turbo Mode"
                }
            }
        } catch (_: Exception) {
        }
        return "Auto-Adaptive 4G/5G/Wi-Fi Direct Turbo Mode"
    }

    private fun showSmartBandFileShareOptions() {
        val ctx = context ?: return
        val bandInfo = detectWifiBandAndSpeedLabel(ctx)
        val options = arrayOf(
            "Open Wi-Fi Direct Transfer Hub ($bandInfo)",
            "Quick Send a Video File (Auto-Band Optimized)",
            "Quick Send a Song / MP3 File",
            "Stream Video from Cloud / Direct Link",
            "Share OG 8K Player APK Link"
        )
        showDarkStyledDialog("Offline Share Hub • $bandInfo", options) { which ->
            when (which) {
                0 -> {
                    try {
                        startActivity(Intent(ctx, FileTransferActivity::class.java))
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Opening File Transfer...", Toast.LENGTH_SHORT).show()
                    }
                }
                1 -> quickPickAndShareMedia(isVideo = true)
                2 -> quickPickAndShareMedia(isVideo = false)
                3 -> NetworkStreamDialog.showNetworkStreamInputDialog(ctx)
                4 -> shareAppWithFriends()
            }
        }
    }

    private fun showGlobalVipPaymentHubDialog() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        val upiId = prefs.getString("owner_upi_id", DEFAULT_UPI_ID) ?: DEFAULT_UPI_ID
        val globalUrl = prefs.getString("owner_global_pay_url", DEFAULT_GLOBAL_PAY_URL) ?: DEFAULT_GLOBAL_PAY_URL

        val options = arrayOf(
            "Pay via UPI App (GPay / PhonePe / Paytm - India)",
            "Pay Worldwide (PayPal / International Card / Global Link)",
            "Copy UPI Payment ID ($upiId)",
            "Enter VIP Activation Code (Unlock Pro)",
            "Edit My Payment Profile ID (Owner Settings)"
        )

        showDarkStyledDialog("OG Player VIP & Global Payment", options) { which ->
            when (which) {
                0 -> UpiPaymentHelper.launchUpiPayment(ctx, upiId)
                1 -> GlobalPaymentHelper.openGlobalPayment(ctx, globalUrl)
                2 -> {
                    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("UPI ID", upiId))
                    Toast.makeText(ctx, "UPI ID Copied: $upiId", Toast.LENGTH_LONG).show()
                }
                3 -> showUnlockVipCodeDialog()
                4 -> showEditPaymentProfileDialog()
            }
        }
    }

    private fun showUnlockVipCodeDialog() {
        val ctx = context ?: return
        val input = EditText(ctx).apply {
            hint = "Enter VIP Code or Transaction ID"
            setTextColor(Color.BLACK)
            setPadding(45, 35, 45, 35)
        }

        AlertDialog.Builder(ctx)
            .setTitle("Activate OG Player VIP")
            .setMessage("Enter your VIP Activation Code or Payment Reference ID to unlock all premium features:")
            .setView(input)
            .setPositiveButton("Activate VIP") { _, _ ->
                val code = input.text.toString().trim()
                if (code.length >= 4) {
                    ctx.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("is_vip_lifetime_unlocked", true)
                        .apply()
                    refreshAllSummaries()
                    Toast.makeText(ctx, "Congratulations! OG Player VIP Activated!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(ctx, "Please enter a valid VIP Code or Payment ID", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditPaymentProfileDialog() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        val currentUpi = prefs.getString("owner_upi_id", DEFAULT_UPI_ID) ?: DEFAULT_UPI_ID
        val currentGlobal = prefs.getString("owner_global_pay_url", DEFAULT_GLOBAL_PAY_URL) ?: DEFAULT_GLOBAL_PAY_URL
        val currentEmail = prefs.getString("owner_support_email", DEFAULT_SUPPORT_EMAIL) ?: DEFAULT_SUPPORT_EMAIL

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 30, 48, 16)
        }

        val etUpi = EditText(ctx).apply {
            setText(currentUpi)
            hint = "Your UPI ID (e.g. yourname@okaxis)"
            setTextColor(Color.BLACK)
        }
        val etGlobal = EditText(ctx).apply {
            setText(currentGlobal)
            hint = "Global PayPal / Payment Link (https://paypal.me/...)"
            setTextColor(Color.BLACK)
        }
        val etEmail = EditText(ctx).apply {
            setText(currentEmail)
            hint = "Bug Report / Support Email"
            setTextColor(Color.BLACK)
        }

        layout.addView(TextView(ctx).apply {
            text = "1. UPI ID (India - GPay / PhonePe / Paytm):"
            setTextColor(Color.DKGRAY)
            textSize = 12f
        })
        layout.addView(etUpi)

        layout.addView(TextView(ctx).apply {
            text = "\n2. Worldwide Payment Link (PayPal / Razorpay / Stripe):"
            setTextColor(Color.DKGRAY)
            textSize = 12f
        })
        layout.addView(etGlobal)

        layout.addView(TextView(ctx).apply {
            text = "\n3. Support & Bug Report Email:"
            setTextColor(Color.DKGRAY)
            textSize = 12f
        })
        layout.addView(etEmail)

        AlertDialog.Builder(ctx)
            .setTitle("Payment Profile & Support ID")
            .setView(layout)
            .setPositiveButton("Save Profile") { _, _ ->
                prefs.edit()
                    .putString("owner_upi_id", etUpi.text.toString().trim().ifEmpty { DEFAULT_UPI_ID })
                    .putString("owner_global_pay_url", etGlobal.text.toString().trim().ifEmpty { DEFAULT_GLOBAL_PAY_URL })
                    .putString("owner_support_email", etEmail.text.toString().trim().ifEmpty { DEFAULT_SUPPORT_EMAIL })
                    .apply()
                refreshAllSummaries()
                Toast.makeText(ctx, "Payment Profile Updated Successfully!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showVideoPickerForMp3Conversion() {
        val ctx = context ?: return
        thread {
            val all = DeepMediaScanner.scanAllVideosEverywhere(ctx).take(40)
            val videoTitles = all.map { it.title }
            val videoPaths = all.map { it.path }

            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                if (videoPaths.isEmpty()) {
                    try {
                        startActivity(Intent(ctx, Mp3ConverterActivity::class.java))
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "No videos found to convert", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    showDarkStyledDialog(
                        "Select Video to Convert to MP3",
                        videoTitles.toTypedArray()
                    ) { which ->
                        try {
                            val intent = Intent(ctx, Mp3ConverterActivity::class.java).apply {
                                putExtra("VIDEO_PATH", videoPaths[which])
                                putExtra("VIDEO_TITLE", videoTitles[which])
                            }
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "Unable to open MP3 Converter", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun quickPickAndShareMedia(isVideo: Boolean) {
        val ctx = context ?: return
        thread {
            val names = mutableListOf<String>()
            val uris = mutableListOf<Uri>()
            try {
                val contentUri = if (isVideo) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                val proj = arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME
                )
                ctx.contentResolver.query(
                    contentUri,
                    proj,
                    null,
                    null,
                    "${MediaStore.MediaColumns.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idC = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                    val nameC = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    while (cursor.moveToNext() && uris.size < 35) {
                        val id = if (idC != -1) cursor.getLong(idC) else 0L
                        val name = if (nameC != -1) cursor.getString(nameC) ?: "File" else "File"
                        names.add(name)
                        uris.add(ContentUris.withAppendedId(contentUri, id))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                if (uris.isEmpty()) {
                    Toast.makeText(ctx, "No media files found to share", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                showDarkStyledDialog(
                    if (isVideo) "Select Video to Share" else "Select Song to Share",
                    names.toTypedArray()
                ) { idx ->
                    try {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = if (isVideo) "video/*" else "audio/*"
                            putExtra(Intent.EXTRA_STREAM, uris[idx])
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(shareIntent, "Send File via"))
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Unable to share file", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showVideoPlayerControlsDialog() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("MAX_LAYOUT_PREFS", Context.MODE_PRIVATE)
        val dockSide = prefs.getBoolean("player_dock_side_to_bottom", false)
        val showSide = prefs.getBoolean("player_show_side_controls", true)
        val showHdrCc = prefs.getBoolean("player_show_hdr_cc", true)
        val holdSpeed = prefs.getFloat("player_long_press_speed", 4.0f)

        val options = arrayOf(
            "Press & Hold Fast Speed: Currently ${holdSpeed}x (Tap to toggle 4x / 6x / 8x)",
            if (dockSide) "Side Buttons Position: Bottom Bar (Tap for Left/Right)"
            else "Side Buttons Position: Left & Right (Tap for Bottom Bar)",
            if (showSide) "Floating Side Controls: Visible [ON]" else "Floating Side Controls: Hidden",
            if (showHdrCc) "Top HDR & CC Badges: Visible [ON]" else "Top HDR & CC Badges: Hidden",
            "Reset All Video Player Controls to Default"
        )

        showDarkStyledDialog("Video Player Controls", options) { which ->
            when (which) {
                0 -> {
                    val nextSpeed = when (holdSpeed) {
                        4.0f -> 6.0f
                        6.0f -> 8.0f
                        else -> 4.0f
                    }
                    prefs.edit().putFloat("player_long_press_speed", nextSpeed).apply()
                    Toast.makeText(ctx, "Press & Hold Speed set to ${nextSpeed}x", Toast.LENGTH_SHORT).show()
                }
                1 -> prefs.edit().putBoolean("player_dock_side_to_bottom", !dockSide).apply()
                2 -> prefs.edit().putBoolean("player_show_side_controls", !showSide).apply()
                3 -> prefs.edit().putBoolean("player_show_hdr_cc", !showHdrCc).apply()
                4 -> {
                    prefs.edit()
                        .putBoolean("player_dock_side_to_bottom", false)
                        .putBoolean("player_show_side_controls", true)
                        .putBoolean("player_show_hdr_cc", true)
                        .putFloat("player_long_press_speed", 4.0f)
                        .apply()
                    Toast.makeText(ctx, "Video Player Controls Reset!", Toast.LENGTH_SHORT).show()
                }
            }
            refreshAllSummaries()
        }
    }

    private fun showAudioPlayerControlsDialog() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
        val shakeOn = prefs.getBoolean("double_shake_enabled", false)
        val squareBox = prefs.getBoolean("square_album_style", false)

        val options = arrayOf(
            if (shakeOn) "Pocket Double-Shake Next Song: ON"
            else "Pocket Double-Shake Next Song: OFF (Default)",
            if (squareBox) "Player Center Style: Square Album Art (Tap for Rotating Vinyl)"
            else "Player Center Style: Rotating Vinyl Disc (Tap for Square Box)",
            "Open OG Sound & Pitch Lab (Lofi / Slowed + Reverb)",
            "Open 5-Band Dolby Equalizer"
        )

        showDarkStyledDialog("Music Player Controls", options) { which ->
            when (which) {
                0 -> {
                    prefs.edit().putBoolean("double_shake_enabled", !shakeOn).apply()
                    Toast.makeText(
                        ctx,
                        if (!shakeOn) "Pocket Double-Shake Enabled" else "Pocket Double-Shake Disabled",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                1 -> {
                    prefs.edit().putBoolean("square_album_style", !squareBox).apply()
                    Toast.makeText(ctx, "Music Player Center Style Updated!", Toast.LENGTH_SHORT).show()
                }
                2 -> AudioPitchHelper.showSoundAndPitchLabDialog(ctx, null)
                3 -> EqualizerHelper.showEqualizerDialog(ctx, AudioPlayerActivity.getSharedAudioSessionId())
            }
            refreshAllSummaries()
        }
    }

    private fun showFiveStarRatingDialog() {
        val ctx = context ?: return
        val ratingOptions = arrayOf(
            "5 Stars (Excellent - Best 8K Media Player!)",
            "4 Stars (Very Good)",
            "3 Stars (Good, Needs Improvement)",
            "Rate & Review Directly on Google Play Store"
        )
        showDarkStyledDialog("Rate OG 8K Player", ratingOptions) { _ ->
            Toast.makeText(ctx, "Thank you for rating us! Opening Play Store...", Toast.LENGTH_SHORT).show()
            openPlayStoreListing()
        }
    }

    private fun openPlayStoreListing() {
        val ctx = context ?: return
        val pkg = ctx.packageName
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
        } catch (e: Exception) {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$pkg")
                )
            )
        }
    }

    private fun showBugReportAndComplaintDialog() {
        val ctx = context ?: return
        val bugCategories = arrayOf(
            "Video Player Playback / Codec Issue",
            "Music Player / Background Audio / Shake Issue",
            "Volume / Equalizer / Sound Issue",
            "VIP Payment / Activation Issue",
            "Write Custom Bug Report or Complaint",
            "Submit Feedback on Google Play Store"
        )
        showDarkStyledDialog("Report a Bug / Complaint", bugCategories) { which ->
            if (which == 5) {
                openPlayStoreListing()
            } else {
                promptBugDescriptionAndSend(bugCategories[which])
            }
        }
    }

    private fun promptBugDescriptionAndSend(topic: String) {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        val supportEmail = prefs.getString("owner_support_email", DEFAULT_SUPPORT_EMAIL) ?: DEFAULT_SUPPORT_EMAIL

        val input = EditText(ctx).apply {
            hint = "Describe the bug or issue in detail..."
            setTextColor(Color.BLACK)
            minLines = 3
            setPadding(40, 30, 40, 30)
        }

        AlertDialog.Builder(ctx)
            .setTitle("Send Bug Report")
            .setMessage("Issue Type: $topic\n\nDevice info (${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}) will be attached automatically:")
            .setView(input)
            .setPositiveButton("Send Report") { _, _ ->
                val userMsg = input.text.toString().trim().ifEmpty { "Bug reported by user." }
                val body = "Issue Category: $topic\n" +
                    "Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})\n" +
                    "App Version: OG 8K Player v3.0\n\n" +
                    "User Description:\n$userMsg"
                try {
                    val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:$supportEmail")
                        putExtra(Intent.EXTRA_SUBJECT, "OG 8K Player Bug Report: $topic")
                        putExtra(Intent.EXTRA_TEXT, body)
                    }
                    startActivity(Intent.createChooser(emailIntent, "Send Bug Report via"))
                } catch (e: Exception) {
                    Toast.makeText(ctx, "Bug Report Logged! Thank you for your feedback.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shareAppWithFriends() {
        val ctx = context ?: return
        val pkg = ctx.packageName
        val shareText = "Download OG 8K Player - Crystal 8K Ultra HD Video Player, Shorts Reels Player & Dolby Lofi Music Player!\n\n" +
            "https://play.google.com/store/apps/details?id=$pkg"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(shareIntent, "Share OG 8K Player"))
    }

    private fun showAboutAppDialog() {
        val ctx = context ?: return
        AlertDialog.Builder(ctx)
            .setTitle("About OG 8K Player")
            .setMessage(
                "OG 8K Player v3.0 (Crystal Pro Edition)\n\n" +
                    "- 4K/8K Hardware & Software Decoder Engine\n" +
                    "- Direction-Locked 300% Volume & Slow Precision Seek\n" +
                    "- Vertical Swipe Shorts, Reels & WhatsApp Status Saver\n" +
                    "- Persistent Background Audio & Bluetooth 1-Click Control\n" +
                    "- Lofi Pitch Shifter, Duplicate Cleaner & Dual-PIN Fake Vault"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showDarkStyledDialog(
        titleText: String,
        items: Array<String>,
        onItemSelected: (Int) -> Unit
    ) {
        val ctx = context ?: return
        val d = resources.displayMetrics.density
        val rootCard = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * d).toInt(), (18 * d).toInt(), (18 * d).toInt(), (14 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#13263B"))
                cornerRadius = 22f * d
                setStroke((1.5f * d).toInt(), Color.parseColor("#5CE1E6"))
            }
        }

        val tvHeader = TextView(ctx).apply {
            text = titleText
            setTextColor(Color.parseColor("#5CE1E6"))
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((12 * d).toInt(), (4 * d).toInt(), (12 * d).toInt(), (12 * d).toInt())
        }
        rootCard.addView(tvHeader)

        val topLine = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (1 * d).toInt()
            )
            setBackgroundColor(Color.parseColor("#26FFFFFF"))
        }
        rootCard.addView(topLine)

        val scrollView = ScrollView(ctx)
        val listContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }

        val dialog = AlertDialog.Builder(ctx)
            .setView(rootCard)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        items.forEachIndexed { idx, label ->
            val tvItem = TextView(ctx).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 14.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER_VERTICAL
                setPadding((12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt())
                setOnClickListener {
                    dialog.dismiss()
                    onItemSelected(idx)
                }
            }
            listContainer.addView(tvItem)
            if (idx < items.size - 1) {
                val sep = View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (1 * d).toInt()
                    )
                    setBackgroundColor(Color.parseColor("#15FFFFFF"))
                }
                listContainer.addView(sep)
            }
        }

        scrollView.addView(listContainer)
        rootCard.addView(scrollView)
        dialog.show()
    }
}
