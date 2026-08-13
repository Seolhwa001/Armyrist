package com.seolhwa.armyrist.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.seolhwa.armyrist.ArmyristColors
import com.seolhwa.armyrist.ArmyristPanelShape

/** No network fallback is ever used. On-device SpeechRecognizer only. */
enum class VoiceUiState { IDLE, LISTENING, RECOGNIZING, STRUCTURING, REVIEW, ERROR, UNAVAILABLE }
class OfflineSpeechSession(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private var keepListening = false
    private var accumulated = mutableListOf<String>()
    private var stateCallback: ((VoiceUiState) -> Unit)? = null
    private var transcriptCallback: ((String) -> Unit)? = null
    private var errorCallback: ((String) -> Unit)? = null

    fun available(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)

    fun start(
        onState: (VoiceUiState) -> Unit,
        onTranscript: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!available()) {
            onState(VoiceUiState.UNAVAILABLE)
            onError("이 기기에서 오프라인 음성인식을 사용할 수 없습니다.")
            return
        }
        destroyRecognizer()
        accumulated.clear()
        keepListening = true
        stateCallback = onState
        transcriptCallback = onTranscript
        errorCallback = onError
        beginRecognition()
    }

    private fun beginRecognition() {
        if (!keepListening) return
        val sr = runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }.getOrNull()
        if (sr == null) {
            keepListening = false
            stateCallback?.invoke(VoiceUiState.UNAVAILABLE)
            errorCallback?.invoke("이 기기에서 오프라인 음성인식을 시작할 수 없습니다.")
            return
        }
        recognizer = sr
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { stateCallback?.invoke(VoiceUiState.LISTENING) }
            override fun onBeginningOfSpeech() { stateCallback?.invoke(VoiceUiState.LISTENING) }
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { stateCallback?.invoke(VoiceUiState.RECOGNIZING) }
            override fun onError(error: Int) {
                destroyRecognizer()
                if (keepListening && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                    beginRecognition()
                } else if (keepListening) {
                    keepListening = false
                    stateCallback?.invoke(VoiceUiState.ERROR)
                    errorCallback?.invoke("음성인식에 실패했습니다. 다시 시도해주세요.")
                } else {
                    finishAccumulated()
                }
            }
            override fun onResults(results: Bundle?) {
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let(accumulated::add)
                destroyRecognizer()
                if (keepListening) {
                    // Android recognizers normally finish after a phrase. Keep the Armyrist voice
                    // session alive by starting the next offline recognition turn and accumulating it.
                    beginRecognition()
                } else {
                    finishAccumulated()
                }
            }
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        sr.startListening(android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        })
    }

    private fun finishAccumulated() {
        val text = accumulated.joinToString(", ").trim()
        if (text.isEmpty()) {
            stateCallback?.invoke(VoiceUiState.ERROR)
            errorCallback?.invoke("인식된 음성이 없습니다.")
        } else {
            stateCallback?.invoke(VoiceUiState.STRUCTURING)
            transcriptCallback?.invoke(text)
        }
        accumulated.clear()
    }

    fun stop() {
        if (!keepListening) return
        keepListening = false
        stateCallback?.invoke(VoiceUiState.RECOGNIZING)
        recognizer?.stopListening() ?: finishAccumulated()
    }

    fun cancel() {
        keepListening = false
        accumulated.clear()
        recognizer?.cancel()
        destroyRecognizer()
    }

    private fun destroyRecognizer() { recognizer?.destroy(); recognizer = null }
    fun destroy() { cancel(); stateCallback = null; transcriptCallback = null; errorCallback = null }
}

