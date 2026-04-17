package com.leejang.sleeptandard.backend

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
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
 * WorkManager(Expedited)에 의해 실행되는 CSV 업로드 작업 단위.
 * - Expedited 작업으로 Doze 모드를 우회 (Android 12+)
 * - OkHttp file.asRequestBody() 스트리밍: 파일 전체를 메모리에 올리지 않음
 * - 실패 시 최대 3회 자동 재시도 (Exponential Backoff)
 */
class CsvUploadWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "CsvUploadWorker"
        private const val BUCKET_NAME = "sleep-logs"
        const val KEY_FILE_PATH = "csv_file_path"
    }

    // 타임아웃을 넉넉하게 설정 (대용량 파일 고려)
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.MINUTES)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Expedited 작업 실행을 위해 반드시 필요한 ForegroundInfo 반환
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        return CsvUploadManager.createForegroundInfo(appContext, "수면 데이터 업로드 중...")
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
            val supabaseClient = SupabaseClientProvider.client

            // [핵심 해결책] 백그라운드에서 앱 프로세스가 시작되었을 때,
            // Supabase 플러그인이 로컬 디스크에서 세션을 읽어오기를 기다려야 함
            supabaseClient.auth.awaitInitialization()

            // 세션 강제 갱신 - 백그라운드에서 오래 대기 시 토큰 만료 방지
            try {
                // awaitInitialization() 이후라야 refreshCurrentSession 이 어떤 세션을 갱신할지 압니다.
                supabaseClient.auth.refreshCurrentSession()
                Log.i(TAG, "🔄 Session refreshed successfully")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Session refresh failed (will try with existing/anon): ${e.message}")
            }

            val userId = supabaseClient.auth.currentUserOrNull()?.id ?: "anonymous"
            val accessToken = supabaseClient.auth.currentSessionOrNull()?.accessToken
                ?: BuildConfig.SUPABASE_ANON_KEY

            val dateFolder = LocalDate.now().toString()
            val storagePath = "$userId/$dateFolder/${file.name}"
            val uploadUrl = "${BuildConfig.SUPABASE_URL}/storage/v1/object/$BUCKET_NAME/$storagePath"

            Log.i(TAG, "📤 Uploading: $storagePath (${file.length() / 1024}KB)")

            // OkHttp 스트리밍 업로드 - 파일을 디스크에서 네트워크로 직접 전송
            // file.asRequestBody() = OOM 없는 진짜 스트리밍
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
                // 업로드 성공 마커 파일 생성 → MainActivity에서 중복 재업로드 방지용
                File(file.parent, "${file.name}.uploaded").createNewFile()
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
