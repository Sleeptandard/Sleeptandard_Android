package com.leejang.sleeptandard.backend

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.leejang.sleeptandard.BuildConfig
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * CsvUploadWorker
 *
 * WorkManager에 의해 실행되는 CSV 업로드 작업 단위.
 * - OkHttp + file.asRequestBody()를 사용한 진짜 스트리밍 업로드
 *   → 파일 전체를 메모리에 올리지 않아 26MB+ 파일도 OOM 없이 안전하게 전송
 * - 네트워크 연결 시에만 실행 (WorkManager 제약 조건)
 * - 실패 시 최대 3회 자동 재시도 (Exponential Backoff)
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

    // 타임아웃을 넉넉하게 설정 (26MB 전송 시 시간이 걸릴 수 있음)
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.MINUTES)  // 대용량 파일 업로드를 고려한 여유로운 타임아웃
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result {
        val filePath = inputData.getString(KEY_FILE_PATH)
            ?: return Result.failure().also { Log.e(TAG, "❌ No file path provided") }

        val file = File(filePath)
        if (!file.exists()) {
            Log.e(TAG, "❌ File not found: $filePath")
            return Result.failure()
        }

        return try {
            val supabaseClient = SupabaseClientProvider.client

            // 로그인된 유저 UID (없으면 "anonymous")
            val userId = supabaseClient.auth.currentUserOrNull()?.id ?: "anonymous"

            // 액세스 토큰 (로그인 시 JWT, 없으면 anon key 사용)
            val accessToken = supabaseClient.auth.currentSessionOrNull()?.accessToken
                ?: BuildConfig.SUPABASE_ANON_KEY

            // Storage 경로: {userId}/{날짜}/{파일명}
            val dateFolder = LocalDate.now().toString()
            val storagePath = "$userId/$dateFolder/${file.name}"

            // Supabase Storage REST API 업로드 URL
            val uploadUrl = "${BuildConfig.SUPABASE_URL}/storage/v1/object/$BUCKET_NAME/$storagePath"

            Log.i(TAG, "📤 Uploading: $storagePath (${file.length() / 1024}KB)")

            // [핵심] OkHttp file.asRequestBody()
            // - 파일을 통째로 메모리에 올리지 않고 디스크에서 네트워크로 직접 스트리밍
            // - 어떤 크기의 파일이든 OOM 없이 안전하게 전송 가능
            val mediaType = "text/csv; charset=utf-8".toMediaTypeOrNull()
            val requestBody = file.asRequestBody(mediaType)

            val request = Request.Builder()
                .url(uploadUrl)
                .header("Authorization", "Bearer $accessToken")
                .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .header("x-upsert", "true")
                .header("Content-Type", "text/csv; charset=utf-8")
                .post(requestBody)
                .build()

            val response = withContext(Dispatchers.IO) {
                okHttpClient.newCall(request).execute()
            }

            if (response.isSuccessful) {
                Log.i(TAG, "✅ Upload complete: $BUCKET_NAME/$storagePath (${file.length() / 1024}KB)")
                Result.success()
            } else {
                val errorBody = response.body?.string() ?: "unknown error"
                Log.e(TAG, "❌ Upload failed (HTTP ${response.code}): $errorBody")
                if (runAttemptCount < 2) Result.retry() else Result.failure()
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Upload exception (attempt=${runAttemptCount + 1}): ${file.name}", e)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}
