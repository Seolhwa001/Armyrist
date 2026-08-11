@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.seolhwa.armyrist

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seolhwa.armyrist.notification.ChecklistNotificationManager
import com.seolhwa.armyrist.notification.ChecklistNotificationSettings

class NotificationSettingsActivity : ComponentActivity() {
    private var uiRevision by mutableIntStateOf(0)

    private val ringtonePicker =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val uri = if (Build.VERSION.SDK_INT >= 33) {
                result.data?.getParcelableExtra(
                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                    Uri::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(
                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI
                )
            }

            if (uri != null) {
                ChecklistNotificationSettings.setSoundUri(this, uri)
                ChecklistNotificationManager.createChannel(this)
                uiRevision++
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    val revision = uiRevision
                    @Suppress("UNUSED_VARIABLE")
                    val observed = revision

                    NotificationSettingsScreen(
                        exactAlarmAvailable =
                            ChecklistNotificationManager
                                .exactAlarmAvailable(this),
                        notificationPermissionGranted =
                            ChecklistNotificationManager
                                .notificationPermissionGranted(this),
                        notificationsEnabled =
                            ChecklistNotificationManager
                                .notificationsEnabled(this),
                        soundTitle =
                            ChecklistNotificationSettings
                                .soundTitle(this),
                        vibrationEnabled =
                            ChecklistNotificationSettings
                                .vibrationEnabled(this),
                        onBack = { finish() },
                        onRequestExactAlarm = {
                            requestExactAlarmAccess()
                        },
                        onRequestNotificationPermission = {
                            requestNotificationPermission()
                        },
                        onPickSound = {
                            openSoundPicker()
                        },
                        onVibrationChange = { enabled ->
                            ChecklistNotificationSettings
                                .setVibrationEnabled(this, enabled)
                            ChecklistNotificationManager
                                .createChannel(this)
                            uiRevision++
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        uiRevision++
    }

    private fun requestExactAlarmAccess() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !ChecklistNotificationManager.exactAlarmAvailable(this)
        ) {
            val intent = Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                4201
            )
        }
    }

    private fun openSoundPicker() {
        val current =
            ChecklistNotificationSettings.soundUri(this)

        val intent =
            Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(
                    RingtoneManager.EXTRA_RINGTONE_TYPE,
                    RingtoneManager.TYPE_ALARM or
                        RingtoneManager.TYPE_NOTIFICATION
                )
                putExtra(
                    RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT,
                    true
                )
                putExtra(
                    RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,
                    false
                )
                putExtra(
                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                    current
                )
                putExtra(
                    RingtoneManager.EXTRA_RINGTONE_TITLE,
                    "체크리스트 알람음 선택"
                )
            }

        ringtonePicker.launch(intent)
    }
}

@Composable
private fun NotificationSettingsScreen(
    exactAlarmAvailable: Boolean,
    notificationPermissionGranted: Boolean,
    notificationsEnabled: Boolean,
    soundTitle: String,
    vibrationEnabled: Boolean,
    onBack: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onPickSound: () -> Unit,
    onVibrationChange: (Boolean) -> Unit
) {
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "체크리스트 알림 설정",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("‹ 홈")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StatusCard(
                title = "정확한 알람",
                active = exactAlarmAvailable,
                activeText = "사용 가능",
                inactiveText = "권한 필요",
                actionText =
                    if (exactAlarmAvailable) null
                    else "정확한 알람 허용",
                onAction = onRequestExactAlarm
            )

            StatusCard(
                title = "알림 권한",
                active =
                    notificationPermissionGranted &&
                        notificationsEnabled,
                activeText = "사용 가능",
                inactiveText = "알림 권한 또는 시스템 알림 설정 확인 필요",
                actionText =
                    if (notificationPermissionGranted) null
                    else "알림 권한 허용",
                onAction = onRequestNotificationPermission
            )

            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "알람음",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(soundTitle)
                    Button(
                        onClick = onPickSound,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("알람음 선택")
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment =
                        androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "진동",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (vibrationEnabled) "사용"
                            else "사용 안 함"
                        )
                    }

                    Switch(
                        checked = vibrationEnabled,
                        onCheckedChange = onVibrationChange
                    )
                }
            }

            Text(
                "정확한 알람 권한이 없으면 Android가 지정 시각보다 늦게 알림을 전달할 수 있습니다. " +
                    "알람음은 휴대전화의 알람/알림음 목록에서 선택합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    active: Boolean,
    activeText: String,
    inactiveText: String,
    actionText: String?,
    onAction: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                if (active) activeText else inactiveText,
                color =
                    if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
            )

            if (actionText != null) {
                Button(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(actionText)
                }
            }
        }
    }
}
