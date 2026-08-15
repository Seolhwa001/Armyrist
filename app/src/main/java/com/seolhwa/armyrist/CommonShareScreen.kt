@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.seolhwa.armyrist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.seolhwa.armyrist.stage2.data.CoreSuiteRepository
import com.seolhwa.armyrist.stage2.domain.ReportTemplate
import com.seolhwa.armyrist.stage2.domain.ToolResult
import java.io.File
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

    val regex = Regex("""\{사용자\}|\{제목\}|\{전달내용\}|\{날짜\}|\{시간\}""")
    return regex.replace(template.body) { match ->
        values[match.value] ?: match.value
    }
}

@Composable
fun CommonShareScreen(
    repo: CoreSuiteRepository,
    result: ToolResult,
    onBack: () -> Unit,
    portableType: ArmyristPortableDataType? = null,
    portableRootId: String? = null
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val templates = remember { repo.getReportTemplates() }
    val default = templates.firstOrNull { it.isDefault }
    var selectedId by rememberSaveable { mutableStateOf(default?.id ?: NONE_TEMPLATE) }
    val selected = templates.firstOrNull { it.id == selectedId }

    var pendingSaveBytes by remember { mutableStateOf<ByteArray?>(null) }
    var encrypt by rememberSaveable { mutableStateOf(false) }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordConfirm by rememberSaveable { mutableStateOf("") }

    val saveFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.armyrist.data")
    ) { uri ->
        val bytes = pendingSaveBytes
        pendingSaveBytes = null
        if (uri != null && bytes != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(bytes)
                } ?: error("openOutputStream failed")
            }.onSuccess {
                Toast.makeText(
                    context,
                    "데이터 파일을 저장했습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure {
                Toast.makeText(
                    context,
                    "데이터 파일을 저장할 수 없습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val capturedAt = remember(selectedId, result) { Date() }
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

    fun createPortableBytes(): ByteArray? {
        val type = portableType
        val rootId = portableRootId
        if (type == null || rootId.isNullOrBlank()) {
            Toast.makeText(
                context,
                "이 화면에서는 데이터 파일을 만들 수 없습니다.",
                Toast.LENGTH_SHORT
            ).show()
            return null
        }

        if (
            encrypt &&
            (
                password.isBlank() ||
                password != passwordConfirm
            )
        ) {
            Toast.makeText(
                context,
                "암호와 암호 확인을 동일하게 입력해주세요.",
                Toast.LENGTH_SHORT
            ).show()
            return null
        }

        return when (
            val generated =
                ArmyristPortableDataManager.createIndividualExport(
                    context = context,
                    dataType = type,
                    rootId = rootId,
                    password =
                        if (encrypt) password.toCharArray()
                        else null
                )
        ) {
            is PortableResult.Success -> generated.value
            is PortableResult.Error -> {
                Toast.makeText(
                    context,
                    generated.message,
                    Toast.LENGTH_LONG
                ).show()
                null
            }
        }
    }

    fun fileName(): String {
        val type = when (portableType) {
            ArmyristPortableDataType.COUNTING -> "counting"
            ArmyristPortableDataType.CHECKLIST -> "checklist"
            ArmyristPortableDataType.TIME_PLAN -> "time-plan"
            ArmyristPortableDataType.REPORT_TEMPLATE -> "report-template"
            else -> "armyrist-data"
        }
        return "$type-${System.currentTimeMillis()}.armyrist"
    }

    fun sharePortable(bytes: ByteArray) {
        runCatching {
            val directory =
                File(context.cacheDir, "portable-share").apply {
                    mkdirs()
                }
            directory.listFiles()?.forEach {
                if (it.isFile) it.delete()
            }

            val file = File(directory, fileName())
            file.writeBytes(bytes)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.armyrist.data"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(
                Intent.createChooser(
                    intent,
                    "Armyrist 데이터 공유"
                )
            )
        }.onFailure {
            Toast.makeText(
                context,
                "데이터 파일을 공유할 수 없습니다.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Scaffold(
        topBar = {
            ArmyristTopBar(
                title = "결과 전달",
                subtitle = "TEXT / DATA",
                leadingLabel = "뒤로",
                onLeading = onBack
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ArmyristPanel(Modifier.fillMaxWidth()) {
                Text(
                    "텍스트 전달",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))

                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = ArmyristPanelShape,
                        border = BorderStroke(
                            1.dp,
                            ArmyristColors.Border
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = ArmyristColors.WorkSurface,
                            contentColor = ArmyristColors.PrimaryText
                        )
                    ) {
                        Text(selected?.name ?: "양식 없음")
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("양식 없음") },
                            onClick = {
                                selectedId = NONE_TEMPLATE
                                expanded = false
                            }
                        )
                        templates.forEach { template ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (template.isDefault) {
                                            "${template.name} · 기본"
                                        } else {
                                            template.name
                                        }
                                    )
                                },
                                onClick = {
                                    selectedId = template.id
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = finalText,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("미리보기") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 170.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor =
                            ArmyristColors.InputSurface,
                        unfocusedContainerColor =
                            ArmyristColors.InputSurface,
                        focusedBorderColor =
                            ArmyristColors.PrimaryControl,
                        unfocusedBorderColor =
                            ArmyristColors.Border
                    )
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    ArmyristActionButton(
                        text = "텍스트 복사",
                        onClick = {
                            val clipboard =
                                context.getSystemService(
                                    Context.CLIPBOARD_SERVICE
                                ) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText(
                                    "Armyrist result",
                                    finalText
                                )
                            )
                            Toast.makeText(
                                context,
                                "텍스트를 복사했습니다.",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.weight(1f)
                    )

                    ArmyristActionButton(
                        text = "텍스트 공유",
                        onClick = {
                            val intent =
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        finalText
                                    )
                                }
                            context.startActivity(
                                Intent.createChooser(
                                    intent,
                                    "텍스트 공유"
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        primary = true
                    )
                }
            }

            if (
                portableType != null &&
                !portableRootId.isNullOrBlank()
            ) {
                ArmyristPanel(Modifier.fillMaxWidth()) {
                    Text(
                        "데이터 파일",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "다른 Armyrist에서 그대로 불러올 수 있는 .armyrist 파일입니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ArmyristColors.SecondaryText
                    )

                    Spacer(Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = encrypt,
                            onCheckedChange = { encrypt = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor =
                                    ArmyristColors.OnDark,
                                checkedTrackColor =
                                    ArmyristColors.PrimaryControl,
                                uncheckedThumbColor =
                                    ArmyristColors.PrimaryText,
                                uncheckedTrackColor =
                                    ArmyristColors.SecondaryControl,
                                uncheckedBorderColor =
                                    ArmyristColors.Border
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "파일 암호화",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (encrypt) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("암호") },
                            visualTransformation =
                                PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = passwordConfirm,
                            onValueChange = {
                                passwordConfirm = it
                            },
                            label = { Text("암호 확인") },
                            visualTransformation =
                                PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(6.dp)
                    ) {
                        ArmyristUtilityActionButton(
                            text = "파일 저장",
                            onClick = {
                                createPortableBytes()?.let {
                                    pendingSaveBytes = it
                                    saveFile.launch(fileName())
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )

                        ArmyristUtilityActionButton(
                            text = "파일 불러오기",
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        context,
                                        PortableTransferActivity::class.java
                                    ).apply {
                                        putExtra(
                                            PortableTransferActivity.EXTRA_MODE,
                                            PortableTransferActivity.MODE_IMPORT
                                        )
                                    }
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )

                        ArmyristUtilityActionButton(
                            text = "파일 공유",
                            onClick = {
                                createPortableBytes()?.let {
                                    sharePortable(it)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (portableType == ArmyristPortableDataType.COUNTING) {
                        Spacer(Modifier.height(10.dp))
                        ArmyristActionButton(
                            text = "주변 Armyrist (PoC)",
                            onClick = {
                                createPortableBytes()?.let { bytes ->
                                    runCatching {
                                        val dir = java.io.File(context.cacheDir, "nearby-send").apply { mkdirs() }
                                        val file = java.io.File(dir, "nearby-${System.currentTimeMillis()}.armyrist")
                                        file.writeBytes(bytes)
                                        context.startActivity(
                                            Intent(context, NearbyTransferActivity::class.java).apply {
                                                putExtra(NearbyTransferActivity.EXTRA_SEND_FILE, file.absolutePath)
                                            }
                                        )
                                    }.onFailure {
                                        Toast.makeText(context, "주변 전송을 준비할 수 없습니다.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            primary = false
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "같은 Wi-Fi/로컬 네트워크의 Armyrist로 직접 전송합니다. 인터넷 서버는 사용하지 않습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ArmyristColors.SecondaryText
                        )

                    }                }
            }
        }
    }

}
