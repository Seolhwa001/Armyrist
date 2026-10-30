package com.seolhwa.armyrist.timeplan.v3.data

import android.content.Context
import com.seolhwa.armyrist.data.GuardedSnapshotStore
import com.seolhwa.armyrist.data.reportStorageError
import com.seolhwa.armyrist.notification.TimePlanActionNotificationManager
import com.seolhwa.armyrist.timeplan.domain.RevisedTimePlan
import com.seolhwa.armyrist.timeplan.domain.EventTimeSpec as LegacySpec
import com.seolhwa.armyrist.timeplan.domain.TimeEventKind as LegacyKind
import com.seolhwa.armyrist.timeplan.v3.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

class DateAwareTimePlanRepository(context: Context) {
    private val notificationChanges = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val notificationCompletionRevision: kotlinx.coroutines.flow.StateFlow<Long> = notificationChanges

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val snapshots = GuardedSnapshotStore(
        readRaw = { prefs.getString(KEY, null) },
        writeRaw = { raw ->
            val editor = prefs.edit()
            if (raw == null) editor.remove(KEY) else editor.putString(KEY, raw)
            editor.commit()
        },
        decode = ::decodeSnapshot,
        encode = ::encodeSnapshot,
        onError = { reportStorageError(appContext, it) }
    )
    @Volatile private var plans: List<DateAwareTimePlan> = snapshots.load(emptyList())

    internal val storageReadable: Boolean get() = snapshots.readable

    @Synchronized fun reloadFromPersistence() { plans = snapshots.load(emptyList(), plans) }

    @Synchronized
    fun getPlans(): List<DateAwareTimePlan> {
        val fallback = plans.sortedByDescending { it.updatedAt.toLongOrNull() ?: 0L }
        val storedOrder = loadPlanOrder()
        if (storedOrder.isEmpty()) return fallback

        val byId = plans.associateBy { it.id }
        val ordered = storedOrder.mapNotNull(byId::get)
        val missing = fallback.filter { it.id !in storedOrder.toSet() }
        return ordered + missing
    }

    @Synchronized
    fun movePlan(planId: String, direction: Int): Boolean {
        if (direction == 0) return true
        val displayed = getPlans().toMutableList()
        val from = displayed.indexOfFirst { it.id == planId }
        if (from < 0) return false
        val to = (from + direction).coerceIn(0, displayed.lastIndex)
        if (from == to) return true

        val item = displayed.removeAt(from)
        displayed.add(to, item)
        return savePlanOrder(displayed.map { it.id })
    }

    /**
     * Persists the complete user-visible TimePlan order after drag/drop.
     * Unknown/duplicate IDs are rejected so reorder cannot silently lose plans.
     */
    @Synchronized
    fun setPlanOrder(orderedIds: List<String>): Boolean {
        val currentIds = plans.map { it.id }.toSet()
        if (orderedIds.size != currentIds.size) return false
        if (orderedIds.distinct().size != orderedIds.size) return false
        if (orderedIds.toSet() != currentIds) return false
        return savePlanOrder(orderedIds)
    }

    @Synchronized fun getPlan(id: String): DateAwareTimePlan? = plans.firstOrNull { it.id == id }
    @Synchronized fun contains(id: String): Boolean = plans.any { it.id == id }

    @Synchronized
    fun createPlan(now: LocalDateTime = LocalDateTime.now()): DateAwareTimePlan? {
        val end = now.plusHours(1)
        val plan = DateTimePlanRules.normalizeTopology(
            DateAwareTimePlan(
                id = UUID.randomUUID().toString(),
                title = "새 시간계획",
                start = DateTimeAnchor(DateTimeValue.explicit(now.withSecond(0).withNano(0))),
                end = DateTimeAnchor(DateTimeValue.explicit(end.withSecond(0).withNano(0))),
                createdAt = System.currentTimeMillis().toString(),
                updatedAt = System.currentTimeMillis().toString()
            )
        )
        val next = plans + plan
        if (!persist(next)) return null
        plans = next
        if (loadPlanOrder().isNotEmpty()) {
            savePlanOrder(listOf(plan.id) + getPlans().map { it.id }.filterNot { it == plan.id })
        }
        TimePlanActionNotificationManager.reconcile(appContext, this)
        return plan
    }

