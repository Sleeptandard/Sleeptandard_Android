package com.example.sleeptandard_mvp_demo.ClassFile

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import java.util.Calendar

class AlarmScheduler(private val context: Context) {

    // 알람 기능 받아오기
    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private var triggerTime : Long = 0

    fun getTriggerTime() :Long{
        return triggerTime
    }

    fun getAlarmManager(): AlarmManager{
        return alarmManager
    }

    fun schedule(alarm: Alarm) {

        // 알람이 실제 울리는 시간 계산
        val triggerTime = calculateNextTriggerTime(alarm)

        // BroadcastReceiver에게 전달할 Intent 정의
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("alarmId", alarm.id)
            putExtra("label", "알람 #${alarm.id}")
            putExtra("ringtoneUri", alarm.ringtoneUri)
            putExtra("volume", alarm.volume) // ✅ 볼륨 값 추가 전달
            putExtra("vibrationEnabled", alarm.vibrationEnabled)
        }

        // 위에만든 intent를 시스템이 대신 실행해주는 PendingIntent
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id, // 알람마다 다른 requestCode
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE   // 같은 id면 Extras만 업데이트 or 만든 뒤에는 변경 불가
        )

        // WTF: USE_EXACT_ALARM 권한이 있으면 에러가 안뜨네?
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent
        )

    }

    fun calculateNextTriggerTime(alarm: Alarm): Long {
        val now = Calendar.getInstance()

        val cal = Calendar.getInstance().apply {
            // isAm, hour를 24시간제로 변환
            var h = alarm.hour % 12
            if (!alarm.isAm) h += 12
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // TODO: 오늘 시간이 이미 지났으면 경고해야 하지 않을까?
        // 오늘 시간이 이미 지났으면 +1일
        if (cal.before(now)) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        triggerTime = cal.timeInMillis

        return triggerTime
    }

    fun cancel(alarm: Alarm) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

}