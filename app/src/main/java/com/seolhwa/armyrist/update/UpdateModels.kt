package com.seolhwa.armyrist.update

import org.json.JSONArray
import org.json.JSONObject

data class UpdateReleaseMetadata(
    val schemaVersion: Int,
    val versionName: String,
    val versionCode: Long,
    val releaseType: String,
    val apkAsset: String,
    val apkSha256: String,
    val releaseNotes: List<String>,
    val apkDownloadUrl: String
) {
    fun toCachedJson(): String = JSONObject().apply {
        put("schemaVersion", schemaVersion)
        put("versionName", versionName)
        put("versionCode", versionCode)
        put("releaseType", releaseType)
        put("apkAsset", apkAsset)
        put("apkSha256", apkSha256)
        put("apkDownloadUrl", apkDownloadUrl)
        put("releaseNotes", JSONArray().apply { releaseNotes.forEach { put(it) } })
    }.toString()

    companion object {
        fun fromCachedJson(raw: String): UpdateReleaseMetadata? = runCatching {
            val json = JSONObject(raw)
            val notes = json.optJSONArray("releaseNotes") ?: JSONArray()
            UpdateReleaseMetadata(
                schemaVersion = json.getInt("schemaVersion"),
                versionName = json.getString("versionName"),
                versionCode = json.getLong("versionCode"),
                releaseType = json.getString("releaseType"),
                apkAsset = json.getString("apkAsset"),
                apkSha256 = json.getString("apkSha256"),
                releaseNotes = List(notes.length()) { notes.getString(it) },
                apkDownloadUrl = json.getString("apkDownloadUrl")
            )
        }.getOrNull()
    }
}

data class InstalledVersion(
    val versionName: String,
    val versionCode: Long
) {
    val displayName: String
        get() = Regex("^\\d+(?:\\.\\d+){1,3}")
            .find(versionName)
            ?.value
            ?: versionName

    companion object {
        fun compareDisplayVersions(left: String, right: String): Int? {
            fun parts(raw: String): List<Int>? {
                val match = Regex("^\\d+(?:\\.\\d+){1,3}$").matchEntire(raw.trim())
                    ?: return null
                return match.value.split('.').map { it.toIntOrNull() ?: return null }
            }
            val a = parts(left) ?: return null
            val b = parts(right) ?: return null
            val size = maxOf(a.size, b.size)
            for (index in 0 until size) {
                val av = a.getOrElse(index) { 0 }
                val bv = b.getOrElse(index) { 0 }
                if (av != bv) return av.compareTo(bv)
            }
            return 0
        }
    }
}

sealed interface UpdateCheckResult {
    data class Latest(
        val installed: InstalledVersion,
        val latestStable: UpdateReleaseMetadata
    ) : UpdateCheckResult

    data class Available(
        val installed: InstalledVersion,
        val release: UpdateReleaseMetadata
    ) : UpdateCheckResult

    data class NoNewerEligibleRelease(
        val installed: InstalledVersion,
        val latestPublishedVersionName: String?
    ) : UpdateCheckResult

    data class Failure(
        val message: String,
        val networkRelated: Boolean = false
    ) : UpdateCheckResult
}

sealed interface UpdateValidationResult {
    data object Valid : UpdateValidationResult
    data class Invalid(val message: String) : UpdateValidationResult
}

enum class UpdateCheckInterval(
    val preferenceValue: String,
    val displayName: String,
    val intervalMillis: Long?
) {
    DISABLED("off", "사용 안 함", null),
    HOURS_12("12h", "12시간마다", 12L * 60L * 60L * 1000L),
    HOURS_24("24h", "24시간마다", 24L * 60L * 60L * 1000L),
    DAYS_3("3d", "3일마다", 3L * 24L * 60L * 60L * 1000L),
    DAYS_7("7d", "7일마다", 7L * 24L * 60L * 60L * 1000L);

    companion object {
        fun fromPreference(value: String?): UpdateCheckInterval =
            entries.firstOrNull { it.preferenceValue == value } ?: HOURS_24
    }
}
