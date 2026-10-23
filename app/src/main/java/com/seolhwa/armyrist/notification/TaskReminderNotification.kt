package com.seolhwa.armyrist.notification
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat

/** Shared presentation and completion controls for checklist and TimePlan reminders. */
internal object TaskReminderNotification {
    fun build(context:Context,channel:String,title:String,subtitle:String,open:PendingIntent,complete:PendingIntent,stop:PendingIntent?=null):Notification =
        NotificationCompat.Builder(context,channel)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title).setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(title).bigText(subtitle))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if(stop==null)NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setWhen(System.currentTimeMillis()).setShowWhen(true)
            .setOngoing(stop!=null).setAutoCancel(stop==null).setContentIntent(open)
            .addAction(android.R.drawable.checkbox_on_background,"할 일 완료",complete)
            .apply{if(stop!=null)addAction(android.R.drawable.ic_media_pause,"알림 중지",stop)}.build()
}
