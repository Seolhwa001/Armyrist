package com.seolhwa.armyrist.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import com.seolhwa.armyrist.R
import com.seolhwa.armyrist.calendar.*
import com.seolhwa.armyrist.stage2.data.CoreSuiteRepository
import com.seolhwa.armyrist.timeplan.v3.data.DateAwareTimePlanRepository
import java.time.LocalDate
import java.time.YearMonth

open class CalendarWidgetProvider:AppWidgetProvider() {
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray){ids.forEach{update(context,manager,it)}}
    override fun onAppWidgetOptionsChanged(context:Context,manager:AppWidgetManager,id:Int,options:Bundle){update(context,manager,id,options)}
    override fun onDeleted(context:Context,ids:IntArray){ids.forEach{context.getSharedPreferences(PREFS,0).edit().remove("month:$it").apply()}}
    override fun onReceive(context:Context,intent:Intent){
        val manager=AppWidgetManager.getInstance(context)
        if(intent.action in setOf(PREVIOUS,NEXT,TODAY)) {
            val id=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID)
            // Do not allow one widget's pending action to mutate another provider instance.
            if(id !in manager.getAppWidgetIds(ComponentName(context,javaClass)))return
            val old=month(context,id)
            val next=when(intent.action){PREVIOUS->old.minusMonths(1);NEXT->old.plusMonths(1);else->YearMonth.now()}
            context.getSharedPreferences(PREFS,0).edit().putString("month:$id",next.toString()).commit()
            update(context,manager,id)
        } else if(intent.action in setOf(Intent.ACTION_DATE_CHANGED,Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED,Intent.ACTION_MY_PACKAGE_REPLACED)) {
            refreshAll(context)
        } else super.onReceive(context,intent)
    }
    companion object {
        internal const val PREFS="killomris_calendar_widgets_v1"
        internal const val PREVIOUS="com.seolhwa.armyrist.widget.PREVIOUS"
        internal const val NEXT="com.seolhwa.armyrist.widget.NEXT"
        internal const val TODAY="com.seolhwa.armyrist.widget.TODAY"
        internal fun month(context:Context,id:Int)=CalendarWidgetModel.parseMonth(context.getSharedPreferences(PREFS,0).getString("month:$id",null))
        internal fun update(context:Context,manager:AppWidgetManager,id:Int,options:Bundle=manager.getAppWidgetOptions(id)) {
            manager.updateAppWidget(id,render(context,id,options))
            manager.notifyAppWidgetViewDataChanged(id,R.id.widget_agenda_list)
        }
        internal fun render(context:Context,id:Int,options:Bundle):RemoteViews {
            val month=month(context,id);val today=LocalDate.now()
            val plans=DateAwareTimePlanRepository(context).getPlans();val lists=CoreSuiteRepository(context).getChecklists()
            val entries=CalendarWidgetModel.remainingEntries(month,plans,lists)
            val marks=(1..month.lengthOfMonth()).associateWith{CalendarAgenda.entriesOn(month.atDay(it),plans,lists,today)}
            fun build(width:Int,height:Int):RemoteViews {
                val mode=CalendarWidgetModel.layout(width,height)
                val view=RemoteViews(context.packageName,R.layout.calendar_widget)
                val miniature=mode==WidgetLayout.DATE
                val showAgenda=mode in setOf(WidgetLayout.AGENDA,WidgetLayout.MONTH_AGENDA)
                val showMonth=mode in setOf(WidgetLayout.MONTH,WidgetLayout.MONTH_AGENDA)
                view.setViewVisibility(R.id.widget_mini,if(miniature)View.VISIBLE else View.GONE)
                view.setViewVisibility(R.id.widget_header,if(miniature)View.GONE else View.VISIBLE)
                view.setViewVisibility(R.id.widget_days,if(showMonth || mode==WidgetLayout.WEEK)View.VISIBLE else View.GONE)
                view.setViewVisibility(R.id.widget_agenda,if(showAgenda)View.VISIBLE else View.GONE)
                view.setViewVisibility(R.id.widget_agenda_label,if(showAgenda)View.VISIBLE else View.GONE)
                view.setTextViewText(R.id.widget_month,if(width<240)"${month.monthValue}월"else"${month.year}년 ${month.monthValue}월")
                if(width<240)view.setViewVisibility(R.id.widget_today,View.GONE)
                if(width<150){view.setViewVisibility(R.id.widget_previous,View.GONE);view.setViewVisibility(R.id.widget_next,View.GONE)}
                if(mode==WidgetLayout.WEEK)view.setViewPadding(R.id.widget_root,4,4,4,4)
                view.setTextViewText(R.id.widget_agenda_label,"이번 달 남은 일정 · ${entries.size}개")
                val date=if(month==YearMonth.from(today))today else month.atDay(1)
                view.setOnClickPendingIntent(R.id.widget_month,open(context,date))
                view.setOnClickPendingIntent(R.id.widget_empty,open(context,date))
                if(miniature){
                    view.setViewPadding(R.id.widget_root,4,4,4,4)
                    view.setTextViewText(R.id.widget_mini_month,"${today.monthValue}월")
                    view.setTextViewTextSize(R.id.widget_mini_month,android.util.TypedValue.COMPLEX_UNIT_SP,9f)
                    view.setTextViewText(R.id.widget_mini_day,today.dayOfMonth.toString())
                    view.setTextViewTextSize(R.id.widget_mini_day,android.util.TypedValue.COMPLEX_UNIT_SP,20f)
                    view.setViewVisibility(R.id.widget_mini_count,View.GONE)
                    view.setContentDescription(R.id.widget_mini,"$today 달력 열기, ${CalendarAgenda.entriesOn(today,plans,lists,today).size}개 일정")
                    view.setOnClickPendingIntent(R.id.widget_mini,open(context,today))
                }
                val component=managerProvider(context,id)
                listOf(R.id.widget_previous to PREVIOUS,R.id.widget_next to NEXT,R.id.widget_today to TODAY).forEach{(control,action)->
                    val intent=Intent(context,component).setAction(action).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)
                        .setData(Uri.parse("killomris-widget://$id/$action"))
                    view.setOnClickPendingIntent(control,PendingIntent.getBroadcast(context,id,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                }
                if(showMonth || mode==WidgetLayout.WEEK){
                    view.removeAllViews(R.id.widget_days)
                    fun row(days:List<LocalDate?>,header:Boolean=false){
                        val row=RemoteViews(context.packageName,R.layout.calendar_widget_week)
                        days.forEachIndexed{col,day->
                            val cell=RemoteViews(context.packageName,R.layout.calendar_widget_day)
                            val color=when(col){0->Color.rgb(181,44,67);6->Color.rgb(46,85,204);else->Color.rgb(20,32,56)}
                            cell.setTextColor(R.id.widget_day_number,color)
                            cell.setTextViewText(R.id.widget_day_number,if(header)listOf("일","월","화","수","목","금","토")[col]else day?.dayOfMonth?.toString().orEmpty())
                            val dayEntries=day?.let{marks[it.dayOfMonth]}.orEmpty()
                            cell.setTextViewText(R.id.widget_day_marks,if(header)" "else "•".repeat(dayEntries.map{it.kind}.distinct().size).ifBlank{" "})
                            if(day!=null){
                                cell.setOnClickPendingIntent(R.id.widget_day_container,open(context,day))
                                cell.setContentDescription(R.id.widget_day_container,"$day 달력 열기, ${dayEntries.size}개 일정")
                                if(day==today)cell.setInt(R.id.widget_day_container,"setBackgroundResource",R.drawable.calendar_widget_today)
                            }
                            row.addView(R.id.widget_week,cell)
                        }
                        view.addView(R.id.widget_days,row)
                    }
                    if(showMonth){
                        row(List(7){null},true)
                        val offset=month.atDay(1).dayOfWeek.value%7
                        repeat((offset+month.lengthOfMonth()+6)/7){week->row(List(7){col->(week*7+col-offset+1).takeIf{it in 1..month.lengthOfMonth()}?.let{month.atDay(it)}})}
                    }else{
                        val start=date.minusDays((date.dayOfWeek.value%7).toLong())
                        row(List(7){start.plusDays(it.toLong()).takeIf{d->YearMonth.from(d)==month}})
                    }
                }
                if(showAgenda){
                    val service=Intent(context,CalendarWidgetService::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)
                        .setData(Uri.parse("killomris-widget://$id/agenda"))
                    view.setRemoteAdapter(R.id.widget_agenda_list,service)
                    view.setEmptyView(R.id.widget_agenda_list,R.id.widget_empty)
                    val template=Intent(context,CalendarActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    view.setPendingIntentTemplate(R.id.widget_agenda_list,PendingIntent.getActivity(context,id,template,
                        PendingIntent.FLAG_UPDATE_CURRENT or if(Build.VERSION.SDK_INT>=31)PendingIntent.FLAG_MUTABLE else 0))
                }
                return view
            }
            if(Build.VERSION.SDK_INT>=31)return RemoteViews(linkedMapOf(
                SizeF(40f,40f) to build(40,40),SizeF(70f,170f) to build(80,200),SizeF(150f,70f) to build(150,100),
                SizeF(150f,170f) to build(150,200),SizeF(240f,260f) to build(280,300),
                SizeF(240f,400f) to build(280,500)))
            val w=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,280)
            val h=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,300)
            return build(w,h)
        }
        private fun managerProvider(context:Context,id:Int):Class<out CalendarWidgetProvider> =
            if(AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider?.className==CalendarCompactWidgetProvider::class.java.name)CalendarCompactWidgetProvider::class.java else CalendarWidgetProvider::class.java
        internal fun open(context:Context,date:LocalDate):PendingIntent=PendingIntent.getActivity(context,0,
            Intent(context,CalendarActivity::class.java).setData(Uri.parse("killomris://calendar/$date"))
                .putExtra("calendarDate",date.toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun refreshAll(context:Context){
            val manager=AppWidgetManager.getInstance(context)
            listOf(CalendarWidgetProvider::class.java,CalendarCompactWidgetProvider::class.java).forEach{provider->
                manager.getAppWidgetIds(ComponentName(context,provider)).forEach{id->runCatching{update(context,manager,id)}}
            }
        }
        fun requestPin(context:Context){
            val manager=AppWidgetManager.getInstance(context)
            if(Build.VERSION.SDK_INT>=26 && manager.isRequestPinAppWidgetSupported){
                manager.requestPinAppWidget(ComponentName(context,CalendarWidgetProvider::class.java),null,null)
            }else Toast.makeText(context,"홈 화면을 길게 누른 뒤 위젯 → 낄롬리스 달력을 선택하세요.",Toast.LENGTH_LONG).show()
        }
    }
}
class CalendarCompactWidgetProvider:CalendarWidgetProvider()

/** Coalesce a transaction's snapshot writes into one widget refresh. */
object CalendarWidgetRefresh {
    private val handler=Handler(Looper.getMainLooper())
    private var scheduled:Runnable?=null
    fun schedule(context:Context){
        val app=context.applicationContext
        scheduled?.let{handler.removeCallbacks(it)}
        val task=Runnable{scheduled=null;CalendarWidgetProvider.refreshAll(app)}
        scheduled=task;handler.postDelayed(task,200)
    }
}
