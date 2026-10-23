@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.seolhwa.armyrist.timeplan.v3.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seolhwa.armyrist.*
import com.seolhwa.armyrist.timeplan.v3.domain.*
import java.time.LocalDateTime
import java.time.Duration
import java.time.format.DateTimeFormatter

private fun markerColor(value:String):Color=runCatching{Color(android.graphics.Color.parseColor(value))}.getOrDefault(Color(0xFF111111))
internal val markerPalette=listOf("#111111","#4263D8","#16845D","#C44857","#9257C5","#D5872A")

@Composable
internal fun ContinuousTimeline(plan:DateAwareTimePlan,axis:TimelineScale,selectionMode:Boolean,selectedKeys:Set<String>,
    onSelection:(Set<String>)->Unit,onSummary:(String)->Unit,onSaveAction:(TimePlanActionItem)->Boolean,
    onStart:()->Unit,onEnd:()->Unit,onEvent:(DateTimeEvent)->Unit,onAddMid:()->Unit,onAddFinal:()->Unit,onMemo:()->Unit,onList:()->Unit){
    val preferences=LocalContext.current.getSharedPreferences("timeline_display",android.content.Context.MODE_PRIVATE)
    var showContents by remember { mutableStateOf(preferences.getBoolean("show_contents",true)) }
    val labels=showContents && !LocalTimelineCompact.current
    val lazy=rememberLazyListState();val density=LocalDensity.current;val haptic=LocalHapticFeedback.current
    val currentPlan by rememberUpdatedState(plan);val currentSelection by rememberUpdatedState(selectedKeys);val publish by rememberUpdatedState(onSelection)
    val lanes=remember(plan){TimelineSelection.lanes(plan)};val laneCount=(lanes.values.maxOrNull()?:0)+1
    var group by remember(plan.id){mutableStateOf<List<String>>(emptyList())};var markerId by remember(plan.id){mutableStateOf<String?>(null)}
    var viewport by remember{mutableStateOf(Rect.Zero)};var active by remember{mutableStateOf(false)}
    var pointerRoot by remember{mutableFloatStateOf(0f)};var anchor by remember{mutableStateOf(axis.start)}
    var before by remember{mutableStateOf(emptySet<String>())};var visited by remember{mutableStateOf(emptySet<String>())};var selectPaint by remember{mutableStateOf(true)}
    fun timeAtPointer():LocalDateTime{
        val y=pointerRoot-viewport.top
        val info=lazy.layoutInfo.visibleItemsInfo.firstOrNull{y>=it.offset&&y<=it.offset+it.size}?:return if(y<0)axis.start else axis.end
        val index=(info.key as? String)?.removePrefix("hour:")?.toIntOrNull()?:return axis.end
        val slice=axis.slice(index)
        val header=if(index==0 || slice.start.toLocalTime()==java.time.LocalTime.MIDNIGHT)with(density){28.dp.toPx()}else 0f
        val fraction=((y-info.offset-header)/(info.size-header).coerceAtLeast(1f)).coerceIn(0f,1f)
        return slice.start.plusSeconds((Duration.between(slice.start,slice.end).seconds*fraction).toLong())
    }
    fun paint(){
        val now=timeAtPointer();val crossed=TimelineSelection.keysInRange(currentPlan,minOf(anchor,now).minusMinutes(3),maxOf(anchor,now).plusMinutes(3))
        val next=visited+crossed
        if(next!=visited){visited=next;publish(if(selectPaint)before+next else before-next);haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)}
    }
    LaunchedEffect(active){
        if(active){var previous=withFrameNanos{it};while(active){
            val now=withFrameNanos{it};val dt=((now-previous)/1_000_000_000f).coerceAtMost(.05f);previous=now
            val edge=with(density){52.dp.toPx()};val local=pointerRoot-viewport.top
            val speed=when{local<edge->-((edge-local)/edge).coerceIn(0f,1f);local>viewport.height-edge->((local-(viewport.height-edge))/edge).coerceIn(0f,1f);else->0f}
            if(speed!=0f){lazy.scrollBy(speed*with(density){600.dp.toPx()}*dt);paint()}
        }}
    }
    fun toggle(key:String){onSelection(if(key in selectedKeys)selectedKeys-key else selectedKeys+key)}
    Column(Modifier.fillMaxSize()){
        if(LocalTimelineCompact.current) {
            Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
                Text("시간축",Modifier.weight(1f),fontSize=12.sp,fontWeight=FontWeight.Bold,maxLines=1)
                TextButton(onClick={onSummary("")},contentPadding=PaddingValues(4.dp)){Text("전체보기 ›",fontSize=10.sp,maxLines=1)}
            }
        } else {
        Row(Modifier.fillMaxWidth().padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Text("시간표",fontWeight=FontWeight.Bold,fontSize=17.sp);Text("길게 눌러 여러 항목 선택",fontSize=11.sp,color=ArmyristColors.SecondaryText)}
            TextButton(onClick=onList,contentPadding=PaddingValues(6.dp)){Text("목록",fontSize=12.sp)}
            if(!selectionMode)Surface(shape=ArmyristControlShape,color=ArmyristColors.SecondaryControl,modifier=Modifier.clickable{onSummary("ALL")}.semantics{contentDescription="실시사항 요약 열기"}){
                Column(Modifier.padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("실시사항 ‹",fontSize=12.sp,color=ArmyristColors.PrimaryControl,fontWeight=FontWeight.Bold);Text("← 왼쪽으로 밀기",fontSize=9.sp,color=ArmyristColors.SecondaryText)}
            }
        }
        }
        if(!LocalTimelineCompact.current) Row(Modifier.fillMaxWidth().padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=showContents,onClick={showContents=true;preferences.edit().putBoolean("show_contents",true).apply()},label={Text("점 + 실시사항",fontSize=12.sp)})
            FilterChip(selected=!showContents,onClick={showContents=false;preferences.edit().putBoolean("show_contents",false).apply()},label={Text("점만",fontSize=12.sp)})
        }
        if(labels)Row(Modifier.fillMaxWidth().padding(start=56.dp,end=10.dp,bottom=4.dp)){
            Text("일정",Modifier.weight(.45f),fontSize=11.sp,fontWeight=FontWeight.Bold,color=ArmyristColors.SecondaryText)
            Text("● 실시사항 · 눌러서 확인",Modifier.weight(.55f),fontSize=11.sp,fontWeight=FontWeight.Bold,color=ArmyristColors.SecondaryText)
        }
        LazyColumn(state=lazy,modifier=Modifier.weight(1f).fillMaxWidth().onGloballyPositioned{viewport=it.boundsInRoot()}.semantics{contentDescription="시간계획 시간축"}
            .pointerInput(plan.id,axis.start,axis.end){detectDragGesturesAfterLongPress(
                onDragStart={p->pointerRoot=viewport.top+p.y;anchor=timeAtPointer();before=currentSelection;visited=emptySet();val keys=TimelineSelection.keysInRange(currentPlan,anchor.minusMinutes(3),anchor.plusMinutes(3));selectPaint=keys.any{it !in before}||keys.isEmpty();active=true;haptic.performHapticFeedback(HapticFeedbackType.LongPress);paint()},
                onDrag={change,delta->change.consume();pointerRoot+=delta.y;paint()},onDragEnd={active=false},onDragCancel={active=false})},contentPadding=PaddingValues(horizontal=8.dp,vertical=6.dp)){
            items(axis.count,key={"hour:$it"}){index->
                val slice=axis.slice(index);val newDay=index==0||slice.start.toLocalTime()==java.time.LocalTime.MIDNIGHT
                if(newDay){val days=java.time.temporal.ChronoUnit.DAYS.between(axis.start.toLocalDate(),slice.start.toLocalDate());Row(Modifier.fillMaxWidth().height(28.dp),verticalAlignment=Alignment.CenterVertically){HorizontalDivider(Modifier.width(14.dp),thickness=2.dp,color=ArmyristColors.PrimaryControl);Text(if(plan.dateDisplayMode==TimePlanDateDisplayMode.RELATIVE_D_DAY)if(days==0L)"D-Day"else"D${if(days>0)"+"else""}$days"else slice.start.format(DateTimeFormatter.ofPattern("MM.dd E",java.util.Locale.KOREAN)),Modifier.padding(horizontal=6.dp),fontSize=12.sp,fontWeight=FontWeight.Bold,color=ArmyristColors.PrimaryControl);HorizontalDivider(Modifier.weight(1f),thickness=2.dp,color=ArmyristColors.PrimaryControl)}}
                val actionGroups=plan.actions.filter{axis.index(it.scheduledDateTime)==index&&it.scheduledDateTime>=axis.start&&it.scheduledDateTime<=axis.end}.groupBy{it.scheduledDateTime.withSecond(0).withNano(0)}.toSortedMap()
                val height=maxOf(32f,slice.minutes.toFloat()*1.5f,if(labels)actionGroups.size*52f else 0f).dp
                BoxWithConstraints(Modifier.fillMaxWidth().height(height)){
                    val rail=46.dp;val dotWidth=if(labels)(maxWidth-rail)*.55f else 32.dp;val space=(maxWidth-rail-dotWidth).coerceAtLeast(20.dp);val laneWidth=space/laneCount
                    Canvas(Modifier.fillMaxSize()){
                        val x=rail.toPx();drawLine(Color(0xFFB8C4D6),Offset(x,0f),Offset(size.width,0f),1.dp.toPx());drawLine(ArmyristColors.Border,Offset(x,0f),Offset(x,size.height),1.dp.toPx())
                        if(slice.minutes>30){val y=size.height*.5f;drawLine(ArmyristColors.SoftBorder.copy(alpha=.4f),Offset(x,y),Offset(size.width,y),1.dp.toPx())}
                    }
                    Text(slice.start.format(DateTimeFormatter.ofPattern("HH:mm")),Modifier.width(rail).padding(top=2.dp).clickable(enabled=index==0){if(selectionMode)toggle("N:START")else onStart()},fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=if("N:START" in selectedKeys&&index==0)ArmyristColors.PrimaryControl else Color(0xFF334155))
                    plan.orderedEvents().forEach{event->
                        val a=DateTimePlanRules.arrival(event.timeSpec);val b=DateTimePlanRules.departure(event.timeSpec)?:a
                        if(a!=null&&b!=null&&((b>a&&b>slice.start&&a<slice.end)||(a==b&&axis.index(a)==index&&a>=axis.start&&a<=axis.end))){
                            val y=slice.fraction(a)*height.value;val bottom=slice.fraction(b)*height.value
                            val h=if(a==b)12f else maxOf(3f,bottom-y);val selected="N:${event.id}" in selectedKeys
                            Surface(modifier=Modifier.offset(x=rail+laneWidth*(lanes[event.id]?:0)+2.dp,y=y.dp).width((laneWidth-4.dp).coerceAtLeast(6.dp)).height(h.dp)
                                .semantics{contentDescription="일정 ${event.name}";this.selected=selected}.clickable{if(selectionMode)toggle("N:${event.id}")else onEvent(event)},
                                color=if(selected)ArmyristColors.PrimaryControl else Color(0xFFEDF2FF),
                                shape=RoundedCornerShape(topStart=if(a>=slice.start)10.dp else 0.dp,topEnd=if(a>=slice.start)10.dp else 0.dp,bottomStart=if(b<=slice.end)10.dp else 0.dp,bottomEnd=if(b<=slice.end)10.dp else 0.dp),border=BorderStroke(if(selected)2.dp else 0.dp,Color.Transparent)){
                                Row(Modifier.fillMaxSize()){Box(Modifier.width(3.dp).fillMaxHeight().background(ArmyristColors.PrimaryControl));
                                if(h>=24f && a>=slice.start && a<slice.end)Column(Modifier.padding(horizontal=7.dp,vertical=5.dp)){
                                    Text(event.name,color=if(selected)Color.White else ArmyristColors.PrimaryText,fontSize=14.sp,lineHeight=18.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                                    if(h>=40f)Text("${a.format(DateTimeFormatter.ofPattern("HH:mm"))}–${b.format(DateTimeFormatter.ofPattern("HH:mm"))}",fontSize=11.sp,lineHeight=14.sp,color=if(selected)Color.White else ArmyristColors.SecondaryText,maxLines=1,overflow=TextOverflow.Ellipsis)
                                }
                            }}
                        }
                    }
                    var previousBottom=0f
                    actionGroups.entries.forEachIndexed{groupIndex,(time,actions)->
                        val pointY=slice.fraction(time)*height.value
                        val labelTop=if(labels)(pointY-22f).coerceIn(previousBottom,(height.value-(actionGroups.size-groupIndex)*52f).coerceAtLeast(previousBottom)) else pointY-22f
                        previousBottom=labelTop+52f
                        val chosen=actions.any{"A:${it.id}" in selectedKeys}
                        val dotColor=if(chosen)ArmyristColors.PrimaryControl else markerColor(actions.first().markerColor)
                        val click={if(selectionMode)onSelection(if(actions.all{"A:${it.id}" in selectedKeys})selectedKeys-actions.map{"A:${it.id}"}.toSet()else selectedKeys+actions.map{"A:${it.id}"})else if(actions.size==1)markerId=actions.single().id else group=actions.map{it.id}}
                        if(labels)Canvas(Modifier.align(Alignment.TopEnd).width(dotWidth).fillMaxHeight()){
                            drawLine(dotColor.copy(alpha=.55f),Offset(10.dp.toPx(),pointY.dp.toPx()),Offset(24.dp.toPx(),(labelTop+22f).dp.toPx()),1.5.dp.toPx())
                        }
                        Box(Modifier.align(Alignment.TopEnd).offset(y=(pointY-6f).dp).width(dotWidth).height(12.dp).padding(start=4.dp),contentAlignment=if(labels)Alignment.CenterStart else Alignment.Center){
                            Box(Modifier.size(12.dp).background(dotColor,CircleShape))
                        }
                        Row(Modifier.align(Alignment.TopEnd).offset(y=labelTop.dp).width(dotWidth).height(44.dp)
                            .semantics{contentDescription="실시사항 ${actions.joinToString{it.content}} ${time.format(DateTimeFormatter.ofPattern("HH:mm"))}"}
                            .clickable(onClick=click).padding(start=if(labels)24.dp else 0.dp,end=2.dp),verticalAlignment=Alignment.CenterVertically){
                            if(labels)Surface(shape=RoundedCornerShape(8.dp),color=if(chosen)Color(0xFFDBE5FF)else Color.White,border=BorderStroke(1.dp,Color(0xFFD8E0EB)),modifier=Modifier.fillMaxWidth()){
                                Column(Modifier.padding(horizontal=6.dp,vertical=3.dp)){
                                    Text(time.format(DateTimeFormatter.ofPattern("HH:mm"))+(if(actions.size>1)" · ${actions.size}개"else if(actions.first().completionState==ActionCompletionState.COMPLETE)" · 완료"else ""),fontSize=11.sp,lineHeight=14.sp,fontWeight=FontWeight.Bold,color=ArmyristColors.PrimaryControl)
                                    Text(actions.first().content,fontSize=12.sp,lineHeight=16.sp,fontWeight=FontWeight.SemiBold,color=ArmyristColors.PrimaryText,maxLines=1,overflow=TextOverflow.Ellipsis)
                                }
                            } else if(actions.size>1)Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){Text(actions.size.toString(),color=Color.White,fontSize=8.sp,fontWeight=FontWeight.Bold)}
                        }
                    }
                }
            }
            item(key="end"){
                Row(Modifier.fillMaxWidth().clickable{if(selectionMode)toggle("N:END")else onEnd()},verticalAlignment=Alignment.CenterVertically){Text(axis.end.format(DateTimeFormatter.ofPattern("HH:mm")),fontSize=11.sp,fontWeight=FontWeight.Bold,modifier=Modifier.width(48.dp));HorizontalDivider(Modifier.weight(1f),thickness=2.dp,color=ArmyristColors.PrimaryControl);Text("종료",fontSize=11.sp,color=ArmyristColors.PrimaryControl)}
                val outside=plan.actions.filter{it.scheduledDateTime<axis.start||it.scheduledDateTime>axis.end}
                if(outside.isNotEmpty()){Text("시간축 밖 실시사항",Modifier.padding(top=12.dp),fontWeight=FontWeight.Bold);outside.forEach{a->TextButton(onClick={if(selectionMode)toggle("A:${a.id}")else markerId=a.id}){Text("● ${a.scheduledDateTime.format(DateTimeFormatter.ofPattern("MM.dd HH:mm"))} ${a.content}",maxLines=2)}}}
                if(!selectionMode){Row(Modifier.fillMaxWidth().padding(top=14.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){OutlinedButton(onClick=onAddMid,modifier=Modifier.weight(1f),contentPadding=PaddingValues(4.dp)){Text("+ 일정",fontSize=12.sp)};OutlinedButton(onClick=onAddFinal,modifier=Modifier.weight(1f),contentPadding=PaddingValues(4.dp)){Text("+ 종료지점",fontSize=12.sp)}};TextButton(onClick=onMemo){Text("메모 편집")}}
            }
        }
    }
    val groupItems=group.mapNotNull{id->plan.actions.find{it.id==id}}
    if(groupItems.isNotEmpty()&&markerId==null)ArmyristDialog(onDismissRequest={group=emptyList()},title={Text("실시사항")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){groupItems.forEach{a->Surface(Modifier.fillMaxWidth().clickable{markerId=a.id},shape=ArmyristControlShape,color=ArmyristColors.WorkSurface){Column(Modifier.padding(12.dp)){Text(a.content,fontWeight=FontWeight.Bold);Text("${a.scheduledDateTime.format(DateTimeFormatter.ofPattern("MM.dd HH:mm"))} · ${TimePlanExecutionRules.pointName(plan,a.parentPointId)}",style=MaterialTheme.typography.bodySmall)}}}}},confirmButton={TextButton(onClick={group=emptyList()}){Text("닫기")}})
    markerId?.let { id ->
        plan.actions.find { it.id == id }?.let { action ->
            ArmyristDialog(
                onDismissRequest = { markerId=null; group=emptyList() },
                title = { Text(action.content) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text("${action.scheduledDateTime.format(DateTimeFormatter.ofPattern("MM.dd HH:mm"))} · ${TimePlanExecutionRules.pointName(plan,action.parentPointId)}")
                        action.note?.takeIf { it.isNotBlank() }?.let { Text(it) }
                        Text(if(action.completionState==ActionCompletionState.COMPLETE) "완료" else "미완료")
                        Text("점 색상",fontWeight=FontWeight.Bold)
                        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            markerPalette.chunked(3).forEach { row ->
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    row.forEach { hex ->
                                        Box(Modifier.size(40.dp).background(markerColor(hex),CircleShape)
                                            .border(if(action.markerColor==hex)3.dp else 0.dp,ArmyristColors.Border,CircleShape)
                                            .semantics { contentDescription="점 색상 $hex" }
                                            .clickable { onSaveAction(action.copy(markerColor=hex)) })
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick={onSummary(action.parentPointId);markerId=null;group=emptyList()}) { Text("요약에서 편집") } },
                dismissButton = { TextButton(onClick={markerId=null;group=emptyList()}) { Text("닫기") } }
            )
        }
    }
}
