package com.seolhwa.armyrist.timeplan.v3

import androidx.test.core.app.ApplicationProvider
import com.seolhwa.armyrist.ArmyristApplication
import com.seolhwa.armyrist.calendar.*
import com.seolhwa.armyrist.timeplan.v3.data.*
import com.seolhwa.armyrist.timeplan.v3.domain.*
import com.seolhwa.armyrist.timeplan.v3.portable.TimePlanPortableV3Codec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=ArmyristApplication::class)
class IndependentActionsTest {
    private val start=LocalDateTime.of(2030,10,23,22,0)
    private val split=start.plusHours(8).plusMinutes(30)
    private fun plan():DateAwareTimePlan {
        fun event(id:String,a:LocalDateTime,b:LocalDateTime,color:String,order:Int)=DateTimeEvent(id,TimeEventKind.MIDWAY,order,id,EventDateTimeSpec.Range(DateTimeValue.explicit(a),DateTimeValue.explicit(b)),color=color)
        return DateTimePlanRules.normalizeTopology(DateAwareTimePlan("independent","당직",start=DateTimeAnchor(DateTimeValue.explicit(start)),end=DateTimeAnchor(DateTimeValue.explicit(split.plusHours(2))),
            midwayEvents=listOf(event("야간",start,split,"#7543A3",0),event("아침",split,split.plusHours(2),"#147850",1)),
            actions=listOf(TimePlanActionItem(id="patrol",parentPointId="야간",content="순찰",scheduledDateTime=split),TimePlanActionItem(id="other",parentPointId="deleted-old-id",content="별도 점검",scheduledDateTime=start.minusHours(1))),createdAt="1",updatedAt="1")).let(TimePlanExecutionRules::normalizeActionOrder)
    }
    @Test fun midnightAndExactBoundaryUseTimestampInsteadOfLegacyParent(){
        val p=plan()
        assertEquals("야간",TimePlanScheduleDisplay.eventAt(p,start.plusHours(2))?.id)
        assertEquals("야간",TimePlanScheduleDisplay.eventAt(p,split.minusNanos(1))?.id)
        assertEquals("아침",TimePlanScheduleDisplay.eventAt(p,split)?.id)
        assertNull(TimePlanScheduleDisplay.eventAt(p,split.plusHours(2)))
        val row=CalendarAgenda.entriesOn(split.toLocalDate(),listOf(p),emptyList()).single{it.itemId=="patrol"}
        assertEquals("아침",row.displayEventId);assertEquals("#147850",row.color)
        assertEquals("야간",p.actions.first{it.id=="patrol"}.parentPointId)
    }
    @Test fun movingSchedulesRegroupsDisplayWithoutChangingActions(){
        val p=plan();val changed=p.copy(midwayEvents=p.midwayEvents.map{if(it.id=="아침")it.copy(timeSpec=EventDateTimeSpec.Range(DateTimeValue.explicit(split.plusMinutes(1)),DateTimeValue.explicit(split.plusHours(2))))else it})
        assertEquals(p.actions,changed.actions)
        val row=CalendarAgenda.entriesOn(split.toLocalDate(),listOf(changed),emptyList()).single{it.itemId=="patrol"}
        assertNull(row.displayEventId);assertEquals("당직",row.ownerTitle)
    }
    @Test fun deletingScheduleKeepsActionsAndAlarmDataAfterReload(){
        val app=ApplicationProvider.getApplicationContext<ArmyristApplication>();val repo=app.dateAwareTimePlanRepository
        val p=plan();assertTrue(repo.commit(p))
        val a=p.actions.first{it.id=="patrol"}.copy(notificationMode=ActionNotificationMode.SIMPLE,notificationEnabled=true,note="보존",completionState=ActionCompletionState.COMPLETE)
        assertTrue(repo.saveAction(p.id,a));assertTrue(repo.completeActionFromNotification(p.id,a.id));val expected=repo.getPlan(p.id)!!.actions.first{it.id==a.id};assertTrue(repo.deleteEvent(p.id,"야간"))
        assertEquals(expected,DateAwareTimePlanRepository(app).getPlan(p.id)!!.actions.first{it.id==a.id})
        assertTrue(repo.saveAction(p.id,TimePlanActionItem(content="독립 추가",scheduledDateTime=split)))
    }
    @Test fun batchDeletingSchedulesOnlyDeletesExplicitlySelectedActions(){
        val p=plan()
        assertEquals(p.actions,TimelineSelection.remove(p,setOf("N:야간")).actions)
        val changed=TimelineSelection.remove(p,setOf("N:야간","A:patrol"))
        assertEquals(listOf("other"),changed.actions.map{it.id})
    }
    @Test fun overlapHasDeterministicSingleDisplayOwner(){
        val p=plan();val overlap=p.midwayEvents.first().copy(id="late",order=2,timeSpec=EventDateTimeSpec.Range(DateTimeValue.explicit(start.plusHours(1)),DateTimeValue.explicit(split)))
        val changed=p.copy(midwayEvents=p.midwayEvents+overlap)
        assertEquals("late",TimePlanScheduleDisplay.eventAt(changed,start.plusHours(2))?.id)
        assertEquals("late",TimePlanScheduleDisplay.eventAt(changed.copy(midwayEvents=changed.midwayEvents.reversed()),start.plusHours(2))?.id)
    }
    @Test fun colorsAndIndependentActionsSurviveBackupAndLegacyDefaults(){
        val p=plan();val restored=TimePlanPortableV3Codec.decode(TimePlanPortableV3Codec.encode(p))
        assertEquals(p,restored)
        val json=DateAwareTimePlanJson.encode(p);json.getJSONArray("midwayEvents").getJSONObject(0).remove("color")
        assertEquals("#7543A3",DateAwareTimePlanJson.decode(json).midwayEvents.first().color)
        json.getJSONArray("actions").getJSONObject(0).remove("parentPointId")
        assertEquals(p.actions.size,DateAwareTimePlanJson.decode(json).actions.size)
    }
}
