package com.seolhwa.armyrist.domain

import org.junit.Assert.*
import org.junit.Test

class ResultGeneratorTest {
    @Test fun resultContainsCurrentStateAndOmitsEmptySections() {
        val sheetId = "s"
        val group = CountingGroup(id="g", sheetId=sheetId, name="A", order=0)
        val sheet = CountingSheet(
            id=sheetId,
            title="창고 실셈",
            groups=listOf(group),
            items=listOf(
                CountingItem(sheetId=sheetId, name="탄약상자", quantity=11, unit="개", groupId="g", order=0),
                CountingItem(sheetId=sheetId, name="물", quantity=4, unit="병", groupId="g", order=1)
            )
        )
        val result = ResultGenerator.generate(sheet)
        assertTrue(result.contains("[창고 실셈]"))
        assertTrue(result.contains("[A]"))
        assertTrue(result.contains("탄약상자 : 11 개"))
        assertTrue(result.contains("- 개 : 11"))
        assertTrue(result.contains("- 병 : 4"))
        assertFalse(result.contains("[메모]"))
        assertFalse(result.contains("[계산]"))
        assertFalse(result.contains("[미지정]"))
    }
}
