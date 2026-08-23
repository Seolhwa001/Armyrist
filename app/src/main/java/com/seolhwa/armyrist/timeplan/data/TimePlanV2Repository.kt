package com.seolhwa.armyrist.timeplan.data

import android.content.Context
import com.seolhwa.armyrist.timeplan.domain.ClockTime
import com.seolhwa.armyrist.timeplan.domain.ClockValue
import com.seolhwa.armyrist.timeplan.domain.EventTimeSpec
import com.seolhwa.armyrist.timeplan.domain.RevisedTimePlan
import com.seolhwa.armyrist.timeplan.domain.TimeAnchor
import com.seolhwa.armyrist.timeplan.domain.TimeDuration
import com.seolhwa.armyrist.timeplan.domain.TimeEvent
import com.seolhwa.armyrist.timeplan.domain.TimeEventKind
import com.seolhwa.armyrist.timeplan.domain.TimeLink
import com.seolhwa.armyrist.timeplan.domain.ValueOrigin
import org.json.JSONArray
import org.json.JSONObject

/**
 * Side-by-side v2 persistence.
 *
 * The legacy CoreSuiteRepository remains untouched while the v1 UI is active.
 * This repository is a separate committed-state store for RevisedTimePlan.
 */
class TimePlanV2Repository(context: Context) {
    private val prefs = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private var plans: List<RevisedTimePlan> = load()

    @Synchronized
    fun getPlans(): List<RevisedTimePlan> =
        plans.sortedByDescending { it.updatedAt.toLongOrNull() ?: Long.MIN_VALUE }

    @Synchronized
    fun getPlan(id: String): RevisedTimePlan? =
        plans.firstOrNull { it.id == id }

    @Synchronized
    fun upsertMigrated(plan: RevisedTimePlan): Boolean {
        val next =
            if (plans.any { it.id == plan.id }) {
                plans.map { if (it.id == plan.id) plan else it }
            } else {
                plans + plan
            }

        return persist(next)
    }

    @Synchronized
    fun createPlan(title: String = "새 시간계획"): RevisedTimePlan? {
        val now = System.currentTimeMillis().toString()
        val plan = RevisedTimePlan(
            id = java.util.UUID.randomUUID().toString(),
            title = title,
            createdAt = now,
            updatedAt = now
        )
        return if (upsertMigrated(plan)) plan else null
    }

    @Synchronized
    fun commit(plan: RevisedTimePlan): Boolean =
        upsertMigrated(plan)

    @Synchronized
    fun delete(id: String): Boolean {
        val next = plans.filterNot { it.id == id }
        if (next.size == plans.size) return true
        return persist(next)
    }

    /**
     * Encodes one legacy/v2 TimePlan for Common Trash.
     *
     * This is deliberately a repository-owned codec so UI code never has to
     * duplicate the private v2 persistence format.
     */
    @Synchronized
    fun exportTrashPayload(id: String): String? =
        getPlan(id)?.let { planToJson(it).toString() }

    /**
     * Restores the exact legacy/v2 snapshot previously exported by
     * exportTrashPayload(). Existing IDs are never overwritten.
     */
    @Synchronized
    fun restoreTrashPayload(payload: String): Boolean {
        val restored = runCatching {
            planFromJson(JSONObject(payload))
        }.getOrNull() ?: return false

        if (plans.any { it.id == restored.id }) return false
        return persist(plans + restored)
    }

    @Synchronized
    fun reloadFromPersistence() {
        plans = load()
    }

    @Synchronized
    fun exportPortableSnapshot(): String {
        val root = JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put(
                "plans",
                JSONArray().apply {
                    plans.forEach { plan ->
                        put(com.seolhwa.armyrist.timeplan.portable.TimePlanPortableV2Codec.encode(plan))
                    }
                }
            )
        return root.toString()
    }

