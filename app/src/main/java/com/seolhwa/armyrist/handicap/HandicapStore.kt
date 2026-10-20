package com.seolhwa.armyrist.handicap

import android.content.Context

class HandicapStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("killomris_handicap_v1", Context.MODE_PRIVATE)
    fun loadConfig() = HandicapCodec.decode(prefs.getString("config", null))
    fun saveConfig(value: HandicapConfig) = prefs.edit().putString("config", HandicapCodec.encode(value)).commit()
    fun loadDraft() = HandicapCodec.decodeDraft(prefs.getString("draft", null))
    fun saveDraft(value: SituationDraft) { prefs.edit().putString("draft", HandicapCodec.encodeDraft(value)).apply() }
}
