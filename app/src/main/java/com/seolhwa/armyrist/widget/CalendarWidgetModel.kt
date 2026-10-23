package com.seolhwa.armyrist.widget

import com.seolhwa.armyrist.calendar.*
import com.seolhwa.armyrist.stage2.domain.Checklist
import com.seolhwa.armyrist.timeplan.v3.domain.DateAwareTimePlan
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

internal enum class WidgetLayout { DATE, WEEK, AGENDA, MONTH, MONTH_AGENDA }
internal object CalendarWidgetModel {
    fun layout(width:Int,height:Int)=when {
        width<150 && height<150->WidgetLayout.DATE
        height<170->WidgetLayout.WEEK
        width<240->WidgetLayout.AGENDA
        height>=400->WidgetLayout.MONTH_AGENDA
        else->WidgetLayout.MONTH
    }
    fun monthEntries(month:YearMonth,plans:List<DateAwareTimePlan>,lists:List<Checklist>,today:LocalDate=LocalDate.now()):List<AgendaEntry> =
        (1..month.lengthOfMonth()).flatMap { CalendarAgenda.entriesOn(month.atDay(it),plans,lists,today) }
            .distinctBy { listOf(it.kind.name,it.ownerId,it.itemId.orEmpty(),it.start.toString()) }
            .sortedWith(compareBy<AgendaEntry>{it.start}.thenBy{it.kind.ordinal}.thenBy{it.title})
    /** Keep ongoing ranges and future unfinished items; calendar day marks remain historical. */
    fun remainingEntries(month:YearMonth,plans:List<DateAwareTimePlan>,lists:List<Checklist>,now:LocalDateTime=LocalDateTime.now()):List<AgendaEntry> =
        monthEntries(month,plans,lists,now.toLocalDate()).filter { isRemaining(it,now) }
    internal fun isRemaining(entry:AgendaEntry,now:LocalDateTime):Boolean =
        !entry.complete && (entry.end?.takeUnless { it.isBefore(entry.start) }?.isAfter(now) ?: !entry.start.isBefore(now))
    fun parseMonth(raw:String?,today:LocalDate=LocalDate.now()):YearMonth = runCatching{YearMonth.parse(raw)}.getOrDefault(YearMonth.from(today))
    fun openDate(entry:AgendaEntry,month:YearMonth):LocalDate = entry.start.toLocalDate().coerceAtLeast(month.atDay(1)).coerceAtMost(month.atEndOfMonth())
}
