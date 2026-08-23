package com.seolhwa.armyrist.timeplan.v3.domain

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

const val TIME_PLAN_DATE_PORTABLE_SCHEMA_VERSION = 6

/**
 * Date-aware TimePlan domain. Duration is always an elapsed amount and never a clock value.
 */
enum class ValueOrigin { EXPLICIT, DERIVED, UNSET }
enum class TimeEventKind { MIDWAY, FINAL }
enum class DraftResolution { READY, REVIEW_REQUIRED, UNRESOLVED }
enum class TimePlanDateDisplayMode { ABSOLUTE, RELATIVE_D_DAY }

data class DateTimeValue(
    val value: LocalDateTime? = null,
    val origin: ValueOrigin = ValueOrigin.UNSET
) {
    init {
        require(value != null || origin == ValueOrigin.UNSET)
        require(value == null || origin != ValueOrigin.UNSET)
    }
    companion object {
        fun unset() = DateTimeValue()
        fun explicit(value: LocalDateTime) = DateTimeValue(value, ValueOrigin.EXPLICIT)
        fun derived(value: LocalDateTime) = DateTimeValue(value, ValueOrigin.DERIVED)
    }
}

sealed interface EventDateTimeSpec {
    data object Unspecified : EventDateTimeSpec
    data class Single(val value: DateTimeValue = DateTimeValue.unset()) : EventDateTimeSpec
    data class Range(
        val start: DateTimeValue = DateTimeValue.unset(),
        val end: DateTimeValue = DateTimeValue.unset()
    ) : EventDateTimeSpec
}

data class DateTimeAnchor(
    val value: DateTimeValue = DateTimeValue.unset(),
    val dateTimeLocked: Boolean = false
)

data class DateTimeEvent(
    val id: String,
    val kind: TimeEventKind,
    val order: Int,
    val name: String,
    val timeSpec: EventDateTimeSpec = EventDateTimeSpec.Unspecified,
    val note: String? = null,
    val dateTimeLocked: Boolean = false
) {
    init {
        require(id.isNotBlank())
        require(order >= 0)
        require(name.isNotBlank())
    }
}

data class DateTimeLink(
    val fromNodeId: String,
    val toNodeId: String,
    val durationMinutes: Long? = null,
    val origin: ValueOrigin = ValueOrigin.UNSET,
    val label: String? = null,
    val durationLocked: Boolean = false
) {
    init {
        require(fromNodeId.isNotBlank() && toNodeId.isNotBlank() && fromNodeId != toNodeId)
        require(durationMinutes == null || durationMinutes >= 0)
        require(durationMinutes != null || origin == ValueOrigin.UNSET)
        require(durationMinutes == null || origin != ValueOrigin.UNSET)
    }
}

data class DateAwareTimePlan(
    val id: String,
    val title: String,
    val start: DateTimeAnchor = DateTimeAnchor(),
    val midwayEvents: List<DateTimeEvent> = emptyList(),
    val finalPoint: DateTimeEvent? = null,
    val end: DateTimeAnchor = DateTimeAnchor(),
    val links: List<DateTimeLink> = emptyList(),
    val actionGroups: List<TimePlanActionGroup> = emptyList(),
    val actions: List<TimePlanActionItem> = emptyList(),
    val memo: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val dateDisplayMode: TimePlanDateDisplayMode = TimePlanDateDisplayMode.ABSOLUTE,
    /** True only for local/portable legacy material that still requires a user-selected base date. */
    val legacyDateMigrationRequired: Boolean = false
) {
    init {
        require(id.isNotBlank())
        require(title.isNotBlank())
        require(midwayEvents.all { it.kind == TimeEventKind.MIDWAY })
        require(finalPoint == null || finalPoint.kind == TimeEventKind.FINAL)
        require(midwayEvents.map { it.id }.distinct().size == midwayEvents.size)
        require(TimePlanExecutionRules.validate(this).isEmpty())
    }
    fun orderedEvents(): List<DateTimeEvent> = midwayEvents.sortedBy { it.order } + listOfNotNull(finalPoint)
}

