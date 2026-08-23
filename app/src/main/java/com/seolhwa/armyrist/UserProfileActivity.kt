package com.seolhwa.armyrist

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seolhwa.armyrist.developer.DeveloperAccessPreferences
import com.seolhwa.armyrist.stage2.data.CoreSuiteRepository
import com.seolhwa.armyrist.update.ArmyristUpdateManager
import com.seolhwa.armyrist.update.UpdateCheckResult
import com.seolhwa.armyrist.update.UpdateSessionState
import kotlinx.coroutines.launch
import com.seolhwa.armyrist.update.UpdateAppInfoPanel

class UserProfileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = (application as ArmyristApplication).coreSuiteRepository
        val startUpdateImmediately = intent.getBooleanExtra(EXTRA_START_UPDATE, false)
        setContent {
            ArmyristTheme {
                Surface(Modifier.fillMaxSize(), color = ArmyristColors.AppBackground) {
                    UserProfileScreen(
                        repo = repo,
                        startUpdateImmediately = startUpdateImmediately,
                        onBack = { finish() }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_START_UPDATE = "armyrist.extra.START_UPDATE"
    }
}

@Composable
private fun UserProfileScreen(
    repo: CoreSuiteRepository,
    startUpdateImmediately: Boolean,
    onBack: () -> Unit
) {
    var displayName by remember { mutableStateOf(repo.getUserProfile().displayName) }
    var savedName by remember { mutableStateOf(repo.getUserProfile().displayName) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val developerPrefs = remember { DeveloperAccessPreferences(context) }
    val updateManager = remember { ArmyristUpdateManager(context) }
    val scope = rememberCoroutineScope()

    var developerUnlocked by remember { mutableStateOf(developerPrefs.unlocked) }
    var developerCode by remember { mutableStateOf("") }
    var developerMessage by remember { mutableStateOf<String?>(null) }
    var developerChecking by remember { mutableStateOf(false) }

    fun saveIfNeeded() {
        if (displayName != savedName) {
            repo.setUserProfile(displayName)
            savedName = displayName
        }
    }

    BackHandler {
        saveIfNeeded()
        onBack()
    }

    Scaffold(
        topBar = {
            ArmyristTopBar(
                title = "내 정보",
                subtitle = "PROFILE · APP INFORMATION · AUTO SAVE",
                leadingLabel = "홈",
                onLeading = {
                    saveIfNeeded()
                    onBack()
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ArmyristPanel(Modifier.fillMaxWidth()) {
                    Text(
                        "사용자 정보",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "보고 양식에서 {사용자} 변수에 사용할 이름입니다.",
                        color = ArmyristColors.SecondaryText
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("사용자 이름") },
                        placeholder = { Text("입력하지 않아도 됩니다") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ArmyristColors.InputSurface,
                            unfocusedContainerColor = ArmyristColors.InputSurface,
                            focusedBorderColor = ArmyristColors.PrimaryControl,
                            unfocusedBorderColor = ArmyristColors.Border
                        )
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            repo.setUserProfile(displayName)
                            savedName = displayName
                            Toast.makeText(context, "저장되었습니다.", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = ArmyristPanelShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ArmyristColors.PrimaryControl,
                            contentColor = ArmyristColors.OnDark
                        )
                    ) {
                        Text("저장", fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                Text(
                    if (displayName.isBlank()) "현재 이름이 비어 있습니다." else "현재 입력: $displayName",
                    style = MaterialTheme.typography.bodySmall,
                    color = ArmyristColors.SecondaryText
                )
            }

            item {
                ArmyristPanel(Modifier.fillMaxWidth()) {
                    Text(
                        "개발자 메뉴",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (developerUnlocked) {
                            "개발/검수용 기능이 활성화되어 있습니다."
                        } else {
                            "개발자 코드를 입력하면 개발/검수용 기능을 사용할 수 있습니다."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = ArmyristColors.SecondaryText
                    )

                    if (!developerUnlocked) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = developerCode,
                            onValueChange = {
                                developerCode = it.filter(Char::isDigit).take(8)
                                developerMessage = null
                            },
                            label = { Text("코드 입력") },
                            placeholder = { Text("개발자 코드") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = ArmyristColors.InputSurface,
                                unfocusedContainerColor = ArmyristColors.InputSurface,
                                focusedBorderColor = ArmyristColors.PrimaryControl,
                                unfocusedBorderColor = ArmyristColors.Border
                            )
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (developerPrefs.unlockWith(developerCode)) {
                                    developerUnlocked = true
                                    developerCode = ""
                                    developerMessage = "개발자 메뉴가 활성화되었습니다."
                                } else {
                                    developerMessage = "코드가 올바르지 않습니다."
                                }
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = ArmyristPanelShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ArmyristColors.PrimaryControl,
                                contentColor = ArmyristColors.OnDark
                            )
                        ) {
                            Text("코드 확인", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Spacer(Modifier.height(12.dp))
                        InfoRowForDeveloper(
                            label = "현재 버전",
                            value = updateManager.installedVersion().displayName
                        )
                        Spacer(Modifier.height(4.dp))
                        InfoRowForDeveloper(
                            label = "versionCode",
                            value = updateManager.installedVersion().versionCode.toString()
                        )

                        Spacer(Modifier.height(12.dp))
                        Button(
                            enabled = !developerChecking,
                            onClick = {
                                developerChecking = true
                                developerMessage = "자동 업데이트 확인을 실행하고 있습니다."
                                scope.launch {
                                    developerMessage = when (
                                        val result = updateManager.check(manual = false)
                                    ) {
                                        is UpdateCheckResult.Available ->
                                            "새 Stable ${result.release.versionName}을 확인했습니다. 홈으로 돌아가면 업데이트 표시를 확인할 수 있습니다."
                                        is UpdateCheckResult.Latest ->
                                            "현재 Stable이 최신입니다."
                                        is UpdateCheckResult.NoNewerEligibleRelease ->
                                            "사용 가능한 새로운 Stable 업데이트가 없습니다."
                                        is UpdateCheckResult.Failure ->
                                            "자동 확인 테스트 실패: ${result.message}"
                                    }
                                    developerChecking = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = ArmyristPanelShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ArmyristColors.PrimaryControl,
                                contentColor = ArmyristColors.OnDark
                            )
                        ) {
                            if (developerChecking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = ArmyristColors.OnDark
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("자동 확인 실행 중")
                            } else {
                                Text("자동 업데이트 확인 즉시 실행", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                updateManager.preferences().markAutomaticCheckDueForDeveloper()
                                UpdateSessionState.automaticCheckAttempted = false
                                developerMessage =
                                    "자동 확인 주기를 만료 상태로 설정했습니다. 홈으로 돌아가면 실제 자동 확인 Flow가 다시 실행됩니다."
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = ArmyristPanelShape
                        ) {
                            Text("다음 홈 진입에서 자동 확인 테스트")
                        }

                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                developerPrefs.lock()
                                developerUnlocked = false
                                developerMessage = "개발자 메뉴를 잠갔습니다."
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("개발자 메뉴 잠금")
                        }

                        Spacer(Modifier.height(4.dp))
                        Text(
                            "개발자 코드는 편의 기능을 숨기기 위한 용도이며 보안 인증 수단이 아닙니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ArmyristColors.SecondaryText
                        )
                    }

                    developerMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = ArmyristColors.SecondaryText
                        )
                    }
                }
            }

            item { UpdateAppInfoPanel(startUpdateImmediately = startUpdateImmediately) }
        }
    }
}


@Composable
private fun InfoRowForDeveloper(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = ArmyristColors.SecondaryText
        )
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
