package com.app.mediaplayer

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.LocaleList
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

object MultiLanguageHelper {

    private const val PREF_NAME = "HYBRID_LANGUAGE_PREFS"
    private const val KEY_LANG_CODE = "selected_language_code"

    data class LanguageItem(
        val code: String,
        val englishName: String,
        val regionGroup: String
    ) {
        fun getNativeDisplayName(): String {
            if (code == "auto") return "Auto-Detect System Language"
            return try {
                val loc = Locale.forLanguageTag(code)
                val native = loc.getDisplayName(loc)
                if (native.isNullOrBlank()) englishName else native.replaceFirstChar { it.uppercase() }
            } catch (_: Exception) {
                englishName
            }
        }
    }

    // 35+ Popular Indian & International Languages
    val supportedLanguages = listOf(
        LanguageItem("auto", "System Default (Auto-Detect)", "AUTO"),

        // Indian Languages (12)
        LanguageItem("hi", "Hindi (India)", "INDIAN"),
        LanguageItem("mr", "Marathi (India)", "INDIAN"),
        LanguageItem("ta", "Tamil (India)", "INDIAN"),
        LanguageItem("te", "Telugu (India)", "INDIAN"),
        LanguageItem("kn", "Kannada (India)", "INDIAN"),
        LanguageItem("ml", "Malayalam (India)", "INDIAN"),
        LanguageItem("bn", "Bengali (India)", "INDIAN"),
        LanguageItem("gu", "Gujarati (India)", "INDIAN"),
        LanguageItem("pa", "Punjabi (India)", "INDIAN"),
        LanguageItem("or", "Odia (India)", "INDIAN"),
        LanguageItem("as", "Assamese (India)", "INDIAN"),
        LanguageItem("ur", "Urdu (India)", "INDIAN"),

        // International Languages (24)
        LanguageItem("en-US", "English (American)", "GLOBAL"),
        LanguageItem("en-GB", "English (United Kingdom)", "GLOBAL"),
        LanguageItem("es", "Spanish (Espanol)", "GLOBAL"),
        LanguageItem("ru", "Russian (Russkiy)", "GLOBAL"),
        LanguageItem("zh-CN", "Chinese Simplified (Mandarin)", "GLOBAL"),
        LanguageItem("zh-TW", "Chinese Traditional (Taiwan)", "GLOBAL"),
        LanguageItem("ja", "Japanese (Nihongo)", "GLOBAL"),
        LanguageItem("ko", "Korean (Hanguk-eo)", "GLOBAL"),
        LanguageItem("ar", "Arabic (Al-Arabiyyah)", "GLOBAL"),
        LanguageItem("fr", "French (Francais)", "GLOBAL"),
        LanguageItem("de", "German (Deutsch)", "GLOBAL"),
        LanguageItem("pt", "Portuguese (Brasil / Portugal)", "GLOBAL"),
        LanguageItem("it", "Italian (Italiano)", "GLOBAL"),
        LanguageItem("tr", "Turkish (Turkce)", "GLOBAL"),
        LanguageItem("id", "Indonesian (Bahasa Indonesia)", "GLOBAL"),
        LanguageItem("vi", "Vietnamese (Tieng Viet)", "GLOBAL"),
        LanguageItem("th", "Thai (Phasa Thai)", "GLOBAL"),
        LanguageItem("nl", "Dutch (Nederlands)", "GLOBAL"),
        LanguageItem("pl", "Polish (Polski)", "GLOBAL"),
        LanguageItem("uk", "Ukrainian (Ukrayinska)", "GLOBAL"),
        LanguageItem("fa", "Persian (Farsi)", "GLOBAL"),
        LanguageItem("ms", "Malay (Bahasa Melayu)", "GLOBAL"),
        LanguageItem("fil", "Filipino (Tagalog)", "GLOBAL"),
        LanguageItem("sw", "Swahili (Kiswahili)", "GLOBAL")
    )

