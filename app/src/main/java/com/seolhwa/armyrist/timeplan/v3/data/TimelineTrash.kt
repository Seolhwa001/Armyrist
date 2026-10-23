package com.seolhwa.armyrist.timeplan.v3.data
import com.seolhwa.armyrist.timeplan.v3.domain.*
import com.seolhwa.armyrist.trash.*
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID

/** Selection backups restore only removed records, preserving later edits to the parent plan. */
object TimelineTrash {
    const val VERSION=7
    fun move(repo:DateAwareTimePlanRepository,trash:CommonTrashRepository,planId:String,keys:Set<String>):Boolean = synchronized(repo){
        val current=repo.getPlan(planId)?:return@synchronized false
        val next=runCatching{TimelineSelection.remove(current,keys)}.getOrNull()?:return@synchronized false
        val removedEvents=current.orderedEvents().filter{e->next.orderedEvents().none{it.id==e.id}}.map{it.id}
        val removedActions=current.actions.filter{a->next.actions.none{it.id==a.id}}.map{it.id}
        val payload=JSONObject().put("plan",DateAwareTimePlanJson.encode(current)).put("events",JSONArray(removedEvents)).put("actions",JSONArray(removedActions))
        val item=trash.moveToTrash(TrashToolType.TIME_PLAN,UUID.randomUUID().toString(),"${current.title} · 선택 항목 ${removedEvents.size+removedActions.size}개",VERSION,payload.toString())?:return@synchronized false
        if(repo.commit(next))true else{trash.permanentlyDelete(item.id);false}
    }
    fun restore(repo:DateAwareTimePlanRepository,payload:String):Boolean = synchronized(repo){runCatching{
        val root=JSONObject(payload);val old=DateAwareTimePlanJson.decode(root.getJSONObject("plan"));val current=repo.getPlan(old.id)?:return@synchronized false
        fun ids(key:String)=root.getJSONArray(key).let{a->(0 until a.length()).map{a.getString(it)}.toSet()}
        val eventIds=ids("events");val actionIds=ids("actions")
        val mids=current.midwayEvents.sortedBy{it.order}.toMutableList();var final=current.finalPoint
        old.orderedEvents().filter{it.id in eventIds}.sortedBy{it.order}.forEach{e->
            if(current.orderedEvents().none{it.id==e.id}){
                if(e.kind==TimeEventKind.FINAL&&final==null)final=e
                else mids.add(e.order.coerceIn(0,mids.size),e.copy(kind=TimeEventKind.MIDWAY))
            }
        }
        val nodeIds=mids.map{it.id}.toSet()+listOfNotNull(final?.id)+listOf("START","END")
        val added=old.actions.filter{it.id in actionIds&&current.actions.none{a->a.id==it.id}}
        require(added.all{it.parentPointId in nodeIds})
        val groupIds=added.mapNotNull{it.groupId}.toSet();val groups=current.actionGroups+old.actionGroups.filter{it.id in groupIds&&current.actionGroups.none{g->g.id==it.id}}
        val candidate=current.copy(midwayEvents=mids.mapIndexed{i,e->e.copy(order=i)},finalPoint=final,actionGroups=groups,actions=current.actions+added,links=emptyList(),updatedAt=System.currentTimeMillis().toString())
        repo.commit(DateTimePlanRules.normalizeTopology(TimePlanExecutionRules.normalizeActionOrder(candidate)))
    }.getOrDefault(false)}
}
