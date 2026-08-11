package com.seolhwa.armyrist.domain

import java.util.UUID

data class CountingSheet(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "새 실셈",
    val memo: String = "",
    val items: List<CountingItem> = emptyList(),
    val groups: List<CountingGroup> = emptyList(),
    val calculations: List<GroupCalculation> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class CountingItem(
    val id: String = UUID.randomUUID().toString(),
    val sheetId: String,
    val name: String,
    val quantity: Int,
    val unit: String,
    val note: String = "",
    val groupId: String? = null,
    val order: Int
)

data class CountingGroup(
    val id: String = UUID.randomUUID().toString(),
    val sheetId: String,
    val name: String,
    val order: Int,
    val color: String = "#6750A4",
    val showAggregate: Boolean = true
)

enum class CalculationOperator { ADD, SUBTRACT }

data class GroupCalculation(
    val id: String = UUID.randomUUID().toString(),
    val sheetId: String,
    val leftGroupId: String,
    val operator: CalculationOperator,
    val rightGroupId: String,
    val name: String = ""
)

data class UnitTotal(val unit: String, val quantity: Int)

object DomainRules {
    fun normalizeRequired(value: String): String? =
        value.trim().takeIf { it.isNotEmpty() }

    fun parseQuantity(raw: String): Int? {
        if (raw.isBlank()) return null
        if (!raw.all { it.isDigit() }) return null
        return raw.toIntOrNull()?.takeIf { it >= 0 }
    }

    fun aggregate(items: List<CountingItem>): Map<String, Int> =
        items.groupBy { it.unit }
            .mapValues { (_, grouped) -> grouped.sumOf { it.quantity } }
            .toSortedMap()

    fun calculate(
        left: Map<String, Int>,
        operator: CalculationOperator,
        right: Map<String, Int>
    ): Map<String, Int> {
        val units = (left.keys + right.keys).toSortedSet()
        return units.associateWith { unit ->
            val l = left[unit] ?: 0
            val r = right[unit] ?: 0
            when (operator) {
                CalculationOperator.ADD -> l + r
                CalculationOperator.SUBTRACT -> l - r
            }
        }
    }
}
