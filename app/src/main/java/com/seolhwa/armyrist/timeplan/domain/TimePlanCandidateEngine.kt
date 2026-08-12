package com.seolhwa.armyrist.timeplan.domain

/**
 * Pure transient edit pipeline for TimePlan v2.
 *
 * This layer never writes persistence. A Candidate contains the proposed state,
 * impacts and conflicts. UI/persistence decide Cancel or atomic Confirm later.
 */
object TimePlanCandidateEngine {

    sealed interface EditIntent {
        data class SetStart(val value: ClockValue) : EditIntent
        data class SetEnd(val value: ClockValue) : EditIntent
        data class SetEventTime(
            val eventId: String,
            val timeSpec: EventTimeSpec
        ) : EditIntent
        data class SetLinkDuration(
            val fromNodeId: String,
            val toNodeId: String,
            val duration: TimeDuration?
        ) : EditIntent
    }

    data class Impact(
        val target: String,
        val before: String,
        val after: String
    )

    enum class ConflictType {
        EXPLICIT_DURATION_CLOCK_MISMATCH,
        EVENT_ORDER_CLOCK_MISMATCH,
        RANGE_ORDER_INVALID,
        PLAN_BOUNDARY_UNSUPPORTED
    }

    data class Conflict(
        val type: ConflictType,
        val target: String,
        val message: String
    )

    data class Candidate(
        val existing: RevisedTimePlan,
        val proposed: RevisedTimePlan,
        val impacts: List<Impact>,
        val conflicts: List<Conflict>
    ) {
        val requiresPreview: Boolean
            get() = conflicts.isNotEmpty() || impacts.size > 1
    }

    fun create(
        existing: RevisedTimePlan,
        intent: EditIntent
    ): Candidate {
        val edited = applyIntent(existing, intent)
        val recalculated = recalculateDerivedLinks(edited)
        val impacts = detectImpacts(existing, recalculated)
        val conflicts = TimePlanConflictEngine.detect(recalculated)
        return Candidate(existing, recalculated, impacts, conflicts)
    }

    private fun applyIntent(
        plan: RevisedTimePlan,
        intent: EditIntent
    ): RevisedTimePlan = when (intent) {
        is EditIntent.SetStart ->
            plan.copy(start = TimeAnchor(intent.value.asExplicit()))

        is EditIntent.SetEnd ->
            plan.copy(end = TimeAnchor(intent.value.asExplicit()))

        is EditIntent.SetEventTime ->
            plan.copy(
                midwayEvents = plan.midwayEvents.map {
                    if (it.id == intent.eventId) {
                        it.copy(timeSpec = intent.timeSpec.asExplicit())
                    } else it
                },
                finalPoint = plan.finalPoint?.let {
                    if (it.id == intent.eventId) {
                        it.copy(timeSpec = intent.timeSpec.asExplicit())
                    } else it
                }
            )

        is EditIntent.SetLinkDuration -> {
            val duration = intent.duration
            val replacement = TimeLink(
                fromNodeId = intent.fromNodeId,
                toNodeId = intent.toNodeId,
                duration = duration,
                origin = if (duration == null) ValueOrigin.UNSET
                else ValueOrigin.EXPLICIT
            )
            plan.copy(
                links = plan.links
                    .filterNot {
                        it.fromNodeId == intent.fromNodeId &&
                            it.toNodeId == intent.toNodeId
                    } + replacement
            )
        }
    }

    /**
     * Explicit clock/range values are never moved here.
     * Only a DERIVED/UNSET link is replaced when both clock references resolve.
     */
    private fun recalculateDerivedLinks(plan: RevisedTimePlan): RevisedTimePlan {
        val refs = TimePlanConflictEngine.nodeReferences(plan)
        val resolved = TimePlanConflictEngine.resolveReferences(refs)
            as? TimePlanCalculator.Calculation.Success ?: return plan

        val absolute = resolved.value.associateBy { it.nodeId }
        val links = plan.links.map { link ->
            if (link.origin == ValueOrigin.EXPLICIT) return@map link
            val from = absolute[link.fromNodeId]?.departure
            val to = absolute[link.toNodeId]?.arrival
            val calculated = TimePlanCalculator.durationBetween(from, to)
            if (calculated is TimePlanCalculator.Calculation.Success) {
                link.copy(
                    duration = calculated.value,
                    origin = ValueOrigin.DERIVED
                )
            } else link
        }
        return plan.copy(links = links)
    }

    private fun detectImpacts(
        before: RevisedTimePlan,
        after: RevisedTimePlan
    ): List<Impact> {
        val result = mutableListOf<Impact>()
        if (before.start != after.start) result +=
            Impact("START", before.start.toString(), after.start.toString())
        if (before.end != after.end) result +=
            Impact("END", before.end.toString(), after.end.toString())

        val beforeEvents = before.orderedEvents().associateBy { it.id }
        after.orderedEvents().forEach { event ->
            val old = beforeEvents[event.id]
            if (old != null && old != event) {
                result += Impact(
                    "EVENT:${event.id}",
                    old.toString(),
                    event.toString()
                )
            }
        }

        val beforeLinks = before.links.associateBy { it.fromNodeId to it.toNodeId }
        after.links.forEach { link ->
            val old = beforeLinks[link.fromNodeId to link.toNodeId]
            if (old != link) {
                result += Impact(
                    "LINK:${link.fromNodeId}->${link.toNodeId}",
                    old.toString(),
                    link.toString()
                )
            }
        }
        return result
    }

