package com.seolhwa.armyrist.handicap

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class HandicapTest {
    private val giver = UnitContact("부여제대", "1234")
    private val upper = UnitContact("상급 A", "1111")
    private val lower = UnitContact("하급 B", "2222")
    private val config = HandicapConfig(issuer = giver, upper = listOf(upper) + List(3) { UnitContact() }, lower = listOf(lower) + List(3) { UnitContact() })
    private fun draft(kind: HandicapKind? = HandicapKind.BLACK) = SituationDraft(kind, giver, listOf("불침번"), "2026.10.21 12:30", listOf("위병소"), listOf("화재"))
    @Test fun blackGoesToFirstLower() { assertEquals(lower, HandicapRules.build(config, draft()).recipient) }
    @Test fun whiteGoesToFirstUpper() { assertEquals(upper, HandicapRules.build(config, draft(HandicapKind.WHITE)).recipient) }
    @Test fun blackWithoutLowerReturnsToSelectedIssuer() {
        val d = draft().copy(issuer = UnitContact("선택한 부여제대", "4444"))
        assertEquals(d.issuer, HandicapRules.build(config.copy(lower = emptyList()), d).recipient)
    }
    @Test fun blackDoesNotSkipToSecondLower() {
        val c = config.copy(lower = listOf(UnitContact(), lower, lower, lower))
        assertEquals(giver, HandicapRules.build(c, draft()).recipient)
    }
    @Test fun whiteWithoutFirstUpperReturnsToIssuer() {
        assertEquals(giver, HandicapRules.build(config.copy(upper = listOf(UnitContact(), upper)), draft(HandicapKind.WHITE)).recipient)
    }
    @Test fun blankNameWithPhoneIsNotRegisteredUnit() {
        assertEquals(giver, HandicapRules.build(config.copy(lower = listOf(UnitContact(" ", "9999"))), draft()).recipient)
    }
    @Test fun unitWithoutPhoneStillShowsName() {
        assertEquals(UnitContact("하급"), HandicapRules.build(config.copy(lower = listOf(UnitContact("하급"))), draft()).recipient)
    }
    @Test fun defaultIssuerIsUsedWhenNoExplicitSelection() {
        assertTrue(HandicapRules.build(config, draft().copy(issuer = UnitContact())).missing.isEmpty())
    }
    @Test fun missingIssuerReportedEvenWhenRouteRegistered() {
        assertTrue(HandicapRules.build(config.copy(issuer = UnitContact()), draft().copy(issuer = UnitContact())).missing.contains("상황부여 제대"))
    }
    @Test fun missingFieldsPreventCompleteReport() {
        assertEquals(6, HandicapRules.build(HandicapConfig(), SituationDraft()).missing.size)
    }
    @Test fun completeDraftHasNoMissingFields() { assertTrue(HandicapRules.build(config, draft()).missing.isEmpty()) }
    @Test fun reportFollowsRequestedOrder() {
        assertEquals("2026.10.21 12:30, 위병소에서 불침번 관련 화재 상황발생.", HandicapRules.build(config, draft()).sentence)
    }
    @Test fun clickOrderDoesNotAffectReport() {
        val a = draft().copy(who = listOf("불침번", "위병조장"), where = listOf("막사", "위병소"), what = listOf("화재", "응급환자"))
        val b = a.copy(who = a.who.reversed(), where = a.where.reversed(), what = a.what.reversed())
        assertEquals(HandicapRules.build(config, a).sentence, HandicapRules.build(config, b).sentence)
    }
    @Test fun customWordsAppearInSentence() {
        val d = draft().copy(who = listOf("사용자 인원"), whenText = "내일 새벽", where = listOf("사용자 장소"), what = listOf("사용자 상황"))
        assertEquals("내일 새벽, 사용자 장소에서 사용자 인원 관련 사용자 상황 상황발생.", HandicapRules.build(config, d).sentence)
    }
    @Test fun currentTimeUsesPassedInstantAndDoesNotTick() {
        assertEquals("2026.10.21 23:59", HandicapRules.currentTime(LocalDateTime.of(2026, 10, 21, 23, 59)))
    }
    @Test fun configRoundTripsAllEightUnitsAndCustomWords() {
        val c = config.copy(upper = List(4) { UnitContact("상급 $it", "$it") }, lower = List(4) { UnitContact("하급 $it", "$it") }, words = WordCategory.entries.associateWith { listOf(SituationWord(it.name, "사용자 ${it.title}")) })
        assertEquals(c, HandicapCodec.decode(HandicapCodec.encode(c)))
    }
    @Test fun deletedDefaultsStayDeleted() {
        val c = config.copy(words = WordCategory.entries.associateWith { emptyList() })
        assertEquals(c, HandicapCodec.decode(HandicapCodec.encode(c)))
    }
    @Test fun malformedConfigUsesDefaults() { assertEquals(HandicapConfig(), HandicapCodec.decode("garbage")) }
    @Test fun absentConfigUsesDefaults() { assertEquals(HandicapConfig(), HandicapCodec.decode(null)) }
    @Test fun missingVocabularyFieldsUseDefaults() { assertEquals(HandicapConfig(), HandicapCodec.decode("{}")) }
    @Test fun duplicateIdsAndEmptyWordsAreRemoved() {
        val c = config.copy(words = mapOf(WordCategory.WHO to listOf(SituationWord("same", "A"), SituationWord("same", "B"), SituationWord("empty", " ")))).normalized()
        assertEquals(listOf(SituationWord("same", "A")), c.words[WordCategory.WHO])
    }
    @Test fun normalizationPadsUnitsAndLimitsInputs() {
        val c = config.copy(upper = emptyList(), issuer = UnitContact("x".repeat(100), "1".repeat(80))).normalized()
        assertEquals(4, c.upper.size); assertEquals(60, c.issuer.name.length); assertEquals(40, c.issuer.phone.length)
    }
    @Test fun draftRoundTripKeepsTimeAndUnsortedSelections() {
        val d = draft().copy(who = listOf("위병조장", "불침번"), whenText = "2026.10.21 12:30")
        assertEquals(d, HandicapCodec.decodeDraft(HandicapCodec.encodeDraft(d)))
    }
    @Test fun malformedDraftIsEmpty() { assertEquals(SituationDraft(), HandicapCodec.decodeDraft("garbage")) }
    @Test fun editorPreservesTrailingSpaceUntilSave() {
        val c = config.copy(issuer = UnitContact("상급 ", "111 "))
        assertEquals(c, HandicapCodec.decode(HandicapCodec.encode(c, normalize = false), normalize = false))
        assertEquals(UnitContact("상급", "111"), HandicapCodec.decode(HandicapCodec.encode(c)).issuer)
    }
    @Test fun routesExplainFallbackAndSelectedType() {
        assertEquals("1차 하급 미등록 → 상황부여 제대", HandicapRules.build(config.copy(lower = emptyList()), draft()).route)
        assertEquals("화이트 → 1차 상급제대", HandicapRules.build(config, draft(HandicapKind.WHITE)).route)
    }
}
