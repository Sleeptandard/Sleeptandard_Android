package com.leejang.sleeptandard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.leejang.sleeptandard.service.SmartAlarmService

class PermissionActivity : ComponentActivity() {

    private var targetAlarmTime: Long = 0L
    private var earlyWakeUpMinutes: Int = 30
    private var isRem: Boolean = true
    private var situationLabel: String = "normal"

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

        targetAlarmTime = intent.getLongExtra(SmartAlarmService.EXTRA_TARGET_TIME, 0L)
        earlyWakeUpMinutes = intent.getIntExtra(SmartAlarmService.EXTRA_EARLY_WAKE_UP_MINUTES, 30)
        isRem = intent.getBooleanExtra(SmartAlarmService.EXTRA_IS_REM, true)
        situationLabel = intent.getStringExtra(SmartAlarmService.EXTRA_SITUATION_LABEL) ?: "normal"

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
            putExtra(SmartAlarmService.EXTRA_EARLY_WAKE_UP_MINUTES, earlyWakeUpMinutes)
            putExtra(SmartAlarmService.EXTRA_IS_REM, isRem)
            putExtra(SmartAlarmService.EXTRA_SITUATION_LABEL, situationLabel)
            action = SmartAlarmService.ACTION_START_TRACKING
        }
        startForegroundService(serviceIntent)
        finish()
    }
}