package com.seolhwa.armyrist.timeplan.v3

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.seolhwa.armyrist.*
import com.seolhwa.armyrist.collection.*
import com.seolhwa.armyrist.timeplan.v3.ui.*
import com.seolhwa.armyrist.trash.*
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
class PlanListSelectionUiTest {
    @get:Rule val compose=createComposeRule()
    private val app get()=ApplicationProvider.getApplicationContext<ArmyristApplication>()
    private fun setup(folder:Boolean=false):List<String>{
        val repo=app.dateAwareTimePlanRepository
        val ids=(1..8).map { n -> repo.createPlan()!!.let { p -> assertTrue(repo.commit(p.copy(title="검수 계획 $n")));p.id } }
        assertTrue(repo.setPlanOrder(ids))
        if(folder)assertNotNull(CommonCollectionRepository(app).createFolder(CollectionToolType.TIME_PLAN,"당직",ids))
        compose.setContent{ArmyristTheme{DateAwareTimePlanApp(repo,app.timePlanV2Repository,app.coreSuiteRepository,app.commonTrashRepository,{},onOpenExecution={_,_,_->})}}
        if(folder)compose.onNodeWithText("당직").performClick()
        return ids
    }
    private fun paint(from:Int,to:Int){
        val start=compose.onNodeWithContentDescription("시간계획 선택 검수 계획 $from")
        val end=compose.onNodeWithContentDescription("시간계획 선택 검수 계획 $to")
        val delta=end.fetchSemanticsNode().boundsInRoot.center.y-start.fetchSemanticsNode().boundsInRoot.center.y
        start.performTouchInput{down(center);advanceEventTime(650);moveBy(Offset(0f,delta));advanceEventTime(50);up()}
    }
    @Test fun dragSelectsCrossedPlansAndBatchTrashPreservesOthers(){
        val ids=setup();paint(1,3)
        compose.onNodeWithText("선택 3").assertIsDisplayed()
        compose.runOnIdle{
            val view=android.view.inspector.WindowInspector.getGlobalWindowViews().last()
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            java.io.File("build/preview-screens/plan-list-selection.png").also{it.parentFile!!.mkdirs()}.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
        compose.onNodeWithContentDescription("선택한 시간계획 휴지통으로 이동").performClick()
        compose.onNodeWithText("취소").performClick()
        compose.runOnIdle{assertEquals(8,app.dateAwareTimePlanRepository.getPlans().size)}
        compose.onNodeWithContentDescription("선택한 시간계획 휴지통으로 이동").performClick()
        compose.onNodeWithText("휴지통으로 이동").performClick()
        compose.runOnIdle{
            assertEquals(ids.drop(3).toSet(),app.dateAwareTimePlanRepository.getPlans().map{it.id}.toSet())
            val trash=app.commonTrashRepository.getItems(TrashToolType.TIME_PLAN)
            assertEquals(ids.take(3).toSet(),trash.map{it.originalId}.toSet())
            assertTrue(trash.all{it.payload.contains("검수 계획")})
        }
    }
    @Test fun upwardSelectionInsideFolderCanBeDeselectedByDragging(){
        setup(true);paint(3,1)
        compose.onNodeWithText("선택 3").assertIsDisplayed()
        paint(1,2)
        compose.onNodeWithText("선택 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("시간계획 선택 검수 계획 3").assertIsSelected()
        compose.onNodeWithContentDescription("시간계획 선택 검수 계획 1").assertIsNotSelected()
    }
    @Test fun stationaryLongPressSelectsWithoutOpeningPlan(){
        setup()
        compose.onNodeWithContentDescription("시간계획 선택 검수 계획 1").performTouchInput{longClick()}
        compose.onNodeWithText("선택 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("시간계획 목록").assertIsDisplayed()
    }
}
