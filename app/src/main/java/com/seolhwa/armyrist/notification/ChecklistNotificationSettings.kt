package com.seolhwa.armyrist.notification

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri

object ChecklistNotificationSettings {
    private const val PREFS = "checklist_notification_settings"
    private const val KEY_SOUND_URI = "sound_uri"
    private const val KEY_VIBRATION = "vibration"

    fun soundUri(context: Context): Uri {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_SOUND_URI, null)

        if (!saved.isNullOrBlank()) {
            return Uri.parse(saved)
        }

        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    fun setSoundUri(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SOUND_URI, uri.toString())
            .apply()
    }

    fun vibrationEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_VIBRATION, true)

    fun setVibrationEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_VIBRATION, enabled)
            .apply()
    }

    fun soundTitle(context: Context): String =
        runCatching {
            RingtoneManager.getRingtone(context, soundUri(context))
                ?.getTitle(context)
        }.getOrNull() ?: "기본 알람음"
}
