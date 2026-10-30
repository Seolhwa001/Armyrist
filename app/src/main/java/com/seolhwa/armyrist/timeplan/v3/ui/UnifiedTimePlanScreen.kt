@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.seolhwa.armyrist.timeplan.v3.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.seolhwa.armyrist.home.rememberLiveTime
import com.seolhwa.armyrist.voice.*
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.seolhwa.armyrist.*
import com.seolhwa.armyrist.calendar.ScheduleDateTimeFields
import com.seolhwa.armyrist.timeplan.v3.domain.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private val entryClock=DateTimeFormatter.ofPattern("HH:mm")
private val entryDate=DateTimeFormatter.ofPattern("MM.dd (EEE)",Locale.KOREAN)
private fun displayDay(plan:DateAwareTimePlan,date:LocalDate):String {
    val base=plan.start.value.value?.toLocalDate()
    if(plan.dateDisplayMode!=TimePlanDateDisplayMode.RELATIVE_D_DAY||base==null)return date.format(entryDate)
    val day=java.time.temporal.ChronoUnit.DAYS.between(base,date)
    return if(day==0L)"D-Day"else"D${if(day>0)"+"else""}$day"
}
private fun displayDateTime(plan:DateAwareTimePlan,time:LocalDateTime)=displayDay(plan,time.toLocalDate())+" "+time.format(entryClock)
private fun durationLabel(minutes:Long?):String=when {
    minutes==null||minutes==0L->"시각만"
    minutes<60->"${minutes}분"
    minutes%60==0L->"${minutes/60}시간"
    else->"${minutes/60}시간 ${minutes%60}분"
}
private fun entryTime(entry:UnifiedTimePlanEntry,plan:DateAwareTimePlan?=null)=if(entry.untimed)"시각 미설정" else entry.start.format(entryClock)+if(entry.ranged)"–"+(if(entry.end.toLocalDate()==entry.start.toLocalDate())entry.end.format(entryClock)else if(plan!=null)displayDateTime(plan,entry.end)else entry.end.format(DateTimeFormatter.ofPattern("MM.dd HH:mm")))else""

