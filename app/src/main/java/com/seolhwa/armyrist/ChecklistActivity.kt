@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.seolhwa.armyrist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.app.AlarmManager
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.seolhwa.armyrist.notification.ChecklistNotificationManager
import com.seolhwa.armyrist.stage2.data.CoreSuiteRepository
import com.seolhwa.armyrist.stage2.domain.*
import com.seolhwa.armyrist.voice.*
import kotlin.math.roundToInt

class ChecklistActivity : ComponentActivity() {
    private lateinit var coreRepository: CoreSuiteRepository
    private var pendingSoundSelection: ((String) -> Unit)? = null

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
                pendingSoundSelection?.invoke(uri.toString())
            }
            pendingSoundSelection = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        coreRepository = (application as ArmyristApplication).coreSuiteRepository
        val initialChecklistId =
            intent.getStringExtra(ChecklistNotificationManager.EXTRA_CHECKLIST_ID)

        setContent {
            ArmyristTheme {
                Surface(Modifier.fillMaxSize(), color = ArmyristColors.AppBackground) {
                    ChecklistApp(
                        repo = coreRepository,
                        initialChecklistId = initialChecklistId,
                        onHome = { finish() },
                        onPickNotificationSound = { existingUri, onPicked ->
                            openNotificationSoundPicker(existingUri, onPicked)
                        },
                        onRequestNotificationPermission = {
                            requestNotificationCapabilitiesIfNeeded()
                        },
                        onReconcileNotifications = {
                            ChecklistNotificationManager.reconcile(
                                this,
                                coreRepository
                            )
                        },
                        onCancelItemNotification = { itemId ->
                            ChecklistNotificationManager.cancel(this, itemId)
                        },
                        onCancelChecklistNotifications = { checklist ->
                            ChecklistNotificationManager.cancelChecklist(
                                this,
                                checklist
                            )
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::coreRepository.isInitialized) {
            ChecklistNotificationManager.reconcile(this, coreRepository)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 4101 && ::coreRepository.isInitialized) {
            ChecklistNotificationManager.reconcile(this, coreRepository)
        }
    }

    private fun requestNotificationCapabilitiesIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                4101
            )
            return
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !ChecklistNotificationManager.exactAlarmAvailable(this)
        ) {
            runCatching {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }
    }

    private fun openNotificationSoundPicker(
        existingUri: String?,
        onPicked: (String) -> Unit
    ) {
        pendingSoundSelection = onPicked

        val existing = existingUri
            ?.takeIf { it.isNotBlank() }
            ?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        ringtonePicker.launch(
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
                    existing
                )
                putExtra(
                    RingtoneManager.EXTRA_RINGTONE_TITLE,
                    "체크리스트 알람음 선택"
                )
            }
        )
    }

}

private enum class ChecklistViewMode { DETAIL, COMPACT }

