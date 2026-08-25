@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.seolhwa.armyrist.timeplan.v3.ui

import android.view.HapticFeedbackConstants
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.seolhwa.armyrist.*
import com.seolhwa.armyrist.collection.*
import com.seolhwa.armyrist.stage2.data.CoreSuiteRepository
import com.seolhwa.armyrist.stage2.domain.ToolResult
import com.seolhwa.armyrist.timeplan.data.TimePlanV2Repository
import com.seolhwa.armyrist.timeplan.v3.data.DateAwareTimePlanRepository
import com.seolhwa.armyrist.timeplan.v3.data.DateAwareTimePlanJson
import com.seolhwa.armyrist.trash.*
import org.json.JSONObject
import com.seolhwa.armyrist.timeplan.v3.domain.*
import com.seolhwa.armyrist.voice.*
import kotlinx.coroutines.launch
import java.time.*
import java.io.File
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

private const val DATE_AWARE_TRASH_PAYLOAD_VERSION = 6
private const val LEGACY_TRASH_PAYLOAD_VERSION = 2

@Composable
fun DateAwareTimePlanApp(
    repository: DateAwareTimePlanRepository,
    legacyRepository: TimePlanV2Repository,
    coreRepository: CoreSuiteRepository,
    trashRepository: CommonTrashRepository,
    onHome: () -> Unit,
    onOpenExecution: (String, String, Set<String>) -> Unit
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedLegacyId by rememberSaveable { mutableStateOf<String?>(null) }
    var sharingId by rememberSaveable { mutableStateOf<String?>(null) }
    var trashOpen by rememberSaveable { mutableStateOf(false) }
    var trashMessage by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val observed = revision

    if (trashOpen) {
        // System Back and the visible "뒤로" button must have the same contract:
        // close Trash and return to the TimePlan list, never finish TimePlanActivity.
        BackHandler(enabled = true) {
            trashOpen = false
        }

        val trashItems =
            trashRepository.getItems(TrashToolType.TIME_PLAN)

        CommonTrashScreen(
            toolLabel = "시간계획",
            items = trashItems,
            retentionDays = trashRepository.retentionDays(),
            onBack = { trashOpen = false },
            onRetentionChange = { days ->
                trashRepository.setRetentionDays(days)
                revision++
            },
            onRestore = { item ->
                when (item.payloadVersion) {
                    LEGACY_TRASH_PAYLOAD_VERSION -> {
                        if (legacyRepository.restoreTrashPayload(item.payload)) {
                            trashRepository.permanentlyDelete(item.id)
                            revision++
                        } else {
                            trashMessage =
                                "이전 형식 시간계획을 복구하지 못했습니다. 같은 계획이 이미 존재하는지 확인해주세요."
                        }
                    }

                    else -> {
                        val restored =
                            runCatching {
                                DateAwareTimePlanJson.decode(JSONObject(item.payload))
                            }.getOrNull()

                        when {
                            restored == null -> {
                                trashMessage = "휴지통 데이터를 읽지 못했습니다."
                            }
                            repository.contains(restored.id) -> {
                                trashMessage =
                                    "같은 시간계획이 이미 존재하여 복구하지 않았습니다."
                            }
                            repository.restoreDeletedPlan(restored) -> {
                                trashRepository.permanentlyDelete(item.id)
                                revision++
                            }
                            else -> {
                                trashMessage = "시간계획을 복구하지 못했습니다."
                            }
                        }
                    }
                }
            },
            onPermanentDelete = { item ->
                trashRepository.permanentlyDelete(item.id)
                revision++
            }
        )

        trashMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { trashMessage = null },
                shape = ArmyristPanelShape,
                containerColor = ArmyristColors.RaisedSurface,
                tonalElevation = 0.dp,
                title = { Text("확인", fontWeight = FontWeight.Bold) },
                text = { Text(message) },
                confirmButton = {
                    Button(
                        onClick = { trashMessage = null },
                        shape = ArmyristPanelShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ArmyristColors.PrimaryControl,
                            contentColor = ArmyristColors.OnDark
                        )
                    ) {
                        Text("확인")
                    }
                }
            )
        }
        return
    }

    val sharing = sharingId?.let(repository::getPlan)
    if (sharing != null) {
        CommonShareScreen(
            repo = coreRepository,
            result = generateDateAwareResult(sharing),
            onBack = { sharingId = null },
            portableType = ArmyristPortableDataType.TIME_PLAN,
            portableRootId = sharing.id
        )
        return
    }

    val pendingLegacyId = selectedLegacyId
    if (pendingLegacyId != null) {
        val legacy = legacyRepository.getPlan(pendingLegacyId)
        if (legacy != null && !repository.contains(pendingLegacyId)) {
            LegacyDateMigrationDialog(
                title = legacy.title,
                onDismiss = { selectedLegacyId = null },
                onApply = { date ->
                    repository.migrateLegacy(legacy, date)?.let { migrated ->
                        // Migration has committed the full DateAware snapshot.
                        // Remove the old v2 source only after that succeeds so it
                        // cannot reappear later as a "날짜 기능 이전 계획".
                        legacyRepository.delete(legacy.id)
                        selectedLegacyId = null
                        selectedId = migrated.id
                        revision++
                    }
                }
            )
            return
        }
        selectedLegacyId = null
    }

    val selected = selectedId?.let(repository::getPlan)
    if (selected == null) {
        val datePlans = repository.getPlans()

        // Older Armyrist builds kept the original v2 plan after successful date
        // migration. While the DateAware plan existed that v2 copy was hidden by
        // ID, but deleting/restoring/importing could expose it again and make it
        // look as if old plans had been newly created.
        //
        // Exact-ID duplicates are migration shadows, not independent documents.
        // Remove them only while their DateAware replacement is present.
        val dateAwareIds = datePlans.map { it.id }.toSet()
        legacyRepository.getPlans()
            .filter { it.id in dateAwareIds }
            .forEach { shadow ->
                legacyRepository.delete(shadow.id)
            }

        val legacyPlans = legacyRepository.getPlans()
        DateAwarePlanList(
            datePlans = datePlans,
            legacyPlans = legacyPlans.map { it.id to it.title },
            onHome = onHome,
            onCreate = {
                val plan = repository.createPlan()
                revision++
                selectedId = plan.id
            },
            onOpen = { selectedId = it },
            onOrderChanged = { orderedIds ->
                if (repository.setPlanOrder(orderedIds)) revision++
            },
            onRename = { id, title ->
                repository.getPlan(id)?.let { current ->
                    if (title.isNotBlank()) {
                        repository.commit(current.copy(title = title.trim(), updatedAt = System.currentTimeMillis().toString()))
                        revision++
                    }
                }
            },
            onOpenTrash = { trashOpen = true },
            trashCount = trashRepository.getItems(TrashToolType.TIME_PLAN).size,
            onDelete = { id ->
                val current = repository.getPlan(id)
                if (current != null) {
                    val trashItem =
                        trashRepository.moveToTrash(
                            toolType = TrashToolType.TIME_PLAN,
                            originalId = current.id,
                            title = current.title,
                            payloadVersion = DATE_AWARE_TRASH_PAYLOAD_VERSION,
                            payload = DateAwareTimePlanJson.encode(current).toString()
                        )

                    if (trashItem != null) {
                        repository.delete(id)
                        if (selectedId == id) selectedId = null
                        revision++
                    } else {
                        trashMessage = "휴지통에 저장하지 못해 삭제를 취소했습니다."
                    }
                }
            },
            onDeleteLegacy = { id ->
                val legacy = legacyRepository.getPlan(id)
                val payload = legacyRepository.exportTrashPayload(id)

                if (legacy == null || payload == null) {
                    trashMessage = "이전 형식 시간계획을 읽지 못해 삭제를 취소했습니다."
                } else {
                    val trashItem =
                        trashRepository.moveToTrash(
                            toolType = TrashToolType.TIME_PLAN,
                            originalId = legacy.id,
                            title = legacy.title,
                            payloadVersion = LEGACY_TRASH_PAYLOAD_VERSION,
                            payload = payload
                        )

                    when {
                        trashItem == null -> {
                            trashMessage = "휴지통에 저장하지 못해 삭제를 취소했습니다."
                        }

                        legacyRepository.delete(id) -> {
                            if (selectedLegacyId == id) selectedLegacyId = null
                            revision++
                        }

                        else -> {
                            // Keep deletion atomic from the user's perspective:
                            // if the active legacy store could not remove the plan,
                            // roll back the just-created Trash duplicate.
                            trashRepository.permanentlyDelete(trashItem.id)
                            trashMessage =
                                "이전 형식 시간계획을 삭제하지 못했습니다. 원본은 유지됩니다."
                        }
                    }
                }
            },
            onOpenLegacy = { selectedLegacyId = it }
        )
    } else {
        DateAwarePlanDetail(
            plan = selected,
            onHome = onHome,
            onBack = { selectedId = null },
            onResult = { sharingId = selected.id },
            onOpenExecution = { mode, pointIds -> onOpenExecution(selected.id, mode, pointIds) },
            onDeleteEvent = { eventId ->
                // Emergency safety boundary: point deletion must never be allowed to
                // tear down the whole TimePlan screen if an unexpected runtime error
                // occurs inside persistence/topology code.
                val selectedPlanId = selected.id
                val result = runCatching {
                    repository.deleteEvent(selectedPlanId, eventId)
                }.getOrElse {
                    android.util.Log.e(
                        "Armyrist-TimePlan",
                        "MIDWAY delete failed plan=$selectedPlanId event=$eventId",
                        it
                    )
                    false
                }
                if (result) {
                    // Keep the same document selected explicitly across recomposition.
                    selectedId = selectedPlanId
                    revision++
                }
                result
            },
            onCommit = { changed ->
                // Re-read the repository before a detail-screen commit. Action edits
                // and moves are performed in TimePlanExecutionActivity and may have
                // changed since this detail composable acquired its plan snapshot.
                //
                // Never let an older detail snapshot overwrite a newly moved Action
                // back to its previous parent/time. Intentional "함께 이동" time deltas
                // are replayed by rebaseDetailActions().
                val latest = repository.getPlan(changed.id)
                val safeCandidate =
                    if (latest != null) {
                        TimePlanExecutionRules.rebaseDetailActions(
                            base = selected,
                            candidate = changed,
                            current = latest
                        )
                    } else {
                        changed
                    }

                if (
                    repository.commit(
                        safeCandidate.copy(updatedAt = System.currentTimeMillis().toString())
                    )
                ) revision++
            }
        )
    }
}

