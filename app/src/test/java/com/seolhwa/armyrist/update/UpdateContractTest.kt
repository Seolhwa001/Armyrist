package com.seolhwa.armyrist.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateContractTest {
    @Test
    fun displayVersionRemovesInternalSuffix() {
        assertEquals(
            "0.6.25",
            InstalledVersion("0.6.25-legacy-resurrection-source-fix", 113).displayName
        )
        assertEquals("0.6.26", InstalledVersion("0.6.26", 114).displayName)
    }

    @Test
    fun configuredIntervalsMatchContract() {
        assertEquals(null, UpdateCheckInterval.DISABLED.intervalMillis)
        assertEquals(12L * 60L * 60L * 1000L, UpdateCheckInterval.HOURS_12.intervalMillis)
        assertEquals(24L * 60L * 60L * 1000L, UpdateCheckInterval.HOURS_24.intervalMillis)
        assertEquals(3L * 24L * 60L * 60L * 1000L, UpdateCheckInterval.DAYS_3.intervalMillis)
        assertEquals(7L * 24L * 60L * 60L * 1000L, UpdateCheckInterval.DAYS_7.intervalMillis)
    }
}
