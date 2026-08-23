package com.seolhwa.armyrist.timeplan.v3

import com.seolhwa.armyrist.timeplan.v3.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class TimePlanExecutionRulesTest {
    private fun plan(): DateAwareTimePlan {
        val start = LocalDateTime.of(2026, 8, 23, 22, 0)
        val mid = DateTimeEvent(
            id = "mid",
            kind = TimeEventKind.MIDWAY,
            order = 0,
            name = "상황 확인",
            timeSpec = EventDateTimeSpec.Single(DateTimeValue.explicit(start.plusHours(2)))
        )
        val end = start.plusHours(6)
        return DateTimePlanRules.normalizeTopology(
            DateAwareTimePlan(
                id = "plan",
                title = "당직",
                start = DateTimeAnchor(DateTimeValue.explicit(start)),
                midwayEvents = listOf(mid),
                end = DateTimeAnchor(DateTimeValue.explicit(end)),
                createdAt = "1",
                updatedAt = "1"
            )
        )
    }

    @Test fun actionZeroToManyAndSummary() {
        val base = plan()
        assertEquals(0, TimePlanExecutionRules.summary(base).total)
        val actions = listOf(
            TimePlanActionItem("a", "mid", "상황실 확인", LocalDateTime.of(2026,8,24,0,10)),
            TimePlanActionItem("b", "mid", "당직일지 확인", LocalDateTime.of(2026,8,24,0,20), completionState = ActionCompletionState.COMPLETE)
        )
        val changed = base.copy(actions = actions)
        val summary = TimePlanExecutionRules.summary(changed)
        assertEquals(2, summary.total)
        assertEquals(1, summary.completed)
        assertEquals(1, summary.incomplete)
        assertEquals(50, summary.completionRate)
        assertTrue(TimePlanExecutionRules.validate(changed).isEmpty())
    }

    @Test fun parentRelativeAddCanCrossMidnight() {
        val parent = LocalDateTime.of(2026, 8, 23, 23, 50)
        assertEquals(
            LocalDateTime.of(2026, 8, 24, 0, 10),
            parent.plusMinutes(20)
        )
    }

    @Test fun parentShiftMovesOnlyThatParentsActions() {
        val base = plan().copy(
            actions = listOf(
                TimePlanActionItem("a", "mid", "A", LocalDateTime.of(2026,8,24,0,10)),
                TimePlanActionItem("b", DateTimePlanRules.START_ID, "B", LocalDateTime.of(2026,8,23,22,10))
            )
        )
        val moved = TimePlanExecutionRules.shiftActionsForParent(base, "mid", 60)
        assertEquals(LocalDateTime.of(2026,8,24,1,10), moved.actions.first { it.id == "a" }.scheduledDateTime)
        assertEquals(LocalDateTime.of(2026,8,23,22,10), moved.actions.first { it.id == "b" }.scheduledDateTime)
    }

    @Test fun batchShiftGroupDeleteAreAtomicTransformations() {
        val group = TimePlanActionGroup("g", "순찰", 0)
        val base = plan().copy(
            actionGroups = listOf(group),
            actions = listOf(
                TimePlanActionItem("a", "mid", "A", LocalDateTime.of(2026,8,24,0,10)),
                TimePlanActionItem("b", "mid", "B", LocalDateTime.of(2026,8,24,0,20))
            )
        )
        val shifted = TimePlanExecutionRules.batchShift(base, setOf("a", "b"), 10)
        assertEquals(20, shifted.actions.first { it.id == "a" }.scheduledDateTime.minute)
        assertEquals(30, shifted.actions.first { it.id == "b" }.scheduledDateTime.minute)
        val grouped = TimePlanExecutionRules.batchAssignGroup(shifted, setOf("a"), "g")
        assertEquals("g", grouped.actions.first { it.id == "a" }.groupId)
        assertNull(grouped.actions.first { it.id == "b" }.groupId)
        val deleted = TimePlanExecutionRules.batchDelete(grouped, setOf("a"))
        assertEquals(listOf("b"), deleted.actions.map { it.id })
    }

    @Test fun removingPointActionsPreventsOrphans() {
        val base = plan().copy(actions = listOf(
            TimePlanActionItem("a", "mid", "A", LocalDateTime.of(2026,8,24,0,10))
        ))
        val cleaned = TimePlanExecutionRules.removePointActions(base, "mid")
        assertTrue(cleaned.actions.isEmpty())
    }

    @Test fun movingActionBetweenPointsPreservesAbsoluteScheduledTime() {
        val base = plan().copy(
            actions = listOf(
                TimePlanActionItem(
                    id = "a",
                    parentPointId = "mid",
                    content = "이동 대상",
                    scheduledDateTime = LocalDateTime.of(2026, 8, 24, 0, 10)
                )
            )
        )

        val moved = TimePlanExecutionRules.moveActionToParentPreservingTime(
            plan = base,
            actionId = "a",
            targetParentPointId = DateTimePlanRules.END_ID
        )

        val action = moved.actions.single { it.id == "a" }
        assertEquals(DateTimePlanRules.END_ID, action.parentPointId)
        assertEquals(LocalDateTime.of(2026, 8, 24, 0, 10), action.scheduledDateTime)
    }


    @Test fun detailCommitDoesNotRestoreOldParentAfterActionWasMovedElsewhere() {
        val original = plan().copy(
            actions = listOf(
                TimePlanActionItem(
                    id = "a",
                    parentPointId = "mid",
                    content = "이동 대상",
                    scheduledDateTime = LocalDateTime.of(2026, 8, 24, 21, 0)
                )
            )
        )

        val moved = original.copy(
            actions = listOf(
                original.actions.single().copy(
                    parentPointId = DateTimePlanRules.END_ID
                )
            )
        )

        // Represents a point-time edit from a detail screen that still has the
        // original Action snapshot.
        val staleDetailCandidate = original.copy(
            end = original.end.copy(
                value = DateTimeValue.explicit(
                    original.end.value.value!!.plusMinutes(10)
                )
            )
        )

        val rebased = TimePlanExecutionRules.rebaseDetailActions(
            base = original,
            candidate = staleDetailCandidate,
            current = moved
        )

        val action = rebased.actions.single()
        assertEquals(DateTimePlanRules.END_ID, action.parentPointId)
        assertEquals(LocalDateTime.of(2026, 8, 24, 21, 0), action.scheduledDateTime)
    }

    @Test fun detailTogetherMoveReplaysOnlyTimeDeltaOnLatestMovedAction() {
        val original = plan().copy(
            actions = listOf(
                TimePlanActionItem(
                    id = "a",
                    parentPointId = "mid",
                    content = "이동 대상",
                    scheduledDateTime = LocalDateTime.of(2026, 8, 24, 21, 0)
                )
            )
        )

        val moved = original.copy(
            actions = listOf(
                original.actions.single().copy(
                    parentPointId = DateTimePlanRules.END_ID,
                    note = "새 비고"
                )
            )
        )

        val togetherMoveCandidate = original.copy(
            actions = listOf(
                original.actions.single().copy(
                    scheduledDateTime = LocalDateTime.of(2026, 8, 24, 21, 10)
                )
            )
        )

        val rebased = TimePlanExecutionRules.rebaseDetailActions(
            base = original,
            candidate = togetherMoveCandidate,
            current = moved
        )

        val action = rebased.actions.single()
        assertEquals(DateTimePlanRules.END_ID, action.parentPointId)
        assertEquals("새 비고", action.note)
        assertEquals(LocalDateTime.of(2026, 8, 24, 21, 10), action.scheduledDateTime)
    }

}