@Composable
fun OfflineVoiceButton(
    modifier: Modifier = Modifier,
    onTranscript: (String) -> Unit,
    onStateChanged: (VoiceUiState) -> Unit = {},
    onMessage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val session = remember { OfflineSpeechSession(context.applicationContext) }
    var state by remember { mutableStateOf(if (session.available()) VoiceUiState.IDLE else VoiceUiState.UNAVAILABLE) }
    DisposableEffect(Unit) { onDispose { session.destroy() } }

    fun update(s: VoiceUiState) { state = s; onStateChanged(s) }
    fun startSession() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        session.start(
            onState = ::update,
            onTranscript = {
                onTranscript(it)
                update(VoiceUiState.REVIEW)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onError = { onMessage(it) }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startSession() else {
            update(VoiceUiState.ERROR)
            onMessage("마이크 권한이 없어 음성 입력을 사용할 수 없습니다. 수동 입력은 계속 사용할 수 있습니다.")
        }
    }

    Button(
        onClick = {
            when (state) {
                VoiceUiState.LISTENING -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    session.stop()
                    update(VoiceUiState.RECOGNIZING)
                }
                VoiceUiState.UNAVAILABLE -> onMessage("이 기기에서 오프라인 음성인식을 사용할 수 없습니다.")
                else -> {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startSession()
                    else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        },
        modifier = modifier.heightIn(min = 52.dp),
        shape = ArmyristPanelShape,
        colors = ButtonDefaults.buttonColors(containerColor = ArmyristColors.PrimaryControl)
    ) {
        Text(
            when (state) {
                VoiceUiState.LISTENING -> "듣는 중... · 눌러서 종료"
                VoiceUiState.RECOGNIZING -> "인식 중..."
                VoiceUiState.STRUCTURING -> "정리 중..."
                VoiceUiState.UNAVAILABLE -> "음성 입력 사용 불가"
                else -> "음성 입력"
            },
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CountingVoiceReviewDialog(
    initial: List<CountingVoiceDraft>,
    onDismiss: () -> Unit,
    onApply: (List<CountingVoiceDraft>) -> Unit
) {
    var drafts by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth().fillMaxHeight(.86f), shape = ArmyristPanelShape) {
            Column(Modifier.padding(14.dp)) {
                Text("음성 입력 검토", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("적용 전에는 기존 실셈 데이터가 변경되지 않습니다.", style = MaterialTheme.typography.bodySmall, color = ArmyristColors.SecondaryText)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(drafts.indices.toList(), key = { it }) { i ->
                        val d = drafts[i]
                        Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, if (d.state == DraftState.READY) ArmyristColors.Border else ArmyristColors.PrimaryControl)) {
                            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(d.name, { v -> drafts = drafts.toMutableList().also { it[i] = d.copy(name = v) } }, label = { Text("품명") }, modifier = Modifier.fillMaxWidth())
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(d.quantity?.toString().orEmpty(), { v ->
                                        val q = v.filter(Char::isDigit).toIntOrNull()
                                        drafts = drafts.toMutableList().also { it[i] = d.copy(quantity = q, state = if (q != null && !d.unit.isNullOrBlank()) DraftState.READY else DraftState.REVIEW_REQUIRED) }
                                    }, label = { Text("수량") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                                    OutlinedTextField(d.unit.orEmpty(), { v ->
                                        drafts = drafts.toMutableList().also { it[i] = d.copy(unit = v, state = if (d.quantity != null && v.isNotBlank()) DraftState.READY else DraftState.REVIEW_REQUIRED) }
                                    }, label = { Text("단위") }, modifier = Modifier.weight(1f))
                                }
                                TextButton(onClick = { drafts = drafts.filterIndexed { idx, _ -> idx != i } }) { Text("삭제") }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("전체 취소") }
                    Button(
                        onClick = { onApply(drafts) },
                        enabled = drafts.isNotEmpty() && drafts.all { it.name.isNotBlank() && it.quantity != null && !it.unit.isNullOrBlank() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = ArmyristColors.PrimaryControl)
                    ) { Text("적용") }
                }
            }
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
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth().fillMaxHeight(.82f), shape = ArmyristPanelShape) {
            Column(Modifier.padding(14.dp)) {
                Text("음성 입력 검토", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("적용 시 모두 새 체크 항목으로 추가됩니다. 기존 항목은 수정하지 않습니다.", style = MaterialTheme.typography.bodySmall, color = ArmyristColors.SecondaryText)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(drafts.indices.toList(), key = { it }) { i ->
                        val d = drafts[i]
                        Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, ArmyristColors.Border)) {
                            Column(Modifier.padding(8.dp)) {
                                OutlinedTextField(d.name, { v -> drafts = drafts.toMutableList().also { it[i] = d.copy(name = v) } }, label = { Text("체크 항목") }, modifier = Modifier.fillMaxWidth())
                                TextButton(onClick = { drafts = drafts.filterIndexed { idx, _ -> idx != i } }) { Text("삭제") }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("전체 취소") }
                    Button(onClick = { onApply(drafts) }, enabled = drafts.isNotEmpty() && drafts.all { it.name.isNotBlank() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = ArmyristColors.PrimaryControl)) { Text("적용") }
                }
            }
        }
    }
}
