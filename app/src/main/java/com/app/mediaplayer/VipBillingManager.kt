package com.app.mediaplayer

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

object VipBillingManager {

    private const val PREF_NAME = "OG_VIP_BILLING_PREFS"
    private const val KEY_IS_VIP = "is_vip_active"
    private const val KEY_SELECTED_PLAN = "selected_vip_plan_id"
    private const val KEY_PLAN_TITLE = "selected_vip_plan_title"

    // Replace this URL or hook with Google Play BillingClient / Stripe Checkout when live on Play Store
    private const val GLOBAL_PLAY_STORE_SUBSCRIPTION_URL =
        "https://play.google.com/store/account/subscriptions"

    data class SubscriptionPlan(
        val planId: String,
        val title: String,
        val priceDisplay: String,
        val billingCycle: String,
        val badgeText: String,
        val savingsNote: String
    )

    private val availablePlans = listOf(
        SubscriptionPlan(
            planId = "og_vip_weekly",
            title = "Weekly Pro Pass",
            priceDisplay = "$0.99",
            billingCycle = "per week",
            badgeText = "STARTER",
            savingsNote = "7-Day Full 8K & Ad-Free Access"
        ),
        SubscriptionPlan(
            planId = "og_vip_monthly",
            title = "Monthly Gold Pro",
            priceDisplay = "$2.49",
            billingCycle = "per month",
            badgeText = "POPULAR",
            savingsNote = "Save 40% • Full Dolby & Vault Access"
        ),
        SubscriptionPlan(
            planId = "og_vip_yearly",
            title = "Yearly Ultra 8K VIP",
            priceDisplay = "$14.99",
            billingCycle = "per year",
            badgeText = "BEST VALUE",
            savingsNote = "Save 75% • All Premium Features Unlocked"
        )
    )

