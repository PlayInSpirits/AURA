package com.eddyizm.tempus.ui.view

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

class AuraTrackGestures(
    private val canSwipe: () -> Boolean,
    private val next: () -> Unit,
    private val pause: () -> Unit,
) : View.OnTouchListener {
    private var tracking = false
    private var startX = 0f
    private var startY = 0f

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tracking = canSwipe()
                if (!tracking) return false
                startX = event.x
                startY = event.y
                view.isPressed = true
                view.parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return false
                val dx = event.x - startX
                val dy = event.y - startY
                val slop = ViewConfiguration.get(view.context).scaledTouchSlop
                if (abs(dx) > slop || abs(dy) > slop) view.isPressed = false
                if (dy < -slop && -dy > abs(dx)) view.parent.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_UP -> {
                if (!tracking) return false
                val slop = ViewConfiguration.get(view.context).scaledTouchSlop
                val min = 40f * view.resources.displayMetrics.density
                when (classify(event.x - startX, event.y - startY, slop.toFloat(), min)) {
                    Action.NEXT -> next()
                    Action.PAUSE -> pause()
                    Action.TAP -> view.performClick()
                    Action.NONE -> Unit
                }
                finish(view)
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> {
                if (!tracking) return false
                finish(view)
                return true
            }
        }
        return tracking || event.actionMasked == MotionEvent.ACTION_UP
    }

    private fun finish(view: View) {
        tracking = false
        view.isPressed = false
        view.parent?.requestDisallowInterceptTouchEvent(false)
    }

    internal enum class Action { TAP, NEXT, PAUSE, NONE }

    internal companion object {
        fun classify(dx: Float, dy: Float, slop: Float, min: Float): Action = when {
            abs(dx) <= slop && abs(dy) <= slop -> Action.TAP
            abs(dx) >= min && abs(dx) > 1.15f * abs(dy) -> Action.NEXT
            dy >= min && dy > 1.15f * abs(dx) -> Action.PAUSE
            else -> Action.NONE
        }
    }
}