@Composable
internal fun UnifiedDateAwarePlanDetail(
    plan:DateAwareTimePlan,onHome:()->Unit,onBack:()->Unit,onResult:()->Unit,
    onSaveEntry:(String?,UnifiedTimePlanDraft)->Boolean,onComplete:(String,ActionCompletionState)->Boolean,
    onTrashSelection:(Set<String>)->Boolean,onAddEntries:(List<UnifiedTimePlanDraft>)->Boolean,onCommit:(DateAwareTimePlan)->Boolean,initialNodeId:String?=null
) {
    val context=LocalContext.current
    val preferences=remember(context){context.getSharedPreferences("unified_timeplan_view",0)}
    var timeline by rememberSaveable(plan.id){mutableStateOf(preferences.getBoolean("timeline",false))}
    var selecting by rememberSaveable(plan.id){mutableStateOf(false)}
    var selectedList by rememberSaveable(plan.id){mutableStateOf(emptyList<String>())}
    val selected=selectedList.toSet()
    var adding by rememberSaveable(plan.id){mutableStateOf(false)}
    var editKey by rememberSaveable(plan.id){mutableStateOf(initialNodeId?.takeIf{it!="START"&&it!="END"}?.let{if(it.startsWith("A:"))it else "N:$it"})}
    var periodOpen by rememberSaveable(plan.id){mutableStateOf(initialNodeId=="START"||initialNodeId=="END")}
    var groupsOpen by remember{mutableStateOf(false)}
    var voiceDrafts by remember{mutableStateOf<List<TimePlanVoiceDraft>?>(null)}
    var memoOpen by rememberSaveable(plan.id){mutableStateOf(false)}
    var titleOpen by rememberSaveable(plan.id){mutableStateOf(false)}
    var deleteKeys by remember { mutableStateOf<Set<String>?>(null) }
    var error by remember{mutableStateOf<String?>(null)}
    val entries=remember(plan){UnifiedTimePlan.entries(plan)}
    fun finishSelection(){selecting=false;selectedList=emptyList()}
    fun select(keys:Set<String>){selectedList=keys.toList();selecting=true}
    fun open(key:String){if(selecting)selectedList=(if(key in selected)selected-key else selected+key).toList()else editKey=key}
    BackHandler { if(selecting)finishSelection()else onBack() }
    Scaffold(containerColor=ArmyristColors.AppBackground,topBar={
        ArmyristTopBar(title=plan.title,subtitle="시간계획 · 실시사항",leadingLabel="홈",leadingIcon=ArmyristTopBarLeadingIcon.HOME,onLeading=onHome,
            secondaryLeadingLabel="메뉴",onSecondaryLeading=onBack,onTitleClick={titleOpen=true},actions={
                TextButton(onClick=onResult){Text("결과",color=ArmyristColors.OnDark)}
            })
    },bottomBar={
        Surface(color=ArmyristColors.WorkSurface,shadowElevation=6.dp){
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
                if(selecting){
                    Text("${selected.size}개 선택",Modifier.weight(1f),fontWeight=FontWeight.Bold)
                    TextButton(onClick={selectedList=entries.map{it.key}}){Text("전체")}
                    Button(enabled=selected.isNotEmpty(),onClick={deleteKeys=selected},modifier=Modifier.heightIn(min=48.dp).semantics{contentDescription="선택한 실시사항 휴지통으로 이동"}){Text("휴지통")}
                }else Button(onClick={adding=true},modifier=Modifier.fillMaxWidth().heightIn(min=50.dp),shape=ArmyristControlShape){Text("+ 실시사항",fontWeight=FontWeight.Bold)}
            }
        }
    }){padding->
        Column(Modifier.fillMaxSize().padding(padding)){
            OutlinedButton(onClick={periodOpen=true},modifier=Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp),shape=ArmyristControlShape){
                Column(Modifier.fillMaxWidth().padding(vertical=3.dp)){
                    Text("시간계획 기간 · 변경",style=MaterialTheme.typography.labelSmall,color=ArmyristColors.SecondaryText)
                    Text(listOfNotNull(plan.start.value.value,plan.end.value.value).joinToString(" → "){displayDateTime(plan,it)}.ifBlank{"기간 설정"},fontWeight=FontWeight.SemiBold)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                ViewModeButton("목록",!timeline,{timeline=false;preferences.edit().putBoolean("timeline",false).apply()},Modifier.weight(1f).testTag("timeplan-view-list"))
                ViewModeButton("시간표",timeline,{timeline=true;preferences.edit().putBoolean("timeline",true).apply()},Modifier.weight(1f).testTag("timeplan-view-timeline"))
                ViewModeButton(if(selecting)"완료"else"선택",selecting,{if(selecting)finishSelection()else selecting=true},Modifier.width(58.dp))
                OfflineVoiceButton(modifier=Modifier.width(64.dp),toolContext=VoiceToolContext.TIME_PLAN,label="음성",onTranscript={voiceDrafts=KoreanVoiceStructurer.timePlan(it,plan.start.value.value?.toLocalDate()?:LocalDate.now())},onMessage={error=it})
            }
            Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically){
                Text("실시사항 ${entries.size}개 · 완료 ${entries.count{it.value.completionState==ActionCompletionState.COMPLETE}}",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)
                if(entries.isNotEmpty())Text("길게 누르고 밀어 선택",style=MaterialTheme.typography.labelSmall,color=ArmyristColors.SecondaryText)
            }
            val liveNow=rememberLiveTime()
            if(TimePlanLiveStatus.isActive(plan,liveNow))Text("● 현재 진행 중인 시간계획",Modifier.fillMaxWidth().background(ArmyristColors.LiveSurface).padding(horizontal=14.dp,vertical=6.dp),color=ArmyristColors.LiveGreen,style=MaterialTheme.typography.labelMedium)
            Box(Modifier.weight(1f)){
                if(timeline)UnifiedTimeline(plan,entries,selected,::select,::open,{entry->if(!onComplete(entry.key,if(entry.value.completionState==ActionCompletionState.COMPLETE)ActionCompletionState.INCOMPLETE else ActionCompletionState.COMPLETE))error="완료 상태를 저장하지 못했습니다."},{memoOpen=true})
                else UnifiedEntryList(plan,entries,selected,::select,::open,{entry->if(!onComplete(entry.key,if(entry.value.completionState==ActionCompletionState.COMPLETE)ActionCompletionState.INCOMPLETE else ActionCompletionState.COMPLETE))error="완료 상태를 저장하지 못했습니다."},{memoOpen=true})
            }
        }
    }
    if(adding||editKey!=null){
        val entry=entries.firstOrNull{it.key==editKey}
        QuickTimePlanEntryEditor(entry,plan.start.value.value?:LocalDateTime.now().withSecond(0).withNano(0),{adding=false;editKey=null},
            onSave={draft->onSaveEntry(if(adding)null else editKey,draft)},onDelete=entry?.let{item->{deleteKeys=setOf(item.key)}},groups=plan.actionGroups,onManageGroups={groupsOpen=true},onComplete=entry?.let{item->{if(!onComplete(item.key,if(item.value.completionState==ActionCompletionState.COMPLETE)ActionCompletionState.INCOMPLETE else ActionCompletionState.COMPLETE))error="완료 상태를 저장하지 못했습니다."}})
    }
    if(groupsOpen)ActionGroupManagerDialog(plan,{groupsOpen=false},onCommit)
    voiceDrafts?.let{drafts->TimePlanVoiceReviewDialog(drafts,{voiceDrafts=null}){applied->
        val additions=applied.mapNotNull{d->d.dateTime?.takeIf{d.name.isNotBlank()&& (d.rangeEnd==null||d.rangeEnd>=it)}?.let{dt->UnifiedTimePlanDraft(d.name,dt,d.rangeEnd?.let{java.time.Duration.between(dt,it).toMinutes()}?.takeIf{it>0},d.note.ifBlank{null})}}
        if(additions.size!=applied.size||!onAddEntries(additions))error="음성 항목을 저장하지 못했습니다. 시각을 확인해주세요." else voiceDrafts=null
    }}
    if(periodOpen)PlanPeriodEditor(plan,{periodOpen=false}){changed->onCommit(changed).also{if(it)periodOpen=false}}
    if(memoOpen)InlineTextEditor("메모",plan.memo.orEmpty(),false,{memoOpen=false}){text->onCommit(plan.copy(memo=text.trim().ifBlank{null})).also{if(it)memoOpen=false}}
    if(titleOpen)InlineTextEditor("시간계획 이름",plan.title,true,{titleOpen=false}){text->onCommit(plan.copy(title=text.trim())).also{if(it)titleOpen=false}}
    deleteKeys?.let{keys->
        ArmyristDialog(onDismissRequest={deleteKeys=null},title={Text("실시사항을 휴지통으로 이동")},text={Text("선택한 ${keys.size}개 항목을 이동합니다. 시간계획의 시작·종료와 다른 항목은 유지됩니다.")},
            confirmButton={Button(onClick={
                val problem=runCatching{TimelineSelection.remove(plan,keys)}.exceptionOrNull()
                if(problem!=null)error=problem.message else if(onTrashSelection(keys)){editKey=null;adding=false;finishSelection()}else error="휴지통으로 옮기지 못했습니다."
                deleteKeys=null
            }){Text("휴지통으로")}},dismissButton={TextButton(onClick={deleteKeys=null}){Text("취소")}})
    }
    error?.let{message->ArmyristDialog(onDismissRequest={error=null},title={Text("확인")},text={Text(message)},confirmButton={TextButton(onClick={error=null}){Text("확인")}})}
}