    @Synchronized
    fun saveAction(planId: String, candidate: TimePlanActionItem): Boolean {
        val current = getPlan(planId) ?: return false
        val previous = current.actions.firstOrNull { it.id == candidate.id }
        val action = candidate.copy(completionState=previous?.completionState ?: candidate.completionState)
        val actions = if(previous == null) current.actions+action else current.actions.map { if(it.id == action.id) action else it }
        return commit(TimePlanExecutionRules.normalizeActionOrder(current.copy(actions=actions,updatedAt=System.currentTimeMillis().toString())))
    }

    @Synchronized
    fun saveUnifiedEntry(planId:String,key:String?,draft:UnifiedTimePlanDraft):Boolean {
        if(!UnifiedTimePlan.validateDraft(draft))return false
        val current=getPlan(planId)?:return false
        val old=key?.let{k->UnifiedTimePlan.entries(current).firstOrNull{it.key==k}?:return false}
        if(old?.locked==true && draft.locked && (old.start!=draft.start || old.value.durationMinutes!=draft.durationMinutes))return false
        if(key?.startsWith("N:")==true) {
            val event=current.orderedEvents().firstOrNull{it.id==key.removePrefix("N:")}?:return false
            val unchangedTime=old!!.start==draft.start && old.value.durationMinutes==draft.durationMinutes && !old.untimed
            val spec=if(unchangedTime)event.timeSpec else if((draft.durationMinutes?:0)>0)EventDateTimeSpec.Range(DateTimeValue.explicit(draft.start),DateTimeValue.explicit(draft.start.plusMinutes(draft.durationMinutes!!)))else EventDateTimeSpec.Single(DateTimeValue.explicit(draft.start))
            val next=event.copy(name=draft.title.trim(),timeSpec=spec,note=draft.note?.trim()?.ifBlank{null},color=draft.color,notificationMode=draft.notificationMode,dateTimeLocked=draft.locked,groupId=draft.groupId)
            return commit(UnifiedTimePlan.replaceEvent(current,next).copy(updatedAt=System.currentTimeMillis().toString()))
        }
        val item=(old?.value?:TimePlanActionItem(content=draft.title.trim(),scheduledDateTime=draft.start,order=current.actions.size)).copy(
            content=draft.title.trim(),scheduledDateTime=draft.start,durationMinutes=draft.durationMinutes,note=draft.note?.trim()?.ifBlank{null},
            groupId=draft.groupId,markerColor=draft.color,notificationEnabled=draft.notificationMode!=ActionNotificationMode.NONE,notificationMode=draft.notificationMode,timeLocked=draft.locked,updatedAt=System.currentTimeMillis().toString())
        return saveAction(planId,item)
    }
    @Synchronized
    fun addUnifiedEntries(planId:String,drafts:List<UnifiedTimePlanDraft>):Boolean {
        if(drafts.isEmpty()||drafts.any{!UnifiedTimePlan.validateDraft(it)})return false
        val current=getPlan(planId)?:return false
        val additions=drafts.mapIndexed{index,d->TimePlanActionItem(content=d.title.trim(),scheduledDateTime=d.start,durationMinutes=d.durationMinutes,note=d.note,markerColor=d.color,timeLocked=d.locked,groupId=d.groupId,notificationMode=d.notificationMode,notificationEnabled=d.notificationMode!=ActionNotificationMode.NONE,order=current.actions.size+index)}
        return commit(TimePlanExecutionRules.normalizeActionOrder(current.copy(actions=current.actions+additions,updatedAt=System.currentTimeMillis().toString())))
    }
    @Synchronized
    fun setUnifiedCompletion(planId:String,key:String,state:ActionCompletionState):Boolean {
        val current=getPlan(planId)?:return false
        val entry=UnifiedTimePlan.entries(current).firstOrNull{it.key==key}?:return false
        val candidate=if(key.startsWith("N:")) {
            val event=current.orderedEvents().firstOrNull{it.id==key.removePrefix("N:")}?:return false
            UnifiedTimePlan.replaceEvent(current,event.copy(completionState=state))
        } else current.copy(actions=current.actions.map{if(it.id==entry.value.id)it.copy(completionState=state,updatedAt=System.currentTimeMillis().toString())else it})
        val saved=commit(candidate.copy(updatedAt=System.currentTimeMillis().toString()))
        if(saved)notificationChanges.value+=1
        return saved
    }

