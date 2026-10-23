package com.seolhwa.armyrist.calendar
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.seolhwa.armyrist.*
import com.seolhwa.armyrist.timeplan.v3.domain.*
import com.seolhwa.armyrist.timeplan.v3.ui.ScheduleActionTab
import com.seolhwa.armyrist.timeplan.v3.ui.ScheduleActionWorkspace
import java.io.File
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w360dp-h800dp",application=ArmyristApplication::class)
class CalendarScreensUiTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var app:ArmyristApplication
    private val date=LocalDate.of(2030,1,2)
    @Before fun setup(){app=ApplicationProvider.getApplicationContext()}
    @Test fun calendarAddsPlanOnSelectedDayAndCancelDoesNotCreateRecords(){
        val repo=app.dateAwareTimePlanRepository;val before=repo.getPlans().map{it.id}.toSet()
        compose.setContent{ArmyristTheme{CalendarScreen(0,date.toString(),{}, {_,_->})}}
        compose.onNodeWithText("+ 일정 추가").performClick()
        compose.onNodeWithText("+ 시간계획").performClick()
        compose.onNodeWithText("이름").performTextInput("달력 검증 계획")
        screenshot("calendar-plan-editor.png")
        compose.onNodeWithText("저장").performClick()
        compose.runOnIdle{val p=repo.getPlans().single{it.id !in before};assertEquals(date,p.start.value.value!!.toLocalDate());assertEquals("달력 검증 계획",p.title)}
        compose.onNodeWithText("달력 검증 계획").assertIsDisplayed()
        compose.onNodeWithText("+ 일정 추가").performClick();compose.onNodeWithText("+ 시간계획").performClick();compose.onNodeWithText("취소").performClick()
        compose.runOnIdle{assertEquals(before.size+1,repo.getPlans().size)}
        screenshot("calendar-agenda.png")
    }
    @Test fun checklistFullScreenEditorSavesScheduledDateWithoutAlarm(){
        val repo=app.coreSuiteRepository;assertTrue(repo.saveCalendarItem(null,"달력 검증 목록","원래 항목","",false,570,date.toString()))
        val list=repo.getChecklists().first{it.title=="달력 검증 목록"};val item=list.items.single()
        var saved=false
        compose.setContent{ArmyristTheme{CalendarEntryEditor(AgendaKind.CHECKLIST,date,AgendaEntry(AgendaKind.CHECKLIST,list.id,item.id,item.name,list.title,date.atTime(9,30)),{},{saved=true})}}
        compose.onNodeWithText("이름").performTextClearance();compose.onNodeWithText("이름").performTextInput("변경된 점검")
        compose.onNodeWithText("저장").performClick()
        compose.runOnIdle{assertTrue(saved);val current=repo.getChecklist(list.id)!!.items.single();assertEquals("변경된 점검",current.name);assertEquals(date.toString(),current.scheduledDate);assertFalse(current.notificationEnabled)}
    }
    @Test fun schedulePanelEditsInlineAndKeepsCompletion(){
        val repo=app.dateAwareTimePlanRepository;val p=repo.createPlan(date.atTime(9,0))!!
        val action=TimePlanActionItem(parentPointId="START",content="패널 점검",scheduledDateTime=date.atTime(9,0),completionState=ActionCompletionState.COMPLETE)
        assertTrue(repo.saveAction(p.id,action))
        compose.setContent{ArmyristTheme{var revision by remember{mutableIntStateOf(0)};val plan=remember(revision){repo.getPlan(p.id)!!};ScheduleActionWorkspace(plan,false,{repo.saveAction(p.id,it).also{ok->if(ok)revision++}}){open->Column(Modifier.fillMaxSize()){ScheduleActionTab(plan,"START",false,{open("START")}){Text("일정")}}}}}
        compose.onNodeWithContentDescription("시작 실시사항 열기").performClick()
        compose.onNodeWithText("패널 점검").performClick()
        compose.onNodeWithText("내용").performTextClearance();compose.onNodeWithText("내용").performTextInput("패널 수정")
        screenshot("schedule-inline-editor.png")
        compose.onNodeWithText("저장").performClick()
        compose.onNodeWithText("패널 수정").assertIsDisplayed()
        compose.runOnIdle{assertEquals(ActionCompletionState.COMPLETE,repo.getPlan(p.id)!!.actions.single().completionState)}
        screenshot("schedule-action-panel.png")
    }
    @Test fun actualTimelineOpensHalfScreenPanel() {
        val repo=app.dateAwareTimePlanRepository;val base=repo.createPlan(date.atTime(9,0))!!
        val plan=DateTimePlanRules.normalizeTopology(base.copy(start=base.start.copy(dateTimeLocked=true),end=base.end.copy(dateTimeLocked=true),midwayEvents=listOf(DateTimeEvent("mid",TimeEventKind.MIDWAY,0,"오후 일정",EventDateTimeSpec.Range(DateTimeValue.explicit(date.atTime(9,20)),DateTimeValue.explicit(date.atTime(9,40))),dateTimeLocked=true))))
        assertTrue(repo.commit(plan))
        compose.setContent{ArmyristTheme{com.seolhwa.armyrist.timeplan.v3.ui.DateAwareTimePlanApp(repo,app.timePlanV2Repository,app.coreSuiteRepository,app.commonTrashRepository,{},initialPlanId=plan.id,onOpenExecution={_,_,_->})}}
        screenshot("actual-timeline-full.png")
        compose.onNodeWithContentDescription("실시사항 요약 열기").performClick()
        compose.onNodeWithText("+ 추가").assertIsDisplayed()
        screenshot("actual-timeline-panel.png")
        compose.onNodeWithText("닫기 ›").performClick()
        compose.onNodeWithText("+ 추가").assertDoesNotExist()
    }
    @Test @Config(qualifiers="w320dp-h480dp") fun clockAndDatePickersWorkOnSmallScreen() {
        var value=date.atTime(9,0)
        compose.setContent{ArmyristTheme{var time by remember{mutableStateOf(value)};ScheduleDateTimeFields(time,{time=it;value=it})}}
        compose.onNodeWithText("시간 선택").performClick()
        compose.onNodeWithText("적용").assertIsDisplayed()
        screenshot("small-clock.png")
        compose.onNodeWithText("숫자로 입력").performClick()
        compose.onNodeWithText("적용").performClick()
        compose.onNodeWithText("날짜 선택").performClick()
        compose.onNodeWithText("적용").assertIsDisplayed().performClick()
        compose.runOnIdle{assertEquals(date,value.toLocalDate())}
    }
    private fun screenshot(name:String)=compose.runOnIdle{
        val view=android.view.inspector.WindowInspector.getGlobalWindowViews().last();val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
        val file=File("build/preview-screens/$name").also{it.parentFile!!.mkdirs()};file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
}
