package com.seolhwa.armyrist.notification

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.seolhwa.armyrist.timeplan.v3.domain.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Local-only diagnostics; stores no action names or plan contents. */
internal object TimePlanAlarmDiagnostics {
    const val EXTRA_TEST = "timeplanAlarmTest"
    private const val TEST_ID = 0x713991
    private const val PREFS = "timeplan_alarm_diagnostics"

    fun record(context: Context, result: String) {
        context.getSharedPreferences(PREFS, 0).edit()
            .putString("last", "${LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM.dd HH:mm:ss"))} · $result").apply()
    }

    fun report(context: Context, plan: DateAwareTimePlan): String {
        TimePlanActionNotificationManager.createChannel(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        val audio = context.getSystemService(AudioManager::class.java)
        val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            TimePlanActionNotificationManager.notificationPermissionGranted(context)
        val actions = UnifiedTimePlan.actions(plan).filter { it.notificationMode != ActionNotificationMode.NONE && it.completionState == ActionCompletionState.INCOMPLETE }
        val upcoming = actions.filter { it.scheduledDateTime > LocalDateTime.now() }
        val simple = if(Build.VERSION.SDK_INT>=26) nm.getNotificationChannel(TimePlanActionNotificationManager.channelId(TimePlanActionItem(content="알림 점검",scheduledDateTime=LocalDateTime.now(),notificationMode=ActionNotificationMode.SIMPLE))) else null
        val music = if(Build.VERSION.SDK_INT>=26) nm.getNotificationChannel(TimePlanActionNotificationManager.musicChannelId()) else null
        return listOf(
            "앱 알림: ${if(enabled) "허용" else "차단됨 · 알림 설정에서 허용 필요"}",
            "정확한 알람: ${if(TimePlanActionNotificationManager.exactAlarmAvailable(context)) "허용" else "미허용 · 절전 중 지연 가능"}",
            "계획 날짜: ${if(plan.dateDisplayMode==TimePlanDateDisplayMode.RELATIVE_D_DAY) "날짜 미설정(D-Day) · 날짜 지정 필요" else "날짜 지정됨"}",
            "남은 알림: ${upcoming.size}개 / 미완료 알림 ${actions.size}개",
            upcoming.minByOrNull { it.scheduledDateTime }?.let { "다음 알림: ${it.scheduledDateTime.format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"))}" } ?: "앞으로 예약된 알림 없음",
            "간단한 알림 채널: ${if(Build.VERSION.SDK_INT<26) "시스템 알림 설정 사용" else if(simple?.importance==NotificationManager.IMPORTANCE_NONE) "차단됨" else if(simple?.sound==null) "소리 없음" else "소리 설정됨"}",
            "음악 알림 채널: ${if(music?.importance==NotificationManager.IMPORTANCE_NONE) "차단됨" else "허용"}",
            "알림 음량: ${audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION)} / ${audio.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)}",
            "알람 음량: ${audio.getStreamVolume(AudioManager.STREAM_ALARM)} / ${audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)}",
            "방해 금지: ${if(nm.currentInterruptionFilter==NotificationManager.INTERRUPTION_FILTER_ALL) "해제" else "설정 확인 필요"}",
            "최근 처리: ${context.getSharedPreferences(PREFS,0).getString("last","기록 없음")}",
            "앱 버전: ${context.packageManager.getPackageInfo(context.packageName,0).versionName}"
        ).joinToString("\n\n")
    }

    fun scheduleTest(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(context, TEST_ID, Intent(context, TimePlanActionAlarmReceiver::class.java).apply {
            action = "com.seolhwa.armyrist.TIMEPLAN_TEST_ALARM"
            putExtra(EXTRA_TEST,true)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val at = System.currentTimeMillis()+10000
        try {
            if(TimePlanActionNotificationManager.exactAlarmAvailable(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pending)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pending)
        } catch (_: SecurityException) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pending) }
        record(context,"시험 알람 예약 · 10초 뒤${if(TimePlanActionNotificationManager.exactAlarmAvailable(context)) "" else " (권한 미허용으로 지연 가능)"}")
    }

    fun showTestNotification(context: Context) {
        if(!TimePlanActionNotificationManager.notificationPermissionGranted(context)) {
            record(context,"시험 알림 차단 · 알림 권한 없음")
            return
        }
        TimePlanActionNotificationManager.createChannel(context)
        val channel=TimePlanActionNotificationManager.channelId(TimePlanActionItem(content="알림 점검",scheduledDateTime=LocalDateTime.now(),notificationMode=ActionNotificationMode.SIMPLE))
        context.getSystemService(NotificationManager::class.java).notify(TEST_ID,
            NotificationCompat.Builder(context,channel).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("시간계획 시험 알림").setContentText("예약 알람이 휴대폰에 전달됐습니다.")
                .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build())
        record(context,"시험 알림 표시 요청 · 무음이면 알림 채널/음량 확인")
    }
}