    @Synchronized
    fun replaceAllPortable(snapshot: String): Boolean {
        val root = JSONObject(snapshot)
        require(root.getInt("schemaVersion") == SCHEMA_VERSION)
        val array = root.optJSONArray("plans") ?: JSONArray()
        val decoded = List(array.length()) { index ->
            com.seolhwa.armyrist.timeplan.portable.TimePlanPortableV2Codec.decode(
                array.getJSONObject(index)
            )
        }
        return persist(decoded)
    }

    @Synchronized
    fun importPortableAsNew(plan: RevisedTimePlan): String? {
        val newPlanId = java.util.UUID.randomUUID().toString()
        val eventIdMap = linkedMapOf<String, String>()

        plan.midwayEvents.forEach {
            eventIdMap[it.id] = java.util.UUID.randomUUID().toString()
        }
        plan.finalPoint?.let {
            eventIdMap[it.id] = java.util.UUID.randomUUID().toString()
        }

        fun remapNode(id: String): String = when (id) {
            com.seolhwa.armyrist.timeplan.domain.TimePlanConflictEngine.START_ID -> id
            com.seolhwa.armyrist.timeplan.domain.TimePlanConflictEngine.END_ID -> id
            else -> eventIdMap[id] ?: error("Unknown TimePlan node id.")
        }

        val now = System.currentTimeMillis().toString()
        val imported = plan.copy(
            id = newPlanId,
            midwayEvents = plan.midwayEvents.map { event ->
                event.copy(id = eventIdMap.getValue(event.id))
            },
            finalPoint = plan.finalPoint?.let { event ->
                event.copy(id = eventIdMap.getValue(event.id))
            },
            links = plan.links.map { link ->
                link.copy(
                    fromNodeId = remapNode(link.fromNodeId),
                    toNodeId = remapNode(link.toNodeId)
                )
            },
            createdAt = now,
            updatedAt = now
        )

        return if (commit(imported)) newPlanId else null
    }

    private fun persist(next: List<RevisedTimePlan>): Boolean {
        val root = JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put(
                "plans",
                JSONArray().apply {
                    next.forEach { put(planToJson(it)) }
                }
            )

        val success = prefs.edit()
            .putString(SNAPSHOT_KEY, root.toString())
            .commit()

        if (success) plans = next
        return success
    }

    private fun load(): List<RevisedTimePlan> {
        val raw = prefs.getString(SNAPSHOT_KEY, null)
            ?: return emptyList()

        return runCatching {
            val root = JSONObject(raw)
            require(root.getInt("schemaVersion") == SCHEMA_VERSION)
            val array = root.optJSONArray("plans") ?: JSONArray()
            List(array.length()) { index ->
                planFromJson(array.getJSONObject(index))
            }
        }.getOrElse {
            // Never destroy the stored snapshot on parse failure.
            emptyList()
        }
    }

    private fun planToJson(plan: RevisedTimePlan): JSONObject =
        JSONObject()
            .put("id", plan.id)
            .put("title", plan.title)
            .put("start", clockValueToJson(plan.start.value))
            .put(
                "midwayEvents",
                JSONArray().apply {
                    plan.midwayEvents.forEach { put(eventToJson(it)) }
                }
            )
            .put(
                "finalPoint",
                plan.finalPoint?.let(::eventToJson) ?: JSONObject.NULL
            )
            .put("end", clockValueToJson(plan.end.value))
            .put(
                "links",
                JSONArray().apply {
                    plan.links.forEach { put(linkToJson(it)) }
                }
            )
            .put("memo", plan.memo ?: JSONObject.NULL)
            .put("createdAt", plan.createdAt)
            .put("updatedAt", plan.updatedAt)

    private fun planFromJson(json: JSONObject): RevisedTimePlan {
        val midway = json.optJSONArray("midwayEvents") ?: JSONArray()
        val links = json.optJSONArray("links") ?: JSONArray()

        return RevisedTimePlan(
            id = json.getString("id"),
            title = json.getString("title"),
            start = TimeAnchor(
                clockValueFromJson(json.getJSONObject("start"))
            ),
            midwayEvents = List(midway.length()) {
                eventFromJson(midway.getJSONObject(it))
            },
            finalPoint =
                if (json.isNull("finalPoint")) null
                else eventFromJson(json.getJSONObject("finalPoint")),
            end = TimeAnchor(
                clockValueFromJson(json.getJSONObject("end"))
            ),
            links = List(links.length()) {
                linkFromJson(links.getJSONObject(it))
            },
            memo =
                if (json.isNull("memo")) null
                else json.getString("memo"),
            createdAt = json.getString("createdAt"),
            updatedAt = json.getString("updatedAt")
        )
    }

