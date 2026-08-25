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
 * Counting reorder V3.
 *
 * Unlike the failed nearest-slot V2, this controller only permits an adjacent
 * logical move after the pointer has clearly crossed that neighbour's gate.
 * This prevents a stationary pointer from making the list oscillate and keeps
 * Detailed and Compact on one shared order.
 */
@Stable
class CountingReorderV2(initialItems: List<CountingItem>) {
    val items = mutableStateListOf<CountingItem>().apply {
        addAll(initialItems.sortedBy { it.order })
    }

    var draggingItemId: String? = null
        private set
    var mode: CountingReorderMode? = null
        private set

    private var startOrder: List<String> = items.map { it.id }
    private var pendingCommit: List<String>? = null
    private var lastMoveAt: Long = 0L

    val isDragging: Boolean get() = draggingItemId != null

    fun begin(itemId: String, newMode: CountingReorderMode) {
        if (items.none { it.id == itemId }) return
        draggingItemId = itemId
        mode = newMode
        startOrder = items.map { it.id }
        lastMoveAt = 0L
    }

    fun sync(latestItems: List<CountingItem>) {
        if (isDragging) return
        val latest = latestItems.sortedBy { it.order }
        val ids = latest.map { it.id }
        val byId = latest.associateBy { it.id }
        val pending = pendingCommit

        // A repository emission can briefly contain the pre-drop order.
        // Refresh contents, but never allow that stale order to roll the UI back.
        if (pending != null && ids != pending) {
            for (i in items.indices) {
                byId[items[i].id]?.let { items[i] = it }
            }
            return
        }

        if (items.map { it.id } != ids) {
            items.clear()
            items.addAll(latest)
        } else {
            for (i in items.indices) {
                byId[items[i].id]?.let { items[i] = it }
            }
        }
        if (pending != null && ids == pending) pendingCommit = null
    }

    fun cancel(latestItems: List<CountingItem>) {
        val byId = latestItems.associateBy { it.id }
        val restored = startOrder.mapNotNull(byId::get)
        items.clear()
        if (restored.size == latestItems.size) items.addAll(restored)
        else items.addAll(latestItems.sortedBy { it.order })
        draggingItemId = null
        mode = null
        lastMoveAt = 0L
    }

    fun commitOrder(): List<String> {
        val order = items.map { it.id }
        pendingCommit = order
        draggingItemId = null
        mode = null
        lastMoveAt = 0L
        return order
    }

    /**
     * Keep the currently visible order authoritative across a Detailed/Compact
     * layout switch. The repository may still emit the pre-drop snapshot for a
     * short time, so treat the current IDs exactly like a pending commit.
     */
    fun pinCurrentOrder() {
        pendingCommit = items.map { it.id }
    }

    /** Move exactly one logical slot. Never jump across several cells in one frame. */
    private fun moveOne(direction: Int, nowMs: Long): Boolean {
        val id = draggingItemId ?: return false
        if (direction == 0 || nowMs - lastMoveAt < 74L) return false
        val from = items.indexOfFirst { it.id == id }
        if (from < 0) return false
        val to = (from + direction.coerceIn(-1, 1)).coerceIn(0, items.lastIndex)
        if (from == to) return false
        val moved = items.removeAt(from)
        items.add(to, moved)
        lastMoveAt = nowMs
        return true
    }

    /**
     * Detailed: only the immediately adjacent slot can open. A 14% hysteresis
     * gate prevents repeated back/forth moves around the centre line.
     */
    fun updateDetailed(pointerY: Float, slots: List<CountingReorderSlot>, nowMs: Long): Boolean {
        val id = draggingItemId ?: return false
        val current = items.indexOfFirst { it.id == id }
        if (current < 0) return false

        val next = slots.firstOrNull { it.index == current + 1 }
        if (next != null) {
            // Entering roughly the first 30% of the next card is enough.
            val gate = next.centerY - next.height * 0.20f
            if (pointerY > gate) return moveOne(1, nowMs)
        }

        val previous = slots.firstOrNull { it.index == current - 1 }
        if (previous != null) {
            // Symmetric rule when moving upward.
            val gate = previous.centerY + previous.height * 0.20f
            if (pointerY < gate) return moveOne(-1, nowMs)
        }
        return false
    }

    /**
     * Compact: row-major logical order, adjacent slots only. The pointer must
     * enter well inside the neighbouring cell (not merely become nearest).
     */
    fun updateCompact(pointerX: Float, pointerY: Float, slots: List<CountingReorderSlot>, nowMs: Long): Boolean {
        val id = draggingItemId ?: return false
        val current = items.indexOfFirst { it.id == id }
        if (current < 0) return false

        fun crossed(
            slot: CountingReorderSlot,
            fromSlot: CountingReorderSlot?,
            forward: Boolean
        ): Boolean {
            val sameRow =
                fromSlot != null &&
                    kotlin.math.abs(fromSlot.centerY - slot.centerY) <
                        minOf(fromSlot.height, slot.height) * 0.45f

            return if (sameRow) {
                // Row-major horizontal move. React as soon as the pointer enters
                // the leading ~30% of the neighbour instead of waiting for overlap.
                if (forward) {
                    pointerX > slot.centerX - slot.width * 0.20f
                } else {
                    pointerX < slot.centerX + slot.width * 0.20f
                }
            } else {
                // Row transition. Use the same early-entry rule vertically.
                if (forward) {
                    pointerY > slot.centerY - slot.height * 0.20f
                } else {
                    pointerY < slot.centerY + slot.height * 0.20f
                }
            }
        }

        val currentSlot = slots.firstOrNull { it.index == current }

        val next = slots.firstOrNull { it.index == current + 1 }
        if (next != null && crossed(next, currentSlot, true)) {
            return moveOne(1, nowMs)
        }

        val previous = slots.firstOrNull { it.index == current - 1 }
        if (previous != null && crossed(previous, currentSlot, false)) {
            return moveOne(-1, nowMs)
        }

        return false
    }
}
