package com.leejang.sleeptandard.ClassFile

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.leejang.sleeptandard.Potch.AlarmLogPhase
import com.leejang.sleeptandard.Potch.AlarmLogSession
import com.leejang.sleeptandard.Potch.AlarmLogSessionStore
import com.leejang.sleeptandard.Potch.PotchBleForegroundService

/** Each dismissed alarm owns its deadline; an old alarm cannot close a new alarm's files. */
class PotchPostAlarmStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_LOG_SESSION_ID) ?: return
        val session = AlarmLogSessionStore(context).load().find { it.id == id } ?: return
        if (session.phase != AlarmLogPhase.POST_ALARM) return
        if (System.currentTimeMillis() < session.rawStopAtMillis) {
            arm(context, session)
            return
        }
        ContextCompat.startForegroundService(context, Intent(context, PotchBleForegroundService::class.java).apply {
            action = PotchBleForegroundService.ACTION_FINISH_POST_ALARM_LOGGING
            putExtra(EXTRA_LOG_SESSION_ID, id)
        })
    }

    companion object {
        const val EXTRA_LOG_SESSION_ID = "extra_log_session_id"
        const val POST_ALARM_COLLECTION_MILLIS = AlarmLogSession.POST_ALARM_MILLIS

        fun schedule(context: Context, alarmId: Int, sessionId: String?) {
            val store = AlarmLogSessionStore(context)
            val id = sessionId ?: store.load().lastOrNull {
                it.alarmId == alarmId && it.phase == AlarmLogPhase.RINGING
            }?.id ?: return
            val session = store.dismiss(id) ?: return
            if (session.phase != AlarmLogPhase.POST_ALARM) return
            PotchBleForegroundService.requestSyncAlarmLogging(context)
            arm(context, session)
        }

        fun arm(context: Context, session: AlarmLogSession) {
            val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, PotchPostAlarmStopReceiver::class.java).apply {
                action = "com.leejang.sleeptandard.action.STOP_POTCH_AFTER_ALARM"
                data = Uri.parse("potch-log://post-alarm/${session.id}")
                putExtra(EXTRA_LOG_SESSION_ID, session.id)
            }
            val pending = PendingIntent.getBroadcast(context, 200_001, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val trigger = SystemClock.elapsedRealtime() +
                (session.rawStopAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pending)
            }
        }
    }
}
