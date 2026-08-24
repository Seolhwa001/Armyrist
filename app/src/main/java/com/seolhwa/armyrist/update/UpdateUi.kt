package com.seolhwa.armyrist.update

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seolhwa.armyrist.ArmyristColors
import com.seolhwa.armyrist.ArmyristPanel
import com.seolhwa.armyrist.ArmyristPanelShape
import com.seolhwa.armyrist.UserProfileActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun AutoUpdateCheckHost(
    onUpdateAvailabilityChanged: (UpdateReleaseMetadata?) -> Unit = {}
) {
    val context = LocalContext.current
    val manager = remember { ArmyristUpdateManager(context) }
    var available by remember { mutableStateOf<UpdateReleaseMetadata?>(null) }

    LaunchedEffect(Unit) {
        if (UpdateSessionState.automaticCheckAttempted) return@LaunchedEffect
        UpdateSessionState.automaticCheckAttempted = true

        val prefs = manager.preferences()
        if (!prefs.isAutomaticCheckDue()) return@LaunchedEffect

        when (val result = manager.check(manual = false)) {
            is UpdateCheckResult.Available -> {
                onUpdateAvailabilityChanged(result.release)
                if (UpdateSessionState.shouldPrompt(result.release.versionCode)) {
                    available = result.release
                }
            }
            is UpdateCheckResult.Latest,
            is UpdateCheckResult.NoNewerEligibleRelease -> {
                onUpdateAvailabilityChanged(null)
            }
            is UpdateCheckResult.Failure -> Unit // Automatic checks never interrupt core use.
        }
    }

    available?.let { release ->
        AlertDialog(
            onDismissRequest = { available = null },
            shape = ArmyristPanelShape,
            containerColor = ArmyristColors.RaisedSurface,
            tonalElevation = 0.dp,
            titleContentColor = ArmyristColors.PrimaryText,
            textContentColor = ArmyristColors.PrimaryText,
            title = {
                Column {
                    Text(
                        "새로운 Armyrist ${release.versionName}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "업데이트를 사용할 수 있습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ArmyristColors.SecondaryText
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = ArmyristColors.SoftBorder)
                    Text(
                        "주요 변경사항",
                        fontWeight = FontWeight.Bold,
                        color = ArmyristColors.SecondaryText
                    )
                    if (release.releaseNotes.isEmpty()) {
                        Text(
                            "변경사항이 제공되지 않았습니다.",
                            color = ArmyristColors.SecondaryText
                        )
                    } else {
                        release.releaseNotes.take(4).forEach {
                            Text(
                                "• $it",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ArmyristColors.SecondaryText
                            )
                        }
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { available = null },
                    shape = ArmyristPanelShape,
                    border = BorderStroke(1.dp, ArmyristColors.SoftBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = ArmyristColors.RaisedSurface,
                        contentColor = ArmyristColors.PrimaryText
                    )
                ) {
                    Text("나중에")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        available = null
                        context.startActivity(
                            Intent(context, UserProfileActivity::class.java)
                                .putExtra(UserProfileActivity.EXTRA_START_UPDATE, true)
                        )
                    },
                    shape = ArmyristPanelShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ArmyristColors.PrimaryControl,
                        contentColor = ArmyristColors.OnDark
                    )
                ) {
                    Text("업데이트", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun UpdateAppInfoPanel(startUpdateImmediately: Boolean = false) {
    val context = LocalContext.current
    val manager = remember { ArmyristUpdateManager(context) }
    val scope = rememberCoroutineScope()
    val installed = remember { manager.installedVersion() }

    var interval by remember { mutableStateOf(manager.preferences().interval) }
    var menuExpanded by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var available by remember {
        mutableStateOf(UpdateSessionState.latestAvailable ?: manager.knownAvailable())
    }
    var downloadState by remember { mutableStateOf<DownloadUiState>(DownloadUiState.Idle) }
    var latestConfirmed by remember { mutableStateOf(false) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    fun beginDownload(release: UpdateReleaseMetadata) {
        if (downloadJob?.isActive == true) return
        downloadState = DownloadUiState.Preparing
        downloadJob = scope.launch {
            val result = manager.downloadAndValidate(
                release = release,
                onProgress = { progress ->
                    downloadState = DownloadUiState.Downloading(progress)
                },
                onValidating = {
                    downloadState = DownloadUiState.Validating
                }
            )
            result.onSuccess { file ->
                downloadState = DownloadUiState.Ready(file.absolutePath)
                when (UpdateInstaller.launch(context, file)) {
                    InstallLaunchResult.Launched -> {
                        message = "Android 설치 화면을 열었습니다. 설치 여부는 시스템 화면에서 선택해주세요."
                    }
                    InstallLaunchResult.PermissionRequired -> {
                        message = "이 출처의 앱 설치 권한을 허용한 뒤 업데이트를 다시 눌러주세요."
                    }
                }
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) {
                    downloadState = DownloadUiState.Idle
                    message = "업데이트 다운로드를 취소했습니다."
                } else {
                    downloadState = DownloadUiState.Failed(
                        error.message ?: "업데이트 파일을 다운로드하거나 검증하지 못했습니다."
                    )
                }
            }
        }
    }

    LaunchedEffect(startUpdateImmediately) {
        if (!startUpdateImmediately) return@LaunchedEffect
        val release = available
        if (release != null) {
            beginDownload(release)
        } else {
            checking = true
            when (val result = manager.check(manual = true)) {
                is UpdateCheckResult.Available -> {
                    available = result.release
                    beginDownload(result.release)
                }
                is UpdateCheckResult.Latest -> {
                    latestConfirmed = true
                    message = "현재 최신 버전입니다."
                }
                is UpdateCheckResult.NoNewerEligibleRelease -> {
                    latestConfirmed = true
                    message = "사용 가능한 새로운 Stable 업데이트가 없습니다."
                }
                is UpdateCheckResult.Failure -> message = result.message
            }
            checking = false
        }
    }

    ArmyristPanel(Modifier.fillMaxWidth()) {
        Text("앱 정보", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))

        InfoRow("현재 버전", installed.displayName)
        Spacer(Modifier.height(6.dp))
        InfoRow(
            "업데이트 상태",
            when {
                available != null -> "새 버전 ${available!!.versionName} 사용 가능"
                checking -> "확인 중"
                latestConfirmed -> "최신 버전 사용 중"
                else -> "업데이트 확인 필요"
            }
        )

        available?.let { release ->
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = ArmyristColors.Border)
            Spacer(Modifier.height(10.dp))
            Text("주요 변경사항", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            if (release.releaseNotes.isEmpty()) {
                Text("변경사항이 제공되지 않았습니다.", color = ArmyristColors.SecondaryText)
            } else {
                release.releaseNotes.forEach { note ->
                    Text("• $note", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Button(
            enabled = !checking && downloadJob?.isActive != true,
            onClick = {
                checking = true
                message = null
                scope.launch {
                    when (val result = manager.check(manual = true)) {
                        is UpdateCheckResult.Available -> {
                            available = result.release
                            latestConfirmed = false
                            message = "새로운 Armyrist ${result.release.versionName} 버전이 있습니다."
                        }
                        is UpdateCheckResult.Latest -> {
                            available = null
                            latestConfirmed = true
                            message = "현재 최신 버전입니다."
                        }
                        is UpdateCheckResult.NoNewerEligibleRelease -> {
                            available = null
                            latestConfirmed = true
                            message = "사용 가능한 새로운 Stable 업데이트가 없습니다."
                        }
                        is UpdateCheckResult.Failure -> message = result.message
                    }
                    checking = false
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = ArmyristPanelShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = ArmyristColors.PrimaryControl,
                contentColor = ArmyristColors.OnDark
            )
        ) {
            if (checking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = ArmyristColors.OnDark
                )
                Spacer(Modifier.width(8.dp))
                Text("확인 중")
            } else {
                Text("업데이트 확인", fontWeight = FontWeight.Bold)
            }
        }

        available?.let { release ->
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                enabled = downloadJob?.isActive != true,
                onClick = { beginDownload(release) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = ArmyristPanelShape,
                border = BorderStroke(1.dp, ArmyristColors.PrimaryControl)
            ) {
                Text("Armyrist ${release.versionName} 업데이트")
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("자동 업데이트 확인", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = ArmyristPanelShape
            ) {
                Text(interval.displayName, modifier = Modifier.weight(1f))
                Text("▼")
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                UpdateCheckInterval.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.displayName) },
                        onClick = {
                            interval = option
                            manager.preferences().interval = option
                            menuExpanded = false
                        }
                    )
                }
            }
        }
        Text(
            "자동 확인은 앱을 사용할 때 주기가 지난 경우에만 실행되며, 자동 다운로드나 자동 설치는 하지 않습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = ArmyristColors.SecondaryText
        )

        when (val state = downloadState) {
            DownloadUiState.Idle -> Unit
            DownloadUiState.Preparing -> {
                Spacer(Modifier.height(14.dp))
                Text("다운로드 준비", fontWeight = FontWeight.Bold)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is DownloadUiState.Downloading -> {
                Spacer(Modifier.height(14.dp))
                Text(
                    state.progress?.let { "다운로드 중 · $it%" } ?: "다운로드 중",
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                if (state.progress != null) {
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(6.dp))
                Text("다운로드 완료 후 APK와 서명을 검증합니다.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    downloadJob?.cancel()
                    downloadState = DownloadUiState.Idle
                    message = "업데이트 다운로드를 취소했습니다."
                }) { Text("다운로드 취소") }
            }
            DownloadUiState.Validating -> {
                Spacer(Modifier.height(14.dp))
                Text("검증 중", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    "APK, Package, Version, SHA-256, Signing Identity를 확인합니다.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            is DownloadUiState.Ready -> {
                Spacer(Modifier.height(12.dp))
                Text("검증 완료 · 설치 준비 완료", fontWeight = FontWeight.Bold)
            }
            is DownloadUiState.Failed -> {
                Spacer(Modifier.height(12.dp))
                Text("다운로드 실패", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                Text(state.message, style = MaterialTheme.typography.bodySmall)
            }
        }

        message?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = ArmyristColors.SecondaryText)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = ArmyristColors.SecondaryText)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

private sealed interface DownloadUiState {
    data object Idle : DownloadUiState
    data object Preparing : DownloadUiState
    data class Downloading(val progress: Int?) : DownloadUiState
    data object Validating : DownloadUiState
    data class Ready(val path: String) : DownloadUiState
    data class Failed(val message: String) : DownloadUiState
}
