package com.seolhwa.armyrist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seolhwa.armyrist.stage2.data.CoreSuiteRepository
import com.seolhwa.armyrist.stage2.domain.*

class ChecklistActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = (application as ArmyristApplication).coreSuiteRepository
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { ChecklistApp(repo) }
            }
        }
    }
}

@Composable
private fun ChecklistApp(repo: CoreSuiteRepository) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    val refresh = { revision++ }
    @Suppress("UNUSED_VARIABLE") val observed = revision

    val selected = selectedId?.let(repo::getChecklist)
    if (selectedId == null || selected == null) {
        ChecklistListScreen(
            checklists = repo.getChecklists(),
            onCreate = {
                selectedId = repo.createChecklist().id
                refresh()
            },
            onOpen = { selectedId = it },
            onDelete = { repo.deleteChecklist(it); refresh() }
        )
    } else {
        BackHandler { selectedId = null }
        ChecklistDetailScreen(
            checklist = selected,
            onBack = { selectedId = null },
            onRename = { if (repo.renameChecklist(selected.id, it)) refresh() },
            onAddItem = { name, note, groupId ->
                if (repo.addChecklistItem(selected.id, name, note, groupId)) refresh()
            },
            onEditItem = { itemId, name, note, groupId ->
                if (repo.editChecklistItem(selected.id, itemId, name, note, groupId)) refresh()
            },
            onDeleteItem = { repo.deleteChecklistItem(selected.id, it); refresh() },
            onStatus = { itemId, status ->
                if (repo.setChecklistStatus(selected.id, itemId, status)) refresh()
            },
            onAddGroup = { if (repo.addChecklistGroup(selected.id, it)) refresh() },
            onDeleteGroup = { repo.deleteChecklistGroup(selected.id, it); refresh() },
            onMemo = { repo.setChecklistMemo(selected.id, it); refresh() },
            onReset = { repo.resetChecklistStatuses(selected.id); refresh() },
            onMove = { itemId, delta -> repo.moveChecklistItem(selected.id, itemId, delta); refresh() }
        )
    }
}

@Composable
private fun ChecklistListScreen(
    checklists: List<Checklist>,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    var deleteTarget by remember { mutableStateOf<Checklist?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("체크리스트", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("+ 새 체크리스트") }
        Spacer(Modifier.height(12.dp))
        if (checklists.isEmpty()) {
            Text("저장된 체크리스트가 없습니다.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(checklists, key = { it.id }) { checklist ->
                    Card(Modifier.fillMaxWidth().clickable { onOpen(checklist.id) }) {
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(checklist.title, fontWeight = FontWeight.SemiBold)
                                val p = ChecklistRules.progress(checklist.items)
                                Text(progressText(p), style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { deleteTarget = checklist }) { Text("삭제") }
                        }
                    }
                }
            }
        }
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("체크리스트 삭제") },
            text = { Text("'${target.title}'을 삭제하시겠습니까?") },
            confirmButton = { TextButton(onClick = { onDelete(target.id); deleteTarget = null }) { Text("삭제") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("취소") } }
        )
    }
}

private fun progressText(p: ChecklistProgress): String =
    if (p.effectiveItems == 0) "진행 대상 없음 · 해당 없음 ${p.notApplicableItems}"
    else "완료 ${p.completeItems} / 미완료 ${p.incompleteItems} / 해당 없음 ${p.notApplicableItems} · ${p.completionPercent}%"

