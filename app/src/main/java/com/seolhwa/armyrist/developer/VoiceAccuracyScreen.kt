package com.seolhwa.armyrist.developer

import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seolhwa.armyrist.*
import com.seolhwa.armyrist.voice.*
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
internal fun VoiceAccuracyScreen(onBack:()->Unit,
    speechInput:@Composable (ProbeTool,String,(String)->Unit,(VoiceUiState)->Unit,(String)->Unit,(String)->Unit)->Unit={probe,prompt,result,state,error,notice->
        OfflineVoiceButton(modifier=Modifier.fillMaxWidth(),toolContext=VoiceToolContext.valueOf(probe.name),onTranscript=result,onStateChanged=state,onMessage=error,readingPrompt=prompt,onNotice=notice)
    }
){
    val context=LocalContext.current;val repo=remember{VoiceAccuracyRepository(context)}
    var loaded by remember{mutableStateOf(runCatching{repo.load()})}
    var toolName by rememberSaveable{mutableStateOf(ProbeTool.COUNTING.name)}
    var kindName by rememberSaveable{mutableStateOf(ProbeKind.SENTENCE.name)}
    val tool=ProbeTool.valueOf(toolName);val kind=ProbeKind.valueOf(kindName)
    var promptId by rememberSaveable{mutableStateOf("")};var promptText by rememberSaveable{mutableStateOf("")};var reference by rememberSaveable{mutableStateOf(LocalDate.now().toString())}
    var truth by rememberSaveable{mutableStateOf("")};var raw by rememberSaveable{mutableStateOf("")};var error by rememberSaveable{mutableStateOf<String?>(null)}
    var source by rememberSaveable{mutableStateOf("MICROPHONE")};var expected by rememberSaveable{mutableStateOf("")};var note by rememberSaveable{mutableStateOf("")}
    var trialId by rememberSaveable{mutableStateOf(java.util.UUID.randomUUID().toString())}
    var saved by rememberSaveable{mutableStateOf(false)};var manual by rememberSaveable{mutableStateOf(false)}
    var started by remember{mutableLongStateOf(0L)};var elapsed by rememberSaveable{mutableStateOf<Long?>(null)}
    val prompt=VoicePrompt(promptId,promptText,LocalDate.parse(reference))
    fun next(){trialId=java.util.UUID.randomUUID().toString();val p=VoiceAccuracy.next(tool,kind,promptText);promptId=p.id;promptText=p.text;reference=p.referenceDate.toString();truth=p.text;raw="";error=null;expected="";note="";saved=false;manual=false;source="MICROPHONE";started=0;elapsed=null}
    LaunchedEffect(tool,kind){if(promptText.isEmpty())next()}
    fun message(value:String){Toast.makeText(context,value,Toast.LENGTH_LONG).show()}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->
        if(uri!=null)runCatching{
            val bytes=repo.export().toByteArray(Charsets.UTF_8)
            checkNotNull(context.contentResolver.openOutputStream(uri,"wt")).use{it.write(bytes)}
        }.onSuccess{message("테스트 자료를 저장했습니다. 이 JSON 파일을 ChatGPT 대화에 첨부해주세요.")}.onFailure{message("내보내기 실패: ${it.message}")}
    }
    BackHandler(onBack=onBack)
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()){
        ArmyristTopBar(title="음성 정확도 테스트",subtitle="개발자 · 데이터 수집",onLeading=onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text("제시문 읽기 → 인식 결과 확인 → 실제 읽은 정답 수정 → 저장 → JSON 내보내기",fontWeight=FontWeight.Bold)
            Text("녹음 파일은 저장하지 않습니다. 기록은 기기에만 저장되며 자동 전송되지 않습니다. 이 기능이 음성 엔진을 직접 학습시키지는 않습니다. 올려주신 자료로 인식 오류와 도구별 해석 규칙을 개선합니다.",style=MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                ProbeTool.entries.forEach{candidate->FilterChip(selected=tool==candidate,onClick={toolName=candidate.name;promptText="";raw="";error=null;saved=false},label={Text(candidate.label)})}
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                ProbeKind.entries.forEach{candidate->FilterChip(selected=kind==candidate,onClick={kindName=candidate.name;promptText="";raw="";error=null;saved=false},label={Text(candidate.label)})}
            }
            Surface(shape=ArmyristPanelShape,color=ArmyristColors.SecondaryControl){Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text("읽을 제시문",style=MaterialTheme.typography.labelMedium,color=ArmyristColors.PrimaryControl)
                Text(promptText,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            }}
            key(promptId){speechInput(tool,promptText,{text->raw=text;source="MICROPHONE";manual=false;error=null;saved=false;elapsed=started.takeIf{it>0}?.let{SystemClock.elapsedRealtime()-it};started=0},{state->
                if(state==VoiceUiState.LISTENING && started==0L){trialId=java.util.UUID.randomUUID().toString();started=SystemClock.elapsedRealtime();raw="";error=null;saved=false;source="MICROPHONE";elapsed=null}
            },{value->trialId=java.util.UUID.randomUUID().toString();error=value;raw="";source="MICROPHONE";saved=false;started=0;elapsed=null},{value->note=listOf(note,value).filter{it.isNotBlank()}.joinToString("\n");saved=false})}
            if(raw.isNotEmpty()){Text("실제 인식 원문 · ${if(source=="MANUAL")"수동 검수"else"마이크"}",fontWeight=FontWeight.Bold);Text(raw)}
            error?.let{Text("실패 기록: $it",color=ArmyristColors.Danger)}
            TextButton(onClick={manual=!manual}){Text(if(manual)"직접 입력 닫기"else"결과 직접 입력 (검수용)")}
            if(manual){
                Text("직접 입력한 결과는 음성 정확도 통계에서 제외합니다.",style=MaterialTheme.typography.bodySmall)
                OutlinedTextField(raw,{if(source!="MANUAL")trialId=java.util.UUID.randomUUID().toString();raw=it.take(4000);source="MANUAL";error=null;saved=false;elapsed=null},label={Text("수동 인식 결과")},modifier=Modifier.fillMaxWidth())
            }
            OutlinedTextField(truth,{truth=it.take(4000);saved=false},label={Text("실제로 읽은 정답")},supportingText={Text("다르게 읽었다면 그 내용으로 고쳐주세요. 인식 원문은 그대로 보관됩니다.")},modifier=Modifier.fillMaxWidth())
            if(raw.isNotBlank()&&error==null)VoiceAccuracy.cer(truth,raw)?.let{cer->Text("${if(source=="MANUAL")"수동 비교 · "else""}문자 오류율 ${"%.1f".format(java.util.Locale.KOREAN,cer*100)}%",fontWeight=FontWeight.Bold)}
            if(raw.isNotBlank()&&error==null)VoiceAccuracy.numberAwareCer(truth,raw)?.let{cer->Text("숫자 표기 통일 후 오류율 ${"%.1f".format(java.util.Locale.KOREAN,cer*100)}%",style=MaterialTheme.typography.bodySmall)}
            Text("문자 오류율은 원문 기준입니다. 추가 지표는 ‘스물네 병/24병’ 같은 숫자·단위 표기를 통일하며 의미 정확도를 뜻하지 않습니다.",style=MaterialTheme.typography.bodySmall)
            if(raw.isNotBlank()){
                Text("현재 버전의 인식 문장 해석",fontWeight=FontWeight.Bold)
                Text(VoiceAccuracy.structured(tool,raw,prompt.referenceDate).toString(2),style=MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(expected,{expected=it.take(4000);saved=false},label={Text("기대하는 항목·시간·수량 (선택)")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(note,{note=it.take(4000);saved=false},label={Text("환경·틀린 부분 메모 (선택)")},modifier=Modifier.fillMaxWidth())
            Button(enabled=loaded.isSuccess&&!saved&&truth.isNotBlank()&&(raw.isNotBlank()||error!=null),onClick={
                runCatching{
                    val info=context.packageManager.getPackageInfo(context.packageName,0)
                    val env=JSONObject().put("appVersion",info.versionName).put("sdkInt",Build.VERSION.SDK_INT).put("manufacturer",Build.MANUFACTURER).put("model",Build.MODEL).put("engine","android_on_device").put("language","ko-KR")
                    repo.save(VoiceAccuracy.trial(tool,kind,prompt,truth,raw,source,error,expected,note,elapsed,env).put("id",trialId))
                }.onSuccess{loaded=runCatching{repo.load()};saved=true;message("기록을 저장했습니다.")}.onFailure{message("저장 실패: ${it.message}")}
            },modifier=Modifier.fillMaxWidth()){Text(if(saved)"저장됨"else"이 결과 저장")}
            OutlinedButton(onClick={next()},modifier=Modifier.fillMaxWidth()){Text("다음 랜덤 제시문")}
            loaded.getOrNull()?.let{items->
                val summary=VoiceAccuracy.summary(items)
                Text("누적 ${items.size}건 · 마이크 ${summary.getInt("microphoneResults")}건 · 실패 ${summary.getInt("failedAttempts")}건 · 수동 ${summary.getInt("manualTrials")}건",fontWeight=FontWeight.Bold)
                if(!summary.isNull("numberAwareCharacterErrorRate"))Text("누적 숫자 표기 통일 후 오류율 ${"%.1f".format(java.util.Locale.KOREAN,summary.getDouble("numberAwareCharacterErrorRate")*100)}%",style=MaterialTheme.typography.bodySmall)
                if(!summary.isNull("characterErrorRate"))Text("누적 문자 오류율 ${"%.1f".format(java.util.Locale.KOREAN,summary.getDouble("characterErrorRate")*100)}% · 일치 ${summary.getInt("normalizedExactMatches")}/${summary.getInt("microphoneResults")}",style=MaterialTheme.typography.bodySmall)
                Button(enabled=items.isNotEmpty(),onClick={export.launch("Killomris-voice-test-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}.json")},modifier=Modifier.fillMaxWidth()){Text("테스트 자료 내보내기")}
                items.takeLast(3).reversed().forEach{item->Text("${ProbeTool.valueOf(item.getString("tool")).label} · ${item.getString("outcome")} · ${item.getString("rawTranscript").ifBlank{item.optString("error")}}",style=MaterialTheme.typography.bodySmall)}
            }
            loaded.exceptionOrNull()?.let{Text("기존 기록을 읽지 못했습니다. 원본은 유지되며 덮어쓰지 않습니다: ${it.message}",color=ArmyristColors.Danger)}
        }
    }
}
