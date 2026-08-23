package com.seolhwa.armyrist.notification

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.seolhwa.armyrist.timeplan.v3.data.DateAwareTimePlanRepository
import com.seolhwa.armyrist.timeplan.v3.domain.ActionCompletionState
import com.seolhwa.armyrist.timeplan.v3.domain.ActionNotificationMode
import java.time.format.DateTimeFormatter

class TimePlanMusicAlarmService : Service() {
    private var player: MediaPlayer? = null
    private var activePlanId: String? = null
    private var activeActionId: String? = null

    override fun onCreate() {
        super.onCreate()
        TimePlanActionNotificationManager.createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                val planId = intent.getStringExtra(TimePlanActionNotificationManager.EXTRA_PLAN_ID)
                val actionId = intent.getStringExtra(TimePlanActionNotificationManager.EXTRA_ACTION_ID)

                if (
                    planId == null || actionId == null ||
                    (planId == activePlanId && actionId == activeActionId)
                ) {
                    stopAlarm()
                }
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val planId =
                    intent.getStringExtra(TimePlanActionNotificationManager.EXTRA_PLAN_ID)
                        ?: return START_NOT_STICKY
                val actionId =
                    intent.getStringExtra(TimePlanActionNotificationManager.EXTRA_ACTION_ID)
                        ?: return START_NOT_STICKY

                val repository = DateAwareTimePlanRepository(applicationContext)
                val plan = repository.getPlan(planId) ?: return START_NOT_STICKY
                val action = plan.actions.firstOrNull { it.id == actionId }
                    ?: return START_NOT_STICKY

                if (
                    action.notificationMode != ActionNotificationMode.MUSIC ||
                    action.completionState != ActionCompletionState.INCOMPLETE ||
                    !TimePlanActionNotificationManager.notificationPermissionGranted(this)
                ) {
                    return START_NOT_STICKY
                }

                activePlanId = planId
                activeActionId = actionId

                val notification = NotificationCompat.Builder(
                    this,
                    TimePlanActionNotificationManager.musicChannelId()
                )
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle(action.content)
                    .setContentText(
                        "${plan.title} · ${
                            action.scheduledDateTime.format(
                                DateTimeFormatter.ofPattern("HH:mm")
                            )
                        } 예정"
                    )
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setContentIntent(
                        TimePlanActionNotificationManager.openActionPendingIntent(
                            this,
                            plan.id,
                            action.id
                        )
                    )
                    .addAction(
                        android.R.drawable.ic_media_pause,
                        "알림 중지",
                        TimePlanActionNotificationManager.stopMusicPendingIntent(
                            this,
                            plan.id,
                            action.id
                        )
                    )
                    .build()

                startForeground(
                    TimePlanActionNotificationManager.notificationId(plan.id, action.id),
                    notification
                )
                startLoopingAlarm()
            }
        }
        return START_NOT_STICKY
    }

    private fun startLoopingAlarm() {
        if (player?.isPlaying == true) return

        player?.release()
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            val alarmUri =
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            setDataSource(applicationContext, alarmUri)
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
        activePlanId = null
        activeActionId = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START =
            "com.seolhwa.armyrist.TIMEPLAN_MUSIC_ALARM_START"
        const val ACTION_STOP =
            "com.seolhwa.armyrist.TIMEPLAN_MUSIC_ALARM_STOP"
    }
}
