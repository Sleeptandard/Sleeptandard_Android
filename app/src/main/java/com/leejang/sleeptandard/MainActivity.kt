package com.leejang.sleeptandard

import android.os.Bundle
import java.io.File
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
import com.leejang.sleeptandard.backend.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.coroutines.runBlocking
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.leejang.sleeptandard.ViewModel.ProfileInsert
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.leejang.sleeptandard.Prefs.UserInfoPreferences

// 마이크 테스트
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

            // 예쁜 "창 전환" 느낌: 살짝 축소 + 페이드아웃
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

        val isResetPassword = intent.data?.path?.startsWith("/reset-password") == true ||
                              intent.data?.host == "reset-password"

        if (isResetPassword) {
            try {
                // SupabaseClient의 확장 함수를 호출합니다.
                SupabaseClientProvider.client.handleDeeplinks(intent)
            } catch (e: Exception) {
                // handleDeeplinks 호출 실패 시 무시 오류 방지
                android.util.Log.e("MainActivity", "handleDeeplinks error", e)
            }
        }

        //val startDestination = getStartDestination(alarmPrefs, isResetPassword)
        val startDestination = Screen.Home.route
        // 자동 로그인으로 홈 화면에 진입한 경우 환영 메시지 띄우기
        if (startDestination == Screen.Home.route) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val uid = SupabaseClientProvider.client.auth.currentUserOrNull()?.id ?: ""
                    if (uid.isNotEmpty()) {
                        val profile = SupabaseClientProvider.client.postgrest["profiles"]
                            .select { filter { eq("id", uid) } }
                            .decodeSingle<ProfileInsert>()
                        
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "${profile.nickname}님 환영합니다!", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "환영합니다!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val userPrefs = UserInfoPreferences(this)
        val loadedUser = userPrefs.loadUserInfo()

        setContent {
            Sleeptandard_MVP_DemoTheme {

                AppNav(
                    scheduler = AlarmScheduler(this),
                    startDestination = startDestination,
                    initialAlarm = alarmPrefs.loadAlarm(),
                    userInfo = loadedUser,
                    isPasswordReset = isResetPassword
                )
            }
        }

        // [안전망] 앱 실행 시 미전송 CSV 파일 자동 재업로드
        // WorkManager가 대용량 파일 전송 중 시스템에 의해 종료된 경우를 대비
        enqueueUnsentCsvFiles()
    }

    /**
     * filesDir에 남아 있는 received_*.csv 파일을 스캔하여 WorkManager에 업로드 등록
     * 이미 업로드된 파일은 .uploaded 마커 파일로 구분하여 중복 업로드 방지
     */
    private fun enqueueUnsentCsvFiles() {
        lifecycleScope.launch(Dispatchers.IO) {
            val csvFiles = filesDir.listFiles { file ->
                file.name.startsWith("received_") && file.name.endsWith(".csv")
            } ?: return@launch

            val unsentFiles = csvFiles.filter { file ->
                // .uploaded 마커 파일이 없는 것만 업로드 대상
                !File(file.parent, "${file.name}.uploaded").exists()
            }

            if (unsentFiles.isEmpty()) {
                android.util.Log.i("MainActivity", "✅ No unsent CSV files found")
                return@launch
            }

            android.util.Log.i("MainActivity", "📋 Found ${unsentFiles.size} unsent CSV files - re-enqueueing...")

            unsentFiles.forEach { file ->
                com.leejang.sleeptandard.backend.CsvUploadManager.enqueueUpload(applicationContext, file)
                android.util.Log.i("MainActivity", "  → Re-enqueued: ${file.name} (${file.length() / 1024}KB)")
            }
        }
    }

    // 시작 화면 정하는 함수
    private fun getStartDestination(
        alarmPrefs: AlarmPreferences,
        isResetPassword: Boolean = false
    ): String {
        val startDestinationFromIntent = intent.getStringExtra("startDestination")

        // Supabase 세션이 디바이스 저장소에서 비동기로 로드될 때까지 대기
        val isLoggedIn = runBlocking {
            try {
                SupabaseClientProvider.client.auth.awaitInitialization()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            SupabaseClientProvider.client.auth.currentSessionOrNull() != null
        }

        return when {
            alarmPrefs.isFirstRun() -> Screen.Tutorial.route // 1순위: 앱을 처음 실행한 경우
            isResetPassword -> Screen.LoginDemo.route        // 2순위: 비밀번호 재설정 딥링크
            !isLoggedIn -> Screen.LoginDemo.route            // 3순위: Supabase 로그인이 안 된 경우 로그인 데모 화면으로
            alarmPrefs.isAlarmSet() -> Screen.SettedAlarm.route       // 4순위: 알람이 설정되어 있는 경우
            startDestinationFromIntent != null -> startDestinationFromIntent // 5순위: 알람을 끄고 온 경우 (피드백 화면)
            else -> Screen.Home.route                                 // 6순위: 일반적인 경우
        }
    }

}

