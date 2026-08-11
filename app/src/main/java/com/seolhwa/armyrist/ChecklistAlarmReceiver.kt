package com.seolhwa.armyrist.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.seolhwa.armyrist.R
import com.seolhwa.armyrist.stage2.data.CoreSuiteRepository
import com.seolhwa.armyrist.stage2.domain.ChecklistStatus

class ChecklistAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val checklistId = intent.getStringExtra(ChecklistNotificationManager.EXTRA_CHECKLIST_ID) ?: return
        val itemId = intent.getStringExtra(ChecklistNotificationManager.EXTRA_ITEM_ID) ?: return

        val repository = CoreSuiteRepository(context.applicationContext)
        val checklist = repository.getChecklist(checklistId) ?: return
        val item = checklist.items.firstOrNull { it.id == itemId } ?: return

        // Stale alarms are safe no-ops.
        if (item.status != ChecklistStatus.INCOMPLETE || !item.notificationEnabled) return

        ChecklistNotificationManager.createChannel(context)
        if (!ChecklistNotificationManager.notificationsEnabled(context)) return

        val notification = NotificationCompat.Builder(context, ChecklistNotificationManager.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(checklist.title)
            .setContentText(item.name)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(
                ChecklistNotificationManager.openChecklistIntent(context, checklist.id, item.id)
            )
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(item.id.hashCode() and 0x7fffffff, notification)
    }
}
