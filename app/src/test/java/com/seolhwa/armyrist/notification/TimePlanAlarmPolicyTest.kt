package com.seolhwa.armyrist.notification

import com.seolhwa.armyrist.timeplan.v3.domain.*
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TimePlanAlarmPolicyTest {
    private val now = LocalDateTime.of(2026, 10, 20, 9, 0)
    private val plan = DateAwareTimePlan(id = "p", title = "계획", createdAt = "0", updatedAt = "0")
    private val action = TimePlanActionItem(
        id = "a", parentPointId = "START", content = "실시",
        scheduledDateTime = now.minusMinutes(1), notificationMode = ActionNotificationMode.MUSIC
    )

    @Test fun openingAppDoesNotSilenceOverdueMusic() {
        assertEquals(AlarmDisposition.PRESERVE_DELIVERED, TimePlanAlarmPolicy.disposition(plan, action, now, true))
    }
    @Test fun editingAnotherPlanDoesNotDismissUnreadSimpleNotification() {
        assertEquals(AlarmDisposition.PRESERVE_DELIVERED, TimePlanAlarmPolicy.disposition(
            plan, action.copy(notificationMode = ActionNotificationMode.SIMPLE), now, true))
    }
    @Test fun completingRingingActionRequiresFullCancellation() {
        assertEquals(AlarmDisposition.CLEAR, TimePlanAlarmPolicy.disposition(
            plan, action.copy(completionState = ActionCompletionState.COMPLETE), now, true))
    }
    @Test fun disablingReminderRequiresFullCancellation() {
        assertEquals(AlarmDisposition.CLEAR, TimePlanAlarmPolicy.disposition(
            plan, action.copy(notificationMode = ActionNotificationMode.NONE), now, true))
    }
    @Test fun movingActionIntoFutureSchedulesAgain() {
        assertEquals(AlarmDisposition.SCHEDULE, TimePlanAlarmPolicy.disposition(
            plan, action.copy(scheduledDateTime = now.plusMinutes(5)), now, true))
    }
    @Test fun exactDueTimeRetainsDeliveredNotification() {
        assertEquals(AlarmDisposition.PRESERVE_DELIVERED, TimePlanAlarmPolicy.disposition(
            plan, action.copy(scheduledDateTime = now), now, true))
    }
    @Test fun permissionRevocationClearsAlarm() {
        assertEquals(AlarmDisposition.CLEAR, TimePlanAlarmPolicy.disposition(plan, action, now, false))
    }
    @Test fun relativePlansCannotKeepAbsoluteAlarm() {
        assertEquals(AlarmDisposition.CLEAR, TimePlanAlarmPolicy.disposition(
            plan.copy(dateDisplayMode = TimePlanDateDisplayMode.RELATIVE_D_DAY), action, now, true))
    }
}
