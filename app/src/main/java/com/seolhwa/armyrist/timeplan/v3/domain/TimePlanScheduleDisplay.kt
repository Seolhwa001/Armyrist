package com.seolhwa.armyrist.timeplan.v3.domain

import java.time.LocalDateTime

/** Display-only grouping. Never mutates an action or its absolute timestamp. */
object TimePlanScheduleDisplay {
    fun eventAt(plan:DateAwareTimePlan,time:LocalDateTime):DateTimeEvent? =
        plan.orderedEvents().filter { event ->
            val start=DateTimePlanRules.arrival(event.timeSpec)
            val end=DateTimePlanRules.departure(event.timeSpec)
            start!=null && when {
                end!=null && end>start -> time>=start && time<end
                else -> time==start
            }
        }.sortedWith(compareByDescending<DateTimeEvent>{DateTimePlanRules.arrival(it.timeSpec)}.thenBy{it.order}.thenBy{it.id}).firstOrNull()

    fun rangeAt(plan:DateAwareTimePlan,time:LocalDateTime,excludeKey:String?=null):UnifiedTimePlanEntry? =
        UnifiedTimePlan.entries(plan).filter{it.key!=excludeKey&&!it.untimed&&when{it.ranged->time>=it.start&&time<it.end;it.key.startsWith("N:")->time==it.start;else->false}}
            .sortedWith(compareByDescending<UnifiedTimePlanEntry>{it.start}.thenBy{it.value.order}.thenBy{it.key}).firstOrNull()
    fun groupId(entry:UnifiedTimePlanEntry):String = if(entry.key.startsWith("N:"))entry.key.removePrefix("N:")else entry.key
    fun colorForAction(plan:DateAwareTimePlan,action:TimePlanActionItem):String =
        if((action.durationMinutes?:0)>0)action.markerColor else rangeAt(plan,action.scheduledDateTime,"A:${action.id}")?.value?.markerColor?:action.markerColor
}
