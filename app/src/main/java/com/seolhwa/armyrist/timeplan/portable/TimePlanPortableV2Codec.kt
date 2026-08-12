package com.seolhwa.armyrist.timeplan.portable

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
 * Portable TimePlan schema v2 codec.
 *
 * This is intentionally independent from local DB/SharedPreferences layout.
 * It serializes the RevisedTimePlan domain meaning only.
 */
object TimePlanPortableV2Codec {
    const val SCHEMA_VERSION = 2

    fun encode(plan: RevisedTimePlan): JSONObject =
        JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("id", plan.id)
            .put("title", plan.title)
            .put("start", clockValueToJson(plan.start.value))
            .put(
                "midwayEvents",
                JSONArray().apply {
                    plan.midwayEvents.sortedBy { it.order }.forEach {
                        put(eventToJson(it))
                    }
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

    fun decode(document: JSONObject): RevisedTimePlan {
        require(document.getInt("schemaVersion") == SCHEMA_VERSION)

        val midwayJson = document.optJSONArray("midwayEvents") ?: JSONArray()
        val linksJson = document.optJSONArray("links") ?: JSONArray()

        val plan = RevisedTimePlan(
            id = document.getString("id"),
            title = document.getString("title"),
            start = TimeAnchor(
                clockValueFromJson(document.getJSONObject("start"))
            ),
            midwayEvents = List(midwayJson.length()) { index ->
                eventFromJson(midwayJson.getJSONObject(index))
            },
            finalPoint =
                if (document.isNull("finalPoint")) null
                else eventFromJson(document.getJSONObject("finalPoint")),
            end = TimeAnchor(
                clockValueFromJson(document.getJSONObject("end"))
            ),
            links = List(linksJson.length()) { index ->
                linkFromJson(linksJson.getJSONObject(index))
            },
            memo =
                if (document.isNull("memo")) null
                else document.getString("memo"),
            createdAt = document.getString("createdAt"),
            updatedAt = document.getString("updatedAt")
        )

        validate(plan)
        return plan
    }

    private fun validate(plan: RevisedTimePlan) {
        require(plan.title.isNotBlank())
        require(plan.midwayEvents.all { it.kind == TimeEventKind.MIDWAY })
        require(plan.finalPoint?.kind != TimeEventKind.MIDWAY)
        require(plan.midwayEvents.map { it.id }.distinct().size == plan.midwayEvents.size)
        require(plan.midwayEvents.map { it.order }.distinct().size == plan.midwayEvents.size)
        require(plan.links.all { it.duration == null || it.duration.minutes >= 0 })
    }

    private fun eventToJson(event: TimeEvent): JSONObject =
        JSONObject()
            .put("id", event.id)
            .put("kind", event.kind.name)
            .put("order", event.order)
            .put("name", event.name)
            .put("timeSpec", timeSpecToJson(event.timeSpec))
            .put("note", event.note ?: JSONObject.NULL)

    private fun eventFromJson(json: JSONObject): TimeEvent =
        TimeEvent(
            id = json.getString("id"),
            kind = TimeEventKind.valueOf(json.getString("kind")),
            order = json.getInt("order"),
            name = json.getString("name"),
            timeSpec = timeSpecFromJson(json.getJSONObject("timeSpec")),
            note = if (json.isNull("note")) null else json.getString("note")
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
            "SINGLE" ->
                EventTimeSpec.Single(
                    clockValueFromJson(json.getJSONObject("value"))
                )
            "RANGE" ->
                EventTimeSpec.Range(
                    start = clockValueFromJson(json.getJSONObject("start")),
                    end = clockValueFromJson(json.getJSONObject("end"))
                )
            else -> error("Unsupported EventTimeSpec.")
        }

    private fun linkToJson(link: TimeLink): JSONObject =
        JSONObject()
            .put("fromNodeId", link.fromNodeId)
            .put("toNodeId", link.toNodeId)
            .put("minutes", link.duration?.minutes ?: JSONObject.NULL)
            .put("origin", link.origin.name)
            .put("label", link.label ?: JSONObject.NULL)

    private fun linkFromJson(json: JSONObject): TimeLink =
        TimeLink(
            fromNodeId = json.getString("fromNodeId"),
            toNodeId = json.getString("toNodeId"),
            duration =
                if (json.isNull("minutes")) null
                else TimeDuration.requireMinutes(json.getInt("minutes")),
            origin = ValueOrigin.valueOf(json.getString("origin")),
            label = if (json.isNull("label")) null else json.getString("label")
        )

    private fun clockValueToJson(value: ClockValue): JSONObject =
        JSONObject()
            .put("minuteOfDay", value.time?.minuteOfDay ?: JSONObject.NULL)
            .put("origin", value.origin.name)

    private fun clockValueFromJson(json: JSONObject): ClockValue {
        if (json.isNull("minuteOfDay")) return ClockValue.unset()

        val time = ClockTime.requireMinuteOfDay(
            json.getInt("minuteOfDay")
        )
        return ClockValue(
            time = time,
            origin = ValueOrigin.valueOf(json.getString("origin"))
        )
    }
}