@Composable
private fun DateAwarePlanList(
    datePlans: List<DateAwareTimePlan>,
    legacyPlans: List<Pair<String,String>>,
    onHome: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    onOrderChanged: (List<String>) -> Unit,
    onRename: (String, String) -> Unit,
    onOpenTrash: () -> Unit,
    trashCount: Int,
    onDelete: (String) -> Unit,
    onDeleteLegacy: (String) -> Unit,
    onOpenLegacy: (String) -> Unit
) {
    var renameTarget by remember { mutableStateOf<DateAwareTimePlan?>(null) }
    var deleteTarget by remember { mutableStateOf<DateAwareTimePlan?>(null) }
    var legacyDeleteTarget by remember {
        mutableStateOf<Pair<String, String>?>(null)
    }
    val context = LocalContext.current
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val reorderScope = rememberCoroutineScope()

    val collectionRepository = remember(context) { CommonCollectionRepository(context) }
    var collectionRevision by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val observedCollectionRevision = collectionRevision
    var openedFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPlanIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val selectionMode = selectedPlanIds.isNotEmpty()
    var createFolderDialog by remember { mutableStateOf(false) }
    var moveFolderDialog by remember { mutableStateOf(false) }
    var folderMenuTarget by remember { mutableStateOf<ArmyristCollectionFolder?>(null) }
    var renameFolderTarget by remember { mutableStateOf<ArmyristCollectionFolder?>(null) }
    var deleteFolderTarget by remember { mutableStateOf<ArmyristCollectionFolder?>(null) }
    var coverFolderId by remember { mutableStateOf<String?>(null) }

    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        val folderId = coverFolderId
        coverFolderId = null
        if (uri != null && folderId != null) {
            if (collectionRepository.importCoverImage(folderId, uri)) {
                collectionRevision++
            }
        }
    }

    val folders = remember(collectionRevision, datePlans.map { it.id }) {
        collectionRepository.folders(CollectionToolType.TIME_PLAN)
    }
    val validPlanIds = datePlans.map { it.id }.toSet()
    val memberToFolder = folders
        .flatMap { folder -> folder.memberIds.map { memberId -> memberId to folder.id } }
        .filter { it.first in validPlanIds }
        .toMap()
    val openedFolder = openedFolderId?.let { id -> folders.firstOrNull { it.id == id } }

    // TimePlan list navigation contract:
    // - Back while multi-select is active exits selection first.
    // - Back while inside a folder returns to the TimePlan root list.
    // - Only the root list delegates Back to the Activity/Home behavior.
    BackHandler(enabled = selectionMode || openedFolderId != null) {
        when {
            selectionMode -> selectedPlanIds = emptyList()
            openedFolderId != null -> {
                openedFolderId = null
                selectedPlanIds = emptyList()
            }
        }
    }

    val orderedPlans = remember { mutableStateListOf<DateAwareTimePlan>() }
    val folderVisualOrder = remember { mutableStateListOf<String>() }
    var draggingPlanId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var reorderDirty by remember { mutableStateOf(false) }

    LaunchedEffect(datePlans.map { Triple(it.id, it.title, it.updatedAt) }, draggingPlanId) {
        if (draggingPlanId == null) {
            val currentIds = orderedPlans.map { it.id }
            val incomingIds = datePlans.map { it.id }
            if (currentIds != incomingIds) {
                orderedPlans.clear()
                orderedPlans.addAll(datePlans)
            } else {
                // Refresh content after rename/edit without disturbing the chosen order.
                val latestById = datePlans.associateBy { it.id }
                for (index in orderedPlans.indices) {
                    latestById[orderedPlans[index].id]?.let { orderedPlans[index] = it }
                }
            }
        }
    }

    LaunchedEffect(openedFolderId, collectionRevision, draggingPlanId) {
        if (draggingPlanId == null) {
            folderVisualOrder.clear()
            folderVisualOrder.addAll(openedFolder?.memberIds.orEmpty())
        }
    }

    fun togglePlanSelection(planId: String) {
        selectedPlanIds =
            if (planId in selectedPlanIds) selectedPlanIds.filterNot { it == planId }
            else selectedPlanIds + planId
    }

    fun visiblePlansForFolder(): List<DateAwareTimePlan> {
        val currentFolder = openedFolder
        return if (currentFolder == null) {
            orderedPlans.filter { memberToFolder[it.id] == null }
        } else {
            val byId = orderedPlans.associateBy { it.id }
            val order =
                if (folderVisualOrder.isNotEmpty()) folderVisualOrder
                else currentFolder.memberIds
            order.mapNotNull(byId::get)
        }
    }

    fun finishReorder(commit: Boolean) {
        val currentFolder = openedFolder
        if (commit && reorderDirty) {
            if (currentFolder != null) {
                if (
                    collectionRepository.replaceMemberOrder(
                        currentFolder.id,
                        folderVisualOrder.toList()
                    )
                ) {
                    collectionRevision++
                }
            } else {
                onOrderChanged(orderedPlans.map { it.id })
            }
        } else if (!commit) {
            if (currentFolder != null) {
                folderVisualOrder.clear()
                folderVisualOrder.addAll(currentFolder.memberIds)
            } else {
                orderedPlans.clear()
                orderedPlans.addAll(datePlans)
            }
        }
        draggingPlanId = null
        dragOffsetY = 0f
        reorderDirty = false
    }

    val openImport = {
        context.startActivity(
            android.content.Intent(context, PortableTransferActivity::class.java).apply {
                putExtra(PortableTransferActivity.EXTRA_MODE, PortableTransferActivity.MODE_IMPORT)
            }
        )
    }

    Scaffold(
        topBar = {
            ArmyristTopBar(
                title = "시간계획",
                subtitle = "TIME PLAN · DATE / MULTI-DAY",
                onLeading = onHome,
                leadingIcon = ArmyristTopBarLeadingIcon.HOME
            )
        },
        bottomBar = {
            Surface(
                color = ArmyristColors.AppBackground,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                if (selectionMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { selectedPlanIds = emptyList() },
                            modifier = Modifier.heightIn(min = 52.dp),
                            shape = ArmyristPanelShape,
                            border = BorderStroke(1.dp, ArmyristColors.SoftBorder)
                        ) {
                            Text("선택 ${selectedPlanIds.size}")
                        }
                        Button(
                            onClick = { createFolderDialog = true },
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                            shape = ArmyristPanelShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ArmyristColors.PrimaryControl,
                                contentColor = ArmyristColors.OnDark
                            )
                        ) {
                            Icon(Icons.Outlined.Folder, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("폴더로 묶기", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { moveFolderDialog = true },
                            modifier = Modifier.heightIn(min = 52.dp),
                            shape = ArmyristPanelShape,
                            border = BorderStroke(1.dp, ArmyristColors.SoftBorder)
                        ) {
                            Icon(Icons.Outlined.DriveFileMove, contentDescription = "이동")
                        }
                    }
                } else {
                    Button(
                        onClick = onCreate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .heightIn(min = 54.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ArmyristColors.PrimaryControl,
                            contentColor = ArmyristColors.OnDark
                        )
                    ) {
                        Text(
                            "+  새 시간계획 만들기",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (datePlans.isEmpty() && legacyPlans.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ArmyristPanelShape,
                    colors = CardDefaults.cardColors(containerColor = ArmyristColors.WorkSurface),
                    border = BorderStroke(1.dp, ArmyristColors.Border)
                ) {
                    Column(
                        Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("저장된 시간계획이 없습니다", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "새 시간계획을 만들어 일정을 정리하세요.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(20.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = openImport,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                shape = ArmyristPanelShape
                            ) { Text("데이터 불러오기") }
                            OutlinedButton(
                                onClick = onOpenTrash,
                                modifier = Modifier.heightIn(min = 48.dp),
                                shape = ArmyristPanelShape
                            ) {
                                Text(
                                    if (trashCount > 0) "휴지통 ($trashCount)"
                                    else "휴지통"
                                )
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(14.dp, 10.dp, 14.dp, 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "timeplan-import-trash") {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = openImport,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = ArmyristPanelShape,
                            border = BorderStroke(1.dp, ArmyristColors.Border),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = ArmyristColors.RaisedSurface,
                                contentColor = ArmyristColors.PrimaryText
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Outlined.FileDownload,
                                contentDescription = null,
                                tint = ArmyristColors.PrimaryControl
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("데이터 불러오기", style = MaterialTheme.typography.labelLarge)
                        }

                        OutlinedButton(
                            onClick = onOpenTrash,
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = ArmyristPanelShape,
                            border = BorderStroke(1.dp, ArmyristColors.Border),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = ArmyristColors.RaisedSurface,
                                contentColor = ArmyristColors.PrimaryText
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Outlined.RestoreFromTrash,
                                contentDescription = null,
                                tint = ArmyristColors.PrimaryControl
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (trashCount > 0) "휴지통 $trashCount" else "휴지통",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                if (openedFolder == null) {
                    items(folders, key = { "folder-${it.id}" }) { folder ->
                        val memberCount = folder.memberIds.count { it in validPlanIds }
                        Card(
                            onClick = {
                                if (!selectionMode) {
                                    openedFolderId = folder.id
                                    selectedPlanIds = emptyList()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ArmyristColors.SoftBorder),
                            colors = CardDefaults.cardColors(
                                containerColor = ArmyristColors.RaisedSurface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FolderCoverThumbnail(folder.coverImagePath)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        folder.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = ArmyristColors.PrimaryText
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        "${memberCount}개 시간계획",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ArmyristColors.SecondaryText
                                    )
                                }
                                Icon(
                                    Icons.Outlined.FolderOpen,
                                    contentDescription = null,
                                    tint = ArmyristColors.PrimaryControl
                                )
                                IconButton(onClick = { folderMenuTarget = folder }) {
                                    Icon(
                                        Icons.Outlined.MoreVert,
                                        contentDescription = "폴더 메뉴",
                                        tint = ArmyristColors.SecondaryText
                                    )
                                }
                            }
                        }
                    }
                } else {
                    item(key = "opened-folder-header") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = ArmyristPanelShape,
                            color = ArmyristColors.InfoSurface,
                            border = BorderStroke(1.dp, ArmyristColors.SoftBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        openedFolderId = null
                                        selectedPlanIds = emptyList()
                                    }
                                ) { Text("← 전체") }
                                Text(
                                    openedFolder.name,
                                    modifier = Modifier.weight(1f),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${openedFolder.memberIds.count { it in validPlanIds }}개",
                                    color = ArmyristColors.SecondaryText
                                )
                                IconButton(onClick = { folderMenuTarget = openedFolder }) {
                                    Icon(Icons.Outlined.MoreVert, contentDescription = "폴더 메뉴")
                                }
                            }
                        }
                    }
                }

                itemsIndexed(
                    visiblePlansForFolder(),
                    key = { _, plan -> "v3-${plan.id}" }
                ) { _, plan ->
                    val isDragging = draggingPlanId == plan.id
                    val cardKey = "v3-${plan.id}"
                    val view = LocalView.current
                    val autoScrollThresholdPx = with(density) { 72.dp.toPx() }
                    val maxAutoScrollPx = with(density) { 22.dp.toPx() }

                    val isSelected = plan.id in selectedPlanIds

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .zIndex(if (isDragging) 2f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragging) dragOffsetY else 0f
                                scaleX = if (isDragging) 1.01f else 1f
                                scaleY = if (isDragging) 1.01f else 1f
                                shadowElevation = if (isDragging) 10f else 0f
                            },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            when {
                                isDragging -> ArmyristColors.PrimaryControl
                                isSelected -> ArmyristColors.PrimaryControl
                                else -> ArmyristColors.SoftBorder
                            }
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = if (isDragging) 6.dp else 1.dp
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor =
                                if (isSelected) ArmyristColors.SecondaryControl
                                else ArmyristColors.RaisedSurface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 38.dp, height = 48.dp)
                                    .pointerInput(plan.id, orderedPlans.size, selectionMode) {
                                        if (selectionMode) return@pointerInput
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                val info = listState.layoutInfo.visibleItemsInfo
                                                    .firstOrNull { it.key == cardKey }
                                                if (info != null) {
                                                    draggingPlanId = plan.id
                                                    dragOffsetY = 0f
                                                    reorderDirty = false
                                                    view.performHapticFeedback(
                                                        HapticFeedbackConstants.LONG_PRESS
                                                    )
                                                }
                                            },
                                            onDragCancel = {
                                                finishReorder(commit = false)
                                            },
                                            onDragEnd = {
                                                view.performHapticFeedback(
                                                    HapticFeedbackConstants.CLOCK_TICK
                                                )
                                                finishReorder(commit = true)
                                            },
                                            onDrag = { change, dragAmount ->
                                                if (draggingPlanId != plan.id) return@detectDragGesturesAfterLongPress
                                                change.consume()
                                                dragOffsetY += dragAmount.y

                                                val layout = listState.layoutInfo
                                                val draggedInfo = layout.visibleItemsInfo
                                                    .firstOrNull { it.key == cardKey }
                                                    ?: return@detectDragGesturesAfterLongPress

                                                val draggedCenter =
                                                    draggedInfo.offset +
                                                        draggedInfo.size / 2f +
                                                        dragOffsetY

                                                val candidates = layout.visibleItemsInfo.filter {
                                                    it.key is String &&
                                                        (it.key as String).startsWith("v3-")
                                                }

                                                val target = candidates
                                                    .filter { it.key != cardKey }
                                                    .minByOrNull {
                                                        kotlin.math.abs(
                                                            (it.offset + it.size / 2f) - draggedCenter
                                                        )
                                                    }

                                                if (target != null) {
                                                    val targetId = (target.key as String)
                                                        .removePrefix("v3-")
                                                    val folderMode = openedFolder != null
                                                    val fromIndex =
                                                        if (folderMode) {
                                                            folderVisualOrder.indexOf(plan.id)
                                                        } else {
                                                            orderedPlans.indexOfFirst { it.id == plan.id }
                                                        }
                                                    val targetIndex =
                                                        if (folderMode) {
                                                            folderVisualOrder.indexOf(targetId)
                                                        } else {
                                                            orderedPlans.indexOfFirst { it.id == targetId }
                                                        }

                                                    val crossedTarget =
                                                        if (targetIndex > fromIndex) {
                                                            draggedCenter >
                                                                target.offset + target.size / 2f
                                                        } else {
                                                            draggedCenter <
                                                                target.offset + target.size / 2f
                                                        }

                                                    if (
                                                        fromIndex >= 0 &&
                                                        targetIndex >= 0 &&
                                                        fromIndex != targetIndex &&
                                                        crossedTarget
                                                    ) {
                                                        val oldOffset = draggedInfo.offset
                                                        val targetOffset = target.offset

                                                        if (folderMode) {
                                                            val movedId = folderVisualOrder.removeAt(fromIndex)
                                                            folderVisualOrder.add(targetIndex, movedId)
                                                        } else {
                                                            val moved = orderedPlans.removeAt(fromIndex)
                                                            orderedPlans.add(targetIndex, moved)
                                                        }

                                                        dragOffsetY += (oldOffset - targetOffset)
                                                        reorderDirty = true
                                                        view.performHapticFeedback(
                                                            HapticFeedbackConstants.CLOCK_TICK
                                                        )
                                                    }
                                                }

                                                // Edge auto-scroll: slow on entering the zone,
                                                // progressively faster toward the screen edge.
                                                val viewportStart = layout.viewportStartOffset.toFloat()
                                                val viewportEnd = layout.viewportEndOffset.toFloat()
                                                val topDistance =
                                                    draggedCenter - viewportStart
                                                val bottomDistance =
                                                    viewportEnd - draggedCenter

                                                val scrollDelta = when {
                                                    topDistance < autoScrollThresholdPx -> {
                                                        val intensity =
                                                            ((autoScrollThresholdPx - topDistance) /
                                                                autoScrollThresholdPx)
                                                                .coerceIn(0f, 1f)
                                                        -maxAutoScrollPx * intensity
                                                    }
                                                    bottomDistance < autoScrollThresholdPx -> {
                                                        val intensity =
                                                            ((autoScrollThresholdPx - bottomDistance) /
                                                                autoScrollThresholdPx)
                                                                .coerceIn(0f, 1f)
                                                        maxAutoScrollPx * intensity
                                                    }
                                                    else -> 0f
                                                }

                                                if (scrollDelta != 0f) {
                                                    reorderScope.launch {
                                                        listState.scrollBy(scrollDelta)
                                                    }
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.DragIndicator,
                                    contentDescription = "순서 변경 핸들",
                                    tint = if (isDragging) {
                                        ArmyristColors.PrimaryControl
                                    } else {
                                        ArmyristColors.MutedText
                                    }
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 6.dp)
                                    .pointerInput(plan.id, selectionMode) {
                                        detectTapGestures(
                                            onTap = {
                                                if (selectionMode) togglePlanSelection(plan.id)
                                                else if (draggingPlanId == null) onOpen(plan.id)
                                            },
                                            onLongPress = {
                                                if (draggingPlanId == null) {
                                                    togglePlanSelection(plan.id)
                                                    view.performHapticFeedback(
                                                        HapticFeedbackConstants.LONG_PRESS
                                                    )
                                                }
                                            }
                                        )
                                    },
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    plan.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ArmyristColors.PrimaryText
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    planSpanText(plan),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ArmyristColors.SecondaryText
                                )
                            }

                            IconButton(
                                onClick = { renameTarget = plan },
                                enabled = !isDragging && !selectionMode,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Edit,
                                    contentDescription = "이름 변경",
                                    tint = ArmyristColors.PrimaryControl
                                )
                            }

                            Box(
                                Modifier
                                    .height(22.dp)
                                    .width(1.dp)
                                    .alpha(0.65f)
                                    .background(ArmyristColors.Divider)
                            )

                            IconButton(
                                onClick = { deleteTarget = plan },
                                enabled = !isDragging && !selectionMode,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.DeleteOutline,
                                    contentDescription = "삭제",
                                    tint = ArmyristColors.DangerMuted
                                )
                            }
                        }
                    }
                }

                items(legacyPlans, key = { "legacy-${it.first}" }) { (id, title) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = ArmyristPanelShape,
                        border = BorderStroke(1.dp, ArmyristColors.PrimaryControl),
                        colors = CardDefaults.cardColors(
                            containerColor = ArmyristColors.WorkSurface
                        )
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onOpenLegacy(id) }
                                    .padding(vertical = 2.dp)
                            ) {
                                Text(title, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    "날짜 기능 이전 계획 · 기준 날짜 지정 필요",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ArmyristColors.PrimaryControl
                                )
                            }

                            IconButton(
                                onClick = { legacyDeleteTarget = id to title },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.DeleteOutline,
                                    contentDescription = "삭제",
                                    tint = ArmyristColors.DangerMuted
                                )
                            }
                        }
                    }
                }

            }
        }
    }

    if (createFolderDialog) {
        SimpleTextDialog(
            "새 폴더",
            "",
            true,
            { createFolderDialog = false }
        ) { value ->
            createFolderDialog = false
            if (value.isNotBlank()) {
                val orderedSelection = orderedPlans.map { it.id }.filter { it in selectedPlanIds }
                if (
                    collectionRepository.createFolder(
                        toolType = CollectionToolType.TIME_PLAN,
                        name = value,
                        memberIds = orderedSelection
                    ) != null
                ) {
                    selectedPlanIds = emptyList()
                    openedFolderId = null
                    collectionRevision++
                }
            }
        }
    }

    if (moveFolderDialog) {
        AlertDialog(
            onDismissRequest = { moveFolderDialog = false },
            shape = ArmyristPanelShape,
            containerColor = ArmyristColors.RaisedSurface,
            tonalElevation = 0.dp,
            title = { Text("선택 항목 이동", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (
                                collectionRepository.moveMembers(
                                    CollectionToolType.TIME_PLAN,
                                    selectedPlanIds,
                                    null
                                )
                            ) {
                                selectedPlanIds = emptyList()
                                openedFolderId = null
                                collectionRevision++
                            }
                            moveFolderDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = ArmyristPanelShape
                    ) {
                        Text("폴더 밖으로 이동")
                    }
                    folders.forEach { folder ->
                        OutlinedButton(
                            onClick = {
                                if (
                                    collectionRepository.moveMembers(
                                        CollectionToolType.TIME_PLAN,
                                        selectedPlanIds,
                                        folder.id
                                    )
                                ) {
                                    selectedPlanIds = emptyList()
                                    collectionRevision++
                                }
                                moveFolderDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = ArmyristPanelShape
                        ) {
                            Icon(Icons.Outlined.Folder, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(folder.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { moveFolderDialog = false }) { Text("닫기") }
            }
        )
    }

    folderMenuTarget?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderMenuTarget = null },
            shape = ArmyristPanelShape,
            containerColor = ArmyristColors.RaisedSurface,
            tonalElevation = 0.dp,
            title = { Text(folder.name, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            renameFolderTarget = folder
                            folderMenuTarget = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = ArmyristPanelShape
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("폴더 이름 변경")
                    }
                    OutlinedButton(
                        onClick = {
                            coverFolderId = folder.id
                            folderMenuTarget = null
                            coverPicker.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = ArmyristPanelShape
                    ) {
                        Icon(Icons.Outlined.Image, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("갤러리에서 대표 이미지 선택")
                    }
                    if (folder.coverImagePath != null) {
                        OutlinedButton(
                            onClick = {
                                if (collectionRepository.clearCoverImage(folder.id)) {
                                    collectionRevision++
                                }
                                folderMenuTarget = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = ArmyristPanelShape
                        ) {
                            Text("대표 이미지 제거")
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            deleteFolderTarget = folder
                            folderMenuTarget = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = ArmyristPanelShape
                    ) {
                        Text("폴더 삭제", color = ArmyristColors.DangerMuted)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { folderMenuTarget = null }) { Text("닫기") }
            }
        )
    }

    renameFolderTarget?.let { folder ->
        SimpleTextDialog(
            "폴더 이름 변경",
            folder.name,
            true,
            { renameFolderTarget = null }
        ) { value ->
            renameFolderTarget = null
            if (value.isNotBlank() && collectionRepository.renameFolder(folder.id, value)) {
                collectionRevision++
            }
        }
    }

    deleteFolderTarget?.let { folder ->
        AlertDialog(
            onDismissRequest = { deleteFolderTarget = null },
            shape = ArmyristPanelShape,
            containerColor = ArmyristColors.RaisedSurface,
            tonalElevation = 0.dp,
            title = { Text("폴더 삭제", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "폴더만 삭제합니다. 안의 시간계획은 삭제되지 않고 전체 목록으로 돌아갑니다."
                )
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { deleteFolderTarget = null },
                    shape = ArmyristPanelShape
                ) { Text("취소") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (collectionRepository.deleteFolder(folder.id)) {
                            if (openedFolderId == folder.id) openedFolderId = null
                            collectionRevision++
                        }
                        deleteFolderTarget = null
                    },
                    shape = ArmyristPanelShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ArmyristColors.PrimaryControl,
                        contentColor = ArmyristColors.OnDark
                    )
                ) { Text("폴더 삭제") }
            }
        )
    }

    renameTarget?.let { target ->
        SimpleTextDialog(
            "시간계획 이름 변경",
            target.title,
            true,
            { renameTarget = null }
        ) { value ->
            renameTarget = null
            if (value.isNotBlank()) onRename(target.id, value)
        }
    }

    legacyDeleteTarget?.let { (id, title) ->
        AlertDialog(
            onDismissRequest = { legacyDeleteTarget = null },
            shape = ArmyristPanelShape,
            containerColor = ArmyristColors.RaisedSurface,
            tonalElevation = 0.dp,
            titleContentColor = ArmyristColors.PrimaryText,
            textContentColor = ArmyristColors.PrimaryText,
            title = {
                Text(
                    "휴지통으로 이동",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "'$title'은 날짜 기능 이전 형식입니다. 휴지통으로 이동한 뒤에도 이전 형식 그대로 복구할 수 있습니다.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { legacyDeleteTarget = null },
                    shape = ArmyristPanelShape
                ) {
                    Text("취소")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        legacyDeleteTarget = null
                        onDeleteLegacy(id)
                    },
                    shape = ArmyristPanelShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ArmyristColors.PrimaryControl,
                        contentColor = ArmyristColors.OnDark
                    )
                ) {
                    Text("휴지통으로 이동")
                }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            shape = ArmyristPanelShape,
            containerColor = ArmyristColors.RaisedSurface,
            tonalElevation = 0.dp,
            titleContentColor = ArmyristColors.PrimaryText,
            textContentColor = ArmyristColors.PrimaryText,
            title = { Text("휴지통으로 이동", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "'${target.title}'을 휴지통으로 이동합니다. 자동 삭제 전에는 복구할 수 있습니다.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { deleteTarget = null },
                    shape = ArmyristPanelShape
                ) { Text("취소") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        deleteTarget = null
                        onDelete(target.id)
                    },
                    shape = ArmyristPanelShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ArmyristColors.PrimaryControl,
                        contentColor = ArmyristColors.OnDark
                    )
                ) { Text("휴지통으로 이동") }
            }
        )
    }
}

