package com.seolhwa.armyrist.calendar
import com.seolhwa.armyrist.stage2.domain.*
import com.seolhwa.armyrist.timeplan.v3.domain.*
import java.time.LocalDate
import java.time.LocalDateTime

enum class AgendaKind(val label: String) { PLAN("시간계획"), EVENT("일정"), ACTION("실시사항"), CHECKLIST("체크리스트") }
data class AgendaEntry(val kind: AgendaKind,val ownerId: String,val itemId: String?,val title: String,val ownerTitle: String,val start: LocalDateTime,val end: LocalDateTime?=null,val complete: Boolean=false)
object CalendarAgenda {
    fun entriesOn(date: LocalDate,plans: List<DateAwareTimePlan>,checklists: List<Checklist>,today: LocalDate=LocalDate.now()): List<AgendaEntry> = buildList {
        fun overlaps(start: LocalDateTime,end: LocalDateTime?) = !date.isBefore(start.toLocalDate()) && !date.isAfter((end?.takeUnless { it.isBefore(start) } ?: start).toLocalDate())
        plans.filter { !it.legacyDateMigrationRequired && it.dateDisplayMode==TimePlanDateDisplayMode.ABSOLUTE }.forEach { plan ->
            val start=plan.start.value.value ?: plan.end.value.value
            if(start!=null && overlaps(start,plan.end.value.value)) add(AgendaEntry(AgendaKind.PLAN,plan.id,null,plan.title,"",start,plan.end.value.value))
            plan.orderedEvents().forEach { event ->
                val a=DateTimePlanRules.arrival(event.timeSpec);val b=DateTimePlanRules.departure(event.timeSpec)
                if(a!=null && overlaps(a,b)) add(AgendaEntry(AgendaKind.EVENT,plan.id,event.id,event.name,plan.title,a,b))
            }
            plan.actions.filter { it.scheduledDateTime.toLocalDate()==date }.forEach { a -> add(AgendaEntry(AgendaKind.ACTION,plan.id,a.id,a.content,"${plan.title} › ${TimePlanExecutionRules.pointName(plan,a.parentPointId)}",a.scheduledDateTime,complete=a.completionState==ActionCompletionState.COMPLETE)) }
        }
        checklists.forEach { list -> list.items.forEach { item ->
            val day=item.scheduledDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today;val minutes=item.scheduledTimeMinutes
            if(day==date && minutes!=null && minutes in 0..1439) add(AgendaEntry(AgendaKind.CHECKLIST,list.id,item.id,item.name,list.title,day.atTime(minutes/60,minutes%60),complete=item.status==ChecklistStatus.COMPLETE))
        } }
    }.sortedWith(compareBy<AgendaEntry> { it.start }.thenBy { it.kind.ordinal }.thenBy { it.title })
}