object DateTimePlanRules {
    const val START_ID = "START"
    const val END_ID = "END"

    fun arrival(spec: EventDateTimeSpec): LocalDateTime? = when (spec) {
        EventDateTimeSpec.Unspecified -> null
        is EventDateTimeSpec.Single -> spec.value.value
        is EventDateTimeSpec.Range -> spec.start.value
    }

    fun departure(spec: EventDateTimeSpec): LocalDateTime? = when (spec) {
        EventDateTimeSpec.Unspecified -> null
        is EventDateTimeSpec.Single -> spec.value.value
        is EventDateTimeSpec.Range -> spec.end.value
    }

    fun stayMinutes(spec: EventDateTimeSpec): Long? {
        val range = spec as? EventDateTimeSpec.Range ?: return null
        val start = range.start.value ?: return null
        val end = range.end.value ?: return null
        if (end.isBefore(start)) return null
        return Duration.between(start, end).toMinutes()
    }

    fun addMinutes(value: LocalDateTime, minutes: Long): LocalDateTime = value.plusMinutes(minutes)
    fun subtractMinutes(value: LocalDateTime, minutes: Long): LocalDateTime = value.minusMinutes(minutes)
    fun forward(previousDeparture: LocalDateTime, durationMinutes: Long): LocalDateTime {
        require(durationMinutes >= 0)
        return previousDeparture.plusMinutes(durationMinutes)
    }
    fun reverse(nextArrival: LocalDateTime, durationMinutes: Long): LocalDateTime {
        require(durationMinutes >= 0)
        return nextArrival.minusMinutes(durationMinutes)
    }
    fun minutesBetween(from: LocalDateTime, to: LocalDateTime): Long? =
        if (to.isBefore(from)) null else Duration.between(from, to).toMinutes()

    fun withDate(value: LocalDateTime, date: LocalDate): LocalDateTime = LocalDateTime.of(date, value.toLocalTime())
    fun withTime(value: LocalDateTime, time: LocalTime): LocalDateTime = LocalDateTime.of(value.toLocalDate(), time)

    fun nodeIds(plan: DateAwareTimePlan): List<String> = buildList {
        add(START_ID)
        addAll(plan.midwayEvents.sortedBy { it.order }.map { it.id })
        plan.finalPoint?.let { add(it.id) }
        add(END_ID)
    }

    fun nodeArrival(plan: DateAwareTimePlan, nodeId: String): LocalDateTime? = when (nodeId) {
        START_ID -> plan.start.value.value
        END_ID -> plan.end.value.value
        else -> plan.orderedEvents().firstOrNull { it.id == nodeId }?.let { arrival(it.timeSpec) }
    }

    fun nodeDeparture(plan: DateAwareTimePlan, nodeId: String): LocalDateTime? = when (nodeId) {
        START_ID -> plan.start.value.value
        END_ID -> plan.end.value.value
        else -> plan.orderedEvents().firstOrNull { it.id == nodeId }?.let { departure(it.timeSpec) }
    }

    fun normalizeTopology(plan: DateAwareTimePlan): DateAwareTimePlan {
        val ids = nodeIds(plan)
        val existing = plan.links.associateBy { it.fromNodeId to it.toNodeId }
        val links = ids.zipWithNext().map { (from, to) ->
            val old = existing[from to to]
            val a = nodeDeparture(plan, from)
            val b = nodeArrival(plan, to)
            val derived = if (a != null && b != null) minutesBetween(a, b) else null
            when {
                old?.durationLocked == true && old.durationMinutes != null -> old
                derived != null && old?.origin == ValueOrigin.EXPLICIT && old.durationMinutes == derived -> old
                derived != null -> DateTimeLink(
                    fromNodeId = from,
                    toNodeId = to,
                    durationMinutes = derived,
                    origin = ValueOrigin.DERIVED,
                    label = old?.label,
                    durationLocked = old?.durationLocked ?: false
                )
                old?.origin == ValueOrigin.EXPLICIT && old.durationMinutes != null -> old
                else -> DateTimeLink(
                    fromNodeId = from,
                    toNodeId = to,
                    durationMinutes = null,
                    origin = ValueOrigin.UNSET,
                    label = old?.label,
                    durationLocked = old?.durationLocked ?: false
                )
            }
        }
        return plan.copy(links = links)
    }

