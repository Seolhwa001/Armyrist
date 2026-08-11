package com.seolhwa.armyrist.notification

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.seolhwa.armyrist.stage2.data.CoreSuiteRepository

class ChecklistBootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        if (
            intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent.action ==
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        ) {
            val repository =
                CoreSuiteRepository(context.applicationContext)

            ChecklistNotificationManager.reconcile(
                context.applicationContext,
                repository
            )
        }
    }
}
