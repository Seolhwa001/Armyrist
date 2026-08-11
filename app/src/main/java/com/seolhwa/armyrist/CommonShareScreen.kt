@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.seolhwa.armyrist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.seolhwa.armyrist.stage2.data.CoreSuiteRepository
import com.seolhwa.armyrist.stage2.domain.ReportTemplate
import com.seolhwa.armyrist.stage2.domain.ToolResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val NONE_TEMPLATE = "__NONE__"

private fun applyReportTemplate(
    template: ReportTemplate?,
    result: ToolResult,
    userName: String,
    now: Date = Date()
): String {
    if (template == null) return result.body

    val values = mapOf(
        "{사용자}" to userName,
        "{제목}" to result.title,
        "{전달내용}" to result.body,
        "{날짜}" to SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now),
        "{시간}" to SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
    )

    // Single-pass token replacement: replacement values are never interpreted again.
    val regex = Regex("""\{사용자\}|\{제목\}|\{전달내용\}|\{날짜\}|\{시간\}""")
    return regex.replace(template.body) { match ->
        values[match.value] ?: match.value
    }
}

@Composable
fun CommonShareScreen(
    repo: CoreSuiteRepository,
    result: ToolResult,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val templates = remember { repo.getReportTemplates() }
    val default = templates.firstOrNull { it.isDefault }
    var selectedId by remember { mutableStateOf(default?.id ?: NONE_TEMPLATE) }
    val selected =
        templates.firstOrNull { it.id == selectedId }

    // One report snapshot per explicit selection/result state.
    // Preview, clipboard and Android Share all use this exact text.
    val capturedAt =
        remember(selectedId, result) {
            Date()
        }

    val capturedUserName =
        remember(selectedId, result) {
            repo.getUserProfile().displayName
        }

    val finalText =
        remember(
            selectedId,
            result,
            templates,
            capturedAt,
            capturedUserName
        ) {
            applyReportTemplate(
                template = selected,
                result = result,
                userName = capturedUserName,
                now = capturedAt
            )
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("결과") },
                navigationIcon = { TextButton(onClick = onBack) { Text("뒤로") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("보고 양식", style = MaterialTheme.typography.titleMedium)

            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(selected?.name ?: "양식 없음")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("양식 없음") },
                        onClick = { selectedId = NONE_TEMPLATE; expanded = false }
                    )
                    templates.forEach { template ->
                        DropdownMenuItem(
                            text = { Text(if (template.isDefault) "${template.name} · 기본" else template.name) },
                            onClick = { selectedId = template.id; expanded = false }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = finalText,
                onValueChange = {},
                readOnly = true,
                label = { Text("미리보기") },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Armyrist result", finalText))
                        Toast.makeText(context, "복사되었습니다.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("복사") }

                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, finalText)
                        }
                        context.startActivity(Intent.createChooser(intent, "공유"))
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("공유") }
            }
        }
    }
}
