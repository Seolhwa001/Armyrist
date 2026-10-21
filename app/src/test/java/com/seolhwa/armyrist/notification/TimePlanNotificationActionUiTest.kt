package com.seolhwa.armyrist.notification

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.IntentFilter
import android.content.ComponentName
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.seolhwa.armyrist.ArmyristApplication
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
@Config(sdk = [35], application = ArmyristApplication::class)
class TimePlanNotificationActionUiTest {
    private lateinit var app: ArmyristApplication
    private lateinit var repo: DateAwareTimePlanRepository
    private lateinit var manager: NotificationManager

    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        val receiverInfo = app.packageManager.getReceiverInfo(ComponentName(app, TimePlanActionCompleteReceiver::class.java), 0)
        org.junit.Assert.assertFalse(receiverInfo.exported)
        // Robolectric does not auto-dispatch to manifest receivers without filters.
        // Register the same private receiver to exercise the real PendingIntent route.
        app.registerReceiver(TimePlanActionCompleteReceiver(), IntentFilter(TimePlanActionCompleteReceiver.ACTION_COMPLETE).apply {
            addDataScheme("killomris")
        }, Context.RECEIVER_NOT_EXPORTED)
        app.getSharedPreferences(DateAwareTimePlanRepository.PREFS, 0).edit().clear().commit()
        repo = app.dateAwareTimePlanRepository
        repo.reloadFromPersistence()
        manager = app.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun plan(vararg ids: String): DateAwareTimePlan {
        val plan = repo.createPlan(LocalDateTime.now().minusMinutes(30))!!
        val value = plan.copy(actions = ids.mapIndexed { i, id -> TimePlanActionItem(
            id = id, parentPointId = DateTimePlanRules.START_ID, content = "실시사항 $id",
            scheduledDateTime = LocalDateTime.now().minusMinutes(1), order = i,
            notificationMode = ActionNotificationMode.SIMPLE
        ) })
        assertTrue(repo.commit(value))
        return repo.getPlan(plan.id)!!
    }

    private fun show(plan: DateAwareTimePlan, id: String) {
        TimePlanActionAlarmReceiver().onReceive(app, Intent().apply {
            putExtra(TimePlanActionNotificationManager.EXTRA_PLAN_ID, plan.id)
            putExtra(TimePlanActionNotificationManager.EXTRA_ACTION_ID, id)
        })
    }

    @Test fun simpleNotificationActionPersistsAndDismissesOnlyItsReminder() {
        val plan = plan("first", "second")
        show(plan, "first"); show(plan, "second")
        val id = TimePlanActionNotificationManager.notificationId(plan.id, "first")
        val notification = shadowOf(manager).getNotification(id)
        val action = notification.actions.single()
        assertEquals("할 일 완료", action.title.toString())
        action.actionIntent.send()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(ActionCompletionState.COMPLETE, repo.getPlan(plan.id)!!.actions[0].completionState)
        assertEquals(ActionCompletionState.INCOMPLETE, repo.getPlan(plan.id)!!.actions[1].completionState)
        assertNull(shadowOf(manager).getNotification(id))
        assertNotNull(shadowOf(manager).getNotification(TimePlanActionNotificationManager.notificationId(plan.id, "second")))
        assertEquals(ActionCompletionState.COMPLETE, DateAwareTimePlanRepository(app).getPlan(plan.id)!!.actions[0].completionState)
        action.actionIntent.send()
        assertEquals(ActionCompletionState.COMPLETE, repo.getPlan(plan.id)!!.actions[0].completionState)
    }

    @Test fun actionIdentitySurvivesHashCollisions() {
        val plan = plan("Aa", "BB")
        assertEquals("Aa".hashCode(), "BB".hashCode())
        val first = TimePlanActionNotificationManager.completeActionPendingIntent(app, plan.id, "Aa")
        val second = TimePlanActionNotificationManager.completeActionPendingIntent(app, plan.id, "BB")
        assertNotEquals(first, second)
        first.send()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(ActionCompletionState.COMPLETE, ActionCompletionState.INCOMPLETE), repo.getPlan(plan.id)!!.actions.map { it.completionState })
        second.send()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(repo.getPlan(plan.id)!!.actions.all { it.completionState == ActionCompletionState.COMPLETE })
    }

    @Test fun staleRepositoryDoesNotOverwriteLaterChangesOrOtherPlans() {
        val plan = plan("a")
        val other = plan("a")
        val fresh = DateAwareTimePlanRepository(app)
        assertTrue(fresh.commit(fresh.getPlan(plan.id)!!.copy(title = "나중에 수정된 계획", memo = "보존할 메모")))
        TimePlanActionNotificationManager.completeActionPendingIntent(app, plan.id, "a").send()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("나중에 수정된 계획", repo.getPlan(plan.id)!!.title)
        assertEquals("보존할 메모", repo.getPlan(plan.id)!!.memo)
        assertEquals(ActionCompletionState.INCOMPLETE, repo.getPlan(other.id)!!.actions.single().completionState)
    }

    @Test fun deletedTargetAndMalformedIntentDoNotRecreateAnything() {
        val plan = plan("a")
        val pending = TimePlanActionNotificationManager.completeActionPendingIntent(app, plan.id, "a")
        repo.delete(plan.id)
        pending.send()
        TimePlanActionCompleteReceiver().onReceive(app, Intent(TimePlanActionCompleteReceiver.ACTION_COMPLETE))
        assertNull(repo.getPlan(plan.id))
        assertTrue(DateAwareTimePlanRepository(app).getPlans().isEmpty())
    }

    @Test fun corruptStorageKeepsReminderAndOriginalBytes() {
        val plan = plan("a")
        show(plan, "a")
        val prefs = app.getSharedPreferences(DateAwareTimePlanRepository.PREFS, 0)
        prefs.edit().putString(DateAwareTimePlanRepository.KEY, "broken-data").commit()
        TimePlanActionNotificationManager.completeActionPendingIntent(app, plan.id, "a").send()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("broken-data", prefs.getString(DateAwareTimePlanRepository.KEY, null))
        assertNotNull(shadowOf(manager).getNotification(TimePlanActionNotificationManager.notificationId(plan.id, "a")))
        assertEquals(ActionCompletionState.INCOMPLETE, repo.getPlan(plan.id)!!.actions.single().completionState)
    }

    @Test fun musicHasSeparateCompleteAndStopActions() {
        val plan = plan("a")
        TimePlanActionNotificationManager.createChannel(app)
        val notification = TimePlanMusicAlarmService.buildNotification(app, plan, plan.actions.single().copy(notificationMode = ActionNotificationMode.MUSIC))
        assertEquals(listOf("할 일 완료", "알림 중지"), notification.actions.map { it.title.toString() })
        notification.actions[1].actionIntent.send()
        assertEquals(ActionCompletionState.INCOMPLETE, repo.getPlan(plan.id)!!.actions.single().completionState)
        notification.actions[0].actionIntent.send()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(ActionCompletionState.COMPLETE, repo.getPlan(plan.id)!!.actions.single().completionState)
    }
}
