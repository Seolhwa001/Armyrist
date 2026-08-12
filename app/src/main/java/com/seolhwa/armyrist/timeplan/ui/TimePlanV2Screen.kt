package com.seolhwa.armyrist.timeplan.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.seolhwa.armyrist.*
import com.seolhwa.armyrist.timeplan.data.TimePlanV2Repository
import com.seolhwa.armyrist.timeplan.domain.*

@Composable
fun TimePlanV2App(repository: TimePlanV2Repository, onHome: () -> Unit) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val observed = revision
    val selected = selectedId?.let(repository::getPlan)

    if (selected == null) {
        TimePlanV2List(repository.getPlans(), onHome) { selectedId = it }
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
private fun TimePlanV2List(plans: List<RevisedTimePlan>, onHome: () -> Unit, onOpen: (String) -> Unit) {
    Scaffold(topBar = {
        ArmyristTopBar("시간계획", "TIME PLAN · CORE REVISION", "홈", onHome)
    }) { padding ->
        if (plans.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("기존 시간계획이 없습니다.")
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(plans, key = { it.id }) { plan ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(plan.id) },
                        shape = ArmyristPanelShape,
                        colors = CardDefaults.cardColors(containerColor = ArmyristColors.RaisedSurface),
                        border = BorderStroke(1.dp, ArmyristColors.Border)
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
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

@Composable
private fun TimePlanV2Detail(
    plan: RevisedTimePlan,
    onBack: () -> Unit,
    onCommit: (RevisedTimePlan) -> Unit
) {
    var title by remember(plan.id, plan.title) { mutableStateOf(plan.title) }
    var memo by remember(plan.id, plan.memo) { mutableStateOf(plan.memo.orEmpty()) }

    fun commitBasics() {
        val clean = title.trim()
        if (clean.isNotEmpty() && (clean != plan.title || memo.trim().ifEmpty { null } != plan.memo)) {
            onCommit(plan.copy(title = clean, memo = memo.trim().ifEmpty { null }))
        }
    }

    Scaffold(topBar = {
        ArmyristTopBar(plan.title, "TIME PLAN · V2 · AUTO SAVE", "목록") {
            commitBasics()
            onBack()
        }
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(10.dp, 8.dp, 10.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            item {
                CompactSection("계획") {
                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            title = it
                            if (it.trim().isNotEmpty()) onCommit(plan.copy(title = it.trim()))
                        },
                        label = { Text("제목") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = memo,
                        onValueChange = {
                            memo = it
                            onCommit(plan.copy(memo = it.trim().ifEmpty { null }))
                        },
                        label = { Text("메모 (선택)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 1,
                        maxLines = 2
                    )
                }
            }

            item {
                CompactSection("시간 범위") {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnchorTile(
                            modifier = Modifier.weight(1f),
                            label = "START",
                            clock = plan.start.value,
                            onClock = { onCommit(plan.copy(start = TimeAnchor(ClockValue.explicit(it)))) }
                        )
                        AnchorTile(
                            modifier = Modifier.weight(1f),
                            label = "END",
                            clock = plan.end.value,
                            onClock = { onCommit(plan.copy(end = TimeAnchor(ClockValue.explicit(it)))) }
                        )
                    }
                }
            }

            item {
                SectionHeader(
                    title = "중도지점",
                    count = plan.midwayEvents.size,
                    action = "+ 중도지점",
                    enabled = false
                )
            }
            if (plan.midwayEvents.isEmpty()) {
                item {
                    EmptyStrip("등록된 중도지점이 없습니다. · 추가 기능은 Step 6에서 연결됩니다.")
                }
            } else {
                plan.midwayEvents.sortedBy { it.order }.forEachIndexed { index, event ->
                    item(key = event.id) {
                        CompactEventRow(index + 1, event) { changed ->
                            onCommit(plan.copy(midwayEvents = plan.midwayEvents.map {
                                if (it.id == changed.id) changed else it
                            }))
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    title = "종료지점",
                    count = if (plan.finalPoint == null) 0 else 1,
                    action = "+ 종료지점",
                    enabled = false
                )
            }
            if (plan.finalPoint == null) {
                item { EmptyStrip("종료지점 없음 · 추가 기능은 Step 6에서 연결됩니다.") }
            } else {
                item(key = plan.finalPoint.id) {
                    CompactEventRow(null, plan.finalPoint) { onCommit(plan.copy(finalPoint = it)) }
                }
            }

            item {
                CompactSection("요약") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        SummaryCell("중도지점", "${plan.midwayEvents.size}개")
                        SummaryCell("종료지점", if (plan.finalPoint == null) "없음" else "1개")
                        SummaryCell("저장", "자동")
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ArmyristPanelShape,
        colors = CardDefaults.cardColors(containerColor = ArmyristColors.WorkSurface),
        border = BorderStroke(1.dp, ArmyristColors.Border)
    ) {
        Column(
            Modifier.padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(title, fontWeight = FontWeight.Bold, color = ArmyristColors.PrimaryControl)
            content()
        }
    }
}

@Composable
private fun AnchorTile(
    modifier: Modifier,
    label: String,
    clock: ClockValue,
    onClock: (ClockTime) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    Card(
        modifier = modifier,
        shape = ArmyristPanelShape,
        colors = CardDefaults.cardColors(containerColor = ArmyristColors.RaisedSurface),
        border = BorderStroke(1.dp, ArmyristColors.Border)
    ) {
        Column(
            Modifier.fillMaxWidth().clickable { showPicker = true }.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(clockText(clock), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("편집", style = MaterialTheme.typography.labelSmall, color = ArmyristColors.SecondaryText)
        }
    }
    if (showPicker) {
        Armyrist24HourTimeDialog(
            initial = clock.time,
            onDismiss = { showPicker = false },
            onConfirm = { showPicker = false; onClock(it) }
        )
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, action: String, enabled: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("$title · $count", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = {},
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 3.dp),
            shape = ArmyristPanelShape
        ) { Text(action) }
    }
}

@Composable
private fun EmptyStrip(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ArmyristPanelShape,
        border = BorderStroke(1.dp, ArmyristColors.Border),
        color = ArmyristColors.RaisedSurface
    ) {
        Text(
            text,
            modifier = Modifier.padding(10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = ArmyristColors.SecondaryText
        )
    }
}

@Composable
private fun CompactEventRow(index: Int?, event: TimeEvent, onChange: (TimeEvent) -> Unit) {
    var expanded by remember(event.id) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ArmyristPanelShape,
        border = BorderStroke(1.dp, ArmyristColors.Border),
        color = if (event.kind == TimeEventKind.FINAL) ArmyristColors.WorkSurface else ArmyristColors.RaisedSurface
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Surface(shape = CircleShape, color = ArmyristColors.PrimaryControl) {
                    Text(
                        index?.toString() ?: "F",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = ArmyristColors.OnDark,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(event.name, fontWeight = FontWeight.Bold)
                    Text(
                        eventTimeText(event.timeSpec),
                        style = MaterialTheme.typography.bodySmall,
                        color = ArmyristColors.SecondaryText
                    )
                }
                if (!event.note.isNullOrBlank()) {
                    Text("메모", style = MaterialTheme.typography.labelSmall, color = ArmyristColors.SecondaryText)
                }
                Text(if (expanded) "▲" else "▼")
            }
            if (expanded) {
                HorizontalDivider()
                EventEditor(event, onChange)
            }
        }
    }
}

@Composable
private fun EventEditor(event: TimeEvent, onChange: (TimeEvent) -> Unit) {
    var name by remember(event.id, event.name) { mutableStateOf(event.name) }
    var note by remember(event.id, event.note) { mutableStateOf(event.note.orEmpty()) }
    val isRange = event.timeSpec is EventTimeSpec.Range

    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                if (it.trim().isNotEmpty()) onChange(event.copy(name = it.trim()))
            },
            label = { Text("일정명") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("시간 범위", modifier = Modifier.weight(1f))
            Switch(
                checked = isRange,
                onCheckedChange = {
                    val newSpec = if (it) {
                        when (val old = event.timeSpec) {
                            is EventTimeSpec.Single -> EventTimeSpec.Range(start = old.value)
                            else -> EventTimeSpec.Range()
                        }
                    } else {
                        when (val old = event.timeSpec) {
                            is EventTimeSpec.Range -> EventTimeSpec.Single(old.start)
                            else -> EventTimeSpec.Single()
                        }
                    }
                    onChange(event.copy(timeSpec = newSpec))
                }
            )
        }
        when (val spec = event.timeSpec) {
            EventTimeSpec.Unspecified -> TimeEditButton("시각 입력", ClockValue.unset()) {
                onChange(event.copy(timeSpec = EventTimeSpec.Single(ClockValue.explicit(it))))
            }
            is EventTimeSpec.Single -> TimeEditButton("시각", spec.value) {
                onChange(event.copy(timeSpec = EventTimeSpec.Single(ClockValue.explicit(it))))
            }
            is EventTimeSpec.Range -> Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(Modifier.weight(1f)) {
                    TimeEditButton("시작", spec.start) {
                        onChange(event.copy(timeSpec = spec.copy(start = ClockValue.explicit(it))))
                    }
                }
                Box(Modifier.weight(1f)) {
                    TimeEditButton("종료", spec.end) {
                        onChange(event.copy(timeSpec = spec.copy(end = ClockValue.explicit(it))))
                    }
                }
            }
        }
        OutlinedTextField(
            value = note,
            onValueChange = {
                note = it
                onChange(event.copy(note = it.trim().ifEmpty { null }))
            },
            label = { Text("비고 (선택)") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2
        )
        Text(
            "자동 저장",
            style = MaterialTheme.typography.labelSmall,
            color = ArmyristColors.SecondaryText
        )
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("시간 입력", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("닫기") }
                }
                Text(
                    "24시간제 · 다이얼과 숫자 입력을 한 화면에서 사용합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ArmyristColors.SecondaryText
                )

                Text("시", fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (row in 0 until 4) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
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
                }

                Text("분", fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    onValueChange = {
                        raw = it.filter(Char::isDigit).take(4)
                        error = null
                    },
                    label = { Text("직접 입력 (예: 940 / 1710)") },
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
                ) {
                    Text("확인 · %02d:%02d".format(hour, minute), fontWeight = FontWeight.Bold)
                }
            }
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

private fun clockText(value: ClockValue): String =
    value.time?.minuteOfDay?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "--:--"

private fun eventTimeText(spec: EventTimeSpec): String = when (spec) {
    EventTimeSpec.Unspecified -> "시각 미입력"
    is EventTimeSpec.Single -> clockText(spec.value)
    is EventTimeSpec.Range -> "${clockText(spec.start)} ~ ${clockText(spec.end)}"
}
