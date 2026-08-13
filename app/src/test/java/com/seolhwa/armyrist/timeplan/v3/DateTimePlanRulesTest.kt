package com.seolhwa.armyrist.timeplan.v3

import com.seolhwa.armyrist.timeplan.v3.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class DateTimePlanRulesTest {
    private fun dt(d:Int,h:Int,m:Int=0)=LocalDateTime.of(2026,8,d,h,m)

    @Test fun durationAcrossMidnight() {
        assertEquals(120L, DateTimePlanRules.minutesBetween(dt(14,23,30), dt(15,1,30)))
    }

    @Test fun durationOver24HoursSupported() {
        assertEquals(2880L, DateTimePlanRules.minutesBetween(dt(14,9), dt(16,9)))
    }

    @Test fun reverseAcrossDateBoundary() {
        assertEquals(dt(14,23,30), DateTimePlanRules.reverse(dt(15,1,30), 120))
    }

    @Test fun dateOnlyEditPreservesClock() {
        val changed=DateTimePlanRules.withDate(dt(15,23,30), dt(16,0).toLocalDate())
        assertEquals(dt(16,23,30), changed)
    }

    @Test fun timeOnlyEditPreservesDate() {
        val changed=DateTimePlanRules.withTime(dt(15,23,30), dt(14,1,40).toLocalTime())
        assertEquals(dt(15,1,40), changed)
    }

    @Test fun durationEditMovesDownstreamAcrossDays() {
        val mid=DateTimeEvent("m",TimeEventKind.MIDWAY,0,"작업",EventDateTimeSpec.Single(DateTimeValue.explicit(dt(14,10))))
        val p=DateTimePlanRules.normalizeTopology(DateAwareTimePlan("p","x",DateTimeAnchor(DateTimeValue.explicit(dt(14,9))),listOf(mid),null,DateTimeAnchor(DateTimeValue.explicit(dt(14,11))),createdAt="0",updatedAt="0"))
        val changed=DateTimePlanRules.setLinkDuration(p,DateTimePlanRules.START_ID,"m",1500,"이동")
        assertEquals(dt(15,10), DateTimePlanRules.arrival(changed.midwayEvents.first().timeSpec))
        assertEquals(dt(15,11), changed.end.value.value)
    }

    @Test fun pointTimeEditRecalculatesAdjacentExplicitDuration() {
        val mid=DateTimeEvent("m",TimeEventKind.MIDWAY,0,"x",EventDateTimeSpec.Single(DateTimeValue.explicit(dt(14,10))))
        val p=DateAwareTimePlan(
            "p","x",
            DateTimeAnchor(DateTimeValue.explicit(dt(14,9))),
            listOf(mid),null,
            DateTimeAnchor(DateTimeValue.explicit(dt(14,11))),
            links=listOf(
                DateTimeLink(DateTimePlanRules.START_ID,"m",60,ValueOrigin.EXPLICIT,"이동"),
                DateTimeLink("m",DateTimePlanRules.END_ID,60,ValueOrigin.EXPLICIT,"이동")
            ),
            createdAt="0",updatedAt="0"
        )
        val changed=p.copy(midwayEvents=listOf(mid.copy(timeSpec=EventDateTimeSpec.Single(DateTimeValue.explicit(dt(14,10,30))))))
        val normalized=DateTimePlanRules.normalizeTopology(changed)
        assertEquals(90L,normalized.links[0].durationMinutes)
        assertEquals(30L,normalized.links[1].durationMinutes)
    }

    @Test fun touchingRangeAndNextPointIsValid() {
        val e=DateTimeEvent("m",TimeEventKind.MIDWAY,0,"범위",EventDateTimeSpec.Range(DateTimeValue.explicit(dt(14,9)),DateTimeValue.explicit(dt(14,10))))
        val p=DateTimePlanRules.normalizeTopology(DateAwareTimePlan("p","x",DateTimeAnchor(DateTimeValue.explicit(dt(14,8))),listOf(e),null,DateTimeAnchor(DateTimeValue.explicit(dt(14,10))),createdAt="0",updatedAt="0"))
        assertTrue(DateTimePlanRules.validate(p).isEmpty())
    }

    @Test fun invalidReverseOrderRejected() {
        val e=DateTimeEvent("m",TimeEventKind.MIDWAY,0,"x",EventDateTimeSpec.Single(DateTimeValue.explicit(dt(14,8))))
        val p=DateAwareTimePlan("p","x",DateTimeAnchor(DateTimeValue.explicit(dt(14,9))),listOf(e),null,DateTimeAnchor(DateTimeValue.explicit(dt(14,10))),createdAt="0",updatedAt="0")
        assertTrue(DateTimePlanRules.validate(p).isNotEmpty())
    }
}
