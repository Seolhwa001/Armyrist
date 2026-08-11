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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    val observedRevision = revision
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
                    onMove = { itemId, d -> repo.moveItem(sheet.id, itemId, d); refresh() },
                    onAssignGroup = { ids, gid -> if (repo.assignItemsToGroup(sheet.id, ids, gid)) refresh() }
                )
                Screen.GROUPS -> GroupScreen(
                    sheet = sheet,
                    onBack = { screen = Screen.COUNTING },
                    onAdd = { n, c -> if (repo.addGroup(sheet.id, n, c)) refresh() },
                    onRename = { gid, n, c -> if (repo.renameGroup(sheet.id, gid, n, c)) refresh() },
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
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("실셈", fontWeight = FontWeight.Bold)
                        Text(
                            "현장 수량 기록",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (sheets.isNotEmpty()) {
                ExtendedFloatingActionButton(onClick = onCreate) {
                    Text("+ 새 실셈")
                }
            }
        }
    ) { padding ->
        if (sheets.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("아직 실셈표가 없습니다", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "새 실셈표를 만들어 바로 수량을 기록하세요.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = onCreate,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) {
                            Text("새 실셈 만들기")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sheets, key = { it.id }) { sheet ->
                    Card(
                        onClick = { onOpen(sheet.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    sheet.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    "항목 ${sheet.items.size} · 그룹 ${sheet.groups.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { rename = sheet }) { Text("이름") }
                            TextButton(onClick = { delete = sheet }) { Text("삭제") }
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
    onMove: (String, Int) -> Unit,
    onAssignGroup: (Set<String>, String?) -> Unit
) {
    var itemEditor by remember { mutableStateOf<CountingItem?>(null) }
    var creating by remember { mutableStateOf(false) }
    var quantityTarget by remember { mutableStateOf<CountingItem?>(null) }
    var deleteTarget by remember { mutableStateOf<CountingItem?>(null) }
    var titleEdit by remember { mutableStateOf(false) }
    var memoEdit by remember { mutableStateOf(false) }
    var menuTarget by remember { mutableStateOf<CountingItem?>(null) }
    var groupAssign by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(sheet.title, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { titleEdit = true }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("✎") }
                        }
                        Text(
                            "항목 ${sheet.items.size} · 자동 저장",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹ 목록") } },
                actions = { TextButton(onClick = onResult) { Text("결과") } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }) {
                Text("+ 항목")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AggregateSummary(sheet)

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AssistChip(onClick = onGroups, label = { Text("그룹") })
                AssistChip(onClick = { groupAssign = true }, label = { Text("그룹 지정") })
                AssistChip(onClick = onCalculations, label = { Text("계산") })
                AssistChip(onClick = { memoEdit = true }, label = { Text("메모") })
            }

            HorizontalDivider()

            if (sheet.items.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("항목이 없습니다", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "아래 + 항목 버튼으로 첫 항목을 추가하세요.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(sheet.items.sortedBy { it.order }, key = { _, item -> item.id }) { index, item ->
                        val group = sheet.groups.firstOrNull { it.id == item.groupId }?.name ?: "미지정"

                        Card(
                            onClick = { itemEditor = item },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val groupColor = sheet.groups.firstOrNull { it.id == item.groupId }?.color
                                if (groupColor != null) {
                                    Box(Modifier.width(5.dp).height(46.dp).background(parseColor(groupColor), CircleShape))
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text("${index + 1}.", fontWeight = FontWeight.Bold, modifier = Modifier.width(34.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "${item.unit} · $group" + if (item.note.isNotBlank()) " · 비고 있음" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                FilledTonalButton(
                                    onClick = { onDecrement(item.id) },
                                    modifier = Modifier.sizeIn(minWidth = 52.dp, minHeight = 52.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("−", style = MaterialTheme.typography.titleLarge)
                                }

                                TextButton(
                                    onClick = { quantityTarget = item },
                                    modifier = Modifier.widthIn(min = 68.dp).heightIn(min = 52.dp)
                                ) {
                                    Text(
                                        item.quantity.toString(),
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                FilledTonalButton(
                                    onClick = { onIncrement(item.id) },
                                    modifier = Modifier.sizeIn(minWidth = 52.dp, minHeight = 52.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("+", style = MaterialTheme.typography.titleLarge)
                                }

                                Box {
                                    TextButton(
                                        onClick = { menuTarget = item },
                                        modifier = Modifier.widthIn(min = 44.dp)
                                    ) { Text("⋮") }

                                    DropdownMenu(
                                        expanded = menuTarget?.id == item.id,
                                        onDismissRequest = { menuTarget = null }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("편집") },
                                            onClick = {
                                                menuTarget = null
                                                itemEditor = item
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("위로 이동") },
                                            onClick = {
                                                menuTarget = null
                                                onMove(item.id, -1)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("아래로 이동") },
                                            onClick = {
                                                menuTarget = null
                                                onMove(item.id, 1)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("삭제") },
                                            onClick = {
                                                menuTarget = null
                                                deleteTarget = item
                                            }
                                        )
                                    }
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

    if (groupAssign) {
        GroupAssignmentDialog(sheet) { ids, gid ->
            groupAssign = false
            if (ids != null) onAssignGroup(ids, gid)
        }
    }

    if (titleEdit) {
        TextInputDialog("제목 변경", sheet.title, "빈 제목은 저장할 수 없습니다.") {
            titleEdit = false
            if (it != null && it.trim().isNotEmpty()) onRename(it)
        }
    }

    if (memoEdit) {
        TextInputDialog(
            "실셈표 메모",
            sheet.memo,
            "",
            allowEmpty = true,
            singleLine = false
        ) {
            memoEdit = false
            if (it != null) onMemo(it)
        }
    }
}

@Composable
private fun AggregateSummary(sheet: CountingSheet) {
    val sections = mutableListOf<Pair<String, Map<String, Int>>>()
    sheet.groups.sortedBy { it.order }.forEach { g ->
        val totals = DomainRules.aggregate(sheet.items.filter { it.groupId == g.id })
        if (totals.isNotEmpty()) sections += g.name to totals
    }
    val ungrouped = sheet.items.filter { it.groupId == null }
    if (ungrouped.isNotEmpty()) {
        sections += "미지정" to DomainRules.aggregate(ungrouped)
    }

    if (sections.isNotEmpty()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                "현재 합계",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 112.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(sections) { (name, totals) ->
                    Surface(
                        tonalElevation = 1.dp,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                name,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.widthIn(min = 76.dp)
                            )
                            Text(
                                totals.entries.joinToString("   ") { "${it.key} ${it.value}" },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class ItemDraft(
    val name: String,
    val quantity: Int,
    val unit: String,
    val note: String,
    val groupId: String?
)

@Composable
private fun ItemDialog(
    sheet: CountingSheet,
    item: CountingItem?,
    done: (ItemDraft?) -> Unit
) {
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
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("항목명") },
                    singleLine = true
                )

                if (item == null) {
                    OutlinedTextField(
                        value = quantityRaw,
                        onValueChange = { quantityRaw = it },
                        label = { Text("수량") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("단위 (예: 개, 봉, 병, 박스)") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("비고") }
                )

                Text("그룹")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = groupId == null,
                        onClick = { groupId = null },
                        label = { Text("미지정") }
                    )
                }

                sheet.groups.sortedBy { it.order }.forEach { g ->
                    FilterChip(
                        selected = groupId == g.id,
                        onClick = { groupId = g.id },
                        label = { Text(g.name) }
                    )
                }

                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val q = if (item == null) DomainRules.parseQuantity(quantityRaw) else item.quantity
                when {
                    name.trim().isEmpty() -> error = "항목명을 입력하세요."
                    unit.trim().isEmpty() -> error = "단위를 입력하세요."
                    q == null -> error = "수량은 0 이상의 정수만 가능합니다."
                    else -> done(
                        ItemDraft(
                            name.trim(),
                            q,
                            unit.trim(),
                            note.trim(),
                            groupId
                        )
                    )
                }
            }) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = { done(null) }) {
                Text("취소")
            }
        }
    )
}

@Composable
private fun QuantityDialog(
    current: Int,
    done: (Int?) -> Unit
) {
    var raw by remember { mutableStateOf(current.toString()) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { done(null) },
        title = { Text("수량 직접 입력") },
        text = {
            Column {
                OutlinedTextField(
                    value = raw,
                    onValueChange = { raw = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = DomainRules.parseQuantity(raw)
                if (parsed == null) {
                    error = "0 이상의 정수를 입력하세요."
                } else {
                    done(parsed)
                }
            }) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = { done(null) }) {
                Text("취소")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupScreen(
    sheet: CountingSheet,
    onBack: () -> Unit,
    onAdd: (String, String) -> Unit,
    onRename: (String, String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var create by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<CountingGroup?>(null) }
    var delete by remember { mutableStateOf<CountingGroup?>(null) }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("그룹 관리") },
                navigationIcon = { TextButton(onClick = onBack) { Text("뒤로") } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { create = true }) {
                Text("그룹 추가")
            }
        }
    ) { padding ->
        if (sheet.groups.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("그룹이 없습니다.")
            }
        } else {
            LazyColumn(
                Modifier.padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sheet.groups.sortedBy { it.order }) { g ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(18.dp).background(parseColor(g.color), CircleShape))
                            Spacer(Modifier.width(10.dp))
                            Text(g.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            TextButton(onClick = { rename = g }) { Text("이름 변경") }
                            TextButton(onClick = { delete = g }) { Text("삭제") }
                        }
                    }
                }
            }
        }
    }

    if (create) {
        GroupEditDialog("그룹 추가", "", "#6750A4") { name, color ->
            create = false
            if (name != null) onAdd(name, color)
        }
    }

    rename?.let { g ->
        GroupEditDialog("그룹 편집", g.name, g.color) { name, color ->
            rename = null
            if (name != null) onRename(g.id, name, color)
        }
    }

    delete?.let { g ->
        val itemCount = sheet.items.count { it.groupId == g.id }
        val calcCount = sheet.calculations.count {
            it.leftGroupId == g.id || it.rightGroupId == g.id
        }

        ConfirmDialog(
            "그룹 삭제",
            "항목 ${itemCount}개는 삭제되지 않고 '미지정'으로 변경됩니다. 관련 계산 ${calcCount}개는 삭제됩니다."
        ) {
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
        topBar = {
            TopAppBar(
                title = { Text("그룹 계산") },
                navigationIcon = { TextButton(onClick = onBack) { Text("뒤로") } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }) {
                Text("계산 추가")
            }
        }
    ) { padding ->
        if (sheet.groups.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("먼저 그룹을 만들어 주세요.")
            }
        } else if (sheet.calculations.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("등록된 계산이 없습니다.")
            }
        } else {
            LazyColumn(
                Modifier.padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sheet.calculations) { c ->
                    val lg = sheet.groups.firstOrNull { it.id == c.leftGroupId }?.name ?: "?"
                    val rg = sheet.groups.firstOrNull { it.id == c.rightGroupId }?.name ?: "?"
                    val op = if (c.operator == CalculationOperator.ADD) "+" else "-"
                    val leftTotals = DomainRules.aggregate(sheet.items.filter { it.groupId == c.leftGroupId })
                    val rightTotals = DomainRules.aggregate(sheet.items.filter { it.groupId == c.rightGroupId })
                    val result = DomainRules.calculate(leftTotals, c.operator, rightTotals)

                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                c.name.ifBlank { "$lg $op $rg" },
                                fontWeight = FontWeight.Bold
                            )
                            Text("$lg $op $rg")
                            result.forEach { (unit, q) ->
                                Text("$unit : $q")
                            }
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

    if (creating) {
        CalculationDialog(sheet, null) { d ->
            creating = false
            d?.let { onAdd(it.left, it.op, it.right, it.name) }
        }
    }

    editor?.let { c ->
        CalculationDialog(sheet, c) { d ->
            editor = null
            d?.let { onEdit(c.id, it.left, it.op, it.right, it.name) }
        }
    }

    delete?.let { c ->
        ConfirmDialog("계산 삭제", "이 계산을 삭제합니다.") {
            delete = null
            if (it) onDelete(c.id)
        }
    }
}

private data class CalcDraft(
    val left: String,
    val op: CalculationOperator,
    val right: String,
    val name: String
)

@Composable
private fun CalculationDialog(
    sheet: CountingSheet,
    current: GroupCalculation?,
    done: (CalcDraft?) -> Unit
) {
    if (sheet.groups.isEmpty()) {
        done(null)
        return
    }

    var left by remember {
        mutableStateOf(current?.leftGroupId ?: sheet.groups.first().id)
    }
    var right by remember {
        mutableStateOf(current?.rightGroupId ?: sheet.groups.first().id)
    }
    var op by remember {
        mutableStateOf(current?.operator ?: CalculationOperator.ADD)
    }
    var name by remember {
        mutableStateOf(current?.name ?: "")
    }

    AlertDialog(
        onDismissRequest = { done(null) },
        title = { Text(if (current == null) "계산 추가" else "계산 편집") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("왼쪽 그룹")
                sheet.groups.forEach { g ->
                    FilterChip(
                        selected = left == g.id,
                        onClick = { left = g.id },
                        label = { Text(g.name) }
                    )
                }

                Text("연산")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = op == CalculationOperator.ADD,
                        onClick = { op = CalculationOperator.ADD },
                        label = { Text("+") }
                    )
                    FilterChip(
                        selected = op == CalculationOperator.SUBTRACT,
                        onClick = { op = CalculationOperator.SUBTRACT },
                        label = { Text("−") }
                    )
                }

                Text("오른쪽 그룹")
                sheet.groups.forEach { g ->
                    FilterChip(
                        selected = right == g.id,
                        onClick = { right = g.id },
                        label = { Text(g.name) }
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("계산명 (선택)") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                done(CalcDraft(left, op, right, name.trim()))
            }) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = { done(null) }) {
                Text("취소")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultScreen(
    sheet: CountingSheet,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val result = remember(sheet) {
        ResultGenerator.generate(sheet)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("결과 미리보기") },
                navigationIcon = { TextButton(onClick = onBack) { Text("뒤로") } }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp)
        ) {
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                LazyColumn(Modifier.padding(12.dp)) {
                    item { Text(result) }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("실셈 결과", result)
                        )
                        Toast.makeText(
                            context,
                            "복사되었습니다.",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("복사")
                }

                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, result)
                        }
                        context.startActivity(
                            Intent.createChooser(intent, "실셈 결과 공유")
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("공유")
                }
            }
        }
    }
}


private val GROUP_COLORS = listOf("#6750A4", "#006C4C", "#9C4238", "#0061A4", "#7D5260", "#6B5E00", "#725188", "#3F6374")

private fun parseColor(hex: String): Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFF6750A4))

