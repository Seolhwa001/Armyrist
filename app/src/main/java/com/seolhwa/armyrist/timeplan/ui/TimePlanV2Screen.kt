package com.seolhwa.armyrist.timeplan.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.sp
import android.view.HapticFeedbackConstants
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.seolhwa.armyrist.*
import com.seolhwa.armyrist.timeplan.data.TimePlanV2Repository
import com.seolhwa.armyrist.stage2.data.CoreSuiteRepository
import com.seolhwa.armyrist.stage2.domain.ToolResult
import com.seolhwa.armyrist.timeplan.domain.*
import java.util.UUID

@Composable
fun TimePlanV2App(
    repository: TimePlanV2Repository,
    coreRepository: CoreSuiteRepository,
    onHome: () -> Unit
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var sharingId by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val observed = revision

    val sharing = sharingId?.let(repository::getPlan)
    if (sharing != null) {
        CommonShareScreen(
            repo = coreRepository,
            result = generateTimePlanResult(sharing),
            onBack = { sharingId = null }
        )
        return
    }

    val selected = selectedId?.let(repository::getPlan)

    if (selected == null) {
        TimePlanV2List(
            plans = repository.getPlans(),
            onHome = onHome,
            onOpen = { selectedId = it },
            onCreate = {
                repository.createPlan()?.let {
                    revision++
                    selectedId = it.id
                }
            }
        )
    } else {
        TimePlanV2Detail(
            plan = selected,
            onHome = onHome,
            onBack = { selectedId = null },
            onResult = { sharingId = selected.id },
            onCommit = {
                if (repository.commit(it.copy(updatedAt = System.currentTimeMillis().toString()))) revision++
            }
        )
    }
}

