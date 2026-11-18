package com.seolhwa.armyrist.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.seolhwa.armyrist.timeplan.v3.data.DateAwareTimePlanRepository
import com.seolhwa.armyrist.timeplan.v3.domain.ActionCompletionState
import com.seolhwa.armyrist.timeplan.v3.domain.ActionNotificationMode
import com.seolhwa.armyrist.timeplan.v3.domain.TimePlanDateDisplayMode
import java.time.format.DateTimeFormatter

class TimePlanActionAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra(TimePlanAlarmDiagnostics.EXTRA_TEST, false)) {
            TimePlanAlarmDiagnostics.record(context, "시험 알람 수신")
            TimePlanAlarmDiagnostics.showTestNotification(context)
            return
        }
        TimePlanAlarmDiagnostics.record(context, "실시사항 알람 수신")
        val planId = intent.getStringExtra(TimePlanActionNotificationManager.EXTRA_PLAN_ID) ?: return
        val actionId = intent.getStringExtra(TimePlanActionNotificationManager.EXTRA_ACTION_ID) ?: return
        val repository = DateAwareTimePlanRepository(context.applicationContext)
        val plan = repository.getPlan(planId) ?: return
        val action = com.seolhwa.armyrist.timeplan.v3.domain.UnifiedTimePlan.actions(plan).firstOrNull { it.id == actionId } ?: return

        if (
            action.scheduledDateTime > java.time.LocalDateTime.now() ||
            plan.dateDisplayMode == TimePlanDateDisplayMode.RELATIVE_D_DAY ||
            action.completionState != ActionCompletionState.INCOMPLETE ||
            action.notificationMode == com.seolhwa.armyrist.timeplan.v3.domain.ActionNotificationMode.NONE ||
            !TimePlanActionNotificationManager.notificationPermissionGranted(context)
        ) return

        TimePlanActionNotificationManager.createChannel(context)

        if (action.notificationMode == ActionNotificationMode.MUSIC) {
            try {
                TimePlanActionNotificationManager.startMusicAlarm(context, plan.id, action.id)
                return
            } catch (failure: IllegalStateException) {
                TimePlanAlarmDiagnostics.record(context, "음악 시작 제한 · 일반 알림으로 전환")
                android.util.Log.w("TimePlanAlarm", "Music service start restricted", failure)
            } catch (failure: SecurityException) {
                TimePlanAlarmDiagnostics.record(context, "음악 시작 권한 제한 · 일반 알림으로 전환")
                android.util.Log.w("TimePlanAlarm", "Music service permission rejected", failure)
            }
        }

        val scheduledText =
            action.scheduledDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
        val secondaryText = "${plan.title} · ${scheduledText} 예정"

        val notification = TaskReminderNotification.build(context,TimePlanActionNotificationManager.channelId(action.copy(notificationMode=ActionNotificationMode.SIMPLE)),action.content,secondaryText,
            TimePlanActionNotificationManager.openActionPendingIntent(context,plan.id,action.id),TimePlanActionNotificationManager.completeActionPendingIntent(context,plan.id,action.id))

        context.getSystemService(NotificationManager::class.java)
            .notify(("${plan.id}|${action.id}".hashCode() and 0x7fffffff), notification)
        TimePlanAlarmDiagnostics.record(context, "실시사항 알림 표시 요청")
    }
}
