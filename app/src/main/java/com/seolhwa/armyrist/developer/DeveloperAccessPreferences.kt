package com.seolhwa.armyrist.developer

import android.content.Context

/**
 * Convenience gate for developer/test utilities.
 *
 * This is intentionally NOT a security/authentication boundary. The unlock code
 * exists only to keep developer-only controls out of the normal user flow.
 */
class DeveloperAccessPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    var unlocked: Boolean
        get() = prefs.getBoolean(KEY_UNLOCKED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_UNLOCKED, value).apply()
        }

    fun unlockWith(code: String): Boolean {
        val success = code.trim() == DEVELOPER_CODE
        if (success) unlocked = true
        return success
    }

    fun lock() {
        unlocked = false
    }

    companion object {
        const val DEVELOPER_CODE = "7103"

        private const val PREFS_NAME = "armyrist_developer_access_v1"
        private const val KEY_UNLOCKED = "developerMenuUnlocked"
    }
}
