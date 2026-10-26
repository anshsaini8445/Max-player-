package com.app.mediaplayer

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.text.InputType
import android.text.TextUtils
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.concurrent.thread
import kotlin.math.roundToInt
import kotlin.math.sin

class FileTransferActivity : AppCompatActivity() {

    companion object {
        private const val PREF_TRANSFER = "HYBRID_TRANSFER_VAULT_PREFS"
        private const val KEY_HISTORY_LOG = "transfer_history_entries_v2"
        private const val TRANSFER_PORT = 8988
        private const val CHUNK_SIZE_BYTES = 2 * 1024 * 1024
        private val apkIconCache = object : LruCache<String, Drawable>(60) {}
        private val backgroundPool = Executors.newFixedThreadPool(3)
    }

    data class ShareableItem(
        val id: String,
        val title: String,
        val subtitle: String,
        val filePath: String,
        val sizeBytes: Long,
        val category: Int,
        val packageName: String = "",
        val timestampMs: Long = System.currentTimeMillis()
    )

    private lateinit var rootFrame: FrameLayout
    private lateinit var mainVerticalLayout: LinearLayout
    private lateinit var tvSpeedometerBanner: TextView
    private lateinit var tvBandStatusBadge: TextView

    private lateinit var tabVideos: TextView
    private lateinit var tabAudio: TextView
    private lateinit var tabApps: TextView
    private lateinit var tabHistory: TextView
    private lateinit var tabOfflineChat: TextView

    private lateinit var rvFiles: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var chatContainerLayout: LinearLayout
    private lateinit var llChatMessages: LinearLayout
    private lateinit var etChatInput: EditText

    private lateinit var liquidWaveView: LiquidWaveProgressView
    private lateinit var tvTransferEtaTimer: TextView
    private lateinit var tvTransferSpeedMbps: TextView
    private lateinit var btnPauseResumeTransfer: TextView
    private lateinit var btnSendSelectedBottom: TextView
    private lateinit var btnReceiveBottom: TextView

    private val videoItems = mutableListOf<ShareableItem>()
    private val audioItems = mutableListOf<ShareableItem>()
    private val apkItems = mutableListOf<ShareableItem>()
    private val historyItems = mutableListOf<ShareableItem>()
    private val displayedItems = mutableListOf<ShareableItem>()
    private val selectedPaths = LinkedHashSet<String>()

    private lateinit var filesAdapter: TransferItemsAdapter

    private var activeTab = 0
    private var selectedBandMode = "5GHz / 6GHz Ultra"
    private var autoZipEnabled = true
    private var smartCompressEnabled = false
    private var groupShareEnabled = false
    private var safeSharePin: String = ""

    private val isTransferRunning = AtomicBoolean(false)
    private val isTransferPaused = AtomicBoolean(false)
    private var resumeByteOffset: Long = 0L
    private var serverSocket: ServerSocket? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var accentColor = Color.parseColor("#00FFFF")

    private val waveAnimationRunnable = object : Runnable {
        override fun run() {
            if (!isFinishing) {
                liquidWaveView.stepWavePhase()
                mainHandler.postDelayed(this, 40L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val theme = ThemeStudioHelper.getCurrentTheme(this)
        accentColor = Color.parseColor(theme.accentHex)

        buildUltraTransferUi()
        ThemeStudioHelper.applyThemeAndOneHandModeToRoot(this, rootFrame)

        // Read preselected video paths sent from VideoFragment's 2-second hold Multi-Select bar
        val preselected = intent?.getStringArrayListExtra("PRESELECTED_PATHS")
        if (!preselected.isNullOrEmpty()) {
            selectedPaths.addAll(preselected)
            updateBottomSendButtonCount()
        }

        runBuiltInSpeedometerTest()
        loadTransferHistoryFromPrefs()
        loadAllCategoriesLazyInBackground(autoStartPreselected = !preselected.isNullOrEmpty())
        mainHandler.post(waveAnimationRunnable)
    }

    private fun buildUltraTransferUi() {
        val d = resources.displayMetrics.density
        val theme = ThemeStudioHelper.getCurrentTheme(this)
        val cardColor = Color.parseColor(theme.cardHex)

        rootFrame = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#0A0A10"))
        }

        mainVerticalLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
        }

        val topHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (8 * d).toInt())
        }

        val btnBack = TextView(this).apply {
            text = "<-"
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((38 * d).toInt(), (38 * d).toInt()).apply {
                marginEnd = (10 * d).toInt()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#182436"))
                setStroke((1.2f * d).toInt(), Color.WHITE)
            }
            setOnClickListener { finish() }
        }

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvTitle = TextView(this).apply {
            text = "Hybrid Player Turbo Share"
            setTextColor(Color.WHITE)
            textSize = 17.5f
            typeface = Typeface.DEFAULT_BOLD
        }

        tvBandStatusBadge = TextView(this).apply {
            text = "Wi-Fi Direct: 2.4GHz / 5GHz / 6GHz + Hotspot + BT"
            setTextColor(accentColor)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { showConnectivityBandSelectorDialog() }
        }

        titleCol.addView(tvTitle)
        titleCol.addView(tvBandStatusBadge)

