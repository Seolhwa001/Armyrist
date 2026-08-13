package com.seolhwa.armyrist.timeplan.v3.portable

import com.seolhwa.armyrist.timeplan.v3.data.DateAwareTimePlanJson
import com.seolhwa.armyrist.timeplan.v3.domain.DateAwareTimePlan
import com.seolhwa.armyrist.timeplan.v3.domain.TIME_PLAN_DATE_PORTABLE_SCHEMA_VERSION
import org.json.JSONObject
import java.util.UUID

object TimePlanPortableV3Codec {
    const val SCHEMA_VERSION = TIME_PLAN_DATE_PORTABLE_SCHEMA_VERSION
    fun encode(plan: DateAwareTimePlan): JSONObject = DateAwareTimePlanJson.encode(plan).put("schemaVersion", SCHEMA_VERSION)
    fun decode(document: JSONObject): DateAwareTimePlan {
        val schema = document.getInt("schemaVersion")
        require(schema == 3 || schema == SCHEMA_VERSION)
        val plan = DateAwareTimePlanJson.decode(document)
        require(com.seolhwa.armyrist.timeplan.v3.domain.DateTimePlanRules.validateForPersistence(plan).isEmpty())
        return plan
    }
    fun regenerateIds(source: DateAwareTimePlan): DateAwareTimePlan {
        val eventMap = source.orderedEvents().associate { it.id to UUID.randomUUID().toString() }
        val mid = source.midwayEvents.map { it.copy(id = eventMap.getValue(it.id)) }
        val final = source.finalPoint?.let { it.copy(id = eventMap.getValue(it.id)) }
        val links = source.links.map { l ->
            l.copy(
                fromNodeId = eventMap[l.fromNodeId] ?: l.fromNodeId,
                toNodeId = eventMap[l.toNodeId] ?: l.toNodeId
            )
        }
        return source.copy(
            id = UUID.randomUUID().toString(),
            midwayEvents = mid,
            finalPoint = final,
            links = links,
            createdAt = System.currentTimeMillis().toString(),
            updatedAt = System.currentTimeMillis().toString()
        )
    }
}
