package com.seolhwa.armyrist.timeplan.v3

import com.seolhwa.armyrist.timeplan.v3.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class TimePlanExecutionResultGeneratorTest {
    @Test fun compactListsOnlyIncompleteWhileDetailedListsAll() {
        val t = LocalDateTime.of(2026,8,23,22,0)
        val plan = DateTimePlanRules.normalizeTopology(DateAwareTimePlan(
            id="p", title="일요일 당직",
            start=DateTimeAnchor(DateTimeValue.explicit(t)),
            end=DateTimeAnchor(DateTimeValue.explicit(t.plusHours(2))),
            actions=listOf(
                TimePlanActionItem("done", DateTimePlanRules.START_ID, "완료한 일", t.plusMinutes(10), completionState=ActionCompletionState.COMPLETE),
                TimePlanActionItem("miss", DateTimePlanRules.START_ID, "못한 일", t.plusMinutes(20), note="긴급 업무")
            ),
            createdAt="1", updatedAt="1"
        ))
        val compact = TimePlanExecutionResultGenerator.compact(plan).body
        val detailed = TimePlanExecutionResultGenerator.detailed(plan).body
        assertFalse(compact.contains("완료한 일"))
        assertTrue(compact.contains("못한 일"))
        assertTrue(compact.contains("긴급 업무"))
        assertTrue(detailed.contains("완료한 일 : 완료"))
        assertTrue(detailed.contains("못한 일 : 미실시"))
        assertTrue(detailed.contains("완료율 50%"))
    }
}
