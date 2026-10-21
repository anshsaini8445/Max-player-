package com.app.mediaplayer

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
import android.media.audiofx.AudioEffect
import android.net.Uri
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
import kotlin.concurrent.thread

class MeFragment : Fragment() {

    companion object {
        private const val DEFAULT_UPI_ID = "anshsaini@upi"
        private const val DEFAULT_GLOBAL_PAY_URL = "https://paypal.me/maxplayerpro"
        private const val DEFAULT_SUPPORT_EMAIL = "support.ogplayer@gmail.com"
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_me, container, false)
        bindViews(view)
        applyCurrentThemeToMeScreen()
        refreshAllSummaries()
        setupAllClicks()
        return view
    }

    override fun onResume() {
        super.onResume()
        applyCurrentThemeToMeScreen()
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

    private fun applyCurrentThemeToMeScreen() {
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

        applyCurrentThemeToMeScreen()
        (activity as? MainActivity)?.applySavedThemeColors()
        Toast.makeText(ctx, "Theme Applied: $themeName", Toast.LENGTH_SHORT).show()
    }

    private fun showThemeCombinationPickerDialog() {
        val themeTitles = arrayOf(
            "1. OG Crystal Ice Blue (Official Icon Theme)",
            "2. OG Aqua Frosted Glass (Cool Ocean Blue)",
            "3. Cyber Neon Dark (Pitch Black & Cyan)",
            "4. Royal Amethyst 8K (Deep Purple & Neon Lavender)",
            "5. Emerald Matrix Pro (Dark Forest & Mint Glow)"
        )

        showDarkStyledDialog("Select OG Player Theme Combination", themeTitles) { which ->
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
            }
        }
    }

    private fun refreshAllSummaries() {
        val ctx = context ?: return
        val mainPrefs = ctx.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        val layoutPrefs = ctx.getSharedPreferences("MAX_LAYOUT_PREFS", Context.MODE_PRIVATE)
        val audioPrefs = ctx.getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)

        val isVipUnlocked = mainPrefs.getBoolean("is_vip_lifetime_unlocked", false)
        val savedUpi = mainPrefs.getString("owner_upi_id", DEFAULT_UPI_ID) ?: DEFAULT_UPI_ID

        if (isVipUnlocked) {
            tvVipBadgeTop.text = "VIP ACTIVE ✓"
            tvVipTitle.text = "OG Player Lifetime VIP"
            tvVipSubtitle.text = "All 8K Ultra HD, MP3 & Vault Features Unlocked"
            btnUpgradeVip.text = "SUPPORT / PAY"
        } else {
            tvVipBadgeTop.text = "GET VIP"
            tvVipTitle.text = "Upgrade to OG Player VIP"
            tvVipSubtitle.text = "Pay from anywhere in the world to unlock VIP"
            btnUpgradeVip.text = "GET VIP / PAY"
        }
        tvPaymentIdDisplay.text = "UPI ID: $savedUpi • Global PayPal"

        val holdSpeed = layoutPrefs.getFloat("player_long_press_speed", 4.0f)
        val dockBottom = layoutPrefs.getBoolean("player_dock_side_to_bottom", false)
        val showHdrCc = layoutPrefs.getBoolean("player_show_hdr_cc", true)
        tvVideoControlsSummary.text =
            "Hold Speed: ${holdSpeed}x • ${if (dockBottom) "Bottom Bar" else "Side Icons"} • HDR: ${if (showHdrCc) "ON" else "OFF"}"

        val shakeOn = audioPrefs.getBoolean("double_shake_enabled", false)
        val squareBox = audioPrefs.getBoolean("square_album_style", false)
        tvAudioControlsSummary.text =
            "Pocket 2x Shake: ${if (shakeOn) "ON" else "OFF"} • Style: ${if (squareBox) "Album Box" else "Rotating Vinyl"}"
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
        cardToolFileTransfer.setOnClickListener { showFileShareAndTransferOptions() }

        cardToolPrivacyVault.setOnClickListener {
            try {
                startActivity(Intent(requireContext(), VaultPinActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Opening Privacy Vault...", Toast.LENGTH_SHORT).show()
            }
        }

        cardToolEqualizer.setOnClickListener { openSystemEqualizer() }
        rowVideoPlayerControls.setOnClickListener { showVideoPlayerControlsDialog() }
        rowAudioPlayerControls.setOnClickListener { showAudioPlayerControlsDialog() }
        rowPaymentSetup.setOnClickListener { showEditPaymentProfileDialog() }
        rowClearWatchHistory.setOnClickListener { clearAllHistoryAndCache() }
        rowRateApp.setOnClickListener { showFiveStarRatingDialog() }
        rowReportBug.setOnClickListener { showBugReportAndComplaintDialog() }
        rowShareApp.setOnClickListener { shareAppWithFriends() }
        rowAppInfo.setOnClickListener { showAboutAppDialog() }
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
            val videoTitles = mutableListOf<String>()
            val videoPaths = mutableListOf<String>()
            try {
                val proj = arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.DATA
                )
                ctx.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    proj,
                    null,
                    null,
                    "${MediaStore.Video.Media.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                    val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                    val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)

                    while (cursor.moveToNext() && videoPaths.size < 40) {
                        val id = if (idCol != -1) cursor.getLong(idCol) else 0L
                        val rawPath = if (dataCol != -1) cursor.getString(dataCol) else null
                        val path = if (!rawPath.isNullOrEmpty()) {
                            rawPath
                        } else {
                            ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id).toString()
                        }
                        val name = if (nameCol != -1) cursor.getString(nameCol) ?: File(path).name else File(path).name
                        videoTitles.add(name)
                        videoPaths.add(path)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

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

    private fun showFileShareAndTransferOptions() {
        val ctx = context ?: return
        val options = arrayOf(
            "Open Wi-Fi / Offline File Transfer Hub",
            "Quick Share a Video to Another Phone",
            "Quick Share a Song / MP3 File",
            "Share OG 8K Player APK Link"
        )
        showDarkStyledDialog("Share & File Transfer", options) { which ->
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
                3 -> shareAppWithFriends()
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
            if (showSide) "Floating Side Controls: Visible ✓" else "Floating Side Controls: Hidden",
            if (showHdrCc) "Top HDR & CC Badges: Visible ✓" else "Top HDR & CC Badges: Hidden",
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
            if (shakeOn) "Pocket 2x Double-Shake Next Song: ON ✓"
            else "Pocket 2x Double-Shake Next Song: OFF",
            if (squareBox) "Player Center Style: Square Album Art (Tap for Rotating Vinyl)"
            else "Player Center Style: Rotating Vinyl Disc (Tap for Square Box)",
            "Open Dolby Equalizer & Bass Boost",
            "Clear Saved Custom Audio Playlists"
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
                2 -> openSystemEqualizer()
                3 -> {
                    prefs.edit().remove("custom_audio_playlists").apply()
                    Toast.makeText(ctx, "Custom Playlists Reset", Toast.LENGTH_SHORT).show()
                }
            }
            refreshAllSummaries()
        }
    }

    private fun openSystemEqualizer() {
        val ctx = context ?: return
        try {
            val eqIntent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, ctx.packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            }
            if (eqIntent.resolveActivity(ctx.packageManager) != null) {
                startActivity(eqIntent)
            } else {
                EqualizerHelper.showEqualizerDialog(ctx, AudioPlayerActivity.getSharedAudioSessionId())
            }
        } catch (e: Exception) {
            EqualizerHelper.showEqualizerDialog(ctx, AudioPlayerActivity.getSharedAudioSessionId())
        }
    }

    private fun clearAllHistoryAndCache() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (key in prefs.all.keys) {
            if (key.startsWith("history_time_") || key.startsWith("watched_pos_")) {
                editor.remove(key)
            }
        }
        editor.apply()
        Toast.makeText(ctx, "Watch History & Temp Cache Cleared!", Toast.LENGTH_SHORT).show()
    }

    private fun showFiveStarRatingDialog() {
        val ctx = context ?: return
        val ratingOptions = arrayOf(
            "★★★★★ 5 Stars (Excellent - Best 8K Media Player!)",
            "★★★★☆ 4 Stars (Very Good)",
            "★★★☆☆ 3 Stars (Good, Needs Improvement)",
            "Rate & Review Directly on Google Play Store"
        )
        showDarkStyledDialog("Rate OG 8K Player", ratingOptions) { which ->
            when (which) {
                0, 1, 2 -> {
                    Toast.makeText(ctx, "Thank you for rating us! Opening Play Store...", Toast.LENGTH_SHORT).show()
                    openPlayStoreListing()
                }
                3 -> openPlayStoreListing()
            }
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
                val selectedTopic = bugCategories[which]
                promptBugDescriptionAndSend(selectedTopic)
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
        val shareText = "Download OG 8K Player - Crystal 8K Ultra HD Video Player (600% Zoom, 8x Speed) & Dolby Music Player!\n\n" +
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
                "OG 8K Player v3.0 (Crystal Edition)\n\n" +
                    "• 4K/8K Hardware Accelerated Video Engine\n" +
                    "• 600% Pinch-to-Zoom & Up to 8.0x Playback Speed\n" +
                    "• Rotating Vinyl Audio Player & Slide-Out Queue\n" +
                    "• Pocket Double-Shake Next Track Sensor\n" +
                    "• 5 Crystal & Neon Full-App Theme Combinations\n" +
                    "• Global VIP Support (UPI & Worldwide PayPal)"
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