@Composable private fun ViewModeButton(label:String,selected:Boolean,onClick:()->Unit,modifier:Modifier){
    OutlinedButton(onClick=onClick,modifier=modifier.heightIn(min=46.dp).semantics{this.selected=selected},shape=ArmyristControlShape,
        colors=ButtonDefaults.outlinedButtonColors(containerColor=if(selected)ArmyristColors.PrimaryControl else ArmyristColors.WorkSurface,contentColor=if(selected)Color.White else ArmyristColors.PrimaryText),
        border=BorderStroke(1.dp,if(selected)ArmyristColors.PrimaryControl else ArmyristColors.Border),contentPadding=PaddingValues(8.dp)){
        Text(label,fontWeight=FontWeight.Bold,maxLines=1)
    }
}

@Composable private fun PlanMemo(plan:DateAwareTimePlan,onEdit:()->Unit,modifier:Modifier=Modifier){
    Surface(modifier.fillMaxWidth().testTag("timeplan-inline-memo"),shape=ArmyristPanelShape,color=ArmyristColors.WorkSurface,border=BorderStroke(1.dp,ArmyristColors.Border)){
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){Text("메모",Modifier.weight(1f),fontWeight=FontWeight.Bold);TextButton(onClick=onEdit){Text("편집")}}
            Text(plan.memo?.takeIf{it.isNotBlank()}?:"메모가 없습니다.",color=if(plan.memo.isNullOrBlank())ArmyristColors.SecondaryText else ArmyristColors.PrimaryText)
        }
    }
}

