package com.seolhwa.armyrist.timeplan.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seolhwa.armyrist.*
import com.seolhwa.armyrist.timeplan.data.TimePlanV2Repository
import com.seolhwa.armyrist.timeplan.domain.*

@Composable
fun TimePlanV2App(
    repository: TimePlanV2Repository,
    onHome: () -> Unit
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val observed = revision
    fun refresh() { revision++ }

    val selected = selectedId?.let(repository::getPlan)
    if (selected == null) {
        TimePlanV2List(
            plans = repository.getPlans(),
            onHome = onHome,
            onOpen = { selectedId = it }
        )
    } else {
        TimePlanV2Detail(
            plan = selected,
            onBack = { selectedId = null },
            onHome = onHome,
            onCommit = {
                if (repository.commit(it.copy(updatedAt = System.currentTimeMillis().toString()))) {
                    refresh()
                }
            }
        )
    }
}

@Composable
private fun TimePlanV2List(
    plans: List<RevisedTimePlan>,
    onHome: () -> Unit,
    onOpen: (String) -> Unit
) {
    Scaffold(
        topBar = {
            ArmyristTopBar(
                title = "시간계획",
                subtitle = "TIME PLAN · CORE REVISION",
                leadingLabel = "홈",
                onLeading = onHome
            )
        }
    ) { padding ->
        if (plans.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("기존 시간계획이 없습니다.")
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(plans, key = { it.id }) { plan ->
                    Card(
                        onClick = { onOpen(plan.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = ArmyristPanelShape,
                        colors = CardDefaults.cardColors(
                            containerColor = ArmyristColors.RaisedSurface
                        ),
                        border = BorderStroke(1.dp, ArmyristColors.Border)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(plan.title, fontWeight = FontWeight.Bold)
                            Text(
                                "${plan.midwayEvents.size}개 중도지점" +
                                    if (plan.finalPoint != null) " · 종료지점 있음" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = ArmyristColors.SecondaryText
                            )
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
    onHome: () -> Unit,
    onCommit: (RevisedTimePlan) -> Unit
) {
    var title by remember(plan.id, plan.title) { mutableStateOf(plan.title) }
    var memo by remember(plan.id, plan.memo) { mutableStateOf(plan.memo.orEmpty()) }

    Scaffold(
        topBar = {
            ArmyristTopBar(
                title = plan.title,
                subtitle = "TIME PLAN · V2",
                leadingLabel = "목록",
                onLeading = onBack,
                actionLabel = "홈",
                onAction = onHome
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp, 10.dp, 12.dp, 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("시간계획 제목") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                AnchorCard(
                    label = "START",
                    clock = plan.start.value,
                    onClock = { clock ->
                        onCommit(plan.copy(start = TimeAnchor(ClockValue.explicit(clock))))
                    }
                )
            }

            plan.midwayEvents.sortedBy { it.order }.forEach { event ->
                item(key = event.id) {
                    EventCard(
                        event = event,
                        onChange = { changed ->
                            onCommit(
                                plan.copy(
                                    midwayEvents = plan.midwayEvents.map {
                                        if (it.id == changed.id) changed else it
                                    }
                                )
                            )
                        }
                    )
                }
            }

            plan.finalPoint?.let { final ->
                item(key = final.id) {
                    EventCard(
                        event = final,
                        onChange = { onCommit(plan.copy(finalPoint = it)) }
                    )
                }
            }

            item {
                AnchorCard(
                    label = "END",
                    clock = plan.end.value,
                    onClock = { clock ->
                        onCommit(plan.copy(end = TimeAnchor(ClockValue.explicit(clock))))
                    }
                )
            }

            item {
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("메모") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }

            item {
                Button(
                    onClick = {
                        val cleanTitle = title.trim()
                        if (cleanTitle.isNotEmpty()) {
                            onCommit(plan.copy(title = cleanTitle, memo = memo.trim().ifEmpty { null }))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = ArmyristPanelShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ArmyristColors.PrimaryControl,
                        contentColor = ArmyristColors.OnDark
                    )
                ) { Text("기본 정보 저장", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun AnchorCard(
    label: String,
    clock: ClockValue,
    onClock: (ClockTime) -> Unit
) {
    ArmyristTimeCard(label) {
        ClockButton(clock = clock, onClock = onClock)
    }
}

@Composable
private fun EventCard(
    event: TimeEvent,
    onChange: (TimeEvent) -> Unit
) {
    var name by remember(event.id, event.name) { mutableStateOf(event.name) }
    var note by remember(event.id, event.note) { mutableStateOf(event.note.orEmpty()) }
    var range by remember(event.id, event.timeSpec) {
        mutableStateOf(event.timeSpec is EventTimeSpec.Range)
    }

    ArmyristTimeCard(if (event.kind == TimeEventKind.FINAL) "FINAL" else "MIDWAY") {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("일정명") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("시간 범위")
            Spacer(Modifier.weight(1f))
            Switch(
                checked = range,
                onCheckedChange = {
                    range = it
                    val spec =
                        if (it) EventTimeSpec.Range()
                        else EventTimeSpec.Single()
                    onChange(event.copy(timeSpec = spec))
                }
            )
        }

        when (val spec = event.timeSpec) {
            EventTimeSpec.Unspecified -> {
                ClockButton(ClockValue.unset()) {
                    onChange(event.copy(timeSpec = EventTimeSpec.Single(ClockValue.explicit(it))))
                }
            }
            is EventTimeSpec.Single -> {
                ClockButton(spec.value) {
                    onChange(event.copy(timeSpec = EventTimeSpec.Single(ClockValue.explicit(it))))
                }
            }
            is EventTimeSpec.Range -> {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.weight(1f)) {
                        ClockButton(spec.start) {
                            onChange(event.copy(timeSpec = spec.copy(start = ClockValue.explicit(it))))
                        }
                    }
                    Text("~", modifier = Modifier.align(Alignment.CenterVertically))
                    Box(Modifier.weight(1f)) {
                        ClockButton(spec.end) {
                            onChange(event.copy(timeSpec = spec.copy(end = ClockValue.explicit(it))))
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("비고") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 1,
            maxLines = 2
        )
        OutlinedButton(
            onClick = {
                val clean = name.trim()
                if (clean.isNotEmpty()) onChange(event.copy(name = clean, note = note.trim().ifEmpty { null }))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = ArmyristPanelShape
        ) { Text("일정 정보 저장") }
    }
}

@Composable
private fun ArmyristTimeCard(
    label: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ArmyristPanelShape,
        colors = CardDefaults.cardColors(containerColor = ArmyristColors.WorkSurface),
        border = BorderStroke(1.dp, ArmyristColors.Border)
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(label, fontWeight = FontWeight.Bold, color = ArmyristColors.PrimaryControl)
            content()
        }
    }
}

@Composable
private fun ClockButton(
    clock: ClockValue,
    onClock: (ClockTime) -> Unit
) {
    val context = LocalContext.current
    val minute = clock.time?.minuteOfDay
    val h = minute?.div(60) ?: 9
    val m = minute?.rem(60) ?: 0
    OutlinedButton(
        onClick = {
            TimePickerDialog(context, { _, hour, minuteOfHour ->
                onClock(ClockTime.requireMinuteOfDay(hour * 60 + minuteOfHour))
            }, h, m, true).show()
        },
        modifier = Modifier.fillMaxWidth(),
        shape = ArmyristPanelShape
    ) {
        Text(
            minute?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "시각 입력",
            fontWeight = FontWeight.SemiBold
        )
    }
}