    fun nodeDateTimeLocked(plan: DateAwareTimePlan, nodeId: String): Boolean = when (nodeId) {
        START_ID -> plan.start.dateTimeLocked
        END_ID -> plan.end.dateTimeLocked
        else -> plan.orderedEvents().firstOrNull { it.id == nodeId }?.dateTimeLocked ?: false
    }

    /**
     * Persistence validation intentionally excludes user-visible temporal conflicts.
     * Conflict states are non-blocking by Architecture Handover No.006 and are
     * recalculated from the stored values at load time.
     */
    fun validateForPersistence(plan: DateAwareTimePlan): List<String> {
        val problems = mutableListOf<String>()
        if (plan.id.isBlank() || plan.title.isBlank()) problems += "identity invalid"
        val ids = nodeIds(plan)
        if (ids.distinct().size != ids.size) problems += "duplicate node id"
        val expectedPairs = ids.zipWithNext().toSet()
        val actualPairs = plan.links.map { it.fromNodeId to it.toNodeId }.toSet()
        if (plan.links.isNotEmpty() && actualPairs != expectedPairs) problems += "link topology mismatch"
        if (plan.links.any { it.durationMinutes != null && it.durationMinutes < 0 }) problems += "negative duration"
        problems += TimePlanExecutionRules.validate(plan)
        return problems
    }

    fun setNodeLock(plan: DateAwareTimePlan, nodeId: String, locked: Boolean): DateAwareTimePlan = when (nodeId) {
        START_ID -> plan.copy(start = plan.start.copy(dateTimeLocked = locked))
        END_ID -> plan.copy(end = plan.end.copy(dateTimeLocked = locked))
        else -> plan.copy(
            midwayEvents = plan.midwayEvents.map { if (it.id == nodeId) it.copy(dateTimeLocked = locked) else it },
            finalPoint = plan.finalPoint?.let { if (it.id == nodeId) it.copy(dateTimeLocked = locked) else it }
        )
    }

    fun setLinkLock(plan: DateAwareTimePlan, from: String, to: String, locked: Boolean): DateAwareTimePlan =
        plan.copy(links = plan.links.map {
            if (it.fromNodeId == from && it.toNodeId == to) it.copy(durationLocked = locked) else it
        })

    /** Atomic date-only batch edit. Range endpoints move by the same day delta. */
    fun batchChangeDate(plan: DateAwareTimePlan, nodeIds: Set<String>, targetDate: LocalDate): DateAwareTimePlan? {
        if (nodeIds.isEmpty()) return plan
        if (nodeIds.any { nodeDateTimeLocked(plan, it) }) return null

        fun shiftValueDate(v: DateTimeValue, days: Long): DateTimeValue =
            if (v.value == null) v else v.copy(value = v.value.plusDays(days), origin = ValueOrigin.EXPLICIT)

        fun changeEvent(e: DateTimeEvent): DateTimeEvent {
            if (e.id !in nodeIds) return e
            val arrivalDate = arrival(e.timeSpec)?.toLocalDate() ?: return e
            val days = java.time.temporal.ChronoUnit.DAYS.between(arrivalDate, targetDate)
            val spec = when (val s = e.timeSpec) {
                EventDateTimeSpec.Unspecified -> s
                is EventDateTimeSpec.Single -> s.copy(value = shiftValueDate(s.value, days))
                is EventDateTimeSpec.Range -> s.copy(
                    start = shiftValueDate(s.start, days),
                    end = shiftValueDate(s.end, days)
                )
            }
            return e.copy(timeSpec = spec)
        }

        var changed = plan
        if (START_ID in nodeIds) {
            val v = plan.start.value.value ?: return null
            changed = changed.copy(start = plan.start.copy(value = DateTimeValue.explicit(LocalDateTime.of(targetDate, v.toLocalTime()))))
        }
        if (END_ID in nodeIds) {
            val v = plan.end.value.value ?: return null
            changed = changed.copy(end = plan.end.copy(value = DateTimeValue.explicit(LocalDateTime.of(targetDate, v.toLocalTime()))))
        }
        changed = changed.copy(
            midwayEvents = changed.midwayEvents.map(::changeEvent),
            finalPoint = changed.finalPoint?.let(::changeEvent)
        )
        return recalculateForExplicitNodes(changed, nodeIds)
    }

