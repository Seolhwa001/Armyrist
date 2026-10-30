package com.seolhwa.armyrist.timeplan.v3.domain

import java.time.LocalDateTime
import java.time.Duration
import java.time.temporal.ChronoUnit

/** Virtual hourly rows preserve clock spacing without allocating a view for every hour. */
data class TimelineSlice(val start:LocalDateTime,val end:LocalDateTime){
    val minutes:Double get()=Duration.between(start,end).seconds/60.0
    fun fraction(time:LocalDateTime)= (Duration.between(start,time).seconds/60.0/minutes).coerceIn(0.0,1.0).toFloat()
}
class TimelineScale(val start:LocalDateTime,val end:LocalDateTime){
    init{require(end.isAfter(start));require(Duration.between(start,end).toHours()<Int.MAX_VALUE-2)}
    private val nextHour=start.truncatedTo(ChronoUnit.HOURS).plusHours(1)
    val count:Int=if(!end.isAfter(nextHour))1 else 1+((Duration.between(nextHour,end).seconds+3599)/3600).toInt()
    fun slice(index:Int):TimelineSlice{
        require(index in 0 until count)
        val a=if(index==0)start else nextHour.plusHours(index.toLong()-1)
        val b=if(index==0)nextHour else a.plusHours(1)
        return TimelineSlice(a,minOf(b,end))
    }
    fun index(time:LocalDateTime):Int=if(time<nextHour)0 else (1+Duration.between(nextHour,time).toHours().toInt()).coerceIn(0,count-1)
    companion object{fun from(plan:DateAwareTimePlan):TimelineScale?=runCatching{TimelineScale(plan.start.value.value!!,plan.end.value.value!!)}.getOrNull()}
}
object TimelineSelection {
    fun keysInRange(plan:DateAwareTimePlan,a:LocalDateTime,b:LocalDateTime):Set<String>{
        val low=minOf(a,b);val high=maxOf(a,b)
        return buildSet {
            DateTimePlanRules.nodeIds(plan).forEach{id->
                val start=DateTimePlanRules.nodeArrival(plan,id)?:return@forEach
                val end=DateTimePlanRules.nodeDeparture(plan,id)?:start
                if(!start.isAfter(high)&&!maxOf(start,end).isBefore(low))add("N:$id")
            }
            plan.actions.filter{!it.scheduledDateTime.isBefore(low)&&!it.scheduledDateTime.isAfter(high)}.forEach{add("A:${it.id}")}
        }
    }
    fun remove(plan:DateAwareTimePlan,keys:Set<String>):DateAwareTimePlan{
        val nodes=plan.orderedEvents().filter{"N:${it.id}" in keys}.map{it.id}.toSet()
        require(plan.orderedEvents().none{it.id in nodes&&it.dateTimeLocked}){"고정된 일정은 고정을 해제한 뒤 삭제해주세요."}
        val actions=plan.actions.filter{"A:${it.id}" in keys}.map{it.id}.toSet()
        require(plan.actions.none{it.id in actions && it.timeLocked}){"고정된 항목은 고정을 해제한 뒤 삭제해주세요."}
        require(nodes.isNotEmpty()||actions.isNotEmpty()){"시작·종료 지점은 삭제하지 않습니다. 일정 또는 실시사항을 선택해주세요."}
        return DateTimePlanRules.normalizeTopology(TimePlanExecutionRules.normalizeActionOrder(plan.copy(
            midwayEvents=plan.midwayEvents.filterNot{it.id in nodes}.sortedBy{it.order}.mapIndexed{i,e->e.copy(order=i)},
            finalPoint=plan.finalPoint?.takeUnless{it.id in nodes},actions=plan.actions.filterNot{it.id in actions},links=emptyList(),updatedAt=System.currentTimeMillis().toString())))
    }
    fun lanes(plan:DateAwareTimePlan):Map<String,Int>{
        val ends=mutableListOf<LocalDateTime>();val result=mutableMapOf<String,Int>()
        plan.orderedEvents().mapNotNull{e->DateTimePlanRules.arrival(e.timeSpec)?.let{Triple(e,it,DateTimePlanRules.departure(e.timeSpec)?:it)}}.sortedBy{it.second}.forEach{(e,a,b)->
            var lane=ends.indexOfFirst{!it.isAfter(a)}
            if(lane<0){lane=ends.size;ends+=maxOf(a.plusMinutes(1),b)}else ends[lane]=maxOf(a.plusMinutes(1),b)
            result[e.id]=lane
        };return result
    }
}
