@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.seolhwa.armyrist.stage2.data.CoreSuiteRepository
import com.seolhwa.armyrist.stage2.domain.TimePlan
import com.seolhwa.armyrist.stage2.domain.TimePlanResultGenerator
import com.seolhwa.armyrist.stage2.domain.TimePlanRules
import com.seolhwa.armyrist.stage2.domain.TimePoint
import com.seolhwa.armyrist.stage2.domain.ToolResult
import kotlin.math.roundToInt

class TimePlanActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repo = (application as ArmyristApplication).coreSuiteRepository

        setContent {
            ArmyristTheme {
                Surface(Modifier.fillMaxSize(), color = ArmyristColors.AppBackground) {
                    TimePlanApp(
                        repo = repo,
                        onHome = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
private fun TimePlanApp(
    repo: CoreSuiteRepository,
    onHome: () -> Unit
) {
    val context = LocalContext.current
    val intervalPrefs = remember {
        context.getSharedPreferences(
            "armyrist_timeplan_interval_labels",
            Context.MODE_PRIVATE
        )
    }

    var selectedId by remember { mutableStateOf<String?>(null) }
    var showingResult by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val observed = revision

    fun refresh() {
        revision++
    }

    val selected = selectedId?.let(repo::getTimePlan)

    when {
        selectedId == null || selected == null -> {
            BackHandler { onHome() }

            TimePlanListScreen(
                plans = repo.getTimePlans(),
                onHome = onHome,
                onCreate = {
                    selectedId = repo.createTimePlan().id
                    refresh()
                },
                onOpen = { selectedId = it },
                onDelete = { planId ->
                    repo.deleteTimePlan(planId)

                    val editor = intervalPrefs.edit()
                    intervalPrefs.all.keys
                        .filter { key -> key.startsWith("$planId:") }
                        .forEach(editor::remove)
                    editor.apply()

                    refresh()
                }
            )
        }

        showingResult -> {
            BackHandler { showingResult = false }

            CommonShareScreen(
                repo = repo,
                result = generateTimePlanResultWithDurations(
                    plan = selected,
                    intervalLabel = { leftPointId ->
                        intervalPrefs.getString(
                            "${selected.id}:$leftPointId",
                            "경과"
                        ) ?: "경과"
                    }
                ),
                onBack = { showingResult = false }
            )
        }

        else -> {
            TimePlanDetailScreen(
                plan = selected,
                onBack = { selectedId = null },
                onHome = onHome,
                onResult = { showingResult = true },
                onRename = { title ->
                    if (repo.renameTimePlan(selected.id, title)) {
                        refresh()
                    }
                },
                onSetMemo = { memo ->
                    repo.updateTimePlan(selected.id) {
                        it.copy(memo = memo)
                    }
                    refresh()
                },
                onAddPoint = {
                    val ordered = selected.points.sortedBy { it.order }
                    val end = ordered.last()
                    val intermediateCount = (ordered.size - 2).coerceAtLeast(0)

                    val inserted = ordered.dropLast(1) +
                        TimePoint(
                            planId = selected.id,
                            order = ordered.lastIndex,
                            name = "중도 ${intermediateCount + 1}",
                            timeMinutes = null
                        ) +
                        end.copy(order = ordered.size)

                    repo.updateTimePlan(selected.id) {
                        it.copy(
                            points = inserted.mapIndexed { index, point ->
                                point.copy(order = index)
                            }
                        )
                    }
                    refresh()
                },
                onEditPoint = { pointId, name, timeMinutes ->
                    val ordered = selected.points.sortedBy { it.order }
                    val candidate = ordered.map { point ->
                        if (point.id == pointId) {
                            point.copy(
                                name = name.trim(),
                                timeMinutes = timeMinutes
                            )
                        } else {
                            point
                        }
                    }

                    if (
                        name.trim().isNotEmpty() &&
                        isValidPartialOrCompleteTimeline(candidate)
                    ) {
                        repo.updateTimePlan(selected.id) {
                            it.copy(points = candidate)
                        }
                        refresh()
                        true
                    } else {
                        false
                    }
                },
                onDeletePoint = { pointId ->
                    val ordered = selected.points.sortedBy { it.order }
                    val index = ordered.indexOfFirst { it.id == pointId }

                    if (index in 1 until ordered.lastIndex) {
                        val candidate = ordered
                            .filterNot { it.id == pointId }
                            .mapIndexed { newIndex, point ->
                                point.copy(order = newIndex)
                            }

                        if (isValidPartialOrCompleteTimeline(candidate)) {
                            repo.updateTimePlan(selected.id) {
                                it.copy(points = candidate)
                            }
                            refresh()
                        }
                    }
                },
                onMovePoint = { pointId, delta ->
                    val ordered = selected.points.sortedBy { it.order }
                    val from = ordered.indexOfFirst { it.id == pointId }

                    if (from in 1 until ordered.lastIndex) {
                        val to = (from + delta)
                            .coerceIn(1, ordered.lastIndex - 1)

                        if (from != to) {
                            val mutable = ordered.toMutableList()
                            val point = mutable.removeAt(from)
                            mutable.add(to, point)

                            val candidate = mutable.mapIndexed { index, p ->
                                p.copy(order = index)
                            }

                            if (isValidPartialOrCompleteTimeline(candidate)) {
                                repo.updateTimePlan(selected.id) {
                                    it.copy(points = candidate)
                                }
                                refresh()
                            }
                        }
                    }
                },
                intervalLabel = { leftPointId ->
                    intervalPrefs.getString(
                        "${selected.id}:$leftPointId",
                        "경과"
                    ) ?: "경과"
                },
                onEditDuration = { leftPointId, duration, label ->
                    val candidate =
                        editDurationAllowMissingEndpoint(
                            points = selected.points,
                            leftPointId = leftPointId,
                            durationMinutes = duration
                        )

                    if (candidate != null) {
                        repo.updateTimePlan(selected.id) {
                            it.copy(points = candidate)
                        }

                        intervalPrefs.edit()
                            .putString(
                                "${selected.id}:$leftPointId",
                                label.trim().ifEmpty { "경과" }
                            )
                            .apply()

                        refresh()
                        true
                    } else {
                        false
                    }
                }
            )
        }
    }
}

private fun editDurationAllowMissingEndpoint(
    points: List<TimePoint>,
    leftPointId: String,
    durationMinutes: Int
): List<TimePoint>? {
    if (durationMinutes !in 0..1439) return null

    val ordered = points.sortedBy { it.order }
    val leftIndex = ordered.indexOfFirst { it.id == leftPointId }

    if (leftIndex !in 0 until ordered.lastIndex) return null

    val leftTime = ordered[leftIndex].timeMinutes ?: return null

    val nextTime =
        (leftTime + durationMinutes) % 1440

    val candidate = ordered.mapIndexed { index, point ->
        if (index == leftIndex + 1) {
            point.copy(timeMinutes = nextTime)
        } else {
            point
        }
    }

    return if (isValidPartialOrCompleteTimeline(candidate)) {
        candidate
    } else {
        null
    }
}


/**
 * 구간 단위 경과시간 계산.
 *
 * 전체 TimePlan에 미입력 지점이 있어도 현재 구간의 양 끝 시각이
 * 입력되어 있으면 해당 구간의 경과시간은 계속 표시한다.
 */
private fun displayedDuration(
    points: List<TimePoint>,
    leftIndex: Int
): Int? {
    val ordered = points.sortedBy { it.order }

    if (leftIndex !in 0 until ordered.lastIndex) {
        return null
    }

    val left = ordered[leftIndex].timeMinutes ?: return null
    val right = ordered[leftIndex + 1].timeMinutes ?: return null

    return if (right >= left) {
        right - left
    } else {
        1440 - left + right
    }
}

private fun isValidPartialOrCompleteTimeline(
    points: List<TimePoint>
): Boolean {
    if (points.any { it.name.trim().isEmpty() }) return false

    val allDefined = points.all { it.timeMinutes != null }

    return if (allDefined) {
        TimePlanRules.derive(points) != null
    } else {
        points.all {
            it.timeMinutes == null || it.timeMinutes in 0..1439
        }
    }
}


private fun generateTimePlanResultWithDurations(
    plan: TimePlan,
    intervalLabel: (String) -> String
): ToolResult {
    val ordered = plan.points.sortedBy { it.order }
    val lines = mutableListOf(plan.title.trim())

    ordered.forEachIndexed { index, point ->
        val time = point.timeMinutes

        if (time != null) {
            lines +=
                "${TimePlanRules.formatShareClock(time)} ${point.name}"
        }

        if (index >= ordered.lastIndex) {
            return@forEachIndexed
        }

        val nextTime = ordered[index + 1].timeMinutes
        if (time == null || nextTime == null) {
            return@forEachIndexed
        }

        val duration =
            if (nextTime >= time) {
                nextTime - time
            } else {
                1440 - time + nextTime
            }

        val label = intervalLabel(point.id).trim()
        val durationText = formatDuration(duration)

        lines +=
            if (label.isEmpty()) {
                "- $durationText"
            } else {
                "- $durationText $label"
            }
    }

    val memo = plan.memo.trim()
    if (memo.isNotEmpty()) {
        lines += ""
        lines += "[메모]"
        lines += memo
    }

    return ToolResult(
        title = plan.title,
        body = lines.joinToString("\n").trim()
    )
}

@Composable
private fun TimePlanListScreen(
    plans: List<TimePlan>,
    onHome: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    var deleteTarget by remember {
        mutableStateOf<TimePlan?>(null)
    }

    Scaffold(
        topBar = {
            ArmyristTopBar(
                title = "시간계획",
                subtitle = "TIME PLAN · 시각 · 경과시간 · AUTO SAVE",
                leadingLabel = "홈",
                onLeading = onHome
            )
        },
        floatingActionButton = {
            if (plans.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onCreate,
                    modifier = Modifier.heightIn(min = 58.dp),
                    shape = ArmyristPanelShape,
                    containerColor = ArmyristColors.PrimaryControl,
                    contentColor = ArmyristColors.OnDark
                ) {
                    Text(
                        "+ 새 시간계획",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { padding ->
        if (plans.isEmpty()) {
            Box(
                modifier = Modifier
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
                        Text(
                            "저장된 시간계획이 없습니다",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "시작과 종료 시각을 입력해 계획을 만드세요.",
                            color =
                                MaterialTheme.colorScheme.onSurfaceVariant
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
                            Text("새 시간계획 만들기")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding =
                    PaddingValues(12.dp, 8.dp, 12.dp, 96.dp),
                verticalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {
                items(
                    plans,
                    key = { it.id }
                ) { plan ->
                    Card(
                        onClick = { onOpen(plan.id) },
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
                                .padding(16.dp),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    plan.title,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(Modifier.height(3.dp))

                                Text(
                                    timePlanSummary(plan),
                                    style =
                                        MaterialTheme.typography.bodySmall,
                                    color =
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            TextButton(
                                onClick = {
                                    deleteTarget = plan
                                }
                            ) {
                                Text("삭제")
                            }
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { plan ->
        AlertDialog(
            onDismissRequest = {
                deleteTarget = null
            },
            title = {
                Text("시간계획 삭제")
            },
            text = {
                Text(
                    "'${plan.title}'을 삭제합니다."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(plan.id)
                        deleteTarget = null
                    }
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                    }
                ) {
                    Text("취소")
                }
            }
        )
    }
}

private fun timePlanSummary(
    plan: TimePlan
): String {
    val ordered = plan.points.sortedBy { it.order }
    val first = ordered.firstOrNull()?.timeMinutes
    val last = ordered.lastOrNull()?.timeMinutes

    return when {
        first != null && last != null -> {
            "${TimePlanRules.formatClock(first)} → " +
                "${TimePlanRules.formatClock(last)} · " +
                "지점 ${ordered.size}"
        }

        else -> {
            "시각 입력 필요 · 지점 ${ordered.size}"
        }
    }
}

@Composable
private fun TimePlanDetailScreen(
    plan: TimePlan,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onResult: () -> Unit,
    onRename: (String) -> Unit,
    onSetMemo: (String) -> Unit,
    onAddPoint: () -> Unit,
    onEditPoint: (
        pointId: String,
        name: String,
        timeMinutes: Int?
    ) -> Boolean,
    onDeletePoint: (String) -> Unit,
    onMovePoint: (String, Int) -> Unit,
    intervalLabel: (String) -> String,
    onEditDuration: (
        leftPointId: String,
        durationMinutes: Int,
        durationLabel: String
    ) -> Boolean
) {
    val context = LocalContext.current
    var titleEdit by remember {
        mutableStateOf(false)
    }
    var memoEdit by remember {
        mutableStateOf(false)
    }
    var editingPoint by remember {
        mutableStateOf<TimePoint?>(null)
    }
    var editingDurationIndex by remember {
        mutableStateOf<Int?>(null)
    }
    var deletePoint by remember {
        mutableStateOf<TimePoint?>(null)
    }

    val ordered = plan.points.sortedBy { it.order }
    val haptic = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) {
        48.dp.toPx()
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            ArmyristTopBar(
                title = plan.title,
                subtitle = "TIME PLAN · 지점 ${ordered.size} · AUTO SAVE",
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
                    Text("전달", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(context, PortableTransferActivity::class.java).apply {
                                putExtra(PortableTransferActivity.EXTRA_MODE, PortableTransferActivity.MODE_EXPORT)
                                putExtra(PortableTransferActivity.EXTRA_TYPE, ArmyristPortableDataType.TIME_PLAN.name)
                                putExtra(PortableTransferActivity.EXTRA_ROOT_ID, plan.id)
                            }
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = ArmyristPanelShape
                ) {
                    Text("데이터", fontWeight = FontWeight.Bold)
                }

            }

            TimePlanStatusCard(plan)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(8.dp, 8.dp, 8.dp, 24.dp),
                verticalArrangement =
                    Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(
                    ordered,
                    key = { _, point -> point.id }
                ) { index, point ->
                    var dragDistance by remember(point.id) {
                        mutableFloatStateOf(0f)
                    }
                    var visualOffset by remember(point.id) {
                        mutableFloatStateOf(0f)
                    }
                    var dragging by remember(point.id) {
                        mutableStateOf(false)
                    }

                    val intermediate =
                        index in 1 until ordered.lastIndex

                    Card(
                        onClick = { editingPoint = point },
                        shape = ArmyristPanelShape,
                        colors = CardDefaults.cardColors(
                            containerColor = ArmyristColors.RaisedSurface,
                            contentColor = ArmyristColors.PrimaryText
                        ),
                        border = BorderStroke(
                            1.dp,
                            ArmyristColors.Border
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(
                                if (dragging) 1f else 0f
                            )
                            .offset {
                                IntOffset(
                                    0,
                                    visualOffset.roundToInt()
                                )
                            }
                            .pointerInput(
                                point.id,
                                intermediate
                            ) {
                                if (intermediate) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            dragDistance = 0f
                                            visualOffset = 0f
                                            dragging = true

                                            haptic.performHapticFeedback(
                                                HapticFeedbackType.LongPress
                                            )
                                        },
                                        onDragCancel = {
                                            dragDistance = 0f
                                            visualOffset = 0f
                                            dragging = false
                                        },
                                        onDragEnd = {
                                            dragDistance = 0f
                                            visualOffset = 0f
                                            dragging = false
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragDistance += amount.y
                                            visualOffset += amount.y

                                            if (
                                                dragDistance >=
                                                threshold
                                            ) {
                                                onMovePoint(
                                                    point.id,
                                                    1
                                                )
                                                dragDistance -=
                                                    threshold
                                                visualOffset -=
                                                    threshold
                                            } else if (
                                                dragDistance <=
                                                -threshold
                                            ) {
                                                onMovePoint(
                                                    point.id,
                                                    -1
                                                )
                                                dragDistance +=
                                                    threshold
                                                visualOffset +=
                                                    threshold
                                            }
                                        }
                                    )
                                }
                            }
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Text(
                                "${index + 1}.",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(34.dp)
                            )

                            Column(
                                Modifier.weight(1f)
                            ) {
                                Text(
                                    point.name,
                                    style =
                                        MaterialTheme.typography.titleMedium,
                                    fontWeight =
                                        FontWeight.SemiBold
                                )

                                Spacer(Modifier.height(4.dp))

                                Surface(
                                    color =
                                        if (point.timeMinutes == null) {
                                            MaterialTheme.colorScheme.error
                                                .copy(alpha = 0.10f)
                                        } else {
                                            ArmyristColors.SecondaryControl
                                        },
                                    shape = ArmyristPanelShape,
                                    border = BorderStroke(
                                        1.dp,
                                        if (point.timeMinutes == null) {
                                            MaterialTheme.colorScheme.error
                                                .copy(alpha = 0.55f)
                                        } else {
                                            ArmyristColors.PrimaryControl
                                        }
                                    )
                                ) {
                                    Text(
                                        point.timeMinutes?.let {
                                            TimePlanRules.formatClock(it)
                                        } ?: "시각 미입력",
                                        modifier = Modifier.padding(
                                            horizontal = 12.dp,
                                            vertical = 7.dp
                                        ),
                                        style =
                                            MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color =
                                            if (point.timeMinutes == null) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                ArmyristColors.PrimaryText
                                            }
                                    )
                                }
                            }

                            if (intermediate) {
                                TextButton(
                                    onClick = {
                                        deletePoint = point
                                    }
                                ) {
                                    Text("삭제")
                                }
                            }
                        }
                    }

                    if (index < ordered.lastIndex) {
                        val duration =
                            displayedDuration(
                                ordered,
                                index
                            )

                        Surface(
                            onClick = {
                                if (point.timeMinutes != null) {
                                    editingDurationIndex = index
                                }
                            },
                            color = ArmyristColors.WorkSurface,
                            shape = ArmyristPanelShape,
                            border = BorderStroke(
                                1.dp,
                                ArmyristColors.Divider
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                        ) {
                            Row(
                                Modifier.padding(
                                    horizontal = 14.dp,
                                    vertical = 8.dp
                                ),
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                Text(
                                    intervalLabel(point.id),
                                    style =
                                        MaterialTheme.typography.labelMedium,
                                    modifier =
                                        Modifier.widthIn(min = 54.dp)
                                )

                                Text(
                                    duration?.let {
                                        formatDuration(it)
                                    } ?: if (point.timeMinutes != null) {
                                        "경과시간 입력"
                                    } else {
                                        "앞 지점 시각 입력 필요"
                                    },
                                    style =
                                        MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ArmyristColors.PrimaryText,
                                    modifier = Modifier.weight(1f)
                                )

                                if (point.timeMinutes != null) {
                                    Text(
                                        "편집",
                                        style =
                                            MaterialTheme.typography.labelMedium,
                                        color =
                                            ArmyristColors.PrimaryControl
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedButton(
                        onClick = onAddPoint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
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
                        Text("+ 중도 지점 추가")
                    }
                }

                item {
                    Card(
                        onClick = {
                            memoEdit = true
                        },
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
                        Column(
                            Modifier.padding(14.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "메모",
                                    fontWeight =
                                        FontWeight.Bold,
                                    modifier =
                                        Modifier.weight(1f)
                                )
                                Text(
                                    "편집",
                                    color =
                                        ArmyristColors.PrimaryControl
                                )
                            }

                            Spacer(Modifier.height(4.dp))

                            Text(
                                plan.memo.ifBlank {
                                    "메모가 없습니다. 눌러서 입력하세요."
                                },
                                color =
                                    if (plan.memo.isBlank())
                                        MaterialTheme.colorScheme
                                            .onSurfaceVariant
                                    else
                                        MaterialTheme.colorScheme
                                            .onSurface
                            )
                        }
                    }
                }
            }
        }
    }

    if (titleEdit) {
        SimpleTextDialog(
            title = "제목 변경",
            initial = plan.title,
            onDismiss = {
                titleEdit = false
            },
            onConfirm = {
                if (it.trim().isNotEmpty()) {
                    onRename(it)
                }
                titleEdit = false
            }
        )
    }

    if (memoEdit) {
        SimpleTextDialog(
            title = "전체 메모",
            initial = plan.memo,
            multiline = true,
            onDismiss = {
                memoEdit = false
            },
            onConfirm = {
                onSetMemo(it)
                memoEdit = false
            }
        )
    }

    editingPoint?.let { point ->
        TimePointEditDialog(
            point = point,
            onDismiss = {
                editingPoint = null
            },
            onConfirm = { name, time ->
                val success = onEditPoint(
                    point.id,
                    name,
                    time
                )

                if (success) {
                    editingPoint = null
                }

                success
            }
        )
    }

    editingDurationIndex?.let { index ->
        if (index in 0 until ordered.lastIndex) {
            val duration =
                displayedDuration(
                    ordered,
                    index
                )

            DurationEditDialog(
                duration = duration,
                initialLabel = intervalLabel(ordered[index].id),
                onDismiss = {
                    editingDurationIndex = null
                },
                onConfirm = { newDuration, newLabel ->
                    val success =
                        onEditDuration(
                            ordered[index].id,
                            newDuration,
                            newLabel
                        )

                    if (success) {
                        editingDurationIndex = null
                    }

                    success
                }
            )
        }
    }

    deletePoint?.let { point ->
        AlertDialog(
            onDismissRequest = {
                deletePoint = null
            },
            title = {
                Text("중도 지점 삭제")
            },
            text = {
                Text(
                    "'${point.name}'을 삭제합니다. " +
                        "앞뒤 시각은 변경되지 않습니다."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePoint(point.id)
                        deletePoint = null
                    }
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        deletePoint = null
                    }
                ) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
private fun TimePlanStatusCard(
    plan: TimePlan
) {
    val ordered = plan.points.sortedBy { it.order }
    val derived = TimePlanRules.derive(ordered)

    Surface(
        color = ArmyristColors.WorkSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(
                horizontal = 12.dp,
                vertical = 8.dp
            )
        ) {
            Text(
                "계획 현황",
                style =
                    MaterialTheme.typography.labelMedium
            )

            when {
                ordered.any {
                    it.timeMinutes == null
                } -> {
                    Text(
                        "시각 입력이 필요한 지점이 있습니다.",
                        fontWeight = FontWeight.SemiBold,
                        color =
                            MaterialTheme.colorScheme.error
                    )
                }

                derived == null -> {
                    Text(
                        "시간 순서가 허용 범위를 벗어났습니다.",
                        fontWeight = FontWeight.SemiBold,
                        color =
                            MaterialTheme.colorScheme.error
                    )
                }

                else -> {
                    val span =
                        derived.last().absoluteMinute -
                            derived.first().absoluteMinute

                    Text(
                        "총 ${formatDuration(span)} · " +
                            "중도 ${(ordered.size - 2).coerceAtLeast(0)}개",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun TimePointEditDialog(
    point: TimePoint,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        timeMinutes: Int?
    ) -> Boolean
) {
    var name by remember(point.id) {
        mutableStateOf(point.name)
    }

    var rawTime by remember(point.id) {
        mutableStateOf(
            point.timeMinutes?.let {
                TimePlanRules.formatShareClock(it)
            } ?: ""
        )
    }

    var error by remember {
        mutableStateOf("")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("지점 편집")
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = ""
                    },
                    label = {
                        Text("명칭")
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = rawTime,
                    onValueChange = {
                        rawTime = it
                            .filter { char ->
                                char.isDigit() ||
                                    char == ':'
                            }
                            .take(5)
                        error = ""
                    },
                    label = {
                        Text("시각")
                    },
                    placeholder = {
                        Text("예: 0830")
                    },
                    supportingText = {
                        Text(
                            "HHMM 또는 HH:MM"
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (error.isNotBlank()) {
                    Text(
                        error,
                        color =
                            MaterialTheme.colorScheme.error,
                        style =
                            MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val normalizedName =
                        name.trim()

                    if (normalizedName.isEmpty()) {
                        error =
                            "명칭을 입력해 주세요."
                        return@TextButton
                    }

                    val parsed =
                        TimePlanRules.parseClock(
                            rawTime
                        )

                    if (parsed == null) {
                        error =
                            "시각은 HHMM 형식으로 입력해 주세요."
                        return@TextButton
                    }

                    val success =
                        onConfirm(
                            normalizedName,
                            parsed
                        )

                    if (!success) {
                        error =
                            "이 시각은 현재 계획에서 사용할 수 없습니다."
                    }
                }
            ) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
private fun DurationEditDialog(
    duration: Int?,
    initialLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (Int, String) -> Boolean
) {
    var raw by remember(duration) {
        mutableStateOf(
            duration?.let(::formatDurationInput) ?: ""
        )
    }

    var label by remember(initialLabel) {
        mutableStateOf(initialLabel)
    }

    var error by remember {
        mutableStateOf("")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("경과시간 변경")
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = {
                        label = it
                        error = ""
                    },
                    label = {
                        Text("구간 명칭")
                    },
                    placeholder = {
                        Text("예: 이동, 준비, 대기")
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = raw,
                    onValueChange = {
                        raw = it
                            .filter { char ->
                                char.isDigit() ||
                                    char == ':'
                            }
                            .take(5)
                        error = ""
                    },
                    label = {
                        Text("경과시간")
                    },
                    placeholder = {
                        Text("예: 0040")
                    },
                    supportingText = {
                        Text(
                            "다음 지점 시각이 비어 있어도 경과시간을 입력하면 자동 계산됩니다."
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (error.isNotBlank()) {
                    Text(
                        error,
                        color =
                            MaterialTheme.colorScheme.error,
                        style =
                            MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed =
                        TimePlanRules.parseDuration(raw)

                    if (parsed == null) {
                        error =
                            "경과시간은 HHMM 형식으로 입력해 주세요."
                        return@TextButton
                    }

                    val normalizedLabel =
                        label.trim().ifEmpty { "경과" }

                    if (!onConfirm(parsed, normalizedLabel)) {
                        error =
                            "변경 후 계획이 24시간 범위를 벗어납니다."
                    }
                }
            ) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
private fun SimpleTextDialog(
    title: String,
    initial: String,
    multiline: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(initial) {
        mutableStateOf(initial)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title)
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                },
                minLines =
                    if (multiline) 4 else 1,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(text)
                }
            ) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
private fun TimePlanResultScreen(
    result: ToolResult,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ArmyristColors.Header,
                    titleContentColor = ArmyristColors.OnDark,
                    navigationIconContentColor = ArmyristColors.OnDark,
                    actionIconContentColor = ArmyristColors.OnDark
                ),
                title = {
                    Text("전달 미리보기")
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("‹ 시간계획")
                    }
                }
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
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    Modifier.padding(12.dp)
                ) {
                    item {
                        Text(result.body)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val clipboard =
                            context.getSystemService(
                                Context.CLIPBOARD_SERVICE
                            ) as ClipboardManager

                        clipboard.setPrimaryClip(
                            ClipData.newPlainText(
                                "시간계획 전달",
                                result.body
                            )
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
                        val intent =
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    result.body
                                )
                            }

                        context.startActivity(
                            Intent.createChooser(
                                intent,
                                "공유"
                            )
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

private fun formatDuration(
    minutes: Int
): String {
    val hour = minutes / 60
    val minute = minutes % 60
    return "%02d:%02d".format(
        hour,
        minute
    )
}

private fun formatDurationInput(
    minutes: Int
): String {
    val hour = minutes / 60
    val minute = minutes % 60
    return "%02d%02d".format(
        hour,
        minute
    )
}
