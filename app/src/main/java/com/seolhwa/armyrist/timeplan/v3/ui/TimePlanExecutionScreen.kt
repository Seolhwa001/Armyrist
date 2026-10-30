@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.seolhwa.armyrist.timeplan.v3.ui
import com.seolhwa.armyrist.ArmyristDialog

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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
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
    val notificationRevision by repository.notificationCompletionRevision.collectAsState()
    @Suppress("UNUSED_VARIABLE") val observed = revision to notificationRevision
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
                title = if (mode == TimePlanExecutionMode.EXECUTE) "실시사항 모드" else "실시사항 편집",
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
                    label = "실시사항 모드",
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
    var adding by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var groupsOpen by remember { mutableStateOf(false) }
    val context=LocalContext.current
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
    if(groupsOpen)ActionGroupManagerDialog(plan,{groupsOpen=false},onCommit)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("실시사항 시각은 일정과 독립적으로 유지됩니다.",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall)
            TextButton(onClick={groupsOpen=true}){Text("그룹")}
            TextButton(onClick={adding=true}){Text("+ 추가")}
        }
        if(error)Text("저장하지 못했습니다.",color=ArmyristColors.Danger)
        Box(Modifier.weight(1f)){TimePlanExecuteScreen(plan,onCommit)}
    }
    if(adding)ActionEditDialog(
        title="실시사항 추가",initial=null,
        baseDateTime=plan.start.value.value?:LocalDateTime.now(),groups=plan.actionGroups,
        onDismiss={adding=false},onConfirm={draft->
            if(draft.notificationMode!=ActionNotificationMode.NONE && Build.VERSION.SDK_INT>=33 && !TimePlanActionNotificationManager.notificationPermissionGranted(context))permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            if(onCommit(TimePlanExecutionRules.normalizeActionOrder(plan.copy(actions=plan.actions+draft.copy(parentPointId="UNASSIGNED",order=plan.actions.size)))))adding=false else error=true
        })
}

@Composable
private fun TimePlanExecuteScreen(
    plan: DateAwareTimePlan,
    onCommit: (DateAwareTimePlan) -> Boolean
) {
    val context=LocalContext.current
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
    var batchShiftOpen by remember { mutableStateOf(false) }
    var batchGroupOpen by remember { mutableStateOf(false) }
    var batchDeleteOpen by remember { mutableStateOf(false) }
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

        if(bulkSelect)Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
            TextButton(enabled=selectedActionIds.isNotEmpty(),onClick={batchShiftOpen=true}){Text("시각 이동")}
            TextButton(enabled=selectedActionIds.isNotEmpty(),onClick={batchGroupOpen=true}){Text("그룹 지정")}
            TextButton(enabled=selectedActionIds.isNotEmpty(),onClick={batchDeleteOpen=true}){Text("삭제")}
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
                                    content = action.content,
                                    completed = completed
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(action.content, fontWeight = FontWeight.SemiBold)
                                Text(
                                    buildString {
                                        append(formatActionDateTime(plan, action.scheduledDateTime))
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

    if(batchShiftOpen)NumberInputDialog("실시사항 시각 이동","선택한 실시사항만 이동할 분을 입력하세요. 음수도 가능합니다.","0",{batchShiftOpen=false}) { delta ->
        val candidate=runCatching{TimePlanExecutionRules.batchShift(plan,selectedActionIds.toSet(),delta)}.getOrNull()
        if(candidate==null || !onCommit(candidate))message="시각을 변경하지 못했습니다."
        batchShiftOpen=false
    }
    if(batchGroupOpen)GroupPickerDialog("그룹 지정",plan.actionGroups,{batchGroupOpen=false}) { group ->
        if(!onCommit(TimePlanExecutionRules.batchAssignGroup(plan,selectedActionIds.toSet(),group)))message="저장하지 못했습니다."
        batchGroupOpen=false
    }
    if(batchDeleteOpen)ArmyristDialog(onDismissRequest={batchDeleteOpen=false},title={Text("실시사항 삭제")},text={Text("선택한 ${selectedActionIds.size}개 실시사항을 삭제할까요? 일정은 유지됩니다.")},confirmButton={TextButton(onClick={
        if(onCommit(TimePlanExecutionRules.batchDelete(plan,selectedActionIds.toSet()))){selectedActionIds=emptyList();bulkSelect=false}else message="삭제하지 못했습니다."
        batchDeleteOpen=false
    }){Text("삭제")}},dismissButton={TextButton(onClick={batchDeleteOpen=false}){Text("취소")}})

    editAction?.let { action ->
        ActionEditDialog(
            title = "실시사항 편집",
            initial = action,
            baseDateTime = action.scheduledDateTime,
            groups = plan.actionGroups,
            onDismiss = { editAction = null },
            onDelete = {
                onCommit(TimePlanExecutionRules.batchDelete(plan, setOf(action.id)))
                editAction = null
            },
            onConfirm = { changed ->
                if(changed.notificationMode!=ActionNotificationMode.NONE && Build.VERSION.SDK_INT>=33 && !TimePlanActionNotificationManager.notificationPermissionGranted(context))permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                val candidate = plan.copy(
                    actions = plan.actions.map {
                        if (it.id == action.id) changed.copy(
                            completionState = it.completionState,
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


    message?.let {
        ArmyristDialog(
            onDismissRequest = { message = null },
            title = { Text("확인") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("확인") } }
        )
    }

    noteTarget?.let { action ->
        var note by remember(action.id) { mutableStateOf(action.note.orEmpty()) }
        ArmyristDialog(
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
    content: String,
    completed: Boolean
) {
    Box(
        modifier = Modifier
            .width(44.dp)
            .heightIn(min = 48.dp)
            .semantics {
                contentDescription = "$content 완료 상태"
                stateDescription = if (completed) "완료" else "미완료"
            },
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
        plan.start.value.value?.toLocalDate()
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
internal fun ActionEditDialog(
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

    ArmyristDialog(
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
                            markerColor = initial?.markerColor ?: "#111111",
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
private fun NumberInputDialog(
    title: String,
    description: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    ArmyristDialog(
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
    ArmyristDialog(
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
internal fun ActionGroupManagerDialog(
    plan: DateAwareTimePlan,
    onDismiss: () -> Unit,
    onCommit: (DateAwareTimePlan) -> Boolean
) {
    var editTarget by remember { mutableStateOf<TimePlanActionGroup?>(null) }
    var create by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<TimePlanActionGroup?>(null) }

    ArmyristDialog(
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
        ArmyristDialog(
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

    ArmyristDialog(
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

