package com.leejang.sleeptandard.backend

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * CsvUploadManager
 *
 * WorkManager를 통해 CSV 파일을 Supabase Storage에 업로드합니다.
 *
 * [핵심 개선]
 * - setExpedited(): Doze 모드를 우회하여 즉시 실행 보장 (Android 12+)
 * - 순차 업로드(APPEND): inference_log → sensor_log 순서로 하나씩 처리
 * - 실패 시 최대 3회 자동 재시도 (Exponential Backoff)
 */
object CsvUploadManager {

    private const val TAG = "CsvUploadManager"

    // 순차 업로드를 위한 고유 작업 체인 이름
    private const val UNIQUE_WORK_NAME = "sleep_csv_upload_chain"

    /**
     * CSV 파일 업로드를 WorkManager에 등록합니다.
     * - Expedited 작업으로 Doze 모드를 우회하여 즉시 실행
     * - APPEND 정책으로 이미 등록된 작업 뒤에 순차적으로 이어붙임
     *   (inference_log와 sensor_log가 동시에 경쟁하지 않도록 직렬화)
     */
    fun enqueueUpload(context: Context, file: File) {
        if (!file.exists()) {
            Log.e(TAG, "❌ File not found: ${file.absolutePath}")
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = workDataOf(
            CsvUploadWorker.KEY_FILE_PATH to file.absolutePath
        )

        val uploadRequest = OneTimeWorkRequestBuilder<CsvUploadWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            // [핵심 1] Expedited: Doze 모드를 우회, Android 12+ 에서 즉시 실행
            // Android 12 미만에서는 일반 WorkManager처럼 동작 (fallback)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        // [핵심 2] beginUniqueWork + APPEND: 순차 실행 보장
        // - 동일한 체인 이름에 APPEND 정책을 사용하면 이전 작업이 끝난 뒤 다음 작업을 실행
        // - inference_log와 sensor_log가 동시에 실행되어 네트워크 경쟁하지 않음
        WorkManager.getInstance(context)
            .beginUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, uploadRequest)
            .enqueue()

        Log.i(TAG, "📋 Upload enqueued (expedited, sequential): ${file.name} (${file.length() / 1024}KB)")
    }

    /**
     * CsvUploadWorker에서 Expedited 작업 실행을 위해 필요한 ForegroundInfo 생성
     * (WorkManager가 내부적으로 호출)
     */
    fun createForegroundInfo(context: Context, fileName: String): ForegroundInfo {
        val channelId = "csv_upload_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "수면 데이터 업로드",
                NotificationManager.IMPORTANCE_LOW
            )
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("수면 데이터 업로드 중")
            .setContentText(fileName)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(3001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(3001, notification)
        }
    }
}
