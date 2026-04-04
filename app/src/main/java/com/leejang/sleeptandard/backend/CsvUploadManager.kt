package com.leejang.sleeptandard.backend

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * CsvUploadManager
 *
 * WorkManager를 통해 CSV 파일을 Supabase Storage에 업로드합니다.
 * - 네트워크 연결 시에만 실행 (와이파이/데이터 자동 대기)
 * - 실패 시 최대 3회 자동 재시도 (Exponential Backoff)
 * - 앱/서비스 종료 와 무관하게 OS 수준에서 안정적으로 실행
 *
 * 업로드 경로: sleep-logs/{userId}/{날짜}/파일명
 */
object CsvUploadManager {

    private const val TAG = "CsvUploadManager"

    /**
     * CSV 파일 업로드를 WorkManager에 등록합니다.
     * - 네트워크가 없으면 연결될 때까지 대기 후 자동 실행
     * - 실패 시 최대 3회 Exponential Backoff 재시도
     *
     * @param context Application Context
     * @param file 업로드할 CSV 파일
     */
    fun enqueueUpload(context: Context, file: File) {
        if (!file.exists()) {
            Log.e(TAG, "❌ File not found: ${file.absolutePath}")
            return
        }

        // 제약 조건: 네트워크 연결 시에만 실행 (와이파이 or 모바일 데이터)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Worker에 파일 경로 전달
        val inputData = workDataOf(
            CsvUploadWorker.KEY_FILE_PATH to file.absolutePath
        )

        // 작업 요청 빌드: 실패 시 30초 후 재시도, Exponential Backoff
        val uploadRequest = OneTimeWorkRequestBuilder<CsvUploadWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueue(uploadRequest)
        Log.i(TAG, "📋 Upload enqueued for: ${file.name} (${file.length() / 1024}KB)")
    }
}
