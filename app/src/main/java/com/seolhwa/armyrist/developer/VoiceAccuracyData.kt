package com.seolhwa.armyrist.developer

import android.content.Context
import android.util.AtomicFile
import com.seolhwa.armyrist.voice.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

enum class ProbeTool(val label:String){COUNTING("실셈"),CHECKLIST("체크리스트"),TIME_PLAN("시간계획")}
enum class ProbeKind(val label:String){WORD("단어"),SENTENCE("문장")}
data class VoicePrompt(val id:String,val text:String,val referenceDate:LocalDate)

object VoiceAccuracy {
    private val words=mapOf(
        ProbeTool.COUNTING to listOf("생수","전투식량","건전지","손전등","무전기","구급낭","보조 배터리","수통","스물네 병","열세 개","서른두 명","마흔여섯 개"),
        ProbeTool.CHECKLIST to listOf("통신장비","차량 상태","생활관","보일러실","위병소","병영식당","응급장비","잠금장치","인원 확인","장비 점검","소화기 확인","출입문 잠금"),
        ProbeTool.TIME_PLAN to listOf("오전","오후","집결","출발","인원 점검","교육 준비","이동","휴식","영시 삼십분","아홉시 사십분","오후 세시","밤 열한시"))
    fun next(tool:ProbeTool,kind:ProbeKind,previous:String?=null,date:LocalDate=LocalDate.now(),random:Random=Random.Default):VoicePrompt{
        val candidates=if(kind==ProbeKind.WORD)words.getValue(tool) else when(tool){
            ProbeTool.COUNTING->listOf("생수 스물네 병, 전투식량 열세 개, 건전지 여섯 개","무전기 ${random.nextInt(2,30)}대, 손전등 ${random.nextInt(2,30)}개","수통 여덟 개 그리고 보조 배터리 열두 개","구급낭 세 개, 생수 마흔여섯 병","생수 스물네 병 전투식량 열세 개 건전지 여섯 개")
            ProbeTool.CHECKLIST->listOf("차량 상태 확인, 통신장비 확인, 인원 확인","생활관 점검 그리고 소화기 확인","보일러실 확인 출입문 잠금 장비 점검","${words.getValue(tool).take(8).random(random)} 확인, 응급장비 준비","손전등 점검, 무전기 점검, 구급낭 준비")
            ProbeTool.TIME_PLAN->listOf("오전 아홉시 집결, 아홉시 사십분 출발","오후 두시 삼십분 교육, 오후 다섯시 삼십분 종료","${date.monthValue}월 ${date.dayOfMonth}일 오전 여덟시 출발, 오후 세시 복귀","오전 ${random.nextInt(6,12)}시 인원 점검, 오후 ${random.nextInt(1,7)}시 장비 점검","밤 열한시 이동, 영시 삼십분 집결")
        }
        val text=candidates.filterNot{it==previous}.ifEmpty{candidates}.random(random)
        return VoicePrompt(UUID.randomUUID().toString(),text,date)
    }
    fun normalized(text:String)=text.lowercase(Locale.ROOT).filter{it.isLetterOrDigit()}
    fun distance(a:String,b:String):Int{
        var row=IntArray(b.length+1){it}
        a.forEachIndexed{i,c->val next=IntArray(b.length+1);next[0]=i+1
            b.forEachIndexed{j,d->next[j+1]=minOf(next[j]+1,row[j+1]+1,row[j]+if(c==d)0 else 1)};row=next}
        return row.last()
    }
    fun cer(truth:String,raw:String):Double?=normalized(truth).takeIf{it.isNotEmpty()}?.let{distance(it,normalized(raw)).toDouble()/it.length}
    fun numberAwareCer(truth:String,raw:String)=cer(KoreanVoiceStructurer.numberAwareComparison(truth),KoreanVoiceStructurer.numberAwareComparison(raw))
    /** Parser snapshots are diagnostic outputs of this version, not semantic ground truth. */
    fun structured(tool:ProbeTool,text:String,date:LocalDate):JSONArray=JSONArray().apply{
        when(tool){
            ProbeTool.COUNTING->KoreanVoiceStructurer.counting(text).forEach{d->put(JSONObject().put("name",d.name).put("quantity",d.quantity?:JSONObject.NULL).put("unit",d.unit?:JSONObject.NULL).put("state",d.state.name).put("reviewReason",d.reviewReason))}
            ProbeTool.CHECKLIST->KoreanVoiceStructurer.checklist(text).forEach{d->put(JSONObject().put("name",d.name).put("scheduledTimeMinutes",d.scheduledTimeMinutes?:JSONObject.NULL).put("state",d.state.name).put("reviewReason",d.reviewReason))}
            ProbeTool.TIME_PLAN->KoreanVoiceStructurer.timePlan(text,date).forEach{d->put(JSONObject().put("name",d.name).put("dateTime",d.dateTime?.toString()?:JSONObject.NULL).put("rangeEnd",d.rangeEnd?.toString()?:JSONObject.NULL).put("state",d.state.name).put("reviewReason",d.reviewReason))}
        }
    }
    fun trial(tool:ProbeTool,kind:ProbeKind,prompt:VoicePrompt,truth:String,raw:String,source:String,error:String?,expectedMeaning:String,note:String,elapsedMs:Long?,environment:JSONObject):JSONObject{
        require(truth.isNotBlank());require(source in listOf("MICROPHONE","MANUAL"));require(raw.isNotBlank()||error!=null)
        val eligible=source=="MICROPHONE"&&error==null
        return JSONObject().put("id",UUID.randomUUID().toString()).put("createdAt",java.time.Instant.now().toString())
            .put("tool",tool.name).put("kind",kind.name).put("promptId",prompt.id).put("prompt",prompt.text).put("referenceDate",prompt.referenceDate.toString())
            .put("spokenTruth",truth.trim()).put("rawTranscript",raw).put("inputSource",source).put("outcome",if(error==null)"RESULT"else"ERROR").put("error",error?:JSONObject.NULL)
            .put("characterErrorRate",if(eligible)cer(truth,raw)?:JSONObject.NULL else JSONObject.NULL)
            .put("numberAwareCharacterErrorRate",if(eligible)numberAwareCer(truth,raw)?:JSONObject.NULL else JSONObject.NULL)
            .put("normalizedExactMatch",if(eligible)normalized(truth)==normalized(raw)else JSONObject.NULL)
            .put("elapsedMs",elapsedMs?:JSONObject.NULL).put("environment",environment)
            .put("recognizedParserOutput",structured(tool,raw,prompt.referenceDate)).put("truthParserOutput",structured(tool,truth,prompt.referenceDate))
            .put("userExpectedMeaning",expectedMeaning.trim()).put("note",note.trim())
    }
    fun summary(items:List<JSONObject>):JSONObject{
        val valid=items.filter{it.optString("inputSource")=="MICROPHONE"&&it.optString("outcome")=="RESULT"}
        val chars=valid.sumOf{normalized(it.getString("spokenTruth")).length}
        val errors=valid.sumOf{distance(normalized(it.getString("spokenTruth")),normalized(it.getString("rawTranscript")))}
        val numberChars=valid.sumOf{normalized(KoreanVoiceStructurer.numberAwareComparison(it.getString("spokenTruth"))).length}
        val numberErrors=valid.sumOf{distance(normalized(KoreanVoiceStructurer.numberAwareComparison(it.getString("spokenTruth"))),normalized(KoreanVoiceStructurer.numberAwareComparison(it.getString("rawTranscript"))))}
        return JSONObject().put("numberAwareCharacterErrorRate",if(numberChars==0)JSONObject.NULL else numberErrors.toDouble()/numberChars).put("trials",items.size).put("microphoneResults",valid.size).put("failedAttempts",items.count{it.optString("outcome")=="ERROR"})
            .put("manualTrials",items.count{it.optString("inputSource")=="MANUAL"}).put("normalizedExactMatches",valid.count{it.optBoolean("normalizedExactMatch")})
            .put("characterErrorRate",if(chars==0)JSONObject.NULL else errors.toDouble()/chars)
    }
}