@Composable private fun UnifiedEntryList(plan:DateAwareTimePlan,entries:List<UnifiedTimePlanEntry>,selected:Set<String>,onSelection:(Set<String>)->Unit,onOpen:(String)->Unit,onComplete:(UnifiedTimePlanEntry)->Unit,onMemo:()->Unit){
    val state=rememberLazyListState();val bounds=remember{mutableStateMapOf<String,Rect>()};var viewport by remember{mutableStateOf(Rect.Zero)}
    val currentEntries by rememberUpdatedState(entries);val currentSelected by rememberUpdatedState(selected);val publish by rememberUpdatedState(onSelection)
    var active by remember{mutableStateOf(false)};var pointer by remember{mutableFloatStateOf(0f)};var anchor by remember{mutableIntStateOf(0)}
    var original by remember{mutableStateOf(emptySet<String>())};var visited by remember{mutableStateOf(emptySet<String>())};var paintSelect by remember{mutableStateOf(true)}
    fun paint(){
        val key=bounds.entries.firstOrNull{pointer>=it.value.top&&pointer<=it.value.bottom}?.key?:return
        val index=currentEntries.indexOfFirst{it.key==key};if(index<0)return
        visited=visited+currentEntries.subList(minOf(anchor,index),maxOf(anchor,index)+1).map{it.key}
        publish(if(paintSelect)original+visited else original-visited)
    }
    LaunchedEffect(active){while(active){withFrameNanos{};val edge=48f;val speed=when{pointer<viewport.top+edge->-12f;pointer>viewport.bottom-edge->12f;else->0f};if(speed!=0f){state.scrollBy(speed);paint()}}}
    LazyColumn(state=state,modifier=Modifier.fillMaxSize().testTag("unified-entry-list").onGloballyPositioned{viewport=it.boundsInRoot()}.pointerInput(plan.id){
        detectDragGesturesAfterLongPress(onDragStart={p->
            pointer=viewport.top+p.y;val key=bounds.entries.firstOrNull{pointer in it.value.top..it.value.bottom}?.key
            if(key!=null){anchor=currentEntries.indexOfFirst{it.key==key}.coerceAtLeast(0);original=currentSelected;visited=emptySet();paintSelect=key !in original;active=true;paint()}
        },onDrag={change,delta->change.consume();pointer+=delta.y;paint()},onDragEnd={active=false},onDragCancel={active=false})
    },contentPadding=PaddingValues(12.dp,6.dp,12.dp,18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        if(entries.isEmpty())item{Text("‘+ 실시사항’을 눌러 내용과 시각을 입력하세요.",Modifier.padding(vertical=24.dp),color=ArmyristColors.SecondaryText)}
        itemsIndexed(entries,key={_,e->e.key}){index,entry->
            Column{
                if(!entry.untimed&&(index==0||entries[index-1].start.toLocalDate()!=entry.start.toLocalDate()))Text(displayDay(plan,entry.start.toLocalDate()),Modifier.padding(bottom=6.dp),style=MaterialTheme.typography.labelMedium,color=ArmyristColors.SecondaryText)
                EntryCard(entry,entry.key in selected,{onOpen(entry.key)},{onComplete(entry)},Modifier.onGloballyPositioned{bounds[entry.key]=it.boundsInRoot()},plan)
                DisposableEffect(entry.key){onDispose{bounds.remove(entry.key)}}
            }
        }
        item(key="memo"){PlanMemo(plan,onMemo)}
    }
}

@Composable private fun EntryCard(entry:UnifiedTimePlanEntry,chosen:Boolean,onOpen:()->Unit,onComplete:()->Unit,modifier:Modifier=Modifier,plan:DateAwareTimePlan?=null){
    val tint=scheduleColor(entry.value.markerColor)
    Surface(modifier.fillMaxWidth().semantics{selected=chosen;contentDescription="실시사항 ${entry.value.content} ${if(entry.untimed)"시각 미설정"else entry.start.format(entryClock)}"}.clickable(onClick=onOpen),shape=ArmyristControlShape,
        color=if(chosen)ArmyristColors.SecondaryControl else ArmyristColors.WorkSurface,border=BorderStroke(if(chosen)2.dp else 1.dp,if(chosen)ArmyristColors.PrimaryControl else ArmyristColors.Border)){
        Row(Modifier.height(IntrinsicSize.Min),verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.width(5.dp).fillMaxHeight().background(tint))
            Checkbox(checked=entry.value.completionState==ActionCompletionState.COMPLETE,onCheckedChange={onComplete()},modifier=Modifier.semantics{contentDescription="${entry.value.content} 완료"})
            Column(Modifier.weight(1f).padding(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                Text(entryTime(entry,plan)+" · "+durationLabel(entry.value.durationMinutes)+(if(entry.locked)" · 고정"else""),style=MaterialTheme.typography.labelMedium,color=tint)
                Text(entry.value.content,fontWeight=FontWeight.SemiBold)
                entry.value.note?.takeIf{it.isNotBlank()}?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=ArmyristColors.SecondaryText)}
            }
            Text("›",Modifier.padding(14.dp),color=ArmyristColors.SecondaryText)
        }
    }
}

