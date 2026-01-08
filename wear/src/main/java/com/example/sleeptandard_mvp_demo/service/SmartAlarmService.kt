package com.example.sleeptandard_mvp_demo.service

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
import com.example.sleeptandard_mvp_demo.wear.R
import com.example.sleeptandard_mvp_demo.backend.model.SensorType
import com.example.sleeptandard_mvp_demo.backend.model.SleepStage
import com.example.sleeptandard_mvp_demo.backend.model.SleepSessionResult
import com.example.sleeptandard_mvp_demo.backend.model.StageEntry
import com.example.sleeptandard_mvp_demo.backend.processing.FeatureExtractor
import com.example.sleeptandard_mvp_demo.backend.processing.InferenceManager
import com.example.sleeptandard_mvp_demo.backend.repository.DataRepository
import com.example.sleeptandard_mvp_demo.backend.repository.UserStatsManager
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.MessageClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await // [핵심] 이 친구가 .await()를 가능하게 합니다
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.util.ArrayDeque
import java.util.Collections
import java.util.concurrent.ConcurrentLinkedDeque
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
    private var lastFeatureExtractionTime = 0L

    private val ACC_SAMPLE_RATE_US = 40000
    private val HR_SAMPLE_RATE_US = 1000000
    private val BATCH_LATENCY_US = 30_000_000
    private val FEATURE_INTERVAL_MS = 30000L

    private var targetAlarmTime: Long = 0L
    private var sessionStartTime: Long = 0L
    private val inferenceHistory = Collections.synchronizedList(mutableListOf<StageEntry>())
    private var consecutiveLightCount = 0
    private var lastStage: SleepStage = SleepStage.UNKNOWN
    private var hasTriggered = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SmartAlarmService onCreate()")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                targetAlarmTime = intent.getLongExtra(EXTRA_TARGET_TIME, 0L)
                sessionStartTime = System.currentTimeMillis()
                Log.i(TAG, "Service Started. Target Time: $targetAlarmTime")
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

            sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
            dataRepository = DataRepository(this)
            userStatsManager = UserStatsManager(this)
            messageClient = Wearable.getMessageClient(this)
            inferenceManager = InferenceManager(this)

            registerSensors()
            createNotificationChannel()

            val notification = buildNotification()

            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            isServiceRunning = true
            Log.i(TAG, "Foreground service started successfully")

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
        val timestamp = System.currentTimeMillis()

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return

            dataRepository.enqueueSensorData(timestamp, SensorType.ACC, x, y, z)
            accWindow.add(Triple(x, y, z))
            if (accWindow.size > ACC_WINDOW_SIZE) {
                accWindow.poll()
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
                if (hrWindow.size >= HR_WINDOW_SIZE && accWindow.size >= ACC_WINDOW_SIZE) {
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

                val hrFeatures = featureExtractor.getFeatures(hrSnapshot, userMean, userStd)
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

        if (currentStage == SleepStage.WAKE) {
            shouldTrigger = true
            triggerReason = "WAKE detected"
        } else if (currentStage == SleepStage.LIGHT) {
            if (lastStage == SleepStage.LIGHT) {
                consecutiveLightCount++
            } else {
                consecutiveLightCount = 1
            }

            if (consecutiveLightCount >= 3) {
                shouldTrigger = true
                triggerReason = "3 consecutive LIGHT"
            }
        } else {
            consecutiveLightCount = 0
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

    // [리팩토링] Suspend 함수로 변경 - 순차 실행 가능
    private suspend fun sendTriggerSignalSuspend(triggerTime: Long) {
        try {
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
        sensorManager.unregisterListener(this)
        dataRepository.stopLogging()
        serviceScope.cancel()

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

        const val ACTION_START_TRACKING = "com.example.sleeptandard_mvp_demo.START_TRACKING"
        const val ACTION_STOP_AND_SEND_RESULT = "com.example.sleeptandard_mvp_demo.STOP_AND_SEND_RESULT"
        const val EXTRA_TARGET_TIME = "EXTRA_TARGET_TIME"

        private const val PATH_TRIGGER_ALARM = "/TRIGGER_ALARM"
        private const val PATH_SLEEP_DATA_RESULT = "/SLEEP_DATA_RESULT"
    }
}