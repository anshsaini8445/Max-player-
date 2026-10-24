package com.app.mediaplayer

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

object VipBillingManager {

    private const val PREF_NAME = "HYBRID_VIP_PREFS"
    private const val KEY_IS_VIP = "is_vip_unlocked"
    private const val KEY_ACTIVE_PLAN = "vip_active_plan_name"

    data class VipPlan(
        val id: String,
        val title: String,
        val priceText: String,
        val cycleSubtitle: String,
        val badgeText: String
    )

    private val plans = listOf(
        VipPlan(
            id = "weekly_vip",
            title = "Weekly VIP Pass",
            priceText = "Rs 99 / $1.49",
            cycleSubtitle = "7 Days Full 8K HDR & Dolby Access",
            badgeText = "STARTER"
        ),
        VipPlan(
            id = "monthly_vip",
            title = "Monthly Pro Pass",
            priceText = "Rs 249 / $2.99",
            cycleSubtitle = "30 Days Ad-Free + 200% Volume Boost",
            badgeText = "POPULAR"
        ),
        VipPlan(
            id = "yearly_vip",
            title = "Yearly Ultimate Pass",
            priceText = "Rs 999 / $11.99",
            cycleSubtitle = "365 Days All-Access + Save 70%",
            badgeText = "BEST VALUE"
        )
    )

