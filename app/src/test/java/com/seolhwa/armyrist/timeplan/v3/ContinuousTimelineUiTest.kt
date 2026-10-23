package com.seolhwa.armyrist.timeplan.v3
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.seolhwa.armyrist.*
import com.seolhwa.armyrist.home.*
import com.seolhwa.armyrist.timeplan.v3.data.*
import com.seolhwa.armyrist.timeplan.v3.domain.*
import com.seolhwa.armyrist.timeplan.v3.ui.*
import com.seolhwa.armyrist.trash.*
import java.time.LocalDateTime
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w360dp-h800dp",application=ArmyristApplication::class)
class ContinuousTimelineUiTest {
    @get:Rule val compose=createComposeRule()
    private val app get()=ApplicationProvider.getApplicationContext<ArmyristApplication>()
    private fun plan():DateAwareTimePlan{
        val repo=app.dateAwareTimePlanRepository;val start=LocalDateTime.of(2030,1,2,8,0);val base=repo.createPlan(start)!!
        val a=DateTimeEvent("morning",TimeEventKind.MIDWAY,0,"오전 근무",EventDateTimeSpec.Range(DateTimeValue.explicit(start.plusMinutes(30)),DateTimeValue.explicit(start.plusMinutes(90))))
        val b=DateTimeEvent("patrol",TimeEventKind.MIDWAY,1,"순찰",EventDateTimeSpec.Range(DateTimeValue.explicit(start.plusHours(2)),DateTimeValue.explicit(start.plusHours(3))))
        val action=TimePlanActionItem(id="check",parentPointId=a.id,content="장비 점검",scheduledDateTime=start.plusHours(1))
        val p=DateTimePlanRules.normalizeTopology(base.copy(end=DateTimeAnchor(DateTimeValue.explicit(start.plusHours(5))),midwayEvents=listOf(a,b),actions=listOf(action)))
        assertTrue(repo.commit(p));return p
    }
    private fun show(plan:DateAwareTimePlan){compose.setContent{ArmyristTheme{DateAwareTimePlanApp(app.dateAwareTimePlanRepository,app.timePlanV2Repository,app.coreSuiteRepository,app.commonTrashRepository,{},initialPlanId=plan.id,onOpenExecution={_,_,_->})}}}
    @Test fun markerShowsActionAndColorPersists(){
        val p=plan();show(p)
        compose.onNodeWithContentDescription("실시사항 장비 점검 09:00").performClick()
        compose.onAllNodesWithText("장비 점검").onLast().assertIsDisplayed();compose.onNodeWithText("点色").assertDoesNotExist()
        compose.onNodeWithContentDescription("점 색상 #16845D").performClick()
        compose.runOnIdle{assertEquals("#16845D",DateAwareTimePlanRepository(app).getPlan(p.id)!!.actions.single().markerColor)}
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithText("점 + 실시사항").performClick()
        screenshot("continuous-timeline.png")
    }
    @Test fun swipeOpensAllActionSummary(){
        val p=plan();show(p)
        compose.onNodeWithContentDescription("시간계획 시간축").performTouchInput{swipeLeft()}
        compose.onNodeWithText("전체 일정").assertIsDisplayed();compose.onNodeWithText("장비 점검").assertIsDisplayed()
        screenshot("continuous-summary.png")
        compose.onNodeWithContentDescription("실시사항 요약 패널").performTouchInput{swipeRight()};compose.onNodeWithText("전체 일정").assertDoesNotExist()
    }
    @Test fun contentsModeIsReadableAndPreferenceIsSaved(){
        val p=plan();show(p)
        compose.onNodeWithText("점 + 실시사항").performClick()
        compose.onNodeWithText("장비 점검",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithText("점만").performClick()
        compose.onNodeWithText("장비 점검",useUnmergedTree=true).assertDoesNotExist()
        compose.runOnIdle{assertFalse(app.getSharedPreferences("timeline_display",0).getBoolean("show_contents",true))}
        compose.onNodeWithText("점 + 실시사항").performClick()
        compose.runOnIdle{assertTrue(app.getSharedPreferences("timeline_display",0).getBoolean("show_contents",false))}
    }
    @Test fun crowdedActionsRemainIndividuallyReachable(){
        val p=plan()
        val actions=listOf(0L,1L,2L,2L).mapIndexed{i,minute->TimePlanActionItem(id="crowd$i",parentPointId="morning",content="가까운 항목 $i",scheduledDateTime=p.actions.single().scheduledDateTime.plusMinutes(minute))}
        val changed=p.copy(actions=actions);assertTrue(app.dateAwareTimePlanRepository.commit(changed));show(changed)
        compose.onNodeWithText("점 + 실시사항").performClick()
        compose.onNodeWithContentDescription("실시사항 가까운 항목 1 09:01").performScrollTo().performClick()
        compose.onAllNodesWithText("가까운 항목 1").onLast().assertIsDisplayed()
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithContentDescription("실시사항 가까운 항목 2, 가까운 항목 3 09:02").performScrollTo().performClick()
        compose.onAllNodesWithText("가까운 항목 3").onLast().assertIsDisplayed()
    }
    @Test fun horizontalTextEditingDoesNotDismissUnsavedAction(){
        val p=plan();show(p)
        compose.onNodeWithContentDescription("실시사항 요약 열기").performClick()
        compose.onNodeWithText("장비 점검").performClick()
        compose.onNodeWithText("내용").performTextReplacement("점검 수정")
        compose.onNodeWithContentDescription("실시사항 요약 패널").performTouchInput{swipeRight()}
        compose.onNodeWithText("점검 수정").assertExists()
        compose.onNodeWithText("저장").performClick()
        compose.runOnIdle{assertEquals("점검 수정",app.dateAwareTimePlanRepository.getPlan(p.id)!!.actions.single().content)}
        compose.onNodeWithContentDescription("실시사항 요약 패널").performTouchInput{swipeRight()}
        compose.onNodeWithText("전체 일정").assertDoesNotExist()
    }
    @Test fun longPressPaintAndTrashCanRestoreOriginalItems(){
        val p=plan();show(p)
        compose.onAllNodesWithContentDescription("일정 오전 근무").onFirst().performTouchInput{
            down(center);advanceEventTime(650);moveBy(Offset(0f,160f));advanceEventTime(50);up()
        }
        compose.onNodeWithText("휴지통").assertIsDisplayed().performClick()
        compose.onNodeWithText("휴지통으로").performClick()
        val repo=app.dateAwareTimePlanRepository
        compose.runOnIdle{
            assertTrue(repo.getPlan(p.id)!!.midwayEvents.none{it.id=="morning"})
            val entry=app.commonTrashRepository.getItems(TrashToolType.TIME_PLAN).single();assertEquals(TimelineTrash.VERSION,entry.payloadVersion)
            assertTrue(TimelineTrash.restore(repo,entry.payload));assertEquals(p.midwayEvents.map{it.id}.toSet(),repo.getPlan(p.id)!!.midwayEvents.map{it.id}.toSet())
        }
    }
    @Test fun listViewAlsoUsesOneSummaryButtonAtTheEdge(){
        val p=plan();show(p)
        compose.onNodeWithText("목록",useUnmergedTree=true).performClick()
        compose.onNodeWithContentDescription("시작 실시사항 열기").assertDoesNotExist()
        compose.onNodeWithContentDescription("실시사항 요약 열기").assertIsDisplayed().performClick()
        compose.onNodeWithText("전체 일정").assertIsDisplayed()
    }
    @Test @Config(qualifiers="w320dp-h480dp") fun compactHomeShowsEveryToolWithoutScrolling(){
        var opened=""
        compose.setContent{ArmyristTheme{CompactHome(0,HomeBannerConfig(compactHome=true),{opened=it},{},{})}}
        listOf("실셈","체크리스트","시간계획","보고 양식","핸디캡 상황","달력").forEach{compose.onNodeWithText(it,useUnmergedTree=true).assertIsDisplayed()}
        compose.onNodeWithText("시간계획").performClick();assertEquals("timeplan",opened)
        screenshot("compact-home-320.png")
        assertTrue(HomeBannerCodec.decode(HomeBannerCodec.encode(HomeBannerConfig(compactHome=true))).compactHome)
    }
    private fun screenshot(name:String)=compose.runOnIdle{
        val view=android.view.inspector.WindowInspector.getGlobalWindowViews().last();val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(image));val file=File("build/preview-screens/$name").also{it.parentFile!!.mkdirs()};file.outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
    }
}
