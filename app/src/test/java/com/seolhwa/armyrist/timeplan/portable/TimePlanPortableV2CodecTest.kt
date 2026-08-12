package com.seolhwa.armyrist.timeplan.portable

import com.seolhwa.armyrist.timeplan.domain.ClockTime
import com.seolhwa.armyrist.timeplan.domain.ClockValue
import com.seolhwa.armyrist.timeplan.domain.EventTimeSpec
import com.seolhwa.armyrist.timeplan.domain.RevisedTimePlan
import com.seolhwa.armyrist.timeplan.domain.TimeAnchor
import com.seolhwa.armyrist.timeplan.domain.TimeDuration
import com.seolhwa.armyrist.timeplan.domain.TimeEvent
import com.seolhwa.armyrist.timeplan.domain.TimeEventKind
import com.seolhwa.armyrist.timeplan.domain.TimeLink
import com.seolhwa.armyrist.timeplan.domain.ValueOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimePlanPortableV2CodecTest {
    private fun t(h: Int, m: Int) =
        ClockTime.requireMinuteOfDay(h * 60 + m)

    @Test
    fun roundTripPreservesRevisedDomainMeaning() {
        val midway = TimeEvent(
            id = "mid",
            kind = TimeEventKind.MIDWAY,
            order = 0,
            name = "집결",
            timeSpec = EventTimeSpec.Range(
                ClockValue.explicit(t(9, 40)),
                ClockValue.explicit(t(10, 0))
            ),
            note = "인원 확인"
        )
        val finalPoint = TimeEvent(
            id = "final",
            kind = TimeEventKind.FINAL,
            order = 1,
            name = "복귀",
            timeSpec = EventTimeSpec.Single(
                ClockValue.derived(t(12, 30))
            ),
            note = "장비 정리"
        )
        val plan = RevisedTimePlan(
            id = "plan",
            title = "표준 교육시간",
            start = TimeAnchor(ClockValue.explicit(t(9, 0))),
            midwayEvents = listOf(midway),
            finalPoint = finalPoint,
            end = TimeAnchor(ClockValue.explicit(t(13, 0))),
            links = listOf(
                TimeLink("__START__", "mid", TimeDuration.requireMinutes(40), ValueOrigin.DERIVED),
                TimeLink("mid", "final", TimeDuration.requireMinutes(150), ValueOrigin.EXPLICIT),
                TimeLink("final", "__END__", TimeDuration.requireMinutes(30), ValueOrigin.DERIVED)
            ),
            memo = "전체 메모",
            createdAt = "1",
            updatedAt = "2"
        )

        val encoded = TimePlanPortableV2Codec.encode(plan)
        val decoded = TimePlanPortableV2Codec.decode(encoded)

        assertEquals(2, encoded.getInt("schemaVersion"))
        assertEquals(plan, decoded)
    }

    @Test
    fun partialStateRoundTripDoesNotInventValues() {
        val plan = RevisedTimePlan(
            id = "partial",
            title = "부분 계획",
            start = TimeAnchor(ClockValue.unset()),
            end = TimeAnchor(ClockValue.unset()),
            createdAt = "1",
            updatedAt = "1"
        )

        val decoded = TimePlanPortableV2Codec.decode(
            TimePlanPortableV2Codec.encode(plan)
        )

        assertNull(decoded.start.value.time)
        assertNull(decoded.end.value.time)
        assertEquals(ValueOrigin.UNSET, decoded.start.value.origin)
        assertEquals(ValueOrigin.UNSET, decoded.end.value.origin)
    }
}
