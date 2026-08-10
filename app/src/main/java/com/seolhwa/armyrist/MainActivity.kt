package com.seolhwa.armyrist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.seolhwa.armyrist.data.CountingRepository
import com.seolhwa.armyrist.domain.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as ArmyristApplication).repository
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { ArmyristApp(repository) }
            }
        }
    }
}

private enum class Screen { SHEETS, COUNTING, GROUPS, CALCULATIONS, RESULT }

@Composable
private fun ArmyristApp(repo: CountingRepository) {
    var screen by remember { mutableStateOf(Screen.SHEETS) }
    var selectedSheetId by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    fun refresh() { revision++ }

    when (screen) {
        Screen.SHEETS -> SheetListScreen(
            sheets = repo.getSheets(),
            onCreate = {
                selectedSheetId = repo.createSheet().id
                refresh()
                screen = Screen.COUNTING
            },
            onOpen = { selectedSheetId = it; screen = Screen.COUNTING },
            onRename = { id, title -> if (repo.renameSheet(id, title)) refresh() },
            onDelete = { repo.deleteSheet(it); refresh() }
        )
        else -> {
            val id = selectedSheetId
            val sheet = if (id != null) repo.getSheet(id) else null
            if (sheet == null) {
                screen = Screen.SHEETS
            } else when (screen) {
                Screen.COUNTING -> CountingScreen(
                    sheet = sheet,
                    onBack = { screen = Screen.SHEETS },
                    onGroups = { screen = Screen.GROUPS },
                    onCalculations = { screen = Screen.CALCULATIONS },
                    onResult = { screen = Screen.RESULT },
                    onRename = { if (repo.renameSheet(sheet.id, it)) refresh() },
                    onMemo = { repo.setMemo(sheet.id, it); refresh() },
                    onAddItem = { n, q, u, note, g -> if (repo.addItem(sheet.id, n, q, u, note, g)) refresh() },
                    onEditItem = { itemId, n, u, note, g -> if (repo.editItem(sheet.id, itemId, n, u, note, g)) refresh() },
                    onDeleteItem = { repo.deleteItem(sheet.id, it); refresh() },
                    onIncrement = { repo.increment(sheet.id, it); refresh() },
                    onDecrement = { repo.decrement(sheet.id, it); refresh() },
                    onQuantity = { itemId, q -> if (repo.setQuantity(sheet.id, itemId, q)) refresh() },
                    onMove = { itemId, d -> repo.moveItem(sheet.id, itemId, d); refresh() }
                )
                Screen.GROUPS -> GroupScreen(
                    sheet = sheet,
                    onBack = { screen = Screen.COUNTING },
                    onAdd = { if (repo.addGroup(sheet.id, it)) refresh() },
                    onRename = { gid, n -> if (repo.renameGroup(sheet.id, gid, n)) refresh() },
                    onDelete = { repo.deleteGroup(sheet.id, it); refresh() }
                )
                Screen.CALCULATIONS -> CalculationScreen(
                    sheet = sheet,
                    onBack = { screen = Screen.COUNTING },
                    onAdd = { l, op, r, n -> if (repo.addCalculation(sheet.id, l, op, r, n)) refresh() },
                    onEdit = { cid, l, op, r, n -> if (repo.editCalculation(sheet.id, cid, l, op, r, n)) refresh() },
                    onDelete = { repo.deleteCalculation(sheet.id, it); refresh() }
                )
                Screen.RESULT -> ResultScreen(sheet = sheet, onBack = { screen = Screen.COUNTING })
                else -> Unit
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetListScreen(
    sheets: List<CountingSheet>,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var rename by remember { mutableStateOf<CountingSheet?>(null) }
    var delete by remember { mutableStateOf<CountingSheet?>(null) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("실셈") }) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = onCreate, text = { Text("새 실셈") }) }
    ) { padding ->
        if (sheets.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("저장된 실셈표가 없습니다.")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onCreate) { Text("새 실셈 만들기") }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sheets, key = { it.id }) { sheet ->
                    Card(onClick = { onOpen(sheet.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(sheet.title, fontWeight = FontWeight.Bold)
                            Text("항목 ${sheet.items.size}개 · 그룹 ${sheet.groups.size}개")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { rename = sheet }) { Text("이름 변경") }
                                TextButton(onClick = { delete = sheet }) { Text("삭제") }
                            }
                        }
                    }
                }
            }
        }
    }
    rename?.let { target ->
        TextInputDialog("실셈표 이름 변경", target.title, "이름을 입력하세요.") {
            rename = null
            if (it != null) onRename(target.id, it)
        }
    }
    delete?.let { target ->
        ConfirmDialog("실셈표 삭제", "'${target.title}'와 모든 항목/그룹/계산을 삭제합니다.") {
            delete = null
            if (it) onDelete(target.id)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountingScreen(
    sheet: CountingSheet,
    onBack: () -> Unit,
    onGroups: () -> Unit,
    onCalculations: () -> Unit,
    onResult: () -> Unit,
    onRename: (String) -> Unit,
    onMemo: (String) -> Unit,
    onAddItem: (String, Int, String, String, String?) -> Unit,
    onEditItem: (String, String, String, String, String?) -> Unit,
    onDeleteItem: (String) -> Unit,
    onIncrement: (String) -> Unit,
    onDecrement: (String) -> Unit,
    onQuantity: (String, Int) -> Unit,
    onMove: (String, Int) -> Unit
) {
    var itemEditor by remember { mutableStateOf<CountingItem?>(null) }
    var creating by remember { mutableStateOf(false) }
    var quantityTarget by remember { mutableStateOf<CountingItem?>(null) }
    var deleteTarget by remember { mutableStateOf<CountingItem?>(null) }
    var titleEdit by remember { mutableStateOf(false) }
    var memoEdit by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(sheet.title) },
                navigationIcon = { TextButton(onClick = onBack) { Text("목록") } },
                actions = { TextButton(onClick = onResult) { Text("결과") } }
            )
        },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { creating = true }, text = { Text("항목 추가") }) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { titleEdit = true }) { Text("제목") }
                TextButton(onClick = onGroups) { Text("그룹") }
                TextButton(onClick = onCalculations) { Text("계산") }
                TextButton(onClick = { memoEdit = true }) { Text("메모") }
            }
            AggregateSummary(sheet)
            HorizontalDivider()
            if (sheet.items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("항목이 없습니다. 항목을 추가하세요.") }
            } else {
                LazyColumn(contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sheet.items.sortedBy { it.order }, key = { it.id }) { item ->
                        val group = sheet.groups.firstOrNull { it.id == item.groupId }?.name ?: "미지정"
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.name, fontWeight = FontWeight.Bold)
                                        Text("${item.unit} · $group")
                                        if (item.note.isNotBlank()) Text("비고: ${item.note}")
                                    }
                                    FilledTonalButton(onClick = { onDecrement(item.id) }, modifier = Modifier.sizeIn(minWidth = 56.dp, minHeight = 48.dp)) { Text("−") }
                                    TextButton(onClick = { quantityTarget = item }, modifier = Modifier.sizeIn(minWidth = 72.dp, minHeight = 48.dp)) {
                                        Text(item.quantity.toString(), style = MaterialTheme.typography.headlineSmall)
                                    }
                                    FilledTonalButton(onClick = { onIncrement(item.id) }, modifier = Modifier.sizeIn(minWidth = 56.dp, minHeight = 48.dp)) { Text("+") }
                                }
                                Row {
                                    TextButton(onClick = { onMove(item.id, -1) }) { Text("↑") }
                                    TextButton(onClick = { onMove(item.id, 1) }) { Text("↓") }
                                    TextButton(onClick = { itemEditor = item }) { Text("편집") }
                                    TextButton(onClick = { deleteTarget = item }) { Text("삭제") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating) ItemDialog(sheet, null) { data ->
        creating = false
        data?.let { onAddItem(it.name, it.quantity, it.unit, it.note, it.groupId) }
    }
    itemEditor?.let { item ->
        ItemDialog(sheet, item) { data ->
            itemEditor = null
            data?.let { onEditItem(item.id, it.name, it.unit, it.note, it.groupId) }
        }
    }
    quantityTarget?.let { item ->
        QuantityDialog(item.quantity) { q ->
            quantityTarget = null
            if (q != null) onQuantity(item.id, q)
        }
    }
    deleteTarget?.let { item ->
        ConfirmDialog("항목 삭제", "'${item.name}' 항목만 삭제합니다.") {
            deleteTarget = null
            if (it) onDeleteItem(item.id)
        }
    }
    if (titleEdit) TextInputDialog("제목 변경", sheet.title, "빈 제목은 저장할 수 없습니다.") {
        titleEdit = false
        if (it != null && it.trim().isNotEmpty()) onRename(it)
    }
    if (memoEdit) TextInputDialog("실셈표 메모", sheet.memo, "", allowEmpty = true, singleLine = false) {
        memoEdit = false
        if (it != null) onMemo(it)
    }
}

