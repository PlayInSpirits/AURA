package com.eddyizm.tempus.ui.view

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WavePaletteTransitionTest {
    @Test
    fun differentCoversBlendOverMoreThanOneSecond() {
        val previous = intArrayOf(0xFF401040.toInt(), 0xFF103050.toInt())
        val next = intArrayOf(0xFF2050C0.toInt(), 0xFFC03010.toInt())
        val palette = WavePaletteTransition(previous)
        palette.retarget(next)
        assertArrayEquals(previous, palette.colors)
        palette.advance(1f)
        assertTrue(palette.isTransitioning)
        assertFalse(palette.colors.contentEquals(previous))
        assertFalse(palette.colors.contentEquals(next))
        palette.advance(1f)
        assertArrayEquals(next, palette.colors)
        assertFalse(palette.isTransitioning)
    }

    @Test
    fun switchingAgainContinuesFromTheDisplayedColors() {
        val palette = WavePaletteTransition(intArrayOf(0xFF000000.toInt()))
        palette.retarget(intArrayOf(0xFFFFFFFF.toInt()))
        palette.advance(.7f)
        val displayed = palette.colors.copyOf()
        val replacement = intArrayOf(0xFF8833CC.toInt())
        palette.retarget(replacement)
        assertArrayEquals(displayed, palette.colors)
        palette.advance(.2f)
        assertFalse(palette.colors.contentEquals(displayed))
        palette.retarget(replacement)
        palette.advance(1.7f)
        assertArrayEquals(replacement, palette.colors)
        assertFalse(palette.isTransitioning)
    }
}
