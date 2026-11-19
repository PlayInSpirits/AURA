package com.eddyizm.tempus.ui.view

import com.eddyizm.tempus.ui.view.AuraTrackGestures.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class AuraTrackGesturesTest {
    @Test
    fun swipingEitherWayAdvances() {
        assertEquals(Action.NEXT, classify(-60f, 8f))
        assertEquals(Action.NEXT, classify(60f, -8f))
    }

    @Test
    fun downPausesAndUpIsAvailableForScrolling() {
        assertEquals(Action.PAUSE, classify(8f, 60f))
        assertEquals(Action.NONE, classify(8f, -60f))
    }

    @Test
    fun tapsAndIncompleteGesturesDoNotChangeTracks() {
        assertEquals(Action.TAP, classify(4f, 6f))
        assertEquals(Action.NONE, classify(39f, 2f))
        assertEquals(Action.NONE, classify(60f, 60f))
    }

    private fun classify(dx: Float, dy: Float) = AuraTrackGestures.classify(dx, dy, 8f, 40f)
}