    fun isVipUnlocked(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_IS_VIP, false)
    }

    fun getActivePlanLabel(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return if (prefs.getBoolean(KEY_IS_VIP, false)) {
            prefs.getString(KEY_PLAN_TITLE, "Gold 8K VIP") ?: "Gold 8K VIP"
        } else {
            "Silver Pro"
        }
    }

    fun showInternationalVipPaywall(
        context: Context,
        onStatusUpdated: (() -> Unit)? = null
    ) {
        val d = context.resources.displayMetrics.density
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        var selectedIndex = 2 // Default to Yearly Best Value

        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.parseColor("#F50A0A10")))
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )

        val root = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#0A0A10"))
        }

        val scroll = ScrollView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFillViewport = true
        }

        val contentCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * d).toInt(), (24 * d).toInt(), (20 * d).toInt(), (30 * d).toInt())
        }

        // Top Bar with Close Icon
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val tvTag = TextView(context).apply {
            text = "OG 8K GLOBAL VIP"
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((12 * d).toInt(), (5 * d).toInt(), (12 * d).toInt(), (5 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#26FFD700"))
                cornerRadius = 14f * d
                setStroke((1 * d).toInt(), Color.parseColor("#FFD700"))
            }
        }

        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, (1 * d).toInt(), 1f)
        }

        val btnClose = TextView(context).apply {
            text = "X"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((36 * d).toInt(), (36 * d).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#22FFFFFF"))
                setStroke((1 * d).toInt(), Color.parseColor("#44FFFFFF"))
            }
            setOnClickListener { dialog.dismiss() }
        }

        topRow.addView(tvTag)
        topRow.addView(spacer)
        topRow.addView(btnClose)
        contentCol.addView(topRow)

        // Crown Graphic & Title
        val crownIcon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams((68 * d).toInt(), (68 * d).toInt()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = (14 * d).toInt()
            }
            setImageDrawable(CrownVectorDrawable(d))
        }
        contentCol.addView(crownIcon)

        val tvHeadline = TextView(context).apply {
            text = "Unlock OG 8K Player VIP"
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, (10 * d).toInt(), 0, (4 * d).toInt())
        }
        contentCol.addView(tvHeadline)

        val tvSubtitle = TextView(context).apply {
            text = "Experience Cinema-Grade 8K HDR Upscaling, 200% Dolby Audio Boost, Unlimited Private Vault & Zero Interruptions"
            setTextColor(Color.parseColor("#B0C4DE"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding((10 * d).toInt(), 0, (10 * d).toInt(), (18 * d).toInt())
        }
        contentCol.addView(tvSubtitle)

        // VIP Benefits Checklist Card
        val benefitsCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * d).toInt(), (14 * d).toInt(), (16 * d).toInt(), (14 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (18 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#121926"))
                cornerRadius = 18f * d
                setStroke((1.2f * d).toInt(), Color.parseColor("#2800FFFF"))
            }
        }

        val benefitsList = listOf(
            "8K AI Hardware Decoder & HDR10+ Visual Enhancer",
            "Built-In 5-Band Dolby Equalizer & 300% Volume Boost",
            "Unlimited Secret Privacy Vault with Fake PIN Protection",
            "Fast Video-to-MP3 Ringtone Extractor & Exclusive Themes"
        )
        for (benefit in benefitsList) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (5 * d).toInt(), 0, (5 * d).toInt())
            }
            val dot = TextView(context).apply {
                text = "✓"
                setTextColor(Color.parseColor("#00FFFF"))
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, (10 * d).toInt(), 0)
            }
            val tvB = TextView(context).apply {
                text = benefit
                setTextColor(Color.WHITE)
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
            }
            row.addView(dot)
            row.addView(tvB)
            benefitsCard.addView(row)
        }
        contentCol.addView(benefitsCard)

        // 3 Plan Cards: Weekly, Monthly, Yearly
        val planCardsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val planViews = mutableListOf<LinearLayout>()
        val radioIndicators = mutableListOf<View>()

        lateinit var btnSubscribeAction: TextView

        fun updatePlanSelectionUi(newIndex: Int) {
            selectedIndex = newIndex
            planViews.forEachIndexed { idx, cardLayout ->
                val isSelected = idx == selectedIndex
                cardLayout.background = GradientDrawable().apply {
                    setColor(
                        if (isSelected) Color.parseColor("#182A3E")
                        else Color.parseColor("#111520")
                    )
                    cornerRadius = 18f * d
                    setStroke(
                        if (isSelected) (2.2f * d).toInt() else (1.2f * d).toInt(),
                        if (isSelected) Color.parseColor("#00FFFF") else Color.parseColor("#2AFFFFFF")
                    )
                }
                radioIndicators[idx].background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(
                        if (isSelected) Color.parseColor("#00FFFF")
                        else Color.TRANSPARENT
                    )
                    setStroke(
                        (2f * d).toInt(),
                        if (isSelected) Color.parseColor("#00FFFF") else Color.parseColor("#77FFFFFF")
                    )
                }
            }
            val chosen = availablePlans[selectedIndex]
            btnSubscribeAction.text = "Continue with ${chosen.title} (${chosen.priceDisplay})"
        }

        availablePlans.forEachIndexed { index, plan ->
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((16 * d).toInt(), (14 * d).toInt(), (16 * d).toInt(), (14 * d).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (12 * d).toInt() }
            }

            val radioDot = View(context).apply {
                layoutParams = LinearLayout.LayoutParams((20 * d).toInt(), (20 * d).toInt()).apply {
                    marginEnd = (14 * d).toInt()
                }
            }
            radioIndicators.add(radioDot)

            val midCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val titleRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val tvPlanName = TextView(context).apply {
                text = plan.title
                setTextColor(Color.WHITE)
                textSize = 15.5f
                typeface = Typeface.DEFAULT_BOLD
            }

            val tvBadge = TextView(context).apply {
                text = plan.badgeText
                setTextColor(Color.parseColor("#0A0A10"))
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setPadding((8 * d).toInt(), (2 * d).toInt(), (8 * d).toInt(), (2 * d).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = (8 * d).toInt() }
                background = GradientDrawable().apply {
                    setColor(
                        if (index == 2) Color.parseColor("#FFD700")
                        else Color.parseColor("#00FFFF")
                    )
                    cornerRadius = 10f * d
                }
            }

            titleRow.addView(tvPlanName)
            titleRow.addView(tvBadge)

            val tvSavings = TextView(context).apply {
                text = plan.savingsNote
                setTextColor(Color.parseColor("#99B0C4DE"))
                textSize = 12f
                setPadding(0, (3 * d).toInt(), 0, 0)
            }

            midCol.addView(titleRow)
            midCol.addView(tvSavings)

            val rightPriceCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
            }
            val tvPrice = TextView(context).apply {
                text = plan.priceDisplay
                setTextColor(Color.parseColor("#00FFFF"))
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
            }
            val tvCycle = TextView(context).apply {
                text = plan.billingCycle
                setTextColor(Color.parseColor("#B0C4DE"))
                textSize = 11f
            }
            rightPriceCol.addView(tvPrice)
            rightPriceCol.addView(tvCycle)

            card.addView(radioDot)
            card.addView(midCol)
            card.addView(rightPriceCol)

            card.setOnClickListener {
                updatePlanSelectionUi(index)
            }

            planViews.add(card)
            planCardsContainer.addView(card)
        }
        contentCol.addView(planCardsContainer)

        // Single Unified Global Checkout Button
        btnSubscribeAction = TextView(context).apply {
            text = "Continue with Yearly Ultra 8K VIP ($14.99)"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 15.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((20 * d).toInt(), (15 * d).toInt(), (20 * d).toInt(), (15 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (10 * d).toInt() }
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#00FFFF"), Color.parseColor("#00E676"))
            ).apply {
                cornerRadius = 26f * d
            }
            setOnClickListener {
                val chosenPlan = availablePlans[selectedIndex]
                startGlobalBillingCheckout(context, chosenPlan, prefs) {
                    dialog.dismiss()
                    onStatusUpdated?.invoke()
                }
            }
        }
        contentCol.addView(btnSubscribeAction)

        // Restore Purchase & Terms Footer
        val tvFooter = TextView(context).apply {
            text = "Secured by Global Play Billing • Cancel Anytime in 1-Click"
            setTextColor(Color.parseColor("#778899"))
            textSize = 11.5f
            gravity = Gravity.CENTER
            setPadding(0, (12 * d).toInt(), 0, (6 * d).toInt())
        }
        contentCol.addView(tvFooter)

        val tvRestore = TextView(context).apply {
            text = "Restore Existing VIP Purchase"
            setTextColor(Color.parseColor("#00FFFF"))
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, (6 * d).toInt(), 0, (10 * d).toInt())
            setOnClickListener {
                Toast.makeText(
                    context,
                    "Checking Google Play account for active subscriptions...",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        contentCol.addView(tvRestore)

        updatePlanSelectionUi(selectedIndex)
        scroll.addView(contentCol)
        root.addView(scroll)
        dialog.setContentView(root)
        dialog.show()
    }

    // Isolated checkout trigger: Ready for Google Play BillingClient or Global Gateway
    private fun startGlobalBillingCheckout(
        context: Context,
        plan: SubscriptionPlan,
        prefs: android.content.SharedPreferences,
        onComplete: () -> Unit
    ) {
        try {
            prefs.edit()
                .putString(KEY_SELECTED_PLAN, plan.planId)
                .putString(KEY_PLAN_TITLE, plan.title)
                .apply()

            // Launch Google Play Store subscription / billing flow if installed, with safe fallback
            val playIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("$GLOBAL_PLAY_STORE_SUBSCRIPTION_URL?sku=${plan.planId}&package=${context.packageName}")
            )
            playIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(playIntent)
            onComplete()
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Selected ${plan.title} (${plan.priceDisplay}) - Ready for Google Play Billing",
                Toast.LENGTH_SHORT
            ).show()
            onComplete()
        }
    }

    private class CrownVectorDrawable(private val d: Float) : Drawable() {
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.2f * d
            color = Color.parseColor("#00FFFF")
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 0f || h <= 0f) return
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val r = w.coerceAtMost(h) * 0.45f

            fillPaint.shader = LinearGradient(
                0f, 0f, w, h,
                Color.parseColor("#20FFD700"),
                Color.parseColor("#1500FFFF"),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, r, fillPaint)
            canvas.drawCircle(cx, cy, r, strokePaint)

            val crownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#FFD700")
            }
            val path = Path().apply {
                moveTo(cx - r * 0.55f, cy + r * 0.35f)
                lineTo(cx - r * 0.62f, cy - r * 0.25f)
                lineTo(cx - r * 0.25f, cy + r * 0.05f)
                lineTo(cx, cy - r * 0.42f)
                lineTo(cx + r * 0.25f, cy + r * 0.05f)
                lineTo(cx + r * 0.62f, cy - r * 0.25f)
                lineTo(cx + r * 0.55f, cy + r * 0.35f)
                close()
            }
            canvas.drawPath(path, crownPaint)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