    @Synchronized
    fun commit(value: DateAwareTimePlan): Boolean {
        if (value.legacyDateMigrationRequired) return false
        if (DateTimePlanRules.validateForPersistence(value).isNotEmpty()) return false
        val normalized = DateTimePlanRules.normalizeTopology(value)
        val next = if (plans.any { it.id == value.id }) plans.map { if (it.id == value.id) normalized else it } else plans + normalized
        if (!persist(next)) return false
        plans = next
        TimePlanActionNotificationManager.reconcile(appContext, this)
        return true
    }

    /** A notification completes one existing action, never toggles it or rewrites a stale plan. */
    @Synchronized
    fun completeActionFromNotification(planId: String, actionId: String): Boolean {
        reloadFromPersistence()
        if (!storageReadable) return false
        val plan = getPlan(planId) ?: return false
        val action = plan.actions.firstOrNull { it.id == actionId }
        if(action==null) {
            val entry=UnifiedTimePlan.entries(plan).firstOrNull{it.value.id==actionId}?:return false
            val saved=setUnifiedCompletion(planId,entry.key,ActionCompletionState.COMPLETE)
            if(saved)TimePlanActionNotificationManager.cancel(appContext,planId,actionId)
            return saved
        }
        if (action.completionState != ActionCompletionState.COMPLETE) {
            val now = System.currentTimeMillis().toString()
            val changed = plan.copy(
                actions = plan.actions.map { item ->
                    if (item.id == actionId) item.copy(completionState = ActionCompletionState.COMPLETE, updatedAt = now)
                    else item
                },
                updatedAt = now
            )
            val next = plans.map { if (it.id == planId) changed else it }
            if (!persist(next)) return false
            plans = next
        }
        // Open execution screens redraw their completion circles without navigation.
        notificationChanges.value += 1
        TimePlanActionNotificationManager.cancel(appContext, planId, actionId)
        return true
    }

    @Synchronized fun delete(id: String) {
        val next = plans.filterNot { it.id == id }
        if (persist(next)) {
            plans = next
            val storedOrder = loadPlanOrder()
            if (storedOrder.isNotEmpty()) {
                savePlanOrder(storedOrder.filterNot { it == id })
            }
            runCatching {
                TimePlanActionNotificationManager.reconcile(appContext, this)
            }
        }
    }

    /**
     * Restores a complete TimePlan snapshot that was previously moved to Common Trash.
     *
     * The snapshot originated from this repository, so restore does not reinterpret
     * Point/Action data or run migration guesses. Existing IDs are never overwritten.
     */
    @Synchronized
    fun restoreDeletedPlan(snapshot: DateAwareTimePlan): Boolean {
        if (plans.any { it.id == snapshot.id }) return false

        val restored =
            DateTimePlanRules.normalizeTopology(
                snapshot.copy(
                    updatedAt = System.currentTimeMillis().toString()
                )
            )

        val next = plans + restored
        if (!persist(next)) return false

        plans = next
        if (loadPlanOrder().isNotEmpty()) {
            savePlanOrder(listOf(restored.id) + getPlans().map { it.id }.filterNot { it == restored.id })
        }

        // Restore future notification schedules. Scheduling failure is secondary to
        // successfully restoring the user's local data.
        runCatching {
            TimePlanActionNotificationManager.reconcile(appContext, this)
        }

        return true
    }

