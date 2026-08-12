package com.seolhwa.armyrist.timeplan.portable

import com.seolhwa.armyrist.stage2.domain.TimePlan
import com.seolhwa.armyrist.stage2.domain.TimePoint
import com.seolhwa.armyrist.timeplan.domain.RevisedTimePlan
import com.seolhwa.armyrist.timeplan.migration.LocalTimePlanV1Migrator
import org.json.JSONArray
import org.json.JSONObject

/**
 * Explicit portable TimePlan schema v1 -> revised domain v2 migration.
 *
 * Portable v1 is the Stage 3 document shape produced from the legacy CoreSuite
 * TimePlan snapshot:
 *
 * {
 *   id, title, memo, createdAt, updatedAt,
 *   points: [{ id, planId, order, name, timeMinutes }]
 * }
 *
 * This migrator does not guess unknown fields and does not invent FINAL.
 * It first parses the documented v1 shape, then reuses the same pure
 * LocalTimePlanV1Migrator that converts legacy TimePlan meaning into the
 * RevisedTimePlan domain.
 */
object TimePlanPortableV1Migrator {

    sealed interface Result {
        data class Success(val value: RevisedTimePlan) : Result
        data class Failure(val reason: String) : Result
    }

    fun migrate(document: JSONObject): Result = runCatching {
        val legacy = parseKnownV1(document)

        when (val migrated = LocalTimePlanV1Migrator.migrate(legacy)) {
            is LocalTimePlanV1Migrator.Result.Success ->
                migrated.value
            is LocalTimePlanV1Migrator.Result.Failure ->
                error(migrated.reason)
        }
    }.fold(
        onSuccess = { Result.Success(it) },
        onFailure = {
            Result.Failure(
                it.message ?: "Portable TimePlan v1 migration failed."
            )
        }
    )

    private fun parseKnownV1(document: JSONObject): TimePlan {
        require(document.has("id"))
        require(document.has("title"))
        require(document.has("points"))

        val id = document.getString("id")
        val title = document.getString("title")
        require(id.isNotBlank())
        require(title.isNotBlank())

        val pointsJson = document.getJSONArray("points")
        require(pointsJson.length() >= 2) {
            "Portable v1 TimePlan requires START and END points."
        }

        val points = List(pointsJson.length()) { index ->
            parsePoint(pointsJson.getJSONObject(index), id)
        }

        return TimePlan(
            id = id,
            title = title,
            points = points,
            memo = document.optString("memo", ""),
            createdAt = document.getLong("createdAt"),
            updatedAt = document.getLong("updatedAt")
        )
    }

    private fun parsePoint(
        json: JSONObject,
        expectedPlanId: String
    ): TimePoint {
        val planId = json.getString("planId")
        require(planId == expectedPlanId) {
            "Portable v1 point belongs to another TimePlan."
        }

        return TimePoint(
            id = json.getString("id"),
            planId = planId,
            order = json.getInt("order"),
            name = json.getString("name"),
            timeMinutes =
                if (json.isNull("timeMinutes")) null
                else json.getInt("timeMinutes")
        )
    }
}
