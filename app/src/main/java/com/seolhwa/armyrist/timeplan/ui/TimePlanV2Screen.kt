package com.seolhwa.armyrist.timeplan.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.seolhwa.armyrist.timeplan.domain.*
import java.util.UUID

@Composable
fun TimePlanV2App(repository: TimePlanV2Repository, onHome: () -> Unit) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val observed = revision
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
            onBack = { selectedId = null },
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
    onBack: () -> Unit,
    onCommit: (RevisedTimePlan) -> Unit
) {
    var titleEdit by remember { mutableStateOf(false) }
    var memoEdit by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<TimeEvent?>(null) }
    var editingLink by remember { mutableStateOf<Pair<String, String>?>(null) }

    val ordered = plan.midwayEvents.sortedBy { it.order }
    val nodes = buildList {
        add(TimePlanConflictEngine.START_ID)
        addAll(ordered.map { it.id })
        plan.finalPoint?.let { add(it.id) }
        add(TimePlanConflictEngine.END_ID)
    }

    fun linkFor(from: String, to: String): TimeLink? =
        plan.links.firstOrNull { it.fromNodeId == from && it.toNodeId == to }

    fun rebuildLinks(changed: RevisedTimePlan): RevisedTimePlan {
        val ids = buildList {
            add(TimePlanConflictEngine.START_ID)
            addAll(changed.midwayEvents.sortedBy { it.order }.map { it.id })
            changed.finalPoint?.let { add(it.id) }
            add(TimePlanConflictEngine.END_ID)
        }
        val old = changed.links.associateBy { it.fromNodeId to it.toNodeId }
        return changed.copy(
            links = ids.zipWithNext().map { (a, b) ->
                old[a to b] ?: TimeLink(a, b)
            }
        )
    }

    Scaffold(topBar = {
        ArmyristTopBar(plan.title, "TIME PLAN · V2 · AUTO SAVE", "목록", onBack)
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(10.dp, 8.dp, 10.dp, 28.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.weight(1f).clickable { titleEdit = true },
                        shape = ArmyristPanelShape,
                        color = ArmyristColors.WorkSurface,
                        border = BorderStroke(1.dp, ArmyristColors.Border)
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text("계획", style = MaterialTheme.typography.labelSmall, color = ArmyristColors.SecondaryText)
                            Text(plan.title, fontWeight = FontWeight.Bold)
                        }
                    }
                    OutlinedButton(
                        onClick = { /* Common Result pipeline is wired in the dedicated Result step. */ },
                        enabled = false,
                        shape = ArmyristPanelShape
                    ) { Text("결과 전달") }
                }
            }

            item {
                PointCard(
                    label = "시작",
                    timeText = clockText(plan.start.value),
                    emphasized = true,
                    onClick = { }
                ) {
                    AnchorTimeEditor(plan.start.value) {
                        onCommit(plan.copy(start = TimeAnchor(ClockValue.explicit(it))))
                    }
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
                                onClick = { }
                            ) {
                                AnchorTimeEditor(plan.end.value) {
                                    onCommit(plan.copy(end = TimeAnchor(ClockValue.explicit(it))))
                                }
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
                            if (plan.finalPoint == null) {
                                val event = TimeEvent(
                                    id = UUID.randomUUID().toString(),
                                    kind = TimeEventKind.FINAL,
                                    order = plan.midwayEvents.size,
                                    name = "종료지점"
                                )
                                onCommit(rebuildLinks(plan.copy(finalPoint = event)))
                            }
                        },
                        enabled = plan.finalPoint == null,
                        modifier = Modifier.weight(1f),
                        shape = ArmyristPanelShape
                    ) { Text(if (plan.finalPoint == null) "+ 종료지점" else "종료지점 있음") }
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
                onCommit(next)
                editingEvent = null
            }
        )
    }

    editingLink?.let { (from, to) ->
        DurationEditDialog(
            initial = linkFor(from, to)?.duration?.minutes,
            onDismiss = { editingLink = null },
            onConfirm = { minutes ->
                val replacement = TimeLink(
                    fromNodeId = from,
                    toNodeId = to,
                    duration = minutes?.let(TimeDuration::requireMinutes),
                    origin = if (minutes == null) ValueOrigin.UNSET else ValueOrigin.EXPLICIT
                )
                onCommit(
                    plan.copy(
                        links = plan.links.filterNot { it.fromNodeId == from && it.toNodeId == to } + replacement
                    )
                )
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
        modifier = Modifier.fillMaxWidth(),
        shape = ArmyristPanelShape,
        color = if (emphasized) ArmyristColors.RaisedSurface else ArmyristColors.WorkSurface,
        border = BorderStroke(1.dp, ArmyristColors.Border)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.width(90.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = ArmyristColors.SecondaryText)
                Text(eventTimeText(event.timeSpec), fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text(event.name, fontWeight = FontWeight.Bold)
                event.note?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = ArmyristColors.SecondaryText, maxLines = 2)
                }
            }
            Text("편집", style = MaterialTheme.typography.labelMedium, color = ArmyristColors.PrimaryControl)
        }
    }
}

