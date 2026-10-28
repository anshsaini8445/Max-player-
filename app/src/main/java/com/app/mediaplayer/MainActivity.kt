package com.app.mediaplayer

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class MainActivity : AppCompatActivity() {

    companion object {
        private var hasShownOpeningSplash = false
    }

    private lateinit var rootMainLayout: RelativeLayout
    private lateinit var topBrandBar: LinearLayout
    private lateinit var bottomNavBar: LinearLayout
    private lateinit var tvAppBrandName: TextView
    private lateinit var imgBrandStarBadge: ImageView
    private lateinit var layoutOpeningSplash: LinearLayout

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

        bindViews()
        applySavedThemeColors()
        handleOpeningSplash()
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
        bottomNavBar = findViewById(R.id.bottomNavBar)
        tvAppBrandName = findViewById(R.id.tvAppBrandName)
        imgBrandStarBadge = findViewById(R.id.imgBrandStarBadge)
        layoutOpeningSplash = findViewById(R.id.layoutOpeningSplash)

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

        val prefs = getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        val savedBrand = prefs.getString("custom_brand_title", "OG Player") ?: "OG Player"
        val cleanBrand = if (savedBrand.contains("MAX", ignoreCase = true)) "OG Player" else savedBrand
        tvAppBrandName.text = cleanBrand

        val showStar = prefs.getBoolean("show_brand_star", true)
        imgBrandStarBadge.visibility = if (showStar) View.VISIBLE else View.GONE

        val brandEditClick = View.OnLongClickListener {
            val input = EditText(this).apply {
                setText(tvAppBrandName.text.toString())
                setTextColor(Color.BLACK)
                setPadding(40, 30, 40, 30)
            }
            AlertDialog.Builder(this)
                .setTitle("Customize Header Name & Star")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val newName = input.text.toString().trim().ifEmpty { "OG Player" }
                    prefs.edit().putString("custom_brand_title", newName).apply()
                    tvAppBrandName.text = newName
                }
                .setNeutralButton(if (imgBrandStarBadge.visibility == View.VISIBLE) "Hide Star" else "Show Star") { _, _ ->
                    val nextVis = imgBrandStarBadge.visibility != View.VISIBLE
                    prefs.edit().putBoolean("show_brand_star", nextVis).apply()
                    imgBrandStarBadge.visibility = if (nextVis) View.VISIBLE else View.GONE
                }
                .setNegativeButton("Cancel", null)
                .show()
            true
        }

        tvAppBrandName.setOnLongClickListener(brandEditClick)
        imgBrandStarBadge.setOnLongClickListener(brandEditClick)
    }

    private fun handleOpeningSplash() {
        if (hasShownOpeningSplash) {
            layoutOpeningSplash.visibility = View.GONE
            return
        }
        hasShownOpeningSplash = true
        layoutOpeningSplash.visibility = View.VISIBLE
        Handler(Looper.getMainLooper()).postDelayed({
            layoutOpeningSplash.animate()
                .alpha(0f)
                .setDuration(280)
                .withEndAction {
                    layoutOpeningSplash.visibility = View.GONE
                    layoutOpeningSplash.alpha = 1f
                }
                .start()
        }, 900)
    }

    private fun setupBottomNavigation() {
        navTabVideo.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            switchTab(0)
        }
        navTabAudio.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            switchTab(1)
        }
        navTabSearch.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            switchTab(2)
        }
        navTabMe.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            switchTab(3)
        }
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

            // Hide bulky top brand bar on Music, Search, and Me tabs to maximize list space
            topBrandBar.visibility = if (index == 0) View.VISIBLE else View.GONE

            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, targetFragment)
                .commitAllowingStateLoss()

            applySavedThemeColors()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun applySavedThemeColors() {
        try {
            val prefs = getSharedPreferences("MAX_THEME_PREFS", Context.MODE_PRIVATE)
            val bgHex = prefs.getString("app_bg_color", "#0A0A10") ?: "#0A0A10"
            val navHex = prefs.getString("app_nav_color", "#121622") ?: "#121622"
            val accentHex = prefs.getString("app_accent_color", "#00FFFF") ?: "#00FFFF"

            val bgColor = Color.parseColor(bgHex)
            val navColor = Color.parseColor(navHex)
            activeAccentColor = Color.parseColor(accentHex)

            rootMainLayout.setBackgroundColor(bgColor)
            topBrandBar.setBackgroundColor(bgColor)
            bottomNavBar.setBackgroundColor(navColor)
            layoutOpeningSplash.setBackgroundColor(bgColor)
            imgBrandStarBadge.setColorFilter(activeAccentColor)

            window.statusBarColor = bgColor
            window.navigationBarColor = navColor

            updateBottomNavVisuals()
        } catch (e: Exception) {
            activeAccentColor = Color.parseColor("#00FFFF")
        }
    }

    private fun updateBottomNavVisuals() {
        val inactiveColor = Color.parseColor("#8A99B5")
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
