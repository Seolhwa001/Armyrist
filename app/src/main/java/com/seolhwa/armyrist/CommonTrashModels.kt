package com.seolhwa.armyrist.trash

enum class TrashToolType {
    TIME_PLAN,
    COUNTING,
    CHECKLIST
}

data class CommonTrashItem(
    val id: String,
    val toolType: TrashToolType,
    val originalId: String,
    val title: String,
    val deletedAt: Long,
    val payloadVersion: Int,
    val payload: String
)

object CommonTrashRetention {
    const val NEVER = -1
    val supportedDays = listOf(7, 30, 90, NEVER)

    fun label(days: Int): String =
        if (days == NEVER) "자동 삭제 안 함" else "${days}일"
}