@Composable
private fun ChecklistApp(
    repo: CoreSuiteRepository,
    initialChecklistId: String?,
    onHome: () -> Unit,
    onPickNotificationSound: (String?, (String) -> Unit) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onReconcileNotifications: () -> Unit,
    onCancelItemNotification: (String) -> Unit,
    onCancelChecklistNotifications: (Checklist) -> Unit
) {
    var selectedId by remember { mutableStateOf(initialChecklistId) }
    var showingResult by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val observed = revision

    fun refresh() { revision++ }

    val selected = selectedId?.let(repo::getChecklist)

    when {
        selectedId == null || selected == null -> {
            BackHandler { onHome() }
            ChecklistListScreen(
                checklists = repo.getChecklists(),
                onHome = onHome,
                onCreate = {
                    selectedId = repo.createChecklist().id
                    refresh()
                },
                onOpen = { selectedId = it },
                onRename = { checklistId, title ->
                    if (title.isNotBlank()) {
                        repo.updateChecklist(checklistId) { it.copy(title = title.trim()) }
                        refresh()
                    }
                },
                onDelete = { checklistId ->
                    repo.getChecklist(checklistId)?.let {
                        onCancelChecklistNotifications(it)
                    }
                    repo.deleteChecklist(checklistId)
                    onReconcileNotifications()
                    refresh()
                }
            )
        }

        showingResult -> {
            BackHandler { showingResult = false }
            CommonShareScreen(
                repo = repo,
                result = ChecklistResultGenerator.generate(selected),
                onBack = { showingResult = false },
                portableType = ArmyristPortableDataType.CHECKLIST,
                portableRootId = selected.id
            )
        }

        else -> {
            ChecklistDetailScreen(
                checklist = selected,
                onBack = { selectedId = null },
                onHome = onHome,
                onResult = { showingResult = true },
                onRename = {
                    if (repo.renameChecklist(selected.id, it)) refresh()
                },
                onAddItem = { name, note, groupId, notificationEnabled, scheduledTimeMinutes, notificationSoundUri ->
                    if (
                        repo.addChecklistItem(
                            selected.id,
                            name,
                            note,
                            groupId,
                            notificationEnabled,
                            scheduledTimeMinutes,
                            notificationSoundUri
                        )
                    ) {
                        if (notificationEnabled) onRequestNotificationPermission()
                        onReconcileNotifications()
                        refresh()
                    }
                },
                onEditItem = { itemId, name, note, groupId, notificationEnabled, scheduledTimeMinutes, notificationSoundUri ->
                    if (
                        repo.editChecklistItem(
                            selected.id,
                            itemId,
                            name,
                            note,
                            groupId,
                            notificationEnabled,
                            scheduledTimeMinutes,
                            notificationSoundUri
                        )
                    ) {
                        if (notificationEnabled) onRequestNotificationPermission()
                        onReconcileNotifications()
                        refresh()
                    }
                },
                onDeleteItem = { itemId ->
                    onCancelItemNotification(itemId)
                    if (repo.trashChecklistItem(selected.id, itemId)) {
                        onReconcileNotifications()
                        refresh()
                    }
                },
                onRestoreItem = {
                    if (repo.restoreChecklistItem(selected.id, it)) {
                        onReconcileNotifications()
                        refresh()
                    }
                },
                onPermanentlyDeleteItem = { itemId ->
                    onCancelItemNotification(itemId)
                    if (repo.permanentlyDeleteChecklistItem(selected.id, itemId)) {
                        onReconcileNotifications()
                        refresh()
                    }
                },
                onStatus = { itemId, status ->
                    if (repo.setChecklistStatus(selected.id, itemId, status)) {
                        onReconcileNotifications()
                        refresh()
                    }
                },
                onAddGroup = { name, color ->
                    if (repo.addChecklistGroup(selected.id, name, color)) refresh()
                },
                onGroupColor = { groupId, color ->
                    if (repo.setChecklistGroupColor(selected.id, groupId, color)) refresh()
                },
                onDeleteGroup = {
                    repo.deleteChecklistGroup(selected.id, it)
                    refresh()
                },
                onAssignGroup = { itemIds, groupId ->
                    if (repo.assignChecklistItemsToGroup(selected.id, itemIds, groupId)) refresh()
                },
                onPickNotificationSound = onPickNotificationSound,
                onBulkNotificationSound = { soundUri ->
                    if (
                        repo.setChecklistNotificationSoundForEnabledItems(
                            selected.id,
                            soundUri
                        )
                    ) {
                        onReconcileNotifications()
                        refresh()
                    }
                },
                onMemo = {
                    repo.setChecklistMemo(selected.id, it)
                    refresh()
                },
                onReset = {
                    repo.resetChecklistStatuses(selected.id)
                    onReconcileNotifications()
                    refresh()
                },
                onMove = { itemId, delta ->
                    repo.moveChecklistItem(selected.id, itemId, delta)
                    refresh()
                }
            )
        }
    }
}

