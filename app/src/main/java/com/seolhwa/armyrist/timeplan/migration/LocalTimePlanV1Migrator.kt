package com.seolhwa.armyrist.timeplan.migration

import com.seolhwa.armyrist.stage2.domain.TimePlan
import com.seolhwa.armyrist.timeplan.domain.ClockTime
import com.seolhwa.armyrist.timeplan.domain.ClockValue
import com.seolhwa.armyrist.timeplan.domain.EventTimeSpec
import com.seolhwa.armyrist.timeplan.domain.RevisedTimePlan
import com.seolhwa.armyrist.timeplan.domain.TimeAnchor
import com.seolhwa.armyrist.timeplan.domain.TimeDuration
import com.seolhwa.armyrist.timeplan.domain.TimeEvent
import com.seolhwa.armyrist.timeplan.domain.TimeEventKind
import com.seolhwa.armyrist.timeplan.domain.TimeLink
import com.seolhwa.armyrist.timeplan.domain.TimePlanCalculator
import com.seolhwa.armyrist.timeplan.domain.TimePlanConflictEngine
import com.seolhwa.armyrist.timeplan.domain.ValueOrigin

/**
 * Explicit Local v1 -> Revised Domain v2 migration.
 *
 * Current Armyrist local v1 persistence stores ordered TimePoint values only.
 * It does NOT persist duration or duration-label fields. Therefore migration
 * derives adjacent link durations only when both neighboring clock references
 * are available. It never invents FINAL or missing clock values.
 *
 * This converter is pure: the legacy TimePlan is never mutated/deleted.
 */
object LocalTimePlanV1Migrator {

    sealed interface Result {
        data class Success(val value: RevisedTimePlan) : Result
        data class Failure(val reason: String) : Result
    }

    fun migrate(legacy: TimePlan): Result = runCatching {
        require(legacy.points.size >= 2) {
            "Legacy TimePlan must contain START and END points."
        }

        val ordered = legacy.points.sortedBy { it.order }
        require(ordered.map { it.order }.distinct().size == ordered.size) {
            "Legacy point order values must be unique."
        }
        require(ordered.all { it.planId == legacy.id }) {
            "Legacy point belongs to a different plan."
        }
        require(ordered.all { it.name.isNotBlank() }) {
            "Legacy point name must not be blank."
        }

        val startPoint = ordered.first()
        val endPoint = ordered.last()
        val middle = ordered.drop(1).dropLast(1)

        val start = TimeAnchor(toClockValue(startPoint.timeMinutes))
        val end = TimeAnchor(toClockValue(endPoint.timeMinutes))

        val midway = middle.mapIndexed { index, point ->
            TimeEvent(
                id = point.id,
                kind = TimeEventKind.MIDWAY,
                order = index,
                name = point.name,
                timeSpec = if (point.timeMinutes == null) {
                    EventTimeSpec.Unspecified
                } else {
                    EventTimeSpec.Single(toClockValue(point.timeMinutes))
                },
                note = null
            )
        }

        val plan = RevisedTimePlan(
            id = legacy.id,
            title = legacy.title,
            start = start,
            midwayEvents = midway,
            finalPoint = null,
            end = end,
            links = buildLinks(start, midway, end),
            memo = legacy.memo.takeIf { it.isNotBlank() },
            createdAt = legacy.createdAt.toString(),
            updatedAt = legacy.updatedAt.toString()
        )

        validateMigrated(plan)
        plan
    }.fold(
        onSuccess = { Result.Success(it) },
        onFailure = { Result.Failure(it.message ?: "Unknown migration failure.") }
    )

    private fun toClockValue(minutes: Int?): ClockValue {
        if (minutes == null) return ClockValue.unset()
        val clock = ClockTime.ofMinuteOfDay(minutes)
            ?: error("Legacy clock minute is outside 0..1439.")
        // v1 cannot reliably distinguish explicit from derived.
        return ClockValue.explicit(clock)
    }

    private fun buildLinks(
        start: TimeAnchor,
        midway: List<TimeEvent>,
        end: TimeAnchor
    ): List<TimeLink> {
        data class Ref(
            val id: String,
            val arrival: ClockValue?,
            val departure: ClockValue?
        )

        val refs = buildList {
            add(
                Ref(
                    TimePlanConflictEngine.START_ID,
                    start.value,
                    start.value
                )
            )
            midway.forEach { event ->
                add(
                    Ref(
                        event.id,
                        TimePlanCalculator.arrivalClock(event.timeSpec),
                        TimePlanCalculator.departureClock(event.timeSpec)
                    )
                )
            }
            add(
                Ref(
                    TimePlanConflictEngine.END_ID,
                    end.value,
                    end.value
                )
            )
        }

        // Resolve the complete known clock sequence once so midnight semantics
        // are consistent across all adjacent links.
        val resolved = TimePlanCalculator.resolveOrderedClocks(
            refs.flatMap { ref ->
                buildList {
                    if (ref.arrival?.time != null) add(ref.arrival.time)
                    if (
                        ref.departure?.time != null &&
                        ref.departure.time != ref.arrival?.time
                    ) add(ref.departure.time)
                }
            }
        )

        val absoluteById =
            if (resolved is TimePlanCalculator.Calculation.Success) {
                var index = 0
                refs.associate { ref ->
                    val arrival =
                        if (ref.arrival?.time != null) resolved.value[index++]
                        else null
                    val departure =
                        if (ref.departure?.time == ref.arrival?.time) arrival
                        else if (ref.departure?.time != null) resolved.value[index++]
                        else null
                    ref.id to (arrival to departure)
                }
            } else emptyMap()

        return refs.zipWithNext().map { (from, to) ->
            val fromDeparture = absoluteById[from.id]?.second
            val toArrival = absoluteById[to.id]?.first
            val calculated =
                TimePlanCalculator.durationBetween(fromDeparture, toArrival)

            if (calculated is TimePlanCalculator.Calculation.Success) {
                TimeLink(
                    fromNodeId = from.id,
                    toNodeId = to.id,
                    duration = calculated.value,
                    // v1 persisted no duration-origin metadata. The calculated
                    // relationship is reconstructed, not claimed as user input.
                    origin = ValueOrigin.DERIVED,
                    label = null
                )
            } else {
                TimeLink(
                    fromNodeId = from.id,
                    toNodeId = to.id,
                    duration = null,
                    origin = ValueOrigin.UNSET,
                    label = null
                )
            }
        }
    }

    private fun validateMigrated(plan: RevisedTimePlan) {
        require(plan.finalPoint == null) {
            "Local v1 migration must never invent FINAL."
        }
        require(plan.midwayEvents.all { it.kind == TimeEventKind.MIDWAY })
        require(plan.midwayEvents.map { it.id }.distinct().size ==
            plan.midwayEvents.size)
    }
}