@Composable
private fun FolderCoverThumbnail(path: String?) {
    val bitmap = remember(path) {
        path?.let { value ->
            runCatching {
                File(value)
                    .takeIf { it.isFile }
                    ?.let { BitmapFactory.decodeFile(it.absolutePath) }
                    ?.asImageBitmap()
            }.getOrNull()
        }
    }

    Surface(
        modifier = Modifier.size(52.dp),
        shape = RoundedCornerShape(10.dp),
        color = ArmyristColors.InfoSurface,
        border = BorderStroke(1.dp, ArmyristColors.SoftBorder)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "폴더 대표 이미지",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = ArmyristColors.PrimaryControl,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun DateAwarePlanDetail(
    plan: DateAwareTimePlan,
    onHome: () -> Unit,
    onBack: () -> Unit,
    onResult: () -> Unit,
    onOpenExecution: (String, Set<String>) -> Unit,
    onDeleteEvent: (String) -> Boolean,
    onCommit: (DateAwareTimePlan) -> Unit
) {
    var editStart by rememberSaveable(plan.id) { mutableStateOf(false) }
    var editEnd by rememberSaveable(plan.id) { mutableStateOf(false) }
    var editEvent by remember { mutableStateOf<DateTimeEvent?>(null) }
    var editLink by remember { mutableStateOf<DateTimeLink?>(null) }
    var editTitle by rememberSaveable(plan.id) { mutableStateOf(false) }
    var editMemo by rememberSaveable(plan.id) { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var voiceDrafts by remember { mutableStateOf<List<TimePlanVoiceDraft>?>(null) }
    var selectionMode by rememberSaveable(plan.id) { mutableStateOf(false) }
    var selectedKeyList by rememberSaveable(plan.id) { mutableStateOf(emptyList<String>()) }
    val selectedKeys = selectedKeyList.toSet()
    var batchDateOpen by rememberSaveable(plan.id) { mutableStateOf(false) }
    var conflictDetail by remember { mutableStateOf<TimePlanConflict?>(null) }
    var pendingNavigation by remember { mutableStateOf<String?>(null) }
    var headerMenuOpen by remember { mutableStateOf(false) }
    var baseDateDialogOpen by rememberSaveable(plan.id) { mutableStateOf(false) }
    var pendingActionShift by remember { mutableStateOf<Pair<DateAwareTimePlan, Map<String, Long>>?>(null) }
    var pendingDeleteEventId by remember { mutableStateOf<String?>(null) }
    var deleteEventInProgress by remember { mutableStateOf(false) }
    val view = LocalView.current
    val conflicts = remember(plan) { TimePlanConstraintEngine.detect(plan) }

    fun requestNavigation(action: String) {
        if (conflicts.isEmpty()) {
            when (action) {
                "BACK" -> onBack()
                "HOME" -> onHome()
                "RESULT" -> onResult()
            }
        } else pendingNavigation = action
    }

    fun toggleSelection(key: String) {
        selectedKeyList =
            (if (key in selectedKeys) selectedKeys - key else selectedKeys + key).toList()
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun commitWithActionTimeImpact(candidate: DateAwareTimePlan) {
        val deltas = DateTimePlanRules.nodeIds(plan).mapNotNull { nodeId ->
            if (plan.actions.none { it.parentPointId == nodeId }) return@mapNotNull null
            val oldTime = DateTimePlanRules.nodeArrival(plan, nodeId) ?: return@mapNotNull null
            val newTime = DateTimePlanRules.nodeArrival(candidate, nodeId) ?: return@mapNotNull null
            val delta = java.time.Duration.between(oldTime, newTime).toMinutes()
            if (delta == 0L) null else nodeId to delta
        }.toMap()
        if (deltas.isEmpty()) onCommit(candidate)
        else pendingActionShift = candidate to deltas
    }

    fun applyLock(locked: Boolean) {
        if (selectedKeys.isEmpty()) return
        var changed = plan
        selectedKeys.forEach { key ->
            when {
                key.startsWith("N:") -> changed = DateTimePlanRules.setNodeLock(changed, key.removePrefix("N:"), locked)
                key.startsWith("L:") -> {
                    val pair = parseLinkSelectionKey(key) ?: return@forEach
                    changed = DateTimePlanRules.setLinkLock(changed, pair.first, pair.second, locked)
                }
            }
        }
        onCommit(changed)
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    BackHandler { requestNavigation("BACK") }

    pendingDeleteEventId?.let { eventId ->
        AlertDialog(
            onDismissRequest = {
                if (!deleteEventInProgress) pendingDeleteEventId = null
            },
            shape = ArmyristPanelShape,
            containerColor = ArmyristColors.RaisedSurface,
            tonalElevation = 0.dp,
            titleContentColor = ArmyristColors.PrimaryText,
            textContentColor = ArmyristColors.PrimaryText,
            title = { Text("중도지점 삭제", fontWeight = FontWeight.Bold) },
            text = { Text("이 지점과 연결된 실시사항도 함께 삭제됩니다.") },
            dismissButton = {
                OutlinedButton(
                    onClick = { pendingDeleteEventId = null },
                    enabled = !deleteEventInProgress,
                    shape = ArmyristPanelShape
                ) { Text("취소") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (deleteEventInProgress) return@Button
                        deleteEventInProgress = true
                        val deleted = onDeleteEvent(eventId)
                        deleteEventInProgress = false
                        if (deleted) {
                            val removedNodeKey = nodeSelectionKey(eventId)
                            selectedKeyList = selectedKeyList.filterNot { key ->
                                key == removedNodeKey ||
                                    key.startsWith("L:$eventId->") ||
                                    key.endsWith("->$eventId")
                            }
                            editLink = null
                            conflictDetail = null
                            pendingActionShift = null
                            pendingDeleteEventId = null
                        } else {
                            pendingDeleteEventId = null
                            message = "지점을 삭제하지 못했습니다. 현재 시간계획은 유지됩니다."
                        }
                    },
                    enabled = !deleteEventInProgress,
                    shape = ArmyristPanelShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ArmyristColors.PrimaryControl,
                        contentColor = ArmyristColors.OnDark
                    )
                ) { Text("삭제") }
            }
        )
    }

    

    Scaffold(
        topBar = {
            ArmyristTopBar(
                title = plan.title,
                subtitle = "TIME PLAN · DATE · AUTO SAVE",
                leadingLabel = "홈",
                leadingIcon = ArmyristTopBarLeadingIcon.HOME,
                onLeading = { requestNavigation("HOME") },
                secondaryLeadingLabel = "메뉴",
                onSecondaryLeading = { requestNavigation("BACK") },
                onTitleClick = { editTitle = true },
                actions = {
                    Box {
                        TextButton(onClick = { headerMenuOpen = true }) {
                            Text("⋮", color = ArmyristColors.OnDark, style = MaterialTheme.typography.titleLarge)
                        }
                        DropdownMenu(expanded = headerMenuOpen, onDismissRequest = { headerMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("결과 전달") },
                                onClick = { headerMenuOpen = false; requestNavigation("RESULT") }
                            )
                            DropdownMenuItem(
                                text = { Text("메모") },
                                onClick = { headerMenuOpen = false; editMemo = true }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!selectionMode) {
                OutlinedButton(
                    onClick = { baseDateDialogOpen = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp).heightIn(min = 44.dp),
                    shape = ArmyristPanelShape,
                    border = BorderStroke(1.dp, ArmyristColors.Border)
                ) {
                    val base = plan.start.value.value?.toLocalDate()
                    Text(if (plan.dateDisplayMode == TimePlanDateDisplayMode.RELATIVE_D_DAY) "기준일 변경 · 날짜 미설정 (D-Day)" else "기준일 변경 · ${base?.format(DateTimeFormatter.ISO_DATE) ?: "미설정"}")
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedButton(
                        onClick = { selectionMode = true; selectedKeyList = emptyList() },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        shape = ArmyristPanelShape,
                        border = BorderStroke(1.dp, ArmyristColors.Border),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = ArmyristColors.WorkSurface,
                            contentColor = ArmyristColors.PrimaryText
                        )
                    ) { Text("편집/선택", style = MaterialTheme.typography.labelLarge) }
                    Button(
                        onClick = { onOpenExecution(TimePlanExecutionActivity.MODE_EXECUTE, emptySet()) },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        shape = ArmyristPanelShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ArmyristColors.PrimaryControl,
                            contentColor = ArmyristColors.OnDark
                        )
                    ) { Text("실시사항 설정", style = MaterialTheme.typography.labelLarge) }
                    Box(Modifier.weight(1f)) {
                        OfflineVoiceButton(
                            toolContext = VoiceToolContext.TIME_PLAN,
                            modifier = Modifier.fillMaxWidth(),
                            onTranscript = {
                                voiceDrafts = KoreanVoiceStructurer.timePlan(
                                    it,
                                    plan.start.value.value?.toLocalDate() ?: LocalDate.now()
                                )
                            },
                            onMessage = { message = it }
                        )
                    }
                }
            } else {
                val allNodeSelectionKeys =
                    DateTimePlanRules.nodeIds(plan).map(::nodeSelectionKey).toSet()
                SelectionActionPanel(
                    selectedKeys = selectedKeys,
                    allNodeSelectionKeys = allNodeSelectionKeys,
                    canBatchDate = selectedKeys.isNotEmpty() && selectedKeys.all { it.startsWith("N:") },
                    onBatchDate = {
                        val nodeIds = selectedKeys.map { it.removePrefix("N:") }.toSet()
                        if (nodeIds.any { DateTimePlanRules.nodeDateTimeLocked(plan, it) }) {
                            message = "선택한 일정에 고정된 시간이 포함되어 있습니다. 날짜를 변경하려면 먼저 고정을 해제해주세요."
                        } else batchDateOpen = true
                    },
                    onLock = { applyLock(true) },
                    onUnlock = { applyLock(false) },
                    onActionEdit = {
                        val nodeIds = selectedKeys.filter { it.startsWith("N:") }.map { it.removePrefix("N:") }.toSet()
                        if (nodeIds.isEmpty()) message = "실시사항을 편집할 지점을 선택해주세요."
                        else onOpenExecution(TimePlanExecutionActivity.MODE_PREPARE, nodeIds)
                    },
                    onSelectAllNodes = {
                        selectedKeyList = allNodeSelectionKeys.toList()
                    },
                    onClearNodes = {
                        selectedKeyList = selectedKeys.filterNot { it.startsWith("N:") }
                    },
                    onDone = { selectionMode = false; selectedKeyList = emptyList() }
                )
            }

            if (baseDateDialogOpen) {
                TimePlanBaseDateDialog(
                    plan = plan,
                    onDismiss = { baseDateDialogOpen = false },
                    onApply = { targetDate, relative ->
                        val oldBase = plan.start.value.value?.toLocalDate()
                        val changed = if (relative || targetDate == null || oldBase == null) {
                            plan.copy(dateDisplayMode = if (relative) TimePlanDateDisplayMode.RELATIVE_D_DAY else TimePlanDateDisplayMode.ABSOLUTE)
                        } else {
                            shiftWholePlanDates(plan, java.time.temporal.ChronoUnit.DAYS.between(oldBase, targetDate))
                                .copy(dateDisplayMode = TimePlanDateDisplayMode.ABSOLUTE)
                        }
                        onCommit(changed)
                        baseDateDialogOpen = false
                    }
                )
            }

            TimelineList(
                plan = plan,
                conflicts = conflicts,
                selectionMode = selectionMode,
                selectedKeys = selectedKeys,
                onToggleNode = { toggleSelection(nodeSelectionKey(it)) },
                onToggleLink = { from, to -> toggleSelection(linkSelectionKey(from, to)) },
                onWarning = { conflictDetail = it },
                onStart = {
                    if (plan.start.dateTimeLocked) message = "고정된 시작시간입니다. 편집 모드에서 고정을 해제한 뒤 수정해주세요."
                    else editStart = true
                },
                onEnd = {
                    if (plan.end.dateTimeLocked) message = "고정된 종료시간입니다. 편집 모드에서 고정을 해제한 뒤 수정해주세요."
                    else editEnd = true
                },
                onEvent = {
                    if (it.dateTimeLocked) message = "고정된 일정입니다. 편집 모드에서 고정을 해제한 뒤 수정해주세요."
                    else editEvent = it
                },
                onLink = {
                    if (it.durationLocked) message = "고정된 경과시간입니다. 편집 모드에서 고정을 해제한 뒤 수정해주세요."
                    else editLink = it
                },
                onMemo = { editMemo = true },
                onAddMid = {
                    val mids=plan.midwayEvents.sortedBy{it.order}
                    val base = (plan.finalPoint?.let{DateTimePlanRules.arrival(it.timeSpec)} ?: plan.end.value.value ?: plan.start.value.value ?: LocalDateTime.now())
                    val e=DateTimeEvent(UUID.randomUUID().toString(), TimeEventKind.MIDWAY, mids.size, "중도 ${mids.size+1}", EventDateTimeSpec.Single(DateTimeValue.explicit(base)))
                    onCommit(DateTimePlanRules.normalizeTopology(plan.copy(midwayEvents=mids+e)))
                },
                onAddFinal = { onCommit(DateTimePlanRules.appendFinal(plan,UUID.randomUUID().toString())) }
            )
        }
    }

    if(editStart) DateTimeEditorDialog("시작 날짜 / 시간", plan.start.value.value, {editStart=false}) { dt ->
        val edited = plan.copy(start=plan.start.copy(value=DateTimeValue.explicit(dt)))
        val candidate = DateTimePlanRules.recalculateForExplicitNodes(edited, setOf(DateTimePlanRules.START_ID))
        commitWithActionTimeImpact(candidate)
        editStart=false
    }
    if(editEnd) DateTimeEditorDialog("종료 날짜 / 시간", plan.end.value.value, {editEnd=false}) { dt ->
        val edited = plan.copy(end=plan.end.copy(value=DateTimeValue.explicit(dt)))
        val candidate = DateTimePlanRules.recalculateForExplicitNodes(edited, setOf(DateTimePlanRules.END_ID))
        commitWithActionTimeImpact(candidate)
        editEnd=false
    }
    editEvent?.let { event ->
        DateTimeEventEditDialog(
            event,
            onDismiss = { editEvent = null },
            onDelete = {
                // Close the editor first. Actual topology mutation is deferred to
                // LaunchedEffect after this dialog leaves composition.
                if (!deleteEventInProgress && pendingDeleteEventId == null) {
                    pendingDeleteEventId = event.id
                    editEvent = null
                }
            }
        ) { changedEvent ->
            // A user-edited event remains explicit. Unlocked later nodes, including END,
            // follow the event's departure delta. A locked END remains fixed and the
            // resulting conflict is intentionally surfaced instead of being moved.
            val candidate = DateTimePlanRules.reflowEventEdit(plan, changedEvent)
                ?: run {
                    val changed = if (changedEvent.kind == TimeEventKind.FINAL) {
                        plan.copy(finalPoint = changedEvent)
                    } else {
                        plan.copy(midwayEvents = plan.midwayEvents.map {
                            if (it.id == changedEvent.id) changedEvent else it
                        })
                    }
                    DateTimePlanRules.recalculateForExplicitNodes(changed, setOf(changedEvent.id))
                }
            commitWithActionTimeImpact(candidate)
            editEvent=null
        }
    }
    editLink?.let { link ->
        DateDurationDialog(link.durationMinutes, link.label.orEmpty(), {editLink=null}) { minutes,label ->
            commitWithActionTimeImpact(DateTimePlanRules.setLinkDuration(plan,link.fromNodeId,link.toNodeId,minutes,label))
            editLink=null
        }
    }
    if(editTitle) SimpleTextDialog("제목 수정",plan.title,true,{editTitle=false}) { value -> if(value.isNotBlank()) onCommit(plan.copy(title=value.trim()));editTitle=false }
    if(editMemo) SimpleTextDialog("메모",plan.memo.orEmpty(),false,{editMemo=false}) { value ->onCommit(plan.copy(memo=value.trim().ifBlank{null}));editMemo=false }

    if (batchDateOpen) {
        val selectedNodes = selectedKeys.filter { it.startsWith("N:") }.map { it.removePrefix("N:") }.toSet()
        val initialDate = selectedNodes.firstNotNullOfOrNull { DateTimePlanRules.nodeArrival(plan, it)?.toLocalDate() }
            ?: plan.start.value.value?.toLocalDate() ?: LocalDate.now()
        BatchDateDialog(
            initialDate = initialDate,
            count = selectedNodes.size,
            onDismiss = { batchDateOpen = false },
            onConfirm = { date ->
                val candidate = DateTimePlanRules.batchChangeDate(plan, selectedNodes, date)
                if (candidate == null) {
                    message = "고정된 일정이 포함되어 날짜를 변경할 수 없습니다."
                } else {
                    commitWithActionTimeImpact(candidate)
                    selectedKeyList = emptyList()
                }
                batchDateOpen = false
            }
        )
    }

    pendingActionShift?.let { pending ->
        val (candidate, deltas) = pending
        AlertDialog(
            onDismissRequest = { pendingActionShift = null },
            title = { Text("실시사항 시간 영향") },
            text = {
                val summary = deltas.entries.joinToString("\n") { (nodeId, delta) ->
                    "${TimePlanExecutionRules.pointName(plan, nodeId)}: ${if (delta >= 0) "+" else ""}${delta}분"
                }
                Text(
                    "시간 변경의 영향을 받는 실시사항이 있습니다.\n\n$summary\n\n‘Action 시간 유지’를 선택하면 실시사항의 기존 절대 시각을 유지합니다."
                )
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { pendingActionShift = null }) {
                        Text("취소")
                    }
                    TextButton(onClick = {
                        onCommit(candidate)
                        pendingActionShift = null
                    }) { Text("실시사항 시간 유지") }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        var shifted = candidate
                        deltas.forEach { (nodeId, delta) ->
                            shifted = TimePlanExecutionRules.shiftActionsForParent(shifted, nodeId, delta)
                        }
                        onCommit(shifted)
                        pendingActionShift = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor=ArmyristColors.PrimaryControl)
                ) { Text("함께 이동") }
            },
            containerColor = ArmyristColors.RaisedSurface,
            tonalElevation = 0.dp
        )
    }

    conflictDetail?.let { conflict ->
        AlertDialog(
            onDismissRequest = { conflictDetail = null },
            title = { Text(conflictTitle(conflict.type)) },
            text = { Text(TimePlanConstraintEngine.messageFor(conflict)) },
            confirmButton = { TextButton(onClick={conflictDetail=null}) { Text("확인") } }
        )
    }

    pendingNavigation?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingNavigation = null },
            shape = ArmyristPanelShape,
            containerColor = ArmyristColors.RaisedSurface,
            tonalElevation = 0.dp,
            titleContentColor = ArmyristColors.PrimaryText,
            textContentColor = ArmyristColors.PrimaryText,
            title = { Text("시간계획 확인", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "확인할 문제 ${conflicts.size}개",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ArmyristColors.PrimaryControl
                    )
                    Text(
                        if (action == "RESULT")
                            "현재 상태로도 결과를 전달할 수 있습니다."
                        else
                            "현재 계획은 그대로 자동 저장됩니다.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { pendingNavigation = null },
                    shape = ArmyristPanelShape
                ) { Text("계획 확인") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingNavigation = null
                        when(action) {
                            "BACK" -> onBack()
                            "HOME" -> onHome()
                            "RESULT" -> onResult()
                        }
                    },
                    shape = ArmyristPanelShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ArmyristColors.PrimaryControl,
                        contentColor = ArmyristColors.OnDark
                    )
                ) { Text("계속") }
            }
        )
    }

    message?.let {
        AlertDialog(
            onDismissRequest = { message = null },
            shape = ArmyristPanelShape,
            containerColor = ArmyristColors.RaisedSurface,
            tonalElevation = 0.dp,
            titleContentColor = ArmyristColors.PrimaryText,
            textContentColor = ArmyristColors.PrimaryText,
            title = { Text("확인", fontWeight = FontWeight.Bold) },
            text = { Text(it) },
            confirmButton = {
                Button(
                    onClick = { message = null },
                    shape = ArmyristPanelShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ArmyristColors.PrimaryControl,
                        contentColor = ArmyristColors.OnDark
                    )
                ) { Text("확인") }
            }
        )
    }
    voiceDrafts?.let { drafts ->
        TimePlanVoiceReviewDialog(drafts, onDismiss={voiceDrafts=null}) { applied ->
            val ready=applied.filter{it.state!=VoiceDraftState.INVALID && it.dateTime!=null}
            if(ready.isEmpty()) { message="적용 가능한 일정이 없습니다."; return@TimePlanVoiceReviewDialog }
            val existing=plan.midwayEvents.sortedBy{it.order}.toMutableList()
            ready.forEach { d ->
                val spec = d.rangeEnd?.let { rangeEnd ->
                    EventDateTimeSpec.Range(DateTimeValue.explicit(d.dateTime!!), DateTimeValue.explicit(rangeEnd))
                } ?: EventDateTimeSpec.Single(DateTimeValue.explicit(d.dateTime!!))
                existing += DateTimeEvent(
                    UUID.randomUUID().toString(), TimeEventKind.MIDWAY, existing.size,
                    d.name.ifBlank { "일정" }, spec, d.note.ifBlank { null }
                )
            }
            val sorted=existing.sortedBy { DateTimePlanRules.arrival(it.timeSpec) }.mapIndexed{i,e->e.copy(order=i)}
            val changed=DateTimePlanRules.normalizeTopology(plan.copy(midwayEvents=sorted))
            if(DateTimePlanRules.validateForPersistence(changed).isEmpty()) {
                onCommit(changed); voiceDrafts=null
            } else message="음성 Draft를 현재 계획 구조에 적용할 수 없습니다."
        }
    }
}

