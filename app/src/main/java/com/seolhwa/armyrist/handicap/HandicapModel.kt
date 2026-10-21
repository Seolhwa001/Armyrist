package com.seolhwa.armyrist.handicap

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

enum class HandicapKind { BLACK, WHITE }
enum class WordCategory(val title: String) { WHO("누가"), WHEN("언제"), WHERE("어디서"), WHAT("무엇을"), HOW("어떻게") }
data class SituationWord(val id: String, val text: String)
data class UnitContact(val name: String = "", val phone: String = "") {
    fun normalized() = copy(name = name.trim().take(60), phone = phone.trim().take(40))
}
data class HandicapConfig(
    val issuer: UnitContact = UnitContact(),
    val upper: List<UnitContact> = List(4) { UnitContact() },
    val lower: List<UnitContact> = List(4) { UnitContact() },
    val words: Map<WordCategory, List<SituationWord>> = defaultWords()
) {
    fun normalized() = copy(
        issuer = issuer.normalized(),
        upper = List(4) { upper.getOrNull(it)?.normalized() ?: UnitContact() },
        lower = List(4) { lower.getOrNull(it)?.normalized() ?: UnitContact() },
        words = WordCategory.entries.associateWith { category ->
            words[category].orEmpty().map { it.copy(id = it.id.take(80), text = it.text.trim().take(80)) }
                .filter { it.id.isNotBlank() && it.text.isNotBlank() }.distinctBy { it.id }.take(100)
        }
    )
    companion object {
        fun defaultWords(): Map<WordCategory, List<SituationWord>> = mapOf(
            WordCategory.WHO to listOf("불침번", "위병조장", "위병근무자", "병사(용사)", "영내간부"),
            WordCategory.WHEN to emptyList(),
            WordCategory.WHERE to listOf("위병소", "통합막사", "병영식당", "체력단련장", "생활관", "보일러실"),
            WordCategory.WHAT to listOf("화재", "응급환자", "외부인 침입", "인원 미복귀", "시설 파손"),
            WordCategory.HOW to listOf("발견", "조치", "보고")
        ).mapValues { (category, values) -> values.mapIndexed { index, text -> SituationWord("${category.name}_$index", text) } }
    }
}
data class SituationDraft(
    val kind: HandicapKind? = null,
    val issuer: UnitContact = UnitContact(),
    val who: List<String> = emptyList(),
    val whenText: String = "",
    val where: List<String> = emptyList(),
    val what: List<String> = emptyList(),
    val how: List<String> = emptyList()
)
data class SituationResult(val sentence: String, val recipient: UnitContact, val route: String, val missing: List<String>)
object HandicapRules {
    fun currentTime(now: LocalDateTime) = now.format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"))
    fun build(config: HandicapConfig, draft: SituationDraft): SituationResult {
        val issuer = draft.issuer.normalized().takeIf { it.name.isNotBlank() } ?: config.issuer.normalized()
        val target = when (draft.kind) {
            HandicapKind.BLACK -> config.upper.getOrNull(0)?.normalized()
            HandicapKind.WHITE -> config.lower.getOrNull(0)?.normalized()
            null -> null
        }
        val hasTarget = target != null && target.name.isNotBlank()
        val recipient = if (hasTarget) target!! else issuer
        val missing = buildList {
            if (draft.kind == null) add("핸디캡 블랙/화이트")
            if (issuer.name.isBlank()) add("상황부여 제대")
            if (draft.who.isEmpty()) add("누가")
            if (draft.whenText.isBlank()) add("언제")
            if (draft.where.isEmpty()) add("어디서")
            if (draft.what.isEmpty()) add("무엇을")
            if (draft.how.isEmpty()) add("어떻게")
        }
        val route = when {
            draft.kind == null -> "핸디캡을 선택해주세요"
            hasTarget && draft.kind == HandicapKind.BLACK -> "블랙 ↑ 1차 상급제대"
            hasTarget -> "화이트 ↓ 1차 하급제대"
            draft.kind == HandicapKind.BLACK -> "1차 상급 미등록 → 상황부여 제대"
            else -> "1차 하급 미등록 → 상황부여 제대"
        }
        fun ordered(category: WordCategory, values: List<String>): String {
            val vocabulary = config.words[category].orEmpty().map { it.text }
            return values.distinct().sortedWith(compareBy<String> { vocabulary.indexOf(it).let { index -> if (index < 0) Int.MAX_VALUE else index } }.thenBy { it }).joinToString(" · ")
        }
        val sentence = "${draft.whenText.ifBlank { "[언제 미입력]" }}, " +
            "${ordered(WordCategory.WHERE, draft.where).ifBlank { "[어디서 미입력]" }}에서 " +
            "${ordered(WordCategory.WHO, draft.who).ifBlank { "[누가 미입력]" }} 관련 " +
            "${ordered(WordCategory.WHAT, draft.what).ifBlank { "[무엇을 미입력]" }} 상황발생.\n수행사항: ${ordered(WordCategory.HOW, draft.how).ifBlank { "[어떻게 미입력]" }}."
        return SituationResult(sentence, recipient, route, missing)
    }
}
object HandicapCodec {
    private fun unit(value: UnitContact) = JSONObject().put("name", value.name).put("phone", value.phone)
    private fun readUnit(j: JSONObject?) = UnitContact(j?.optString("name").orEmpty(), j?.optString("phone").orEmpty())
    private fun strings(values: List<String>) = JSONArray().also { a -> values.forEach { a.put(it) } }
    private fun readStrings(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { a!!.optString(it) }.filter { it.isNotBlank() }.take(100)
    fun encode(config: HandicapConfig, normalize: Boolean = true): String {
        val c = if (normalize) config.normalized() else config
        val words = JSONObject()
        WordCategory.entries.forEach { category -> words.put(category.name, JSONArray().also { a ->
            c.words[category].orEmpty().forEach { a.put(JSONObject().put("id", it.id).put("text", it.text)) }
        }) }
        return JSONObject().put("schemaVersion", 2).put("issuer", unit(c.issuer))
            .put("upper", JSONArray().also { a -> c.upper.forEach { a.put(unit(it)) } })
            .put("lower", JSONArray().also { a -> c.lower.forEach { a.put(unit(it)) } })
            .put("words", words).toString()
    }
    fun decode(raw: String?, normalize: Boolean = true): HandicapConfig = runCatching {
        if (raw.isNullOrBlank()) return HandicapConfig()
        val j = JSONObject(raw)
        val defaults = HandicapConfig()
        HandicapConfig(readUnit(j.optJSONObject("issuer")),
            List(4) { readUnit(j.optJSONArray("upper")?.optJSONObject(it)) },
            List(4) { readUnit(j.optJSONArray("lower")?.optJSONObject(it)) },
            WordCategory.entries.associateWith { category ->
                val a = j.optJSONObject("words")?.optJSONArray(category.name)
                val values = if (a == null) defaults.words[category].orEmpty() else (0 until a.length()).mapNotNull { index ->
                    a.optJSONObject(index)?.let { SituationWord(it.optString("id"), it.optString("text")) }
                }
                if (j.optInt("schemaVersion", 1) < 2 && category == WordCategory.WHERE && a != null) {
                    val migrated = values.map { if (it.id == "WHERE_1" && it.text == "막사") it.copy(text = "통합막사") else it }
                    val additions = defaults.words[category].orEmpty().filter { it.id in listOf("WHERE_4", "WHERE_5") && migrated.none { old -> old.id == it.id || old.text == it.text } }
                    migrated + additions
                } else values
            }).let { if (normalize) it.normalized() else it }
    }.getOrDefault(HandicapConfig())
    fun encodeDraft(d: SituationDraft): String = JSONObject().put("kind", d.kind?.name ?: "")
        .put("issuer", unit(d.issuer)).put("who", strings(d.who)).put("when", d.whenText)
        .put("where", strings(d.where)).put("what", strings(d.what)).put("how", strings(d.how)).toString()
    fun decodeDraft(raw: String?): SituationDraft = runCatching {
        if (raw.isNullOrBlank()) return SituationDraft()
        val j = JSONObject(raw)
        SituationDraft(HandicapKind.entries.firstOrNull { it.name == j.optString("kind") }, readUnit(j.optJSONObject("issuer")),
            readStrings(j.optJSONArray("who")), j.optString("when").take(120), readStrings(j.optJSONArray("where")), readStrings(j.optJSONArray("what")), readStrings(j.optJSONArray("how")))
    }.getOrDefault(SituationDraft())
}
