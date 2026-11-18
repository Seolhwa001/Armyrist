package com.seolhwa.armyrist.timeplan.v3

import androidx.test.core.app.ApplicationProvider
import com.seolhwa.armyrist.ArmyristApplication
import com.seolhwa.armyrist.timeplan.v3.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=ArmyristApplication::class)
class TimePlanPeriodChangesTest {
    private val repo get()=ApplicationProvider.getApplicationContext<ArmyristApplication>().dateAwareTimePlanRepository
    private val time=LocalDateTime.of(2030,12,31,22,0)
    private fun plan():DateAwareTimePlan {
        val base=repo.createPlan(time)!!
        val event=DateTimeEvent("event",TimeEventKind.MIDWAY,0,"야간",EventDateTimeSpec.Range(DateTimeValue.explicit(time),DateTimeValue.explicit(time.plusHours(8))),dateTimeLocked=true)
        val p=DateTimePlanRules.normalizeTopology(base.copy(end=DateTimeAnchor(DateTimeValue.explicit(time.plusHours(10)),true),midwayEvents=listOf(event),memo="보존할 메모",actions=listOf(TimePlanActionItem(id="night",content="야간 점검",scheduledDateTime=time.plusHours(4),durationMinutes=15,timeLocked=true,notificationMode=ActionNotificationMode.SIMPLE,note="비고"))))
        assertTrue(repo.commit(p));return p
    }
    private fun moved(p:DateAwareTimePlan,days:Long)=p.copy(start=p.start.copy(value=p.start.value.copy(value=time.plusDays(days))),end=p.end.copy(value=p.end.value.copy(value=p.end.value.value!!.plusDays(days))))
    @Test fun specificDateMovesOvernightLockedEntriesAndKeepsTheirMetadata(){
        val p=plan();assertTrue(repo.changePeriod(p.id,moved(p,4)))
        val saved=repo.getPlan(p.id)!!
        assertEquals(p.actions.single().copy(scheduledDateTime=p.actions.single().scheduledDateTime.plusDays(4)),saved.actions.single())
        assertEquals(time.plusDays(4),DateTimePlanRules.arrival(saved.midwayEvents.single().timeSpec))
        assertEquals(time.plusDays(4).plusHours(8),DateTimePlanRules.departure(saved.midwayEvents.single().timeSpec))
        assertTrue(saved.midwayEvents.single().dateTimeLocked);assertEquals(p.memo,saved.memo)
    }
    @Test fun staleEditorDoesNotOverwriteNotificationCompletionOrNewEntries(){
        val p=plan();assertTrue(repo.setUnifiedCompletion(p.id,"A:night",ActionCompletionState.COMPLETE))
        assertTrue(repo.saveAction(p.id,TimePlanActionItem(id="new",content="추가 점검",scheduledDateTime=time.plusHours(7))))
        assertTrue(repo.changePeriod(p.id,moved(p,-10)))
        val saved=repo.getPlan(p.id)!!
        assertEquals(ActionCompletionState.COMPLETE,saved.actions.single{it.id=="night"}.completionState)
        assertEquals(time.minusDays(10).plusHours(7),saved.actions.single{it.id=="new"}.scheduledDateTime)
    }
    @Test fun endOnlyAndClockOnlyEditsDoNotMoveEntries(){
        val p=plan();assertTrue(repo.changePeriod(p.id,p.copy(end=p.end.copy(value=DateTimeValue.explicit(time.plusHours(12))))))
        assertEquals(p.actions,repo.getPlan(p.id)!!.actions)
        val current=repo.getPlan(p.id)!!;assertTrue(repo.changePeriod(p.id,current.copy(start=current.start.copy(value=DateTimeValue.explicit(time.plusHours(1))))))
        assertEquals(p.actions,repo.getPlan(p.id)!!.actions)
    }
    @Test fun invalidPeriodIsAtomic(){
        val p=plan();assertFalse(repo.changePeriod(p.id,p.copy(end=p.end.copy(value=DateTimeValue.explicit(time.minusDays(1))))))
        assertEquals(p,repo.getPlan(p.id))
    }
    @Test fun todayRepairsStrandedDatesWhenBaseWasAlreadyChanged() {
        val p=plan()
        val stranded=p.copy(actions=listOf(
            p.actions.single().copy(id="evening",scheduledDateTime=time.minusDays(40).plusHours(1)),
            p.actions.single().copy(id="night",scheduledDateTime=time.minusDays(40).plusHours(4))
        ))
        assertTrue(repo.commit(stranded))
        assertTrue(repo.changePeriod(p.id,p,repairOutsideDates=true))
        assertEquals(time.plusHours(1),repo.getPlan(p.id)!!.actions.first().scheduledDateTime)
        assertEquals(time.plusHours(4),repo.getPlan(p.id)!!.actions.last().scheduledDateTime)
    }

