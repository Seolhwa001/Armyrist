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
                end = DateTimeAnchor(DateTimeValue.explicit(endTime)),
                createdAt = "2026-08-23T00:00:00",
                updatedAt = "2026-08-23T00:00:00"
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

    @Test
    fun rangeEventExtendingPastUnlockedEndMovesEndAndRefreshesDuration() {
        val startTime = LocalDateTime.of(2026, 8, 23, 8, 0)
        val oldMidStart = LocalDateTime.of(2026, 8, 23, 10, 0)
        val oldMidEnd = LocalDateTime.of(2026, 8, 23, 11, 0)
        val oldEnd = LocalDateTime.of(2026, 8, 23, 12, 0)
        val mid = DateTimeEvent(
            id = "mid-range",
            order = 0,
            kind = TimeEventKind.MIDWAY,
            name = "휴식",
            timeSpec = EventDateTimeSpec.Range(
                DateTimeValue.explicit(oldMidStart),
                DateTimeValue.explicit(oldMidEnd)
            )
        )
        val plan = DateTimePlanRules.normalizeTopology(
            DateAwareTimePlan(
                id = "p-range",
                title = "test",
                start = DateTimeAnchor(DateTimeValue.explicit(startTime)),
                midwayEvents = listOf(mid),
                end = DateTimeAnchor(DateTimeValue.explicit(oldEnd)),
                createdAt = "2026-08-23T00:00:00",
                updatedAt = "2026-08-23T00:00:00"
            )
        )

        val changedMid = mid.copy(
            timeSpec = EventDateTimeSpec.Range(
                DateTimeValue.explicit(oldMidStart),
                DateTimeValue.explicit(LocalDateTime.of(2026, 8, 23, 13, 0))
            )
        )
        val changed = DateTimePlanRules.reflowEventEdit(plan, changedMid)!!

        // 0.6.5 chronology contract:
        // an unlocked END expands only as far as necessary to contain the edited range.
        // Therefore a range ending at 13:00 moves END to 13:00 and the following
        // interval becomes 0 minutes instead of preserving the old 60-minute gap.
        assertEquals(LocalDateTime.of(2026, 8, 23, 13, 0), changed.end.value.value)
        val afterMid = changed.links.first {
            it.fromNodeId == "mid-range" && it.toNodeId == DateTimePlanRules.END_ID
        }
        assertEquals(0L, afterMid.durationMinutes)
    }
}
