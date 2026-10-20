package com.seolhwa.armyrist.notification

import com.seolhwa.armyrist.timeplan.v3.domain.*
import java.time.LocalDateTime

internal enum class AlarmDisposition { SCHEDULE, PRESERVE_DELIVERED, CLEAR }

internal object TimePlanAlarmPolicy {
    fun disposition(
        plan: DateAwareTimePlan,
        action: TimePlanActionItem,
        now: LocalDateTime,
        permissionGranted: Boolean
    ): AlarmDisposition = when {
        !permissionGranted ||
            plan.dateDisplayMode == TimePlanDateDisplayMode.RELATIVE_D_DAY ||
            action.completionState != ActionCompletionState.INCOMPLETE ||
            action.notificationMode == ActionNotificationMode.NONE -> AlarmDisposition.CLEAR
        action.scheduledDateTime > now -> AlarmDisposition.SCHEDULE
        else -> AlarmDisposition.PRESERVE_DELIVERED
    }
}
