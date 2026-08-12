package com.seolhwa.armyrist.timeplan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TimePlanNo009Test {
    private fun t(h: Int, m: Int) = ClockTime.requireMinuteOfDay(h * 60 + m)

    private fun event(id: String, order: Int, h: Int, m: Int) =
        TimeEvent(
            id = id,
            kind = TimeEventKind.MIDWAY,
            order = order,
            name = id,
            timeSpec = EventTimeSpec.Single(ClockValue.explicit(t(h, m)))
        )

    @Test fun zeroWaypointStillHasStartEndInterval() {
        val p = RevisedTimePlan(
            id="p", title="x",
            start=TimeAnchor(ClockValue.explicit(t(8,30))),
            end=TimeAnchor(ClockValue.explicit(t(16,0))),
            createdAt="0", updatedAt="0"
        )
        val n = TimePlanCandidateEngine.normalizeTopology(p)
        assertEquals(1, n.links.size)
        assertEquals(450, n.links.single().duration?.minutes)
        assertEquals(ValueOrigin.DERIVED, n.links.single().origin)
    }

    @Test fun deletingOnlyWaypointMergesToStartEndInterval() {
        val wp = event("a",0,9,30)
        val p = RevisedTimePlan(
            id="p", title="x",
            start=TimeAnchor(ClockValue.explicit(t(8,30))),
            midwayEvents=listOf(wp),
            end=TimeAnchor(ClockValue.explicit(t(16,0))),
            links=listOf(
                TimeLink(TimePlanConflictEngine.START_ID,"a",TimeDuration.requireMinutes(60),ValueOrigin.DERIVED),
                TimeLink("a",TimePlanConflictEngine.END_ID,TimeDuration.requireMinutes(390),ValueOrigin.DERIVED)
            ),
            createdAt="0", updatedAt="0"
        )
        val n = TimePlanCandidateEngine.normalizeTopology(p.copy(midwayEvents=emptyList()))
        assertEquals(1, n.links.size)
        assertEquals(450, n.links.single().duration?.minutes)
    }

    @Test fun deletingMiddleWaypointDerivesNewAdjacentIntervalFromClocks() {
        val a=event("a",0,9,0)
        val b=event("b",1,9,40)
        val c=event("c",2,10,0)
        val p = RevisedTimePlan(
            id="p", title="x",
            start=TimeAnchor(ClockValue.explicit(t(8,30))),
            midwayEvents=listOf(a,b,c),
            end=TimeAnchor(ClockValue.explicit(t(11,0))),
            createdAt="0", updatedAt="0"
        )
        val n = TimePlanCandidateEngine.normalizeTopology(p.copy(midwayEvents=listOf(a,c)))
        val merged = n.links.firstOrNull { it.fromNodeId=="a" && it.toNodeId=="c" }
        assertNotNull(merged)
        assertEquals(60, merged!!.duration?.minutes)
    }

    @Test fun midnightIntervalIsDerived() {
        val p = RevisedTimePlan(
            id="p", title="x",
            start=TimeAnchor(ClockValue.explicit(t(23,40))),
            end=TimeAnchor(ClockValue.explicit(t(0,20))),
            createdAt="0", updatedAt="0"
        )
        val n = TimePlanCandidateEngine.normalizeTopology(p)
        assertEquals(40, n.links.single().duration?.minutes)
    }
}
