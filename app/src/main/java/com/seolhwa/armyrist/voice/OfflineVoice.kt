package com.seolhwa.armyrist.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
import androidx.core.content.ContextCompat
import com.seolhwa.armyrist.ArmyristColors
import com.seolhwa.armyrist.ArmyristPanelShape

/** No network fallback is ever used. On-device SpeechRecognizer only. */
enum class VoiceUiState { IDLE, LISTENING, RECOGNIZING, STRUCTURING, REVIEW, ERROR, UNAVAILABLE }

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

class OfflineSpeechSession(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var active = false
    private var finishRequested = false
    private var restartPending = false
    private var cycleGeneration = 0L
    private val transcriptParts = mutableListOf<String>()
    private var stateCallback: ((VoiceUiState) -> Unit)? = null
    private var transcriptCallback: ((String) -> Unit)? = null
    private var errorCallback: ((String) -> Unit)? = null

    fun available(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching {
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            }.getOrDefault(false)

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
        destroy()
        transcriptParts.clear()
        active = true
        finishRequested = false
        stateCallback = onState
        transcriptCallback = onTranscript
        errorCallback = onError
        scheduleRecognizerCycle(0L, "session-start")
    }

    /**
     * One Android SpeechRecognizer cycle is not the Armyrist user voice session.
     * A new cycle is posted after the previous callback has unwound so OEM recognizers
     * are not destroyed/recreated synchronously inside onResults()/onError().
     */
    private fun scheduleRecognizerCycle(delayMs: Long, reason: String) {
        if (!active || finishRequested || restartPending) return
        restartPending = true
        val expectedGeneration = ++cycleGeneration
        Log.d(TAG, "voice restart scheduled reason=$reason generation=$expectedGeneration hasTranscript=${transcriptParts.isNotEmpty()} userFinish=$finishRequested")
        mainHandler.postDelayed({
            restartPending = false
            if (!active || finishRequested || expectedGeneration != cycleGeneration) return@postDelayed
            startRecognizerCycle(expectedGeneration)
        }, delayMs)
    }

    private fun startRecognizerCycle(generation: Long) {
        if (!active || finishRequested || generation != cycleGeneration) return
        recognizer?.destroy()
        recognizer = null
        val sr = runCatching {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        }.getOrNull()
        if (sr == null) {
            fail("이 기기에서 오프라인 음성인식을 시작할 수 없습니다.", unavailable = true)
            return
        }
        recognizer = sr
        Log.d(TAG, "voice cycle start generation=$generation hasTranscript=${transcriptParts.isNotEmpty()} userFinish=$finishRequested")
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (isCurrentCycle(sr, generation)) stateCallback?.invoke(VoiceUiState.LISTENING)
            }
            override fun onBeginningOfSpeech() {
                if (isCurrentCycle(sr, generation)) stateCallback?.invoke(VoiceUiState.LISTENING)
            }
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() {
                if (isCurrentCycle(sr, generation)) stateCallback?.invoke(VoiceUiState.RECOGNIZING)
            }
            override fun onError(error: Int) {
                if (!isCurrentCycle(sr, generation)) return
                Log.w(TAG, "voice callback=onError errorCode=$error generation=$generation sessionActive=$active userFinish=$finishRequested hasTranscript=${transcriptParts.isNotEmpty()} restartPending=$restartPending")
                releaseCycle(sr)
                if (!active) return
                if (finishRequested) {
                    finishWithAccumulatedTranscript()
                    return
                }
                if (isRecoverableError(error)) {
                    // Do not turn a recoverable Android recognition-cycle failure into a
                    // user-session failure. Back off slightly for BUSY/CLIENT style races.
                    val delay = when (error) {
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> 600L
                        else -> 250L
                    }
                    stateCallback?.invoke(VoiceUiState.LISTENING)
                    scheduleRecognizerCycle(delay, "recoverable-error-$error")
                } else {
                    fail("음성인식에 실패했습니다. 다시 시도해주세요. (오류 $error)")
                }
            }
            override fun onResults(results: Bundle?) {
                if (!isCurrentCycle(sr, generation)) return
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                if (!text.isNullOrEmpty()) transcriptParts += text
                Log.d(TAG, "voice callback=onResults generation=$generation resultPresent=${!text.isNullOrEmpty()} accumulatedParts=${transcriptParts.size} userFinish=$finishRequested")
                releaseCycle(sr)
                if (!active) return
                if (finishRequested) {
                    finishWithAccumulatedTranscript()
                } else {
                    stateCallback?.invoke(VoiceUiState.LISTENING)
                    scheduleRecognizerCycle(250L, "results-complete")
                }
            }
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        runCatching {
            sr.startListening(android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            })
        }.onFailure {
            Log.e(TAG, "voice startListening failed generation=$generation", it)
            releaseCycle(sr)
            if (active && !finishRequested) scheduleRecognizerCycle(600L, "startListening-exception")
        }
    }

    private fun isCurrentCycle(sr: SpeechRecognizer, generation: Long): Boolean =
        active && recognizer === sr && generation == cycleGeneration

    private fun releaseCycle(sr: SpeechRecognizer) {
        if (recognizer === sr) recognizer = null
        runCatching { sr.destroy() }
    }

    private fun isRecoverableError(error: Int): Boolean = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
        SpeechRecognizer.ERROR_CLIENT -> true
        else -> false
    }

    /** Explicit user finish. No later recognition cycle may be started. */
    fun finish() {
        if (!active) return
        finishRequested = true
        restartPending = false
        mainHandler.removeCallbacksAndMessages(null)
        stateCallback?.invoke(VoiceUiState.RECOGNIZING)
        val current = recognizer
        if (current != null) {
            runCatching { current.stopListening() }
                .onFailure {
                    Log.w(TAG, "voice stopListening failed; finalizing accumulated transcript", it)
                    releaseCycle(current)
                    finishWithAccumulatedTranscript()
                }
        } else {
            cycleGeneration++ // invalidate a posted cycle when no recognizer is active
            finishWithAccumulatedTranscript()
        }
    }

    private fun finishWithAccumulatedTranscript() {
        if (!active) return
        // Preserve recognition-cycle boundaries as structuring hints.
        // A cycle is NOT forced to equal one Domain item; tool structurers may still
        // split each segment further by punctuation/grammar.
        val fullTranscript = transcriptParts.joinToString("\n").trim()
        active = false
        restartPending = false
        mainHandler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        if (fullTranscript.isEmpty()) {
            stateCallback?.invoke(VoiceUiState.ERROR)
            errorCallback?.invoke("인식된 음성이 없습니다.")
        } else {
            stateCallback?.invoke(VoiceUiState.STRUCTURING)
            transcriptCallback?.invoke(fullTranscript)
        }
    }

    private fun fail(message: String, unavailable: Boolean = false) {
        active = false
        finishRequested = false
        restartPending = false
        cycleGeneration++
        mainHandler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        Log.e(TAG, "voice session failed unavailable=$unavailable hasTranscript=${transcriptParts.isNotEmpty()}")
        stateCallback?.invoke(if (unavailable) VoiceUiState.UNAVAILABLE else VoiceUiState.ERROR)
        errorCallback?.invoke(message)
    }

    fun cancel() {
        active = false
        finishRequested = false
        restartPending = false
        cycleGeneration++
        mainHandler.removeCallbacksAndMessages(null)
        recognizer?.cancel()
        destroy()
    }

    fun destroy() {
        active = false
        finishRequested = false
        restartPending = false
        cycleGeneration++
        mainHandler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        transcriptParts.clear()
        stateCallback = null
        transcriptCallback = null
        errorCallback = null
    }

    companion object {
        private const val TAG = "ArmyristVoice"
    }
}

