package com.leejang.sleeptandard.ClassFile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.leejang.sleeptandard.Potch.PotchBleForegroundService
import com.leejang.sleeptandard.Prefs.AlarmPreferences

/** Starts Potch acquisition when the fixed 15-minute smart-alarm window opens. */
class PotchAlarmMonitorReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, 0)
        val targetTimeMillis =
            intent.getLongExtra(AlarmScheduler.EXTRA_TARGET_TIME_MILLIS, 0L)

        val preferences = AlarmPreferences(context)
        Log.i(
            "WTF",
            "PotchAlarmMonitorReceiver.onReceive: alarmId=$alarmId, " +
                "targetTimeMillis=$targetTimeMillis, hasAlarm=${preferences.isAlarmSet()}, " +
                "savedTarget=${preferences.getScheduledTriggerTimeMillis()}"
        )
        if (
            !preferences.isAlarmSet() ||
            targetTimeMillis <= 0L ||
            preferences.getScheduledTriggerTimeMillis() != targetTimeMillis
        ) {
            Log.w("WTF", "Potch 모니터링 시작 요청 무시: 예약 상태 불일치")
            return
        }

        startMonitoring(context, alarmId, targetTimeMillis)
    }

    companion object {
        fun startMonitoring(context: Context, alarmId: Int, targetTimeMillis: Long) {
            val serviceIntent = Intent(context, PotchBleForegroundService::class.java).apply {
                action = PotchBleForegroundService.ACTION_START_ALARM_MONITORING
                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmScheduler.EXTRA_TARGET_TIME_MILLIS, targetTimeMillis)
            }
            ContextCompat.startForegroundService(context, serviceIntent)
            Log.i(
                "WTF",
                "Potch 모니터링 ForegroundService 시작 요청: " +
                    "alarmId=$alarmId, targetTimeMillis=$targetTimeMillis"
            )
        }
    }
}