    /**
     * Atomically removes one MIDWAY/FINAL event without ever removing the owning plan.
     *
     * UI code must not synthesize and commit a partially-mutated topology during an
     * active edit dialog. The repository owns the complete transaction:
     * current snapshot -> remove event -> remove child actions -> rebuild order/links
     * -> validate -> persist -> publish.
     */
    @Synchronized
    fun deleteEvent(planId: String, eventId: String): Boolean =
        runCatching {
            deleteEventInternal(planId, eventId)
        }.onFailure {
            android.util.Log.e(
                "Armyrist-TimePlan",
                "Atomic event deletion failed plan=$planId event=$eventId",
                it
            )
        }.getOrDefault(false)

    private fun deleteEventInternal(planId: String, eventId: String): Boolean {
        val current = plans.firstOrNull { it.id == planId } ?: return false

        val isFinal = current.finalPoint?.id == eventId
        val isMidway = current.midwayEvents.any { it.id == eventId }
        if (!isFinal && !isMidway) return false

        // Deletion is intentionally implemented as a narrowing transaction.
        // Do not run whole-plan validators here: older plans can legitimately carry
        // unresolved temporal conflicts or legacy Action defects that are unrelated
        // to the point being removed.
        val survivingMidways =
            current.midwayEvents
                .filterNot { it.id == eventId }
                .sortedBy { it.order }
                .mapIndexed { index, event -> event.copy(order = index) }

        val withoutPoint =
            current.copy(
                midwayEvents = survivingMidways,
                finalPoint = if (isFinal) null else current.finalPoint,
                // Old links are discarded completely. They will be rebuilt only
                // from the nodes that survive this transaction.
                links = emptyList(),
                // Actions are independent; deleting a schedule never deletes its actions.
                actions = current.actions,
                updatedAt = System.currentTimeMillis().toString()
            )

        val survivingNodeIds = DateTimePlanRules.nodeIds(withoutPoint).toSet()
        val survivingGroupIds = withoutPoint.actionGroups.map { it.id }.toSet()

        // Sanitize only references that cannot survive the delete.
        // Unrelated Action contents/completion/notification state are preserved.
        val sanitized =
            TimePlanExecutionRules.normalizeActionOrder(
                withoutPoint.copy(
                    actions = withoutPoint.actions
                        .map { action ->
                            if (action.groupId != null && action.groupId !in survivingGroupIds) {
                                action.copy(groupId = null)
                            } else {
                                action
                            }
                        }
                )
            )

        val rebuilt =
            DateTimePlanRules.normalizeTopology(
                sanitized.copy(links = emptyList())
            )

        // Persist the complete replacement snapshot once. There are deliberately no
        // additional whole-plan validation gates between reconstruction and persist.
        val next = plans.map { plan ->
            if (plan.id == planId) rebuilt else plan
        }

        if (!persist(next)) {
            android.util.Log.e(
                "Armyrist-TimePlan",
                "MIDWAY delete persist failed plan=$planId event=$eventId"
            )
            return false
        }

        plans = next

        // Alarm reconciliation is secondary. A platform-side scheduling failure must
        // never undo or report failure for an already-persisted point deletion.
        runCatching {
            TimePlanActionNotificationManager.reconcile(appContext, this)
        }.onFailure {
            android.util.Log.e(
                "Armyrist-TimePlan",
                "Post-delete notification reconcile failed plan=$planId event=$eventId",
                it
            )
        }

        return true
    }