@Composable
fun OfflineVoiceButton(
    modifier: Modifier = Modifier,
    toolContext: VoiceToolContext = VoiceToolContext.GENERIC,
    onTranscript: (String) -> Unit,
    onStateChanged: (VoiceUiState) -> Unit = {},
    onMessage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val session = remember { OfflineSpeechSession(context.applicationContext) }
    var state by remember { mutableStateOf(if (session.available()) VoiceUiState.IDLE else VoiceUiState.UNAVAILABLE) }
    var recordingScreenVisible by remember { mutableStateOf(false) }

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
            }
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

    Button(
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
        colors = ButtonDefaults.buttonColors(containerColor = ArmyristColors.PrimaryControl)
    ) {
        Text(
            when (state) {
                VoiceUiState.RECOGNIZING -> "인식 중..."
                VoiceUiState.STRUCTURING -> "정리 중..."
                VoiceUiState.UNAVAILABLE -> "음성 입력 사용 불가"
                else -> "음성 입력"
            },
            fontWeight = FontWeight.Bold
        )
    }

    if (recordingScreenVisible) {
        VoiceRecordingScreen(
            toolContext = toolContext,
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
private fun VoiceRecordingScreen(
    toolContext: VoiceToolContext,
    state: VoiceUiState,
    onFinish: () -> Unit,
    onCancel: () -> Unit
) {
    val guide = remember(toolContext) { guideFor(toolContext) }

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
                            VoiceUiState.RECOGNIZING -> "인식 중..."
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
                                    "말할 수 있어요 (예시)",
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

                    Spacer(Modifier.weight(1f))

                    Button(
                        onClick = onFinish,
                        enabled = state == VoiceUiState.LISTENING || state == VoiceUiState.RECOGNIZING,
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
fun CountingVoiceReviewDialog(
    initial: List<CountingVoiceDraft>,
    onDismiss: () -> Unit,
    onApply: (List<CountingVoiceDraft>) -> Unit
) {
    var drafts by remember { mutableStateOf(initial) }

    fun update(index: Int, draft: CountingVoiceDraft) {
        drafts = drafts.toMutableList().also { it[index] = KoreanVoiceStructurer.revalidate(draft) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth().fillMaxHeight(.88f).imePadding(), shape = ArmyristPanelShape) {
            Column(Modifier.padding(14.dp)) {
                Text("음성 입력 검토", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "아래 Draft를 확인하고 필요한 항목을 수정하세요. 적용 전에는 기존 실셈 데이터가 변경되지 않습니다.",
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
        Card(Modifier.fillMaxWidth().fillMaxHeight(.84f).imePadding(), shape = ArmyristPanelShape) {
            Column(Modifier.padding(14.dp)) {
                Text("음성 입력 검토", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "아래 Draft를 확인하고 필요한 항목을 수정하세요. 기존 항목은 적용 전까지 변경되지 않습니다.",
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
                    ) { Text("적용") }
                }
            }
        }
    }
}
