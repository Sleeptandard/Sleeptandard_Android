package com.example.sleeptandard_mvp_demo.Component

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import com.example.sleeptandard_mvp_demo.Screen.HomeScreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.createGraph
import com.example.sleeptandard_mvp_demo.AlarmRingScreen

import com.example.sleeptandard_mvp_demo.ClassFile.Alarm
import com.example.sleeptandard_mvp_demo.ClassFile.AlarmScheduler
import com.example.sleeptandard_mvp_demo.ClassFile.QnARepository
import com.example.sleeptandard_mvp_demo.Prefs.AlarmPreferences
import com.example.sleeptandard_mvp_demo.Screen.ExperimentScreen
import com.example.sleeptandard_mvp_demo.Screen.InquireScreen
import com.example.sleeptandard_mvp_demo.Screen.JournalScreen
import com.example.sleeptandard_mvp_demo.Screen.QnAScreen
import com.example.sleeptandard_mvp_demo.Screen.QnADetailScreen
import com.example.sleeptandard_mvp_demo.Screen.ReviewAlarmScreen
import com.example.sleeptandard_mvp_demo.Screen.SendingDataScreen
import com.example.sleeptandard_mvp_demo.Screen.SettedAlarmScreen
import com.example.sleeptandard_mvp_demo.Screen.SettingsScreen
import com.example.sleeptandard_mvp_demo.Screen.SplashScreen
import com.example.sleeptandard_mvp_demo.Screen.TutorialScreen
import com.example.sleeptandard_mvp_demo.ViewModel.AlarmViewModel
import kotlinx.coroutines.delay

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
                goToAlarmRingScreen = {
                    rememberNavController.navigate(Screen.AlarmRing.route)
                },
                goToFeedbackScreen = {
                    rememberNavController.navigate(Screen.ReviewAlarm.route)
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
            /* 뒤로가기 하면 화면 스택 전부 날아가고 홈으로 돌아가는건데 앞으로 구현할것 생각하면 못쓸거 같긴 함.
            BackHandler {
                rememberNavController.navigate(Screen.Home.route) {
                    popUpTo(rememberNavController.graph.startDestinationId) {
                        inclusive = true
                    }
                    launchSingleTop = true
                }
            }
            */
            JournalScreen()
        }

        composable(Screen.Settings.route) {
            /*
            BackHandler {
                rememberNavController.navigate(Screen.Home.route) {
                    popUpTo(rememberNavController.graph.startDestinationId) {
                        inclusive = true
                    }
                    launchSingleTop = true
                }
            }
             */
            SettingsScreen(
                onClickQnA = {
                    rememberNavController.navigate(Screen.QnA.route)
                },
                onClickTutorial = {rememberNavController.navigate(Screen.Tutorial.route)},
                onClickPermission = {},
                onClickSendingData = {rememberNavController.navigate(Screen.SendingData.route)}
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
            InquireScreen()
        }
        composable(Screen.Tutorial.route){
            TutorialScreen(
                onFinish = {
                    rememberNavController.navigate(Screen.Home.route){
                        popUpTo(Screen.Home.route){inclusive = true}
                    }
                }
            )
        }
        composable(Screen.SendingData.route) {
            SendingDataScreen()
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
                label = "시험용임",
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