    private fun eventToJson(event: TimeEvent): JSONObject =
        JSONObject()
            .put("id", event.id)
            .put("kind", event.kind.name)
            .put("order", event.order)
            .put("name", event.name)
            .put("timeSpec", timeSpecToJson(event.timeSpec))
            .put("note", event.note ?: JSONObject.NULL)

    private fun eventFromJson(json: JSONObject) =
        TimeEvent(
            id = json.getString("id"),
            kind = TimeEventKind.valueOf(json.getString("kind")),
            order = json.getInt("order"),
            name = json.getString("name"),
            timeSpec = timeSpecFromJson(json.getJSONObject("timeSpec")),
            note =
                if (json.isNull("note")) null
                else json.getString("note")
        )

    private fun timeSpecToJson(spec: EventTimeSpec): JSONObject =
        when (spec) {
            EventTimeSpec.Unspecified ->
                JSONObject().put("type", "UNSPECIFIED")

            is EventTimeSpec.Single ->
                JSONObject()
                    .put("type", "SINGLE")
                    .put("value", clockValueToJson(spec.value))

            is EventTimeSpec.Range ->
                JSONObject()
                    .put("type", "RANGE")
                    .put("start", clockValueToJson(spec.start))
                    .put("end", clockValueToJson(spec.end))
        }

    private fun timeSpecFromJson(json: JSONObject): EventTimeSpec =
        when (json.getString("type")) {
            "UNSPECIFIED" -> EventTimeSpec.Unspecified
            "SINGLE" -> EventTimeSpec.Single(
                clockValueFromJson(json.getJSONObject("value"))
            )
            "RANGE" -> EventTimeSpec.Range(
                start = clockValueFromJson(json.getJSONObject("start")),
                end = clockValueFromJson(json.getJSONObject("end"))
            )
            else -> error("Unknown EventTimeSpec.")
        }

    private fun linkToJson(link: TimeLink): JSONObject =
        JSONObject()
            .put("fromNodeId", link.fromNodeId)
            .put("toNodeId", link.toNodeId)
            .put(
                "minutes",
                link.duration?.minutes ?: JSONObject.NULL
            )
            .put("origin", link.origin.name)
            .put("label", link.label ?: JSONObject.NULL)

    private fun linkFromJson(json: JSONObject) =
        TimeLink(
            fromNodeId = json.getString("fromNodeId"),
            toNodeId = json.getString("toNodeId"),
            duration =
                if (json.isNull("minutes")) null
                else TimeDuration.requireMinutes(json.getInt("minutes")),
            origin = ValueOrigin.valueOf(json.getString("origin")),
            label =
                if (json.isNull("label")) null
                else json.getString("label")
        )

    private fun clockValueToJson(value: ClockValue): JSONObject =
        JSONObject()
            .put(
                "minuteOfDay",
                value.time?.minuteOfDay ?: JSONObject.NULL
            )
            .put("origin", value.origin.name)

    private fun clockValueFromJson(json: JSONObject): ClockValue {
        val origin = ValueOrigin.valueOf(json.getString("origin"))
        if (json.isNull("minuteOfDay")) return ClockValue.unset()

        val clock = ClockTime.requireMinuteOfDay(
            json.getInt("minuteOfDay")
        )
        return ClockValue(clock, origin)
    }

    companion object {
        private const val PREFS_NAME = "armyrist_timeplan_v2"
        private const val SNAPSHOT_KEY = "snapshot_v2"
        private const val SCHEMA_VERSION = 2
    }
}
