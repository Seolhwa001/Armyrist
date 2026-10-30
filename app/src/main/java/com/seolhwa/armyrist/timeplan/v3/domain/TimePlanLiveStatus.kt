package com.seolhwa.armyrist.timeplan.v3.domain

import java.time.LocalDateTime

/** Wall-clock status. Relative D-Day templates are deliberately excluded. */
object TimePlanLiveStatus {
    data class UpcomingAction(val plan: DateAwareTimePlan, val action: TimePlanActionItem)

    fun interval(plan: DateAwareTimePlan): Pair<LocalDateTime, LocalDateTime>? {
        if (plan.legacyDateMigrationRequired || plan.dateDisplayMode != TimePlanDateDisplayMode.ABSOLUTE) return null
        val start = plan.start.value.value ?: return null
        val end = plan.end.value.value
            ?: plan.finalPoint?.let { DateTimePlanRules.departure(it.timeSpec) }
            ?: return null
        return if (end.isAfter(start)) start to end else null
    }

    fun isActive(plan: DateAwareTimePlan, now: LocalDateTime): Boolean =
        interval(plan)?.let { (start, end) -> !now.isBefore(start) && now.isBefore(end) } == true

    fun activePlans(plans: List<DateAwareTimePlan>, now: LocalDateTime): List<DateAwareTimePlan> =
        plans.filter { isActive(it, now) }.sortedWith(
            compareBy<DateAwareTimePlan> { interval(it)!!.second }.thenBy { it.id }
        )

    fun nextAction(plans: List<DateAwareTimePlan>, now: LocalDateTime): UpcomingAction? =
        plans.asSequence().filter { interval(it) != null }.flatMap { plan ->
            UnifiedTimePlan.actions(plan).asSequence()
                .filter { it.completionState == ActionCompletionState.INCOMPLETE && !it.scheduledDateTime.isBefore(now) }
                .map { UpcomingAction(plan, it) }
        }.minWithOrNull(compareBy<UpcomingAction> { it.action.scheduledDateTime }
            .thenBy { it.plan.id }.thenBy { it.action.order }.thenBy { it.action.id })
}
