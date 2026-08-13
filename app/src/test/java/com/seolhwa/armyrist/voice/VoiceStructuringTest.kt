package com.seolhwa.armyrist.voice

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class VoiceStructuringTest {
    @Test fun countingMultipleKoreanNumbers() {
        val d=KoreanVoiceStructurer.counting("생수 스물네 병, 전투식량 열세 개, 건전지 여섯 개")
        assertEquals(3,d.size)
        assertEquals(24,d[0].quantity)
        assertEquals("병",d[0].unit)
        assertEquals(13,d[1].quantity)
        assertEquals(6,d[2].quantity)
        assertTrue(d.all { it.state == DraftState.READY })
    }

    @Test fun countingMissingUnitNeedsReview() {
        val d=KoreanVoiceStructurer.counting("건전지 여섯")
        assertEquals(DraftState.UNRESOLVED,d.single().state)
    }

    @Test fun checklistCreatesMultipleNewDrafts() {
        val d=KoreanVoiceStructurer.checklist("차량 상태 확인, 통신장비 확인, 인원 확인")
        assertEquals(listOf("차량 상태 확인","통신장비 확인","인원 확인"),d.map{it.name})
    }

    @Test fun timePlanMultiDayUsesExplicitDays() {
        val d=KoreanVoiceStructurer.timePlan("14일 9시 출발, 15일 새벽 1시 작업, 16일 8시 복귀", LocalDate.of(2026,8,14))
        assertEquals(LocalDateTime.of(2026,8,14,9,0),d[0].dateTime)
        assertEquals(LocalDateTime.of(2026,8,15,1,0),d[1].dateTime)
        assertEquals(LocalDateTime.of(2026,8,16,8,0),d[2].dateTime)
    }

    @Test fun timePlanRangeAcrossMidnightIsStructured() {
        val d=KoreanVoiceStructurer.timePlan("14일 23시부터 1시까지 야간작업",LocalDate.of(2026,8,14)).single()
        assertEquals(LocalDateTime.of(2026,8,14,23,0),d.dateTime)
        assertEquals(LocalDateTime.of(2026,8,15,1,0),d.rangeEnd)
        assertEquals(DraftState.READY,d.state)
    }

    @Test fun ambiguousTimeWithoutDateIsUnresolved() {
        val d=KoreanVoiceStructurer.timePlan("새벽 1시 작업",LocalDate.of(2026,8,14))
        assertEquals(DraftState.UNRESOLVED,d.single().state)
        assertNull(d.single().dateTime)
    }

    @Test
    fun countingNumericQuantityWithSeparatedUnitIsReady() {
        val d = KoreanVoiceStructurer.counting("건전지 6 개").single()
        assertEquals(6, d.quantity)
        assertEquals("개", d.unit)
        assertEquals(DraftState.READY, d.state)
    }

    @Test
    fun countingNumericQuantityWithAttachedUnitIsReady() {
        val d = KoreanVoiceStructurer.counting("건전지 6개").single()
        assertEquals(6, d.quantity)
        assertEquals("개", d.unit)
        assertEquals(DraftState.READY, d.state)
    }
}
