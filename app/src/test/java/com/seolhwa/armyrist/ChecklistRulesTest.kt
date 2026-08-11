package com.seolhwa.armyrist.stage2.domain

import org.junit.Assert.*
import org.junit.Test

class ChecklistRulesTest {
    private fun item(
        id: String,
        status: ChecklistStatus
    ) = ChecklistItem(
        id = id,
        checklistId = "c",
        order = id.last().digitToIntOrNull() ?: 0,
        name = id,
        status = status
    )

    @Test
    fun notApplicableExcludedFromDenominator() {
        val progress = ChecklistRules.progress(
            listOf(
                item("i1", ChecklistStatus.COMPLETE),
                item("i2", ChecklistStatus.INCOMPLETE),
                item("i3", ChecklistStatus.NOT_APPLICABLE)
            )
        )

        assertEquals(2, progress.effectiveItems)
        assertEquals(50, progress.completionPercent)
    }

    @Test
    fun allNotApplicableHasNoNumericRate() {
        val progress = ChecklistRules.progress(
            listOf(
                item("i1", ChecklistStatus.NOT_APPLICABLE),
                item("i2", ChecklistStatus.NOT_APPLICABLE)
            )
        )

        assertEquals(0, progress.effectiveItems)
        assertNull(progress.completionPercent)
    }

    @Test
    fun resetChangesOnlyStatus() {
        val original = listOf(
            item("i1", ChecklistStatus.COMPLETE).copy(note = "유지"),
            item("i2", ChecklistStatus.NOT_APPLICABLE),
            item("i3", ChecklistStatus.INCOMPLETE)
        )

        val reset = ChecklistRules.resetStatuses(original)

        assertTrue(reset.all { it.status == ChecklistStatus.INCOMPLETE })
        assertEquals("유지", reset[0].note)
        assertEquals(original.map { it.id }, reset.map { it.id })
    }
}
