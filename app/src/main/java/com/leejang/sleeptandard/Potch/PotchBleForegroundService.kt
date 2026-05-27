package com.leejang.sleeptandard.Potch

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.leejang.sleeptandard.Potch.PotchBleManager
import com.leejang.sleeptandard.Potch.PotchDataLogger
import com.leejang.sleeptandard.Potch.PotchDataProcessor
import com.leejang.sleeptandard.Potch.PotchEpochAccumulator
import com.leejang.sleeptandard.Potch.PotchInferenceManager
import com.leejang.sleeptandard.Potch.PotchServiceStateHolder
import com.leejang.sleeptandard.Potch.PotchWindowBuffer
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

    // ── 추론 파이프라인 ────────────────────────────────────────────────
    private var inferenceManager: PotchInferenceManager? = null
    private var windowBuffer: PotchWindowBuffer? = null
    private var epochAccumulator: PotchEpochAccumulator? = null
    // ──────────────────────────────────────────────────────────────────

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

        // ── 추론 파이프라인 초기화 ─────────────────────────────────────
        val inference = PotchInferenceManager(applicationContext)

        val window = PotchWindowBuffer(windowSize = 5) { epochWindow ->
            // 5 에포크 완성 → 추론 실행 (백그라운드 스레드)
            serviceScope.launch(Dispatchers.Default) {
                val stage = inference.predict(epochWindow)
                PotchServiceStateHolder.updateSleepStage(stage)
            }
        }

        val accumulator = PotchEpochAccumulator { epoch ->
            window.addEpoch(epoch)
        }

        inferenceManager = inference
        windowBuffer = window
        epochAccumulator = accumulator
        // ──────────────────────────────────────────────────────────────

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

                // 새 SensorData가 파싱될 때마다 accumulator에 전달
                state.lastParsedData?.let { sensorData ->
                    serviceScope.launch(Dispatchers.Default) {
                        accumulator.process(sensorData)
                    }
                }
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
        bleManager?.stopReconnectAndSaveLog()
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    override fun onDestroy() {
        bleManager?.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("알람의 정석")
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
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