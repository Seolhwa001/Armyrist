@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.seolhwa.armyrist.timeplan.v3.ui

import androidx.compose.ui.draw.drawBehind

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.seolhwa.armyrist.*
import com.seolhwa.armyrist.notification.TimePlanActionNotificationManager
import com.seolhwa.armyrist.stage2.data.CoreSuiteRepository
import com.seolhwa.armyrist.timeplan.v3.data.DateAwareTimePlanRepository
import com.seolhwa.armyrist.timeplan.v3.domain.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.abs

enum class TimePlanExecutionMode { PREPARE, EXECUTE }
enum class TimePlanExecutionView { TIMELINE, GROUP }

@Composable
fun TimePlanExecutionApp(
    planId: String,
    initialMode: String,
    initialPointIds: Set<String>,
    repository: DateAwareTimePlanRepository,
    coreRepository: CoreSuiteRepository,
    onBack: () -> Unit
) {
    var revision by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val observed = revision
    var mode by rememberSaveable(planId) {
        mutableStateOf(
            if (initialMode == TimePlanExecutionActivity.MODE_EXECUTE) {
                TimePlanExecutionMode.EXECUTE
            } else TimePlanExecutionMode.PREPARE
        )
    }
    var selectedPointIds by rememberSaveable(planId) {
        mutableStateOf(initialPointIds.toList())
    }
    var sharing by rememberSaveable(planId) { mutableStateOf(false) }
    val plan = repository.getPlan(planId)

    if (plan == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("시간계획을 찾을 수 없습니다.")
                Spacer(Modifier.height(12.dp))
                Button(onClick = onBack) { Text("돌아가기") }
            }
        }
        return
    }

    if (sharing) {
        CommonShareScreen(
            repo = coreRepository,
            result = TimePlanExecutionResultGenerator.compact(plan),
            detailedResult = TimePlanExecutionResultGenerator.detailed(plan),
            onBack = { sharing = false },
            portableType = ArmyristPortableDataType.TIME_PLAN,
            portableRootId = plan.id
        )
        return
    }

    fun commit(candidate: DateAwareTimePlan): Boolean {
        val normalized = TimePlanExecutionRules.normalizeActionOrder(
            candidate.copy(updatedAt = System.currentTimeMillis().toString())
        )
        val ok = repository.commit(normalized)
        if (ok) revision++
        return ok
    }

    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            ArmyristTopBar(
                title = if (mode == TimePlanExecutionMode.EXECUTE) "수행 모드" else "실시사항 편집",
                subtitle = plan.title,
                leadingLabel = "뒤로",
                onLeading = onBack,
                actions = {
                    TextButton(onClick = { sharing = true }) {
                        Text("결과", color = ArmyristColors.OnDark)
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ArmyristModeButton(
                    label = "편집/준비",
                    selected = mode == TimePlanExecutionMode.PREPARE,
                    onClick = { mode = TimePlanExecutionMode.PREPARE },
                    modifier = Modifier.weight(1f)
                )
                ArmyristModeButton(
                    label = "수행 모드",
                    selected = mode == TimePlanExecutionMode.EXECUTE,
                    onClick = { mode = TimePlanExecutionMode.EXECUTE },
                    modifier = Modifier.weight(1f)
                )
            }

            if (mode == TimePlanExecutionMode.PREPARE) {
                TimePlanPrepareScreen(
                    plan = plan,
                    initiallySelectedPointIds = selectedPointIds.toSet(),
                    onSelectedPointIdsChange = { selectedPointIds = it.toList() },
                    onCommit = ::commit
                )
            } else {
                TimePlanExecuteScreen(
                    plan = plan,
                    onCommit = ::commit
                )
            }
        }
    }
}

