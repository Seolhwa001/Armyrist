package com.seolhwa.armyrist.voice

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class VoiceDraftState {
    VALID,
    REVIEW_REQUIRED,
    INVALID;

    // Compatibility aliases for older call sites while the project transitions.
    companion object {
        val READY: VoiceDraftState = VALID
        val UNRESOLVED: VoiceDraftState = INVALID
    }
}
typealias DraftState = VoiceDraftState

enum class VoiceFieldState { VALID, REVIEW_REQUIRED, INVALID }

data class CountingVoiceDraft(
    val name: String,
    val quantity: Int?,
    val unit: String?,
    val note: String = "",
    val rawTranscript: String = "",
    val state: VoiceDraftState,
    val nameState: VoiceFieldState = if (name.isBlank()) VoiceFieldState.INVALID else VoiceFieldState.VALID,
    val quantityState: VoiceFieldState = if (quantity == null) VoiceFieldState.INVALID else VoiceFieldState.VALID,
    val unitState: VoiceFieldState = if (unit.isNullOrBlank()) VoiceFieldState.INVALID else VoiceFieldState.VALID
)

data class ChecklistVoiceDraft(
    val name: String,
    val note: String = "",
    val scheduledTimeMinutes: Int? = null,
    val rawTranscript: String = "",
    val state: VoiceDraftState = if (name.isBlank()) VoiceDraftState.INVALID else VoiceDraftState.VALID,
    val nameState: VoiceFieldState = if (name.isBlank()) VoiceFieldState.INVALID else VoiceFieldState.VALID
)

data class TimePlanVoiceDraft(
    val name: String,
    val dateTime: LocalDateTime?,
    val rangeEnd: LocalDateTime? = null,
    val note: String = "",
    val rawTranscript: String = "",
    val state: VoiceDraftState,
    val nameState: VoiceFieldState = if (name.isBlank()) VoiceFieldState.INVALID else VoiceFieldState.VALID,
    val dateTimeState: VoiceFieldState = if (dateTime == null) VoiceFieldState.INVALID else VoiceFieldState.VALID,
    val rangeEndState: VoiceFieldState = when {
        rangeEnd == null -> VoiceFieldState.VALID
        dateTime == null || rangeEnd.isBefore(dateTime) -> VoiceFieldState.INVALID
        else -> VoiceFieldState.VALID
    }
)