private fun nodeSelectionKey(nodeId: String) = "N:$nodeId"
private fun linkSelectionKey(from: String, to: String) = "L:$from->$to"
private fun parseLinkSelectionKey(key: String): Pair<String,String>? {
    if (!key.startsWith("L:")) return null
    val parts = key.removePrefix("L:").split("->", limit=2)
    return if (parts.size == 2) parts[0] to parts[1] else null
}
private fun conflictTitle(type: TimePlanConflictType): String = when(type) {
    TimePlanConflictType.TIME_OVERLAP -> "시간 중복"
    TimePlanConflictType.LOCKED_RELATION_MISMATCH -> "시간 관계 불일치"
    TimePlanConflictType.INVALID_TEMPORAL_RELATION -> "시간 관계 확인"
}

@Composable
private fun SelectionActionPanel(
    selectedKeys: Set<String>,
    allNodeSelectionKeys: Set<String>,
    canBatchDate: Boolean,
    onBatchDate: () -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onActionEdit: () -> Unit,
    onSelectAllNodes: () -> Unit,
    onClearNodes: () -> Unit,
    onDone: () -> Unit
) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        shape = ArmyristPanelShape,
        border = BorderStroke(1.dp, ArmyristColors.PrimaryControl),
        color = ArmyristColors.RaisedSurface
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val selectedNodeKeys = selectedKeys.filter { it.startsWith("N:") }.toSet()
            val allNodesSelected =
                allNodeSelectionKeys.isNotEmpty() &&
                    selectedNodeKeys.containsAll(allNodeSelectionKeys)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("✓ ${selectedKeys.size}개 선택", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(
                    onClick = if (allNodesSelected) onClearNodes else onSelectAllNodes
                ) {
                    Text(if (allNodesSelected) "지점 전체 해제" else "전체 지점 선택")
                }
                TextButton(onClick = onDone) { Text("완료") }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedButton(
                    onClick = onActionEdit,
                    enabled = selectedKeys.any { it.startsWith("N:") },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) { Text("실시사항 편집") }
                if (canBatchDate) {
                    OutlinedButton(
                        onClick = onBatchDate,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) { Text("날짜 변경") }
                }
                OutlinedButton(
                    onClick = onLock,
                    enabled = selectedKeys.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                ) { Text("고정") }
                OutlinedButton(
                    onClick = onUnlock,
                    enabled = selectedKeys.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                ) { Text("고정 해제") }
            }
        }
    }
}

