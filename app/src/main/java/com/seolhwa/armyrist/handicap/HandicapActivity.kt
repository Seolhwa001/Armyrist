package com.seolhwa.armyrist.handicap

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seolhwa.armyrist.*
import java.time.LocalDateTime
import java.util.UUID

class HandicapActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ArmyristTheme { HandicapRoot(onClose = { finish() }) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HandicapRoot(onClose: () -> Unit) {
    val context = LocalContext.current
    val store = remember { HandicapStore(context) }
    var configRaw by remember { mutableStateOf(HandicapCodec.encode(store.loadConfig())) }
    var draftRaw by rememberSaveable { mutableStateOf(HandicapCodec.encodeDraft(store.loadDraft())) }
    var settings by rememberSaveable { mutableStateOf(false) }
    val config = remember(configRaw) { HandicapCodec.decode(configRaw) }
    val draft = remember(draftRaw) { HandicapCodec.decodeDraft(draftRaw) }
    fun update(value: SituationDraft) { draftRaw = HandicapCodec.encodeDraft(value); store.saveDraft(value) }
    if (settings) {
        HandicapSettings(config, onSave = { value ->
            if (store.saveConfig(value)) {
                configRaw = HandicapCodec.encode(value); settings = false
                Toast.makeText(context, "핸디캡 설정을 저장했습니다.", Toast.LENGTH_SHORT).show()
            } else Toast.makeText(context, "저장하지 못했습니다. 다시 시도해주세요.", Toast.LENGTH_LONG).show()
        }, onBack = { settings = false })
        return
    }
    var resultOpen by rememberSaveable { mutableStateOf(false) }
    var clearOpen by remember { mutableStateOf(false) }
    var timeOpen by remember { mutableStateOf(false) }
    var manualTime by rememberSaveable { mutableStateOf("") }
    var issuerOpen by remember { mutableStateOf(false) }
    var issuerName by rememberSaveable { mutableStateOf("") }
    var issuerPhone by rememberSaveable { mutableStateOf("") }
    val result = remember(config, draft) { HandicapRules.build(config, draft) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("핸디캡 상황 정리") }, navigationIcon = {
            TextButton(onClick = onClose) { Text("뒤로") }
        }, actions = { TextButton(onClick = { settings = true }) { Text("설정") } }) },
        bottomBar = {
            Surface(shadowElevation = 5.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { clearOpen = true }) { Text("초기화") }
                    Button(onClick = {
                        if (result.missing.isEmpty()) resultOpen = true
                        else Toast.makeText(context, "선택해주세요: ${result.missing.joinToString(", ")}", Toast.LENGTH_LONG).show()
                    }, modifier = Modifier.weight(1f)) { Text("확인 · 문장 정리") }
                }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("들은 단어를 순서 없이 누르세요. 누가·어디서·무엇을은 여러 개 선택할 수 있습니다.", color = ArmyristColors.SecondaryText, style = MaterialTheme.typography.bodyMedium) }
            item { Section("상황부여 제대") {
                val choices = (listOf(config.issuer) + config.upper + config.lower).filter { it.name.isNotBlank() }.distinctBy { it.name to it.phone }
                Chips {
                    choices.forEach { unit -> FilterChip(selected = draft.issuer == unit, onClick = { update(draft.copy(issuer = unit)) }, label = { Text(unit.name) }) }
                    FilterChip(selected = draft.issuer.name.isNotBlank() && choices.none { it == draft.issuer },
                        onClick = { issuerName = draft.issuer.name; issuerPhone = draft.issuer.phone; issuerOpen = true },
                        label = { Text(if (draft.issuer.name.isNotBlank() && choices.none { it == draft.issuer }) draft.issuer.name else "직접 입력") })
                }
                if (draft.issuer.name.isBlank() && config.issuer.name.isNotBlank()) Text("기본 부여제대: ${config.issuer.name}", style = MaterialTheme.typography.bodySmall)
                if (choices.isEmpty()) Text("직접 입력하거나 설정에서 기본 부여제대를 등록하세요.", style = MaterialTheme.typography.bodySmall)
            } }
            item { Section("핸디캡") {
                Chips {
                    FilterChip(selected = draft.kind == HandicapKind.BLACK, onClick = { update(draft.copy(kind = HandicapKind.BLACK)) },
                        label = { Text("블랙") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF253247), selectedLabelColor = Color.White))
                    FilterChip(selected = draft.kind == HandicapKind.WHITE, onClick = { update(draft.copy(kind = HandicapKind.WHITE)) }, label = { Text("화이트") })
                }
                Text("블랙 → 1차 하급 / 화이트 → 1차 상급\n해당 1차 제대 미등록 시 상황부여 제대", style = MaterialTheme.typography.bodySmall, color = ArmyristColors.SecondaryText)
            } }
            item { WordsSection("누가", config.words[WordCategory.WHO].orEmpty(), draft.who) { text ->
                update(draft.copy(who = toggled(draft.who, text)))
            } }
            item { Section("언제") {
                Chips {
                    FilterChip(selected = false, onClick = { update(draft.copy(whenText = HandicapRules.currentTime(LocalDateTime.now()))) }, label = { Text("현재시간") })
                    FilterChip(selected = timeOpen, onClick = { manualTime = draft.whenText; timeOpen = true }, label = { Text("직접입력") })
                    config.words[WordCategory.WHEN].orEmpty().forEach { word -> FilterChip(selected = draft.whenText == word.text, onClick = { update(draft.copy(whenText = word.text)) }, label = { Text(word.text) }) }
                }
                if (draft.whenText.isNotBlank()) Text("선택 시각: ${draft.whenText}", color = ArmyristColors.PrimaryControl, fontWeight = FontWeight.SemiBold)
                Text("현재시간은 누른 순간으로 기록됩니다.", style = MaterialTheme.typography.bodySmall, color = ArmyristColors.SecondaryText)
            } }
            item { WordsSection("어디서", config.words[WordCategory.WHERE].orEmpty(), draft.where) { text -> update(draft.copy(where = toggled(draft.where, text))) } }
            item { WordsSection("무엇을", config.words[WordCategory.WHAT].orEmpty(), draft.what) { text -> update(draft.copy(what = toggled(draft.what, text))) } }
            item { Text("설정에서 모든 단어 버튼과 제대 연락처를 편집할 수 있습니다. 선택 내용은 다음 초기화 전까지 이 기기에 보관됩니다.", color = ArmyristColors.SecondaryText, style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (timeOpen) AlertDialog(onDismissRequest = { timeOpen = false }, title = { Text("발생 시각 직접입력") }, text = {
        OutlinedTextField(manualTime, { manualTime = it.take(120) }, label = { Text("예: 오늘 18시 30분 / 10월 21일 09:00") }, modifier = Modifier.fillMaxWidth())
    }, confirmButton = { TextButton(enabled = manualTime.isNotBlank(), onClick = { update(draft.copy(whenText = manualTime.trim())); timeOpen = false }) { Text("적용") } }, dismissButton = { TextButton(onClick = { timeOpen = false }) { Text("취소") } })
    if (issuerOpen) AlertDialog(onDismissRequest = { issuerOpen = false }, title = { Text("상황부여 제대") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(issuerName, { issuerName = it.take(60) }, label = { Text("제대명") }, singleLine = true)
            OutlinedTextField(issuerPhone, { issuerPhone = it.take(40) }, label = { Text("전화번호 (선택)") }, singleLine = true)
        }
    }, confirmButton = { TextButton(enabled = issuerName.isNotBlank(), onClick = { update(draft.copy(issuer = UnitContact(issuerName.trim(), issuerPhone.trim()))); issuerOpen = false }) { Text("적용") } }, dismissButton = { TextButton(onClick = { issuerOpen = false }) { Text("취소") } })
    if (clearOpen) AlertDialog(onDismissRequest = { clearOpen = false }, title = { Text("새 상황을 입력할까요?") }, text = { Text("선택한 단어와 발생 시각을 지웁니다. 제대 설정과 단어 버튼은 유지합니다.") },
        confirmButton = { TextButton(onClick = { update(SituationDraft()); resultOpen = false; clearOpen = false }) { Text("초기화") } }, dismissButton = { TextButton(onClick = { clearOpen = false }) { Text("취소") } })
    if (resultOpen) AlertDialog(onDismissRequest = { resultOpen = false }, title = { Text("${if (draft.kind == HandicapKind.BLACK) "블랙" else "화이트"} 상황 정리") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(result.sentence, style = MaterialTheme.typography.bodyLarge)
            HorizontalDivider()
            Text("연락할 제대", color = ArmyristColors.SecondaryText)
            Text(result.recipient.name.ifBlank { "제대 미등록" }, fontWeight = FontWeight.Bold)
            Text(result.route, style = MaterialTheme.typography.bodySmall)
            Text(result.recipient.phone.ifBlank { "전화번호 미등록 · 설정에서 추가하세요" })
            OutlinedButton(onClick = {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("핸디캡 상황", result.sentence + "\n" + result.route + "\n" + result.recipient.name + " " + result.recipient.phone))
                Toast.makeText(context, "정리 내용을 복사했습니다.", Toast.LENGTH_SHORT).show()
            }) { Text("내용 복사") }
            Button(enabled = result.recipient.phone.isNotBlank(), onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(result.recipient.phone)}"))) }
                    .onFailure { Toast.makeText(context, "전화 앱을 열 수 없습니다.", Toast.LENGTH_LONG).show() }
            }) { Text("전화 앱 열기") }
        }
    }, confirmButton = { TextButton(onClick = { resultOpen = false }) { Text("돌아가서 수정") } })
}

