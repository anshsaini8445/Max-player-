package com.app.mediaplayer

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.cardview.widget.CardView

object PlayerVisualEnhancerHelper {

    fun populateVisualEnhancerCard(
        context: Context,
        cardPanel: CardView,
        initialMode: Int,
        initialApplyAll: Boolean,
        onClose: () -> Unit,
        onModeSelected: (Int) -> Unit,
        onApplyAllToggled: (Boolean) -> Unit
    ) {
        val d = context.resources.displayMetrics.density
        cardPanel.removeAllViews()

        val veScroll = ScrollView(context)
        val veContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt())
        }

        val tvVeTitle = TextView(context).apply {
            text = "←  Visual Enhancer"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { onClose() }
        }
        veContainer.addView(tvVeTitle)

        val modesGrid = GridLayout(context).apply {
            columnCount = 2
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (12 * d).toInt() }
        }

        val modeNames = arrayOf(
            "Original",
            "HDR",
            "Clear",
            "Ultra Clear",
            "Arctic Blue",
            "Warm Glow",
            "Cinematic"
        )
        val modePreviewColors = arrayOf(
            "#2A2A34",
            "#5E3524",
            "#225529",
            "#602825",
            "#154C79",
            "#7A4914",
            "#4A3728"
        )
        val modeCardViews = mutableListOf<CardView>()

        modeNames.forEachIndexed { index, name ->
            val card = CardView(context).apply {
                radius = 12f * d
                cardElevation = 2f * d
                setCardBackgroundColor(Color.parseColor(modePreviewColors[index]))
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = (68 * d).toInt()
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins((5 * d).toInt(), (5 * d).toInt(), (5 * d).toInt(), (5 * d).toInt())
                }
            }
            val box = FrameLayout(context)
            val tvName = TextView(context).apply {
                text = if (initialMode == index) "✓ $name" else name
                setTextColor(Color.WHITE)
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            box.addView(tvName)
            card.addView(box)
            card.setOnClickListener {
                onModeSelected(index)
                modeCardViews.forEachIndexed { idx, cv ->
                    val label = (cv.getChildAt(0) as FrameLayout).getChildAt(0) as TextView
                    label.text = if (idx == index) "✓ ${modeNames[idx]}" else modeNames[idx]
                }
            }
            modeCardViews.add(card)
            modesGrid.addView(card)
        }
        veContainer.addView(modesGrid)

        val applyAllRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((6 * d).toInt(), (14 * d).toInt(), (6 * d).toInt(), (4 * d).toInt())
        }
        val tvApplyAll = TextView(context).apply {
            text = "Apply to all"
            setTextColor(Color.WHITE)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val swApplyAll = Switch(context).apply {
            isChecked = initialApplyAll
            setOnCheckedChangeListener { _, isChecked ->
                onApplyAllToggled(isChecked)
            }
        }
        applyAllRow.addView(tvApplyAll)
        applyAllRow.addView(swApplyAll)
        veContainer.addView(applyAllRow)

        veScroll.addView(veContainer)
        cardPanel.addView(veScroll)
    }

    fun applyFilterToOverlay(
        modeIndex: Int,
        isNightMode: Boolean,
        btnHdrMode: TextView,
        viewVisualOverlay: View
    ) {
        when (modeIndex) {
            0 -> {
                btnHdrMode.setTextColor(Color.WHITE)
                viewVisualOverlay.setBackgroundColor(
                    if (isNightMode) Color.parseColor("#88000000") else Color.TRANSPARENT
                )
            }
            1 -> {
                btnHdrMode.setTextColor(Color.parseColor("#00E676"))
                viewVisualOverlay.setBackgroundColor(Color.parseColor("#22FF9800"))
            }
            2 -> {
                btnHdrMode.setTextColor(Color.parseColor("#00E676"))
                viewVisualOverlay.setBackgroundColor(Color.parseColor("#1800E5FF"))
            }
            3 -> {
                btnHdrMode.setTextColor(Color.parseColor("#00E676"))
                viewVisualOverlay.setBackgroundColor(Color.parseColor("#24FF4081"))
            }
            4 -> {
                btnHdrMode.setTextColor(Color.parseColor("#00E676"))
                viewVisualOverlay.setBackgroundColor(Color.parseColor("#2800B0FF"))
            }
            5 -> {
                btnHdrMode.setTextColor(Color.parseColor("#00E676"))
                viewVisualOverlay.setBackgroundColor(Color.parseColor("#30FFAB00"))
            }
            6 -> {
                btnHdrMode.setTextColor(Color.parseColor("#00E676"))
                viewVisualOverlay.setBackgroundColor(Color.parseColor("#2CD4AF37"))
            }
        }
    }
}
