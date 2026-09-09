package com.leejang.sleeptandard.ViewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.leejang.sleeptandard.ClassFile.Alarm

class AlarmViewModel(application: Application): AndroidViewModel(application) {

    private var _alarm by mutableStateOf(Alarm())
    val alarm: Alarm get() = _alarm

    private val _alarms = mutableStateListOf<Alarm>()
    val alarms: List<Alarm> get() = _alarms

    fun saveAlarm(hour: Int, minute: Int, isAm: Boolean, ringtoneUri: String, vibrationEnabled: Boolean, volume: Int): Boolean {
        _alarm = Alarm(1, hour, minute, isAm, ringtoneUri, vibrationEnabled, volume)
        return true
    }

    // 외부에서 Alarm 객체를 통째로 넣어줄 수 있게
    fun copyAlarm(alarm: Alarm) {
        _alarm = alarm
    }

    // 알람 추가
    fun addAlarm(hour: Int, minute: Int, isAm: Boolean,  ringtoneUri: String, vibrationEnabled: Boolean): Alarm {
        val newId = if (_alarms.isEmpty()) 1 else _alarms.maxOf { it.id } + 1
        val newAlarm = Alarm(id = newId, hour = hour, minute = minute, isAm = isAm,  ringtoneUri = ringtoneUri, vibrationEnabled = vibrationEnabled)
        _alarms.add(newAlarm)
        return newAlarm
    }

    // 알람 삭제
    fun deleteAlarm(id: Int) {
        _alarms.removeAll { it.id == id }
    }
}
