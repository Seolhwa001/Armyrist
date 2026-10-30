package com.seolhwa.armyrist.timeplan.v3
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.seolhwa.armyrist.*
import com.seolhwa.armyrist.timeplan.v3.data.*
import com.seolhwa.armyrist.timeplan.v3.domain.*
import com.seolhwa.armyrist.timeplan.v3.ui.DateAwareTimePlanApp
import com.seolhwa.armyrist.trash.TrashToolType
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime
import java.io.File
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w360dp-h800dp",application=ArmyristApplication::class)
class ContinuousTimelineUiTest {
 @get:Rule val compose=createComposeRule()
 private val app get()=ApplicationProvider.getApplicationContext<ArmyristApplication>()
 private val t=LocalDateTime.of(2030,11,5,9,0)
 private fun plan():DateAwareTimePlan{
  val r=app.dateAwareTimePlanRepository;val base=r.createPlan(t)!!
  val event=DateTimeEvent("old",TimeEventKind.MIDWAY,0,"기존 구간",EventDateTimeSpec.Range(DateTimeValue.explicit(t.plusMinutes(30)),DateTimeValue.explicit(t.plusMinutes(90))),note="구간 메모",color="#147850")
  val p=DateTimePlanRules.normalizeTopology(base.copy(end=DateTimeAnchor(DateTimeValue.explicit(t.plusHours(3))),midwayEvents=listOf(event),actions=listOf(TimePlanActionItem(id="a",content="장비 점검",scheduledDateTime=t.plusHours(3))),memo="메모 내용은 버튼 없이 보입니다.\n두 번째 줄도 표시합니다."))
  assertTrue(r.commit(p));return p
 }
 private fun show(p:DateAwareTimePlan){compose.setContent{ArmyristTheme{DateAwareTimePlanApp(app.dateAwareTimePlanRepository,app.timePlanV2Repository,app.coreSuiteRepository,app.commonTrashRepository,{},initialPlanId=p.id,onOpenExecution={_,_,_->})}}}
 @Test fun oldAndNewEntriesShareOneEditableList(){
  val p=plan();show(p)
  compose.onNodeWithText("기존 구간").assertIsDisplayed();compose.onNodeWithText("장비 점검").assertIsDisplayed()
  compose.onNodeWithText("장비 점검").performClick();compose.onNodeWithText("실시사항 편집").assertIsDisplayed()
  compose.onNodeWithText("내용").performTextReplacement("점검 수정");compose.onNodeWithText("직접입력").performClick();compose.onNodeWithText("저장").performClick()
  compose.runOnIdle{val a=app.dateAwareTimePlanRepository.getPlan(p.id)!!.actions.single();assertEquals("점검 수정",a.content);assertEquals(30L,a.durationMinutes);assertEquals(p.end.value.value,a.scheduledDateTime)}
 }
 @Test fun endpointItemsCanBeEditedInTimelineWithoutOpeningEndPoint(){
  val base=plan();val p=base.copy(midwayEvents=listOf(base.midwayEvents.single().copy(timeSpec=EventDateTimeSpec.Single(DateTimeValue.explicit(base.end.value.value!!)),name="종료시각 항목")))
  assertTrue(app.dateAwareTimePlanRepository.commit(p));show(p)
  compose.onNodeWithTag("timeplan-view-timeline").performClick()
  compose.onNodeWithTag("unified-timeline").performScrollToNode(hasContentDescription("실시사항 종료시각 항목 12:00"))
  compose.onNodeWithContentDescription("실시사항 종료시각 항목 12:00").performScrollTo().performClick()
  compose.onNodeWithText("실시사항 편집").assertIsDisplayed();compose.onNodeWithText("시간계획 기간").assertDoesNotExist()
  compose.onNodeWithText("내용").performTextReplacement("정상 편집");compose.onNodeWithText("저장").performClick()
  compose.runOnIdle{assertEquals("정상 편집",app.dateAwareTimePlanRepository.getPlan(p.id)!!.midwayEvents.single().name);assertEquals(p.end,app.dateAwareTimePlanRepository.getPlan(p.id)!!.end)}
 }
 @Test fun quickAddNeedsNoAttachmentAndSupportsPointOrDuration(){
  val p=plan();show(p)
  compose.onNodeWithText("+ 실시사항").performClick()
  compose.onNodeWithText("연결할 일정").assertDoesNotExist();compose.onNodeWithText("시각만").assertIsDisplayed()
  compose.onNodeWithText("내용").performTextInput("빠른 추가");compose.onNodeWithText("직접입력").performClick();compose.onNodeWithText("저장").performClick()
  compose.runOnIdle{assertEquals(30L,app.dateAwareTimePlanRepository.getPlan(p.id)!!.actions.single{it.content=="빠른 추가"}.durationMinutes)}
 }
 @Test fun viewButtonsStayVisibleAndSelectedModeSurvivesReentry(){
  val p=plan();show(p)
  compose.onNodeWithTag("timeplan-view-list").assertIsDisplayed().assertIsSelected()
  compose.onNodeWithTag("timeplan-view-timeline").performClick().assertIsSelected();compose.onNodeWithTag("timeplan-view-list").assertIsDisplayed()
  compose.onNodeWithText("메뉴").performClick();compose.onNodeWithText(p.title).performClick()
  compose.onNodeWithTag("timeplan-view-timeline").assertIsSelected();compose.onNodeWithTag("timeplan-view-list").performClick().assertIsSelected()
 }
 @Test fun memoAndItemNotesAreVisibleWithoutOpeningEditor(){
  val p=plan();show(p)
  compose.onNodeWithText("구간 메모").assertIsDisplayed()
  compose.onNodeWithTag("unified-entry-list").performScrollToNode(hasText(p.memo!!))
  compose.onNodeWithText(p.memo!!).assertIsDisplayed();screenshot("unified-list-087.png")
  compose.onNodeWithTag("timeplan-view-timeline").performClick()
  compose.onNodeWithTag("unified-timeline").performScrollToNode(hasTestTag("timeplan-inline-memo"))
  compose.onNodeWithText(p.memo!!).assertIsDisplayed();screenshot("unified-timeline-087.png")
 }
 @Test fun longPressPaintAndTrashKeepUnselectedRecordsAndCanRestore(){
  val p=plan();show(p)
  compose.onNodeWithContentDescription("실시사항 기존 구간 09:30").performTouchInput{down(center);advanceEventTime(650);moveBy(Offset(0f,130f));advanceEventTime(50);up()}
  compose.onNodeWithContentDescription("선택한 실시사항 휴지통으로 이동").assertIsEnabled().performClick()
  compose.onNodeWithText("휴지통으로").performClick()
  compose.runOnIdle{val item=app.commonTrashRepository.getItems(TrashToolType.TIME_PLAN).single();assertTrue(TimelineTrash.restore(app.dateAwareTimePlanRepository,item.payload));assertEquals(2,UnifiedTimePlan.entries(app.dateAwareTimePlanRepository.getPlan(p.id)!!).size)}
 }
 @Test fun editingColorOfLegacyRangePreservesItsTimesAndMemo(){
  val p=plan();show(p);compose.onNodeWithText("기존 구간").performClick()
  compose.onNodeWithText("메모 · 색상 · 알림 ∨").performScrollTo().performClick()
  compose.onNodeWithContentDescription("실시사항 색상 #7543A3").performScrollTo().performClick();compose.onNodeWithText("저장").performClick()
  compose.runOnIdle{val saved=app.dateAwareTimePlanRepository.getPlan(p.id)!!.midwayEvents.single();assertEquals("#7543A3",saved.color);assertEquals(p.midwayEvents.single().timeSpec,saved.timeSpec);assertEquals("구간 메모",saved.note)}
 }
 @Test fun horizontalTextGestureDoesNotDismissUnsavedEditor(){
  val p=plan();show(p);compose.onNodeWithText("장비 점검").performClick()
  compose.onNodeWithText("내용").performTextReplacement("저장 전 내용");compose.onNodeWithText("내용").performTouchInput{swipeLeft()}
  compose.onNodeWithText("저장 전 내용").assertExists();compose.onNodeWithText("저장").performClick()
  compose.runOnIdle{assertEquals("저장 전 내용",app.dateAwareTimePlanRepository.getPlan(p.id)!!.actions.single().content)}
 }
 @Test fun rangeTitleAppearsOnlyAtItsStart(){
  val p=plan();show(p);compose.onNodeWithTag("timeplan-view-timeline").performClick()
  compose.onAllNodesWithText("기존 구간",useUnmergedTree=true).assertCountEquals(1)
  compose.onNodeWithText("09:30–10:30",useUnmergedTree=true).assertIsDisplayed()
 }
 @Test fun relativeDayLabelsRemainVisibleInBothViews(){
  val p=plan().copy(dateDisplayMode=TimePlanDateDisplayMode.RELATIVE_D_DAY)
  assertTrue(app.dateAwareTimePlanRepository.commit(p));show(p)
  compose.onNodeWithText("D-Day").assertIsDisplayed()
  compose.onNodeWithTag("timeplan-view-timeline").performClick()
  compose.onNodeWithText("D-Day").assertIsDisplayed()
 }
 @Test fun endClockEntrySavesDurationAndShowsFamiliarFields(){
  val p=plan();show(p)
  compose.onNodeWithText("+ 실시사항").performClick()
  compose.onNodeWithText("내용").performTextInput("종료시각으로 추가")
  compose.onNodeWithText("종료시간 선택").performClick()
  compose.onNodeWithText("종료",substring=false).performScrollTo().assertIsDisplayed()
  compose.onNodeWithText("총 30분").performScrollTo().assertIsDisplayed()
  screenshot("entry-clock-088.png")
  compose.onNodeWithText("저장").performClick()
  compose.runOnIdle{assertEquals(30L,app.dateAwareTimePlanRepository.getPlan(p.id)!!.actions.single{it.content=="종료시각으로 추가"}.durationMinutes)}
 }
 @Test fun explicitDurationEditUnlocksLegacyPinnedEventAndSaves(){
  val base=plan();val p=base.copy(midwayEvents=listOf(base.midwayEvents.single().copy(dateTimeLocked=true)))
  assertTrue(app.dateAwareTimePlanRepository.commit(p));show(p)
  compose.onNodeWithText("기존 구간").performClick()
  compose.onNodeWithText("15분").assertDoesNotExist();compose.onNodeWithText("30분").assertDoesNotExist();compose.onNodeWithText("1시간",substring=false).assertDoesNotExist()
  compose.onNodeWithText("직접입력").performClick()
  compose.onNodeWithText("시간",substring=false).performScrollTo().performTextReplacement("2")
  compose.onNodeWithText("저장").performClick()
  compose.onNodeWithText("실시사항 편집").assertDoesNotExist()
  compose.runOnIdle{val e=app.dateAwareTimePlanRepository.getPlan(p.id)!!.midwayEvents.single();assertFalse(e.dateTimeLocked);assertEquals(120L,java.time.Duration.between(DateTimePlanRules.arrival(e.timeSpec),DateTimePlanRules.departure(e.timeSpec)).toMinutes())}
 }
 private fun screenshot(name:String){val view=android.view.inspector.WindowInspector.getGlobalWindowViews().last();val b=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(b));File("build/preview-screens/$name").also{it.parentFile!!.mkdirs()}.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
}
