package com.seolhwa.armyrist.stage2.domain

data class DerivedTimePoint(
    val point: TimePoint,
    val absoluteMinute: Int
)

object TimePlanRules {
    private const val DAY_MINUTES = 1440

    fun parseClock(raw: String): Int? {
        val normalized = raw.trim().replace(":", "")
        if (normalized.length != 4 || normalized.any { !it.isDigit() }) return null

        val hour = normalized.substring(0, 2).toIntOrNull() ?: return null
        val minute = normalized.substring(2, 4).toIntOrNull() ?: return null

        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    fun parseDuration(raw: String): Int? {
        val normalized = raw.trim().replace(":", "")
        if (normalized.length != 4 || normalized.any { !it.isDigit() }) return null

        val hour = normalized.substring(0, 2).toIntOrNull() ?: return null
        val minute = normalized.substring(2, 4).toIntOrNull() ?: return null

        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    fun formatClock(timeMinutes: Int): String {
        require(timeMinutes in 0 until DAY_MINUTES)
        val hour = timeMinutes / 60
        val minute = timeMinutes % 60
        return "%02d:%02d".format(hour, minute)
    }

    fun formatShareClock(timeMinutes: Int): String {
        require(timeMinutes in 0 until DAY_MINUTES)
        val hour = timeMinutes / 60
        val minute = timeMinutes % 60
        return "%02d%02d".format(hour, minute)
    }

    fun derive(points: List<TimePoint>): List<DerivedTimePoint>? {
        if (points.isEmpty()) return emptyList()
        if (points.any { it.timeMinutes == null }) return null

        val ordered = points.sortedBy { it.order }
        val result = mutableListOf<DerivedTimePoint>()
        var dayOffset = 0
        var previousClock: Int? = null
        var crossings = 0

        for (point in ordered) {
            val clock = point.timeMinutes ?: return null
            if (clock !in 0 until DAY_MINUTES) return null

            if (previousClock != null && clock < previousClock) {
                crossings++
                dayOffset++
            }

            if (crossings > 1) return null

            result += DerivedTimePoint(
                point = point,
                absoluteMinute = clock + dayOffset * DAY_MINUTES
            )
            previousClock = clock
        }

        if (result.size >= 2) {
            val span = result.last().absoluteMinute - result.first().absoluteMinute
            if (span < 0 || span >= DAY_MINUTES) return null
        }

        return result
    }

    fun adjacentDuration(points: List<TimePoint>, index: Int): Int? {
        val derived = derive(points) ?: return null
        if (index !in 0 until derived.lastIndex) return null

        val duration = derived[index + 1].absoluteMinute - derived[index].absoluteMinute
        return duration.takeIf { it in 0 until DAY_MINUTES }
    }

    fun editPointTime(
        points: List<TimePoint>,
        pointId: String,
        newTimeMinutes: Int
    ): List<TimePoint>? {
        if (newTimeMinutes !in 0 until DAY_MINUTES) return null

        val candidate = points.map {
            if (it.id == pointId) it.copy(timeMinutes = newTimeMinutes) else it
        }

        return candidate.takeIf { derive(it) != null }
    }

    fun editDuration(
        points: List<TimePoint>,
        leftPointId: String,
        durationMinutes: Int
    ): List<TimePoint>? {
        if (durationMinutes !in 0 until DAY_MINUTES) return null

        val ordered = points.sortedBy { it.order }
        val leftIndex = ordered.indexOfFirst { it.id == leftPointId }
        if (leftIndex < 0 || leftIndex >= ordered.lastIndex) return null

        val derived = derive(ordered) ?: return null
        val newAbsolute = derived[leftIndex].absoluteMinute + durationMinutes
        val endpointId = ordered[leftIndex + 1].id
        val newClock = ((newAbsolute % DAY_MINUTES) + DAY_MINUTES) % DAY_MINUTES

        val candidate = ordered.map {
            if (it.id == endpointId) it.copy(timeMinutes = newClock) else it
        }

        return candidate.takeIf { derive(it) != null }
    }

    fun reorderIntermediate(
        points: List<TimePoint>,
        pointId: String,
        delta: Int
    ): List<TimePoint>? {
        val ordered = points.sortedBy { it.order }.toMutableList()
        if (ordered.size < 3) return ordered

        val index = ordered.indexOfFirst { it.id == pointId }
        if (index <= 0 || index >= ordered.lastIndex) return null

        val target = (index + delta).coerceIn(1, ordered.lastIndex - 1)
        if (target == index) return ordered

        val point = ordered.removeAt(index)
        ordered.add(target, point)

        val normalized = ordered.mapIndexed { i, p -> p.copy(order = i) }
        return normalized.takeIf { derive(it) != null }
    }
}
