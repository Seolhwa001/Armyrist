package com.seolhwa.armyrist.ui

internal object DragMotion {
    /** Keep the grabbed point stationary even when neighbouring rows have different heights. */
    fun compensation(fromOffset: Int, fromSize: Int, toOffset: Int, toSize: Int, movingDown: Boolean): Float {
        val destination = if (movingDown) toOffset + toSize - fromSize else toOffset
        return (fromOffset - destination).toFloat()
    }

    /** Existing drag speeds are specified per 60 Hz frame; use actual frame time. */
    fun frameScale(previousNanos: Long, nowNanos: Long): Float =
        if (previousNanos <= 0L || nowNanos <= previousNanos) 0f
        else ((nowNanos - previousNanos).coerceAtMost(50_000_000L) / 16_666_667f)
}
