package com.seolhwa.armyrist.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import com.seolhwa.armyrist.ChecklistActivity
import com.seolhwa.armyrist.stage2.data.CoreSuiteRepository
import com.seolhwa.armyrist.stage2.domain.Checklist
import com.seolhwa.armyrist.stage2.domain.ChecklistItem
import com.seolhwa.armyrist.stage2.domain.ChecklistStatus
import java.util.Calendar

object ChecklistNotificationManager {
    private const val CHANNEL_PREFIX = "checklist_item_sound_v4_"

    const val EXTRA_CHECKLIST_ID = "checklistId"
    const val EXTRA_ITEM_ID = "itemId"

    fun soundUri(context: Context, item: ChecklistItem): Uri =
        item.notificationSoundUri
            ?.takeIf { it.isNotBlank() }
            ?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    fun soundTitle(context: Context, soundUri: String?): String {
        val uri = soundUri
            ?.takeIf { it.isNotBlank() }
            ?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        return runCatching {
            RingtoneManager.getRingtone(context, uri)?.getTitle(context)
        }.getOrNull() ?: "기본 알람음"
    }

    fun channelId(context: Context, item: ChecklistItem): String {
        val key = soundUri(context, item).toString()
        return CHANNEL_PREFIX + key.hashCode().toUInt().toString(16)
    }

    fun createChannel(context: Context, item: ChecklistItem): String {
        val id = channelId(context, item)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(id) == null) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                manager.createNotificationChannel(
                    NotificationChannel(
                        id,
                        "체크리스트 알람",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "체크리스트 항목의 지정 시각 알람"
                        setSound(soundUri(context, item), audioAttributes)
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0, 300, 180, 300)
                        lockscreenVisibility =
                            android.app.Notification.VISIBILITY_PUBLIC
                    }
                )
            }
        }

        return id
    }

    fun notificationPermissionGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun notificationsEnabled(context: Context, item: ChecklistItem): Boolean {
        if (!notificationPermissionGranted(context)) return false

        val manager = context.getSystemService(NotificationManager::class.java)
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            !manager.areNotificationsEnabled()
        ) {
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = manager.getNotificationChannel(createChannel(context, item))
            if (channel?.importance == NotificationManager.IMPORTANCE_NONE) {
                return false
            }
        }

        return true
    }

    fun exactAlarmAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java)
            .canScheduleExactAlarms()
    }

    fun scheduledEpochMillis(
        minutes: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Long? {
        if (minutes !in 0..1439) return null

        val now = Calendar.getInstance().apply {
            timeInMillis = nowMillis
        }
        val target = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, minutes / 60)
            set(Calendar.MINUTE, minutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return target.timeInMillis.takeIf { it > now.timeInMillis }
    }

    fun isEligible(
        context: Context,
        item: ChecklistItem,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean =
        item.status == ChecklistStatus.INCOMPLETE &&
            item.notificationEnabled &&
            item.scheduledTimeMinutes != null &&
            scheduledEpochMillis(item.scheduledTimeMinutes, nowMillis) != null &&
            notificationsEnabled(context, item)

    fun reconcile(context: Context, repository: CoreSuiteRepository) {
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
        checklist.deletedItems.forEach { cancel(context, it.id) }
        checklist.items.forEach { item ->
            if (isEligible(context, item)) {
                schedule(context, checklist, item)
            } else {
                cancel(context, item.id)
            }
        }
    }

    fun schedule(
        context: Context,
        checklist: Checklist,
        item: ChecklistItem
    ): Boolean {
        val configured = item.scheduledTimeMinutes ?: return false
        val triggerAt = scheduledEpochMillis(configured) ?: run {
            cancel(context, item.id)
            return false
        }

        if (
            item.status != ChecklistStatus.INCOMPLETE ||
            !item.notificationEnabled ||
            !notificationsEnabled(context, item)
        ) {
            cancel(context, item.id)
            return false
        }

        val alarmManager = context.getSystemService(AlarmManager::class.java)

        // IMPORTANT:
        // Previous implementation built the PendingIntent first and then cancel()
        // cancelled that exact PendingIntent object before it was scheduled.
        // Always cancel the old alarm FIRST, then create a fresh PendingIntent.
        cancel(context, item.id)

        val pending = alarmPendingIntent(
            context = context,
            checklistId = checklist.id,
            itemId = item.id
        )

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !alarmManager.canScheduleExactAlarms()
        ) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pending
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pending
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pending
            )
        }

        return true
    }

    fun cancel(context: Context, itemId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)

        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(itemId),
            Intent(context, ChecklistAlarmReceiver::class.java)
                .setAction("com.seolhwa.armyrist.CHECKLIST_ALARM"),
            PendingIntent.FLAG_NO_CREATE or immutableFlag()
        )

        if (pending != null) {
            alarmManager.cancel(pending)
            pending.cancel()
        }

        context.getSystemService(NotificationManager::class.java)
            .cancel(requestCode(itemId))
    }

    fun cancelChecklist(context: Context, checklist: Checklist) {
        (checklist.items + checklist.deletedItems).forEach {
            cancel(context, it.id)
        }
    }

    fun openChecklistIntent(
        context: Context,
        checklistId: String,
        itemId: String
    ): PendingIntent {
        val intent = Intent(context, ChecklistActivity::class.java).apply {
            putExtra(EXTRA_CHECKLIST_ID, checklistId)
            putExtra(EXTRA_ITEM_ID, itemId)
            flags =
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        return PendingIntent.getActivity(
            context,
            requestCode("open:$itemId"),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
    }

    private fun alarmPendingIntent(
        context: Context,
        checklistId: String,
        itemId: String
    ): PendingIntent {
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

    private fun requestCode(value: String): Int =
        value.hashCode() and 0x7fffffff

    private fun immutableFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
}
