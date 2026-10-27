package com.app.mediaplayer

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
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
object PlayerThreeDotMenuHelper {

    interface PlayerMenuCallbacks {
        fun getExoPlayer(): ExoPlayer?
        fun getVideoPath(): String
        fun getVideoTitle(): String
        fun getCurrentPositionMs(): Long
        fun getDurationMs(): Long
        fun seekToPositionMs(posMs: Long)
        fun closeAllPanels()
        fun onFavoriteToggled(isFav: Boolean)
        fun onVrModeToggled()
        fun onAbRepeatClicked()
        fun onNightModeToggled()
        fun onMirrorModeToggled()
        fun onOpenSleepTimer()
        fun onLoopModeSelected(mode: Int)
        fun onBrightnessChanged(percent: Float)
        fun onDecoderModeChanged(stage: Int)
        fun onLayoutPreferencesChanged()
        fun onResetVideoZoom()
        fun onDeleteCurrentVideoPermanently()
    }

    fun makeFrostedCircle(d: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#2E10141E"))
            setStroke((1.3f * d).toInt(), Color.parseColor("#4DFFFFFF"))
        }
    }

    fun makeFrostedPill(d: Float): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = 14f * d
            setColor(Color.parseColor("#2E10141E"))
            setStroke((1.3f * d).toInt(), Color.parseColor("#4DFFFFFF"))
        }
    }

    fun styleAndBindThreeDotMenu(
        context: Context,
        layoutAdvancedSettings: ScrollView,
        imgFavoriteIcon: ImageView,
        btnLoopShuffle: TextView,
        btnLoopOne: TextView,
        btnLoopAll: TextView,
        btnLoopOff: TextView,
        btnHwDecoder: TextView,
        btnSwDecoder: TextView,
        seekBarBrightness: SeekBar,
        callbacks: PlayerMenuCallbacks
    ) {
        val d = context.resources.displayMetrics.density

        // Elevate and isolate touch events so ScrollView scrolls smoothly and children receive all clicks
        layoutAdvancedSettings.isClickable = true
        layoutAdvancedSettings.isFocusable = true
        layoutAdvancedSettings.isFocusableInTouchMode = true
        layoutAdvancedSettings.isFillViewport = true
        layoutAdvancedSettings.isSmoothScrollingEnabled = true
        layoutAdvancedSettings.isVerticalScrollBarEnabled = true
        layoutAdvancedSettings.elevation = 42f * d
        layoutAdvancedSettings.translationZ = 42f * d
        layoutAdvancedSettings.background = GradientDrawable().apply {
            setColor(Color.parseColor("#F50A0A10"))
            cornerRadius = 24f * d
            setStroke((1.5f * d).toInt(), Color.parseColor("#44FFFFFF"))
        }

        layoutAdvancedSettings.setOnTouchListener { v, event ->
            v.parent?.requestDisallowInterceptTouchEvent(true)
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                v.performClick()
            }
            false
        }

        (layoutAdvancedSettings.getChildAt(0) as? LinearLayout)?.let { innerCol ->
            innerCol.isClickable = false
            innerCol.setPadding(
                innerCol.paddingLeft,
                innerCol.paddingTop,
                innerCol.paddingRight,
                (32 * d).toInt()
            )
        }

        layoutAdvancedSettings.findViewById<TextView>(R.id.btnCloseSettings)?.apply {
            text = "X"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#28FFFFFF"))
                setStroke((1.2f * d).toInt(), Color.WHITE)
            }
            setOnClickListener { callbacks.closeAllPanels() }
        }

        insertTopQuickCardsInThreeDotMenu(context, layoutAdvancedSettings, callbacks)

        val tileSpecs = listOf(
            R.id.optAudioTrack to "AUDIO_WAVE",
            R.id.optEqualizer to "EQ_SLIDERS",
            R.id.optCast to "CAST_TV",
            R.id.optShare to "SHARE_NODES",
            R.id.optCut to "MP3_DISC",
            R.id.optFavorite to "STAR_FAV",
            R.id.optBookmark to "BOOKMARK",
            R.id.optVrMode to "VR_GLASSES",
            R.id.optAbRepeat to "AB_LOOP",
            R.id.optNightMode to "MOON_NIGHT",
            R.id.optMirrorMode to "MIRROR_FLIP",
            R.id.optSleepTimer to "TIMER_CLOCK",
            R.id.optEditPlayer to "EDIT_GRID",
            R.id.optTutorials to "GESTURE_HAND",
            R.id.optFileInfo to "DELETE_TRASH",
            R.id.optFeedback to "SOUND_LAB"
        )

        for ((viewId, iconType) in tileSpecs) {
            val tile = layoutAdvancedSettings.findViewById<LinearLayout>(viewId) ?: continue
            tile.isClickable = true
            tile.isFocusable = true
            tile.background = GradientDrawable().apply {
                setColor(Color.parseColor("#18FFFFFF"))
                cornerRadius = 16f * d
                setStroke((1 * d).toInt(), Color.parseColor("#33FFFFFF"))
            }
            tile.setPadding((6 * d).toInt(), (10 * d).toInt(), (6 * d).toInt(), (10 * d).toInt())

            for (i in 0 until tile.childCount) {
                val child = tile.getChildAt(i)
                child.isClickable = false
                child.isFocusable = false
                when (child) {
                    is ImageView -> {
                        child.clearColorFilter()
                        child.layoutParams = LinearLayout.LayoutParams((32 * d).toInt(), (32 * d).toInt())
                        child.setImageDrawable(PlayerVectorIconDrawable(iconType, d, Color.WHITE))
                    }
                    is TextView -> {
                        child.setTextColor(Color.WHITE)
                        child.textSize = 12f
                        child.typeface = Typeface.DEFAULT_BOLD
                    }
                }
            }
        }

        imgFavoriteIcon.setImageDrawable(PlayerVectorIconDrawable("STAR_FAV", d, Color.WHITE))

        listOf(btnLoopShuffle, btnLoopOne, btnLoopAll, btnLoopOff, btnHwDecoder, btnSwDecoder).forEach { btn ->
            btn.isClickable = true
            btn.isFocusable = true
            btn.setTextColor(Color.WHITE)
            btn.background = GradientDrawable().apply {
                setColor(Color.parseColor("#18FFFFFF"))
                cornerRadius = 12f * d
                setStroke((1 * d).toInt(), Color.parseColor("#38FFFFFF"))
            }
        }

        layoutAdvancedSettings.findViewById<LinearLayout>(R.id.optAudioTrack)?.setOnClickListener {
            callbacks.closeAllPanels()
            showMultiLanguageAudioTrackDialog(context, callbacks.getExoPlayer())
        }

        layoutAdvancedSettings.findViewById<LinearLayout>(R.id.optEqualizer)?.setOnClickListener {
            callbacks.closeAllPanels()
            val sessionId = callbacks.getExoPlayer()?.audioSessionId ?: 0
            EqualizerHelper.showEqualizerDialog(context, sessionId)
        }

        layoutAdvancedSettings.findViewById<LinearLayout>(R.id.optCast)?.setOnClickListener {
            callbacks.closeAllPanels()
            try {
                context.startActivity(Intent(Settings.ACTION_CAST_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(context, "Cast Settings Unavailable", Toast.LENGTH_SHORT).show()
            }
        }

        layoutAdvancedSettings.findViewById<LinearLayout>(R.id.optShare)?.setOnClickListener {
            callbacks.closeAllPanels()
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/*"
                    putExtra(Intent.EXTRA_STREAM, Uri.parse(callbacks.getVideoPath()))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
            } catch (_: Exception) {
                Toast.makeText(context, "Unable to share file", Toast.LENGTH_SHORT).show()
            }
        }

        layoutAdvancedSettings.findViewById<LinearLayout>(R.id.optCut)?.setOnClickListener {
            callbacks.closeAllPanels()
            Toast.makeText(context, "Extracting audio to MP3...", Toast.LENGTH_SHORT).show()
            Mp3ConverterActivity.convertVideoToAudioInstant(
                context = context,
                videoPath = callbacks.getVideoPath(),
                videoTitle = callbacks.getVideoTitle(),
                onFinished = { success, outPathOrMsg ->
                    Toast.makeText(
                        context,
                        if (success) "Audio Extracted & Added to Music: $outPathOrMsg" else outPathOrMsg,
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }

        layoutAdvancedSettings.findViewById<LinearLayout>(R.id.optFavorite)?.setOnClickListener {
            val prefs = context.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
            val path = callbacks.getVideoPath()
            val nextFav = !prefs.getBoolean("fav_$path", false)
            prefs.edit().putBoolean("fav_$path", nextFav).apply()
            callbacks.onFavoriteToggled(nextFav)
            Toast.makeText(
                context,
                if (nextFav) "Added to Favorites" else "Removed from Favorites",
                Toast.LENGTH_SHORT
            ).show()
        }

        layoutAdvancedSettings.findViewById<LinearLayout>(R.id.optBookmark)?.setOnClickListener {
            callbacks.closeAllPanels()
            val prefs = context.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
            val path = callbacks.getVideoPath()
            val title = callbacks.getVideoTitle()
            val savedBookmark = prefs.getLong("bookmark_$path", -1L)
            val currentPos = callbacks.getCurrentPositionMs()
            val options = if (savedBookmark != -1L) {
                arrayOf(
                    "Save Bookmark at ${formatMs(currentPos)}",
                    "Jump to Saved Bookmark (${formatMs(savedBookmark)})",
                    "Clear Bookmark",
                    "Delete Video Permanently (From Phone)"
                )
            } else {
                arrayOf(
                    "Save Bookmark at ${formatMs(currentPos)}",
                    "Lock Video into Secret Privacy Vault",
                    "Delete Video Permanently (From Phone)"
                )
            }
            VideoFragment.showBrightVideoMenuDialog(context, "Video Bookmarks & Actions", options) { which ->
                if (savedBookmark != -1L) {
                    when (which) {
                        0 -> {
                            prefs.edit().putLong("bookmark_$path", currentPos).apply()
                            Toast.makeText(context, "Bookmark Saved!", Toast.LENGTH_SHORT).show()
                        }
                        1 -> callbacks.seekToPositionMs(savedBookmark)
                        2 -> {
                            prefs.edit().remove("bookmark_$path").apply()
                            Toast.makeText(context, "Bookmark Cleared", Toast.LENGTH_SHORT).show()
                        }
                        3 -> callbacks.onDeleteCurrentVideoPermanently()
                    }
                } else {
                    when (which) {
                        0 -> {
                            prefs.edit().putLong("bookmark_$path", currentPos).apply()
                            Toast.makeText(context, "Bookmark Saved!", Toast.LENGTH_SHORT).show()
                        }
                        1 -> FakeVaultHelper.lockMediaIntoVault(context, path, title)
                        2 -> callbacks.onDeleteCurrentVideoPermanently()
                    }
                }
            }
        }

        layoutAdvancedSettings.findViewById<LinearLayout>(R.id.optVrMode)?.setOnClickListener {
            callbacks.onVrModeToggled()
            callbacks.closeAllPanels()
        }

        layoutAdvancedSettings.findViewById<LinearLayout>(R.id.optAbRepeat)?.setOnClickListener {
            callbacks.onAbRepeatClicked()
        }

        layoutAdvancedSettings.findViewById<LinearLayout>(R.id.optNightMode)?.setOnClickListener {
            callbacks.onNightModeToggled()
            callbacks.closeAllPanels()
        }

        layoutAdvancedSettings.findViewById<LinearLayout>(R.id.optMirrorMode)?.setOnClickListener {
            callbacks.onMirrorModeToggled()
            callbacks.closeAllPanels()
        }

        layoutAdvancedSettings.findViewById<LinearLayout>(R.id.optSleepTimer)?.setOnClickListener {
            callbacks.closeAllPanels()
            callbacks.onOpenSleepTimer()
        }

        btnLoopShuffle.setOnClickListener {
            callbacks.onLoopModeSelected(0)
            Toast.makeText(context, "Shuffle Loop Enabled", Toast.LENGTH_SHORT).show()
        }
        btnLoopOne.setOnClickListener {
            callbacks.onLoopModeSelected(1)
            Toast.makeText(context, "Repeat Current Video Enabled", Toast.LENGTH_SHORT).show()
        }
        btnLoopAll.setOnClickListener {
            callbacks.onLoopModeSelected(2)
            Toast.makeText(context, "Loop All Videos Enabled", Toast.LENGTH_SHORT).show()
        }
        btnLoopOff.setOnClickListener {
            callbacks.onLoopModeSelected(3)
            Toast.makeText(context, "Play Single Video (Stop After Current)", Toast.LENGTH_SHORT).show()
        }

        seekBarBrightness.setOnTouchListener { v, _ ->
            v.parent?.requestDisallowInterceptTouchEvent(true)
            false
        }

        seekBarBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    callbacks.onBrightnessChanged(progress.coerceIn(5, 100).toFloat())
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        btnHwDecoder.setOnClickListener {
            callbacks.closeAllPanels()
            callbacks.onDecoderModeChanged(0)
            Toast.makeText(context, "Active: Hardware (HW+) 8K Engine", Toast.LENGTH_SHORT).show()
        }

        btnSwDecoder.setOnClickListener {
            callbacks.closeAllPanels()
            callbacks.onDecoderModeChanged(1)
            Toast.makeText(context, "Active: Software Compatibility Engine", Toast.LENGTH_SHORT).show()
        }

        layoutAdvancedSettings.findViewById<LinearLayout>(R.id.optEditPlayer)?.setOnClickListener {
            callbacks.closeAllPanels()
            showCustomizePlayerLayoutDialog(context, callbacks)
        }

        layoutAdvancedSettings.findViewById<LinearLayout>(R.id.optTutorials)?.setOnClickListener {
            callbacks.closeAllPanels()
            AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Player Gestures Guide")
                .setMessage(
                    "1. Right Vertical Swipe: 0% to 200% Volume HUD Boost\n" +
                        "2. Left Vertical Swipe: Top Camera-Area Brightness HUD\n" +
                        "3. Horizontal Slide: Fast Movie Frame Seek\n" +
                        "4. Hold & Slide Left/Right: 0.25x to 8.0x Dynamic Speed\n" +
                        "5. Two-Finger Pinch: Full-Screen 50% to 600% Video Zoom"
                )
                .setPositiveButton("Got It", null)
                .show()
        }

        layoutAdvancedSettings.findViewById<LinearLayout>(R.id.optFileInfo)?.setOnClickListener {
            callbacks.closeAllPanels()
            val path = callbacks.getVideoPath()
            val title = callbacks.getVideoTitle()
            val options = arrayOf(
                "Delete Video Permanently from Phone Storage",
                "View Video Resolution & File Path"
            )
            VideoFragment.showBrightVideoMenuDialog(context, title, options) { which ->
                if (which == 0) {
                    callbacks.onDeleteCurrentVideoPermanently()
                } else {
                    val file = File(path)
                    val sizeMb = if (file.exists()) {
                        String.format(Locale.US, "%.2f MB", file.length() / (1024.0 * 1024.0))
                    } else {
                        "Unknown"
                    }
                    val durationStr = formatMs(callbacks.getDurationMs())
                    AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("File Information")
                        .setMessage("Title: $title\n\nPath: $path\n\nSize: $sizeMb\n\nDuration: $durationStr")
                        .setPositiveButton("Close", null)
                        .show()
                }
            }
        }

        layoutAdvancedSettings.findViewById<LinearLayout>(R.id.optFeedback)?.setOnClickListener {
            callbacks.closeAllPanels()
            AudioPitchHelper.showSoundAndPitchLabDialog(context, callbacks.getExoPlayer())
        }
    }

    private fun insertTopQuickCardsInThreeDotMenu(
        context: Context,
        layoutAdvancedSettings: ScrollView,
        callbacks: PlayerMenuCallbacks
    ) {
        try {
            val d = context.resources.displayMetrics.density
            val innerCol = layoutAdvancedSettings.getChildAt(0) as? LinearLayout ?: return
            if (innerCol.findViewWithTag<View>("TOP_QUICK_ROW_TAG") != null) return

            val topActionsRow = LinearLayout(context).apply {
                tag = "TOP_QUICK_ROW_TAG"
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (12 * d).toInt() }
            }

            val langCard = LinearLayout(context).apply {
                isClickable = true
                isFocusable = true
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt())
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.25f).apply {
                    marginEnd = (6 * d).toInt()
                }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#22FFFFFF"))
                    cornerRadius = 16f * d
                    setStroke((1.3f * d).toInt(), Color.WHITE)
                }
                setOnClickListener {
                    callbacks.closeAllPanels()
                    showMultiLanguageAudioTrackDialog(context, callbacks.getExoPlayer())
                }
            }

            val langIcon = ImageView(context).apply {
                isClickable = false
                isFocusable = false
                layoutParams = LinearLayout.LayoutParams((28 * d).toInt(), (28 * d).toInt()).apply {
                    marginEnd = (8 * d).toInt()
                }
                setImageDrawable(PlayerVectorIconDrawable("AUDIO_WAVE", d, Color.WHITE))
            }

            val tvLangLabel = TextView(context).apply {
                isClickable = false
                isFocusable = false
                text = "Audio Track\n(Hindi / English)"
                setTextColor(Color.WHITE)
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
            }
            langCard.addView(langIcon)
            langCard.addView(tvLangLabel)

            val deleteCard = LinearLayout(context).apply {
                isClickable = true
                isFocusable = true
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt())
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (6 * d).toInt()
                }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#22FFFFFF"))
                    cornerRadius = 16f * d
                    setStroke((1.3f * d).toInt(), Color.WHITE)
                }
                setOnClickListener {
                    callbacks.onDeleteCurrentVideoPermanently()
                }
            }

            val delIcon = ImageView(context).apply {
                isClickable = false
                isFocusable = false
                layoutParams = LinearLayout.LayoutParams((28 * d).toInt(), (28 * d).toInt()).apply {
                    marginEnd = (8 * d).toInt()
                }
                setImageDrawable(PlayerVectorIconDrawable("DELETE_TRASH", d, Color.WHITE))
            }

            val tvDelLabel = TextView(context).apply {
                isClickable = false
                isFocusable = false
                text = "Delete Video\n(From Phone)"
                setTextColor(Color.WHITE)
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
            }
            deleteCard.addView(delIcon)
            deleteCard.addView(tvDelLabel)

            topActionsRow.addView(langCard)
            topActionsRow.addView(deleteCard)

            val insertIdx = if (innerCol.childCount > 1) 1 else 0
            innerCol.addView(topActionsRow, insertIdx)
        } catch (_: Exception) {
        }
    }

    fun showMultiLanguageAudioTrackDialog(context: Context, player: ExoPlayer?) {
        if (player == null) {
            Toast.makeText(context, "Default Audio Stream Active", Toast.LENGTH_SHORT).show()
            return
        }
        val audioGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }

        val optionsList = mutableListOf<String>()
        val actionsList = mutableListOf<() -> Unit>()

        if (audioGroups.isNotEmpty()) {
            audioGroups.forEachIndexed { groupIdx, group ->
                for (trackIdx in 0 until group.length) {
                    val fmt = group.getTrackFormat(trackIdx)
                    val rawLang = fmt.language ?: ""
                    val readableLang = formatReadableLanguageName(rawLang)
                    val label = fmt.label ?: "Audio Stream ${groupIdx + 1}"
                    val channels = if (fmt.channelCount >= 6) "5.1 Surround" else "Stereo"
                    val isSelected = group.isTrackSelected(trackIdx)
                    val prefix = if (isSelected) "[Active] " else ""

                    optionsList.add("$prefix$readableLang ($label - $channels)")
                    actionsList.add {
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIdx))
                            .build()
                        Toast.makeText(
                            context,
                            "Switched Video Audio to: $readableLang ($label)",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        val quickPreferredLanguages = listOf(
            "hi" to "Hindi Audio Track (Preferred)",
            "en" to "English Audio Track (Preferred)",
            "ta" to "Tamil Audio Track",
            "te" to "Telugu Audio Track",
            "mr" to "Marathi Audio Track",
            "es" to "Spanish Audio Track",
            "ru" to "Russian Audio Track"
        )

        for ((isoCode, displayTitle) in quickPreferredLanguages) {
            optionsList.add("Force Prefer: $displayTitle")
            actionsList.add {
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    .setPreferredAudioLanguage(isoCode)
                    .build()
                Toast.makeText(
                    context,
                    "Audio Language Preference Set: $displayTitle",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        VideoFragment.showBrightVideoMenuDialog(
            context,
            "Select Video Audio Language (Hindi / English)",
            optionsList.toTypedArray()
        ) { which ->
            if (which in actionsList.indices) {
                actionsList[which].invoke()
            }
        }
    }

    private fun formatReadableLanguageName(isoCode: String): String {
        if (isoCode.isBlank() || isoCode.equals("und", ignoreCase = true)) {
            return "Original / Default Audio"
        }
        val lower = isoCode.lowercase(Locale.US)
        return when {
            lower.startsWith("hi") -> "Hindi"
            lower.startsWith("en") -> "English"
            lower.startsWith("ta") -> "Tamil"
            lower.startsWith("te") -> "Telugu"
            lower.startsWith("kn") -> "Kannada"
            lower.startsWith("ml") -> "Malayalam"
            lower.startsWith("mr") -> "Marathi"
            lower.startsWith("bn") -> "Bengali"
            lower.startsWith("pa") -> "Punjabi"
            lower.startsWith("gu") -> "Gujarati"
            lower.startsWith("ur") -> "Urdu"
            lower.startsWith("es") -> "Spanish"
            lower.startsWith("ru") -> "Russian"
            lower.startsWith("ja") -> "Japanese"
            lower.startsWith("zh") -> "Chinese"
            lower.startsWith("ko") -> "Korean"
            lower.startsWith("fr") -> "French"
            lower.startsWith("de") -> "German"
            else -> Locale.forLanguageTag(isoCode).displayLanguage.ifEmpty { isoCode.uppercase(Locale.US) }
        }
    }

    private fun showCustomizePlayerLayoutDialog(context: Context, callbacks: PlayerMenuCallbacks) {
        val prefs = context.getSharedPreferences("MAX_LAYOUT_PREFS", Context.MODE_PRIVATE)
        val dockSide = prefs.getBoolean("player_dock_side_to_bottom", false)
        val showSide = prefs.getBoolean("player_show_side_controls", true)
        val showHdrCc = prefs.getBoolean("player_show_hdr_cc", true)

        val options = arrayOf(
            if (dockSide) "Move Side Icons Back to Left/Right" else "Move Side Icons to Bottom Bar",
            if (showSide) "Hide Side Controls" else "Show Side Controls",
            if (showHdrCc) "Hide Top HDR & CC Badges" else "Show Top HDR & CC Badges",
            "Reset Player Layout & Zoom (100%)"
        )

        VideoFragment.showBrightVideoMenuDialog(context, "Edit Player Layout", options) { which ->
            when (which) {
                0 -> {
                    prefs.edit().putBoolean("player_dock_side_to_bottom", !dockSide).apply()
                    callbacks.onLayoutPreferencesChanged()
                }
                1 -> {
                    prefs.edit().putBoolean("player_show_side_controls", !showSide).apply()
                    callbacks.onLayoutPreferencesChanged()
                }
                2 -> {
                    prefs.edit().putBoolean("player_show_hdr_cc", !showHdrCc).apply()
                    callbacks.onLayoutPreferencesChanged()
                }
                3 -> {
                    prefs.edit()
                        .putBoolean("player_dock_side_to_bottom", false)
                        .putBoolean("player_show_side_controls", true)
                        .putBoolean("player_show_hdr_cc", true)
                        .apply()
                    callbacks.onResetVideoZoom()
                    callbacks.onLayoutPreferencesChanged()
                }
            }
        }
    }

    private fun formatMs(millis: Long): String {
        val totalSeconds = (millis.coerceAtLeast(0L)) / 1000L
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    class SpeedTickRulerView(
        context: Context,
        private val density: Float,
        private val onSpeedChanged: (Float) -> Unit
    ) : View(context) {

        private var speed = 1.0f

        private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#88FFFFFF")
            strokeWidth = 1.6f * density
            strokeCap = Paint.Cap.ROUND
        }

        private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 2.8f * density
            strokeCap = Paint.Cap.ROUND
        }

        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 10.5f * density
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        fun setSpeedValue(newSpeed: Float) {
            speed = newSpeed.coerceIn(0.25f, 8.0f)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val padX = 16f * density
            val usableW = (w - 2 * padX).coerceAtLeast(1f)
            val totalTicks = 40
            val topY = 8f * density
            val bottomMajor = h - 22f * density
            val bottomMinor = h - 30f * density

            for (i in 0..totalTicks) {
                val frac = i.toFloat() / totalTicks.toFloat()
                val x = padX + frac * usableW
                val isMajor = i % 5 == 0
                canvas.drawLine(
                    x,
                    if (isMajor) topY else topY + 6f * density,
                    x,
                    if (isMajor) bottomMajor else bottomMinor,
                    tickPaint
                )
            }

            val milestones = listOf(
                0.25f to "0.25x",
                1.0f to "1.0x",
                2.0f to "2.0x",
                4.0f to "4.0x",
                8.0f to "8.0x"
            )
            for ((msVal, text) in milestones) {
                val f = ((msVal - 0.25f) / 7.75f).coerceIn(0f, 1f)
                val x = padX + f * usableW
                canvas.drawText(text, x, h - 4f * density, labelPaint)
            }

            val curFrac = ((speed - 0.25f) / 7.75f).coerceIn(0f, 1f)
            val curX = padX + curFrac * usableW
            canvas.drawLine(curX, 2f * density, curX, bottomMajor + 4f * density, activePaint)
            canvas.drawCircle(curX, 5f * density, 4.5f * density, activePaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            parent?.requestDisallowInterceptTouchEvent(true)
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                    val padX = 16f * density
                    val usableW = (width.toFloat() - 2 * padX).coerceAtLeast(1f)
                    val frac = ((event.x - padX) / usableW).coerceIn(0f, 1f)
                    val rawSpeed = 0.25f + frac * 7.75f
                    val rounded = (rawSpeed * 20f).roundToInt() / 20f
                    speed = rounded.coerceIn(0.25f, 8.0f)
                    invalidate()
                    onSpeedChanged(speed)
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }

    class PlayerVectorIconDrawable(
        private val type: String,
        private val density: Float,
        private val iconColor: Int
    ) : Drawable() {

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = iconColor
            style = Paint.Style.STROKE
            strokeWidth = 2.4f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = iconColor
            style = Paint.Style.FILL
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 0f || h <= 0f) return
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val r = w.coerceAtMost(h) * 0.38f
            val path = Path()

            when (type) {
                "BACK_ARROW" -> {
                    canvas.drawLine(cx + r * 0.75f, cy, cx - r * 0.65f, cy, strokePaint)
                    canvas.drawLine(cx - r * 0.65f, cy, cx - r * 0.05f, cy - r * 0.58f, strokePaint)
                    canvas.drawLine(cx - r * 0.65f, cy, cx - r * 0.05f, cy + r * 0.58f, strokePaint)
                }
                "PLAY" -> {
                    path.moveTo(cx - r * 0.35f, cy - r * 0.58f)
                    path.lineTo(cx + r * 0.62f, cy)
                    path.lineTo(cx - r * 0.35f, cy + r * 0.58f)
                    path.close()
                    canvas.drawPath(path, fillPaint)
                }
                "PAUSE" -> {
                    val bw = r * 0.32f
                    val bh = r * 1.10f
                    canvas.drawRoundRect(
                        RectF(cx - r * 0.48f, cy - bh / 2f, cx - r * 0.48f + bw, cy + bh / 2f),
                        2.5f * density,
                        2.5f * density,
                        fillPaint
                    )
                    canvas.drawRoundRect(
                        RectF(cx + r * 0.16f, cy - bh / 2f, cx + r * 0.16f + bw, cy + bh / 2f),
                        2.5f * density,
                        2.5f * density,
                        fillPaint
                    )
                }
                "PREV" -> {
                    canvas.drawLine(cx - r * 0.55f, cy - r * 0.52f, cx - r * 0.55f, cy + r * 0.52f, strokePaint)
                    path.moveTo(cx + r * 0.55f, cy - r * 0.52f)
                    path.lineTo(cx - r * 0.38f, cy)
                    path.lineTo(cx + r * 0.55f, cy + r * 0.52f)
                    path.close()
                    canvas.drawPath(path, fillPaint)
                }
                "NEXT" -> {
                    canvas.drawLine(cx + r * 0.55f, cy - r * 0.52f, cx + r * 0.55f, cy + r * 0.52f, strokePaint)
                    path.moveTo(cx - r * 0.55f, cy - r * 0.52f)
                    path.lineTo(cx + r * 0.38f, cy)
                    path.lineTo(cx - r * 0.55f, cy + r * 0.52f)
                    path.close()
                    canvas.drawPath(path, fillPaint)
                }
                "HEADPHONES" -> {
                    val arc = RectF(cx - r * 0.85f, cy - r * 0.85f, cx + r * 0.85f, cy + r * 0.65f)
                    canvas.drawArc(arc, 180f, 180f, false, strokePaint)
                    val leftCup = RectF(cx - r * 0.92f, cy - r * 0.1f, cx - r * 0.48f, cy + r * 0.72f)
                    val rightCup = RectF(cx + r * 0.48f, cy - r * 0.1f, cx + r * 0.92f, cy + r * 0.72f)
                    canvas.drawRoundRect(leftCup, 3f * density, 3f * density, fillPaint)
                    canvas.drawRoundRect(rightCup, 3f * density, 3f * density, fillPaint)
                }
                "QUEUE_LIST" -> {
                    canvas.drawLine(cx - r * 0.75f, cy - r * 0.55f, cx + r * 0.75f, cy - r * 0.55f, strokePaint)
                    canvas.drawLine(cx - r * 0.75f, cy, cx + r * 0.75f, cy, strokePaint)
                    canvas.drawLine(cx - r * 0.75f, cy + r * 0.55f, cx + r * 0.35f, cy + r * 0.55f, strokePaint)
                }
                "THREE_DOTS" -> {
                    canvas.drawCircle(cx, cy - r * 0.65f, 2.5f * density, fillPaint)
                    canvas.drawCircle(cx, cy, 2.5f * density, fillPaint)
                    canvas.drawCircle(cx, cy + r * 0.65f, 2.5f * density, fillPaint)
                }
                "SPEAKER_ON", "SPEAKER_MUTE" -> {
                    path.moveTo(cx - r * 0.75f, cy - r * 0.32f)
                    path.lineTo(cx - r * 0.3f, cy - r * 0.32f)
                    path.lineTo(cx + r * 0.18f, cy - r * 0.75f)
                    path.lineTo(cx + r * 0.18f, cy + r * 0.75f)
                    path.lineTo(cx - r * 0.3f, cy + r * 0.32f)
                    path.lineTo(cx - r * 0.75f, cy + r * 0.32f)
                    path.close()
                    canvas.drawPath(path, strokePaint)
                    if (type == "SPEAKER_MUTE") {
                        canvas.drawLine(cx + r * 0.38f, cy - r * 0.35f, cx + r * 0.85f, cy + r * 0.35f, strokePaint)
                        canvas.drawLine(cx + r * 0.85f, cy - r * 0.35f, cx + r * 0.38f, cy + r * 0.35f, strokePaint)
                    } else {
                        val arc = RectF(cx - r * 0.1f, cy - r * 0.55f, cx + r * 0.78f, cy + r * 0.55f)
                        canvas.drawArc(arc, -45f, 90f, false, strokePaint)
                    }
                }
                "LOCK_OPEN", "LOCK_CLOSED" -> {
                    val body = RectF(cx - r * 0.68f, cy - r * 0.1f, cx + r * 0.68f, cy + r * 0.82f)
                    canvas.drawRoundRect(body, 3.5f * density, 3.5f * density, strokePaint)
                    val shackle = RectF(cx - r * 0.45f, cy - r * 0.78f, cx + r * 0.45f, cy - r * 0.05f)
                    canvas.drawArc(shackle, 180f, if (type == "LOCK_OPEN") 135f else 180f, false, strokePaint)
                    canvas.drawCircle(cx, cy + r * 0.35f, 2.2f * density, fillPaint)
                }
                "SCREENSHOT_CAM" -> {
                    val cam = RectF(cx - r * 0.82f, cy - r * 0.52f, cx + r * 0.82f, cy + r * 0.68f)
                    canvas.drawRoundRect(cam, 4f * density, 4f * density, strokePaint)
                    canvas.drawCircle(cx, cy + r * 0.08f, r * 0.34f, strokePaint)
                }
                "ROTATE" -> {
                    val box = RectF(cx - r * 0.75f, cy - r * 0.5f, cx + r * 0.75f, cy + r * 0.5f)
                    canvas.drawRoundRect(box, 3f * density, 3f * density, strokePaint)
                }
                "ASPECT_FIT", "ASPECT_STRETCH", "ASPECT_CROP", "ASPECT_16_9" -> {
                    val frame = RectF(cx - r * 0.85f, cy - r * 0.58f, cx + r * 0.85f, cy + r * 0.58f)
                    canvas.drawRoundRect(frame, 3.5f * density, 3.5f * density, strokePaint)
                    if (type == "ASPECT_STRETCH") {
                        canvas.drawLine(cx - r * 0.45f, cy, cx + r * 0.45f, cy, strokePaint)
                    } else if (type == "ASPECT_CROP") {
                        canvas.drawCircle(cx, cy, r * 0.26f, fillPaint)
                    }
                }
                "PIP_WINDOW" -> {
                    val outer = RectF(cx - r * 0.85f, cy - r * 0.6f, cx + r * 0.85f, cy + r * 0.6f)
                    canvas.drawRoundRect(outer, 3.5f * density, 3.5f * density, strokePaint)
                    val inner = RectF(cx + r * 0.05f, cy - r * 0.05f, cx + r * 0.68f, cy + r * 0.45f)
                    canvas.drawRoundRect(inner, 2f * density, 2f * density, fillPaint)
                }
                "AUDIO_WAVE", "SOUND_LAB" -> {
                    canvas.drawLine(cx - r * 0.6f, cy - r * 0.35f, cx - r * 0.6f, cy + r * 0.35f, strokePaint)
                    canvas.drawLine(cx - r * 0.2f, cy - r * 0.75f, cx - r * 0.2f, cy + r * 0.75f, strokePaint)
                    canvas.drawLine(cx + r * 0.2f, cy - r * 0.55f, cx + r * 0.2f, cy + r * 0.55f, strokePaint)
                    canvas.drawLine(cx + r * 0.6f, cy - r * 0.25f, cx + r * 0.6f, cy + r * 0.25f, strokePaint)
                }
                "EQ_SLIDERS" -> {
                    canvas.drawLine(cx - r * 0.55f, cy - r * 0.7f, cx - r * 0.55f, cy + r * 0.7f, strokePaint)
                    canvas.drawLine(cx, cy - r * 0.7f, cx, cy + r * 0.7f, strokePaint)
                    canvas.drawLine(cx + r * 0.55f, cy - r * 0.7f, cx + r * 0.55f, cy + r * 0.7f, strokePaint)
                    canvas.drawCircle(cx - r * 0.55f, cy - r * 0.2f, 2.8f * density, fillPaint)
                    canvas.drawCircle(cx, cy + r * 0.25f, 2.8f * density, fillPaint)
                    canvas.drawCircle(cx + r * 0.55f, cy - r * 0.25f, 2.8f * density, fillPaint)
                }
                "CAST_TV" -> {
                    val tv = RectF(cx - r * 0.8f, cy - r * 0.55f, cx + r * 0.8f, cy + r * 0.55f)
                    canvas.drawRoundRect(tv, 3.5f * density, 3.5f * density, strokePaint)
                    canvas.drawLine(cx - r * 0.4f, cy + r * 0.8f, cx + r * 0.4f, cy + r * 0.8f, strokePaint)
                }
                "SHARE_NODES" -> {
                    canvas.drawLine(cx - r * 0.5f, cy, cx + r * 0.5f, cy - r * 0.5f, strokePaint)
                    canvas.drawLine(cx - r * 0.5f, cy, cx + r * 0.5f, cy + r * 0.5f, strokePaint)
                    canvas.drawCircle(cx - r * 0.5f, cy, 3f * density, fillPaint)
                    canvas.drawCircle(cx + r * 0.5f, cy - r * 0.5f, 3f * density, fillPaint)
                    canvas.drawCircle(cx + r * 0.5f, cy + r * 0.5f, 3f * density, fillPaint)
                }
                "MP3_DISC" -> {
                    canvas.drawCircle(cx, cy, r * 0.8f, strokePaint)
                    canvas.drawCircle(cx, cy, r * 0.25f, fillPaint)
                }
                "STAR_FAV" -> {
                    canvas.drawCircle(cx, cy, r * 0.75f, strokePaint)
                    canvas.drawCircle(cx, cy, r * 0.35f, fillPaint)
                }
                "BOOKMARK" -> {
                    path.moveTo(cx - r * 0.55f, cy - r * 0.75f)
                    path.lineTo(cx + r * 0.55f, cy - r * 0.75f)
                    path.lineTo(cx + r * 0.55f, cy + r * 0.75f)
                    path.lineTo(cx, cy + r * 0.35f)
                    path.lineTo(cx - r * 0.55f, cy + r * 0.75f)
                    path.close()
                    canvas.drawPath(path, strokePaint)
                }
                "VR_GLASSES", "AB_LOOP", "MIRROR_FLIP", "EDIT_GRID" -> {
                    val box = RectF(cx - r * 0.78f, cy - r * 0.52f, cx + r * 0.78f, cy + r * 0.52f)
                    canvas.drawRoundRect(box, 4f * density, 4f * density, strokePaint)
                    canvas.drawLine(cx, cy - r * 0.52f, cx, cy + r * 0.52f, strokePaint)
                }
                "MOON_NIGHT", "TIMER_CLOCK", "GESTURE_HAND" -> {
                    canvas.drawCircle(cx, cy, r * 0.78f, strokePaint)
                    canvas.drawLine(cx, cy, cx, cy - r * 0.45f, strokePaint)
                    canvas.drawLine(cx, cy, cx + r * 0.35f, cy, strokePaint)
                }
                "DELETE_TRASH" -> {
                    canvas.drawLine(cx - r * 0.75f, cy - r * 0.52f, cx + r * 0.75f, cy - r * 0.52f, strokePaint)
                    val bin = RectF(cx - r * 0.55f, cy - r * 0.52f, cx + r * 0.55f, cy + r * 0.78f)
                    canvas.drawRoundRect(bin, 3f * density, 3f * density, strokePaint)
                }
                else -> {
                    canvas.drawCircle(cx, cy, r * 0.7f, strokePaint)
                }
            }
        }

        override fun setAlpha(alpha: Int) {
            strokePaint.alpha = alpha
            fillPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            strokePaint.colorFilter = colorFilter
            fillPaint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
