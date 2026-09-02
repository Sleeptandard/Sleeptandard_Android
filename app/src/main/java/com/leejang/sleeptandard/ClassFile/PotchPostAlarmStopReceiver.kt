package com.leejang.sleeptandard.ClassFile

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.leejang.sleeptandard.Potch.PotchBleForegroundService

/** 알람 해제 후 5분간 데이터를 더 수집한 뒤 로그를 저장하고 Potch 연결을 종료한다. */
class PotchPostAlarmStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val token = intent.getLongExtra(EXTRA_STOP_TOKEN, 0L)
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val scheduledToken = preferences.getLong(KEY_STOP_TOKEN, 0L)

        if (token <= 0L || token != scheduledToken) {
            Log.w(
                WTF_TAG,
                "알람 후 Potch 종료 요청 무시: token=$token, scheduledToken=$scheduledToken"
            )
            return
        }

        preferences.edit {
            remove(KEY_STOP_TOKEN)
            remove(KEY_STOP_AT_MILLIS)
        }

        Log.i(WTF_TAG, "알람 해제 후 5분 경과: Potch 로그 저장 및 연결 종료 요청")
        val serviceIntent = Intent(context, PotchBleForegroundService::class.java).apply {
            action = PotchBleForegroundService.ACTION_STOP_AND_SAVE
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    companion object {
        const val POST_ALARM_COLLECTION_MILLIS = 5 * 60_000L

        private const val ACTION_STOP_AFTER_ALARM =
            "com.leejang.sleeptandard.action.STOP_POTCH_AFTER_ALARM"
        private const val EXTRA_STOP_TOKEN = "extra_stop_token"
        private const val PREFS_NAME = "potch_post_alarm_stop"
        private const val KEY_STOP_TOKEN = "stop_token"
        private const val KEY_STOP_AT_MILLIS = "stop_at_millis"
        private const val REQUEST_CODE = 200_001
        private const val WTF_TAG = "WTF"

        fun schedule(context: Context, alarmId: Int) {
            val appContext = context.applicationContext
            val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val token = System.currentTimeMillis()
            val stopAtMillis = token + POST_ALARM_COLLECTION_MILLIS
            val triggerAtElapsed = SystemClock.elapsedRealtime() + POST_ALARM_COLLECTION_MILLIS

            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
                putLong(KEY_STOP_TOKEN, token)
                putLong(KEY_STOP_AT_MILLIS, stopAtMillis)
            }

            val pendingIntent = requireNotNull(
                pendingIntent(
                    context = appContext,
                    token = token,
                    flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()
            ) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtElapsed,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtElapsed,
                    pendingIntent
                )
            }

            Log.i(
                WTF_TAG,
                "알람 해제 후 Potch 종료 예약: alarmId=$alarmId, " +
                    "delayMillis=$POST_ALARM_COLLECTION_MILLIS, stopAtMillis=$stopAtMillis"
            )
        }

        fun cancelScheduledStop(context: Context) {
            val appContext = context.applicationContext
            val existing = pendingIntent(
                context = appContext,
                token = 0L,
                flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (existing != null) {
                val alarmManager =
                    appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.cancel(existing)
                existing.cancel()
            }
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
                remove(KEY_STOP_TOKEN)
                remove(KEY_STOP_AT_MILLIS)
            }
        }

        private fun pendingIntent(
            context: Context,
            token: Long,
            flags: Int,
        ): PendingIntent? = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, PotchPostAlarmStopReceiver::class.java).apply {
                action = ACTION_STOP_AFTER_ALARM
                putExtra(EXTRA_STOP_TOKEN, token)
            },
            flags
        )
    }
}
