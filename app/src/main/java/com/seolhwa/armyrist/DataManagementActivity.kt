package com.seolhwa.armyrist

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.time.LocalDate

class DataManagementActivity : ComponentActivity() {
    private var pendingBackupBytes: ByteArray? = null
    private var selectedRestoreBytes: ByteArray? = null

    private val createBackupFile =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument(
                "application/octet-stream"
            )
        ) { uri ->
            val bytes = pendingBackupBytes
            pendingBackupBytes = null

            if (uri != null && bytes != null) {
                runCatching {
                    contentResolver.openOutputStream(uri)?.use {
                        it.write(bytes)
                    } ?: error("openOutputStream failed")
                }.onSuccess {
                    Toast.makeText(
                        this,
                        "백업 파일을 저장했습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }.onFailure {
                    Toast.makeText(
                        this,
                        "백업 파일을 저장할 수 없습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    private val openRestoreFile =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                selectedRestoreBytes =
                    readUriBytesSafely(uri)

                selectedRestoreBytes?.let {
                    onRestoreFileSelected?.invoke(it)
                }
            }
        }

    private var onRestoreFileSelected:
        ((ByteArray) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ArmyristPortableDataManager
            .recoverInterruptedRestore(this)

        setContent {
            ArmyristTheme {
                Surface(
                    Modifier.fillMaxSize(),
                    color = ArmyristColors.AppBackground
                ) {
                    DataManagementScreen(
                        onBack = { finish() },
                        onCreateBackup = { encrypted, password ->
                            val result =
                                ArmyristPortableDataManager
                                    .createFullBackup(
                                        this,
                                        if (encrypted) {
                                            password.toCharArray()
                                        } else {
                                            null
                                        }
                                    )

                            when (result) {
                                is PortableResult.Success -> {
                                    pendingBackupBytes = result.value
                                    createBackupFile.launch(
                                        "armyrist-backup-${
                                            LocalDate.now()
                                        }.armyrist"
                                    )
                                }
                                is PortableResult.Error -> {
                                    Toast.makeText(
                                        this,
                                        result.message,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        onChooseRestoreFile = { callback ->
                            onRestoreFileSelected = callback
                            openRestoreFile.launch(
                                arrayOf(
                                    "application/octet-stream",
                                    "application/json",
                                    "*/*"
                                )
                            )
                        },
                        onRestoreValidated = { backup ->
                            when (
                                ArmyristPortableDataManager
                                    .restoreFullBackup(
                                        this,
                                        backup
                                    )
                            ) {
                                is PortableResult.Success -> {
                                    (application as ArmyristApplication)
                                        .reloadAfterPortableDataChange()
                                    Toast.makeText(
                                        this,
                                        "전체 복원이 완료되었습니다.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    true
                                }
                                is PortableResult.Error -> false
                            }
                        }
                    )
                }
            }
        }
    }

    private fun readUriBytesSafely(uri: Uri): ByteArray? {
        return runCatching {
            contentResolver.openInputStream(uri)?.use { stream ->
                val maxBytes = 32 * 1024 * 1024
                val buffer = ByteArray(8192)
                val output = java.io.ByteArrayOutputStream()
                var total = 0

                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= maxBytes)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: error("openInputStream failed")
        }.getOrElse {
            Toast.makeText(
                this,
                "파일이 너무 크거나 읽을 수 없습니다.",
                Toast.LENGTH_SHORT
            ).show()
            null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataManagementScreen(
    onBack: () -> Unit,
    onCreateBackup: (Boolean, String) -> Unit,
    onChooseRestoreFile: (((ByteArray) -> Unit) -> Unit),
    onRestoreValidated: (ValidatedBackup) -> Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var encrypt by remember { mutableStateOf(false) }
    var passwordDialog by remember { mutableStateOf(false) }

    var restoreBytes by remember { mutableStateOf<ByteArray?>(null) }
    var restoreInspection by remember {
        mutableStateOf<ContainerInspection?>(null)
    }
    var restoreBackup by remember {
        mutableStateOf<ValidatedBackup?>(null)
    }
    var restorePasswordDialog by remember {
        mutableStateOf(false)
    }
    var restoreConfirm by remember {
        mutableStateOf(false)
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            ArmyristTopBar(
                title = "데이터 관리",
                subtitle = "DATA · BACKUP / RESTORE · OFFLINE",
                leadingLabel = "홈",
                onLeading = onBack
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val summary = remember {
                ArmyristPortableDataManager.currentSummary(
                    context
                )
            }

            ArmyristPanel(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "전체 백업",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text("현재 Armyrist 데이터를 하나의 파일로 보관합니다.")
                Spacer(Modifier.height(12.dp))
                DataCount("실셈", summary.countingSheets)
                DataCount("체크리스트", summary.checklists)
                DataCount("시간계획", summary.timePlans)
                DataCount("보고 양식", summary.reportTemplates)
                DataCountText("내 정보", "포함")

                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = encrypt,
                        onCheckedChange = { encrypt = it }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            "파일 암호화",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (encrypt) {
                                "AES-256-GCM으로 보호"
                            } else {
                                "암호화 안 함"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = ArmyristColors.SecondaryText
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (encrypt) {
                            passwordDialog = true
                        } else {
                            onCreateBackup(false, "")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp),
                    shape = ArmyristPanelShape
                ) {
                    Text("백업 생성")
                }
            }

            ArmyristPanel(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "전체 복원",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "백업 파일의 데이터로 현재 Armyrist 데이터를 교체합니다."
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "복원 실패 시 기존 데이터는 유지됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ArmyristColors.SecondaryText
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        onChooseRestoreFile { bytes ->
                            restoreBytes = bytes
                            when (
                                val inspection =
                                    ArmyristPortableDataManager
                                        .inspect(bytes)
                            ) {
                                is PortableResult.Success -> {
                                    restoreInspection =
                                        inspection.value
                                    if (inspection.value.encrypted) {
                                        restorePasswordDialog = true
                                    } else {
                                        when (
                                            val validated =
                                                ArmyristPortableDataManager
                                                    .validateBackup(
                                                        bytes,
                                                        null
                                                    )
                                        ) {
                                            is PortableResult.Success -> {
                                                restoreBackup =
                                                    validated.value
                                                restoreConfirm = true
                                            }
                                            is PortableResult.Error -> {
                                                Toast.makeText(
                                                    context,
                                                    validated.message,
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                }
                                is PortableResult.Error -> {
                                    Toast.makeText(
                                        context,
                                        inspection.message,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp),
                    shape = ArmyristPanelShape
                ) {
                    Text("백업 파일 선택")
                }
            }

            ArmyristPanel(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "개별 데이터 가져오기",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text("실셈·체크리스트·시간계획·보고 양식 .armyrist 파일을 새 문서로 추가합니다.")
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            android.content.Intent(
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
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    shape = ArmyristPanelShape
                ) {
                    Text("Armyrist 데이터 파일 선택")
                }
            }
        }
    }

    if (passwordDialog) {
        PasswordConfirmDialog(
            title = "백업 파일 암호화",
            onDismiss = { passwordDialog = false },
            onConfirm = { password ->
                passwordDialog = false
                onCreateBackup(true, password)
            }
        )
    }

    if (restorePasswordDialog) {
        PasswordInputDialog(
            title = "암호화된 Armyrist 파일",
            onDismiss = {
                restorePasswordDialog = false
                restoreBytes = null
            },
            onConfirm = { password ->
                val bytes = restoreBytes ?: return@PasswordInputDialog
                when (
                    val validated =
                        ArmyristPortableDataManager.validateBackup(
                            bytes,
                            password.toCharArray()
                        )
                ) {
                    is PortableResult.Success -> {
                        restorePasswordDialog = false
                        restoreBackup = validated.value
                        restoreConfirm = true
                    }
                    is PortableResult.Error -> {
                        Toast.makeText(
                            context,
                            validated.message,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }

    if (restoreConfirm) {
        val backup = restoreBackup
        if (backup != null) {
            RestoreConfirmDialog(
                backup = backup,
                current =
                    ArmyristPortableDataManager.currentSummary(
                        context
                    ),
                onDismiss = {
                    restoreConfirm = false
                    restoreBackup = null
                    restoreBytes = null
                },
                onConfirm = {
                    if (onRestoreValidated(backup)) {
                        restoreConfirm = false
                        restoreBackup = null
                        restoreBytes = null
                    }
                }
            )
        }
    }
}

@Composable
private fun DataCount(label: String, count: Int) {
    DataCountText(label, count.toString())
}

@Composable
private fun DataCountText(
    label: String,
    value: String
) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = ArmyristColors.SecondaryText
        )
        Text(
            value,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PasswordConfirmDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "암호를 잊으면 파일을 복구할 수 없습니다.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = ""
                    },
                    label = { Text("암호") },
                    visualTransformation =
                        PasswordVisualTransformation()
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = {
                        confirm = it
                        error = ""
                    },
                    label = { Text("암호 확인") },
                    visualTransformation =
                        PasswordVisualTransformation()
                )
                if (error.isNotBlank()) {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        password.isEmpty() ->
                            error = "암호를 입력하세요."
                        password != confirm ->
                            error = "암호가 일치하지 않습니다."
                        else -> onConfirm(password)
                    }
                }
            ) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
private fun PasswordInputDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("암호") },
                visualTransformation =
                    PasswordVisualTransformation()
            )
        },
        confirmButton = {
            TextButton(
                enabled = password.isNotEmpty(),
                onClick = { onConfirm(password) }
            ) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
private fun RestoreConfirmDialog(
    backup: ValidatedBackup,
    current: BackupSummary,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("전체 복원") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "현재 Armyrist 데이터가 선택한 백업의 데이터로 교체됩니다.",
                    fontWeight = FontWeight.Bold
                )

                Text("현재 데이터")
                DataCount("실셈", current.countingSheets)
                DataCount("체크리스트", current.checklists)
                DataCount("시간계획", current.timePlans)
                DataCount("보고 양식", current.reportTemplates)

                HorizontalDivider()

                Text("복원할 백업")
                DataCount(
                    "실셈",
                    backup.summary.countingSheets
                )
                DataCount(
                    "체크리스트",
                    backup.summary.checklists
                )
                DataCount(
                    "시간계획",
                    backup.summary.timePlans
                )
                DataCount(
                    "보고 양식",
                    backup.summary.reportTemplates
                )
                DataCountText("내 정보", "포함")
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ArmyristColors.Danger,
                    contentColor = ArmyristColors.OnDark
                )
            ) {
                Text("복원")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}
