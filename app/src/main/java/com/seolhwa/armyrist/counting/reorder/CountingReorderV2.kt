package com.seolhwa.armyrist.counting.reorder

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
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

/**
 * Counting Reorder V5
 *
 * Reusable contract:
 * 1. Canonical repository order never changes during pointer movement.
 * 2. The dragged ID is removed from backgroundOrder.
 * 3. A single placeholderIndex represents the insertion location.
 * 4. UI projection inserts the dragged ID only for layout/placeholder sizing.
 * 5. Drop publishes exactly one final ID order.
 *
 * This avoids the ambiguous "move the dragged item through the list while it
 * is also being rendered as an overlay" state that caused asymmetric compact
 * movement and failed return-to-origin gestures.
 */
@Stable
class CountingReorderV2(
    initialItems: List<CountingItem>
) {
    private val canonicalItems =
        mutableStateListOf<CountingItem>().apply {
            addAll(initialItems.sortedBy { it.order })
        }

    private val restingOrder =
        mutableStateListOf<String>().apply {
            addAll(canonicalItems.map { it.id })
        }

    private val backgroundOrder =
        mutableStateListOf<String>()

    private val _placeholderIndex = mutableStateOf(0)
    val placeholderIndex: Int
        get() = _placeholderIndex.value

    var draggingItemId: String? = null
        private set

    var mode: CountingReorderMode? = null
        private set

    private var dragStartOrder: List<String> = restingOrder.toList()
    private var pendingCommit: List<String>? = null
    private var lastPlaceholderMoveAtMs: Long = 0L

    // Detailed-mode direction memory.
    // The previous nearest-slot implementation could reverse itself after a
    // LazyColumn relayout even while the finger was still travelling upward.
    // Keep the user's actual pointer direction as the source of truth.
    private var lastDetailedPointerY: Float = Float.NaN
    private var detailedDirection: Int = 0
    private var detailedDragStartedAtMs: Long = 0L
    private var lastDetailedMoveAtMs: Long = 0L

    val isDragging: Boolean
        get() = draggingItemId != null

    private fun projectedIds(): List<String> {
        val dragged = draggingItemId ?: return restingOrder.toList()
        val result = backgroundOrder.toMutableList()
        val insertion =
            placeholderIndex.coerceIn(0, result.size)
        result.add(insertion, dragged)
        return result
    }

    /**
     * Projection keeps the dragged item in the Lazy layout only as an
     * invisible size-preserving placeholder. Its visible representation is
     * owned by the stable parent overlay.
     */
    val items: List<CountingItem>
        get() {
            val byId = canonicalItems.associateBy { it.id }
            return projectedIds().mapNotNull(byId::get)
        }

    /**
     * Synchronous live projection lookup for pointer-time geometry.
     * Do not derive drag indices from a previous Compose frame snapshot.
     */
    fun projectedIndexOf(itemId: String): Int =
        projectedIds().indexOf(itemId)

    /**
     * TimePlan-style 1-D adapter entry point.
     * Moves only the insertion placeholder; repository order is still written
     * once on Drop by commitOrder().
     */
    fun movePlaceholderTo(targetIndex: Int): Boolean {
        if (!isDragging) return false
        val target = targetIndex.coerceIn(0, backgroundOrder.size)
        if (target == placeholderIndex) return false
        _placeholderIndex.value = target
        return true
    }

    fun begin(
        itemId: String,
        newMode: CountingReorderMode
    ) {
        if (canonicalItems.none { it.id == itemId }) return

        val order = restingOrder.toList()
        val sourceIndex = order.indexOf(itemId)
        if (sourceIndex < 0) return

        draggingItemId = itemId
        mode = newMode
        dragStartOrder = order

        backgroundOrder.clear()
        backgroundOrder.addAll(order.filter { it != itemId })
        _placeholderIndex.value = sourceIndex
        lastPlaceholderMoveAtMs = 0L
        lastDetailedPointerY = Float.NaN
        detailedDirection = 0
        detailedDragStartedAtMs = android.os.SystemClock.uptimeMillis()
        lastDetailedMoveAtMs = 0L
    }

    fun sync(latestItems: List<CountingItem>) {
        if (isDragging) return

        val latest = latestItems.sortedBy { it.order }
        val latestIds = latest.map { it.id }
        val latestById = latest.associateBy { it.id }
        val pending = pendingCommit

        canonicalItems.clear()

        if (
            pending != null &&
            latestIds != pending &&
            pending.all(latestById::containsKey)
        ) {
            canonicalItems.addAll(
                pending.mapNotNull(latestById::get)
            )
            restingOrder.clear()
            restingOrder.addAll(pending)
            return
        }

        canonicalItems.addAll(latest)
        restingOrder.clear()
        restingOrder.addAll(latestIds)

        if (pending != null && latestIds == pending) {
            pendingCommit = null
        }
    }

    fun cancel(latestItems: List<CountingItem>) {
        val latestById = latestItems.associateBy { it.id }

        restingOrder.clear()
        if (
            dragStartOrder.size == latestItems.size &&
            dragStartOrder.all(latestById::containsKey)
        ) {
            restingOrder.addAll(dragStartOrder)
        } else {
            restingOrder.addAll(
                latestItems.sortedBy { it.order }.map { it.id }
            )
        }

        finishSession()
    }

    fun commitOrder(): List<String> {
        val dragged = draggingItemId
        if (dragged == null) {
            return restingOrder.toList()
        }

        val result = backgroundOrder.toMutableList()
        result.add(
            placeholderIndex.coerceIn(0, result.size),
            dragged
        )

        pendingCommit = result.toList()
        restingOrder.clear()
        restingOrder.addAll(result)

        val byId = canonicalItems.associateBy { it.id }
        canonicalItems.clear()
        canonicalItems.addAll(
            result.mapNotNull(byId::get)
        )

        finishSession()
        return result
    }

    private fun finishSession() {
        draggingItemId = null
        mode = null
        backgroundOrder.clear()
        lastPlaceholderMoveAtMs = 0L
        lastDetailedPointerY = Float.NaN
        detailedDirection = 0
        detailedDragStartedAtMs = 0L
        lastDetailedMoveAtMs = 0L
    }

    private fun setPlaceholder(
        targetIndex: Int,
        nowMs: Long
    ): Boolean {
        if (!isDragging) return false
        if (nowMs - lastPlaceholderMoveAtMs < 88L) {
            return false
        }

        val target =
            targetIndex.coerceIn(0, backgroundOrder.size)
        if (target == placeholderIndex) return false

        _placeholderIndex.value = target
        lastPlaceholderMoveAtMs = nowMs
        return true
    }

    /**
     * Detailed is a one-dimensional insertion problem.
     *
     * The slot occupied by the invisible dragged projection is the current
     * placeholder. Entering the leading portion of another slot changes the
     * placeholder directly to that slot index, so moving back to the original
     * position is fully reversible.
     */
    fun updateDetailed(
        pointerY: Float,
        slots: List<CountingReorderSlot>,
        nowMs: Long
    ): Boolean {
        if (!isDragging || slots.isEmpty()) return false

        if (!lastDetailedPointerY.isNaN()) {
            val delta = pointerY - lastDetailedPointerY
            when {
                delta < -1.5f -> detailedDirection = -1
                delta > 1.5f -> detailedDirection = 1
            }
        }
        lastDetailedPointerY = pointerY

        // Ignore the initial pointer/layout synchronization frames.
        // This prevents item #1 from visually plunging immediately on pickup.
        if (detailedDragStartedAtMs > 0L &&
            nowMs - detailedDragStartedAtMs < 72L
        ) return false

        // Give each accepted adjacent displacement a short, symmetric settle
        // interval. The dragged overlay remains pointer-driven; only the
        // background placeholder transaction is rate-limited.
        if (lastDetailedMoveAtMs > 0L &&
            nowMs - lastDetailedMoveAtMs < 46L
        ) return false

        val current = placeholderIndex
        val currentSlot = slots.firstOrNull { it.index == current }
        if (detailedDirection == 0) return false

        if (detailedDirection < 0) {
            val previous = slots.firstOrNull { it.index == current - 1 } ?: return false
            val boundary = if (currentSlot != null) {
                (currentSlot.centerY + previous.centerY) / 2f
            } else {
                previous.centerY + previous.height * 0.12f
            }
            val hysteresis = maxOf(8f, previous.height * 0.075f)

            if (pointerY < boundary - hysteresis) {
                val moved = setPlaceholder(current - 1, nowMs)
                if (moved) lastDetailedMoveAtMs = nowMs
                return moved
            }
            return false
        }

        val next = slots.firstOrNull { it.index == current + 1 } ?: return false
        val boundary = if (currentSlot != null) {
            (currentSlot.centerY + next.centerY) / 2f
        } else {
            next.centerY - next.height * 0.12f
        }
        val hysteresis = maxOf(8f, next.height * 0.075f)

        if (pointerY > boundary + hysteresis) {
            val moved = setPlaceholder(current + 1, nowMs)
            if (moved) lastDetailedMoveAtMs = nowMs
            return moved
        }
        return false
    }

    /**
     * Compact is a real 2-D cell selection problem.
     *
     * We identify the actual visible grid cell occupied by the overlay centre
     * instead of converting directions into ±1 steps. This makes left/right,
     * up/down and all four diagonals symmetric.
     *
     * A direct target index is safe here because canonical/background order is
     * not mutated; only placeholderIndex changes.
     */
    fun updateCompact(
        pointerX: Float,
        pointerY: Float,
        slots: List<CountingReorderSlot>,
        nowMs: Long
    ): Boolean {
        if (!isDragging || slots.isEmpty()) return false

        val target = slots
            .asSequence()
            .filter {
                // The overlay centre must genuinely enter a cell.
                // A small expansion removes dead gaps without letting
                // a distant cell steal the placeholder.
                pointerX >= it.centerX - it.width * 0.54f &&
                    pointerX <= it.centerX + it.width * 0.54f &&
                    pointerY >= it.centerY - it.height * 0.54f &&
                    pointerY <= it.centerY + it.height * 0.54f
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

        return setPlaceholder(target.index, nowMs)
    }
}
