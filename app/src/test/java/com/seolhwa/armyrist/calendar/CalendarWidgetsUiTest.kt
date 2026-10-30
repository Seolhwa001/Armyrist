package com.seolhwa.armyrist.calendar
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.seolhwa.armyrist.*
import com.seolhwa.armyrist.home.*
import com.seolhwa.armyrist.timeplan.v3.domain.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w360dp-h800dp",application=ArmyristApplication::class)
class CalendarWidgetsUiTest {
    @get:Rule val compose=createComposeRule()
    private val app get()=ApplicationProvider.getApplicationContext<ArmyristApplication>()
    @Test fun homeCalendarCollapsePersistsAndCanReopen(){
        val prefs=CalendarPreferences(app);assertTrue(prefs.setCollapsed(true,true))
        compose.setContent{ArmyristTheme{HomeCalendarCard(0,{})}}
        compose.onNodeWithText("오늘").assertDoesNotExist()
        compose.onNodeWithText("달력 펼치기 ∨").performClick()
        compose.onNodeWithText("오늘").assertIsDisplayed()
        compose.onNodeWithText("달력 접기 ∧").performClick()
        assertTrue(CalendarPreferences(app).collapsed(true))
    }
    @Test fun calendarBannerCanBeDisabledIndependentlyFromPlanBanner(){
        compose.setContent{ArmyristTheme{Column(Modifier.verticalScroll(rememberScrollState())){HomeBannerSettingsPanel()}}}
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.onNodeWithText("배너 설정 저장").performScrollTo().performClick()
        val saved=HomeBannerPreferences(app).load();assertFalse(saved.calendarEnabled);assertEquals(HomeBannerMode.TIME_PLAN,saved.mode)
        assertEquals(saved,HomeBannerCodec.decode(HomeBannerCodec.encode(saved)))
    }
    @Test fun dailyImagesStayIndependentWhenOtherDayIsChangedOrCleared(){
        val prefs=CalendarPreferences(app);val day=LocalDate.of(2030,1,2)
        val source=File(app.cacheDir,"day-image.png");source.outputStream().use{Bitmap.createBitmap(10,10,Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.GREEN)}.compress(Bitmap.CompressFormat.PNG,100,it)}
        assertTrue(prefs.importDayImage(day,Uri.fromFile(source)));assertTrue(prefs.importDayImage(day.plusDays(1),Uri.fromFile(source)))
        val first=prefs.dayImage(day)!!;val second=prefs.dayImage(day.plusDays(1))!!;assertNotEquals(first,second)
        assertEquals(2,prefs.dayImages(java.time.YearMonth.from(day)).size)
        assertTrue(prefs.clearDayImage(day));assertFalse(File(first).exists());assertTrue(File(second).exists());assertEquals(second,CalendarPreferences(app).dayImage(day.plusDays(1)))
    }
    @Test fun outsideScheduleActionsRemainVisibleWithoutLegacyAttachment(){
        val date=LocalDate.of(2030,1,2);val repo=app.dateAwareTimePlanRepository;val base=repo.createPlan(date.minusDays(1).atTime(9,0))!!
        val event=DateTimeEvent("evening",TimeEventKind.MIDWAY,0,"오후 당직",EventDateTimeSpec.Single(DateTimeValue.explicit(date.minusDays(1).atTime(10,0))))
        val action=TimePlanActionItem(parentPointId=event.id,content="장비 상태 확인",scheduledDateTime=date.atTime(9,30))
        val plan=DateTimePlanRules.normalizeTopology(base.copy(title="수요일 당직",midwayEvents=listOf(event),actions=listOf(action)));assertTrue(repo.commit(plan))
        val entries=CalendarAgenda.entriesOn(date,listOf(plan),emptyList());assertEquals(1,entries.size)
        var opened:AgendaEntry?=null
        compose.setContent{ArmyristTheme{CalendarPlanBranch(plan,entries,{opened=it})}}
        compose.onNodeWithText("수요일 당직").assertIsDisplayed();compose.onNodeWithText("오후 당직").assertDoesNotExist();compose.onNodeWithText("일정 외 실시사항").assertIsDisplayed();compose.onNodeWithText("장비 상태 확인").assertIsDisplayed().performClick()
        assertEquals(action.id,opened?.itemId);assertEquals(AgendaKind.ACTION,opened?.kind)
        compose.runOnIdle{val view=android.view.inspector.WindowInspector.getGlobalWindowViews().last();val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap));val file=File("build/preview-screens/calendar-hierarchy.png").also{it.parentFile!!.mkdirs()};file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
    }
}
