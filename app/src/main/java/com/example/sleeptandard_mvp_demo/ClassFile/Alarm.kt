package com.example.sleeptandard_mvp_demo.ClassFile

/* Not using: 알람은 하루치만 설정 하도록 함
enum class AlarmDay(val label: String) {
    MON("월"),
    TUE("화"),
    WED("수"),
    THU("목"),
    FRI("금"),
    SAT("토"),
    SUN("일")
}*/

data class Alarm (
    /* id 불필요할것 같지만 일단 냅둠 */
    val id: Int = 0,
    val hour: Int = 8,
    val minute: Int = 30,
    val isAm: Boolean = true,
    val ringtoneUri: String = "",
    val vibrationEnabled: Boolean = true,
    val volume: Int = 10 // ✅ 앱 전용 볼륨 값 (0~15 단계) 추가
    /* Not using: 알람은 하루치만 설정 하도록 함
val days: Set<AlarmDay> = emptySet(),*/
    /* Not using: 알람 설정을 완료여부로 판단
     val isOn: Boolean = true */
    )