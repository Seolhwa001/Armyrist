package com.seolhwa.armyrist.timeplan.v3.domain

import com.seolhwa.armyrist.stage2.domain.ToolResult
import java.time.format.DateTimeFormatter

object TimePlanExecutionResultGenerator {
    private val clock = DateTimeFormatter.ofPattern("MM.dd HH:mm")

    fun compact(plan: DateAwareTimePlan): ToolResult {
        val entries=UnifiedTimePlan.entries(plan)
        val all=entries.map{it.value}
        val summary=TimePlanExecutionRules.summary(plan.copy(midwayEvents=emptyList(),finalPoint=null,links=emptyList(),actions=all))
        val incomplete = all
            .filter { it.completionState == ActionCompletionState.INCOMPLETE }
            .sortedBy { it.scheduledDateTime }

        val lines = mutableListOf<String>()
        lines += "실시사항 ${summary.total}건"
        lines += "완료 ${summary.completed}건 / 미실시 ${summary.incomplete}건"

        if (incomplete.isNotEmpty()) {
            lines += ""
            lines += "[미실시]"
            incomplete.forEach { action ->
                lines += "- ${actionLabel(action,entries)} ${action.content}"
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
        val entries=UnifiedTimePlan.entries(plan)
        val all=entries.map{it.value}
        val summary=TimePlanExecutionRules.summary(plan.copy(midwayEvents=emptyList(),finalPoint=null,links=emptyList(),actions=all))
        val lines = mutableListOf<String>()
        all.sortedBy { it.scheduledDateTime }.forEach { action ->
            val status = if(action.completionState==ActionCompletionState.COMPLETE) "완료" else "미실시"
            lines += "- ${actionLabel(action,entries)} ${action.content} : $status"
            action.note?.takeIf { it.isNotBlank() }?.let { lines += "  · $it" }
        }
        lines += ""

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

    private fun actionLabel(action:TimePlanActionItem,entries:List<UnifiedTimePlanEntry>):String {
        if(entries.firstOrNull{it.value.id==action.id}?.untimed==true)return "시각 미설정"
        val range=action.durationMinutes?.takeIf{it>0}?.let{"~"+action.scheduledDateTime.plusMinutes(it).format(clock)}.orEmpty()
        return action.scheduledDateTime.format(clock)+range
    }

    private fun groupLines(plan: DateAwareTimePlan): List<String>? {
        if (plan.actionGroups.isEmpty()) return null
        val lines = mutableListOf<String>()
        plan.actionGroups.sortedBy { it.order }.forEach { group ->
            val actions = UnifiedTimePlan.actions(plan).filter { it.groupId == group.id }
            if (actions.isEmpty()) return@forEach
            val complete = actions.count { it.completionState == ActionCompletionState.COMPLETE }
            lines += "- ${group.name} $complete / ${actions.size}"
        }
        return lines.takeIf { it.isNotEmpty() }
    }
}
