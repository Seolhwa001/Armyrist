package com.seolhwa.armyrist.stage2.domain

import java.util.UUID

data class ToolResult(
    val title: String,
    val body: String
)

enum class ChecklistStatus {
    INCOMPLETE,
    COMPLETE,
    NOT_APPLICABLE
}

data class Checklist(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "새 체크리스트",
    val memo: String = "",
    val groups: List<ChecklistGroup> = emptyList(),
    val items: List<ChecklistItem> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ChecklistGroup(
    val id: String = UUID.randomUUID().toString(),
    val checklistId: String,
    val name: String,
    val order: Int
)

data class ChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    val checklistId: String,
    val groupId: String? = null,
    val order: Int,
    val name: String,
    val status: ChecklistStatus = ChecklistStatus.INCOMPLETE,
    val note: String = ""
)

data class TimePlan(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "새 시간계획",
    val points: List<TimePoint>,
    val memo: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class TimePoint(
    val id: String = UUID.randomUUID().toString(),
    val planId: String,
    val order: Int,
    val name: String,
    val timeMinutes: Int?
)

data class UserProfile(
    val displayName: String = ""
)

data class ReportTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val body: String = "",
    val order: Int,
    val isDefault: Boolean = false
)

data class ChecklistProgress(
    val totalItems: Int,
    val completeItems: Int,
    val incompleteItems: Int,
    val notApplicableItems: Int,
    val effectiveItems: Int,
    val completionPercent: Int?
)

object ChecklistRules {
    fun progress(items: List<ChecklistItem>): ChecklistProgress {
        val total = items.size
        val complete = items.count { it.status == ChecklistStatus.COMPLETE }
        val incomplete = items.count { it.status == ChecklistStatus.INCOMPLETE }
        val notApplicable = items.count { it.status == ChecklistStatus.NOT_APPLICABLE }
        val effective = total - notApplicable

        val percent = if (effective > 0) {
            ((complete.toDouble() / effective.toDouble()) * 100.0).toInt()
        } else {
            null
        }

        return ChecklistProgress(
            totalItems = total,
            completeItems = complete,
            incompleteItems = incomplete,
            notApplicableItems = notApplicable,
            effectiveItems = effective,
            completionPercent = percent
        )
    }

    fun resetStatuses(items: List<ChecklistItem>): List<ChecklistItem> =
        items.map { it.copy(status = ChecklistStatus.INCOMPLETE) }
}
