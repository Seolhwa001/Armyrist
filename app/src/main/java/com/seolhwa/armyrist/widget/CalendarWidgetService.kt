package com.seolhwa.armyrist.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.seolhwa.armyrist.R
import com.seolhwa.armyrist.calendar.*
import com.seolhwa.armyrist.stage2.data.CoreSuiteRepository
import com.seolhwa.armyrist.timeplan.v3.data.DateAwareTimePlanRepository
import java.time.format.DateTimeFormatter

class CalendarWidgetService:RemoteViewsService(){
    override fun onGetViewFactory(intent:Intent):RemoteViewsFactory = AgendaFactory(applicationContext,intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1))
    internal class AgendaFactory(private val context:Context,private val id:Int):RemoteViewsFactory{
        private var rows=emptyList<AgendaEntry>()
        private var month=CalendarWidgetProvider.month(context,id)
        override fun onCreate(){}
        override fun onDataSetChanged(){
            month=CalendarWidgetProvider.month(context,id)
            rows=CalendarWidgetModel.remainingEntries(month,DateAwareTimePlanRepository(context).getPlans(),CoreSuiteRepository(context).getChecklists())
        }
        override fun onDestroy(){rows=emptyList()}
        override fun getCount()=rows.size
        override fun getViewAt(position:Int):RemoteViews?{
            val entry=rows.getOrNull(position)?:return null
            return RemoteViews(context.packageName,R.layout.calendar_widget_agenda_row).apply{
                val date=CalendarWidgetModel.openDate(entry,month)
                val time=entry.start.format(DateTimeFormatter.ofPattern("HH:mm"))
                setTextViewText(R.id.widget_entry_meta,"${date.monthValue}.${date.dayOfMonth} $time · ${entry.kind.label}${if(entry.complete)" · 완료"else""}")
                setTextColor(R.id.widget_entry_meta,entry.color?.let{runCatching{Color.parseColor(it)}.getOrNull()} ?: when(entry.kind){AgendaKind.PLAN->Color.rgb(46,85,204);AgendaKind.EVENT->Color.rgb(110,65,165);AgendaKind.ACTION->Color.rgb(20,120,80);AgendaKind.CHECKLIST->Color.rgb(151,91,15)})
                setTextViewText(R.id.widget_entry_title,entry.title)
                setTextViewText(R.id.widget_entry_parent,entry.ownerTitle)
                setViewVisibility(R.id.widget_entry_parent,if(entry.ownerTitle.isBlank())View.GONE else View.VISIBLE)
                setOnClickFillInIntent(R.id.widget_entry_root,Intent().putExtra("calendarDate",date.toString()))
                setContentDescription(R.id.widget_entry_root,"$date ${entry.kind.label} ${entry.title}, ${entry.ownerTitle}, 달력에서 편집")
            }
        }
        override fun getLoadingView():RemoteViews?=null
        override fun getViewTypeCount()=1
        override fun getItemId(position:Int)=position.toLong()
        override fun hasStableIds()=false
    }
}
