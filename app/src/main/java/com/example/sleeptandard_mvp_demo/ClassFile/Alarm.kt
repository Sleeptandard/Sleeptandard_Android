package com.example.sleeptandard_mvp_demo.ClassFile

data class Alarm (
    val id: Int = 0,
    val hour: Int = 8,
    val minute: Int = 30,
    val isAm: Boolean = true,
    val ringtoneUri: String = "",
    val vibrationEnabled: Boolean = true,
    val volume: Int = 10 // ✅ 앱 전용 볼륨 값 (0~15 단계) 추가
    )