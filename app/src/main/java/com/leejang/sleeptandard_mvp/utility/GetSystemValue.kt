package com.leejang.sleeptandard_mvp.utility

import android.content.Context
import android.provider.Settings
import android.util.Log

/**
 * 시스템의 알림 진동 세기를 가져옵니다. (0 ~ 3 또는 0 ~ 5, 기기마다 다름)
 * 대부분의 기기에서 0은 진동 꺼짐을 의미합니다.
 */
fun getIsSystemVibrationOn(context: Context): Boolean {
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