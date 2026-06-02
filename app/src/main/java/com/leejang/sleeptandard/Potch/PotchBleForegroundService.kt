package com.leejang.sleeptandard.Potch

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.leejang.sleeptandard.MainActivity
import com.leejang.sleeptandard.Potch.PotchBleManager
import com.leejang.sleeptandard.Potch.PotchDataLogger
import com.leejang.sleeptandard.Potch.PotchDataProcessor
import com.leejang.sleeptandard.Potch.PotchServiceStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PotchBleForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.leejang.sleeptandard.Potch.ACTION_START"
        const val ACTION_STOP_AND_SAVE = "com.leejang.sleeptandard.Potch.ACTION_STOP_AND_SAVE"

        private const val CHANNEL_ID = "potch_ble_channel"
        private const val NOTIFICATION_ID = 3001
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var dataLogger: PotchDataLogger? = null
    private var dataProcessor: PotchDataProcessor? = null
    private var bleManager: PotchBleManager? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Potch 수신 준비 중"))
        initializePotchObjects()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                markSessionRunning(true)
                startPotchReceiving()
            }

            ACTION_STOP_AND_SAVE -> {
                markSessionRunning(false)
                stopPotchReceivingAndSave()
            }

            null -> {
                if (isSessionRunning()) {
                    startPotchReceiving()
                }
            }
        }

        return START_STICKY
    }

    private fun initializePotchObjects() {
        if (dataLogger != null && dataProcessor != null && bleManager != null) return

        val logger = PotchDataLogger(applicationContext)
        val processor = PotchDataProcessor(dataLogger = logger)
        val manager = PotchBleManager(
            context = applicationContext,
            dataProcessor = processor,
            dataLogger = logger
        )

        dataLogger = logger
        dataProcessor = processor
        bleManager = manager

        serviceScope.launch {
            manager.state.collect { state ->
                PotchServiceStateHolder.updateBleState(state)

                val text = when {
                    state.isConnected -> "Potch 연결됨 · 데이터 수신 중"
                    state.isReconnecting -> "Potch 재연결 시도 중"
                    state.isScanning -> "Potch 검색 중"
                    else -> "Potch 대기 중"
                }

                updateNotification(text)
            }
        }

        serviceScope.launch {
            processor.state.collect { state ->
                PotchServiceStateHolder.updateProcessorState(state)
            }
        }
    }

    private fun startPotchReceiving() {
        if (!hasRequiredPermissions()) {
            updateNotification("Potch 권한이 부족합니다")
            return
        }

        initializePotchObjects()
        bleManager?.startScan()
    }

    private fun stopPotchReceivingAndSave() {
        val manager = bleManager

        if (manager == null) {
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
            return
        }

        updateNotification("Potch 로그 저장 중...")

        // BLE 연결/스캔/재연결은 메인 스레드에서 먼저 정리
        manager.stopReconnectOnly()

        // 대용량 파일 복사는 IO 스레드에서 실행
        serviceScope.launch {
            val savedPath = kotlinx.coroutines.withContext(Dispatchers.IO) {
                manager.saveCurrentLog()
            }

            manager.updateLogSavedState(savedPath)

            updateNotification(
                if (savedPath != null) {
                    "로그 저장 완료"
                } else {
                    "저장할 로그가 없습니다"
                }
            )

            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    override fun onDestroy() {
        bleManager?.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(contentText: String): Notification {
        // 알림 클릭 시 앱의 MainActivity를 연다.
        // MainActivity 쪽에서 open_screen 값을 보고 Potch/Experiment 화면으로 이동시키면 됨.
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_screen", "experiment")
        }

        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            3001,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 알림의 "종료 및 저장" 버튼을 눌렀을 때 ForegroundService에 종료 액션 전달
        val stopIntent = Intent(this, PotchBleForegroundService::class.java).apply {
            action = ACTION_STOP_AND_SAVE
        }

        val stopPendingIntent = PendingIntent.getService(
            this,
            3002,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("알람의 정석")
            .setContentText(contentText)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "종료 및 저장",
                stopPendingIntent
            )
            .build()
    }

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Potch BLE 수신",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Potch 수면 데이터 수신 상태"
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun hasRequiredPermissions(): Boolean {
        val hasBluetoothPermissions =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            }

        val hasNotificationPermission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

        return hasBluetoothPermissions && hasNotificationPermission
    }

    private fun markSessionRunning(running: Boolean) {
        getSharedPreferences("potch_service", MODE_PRIVATE)
            .edit()
            .putBoolean("session_running", running)
            .apply()
    }

    private fun isSessionRunning(): Boolean {
        return getSharedPreferences("potch_service", MODE_PRIVATE)
            .getBoolean("session_running", false)
    }
}