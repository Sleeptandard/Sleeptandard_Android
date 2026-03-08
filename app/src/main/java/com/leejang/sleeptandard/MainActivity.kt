package com.leejang.sleeptandard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

import com.leejang.sleeptandard.ClassFile.AlarmScheduler
import com.leejang.sleeptandard.Component.AppNav
import com.leejang.sleeptandard.Component.Screen
import com.leejang.sleeptandard.ui.theme.Sleeptandard_MVP_DemoTheme
import com.leejang.sleeptandard.Permission.checkFullScreenIntentPermission
import com.leejang.sleeptandard.Permission.checkNotificationPermission
import com.leejang.sleeptandard.Permission.checkSetExactAlarms
import com.leejang.sleeptandard.Prefs.AlarmPreferences

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()

        super.onCreate(savedInstanceState)

        // 권한 설정 여부 확인. 안되어 있으면 설정 창으로
        val scheduler = AlarmScheduler(applicationContext)
        checkSetExactAlarms(scheduler, this)
        checkFullScreenIntentPermission(this)
        checkNotificationPermission(this)

        val alarmPrefs = AlarmPreferences(this)

        enableEdgeToEdge()

        // 스플래시 화면에서 빠져나오는 전환 애니메이션
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
                    scheduler = AlarmScheduler(this),
                    startDestination = getStartDestination(alarmPrefs), // 기존 로직을 함수로 분리
                    initialAlarm = alarmPrefs.loadAlarm()
                )
            }
        }
    }

    // 시작 화면 정하는 함수
    private fun getStartDestination(
        alarmPrefs: AlarmPreferences
    ): String {
        val startDestinationFromIntent = intent.getStringExtra("startDestination")

        return when {
            alarmPrefs.isFirstRun() -> Screen.Tutorial.route           // 1순위: 앱을 처음 실행한 경우
            alarmPrefs.isAlarmSet() -> Screen.SettedAlarm.route       // 2순위: 알람이 설정되어 있는 경우
            startDestinationFromIntent != null -> startDestinationFromIntent // 3순위: 알람을 끄고 온 경우 (피드백 화면)
            else -> Screen.Home.route                                 // 4순위: 일반적인 경우
        }
    }

}

