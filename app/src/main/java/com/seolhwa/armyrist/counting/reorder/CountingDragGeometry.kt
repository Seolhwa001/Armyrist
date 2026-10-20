package com.seolhwa.armyrist.counting.reorder

import kotlin.math.min

/** Geometry in the stable drag host, independent of LazyColumn key anchoring. */
internal object CountingDragGeometry {
    fun hostY(itemOffset: Int, beforeContentPadding: Int): Float =
        (itemOffset + beforeContentPadding).toFloat()

    fun overlayTop(pointerY: Float, grabOffsetY: Float): Float = pointerY - grabOffsetY

    /** Signed fraction of maximum scroll speed. At least 60% stays neutral. */
    fun edgeScrollFraction(pointerY: Float, viewportHeight: Float, requestedEdge: Float): Float {
        if (!pointerY.isFinite() || viewportHeight <= 0f || requestedEdge <= 0f) return 0f
        val band = min(requestedEdge, viewportHeight * 0.2f)
        val depth = when {
            pointerY < band -> -((band - pointerY) / band).coerceIn(0f, 1f)
            pointerY > viewportHeight - band -> ((pointerY - viewportHeight + band) / band).coerceIn(0f, 1f)
            else -> 0f
        }
        return depth * kotlin.math.abs(depth)
    }

    /** One adjacent step in the actual pointer/scroll direction, never a remote slot. */
    fun adjacentTarget(
        fromIndex: Int,
        heldCenterY: Float,
        heldHeight: Float,
        direction: Int,
        slots: List<CountingReorderSlot>
    ): Int? {
        if (direction == 0 || fromIndex < 0) return null
        val step = if (direction > 0) 1 else -1
        val neighbour = slots.firstOrNull { it.index == fromIndex + step } ?: return null
        val hysteresis = min(heldHeight, neighbour.height) * 0.08f
        val crossed = if (step > 0) heldCenterY > neighbour.centerY + hysteresis
            else heldCenterY < neighbour.centerY - hysteresis
        return neighbour.index.takeIf { crossed }
    }
}
