package com.leejang.sleeptandard.backend.repository

import android.content.Context
import android.util.Log
import com.leejang.sleeptandard.backend.model.SensorType
import java.io.File
import java.io.FileOutputStream // [수정] FileWriter 대신 FileOutputStream 사용
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

// [구조 개선] LogEvent를 리포지토리 내부 구현 상세로 은닉
private sealed class LogEvent {
    data class SensorData(
        val timestamp: Long,
        val type: SensorType,
        val x: Float, val y: Float, val z: Float
    ) : LogEvent()

    data class InferenceLog(
        val timestamp: Long,
        val result: String
    ) : LogEvent()

    object Stop : LogEvent()
}

class DataRepository(
    private val context: Context,
    private val situationLabel: String = "normal" // [추가] 특별 상황 라벨 (optional)
) {

    private val QUEUE_CAPACITY = 2048
    private val dataQueue = LinkedBlockingQueue<LogEvent>(QUEUE_CAPACITY)

    @Volatile private var isLogging = false
    private var logThread: Thread? = null
    private var completionLatch: CountDownLatch? = null

    // [개선] 세션 시작 시간을 파일명에 포함하여 고유한 로그 파일 생성
    private val sessionTimestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        .format(Date(System.currentTimeMillis()))

    // [추가] 라벨을 파일명에 포함 (특수문자 제거하여 파일 시스템 안전성 확보)
    private val sanitizedLabel = situationLabel
        .replace(Regex("[^a-zA-Z0-9가-힣_]"), "")
        .take(30) // 파일명 길이 제한

    private val sensorFileName = "sensor_log_${sanitizedLabel}_${sessionTimestamp}.csv"
    private val inferenceFileName = "inference_log_${sanitizedLabel}_${sessionTimestamp}.csv"

    init {
        Log.i(TAG, "DataRepository initialized. Session: $sessionTimestamp, Label: $situationLabel")
        startConsumer()
    }

    companion object {
        private const val TAG = "DataRepository"
    }

    // 외부 API는 원시값(Primitive)을 받아 객체 생성 비용 최소화 유지
    fun enqueueSensorData(timestamp: Long, type: SensorType, x: Float, y: Float, z: Float) {
        if (!isLogging) return
        offerWithDropOldest(LogEvent.SensorData(timestamp, type, x, y, z))
    }

    fun enqueueInferenceLog(timestamp: Long, result: String) {
        if (!isLogging) {
            Log.w(TAG, "⚠️ enqueueInferenceLog called but isLogging=false! Data will be lost!")
            return
        }
        Log.d(TAG, "📊 Enqueueing inference log: $result")
        offerWithDropOldest(LogEvent.InferenceLog(timestamp, result))
    }

    private fun offerWithDropOldest(event: LogEvent) {
        if (!dataQueue.offer(event)) {
            dataQueue.poll() // Backpressure: 오래된 데이터 버림
            if (!dataQueue.offer(event)) {
                Log.w(TAG, "Queue full, dropping event")
            }
        }
    }

    private fun startConsumer() {
        isLogging = true
        completionLatch = CountDownLatch(1)
        logThread = thread(start = true, name = "LogWriterThread") {
            val sensorFile = File(context.filesDir, sensorFileName)
            val inferenceFile = File(context.filesDir, inferenceFileName)
            
            Log.i(TAG, "📁 Creating log files:")
            Log.i(TAG, "  - Sensor: ${sensorFile.absolutePath}")
            Log.i(TAG, "  - Inference: ${inferenceFile.absolutePath}")

            ensureHeader(sensorFile, "Timestamp,Type,X,Y,Z\n")
            ensureHeader(inferenceFile, "Tag,Timestamp,Result,Details\n")
            
            Log.i(TAG, "✅ Log files initialized, starting consumer loop...")

            try {
                var sensorDataCount = 0L
                var inferenceLogCount = 0L

                // [핵심 수정] FileWriter -> FileOutputStream으로 변경하여 bufferedWriter() 확장 함수 사용 가능하게 함
                FileOutputStream(sensorFile, true).bufferedWriter().use { sensorWriter ->
                    FileOutputStream(inferenceFile, true).bufferedWriter().use { inferenceWriter ->

                        // isLogging이 false가 되어도 큐에 남은 데이터(!isEmpty)는 다 처리하고 종료
                        while (isLogging || !dataQueue.isEmpty()) {
                            try {
                                val event = dataQueue.poll(3000, TimeUnit.MILLISECONDS) ?: continue

                                when (event) {
                                    is LogEvent.SensorData -> {
                                        sensorWriter.write("${event.timestamp},${event.type},${event.x},${event.y},${event.z}\n")
                                        sensorDataCount++
                                    }

                                    is LogEvent.InferenceLog -> {
                                        val line = "INFERENCE_LOG,${event.timestamp},${event.result}\n"
                                        inferenceWriter.write(line)
                                        inferenceWriter.flush()
                                        inferenceLogCount++
                                        Log.d(TAG, "✅ Inference log written to file (#$inferenceLogCount): ${event.result}")
                                    }

                                    is LogEvent.Stop -> {
                                        // 더 이상 새로운 데이터를 받지 않겠다고 플래그만 내림.
                                        // while 문의 !dataQueue.isEmpty() 조건에 의해 남은 데이터를 모두 처리하게 됨.
                                        Log.i(TAG, "🛑 Stop signal received in writer thread")
                                        isLogging = false
                                    }
                                }
                            } catch (e: InterruptedException) {
                                Thread.currentThread().interrupt()
                                break
                            } catch (e: Exception) {
                                Log.e(TAG, "Writing Error", e)
                            }
                        }
                        // 루프 종료 후 최종 플러시
                        sensorWriter.flush()
                        inferenceWriter.flush()
                        
                        Log.i(TAG, "📊 Final Statistics:")
                        Log.i(TAG, "  - Sensor data written: $sensorDataCount lines")
                        Log.i(TAG, "  - Inference logs written: $inferenceLogCount lines")
                        Log.i(TAG, "  - Sensor file size: ${sensorFile.length()} bytes")
                        Log.i(TAG, "  - Inference file size: ${inferenceFile.length()} bytes")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "File Stream Error", e)
            } finally {
                logThread = null
                completionLatch?.countDown()  // 로그 쓰기 완료 신호
                Log.d(TAG, "LogWriterThread completed, latch released")
            }
        }


    }

    fun stopLogging() {
        Log.i(TAG, "🛑 stopLogging() called | Queue size: ${dataQueue.size}")
        isLogging = false
        // 종료 신호 주입 (Spin-lock)
        while (!dataQueue.offer(LogEvent.Stop)) {
            dataQueue.poll()
        }
        Log.d(TAG, "Stop signal sent to LogWriterThread | Remaining queue items: ${dataQueue.size}")
    }
    
    /**
     * 로그 쓰기 완료를 대기
     * 
     * @param timeoutMs 대기 시간 (밀리초)
     * @return true: 정상 완료, false: 타임아웃
     */
    fun waitForCompletion(timeoutMs: Long = 5000): Boolean {
        return try {
            val latch = completionLatch
            if (latch == null) {
                Log.w(TAG, "CompletionLatch not initialized, assuming already completed")
                true
            } else {
                val completed = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
                if (completed) {
                    Log.i(TAG, "✅ Log writing completed successfully")
                } else {
                    Log.w(TAG, "⚠️ Log writing timeout after ${timeoutMs}ms")
                }
                completed
            }
        } catch (e: InterruptedException) {
            Log.e(TAG, "Wait interrupted", e)
            Thread.currentThread().interrupt()
            false
        }
    }

    private fun ensureHeader(file: File, header: String) {
        if (!file.exists()) {
            try {
                // [핵심 수정] 파일 생성 시에도 FileOutputStream 사용 (Byte Array 방식이 더 안전)
                FileOutputStream(file).use { it.write(header.toByteArray()) }
                Log.i(TAG, "✅ Created file with header: ${file.name}")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to create header for ${file.name}", e)
                e.printStackTrace()
            }
        } else {
            Log.d(TAG, "📄 File already exists: ${file.name}")
        }
    }
}