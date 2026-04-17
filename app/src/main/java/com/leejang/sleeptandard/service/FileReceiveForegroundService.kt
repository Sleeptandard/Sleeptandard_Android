package com.leejang.sleeptandard.service

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
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.leejang.sleeptandard.backend.CsvUploadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * FileReceiveForegroundService
 *
 * 삼성 등 제조사의 PowerManager가 백그라운드 WakeLock을 강제 비활성화(DISABLED)하는 문제 해결.
 * ForegroundService 상태에서는 삼성 PowerManager가 WakeLock을 끄지 못함.
 *
 * 역할: 워치 → 폰 대용량 파일(sensor.log ~22MB) 수신을 ForegroundService로 완전 보호
 */
class FileReceiveForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "FileReceiveFgService"
        private const val NOTIFICATION_ID = 4001
        private const val CHANNEL_ID = "file_receive_channel"
        const val EXTRA_CHANNEL = "extra_channel"

        fun buildIntent(context: Context, channel: ChannelClient.Channel): Intent {
            return Intent(context, FileReceiveForegroundService::class.java).apply {
                putExtra(EXTRA_CHANNEL, channel)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_CHANNEL, ChannelClient.Channel::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_CHANNEL)
        }

        if (channel == null) {
            Log.e(TAG, "No channel provided, stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        val fileName = channel.path.substringAfterLast("/")

        // 1. ForegroundService 시작 → 삼성 PowerManager가 이 프로세스의 WakeLock을 끄지 못함
        createNotificationChannel()
        val notification = buildNotification("수신 중: $fileName")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // 2. WakeLock 획득 (ForegroundService에서는 삼성이 강제로 비활성화 불가)
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SleepStandard:FileReceiveWakeLock"
        ).apply {
            acquire(20 * 60 * 1000L) // 최대 20분
            Log.d(TAG, "WakeLock acquired (ForegroundService - Samsung-proof)")
        }

        // 3. 파일 수신 실행
        serviceScope.launch {
            try {
                receiveFile(channel, fileName)
            } finally {
                cleanup()
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun receiveFile(channel: ChannelClient.Channel, fileName: String) {
        try {
            Log.i(TAG, "📥 [ForegroundService] Receiving: $fileName")
            val outputFile = File(filesDir, "received_$fileName")
            val channelClient = Wearable.getChannelClient(this)
            val inputStream = channelClient.getInputStream(channel).await()

            var totalBytes = 0L
            inputStream.use { input ->
                outputFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                    }
                }
            }

            Log.i(TAG, "✅ File saved: ${outputFile.name} (${totalBytes / 1024}KB)")

            // 업로드 큐 등록
            CsvUploadManager.enqueueUpload(applicationContext, outputFile)

            // 채널 닫기
            try {
                channelClient.close(channel).await()
            } catch (e: Exception) {
                Log.w(TAG, "Channel close warning: ${e.message}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ File receive failed: ${e.message}", e)
            try {
                Wearable.getChannelClient(this).close(channel).await()
            } catch (ce: Exception) { /* ignore */ }
        }
    }

    private fun cleanup() {
        try { wakeLock?.let { if (it.isHeld) it.release() } } catch (e: Exception) { /* ignore */ }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "FileReceiveForegroundService stopped")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "파일 수신", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("수면 데이터 수신 중")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        try { wakeLock?.let { if (it.isHeld) it.release() } } catch (e: Exception) { /* ignore */ }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
