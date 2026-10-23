package com.seolhwa.armyrist.widget
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.seolhwa.armyrist.ArmyristApplication
import com.seolhwa.armyrist.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=ArmyristApplication::class)
class CalendarWidgetModernTest {
    private val app get()=ApplicationProvider.getApplicationContext<ArmyristApplication>()
    @Test fun navigationIsIndependentPerInstanceAndTodayResets(){
        val manager=AppWidgetManager.getInstance(app);val shadow=shadowOf(manager)
        val ids=shadow.createWidgets(CalendarWidgetProvider::class.java,R.layout.calendar_widget_initial,2)
        val provider=CalendarWidgetProvider();val old=CalendarWidgetProvider.month(app,ids[0])
        provider.onReceive(app,Intent(CalendarWidgetProvider.NEXT).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,ids[0]))
        assertEquals(old.plusMonths(1),CalendarWidgetProvider.month(app,ids[0]));assertEquals(old,CalendarWidgetProvider.month(app,ids[1]))
        provider.onReceive(app,Intent(CalendarWidgetProvider.TODAY).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,ids[0]))
        assertEquals(YearMonth.now(),CalendarWidgetProvider.month(app,ids[0]))
        provider.onDeleted(app,intArrayOf(ids[0]));assertFalse(app.getSharedPreferences(CalendarWidgetProvider.PREFS,0).contains("month:${ids[0]}"))
    }
    @Test fun changedSnapshotRefreshesAnInstalledDateWidget(){
        val manager=AppWidgetManager.getInstance(app);val shadow=shadowOf(manager)
        val id=shadow.createWidget(CalendarCompactWidgetProvider::class.java,R.layout.calendar_widget_initial)
        val date=LocalDate.now()
        app.dateAwareTimePlanRepository.createPlan(date.atTime(8,0))!!
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
        val view=shadow.getViewFor(id)
        val desc=view.findViewById<android.view.View>(R.id.widget_mini).contentDescription.toString()
        assertTrue(desc,desc.contains("1개 일정"))
        assertEquals(date.dayOfMonth.toString(),view.findViewById<TextView>(R.id.widget_mini_day).text.toString())
    }
    @Test fun responsiveRemoteViewsCanBeParceledAndRestored(){
        val views=CalendarWidgetProvider.render(app,401,Bundle())
        val parcel=android.os.Parcel.obtain()
        try{views.writeToParcel(parcel,0);parcel.setDataPosition(0);val copy=android.widget.RemoteViews.CREATOR.createFromParcel(parcel);assertEquals(app.packageName,copy.`package`)}finally{parcel.recycle()}
    }
}