object KoreanVoiceStructurer {
    /**
     * Recognition-cycle boundaries are preserved by OfflineSpeechSession as newlines.
     * Each segment can still contain multiple items separated by punctuation/conjunctions.
     */
    private fun speechChunks(transcript: String): List<String> =
        transcript
            .split(Regex("\\n+|[,.，。;；]|\\s+그리고\\s+|\\s+및\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /**
     * If STT removes punctuation from a single utterance, repeated
     * <name> <quantity> <unit> structures can still be separated.
     * This is only used when two or more quantity tokens are found.
     */
    private fun countingChunks(transcript: String): List<String> =
        speechChunks(transcript).flatMap { segment ->
            val tokens = segment.split(Regex("\\s+")).filter { it.isNotBlank() }
            val quantityIndexes = tokens.indices.filter { index ->
                numberOf(tokens[index]) != null ||
                    Regex("^\\d+[가-힣A-Za-z]+$").matches(tokens[index])
            }
            if (quantityIndexes.size < 2) return@flatMap listOf(segment)

            val parts = mutableListOf<String>()
            var start = 0
            quantityIndexes.forEachIndexed { qPos, qIndex ->
                val attached = Regex("^(\\d+)([가-힣A-Za-z]+)$").matchEntire(tokens[qIndex])
                val end = if (attached != null) qIndex else qIndex + 1
                if (end >= tokens.size) return@forEachIndexed
                val nextStart = if (qPos + 1 < quantityIndexes.size) {
                    // The next item name begins after this item's unit and before its quantity.
                    val nextQ = quantityIndexes[qPos + 1]
                    // Keep at least one token for the next name.
                    maxOf(end + 1, nextQ - 1)
                } else tokens.size
                val itemEndExclusive = if (qPos + 1 < quantityIndexes.size) nextStart else tokens.size
                if (start < itemEndExclusive) {
                    parts += tokens.subList(start, itemEndExclusive).joinToString(" ")
                }
                start = itemEndExclusive
            }
            parts.filter { it.isNotBlank() }.ifEmpty { listOf(segment) }
        }

    /** Checklist STT often drops commas; common action endings provide a safe hint. */
    private fun checklistChunks(transcript: String): List<String> =
        speechChunks(transcript).flatMap { segment ->
            val matches = Regex(".+?(?:확인|점검|체크|검사|준비)(?=\\s|$)")
                .findAll(segment)
                .map { it.value.trim() }
                .filter { it.isNotBlank() }
                .toList()
            if (matches.size >= 2 && matches.joinToString(" ").replace(Regex("\\s+"), " ").trim() ==
                segment.replace(Regex("\\s+"), " ").trim()
            ) matches else listOf(segment)
        }

    /** Explicit day markers are strong TimePlan event boundaries even if STT drops commas. */
    private fun timePlanChunks(transcript: String): List<String> =
        speechChunks(transcript).flatMap { segment ->
            val starts = Regex("(?<!\\d)(\\d{1,2})일\\s*").findAll(segment).map { it.range.first }.toList()
            if (starts.size < 2) listOf(segment)
            else starts.mapIndexed { index, start ->
                val end = starts.getOrNull(index + 1) ?: segment.length
                segment.substring(start, end).trim()
            }.filter { it.isNotBlank() }
        }

    private val nativeNumbers = linkedMapOf(
        "스물아홉" to 29, "스물여덟" to 28, "스물일곱" to 27, "스물여섯" to 26, "스물다섯" to 25,
        "스물네" to 24, "스물세" to 23, "스물두" to 22, "스물한" to 21, "스물" to 20,
        "열아홉" to 19, "열여덟" to 18, "열일곱" to 17, "열여섯" to 16, "열다섯" to 15,
        "열네" to 14, "열세" to 13, "열두" to 12, "열한" to 11, "열" to 10,
        "아홉" to 9, "여덟" to 8, "일곱" to 7, "여섯" to 6, "다섯" to 5, "네" to 4,
        "세" to 3, "두" to 2, "한" to 1, "하나" to 1
    )

    fun numberOf(raw: String): Int? {
        raw.trim().toIntOrNull()?.let { return it }
        nativeNumbers.entries.firstOrNull { raw.trim() == it.key }?.let { return it.value }
        return sinoNumber(raw.trim())
    }

    private fun sinoNumber(raw: String): Int? {
        if (raw.isBlank()) return null
        val digit = mapOf('일' to 1, '이' to 2, '삼' to 3, '사' to 4, '오' to 5, '육' to 6, '칠' to 7, '팔' to 8, '구' to 9)
        if (raw.length == 1) return digit[raw[0]]
        if ('십' in raw) {
            val p = raw.split('십')
            val tens = if (p[0].isBlank()) 1 else p[0].firstOrNull()?.let { digit[it] } ?: return null
            val ones = if (p.getOrElse(1) { "" }.isBlank()) 0 else p[1].firstOrNull()?.let { digit[it] } ?: return null
            return tens * 10 + ones
        }
        return null
    }

    private val commonCountingUnits = setOf(
        "개", "명", "대", "병", "마리", "발", "장", "정", "통", "권", "봉", "세트", "박스", "켤레", "쌍"
    )

    private fun ambiguousAttachedQuantityUnit(token: String): Pair<Int, String>? {
        // Example: "세대" can mean an ordinary word or "세 대".
        // Return a candidate only as REVIEW_REQUIRED; never silently commit the interpretation.
        nativeNumbers.entries
            .sortedByDescending { it.key.length }
            .forEach { (word, number) ->
                if (token.startsWith(word) && token.length > word.length) {
                    val unit = token.removePrefix(word)
                    if (unit in commonCountingUnits) return number to unit
                }
            }
        return null
    }

    fun revalidate(draft: CountingVoiceDraft): CountingVoiceDraft {
        val nameState = if (draft.name.isBlank()) VoiceFieldState.INVALID else draft.nameState
        val quantityState = if (draft.quantity == null) VoiceFieldState.INVALID else draft.quantityState
        val unitState = if (draft.unit.isNullOrBlank()) VoiceFieldState.INVALID else draft.unitState
        val fieldStates = listOf(nameState, quantityState, unitState)
        val state = when {
            VoiceFieldState.INVALID in fieldStates -> VoiceDraftState.INVALID
            VoiceFieldState.REVIEW_REQUIRED in fieldStates -> VoiceDraftState.REVIEW_REQUIRED
            else -> VoiceDraftState.VALID
        }
        return draft.copy(
            state = state,
            nameState = nameState,
            quantityState = quantityState,
            unitState = unitState
        )
    }

    fun revalidate(draft: ChecklistVoiceDraft): ChecklistVoiceDraft {
        val nameState = if (draft.name.isBlank()) VoiceFieldState.INVALID else draft.nameState
        val state = when (nameState) {
            VoiceFieldState.INVALID -> VoiceDraftState.INVALID
            VoiceFieldState.REVIEW_REQUIRED -> VoiceDraftState.REVIEW_REQUIRED
            VoiceFieldState.VALID -> VoiceDraftState.VALID
        }
        return draft.copy(state = state, nameState = nameState)
    }

    fun revalidate(draft: TimePlanVoiceDraft): TimePlanVoiceDraft {
        val nameState = if (draft.name.isBlank()) VoiceFieldState.INVALID else draft.nameState
        val dateState = if (draft.dateTime == null) VoiceFieldState.INVALID else draft.dateTimeState
        val endState = when {
            draft.rangeEnd == null -> draft.rangeEndState
            draft.dateTime == null || draft.rangeEnd.isBefore(draft.dateTime) -> VoiceFieldState.INVALID
            else -> draft.rangeEndState
        }
        val fieldStates = listOf(nameState, dateState, endState)
        val state = when {
            VoiceFieldState.INVALID in fieldStates -> VoiceDraftState.INVALID
            VoiceFieldState.REVIEW_REQUIRED in fieldStates -> VoiceDraftState.REVIEW_REQUIRED
            else -> VoiceDraftState.VALID
        }
        return draft.copy(
            state = state,
            nameState = nameState,
            dateTimeState = dateState,
            rangeEndState = endState
        )
    }

    fun counting(transcript: String): List<CountingVoiceDraft> {
        val chunks = countingChunks(transcript)
        val separatedPattern = Regex("^(.+?)\\s+(\\d+|[가-힣]+)\\s+([가-힣A-Za-z]+)$")
        val attachedNumericPattern = Regex("^(.+?)\\s+(\\d+)([가-힣A-Za-z]+)$")

        return chunks.map { chunk ->
            val m = separatedPattern.matchEntire(chunk) ?: attachedNumericPattern.matchEntire(chunk)
            if (m != null) {
                val q = numberOf(m.groupValues[2])
                CountingVoiceDraft(
                    name = m.groupValues[1].trim(),
                    quantity = q,
                    unit = m.groupValues[3].trim(),
                    rawTranscript = chunk,
                    state = if (q == null) VoiceDraftState.INVALID else VoiceDraftState.VALID,
                    quantityState = if (q == null) VoiceFieldState.INVALID else VoiceFieldState.VALID
                )
            } else {
                val tokens = chunk.split(Regex("\\s+")).filter { it.isNotBlank() }
                val last = tokens.lastOrNull().orEmpty()
                val ambiguous = ambiguousAttachedQuantityUnit(last)
                val trailingQuantity = last.takeIf { ambiguous == null }?.let(::numberOf)

                when {
                    tokens.size >= 2 && ambiguous != null -> {
                        CountingVoiceDraft(
                            name = tokens.dropLast(1).joinToString(" "),
                            quantity = ambiguous.first,
                            unit = ambiguous.second,
                            rawTranscript = chunk,
                            state = VoiceDraftState.REVIEW_REQUIRED,
                            quantityState = VoiceFieldState.REVIEW_REQUIRED,
                            unitState = VoiceFieldState.REVIEW_REQUIRED
                        )
                    }
                    tokens.size >= 2 && trailingQuantity != null -> {
                        CountingVoiceDraft(
                            name = tokens.dropLast(1).joinToString(" "),
                            quantity = trailingQuantity,
                            unit = null,
                            rawTranscript = chunk,
                            state = VoiceDraftState.INVALID,
                            unitState = VoiceFieldState.INVALID
                        )
                    }
                    else -> {
                        CountingVoiceDraft(
                            name = chunk,
                            quantity = null,
                            unit = null,
                            rawTranscript = chunk,
                            state = VoiceDraftState.INVALID
                        )
                    }
                }
            }
        }
    }

    fun checklist(transcript: String): List<ChecklistVoiceDraft> =
        checklistChunks(transcript).map { chunk ->
            ChecklistVoiceDraft(name = chunk, rawTranscript = chunk)
        }

    /** Deterministic offline structuring. Ambiguous/missing dates remain UNRESOLVED. */
    fun timePlan(transcript: String, referenceDate: LocalDate): List<TimePlanVoiceDraft> {
        val chunks = timePlanChunks(transcript)
        var inheritedDate: LocalDate? = null

        // Android Korean STT commonly emits either "3시" or "세 시".
        // Keep both numeric and Korean number words as valid clock-hour tokens.
        val hourToken = "(?:\\d{1,2}|스물(?:한|두|세|네)?|열(?:한|두|세|네|다섯|여섯|일곱|여덟|아홉)?|한|두|세|네|다섯|여섯|일곱|여덟|아홉)"
        val minuteToken = "(?:\\d{1,2}|십(?:일|이|삼|사|오|육|칠|팔|구)?|이십(?:일|이|삼|사|오|육|칠|팔|구)?|삼십(?:일|이|삼|사|오|육|칠|팔|구)?|사십(?:일|이|삼|사|오|육|칠|팔|구)?|오십(?:일|이|삼|사|오|육|칠|팔|구)?)"
        val timeRegex = Regex("(?:(새벽|오전|오후)\\s*)?($hourToken)\\s*시(?:\\s*($minuteToken)\\s*분)?")

        fun resolveTime(match: MatchResult, date: LocalDate): LocalDateTime? {
            var h = numberOf(match.groupValues[2]) ?: return null
            val marker = match.groupValues[1]
            if (marker == "오후" && h in 1..11) h += 12
            if ((marker == "새벽" || marker == "오전") && h == 12) h = 0
            val minute = match.groupValues[3].takeIf { it.isNotBlank() }?.let(::numberOf) ?: 0
            if (h !in 0..23 || minute !in 0..59) return null
            return runCatching { LocalDateTime.of(date, LocalTime.of(h, minute)) }.getOrNull()
        }

        return chunks.map { chunk ->
            val dayMatch = Regex("(\\d{1,2})일").find(chunk)
            val date = dayMatch?.groupValues?.get(1)?.toIntOrNull()?.let { day ->
                runCatching { referenceDate.withDayOfMonth(day) }.getOrNull()
            } ?: inheritedDate
            if (dayMatch != null && date != null) inheritedDate = date

            val timeMatches = timeRegex.findAll(chunk).toList()
            val start = if (date != null && timeMatches.isNotEmpty()) resolveTime(timeMatches[0], date) else null
            val wantsRange = "부터" in chunk || "~" in chunk || "까지" in chunk
            var rangeEnd = if (wantsRange && date != null && timeMatches.size >= 2) {
                resolveTime(timeMatches[1], date)
            } else null
            if (start != null && rangeEnd != null && rangeEnd!!.isBefore(start)) {
                rangeEnd = rangeEnd!!.plusDays(1)
            }

            val cleanedName = chunk
                .replace(Regex("(\\d{1,2})일"), "")
                .replace(timeRegex, "")
                .replace("부터", "")
                .replace("까지", "")
                .replace("~", "")
                .trim()
                .ifBlank { "일정" }

            val ready = start != null && (!wantsRange || rangeEnd != null)
            TimePlanVoiceDraft(
                name = cleanedName,
                dateTime = start,
                rangeEnd = rangeEnd,
                rawTranscript = chunk,
                state = if (ready) VoiceDraftState.VALID else VoiceDraftState.INVALID,
                dateTimeState = if (start == null) VoiceFieldState.INVALID else VoiceFieldState.VALID,
                rangeEndState = if (!wantsRange || rangeEnd != null) VoiceFieldState.VALID else VoiceFieldState.INVALID
            )
        }
    }

}