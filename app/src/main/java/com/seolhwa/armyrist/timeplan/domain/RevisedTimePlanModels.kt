package com.seolhwa.armyrist.timeplan.domain

/**
 * Revised TimePlan domain contract from Architecture Handover No.004.
 *
 * This file intentionally contains domain types and local invariants only.
 * Calculation, candidate/conflict processing, migration and UI are later steps.
 */
const val TIME_PLAN_PORTABLE_SCHEMA_VERSION = 2
const val MINUTES_PER_DAY = 1440

@JvmInline
value class ClockTime private constructor(val minuteOfDay: Int) {
    companion object {
        fun ofMinuteOfDay(value: Int): ClockTime? =
            value.takeIf { it in 0 until MINUTES_PER_DAY }?.let(::ClockTime)

        fun requireMinuteOfDay(value: Int): ClockTime =
            requireNotNull(ofMinuteOfDay(value)) {
                "Clock minute must be in 0..1439."
            }
    }
}

@JvmInline
value class TimeDuration private constructor(val minutes: Int) {
    companion object {
        fun ofMinutes(value: Int): TimeDuration? =
            value.takeIf { it >= 0 }?.let(::TimeDuration)

        fun requireMinutes(value: Int): TimeDuration =
            requireNotNull(ofMinutes(value)) {
                "Duration must be zero or greater."
            }
    }
}

enum class ValueOrigin {
    EXPLICIT,
    DERIVED,
    UNSET
}

enum class TimeEventKind {
    MIDWAY,
    FINAL
}

data class ClockValue(
    val time: ClockTime? = null,
    val origin: ValueOrigin = ValueOrigin.UNSET
) {
    init {
        require(time != null || origin == ValueOrigin.UNSET) {
            "A missing clock value must use UNSET origin."
        }
        require(time == null || origin != ValueOrigin.UNSET) {
            "A present clock value cannot use UNSET origin."
        }
    }

    companion object {
        fun unset() = ClockValue()
        fun explicit(time: ClockTime) = ClockValue(time, ValueOrigin.EXPLICIT)
        fun derived(time: ClockTime) = ClockValue(time, ValueOrigin.DERIVED)
    }
}

sealed interface EventTimeSpec {
    data object Unspecified : EventTimeSpec

    data class Single(
        val value: ClockValue = ClockValue.unset()
    ) : EventTimeSpec

    data class Range(
        val start: ClockValue = ClockValue.unset(),
        val end: ClockValue = ClockValue.unset()
    ) : EventTimeSpec
}

data class TimeAnchor(
    val value: ClockValue = ClockValue.unset()
)

data class TimeEvent(
    val id: String,
    val kind: TimeEventKind,
    val order: Int,
    val name: String,
    val timeSpec: EventTimeSpec = EventTimeSpec.Unspecified,
    val note: String? = null
) {
    init {
        require(id.isNotBlank()) { "Event id must not be blank." }
        require(order >= 0) { "Event order must be zero or greater." }
        require(name.isNotBlank()) { "Event name must not be blank." }
    }
}

data class TimeLink(
    val fromNodeId: String,
    val toNodeId: String,
    val duration: TimeDuration? = null,
    val origin: ValueOrigin = ValueOrigin.UNSET,
    val label: String? = null
) {
    init {
        require(fromNodeId.isNotBlank() && toNodeId.isNotBlank()) {
            "TimeLink node ids must not be blank."
        }
        require(fromNodeId != toNodeId) {
            "TimeLink cannot connect a node to itself."
        }
        require(duration != null || origin == ValueOrigin.UNSET) {
            "A missing duration must use UNSET origin."
        }
        require(duration == null || origin != ValueOrigin.UNSET) {
            "A present duration cannot use UNSET origin."
        }
    }
}

data class RevisedTimePlan(
    val id: String,
    val title: String,
    val start: TimeAnchor = TimeAnchor(),
    val midwayEvents: List<TimeEvent> = emptyList(),
    val finalPoint: TimeEvent? = null,
    val end: TimeAnchor = TimeAnchor(),
    val links: List<TimeLink> = emptyList(),
    val memo: String? = null,
    val createdAt: String,
    val updatedAt: String
) {
    init {
        require(id.isNotBlank()) { "TimePlan id must not be blank." }
        require(title.isNotBlank()) { "TimePlan title must not be blank." }
        require(midwayEvents.all { it.kind == TimeEventKind.MIDWAY }) {
            "midwayEvents may contain MIDWAY events only."
        }
        require(finalPoint == null || finalPoint.kind == TimeEventKind.FINAL) {
            "finalPoint must be FINAL."
        }
        require(midwayEvents.map { it.id }.distinct().size == midwayEvents.size) {
            "MIDWAY event ids must be unique."
        }
        require(midwayEvents.map { it.order }.distinct().size == midwayEvents.size) {
            "MIDWAY event order values must be unique."
        }
        require(finalPoint == null || midwayEvents.none { it.id == finalPoint.id }) {
            "FINAL id must not duplicate a MIDWAY id."
        }
    }

    fun orderedEvents(): List<TimeEvent> =
        midwayEvents.sortedBy { it.order } + listOfNotNull(finalPoint)
}

enum class TimePlanResolutionState {
    EMPTY,
    PARTIAL,
    CALCULABLE,
    COMPLETE,
    CONFLICTED
}
