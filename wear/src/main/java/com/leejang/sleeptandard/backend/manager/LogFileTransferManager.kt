package com.leejang.sleeptandard.backend.manager

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * LogFileTransferManager
 * 
 * Wear OS의 로그 파일을 Phone으로 전송하는 매니저
 * - ChannelClient 사용 (큰 파일 전송 가능)
 * - 가장 최근 sensor_log, inference_log만 전송
 * - 전송 완료 후 파일 삭제
 * 
 * ⚠️ [주의] ⚠️
 * 이 클래스의 메서드(sendLatestLogsToPhone 등)를 직접 호출하지 마세요.
 * 파일 전송 중 화면이 꺼지면(Doze 모드 진입 시) 전송이 중단될 수 있습니다.
 * 반드시 백그라운드 실행을 보장하는 `LogTransferService`를 통해서만 호출해야 합니다.
 */
class LogFileTransferManager(private val context: Context) {

    companion object {
        private const val TAG = "LogFileTransferManager"
        private const val CHANNEL_PATH_PREFIX = "/sleep_log_transfer"
    }

    /**
     * 가장 최근 로그 파일들을 Phone으로 전송
     * 
     * @return 전송 성공 여부
     */
    suspend fun sendLatestLogsToPhone(): Result<Int> {
        return try {
            val logFiles = getLatestLogFiles()
            
            if (logFiles.isEmpty()) {
                Log.w(TAG, "No log files found to transfer")
                return Result.failure(Exception("전송할 로그 파일이 없습니다"))
            }

            Log.i(TAG, "Found ${logFiles.size} log files to transfer")
            
            // 연결된 노드 찾기
            val nodeClient = Wearable.getNodeClient(context)
            val nodes = nodeClient.connectedNodes.await()
            
            if (nodes.isEmpty()) {
                Log.e(TAG, "No connected nodes found")
                return Result.failure(Exception("연결된 디바이스가 없습니다"))
            }

            val phoneNodeId = nodes.first().id
            Log.i(TAG, "Transferring to node: $phoneNodeId")

            var successCount = 0
            
            // 각 파일 전송
            logFiles.forEach { file ->
                try {
                    transferFile(phoneNodeId, file)
                    successCount++
                    Log.i(TAG, "✅ Successfully transferred: ${file.name}")
                    
                    // 전송 성공 시 파일 삭제
                    if (file.delete()) {
                        Log.i(TAG, "🗑️ Deleted transferred file: ${file.name}")
                    } else {
                        Log.w(TAG, "Failed to delete file: ${file.name}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to transfer ${file.name}", e)
                }
            }

            if (successCount > 0) {
                Result.success(successCount)
            } else {
                Result.failure(Exception("파일 전송에 실패했습니다"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Transfer failed", e)
            Result.failure(e)
        }
    }

    /**
     * ChannelClient를 사용하여 파일 전송
     */
    private suspend fun transferFile(nodeId: String, file: File) {
        val channelClient = Wearable.getChannelClient(context)
        
        // 채널 경로에 파일명 포함
        val channelPath = "$CHANNEL_PATH_PREFIX/${file.name}"
        
        val fileSize = file.length()
        Log.d(TAG, "Opening channel: $channelPath (${fileSize} bytes)")
        
        // [경고] 파일이 너무 작으면 경고 표시
        if (fileSize < 100) {
            Log.w(TAG, "⚠️ WARNING: ${file.name} is very small (${fileSize} bytes)")
            Log.w(TAG, "⚠️ File may contain only header, which can cause sharing issues")
            
            // 파일 내용 확인 (디버깅용)
            try {
                val lineCount = file.readLines().size
                Log.w(TAG, "⚠️ Line count: $lineCount (header + data)")
                if (lineCount <= 1) {
                    Log.e(TAG, "❌ File contains NO DATA, only header!")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read file for validation", e)
            }
        }
        
        // 채널 열기
        val channel = channelClient.openChannel(nodeId, channelPath).await()
        
        try {
            // 파일 전송
            val outputStream = channelClient.getOutputStream(channel).await()
            
            outputStream.use { output ->
                file.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytes = 0L
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                    }
                    
                    Log.d(TAG, "Transferred $totalBytes bytes for ${file.name}")
                }
            }
            
            // 채널 닫기
            channelClient.close(channel).await()
            Log.i(TAG, "Channel closed for ${file.name}")
            
        } catch (e: Exception) {
            // 에러 발생 시 채널 닫기 시도
            try {
                channelClient.close(channel).await()
            } catch (closeError: Exception) {
                Log.e(TAG, "Failed to close channel", closeError)
            }
            throw e
        }
    }

    /**
     * filesDir에서 가장 최근 sensor_log와 inference_log 파일 찾기
     */
    private fun getLatestLogFiles(): List<File> {
        val filesDir = context.filesDir
        
        // 모든 로그 파일 찾기
        val allFiles = filesDir.listFiles { file ->
            file.name.startsWith("sensor_log_") && file.name.endsWith(".csv") ||
            file.name.startsWith("inference_log_") && file.name.endsWith(".csv")
        } ?: emptyArray()

        // 타입별로 그룹화
        val sensorLogs = allFiles.filter { it.name.startsWith("sensor_log_") }
            .sortedByDescending { it.lastModified() }
        
        val inferenceLogs = allFiles.filter { it.name.startsWith("inference_log_") }
            .sortedByDescending { it.lastModified() }

        // 가장 최근 파일만 선택
        val result = mutableListOf<File>()
        
        sensorLogs.firstOrNull()?.let { result.add(it) }
        inferenceLogs.firstOrNull()?.let { result.add(it) }

        Log.i(TAG, "Latest log files found:")
        result.forEach { file ->
            Log.i(TAG, "  - ${file.name} (${file.length() / 1024}KB, modified: ${file.lastModified()})")
        }

        return result
    }

    /**
     * 저장된 로그 파일 개수 및 총 크기 확인
     */
    fun getLogFileStats(): LogFileStats {
        val filesDir = context.filesDir
        
        val logFiles = filesDir.listFiles { file ->
            file.name.endsWith(".csv")
        } ?: emptyArray()

        val totalSize = logFiles.sumOf { it.length() }
        
        return LogFileStats(
            fileCount = logFiles.size,
            totalSizeBytes = totalSize,
            files = logFiles.map { FileInfo(it.name, it.length(), it.lastModified()) }
        )
    }
}

/**
 * 로그 파일 통계 정보
 */
data class LogFileStats(
    val fileCount: Int,
    val totalSizeBytes: Long,
    val files: List<FileInfo>
) {
    val totalSizeMB: Double get() = totalSizeBytes / (1024.0 * 1024.0)
}

data class FileInfo(
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long
) {
    val sizeMB: Double get() = sizeBytes / (1024.0 * 1024.0)
}
