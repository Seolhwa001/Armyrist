package com.seolhwa.armyrist.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.seolhwa.armyrist.ChecklistActivity
import com.seolhwa.armyrist.stage2.data.CoreSuiteRepository
import com.seolhwa.armyrist.stage2.domain.Checklist
import com.seolhwa.armyrist.stage2.domain.ChecklistItem
import com.seolhwa.armyrist.stage2.domain.ChecklistStatus
import java.util.Calendar

object ChecklistNotificationManager {
    const val CHANNEL_ID = "checklist_scheduled"
    const val EXTRA_CHECKLIST_ID = "checklistId"
    const val EXTRA_ITEM_ID = "itemId"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "체크리스트 알림",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "체크리스트 항목의 지정 시각 알림"
                }
            )
        }
    }

    fun notificationPermissionGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun notificationsEnabled(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        val enabledBySystem =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                manager.areNotificationsEnabled()
            } else {
                true
            }
        return notificationPermissionGranted(context) && enabledBySystem
    }

    fun scheduledEpochMillis(minutes: Int, nowMillis: Long = System.currentTimeMillis()): Long? {
        if (minutes !in 0..1439) return null
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val target = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, minutes / 60)
            set(Calendar.MINUTE, minutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return target.timeInMillis.takeIf { it > now.timeInMillis }
    }

    fun isEligible(context: Context, item: ChecklistItem, nowMillis: Long = System.currentTimeMillis()): Boolean =
        item.status == ChecklistStatus.INCOMPLETE &&
            item.notificationEnabled &&
            item.scheduledTimeMinutes != null &&
            scheduledEpochMillis(item.scheduledTimeMinutes, nowMillis) != null &&
            notificationsEnabled(context)

    fun reconcile(context: Context, repository: CoreSuiteRepository) {
        createChannel(context)
        repository.getChecklists().forEach { checklist ->
            checklist.deletedItems.forEach { cancel(context, it.id) }
            checklist.items.forEach { item ->
                if (isEligible(context, item)) {
                    schedule(context, checklist, item)
                } else {
                    cancel(context, item.id)
                }
            }
        }
    }

    fun reconcileChecklist(context: Context, checklist: Checklist) {
        createChannel(context)
        checklist.deletedItems.forEach { cancel(context, it.id) }
        checklist.items.forEach { item ->
            if (isEligible(context, item)) schedule(context, checklist, item)
            else cancel(context, item.id)
        }
    }

    fun schedule(context: Context, checklist: Checklist, item: ChecklistItem): Boolean {
        val time = item.scheduledTimeMinutes ?: return false
        val triggerAt = scheduledEpochMillis(time) ?: run {
            cancel(context, item.id)
            return false
        }
        if (!notificationsEnabled(context) || item.status != ChecklistStatus.INCOMPLETE || !item.notificationEnabled) {
            cancel(context, item.id)
            return false
        }

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pending = alarmPendingIntent(context, checklist.id, item.id)

        // Same item ID maps to the same PendingIntent, so repeated reconciliation replaces
        // rather than duplicates the schedule.
        cancel(context, item.id)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
        return true
    }

    fun cancel(context: Context, itemId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, ChecklistAlarmReceiver::class.java)
            .setAction("com.seolhwa.armyrist.CHECKLIST_ALARM")
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(itemId),
            intent,
            PendingIntent.FLAG_NO_CREATE or immutableFlag()
        )
        if (pending != null) {
            alarmManager.cancel(pending)
            pending.cancel()
        }
        context.getSystemService(NotificationManager::class.java).cancel(requestCode(itemId))
    }

    fun cancelChecklist(context: Context, checklist: Checklist) {
        (checklist.items + checklist.deletedItems).forEach { cancel(context, it.id) }
    }

    fun openChecklistIntent(context: Context, checklistId: String, itemId: String): PendingIntent {
        val intent = Intent(context, ChecklistActivity::class.java).apply {
            putExtra(EXTRA_CHECKLIST_ID, checklistId)
            putExtra(EXTRA_ITEM_ID, itemId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode("open:$itemId"),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
    }

    private fun alarmPendingIntent(context: Context, checklistId: String, itemId: String): PendingIntent {
        val intent = Intent(context, ChecklistAlarmReceiver::class.java).apply {
            action = "com.seolhwa.armyrist.CHECKLIST_ALARM"
            putExtra(EXTRA_CHECKLIST_ID, checklistId)
            putExtra(EXTRA_ITEM_ID, itemId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(itemId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
    }

    private fun requestCode(value: String): Int = value.hashCode() and 0x7fffffff

    private fun immutableFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
}
