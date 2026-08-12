package com.seolhwa.armyrist.timeplan.domain

import org.junit.Assert.*
import org.junit.Test

class TimePlanCalculatorTest {
    private fun c(h:Int,m:Int)=ClockTime.requireMinuteOfDay(h*60+m)

    @Test fun forwardSameDay() {
        val r=TimePlanCalculator.forward(TimePlanCalculator.AbsoluteClock(c(9,0),0),TimeDuration.requireMinutes(40))
        r as TimePlanCalculator.Calculation.Success
        assertEquals(c(9,40),r.value.time)
    }
    @Test fun forwardMidnight() {
        val r=TimePlanCalculator.forward(TimePlanCalculator.AbsoluteClock(c(23,40),0),TimeDuration.requireMinutes(40))
        r as TimePlanCalculator.Calculation.Success
        assertEquals(c(0,20),r.value.time); assertEquals(1,r.value.dayOffset)
    }
    @Test fun reverseMidnight() {
        val r=TimePlanCalculator.reverse(TimePlanCalculator.AbsoluteClock(c(0,20),1),TimeDuration.requireMinutes(40))
        r as TimePlanCalculator.Calculation.Success
        assertEquals(c(23,40),r.value.time); assertEquals(0,r.value.dayOffset)
    }
    @Test fun rangeReferencesAndStay() {
        val x=EventTimeSpec.Range(ClockValue.explicit(c(9,40)),ClockValue.explicit(c(10,0)))
        assertEquals(c(9,40),TimePlanCalculator.arrivalClock(x)?.time)
        assertEquals(c(10,0),TimePlanCalculator.departureClock(x)?.time)
        val r=TimePlanCalculator.stayDuration(x) as TimePlanCalculator.Calculation.Success
        assertEquals(20,r.value.minutes)
    }
    @Test fun zeroDurationAllowed() {
        val r=TimePlanCalculator.forward(TimePlanCalculator.AbsoluteClock(c(10,0),0),TimeDuration.requireMinutes(0))
        r as TimePlanCalculator.Calculation.Success
        assertEquals(c(10,0),r.value.time)
    }
    @Test fun unresolvedIsNotGuessed() {
        assertTrue(TimePlanCalculator.forward(null,TimeDuration.requireMinutes(10)) is TimePlanCalculator.Calculation.Unresolved)
    }
    @Test fun secondMidnightRejected() {
        val r=TimePlanCalculator.resolveOrderedClocks(listOf(c(23,0),c(1,0),c(23,30),c(0,30)))
        assertTrue(r is TimePlanCalculator.Calculation.Unsupported)
    }
    @Test fun interEventDuration() {
        val r=TimePlanCalculator.durationBetween(TimePlanCalculator.AbsoluteClock(c(10,0),0),TimePlanCalculator.AbsoluteClock(c(11,20),0))
        r as TimePlanCalculator.Calculation.Success
        assertEquals(80,r.value.minutes)
    }
}
