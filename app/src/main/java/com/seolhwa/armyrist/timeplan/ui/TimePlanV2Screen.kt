package com.seolhwa.armyrist.timeplan.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
    var pendingBoundaryEdit by remember {
        mutableStateOf<TimeEvent?>(null)
    }
    var pendingEventConflict by remember {
        mutableStateOf<Pair<TimeEvent, List<TimePlanCandidateEngine.Conflict>>?>(null)
    }
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
                if (
                    TimePlanCandidateEngine.requiresEndBoundaryConfirmation(
                        existing = plan,
                        eventId = changed.id,
                        proposedSpec = changed.timeSpec
                    )
                ) {
                    pendingBoundaryEdit = changed
                } else {
                    val candidate = TimePlanCandidateEngine.createEventEdit(
                        existing = plan,
                        changedEvent = changed
                    )
                    if (candidate.conflicts.isEmpty()) {
                        onCommit(candidate.proposed)
                    } else {
                        pendingEventConflict = changed to candidate.conflicts
                    }
                }
                editingEvent = null
            }
        )
    }

    editingLink?.let { (from, to) ->
        val currentLink = linkFor(from, to)
        DurationEditDialog(
            initialMinutes = currentLink?.duration?.minutes,
            initialLabel = currentLink?.label.orEmpty(),
            onDismiss = { editingLink = null },
            onConfirm = { minutes, label ->
                val candidate = TimePlanCandidateEngine.create(
                    plan,
                    TimePlanCandidateEngine.EditIntent.SetLinkDuration(
                        fromNodeId = from,
                        toNodeId = to,
                        duration = minutes?.let(TimeDuration::requireMinutes),
                        label = label
                    )
                )
                if (candidate.conflicts.isEmpty()) onCommit(candidate.proposed)
                editingLink = null
            }
        )
    }

    pendingEventConflict?.let { (changedEvent, conflicts) ->
        val hasHardRangeError = conflicts.any {
            it.type == TimePlanCandidateEngine.ConflictType.RANGE_ORDER_INVALID
        }
        val needsPrefixReflow =
            TimePlanCandidateEngine.eventEditNeedsPrefixReflow(
                existing = plan,
                changedEvent = changedEvent
            )
        AlertDialog(
            onDismissRequest = { pendingEventConflict = null },
            title = {
                Text(
                    if (hasHardRangeError) "시간 범위 확인"
                    else "일정 충돌 확인"
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (hasHardRangeError) {
                            "입력한 시간 범위의 시작·종료 순서를 확인해 주세요."
                        } else {
                            "입력한 시각 또는 시간 범위가 앞뒤 일정과 겹치거나 현재 시간관계와 충돌합니다."
                        }
                    )
                    if (!hasHardRangeError) {
                        Text(
                            if (needsPrefixReflow) {
                                "시간범위 시작이 앞 일정과 겹칩니다. 앞 일정은 필요한 만큼 당기고, 이후 일정은 범위 종료 변화량만큼 이동합니다."
                            } else {
                                "입력값을 유지하면서 이 지점 이후의 일정을 함께 이동할 수 있습니다."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = ArmyristColors.SecondaryText
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingEventConflict = null }) {
                    Text("취소")
                }
            },
            confirmButton = {
                if (!hasHardRangeError) {
                    Button(
                        onClick = {
                            val adjusted =
                                TimePlanCandidateEngine.createEventEditWithTimelineReflow(
                                    existing = plan,
                                    changedEvent = changedEvent
                                )
                            if (adjusted.conflicts.isEmpty()) {
                                onCommit(adjusted.proposed)
                                pendingEventConflict = null
                            } else {
                                // Keep the dialog open and replace the conflict list.
                                // The user is never returned to the timeline as if save succeeded.
                                pendingEventConflict = changedEvent to adjusted.conflicts
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ArmyristColors.PrimaryControl,
                            contentColor = ArmyristColors.OnDark
                        )
                    ) {
                        Text(if (needsPrefixReflow) "앞·뒤 일정 조정" else "이후 일정 조정")
                    }
                }
            }
        )
    }

    pendingBoundaryEdit?.let { changedEvent ->
        val proposedClock = TimePlanCalculator.arrivalClock(changedEvent.timeSpec)?.time
        val endClock = plan.end.value.time
        AlertDialog(
            onDismissRequest = { pendingBoundaryEdit = null },
            title = { Text("시간 순서 확인") },
            text = {
                Text(
                    if (proposedClock != null && endClock != null) {
                        "입력한 중도 시각 ${formatClock(proposedClock)}이 현재 종료 시각 " +
                            "${formatClock(endClock)}보다 늦습니다. 이후 일정을 같은 만큼 이동하시겠습니까?"
                    } else {
                        "입력한 시각이 현재 종료 범위를 넘어갑니다. 이후 일정을 함께 이동하시겠습니까?"
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { pendingBoundaryEdit = null }) {
                    Text("취소")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val candidate =
                            TimePlanCandidateEngine.createEventEditWithTimelineReflow(
                                existing = plan,
                                changedEvent = changedEvent
                            )
                        if (candidate.conflicts.isEmpty()) {
                            onCommit(candidate.proposed)
                        } else {
                            pendingEventConflict = changedEvent to candidate.conflicts
                        }
                        pendingBoundaryEdit = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ArmyristColors.PrimaryControl,
                        contentColor = ArmyristColors.OnDark
                    )
                ) {
                    Text("이후 일정 조정")
                }
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
            Box(Modifier.width(38.dp), contentAlignment = Alignment.Center) {
                TimelinePointGlyph(isRange = false, emphasized = emphasized)
            }
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
            Box(Modifier.width(38.dp), contentAlignment = Alignment.Center) {
                TimelinePointGlyph(
                    isRange = event.timeSpec is EventTimeSpec.Range,
                    emphasized = event.kind == TimeEventKind.FINAL
                )
            }
        }
    }
}

@Composable
private fun TimelinePointGlyph(isRange: Boolean, emphasized: Boolean) {
    val color = ArmyristColors.PrimaryControl
    Canvas(Modifier.width(34.dp).height(if (isRange) 54.dp else 34.dp)) {
        val x = size.width / 2f
        val radius = if (emphasized) 6.dp.toPx() else 5.dp.toPx()
        if (isRange) {
            val topY = 10.dp.toPx()
            val bottomY = size.height - 10.dp.toPx()
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(x, topY + radius),
                end = androidx.compose.ui.geometry.Offset(x, bottomY - radius),
                strokeWidth = 2.dp.toPx()
            )
            drawCircle(color = color, radius = radius, center = androidx.compose.ui.geometry.Offset(x, topY))
            drawCircle(color = color, radius = radius, center = androidx.compose.ui.geometry.Offset(x, bottomY))
        } else {
            drawCircle(color = color, radius = radius, center = center)
        }
    }
}

@Composable
private fun TimelineConnectorGlyph() {
    Canvas(Modifier.width(34.dp).height(42.dp)) {
        val x = size.width / 2f
        drawLine(
            color = ArmyristColors.Border,
            start = androidx.compose.ui.geometry.Offset(x, 0f),
            end = androidx.compose.ui.geometry.Offset(x, size.height),
            strokeWidth = 2.dp.toPx()
        )
    }
}

@Composable
private fun ElapsedConnector(link: TimeLink?, onClick: () -> Unit) {
    val minutes = link?.duration?.minutes
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
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
                val displayLabel = link?.label?.takeIf { it.isNotBlank() } ?: "경과"
                Text(
                    if (minutes == null) {
                        if (displayLabel == "경과") "+ 경과시간 입력"
                        else "$displayLabel · 경과시간 입력  ▼"
                    } else {
                        "$displayLabel ${durationText(minutes)}  ▼"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Box(Modifier.width(38.dp), contentAlignment = Alignment.Center) {
            TimelineConnectorGlyph()
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
                    Switch(
                        checked = range,
                        onCheckedChange = { enabled ->
                            if (enabled && !range) {
                                val base = single.time
                                if (rangeStart.time == null && base != null) {
                                    rangeStart = ClockValue.explicit(base)
                                }
                                if (rangeEnd.time == null && base != null) {
                                    rangeEnd = ClockValue.explicit(base)
                                }
                            }
                            range = enabled
                        }
                    )
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
                    Text(
                        "범위 시작은 도착, 범위 종료는 다음 일정으로 출발하는 시각입니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ArmyristColors.SecondaryText
                    )
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
    initialMinutes: Int?,
    initialLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (Int?, String?) -> Unit
) {
    val safeInitial = (initialMinutes ?: 0).coerceIn(0, MINUTES_PER_DAY - 1)
    var selectedDurationMinutes by remember {
        mutableIntStateOf(safeInitial)
    }
    var raw by remember {
        mutableStateOf(initialMinutes?.toString().orEmpty())
    }
    var label by remember { mutableStateOf(initialLabel) }
    var error by remember { mutableStateOf<String?>(null) }

    val selectedHours = selectedDurationMinutes / 60
    val selectedMinutes = selectedDurationMinutes % 60
    val minuteWheelReference = nearestFiveMinuteDetent(selectedMinutes)

    fun setSelected(hours: Int, minutes: Int) {
        selectedDurationMinutes = (hours * 60 + minutes)
            .coerceIn(0, MINUTES_PER_DAY - 1)
        raw = selectedDurationMinutes.toString()
        error = null
    }

    fun parseRawDuration(value: String): Int? {
        val parsed = value.filter(Char::isDigit).toIntOrNull() ?: return null
        return parsed.takeIf { it in 0 until MINUTES_PER_DAY }
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
                    "경과시간",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "시간·분 휠 또는 총 분 직접 입력",
                    style = MaterialTheme.typography.bodySmall,
                    color = ArmyristColors.SecondaryText
                )

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("경과 명칭") },
                    placeholder = { Text("예: 이동, 교육, 정비") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.width(116.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "시간",
                            fontWeight = FontWeight.Bold,
                            color = ArmyristColors.PrimaryControl
                        )
                        Spacer(Modifier.height(2.dp))
                        ArmyristWheelPicker(
                            values = (0..23).toList(),
                            selectedValue = selectedHours,
                            valueText = { it.toString() },
                            onUserSelected = { hours ->
                                setSelected(hours, selectedMinutes)
                            }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(34.dp)
                            .height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            ":",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = ArmyristColors.PrimaryText
                        )
                    }

                    Column(
                        modifier = Modifier.width(116.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "분",
                            fontWeight = FontWeight.Bold,
                            color = ArmyristColors.PrimaryControl
                        )
                        Spacer(Modifier.height(2.dp))
                        ArmyristWheelPicker(
                            values = (0..55 step 5).toList(),
                            selectedValue = minuteWheelReference,
                            valueText = { "%02d".format(it) },
                            onUserSelected = { minutes ->
                                setSelected(selectedHours, minutes)
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
                        parseRawDuration(raw)?.let { exactMinutes ->
                            selectedDurationMinutes = exactMinutes
                        }
                    },
                    label = { Text("총 분 (예: 40 / 80 / 120)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = error != null,
                    supportingText = {
                        error?.let { Text(it) }
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onConfirm(
                                null,
                                label.trim().ifEmpty { null }
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = ArmyristPanelShape
                    ) {
                        Text("비우기")
                    }

                    Button(
                        onClick = {
                            val exact = parseRawDuration(raw)
                            if (exact == null) {
                                error = "0~1439분 범위의 값을 입력하세요."
                            } else {
                                selectedDurationMinutes = exact
                                onConfirm(
                                    exact,
                                    label.trim().ifEmpty { null }
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = ArmyristPanelShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ArmyristColors.PrimaryControl,
                            contentColor = ArmyristColors.OnDark
                        )
                    ) {
                        Text(
                            "확인 · ${durationText(selectedDurationMinutes)}",
                            fontWeight = FontWeight.Bold
                        )
                    }
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
    val initialMinuteOfDay = initial?.minuteOfDay ?: (9 * 60)
    var selectedMinuteOfDay by remember { mutableIntStateOf(initialMinuteOfDay) }
    var raw by remember {
        mutableStateOf(
            "%02d%02d".format(
                selectedMinuteOfDay / 60,
                selectedMinuteOfDay % 60
            )
        )
    }
    var error by remember { mutableStateOf<String?>(null) }

    val selectedHour = selectedMinuteOfDay / 60
    val selectedMinute = selectedMinuteOfDay % 60
    val minuteWheelReference = nearestFiveMinuteDetent(selectedMinute)

    fun setSelected(hour: Int, minute: Int) {
        selectedMinuteOfDay = hour * 60 + minute
        raw = "%02d%02d".format(hour, minute)
        error = null
    }

    fun parseRaw(value: String): Pair<Int, Int>? {
        val digits = value.filter(Char::isDigit)
        val parsed = when (digits.length) {
            3 -> {
                val h = digits.substring(0, 1).toIntOrNull()
                val m = digits.substring(1, 3).toIntOrNull()
                if (h != null && m != null) h to m else null
            }
            4 -> {
                val h = digits.substring(0, 2).toIntOrNull()
                val m = digits.substring(2, 4).toIntOrNull()
                if (h != null && m != null) h to m else null
            }
            else -> null
        }
        return parsed?.takeIf { it.first in 0..23 && it.second in 0..59 }
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.width(116.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("시", fontWeight = FontWeight.Bold, color = ArmyristColors.PrimaryControl)
                        Spacer(Modifier.height(2.dp))
                        ArmyristWheelPicker(
                            values = (0..23).toList(),
                            selectedValue = selectedHour,
                            valueText = { "%02d".format(it) },
                            onUserSelected = { hour ->
                                setSelected(hour, selectedMinute)
                            }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(34.dp)
                            .height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            ":",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = ArmyristColors.PrimaryText
                        )
                    }

                    Column(
                        modifier = Modifier.width(116.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("분", fontWeight = FontWeight.Bold, color = ArmyristColors.PrimaryControl)
                        Spacer(Modifier.height(2.dp))
                        ArmyristWheelPicker(
                            values = (0..55 step 5).toList(),
                            selectedValue = minuteWheelReference,
                            valueText = { "%02d".format(it) },
                            onUserSelected = { minute ->
                                setSelected(selectedHour, minute)
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
                        parseRaw(raw)?.let { (hour, minute) ->
                            selectedMinuteOfDay = hour * 60 + minute
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
                        val parsed = parseRaw(raw)
                        if (parsed == null) {
                            error = "000~2359 범위의 올바른 시각을 입력하세요."
                        } else {
                            selectedMinuteOfDay = parsed.first * 60 + parsed.second
                            onConfirm(ClockTime.requireMinuteOfDay(selectedMinuteOfDay))
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
    val rounded = ((minute + 2) / 5) * 5
    return when {
        rounded >= 60 -> 55
        else -> rounded.coerceIn(0, 55)
    }
}

@Composable
private fun ArmyristWheelPicker(
    values: List<Int>,
    selectedValue: Int,
    valueText: (Int) -> String,
    onUserSelected: (Int) -> Unit
) {
    require(values.isNotEmpty())

    val view = LocalView.current
    val itemHeight = 44.dp
    val centerPadding = itemHeight
    val cycles = 1000
    val middle = cycles / 2
    val selectedIndex = values.indexOf(selectedValue).coerceAtLeast(0)
    val initialCenteredIndex = middle * values.size + selectedIndex

    // With one item-height of top padding, centered item N is positioned by
    // firstVisibleItem = N - 1.
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = (initialCenteredIndex - 1).coerceAtLeast(0)
    )
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = state)

    var centeredIndex by remember { mutableIntStateOf(initialCenteredIndex) }
    var programmaticSync by remember { mutableStateOf(false) }

    // External changes (direct text input / the other control) may need to move
    // this wheel. Never recenter while THIS wheel is under the user's finger or
    // fling animation: doing so cancels/rebases the scroll and was the source of
    // the "white value / jumps to 23 or 08" bug.
    LaunchedEffect(selectedValue, state.isScrollInProgress) {
        if (state.isScrollInProgress) return@LaunchedEffect

        val currentNormalized =
            ((centeredIndex % values.size) + values.size) % values.size
        if (currentNormalized == selectedIndex) return@LaunchedEffect

        val currentCycle = centeredIndex / values.size
        var target = currentCycle * values.size + selectedIndex

        // Keep the programmatic move close to the current cycle.
        if (target - centeredIndex > values.size / 2) target -= values.size
        if (centeredIndex - target > values.size / 2) target += values.size

        target = target.coerceIn(1, cycles * values.size - 2)

        programmaticSync = true
        state.scrollToItem(target - 1)
        centeredIndex = target
        programmaticSync = false
    }

    LaunchedEffect(state) {
        snapshotFlow {
            val info = state.layoutInfo
            val viewportCenter =
                (info.viewportStartOffset + info.viewportEndOffset) / 2
            val centered = info.visibleItemsInfo.minByOrNull { item ->
                kotlin.math.abs(
                    (item.offset + item.size / 2) - viewportCenter
                )
            }
            Triple(centered?.index, state.isScrollInProgress, programmaticSync)
        }.collect { (newCenteredIndex, scrolling, syncing) ->
            if (newCenteredIndex == null) return@collect

            // Always keep visual selection attached to the actual center row.
            if (newCenteredIndex != centeredIndex) {
                centeredIndex = newCenteredIndex

                if (scrolling && !syncing) {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    val normalized =
                        ((newCenteredIndex % values.size) + values.size) % values.size
                    onUserSelected(values[normalized])
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .width(116.dp)
            .height(132.dp),
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
            contentPadding = PaddingValues(vertical = centerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(cycles * values.size) { index ->
                val value = values[index % values.size]
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    val isCentered = index == centeredIndex
                    Text(
                        valueText(value),
                        fontSize = if (isCentered) 21.sp else 17.sp,
                        fontWeight =
                            if (isCentered) FontWeight.Bold else FontWeight.Medium,
                        color =
                            if (isCentered) Color.White
                            else ArmyristColors.SecondaryText
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
        val link = plan.links.firstOrNull {
            it.fromNodeId == fromId && it.toNodeId == toId
        }
        val duration = link?.duration
        if (duration != null && duration.minutes > 0) {
            val label = link.label?.trim()?.takeIf { it.isNotEmpty() } ?: "경과"
            lines += "- $label ${formatDuration(duration.minutes)}"
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