    fun isVipActive(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_IS_VIP, false)
    }

    fun getActivePlanLabel(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_IS_VIP, false)) return "FREE MEMBER"
        return prefs.getString(KEY_ACTIVE_PLAN, "VIP PRO") ?: "VIP PRO"
    }

    fun showInternationalVipPaywall(
        context: Context,
        onSubscriptionUpdated: () -> Unit = {}
    ) {
        val d = context.resources.displayMetrics.density
        var selectedIndex = 2

        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val rootCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * d).toInt(), (20 * d).toInt(), (20 * d).toInt(), (18 * d).toInt())
            layoutParams = ViewGroup.LayoutParams(
                (context.resources.displayMetrics.widthPixels * 0.94f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.parseColor("#131C2E"),
                    Color.parseColor("#0A0A10")
                )
            ).apply {
                cornerRadius = 26f * d
                setStroke((1.8f * d).toInt(), Color.parseColor("#FFD700"))
            }
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (10 * d).toInt())
        }

        val tvCrownTitle = TextView(context).apply {
            text = "HYBRID PLAYER 8K VIP"
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 18.5f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnClose = TextView(context).apply {
            text = "X"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((32 * d).toInt(), (32 * d).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#26FFFFFF"))
            }
            setOnClickListener { dialog.dismiss() }
        }

        headerRow.addView(tvCrownTitle)
        headerRow.addView(btnClose)
        rootCard.addView(headerRow)

        val tvPerks = TextView(context).apply {
            text = "• 8K AI HDR Visual Upscaler & AMOLED Filters\n" +
                "• 200% Super Loud Dolby Audio & Pitch Studio\n" +
                "• Secret PIN Privacy Vault & Unlimited MP3 Extractor\n" +
                "• 100% Ad-Free Cinema Experience Worldwide"
            setTextColor(Color.parseColor("#D8E6F8"))
            textSize = 12.5f
            setLineSpacing(4f * d, 1f)
            setPadding((4 * d).toInt(), (2 * d).toInt(), (4 * d).toInt(), (14 * d).toInt())
        }
        rootCard.addView(tvPerks)

        val scroll = ScrollView(context)
        val plansContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val planCards = mutableListOf<LinearLayout>()
        lateinit var btnContinueSubscribe: TextView

        fun updatePlanHighlight(newIdx: Int) {
            selectedIndex = newIdx
            planCards.forEachIndexed { idx, cardLayout ->
                val isSelected = idx == selectedIndex
                cardLayout.background = GradientDrawable().apply {
                    setColor(
                        if (isSelected) Color.parseColor("#1D2C44")
                        else Color.parseColor("#121824")
                    )
                    cornerRadius = 18f * d
                    setStroke(
                        ((if (isSelected) 2.0f else 1.0f) * d).toInt(),
                        if (isSelected) Color.parseColor("#00FFFF") else Color.parseColor("#2AFFFFFF")
                    )
                }
            }
            val chosen = plans[selectedIndex]
            btnContinueSubscribe.text = "CONTINUE WITH ${chosen.title.uppercase()} (${chosen.priceText})"
        }

        plans.forEachIndexed { index, plan ->
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((16 * d).toInt(), (14 * d).toInt(), (16 * d).toInt(), (14 * d).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (10 * d).toInt() }
                setOnClickListener {
                    updatePlanHighlight(index)
                }
            }

            val leftCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvPlanTitle = TextView(context).apply {
                text = plan.title
                setTextColor(Color.WHITE)
                textSize = 15.5f
                typeface = Typeface.DEFAULT_BOLD
            }

            val tvPlanSub = TextView(context).apply {
                text = plan.cycleSubtitle
                setTextColor(Color.parseColor("#9FB4CE"))
                textSize = 11.5f
                setPadding(0, (3 * d).toInt(), 0, 0)
            }

            leftCol.addView(tvPlanTitle)
            leftCol.addView(tvPlanSub)

            val rightCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
            }

            val tvBadge = TextView(context).apply {
                text = plan.badgeText
                setTextColor(Color.parseColor("#0A0A10"))
                textSize = 9.5f
                typeface = Typeface.DEFAULT_BOLD
                setPadding((8 * d).toInt(), (3 * d).toInt(), (8 * d).toInt(), (3 * d).toInt())
                background = GradientDrawable().apply {
                    setColor(
                        if (index == 2) Color.parseColor("#FFD700")
                        else Color.parseColor("#00FFFF")
                    )
                    cornerRadius = 10f * d
                }
            }

            val tvPrice = TextView(context).apply {
                text = plan.priceText
                setTextColor(Color.parseColor("#00FFFF"))
                textSize = 14.5f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, (5 * d).toInt(), 0, 0)
            }

            rightCol.addView(tvBadge)
            rightCol.addView(tvPrice)

            card.addView(leftCol)
            card.addView(rightCol)
            planCards.add(card)
            plansContainer.addView(card)
        }

        scroll.addView(plansContainer)
        rootCard.addView(scroll)

        btnContinueSubscribe = TextView(context).apply {
            text = "SUBSCRIBE NOW"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((16 * d).toInt(), (14 * d).toInt(), (16 * d).toInt(), (14 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (8 * d).toInt() }
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.parseColor("#FFD700"),
                    Color.parseColor("#00FFFF")
                )
            ).apply {
                cornerRadius = 22f * d
            }
            setOnClickListener {
                val chosen = plans[selectedIndex]
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_IS_VIP, true)
                    .putString(KEY_ACTIVE_PLAN, chosen.title.uppercase())
                    .apply()
                dialog.dismiss()
                onSubscriptionUpdated()
                Toast.makeText(
                    context,
                    "Activated: ${chosen.title}! All Hybrid Player VIP Features Unlocked.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        rootCard.addView(btnContinueSubscribe)

        val btnRestore = TextView(context).apply {
            text = "Restore Google Play Purchase  |  Cancel Anytime"
            setTextColor(Color.parseColor("#88B0C4DE"))
            textSize = 11.5f
            gravity = Gravity.CENTER
            setPadding(0, (12 * d).toInt(), 0, (2 * d).toInt())
            setOnClickListener {
                Toast.makeText(context, "Checking Google Play purchase history...", Toast.LENGTH_SHORT).show()
            }
        }
        rootCard.addView(btnRestore)

        updatePlanHighlight(2)
        dialog.setContentView(rootCard)
        dialog.show()
    }
}
