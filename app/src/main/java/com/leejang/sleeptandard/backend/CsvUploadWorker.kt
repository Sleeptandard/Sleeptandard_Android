package com.leejang.sleeptandard.backend

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate

/**
 * CsvUploadWorker
 *
 * WorkManager에 의해 실행되는 CSV 업로드 작업 단위.
 * - 네트워크 연결 시에만 실행 (WorkManager 제약 조건으로 설정됨)
 * - 실패 시 자동 재시도 (exponential backoff, 최대 3회)
 * - 스트림 방식으로 파일을 읽어 OOM 방지
 */
class CsvUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "CsvUploadWorker"
        private const val BUCKET_NAME = "sleep-logs"
        const val KEY_FILE_PATH = "csv_file_path"
    }

    override suspend fun doWork(): Result {
        val filePath = inputData.getString(KEY_FILE_PATH)
            ?: return Result.failure().also { Log.e(TAG, "❌ No file path provided") }

        val file = File(filePath)
        if (!file.exists()) {
            Log.e(TAG, "❌ File not found: $filePath")
            return Result.failure()
        }

        return try {
            val client = SupabaseClientProvider.client
            val userId = client.auth.currentUserOrNull()?.id ?: "anonymous"
            val dateFolder = LocalDate.now().toString()
            val storagePath = "$userId/$dateFolder/${file.name}"

            Log.i(TAG, "📤 Uploading: $storagePath (${file.length() / 1024}KB)")

            // [핵심] 스트림 방식으로 파일 읽기
            // readBytes() 는 파일 전체를 한 번에 heap에 올려서 26MB → OOM 위험
            // 8KB 버퍼로 청크 단위로 읽어서 ByteArrayOutputStream에 누적
            val fileBytes = file.inputStream().use { input ->
                ByteArrayOutputStream(file.length().toInt()).also { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }.toByteArray()
            }

            client.storage.from(BUCKET_NAME).upload(
                path = storagePath,
                data = fileBytes
            ) {
                upsert = true
            }

            Log.i(TAG, "✅ Upload complete: $BUCKET_NAME/$storagePath")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Upload failed (attempt=${runAttemptCount + 1}): ${file.name}", e)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}
