package com.seolhwa.armyrist.calendar

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seolhwa.armyrist.*
import com.seolhwa.armyrist.stage2.domain.Checklist
import com.seolhwa.armyrist.timeplan.v3.domain.DateAwareTimePlan
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun AgendaKind.color(): Color = when(this) {
    AgendaKind.PLAN -> Color(0xFF4263D8); AgendaKind.EVENT -> Color(0xFF8B5FC1)
    AgendaKind.ACTION -> Color(0xFF16845D); AgendaKind.CHECKLIST -> Color(0xFFD5872A)
}

@Composable
internal fun CalendarCover(path: String?) {
    val bitmap by produceState<android.graphics.Bitmap?>(null,path) {
        value = withContext(Dispatchers.IO) { path?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() } }
    }
    bitmap?.let { Image(it.asImageBitmap(),"달력 커버 이미지",Modifier.fillMaxWidth().height(126.dp),contentScale=ContentScale.Crop) }
}

@Composable
private fun DayThumbnail(path: String) {
    val bitmap by produceState<android.graphics.Bitmap?>(null,path) { value=withContext(Dispatchers.IO){BitmapFactory.decodeFile(path,BitmapFactory.Options().apply{inSampleSize=16})} }
    bitmap?.let{Image(it.asImageBitmap(),null,Modifier.size(18.dp),contentScale=ContentScale.Crop)}
}

