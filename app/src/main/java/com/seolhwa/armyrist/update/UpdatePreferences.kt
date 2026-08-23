package com.seolhwa.armyrist.update

import android.content.Context

class UpdatePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    var interval: UpdateCheckInterval
        get() = UpdateCheckInterval.fromPreference(prefs.getString(KEY_INTERVAL, null))
        set(value) {
            prefs.edit().putString(KEY_INTERVAL, value.preferenceValue).apply()
        }

    val lastSuccessfulCheck: Long
        get() = prefs.getLong(KEY_LAST_SUCCESS, 0L)

    fun markSuccessfulCheck(atMillis: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_SUCCESS, atMillis).apply()
    }

    fun isAutomaticCheckDue(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val duration = interval.intervalMillis ?: return false
        val last = lastSuccessfulCheck
        return last <= 0L || nowMillis - last >= duration
    }

    fun saveKnownAvailable(release: UpdateReleaseMetadata) {
        prefs.edit().putString(KEY_KNOWN_AVAILABLE, release.toCachedJson()).apply()
    }

    fun clearKnownAvailable() {
        prefs.edit().remove(KEY_KNOWN_AVAILABLE).apply()
    }

    fun knownAvailable(installedVersionCode: Long): UpdateReleaseMetadata? {
        val raw = prefs.getString(KEY_KNOWN_AVAILABLE, null) ?: return null
        val parsed = UpdateReleaseMetadata.fromCachedJson(raw) ?: run {
            clearKnownAvailable()
            return null
        }
        if (parsed.versionCode <= installedVersionCode) {
            clearKnownAvailable()
            return null
        }
        return parsed
    }

    companion object {
        private const val PREFS_NAME = "armyrist_update_settings_v1"
        private const val KEY_INTERVAL = "updateCheckInterval"
        private const val KEY_LAST_SUCCESS = "lastSuccessfulUpdateCheck"
        private const val KEY_KNOWN_AVAILABLE = "knownAvailableRelease"
    }
}
