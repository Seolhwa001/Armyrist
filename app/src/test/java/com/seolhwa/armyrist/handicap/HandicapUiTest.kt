package com.seolhwa.armyrist.handicap

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.io.File
import org.robolectric.annotation.GraphicsMode
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.seolhwa.armyrist.ArmyristTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp", application = Application::class)
class HandicapUiTest {
    @get:Rule val compose = createComposeRule()
    @Before fun clear() {
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("killomris_handicap_v1", 0).edit().clear().commit()
    }
    @Test fun normalPhoneShowsAllCategoriesAndConfirmWithoutVerticalScroll() {
        val store = HandicapStore(ApplicationProvider.getApplicationContext<Context>())
        store.saveConfig(HandicapConfig(issuer = UnitContact("상황부여 제대")))
        store.saveDraft(SituationDraft(HandicapKind.BLACK, UnitContact("상황부여 제대"), listOf("불침번"), "2026.10.21 09:00", listOf("위병소"), listOf("화재"), listOf("발견", "보고")))
        compose.setContent { ArmyristTheme { HandicapRoot {} } }
        listOf("부여제대", "핸디캡", "누가", "언제", "어디서", "무엇을", "어떻게", "확인 · 문장 정리").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        listOf("발견", "조치", "보고").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.runOnIdle {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single()
            val view = activity.window.decorView
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            val output = File("build/preview-screens/handicap-360x640.png").also { it.parentFile?.mkdirs() }
            output.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test @Config(qualifiers = "w320dp-h480dp") fun smallPhoneKeepsHowAndConfirmVisible() {
        compose.setContent { ArmyristTheme { HandicapRoot {} } }
        listOf("부여제대", "누가", "언제", "어디서", "무엇을", "어떻게", "확인 · 문장 정리").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }
    @Test fun unorderedTapsProduceBlackUpwardReportWithHow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = HandicapConfig(issuer = UnitContact("부여제대"), upper = listOf(UnitContact("상급 A")) + List(3) { UnitContact() })
        HandicapStore(context).saveConfig(config)
        compose.setContent { ArmyristTheme { HandicapRoot {} } }
        listOf("보고", "위병소", "화재", "불침번", "블랙 ↑ 상급", "현재시간", "확인 · 문장 정리").forEach { compose.onNodeWithText(it).performClick() }
        compose.onNodeWithText("상급 A").assertIsDisplayed()
        compose.onNodeWithText("블랙 ↑ 1차 상급제대").assertIsDisplayed()
        compose.onNodeWithText("수행사항: 보고.", substring = true).assertIsDisplayed()
    }
    @Test fun whiteWithoutLowerReturnsToIssuer() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        HandicapStore(context).saveConfig(HandicapConfig(issuer = UnitContact("원 부여제대")))
        compose.setContent { ArmyristTheme { HandicapRoot {} } }
        listOf("발견", "위병소", "화재", "불침번", "화이트 ↓ 하급", "현재시간", "확인 · 문장 정리").forEach { compose.onNodeWithText(it).performClick() }
        compose.onNodeWithText("1차 하급 미등록 → 상황부여 제대").assertIsDisplayed()
    }
    @Test fun resultDialogShowsReadableReportAndPinnedActions() {
        compose.setContent { ArmyristTheme {
            SituationResultDialog(HandicapKind.BLACK, "2026.10.21 09:00, 위병소에서 불침번이 화재 상황발생. 수행사항: 발견, 보고.", UnitContact("1차 상급제대", "010-1234-5678"), "블랙 ↑ 1차 상급제대", {}, {}, {})
        } }
        listOf("상황 정리", "정리된 문장", "연락할 제대", "1차 상급제대", "010-1234-5678", "내용 복사", "전화 앱 열기", "돌아가서 수정").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.runOnIdle {
            val view = android.view.inspector.WindowInspector.getGlobalWindowViews().last()
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            val output = File("build/preview-screens/handicap-result-360x640.png").also { it.parentFile?.mkdirs() }
            output.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test @Config(qualifiers = "w320dp-h480dp") fun longReportKeepsActionsAvailableAndCanScrollToRecipient() {
        var copied = false
        compose.setContent { ArmyristTheme {
            SituationResultDialog(HandicapKind.WHITE, "상황 발생에 대한 상세 설명입니다. ".repeat(40), UnitContact("상황부여 제대"), "1차 하급 미등록 → 상황부여 제대", {}, { copied = true }, {})
        } }
        compose.onNodeWithText("내용 복사").assertIsDisplayed().performClick()
        compose.runOnIdle { org.junit.Assert.assertTrue(copied) }
        compose.onNodeWithText("전화 앱 열기").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("돌아가서 수정").assertIsDisplayed()
        compose.onNodeWithText("상황부여 제대").performScrollTo().assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w412dp-h820dp") fun tallPhoneDoesNotStretchHowButtons() {
        compose.setContent { ArmyristTheme { HandicapRoot {} } }
        val how = compose.onNodeWithText("발견").fetchSemanticsNode().boundsInRoot.height
        val time = compose.onNodeWithText("현재시간").fetchSemanticsNode().boundsInRoot.height
        org.junit.Assert.assertTrue("How controls must keep a compact height on tall phones", how <= time * 1.3f)
        listOf("누가", "언제", "어디서", "무엇을", "어떻게", "확인 · 문장 정리").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.runOnIdle {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single()
            val view = activity.window.decorView
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            val output = File("build/preview-screens/handicap-412x820.png").also { it.parentFile?.mkdirs() }
            output.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

}
