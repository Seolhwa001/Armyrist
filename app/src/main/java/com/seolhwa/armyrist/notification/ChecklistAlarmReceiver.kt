package com.seolhwa.armyrist.notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.seolhwa.armyrist.stage2.data.CoreSuiteRepository
import com.seolhwa.armyrist.stage2.domain.ChecklistStatus
import com.seolhwa.armyrist.timeplan.v3.domain.ActionNotificationMode
class ChecklistAlarmReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        val listId=intent.getStringExtra(ChecklistNotificationManager.EXTRA_CHECKLIST_ID)?:return
        val itemId=intent.getStringExtra(ChecklistNotificationManager.EXTRA_ITEM_ID)?:return
        val list=CoreSuiteRepository(context.applicationContext).getChecklist(listId)?:return
        val item=list.items.find{it.id==itemId}?:return
        if(item.status!=ChecklistStatus.INCOMPLETE||!item.notificationEnabled||item.notificationMode==ActionNotificationMode.NONE||!ChecklistNotificationManager.notificationsEnabled(context,item))return
        if(item.notificationMode==ActionNotificationMode.MUSIC && ChecklistNotificationManager.startMusicAlarm(context,listId,itemId))return
        val reminderItem=if(item.notificationMode==ActionNotificationMode.MUSIC)item.copy(notificationMode=ActionNotificationMode.SIMPLE)else item
        context.getSystemService(NotificationManager::class.java).notify(ChecklistNotificationManager.notificationId(item.id),
            TaskReminderNotification.build(context,ChecklistNotificationManager.createChannel(context,reminderItem),item.name,"${list.title} · ${ChecklistNotificationManager.scheduledLabel(item)} 예정",
                ChecklistNotificationManager.openChecklistIntent(context,list.id,item.id),ChecklistNotificationManager.completePendingIntent(context,list.id,item.id)))
    }
}