    @Test fun shiftedPlanRepairsOnlyStrandedEntriesAndPreservesMetadata() {
        val p=plan()
        val normal=p.actions.single()
        val stale=normal.copy(id="stranded",scheduledDateTime=time.minusMonths(2).plusHours(1),completionState=ActionCompletionState.COMPLETE)
        assertTrue(repo.commit(p.copy(actions=listOf(normal,stale))))
        val edited=moved(p,8)
        assertTrue(repo.changePeriod(p.id,edited,true))
        val saved=repo.getPlan(p.id)!!
        assertEquals(normal.copy(scheduledDateTime=normal.scheduledDateTime.plusDays(8)),saved.actions.first())
        assertEquals(stale.copy(scheduledDateTime=time.plusDays(8).plusHours(1)),saved.actions.last())
    }
    @Test fun repairCoversLegacyRangeAndFinalPointWithoutChangingDuration() {
        val p=plan()
        val stale=p.midwayEvents.single().copy(timeSpec=EventDateTimeSpec.Range(DateTimeValue.explicit(time.minusDays(50)),DateTimeValue.explicit(time.minusDays(50).plusHours(8))))
        val final=stale.copy(id="final",kind=TimeEventKind.FINAL,order=1,timeSpec=EventDateTimeSpec.Single(DateTimeValue.explicit(time.minusDays(50).plusHours(9))))
        val dirty=DateTimePlanRules.normalizeTopology(p.copy(midwayEvents=listOf(stale),finalPoint=final,links=emptyList()))
        assertTrue(repo.commit(dirty));assertTrue(repo.changePeriod(p.id,p,true))
        val saved=repo.getPlan(p.id)!!
        assertEquals(time,DateTimePlanRules.arrival(saved.midwayEvents.single().timeSpec))
        assertEquals(time.plusHours(8),DateTimePlanRules.departure(saved.midwayEvents.single().timeSpec))
        assertEquals(time.plusHours(9),DateTimePlanRules.arrival(saved.finalPoint!!.timeSpec))
        assertTrue(saved.midwayEvents.single().dateTimeLocked)
    }
    @Test fun multiDayAmbiguityIsReportedAndNeverGuessed() {
        val p=plan();val stale=p.actions.single().copy(scheduledDateTime=time.minusDays(40).plusHours(4))
        val dirty=p.copy(end=DateTimeAnchor(DateTimeValue.explicit(time.plusDays(3))),actions=listOf(stale))
        assertTrue(repo.commit(dirty))
        val preview=TimePlanPeriodChanges.previewRepairs(dirty,dirty)
        assertEquals(1,preview.size);assertNull(preview.single().target)
        assertTrue(repo.changePeriod(p.id,dirty,true))
        assertEquals(stale,repo.getPlan(p.id)!!.actions.single())
    }
    @Test fun normalPeriodEditDoesNotSilentlyReinterpretIntentionalOutsideEntries() {
        val p=plan();val outside=p.actions.single().copy(scheduledDateTime=time.minusDays(3))
        assertTrue(repo.commit(p.copy(actions=listOf(outside))))
        assertTrue(repo.changePeriod(p.id,moved(p,2)))
        assertEquals(outside.scheduledDateTime.plusDays(2),repo.getPlan(p.id)!!.actions.single().scheduledDateTime)
    }
    @Test fun repairUsesLatestEntriesAndCompletionRatherThanEditorSnapshot() {
        val p=plan();val outside=p.actions.single().copy(scheduledDateTime=time.minusDays(8).plusHours(2))
        assertTrue(repo.saveAction(p.id,outside))
        assertTrue(repo.setUnifiedCompletion(p.id,"A:${outside.id}",ActionCompletionState.COMPLETE))
        assertTrue(repo.changePeriod(p.id,p,true))
        val saved=repo.getPlan(p.id)!!.actions.single()
        assertEquals(time.plusHours(2),saved.scheduledDateTime)
        assertEquals(ActionCompletionState.COMPLETE,saved.completionState)
    }

    @Test fun repairedDateReplacesTheAlarmReservationWithTheCorrectDay() {
        val app=ApplicationProvider.getApplicationContext<ArmyristApplication>()
        org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val p=plan()
        val stale=p.actions.single().copy(scheduledDateTime=time.minusDays(40).plusHours(4))
        assertTrue(repo.saveAction(p.id,stale))
        assertTrue(repo.changePeriod(p.id,p,true))
        val alarm=org.robolectric.Shadows.shadowOf(app.getSystemService(android.app.AlarmManager::class.java)).scheduledAlarms.single()
        assertEquals(time.plusHours(4).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),alarm.triggerAtTime)
        assertEquals("night",org.robolectric.Shadows.shadowOf(alarm.operation).savedIntent.getStringExtra(com.seolhwa.armyrist.notification.TimePlanActionNotificationManager.EXTRA_ACTION_ID))
    }

}
