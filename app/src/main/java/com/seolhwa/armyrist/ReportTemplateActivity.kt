package com.seolhwa.armyrist

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
                Surface(
                    Modifier.fillMaxSize(),
                    color = ArmyristColors.AppBackground
                ) {
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
                val ok = if (editing == null) {
                    repo.createReportTemplate(name, body) != null
                } else {
                    repo.updateReportTemplate(editing.id, name, body)
                }
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
            onDefault = {
                repo.setDefaultTemplate(it)
                revision++
            },
            onUnsetDefault = {
                repo.setDefaultTemplate(null)
                revision++
            },
            onDelete = {
                repo.deleteReportTemplate(it)
                revision++
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
        topBar = {
            ArmyristTopBar(
                title = "보고 양식",
                subtitle = "REPORT TEMPLATE · AUTO SAVE",
                leadingLabel = "홈",
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
            Column(Modifier.padding(padding).padding(20.dp)) {
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
                        colors = CardDefaults.cardColors(
                            containerColor = ArmyristColors.RaisedSurface
                        ),
                        border = BorderStroke(1.dp, ArmyristColors.Border)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(template.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                if (template.isDefault) AssistChip(onClick = {}, label = { Text("기본") })
                            }
                            Text(
                                template.body.ifBlank { "내용 없음" },
                                maxLines = 3,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (template.isDefault) {
                                    TextButton(onClick = onUnsetDefault) { Text("기본 해제") }
                                } else {
                                    TextButton(onClick = { onDefault(template.id) }) { Text("기본 지정") }
                                }
                                ArmyristUtilityActionButton(
                                    text = "데이터 전달",
                                    onClick = {
                                        context.startActivity(
                                            android.content.Intent(
                                                context,
                                                PortableTransferActivity::class.java
                                            ).apply {
                                                putExtra(
                                                    PortableTransferActivity.EXTRA_MODE,
                                                    PortableTransferActivity.MODE_EXPORT
                                                )
                                                putExtra(
                                                    PortableTransferActivity.EXTRA_TYPE,
                                                    ArmyristPortableDataType.REPORT_TEMPLATE.name
                                                )
                                                putExtra(
                                                    PortableTransferActivity.EXTRA_ROOT_ID,
                                                    template.id
                                                )
                                            }
                                        )
                                    }
                                )
                                TextButton(onClick = { onDelete(template.id) }) { Text("삭제") }
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
    var name by remember(template?.id) {
        mutableStateOf(template?.name ?: "")
    }
    var body by remember(template?.id) {
        val initial = template?.body ?: ""
        mutableStateOf(
            TextFieldValue(
                text = initial,
                selection = TextRange(initial.length)
            )
        )
    }
    var error by remember { mutableStateOf("") }

    fun insertToken(token: String) {
        val start =
            body.selection.min.coerceIn(0, body.text.length)
        val end =
            body.selection.max.coerceIn(0, body.text.length)

        val newText =
            body.text.substring(0, start) +
                token +
                body.text.substring(end)

        body = TextFieldValue(
            text = newText,
            selection = TextRange(start + token.length)
        )
    }

    Scaffold(
        topBar = {
            ArmyristTopBar(
                title =
                    if (template == null) {
                        "새 보고 양식"
                    } else {
                        "보고 양식 편집"
                    },
                subtitle = "REPORT TEMPLATE · EDIT",
                leadingLabel = "홈",
                onLeading = onHome
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onBack,
                shape = ArmyristPanelShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor =
                        ArmyristColors.HeaderRaised,
                    contentColor =
                        ArmyristColors.OnDark
                )
            ) {
                Text(
                    "목록",
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    error = ""
                },
                label = { Text("양식 이름") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                "지원 변수",
                fontWeight = FontWeight.SemiBold
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "{사용자}",
                    "{제목}"
                ).forEach { token ->
                    AssistChip(
                        onClick = { insertToken(token) },
                        label = { Text(token) }
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "{전달내용}",
                    "{날짜}"
                ).forEach { token ->
                    AssistChip(
                        onClick = { insertToken(token) },
                        label = { Text(token) }
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(6.dp)
            ) {
                AssistChip(
                    onClick = { insertToken("{시간}") },
                    label = { Text("{시간}") }
                )
            }

            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("보고 양식") },
                minLines = 8,
                modifier = Modifier.fillMaxWidth()
            )

            if (error.isNotEmpty()) {
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    if (name.trim().isEmpty()) {
                        error = "양식 이름을 입력하세요."
                    } else if (onSave(name, body.text)) {
                        Toast.makeText(
                            context,
                            "저장되었습니다.",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        error = "저장할 수 없습니다."
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = ArmyristPanelShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor =
                        ArmyristColors.PrimaryControl,
                    contentColor =
                        ArmyristColors.OnDark
                )
            ) {
                Text(
                    "저장",
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                "예: 충성! {사용자}입니다.\n\n" +
                    "{전달내용}\n\n{날짜} {시간}",
                style = MaterialTheme.typography.bodySmall,
                color = ArmyristColors.SecondaryText
            )
        }
    }
}
