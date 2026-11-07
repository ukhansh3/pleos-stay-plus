package com.pleosstay.app.rest

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import com.pleosstay.app.MainActivity
import com.pleosstay.app.R
import com.pleosstay.app.restRuntime

internal interface RestAlerts {
    val playback: kotlinx.coroutines.flow.StateFlow<RestPlayback>? get() = null
    fun mode(style: RestAlert): RestAlarmMode =
        if (problem(style) == null) RestAlarmMode.SYSTEM_EXACT else RestAlarmMode.UNAVAILABLE
    fun foregroundProblem(style: RestAlert): String? = "앱 실행 중 알람을 사용할 수 없어요."
    fun problem(style: RestAlert): String?
    fun soundProblem(style: RestAlert): String?
    fun schedule(session: RestSession)
    fun cancel(session: RestSession)
    fun sound(session: RestSession, reason: RestEnd)
    fun silence()
}

internal class RestAlarm(private val context: Context) : RestAlerts {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    private val direct = RestAlarmPlayer(context)
    override val playback = direct.state

    override fun mode(style: RestAlert): RestAlarmMode = when {
        problem(style) == null -> RestAlarmMode.SYSTEM_EXACT
        foregroundProblem(style) == null -> RestAlarmMode.FOREGROUND_ONLY
        else -> RestAlarmMode.UNAVAILABLE
    }

    override fun foregroundProblem(style: RestAlert): String? = direct.problem(style)

    override fun problem(style: RestAlert): String? {
        if (Build.VERSION.SDK_INT >= 31 && !alarms.canScheduleExactAlarms()) return "알람 및 리마인더 권한을 허용해 주세요."
        return soundProblem(style)
    }

    override fun soundProblem(style: RestAlert): String? {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return "휴식 알람을 받으려면 알림 권한을 허용해 주세요."
        }
        if (!notifications.areNotificationsEnabled()) return "앱 알림을 설정에서 켜 주세요."
        val channel = channel(style)
        return if (channel.importance < NotificationManager.IMPORTANCE_DEFAULT || channel.sound == null)
            "휴식 알람 채널의 소리를 설정에서 켜 주세요." else null
    }

    override fun schedule(session: RestSession) {
        check(session.endsAt > System.currentTimeMillis()) { "휴식 종료 예정 시각이 지났습니다." }
        if (session.alarmMode == RestAlarmMode.FOREGROUND_ONLY) {
            foregroundProblem(session.preset.alert)?.let { error(it) }
            return // The controller owns the live deadline; no OS reservation is claimed.
        }
        check(session.alarmMode == RestAlarmMode.SYSTEM_EXACT)
        problem(session.preset.alert)?.let { error(it) }
        val intent = pending(ACTION_TIME, session.alarmToken)
        try {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, session.endsAt, intent)
        } catch (error: Exception) {
            intent.cancel()
            throw error
        }
    }

    override fun cancel(session: RestSession) {
        if (session.alarmMode != RestAlarmMode.SYSTEM_EXACT) return
        val intent = PendingIntent.getBroadcast(context, 0, intent(ACTION_TIME, session.alarmToken),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) ?: return
        alarms.cancel(intent)
        intent.cancel()
    }

    override fun sound(session: RestSession, reason: RestEnd) {
        if (session.alarmMode == RestAlarmMode.FOREGROUND_ONLY) {
            direct.play(session.preset.alert)
            return
        }
        val channel = channel(session.preset.alert)
        val open = PendingIntent.getActivity(context, 4, Intent(context, MainActivity::class.java)
            .putExtra("openRest", true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        notifications.notify(NOTIFICATION_ID, Notification.Builder(context, channel.id)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(reason.label).setContentText(session.preset.name)
            .setCategory(Notification.CATEGORY_ALARM).setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "알람 정지", pending(ACTION_STOP, session.alarmToken)).build())
            .setTimeoutAfter(30_000).build())
    }

    override fun silence() {
        direct.stop()
        notifications.cancel(NOTIFICATION_ID)
    }

    private fun channel(style: RestAlert): NotificationChannel {
        val id = "rest-" + style.name.lowercase()
        val resource = if (style == RestAlert.SOFT) R.raw.rest_soft else R.raw.rest_wake
        notifications.createNotificationChannel(NotificationChannel(id, style.label, NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(Uri.parse("android.resource://${context.packageName}/raw/${context.resources.getResourceEntryName(resource)}"),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        })
        return notifications.getNotificationChannel(id)
    }

    private fun intent(action: String, token: String) = Intent(context, RestAlarmReceiver::class.java)
        .setAction(action).setData(Uri.Builder().scheme("stay-rest").authority("alarm").appendPath(token).build())

    private fun pending(action: String, token: String): PendingIntent =
        PendingIntent.getBroadcast(context, 0, intent(action, token), PendingIntent.FLAG_IMMUTABLE)

    companion object {
        const val ACTION_TIME = "com.pleosstay.app.rest.TIME"
        const val ACTION_STOP = "com.pleosstay.app.rest.STOP"
        private const val NOTIFICATION_ID = 426
    }
}

class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val token = intent.data?.lastPathSegment ?: return
        val pending = goAsync()
        try {
            restRuntime(context).receive(intent.action, token) { pending.finish() }
        } catch (_: Exception) {
            pending.finish()
        }
    }
}
