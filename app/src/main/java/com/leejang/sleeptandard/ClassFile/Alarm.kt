package com.leejang.sleeptandard.ClassFile

data class Alarm (
    val id: Int = 0,
    val hour: Int = 8,
    val minute: Int = 30,
    val isAm: Boolean = true,

    /** 실험중 **/
    var ringtoneUri: String = "",

    val vibrationEnabled: Boolean = true,
    val volume: Int = 10, // ✅ 앱 전용 볼륨 값 (0~15 단계) 추가
    val earlyWakeUpMinutes: Int = 30,    // 기상 윈도우 시간
    val isRem: Boolean = false  // rem에서 깨울지 여부
    )