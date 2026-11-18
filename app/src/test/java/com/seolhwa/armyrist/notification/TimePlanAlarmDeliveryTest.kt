package com.seolhwa.armyrist.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.seolhwa.armyrist.timeplan.v3.data.DateAwareTimePlanRepository
import com.seolhwa.armyrist.timeplan.v3.domain.*
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class TimePlanAlarmDeliveryTest {
    private lateinit var context: Context
    private lateinit var repo: DateAwareTimePlanRepository
    private lateinit var alarms: AlarmManager
    private lateinit var notifications: NotificationManager
    private lateinit var plan: DateAwareTimePlan
    private lateinit var action: TimePlanActionItem

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        alarms = context.getSystemService(AlarmManager::class.java)
        notifications = context.getSystemService(NotificationManager::class.java)
        repo = DateAwareTimePlanRepository(context)
        plan = repo.createPlan()!!
        action = TimePlanActionItem(id="reminder", content="보고", scheduledDateTime=LocalDateTime.now().plusMinutes(5), notificationMode=ActionNotificationMode.SIMPLE)
        assertTrue(repo.commit(plan.copy(actions=listOf(action))))
    }

    @Test fun reopeningAfterDueTimeKeepsUndeliveredAlarm() {
        val pending = shadowOf(alarms).scheduledAlarms.single().operation
        TimePlanActionNotificationManager.reconcile(context, repo, action.scheduledDateTime.plusMinutes(10))
        assertEquals(pending, shadowOf(alarms).scheduledAlarms.single().operation)
        assertNull(shadowOf(notifications).getNotification(TimePlanActionNotificationManager.notificationId(plan.id, action.id)))
        // Completing the item must still cancel the retained alarm.
        assertTrue(repo.completeActionFromNotification(plan.id, action.id))
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
    }

    @Test fun disabledOverdueAlarmIsCancelled() {
        TimePlanActionNotificationManager.reconcile(context, repo, action.scheduledDateTime.plusMinutes(1))
        assertTrue(repo.saveAction(plan.id, action.copy(notificationMode=ActionNotificationMode.NONE)))
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
    }

    @Test fun staleBroadcastDoesNotRingBeforeEditedTime() {
        TimePlanActionAlarmReceiver().onReceive(context, Intent().apply {
            putExtra(TimePlanActionNotificationManager.EXTRA_PLAN_ID, plan.id)
            putExtra(TimePlanActionNotificationManager.EXTRA_ACTION_ID, this@TimePlanAlarmDeliveryTest.action.id)
        })
        assertNull(shadowOf(notifications).getNotification(TimePlanActionNotificationManager.notificationId(plan.id,action.id)))
    }

    @Test fun overdueBroadcastStillDeliversAndCanBeCompleted() {
        assertTrue(repo.saveAction(plan.id,action.copy(scheduledDateTime=LocalDateTime.now().minusMinutes(1))))
        TimePlanActionAlarmReceiver().onReceive(context, Intent().apply {
            putExtra(TimePlanActionNotificationManager.EXTRA_PLAN_ID, plan.id)
            putExtra(TimePlanActionNotificationManager.EXTRA_ACTION_ID, this@TimePlanAlarmDeliveryTest.action.id)
        })
        assertNotNull(shadowOf(notifications).getNotification(TimePlanActionNotificationManager.notificationId(plan.id,action.id)))
    }
}