@Composable private fun UnifiedTimeline(plan:DateAwareTimePlan,entries:List<UnifiedTimePlanEntry>,selected:Set<String>,onSelection:(Set<String>)->Unit,onOpen:(String)->Unit,onComplete:(UnifiedTimePlanEntry)->Unit,onMemo:()->Unit){
    val axis=remember(plan){UnifiedTimePlan.scale(plan)}
    if(axis==null){UnifiedEntryList(plan,entries,selected,onSelection,onOpen,onComplete,onMemo);return}
    val lanes=remember(entries){UnifiedTimePlan.lanes(entries)};val laneCount=(lanes.values.maxOrNull()?:0)+1
    val horizontal=rememberScrollState();val lazy=rememberLazyListState();val density=LocalDensity.current
    var viewport by remember{mutableStateOf(Rect.Zero)};var active by remember{mutableStateOf(false)};var pointer by remember{mutableFloatStateOf(0f)}
    var anchor by remember{mutableStateOf(axis.start)};var original by remember{mutableStateOf(emptySet<String>())};var visited by remember{mutableStateOf(emptySet<String>())};var paintSelect by remember{mutableStateOf(true)}
    val currentPlan by rememberUpdatedState(plan);val currentSelected by rememberUpdatedState(selected);val publish by rememberUpdatedState(onSelection)
    fun pointerTime():LocalDateTime {
        val y=pointer-viewport.top;val item=lazy.layoutInfo.visibleItemsInfo.firstOrNull{y>=it.offset&&y<=it.offset+it.size&&it.key.toString().startsWith("hour:")}
            ?:return if(y<0)axis.start else axis.end
        val index=item.key.toString().removePrefix("hour:").toInt();val slice=axis.slice(index)
        val header=if(index==0||slice.start.toLocalTime()==LocalTime.MIDNIGHT)with(density){28.dp.toPx()}else 0f
        val fraction=((y-item.offset-header)/(item.size-header).coerceAtLeast(1f)).coerceIn(0f,1f)
        return slice.start.plusSeconds((slice.minutes*60*fraction).toLong())
    }
    fun paint(){visited=visited+UnifiedTimePlan.keysInRange(currentPlan,anchor,pointerTime());publish(if(paintSelect)original+visited else original-visited)}
    LaunchedEffect(active){while(active){withFrameNanos{};val speed=when{pointer<viewport.top+48f->-12f;pointer>viewport.bottom-48f->12f;else->0f};if(speed!=0f){lazy.scrollBy(speed);paint()}}}
    Column(Modifier.fillMaxSize()){
        if(laneCount>1)Text("겹치는 항목은 좌우로 이동해 확인할 수 있습니다.",Modifier.padding(horizontal=14.dp,vertical=4.dp),style=MaterialTheme.typography.labelSmall,color=ArmyristColors.SecondaryText)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()){
            val viewportWidth=maxWidth;val chartWidth=maxOf(viewportWidth,60.dp+156.dp*laneCount)
            val laneWidth=(chartWidth-76.dp)/laneCount
            Box(Modifier.fillMaxSize().horizontalScroll(horizontal)){
                LazyColumn(state=lazy,modifier=Modifier.width(chartWidth).fillMaxHeight().testTag("unified-timeline").semantics{contentDescription="시간계획 시간축"}.onGloballyPositioned{viewport=it.boundsInRoot()}.pointerInput(plan.id,axis.start,axis.end){
                    detectDragGesturesAfterLongPress(onDragStart={p->pointer=viewport.top+p.y;anchor=pointerTime();original=currentSelected;visited=emptySet();paintSelect=UnifiedTimePlan.keysInRange(currentPlan,anchor.minusMinutes(1),anchor.plusMinutes(1)).any{it !in original};active=true;paint()},onDrag={change,delta->change.consume();pointer+=delta.y;paint()},onDragEnd={active=false},onDragCancel={active=false})
                },contentPadding=PaddingValues(horizontal=8.dp,vertical=6.dp)){
                    items(axis.count,key={"hour:$it"}){index->
                        val slice=axis.slice(index);val day=index==0||slice.start.toLocalTime()==LocalTime.MIDNIGHT
                        Column{
                            if(day)Row(Modifier.height(28.dp),verticalAlignment=Alignment.CenterVertically){Text(displayDay(plan,slice.start.toLocalDate()),Modifier.padding(start=4.dp,end=8.dp),color=ArmyristColors.PrimaryControl,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.labelMedium);HorizontalDivider(Modifier.weight(1f))}
                            Box(Modifier.fillMaxWidth().height(100.dp)){
                                Canvas(Modifier.fillMaxSize()){drawLine(ArmyristColors.Border,androidx.compose.ui.geometry.Offset(52.dp.toPx(),0f),androidx.compose.ui.geometry.Offset(size.width,0f),1.dp.toPx())}
                                Text(slice.start.format(entryClock),Modifier.padding(top=3.dp).width(50.dp),style=MaterialTheme.typography.labelSmall,color=ArmyristColors.SecondaryText)
                                entries.filterNot{it.untimed}.forEach{entry->
                                    val hit=if(entry.ranged)entry.start<slice.end&&entry.end>slice.start else entry.start>=slice.start&&entry.start<slice.end
                                    if(hit){
                                        val y=slice.fraction(entry.start)*100f
                                        val raw=if(entry.ranged)(slice.fraction(entry.end)*100f-y)else 48f
                                        val h=raw.coerceIn(48f,100f);val top=y.coerceIn(0f,100f-h)
                                        val first=entry.start>=slice.start&&entry.start<slice.end
                                        val tint=scheduleColor(entry.value.markerColor);val chosen=entry.key in selected
                                        Surface(Modifier.offset(x=56.dp+(laneWidth+8.dp)*(lanes[entry.key]?:0),y=top.dp).width(laneWidth).height(h.dp)
                                            .semantics{this.selected=chosen;contentDescription="실시사항 ${entry.value.content} ${entry.start.format(entryClock)}"}.clickable{onOpen(entry.key)},
                                            color=if(chosen)ArmyristColors.SecondaryControl else tint.copy(alpha=.10f),shape=RoundedCornerShape(topStart=if(first)12.dp else 0.dp,topEnd=if(first)12.dp else 0.dp,bottomStart=if(!entry.ranged||entry.end<=slice.end)12.dp else 0.dp,bottomEnd=if(!entry.ranged||entry.end<=slice.end)12.dp else 0.dp),border=if(chosen)BorderStroke(2.dp,ArmyristColors.PrimaryControl)else null){
                                            Box(Modifier.fillMaxSize()){
                                            Box(Modifier.width(3.dp).fillMaxHeight().background(tint))
                                            if(first)Column(Modifier.padding(horizontal=12.dp,vertical=5.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){
                                                Text(entryTime(entry,plan),style=MaterialTheme.typography.labelSmall,color=tint,maxLines=1,overflow=TextOverflow.Ellipsis)
                                                Text((if(entry.value.completionState==ActionCompletionState.COMPLETE)"✓ "else"")+entry.value.content,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                                                if(h>=80f)entry.value.note?.takeIf{it.isNotBlank()}?.let{Text(it,style=MaterialTheme.typography.labelSmall,color=ArmyristColors.SecondaryText,maxLines=2,overflow=TextOverflow.Ellipsis)}
                                            }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item{Text(displayDateTime(plan,axis.end),Modifier.padding(vertical=10.dp),style=MaterialTheme.typography.labelSmall,color=ArmyristColors.SecondaryText)}
                    entries.filter{it.untimed}.forEach{entry->item(key=entry.key){EntryCard(entry,entry.key in selected,{onOpen(entry.key)},{onComplete(entry)},Modifier.width(viewportWidth).padding(bottom=8.dp),plan)}}
                    item(key="memo"){PlanMemo(plan,onMemo,Modifier.width(viewportWidth-16.dp).offset{IntOffset(horizontal.value,0)}.padding(bottom=18.dp))}
                }
            }
        }
    }
}

@Composable internal fun TimePlanEditorFrame(title:String,onDismiss:()->Unit,content:@Composable ColumnScope.()->Unit){
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnClickOutside=false)){
        BoxWithConstraints(Modifier.fillMaxSize().imePadding().padding(14.dp),contentAlignment=Alignment.Center){
            Card(Modifier.widthIn(max=560.dp).fillMaxWidth().height(maxHeight*.82f),shape=ArmyristPanelShape,colors=CardDefaults.cardColors(containerColor=ArmyristColors.WorkSurface)){
                Column(Modifier.fillMaxSize().padding(14.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);TextButton(onClick=onDismiss){Text("취소")}}
                    content()
                }
            }
        }
    }
}

@Composable internal fun QuickTimePlanEntryEditor(entry:UnifiedTimePlanEntry?,base:LocalDateTime,onDismiss:()->Unit,onSave:(UnifiedTimePlanDraft)->Boolean,onDelete:(()->Unit)?=null,groups:List<TimePlanActionGroup> = emptyList(),onManageGroups:(()->Unit)?=null,onComplete:(()->Unit)?=null){
    var title by remember(entry?.key){mutableStateOf(entry?.value?.content.orEmpty())}
    var time by remember(entry?.key){mutableStateOf(entry?.takeUnless{it.untimed}?.start?:base)}
    var durationOn by remember(entry?.key){mutableStateOf((entry?.value?.durationMinutes?:0)>0)}
    var hours by remember(entry?.key){mutableStateOf(((entry?.value?.durationMinutes?:30)/60).toString())}
    var minutes by remember(entry?.key){mutableStateOf(((entry?.value?.durationMinutes?:30)%60).toString())}
    var endInput by remember(entry?.key){mutableStateOf((entry?.end?:base.plusMinutes(30)))}
    var useEnd by remember(entry?.key){mutableStateOf((entry?.value?.durationMinutes?:0)>0)}
    var note by remember(entry?.key){mutableStateOf(entry?.value?.note.orEmpty())}
    var color by remember(entry?.key){mutableStateOf(entry?.value?.markerColor?:"#2E55CC")}
    var mode by remember(entry?.key){mutableStateOf(entry?.value?.notificationMode?:ActionNotificationMode.NONE)}
    var locked by remember(entry?.key){mutableStateOf(entry?.locked?:false)}
    var groupId by remember(entry?.key){mutableStateOf(entry?.value?.groupId)}
    var more by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    val duration=if(durationOn&&useEnd)java.time.Duration.between(time,endInput).toMinutes()else if(durationOn)hours.toLongOrNull()?.let{h->minutes.toLongOrNull()?.takeIf{it in 0..59}?.let{m->h*60+m}}else null
    val valid=title.isNotBlank()&&(!durationOn||(duration!=null&&duration>0))
    TimePlanEditorFrame(if(entry==null)"실시사항 추가"else"실시사항 편집",onDismiss){
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
            OutlinedTextField(title,{title=it},label={Text("내용")},modifier=Modifier.fillMaxWidth(),minLines=1,maxLines=3)
            if(entry!=null&&onComplete!=null)Row(verticalAlignment=Alignment.CenterVertically){Checkbox(entry.value.completionState==ActionCompletionState.COMPLETE,{onComplete()});Text("완료")}
            Text(if(durationOn)"시작"else"실시 시각",style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold)
            ScheduleDateTimeFields(time,{changed->val shift=java.time.Duration.between(time,changed);time=changed;endInput=endInput.plus(shift)})
            Text("언제까지 진행하나요?",style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){
                FilterChip(selected=!durationOn,onClick={durationOn=false;useEnd=false},label={Text("시각만")})
                FilterChip(selected=durationOn&&useEnd,onClick={if(!useEnd)endInput=time.plusMinutes(duration?.takeIf{it>0}?:30);durationOn=true;useEnd=true},label={Text("종료시간 선택")})
                FilterChip(selected=durationOn&&!useEnd,onClick={if(useEnd){val d=duration?:30;hours=(d/60).toString();minutes=(d%60).toString()};durationOn=true;useEnd=false},label={Text("직접입력")})
            }
            if(durationOn&&useEnd){
                Text("종료",fontWeight=FontWeight.Bold)
                ScheduleDateTimeFields(endInput,{endInput=it})
                if(endInput<=time)Text("종료는 시작보다 늦게 선택해주세요. 다음 날이면 날짜도 바꿔주세요.",color=ArmyristColors.Danger)
                else Text("총 ${durationLabel(duration)}",color=ArmyristColors.PrimaryControl,fontWeight=FontWeight.SemiBold)
            }
            if(!durationOn)Text("종료 없이 해당 시각에 한 번 표시합니다.",style=MaterialTheme.typography.bodySmall,color=ArmyristColors.SecondaryText)
            if(durationOn&&!useEnd){
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedTextField(hours,{hours=it.filter(Char::isDigit).take(6)},label={Text("시간")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.weight(1f))
                    OutlinedTextField(minutes,{minutes=it.filter(Char::isDigit).take(2)},label={Text("분")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.weight(1f))
                }
                duration?.let{d->runCatching{time.plusMinutes(d)}.getOrNull()?.let{end->Text("종료 · ${end.format(DateTimeFormatter.ofPattern("MM.dd HH:mm"))}",color=ArmyristColors.PrimaryControl,style=MaterialTheme.typography.labelMedium)}}
            }
            TextButton(onClick={more=!more}){Text(if(more)"추가 설정 접기 ∧"else"메모 · 색상 · 알림 ∨")}
            if(more){
                OutlinedTextField(note,{note=it},label={Text("메모")},modifier=Modifier.fillMaxWidth(),minLines=2)
                ScheduleColorPicker(color){color=it}
                if(groups.isNotEmpty()){
                    Text("그룹",fontWeight=FontWeight.Bold)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                        FilterChip(selected=groupId==null,onClick={groupId=null},label={Text("미지정")})
                        groups.sortedBy{it.order}.forEach{g->FilterChip(selected=groupId==g.id,onClick={groupId=g.id},label={Text(g.name)})}
                    }
                }
                onManageGroups?.let{TextButton(onClick=it){Text("그룹 관리")}}
                Text("알림",fontWeight=FontWeight.Bold)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    ActionNotificationMode.entries.forEach{candidate->FilterChip(selected=mode==candidate,onClick={mode=candidate},label={Text(when(candidate){ActionNotificationMode.NONE->"없음";ActionNotificationMode.SIMPLE->"간단한 알림";ActionNotificationMode.MUSIC->"음악 알림"})})}
                }

            }
            Row(verticalAlignment=Alignment.CenterVertically){Checkbox(checked=locked,onCheckedChange={locked=it});Text("시각 고정")}
            if(locked)Text("기존 고정 설정이 켜져 있습니다. 시각·소요 시간을 직접 변경하면 고정을 해제하고 저장합니다.",style=MaterialTheme.typography.bodySmall,color=ArmyristColors.SecondaryText)
            error?.let{Text(it,color=ArmyristColors.Danger)}
        }
        Row(Modifier.fillMaxWidth().padding(top=10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            if(onDelete!=null)OutlinedButton(onClick=onDelete,modifier=Modifier.heightIn(min=48.dp)){Text("삭제")}
            Button(enabled=valid,onClick={
                val draft=UnifiedTimePlanDraft(title.trim(),time,if(durationOn)duration else null,note.trim().ifBlank{null},color,mode,locked && (entry==null || (time==entry.start && (if(durationOn)duration else null)==entry.value.durationMinutes)),groupId)
                if(onSave(draft))onDismiss()else error="저장하지 못했습니다. 계획 데이터와 저장 공간을 확인한 뒤 다시 시도해주세요."
            },modifier=Modifier.weight(1f).heightIn(min=48.dp)){Text("저장",fontWeight=FontWeight.Bold)}
        }
    }
}

@Composable private fun InlineTextEditor(title:String,initial:String,required:Boolean,onDismiss:()->Unit,onSave:(String)->Boolean){
    var text by remember{mutableStateOf(initial)};var error by remember{mutableStateOf(false)}
    TimePlanEditorFrame(title,onDismiss){
        OutlinedTextField(text,{text=it},modifier=Modifier.weight(1f).fillMaxWidth(),label={Text(title)})
        if(error)Text("저장하지 못했습니다.",color=ArmyristColors.Danger)
        Button(enabled=!required||text.isNotBlank(),onClick={if(!onSave(text))error=true},modifier=Modifier.fillMaxWidth().padding(top=10.dp).heightIn(min=48.dp)){Text("저장")}
    }
}

@Composable private fun PlanPeriodEditor(plan:DateAwareTimePlan,onDismiss:()->Unit,onSave:(DateAwareTimePlan)->Boolean){
    var start by remember{mutableStateOf(plan.start.value.value?:LocalDateTime.now().withSecond(0).withNano(0))}
    var end by remember{mutableStateOf(plan.end.value.value?:start.plusHours(1))}
    var startLocked by remember{mutableStateOf(plan.start.dateTimeLocked)};var endLocked by remember{mutableStateOf(plan.end.dateTimeLocked)}
    var absolute by remember{mutableStateOf(plan.dateDisplayMode==TimePlanDateDisplayMode.ABSOLUTE)};var error by remember{mutableStateOf<String?>(null)}
    TimePlanEditorFrame("시간계획 기간",onDismiss){
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text("시작",fontWeight=FontWeight.Bold);ScheduleDateTimeFields(start,{start=it})
            Row(verticalAlignment=Alignment.CenterVertically){Checkbox(startLocked,{startLocked=it});Text("시작 시각 고정")}
            Text("종료",fontWeight=FontWeight.Bold);ScheduleDateTimeFields(end,{end=it})
            Row(verticalAlignment=Alignment.CenterVertically){Checkbox(endLocked,{endLocked=it});Text("종료 시각 고정")}
            Row(verticalAlignment=Alignment.CenterVertically){Switch(absolute,{absolute=it});Text(if(absolute)"날짜로 표시"else"D-Day로 표시")}
            Text("기간을 바꿔도 실시사항 시각은 유지됩니다. 시간표에는 기간 밖의 항목도 표시합니다.",style=MaterialTheme.typography.bodySmall,color=ArmyristColors.SecondaryText)
            error?.let{Text(it,color=ArmyristColors.Danger)}
        }
        Button(onClick={
            when{
                end<start->error="종료는 시작 이후로 입력해주세요."
                plan.start.dateTimeLocked&&startLocked&&start!=plan.start.value.value->error="시작 시각 고정을 해제해주세요."
                plan.end.dateTimeLocked&&endLocked&&end!=plan.end.value.value->error="종료 시각 고정을 해제해주세요."
                else->if(onSave(plan.copy(start=DateTimeAnchor(DateTimeValue.explicit(start),startLocked),end=DateTimeAnchor(DateTimeValue.explicit(end),endLocked),dateDisplayMode=if(absolute)TimePlanDateDisplayMode.ABSOLUTE else TimePlanDateDisplayMode.RELATIVE_D_DAY)))onDismiss()else error="기간을 저장하지 못했습니다."
            }
        },modifier=Modifier.fillMaxWidth().padding(top=10.dp).heightIn(min=48.dp)){Text("저장")}
    }
}
