package com.seolhwa.armyrist.home

import org.json.JSONArray

object HomeToolOrder {
    val defaults = listOf("counting", "checklist", "timeplan", "report", "handicap")
    fun normalize(values: List<String>) = values.filter { it in defaults }.distinct().let { it + defaults.filterNot(it::contains) }
    fun decode(raw: String?): List<String> = runCatching {
        val array = JSONArray(raw ?: return defaults)
        normalize((0 until array.length()).map { array.optString(it) })
    }.getOrDefault(defaults)
    fun encode(values: List<String>) = JSONArray(normalize(values)).toString()
    fun move(values: List<String>, id: String, target: String): List<String> = moveToIndex(values, id, normalize(values).indexOf(target))
    fun moveToIndex(values: List<String>, id: String, index: Int): List<String> {
        val order = normalize(values)
        val from = order.indexOf(id)
        if (from < 0 || index !in order.indices || from == index) return order
        return order.toMutableList().also { it.removeAt(from); it.add(index, id) }
    }
}
