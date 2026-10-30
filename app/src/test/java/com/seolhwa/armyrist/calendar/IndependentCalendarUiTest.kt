package com.seolhwa.armyrist.calendar

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.seolhwa.armyrist.*
import com.seolhwa.armyrist.timeplan.v3.domain.*
import com.seolhwa.armyrist.timeplan.v3.ui.ScheduleActionWorkspace
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
class IndependentCalendarUiTest {
    @get:Rule val compose=createComposeRule()
    private val start=LocalDateTime.of(2030,10,23,22,0)
    private fun plan()=DateAwareTimePlan("p","일요일 당직",start=DateTimeAnchor(DateTimeValue.explicit(start)),end=DateTimeAnchor(DateTimeValue.explicit(start.plusHours(11))),createdAt="1",updatedAt="1")
    @Test fun calendarGroupsByTimeAndShowsUnmatchedItemsWithoutDuplicatingThem(){
        val split=start.plusHours(8).plusMinutes(30)
        val p=plan().copy(midwayEvents=listOf(
            DateTimeEvent("night",TimeEventKind.MIDWAY,0,"야간",EventDateTimeSpec.Range(DateTimeValue.explicit(start),DateTimeValue.explicit(split)),color="#7543A3"),
            DateTimeEvent("morning",TimeEventKind.MIDWAY,1,"아침",EventDateTimeSpec.Range(DateTimeValue.explicit(split),DateTimeValue.explicit(split.plusHours(2))),color="#147850")
        ),actions=listOf(
            TimePlanActionItem(content="순찰",scheduledDateTime=start.plusHours(2)),
            TimePlanActionItem(parentPointId="night",content="애국가",scheduledDateTime=split),
            TimePlanActionItem(content="별도 점검",scheduledDateTime=split.plusHours(3))
        ))
        val entries=CalendarAgenda.entriesOn(split.toLocalDate(),listOf(p),emptyList())
        var opened:AgendaEntry?=null
        compose.setContent{ArmyristTheme{CalendarPlanBranch(p,entries){opened=it}}}
        compose.onNodeWithText("야간").assertIsDisplayed();compose.onNodeWithText("아침").assertIsDisplayed()
        compose.onAllNodesWithText("애국가").assertCountEquals(1)
        compose.onNodeWithText("일정 외 실시사항").assertIsDisplayed()
        compose.onNodeWithText("애국가").performClick()
        compose.runOnIdle{assertEquals("morning",opened!!.displayEventId);assertEquals(split,opened!!.start)}
        val view=android.view.inspector.WindowInspector.getGlobalWindowViews().last()
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
        File("build/preview-screens/calendar-independent-086.png").also{it.parentFile!!.mkdirs()}.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
    @Test fun actionPaneAddsWithoutAnyScheduleOrAttachmentPicker(){
        val p=plan();var saved:TimePlanActionItem?=null
        compose.setContent{ArmyristTheme{ScheduleActionWorkspace(p,false,{saved=it;true}){open->Button(onClick={open("ALL")}){Text("요약 열기")}}}}
        compose.onNodeWithText("요약 열기").performClick()
        compose.onNodeWithText("+ 추가").performClick()
        compose.onNodeWithText("연결할 일정").assertDoesNotExist()
        compose.onNodeWithText("내용").performTextInput("독립 실시사항")
        compose.onNodeWithText("저장").performClick()
        compose.runOnIdle{assertEquals("독립 실시사항",saved!!.content);assertEquals("UNASSIGNED",saved!!.parentPointId)}
    }
}
