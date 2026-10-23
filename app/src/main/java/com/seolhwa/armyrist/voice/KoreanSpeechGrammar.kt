package com.seolhwa.armyrist.voice

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Offline candidates only: inferred interpretation is always visible in review. */
internal object KoreanSpeechGrammar {
    private val ones=mapOf("한" to 1,"하나" to 1,"두" to 2,"둘" to 2,"세" to 3,"셋" to 3,"네" to 4,"넷" to 4,"다섯" to 5,"여섯" to 6,"일곱" to 7,"여덟" to 8,"아홉" to 9)
    private val tens=mapOf("열" to 10,"스물" to 20,"서른" to 30,"마흔" to 40,"쉰" to 50,"예순" to 60,"일흔" to 70,"여든" to 80,"아흔" to 90)
    private val native=buildMap {putAll(ones);putAll(tens);put("스무",20);tens.forEach{(t,n)->ones.forEach{(o,v)->put(t+o,n+v)}}}
    private val digits=mapOf('일' to 1,'이' to 2,'삼' to 3,'사' to 4,'오' to 5,'육' to 6,'칠' to 7,'팔' to 8,'구' to 9)
    fun numberOf(raw:String):Int? {
        val t=raw.replace(Regex("\\s+"),"")
        if(t.matches(Regex("[0-9]+")))return t.toIntOrNull()
        native[t]?.let{return it};if(t=="영"||t=="공")return 0
        if(t.isEmpty()||t.any{it !in "일이삼사오육칠팔구십백천"})return null
        var total=0;var pending=0;var lastUnit=10000
        for(c in t){val d=digits[c];if(d!=null){if(pending!=0)return null;pending=d}else{
            val u=when(c){'십'->10;'백'->100;else->1000};if(u>=lastUnit)return null
            total+=(if(pending==0)1 else pending)*u;pending=0;lastUnit=u
        }}
        return total+pending
    }
    private fun nativePattern(word:String):String {
        val t=tens.keys.firstOrNull{word.startsWith(it)&&word.length>it.length}
        return if(t==null)Regex.escape(word)else Regex.escape(t)+"\\s*"+Regex.escape(word.removePrefix(t))
    }
    private val numberToken="(?:[0-9]+|"+native.keys.sortedByDescending{it.length}.joinToString("|"){nativePattern(it)}+"|[일이삼사오육칠팔구십백천]+|영|공)"
    private const val units="세트|박스|켤레|마리|개|명|대|병|발|장|정|통|권|봉|쌍"
    private val quantity=Regex("(?<!\\S)($numberToken)\\s*($units|데)(?=\\s|$)")
    private val customQuantity=Regex("^(.+?)\\s+($numberToken)\\s*([가-힣A-Za-z]+)$")
    private val comparable=Regex("(?<![가-힣0-9])($numberToken)\\s*(시간|$units|시|분|일|월|년)(?=$|\\s|[,.!?，。])")
    fun numberAwareComparison(text:String)=comparable.replace(text){m->numberOf(m.groupValues[1])?.let{"$it${m.groupValues[2]}"}?:m.value}
    private val boundaries=Regex("\\n+|[,.，。;；]|\\s+그리고\\s+|\\s+및\\s+")
    private fun chunks(text:String)=text.split(boundaries).map{it.trim()}.filter{it.isNotEmpty()}
    private val actions=Regex(".+?(?<!미)(?:확인|점검|체크|검사|준비|잠금|해제|보고|제출|정리|청소|교체|반납)(?=\\s|$)")
    private val confusedTerm=Regex("(?<![가-힣])구금낭(?=$|\\s|[,.])")
    private fun correct(raw:String)=raw.replace(confusedTerm,"구급낭")
    fun counting(text:String):List<CountingVoiceDraft> = chunks(text).flatMap{segment->
        val matches=quantity.findAll(segment).toList()
        if(matches.isEmpty()){
            val c=customQuantity.matchEntire(segment)
            if(c!=null&&numberOf(c.groupValues[2])!=null){
                val attachedNative=!c.groupValues[2].first().isDigit()&&c.groups[2]!!.range.last+1==c.groups[3]!!.range.first
                listOf(countingDraft(c.groupValues[1],numberOf(c.groupValues[2]),c.groupValues[3],segment,attachedNative))
            }else{
                val words=segment.split(Regex("\\s+"));val q=if(words.size>1)numberOf(words.last())else null
                listOf(countingDraft(if(q!=null)words.dropLast(1).joinToString(" ")else segment,q,null,segment,false))
            }
        }else buildList{
            var start=0
            matches.forEach{m->val raw=segment.substring(start,m.range.last+1).trim();val name=segment.substring(start,m.range.first).trim()
                val unit=m.groupValues[2];val inferred=unit=="데"||(!m.groupValues[1].first().isDigit()&&!m.value.contains(Regex("\\s")))
                add(countingDraft(name,numberOf(m.groupValues[1]),if(unit=="데")"대"else unit,raw,inferred));start=m.range.last+1
            }
            segment.substring(start).trim().takeIf{it.isNotEmpty()}?.let{add(countingDraft(it,null,null,it,false))}
        }
    }
    private fun countingDraft(rawName:String,q:Int?,unit:String?,raw:String,inferred:Boolean):CountingVoiceDraft {
        val name=correct(rawName);val changed=name!=rawName
        return KoreanVoiceStructurer.revalidate(CountingVoiceDraft(name,q,unit,rawTranscript=raw,state=VoiceDraftState.VALID,
            reviewReason=listOfNotNull(if(changed)"용어 교정 후보: $rawName → $name"else null,if(inferred)"붙은 숫자·단위를 해석했습니다. 수량과 단위를 확인하세요."else null).joinToString("\n"),
            nameState=if(name.isBlank())VoiceFieldState.INVALID else if(changed)VoiceFieldState.REVIEW_REQUIRED else VoiceFieldState.VALID,
            quantityState=if(q==null)VoiceFieldState.INVALID else if(inferred)VoiceFieldState.REVIEW_REQUIRED else VoiceFieldState.VALID,
            unitState=if(unit==null)VoiceFieldState.INVALID else if(inferred)VoiceFieldState.REVIEW_REQUIRED else VoiceFieldState.VALID))
    }
    fun checklist(text:String):List<ChecklistVoiceDraft> = chunks(text).flatMap{segment->
        val matches=actions.findAll(segment).toList()
        val parts=if(matches.size<2)listOf(segment)else matches.map{it.value.trim()}+listOfNotNull(segment.substring(matches.last().range.last+1).trim().takeIf{it.isNotEmpty()})
        parts.map{raw->val name=correct(raw);val changed=name!=raw;val inferred=parts.size>1
            ChecklistVoiceDraft(name=name,rawTranscript=raw,state=if(changed||inferred)VoiceDraftState.REVIEW_REQUIRED else VoiceDraftState.VALID,
                nameState=if(changed||inferred)VoiceFieldState.REVIEW_REQUIRED else VoiceFieldState.VALID,
                reviewReason=listOfNotNull(if(changed)"용어 교정 후보: $raw → $name"else null,if(inferred)"쉼표가 없어 동작 표현을 기준으로 나눴습니다. 항목 경계를 확인하세요."else null).joinToString("\n"))}
    }
    private val hour="(?:[0-9]{1,2}|"+native.filterValues{it<=24}.keys.sortedByDescending{it.length}.joinToString("|"){nativePattern(it)}+"|[일이삼사오육칠팔구십]+|영|공)"
    private const val minute="(?:[0-9]{1,2}|[일이삼사오육칠팔구십]+|영|공)"
    private val clock=Regex("(?<![0-9가-힣])(?:(새벽|오전|오후|저녁|밤|낮)\\s*)?($hour)\\s*시(?:\\s*($minute)\\s*분|\\s*(반))?")
    private val day=Regex("(?:(\\d{4})년\\s*)?(?:(\\d{1,2})월\\s*)?(\\d{1,2})일")
    private val relativeDay=Regex("오늘|내일|모레")
    private fun timeChunks(text:String)=chunks(text).flatMap{segment->
        val clocks=clock.findAll(segment).toList();val starts=mutableListOf(0)
        clocks.zipWithNext().forEach{(before,after)->
            val gap=segment.substring(before.range.last+1,after.range.first)
            val marker=day.findAll(gap).lastOrNull()?:relativeDay.findAll(gap).lastOrNull()
            if(gap.trim() !in listOf("부터","~","∼")||marker!=null)starts+=if(marker==null)after.range.first else before.range.last+1+marker.range.first
        }
        starts.distinct().mapIndexed{i,start->segment.substring(start,starts.getOrNull(i+1)?:segment.length).trim()}
    }
    fun timePlan(text:String,referenceDate:LocalDate):List<TimePlanVoiceDraft>{
        var inheritedDate=referenceDate;var previousStart:LocalDateTime?=null;var inheritedMarker=""
        return timeChunks(text).map{raw->
            val explicitDay=day.find(raw);val relative=relativeDay.find(raw);val explicit=explicitDay!=null||relative!=null
            val date=when{explicitDay!=null->runCatching{LocalDate.of(explicitDay.groupValues[1].toIntOrNull()?:referenceDate.year,explicitDay.groupValues[2].toIntOrNull()?:inheritedDate.monthValue,explicitDay.groupValues[3].toInt())}.getOrNull()
                relative!=null->referenceDate.plusDays(when(relative.value){"내일"->1L;"모레"->2L;else->0L});else->inheritedDate}
            if(explicit)inheritedMarker=""
            val clocks=clock.findAll(raw).toList();val first=clocks.firstOrNull();val marker=first?.groupValues?.get(1).orEmpty()
            val h=first?.groupValues?.get(2)?.let(::numberOf);val inherit=marker.isBlank()&&h in 1..12&&inheritedMarker.isNotBlank()
            var review=!explicit;val reasons=mutableListOf<String>()
            if(!explicit)reasons+="날짜 생략: 기준일/앞 일정의 날짜를 사용했습니다."
            if(inherit){review=true;reasons+="오전·오후를 앞 일정에서 이어받았습니다."}
            fun resolve(m:MatchResult?,d:LocalDate?,fallback:String):LocalDateTime?{
                if(m==null||d==null)return null
                var hour=numberOf(m.groupValues[2])?:return null
                val mark=m.groupValues[1].ifBlank{if(hour in 1..12)fallback else ""}
                if(mark.isNotBlank()&&hour !in 0..12)return null
                if((mark in listOf("오후","저녁","낮")&&hour in 1..11)||(mark=="밤"&&hour in 6..11))hour+=12
                if(mark in listOf("오전","새벽","밤")&&hour==12)hour=0
                val min=if(m.groupValues[4].isNotBlank())30 else m.groupValues[3].takeIf{it.isNotBlank()}?.let(::numberOf)?:0
                if(hour !in 0..23||min !in 0..59)return null
                return LocalDateTime.of(d,LocalTime.of(hour,min))
            }
            var start=resolve(first,date,if(inherit)inheritedMarker else "")
            if(start!=null&&!explicit&&previousStart?.let{start!!.isBefore(it)}==true){
                if(previousStart!!.hour>=18&&start!!.hour<6){start=start!!.plusDays(1);reasons+="자정을 지난 다음 날로 해석했습니다."}else reasons+="앞 일정보다 이른 시각입니다. 날짜·오전/오후를 확인하세요."
                review=true
            }
            val range="부터" in raw||"까지" in raw||"~" in raw||"∼" in raw
            val second=clocks.getOrNull(1);var end=if(range)resolve(second,start?.toLocalDate(),marker)else null
            if(second!=null&&start!=null&&second.groupValues[1].isBlank()&&numberOf(second.groupValues[2]) in 1..12&&marker.isNotBlank()){
                resolve(second,start!!.toLocalDate(),"")?.let{plain->val half=plain.hour%12
                    end=listOf(plain.withHour(half),plain.withHour(half+12)).flatMap{listOf(it,it.plusDays(1))}.filter{!it.isBefore(start)}.minOrNull()
                    review=true;reasons+="종료 시각의 오전·오후를 이어지는 시간대로 추정했습니다."}
            }
            if(start!=null&&end!=null&&end!!.isBefore(start)){end=end!!.plusDays(1);review=true;reasons+="종료를 다음 날로 해석했습니다."}
            if(start!=null){inheritedDate=start!!.toLocalDate();previousStart=start};if(marker.isNotBlank())inheritedMarker=marker
            val rawName=raw.replace(day,"").replace(relativeDay,"").replace(clock,"").replace(Regex("부터|까지|[~∼]"),"").replace(Regex("\\s+")," ").trim()
            val name=correct(rawName);if(name!=rawName)reasons+="용어 교정 후보: $rawName → $name"
            return@map KoreanVoiceStructurer.revalidate(TimePlanVoiceDraft(name,start,end,rawTranscript=raw,state=VoiceDraftState.VALID,reviewReason=reasons.joinToString("\n"),
                nameState=if(name.isBlank())VoiceFieldState.INVALID else if(name!=rawName)VoiceFieldState.REVIEW_REQUIRED else VoiceFieldState.VALID,
                dateTimeState=if(start==null)VoiceFieldState.INVALID else if(review)VoiceFieldState.REVIEW_REQUIRED else VoiceFieldState.VALID,
                rangeEndState=if(range&&end==null)VoiceFieldState.INVALID else if(range&&review)VoiceFieldState.REVIEW_REQUIRED else VoiceFieldState.VALID))
        }
    }
}
