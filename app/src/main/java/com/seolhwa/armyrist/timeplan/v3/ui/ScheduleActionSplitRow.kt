package com.seolhwa.armyrist.timeplan.v3.ui
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seolhwa.armyrist.*
import com.seolhwa.armyrist.calendar.ScheduleDateTimeFields
import com.seolhwa.armyrist.timeplan.v3.domain.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal val LocalTimelineCompact = staticCompositionLocalOf { false }

@Composable
internal fun ScheduleActionWorkspace(plan: DateAwareTimePlan,selectionMode: Boolean,onSave: (TimePlanActionItem)->Boolean,content: @Composable ((String)->Unit)->Unit) {
    var nodeId by rememberSaveable(plan.id){mutableStateOf<String?>(null)}
    val node=nodeId?.takeIf { (it=="ALL" || it in DateTimePlanRules.nodeIds(plan)) && !selectionMode }
    val threshold=with(LocalDensity.current){52.dp.toPx()}
    LaunchedEffect(selectionMode){if(selectionMode)nodeId=null}
    val fraction by animateFloatAsState(if(node==null)1f else .50f,tween(260),label="일정 이동")
    var paneEditing by remember { mutableStateOf(false) }
    LaunchedEffect(node){paneEditing=false}
    BackHandler(node!=null){nodeId=null}
    Row(Modifier.fillMaxSize().clipToBounds().pointerInput(selectionMode,node,paneEditing) {
        if(!selectionMode && !paneEditing) { var distance=0f;detectHorizontalDragGestures(onDragStart={distance=0f},onDragEnd={if(distance < -threshold)nodeId="ALL";if(distance > threshold)nodeId=null},onHorizontalDrag={change,dx->distance+=dx;change.consume()}) }
    }) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight()){CompositionLocalProvider(LocalTimelineCompact provides (node!=null)){content { nodeId=if(nodeId!=null)null else "ALL" }}}
        if(node!=null && fraction<.99f) ActionPane(plan,node,onSave,{nodeId=null},{paneEditing=it},Modifier.weight(1f).fillMaxHeight())
    }
}
@Composable
internal fun ScheduleActionTab(plan: DateAwareTimePlan,nodeId: String,selectionMode: Boolean,onOpen: ()->Unit,content: @Composable ()->Unit) {
    val compact=LocalTimelineCompact.current
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(ArmyristPanelShape).background(ArmyristColors.SecondaryControl)) {
        Box(Modifier.weight(1f)){content()}
        if(!selectionMode) Column(
            Modifier.width(if(compact)44.dp else 64.dp).fillMaxHeight().heightIn(min=48.dp)
                .semantics { contentDescription="${TimePlanExecutionRules.pointName(plan,nodeId)} 실시사항 열기" }
                .clickable(onClick=onOpen).padding(horizontal=3.dp,vertical=6.dp),
            horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center
        ) {
            if(compact)Icon(Icons.Outlined.FactCheck,null,Modifier.size(18.dp),tint=ArmyristColors.PrimaryControl)
            else Text("실시사항",color=ArmyristColors.PrimaryControl,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.labelSmall,maxLines=1)
            Text("${plan.actions.size}개  ›",color=ArmyristColors.SecondaryText,style=MaterialTheme.typography.labelSmall)
        }
    }
}
@Composable
private fun ActionPane(plan: DateAwareTimePlan,nodeId: String,onSave: (TimePlanActionItem)->Boolean,onClose: ()->Unit,onEditing:(Boolean)->Unit,modifier: Modifier) {
    var editing by remember(nodeId){mutableStateOf(false)};var actionId by remember(nodeId){mutableStateOf<String?>(null)}
    LaunchedEffect(editing){onEditing(editing)}
    Surface(modifier.semantics{contentDescription="실시사항 요약 패널"}.imePadding(),shape=ArmyristPanelShape,color=ArmyristColors.SecondaryControl) {
        Column(Modifier.padding(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton(onClick=onClose,modifier=Modifier.fillMaxWidth()){Text("닫기 ›")}
            if(!editing)Text("오른쪽으로 밀어 닫기 →",style=MaterialTheme.typography.labelSmall,color=ArmyristColors.PrimaryControl)
            Text("실시사항",fontWeight=FontWeight.Bold);Text("시각순 · 일정과 독립",style=MaterialTheme.typography.labelSmall)
            HorizontalDivider(color=ArmyristColors.SoftBorder)
            if(editing) InlineActionEditor(plan,nodeId,actionId,onSave,{editing=false},Modifier.weight(1f))
            else {
                val actions=plan.actions.sortedBy{it.scheduledDateTime}
                LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(actions.isEmpty())item { Text("등록된 항목이 없어요",style=MaterialTheme.typography.bodySmall) }
                    items(actions,key={it.id}) { a -> Surface(Modifier.fillMaxWidth().clickable { actionId=a.id;editing=true },shape=ArmyristControlShape,color=ArmyristColors.RaisedSurface) {
                        Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text((if(a.completionState==ActionCompletionState.COMPLETE)"✓ " else "○ ")+a.scheduledDateTime.format(DateTimeFormatter.ofPattern("HH:mm")),style=MaterialTheme.typography.labelMedium,color=ArmyristColors.PrimaryControl)
                            Text(a.content,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodyMedium)
                        }
                    } }
                }
                Button(onClick={actionId=null;editing=true},modifier=Modifier.fillMaxWidth(),contentPadding=PaddingValues(4.dp)){Text("+ 추가")}
            }
        }
    }
}
@Composable
private fun InlineActionEditor(plan: DateAwareTimePlan,nodeId: String,actionId: String?,onSave: (TimePlanActionItem)->Boolean,onCancel: ()->Unit,modifier: Modifier) {
    val initial=remember(actionId,nodeId){plan.actions.firstOrNull { it.id==actionId }}
    var parent by remember(actionId,nodeId){mutableStateOf(initial?.parentPointId ?: nodeId.takeUnless{it=="ALL"} ?: "START")}
    var name by remember(actionId,nodeId){mutableStateOf(initial?.content.orEmpty())}
    var note by remember(actionId,nodeId){mutableStateOf(initial?.note.orEmpty())}
    var date by remember(actionId,nodeId){mutableStateOf(initial?.scheduledDateTime ?: DateTimePlanRules.nodeArrival(plan,nodeId) ?: LocalDateTime.now())}
    var mode by remember(actionId,nodeId){mutableStateOf(initial?.notificationMode ?: ActionNotificationMode.NONE)}
    var error by remember{mutableStateOf(false)}
    Column(modifier,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name,{name=it},label={Text("내용")},modifier=Modifier.fillMaxWidth())
            ScheduleDateTimeFields(date,{date=it},compact=true)
            OutlinedTextField(note,{note=it},label={Text("비고")},modifier=Modifier.fillMaxWidth())
            Text("알림",style=MaterialTheme.typography.labelMedium)
            ActionNotificationMode.entries.forEach { candidate ->
                FilterChip(selected=mode==candidate,onClick={mode=candidate},label={Text(when(candidate){ActionNotificationMode.NONE->"없음";ActionNotificationMode.SIMPLE->"간단한 알림";ActionNotificationMode.MUSIC->"음악 알림"})},modifier=Modifier.fillMaxWidth())
            }
            if(error)Text("저장하지 못했습니다.",color=ArmyristColors.Danger)
        }
        Button(enabled=name.isNotBlank(),onClick={
            val candidate=initial?.copy(content=name.trim(),note=note.trim().ifBlank{null},scheduledDateTime=date,notificationMode=mode,notificationEnabled=mode!=ActionNotificationMode.NONE)
                ?: TimePlanActionItem(content=name.trim(),note=note.trim().ifBlank{null},scheduledDateTime=date,notificationMode=mode,notificationEnabled=mode!=ActionNotificationMode.NONE)
            if(onSave(candidate))onCancel() else error=true
        },modifier=Modifier.fillMaxWidth()){Text("저장")}
        TextButton(onClick=onCancel,modifier=Modifier.fillMaxWidth()){Text("취소")}
    }
}
