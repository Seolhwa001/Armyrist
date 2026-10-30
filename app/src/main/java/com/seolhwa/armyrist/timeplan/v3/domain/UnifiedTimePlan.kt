package com.seolhwa.armyrist.timeplan.v3.domain

import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** One user-facing activity; legacy schedules are adapted without rewriting stored records. */
data class UnifiedTimePlanEntry(val key:String,val value:TimePlanActionItem,val locked:Boolean=false,val untimed:Boolean=false) {
    val start get()=value.scheduledDateTime
    val end:LocalDateTime get()=runCatching{start.plusMinutes(value.durationMinutes?:0)}.getOrDefault(start)
    val ranged get()=end>start
}
data class UnifiedTimePlanDraft(
    val title:String,val start:LocalDateTime,val durationMinutes:Long?=null,
    val note:String?=null,val color:String="#2E55CC",val notificationMode:ActionNotificationMode=ActionNotificationMode.NONE,val locked:Boolean=false,val groupId:String?=null
)
object UnifiedTimePlan {
    const val EVENT_ALARM_PREFIX="legacy-event:"
    fun entries(plan:DateAwareTimePlan):List<UnifiedTimePlanEntry> = (
        plan.orderedEvents().map { e ->
            val start=DateTimePlanRules.arrival(e.timeSpec)
            val end=DateTimePlanRules.departure(e.timeSpec)
            UnifiedTimePlanEntry("N:${e.id}",TimePlanActionItem(
                id=EVENT_ALARM_PREFIX+e.id,content=e.name,scheduledDateTime=start?:end?:plan.start.value.value?:LocalDateTime.of(2000,1,1,0,0),
                durationMinutes=if(start!=null&&end!=null&&end>start)Duration.between(start,end).toMinutes() else null,
                completionState=e.completionState,notificationEnabled=e.notificationMode!=ActionNotificationMode.NONE,notificationMode=e.notificationMode,
                createdAt=plan.createdAt,updatedAt=plan.updatedAt,note=e.note,groupId=e.groupId,order=e.order,markerColor=e.color,timeLocked=e.dateTimeLocked),e.dateTimeLocked,start==null)
        }+plan.actions.map{UnifiedTimePlanEntry("A:${it.id}",it,it.timeLocked)}
    ).sortedWith(compareBy<UnifiedTimePlanEntry>{it.untimed}.thenBy{it.start}.thenBy{it.key})
    fun actions(plan:DateAwareTimePlan)=entries(plan).filterNot{it.untimed}.map{it.value}
    fun replaceEvent(plan:DateAwareTimePlan,event:DateTimeEvent):DateAwareTimePlan = plan.copy(
        midwayEvents=plan.midwayEvents.map{if(it.id==event.id)event else it},
        finalPoint=plan.finalPoint?.let{if(it.id==event.id)event else it})
    fun validateDraft(draft:UnifiedTimePlanDraft):Boolean = draft.title.isNotBlank() &&
        draft.color.matches(Regex("#[0-9a-fA-F]{6}")) && (draft.durationMinutes==null||draft.durationMinutes>=0) &&
        runCatching{draft.start.plusMinutes(draft.durationMinutes?:0)}.isSuccess
    /** Extend both ends and reserve space after a boundary point; never clamp an item onto the end line. */
    fun scale(plan:DateAwareTimePlan):TimelineScale? {
        val timed=entries(plan).filterNot{it.untimed}
        val starts=listOfNotNull(plan.start.value.value,plan.end.value.value)+timed.map{it.start}
        if(starts.isEmpty())return null
        val start=starts.min().truncatedTo(ChronoUnit.HOURS)
        val ends=listOfNotNull(plan.end.value.value)+timed.map{if(it.ranged)it.end else it.start.plusMinutes(30)}
        val last=(ends.maxOrNull()?:start.plusHours(1)).coerceAtLeast(start.plusMinutes(1))
        val end=if(last==last.truncatedTo(ChronoUnit.HOURS))last else last.truncatedTo(ChronoUnit.HOURS).plusHours(1)
        return runCatching{TimelineScale(start,end)}.getOrNull()
    }
    fun lanes(entries:List<UnifiedTimePlanEntry>):Map<String,Int> {
        val ends=mutableListOf<LocalDateTime>();val result=mutableMapOf<String,Int>()
        entries.filterNot{it.untimed}.sortedWith(compareBy<UnifiedTimePlanEntry>{it.start}.thenBy{it.key}).forEach { e ->
            val end=maxOf(e.end,e.start.plusMinutes(30));var lane=ends.indexOfFirst{it<=e.start}
            if(lane<0){lane=ends.size;ends+=end}else ends[lane]=end
            result[e.key]=lane
        };return result
    }
    fun keysInRange(plan:DateAwareTimePlan,a:LocalDateTime,b:LocalDateTime):Set<String> {
        val low=minOf(a,b);val high=maxOf(a,b)
        return entries(plan).filter{!it.untimed&&it.start<=high&&it.end>=low}.map{it.key}.toSet()
    }
}