@Composable
private fun TimePlanV2List(
    plans: List<RevisedTimePlan>,
    onHome: () -> Unit,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit
) {
    Scaffold(topBar = {
        ArmyristTopBar("시간계획", "TIME PLAN · V2", "홈", onHome)
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Button(
                onClick = onCreate,
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                shape = ArmyristPanelShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ArmyristColors.PrimaryControl,
                    contentColor = ArmyristColors.OnDark
                )
            ) { Text("+ 새 시간계획", fontWeight = FontWeight.Bold) }

            if (plans.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("저장된 시간계획이 없습니다.", color = ArmyristColors.SecondaryText)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(10.dp, 0.dp, 10.dp, 24.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(plans, key = { it.id }) { plan ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onOpen(plan.id) },
                            shape = ArmyristPanelShape,
                            colors = CardDefaults.cardColors(containerColor = ArmyristColors.RaisedSurface),
                            border = BorderStroke(1.dp, ArmyristColors.Border)
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(plan.title, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${plan.midwayEvents.size}개 중도지점" +
                                            if (plan.finalPoint != null) " · 종료지점 있음" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ArmyristColors.SecondaryText
                                    )
                                }
                                Text("›", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimePlanV2Detail(
    plan: RevisedTimePlan,
    onHome: () -> Unit,
    onBack: () -> Unit,
    onResult: () -> Unit,
    onCommit: (RevisedTimePlan) -> Unit
) {
    var titleEdit by remember { mutableStateOf(false) }
    var memoEdit by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<TimeEvent?>(null) }
    var editingLink by remember { mutableStateOf<Pair<String, String>?>(null) }
    var editingStart by remember { mutableStateOf(false) }
    var editingEnd by remember { mutableStateOf(false) }

    val ordered = plan.midwayEvents.sortedBy { it.order }
    val nodes = buildList {
        add(TimePlanConflictEngine.START_ID)
        addAll(ordered.map { it.id })
        plan.finalPoint?.let { add(it.id) }
        add(TimePlanConflictEngine.END_ID)
    }

    fun linkFor(from: String, to: String): TimeLink? =
        plan.links.firstOrNull { it.fromNodeId == from && it.toNodeId == to }

    fun rebuildLinks(changed: RevisedTimePlan): RevisedTimePlan =
        TimePlanCandidateEngine.normalizeTopology(changed)

    Scaffold(
        topBar = {
            ArmyristTopBar(
                title = plan.title,
                subtitle = "TIME PLAN · V2 · AUTO SAVE",
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
                    border = BorderStroke(1.dp, ArmyristColors.PrimaryControl),
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

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp, 0.dp, 8.dp, 24.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
            item {
                PointCard(
                    label = "시작",
                    timeText = clockText(plan.start.value),
                    emphasized = true,
                    onClick = { editingStart = true }
                ) {
                    Text("편집", style = MaterialTheme.typography.labelMedium, color = ArmyristColors.PrimaryControl)
                }
            }

            nodes.zipWithNext().forEachIndexed { index, pair ->
                val from = pair.first
                val to = pair.second
                val link = linkFor(from, to)
                item(key = "link-$from-$to") {
                    ElapsedConnector(
                        link = link,
                        onClick = { editingLink = from to to }
                    )
                }

                when {
                    to == TimePlanConflictEngine.END_ID -> {
                        item(key = "end") {
                            PointCard(
                                label = "종료",
                                timeText = clockText(plan.end.value),
                                emphasized = true,
                                onClick = { editingEnd = true }
                            ) {
                                Text("편집", style = MaterialTheme.typography.labelMedium, color = ArmyristColors.PrimaryControl)
                            }
                        }
                    }
                    plan.finalPoint?.id == to -> {
                        val event = plan.finalPoint!!
                        item(key = event.id) {
                            EventPointCard(
                                label = "종료지점",
                                event = event,
                                onClick = { editingEvent = event }
                            )
                        }
                    }
                    else -> {
                        val event = ordered.firstOrNull { it.id == to }
                        if (event != null) {
                            item(key = event.id) {
                                EventPointCard(
                                    label = "중도지점 ${ordered.indexOfFirst { it.id == event.id } + 1}",
                                    event = event,
                                    onClick = { editingEvent = event }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            val nextOrder = (plan.midwayEvents.maxOfOrNull { it.order } ?: -1) + 1
                            val event = TimeEvent(
                                id = UUID.randomUUID().toString(),
                                kind = TimeEventKind.MIDWAY,
                                order = nextOrder,
                                name = "중도 ${plan.midwayEvents.size + 1}"
                            )
                            onCommit(rebuildLinks(plan.copy(midwayEvents = plan.midwayEvents + event)))
                        },
                        modifier = Modifier.weight(1f),
                        shape = ArmyristPanelShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ArmyristColors.PrimaryControl,
                            contentColor = ArmyristColors.OnDark
                        )
                    ) { Text("+ 중도지점") }

                    OutlinedButton(
                        onClick = {
                            onCommit(
                                TimePlanCandidateEngine.appendFinalPoint(
                                    plan = plan,
                                    newFinalId = UUID.randomUUID().toString()
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = ArmyristPanelShape
                    ) { Text("+ 종료지점") }
                }
            }

            item {
                val span = resolvedPlanSpan(plan)
                CompactSummary(
                    span = span,
                    midwayCount = plan.midwayEvents.size,
                    endText = clockText(plan.end.value)
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { memoEdit = true },
                    shape = ArmyristPanelShape,
                    color = ArmyristColors.WorkSurface,
                    border = BorderStroke(1.dp, ArmyristColors.Border)
                ) {
                    Column(Modifier.padding(11.dp)) {
                        Text("메모", fontWeight = FontWeight.Bold, color = ArmyristColors.PrimaryControl)
                        Text(
                            plan.memo?.takeIf { it.isNotBlank() } ?: "메모가 없습니다. 눌러서 입력하세요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (plan.memo.isNullOrBlank()) ArmyristColors.SecondaryText else ArmyristColors.PrimaryText
                        )
                    }
                }
            }
        }
        }
    }

    if (editingStart) {
        Armyrist24HourTimeDialog(
            initial = plan.start.value.time,
            onDismiss = { editingStart = false },
            onConfirm = { clock ->
                val candidate = TimePlanCandidateEngine.create(
                    plan,
                    TimePlanCandidateEngine.EditIntent.SetStart(ClockValue.explicit(clock))
                )
                if (candidate.conflicts.isEmpty()) onCommit(candidate.proposed)
                editingStart = false
            }
        )
    }

    if (editingEnd) {
        Armyrist24HourTimeDialog(
            initial = plan.end.value.time,
            onDismiss = { editingEnd = false },
            onConfirm = { clock ->
                val candidate = TimePlanCandidateEngine.create(
                    plan,
                    TimePlanCandidateEngine.EditIntent.SetEnd(ClockValue.explicit(clock))
                )
                if (candidate.conflicts.isEmpty()) onCommit(candidate.proposed)
                editingEnd = false
            }
        )
    }

    if (titleEdit) {
        TextEditDialog(
            title = "제목 수정",
            initial = plan.title,
            singleLine = true,
            onDismiss = { titleEdit = false },
            onConfirm = {
                val clean = it.trim()
                if (clean.isNotEmpty()) onCommit(plan.copy(title = clean))
                titleEdit = false
            }
        )
    }

    if (memoEdit) {
        TextEditDialog(
            title = "메모",
            initial = plan.memo.orEmpty(),
            singleLine = false,
            onDismiss = { memoEdit = false },
            onConfirm = {
                onCommit(plan.copy(memo = it.trim().ifEmpty { null }))
                memoEdit = false
            }
        )
    }

    editingEvent?.let { event ->
        EventEditDialog(
            event = event,
            onDismiss = { editingEvent = null },
            onDelete = {
                val changed =
                    if (event.kind == TimeEventKind.FINAL) plan.copy(finalPoint = null)
                    else plan.copy(midwayEvents = plan.midwayEvents.filterNot { it.id == event.id })
                onCommit(rebuildLinks(changed))
                editingEvent = null
            },
            onConfirm = { changed ->
                val next =
                    if (changed.kind == TimeEventKind.FINAL) plan.copy(finalPoint = changed)
                    else plan.copy(midwayEvents = plan.midwayEvents.map { if (it.id == changed.id) changed else it })
                val candidate = TimePlanCandidateEngine.create(
                    next,
                    TimePlanCandidateEngine.EditIntent.SetEventTime(changed.id, changed.timeSpec)
                )
                if (candidate.conflicts.isEmpty()) onCommit(candidate.proposed)
                editingEvent = null
            }
        )
    }

    editingLink?.let { (from, to) ->
        DurationEditDialog(
            initial = linkFor(from, to)?.duration?.minutes,
            onDismiss = { editingLink = null },
            onConfirm = { minutes ->
                val candidate = TimePlanCandidateEngine.create(
                    plan,
                    TimePlanCandidateEngine.EditIntent.SetLinkDuration(
                        fromNodeId = from,
                        toNodeId = to,
                        duration = minutes?.let(TimeDuration::requireMinutes)
                    )
                )
                if (candidate.conflicts.isEmpty()) onCommit(candidate.proposed)
                editingLink = null
            }
        )
    }
}

@Composable
private fun PointCard(
    label: String,
    timeText: String,
    emphasized: Boolean,
    onClick: () -> Unit,
    editor: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = ArmyristPanelShape,
        color = if (emphasized) ArmyristColors.PrimaryControl.copy(alpha = 0.08f) else ArmyristColors.WorkSurface,
        border = BorderStroke(if (emphasized) 2.dp else 1.dp, ArmyristColors.PrimaryControl)
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.width(82.dp)) {
                Text(label, fontWeight = FontWeight.Bold, color = ArmyristColors.PrimaryControl)
            }
            Text(
                timeText,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Box { editor() }
        }
    }
}

@Composable
private fun AnchorTimeEditor(clock: ClockValue, onClock: (ClockTime) -> Unit) {
    var show by remember { mutableStateOf(false) }
    TextButton(onClick = { show = true }) { Text("편집") }
    if (show) {
        Armyrist24HourTimeDialog(
            initial = clock.time,
            onDismiss = { show = false },
            onConfirm = { show = false; onClock(it) }
        )
    }
}

@Composable
private fun EventPointCard(label: String, event: TimeEvent, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = ArmyristPanelShape,
        color = ArmyristColors.WorkSurface,
        border = BorderStroke(1.dp, ArmyristColors.Border)
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.width(92.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = ArmyristColors.SecondaryText)
                Text(eventTimeText(event.timeSpec), fontWeight = FontWeight.Bold)
            }
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    event.name,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.weight(0.42f)
                )
                event.note?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = ArmyristColors.SecondaryText,
                        maxLines = 1,
                        modifier = Modifier.weight(0.58f)
                    )
                }
            }
            Text("편집", style = MaterialTheme.typography.labelMedium, color = ArmyristColors.PrimaryControl)
        }
    }
}

