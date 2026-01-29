package com.leejang.sleeptandard_mvp.Prefs

import android.content.Context
import com.leejang.sleeptandard_mvp.ClassFile.Alarm

class AlarmPreferences(private val context: Context) {

    private val prefs = context.getSharedPreferences("alarm_prefs", Context.MODE_PRIVATE)

    fun saveAlarm(alarm: Alarm) {
        prefs.edit()
            .putBoolean("hasAlarm", true)
            .putInt("hour", alarm.hour)
            .putInt("minute", alarm.minute)
            .putBoolean("isAm", alarm.isAm)
            .putString("ringtoneUri", alarm.ringtoneUri)
            .putInt("volume", alarm.volume) // ✅ 저장 추가
            .putBoolean("vibrationEnabled", alarm.vibrationEnabled)
            .apply()
    }

    fun loadAlarm(): Alarm {
        return Alarm(
            id = 1,
            hour = prefs.getInt("hour", 8),
            minute = prefs.getInt("minute", 30),
            isAm = prefs.getBoolean("isAm", true),
            ringtoneUri = prefs.getString("ringtoneUri", "") ?: "",
            volume = prefs.getInt("volume", 5), // ✅ 불러오기 추가
            vibrationEnabled = prefs.getBoolean("vibrationEnabled", true)
        )
    }

    fun clearAlarm(){
        prefs.edit()
            .putBoolean("hasAlarm", false)
            .putInt("hour", 8)
            .putInt("minute", 30)
            .putBoolean("isAm", true)
            .apply()
    }

    /** 알람 설정 기억해놓기 위해서 위에거로 바꿈  10.23 **/
    /*
    fun clearAlarm() {
        prefs.edit()
            .clear()
            .apply()
    }
     */

    fun isAlarmSet(): Boolean {
        return prefs.getBoolean("hasAlarm", false)
    }
}