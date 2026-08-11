package com.seolhwa.armyrist.stage2.domain

import org.junit.Assert.*
import org.junit.Test

class TimePlanRulesTest {
    private fun point(order: Int, name: String, minutes: Int): TimePoint =
        TimePoint(
            id = "p$order",
            planId = "plan",
            order = order,
            name = name,
            timeMinutes = minutes
        )

    @Test
    fun parseHHMM() {
        assertEquals(510, TimePlanRules.parseClock("0830"))
        assertEquals(510, TimePlanRules.parseClock("08:30"))
        assertNull(TimePlanRules.parseClock("2460"))
        assertNull(TimePlanRules.parseClock("2365"))
        assertNull(TimePlanRules.parseClock("abcd"))
    }

    @Test
    fun midnightCrossing() {
        val points = listOf(
            point(0, "시작", 23 * 60),
            point(1, "종료", 60)
        )

        val derived = TimePlanRules.derive(points)!!
        assertEquals(23 * 60, derived[0].absoluteMinute)
        assertEquals(25 * 60, derived[1].absoluteMinute)
        assertEquals(120, TimePlanRules.adjacentDuration(points, 0))
    }

    @Test
    fun pointTimeEditDoesNotMoveFollowingClock() {
        val points = listOf(
            point(0, "시작", 600),
            point(1, "집합", 640),
            point(2, "준비완료", 760)
        )

        val edited = TimePlanRules.editPointTime(points, "p1", 660)!!
        assertEquals(600, edited[0].timeMinutes)
        assertEquals(660, edited[1].timeMinutes)
        assertEquals(760, edited[2].timeMinutes)
        assertEquals(60, TimePlanRules.adjacentDuration(edited, 0))
        assertEquals(100, TimePlanRules.adjacentDuration(edited, 1))
    }

    @Test
    fun durationEditMovesOnlyEndpoint() {
        val points = listOf(
            point(0, "시작", 600),
            point(1, "집합", 640),
            point(2, "준비완료", 760)
        )

        val edited = TimePlanRules.editDuration(points, "p0", 60)!!
        assertEquals(600, edited[0].timeMinutes)
        assertEquals(660, edited[1].timeMinutes)
        assertEquals(760, edited[2].timeMinutes)
    }
}