@Composable
private fun ElapsedConnector(link: TimeLink?, onClick: () -> Unit) {
    val minutes = link?.duration?.minutes
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.heightIn(min = 40.dp),
            shape = ArmyristPanelShape,
            border = BorderStroke(1.dp, ArmyristColors.Border),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = ArmyristColors.AppBackground,
                contentColor = ArmyristColors.PrimaryControl
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)
        ) {
            Text(
                if (minutes == null) "+ 경과시간 입력" else "경과 ${durationText(minutes)}  ▼",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CompactSummary(span: String, midwayCount: Int, endText: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ArmyristPanelShape,
        color = ArmyristColors.WorkSurface,
        border = BorderStroke(1.dp, ArmyristColors.Border)
    ) {
        Row(
            Modifier.padding(9.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryCell("전체시간", span)
            SummaryCell("중도지점", "${midwayCount}개")
            SummaryCell("종료", endText)
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = ArmyristColors.SecondaryText)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EventEditDialog(
    event: TimeEvent,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onConfirm: (TimeEvent) -> Unit
) {
    var name by remember(event.id) { mutableStateOf(event.name) }
    var note by remember(event.id) { mutableStateOf(event.note.orEmpty()) }
    var range by remember(event.id) { mutableStateOf(event.timeSpec is EventTimeSpec.Range) }
    var single by remember(event.id) {
        mutableStateOf((event.timeSpec as? EventTimeSpec.Single)?.value ?: ClockValue.unset())
    }
    var rangeStart by remember(event.id) {
        mutableStateOf((event.timeSpec as? EventTimeSpec.Range)?.start ?: ClockValue.unset())
    }
    var rangeEnd by remember(event.id) {
        mutableStateOf((event.timeSpec as? EventTimeSpec.Range)?.end ?: ClockValue.unset())
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = ArmyristPanelShape,
            color = ArmyristColors.WorkSurface,
            border = BorderStroke(1.dp, ArmyristColors.Border)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (event.kind == TimeEventKind.FINAL) "종료지점 편집" else "중도지점 편집", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("지점명") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("시간 범위 사용", modifier = Modifier.weight(1f))
                    Switch(checked = range, onCheckedChange = { range = it })
                }
                if (range) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.weight(1f)) {
                            TimeEditButton("시작", rangeStart) { rangeStart = ClockValue.explicit(it) }
                        }
                        Box(Modifier.weight(1f)) {
                            TimeEditButton("종료", rangeEnd) { rangeEnd = ClockValue.explicit(it) }
                        }
                    }
                } else {
                    TimeEditButton("시각", single) { single = ClockValue.explicit(it) }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("비고") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f), shape = ArmyristPanelShape) {
                        Text("삭제")
                    }
                    Button(
                        onClick = {
                            val clean = name.trim()
                            if (clean.isNotEmpty()) {
                                val spec = if (range) EventTimeSpec.Range(rangeStart, rangeEnd)
                                else EventTimeSpec.Single(single)
                                onConfirm(event.copy(name = clean, timeSpec = spec, note = note.trim().ifEmpty { null }))
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = ArmyristPanelShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ArmyristColors.PrimaryControl,
                            contentColor = ArmyristColors.OnDark
                        )
                    ) { Text("확인") }
                }
            }
        }
    }
}

