package com.seolhwa.armyrist.timeplan.domain

import org.junit.Assert.*
import org.junit.Test

class TimePlanCandidateEngineTest {
    private fun c(h:Int,m:Int)=ClockTime.requireMinuteOfDay(h*60+m)
    private fun base(linkOrigin: ValueOrigin = ValueOrigin.DERIVED) =
        RevisedTimePlan(
            id="p", title="Plan",
            start=TimeAnchor(ClockValue.explicit(c(9,0))),
            midwayEvents=listOf(
                TimeEvent(
                    id="m", kind=TimeEventKind.MIDWAY, order=0, name="Meet",
                    timeSpec=EventTimeSpec.Single(ClockValue.explicit(c(9,40)))
                )
            ),
            end=TimeAnchor(ClockValue.explicit(c(11,0))),
            links=listOf(
                TimeLink(
                    TimePlanConflictEngine.START_ID, "m",
                    TimeDuration.requireMinutes(40), linkOrigin
                ),
                TimeLink(
                    "m", TimePlanConflictEngine.END_ID,
                    TimeDuration.requireMinutes(80), ValueOrigin.DERIVED
                )
            ),
            createdAt="c", updatedAt="u"
        )

    @Test fun existingStateIsNotMutated() {
        val existing=base()
        val candidate=TimePlanCandidateEngine.create(
            existing,
            TimePlanCandidateEngine.EditIntent.SetEventTime(
                "m", EventTimeSpec.Single(ClockValue.explicit(c(10,0)))
            )
        )
        val original=(existing.midwayEvents.first().timeSpec as EventTimeSpec.Single).value.time
        assertEquals(c(9,40),original)
        assertNotEquals(existing,candidate.proposed)
    }

    @Test fun explicitClockEditRecalculatesDerivedAdjacentLinks() {
        val candidate=TimePlanCandidateEngine.create(
            base(),
            TimePlanCandidateEngine.EditIntent.SetEventTime(
                "m", EventTimeSpec.Single(ClockValue.explicit(c(10,0)))
            )
        )
        assertEquals(60,candidate.proposed.links[0].duration?.minutes)
        assertEquals(60,candidate.proposed.links[1].duration?.minutes)
        assertEquals(ValueOrigin.DERIVED,candidate.proposed.links[0].origin)
    }

    @Test fun explicitDurationShiftsAllDownstreamClocksByDelta() {
        val existing=base()
        val candidate=TimePlanCandidateEngine.create(
            existing,
            TimePlanCandidateEngine.EditIntent.SetLinkDuration(
                TimePlanConflictEngine.START_ID, "m",
                TimeDuration.requireMinutes(50)
            )
        )
        val midway=(candidate.proposed.midwayEvents.first().timeSpec as EventTimeSpec.Single).value
        assertEquals(c(9,50),midway.time)
        assertEquals(ValueOrigin.DERIVED,midway.origin)
        assertEquals(c(11,10),candidate.proposed.end.value.time)
        assertEquals(ValueOrigin.DERIVED,candidate.proposed.end.value.origin)
        val firstLink = candidate.proposed.links.first {
            it.fromNodeId == TimePlanConflictEngine.START_ID && it.toNodeId == "m"
        }
        val secondLink = candidate.proposed.links.first {
            it.fromNodeId == "m" && it.toNodeId == TimePlanConflictEngine.END_ID
        }
        assertEquals(50,firstLink.duration?.minutes)
        assertEquals(80,secondLink.duration?.minutes)
        assertTrue(candidate.conflicts.isEmpty())
    }

    @Test fun laterMidwayThanSameDayEndRequiresConfirmation() {
        assertTrue(
            TimePlanCandidateEngine.requiresEndBoundaryConfirmation(
                existing=base(),
                eventId="m",
                proposedSpec=EventTimeSpec.Single(ClockValue.explicit(c(22,0)))
            )
        )
    }

    @Test fun confirmedEventEditShiftsDownstreamBySameDelta() {
        val candidate=TimePlanCandidateEngine.createEventTimeWithDownstreamShift(
            existing=base(),
            eventId="m",
            proposedSpec=EventTimeSpec.Single(ClockValue.explicit(c(10,0)))
        )
        val midway=(candidate.proposed.midwayEvents.first().timeSpec as EventTimeSpec.Single).value
        assertEquals(c(10,0),midway.time)
        assertEquals(ValueOrigin.EXPLICIT,midway.origin)
        assertEquals(c(11,20),candidate.proposed.end.value.time)
        assertEquals(60,candidate.proposed.links[0].duration?.minutes)
        assertEquals(80,candidate.proposed.links[1].duration?.minutes)
        assertTrue(candidate.conflicts.isEmpty())
    }


    @Test fun durationEditStoresCustomLinkLabel() {
        val existing=base()
        val candidate=TimePlanCandidateEngine.create(
            existing,
            TimePlanCandidateEngine.EditIntent.SetLinkDuration(
                TimePlanConflictEngine.START_ID,
                "m",
                TimeDuration.requireMinutes(50),
                label="이동"
            )
        )
        val link=candidate.proposed.links.first {
            it.fromNodeId == TimePlanConflictEngine.START_ID && it.toNodeId == "m"
        }
        assertEquals("이동",link.label)
        assertEquals(50,link.duration?.minutes)
    }


    @Test fun rangeExtensionShiftsDownstreamFromRangeEnd() {
        val existing=base()
        val candidate=TimePlanCandidateEngine.createEventTimeWithDownstreamShift(
            existing=existing,
            eventId="m",
            proposedSpec=EventTimeSpec.Range(
                ClockValue.explicit(c(9,40)),
                ClockValue.explicit(c(10,10))
            )
        )
        val event=candidate.proposed.midwayEvents.first()
        val spec=event.timeSpec as EventTimeSpec.Range
        assertEquals(c(9,40),spec.start.time)
        assertEquals(c(10,10),spec.end.time)
        // Old departure was 09:40, new departure is 10:10 => downstream +30m.
        assertEquals(c(11,30),candidate.proposed.end.value.time)
    }

    @Test fun rangeConflictCandidateRetainsProposedRangeForPreview() {
        val existing=base()
        val candidate=TimePlanCandidateEngine.create(
            existing,
            TimePlanCandidateEngine.EditIntent.SetEventTime(
                "m",
                EventTimeSpec.Range(
                    ClockValue.explicit(c(8,30)),
                    ClockValue.explicit(c(10,0))
                )
            )
        )
        val spec=candidate.proposed.midwayEvents.first().timeSpec as EventTimeSpec.Range
        assertEquals(c(8,30),spec.start.time)
        assertEquals(c(10,0),spec.end.time)
    }

    @Test fun cancelCanDiscardCandidateBecauseExistingIsRetained() {
        val existing=base()
        val candidate=TimePlanCandidateEngine.create(
            existing,
            TimePlanCandidateEngine.EditIntent.SetEnd(ClockValue.explicit(c(12,0)))
        )
        assertSame(existing,candidate.existing)
        assertEquals(c(11,0),candidate.existing.end.value.time)
    }
}
