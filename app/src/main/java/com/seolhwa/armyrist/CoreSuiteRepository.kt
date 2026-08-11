package com.seolhwa.armyrist.stage2.data

import android.content.Context
import com.seolhwa.armyrist.stage2.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class CoreSuiteRepository(context: Context) {
    private val prefs = context.getSharedPreferences(
        "armyrist_stage2_core",
        Context.MODE_PRIVATE
    )

    private val snapshotKey = "snapshot_v1"

    private var checklists: List<Checklist> = emptyList()
    private var timePlans: List<TimePlan> = emptyList()
    private var userProfile: UserProfile = UserProfile()
    private var reportTemplates: List<ReportTemplate> = emptyList()

    init {
        load()
    }

    @Synchronized
    fun getChecklists(): List<Checklist> =
        checklists.sortedByDescending { it.updatedAt }

    @Synchronized
    fun getChecklist(id: String): Checklist? =
        checklists.firstOrNull { it.id == id }

    @Synchronized
    fun createChecklist(): Checklist {
        val checklist = Checklist()
        checklists = checklists + checklist
        persist()
        return checklist
    }

    @Synchronized
    fun updateChecklist(
        id: String,
        transform: (Checklist) -> Checklist
    ): Checklist? {
        val current = getChecklist(id) ?: return null
        val next = transform(current).copy(
            updatedAt = System.currentTimeMillis()
        )

        require(next.title.trim().isNotEmpty())
        require(next.groups.all { it.name.trim().isNotEmpty() })
        require(next.items.all { it.name.trim().isNotEmpty() })
        require(next.deletedItems.all { it.name.trim().isNotEmpty() })
        require(next.items.all { item ->
            !item.notificationEnabled ||
                (item.scheduledTimeMinutes != null && item.scheduledTimeMinutes in 0..1439)
        })

        val groupIds = next.groups.map { it.id }.toSet()
        require(next.items.all {
            it.groupId == null || it.groupId in groupIds
        })

        checklists = checklists.map {
            if (it.id == id) next else it
        }
        persist()
        return next
    }

    @Synchronized
    fun renameChecklist(id: String, title: String): Boolean {
        val normalized = title.trim().takeIf { it.isNotEmpty() } ?: return false
        return updateChecklist(id) { it.copy(title = normalized) } != null
    }

    @Synchronized
    fun deleteChecklist(id: String) {
        checklists = checklists.filterNot { it.id == id }
        persist()
    }

    @Synchronized
    fun addChecklistItem(
        checklistId: String,
        name: String,
        note: String = "",
        groupId: String? = null,
        notificationEnabled: Boolean = false,
        scheduledTimeMinutes: Int? = null,
        notificationSoundUri: String? = null
    ): Boolean {
        val normalized = name.trim().takeIf { it.isNotEmpty() } ?: return false
        val checklist = getChecklist(checklistId) ?: return false

        if (groupId != null && checklist.groups.none { it.id == groupId }) {
            return false
        }

        val order = (checklist.items.maxOfOrNull { it.order } ?: -1) + 1
        val item = ChecklistItem(
            checklistId = checklistId,
            groupId = groupId,
            order = order,
            name = normalized,
            note = note.trim(),
            notificationEnabled = notificationEnabled,
            scheduledTimeMinutes = scheduledTimeMinutes,
            notificationSoundUri = notificationSoundUri
        )

        return updateChecklist(checklistId) {
            it.copy(items = it.items + item)
        } != null
    }

    @Synchronized
    fun editChecklistItem(
        checklistId: String,
        itemId: String,
        name: String,
        note: String,
        groupId: String?,
        notificationEnabled: Boolean = false,
        scheduledTimeMinutes: Int? = null,
        notificationSoundUri: String? = null
    ): Boolean {
        val normalized = name.trim().takeIf { it.isNotEmpty() } ?: return false
        val checklist = getChecklist(checklistId) ?: return false

        if (groupId != null && checklist.groups.none { it.id == groupId }) {
            return false
        }

        return updateChecklist(checklistId) { current ->
            current.copy(
                items = current.items.map {
                    if (it.id == itemId) {
                        it.copy(
                            name = normalized,
                            note = note.trim(),
                            groupId = groupId,
                            notificationEnabled = notificationEnabled,
                            scheduledTimeMinutes = scheduledTimeMinutes,
                            notificationSoundUri = notificationSoundUri
                        )
                    } else it
                }
            )
        } != null
    }

    @Synchronized
    fun setChecklistNotificationSoundForEnabledItems(
        checklistId: String,
        notificationSoundUri: String?
    ): Boolean =
        updateChecklist(checklistId) { current ->
            current.copy(
                items = current.items.map { item ->
                    if (item.notificationEnabled) {
                        item.copy(notificationSoundUri = notificationSoundUri)
                    } else {
                        item
                    }
                }
            )
        } != null

    @Synchronized
    fun setChecklistStatus(
        checklistId: String,
        itemId: String,
        status: ChecklistStatus
    ): Boolean =
        updateChecklist(checklistId) { current ->
            current.copy(
                items = current.items.map {
                    if (it.id == itemId) it.copy(status = status) else it
                }
            )
        } != null

    @Synchronized
    fun trashChecklistItem(checklistId: String, itemId: String): Boolean {
        val checklist = getChecklist(checklistId) ?: return false
        val target = checklist.items.firstOrNull { it.id == itemId } ?: return false

        return updateChecklist(checklistId) { current ->
            current.copy(
                items = current.items
                    .filterNot { it.id == itemId }
                    .sortedBy { it.order }
                    .mapIndexed { index, item ->
                        item.copy(order = index)
                    },
                deletedItems = current.deletedItems
                    .filterNot { it.id == itemId } + target
            )
        } != null
    }

    @Synchronized
    fun restoreChecklistItem(checklistId: String, itemId: String): Boolean {
        val checklist = getChecklist(checklistId) ?: return false
        val target = checklist.deletedItems.firstOrNull { it.id == itemId } ?: return false

        val restoredGroupId = target.groupId?.takeIf { groupId ->
            checklist.groups.any { it.id == groupId }
        }

        val active = checklist.items.sortedBy { it.order }.toMutableList()
        val insertIndex = target.order.coerceIn(0, active.size)
        active.add(insertIndex, target.copy(groupId = restoredGroupId))

        return updateChecklist(checklistId) { current ->
            current.copy(
                items = active.mapIndexed { index, item ->
                    item.copy(order = index)
                },
                deletedItems = current.deletedItems.filterNot { it.id == itemId }
            )
        } != null
    }

    @Synchronized
    fun permanentlyDeleteChecklistItem(checklistId: String, itemId: String): Boolean =
        updateChecklist(checklistId) { current ->
            current.copy(
                deletedItems = current.deletedItems.filterNot { it.id == itemId }
            )
        } != null

    @Synchronized
    fun moveChecklistItem(
        checklistId: String,
        itemId: String,
        delta: Int
    ) {
        val checklist = getChecklist(checklistId) ?: return
        val ordered = checklist.items.sortedBy { it.order }.toMutableList()
        val from = ordered.indexOfFirst { it.id == itemId }
        if (from < 0) return

        val to = (from + delta).coerceIn(0, ordered.lastIndex)
        if (from == to) return

        val item = ordered.removeAt(from)
        ordered.add(to, item)

        updateChecklist(checklistId) {
            it.copy(
                items = ordered.mapIndexed { index, value ->
                    value.copy(order = index)
                }
            )
        }
    }

    @Synchronized
    fun addChecklistGroup(
        checklistId: String,
        name: String,
        color: String = "#6750A4"
    ): Boolean {
        val normalized = name.trim().takeIf { it.isNotEmpty() } ?: return false
        val checklist = getChecklist(checklistId) ?: return false
        val order = (checklist.groups.maxOfOrNull { it.order } ?: -1) + 1

        return updateChecklist(checklistId) {
            it.copy(
                groups = it.groups + ChecklistGroup(
                    checklistId = checklistId,
                    name = normalized,
                    order = order,
                    color = color
                )
            )
        } != null
    }

    @Synchronized
    fun setChecklistGroupColor(
        checklistId: String,
        groupId: String,
        color: String
    ): Boolean =
        updateChecklist(checklistId) { current ->
            current.copy(
                groups = current.groups.map {
                    if (it.id == groupId) it.copy(color = color) else it
                }
            )
        } != null

    @Synchronized
    fun assignChecklistItemsToGroup(
        checklistId: String,
        itemIds: Set<String>,
        groupId: String?
    ): Boolean {
        val checklist = getChecklist(checklistId) ?: return false
        if (groupId != null && checklist.groups.none { it.id == groupId }) return false
        if (itemIds.isEmpty()) return true

        return updateChecklist(checklistId) { current ->
            current.copy(
                items = current.items.map { item ->
                    if (item.id in itemIds) item.copy(groupId = groupId) else item
                }
            )
        } != null
    }

    @Synchronized
    fun renameChecklistGroup(
        checklistId: String,
        groupId: String,
        name: String
    ): Boolean {
        val normalized = name.trim().takeIf { it.isNotEmpty() } ?: return false

        return updateChecklist(checklistId) { current ->
            current.copy(
                groups = current.groups.map {
                    if (it.id == groupId) it.copy(name = normalized) else it
                }
            )
        } != null
    }

    @Synchronized
    fun deleteChecklistGroup(checklistId: String, groupId: String) {
        updateChecklist(checklistId) { current ->
            current.copy(
                groups = current.groups
                    .filterNot { it.id == groupId }
                    .sortedBy { it.order }
                    .mapIndexed { index, group ->
                        group.copy(order = index)
                    },
                items = current.items.map {
                    if (it.groupId == groupId) it.copy(groupId = null) else it
                }
            )
        }
    }

    @Synchronized
    fun resetChecklistStatuses(checklistId: String) {
        updateChecklist(checklistId) {
            it.copy(items = ChecklistRules.resetStatuses(it.items))
        }
    }

    @Synchronized
    fun setChecklistMemo(checklistId: String, memo: String) {
        updateChecklist(checklistId) {
            it.copy(memo = memo)
        }
    }

    @Synchronized
    fun getTimePlans(): List<TimePlan> =
        timePlans.sortedByDescending { it.updatedAt }

    @Synchronized
    fun getTimePlan(id: String): TimePlan? =
        timePlans.firstOrNull { it.id == id }

    @Synchronized
    fun createTimePlan(): TimePlan {
        val planId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val plan = TimePlan(
            id = planId,
            points = listOf(
                TimePoint(
                    planId = planId,
                    order = 0,
                    name = "시작",
                    timeMinutes = null
                ),
                TimePoint(
                    planId = planId,
                    order = 1,
                    name = "종료",
                    timeMinutes = null
                )
            ),
            createdAt = now,
            updatedAt = now
        )

        timePlans = timePlans + plan
        persist()
        return plan
    }

    @Synchronized
    fun updateTimePlan(
        id: String,
        transform: (TimePlan) -> TimePlan
    ): TimePlan? {
        val current = getTimePlan(id) ?: return null
        val next = transform(current).copy(
            updatedAt = System.currentTimeMillis()
        )

        require(next.title.trim().isNotEmpty())
        require(next.points.size >= 2)
        require(next.points.all { it.name.trim().isNotEmpty() })
        require(next.points.all {
            it.timeMinutes == null || it.timeMinutes in 0..1439
        })

        val definedPoints = next.points.filter { it.timeMinutes != null }
        if (definedPoints.size == next.points.size) {
            require(TimePlanRules.derive(next.points) != null)
        }

        timePlans = timePlans.map {
            if (it.id == id) next else it
        }
        persist()
        return next
    }

    @Synchronized
    fun renameTimePlan(id: String, title: String): Boolean {
        val normalized = title.trim().takeIf { it.isNotEmpty() } ?: return false
        return updateTimePlan(id) { it.copy(title = normalized) } != null
    }

    @Synchronized
    fun deleteTimePlan(id: String) {
        timePlans = timePlans.filterNot { it.id == id }
        persist()
    }

    @Synchronized
    fun setUserProfile(displayName: String) {
        userProfile = UserProfile(displayName = displayName)
        persist()
    }

    @Synchronized
    fun getUserProfile(): UserProfile = userProfile

    @Synchronized
    fun getReportTemplates(): List<ReportTemplate> =
        reportTemplates.sortedBy { it.order }

    @Synchronized
    fun getReportTemplate(id: String): ReportTemplate? =
        reportTemplates.firstOrNull { it.id == id }

    @Synchronized
    fun createReportTemplate(
        name: String,
        body: String = ""
    ): ReportTemplate? {
        val normalized = name.trim().takeIf { it.isNotEmpty() } ?: return null
        val order = (reportTemplates.maxOfOrNull { it.order } ?: -1) + 1

        val template = ReportTemplate(
            name = normalized,
            body = body,
            order = order
        )

        reportTemplates = reportTemplates + template
        persist()
        return template
    }

    @Synchronized
    fun updateReportTemplate(
        id: String,
        name: String,
        body: String
    ): Boolean {
        val normalized = name.trim().takeIf { it.isNotEmpty() } ?: return false
        if (reportTemplates.none { it.id == id }) return false

        reportTemplates = reportTemplates.map {
            if (it.id == id) {
                it.copy(name = normalized, body = body)
            } else it
        }
        persist()
        return true
    }

    @Synchronized
    fun setDefaultTemplate(id: String?) {
        reportTemplates = reportTemplates.map {
            it.copy(isDefault = id != null && it.id == id)
        }
        persist()
    }

    @Synchronized
    fun deleteReportTemplate(id: String) {
        reportTemplates = reportTemplates
            .filterNot { it.id == id }
            .sortedBy { it.order }
            .mapIndexed { index, template ->
                template.copy(order = index)
            }
        persist()
    }

    private fun persist() {
        val root = JSONObject()
            .put("version", 1)
            .put("checklists", JSONArray().apply {
                checklists.forEach { put(checklistToJson(it)) }
            })
            .put("timePlans", JSONArray().apply {
                timePlans.forEach { put(timePlanToJson(it)) }
            })
            .put("userProfile", JSONObject().apply {
                put("displayName", userProfile.displayName)
            })
            .put("reportTemplates", JSONArray().apply {
                reportTemplates.forEach { put(templateToJson(it)) }
            })

        prefs.edit()
            .putString(snapshotKey, root.toString())
            .commit()
    }

    private fun load() {
        val raw = prefs.getString(snapshotKey, null) ?: return

        runCatching {
            val root = JSONObject(raw)

            val checklistArray = root.optJSONArray("checklists") ?: JSONArray()
            checklists = List(checklistArray.length()) {
                checklistFromJson(checklistArray.getJSONObject(it))
            }

            val timePlanArray = root.optJSONArray("timePlans") ?: JSONArray()
            timePlans = List(timePlanArray.length()) {
                timePlanFromJson(timePlanArray.getJSONObject(it))
            }

            val profile = root.optJSONObject("userProfile")
            userProfile = UserProfile(
                displayName = profile?.optString("displayName", "") ?: ""
            )

            val templateArray = root.optJSONArray("reportTemplates") ?: JSONArray()
            reportTemplates = List(templateArray.length()) {
                templateFromJson(templateArray.getJSONObject(it))
            }
        }
    }

    private fun checklistToJson(value: Checklist): JSONObject =
        JSONObject()
            .put("id", value.id)
            .put("title", value.title)
            .put("memo", value.memo)
            .put("createdAt", value.createdAt)
            .put("updatedAt", value.updatedAt)
            .put("groups", JSONArray().apply {
                value.groups.forEach { group ->
                    put(
                        JSONObject()
                            .put("id", group.id)
                            .put("checklistId", group.checklistId)
                            .put("name", group.name)
                            .put("order", group.order)
                            .put("color", group.color)
                    )
                }
            })
            .put("items", JSONArray().apply {
                value.items.forEach { item ->
                    put(
                        JSONObject()
                            .put("id", item.id)
                            .put("checklistId", item.checklistId)
                            .put("groupId", item.groupId ?: JSONObject.NULL)
                            .put("order", item.order)
                            .put("name", item.name)
                            .put("status", item.status.name)
                            .put("note", item.note)
                            .put("notificationEnabled", item.notificationEnabled)
                            .put("scheduledTimeMinutes", item.scheduledTimeMinutes ?: JSONObject.NULL)
                            .put("notificationSoundUri", item.notificationSoundUri ?: JSONObject.NULL)
                    )
                }
            })
            .put("deletedItems", JSONArray().apply {
                value.deletedItems.forEach { item ->
                    put(
                        JSONObject()
                            .put("id", item.id)
                            .put("checklistId", item.checklistId)
                            .put("groupId", item.groupId ?: JSONObject.NULL)
                            .put("order", item.order)
                            .put("name", item.name)
                            .put("status", item.status.name)
                            .put("note", item.note)
                            .put("notificationEnabled", item.notificationEnabled)
                            .put("scheduledTimeMinutes", item.scheduledTimeMinutes ?: JSONObject.NULL)
                            .put("notificationSoundUri", item.notificationSoundUri ?: JSONObject.NULL)
                    )
                }
            })

    private fun checklistFromJson(value: JSONObject): Checklist {
        val groups = value.optJSONArray("groups") ?: JSONArray()
        val items = value.optJSONArray("items") ?: JSONArray()
        val deletedItems = value.optJSONArray("deletedItems") ?: JSONArray()

        return Checklist(
            id = value.getString("id"),
            title = value.getString("title"),
            memo = value.optString("memo", ""),
            groups = List(groups.length()) { index ->
                val group = groups.getJSONObject(index)
                ChecklistGroup(
                    id = group.getString("id"),
                    checklistId = group.getString("checklistId"),
                    name = group.getString("name"),
                    order = group.getInt("order"),
                    color = group.optString("color", "#6750A4")
                )
            },
            items = List(items.length()) { index ->
                val item = items.getJSONObject(index)
                ChecklistItem(
                    id = item.getString("id"),
                    checklistId = item.getString("checklistId"),
                    groupId = if (item.isNull("groupId")) null
                    else item.getString("groupId"),
                    order = item.getInt("order"),
                    name = item.getString("name"),
                    status = ChecklistStatus.valueOf(
                        item.getString("status")
                    ),
                    note = item.optString("note", ""),
                    notificationEnabled = item.optBoolean("notificationEnabled", false),
                    scheduledTimeMinutes = if (item.isNull("scheduledTimeMinutes")) null
                    else item.optInt("scheduledTimeMinutes"),
                    notificationSoundUri = if (item.isNull("notificationSoundUri")) null
                    else item.optString("notificationSoundUri")
                )
            },
            deletedItems = List(deletedItems.length()) { index ->
                val item = deletedItems.getJSONObject(index)
                ChecklistItem(
                    id = item.getString("id"),
                    checklistId = item.getString("checklistId"),
                    groupId = if (item.isNull("groupId")) null
                    else item.getString("groupId"),
                    order = item.getInt("order"),
                    name = item.getString("name"),
                    status = ChecklistStatus.valueOf(
                        item.getString("status")
                    ),
                    note = item.optString("note", ""),
                    notificationEnabled = item.optBoolean("notificationEnabled", false),
                    scheduledTimeMinutes = if (item.isNull("scheduledTimeMinutes")) null
                    else item.optInt("scheduledTimeMinutes"),
                    notificationSoundUri = if (item.isNull("notificationSoundUri")) null
                    else item.optString("notificationSoundUri")
                )
            },
            createdAt = value.getLong("createdAt"),
            updatedAt = value.getLong("updatedAt")
        )
    }

    private fun timePlanToJson(value: TimePlan): JSONObject =
        JSONObject()
            .put("id", value.id)
            .put("title", value.title)
            .put("memo", value.memo)
            .put("createdAt", value.createdAt)
            .put("updatedAt", value.updatedAt)
            .put("points", JSONArray().apply {
                value.points.forEach { point ->
                    put(
                        JSONObject()
                            .put("id", point.id)
                            .put("planId", point.planId)
                            .put("order", point.order)
                            .put("name", point.name)
                            .put(
                                "timeMinutes",
                                point.timeMinutes ?: JSONObject.NULL
                            )
                    )
                }
            })

    private fun timePlanFromJson(value: JSONObject): TimePlan {
        val points = value.optJSONArray("points") ?: JSONArray()

        return TimePlan(
            id = value.getString("id"),
            title = value.getString("title"),
            memo = value.optString("memo", ""),
            points = List(points.length()) { index ->
                val point = points.getJSONObject(index)
                TimePoint(
                    id = point.getString("id"),
                    planId = point.getString("planId"),
                    order = point.getInt("order"),
                    name = point.getString("name"),
                    timeMinutes = if (point.isNull("timeMinutes")) null
                    else point.getInt("timeMinutes")
                )
            },
            createdAt = value.getLong("createdAt"),
            updatedAt = value.getLong("updatedAt")
        )
    }

    private fun templateToJson(value: ReportTemplate): JSONObject =
        JSONObject()
            .put("id", value.id)
            .put("name", value.name)
            .put("body", value.body)
            .put("order", value.order)
            .put("isDefault", value.isDefault)

    private fun templateFromJson(value: JSONObject): ReportTemplate =
        ReportTemplate(
            id = value.getString("id"),
            name = value.getString("name"),
            body = value.optString("body", ""),
            order = value.getInt("order"),
            isDefault = value.optBoolean("isDefault", false)
        )
}
