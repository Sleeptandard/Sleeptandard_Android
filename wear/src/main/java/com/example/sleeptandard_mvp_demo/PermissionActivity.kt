package com.example.sleeptandard_mvp_demo

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.sleeptandard_mvp_demo.service.SmartAlarmService

class PermissionActivity : ComponentActivity() {

    private var targetAlarmTime: Long = 0L

    private val requiredPermissions = arrayOf(
        Manifest.permission.BODY_SENSORS,
        Manifest.permission.ACTIVITY_RECOGNITION,
        Manifest.permission.POST_NOTIFICATIONS
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startTrackingService()
        } else {
            Toast.makeText(this, "권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 인텐트로 전달받은 목표 시간 저장
        targetAlarmTime = intent.getLongExtra(SmartAlarmService.EXTRA_TARGET_TIME, 0L)

        if (checkPermissions()) {
            startTrackingService()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    private fun checkPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startTrackingService() {
        val serviceIntent = Intent(this, SmartAlarmService::class.java).apply {
            putExtra(SmartAlarmService.EXTRA_TARGET_TIME, targetAlarmTime)
            action = SmartAlarmService.ACTION_START_TRACKING
        }
        startForegroundService(serviceIntent)
        finish() // 역할 끝났으니 종료
    }
}