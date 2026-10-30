package com.seolhwa.armyrist

import android.content.Context

internal class ChecklistViewPreferences(context:Context) {
    private val prefs=context.applicationContext.getSharedPreferences("checklist_view_preferences",Context.MODE_PRIVATE)
    fun mode(id:String):String = prefs.getString("mode:$id",prefs.getString("default_mode","DETAIL"))
        .takeIf { it=="DETAIL" || it=="COMPACT" } ?: "DETAIL"
    fun setMode(id:String,mode:String):Boolean {
        require(mode in setOf("DETAIL","COMPACT"))
        return prefs.edit().putString("mode:$id",mode).putString("default_mode",mode).commit()
    }
}
