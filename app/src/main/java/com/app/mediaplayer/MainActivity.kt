package com.app.mediaplayer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class MainActivity : AppCompatActivity() {

    private lateinit var rootMainLayout: RelativeLayout
    private lateinit var topBrandBar: LinearLayout
    private lateinit var tvAppBrandName: TextView
    private lateinit var btnTopQuickSearch: ImageView
    private lateinit var btnTopVaultQuick: ImageView

    private lateinit var navTabVideo: LinearLayout
    private lateinit var navTabAudio: LinearLayout
    private lateinit var navTabSearch: LinearLayout
    private lateinit var navTabMe: LinearLayout

    private lateinit var imgNavVideo: ImageView
    private lateinit var imgNavAudio: ImageView
    private lateinit var imgNavSearch: ImageView
    private lateinit var imgNavMe: ImageView

    private lateinit var tvNavVideo: TextView
    private lateinit var tvNavAudio: TextView
    private lateinit var tvNavSearch: TextView
    private lateinit var tvNavMe: TextView

    private var videoFragment: VideoFragment? = null
    private var audioFragment: AudioFragment? = null
    private var searchFragment: SearchFragment? = null
    private var meFragment: MeFragment? = null

    private var activeTabIndex = 0
    private var activeAccentColor = Color.parseColor("#00FFFF")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.statusBarColor = Color.parseColor("#0A0A10")
        window.navigationBarColor = Color.parseColor("#101018")

        bindViews()
        requestNeededPermissions()
        setupBottomNavigation()
        switchTab(0)
    }

    override fun onResume() {
        super.onResume()
        applySavedThemeColors()
    }

    private fun bindViews() {
        rootMainLayout = findViewById(R.id.rootMainLayout)
        topBrandBar = findViewById(R.id.topBrandBar)
        tvAppBrandName = findViewById(R.id.tvAppBrandName)
        btnTopQuickSearch = findViewById(R.id.btnTopQuickSearch)
        btnTopVaultQuick = findViewById(R.id.btnTopVaultQuick)

        navTabVideo = findViewById(R.id.navTabVideo)
        navTabAudio = findViewById(R.id.navTabAudio)
        navTabSearch = findViewById(R.id.navTabSearch)
        navTabMe = findViewById(R.id.navTabMe)

        imgNavVideo = findViewById(R.id.imgNavVideo)
        imgNavAudio = findViewById(R.id.imgNavAudio)
        imgNavSearch = findViewById(R.id.imgNavSearch)
        imgNavMe = findViewById(R.id.imgNavMe)

        tvNavVideo = findViewById(R.id.tvNavVideo)
        tvNavAudio = findViewById(R.id.tvNavAudio)
        tvNavSearch = findViewById(R.id.tvNavSearch)
        tvNavMe = findViewById(R.id.tvNavMe)

        btnTopQuickSearch.setOnClickListener {
            switchTab(2)
        }

        btnTopVaultQuick.setOnClickListener {
            try {
                startActivity(Intent(this, VaultPinActivity::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setupBottomNavigation() {
        navTabVideo.setOnClickListener { switchTab(0) }
        navTabAudio.setOnClickListener { switchTab(1) }
        navTabSearch.setOnClickListener { switchTab(2) }
        navTabMe.setOnClickListener { switchTab(3) }
    }

    fun switchTab(index: Int) {
        activeTabIndex = index
        try {
            val targetFragment: Fragment = when (index) {
                0 -> {
                    if (videoFragment == null) videoFragment = VideoFragment()
                    videoFragment!!
                }
                1 -> {
                    if (audioFragment == null) audioFragment = AudioFragment()
                    audioFragment!!
                }
                2 -> {
                    if (searchFragment == null) searchFragment = SearchFragment()
                    searchFragment!!
                }
                else -> {
                    if (meFragment == null) meFragment = MeFragment()
                    meFragment!!
                }
            }

            topBrandBar.visibility = if (index == 3) View.GONE else View.VISIBLE

            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, targetFragment)
                .commitAllowingStateLoss()

            updateBottomNavVisuals()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applySavedThemeColors() {
        try {
            val prefs = getSharedPreferences("MAX_THEME_PREFS", Context.MODE_PRIVATE)
            val bgHex = prefs.getString("app_bg_color", "#0A0A10") ?: "#0A0A10"
            val accentHex = prefs.getString("app_accent_color", "#00FFFF") ?: "#00FFFF"
            val bgColor = Color.parseColor(bgHex)
            activeAccentColor = Color.parseColor(accentHex)

            rootMainLayout.setBackgroundColor(bgColor)
            topBrandBar.setBackgroundColor(bgColor)
            btnTopQuickSearch.setColorFilter(activeAccentColor)
            updateBottomNavVisuals()
        } catch (e: Exception) {
            activeAccentColor = Color.parseColor("#00FFFF")
        }
    }

    private fun updateBottomNavVisuals() {
        val inactiveColor = Color.parseColor("#888899")

        val vColor = if (activeTabIndex == 0) activeAccentColor else inactiveColor
        val aColor = if (activeTabIndex == 1) activeAccentColor else inactiveColor
        val sColor = if (activeTabIndex == 2) activeAccentColor else inactiveColor
        val mColor = if (activeTabIndex == 3) activeAccentColor else inactiveColor

        imgNavVideo.setColorFilter(vColor)
        tvNavVideo.setTextColor(vColor)

        imgNavAudio.setColorFilter(aColor)
        tvNavAudio.setTextColor(aColor)

        imgNavSearch.setColorFilter(sColor)
        tvNavSearch.setTextColor(sColor)

        imgNavMe.setColorFilter(mColor)
        tvNavMe.setTextColor(mColor)
    }

    private fun requestNeededPermissions() {
        try {
            val permissionsToRequest = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                ) {
                    permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }

            if (permissionsToRequest.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 1001)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (activeTabIndex != 0) {
            switchTab(0)
        } else {
            super.onBackPressed()
        }
    }
}
