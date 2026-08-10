package com.seolhwa.armyrist.domain

import org.junit.Assert.*
import org.junit.Test

class DomainRulesTest {
    @Test fun quantityValidation() {
        assertEquals(0, DomainRules.parseQuantity("0"))
        assertEquals(25, DomainRules.parseQuantity("25"))
        assertNull(DomainRules.parseQuantity("-1"))
        assertNull(DomainRules.parseQuantity("1.5"))
        assertNull(DomainRules.parseQuantity("ABC"))
        assertNull(DomainRules.parseQuantity(""))
    }

    @Test fun mixedUnitsNeverMerge() {
        val items = listOf(
            CountingItem(sheetId="s", name="a", quantity=10, unit="개", order=0),
            CountingItem(sheetId="s", name="b", quantity=20, unit="개", order=1),
            CountingItem(sheetId="s", name="c", quantity=5, unit="병", order=2),
            CountingItem(sheetId="s", name="d", quantity=7, unit="봉", order=3)
        )
        val result = DomainRules.aggregate(items)
        assertEquals(mapOf("개" to 30, "병" to 5, "봉" to 7), result)
        assertFalse(result.values.contains(62))
    }

    @Test fun subtractionAllowsNegativeCalculatedValue() {
        val result = DomainRules.calculate(mapOf("개" to 10), CalculationOperator.SUBTRACT, mapOf("개" to 20))
        assertEquals(-10, result["개"])
    }

    @Test fun missingUnitIsZero() {
        val result = DomainRules.calculate(
            mapOf("개" to 20, "병" to 5),
            CalculationOperator.SUBTRACT,
            mapOf("개" to 7, "봉" to 3)
        )
        assertEquals(13, result["개"])
        assertEquals(5, result["병"])
        assertEquals(-3, result["봉"])
    }
}
