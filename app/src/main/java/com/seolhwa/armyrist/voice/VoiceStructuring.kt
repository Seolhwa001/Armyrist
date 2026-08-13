package com.seolhwa.armyrist.voice

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class DraftState { READY, REVIEW_REQUIRED, UNRESOLVED }

data class CountingVoiceDraft(
    val name: String,
    val quantity: Int?,
    val unit: String?,
    val note: String = "",
    val state: DraftState
)

data class ChecklistVoiceDraft(
    val name: String,
    val note: String = "",
    val scheduledTimeMinutes: Int? = null,
    val state: DraftState = DraftState.READY
)

data class TimePlanVoiceDraft(
    val name: String,
    val dateTime: LocalDateTime?,
    val rangeEnd: LocalDateTime? = null,
    val note: String = "",
    val state: DraftState
)

object KoreanVoiceStructurer {
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

    fun counting(transcript: String): List<CountingVoiceDraft> {
        val chunks = transcript
            .split(Regex("[,，]| 그리고 | 그리고|,?\\s*및\\s*"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val pattern = Regex("^(.+?)\\s+(\\d+|[가-힣]+)\\s*([가-힣A-Za-z]+)$")
        return chunks.map { chunk ->
            val m = pattern.find(chunk)
            if (m == null) {
                CountingVoiceDraft(chunk, null, null, state = DraftState.UNRESOLVED)
            } else {
                val q = numberOf(m.groupValues[2])
                CountingVoiceDraft(
                    m.groupValues[1].trim(),
                    q,
                    m.groupValues[3].trim(),
                    state = if (q == null) DraftState.REVIEW_REQUIRED else DraftState.READY
                )
            }
        }
    }

    fun checklist(transcript: String): List<ChecklistVoiceDraft> =
        transcript
            .split(Regex("[,，]| 그리고 | 그리고|\\s+및\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { ChecklistVoiceDraft(it) }

    /** Deterministic offline structuring. Ambiguous/missing dates remain UNRESOLVED. */
    fun timePlan(transcript: String, referenceDate: LocalDate): List<TimePlanVoiceDraft> {
        val chunks = transcript.split(Regex("[,，]| 그리고 | 그리고"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        var inheritedDate: LocalDate? = null
        val timeRegex = Regex("(?:(새벽|오전|오후)\\s*)?(\\d{1,2})시(?:\\s*(\\d{1,2})분)?")

        fun resolveTime(match: MatchResult, date: LocalDate): LocalDateTime? {
            var h = match.groupValues[2].toIntOrNull() ?: return null
            val marker = match.groupValues[1]
            if (marker == "오후" && h in 1..11) h += 12
            if ((marker == "새벽" || marker == "오전") && h == 12) h = 0
            val minute = match.groupValues[3].toIntOrNull() ?: 0
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
                state = if (ready) DraftState.READY else DraftState.UNRESOLVED
            )
        }
    }

}