@Composable
private fun AggregateSummary(sheet: CountingSheet) {
    val sections = mutableListOf<Pair<String, Map<String, Int>>>()
    sheet.groups.sortedBy { it.order }.forEach { g ->
        sections += g.name to DomainRules.aggregate(sheet.items.filter { it.groupId == g.id })
    }
    val ungrouped = sheet.items.filter { it.groupId == null }
    if (ungrouped.isNotEmpty()) sections += "미지정" to DomainRules.aggregate(ungrouped)

    if (sections.isNotEmpty()) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 150.dp).padding(horizontal = 12.dp)) {
            items(sections) { (name, totals) ->
                Text("$name  " + totals.entries.joinToString(" / ") { "${it.key} ${it.value}" })
            }
        }
    }
}

private data class ItemDraft(val name: String, val quantity: Int, val unit: String, val note: String, val groupId: String?)

@Composable
private fun ItemDialog(sheet: CountingSheet, item: CountingItem?, done: (ItemDraft?) -> Unit) {
    var name by remember { mutableStateOf(item?.name ?: "") }
    var quantityRaw by remember { mutableStateOf(item?.quantity?.toString() ?: "0") }
    var unit by remember { mutableStateOf(item?.unit ?: "개") }
    var note by remember { mutableStateOf(item?.note ?: "") }
    var groupId by remember { mutableStateOf(item?.groupId) }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { done(null) },
        title = { Text(if (item == null) "항목 추가" else "항목 편집") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("항목명") }, singleLine = true)
                if (item == null) OutlinedTextField(
                    quantityRaw, { quantityRaw = it }, label = { Text("수량") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(unit, { unit = it }, label = { Text("단위 (예: 개, 봉, 병, 박스)") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("비고") })
                Text("그룹")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected = groupId == null, onClick = { groupId = null }, label = { Text("미지정") })
                }
                sheet.groups.sortedBy { it.order }.forEach { g ->
                    FilterChip(selected = groupId == g.id, onClick = { groupId = g.id }, label = { Text(g.name) })
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val q = if (item == null) DomainRules.parseQuantity(quantityRaw) else item.quantity
                when {
                    name.trim().isEmpty() -> error = "항목명을 입력하세요."
                    unit.trim().isEmpty() -> error = "단위를 입력하세요."
                    q == null -> error = "수량은 0 이상의 정수만 가능합니다."
                    else -> done(ItemDraft(name.trim(), q, unit.trim(), note.trim(), groupId))
                }
            }) { Text("확인") }
        },
        dismissButton = { TextButton(onClick = { done(null) }) { Text("취소") } }
    )
}