private fun shiftWholePlanDates(plan: DateAwareTimePlan, days: Long): DateAwareTimePlan {
    if (days == 0L) return plan
    fun shift(v: DateTimeValue) = v.value?.let { DateTimeValue(it.plusDays(days), v.origin) } ?: v
    fun shiftSpec(spec: EventDateTimeSpec): EventDateTimeSpec = when (spec) {
        EventDateTimeSpec.Unspecified -> spec
        is EventDateTimeSpec.Single -> spec.copy(value = shift(spec.value))
        is EventDateTimeSpec.Range -> spec.copy(start = shift(spec.start), end = shift(spec.end))
    }
    return plan.copy(
        start = plan.start.copy(value = shift(plan.start.value)),
        midwayEvents = plan.midwayEvents.map { it.copy(timeSpec = shiftSpec(it.timeSpec)) },
        finalPoint = plan.finalPoint?.let { it.copy(timeSpec = shiftSpec(it.timeSpec)) },
        end = plan.end.copy(value = shift(plan.end.value)),
        actions = plan.actions.map { it.copy(scheduledDateTime = it.scheduledDateTime.plusDays(days)) }
    )
}

private enum class BaseDateChoice {
    TODAY,
    SPECIFIC,
    RELATIVE
}

@Composable
private fun TimePlanBaseDateDialog(
    plan: DateAwareTimePlan,
    onDismiss: () -> Unit,
    onApply: (LocalDate?, Boolean) -> Unit
) {
    var calendar by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(plan.start.value.value?.toLocalDate() ?: LocalDate.now()) }
    var choice by remember {
        mutableStateOf(
            if (plan.dateDisplayMode == TimePlanDateDisplayMode.RELATIVE_D_DAY) {
                BaseDateChoice.RELATIVE
            } else {
                BaseDateChoice.SPECIFIC
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = ArmyristPanelShape,
        containerColor = ArmyristColors.RaisedSurface,
        tonalElevation = 0.dp,
        title = { Text("기준일 변경", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "계획의 기준일을 선택합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ArmyristColors.SecondaryText
                )
                BaseDateChoiceRow(
                    selected = choice == BaseDateChoice.TODAY,
                    title = "오늘 날짜로",
                    subtitle = LocalDate.now().format(DateTimeFormatter.ISO_DATE),
                    onClick = { choice = BaseDateChoice.TODAY }
                )
                BaseDateChoiceRow(
                    selected = choice == BaseDateChoice.SPECIFIC,
                    title = "특정 날짜로",
                    subtitle = date.format(DateTimeFormatter.ISO_DATE),
                    onClick = { choice = BaseDateChoice.SPECIFIC; calendar = true }
                )
                BaseDateChoiceRow(
                    selected = choice == BaseDateChoice.RELATIVE,
                    title = "날짜 미설정",
                    subtitle = "D-Day / D+1 / D+2",
                    onClick = { choice = BaseDateChoice.RELATIVE }
                )
                Text(
                    "기준일을 변경하면 계획의 모든 지점과 실시사항을 같은 일수만큼 이동합니다. 날짜 미설정은 내부 시간 관계를 보존하고 D-Day 기준으로 표시합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ArmyristColors.SecondaryText
                )
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = ArmyristPanelShape) { Text("취소") }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (choice) {
                        BaseDateChoice.TODAY -> onApply(LocalDate.now(), false)
                        BaseDateChoice.SPECIFIC -> onApply(date, false)
                        BaseDateChoice.RELATIVE -> onApply(null, true)
                    }
                },
                shape = ArmyristPanelShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ArmyristColors.PrimaryControl,
                    contentColor = ArmyristColors.OnDark
                )
            ) { Text("적용") }
        }
    )

    if (calendar) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { calendar = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let {
                            date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                            choice = BaseDateChoice.SPECIFIC
                        }
                        calendar = false
                    }
                ) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { calendar = false }) { Text("취소") } }
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun BaseDateChoiceRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = ArmyristPanelShape,
        color = if (selected) ArmyristColors.SecondaryControl else ArmyristColors.WorkSurface,
        border = BorderStroke(
            1.dp,
            if (selected) ArmyristColors.PrimaryControl else ArmyristColors.Border
        )
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = ArmyristColors.SecondaryText
                )
            }
        }
    }
}

@Composable
private fun BatchDateDialog(
    initialDate: LocalDate,
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    var date by remember(initialDate) { mutableStateOf(initialDate) }
    var calendar by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest=onDismiss,
        title={Text("날짜 변경")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("선택한 일정 ${count}개")
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
                    OutlinedButton(onClick={date=date.minusDays(1)}) { Text("-1일") }
                    Text(date.format(DateTimeFormatter.ofPattern("MM.dd (EEE)", Locale.KOREAN)), fontWeight=FontWeight.Bold)
                    OutlinedButton(onClick={date=date.plusDays(1)}) { Text("+1일") }
                }
                TextButton(onClick={calendar=true}, modifier=Modifier.fillMaxWidth()) { Text("달력") }
                Text("기존 시각은 유지됩니다.", style=MaterialTheme.typography.bodySmall, color=ArmyristColors.SecondaryText)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ArmyristPanelShape,
                    color = ArmyristColors.WorkSurface,
                    border = BorderStroke(1.dp, ArmyristColors.Border)
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text("변경 후", style = MaterialTheme.typography.labelSmall, color = ArmyristColors.SecondaryText)
                        Text("${date.format(DateTimeFormatter.ofPattern("MM.dd"))} / 일정 ${count}개", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        dismissButton={TextButton(onClick=onDismiss){Text("취소")}},
        confirmButton={Button(onClick={onConfirm(date)},colors=ButtonDefaults.buttonColors(containerColor=ArmyristColors.PrimaryControl)){Text("적용")}}
    )
    if(calendar) {
        val state=rememberDatePickerState(initialSelectedDateMillis=date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest={calendar=false},
            confirmButton={TextButton(onClick={state.selectedDateMillis?.let{date=Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()};calendar=false}){Text("확인")}},
            dismissButton={TextButton(onClick={calendar=false}){Text("취소")}}
        ) { DatePicker(state=state) }
    }
}

