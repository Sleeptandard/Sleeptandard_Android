package com.leejang.sleeptandard_mvp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

import com.leejang.sleeptandard_mvp.ClassFile.AlarmScheduler
import com.leejang.sleeptandard_mvp.Component.AppNav
import com.leejang.sleeptandard_mvp.Component.Screen
import com.leejang.sleeptandard_mvp.ui.theme.Sleeptandard_MVP_DemoTheme
import com.leejang.sleeptandard_mvp.Permission.checkFullScreenIntentPermission
import com.leejang.sleeptandard_mvp.Permission.checkNotificationPermission
import com.leejang.sleeptandard_mvp.Permission.checkSetExactAlarms
import com.leejang.sleeptandard_mvp.Prefs.AlarmPreferences


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()

        super.onCreate(savedInstanceState)

        // 권한 설정 여부 확인. 안되어 있으면 설정 창으로
        val scheduler = AlarmScheduler(applicationContext)
        checkSetExactAlarms(scheduler, this)
        checkFullScreenIntentPermission(this)
        checkNotificationPermission(this)

        // SharedPreferences 불러오기
        val alarmPrefs = AlarmPreferences(this)
        val initialAlarm = alarmPrefs.loadAlarm()
        val isFirstRun = alarmPrefs.isFirstRun() // 첫 실행 여부 확인

        // 인텐트에서 온 startDestination(알람 끈 후 reviewAlarm용)이 우선
        val startDestinationFromIntent =
            intent.getStringExtra("startDestination")

        val startDestination = when {
            isFirstRun -> Screen.Tutorial.route           // 1순위: 처음 깔았다면 무조건 튜토리얼
            alarmPrefs.isAlarmSet() -> Screen.SettedAlarm.route // 2순위: 알람이 설정되어 있다면 설정된 화면
            else -> Screen.Home.route                     // 3순위: 일반적인 홈 화면
        }

        enableEdgeToEdge()

        splash.setOnExitAnimationListener { splashScreenView ->

            // 예쁜 “창 전환” 느낌: 살짝 축소 + 페이드아웃
            splashScreenView.view.animate()
                .alpha(0f)
                .scaleX(0.98f)
                .scaleY(0.98f)
                .setDuration(750L)
                .setInterpolator(FastOutSlowInInterpolator())
                .withEndAction {
                    // ✅ 반드시 제거해줘야 함
                    splashScreenView.remove()
                }
                .start()
        }

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
