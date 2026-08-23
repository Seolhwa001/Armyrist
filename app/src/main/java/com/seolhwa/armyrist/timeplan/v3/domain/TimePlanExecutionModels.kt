package com.seolhwa.armyrist.timeplan.v3.domain

import java.time.LocalDateTime
import java.util.UUID

enum class ActionCompletionState { INCOMPLETE, COMPLETE }

data class TimePlanActionGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val order: Int,
    val color: String = "#7A7D61"
) {
    init {
        require(id.isNotBlank())
        require(name.isNotBlank())
        require(order >= 0)
    }
}

data class TimePlanActionItem(
    val id: String = UUID.randomUUID().toString(),
    val parentPointId: String,
    val content: String,
    val scheduledDateTime: LocalDateTime,
    val completionState: ActionCompletionState = ActionCompletionState.INCOMPLETE,
    val notificationEnabled: Boolean = false,
    val groupId: String? = null,
    val note: String? = null,
    val order: Int = 0,
    val createdAt: String = System.currentTimeMillis().toString(),
    val updatedAt: String = System.currentTimeMillis().toString()
) {
    init {
        require(id.isNotBlank())
        require(parentPointId.isNotBlank())
        require(content.isNotBlank())
        require(order >= 0)
    }
}

data class TimePlanExecutionSummary(
    val total: Int,
    val completed: Int,
    val incomplete: Int,
    val completionRate: Int?
)

object TimePlanExecutionRules {
    fun pointName(plan: DateAwareTimePlan, nodeId: String): String = when (nodeId) {
        DateTimePlanRules.START_ID -> "시작"
        DateTimePlanRules.END_ID -> "종료"
        else -> plan.orderedEvents().firstOrNull { it.id == nodeId }?.name ?: "지점"
    }

    fun pointDateTime(plan: DateAwareTimePlan, nodeId: String): LocalDateTime? =
        DateTimePlanRules.nodeArrival(plan, nodeId)

    fun actionsForPoint(plan: DateAwareTimePlan, nodeId: String): List<TimePlanActionItem> =
        plan.actions.filter { it.parentPointId == nodeId }
            .sortedWith(compareBy<TimePlanActionItem> { it.scheduledDateTime }.thenBy { it.order })

    fun summary(plan: DateAwareTimePlan): TimePlanExecutionSummary {
        val total = plan.actions.size
        val completed = plan.actions.count { it.completionState == ActionCompletionState.COMPLETE }
        val incomplete = total - completed
        val rate = if (total == 0) null else ((completed.toDouble() / total.toDouble()) * 100.0).toInt()
        return TimePlanExecutionSummary(total, completed, incomplete, rate)
    }

    fun shiftActionsForParent(
        plan: DateAwareTimePlan,
        parentPointId: String,
        deltaMinutes: Long
    ): DateAwareTimePlan = plan.copy(
        actions = plan.actions.map { action ->
            if (action.parentPointId == parentPointId) {
                action.copy(
                    scheduledDateTime = action.scheduledDateTime.plusMinutes(deltaMinutes),
                    updatedAt = System.currentTimeMillis().toString()
                )
            } else action
        }
    )

    fun batchShift(
        plan: DateAwareTimePlan,
        actionIds: Set<String>,
        deltaMinutes: Long
    ): DateAwareTimePlan = plan.copy(
        actions = plan.actions.map { action ->
            if (action.id in actionIds) {
                action.copy(
                    scheduledDateTime = action.scheduledDateTime.plusMinutes(deltaMinutes),
                    updatedAt = System.currentTimeMillis().toString()
                )
            } else action
        }
    )

    fun batchAssignGroup(
        plan: DateAwareTimePlan,
        actionIds: Set<String>,
        groupId: String?
    ): DateAwareTimePlan = plan.copy(
        actions = plan.actions.map { action ->
            if (action.id in actionIds) {
                action.copy(groupId = groupId, updatedAt = System.currentTimeMillis().toString())
            } else action
        }
    )

    fun batchDelete(plan: DateAwareTimePlan, actionIds: Set<String>): DateAwareTimePlan =
        normalizeActionOrder(plan.copy(actions = plan.actions.filterNot { it.id in actionIds }))

    fun removePointActions(plan: DateAwareTimePlan, parentPointId: String): DateAwareTimePlan =
        normalizeActionOrder(plan.copy(actions = plan.actions.filterNot { it.parentPointId == parentPointId }))

    fun normalizeActionOrder(plan: DateAwareTimePlan): DateAwareTimePlan {
        val byParent = plan.actions.groupBy { it.parentPointId }
        val normalized = byParent.values.flatMap { actions ->
            actions.sortedWith(compareBy<TimePlanActionItem> { it.scheduledDateTime }.thenBy { it.order })
                .mapIndexed { index, action -> action.copy(order = index) }
        }
        return plan.copy(actions = normalized)
    }

    fun validate(plan: DateAwareTimePlan): List<String> {
        val problems = mutableListOf<String>()
        val nodeIds = DateTimePlanRules.nodeIds(plan).toSet()
        val groupIds = plan.actionGroups.map { it.id }.toSet()
        if (plan.actionGroups.map { it.id }.distinct().size != plan.actionGroups.size) problems += "duplicate action group id"
        if (plan.actions.map { it.id }.distinct().size != plan.actions.size) problems += "duplicate action id"
        if (plan.actions.any { it.parentPointId !in nodeIds }) problems += "orphan action parent"
        if (plan.actions.any { it.groupId != null && it.groupId !in groupIds }) problems += "orphan action group"
        if (plan.actions.any { it.content.isBlank() }) problems += "blank action content"
        return problems
    }
}