@Composable
private fun TimePlanPrepareScreen(
    plan: DateAwareTimePlan,
    initiallySelectedPointIds: Set<String>,
    onSelectedPointIdsChange: (Set<String>) -> Unit,
    onCommit: (DateAwareTimePlan) -> Boolean
) {
    val context = LocalContext.current
    val allPointIds = remember(plan) { DateTimePlanRules.nodeIds(plan) }
    var selectedPoints by rememberSaveable(plan.id) {
        mutableStateOf(initiallySelectedPointIds.intersect(allPointIds.toSet()).toList())
    }
    var selectedActions by rememberSaveable(plan.id) { mutableStateOf(emptyList<String>()) }
    var editAction by remember { mutableStateOf<TimePlanActionItem?>(null) }
    var addForPoint by remember { mutableStateOf<String?>(null) }
    var batchAdd by remember { mutableStateOf(false) }
    var batchShift by remember { mutableStateOf(false) }
    var groupManager by remember { mutableStateOf(false) }
    var moveAction by remember { mutableStateOf<TimePlanActionItem?>(null) }
    var batchGroup by remember { mutableStateOf(false) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedPoints) { onSelectedPointIdsChange(selectedPoints.toSet()) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        message = if (granted) "알림 권한을 허용했습니다." else "알림 권한이 없어 예정 알림은 표시되지 않습니다."
    }

    fun togglePoint(id: String) {
        selectedPoints = (if (id in selectedPoints) selectedPoints - id else selectedPoints + id)
    }
    fun toggleAction(id: String) {
        selectedActions = (if (id in selectedActions) selectedActions - id else selectedActions + id)
    }

    Column(Modifier.fillMaxSize()) {
        ArmyristPanel(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("실시사항 준비", fontWeight = FontWeight.Bold)
                Text(
                    "지점 ${selectedPoints.size}개 · 실시사항 ${selectedActions.size}개 선택",
                    style = MaterialTheme.typography.bodySmall,
                    color = ArmyristColors.SecondaryText
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = { groupManager = true },
                        modifier = Modifier.weight(1f).heightIn(min = 38.dp),
                        shape = ArmyristPanelShape,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) { Text("그룹 관리") }

                    OutlinedButton(
                        onClick = {
                            selectedPoints =
                                if (
                                    selectedPoints.toSet()
                                        .containsAll(allPointIds.toSet()) &&
                                    allPointIds.isNotEmpty()
                                ) {
                                    emptyList()
                                } else {
                                    allPointIds
                                }
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 38.dp),
                        shape = ArmyristPanelShape,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            if (
                                selectedPoints.toSet()
                                    .containsAll(allPointIds.toSet()) &&
                                allPointIds.isNotEmpty()
                            ) "지점 해제" else "지점 전체"
                        )
                    }

                    OutlinedButton(
                        enabled = plan.actions.isNotEmpty(),
                        onClick = {
                            val allActionIds = plan.actions.map { it.id }
                            selectedActions =
                                if (
                                    selectedActions.toSet()
                                        .containsAll(allActionIds.toSet()) &&
                                    allActionIds.isNotEmpty()
                                ) {
                                    emptyList()
                                } else {
                                    allActionIds
                                }
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 38.dp),
                        shape = ArmyristPanelShape,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        val allActionIds = plan.actions.map { it.id }
                        Text(
                            if (
                                selectedActions.toSet()
                                    .containsAll(allActionIds.toSet()) &&
                                allActionIds.isNotEmpty()
                            ) "실시사항 해제" else "실시사항 전체"
                        )
                    }
                }

                if (selectedPoints.isNotEmpty()) {
                    Button(
                        onClick = { batchAdd = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = ArmyristPanelShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ArmyristColors.SecondaryControl,
                            contentColor = ArmyristColors.PrimaryText
                        ),
                        border = BorderStroke(1.dp, ArmyristColors.PrimaryControl)
                    ) { Text("선택 지점에 실시사항 일괄 추가") }
                }

                if (selectedActions.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OutlinedButton(
                            onClick = { batchShift = true },
                            modifier = Modifier.weight(1f),
                            shape = ArmyristPanelShape
                        ) { Text("시간 이동") }
                        OutlinedButton(
                            onClick = { batchGroup = true },
                            modifier = Modifier.weight(1f),
                            shape = ArmyristPanelShape
                        ) { Text("그룹 지정") }
                        OutlinedButton(
                            onClick = { confirmDeleteSelected = true },
                            modifier = Modifier.weight(1f),
                            shape = ArmyristPanelShape
                        ) { Text("삭제") }
                    }
                }
            }
        }

        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(8.dp, 4.dp, 8.dp, 88.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            items(allPointIds, key = { "point-$it" }) { pointId ->
                val pointActions = TimePlanExecutionRules.actionsForPoint(plan, pointId)
                val pointTime = TimePlanExecutionRules.pointDateTime(plan, pointId)
                val pointSelected = pointId in selectedPoints
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ArmyristPanelShape,
                    colors = CardDefaults.cardColors(
                        containerColor = if (pointSelected) ArmyristColors.SecondaryControl else ArmyristColors.RaisedSurface
                    ),
                    border = BorderStroke(1.dp, if (pointSelected) ArmyristColors.PrimaryControl else ArmyristColors.Border)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = pointSelected, onCheckedChange = { togglePoint(pointId) })
                            Column(Modifier.weight(1f)) {
                                Text(TimePlanExecutionRules.pointName(plan, pointId), fontWeight = FontWeight.Bold)
                                Text(pointTime?.format(dateClock) ?: "시간 미설정", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(
                                onClick = {
                                    if (pointTime == null) message = "지점 시간이 설정되어 있어야 실시사항을 추가할 수 있습니다."
                                    else addForPoint = pointId
                                }
                            ) { Text("+ 추가") }
                        }

                        if (pointActions.isEmpty()) {
                            Text("실시사항 없음", style = MaterialTheme.typography.bodySmall, color = ArmyristColors.SecondaryText)
                        } else {
                            pointActions.forEach { action ->
                                val selected = action.id in selectedActions
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = ArmyristPanelShape,
                                    color = if (selected) ArmyristColors.SecondaryControl else ArmyristColors.WorkSurface,
                                    border = BorderStroke(1.dp, ArmyristColors.Border)
                                ) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { editAction = action }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = selected,
                                            onCheckedChange = { toggleAction(action.id) }
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(action.content, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                buildString {
                                                    append(formatActionDateTime(plan, action.scheduledDateTime))
                                                    action.groupId?.let { gid ->
                                                        plan.actionGroups.firstOrNull { it.id == gid }?.let { append(" · ${it.name}") }
                                                    }
                                                    if (action.notificationMode == ActionNotificationMode.SIMPLE) append(" · 간단한 알림")
                                                    if (action.notificationMode == ActionNotificationMode.MUSIC) append(" · 음악 알림")
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = ArmyristColors.SecondaryText
                                            )
                                            action.note?.takeIf { it.isNotBlank() }?.let {
                                                Text("비고: $it", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                        Text(
                                            "편집",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = ArmyristColors.PrimaryControl,
                                            modifier = Modifier.padding(start = 6.dp)
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

    addForPoint?.let { pointId ->
        val pointTime = TimePlanExecutionRules.pointDateTime(plan, pointId)
        if (pointTime != null) {
            ActionEditDialog(
                title = "실시사항 추가",
                initial = null,
                baseDateTime = pointTime,
                groups = plan.actionGroups,
                onDismiss = { addForPoint = null },
                onConfirm = { draft ->
                    if (draft.notificationEnabled && Build.VERSION.SDK_INT >= 33 && !TimePlanActionNotificationManager.notificationPermissionGranted(context)) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    val action = draft.copy(
                        id = UUID.randomUUID().toString(),
                        parentPointId = pointId,
                        order = TimePlanExecutionRules.actionsForPoint(plan, pointId).size,
                        createdAt = System.currentTimeMillis().toString(),
                        updatedAt = System.currentTimeMillis().toString()
                    )
                    if (!onCommit(plan.copy(actions = plan.actions + action))) message = "실시사항을 저장하지 못했습니다."
                    addForPoint = null
                }
            )
        }
    }

    editAction?.let { action ->
        ActionEditDialog(
            title = "실시사항 편집",
            initial = action,
            baseDateTime = action.scheduledDateTime,
            groups = plan.actionGroups,
            onDismiss = { editAction = null },
            onMove = {
                moveAction = action
                editAction = null
            },
            onDelete = {
                onCommit(TimePlanExecutionRules.batchDelete(plan, setOf(action.id)))
                editAction = null
            },
            onConfirm = { changed ->
                if (changed.notificationEnabled && Build.VERSION.SDK_INT >= 33 && !TimePlanActionNotificationManager.notificationPermissionGranted(context)) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                val candidate = plan.copy(
                    actions = plan.actions.map {
                        if (it.id == action.id) changed.copy(
                            id = action.id,
                            parentPointId = action.parentPointId,
                            order = action.order,
                            createdAt = action.createdAt,
                            updatedAt = System.currentTimeMillis().toString()
                        ) else it
                    }
                )
                if (!onCommit(candidate)) message = "실시사항을 저장하지 못했습니다."
                editAction = null
            }
        )
    }

    moveAction?.let { action ->
        ActionMoveDialog(
            plan = plan,
            action = action,
            onDismiss = { moveAction = null },
            onMove = { targetPointId ->
                val targetTime = TimePlanExecutionRules.pointDateTime(plan, targetPointId)
                val sourceTime = TimePlanExecutionRules.pointDateTime(plan, action.parentPointId)
                val shiftedTime =
                    if (targetTime != null && sourceTime != null) {
                        action.scheduledDateTime.plusMinutes(
                            java.time.Duration.between(sourceTime, targetTime).toMinutes()
                        )
                    } else action.scheduledDateTime
                val candidate = TimePlanExecutionRules.normalizeActionOrder(
                    plan.copy(
                        actions = plan.actions.map {
                            if (it.id == action.id) it.copy(
                                parentPointId = targetPointId,
                                scheduledDateTime = shiftedTime,
                                updatedAt = System.currentTimeMillis().toString()
                            ) else it
                        }
                    )
                )
                if (!onCommit(candidate)) message = "실시사항을 이동하지 못했습니다."
                moveAction = null
            }
        )
    }

    if (batchAdd) {
        BatchAddDialog(
            count = selectedPoints.size,
            groups = plan.actionGroups,
            onDismiss = { batchAdd = false },
            onConfirm = { content, offset, note, groupId, notify ->
                val now = System.currentTimeMillis().toString()
                val additions = selectedPoints.mapNotNull { pointId ->
                    TimePlanExecutionRules.pointDateTime(plan, pointId)?.let { parentTime ->
                        TimePlanActionItem(
                            id = UUID.randomUUID().toString(),
                            parentPointId = pointId,
                            content = content,
                            scheduledDateTime = parentTime.plusMinutes(offset),
                            notificationEnabled = notify,
                            groupId = groupId,
                            note = note,
                            order = TimePlanExecutionRules.actionsForPoint(plan, pointId).size,
                            createdAt = now,
                            updatedAt = now
                        )
                    }
                }
                if (additions.size != selectedPoints.size) {
                    message = "시간이 설정되지 않은 지점이 포함되어 일괄 추가하지 않았습니다."
                } else if (content.isNotBlank()) {
                    if (notify && Build.VERSION.SDK_INT >= 33 && !TimePlanActionNotificationManager.notificationPermissionGranted(context)) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    onCommit(plan.copy(actions = plan.actions + additions))
                    selectedPoints = emptyList()
                }
                batchAdd = false
            }
        )
    }

    if (batchShift) {
        NumberInputDialog(
            title = "선택 실시사항 시간 이동",
            description = "+/- 분 단위로 입력합니다.",
            initial = "10",
            onDismiss = { batchShift = false },
            onConfirm = { minutes ->
                onCommit(TimePlanExecutionRules.batchShift(plan, selectedActions.toSet(), minutes))
                selectedActions = emptyList()
                batchShift = false
            }
        )
    }

    if (batchGroup) {
        GroupPickerDialog(
            title = "그룹 지정",
            groups = plan.actionGroups,
            onDismiss = { batchGroup = false },
            onSelect = { groupId ->
                onCommit(TimePlanExecutionRules.batchAssignGroup(plan, selectedActions.toSet(), groupId))
                selectedActions = emptyList()
                batchGroup = false
            }
        )
    }

    if (confirmDeleteSelected) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSelected = false },
            title = { Text("실시사항 삭제") },
            text = { Text("선택한 실시사항 ${selectedActions.size}개를 삭제할까요?") },
            dismissButton = { TextButton(onClick = { confirmDeleteSelected = false }) { Text("취소") } },
            confirmButton = {
                Button(onClick = {
                    onCommit(TimePlanExecutionRules.batchDelete(plan, selectedActions.toSet()))
                    selectedActions = emptyList()
                    confirmDeleteSelected = false
                }) { Text("삭제") }
            }
        )
    }

    if (groupManager) {
        ActionGroupManagerDialog(
            plan = plan,
            onDismiss = { groupManager = false },
            onCommit = onCommit
        )
    }

    message?.let {
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("확인") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("확인") } }
        )
    }
}