@Composable
private fun GroupEditDialog(title: String, initialName: String, initialColor: String, done: (String?, String) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor) }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { done(null, color) }, title = { Text(title) },
        text = { Column { OutlinedTextField(name, { name = it }, label = { Text("그룹명") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp)); Text("그룹 색상")
            Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { GROUP_COLORS.take(4).forEach { c -> ColorDot(c, color == c) { color = c } } }
            Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { GROUP_COLORS.drop(4).forEach { c -> ColorDot(c, color == c) { color = c } } }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(onClick = { if (name.trim().isEmpty()) error = "그룹명을 입력하세요." else done(name.trim(), color) }) { Text("확인") } },
        dismissButton = { TextButton(onClick = { done(null, color) }) { Text("취소") } }
    )
}

@Composable private fun ColorDot(color: String, selected: Boolean, onClick: () -> Unit) {
    Surface(modifier = Modifier.size(if (selected) 38.dp else 34.dp).clickable(onClick = onClick), shape = CircleShape, color = parseColor(color), tonalElevation = if (selected) 6.dp else 0.dp) { if (selected) Box(contentAlignment = Alignment.Center) { Text("✓", color = Color.White) } }
}

@Composable
private fun GroupAssignmentDialog(sheet: CountingSheet, done: (Set<String>?, String?) -> Unit) {
    var groupId by remember { mutableStateOf<String?>(sheet.groups.firstOrNull()?.id) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    AlertDialog(
        onDismissRequest = { done(null, null) },
        title = { Text("그룹 지정") },
        text = { Column {
            if (sheet.groups.isEmpty()) Text("먼저 그룹을 생성하세요.") else {
                Text("1. 배치할 그룹 선택", fontWeight = FontWeight.Bold)
                sheet.groups.sortedBy { it.order }.forEach { g -> Row(Modifier.fillMaxWidth().clickable { groupId = g.id }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(groupId == g.id, { groupId = g.id }); Box(Modifier.size(14.dp).background(parseColor(g.color), CircleShape)); Spacer(Modifier.width(8.dp)); Text(g.name) } }
                HorizontalDivider(); Spacer(Modifier.height(8.dp)); Text("2. 항목 선택", fontWeight = FontWeight.Bold)
                LazyColumn(Modifier.heightIn(max = 320.dp)) { items(sheet.items.sortedBy { it.order }) { item -> val checked = item.id in selected; Row(Modifier.fillMaxWidth().clickable { selected = if (checked) selected - item.id else selected + item.id }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, { v -> selected = if (v) selected + item.id else selected - item.id }); Text("${item.order + 1}. ${item.name}") } } }
            }
        } },
        confirmButton = { TextButton(enabled = groupId != null && selected.isNotEmpty(), onClick = { done(selected, groupId) }) { Text("확인") } },
        dismissButton = { TextButton(onClick = { done(null, null) }) { Text("취소") } }
    )
}

@Composable
private fun TextInputDialog(
    title: String,
    initial: String,
    helper: String,
    allowEmpty: Boolean = false,
    singleLine: Boolean = true,
    done: (String?) -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { done(null) },
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = singleLine,
                    modifier = Modifier.fillMaxWidth()
                )
                if (helper.isNotBlank()) {
                    Text(
                        helper,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (error.isNotBlank()) {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (!allowEmpty && value.trim().isEmpty()) {
                    error = "빈 값은 저장할 수 없습니다."
                } else {
                    done(value)
                }
            }) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = { done(null) }) {
                Text("취소")
            }
        }
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    done: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = { done(false) },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { done(true) }) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = { done(false) }) {
                Text("취소")
            }
        }
    )
}
