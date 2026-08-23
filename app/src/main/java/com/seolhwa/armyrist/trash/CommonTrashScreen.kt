package com.seolhwa.armyrist.trash

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.seolhwa.armyrist.ArmyristColors
import com.seolhwa.armyrist.ArmyristPanelShape
import com.seolhwa.armyrist.ArmyristTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

@Composable
fun CommonTrashScreen(
    toolLabel: String,
    items: List<CommonTrashItem>,
    retentionDays: Int,
    onBack: () -> Unit,
    onRetentionChange: (Int) -> Unit,
    onRestore: (CommonTrashItem) -> Unit,
    onPermanentDelete: (CommonTrashItem) -> Unit
) {
    var retentionDialog by remember { mutableStateOf(false) }
    var permanentDeleteTarget by remember { mutableStateOf<CommonTrashItem?>(null) }

    Scaffold(
        topBar = {
            ArmyristTopBar(
                "휴지통",
                "$toolLabel · LOCAL TRASH",
                "뒤로",
                onBack,
                leadingIcon = com.seolhwa.armyrist.ArmyristTopBarLeadingIcon.BACK
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = ArmyristPanelShape,
                colors = CardDefaults.cardColors(
                    containerColor = ArmyristColors.WorkSurface
                ),
                border = BorderStroke(1.dp, ArmyristColors.Border)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "자동 삭제",
                            fontWeight = FontWeight.Bold,
                            color = ArmyristColors.PrimaryText
                        )
                        Text(
                            CommonTrashRetention.label(retentionDays),
                            style = MaterialTheme.typography.bodySmall,
                            color = ArmyristColors.SecondaryText
                        )
                    }
                    IconButton(onClick = { retentionDialog = true }) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = "자동 삭제 기간 설정",
                            tint = ArmyristColors.PrimaryControl
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (items.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ArmyristPanelShape,
                    colors = CardDefaults.cardColors(
                        containerColor = ArmyristColors.RaisedSurface
                    ),
                    border = BorderStroke(1.dp, ArmyristColors.Border)
                ) {
                    Column(
                        Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("휴지통이 비어 있습니다", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "삭제한 항목은 설정한 기간 동안 이곳에 보관됩니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ArmyristColors.SecondaryText
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        TrashItemCard(
                            item = item,
                            retentionDays = retentionDays,
                            onRestore = { onRestore(item) },
                            onDelete = { permanentDeleteTarget = item }
                        )
                    }
                }
            }
        }
    }

    if (retentionDialog) {
        AlertDialog(
            onDismissRequest = { retentionDialog = false },
            shape = ArmyristPanelShape,
            containerColor = ArmyristColors.RaisedSurface,
            tonalElevation = 0.dp,
            title = { Text("자동 삭제 기간", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CommonTrashRetention.supportedDays.forEach { days ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = ArmyristPanelShape,
                            color =
                                if (retentionDays == days) ArmyristColors.SecondaryControl
                                else ArmyristColors.WorkSurface,
                            border = BorderStroke(
                                1.dp,
                                if (retentionDays == days) ArmyristColors.PrimaryControl
                                else ArmyristColors.Border
                            ),
                            onClick = {
                                onRetentionChange(days)
                                retentionDialog = false
                            }
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = retentionDays == days,
                                    onClick = null
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(CommonTrashRetention.label(days))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { retentionDialog = false }) {
                    Text("닫기")
                }
            }
        )
    }

    permanentDeleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { permanentDeleteTarget = null },
            shape = ArmyristPanelShape,
            containerColor = ArmyristColors.RaisedSurface,
            tonalElevation = 0.dp,
            title = { Text("영구 삭제", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "'${item.title}'을 영구 삭제합니다. 이 작업은 복구할 수 없습니다."
                )
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { permanentDeleteTarget = null },
                    shape = ArmyristPanelShape
                ) { Text("취소") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        permanentDeleteTarget = null
                        onPermanentDelete(item)
                    },
                    shape = ArmyristPanelShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ArmyristColors.PrimaryControl,
                        contentColor = ArmyristColors.OnDark
                    )
                ) { Text("영구 삭제") }
            }
        )
    }
}

@Composable
private fun TrashItemCard(
    item: CommonTrashItem,
    retentionDays: Int,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val deletedText = remember(item.deletedAt) {
        SimpleDateFormat("MM.dd HH:mm", Locale.getDefault()).format(Date(item.deletedAt))
    }
    val remainingDays = if (retentionDays == CommonTrashRetention.NEVER) null else {
        val expiresAt = item.deletedAt + TimeUnit.DAYS.toMillis(retentionDays.toLong())
        ceil(((expiresAt - System.currentTimeMillis()).coerceAtLeast(0L)) /
            TimeUnit.DAYS.toMillis(1).toDouble()).toInt()
    }
    val retentionText = when {
        retentionDays == CommonTrashRetention.NEVER -> "자동 삭제 안 함"
        remainingDays == null || remainingDays <= 0 -> "곧 자동 삭제"
        else -> "${remainingDays}일 후 자동 삭제"
    }
    val urgent = remainingDays != null && remainingDays <= 1

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ArmyristColors.RaisedSurface),
        border = BorderStroke(1.dp, ArmyristColors.Border)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ArmyristColors.PrimaryText
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "$retentionText · $deletedText 삭제",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (urgent) MaterialTheme.colorScheme.error else ArmyristColors.SecondaryText
                )
            }
            IconButton(onClick = onRestore) {
                Icon(
                    Icons.Outlined.Restore,
                    contentDescription = "복구",
                    tint = ArmyristColors.PrimaryControl
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "영구 삭제",
                    tint = ArmyristColors.SecondaryText
                )
            }
        }
    }
}