    fun validate(plan: DateAwareTimePlan): List<String> {
        if (plan.legacyDateMigrationRequired) return emptyList()
        val problems = mutableListOf<String>()
        plan.orderedEvents().forEach { event ->
            val spec = event.timeSpec
            if (spec is EventDateTimeSpec.Range) {
                val a = spec.start.value
                val b = spec.end.value
                if (a != null && b != null && b.isBefore(a)) problems += "${event.name}: range end before start"
            }
        }
        val ids = nodeIds(plan)
        val expectedPairs = ids.zipWithNext().toSet()
        val actualPairs = plan.links.map { it.fromNodeId to it.toNodeId }.toSet()
        if (plan.links.isNotEmpty() && actualPairs != expectedPairs) {
            problems += "link topology mismatch"
        }
        ids.zipWithNext().forEach { (from, to) ->
            val a = nodeDeparture(plan, from)
            val b = nodeArrival(plan, to)
            if (a != null && b != null) {
                if (b.isBefore(a)) {
                    problems += "$from->$to order conflict"
                } else {
                    val expected = Duration.between(a, b).toMinutes()
                    val link = plan.links.firstOrNull { it.fromNodeId == from && it.toNodeId == to }
                    if (link?.durationMinutes != null && link.durationMinutes != expected) {
                        problems += "$from->$to duration mismatch"
                    }
                }
            }
        }
        return problems
    }

    /** Explicit duration edit shifts all later DateTimes while preserving the edited link. */
    /**
     * Recalculate unlocked opposite nodes around one or more explicit user-edited DateTimes.
     * Locked durations are constraints; explicit nodes and DateTime-locked nodes are never moved.
     * Any mismatch that cannot be solved remains in the model and is surfaced by TimePlanConstraintEngine.
     */
    fun recalculateForExplicitNodes(plan: DateAwareTimePlan, explicitNodeIds: Set<String>): DateAwareTimePlan {
        var current = plan
        nodeIds(plan).zipWithNext().forEach { (from, to) ->
            val link = current.links.firstOrNull { it.fromNodeId == from && it.toNodeId == to } ?: return@forEach
            val minutes = link.durationMinutes ?: return@forEach
            if (!link.durationLocked) return@forEach
            val fromDt = nodeDeparture(current, from) ?: return@forEach
            val toDt = nodeArrival(current, to) ?: return@forEach
            val actual = Duration.between(fromDt, toDt).toMinutes()
            if (actual == minutes) return@forEach

            val fromProtected = from in explicitNodeIds || nodeDateTimeLocked(current, from)
            val toProtected = to in explicitNodeIds || nodeDateTimeLocked(current, to)
            when {
                fromProtected && !toProtected -> {
                    val desiredTo = fromDt.plusMinutes(minutes)
                    val delta = Duration.between(toDt, desiredTo).toMinutes()
                    current = shiftFromNode(current, to, delta)
                }
                toProtected && !fromProtected -> {
                    val desiredFrom = toDt.minusMinutes(minutes)
                    val delta = Duration.between(fromDt, desiredFrom).toMinutes()
                    current = shiftThroughNode(current, from, delta, towardPrefix = true)
                }
                else -> Unit
            }
        }
        return normalizeTopology(current)
    }

