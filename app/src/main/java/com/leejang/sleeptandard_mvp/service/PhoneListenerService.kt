package com.leejang.sleeptandard_mvp.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.leejang.sleeptandard_mvp.ClassFile.AlarmReceiver
import com.leejang.sleeptandard_mvp.Prefs.AlarmPreferences
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import java.nio.ByteBuffer

/**
 * PhoneListenerService - Watch로부터 메시지를 수신하는 서비스
 * 
 * 역할:
 * - /TRIGGER_ALARM: Watch가 감지한 최적의 기상 시점에 알람 트리거
 * - /SLEEP_DATA_RESULT: 수면 데이터 결과 수신 (추후 UI 표시용)
 */
class PhoneListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "Message received from Watch: ${messageEvent.path}")
        
        when (messageEvent.path) {
            PATH_TRIGGER_ALARM -> {
                handleTriggerAlarm(messageEvent.data)
            }
            PATH_SLEEP_DATA_RESULT -> {
                handleSleepDataResult(messageEvent.data)
            }
            else -> {
                Log.w(TAG, "Unknown message path: ${messageEvent.path}")
            }
        }
    }
    
    /**
     * /TRIGGER_ALARM 처리
     * Watch가 최적의 기상 시점을 감지했을 때 호출됨
     * 
     * @param data Byte Array containing trigger time (Long, 8 bytes)
     */
    private fun handleTriggerAlarm(data: ByteArray) {
        try {
            // Parse trigger time from Watch
            val triggerTime = if (data.size >= 8) {
                ByteBuffer.wrap(data).long
            } else {
                System.currentTimeMillis()
            }
            
            Log.i(TAG, "Smart Alarm Trigger received! Time: $triggerTime")
            
            // [핵심] 현재 설정된 알람 정보 가져오기
            val alarmPrefs = AlarmPreferences(this)
            val currentAlarm = alarmPrefs.loadAlarm()
            
            // [중요] 백업 알람 즉시 취소 (스마트 알람이 울렸으므로 목표 시간의 백업 알람 불필요)
            try {
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val backupIntent = Intent(this, AlarmReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    this,
                    currentAlarm.id, // 동일한 requestCode 사용
                    backupIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
                )
                
                // PendingIntent가 존재하면 취소
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                    Log.i(TAG, "✅ Backup alarm cancelled for alarmId: ${currentAlarm.id}")
                } else {
                    Log.d(TAG, "No pending backup alarm found for alarmId: ${currentAlarm.id}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cancel backup alarm", e)
            }
            
            // Broadcast to AlarmReceiver to trigger the alarm (스마트 알람)
            val intent = Intent(this, AlarmReceiver::class.java).apply {
                action = "com.leejang.sleeptandard_mvp.TRIGGER_ALARM"
                putExtra("alarmId", currentAlarm.id)
                putExtra("ringtoneUri", currentAlarm.ringtoneUri)
                putExtra("vibrationEnabled", currentAlarm.vibrationEnabled)
                putExtra("volume", currentAlarm.volume)
                putExtra("triggerTime", triggerTime)
            }
            
            sendBroadcast(intent)
            Log.d(TAG, "Smart Alarm broadcast sent to AlarmReceiver with alarmId: ${currentAlarm.id}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle TRIGGER_ALARM", e)
        }
    }
    
    /**
     * /SLEEP_DATA_RESULT 처리
     * Watch로부터 수면 데이터 결과를 수신
     * 
     * @param data JSON string containing sleep session result
     */
    private fun handleSleepDataResult(data: ByteArray) {
        try {
            val jsonResult = String(data, Charsets.UTF_8)
            Log.i(TAG, "Sleep data result received: ${jsonResult.take(100)}...")
            
            // TODO: Parse JSON and save to local database or display in UI
            // For now, just log it
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle SLEEP_DATA_RESULT", e)
        }
    }
    
    companion object {
        private const val TAG = "PhoneListenerService"
        
        // Message paths from Watch
        private const val PATH_TRIGGER_ALARM = "/TRIGGER_ALARM"
        private const val PATH_SLEEP_DATA_RESULT = "/SLEEP_DATA_RESULT"
    }
}

