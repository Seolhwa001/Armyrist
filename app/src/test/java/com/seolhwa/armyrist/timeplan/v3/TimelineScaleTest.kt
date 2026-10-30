package com.seolhwa.armyrist.timeplan.v3
import com.seolhwa.armyrist.timeplan.v3.domain.*
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test
class TimelineScaleTest {
    private val start=LocalDateTime.of(2030,1,1,23,20)
    @Test fun partialHoursContinueAcrossMidnightWithoutLosingMinutes(){
        val axis=TimelineScale(start,start.plusHours(2).plusMinutes(50));assertEquals(4,axis.count)
        val rows=(0 until axis.count).map(axis::slice)
        assertEquals(start,rows.first().start);assertEquals(start.plusHours(2).plusMinutes(50),rows.last().end)
        rows.zipWithNext().forEach{assertEquals(it.first.end,it.second.start)}
        assertEquals(170.0,rows.sumOf{it.minutes},0.001)
        assertEquals(1,axis.index(LocalDateTime.of(2030,1,2,0,0)))
        assertEquals(0.5f,rows.first().fraction(start.plusMinutes(20)),0.001f)
    }
    @Test fun longPlansUseVirtualHourlyRows(){
        val axis=TimelineScale(start,start.plusYears(20));assertTrue(axis.count>170000)
        val final=axis.slice(axis.count-1);assertEquals(start.plusYears(20),final.end)
        assertEquals(axis.count-1,axis.index(final.end))
    }
    private fun plan():DateAwareTimePlan{
        val a=DateTimeEvent("a",TimeEventKind.MIDWAY,0,"오전",EventDateTimeSpec.Range(DateTimeValue.explicit(start),DateTimeValue.explicit(start.plusHours(2))))
        val b=DateTimeEvent("b",TimeEventKind.MIDWAY,1,"야간",EventDateTimeSpec.Range(DateTimeValue.explicit(start.plusHours(1)),DateTimeValue.explicit(start.plusHours(3))))
        return DateAwareTimePlan("p","계획",start=DateTimeAnchor(DateTimeValue.explicit(start.minusHours(1))),end=DateTimeAnchor(DateTimeValue.explicit(start.plusHours(4))),midwayEvents=listOf(a,b),actions=listOf(TimePlanActionItem(id="action",parentPointId="a",content="점검",scheduledDateTime=start.plusHours(1))),createdAt="0",updatedAt="0")
    }
    @Test fun reverseAndForwardPaintSelectTheSameOverlappingItems(){
        val p=plan();val a=start.plusMinutes(50);val b=start.plusMinutes(70)
        assertEquals(setOf("N:a","N:b","A:action"),TimelineSelection.keysInRange(p,a,b))
        assertEquals(TimelineSelection.keysInRange(p,a,b),TimelineSelection.keysInRange(p,b,a))
    }
    @Test fun overlappingSchedulesUseSeparateLanes(){assertNotEquals(TimelineSelection.lanes(plan())["a"],TimelineSelection.lanes(plan())["b"])}
    @Test fun deletingScheduleKeepsIndependentActionsAndRebuildsLinks(){
        val p=plan();val next=TimelineSelection.remove(p,setOf("N:a","N:START"))
        assertEquals(listOf("b"),next.midwayEvents.map{it.id});assertEquals(p.actions,next.actions);assertEquals(p.start,next.start)
        assertEquals(listOf("START" to "b","b" to "END"),next.links.map{it.fromNodeId to it.toNodeId})
    }
    @Test fun directActionDeletionPreservesParent(){val next=TimelineSelection.remove(plan(),setOf("A:action"));assertEquals(2,next.midwayEvents.size);assertTrue(next.actions.isEmpty())}
    @Test fun lockedScheduleAndAnchorsCannotBeDeleted(){
        assertThrows(IllegalArgumentException::class.java){TimelineSelection.remove(plan(),setOf("N:START","N:END"))}
        val p=plan().let{it.copy(midwayEvents=it.midwayEvents.map{e->e.copy(dateTimeLocked=true)})}
        assertThrows(IllegalArgumentException::class.java){TimelineSelection.remove(p,setOf("N:a"))}
    }
}
