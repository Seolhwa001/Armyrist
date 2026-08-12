package com.seolhwa.armyrist

import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.scrollBy
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.seolhwa.armyrist.data.CountingRepository
import com.seolhwa.armyrist.stage2.data.CoreSuiteRepository
import com.seolhwa.armyrist.stage2.domain.ToolResult
import com.seolhwa.armyrist.domain.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as ArmyristApplication
        val repository = app.repository
        val coreSuiteRepository = app.coreSuiteRepository

        setContent {
            ArmyristTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ArmyristColors.AppBackground
                ) {
                    ArmyristApp(
                        repo = repository,
                        coreRepo = coreSuiteRepository,
                        onHome = { finish() }
                    )
                }
            }
        }
    }
}

private enum class Screen { SHEETS, COUNTING, GROUPS, CALCULATIONS, RESULT }

@Composable
private fun ArmyristApp(
    repo: CountingRepository,
    coreRepo: CoreSuiteRepository,
    onHome: () -> Unit
) {
    var screen by remember { mutableStateOf(Screen.SHEETS) }
    var selectedSheetId by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE")
    val observedRevision = revision

    fun refresh() {
        revision++
    }

    when (screen) {
        Screen.SHEETS -> SheetListScreen(
            sheets = repo.getSheets(),
            onHome = onHome,
            onCreate = {
                selectedSheetId = repo.createSheet().id
                refresh()
                screen = Screen.COUNTING
            },
            onOpen = {
                selectedSheetId = it
                screen = Screen.COUNTING
            },
            onRename = { id, title ->
                if (repo.renameSheet(id, title)) refresh()
            },
            onDelete = {
                repo.deleteSheet(it)
                refresh()
            }
        )

        else -> {
            val id = selectedSheetId
            val sheet = id?.let(repo::getSheet)

            if (sheet == null) {
                screen = Screen.SHEETS
            } else {
                when (screen) {
                    Screen.COUNTING -> CountingScreen(
                        sheet = sheet,
                        onHome = onHome,
                        onBack = { screen = Screen.SHEETS },
                        onAddGroup = { name, color, showAggregate ->
                            if (
                                repo.addGroup(
                                    sheet.id,
                                    name,
                                    color,
                                    showAggregate
                                )
                            ) refresh()
                        },
                        onRenameGroup = {
                            groupId,
                            name,
                            color,
                            showAggregate ->
                            if (
                                repo.renameGroup(
                                    sheet.id,
                                    groupId,
                                    name,
                                    color,
                                    showAggregate
                                )
                            ) refresh()
                        },
                        onDeleteGroup = {
                            repo.deleteGroup(sheet.id, it)
                            refresh()
                        },
                        onCalculations = { screen = Screen.CALCULATIONS },
                        onResult = { screen = Screen.RESULT },
                        onRename = {
                            if (repo.renameSheet(sheet.id, it)) refresh()
                        },
                        onMemo = {
                            repo.setMemo(sheet.id, it)
                            refresh()
                        },
                        onAddItem = { name, quantity, unit, note, groupId ->
                            if (repo.addItem(sheet.id, name, quantity, unit, note, groupId)) refresh()
                        },
                        onEditItem = { itemId, name, unit, note, groupId ->
                            if (repo.editItem(sheet.id, itemId, name, unit, note, groupId)) refresh()
                        },
                        onDeleteItem = {
                            repo.deleteItem(sheet.id, it)
                            refresh()
                        },
                        onIncrement = {
                            repo.increment(sheet.id, it)
                            refresh()
                        },
                        onDecrement = {
                            repo.decrement(sheet.id, it)
                            refresh()
                        },
                        onQuantity = { itemId, quantity ->
                            if (repo.setQuantity(sheet.id, itemId, quantity)) refresh()
                        },
                        onMove = { itemId, delta ->
                            repo.moveItem(sheet.id, itemId, delta)
                            refresh()
                        },
                        onAssignGroup = { itemIds, groupId ->
                            if (repo.assignItemsToGroup(sheet.id, itemIds, groupId)) refresh()
                        }
                    )

                    Screen.GROUPS -> GroupScreen(
                        sheet = sheet,
                        onBack = { screen = Screen.COUNTING },
                        onAdd = { name, color, showAggregate ->
                            if (
                                repo.addGroup(
                                    sheet.id,
                                    name,
                                    color,
                                    showAggregate
                                )
                            ) refresh()
                        },
                        onRename = {
                            groupId,
                            name,
                            color,
                            showAggregate ->
                            if (
                                repo.renameGroup(
                                    sheet.id,
                                    groupId,
                                    name,
                                    color,
                                    showAggregate
                                )
                            ) refresh()
                        },
                        onDelete = {
                            repo.deleteGroup(sheet.id, it)
                            refresh()
                        }
                    )

                    Screen.CALCULATIONS -> CalculationScreen(
                        sheet = sheet,
                        onBack = { screen = Screen.COUNTING },
                        onAdd = { left, operator, right, name ->
                            if (repo.addCalculation(sheet.id, left, operator, right, name)) refresh()
                        },
                        onEdit = { calcId, left, operator, right, name ->
                            if (repo.editCalculation(sheet.id, calcId, left, operator, right, name)) refresh()
                        },
                        onDelete = {
                            repo.deleteCalculation(sheet.id, it)
                            refresh()
                        }
                    )

                    Screen.RESULT -> CommonShareScreen(
                        repo = coreRepo,
                        result = ToolResult(
                            title = sheet.title,
                            body = ResultGenerator.generate(sheet)
                        ),
                        onBack = { screen = Screen.COUNTING },
                        portableType = ArmyristPortableDataType.COUNTING,
                        portableRootId = sheet.id
                    )

                    Screen.SHEETS -> Unit
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetListScreen(
    sheets: List<CountingSheet>,
    onHome: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var renameTarget by remember { mutableStateOf<CountingSheet?>(null) }
    var deleteTarget by remember { mutableStateOf<CountingSheet?>(null) }

    Scaffold(
        topBar = {
            ArmyristTopBar(
                title = "실셈",
                subtitle = "COUNTING / 현장 수량 기록",
                leadingLabel = "홈",
                onLeading = onHome
            )
        },
        floatingActionButton = {
            if (sheets.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onCreate,
                    modifier = Modifier.heightIn(min = 58.dp),
                    shape = ArmyristPanelShape,
                    containerColor = ArmyristColors.PrimaryControl,
                    contentColor = ArmyristColors.OnDark
                ) {
                    Text(
                        "+ 새 실셈",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { padding ->
        if (sheets.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ArmyristPanelShape,
                    colors = CardDefaults.cardColors(
                        containerColor = ArmyristColors.WorkSurface
                    ),
                    border = BorderStroke(
                        1.dp,
                        ArmyristColors.Border
                    )
                ) {
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 54.dp),
                            shape = ArmyristPanelShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ArmyristColors.PrimaryControl,
                                contentColor = ArmyristColors.OnDark
                            )
                        ) {
                            Text(
                                "새 실셈 만들기",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 8.dp,
                    bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sheets, key = { it.id }) { sheet ->
                    Card(
                        onClick = { onOpen(sheet.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = ArmyristPanelShape,
                        colors = CardDefaults.cardColors(
                            containerColor = ArmyristColors.RaisedSurface
                        ),
                        border = BorderStroke(
                            1.dp,
                            ArmyristColors.Border
                        )
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
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
                            TextButton(onClick = { renameTarget = sheet }) { Text("이름") }
                            TextButton(onClick = { deleteTarget = sheet }) { Text("삭제") }
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        TextInputDialog(
            title = "실셈표 이름 변경",
            initial = target.title,
            helper = "이름을 입력하세요."
        ) {
            renameTarget = null
            if (it != null) onRename(target.id, it)
        }
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "실셈표 삭제",
            message = "'${target.title}'와 모든 항목/그룹/계산을 삭제합니다."
        ) {
            deleteTarget = null
            if (it) onDelete(target.id)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountingScreen(
    sheet: CountingSheet,
    onHome: () -> Unit,
    onBack: () -> Unit,
    onAddGroup: (String, String, Boolean) -> Unit,
    onRenameGroup: (String, String, String, Boolean) -> Unit,
    onDeleteGroup: (String) -> Unit,
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
    val context = LocalContext.current
    var itemEditor by remember { mutableStateOf<CountingItem?>(null) }
    var creating by remember { mutableStateOf(false) }
    var quantityTarget by remember { mutableStateOf<CountingItem?>(null) }
    var deleteTarget by remember { mutableStateOf<CountingItem?>(null) }
    var titleEdit by remember { mutableStateOf(false) }
    var memoEdit by remember { mutableStateOf(false) }
    var groupManager by remember { mutableStateOf(false) }
    var menuTarget by remember { mutableStateOf<CountingItem?>(null) }

    var groupPickerOpen by remember { mutableStateOf(false) }
    var assignmentGroupId by remember { mutableStateOf<String?>(null) }
    var assignmentSelected by remember { mutableStateOf(setOf<String>()) }

    val dragThresholdPx = with(LocalDensity.current) { 44.dp.toPx() }
    val listState = rememberLazyListState()

    BackHandler {
        if (assignmentGroupId != null) {
            assignmentGroupId = null
            assignmentSelected = emptySet()
        } else {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            ArmyristTopBar(
                title = sheet.title,
                subtitle = "COUNTING · 항목 ${sheet.items.size} · AUTO SAVE",
                leadingLabel = "홈",
                onLeading = onHome
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                    shape = ArmyristPanelShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ArmyristColors.HeaderRaised,
                        contentColor = ArmyristColors.OnDark
                    )
                ) {
                    Text("목록", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { titleEdit = true },
                    modifier = Modifier.weight(1f),
                    shape = ArmyristPanelShape,
                    border = BorderStroke(
                        1.dp,
                        ArmyristColors.PrimaryControl
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = ArmyristColors.WorkSurface,
                        contentColor = ArmyristColors.PrimaryText
                    )
                ) {
                    Text("제목 수정", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onResult,
                    modifier = Modifier.weight(1f),
                    shape = ArmyristPanelShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ArmyristColors.PrimaryControl,
                        contentColor = ArmyristColors.OnDark
                    )
                ) {
                    Text("결과 전달", fontWeight = FontWeight.Bold)
                }

            }

            AggregateSummary(sheet)
            CalculationSummary(sheet)

            if (assignmentGroupId == null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ArmyristUtilityButton(
                        text = "그룹",
                        onClick = { groupManager = true }
                    )
                    ArmyristUtilityButton(
                        text = "그룹 지정",
                        onClick = { groupPickerOpen = true }
                    )
                    ArmyristUtilityButton(
                        text = "계산",
                        onClick = onCalculations
                    )
                    ArmyristUtilityButton(
                        text = "메모",
                        onClick = { memoEdit = true }
                    )
                }
            } else {
                val targetGroup = sheet.groups.firstOrNull { it.id == assignmentGroupId }
                val targetLabel =
                    if (assignmentGroupId?.isEmpty() == true) {
                        "미지정"
                    } else {
                        targetGroup?.name ?: "그룹"
                    }

                Surface(
                    color = targetGroup?.let { parseColor(it.color).copy(alpha = 0.14f) }
                        ?: ArmyristColors.SecondaryControl,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "$targetLabel 지정 중",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "항목을 터치해 선택 · ${assignmentSelected.size}개",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(
                            onClick = {
                                assignmentGroupId = null
                                assignmentSelected = emptySet()
                            }
                        ) {
                            Text("취소")
                        }
                        Button(
                            enabled = assignmentSelected.isNotEmpty(),
                            onClick = {
                                onAssignGroup(
                                    assignmentSelected,
                                    assignmentGroupId?.takeIf { it.isNotEmpty() }
                                )
                                assignmentGroupId = null
                                assignmentSelected = emptySet()
                            }
                        ) {
                            Text("확인")
                        }
                    }
                }
            }

            HorizontalDivider()

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 8.dp,
                    end = 8.dp,
                    top = 6.dp,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (sheet.items.isEmpty()) {
                    item {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("항목이 없습니다", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "아래 새 항목 추가 버튼으로 시작하세요.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                itemsIndexed(
                    sheet.items.sortedBy { it.order },
                    key = { _, item -> item.id }
                ) { index, item ->
                    val currentGroup = sheet.groups.firstOrNull { it.id == item.groupId }
                    val groupName = currentGroup?.name ?: "미지정"
                    val assignmentMode = assignmentGroupId != null
                    val selected = item.id in assignmentSelected

                    var dragOffsetY by remember(item.id) {
                        mutableFloatStateOf(0f)
                    }
                    var isDragging by remember(item.id) {
                        mutableStateOf(false)
                    }
                    val haptic = LocalHapticFeedback.current
                    val dragScope = rememberCoroutineScope()
                    val edgeThresholdPx =
                        with(LocalDensity.current) { 72.dp.toPx() }
                    val autoScrollStepPx =
                        with(LocalDensity.current) { 18.dp.toPx() }

                    val groupColor =
                        currentGroup?.let { parseColor(it.color) }

                    val baseColor = groupColor
                        ?.copy(alpha = 0.13f)
                        ?: ArmyristColors.RaisedSurface

                    val selectedColor = sheet.groups
                        .firstOrNull { it.id == assignmentGroupId }
                        ?.let { parseColor(it.color).copy(alpha = 0.26f) }
                        ?: ArmyristColors.SecondaryControl

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor =
                                if (selected) {
                                    selectedColor
                                } else {
                                    baseColor
                                }
                        ),
                        shape = ArmyristPanelShape,
                        border = BorderStroke(
                            1.dp,
                            when {
                                selected -> ArmyristColors.PrimaryControl
                                groupColor != null ->
                                    groupColor.copy(alpha = 0.75f)
                                else -> ArmyristColors.Divider
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                translationY =
                                    if (isDragging) dragOffsetY else 0f
                                shadowElevation =
                                    if (isDragging) 10f else 0f
                            }
                            .pointerInput(
                                item.id,
                                assignmentMode,
                                sheet.items.map { it.id }
                            ) {
                                if (!assignmentMode) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            isDragging = true
                                            dragOffsetY = 0f
                                            haptic.performHapticFeedback(
                                                HapticFeedbackType.LongPress
                                            )
                                        },
                                        onDragCancel = {
                                            isDragging = false
                                            dragOffsetY = 0f
                                        },
                                        onDragEnd = {
                                            isDragging = false
                                            dragOffsetY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount.y

                                            if (
                                                dragOffsetY >=
                                                dragThresholdPx
                                            ) {
                                                onMove(item.id, 1)
                                                dragOffsetY -=
                                                    dragThresholdPx
                                                haptic.performHapticFeedback(
                                                    HapticFeedbackType.TextHandleMove
                                                )
                                            } else if (
                                                dragOffsetY <=
                                                -dragThresholdPx
                                            ) {
                                                onMove(item.id, -1)
                                                dragOffsetY +=
                                                    dragThresholdPx
                                                haptic.performHapticFeedback(
                                                    HapticFeedbackType.TextHandleMove
                                                )
                                            }

                                            val info =
                                                listState.layoutInfo
                                            val dragged =
                                                info.visibleItemsInfo
                                                    .firstOrNull {
                                                        it.key == item.id
                                                    }

                                            if (dragged != null) {
                                                val top =
                                                    dragged.offset +
                                                        dragOffsetY
                                                val bottom =
                                                    top + dragged.size
                                                when {
                                                    top <
                                                        info.viewportStartOffset +
                                                        edgeThresholdPx -> {
                                                        dragScope.launch {
                                                            listState.scrollBy(
                                                                -autoScrollStepPx
                                                            )
                                                        }
                                                    }
                                                    bottom >
                                                        info.viewportEndOffset -
                                                        edgeThresholdPx -> {
                                                        dragScope.launch {
                                                            listState.scrollBy(
                                                                autoScrollStepPx
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                            .clickable {
                                if (assignmentMode) {
                                    assignmentSelected =
                                        if (selected) assignmentSelected - item.id
                                        else assignmentSelected + item.id
                                } else {
                                    itemEditor = item
                                }
                            }
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (groupColor != null) {
                                Box(
                                    Modifier
                                        .width(5.dp)
                                        .heightIn(min = 70.dp)
                                        .background(groupColor)
                                )
                            }

                            Row(
                                Modifier
                                    .weight(1f)
                                    .padding(
                                        horizontal = 12.dp,
                                        vertical = 10.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                            Text(
                                if (selected) "✓" else "${index + 1}.",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(34.dp)
                            )

                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${item.unit} · $groupName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (item.note.isNotBlank()) {
                                    Text(
                                        "비고: ${item.note}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (!assignmentMode) {
                                OutlinedButton(
                                    onClick = { onDecrement(item.id) },
                                    modifier = Modifier.sizeIn(
                                        minWidth = 54.dp,
                                        minHeight = 54.dp
                                    ),
                                    shape = ArmyristPanelShape,
                                    border = BorderStroke(
                                        1.dp,
                                        ArmyristColors.Border
                                    ),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("−", style = MaterialTheme.typography.titleLarge)
                                }

                                Button(
                                    onClick = { quantityTarget = item },
                                    modifier = Modifier
                                        .widthIn(min = 72.dp)
                                        .heightIn(min = 54.dp),
                                    shape = ArmyristPanelShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor =
                                            ArmyristColors.HeaderRaised,
                                        contentColor =
                                            ArmyristColors.OnDark
                                    ),
                                    contentPadding = PaddingValues(
                                        horizontal = 8.dp
                                    )
                                ) {
                                    Text(
                                        item.quantity.toString(),
                                        style =
                                            MaterialTheme.typography
                                                .headlineMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Button(
                                    onClick = { onIncrement(item.id) },
                                    modifier = Modifier.sizeIn(
                                        minWidth = 54.dp,
                                        minHeight = 54.dp
                                    ),
                                    shape = ArmyristPanelShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor =
                                            ArmyristColors.PrimaryControl,
                                        contentColor =
                                            ArmyristColors.OnDark
                                    ),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("+", style = MaterialTheme.typography.titleLarge)
                                }

                                Box {
                                    TextButton(
                                        onClick = { menuTarget = item },
                                        modifier = Modifier.widthIn(min = 44.dp)
                                    ) {
                                        Text("⋮")
                                    }

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
                            } else {
                                Text(
                                    item.quantity.toString(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                            }
                            }
                        }
                    }
                }

                item {
                    OutlinedButton(
                        onClick = { creating = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp),
                        shape = ArmyristPanelShape,
                        border = BorderStroke(
                            1.dp,
                            ArmyristColors.Accent
                        )
                    ) {
                        Text("+ 새 항목 추가")
                    }
                }

                item {
                    Card(
                        onClick = { memoEdit = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = ArmyristPanelShape,
                        colors = CardDefaults.cardColors(
                            containerColor = ArmyristColors.WorkSurface
                        ),
                        border = BorderStroke(
                            1.dp,
                            ArmyristColors.Border
                        )
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "메모",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "편집",
                                    color = ArmyristColors.PrimaryControl
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                sheet.memo.ifBlank {
                                    "메모가 없습니다. 눌러서 입력하세요."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (sheet.memo.isBlank()) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        ItemDialog(sheet, null) { data ->
            creating = false
            data?.let {
                onAddItem(it.name, it.quantity, it.unit, it.note, it.groupId)
            }
        }
    }

    itemEditor?.let { item ->
        ItemDialog(sheet, item) { data ->
            itemEditor = null
            data?.let {
                onEditItem(item.id, it.name, it.unit, it.note, it.groupId)
            }
        }
    }

    quantityTarget?.let { item ->
        QuantityDialog(item.quantity) { quantity ->
            quantityTarget = null
            if (quantity != null) onQuantity(item.id, quantity)
        }
    }

    deleteTarget?.let { item ->
        ConfirmDialog(
            title = "항목 삭제",
            message = "'${item.name}' 항목만 삭제합니다."
        ) {
            deleteTarget = null
            if (it) onDeleteItem(item.id)
        }
    }

    if (groupPickerOpen) {
        GroupPickerDialog(sheet) { groupId ->
            groupPickerOpen = false
            if (groupId != null) {
                assignmentGroupId =
                    if (groupId == "__UNASSIGNED__") "" else groupId
                assignmentSelected = emptySet()
            }
        }
    }

    if (titleEdit) {
        TextInputDialog(
            title = "제목 변경",
            initial = sheet.title,
            helper = "빈 제목은 저장할 수 없습니다."
        ) {
            titleEdit = false
            if (it != null && it.trim().isNotEmpty()) onRename(it)
        }
    }

    if (groupManager) {
        CountingGroupManagerDialog(
            sheet = sheet,
            onAdd = onAddGroup,
            onRename = onRenameGroup,
            onDelete = onDeleteGroup,
            onDismiss = { groupManager = false }
        )
    }

    if (memoEdit) {
        TextInputDialog(
            title = "실셈표 메모",
            initial = sheet.memo,
            helper = "",
            allowEmpty = true,
            singleLine = false
        ) {
            memoEdit = false
            if (it != null) onMemo(it)
        }
    }
}

@Composable
private fun ArmyristUtilityButton(
    text: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        shape = ArmyristPanelShape,
        border = BorderStroke(
            1.dp,
            ArmyristColors.Border
        ),
        contentPadding =
            PaddingValues(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun AggregateSummary(sheet: CountingSheet) {
    val sections = mutableListOf<Pair<String, Map<String, Int>>>()

    sheet.groups.sortedBy { it.order }.forEach { group ->
        if (!group.showAggregate) return@forEach

        val totals = DomainRules.aggregate(
            sheet.items.filter { it.groupId == group.id }
        )
        if (totals.isNotEmpty()) {
            sections += group.name to totals
        }
    }

    val ungrouped = sheet.items.filter { it.groupId == null }
    if (ungrouped.isNotEmpty()) {
        sections += "미지정" to DomainRules.aggregate(ungrouped)
    }

    if (sections.isNotEmpty()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                "현재 합계",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )

            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 112.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(sections) { (name, totals) ->
                    val group = sheet.groups.firstOrNull { it.name == name }
                    Surface(
                        color = group?.let {
                            parseColor(it.color).copy(alpha = 0.12f)
                        } ?: ArmyristColors.RaisedSurface,
                        shape = ArmyristPanelShape,
                        border = BorderStroke(
                            1.dp,
                            ArmyristColors.Border
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                name,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.widthIn(min = 76.dp)
                            )
                            Text(
                                totals.entries.joinToString("   ") {
                                    "${it.key} ${it.value}"
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalculationSummary(sheet: CountingSheet) {
    if (sheet.calculations.isEmpty()) return

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            "그룹 계산",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        sheet.calculations.forEach { calculation ->
            val leftGroup = sheet.groups.firstOrNull {
                it.id == calculation.leftGroupId
            } ?: return@forEach
            val rightGroup = sheet.groups.firstOrNull {
                it.id == calculation.rightGroupId
            } ?: return@forEach

            val left = DomainRules.aggregate(
                sheet.items.filter { it.groupId == leftGroup.id }
            )
            val right = DomainRules.aggregate(
                sheet.items.filter { it.groupId == rightGroup.id }
            )
            val result = DomainRules.calculate(
                left,
                calculation.operator,
                right
            )

            val symbol = if (calculation.operator == CalculationOperator.ADD) "+" else "−"
            val label = calculation.name.ifBlank {
                "${leftGroup.name} $symbol ${rightGroup.name}"
            }

            Surface(
                tonalElevation = 1.dp,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        label,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.widthIn(min = 110.dp)
                    )
                    Text(
                        result.entries.joinToString("   ") {
                            "${it.key} ${it.value}"
                        }
                    )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemDialog(
    sheet: CountingSheet,
    item: CountingItem?,
    done: (ItemDraft?) -> Unit
) {
    var name by remember { mutableStateOf(item?.name ?: "") }
    var quantityRaw by remember {
        mutableStateOf(item?.quantity?.toString() ?: "0")
    }
    var unit by remember { mutableStateOf(item?.unit ?: "개") }
    var note by remember { mutableStateOf(item?.note ?: "") }
    var groupId by remember { mutableStateOf(item?.groupId) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { done(null) },
        shape = ArmyristPanelShape,
        containerColor = ArmyristColors.WorkSurface,
        title = {
            Column {
                Text(
                    if (item == null) "항목 추가" else "항목 편집",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "COUNTING ITEM",
                    style = MaterialTheme.typography.labelSmall,
                    color = ArmyristColors.SecondaryText
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = ""
                    },
                    label = { Text("항목명") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ArmyristColors.InputSurface,
                        unfocusedContainerColor = ArmyristColors.InputSurface,
                        focusedBorderColor = ArmyristColors.PrimaryControl,
                        unfocusedBorderColor = ArmyristColors.Border
                    )
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (item == null) {
                        OutlinedTextField(
                            value = quantityRaw,
                            onValueChange = {
                                quantityRaw = it
                                error = ""
                            },
                            label = { Text("수량") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            ),
                            modifier = Modifier.weight(0.8f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = ArmyristColors.InputSurface,
                                unfocusedContainerColor = ArmyristColors.InputSurface,
                                focusedBorderColor = ArmyristColors.PrimaryControl,
                                unfocusedBorderColor = ArmyristColors.Border
                            )
                        )
                    }

                    OutlinedTextField(
                        value = unit,
                        onValueChange = {
                            unit = it
                            error = ""
                        },
                        label = { Text("단위") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ArmyristColors.InputSurface,
                            unfocusedContainerColor = ArmyristColors.InputSurface,
                            focusedBorderColor = ArmyristColors.PrimaryControl,
                            unfocusedBorderColor = ArmyristColors.Border
                        )
                    )
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("비고") },
                    minLines = 1,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ArmyristColors.InputSurface,
                        unfocusedContainerColor = ArmyristColors.InputSurface,
                        focusedBorderColor = ArmyristColors.PrimaryControl,
                        unfocusedBorderColor = ArmyristColors.Border
                    )
                )

                Text(
                    "그룹",
                    fontWeight = FontWeight.SemiBold
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    maxItemsInEachRow = 3
                ) {
                    val unassignedSelected = groupId == null
                    Surface(
                        onClick = { groupId = null },
                        shape = ArmyristPanelShape,
                        color =
                            if (unassignedSelected) {
                                ArmyristColors.SecondaryControl
                            } else {
                                ArmyristColors.RaisedSurface
                            },
                        border = BorderStroke(
                            if (unassignedSelected) 2.dp else 1.dp,
                            if (unassignedSelected) {
                                ArmyristColors.PrimaryControl
                            } else {
                                ArmyristColors.Border
                            }
                        )
                    ) {
                        Text(
                            "미지정",
                            modifier = Modifier.padding(
                                horizontal = 10.dp,
                                vertical = 8.dp
                            ),
                            fontWeight =
                                if (unassignedSelected) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Medium
                                }
                        )
                    }

                    sheet.groups
                        .sortedBy { it.order }
                        .forEach { group ->
                            val color = parseColor(group.color)
                            val selected = groupId == group.id

                            Surface(
                                onClick = { groupId = group.id },
                                shape = ArmyristPanelShape,
                                color = color.copy(
                                    alpha =
                                        if (selected) 0.28f else 0.12f
                                ),
                                border = BorderStroke(
                                    if (selected) 2.dp else 1.dp,
                                    color.copy(
                                        alpha =
                                            if (selected) 0.95f else 0.62f
                                    )
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = 9.dp,
                                        vertical = 7.dp
                                    ),
                                    verticalAlignment =
                                        Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier
                                            .size(10.dp)
                                            .background(
                                                color,
                                                ArmyristPanelShape
                                            )
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        group.name,
                                        maxLines = 1,
                                        fontWeight =
                                            if (selected) {
                                                FontWeight.Bold
                                            } else {
                                                FontWeight.Medium
                                            }
                                    )
                                }
                            }
                        }
                }

                if (error.isNotBlank()) {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val quantity =
                        if (item == null) {
                            DomainRules.parseQuantity(quantityRaw)
                        } else {
                            item.quantity
                        }

                    when {
                        name.trim().isEmpty() ->
                            error = "항목명을 입력하세요."

                        unit.trim().isEmpty() ->
                            error = "단위를 입력하세요."

                        quantity == null ->
                            error = "수량은 0 이상의 정수만 가능합니다."

                        else -> {
                            done(
                                ItemDraft(
                                    name = name.trim(),
                                    quantity = quantity,
                                    unit = unit.trim(),
                                    note = note.trim(),
                                    groupId = groupId
                                )
                            )
                        }
                    }
                },
                shape = ArmyristPanelShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ArmyristColors.PrimaryControl,
                    contentColor = ArmyristColors.OnDark
                )
            ) {
                Text(
                    "확인",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = { done(null) },
                shape = ArmyristPanelShape,
                border = BorderStroke(
                    1.dp,
                    ArmyristColors.Border
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = ArmyristColors.WorkSurface,
                    contentColor = ArmyristColors.PrimaryText
                )
            ) {
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
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    )
                )
                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = DomainRules.parseQuantity(raw)
                    if (parsed == null) {
                        error = "0 이상의 정수를 입력하세요."
                    } else {
                        done(parsed)
                    }
                }
            ) {
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
private fun CountingGroupManagerDialog(
    sheet: CountingSheet,
    onAdd: (String, String, Boolean) -> Unit,
    onRename: (String, String, String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var create by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<CountingGroup?>(null) }
    var delete by remember { mutableStateOf<CountingGroup?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = ArmyristPanelShape,
        containerColor = ArmyristColors.WorkSurface,
        title = {
            Text(
                "그룹 관리",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                if (sheet.groups.isEmpty()) {
                    Text(
                        "그룹이 없습니다.",
                        color = ArmyristColors.SecondaryText,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        items(
                            sheet.groups.sortedBy { it.order },
                            key = { it.id }
                        ) { group ->
                            val color = parseColor(group.color)
                            Surface(
                                onClick = { rename = group },
                                color = color.copy(alpha = 0.12f),
                                shape = ArmyristPanelShape,
                                border = BorderStroke(
                                    1.dp,
                                    color.copy(alpha = 0.65f)
                                )
                            ) {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment =
                                            Alignment.CenterVertically
                                    ) {
                                        Box(
                                            Modifier
                                                .size(18.dp)
                                                .background(
                                                    color,
                                                    CircleShape
                                                )
                                        )
                                        Spacer(Modifier.width(9.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                group.name,
                                                fontWeight =
                                                    FontWeight.SemiBold
                                            )
                                            Text(
                                                if (group.showAggregate) {
                                                    "합계 표시"
                                                } else {
                                                    "합계 숨김"
                                                },
                                                style =
                                                    MaterialTheme.typography
                                                        .bodySmall,
                                                color =
                                                    ArmyristColors
                                                        .SecondaryText
                                            )
                                        }
                                        TextButton(
                                            onClick = { rename = group }
                                        ) {
                                            Text("편집")
                                        }
                                        TextButton(
                                            onClick = { delete = group }
                                        ) {
                                            Text("삭제")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { create = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ArmyristPanelShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ArmyristColors.PrimaryControl,
                        contentColor = ArmyristColors.OnDark
                    )
                ) {
                    Text("+ 그룹 추가")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )

    if (create) {
        GroupEditDialog(
            title = "그룹 추가",
            initialName = "",
            initialColor = "#596B45",
            initialShowAggregate = true
        ) { name, color, showAggregate ->
            create = false
            if (name != null) {
                onAdd(name, color, showAggregate)
            }
        }
    }

    rename?.let { group ->
        GroupEditDialog(
            title = "그룹 편집",
            initialName = group.name,
            initialColor = group.color,
            initialShowAggregate = group.showAggregate
        ) { name, color, showAggregate ->
            rename = null
            if (name != null) {
                onRename(
                    group.id,
                    name,
                    color,
                    showAggregate
                )
            }
        }
    }

    delete?.let { group ->
        AlertDialog(
            onDismissRequest = { delete = null },
            title = { Text("그룹 삭제") },
            text = {
                Text(
                    "이 그룹의 항목은 삭제되지 않고 미지정으로 변경됩니다. " +
                        "관련 계산은 삭제됩니다."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(group.id)
                        delete = null
                    }
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { delete = null }) {
                    Text("취소")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupScreen(
    sheet: CountingSheet,
    onBack: () -> Unit,
    onAdd: (String, String, Boolean) -> Unit,
    onRename: (String, String, String, Boolean) -> Unit,
    onDelete: (String) -> Unit
) {
    var create by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<CountingGroup?>(null) }
    var delete by remember { mutableStateOf<CountingGroup?>(null) }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            ArmyristTopBar(
                title = "그룹 관리",
                subtitle = "COUNTING · GROUP",
                leadingLabel = "뒤로",
                onLeading = onBack
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { create = true }) {
                Text("그룹 추가")
            }
        }
    ) { padding ->
        if (sheet.groups.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("그룹이 없습니다.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sheet.groups.sortedBy { it.order }) { group ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = parseColor(group.color).copy(alpha = 0.12f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(18.dp)
                                    .background(parseColor(group.color), CircleShape)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    group.name,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (group.showAggregate) {
                                        "합계 표시"
                                    } else {
                                        "합계 숨김"
                                    },
                                    style =
                                        MaterialTheme.typography.bodySmall,
                                    color =
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { rename = group }) {
                                Text("편집")
                            }
                            TextButton(onClick = { delete = group }) {
                                Text("삭제")
                            }
                        }
                    }
                }
            }
        }
    }

    if (create) {
        GroupEditDialog(
            title = "그룹 추가",
            initialName = "",
            initialColor = "#596B45",
            initialShowAggregate = true
        ) { name, color, showAggregate ->
            create = false
            if (name != null) {
                onAdd(name, color, showAggregate)
            }
        }
    }

    rename?.let { group ->
        GroupEditDialog(
            title = "그룹 편집",
            initialName = group.name,
            initialColor = group.color,
            initialShowAggregate = group.showAggregate
        ) { name, color, showAggregate ->
            rename = null
            if (name != null) {
                onRename(
                    group.id,
                    name,
                    color,
                    showAggregate
                )
            }
        }
    }

    delete?.let { group ->
        val itemCount = sheet.items.count { it.groupId == group.id }
        val calculationCount = sheet.calculations.count {
            it.leftGroupId == group.id || it.rightGroupId == group.id
        }

        ConfirmDialog(
            title = "그룹 삭제",
            message = "항목 ${itemCount}개는 삭제되지 않고 '미지정'으로 변경됩니다. " +
                "관련 계산 ${calculationCount}개는 삭제됩니다."
        ) {
            delete = null
            if (it) onDelete(group.id)
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
    var deleteTarget by remember { mutableStateOf<GroupCalculation?>(null) }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            ArmyristTopBar(
                title = "그룹 계산",
                subtitle = "COUNTING · CALCULATION",
                leadingLabel = "뒤로",
                onLeading = onBack
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }) {
                Text("계산 추가")
            }
        }
    ) { padding ->
        when {
            sheet.groups.isEmpty() -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("먼저 그룹을 만들어 주세요.")
                }
            }

            sheet.calculations.isEmpty() -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("등록된 계산이 없습니다.")
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.padding(padding),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sheet.calculations) { calculation ->
                        val leftName = sheet.groups.firstOrNull {
                            it.id == calculation.leftGroupId
                        }?.name ?: "?"
                        val rightName = sheet.groups.firstOrNull {
                            it.id == calculation.rightGroupId
                        }?.name ?: "?"
                        val symbol =
                            if (calculation.operator == CalculationOperator.ADD) "+" else "−"

                        val leftTotals = DomainRules.aggregate(
                            sheet.items.filter {
                                it.groupId == calculation.leftGroupId
                            }
                        )
                        val rightTotals = DomainRules.aggregate(
                            sheet.items.filter {
                                it.groupId == calculation.rightGroupId
                            }
                        )
                        val result = DomainRules.calculate(
                            leftTotals,
                            calculation.operator,
                            rightTotals
                        )

                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    calculation.name.ifBlank {
                                        "$leftName $symbol $rightName"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                                Text("$leftName $symbol $rightName")
                                result.forEach { (unit, quantity) ->
                                    Text("$unit : $quantity")
                                }
                                Row {
                                    TextButton(onClick = { editor = calculation }) {
                                        Text("편집")
                                    }
                                    TextButton(onClick = { deleteTarget = calculation }) {
                                        Text("삭제")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        CalculationDialog(sheet, null) { draft ->
            creating = false
            draft?.let {
                onAdd(it.left, it.op, it.right, it.name)
            }
        }
    }

    editor?.let { calculation ->
        CalculationDialog(sheet, calculation) { draft ->
            editor = null
            draft?.let {
                onEdit(
                    calculation.id,
                    it.left,
                    it.op,
                    it.right,
                    it.name
                )
            }
        }
    }

    deleteTarget?.let { calculation ->
        ConfirmDialog(
            title = "계산 삭제",
            message = "이 계산을 삭제합니다."
        ) {
            deleteTarget = null
            if (it) onDelete(calculation.id)
        }
    }
}

private data class CalcDraft(
    val left: String,
    val op: CalculationOperator,
    val right: String,
    val name: String
)

@OptIn(ExperimentalLayoutApi::class)
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
    var operator by remember {
        mutableStateOf(current?.operator ?: CalculationOperator.ADD)
    }
    var name by remember {
        mutableStateOf(current?.name ?: "")
    }

    val scrollState = rememberScrollState()

    val selectedChipColors = FilterChipDefaults.filterChipColors(
        containerColor = ArmyristColors.RaisedSurface,
        labelColor = ArmyristColors.PrimaryText,
        selectedContainerColor = ArmyristColors.PrimaryControl,
        selectedLabelColor = ArmyristColors.OnDark
    )

    AlertDialog(
        onDismissRequest = { done(null) },
        shape = ArmyristPanelShape,
        containerColor = ArmyristColors.WorkSurface,
        titleContentColor = ArmyristColors.PrimaryText,
        textContentColor = ArmyristColors.PrimaryText,
        title = {
            Column {
                Text(
                    if (current == null) "계산 추가" else "계산 편집",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "COUNTING · CALCULATION",
                    style = MaterialTheme.typography.labelSmall,
                    color = ArmyristColors.SecondaryText
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("왼쪽 그룹", fontWeight = FontWeight.SemiBold)

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    sheet.groups.sortedBy { it.order }.forEach { group ->
                        val selected = left == group.id
                        FilterChip(
                            selected = selected,
                            onClick = { left = group.id },
                            label = {
                                Text(
                                    group.name,
                                    fontWeight = if (selected) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Medium
                                    }
                                )
                            },
                            colors = selectedChipColors,
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = ArmyristColors.Border,
                                selectedBorderColor = ArmyristColors.PrimaryControl,
                                borderWidth = 1.dp,
                                selectedBorderWidth = 2.dp
                            )
                        )
                    }
                }

                HorizontalDivider(color = ArmyristColors.Divider)

                Text("연산", fontWeight = FontWeight.SemiBold)

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    val addSelected = operator == CalculationOperator.ADD
                    FilterChip(
                        selected = addSelected,
                        onClick = { operator = CalculationOperator.ADD },
                        label = { Text("+", fontWeight = FontWeight.Bold) },
                        colors = selectedChipColors,
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = addSelected,
                            borderColor = ArmyristColors.Border,
                            selectedBorderColor = ArmyristColors.PrimaryControl,
                            borderWidth = 1.dp,
                            selectedBorderWidth = 2.dp
                        )
                    )

                    val subtractSelected =
                        operator == CalculationOperator.SUBTRACT
                    FilterChip(
                        selected = subtractSelected,
                        onClick = {
                            operator = CalculationOperator.SUBTRACT
                        },
                        label = { Text("−", fontWeight = FontWeight.Bold) },
                        colors = selectedChipColors,
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = subtractSelected,
                            borderColor = ArmyristColors.Border,
                            selectedBorderColor = ArmyristColors.PrimaryControl,
                            borderWidth = 1.dp,
                            selectedBorderWidth = 2.dp
                        )
                    )
                }

                HorizontalDivider(color = ArmyristColors.Divider)

                Text("오른쪽 그룹", fontWeight = FontWeight.SemiBold)

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    sheet.groups.sortedBy { it.order }.forEach { group ->
                        val selected = right == group.id
                        FilterChip(
                            selected = selected,
                            onClick = { right = group.id },
                            label = {
                                Text(
                                    group.name,
                                    fontWeight = if (selected) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Medium
                                    }
                                )
                            },
                            colors = selectedChipColors,
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = ArmyristColors.Border,
                                selectedBorderColor = ArmyristColors.PrimaryControl,
                                borderWidth = 1.dp,
                                selectedBorderWidth = 2.dp
                            )
                        )
                    }
                }

                HorizontalDivider(color = ArmyristColors.Divider)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("계산명 (선택)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ArmyristColors.InputSurface,
                        unfocusedContainerColor = ArmyristColors.InputSurface,
                        focusedBorderColor = ArmyristColors.PrimaryControl,
                        unfocusedBorderColor = ArmyristColors.Border
                    )
                )

                Spacer(Modifier.height(2.dp))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    done(
                        CalcDraft(
                            left = left,
                            op = operator,
                            right = right,
                            name = name.trim()
                        )
                    )
                },
                shape = ArmyristPanelShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ArmyristColors.PrimaryControl,
                    contentColor = ArmyristColors.OnDark
                )
            ) {
                Text("확인", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = { done(null) },
                shape = ArmyristPanelShape,
                border = BorderStroke(1.dp, ArmyristColors.Border),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = ArmyristColors.WorkSurface,
                    contentColor = ArmyristColors.PrimaryText
                )
            ) {
                Text("취소")
            }
        }
    )
}

private val GROUP_COLORS = listOf(
    "#596B45",
    "#006C4C",
    "#9C4238",
    "#0061A4",
    "#7D5260",
    "#6B5E00",
    "#725188",
    "#3F6374"
)

private fun parseColor(hex: String): Color {
    return runCatching {
        Color(android.graphics.Color.parseColor(hex))
    }.getOrDefault(Color(0xFF596B45))
}

@Composable
private fun GroupEditDialog(
    title: String,
    initialName: String,
    initialColor: String,
    initialShowAggregate: Boolean,
    done: (String?, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor) }
    var showAggregate by remember {
        mutableStateOf(initialShowAggregate)
    }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = {
            done(null, color, showAggregate)
        },
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("그룹명") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(14.dp))
                Text("그룹 색상")
                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GROUP_COLORS.take(4).forEach { candidate ->
                        ColorDot(
                            color = candidate,
                            selected = color == candidate,
                            onClick = { color = candidate }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GROUP_COLORS.drop(4).forEach { candidate ->
                        ColorDot(
                            color = candidate,
                            selected = color == candidate,
                            onClick = { color = candidate }
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "합계 표시",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "그룹 일반 합계를 화면/전달문에 표시합니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = showAggregate,
                        onCheckedChange = {
                            showAggregate = it
                        }
                    )
                }

                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.trim().isEmpty()) {
                        error = "그룹명을 입력하세요."
                    } else {
                        done(
                            name.trim(),
                            color,
                            showAggregate
                        )
                    }
                }
            ) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    done(null, color, showAggregate)
                }
            ) {
                Text("취소")
            }
        }
    )
}

@Composable
private fun ColorDot(
    color: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(if (selected) 38.dp else 34.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = parseColor(color),
        tonalElevation = if (selected) 6.dp else 0.dp
    ) {
        if (selected) {
            Box(contentAlignment = Alignment.Center) {
                Text("✓", color = Color.White)
            }
        }
    }
}

@Composable
private fun GroupPickerDialog(
    sheet: CountingSheet,
    done: (String?) -> Unit
) {
    AlertDialog(
        onDismissRequest = { done(null) },
        shape = ArmyristPanelShape,
        containerColor = ArmyristColors.WorkSurface,
        titleContentColor = ArmyristColors.PrimaryText,
        textContentColor = ArmyristColors.PrimaryText,
        title = {
            Column {
                Text(
                    "그룹 지정",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "지정할 그룹을 선택하세요.",
                    style = MaterialTheme.typography.labelSmall,
                    color = ArmyristColors.SecondaryText
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = ArmyristColors.RaisedSurface,
                    shape = ArmyristPanelShape,
                    border = BorderStroke(
                        1.dp,
                        ArmyristColors.Border
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { done("__UNASSIGNED__") }
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 14.dp,
                                vertical = 14.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(16.dp)
                                .background(
                                    ArmyristColors.Divider,
                                    CircleShape
                                )
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "미지정",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                sheet.groups
                    .sortedBy { it.order }
                    .forEach { group ->
                        val color = parseColor(group.color)

                        Surface(
                            color = color.copy(alpha = 0.13f),
                            shape = ArmyristPanelShape,
                            border = BorderStroke(
                                1.dp,
                                color.copy(alpha = 0.72f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { done(group.id) }
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = 14.dp,
                                        vertical = 14.dp
                                    ),
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(16.dp)
                                        .background(
                                            color,
                                            CircleShape
                                        )
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    group.name,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { done(null) }) {
                Text("취소")
            }
        }
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
            TextButton(
                onClick = {
                    if (!allowEmpty && value.trim().isEmpty()) {
                        error = "빈 값은 저장할 수 없습니다."
                    } else {
                        done(value)
                    }
                }
            ) {
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
