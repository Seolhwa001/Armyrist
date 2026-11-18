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
}
