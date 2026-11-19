package com.eddyizm.tempus.ui.view

import kotlin.math.roundToInt

internal class WavePaletteTransition(initial: IntArray) {
    val colors = initial.copyOf()
    private val start = initial.copyOf()
    private var target = initial.copyOf()
    private var progress = 1f

    val isTransitioning: Boolean get() = progress < 1f

    fun retarget(next: IntArray) {
        if (target.contentEquals(next)) return
        colors.copyInto(start)
        target = next.copyOf()
        progress = if (colors.contentEquals(target)) 1f else 0f
    }

    fun advance(elapsedSeconds: Float) {
        if (!isTransitioning) return
        progress = (progress + elapsedSeconds / DURATION_SECONDS).coerceIn(0f, 1f)
        val eased = progress * progress * (3f - 2f * progress)
        for (index in colors.indices) {
            colors[index] = blend(start[index], target[index], eased)
        }
    }

    fun finish() {
        target.copyInto(colors)
        progress = 1f
    }

    private fun blend(from: Int, to: Int, progress: Float): Int {
        fun channel(shift: Int): Int {
            val start = (from ushr shift) and 0xFF
            val end = (to ushr shift) and 0xFF
            return (start + (end - start) * progress).roundToInt() shl shift
        }
        return channel(24) or channel(16) or channel(8) or channel(0)
    }

    private companion object {
        const val DURATION_SECONDS = 1.85f
    }
}
