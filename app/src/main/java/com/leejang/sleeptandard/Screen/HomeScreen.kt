package com.leejang.sleeptandard.Screen

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leejang.sleeptandard.ClassFile.AlarmScheduler
import com.leejang.sleeptandard.Component.AlarmSoundSettingContent
import com.leejang.sleeptandard.Component.ConfirmButton
import com.leejang.sleeptandard.Component.CustomTimePicker
import com.leejang.sleeptandard.Component.OptionsSection
import com.leejang.sleeptandard.Component.PotchSelectionSheet
import com.leejang.sleeptandard.Component.PotchConnectionState
import com.leejang.sleeptandard.Component.ShowWakeUpRange
import com.leejang.sleeptandard.Component.SituationContent
import com.leejang.sleeptandard.Component.SituationOption
import com.leejang.sleeptandard.Component.WakeUpWindow
import com.leejang.sleeptandard.Component.WindowTutorial
import com.leejang.sleeptandard.Component.calculateWakeUpRangeText
import com.leejang.sleeptandard.Component.neumorphicBackground
import com.leejang.sleeptandard.Potch.PotchBleViewModel
import com.leejang.sleeptandard.Prefs.AlarmPreferences
import com.leejang.sleeptandard.ViewModel.AlarmViewModel
import com.leejang.sleeptandard.ui.theme.DarkBackground
import com.leejang.sleeptandard.utility.getIsNotificationVibrationOn
import kotlin.math.roundToInt

private fun requiredPotchPermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()


