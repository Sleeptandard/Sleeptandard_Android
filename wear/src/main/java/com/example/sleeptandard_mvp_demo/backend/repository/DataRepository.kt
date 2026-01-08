package com.example.sleeptandard_mvp_demo.backend.repository

import android.content.Context
import android.util.Log
import com.example.sleeptandard_mvp_demo.backend.model.SensorType
import java.io.File
import java.io.FileWriter
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

class DataRepository(private val context: Context) {

    private val QUEUE_CAPACITY = 2048
    private val dataQueue = LinkedBlockingQueue<LogEvent>(QUEUE_CAPACITY)
    
    @Volatile private var isLogging = false
    private var logThread: Thread? = null

    private val SENSOR_FILE_NAME = "sensor_log.csv"
    private val INFERENCE_FILE_NAME = "inference_log.csv"

    init {
        startConsumer()
    }

    // 외부 API는 원시값(Primitive)을 받아 객체 생성 비용 최소화 유지
    fun enqueueSensorData(timestamp: Long, type: SensorType, x: Float, y: Float, z: Float) {
        if (!isLogging) return
        offerWithDropOldest(LogEvent.SensorData(timestamp, type, x, y, z))
    }

    fun enqueueInferenceLog(timestamp: Long, result: String) {
        if (!isLogging) return
        offerWithDropOldest(LogEvent.InferenceLog(timestamp, result))
    }

    private fun offerWithDropOldest(event: LogEvent) {
        if (!dataQueue.offer(event)) {
            dataQueue.poll() // Backpressure: 오래된 데이터 버림
            if (!dataQueue.offer(event)) {
                Log.w("DataRepository", "Queue full, dropping event")
            }
        }
    }

    private fun startConsumer() {
        isLogging = true
        logThread = thread(start = true, name = "LogWriterThread") {
            val sensorFile = File(context.filesDir, SENSOR_FILE_NAME)
            val inferenceFile = File(context.filesDir, INFERENCE_FILE_NAME)

            ensureHeader(sensorFile, "Timestamp,Type,X,Y,Z\n")
            ensureHeader(inferenceFile, "Tag,Timestamp,Result,Details\n")

            try {
                FileWriter(sensorFile, true).bufferedWriter().use { sensorWriter ->
                    FileWriter(inferenceFile, true).bufferedWriter().use { inferenceWriter ->

                        // [핵심 수정] isLogging이 false가 되어도 큐에 남은 데이터(!isEmpty)는 다 처리하고 종료
                        while (isLogging || !dataQueue.isEmpty()) {
                            try {
                                val event = dataQueue.poll(3000, TimeUnit.MILLISECONDS) ?: continue

                                when (event) {
                                    is LogEvent.SensorData -> {
                                        sensorWriter.write("${event.timestamp},${event.type},${event.x},${event.y},${event.z}\n")
                                    }

                                    is LogEvent.InferenceLog -> {
                                        inferenceWriter.write("INFERENCE_LOG,${event.timestamp},${event.result}\n")
                                        inferenceWriter.flush()
                                    }

                                    is LogEvent.Stop -> {
                                        // [핵심 수정] 즉시 break 하지 않음.
                                        // 더 이상 새로운 데이터를 받지 않겠다고 플래그만 내림.
                                        // while 문의 !dataQueue.isEmpty() 조건에 의해 남은 데이터를 모두 처리하게 됨.
                                        isLogging = false
                                    }
                                }
                            } catch (e: InterruptedException) {
                                Thread.currentThread().interrupt()
                                break
                            } catch (e: Exception) {
                                Log.e("DataRepository", "Writing Error", e)
                            }
                        }
                        // 루프 종료 후 최종 플러시
                        sensorWriter.flush()
                        inferenceWriter.flush()
                    }
                }
            } catch (e: Exception) {
                Log.e("DataRepository", "File Stream Error", e)
            } finally {
                logThread = null
            }
        }


    }

    fun stopLogging() {
        isLogging = false
        // 종료 신호 주입 (Spin-lock)
        while (!dataQueue.offer(LogEvent.Stop)) {
            dataQueue.poll()
        }
        // 스레드를 interrupt 하지 않고 자연스럽게 종료되도록 유도 (잔여 데이터 처리를 위해)
        // 필요하다면 타임아웃 후 interrupt 하는 로직을 추가할 수 있음
    }

    private fun ensureHeader(file: File, header: String) {
        if (!file.exists()) {
            try {
                FileWriter(file).use { it.write(header) }
            } catch (e: Exception) { e.printStackTrace() }
        }

    }
}