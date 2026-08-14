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
        assertTrue(d.all { it.state == VoiceDraftState.VALID })
    }

    @Test fun countingMissingUnitNeedsReview() {
        val d=KoreanVoiceStructurer.counting("건전지 여섯")
        assertEquals(VoiceDraftState.INVALID,d.single().state)
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
        assertEquals(VoiceDraftState.VALID,d.state)
    }

    @Test fun ambiguousTimeWithoutDateIsUnresolved() {
        val d=KoreanVoiceStructurer.timePlan("새벽 1시 작업",LocalDate.of(2026,8,14))
        assertEquals(VoiceDraftState.INVALID,d.single().state)
        assertNull(d.single().dateTime)
    }

    @Test
    fun countingNumericQuantityWithSeparatedUnitIsReady() {
        val d = KoreanVoiceStructurer.counting("건전지 6 개").single()
        assertEquals(6, d.quantity)
        assertEquals("개", d.unit)
        assertEquals(VoiceDraftState.VALID, d.state)
    }

    @Test
    fun countingNumericQuantityWithAttachedUnitIsReady() {
        val d = KoreanVoiceStructurer.counting("건전지 6개").single()
        assertEquals(6, d.quantity)
        assertEquals("개", d.unit)
        assertEquals(VoiceDraftState.VALID, d.state)
    }

    @Test
    fun checklistRecognitionSegmentsBecomeSeparateDraftHints() {
        val d = KoreanVoiceStructurer.checklist(
            "차량 상태 확인\n통신 상태 확인\n인원 점검"
        )
        assertEquals(
            listOf("차량 상태 확인", "통신 상태 확인", "인원 점검"),
            d.map { it.name }
        )
    }

    @Test
    fun countingRecognitionSegmentsRemainSeparateItems() {
        val d = KoreanVoiceStructurer.counting("고양이 세 마리\n강아지 한 마리")
        assertEquals(2, d.size)
        assertEquals("고양이", d[0].name)
        assertEquals(3, d[0].quantity)
        assertEquals("마리", d[0].unit)
        assertEquals("강아지", d[1].name)
        assertEquals(1, d[1].quantity)
        assertEquals("마리", d[1].unit)
    }

    @Test
    fun timePlanKoreanSpokenHourIsRecognizedWhenDateIsExplicit() {
        val d = KoreanVoiceStructurer.timePlan(
            "14일 오후 세 시 작업",
            LocalDate.of(2026, 8, 14)
        ).single()
        assertEquals(LocalDateTime.of(2026, 8, 14, 15, 0), d.dateTime)
        assertEquals("작업", d.name)
        assertEquals(VoiceDraftState.VALID, d.state)
    }

    @Test
    fun timePlanRecognitionSegmentsCreateMultipleDrafts() {
        val d = KoreanVoiceStructurer.timePlan(
            "14일 오전 아홉 시 출발\n15일 새벽 한 시 작업\n16일 오전 여덟 시 복귀",
            LocalDate.of(2026, 8, 14)
        )
        assertEquals(3, d.size)
        assertEquals(LocalDateTime.of(2026, 8, 14, 9, 0), d[0].dateTime)
        assertEquals(LocalDateTime.of(2026, 8, 15, 1, 0), d[1].dateTime)
        assertEquals(LocalDateTime.of(2026, 8, 16, 8, 0), d[2].dateTime)
    }


    @Test
    fun countingSingleUtteranceWithoutPunctuationCanSplitRepeatedStructures() {
        val d = KoreanVoiceStructurer.counting("생수 스물네 병 전투식량 열세 개 건전지 여섯 개")
        assertEquals(3, d.size)
        assertEquals(listOf("생수", "전투식량", "건전지"), d.map { it.name })
        assertEquals(listOf(24, 13, 6), d.map { it.quantity })
    }

    @Test
    fun checklistSingleUtteranceWithoutPunctuationUsesActionEndings() {
        val d = KoreanVoiceStructurer.checklist("차량 상태 확인 통신장비 확인 인원 점검")
        assertEquals(listOf("차량 상태 확인", "통신장비 확인", "인원 점검"), d.map { it.name })
    }

    @Test
    fun timePlanSingleUtteranceWithoutPunctuationUsesExplicitDayBoundaries() {
        val d = KoreanVoiceStructurer.timePlan(
            "14일 오전 아홉 시 출발 15일 새벽 한 시 작업 16일 오전 여덟 시 복귀",
            LocalDate.of(2026, 8, 14)
        )
        assertEquals(3, d.size)
        assertEquals(LocalDateTime.of(2026, 8, 14, 9, 0), d[0].dateTime)
        assertEquals(LocalDateTime.of(2026, 8, 15, 1, 0), d[1].dateTime)
        assertEquals(LocalDateTime.of(2026, 8, 16, 8, 0), d[2].dateTime)
    }


    @Test
    fun ambiguousAttachedKoreanQuantityUnitRequiresReview() {
        val d = KoreanVoiceStructurer.counting("전차 세대").single()
        assertEquals("전차", d.name)
        assertEquals(3, d.quantity)
        assertEquals("대", d.unit)
        assertEquals(VoiceDraftState.REVIEW_REQUIRED, d.state)
        assertEquals(VoiceFieldState.REVIEW_REQUIRED, d.quantityState)
        assertEquals(VoiceFieldState.REVIEW_REQUIRED, d.unitState)
        assertEquals("전차 세대", d.rawTranscript)
    }

    @Test
    fun missingRequiredCountingUnitIsInvalid() {
        val d = KoreanVoiceStructurer.counting("건전지 여섯").single()
        assertEquals(VoiceDraftState.INVALID, d.state)
        assertEquals(VoiceFieldState.INVALID, d.unitState)
    }

    @Test
    fun userConfirmedAmbiguousCountingFieldsCanBecomeValid() {
        val d = KoreanVoiceStructurer.counting("전차 세대").single()
        val edited = KoreanVoiceStructurer.revalidate(
            d.copy(
                quantityState = VoiceFieldState.VALID,
                unitState = VoiceFieldState.VALID
            )
        )
        assertEquals(VoiceDraftState.VALID, edited.state)
    }

    @Test
    fun rawTranscriptIsPreservedPerDraft() {
        val d = KoreanVoiceStructurer.counting("고양이 세 마리\\n강아지 세 마리")
        assertEquals(listOf("고양이 세 마리", "강아지 세 마리"), d.map { it.rawTranscript })
    }

}
