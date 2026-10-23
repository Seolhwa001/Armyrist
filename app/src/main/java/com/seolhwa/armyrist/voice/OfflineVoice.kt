package com.seolhwa.armyrist.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.seolhwa.armyrist.ArmyristColors
import com.seolhwa.armyrist.ArmyristPanelShape

/** No network fallback is ever used. On-device SpeechRecognizer only. */
enum class VoiceUiState { IDLE, PREPARING, LISTENING, RECOGNIZING, STRUCTURING, REVIEW, ERROR, UNAVAILABLE }

enum class VoiceToolContext {
    COUNTING,
    CHECKLIST,
    TIME_PLAN,
    GENERIC
}

private data class VoiceContextGuide(
    val example: String,
    val helper: String
)

private fun guideFor(tool: VoiceToolContext): VoiceContextGuide? = when (tool) {
    VoiceToolContext.COUNTING -> VoiceContextGuide(
        example = "“생수 스물네 병, 전투식량 열세 개, 건전지 여섯 개.”",
        helper = "여러 항목을 한 번에 말할 수 있어요."
    )
    VoiceToolContext.CHECKLIST -> VoiceContextGuide(
        example = "“차량 상태 확인, 통신장비 확인, 인원 확인.”",
        helper = "여러 점검 항목을 한 번에 말할 수 있어요."
    )
    VoiceToolContext.TIME_PLAN -> VoiceContextGuide(
        example = "“8월 14일 9시 출발, 9시 40분 집결, 15일 새벽 1시 이동.”",
        helper = "날짜가 바뀌는 일정도 한 번에 말할 수 있어요."
    )
    VoiceToolContext.GENERIC -> null
}

private fun toolLabel(tool: VoiceToolContext): String = when (tool) {
    VoiceToolContext.COUNTING -> "COUNTING"
    VoiceToolContext.CHECKLIST -> "CHECKLIST"
    VoiceToolContext.TIME_PLAN -> "TIME PLAN"
    VoiceToolContext.GENERIC -> "VOICE"
}

@Composable
fun OfflineVoiceButton(
    modifier: Modifier = Modifier,
    toolContext: VoiceToolContext = VoiceToolContext.GENERIC,
    onTranscript: (String) -> Unit,
    onStateChanged: (VoiceUiState) -> Unit = {},
    onMessage: (String) -> Unit = {},
    readingPrompt: String? = null,
    onNotice: (String) -> Unit = onMessage
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val session = remember { OfflineSpeechSession(context.applicationContext) }
    var state by remember { mutableStateOf(if (session.available()) VoiceUiState.IDLE else VoiceUiState.UNAVAILABLE) }
    var recordingScreenVisible by remember { mutableStateOf(false) }
    var liveTranscript by remember { mutableStateOf("") }

    DisposableEffect(Unit) { onDispose { session.destroy() } }

    fun update(s: VoiceUiState) {
        state = s
        onStateChanged(s)
        if (s == VoiceUiState.REVIEW || s == VoiceUiState.ERROR || s == VoiceUiState.UNAVAILABLE) {
            recordingScreenVisible = false
        }
    }

    fun startSession() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        recordingScreenVisible = true
        liveTranscript = ""
        session.start(
            onState = ::update,
            onTranscript = {
                onTranscript(it)
                update(VoiceUiState.REVIEW)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onError = {
                recordingScreenVisible = false
                onMessage(it)
            },
            onPreview = { liveTranscript = it },
            onNotice = onNotice,
            toolContext = toolContext
        )
    }

    fun cancelSession() {
        session.cancel()
        recordingScreenVisible = false
        update(if (session.available()) VoiceUiState.IDLE else VoiceUiState.UNAVAILABLE)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startSession() else {
            update(VoiceUiState.ERROR)
            onMessage("마이크 권한이 없어 음성 입력을 사용할 수 없습니다. 수동 입력은 계속 사용할 수 있습니다.")
        }
    }

    OutlinedButton(
        onClick = {
            when (state) {
                VoiceUiState.UNAVAILABLE -> onMessage("이 기기에서 오프라인 음성인식을 사용할 수 없습니다.")
                VoiceUiState.RECOGNIZING, VoiceUiState.STRUCTURING -> Unit
                else -> {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        startSession()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            }
        },
        modifier = modifier.heightIn(min = 52.dp),
        shape = ArmyristPanelShape,
        enabled = state != VoiceUiState.RECOGNIZING && state != VoiceUiState.STRUCTURING,
        border = BorderStroke(1.dp, ArmyristColors.Border),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = ArmyristColors.WorkSurface,
            contentColor = ArmyristColors.PrimaryText
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            when (state) {
                VoiceUiState.RECOGNIZING -> "인식 중..."
                VoiceUiState.STRUCTURING -> "정리 중..."
                VoiceUiState.UNAVAILABLE -> "음성 입력 사용 불가"
                else -> "음성 입력"
            },
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge
        )
    }

    if (recordingScreenVisible) {
        VoiceRecordingScreen(
            toolContext = toolContext,
            readingPrompt = readingPrompt,
            liveTranscript = liveTranscript,
            state = state,
            onFinish = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                session.finish()
            },
            onCancel = ::cancelSession
        )
    }
}

