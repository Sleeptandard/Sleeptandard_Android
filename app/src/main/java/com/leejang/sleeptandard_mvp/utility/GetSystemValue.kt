package com.leejang.sleeptandard_mvp.utility

import android.content.Context
import android.provider.Settings
import android.util.Log

/**
 * 시스템의 알림 진동 세기를 가져옵니다. (0 ~ 3 또는 0 ~ 5, 기기마다 다름)
 * 대부분의 기기에서 0은 진동 꺼짐을 의미합니다.
 */
fun getIsSystemVibrationOn2(context: Context): Boolean {
    val vibrationSetting = try {
        Settings.System.getInt(context.contentResolver, "vibration_notif_intensity")
    }catch(e: Exception){
        Log.d("VibrationSetting", "Exception: $e")
    }
    Log.d("VibrationSetting", "vibrationSetting: $vibrationSetting")

    return try {
        // 알림 진동 세기 키값은 대중적으로 "vibration_notif_intensity"를 사용합니다.
        if(vibrationSetting > 0){
            true
        }else false
    } catch (e: Exception) {
        // 해당 키가 없거나 접근 불가 시 기본값 1(진동 켜짐 가정) 반환
        true
    }
}

fun getIsSystemVibrationOn(context: Context): Boolean {
    val resolver = context.contentResolver
    Log.d("VibrationSetting", "=== 진동 세기 체크 시작 ===") // ✅ 진입 확인용

    val keys = listOf(
        "vibration_notif_intensity",
        "notification_vibration_intensity",
        "vibrate_on_notifications",
        "VIB_NOTI_MAGNITUDE",
        "SEM_VIBRATION_NOTIFICATION_INTENSITY"
    )

    for (key in keys) {
        try {
            val intensity = Settings.System.getInt(resolver, key)
            Log.d("VibrationSetting", "✅ 찾은 키: $key, 값: $intensity")
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