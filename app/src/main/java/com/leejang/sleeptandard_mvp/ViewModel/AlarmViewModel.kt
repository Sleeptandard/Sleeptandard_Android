package com.leejang.sleeptandard_mvp.ViewModel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.leejang.sleeptandard_mvp.ClassFile.Alarm

class AlarmViewModel(application: Application): AndroidViewModel(application) {

    private var _alarm by mutableStateOf(Alarm())
    val alarm: Alarm get() = _alarm

    private val _alarms = mutableStateListOf<Alarm>()
    val alarms: List<Alarm> get() = _alarms

    fun saveAlarm(hour: Int, minute: Int, isAm: Boolean, ringtoneUri: String, vibrationEnabled: Boolean, volume: Int): Boolean {
        _alarm = Alarm(1, hour, minute, isAm, ringtoneUri, vibrationEnabled, volume) // ✅ volume 추가
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
    
    // ==================== Watch Communication ====================
    
    /**
     * Watch에게 수면 추적 시작 명령 전송
     * 
     * @param targetTime 목표 알람 시간 (milliseconds)
     * @param situationLabel 특별 상황 라벨 (optional, 기본값: "normal")
     */
    fun startSleepTracking(targetTime: Long, situationLabel: String = "normal") {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val messageClient = Wearable.getMessageClient(getApplication())
                val connectedNodes = Tasks.await(Wearable.getNodeClient(getApplication()).connectedNodes)
                
                if (connectedNodes.isEmpty()) {
                    Log.e(TAG, "No connected Watch found")
                    return@launch
                }
                
                // [수정] 메시지 구조: [8바이트 targetTime] + [나머지 바이트 label UTF-8]
                val watchNodeId = connectedNodes.first().id
                val labelBytes = situationLabel.toByteArray(Charsets.UTF_8)
                val payload = ByteBuffer.allocate(8 + labelBytes.size)
                    .putLong(targetTime)
                    .put(labelBytes)
                    .array()
                
                Tasks.await(messageClient.sendMessage(watchNodeId, PATH_START_TRACKING, payload))
                Log.i(TAG, "START_TRACKING sent to Watch. Target: $targetTime, Label: $situationLabel")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start sleep tracking", e)
            }
        }
    }

    /**
     * Watch에게 수면 추적 중지 명령 전송
     */
    fun stopSleepTracking() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val messageClient = Wearable.getMessageClient(getApplication())
                val connectedNodes = Tasks.await(Wearable.getNodeClient(getApplication()).connectedNodes)
                
                if (connectedNodes.isEmpty()) {
                    Log.e(TAG, "No connected Watch found")
                    return@launch
                }
                
                val watchNodeId = connectedNodes.first().id
                
                Tasks.await(messageClient.sendMessage(watchNodeId, PATH_STOP_TRACKING, ByteArray(0)))
                Log.i(TAG, "STOP_TRACKING sent to Watch")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop sleep tracking", e)
            }
        }
    }
    
    companion object {
        private const val TAG = "AlarmViewModel"
        
        // Message paths to Watch
        private const val PATH_START_TRACKING = "/START_TRACKING"
        private const val PATH_STOP_TRACKING = "/STOP_TRACKING"
    }

}