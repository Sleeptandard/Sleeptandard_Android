package com.leejang.sleeptandard.backend

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.leejang.sleeptandard.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

/** FastAPI가 발급한 UploadPart Presigned URL로 Potch raw file을 S3에 재개 가능하게 업로드한다. */
class RawDataUploadWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_FILE_PATH = "raw_file_path"
        private const val TAG = "RawDataUploadWorker"
        private const val MAX_WORK_ATTEMPTS = 5
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val BINARY_MEDIA_TYPE = PotchRawFileContract.CONTENT_TYPE.toMediaType()
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.MINUTES)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val stateStore = UploadStateStore(appContext)
    private val baseUrl = BuildConfig.SLEEP_SERVER_BASE_URL.trim().trimEnd('/')

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val filePath = inputData.getString(KEY_FILE_PATH)
            ?: return@withContext Result.failure(workDataOf("error" to "raw file path 누락"))
        val file = File(filePath)
        val validationError = PotchRawFileContract.validationError(file)
        if (validationError != null) {
            return@withContext Result.failure(workDataOf("error" to validationError))
        }
        if (baseUrl.isEmpty() || baseUrl == "null") {
            Log.w(TAG, "TODO(Server): SLEEP_SERVER_BASE_URL이 설정되지 않음")
            return@withContext Result.failure(workDataOf("error" to "server URL 미설정"))
        }

        val sessionId = requireNotNull(PotchRawFileContract.sessionId(file))
        val initialSize = file.length()
        val initialModified = file.lastModified()

        try {
            val sha256 = PotchRawFileContract.sha256Hex(file)
            var localState = stateStore.load(sessionId)?.takeIf {
                it.fileSizeBytes == initialSize &&
                    it.fileLastModifiedMillis == initialModified &&
                    it.fileSha256 == sha256
            } ?: initiateUpload(file, sessionId, sha256).also {
                stateStore.save(sessionId, it)
            }

            val remoteStatus = try {
                fetchStatus(sessionId, localState.uploadId)
            } catch (error: HttpStatusException) {
                if (error.code != 404) throw error
                // S3 lifecycle/abort로 upload가 사라졌다면 새 multipart upload을 생성한다.
                stateStore.clear(sessionId)
                localState = initiateUpload(file, sessionId, sha256).also {
                    stateStore.save(sessionId, it)
                }
                UploadStatus(status = "UPLOADING", uploadedParts = emptyList())
            }

            if (remoteStatus.status == "COMPLETE") {
                markComplete(file, sessionId)
                return@withContext Result.success()
            }
            check(remoteStatus.status == "UPLOADING") {
                "업로드를 재개할 수 없는 상태: ${remoteStatus.status}"
            }

            val partSize = localState.partSizeBytes
            require(partSize > 0L)
            require(initialSize <= partSize || partSize >= PotchRawFileContract.MIN_NON_FINAL_PART_SIZE_BYTES) {
                "S3 multipart partSize가 5MiB보다 작음: $partSize"
            }

            val totalParts = ceil(initialSize.toDouble() / partSize.toDouble()).toInt()
            remoteStatus.uploadedParts.forEach { part ->
                require(part.partNumber in 1..totalParts) {
                    "서버가 반환한 partNumber가 범위를 벗어남: ${part.partNumber}"
                }
                val expectedOffset = (part.partNumber - 1L) * partSize
                val expectedSize = minOf(partSize, initialSize - expectedOffset)
                require(part.sizeBytes == expectedSize) {
                    "part ${part.partNumber} 크기 불일치: expected=$expectedSize actual=${part.sizeBytes}"
                }
                require(part.checksumCrc32c.isNotBlank()) {
                    "part ${part.partNumber} CRC32C가 서버 상태 응답에 없음"
                }
            }
            val completed = remoteStatus.uploadedParts.associateBy { it.partNumber }.toMutableMap()

            for (partNumber in 1..totalParts) {
                if (completed.containsKey(partNumber)) continue
                ensureFileUnchanged(file, initialSize, initialModified)

                val offset = (partNumber - 1L) * partSize
                val byteCount = minOf(partSize, initialSize - offset)
                val checksum = PotchRawFileContract.crc32cBase64(file, offset, byteCount)
                val presigned = requestPresignedPart(
                    sessionId = sessionId,
                    uploadId = localState.uploadId,
                    partNumber = partNumber,
                    byteCount = byteCount,
                    checksumCrc32c = checksum
                )
                val etag = uploadPart(file, offset, byteCount, checksum, presigned)
                completed[partNumber] = UploadedPart(partNumber, etag, checksum, byteCount)

                setProgress(
                    workDataOf(
                        "uploaded_parts" to completed.size,
                        "total_parts" to totalParts,
                        "file_name" to file.name
                    )
                )
            }

            ensureFileUnchanged(file, initialSize, initialModified)
            val orderedParts = (1..totalParts).map { partNumber ->
                completed[partNumber]
                    ?: throw IOException("완료 요청에 필요한 part $partNumber ETag가 없음")
            }
            completeUpload(sessionId, localState.uploadId, orderedParts, initialSize, sha256)
            markComplete(file, sessionId)
            Log.i(TAG, "Raw multipart upload 완료: ${file.name}, parts=$totalParts")
            Result.success()
        } catch (error: HttpStatusException) {
            Log.e(TAG, "FastAPI 업로드 오류 HTTP ${error.code}: ${error.responseBody}")
            if (error.isRetryable && runAttemptCount + 1 < MAX_WORK_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure(workDataOf("http_status" to error.code, "error" to error.responseBody))
            }
        } catch (error: Exception) {
            Log.e(TAG, "Raw multipart upload 실패, attempt=${runAttemptCount + 1}", error)
            if (runAttemptCount + 1 < MAX_WORK_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure(workDataOf("error" to (error.message ?: error.javaClass.simpleName)))
            }
        }
    }

    private fun initiateUpload(file: File, sessionId: String, sha256: String): UploadState {
        val payload = JSONObject()
            .put("formatVersion", PotchRawFileContract.FORMAT_VERSION)
            .put("fileName", file.name)
            .put("contentType", PotchRawFileContract.CONTENT_TYPE)
            .put("sizeBytes", file.length())
            .put("recordSizeBytes", PotchRawFileContract.RECORD_SIZE_BYTES)
            .put("recordCount", file.length() / PotchRawFileContract.RECORD_SIZE_BYTES)
            .put("sha256", sha256)

        val response = executeJson(
            Request.Builder()
                .url("$baseUrl/v1/sleep-sessions/$sessionId/raw-uploads")
                .header("Idempotency-Key", "$sessionId:raw:$sha256")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
        )

        return UploadState(
            uploadId = response.getString("uploadId"),
            objectKey = response.getString("objectKey"),
            partSizeBytes = response.optLong(
                "partSizeBytes",
                PotchRawFileContract.DEFAULT_PART_SIZE_BYTES
            ),
            fileSizeBytes = file.length(),
            fileLastModifiedMillis = file.lastModified(),
            fileSha256 = sha256
        )
    }

    private fun fetchStatus(sessionId: String, uploadId: String): UploadStatus {
        val response = executeJson(
            Request.Builder()
                .url("$baseUrl/v1/sleep-sessions/$sessionId/raw-uploads/$uploadId")
                .get()
        )
        val partsJson = response.optJSONArray("uploadedParts") ?: JSONArray()
        val parts = buildList {
            for (index in 0 until partsJson.length()) {
                val part = partsJson.getJSONObject(index)
                add(
                    UploadedPart(
                        partNumber = part.getInt("partNumber"),
                        etag = part.getString("etag"),
                        checksumCrc32c = part.optString("checksumCrc32c"),
                        sizeBytes = part.optLong("sizeBytes")
                    )
                )
            }
        }
        return UploadStatus(response.getString("status"), parts)
    }

    private fun requestPresignedPart(
        sessionId: String,
        uploadId: String,
        partNumber: Int,
        byteCount: Long,
        checksumCrc32c: String
    ): PresignedPart {
        val payload = JSONObject()
            .put("partNumber", partNumber)
            .put("sizeBytes", byteCount)
            .put("checksumCrc32c", checksumCrc32c)
        val response = executeJson(
            Request.Builder()
                .url("$baseUrl/v1/sleep-sessions/$sessionId/raw-uploads/$uploadId/parts/presign")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
        )

        val headers = mutableMapOf<String, String>()
        response.optJSONObject("headers")?.let { json ->
            json.keys().forEach { name -> headers[name] = json.getString(name) }
        }
        return PresignedPart(response.getString("url"), headers)
    }

    private fun uploadPart(
        file: File,
        offset: Long,
        byteCount: Long,
        checksumCrc32c: String,
        presigned: PresignedPart
    ): String {
        val requestBuilder = Request.Builder()
            .url(presigned.url)
            .put(FileSliceRequestBody(file, offset, byteCount, BINARY_MEDIA_TYPE))
        presigned.headers.forEach { (name, value) -> requestBuilder.header(name, value) }

        // TODO(Server): presign 응답은 이 헤더를 포함해야 하며 S3 서명에도 같은 값이 들어가야 한다.
        val signedChecksum = presigned.headers.entries
            .firstOrNull { it.key.equals("x-amz-checksum-crc32c", ignoreCase = true) }
            ?.value
        require(signedChecksum == checksumCrc32c) {
            "presign 응답의 x-amz-checksum-crc32c가 요청과 다름"
        }

        http.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw HttpStatusException(
                    code = response.code,
                    responseBody = response.body?.string().orEmpty(),
                    presignedS3Request = true
                )
            }
            return response.header("ETag")
                ?: throw IOException("S3 UploadPart 응답에 ETag 헤더가 없음")
        }
    }

    private fun completeUpload(
        sessionId: String,
        uploadId: String,
        parts: List<UploadedPart>,
        sizeBytes: Long,
        sha256: String
    ) {
        val partsJson = JSONArray()
        parts.sortedBy { it.partNumber }.forEach { part ->
            partsJson.put(
                JSONObject()
                    .put("partNumber", part.partNumber)
                    .put("etag", part.etag)
                    .put("checksumCrc32c", part.checksumCrc32c)
            )
        }
        val payload = JSONObject()
            .put("parts", partsJson)
            .put("sizeBytes", sizeBytes)
            .put("sha256", sha256)
        val response = executeJson(
            Request.Builder()
                .url("$baseUrl/v1/sleep-sessions/$sessionId/raw-uploads/$uploadId/complete")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
        )
        check(response.getString("status") == "COMPLETE") {
            "CompleteMultipartUpload 응답 상태가 COMPLETE가 아님"
        }
    }

    private fun executeJson(builder: Request.Builder): JSONObject {
        SleepServerAuthProvider.bearerToken(appContext)?.let { token ->
            builder.header("Authorization", "Bearer $token")
        } ?: Log.w(TAG, "TODO(App/Auth): FastAPI Authorization token이 아직 연결되지 않음")

        http.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw HttpStatusException(response.code, body)
            return JSONObject(body.ifBlank { "{}" })
        }
    }

    private fun ensureFileUnchanged(file: File, expectedSize: Long, expectedModified: Long) {
        check(file.isFile && file.length() == expectedSize && file.lastModified() == expectedModified) {
            "업로드 중 raw 파일이 변경됨"
        }
    }

    private fun markComplete(file: File, sessionId: String) {
        RawDataUploadManager.uploadedMarker(file).createNewFile()
        stateStore.clear(sessionId)
    }

    private data class UploadState(
        val uploadId: String,
        val objectKey: String,
        val partSizeBytes: Long,
        val fileSizeBytes: Long,
        val fileLastModifiedMillis: Long,
        val fileSha256: String
    )

    private data class UploadedPart(
        val partNumber: Int,
        val etag: String,
        val checksumCrc32c: String,
        val sizeBytes: Long
    )

    private data class UploadStatus(val status: String, val uploadedParts: List<UploadedPart>)
    private data class PresignedPart(val url: String, val headers: Map<String, String>)

    private class UploadStateStore(context: Context) {
        private val preferences = context.getSharedPreferences(
            "potch_raw_multipart_uploads",
            Context.MODE_PRIVATE
        )

        fun load(sessionId: String): UploadState? = runCatching {
            val json = JSONObject(preferences.getString(sessionId, null) ?: return null)
            UploadState(
                uploadId = json.getString("uploadId"),
                objectKey = json.getString("objectKey"),
                partSizeBytes = json.getLong("partSizeBytes"),
                fileSizeBytes = json.getLong("fileSizeBytes"),
                fileLastModifiedMillis = json.getLong("fileLastModifiedMillis"),
                fileSha256 = json.getString("fileSha256")
            )
        }.getOrNull()

        fun save(sessionId: String, state: UploadState) {
            val json = JSONObject()
                .put("uploadId", state.uploadId)
                .put("objectKey", state.objectKey)
                .put("partSizeBytes", state.partSizeBytes)
                .put("fileSizeBytes", state.fileSizeBytes)
                .put("fileLastModifiedMillis", state.fileLastModifiedMillis)
                .put("fileSha256", state.fileSha256)
            check(preferences.edit().putString(sessionId, json.toString()).commit())
        }

        fun clear(sessionId: String) {
            check(preferences.edit().remove(sessionId).commit())
        }
    }

    private class FileSliceRequestBody(
        private val file: File,
        private val offset: Long,
        private val byteCount: Long,
        private val mediaType: MediaType
    ) : RequestBody() {
        override fun contentType(): MediaType = mediaType
        override fun contentLength(): Long = byteCount

        override fun writeTo(sink: BufferedSink) {
            RandomAccessFile(file, "r").use { input ->
                input.seek(offset)
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var remaining = byteCount
                while (remaining > 0L) {
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read <= 0) throw IOException("청크 전송 중 파일이 조기에 끝남")
                    sink.write(buffer, 0, read)
                    remaining -= read
                }
            }
        }
    }

    private class HttpStatusException(
        val code: Int,
        val responseBody: String,
        private val presignedS3Request: Boolean = false
    ) : IOException() {
        val isRetryable: Boolean =
            (presignedS3Request && code in setOf(403, 404)) ||
                code == 408 || code == 409 || code == 429 || code in 500..599
    }
}