    fun setLinkDuration(plan: DateAwareTimePlan, from: String, to: String, minutes: Long, label: String?): DateAwareTimePlan {
        require(minutes >= 0)
        val fromDeparture = nodeDeparture(plan, from) ?: return plan
        val oldTo = nodeArrival(plan, to) ?: return plan
        val fromLocked = nodeDateTimeLocked(plan, from)
        val toLocked = nodeDateTimeLocked(plan, to)
        var shifted = plan

        if (toLocked && !fromLocked) {
            val desiredFrom = oldTo.minusMinutes(minutes)
            val delta = Duration.between(fromDeparture, desiredFrom).toMinutes()
            shifted = shiftThroughNode(plan, from, delta, towardPrefix = true)
        } else if (!toLocked) {
            val newTo = fromDeparture.plusMinutes(minutes)
            val delta = Duration.between(oldTo, newTo).toMinutes()
            shifted = shiftFromNode(plan, to, delta)
        }

        shifted = normalizeTopology(shifted)
        val oldLink = plan.links.firstOrNull { it.fromNodeId == from && it.toNodeId == to }
        return shifted.copy(links = shifted.links.map {
            if (it.fromNodeId == from && it.toNodeId == to) {
                it.copy(
                    durationMinutes = minutes,
                    origin = ValueOrigin.EXPLICIT,
                    label = label?.trim()?.ifBlank { null },
                    durationLocked = oldLink?.durationLocked ?: it.durationLocked
                )
            } else it
        })
    }

    private fun shiftThroughNode(plan: DateAwareTimePlan, nodeId: String, deltaMinutes: Long, towardPrefix: Boolean): DateAwareTimePlan {
        if (deltaMinutes == 0L) return plan
        val ids = nodeIds(plan)
        val pivot = ids.indexOf(nodeId)
        if (pivot < 0) return plan
        val affected = if (towardPrefix) ids.take(pivot + 1).toSet() else ids.drop(pivot).toSet()

        fun shiftValue(v: DateTimeValue, locked: Boolean): DateTimeValue =
            if (v.value == null || locked) v else DateTimeValue.derived(v.value.plusMinutes(deltaMinutes))
        fun shiftSpec(e: DateTimeEvent): EventDateTimeSpec {
            if (e.id !in affected || e.dateTimeLocked) return e.timeSpec
            return when (val s = e.timeSpec) {
                EventDateTimeSpec.Unspecified -> s
                is EventDateTimeSpec.Single -> s.copy(value = shiftValue(s.value, false))
                is EventDateTimeSpec.Range -> s.copy(start = shiftValue(s.start, false), end = shiftValue(s.end, false))
            }
        }
        return plan.copy(
            start = if (START_ID in affected) plan.start.copy(value = shiftValue(plan.start.value, plan.start.dateTimeLocked)) else plan.start,
            midwayEvents = plan.midwayEvents.map { if (it.id in affected) it.copy(timeSpec = shiftSpec(it)) else it },
            finalPoint = plan.finalPoint?.let { if (it.id in affected) it.copy(timeSpec = shiftSpec(it)) else it },
            end = if (END_ID in affected) plan.end.copy(value = shiftValue(plan.end.value, plan.end.dateTimeLocked)) else plan.end
        )
    }

    fun shiftFromNode(plan: DateAwareTimePlan, nodeId: String, deltaMinutes: Long): DateAwareTimePlan =
        shiftThroughNode(plan, nodeId, deltaMinutes, towardPrefix = false)