    @Synchronized
    fun importAsNew(source: DateAwareTimePlan): String? {
        val fresh = com.seolhwa.armyrist.timeplan.v3.portable.TimePlanPortableV3Codec.regenerateIds(source)
        if (DateTimePlanRules.validateForPersistence(fresh).isNotEmpty()) return null
        val next = plans + DateTimePlanRules.normalizeTopology(fresh)
        if (!persist(next)) return null
        plans = next
        if (loadPlanOrder().isNotEmpty()) {
            savePlanOrder(listOf(fresh.id) + getPlans().map { it.id }.filterNot { it == fresh.id })
        }
        TimePlanActionNotificationManager.reconcile(appContext, this)
        return fresh.id
    }

    @Synchronized
    fun replaceAll(restored: List<DateAwareTimePlan>): Boolean {
        if (restored.any { DateTimePlanRules.validateForPersistence(it).isNotEmpty() }) return false
        val next = restored.map(DateTimePlanRules::normalizeTopology)
        if (!persist(next)) return false
        plans = next
        savePlanOrder(next.map { it.id })
        TimePlanActionNotificationManager.reconcile(appContext, this)
        return true
    }

    fun exportSnapshot(): String {
        check(snapshots.readable) { "Cannot export unreadable TimePlan data." }
        return JSONObject()
        .put("schemaVersion", SCHEMA)
        .put("plans", JSONArray().apply { getPlans().forEach { put(DateAwareTimePlanJson.encode(it)) } })
        .toString()
    }

    /**
     * User-confirmed atomic legacy date migration. No persistence happens before the candidate validates.
     */
    @Synchronized
    fun migrateLegacy(legacy: RevisedTimePlan, baseDate: LocalDate): DateAwareTimePlan? {
        val candidate = LegacyDateMigration.createCandidate(legacy, baseDate) ?: return null
        if (DateTimePlanRules.validateForPersistence(candidate).isNotEmpty()) return null
        val normalized = DateTimePlanRules.normalizeTopology(candidate)
        val next = plans.filterNot { it.id == normalized.id } + normalized
        if (!persist(next)) return null
        plans = next
        TimePlanActionNotificationManager.reconcile(appContext, this)
        return normalized
    }

    private fun persist(values: List<DateAwareTimePlan>): Boolean = snapshots.save(values)

    private fun encodeSnapshot(values: List<DateAwareTimePlan>): String = JSONObject()
        .put("schemaVersion", SCHEMA)
        .put("plans", JSONArray().apply { values.forEach { put(DateAwareTimePlanJson.encode(it)) } })
        .toString()

    private fun decodeSnapshot(raw: String): List<DateAwareTimePlan> {
        val root = JSONObject(raw)
        require(root.getInt("schemaVersion") in setOf(3, 4, SCHEMA))
        val array = root.getJSONArray("plans")
        return List(array.length()) { DateAwareTimePlanJson.decode(array.getJSONObject(it)) }
    }

    private fun loadPlanOrder(): List<String> =
        prefs.getString(KEY_PLAN_ORDER, null)
            ?.split('|')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            .orEmpty()

    private fun savePlanOrder(ids: List<String>): Boolean =
        prefs.edit()
            .putString(KEY_PLAN_ORDER, ids.distinct().joinToString("|"))
            .commit()

    companion object {
        const val PREFS = "armyrist_timeplan_v3"
        const val KEY = "snapshot_v3"
        const val KEY_PLAN_ORDER = "plan_order_v1"
        const val SCHEMA = 5
    }
}

