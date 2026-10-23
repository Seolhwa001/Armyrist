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
    val reviewReason: String = "",
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
    val reviewReason: String = "",
    val state: VoiceDraftState = if (name.isBlank()) VoiceDraftState.INVALID else VoiceDraftState.VALID,
    val nameState: VoiceFieldState = if (name.isBlank()) VoiceFieldState.INVALID else VoiceFieldState.VALID
)

data class TimePlanVoiceDraft(
    val name: String,
    val dateTime: LocalDateTime?,
    val rangeEnd: LocalDateTime? = null,
    val note: String = "",
    val rawTranscript: String = "",
    val reviewReason: String = "",
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
    fun numberOf(raw:String)=KoreanSpeechGrammar.numberOf(raw)
    fun numberAwareComparison(text:String)=KoreanSpeechGrammar.numberAwareComparison(text)
    fun counting(transcript:String)=KoreanSpeechGrammar.counting(transcript)
    fun checklist(transcript:String)=KoreanSpeechGrammar.checklist(transcript)
    fun timePlan(transcript:String,referenceDate:LocalDate)=KoreanSpeechGrammar.timePlan(transcript,referenceDate)
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

}
