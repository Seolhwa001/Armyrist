package com.seolhwa.armyrist.calendar
import androidx.test.core.app.ApplicationProvider
import com.seolhwa.armyrist.ArmyristApplication
import com.seolhwa.armyrist.notification.ChecklistNotificationManager
import com.seolhwa.armyrist.stage2.data.CoreSuiteRepository
import com.seolhwa.armyrist.timeplan.v3.domain.*
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=ArmyristApplication::class)
class CalendarPersistenceUiTest {
    @Test fun futureDateDoesNotCollapseToTodayAndPastDoesNotRollForward(){
        val day=LocalDate.of(2030,1,2);val now=day.minusDays(2).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val expected=day.atTime(9,30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expected,ChecklistNotificationManager.scheduledEpochMillis(570,now,day.toString()))
        assertNull(ChecklistNotificationManager.scheduledEpochMillis(570,expected+1,day.toString()))
        assertNull(ChecklistNotificationManager.scheduledEpochMillis(570,now,"invalid"))
    }
    @Test fun datedItemSurvivesRepositoryReload(){
        val app=ApplicationProvider.getApplicationContext<ArmyristApplication>();val repo=app.coreSuiteRepository
        val before=repo.getChecklists().map{it.id}.toSet()
        assertTrue(repo.saveCalendarItem(null,"새 목록","점검","메모",false,570,"2030-01-02"))
        val list=repo.getChecklists().single{it.id !in before};val loaded=CoreSuiteRepository(app).getChecklist(list.id)!!
        assertEquals("2030-01-02",loaded.items.single().scheduledDate);assertEquals(570,loaded.items.single().scheduledTimeMinutes)
        assertFalse(loaded.items.single().notificationEnabled)
    }
    @Test fun editingStaleActionPreservesNotificationCompletion(){
        val app=ApplicationProvider.getApplicationContext<ArmyristApplication>();val repo=app.dateAwareTimePlanRepository
        val plan=repo.createPlan()!!;val action=TimePlanActionItem(parentPointId="START",content="점검",scheduledDateTime=LocalDate.now().plusDays(1).atTime(9,0))
        assertTrue(repo.saveAction(plan.id,action));assertTrue(repo.completeActionFromNotification(plan.id,action.id))
        assertTrue(repo.saveAction(plan.id,action.copy(content="수정 점검")))
        val current=repo.getPlan(plan.id)!!.actions.single();assertEquals("수정 점검",current.content);assertEquals(ActionCompletionState.COMPLETE,current.completionState)
        assertTrue(repo.saveAction(plan.id,action.copy(parentPointId="missing")));assertEquals(ActionCompletionState.COMPLETE,repo.getPlan(plan.id)!!.actions.single().completionState)
    }
}