@Composable
private fun ChecklistListScreen(
    checklists: List<Checklist>,
    onHome: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var renameTarget by remember { mutableStateOf<Checklist?>(null) }
    var deleteTarget by remember { mutableStateOf<Checklist?>(null) }

    Scaffold(
        topBar = {
            ArmyristTopBar(
                title = "체크리스트",
                subtitle = "CHECKLIST · 반복 점검 · AUTO SAVE",
                leadingLabel = "홈",
                onLeading = onHome
            )
        },
        floatingActionButton = {
            if (checklists.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onCreate,
                    modifier = Modifier.heightIn(min = 58.dp),
                    shape = ArmyristPanelShape,
                    containerColor = ArmyristColors.PrimaryControl,
                    contentColor = ArmyristColors.OnDark
                ) {
                    Text(
                        "+ 새 체크리스트",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { padding ->
        if (checklists.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ArmyristPanelShape,
                    colors = CardDefaults.cardColors(
                        containerColor = ArmyristColors.WorkSurface
                    ),
                    border = BorderStroke(
                        1.dp,
                        ArmyristColors.Border
                    )
                ) {
                    Column(
                        Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("저장된 체크리스트가 없습니다", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "새 체크리스트를 만들어 점검을 시작하세요.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = onCreate,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                            shape = ArmyristPanelShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ArmyristColors.PrimaryControl,
                                contentColor = ArmyristColors.OnDark
                            )
                        ) { Text("새 체크리스트 만들기") }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(checklists, key = { it.id }) { checklist ->
                    val p = ChecklistRules.progress(checklist.items)
                    Card(
                        onClick = { onOpen(checklist.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = ArmyristPanelShape,
                        colors = CardDefaults.cardColors(
                            containerColor = ArmyristColors.RaisedSurface
                        ),
                        border = BorderStroke(
                            1.dp,
                            ArmyristColors.Border
                        )
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(checklist.title, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    progressText(p),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { renameTarget = checklist }) { Text("이름") }
                            TextButton(onClick = { deleteTarget = checklist }) { Text("삭제") }
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        var renameValue by remember(target.id) { mutableStateOf(target.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("체크리스트 이름 변경") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    label = { Text("이름") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameValue.isNotBlank()) onRename(target.id, renameValue)
                        renameTarget = null
                    }
                ) { Text("확인") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("취소") }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("체크리스트 삭제") },
            text = { Text("'${target.title}'을 삭제합니다.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target.id)
                    deleteTarget = null
                }) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("취소") }
            }
        )
    }
}

private fun progressText(p: ChecklistProgress): String =
    if (p.effectiveItems == 0) {
        "진행 대상 없음 · 해당 없음 ${p.notApplicableItems}"
    } else {
        "완료 ${p.completeItems} / 미완료 ${p.incompleteItems} / 해당 없음 ${p.notApplicableItems} · ${p.completionPercent}%"
    }

@Composable
private fun ChecklistDetailScreen(
    checklist: Checklist,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onResult: () -> Unit,
    onRename: (String) -> Unit,
    onAddItem: (String, String, String?, Boolean, Int?, String?) -> Unit,
    onEditItem: (String, String, String, String?, Boolean, Int?, String?) -> Unit,
    onDeleteItem: (String) -> Unit,
    onRestoreItem: (String) -> Unit,
    onPermanentlyDeleteItem: (String) -> Unit,
    onStatus: (String, ChecklistStatus) -> Unit,
    onAddGroup: (String, String) -> Unit,
    onGroupColor: (String, String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onAssignGroup: (Set<String>, String?) -> Unit,
    onPickNotificationSound: (String?, (String) -> Unit) -> Unit,
    onBulkNotificationSound: (String?) -> Unit,
    onMemo: (String) -> Unit,
    onReset: () -> Unit,
    onMove: (String, Int) -> Unit
) {
    val context = LocalContext.current
    var titleEdit by remember { mutableStateOf(false) }
    var addingItem by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ChecklistItem?>(null) }
    var groupManager by remember { mutableStateOf(false) }
    var groupPicker by remember { mutableStateOf(false) }
    var memoEdit by remember { mutableStateOf(false) }
    var resetConfirm by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ChecklistItem?>(null) }
    var trashOpen by remember { mutableStateOf(false) }
    var permanentDeleteTarget by remember { mutableStateOf<ChecklistItem?>(null) }
    var viewMode by remember { mutableStateOf(ChecklistViewMode.DETAIL) }
    var voiceDrafts by remember { mutableStateOf<List<ChecklistVoiceDraft>?>(null) }
    var voiceMessage by remember { mutableStateOf<String?>(null) }

    var assignmentGroupId by remember { mutableStateOf<String?>(null) }
    var assignmentUngroup by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    val haptic = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 46.dp.toPx() }

    BackHandler {
        when {
            assignmentGroupId != null || assignmentUngroup -> {
                assignmentGroupId = null
                assignmentUngroup = false
                selectedIds = emptySet()
            }
            else -> onBack()
        }
    }

    val assignmentMode = assignmentGroupId != null || assignmentUngroup
    val assignmentGroup = checklist.groups.firstOrNull { it.id == assignmentGroupId }

    Scaffold(
        topBar = {
            ArmyristTopBar(
                title = checklist.title,
                subtitle = "CHECKLIST · 항목 ${checklist.items.size} · AUTO SAVE",
                leadingLabel = "홈",
                onLeading = onHome
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                    shape = ArmyristPanelShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ArmyristColors.HeaderRaised,
                        contentColor = ArmyristColors.OnDark
                    )
                ) {
                    Text("목록", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { titleEdit = true },
                    modifier = Modifier.weight(1f),
                    shape = ArmyristPanelShape,
                    border = BorderStroke(1.dp, ArmyristColors.PrimaryControl),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = ArmyristColors.WorkSurface,
                        contentColor = ArmyristColors.PrimaryText
                    )
                ) {
                    Text("제목 수정", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onResult,
                    modifier = Modifier.weight(1f),
                    shape = ArmyristPanelShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ArmyristColors.PrimaryControl,
                        contentColor = ArmyristColors.OnDark
                    )
                ) {
                    Text("결과 전달", fontWeight = FontWeight.Bold)
                }

            }

            OfflineVoiceButton(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                onTranscript = { transcript ->
                    voiceDrafts = KoreanVoiceStructurer.checklist(transcript)
                },
                onMessage = { voiceMessage = it }
            )

            ChecklistProgressSummary(checklist)

            if (!assignmentMode) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ChecklistUtilityButton(text = "그룹", onClick = { groupManager = true })
                    ChecklistUtilityButton(text = "그룹 지정", onClick = { groupPicker = true })
                    ChecklistUtilityButton(
                        text = "알람음 일괄",
                        onClick = {
                            val enabledItems =
                                checklist.items.filter {
                                    it.notificationEnabled
                                }

                            if (enabledItems.isNotEmpty()) {
                                onPickNotificationSound(
                                    enabledItems
                                        .firstOrNull()
                                        ?.notificationSoundUri
                                ) { soundUri ->
                                    onBulkNotificationSound(soundUri)
                                }
                            }
                        }
                    )
                    ChecklistUtilityButton(text = "메모", onClick = { memoEdit = true })
                    ChecklistUtilityButton(text = "초기화", onClick = { resetConfirm = true })
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (checklist.deletedItems.isNotEmpty()) {
                        TextButton(onClick = { trashOpen = true }) {
                            Text("삭제된 항목 ${checklist.deletedItems.size}")
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val detailSelected =
                            viewMode == ChecklistViewMode.DETAIL
                        val compactSelected =
                            viewMode == ChecklistViewMode.COMPACT

                        OutlinedButton(
                            onClick = {
                                viewMode = ChecklistViewMode.DETAIL
                            },
                            shape = ArmyristPanelShape,
                            border = BorderStroke(
                                1.dp,
                                if (detailSelected) {
                                    ArmyristColors.PrimaryControl
                                } else {
                                    ArmyristColors.Border
                                }
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor =
                                    if (detailSelected) {
                                        ArmyristColors.SecondaryControl
                                    } else {
                                        ArmyristColors.WorkSurface
                                    },
                                contentColor = ArmyristColors.PrimaryText
                            ),
                            contentPadding = PaddingValues(
                                horizontal = 12.dp,
                                vertical = 6.dp
                            )
                        ) {
                            Text("자세히")
                        }

                        OutlinedButton(
                            onClick = {
                                viewMode = ChecklistViewMode.COMPACT
                            },
                            shape = ArmyristPanelShape,
                            border = BorderStroke(
                                1.dp,
                                if (compactSelected) {
                                    ArmyristColors.PrimaryControl
                                } else {
                                    ArmyristColors.Border
                                }
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor =
                                    if (compactSelected) {
                                        ArmyristColors.SecondaryControl
                                    } else {
                                        ArmyristColors.WorkSurface
                                    },
                                contentColor = ArmyristColors.PrimaryText
                            ),
                            contentPadding = PaddingValues(
                                horizontal = 12.dp,
                                vertical = 6.dp
                            )
                        ) {
                            Text("간략히")
                        }
                    }
                }
            } else {
                Surface(
                    color = assignmentGroup?.let {
                        parseColor(it.color).copy(alpha = 0.14f)
                    } ?: ArmyristColors.SecondaryControl,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp, 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (assignmentUngroup) "미지정으로 변경" else "${assignmentGroup?.name ?: "그룹"} 지정 중",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "항목을 터치해 선택 · ${selectedIds.size}개",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = {
                            assignmentGroupId = null
                            assignmentUngroup = false
                            selectedIds = emptySet()
                        }) { Text("취소") }
                        Button(
                            enabled = selectedIds.isNotEmpty(),
                            onClick = {
                                onAssignGroup(
                                    selectedIds,
                                    if (assignmentUngroup) null else assignmentGroupId
                                )
                                assignmentGroupId = null
                                assignmentUngroup = false
                                selectedIds = emptySet()
                            }
                        ) { Text("확인") }
                    }
                }
            }

            HorizontalDivider()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp, 6.dp, 8.dp, 24.dp),
                verticalArrangement = Arrangement.spacedBy(
                    if (viewMode == ChecklistViewMode.COMPACT) 4.dp else 7.dp
                )
            ) {
                itemsIndexed(
                    checklist.items.sortedBy { it.order },
                    key = { _, item -> item.id }
                ) { index, item ->
                    val group = checklist.groups.firstOrNull { it.id == item.groupId }
                    val selected = item.id in selectedIds

                    var dragDistance by remember(item.id) { mutableFloatStateOf(0f) }
                    var visualOffset by remember(item.id) { mutableFloatStateOf(0f) }
                    var dragging by remember(item.id) { mutableStateOf(false) }

                    val cardColor = when {
                        selected -> assignmentGroup?.let {
                            parseColor(it.color).copy(alpha = 0.26f)
                        } ?: ArmyristColors.SecondaryControl
                        group != null -> parseColor(group.color).copy(alpha = 0.13f)
                        else -> ArmyristColors.RaisedSurface
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardColor),
                        shape = ArmyristPanelShape,
                        border = BorderStroke(
                            1.dp,
                            group?.let { parseColor(it.color).copy(alpha = 0.72f) }
                                ?: ArmyristColors.Divider
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (dragging) 1f else 0f)
                            .offset { IntOffset(0, visualOffset.roundToInt()) }
                            .pointerInput(item.id, assignmentMode) {
                                if (!assignmentMode) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            dragDistance = 0f
                                            visualOffset = 0f
                                            dragging = true
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragCancel = {
                                            dragDistance = 0f
                                            visualOffset = 0f
                                            dragging = false
                                        },
                                        onDragEnd = {
                                            dragDistance = 0f
                                            visualOffset = 0f
                                            dragging = false
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragDistance += amount.y
                                            visualOffset += amount.y

                                            if (dragDistance >= threshold) {
                                                onMove(item.id, 1)
                                                dragDistance -= threshold
                                                visualOffset -= threshold
                                            } else if (dragDistance <= -threshold) {
                                                onMove(item.id, -1)
                                                dragDistance += threshold
                                                visualOffset += threshold
                                            }
                                        }
                                    )
                                }
                            }
                            .clickable {
                                if (assignmentMode) {
                                    selectedIds =
                                        if (selected) selectedIds - item.id
                                        else selectedIds + item.id
                                } else if (!dragging) {
                                    editingItem = item
                                }
                            }
                    ) {
                        if (viewMode == ChecklistViewMode.COMPACT && !assignmentMode) {
                            CompactChecklistRow(
                                index = index,
                                item = item,
                                groupName = group?.name ?: "미지정",
                                onStatus = { onStatus(item.id, it) }
                            )
                        } else {
                            DetailChecklistRow(
                                index = index,
                                item = item,
                                groupName = group?.name ?: "미지정",
                                selected = selected,
                                assignmentMode = assignmentMode,
                                onStatus = { onStatus(item.id, it) },
                                onEdit = { editingItem = item },
                                onDelete = { deleteTarget = item }
                            )
                        }
                    }
                }

                item {
                    OutlinedButton(
                        onClick = { addingItem = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = ArmyristPanelShape,
                        border = BorderStroke(1.dp, ArmyristColors.PrimaryControl)
                    ) { Text("+ 새 항목 추가") }
                }

                item {
                    Card(
                        onClick = { memoEdit = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = ArmyristPanelShape,
                        colors = CardDefaults.cardColors(
                            containerColor = ArmyristColors.RaisedSurface
                        ),
                        border = BorderStroke(1.dp, ArmyristColors.Border)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text("메모", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("편집", color = ArmyristColors.PrimaryControl)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                checklist.memo.ifBlank { "메모가 없습니다. 눌러서 입력하세요." },
                                color = if (checklist.memo.isBlank())
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }

    if (titleEdit) {
        TextEditDialog("제목 변경", checklist.title, onDismiss = { titleEdit = false }) {
            if (it.trim().isNotEmpty()) onRename(it)
            titleEdit = false
        }
    }

    if (addingItem) {
        ItemEditDialog(
            item = null,
            groups = checklist.groups,
            onPickNotificationSound = onPickNotificationSound,
            onDismiss = { addingItem = false }
        ) {
                name, note, groupId, notificationEnabled, scheduledTimeMinutes, notificationSoundUri ->
            onAddItem(
                name,
                note,
                groupId,
                notificationEnabled,
                scheduledTimeMinutes,
                notificationSoundUri
            )
            addingItem = false
        }
    }

    editingItem?.let { item ->
        ItemEditDialog(
            item = item,
            groups = checklist.groups,
            onPickNotificationSound = onPickNotificationSound,
            onDismiss = { editingItem = null }
        ) {
                name, note, groupId, notificationEnabled, scheduledTimeMinutes, notificationSoundUri ->
            onEditItem(
                item.id,
                name,
                note,
                groupId,
                notificationEnabled,
                scheduledTimeMinutes,
                notificationSoundUri
            )
            editingItem = null
        }
    }

    if (memoEdit) {
        TextEditDialog("전체 메모", checklist.memo, true, { memoEdit = false }) {
            onMemo(it)
            memoEdit = false
        }
    }

    if (groupManager) {
        GroupManagerDialog(
            checklist = checklist,
            onAdd = onAddGroup,
            onColor = onGroupColor,
            onDelete = onDeleteGroup,
            onDismiss = { groupManager = false }
        )
    }

    if (groupPicker) {
        GroupPickerDialog(
            checklist = checklist,
            onDismiss = { groupPicker = false },
            onSelect = { groupId, ungroup ->
                groupPicker = false
                assignmentGroupId = groupId
                assignmentUngroup = ungroup
                selectedIds = emptySet()
            }
        )
    }

    if (resetConfirm) {
        AlertDialog(
            onDismissRequest = { resetConfirm = false },
            title = { Text("상태 초기화") },
            text = { Text("모든 항목을 미완료로 되돌립니다. 항목·그룹·비고·메모는 유지됩니다.") },
            confirmButton = {
                TextButton(onClick = {
                    onReset()
                    resetConfirm = false
                }) { Text("초기화") }
            },
            dismissButton = {
                TextButton(onClick = { resetConfirm = false }) { Text("취소") }
            }
        )
    }

    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("항목 삭제") },
            text = {
                Text("'${item.name}'을 삭제된 항목으로 이동합니다. 나중에 복구할 수 있습니다.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteItem(item.id)
                    deleteTarget = null
                }) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("취소") }
            }
        )
    }

    if (trashOpen) {
        DeletedItemsDialog(
            checklist = checklist,
            onRestore = onRestoreItem,
            onRequestPermanentDelete = { permanentDeleteTarget = it },
            onDismiss = { trashOpen = false }
        )
    }

    permanentDeleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { permanentDeleteTarget = null },
            title = { Text("영구 삭제") },
            text = {
                Text("'${item.name}'을 완전히 삭제합니다. 이 작업은 복구할 수 없습니다.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onPermanentlyDeleteItem(item.id)
                    permanentDeleteTarget = null
                }) { Text("영구 삭제") }
            },
            dismissButton = {
                TextButton(onClick = { permanentDeleteTarget = null }) { Text("취소") }
            }
        )
    }

    voiceDrafts?.let { drafts ->
        ChecklistVoiceReviewDialog(
            initial = drafts,
            onDismiss = { voiceDrafts = null },
            onApply = { applied ->
                applied.forEach { d ->
                    onAddItem(
                        d.name,
                        d.note,
                        null,
                        false,
                        d.scheduledTimeMinutes,
                        null
                    )
                }
                voiceDrafts = null
            }
        )
    }

    voiceMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { voiceMessage = null },
            title = { Text("음성 입력") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { voiceMessage = null }) { Text("확인") } }
        )
    }
}