@Composable
private fun TimePlanExecuteScreen(
    plan: DateAwareTimePlan,
    onCommit: (DateAwareTimePlan) -> Boolean
) {
    var view by rememberSaveable(plan.id) { mutableStateOf(TimePlanExecutionView.TIMELINE) }
    var noteTarget by remember { mutableStateOf<TimePlanActionItem?>(null) }
    var editAction by remember { mutableStateOf<TimePlanActionItem?>(null) }
    var moveAction by remember { mutableStateOf<TimePlanActionItem?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var bulkSelect by rememberSaveable(plan.id) { mutableStateOf(false) }
    var selectedActionIds by rememberSaveable(plan.id) { mutableStateOf(emptyList<String>()) }
    val summary = TimePlanExecutionRules.summary(plan)
    val executionListState = rememberLazyListState()
    val actionVerticalBounds = remember { mutableStateMapOf<String, Pair<Float, Float>>() }
    var listTopInRoot by remember { mutableFloatStateOf(0f) }
    var listBottomInRoot by remember { mutableFloatStateOf(0f) }
    val dragPreviewStates = remember { mutableStateMapOf<String, ActionCompletionState>() }
    val dragScrollScope = rememberCoroutineScope()

    fun actionIdAt(rootY: Float): String? =
        actionVerticalBounds.entries.firstOrNull { (_, bounds) ->
            rootY >= bounds.first && rootY <= bounds.second
        }?.key

    Column(Modifier.fillMaxSize()) {
        ArmyristPanel(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
            Text("수행 현황", fontWeight = FontWeight.Bold)
            Text(
                "전체 ${summary.total} · 완료 ${summary.completed} · 미실시 ${summary.incomplete} · 완료율 ${summary.completionRate?.let { "$it%" } ?: "-"}",
                style = MaterialTheme.typography.bodySmall
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ArmyristModeButton(
                    label = "시간순",
                    selected = view == TimePlanExecutionView.TIMELINE,
                    onClick = { view = TimePlanExecutionView.TIMELINE },
                    modifier = Modifier.weight(1f)
                )
                ArmyristModeButton(
                    label = "그룹별",
                    selected = view == TimePlanExecutionView.GROUP,
                    onClick = { view = TimePlanExecutionView.GROUP },
                    modifier = Modifier.weight(1f)
                )
            }

            if (plan.actions.isNotEmpty()) {
                val allActionIds = plan.actions.map { it.id }
                if (!bulkSelect) {
                    OutlinedButton(
                        onClick = {
                            bulkSelect = true
                            selectedActionIds = allActionIds
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 38.dp),
                        shape = ArmyristPanelShape,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        border = BorderStroke(1.dp, ArmyristColors.Border),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = ArmyristColors.WorkSurface,
                            contentColor = ArmyristColors.PrimaryText
                        )
                    ) {
                        Text("전체 선택", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = ArmyristPanelShape,
                        color = ArmyristColors.WorkSurface,
                        border = BorderStroke(1.dp, ArmyristColors.Border)
                    ) {
                        Column(
                            Modifier.padding(5.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "선택 ${selectedActionIds.size} / ${plan.actions.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = ArmyristColors.SecondaryText
                            )
                            OutlinedButton(
                                onClick = { selectedActionIds = emptyList(); bulkSelect = false },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 38.dp),
                                shape = ArmyristPanelShape,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                border = BorderStroke(1.dp, ArmyristColors.Border),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = ArmyristColors.WorkSurface, contentColor = ArmyristColors.PrimaryText)
                            ) { Text("선택 종료", fontWeight = FontWeight.SemiBold) }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    enabled = selectedActionIds.isNotEmpty(),
                                    onClick = {
                                        onCommit(
                                            plan.copy(
                                                actions = plan.actions.map {
                                                    if (it.id in selectedActionIds) {
                                                        it.copy(
                                                            completionState = ActionCompletionState.COMPLETE,
                                                            updatedAt = System.currentTimeMillis().toString()
                                                        )
                                                    } else it
                                                }
                                            )
                                        )
                                    },
                                    modifier = Modifier.weight(1f).heightIn(min = 38.dp),
                                    shape = ArmyristPanelShape,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ArmyristColors.PrimaryControl,
                                        contentColor = ArmyristColors.OnDark
                                    )
                                ) {
                                    Text("실시 완료")
                                }
                                OutlinedButton(
                                    enabled = selectedActionIds.isNotEmpty(),
                                    onClick = {
                                        onCommit(
                                            plan.copy(
                                                actions = plan.actions.map {
                                                    if (it.id in selectedActionIds) {
                                                        it.copy(
                                                            completionState = ActionCompletionState.INCOMPLETE,
                                                            updatedAt = System.currentTimeMillis().toString()
                                                        )
                                                    } else it
                                                }
                                            )
                                        )
                                    },
                                    modifier = Modifier.weight(1f).heightIn(min = 38.dp),
                                    shape = ArmyristPanelShape,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("실시 완료 취소")
                                }
                            }
                        }
                    }
                }
            }
        }

        val sections: List<Pair<String, List<TimePlanActionItem>>> = if (view == TimePlanExecutionView.TIMELINE) {
            listOf("시간순" to plan.actions.sortedWith(compareBy<TimePlanActionItem> { it.scheduledDateTime }.thenBy { it.order }))
                .filter { it.second.isNotEmpty() }
        } else {
            buildList {
                plan.actionGroups.sortedBy { it.order }.forEach { group ->
                    val actions = plan.actions.filter { it.groupId == group.id }.sortedBy { it.scheduledDateTime }
                    if (actions.isNotEmpty()) add(group.name to actions)
                }
                val ungrouped = plan.actions.filter { it.groupId == null }.sortedBy { it.scheduledDateTime }
                if (ungrouped.isNotEmpty()) add("미지정" to ungrouped)
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
        LazyColumn(
            state = executionListState,
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    listTopInRoot = coordinates.positionInRoot().y
                    listBottomInRoot = listTopInRoot + coordinates.size.height
                }
                .pointerInput(plan.id, plan.actions, bulkSelect) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val railWidthPx = 52.dp.toPx()

                        // Only a gesture that STARTS in the left rail belongs to
                        // Completion Rail. Everything else remains normal scrolling.
                        if (bulkSelect || down.position.x > railWidthPx) {
                            return@awaitEachGesture
                        }

                        val startRootY = listTopInRoot + down.position.y
                        val startId = actionIdAt(startRootY)
                            ?: return@awaitEachGesture
                        val startAction = plan.actions.firstOrNull { it.id == startId }
                            ?: return@awaitEachGesture
                        val target =
                            if (startAction.completionState == ActionCompletionState.COMPLETE) {
                                ActionCompletionState.INCOMPLETE
                            } else {
                                ActionCompletionState.COMPLETE
                            }

                        down.consume()
                        dragPreviewStates.clear()
                        val visited = linkedSetOf<String>()

                        fun visit(rootY: Float) {
                            val id = actionIdAt(rootY) ?: return
                            if (visited.add(id)) {
                                dragPreviewStates[id] = target
                            }
                        }

                        visit(startRootY)

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break

                            change.consume()
                            visit(listTopInRoot + change.position.y)

                            val edge = 64.dp.toPx()
                            when {
                                change.position.y < edge ->
                                    dragScrollScope.launch { executionListState.scrollBy(-18.dp.toPx()) }
                                change.position.y > size.height - edge ->
                                    dragScrollScope.launch { executionListState.scrollBy(18.dp.toPx()) }
                            }
                        }

                        val updates = dragPreviewStates.toMap()
                        if (updates.isNotEmpty()) {
                            val now = System.currentTimeMillis().toString()
                            onCommit(
                                plan.copy(
                                    actions = plan.actions.map { action ->
                                        updates[action.id]?.let { state ->
                                            action.copy(
                                                completionState = state,
                                                updatedAt = now
                                            )
                                        } ?: action
                                    }
                                )
                            )
                        }
                        dragPreviewStates.clear()
                    }
                },
            contentPadding = PaddingValues(8.dp, 4.dp, 8.dp, 88.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            sections.forEach { (title, actions) ->
                item(key = "header-$title") {
                    Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
                }
                itemsIndexed(actions, key = { _, action -> action.id }) { actionIndex, action ->
                    val isFirstRailAction =
                        view == TimePlanExecutionView.TIMELINE && actionIndex == 0
                    val isLastRailAction =
                        view == TimePlanExecutionView.TIMELINE && actionIndex == actions.lastIndex
                    val completed =
                        (dragPreviewStates[action.id] ?: action.completionState) ==
                            ActionCompletionState.COMPLETE
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (bulkSelect) {
                                        selectedActionIds =
                                            if (action.id in selectedActionIds) {
                                                selectedActionIds - action.id
                                            } else {
                                                selectedActionIds + action.id
                                            }
                                    }
                                },
                                onLongClick = {
                                    if (!bulkSelect) editAction = action
                                }
                            )
                            .onGloballyPositioned { coordinates ->
                                val top = coordinates.positionInRoot().y
                                actionVerticalBounds[action.id] =
                                    top to (top + coordinates.size.height)
                            },
                        shape = ArmyristPanelShape,
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                bulkSelect && action.id in selectedActionIds ->
                                    ArmyristColors.SecondaryControl
                                else ->
                                    ArmyristColors.RaisedSurface
                            }
                        ),
                        border = BorderStroke(
                            1.dp,
                            when {
                                bulkSelect && action.id in selectedActionIds ->
                                    ArmyristColors.PrimaryControl
                                completed ->
                                    ArmyristColors.PrimaryControl.copy(alpha = 0.72f)
                                else ->
                                    ArmyristColors.Border
                            }
                        )
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .drawBehind {
                                    if (!bulkSelect && view == TimePlanExecutionView.TIMELINE) {
                                        // Row coordinates:
                                        // 10dp horizontal content padding + 22dp half of the
                                        // 44dp rail-control column = circle center at 32dp.
                                        val railX = 32.dp.toPx()
                                        val centerY = size.height / 2f
                                        val startY =
                                            if (isFirstRailAction) centerY else 0f
                                        val endY =
                                            if (isLastRailAction) centerY else size.height

                                        drawLine(
                                            color = ArmyristColors.PrimaryControl.copy(alpha = 0.52f),
                                            start = androidx.compose.ui.geometry.Offset(railX, startY),
                                            end = androidx.compose.ui.geometry.Offset(railX, endY),
                                            strokeWidth = 2.dp.toPx()
                                        )
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (bulkSelect) {
                                Checkbox(
                                    checked = action.id in selectedActionIds,
                                    onCheckedChange = {
                                        selectedActionIds = if (action.id in selectedActionIds) {
                                            selectedActionIds - action.id
                                        } else {
                                            selectedActionIds + action.id
                                        }
                                    }
                                )
                            } else {
                                CompletionRailControl(
                                    completed = completed
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(action.content, fontWeight = FontWeight.SemiBold)
                                Text(
                                    buildString {
                                        append(formatActionDateTime(plan, action.scheduledDateTime))
                                        if (view == TimePlanExecutionView.TIMELINE) {
                                            append(" · ")
                                            append(TimePlanExecutionRules.pointName(plan, action.parentPointId))
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ArmyristColors.SecondaryText
                                )
                                action.note?.takeIf { it.isNotBlank() }?.let { Text("비고: $it", style = MaterialTheme.typography.bodySmall) }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(if (completed) "완료" else "미실시", style = MaterialTheme.typography.labelMedium)
                                TextButton(
                                    onClick = { noteTarget = action },
                                    modifier = Modifier.defaultMinSize(minWidth = 42.dp, minHeight = 32.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) { Text("비고") }
                            }
                        }
                    }
                }
            }
        }
        }
    }

    editAction?.let { action ->
        ActionEditDialog(
            title = "실시사항 편집",
            initial = action,
            baseDateTime = action.scheduledDateTime,
            groups = plan.actionGroups,
            onDismiss = { editAction = null },
            onMove = {
                moveAction = action
                editAction = null
            },
            onDelete = {
                onCommit(TimePlanExecutionRules.batchDelete(plan, setOf(action.id)))
                editAction = null
            },
            onConfirm = { changed ->
                val candidate = plan.copy(
                    actions = plan.actions.map {
                        if (it.id == action.id) changed.copy(
                            id = action.id,
                            parentPointId = action.parentPointId,
                            order = action.order,
                            createdAt = action.createdAt,
                            updatedAt = System.currentTimeMillis().toString()
                        ) else it
                    }
                )
                if (!onCommit(candidate)) message = "실시사항을 저장하지 못했습니다."
                editAction = null
            }
        )
    }

    moveAction?.let { action ->
        ActionMoveDialog(
            plan = plan,
            action = action,
            onDismiss = { moveAction = null },
            onMove = { targetPointId ->
                val targetTime = TimePlanExecutionRules.pointDateTime(plan, targetPointId)
                val sourceTime = TimePlanExecutionRules.pointDateTime(plan, action.parentPointId)
                val shiftedTime =
                    if (targetTime != null && sourceTime != null) {
                        action.scheduledDateTime.plusMinutes(
                            java.time.Duration.between(sourceTime, targetTime).toMinutes()
                        )
                    } else action.scheduledDateTime
                val candidate = TimePlanExecutionRules.normalizeActionOrder(
                    plan.copy(actions = plan.actions.map {
                        if (it.id == action.id) it.copy(
                            parentPointId = targetPointId,
                            scheduledDateTime = shiftedTime,
                            updatedAt = System.currentTimeMillis().toString()
                        ) else it
                    })
                )
                if (!onCommit(candidate)) message = "실시사항을 이동하지 못했습니다."
                moveAction = null
            }
        )
    }

    message?.let {
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("확인") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("확인") } }
        )
    }

    noteTarget?.let { action ->
        var note by remember(action.id) { mutableStateOf(action.note.orEmpty()) }
        AlertDialog(
            onDismissRequest = { noteTarget = null },
            title = { Text("실시사항 비고") },
            text = { OutlinedTextField(note, { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text("비고 / 미실시 사유") }) },
            dismissButton = { TextButton(onClick = { noteTarget = null }) { Text("취소") } },
            confirmButton = {
                Button(onClick = {
                    onCommit(plan.copy(actions = plan.actions.map {
                        if (it.id == action.id) it.copy(note = note.trim().ifBlank { null }, updatedAt = System.currentTimeMillis().toString()) else it
                    }))
                    noteTarget = null
                }) { Text("저장") }
            }
        )
    }
}