@Composable
private fun ChecklistDetailScreen(
    checklist: Checklist,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onAddItem: (String, String, String?) -> Unit,
    onEditItem: (String, String, String, String?) -> Unit,
    onDeleteItem: (String) -> Unit,
    onStatus: (String, ChecklistStatus) -> Unit,
    onAddGroup: (String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onMemo: (String) -> Unit,
    onReset: () -> Unit,
    onMove: (String, Int) -> Unit
) {
    var titleDialog by remember { mutableStateOf(false) }
    var itemDialog by remember { mutableStateOf<ChecklistItem?>(null) }
    var addingItem by remember { mutableStateOf(false) }
    var groupDialog by remember { mutableStateOf(false) }
    var memoDialog by remember { mutableStateOf(false) }
    var resetDialog by remember { mutableStateOf(false) }
    val progress = ChecklistRules.progress(checklist.items)

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text("← 목록") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { titleDialog = true }) { Text("제목 변경") }
        }
        Text(checklist.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(progressText(progress))
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { groupDialog = true }) { Text("그룹") }
            OutlinedButton(onClick = { memoDialog = true }) { Text("메모") }
            OutlinedButton(onClick = { resetDialog = true }) { Text("상태 초기화") }
        }
        Spacer(Modifier.height(10.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(checklist.items.sortedBy { it.order }, key = { it.id }) { item ->
                val groupName = checklist.groups.firstOrNull { it.id == item.groupId }?.name ?: "미지정"
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(item.name, fontWeight = FontWeight.SemiBold)
                        Text("$groupName${if (item.note.isNotBlank()) " · ${item.note}" else ""}",
                            style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = item.status == ChecklistStatus.INCOMPLETE,
                                onClick = { onStatus(item.id, ChecklistStatus.INCOMPLETE) },
                                label = { Text("미완료") })
                            FilterChip(
                                selected = item.status == ChecklistStatus.COMPLETE,
                                onClick = { onStatus(item.id, ChecklistStatus.COMPLETE) },
                                label = { Text("완료") })
                            FilterChip(
                                selected = item.status == ChecklistStatus.NOT_APPLICABLE,
                                onClick = { onStatus(item.id, ChecklistStatus.NOT_APPLICABLE) },
                                label = { Text("해당 없음") })
                        }
                        Row {
                            TextButton(onClick = { onMove(item.id, -1) }) { Text("↑") }
                            TextButton(onClick = { onMove(item.id, 1) }) { Text("↓") }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { itemDialog = item }) { Text("편집") }
                            TextButton(onClick = { onDeleteItem(item.id) }) { Text("삭제") }
                        }
                    }
                }
            }
            item {
                Button(onClick = { addingItem = true }, modifier = Modifier.fillMaxWidth()) { Text("+ 새 항목 추가") }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (checklist.memo.isBlank()) "메모 없음 · 탭하여 입력" else "메모\n${checklist.memo}",
                    modifier = Modifier.fillMaxWidth().clickable { memoDialog = true }.padding(12.dp)
                )
            }
        }
    }

    if (titleDialog) TextEditDialog("제목 변경", checklist.title, onDismiss = { titleDialog = false }) {
        onRename(it); titleDialog = false
    }
    if (addingItem) ItemEditDialog(null, checklist.groups, { addingItem = false }) { n, note, group ->
        onAddItem(n, note, group); addingItem = false
    }
    itemDialog?.let { item ->
        ItemEditDialog(item, checklist.groups, { itemDialog = null }) { n, note, group ->
            onEditItem(item.id, n, note, group); itemDialog = null
        }
    }
    if (memoDialog) TextEditDialog("전체 메모", checklist.memo, true, { memoDialog = false }) {
        onMemo(it); memoDialog = false
    }
    if (groupDialog) GroupDialog(checklist, onAddGroup, onDeleteGroup) { groupDialog = false }
    if (resetDialog) AlertDialog(
        onDismissRequest = { resetDialog = false },
        title = { Text("상태 초기화") },
        text = { Text("모든 항목을 미완료 상태로 되돌립니다. 항목·그룹·비고·메모는 유지됩니다.") },
        confirmButton = { TextButton(onClick = { onReset(); resetDialog = false }) { Text("초기화") } },
        dismissButton = { TextButton(onClick = { resetDialog = false }) { Text("취소") } }
    )
}

@Composable
private fun TextEditDialog(
    title: String,
    initial: String,
    multiline: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, minLines = if (multiline) 4 else 1) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("확인") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
private fun ItemEditDialog(
    item: ChecklistItem?,
    groups: List<ChecklistGroup>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String?) -> Unit
) {
    var name by remember { mutableStateOf(item?.name ?: "") }
    var note by remember { mutableStateOf(item?.note ?: "") }
    var groupId by remember { mutableStateOf(item?.groupId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "항목 추가" else "항목 편집") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("항목명") })
                OutlinedTextField(note, { note = it }, label = { Text("비고") })
                Text("그룹")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected = groupId == null, onClick = { groupId = null }, label = { Text("미지정") })
                    groups.sortedBy { it.order }.take(3).forEach { g ->
                        FilterChip(selected = groupId == g.id, onClick = { groupId = g.id }, label = { Text(g.name) })
                    }
                }
                if (groups.size > 3) Text("그룹이 많은 경우 그룹 관리에서 정리해 주세요.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = name.trim().isNotEmpty(), onClick = { onConfirm(name, note, groupId) }) { Text("확인") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
private fun GroupDialog(
    checklist: Checklist,
    onAdd: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("그룹 관리") },
        text = {
            Column {
                checklist.groups.sortedBy { it.order }.forEach { g ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(g.name, Modifier.weight(1f).padding(vertical = 12.dp))
                        TextButton(onClick = { onDelete(g.id) }) { Text("삭제") }
                    }
                }
                OutlinedTextField(newName, { newName = it }, label = { Text("새 그룹명") })
                TextButton(enabled = newName.trim().isNotEmpty(), onClick = { onAdd(newName); newName = "" }) { Text("+ 그룹 추가") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )
}