@Composable
private fun ChecklistUtilityButton(
    text: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        shape = ArmyristPanelShape,
        border = BorderStroke(
            1.dp,
            ArmyristColors.Border
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = ArmyristColors.WorkSurface,
            contentColor = ArmyristColors.PrimaryText
        ),
        contentPadding =
            PaddingValues(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun ChecklistProgressSummary(checklist: Checklist) {
    val p = ChecklistRules.progress(checklist.items)
    Surface(
        color = ArmyristColors.WorkSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("진행 현황", style = MaterialTheme.typography.labelMedium)
            Text(progressText(p), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DetailChecklistRow(
    index: Int,
    item: ChecklistItem,
    groupName: String,
    selected: Boolean,
    assignmentMode: Boolean,
    onStatus: (ChecklistStatus) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column(Modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (selected) "✓" else "${index + 1}.",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(34.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(groupName, style = MaterialTheme.typography.bodySmall)
                if (item.note.isNotBlank()) {
                    Text(
                        "비고: ${item.note}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (item.notificationEnabled && item.scheduledTimeMinutes != null) {
                    val context = LocalContext.current
                    val past = ChecklistNotificationManager.scheduledEpochMillis(
                        item.scheduledTimeMinutes
                    ) == null
                    val deliveryUnavailable =
                        !ChecklistNotificationManager.notificationsEnabled(context, item)

                    Text(
                        buildString {
                            append("알림 ${formatChecklistTime(item.scheduledTimeMinutes)}")
                            if (past) append(" · 지난 시각")
                            else if (deliveryUnavailable) append(" · 현재 알림 사용 불가")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!assignmentMode) {
                TextButton(onClick = onEdit) { Text("편집") }
                TextButton(onClick = onDelete) { Text("삭제") }
            }
        }

        if (!assignmentMode) {
            Spacer(Modifier.height(8.dp))
            StatusSelector(item.status, onStatus)
        }
    }
}

@Composable
private fun CompactChecklistRow(
    index: Int,
    item: ChecklistItem,
    groupName: String,
    onStatus: (ChecklistStatus) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${index + 1}.", fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                buildString {
                    append(groupName)
                    if (item.notificationEnabled && item.scheduledTimeMinutes != null) {
                        append(" · ")
                        append(formatChecklistTime(item.scheduledTimeMinutes))
                    }
                },
                style = MaterialTheme.typography.labelSmall
            )
        }
        CompactStatusButton(
            text = when (item.status) {
                ChecklistStatus.INCOMPLETE -> "미완료"
                ChecklistStatus.COMPLETE -> "완료"
                ChecklistStatus.NOT_APPLICABLE -> "해당 없음"
            },
            status = item.status,
            onClick = {
                val next = when (item.status) {
                    ChecklistStatus.INCOMPLETE -> ChecklistStatus.COMPLETE
                    ChecklistStatus.COMPLETE -> ChecklistStatus.NOT_APPLICABLE
                    ChecklistStatus.NOT_APPLICABLE -> ChecklistStatus.INCOMPLETE
                }
                onStatus(next)
            }
        )
    }
}

@Composable
private fun StatusSelector(
    status: ChecklistStatus,
    onSelect: (ChecklistStatus) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        StatusButton(
            "미완료",
            status == ChecklistStatus.INCOMPLETE,
            Color(0xFFE8E5EA),
            Color(0xFF514C56),
            Modifier.weight(1f)
        ) { onSelect(ChecklistStatus.INCOMPLETE) }

        StatusButton(
            "완료",
            status == ChecklistStatus.COMPLETE,
            Color(0xFFD7F0DE),
            Color(0xFF1E6335),
            Modifier.weight(1f)
        ) { onSelect(ChecklistStatus.COMPLETE) }

        StatusButton(
            "해당 없음",
            status == ChecklistStatus.NOT_APPLICABLE,
            Color(0xFFDDE9F3),
            Color(0xFF365970),
            Modifier.weight(1f)
        ) { onSelect(ChecklistStatus.NOT_APPLICABLE) }
    }
}

@Composable
private fun StatusButton(
    text: String,
    selected: Boolean,
    selectedColor: Color,
    selectedTextColor: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        shape = ArmyristPanelShape,
        color = if (selected) selectedColor else MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
        contentColor = if (selected) selectedTextColor else MaterialTheme.colorScheme.onSurfaceVariant,
        border = ButtonDefaults.outlinedButtonBorder(enabled = true)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun CompactStatusButton(
    text: String,
    status: ChecklistStatus,
    onClick: () -> Unit
) {
    val bg = when (status) {
        ChecklistStatus.INCOMPLETE -> Color(0xFFE8E5EA)
        ChecklistStatus.COMPLETE -> Color(0xFFD7F0DE)
        ChecklistStatus.NOT_APPLICABLE -> Color(0xFFDDE9F3)
    }
    val fg = when (status) {
        ChecklistStatus.INCOMPLETE -> Color(0xFF514C56)
        ChecklistStatus.COMPLETE -> Color(0xFF1E6335)
        ChecklistStatus.NOT_APPLICABLE -> Color(0xFF365970)
    }

    Surface(
        onClick = onClick,
        color = bg,
        contentColor = fg,
        shape = ArmyristPanelShape
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TextEditDialog(
    title: String,
    initial: String,
    multiline: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(initial) { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                minLines = if (multiline) 4 else 1,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("확인") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

@Composable
private fun ItemEditDialog(
    item: ChecklistItem?,
    groups: List<ChecklistGroup>,
    onPickNotificationSound: (String?, (String) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String?, Boolean, Int?, String?) -> Unit
) {
    var name by remember { mutableStateOf(item?.name ?: "") }
    var note by remember { mutableStateOf(item?.note ?: "") }
    var groupId by remember { mutableStateOf(item?.groupId) }
    var notificationEnabled by remember {
        mutableStateOf(item?.notificationEnabled ?: false)
    }
    var timeRaw by remember {
        mutableStateOf(
            item?.scheduledTimeMinutes?.let(::formatChecklistTimeRaw) ?: ""
        )
    }
    var notificationSoundUri by remember {
        mutableStateOf(item?.notificationSoundUri)
    }
    var error by remember { mutableStateOf("") }
    val context = LocalContext.current

    val parsedTime = parseChecklistTime(timeRaw)
    val isPast = notificationEnabled &&
        parsedTime != null &&
        ChecklistNotificationManager.scheduledEpochMillis(parsedTime) == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "항목 추가" else "항목 편집") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("항목명") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("비고") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Text("그룹")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        FilterChip(
                            selected = groupId == null,
                            onClick = { groupId = null },
                            label = { Text("미지정") }
                        )
                        groups.sortedBy { it.order }.forEach { group ->
                            FilterChip(
                                selected = groupId == group.id,
                                onClick = { groupId = group.id },
                                label = { Text(group.name) }
                            )
                        }
                    }
                }
                item {
                    HorizontalDivider()
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("알림", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (notificationEnabled) "지정 시각 알림 사용" else "알림 사용 안 함",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = notificationEnabled,
                            onCheckedChange = {
                                notificationEnabled = it
                                error = ""
                            }
                        )
                    }
                }
                if (notificationEnabled) {
                    item {
                        OutlinedTextField(
                            value = timeRaw,
                            onValueChange = {
                                timeRaw = it.take(5)
                                error = ""
                            },
                            label = { Text("예정시각") },
                            supportingText = {
                                Text("HHMM 입력 · 예: 1530")
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (isPast) {
                        item {
                            Text(
                                "지난 시각입니다. 알림이 예약되지 않습니다.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                if (notificationEnabled) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "알람음",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        ChecklistNotificationManager.soundTitle(
                                            context,
                                            notificationSoundUri
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        onPickNotificationSound(
                                            notificationSoundUri
                                        ) { picked ->
                                            notificationSoundUri = picked
                                        }
                                    }
                                ) {
                                    Text("선택")
                                }
                            }
                        }
                    }
                }

                if (error.isNotBlank()) {
                    item {
                        Text(
                            error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.trim().isNotEmpty(),
                onClick = {
                    val time = parseChecklistTime(timeRaw)
                    when {
                        notificationEnabled && time == null -> {
                            error = "알림을 사용하려면 올바른 시각을 입력하세요."
                        }
                        else -> {
                            onConfirm(
                                name.trim(),
                                note.trim(),
                                groupId,
                                notificationEnabled,
                                time,
                                notificationSoundUri
                            )
                        }
                    }
                }
            ) { Text("확인") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

private fun parseChecklistTime(raw: String): Int? {
    val normalized = raw.trim().replace(":", "")
    if (normalized.length != 4 || normalized.any { !it.isDigit() }) return null
    val hour = normalized.substring(0, 2).toIntOrNull() ?: return null
    val minute = normalized.substring(2, 4).toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

private fun formatChecklistTime(minutes: Int): String =
    "%02d:%02d".format(minutes / 60, minutes % 60)

private fun formatChecklistTimeRaw(minutes: Int): String =
    "%02d%02d".format(minutes / 60, minutes % 60)

private val GROUP_COLORS = listOf(
    "#6750A4", "#2E7D32", "#1565C0", "#C62828",
    "#EF6C00", "#00838F", "#6D4C41", "#546E7A"
)

@Composable
private fun GroupManagerDialog(
    checklist: Checklist,
    onAdd: (String, String) -> Unit,
    onColor: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var creating by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("그룹 관리") },
        text = {
            Column {
                if (checklist.groups.isEmpty()) {
                    Text(
                        "그룹이 없습니다.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(checklist.groups.sortedBy { it.order }, key = { it.id }) { group ->
                            Surface(
                                color = parseColor(group.color).copy(alpha = 0.12f),
                                shape = ArmyristPanelShape
                            ) {
                                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier.size(18.dp)
                                                .background(parseColor(group.color), CircleShape)
                                        )
                                        Spacer(Modifier.width(9.dp))
                                        Text(group.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                        TextButton(onClick = { onDelete(group.id) }) { Text("삭제") }
                                    }
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        GROUP_COLORS.forEach { color ->
                                            ColorDot(
                                                color = color,
                                                selected = group.color == color,
                                                onClick = { onColor(group.id, color) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { creating = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("+ 그룹 추가") }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        }
    )

    if (creating) {
        GroupCreateDialog(
            onDismiss = { creating = false },
            onConfirm = { name, color ->
                onAdd(name, color)
                creating = false
            }
        )
    }
}

@Composable
private fun GroupCreateDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(GROUP_COLORS.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("그룹 추가") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("그룹명") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("색상")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GROUP_COLORS.forEach { candidate ->
                        ColorDot(
                            color = candidate,
                            selected = color == candidate,
                            onClick = { color = candidate }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.trim().isNotEmpty(),
                onClick = { onConfirm(name.trim(), color) }
            ) { Text("추가") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

@Composable
private fun ColorDot(
    color: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.size(if (selected) 36.dp else 32.dp),
        shape = CircleShape,
        color = parseColor(color),
        onClick = onClick
    ) {
        if (selected) {
            Box(contentAlignment = Alignment.Center) {
                Text("✓", color = Color.White)
            }
        }
    }
}

@Composable
private fun GroupPickerDialog(
    checklist: Checklist,
    onDismiss: () -> Unit,
    onSelect: (String?, Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("그룹 지정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Surface(
                    color = ArmyristColors.RaisedSurface,
                    shape = ArmyristPanelShape,
                    modifier = Modifier.fillMaxWidth().clickable {
                        onSelect(null, true)
                    }
                ) {
                    Text("미지정", modifier = Modifier.padding(14.dp), fontWeight = FontWeight.SemiBold)
                }

                checklist.groups.sortedBy { it.order }.forEach { group ->
                    Surface(
                        color = parseColor(group.color).copy(alpha = 0.12f),
                        shape = ArmyristPanelShape,
                        modifier = Modifier.fillMaxWidth().clickable {
                            onSelect(group.id, false)
                        }
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(16.dp)
                                    .background(parseColor(group.color), CircleShape)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(group.name, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

@Composable
private fun DeletedItemsDialog(
    checklist: Checklist,
    onRestore: (String) -> Unit,
    onRequestPermanentDelete: (ChecklistItem) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("삭제된 항목") },
        text = {
            if (checklist.deletedItems.isEmpty()) {
                Text("삭제된 항목이 없습니다.")
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(
                        checklist.deletedItems.sortedByDescending { it.order },
                        key = { it.id }
                    ) { item ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(item.name, fontWeight = FontWeight.SemiBold)

                                val groupName = item.groupId?.let { groupId ->
                                    checklist.groups.firstOrNull { it.id == groupId }?.name
                                }
                                val contextText = buildList {
                                    if (groupName != null) add(groupName)
                                    if (item.note.isNotBlank()) add("비고: ${item.note}")
                                }.joinToString(" · ")

                                if (contextText.isNotBlank()) {
                                    Text(
                                        contextText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { onRestore(item.id) }) {
                                        Text("복구")
                                    }
                                    TextButton(onClick = { onRequestPermanentDelete(item) }) {
                                        Text("영구 삭제")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        }
    )
}

private fun parseColor(hex: String): Color =
    runCatching {
        Color(android.graphics.Color.parseColor(hex))
    }.getOrDefault(Color(0xFF596B45))
