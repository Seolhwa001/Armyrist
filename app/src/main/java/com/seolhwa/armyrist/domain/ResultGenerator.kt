package com.seolhwa.armyrist.domain

object ResultGenerator {
    fun generate(sheet: CountingSheet): String {
        val blocks = mutableListOf<String>()
        blocks += "[${sheet.title}]"

        val orderedGroups = sheet.groups.sortedBy { it.order }
        for (group in orderedGroups) {
            val items = sheet.items.filter { it.groupId == group.id }.sortedBy { it.order }
            blocks += groupBlock(group.name, items)
        }

        val ungrouped = sheet.items.filter { it.groupId == null }.sortedBy { it.order }
        if (ungrouped.isNotEmpty()) {
            blocks += groupBlock("미지정", ungrouped)
        }

        if (sheet.calculations.isNotEmpty()) {
            val lines = mutableListOf("[계산]")
            for (calc in sheet.calculations) {
                val leftGroup = sheet.groups.firstOrNull { it.id == calc.leftGroupId } ?: continue
                val rightGroup = sheet.groups.firstOrNull { it.id == calc.rightGroupId } ?: continue
                val left = DomainRules.aggregate(sheet.items.filter { it.groupId == leftGroup.id })
                val right = DomainRules.aggregate(sheet.items.filter { it.groupId == rightGroup.id })
                val result = DomainRules.calculate(left, calc.operator, right)
                val symbol = if (calc.operator == CalculationOperator.ADD) "+" else "-"
                val label = calc.name.trim().ifEmpty { "${leftGroup.name} $symbol ${rightGroup.name}" }
                lines += label
                result.forEach { (unit, quantity) -> lines += "- $unit : $quantity" }
            }
            if (lines.size > 1) blocks += lines.joinToString("\n")
        }

        if (sheet.memo.isNotBlank()) {
            blocks += "[메모]\n${sheet.memo.trim()}"
        }

        return blocks.filter { it.isNotBlank() }.joinToString("\n\n").trim()
    }

    private fun groupBlock(name: String, items: List<CountingItem>): String {
        val lines = mutableListOf("[$name]")
        items.forEach { item ->
            lines += "${item.name} : ${item.quantity} ${item.unit}"
            if (item.note.isNotBlank()) lines += "  비고: ${item.note.trim()}"
        }
        if (items.isNotEmpty()) {
            lines += "합계"
            DomainRules.aggregate(items).forEach { (unit, quantity) ->
                lines += "- $unit : $quantity"
            }
        }
        return lines.joinToString("\n")
    }
}
