package com.leejang.sleeptandard.Component

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
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
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.leejang.sleeptandard.Screen.HomeScreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
import com.leejang.sleeptandard.AlarmRingScreen

import com.leejang.sleeptandard.ClassFile.Alarm
import com.leejang.sleeptandard.ClassFile.AlarmScheduler
import com.leejang.sleeptandard.ClassFile.QnARepository
import com.leejang.sleeptandard.ClassFile.User
import com.leejang.sleeptandard.Prefs.AlarmPreferences
import com.leejang.sleeptandard.Prefs.UserInfoPreferences
import com.leejang.sleeptandard.Screen.AccountManagementScreen
import com.leejang.sleeptandard.Screen.ExperimentScreen
import com.leejang.sleeptandard.Screen.InquireScreen
import com.leejang.sleeptandard.Screen.JournalScreen
import com.leejang.sleeptandard.Screen.LoginDemoScreen
import com.leejang.sleeptandard.Screen.QnAScreen
import com.leejang.sleeptandard.Screen.QnADetailScreen
import com.leejang.sleeptandard.Screen.ReviewAlarmScreen
import com.leejang.sleeptandard.Screen.SendingDataScreen
import com.leejang.sleeptandard.Screen.SettedAlarmScreen
import com.leejang.sleeptandard.Screen.SettingsScreen
import com.leejang.sleeptandard.Screen.TutorialScreen
import com.leejang.sleeptandard.ViewModel.AlarmViewModel
import com.leejang.sleeptandard.ViewModel.AuthViewModel
import com.leejang.sleeptandard.ui.theme.AppIcons

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Journal : Screen("journal")
    object Settings : Screen("settings")
    object SendingData: Screen("sendingdata")
    object QnADetail : Screen("qna_detail/{id}") {
        fun createRoute(id: String) = "qna_detail/$id"
    }
    object AccountManagement : Screen("accont_management")

    // 컴포즈 스플래시 화면
    // object Splash : Screen("splash" , showBottomBar = false)
    object SettedAlarm : Screen("settedAlarm")
    object ReviewAlarm : Screen("reviewAlarm")
    object QnA: Screen("qna")
    object Inquire: Screen("inquire")
    object Tutorial: Screen("tutorial")
    object AlarmRing: Screen("alarmringscreen")


    object Experiment : Screen("experiment")

    /** 로그인 데모 **/
    object LoginDemo : Screen("loginDemo")
}

