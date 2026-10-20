package com.seolhwa.armyrist.counting.reorder

import com.seolhwa.armyrist.domain.CountingItem
import org.junit.Assert.*
import org.junit.Test

class CountingReorderPerformanceTest {
    private val original = List(2000) { i ->
        CountingItem(id = "i$i", sheetId = "s", name = "항목$i", quantity = i, unit = "개", order = i)
    }
    private fun state() = CountingReorderV2(original) { 1_000L }
    @Test fun repeatedPointerLookupsReuseSameProjection() {
        val reorder = state()
        val projection = reorder.items
        repeat(2000) {
            assertEquals(it, reorder.projectedIndexOf("i$it"))
            assertSame(projection, reorder.items)
        }
    }
    @Test fun projectedIndexMatchesEveryRowAfterMovesInBothDirections() {
        val reorder = state()
        reorder.begin("i100", CountingReorderMode.DETAILED)
        for (target in listOf(1500, 0, 1999, 100)) {
            reorder.movePlaceholderTo(target)
            val projection = reorder.items
            projection.forEachIndexed { i, row -> assertEquals(i, reorder.projectedIndexOf(row.id)) }
        }
        assertEquals(original.map { it.id }, reorder.commitOrder())
    }
    @Test fun unchangedSyncDoesNotRebuildProjection() {
        val reorder = state()
        val before = reorder.items
        reorder.sync(original)
        assertSame(before, reorder.items)
    }
    @Test fun compactCanRetargetOnTheNextDisplayFrame() {
        val reorder = state()
        reorder.begin("i0", CountingReorderMode.COMPACT)
        fun slot(id: String, index: Int, x: Float) = CountingReorderSlot(id, index, x, 50f, 100f, 100f)
        assertTrue(reorder.updateCompact(150f, 50f, listOf(slot("i1", 1, 150f)), 1000L))
        assertTrue(reorder.updateCompact(250f, 50f, listOf(slot("i2", 2, 250f)), 1008L))
        assertEquals(2, reorder.projectedIndexOf("i0"))
    }
}
