package com.seolhwa.armyrist.domain

import org.junit.Assert.*
import org.junit.Test

class ResultGeneratorTest {
    @Test
    fun resultContainsCurrentStateAndOmitsEmptySections() {
        val sheetId = "s"
        val group = CountingGroup(
            id = "g",
            sheetId = sheetId,
            name = "A",
            order = 0
        )
        val sheet = CountingSheet(
            id = sheetId,
            title = "창고 실셈",
            groups = listOf(group),
            items = listOf(
                CountingItem(
                    sheetId = sheetId,
                    name = "탄약상자",
                    quantity = 11,
                    unit = "개",
                    groupId = "g",
                    order = 0
                ),
                CountingItem(
                    sheetId = sheetId,
                    name = "물",
                    quantity = 4,
                    unit = "병",
                    groupId = "g",
                    order = 1
                )
            )
        )

        val result = ResultGenerator.generate(sheet)

        assertTrue(result.startsWith("창고 실셈"))
        assertFalse(result.contains("[창고 실셈]"))
        assertTrue(result.contains("[A]"))
        assertTrue(result.contains("- 탄약상자 : 11개"))
        assertTrue(result.contains("- 물 : 4병"))
        assertTrue(result.contains("- 합계 : 11개 / 4병"))
        assertFalse(result.contains("[메모]"))
        assertFalse(result.contains("[계산]"))
        assertFalse(result.contains("[미지정]"))
    }

    @Test
    fun aggregateCanBeHiddenWithoutRemovingItems() {
        val sheetId = "s"
        val group = CountingGroup(
            id = "g",
            sheetId = sheetId,
            name = "A",
            order = 0,
            showAggregate = false
        )
        val sheet = CountingSheet(
            id = sheetId,
            title = "인원 현황",
            groups = listOf(group),
            items = listOf(
                CountingItem(
                    sheetId = sheetId,
                    name = "휴가",
                    quantity = 3,
                    unit = "명",
                    groupId = "g",
                    order = 0
                )
            )
        )

        val result = ResultGenerator.generate(sheet)

        assertTrue(result.contains("- 휴가 : 3명"))
        assertFalse(result.contains("합계"))
    }
}
