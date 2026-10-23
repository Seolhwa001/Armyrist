package com.seolhwa.armyrist.developer

import androidx.test.core.app.ApplicationProvider
import com.seolhwa.armyrist.ArmyristApplication
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=ArmyristApplication::class)
class VoiceAccuracyTest{
    private val date=LocalDate.of(2026,10,24)
    private fun sample(source:String="MICROPHONE",error:String?=null)=VoiceAccuracy.trial(ProbeTool.COUNTING,ProbeKind.SENTENCE,VoicePrompt("p","생수 세 병",date),"생수 세 병",if(error==null)"생수 새 병"else"",source,error,"생수, 3병","조용한 실내",1000,JSONObject().put("appVersion","0.6.82"))
    @Test fun metricsIgnoreFormattingButKeepRealRecognitionErrors(){
        assertEquals(0.0,VoiceAccuracy.cer("생수 세 병!","생수세병")!!,0.0)
        assertEquals(.25,VoiceAccuracy.cer("생수 세 병","생수 새 병")!!,0.0)
        assertEquals(2.0,VoiceAccuracy.cer("가","가나다")!!,0.0)
        assertNull(VoiceAccuracy.cer("!!!","가"))
        val summary=VoiceAccuracy.summary(listOf(sample(),sample("MANUAL"),sample(error="미지원")))
        assertEquals(1,summary.getInt("microphoneResults"));assertEquals(1,summary.getInt("failedAttempts"));assertEquals(1,summary.getInt("manualTrials"));assertEquals(.25,summary.getDouble("characterErrorRate"),0.0)
    }
    @Test fun everyToolProducesWordsAndSentencesWithoutRepeatingPrevious(){
        ProbeTool.entries.forEach{tool->ProbeKind.entries.forEach{kind->
            val first=VoiceAccuracy.next(tool,kind,date=date,random=Random(7));val second=VoiceAccuracy.next(tool,kind,first.text,date,Random(7))
            assertNotEquals(first.text,second.text);assertEquals(date,second.referenceDate);assertTrue(first.text.isNotBlank())
        }}
    }
    @Test fun savedCorrectionsReplaceOneTrialAndExportKeepsRawAndTruthSeparately(){
        val app=ApplicationProvider.getApplicationContext<ArmyristApplication>();val repo=VoiceAccuracyRepository(app)
        val row=sample();repo.save(row);repo.save(VoiceAccuracy.trial(ProbeTool.COUNTING,ProbeKind.SENTENCE,VoicePrompt("p","생수 세 병",date),"생수 새 병","생수 새 병","MICROPHONE",null,"생수, 3병","조용한 실내",1000,JSONObject()).put("id",row.getString("id")))
        val reopened=VoiceAccuracyRepository(app);assertEquals(1,reopened.load().size)
        val exported=JSONObject(reopened.export());assertFalse(exported.getBoolean("audioIncluded"));assertEquals("killomris_voice_accuracy",exported.getString("datasetType"))
        val record=exported.getJSONArray("trials").getJSONObject(0)
        assertEquals("생수 새 병",record.getString("rawTranscript"));assertEquals("생수 새 병",record.getString("spokenTruth"))
        assertEquals("생수, 3병",record.getString("userExpectedMeaning"));assertTrue(record.has("recognizedParserOutput"));assertTrue(record.has("truthParserOutput"))
    }
    @Test fun corruptHistoryIsNeverOverwrittenByNewTrial(){
        val app=ApplicationProvider.getApplicationContext<ArmyristApplication>();val file=java.io.File(app.filesDir,"voice_accuracy_v1.json");file.writeText("broken")
        assertTrue(runCatching{VoiceAccuracyRepository(app).save(sample())}.isFailure)
        assertEquals("broken",file.readText())
    }
    @Test fun actualToolParsersRetainQuantityAndTimeDiagnostics(){
        val count=VoiceAccuracy.structured(ProbeTool.COUNTING,"생수 세 병",date).getJSONObject(0)
        assertEquals(3,count.getInt("quantity"));assertEquals("병",count.getString("unit"))
        assertEquals(2,VoiceAccuracy.structured(ProbeTool.CHECKLIST,"인원 확인, 차량 점검",date).length())
        val time=VoiceAccuracy.structured(ProbeTool.TIME_PLAN,"24일 9시 집결",date).getJSONObject(0)
        assertEquals("2026-10-24T09:00",time.getString("dateTime"))
        assertTrue(VoiceAccuracy.structured(ProbeTool.TIME_PLAN,"9시 집결",date).getJSONObject(0).isNull("dateTime"))
    }
}
