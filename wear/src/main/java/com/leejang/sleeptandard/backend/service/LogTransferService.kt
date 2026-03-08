package com.leejang.sleeptandard.backend.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.leejang.sleeptandard.backend.manager.LogFileTransferManager
import com.leejang.sleeptandard.wear.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * LogTransferService
 * 
 * 로그 파일을 Phone으로 전송하는 ForegroundService
 * - WakeLock 사용: 전송 중 화면이 꺼져도 작업 지속
 * - ForegroundService: 시스템이 프로세스를 강제 종료하지 못하도록 보호
 */
class LogTransferService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "LogTransferService onCreate()")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "LogTransferService started")
        
        // 1. Notification Channel 생성
        createNotificationChannel()
        
        // 2. Foreground Service 시작
        val notification = buildNotification("로그 파일 전송 준비 중...")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        
        // 3. WakeLock 획득
        acquireWakeLock()
        
        // 4. 파일 전송 시작
        serviceScope.launch {
            try {
                transferLogs()
            } catch (e: Exception) {
                Log.e(TAG, "Transfer failed with exception", e)
                updateNotification("❌ 전송 실패: ${e.message}")
            } finally {
                cleanup()
            }
        }
        
        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SleepStandard::LogTransfer"
        ).apply {
            acquire(10 * 60 * 1000L) // 최대 10분
            Log.d(TAG, "WakeLock acquired")
        }
    }

    private suspend fun transferLogs() {
        val transferManager = LogFileTransferManager(this)
        
        updateNotification("📤 로그 파일 전송 중...")
        
        val result = transferManager.sendLatestLogsToPhone()
        
        result.onSuccess { count ->
            Log.i(TAG, "Transfer completed successfully: $count files")
            updateNotification("✅ ${count}개 파일 전송 완료")
        }.onFailure { error ->
            Log.e(TAG, "Transfer failed", error)
            updateNotification("❌ 전송 실패: ${error.message}")
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "로그 전송",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "로그 파일을 폰으로 전송합니다"
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sleep Standard")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notification = buildNotification(contentText)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun cleanup() {
        Log.d(TAG, "Cleaning up LogTransferService")
        
        // WakeLock 해제
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock released")
            }
        }
        
        // 코루틴 스코프 취소
        serviceScope.cancel()
        
        // Foreground Service 종료
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        
        Log.d(TAG, "LogTransferService stopped")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "LogTransferService onDestroy()")
        cleanup()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "LogTransferService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "log_transfer_channel"
    }
}