@Composable
private fun TimelineList(
    plan:DateAwareTimePlan,
    conflicts: List<TimePlanConflict>,
    selectionMode: Boolean,
    selectedKeys: Set<String>,
    onToggleNode:(String)->Unit,
    onToggleLink:(String,String)->Unit,
    onWarning:(TimePlanConflict)->Unit,
    onStart:()->Unit,onEnd:()->Unit,onEvent:(DateTimeEvent)->Unit,onLink:(DateTimeLink)->Unit,
    onMemo:()->Unit,onAddMid:()->Unit,onAddFinal:()->Unit
){
    val nodes=DateTimePlanRules.nodeIds(plan)
    val events=plan.orderedEvents().associateBy{it.id}
    var lastDate: LocalDate? = null
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(8.dp,4.dp,8.dp,24.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){
        nodes.forEachIndexed { index,id ->
            val arrival=DateTimePlanRules.nodeArrival(plan,id)
            val date=arrival?.toLocalDate()
            if(date!=null && date!=lastDate){ item(key="day-$date") { DayHeader(date, plan.start.value.value?.toLocalDate(), plan.dateDisplayMode) }; lastDate=date }
            item(key="node-$id") {
                val nodeConflict = conflicts.firstOrNull { id in it.affectedNodeIds }
                when(id){
                    DateTimePlanRules.START_ID->DatePointCard(
                        label="시작", dt=plan.start.value.value, emphasized=true,
                        locked=plan.start.dateTimeLocked,
                        selectionMode=selectionMode, selected=nodeSelectionKey(id) in selectedKeys,
                        conflict=nodeConflict, onToggle={onToggleNode(id)}, onWarning=onWarning,
                        onClick=onStart
                    )
                    DateTimePlanRules.END_ID->DatePointCard(
                        label="종료", dt=plan.end.value.value, emphasized=true,
                        locked=plan.end.dateTimeLocked,
                        selectionMode=selectionMode, selected=nodeSelectionKey(id) in selectedKeys,
                        conflict=nodeConflict, onToggle={onToggleNode(id)}, onWarning=onWarning,
                        onClick=onEnd
                    )
                    else->events[id]?.let{event ->
                        EventCard(
                            e=event,
                            selectionMode=selectionMode,
                            selected=nodeSelectionKey(id) in selectedKeys,
                            conflict=nodeConflict,
                            onToggle={onToggleNode(id)},
                            onWarning=onWarning,
                            onClick={onEvent(event)}
                        )
                    }
                }
            }
            if(index<nodes.lastIndex){
                val to=nodes[index+1]
                val link=plan.links.firstOrNull{it.fromNodeId==id&&it.toNodeId==to}
                val linkConflict = conflicts.firstOrNull { it.affectedLink == (id to to) }
                item(key="link-$id-$to") {
                    val displayMinutes =
                        if (link?.durationLocked == true) {
                            link.durationMinutes
                        } else {
                            val fromTime = DateTimePlanRules.nodeDeparture(plan, id)
                            val toTime = DateTimePlanRules.nodeArrival(plan, to)
                            if (fromTime != null && toTime != null && !toTime.isBefore(fromTime)) {
                                java.time.Duration.between(fromTime, toTime).toMinutes()
                            } else {
                                null
                            }
                        }
                    LinkRow(
                        link=link,
                        displayMinutes=displayMinutes,
                        selectionMode=selectionMode,
                        selected=linkSelectionKey(id,to) in selectedKeys,
                        conflict=linkConflict,
                        onToggle={onToggleLink(id,to)},
                        onWarning=onWarning,
                        onClick={ if(link!=null) onLink(link) }
                    )
                }
            }
        }
        if(!selectionMode) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onAddMid,
                        modifier = Modifier.weight(1f).heightIn(min = 42.dp),
                        shape = ArmyristPanelShape,
                        border = BorderStroke(1.dp, ArmyristColors.Border),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) { Text("+ 중도지점") }
                    Button(
                        onClick = onAddFinal,
                        modifier = Modifier.weight(1f).heightIn(min = 42.dp),
                        shape = ArmyristPanelShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ArmyristColors.PrimaryControl,
                            contentColor = ArmyristColors.OnDark
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) { Text("+ 종료지점") }
                }
            }
        }
        item { SummaryCard(plan) }
        item {
            Card(
                Modifier.fillMaxWidth().clickable(enabled = !selectionMode) { onMemo() },
                shape = ArmyristPanelShape,
                border = BorderStroke(1.dp, ArmyristColors.Border),
                colors = CardDefaults.cardColors(containerColor = ArmyristColors.RaisedSurface)
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text("메모", fontWeight = FontWeight.Bold, color = ArmyristColors.PrimaryText)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        plan.memo?.takeIf { it.isNotBlank() }
                            ?: "메모가 없습니다. 눌러서 입력하세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ArmyristColors.SecondaryText
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeader(
    date: LocalDate,
    baseDate: LocalDate?,
    mode: TimePlanDateDisplayMode
) {
    val dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
    val label = if (
        mode == TimePlanDateDisplayMode.RELATIVE_D_DAY && baseDate != null
    ) {
        val offset = java.time.temporal.ChronoUnit.DAYS.between(baseDate, date)
        if (offset == 0L) "D-Day" else "D${if (offset > 0) "+" else ""}$offset"
    } else {
        "${date.format(DateTimeFormatter.ofPattern("MM.dd"))}  $dayName"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        HorizontalDivider(
            modifier = Modifier.width(28.dp),
            color = ArmyristColors.PrimaryControl.copy(alpha = 0.40f)
        )
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = ArmyristColors.PrimaryControl
        )
        HorizontalDivider(
            modifier = Modifier.width(28.dp),
            color = ArmyristColors.PrimaryControl.copy(alpha = 0.40f)
        )
    }
}

@Composable
private fun DatePointCard(
    label: String,
    dt: LocalDateTime?,
    emphasized: Boolean,
    locked: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    conflict: TimePlanConflict?,
    onToggle: () -> Unit,
    onWarning: (TimePlanConflict) -> Unit,
    onClick: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().clickable { if(selectionMode) onToggle() else onClick() },
        shape = ArmyristPanelShape,
        border = BorderStroke(if (emphasized) 2.dp else 1.dp, if(conflict!=null) MaterialTheme.colorScheme.error else ArmyristColors.PrimaryControl),
        colors = CardDefaults.cardColors(containerColor = ArmyristColors.RaisedSurface)
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp),verticalAlignment = Alignment.CenterVertically) {
            if(selectionMode) Checkbox(checked=selected,onCheckedChange={onToggle()})
            Column(Modifier.width(112.dp)) {
                Text(label, style=MaterialTheme.typography.labelSmall, color=ArmyristColors.SecondaryText)
                Text(dt?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--:--",fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            if(locked) LockBadge()
            conflict?.let { WarningButton { onWarning(it) } }
        }
    }
}

@Composable
private fun EventCard(
    e: DateTimeEvent,
    selectionMode: Boolean,
    selected: Boolean,
    conflict: TimePlanConflict?,
    onToggle: () -> Unit,
    onWarning: (TimePlanConflict) -> Unit,
    onClick: () -> Unit
) {
    val time = when (val spec = e.timeSpec) {
        EventDateTimeSpec.Unspecified -> "--:--"
        is EventDateTimeSpec.Single -> spec.value.value?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--:--"
        is EventDateTimeSpec.Range -> rangeDisplay(spec.start.value, spec.end.value)
    }
    Card(
        Modifier.fillMaxWidth().clickable { if(selectionMode) onToggle() else onClick() },
        shape = ArmyristPanelShape,
        border = BorderStroke(1.dp, when {
            conflict != null -> MaterialTheme.colorScheme.error
            e.kind == TimeEventKind.FINAL -> ArmyristColors.PrimaryControl
            else -> ArmyristColors.Border
        }),
        colors = CardDefaults.cardColors(containerColor = ArmyristColors.WorkSurface)
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp),verticalAlignment = Alignment.CenterVertically) {
            if(selectionMode) Checkbox(checked=selected,onCheckedChange={onToggle()})
            Column(Modifier.width(112.dp)) {
                Text(if (e.kind == TimeEventKind.FINAL) "종료지점" else "중도지점 ${e.order + 1}",style = MaterialTheme.typography.labelSmall,color = ArmyristColors.SecondaryText)
                Text(time, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text(e.name, fontWeight = FontWeight.Bold, maxLines = 1)
                e.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ArmyristColors.SecondaryText, maxLines = 1) }
            }
            if(e.dateTimeLocked) LockBadge()
            conflict?.let { WarningButton { onWarning(it) } }
        }
    }
}

@Composable private fun LockBadge() {
    Surface(shape=ArmyristPanelShape,color=ArmyristColors.WorkSurface,border=BorderStroke(1.dp,ArmyristColors.PrimaryControl),modifier=Modifier.padding(horizontal=3.dp)) {
        Text("고정", modifier=Modifier.padding(horizontal=5.dp,vertical=2.dp), style=MaterialTheme.typography.labelSmall, color=ArmyristColors.PrimaryControl, fontWeight=FontWeight.Bold)
    }
}

@Composable private fun WarningButton(onClick:()->Unit) {
    TextButton(onClick=onClick, contentPadding=PaddingValues(horizontal=6.dp,vertical=0.dp), modifier=Modifier.defaultMinSize(minWidth=36.dp,minHeight=36.dp)) {
        Text("!", color=MaterialTheme.colorScheme.error, fontWeight=FontWeight.ExtraBold, fontSize=18.sp)
    }
}


