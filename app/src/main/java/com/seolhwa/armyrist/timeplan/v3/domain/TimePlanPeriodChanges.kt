package com.seolhwa.armyrist.timeplan.v3.domain

import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** A preview of an out-of-period entry. Null target means its day cannot be inferred safely. */
data class PeriodDateRepair(val key: String, val title: String, val from: LocalDateTime, val target: LocalDateTime?)

object TimePlanPeriodChanges {
    /** Move valid dates together; repair stranded dates only when explicitly requested. */
    fun apply(current: DateAwareTimePlan, edited: DateAwareTimePlan, repairOutsideDates: Boolean = false): DateAwareTimePlan {
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
        var changed = current.copy(
            start=edited.start,end=edited.end,dateDisplayMode=edited.dateDisplayMode,
            midwayEvents=current.midwayEvents.map(::event),finalPoint=current.finalPoint?.let(::event),
            actions=current.actions.map { it.copy(scheduledDateTime=it.scheduledDateTime.plusDays(days)) },
            links=emptyList(),updatedAt=System.currentTimeMillis().toString()
        )
        if (repairOutsideDates) {
            val repairs = candidates(changed).filter { it.target != null }.associateBy { it.key }
            fun repairEvent(e: DateTimeEvent): DateTimeEvent {
                val repair = repairs["N:${e.id}"] ?: return e
                val delta = ChronoUnit.DAYS.between(repair.from.toLocalDate(), repair.target!!.toLocalDate())
                fun move(v:DateTimeValue)=v.copy(value=v.value?.plusDays(delta))
                return e.copy(timeSpec=when(val spec=e.timeSpec){
                    EventDateTimeSpec.Unspecified -> spec
                    is EventDateTimeSpec.Single -> spec.copy(value=move(spec.value))
                    is EventDateTimeSpec.Range -> spec.copy(start=move(spec.start),end=move(spec.end))
                })
            }
            changed=changed.copy(
                midwayEvents=changed.midwayEvents.map(::repairEvent),
                finalPoint=changed.finalPoint?.let(::repairEvent),
                actions=changed.actions.map { action -> repairs["A:${action.id}"]?.target?.let { action.copy(scheduledDateTime=it) } ?: action }
            )
        }
        return DateTimePlanRules.normalizeTopology(changed)
    }

    fun previewRepairs(current:DateAwareTimePlan,edited:DateAwareTimePlan):List<PeriodDateRepair> =
        candidates(apply(current,edited))

    private fun candidates(plan:DateAwareTimePlan):List<PeriodDateRepair> {
        val start=requireNotNull(plan.start.value.value)
        val end=requireNotNull(plan.end.value.value)
        return UnifiedTimePlan.entries(plan).filter { !it.untimed && (it.start<start || it.start>end) }.map { entry ->
            // Preserve clock and duration. A 24h+ window may offer several valid days;
            // never guess between them, or collapse a multi-day plan onto one date.
            val latestStart=runCatching { end.minusMinutes(entry.value.durationMinutes?:0) }.getOrNull()
            var first=start.toLocalDate().atTime(entry.start.toLocalTime())
            if(first<start)first=first.plusDays(1)
            val target=if(latestStart!=null && first<=latestStart &&
                Duration.between(first,latestStart).toDays()==0L)first else null
            PeriodDateRepair(entry.key,entry.value.content,entry.start,target)
        }
    }
}
