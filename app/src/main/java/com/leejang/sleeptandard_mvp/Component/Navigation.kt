package com.leejang.sleeptandard_mvp.Component

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.leejang.sleeptandard_mvp.Screen.HomeScreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.createGraph
import com.leejang.sleeptandard_mvp.backend.manager.SupabaseManager
import io.github.jan.supabase.gotrue.gotrue
import io.github.jan.supabase.gotrue.providers.builtin.Email
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.leejang.sleeptandard_mvp.AlarmRingScreen

import com.leejang.sleeptandard_mvp.ClassFile.Alarm
import com.leejang.sleeptandard_mvp.ClassFile.AlarmScheduler
import com.leejang.sleeptandard_mvp.ClassFile.QnARepository
import com.leejang.sleeptandard_mvp.Prefs.AlarmPreferences
import com.leejang.sleeptandard_mvp.Screen.ExperimentScreen
import com.leejang.sleeptandard_mvp.Screen.InquireScreen
import com.leejang.sleeptandard_mvp.Screen.JournalScreen
import com.leejang.sleeptandard_mvp.Screen.QnAScreen
import com.leejang.sleeptandard_mvp.Screen.QnADetailScreen
import com.leejang.sleeptandard_mvp.Screen.ReviewAlarmScreen
import com.leejang.sleeptandard_mvp.Screen.SendingDataScreen
import com.leejang.sleeptandard_mvp.Screen.SettedAlarmScreen
import com.leejang.sleeptandard_mvp.Screen.SettingsScreen
import com.leejang.sleeptandard_mvp.Screen.TutorialScreen
import com.leejang.sleeptandard_mvp.ViewModel.AlarmViewModel
import com.leejang.sleeptandard_mvp.ui.theme.AppIcons

sealed class Screen(val route: String, val showBottomBar: Boolean = true) {
    object Home : Screen("home", showBottomBar = true)
    object Journal : Screen("journal", showBottomBar = true)
    object Settings : Screen("settings", showBottomBar = true)
    object SendingData: Screen("sendingdata", showBottomBar = true)
    object QnADetail : Screen("qna_detail/{id}", showBottomBar = true) {
        fun createRoute(id: String) = "qna_detail/$id"
    }

    // 컴포즈 스플래시 화면
    // object Splash : Screen("splash" , showBottomBar = false)
    object SettedAlarm : Screen("settedAlarm", showBottomBar = false)
    object ReviewAlarm : Screen("reviewAlarm", showBottomBar = false)
    object QnA: Screen("qna", showBottomBar = false)
    object Inquire: Screen("inquire", showBottomBar = false )
    object Tutorial: Screen("tutorial", showBottomBar = false)
    object AlarmRing: Screen("alarmringscreen", showBottomBar = false)


    object Experiment : Screen("experiment", showBottomBar = false)
}