@Composable
private fun DurationEditDialog(
    initial: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit
) {
    var raw by remember { mutableStateOf(initial?.toString().orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = ArmyristPanelShape,
            color = ArmyristColors.WorkSurface,
            border = BorderStroke(1.dp, ArmyristColors.Border)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("경과시간", fontWeight = FontWeight.Bold)
                Text("분 단위로 입력합니다. 예: 40, 80, 120", style = MaterialTheme.typography.bodySmall, color = ArmyristColors.SecondaryText)
                OutlinedTextField(
                    value = raw,
                    onValueChange = { raw = it.filter(Char::isDigit); error = null },
                    label = { Text("분") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { onConfirm(null) },
                        modifier = Modifier.weight(1f),
                        shape = ArmyristPanelShape
                    ) { Text("비우기") }
                    Button(
                        onClick = {
                            val v = raw.toIntOrNull()
                            if (v == null || v < 0) error = "0 이상의 분 값을 입력하세요."
                            else onConfirm(v)
                        },
                        modifier = Modifier.weight(1f),
                        shape = ArmyristPanelShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ArmyristColors.PrimaryControl,
                            contentColor = ArmyristColors.OnDark
                        )
                    ) { Text("확인") }
                }
            }
        }
    }
}

@Composable
private fun TextEditDialog(
    title: String,
    initial: String,
    singleLine: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = ArmyristPanelShape,
            color = ArmyristColors.WorkSurface,
            border = BorderStroke(1.dp, ArmyristColors.Border)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = singleLine,
                    minLines = if (singleLine) 1 else 3
                )
                Button(
                    onClick = { onConfirm(value) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ArmyristPanelShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ArmyristColors.PrimaryControl,
                        contentColor = ArmyristColors.OnDark
                    )
                ) { Text("확인") }
            }
        }
    }
}

