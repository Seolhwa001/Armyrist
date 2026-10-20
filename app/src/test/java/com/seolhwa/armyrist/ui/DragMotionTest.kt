package com.seolhwa.armyrist.ui

import org.junit.Assert.*
import org.junit.Test

class DragMotionTest {
    @Test fun shorterNeighbourDoesNotMoveGrabbedPoint() {
        val offset = DragMotion.compensation(0, 100, 107, 50, true)
        assertEquals(40f, 57f + 40f + offset, 0.001f)
    }
    @Test fun tallerNeighbourIsNotClampedToDraggedHeight() {
        val offset = DragMotion.compensation(0, 100, 107, 300, true)
        assertEquals(-307f, offset, 0.001f)
        assertEquals(80f, 307f + 80f + offset, 0.001f)
    }
    @Test fun upwardMoveKeepsSamePointWithDifferentHeights() {
        val offset = DragMotion.compensation(57, 100, 0, 50, false)
        assertEquals(37f, 0f - 20f + offset, 0.001f)
    }
    @Test fun longDistanceMoveKeepsFingerPosition() {
        assertEquals(-264f, DragMotion.compensation(0, 100, 164, 200, true), 0.001f)
    }
    @Test fun highRefreshDoesNotDoubleScrollSpeed() {
        fun distance(hz: Int): Float {
            var previous = 1_000_000_000L
            var total = 0f
            repeat(hz) { i ->
                val now = 1_000_000_000L + ((i + 1) * 1_000_000_000L / hz)
                total += 10f * DragMotion.frameScale(previous, now)
                previous = now
            }
            return total
        }
        assertEquals(600f, distance(30), 0.01f)
        assertEquals(distance(60), distance(120), 0.01f)
    }
    @Test fun resumeDoesNotCauseAWholeSecondJump() {
        assertEquals(0f, DragMotion.frameScale(0, 1_000_000_000), 0f)
        assertTrue(DragMotion.frameScale(1, 1_000_000_001) <= 3.01f)
        assertEquals(0f, DragMotion.frameScale(100, 99), 0f)
    }
}
