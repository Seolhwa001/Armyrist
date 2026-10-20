package com.seolhwa.armyrist.home

import com.seolhwa.armyrist.timeplan.v3.domain.*
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class HomeBannerTest {
    private val now = LocalDateTime.of(2026, 10, 20, 12, 0)
    private fun plan(id: String = "p", start: LocalDateTime = now.minusHours(1), end: LocalDateTime = now.plusHours(1)) =
        DateAwareTimePlan(id, "계획 $id", start = DateTimeAnchor(DateTimeValue.explicit(start)),
            end = DateTimeAnchor(DateTimeValue.explicit(end)), createdAt = "0", updatedAt = "0")
    private fun action(id: String, at: LocalDateTime, complete: Boolean = false) =
        TimePlanActionItem(id = id, parentPointId = DateTimePlanRules.START_ID, content = id,
            scheduledDateTime = at, completionState = if (complete) ActionCompletionState.COMPLETE else ActionCompletionState.INCOMPLETE)

    @Test fun startIsInclusiveAndEndExclusive() {
        val p = plan()
        assertTrue(TimePlanLiveStatus.isActive(p, now))
        assertTrue(TimePlanLiveStatus.isActive(p, now.minusHours(1)))
        assertFalse(TimePlanLiveStatus.isActive(p, now.plusHours(1)))
        assertFalse(TimePlanLiveStatus.isActive(p, now.minusHours(1).minusSeconds(1)))
    }
    @Test fun overnightPlanUsesDateNotJustClockTime() {
        val p = plan(start = now.minusDays(1).withHour(23), end = now.withHour(2))
        assertTrue(TimePlanLiveStatus.isActive(p, now.withHour(1)))
        assertFalse(TimePlanLiveStatus.isActive(p, now.plusDays(1).withHour(1)))
    }
    @Test fun relativeAndLegacyTemplatesAreNotLive() {
        assertFalse(TimePlanLiveStatus.isActive(plan().copy(dateDisplayMode = TimePlanDateDisplayMode.RELATIVE_D_DAY), now))
        assertFalse(TimePlanLiveStatus.isActive(plan().copy(legacyDateMigrationRequired = true), now))
    }
    @Test fun missingOrReversedBoundsAreNotLive() {
        assertFalse(TimePlanLiveStatus.isActive(plan().copy(end = DateTimeAnchor()), now))
        assertFalse(TimePlanLiveStatus.isActive(plan().copy(start = DateTimeAnchor()), now))
        assertFalse(TimePlanLiveStatus.isActive(plan(start = now.plusHours(2), end = now), now))
    }
    @Test fun finalRangeCanSupplyMissingEnd() {
        val p = plan().copy(end = DateTimeAnchor(), finalPoint = DateTimeEvent("f", TimeEventKind.FINAL, 0, "최종",
            EventDateTimeSpec.Range(DateTimeValue.explicit(now), DateTimeValue.explicit(now.plusHours(2)))))
        assertTrue(TimePlanLiveStatus.isActive(p, now.plusHours(1)))
    }
    @Test fun allConcurrentPlansAreShownDeterministically() {
        val a = plan("a", end = now.plusMinutes(20)); val b = plan("b", end = now.plusMinutes(10))
        assertEquals(listOf("b", "a"), TimePlanLiveStatus.activePlans(listOf(a, b), now).map { it.id })
    }
    @Test fun nextSkipsCompletedAndPastActionsAcrossPlans() {
        val a = plan("a").copy(actions = listOf(action("past", now.minusMinutes(1)), action("done", now.plusMinutes(1), true), action("later", now.plusMinutes(30))))
        val b = plan("b").copy(actions = listOf(action("next", now.plusMinutes(5))))
        val next = TimePlanLiveStatus.nextAction(listOf(a, b), now)!!
        assertEquals("b", next.plan.id); assertEquals("next", next.action.id)
    }
    @Test fun actionExactlyNowIsEligible() {
        assertEquals("now", TimePlanLiveStatus.nextAction(listOf(plan().copy(actions = listOf(action("now", now)))), now)?.action?.id)
    }
    @Test fun noUpcomingActionReturnsNull() {
        assertNull(TimePlanLiveStatus.nextAction(emptyList(), now))
        assertNull(TimePlanLiveStatus.nextAction(listOf(plan().copy(actions = listOf(action("old", now.minusMinutes(1))))), now))
    }
    @Test fun selectedPlanSetDoesNotLeakOtherActions() {
        val a = plan("a").copy(actions = listOf(action("a", now.plusMinutes(20))))
        val b = plan("b").copy(actions = listOf(action("b", now.plusMinutes(1))))
        assertEquals("a", TimePlanLiveStatus.nextAction(listOf(a, b).filter { it.id == "a" }, now)?.action?.id)
    }
    @Test fun relativeTemplatesDoNotContributeNextActions() {
        val p = plan().copy(dateDisplayMode = TimePlanDateDisplayMode.RELATIVE_D_DAY, actions = listOf(action("template", now.plusMinutes(1))))
        assertNull(TimePlanLiveStatus.nextAction(listOf(p), now))
    }
    @Test fun emptyAndCorruptSettingsUseTimePlanDefaults() {
        assertEquals(HomeBannerConfig(), HomeBannerCodec.decode(null))
        assertEquals(HomeBannerConfig(), HomeBannerCodec.decode("broken"))
        assertEquals(HomeBannerConfig(), HomeBannerCodec.decode("{}"))
    }
    @Test fun savedNoticeAndSelectionRoundTripIncludingUnicode() {
        val c = HomeBannerConfig(HomeBannerMode.NOTICE, "plan-한글", "진행", "다음", "공지 😀", "첫 줄\n둘째 줄")
        assertEquals(c, HomeBannerCodec.decode(HomeBannerCodec.encode(c)))
    }
    @Test fun hiddenBannerStaysHiddenAfterRestart() {
        val c = HomeBannerConfig(mode = HomeBannerMode.HIDDEN)
        assertEquals(HomeBannerMode.HIDDEN, HomeBannerCodec.decode(HomeBannerCodec.encode(c)).mode)
    }
    @Test fun whitespaceHeadingsFallBackAndContentIsBounded() {
        val c = HomeBannerConfig(planId = " ", currentHeading = " ", nextHeading = "", noticeBody = "a".repeat(2000)).normalized()
        assertNull(c.planId); assertEquals("현재 진행중인 시간계획", c.currentHeading)
        assertEquals("다음 실시사항", c.nextHeading); assertEquals(1000, c.noticeBody.length)
    }
    @Test fun unknownModeIsForwardCompatible() {
        assertEquals(HomeBannerMode.TIME_PLAN, HomeBannerCodec.decode("{\"mode\":\"FUTURE\"}").mode)
    }
}