@Composable
private fun LinkRow(
    link: DateTimeLink?,
    displayMinutes: Long?,
    selectionMode: Boolean,
    selected: Boolean,
    conflict: TimePlanConflict?,
    onToggle: () -> Unit,
    onWarning: (TimePlanConflict) -> Unit,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 38.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) Checkbox(checked = selected, onCheckedChange = { onToggle() })

        Row(
            Modifier
                .weight(1f)
                .clickable(enabled = link != null || selectionMode) {
                    if (selectionMode) onToggle() else onClick()
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.weight(1f))
            HorizontalDivider(Modifier.width(12.dp), color = ArmyristColors.Border.copy(alpha = 0.65f))
            Surface(
                modifier = Modifier.padding(horizontal = 6.dp),
                shape = ArmyristPanelShape,
                color = if (selected && selectionMode) ArmyristColors.WorkSurface else Color.Transparent
            ) {
                Row(
                    Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${link?.label?.takeIf { it.isNotBlank() } ?: "경과"} · ${durationText(displayMinutes)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = ArmyristColors.SecondaryText
                    )
                    if (!selectionMode) Text(" ▼", color = ArmyristColors.SecondaryText)
                    if (link?.durationLocked == true) {
                        Spacer(Modifier.width(4.dp))
                        LockBadge()
                    }
                    conflict?.let {
                        Spacer(Modifier.width(2.dp))
                        WarningButton { onWarning(it) }
                    }
                }
            }
            HorizontalDivider(Modifier.width(12.dp), color = ArmyristColors.Border.copy(alpha = 0.65f))
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable private fun SummaryCard(plan:DateAwareTimePlan){
    val s=plan.start.value.value;val e=plan.end.value.value;val total=if(s!=null&&e!=null&&!e.isBefore(s)) Duration.between(s,e).toMinutes() else null
    Card(Modifier.fillMaxWidth().padding(top=6.dp),shape=ArmyristPanelShape,colors=CardDefaults.cardColors(containerColor=ArmyristColors.RaisedSurface)){Row(Modifier.padding(12.dp)){SummaryCell("전체시간",durationText(total),Modifier.weight(1f));SummaryCell("중도지점","${plan.midwayEvents.size}개",Modifier.weight(1f));SummaryCell("종료",e?.format(DateTimeFormatter.ofPattern("MM.dd HH:mm"))?:"--",Modifier.weight(1f))}}
}
@Composable private fun SummaryCell(label:String,value:String,m:Modifier){Column(m){Text(label,style=MaterialTheme.typography.labelSmall,color=ArmyristColors.SecondaryText);Text(value,fontWeight=FontWeight.Bold)}}
private fun durationText(m:Long?):String=when{m==null->"미설정";m==0L->"0분";m<60->"${m}분";m%60==0L->"${m/60}시간";else->"${m/60}시간 ${m%60}분"}
private fun planSpanText(p: DateAwareTimePlan): String {
    val s = p.start.value.value
    val e = p.end.value.value
    if (s == null || e == null) return "날짜/시간 일부 미설정"

    if (p.dateDisplayMode == TimePlanDateDisplayMode.RELATIVE_D_DAY) {
        val base = s.toLocalDate()
        fun relativeLabel(dt: LocalDateTime): String {
            val day = java.time.temporal.ChronoUnit.DAYS.between(base, dt.toLocalDate())
            val d = if (day == 0L) "D-Day" else "D${if (day > 0) "+" else ""}$day"
            return "$d ${dt.format(DateTimeFormatter.ofPattern("HH:mm"))}"
        }
        return "${relativeLabel(s)} → ${relativeLabel(e)}"
    }

    return "${s.format(DateTimeFormatter.ofPattern("MM.dd HH:mm"))} → ${e.format(DateTimeFormatter.ofPattern("MM.dd HH:mm"))}"
}

@Composable
private fun DateTimeEditorDialog(
    title: String,
    initial: LocalDateTime?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime) -> Unit
) {
    val base = initial ?: LocalDateTime.now().withSecond(0).withNano(0)
    var candidate by remember(initial) { mutableStateOf(base) }
    var raw by remember(initial) {
        mutableStateOf("%02d%02d".format(base.hour, base.minute))
    }
    var calendar by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = ArmyristPanelShape,
            border = BorderStroke(1.dp, ArmyristColors.Border),
            colors = CardDefaults.cardColors(
                containerColor = ArmyristColors.RaisedSurface
            )
        ) {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = ArmyristColors.PrimaryText
                )

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { candidate = candidate.minusDays(1) },
                        modifier = Modifier.weight(1f).heightIn(min = 38.dp),
                        shape = ArmyristPanelShape,
                        border = BorderStroke(1.dp, ArmyristColors.Border),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("-1일")
                    }

                    Surface(
                        modifier = Modifier.weight(2f),
                        shape = ArmyristPanelShape,
                        color = ArmyristColors.WorkSurface,
                        border = BorderStroke(1.dp, ArmyristColors.Border)
                    ) {
                        Text(
                            candidate.toLocalDate()
                                .format(DateTimeFormatter.ofPattern("yyyy.MM.dd")),
                            modifier = Modifier.padding(vertical = 9.dp),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            color = ArmyristColors.PrimaryText
                        )
                    }

                    OutlinedButton(
                        onClick = { candidate = candidate.plusDays(1) },
                        modifier = Modifier.weight(1f).heightIn(min = 38.dp),
                        shape = ArmyristPanelShape,
                        border = BorderStroke(1.dp, ArmyristColors.Border),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("+1일")
                    }
                }

                OutlinedButton(
                    onClick = { calendar = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 38.dp),
                    shape = ArmyristPanelShape,
                    border = BorderStroke(1.dp, ArmyristColors.Border),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = ArmyristColors.WorkSurface,
                        contentColor = ArmyristColors.PrimaryText
                    )
                ) {
                    Text("달력에서 선택")
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "시간",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = ArmyristColors.SecondaryText
                        )
                        Spacer(Modifier.height(4.dp))
                        Wheel(
                            (0..23).toList(),
                            candidate.hour,
                            { value ->
                                val next = candidate.withHour(value)
                                candidate = next
                                raw = "%02d%02d".format(next.hour, next.minute)
                            },
                            Modifier.fillMaxWidth()
                        )
                    }

                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "분",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = ArmyristColors.SecondaryText
                        )
                        Spacer(Modifier.height(4.dp))
                        Wheel(
                            (0..55 step 5).toList(),
                            nearestFive(candidate.minute),
                            { value ->
                                val next = candidate.withMinute(value)
                                candidate = next
                                raw = "%02d%02d".format(next.hour, next.minute)
                            },
                            Modifier.fillMaxWidth()
                        )
                    }
                }

                OutlinedTextField(
                    value = raw,
                    onValueChange = { value ->
                        val digits = value.filter(Char::isDigit).take(4)
                        raw = digits
                        parseHHMM(digits)?.let {
                            candidate = candidate
                                .withHour(it.first)
                                .withMinute(it.second)
                        }
                    },
                    label = { Text("직접입력 · HHMM") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ArmyristColors.PrimaryControl,
                        cursorColor = ArmyristColors.PrimaryControl
                    )
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ArmyristPanelShape,
                    color = ArmyristColors.WorkSurface,
                    border = BorderStroke(1.dp, ArmyristColors.Border)
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                        Text(
                            "변경값",
                            style = MaterialTheme.typography.labelSmall,
                            color = ArmyristColors.SecondaryText
                        )
                        Text(
                            candidate.format(
                                DateTimeFormatter.ofPattern("MM.dd HH:mm")
                            ),
                            fontWeight = FontWeight.Bold,
                            color = ArmyristColors.PrimaryControl
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                        shape = ArmyristPanelShape,
                        border = BorderStroke(1.dp, ArmyristColors.Border)
                    ) {
                        Text("취소")
                    }

                    Button(
                        onClick = { onConfirm(candidate) },
                        modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                        shape = ArmyristPanelShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ArmyristColors.PrimaryControl,
                            contentColor = ArmyristColors.OnDark
                        )
                    ) {
                        Text("적용")
                    }
                }
            }
        }
    }

    if (calendar) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = candidate.toLocalDate()
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { calendar = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { ms ->
                            candidate = LocalDateTime.of(
                                Instant.ofEpochMilli(ms)
                                    .atZone(ZoneOffset.UTC)
                                    .toLocalDate(),
                                candidate.toLocalTime()
                            )
                        }
                        calendar = false
                    }
                ) {
                    Text("확인")
                }
            },
            dismissButton = {
                TextButton(onClick = { calendar = false }) {
                    Text("취소")
                }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun Wheel(
    values: List<Int>,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier
) {
    require(values.isNotEmpty())
    val view = LocalView.current
    val itemHeight = 44.dp
    val centerPadding = itemHeight
    val cycles = 1000
    val middle = cycles / 2
    val selectedIndex = values.indexOf(selected).coerceAtLeast(0)
    val initialCenteredIndex = middle * values.size + selectedIndex
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = initialCenteredIndex.coerceAtLeast(0)
    )
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = state)
    var centeredIndex by remember { mutableIntStateOf(initialCenteredIndex) }
    var programmaticSync by remember { mutableStateOf(false) }

    LaunchedEffect(selected, state.isScrollInProgress) {
        if (state.isScrollInProgress) return@LaunchedEffect
        val currentNormalized = ((centeredIndex % values.size) + values.size) % values.size
        if (currentNormalized == selectedIndex) return@LaunchedEffect
        val currentCycle = centeredIndex / values.size
        var target = currentCycle * values.size + selectedIndex
        if (target - centeredIndex > values.size / 2) target -= values.size
        if (centeredIndex - target > values.size / 2) target += values.size
        target = target.coerceIn(1, cycles * values.size - 2)
        programmaticSync = true
        state.scrollToItem(target)
        centeredIndex = target
        programmaticSync = false
    }

    LaunchedEffect(state) {
        snapshotFlow {
            val info = state.layoutInfo
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
            val centered = info.visibleItemsInfo.minByOrNull { item ->
                kotlin.math.abs((item.offset + item.size / 2) - viewportCenter)
            }
            Triple(centered?.index, state.isScrollInProgress, programmaticSync)
        }.collect { (newCenteredIndex, scrolling, syncing) ->
            if (newCenteredIndex == null) return@collect
            if (newCenteredIndex != centeredIndex) {
                centeredIndex = newCenteredIndex
                if (scrolling && !syncing) {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    val normalized = ((newCenteredIndex % values.size) + values.size) % values.size
                    onSelected(values[normalized])
                }
            }
        }
    }

    Box(
        modifier = modifier.height(132.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(itemHeight),
            shape = ArmyristPanelShape,
            color = ArmyristColors.PrimaryControl,
            border = BorderStroke(1.dp, ArmyristColors.PrimaryControl)
        ) {}
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = centerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(cycles * values.size) { index ->
                val value = values[index % values.size]
                Box(Modifier.fillMaxWidth().height(itemHeight), contentAlignment = Alignment.Center) {
                    val isCentered = index == centeredIndex
                    Text(
                        "%02d".format(value),
                        fontSize = if (isCentered) 21.sp else 17.sp,
                        fontWeight = if (isCentered) FontWeight.Bold else FontWeight.Medium,
                        color = if (isCentered) Color.White else ArmyristColors.SecondaryText
                    )
                }
            }
        }
    }
}

private fun nearestFive(m:Int)=((m+2)/5*5)%60
private fun parseHHMM(raw:String):Pair<Int,Int>?{if(raw.length !in 3..4)return null;val n=raw.toIntOrNull()?:return null;val h=n/100;val m=n%100;return if(h in 0..23&&m in 0..59)h to m else null}

@Composable
private fun DateDurationDialog(initial:Long?,initialLabel:String,onDismiss:()->Unit,onConfirm:(Long,String)->Unit){
    var total by remember{mutableLongStateOf(initial?:0L)};var label by remember{mutableStateOf(initialLabel)};var raw by remember{mutableStateOf(total.toString())};val h=(total/60).toInt();val m=(total%60).toInt()
    Dialog(onDismissRequest=onDismiss){Card(Modifier.fillMaxWidth(),shape=ArmyristPanelShape){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("경과시간",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);OutlinedTextField(label,{label=it},label={Text("경과 명칭")},modifier=Modifier.fillMaxWidth());Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(18.dp)){
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("시간", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Wheel((0..maxOf(999,h)).toList(),h,{nh->val next=nh*60L+(total%60);total=next;raw=next.toString()},Modifier.fillMaxWidth())
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("분", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Wheel((0..55 step 5).toList(),nearestFive(m),{nm->val next=(total/60)*60+nm;total=next;raw=next.toString()},Modifier.fillMaxWidth())
        }
    };OutlinedTextField(raw,{v->val d=v.filter(Char::isDigit);raw=d;d.toLongOrNull()?.let{total=it}},label={Text("직접입력 · 전체 분")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth());Surface(Modifier.fillMaxWidth(),shape=ArmyristPanelShape,color=ArmyristColors.WorkSurface,border=BorderStroke(1.dp,ArmyristColors.Border)){Column(Modifier.padding(10.dp)){Text("변경값",style=MaterialTheme.typography.labelSmall,color=ArmyristColors.SecondaryText);Text("${label.ifBlank { "경과" }} · ${durationText(total)}",fontWeight=FontWeight.Bold)}};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick=onDismiss,modifier=Modifier.weight(1f)){Text("취소")};Button(onClick={onConfirm(total,label)},modifier=Modifier.weight(1f),colors=ButtonDefaults.buttonColors(containerColor=ArmyristColors.PrimaryControl)){Text("적용")}}}}}
}

@Composable
private fun DateTimeEventEditDialog(
    event: DateTimeEvent,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onConfirm: (DateTimeEvent) -> Unit
) {
    var name by remember(event.id) { mutableStateOf(event.name) }
    var note by remember(event.id) { mutableStateOf(event.note.orEmpty()) }
    var isRange by remember(event.id) { mutableStateOf(event.timeSpec is EventDateTimeSpec.Range) }
    var a by remember(event.id) {
        mutableStateOf(
            DateTimePlanRules.arrival(event.timeSpec) ?: LocalDateTime.now()
        )
    }
    var b by remember(event.id) {
        mutableStateOf(
            DateTimePlanRules.departure(event.timeSpec)
                ?: DateTimePlanRules.arrival(event.timeSpec)
                ?: LocalDateTime.now()
        )
    }
    var editA by remember { mutableStateOf(false) }
    var editB by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = ArmyristPanelShape,
            border = BorderStroke(1.dp, ArmyristColors.Border),
            colors = CardDefaults.cardColors(
                containerColor = ArmyristColors.RaisedSurface
            )
        ) {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    if (event.kind == TimeEventKind.FINAL) {
                        "종료지점 편집"
                    } else {
                        "중도지점 편집"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = ArmyristColors.PrimaryText
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("지점명") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ArmyristColors.PrimaryControl,
                        cursorColor = ArmyristColors.PrimaryControl
                    )
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("비고") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ArmyristColors.PrimaryControl,
                        cursorColor = ArmyristColors.PrimaryControl
                    )
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = isRange,
                        onCheckedChange = { isRange = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ArmyristColors.WorkSurface,
                            checkedTrackColor = ArmyristColors.PrimaryControl
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("시간범위 사용", color = ArmyristColors.PrimaryText)
                }

                OutlinedButton(
                    onClick = { editA = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp),
                    shape = ArmyristPanelShape,
                    border = BorderStroke(1.dp, ArmyristColors.Border),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = ArmyristColors.WorkSurface,
                        contentColor = ArmyristColors.PrimaryText
                    )
                ) {
                    Text(
                        (if (isRange) "시작 " else "시각 ") +
                            a.format(DateTimeFormatter.ofPattern("MM.dd HH:mm"))
                    )
                }

                if (isRange) {
                    OutlinedButton(
                        onClick = { editB = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp),
                        shape = ArmyristPanelShape,
                        border = BorderStroke(1.dp, ArmyristColors.Border),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = ArmyristColors.WorkSurface,
                            contentColor = ArmyristColors.PrimaryText
                        )
                    ) {
                        Text(
                            "종료 " +
                                b.format(DateTimeFormatter.ofPattern("MM.dd HH:mm"))
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.heightIn(min = 38.dp)
                    ) {
                        Text("삭제")
                    }

                    Spacer(Modifier.weight(1f))

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 38.dp),
                        shape = ArmyristPanelShape
                    ) {
                        Text("취소")
                    }

                    Button(
                        onClick = {
                            val spec =
                                if (isRange) {
                                    EventDateTimeSpec.Range(
                                        DateTimeValue.explicit(a),
                                        DateTimeValue.explicit(b)
                                    )
                                } else {
                                    EventDateTimeSpec.Single(
                                        DateTimeValue.explicit(a)
                                    )
                                }
                            onConfirm(
                                event.copy(
                                    name = name.trim().ifBlank { event.name },
                                    note = note.trim().ifBlank { null },
                                    timeSpec = spec
                                )
                            )
                        },
                        modifier = Modifier.heightIn(min = 38.dp),
                        shape = ArmyristPanelShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ArmyristColors.PrimaryControl,
                            contentColor = ArmyristColors.OnDark
                        )
                    ) {
                        Text("확인")
                    }
                }
            }
        }
    }

    if (editA) {
        DateTimeEditorDialog(
            if (isRange) "시작 날짜 / 시간" else "날짜 / 시간",
            a,
            { editA = false }
        ) {
            a = it
            editA = false
        }
    }

    if (editB) {
        DateTimeEditorDialog(
            "종료 날짜 / 시간",
            b,
            { editB = false }
        ) {
            b = it
            editB = false
        }
    }
}