private fun toggled(values: List<String>, text: String) = if (text in values) values - text else values + text

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = ArmyristColors.RaisedSurface, shape = ArmyristPanelShape, border = BorderStroke(1.dp, ArmyristColors.SoftBorder)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
}
@Composable
private fun WordsSection(title: String, words: List<SituationWord>, selected: List<String>, onSelect: (String) -> Unit) {
    Section(title) {
        Chips { words.forEach { word -> FilterChip(selected = word.text in selected, onClick = { onSelect(word.text) }, label = { Text(word.text) }) } }
        // Keep selections visible if the user edits the vocabulary while a situation is open.
        val archived = selected.filter { text -> words.none { it.text == text } }
        if (archived.isNotEmpty()) Chips { archived.forEach { text -> FilterChip(selected = true, onClick = { onSelect(text) }, label = { Text("$text (기존 선택)") }) } }
        if (words.isEmpty()) Text("등록된 항목이 없습니다. 설정에서 추가해주세요.", style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HandicapSettings(initial: HandicapConfig, onSave: (HandicapConfig) -> Unit, onBack: () -> Unit) {
    var raw by rememberSaveable { mutableStateOf(HandicapCodec.encode(initial)) }
    val config = remember(raw) { HandicapCodec.decode(raw, normalize = false) }
    fun change(value: HandicapConfig) { raw = HandicapCodec.encode(value, normalize = false) }
    var discard by remember { mutableStateOf(false) }
    fun back() { if (config != initial) discard = true else onBack() }
    BackHandler { back() }
    var editingCategory by remember { mutableStateOf<WordCategory?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var wordText by remember { mutableStateOf("") }
    var deleteWord by remember { mutableStateOf<Pair<WordCategory, SituationWord>?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text("핸디캡 설정") }, navigationIcon = { TextButton(onClick = { back() }) { Text("뒤로") } }, actions = { TextButton(onClick = { onSave(config) }) { Text("저장") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("제대명과 전화번호를 입력하세요. 연락 안내에는 1차 제대를 사용하며 2~4차 제대도 상황부여 제대로 선택할 수 있습니다.", color = ArmyristColors.SecondaryText) }
            item { Section("기본 상황부여 제대") { UnitFields(config.issuer) { change(config.copy(issuer = it)) } } }
            items(4) { index -> Section("${index + 1}차 상급제대") { UnitFields(config.upper[index]) { value -> change(config.copy(upper = config.upper.toMutableList().also { it[index] = value })) } } }
            items(4) { index -> Section("${index + 1}차 하급제대") { UnitFields(config.lower[index]) { value -> change(config.copy(lower = config.lower.toMutableList().also { it[index] = value })) } } }
            WordCategory.entries.forEach { category -> item { Section("${category.title} 버튼 편집") {
                if (category == WordCategory.WHEN) Text("현재시간·직접입력은 항상 제공됩니다. 아래에는 자주 쓰는 시각 표현을 추가하세요.", style = MaterialTheme.typography.bodySmall)
                config.words[category].orEmpty().forEach { word ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(word.text, Modifier.weight(1f).padding(vertical = 12.dp))
                        TextButton(onClick = { editingCategory = category; editingId = word.id; wordText = word.text }) { Text("수정") }
                        TextButton(onClick = { deleteWord = category to word }) { Text("삭제") }
                    }
                }
                OutlinedButton(enabled = config.words[category].orEmpty().size < 100, onClick = { editingCategory = category; editingId = null; wordText = "" }) { Text("${category.title} 항목 추가") }
            } } }
            item { Button(onClick = { onSave(config) }, modifier = Modifier.fillMaxWidth()) { Text("설정 저장") } }
            item { Text("이 설정과 작성 중인 상황은 이 기기에만 저장됩니다. 기존 전체 데이터 백업에는 포함되지 않습니다.", style = MaterialTheme.typography.bodySmall, color = ArmyristColors.SecondaryText) }
        }
    }
    editingCategory?.let { category ->
        val duplicate = config.words[category].orEmpty().any { it.id != editingId && it.text == wordText.trim() }
        AlertDialog(onDismissRequest = { editingCategory = null }, title = { Text("${category.title} 항목 ${if (editingId == null) "추가" else "수정"}") }, text = {
            OutlinedTextField(wordText, { wordText = it.take(80) }, label = { Text("버튼에 표시할 단어") }, isError = duplicate, supportingText = { if (duplicate) Text("같은 이름의 항목이 있습니다.") })
        }, confirmButton = { TextButton(enabled = wordText.isNotBlank() && !duplicate, onClick = {
            val words = config.words[category].orEmpty().toMutableList()
            val index = words.indexOfFirst { it.id == editingId }
            val value = SituationWord(editingId ?: UUID.randomUUID().toString(), wordText.trim())
            if (index < 0) words.add(value) else words[index] = value
            change(config.copy(words = config.words + (category to words))); editingCategory = null
        }) { Text("적용") } }, dismissButton = { TextButton(onClick = { editingCategory = null }) { Text("취소") } })
    }
    deleteWord?.let { (category, word) -> AlertDialog(onDismissRequest = { deleteWord = null }, title = { Text("버튼을 삭제할까요?") }, text = { Text(word.text) }, confirmButton = {
        TextButton(onClick = { change(config.copy(words = config.words + (category to config.words[category].orEmpty().filter { it.id != word.id }))); deleteWord = null }) { Text("삭제") }
    }, dismissButton = { TextButton(onClick = { deleteWord = null }) { Text("취소") } }) }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("저장하지 않고 나갈까요?") }, text = { Text("수정한 설정은 저장되지 않습니다.") }, confirmButton = { TextButton(onClick = onBack) { Text("나가기") } }, dismissButton = { TextButton(onClick = { discard = false }) { Text("계속 편집") } })
}
@Composable
private fun UnitFields(value: UnitContact, onChange: (UnitContact) -> Unit) {
    OutlinedTextField(value.name, { onChange(value.copy(name = it.take(60))) }, label = { Text("제대명") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value.phone, { onChange(value.copy(phone = it.take(40))) }, label = { Text("전화번호 (선택)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
}
