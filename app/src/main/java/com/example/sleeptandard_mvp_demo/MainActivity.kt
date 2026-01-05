package com.example.sleeptandard_mvp_demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

import com.example.sleeptandard_mvp_demo.ClassFile.AlarmScheduler
import com.example.sleeptandard_mvp_demo.Component.AppNav
import com.example.sleeptandard_mvp_demo.Component.Screen
import com.example.sleeptandard_mvp_demo.ui.theme.Sleeptandard_MVP_DemoTheme
import com.example.sleeptandard_mvp_demo.Permission.checkFullScreenIntentPermission
import com.example.sleeptandard_mvp_demo.Permission.checkNotificationPermission
import com.example.sleeptandard_mvp_demo.Permission.checkSetExactAlarms
import com.example.sleeptandard_mvp_demo.Prefs.AlarmPreferences


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        // 권한 설정 여부 확인. 안되어 있으면 설정 창으로
        val scheduler = AlarmScheduler(applicationContext)
        checkSetExactAlarms(scheduler, this)
        checkFullScreenIntentPermission(this)
        checkNotificationPermission(this)

        // SharedPreferences 불러오기
        val alarmPrefs = AlarmPreferences(this)
        val initialAlarm = alarmPrefs.loadAlarm()

        // 인텐트에서 온 startDestination(알람 끈 후 reviewAlarm용)이 우선
        val startDestinationFromIntent =
            intent.getStringExtra("startDestination")

        // 실험중
        val startDestination =
            startDestinationFromIntent
                ?: if (alarmPrefs.isAlarmSet()) Screen.SettedAlarm.route
                // else Screen.Splash.route // 컴포즈 스플래시 화면
                else Screen.Home.route

        enableEdgeToEdge()
        setContent {
            Sleeptandard_MVP_DemoTheme {
                AppNav(
                    scheduler = scheduler,
                    startDestination = startDestination,
                    initialAlarm = initialAlarm
                )
            }
        }
    }
}
