package com.leejang.sleeptandard.backend

import android.os.Build
import com.leejang.sleeptandard.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

data class StartedSleepSession(
    val sessionId: String,
    val startedAtMillis: Long,
    val sessionStatus: String,
    val uploadStatus: String
)

class SleepSessionApiException(
    val httpStatus: Int,
    val code: String?,
    message: String
) : IOException(message) {
    val isRetryable: Boolean =
        httpStatus == 408 || httpStatus == 409 || httpStatus == 429 || httpStatus in 500..599
}

/** FastAPI 수면 세션 생성 API를 호출하고 서버가 발급한 session_id를 반환한다. */
object SleepSessionClient {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val baseUrl: String
        get() = BuildConfig.SLEEP_SERVER_BASE_URL.trim().trimEnd('/')

    suspend fun start(
        accessToken: String,
        deviceCode: String,
        startedAtMillis: Long
    ): StartedSleepSession = withContext(Dispatchers.IO) {
        check(baseUrl.isNotBlank() && baseUrl != "null") {
            "개발 서버 주소가 설정되지 않았습니다."
        }

        val payload = JSONObject()
            .put("device_code", deviceCode)
            .put("app_version", BuildConfig.VERSION_NAME)
            .put("os_type", "android")
            .put("os_version", Build.VERSION.RELEASE)
            .put("started_at", Instant.ofEpochMilli(startedAtMillis).toString())

        val request = Request.Builder()
            .url("$baseUrl/sleep-sessions/start")
            .header("Authorization", "Bearer $accessToken")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(body) }.getOrNull()
            if (!response.isSuccessful) {
                val code = json?.optString("code")?.takeIf { it.isNotBlank() }
                val serverMessage = json?.optString("message")?.takeIf { it.isNotBlank() }
                throw SleepSessionApiException(
                    httpStatus = response.code,
                    code = code,
                    message = SleepServerAuthErrorMessages.fromServer(
                        code = code,
                        serverMessage = serverMessage,
                        httpStatus = response.code
                    )
                )
            }

            val result = json ?: throw IOException("수면 세션 응답을 읽을 수 없습니다.")
            val startedAt = OffsetDateTime.parse(result.getString("started_at")).toInstant()
            StartedSleepSession(
                sessionId = result.getString("session_id"),
                startedAtMillis = startedAt.toEpochMilli(),
                sessionStatus = result.getString("session_status"),
                uploadStatus = result.getString("upload_status")
            )
        }
    }
}
