package com.leejang.sleeptandard.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.leejang.sleeptandard.ClassFile.AlarmReceiver
import com.leejang.sleeptandard.Prefs.AlarmPreferences
import com.leejang.sleeptandard.backend.CsvUploadManager
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.nio.ByteBuffer



class PhoneListenerService : WearableListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "Message received from Watch: ${messageEvent.path}")
        
        when (messageEvent.path) {
            PATH_SENSING_STARTED -> {
                handleWatchSensingStarted()
            }
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
     * /WATCH_SENSING_STARTED 처리
     * Watch에서 센서 감지가 시작되었을 때 호출됨
     */
    private fun handleWatchSensingStarted() {
        Log.i(TAG, "Watch sensing started!")
        
        // 메인 스레드에서 토스트 표시
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                applicationContext,
                "워치가 연결되었습니다 ✅",
                Toast.LENGTH_SHORT
            ).show()
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
                // ✅ AlarmScheduler에서 설정한 것과 동일한 extras를 넣어야 PendingIntent를 찾을 수 있음
                val backupIntent = Intent(this, AlarmReceiver::class.java).apply {
                    putExtra("alarmId", currentAlarm.id)
                    putExtra("ringtoneUri", currentAlarm.ringtoneUri)
                    putExtra("volume", currentAlarm.volume)
                    putExtra("vibrationEnabled", currentAlarm.vibrationEnabled)
                }
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
                    Log.w(TAG, "⚠️ No pending backup alarm found for alarmId: ${currentAlarm.id}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cancel backup alarm", e)
            }
            
            // Broadcast to AlarmReceiver to trigger the alarm (스마트 알람)
            val intent = Intent(this, AlarmReceiver::class.java).apply {
                action = "com.leejang.sleeptandard.TRIGGER_ALARM"
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
    
    /**
     * Watch로부터 Channel을 통한 파일 전송 수신
     * → FileReceiveForegroundService에 위임하여 삼성 WakeLock 강제 비활성화 문제 해결
     */
    override fun onChannelOpened(channel: ChannelClient.Channel) {
        super.onChannelOpened(channel)
        
        val channelPath = channel.path
        Log.i(TAG, "📥 Channel opened: $channelPath")
        
        // /sleep_log_transfer/로 시작하는 채널만 처리
        if (channelPath.startsWith(PATH_LOG_TRANSFER_PREFIX)) {
            Log.i(TAG, "🚀 Delegating to FileReceiveForegroundService")
            val intent = FileReceiveForegroundService.buildIntent(this, channel)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }

    /**
     * 수신된 파일 검증 및 디버깅 정보 출력
     * (OOM 방지를 위해 readLines 대신 라인별로 직접 카운트)
     */
    private fun validateReceivedFile(file: File) {
        try {
            val fileSize = file.length()
            
            // OOM 방지를 위해 readLines() 대신 스트리밍 방식으로 라인 수 계산
            var lineCount = 0
            var previewLine: String? = null
            
            file.useLines { lines ->
                val iterator = lines.iterator()
                if (iterator.hasNext()) {
                    previewLine = iterator.next()
                    lineCount++
                }
                while (iterator.hasNext()) {
                    iterator.next()
                    lineCount++
                }
            }
            
            Log.i(TAG, "📊 File Validation:")
            Log.i(TAG, "  - Name: ${file.name}")
            Log.i(TAG, "  - Size: ${fileSize / 1024} KB")
            Log.i(TAG, "  - Total Lines: $lineCount")
            Log.i(TAG, "  - First Line Preview: ${previewLine?.take(50)}")
            
            // 파일이 너무 작으면 경고
            if (fileSize < 100) {
                Log.w(TAG, "⚠️ WARNING: File is very small (${fileSize} bytes)")
                Log.w(TAG, "⚠️ This may cause 'Unsupported' error when sharing")
            }
            
            // 헤더만 있고 데이터가 없으면 경고
            if (lineCount <= 1) {
                Log.e(TAG, "❌ CRITICAL: File contains only header, NO DATA!")
                Log.e(TAG, "❌ This will cause 'Unsupported' error when sharing")
            } else if (lineCount < 5) {
                Log.w(TAG, "⚠️ WARNING: File has very little data ($lineCount lines)")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to validate file", e)
        }
    }

    /**
     * 파일 수신 완료 알림
     */
    private fun showFileReceivedNotification(fileName: String, sizeBytes: Long) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Android 8.0+ 채널 생성
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "로그 파일 전송",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "워치로부터 로그 파일 수신 알림"
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("로그 파일 수신 완료")
            .setContentText("$fileName (${sizeBytes / 1024}KB)")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_FILE_RECEIVED, notification)
        
        // 메인 스레드에서 토스트 표시
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                applicationContext,
                "로그 파일 수신 완료 ✅",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    companion object {
        private const val TAG = "PhoneListenerService"
        
        // Message paths from Watch
        private const val PATH_SENSING_STARTED = "/WATCH_SENSING_STARTED"
        private const val PATH_TRIGGER_ALARM = "/TRIGGER_ALARM"
        private const val PATH_SLEEP_DATA_RESULT = "/SLEEP_DATA_RESULT"
        
        // Channel paths for file transfer
        private const val PATH_LOG_TRANSFER_PREFIX = "/sleep_log_transfer"
        
        // Notification
        private const val NOTIFICATION_CHANNEL_ID = "log_file_transfer"
        private const val NOTIFICATION_ID_FILE_RECEIVED = 2001
    }
}