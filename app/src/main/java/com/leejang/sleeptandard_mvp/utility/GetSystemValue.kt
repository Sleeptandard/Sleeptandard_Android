package com.leejang.sleeptandard_mvp.utility

import android.content.Context
import android.provider.Settings
import android.util.Log

fun getIsNotificationVibrationOn(context: Context): Boolean {
    val resolver = context.contentResolver
    Log.d("VibrationSetting", "=== 진동 세기 체크 시작 ===") // ✅ 진입 확인용

    val keys = listOf(
        // 삼성 S10 알림 진동세기 찾는 키
        "SEM_VIBRATION_NOTIFICATION_INTENSITY",
        // 이 밑으로는 제미나이 피셜
        // 삼성
        "VIB_NOTI_MAGNITUDE",
        // 안드로이드
        "vibration_notif_intensity",
        "notification_vibration_intensity",
        "vibrate_on_notifications",
    )

    for (key in keys) {
        try {
            // 진동 세기 찾아보기
            val intensity = Settings.System.getInt(resolver, key)
            Log.d("VibrationSetting", "✅ 찾은 키: $key, 값: $intensity")

            // 진동 세기가 0보다 큽니까?
            return intensity > 0
        } catch (e: Settings.SettingNotFoundException) {
            // 키가 없으면 로그를 남기고 다음으로 이동
            Log.d("VibrationSetting", "❌ 키 없음: $key")
            continue
        } catch (e: Exception) {
            Log.d("VibrationSetting", "⚠️ 에러 ($key): $e")
        }
    }

    Log.d("VibrationSetting", "종료: 일치하는 키를 하나도 찾지 못함. 기본값 true 반환") // ✅ 최종 결과 로그
    return true
}