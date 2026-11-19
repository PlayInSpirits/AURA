package com.eddyizm.tempus.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
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
    private var touchActive = false
    private var intercepted = false
    private var startScroll = 0
    private var startY = 0f
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        onScrolled?.invoke(t)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            snapAnimator?.cancel()
            touchActive = true
            intercepted = false
            startScroll = scrollY
            startY = ev.y
        }
        val handled = super.dispatchTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            touchActive = false
            val h = snapHeight
            val delta = startY - ev.y
            if (h > 0 && intercepted) {
                val target = when {
                    startScroll < slop && (scrollY >= h * .18f || delta >= h * .18f) -> h
                    startScroll < slop -> 0
                    startScroll in h - slop..h + slop && delta < -slop -> 0
                    scrollY in 1 until h -> if (delta < 0) 0 else h
                    else -> null
                }
                target?.let(::snapTo)
            }
        }
        return handled
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean =
        super.onInterceptTouchEvent(ev).also {
            if (it && ev.actionMasked == MotionEvent.ACTION_MOVE) intercepted = true
        }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_MOVE && kotlin.math.abs(startY - ev.y) > slop) intercepted = true
        return super.onTouchEvent(ev)
    }

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        val preview = if (touchActive && startScroll < slop && snapHeight > 0)
            scrollY.coerceAtMost((snapHeight * .29f).toInt()) else scrollY
        super.onOverScrolled(scrollX, preview, clampedX, clampedY)
    }

    override fun fling(velocityY: Int) {
        if (touchActive && snapHeight > 0 &&
            (startScroll < slop || scrollY < snapHeight || (startScroll <= snapHeight + slop && velocityY < 0))) return
        super.fling(velocityY)
    }

    fun snapTo(target: Int) {
        snapAnimator?.cancel()
        val from = scrollY
        if (from == target) return
        snapAnimator = ValueAnimator.ofInt(from, target).apply {
            duration = 430L
            interpolator = OvershootInterpolator(0.7f)
            addUpdateListener { scrollTo(0, it.animatedValue as Int) }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        snapAnimator?.cancel()
        super.onDetachedFromWindow()
    }
}