object LegacyDateMigration {
    fun createCandidate(legacy: RevisedTimePlan, baseDate: LocalDate): DateAwareTimePlan? {
        var currentDate = baseDate
        var previousClock: Int? = null
        fun resolve(clockMinute: Int?): LocalDateTime? {
            if (clockMinute == null) return null
            if (previousClock != null && clockMinute < previousClock!!) currentDate = currentDate.plusDays(1)
            previousClock = clockMinute
            return LocalDateTime.of(currentDate, LocalTime.of(clockMinute / 60, clockMinute % 60))
        }
        fun value(v: com.seolhwa.armyrist.timeplan.domain.ClockValue): DateTimeValue =
            v.time?.minuteOfDay?.let { minute ->
                DateTimeValue(resolve(minute), when (v.origin) {
                    com.seolhwa.armyrist.timeplan.domain.ValueOrigin.EXPLICIT -> ValueOrigin.EXPLICIT
                    com.seolhwa.armyrist.timeplan.domain.ValueOrigin.DERIVED -> ValueOrigin.DERIVED
                    com.seolhwa.armyrist.timeplan.domain.ValueOrigin.UNSET -> ValueOrigin.UNSET
                })
            } ?: DateTimeValue.unset()
        fun spec(s: LegacySpec): EventDateTimeSpec = when (s) {
            LegacySpec.Unspecified -> EventDateTimeSpec.Unspecified
            is LegacySpec.Single -> EventDateTimeSpec.Single(value(s.value))
            is LegacySpec.Range -> EventDateTimeSpec.Range(value(s.start), value(s.end))
        }
        val start = value(legacy.start.value)
        val mids = legacy.midwayEvents.sortedBy { it.order }.map { e ->
            DateTimeEvent(e.id, TimeEventKind.MIDWAY, e.order, e.name, spec(e.timeSpec), e.note)
        }
        val final = legacy.finalPoint?.let { e ->
            DateTimeEvent(e.id, TimeEventKind.FINAL, mids.size, e.name, spec(e.timeSpec), e.note)
        }
        val end = value(legacy.end.value)
        val nodeIds = buildList { add(DateTimePlanRules.START_ID); addAll(mids.map { it.id }); final?.let { add(it.id) }; add(DateTimePlanRules.END_ID) }
        val legacyLinks = legacy.links.associateBy { it.fromNodeId to it.toNodeId }
        val links = nodeIds.zipWithNext().map { (from,to) ->
            val l = legacyLinks[from to to]
            DateTimeLink(
                from, to,
                l?.duration?.minutes?.toLong(),
                when (l?.origin) {
                    com.seolhwa.armyrist.timeplan.domain.ValueOrigin.EXPLICIT -> ValueOrigin.EXPLICIT
                    com.seolhwa.armyrist.timeplan.domain.ValueOrigin.DERIVED -> ValueOrigin.DERIVED
                    else -> ValueOrigin.UNSET
                },
                l?.label
            )
        }
        return DateAwareTimePlan(
            id = legacy.id,
            title = legacy.title,
            start = DateTimeAnchor(start),
            midwayEvents = mids,
            finalPoint = final,
            end = DateTimeAnchor(end),
            links = links,
            memo = legacy.memo,
            createdAt = legacy.createdAt,
            updatedAt = System.currentTimeMillis().toString(),
            legacyDateMigrationRequired = false
        )
    }
}

object DateAwareTimePlanJson {
    fun encode(p: DateAwareTimePlan): JSONObject = JSONObject()
        .put("schemaVersion", 6).put("id", p.id).put("title", p.title)
        .put("dateDisplayMode", p.dateDisplayMode.name)
        .put("start", valueToJson(p.start.value).put("dateTimeLocked", p.start.dateTimeLocked))
        .put("midwayEvents", JSONArray().apply { p.midwayEvents.sortedBy { it.order }.forEach { put(eventToJson(it)) } })
        .put("finalPoint", p.finalPoint?.let(::eventToJson) ?: JSONObject.NULL)
        .put("end", valueToJson(p.end.value).put("dateTimeLocked", p.end.dateTimeLocked))
        .put("links", JSONArray().apply { p.links.forEach { put(linkToJson(it)) } })
        .put("actionGroups", JSONArray().apply { p.actionGroups.sortedBy { it.order }.forEach { put(actionGroupToJson(it)) } })
        .put("actions", JSONArray().apply { p.actions.sortedWith(compareBy<TimePlanActionItem> { it.parentPointId }.thenBy { it.order }).forEach { put(actionToJson(it)) } })
        .put("memo", p.memo ?: JSONObject.NULL)
        .put("createdAt", p.createdAt).put("updatedAt", p.updatedAt)

