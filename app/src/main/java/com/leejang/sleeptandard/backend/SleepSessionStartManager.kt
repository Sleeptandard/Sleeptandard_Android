package com.leejang.sleeptandard.backend

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.leejang.sleeptandard.Potch.AlarmLogPhase
import com.leejang.sleeptandard.Potch.AlarmLogSessionStore
import com.leejang.sleeptandard.Potch.PotchBleForegroundService
import com.leejang.sleeptandard.Prefs.AlarmPreferences
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 알람 예약과 서버 수면 세션 생성을 연결한다. */
object SleepSessionStartManager {
    private const val UNIQUE_WORK_PREFIX = "sleep_session_start_"
    private const val SESSION_PREFS = "sleep_server_session_ids"

    fun createOrUpdate(context: Context, alarmId: Int, targetTimeMillis: Long) {
        val appContext = context.applicationContext
        val store = AlarmLogSessionStore(appContext)
        val existing = store.load().lastOrNull { it.phase == AlarmLogPhase.SCHEDULED }
        if (existing != null && isServerSession(appContext, existing.id)) {
            store.updateScheduledAlarm(alarmId = alarmId, target = targetTimeMillis)
            PotchBleForegroundService.requestSyncAlarmLogging(appContext)
            return
        }

        val request = OneTimeWorkRequestBuilder<SleepSessionStartWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(
                workDataOf(
                    SleepSessionStartWorker.KEY_ALARM_ID to alarmId,
                    SleepSessionStartWorker.KEY_TARGET_TIME to targetTimeMillis,
                    SleepSessionStartWorker.KEY_STARTED_AT to System.currentTimeMillis()
                )
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            UNIQUE_WORK_PREFIX + alarmId,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context, alarmId: Int) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(UNIQUE_WORK_PREFIX + alarmId)
    }

    internal fun rememberServerSession(context: Context, sessionId: String) {
        context.applicationContext.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(sessionId, true)
            .apply()
    }

    private fun isServerSession(context: Context, sessionId: String): Boolean =
        context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
            .getBoolean(sessionId, false)
}

class SleepSessionStartWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    companion object {
        const val KEY_ALARM_ID = "alarm_id"
        const val KEY_TARGET_TIME = "target_time_millis"
        const val KEY_STARTED_AT = "started_at_millis"
        private const val MAX_ATTEMPTS = 5
        private const val TAG = "SleepSessionStart"
    }

    override suspend fun doWork(): Result {
        val alarmId = inputData.getInt(KEY_ALARM_ID, Int.MIN_VALUE)
        val targetTimeMillis = inputData.getLong(KEY_TARGET_TIME, 0L)
        val startedAtMillis = inputData.getLong(KEY_STARTED_AT, 0L)
        if (alarmId == Int.MIN_VALUE || targetTimeMillis <= 0L || startedAtMillis <= 0L) {
            return Result.failure(workDataOf("error" to "수면 세션 생성 정보가 없습니다."))
        }

        val token = SleepServerAuthProvider.bearerToken(appContext)
            ?: return Result.failure(
                workDataOf("auth_required" to true, "error" to "로그인이 필요합니다.")
            )

        return try {
            val session = SleepSessionClient.start(
                accessToken = token,
                deviceCode = registeredDeviceCode(),
                startedAtMillis = startedAtMillis
            )

            val scheduledTarget = AlarmPreferences(appContext).getScheduledTriggerTimeMillis()
            if (scheduledTarget != targetTimeMillis) {
                Log.w(TAG, "Discard stale session response: target=$targetTimeMillis current=$scheduledTarget")
                return Result.failure(workDataOf("error" to "이미 변경되거나 취소된 알람입니다."))
            }

            SleepSessionStartManager.rememberServerSession(appContext, session.sessionId)
            AlarmLogSessionStore(appContext).bindServerSession(
                alarmId = alarmId,
                target = targetTimeMillis,
                serverSessionId = session.sessionId,
                startedAtMillis = session.startedAtMillis
            )
            runCatching {
                PotchBleForegroundService.requestSyncAlarmLogging(appContext)
            }.onFailure { error ->
                // The stored server session is restored when alarm monitoring starts even if
                // Android temporarily blocks a background foreground-service launch here.
                Log.w(TAG, "Session saved; immediate logger sync deferred", error)
            }
            Log.i(TAG, "Sleep session ready: sessionId=${session.sessionId}")
            Result.success(workDataOf("session_id" to session.sessionId))
        } catch (error: SleepSessionApiException) {
            Log.e(TAG, "Sleep session API failed: ${error.httpStatus} ${error.code}", error)
            if (error.httpStatus == 401) {
                SleepServerAuthProvider.clear(appContext)
                Result.failure(
                    workDataOf(
                        "auth_required" to true,
                        "http_status" to error.httpStatus,
                        "error" to (error.message ?: "로그인이 만료되었습니다.")
                    )
                )
            } else if (error.isRetryable && runAttemptCount + 1 < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure(
                    workDataOf(
                        "http_status" to error.httpStatus,
                        "error" to (error.message ?: "수면 세션을 만들 수 없습니다.")
                    )
                )
            }
        } catch (error: IOException) {
            Log.e(TAG, "Sleep session network failure", error)
            if (runAttemptCount + 1 < MAX_ATTEMPTS) Result.retry()
            else Result.failure(workDataOf("error" to (error.message ?: "네트워크 오류")))
        } catch (error: Exception) {
            Log.e(TAG, "Sleep session creation failure", error)
            Result.failure(workDataOf("error" to (error.message ?: "수면 세션 생성 오류")))
        }
    }

    private fun registeredDeviceCode(): String {
        val address = appContext.getSharedPreferences("potch_service", Context.MODE_PRIVATE)
            .getString("registered_potch_address", null)
            ?.replace(":", "")
            ?.uppercase(Locale.US)
        if (!address.isNullOrBlank()) return "potch-$address"

        val androidId = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ANDROID_ID
        ).orEmpty().ifBlank { "unknown" }
        return "android-${Build.MODEL}-$androidId".take(50)
    }
}
