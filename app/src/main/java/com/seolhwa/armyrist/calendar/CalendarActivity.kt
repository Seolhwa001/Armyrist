@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.seolhwa.armyrist.calendar

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seolhwa.armyrist.*
import com.seolhwa.armyrist.notification.ChecklistNotificationManager
import com.seolhwa.armyrist.notification.TimePlanActionNotificationManager
import com.seolhwa.armyrist.stage2.domain.ChecklistItem
import com.seolhwa.armyrist.timeplan.v3.domain.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CalendarActivity: ComponentActivity() {
    private var revision by mutableIntStateOf(0)
    override fun onResume(){super.onResume();revision++}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ArmyristTheme { CalendarScreen(revision,intent.getStringExtra("calendarDate"),{finish()}) { plan,node ->
            startActivity(Intent(this,TimePlanActivity::class.java).putExtra("calendarPlanId",plan).putExtra("calendarNodeId",node))
        } } }
    }
}

@Composable
internal fun CalendarScreen(externalRevision: Int, initialDate: String?, onBack: ()->Unit,onPlan: (String,String?)->Unit) {
    val context=LocalContext.current;val app=context.applicationContext as ArmyristApplication
    val notificationRevision by app.coreSuiteRepository.notificationCompletionRevision.collectAsState()
    var revision by remember{mutableIntStateOf(0)}
    val plans=remember(externalRevision,revision){app.dateAwareTimePlanRepository.getPlans()}
    val lists=remember(externalRevision,revision,notificationRevision){app.coreSuiteRepository.getChecklists()}
    var dateText by rememberSaveable{mutableStateOf(runCatching{LocalDate.parse(initialDate)}.getOrDefault(LocalDate.now()).toString())};val date=LocalDate.parse(dateText)
    var monthText by rememberSaveable{mutableStateOf(YearMonth.from(date).toString())};val month=YearMonth.parse(monthText)
    var adding by remember{mutableStateOf(false)};var kind by remember{mutableStateOf<AgendaKind?>(null)};var editing by remember{mutableStateOf<AgendaEntry?>(null)}
    val prefs=remember{CalendarPreferences(context)};val cover=remember(externalRevision,revision){prefs.cover()};val scope=rememberCoroutineScope();var coverError by remember{mutableStateOf(false)}
    var collapsed by remember{mutableStateOf(prefs.collapsed(false))}
    val dayImages=remember(externalRevision,revision,month){prefs.dayImages(month)}
    val dayImage=remember(externalRevision,revision,date){prefs.dayImage(date)}
    var imageDate by rememberSaveable{mutableStateOf<String?>(null)}
    val dayPicker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->
        val target=imageDate?.let{LocalDate.parse(it)}
        if(uri!=null&&target!=null)scope.launch{coverError=!withContext(Dispatchers.IO){prefs.importDayImage(target,uri)};revision++}
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null)scope.launch{coverError=!withContext(Dispatchers.IO){prefs.importCover(uri)};revision++}}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){
        ChecklistNotificationManager.reconcile(context,app.coreSuiteRepository);TimePlanActionNotificationManager.reconcile(context,app.dateAwareTimePlanRepository)
    }
    fun afterSave(notify:Boolean){
        revision++;kind=null;editing=null;adding=false
        ChecklistNotificationManager.reconcile(context,app.coreSuiteRepository)
        if(notify && Build.VERSION.SDK_INT>=33)permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    if(kind!=null) {
        CalendarEntryEditor(kind!!,date,editing,{kind=null;editing=null}){afterSave(it)}
        return
    }
    BackHandler(adding){adding=false}
    Scaffold(topBar={TopAppBar(title={Text("달력")},navigationIcon={TextButton(onClick=onBack){Text("뒤로")}},actions={TextButton(onClick={com.seolhwa.armyrist.widget.CalendarWidgetProvider.requestPin(context)}){Text("위젯 추가")};ArmyristHelpButton(ArmyristHelpTopic.CALENDAR)})},containerColor=ArmyristColors.AppBackground) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item { Surface(shape=ArmyristPanelShape,color=ArmyristColors.WorkSurface){Column{
                Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){
                    Text("${month.year}년 ${month.monthValue}월",Modifier.weight(1f),fontWeight=FontWeight.Bold)
                    TextButton(onClick={val next=!collapsed;if(prefs.setCollapsed(false,next))collapsed=next}){Text(if(collapsed)"달력 펼치기 ∨"else"달력 접기 ∧")}
                }
                if(!collapsed){CalendarCover(cover);MonthCalendar(month,date,plans,lists,{monthText=it.toString();dateText=it.atDay(date.dayOfMonth.coerceAtMost(it.lengthOfMonth())).toString()},{dateText=it.toString()},dayImages=dayImages)}
            }} }
            item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton(onClick={picker.launch("image/*")}){Text(if(cover==null)"커버 이미지 추가"else"커버 변경")};if(cover!=null)TextButton(onClick={if(prefs.clearCover())revision++ else coverError=true}){Text("커버 제거")}} }
            if(coverError)item{Text("이미지를 저장하지 못했습니다. 다른 이미지를 선택해주세요.",color=MaterialTheme.colorScheme.error)}
            item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){AgendaKind.entries.forEach { Text("● ${it.label}",color=it.color(),style=MaterialTheme.typography.labelSmall) }} }
            item { Row(verticalAlignment=Alignment.CenterVertically){Text("${date.monthValue}월 ${date.dayOfMonth}일",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Button(onClick={adding=!adding}){Text(if(adding)"닫기"else"+ 일정 추가")}} }
            item {
                Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
                    CalendarCover(dayImage)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){
                        TextButton(onClick={imageDate=date.toString();dayPicker.launch("image/*")}){Text(if(dayImage==null)"이 날짜 대표이미지 추가"else"이 날짜 대표이미지 변경")}
                        if(dayImage!=null)TextButton(onClick={if(prefs.clearDayImage(date))revision++else coverError=true}){Text("제거")}
                    }
                }
            }
            if(adding)item { Surface(shape=ArmyristPanelShape,color=ArmyristColors.WorkSurface){Column(Modifier.padding(12.dp)){Text("추가할 항목을 선택하세요",fontWeight=FontWeight.Bold);AgendaKind.entries.forEach { type->TextButton(onClick={kind=type;editing=null}){Text("+ ${type.label}")} }}} }
            val entries=CalendarAgenda.entriesOn(date,plans,lists)
            if(entries.isEmpty())item{Text("이 날짜에 예정된 항목이 없습니다.",Modifier.padding(vertical=20.dp),color=ArmyristColors.SecondaryText)}
            fun openEntry(entry: AgendaEntry){if(entry.kind==AgendaKind.PLAN||entry.kind==AgendaKind.EVENT)onPlan(entry.ownerId,entry.itemId)else{editing=entry;kind=entry.kind}}
            entries.filter{it.kind!=AgendaKind.CHECKLIST}.groupBy{it.ownerId}.forEach{(id,group)->
                plans.find{it.id==id}?.let{plan->item(key="plan:$id"){CalendarPlanBranch(plan,group,::openEntry)}}
            }
            val checklistEntries=entries.filter{it.kind==AgendaKind.CHECKLIST}
            if(checklistEntries.isNotEmpty())item{Text("체크리스트",fontWeight=FontWeight.Bold)}
            checklistEntries.forEach{entry->item(key="checklist:${entry.ownerId}:${entry.itemId}"){AgendaRow(entry){openEntry(entry)}}}
            item{Text("날짜가 지정된 항목을 함께 표시합니다. D-Day 계획은 달력에서 제외됩니다.",style=MaterialTheme.typography.bodySmall,color=ArmyristColors.SecondaryText)}
        }
    }
}