class VoiceAccuracyRepository(context:Context){
    private val file=AtomicFile(File(context.filesDir,"voice_accuracy_v1.json"))
    @Synchronized fun load():List<JSONObject>{
        if(!file.baseFile.exists()&&!File(file.baseFile.path+".bak").exists())return emptyList()
        val root=JSONObject(file.openRead().bufferedReader().use{it.readText()});require(root.getInt("schemaVersion")==1)
        val a=root.getJSONArray("trials");return (0 until a.length()).map{a.getJSONObject(it)}
    }
    @Synchronized fun save(trial:JSONObject){
        val all=load();val old=all.find{it.getString("id")==trial.getString("id")}
        if(old!=null)trial.put("createdAt",old.getString("createdAt"))
        val updated=if(old==null)all+trial else all.map{if(it.getString("id")==trial.getString("id"))trial else it}
        val bytes=export(updated).toByteArray(Charsets.UTF_8)
        val stream=file.startWrite()
        try{stream.write(bytes);file.finishWrite(stream)}catch(e:Exception){file.failWrite(stream);throw e}
    }
    fun export(items:List<JSONObject> = load()):String=JSONObject().put("schemaVersion",1).put("datasetType","killomris_voice_accuracy")
        .put("exportedAt",java.time.Instant.now().toString()).put("language","ko-KR").put("audioIncluded",false)
        .put("metricsDefinition","CER: Unicode letters/digits only; whitespace and punctuation ignored; spoken-number spelling differences remain. Manual input and failed attempts excluded from recognition metrics. Number-aware CER additionally normalizes known number+unit expressions; it is not semantic accuracy. Parser snapshots are diagnostic, not human-verified expected structures.")
        .put("summary",VoiceAccuracy.summary(items)).put("byTool",JSONObject().apply{ProbeTool.entries.forEach{t->put(t.name,VoiceAccuracy.summary(items.filter{it.optString("tool")==t.name}))}})
        .put("trials",JSONArray(items)).toString(2)
}
