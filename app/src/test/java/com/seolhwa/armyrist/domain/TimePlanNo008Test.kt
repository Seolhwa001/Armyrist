package com.seolhwa.armyrist.timeplan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimePlanNo008Test {
    private fun t(h: Int, m: Int) = ClockTime.requireMinuteOfDay(h * 60 + m)

    @Test fun clockEditRecalculatesElapsed() {
        val event = TimeEvent("e1", TimeEventKind.MIDWAY, 0, "중도",
            EventTimeSpec.Single(ClockValue.explicit(t(8,50))))
        val plan = RevisedTimePlan("p","x", TimeAnchor(ClockValue.explicit(t(8,30))),
            listOf(event), end = TimeAnchor(ClockValue.explicit(t(16,0))),
            links = listOf(
                TimeLink(TimePlanConflictEngine.START_ID,"e1",TimeDuration.requireMinutes(20),ValueOrigin.EXPLICIT),
                TimeLink("e1",TimePlanConflictEngine.END_ID,TimeDuration.requireMinutes(430),ValueOrigin.DERIVED)
            ), createdAt="0", updatedAt="0")
        val c = TimePlanCandidateEngine.create(plan,
            TimePlanCandidateEngine.EditIntent.SetEventTime("e1",
                EventTimeSpec.Single(ClockValue.explicit(t(9,0)))))
        val link = c.proposed.links.first { it.toNodeId == "e1" }
        assertEquals(30, link.duration?.minutes)
        assertEquals(ValueOrigin.DERIVED, link.origin)
    }

    @Test fun durationEditMovesDerivedDownstreamButNotExplicitEnd() {
        val e1 = TimeEvent("e1", TimeEventKind.MIDWAY, 0, "1",
            EventTimeSpec.Single(ClockValue.derived(t(8,50))))
        val e2 = TimeEvent("e2", TimeEventKind.MIDWAY, 1, "2",
            EventTimeSpec.Single(ClockValue.derived(t(9,10))))
        val plan = RevisedTimePlan("p","x", TimeAnchor(ClockValue.explicit(t(8,30))),
            listOf(e1,e2), end=TimeAnchor(ClockValue.explicit(t(16,0))),
            links=listOf(
                TimeLink(TimePlanConflictEngine.START_ID,"e1",TimeDuration.requireMinutes(20),ValueOrigin.EXPLICIT),
                TimeLink("e1","e2",TimeDuration.requireMinutes(20),ValueOrigin.EXPLICIT),
                TimeLink("e2",TimePlanConflictEngine.END_ID,TimeDuration.requireMinutes(410),ValueOrigin.DERIVED)
            ),createdAt="0",updatedAt="0")
        val c = TimePlanCandidateEngine.create(plan,
            TimePlanCandidateEngine.EditIntent.SetLinkDuration(
                TimePlanConflictEngine.START_ID,"e1",TimeDuration.requireMinutes(30)))
        val events = c.proposed.midwayEvents.sortedBy { it.order }
        assertEquals(t(9,0), (events[0].timeSpec as EventTimeSpec.Single).value.time)
        assertEquals(t(9,20), (events[1].timeSpec as EventTimeSpec.Single).value.time)
        assertEquals(t(16,0), c.proposed.end.value.time)
    }
}
