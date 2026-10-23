package com.seolhwa.armyrist.notification

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.seolhwa.armyrist.stage2.data.CoreSuiteRepository
import com.seolhwa.armyrist.stage2.domain.ChecklistStatus
import com.seolhwa.armyrist.timeplan.v3.domain.ActionNotificationMode
import java.time.format.DateTimeFormatter

class ChecklistMusicAlarmService : Service() {
    private var player: MediaPlayer? = null
    private var activeChecklistId: String? = null
    private var activeItemId: String? = null
    private var activeSchedule: String? = null

    override fun onCreate() {
        super.onCreate()
        runningService = this
        // Item-specific channels are created after loading the requested item.
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                val planId = intent.getStringExtra(ChecklistNotificationManager.EXTRA_CHECKLIST_ID)
                val actionId = intent.getStringExtra(ChecklistNotificationManager.EXTRA_ITEM_ID)

                if (
                    planId == null || actionId == null ||
                    (planId == activeChecklistId && actionId == activeItemId)
                ) {
                    stopAlarm()
                }
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val planId =
                    intent.getStringExtra(ChecklistNotificationManager.EXTRA_CHECKLIST_ID)
                        ?: return rejectStart()
                val actionId =
                    intent.getStringExtra(ChecklistNotificationManager.EXTRA_ITEM_ID)
                        ?: return rejectStart()

                val repository = CoreSuiteRepository(applicationContext)
                val plan = repository.getChecklist(planId) ?: return rejectStart()
                val action = plan.items.firstOrNull { it.id == actionId }
                    ?: return rejectStart()

                if (
                    action.notificationMode != ActionNotificationMode.MUSIC ||
                    action.status != ChecklistStatus.INCOMPLETE ||
                    !action.notificationEnabled || !ChecklistNotificationManager.notificationsEnabled(this, action)
                ) {
                    return rejectStart()
                }

                if(activeItemId!=null && activeItemId!=actionId){
                    player?.release();player=null
                    getSystemService(android.app.NotificationManager::class.java).cancel(ChecklistNotificationManager.notificationId(activeItemId!!))
                }
                activeSchedule="${action.scheduledDate}:${action.scheduledTimeMinutes}"
                activeChecklistId = planId
                activeItemId = actionId

                val notification = buildNotification(this, plan, action)

                startForeground(
                    ChecklistNotificationManager.notificationId(action.id),
                    notification
                )
                runCatching { startLoopingAlarm(action) }.onFailure {
                    android.widget.Toast.makeText(this,"알람음을 재생하지 못했습니다. 알람음을 다시 선택해주세요.",android.widget.Toast.LENGTH_LONG).show();stopAlarm()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun rejectStart():Int {
        if(activeItemId==null)stopSelf()
        return START_NOT_STICKY
    }

    private fun startLoopingAlarm(item:com.seolhwa.armyrist.stage2.domain.ChecklistItem) {
        if (player?.isPlaying == true) return

        player?.release()
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setDataSource(applicationContext, ChecklistNotificationManager.soundUri(this@ChecklistMusicAlarmService,item))
            isLooping = true
            prepare()
            start()
        }
    }

    private fun stopAlarm() {
        player?.runCatching {
            if (isPlaying) stop()
        }
        player?.release()
        player = null
        activeChecklistId = null
        activeItemId = null
        activeSchedule = null
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        if (runningService === this) runningService = null
        player?.release()
        player = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        internal fun buildNotification(context:android.content.Context,plan:com.seolhwa.armyrist.stage2.domain.Checklist,action:com.seolhwa.armyrist.stage2.domain.ChecklistItem):Notification =
            TaskReminderNotification.build(context,ChecklistNotificationManager.createChannel(context,action),action.name,"${plan.title} · ${ChecklistNotificationManager.scheduledLabel(action)} 예정",
                ChecklistNotificationManager.openChecklistIntent(context,plan.id,action.id),ChecklistNotificationManager.completePendingIntent(context,plan.id,action.id),ChecklistNotificationManager.stopPendingIntent(context,plan.id,action.id))

        @Volatile private var runningService: ChecklistMusicAlarmService? = null

        internal fun isActive(item:com.seolhwa.armyrist.stage2.domain.ChecklistItem):Boolean {
            val service=runningService
            return service?.activeItemId==item.id && service.activeSchedule=="${item.scheduledDate}:${item.scheduledTimeMinutes}"
        }
        internal fun stopItemIfActive(itemId:String) {
            android.os.Handler(android.os.Looper.getMainLooper()).post { val service=runningService;if(service?.activeItemId==itemId)service.stopAlarm() }
        }
        internal fun stopIfActive(planId: String, actionId: String) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                val service = runningService
                if (service != null && service.activeChecklistId == planId && service.activeItemId == actionId) {
                    service.stopAlarm()
                }
            }
        }

        const val ACTION_START =
            "com.seolhwa.armyrist.CHECKLIST_MUSIC_ALARM_START"
        const val ACTION_STOP =
            "com.seolhwa.armyrist.CHECKLIST_MUSIC_ALARM_STOP"
    }
}