@Composable private fun LegacyDateMigrationDialog(title:String,onDismiss:()->Unit,onApply:(LocalDate)->Unit){var date by remember{mutableStateOf(LocalDate.now())};var calendar by remember{mutableStateOf(false)};AlertDialog(onDismissRequest=onDismiss,title={Text("기준 날짜 지정")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){Text("‘$title’은 날짜 기능이 추가되기 전에 작성되었습니다.\n계획의 시작 기준 날짜를 선택해주세요.");Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){OutlinedButton(onClick={date=date.minusDays(1)}){Text("-1일")};Text(date.format(DateTimeFormatter.ISO_DATE),fontWeight=FontWeight.Bold);OutlinedButton(onClick={date=date.plusDays(1)}){Text("+1일")}};TextButton(onClick={calendar=true},modifier=Modifier.fillMaxWidth()){Text("달력")};Text("적용 전에는 기존 데이터가 변경되지 않습니다.",style=MaterialTheme.typography.bodySmall,color=ArmyristColors.SecondaryText)}},dismissButton={TextButton(onClick=onDismiss){Text("취소")}},confirmButton={Button(onClick={onApply(date)},colors=ButtonDefaults.buttonColors(containerColor=ArmyristColors.PrimaryControl)){Text("적용")}});if(calendar){val state=rememberDatePickerState(initialSelectedDateMillis=date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());DatePickerDialog(onDismissRequest={calendar=false},confirmButton={TextButton(onClick={state.selectedDateMillis?.let{date=Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()};calendar=false}){Text("확인")}}){DatePicker(state=state)}}}

@Composable
private fun TimePlanVoiceReviewDialog(
    initial: List<TimePlanVoiceDraft>,
    onDismiss: () -> Unit,
    onApply: (List<TimePlanVoiceDraft>) -> Unit
) {
    var drafts by remember { mutableStateOf(initial) }
    var editingDateIndex by remember { mutableStateOf<Int?>(null) }
    var editingRangeEndIndex by remember { mutableStateOf<Int?>(null) }

    fun update(index: Int, draft: TimePlanVoiceDraft) {
        drafts = drafts.toMutableList().also { it[index] = KoreanVoiceStructurer.revalidate(draft) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth().fillMaxHeight(.88f).imePadding(), shape = ArmyristPanelShape) {
            Column(Modifier.padding(14.dp)) {
                Text("음성 입력 검토", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "아래 Draft를 확인하고 필요한 항목을 수정하세요. 적용 전에는 기존 시간계획이 변경되지 않습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ArmyristColors.SecondaryText
                )
                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(drafts.indices.toList(), key = { it }) { i ->
                        val d = drafts[i]
                        val timeFieldState = when {
                            d.dateTimeState == VoiceFieldState.INVALID || d.rangeEndState == VoiceFieldState.INVALID -> VoiceFieldState.INVALID
                            d.dateTimeState == VoiceFieldState.REVIEW_REQUIRED || d.rangeEndState == VoiceFieldState.REVIEW_REQUIRED -> VoiceFieldState.REVIEW_REQUIRED
                            else -> VoiceFieldState.VALID
                        }
                        val borderColor = when (d.state) {
                            VoiceDraftState.VALID -> ArmyristColors.Border
                            VoiceDraftState.REVIEW_REQUIRED -> Color(0xFFC77800)
                            VoiceDraftState.INVALID -> ArmyristColors.Danger
                        }
                        Card(
                            Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, borderColor)
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                VoiceDraftStatusHeader(d.state)

                                OutlinedTextField(
                                    value = d.name,
                                    onValueChange = { v ->
                                        update(i, d.copy(
                                            name = v,
                                            nameState = if (v.isBlank()) VoiceFieldState.INVALID else VoiceFieldState.VALID
                                        ))
                                    },
                                    label = { Text("일정명") },
                                    trailingIcon = { VoiceFieldMarker(d.nameState) },
                                    colors = voiceReviewFieldColors(d.nameState),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = ArmyristPanelShape,
                                    color = ArmyristColors.WorkSurface,
                                    border = BorderStroke(
                                        1.dp,
                                        when (timeFieldState) {
                                            VoiceFieldState.VALID -> ArmyristColors.Border
                                            VoiceFieldState.REVIEW_REQUIRED -> Color(0xFFC77800)
                                            VoiceFieldState.INVALID -> ArmyristColors.Danger
                                        }
                                    )
                                ) {
                                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("날짜 / 시간", style = MaterialTheme.typography.labelMedium)
                                        Text(
                                            when {
                                                d.dateTime == null -> "날짜/시간 확인 필요"
                                                d.rangeEnd != null ->
                                                    "${d.dateTime.format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"))} ~ " +
                                                        d.rangeEnd.format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"))
                                                else -> d.dateTime.format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"))
                                            },
                                            fontWeight = FontWeight.Bold,
                                            color = when (timeFieldState) {
                                                VoiceFieldState.VALID -> ArmyristColors.PrimaryText
                                                VoiceFieldState.REVIEW_REQUIRED -> Color(0xFFC77800)
                                                VoiceFieldState.INVALID -> ArmyristColors.Danger
                                            }
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(onClick = { editingDateIndex = i }) {
                                        Text(if (d.rangeEnd != null) "시작 수정" else "날짜/시간 수정")
                                    }
                                    if (d.rangeEnd != null) {
                                        OutlinedButton(onClick = { editingRangeEndIndex = i }) { Text("종료 수정") }
                                    }
                                    TextButton(onClick = {
                                        drafts = drafts.filterIndexed { idx, _ -> idx != i }
                                    }) { Text("삭제") }
                                }

                                VoiceTranscriptDisclosure(d.rawTranscript)
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("전체 취소") }
                    Button(
                        onClick = { onApply(drafts) },
                        enabled = drafts.isNotEmpty() && drafts.none { it.state == VoiceDraftState.INVALID },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = ArmyristColors.PrimaryControl)
                    ) { Text("적용") }
                }
            }
        }
    }

    editingDateIndex?.let { index ->
        val current = drafts.getOrNull(index)
        if (current != null) {
            DateTimeEditorDialog(
                title = if (current.rangeEnd != null) "Draft 범위 시작" else "Draft 날짜 / 시간",
                initial = current.dateTime,
                onDismiss = { editingDateIndex = null },
                onConfirm = { dt ->
                    val endState = when {
                        current.rangeEnd == null -> current.rangeEndState
                        current.rangeEnd.isBefore(dt) -> VoiceFieldState.INVALID
                        else -> current.rangeEndState
                    }
                    update(index, current.copy(
                        dateTime = dt,
                        dateTimeState = VoiceFieldState.VALID,
                        rangeEndState = endState
                    ))
                    editingDateIndex = null
                }
            )
        } else editingDateIndex = null
    }

    editingRangeEndIndex?.let { index ->
        val current = drafts.getOrNull(index)
        if (current != null) {
            DateTimeEditorDialog(
                title = "Draft 범위 종료",
                initial = current.rangeEnd ?: current.dateTime,
                onDismiss = { editingRangeEndIndex = null },
                onConfirm = { dt ->
                    update(index, current.copy(
                        rangeEnd = dt,
                        rangeEndState = if (current.dateTime != null && !dt.isBefore(current.dateTime))
                            VoiceFieldState.VALID else VoiceFieldState.INVALID
                    ))
                    editingRangeEndIndex = null
                }
            )
        } else editingRangeEndIndex = null
    }
}

@Composable
private fun SimpleTextDialog(
    title: String,
    initial: String,
    single: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by rememberSaveable(title, initial) { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = ArmyristPanelShape,
        containerColor = ArmyristColors.RaisedSurface,
        tonalElevation = 0.dp,
        titleContentColor = ArmyristColors.PrimaryText,
        textContentColor = ArmyristColors.PrimaryText,
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = single,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ArmyristColors.PrimaryControl,
                    cursorColor = ArmyristColors.PrimaryControl
                )
            )
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = ArmyristPanelShape
            ) { Text("취소") }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(value) },
                shape = ArmyristPanelShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ArmyristColors.PrimaryControl,
                    contentColor = ArmyristColors.OnDark
                )
            ) { Text("확인") }
        }
    )
}

private fun rangeDisplay(start: LocalDateTime?, end: LocalDateTime?): String {
    if (start == null || end == null) return "--:-- ~ --:--"
    return if (start.toLocalDate() == end.toLocalDate()) {
        "${start.format(DateTimeFormatter.ofPattern("HH:mm"))} ~ ${end.format(DateTimeFormatter.ofPattern("HH:mm"))}"
    } else {
        "${start.format(DateTimeFormatter.ofPattern("HH:mm"))} ~ ${end.format(DateTimeFormatter.ofPattern("MM.dd HH:mm"))}"
    }
}

fun generateDateAwareResult(plan: DateAwareTimePlan): ToolResult {
    val lines = mutableListOf<String>()
    var day: LocalDate? = null

    fun header(dt: LocalDateTime?) {
        val d = dt?.toLocalDate() ?: return
        if (d != day) {
            day = d
            val dow = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
            lines += "[${d.format(DateTimeFormatter.ofPattern("MM.dd"))} ($dow)]"
        }
    }

    val nodes = DateTimePlanRules.nodeIds(plan)
    val events = plan.orderedEvents().associateBy { it.id }

    nodes.forEachIndexed { index, nodeId ->
        when (nodeId) {
            DateTimePlanRules.START_ID -> {
                plan.start.value.value?.let {
                    header(it)
                    lines += "- ${it.format(DateTimeFormatter.ofPattern("HH:mm"))} 시작"
                }
            }
            DateTimePlanRules.END_ID -> {
                plan.end.value.value?.let {
                    header(it)
                    lines += "- ${it.format(DateTimeFormatter.ofPattern("HH:mm"))} 종료"
                }
            }
            else -> {
                events[nodeId]?.let { e ->
                    val arrival = DateTimePlanRules.arrival(e.timeSpec)
                    header(arrival)
                    val time = when (val spec = e.timeSpec) {
                        EventDateTimeSpec.Unspecified -> "--:--"
                        is EventDateTimeSpec.Single ->
                            spec.value.value?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--:--"
                        is EventDateTimeSpec.Range ->
                            rangeDisplay(spec.start.value, spec.end.value)
                    }
                    lines += "- $time ${e.name}${e.note?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}"
                }
            }
        }

        if (index < nodes.lastIndex) {
            val next = nodes[index + 1]
            plan.links.firstOrNull { it.fromNodeId == nodeId && it.toNodeId == next }?.let { link ->
                val minutes =
                    if (link.durationLocked) {
                        link.durationMinutes
                    } else {
                        val fromTime = DateTimePlanRules.nodeDeparture(plan, nodeId)
                        val toTime = DateTimePlanRules.nodeArrival(plan, next)
                        if (fromTime != null && toTime != null && !toTime.isBefore(fromTime)) {
                            java.time.Duration.between(fromTime, toTime).toMinutes()
                        } else null
                    }
                if (minutes != null && minutes > 0L) {
                    lines += "  ↳ ${link.label?.takeIf { it.isNotBlank() } ?: "경과"} · ${durationText(minutes)}"
                }
            }
        }
    }

    plan.memo?.takeIf { it.isNotBlank() }?.let {
        lines += ""
        lines += "메모: $it"
    }
    return ToolResult(plan.title, lines.joinToString("\n"))
}
