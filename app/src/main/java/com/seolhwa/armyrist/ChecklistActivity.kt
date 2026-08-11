package com.seolhwa.armyrist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seolhwa.armyrist.stage2.data.CoreSuiteRepository
import com.seolhwa.armyrist.stage2.domain.*

class ChecklistActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); val repo=(application as ArmyristApplication).coreSuiteRepository; setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { ChecklistApp(repo) } } } }
}

@Composable private fun ChecklistApp(repo: CoreSuiteRepository) {
    var selectedId by remember { mutableStateOf<String?>(null) }; var result by remember { mutableStateOf(false) }; var revision by remember { mutableIntStateOf(0) }; @Suppress("UNUSED_VARIABLE") val observed=revision
    fun refresh(){revision++}; val selected=selectedId?.let(repo::getChecklist)
    when { selectedId==null || selected==null -> ChecklistListScreen(repo.getChecklists(), { selectedId=repo.createChecklist().id; refresh() }, {selectedId=it}, {repo.deleteChecklist(it);refresh()})
        result -> { BackHandler{result=false}; ResultScreen(ChecklistResultGenerator.generate(selected), {result=false}) }
        else -> { BackHandler{selectedId=null}; ChecklistDetailScreen(selected, {selectedId=null}, {result=true}, {if(repo.renameChecklist(selected.id,it))refresh()}, {n,note,g->if(repo.addChecklistItem(selected.id,n,note,g))refresh()}, {id,n,note,g->if(repo.editChecklistItem(selected.id,id,n,note,g))refresh()}, {repo.deleteChecklistItem(selected.id,it);refresh()}, {id,s->if(repo.setChecklistStatus(selected.id,id,s))refresh()}, {n,c->if(repo.addChecklistGroup(selected.id,n,c))refresh()}, {id,c->if(repo.setChecklistGroupColor(selected.id,id,c))refresh()}, {repo.deleteChecklistGroup(selected.id,it);refresh()}, {repo.setChecklistMemo(selected.id,it);refresh()}, {repo.resetChecklistStatuses(selected.id);refresh()}, {id,d->repo.moveChecklistItem(selected.id,id,d);refresh()}) }
    }
}

@Composable private fun ChecklistListScreen(checklists:List<Checklist>,onCreate:()->Unit,onOpen:(String)->Unit,onDelete:(String)->Unit){ var del by remember{mutableStateOf<Checklist?>(null)}; Column(Modifier.fillMaxSize().padding(16.dp)){ Text("체크리스트",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold); Spacer(Modifier.height(12.dp)); Button(onClick=onCreate,modifier=Modifier.fillMaxWidth()){Text("+ 새 체크리스트")}; Spacer(Modifier.height(12.dp)); if(checklists.isEmpty()) Text("저장된 체크리스트가 없습니다.") else LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(checklists,key={it.id}){c->Card(Modifier.fillMaxWidth().clickable{onOpen(c.id)}){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(c.title,fontWeight=FontWeight.SemiBold);Text(progressText(ChecklistRules.progress(c.items)),style=MaterialTheme.typography.bodySmall)};TextButton(onClick={del=c}){Text("삭제")}}}}}}; del?.let{t->AlertDialog(onDismissRequest={del=null},title={Text("체크리스트 삭제")},text={Text("'${t.title}'을 삭제하시겠습니까?")},confirmButton={TextButton(onClick={onDelete(t.id);del=null}){Text("삭제")}},dismissButton={TextButton(onClick={del=null}){Text("취소")}})} }
private fun progressText(p:ChecklistProgress)=if(p.effectiveItems==0)"진행 대상 없음 · 해당 없음 ${p.notApplicableItems}" else "완료 ${p.completeItems} / 미완료 ${p.incompleteItems} / 해당 없음 ${p.notApplicableItems} · ${p.completionPercent}%"

