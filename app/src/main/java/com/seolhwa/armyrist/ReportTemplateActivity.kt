package com.seolhwa.armyrist

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.seolhwa.armyrist.stage2.data.CoreSuiteRepository
import com.seolhwa.armyrist.stage2.domain.ReportTemplate

class ReportTemplateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = (application as ArmyristApplication).coreSuiteRepository
        setContent {
            ArmyristTheme {
                Surface(Modifier.fillMaxSize(), color = ArmyristColors.AppBackground) {
                    ReportTemplateApp(repo) { finish() }
                }
            }
        }
    }
}

@Composable
private fun ReportTemplateApp(repo: CoreSuiteRepository, onHome: () -> Unit) {
    var revision by remember { mutableIntStateOf(0) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val templates = remember(revision) { repo.getReportTemplates() }
    val editing = editingId?.let(repo::getReportTemplate)

    BackHandler {
        when {
            editingId != null -> editingId = null
            creating -> creating = false
            else -> onHome()
        }
    }

    if (editing != null || creating) {
        TemplateEditor(
            template = editing,
            onHome = onHome,
            onBack = { editingId = null; creating = false },
            onSave = { name, body ->
                val ok = if (editing == null) repo.createReportTemplate(name, body) != null
                else repo.updateReportTemplate(editing.id, name, body)
                if (ok) {
                    revision++
                    editingId = null
                    creating = false
                }
                ok
            }
        )
    } else {
        TemplateList(
            templates = templates,
            onHome = onHome,
            onCreate = { creating = true },
            onOpen = { editingId = it },
            onDefault = { repo.setDefaultTemplate(it); revision++ },
            onUnsetDefault = { repo.setDefaultTemplate(null); revision++ },
            onDelete = { repo.deleteReportTemplate(it); revision++ }
        )
    }
}

@Composable
private fun TemplateList(
    templates: List<ReportTemplate>,
    onHome: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    onDefault: (String) -> Unit,
    onUnsetDefault: () -> Unit,
    onDelete: (String) -> Unit
) {
    val context = LocalContext.current
    Scaffold(
        containerColor = ArmyristColors.AppBackground,
        topBar = {
            ArmyristTopBar(
                title = "보고 양식",
                subtitle = "REPORT TEMPLATE · AUTO SAVE",
                leadingIcon = ArmyristTopBarLeadingIcon.HOME,
                onLeading = onHome
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                shape = ArmyristPanelShape,
                containerColor = ArmyristColors.PrimaryControl,
                contentColor = ArmyristColors.OnDark,
                text = { Text("새 보고 양식", fontWeight = FontWeight.Bold) },
                icon = { Text("+", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        if (templates.isEmpty()) {
            ArmyristPanel(
                modifier = Modifier.padding(padding).padding(20.dp).fillMaxWidth()
            ) {
                Text("등록된 보고 양식이 없습니다.", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("{사용자}, {제목}, {전달내용}, {날짜}, {시간} 변수를 사용할 수 있습니다.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(templates, key = { it.id }) { template ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(template.id) },
                        shape = ArmyristPanelShape,
                        colors = CardDefaults.cardColors(containerColor = ArmyristColors.RaisedSurface),
                        border = BorderStroke(1.dp, ArmyristColors.Border)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(template.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                if (template.isDefault) {
                                    AssistChip(onClick = {}, label = { Text("기본") })
                                }
                            }
                            Text(template.body.ifBlank { "내용 없음" }, maxLines = 3)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = {
                                    if (template.isDefault) onUnsetDefault() else onDefault(template.id)
                                }) { Text(if (template.isDefault) "기본 해제" else "기본 지정") }

                                ArmyristUtilityActionButton(
                                    text = "데이터 전달",
                                    onClick = {
                                        context.startActivity(
                                            android.content.Intent(context, PortableTransferActivity::class.java).apply {
                                                putExtra(PortableTransferActivity.EXTRA_MODE, PortableTransferActivity.MODE_EXPORT)
                                                putExtra(PortableTransferActivity.EXTRA_TYPE, ArmyristPortableDataType.REPORT_TEMPLATE.name)
                                                putExtra(PortableTransferActivity.EXTRA_ROOT_ID, template.id)
                                            }
                                        )
                                    }
                                )
                                TextButton(onClick = { onDelete(template.id) }) {
                                    Text("삭제", color = ArmyristColors.Danger)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateEditor(
    template: ReportTemplate?,
    onHome: () -> Unit,
    onBack: () -> Unit,
    onSave: (String, String) -> Boolean
) {
    val context = LocalContext.current
    var name by remember(template?.id) { mutableStateOf(template?.name ?: "") }
    var body by remember(template?.id) {
        val initial = template?.body ?: ""
        mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
    }
    var error by remember { mutableStateOf("") }

    fun insertToken(token: String) {
        val start = body.selection.min.coerceIn(0, body.text.length)
        val end = body.selection.max.coerceIn(0, body.text.length)
        val newText = body.text.substring(0, start) + token + body.text.substring(end)
        body = TextFieldValue(newText, TextRange(start + token.length))
    }

    Scaffold(
        containerColor = ArmyristColors.AppBackground,
        topBar = {
            ArmyristTopBar(
                title = if (template == null) "새 보고 양식" else "보고 양식 편집",
                subtitle = "REPORT TEMPLATE · EDIT",
                leadingIcon = ArmyristTopBarLeadingIcon.HOME,
                onLeading = onHome,
                secondaryLeadingLabel = "목록",
                onSecondaryLeading = onBack
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ArmyristPanel(modifier = Modifier.fillMaxWidth()) {
                Text("양식 정보", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = "" },
                    label = { Text("양식 이름") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = ArmyristControlShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ArmyristColors.InputSurface,
                        unfocusedContainerColor = ArmyristColors.InputSurface
                    )
                )
            }

            ArmyristPanel(modifier = Modifier.fillMaxWidth()) {
                Text("지원 변수", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "누르면 현재 커서 위치에 삽입됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ArmyristColors.SecondaryText
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("{사용자}", "{제목}", "{전달내용}", "{날짜}", "{시간}").forEach { token ->
                        AssistChip(
                            onClick = { insertToken(token) },
                            label = { Text(token, fontWeight = FontWeight.SemiBold) },
                            shape = ArmyristControlShape,
                            border = BorderStroke(1.dp, ArmyristColors.SoftBorder),
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = ArmyristColors.RaisedSurface,
                                labelColor = ArmyristColors.PrimaryText
                            )
                        )
                    }
                }
            }

            ArmyristPanel(modifier = Modifier.fillMaxWidth()) {
                Text("보고 양식", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    minLines = 9,
                    modifier = Modifier.fillMaxWidth(),
                    shape = ArmyristControlShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ArmyristColors.InputSurface,
                        unfocusedContainerColor = ArmyristColors.InputSurface
                    )
                )
                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (name.trim().isEmpty()) error = "양식 이름을 입력하세요."
                        else if (onSave(name, body.text)) {
                            Toast.makeText(context, "저장되었습니다.", Toast.LENGTH_SHORT).show()
                        } else error = "저장할 수 없습니다."
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    shape = ArmyristPanelShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ArmyristColors.PrimaryControl,
                        contentColor = ArmyristColors.OnDark
                    )
                ) {
                    Text("저장", fontWeight = FontWeight.Bold)
                }
            }

            Text(
                "예: 충성! {사용자}입니다.\n\n{전달내용}\n\n{날짜} {시간}",
                style = MaterialTheme.typography.bodySmall,
                color = ArmyristColors.SecondaryText
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
