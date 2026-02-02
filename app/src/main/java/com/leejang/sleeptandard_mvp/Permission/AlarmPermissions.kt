package com.leejang.sleeptandard_mvp.Permission

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.leejang.sleeptandard_mvp.ClassFile.AlarmScheduler

private const val REQ_POST_NOTIFICATIONS = 1001

private fun openNotificationSettings(activity: Activity) {
    val intent = Intent().apply {
        action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
        putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
        // 일부 기기 호환용
        putExtra("app_package", activity.packageName)
        putExtra("app_uid", activity.applicationInfo?.uid)
    }
    activity.startActivity(intent)
}

fun checkNotificationPermission(activity: Activity) {
    // 1) 안드로이드 13 이상: POST_NOTIFICATIONS 런타임 권한 확인
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            // 안내 다이얼로그 띄우고 → OK 누르면 권한 요청
            AlertDialog.Builder(activity)
                .setTitle("알림 권한이 필요해요")
                .setMessage("알람이 울릴 때 알림을 보여주기 위해 알림 권한을 허용해 주세요.")
                .setPositiveButton("허용하기") { _, _ ->
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        REQ_POST_NOTIFICATIONS
                    )
                }
                .setNegativeButton("나중에", null)
                .show()
        }
        return
    }

    // 2) 안드로이드 12 이하: 시스템 알림이 꺼져 있는지 확인 (권한 X, 앱 설정만 존재)
    val nm = NotificationManagerCompat.from(activity)
    if (!nm.areNotificationsEnabled()) {
        AlertDialog.Builder(activity)
            .setTitle("알림이 꺼져 있어요")
            .setMessage("알람을 제대로 받으려면 이 앱의 알림을 켜 주세요.\n설정 화면으로 이동할까요?")
            .setPositiveButton("설정으로 이동") { _, _ ->
                openNotificationSettings(activity)
            }
            .setNegativeButton("취소", null)
            .show()
    }
}


fun checkFullScreenIntentPermission(activity: Activity) {
    Log.d("checkFSI", "enter the checking permission fun")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        Log.d("checkFSI", "version check complete")

        val nm = activity.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        Log.d("checkFSI", "can use FSI? : ${!nm.canUseFullScreenIntent()}")
        // 권한 없으면 설정 페이지로 이동
        if (!nm.canUseFullScreenIntent()) {
            Log.d("checkFSI", "can't use FSI")
            val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                // ⚠️ 이 data 넣어줘야 ActivityNotFoundException 안 남
                data = Uri.fromParts("package", activity.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        }
    }
}

fun checkSetExactAlarms(scheduler: AlarmScheduler, context: Context){
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
        if (!scheduler.getAlarmManager().canScheduleExactAlarms()) {
            // 사용자가 "정확한 알람" 권한을 아직 안 줌 → 설정 화면으로 보냄
            // TODO: SCHEDULE_EXACT_ALARM 대신 USE_EXACT_ALARM 고려
            val settingsIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(settingsIntent)
            return
        }

    }
}

// 모든 권한들이 허용되었는지 확인하는 함수
fun isAllEssentialPermissionsGranted(context: Context): Boolean {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

    // 1. 정확한 알람 권한 (Android 12+)
    val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        alarmManager.canScheduleExactAlarms()
    } else true

    // 2. 알림 권한 (Android 13+)
    val notificationEnabled = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()

    // 3. 전체 화면 인텐트 권한 (Android 14+)
    val canUseFSI = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        nm.canUseFullScreenIntent()
    } else true

    return canScheduleExact && notificationEnabled && canUseFSI
}

/**
 * 시스템의 '앱 상세 정보' 화면으로 이동합니다.
 * 사용자가 권한을 영구 거부했을 때 직접 허용하도록 유도할 때 사용합니다.
 */
fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        // 현재 앱의 패키지명을 Uri 데이터로 설정하여 해당 앱 페이지로 바로 이동
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}