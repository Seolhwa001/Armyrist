package com.seolhwa.armyrist.timeplan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimePlanNo011Test {
    private fun t(h: Int, m: Int) = ClockTime.requireMinuteOfDay(h * 60 + m)

    private fun finalPoint(
        id: String = "final-a",
        name: String = "종료지점",
        note: String? = null,
        h: Int? = null,
        m: Int? = null
    ) = TimeEvent(
        id = id,
        kind = TimeEventKind.FINAL,
        order = 0,
        name = name,
        timeSpec = if (h == null || m == null) EventTimeSpec.Unspecified
        else EventTimeSpec.Single(ClockValue.explicit(t(h, m))),
        note = note
    )

    private fun base(final: TimeEvent? = null) = RevisedTimePlan(
        id = "p",
        title = "x",
        start = TimeAnchor(ClockValue.explicit(t(8, 30))),
        finalPoint = final,
        end = TimeAnchor(ClockValue.explicit(t(16, 0))),
        createdAt = "0",
        updatedAt = "0"
    )

    @Test fun firstAddCreatesOneFinal() {
        val result = TimePlanCandidateEngine.appendFinalPoint(base(), "new-final")
        assertEquals("new-final", result.finalPoint?.id)
        assertEquals(TimeEventKind.FINAL, result.finalPoint?.kind)
        assertTrue(result.midwayEvents.isEmpty())
    }

    @Test fun repeatedAddDemotesOldFinalAndCreatesNewFinal() {
        val old = finalPoint(h = 12, m = 30)
        val result = TimePlanCandidateEngine.appendFinalPoint(base(old), "new-final")
        assertEquals(1, result.midwayEvents.size)
        assertEquals(old.id, result.midwayEvents.single().id)
        assertEquals(TimeEventKind.MIDWAY, result.midwayEvents.single().kind)
        assertEquals("중도 1", result.midwayEvents.single().name)
        assertEquals("new-final", result.finalPoint?.id)
        assertEquals(1, listOfNotNull(result.finalPoint).size)
    }

    @Test fun demotionPreservesUserData() {
        val old = finalPoint(name = "2교육장", note = "장비 확인", h = 12, m = 30)
        val result = TimePlanCandidateEngine.appendFinalPoint(base(old), "new-final")
        val converted = result.midwayEvents.single()
        assertEquals(old.id, converted.id)
        assertEquals("2교육장", converted.name)
        assertEquals("장비 확인", converted.note)
        assertEquals(old.timeSpec, converted.timeSpec)
    }

    @Test fun newFinalStartsUnspecifiedAndTopologyExists() {
        val old = finalPoint(h = 12, m = 0)
        val result = TimePlanCandidateEngine.appendFinalPoint(base(old), "new-final")
        assertEquals(EventTimeSpec.Unspecified, result.finalPoint?.timeSpec)
        assertEquals(3, result.links.size)
        val toNewFinal = result.links.first { it.toNodeId == "new-final" }
        val fromNewFinal = result.links.first { it.fromNodeId == "new-final" }
        assertNull(toNewFinal.duration)
        assertNull(fromNewFinal.duration)
    }

    @Test fun repeatedAddsKeepOnlyOneFinal() {
        var p = base()
        repeat(5) { index ->
            p = TimePlanCandidateEngine.appendFinalPoint(p, "final-$index")
            assertEquals(TimeEventKind.FINAL, p.finalPoint?.kind)
        }
        assertEquals(4, p.midwayEvents.size)
        assertEquals("final-4", p.finalPoint?.id)
        assertTrue(p.midwayEvents.all { it.kind == TimeEventKind.MIDWAY })
    }
}
