package com.leejang.sleeptandard_mvp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.leejang.sleeptandard_mvp.wear.R
import com.leejang.sleeptandard_mvp.backend.model.SensorType
import com.leejang.sleeptandard_mvp.backend.model.SleepStage
import com.leejang.sleeptandard_mvp.backend.model.SleepSessionResult
import com.leejang.sleeptandard_mvp.backend.model.StageEntry
import com.leejang.sleeptandard_mvp.backend.processing.FeatureExtractor
import com.leejang.sleeptandard_mvp.backend.processing.InferenceManager
import com.leejang.sleeptandard_mvp.backend.repository.DataRepository
import com.leejang.sleeptandard_mvp.backend.repository.UserStatsManager
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.MessageClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await // [핵심] 이 친구가 .await()를 가능하게 합니다
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.util.ArrayDeque
import java.util.Collections
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger
import android.os.IBinder

class SmartAlarmService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var dataRepository: DataRepository
    private lateinit var userStatsManager: UserStatsManager
    private val featureExtractor = FeatureExtractor()
    private lateinit var inferenceManager: InferenceManager

    private val serviceScope = CoroutineScope(Dispatchers.Default)

    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var messageClient: MessageClient

    private var isServiceRunning = false

    private val HR_WINDOW_SIZE = 30
    private val ACC_WINDOW_SIZE = 750

    private val hrWindow = ArrayDeque<Float>(HR_WINDOW_SIZE)
    private val accWindow = ConcurrentLinkedDeque<Triple<Float, Float, Float>>()
    private val accWindowCount = AtomicInteger(0)
    private var lastFeatureExtractionTime = 0L

    private val ACC_SAMPLE_RATE_US = 40000
    private val HR_SAMPLE_RATE_US = 1000000
    private val BATCH_LATENCY_US = 30_000_000
    private val FEATURE_INTERVAL_MS = 30000L

    private var targetAlarmTime: Long = 0L
    private var sessionStartTime: Long = 0L
    private var situationLabel: String = "normal" // [추가] 특별 상황 라벨
    private val inferenceHistory = Collections.synchronizedList(mutableListOf<StageEntry>())
    private var consecutiveRemCount = 0  // REM 수면 연속 카운트
    private var lastStage: SleepStage = SleepStage.UNKNOWN
    private var hasTriggered = false
    private var hasNotifiedSensingStart = false  // [신규] 첫 센싱 감지 알림 플래그

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SmartAlarmService onCreate()")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                targetAlarmTime = intent.getLongExtra(EXTRA_TARGET_TIME, 0L)
                situationLabel = intent.getStringExtra(EXTRA_SITUATION_LABEL) ?: "normal" // [추가] 라벨 받기
                sessionStartTime = System.currentTimeMillis()
                Log.i(TAG, "Service Started. Target Time: $targetAlarmTime, Label: $situationLabel")
                
                // [핵심] Foreground Service는 가능한 빨리 startForeground 호출 필요
                createNotificationChannel()
                val notification = buildNotification()
                if (Build.VERSION.SDK_INT >= 34) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                
                initializeService()
            }
            ACTION_STOP_AND_SEND_RESULT -> {
                Log.i(TAG, "Stop requested")
                stopAndSendResult()
                return START_NOT_STICKY
            }
            else -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun initializeService() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SmartAlarm:TrackingWakeLock"
            )

            if (!wakeLock.isHeld) {
                wakeLock.acquire(8 * 60 * 60 * 1000L)
                Log.d(TAG, "WakeLock acquired")
            }

            // Android 12+ attributionTag 에러 방지
            sensorManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                createAttributionContext(null).getSystemService(SENSOR_SERVICE) as SensorManager
            } else {
                getSystemService(SENSOR_SERVICE) as SensorManager
            }
            dataRepository = DataRepository(this, situationLabel) // [수정] 라벨 전달
            userStatsManager = UserStatsManager(this)
            messageClient = Wearable.getMessageClient(this)
            inferenceManager = InferenceManager(this)

            registerSensors()

            isServiceRunning = true
            Log.i(TAG, "Service initialized successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Service initialization failed", e)
            try {
                if (::wakeLock.isInitialized && wakeLock.isHeld) {
                    wakeLock.release()
                }
            } catch (ex: Exception) { /* Ignore */ }
            stopSelf()
        }
    }

    private fun registerSensors() {
        val hrSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        val accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (hrSensor != null) {
            sensorManager.registerListener(this, hrSensor, HR_SAMPLE_RATE_US)
        }
        if (accSensor != null) {
            sensorManager.registerListener(this, accSensor, ACC_SAMPLE_RATE_US, BATCH_LATENCY_US)
        }
        Log.d(TAG, "Sensors registered")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sleep Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sleep Tracking Active")
            .setContentText("Monitoring sensors...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        
        // [안전성] 서비스 초기화 완료 전 센서 이벤트 무시
        if (!isServiceRunning) return
        if (!::dataRepository.isInitialized || !::userStatsManager.isInitialized || !::inferenceManager.isInitialized) {
            return
        }
        
        // [신규] 첫 센싱 감지 시 폰에 알림 전송
        if (!hasNotifiedSensingStart) {
            hasNotifiedSensingStart = true
            serviceScope.launch {
                sendSensingStartSignal()
            }
        }
        
        val timestamp = System.currentTimeMillis()

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return

            dataRepository.enqueueSensorData(timestamp, SensorType.ACC, x, y, z)
            accWindow.add(Triple(x, y, z))
            accWindowCount.incrementAndGet()
            if (accWindowCount.get() > ACC_WINDOW_SIZE) {
                if (accWindow.poll() != null) {
                    accWindowCount.decrementAndGet()
                }
            }

        } else if (event.sensor.type == Sensor.TYPE_HEART_RATE) {
            val hrValue = event.values[0]
            if (!hrValue.isFinite() || hrValue <= 0) return

            dataRepository.enqueueSensorData(timestamp, SensorType.HR, hrValue, 0f, 0f)
            userStatsManager.update(hrValue)

            if (hrWindow.size >= HR_WINDOW_SIZE) {
                hrWindow.removeFirst()
            }
            hrWindow.addLast(hrValue)

            if (timestamp - lastFeatureExtractionTime >= FEATURE_INTERVAL_MS) {
                if (hrWindow.size >= HR_WINDOW_SIZE && accWindowCount.get() >= ACC_WINDOW_SIZE) {
                    runInferencePipeline(timestamp)
                    lastFeatureExtractionTime = timestamp
                }
            }
        }
    }

    private fun runInferencePipeline(timestamp: Long) {
        val hrSnapshot = hrWindow.toList()
        val accSnapshot = accWindow.toList()

        serviceScope.launch {
            try {
                val userMean = userStatsManager.getUserMean()
                val userStd = userStatsManager.getUserStd()
                
                // RMSSD 계산 및 통계 업데이트
                val rmssdRaw = featureExtractor.calculateApproxRmssd(hrSnapshot)
                userStatsManager.updateRmssd(rmssdRaw)
                
                val userBaseRmssd = userStatsManager.getUserBaseRmssd()
                val userStdRmssd = userStatsManager.getUserStdRmssd()

                val hrFeatures = featureExtractor.getFeatures(
                    hrSnapshot, 
                    userMean, 
                    userStd,
                    userBaseRmssd,
                    userStdRmssd
                )
                val featureString = hrFeatures.joinToString(",")

                val currentStage = inferenceManager.predict(accSnapshot, hrFeatures)

                inferenceHistory.add(StageEntry(timestamp, currentStage.name))
                dataRepository.enqueueInferenceLog(timestamp, "${currentStage.name},0.0,$featureString")

                Log.d(TAG, "Inference Result: $currentStage")
                checkSmartWindowAndTrigger(timestamp, currentStage)

            } catch (e: Exception) {
                Log.e(TAG, "Inference Failed", e)
            }
        }
    }

    private fun checkSmartWindowAndTrigger(currentTime: Long, currentStage: SleepStage) {
        if (hasTriggered || targetAlarmTime == 0L) return

        val windowStart = targetAlarmTime - SMART_WINDOW_MS

        if (currentTime < windowStart) return
        
        // [개선] 목표 시간 초과 시 폴백 알람 실행
        if (currentTime > targetAlarmTime) {
            Log.w(TAG, "⏰ Target time reached without smart trigger. Triggering fallback alarm...")
            hasTriggered = true // 중복 실행 방지
            
            serviceScope.launch {
                try {
                    // 1. 폴백 알람 전송 (목표 시간에 무조건 울림)
                    sendTriggerSignalSuspend(targetAlarmTime)
                    
                    // 2. 짧은 대기 (메시지 전송 안정성 확보)
                    delay(500L)
                    
                    // 3. 결과 전송 및 서비스 종료
                    stopAndSendResultSuspend()
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error during fallback alarm", e)
                    stopSelf() // 에러 발생 시에도 서비스는 종료
                }
            }
            return
        }

        var shouldTrigger = false
        var triggerReason = ""

        // REM 수면 3번 연속 감지 시 알람 트리거
        if (currentStage == SleepStage.REM) {
            if (lastStage == SleepStage.REM) {
                consecutiveRemCount++
            } else {
                consecutiveRemCount = 1
            }

            if (consecutiveRemCount >= 3) {
                shouldTrigger = true
                triggerReason = "3 consecutive REM Sleep"
            }
        } else {
            // REM이 아닌 경우 (WAKE, LIGHT, DEEP, UNKNOWN) 카운트 초기화
            consecutiveRemCount = 0
        }

        // [핵심] 트리거 조건 충족 시 자동 종료 시퀀스 실행
        if (shouldTrigger) {
            Log.i(TAG, "🔔 Trigger Condition Met: $triggerReason! Initiating shutdown sequence...")
            hasTriggered = true // 중복 실행 방지

            serviceScope.launch {
                try {
                    // 1. 알람 신호 전송 (폰 울리기)
                    sendTriggerSignalSuspend(currentTime)

                    // 2. 짧은 대기 (메시지 전송 안정성 확보)
                    delay(500L)

                    // 3. 결과 전송 및 서비스 종료 (내부에서 stopSelf 호출됨)
                    stopAndSendResultSuspend()

                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error during auto-shutdown sequence", e)
                    stopSelf() // 에러 발생 시에도 서비스는 종료
                }
            }
        }

        lastStage = currentStage
    }

    // [신규] 첫 센싱 시작 신호를 폰에 전송
    private suspend fun sendSensingStartSignal() {
        try {
            if (!::messageClient.isInitialized) {
                Log.w(TAG, "MessageClient not initialized, skipping sensing start signal")
                return
            }
            
            val nodeClient = Wearable.getNodeClient(this@SmartAlarmService)
            val connectedNodes = nodeClient.connectedNodes.await()
            
            if (connectedNodes.isNotEmpty()) {
                val phoneNodeId = connectedNodes.first().id
                messageClient.sendMessage(phoneNodeId, PATH_SENSING_STARTED, ByteArray(0)).await()
                Log.i(TAG, "✅ Sensing start signal sent to phone!")
            } else {
                Log.w(TAG, "No connected nodes to send sensing start signal")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send sensing start signal", e)
        }
    }

    // [리팩토링] Suspend 함수로 변경 - 순차 실행 가능
    private suspend fun sendTriggerSignalSuspend(triggerTime: Long) {
        try {
            // [안전성] MessageClient 초기화 확인
            if (!::messageClient.isInitialized) {
                Log.w(TAG, "MessageClient not initialized, skipping trigger signal")
                return
            }
            
            val nodeClient = Wearable.getNodeClient(this@SmartAlarmService)
            val connectedNodes = nodeClient.connectedNodes.await()

            if (connectedNodes.isNotEmpty()) {
                val payload = ByteBuffer.allocate(8).putLong(triggerTime).array()
                val phoneNodeId = connectedNodes.first().id

                messageClient.sendMessage(phoneNodeId, PATH_TRIGGER_ALARM, payload).await()
                Log.i(TAG, "✅ Trigger signal sent to phone!")
            } else {
                Log.w(TAG, "No connected nodes to send trigger signal")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send trigger", e)
        }
    }

    // [리팩토링] Suspend 함수로 변경 - 순차 실행 가능
    private suspend fun stopAndSendResultSuspend() {
        try {
            // [안전성] MessageClient 초기화 확인 - 초기화되지 않았으면 전송 건너뛰기
            if (!::messageClient.isInitialized) {
                Log.w(TAG, "MessageClient not initialized, skipping result transmission")
                return
            }
            
            val result = SleepSessionResult(
                startTime = sessionStartTime,
                endTime = System.currentTimeMillis(),
                stageHistory = inferenceHistory.toList()
            )
            val jsonPayload = Json.encodeToString(result)

            val nodeClient = Wearable.getNodeClient(this@SmartAlarmService)
            val connectedNodes = nodeClient.connectedNodes.await()

            if (connectedNodes.isNotEmpty()) {
                val phoneNodeId = connectedNodes.first().id
                messageClient.sendMessage(phoneNodeId, PATH_SLEEP_DATA_RESULT, jsonPayload.toByteArray()).await()
                Log.i(TAG, "✅ Result sent to phone.")
            } else {
                Log.w(TAG, "No connected nodes to send result")
            }
            
            // [자동 로그 전송] 알람 종료 시 로그 파일 자동 전송
            try {
                // [중요] 로그 쓰기 완료 대기 (파일 누락 방지)
                Log.i(TAG, "⏳ Waiting for log writing to complete...")
                
                // DataRepository 중단 및 완료 대기
                if (::dataRepository.isInitialized) {
                    withContext(Dispatchers.IO) {
                        dataRepository.stopLogging()
                        val completed = dataRepository.waitForCompletion(5000)
                        if (!completed) {
                            Log.w(TAG, "⚠️ Log writing timeout, but proceeding with transfer")
                        }
                    }
                }
                
                Log.i(TAG, "🚀 Auto-transferring log files to phone...")
                val transferManager = com.leejang.sleeptandard_mvp.backend.manager.LogFileTransferManager(this@SmartAlarmService)
                val transferResult = transferManager.sendLatestLogsToPhone()
                
                transferResult.onSuccess { count ->
                    Log.i(TAG, "✅ Auto-transfer completed: $count files")
                }.onFailure { error ->
                    Log.w(TAG, "⚠️ Auto-transfer failed: ${error.message}")
                    // 자동 전송 실패는 치명적이지 않으므로 서비스 종료는 계속 진행
                }
            } catch (e: Exception) {
                Log.e(TAG, "Auto-transfer error (non-critical)", e)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send result", e)
        } finally {
            stopSelf()
        }
    }

    // [래퍼] onStartCommand에서 호출되는 기존 함수 유지
    private fun stopAndSendResult() {
        serviceScope.launch {
            stopAndSendResultSuspend()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        
        // [안전성] SensorManager 초기화 확인 후 리스너 해제
        if (::sensorManager.isInitialized) {
            try {
                sensorManager.unregisterListener(this)
                Log.d(TAG, "Sensor listeners unregistered")
            } catch (e: Exception) {
                Log.e(TAG, "Sensor unregister error", e)
            }
        } else {
            Log.w(TAG, "SensorManager not initialized, skipping unregister")
        }
        
        // [안전성] DataRepository 초기화 확인 후 로깅 중단
        // 참고: stopAndSendResultSuspend()에서 이미 처리됨
        if (::dataRepository.isInitialized) {
            try {
                // 이미 stopLogging()이 호출되었을 수 있음
                // 중복 호출은 안전하지만 로그만 남김
                Log.d(TAG, "DataRepository cleanup in onDestroy (may be already stopped)")
            } catch (e: Exception) {
                Log.e(TAG, "DataRepository cleanup error", e)
            }
        } else {
            Log.w(TAG, "DataRepository not initialized, skipping cleanup")
        }
        
        // Coroutine Scope 취소
        serviceScope.cancel()

        // [안전성] WakeLock 초기화 및 Held 상태 확인 후 해제
        try {
            if (::wakeLock.isInitialized && wakeLock.isHeld) {
                wakeLock.release()
                Log.d(TAG, "WakeLock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "WakeLock release error", e)
        }

        Log.d(TAG, "SmartAlarmService destroyed")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "SmartAlarmService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "sleep_tracking_channel"

        private const val SMART_WINDOW_MS = 30 * 60 * 1000L

        const val ACTION_START_TRACKING = "com.leejang.sleeptandard_mvp.START_TRACKING"
        const val ACTION_STOP_AND_SEND_RESULT = "com.leejang.sleeptandard_mvp.STOP_AND_SEND_RESULT"
        const val EXTRA_TARGET_TIME = "EXTRA_TARGET_TIME"
        const val EXTRA_SITUATION_LABEL = "EXTRA_SITUATION_LABEL" // [추가] 라벨 Extra 키

        private const val PATH_SENSING_STARTED = "/WATCH_SENSING_STARTED"  // [신규] 센싱 시작 경로
        private const val PATH_TRIGGER_ALARM = "/TRIGGER_ALARM"
        private const val PATH_SLEEP_DATA_RESULT = "/SLEEP_DATA_RESULT"
    }
}