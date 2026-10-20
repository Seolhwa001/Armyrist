package com.seolhwa.armyrist.data

/** Publishes no replacement data when loading or committing a snapshot fails. */
internal class GuardedSnapshotStore<T>(
    private val readRaw: () -> String?,
    private val writeRaw: (String?) -> Boolean,
    private val decode: (String) -> T,
    private val encode: (T) -> String,
    private val onError: (String) -> Unit
) {
    var readable: Boolean = true
        private set
    private var verifiedRaw: String? = null
    private var hasVerifiedRaw = false

    fun load(emptyValue: T, previousValue: T = emptyValue): T = try {
        val raw = readRaw()
        val value = if (raw == null) emptyValue else decode(raw)
        verifiedRaw = raw
        hasVerifiedRaw = true
        readable = true
        value
    } catch (_: Exception) {
        readable = false
        onError("저장 데이터를 읽을 수 없습니다. 원본을 보호하기 위해 수정을 중지했습니다. 데이터 관리에서 백업을 복원해주세요.")
        previousValue
    }

    fun save(value: T): Boolean {
        if (!readable) {
            onError("저장 원본을 보호하고 있습니다. 백업 복원 후 다시 시도해주세요.")
            return false
        }
        val previous: String?
        try {
            previous = readRaw()
            // Also protect against corruption introduced since the last load.
            if (previous != null && (!hasVerifiedRaw || previous != verifiedRaw)) decode(previous)
        } catch (_: Exception) {
            readable = false
            onError("저장 데이터가 손상되어 변경하지 않았습니다. 백업을 복원해주세요.")
            return false
        }
        var encoded: String? = null
        val success = try {
            encoded = encode(value)
            writeRaw(encoded)
        } catch (_: Exception) { false }
        if (success) {
            verifiedRaw = encoded
            hasVerifiedRaw = true
        }
        if (!success) {
            // SharedPreferences updates its memory even when its disk commit fails.
            // Restore that memory too, so a later reader cannot publish failed edits.
            runCatching { writeRaw(previous) }
            onError("저장하지 못했습니다. 저장 공간을 확인한 뒤 다시 시도해주세요.")
        }
        return success
    }
}
