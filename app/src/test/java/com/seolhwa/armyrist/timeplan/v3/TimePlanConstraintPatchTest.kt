package com.seolhwa.armyrist.timeplan.v3

import com.seolhwa.armyrist.timeplan.v3.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class TimePlanConstraintPatchTest {
    private fun dt(day:Int,h:Int,m:Int=0)=LocalDateTime.of(2026,8,day,h,m)

    @Test fun lockedDateTimeMovesOppositeSideForDurationEdit() {
        val mid=DateTimeEvent(
            "m", TimeEventKind.MIDWAY, 0, "고정",
            EventDateTimeSpec.Single(DateTimeValue.explicit(dt(14,10,40))),
            dateTimeLocked=true
        )
        val p=DateTimePlanRules.normalizeTopology(DateAwareTimePlan(
            "p","x",
            DateTimeAnchor(DateTimeValue.explicit(dt(14,10,0))),
            listOf(mid),null,
            DateTimeAnchor(DateTimeValue.explicit(dt(14,11,0))),
            createdAt="0",updatedAt="0"
        ))
        val changed=DateTimePlanRules.setLinkDuration(p,DateTimePlanRules.START_ID,"m",60,"이동")
        assertEquals(dt(14,9,40),changed.start.value.value)
        assertEquals(dt(14,10,40),DateTimePlanRules.arrival(changed.midwayEvents.first().timeSpec))
    }

    @Test fun lockedDurationMovesOppositeSideForExplicitNodeEdit() {
        val mid=DateTimeEvent("m",TimeEventKind.MIDWAY,0,"도착",EventDateTimeSpec.Single(DateTimeValue.explicit(dt(14,11,0))))
        var p=DateTimePlanRules.normalizeTopology(DateAwareTimePlan(
            "p","x",
            DateTimeAnchor(DateTimeValue.explicit(dt(14,10,0))),
            listOf(mid),null,
            DateTimeAnchor(DateTimeValue.explicit(dt(14,12,0))),
            createdAt="0",updatedAt="0"
        ))
        p=DateTimePlanRules.setLinkLock(p,DateTimePlanRules.START_ID,"m",true)
        val edited=p.copy(midwayEvents=listOf(mid.copy(timeSpec=EventDateTimeSpec.Single(DateTimeValue.explicit(dt(14,11,20))))))
        val changed=DateTimePlanRules.recalculateForExplicitNodes(edited,setOf("m"))
        assertEquals(dt(14,10,20),changed.start.value.value)
        assertEquals(60L,changed.links.first().durationMinutes)
    }

    @Test fun unsatisfiableLockedRelationCreatesWarningWithoutMutation() {
        val mid=DateTimeEvent(
            "m",TimeEventKind.MIDWAY,0,"도착",
            EventDateTimeSpec.Single(DateTimeValue.explicit(dt(14,10,40))),
            dateTimeLocked=true
        )
        val p=DateAwareTimePlan(
            "p","x",
            DateTimeAnchor(DateTimeValue.explicit(dt(14,10,0)),dateTimeLocked=true),
            listOf(mid),null,
            DateTimeAnchor(DateTimeValue.explicit(dt(14,12,0))),
            links=listOf(DateTimeLink(DateTimePlanRules.START_ID,"m",60,ValueOrigin.EXPLICIT,"이동",durationLocked=true),DateTimeLink("m",DateTimePlanRules.END_ID,80,ValueOrigin.DERIVED)),
            createdAt="0",updatedAt="0"
        )
        val c=TimePlanConstraintEngine.detect(p)
        assertTrue(c.any{it.type==TimePlanConflictType.LOCKED_RELATION_MISMATCH})
        assertEquals(dt(14,10,0),p.start.value.value)
        assertEquals(dt(14,10,40),DateTimePlanRules.arrival(p.midwayEvents.first().timeSpec))
    }

    @Test fun rangeOverlapDetectedButAdjacentRangesAllowed() {
        val a=DateTimeEvent("a",TimeEventKind.MIDWAY,0,"A",EventDateTimeSpec.Range(DateTimeValue.explicit(dt(14,10)),DateTimeValue.explicit(dt(14,11))))
        val b=DateTimeEvent("b",TimeEventKind.MIDWAY,1,"B",EventDateTimeSpec.Range(DateTimeValue.explicit(dt(14,10,30)),DateTimeValue.explicit(dt(14,12))))
        val p=DateAwareTimePlan("p","x",DateTimeAnchor(DateTimeValue.explicit(dt(14,9))),listOf(a,b),null,DateTimeAnchor(DateTimeValue.explicit(dt(14,13))),createdAt="0",updatedAt="0")
        assertTrue(TimePlanConstraintEngine.detect(p).any{it.type==TimePlanConflictType.TIME_OVERLAP})
        val adjacent=p.copy(midwayEvents=listOf(a,b.copy(timeSpec=EventDateTimeSpec.Range(DateTimeValue.explicit(dt(14,11)),DateTimeValue.explicit(dt(14,12))))))
        assertFalse(TimePlanConstraintEngine.detect(adjacent).any{it.type==TimePlanConflictType.TIME_OVERLAP})
    }

    @Test fun pointInsideRangeDetectedAndEndBoundaryAllowed() {
        val range=DateTimeEvent("r",TimeEventKind.MIDWAY,0,"범위",EventDateTimeSpec.Range(DateTimeValue.explicit(dt(14,10)),DateTimeValue.explicit(dt(14,11))))
        val inside=DateTimeEvent("p",TimeEventKind.MIDWAY,1,"점",EventDateTimeSpec.Single(DateTimeValue.explicit(dt(14,10,30))))
        val base=DateAwareTimePlan("p0","x",DateTimeAnchor(DateTimeValue.explicit(dt(14,9))),listOf(range,inside),null,DateTimeAnchor(DateTimeValue.explicit(dt(14,12))),createdAt="0",updatedAt="0")
        assertTrue(TimePlanConstraintEngine.detect(base).any{it.type==TimePlanConflictType.TIME_OVERLAP})
        val boundary=base.copy(midwayEvents=listOf(range,inside.copy(timeSpec=EventDateTimeSpec.Single(DateTimeValue.explicit(dt(14,11))))))
        assertFalse(TimePlanConstraintEngine.detect(boundary).any{it.type==TimePlanConflictType.TIME_OVERLAP})
    }

    @Test fun batchDatePreservesClockAndRangeSpan() {
        val range=DateTimeEvent("r",TimeEventKind.MIDWAY,0,"범위",EventDateTimeSpec.Range(DateTimeValue.explicit(dt(14,23,30)),DateTimeValue.explicit(dt(15,1,0))))
        val p=DateTimePlanRules.normalizeTopology(DateAwareTimePlan("p","x",DateTimeAnchor(DateTimeValue.explicit(dt(14,20))),listOf(range),null,DateTimeAnchor(DateTimeValue.explicit(dt(15,2))),createdAt="0",updatedAt="0"))
        val changed=DateTimePlanRules.batchChangeDate(p,setOf("r"),LocalDate.of(2026,8,16))!!
        assertEquals(dt(16,23,30),DateTimePlanRules.arrival(changed.midwayEvents.first().timeSpec))
        assertEquals(dt(17,1,0),DateTimePlanRules.departure(changed.midwayEvents.first().timeSpec))
    }

    @Test fun lockedEventBlocksBatchDate() {
        val e=DateTimeEvent("m",TimeEventKind.MIDWAY,0,"x",EventDateTimeSpec.Single(DateTimeValue.explicit(dt(14,10))),dateTimeLocked=true)
        val p=DateAwareTimePlan("p","x",DateTimeAnchor(DateTimeValue.explicit(dt(14,9))),listOf(e),null,DateTimeAnchor(DateTimeValue.explicit(dt(14,11))),createdAt="0",updatedAt="0")
        assertNull(DateTimePlanRules.batchChangeDate(p,setOf("m"),LocalDate.of(2026,8,15)))
    }
}