@Composable private fun ChecklistDetailScreen(c:Checklist,onBack:()->Unit,onResult:()->Unit,onRename:(String)->Unit,onAdd:(String,String,String?)->Unit,onEdit:(String,String,String,String?)->Unit,onDelete:(String)->Unit,onStatus:(String,ChecklistStatus)->Unit,onAddGroup:(String,String)->Unit,onGroupColor:(String,String)->Unit,onDeleteGroup:(String)->Unit,onMemo:(String)->Unit,onReset:()->Unit,onMove:(String,Int)->Unit){
    var titleEdit by remember{mutableStateOf(false)}; var add by remember{mutableStateOf(false)}; var edit by remember{mutableStateOf<ChecklistItem?>(null)}; var groups by remember{mutableStateOf(false)}; var memo by remember{mutableStateOf(false)}; var reset by remember{mutableStateOf(false)}; val haptic=LocalHapticFeedback.current; val threshold=with(LocalDensity.current){44.dp.toPx()}
    Scaffold(topBar={TopAppBar(title={Column{Row(verticalAlignment=Alignment.CenterVertically){Text(c.title,fontWeight=FontWeight.Bold);TextButton(onClick={titleEdit=true},contentPadding=PaddingValues(horizontal=8.dp)){Text("✎")}};Text("항목 ${c.items.size} · 자동 저장",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}},navigationIcon={TextButton(onClick=onBack){Text("‹ 목록")}},actions={TextButton(onClick=onResult){Text("결과")}})}){pad->
        Column(Modifier.fillMaxSize().padding(pad)){ Text(progressText(ChecklistRules.progress(c.items)),Modifier.padding(horizontal=12.dp,vertical=6.dp)); Row(Modifier.padding(horizontal=8.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)){AssistChip(onClick={groups=true},label={Text("그룹")});AssistChip(onClick={memo=true},label={Text("메모")});AssistChip(onClick={reset=true},label={Text("상태 초기화")})}; HorizontalDivider(); LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(8.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
            itemsIndexed(c.items.sortedBy{it.order},key={_,i->i.id}){index,item-> val g=c.groups.firstOrNull{it.id==item.groupId}; var dy by remember(item.id){mutableFloatStateOf(0f)}; Card(colors=CardDefaults.cardColors(containerColor=g?.let{parseColor(it.color).copy(alpha=.12f)}?:MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.3f)),modifier=Modifier.fillMaxWidth().pointerInput(item.id){detectDragGesturesAfterLongPress(onDragStart={dy=0f;haptic.performHapticFeedback(HapticFeedbackType.LongPress)},onDragCancel={dy=0f},onDragEnd={dy=0f},onDrag={change,amount->change.consume();dy+=amount.y;if(dy>=threshold){onMove(item.id,1);dy=0f}else if(dy<=-threshold){onMove(item.id,-1);dy=0f}})}){Column(Modifier.padding(12.dp)){Row(verticalAlignment=Alignment.CenterVertically){Text("${index+1}.",fontWeight=FontWeight.Bold,modifier=Modifier.width(34.dp));Column(Modifier.weight(1f)){Text(item.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);Text((g?.name?:"미지정")+(if(item.note.isNotBlank())" · ${item.note}" else ""),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};TextButton(onClick={edit=item}){Text("편집")};TextButton(onClick={onDelete(item.id)}){Text("삭제")}};Spacer(Modifier.height(8.dp));StatusSelector(item.status){onStatus(item.id,it)}}}}
            item{Button(onClick={add=true},modifier=Modifier.fillMaxWidth()){Text("+ 새 항목 추가")};Spacer(Modifier.height(8.dp));Text(if(c.memo.isBlank())"메모 없음 · 탭하여 입력" else "메모\n${c.memo}",modifier=Modifier.fillMaxWidth().clickable{memo=true}.padding(12.dp))}
        }}
    }
    if(titleEdit) TextEditDialog("제목 변경",c.title,onDismiss={titleEdit=false}){onRename(it);titleEdit=false}; if(add) ItemEditDialog(null,c.groups,{add=false}){n,no,g->onAdd(n,no,g);add=false}; edit?.let{i->ItemEditDialog(i,c.groups,{edit=null}){n,no,g->onEdit(i.id,n,no,g);edit=null}}; if(memo)TextEditDialog("전체 메모",c.memo,true,{memo=false}){onMemo(it);memo=false}; if(groups)GroupDialog(c,onAddGroup,onGroupColor,onDeleteGroup){groups=false}; if(reset)AlertDialog(onDismissRequest={reset=false},title={Text("상태 초기화")},text={Text("모든 항목을 미완료로 되돌립니다. 항목·그룹·비고·메모는 유지됩니다.")},confirmButton={TextButton(onClick={onReset();reset=false}){Text("초기화")}},dismissButton={TextButton(onClick={reset=false}){Text("취소")}})
}

@Composable private fun StatusSelector(status:ChecklistStatus,onSelect:(ChecklistStatus)->Unit){ Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){StatusButton("미완료",status==ChecklistStatus.INCOMPLETE,Color(0xFFE7E3EA),Color(0xFF514C56),Modifier.weight(1f)){onSelect(ChecklistStatus.INCOMPLETE)};StatusButton("완료",status==ChecklistStatus.COMPLETE,Color(0xFFD9F0DE),Color(0xFF1F6334),Modifier.weight(1f)){onSelect(ChecklistStatus.COMPLETE)};StatusButton("해당 없음",status==ChecklistStatus.NOT_APPLICABLE,Color(0xFFDDE7F0),Color(0xFF35556F),Modifier.weight(1f)){onSelect(ChecklistStatus.NOT_APPLICABLE)}} }
@Composable private fun StatusButton(text:String,selected:Boolean,bg:Color,fg:Color,modifier:Modifier,onClick:()->Unit){ Surface(onClick=onClick,modifier=modifier.height(44.dp),shape=MaterialTheme.shapes.medium,color=if(selected)bg else MaterialTheme.colorScheme.surface,contentColor=if(selected)fg else MaterialTheme.colorScheme.onSurfaceVariant,border=ButtonDefaults.outlinedButtonBorder(enabled=true)){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text(text,fontWeight=if(selected)FontWeight.Bold else FontWeight.Medium)}} }

