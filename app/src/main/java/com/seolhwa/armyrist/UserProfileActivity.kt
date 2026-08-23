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
import com.seolhwa.armyrist.stage2.data.CoreSuiteRepository
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

            item { UpdateAppInfoPanel(startUpdateImmediately = startUpdateImmediately) }
        }
    }
}
