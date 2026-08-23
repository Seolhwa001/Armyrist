package com.seolhwa.armyrist.timeplan.v3.domain

import com.seolhwa.armyrist.stage2.domain.ToolResult
import java.time.format.DateTimeFormatter

object TimePlanExecutionResultGenerator {
    private val clock = DateTimeFormatter.ofPattern("MM.dd HH:mm")

    fun compact(plan: DateAwareTimePlan): ToolResult {
        val summary = TimePlanExecutionRules.summary(plan)
        val incomplete = plan.actions
            .filter { it.completionState == ActionCompletionState.INCOMPLETE }
            .sortedBy { it.scheduledDateTime }

        val lines = mutableListOf<String>()
        lines += "실시사항 ${summary.total}건"
        lines += "완료 ${summary.completed}건 / 미실시 ${summary.incomplete}건"

        if (incomplete.isNotEmpty()) {
            lines += ""
            lines += "[미실시]"
            incomplete.forEach { action ->
                lines += "- ${action.scheduledDateTime.format(clock)} ${action.content}"
                action.note?.takeIf { it.isNotBlank() }?.let { lines += "  · $it" }
            }
        }

        groupLines(plan)?.let {
            lines += ""
            lines += "[그룹별]"
            lines += it
        }

        plan.memo?.takeIf { it.isNotBlank() }?.let {
            lines += ""
            lines += "[특이사항]"
            lines += it
        } ?: run {
            lines += ""
            lines += "특이사항 없음."
        }

        return ToolResult(
            title = "${plan.title} 수행 결과",
            body = lines.joinToString("\n").trim()
        )
    }

    fun detailed(plan: DateAwareTimePlan): ToolResult {
        val summary = TimePlanExecutionRules.summary(plan)
        val lines = mutableListOf<String>()
        val nodeIds = DateTimePlanRules.nodeIds(plan)

        nodeIds.forEach { nodeId ->
            val pointActions = TimePlanExecutionRules.actionsForPoint(plan, nodeId)
            if (pointActions.isEmpty()) return@forEach
            val pointTime = pointTimeLabel(plan, nodeId)
            lines += "■ $pointTime ${TimePlanExecutionRules.pointName(plan, nodeId)}"
            pointActions.forEach { action ->
                val status = if (action.completionState == ActionCompletionState.COMPLETE) "완료" else "미실시"
                lines += "- ${action.scheduledDateTime.format(clock)} ${action.content} : $status"
                action.note?.takeIf { it.isNotBlank() }?.let { lines += "  · $it" }
            }
            lines += ""
        }

        lines += "[수행 현황]"
        lines += "전체 ${summary.total}건"
        lines += "완료 ${summary.completed}건"
        lines += "미실시 ${summary.incomplete}건"
        lines += "완료율 ${summary.completionRate?.let { "$it%" } ?: "-"}"

        groupLines(plan)?.let {
            lines += ""
            lines += "[그룹별]"
            lines += it
        }

        plan.memo?.takeIf { it.isNotBlank() }?.let {
            lines += ""
            lines += "[특이사항]"
            lines += it
        }

        return ToolResult(
            title = "${plan.title} 수행 결과",
            body = lines.joinToString("\n").trim()
        )
    }

    private fun pointTimeLabel(plan: DateAwareTimePlan, nodeId: String): String {
        if (nodeId == DateTimePlanRules.START_ID || nodeId == DateTimePlanRules.END_ID) {
            return TimePlanExecutionRules.pointDateTime(plan, nodeId)?.format(clock) ?: "--.-- --:--"
        }
        val event = plan.orderedEvents().firstOrNull { it.id == nodeId }
            ?: return "--.-- --:--"
        return when (val spec = event.timeSpec) {
            EventDateTimeSpec.Unspecified -> "--.-- --:--"
            is EventDateTimeSpec.Single -> spec.value.value?.format(clock) ?: "--.-- --:--"
            is EventDateTimeSpec.Range -> {
                val start = spec.start.value
                val end = spec.end.value
                when {
                    start != null && end != null ->
                        "${start.format(clock)}~${end.format(DateTimeFormatter.ofPattern("HH:mm"))}"
                    start != null -> start.format(clock)
                    end != null -> end.format(clock)
                    else -> "--.-- --:--"
                }
            }
        }
    }

    private fun groupLines(plan: DateAwareTimePlan): List<String>? {
        if (plan.actionGroups.isEmpty()) return null
        val lines = mutableListOf<String>()
        plan.actionGroups.sortedBy { it.order }.forEach { group ->
            val actions = plan.actions.filter { it.groupId == group.id }
            if (actions.isEmpty()) return@forEach
            val complete = actions.count { it.completionState == ActionCompletionState.COMPLETE }
            lines += "- ${group.name} $complete / ${actions.size}"
        }
        return lines.takeIf { it.isNotEmpty() }
    }
}
