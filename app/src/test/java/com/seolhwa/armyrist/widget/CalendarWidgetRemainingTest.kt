package com.seolhwa.armyrist.widget

import com.seolhwa.armyrist.calendar.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class CalendarWidgetRemainingTest {
    private val now=LocalDateTime.of(2026,10,23,14,30)
    private fun entry(start:LocalDateTime,end:LocalDateTime?=null,complete:Boolean=false)=
        AgendaEntry(AgendaKind.ACTION,"plan","action","점검","계획",start,end,complete)
    @Test fun excludesPastPointsAndCompletedFutureItems(){
        assertFalse(CalendarWidgetModel.isRemaining(entry(now.minusMinutes(1)),now))
        assertFalse(CalendarWidgetModel.isRemaining(entry(now.plusDays(1),complete=true),now))
        assertTrue(CalendarWidgetModel.isRemaining(entry(now),now))
        assertTrue(CalendarWidgetModel.isRemaining(entry(now.plusMinutes(1)),now))
    }
    @Test fun keepsOngoingRangesAndExcludesEndedRanges(){
        assertTrue(CalendarWidgetModel.isRemaining(entry(now.minusDays(1),now.plusMinutes(1)),now))
        assertFalse(CalendarWidgetModel.isRemaining(entry(now.minusDays(1),now),now))
        assertFalse(CalendarWidgetModel.isRemaining(entry(now.minusDays(1),now.minusMinutes(1)),now))
        assertFalse(CalendarWidgetModel.isRemaining(entry(now.minusDays(1),now.plusDays(1),true),now))
    }
    @Test fun invalidEndCannotResurrectAnExpiredPoint(){
        assertFalse(CalendarWidgetModel.isRemaining(entry(now.minusMinutes(1),now.minusHours(1)),now))
    }
}
