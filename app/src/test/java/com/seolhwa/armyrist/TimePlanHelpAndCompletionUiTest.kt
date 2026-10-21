package com.seolhwa.armyrist

import android.content.Context
import android.content.IntentFilter
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.seolhwa.armyrist.notification.TimePlanActionCompleteReceiver
import com.seolhwa.armyrist.notification.TimePlanActionNotificationManager
import com.seolhwa.armyrist.timeplan.v3.data.DateAwareTimePlanRepository
import com.seolhwa.armyrist.timeplan.v3.domain.*
import com.seolhwa.armyrist.timeplan.v3.ui.TimePlanExecutionApp
import java.io.File
import java.time.LocalDateTime
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp", application = ArmyristApplication::class)
class TimePlanHelpAndCompletionUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var app: ArmyristApplication

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
        app.dateAwareTimePlanRepository.reloadFromPersistence()
    }

    @Test fun notificationImmediatelyUpdatesCircleInAlreadyOpenExecutionScreen() {
        val repo = app.dateAwareTimePlanRepository
        val plan = repo.createPlan(LocalDateTime.now().minusMinutes(30))!!
        org.junit.Assert.assertTrue(repo.commit(plan.copy(actions = listOf(TimePlanActionItem(
            id = "action", parentPointId = DateTimePlanRules.START_ID, content = "장비 점검",
            scheduledDateTime = LocalDateTime.now().minusMinutes(1), notificationMode = ActionNotificationMode.SIMPLE
        )))))
        compose.setContent { ArmyristTheme {
            TimePlanExecutionApp(plan.id, TimePlanExecutionActivity.MODE_EXECUTE, emptySet(), repo, app.coreSuiteRepository, {})
        } }
        compose.onNodeWithContentDescription("장비 점검 완료 상태").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "미완료"))
        compose.runOnIdle { TimePlanActionNotificationManager.completeActionPendingIntent(app, plan.id, "action").send() }
        compose.onNodeWithContentDescription("장비 점검 완료 상태").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "완료"))
        compose.onNodeWithText("전체 1 · 완료 1 · 미실시 0 · 완료율 100%").assertIsDisplayed()
        screenshot("notification-completed-circle.png")
    }

    @Test @Config(qualifiers = "w320dp-h480dp") fun upperRightHelpOpensCurrentScreenAndOtherToolGuides() {
        compose.setContent { ArmyristTheme {
            Column { ArmyristTopBar(title = "수행 모드", onLeading = {}) }
        } }
        compose.onNodeWithContentDescription("도움말").assertIsDisplayed().performClick()
        compose.onNodeWithText("시간계획 도움말").assertIsDisplayed()
        compose.onNodeWithText("알림에서 할 일 완료").performScrollTo().assertIsDisplayed()
        screenshot("timeplan-help.png")
        compose.onNodeWithText("전체 도움말").performClick()
        compose.onNodeWithText("핸디캡 상황").performScrollTo().performClick()
        compose.onNodeWithText("핸디캡 상황 도움말").assertIsDisplayed()
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithContentDescription("도움말").assertIsDisplayed()
    }

    private fun screenshot(name: String) = compose.runOnIdle {
        val view = android.view.inspector.WindowInspector.getGlobalWindowViews().last()
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        val output = File("build/preview-screens/$name").also { it.parentFile?.mkdirs() }
        output.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
