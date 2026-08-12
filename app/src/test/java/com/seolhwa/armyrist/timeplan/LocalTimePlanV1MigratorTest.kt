package com.seolhwa.armyrist.timeplan.migration

import com.seolhwa.armyrist.stage2.domain.TimePlan
import com.seolhwa.armyrist.stage2.domain.TimePoint
import com.seolhwa.armyrist.timeplan.domain.EventTimeSpec
import com.seolhwa.armyrist.timeplan.domain.TimeEventKind
import com.seolhwa.armyrist.timeplan.domain.ValueOrigin
import org.junit.Assert.*
import org.junit.Test

class LocalTimePlanV1MigratorTest {
    private fun legacy(times: List<Int?>): TimePlan {
        val id = "p"
        return TimePlan(
            id = id,
            title = "기존 계획",
            points = times.mapIndexed { index, time ->
                TimePoint(
                    id = "pt$index",
                    planId = id,
                    order = index,
                    name = when (index) {
                        0 -> "시작"
                        times.lastIndex -> "종료"
                        else -> "중도 $index"
                    },
                    timeMinutes = time
                )
            },
            memo = "memo",
            createdAt = 10L,
            updatedAt = 20L
        )
    }

    @Test fun startMiddleEndMigrateWithoutFinal() {
        val result = LocalTimePlanV1Migrator.migrate(
            legacy(listOf(540, 580, 660))
        ) as LocalTimePlanV1Migrator.Result.Success
        assertEquals(1, result.value.midwayEvents.size)
        assertEquals(TimeEventKind.MIDWAY, result.value.midwayEvents[0].kind)
        assertNull(result.value.finalPoint)
    }

    @Test fun legacyClockOriginIsConservativelyExplicit() {
        val result = LocalTimePlanV1Migrator.migrate(
            legacy(listOf(540, 580, 660))
        ) as LocalTimePlanV1Migrator.Result.Success
        val middle = result.value.midwayEvents.first().timeSpec
            as EventTimeSpec.Single
        assertEquals(ValueOrigin.EXPLICIT, result.value.start.value.origin)
        assertEquals(ValueOrigin.EXPLICIT, middle.value.origin)
        assertEquals(ValueOrigin.EXPLICIT, result.value.end.value.origin)
    }

    @Test fun adjacentDurationIsDerivedFromActualClockDifference() {
        val result = LocalTimePlanV1Migrator.migrate(
            legacy(listOf(540, 580, 660))
        ) as LocalTimePlanV1Migrator.Result.Success
        assertEquals(listOf(40, 80), result.value.links.map { it.duration?.minutes })
        assertTrue(result.value.links.all { it.origin == ValueOrigin.DERIVED })
    }

    @Test fun missingClockRemainsUnsetAndIsNotGuessed() {
        val result = LocalTimePlanV1Migrator.migrate(
            legacy(listOf(540, null, 660))
        ) as LocalTimePlanV1Migrator.Result.Success
        assertTrue(result.value.midwayEvents.first().timeSpec is EventTimeSpec.Unspecified)
        assertTrue(result.value.links.all { it.duration == null })
    }

    @Test fun oneMidnightCrossingMigrates() {
        val result = LocalTimePlanV1Migrator.migrate(
            legacy(listOf(1420, 20, 80))
        ) as LocalTimePlanV1Migrator.Result.Success
        assertEquals(listOf(40, 60), result.value.links.map { it.duration?.minutes })
    }

    @Test fun sourceLegacyObjectIsNotMutated() {
        val source = legacy(listOf(540, 580, 660))
        val before = source.copy(points = source.points.toList())
        LocalTimePlanV1Migrator.migrate(source)
        assertEquals(before, source)
    }

    @Test fun invalidForeignPointFailsWithoutPartialResult() {
        val source = legacy(listOf(540, 580, 660))
        val broken = source.copy(
            points = source.points.mapIndexed { i, p ->
                if (i == 1) p.copy(planId = "other") else p
            }
        )
        assertTrue(
            LocalTimePlanV1Migrator.migrate(broken)
                is LocalTimePlanV1Migrator.Result.Failure
        )
    }
}
