package com.seolhwa.armyrist.calendar
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.UUID
import java.time.LocalDate
import java.time.YearMonth
class CalendarPreferences(private val context: Context) {
    private val prefs=context.getSharedPreferences("killomris_calendar_v1",0)
    fun collapsed(home: Boolean)=prefs.getBoolean(if(home) "homeCollapsed" else "calendarCollapsed",false)
    fun setCollapsed(home: Boolean,value: Boolean)=prefs.edit().putBoolean(if(home) "homeCollapsed" else "calendarCollapsed",value).commit()
    fun dayImage(date: LocalDate)=prefs.getString("day:$date",null)?.takeIf{File(it).isFile}
    fun dayImages(month: YearMonth)=(1..month.lengthOfMonth()).mapNotNull{n->val d=month.atDay(n);dayImage(d)?.let{d to it}}.toMap()
    fun clearDayImage(date: LocalDate)=clearImage("day:$date")
    fun importDayImage(date: LocalDate,uri: Uri)=importImage("day:$date",uri)
    fun cover()=prefs.getString("cover",null)?.takeIf { File(it).isFile }
    fun clearCover()=clearImage("cover")
    private fun clearImage(key: String): Boolean {
        val old=prefs.getString(key,null);if(!prefs.edit().remove(key).commit())return false;old?.let { File(it).delete() };return true
    }
    fun importCover(uri: Uri)=importImage("cover",uri)
    private fun importImage(key: String,uri: Uri): Boolean = runCatching {
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,bounds) }
        require(bounds.outWidth>0 && bounds.outHeight>0);var sample=1
        while(maxOf(bounds.outWidth,bounds.outHeight)/sample>1600)sample*=2
        val image=context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inSampleSize=sample }) } ?: return false
        val file=File(context.filesDir,"calendar-cover-${UUID.randomUUID()}.jpg")
        try {
            file.outputStream().use { require(image.compress(Bitmap.CompressFormat.JPEG,88,it)) }
            val old=prefs.getString(key,null);if(!prefs.edit().putString(key,file.path).commit()){file.delete();return false};old?.let { File(it).delete() }
        } finally { image.recycle() };true
    }.getOrDefault(false)
}
