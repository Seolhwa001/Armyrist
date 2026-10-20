package com.seolhwa.armyrist.counting.reorder

import org.junit.Assert.*
import org.junit.Test

class CountingDragGeometryTest {
    private fun slot(index: Int, top: Float, height: Float) =
        CountingReorderSlot("id$index", index, 0f, top + height / 2f, 300f, height)

    @Test fun shortViewportCentreNeverStartsEitherAutoScrollDirection() {
        // The recorded screen has a large fixed summary and only a few visible rows.
        for (height in listOf(120f, 240f, 420f, 800f)) {
            for (percent in 20..80) {
                assertEquals(0f, CountingDragGeometry.edgeScrollFraction(height * percent / 100f, height, 336f), 0.0001f)
            }
        }
    }

    @Test fun shortViewportEdgeBandsHaveOppositeDirectionsAndNeutralCentre() {
        assertEquals(-1f, CountingDragGeometry.edgeScrollFraction(0f, 240f, 336f), 0f)
        assertEquals(0f, CountingDragGeometry.edgeScrollFraction(120f, 240f, 336f), 0f)
        assertEquals(1f, CountingDragGeometry.edgeScrollFraction(240f, 240f, 336f), 0f)
        assertEquals(-0.25f, CountingDragGeometry.edgeScrollFraction(24f, 240f, 336f), 0.0001f)
        assertEquals(0.25f, CountingDragGeometry.edgeScrollFraction(216f, 240f, 336f), 0.0001f)
    }

    @Test fun firstRowRemainsAtGrabPointAfterAnchorChangesAndVariableHeightSwaps() {
        val grab = 38f
        val pointer = 238f
        val overlayTop = CountingDragGeometry.overlayTop(pointer, grab)
        // Different post-swap positions, including LazyColumn retaining first key.
        for (placeholderOffset in listOf(0, 57, 307, -180)) {
            assertEquals(238f, overlayTop + grab, 0f)
            assertNotEquals(placeholderOffset.toFloat(), overlayTop)
        }
        // A stationary finger must not acquire any scroll/reorder correction.
        repeat(50) { assertEquals(overlayTop, CountingDragGeometry.overlayTop(pointer, grab), 0f) }
    }

    @Test fun paddingAndPartlyClippedRowUseSameHostCoordinates() {
        val rowTop = CountingDragGeometry.hostY(-24, 18)
        val grab = 30f - rowTop
        assertEquals(-6f, rowTop, 0f)
        assertEquals(rowTop, CountingDragGeometry.overlayTop(30f, grab), 0f)
        assertEquals(74f, CountingDragGeometry.overlayTop(110f, grab), 0f)
    }

    @Test fun longDragNeverJumpsToDistantVisibleSlot() {
        val slots = (0..8).map { slot(it, it * 106f, 100f) }
        assertEquals(1, CountingDragGeometry.adjacentTarget(0, 800f, 100f, 1, slots))
        assertEquals(7, CountingDragGeometry.adjacentTarget(8, 0f, 100f, -1, slots))
    }

    @Test fun upwardDragCannotReverseDownAfterLayoutChanges() {
        val slots = listOf(slot(0, 0f, 300f), slot(1, 306f, 70f), slot(2, 382f, 100f))
        // Even a neighbour overlapping the held centre must never select the opposite direction.
        assertNull(CountingDragGeometry.adjacentTarget(1, 450f, 70f, -1, slots))
        assertEquals(0, CountingDragGeometry.adjacentTarget(1, 120f, 70f, -1, slots))
        assertNull(CountingDragGeometry.adjacentTarget(1, 120f, 70f, 1, slots))
    }

    @Test fun crossingAndReturningHasSymmetricHysteresis() {
        val before = listOf(slot(0, 0f, 100f), slot(1, 106f, 100f))
        assertNull(CountingDragGeometry.adjacentTarget(0, 160f, 100f, 1, before))
        assertEquals(1, CountingDragGeometry.adjacentTarget(0, 165f, 100f, 1, before))
        assertNull(CountingDragGeometry.adjacentTarget(1, 45f, 100f, -1, before))
        assertEquals(0, CountingDragGeometry.adjacentTarget(1, 41f, 100f, -1, before))
        assertNull(CountingDragGeometry.adjacentTarget(0, 800f, 100f, 0, before))
    }

    @Test fun invalidViewportDoesNotScrollAndOutsidePointersAreBounded() {
        assertEquals(0f, CountingDragGeometry.edgeScrollFraction(Float.NaN, 240f, 112f), 0f)
        assertEquals(0f, CountingDragGeometry.edgeScrollFraction(50f, 0f, 112f), 0f)
        assertEquals(-1f, CountingDragGeometry.edgeScrollFraction(-1000f, 240f, 112f), 0f)
        assertEquals(1f, CountingDragGeometry.edgeScrollFraction(1000f, 240f, 112f), 0f)
    }
}