@Composable
private fun TimeEditButton(label: String, clock: ClockValue, onClock: (ClockTime) -> Unit) {
    var show by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { show = true },
        modifier = Modifier.fillMaxWidth(),
        shape = ArmyristPanelShape
    ) { Text("$label  ${clockText(clock)}") }
    if (show) {
        Armyrist24HourTimeDialog(
            initial = clock.time,
            onDismiss = { show = false },
            onConfirm = { show = false; onClock(it) }
        )
    }
}

@Composable
private fun Armyrist24HourTimeDialog(
    initial: ClockTime?,
    onDismiss: () -> Unit,
    onConfirm: (ClockTime) -> Unit
) {
    var selectedHour by remember { mutableIntStateOf(initial?.minuteOfDay?.div(60) ?: 9) }
    var selectedMinute by remember { mutableIntStateOf(initial?.minuteOfDay?.rem(60) ?: 0) }
    var raw by remember {
        mutableStateOf("%02d%02d".format(selectedHour, selectedMinute))
    }
    var error by remember { mutableStateOf<String?>(null) }

    // Direct-input values may be 1-minute precision.  The minute wheel only
    // has 5-minute detents, so it points to the nearest detent without
    // modifying selectedMinute until the user actually scrolls that wheel.
    val minuteDetents = remember { (0..55 step 5).toList() }
    var minuteWheelReference by remember {
        mutableIntStateOf(nearestFiveMinuteDetent(selectedMinute))
    }

    fun updateFromWheel(hour: Int = selectedHour, minute: Int = selectedMinute) {
        selectedHour = hour
        selectedMinute = minute
        raw = "%02d%02d".format(selectedHour, selectedMinute)
        error = null
    }

    fun parseRaw(value: String): Pair<Int, Int>? {
        val digits = value.filter(Char::isDigit)
        val parsed = when (digits.length) {
            3 -> digits.substring(0, 1).toIntOrNull()?.let { h ->
                digits.substring(1, 3).toIntOrNull()?.let { m -> h to m }
            }
            4 -> digits.substring(0, 2).toIntOrNull()?.let { h ->
                digits.substring(2, 4).toIntOrNull()?.let { m -> h to m }
            }
            else -> null
        }
        return parsed?.takeIf { it.first in 0..23 && it.second in 0..59 }
    }

    fun applyRaw(): Boolean {
        val parsed = parseRaw(raw)
        if (parsed == null) {
            error = "000~2359 범위의 올바른 시각을 입력하세요."
            return false
        }
        selectedHour = parsed.first
        selectedMinute = parsed.second
        minuteWheelReference = nearestFiveMinuteDetent(selectedMinute)
        raw = "%02d%02d".format(selectedHour, selectedMinute)
        error = null
        return true
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = ArmyristPanelShape,
            color = ArmyristColors.WorkSurface,
            border = BorderStroke(1.dp, ArmyristColors.Border)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "시간 입력",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "24시간제 · 휠 또는 직접 입력",
                    style = MaterialTheme.typography.bodySmall,
                    color = ArmyristColors.SecondaryText
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "시",
                            fontWeight = FontWeight.Bold,
                            color = ArmyristColors.PrimaryControl
                        )
                        Spacer(Modifier.height(4.dp))
                        ArmyristInfiniteWheelPicker(
                            values = (0..23).toList(),
                            selectedValue = selectedHour,
                            valueText = { "%02d".format(it) },
                            onUserSelected = { hour ->
                                updateFromWheel(hour = hour)
                            }
                        )
                    }

                    Text(
                        ":",
                        modifier = Modifier.padding(top = 72.dp),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = ArmyristColors.PrimaryText
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "분",
                            fontWeight = FontWeight.Bold,
                            color = ArmyristColors.PrimaryControl
                        )
                        Spacer(Modifier.height(4.dp))
                        ArmyristInfiniteWheelPicker(
                            values = minuteDetents,
                            selectedValue = minuteWheelReference,
                            valueText = { "%02d".format(it) },
                            onUserSelected = { minute ->
                                minuteWheelReference = minute
                                updateFromWheel(minute = minute)
                            }
                        )
                    }
                }

                Text(
                    "직접 입력",
                    fontWeight = FontWeight.Bold,
                    color = ArmyristColors.PrimaryControl
                )
                OutlinedTextField(
                    value = raw,
                    onValueChange = { incoming ->
                        raw = incoming.filter(Char::isDigit).take(4)
                        error = null

                        // Sync immediately as soon as the entered HHMM is valid.
                        // 1-minute precision is deliberately preserved.
                        parseRaw(raw)?.let { parsed ->
                            selectedHour = parsed.first
                            selectedMinute = parsed.second
                            minuteWheelReference = nearestFiveMinuteDetent(selectedMinute)
                        }
                    },
                    label = { Text("직접 입력 (850 / 0850 / 1737)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } }
                )

                Button(
                    onClick = {
                        if (applyRaw()) {
                            onConfirm(
                                ClockTime.requireMinuteOfDay(
                                    selectedHour * 60 + selectedMinute
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ArmyristPanelShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ArmyristColors.PrimaryControl,
                        contentColor = ArmyristColors.OnDark
                    )
                ) {
                    Text(
                        "확인 · %02d:%02d".format(selectedHour, selectedMinute),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun nearestFiveMinuteDetent(minute: Int): Int {
    val lower = (minute / 5) * 5
    val upper = (lower + 5).coerceAtMost(55)
    return if (minute - lower < upper - minute) lower else upper
}

@Composable
private fun ArmyristInfiniteWheelPicker(
    values: List<Int>,
    selectedValue: Int,
    valueText: (Int) -> String,
    onUserSelected: (Int) -> Unit
) {
    require(values.isNotEmpty())

    val view = LocalView.current
    val itemHeight = 48.dp
    val repetitionCount = 1000
    val middleCycle = repetitionCount / 2
    val selectedBaseIndex = values.indexOf(selectedValue).coerceAtLeast(0)
    val initialIndex = middleCycle * values.size + selectedBaseIndex

    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = state)

    var programmaticSync by remember { mutableStateOf(false) }
    var lastHapticIndex by remember { mutableIntStateOf(initialIndex) }

    // Direct input or the other wheel can move the reference value.
    // Re-center silently; do not reinterpret this as a user wheel action.
    LaunchedEffect(selectedValue) {
        val currentValue = values[
            ((state.firstVisibleItemIndex % values.size) + values.size) % values.size
        ]
        if (currentValue != selectedValue) {
            val current = state.firstVisibleItemIndex
            val currentCycle = current / values.size
            val target = currentCycle * values.size + selectedBaseIndex
            programmaticSync = true
            state.scrollToItem(target)
            lastHapticIndex = target
            programmaticSync = false
        }
    }

    // Value transition, not frame transition, drives selection and haptics.
    LaunchedEffect(state) {
        snapshotFlow {
            Triple(
                state.firstVisibleItemIndex,
                state.firstVisibleItemScrollOffset,
                state.isScrollInProgress
            )
        }.collect { (firstIndex, offset, scrolling) ->
            if (!scrolling || programmaticSync) return@collect

            // Whichever detent is closest to the center is treated as selected
            // while the user's finger/flick is moving.
            val itemPx = state.layoutInfo.visibleItemsInfo
                .firstOrNull()
                ?.size
                ?.coerceAtLeast(1)
                ?: return@collect
            val nearestIndex = if (offset >= itemPx / 2) firstIndex + 1 else firstIndex

            if (nearestIndex != lastHapticIndex) {
                val stepCount = kotlin.math.abs(nearestIndex - lastHapticIndex)
                repeat(stepCount.coerceAtMost(6)) {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                lastHapticIndex = nearestIndex

                val normalized = ((nearestIndex % values.size) + values.size) % values.size
                onUserSelected(values[normalized])
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(itemHeight * 3),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight),
            shape = ArmyristPanelShape,
            color = ArmyristColors.PrimaryControl,
            border = BorderStroke(1.dp, ArmyristColors.PrimaryControl)
        ) {}

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = itemHeight),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(repetitionCount * values.size) { index ->
                val value = values[index % values.size]
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    val isCentered = index == lastHapticIndex
                    Text(
                        valueText(value),
                        fontSize = if (isCentered) 22.sp else 18.sp,
                        fontWeight = if (isCentered) FontWeight.Bold else FontWeight.Medium,
                        color = if (isCentered) ArmyristColors.OnDark else ArmyristColors.SecondaryText
                    )
                }
            }
        }
    }
}

private fun generateTimePlanResult(plan: RevisedTimePlan): ToolResult {
    val orderedEvents = buildList {
        addAll(plan.midwayEvents.sortedBy { it.order })
        plan.finalPoint?.let { add(it) }
    }

    val lines = mutableListOf<String>()

    plan.start.value.time?.let {
        lines += "${formatClock(it)} 시작"
    } ?: run {
        lines += "시작"
    }

    fun appendLink(fromId: String, toId: String) {
        val duration = plan.links.firstOrNull {
            it.fromNodeId == fromId && it.toNodeId == toId
        }?.duration
        if (duration != null) {
            lines += "- ${formatDuration(duration.minutes)}"
        }
    }

    var previousId = TimePlanConflictEngine.START_ID

    orderedEvents.forEach { event ->
        appendLink(previousId, event.id)

        val time = formatEventTimeForResult(event.timeSpec)
        val heading = listOf(time, event.name)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        if (heading.isNotBlank()) {
            lines += heading
        }
        event.note?.takeIf { it.isNotBlank() }?.let {
            lines += it
        }
        previousId = event.id
    }

    appendLink(previousId, TimePlanConflictEngine.END_ID)

    plan.end.value.time?.let {
        lines += "${formatClock(it)} 종료"
    } ?: run {
        lines += "종료"
    }

    plan.memo?.takeIf { it.isNotBlank() }?.let {
        lines += ""
        lines += "[메모]"
        lines += it
    }

    return ToolResult(
        title = plan.title,
        body = lines.joinToString("\n")
    )
}

private fun formatEventTimeForResult(spec: EventTimeSpec): String = when (spec) {
    EventTimeSpec.Unspecified -> ""
    is EventTimeSpec.Single ->
        spec.value.time?.let(::formatClock).orEmpty()
    is EventTimeSpec.Range -> {
        val start = spec.start.time?.let(::formatClock)
        val end = spec.end.time?.let(::formatClock)
        when {
            start != null && end != null -> "$start ~ $end"
            start != null -> start
            end != null -> end
            else -> ""
        }
    }
}

private fun formatClock(clock: ClockTime): String =
    "%02d%02d".format(clock.minuteOfDay / 60, clock.minuteOfDay % 60)

private fun formatDuration(minutes: Int): String = when {
    minutes < 60 -> "${minutes}분"
    minutes % 60 == 0 -> "${minutes / 60}시간"
    else -> "${minutes / 60}시간 ${minutes % 60}분"
}

private fun clockText(value: ClockValue): String =
    value.time?.minuteOfDay?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "--:--"

private fun eventTimeText(spec: EventTimeSpec): String = when (spec) {
    EventTimeSpec.Unspecified -> "시각 미입력"
    is EventTimeSpec.Single -> clockText(spec.value)
    is EventTimeSpec.Range -> "${clockText(spec.start)} ~ ${clockText(spec.end)}"
}

private fun durationText(minutes: Int): String =
    if (minutes < 60) "${minutes}분"
    else if (minutes % 60 == 0) "${minutes / 60}시간"
    else "${minutes / 60}시간 ${minutes % 60}분"

private fun resolvedPlanSpan(plan: RevisedTimePlan): String {
    val start = plan.start.value.time?.minuteOfDay ?: return "미확정"
    val end = plan.end.value.time?.minuteOfDay ?: return "미확정"
    val diff = if (end >= start) end - start else (1440 - start) + end
    return durationText(diff)
}
