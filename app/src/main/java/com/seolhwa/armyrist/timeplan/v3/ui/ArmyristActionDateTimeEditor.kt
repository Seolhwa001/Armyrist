@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.seolhwa.armyrist.timeplan.v3.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seolhwa.armyrist.ArmyristColors
import com.seolhwa.armyrist.ArmyristPanelShape
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
fun ArmyristActionDateTimeEditor(
    title: String,
    initial: LocalDateTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime) -> Unit
) {
    var candidate by remember(initial) { mutableStateOf(initial.withSecond(0).withNano(0)) }
    var raw by remember(initial) { mutableStateOf("%02d%02d".format(candidate.hour, candidate.minute)) }
    var calendar by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = ArmyristPanelShape,
        containerColor = ArmyristColors.RaisedSurface,
        tonalElevation = 0.dp,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(onClick = { candidate = candidate.minusDays(1) }) { Text("-1일") }
                    Text(
                        candidate.toLocalDate().format(DateTimeFormatter.ofPattern("yyyy.MM.dd")),
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(onClick = { candidate = candidate.plusDays(1) }) { Text("+1일") }
                }

                TextButton(onClick = { calendar = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("달력에서 선택")
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("시간", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        ActionTimeWheel(
                            values = (0..23).toList(),
                            selected = candidate.hour,
                            onSelected = { h ->
                                candidate = candidate.withHour(h)
                                raw = "%02d%02d".format(candidate.hour, candidate.minute)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("분", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        ActionTimeWheel(
                            values = (0..55 step 5).toList(),
                            selected = nearestFive(candidate.minute),
                            onSelected = { m ->
                                candidate = candidate.withMinute(m)
                                raw = "%02d%02d".format(candidate.hour, candidate.minute)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                OutlinedTextField(
                    value = raw,
                    onValueChange = { value ->
                        val digits = value.filter(Char::isDigit).take(4)
                        raw = digits
                        parseHHMM(digits)?.let { (h, m) ->
                            candidate = candidate.withHour(h).withMinute(m)
                        }
                    },
                    label = { Text("직접입력 · HHMM") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ArmyristPanelShape,
                    color = ArmyristColors.WorkSurface,
                    border = BorderStroke(1.dp, ArmyristColors.Border)
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text("변경값", style = MaterialTheme.typography.labelSmall, color = ArmyristColors.SecondaryText)
                        Text(
                            candidate.format(DateTimeFormatter.ofPattern("MM.dd HH:mm")),
                            fontWeight = FontWeight.Bold,
                            color = ArmyristColors.PrimaryControl
                        )
                    }
                }
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("취소") } },
        confirmButton = {
            Button(
                onClick = { onConfirm(candidate) },
                colors = ButtonDefaults.buttonColors(containerColor = ArmyristColors.PrimaryControl)
            ) { Text("적용") }
        }
    )

    if (calendar) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = candidate.toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { calendar = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        candidate = LocalDateTime.of(
                            Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate(),
                            candidate.toLocalTime()
                        )
                    }
                    calendar = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { calendar = false }) { Text("취소") } }
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun ActionTimeWheel(
    values: List<Int>,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier
) {
    val view = LocalView.current
    val itemHeight = 44.dp
    val cycles = 1000
    val middle = cycles / 2
    val selectedIndex = values.indexOf(selected).coerceAtLeast(0)
    val initial = middle * values.size + selectedIndex
    val state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = initial)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = state)
    var centeredIndex by remember { mutableIntStateOf(initial) }

    LaunchedEffect(selected, state.isScrollInProgress) {
        if (state.isScrollInProgress) return@LaunchedEffect
        val normalized = ((centeredIndex % values.size) + values.size) % values.size
        if (normalized == selectedIndex) return@LaunchedEffect
        val cycle = centeredIndex / values.size
        var target = cycle * values.size + selectedIndex
        if (target - centeredIndex > values.size / 2) target -= values.size
        if (centeredIndex - target > values.size / 2) target += values.size
        target = target.coerceIn(1, cycles * values.size - 2)
        state.scrollToItem(target)
        centeredIndex = target
    }

    LaunchedEffect(state) {
        snapshotFlow {
            val info = state.layoutInfo
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
            val centered = info.visibleItemsInfo.minByOrNull { item ->
                kotlin.math.abs((item.offset + item.size / 2) - viewportCenter)
            }
            centered?.index to state.isScrollInProgress
        }.collect { (index, scrolling) ->
            if (index != null && index != centeredIndex) {
                centeredIndex = index
                if (scrolling) {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onSelected(values[((index % values.size) + values.size) % values.size])
                }
            }
        }
    }

    Box(modifier.height(132.dp), contentAlignment = Alignment.Center) {
        Surface(
            Modifier.fillMaxWidth().height(itemHeight),
            shape = ArmyristPanelShape,
            color = ArmyristColors.PrimaryControl
        ) {}
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = itemHeight),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(cycles * values.size) { index ->
                val value = values[index % values.size]
                Box(Modifier.fillMaxWidth().height(itemHeight), contentAlignment = Alignment.Center) {
                    val centered = index == centeredIndex
                    Text(
                        "%02d".format(value),
                        fontSize = if (centered) 21.sp else 17.sp,
                        fontWeight = if (centered) FontWeight.Bold else FontWeight.Medium,
                        color = if (centered) androidx.compose.ui.graphics.Color.White else ArmyristColors.SecondaryText
                    )
                }
            }
        }
    }
}

private fun nearestFive(m: Int) = ((m + 2) / 5 * 5) % 60
private fun parseHHMM(raw: String): Pair<Int, Int>? {
    if (raw.length !in 3..4) return null
    val n = raw.toIntOrNull() ?: return null
    val h = n / 100
    val m = n % 100
    return if (h in 0..23 && m in 0..59) h to m else null
}
