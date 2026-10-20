package com.seolhwa.armyrist.ui

import androidx.compose.runtime.*

/** Pointer movement only changes this local projection. A changed drop emits one order. */
@Stable
internal class IdReorderDraft(initial: List<String>) {
    var order: List<String> by mutableStateOf(initial.toList())
        private set
    var draggingId: String? by mutableStateOf(null)
        private set
    private var original = order

    fun sync(ids: List<String>) {
        if (draggingId == null && order != ids) order = ids.toList()
    }

    fun begin(id: String): Boolean {
        if (id !in order || draggingId != null) return false
        original = order
        draggingId = id
        return true
    }

    fun moveTo(targetIndex: Int): Boolean {
        val from = order.indexOf(draggingId)
        if (from < 0) return false
        val to = targetIndex.coerceIn(0, order.lastIndex)
        if (from == to) return false
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        return true
    }

    fun finish(commit: Boolean): List<String>? {
        if (draggingId == null) return null
        draggingId = null
        val changed = order != original
        if (!commit) order = original
        return if (commit && changed) order.toList() else null
    }
}

internal fun <T> reorderedByIds(items: List<T>, ids: List<String>, idOf: (T) -> String): List<T>? {
    val byId = items.associateBy(idOf)
    if (byId.size != items.size || ids.size != items.size || ids.toSet().size != ids.size) return null
    if (ids.toSet() != byId.keys) return null
    return ids.map { byId.getValue(it) }
}
