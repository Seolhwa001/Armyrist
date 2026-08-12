package com.seolhwa.armyrist.timeplan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RevisedTimePlanModelsTest {
    @Test
    fun clockTime_acceptsOnlyMinuteOfDay() {
        assertEquals(0, ClockTime.ofMinuteOfDay(0)?.minuteOfDay)
        assertEquals(1439, ClockTime.ofMinuteOfDay(1439)?.minuteOfDay)
        assertNull(ClockTime.ofMinuteOfDay(-1))
        assertNull(ClockTime.ofMinuteOfDay(1440))
    }

    @Test
    fun duration_allowsZeroAndRejectsNegative() {
        assertEquals(0, TimeDuration.ofMinutes(0)?.minutes)
        assertEquals(80, TimeDuration.ofMinutes(80)?.minutes)
        assertNull(TimeDuration.ofMinutes(-1))
    }

    @Test
    fun partialRange_isValidDomainState() {
        val range = EventTimeSpec.Range(
            start = ClockValue.explicit(ClockTime.requireMinuteOfDay(580))
        )
        assertTrue(range.end.time == null)
        assertEquals(ValueOrigin.UNSET, range.end.origin)
    }

    @Test(expected = IllegalArgumentException::class)
    fun midwayCollection_rejectsFinalEvent() {
        RevisedTimePlan(
            id = "p",
            title = "plan",
            midwayEvents = listOf(
                TimeEvent("e", TimeEventKind.FINAL, 0, "final")
            ),
            createdAt = "created",
            updatedAt = "updated"
        )
    }

    @Test
    fun orderedEvents_placesFinalAfterAllMidway() {
        val plan = RevisedTimePlan(
            id = "p",
            title = "plan",
            midwayEvents = listOf(
                TimeEvent("b", TimeEventKind.MIDWAY, 1, "B"),
                TimeEvent("a", TimeEventKind.MIDWAY, 0, "A")
            ),
            finalPoint = TimeEvent("f", TimeEventKind.FINAL, 0, "Final"),
            createdAt = "created",
            updatedAt = "updated"
        )
        assertEquals(listOf("a", "b", "f"), plan.orderedEvents().map { it.id })
    }
}
