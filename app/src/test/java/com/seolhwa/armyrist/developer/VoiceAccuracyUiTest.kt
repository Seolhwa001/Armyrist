package com.seolhwa.armyrist.developer

import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.seolhwa.armyrist.*
import com.seolhwa.armyrist.voice.VoiceUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w360dp-h800dp",application=ArmyristApplication::class)
class VoiceAccuracyUiTest{
    @get:Rule val compose=createComposeRule()
    private val app get()=ApplicationProvider.getApplicationContext<ArmyristApplication>()
    @Test fun microphoneRawIsKeptWhileTruthCanBeCorrectedAndRecordExported(){
        compose.setContent{ArmyristTheme{VoiceAccuracyScreen({},speechInput={_,_,result,state,_,_->Button(onClick={state(VoiceUiState.LISTENING);result("생수 새 병")}){Text("테스트 인식")}})}}
        compose.onNodeWithText("테스트 인식").performScrollTo().performClick()
        compose.onNodeWithText("실제로 읽은 정답").performScrollTo().performTextReplacement("생수 세 병")
        compose.onNodeWithText("이 결과 저장").performScrollTo().performClick()
        compose.runOnIdle{
            val saved=VoiceAccuracyRepository(app).load().single();assertEquals("생수 새 병",saved.getString("rawTranscript"));assertEquals("생수 세 병",saved.getString("spokenTruth"));assertEquals("MICROPHONE",saved.getString("inputSource"))
        }
        compose.onNodeWithText("실제로 읽은 정답").performScrollTo().performTextReplacement("생수 새 병")
        compose.onNodeWithText("이 결과 저장").performScrollTo().performClick()
        compose.runOnIdle{val rows=VoiceAccuracyRepository(app).load();assertEquals(1,rows.size);assertEquals(0.0,rows.single().getDouble("characterErrorRate"),0.0)}
        compose.onNodeWithText("읽을 제시문").performScrollTo()
        screenshot("voice-accuracy-top.png")
        compose.onNodeWithText("테스트 자료 내보내기").performScrollTo().assertIsEnabled()
        screenshot("voice-accuracy.png")
        compose.onNodeWithText("다음 랜덤 제시문").performScrollTo().performClick()
        compose.onNodeWithText("실제 인식 원문 · 마이크").assertDoesNotExist()
    }
    @Test fun unsupportedMicrophoneCanSaveFailureWithoutFalseAccuracy(){
        compose.setContent{ArmyristTheme{VoiceAccuracyScreen({},speechInput={_,_,_,_,error,_->Button(onClick={error("미지원 기기")}){Text("실패 테스트")}})}}
        compose.onNodeWithText("실패 테스트").performScrollTo().performClick()
        compose.onNodeWithText("이 결과 저장").performScrollTo().performClick()
        compose.runOnIdle{val row=VoiceAccuracyRepository(app).load().single();assertEquals("ERROR",row.getString("outcome"));assertTrue(row.isNull("characterErrorRate"))}
    }
    private fun screenshot(name:String)=compose.runOnIdle{
        val view=android.view.inspector.WindowInspector.getGlobalWindowViews().last();val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888);view.draw(android.graphics.Canvas(bitmap))
        java.io.File("build/preview-screens/$name").also{it.parentFile!!.mkdirs()}.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
}