@Composable
fun AppNav(
    scheduler: AlarmScheduler,
    // 실험중
    startDestination: String = Screen.Home.route,
    initialAlarm: Alarm? = null   // ✨ 추가
){

    /*** 기존에 있던 코드 ***/
    val rememberNavController = rememberNavController()
    val alarmViewModel: AlarmViewModel = viewModel()

    // 앱 시작 시, initialAlarm이 있으면 ViewModel에 세팅
    LaunchedEffect(initialAlarm) {
        if (initialAlarm != null) {
            alarmViewModel.copyAlarm(initialAlarm)
        }
    }

    // AlarmPreference를 위한 컨텍스트
    val context = LocalContext.current
    val alarmPrefs = AlarmPreferences(context)
    val isAlarmSetted = alarmPrefs.isAlarmSet()

    val navGraph = rememberNavController.createGraph(startDestination = startDestination){

        /* 컴포즈 스플래시
        composable(Screen.Splash.route){
            LaunchedEffect(Unit) {
                delay(900) // 0.9초 보여주기
                rememberNavController.navigate("home") {
                    popUpTo("splash") { inclusive = true } // 스플래시를 backstack에서 제거
                }
            }
            SplashScreen()
        }
         */

        composable(Screen.Home.route){
            HomeScreen(
                alarmViewModel = alarmViewModel,
                scheduler = scheduler,
                onClickConfirm = {
                    rememberNavController.navigate(Screen.SettedAlarm.route){
                        popUpTo(Screen.Home.route){inclusive = true}
                    }
                },
                goExperimentScreen = {
                    rememberNavController.navigate(Screen.Experiment.route)
                }
            )
        }

        composable(Screen.SettedAlarm.route){
            SettedAlarmScreen(
                alarmViewModel = alarmViewModel,
                scheduler = scheduler,
                onTurnAlarmOff = {
                    rememberNavController.navigate(Screen.Home.route){
                        popUpTo(Screen.SettedAlarm.route){inclusive = true}
                    }
                }
            )
        }

        composable(Screen.ReviewAlarm.route){
            ReviewAlarmScreen(
                onSubmit = {
                    rememberNavController.navigate(Screen.Home.route){
                        // 네비 스택 초기화
                        popUpTo(Screen.ReviewAlarm.route){inclusive = true}
                    }

                }
            )
        }

        composable(Screen.Journal.route) {

            JournalScreen()
        }

        composable(Screen.Settings.route) {

            val scope = rememberCoroutineScope()

            SettingsScreen(
                onClickQnA = {
                    rememberNavController.navigate(Screen.QnA.route)
                },
                onClickTutorial = {rememberNavController.navigate(Screen.Tutorial.route)},
                onClickPermission = {val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    // 이 앱의 패키지명을 Uri 데이터로 설정하여 해당 앱 설정 페이지로 바로 이동하게 함
                    data = Uri.fromParts("package", context.packageName, null)
                }
                    context.startActivity(intent)},
                onClickSendingData = {rememberNavController.navigate(Screen.SendingData.route)},
                onClickTestLogin = {
                    scope.launch(Dispatchers.IO) {
                        try {
                            // 1단계: 로그인 시도
                            try {
                                SupabaseManager.client.gotrue.loginWith(Email) {
                                    email = "test@sleep.com"
                                    password = "testpassword123!"
                                }
                            } catch (loginEx: Exception) {
                                // 로그인 실패 시 회원가입 시도 (계정 없음으로 간주)
                                SupabaseManager.client.gotrue.signUpWith(Email) {
                                    email = "test@sleep.com"
                                    password = "testpassword123!"
                                }
                            }

                            // 2단계: user_id 추출
                            val userId = SupabaseManager.client.gotrue.currentUserOrNull()?.id
                                ?: throw Exception("user_id를 가져올 수 없습니다.")

                            // 3단계: SharedPreferences에 user_id 저장
                            context.getSharedPreferences("sleep_prefs", Context.MODE_PRIVATE)
                                .edit()
                                .putString("user_id", userId)
                                .apply()

                            // 4단계: 성공 Toast
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "테스트 로그인 성공!\nID: $userId", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "로그인 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )
        }

        composable(Screen.QnA.route){
            QnAScreen(
                onBack = { rememberNavController.popBackStack() },
                onClickAsk = { rememberNavController.navigate(Screen.Inquire.route) },
                onClickItem = { id ->
                    rememberNavController.navigate(Screen.QnADetail.createRoute(id))
                }
            )
        }
        composable(Screen.Inquire.route){
            InquireScreen(
                onBack = {
                    rememberNavController.popBackStack()
                }
            )
        }
        composable(Screen.Tutorial.route){
            TutorialScreen(
                onFinish = {
                    alarmPrefs.setFirstRunCompleted()
                    rememberNavController.popBackStack()
                    rememberNavController.navigate(Screen.Home.route)
                }
            )
        }
        composable(Screen.SendingData.route) {
            SendingDataScreen(
                onBack = { rememberNavController.popBackStack() }
            )
        }

        composable("qna_detail/{id}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: return@composable

            val item = QnARepository.findById(id)  // ✅ id로 찾기(4번에서 만듦)
            if (item != null) {
                QnADetailScreen(
                    item = item,
                    onBack = { rememberNavController.popBackStack() },
                    onClickAskDeveloper = { rememberNavController.navigate(Screen.Inquire.route) }
                )
            }
        }

        /** 실험장 **/
        composable(Screen.Experiment.route){
            ExperimentScreen()
        }

        composable(Screen.AlarmRing.route){
            AlarmRingScreen(
                // label = "시험용임",
                onStop = {}
            )
        }

    }

    val navBackStackEntry by rememberNavController.currentBackStackEntryAsState()   // 최신 스택을 가져옴 (현재 위치한 경로)
    val currentRoute = navBackStackEntry?.destination?.route    // 최신 스택의 route를 가져옴 (현재 위치한 경로)

    Scaffold(
        bottomBar = {
            // 지금 라우트가 홈, 일지, 설정, 알람설정완료화면, 데이터보내기  이면 바텀네비바를 띄우기 위한 Boolean값임.
            val showBottom = when (currentRoute) {
                Screen.Home.route,
                Screen.Journal.route,
                Screen.Settings.route,
                Screen.SettedAlarm.route,
                Screen.SendingData.route -> true
                else -> false
            }

            // 위에 조건에 부합하면 바텀네바바를 띄움
            if (showBottom) {
                AlarmBottomNavBar(
                    selectedIndex = when (currentRoute) {
                        Screen.Home.route -> 0
                        Screen.SettedAlarm.route -> 0
                        Screen.Journal.route -> 1
                        Screen.Settings.route -> 2
                        Screen.SendingData.route -> 2

                        else -> 2
                    },
                    onSelect = { idx ->
                        val target = when (idx) {
                            0 -> if(isAlarmSetted) Screen.SettedAlarm.route else Screen.Home.route
                            1 -> Screen.Journal.route
                            2 -> Screen.Settings.route
                            else -> Screen.Home.route
                        }
                        rememberNavController.navigate(target) {
                            launchSingleTop = true
                            restoreState = true
                            popUpTo(Screen.Home.route) { saveState = true }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->

        NavHost(
            navController = rememberNavController,
            modifier = Modifier.padding(innerPadding),
            graph = navGraph
        )
    }

}

@Composable
fun AlarmBottomNavBar(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            StandaloneBottomItem(
                selected = selectedIndex == 0,
                iconRes = AppIcons.NavAlarm,
                label = "알람",
                onClick = { onSelect(0) }
            )
            StandaloneBottomItem(
                selected = selectedIndex == 1,
                iconRes = AppIcons.NavJournal,
                label = "일지",
                onClick = { onSelect(1) }
            )
            StandaloneBottomItem(
                selected = selectedIndex == 2,
                iconRes = AppIcons.NavSettings,
                label = "설정",
                onClick = { onSelect(2) }
            )
        }
    }
}

@Composable
fun StandaloneBottomItem(
    selected: Boolean,
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab
            )
            .padding(0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            // ✅ 선택: 원본색 유지 / 비선택: 회색 틴트
            tint = if (selected) Color(0xFFE0F5FD)
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )

        if (selected) {
            Spacer(Modifier.height(7.dp))
            // ✅ 점 표시
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = Color(0xFFE0F5FD),
                        shape = CircleShape
                    )
            )
        } else {
            // ✅ 텍스트 표시
            Text(
                text = label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
    }
}