@Composable
private fun ElapsedConnector(link: TimeLink?, onClick: () -> Unit) {
    val text = link?.duration?.minutes?.let(::durationText) ?: "경과 미입력"
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 38.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(34.dp))
        Text("│", color = ArmyristColors.Border)
        TextButton(onClick = onClick) {
            Text("경과 · $text", color = ArmyristColors.PrimaryControl, fontWeight = FontWeight.Bold)
        }
        Text("▼", color = ArmyristColors.Border)
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
    var hour by remember { mutableIntStateOf(initial?.minuteOfDay?.div(60) ?: 9) }
    var minute by remember { mutableIntStateOf(initial?.minuteOfDay?.rem(60) ?: 0) }
    var raw by remember { mutableStateOf("%02d%02d".format(hour, minute)) }
    var error by remember { mutableStateOf<String?>(null) }

    fun applyRaw(): Boolean {
        val digits = raw.filter(Char::isDigit)
        val parsed = when (digits.length) {
            3 -> digits.substring(0, 1).toIntOrNull()?.let { h ->
                digits.substring(1, 3).toIntOrNull()?.let { m -> h to m }
            }
            4 -> digits.substring(0, 2).toIntOrNull()?.let { h ->
                digits.substring(2, 4).toIntOrNull()?.let { m -> h to m }
            }
            else -> null
        }
        if (parsed == null || parsed.first !in 0..23 || parsed.second !in 0..59) {
            error = "0000~2359 범위의 시각을 입력하세요."
            return false
        }
        hour = parsed.first
        minute = parsed.second
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
                Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("시간 입력", fontWeight = FontWeight.Bold)
                Text("24시간제 · 선택과 직접 입력을 한 화면에서 사용합니다.", style = MaterialTheme.typography.bodySmall, color = ArmyristColors.SecondaryText)
                for (row in 0 until 4) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (col in 0 until 6) {
                            val h = row * 6 + col
                            FilterChip(
                                selected = hour == h,
                                onClick = { hour = h; raw = "%02d%02d".format(hour, minute); error = null },
                                label = { Text("%02d".format(h)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf(0, 10, 20, 30, 40, 50).forEach { m ->
                        FilterChip(
                            selected = minute == m,
                            onClick = { minute = m; raw = "%02d%02d".format(hour, minute); error = null },
                            label = { Text("%02d".format(m)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                OutlinedTextField(
                    value = raw,
                    onValueChange = { raw = it.filter(Char::isDigit).take(4); error = null },
                    label = { Text("직접 입력 (940 / 1710)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } }
                )
                Button(
                    onClick = {
                        if (applyRaw()) onConfirm(ClockTime.requireMinuteOfDay(hour * 60 + minute))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ArmyristPanelShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ArmyristColors.PrimaryControl,
                        contentColor = ArmyristColors.OnDark
                    )
                ) { Text("확인 · %02d:%02d".format(hour, minute), fontWeight = FontWeight.Bold) }
            }
        }
    }
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
