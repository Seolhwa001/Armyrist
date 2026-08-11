package com.seolhwa.armyrist.stage2.domain

object ChecklistResultGenerator {
    fun generate(checklist: Checklist): ToolResult {
        val blocks = mutableListOf<String>()
        val orderedGroups = checklist.groups.sortedBy { it.order }

        blocks += "[${checklist.title}]"

        for (group in orderedGroups) {
            val groupItems = checklist.items
                .filter { it.groupId == group.id }
                .sortedBy { it.order }

            if (groupItems.isNotEmpty()) {
                blocks += groupBlock(group.name, groupItems)
            }
        }

        val ungrouped = checklist.items
            .filter { it.groupId == null }
            .sortedBy { it.order }

        if (ungrouped.isNotEmpty()) {
            blocks += groupBlock("미지정", ungrouped)
        }

        val progress = ChecklistRules.progress(checklist.items)
        val progressText = progress.completionPercent?.let { "$it%" } ?: "진행 대상 없음"

        blocks += buildString {
            appendLine("[전체 현황]")
            appendLine("완료: ${progress.completeItems}")
            appendLine("미완료: ${progress.incompleteItems}")
            appendLine("해당 없음: ${progress.notApplicableItems}")
            append("진행률: $progressText")
        }

        if (checklist.memo.isNotBlank()) {
            blocks += "[메모]\n${checklist.memo.trim()}"
        }

        return ToolResult(
            title = checklist.title,
            body = blocks.joinToString("\n\n").trim()
        )
    }

    private fun groupBlock(name: String, items: List<ChecklistItem>): String {
        val progress = ChecklistRules.progress(items)
        val lines = mutableListOf<String>()

        lines += "[$name]"
        lines += "완료 ${progress.completeItems} / 미완료 ${progress.incompleteItems} / 해당 없음 ${progress.notApplicableItems}"

        val sections = listOf(
            ChecklistStatus.COMPLETE to "완료",
            ChecklistStatus.INCOMPLETE to "미완료",
            ChecklistStatus.NOT_APPLICABLE to "해당 없음"
        )

        for ((status, label) in sections) {
            val matching = items.filter { it.status == status }
            if (matching.isEmpty()) continue

            lines += ""
            lines += "[$label]"
            for (item in matching) {
                lines += item.name
                if (item.note.isNotBlank()) {
                    lines += "  비고: ${item.note.trim()}"
                }
            }
        }

        return lines.joinToString("\n")
    }
}

object TimePlanResultGenerator {
    fun generate(plan: TimePlan): ToolResult {
        val ordered = plan.points.sortedBy { it.order }

        val lines = mutableListOf("[${plan.title}]")
        for (point in ordered) {
            val time = point.timeMinutes ?: continue
            lines += "${TimePlanRules.formatShareClock(time)} ${point.name}"
        }

        if (plan.memo.isNotBlank()) {
            lines += ""
            lines += "[메모]"
            lines += plan.memo.trim()
        }

        return ToolResult(
            title = plan.title,
            body = lines.joinToString("\n").trim()
        )
    }
}
