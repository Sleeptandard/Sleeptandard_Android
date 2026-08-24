package com.leejang.sleeptandard.Screen

import android.annotation.SuppressLint
import android.content.Intent
import android.media.RingtoneManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.leejang.sleeptandard.ClassFile.AlarmScheduler
import com.leejang.sleeptandard.Component.AlarmSoundSettingContent
import com.leejang.sleeptandard.Component.ConfirmButton
import com.leejang.sleeptandard.Component.CustomTimePicker
import com.leejang.sleeptandard.Component.OptionsSection
import com.leejang.sleeptandard.Component.ShowWakeUpRange
import com.leejang.sleeptandard.Component.SituationContent
import com.leejang.sleeptandard.Component.SituationOption
import com.leejang.sleeptandard.Component.WakeUpWindow
import com.leejang.sleeptandard.Component.WindowTutorial
import com.leejang.sleeptandard.Component.calculateWakeUpRangeText
import com.leejang.sleeptandard.Component.neumorphicBackground
import com.leejang.sleeptandard.Prefs.AlarmPreferences
import com.leejang.sleeptandard.ViewModel.AlarmViewModel
import com.leejang.sleeptandard.ui.theme.DarkBackground
import com.leejang.sleeptandard.utility.getIsNotificationVibrationOn


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
) {
    val context = LocalContext.current
    val alarmPrefs = remember(context) { AlarmPreferences(context) }  // 알람 SharedPreference 가져오기

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

    Column(
        modifier = Modifier
            .fillMaxSize()
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

            ShowWakeUpRange(selectedHour,selectedMinute,selectedIsAm)

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
                    isRem = isRem,
                    onRemCheckedChange = { isRem = it }
                )

                Spacer(modifier = Modifier.weight(2f))

                ConfirmButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    onClick = {
                        // TODO: 팟치 배터리 상태를 확인한 경고창을 띄울지말지 정한 다음 알람 설정 완료 화면으로 navigate
                        /*
                        showSituationModal = true
                         */
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