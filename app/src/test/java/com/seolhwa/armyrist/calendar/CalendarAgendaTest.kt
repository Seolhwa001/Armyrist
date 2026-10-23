package com.seolhwa.armyrist.calendar
import com.seolhwa.armyrist.stage2.domain.*
import com.seolhwa.armyrist.timeplan.v3.domain.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
class CalendarAgendaTest {
    private val day=LocalDate.of(2026,12,31)
    private fun plan()=DateAwareTimePlan("p","계획",start=DateTimeAnchor(DateTimeValue.explicit(day.atTime(23,0))),end=DateTimeAnchor(DateTimeValue.explicit(day.plusDays(1).atTime(1,0))),createdAt="0",updatedAt="0")
    @Test fun rangesAppearOnBothDaysAcrossYearBoundary(){
        val p=plan().copy(midwayEvents=listOf(DateTimeEvent("e",TimeEventKind.MIDWAY,0,"경계 일정",EventDateTimeSpec.Range(DateTimeValue.explicit(day.atTime(23,30)),DateTimeValue.explicit(day.plusDays(1).atTime(0,30))))))
        listOf(day,day.plusDays(1)).forEach{assertEquals(listOf(AgendaKind.PLAN,AgendaKind.EVENT),CalendarAgenda.entriesOn(it,listOf(p),emptyList()).map{e->e.kind})}
        assertTrue(CalendarAgenda.entriesOn(day.plusDays(2),listOf(p),emptyList()).isEmpty())
    }
    @Test fun relativeAndUnmigratedPlansDoNotBecomeRealCalendarDates(){
        assertTrue(CalendarAgenda.entriesOn(day,listOf(plan().copy(dateDisplayMode=TimePlanDateDisplayMode.RELATIVE_D_DAY),plan().copy(id="legacy",legacyDateMigrationRequired=true)),emptyList()).isEmpty())
    }
    @Test fun scheduledChecklistWithoutAlarmAppearsButTrashDoesNot(){
        val item=ChecklistItem(checklistId="l",order=0,name="점검",scheduledTimeMinutes=540,scheduledDate=day.toString())
        val list=Checklist(id="l",items=listOf(item),deletedItems=listOf(item.copy(id="trash")))
        val entries=CalendarAgenda.entriesOn(day,emptyList(),listOf(list),day.minusDays(1))
        assertEquals(1,entries.size);assertEquals(item.id,entries.single().itemId)
        assertTrue(CalendarAgenda.entriesOn(day.plusDays(1),emptyList(),listOf(list)).isEmpty())
        assertEquals(1,CalendarAgenda.entriesOn(day,emptyList(),listOf(list.copy(items=listOf(item.copy(scheduledDate=null)))),day).size)
    }
    @Test fun actionUsesOwnDateAndKeepsCompletion(){
        val actionDay=day.plusDays(3)
        val p=plan().copy(actions=listOf(TimePlanActionItem(parentPointId="START",content="실시사항",scheduledDateTime=actionDay.atTime(9,0),completionState=ActionCompletionState.COMPLETE)))
        val entries=CalendarAgenda.entriesOn(actionDay,listOf(p),emptyList())
        assertEquals(AgendaKind.ACTION,entries.single().kind);assertTrue(entries.single().complete)
    }
}
