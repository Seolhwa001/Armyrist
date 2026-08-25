package com.seolhwa.armyrist.counting.reorder

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import com.seolhwa.armyrist.domain.CountingItem

enum class CountingReorderMode { DETAILED, COMPACT }

data class CountingReorderSlot(
    val itemId: String,
    val index: Int,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float
)

/**
 * Counting reorder V4 — placeholder transaction model.
 *
 * Important invariant:
 * - During drag, canonicalItems never change order.
 * - Only previewOrder (the placeholder position) changes.
 * - commitOrder() is the only operation that publishes a new canonical order.
 *
 * This prevents repository/layout churn from becoming the drag state itself.
 */
@Stable
class CountingReorderV2(initialItems: List<CountingItem>) {
    private val canonicalItems = mutableStateListOf<CountingItem>().apply {
        addAll(initialItems.sortedBy { it.order })
    }
    private val previewOrder = mutableStateListOf<String>().apply {
        addAll(canonicalItems.map { it.id })
    }

    /** UI projection. During drag this reflects placeholder order only. */
    val items: List<CountingItem>
        get() {
            val byId = canonicalItems.associateBy { it.id }
            return previewOrder.mapNotNull(byId::get)
        }

    var draggingItemId: String? = null
        private set
    var mode: CountingReorderMode? = null
        private set

    private var startOrder: List<String> = previewOrder.toList()
    private var pendingCommit: List<String>? = null
    private var lastPlaceholderMoveAt = 0L

    val isDragging: Boolean get() = draggingItemId != null

    fun begin(itemId: String, newMode: CountingReorderMode) {
        if (canonicalItems.none { it.id == itemId }) return
        draggingItemId = itemId
        mode = newMode
        startOrder = previewOrder.toList()
        lastPlaceholderMoveAt = 0L
    }

    fun sync(latestItems: List<CountingItem>) {
        if (isDragging) return
        val latest = latestItems.sortedBy { it.order }
        val ids = latest.map { it.id }
        val byId = latest.associateBy { it.id }

        // Refresh item contents without allowing a stale repository order
        // to overwrite a just-committed preview order.
        val pending = pendingCommit
        canonicalItems.clear()
        if (pending != null && ids != pending && pending.all(byId::containsKey)) {
            canonicalItems.addAll(pending.mapNotNull(byId::get))
            previewOrder.clear()
            previewOrder.addAll(pending)
            return
        }

        canonicalItems.addAll(latest)
        previewOrder.clear()
        previewOrder.addAll(ids)
        if (pending != null && ids == pending) pendingCommit = null
    }

    fun cancel(latestItems: List<CountingItem>) {
        val latestById = latestItems.associateBy { it.id }
        previewOrder.clear()
        previewOrder.addAll(startOrder.filter(latestById::containsKey))
        if (previewOrder.size != latestItems.size) {
            previewOrder.clear()
            previewOrder.addAll(latestItems.sortedBy { it.order }.map { it.id })
        }
        draggingItemId = null
        mode = null
        lastPlaceholderMoveAt = 0L
    }

    fun commitOrder(): List<String> {
        val order = previewOrder.toList()
        pendingCommit = order

        val byId = canonicalItems.associateBy { it.id }
        canonicalItems.clear()
        canonicalItems.addAll(order.mapNotNull(byId::get))

        draggingItemId = null
        mode = null
        lastPlaceholderMoveAt = 0L
        return order
    }

    private fun movePlaceholder(direction: Int, nowMs: Long): Boolean {
        val id = draggingItemId ?: return false
        if (direction == 0 || nowMs - lastPlaceholderMoveAt < 86L) return false
        val from = previewOrder.indexOf(id)
        if (from < 0) return false
        val to = (from + direction.coerceIn(-1, 1)).coerceIn(0, previewOrder.lastIndex)
        if (from == to) return false

        previewOrder.removeAt(from)
        previewOrder.add(to, id)
        lastPlaceholderMoveAt = nowMs
        return true
    }

    /**
     * Detailed: the finger crosses the boundary between the dragged slot and
     * the adjacent slot. We do not wait until the dragged card passes the
     * neighbour's centre.
     */
    fun updateDetailed(
        pointerY: Float,
        slots: List<CountingReorderSlot>,
        nowMs: Long
    ): Boolean {
        val id = draggingItemId ?: return false
        val current = previewOrder.indexOf(id)
        if (current < 0) return false

        val currentSlot = slots.firstOrNull { it.index == current }
        val next = slots.firstOrNull { it.index == current + 1 }
        if (next != null) {
            val boundary = if (currentSlot != null)
                (currentSlot.centerY + next.centerY) / 2f
            else
                next.centerY - next.height * 0.38f
            if (pointerY > boundary) return movePlaceholder(1, nowMs)
        }

        val previous = slots.firstOrNull { it.index == current - 1 }
        if (previous != null) {
            val boundary = if (currentSlot != null)
                (currentSlot.centerY + previous.centerY) / 2f
            else
                previous.centerY + previous.height * 0.38f
            if (pointerY < boundary) return movePlaceholder(-1, nowMs)
        }
        return false
    }

    /**
     * Compact: row-major adjacent placeholder only.
     * Same-row transitions use X boundary; row transitions use Y boundary.
     */
    fun updateCompact(
        pointerX: Float,
        pointerY: Float,
        slots: List<CountingReorderSlot>,
        nowMs: Long
    ): Boolean {
        val id = draggingItemId ?: return false
        val current = previewOrder.indexOf(id)
        if (current < 0 || slots.isEmpty()) return false

        // Find the cell the overlay has actually entered. Rectangles are
        // slightly expanded so the background reacts before full overlap.
        val target = slots
            .asSequence()
            .filter { it.itemId != id }
            .filter {
                pointerX >= it.centerX - it.width * 0.58f &&
                    pointerX <= it.centerX + it.width * 0.58f &&
                    pointerY >= it.centerY - it.height * 0.58f &&
                    pointerY <= it.centerY + it.height * 0.58f
            }
            .minByOrNull {
                val nx =
                    (pointerX - it.centerX) /
                        it.width.coerceAtLeast(1f)
                val ny =
                    (pointerY - it.centerY) /
                        it.height.coerceAtLeast(1f)
                nx * nx + ny * ny
            }
            ?: return false

        val delta = target.index - current
        if (delta == 0) return false

        // Never jump several slots in a single decision. A diagonal or a
        // distant target simply causes controlled adjacent moves over time.
        return movePlaceholder(
            if (delta > 0) 1 else -1,
            nowMs
        )
    }
}
