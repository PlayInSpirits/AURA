package com.eddyizm.tempus.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.animation.OvershootInterpolator
import androidx.core.widget.NestedScrollView

/**
 * AURA: Home's scroller. The first screen is the "My Wave" hero ([snapHeight] tall); the rest of
 * Home waits below it. A pull on the hero does not stop half way: on release the page springs either
 * back to the wave or down to the sections, overshooting a little and settling, like a sheet.
 * Below the hero it scrolls as usual.
 */
class AuraSnapScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.core.R.attr.nestedScrollViewStyle,
) : NestedScrollView(context, attrs, defStyleAttr) {

    /** Height of the hero, 0 = no snapping. */
    var snapHeight: Int = 0

    /** Called with the scroll position on every change (also while springing). */
    var onScrolled: ((Int) -> Unit)? = null

    private var snapAnimator: ValueAnimator? = null
    private var dragging = false
    private var flung = false

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        onScrolled?.invoke(t)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            snapAnimator?.cancel()
            dragging = true
            flung = false
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                snapAnimator?.cancel()
                dragging = true
                flung = false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val handled = super.onTouchEvent(ev)
                dragging = false
                // A slow release (no fling) inside the hero: settle on the nearest side.
                if (!flung) post { settle(0) }
                return handled
            }
        }
        return super.onTouchEvent(ev)
    }

    override fun fling(velocityY: Int) {
        flung = true
        if (snapHeight > 0 && scrollY in 1 until snapHeight) {
            settle(velocityY)
            return
        }
        super.fling(velocityY)
    }

    /** Springs to the wave (0) or to the sections ([snapHeight]) unless already outside the hero. */
    private fun settle(velocityY: Int) {
        val h = snapHeight
        val y = scrollY
        if (h <= 0 || dragging || y <= 0 || y >= h) return
        val target = when {
            velocityY > 600 -> h
            velocityY < -600 -> 0
            y > h * 0.18f -> h
            else -> 0
        }
        snapTo(target)
    }

    fun snapTo(target: Int) {
        snapAnimator?.cancel()
        val from = scrollY
        if (from == target) return
        snapAnimator = ValueAnimator.ofInt(from, target).apply {
            duration = 620L
            interpolator = OvershootInterpolator(0.9f)
            addUpdateListener { scrollTo(0, it.animatedValue as Int) }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        snapAnimator?.cancel()
        super.onDetachedFromWindow()
    }
}
