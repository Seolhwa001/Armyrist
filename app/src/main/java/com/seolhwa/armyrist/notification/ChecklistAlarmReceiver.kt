package com.seolhwa.armyrist.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.seolhwa.armyrist.stage2.data.CoreSuiteRepository
import com.seolhwa.armyrist.stage2.domain.ChecklistStatus

class ChecklistAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val checklistId =
            intent.getStringExtra(
                ChecklistNotificationManager.EXTRA_CHECKLIST_ID
            ) ?: return

        val itemId =
            intent.getStringExtra(
                ChecklistNotificationManager.EXTRA_ITEM_ID
            ) ?: return

        val repository =
            CoreSuiteRepository(context.applicationContext)

        val checklist =
            repository.getChecklist(checklistId) ?: return

        val item =
            checklist.items.firstOrNull { it.id == itemId } ?: return

        // A delivered but stale alarm must never recreate or mutate deleted/completed data.
        if (
            item.status != ChecklistStatus.INCOMPLETE ||
            !item.notificationEnabled
        ) {
            return
        }

        if (!ChecklistNotificationManager.notificationsEnabled(context, item)) {
            return
        }

        val channelId =
            ChecklistNotificationManager.createChannel(context, item)

        val notification =
            NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(checklist.title)
                .setContentText(item.name)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(item.name)
                )
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(
                    ChecklistNotificationManager.openChecklistIntent(
                        context,
                        checklist.id,
                        item.id
                    )
                )
                .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(
                item.id.hashCode() and 0x7fffffff,
                notification
            )
    }
}
