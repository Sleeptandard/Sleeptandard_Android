package com.example.sleeptandard_mvp_demo.backend.service

// [필수 Import 추가됨]
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.sleeptandard_mvp_demo.PermissionActivity
import com.example.sleeptandard_mvp_demo.service.SmartAlarmService
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import java.nio.ByteBuffer

/**
 * WatchListenerService - 폰으로부터 명령을 수신하는 서비스
 */
class WatchListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "Message received: ${messageEvent.path}")

        when (messageEvent.path) {
            PATH_START_TRACKING -> {
                handleStartTracking(messageEvent.data)
            }
            PATH_STOP_TRACKING -> {
                handleStopTracking()
            }
            else -> {
                Log.w(TAG, "Unknown message path: ${messageEvent.path}")
            }
        }
    }

    private fun handleStartTracking(data: ByteArray) {
        try {
            if (data.size < 8) return
            val targetAlarmTime = ByteBuffer.wrap(data).long
            Log.i(TAG, "START_TRACKING received. Target: $targetAlarmTime")

            // 1. 필수 권한 목록 확인
            val permissions = arrayOf(
                Manifest.permission.BODY_SENSORS,
                Manifest.permission.ACTIVITY_RECOGNITION,
                Manifest.permission.POST_NOTIFICATIONS
            )

            // 2. 권한이 모두 있는지 체크
            // (ContextCompat과 PackageManager import가 없으면 여기서 빨간줄이 뜹니다)
            val allGranted = permissions.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }

            if (allGranted) {
                // 3-A. 권한이 다 있으면 -> 바로 서비스 시작
                val intent = Intent(this, SmartAlarmService::class.java).apply {
                    putExtra(SmartAlarmService.EXTRA_TARGET_TIME, targetAlarmTime)
                    action = SmartAlarmService.ACTION_START_TRACKING
                }
                startForegroundService(intent)
            } else {
                // 3-B. 권한이 없으면 -> PermissionActivity 실행하여 권한 요청
                Log.w(TAG, "Permissions missing. Launching Activity.")

                // (PermissionActivity import가 없으면 여기서 빨간줄이 뜹니다)
                val intent = Intent(this, PermissionActivity::class.java).apply {
                    putExtra(SmartAlarmService.EXTRA_TARGET_TIME, targetAlarmTime)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // 서비스에서 액티비티 켤 때 필수
                }
                startActivity(intent)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle START_TRACKING", e)
        }
    }

    private fun handleStopTracking() {
        try {
            Log.i(TAG, "STOP_TRACKING received")
            val intent = Intent(this, SmartAlarmService::class.java).apply {
                action = SmartAlarmService.ACTION_STOP_AND_SEND_RESULT
            }
            startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle STOP_TRACKING", e)
        }
    }

    companion object {
        private const val TAG = "WatchListenerService"
        private const val PATH_START_TRACKING = "/START_TRACKING"
        private const val PATH_STOP_TRACKING = "/STOP_TRACKING"
    }
}