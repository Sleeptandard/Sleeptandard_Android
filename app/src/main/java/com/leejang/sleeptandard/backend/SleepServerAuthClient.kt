package com.leejang.sleeptandard.backend

import com.leejang.sleeptandard.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class SleepServerUser(
    val userId: String,
    val email: String,
    val nickname: String,
    val gender: String?,
    val birthdate: String?
)

data class SleepServerSession(
    val accessToken: String,
    val expiresIn: Long,
    val user: SleepServerUser
)

class SleepServerAuthException(
    val code: String? = null,
    message: String
) : IOException(message)

internal object SleepServerAuthErrorMessages {
    fun fromServer(code: String?, serverMessage: String?, httpStatus: Int): String = when (code) {
        "EMAIL_ALREADY_EXISTS" -> "이미 가입된 이메일입니다."
        "INVALID_LOGIN_CREDENTIALS" -> "이메일 또는 비밀번호가 올바르지 않습니다."
        "INVALID_ACCESS_TOKEN", "AUTHENTICATION_REQUIRED" ->
            "로그인이 만료되었습니다. 다시 로그인해주세요."
        "VALIDATION_ERROR" -> "입력한 회원정보를 다시 확인해주세요."
        else -> serverMessage?.takeIf { it.isNotBlank() }
            ?: "서버 요청에 실패했습니다. (HTTP $httpStatus)"
    }
}

/** FastAPI 회원가입, 로그인, 현재 사용자 확인을 담당한다. */
object SleepServerAuthClient {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val baseUrl: String
        get() = BuildConfig.SLEEP_SERVER_BASE_URL.trim().trimEnd('/')

    suspend fun signup(
        email: String,
        password: String,
        nickname: String,
        gender: String?,
        birthdate: String?
    ): SleepServerSession = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("email", email)
            .put("password", password)
            .put("nickname", nickname)
        gender?.let { payload.put("gender", it) }
        birthdate?.let { payload.put("birthdate", it) }

        parseSession(post("/auth/signup", payload))
    }

    suspend fun login(email: String, password: String): SleepServerSession =
        withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("email", email)
                .put("password", password)
            parseSession(post("/auth/login", payload))
        }

    suspend fun me(accessToken: String): SleepServerUser = withContext(Dispatchers.IO) {
        ensureConfigured()
        val request = Request.Builder()
            .url("$baseUrl/auth/me")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        parseUser(execute(request))
    }

    private fun post(path: String, payload: JSONObject): JSONObject {
        ensureConfigured()
        val request = Request.Builder()
            .url("$baseUrl$path")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        return execute(request)
    }

    private fun execute(request: Request): JSONObject = http.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        val json = runCatching { JSONObject(body) }.getOrNull()
        if (!response.isSuccessful) {
            val code = json?.optString("code")?.takeIf { it.isNotBlank() }
            val serverMessage = json?.optString("message")?.takeIf { it.isNotBlank() }
            throw SleepServerAuthException(
                code = code,
                message = SleepServerAuthErrorMessages.fromServer(
                    code = code,
                    serverMessage = serverMessage,
                    httpStatus = response.code
                )
            )
        }
        json ?: throw SleepServerAuthException(message = "서버 응답을 읽을 수 없습니다.")
    }

    private fun parseSession(json: JSONObject): SleepServerSession = SleepServerSession(
        accessToken = json.getString("access_token"),
        expiresIn = json.getLong("expires_in"),
        user = parseUser(json.getJSONObject("user"))
    )

    private fun parseUser(json: JSONObject): SleepServerUser = SleepServerUser(
        userId = json.getString("user_id"),
        email = json.getString("email"),
        nickname = json.getString("nickname"),
        gender = json.optNullableString("gender"),
        birthdate = json.optNullableString("birthdate")
    )

    private fun JSONObject.optNullableString(name: String): String? =
        if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

    private fun ensureConfigured() {
        if (baseUrl.isBlank() || baseUrl == "null") {
            throw SleepServerAuthException(message = "개발 서버 주소가 설정되지 않았습니다.")
        }
    }
}
