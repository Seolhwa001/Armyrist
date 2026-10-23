package com.seolhwa.armyrist.voice
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
class VoiceAdditionalRegressionTest {
    @Test fun nativeNumbersAndMultiwordNamesAreRetained(){val d=KoreanVoiceStructurer.counting("보조 배터리 서른 두 개 휴대용 무전기 마흔여섯 대");assertEquals(listOf("보조 배터리","휴대용 무전기"),d.map{it.name});assertEquals(listOf(32,46),d.map{it.quantity});assertNull(KoreanVoiceStructurer.numberOf("십사십"))}
    @Test fun invalidDatesRemainInvalidAndNightRangesCrossMidnight(){
        val date=LocalDate.of(2026,10,23)
        listOf("2월 30일 9시 집결","32일 9시 집결","24일 25시 집결","24일 9시 99분 출발").forEach{assertEquals(it,VoiceDraftState.INVALID,KoreanVoiceStructurer.timePlan(it,date).single().state)}
        assertEquals(LocalDateTime.of(2026,10,24,1,0),KoreanVoiceStructurer.timePlan("23일 오후 11시부터 1시까지 근무",date).single().rangeEnd)
    }
    @Test fun tailsAreNeverDiscardedAndCorrectionNeedsReview(){
        val d=KoreanVoiceStructurer.counting("생수 3병 확인 필요한 물품");assertEquals(2,d.size);assertEquals("확인 필요한 물품",d.last().name);assertEquals(VoiceDraftState.INVALID,d.last().state)
        val c=KoreanVoiceStructurer.checklist("구금낭 준비").single();assertEquals("구급낭 준비",c.name);assertEquals("구금낭 준비",c.rawTranscript);assertEquals(VoiceDraftState.REVIEW_REQUIRED,c.state)
    }
    @Test fun metricNormalizesNumberNotationWithoutHidingActualErrors(){
        assertEquals(KoreanVoiceStructurer.numberAwareComparison("스물네 병"),KoreanVoiceStructurer.numberAwareComparison("24병"))
        assertNotEquals(KoreanVoiceStructurer.numberAwareComparison("구급낭"),KoreanVoiceStructurer.numberAwareComparison("구금낭"))
    }
}
