package com.seolhwa.armyrist.stage2.domain

object ChecklistResultGenerator {
    fun generate(checklist: Checklist): ToolResult {
        val blocks = mutableListOf<String>()

        val ungrouped =
            checklist.items
                .filter { it.groupId == null }
                .sortedBy { it.order }

        if (ungrouped.isNotEmpty()) {
            blocks +=
                ungrouped
                    .flatMap(::formatChecklistItem)
                    .joinToString("\n")
        }

        checklist.groups.sortedBy { it.order }.forEach { group ->
            val items =
                checklist.items
                    .filter { it.groupId == group.id }
                    .sortedBy { it.order }

            if (items.isEmpty()) return@forEach

            val lines = mutableListOf("[${group.name}]")
            lines += items.flatMap(::formatChecklistItem)
            blocks += lines.joinToString("\n")
        }

        val progress = ChecklistRules.progress(checklist.items)

        blocks += buildString {
            appendLine("완료 : ${progress.completeItems}")
            appendLine("미완료 : ${progress.incompleteItems}")
            append("해당 없음 : ${progress.notApplicableItems}")
        }

        val memo = checklist.memo.trim()
        if (memo.isNotEmpty()) {
            blocks += "[메모]\n$memo"
        }

        return ToolResult(
            title = checklist.title,
            body =
                blocks
                    .filter { it.isNotBlank() }
                    .joinToString("\n\n")
                    .trim()
        )
    }

    private fun formatChecklistItem(
        item: ChecklistItem
    ): List<String> {
        val statusText =
            when (item.status) {
                ChecklistStatus.INCOMPLETE -> "미완료"
                ChecklistStatus.COMPLETE -> "완료"
                ChecklistStatus.NOT_APPLICABLE -> "해당 없음"
            }

        val timeSuffix =
            item.scheduledTimeMinutes?.let {
                " — ${formatChecklistTime(it)}"
            } ?: ""

        val base =
            "- $statusText : ${item.name}$timeSuffix"

        val note = item.note.trim()
        if (note.isEmpty()) return listOf(base)

        val hasLineBreak =
            note.contains('\n') || note.contains('\r')

        if (!hasLineBreak && note.length <= 30) {
            return listOf("$base ($note)")
        }

        val noteLines =
            note.replace("\r\n", "\n")
                .replace('\r', '\n')
                .split('\n')
                .map { "  $it" }

        return listOf(base) + noteLines
    }

    private fun formatChecklistTime(minutes: Int): String =
        "%02d:%02d".format(minutes / 60, minutes % 60)
}

object TimePlanResultGenerator {
    fun generate(plan: TimePlan): ToolResult {
        val ordered = plan.points.sortedBy { it.order }
        val lines = mutableListOf<String>()

        ordered.forEach { point ->
            val time = point.timeMinutes ?: return@forEach
            lines +=
                "${TimePlanRules.formatShareClock(time)} ${point.name}"
        }

        val memo = plan.memo.trim()
        if (memo.isNotEmpty()) {
            lines += ""
            lines += "[메모]"
            lines += memo
        }

        return ToolResult(
            title = plan.title,
            body = lines.joinToString("\n").trim()
        )
    }
}
