package com.seolhwa.armyrist.widget

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.seolhwa.armyrist.ArmyristApplication
import com.seolhwa.armyrist.R
import com.seolhwa.armyrist.timeplan.v3.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.YearMonth

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=ArmyristApplication::class)
class CalendarWidgetTest {
    private val app get()=ApplicationProvider.getApplicationContext<ArmyristApplication>()
    @Test fun allRequestedCellRangesHaveAnAdaptiveLayout(){
        for(columns in 1..4)for(rows in 1..12){
            val mode=CalendarWidgetModel.layout(columns*80,rows*80)
            assertNotNull(mode)
        }
        assertEquals(WidgetLayout.DATE,CalendarWidgetModel.layout(70,70))
        assertEquals(WidgetLayout.MONTH_AGENDA,CalendarWidgetModel.layout(350,960))
        assertEquals(YearMonth.of(2030,2),CalendarWidgetModel.parseMonth("bad",LocalDate.of(2030,2,1)))
    }
    @Test fun miniMonthAndTallViewsInflateAndResizeWithoutClippingControls(){
        val manager=AppWidgetManager.getInstance(app)
        for((w,h)in listOf(70 to 70,180 to 80,180 to 600,350 to 300,350 to 960)){
            val options=Bundle().apply{putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,w);putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,h)}
            val remote=CalendarWidgetProvider.render(app,101,options)
            val host=AppWidgetHostView(app)
            host.setAppWidget(101,AppWidgetProviderInfo().apply{provider=ComponentName(app,CalendarWidgetProvider::class.java)})
            val root=remote.apply(app,host)
            root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h)
            val mini=root.findViewById<View>(R.id.widget_mini)
            assertEquals(if(w==70)View.VISIBLE else View.GONE,mini.visibility)
            if(w>70)assertEquals(View.VISIBLE,root.findViewById<View>(R.id.widget_header).visibility)
            if(h==960){assertEquals(View.VISIBLE,root.findViewById<View>(R.id.widget_agenda).visibility);screenshot(root,"calendar-widget-tall-084.png",w,h)}
        }
        assertNotNull(manager)
    }
    @Test fun serviceReadsFreshSnapshotsAndExcludesCompletedItems(){
        val repo=app.dateAwareTimePlanRepository;val date=LocalDate.now();val base=repo.createPlan(date.atTime(8,0))!!
        val event=DateTimeEvent("event",TimeEventKind.MIDWAY,0,"오전 일정",EventDateTimeSpec.Single(DateTimeValue.explicit(date.atTime(9,0))))
        val action=TimePlanActionItem(parentPointId=event.id,content="위젯 실시사항",scheduledDateTime=date.plusDays(1).atTime(9,30),completionState=ActionCompletionState.COMPLETE)
        assertTrue(repo.commit(DateTimePlanRules.normalizeTopology(base.copy(title="위젯 시간계획",midwayEvents=listOf(event),actions=listOf(action)))))
        val factory=CalendarWidgetService.AgendaFactory(app,201);factory.onDataSetChanged()
        val rows=(0 until factory.getCount()).mapNotNull{factory.getViewAt(it)?.apply(app,android.widget.FrameLayout(app))}
        assertFalse(rows.any{it.findViewById<TextView>(R.id.widget_entry_title).text.toString()=="위젯 실시사항"})
        val pending=CalendarWidgetProvider.open(app,date.plusDays(1))
        assertEquals(date.plusDays(1).toString(),shadowOf(pending).savedIntent.getStringExtra("calendarDate"))
        factory.onDestroy();assertEquals(0,factory.getCount())
    }
    @Test fun spanningPlansAreNotDuplicatedForEveryDayOfMonth(){
        val date=LocalDate.now();val repo=app.dateAwareTimePlanRepository;val base=repo.createPlan(date.atTime(8,0))!!
        val plan=base.copy(end=DateTimeAnchor(DateTimeValue.explicit(date.plusDays(3).atTime(10,0))))
        val entries=CalendarWidgetModel.monthEntries(YearMonth.from(date),listOf(plan),emptyList())
        assertEquals(1,entries.count{it.kind==com.seolhwa.armyrist.calendar.AgendaKind.PLAN})
    }
    private fun screenshot(view:View,name:String,w:Int,h:Int){
        val b=android.graphics.Bitmap.createBitmap(w,h,android.graphics.Bitmap.Config.ARGB_8888);view.draw(android.graphics.Canvas(b))
        java.io.File("build/preview-screens/$name").also{it.parentFile!!.mkdirs()}.outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()
    }
}