@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    alarmViewModel: AlarmViewModel,
    scheduler: AlarmScheduler,
    onClickConfirm: ()-> Unit,
    showWindowTutorial: Boolean,
    onDismissTutorial: (Boolean) -> Unit, // ✅ Boolean 인자 추가
    goExperimentScreen: ()-> Unit = {},
    onBatteryWarningVisibilityChange: (Boolean) -> Unit = {},
    potchViewModel: PotchBleViewModel = viewModel(),
) {
    val context = LocalContext.current
    val alarmPrefs = remember(context) { AlarmPreferences(context) }  // 알람 SharedPreference 가져오기
    val bleState by potchViewModel.bleState.collectAsState()
    val processorState by potchViewModel.processorState.collectAsState()
    var potchPermissionDenied by remember { mutableStateOf(false) }
    var bluetoothEnabled by remember { mutableStateOf(isPhoneBluetoothEnabled(context)) }
    var showBluetoothOffMessage by remember { mutableStateOf(false) }
    var showLowBatteryWarning by remember { mutableStateOf(false) }
    var warningBattery by remember { mutableIntStateOf(0) }

    val potchPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val allGranted = requiredPotchPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED
        }
        potchPermissionDenied = !allGranted

        if (allGranted) {
            bluetoothEnabled = isPhoneBluetoothEnabled(context)
            if (bluetoothEnabled) {
                showBluetoothOffMessage = false
                potchViewModel.startHomeConnection()
            } else {
                showBluetoothOffMessage = true
            }
        } else {
            Toast.makeText(
                context,
                "팟치 연결을 위해 블루투스 권한이 필요합니다.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    DisposableEffect(context) {
        val bluetoothStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
                bluetoothEnabled = intent.getIntExtra(
                    BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR
                ) == BluetoothAdapter.STATE_ON
                if (bluetoothEnabled) showBluetoothOffMessage = false
            }
        }
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                bluetoothStateReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(bluetoothStateReceiver, filter)
        }

        onDispose {
            runCatching { context.unregisterReceiver(bluetoothStateReceiver) }
        }
    }

    /**** 알람뷰모델에 넣을 값들임 ****/
    var selectedHour by remember { mutableIntStateOf(alarmViewModel.alarm.hour) }
    var selectedMinute by remember { mutableIntStateOf(alarmViewModel.alarm.minute) }
    var selectedIsAm by remember { mutableStateOf(alarmViewModel.alarm.isAm) }
    var selectedRingtoneUri by remember { mutableStateOf(alarmViewModel.alarm.ringtoneUri) }
    var selectedVibrationEnabled by remember { mutableStateOf(alarmViewModel.alarm.vibrationEnabled) }
    var selectedVolume by remember { mutableIntStateOf(alarmViewModel.alarm.volume) }
    var earlyWakeUpMinutes by remember { mutableIntStateOf(alarmViewModel.alarm.earlyWakeUpMinutes) }
    var isRem by remember { mutableStateOf(alarmViewModel.alarm.isRem) }

    // 옵션섹션 - 알람음설정 컴포넌트에 띄울 알람음 이름
    var alarmName by remember { mutableStateOf("") }

    // 타임피커가 돌아가던중 다른 컴포넌트를 클릭했을때의 타임피커 멈춤 트리거
    var stopSignal by remember { mutableIntStateOf(0) }

    /*
    /****** 상황선택 관련 녀석들 ******/
    var showSituationModal by remember { mutableStateOf(false) }     // 메모 모달창 띄우는 트리거
    var customText by remember { mutableStateOf("") }    // "직접추가"에서 입력한 텍스트
    // 기본 상태
    val situationOptions = listOf(
        SituationOption("drink", "술 한 잔 했어요"),
        SituationOption("tired", "활동량이 많았어요 (운동/업무 등)"),
        SituationOption("sick", "몸이 좋지 않아요"),
    )
    // "직접추가" 모드 트리거
    var isCustomMode by remember { mutableStateOf(false) }

     */


    /** 사운드 설정창 띄우는 트리거 **/
    var showSoundSheet by remember { mutableStateOf(false) }

    /** 진동 세기 감지하는데 사용하는 녀석들 **/
    // 시스템 진동 세기 상태 관리
    var isNotificationVibrationOn by remember { mutableStateOf(false) }
    // 화면이 켜질 때마다 시스템 설정값 확인
    val lifecycleOwner = LocalLifecycleOwner.current
    // 화면이 다시 활성화될 때마다(Resume) 실행되는 로직
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                Log.d("VibrationSetting", "앱으로 돌아옴: 진동 세기 다시 체크")
                isNotificationVibrationOn = getIsNotificationVibrationOn(context)
                bluetoothEnabled = isPhoneBluetoothEnabled(context)
                if (bluetoothEnabled) showBluetoothOffMessage = false
            }
        }

        // 옵저버 등록
        lifecycleOwner.lifecycle.addObserver(observer)

        // 컴포저블이 파괴될 때 옵저버 제거
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 알람뷰모델에 저장되어 있는 알람 설정값들과 화면 상태 동기화
    LaunchedEffect(alarmViewModel.alarm) { // alarm 객체 전체를 관찰
        val alarm = alarmViewModel.alarm
        selectedRingtoneUri = alarm.ringtoneUri
        selectedVibrationEnabled = alarm.vibrationEnabled // ✅ 진동 상태 동기화
        selectedVolume = alarm.volume                     // ✅ 볼륨 상태 동기화

        if (alarm.ringtoneUri.isNotBlank()) {
            val uri = alarm.ringtoneUri.toUri()
            val ringtone = RingtoneManager.getRingtone(context, uri)
            alarmName = ringtone?.getTitle(context) ?: "소리 없음"
        } else {
            alarmName = "소리 없음"
        }
    }

    val potchState = when {
        !bluetoothEnabled && showBluetoothOffMessage -> PotchConnectionState.FAILED
        !bluetoothEnabled -> PotchConnectionState.NOTHING
        bleState.isNotificationReady -> PotchConnectionState.CONNECTED
        bleState.isScanning || bleState.isConnecting || bleState.isReconnecting ->
            PotchConnectionState.CONNECTING
        potchPermissionDenied || bleState.lastError != null -> PotchConnectionState.FAILED
        bleState.isConnected -> PotchConnectionState.CONNECTING
        else -> PotchConnectionState.NOTHING
    }

    val currentBattery = processorState.lastParsedData
        ?.batteryVoltage
        ?.takeIf { it.isFinite() }
        ?.let(::voltageToPotchBatteryPercent)

    fun saveAndScheduleAlarm() {
        alarmViewModel.saveAlarm(
            hour = selectedHour,
            minute = selectedMinute,
            isAm = selectedIsAm,
            ringtoneUri = selectedRingtoneUri,
            vibrationEnabled = selectedVibrationEnabled,
            volume = selectedVolume,
            earlyWakeUpMinutes = earlyWakeUpMinutes,
            isRem = isRem,
        )
        scheduler.schedule(alarmViewModel.alarm)
        alarmViewModel.startSleepTracking(
            targetTime = scheduler.getTriggerTime(),
            situationLabel = "normal"
        )
        alarmPrefs.saveAlarm(alarmViewModel.alarm)
        onClickConfirm()
    }

    LaunchedEffect(showLowBatteryWarning) {
        onBatteryWarningVisibilityChange(showLowBatteryWarning)
    }

    DisposableEffect(Unit) {
        onDispose {
            onBatteryWarningVisibilityChange(false)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .blur(if (showLowBatteryWarning) 20.dp else 0.dp)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(
                modifier = Modifier.height(10.dp)
            )
            Spacer(
                modifier = Modifier.weight(5f)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ){
                ShowWakeUpRange(selectedHour,selectedMinute,selectedIsAm)
            }
            /*** 타임 피커 ***/
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(320f / 260f)
                    .neumorphicBackground()
                    .innerShadow(
                        shape = RoundedCornerShape(30.dp),
                        shadow = Shadow(
                            radius = 25.dp,
                            spread = (-12).dp,
                            color = Color(0xFF030E1E).copy(0.8f),
                            offset = DpOffset(x = 5.dp, 6.dp)
                        )
                    ),
                contentAlignment = Alignment.Center

            ) {
                CustomTimePicker(
                    defaultHour12 = selectedHour,
                    defaultMinute = selectedMinute,
                    defaultIsAm = selectedIsAm,
                    stopSignal = stopSignal,
                    onTimeChange = { hour12, minute, isAm ->
                        selectedHour = hour12
                        selectedMinute = minute
                        selectedIsAm = isAm
                    },
                )
            }

            Spacer(Modifier.weight(2f))

        }

        /********    타임피커 밑    ********/

        // 밑을 전부 박스로 감싸서 버튼을 눌렀을때 타임피커 휠의 움직임을 멈추게 함
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                            stopSignal++ // ✅ 외부 터치 발생 → 타임피커 멈춤 신호
                        }
                    }
                }
        )
        {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(1f))

                /*
                WakeUpWindow(
                    modifier = Modifier
                        .fillMaxWidth(),
                    onValueChange = { earlyWakeUpMinutes = it },
                    selectedHour = selectedHour,
                    selectedMinute = selectedMinute,
                    selectedIsAm = selectedIsAm,
                    earlyWakeUpMinutes = earlyWakeUpMinutes
                )

                 */

                Spacer(Modifier.weight(1f))

                OptionsSection(
                    modifier = Modifier
                        .fillMaxWidth(),

                    // 링톤 설정
                    onSoundClick = {
                        showSoundSheet = true
                    },

                    // 진동 토글
                    onVibrationClick = {
                        if (isNotificationVibrationOn) {
                            selectedVibrationEnabled = !selectedVibrationEnabled
                        } else {
                            try {
                                // 안드로이드 시스템 소리 및 진동 설정창 호출
                                val intent =
                                    Intent(Settings.ACTION_SOUND_SETTINGS)
                                context.startActivity(intent)

                                // (선택 사항) 사용자에게 안내 메시지 표시
                                Toast.makeText(
                                    context,
                                    "알림 진동 세기를 조절해주세요.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: Exception) {
                                // 드문 경우지만 진동 설정창에 직접 접근이 안 될 때 일반 설정창으로 보냄
                                val intent =
                                    Intent(Settings.ACTION_SETTINGS)
                                context.startActivity(intent)
                                Log.d("notification_error", "$e")
                            }
                        }
                    },
                    checked = selectedVibrationEnabled,
                    onCheckedChange = { selectedVibrationEnabled = it },
                    alarmName = alarmName,
                    isSystemVibrationOn = isNotificationVibrationOn,
                    showBluetoothOffMessage = showBluetoothOffMessage,
                    potchState = potchState,
                    tryPotchConnecting = {
                        if (!bluetoothEnabled) {
                            showBluetoothOffMessage = true
                        } else if (potchState == PotchConnectionState.NOTHING ||
                            potchState == PotchConnectionState.FAILED
                        ) {
                            showBluetoothOffMessage = false
                            potchPermissionDenied = false
                            val missingPermissions = requiredPotchPermissions().filter { permission ->
                                ContextCompat.checkSelfPermission(context, permission) !=
                                        PackageManager.PERMISSION_GRANTED
                            }

                            if (missingPermissions.isEmpty()) {
                                potchViewModel.startHomeConnection()
                            } else {
                                potchPermissionLauncher.launch(missingPermissions.toTypedArray())
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.weight(2f))

                ConfirmButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    onClick = {
                        if (
                            potchState == PotchConnectionState.CONNECTED &&
                            currentBattery != null &&
                            currentBattery <= LOW_POTCH_BATTERY_PERCENT
                        ) {
                            warningBattery = currentBattery
                            showLowBatteryWarning = true
                        } else {
                            saveAndScheduleAlarm()
                        }
                    }
                )

                Button(
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    onClick = goExperimentScreen
                ){
                    Text(
                        text = "실험장"
                    )
                }

                Spacer(Modifier.height(25.dp))
            }
        }
    }

    /************************       이 밑으로 모달 창          *********************************/

    if (bleState.isDeviceSelectionRequired) {
        PotchSelectionSheet(
            devices = bleState.discoveredDevices,
            isScanning = bleState.isScanning,
            errorMessage = bleState.lastError,
            onSelect = potchViewModel::selectPotch,
            onRetry = potchViewModel::startHomeConnection,
            onDismiss = potchViewModel::cancelDeviceDiscovery
        )
    }

    if (showLowBatteryWarning) {
        PotchLowBatteryWarningDialog(
            currentBattery = warningBattery,
            onDismiss = { showLowBatteryWarning = false },
            onUseAnyway = {
                showLowBatteryWarning = false
                saveAndScheduleAlarm()
            }
        )
    }

    /*** 사운드 선택 모달 ***/
    if (showSoundSheet) {
        val soundSheetState =
            rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = {
                // ✅ 핵심: 모달이 어떤 방식으로든 닫힐 때 모든 입력 상태를 초기화합니다.
                showSoundSheet = false
                /*
                isCustomMode = false  // 다음번 열 때 리스트가 보이도록 리셋
                customText = ""       // 입력하던 텍스트도 비워줌

                 */
            },
            sheetState = soundSheetState,
            containerColor = DarkBackground,
            scrimColor = Color.Black.copy(alpha = 0.55f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }, // 여백 없애기
            dragHandle = null, // 드래그핸들 없앰.
            sheetGesturesEnabled = false
        ) {
            // ✅ 여기 안에 AlarmSoundSettingScreen의 "내용"을 넣는다
            AlarmSoundSettingContent(
                onVolumeChange = { selectedVolume = it },
                currentUriString = alarmViewModel.alarm.ringtoneUri,
                onClose = { showSoundSheet = false },
                onSelectUriString = { uriStr ->
                    alarmViewModel.saveAlarm(
                        hour = selectedHour,
                        minute = selectedMinute,
                        isAm = selectedIsAm,
                        ringtoneUri = uriStr,
                        vibrationEnabled = selectedVibrationEnabled,
                        volume = selectedVolume,
                        earlyWakeUpMinutes = earlyWakeUpMinutes,
                        isRem = isRem,
                    )
                },
                defaultVolume = alarmViewModel.alarm.volume,
            )
        }
    }

    /*
    /*** 상황 설정 모달 ***/
    if (showSituationModal) {

        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        )

        ModalBottomSheet(
            onDismissRequest = { showSituationModal = false },
            sheetState = sheetState,
            containerColor = Color(0xFF050C16),
            // 밖 영역은 어두워지고 클릭 막힘(scrim)
            scrimColor = Color.Black.copy(alpha = 0.55f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = { BottomSheetDefaults.DragHandle(width = 126.dp) }
        ) {
            // 진짜 개지랄
            SituationContent(
                alarmPrefs = alarmPrefs,
                alarmViewModel = alarmViewModel,
                scheduler = scheduler,
                selectedHour = selectedHour,
                selectedMinute = selectedMinute,
                selectedIsAm = selectedIsAm,
                selectedRingtoneUri = selectedRingtoneUri,
                selectedVibrationEnabled = selectedVibrationEnabled,
                selectedVolume = selectedVolume,
                earlyWakeUpMinutes = earlyWakeUpMinutes,
                isRem = isRem,
                isCustomMode = isCustomMode,
                situationOptions = situationOptions,
                onCustomMode = { isCustomMode = true },
                offCustomMode = { isCustomMode = false },
                offSituationModal = { showSituationModal = false },
                onClickConfirm = onClickConfirm,
                customText = customText,
                onCustomTextChange = { customText = it }
            )
        }
    }

     */

    /**** 윈도우 튜토리얼창 ****/
    if (showWindowTutorial) {
        Dialog(
            onDismissRequest = { onDismissTutorial(false) }, // 배경 클릭 시에는 '체크 안 함'으로 간주
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF050C16).copy(alpha = 0.75f))
            ) {

                WindowTutorial(
                    onDismiss = { isChecked -> onDismissTutorial(isChecked) }
                )

            }
        }
    }
}

