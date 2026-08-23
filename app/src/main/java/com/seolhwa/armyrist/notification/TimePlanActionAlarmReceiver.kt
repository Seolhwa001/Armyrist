package com.seolhwa.armyrist.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.seolhwa.armyrist.timeplan.v3.data.DateAwareTimePlanRepository
import com.seolhwa.armyrist.timeplan.v3.domain.ActionCompletionState

class TimePlanActionAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val planId = intent.getStringExtra(TimePlanActionNotificationManager.EXTRA_PLAN_ID) ?: return
        val actionId = intent.getStringExtra(TimePlanActionNotificationManager.EXTRA_ACTION_ID) ?: return
        val repository = DateAwareTimePlanRepository(context.applicationContext)
        val plan = repository.getPlan(planId) ?: return
        val action = plan.actions.firstOrNull { it.id == actionId } ?: return

        if (
            action.completionState != ActionCompletionState.INCOMPLETE ||
            !action.notificationEnabled ||
            !TimePlanActionNotificationManager.notificationPermissionGranted(context)
        ) return

        TimePlanActionNotificationManager.createChannel(context)
        val notification = NotificationCompat.Builder(
            context,
            TimePlanActionNotificationManager.channelId()
        )
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(plan.title)
            .setContentText(action.content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(action.content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(
                TimePlanActionNotificationManager.openActionPendingIntent(
                    context,
                    plan.id,
                    action.id
                )
            )
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(("${plan.id}|${action.id}".hashCode() and 0x7fffffff), notification)
    }
}
