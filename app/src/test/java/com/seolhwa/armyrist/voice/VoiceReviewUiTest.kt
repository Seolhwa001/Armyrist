package com.seolhwa.armyrist.voice
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.seolhwa.armyrist.ArmyristTheme
import com.seolhwa.armyrist.ArmyristApplication
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
class VoiceReviewUiTest{
    @get:Rule val compose=createComposeRule()
    @Test fun reviewKeepsOriginalUntilExplicitApply(){
        var applied:List<CountingVoiceDraft>?=null;val drafts=KoreanVoiceStructurer.counting("무전기 스물한데")
        compose.setContent{ArmyristTheme{CountingVoiceReviewDialog(drafts,{},{applied=it})}}
        compose.onNodeWithText("! 확인 필요").assertExists()
        compose.onNodeWithText("인식된 말 보기 ▼").performScrollTo().performClick()
        compose.onNodeWithText("인식된 말: \"무전기 스물한데\"").assertExists();assertNull(applied)
        compose.onNodeWithText("확인 후 적용").performClick();compose.runOnIdle{assertEquals(21,applied!!.single().quantity)}
    }
    @Test fun liveTranscriptAndStopRemainVisible(){
        var finished=false
        compose.setContent{ArmyristTheme{VoiceRecordingScreen(VoiceToolContext.CHECKLIST,null,"차량 점검\n인원 확인",VoiceUiState.LISTENING,{finished=true},{})}}
        compose.onNodeWithText("차량 점검\n인원 확인").assertExists();compose.onNodeWithText("■  입력 종료").performClick();compose.runOnIdle{assertTrue(finished)}
    }
}
