package com.app.mediaplayer

import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView

object InShotScrollAnimator {

    private val interpolator = DecelerateInterpolator(1.4f)

    // Applies InShot Music Player style bottom-to-top smooth expand animation on every row
    fun animateRowOnBind(itemView: View) {
        itemView.scaleX = 0.88f
        itemView.scaleY = 0.88f
        itemView.alpha = 0.72f
        itemView.translationY = 28f

        itemView.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .alpha(1.0f)
            .translationY(0f)
            .setDuration(240L)
            .setInterpolator(interpolator)
            .start()
    }

    // Attaches continuous dynamic scaling so bottom rows look slightly compact and expand as they move up
    fun attachToRecyclerView(recyclerView: RecyclerView) {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                val rvHeight = rv.height.toFloat().coerceAtLeast(1f)
                for (i in 0 until rv.childCount) {
                    val child = rv.getChildAt(i) ?: continue
                    val childCenterY = (child.top + child.bottom) / 2f
                    val bottomFraction = (childCenterY / rvHeight).coerceIn(0f, 1f)
                    // Items near the bottom edge are slightly narrower (0.91f) and expand to 1.0f as scrolled up
                    val targetScale = if (bottomFraction > 0.78f) {
                        val factor = (bottomFraction - 0.78f) / 0.22f
                        (1.0f - (0.09f * factor)).coerceIn(0.90f, 1.0f)
                    } else {
                        1.0f
                    }
                    child.scaleX = targetScale
                    child.scaleY = targetScale
                }
            }
        })
    }
}
