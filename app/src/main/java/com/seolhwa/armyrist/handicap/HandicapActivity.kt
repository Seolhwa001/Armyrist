package com.seolhwa.armyrist.handicap
import com.seolhwa.armyrist.ArmyristDialog

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
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
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
internal fun HandicapRoot(onClose: () -> Unit) {
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
    var issuerPicker by remember { mutableStateOf(false) }
    var timePicker by remember { mutableStateOf(false) }
    var issuerName by rememberSaveable { mutableStateOf("") }
    var issuerPhone by rememberSaveable { mutableStateOf("") }
    val result = remember(config, draft) { HandicapRules.build(config, draft) }
    Scaffold(
        topBar = {
            Surface(color = ArmyristColors.RaisedSurface) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().height(48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onClose) { Text("뒤로") }
                    Text("핸디캡 상황", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { settings = true }) { Text("설정") }
                    ArmyristHelpButton(ArmyristHelpTopic.HANDICAP)
                }
            }
        },
        bottomBar = {
            Surface(shadowElevation = 5.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { clearOpen = true }) { Text("초기화") }
                    Button(onClick = {
                        if (result.missing.isEmpty()) resultOpen = true
                        else Toast.makeText(context, "선택해주세요: ${result.missing.joinToString(", ")}", Toast.LENGTH_LONG).show()
                    }, modifier = Modifier.weight(1f)) { Text("확인 · 문장 정리", fontWeight = FontWeight.Bold) }
                }
            }
        }
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).padding(horizontal = 10.dp, vertical = 4.dp)) {
            val short = maxHeight < 430.dp
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("부여제대", Modifier.width(58.dp), fontSize = 13.sp, color = Color(0xFF19324F), fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = { issuerPicker = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFFA9BBD2)), colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Color(0xFF19324F)), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                        Text(draft.issuer.name.ifBlank { config.issuer.name.ifBlank { "제대 선택 / 직접입력" } }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("핸디캡", Modifier.width(52.dp), fontSize = 13.sp, color = Color(0xFF19324F), fontWeight = FontWeight.Bold)
                    CompactChip("블랙 ↑ 상급", draft.kind == HandicapKind.BLACK, { update(draft.copy(kind = HandicapKind.BLACK)) }, Modifier.weight(1f), dark = true)
                    CompactChip("화이트 ↓ 하급", draft.kind == HandicapKind.WHITE, { update(draft.copy(kind = HandicapKind.WHITE)) }, Modifier.weight(1f))
                }
                CompactWords(WordCategory.WHO, config.words[WordCategory.WHO].orEmpty(), draft.who, Modifier.weight(1f), short) { update(draft.copy(who = toggled(draft.who, it))) }
                Row(Modifier.fillMaxWidth().height(if (short) 64.dp else 72.dp).background(Color(0xFFEAF1FC), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFCCD9ED), RoundedCornerShape(12.dp)).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("언제", Modifier.width(44.dp), fontSize = 13.sp, color = Color(0xFF19324F), fontWeight = FontWeight.Bold)
                    Column(Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CompactChip("현재시간", false, { update(draft.copy(whenText = HandicapRules.currentTime(LocalDateTime.now()))) })
                            CompactChip("직접입력", false, { manualTime = draft.whenText; timeOpen = true })
                            if (config.words[WordCategory.WHEN].orEmpty().isNotEmpty()) TextButton(onClick = { timePicker = true }, contentPadding = PaddingValues(4.dp)) { Text("등록 시각", fontSize = 12.sp) }
                        }
                        if (!short || draft.whenText.isNotBlank()) Text(draft.whenText.ifBlank { "누른 순간의 시각 기록" }, fontSize = 12.sp, fontWeight = if (draft.whenText.isBlank()) FontWeight.Normal else FontWeight.Bold, color = ArmyristColors.PrimaryControl, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                CompactWords(WordCategory.WHERE, config.words[WordCategory.WHERE].orEmpty(), draft.where, Modifier.weight(1f), short) { update(draft.copy(where = toggled(draft.where, it))) }
                CompactWords(WordCategory.WHAT, config.words[WordCategory.WHAT].orEmpty(), draft.what, Modifier.weight(1f), short) { update(draft.copy(what = toggled(draft.what, it))) }
                CompactWords(WordCategory.HOW, config.words[WordCategory.HOW].orEmpty(), draft.how, Modifier.height(if (short) 48.dp else 56.dp), true) { update(draft.copy(how = toggled(draft.how, it))) }
            }
        }
    }
    if (issuerPicker) ArmyristDialog(onDismissRequest = { issuerPicker = false }, title = { Text("상황부여 제대 선택") }, text = {
        val choices = (listOf(config.issuer) + config.upper + config.lower).filter { it.name.isNotBlank() }.distinctBy { it.name to it.phone }
        Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
            choices.forEach { unit -> TextButton(onClick = { update(draft.copy(issuer = unit)); issuerPicker = false }, modifier = Modifier.fillMaxWidth()) { Text(unit.name) } }
            OutlinedButton(onClick = { issuerName = draft.issuer.name; issuerPhone = draft.issuer.phone; issuerPicker = false; issuerOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("직접 입력") }
        }
    }, confirmButton = { TextButton(onClick = { issuerPicker = false }) { Text("닫기") } })
    if (timePicker) ArmyristDialog(onDismissRequest = { timePicker = false }, title = { Text("등록된 시각") }, text = {
        Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
            config.words[WordCategory.WHEN].orEmpty().forEach { word -> TextButton(onClick = { update(draft.copy(whenText = word.text)); timePicker = false }, modifier = Modifier.fillMaxWidth()) { Text(word.text) } }
        }
    }, confirmButton = { TextButton(onClick = { timePicker = false }) { Text("닫기") } })
    if (timeOpen) ArmyristDialog(onDismissRequest = { timeOpen = false }, title = { Text("발생 시각 직접입력") }, text = {
        OutlinedTextField(manualTime, { manualTime = it.take(120) }, label = { Text("예: 오늘 18시 30분 / 10월 21일 09:00") }, modifier = Modifier.fillMaxWidth())
    }, confirmButton = { TextButton(enabled = manualTime.isNotBlank(), onClick = { update(draft.copy(whenText = manualTime.trim())); timeOpen = false }) { Text("적용") } }, dismissButton = { TextButton(onClick = { timeOpen = false }) { Text("취소") } })
    if (issuerOpen) ArmyristDialog(onDismissRequest = { issuerOpen = false }, title = { Text("상황부여 제대") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(issuerName, { issuerName = it.take(60) }, label = { Text("제대명") }, singleLine = true)
            OutlinedTextField(issuerPhone, { issuerPhone = it.take(40) }, label = { Text("전화번호 (선택)") }, singleLine = true)
        }
    }, confirmButton = { TextButton(enabled = issuerName.isNotBlank(), onClick = { update(draft.copy(issuer = UnitContact(issuerName.trim(), issuerPhone.trim()))); issuerOpen = false }) { Text("적용") } }, dismissButton = { TextButton(onClick = { issuerOpen = false }) { Text("취소") } })
    if (clearOpen) ArmyristDialog(onDismissRequest = { clearOpen = false }, title = { Text("새 상황을 입력할까요?") }, text = { Text("선택한 단어와 발생 시각을 지웁니다. 제대 설정과 단어 버튼은 유지합니다.") },
        confirmButton = { TextButton(onClick = { update(SituationDraft()); resultOpen = false; clearOpen = false }) { Text("초기화") } }, dismissButton = { TextButton(onClick = { clearOpen = false }) { Text("취소") } })
    if (resultOpen) SituationResultDialog(
        kind = draft.kind ?: HandicapKind.BLACK,
        sentence = result.sentence,
        recipient = result.recipient,
        route = result.route,
        onDismiss = { resultOpen = false },
        onCopy = {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("핸디캡 상황", result.sentence + "\n" + result.route + "\n" + result.recipient.name + " " + result.recipient.phone))
            Toast.makeText(context, "정리 내용을 복사했습니다.", Toast.LENGTH_SHORT).show()
        },
        onCall = {
            runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(result.recipient.phone)}"))) }
                .onFailure { Toast.makeText(context, "전화 앱을 열 수 없습니다.", Toast.LENGTH_LONG).show() }
        }
    )
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
    Scaffold(topBar = { TopAppBar(title = { Text("핸디캡 설정") }, navigationIcon = { TextButton(onClick = { back() }) { Text("뒤로") } }, actions = { TextButton(onClick = { onSave(config) }) { Text("저장") }; ArmyristHelpButton(ArmyristHelpTopic.HANDICAP) }) }) { padding ->
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
        ArmyristDialog(onDismissRequest = { editingCategory = null }, title = { Text("${category.title} 항목 ${if (editingId == null) "추가" else "수정"}") }, text = {
            OutlinedTextField(wordText, { wordText = it.take(80) }, label = { Text("버튼에 표시할 단어") }, isError = duplicate, supportingText = { if (duplicate) Text("같은 이름의 항목이 있습니다.") })
        }, confirmButton = { TextButton(enabled = wordText.isNotBlank() && !duplicate, onClick = {
            val words = config.words[category].orEmpty().toMutableList()
            val index = words.indexOfFirst { it.id == editingId }
            val value = SituationWord(editingId ?: UUID.randomUUID().toString(), wordText.trim())
            if (index < 0) words.add(value) else words[index] = value
            change(config.copy(words = config.words + (category to words))); editingCategory = null
        }) { Text("적용") } }, dismissButton = { TextButton(onClick = { editingCategory = null }) { Text("취소") } })
    }
    deleteWord?.let { (category, word) -> ArmyristDialog(onDismissRequest = { deleteWord = null }, title = { Text("버튼을 삭제할까요?") }, text = { Text(word.text) }, confirmButton = {
        TextButton(onClick = { change(config.copy(words = config.words + (category to config.words[category].orEmpty().filter { it.id != word.id }))); deleteWord = null }) { Text("삭제") }
    }, dismissButton = { TextButton(onClick = { deleteWord = null }) { Text("취소") } }) }
    if (discard) ArmyristDialog(onDismissRequest = { discard = false }, title = { Text("저장하지 않고 나갈까요?") }, text = { Text("수정한 설정은 저장되지 않습니다.") }, confirmButton = { TextButton(onClick = onBack) { Text("나가기") } }, dismissButton = { TextButton(onClick = { discard = false }) { Text("계속 편집") } })
}
@Composable
private fun UnitFields(value: UnitContact, onChange: (UnitContact) -> Unit) {
    OutlinedTextField(value.name, { onChange(value.copy(name = it.take(60))) }, label = { Text("제대명") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value.phone, { onChange(value.copy(phone = it.take(40))) }, label = { Text("전화번호 (선택)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

// A check mark, strong fill and border make selection readable without relying on color alone.
@Composable
private fun CompactChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, dark: Boolean = false) {
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 36.dp).semantics { this.selected = selected }, shape = RoundedCornerShape(10.dp),
        color = if (selected && dark) Color(0xFF202B3D) else if (selected) Color(0xFF245DCF) else Color.White,
        contentColor = if (selected) Color.White else Color(0xFF26364D),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected && dark) Color(0xFF202B3D) else if (selected) Color(0xFF245DCF) else Color(0xFFCBD5E1))) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (selected) Text("✓", Modifier.padding(end = 4.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
internal fun SituationResultDialog(kind: HandicapKind, sentence: String, recipient: UnitContact, route: String, onDismiss: () -> Unit, onCopy: () -> Unit, onCall: () -> Unit) {
    val accent = Color(0xFF245DCF)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 24.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = maxHeight), shape = RoundedCornerShape(24.dp), color = Color.White, contentColor = Color(0xFF17253A)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(color = if (kind == HandicapKind.BLACK) Color(0xFF202B3D) else Color(0xFFE8EFFD), contentColor = if (kind == HandicapKind.BLACK) Color.White else accent, shape = RoundedCornerShape(8.dp)) {
                            Text(if (kind == HandicapKind.BLACK) "블랙 ↑" else "화이트 ↓", Modifier.padding(horizontal = 10.dp, vertical = 7.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Text("상황 정리", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(color = Color(0xFFF1F5FB), shape = RoundedCornerShape(14.dp)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("정리된 문장", fontSize = 12.sp, color = Color(0xFF52647C), fontWeight = FontWeight.Bold)
                                SelectionContainer { Text(sentence, fontSize = 18.sp, lineHeight = 29.sp, fontWeight = FontWeight.Medium) }
                            }
                        }
                        Surface(color = Color(0xFFEFF5FF), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFFC7D8FC))) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("연락할 제대", fontSize = 12.sp, color = accent, fontWeight = FontWeight.Bold)
                                Text(recipient.name.ifBlank { "제대 미등록" }, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
                                Text(route, fontSize = 13.sp, lineHeight = 19.sp, color = Color(0xFF415675))
                                Text(recipient.phone.ifBlank { "전화번호 미등록 · 설정에서 추가하세요" }, fontSize = if (recipient.phone.isBlank()) 13.sp else 19.sp, fontWeight = FontWeight.SemiBold, color = if (recipient.phone.isBlank()) Color(0xFF52647C) else accent)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 6.dp)) { Text("내용 복사", fontWeight = FontWeight.Bold) }
                        Button(enabled = recipient.phone.isNotBlank(), onClick = onCall, modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 6.dp), colors = ButtonDefaults.buttonColors(containerColor = accent)) { Text("전화 앱 열기", fontWeight = FontWeight.Bold) }
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("돌아가서 수정", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
private fun CompactWords(category: WordCategory, words: List<SituationWord>, selected: List<String>, modifier: Modifier, oneRow: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val all = (words.map { it.text } + selected).distinct()
    Row(modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFD3DEEC), RoundedCornerShape(12.dp)).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Column(Modifier.width(44.dp)) {
            Text(category.title, fontSize = 13.sp, color = Color(0xFF19324F), fontWeight = FontWeight.Bold)
            TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(25.dp)) { Text("전체", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
        }
        if (all.isEmpty()) TextButton(onClick = { expanded = true }) { Text("등록 항목 없음", fontSize = 12.sp) }
        else LazyHorizontalGrid(rows = GridCells.Fixed(if (oneRow) 1 else 2), modifier = Modifier.weight(1f).fillMaxHeight().padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            items(all, key = { it }) { text ->
                Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                    CompactChip(text, text in selected, { onSelect(text) }, Modifier.widthIn(min = 76.dp, max = 168.dp).heightIn(max = 44.dp))
                }
            }
        }
    }
    if (expanded) ArmyristDialog(onDismissRequest = { expanded = false }, title = { Text("${category.title} · 여러 개 선택") }, text = {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            all.forEach { text -> FilterChip(selected = text in selected, onClick = { onSelect(text) }, label = { Text(text) }) }
            if (all.isEmpty()) Text("설정에서 항목을 추가해주세요.")
        }
    }, confirmButton = { TextButton(onClick = { expanded = false }) { Text("완료") } })
}
