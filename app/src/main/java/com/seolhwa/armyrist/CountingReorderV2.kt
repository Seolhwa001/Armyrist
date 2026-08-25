package com.seolhwa.armyrist.counting.reorder

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import com.seolhwa.armyrist.domain.CountingItem
import kotlin.math.abs

enum class CountingReorderMode { DETAILED, COMPACT }

data class CountingReorderSlot(
    val itemId: String,
    val index: Int,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float
)

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

    fun moveTo(targetIndex: Int, nowMs: Long): Boolean {
        val id = draggingItemId ?: return false
        if (nowMs - lastMoveAt < 72L) return false
        val from = items.indexOfFirst { it.id == id }
        if (from < 0) return false
        val to = targetIndex.coerceIn(0, items.lastIndex)
        if (from == to) return false
        val moved = items.removeAt(from)
        items.add(to, moved)
        lastMoveAt = nowMs
        return true
    }

    fun detailedTarget(pointerY: Float, slots: List<CountingReorderSlot>): Int? {
        val id = draggingItemId ?: return null
        return slots.asSequence()
            .filter { it.itemId != id }
            .minByOrNull { abs(it.centerY - pointerY) }
            ?.index
    }

    fun compactTarget(pointerX: Float, pointerY: Float, slots: List<CountingReorderSlot>): Int? {
        val id = draggingItemId ?: return null
        return slots.asSequence()
            .filter { it.itemId != id }
            .minByOrNull {
                val nx = (it.centerX - pointerX) / it.width.coerceAtLeast(1f)
                val ny = (it.centerY - pointerY) / it.height.coerceAtLeast(1f)
                nx * nx + ny * ny
            }
            ?.index
    }
}
