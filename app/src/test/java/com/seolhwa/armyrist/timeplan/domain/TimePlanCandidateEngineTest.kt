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


    @Test fun fullEventEditPersistsNameAndNote() {
        val existing = base()
        val old = existing.midwayEvents.first()
        val changed = old.copy(
            name = "새 지점명",
            note = "새 비고",
            timeSpec = EventTimeSpec.Single(ClockValue.explicit(c(10,0)))
        )

        val candidate = TimePlanCandidateEngine.createEventEdit(
            existing = existing,
            changedEvent = changed
        )

        val saved = candidate.proposed.midwayEvents.first()
        assertEquals("새 지점명", saved.name)
        assertEquals("새 비고", saved.note)
        assertEquals(
            c(10,0),
            (saved.timeSpec as EventTimeSpec.Single).value.time
        )
    }

    @Test fun fullEventEditWithDownstreamShiftAlsoPersistsMetadata() {
        val existing = base()
        val old = existing.midwayEvents.first()
        val changed = old.copy(
            name = "이동 완료",
            note = "장비 확인",
            timeSpec = EventTimeSpec.Single(ClockValue.explicit(c(10,0)))
        )

        val candidate = TimePlanCandidateEngine.createEventEditWithDownstreamShift(
            existing = existing,
            changedEvent = changed
        )

        val saved = candidate.proposed.midwayEvents.first()
        assertEquals("이동 완료", saved.name)
        assertEquals("장비 확인", saved.note)
        assertEquals(c(10,0), (saved.timeSpec as EventTimeSpec.Single).value.time)
        assertEquals(c(11,20), candidate.proposed.end.value.time)
    }


    @Test fun rangeStartingEarlierThanPreviousDepartureCanReflowPrefixAndSuffix() {
        val existing = RevisedTimePlan(
            id = "p",
            title = "x",
            start = TimeAnchor(ClockValue.explicit(c(8,30))),
            midwayEvents = listOf(
                TimeEvent(
                    id = "a",
                    kind = TimeEventKind.MIDWAY,
                    order = 0,
                    name = "A",
                    timeSpec = EventTimeSpec.Single(ClockValue.explicit(c(9,30)))
                ),
                TimeEvent(
                    id = "b",
                    kind = TimeEventKind.MIDWAY,
                    order = 1,
                    name = "B",
                    timeSpec = EventTimeSpec.Single(ClockValue.explicit(c(10,0)))
                )
            ),
            end = TimeAnchor(ClockValue.explicit(c(12,0))),
            links = listOf(
                TimeLink(
                    TimePlanConflictEngine.START_ID, "a",
                    TimeDuration.requireMinutes(60), ValueOrigin.DERIVED
                ),
                TimeLink("a", "b", TimeDuration.requireMinutes(30), ValueOrigin.DERIVED),
                TimeLink(
                    "b", TimePlanConflictEngine.END_ID,
                    TimeDuration.requireMinutes(120), ValueOrigin.DERIVED
                )
            ),
            createdAt = "0",
            updatedAt = "0"
        )

        val changed = existing.midwayEvents[1].copy(
            timeSpec = EventTimeSpec.Range(
                ClockValue.explicit(c(9,20)),
                ClockValue.explicit(c(10,20))
            )
        )

        assertTrue(
            TimePlanCandidateEngine.eventEditNeedsPrefixReflow(existing, changed)
        )

        val candidate =
            TimePlanCandidateEngine.createEventEditWithTimelineReflow(
                existing,
                changed
            )

        val first = candidate.proposed.midwayEvents.first { it.id == "a" }
        val second = candidate.proposed.midwayEvents.first { it.id == "b" }
        assertEquals(
            c(9,20),
            (first.timeSpec as EventTimeSpec.Single).value.time
        )
        val range = second.timeSpec as EventTimeSpec.Range
        assertEquals(c(9,20), range.start.time)
        assertEquals(c(10,20), range.end.time)
        assertEquals(c(12,20), candidate.proposed.end.value.time)
        assertTrue(candidate.conflicts.isEmpty())
    }


    @Test fun singlePointInsideAnotherRangeIsHardOccupiedRangeConflict() {
        val existing = RevisedTimePlan(
            id = "p", title = "x",
            start = TimeAnchor(ClockValue.explicit(c(8,0))),
            midwayEvents = listOf(
                TimeEvent(
                    id = "r", kind = TimeEventKind.MIDWAY, order = 0, name = "교육",
                    timeSpec = EventTimeSpec.Range(
                        ClockValue.explicit(c(9,0)),
                        ClockValue.explicit(c(10,0))
                    )
                ),
                TimeEvent(
                    id = "p2", kind = TimeEventKind.MIDWAY, order = 1, name = "이동",
                    timeSpec = EventTimeSpec.Single(ClockValue.explicit(c(10,30)))
                )
            ),
            end = TimeAnchor(ClockValue.explicit(c(12,0))),
            links = emptyList(), createdAt = "0", updatedAt = "0"
        )
        val changed = existing.midwayEvents[1].copy(
            timeSpec = EventTimeSpec.Single(ClockValue.explicit(c(9,30)))
        )
        val conflict = TimePlanCandidateEngine.occupiedRangeConflict(existing, changed)
        assertEquals("교육", conflict?.eventName)
        assertEquals(c(9,0), conflict?.rangeStart)
        assertEquals(c(10,0), conflict?.rangeEnd)
    }

    @Test fun pointAtExactRangeEndIsAllowed() {
        val existing = RevisedTimePlan(
            id = "p", title = "x",
            start = TimeAnchor(ClockValue.explicit(c(8,0))),
            midwayEvents = listOf(
                TimeEvent(
                    id = "r", kind = TimeEventKind.MIDWAY, order = 0, name = "교육",
                    timeSpec = EventTimeSpec.Range(
                        ClockValue.explicit(c(9,0)),
                        ClockValue.explicit(c(10,0))
                    )
                ),
                TimeEvent(
                    id = "p2", kind = TimeEventKind.MIDWAY, order = 1, name = "이동",
                    timeSpec = EventTimeSpec.Single(ClockValue.explicit(c(10,30)))
                )
            ),
            end = TimeAnchor(ClockValue.explicit(c(12,0))),
            links = emptyList(), createdAt = "0", updatedAt = "0"
        )
        val changed = existing.midwayEvents[1].copy(
            timeSpec = EventTimeSpec.Single(ClockValue.explicit(c(10,0)))
        )
        assertEquals(null, TimePlanCandidateEngine.occupiedRangeConflict(existing, changed))
    }

    @Test fun overlappingRangeWithAnotherRangeIsHardConflict() {
        val existing = RevisedTimePlan(
            id = "p", title = "x",
            start = TimeAnchor(ClockValue.explicit(c(8,0))),
            midwayEvents = listOf(
                TimeEvent(
                    id = "r", kind = TimeEventKind.MIDWAY, order = 0, name = "교육",
                    timeSpec = EventTimeSpec.Range(
                        ClockValue.explicit(c(9,0)),
                        ClockValue.explicit(c(10,0))
                    )
                ),
                TimeEvent(
                    id = "r2", kind = TimeEventKind.MIDWAY, order = 1, name = "정비",
                    timeSpec = EventTimeSpec.Range(
                        ClockValue.explicit(c(10,30)),
                        ClockValue.explicit(c(11,0))
                    )
                )
            ),
            end = TimeAnchor(ClockValue.explicit(c(12,0))),
            links = emptyList(), createdAt = "0", updatedAt = "0"
        )
        val changed = existing.midwayEvents[1].copy(
            timeSpec = EventTimeSpec.Range(
                ClockValue.explicit(c(9,30)),
                ClockValue.explicit(c(10,30))
            )
        )
        assertEquals("교육", TimePlanCandidateEngine.occupiedRangeConflict(existing, changed)?.eventName)
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
