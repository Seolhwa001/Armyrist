package com.seolhwa.armyrist.calendar

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.geometry.Offset
import com.seolhwa.armyrist.ArmyristTheme
import com.seolhwa.armyrist.ArmyristApplication
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.YearMonth

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w360dp-h800dp",application=ArmyristApplication::class)
class CalendarSwipeUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun leftAndRightSwipeCrossYearWithoutSelectingADay(){
        var month by mutableStateOf(YearMonth.of(2030,12));var selected:LocalDate?=null
        compose.setContent{ArmyristTheme{MonthCalendar(month,LocalDate.of(2030,12,31),emptyList(),emptyList(),{month=it},{selected=it})}}
        compose.onNodeWithTag("month-calendar").performTouchInput{swipeLeft()}
        compose.runOnIdle{assertEquals(YearMonth.of(2031,1),month);assertNull(selected)}
        compose.onNodeWithTag("month-grid-2031-01").assertExists()
        compose.onNodeWithTag("month-calendar").performTouchInput{swipeRight()}
        compose.runOnIdle{assertEquals(YearMonth.of(2030,12),month);assertNull(selected)}
    }
    @Test fun verticalShortAndCancelledDragsDoNotNavigate(){
        var month by mutableStateOf(YearMonth.of(2030,2))
        compose.setContent{ArmyristTheme{MonthCalendar(month,month.atDay(1),emptyList(),emptyList(),{month=it},{})}}
        val calendar=compose.onNodeWithTag("month-calendar")
        calendar.performTouchInput{swipeUp()}
        calendar.performTouchInput{down(center);moveBy(Offset(-25f,0f));up()}
        calendar.performTouchInput{down(center);moveBy(Offset(-180f,0f));cancel()}
        compose.runOnIdle{assertEquals(YearMonth.of(2030,2),month)}
    }
    @Test fun navigationButtonsAndDayClickRemainAvailable(){
        var month by mutableStateOf(YearMonth.of(2030,2));var selected:LocalDate?=null
        compose.setContent{ArmyristTheme{MonthCalendar(month,month.atDay(1),emptyList(),emptyList(),{month=it},{selected=it})}}
        compose.onNodeWithContentDescription("다음 달").performClick()
        compose.onNodeWithContentDescription("이전 달").performClick()
        compose.onNodeWithContentDescription("2030-02-28 선택").performClick()
        compose.runOnIdle{assertEquals(LocalDate.of(2030,2,28),selected)}
        screenshot("calendar-clean-084.png")
    }
    private fun screenshot(name:String)=compose.runOnIdle{
        val view=android.view.inspector.WindowInspector.getGlobalWindowViews().last()
        val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888);view.draw(android.graphics.Canvas(bitmap))
        java.io.File("build/preview-screens/$name").also{it.parentFile!!.mkdirs()}.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
}
