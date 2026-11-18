package com.seolhwa.armyrist.timeplan.v3.domain

import java.time.temporal.ChronoUnit

/** Explicitly moving the base date moves all entries by the same calendar-day delta. */
object TimePlanPeriodChanges {
    fun apply(current: DateAwareTimePlan, edited: DateAwareTimePlan): DateAwareTimePlan {
        val old = requireNotNull(current.start.value.value)
        val start = requireNotNull(edited.start.value.value)
        val end = requireNotNull(edited.end.value.value)
        require(end >= start)
        val days = ChronoUnit.DAYS.between(old.toLocalDate(), start.toLocalDate())
        fun shift(value: DateTimeValue) = value.copy(value = value.value?.plusDays(days))
        fun event(e: DateTimeEvent): DateTimeEvent = e.copy(timeSpec = when (val spec=e.timeSpec) {
            EventDateTimeSpec.Unspecified -> spec
            is EventDateTimeSpec.Single -> spec.copy(value=shift(spec.value))
            is EventDateTimeSpec.Range -> spec.copy(start=shift(spec.start),end=shift(spec.end))
        })
        return DateTimePlanRules.normalizeTopology(current.copy(
            start=edited.start,end=edited.end,dateDisplayMode=edited.dateDisplayMode,
            midwayEvents=current.midwayEvents.map(::event),finalPoint=current.finalPoint?.let(::event),
            actions=current.actions.map { it.copy(scheduledDateTime=it.scheduledDateTime.plusDays(days)) },
            links=emptyList(),updatedAt=System.currentTimeMillis().toString()
        ))
    }
}
