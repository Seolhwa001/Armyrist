package com.seolhwa.armyrist

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.seolhwa.armyrist.stage2.data.CoreSuiteRepository

class UserProfileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = (application as ArmyristApplication).coreSuiteRepository
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    UserProfileScreen(repo = repo, onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserProfileScreen(
    repo: CoreSuiteRepository,
    onBack: () -> Unit
) {
    var displayName by remember {
        mutableStateOf(repo.getUserProfile().displayName)
    }
    var savedName by remember {
        mutableStateOf(repo.getUserProfile().displayName)
    }
    val context = androidx.compose.ui.platform.LocalContext.current

    BackHandler {
        if (displayName != savedName) {
            repo.setUserProfile(displayName)
        }
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("내 정보") },
                navigationIcon = {
                    TextButton(onClick = {
                        if (displayName != savedName) repo.setUserProfile(displayName)
                        onBack()
                    }) { Text("‹ 홈") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(20.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "보고 양식에서 {사용자} 변수에 사용할 이름입니다.",
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("사용자 이름") },
                placeholder = { Text("입력하지 않아도 됩니다") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    repo.setUserProfile(displayName)
                    savedName = displayName
                    Toast.makeText(context, "저장되었습니다.", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("저장")
            }

            Text(
                if (displayName.isBlank()) "현재 이름이 비어 있습니다." else "현재 입력: $displayName",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
