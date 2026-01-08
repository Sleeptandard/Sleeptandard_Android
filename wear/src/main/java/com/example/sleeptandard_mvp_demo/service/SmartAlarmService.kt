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
import android.os.IBinder
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
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.util.ArrayDeque
import java.util.Collections

class SmartAlarmService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var dataRepository: DataRepository
    private lateinit var userStatsManager: UserStatsManager
    private lateinit var inferenceManager: InferenceManager
    private val featureExtractor = FeatureExtractor()
    private val serviceScope = CoroutineScope(Dispatchers.Default)

    // WakeLock for preventing CPU sleep during tracking
    private lateinit var wakeLock: PowerManager.WakeLock
    
    // Wearable Message Client for Phone communication
    private lateinit var messageClient: MessageClient
    
    // Synchronization lock for window access
    private val windowLock = Any()

    // Window sizes (30s intervals for power optimization)
    private val HR_WINDOW_SIZE = 30  // 30 seconds * 1Hz
    private val ACC_WINDOW_SIZE = 750  // 30 seconds * 25Hz
    
    private val hrWindow = ArrayDeque<Float>(HR_WINDOW_SIZE)
    private val accWindow = ArrayDeque<Triple<Float, Float, Float>>(ACC_WINDOW_SIZE)
    private var lastFeatureExtractionTime = 0L

    // Sampling rates and intervals (Power Budget Optimization)
    private val ACC_SAMPLE_RATE_US = 40000  // 25Hz
    private val HR_SAMPLE_RATE_US = 1000000  // 1Hz
    private val BATCH_LATENCY_US = 30_000_000  // 30 seconds batch latency
    private val FEATURE_INTERVAL_MS = 30000L  // 30 seconds inference interval
    
    // Smart Window Logic (Phase 3)
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
        // Handle different actions
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                // Extract targetAlarmTime from intent
                targetAlarmTime = intent.getLongExtra(EXTRA_TARGET_TIME, 0L)
                sessionStartTime = System.currentTimeMillis()
                
                Log.i(TAG, "Service Started with Batch=30s, Interval=30s")
                Log.i(TAG, "Target Alarm Time: $targetAlarmTime")
                
                initializeService()
            }
            ACTION_STOP_AND_SEND_RESULT -> {
                Log.i(TAG, "Stop and send result requested")
                stopAndSendResult()
                return START_NOT_STICKY
            }
            else -> {
                Log.w(TAG, "Unknown action: ${intent?.action}")
                stopSelf()
                return START_NOT_STICKY
            }
        }
        
        return START_NOT_STICKY
    }
    
    private fun initializeService() {
        try {
            // 1. Initialize WakeLock
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SmartAlarm:TrackingWakeLock"
            )
            
            if (!wakeLock.isHeld) {
                wakeLock.acquire(8 * 60 * 60 * 1000L) // Max 8 hours
                Log.d(TAG, "WakeLock acquired")
            }
            
            // 2. Initialize backend components
            sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
            dataRepository = DataRepository(this)
            userStatsManager = UserStatsManager(this)
            inferenceManager = InferenceManager(this)
            messageClient = Wearable.getMessageClient(this)
            
            // 3. Register sensors with hardware batching
            val hrSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
            val accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            
            if (hrSensor == null || accSensor == null) {
                throw IllegalStateException("Required sensors not available")
            }
            
            // Register HR sensor (1Hz, no batching needed)
            sensorManager.registerListener(this, hrSensor, HR_SAMPLE_RATE_US)
            
            // Register ACC sensor with 30s batch latency (Power Optimization)
            sensorManager.registerListener(
                this, 
                accSensor, 
                ACC_SAMPLE_RATE_US,
                BATCH_LATENCY_US
            )
            
            Log.d(TAG, "Sensors registered with 30s batch latency")
            
            // 4. Create notification and promote to foreground
            createNotificationChannel()
            val notification = buildNotification()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            
            Log.i(TAG, "Foreground service started successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Service initialization failed", e)
            
            // Release WakeLock if acquired
            try {
                if (::wakeLock.isInitialized && wakeLock.isHeld) {
                    wakeLock.release()
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to release WakeLock during cleanup", ex)
            }
            
            // Stop service immediately
            stopSelf()
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sleep Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors sleep stages using sensors"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sleep Tracking Active")
            .setContentText("Monitoring sensors...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        val timestamp = System.currentTimeMillis()

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                
                if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return
                
                // Log raw data
                dataRepository.enqueueSensorData(timestamp, SensorType.ACC, x, y, z)
                
                // Update ACC window with thread safety
                synchronized(windowLock) {
                    if (accWindow.size >= ACC_WINDOW_SIZE) {
                        accWindow.removeFirst()
                    }
                    accWindow.addLast(Triple(x, y, z))
                }
            }
            
            Sensor.TYPE_HEART_RATE -> {
                val hrValue = event.values[0]
                
                if (!hrValue.isFinite() || hrValue <= 0) return

                // Log raw data
                dataRepository.enqueueSensorData(timestamp, SensorType.HR, hrValue, 0f, 0f)
                
                // Update statistics
                userStatsManager.update(hrValue)

                // Update HR window and check inference trigger
                handleHeartRateLogic(hrValue, timestamp)
            }
        }
    }

    private fun handleHeartRateLogic(hrValue: Float, timestamp: Long) {
        val shouldRunInference: Boolean
        val hrSnapshot: List<Float>
        val accSnapshot: List<Triple<Float, Float, Float>>

        synchronized(windowLock) {
            // Update HR window
            if (hrWindow.size >= HR_WINDOW_SIZE) {
                hrWindow.removeFirst()
            }
            hrWindow.addLast(hrValue)

            // Check if inference should run (60s interval)
            shouldRunInference = (hrWindow.size == HR_WINDOW_SIZE) &&
                    (accWindow.size >= ACC_WINDOW_SIZE) &&
                    (timestamp - lastFeatureExtractionTime >= FEATURE_INTERVAL_MS)
            
            if (shouldRunInference) {
                hrSnapshot = hrWindow.toList()
                accSnapshot = accWindow.toList()
            } else {
                hrSnapshot = emptyList()
                accSnapshot = emptyList()
            }
        }

        if (shouldRunInference) {
            lastFeatureExtractionTime = timestamp
            runInferencePipeline(timestamp, hrSnapshot, accSnapshot)
        }
    }

    private fun runInferencePipeline(
        timestamp: Long, 
        hrBuffer: List<Float>,
        accBuffer: List<Triple<Float, Float, Float>>
    ) {
        serviceScope.launch {
            val userMean = userStatsManager.getUserMean()
            val userStd = userStatsManager.getUserStd()

            // Extract HR features
            val hrFeatures = featureExtractor.getFeatures(hrBuffer, userMean, userStd)
            val featureString = hrFeatures.joinToString(",")

            // PyTorch Mobile inference
            val (currentStage, confidence) = inferenceManager.predict(accBuffer, hrFeatures)
            
            // Save to inference history
            val stageEntry = StageEntry(timestamp, currentStage.name)
            inferenceHistory.add(stageEntry)
            
            // Log to file with confidence score
            dataRepository.enqueueInferenceLog(
                timestamp, 
                "${currentStage.name},$confidence,$featureString,accSamples=${accBuffer.size}"
            )
            
            // Smart Window Logic
            checkSmartWindowAndTrigger(timestamp, currentStage)
            
            Log.d(TAG, "Inference completed: Stage=$currentStage (Conf: $confidence), HR=${hrBuffer.size}, ACC=${accBuffer.size}")
        }
    }
    
    
    /**
     * Smart Window 체크 및 트리거 로직
     */
    private fun checkSmartWindowAndTrigger(currentTime: Long, currentStage: SleepStage) {
        if (hasTriggered || targetAlarmTime == 0L) return
        
        val windowStart = targetAlarmTime - SMART_WINDOW_MS
        val isInWindow = currentTime in windowStart..targetAlarmTime
        
        Log.d(TAG, "SmartLogic - Time: $currentTime, Target: $targetAlarmTime, InWindow: $isInWindow, Stage: $currentStage")
        
        if (currentTime < windowStart) {
            // Too early - just log
            Log.d(TAG, "Outside window (too early). Just logging.")
            return
        }
        
        if (currentTime > targetAlarmTime) {
            // Too late - Phone handles backup alarm
            Log.d(TAG, "Outside window (too late). Phone handles backup.")
            return
        }
        
        // Inside Smart Window - Check trigger conditions
        if (isInWindow) {
            checkTriggerConditions(currentTime, currentStage)
        }
    }
    
    /**
     * 트리거 조건 체크
     * - WAKE: 즉시 트리거
     * - LIGHT: 3회 연속 시 트리거
     */
    private fun checkTriggerConditions(currentTime: Long, currentStage: SleepStage) {
        when (currentStage) {
            SleepStage.WAKE -> {
                Log.i(TAG, "WAKE detected! Triggering alarm immediately.")
                sendTriggerSignal(currentTime)
                hasTriggered = true
            }
            SleepStage.LIGHT -> {
                if (lastStage == SleepStage.LIGHT) {
                    consecutiveLightCount++
                } else {
                    consecutiveLightCount = 1
                }
                
                Log.d(TAG, "LIGHT detected (${consecutiveLightCount}/3)")
                
                if (consecutiveLightCount >= 3) {
                    Log.i(TAG, "3 consecutive LIGHT stages! Triggering alarm.")
                    sendTriggerSignal(currentTime)
                    hasTriggered = true
                }
            }
            else -> {
                consecutiveLightCount = 0
            }
        }
        
        lastStage = currentStage
    }

    override fun onDestroy() {
        // 1. Unregister sensors to stop data flow
        sensorManager.unregisterListener(this)
        
        // 2. Stop logging and flush remaining data
        dataRepository.stopLogging()
        
        // 3. Release inference model resources
        if (::inferenceManager.isInitialized) {
            inferenceManager.release()
        }
        
        // 4. Cancel coroutine scope
        serviceScope.cancel()
        
        // 5. Release WakeLock
        try {
            if (wakeLock.isHeld) {
                wakeLock.release()
                Log.d(TAG, "WakeLock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release WakeLock", e)
        }
        
        super.onDestroy()
        Log.d(TAG, "SmartAlarmService destroyed")
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBind(intent: Intent?): IBinder? = null
    
    /**
     * 폰으로 트리거 신호 전송
     */
    private fun sendTriggerSignal(triggerTime: Long) {
        serviceScope.launch {
            try {
                val connectedNodes = Tasks.await(Wearable.getNodeClient(this@SmartAlarmService).connectedNodes)
                
                if (connectedNodes.isEmpty()) {
                    Log.e(TAG, "No connected phone found")
                    return@launch
                }
                
                val phoneNodeId = connectedNodes.first().id
                val payload = ByteBuffer.allocate(8).putLong(triggerTime).array()
                
                Tasks.await(messageClient.sendMessage(phoneNodeId, PATH_TRIGGER_ALARM, payload))
                Log.i(TAG, "Trigger signal sent to phone successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send trigger signal", e)
            }
        }
    }
    
    /**
     * 서비스 중지 및 결과 전송
     */
    private fun stopAndSendResult() {
        serviceScope.launch {
            try {
                // Step 1: Prepare sleep session result
                val result = SleepSessionResult(
                    startTime = sessionStartTime,
                    endTime = System.currentTimeMillis(),
                    stageHistory = inferenceHistory.toList()
                )
                
                // Step 2: Serialize to JSON
                val jsonPayload = Json.encodeToString(result)
                Log.d(TAG, "Sleep result serialized: ${inferenceHistory.size} stages")
                
                // Step 3: Send to Phone
                val connectedNodes = Tasks.await(Wearable.getNodeClient(this@SmartAlarmService).connectedNodes)
                
                if (connectedNodes.isNotEmpty()) {
                    val phoneNodeId = connectedNodes.first().id
                    Tasks.await(messageClient.sendMessage(phoneNodeId, PATH_SLEEP_DATA_RESULT, jsonPayload.toByteArray()))
                    Log.i(TAG, "Sleep result sent to phone successfully")
                } else {
                    Log.w(TAG, "No connected phone found, data not sent")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send sleep result", e)
            } finally {
                // Step 4: Stop service
                stopSelf()
            }
        }
    }
    
    companion object {
        private const val TAG = "SmartAlarmService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "sleep_tracking_channel"
        
        // Smart Window (30 minutes before target)
        private const val SMART_WINDOW_MS = 30 * 60 * 1000L  // 30 minutes
        
        // Actions
        const val ACTION_START_TRACKING = "com.example.sleeptandard_mvp_demo.START_TRACKING"
        const val ACTION_STOP_AND_SEND_RESULT = "com.example.sleeptandard_mvp_demo.STOP_AND_SEND_RESULT"
        
        // Intent Extras
        const val EXTRA_TARGET_TIME = "EXTRA_TARGET_TIME"
        
        // Message paths to Phone
        private const val PATH_TRIGGER_ALARM = "/TRIGGER_ALARM"
        private const val PATH_SLEEP_DATA_RESULT = "/SLEEP_DATA_RESULT"
    }
}