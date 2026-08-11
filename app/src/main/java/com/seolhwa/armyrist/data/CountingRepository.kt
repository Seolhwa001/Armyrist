package com.seolhwa.armyrist.data

import android.content.Context
import com.seolhwa.armyrist.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class CountingRepository(context: Context) {
    private val prefs = context.getSharedPreferences("armyrist_stage1", Context.MODE_PRIVATE)
    private val key = "snapshot_v1"

    @Volatile
    private var sheets: List<CountingSheet> = load()

    @Synchronized
    fun getSheets(): List<CountingSheet> = sheets.sortedByDescending { it.updatedAt }

    @Synchronized
    fun getSheet(id: String): CountingSheet? = sheets.firstOrNull { it.id == id }

    @Synchronized
    fun createSheet(): CountingSheet {
        val sheet = CountingSheet()
        sheets = sheets + sheet
        persist()
        return sheet
    }

    @Synchronized
    fun updateSheet(id: String, transform: (CountingSheet) -> CountingSheet): CountingSheet? {
        val current = sheets.firstOrNull { it.id == id } ?: return null
        val next = transform(current).copy(updatedAt = System.currentTimeMillis())
        require(next.title.trim().isNotEmpty())
        require(next.items.all { it.quantity >= 0 && it.name.trim().isNotEmpty() && it.unit.trim().isNotEmpty() })
        require(next.groups.all { it.name.trim().isNotEmpty() })
        val groupIds = next.groups.map { it.id }.toSet()
        require(next.items.all { it.groupId == null || it.groupId in groupIds })
        require(next.calculations.all { it.leftGroupId in groupIds && it.rightGroupId in groupIds })
        sheets = sheets.map { if (it.id == id) next else it }
        persist()
        return next
    }

    @Synchronized
    fun renameSheet(id: String, title: String): Boolean {
        val normalized = DomainRules.normalizeRequired(title) ?: return false
        return updateSheet(id) { it.copy(title = normalized) } != null
    }

    @Synchronized
    fun deleteSheet(id: String) {
        sheets = sheets.filterNot { it.id == id }
        persist()
    }

    @Synchronized
    fun addItem(sheetId: String, name: String, quantity: Int, unit: String, note: String, groupId: String?): Boolean {
        val n = DomainRules.normalizeRequired(name) ?: return false
        val u = DomainRules.normalizeRequired(unit) ?: return false
        if (quantity < 0) return false
        val sheet = getSheet(sheetId) ?: return false
        if (groupId != null && sheet.groups.none { it.id == groupId }) return false
        val nextOrder = (sheet.items.maxOfOrNull { it.order } ?: -1) + 1
        val item = CountingItem(sheetId = sheetId, name = n, quantity = quantity, unit = u, note = note.trim(), groupId = groupId, order = nextOrder)
        return updateSheet(sheetId) { it.copy(items = it.items + item) } != null
    }

    @Synchronized
    fun editItem(sheetId: String, itemId: String, name: String, unit: String, note: String, groupId: String?): Boolean {
        val n = DomainRules.normalizeRequired(name) ?: return false
        val u = DomainRules.normalizeRequired(unit) ?: return false
        val sheet = getSheet(sheetId) ?: return false
        if (groupId != null && sheet.groups.none { it.id == groupId }) return false
        return updateSheet(sheetId) { s ->
            s.copy(items = s.items.map {
                if (it.id == itemId) it.copy(name = n, unit = u, note = note.trim(), groupId = groupId) else it
            })
        } != null
    }

    @Synchronized
    fun setQuantity(sheetId: String, itemId: String, quantity: Int): Boolean {
        if (quantity < 0) return false
        return updateSheet(sheetId) { s ->
            s.copy(items = s.items.map { if (it.id == itemId) it.copy(quantity = quantity) else it })
        } != null
    }

    fun increment(sheetId: String, itemId: String) {
        val item = getSheet(sheetId)?.items?.firstOrNull { it.id == itemId } ?: return
        if (item.quantity < Int.MAX_VALUE) setQuantity(sheetId, itemId, item.quantity + 1)
    }

    fun decrement(sheetId: String, itemId: String) {
        val item = getSheet(sheetId)?.items?.firstOrNull { it.id == itemId } ?: return
        setQuantity(sheetId, itemId, (item.quantity - 1).coerceAtLeast(0))
    }

    @Synchronized
    fun deleteItem(sheetId: String, itemId: String) {
        updateSheet(sheetId) { s ->
            val normalized = s.items.filterNot { it.id == itemId }
                .sortedBy { it.order }.mapIndexed { index, item -> item.copy(order = index) }
            s.copy(items = normalized)
        }
    }

    @Synchronized
    fun moveItem(sheetId: String, itemId: String, delta: Int) {
        val sheet = getSheet(sheetId) ?: return
        val sorted = sheet.items.sortedBy { it.order }.toMutableList()
        val from = sorted.indexOfFirst { it.id == itemId }
        if (from < 0) return
        val to = (from + delta).coerceIn(0, sorted.lastIndex)
        if (from == to) return
        val item = sorted.removeAt(from)
        sorted.add(to, item)
        updateSheet(sheetId) { it.copy(items = sorted.mapIndexed { i, v -> v.copy(order = i) }) }
    }

    @Synchronized
    fun addGroup(sheetId: String, name: String, color: String = "#6750A4"): Boolean {
        val n = DomainRules.normalizeRequired(name) ?: return false
        val sheet = getSheet(sheetId) ?: return false
        val order = (sheet.groups.maxOfOrNull { it.order } ?: -1) + 1
        val group = CountingGroup(sheetId = sheetId, name = n, order = order, color = color)
        return updateSheet(sheetId) { it.copy(groups = it.groups + group) } != null
    }

    @Synchronized
    fun renameGroup(sheetId: String, groupId: String, name: String, color: String? = null): Boolean {
        val n = DomainRules.normalizeRequired(name) ?: return false
        return updateSheet(sheetId) { s ->
            s.copy(groups = s.groups.map { if (it.id == groupId) it.copy(name = n, color = color ?: it.color) else it })
        } != null
    }


    @Synchronized
    fun assignItemsToGroup(sheetId: String, itemIds: Set<String>, groupId: String?): Boolean {
        val sheet = getSheet(sheetId) ?: return false
        if (groupId != null && sheet.groups.none { it.id == groupId }) return false
        return updateSheet(sheetId) { s ->
            s.copy(items = s.items.map { if (it.id in itemIds) it.copy(groupId = groupId) else it })
        } != null
    }

    @Synchronized
    fun deleteGroup(sheetId: String, groupId: String) {
        updateSheet(sheetId) { s ->
            val groups = s.groups.filterNot { it.id == groupId }
                .sortedBy { it.order }.mapIndexed { i, g -> g.copy(order = i) }
            s.copy(
                groups = groups,
                items = s.items.map { if (it.groupId == groupId) it.copy(groupId = null) else it },
                calculations = s.calculations.filterNot { it.leftGroupId == groupId || it.rightGroupId == groupId }
            )
        }
    }

    @Synchronized
    fun addCalculation(sheetId: String, left: String, operator: CalculationOperator, right: String, name: String): Boolean {
        val sheet = getSheet(sheetId) ?: return false
        if (sheet.groups.none { it.id == left } || sheet.groups.none { it.id == right }) return false
        val calc = GroupCalculation(sheetId = sheetId, leftGroupId = left, operator = operator, rightGroupId = right, name = name.trim())
        return updateSheet(sheetId) { it.copy(calculations = it.calculations + calc) } != null
    }

    @Synchronized
    fun editCalculation(sheetId: String, calcId: String, left: String, operator: CalculationOperator, right: String, name: String): Boolean {
        val sheet = getSheet(sheetId) ?: return false
        if (sheet.groups.none { it.id == left } || sheet.groups.none { it.id == right }) return false
        return updateSheet(sheetId) { s ->
            s.copy(calculations = s.calculations.map {
                if (it.id == calcId) it.copy(leftGroupId = left, operator = operator, rightGroupId = right, name = name.trim()) else it
            })
        } != null
    }

    @Synchronized
    fun deleteCalculation(sheetId: String, calcId: String) {
        updateSheet(sheetId) { it.copy(calculations = it.calculations.filterNot { c -> c.id == calcId }) }
    }

    @Synchronized
    fun setMemo(sheetId: String, memo: String) {
        updateSheet(sheetId) { it.copy(memo = memo) }
    }

    private fun persist() {
        val root = JSONObject().put("version", 1).put("sheets", JSONArray().apply {
            sheets.forEach { put(sheetToJson(it)) }
        })
        // commit() is synchronous: a committed domain change is durable before returning.
        prefs.edit().putString(key, root.toString()).commit()
    }

    private fun load(): List<CountingSheet> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val arr = JSONObject(raw).getJSONArray("sheets")
            List(arr.length()) { index -> sheetFromJson(arr.getJSONObject(index)) }
        }.getOrElse { emptyList() }
    }

    private fun sheetToJson(s: CountingSheet) = JSONObject()
        .put("id", s.id).put("title", s.title).put("memo", s.memo)
        .put("createdAt", s.createdAt).put("updatedAt", s.updatedAt)
        .put("items", JSONArray().apply { s.items.forEach { put(itemToJson(it)) } })
        .put("groups", JSONArray().apply { s.groups.forEach { put(groupToJson(it)) } })
        .put("calculations", JSONArray().apply { s.calculations.forEach { put(calcToJson(it)) } })

    private fun itemToJson(i: CountingItem) = JSONObject()
        .put("id", i.id).put("sheetId", i.sheetId).put("name", i.name)
        .put("quantity", i.quantity).put("unit", i.unit).put("note", i.note)
        .put("groupId", i.groupId ?: JSONObject.NULL).put("order", i.order)

    private fun groupToJson(g: CountingGroup) = JSONObject()
        .put("id", g.id).put("sheetId", g.sheetId).put("name", g.name).put("order", g.order).put("color", g.color)

    private fun calcToJson(c: GroupCalculation) = JSONObject()
        .put("id", c.id).put("sheetId", c.sheetId).put("leftGroupId", c.leftGroupId)
        .put("operator", c.operator.name).put("rightGroupId", c.rightGroupId).put("name", c.name)

    private fun sheetFromJson(o: JSONObject): CountingSheet {
        val items = o.getJSONArray("items")
        val groups = o.getJSONArray("groups")
        val calculations = o.getJSONArray("calculations")
        return CountingSheet(
            id = o.getString("id"), title = o.getString("title"), memo = o.optString("memo", ""),
            items = List(items.length()) { itemFromJson(items.getJSONObject(it)) },
            groups = List(groups.length()) { groupFromJson(groups.getJSONObject(it)) },
            calculations = List(calculations.length()) { calcFromJson(calculations.getJSONObject(it)) },
            createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt")
        )
    }

    private fun itemFromJson(o: JSONObject) = CountingItem(
        id = o.getString("id"), sheetId = o.getString("sheetId"), name = o.getString("name"),
        quantity = o.getInt("quantity"), unit = o.getString("unit"), note = o.optString("note", ""),
        groupId = if (o.isNull("groupId")) null else o.getString("groupId"), order = o.getInt("order")
    )

    private fun groupFromJson(o: JSONObject) = CountingGroup(
        id = o.getString("id"), sheetId = o.getString("sheetId"), name = o.getString("name"), order = o.getInt("order"), color = o.optString("color", "#6750A4")
    )

    private fun calcFromJson(o: JSONObject) = GroupCalculation(
        id = o.getString("id"), sheetId = o.getString("sheetId"), leftGroupId = o.getString("leftGroupId"),
        operator = CalculationOperator.valueOf(o.getString("operator")),
        rightGroupId = o.getString("rightGroupId"), name = o.optString("name", "")
    )
}