    fun getSavedLanguageCode(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANG_CODE, "auto") ?: "auto"
    }

    fun getCurrentLanguageDisplayName(context: Context): String {
        val code = getSavedLanguageCode(context)
        val item = supportedLanguages.firstOrNull { it.code == code } ?: supportedLanguages[0]
        return if (code == "auto") {
            val sysLoc = Locale.getDefault()
            "Auto (${sysLoc.displayLanguage})"
        } else {
            "${item.englishName} • ${item.getNativeDisplayName()}"
        }
    }

    fun applySavedLocale(context: Context): Context {
        val code = getSavedLanguageCode(context)
        val targetLocale = if (code == "auto") {
            Locale.getDefault()
        } else {
            try {
                Locale.forLanguageTag(code)
            } catch (_: Exception) {
                Locale.getDefault()
            }
        }

        Locale.setDefault(targetLocale)
        val res = context.resources
        val config = Configuration(res.configuration)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList = LocaleList(targetLocale)
            LocaleList.setDefault(localeList)
            config.setLocales(localeList)
        } else {
            @Suppress("DEPRECATION")
            config.locale = targetLocale
        }
        config.setLayoutDirection(targetLocale)

        @Suppress("DEPRECATION")
        res.updateConfiguration(config, res.displayMetrics)
        return context.createConfigurationContext(config)
    }

    fun setAndApplyLanguage(
        context: Context,
        languageCode: String,
        onApplied: (() -> Unit)? = null
    ) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANG_CODE, languageCode)
            .apply()

        applySavedLocale(context)
        onApplied?.invoke()
    }

    fun showLanguageSelectorDialog(
        context: Context,
        onLanguageChanged: () -> Unit
    ) {
        val d = context.resources.displayMetrics.density
        val theme = ThemeStudioHelper.getCurrentTheme(context)
        val accentColor = Color.parseColor(theme.accentHex)
        val currentCode = getSavedLanguageCode(context)

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
            setPadding(0, 0, 0, (10 * d).toInt())
        }

        val tvHeader = TextView(context).apply {
            text = "Multi-Language Engine (35+ Languages)"
            setTextColor(accentColor)
            textSize = 16.5f
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

        val etSearchLang = EditText(context).apply {
            hint = "Search language (Hindi, English, Russian, Tamil...)"
            setHintTextColor(Color.parseColor("#7F92A8"))
            setTextColor(Color.WHITE)
            textSize = 13.5f
            setSingleLine(true)
            setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (12 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141E30"))
                cornerRadius = 16f * d
                setStroke((1.2f * d).toInt(), accentColor)
            }
        }
        root.addView(etSearchLang)

        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (380 * d).toInt()
            )
        }

        val listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(listContainer)
        root.addView(scroll)

        fun populateLanguageRows(filterQuery: String) {
            listContainer.removeAllViews()
            val q = filterQuery.trim().lowercase(Locale.US)
            val filtered = if (q.isEmpty()) {
                supportedLanguages
            } else {
                supportedLanguages.filter {
                    it.englishName.lowercase(Locale.US).contains(q) ||
                        it.getNativeDisplayName().lowercase(Locale.US).contains(q) ||
                        it.code.lowercase(Locale.US).contains(q)
                }
            }

            filtered.forEach { lang ->
                val isSelected = lang.code == currentCode
                val card = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = (8 * d).toInt() }
                    background = GradientDrawable().apply {
                        setColor(if (isSelected) Color.parseColor("#1C2D46") else Color.parseColor("#121A26"))
                        cornerRadius = 14f * d
                        setStroke(
                            ((if (isSelected) 1.8f else 1.0f) * d).toInt(),
                            if (isSelected) accentColor else Color.parseColor("#22FFFFFF")
                        )
                    }
                    setOnClickListener {
                        setAndApplyLanguage(context, lang.code) {
                            dialog.dismiss()
                            onLanguageChanged()
                            (context as? Activity)?.recreate()
                            Toast.makeText(
                                context,
                                "Language Applied: ${lang.englishName}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                val leftCol = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                val tvTitle = TextView(context).apply {
                    text = if (isSelected) "✓  ${lang.englishName}" else lang.englishName
                    setTextColor(if (isSelected) accentColor else Color.WHITE)
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                }

                val tvNative = TextView(context).apply {
                    text = "${lang.getNativeDisplayName()}  •  ${lang.regionGroup}"
                    setTextColor(Color.parseColor("#9FB4CE"))
                    textSize = 11.5f
                    setPadding(0, (2 * d).toInt(), 0, 0)
                }

                leftCol.addView(tvTitle)
                leftCol.addView(tvNative)

                val tvBadge = TextView(context).apply {
                    text = lang.code.uppercase(Locale.US)
                    setTextColor(Color.parseColor("#0A0A10"))
                    textSize = 10.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding((8 * d).toInt(), (4 * d).toInt(), (8 * d).toInt(), (4 * d).toInt())
                    background = GradientDrawable().apply {
                        setColor(if (isSelected) accentColor else Color.parseColor("#88B0C4DE"))
                        cornerRadius = 10f * d
                    }
                }

                card.addView(leftCol)
                card.addView(tvBadge)
                listContainer.addView(card)
            }
        }

        etSearchLang.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                populateLanguageRows(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        populateLanguageRows("")
        dialog.show()
    }
}
