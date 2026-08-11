package com.seolhwa.armyrist.domain

object ResultGenerator {
    fun generate(sheet: CountingSheet): String {
        val blocks = mutableListOf<String>()

        blocks += sheet.title.trim()

        val ungrouped =
            sheet.items
                .filter { it.groupId == null }
                .sortedBy { it.order }

        if (ungrouped.isNotEmpty()) {
            blocks += itemLines(ungrouped).joinToString("\n")
        }

        sheet.groups.sortedBy { it.order }.forEach { group ->
            val items =
                sheet.items
                    .filter { it.groupId == group.id }
                    .sortedBy { it.order }

            if (items.isEmpty()) return@forEach

            val lines = mutableListOf<String>()
            lines += "[${group.name}]"
            lines += itemLines(items)

            if (group.showAggregate) {
                lines += aggregateLines(
                    values = aggregateInFirstSeenUnitOrder(items),
                    singlePrefix = "- 합계 : "
                )
            }

            blocks += lines.joinToString("\n")
        }

        if (sheet.calculations.isNotEmpty()) {
            val lines = mutableListOf("[계산]")

            sheet.calculations.forEach { calculation ->
                val leftGroup =
                    sheet.groups.firstOrNull {
                        it.id == calculation.leftGroupId
                    } ?: return@forEach

                val rightGroup =
                    sheet.groups.firstOrNull {
                        it.id == calculation.rightGroupId
                    } ?: return@forEach

                val leftItems =
                    sheet.items
                        .filter { it.groupId == leftGroup.id }
                        .sortedBy { it.order }

                val rightItems =
                    sheet.items
                        .filter { it.groupId == rightGroup.id }
                        .sortedBy { it.order }

                val left = DomainRules.aggregate(leftItems)
                val right = DomainRules.aggregate(rightItems)
                val calculated =
                    DomainRules.calculate(
                        left,
                        calculation.operator,
                        right
                    )

                val unitOrder = linkedSetOf<String>()
                leftItems.forEach { unitOrder += it.unit }
                rightItems.forEach { unitOrder += it.unit }
                calculated.keys.forEach { unitOrder += it }

                val orderedValues =
                    unitOrder.mapNotNull { unit ->
                        calculated[unit]?.let { unit to it }
                    }

                val symbol =
                    when (calculation.operator) {
                        CalculationOperator.ADD -> "+"
                        CalculationOperator.SUBTRACT -> "-"
                    }

                val label =
                    calculation.name.trim().ifEmpty {
                        "${leftGroup.name} $symbol ${rightGroup.name}"
                    }

                lines += calculationLines(label, orderedValues)
            }

            if (lines.size > 1) {
                blocks += lines.joinToString("\n")
            }
        }

        val memo = sheet.memo.trim()
        if (memo.isNotEmpty()) {
            blocks += "[메모]\n$memo"
        }

        return blocks
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
            .trim()
    }

    private fun itemLines(items: List<CountingItem>): List<String> =
        items.flatMap(::formatItem)

    private fun formatItem(item: CountingItem): List<String> {
        val base = "- ${item.name} : ${item.quantity}${item.unit}"
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

    private fun aggregateInFirstSeenUnitOrder(
        items: List<CountingItem>
    ): List<Pair<String, Int>> {
        val totals = linkedMapOf<String, Int>()

        items.sortedBy { it.order }.forEach { item ->
            totals[item.unit] =
                (totals[item.unit] ?: 0) + item.quantity
        }

        return totals.toList()
    }

    private fun aggregateLines(
        values: List<Pair<String, Int>>,
        singlePrefix: String
    ): List<String> {
        if (values.isEmpty()) return emptyList()

        if (values.size == 1) {
            val (unit, quantity) = values.first()
            return listOf("$singlePrefix$quantity$unit")
        }

        val inline =
            values.joinToString(" / ") { (unit, quantity) ->
                "$quantity$unit"
            }

        val fullInline = "$singlePrefix$inline"

        if (values.size <= 3 && fullInline.length <= 60) {
            return listOf(fullInline)
        }

        return buildList {
            add("[합계]")
            values.forEach { (unit, quantity) ->
                add("- $unit : $quantity")
            }
        }
    }

    private fun calculationLines(
        label: String,
        values: List<Pair<String, Int>>
    ): List<String> {
        if (values.isEmpty()) {
            return listOf("- $label")
        }

        if (values.size == 1) {
            val (unit, quantity) = values.first()
            return listOf("- $label : $quantity$unit")
        }

        val inline =
            values.joinToString(" / ") { (unit, quantity) ->
                "$quantity$unit"
            }

        val candidate = "- $label : $inline"

        if (values.size <= 3 && candidate.length <= 60) {
            return listOf(candidate)
        }

        return buildList {
            add("- $label")
            values.forEach { (unit, quantity) ->
                add("  - $unit : $quantity")
            }
        }
    }
}
