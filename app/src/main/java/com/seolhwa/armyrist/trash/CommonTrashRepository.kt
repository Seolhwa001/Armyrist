package com.seolhwa.armyrist.trash

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class CommonTrashRepository(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    @Synchronized
    fun getItems(toolType: TrashToolType? = null): List<CommonTrashItem> {
        purgeExpired()
        return loadItems()
            .asSequence()
            .filter { toolType == null || it.toolType == toolType }
            .sortedByDescending { it.deletedAt }
            .toList()
    }

    @Synchronized
    fun moveToTrash(
        toolType: TrashToolType,
        originalId: String,
        title: String,
        payloadVersion: Int,
        payload: String
    ): CommonTrashItem? {
        if (originalId.isBlank() || payload.isBlank()) return null

        val current = loadItems().filterNot {
            it.toolType == toolType && it.originalId == originalId
        }

        val item = CommonTrashItem(
            id = UUID.randomUUID().toString(),
            toolType = toolType,
            originalId = originalId,
            title = title.ifBlank { "제목 없음" },
            deletedAt = System.currentTimeMillis(),
            payloadVersion = payloadVersion,
            payload = payload
        )

        return if (persist(current + item)) item else null
    }

    @Synchronized
    fun permanentlyDelete(itemId: String): Boolean {
        val current = loadItems()
        val next = current.filterNot { it.id == itemId }
        if (next.size == current.size) return false
        return persist(next)
    }

    @Synchronized
    fun contains(itemId: String): Boolean =
        loadItems().any { it.id == itemId }

    @Synchronized
    fun retentionDays(): Int =
        prefs.getInt(KEY_RETENTION_DAYS, DEFAULT_RETENTION_DAYS)
            .takeIf { it in CommonTrashRetention.supportedDays }
            ?: DEFAULT_RETENTION_DAYS

    @Synchronized
    fun setRetentionDays(days: Int): Boolean {
        if (days !in CommonTrashRetention.supportedDays) return false
        if (!prefs.edit().putInt(KEY_RETENTION_DAYS, days).commit()) return false
        purgeExpired()
        return true
    }

    @Synchronized
    fun purgeExpired(nowMillis: Long = System.currentTimeMillis()): Int {
        val retention = retentionDays()
        if (retention == CommonTrashRetention.NEVER) return 0

        val cutoff = nowMillis - TimeUnit.DAYS.toMillis(retention.toLong())
        val current = loadItems()
        val next = current.filter { it.deletedAt > cutoff }
        val removed = current.size - next.size
        if (removed > 0) persist(next)
        return removed
    }

    private fun loadItems(): List<CommonTrashItem> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            val root = JSONObject(raw)
            val array = root.optJSONArray("items") ?: JSONArray()
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    val toolType = runCatching {
                        TrashToolType.valueOf(obj.getString("toolType"))
                    }.getOrNull() ?: continue

                    add(
                        CommonTrashItem(
                            id = obj.getString("id"),
                            toolType = toolType,
                            originalId = obj.getString("originalId"),
                            title = obj.optString("title", "제목 없음"),
                            deletedAt = obj.getLong("deletedAt"),
                            payloadVersion = obj.optInt("payloadVersion", 1),
                            payload = obj.getString("payload")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persist(items: List<CommonTrashItem>): Boolean {
        val root =
            JSONObject()
                .put("schemaVersion", SCHEMA_VERSION)
                .put(
                    "items",
                    JSONArray().apply {
                        items.forEach { item ->
                            put(
                                JSONObject()
                                    .put("id", item.id)
                                    .put("toolType", item.toolType.name)
                                    .put("originalId", item.originalId)
                                    .put("title", item.title)
                                    .put("deletedAt", item.deletedAt)
                                    .put("payloadVersion", item.payloadVersion)
                                    .put("payload", item.payload)
                            )
                        }
                    }
                )

        return prefs.edit().putString(KEY_ITEMS, root.toString()).commit()
    }

    companion object {
        private const val PREFS_NAME = "armyrist_common_trash"
        private const val KEY_ITEMS = "items_v1"
        private const val KEY_RETENTION_DAYS = "retention_days"
        private const val SCHEMA_VERSION = 1
        private const val DEFAULT_RETENTION_DAYS = 30
    }
}
