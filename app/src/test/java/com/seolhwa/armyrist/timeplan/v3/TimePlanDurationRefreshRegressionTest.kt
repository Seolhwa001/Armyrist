package com.seolhwa.armyrist.timeplan.v3

import com.seolhwa.armyrist.timeplan.v3.domain.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class TimePlanDurationRefreshRegressionTest {
    @Test
    fun unlockedDurationRecalculatesWhenPointTimeChanges() {
        val startTime = LocalDateTime.of(2026, 8, 23, 11, 40)
        val midTime = LocalDateTime.of(2026, 8, 23, 15, 40)
        val endTime = LocalDateTime.of(2026, 8, 23, 20, 40)
        val mid = DateTimeEvent(
            id = "mid",
            order = 0,
            kind = TimeEventKind.MIDWAY,
            name = "중도",
            timeSpec = EventDateTimeSpec.Single(DateTimeValue.explicit(midTime))
        )
        val plan = DateTimePlanRules.normalizeTopology(
            DateAwareTimePlan(
                id = "p",
                title = "test",
                start = DateTimeAnchor(DateTimeValue.explicit(startTime)),
                midwayEvents = listOf(mid),
                end = DateTimeAnchor(DateTimeValue.explicit(endTime))
            )
        )

        val changedMid = mid.copy(
            timeSpec = EventDateTimeSpec.Single(
                DateTimeValue.explicit(LocalDateTime.of(2026, 8, 23, 16, 40))
            )
        )
        val changed = DateTimePlanRules.recalculateForExplicitNodes(
            plan.copy(midwayEvents = listOf(changedMid)),
            setOf("mid")
        )

        val first = changed.links.first { it.fromNodeId == DateTimePlanRules.START_ID && it.toNodeId == "mid" }
        val second = changed.links.first { it.fromNodeId == "mid" && it.toNodeId == DateTimePlanRules.END_ID }
        assertEquals(300L, first.durationMinutes)
        assertEquals(240L, second.durationMinutes)
    }
}
