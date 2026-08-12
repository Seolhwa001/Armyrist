package com.seolhwa.armyrist.timeplan.domain

object TimePlanCalculator {
    private const val DAY = MINUTES_PER_DAY

    data class AbsoluteClock(val time: ClockTime, val dayOffset: Int) {
        init { require(dayOffset in 0..1) }
        val absoluteMinute: Int get() = time.minuteOfDay + dayOffset * DAY
    }

    sealed interface Calculation<out T> {
        data class Success<T>(val value: T) : Calculation<T>
        data object Unresolved : Calculation<Nothing>
        data class Unsupported(val reason: String) : Calculation<Nothing>
    }

    fun arrivalClock(spec: EventTimeSpec): ClockValue? = when (spec) {
        EventTimeSpec.Unspecified -> null
        is EventTimeSpec.Single -> spec.value.takeIf { it.time != null }
        is EventTimeSpec.Range -> spec.start.takeIf { it.time != null }
    }

    fun departureClock(spec: EventTimeSpec): ClockValue? = when (spec) {
        EventTimeSpec.Unspecified -> null
        is EventTimeSpec.Single -> spec.value.takeIf { it.time != null }
        is EventTimeSpec.Range -> spec.end.takeIf { it.time != null }
    }

    fun stayDuration(spec: EventTimeSpec): Calculation<TimeDuration> {
        if (spec !is EventTimeSpec.Range) return Calculation.Unresolved
        val start = spec.start.time ?: return Calculation.Unresolved
        val end = spec.end.time ?: return Calculation.Unresolved
        val raw = end.minuteOfDay - start.minuteOfDay
        val delta = if (raw >= 0) raw else raw + DAY
        return Calculation.Success(TimeDuration.requireMinutes(delta))
    }

    fun forward(previous: AbsoluteClock?, duration: TimeDuration?): Calculation<AbsoluteClock> {
        if (previous == null || duration == null) return Calculation.Unresolved
        if (duration.minutes >= DAY) return Calculation.Unsupported("Duration must be <24h.")
        val target = previous.absoluteMinute + duration.minutes
        if (target >= DAY * 2) return Calculation.Unsupported("Second midnight crossing.")
        return Calculation.Success(
            AbsoluteClock(ClockTime.requireMinuteOfDay(target % DAY), target / DAY)
        )
    }

    fun reverse(next: AbsoluteClock?, duration: TimeDuration?): Calculation<AbsoluteClock> {
        if (next == null || duration == null) return Calculation.Unresolved
        if (duration.minutes >= DAY) return Calculation.Unsupported("Duration must be <24h.")
        val target = next.absoluteMinute - duration.minutes
        if (target < 0) return Calculation.Unsupported("Before supported plan boundary.")
        return Calculation.Success(
            AbsoluteClock(ClockTime.requireMinuteOfDay(target % DAY), target / DAY)
        )
    }

    fun durationBetween(previous: AbsoluteClock?, next: AbsoluteClock?): Calculation<TimeDuration> {
        if (previous == null || next == null) return Calculation.Unresolved
        val delta = next.absoluteMinute - previous.absoluteMinute
        if (delta !in 0 until DAY) return Calculation.Unsupported("Adjacent duration outside <24h.")
        return Calculation.Success(TimeDuration.requireMinutes(delta))
    }

    fun resolveOrderedClocks(clocks: List<ClockTime?>): Calculation<List<AbsoluteClock?>> {
        var previous: ClockTime? = null
        var day = 0
        var crossings = 0
        var first: Int? = null
        val result = mutableListOf<AbsoluteClock?>()
        for (clock in clocks) {
            if (clock == null) { result += null; continue }
            if (previous != null && clock.minuteOfDay < previous.minuteOfDay) {
                crossings++; day++
                if (crossings > 1) return Calculation.Unsupported("Second midnight crossing.")
            }
            val resolved = AbsoluteClock(clock, day)
            if (first == null) first = resolved.absoluteMinute
            if (resolved.absoluteMinute - first!! >= DAY)
                return Calculation.Unsupported("Plan span must be <24h.")
            result += resolved
            previous = clock
        }
        return Calculation.Success(result)
    }
}