/** Full-screen, scrollable editor keeps the save controls usable with the keyboard open. */
@Composable
internal fun CalendarEntryEditor(kind: AgendaKind,date: LocalDate,entry: AgendaEntry?,onCancel: ()->Unit,onSaved:(Boolean)->Unit) {
    val context=LocalContext.current;val app=context.applicationContext as ArmyristApplication
    val repo=app.dateAwareTimePlanRepository;val core=app.coreSuiteRepository
    val plans=remember{repo.getPlans().filter{!it.legacyDateMigrationRequired&&it.dateDisplayMode==TimePlanDateDisplayMode.ABSOLUTE}}
    val lists=remember{core.getChecklists()}
    val oldAction=remember{entry?.let{repo.getPlan(it.ownerId)?.actions?.find{a->a.id==it.itemId}}}
    val oldItem:ChecklistItem?=remember{entry?.let{core.getChecklist(it.ownerId)?.items?.find{a->a.id==it.itemId}}}
    var owner by rememberSaveable{mutableStateOf(entry?.ownerId ?: if(kind==AgendaKind.CHECKLIST)lists.firstOrNull()?.id.orEmpty() else plans.firstOrNull()?.id.orEmpty())}
    var node by rememberSaveable{mutableStateOf(oldAction?.parentPointId?:DateTimePlanRules.START_ID)}
    var name by rememberSaveable{mutableStateOf(entry?.title.orEmpty())};var note by rememberSaveable{mutableStateOf(oldAction?.note?:oldItem?.note.orEmpty())}
    var newList by rememberSaveable{mutableStateOf("새 체크리스트")}
    var timeText by rememberSaveable{mutableStateOf((entry?.start?:date.atTime(9,0)).toString())};val time=LocalDateTime.parse(timeText)
    var endText by rememberSaveable{mutableStateOf(time.plusHours(1).toString())};val end=LocalDateTime.parse(endText)
    var mode by rememberSaveable{mutableStateOf(oldAction?.notificationMode?:oldItem?.notificationMode?:ActionNotificationMode.NONE)}
    var error by remember{mutableStateOf<String?>(null)}
    BackHandler(onBack=onCancel)
    fun save(){
        if(name.isBlank()){error="이름을 입력해주세요.";return}
        val now=System.currentTimeMillis().toString()
        val saved=when(kind){
            AgendaKind.PLAN -> if(end.isBefore(time)){error="종료는 시작 이후로 지정해주세요.";false}else repo.commit(DateTimePlanRules.normalizeTopology(DateAwareTimePlan(UUID.randomUUID().toString(),name.trim(),start=DateTimeAnchor(DateTimeValue.explicit(time)),end=DateTimeAnchor(DateTimeValue.explicit(end)),memo=note.trim().ifBlank{null},createdAt=now,updatedAt=now)))
            AgendaKind.EVENT -> repo.getPlan(owner)?.let{latest-> val event=DateTimeEvent(UUID.randomUUID().toString(),TimeEventKind.MIDWAY,latest.midwayEvents.size,name.trim(),EventDateTimeSpec.Single(DateTimeValue.explicit(time)),note.trim().ifBlank{null});repo.commit(DateTimePlanRules.normalizeTopology(latest.copy(midwayEvents=latest.midwayEvents+event,updatedAt=now)))}?:false
            AgendaKind.ACTION -> repo.getPlan(owner)?.let{latest->
                val existing=oldAction?.let{old->latest.actions.find{it.id==old.id}}
                if(oldAction!=null&&existing==null)false else {
                    val item=(existing?:TimePlanActionItem(parentPointId=node,content=name.trim(),scheduledDateTime=time,order=latest.actions.size)).copy(content=name.trim(),scheduledDateTime=time,note=note.trim().ifBlank{null},notificationEnabled=mode!=ActionNotificationMode.NONE,notificationMode=mode,updatedAt=now)
                    repo.saveAction(owner,item)
                }
            }?:false
            AgendaKind.CHECKLIST -> if(oldItem!=null){
                if(core.getChecklist(owner)?.items?.none{it.id==oldItem.id}!=false)false else core.editChecklistItem(owner,oldItem.id,name,note,oldItem.groupId,mode!=ActionNotificationMode.NONE,time.hour*60+time.minute,oldItem.notificationSoundUri,time.toLocalDate().toString(),mode)
            }else core.saveCalendarItem(owner.ifBlank{null},newList,name,note,mode!=ActionNotificationMode.NONE,time.hour*60+time.minute,time.toLocalDate().toString(),mode)
        }
        if(saved)onSaved(mode!=ActionNotificationMode.NONE)else if(error==null)error="저장하지 못했습니다. 연결된 항목과 입력 내용을 확인해주세요."
    }
    Scaffold(modifier=Modifier.imePadding(),containerColor=ArmyristColors.AppBackground,topBar={TopAppBar(title={Text("${kind.label} ${if(entry==null)"추가"else"편집"}")},navigationIcon={TextButton(onClick=onCancel){Text("취소")}})},bottomBar={Surface(color=ArmyristColors.RaisedSurface){Button(onClick={save()},modifier=Modifier.fillMaxWidth().navigationBarsPadding().padding(14.dp)){Text("저장")}}}){padding->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
            if(kind==AgendaKind.EVENT||kind==AgendaKind.ACTION){
                Text("연결할 시간계획",fontWeight=FontWeight.Bold)
                if(plans.isEmpty())Text("먼저 시간계획을 추가해주세요.",color=MaterialTheme.colorScheme.error)
                plans.forEach{plan->FilterChip(selected=owner==plan.id,enabled=entry==null,onClick={owner=plan.id;node=DateTimePlanRules.START_ID},label={Text(plan.title)})}
                if(kind==AgendaKind.ACTION&&entry==null)plans.find{it.id==owner}?.let{plan->Text("연결할 일정",fontWeight=FontWeight.Bold);DateTimePlanRules.nodeIds(plan).forEach{id->FilterChip(selected=node==id,onClick={node=id},label={Text(TimePlanExecutionRules.pointName(plan,id))})}}
            }
            if(kind==AgendaKind.CHECKLIST&&entry==null){Text("저장할 체크리스트",fontWeight=FontWeight.Bold);lists.forEach{list->FilterChip(selected=owner==list.id,onClick={owner=list.id},label={Text(list.title)})};FilterChip(selected=owner.isBlank(),onClick={owner=""},label={Text("+ 새 체크리스트")});if(owner.isBlank())OutlinedTextField(value=newList,onValueChange={newList=it},label={Text("체크리스트 이름")},modifier=Modifier.fillMaxWidth(),singleLine=true)}
            OutlinedTextField(value=name,onValueChange={name=it;error=null},label={Text("이름")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            Text(if(kind==AgendaKind.PLAN)"시작"else"예정 날짜·시간",fontWeight=FontWeight.Bold)
            ScheduleDateTimeFields(time,{timeText=it.toString();if(end.isBefore(it))endText=it.plusHours(1).toString()})
            if(kind==AgendaKind.PLAN){Text("종료",fontWeight=FontWeight.Bold);ScheduleDateTimeFields(end,{endText=it.toString()})}
            OutlinedTextField(value=note,onValueChange={note=it},label={Text("메모")},modifier=Modifier.fillMaxWidth(),minLines=2)
            if(kind==AgendaKind.ACTION||kind==AgendaKind.CHECKLIST){
                Row(verticalAlignment=Alignment.CenterVertically){Text("알림 받기",Modifier.weight(1f));Switch(checked=mode!=ActionNotificationMode.NONE,onCheckedChange={mode=if(it)ActionNotificationMode.SIMPLE else ActionNotificationMode.NONE})}
                if(mode!=ActionNotificationMode.NONE)Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=mode==ActionNotificationMode.SIMPLE,onClick={mode=ActionNotificationMode.SIMPLE},label={Text("일반 알림")});FilterChip(selected=mode==ActionNotificationMode.MUSIC,onClick={mode=ActionNotificationMode.MUSIC},label={Text("음악 알림")})}
                if(time.isBefore(LocalDateTime.now())&&mode!=ActionNotificationMode.NONE)Text("지난 시간은 저장되지만 알림은 울리지 않습니다.",color=ArmyristColors.SecondaryText)
            }
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }
    }
}