@Composable private fun TextEditDialog(title:String,initial:String,multiline:Boolean=false,onDismiss:()->Unit,onConfirm:(String)->Unit){var text by remember(initial){mutableStateOf(initial)};AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={OutlinedTextField(text,{text=it},minLines=if(multiline)4 else 1)},confirmButton={TextButton(onClick={onConfirm(text)}){Text("확인")}},dismissButton={TextButton(onClick=onDismiss){Text("취소")}})}
@Composable private fun ItemEditDialog(item:ChecklistItem?,groups:List<ChecklistGroup>,onDismiss:()->Unit,onConfirm:(String,String,String?)->Unit){var name by remember{mutableStateOf(item?.name?:"")};var note by remember{mutableStateOf(item?.note?:"")};var gid by remember{mutableStateOf(item?.groupId)};AlertDialog(onDismissRequest=onDismiss,title={Text(if(item==null)"항목 추가" else "항목 편집")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(name,{name=it},label={Text("항목명")});OutlinedTextField(note,{note=it},label={Text("비고")});Text("그룹");FlowRow(horizontalArrangement=Arrangement.spacedBy(5.dp)){FilterChip(gid==null,{gid=null},{Text("미지정")});groups.sortedBy{it.order}.forEach{g->FilterChip(gid==g.id,{gid=g.id},{Text(g.name)})}}}},confirmButton={TextButton(enabled=name.trim().isNotEmpty(),onClick={onConfirm(name,note,gid)}){Text("확인")}},dismissButton={TextButton(onClick=onDismiss){Text("취소")}})}
private val groupColors=listOf("#6750A4","#2E7D32","#1565C0","#C62828","#EF6C00","#00838F","#6D4C41","#546E7A")
@Composable private fun GroupDialog(c:Checklist,onAdd:(String,String)->Unit,onColor:(String,String)->Unit,onDelete:(String)->Unit,onDismiss:()->Unit){var name by remember{mutableStateOf("")};var color by remember{mutableStateOf(groupColors[0])};AlertDialog(onDismissRequest=onDismiss,title={Text("그룹 관리")},text={LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp)){items(c.groups.sortedBy{it.order},key={it.id}){g->Surface(color=parseColor(g.color).copy(alpha=.12f),shape=MaterialTheme.shapes.medium){Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(18.dp).background(parseColor(g.color),CircleShape));Spacer(Modifier.width(8.dp));Text(g.name,Modifier.weight(1f));groupColors.forEach{cl->Box(Modifier.padding(2.dp).size(22.dp).background(parseColor(cl),CircleShape).clickable{onColor(g.id,cl)})};TextButton(onClick={onDelete(g.id)}){Text("삭제")}}}};item{OutlinedTextField(name,{name=it},label={Text("새 그룹명")});Row{groupColors.forEach{cl->Box(Modifier.padding(4.dp).size(28.dp).background(parseColor(cl),CircleShape).clickable{color=cl})}};Button(enabled=name.trim().isNotEmpty(),onClick={onAdd(name,color);name=""},modifier=Modifier.fillMaxWidth()){Text("+ 그룹 추가")}}}},confirmButton={TextButton(onClick=onDismiss){Text("닫기")}})}

@Composable private fun ResultScreen(result:ToolResult,onBack:()->Unit){val context=LocalContext.current;Scaffold(topBar={TopAppBar(title={Text("결과")},navigationIcon={TextButton(onClick=onBack){Text("‹ 체크리스트")}})}){p->Column(Modifier.fillMaxSize().padding(p).padding(16.dp)){Text(result.title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Spacer(Modifier.height(12.dp));Surface(Modifier.weight(1f).fillMaxWidth(),color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.35f),shape=MaterialTheme.shapes.large){LazyColumn(Modifier.padding(14.dp)){item{Text(result.body)}}};Spacer(Modifier.height(12.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={val cm=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager;cm.setPrimaryClip(ClipData.newPlainText("체크리스트 결과",result.body));Toast.makeText(context,"복사되었습니다.",Toast.LENGTH_SHORT).show()},modifier=Modifier.weight(1f)){Text("복사")};Button(onClick={val i=Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,result.body)};context.startActivity(Intent.createChooser(i,"공유"))},modifier=Modifier.weight(1f)){Text("공유")}}}}}
private fun parseColor(hex:String):Color=runCatching{Color(android.graphics.Color.parseColor(hex))}.getOrDefault(Color(0xFF6750A4))
