package com.leejang.sleeptandard.backend

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.leejang.sleeptandard.BuildConfig
import com.leejang.sleeptandard.Potch.PotchDataLogger
import com.leejang.sleeptandard.Potch.PotchLogExporter
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 닫힌 Potch raw .bin 파일을 재개 가능한 S3 multipart 업로드로 예약한다.
 *
 * 현재 FastAPI 서버가 없으므로 SLEEP_SERVER_BASE_URL이 비어 있으면 작업을 예약하지 않는다.
 * 서버가 준비된 뒤 local.properties에 URL을 설정하면 앱 시작 시 미전송 파일도 다시 예약된다.
 */
object RawDataUploadManager {
    private const val TAG = "RawDataUploadManager"
    private const val UNIQUE_WORK_PREFIX = "potch_raw_multipart_upload_"

    fun enqueue(context: Context, file: File) {
        val appContext = context.applicationContext
        val validationError = PotchRawFileContract.validationError(file)
        if (validationError != null) {
            Log.e(TAG, "Raw upload 대상 제외: $validationError, file=${file.name}")
            return
        }

        if (!isServerConfigured()) {
            // TODO(Server): FastAPI 배포 후 local.properties에 SLEEP_SERVER_BASE_URL을 추가한다.
            Log.w(TAG, "TODO(Server): SLEEP_SERVER_BASE_URL 미설정, raw 업로드 보류: ${file.name}")
            return
        }

        if (uploadedMarker(file).exists()) return

        val sessionId = requireNotNull(PotchRawFileContract.sessionId(file))
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = OneTimeWorkRequestBuilder<RawDataUploadWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(RawDataUploadWorker.KEY_FILE_PATH to file.absolutePath))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            UNIQUE_WORK_PREFIX + sessionId,
            ExistingWorkPolicy.KEEP,
            request
        )
        Log.i(TAG, "Raw multipart upload 예약: ${file.name}, size=${file.length()}")
    }

    /** 앱 프로세스가 재시작되어도 이전에 닫힌 미전송 파일을 복구한다. */
    fun enqueuePendingClosedFiles(context: Context) {
        if (!isServerConfigured()) return
        PotchDataLogger.listInternalLogFiles(context)
            .asSequence()
            .filter { it.name.startsWith("potch_packet_raw_data_") && it.name.endsWith(".bin") }
            .filter { PotchLogExporter.isClosed(context, it.name) }
            .map { File(it.absolutePath) }
            .filterNot { uploadedMarker(it).exists() }
            .forEach { enqueue(context, it) }
    }

    fun uploadedMarker(file: File): File = File(file.parentFile, "${file.name}.s3-uploaded")

    private fun isServerConfigured(): Boolean =
        BuildConfig.SLEEP_SERVER_BASE_URL.trim().let { it.isNotEmpty() && it != "null" }
}
