package com.seolhwa.armyrist.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.seolhwa.armyrist.ArmyristApplication
import com.seolhwa.armyrist.timeplan.v3.data.DateAwareTimePlanRepository

/** Explicit, private notification action; persists before dismissing the reminder. */
class TimePlanActionCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_COMPLETE) return
        val planId = intent.getStringExtra(TimePlanActionNotificationManager.EXTRA_PLAN_ID)
            ?.takeIf { it.isNotBlank() } ?: return
        val actionId = intent.getStringExtra(TimePlanActionNotificationManager.EXTRA_ACTION_ID)
            ?.takeIf { it.isNotBlank() } ?: return
        // Reuse the repository held by open screens, including on a cold process start.
        val repository = (context.applicationContext as? ArmyristApplication)
            ?.dateAwareTimePlanRepository ?: DateAwareTimePlanRepository(context)
        if (!repository.completeActionFromNotification(planId, actionId)) {
            Toast.makeText(context, "완료를 저장하지 못했습니다. 앱에서 실시사항을 확인해주세요.", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.seolhwa.armyrist.TIMEPLAN_ACTION_COMPLETE"
    }
}