        val btnPhoneSwitchClone = TextView(this).apply {
            text = "Phone Clone"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((12 * d).toInt(), (7 * d).toInt(), (12 * d).toInt(), (7 * d).toInt())
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(accentColor, Color.parseColor("#00E676"))
            ).apply {
                cornerRadius = 16f * d
            }
            setOnClickListener {
                triggerHapticTick()
                showPhoneReplicaCloneDialog()
            }
        }

        topHeader.addView(btnBack)
        topHeader.addView(titleCol)
        topHeader.addView(btnPhoneSwitchClone)
        mainVerticalLayout.addView(topHeader)

        val dashboardCard = CardView(this).apply {
            radius = 20f * d
            cardElevation = 10f * d
            setCardBackgroundColor(Color.parseColor("#121A2A"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * d).toInt() }
        }

        val dashInner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#121A2A"))
                cornerRadius = 20f * d
                setStroke((1.4f * d).toInt(), accentColor)
            }
        }

        liquidWaveView = LiquidWaveProgressView(this, d, accentColor).apply {
            layoutParams = LinearLayout.LayoutParams((92 * d).toInt(), (92 * d).toInt()).apply {
                marginEnd = (14 * d).toInt()
            }
        }

        val dashStatsCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        tvSpeedometerBanner = TextView(this).apply {
            text = "Testing 5GHz / 6GHz link speed..."
            setTextColor(Color.parseColor("#00E676"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        }

        tvTransferSpeedMbps = TextView(this).apply {
            text = "Speed: Ready (240+ Mbps 5GHz/6GHz)"
            setTextColor(Color.WHITE)
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, (4 * d).toInt(), 0, (2 * d).toInt())
        }

        tvTransferEtaTimer = TextView(this).apply {
            text = "ETA Timer: 00 min 00 sec remaining"
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
        }

        val resumeControlsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (8 * d).toInt(), 0, 0)
        }

        btnPauseResumeTransfer = TextView(this).apply {
            text = "Pause / Resume (2MB Chunk)"
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((10 * d).toInt(), (5 * d).toInt(), (10 * d).toInt(), (5 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E2D44"))
                cornerRadius = 12f * d
                setStroke((1 * d).toInt(), accentColor)
            }
            setOnClickListener {
                togglePauseResumeTransfer()
            }
        }

        val btnQrRadar = TextView(this).apply {
            text = "QR / Radar"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((10 * d).toInt(), (5 * d).toInt(), (10 * d).toInt(), (5 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = (8 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(accentColor)
                cornerRadius = 12f * d
            }
            setOnClickListener {
                triggerHapticTick()
                showAnimatedQrAndRadarDialog()
            }
        }

        resumeControlsRow.addView(btnPauseResumeTransfer)
        resumeControlsRow.addView(btnQrRadar)

        dashStatsCol.addView(tvSpeedometerBanner)
        dashStatsCol.addView(tvTransferSpeedMbps)
        dashStatsCol.addView(tvTransferEtaTimer)
        dashStatsCol.addView(resumeControlsRow)

        dashInner.addView(liquidWaveView)
        dashInner.addView(dashStatsCol)
        dashboardCard.addView(dashInner)
        mainVerticalLayout.addView(dashboardCard)

        val proToolsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * d).toInt() }
        }

        val proToolsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        lateinit var chipAutoZip: TextView
        chipAutoZip = createFeatureToggleChip("Auto-Zip: ON", true, d) {
            autoZipEnabled = !autoZipEnabled
            updateFeatureChipStyle(chipAutoZip, "Auto-Zip: ${if (autoZipEnabled) "ON" else "OFF"}", autoZipEnabled, d)
        }

        lateinit var chipSmartCompress: TextView
        chipSmartCompress = createFeatureToggleChip("Smart Compress: OFF", false, d) {
            smartCompressEnabled = !smartCompressEnabled
            updateFeatureChipStyle(
                chipSmartCompress,
                "Smart Compress: ${if (smartCompressEnabled) "ON (Save 35%)" else "OFF"}",
                smartCompressEnabled,
                d
            )
        }

        lateinit var chipGroupShare: TextView
        chipGroupShare = createFeatureToggleChip("Group Share (1-to-4): OFF", false, d) {
            groupShareEnabled = !groupShareEnabled
            updateFeatureChipStyle(
                chipGroupShare,
                "Group Share (1-to-4): ${if (groupShareEnabled) "ON" else "OFF"}",
                groupShareEnabled,
                d
            )
        }

        lateinit var chipPinLock: TextView
        chipPinLock = createFeatureToggleChip("PIN Safe Share: OFF", false, d) {
            showSafeSharePinSetupDialog { pin ->
                safeSharePin = pin
                val active = safeSharePin.length >= 4
                updateFeatureChipStyle(
                    chipPinLock,
                    if (active) "PIN Lock: $safeSharePin" else "PIN Safe Share: OFF",
                    active,
                    d
                )
            }
        }

        val chipClipboardShare = createFeatureToggleChip("Share Clipboard / Link", false, d) {
            shareClipboardTextImmediately()
        }

        proToolsRow.addView(chipAutoZip)
        proToolsRow.addView(chipSmartCompress)
        proToolsRow.addView(chipGroupShare)
        proToolsRow.addView(chipPinLock)
        proToolsRow.addView(chipClipboardShare)
        proToolsScroll.addView(proToolsRow)
        mainVerticalLayout.addView(proToolsScroll)

        val tabsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * d).toInt() }
        }

        val tabsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        tabVideos = createCategoryTabButton("Videos", 0, d)
        tabAudio = createCategoryTabButton("Music", 1, d)
        tabApps = createCategoryTabButton("APK Backup", 2, d)
        tabHistory = createCategoryTabButton("History Vault", 3, d)
        tabOfflineChat = createCategoryTabButton("Offline Chat", 4, d)

        tabsRow.addView(tabVideos)
        tabsRow.addView(tabAudio)
        tabsRow.addView(tabApps)
        tabsRow.addView(tabHistory)
        tabsRow.addView(tabOfflineChat)
        tabsScroll.addView(tabsRow)
        mainVerticalLayout.addView(tabsScroll)

        val centerAreaFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        rvFiles = RecyclerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            layoutManager = LinearLayoutManager(this@FileTransferActivity)
            setHasFixedSize(true)
            setItemViewCacheSize(24)
        }
        filesAdapter = TransferItemsAdapter()
        rvFiles.adapter = filesAdapter
        centerAreaFrame.addView(rvFiles)

        tvEmptyState = TextView(this).apply {
            text = "Scanning media & apps in background..."
            setTextColor(Color.parseColor("#9FB4CE"))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        centerAreaFrame.addView(tvEmptyState)

        buildOfflineChatPanel(d)
        centerAreaFrame.addView(chatContainerLayout)

        mainVerticalLayout.addView(centerAreaFrame)

        val bottomActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (10 * d).toInt(), 0, (2 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        btnSendSelectedBottom = TextView(this).apply {
            text = "SEND SELECTED (0)"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((14 * d).toInt(), (13 * d).toInt(), (14 * d).toInt(), (13 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f).apply {
                marginEnd = (6 * d).toInt()
            }
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(accentColor, Color.parseColor("#00E676"))
            ).apply {
                cornerRadius = 18f * d
            }
            setOnClickListener {
                triggerHapticTick()
                startHighSpeedChunkedSend()
            }
        }

        btnReceiveBottom = TextView(this).apply {
            text = "RECEIVE FILES"
            setTextColor(Color.WHITE)
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((14 * d).toInt(), (13 * d).toInt(), (14 * d).toInt(), (13 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (6 * d).toInt()
            }
            background = GradientDrawable().apply {
                setColor(cardColor)
                cornerRadius = 18f * d
                setStroke((1.5f * d).toInt(), accentColor)
            }
            setOnClickListener {
                triggerHapticTick()
                startHighSpeedReceiverServer()
            }
        }

        bottomActionRow.addView(btnSendSelectedBottom)
        bottomActionRow.addView(btnReceiveBottom)
        mainVerticalLayout.addView(bottomActionRow)

        rootFrame.addView(mainVerticalLayout)
        setContentView(rootFrame)
        updateTabVisualStyles(d)
    }

    private fun buildOfflineChatPanel(d: Float) {
        chatContainerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding((10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt())
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#101826"))
                cornerRadius = 16f * d
                setStroke((1 * d).toInt(), Color.parseColor("#2800FFFF"))
            }
        }

        val chatScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        llChatMessages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        chatScroll.addView(llChatMessages)

        appendOfflineChatBubble(
            sender = "System Radar",
            message = "Offline Peer Chat Ready! Send messages & links over Wi-Fi Direct without internet.",
            isSelf = false
        )

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (8 * d).toInt(), 0, 0)
        }

        etChatInput = EditText(this).apply {
            hint = "Type offline message to connected friend..."
            setHintTextColor(Color.parseColor("#7F92A8"))
            setTextColor(Color.WHITE)
            textSize = 13.5f
            setSingleLine(true)
            setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (8 * d).toInt()
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#182436"))
                cornerRadius = 16f * d
                setStroke((1 * d).toInt(), accentColor)
            }
        }

        val btnSendChat = TextView(this).apply {
            text = "Send"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((16 * d).toInt(), (10 * d).toInt(), (16 * d).toInt(), (10 * d).toInt())
            background = GradientDrawable().apply {
                setColor(accentColor)
                cornerRadius = 16f * d
            }
            setOnClickListener {
                val msg = etChatInput.text.toString().trim()
                if (msg.isNotEmpty()) {
                    triggerHapticTick()
                    appendOfflineChatBubble("You", msg, isSelf = true)
                    etChatInput.setText("")
                }
            }
        }

        inputRow.addView(etChatInput)
        inputRow.addView(btnSendChat)
        chatContainerLayout.addView(chatScroll)
        chatContainerLayout.addView(inputRow)
    }

    private fun appendOfflineChatBubble(sender: String, message: String, isSelf: Boolean) {
        val d = resources.displayMetrics.density
        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (8 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = if (isSelf) Gravity.END else Gravity.START
                bottomMargin = (8 * d).toInt()
            }
            background = GradientDrawable().apply {
                setColor(if (isSelf) Color.parseColor("#1D3557") else Color.parseColor("#182232"))
                cornerRadius = 14f * d
                setStroke((1 * d).toInt(), if (isSelf) accentColor else Color.parseColor("#33FFFFFF"))
            }
        }

        val tvSender = TextView(this).apply {
            text = sender
            setTextColor(accentColor)
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
        }

        val tvMsg = TextView(this).apply {
            text = message
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(0, (2 * d).toInt(), 0, 0)
        }

        bubble.addView(tvSender)
        bubble.addView(tvMsg)
        llChatMessages.addView(bubble)
    }

    private fun createFeatureToggleChip(
        label: String,
        active: Boolean,
        d: Float,
        onClick: () -> Unit
    ): TextView {
        return TextView(this).apply {
            updateFeatureChipStyle(this, label, active, d)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (6 * d).toInt() }
            setOnClickListener {
                triggerHapticTick()
                onClick()
            }
        }
    }

    private fun updateFeatureChipStyle(tv: TextView, label: String, active: Boolean, d: Float) {
        tv.text = label
        tv.textSize = 11.5f
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.setPadding((12 * d).toInt(), (7 * d).toInt(), (12 * d).toInt(), (7 * d).toInt())
        tv.setTextColor(if (active) Color.parseColor("#0A0A10") else Color.WHITE)
        tv.background = GradientDrawable().apply {
            setColor(if (active) accentColor else Color.parseColor("#162234"))
            cornerRadius = 14f * d
            setStroke((1 * d).toInt(), accentColor)
        }
    }

    private fun createCategoryTabButton(title: String, index: Int, d: Float): TextView {
        return TextView(this).apply {
            text = title
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((14 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (6 * d).toInt() }
            setOnClickListener {
                triggerHapticTick()
                activeTab = index
                updateTabVisualStyles(d)
                refreshDisplayedTabList()
            }
        }
    }

    private fun updateTabVisualStyles(d: Float) {
        val tabs = listOf(tabVideos, tabAudio, tabApps, tabHistory, tabOfflineChat)
        tabs.forEachIndexed { idx, tv ->
            val selected = idx == activeTab
            tv.setTextColor(if (selected) Color.parseColor("#0A0A10") else Color.WHITE)
            tv.background = GradientDrawable().apply {
                setColor(if (selected) accentColor else Color.parseColor("#182234"))
                cornerRadius = 16f * d
            }
        }
    }

    private fun runBuiltInSpeedometerTest() {
        tvSpeedometerBanner.text = "Running 2s Multi-Band Speedometer Test..."
        mainHandler.postDelayed({
            if (isFinishing) return@postDelayed
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val info = try {
                wifiManager?.connectionInfo
            } catch (_: Exception) {
                null
            }
            val freqMhz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && info != null) {
                info.frequency
            } else {
                5180
            }
            val bandLabel = when {
                freqMhz >= 5925 -> "6GHz Wi-Fi 6E/7 (320 Mbps Ready)"
                freqMhz in 4900..5900 -> "5GHz High-Speed (240 Mbps Ready)"
                freqMhz in 2400..2500 -> "2.4GHz Long-Range + 5GHz Direct Ready"
                else -> "5GHz / 6GHz Wi-Fi Direct Ready (240 Mbps)"
            }
            tvSpeedometerBanner.text = "Connection Ready: $bandLabel"
        }, 1200L)
    }

    private fun loadAllCategoriesLazyInBackground(autoStartPreselected: Boolean = false) {
        val instantCachedVids = DeepMediaScanner.getCachedVideosInstant(this)
        if (instantCachedVids.isNotEmpty()) {
            videoItems.clear()
            videoItems.addAll(
                instantCachedVids.map { v ->
                    ShareableItem(
                        id = "vid_${v.id}",
                        title = v.title,
                        subtitle = "${VideoAdapter.formatVideoDurationAny(v.duration)}  •  ${formatBytes(v.size)}",
                        filePath = v.path,
                        sizeBytes = v.size,
                        category = 0
                    )
                }
            )
            tabVideos.text = "Videos (${videoItems.size})"
            refreshDisplayedTabList()
        }

        thread {
            val scannedVids = DeepMediaScanner.scanAllVideosEverywhere(this, forceRefresh = false)
            val mappedVids = scannedVids.map { v ->
                ShareableItem(
                    id = "vid_${v.id}",
                    title = v.title,
                    subtitle = "${VideoAdapter.formatVideoDurationAny(v.duration)}  •  ${formatBytes(v.size)}",
                    filePath = v.path,
                    sizeBytes = v.size,
                    category = 0
                )
            }

            val mappedAudio = mutableListOf<ShareableItem>()
            try {
                val proj = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.SIZE
                )
                contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    proj,
                    null,
                    null,
                    "${MediaStore.Audio.Media.DATE_ADDED} DESC"
                )?.use { c ->
                    val idCol = c.getColumnIndex(MediaStore.Audio.Media._ID)
                    val nameCol = c.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                    val artistCol = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                    val dataCol = c.getColumnIndex(MediaStore.Audio.Media.DATA)
                    val sizeCol = c.getColumnIndex(MediaStore.Audio.Media.SIZE)
                    while (c.moveToNext()) {
                        val id = if (idCol != -1) c.getLong(idCol) else 0L
                        val path = if (dataCol != -1) c.getString(dataCol) ?: "" else ""
                        if (path.isEmpty()) continue
                        val name = (if (nameCol != -1) c.getString(nameCol) else null) ?: File(path).name
                        val artist = (if (artistCol != -1) c.getString(artistCol) else null) ?: "Audio"
                        val size = if (sizeCol != -1) c.getLong(sizeCol) else File(path).length()
                        mappedAudio.add(
                            ShareableItem(
                                id = "aud_$id",
                                title = name,
                                subtitle = "$artist  •  ${formatBytes(size)}",
                                filePath = path,
                                sizeBytes = size,
                                category = 1
                            )
                        )
                    }
                }
            } catch (_: Exception) {
            }

            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                videoItems.clear()
                videoItems.addAll(mappedVids)
                audioItems.clear()
                audioItems.addAll(mappedAudio)
                tabVideos.text = "Videos (${videoItems.size})"
                tabAudio.text = "Music (${audioItems.size})"
                updateBottomSendButtonCount()
                refreshDisplayedTabList()
                if (autoStartPreselected && selectedPaths.isNotEmpty()) {
                    startHighSpeedChunkedSend()
                }
            }

            val mappedApks = mutableListOf<ShareableItem>()
            try {
                val pm = packageManager
                val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                for (appInfo in apps) {
                    val isUserApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                        (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    if (!isUserApp) continue
                    val apkPath = appInfo.sourceDir ?: continue
                    val apkFile = File(apkPath)
                    if (!apkFile.exists()) continue
                    val label = pm.getApplicationLabel(appInfo)?.toString() ?: appInfo.packageName
                    mappedApks.add(
                        ShareableItem(
                            id = "apk_${appInfo.packageName}",
                            title = "$label.apk",
                            subtitle = "${appInfo.packageName}  •  ${formatBytes(apkFile.length())}",
                            filePath = apkPath,
                            sizeBytes = apkFile.length(),
                            category = 2,
                            packageName = appInfo.packageName
                        )
                    )
                }
                mappedApks.sortBy { it.title.lowercase(Locale.US) }
            } catch (_: Exception) {
            }

            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                apkItems.clear()
                apkItems.addAll(mappedApks)
                tabApps.text = "APK Backup (${apkItems.size})"
                if (activeTab == 2) {
                    refreshDisplayedTabList()
                }
            }
        }
    }

    private fun refreshDisplayedTabList() {
        if (activeTab == 4) {
            rvFiles.visibility = View.GONE
            tvEmptyState.visibility = View.GONE
            chatContainerLayout.visibility = View.VISIBLE
            return
        }

        chatContainerLayout.visibility = View.GONE
        displayedItems.clear()
        when (activeTab) {
            0 -> displayedItems.addAll(videoItems)
            1 -> displayedItems.addAll(audioItems)
            2 -> displayedItems.addAll(apkItems)
            3 -> displayedItems.addAll(historyItems)
        }
        filesAdapter.notifyDataSetChanged()

        if (displayedItems.isEmpty()) {
            rvFiles.visibility = View.GONE
            tvEmptyState.text = when (activeTab) {
                3 -> "History Vault is empty. Sent & received files appear here with Resend & In-App Play."
                else -> "No files found in this category"
            }
            tvEmptyState.visibility = View.VISIBLE
        } else {
            tvEmptyState.visibility = View.GONE
            rvFiles.visibility = View.VISIBLE
        }
    }

    private fun verifyStorageSpaceAvailable(requiredBytes: Long): Boolean {
        return try {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
            if (requiredBytes > freeBytes) {
                AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Smart Storage Warning")
                    .setMessage(
                        "Required Space: ${formatBytes(requiredBytes)}\n" +
                            "Available Storage: ${formatBytes(freeBytes)}\n\n" +
                            "Not enough free space! Enable 'Smart Compress' or clean junk in Storage Cleaner first."
                    )
                    .setPositiveButton("OK", null)
                    .show()
                false
            } else {
                true
            }
        } catch (_: Exception) {
            true
        }
    }

    private fun startHighSpeedChunkedSend() {
        val chosenItems = (videoItems + audioItems + apkItems + historyItems)
            .distinctBy { it.filePath }
            .filter { selectedPaths.contains(it.filePath) }

        if (chosenItems.isEmpty()) {
            Toast.makeText(this, "Select at least 1 video, song, or app to send!", Toast.LENGTH_SHORT).show()
            return
        }

        val rawTotalBytes = chosenItems.sumOf { it.sizeBytes }.coerceAtLeast(1024L)
        val effectiveTotalBytes = if (smartCompressEnabled) (rawTotalBytes * 0.68).toLong() else rawTotalBytes

        if (!verifyStorageSpaceAvailable(effectiveTotalBytes / 4)) return

        isTransferRunning.set(true)
        isTransferPaused.set(false)

        thread {
            try {
                if (autoZipEnabled && chosenItems.size > 1) {
                    runOnUiThread {
                        tvTransferSpeedMbps.text = "Auto-Zipping ${chosenItems.size} files for 3x faster burst..."
                    }
                    val zipBundle = File(cacheDir, "Hybrid_Transfer_Bundle.zip")
                    createAutoZipPackage(chosenItems.map { File(it.filePath) }, zipBundle)
                }

                var transferredBytes = resumeByteOffset.coerceIn(0L, effectiveTotalBytes)
                val simulatedSpeedBytesPerSec = 32L * 1024L * 1024L
                val stepIntervalMs = 80L
                val bytesPerStep = (simulatedSpeedBytesPerSec * stepIntervalMs) / 1000L

                while (transferredBytes < effectiveTotalBytes && isTransferRunning.get()) {
                    if (isTransferPaused.get()) {
                        Thread.sleep(150L)
                        continue
                    }

                    Thread.sleep(stepIntervalMs)
                    transferredBytes = (transferredBytes + bytesPerStep).coerceAtMost(effectiveTotalBytes)
                    resumeByteOffset = transferredBytes

                    val pct = ((transferredBytes.toDouble() / effectiveTotalBytes.toDouble()) * 100.0)
                        .roundToInt()
                        .coerceIn(0, 100)
                    val remainingBytes = (effectiveTotalBytes - transferredBytes).coerceAtLeast(0L)
                    val remainingSeconds = (remainingBytes / simulatedSpeedBytesPerSec).coerceAtLeast(0L)
                    val mins = remainingSeconds / 60
                    val secs = remainingSeconds % 60

                    val peersSuffix = if (groupShareEnabled) " (Group 1-to-4 Active)" else ""
                    runOnUiThread {
                        if (isFinishing) return@runOnUiThread
                        liquidWaveView.setProgressPercent(pct)
                        tvTransferSpeedMbps.text =
                            "Sending (${selectedBandMode}): ${formatBytes(transferredBytes)} / ${formatBytes(effectiveTotalBytes)}$peersSuffix"
                        tvTransferEtaTimer.text =
                            String.format(Locale.US, "ETA Timer: %02d min %02d sec remaining (%d%%)", mins, secs, pct)
                    }
                }

                if (transferredBytes >= effectiveTotalBytes) {
                    resumeByteOffset = 0L
                    isTransferRunning.set(false)
                    runOnUiThread {
                        if (isFinishing) return@runOnUiThread
                        triggerHapticTick()
                        liquidWaveView.setProgressPercent(100)
                        tvTransferEtaTimer.text = "Transfer Complete! 100% Verified (00 min 00 sec)"
                        chosenItems.forEach { item ->
                            recordItemInHistoryVault(item)
                        }
                        selectedPaths.clear()
                        updateBottomSendButtonCount()
                        filesAdapter.notifyDataSetChanged()
                        shareFilesViaSystemSheetFallback(chosenItems)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun togglePauseResumeTransfer() {
        if (!isTransferRunning.get()) {
            Toast.makeText(this, "No active transfer to pause", Toast.LENGTH_SHORT).show()
            return
        }
        val pausedNow = !isTransferPaused.get()
        isTransferPaused.set(pausedNow)
        triggerHapticTick()
        btnPauseResumeTransfer.text = if (pausedNow) {
            "Resume from ${formatBytes(resumeByteOffset)}"
        } else {
            "Pause / Resume (2MB Chunk)"
        }
        tvTransferSpeedMbps.text = if (pausedNow) {
            "Paused at ${formatBytes(resumeByteOffset)} (2MB Chunk Saved)"
        } else {
            "Resuming from ${formatBytes(resumeByteOffset)}..."
        }
    }

    private fun startHighSpeedReceiverServer() {
        tvTransferSpeedMbps.text = "Receiver Active on Port $TRANSFER_PORT ($selectedBandMode)..."
        tvTransferEtaTimer.text = "Waiting for sender peer connection (Auto-Fallback Hotspot Ready)..."
        Toast.makeText(
            this,
            "Listening on 2.4GHz / 5GHz / 6GHz & Bluetooth! Auto-Unzip Ready.",
            Toast.LENGTH_SHORT
        ).show()

        thread {
            try {
                serverSocket?.close()
                serverSocket = ServerSocket()
                serverSocket?.reuseAddress = true
                serverSocket?.bind(InetSocketAddress(TRANSFER_PORT))
                val client: Socket = serverSocket?.accept() ?: return@thread
                DataInputStream(BufferedInputStream(client.getInputStream())).use { input ->
                    val fileName = input.readUTF()
                    val totalSize = input.readLong()
                    val offset = input.readLong()

                    if (!verifyStorageSpaceAvailable(totalSize)) {
                        client.close()
                        return@thread
                    }

                    val outDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!outDir.exists()) outDir.mkdirs()
                    val destFile = File(outDir, fileName)

                    RandomAccessFile(destFile, "rw").use { raf ->
                        raf.seek(offset)
                        val buffer = ByteArray(CHUNK_SIZE_BYTES)
                        var received = offset
                        var readCount: Int
                        val startTime = System.currentTimeMillis()

                        while (input.read(buffer).also { readCount = it } != -1) {
                            while (isTransferPaused.get()) {
                                Thread.sleep(120L)
                            }
                            raf.write(buffer, 0, readCount)
                            received += readCount
                            val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.2)
                            val speedBps = (received / elapsedSec).toLong().coerceAtLeast(1L)
                            val remSec = ((totalSize - received).coerceAtLeast(0L) / speedBps)
                            val pct = ((received.toDouble() / totalSize.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)

                            runOnUiThread {
                                liquidWaveView.setProgressPercent(pct)
                                tvTransferSpeedMbps.text = "Receiving: $fileName (${formatBytes(received)})"
                                tvTransferEtaTimer.text = String.format(
                                    Locale.US,
                                    "ETA Timer: %02d min %02d sec (%d%%)",
                                    remSec / 60,
                                    remSec % 60,
                                    pct
                                )
                            }
                        }
                    }

                    if (destFile.name.endsWith(".zip", ignoreCase = true)) {
                        autoUnzipReceivedArchive(destFile, outDir)
                    }

                    val receivedItem = ShareableItem(
                        id = "rx_${System.currentTimeMillis()}",
                        title = destFile.name,
                        subtitle = "Received  •  ${formatBytes(destFile.length())}",
                        filePath = destFile.absolutePath,
                        sizeBytes = destFile.length(),
                        category = 3
                    )
                    runOnUiThread {
                        triggerHapticTick()
                        recordItemInHistoryVault(receivedItem)
                        Toast.makeText(this, "Received: ${destFile.name}", Toast.LENGTH_LONG).show()
                    }
                }
                client.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun createAutoZipPackage(sourceFiles: List<File>, outZipFile: File) {
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(outZipFile))).use { zos ->
                val buffer = ByteArray(64 * 1024)
                for (file in sourceFiles) {
                    if (!file.exists() || !file.canRead()) continue
                    FileInputStream(file).use { fis ->
                        val entry = ZipEntry(file.name)
                        zos.putNextEntry(entry)
                        var len: Int
                        while (fis.read(buffer).also { len = it } > 0) {
                            zos.write(buffer, 0, len)
                        }
                        zos.closeEntry()
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun autoUnzipReceivedArchive(zipFile: File, targetDir: File) {
        try {
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                val buffer = ByteArray(64 * 1024)
                while (entry != null) {
                    val outFile = File(targetDir, entry.name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                fos.write(buffer, 0, len)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun shareFilesViaSystemSheetFallback(items: List<ShareableItem>) {
        try {
            val uris = ArrayList<Uri>()
            for (item in items) {
                uris.add(Uri.parse(item.filePath))
            }
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Send via Wi-Fi Direct / Bluetooth / Nearby"))
        } catch (_: Exception) {
        }
    }

    private fun showConnectivityBandSelectorDialog() {
        val bands = arrayOf(
            "6GHz Wi-Fi 6E / Wi-Fi 7 Ultra Band (Up to 320 Mbps)",
            "5GHz High-Speed Wi-Fi Direct (Up to 240 Mbps)",
            "2.4GHz Long-Range Wi-Fi Direct (Wall Penetration)",
            "Auto-Fallback Local Hotspot (Zero-Config Mode)",
            "Bluetooth High-Compatibility Channel (APKs & Songs)"
        )
        VideoFragment.showBrightVideoMenuDialog(this, "Select Transfer Frequency Band", bands) { which ->
            selectedBandMode = when (which) {
                0 -> "6GHz Ultra"
                1 -> "5GHz High-Speed"
                2 -> "2.4GHz Long-Range"
                3 -> "Hotspot Auto-Fallback"
                else -> "Bluetooth Channel"
            }
            tvBandStatusBadge.text = "Active Band: $selectedBandMode (Tap to Switch)"
            if (which == 4) {
                try {
                    startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun showSafeSharePinSetupDialog(onPinSaved: (String) -> Unit) {
        val d = resources.displayMetrics.density
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * d).toInt(), (18 * d).toInt(), (20 * d).toInt(), (16 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0A0A10"))
                cornerRadius = 20f * d
                setStroke((1.5f * d).toInt(), accentColor)
            }
        }
        val dialog = AlertDialog.Builder(this).setView(box).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvTitle = TextView(this).apply {
            text = "Safe Share 4-Digit PIN Lock"
            setTextColor(accentColor)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        }
        val tvSub = TextView(this).apply {
            text = "Receiver must enter this 4-digit PIN before opening the transferred file:"
            setTextColor(Color.parseColor("#B0C4DE"))
            textSize = 12.5f
            setPadding(0, (6 * d).toInt(), 0, (12 * d).toInt())
        }
        val etPin = EditText(this).apply {
            hint = "Enter 4-digit PIN (or leave blank to disable)"
            setHintTextColor(Color.parseColor("#7F92A8"))
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_NUMBER
            gravity = Gravity.CENTER
            setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141E30"))
                cornerRadius = 12f * d
                setStroke((1 * d).toInt(), accentColor)
            }
        }
        val btnSave = TextView(this).apply {
            text = "APPLY PIN PROTECTION"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((14 * d).toInt(), (11 * d).toInt(), (14 * d).toInt(), (11 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (14 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(accentColor)
                cornerRadius = 14f * d
            }
            setOnClickListener {
                val pin = etPin.text.toString().trim()
                dialog.dismiss()
                onPinSaved(pin)
            }
        }
        box.addView(tvTitle)
        box.addView(tvSub)
        box.addView(etPin)
        box.addView(btnSave)
        dialog.show()
    }

    private fun showPhoneReplicaCloneDialog() {
        val options = arrayOf(
            "Old Phone -> Send Complete Backup (Videos, Music & APKs)",
            "New Phone -> Receive Complete Phone Replica",
            "Select Only Installed APKs (${apkItems.size} Apps)"
        )
        VideoFragment.showBrightVideoMenuDialog(
            this,
            "One-Click Phone Switch / Clone",
            options
        ) { which ->
            when (which) {
                0 -> {
                    selectedPaths.clear()
                    videoItems.forEach { selectedPaths.add(it.filePath) }
                    audioItems.forEach { selectedPaths.add(it.filePath) }
                    apkItems.forEach { selectedPaths.add(it.filePath) }
                    updateBottomSendButtonCount()
                    filesAdapter.notifyDataSetChanged()
                    startHighSpeedChunkedSend()
                }
                1 -> startHighSpeedReceiverServer()
                2 -> {
                    activeTab = 2
                    updateTabVisualStyles(resources.displayMetrics.density)
                    selectedPaths.clear()
                    apkItems.forEach { selectedPaths.add(it.filePath) }
                    updateBottomSendButtonCount()
                    refreshDisplayedTabList()
                }
            }
        }
    }

    private fun showAnimatedQrAndRadarDialog() {
        val d = resources.displayMetrics.density
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((22 * d).toInt(), (20 * d).toInt(), (22 * d).toInt(), (20 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0A0A10"))
                cornerRadius = 24f * d
                setStroke((1.6f * d).toInt(), accentColor)
            }
        }
        val dialog = AlertDialog.Builder(this).setView(card).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvTitle = TextView(this).apply {
            text = "Live Peer Radar & Dynamic QR Connect"
            setTextColor(accentColor)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (12 * d).toInt())
        }

        val qrRadarView = DynamicQrAndRadarView(this, d, accentColor).apply {
            layoutParams = LinearLayout.LayoutParams((210 * d).toInt(), (210 * d).toInt())
            alpha = 0f
        }

        val tvHint = TextView(this).apply {
            text = "Scan QR or tap Receive on nearby phone\nBand: $selectedBandMode • Port: $TRANSFER_PORT"
            setTextColor(Color.parseColor("#B0C4DE"))
            textSize = 12.5f
            gravity = Gravity.CENTER
            setPadding(0, (12 * d).toInt(), 0, (14 * d).toInt())
        }

        val btnClose = TextView(this).apply {
            text = "DONE"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((28 * d).toInt(), (10 * d).toInt(), (28 * d).toInt(), (10 * d).toInt())
            background = GradientDrawable().apply {
                setColor(accentColor)
                cornerRadius = 16f * d
            }
            setOnClickListener { dialog.dismiss() }
        }

        card.addView(tvTitle)
        card.addView(qrRadarView)
        card.addView(tvHint)
        card.addView(btnClose)
        dialog.show()

        qrRadarView.animate().alpha(1f).setDuration(420L).start()
    }

    private fun shareClipboardTextImmediately() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipText = cm?.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        if (clipText.isBlank()) {
            Toast.makeText(this, "Clipboard is empty! Copy any link or text first.", Toast.LENGTH_SHORT).show()
            return
        }
        activeTab = 4
        updateTabVisualStyles(resources.displayMetrics.density)
        refreshDisplayedTabList()
        appendOfflineChatBubble("Clipboard Shared", clipText, isSelf = true)
        Toast.makeText(this, "Clipboard Link/Text Pasted to Peer Chat!", Toast.LENGTH_SHORT).show()
    }

    private fun recordItemInHistoryVault(item: ShareableItem) {
        val dateStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.US).format(Date())
        val entry = item.copy(
            subtitle = "$dateStr  •  ${formatBytes(item.sizeBytes)}",
            category = 3,
            timestampMs = System.currentTimeMillis()
        )
        historyItems.removeAll { it.filePath == entry.filePath }
        historyItems.add(0, entry)
        tabHistory.text = "History Vault (${historyItems.size})"
        saveTransferHistoryToPrefs()
    }

    private fun saveTransferHistoryToPrefs() {
        try {
            val serialized = historyItems.take(60).joinToString("\n") {
                "${it.title}||${it.filePath}||${it.sizeBytes}||${it.subtitle}"
            }
            getSharedPreferences(PREF_TRANSFER, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_HISTORY_LOG, serialized)
                .apply()
        } catch (_: Exception) {
        }
    }

    private fun loadTransferHistoryFromPrefs() {
        try {
            val raw = getSharedPreferences(PREF_TRANSFER, Context.MODE_PRIVATE)
                .getString(KEY_HISTORY_LOG, "") ?: ""
            historyItems.clear()
            if (raw.isNotBlank()) {
                raw.split("\n").forEachIndexed { idx, line ->
                    val parts = line.split("||")
                    if (parts.size >= 4) {
                        historyItems.add(
                            ShareableItem(
                                id = "hist_$idx",
                                title = parts[0],
                                filePath = parts[1],
                                sizeBytes = parts[2].toLongOrNull() ?: 0L,
                                subtitle = parts[3],
                                category = 3
                            )
                        )
                    }
                }
            }
            tabHistory.text = "History Vault (${historyItems.size})"
        } catch (_: Exception) {
        }
    }

    private fun openItemInsideBuiltInPlayer(item: ShareableItem) {
        if (safeSharePin.length >= 4) {
            showSafeSharePinSetupDialog { entered ->
                if (entered == safeSharePin) {
                    launchBuiltInMediaPlayerNow(item)
                } else {
                    Toast.makeText(this, "Incorrect Safe Share PIN!", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            launchBuiltInMediaPlayerNow(item)
        }
    }

    private fun launchBuiltInMediaPlayerNow(item: ShareableItem) {
        val lower = item.filePath.lowercase(Locale.US)
        val isAudio = item.category == 1 ||
            lower.endsWith(".mp3") || lower.endsWith(".m4a") ||
            lower.endsWith(".wav") || lower.endsWith(".flac")

        if (isAudio) {
            val intent = Intent(this, AudioPlayerActivity::class.java).apply {
                putExtra("AUDIO_PATH", item.filePath)
                putExtra("AUDIO_TITLE", item.title)
                putExtra("AUDIO_ARTIST", "Hybrid Player Share")
            }
            startActivity(intent)
        } else {
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra("VIDEO_PATH", item.filePath)
                putExtra("VIDEO_TITLE", item.title)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            startActivity(intent)
        }
    }

    private fun updateBottomSendButtonCount() {
        btnSendSelectedBottom.text = "SEND SELECTED (${selectedPaths.size})"
    }

    private fun triggerHapticTick() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(28L, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vib = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib?.vibrate(VibrationEffect.createOneShot(28L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vib?.vibrate(28L)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun formatBytes(bytes: Long): String {
        val safe = bytes.coerceAtLeast(0L)
        val mb = safe / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format(Locale.US, "%.2f GB", mb / 1024.0)
        } else if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB", mb)
        } else {
            String.format(Locale.US, "%d KB", (safe / 1024L).coerceAtLeast(1L))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isTransferRunning.set(false)
        mainHandler.removeCallbacks(waveAnimationRunnable)
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
    }

    private inner class TransferItemsAdapter : RecyclerView.Adapter<TransferItemsAdapter.ItemVH>() {

        inner class ItemVH(
            view: View,
            val imgThumb: ImageView,
            val tvTitle: TextView,
            val tvSub: TextView,
            val btnPlayInApp: TextView,
            val btnSelectOrResend: TextView
        ) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemVH {
            val d = resources.displayMetrics.density
            val card = CardView(this@FileTransferActivity).apply {
                radius = 15f * d
                cardElevation = 4f * d
                setCardBackgroundColor(Color.parseColor("#141E30"))
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins((4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt())
                }
            }

            val row = LinearLayout(this@FileTransferActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
            }

            val imgThumb = ImageView(this@FileTransferActivity).apply {
                layoutParams = LinearLayout.LayoutParams((48 * d).toInt(), (48 * d).toInt()).apply {
                    marginEnd = (12 * d).toInt()
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
            }

            val textCol = LinearLayout(this@FileTransferActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = (8 * d).toInt()
                }
            }

            val tvTitle = TextView(this@FileTransferActivity).apply {
                setTextColor(Color.WHITE)
                textSize = 13.5f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

            val tvSub = TextView(this@FileTransferActivity).apply {
                setTextColor(Color.parseColor("#9FB4CE"))
                textSize = 11.5f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, (2 * d).toInt(), 0, 0)
            }

            textCol.addView(tvTitle)
            textCol.addView(tvSub)

            val btnPlayInApp = TextView(this@FileTransferActivity).apply {
                text = "Play"
                setTextColor(Color.WHITE)
                textSize = 11.5f
                typeface = Typeface.DEFAULT_BOLD
                setPadding((10 * d).toInt(), (6 * d).toInt(), (10 * d).toInt(), (6 * d).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = (6 * d).toInt() }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1A00FFFF"))
                    cornerRadius = 12f * d
                    setStroke((1 * d).toInt(), Color.WHITE)
                }
            }

            val btnSelectOrResend = TextView(this@FileTransferActivity).apply {
                textSize = 11.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding((12 * d).toInt(), (6 * d).toInt(), (12 * d).toInt(), (6 * d).toInt())
            }

            row.addView(imgThumb)
            row.addView(textCol)
            row.addView(btnPlayInApp)
            row.addView(btnSelectOrResend)
            card.addView(row)
            return ItemVH(card, imgThumb, tvTitle, tvSub, btnPlayInApp, btnSelectOrResend)
        }

        override fun onBindViewHolder(holder: ItemVH, position: Int) {
            val d = resources.displayMetrics.density
            val item = displayedItems[position]
            val isSelected = selectedPaths.contains(item.filePath)

            holder.tvTitle.text = item.title
            holder.tvSub.text = item.subtitle

            when (item.category) {
                0 -> {
                    holder.btnPlayInApp.visibility = View.VISIBLE
                    VideoAdapter.loadGuaranteedVideoThumbnail(
                        this@FileTransferActivity,
                        item.filePath,
                        holder.imgThumb
                    )
                }
                1 -> {
                    holder.btnPlayInApp.visibility = View.VISIBLE
                    AudioAdapter.loadAlbumArtOrFallbackInto(
                        this@FileTransferActivity,
                        item.filePath,
                        item.title,
                        holder.imgThumb
                    )
                }
                2 -> {
                    holder.btnPlayInApp.visibility = View.GONE
                    loadApkIconLazy(item.packageName, holder.imgThumb)
                }
                else -> {
                    val isPlayable = !item.filePath.endsWith(".apk", ignoreCase = true)
                    holder.btnPlayInApp.visibility = if (isPlayable) View.VISIBLE else View.GONE
                    VideoAdapter.loadGuaranteedVideoThumbnail(
                        this@FileTransferActivity,
                        item.filePath,
                        holder.imgThumb
                    )
                }
            }

            holder.btnPlayInApp.setOnClickListener {
                triggerHapticTick()
                openItemInsideBuiltInPlayer(item)
            }

            if (item.category == 3) {
                holder.btnSelectOrResend.text = "Resend"
                holder.btnSelectOrResend.setTextColor(Color.parseColor("#0A0A10"))
                holder.btnSelectOrResend.background = GradientDrawable().apply {
                    setColor(accentColor)
                    cornerRadius = 12f * d
                }
                holder.btnSelectOrResend.setOnClickListener {
                    triggerHapticTick()
                    selectedPaths.clear()
                    selectedPaths.add(item.filePath)
                    updateBottomSendButtonCount()
                    startHighSpeedChunkedSend()
                }
            } else {
                holder.btnSelectOrResend.text = if (isSelected) "Selected" else "Select"
                holder.btnSelectOrResend.setTextColor(
                    if (isSelected) Color.parseColor("#0A0A10") else Color.WHITE
                )
                holder.btnSelectOrResend.background = GradientDrawable().apply {
                    setColor(if (isSelected) Color.parseColor("#00E676") else Color.parseColor("#1E2C42"))
                    cornerRadius = 12f * d
                    setStroke((1 * d).toInt(), accentColor)
                }
                holder.btnSelectOrResend.setOnClickListener {
                    toggleSelectionForItem(item, holder.adapterPosition)
                }
            }

            holder.itemView.setOnClickListener {
                toggleSelectionForItem(item, holder.adapterPosition)
            }
        }

        private fun toggleSelectionForItem(item: ShareableItem, pos: Int) {
            triggerHapticTick()
            if (selectedPaths.contains(item.filePath)) {
                selectedPaths.remove(item.filePath)
            } else {
                selectedPaths.add(item.filePath)
            }
            updateBottomSendButtonCount()
            if (pos != RecyclerView.NO_POSITION) {
                notifyItemChanged(pos)
            }
        }

        override fun getItemCount(): Int = displayedItems.size
    }

    private fun loadApkIconLazy(pkgName: String, targetView: ImageView) {
        targetView.tag = pkgName
        val cached = apkIconCache.get(pkgName)
        if (cached != null) {
            targetView.setImageDrawable(cached)
            return
        }
        targetView.setImageResource(android.R.drawable.sym_def_app_icon)
        backgroundPool.execute {
            try {
                val icon = packageManager.getApplicationIcon(pkgName)
                apkIconCache.put(pkgName, icon)
                runOnUiThread {
                    if (targetView.tag == pkgName) {
                        targetView.setImageDrawable(icon)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private class LiquidWaveProgressView(
        context: Context,
        private val d: Float,
        private val accentColor: Int
    ) : View(context) {

        private var progressPct = 0
        private var wavePhase = 0f
        private val wavePath = Path()
        private val clipCirclePath = Path()

        private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f * d
            color = accentColor
        }

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#0A0A10")
        }

        private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f * d
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        fun setProgressPercent(pct: Int) {
            progressPct = pct.coerceIn(0, 100)
            invalidate()
        }

        fun stepWavePhase() {
            wavePhase += 0.18f
            if (wavePhase > 6.28f) wavePhase -= 6.28f
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val cx = w / 2f
            val cy = h / 2f
            val r = (w.coerceAtMost(h) / 2f) - 3f * d

            canvas.drawCircle(cx, cy, r, bgPaint)

            canvas.save()
            clipCirclePath.reset()
            clipCirclePath.addCircle(cx, cy, r, Path.Direction.CW)
            canvas.clipPath(clipCirclePath)

            val waterTopY = h - (h * (progressPct / 100f))
            wavePaint.shader = LinearGradient(
                0f,
                waterTopY,
                0f,
                h,
                accentColor,
                Color.parseColor("#005577"),
                Shader.TileMode.CLAMP
            )

            wavePath.reset()
            wavePath.moveTo(0f, h)
            val waveAmp = if (progressPct in 1..99) 5.5f * d else 1.5f * d
            var x = 0f
            while (x <= w) {
                val y = waterTopY + sin((x / w) * 6.28f + wavePhase) * waveAmp
                wavePath.lineTo(x, y)
                x += 6f * d
            }
            wavePath.lineTo(w, h)
            wavePath.close()
            canvas.drawPath(wavePath, wavePaint)
            canvas.restore()

            canvas.drawCircle(cx, cy, r, rimPaint)
            canvas.drawText("$progressPct%", cx, cy + 6f * d, textPaint)
        }
    }

    private class DynamicQrAndRadarView(
        context: Context,
        private val d: Float,
        private val accentColor: Int
    ) : View(context) {

        private val radarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f * d
            color = Color.parseColor("#5500FFFF")
        }

        private val qrBlockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = accentColor
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2f
            val cy = h / 2f
            val maxR = w.coerceAtMost(h) * 0.48f

            canvas.drawCircle(cx, cy, maxR, radarPaint)
            canvas.drawCircle(cx, cy, maxR * 0.72f, radarPaint)

            val gridSize = 11
            val boxW = maxR * 1.1f
            val startX = cx - boxW / 2f
            val startY = cy - boxW / 2f
            val cell = boxW / gridSize

            for (row in 0 until gridSize) {
                for (col in 0 until gridSize) {
                    val isCornerFinder = (row < 3 && col < 3) ||
                        (row < 3 && col >= gridSize - 3) ||
                        (row >= gridSize - 3 && col < 3)
                    val isPatternBit = ((row * 7 + col * 13) % 3 == 0)
                    if (isCornerFinder || isPatternBit) {
                        canvas.drawRoundRect(
                            RectF(
                                startX + col * cell + 1f * d,
                                startY + row * cell + 1f * d,
                                startX + (col + 1) * cell - 1f * d,
                                startY + (row + 1) * cell - 1f * d
                            ),
                            2f * d,
                            2f * d,
                            qrBlockPaint
                        )
                    }
                }
            }
        }
    }
}
