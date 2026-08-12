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
            val duration: TimeDuration?,
            val label: String? = null
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


    /**
     * Applies the entire edited event (name, note, kind/order-preserving metadata,
     * and timeSpec) before recalculating adjacent links/conflicts.
     *
     * This exists because SetEventTime intentionally changes timeSpec only.
     * Event-edit UI must not lose name/note changes while passing through
     * CandidateEngine.
     */
    fun createEventEdit(
        existing: RevisedTimePlan,
        changedEvent: TimeEvent
    ): Candidate {
        val eventExists = existing.orderedEvents().any { it.id == changedEvent.id }
        if (!eventExists) {
            return Candidate(
                existing = existing,
                proposed = existing,
                impacts = emptyList(),
                conflicts = emptyList()
            )
        }

        val metadataApplied =
            if (changedEvent.kind == TimeEventKind.FINAL) {
                existing.copy(finalPoint = changedEvent)
            } else {
                existing.copy(
                    midwayEvents = existing.midwayEvents.map { event ->
                        if (event.id == changedEvent.id) changedEvent else event
                    }
                )
            }

        val intent = EditIntent.SetEventTime(
            eventId = changedEvent.id,
            timeSpec = changedEvent.timeSpec
        )
        val recalculated = recalculateLinksForIntent(metadataApplied, intent)

        return Candidate(
            existing = existing,
            proposed = recalculated,
            impacts = detectImpacts(existing, recalculated),
            conflicts = TimePlanConflictEngine.detect(recalculated)
        )
    }

    fun create(
        existing: RevisedTimePlan,
        intent: EditIntent
    ): Candidate {
        val edited = applyIntent(existing, intent)
        val propagated = when (intent) {
            is EditIntent.SetLinkDuration ->
                shiftDownstreamForDurationChange(existing, edited, intent)
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
    /**
     * Appends a FINAL point without disabling the action when one already exists.
     * If a FINAL already exists, it is demoted to the last MIDWAY while preserving
     * its identity, clock/range, note and any user-provided name. The whole topology
     * is then rebuilt as one pure state transition.
     */
    fun appendFinalPoint(
        plan: RevisedTimePlan,
        newFinalId: String,
        defaultFinalName: String = "종료지점"
    ): RevisedTimePlan {
        require(newFinalId.isNotBlank()) { "New FINAL id must not be blank." }

        val orderedMidways = plan.midwayEvents.sortedBy { it.order }
        val convertedFinal = plan.finalPoint?.let { previous ->
            val nextMidNumber = orderedMidways.size + 1
            previous.copy(
                kind = TimeEventKind.MIDWAY,
                order = orderedMidways.size,
                name = if (previous.name == defaultFinalName) {
                    "중도 $nextMidNumber"
                } else {
                    previous.name
                }
            )
        }
        val newMidways = (orderedMidways + listOfNotNull(convertedFinal))
            .mapIndexed { index, event -> event.copy(order = index) }
        val newFinalTimeSpec = plan.end.value.time?.let { endClock ->
            EventTimeSpec.Single(
                ClockValue.derived(endClock)
            )
        } ?: EventTimeSpec.Unspecified

        val newFinal = TimeEvent(
            id = newFinalId,
            kind = TimeEventKind.FINAL,
            order = newMidways.size,
            name = defaultFinalName,
            timeSpec = newFinalTimeSpec
        )

        return normalizeTopology(
            plan.copy(
                midwayEvents = newMidways,
                finalPoint = newFinal
            )
        )
    }


    /**
     * True when a newly entered event clock would sit later than the current
     * same-day END wall-clock.  The UI uses this as a confirmation boundary
     * instead of silently reinterpreting END as next-day.
     */
    fun requiresEndBoundaryConfirmation(
        existing: RevisedTimePlan,
        eventId: String,
        proposedSpec: EventTimeSpec
    ): Boolean {
        if (existing.orderedEvents().none { it.id == eventId }) return false

        val endClock = existing.end.value.time ?: return false
        val eventClock = TimePlanCalculator.arrivalClock(proposedSpec)?.time ?: return false

        val resolved = TimePlanConflictEngine.resolveReferences(
            TimePlanConflictEngine.nodeReferences(existing)
        ) as? TimePlanCalculator.Calculation.Success ?: return false
        val endAbsolute = resolved.value
            .firstOrNull { it.nodeId == TimePlanConflictEngine.END_ID }
            ?.arrival
            ?: return false

        // Existing overnight plans already have an explicit midnight context.
        // Warn only when END currently belongs to the same day and the newly
        // entered event wall-clock overtakes it.
        return endAbsolute.dayOffset == 0 &&
            eventClock.minuteOfDay > endClock.minuteOfDay
    }

    /**
     * Applies an explicit event-clock edit and shifts every downstream clock by
     * the same delta.  This is used only after the user confirms the boundary
     * warning.  The edited event remains EXPLICIT; automatically moved clocks
     * become DERIVED.
     */

    /**
     * Full-event equivalent of createEventTimeWithDownstreamShift().
     * Preserves edited name/note while shifting downstream clocks.
     */
    fun createEventEditWithDownstreamShift(
        existing: RevisedTimePlan,
        changedEvent: TimeEvent
    ): Candidate {
        val oldEvent = existing.orderedEvents().firstOrNull { it.id == changedEvent.id }
            ?: return createEventEdit(existing, changedEvent)

        val oldDeparture = TimePlanCalculator.departureClock(oldEvent.timeSpec)?.time
            ?: return createEventEdit(existing, changedEvent)
        val newDeparture = TimePlanCalculator.departureClock(changedEvent.timeSpec)?.time
            ?: return createEventEdit(existing, changedEvent)

        val metadataApplied =
            if (changedEvent.kind == TimeEventKind.FINAL) {
                existing.copy(finalPoint = changedEvent)
            } else {
                existing.copy(
                    midwayEvents = existing.midwayEvents.map { event ->
                        if (event.id == changedEvent.id) changedEvent else event
                    }
                )
            }

        val delta = signedClockDelta(oldDeparture, newDeparture)
        val shifted = shiftNodesAfter(
            plan = metadataApplied,
            anchorNodeId = changedEvent.id,
            deltaMinutes = delta,
            includeAnchor = false
        )
        val intent = EditIntent.SetEventTime(changedEvent.id, changedEvent.timeSpec)
        val recalculated = recalculateLinksForIntent(shifted, intent)

        return Candidate(
            existing = existing,
            proposed = recalculated,
            impacts = detectImpacts(existing, recalculated),
            conflicts = TimePlanConflictEngine.detect(recalculated)
        )
    }

    fun createEventTimeWithDownstreamShift(
        existing: RevisedTimePlan,
        eventId: String,
        proposedSpec: EventTimeSpec
    ): Candidate {
        val oldEvent = existing.orderedEvents().firstOrNull { it.id == eventId }
            ?: return create(existing, EditIntent.SetEventTime(eventId, proposedSpec))

        val oldDeparture = TimePlanCalculator.departureClock(oldEvent.timeSpec)?.time
            ?: return create(existing, EditIntent.SetEventTime(eventId, proposedSpec))
        val newDeparture = TimePlanCalculator.departureClock(proposedSpec)?.time
            ?: return create(existing, EditIntent.SetEventTime(eventId, proposedSpec))

        // Downstream starts after this event is finished.  For a Range edit,
        // therefore, the correct propagation delta is based on DEPARTURE
        // (range end), not ARRIVAL (range start).
        val delta = signedClockDelta(oldDeparture, newDeparture)
        val edited = applyIntent(
            existing,
            EditIntent.SetEventTime(eventId, proposedSpec)
        )
        val shifted = shiftNodesAfter(
            plan = edited,
            anchorNodeId = eventId,
            deltaMinutes = delta,
            includeAnchor = false
        )
        val recalculated = recalculateLinksForIntent(
            shifted,
            EditIntent.SetEventTime(eventId, proposedSpec)
        )
        return Candidate(
            existing = existing,
            proposed = recalculated,
            impacts = detectImpacts(existing, recalculated),
            conflicts = TimePlanConflictEngine.detect(recalculated)
        )
    }

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
                else ValueOrigin.EXPLICIT,
                label = intent.label?.trim()?.ifEmpty { null }
            )
            val hasExisting = plan.links.any {
                it.fromNodeId == intent.fromNodeId &&
                    it.toNodeId == intent.toNodeId
            }
            plan.copy(
                links = if (hasExisting) {
                    plan.links.map { link ->
                        if (
                            link.fromNodeId == intent.fromNodeId &&
                            link.toNodeId == intent.toNodeId
                        ) replacement else link
                    }
                } else {
                    plan.links + replacement
                }
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
     * A duration edit changes the relationship between two adjacent points.
     * The target point and every later point move by the duration delta so the
     * rest of the timeline keeps its relative spacing.
     *
     * This intentionally does NOT stop at EXPLICIT clocks.  EXPLICIT means
     * "entered by the user", not "locked".  A future dedicated lock feature,
     * if introduced, must use a separate state.
     */
    private fun shiftDownstreamForDurationChange(
        existing: RevisedTimePlan,
        edited: RevisedTimePlan,
        intent: EditIntent.SetLinkDuration
    ): RevisedTimePlan {
        val newDuration = intent.duration ?: return edited
        val oldDuration = existing.links.firstOrNull {
            it.fromNodeId == intent.fromNodeId &&
                it.toNodeId == intent.toNodeId
        }?.duration

        val delta = newDuration.minutes - (oldDuration?.minutes ?: 0)
        if (delta == 0) return edited

        return shiftNodesAfter(
            plan = edited,
            anchorNodeId = intent.toNodeId,
            deltaMinutes = delta,
            includeAnchor = true
        )
    }

    private fun shiftNodesAfter(
        plan: RevisedTimePlan,
        anchorNodeId: String,
        deltaMinutes: Int,
        includeAnchor: Boolean
    ): RevisedTimePlan {
        if (deltaMinutes == 0) return plan

        val refs = TimePlanConflictEngine.nodeReferences(plan)
        val anchorIndex = refs.indexOfFirst { it.nodeId == anchorNodeId }
        if (anchorIndex < 0) return plan

        val affectedIds = refs
            .drop(if (includeAnchor) anchorIndex else anchorIndex + 1)
            .map { it.nodeId }
            .toSet()

        fun shifted(value: ClockValue): ClockValue {
            val time = value.time ?: return value
            val raw = (time.minuteOfDay + deltaMinutes) % MINUTES_PER_DAY
            val normalized = if (raw < 0) raw + MINUTES_PER_DAY else raw
            return ClockValue.derived(ClockTime.requireMinuteOfDay(normalized))
        }

        fun shiftedSpec(spec: EventTimeSpec): EventTimeSpec = when (spec) {
            EventTimeSpec.Unspecified -> spec
            is EventTimeSpec.Single -> spec.copy(value = shifted(spec.value))
            is EventTimeSpec.Range -> spec.copy(
                start = shifted(spec.start),
                end = shifted(spec.end)
            )
        }

        val shiftedMidways = plan.midwayEvents.map { event ->
            if (event.id in affectedIds) event.copy(timeSpec = shiftedSpec(event.timeSpec))
            else event
        }
        val shiftedFinal = plan.finalPoint?.let { event ->
            if (event.id in affectedIds) event.copy(timeSpec = shiftedSpec(event.timeSpec))
            else event
        }
        val shiftedEnd =
            if (TimePlanConflictEngine.END_ID in affectedIds) {
                TimeAnchor(shifted(plan.end.value))
            } else plan.end

        return plan.copy(
            midwayEvents = shiftedMidways,
            finalPoint = shiftedFinal,
            end = shiftedEnd
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

    private fun signedClockDelta(
        before: ClockTime,
        after: ClockTime
    ): Int {
        var delta = after.minuteOfDay - before.minuteOfDay
        // Use the nearest signed interpretation. This keeps normal same-day
        // edits intuitive while still supporting edits around midnight.
        if (delta > MINUTES_PER_DAY / 2) delta -= MINUTES_PER_DAY
        if (delta < -MINUTES_PER_DAY / 2) delta += MINUTES_PER_DAY
        return delta
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