@Composable
private fun QuantityDialog(current: Int, done: (Int?) -> Unit) {
    var raw by remember { mutableStateOf(current.toString()) }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { done(null) },
        title = { Text("수량 직접 입력") },
        text = {
            Column {
                OutlinedTextField(raw, { raw = it }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = DomainRules.parseQuantity(raw)
                if (parsed == null) error = "0 이상의 정수를 입력하세요." else done(parsed)
            }) { Text("확인") }
        },
        dismissButton = { TextButton(onClick = { done(null) }) { Text("취소") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupScreen(sheet: CountingSheet, onBack: () -> Unit, onAdd: (String) -> Unit, onRename: (String, String) -> Unit, onDelete: (String) -> Unit) {
    var create by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<CountingGroup?>(null) }
    var delete by remember { mutableStateOf<CountingGroup?>(null) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("그룹 관리") }, navigationIcon = { TextButton(onClick = onBack) { Text("뒤로") } }) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { create = true }, text = { Text("그룹 추가") }) }
    ) { padding ->
        if (sheet.groups.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("그룹이 없습니다.") }
        } else {
            LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sheet.groups.sortedBy { it.order }) { g ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(g.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            TextButton(onClick = { rename = g }) { Text("이름 변경") }
                            TextButton(onClick = { delete = g }) { Text("삭제") }
                        }
                    }
                }
            }
        }
    }
    if (create) TextInputDialog("그룹 추가", "", "그룹명을 입력하세요.") {
        create = false
        if (it != null && it.trim().isNotEmpty()) onAdd(it)
    }
    rename?.let { g -> TextInputDialog("그룹 이름 변경", g.name, "그룹명을 입력하세요.") {
        rename = null
        if (it != null && it.trim().isNotEmpty()) onRename(g.id, it)
    } }
    delete?.let { g ->
        val itemCount = sheet.items.count { it.groupId == g.id }
        val calcCount = sheet.calculations.count { it.leftGroupId == g.id || it.rightGroupId == g.id }
        ConfirmDialog("그룹 삭제", "항목 $itemCount개는 삭제되지 않고 '미지정'으로 변경됩니다. 관련 계산 $calcCount개는 삭제됩니다.") {
            delete = null
            if (it) onDelete(g.id)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalculationScreen(
    sheet: CountingSheet,
    onBack: () -> Unit,
    onAdd: (String, CalculationOperator, String, String) -> Unit,
    onEdit: (String, String, CalculationOperator, String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var editor by remember { mutableStateOf<GroupCalculation?>(null) }
    var creating by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf<GroupCalculation?>(null) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("그룹 계산") }, navigationIcon = { TextButton(onClick = onBack) { Text("뒤로") } }) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { creating = true }, text = { Text("계산 추가") }) }
    ) { padding ->
        if (sheet.groups.size < 1) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("먼저 그룹을 만들어 주세요.") }
        } else if (sheet.calculations.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("등록된 계산이 없습니다.") }
        } else {
            LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sheet.calculations) { c ->
                    val lg = sheet.groups.firstOrNull { it.id == c.leftGroupId }?.name ?: "?"
                    val rg = sheet.groups.firstOrNull { it.id == c.rightGroupId }?.name ?: "?"
                    val op = if (c.operator == CalculationOperator.ADD) "+" else "-"
                    val leftTotals = DomainRules.aggregate(sheet.items.filter { it.groupId == c.leftGroupId })
                    val rightTotals = DomainRules.aggregate(sheet.items.filter { it.groupId == c.rightGroupId })
                    val result = DomainRules.calculate(leftTotals, c.operator, rightTotals)
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(c.name.ifBlank { "$lg $op $rg" }, fontWeight = FontWeight.Bold)
                            Text("$lg $op $rg")
                            result.forEach { (unit, q) -> Text("$unit : $q") }
                            Row {
                                TextButton(onClick = { editor = c }) { Text("편집") }
                                TextButton(onClick = { delete = c }) { Text("삭제") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (creating) CalculationDialog(sheet, null) { d ->
        creating = false
        d?.let { onAdd(it.left, it.op, it.right, it.name) }
    }
    editor?.let { c -> CalculationDialog(sheet, c) { d ->
        editor = null
        d?.let { onEdit(c.id, it.left, it.op, it.right, it.name) }
    } }
    delete?.let { c -> ConfirmDialog("계산 삭제", "이 계산을 삭제합니다.") {
        delete = null
        if (it) onDelete(c.id)
    } }
}

private data class CalcDraft(val left: String, val op: CalculationOperator, val right: String, val name: String)

@Composable
private fun CalculationDialog(sheet: CountingSheet, current: GroupCalculation?, done: (CalcDraft?) -> Unit) {
    if (sheet.groups.isEmpty()) { done(null); return }
    var left by remember { mutableStateOf(current?.leftGroupId ?: sheet.groups.first().id) }
    var right by remember { mutableStateOf(current?.rightGroupId ?: sheet.groups.first().id) }
    var op by remember { mutableStateOf(current?.operator ?: CalculationOperator.ADD) }
    var name by remember { mutableStateOf(current?.name ?: "") }
    AlertDialog(
        onDismissRequest = { done(null) },
        title = { Text(if (current == null) "계산 추가" else "계산 편집") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("왼쪽 그룹")
                sheet.groups.forEach { g -> FilterChip(selected = left == g.id, onClick = { left = g.id }, label = { Text(g.name) }) }
                Text("연산")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = op == CalculationOperator.ADD, onClick = { op = CalculationOperator.ADD }, label = { Text("+") })
                    FilterChip(selected = op == CalculationOperator.SUBTRACT, onClick = { op = CalculationOperator.SUBTRACT }, label = { Text("−") })
                }
                Text("오른쪽 그룹")
                sheet.groups.forEach { g -> FilterChip(selected = right == g.id, onClick = { right = g.id }, label = { Text(g.name) }) }
                OutlinedTextField(name, { name = it }, label = { Text("계산명 (선택)") })
            }
        },
        confirmButton = { TextButton(onClick = { done(CalcDraft(left, op, right, name.trim())) }) { Text("확인") } },
        dismissButton = { TextButton(onClick = { done(null) }) { Text("취소") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultScreen(sheet: CountingSheet, onBack: () -> Unit) {
    val context = LocalContext.current
    val result = remember(sheet) { ResultGenerator.generate(sheet) }
    Scaffold(topBar = { TopAppBar(title = { Text("결과 미리보기") }, navigationIcon = { TextButton(onClick = onBack) { Text("뒤로") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            Surface(tonalElevation = 2.dp, modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(Modifier.padding(12.dp)) { item { Text(result) } }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("실셈 결과", result))
                    Toast.makeText(context, "복사되었습니다.", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.weight(1f)) { Text("복사") }
                Button(onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, result)
                    }
                    context.startActivity(Intent.createChooser(intent, "실셈 결과 공유"))
                }, modifier = Modifier.weight(1f)) { Text("공유") }
            }
        }
    }
}

@Composable
private fun TextInputDialog(title: String, initial: String, helper: String, allowEmpty: Boolean = false, singleLine: Boolean = true, done: (String?) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { done(null) },
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value, { value = it }, singleLine = singleLine, modifier = Modifier.fillMaxWidth())
                if (helper.isNotBlank()) Text(helper, style = MaterialTheme.typography.bodySmall)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = {
            if (!allowEmpty && value.trim().isEmpty()) error = "빈 값은 저장할 수 없습니다." else done(value)
        }) { Text("확인") } },
        dismissButton = { TextButton(onClick = { done(null) }) { Text("취소") } }
    )
}

@Composable
private fun ConfirmDialog(title: String, message: String, done: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { done(false) },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = { done(true) }) { Text("확인") } },
        dismissButton = { TextButton(onClick = { done(false) }) { Text("취소") } }
    )
}
