package com.app.mediaplayer

import android.app.AlertDialog
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
        private const val SUPPORT_EMAIL = "support.maxplayerpro@gmail.com"
    }

    private lateinit var cardProfileBanner: CardView
    private lateinit var tvDeviceMediaStats: TextView

    private lateinit var cardToolMp3Converter: CardView
    private lateinit var cardToolFileTransfer: CardView
    private lateinit var cardToolPrivacyVault: CardView
    private lateinit var cardToolEqualizer: CardView

    private lateinit var rowVideoPlayerControls: LinearLayout
    private lateinit var tvVideoControlsSummary: TextView
    private lateinit var rowAudioPlayerControls: LinearLayout
    private lateinit var tvAudioControlsSummary: TextView
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
        refreshAllSummaries()
        loadDeviceMediaCountsAsync()
        setupAllClicks()
        return view
    }

    override fun onResume() {
        super.onResume()
        refreshAllSummaries()
        loadDeviceMediaCountsAsync()
    }

    private fun bindViews(view: View) {
        cardProfileBanner = view.findViewById(R.id.cardProfileBanner)
        tvDeviceMediaStats = view.findViewById(R.id.tvDeviceMediaStats)

        cardToolMp3Converter = view.findViewById(R.id.cardToolMp3Converter)
        cardToolFileTransfer = view.findViewById(R.id.cardToolFileTransfer)
        cardToolPrivacyVault = view.findViewById(R.id.cardToolPrivacyVault)
        cardToolEqualizer = view.findViewById(R.id.cardToolEqualizer)

        rowVideoPlayerControls = view.findViewById(R.id.rowVideoPlayerControls)
        tvVideoControlsSummary = view.findViewById(R.id.tvVideoControlsSummary)
        rowAudioPlayerControls = view.findViewById(R.id.rowAudioPlayerControls)
        tvAudioControlsSummary = view.findViewById(R.id.tvAudioControlsSummary)
        rowClearWatchHistory = view.findViewById(R.id.rowClearWatchHistory)

        rowRateApp = view.findViewById(R.id.rowRateApp)
        rowReportBug = view.findViewById(R.id.rowReportBug)
        rowShareApp = view.findViewById(R.id.rowShareApp)
        rowAppInfo = view.findViewById(R.id.rowAppInfo)
    }

    private fun refreshAllSummaries() {
        val ctx = context ?: return
        val layoutPrefs = ctx.getSharedPreferences("MAX_LAYOUT_PREFS", Context.MODE_PRIVATE)
        val audioPrefs = ctx.getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)

        // 1. Video Player Summary
        val holdSpeed = layoutPrefs.getFloat("player_long_press_speed", 4.0f)
        val dockBottom = layoutPrefs.getBoolean("player_dock_side_to_bottom", false)
        val showHdrCc = layoutPrefs.getBoolean("player_show_hdr_cc", true)
        tvVideoControlsSummary.text =
            "Hold Speed: ${holdSpeed}x • ${if (dockBottom) "Bottom Bar" else "Side Icons"} • HDR: ${if (showHdrCc) "ON" else "OFF"}"

        // 2. Music Player Summary
        val shakeOn = audioPrefs.getBoolean("double_shake_enabled", true)
        val squareBox = audioPrefs.getBoolean("square_album_style", false)
        tvAudioControlsSummary.text =
            "Pocket 2x Shake: ${if (shakeOn) "ON" else "OFF"} • Style: ${if (squareBox) "Album Box" else "Rotating Vinyl"}"
    }

    private fun loadDeviceMediaCountsAsync() {
        val ctx = context ?: return
        thread {
            var videoCount = 0
            var audioCount = 0
            try {
                ctx.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Video.Media._ID),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    videoCount = cursor.count
                }
                ctx.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Audio.Media._ID),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    audioCount = cursor.count
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                tvDeviceMediaStats.text = "Library: $videoCount Videos • $audioCount Songs (All Pro Features Free)"
            }
        }
    }

    private fun setupAllClicks() {
        cardProfileBanner.setOnClickListener {
            showAboutAppDialog()
        }

        // 1. Convert to MP3 Tool
        cardToolMp3Converter.setOnClickListener {
            showVideoPickerForMp3Conversion()
        }

        // 2. File Share & Transfer
        cardToolFileTransfer.setOnClickListener {
            showFileShareAndTransferOptions()
        }

        // 3. Privacy Vault (Secret Box)
        cardToolPrivacyVault.setOnClickListener {
            try {
                startActivity(Intent(requireContext(), VaultPinActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Opening Privacy Vault...", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. Dolby Equalizer & Bass Boost
        cardToolEqualizer.setOnClickListener {
            openSystemEqualizer()
        }

        // 5. Video Player Controls
        rowVideoPlayerControls.setOnClickListener {
            showVideoPlayerControlsDialog()
        }

        // 6. Audio Player Controls
        rowAudioPlayerControls.setOnClickListener {
            showAudioPlayerControlsDialog()
        }

        // 7. Clear Playback History
        rowClearWatchHistory.setOnClickListener {
            clearAllHistoryAndCache()
        }

        // 8. Rate 5 Stars
        rowRateApp.setOnClickListener {
            showFiveStarRatingDialog()
        }

        // 9. Report a Bug / Complaint
        rowReportBug.setOnClickListener {
            showBugReportAndComplaintDialog()
        }

        // 10. Share App
        rowShareApp.setOnClickListener {
            shareAppWithFriends()
        }

        // 11. App Info
        rowAppInfo.setOnClickListener {
            showAboutAppDialog()
        }
    }

    // VIDEO TO MP3 PICKER & CONVERTER LAUNCHER
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
                        videoTitles.add("🎵 $name")
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
                                putExtra("VIDEO_TITLE", videoTitles[which].removePrefix("🎵 "))
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

    // PLAYIT STYLE FILE SHARE & TRANSFER
    private fun showFileShareAndTransferOptions() {
        val ctx = context ?: return
        val options = arrayOf(
            "📡 Open Wi-Fi / Offline File Transfer Hub",
            "🎬 Quick Share a Video to Another Phone",
            "🎵 Quick Share a Song / MP3 File",
            "📲 Share MAX Player Pro App Link"
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

    // VIDEO PLAYER CONTROLS DIALOG
    private fun showVideoPlayerControlsDialog() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("MAX_LAYOUT_PREFS", Context.MODE_PRIVATE)
        val dockSide = prefs.getBoolean("player_dock_side_to_bottom", false)
        val showSide = prefs.getBoolean("player_show_side_controls", true)
        val showHdrCc = prefs.getBoolean("player_show_hdr_cc", true)
        val holdSpeed = prefs.getFloat("player_long_press_speed", 4.0f)

        val options = arrayOf(
            "⚡ Press & Hold Fast Speed: Currently ${holdSpeed}x (Tap to toggle 4x / 6x / 8x)",
            if (dockSide) "📌 Side Buttons Position: Bottom Bar (Tap for Left/Right)" else "📌 Side Buttons Position: Left & Right (Tap for Bottom Bar)",
            if (showSide) "👁️ Floating Side Controls: Visible ✓" else "👁️ Floating Side Controls: Hidden",
            if (showHdrCc) "✨ Top HDR & CC Badges: Visible ✓" else "✨ Top HDR & CC Badges: Hidden",
            "🔄 Reset All Video Player Controls to Default"
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

    // MUSIC / AUDIO PLAYER CONTROLS DIALOG
    private fun showAudioPlayerControlsDialog() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("MAX_AUDIO_PREFS", Context.MODE_PRIVATE)
        val shakeOn = prefs.getBoolean("double_shake_enabled", true)
        val squareBox = prefs.getBoolean("square_album_style", false)

        val options = arrayOf(
            if (shakeOn) "📳 Pocket 2x Double-Shake Next Song: ON ✓" else "📳 Pocket 2x Double-Shake Next Song: OFF",
            if (squareBox) "💿 Player Center Style: Square Album Art (Tap for Rotating Vinyl)" else "💿 Player Center Style: Rotating Vinyl Disc (Tap for Square Box)",
            "🎛️ Open Dolby Equalizer & Bass Boost",
            "🗑️ Clear Saved Custom Audio Playlists"
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
                Toast.makeText(ctx, "Built-in Dolby Bass Boost Active in Audio & Video Player!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(ctx, "Built-in Dolby Bass Boost Active!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearAllHistoryAndCache() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (key in prefs.all.keys) {
            if (key.startsWith("history_time_")) {
                editor.remove(key)
            }
        }
        editor.apply()
        Toast.makeText(ctx, "Watch History & Temp Cache Cleared!", Toast.LENGTH_SHORT).show()
    }

    // 5-STAR RATING SYSTEM (In-App + Google Play Store Redirect)
    private fun showFiveStarRatingDialog() {
        val ctx = context ?: return
        val ratingOptions = arrayOf(
            "★★★★★  5 Stars (Excellent - Best Media Player!)",
            "★★★★☆  4 Stars (Very Good)",
            "★★★☆☆  3 Stars (Good, Needs Improvement)",
            "🌐 Rate & Review Directly on Google Play Store"
        )

        showDarkStyledDialog("Rate MAX Player Pro", ratingOptions) { which ->
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

    // BUG REPORT & PLAY STORE COMPLAINT SYSTEM
    private fun showBugReportAndComplaintDialog() {
        val ctx = context ?: return
        val bugCategories = arrayOf(
            "🎬 Video Player Playback / Codec Issue",
            "🎵 Music Player / Background Audio / Shake Issue",
            "🔊 Volume / Equalizer / Sound Issue",
            "📂 File Transfer / MP3 Converter / Vault Issue",
            "📝 Write Custom Bug Report or Suggestion",
            "🏪 Submit Feedback on Google Play Store"
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
                    "App Version: MAX Player Pro v2.5.0\n\n" +
                    "User Description:\n$userMsg"

                try {
                    val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:$SUPPORT_EMAIL")
                        putExtra(Intent.EXTRA_SUBJECT, "MAX Player Pro Bug Report: $topic")
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
        val shareText = "Download MAX Player Pro - 4K Ultra HD Video Player (600% Zoom, 8x Speed) & Dolby Music Player with Pocket Shake!\n\n" +
            "https://play.google.com/store/apps/details?id=$pkg"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(shareIntent, "Share MAX Player Pro"))
    }

    private fun showAboutAppDialog() {
        val ctx = context ?: return
        AlertDialog.Builder(ctx)
            .setTitle("About MAX Player Pro")
            .setMessage(
                "MAX Player Pro v2.5.0 (100% Free Pro Edition)\n\n" +
                    "• 4K/8K Hardware Accelerated Video Engine\n" +
                    "• 600% Pinch-to-Zoom & Up to 8.0x Playback Speed\n" +
                    "• Straight Waveform Audio Player & Slide-Out Queue\n" +
                    "• Pocket Double-Shake Next Track Sensor\n" +
                    "• Built-in Video to MP3 Converter & Privacy Vault"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    // PREMIUM DARK CARD DIALOG WITH PURE WHITE TEXT
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
                setColor(Color.parseColor("#141422"))
                cornerRadius = 22f * d
                setStroke((1 * d).toInt(), Color.parseColor("#3300FFFF"))
            }
        }

        val tvHeader = TextView(ctx).apply {
            text = titleText
            setTextColor(Color.parseColor("#00FFFF"))
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