    fun decode(j: JSONObject): DateAwareTimePlan {
        val schema = j.getInt("schemaVersion")
        require(schema in setOf(3, 4, 5, 6))
        val mids = j.optJSONArray("midwayEvents") ?: JSONArray()
        val links = j.optJSONArray("links") ?: JSONArray()
        val actionGroups = j.optJSONArray("actionGroups") ?: JSONArray()
        val actions = j.optJSONArray("actions") ?: JSONArray()
        return DateAwareTimePlan(
            id=j.getString("id"), title=j.getString("title"),
            start=j.getJSONObject("start").let { a ->
                DateTimeAnchor(valueFromJson(a), a.optBoolean("dateTimeLocked", false))
            },
            midwayEvents=List(mids.length()){ eventFromJson(mids.getJSONObject(it)) },
            finalPoint=if(j.isNull("finalPoint")) null else eventFromJson(j.getJSONObject("finalPoint")),
            end=j.getJSONObject("end").let { a ->
                DateTimeAnchor(valueFromJson(a), a.optBoolean("dateTimeLocked", false))
            },
            links=List(links.length()){ linkFromJson(links.getJSONObject(it)) },
            actionGroups=List(actionGroups.length()){ actionGroupFromJson(actionGroups.getJSONObject(it)) },
            actions=List(actions.length()){ actionFromJson(actions.getJSONObject(it)) },
            memo=if(j.isNull("memo")) null else j.getString("memo"),
            createdAt=j.getString("createdAt"), updatedAt=j.getString("updatedAt"),
            dateDisplayMode = runCatching { TimePlanDateDisplayMode.valueOf(j.optString("dateDisplayMode", TimePlanDateDisplayMode.ABSOLUTE.name)) }.getOrDefault(TimePlanDateDisplayMode.ABSOLUTE)
        )
    }
    private fun valueToJson(v: DateTimeValue)=JSONObject().put("dateTime",v.value?.toString()?:JSONObject.NULL).put("origin",v.origin.name)
    private fun valueFromJson(j:JSONObject)=if(j.isNull("dateTime")) DateTimeValue.unset() else DateTimeValue(LocalDateTime.parse(j.getString("dateTime")),ValueOrigin.valueOf(j.getString("origin")))
    private fun specToJson(s:EventDateTimeSpec):JSONObject=when(s){
        EventDateTimeSpec.Unspecified->JSONObject().put("type","UNSPECIFIED")
        is EventDateTimeSpec.Single->JSONObject().put("type","SINGLE").put("value",valueToJson(s.value))
        is EventDateTimeSpec.Range->JSONObject().put("type","RANGE").put("start",valueToJson(s.start)).put("end",valueToJson(s.end))
    }
    private fun specFromJson(j:JSONObject):EventDateTimeSpec=when(j.getString("type")){
        "UNSPECIFIED"->EventDateTimeSpec.Unspecified
        "SINGLE"->EventDateTimeSpec.Single(valueFromJson(j.getJSONObject("value")))
        "RANGE"->EventDateTimeSpec.Range(valueFromJson(j.getJSONObject("start")),valueFromJson(j.getJSONObject("end")))
        else->error("unsupported spec")
    }
    private fun eventToJson(e:DateTimeEvent)=JSONObject()
        .put("id",e.id).put("kind",e.kind.name).put("order",e.order).put("name",e.name)
        .put("timeSpec",specToJson(e.timeSpec)).put("note",e.note?:JSONObject.NULL)
        .put("dateTimeLocked", e.dateTimeLocked).put("color", e.color).put("completionState", e.completionState.name).put("notificationMode", e.notificationMode.name).put("groupId",e.groupId?:JSONObject.NULL)
    private fun eventFromJson(j:JSONObject)=DateTimeEvent(
        j.getString("id"), TimeEventKind.valueOf(j.getString("kind")), j.getInt("order"),
        j.getString("name"), specFromJson(j.getJSONObject("timeSpec")),
        if(j.isNull("note")) null else j.getString("note"),
        j.optBoolean("dateTimeLocked", false),
        j.optString("color", "#7543A3").takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) } ?: "#7543A3",
        runCatching { ActionCompletionState.valueOf(j.optString("completionState")) }.getOrDefault(ActionCompletionState.INCOMPLETE),
        runCatching { ActionNotificationMode.valueOf(j.optString("notificationMode")) }.getOrDefault(ActionNotificationMode.NONE),
        if(j.isNull("groupId"))null else j.getString("groupId")
    )
    private fun linkToJson(l:DateTimeLink)=JSONObject()
        .put("fromNodeId",l.fromNodeId).put("toNodeId",l.toNodeId)
        .put("minutes",l.durationMinutes?:JSONObject.NULL).put("origin",l.origin.name)
        .put("label",l.label?:JSONObject.NULL).put("durationLocked", l.durationLocked)
    private fun linkFromJson(j:JSONObject)=DateTimeLink(
        j.getString("fromNodeId"), j.getString("toNodeId"),
        if(j.isNull("minutes")) null else j.getLong("minutes"),
        ValueOrigin.valueOf(j.getString("origin")),
        if(j.isNull("label")) null else j.getString("label"),
        j.optBoolean("durationLocked", false)
    )
    private fun actionGroupToJson(g: TimePlanActionGroup) = JSONObject()
        .put("id", g.id).put("name", g.name).put("order", g.order).put("color", g.color)
    private fun actionGroupFromJson(j: JSONObject) = TimePlanActionGroup(
        id = j.getString("id"),
        name = j.getString("name"),
        order = j.getInt("order"),
        color = j.optString("color", "#7A7D61")
    )
    private fun actionToJson(a: TimePlanActionItem) = JSONObject()
        .put("id", a.id)
        .put("parentPointId", a.parentPointId)
        .put("content", a.content)
        .put("scheduledDateTime", a.scheduledDateTime.toString())
        .put("completionState", a.completionState.name)
        .put("notificationEnabled", a.notificationMode != ActionNotificationMode.NONE)
        .put("notificationMode", a.notificationMode.name)
        .put("markerColor", a.markerColor)
        .put("durationMinutes", a.durationMinutes ?: JSONObject.NULL).put("timeLocked", a.timeLocked)
        .put("groupId", a.groupId ?: JSONObject.NULL)
        .put("note", a.note ?: JSONObject.NULL)
        .put("order", a.order)
        .put("createdAt", a.createdAt)
        .put("updatedAt", a.updatedAt)
    private fun actionFromJson(j: JSONObject) = TimePlanActionItem(
        id = j.getString("id"),
        parentPointId = j.optString("parentPointId", "UNASSIGNED"),
        durationMinutes = if(j.has("durationMinutes") && !j.isNull("durationMinutes"))j.getLong("durationMinutes")else null,
        timeLocked = j.optBoolean("timeLocked", false),
        content = j.getString("content"),
        scheduledDateTime = LocalDateTime.parse(j.getString("scheduledDateTime")),
        markerColor = j.optString("markerColor", "#111111").takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) } ?: "#111111",
        completionState = ActionCompletionState.valueOf(j.optString("completionState", ActionCompletionState.INCOMPLETE.name)),
        notificationEnabled = j.optBoolean("notificationEnabled", false),
        notificationMode = runCatching { ActionNotificationMode.valueOf(j.optString("notificationMode")) }.getOrElse { if (j.optBoolean("notificationEnabled", false)) ActionNotificationMode.SIMPLE else ActionNotificationMode.NONE },
        groupId = if (j.isNull("groupId")) null else j.getString("groupId"),
        note = if (j.isNull("note")) null else j.getString("note"),
        order = j.optInt("order", 0),
        createdAt = j.optString("createdAt", System.currentTimeMillis().toString()),
        updatedAt = j.optString("updatedAt", System.currentTimeMillis().toString())
    )
}
