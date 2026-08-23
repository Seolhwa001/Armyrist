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
import com.seolhwa.armyrist.TimePlanExecutionActivity
import com.seolhwa.armyrist.timeplan.v3.data.DateAwareTimePlanRepository
import com.seolhwa.armyrist.timeplan.v3.domain.ActionCompletionState
import com.seolhwa.armyrist.timeplan.v3.domain.DateAwareTimePlan
import com.seolhwa.armyrist.timeplan.v3.domain.TimePlanActionItem
import java.time.ZoneId

object TimePlanActionNotificationManager {
    private const val CHANNEL_ID = "timeplan_action_reminder_v1"
    private const val PREFS = "armyrist_timeplan_action_alarms"
    private const val KEY_IDS = "scheduled_ids"

    const val EXTRA_PLAN_ID = "planId"
    const val EXTRA_ACTION_ID = "actionId"

    fun notificationPermissionGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "시간계획 실시사항 알림",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "시간계획 수행 모드 실시사항 예정 시각 알림"
                    }
                )
            }
        }
    }

    fun channelId(): String = CHANNEL_ID

    fun isEligible(
        context: Context,
        action: TimePlanActionItem,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val trigger = action.scheduledDateTime
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        return action.notificationEnabled &&
            action.completionState == ActionCompletionState.INCOMPLETE &&
            trigger > nowMillis &&
            notificationPermissionGranted(context)
    }

    fun reconcile(context: Context, repository: DateAwareTimePlanRepository) {
        createChannel(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previously = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty().toSet()
        val active = mutableSetOf<String>()

        repository.getPlans().forEach { plan ->
            plan.actions.forEach { action ->
                val key = key(plan.id, action.id)
                if (isEligible(context, action)) {
                    schedule(context, plan, action)
                    active += key
                } else {
                    cancel(context, plan.id, action.id)
                }
            }
        }

        (previously - active).forEach { stored ->
            val parts = stored.split('|', limit = 2)
            if (parts.size == 2) cancel(context, parts[0], parts[1])
        }
        prefs.edit().putStringSet(KEY_IDS, active).apply()
    }

    fun schedule(context: Context, plan: DateAwareTimePlan, action: TimePlanActionItem): Boolean {
        if (!isEligible(context, action)) {
            cancel(context, plan.id, action.id)
            return false
        }

        val triggerAt = action.scheduledDateTime
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val manager = context.getSystemService(AlarmManager::class.java)
        cancel(context, plan.id, action.id)
        val pending = alarmPendingIntent(context, plan.id, action.id)

        // Reminder contract: deliver at the scheduled time even while the screen is off
        // whenever Android grants exact-alarm capability. If the user/device does not
        // grant that capability, retain a safe inexact while-idle fallback instead of crashing.
        val exactAllowed =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && exactAllowed ->
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            else ->
                manager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
        return true
    }

    fun cancel(context: Context, planId: String, actionId: String) {
        val manager = context.getSystemService(AlarmManager::class.java)
        PendingIntent.getBroadcast(
            context,
            requestCode(planId, actionId),
            Intent(context, TimePlanActionAlarmReceiver::class.java)
                .setAction("com.seolhwa.armyrist.TIMEPLAN_ACTION_ALARM"),
            PendingIntent.FLAG_NO_CREATE or immutableFlag()
        )?.let {
            manager.cancel(it)
            it.cancel()
        }
        context.getSystemService(NotificationManager::class.java)
            .cancel(requestCode(planId, actionId))
    }

    fun openActionPendingIntent(context: Context, planId: String, actionId: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode(planId, actionId),
            Intent(context, TimePlanExecutionActivity::class.java).apply {
                putExtra(TimePlanExecutionActivity.EXTRA_PLAN_ID, planId)
                putExtra(TimePlanExecutionActivity.EXTRA_MODE, TimePlanExecutionActivity.MODE_EXECUTE)
                putExtra(EXTRA_ACTION_ID, actionId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )

    private fun alarmPendingIntent(context: Context, planId: String, actionId: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(planId, actionId),
            Intent(context, TimePlanActionAlarmReceiver::class.java).apply {
                action = "com.seolhwa.armyrist.TIMEPLAN_ACTION_ALARM"
                putExtra(EXTRA_PLAN_ID, planId)
                putExtra(EXTRA_ACTION_ID, actionId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )

    private fun requestCode(planId: String, actionId: String): Int =
        ("$planId|$actionId".hashCode() and 0x7fffffff)

    private fun key(planId: String, actionId: String): String = "$planId|$actionId"

    private fun immutableFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
}
