package com.seolhwa.armyrist.home

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seolhwa.armyrist.*
import com.seolhwa.armyrist.timeplan.v3.domain.TimePlanLiveStatus
import java.time.format.DateTimeFormatter

@Composable
fun HomeBannerCard(config: HomeBannerConfig, onOpenPlans: () -> Unit, onEdit: () -> Unit) {
    if (config.mode == HomeBannerMode.HIDDEN) return
    val context = LocalContext.current
    val repository = (context.applicationContext as ArmyristApplication).dateAwareTimePlanRepository
    val now = rememberLiveTime()
    val plans = remember(now, config.planId) {
        repository.getPlans().filter { config.planId == null || it.id == config.planId }
    }
    val active = remember(plans, now) { TimePlanLiveStatus.activePlans(plans, now) }
    val next = remember(plans, now) { TimePlanLiveStatus.nextAction(plans, now) }
    val live = config.mode == HomeBannerMode.TIME_PLAN && active.isNotEmpty()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (live) ArmyristColors.LiveSurface else ArmyristColors.RaisedSurface,
        shape = ArmyristPanelShape,
        border = BorderStroke(1.dp, if (live) ArmyristColors.LiveBorder else ArmyristColors.SoftBorder)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Schedule, contentDescription = null,
                    tint = if (live) ArmyristColors.LiveGreen else ArmyristColors.PrimaryControl)
                Spacer(Modifier.width(8.dp))
                Text(if (config.mode == HomeBannerMode.NOTICE) "홈 안내" else "오늘의 흐름",
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                    color = ArmyristColors.SecondaryText)
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Edit, contentDescription = "홈 배너 편집", tint = ArmyristColors.SecondaryText)
                }
            }
            if (config.mode == HomeBannerMode.NOTICE) {
                Text(config.noticeTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (config.noticeBody.isNotBlank()) Text(config.noticeBody,
                    style = MaterialTheme.typography.bodyMedium, color = ArmyristColors.SecondaryText)
            } else {
                Column(Modifier.fillMaxWidth().clickable(onClick = onOpenPlans), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(config.currentHeading, style = MaterialTheme.typography.labelMedium,
                        color = if (live) ArmyristColors.LiveGreen else ArmyristColors.SecondaryText)
                    Text(when {
                        active.isNotEmpty() -> active.take(3).joinToString(" · ") { it.title } +
                            if (active.size > 3) " 외 ${active.size - 3}개" else ""
                        config.planId != null && plans.isEmpty() -> "연결한 계획을 찾을 수 없습니다"
                        else -> "지금은 진행 중인 계획이 없어요"
                    }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        color = if (live) ArmyristColors.LiveGreen else ArmyristColors.PrimaryText)
                    if (active.isNotEmpty()) {
                        val range = TimePlanLiveStatus.interval(active.first())!!
                        Text("${range.first.format(DateTimeFormatter.ofPattern("MM.dd HH:mm"))} – ${range.second.format(DateTimeFormatter.ofPattern("MM.dd HH:mm"))}",
                            style = MaterialTheme.typography.bodySmall, color = ArmyristColors.LiveGreen)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp), color = ArmyristColors.SoftBorder)
                    Text(config.nextHeading, style = MaterialTheme.typography.labelMedium, color = ArmyristColors.SecondaryText)
                    Text(next?.action?.content ?: "예정된 미완료 실시사항이 없어요",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    next?.let {
                        Text("${it.action.scheduledDateTime.format(DateTimeFormatter.ofPattern("MM.dd HH:mm"))}  ·  ${it.plan.title}",
                            style = MaterialTheme.typography.bodySmall, color = ArmyristColors.SecondaryText)
                    }
                    if (active.isEmpty() && next == null) Text("날짜와 시작·종료 시각이 있는 시간계획을 연결해주세요.",
                        style = MaterialTheme.typography.bodySmall, color = ArmyristColors.MutedText)
                }
            }
        }
    }
}