@Composable
private fun CompletionRailControl(
    completed: Boolean
) {
    Box(
        modifier = Modifier
            .width(44.dp)
            .heightIn(min = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color =
                if (completed) ArmyristColors.PrimaryControl
                else ArmyristColors.RaisedSurface,
            border = BorderStroke(
                2.dp,
                ArmyristColors.PrimaryControl.copy(
                    alpha = if (completed) 1f else 0.62f
                )
            )
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (completed) {
                    Text(
                        "✓",
                        color = ArmyristColors.OnDark,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun ArmyristModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier.heightIn(min = 38.dp),
            shape = ArmyristPanelShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = ArmyristColors.SecondaryControl,
                contentColor = ArmyristColors.PrimaryText
            ),
            border = BorderStroke(1.dp, ArmyristColors.PrimaryControl),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(label, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 38.dp),
            shape = ArmyristPanelShape,
            border = BorderStroke(1.dp, ArmyristColors.Border),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = ArmyristColors.WorkSurface,
                contentColor = ArmyristColors.PrimaryText
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(label)
        }
    }
}

private val dateClock = DateTimeFormatter.ofPattern("MM.dd HH:mm")

private fun formatActionDateTime(
    plan: DateAwareTimePlan,
    value: LocalDateTime
): String {
    if (plan.dateDisplayMode != TimePlanDateDisplayMode.RELATIVE_D_DAY) {
        return value.format(dateClock)
    }

    // Relative mode is display-only. Keep the real LocalDateTime intact for
    // persistence, sorting, alarms, and calculations.
    val baseDate =
        DateTimePlanRules.resolvedStart(plan)?.toLocalDate()
            ?: plan.actions.minOfOrNull { it.scheduledDateTime.toLocalDate() }
            ?: value.toLocalDate()

    val offset =
        java.time.temporal.ChronoUnit.DAYS.between(baseDate, value.toLocalDate())

    val dayLabel =
        if (offset == 0L) {
            "D-Day"
        } else {
            "D${if (offset > 0) "+" else ""}$offset"
        }

    return "$dayLabel ${value.format(DateTimeFormatter.ofPattern("HH:mm"))}"
}

@Composable
private fun ActionEditDialog(
    title: String,
    initial: TimePlanActionItem?,
    baseDateTime: LocalDateTime,
    groups: List<TimePlanActionGroup>,
    onDismiss: () -> Unit,
    onMove: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onConfirm: (TimePlanActionItem) -> Unit
) {
    var content by remember(initial?.id) { mutableStateOf(initial?.content.orEmpty()) }
    var scheduledDateTime by remember(initial?.id) { mutableStateOf(initial?.scheduledDateTime ?: baseDateTime) }
    var editDateTime by remember { mutableStateOf(false) }
    var note by remember(initial?.id) { mutableStateOf(initial?.note.orEmpty()) }
    var groupId by remember(initial?.id) { mutableStateOf(initial?.groupId) }
    var notificationMode by remember(initial?.id) { mutableStateOf(initial?.notificationMode ?: ActionNotificationMode.NONE) }
    var notificationMenu by remember { mutableStateOf(false) }
    var groupMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        ),
        title = { Text(title) },
        containerColor = ArmyristColors.RaisedSurface,
        tonalElevation = 0.dp,
        titleContentColor = ArmyristColors.PrimaryText,
        textContentColor = ArmyristColors.PrimaryText,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(content, { content = it }, label = { Text("내용") }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(
                    onClick = { editDateTime = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ArmyristPanelShape
                ) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text("시간", style = MaterialTheme.typography.labelSmall, color = ArmyristColors.SecondaryText)
                        Text(
                            scheduledDateTime.format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                OutlinedTextField(note, { note = it }, label = { Text("비고") }, modifier = Modifier.fillMaxWidth())
                Box {
                    OutlinedButton(onClick = { groupMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(groups.firstOrNull { it.id == groupId }?.name ?: "그룹 미지정")
                    }
                    DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                        DropdownMenuItem(text = { Text("미지정") }, onClick = { groupId = null; groupMenu = false })
                        groups.sortedBy { it.order }.forEach { group ->
                            DropdownMenuItem(text = { Text(group.name) }, onClick = { groupId = group.id; groupMenu = false })
                        }
                    }
                }
                Box {
                    OutlinedButton(onClick = { notificationMenu = true }, modifier = Modifier.fillMaxWidth(), shape = ArmyristPanelShape) {
                        Text(when (notificationMode) { ActionNotificationMode.NONE -> "알림 없음"; ActionNotificationMode.SIMPLE -> "간단한 알림"; ActionNotificationMode.MUSIC -> "음악 알림" })
                    }
                    DropdownMenu(expanded = notificationMenu, onDismissRequest = { notificationMenu = false }) {
                        DropdownMenuItem(text = { Text("알림 없음") }, onClick = { notificationMode = ActionNotificationMode.NONE; notificationMenu = false })
                        DropdownMenuItem(text = { Text("간단한 알림") }, onClick = { notificationMode = ActionNotificationMode.SIMPLE; notificationMenu = false })
                        DropdownMenuItem(text = { Text("음악 알림") }, onClick = { notificationMode = ActionNotificationMode.MUSIC; notificationMenu = false })
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        dismissButton = {
            Row {
                onMove?.let { move -> TextButton(onClick = move) { Text("이동") } }
                onDelete?.let { delete -> TextButton(onClick = delete) { Text("삭제") } }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        },
        confirmButton = {
            Button(onClick = {
                when {
                    content.isBlank() -> error = "내용을 입력해주세요."
                    else -> onConfirm(
                        TimePlanActionItem(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            parentPointId = initial?.parentPointId ?: "TEMP",
                            content = content.trim(),
                            scheduledDateTime = scheduledDateTime,
                            completionState = initial?.completionState ?: ActionCompletionState.INCOMPLETE,
                            notificationEnabled = notificationMode != ActionNotificationMode.NONE,
                            notificationMode = notificationMode,
                            groupId = groupId,
                            note = note.trim().ifBlank { null },
                            order = initial?.order ?: 0,
                            createdAt = initial?.createdAt ?: System.currentTimeMillis().toString(),
                            updatedAt = System.currentTimeMillis().toString()
                        )
                    )
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = ArmyristColors.PrimaryControl)) { Text("확인") }
        }
    )

    if (editDateTime) {
        ArmyristActionDateTimeEditor(
            title = "실시사항 시간",
            initial = scheduledDateTime,
            onDismiss = { editDateTime = false },
            onConfirm = {
                scheduledDateTime = it
                editDateTime = false
            }
        )
    }
}

@Composable
private fun ActionMoveDialog(
    plan: DateAwareTimePlan,
    action: TimePlanActionItem,
    onDismiss: () -> Unit,
    onMove: (String) -> Unit
) {
    val pointIds = DateTimePlanRules.nodeIds(plan)
    var target by remember(action.id) { mutableStateOf(action.parentPointId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
        title = { Text("실시사항 이동", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(pointIds, key = { it }) { pointId ->
                    val selected = pointId == target
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { target = pointId },
                        shape = ArmyristPanelShape,
                        color = if (selected) ArmyristColors.SecondaryControl else ArmyristColors.WorkSurface,
                        border = BorderStroke(
                            1.dp,
                            if (selected) ArmyristColors.PrimaryControl else ArmyristColors.Border
                        )
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected, onClick = { target = pointId })
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(TimePlanExecutionRules.pointName(plan, pointId), fontWeight = FontWeight.SemiBold)
                                TimePlanExecutionRules.pointDateTime(plan, pointId)?.let {
                                    Text(it.format(dateClock), style = MaterialTheme.typography.bodySmall, color = ArmyristColors.SecondaryText)
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
        confirmButton = {
            Button(
                enabled = target != action.parentPointId,
                onClick = { onMove(target) },
                colors = ButtonDefaults.buttonColors(containerColor = ArmyristColors.PrimaryControl)
            ) { Text("이동") }
        }
    )
}

@Composable
private fun BatchAddDialog(
    count: Int,
    groups: List<TimePlanActionGroup>,
    onDismiss: () -> Unit,
    onConfirm: (String, Long, String?, String?, Boolean) -> Unit
) {
    var content by remember { mutableStateOf("") }
    var offset by remember { mutableStateOf("10") }
    var note by remember { mutableStateOf("") }
    var groupId by remember { mutableStateOf<String?>(null) }
    var notify by remember { mutableStateOf(false) }
    var groupMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        ),
        title = { Text("실시사항 일괄 추가") },
        containerColor = ArmyristColors.RaisedSurface,
        tonalElevation = 0.dp,
        titleContentColor = ArmyristColors.PrimaryText,
        textContentColor = ArmyristColors.PrimaryText,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("선택 지점 ${count}개")
                OutlinedTextField(content, { content = it }, label = { Text("내용") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(offset, { offset = it }, label = { Text("Point 기준 +분") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("비고") }, modifier = Modifier.fillMaxWidth())
                Box {
                    OutlinedButton(onClick = { groupMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(groups.firstOrNull { it.id == groupId }?.name ?: "그룹 미지정") }
                    DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                        DropdownMenuItem(text = { Text("미지정") }, onClick = { groupId = null; groupMenu = false })
                        groups.sortedBy { it.order }.forEach { group -> DropdownMenuItem(text = { Text(group.name) }, onClick = { groupId = group.id; groupMenu = false }) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(notify, { notify = it }); Spacer(Modifier.width(8.dp)); Text("예정 알림") }
                Text("취소/뒤로가기를 누르면 아직 저장되지 않습니다.", style = MaterialTheme.typography.bodySmall, color = ArmyristColors.SecondaryText)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
        confirmButton = {
            Button(
                enabled = content.isNotBlank() && offset.toLongOrNull() != null,
                onClick = { onConfirm(content.trim(), offset.toLong(), note.trim().ifBlank { null }, groupId, notify) },
                colors = ButtonDefaults.buttonColors(containerColor = ArmyristColors.PrimaryControl)
            ) { Text("적용") }
        }
    )
}

@Composable
private fun NumberInputDialog(
    title: String,
    description: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column { Text(description); Spacer(Modifier.height(8.dp)); OutlinedTextField(value, { value = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
        confirmButton = { Button(enabled = value.toLongOrNull() != null, onClick = { onConfirm(value.toLong()) }) { Text("적용") } }
    )
}

@Composable
private fun GroupPickerDialog(
    title: String,
    groups: List<TimePlanActionGroup>,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = ArmyristPanelShape,
        containerColor = ArmyristColors.WorkSurface,
        title = {
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    "지정할 그룹을 선택하세요.",
                    style = MaterialTheme.typography.labelSmall,
                    color = ArmyristColors.SecondaryText
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    Surface(
                        color = ArmyristColors.RaisedSurface,
                        shape = ArmyristPanelShape,
                        border = BorderStroke(1.dp, ArmyristColors.Border),
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(null) }
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(16.dp).background(ArmyristColors.Divider, CircleShape))
                            Spacer(Modifier.width(10.dp))
                            Text("미지정", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                items(groups.sortedBy { it.order }, key = { it.id }) { group ->
                    val color = actionGroupColor(group.color)
                    Surface(
                        color = color.copy(alpha = 0.13f),
                        shape = ArmyristPanelShape,
                        border = BorderStroke(1.dp, color.copy(alpha = 0.72f)),
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(group.id) }
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(16.dp).background(color, CircleShape))
                            Spacer(Modifier.width(10.dp))
                            Text(group.name, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
private fun ActionGroupManagerDialog(
    plan: DateAwareTimePlan,
    onDismiss: () -> Unit,
    onCommit: (DateAwareTimePlan) -> Boolean
) {
    var editTarget by remember { mutableStateOf<TimePlanActionGroup?>(null) }
    var create by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<TimePlanActionGroup?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = ArmyristPanelShape,
        containerColor = ArmyristColors.WorkSurface,
        title = { Text("실시사항 그룹 관리", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (plan.actionGroups.isEmpty()) {
                    Text(
                        "그룹이 없습니다.",
                        color = ArmyristColors.SecondaryText,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        items(plan.actionGroups.sortedBy { it.order }, key = { it.id }) { group ->
                            val color = actionGroupColor(group.color)
                            Surface(
                                color = color.copy(alpha = 0.12f),
                                shape = ArmyristPanelShape,
                                border = BorderStroke(1.dp, color.copy(alpha = 0.65f))
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(Modifier.size(18.dp).background(color, CircleShape))
                                    Spacer(Modifier.width(9.dp))
                                    Text(group.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { editTarget = group }) { Text("편집") }
                                    TextButton(onClick = { deleteTarget = group }) { Text("삭제") }
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
                ) { Text("+ 그룹 추가") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )

    if (create) {
        ActionGroupEditDialog(
            title = "그룹 추가",
            initialName = "",
            initialColor = actionGroupPalette[plan.actionGroups.size % actionGroupPalette.size],
            onDismiss = { create = false },
            onConfirm = { name, color ->
                onCommit(
                    plan.copy(
                        actionGroups = plan.actionGroups + TimePlanActionGroup(
                            name = name,
                            order = plan.actionGroups.size,
                            color = color
                        )
                    )
                )
                create = false
            }
        )
    }

    editTarget?.let { group ->
        ActionGroupEditDialog(
            title = "그룹 편집",
            initialName = group.name,
            initialColor = group.color,
            onDismiss = { editTarget = null },
            onConfirm = { name, color ->
                onCommit(
                    plan.copy(
                        actionGroups = plan.actionGroups.map {
                            if (it.id == group.id) it.copy(name = name, color = color) else it
                        }
                    )
                )
                editTarget = null
            }
        )
    }

    deleteTarget?.let { group ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = ArmyristColors.WorkSurface,
            title = { Text("그룹 삭제") },
            text = { Text("'${group.name}' 그룹을 삭제할까요? 연결된 실시사항은 미지정으로 변경됩니다.") },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("취소") } },
            confirmButton = {
                Button(
                    onClick = {
                        onCommit(
                            plan.copy(
                                actionGroups = plan.actionGroups
                                    .filterNot { it.id == group.id }
                                    .mapIndexed { index, g -> g.copy(order = index) },
                                actions = plan.actions.map {
                                    if (it.groupId == group.id) {
                                        it.copy(groupId = null, updatedAt = System.currentTimeMillis().toString())
                                    } else it
                                }
                            )
                        )
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ArmyristColors.PrimaryControl)
                ) { Text("삭제") }
            }
        )
    }
}

@Composable
private fun ActionGroupEditDialog(
    title: String,
    initialName: String,
    initialColor: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var color by remember(initialColor) { mutableStateOf(initialColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = ArmyristPanelShape,
        containerColor = ArmyristColors.WorkSurface,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("그룹명") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text("색상", fontWeight = FontWeight.SemiBold)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    actionGroupPalette.forEach { option ->
                        val selected = option.equals(color, ignoreCase = true)
                        Surface(
                            modifier = Modifier
                                .size(if (selected) 38.dp else 34.dp)
                                .clickable { color = option },
                            shape = CircleShape,
                            color = actionGroupColor(option),
                            tonalElevation = if (selected) 6.dp else 0.dp
                        ) {
                            if (selected) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("✓", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim(), color) },
                colors = ButtonDefaults.buttonColors(containerColor = ArmyristColors.PrimaryControl)
            ) { Text("확인") }
        }
    )
}

private val actionGroupPalette = listOf(
    "#7356B6",
    "#17845D",
    "#B64A3C",
    "#1678B8",
    "#98596F",
    "#8B7A00",
    "#7B4F9D",
    "#4D758C"
)

private fun actionGroupColor(value: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(value)) }
        .getOrDefault(Color(0xFF7A7D61))

