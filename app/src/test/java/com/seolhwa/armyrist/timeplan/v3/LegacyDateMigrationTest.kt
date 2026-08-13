package com.seolhwa.armyrist.timeplan.v3

import com.seolhwa.armyrist.timeplan.domain.*
import com.seolhwa.armyrist.timeplan.v3.data.LegacyDateMigration
import com.seolhwa.armyrist.timeplan.v3.domain.DateTimePlanRules
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class LegacyDateMigrationTest {
    private fun c(h:Int,m:Int)=ClockTime.requireMinuteOfDay(h*60+m)

    @Test fun midnightMigrationUsesUserBaseDate() {
        val legacy=RevisedTimePlan(
            id="p",title="legacy",
            start=TimeAnchor(ClockValue.explicit(c(23,0))),
            midwayEvents=listOf(
                TimeEvent("a",TimeEventKind.MIDWAY,0,"a",EventTimeSpec.Single(ClockValue.explicit(c(23,40)))),
                TimeEvent("b",TimeEventKind.MIDWAY,1,"b",EventTimeSpec.Single(ClockValue.explicit(c(0,20))))
            ),
            end=TimeAnchor(ClockValue.explicit(c(1,30))),
            createdAt="0",updatedAt="0"
        )
        val migrated=LegacyDateMigration.createCandidate(legacy,LocalDate.of(2026,8,14))!!
        assertEquals(LocalDateTime.of(2026,8,14,23,0),migrated.start.value.value)
        assertEquals(LocalDateTime.of(2026,8,15,0,20),DateTimePlanRules.arrival(migrated.midwayEvents[1].timeSpec))
        assertEquals(LocalDateTime.of(2026,8,15,1,30),migrated.end.value.value)
    }
}
