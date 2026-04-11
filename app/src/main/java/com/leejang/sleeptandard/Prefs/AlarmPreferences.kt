package com.leejang.sleeptandard.Prefs

import android.content.Context
import android.media.RingtoneManager
import com.leejang.sleeptandard.ClassFile.Alarm
import androidx.core.content.edit

class AlarmPreferences(private val context: Context) {

    private val prefs = context.getSharedPreferences("alarm_prefs", Context.MODE_PRIVATE)

    // 앱이 처음 실행되었는지 확인 (기본값 true)
    fun isFirstRun(): Boolean = prefs.getBoolean("is_first_run", true)

    // 튜토리얼을 완료했을 때 호출하여 플래그를 false로 변경
    fun setFirstRunCompleted() {
        prefs.edit { putBoolean("is_first_run", false) }
    }

    fun saveAlarm(alarm: Alarm) {
        prefs.edit {
            putBoolean("hasAlarm", true)
                .putInt("hour", alarm.hour)
                .putInt("minute", alarm.minute)
                .putBoolean("isAm", alarm.isAm)
                .putString("ringtoneUri", alarm.ringtoneUri)
                .putInt("volume", alarm.volume) // ✅ 저장 추가
                .putBoolean("vibrationEnabled", alarm.vibrationEnabled)
                .putInt("earlyWakeUpMinutes", alarm.earlyWakeUpMinutes)
                .putBoolean("isRem", alarm.isRem)
        }
    }

    fun loadAlarm(): Alarm {
        // 1. 시스템 기본 알람음 URI를 가져옵니다. (없을 경우를 대비해 null 체크)
        val defaultRingtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.toString() ?: ""

        return Alarm(
            id = 1,
            hour = prefs.getInt("hour", 8),
            minute = prefs.getInt("minute", 30),
            isAm = prefs.getBoolean("isAm", true),
            ringtoneUri = prefs.getString("ringtoneUri", defaultRingtoneUri) ?: defaultRingtoneUri,
            volume = prefs.getInt("volume", 5), // ✅ 불러오기 추가
            vibrationEnabled = prefs.getBoolean("vibrationEnabled", true),
            earlyWakeUpMinutes = prefs.getInt("earlyWakeUpMinutes", 30)
        )
    }

    fun clearAlarm(){
        prefs.edit {
            putBoolean("hasAlarm", false)
                .putInt("hour", 8)
                .putInt("minute", 30)
                .putBoolean("isAm", true)
        }
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
    /** * 기상 윈도우 튜토리얼 노출 여부를 가져옵니다.
     * 기본값은 true로 설정하여 처음에는 무조건 보이게 합니다.
     */
    fun getShowWindowTutorial(): Boolean {
        return prefs.getBoolean("show_window_tutorial", true)
    }

    /**
     * 기상 윈도우 튜토리얼 노출 여부를 저장합니다.
     * 체크박스 선택 여부에 따라 true 또는 false를 저장합니다.
     */
    fun setShowWindowTutorial(show: Boolean) {
        prefs.edit {
            putBoolean("show_window_tutorial", show)
        }
    }
}