@Composable
fun AppNav(
    scheduler: AlarmScheduler,
    // 실험중
    startDestination: String = Screen.Home.route,
    initialAlarm: Alarm? = null,
    userInfo: User? = null,
    isPasswordReset: Boolean = false
){
    /*** 기존에 있던 코드 ***/
    val rememberNavController = rememberNavController()
    val alarmViewModel: AlarmViewModel = viewModel()
    val authViewModel: AuthViewModel = viewModel()


    // 앱 시작 시, initialAlarm이 있으면 ViewModel에 세팅
    LaunchedEffect(initialAlarm) {
        if (initialAlarm != null) {
            alarmViewModel.copyAlarm(initialAlarm)
        }
    }

    LaunchedEffect(userInfo) {
        if (userInfo != null){
            authViewModel.getUserInfo(userInfo)
        }
    }

    // AlarmPreference를 위한 컨텍스트
    val context = LocalContext.current
    val alarmPrefs = AlarmPreferences(context)
    val isAlarmSetted = alarmPrefs.isAlarmSet()
    val userPrefs = remember(context) { UserInfoPreferences(context) }  // 알람 SharedPreference 가져오기

    // 네비게이션바 블러처리 여부
    var isBlurred by remember{ mutableStateOf(false) }


    // ✅ 1. 영구 저장소에서 초기 값을 가져와 세션 상태로 관리합니다.
    var showWindowTutorial by remember { mutableStateOf(alarmPrefs.getShowWindowTutorial()) }


    val navGraph = rememberNavController.createGraph(startDestination = startDestination){

        /** 로그인 데모 **/
        composable(Screen.LoginDemo.route){
            LoginDemoScreen(
                authViewModel = authViewModel,
                onConfirm = { user: User ->
                    // UserInfoPrefs에 유저 정보 저장
                    userPrefs.saveUserInfo(user)
                    // 전역 뷰모델 최신화
                    authViewModel.getUserInfo(user)
                    
                    rememberNavController.navigate(Screen.Home.route)
                    Toast.makeText(context, user.nickname, Toast.LENGTH_SHORT).show()
                },
                isPasswordReset = isPasswordReset
            )
        }

        composable(Screen.Home.route){
            HomeScreen(
                alarmViewModel = alarmViewModel,
                scheduler = scheduler,
                onClickConfirm = {
                    rememberNavController.navigate(Screen.SettedAlarm.route){
                        popUpTo(Screen.Home.route){inclusive = true}
                    }
                },
                /*
                goExperimentScreen = {
                    rememberNavController.navigate(Screen.Experiment.route)
                },
                 */
                showWindowTutorial = showWindowTutorial,
                onDismissTutorial = { isChecked ->
                    // 2번 요구사항: 체크박스를 체크하고 닫았다면 영구적으로 보이지 않게 저장
                    if (isChecked) {
                        alarmPrefs.setShowWindowTutorial(false)
                    }
                    // 현재 세션에서 창 닫기
                    showWindowTutorial = false
                }
            )
        }

        composable(Screen.SettedAlarm.route){
            SettedAlarmScreen(
                alarmViewModel = alarmViewModel,
                scheduler = scheduler,
                onTurnAlarmOff = {
                    rememberNavController.navigate(Screen.Home.route){
                        // 0번(루트)까지 모든 화면을 스택에서 제거(inclusive)합니다.
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
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

            SettingsScreen(
                onClickAccount = {
                    rememberNavController.navigate(Screen.AccountManagement.route)
                },
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
            )
        }

        composable(Screen.AccountManagement.route){
            AccountManagementScreen(
                onBack = { rememberNavController.popBackStack() },
                userViewModel = authViewModel,
                onEmailUpdate = {
                    authViewModel.updateUserEmail(
                        newEmail = authViewModel.email,
                        onSuccess = { 
                            userPrefs.saveUserInfo(authViewModel.loadUserInfo()) 
                            Toast.makeText(context, "이메일이 변경되었습니다.", Toast.LENGTH_SHORT).show()
                        },
                        onError = { errorMsg ->
                            Toast.makeText(context, "이메일 변경 실패: $errorMsg", Toast.LENGTH_SHORT).show()
                        }
                    )
                },
                onNicknameUpdate = {
                    authViewModel.saveProfileUpdate(
                        onSuccess = { userPrefs.saveUserInfo(authViewModel.loadUserInfo()) }
                    )
                },
                onGenderUpdate = {
                    authViewModel.saveProfileUpdate(
                        onSuccess = { userPrefs.saveUserInfo(authViewModel.loadUserInfo()) }
                    )
                },
                onBirthdateUpdate = {
                    authViewModel.saveProfileUpdate(
                        onSuccess = { userPrefs.saveUserInfo(authViewModel.loadUserInfo()) }
                    )
                },
                onPasswordUpdate = {

                    authViewModel.updateUserPassword(
                        newPassword = authViewModel.password,
                        onSuccess = { 
                            userPrefs.saveUserInfo(authViewModel.loadUserInfo()) 
                            Toast.makeText(context, "비밀번호가 변경되었습니다.", Toast.LENGTH_SHORT).show()
                        },
                        onError = { errorMsg ->
                            Toast.makeText(context, "비밀번호 변경 실패: $errorMsg", Toast.LENGTH_SHORT).show()
                        }
                    )
                },
                onLogout = {
                    authViewModel.logoutUser(
                        onSuccess = {
                            userPrefs.clearUserInfo()
                            rememberNavController.navigate(Screen.LoginDemo.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                },
                onAccountDelete = {
                    authViewModel.deleteUserAccount(
                        onSuccess = {
                            userPrefs.clearUserInfo()
                            rememberNavController.navigate(Screen.LoginDemo.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }
            )
        }

        composable(Screen.QnA.route){
            QnAScreen(
                onBack = { rememberNavController.popBackStack() },
                onClickItem = { id ->
                    rememberNavController.navigate(Screen.QnADetail.createRoute(id))
                },
                onSubmit = { title, body, uris ->
                    val emailIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        // 1. 수신자 설정
                        putExtra(Intent.EXTRA_EMAIL, arrayOf("studyjun0224@gmail.com"))

                        // 2. 제목 설정
                        putExtra(Intent.EXTRA_SUBJECT, "[문의사항] $title")

                        // 3. 본문 설정
                        putExtra(Intent.EXTRA_TEXT, body)

                        // 4. 첨부 파일(이미지) 추가
                        // Intent는 ArrayList 형태의 Uri를 받습니다.
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))

                        // 5. 타입 설정 (이미지 및 메시지 형식)
                        type = "message/rfc822" // 또는 "image/*"

                        // 첨부 파일에 대한 읽기 권한 부여
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    try {
                        // 이메일 앱 선택창 띄우기
                        context.startActivity(Intent.createChooser(emailIntent, "이메일 앱을 선택하세요"))

                        // 제출 후 화면 뒤로 가기 (선택 사항)
                        rememberNavController.popBackStack()
                    } catch (e: Exception) {
                        Toast.makeText(context, "이메일 앱을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                        Log.d("no_email", "$e")
                    }
                },
                showInquireModal = isBlurred,
                onClickAsk = { isBlurred = true },
                onDismiss = {isBlurred = false}
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
                    // ✅ 3번 요구사항: TutorialScreen 완료 시 튜토리얼을 볼 수 있게 설정
                    alarmPrefs.setShowWindowTutorial(true) // 영구 저장소 업데이트
                    showWindowTutorial = true               // 세션 상태 업데이트

                    if(userPrefs.isLogined()) {
                        rememberNavController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Tutorial.route) { inclusive = true }
                        }
                    }else{
                        rememberNavController.navigate(Screen.LoginDemo.route)
                    }
                }
                /*
                onFinish = {
                    alarmPrefs.setFirstRunCompleted()
                    rememberNavController.popBackStack()
                    when{
                        // TODO: 로그인 정보가 없는경우 -> Screen.LoginDemo.route
                        alarmPrefs.isFirstRun() -> Screen.Tutorial.route
                        else -> Screen.Home.route
                    }
                    rememberNavController.navigate(Screen.Home.route)
                }

                 */
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
                Screen.SettedAlarm.route,
                Screen.Settings.route,
                Screen.QnA.route,
                Screen.QnADetail.route,
                Screen.SendingData.route -> true
                Screen.AccountManagement.route -> true
                else -> false
            }

            // 위에 조건에 부합하면 바텀네바바를 띄움
            if (showBottom) {
                AlarmBottomNavBar(
                    isBlurred = isBlurred,
                    selectedIndex = when (currentRoute) {
                        Screen.Home.route -> 0
                        Screen.SettedAlarm.route -> 0
                        Screen.Journal.route -> 1
                        Screen.Settings.route -> 2
                        Screen.QnA.route -> 2
                        Screen.QnADetail.route -> 2
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
                            launchSingleTop = true  // 동일 화면이 스택 맨 위에 있다면 새로 만들지 않음
                            restoreState = true     // 이전에 입력한 정보 등이 있다면 복구
                            // 뒤로가기를 누르면 그래프에 설정되어 있는 startDestination으로 날아감
                            popUpTo(rememberNavController.graph.startDestinationId) {
                                saveState = true
                            }
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
    isBlurred: Boolean,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val blurRadius = if (isBlurred) 20.dp else 0.dp
    NavigationBar(
        modifier = Modifier
            .neumorphicBackground(
                highlightColor = Color(0xFF12253F).copy(alpha = 0.3f)
            ),
        containerColor = MaterialTheme.colorScheme.background,
        windowInsets = NavigationBarDefaults.windowInsets
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().blur(blurRadius),
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