@Composable
internal fun VoiceRecordingScreen(
    toolContext: VoiceToolContext,
    readingPrompt: String?,
    liveTranscript: String,
    state: VoiceUiState,
    onFinish: () -> Unit,
    onCancel: () -> Unit
) {
    val guide = remember(toolContext,readingPrompt) { readingPrompt?.let { VoiceContextGuide(it,"읽은 뒤 아래 완료 버튼을 누르세요.") } ?: guideFor(toolContext) }

    BackHandler(onBack = onCancel)

    Dialog(onDismissRequest = onCancel) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(.96f),
            shape = ArmyristPanelShape,
            color = ArmyristColors.AppBackground
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Surface(
                    color = ArmyristColors.PrimaryControl,
                    contentColor = ArmyristColors.OnDark,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onCancel,
                            colors = ButtonDefaults.textButtonColors(contentColor = ArmyristColors.OnDark)
                        ) {
                            Text("←")
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                "음성 입력",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                toolLabel(toolContext),
                                style = MaterialTheme.typography.labelMedium,
                                color = ArmyristColors.OnDark.copy(alpha = .78f)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(12.dp))

                    Text(
                        "●",
                        style = MaterialTheme.typography.displaySmall,
                        color = ArmyristColors.PrimaryControl
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        when (state) {
                            VoiceUiState.PREPARING -> "마이크 준비 중 · 잠시 기다려주세요"
                            VoiceUiState.RECOGNIZING -> "문장 인식 중 · 잠시 기다려주세요"
                            VoiceUiState.STRUCTURING -> "정리 중..."
                            else -> "듣는 중... 말하세요"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(22.dp))

                    if (guide != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = ArmyristPanelShape,
                            colors = CardDefaults.cardColors(containerColor = ArmyristColors.WorkSurface),
                            border = BorderStroke(1.dp, ArmyristColors.Border)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    if(readingPrompt==null)"말할 수 있어요 (예시)"else"이 문장을 읽어주세요",
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    guide.example,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    guide.helper,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("인식 중인 내용 · 확정 전에는 바뀔 수 있습니다",style=MaterialTheme.typography.labelMedium)
                    LazyColumn(Modifier.weight(1f).fillMaxWidth()) { item { Text(liveTranscript.ifBlank{"‘듣는 중’ 표시가 나오면 말해주세요."},Modifier.padding(10.dp)) } }
                    Text("문장 사이에 준비/인식 중 표시가 나오면 잠시 멈췄다가 이어 말하세요.",style=MaterialTheme.typography.bodySmall,color=ArmyristColors.SecondaryText)
                    Button(
                        onClick = onFinish,
                        enabled = state in listOf(VoiceUiState.PREPARING,VoiceUiState.LISTENING,VoiceUiState.RECOGNIZING),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                        shape = ArmyristPanelShape,
                        colors = ButtonDefaults.buttonColors(containerColor = ArmyristColors.PrimaryControl)
                    ) {
                        Text("■  입력 종료", fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "말하기가 끝나면 '입력 종료'를 누르세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private val VoiceReviewWarningColor = Color(0xFFC77800)

@Composable
fun VoiceDraftStatusHeader(state: VoiceDraftState) {
    val (label, color) = when (state) {
        VoiceDraftState.VALID -> "✓ 정상" to ArmyristColors.Accent
        VoiceDraftState.REVIEW_REQUIRED -> "! 확인 필요" to VoiceReviewWarningColor
        VoiceDraftState.INVALID -> "! 문제 있음" to ArmyristColors.Danger
    }
    Text(label, fontWeight = FontWeight.Bold, color = color)
}

@Composable
fun VoiceTranscriptDisclosure(rawTranscript: String) {
    if (rawTranscript.isBlank()) return
    var expanded by remember(rawTranscript) { mutableStateOf(false) }
    OutlinedButton(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(if (expanded) "인식된 말 숨기기 ▲" else "인식된 말 보기 ▼")
    }
    if (expanded) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = ArmyristPanelShape,
            color = ArmyristColors.WorkSurface,
            border = BorderStroke(1.dp, ArmyristColors.Border)
        ) {
            Text(
                "인식된 말: \"$rawTranscript\"",
                modifier = Modifier.padding(10.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun VoiceFieldMarker(state: VoiceFieldState) {
    if (state == VoiceFieldState.VALID) return
    Text(
        "!",
        fontWeight = FontWeight.Black,
        color = if (state == VoiceFieldState.INVALID) ArmyristColors.Danger else VoiceReviewWarningColor
    )
}

@Composable
fun voiceReviewFieldColors(state: VoiceFieldState): TextFieldColors {
    val c = when (state) {
        VoiceFieldState.VALID -> ArmyristColors.Border
        VoiceFieldState.REVIEW_REQUIRED -> VoiceReviewWarningColor
        VoiceFieldState.INVALID -> ArmyristColors.Danger
    }
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c,
        unfocusedBorderColor = c,
        errorBorderColor = c
    )
}

@Composable
fun VoiceReviewFrame(onDismiss:()->Unit,content:@Composable ColumnScope.()->Unit) {
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        BoxWithConstraints(Modifier.fillMaxSize().imePadding().padding(16.dp),contentAlignment=Alignment.Center) {
            Card(Modifier.widthIn(max=560.dp).fillMaxWidth().height(maxHeight*.92f),shape=ArmyristPanelShape) {
                Column(Modifier.fillMaxSize().padding(14.dp),content=content)
            }
        }
    }
}

@Composable
fun CountingVoiceReviewDialog(
    initial: List<CountingVoiceDraft>,
    onDismiss: () -> Unit,
    onApply: (List<CountingVoiceDraft>) -> Unit
) {
    var drafts by remember { mutableStateOf(initial) }

    fun update(index: Int, draft: CountingVoiceDraft) {
        drafts = drafts.toMutableList().also { it[index] = KoreanVoiceStructurer.revalidate(draft) }
    }

    VoiceReviewFrame(onDismiss) {
                Text("음성 입력 검토", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "아래 항목을 확인하고 필요한 내용을 수정하세요. 적용 전에는 기존 실셈 데이터가 변경되지 않습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ArmyristColors.SecondaryText
                )
                Spacer(Modifier.height(8.dp))

                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(drafts.indices.toList(), key = { it }) { i ->
                        val d = drafts[i]
                        val borderColor = when (d.state) {
                            VoiceDraftState.VALID -> ArmyristColors.Border
                            VoiceDraftState.REVIEW_REQUIRED -> VoiceReviewWarningColor
                            VoiceDraftState.INVALID -> ArmyristColors.Danger
                        }
                        Card(
                            Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, borderColor)
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                VoiceDraftStatusHeader(d.state)
                                if(d.reviewReason.isNotBlank())Text(d.reviewReason,style=MaterialTheme.typography.bodySmall,color=ArmyristColors.SecondaryText)

                                OutlinedTextField(
                                    value = d.name,
                                    onValueChange = { v ->
                                        update(i, d.copy(
                                            name = v,
                                            nameState = if (v.isBlank()) VoiceFieldState.INVALID else VoiceFieldState.VALID
                                        ))
                                    },
                                    label = { Text("품명") },
                                    trailingIcon = { VoiceFieldMarker(d.nameState) },
                                    colors = voiceReviewFieldColors(d.nameState),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = d.quantity?.toString().orEmpty(),
                                        onValueChange = { v ->
                                            val q = v.filter(Char::isDigit).toIntOrNull()
                                            update(i, d.copy(
                                                quantity = q,
                                                quantityState = if (q == null) VoiceFieldState.INVALID else VoiceFieldState.VALID
                                            ))
                                        },
                                        label = { Text("수량") },
                                        trailingIcon = { VoiceFieldMarker(d.quantityState) },
                                        colors = voiceReviewFieldColors(d.quantityState),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = d.unit.orEmpty(),
                                        onValueChange = { v ->
                                            update(i, d.copy(
                                                unit = v,
                                                unitState = if (v.isBlank()) VoiceFieldState.INVALID else VoiceFieldState.VALID
                                            ))
                                        },
                                        label = { Text("단위") },
                                        trailingIcon = { VoiceFieldMarker(d.unitState) },
                                        colors = voiceReviewFieldColors(d.unitState),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                VoiceTranscriptDisclosure(d.rawTranscript)

                                TextButton(onClick = {
                                    drafts = drafts.filterIndexed { idx, _ -> idx != i }
                                }) { Text("삭제") }
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("전체 취소") }
                    Button(
                        onClick = { onApply(drafts) },
                        enabled = drafts.isNotEmpty() && drafts.none { it.state == VoiceDraftState.INVALID },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = ArmyristColors.PrimaryControl)
                    ) { Text(if(drafts.any{it.state==VoiceDraftState.REVIEW_REQUIRED})"확인 후 적용"else"적용") }
                }
    }
}

@Composable
fun ChecklistVoiceReviewDialog(
    initial: List<ChecklistVoiceDraft>,
    onDismiss: () -> Unit,
    onApply: (List<ChecklistVoiceDraft>) -> Unit
) {
    var drafts by remember { mutableStateOf(initial) }

    VoiceReviewFrame(onDismiss) {
                Text("음성 입력 검토", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "아래 항목을 확인하고 필요한 내용을 수정하세요. 기존 항목은 적용 전까지 변경되지 않습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ArmyristColors.SecondaryText
                )
                Spacer(Modifier.height(8.dp))

                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(drafts.indices.toList(), key = { it }) { i ->
                        val d = drafts[i]
                        val borderColor = when (d.state) {
                            VoiceDraftState.VALID -> ArmyristColors.Border
                            VoiceDraftState.REVIEW_REQUIRED -> VoiceReviewWarningColor
                            VoiceDraftState.INVALID -> ArmyristColors.Danger
                        }
                        Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, borderColor)) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                VoiceDraftStatusHeader(d.state)
                                if(d.reviewReason.isNotBlank())Text(d.reviewReason,style=MaterialTheme.typography.bodySmall,color=ArmyristColors.SecondaryText)
                                OutlinedTextField(
                                    value = d.name,
                                    onValueChange = { v ->
                                        val changed = d.copy(
                                            name = v,
                                            nameState = if (v.isBlank()) VoiceFieldState.INVALID else VoiceFieldState.VALID
                                        )
                                        drafts = drafts.toMutableList().also {
                                            it[i] = KoreanVoiceStructurer.revalidate(changed)
                                        }
                                    },
                                    label = { Text("체크 항목") },
                                    trailingIcon = { VoiceFieldMarker(d.nameState) },
                                    colors = voiceReviewFieldColors(d.nameState),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                VoiceTranscriptDisclosure(d.rawTranscript)
                                TextButton(onClick = {
                                    drafts = drafts.filterIndexed { idx, _ -> idx != i }
                                }) { Text("삭제") }
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("전체 취소") }
                    Button(
                        onClick = { onApply(drafts) },
                        enabled = drafts.isNotEmpty() && drafts.none { it.state == VoiceDraftState.INVALID },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = ArmyristColors.PrimaryControl)
                    ) { Text(if(drafts.any{it.state==VoiceDraftState.REVIEW_REQUIRED})"확인 후 적용"else"적용") }
                }
    }
}