@Composable
private fun PotchLowBatteryWarningDialog(
    currentBattery: Int,
    onDismiss: () -> Unit,
    onUseAnyway: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x4D050C16))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                shape = RoundedCornerShape(40.dp),
                color = Color(0xFFF1F2F3),
                tonalElevation = 0.dp,
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "팟치 배터리가 부족해요",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                color = Color(0xFF050C16),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "현재 ${currentBattery}%",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = Color(0xFF050C16),
                                    fontSize = 18.sp
                                )
                            )
                            Text(
                                text = buildAnnotatedString {
                                    append("8시간 30분 사용에는 ")
                                    withStyle(SpanStyle(color = Color(0xFFEB3737))) {
                                        append("${LOW_POTCH_BATTERY_PERCENT}%")
                                    }
                                    append(" 이상 필요해요.")
                                },
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = Color(0xFF050C16),
                                    fontSize = 17.sp
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth(0.68f)
                                .height(58.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFB1F7FC),
                                contentColor = Color(0xFF050C16)
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                        ) {
                            Text(
                                text = "충전하고 오기",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }

                        TextButton(
                            onClick = onUseAnyway,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Color(0xFF30343A)
                            )
                        ) {
                            Text(
                                text = "그냥 사용하기",
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val LOW_POTCH_BATTERY_PERCENT = 40

@SuppressLint("MissingPermission")
private fun isPhoneBluetoothEnabled(context: Context): Boolean =
    runCatching {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothManager?.adapter?.isEnabled == true
    }.getOrDefault(true)

private fun voltageToPotchBatteryPercent(voltage: Double): Int =
    (((voltage - 3.2) / (4.2 - 3.2)) * 100.0).roundToInt().coerceIn(0, 100)
