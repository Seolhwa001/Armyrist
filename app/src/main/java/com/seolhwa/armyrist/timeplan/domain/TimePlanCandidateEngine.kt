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
        val propagated = when (intent) {
            is EditIntent.SetLinkDuration -> propagateFromExplicitDuration(edited, intent)
            else -> edited
        }
        val recalculated = recalculateLinksForIntent(propagated, intent)
        val impacts = detectImpacts(existing, recalculated)
        val conflicts = TimePlanConflictEngine.detect(recalculated)
        return Candidate(existing, recalculated, impacts, conflicts)
    }

    /**
     * Rebuilds the Point -> Interval -> Point topology after waypoint/final-point
     * insertion or deletion. Interval is a relationship between adjacent points,
     * never a child of a waypoint.
     *
     * Existing adjacent links are preserved. Newly adjacent points receive a link
     * immediately, and when both clocks resolve its duration is derived at once.
     */
    fun normalizeTopology(plan: RevisedTimePlan): RevisedTimePlan {
        val refs = TimePlanConflictEngine.nodeReferences(plan)
        val old = plan.links.associateBy { it.fromNodeId to it.toNodeId }
        val rebuilt = refs.zipWithNext().map { (from, to) ->
            old[from.nodeId to to.nodeId] ?: TimeLink(
                fromNodeId = from.nodeId,
                toNodeId = to.nodeId,
                duration = null,
                origin = ValueOrigin.UNSET
            )
        }
        val topology = plan.copy(links = rebuilt)
        return recalculateAllResolvableLinks(topology)
    }

    private fun recalculateAllResolvableLinks(plan: RevisedTimePlan): RevisedTimePlan {
        val refs = TimePlanConflictEngine.nodeReferences(plan)
        val resolved = TimePlanConflictEngine.resolveReferences(refs)
            as? TimePlanCalculator.Calculation.Success ?: return plan
        val absolute = resolved.value.associateBy { it.nodeId }
        return plan.copy(
            links = plan.links.map { link ->
                val from = absolute[link.fromNodeId]?.departure
                val to = absolute[link.toNodeId]?.arrival
                val calculated = TimePlanCalculator.durationBetween(from, to)
                if (calculated is TimePlanCalculator.Calculation.Success) {
                    link.copy(duration = calculated.value, origin = ValueOrigin.DERIVED)
                } else link
            }
        )
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
    private fun recalculateLinksForIntent(
        plan: RevisedTimePlan,
        intent: EditIntent
    ): RevisedTimePlan {
        val refs = TimePlanConflictEngine.nodeReferences(plan)
        val resolved = TimePlanConflictEngine.resolveReferences(refs)
            as? TimePlanCalculator.Calculation.Success ?: return plan

        val absolute = resolved.value.associateBy { it.nodeId }
        val links = plan.links.map { link ->
            val preserveExplicit = intent is EditIntent.SetLinkDuration &&
                link.origin == ValueOrigin.EXPLICIT
            if (preserveExplicit) return@map link
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

    /**
     * An explicit duration edit may move only DERIVED/UNSET downstream clocks.
     * Propagation stops at an EXPLICIT clock; conflict detection then reports
     * any mismatch instead of silently overwriting user-entered clock values.
     */
    private fun propagateFromExplicitDuration(
        plan: RevisedTimePlan,
        intent: EditIntent.SetLinkDuration
    ): RevisedTimePlan {
        val duration = intent.duration ?: return plan
        val refs = TimePlanConflictEngine.nodeReferences(plan)
        val index = refs.indexOfFirst { it.nodeId == intent.fromNodeId }
        if (index < 0 || index + 1 >= refs.size) return plan

        fun departureAbsolute(nodeIndex: Int): TimePlanCalculator.AbsoluteClock? {
            val clocks = refs.take(nodeIndex + 1).flatMap { ref ->
                buildList {
                    ref.arrival?.time?.let { add(it) }
                    if (ref.departure?.time != ref.arrival?.time) ref.departure?.time?.let { add(it) }
                }
            }
            val resolved = TimePlanCalculator.resolveOrderedClocks(clocks)
                as? TimePlanCalculator.Calculation.Success ?: return null
            return resolved.value.lastOrNull()
        }

        var current = plan
        var fromAbs = departureAbsolute(index) ?: return plan
        var linkIndex = index

        while (linkIndex < refs.lastIndex) {
            val fromId = refs[linkIndex].nodeId
            val toId = refs[linkIndex + 1].nodeId
            val link = current.links.firstOrNull { it.fromNodeId == fromId && it.toNodeId == toId }
                ?: break
            val stepDuration = if (fromId == intent.fromNodeId && toId == intent.toNodeId) duration
                else link.duration ?: break
            val target = TimePlanCalculator.forward(fromAbs, stepDuration)
                as? TimePlanCalculator.Calculation.Success ?: break

            val toRef = TimePlanConflictEngine.nodeReferences(current)
                .firstOrNull { it.nodeId == toId } ?: break
            val arrival = toRef.arrival
            if (arrival?.origin == ValueOrigin.EXPLICIT) break

            val derived = ClockValue.derived(target.value.time)
            current = setNodeArrival(current, toId, derived)

            val updatedRef = TimePlanConflictEngine.nodeReferences(current)
                .firstOrNull { it.nodeId == toId } ?: break
            val departure = updatedRef.departure
            if (departure?.time != null) {
                val resolved = TimePlanCalculator.resolveOrderedClocks(
                    listOf(target.value.time, departure.time)
                ) as? TimePlanCalculator.Calculation.Success
                fromAbs = resolved?.value?.lastOrNull() ?: target.value
            } else {
                fromAbs = target.value
            }
            linkIndex++
        }
        return current
    }

    private fun setNodeArrival(
        plan: RevisedTimePlan,
        nodeId: String,
        value: ClockValue
    ): RevisedTimePlan {
        if (nodeId == TimePlanConflictEngine.END_ID) {
            return if (plan.end.value.origin == ValueOrigin.EXPLICIT) plan
            else plan.copy(end = TimeAnchor(value))
        }
        fun update(event: TimeEvent): TimeEvent = when (val spec = event.timeSpec) {
            EventTimeSpec.Unspecified -> event.copy(timeSpec = EventTimeSpec.Single(value))
            is EventTimeSpec.Single ->
                if (spec.value.origin == ValueOrigin.EXPLICIT) event
                else event.copy(timeSpec = spec.copy(value = value))
            is EventTimeSpec.Range ->
                if (spec.start.origin == ValueOrigin.EXPLICIT) event
                else event.copy(timeSpec = spec.copy(start = value))
        }
        return plan.copy(
            midwayEvents = plan.midwayEvents.map { if (it.id == nodeId) update(it) else it },
            finalPoint = plan.finalPoint?.let { if (it.id == nodeId) update(it) else it }
        )
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