@Composable
fun HomeBannerSettingsPanel() {
    val context = LocalContext.current
    val preferences = remember(context) { HomeBannerPreferences(context) }
    var saved by remember { mutableStateOf(preferences.load()) }
    var draft by remember { mutableStateOf(saved) }
    var planMenu by remember { mutableStateOf(false) }
    val plans = remember { (context.applicationContext as ArmyristApplication).dateAwareTimePlanRepository.getPlans() }
    ArmyristPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Text("홈 배너 설정", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("시간계획을 연결하거나 안내문을 직접 게시할 수 있어요.",
            style = MaterialTheme.typography.bodySmall, color = ArmyristColors.SecondaryText)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("달력 배너 위젯", fontWeight=FontWeight.SemiBold); Text("홈에서 날짜별 일정 보기 · 접기 상태 유지",style=MaterialTheme.typography.bodySmall) }
            Switch(checked=draft.calendarEnabled,onCheckedChange={draft=draft.copy(calendarEnabled=it)})
        }
        HorizontalDivider(Modifier.padding(vertical=10.dp))
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Text("홈 간결 모드",fontWeight=FontWeight.Bold);Text("달력 요약과 업무 도구를 한 화면에 표시",style=MaterialTheme.typography.bodySmall)}
            Switch(checked=draft.compactHome,onCheckedChange={draft=draft.copy(compactHome=it)})
        }
        Text("시간계획·안내 배너",fontWeight=FontWeight.SemiBold)
        HomeBannerMode.entries.forEach { mode ->
            Row(Modifier.fillMaxWidth().clickable { draft = draft.copy(mode = mode) }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = draft.mode == mode, onClick = { draft = draft.copy(mode = mode) })
                Text(when (mode) { HomeBannerMode.TIME_PLAN -> "시간계획 연동"; HomeBannerMode.NOTICE -> "직접 작성한 안내문"; HomeBannerMode.HIDDEN -> "시간계획·안내 배너 숨김" })
            }
        }
        if (draft.mode == HomeBannerMode.TIME_PLAN) {
            Box {
                OutlinedButton(onClick = { planMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (draft.planId == null) "연동 계획: 전체" else "연동 계획: ${plans.firstOrNull { it.id == draft.planId }?.title ?: "삭제된 계획 · 다시 선택"}",
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(expanded = planMenu, onDismissRequest = { planMenu = false }) {
                    DropdownMenuItem(text = { Text("전체 시간계획") }, onClick = { draft = draft.copy(planId = null); planMenu = false })
                    plans.forEach { plan -> DropdownMenuItem(text = { Text(plan.title) }, onClick = { draft = draft.copy(planId = plan.id); planMenu = false }) }
                }
            }
            OutlinedTextField(value = draft.currentHeading, onValueChange = { draft = draft.copy(currentHeading = it.take(60)) },
                label = { Text("현재 계획 제목") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = draft.nextHeading, onValueChange = { draft = draft.copy(nextHeading = it.take(60)) },
                label = { Text("다음 실시사항 제목") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("실제 날짜 기준으로 표시합니다. 날짜 미설정(D-Day) 계획과 완료한 실시사항은 제외해요.",
                modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = ArmyristColors.SecondaryText)
        } else if (draft.mode == HomeBannerMode.NOTICE) {
            OutlinedTextField(value = draft.noticeTitle, onValueChange = { draft = draft.copy(noticeTitle = it.take(80)) },
                label = { Text("안내 제목") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = draft.noticeBody, onValueChange = { draft = draft.copy(noticeBody = it.take(1000)) },
                label = { Text("게시할 내용") }, minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(14.dp))
        Button(onClick = {
            val value = draft.normalized()
            if (preferences.save(value)) {
                draft = value; saved = value
                Toast.makeText(context, "홈 배너를 저장했습니다.", Toast.LENGTH_SHORT).show()
            } else Toast.makeText(context, "저장하지 못했습니다. 다시 시도해주세요.", Toast.LENGTH_LONG).show()
        }, modifier = Modifier.fillMaxWidth()) { Text("배너 설정 저장") }
        if (draft != saved) Text("저장하지 않은 변경사항이 있어요.", style = MaterialTheme.typography.bodySmall, color = ArmyristColors.PrimaryControl)
    }
}