    private fun ClockValue.asExplicit(): ClockValue =
        if (time == null) ClockValue.unset()
        else ClockValue.explicit(time)

    private fun EventTimeSpec.asExplicit(): EventTimeSpec = when (this) {
        EventTimeSpec.Unspecified -> this
        is EventTimeSpec.Single -> copy(value = value.asExplicit())
        is EventTimeSpec.Range -> copy(
            start = start.asExplicit(),
            end = end.asExplicit()
        )
    }
}

object TimePlanConflictEngine {
    const val START_ID = "__START__"
    const val END_ID = "__END__"

    data class NodeReference(
        val nodeId: String,
        val arrival: ClockValue?,
        val departure: ClockValue?
    )

    data class AbsoluteReference(
        val nodeId: String,
        val arrival: TimePlanCalculator.AbsoluteClock?,
        val departure: TimePlanCalculator.AbsoluteClock?
    )

    fun nodeReferences(plan: RevisedTimePlan): List<NodeReference> {
        val result = mutableListOf(
            NodeReference(START_ID, plan.start.value, plan.start.value)
        )
        plan.orderedEvents().forEach { event ->
            result += NodeReference(
                event.id,
                TimePlanCalculator.arrivalClock(event.timeSpec),
                TimePlanCalculator.departureClock(event.timeSpec)
            )
        }
        result += NodeReference(END_ID, plan.end.value, plan.end.value)
        return result
    }

    fun resolveReferences(
        refs: List<NodeReference>
    ): TimePlanCalculator.Calculation<List<AbsoluteReference>> {
        val sequence = mutableListOf<ClockTime?>()
        refs.forEach {
            sequence += it.arrival?.time
            if (it.departure?.time != it.arrival?.time) {
                sequence += it.departure?.time
            }
        }
        val resolved = TimePlanCalculator.resolveOrderedClocks(sequence)
        if (resolved !is TimePlanCalculator.Calculation.Success) {
            return when (resolved) {
                is TimePlanCalculator.Calculation.Unsupported ->
                    TimePlanCalculator.Calculation.Unsupported(resolved.reason)
                else -> TimePlanCalculator.Calculation.Unresolved
            }
        }

        var index = 0
        val absolute = refs.map { ref ->
            val arrival = if (ref.arrival?.time != null) resolved.value[index++] else null
            val departure =
                if (ref.departure?.time == ref.arrival?.time) arrival
                else if (ref.departure?.time != null) resolved.value[index++] else null
            AbsoluteReference(ref.nodeId, arrival, departure)
        }
        return TimePlanCalculator.Calculation.Success(absolute)
    }

    fun detect(plan: RevisedTimePlan): List<TimePlanCandidateEngine.Conflict> {
        val conflicts = mutableListOf<TimePlanCandidateEngine.Conflict>()

        plan.orderedEvents().forEach { event ->
            val range = event.timeSpec as? EventTimeSpec.Range ?: return@forEach
            if (range.start.time != null && range.end.time != null) {
                val stay = TimePlanCalculator.stayDuration(range)
                if (stay is TimePlanCalculator.Calculation.Unsupported) {
                    conflicts += conflict(
                        TimePlanCandidateEngine.ConflictType.RANGE_ORDER_INVALID,
                        "EVENT:${event.id}",
                        stay.reason
                    )
                }
            }
        }

        val refs = nodeReferences(plan)
        val resolved = resolveReferences(refs)
        if (resolved is TimePlanCalculator.Calculation.Unsupported) {
            conflicts += conflict(
                TimePlanCandidateEngine.ConflictType.PLAN_BOUNDARY_UNSUPPORTED,
                "PLAN",
                resolved.reason
            )
            return conflicts
        }
        if (resolved !is TimePlanCalculator.Calculation.Success) return conflicts

        val abs = resolved.value.associateBy { it.nodeId }
        plan.links.filter { it.origin == ValueOrigin.EXPLICIT && it.duration != null }
            .forEach { link ->
                val from = abs[link.fromNodeId]?.departure
                val to = abs[link.toNodeId]?.arrival
                if (from != null && to != null) {
                    val actual = TimePlanCalculator.durationBetween(from, to)
                    if (
                        actual is TimePlanCalculator.Calculation.Success &&
                        actual.value != link.duration
                    ) {
                        conflicts += conflict(
                            TimePlanCandidateEngine.ConflictType
                                .EXPLICIT_DURATION_CLOCK_MISMATCH,
                            "${link.fromNodeId}->${link.toNodeId}",
                            "Explicit duration conflicts with explicit clock relationship."
                        )
                    }
                }
            }

        return conflicts
    }

    private fun conflict(
        type: TimePlanCandidateEngine.ConflictType,
        target: String,
        message: String
    ) = TimePlanCandidateEngine.Conflict(type, target, message)
}