@Composable
internal fun MonthCalendar(month: YearMonth, selected: LocalDate, plans: List<DateAwareTimePlan>, lists: List<Checklist>,
    onMonth: (YearMonth)->Unit, onSelect: (LocalDate)->Unit, compact: Boolean=false, dayImages: Map<LocalDate,String> = emptyMap()) {
    val today=LocalDate.now()
    val marks=remember(month,plans,lists,today) { (1..month.lengthOfMonth()).associateWith { CalendarAgenda.entriesOn(month.atDay(it),plans,lists,today).map { e->e.kind }.distinct() } }
    Column(Modifier.fillMaxWidth().padding(if(compact) 8.dp else 12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={onMonth(month.minusMonths(1))}) { Text("‹",style=MaterialTheme.typography.titleLarge) }
            Text("${month.year}년 ${month.monthValue}월",Modifier.weight(1f),fontWeight=FontWeight.Bold)
            TextButton(onClick={onMonth(YearMonth.from(today));onSelect(today)}){Text("오늘")}
            TextButton(onClick={onMonth(month.plusMonths(1))}){Text("›",style=MaterialTheme.typography.titleLarge)}
        }
        Row { listOf("일","월","화","수","목","금","토").forEachIndexed { i,s-> Box(Modifier.weight(1f),contentAlignment=Alignment.Center){Text(s,style=MaterialTheme.typography.labelSmall,color=if(i==0)Color(0xFFC85555)else ArmyristColors.SecondaryText)} } }
        val offset=month.atDay(1).dayOfWeek.value%7
        repeat((offset+month.lengthOfMonth()+6)/7) { week ->
            Row {
                repeat(7) { col ->
                    val number=week*7+col-offset+1
                    Box(Modifier.weight(1f).height(if(compact && dayImages.isEmpty())44.dp else 54.dp),contentAlignment=Alignment.Center) {
                        if(number in 1..month.lengthOfMonth()) {
                            val date=month.atDay(number);val active=date==selected
                            Column(Modifier.fillMaxSize().background(if(active)ArmyristColors.SecondaryControl else Color.Transparent,ArmyristControlShape).clickable(onClickLabel="${date} 선택"){onSelect(date)},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                                Text(number.toString(),fontWeight=if(active||date==today)FontWeight.Bold else FontWeight.Normal,color=when { active->ArmyristColors.PrimaryControl;date==today->Color(0xFF16845D);col==0->Color(0xFFC85555);else->ArmyristColors.PrimaryText })
                                dayImages[date]?.let { DayThumbnail(it) }
                                Row(horizontalArrangement=Arrangement.spacedBy(3.dp),modifier=Modifier.height(8.dp),verticalAlignment=Alignment.CenterVertically) { marks[number].orEmpty().forEach { Box(Modifier.size(4.dp).background(it.color(),CircleShape)) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AgendaRow(entry: AgendaEntry,onClick: ()->Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick=onClick),shape=ArmyristControlShape,color=ArmyristColors.RaisedSurface) {
        Row(Modifier.heightIn(min=76.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(48.dp).background(entry.kind.color()))
            Column(Modifier.weight(1f).padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("${entry.kind.label} · ${entry.start.format(DateTimeFormatter.ofPattern("HH:mm"))}${if(entry.complete) " · 완료" else ""}",style=MaterialTheme.typography.labelSmall,color=entry.kind.color())
                Text(entry.title,fontWeight=FontWeight.SemiBold)
                if(entry.ownerTitle.isNotBlank())Text(entry.ownerTitle,style=MaterialTheme.typography.bodySmall,color=ArmyristColors.SecondaryText)
            }
            Text("›",Modifier.padding(12.dp),color=ArmyristColors.SecondaryText)
        }
    }
}

@Composable
fun HomeCalendarCard(revision: Int,onOpen: (LocalDate)->Unit) {
    val context=LocalContext.current;val app=context.applicationContext as ArmyristApplication
    val notificationRevision by app.coreSuiteRepository.notificationCompletionRevision.collectAsState()
    val plans=remember(revision){app.dateAwareTimePlanRepository.getPlans()};val lists=remember(revision,notificationRevision){app.coreSuiteRepository.getChecklists()}
    var dateText by rememberSaveable{mutableStateOf(LocalDate.now().toString())};val date=LocalDate.parse(dateText)
    var monthText by rememberSaveable{mutableStateOf(YearMonth.from(date).toString())};val month=YearMonth.parse(monthText)
    val prefs=remember{CalendarPreferences(context)}
    var collapsed by remember { mutableStateOf(prefs.collapsed(true)) }
    val dayImages=remember(revision,month){prefs.dayImages(month)}
    val dayImage=remember(revision,date){prefs.dayImage(date)}
    val cover=remember(revision){prefs.cover()};val entries=CalendarAgenda.entriesOn(date,plans,lists)
    Surface(shape=ArmyristPanelShape,color=ArmyristColors.WorkSurface) {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("달력",Modifier.weight(1f),fontWeight=FontWeight.Bold)
                TextButton(onClick={val next=!collapsed;if(prefs.setCollapsed(true,next))collapsed=next}){Text(if(collapsed)"달력 펼치기 ∨"else"달력 접기 ∧")}
            }
            if(!collapsed) {
                CalendarCover(cover)
                MonthCalendar(month,date,plans,lists,{monthText=it.toString();dateText=it.atDay(date.dayOfMonth.coerceAtMost(it.lengthOfMonth())).toString()},{dateText=it.toString()},compact=true,dayImages=dayImages)
            }
            CalendarCover(dayImage)
            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically){Text("${date.monthValue}월 ${date.dayOfMonth}일 · ${entries.size}개",Modifier.weight(1f),fontWeight=FontWeight.Bold);TextButton(onClick={onOpen(date)}){Text("달력 열기 ›")}}
                if(entries.isEmpty())Text("예정된 항목이 없습니다.",color=ArmyristColors.SecondaryText,style=MaterialTheme.typography.bodySmall)
                entries.take(2).forEach { AgendaRow(it){onOpen(date)} }
            }
        }
    }
}

@Composable
internal fun CalendarPlanBranch(plan: DateAwareTimePlan, entries: List<AgendaEntry>, onOpen: (AgendaEntry)->Unit) {
    val planEntry=entries.firstOrNull{it.kind==AgendaKind.PLAN} ?: AgendaEntry(AgendaKind.PLAN,plan.id,null,plan.title,"",entries.first().start)
    Surface(shape=ArmyristPanelShape,color=ArmyristColors.WorkSurface) {
        Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().clickable{onOpen(planEntry)},verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){Text("시간계획",color=AgendaKind.PLAN.color(),style=MaterialTheme.typography.labelSmall);Text(plan.title,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
                    val range=listOfNotNull(plan.start.value.value,plan.end.value.value).joinToString(" – "){it.format(DateTimeFormatter.ofPattern("MM.dd HH:mm"))}
                    if(range.isNotBlank())Text(range,style=MaterialTheme.typography.labelSmall,color=ArmyristColors.SecondaryText)
                }
                Text("편집 ›",color=ArmyristColors.PrimaryControl,style=MaterialTheme.typography.labelMedium)
            }
            val events=entries.filter{it.kind==AgendaKind.EVENT}.associateBy{it.itemId}
            val actions=entries.filter{it.kind==AgendaKind.ACTION}.groupBy{entry->plan.actions.firstOrNull{it.id==entry.itemId}?.parentPointId}
            com.seolhwa.armyrist.timeplan.v3.domain.DateTimePlanRules.nodeIds(plan).forEach{node->
                val event=events[node];val children=actions[node].orEmpty()
                if(event!=null||children.isNotEmpty())Surface(Modifier.fillMaxWidth().padding(start=10.dp),shape=ArmyristControlShape,color=ArmyristColors.RaisedSurface){
                    Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                        val nodeEntry=event ?: AgendaEntry(AgendaKind.EVENT,plan.id,node,com.seolhwa.armyrist.timeplan.v3.domain.TimePlanExecutionRules.pointName(plan,node),plan.title,com.seolhwa.armyrist.timeplan.v3.domain.DateTimePlanRules.nodeArrival(plan,node)?:planEntry.start)
                        Row(Modifier.fillMaxWidth().clickable{onOpen(nodeEntry)},verticalAlignment=Alignment.CenterVertically){
                            Column(Modifier.weight(1f)){Text("일정 · ${nodeEntry.start.format(DateTimeFormatter.ofPattern("MM.dd HH:mm"))}${nodeEntry.end?.takeUnless{it==nodeEntry.start}?.let{ " – "+it.format(DateTimeFormatter.ofPattern("MM.dd HH:mm")) }.orEmpty()} · ${children.size}개 실시사항",style=MaterialTheme.typography.labelSmall,color=AgendaKind.EVENT.color());Text(nodeEntry.title,fontWeight=FontWeight.SemiBold)}
                            Text("›",color=ArmyristColors.SecondaryText)
                        }
                        children.forEach{child->
                            Row(Modifier.fillMaxWidth().padding(start=12.dp).clickable{onOpen(child)}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
                                Text(if(child.complete)"✓"else"○",Modifier.padding(end=8.dp),color=AgendaKind.ACTION.color())
                                Column(Modifier.weight(1f)){Text("실시사항 · ${child.start.format(DateTimeFormatter.ofPattern("HH:mm"))}",style=MaterialTheme.typography.labelSmall,color=AgendaKind.ACTION.color());Text(child.title)}
                                Text("›",color=ArmyristColors.SecondaryText)
                            }
                        }
                    }
                }
            }
            if(entries.none{it.kind==AgendaKind.EVENT||it.kind==AgendaKind.ACTION})Text("이 날짜에 지정된 하위 일정·실시사항이 없습니다.",style=MaterialTheme.typography.bodySmall,color=ArmyristColors.SecondaryText)
        }
    }
}
