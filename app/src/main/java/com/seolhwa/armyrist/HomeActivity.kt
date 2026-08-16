package com.seolhwa.armyrist

import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Build
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (
            NearbyConnectionsPoC.isReceiveEnabled(this) &&
            NearbyConnectionsPoC.hasRuntimePermissions(this)
        ) {
            NearbyConnectionsPoC.startReceiverService(this)
        }

        setContent {
            ArmyristTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ArmyristColors.AppBackground
                ) {
                    HomeScreen(
                        onCounting = {
                            startActivity(Intent(this, MainActivity::class.java))
                        },
                        onChecklist = {
                            startActivity(Intent(this, ChecklistActivity::class.java))
                        },
                        onTimePlan = {
                            startActivity(Intent(this, TimePlanActivity::class.java))
                        },
                        onReportTemplate = {
                            startActivity(Intent(this, ReportTemplateActivity::class.java))
                        },
                        onUserProfile = {
                            startActivity(Intent(this, UserProfileActivity::class.java))
                        },
                        onDataManagement = {
                            startActivity(
                                Intent(
                                    this,
                                    DataManagementActivity::class.java
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    onCounting: () -> Unit,
    onChecklist: () -> Unit,
    onTimePlan: () -> Unit,
    onReportTemplate: () -> Unit,
    onUserProfile: () -> Unit,
    onDataManagement: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ArmyristPanel(
            modifier = Modifier.fillMaxWidth(),
            dark = true,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "ARMYRIST",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    ArmyristSystemLabel(
                        "ARMYRIST SYSTEM",
                        onDark = true
                    )
                }
                Text(
                    "SYS: NORMAL",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFD7E0B9),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        ArmyristSectionLabel("업무 도구")

        HomeToolPanel(
            title = "실셈",
            subtitle = "수량 기록 · 그룹 집계 · 결과 공유",
            onClick = onCounting
        )
        HomeToolPanel(
            title = "체크리스트",
            subtitle = "반복 점검 · 상태 관리 · 진행 현황",
            onClick = onChecklist
        )
        HomeToolPanel(
            title = "시간계획",
            subtitle = "시각 · 경과시간 · 중도 지점 관리",
            onClick = onTimePlan
        )
        HomeToolPanel(
            title = "보고 양식",
            subtitle = "공통 결과 전달 양식 관리",
            onClick = onReportTemplate
        )

        Spacer(Modifier.height(2.dp))
        ArmyristSectionLabel("공통 / 설정")

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onUserProfile),
            color = ArmyristColors.WorkSurface,
            shape = ArmyristPanelShape,
            border = BorderStroke(1.dp, ArmyristColors.Border)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "내 정보",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "사용자 이름 관리  ›",
                    style = MaterialTheme.typography.bodySmall,
                    color = ArmyristColors.SecondaryText
                )
            }
        }


        NearbyReceiveToggle()

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onDataManagement),
            color = ArmyristColors.WorkSurface,
            shape = ArmyristPanelShape,
            border = BorderStroke(1.dp, ArmyristColors.Border)
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = 14.dp,
                    vertical = 13.dp
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "데이터 관리",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "백업 · 복원  ›",
                    style = MaterialTheme.typography.bodySmall,
                    color = ArmyristColors.SecondaryText
                )
            }
        }

        Spacer(Modifier.weight(1f))

        ArmyristSystemLabel(
            "CORE SUITE v1  ·  OFFLINE READY",
            modifier = Modifier.fillMaxWidth()
        )
    }
}


@Composable
private fun NearbyReceiveToggle() {
    val context = LocalContext.current
    var enabled by remember {
        mutableStateOf(
            NearbyConnectionsPoC.isReceiveEnabled(context)
        )
    }
    var starting by remember { mutableStateOf(false) }
    var message by remember {
        mutableStateOf(
            if (enabled) "● 수신 대기 중" else "수신 모드가 꺼져 있습니다."
        )
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    NearbyConnectionsPoC.ACTION_ADVERTISING_ACTIVE -> {
                        starting = false
                        enabled = true
                        message = "● 수신 대기 중"
                    }

                    NearbyConnectionsPoC.ACTION_ADVERTISING_FAILED -> {
                        starting = false
                        enabled = false
                        message =
                            "주변 데이터 수신을 시작하지 못했습니다. 권한과 무선 연결 상태를 확인해주세요."
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(NearbyConnectionsPoC.ACTION_ADVERTISING_ACTIVE)
            addAction(NearbyConnectionsPoC.ACTION_ADVERTISING_FAILED)
        }

        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(
                receiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }

        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    val launcher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            val granted =
                NearbyConnectionsPoC.hasRuntimePermissions(context) &&
                    result.values.all { it }

            if (granted) {
                starting = true
                enabled = false
                message = "수신 대기를 시작하는 중…"
                NearbyConnectionsPoC.startReceiverService(context)
            } else {
                NearbyConnectionsPoC.setReceiveEnabled(context, false)
                starting = false
                enabled = false
                message =
                    "주변 데이터 수신을 사용하려면 근처 기기 권한이 필요합니다."
            }
        }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ArmyristColors.WorkSurface,
        shape = ArmyristPanelShape,
        border = BorderStroke(1.dp, ArmyristColors.Border)
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 14.dp,
                vertical = 11.dp
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "주변 데이터 수신",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (enabled) {
                                ArmyristColors.PrimaryControl
                            } else {
                                ArmyristColors.SecondaryText
                            }
                    )
                }

                Switch(
                    checked = enabled,
                    enabled = !starting,
                    onCheckedChange = { turnOn ->
                        if (!turnOn) {
                            NearbyConnectionsPoC.stopReceiverService(context)
                            starting = false
                            enabled = false
                            message = "수신 모드가 꺼져 있습니다."
                        } else if (
                            NearbyConnectionsPoC.hasRuntimePermissions(context)
                        ) {
                            starting = true
                            enabled = false
                            message = "수신 대기를 시작하는 중…"
                            NearbyConnectionsPoC.startReceiverService(context)
                        } else {
                            launcher.launch(
                                NearbyConnectionsPoC.runtimePermissions()
                            )
                        }
                    }
                )
            }

            if (enabled) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "아미리스를 보고 있지 않아도 주변 전송 요청을 받을 수 있도록 수신 서비스를 유지합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ArmyristColors.SecondaryText
                )
            }
        }
    }
}

@Composable
private fun HomeToolPanel(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = ArmyristColors.RaisedSurface,
        shape = ArmyristPanelShape,
        border = BorderStroke(1.dp, ArmyristColors.Border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = ArmyristColors.SecondaryText
                )
            }

            Text(
                "›",
                style = MaterialTheme.typography.headlineSmall,
                color = ArmyristColors.PrimaryControl
            )
        }
    }
}
