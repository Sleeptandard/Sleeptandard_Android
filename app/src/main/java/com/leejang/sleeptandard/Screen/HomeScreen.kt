package com.leejang.sleeptandard.Screen

import android.annotation.SuppressLint
import android.graphics.BlurMaskFilter
import android.media.RingtoneManager
import android.util.Log

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.painterResource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomSheetDefaults

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.sp
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
import com.leejang.sleeptandard.Component.DiamondStepSlider
import com.leejang.sleeptandard.Component.OptionsSection
import com.leejang.sleeptandard.Component.WakeUpWindow
import com.leejang.sleeptandard.Component.WindowTutorial
import com.leejang.sleeptandard.Component.calculateWakeUpRangeText
import com.leejang.sleeptandard.Component.neumorphicBackground
import com.leejang.sleeptandard.Permission.isAllEssentialPermissionsGranted
import com.leejang.sleeptandard.Permission.openAppSettings
import com.leejang.sleeptandard.Prefs.AlarmPreferences
import com.leejang.sleeptandard.ViewModel.AlarmViewModel
import com.leejang.sleeptandard.ui.theme.AppIcons
import com.leejang.sleeptandard.Prefs.CustomSituationItem
import com.leejang.sleeptandard.Prefs.CustomSituationPreferences
import com.leejang.sleeptandard.ui.theme.DarkBackground
import com.leejang.sleeptandard.utility.getIsNotificationVibrationOn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    alarmViewModel: AlarmViewModel,
    scheduler: AlarmScheduler,
    onClickConfirm: ()-> Unit,
    goExperimentScreen: ()-> Unit = {},
    showWindowTutorial: Boolean,
    onDismissTutorial: (Boolean) -> Unit, // ✅ Boolean 인자 추가
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope() // 코루틴 스코프 선언
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

    // 옵션 컴포넌트에 띄울 알람음 이름
    var alarmName by remember { mutableStateOf("") }

    // 타임피커 멈춤 트리거
    var stopSignal by remember { mutableIntStateOf(0) }

    /****** 메모장 관련 녀석들 ******/
    var showSituationModal by remember { mutableStateOf(false) }     // 메모 모달창 띄우는 트리거
    var selectedSituation by remember { mutableStateOf(setOf<String>()) }    // 메모 모달창에서 선택한 상태(여러 개 토글 가능)

    // 메모장에 들어가는 상태 데이터 클래스 정의
    data class SituationOption(
        val id: String,
        val label: String,
    )
    // 기본 상태
    var situationOptions = listOf(
        SituationOption("drink", "술 한 잔 했어요"),
        SituationOption("tired", "활동량이 많았어요 (운동/업무 등)"),
        SituationOption("sick", "몸이 좋지 않아요"),
    )

    // "직접추가" 모드 트리거
    var isCustomMode by remember { mutableStateOf(false) }

    // "직접추가"에서 입력한 텍스트
    var customText by remember { mutableStateOf("") }

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
        ){
            Spacer(
                modifier = Modifier.height(10.dp)
            )
            Spacer(
                modifier = Modifier.weight(5f)
            )
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
                
                WakeUpWindow(
                    modifier = Modifier
                        .fillMaxWidth(),
                    onValueChange = { earlyWakeUpMinutes = it },
                    selectedHour = selectedHour,
                    selectedMinute = selectedMinute,
                    selectedIsAm = selectedIsAm,
                    earlyWakeUpMinutes = earlyWakeUpMinutes
                )
                
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
                                    android.content.Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)
                                context.startActivity(intent)

                                // (선택 사항) 사용자에게 안내 메시지 표시
                                android.widget.Toast.makeText(
                                    context,
                                    "알림 진동 세기를 조절해주세요.",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: Exception) {
                                // 드문 경우지만 진동 설정창에 직접 접근이 안 될 때 일반 설정창으로 보냄
                                val intent =
                                    android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                                context.startActivity(intent)
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
                    onClick = { showSituationModal = true }
                )

                Spacer(Modifier.height(25.dp))
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
                        isCustomMode = false  // 다음번 열 때 리스트가 보이도록 리셋
                        customText = ""       // 입력하던 텍스트도 비워줌
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
                            // ViewModel에 저장 (그리고 prefs 저장)
                            /** 실험중 **/

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


                            /*
                                alarmViewModel.editUriString(
                                    ringtoneUri = uriStr
                                )

                                 */
                        },
                        defaultVolume = alarmViewModel.alarm.volume,
                    )
                }
            }

            /*** 상황 설정 모달 ***/
            if (showSituationModal) {

                val sheetState = rememberModalBottomSheetState(
                    skipPartiallyExpanded = true
                )

                val allOptions = situationOptions


                ModalBottomSheet(
                    onDismissRequest = { showSituationModal = false },
                    sheetState = sheetState,
                    containerColor = Color(0xFF050C16),
                    // 밖 영역은 어두워지고 클릭 막힘(scrim)
                    scrimColor = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    dragHandle = { BottomSheetDefaults.DragHandle(width = 126.dp) }
                ) {
                    // 내용
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        Text(
                            text = "수면에 영향을 줄 상황이 있었나요?",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White,
                                fontSize = 20.sp
                            )
                        )

                        // 커스텀 메모 모드인지 아닌지에 따른 UI 분기
                        if (!isCustomMode) {

                            Spacer(Modifier.height(8.dp))

                            Text(
                                text = "해당되는 항목을 모두 선택하세요",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 14.sp
                                )
                            )


                            Spacer(Modifier.height(36.dp))

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(items = situationOptions) { situation ->

                                    // ✅ 별도의 isSelected 상태를 만들지 않고, 전체 세트에 포함되어 있는지 직접 확인합니다.
                                    val isSelected =
                                        selectedSituation.contains(situation.id)
                                    val backgroundColor =
                                        if (isSelected) Color(0xFFAFF4F9) else Color(
                                            0xFFF1F4F9
                                        )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .background(
                                                color = backgroundColor,
                                                shape = RoundedCornerShape(size = 20.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(size = 20.dp))
                                                .clickable(
                                                    onClick = {
                                                        if (isSelected) {
                                                            selectedSituation =
                                                                selectedSituation - situation.id
                                                        } else {
                                                            selectedSituation =
                                                                selectedSituation + situation.id
                                                        }
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = situation.label,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    color = Color.Black,
                                                    fontSize = 16.sp
                                                )
                                            )
                                        }

                                    }

                                }


                            }

                            Spacer(Modifier.height(36.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .border(
                                        width = 2.dp,
                                        color = Color.White,
                                        shape = RoundedCornerShape(size = 20.dp)
                                    )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(size = 20.dp))
                                        .clickable(
                                            onClick = {
                                                isCustomMode = true
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(AppIcons.HomeInputPencil),
                                            contentDescription = "직접 입력 아이콘"
                                        )

                                        Text(
                                            text = "직접 입력하기",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = Color.White,
                                                fontSize = 16.sp
                                            )
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(36.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                /** 건너뛰기 **/
                                Button(
                                    onClick = {

                                        if (!isAllEssentialPermissionsGranted(context)) {

                                            android.widget.Toast.makeText(
                                                context,
                                                "권한 설정이 필요합니다. 잠시 후 설정 화면으로 이동합니다.",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()

                                            // 코루틴을 사용하여 지연 실행
                                            scope.launch {
                                                delay(1500L) // 1.5초 지연 (사용자가 토스트를 읽을 시간)
                                                openAppSettings(context)
                                            }
                                            // 권한이 없으므로 알람 등록을 진행하지 않고 종료
                                            return@Button
                                        }
                                        showSituationModal = false

                                        // 알람정보 뷰모델로 저장하고 스케쥴러에 등록하고 다음 화면으로
                                        alarmViewModel.saveAlarm(
                                            selectedHour,
                                            selectedMinute,
                                            selectedIsAm,
                                            selectedRingtoneUri,
                                            selectedVibrationEnabled,
                                            selectedVolume,
                                            earlyWakeUpMinutes = earlyWakeUpMinutes,
                                            isRem = isRem,
                                        )
                                        scheduler.schedule(alarmViewModel.alarm)

                                        val triggerTime = scheduler.getTriggerTime()

                                        // [추가] 선택된 상황을 라벨 문자열로 변환
                                        val situationLabel =
                                            selectedSituation.mapNotNull { id ->
                                                allOptions.find { it.id == id }?.label
                                            }.joinToString("_").ifEmpty { "normal" }

                                        // 2. 워치 깨우기 (전선 연결! + 상황 라벨 전달)
                                        alarmViewModel.startSleepTracking(
                                            triggerTime,
                                            situationLabel
                                        )
                                        // 3. 눈으로 확인하기 위한 토스트 메시지 (추가)
                                        android.widget.Toast.makeText(
                                            context,
                                            "워치 연결 시도 중...",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()

                                        // 여기서 알람 정보를 디스크에 저장
                                        alarmPrefs.saveAlarm(alarmViewModel.alarm)

                                        onClickConfirm()


                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                        .border(
                                            width = 1.dp,
                                            color = Color(0xFF2A2D32),
                                            shape = RoundedCornerShape(size = 100.dp)
                                        ),
                                    shape = RoundedCornerShape(100.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color(0xFF111111),
                                        disabledContainerColor = Color.White.copy(alpha = 0.5f),
                                        disabledContentColor = Color(0xFF111111)
                                    ),
                                    // 선택한 아이템이 있으면 비활성화.
                                    enabled = selectedSituation.isEmpty()
                                ) {
                                    Text(
                                        text = "없어요",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = 18.sp,
                                            color = Color.Black
                                        )
                                    )
                                }

                                /*** 상황선택 확인 ***/
                                Button(
                                    onClick = {
                                        showSituationModal = false

                                        if (!isAllEssentialPermissionsGranted(context)) {

                                            android.widget.Toast.makeText(
                                                context,
                                                "권한 설정이 필요합니다. 잠시 후 설정 화면으로 이동합니다.",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()

                                            // 코루틴을 사용하여 지연 실행
                                            scope.launch {
                                                delay(1500L) // 1.5초 지연 (사용자가 토스트를 읽을 시간)
                                                openAppSettings(context)
                                            }

                                            // 권한이 없으므로 알람 등록을 진행하지 않고 종료
                                            return@Button
                                        }

                                        // 알람정보 뷰모델로 저장하고 스케쥴러에 등록하고 다음 화면으로

                                        alarmViewModel.saveAlarm(
                                            selectedHour,
                                            selectedMinute,
                                            selectedIsAm,
                                            selectedRingtoneUri,
                                            selectedVibrationEnabled,
                                            selectedVolume,
                                            earlyWakeUpMinutes = earlyWakeUpMinutes,
                                            isRem = isRem,
                                        )
                                        scheduler.schedule(alarmViewModel.alarm)

                                        val triggerTime = scheduler.getTriggerTime()

                                        // [추가] 선택된 상황을 라벨 문자열로 변환
                                        val situationLabel =
                                            selectedSituation.mapNotNull { id ->
                                                allOptions.find { it.id == id }?.label
                                            }.joinToString("_").ifEmpty { "normal" }

                                        // 2. 워치 깨우기 (전선 연결! + 상황 라벨 전달)
                                        alarmViewModel.startSleepTracking(
                                            triggerTime,
                                            situationLabel
                                        )
                                        // 3. 눈으로 확인하기 위한 토스트 메시지 (추가)
                                        android.widget.Toast.makeText(
                                            context,
                                            "워치 연결 시도 중...",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()

                                        // 여기서 알람 정보를 디스크에 저장
                                        alarmPrefs.saveAlarm(alarmViewModel.alarm)

                                        onClickConfirm()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp),
                                    shape = RoundedCornerShape(100.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFAFF4F9),
                                        contentColor = Color(0xFF111111),
                                        disabledContainerColor = Color(0x80AFF4F9),
                                        disabledContentColor = Color(0xFF111111)
                                    ),
                                    // 선택한 아이템이 없다면 비활성화.
                                    enabled = selectedSituation.isNotEmpty()
                                ) {
                                    Text(
                                        text = "완료",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = 18.sp,
                                            color = Color.Black
                                        )
                                    )
                                }

                            }
                            Spacer(Modifier.height(30.dp))
                        }

                        // 직접 추가시 모달
                        else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .imePadding()              // ✅ 키보드 올라오면 자동으로 위로 밀림
                                    .navigationBarsPadding()   // ✅ 하단 제스처바/네비바 고려
                            ) {
                                Spacer(Modifier.height(52.dp))

                                OutlinedTextField(
                                    value = customText,
                                    onValueChange = { customText = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(20.dp)),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color.Black,
                                        fontSize = 14.sp
                                    ),
                                    placeholder = {
                                        Text(
                                            "어떤 상황인지 작성해주세요.",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = Color.Black.copy(alpha = 0.5f),
                                                fontSize = 14.sp
                                            )
                                        )
                                    },
                                    singleLine = false,
                                    minLines = 4,
                                    maxLines = 6,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color.White,
                                        cursorColor = Color.Transparent,
                                        focusedTextColor = Color.Black,
                                        unfocusedTextColor = Color.Black
                                    )
                                )

                                Spacer(Modifier.height(52.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                                ) {
                                    Button(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(56.dp),
                                        onClick = {
                                            isCustomMode = false
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.White,
                                            contentColor = Color.Black
                                        )
                                    ) {
                                        Text(
                                            text = "취소",
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = 18.sp,
                                                color = Color.Black
                                            )
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            // ✅ "추가"가 체크되어 있고, 텍스트가 비어있지 않으면 그리드 아이템으로 추가
                                            val trimmed = customText.trim()

                                            // 필수권한 확인
                                            if (!isAllEssentialPermissionsGranted(context)) {

                                                android.widget.Toast.makeText(
                                                    context,
                                                    "권한 설정이 필요합니다. 잠시 후 설정 화면으로 이동합니다.",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()

                                                // 코루틴을 사용하여 지연 실행
                                                scope.launch {
                                                    delay(1500L) // 1.5초 지연 (사용자가 토스트를 읽을 시간)
                                                    openAppSettings(context)
                                                }

                                                // 권한이 없으므로 알람 등록을 진행하지 않고 종료
                                                return@Button
                                            }


                                            // 알람정보 뷰모델로 저장하고 스케쥴러에 등록하고 다음 화면으로

                                            alarmViewModel.saveAlarm(
                                                selectedHour,
                                                selectedMinute,
                                                selectedIsAm,
                                                selectedRingtoneUri,
                                                selectedVibrationEnabled,
                                                selectedVolume,
                                                earlyWakeUpMinutes = earlyWakeUpMinutes,
                                                isRem = isRem,
                                            )
                                            scheduler.schedule(alarmViewModel.alarm)

                                            val triggerTime = scheduler.getTriggerTime()

                                            // [추가] 선택된 상황을 라벨 문자열로 변환
                                            val situationLabel =
                                                selectedSituation.mapNotNull { id ->
                                                    allOptions.find { it.id == id }?.label
                                                }.joinToString("_").ifEmpty { "normal" }

                                            // 2. 워치 깨우기 (전선 연결! + 상황 라벨 전달)
                                            alarmViewModel.startSleepTracking(
                                                triggerTime,
                                                situationLabel
                                            )
                                            // 3. 눈으로 확인하기 위한 토스트 메시지 (추가)
                                            android.widget.Toast.makeText(
                                                context,
                                                "워치 연결 시도 중...",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()

                                            // 여기서 알람 정보를 디스크에 저장
                                            alarmPrefs.saveAlarm(alarmViewModel.alarm)

                                            onClickConfirm()

                                            // 모드 종료
                                            isCustomMode = false
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(56.dp),
                                        shape = RoundedCornerShape(100.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFAFF4F9),
                                            disabledContainerColor = Color(0xFFAFF4F9).copy(
                                                alpha = 0.5f
                                            ),
                                            contentColor = Color.Black,
                                            disabledContentColor = Color.Black
                                        ),
                                        enabled = customText.isNotEmpty()
                                    ) {
                                        Text(
                                            text = "완료",
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = 18.sp,
                                                color = Color.Black
                                            )
                                        )
                                    }

                                }
                                Spacer(Modifier.height(30.dp))

                            }
                        }

                    }

                }
            }


        }
    }
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