package com.seolhwa.armyrist.notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.seolhwa.armyrist.ArmyristApplication
import com.seolhwa.armyrist.stage2.data.CoreSuiteRepository
class ChecklistActionReceiver: BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        val listId=intent.getStringExtra(ChecklistNotificationManager.EXTRA_CHECKLIST_ID)?.takeIf{it.isNotBlank()}?:return
        val itemId=intent.getStringExtra(ChecklistNotificationManager.EXTRA_ITEM_ID)?.takeIf{it.isNotBlank()}?:return
        when(intent.action){
            ACTION_COMPLETE->{
                val repo=(context.applicationContext as? ArmyristApplication)?.coreSuiteRepository?:CoreSuiteRepository(context)
                if(!repo.completeItemFromNotification(listId,itemId))Toast.makeText(context,"완료를 저장하지 못했습니다. 체크리스트를 확인해주세요.",Toast.LENGTH_LONG).show()
            }
            ACTION_STOP->ChecklistMusicAlarmService.stopIfActive(listId,itemId)
        }
    }
    companion object{
        const val ACTION_COMPLETE="com.seolhwa.armyrist.CHECKLIST_COMPLETE"
        const val ACTION_STOP="com.seolhwa.armyrist.CHECKLIST_STOP"
    }
}
