package com.leejang.sleeptandard.backend

import android.content.Context
import androidx.core.content.edit

/** FastAPI 토큰을 앱 전용 저장소에 보관하고 백그라운드 Worker에도 제공한다. */
object SleepServerAuthProvider {
    private const val PREFS_NAME = "sleep_server_auth"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_EXPIRES_AT = "expires_at"

    fun saveSession(
        context: Context,
        accessToken: String,
        userId: String,
        expiresInSeconds: Long
    ) {
        val expiresAt = System.currentTimeMillis() + expiresInSeconds * 1_000L
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_ACCESS_TOKEN, accessToken)
                putString(KEY_USER_ID, userId)
                putLong(KEY_EXPIRES_AT, expiresAt)
            }
    }

    fun bearerToken(context: Context): String? {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        if (expiresAt <= System.currentTimeMillis()) {
            clear(context)
            return null
        }
        return token
    }

    fun clear(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { clear() }
    }
}
