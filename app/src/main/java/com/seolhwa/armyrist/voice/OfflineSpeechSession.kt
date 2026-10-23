package com.seolhwa.armyrist.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

internal interface SpeechEngine {fun listen(listener:RecognitionListener,intent:Intent);fun stop();fun destroy()}
internal interface SpeechEngineFactory {fun available():Boolean;fun create():SpeechEngine}
private class AndroidSpeechEngineFactory(private val context:Context):SpeechEngineFactory {
    override fun available()=Build.VERSION.SDK_INT>=31&&runCatching{SpeechRecognizer.isOnDeviceRecognitionAvailable(context)}.getOrDefault(false)
    override fun create():SpeechEngine {
        val recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        return object:SpeechEngine{
            override fun listen(listener:RecognitionListener,intent:Intent){recognizer.setRecognitionListener(listener);recognizer.startListening(intent)}
            override fun stop()=recognizer.stopListening()
            override fun destroy()=recognizer.destroy()
        }
    }
}
/** A user session spans engine cycles. Only terminal results enter the saved transcript. */
class OfflineSpeechSession internal constructor(private val factory:SpeechEngineFactory){
    constructor(context:Context):this(AndroidSpeechEngineFactory(context))
    private val handler=Handler(Looper.getMainLooper())
    private var engine:SpeechEngine?=null
    private var generation=0L
    private var active=false
    private var finishing=false
    private var retries=0
    private var partial=""
    private var hadGap=false
    private var tool=VoiceToolContext.GENERIC
    private val parts=mutableListOf<String>()
    private var state:((VoiceUiState)->Unit)?=null
    private var result:((String)->Unit)?=null
    private var error:((String)->Unit)?=null
    private var preview:((String)->Unit)?=null
    private var notice:((String)->Unit)?=null
    fun available()=factory.available()
    fun start(onState:(VoiceUiState)->Unit,onTranscript:(String)->Unit,onError:(String)->Unit,onPreview:(String)->Unit={},onNotice:(String)->Unit={},toolContext:VoiceToolContext=VoiceToolContext.GENERIC){
        destroy();state=onState;result=onTranscript;error=onError;preview=onPreview;notice=onNotice;tool=toolContext
        if(!available()){state?.invoke(VoiceUiState.UNAVAILABLE);error?.invoke("이 기기에서 오프라인 음성인식을 사용할 수 없습니다.");return}
        active=true;schedule(0)
    }
    private fun emitPreview(){preview?.invoke((parts+listOf(partial)).filter{it.isNotBlank()}.joinToString("\n"))}
    private fun schedule(delay:Long){
        if(!active||finishing)return
        val token=++generation;state?.invoke(VoiceUiState.PREPARING)
        handler.postDelayed({if(active&&!finishing&&token==generation)begin(token)},delay)
    }
    private fun begin(token:Long){
        val current=runCatching{factory.create()}.getOrElse{retry("음성인식을 시작하지 못했습니다.");return}
        engine=current;partial=""
        fun valid()=active&&engine===current&&generation==token
        val listener=object:RecognitionListener {
            override fun onReadyForSpeech(params:Bundle?){if(valid()&&!finishing)state?.invoke(VoiceUiState.LISTENING)}
            override fun onBeginningOfSpeech(){if(valid()&&!finishing)state?.invoke(VoiceUiState.LISTENING)}
            override fun onRmsChanged(rmsdB:Float){}
            override fun onBufferReceived(buffer:ByteArray?){}
            override fun onEndOfSpeech(){if(valid())state?.invoke(VoiceUiState.RECOGNIZING)}
            override fun onPartialResults(partialResults:Bundle?){if(valid()){partial=partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty();emitPreview()}}
            override fun onResults(results:Bundle?){
                if(!valid())return
                val text=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                if(text.isNotBlank()){parts+=text;retries=0}else if(partial.isNotBlank())hadGap=true
                partial="";release(current);emitPreview()
                if(finishing)complete()else schedule(RESTART_MS)
            }
            override fun onError(code:Int){
                if(!valid())return
                if(partial.isNotBlank())hadGap=true
                partial="";release(current);emitPreview()
                if(finishing){complete();return}
                when(code){SpeechRecognizer.ERROR_NO_MATCH,SpeechRecognizer.ERROR_SPEECH_TIMEOUT->schedule(RESTART_MS)
                    SpeechRecognizer.ERROR_CLIENT,SpeechRecognizer.ERROR_RECOGNIZER_BUSY->retry("음성인식 연결이 불안정합니다. 다시 시작해주세요.")
                    else->fail("음성인식이 중단되었습니다. (오류 $code)")}
            }
            override fun onEvent(eventType:Int,params:Bundle?){}
        }
        runCatching{current.listen(listener,recognitionIntent(tool))}.onFailure{if(valid()){release(current);retry("음성인식을 시작하지 못했습니다.")}}
    }
    private fun release(current:SpeechEngine){if(engine===current)engine=null;handler.post{runCatching{current.destroy()}}}
    private fun retry(message:String){retries++;if(retries>=3)fail(message)else schedule(300L*retries)}
    fun finish(){
        if(!active||finishing)return
        finishing=true;state?.invoke(VoiceUiState.RECOGNIZING)
        val current=engine
        if(current==null){generation++;complete();return}
        val token=generation
        handler.postDelayed({if(active&&finishing&&token==generation){hadGap=true;partial="";release(current);complete()}},FINISH_TIMEOUT_MS)
        runCatching{current.stop()}.onFailure{hadGap=true;partial="";release(current);complete()}
    }
    private fun complete(){
        if(!active)return
        active=false;finishing=false;generation++
        val text=parts.joinToString("\n")
        if(text.isBlank()){state?.invoke(VoiceUiState.ERROR);error?.invoke("확정된 인식 결과가 없습니다. 다시 말해주세요.")}
        else{state?.invoke(VoiceUiState.STRUCTURING);result?.invoke(text);if(hadGap)notice?.invoke("일부 발화가 확정되지 않았습니다. 인식된 내용에 빠진 항목이 없는지 확인하세요.")}
    }
    private fun fail(message:String){engine?.let(::release);if(parts.isNotEmpty()){hadGap=true;complete()}else{active=false;finishing=false;generation++;state?.invoke(VoiceUiState.ERROR);error?.invoke(message)}}
    fun cancel()=destroy()
    fun destroy(){active=false;finishing=false;generation++;engine?.let(::release);engine=null;parts.clear();partial="";retries=0;hadGap=false;state=null;result=null;error=null;preview=null;notice=null}
    companion object {
        internal const val RESTART_MS=80L
        internal const val FINISH_TIMEOUT_MS=8000L
        internal fun recognitionIntent(tool:VoiceToolContext)=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ko-KR");putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3)
            if(Build.VERSION.SDK_INT>=33)putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS,ArrayList(when(tool){
                VoiceToolContext.COUNTING->listOf("구급낭","무전기","전투식량","보조 배터리","수통")
                VoiceToolContext.CHECKLIST->listOf("구급낭","통신장비","보일러실","출입문 잠금","인원 확인")
                VoiceToolContext.TIME_PLAN->listOf("집결","인원 점검","장비 점검");else->emptyList()}))
        }
    }
}
