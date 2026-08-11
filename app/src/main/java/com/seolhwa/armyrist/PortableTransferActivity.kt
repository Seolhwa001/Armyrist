package com.seolhwa.armyrist

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.seolhwa.armyrist.notification.ChecklistNotificationManager

class PortableTransferActivity : ComponentActivity() {
    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_TYPE = "type"
        const val EXTRA_ROOT_ID = "rootId"
        const val MODE_EXPORT = "export"
        const val MODE_IMPORT = "import"
    }

    private var pendingBytes: ByteArray? = null
    private var importBytes: ByteArray? = null

    private val createFile = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val bytes = pendingBytes
        pendingBytes = null
        if (uri != null && bytes != null) {
            runCatching {
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("open failed")
            }.onSuccess {
                Toast.makeText(this, "Armyrist 데이터 파일을 저장했습니다.", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this, "파일을 저장할 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val openFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val bytes = runCatching {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("read failed")
        }.getOrElse {
            Toast.makeText(this, "파일을 읽을 수 없습니다.", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        importBytes = bytes
        renderImport(bytes)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent.getStringExtra(EXTRA_MODE)) {
            MODE_EXPORT -> renderExport()
            MODE_IMPORT -> openFile.launch(arrayOf("application/octet-stream", "application/json", "*/*"))
            else -> finish()
        }
    }

    private fun renderExport() {
        val type = runCatching {
            ArmyristPortableDataType.valueOf(intent.getStringExtra(EXTRA_TYPE).orEmpty())
        }.getOrNull()
        val rootId = intent.getStringExtra(EXTRA_ROOT_ID)
        if (type == null || type == ArmyristPortableDataType.BACKUP || rootId.isNullOrBlank()) {
            finish()
            return
        }

        setContent {
            ArmyristTheme {
                var encrypt by remember { mutableStateOf(false) }
                var password by remember { mutableStateOf("") }
                var confirm by remember { mutableStateOf("") }

                Scaffold(
                    topBar = {
                        ArmyristTopBar(
                            title = "데이터 내보내기",
                            subtitle = "${typeLabel(type)} · .armyrist",
                            leadingLabel = "뒤로",
                            onLeading = { finish() }
                        )
                    }
                ) { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ArmyristPanel(Modifier.fillMaxWidth()) {
                            Text("현재 문서를 다른 Armyrist로 전달할 수 있는 데이터 파일로 만듭니다.")
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(checked = encrypt, onCheckedChange = { encrypt = it })
                                Spacer(Modifier.width(8.dp))
                                Text("파일 암호화", fontWeight = FontWeight.SemiBold)
                            }
                            if (encrypt) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("암호") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = confirm,
                                    onValueChange = { confirm = it },
                                    label = { Text("암호 확인") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            ArmyristActionButton(
                                text = "파일 생성",
                                onClick = {
                                    if (encrypt && (password.isBlank() || password != confirm)) {
                                        Toast.makeText(
                                            this@PortableTransferActivity,
                                            "암호와 암호 확인을 동일하게 입력해주세요.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        val result = ArmyristPortableDataManager.createIndividualExport(
                                            this@PortableTransferActivity,
                                            type,
                                            rootId,
                                            if (encrypt) password.toCharArray() else null
                                        )
                                        when (result) {
                                            is PortableResult.Success -> {
                                                pendingBytes = result.value
                                                createFile.launch(
                                                    "${safeTypeName(type)}-${System.currentTimeMillis()}.armyrist"
                                                )
                                            }
                                            is PortableResult.Error -> Toast.makeText(
                                                this@PortableTransferActivity,
                                                result.message,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                                shape = ArmyristPanelShape
                            ) { Text("파일 생성", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }

    private fun renderImport(bytes: ByteArray) {
        val inspection = ArmyristPortableDataManager.inspect(bytes)
        if (inspection is PortableResult.Error) {
            Toast.makeText(this, inspection.message, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        inspection as PortableResult.Success
        if (inspection.value.dataType == ArmyristPortableDataType.BACKUP) {
            Toast.makeText(this, "전체 백업 파일은 데이터 관리의 전체 복원을 사용해주세요.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContent {
            ArmyristTheme {
                var password by remember { mutableStateOf("") }
                var validated by remember {
                    mutableStateOf<ArmyristPortableDataManager.ValidatedPortableDocument?>(null)
                }
                val encrypted = inspection.value.encrypted

                fun validateNow() {
                    when (val result = ArmyristPortableDataManager.validateIndividualImport(
                        bytes,
                        if (encrypted) password.toCharArray() else null
                    )) {
                        is PortableResult.Success -> validated = result.value
                        is PortableResult.Error -> Toast.makeText(
                            this@PortableTransferActivity, result.message, Toast.LENGTH_LONG
                        ).show()
                    }
                }

                LaunchedEffect(Unit) {
                    if (!encrypted) validateNow()
                }

                Scaffold(
                    topBar = {
                        ArmyristTopBar(
                            title = "데이터 가져오기",
                            subtitle = "${typeLabel(inspection.value.dataType)} · 새 문서 생성",
                            leadingLabel = "취소",
                            onLeading = { finish() }
                        )
                    }
                ) { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (encrypted && validated == null) {
                            ArmyristPanel(Modifier.fillMaxWidth()) {
                                Text("암호화된 Armyrist 파일입니다.", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("암호") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(10.dp))
                                Button(
                                    onClick = { validateNow() },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = ArmyristPanelShape
                                ) { Text("확인") }
                            }
                        }

                        validated?.let { data ->
                            val p = data.preview
                            ArmyristPanel(Modifier.fillMaxWidth()) {
                                Text(p.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                Text("종류: ${typeLabel(p.dataType)}")
                                if (p.itemCount > 0) Text("항목: ${p.itemCount}개")
                                if (p.groupCount > 0) Text("그룹: ${p.groupCount}개")
                                if (p.calculationCount > 0) Text("계산: ${p.calculationCount}개")
                                if (p.scheduledCount > 0) Text("예정시각 설정: ${p.scheduledCount}개")
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "기존 문서는 변경하지 않고 새로운 문서로 추가됩니다.",
                                    color = ArmyristColors.SecondaryText
                                )
                                Spacer(Modifier.height(14.dp))
                                Button(
                                    onClick = {
                                        when (val result = ArmyristPortableDataManager.importIndividual(
                                            this@PortableTransferActivity, data
                                        )) {
                                            is PortableResult.Success -> {
                                                if (p.dataType == ArmyristPortableDataType.CHECKLIST) {
                                                    ChecklistNotificationManager.reconcile(
                                                        this@PortableTransferActivity,
                                                        (application as ArmyristApplication).coreSuiteRepository
                                                    )
                                                }
                                                Toast.makeText(
                                                    this@PortableTransferActivity,
                                                    "새 ${typeLabel(p.dataType)} 문서로 가져왔습니다.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                finish()
                                            }
                                            is PortableResult.Error -> Toast.makeText(
                                                this@PortableTransferActivity,
                                                result.message,
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                                    shape = ArmyristPanelShape
                                ) { Text("가져오기", fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun typeLabel(type: ArmyristPortableDataType): String = when (type) {
        ArmyristPortableDataType.BACKUP -> "전체 백업"
        ArmyristPortableDataType.COUNTING -> "실셈"
        ArmyristPortableDataType.CHECKLIST -> "체크리스트"
        ArmyristPortableDataType.TIME_PLAN -> "시간계획"
        ArmyristPortableDataType.REPORT_TEMPLATE -> "보고 양식"
    }

    private fun safeTypeName(type: ArmyristPortableDataType): String = when (type) {
        ArmyristPortableDataType.COUNTING -> "counting"
        ArmyristPortableDataType.CHECKLIST -> "checklist"
        ArmyristPortableDataType.TIME_PLAN -> "time-plan"
        ArmyristPortableDataType.REPORT_TEMPLATE -> "report-template"
        ArmyristPortableDataType.BACKUP -> "backup"
    }
}
