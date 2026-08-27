package com.leejang.sleeptandard.ClassFile

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.leejang.sleeptandard.Potch.PotchBleForegroundService
import com.leejang.sleeptandard.Prefs.AlarmPreferences
import java.util.Calendar

/**
 * One user alarm is represented by two exact alarms:
 * 1. start Potch monitoring 15 minutes before the requested time;
 * 2. ring at the requested time as a fail-safe.
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private var triggerTime: Long = 0L

    fun getTriggerTime(): Long = triggerTime

    fun getAlarmManager(): AlarmManager = alarmManager

    fun schedule(alarm: Alarm) {
        val preferences = AlarmPreferences(context)
        val previousTarget = preferences.getScheduledTriggerTimeMillis()
        cancelPendingIntents(alarm.id)
        PotchBleForegroundService.requestStopAlarmMonitoring(context, previousTarget)

        val targetTime = calculateNextTriggerTime(alarm)
        preferences.saveAlarm(alarm, targetTime)

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            targetTime,
            targetPendingIntent(alarm)
        )

        val monitoringStartTime = targetTime - MONITORING_WINDOW_MILLIS
        if (monitoringStartTime <= System.currentTimeMillis()) {
            PotchAlarmMonitorReceiver.startMonitoring(
                context = context,
                alarmId = alarm.id,
                targetTimeMillis = targetTime
            )
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                monitoringStartTime,
                monitoringPendingIntent(alarm.id, targetTime)
            )
        }
    }

    fun calculateNextTriggerTime(alarm: Alarm): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            var hour24 = alarm.hour % 12
            if (!alarm.isAm) hour24 += 12
            set(Calendar.HOUR_OF_DAY, hour24)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (target.before(now)) {
            target.add(Calendar.DAY_OF_MONTH, 1)
        }

        triggerTime = target.timeInMillis
        return triggerTime
    }

    /** Cancels both the target-time alarm and the 15-minute monitoring alarm. */
    fun cancel(alarm: Alarm) {
        val preferences = AlarmPreferences(context)
        val targetTime = preferences.getScheduledTriggerTimeMillis()
        cancelPendingIntents(alarm.id)
        preferences.clearScheduledTriggerTime()
        PotchBleForegroundService.requestStopAlarmMonitoring(context, targetTime)
    }

    /**
     * Called by the Potch service after a score strictly greater than 80 is observed.
     * The scheduled alarms are removed before the normal alarm receiver is invoked.
     */
    fun triggerFromPotch(alarm: Alarm, targetTimeMillis: Long) {
        cancelPendingIntents(alarm.id)
        AlarmPreferences(context).clearScheduledTriggerTime()
        PotchBleForegroundService.requestStopAlarmMonitoring(context, targetTimeMillis)
        context.sendBroadcast(createRingIntent(context, alarm))
    }

    /** Cleanup used when the target-time fallback alarm actually fires. */
    fun completeTriggeredAlarm(alarmId: Int, targetTimeMillis: Long) {
        cancelPendingIntents(alarmId)
        AlarmPreferences(context).clearScheduledTriggerTime()
        PotchBleForegroundService.requestStopAlarmMonitoring(context, targetTimeMillis)
    }

    private fun cancelPendingIntents(alarmId: Int) {
        targetPendingIntent(alarmId)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
        monitoringPendingIntent(alarmId)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    private fun targetPendingIntent(alarm: Alarm): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            alarm.id,
            createRingIntent(context, alarm).apply {
                putExtra(EXTRA_TARGET_TIME_MILLIS, triggerTime)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun targetPendingIntent(alarmId: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            alarmId,
            Intent(context, AlarmReceiver::class.java).apply { action = ACTION_RING_ALARM },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

    private fun monitoringPendingIntent(alarmId: Int, targetTimeMillis: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            monitoringRequestCode(alarmId),
            Intent(context, PotchAlarmMonitorReceiver::class.java).apply {
                action = ACTION_START_POTCH_MONITORING
                putExtra(EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_TARGET_TIME_MILLIS, targetTimeMillis)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun monitoringPendingIntent(alarmId: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            monitoringRequestCode(alarmId),
            Intent(context, PotchAlarmMonitorReceiver::class.java).apply {
                action = ACTION_START_POTCH_MONITORING
            },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

    companion object {
        const val MONITORING_WINDOW_MINUTES = 15
        const val POTCH_SCORE_THRESHOLD = 80.0
        const val ACTION_RING_ALARM = "com.leejang.sleeptandard.action.RING_ALARM"
        const val ACTION_START_POTCH_MONITORING =
            "com.leejang.sleeptandard.action.START_POTCH_ALARM_MONITORING"
        const val EXTRA_ALARM_ID = "alarmId"
        const val EXTRA_TARGET_TIME_MILLIS = "targetTimeMillis"
        const val EXTRA_RINGTONE_URI = "ringtoneUri"
        const val EXTRA_VOLUME = "volume"
        const val EXTRA_VIBRATION_ENABLED = "vibrationEnabled"

        const val MONITORING_WINDOW_MILLIS = MONITORING_WINDOW_MINUTES * 60_000L
        private const val MONITOR_REQUEST_CODE_OFFSET = 100_000

        fun createRingIntent(context: Context, alarm: Alarm): Intent =
            Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_RING_ALARM
                putExtra(EXTRA_ALARM_ID, alarm.id)
                putExtra(EXTRA_RINGTONE_URI, alarm.ringtoneUri)
                putExtra(EXTRA_VOLUME, alarm.volume)
                putExtra(EXTRA_VIBRATION_ENABLED, alarm.vibrationEnabled)
            }

        private fun monitoringRequestCode(alarmId: Int): Int =
            MONITOR_REQUEST_CODE_OFFSET + alarmId
    }
}
