package com.seolhwa.armyrist.notification
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.seolhwa.armyrist.*
import com.seolhwa.armyrist.stage2.domain.*
import com.seolhwa.armyrist.stage2.data.CoreSuiteRepository
import com.seolhwa.armyrist.timeplan.v3.domain.ActionNotificationMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=ArmyristApplication::class)
class ChecklistReminderUiTest {
    private val app get()=ApplicationProvider.getApplicationContext<ArmyristApplication>()
    private fun item(mode:ActionNotificationMode):Pair<Checklist,ChecklistItem>{val repo=app.coreSuiteRepository;assertTrue(repo.saveCalendarItem(null,"당직 점검","장비 확인","",true,540,"2030-01-02",mode));val list=repo.getChecklists().first();return list to list.items.single()}
    @Test fun simpleReminderHasCompletionActionAndUsesItemAsTitle(){
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val (list,item)=item(ActionNotificationMode.SIMPLE)
        ChecklistAlarmReceiver().onReceive(app,Intent().putExtra(ChecklistNotificationManager.EXTRA_CHECKLIST_ID,list.id).putExtra(ChecklistNotificationManager.EXTRA_ITEM_ID,item.id))
        val note=shadowOf(app.getSystemService(NotificationManager::class.java)).getNotification(ChecklistNotificationManager.notificationId(item.id))
        assertNotNull(note);assertEquals(item.name,note.extras.getString(Notification.EXTRA_TITLE));assertEquals("할 일 완료",note.actions.single().title)
    }
    @Test fun completionIsIdempotentPersistsAndDoesNotRecreateDeletedItems(){
        val (list,item)=item(ActionNotificationMode.MUSIC);val repo=app.coreSuiteRepository
        val intent=Intent(ChecklistActionReceiver.ACTION_COMPLETE).putExtra(ChecklistNotificationManager.EXTRA_CHECKLIST_ID,list.id).putExtra(ChecklistNotificationManager.EXTRA_ITEM_ID,item.id)
        val receiver=ChecklistActionReceiver();receiver.onReceive(app,intent);receiver.onReceive(app,intent)
        assertEquals(ChecklistStatus.COMPLETE,repo.getChecklist(list.id)!!.items.single().status)
        assertEquals(ChecklistStatus.COMPLETE,CoreSuiteRepository(app).getChecklist(list.id)!!.items.single().status)
        assertEquals(ActionNotificationMode.MUSIC,CoreSuiteRepository(app).getChecklist(list.id)!!.items.single().notificationMode)
        repo.trashChecklistItem(list.id,item.id)
        assertFalse(repo.completeItemFromNotification(list.id,item.id));assertTrue(repo.getChecklist(list.id)!!.items.isEmpty())
    }
    @Test fun musicReminderHasSeparateStopAndCompletionWithoutCompletingOnStop(){
        val (list,item)=item(ActionNotificationMode.MUSIC)
        val note=ChecklistMusicAlarmService.buildNotification(app,list,item)
        assertEquals(listOf("할 일 완료","알림 중지"),note.actions.map{it.title.toString()})
        assertTrue(note.flags and Notification.FLAG_ONGOING_EVENT !=0)
        ChecklistActionReceiver().onReceive(app,Intent(ChecklistActionReceiver.ACTION_STOP).putExtra(ChecklistNotificationManager.EXTRA_CHECKLIST_ID,list.id).putExtra(ChecklistNotificationManager.EXTRA_ITEM_ID,item.id))
        assertEquals(ChecklistStatus.INCOMPLETE,app.coreSuiteRepository.getChecklist(list.id)!!.items.single().status)
        assertNotEquals(ChecklistNotificationManager.completePendingIntent(app,list.id,item.id),ChecklistNotificationManager.stopPendingIntent(app,list.id,item.id))
    }
    @Test fun actionIdentityDoesNotCollideForEqualHashCodes(){
        assertEquals("FB".hashCode(),"Ea".hashCode())
        assertNotEquals(ChecklistNotificationManager.completePendingIntent(app,"list","FB"),ChecklistNotificationManager.completePendingIntent(app,"list","Ea"))
    }
}
