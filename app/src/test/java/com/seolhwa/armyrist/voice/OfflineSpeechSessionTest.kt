package com.seolhwa.armyrist.voice
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class OfflineSpeechSessionTest {
    private class Engine:SpeechEngine{
        lateinit var listener:RecognitionListener;var stopCount=0
        override fun listen(listener:RecognitionListener,intent:Intent){this.listener=listener}
        override fun stop(){stopCount++};override fun destroy(){}
        fun result(text:String)=listener.onResults(bundle(text))
        fun partial(text:String)=listener.onPartialResults(bundle(text))
        companion object{fun bundle(s:String)=Bundle().apply{putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION,arrayListOf(s))}}
    }
    private class Factory:SpeechEngineFactory{
        val all=mutableListOf<Engine>();override fun available()=true;override fun create()=Engine().also{all+=it}
    }
    private class Fixture{
        val factory=Factory();val session=OfflineSpeechSession(factory);val results=mutableListOf<String>();val notices=mutableListOf<String>();val errors=mutableListOf<String>()
        init{session.start({},results::add,errors::add,onNotice=notices::add);tick(0)}
        val engine get()=factory.all.last()
    }
    companion object{private fun tick(ms:Long)=shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))}
    @Test fun finishWaitsForTerminalResultWithoutPromotingPartial(){
        val f=Fixture();f.engine.partial("생수 세");f.session.finish();f.session.finish()
        assertEquals(1,f.engine.stopCount);assertTrue(f.results.isEmpty());f.engine.result("생수 세 병")
        tick(9000);assertEquals(listOf("생수 세 병"),f.results)
    }
    @Test fun errorAndTimeoutKeepConfirmedResultsAndWarn(){
        val f=Fixture();f.engine.result("차량 점검");tick(80);f.engine.partial("장비 점검");f.session.finish();tick(8000)
        assertEquals(listOf("차량 점검"),f.results);assertEquals(1,f.notices.size)
        val g=Fixture();g.engine.result("인원 확인");tick(80);g.engine.listener.onError(SpeechRecognizer.ERROR_AUDIO)
        assertEquals(listOf("인원 확인"),g.results);assertEquals(1,g.notices.size)
    }
    @Test fun lateCallbacksAndCancelCannotRestartOrAppend(){
        val f=Fixture();val old=f.engine;old.result("인원 확인");tick(80);old.result("지연 결과");f.engine.result("인원 확인");f.session.finish()
        assertEquals(listOf("인원 확인\n인원 확인"),f.results)
        val g=Fixture();val engine=g.engine;g.session.cancel();engine.result("취소 결과");tick(9000);assertTrue(g.results.isEmpty());assertEquals(1,g.factory.all.size)
    }
    @Test fun busyRetriesAreBounded(){val f=Fixture();repeat(3){f.engine.listener.onError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY);tick(1000)};assertEquals(3,f.factory.all.size);assertEquals(1,f.errors.size)}
}
