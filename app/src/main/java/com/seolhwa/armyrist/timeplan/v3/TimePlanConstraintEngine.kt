package com.seolhwa.armyrist.timeplan.v3.domain

import java.time.Duration
import java.time.LocalDateTime

enum class TimePlanConflictType {
    TIME_OVERLAP,
    LOCKED_RELATION_MISMATCH,
    INVALID_TEMPORAL_RELATION
}

data class TimePlanConflict(
    val type: TimePlanConflictType,
    val affectedNodeIds: Set<String> = emptySet(),
    val affectedLink: Pair<String, String>? = null,
    val relatedNames: List<String> = emptyList(),
    val actualMinutes: Long? = null,
    val lockedMinutes: Long? = null
)

/**
 * Rebuildable validation state. Conflicts are not persisted as user data and are non-blocking.
 */
object TimePlanConstraintEngine {
    private data class Occupancy(
        val nodeId: String,
        val name: String,
        val start: LocalDateTime,
        val endExclusive: LocalDateTime?,
        val isPoint: Boolean
    )

    fun detect(plan: DateAwareTimePlan): List<TimePlanConflict> {
        val result = mutableListOf<TimePlanConflict>()
        result += invalidTemporalRelations(plan)
        result += lockedRelationMismatches(plan)
        result += overlaps(plan)
        return result.distinct()
    }

    private fun invalidTemporalRelations(plan: DateAwareTimePlan): List<TimePlanConflict> {
        val result = mutableListOf<TimePlanConflict>()
        plan.orderedEvents().forEach { event ->
            val spec = event.timeSpec
            if (spec is EventDateTimeSpec.Range) {
                val a = spec.start.value
                val b = spec.end.value
                if (a != null && b != null && b.isBefore(a)) {
                    result += TimePlanConflict(
                        type = TimePlanConflictType.INVALID_TEMPORAL_RELATION,
                        affectedNodeIds = setOf(event.id),
                        relatedNames = listOf(event.name)
                    )
                }
            }
        }
        DateTimePlanRules.nodeIds(plan).zipWithNext().forEach { (from, to) ->
            val a = DateTimePlanRules.nodeDeparture(plan, from)
            val b = DateTimePlanRules.nodeArrival(plan, to)
            if (a != null && b != null && b.isBefore(a)) {
                result += TimePlanConflict(
                    type = TimePlanConflictType.INVALID_TEMPORAL_RELATION,
                    affectedNodeIds = setOf(from, to),
                    affectedLink = from to to
                )
            }
        }
        return result
    }

    private fun lockedRelationMismatches(plan: DateAwareTimePlan): List<TimePlanConflict> {
        return plan.links.mapNotNull { link ->
            if (!link.durationLocked || link.durationMinutes == null) return@mapNotNull null
            val from = DateTimePlanRules.nodeDeparture(plan, link.fromNodeId) ?: return@mapNotNull null
            val to = DateTimePlanRules.nodeArrival(plan, link.toNodeId) ?: return@mapNotNull null
            val actual = Duration.between(from, to).toMinutes()
            if (actual == link.durationMinutes) return@mapNotNull null

            TimePlanConflict(
                type = TimePlanConflictType.LOCKED_RELATION_MISMATCH,
                affectedNodeIds = setOf(link.fromNodeId, link.toNodeId),
                affectedLink = link.fromNodeId to link.toNodeId,
                actualMinutes = actual,
                lockedMinutes = link.durationMinutes
            )
        }
    }

    private fun overlaps(plan: DateAwareTimePlan): List<TimePlanConflict> {
        val occupancies = plan.orderedEvents().mapNotNull { event ->
            when (val spec = event.timeSpec) {
                EventDateTimeSpec.Unspecified -> null
                is EventDateTimeSpec.Single -> spec.value.value?.let {
                    Occupancy(event.id, event.name, it, null, true)
                }
                is EventDateTimeSpec.Range -> {
                    val start = spec.start.value ?: return@mapNotNull null
                    val end = spec.end.value ?: return@mapNotNull null
                    if (end.isBefore(start)) return@mapNotNull null
                    Occupancy(event.id, event.name, start, end, false)
                }
            }
        }

        val result = mutableListOf<TimePlanConflict>()
        for (i in occupancies.indices) {
            for (j in i + 1 until occupancies.size) {
                val a = occupancies[i]
                val b = occupancies[j]
                if (overlaps(a, b)) {
                    result += TimePlanConflict(
                        type = TimePlanConflictType.TIME_OVERLAP,
                        affectedNodeIds = setOf(a.nodeId, b.nodeId),
                        relatedNames = listOf(a.name, b.name)
                    )
                }
            }
        }
        return result
    }

    private fun overlaps(a: Occupancy, b: Occupancy): Boolean {
        if (a.isPoint && b.isPoint) return false
        if (a.isPoint) return pointInsideRange(a.start, b)
        if (b.isPoint) return pointInsideRange(b.start, a)
        val ae = a.endExclusive ?: return false
        val be = b.endExclusive ?: return false
        // Half-open ranges: [start, end). Exact touching boundaries are not overlap.
        return a.start < be && b.start < ae
    }

    private fun pointInsideRange(point: LocalDateTime, range: Occupancy): Boolean {
        val end = range.endExclusive ?: return false
        return !point.isBefore(range.start) && point.isBefore(end)
    }

    fun messageFor(conflict: TimePlanConflict): String = when (conflict.type) {
        TimePlanConflictType.TIME_OVERLAP -> {
            val first = conflict.relatedNames.getOrNull(0) ?: "일정"
            val second = conflict.relatedNames.getOrNull(1) ?: "다른 일정"
            "‘$first’ 일정이 ‘$second’ 일정과 시간이 겹칩니다.\n현재 값은 자동으로 수정되지 않습니다."
        }
        TimePlanConflictType.LOCKED_RELATION_MISMATCH -> {
            val actual = conflict.actualMinutes?.let { "${it}분" } ?: "계산된 시간"
            val locked = conflict.lockedMinutes?.let { "${it}분" } ?: "고정 경과시간"
            "실제 시간 차이는 $actual 이지만 경과시간은 $locked 으로 고정되어 있습니다.\n두 조건을 동시에 만족할 수 없어 현재 값은 자동으로 수정되지 않습니다."
        }
        TimePlanConflictType.INVALID_TEMPORAL_RELATION ->
            "현재 날짜/시간 관계가 유효한 순서를 만족하지 않습니다.\n현재 값은 자동으로 수정되지 않습니다."
    }
}
