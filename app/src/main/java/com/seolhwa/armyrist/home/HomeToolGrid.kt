package com.seolhwa.armyrist.home

import android.widget.Toast
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

@Composable
fun HomeToolGrid(listState: LazyListState, viewport: Rect, card: @Composable (id: String, modifier: Modifier, dragging: Boolean) -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("killomris_home_tools_v1", 0) }
    var order by remember { mutableStateOf(HomeToolOrder.decode(preferences.getString("order", null))) }
    var before by remember { mutableStateOf(order) }
    var dragged by remember { mutableStateOf<String?>(null) }
    var grabOffset by remember { mutableStateOf(Offset.Zero) }
    var pointerRoot by remember { mutableStateOf(Offset.Zero) }
    var gridOrigin by remember { mutableStateOf(Offset.Zero) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var draggedSize by remember { mutableStateOf(IntSize.Zero) }
    // Physical slots remain fixed while tool identities move between them.
    val slots = remember { mutableStateMapOf<Int, Rect>() }
    val haptic = LocalHapticFeedback.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val inset = with(density) { 12.dp.toPx() }
    val edge = with(density) { 48.dp.toPx() }
    val currentViewport by rememberUpdatedState(viewport)
    fun placeAtPointer() {
        val id = dragged ?: return
        val point = pointerRoot - gridOrigin
        val index = slots.entries.firstOrNull { (_, rect) ->
            Rect(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset).contains(point)
        }?.key ?: return
        order = HomeToolOrder.moveToIndex(order, id, index)
    }
    LaunchedEffect(dragged) {
        while (dragged != null) {
            withFrameNanos { }
            val bounds = currentViewport
            val speed = when {
                bounds.height <= 0f -> 0f
                pointerRoot.y < bounds.top + edge -> -8f * ((bounds.top + edge - pointerRoot.y) / edge).coerceIn(0f, 1f)
                pointerRoot.y > bounds.bottom - edge -> 8f * ((pointerRoot.y - bounds.bottom + edge) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (speed != 0f) listState.scrollBy(speed)
            placeAtPointer()
        }
    }
    Box(Modifier.fillMaxWidth().onGloballyPositioned { coordinates = it; gridOrigin = it.positionInRoot() }.pointerInput(Unit) {
        detectDragGesturesAfterLongPress(
            onDragStart = { point ->
                val hit = slots.entries.firstOrNull { it.value.contains(point) }
                if (hit != null) {
                    before = order; dragged = order.getOrNull(hit.key)
                    grabOffset = point - hit.value.topLeft; pointerRoot = point + gridOrigin
                    draggedSize = IntSize(hit.value.width.roundToInt(), hit.value.height.roundToInt())
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            },
            onDrag = { change, amount ->
                if (dragged != null) { change.consume(); pointerRoot += amount; placeAtPointer() }
            },
            onDragEnd = {
                placeAtPointer()
                if (dragged != null && order != before) {
                    if (!preferences.edit().putString("order", HomeToolOrder.encode(order)).commit()) {
                        order = before
                        Toast.makeText(context, "배치를 저장하지 못했습니다.", Toast.LENGTH_LONG).show()
                    }
                }
                dragged = null
            },
            onDragCancel = { if (dragged != null) order = before; dragged = null }
        )
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            order.chunked(2).forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEachIndexed { columnIndex, id -> key(id) {
                        val index = rowIndex * 2 + columnIndex
                        Box(Modifier.weight(1f).onGloballyPositioned { child ->
                            val parent = coordinates
                            if (parent != null && parent.isAttached && child.isAttached) {
                                val start = parent.localPositionOf(child, Offset.Zero)
                                val rect = Rect(start, androidx.compose.ui.geometry.Size(child.size.width.toFloat(), child.size.height.toFloat()))
                                if (slots[index] != rect) slots[index] = rect
                            }
                        }.alpha(if (id == dragged) 0f else 1f).then(if (id == dragged) Modifier.clearAndSetSemantics { } else Modifier)) {
                            card(id, Modifier.fillMaxWidth(), dragged != null)
                        }
                    } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        dragged?.let { id ->
            val size = with(density) { draggedSize.width.toDp() to draggedSize.height.toDp() }
            Box(Modifier.offset { IntOffset((pointerRoot.x - gridOrigin.x - grabOffset.x).roundToInt(), (pointerRoot.y - gridOrigin.y - grabOffset.y).roundToInt()) }
                .size(size.first, size.second).zIndex(10f).alpha(0.92f)) { card(id, Modifier.fillMaxSize(), true) }
        }
    }
}