    /**
     * Confirmed event edit reflow. The edited event stays explicit.
     * - Prefix is shifted backward only when the new arrival overlaps the previous departure.
     * - Suffix is shifted by the edited event's departure delta.
     * This preserves the pre-Date contract without reintroducing <24h/day-offset limits.
     */
    fun reflowEventEdit(existing: DateAwareTimePlan, changedEvent: DateTimeEvent): DateAwareTimePlan? {
        val ordered = existing.orderedEvents()
        val index = ordered.indexOfFirst { it.id == changedEvent.id }
        if (index < 0) return null
        val oldEvent = ordered[index]
        val oldArrival = arrival(oldEvent.timeSpec) ?: return null
        val oldDeparture = departure(oldEvent.timeSpec) ?: return null
        val newArrival = arrival(changedEvent.timeSpec) ?: return null
        val newDeparture = departure(changedEvent.timeSpec) ?: return null
        if (newDeparture.isBefore(newArrival)) return null

        var base = if (changedEvent.kind == TimeEventKind.FINAL) {
            existing.copy(finalPoint = changedEvent)
        } else {
            existing.copy(midwayEvents = existing.midwayEvents.map { if (it.id == changedEvent.id) changedEvent else it })
        }

        val ids = nodeIds(existing)
        val nodeIndex = ids.indexOf(changedEvent.id)
        if (nodeIndex < 0) return null

        // Prefix overlap: shift START..previous node backward just enough.
        if (nodeIndex > 0) {
            val previousId = ids[nodeIndex - 1]
            val previousDeparture = nodeDeparture(existing, previousId)
            if (previousDeparture != null && newArrival.isBefore(previousDeparture)) {
                val overlap = Duration.between(newArrival, previousDeparture).toMinutes()
                val prefixIds = ids.take(nodeIndex).toSet()
                fun shiftValue(v: DateTimeValue): DateTimeValue =
                    if (v.value == null) v else DateTimeValue.derived(v.value.minusMinutes(overlap))
                fun shiftSpec(eventId: String, spec: EventDateTimeSpec): EventDateTimeSpec {
                    if (eventId !in prefixIds) return spec
                    return when (spec) {
                        EventDateTimeSpec.Unspecified -> spec
                        is EventDateTimeSpec.Single -> spec.copy(value = shiftValue(spec.value))
                        is EventDateTimeSpec.Range -> spec.copy(start = shiftValue(spec.start), end = shiftValue(spec.end))
                    }
                }
                base = base.copy(
                    start = if (START_ID in prefixIds) DateTimeAnchor(shiftValue(base.start.value)) else base.start,
                    midwayEvents = base.midwayEvents.map { it.copy(timeSpec = shiftSpec(it.id, it.timeSpec)) },
                    finalPoint = base.finalPoint?.let { it.copy(timeSpec = shiftSpec(it.id, it.timeSpec)) }
                )
            }
        }

        // Suffix follows the departure change exactly.
        val suffixDelta = Duration.between(oldDeparture, newDeparture).toMinutes()
        if (suffixDelta != 0L && nodeIndex < ids.lastIndex) {
            val nextId = ids[nodeIndex + 1]
            base = shiftFromNode(base, nextId, suffixDelta)
        }

        // Re-apply edited event after prefix/suffix movement so it cannot become DERIVED.
        base = if (changedEvent.kind == TimeEventKind.FINAL) {
            base.copy(finalPoint = changedEvent)
        } else {
            base.copy(midwayEvents = base.midwayEvents.map { if (it.id == changedEvent.id) changedEvent else it })
        }
        return normalizeTopology(base)
    }

    fun eventEditNeedsReflow(existing: DateAwareTimePlan, changedEvent: DateTimeEvent): Boolean {
        val old = existing.orderedEvents().firstOrNull { it.id == changedEvent.id } ?: return false
        return arrival(old.timeSpec) != arrival(changedEvent.timeSpec) || departure(old.timeSpec) != departure(changedEvent.timeSpec)
    }

    fun appendFinal(plan: DateAwareTimePlan, id: String): DateAwareTimePlan {
        val mids = plan.midwayEvents.sortedBy { it.order }.toMutableList()
        plan.finalPoint?.let { old ->
            mids += old.copy(kind = TimeEventKind.MIDWAY, order = mids.size, name = if (old.name == "종료지점") "중도 ${mids.size + 1}" else old.name)
        }
        val final = DateTimeEvent(
            id = id,
            kind = TimeEventKind.FINAL,
            order = mids.size,
            name = "종료지점",
            timeSpec = plan.end.value.value?.let { EventDateTimeSpec.Single(DateTimeValue.derived(it)) } ?: EventDateTimeSpec.Unspecified
        )
        return normalizeTopology(plan.copy(midwayEvents = mids.mapIndexed { i,e -> e.copy(order=i) }, finalPoint = final))
    }
}
