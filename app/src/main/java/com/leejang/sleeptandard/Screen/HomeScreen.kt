package com.leejang.sleeptandard.Screen

import android.annotation.SuppressLint
import android.graphics.BlurMaskFilter
import android.media.RingtoneManager
import android.util.Log

import androidx.compose.foundation.BorderStroke
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

import androidx.compose.ui.Alignment
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
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
import com.leejang.sleeptandard.Component.calculateWakeUpRangeText
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
    goExperimentScreen: ()-> Unit
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
        val iconRes: Int?
    )
    // 기본 상태
    var situationOptions = listOf(
        SituationOption("custom", "직접 추가", AppIcons.MemoPencil),
        SituationOption("sick", "아픔", AppIcons.MemoAid),
        SituationOption("drink", "과음", AppIcons.MemoDrink),
        SituationOption("nap", "낮잠", AppIcons.MemoNap),
        SituationOption("eat", "과식", AppIcons.MemoHamburger),
        SituationOption("pill", "수면제", AppIcons.MemoPill),
    )

    // "직접추가" 모드 트리거
    var isCustomMode by remember { mutableStateOf(false) }

    // "직접추가"에서 입력한 텍스트
    // TODO: 하나만 선택되게 해야하나?
    var customText by remember { mutableStateOf("") }

    // "직접추가"에서 체크박스 상태
    var customChecked by remember { mutableStateOf(false) }

    // 커스텀으로 추가된 옵션들
    var customOptions by remember { mutableStateOf(listOf<SituationOption>()) }
    val customIdSet = remember(customOptions) { customOptions.map { it.id }.toSet() }   // id만 따로 모아놓음

    // 편집 모드 트리거
    var isEditMode by remember { mutableStateOf(false) }

    // 삭제 대상으로 체크한 커스텀 옵션 id들
    var selectedCustomForDelete by remember { mutableStateOf(setOf<String>()) }

    // "직접추가"한 상황들을 담고 있는 Prefs
    val customSituationPrefs = remember { CustomSituationPreferences(context) }

    // 커스텀으로 추가한 아이템이 있는지 여부
    val hasCustom = customOptions.isNotEmpty()
    // 모달창에서 추가한 아이템이 있다면 4행을 보여주고 없다면 3행을 보여줌
    val visibleRows = if (hasCustom) 4 else 3
    // 모달창 lazycolumn 크기 수치
    val itemHeight = 88.dp
    val spacing = 12.dp
    val gridHeight = itemHeight * visibleRows + spacing * (visibleRows - 1)

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

    // CustomSituationPrefs 불러오기
    LaunchedEffect(Unit) {
        val loaded = customSituationPrefs.load()
        customOptions = loaded.map {
            SituationOption(
                id = it.id,
                label = it.label,
                iconRes = null
            )
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
        Spacer(
            modifier = Modifier.weight(74f)
        )
        Box(
            modifier = Modifier
                .size(336.dp, 273.dp)
                .drawBehind {
                    // 흰색 그림자
                    val highlightColor1 = Color(0xFFB9C8DF).copy(alpha = 0.15f)
                    val blurRadius1 = 20.dp.toPx()
                    val offsetX1 = (-5).dp.toPx()
                    val offsetY1 = (-5).dp.toPx()

                    drawIntoCanvas { canvas ->
                        val paint = Paint().asFrameworkPaint().apply {
                            color = highlightColor1.toArgb()
                            maskFilter = BlurMaskFilter(blurRadius1, BlurMaskFilter.Blur.NORMAL)
                        }

                        canvas.nativeCanvas.drawRoundRect(
                            offsetX1, offsetY1,
                            size.width + offsetX1, size.height + offsetY1,
                            30.dp.toPx(), 30.dp.toPx(),
                            paint
                        )
                    }

                    // 검은색 그림자
                    val highlightColor2 = Color(0xFF020710).copy(alpha = 0.9f)
                    val blurRadius2 = 15.dp.toPx()
                    val offsetX2 = (8).dp.toPx()
                    val offsetY2 = (8).dp.toPx()

                    drawIntoCanvas { canvas ->
                        val paint = Paint().asFrameworkPaint().apply {
                            color = highlightColor2.toArgb()
                            maskFilter = BlurMaskFilter(blurRadius2, BlurMaskFilter.Blur.NORMAL)
                        }

                        canvas.nativeCanvas.drawRoundRect(
                            offsetX2, offsetY2,
                            size.width + offsetX2, size.height + offsetY2,
                            30.dp.toPx(), 30.dp.toPx(),
                            paint
                        )
                    }

                    val gradient = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF07101E),
                            Color(0xFF101A2A)
                        ),
                        // 시작점을 박스의 정중앙(Center)으로 설정
                        start = Offset(size.width / 2, size.height / 2),
                        // 끝점을 박스의 우측 하단(BottomEnd)으로 설정
                        end = Offset(size.width, size.height * 2 / 3)
                    )
                    drawRoundRect(
                        brush = gradient,
                        cornerRadius = CornerRadius(30.dp.toPx(), 30.dp.toPx()) // 30dp만큼 둥글게
                    )
                }
                // Inner shadow
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

        Spacer(Modifier.weight(15f))

        Box(
            modifier = Modifier
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("최대 30분", color = Color(0xFFAFF4F9), fontSize = 13.sp)
                    Text("최소 10분", color = Color(0xFFAFF4F9), fontSize = 13.sp)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.85f) // ✅ 여기서 슬라이더의 전체 길이를 조절하세요! (0.7 = 70%)
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    DiamondStepSlider(
                        value = earlyWakeUpMinutes,
                        onValueChange = { earlyWakeUpMinutes = it },
                        modifier = Modifier.fillMaxWidth(9f/10f)
                    )
                }
                Text(
                    text = calculateWakeUpRangeText(
                        selectedHour,
                        selectedMinute,
                        selectedIsAm,
                        earlyWakeUpMinutes
                    ),
                    color = Color.White,
                    fontSize = 15.sp
                )

                // Spacer(Modifier.height(15.dp))

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

                Spacer(modifier = Modifier.height(16.dp))

                ConfirmButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    onClick = { showSituationModal = true }
                )


                Button(
                    onClick = goExperimentScreen
                ) {
                    Text(text = "goExperimentScreen")
                }



                /***사운드 선택 모달***/
                if (showSoundSheet) {
                    val soundSheetState =
                        rememberModalBottomSheetState(skipPartiallyExpanded = true)

                    ModalBottomSheet(
                        onDismissRequest = { showSoundSheet = false },
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
                                /*
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

                                 */
                                alarmViewModel.editUriString(
                                    ringtoneUri = uriStr
                                )
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

                    val allOptions = situationOptions + customOptions

                    // 여기 glass 바텀 쓋
                    /*** 상황 설정 모달 ***/
                    if (showSituationModal) {

                        val sheetState = rememberModalBottomSheetState(
                            skipPartiallyExpanded = true
                        )

                        val allOptions = situationOptions + customOptions


                        ModalBottomSheet(
                            onDismissRequest = { showSituationModal = false },
                            sheetState = sheetState,
                            containerColor = Color(0xFF1B2432),
                            // 밖 영역은 어두워지고 클릭 막힘(scrim)
                            scrimColor = Color.Black.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                        ) {
                            // 내용
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .padding(bottom = 20.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "특별한 상황이 있나요?",
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {

                                        // ✅ 커스텀 옵션이 있을 때만 보이게 (+ 직접추가 입력모드 아닐 때만)
                                        if (customOptions.isNotEmpty() && !isCustomMode) {

                                            Text(
                                                text = if (!isEditMode) "편집" else "삭제",
                                                color = if (!isEditMode) Color.White.copy(alpha = 0.7f) else Color(0xFFFF5A5A),
                                                modifier = Modifier
                                                    .padding(end = 8.dp)
                                                    .clickable {
                                                        if (!isEditMode) {
                                                            // 편집 시작
                                                            isEditMode = true
                                                            selectedCustomForDelete = emptySet()
                                                        } else {
                                                            // ✅ 삭제 실행
                                                            if (selectedCustomForDelete.isNotEmpty()) {
                                                                val updated =
                                                                    customOptions.filterNot { it.id in selectedCustomForDelete }

                                                                customOptions = updated
                                                                selectedSituation =
                                                                    selectedSituation - selectedCustomForDelete

                                                                customSituationPrefs.save(
                                                                    updated.map { option ->
                                                                        CustomSituationItem(
                                                                            id = option.id,
                                                                            label = option.label
                                                                        )
                                                                    }
                                                                )
                                                            }

                                                            // 편집 종료 + 원래 선택창으로
                                                            isEditMode = false
                                                            selectedCustomForDelete = emptySet()
                                                        }
                                                    }
                                            )
                                        }

                                        IconButton(onClick = {
                                            // 닫을 때 편집모드도 같이 종료
                                            isEditMode = false
                                            selectedCustomForDelete = emptySet()
                                            isCustomMode = false
                                            showSituationModal = false
                                        }) {
                                            Text("✕", color = Color.White)
                                        }
                                    }
                                }

                                Spacer(Modifier.height(16.dp))

                                // 커스텀 메모 모드인지 아닌지에 따른 UI 분기
                                if (!isCustomMode) {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(2),
                                        modifier = Modifier.height(gridHeight),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(allOptions) { option ->
                                            val isCustom = option.id in customIdSet

                                            // 일반 선택 상태(기존)
                                            val isSelected = selectedSituation.contains(option.id)

                                            // 편집(삭제선택) 상태
                                            val isMarkedForDelete = selectedCustomForDelete.contains(option.id)

                                            // ✅ 편집모드면 커스텀만 클릭 가능
                                            val enabledClick = !isEditMode || isCustom

                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(88.dp)
                                                    .clickable(enabled = enabledClick) {
                                                        when {
                                                            // 편집 모드: 커스텀만 삭제 선택 토글
                                                            isEditMode && isCustom -> {
                                                                selectedCustomForDelete =
                                                                    if (isMarkedForDelete) selectedCustomForDelete - option.id
                                                                    else selectedCustomForDelete + option.id
                                                            }

                                                            // 일반 모드: 직접추가면 입력 모드로
                                                            !isEditMode && option.id == "custom" -> {
                                                                isCustomMode = true
                                                                customText = ""
                                                                customChecked = false
                                                            }

                                                            // 일반 모드: 선택 토글(기본+커스텀 모두 가능)
                                                            else -> {
                                                                selectedSituation =
                                                                    if (isSelected) selectedSituation - option.id
                                                                    else selectedSituation + option.id
                                                            }
                                                        }
                                                    },
                                                shape = RoundedCornerShape(20.dp),

                                                // ✅ 편집모드에서 기본 옵션은 흐리게 보여주기
                                                color = when {
                                                    isEditMode && !isCustom -> Color(0xFF121A26).copy(alpha = 0.35f)
                                                    isEditMode && isCustom -> Color(0xFF121A26)
                                                    // isSelected -> Color(0xFF2D3B52)
                                                    else -> Color(0xFF121A26)
                                                },

                                                // ✅ 테두리: 편집모드에서 삭제 선택되면 빨간 border, 일반 선택은 흰 border(원하면)
                                                border = when {
                                                    isEditMode && isCustom && isMarkedForDelete -> BorderStroke(1.dp, Color(0xFFFF5A5A))
                                                    (!isEditMode && isSelected) -> BorderStroke(1.dp, Color.White) // 네가 원한 선택 테두리
                                                    else -> null
                                                }
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(vertical = 14.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    if(option.iconRes != null){
                                                        Icon(
                                                            painter = painterResource(option.iconRes),
                                                            contentDescription = option.label,
                                                            modifier = Modifier.size(24.dp)
                                                        )

                                                        Spacer(Modifier.height(8.dp))
                                                    }

                                                    Text(
                                                        text = option.label,
                                                        color = Color.White,
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(18.dp))

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ){
                                        Button(
                                            onClick = {

                                                if(!isAllEssentialPermissionsGranted(context)){

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
                                                val situationLabel = selectedSituation.mapNotNull { id ->
                                                    allOptions.find { it.id == id }?.label
                                                }.joinToString("_").ifEmpty { "normal" }

                                                // 2. 워치 깨우기 (전선 연결! + 상황 라벨 전달)
                                                alarmViewModel.startSleepTracking(triggerTime, situationLabel)
                                                // 3. 눈으로 확인하기 위한 토스트 메시지 (추가)
                                                android.widget.Toast.makeText(context, "워치 연결 시도 중...", android.widget.Toast.LENGTH_SHORT).show()

                                                // 여기서 알람 정보를 디스크에 저장
                                                alarmPrefs.saveAlarm(alarmViewModel.alarm)

                                                onClickConfirm()


                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .border(
                                                    width = 1.dp,
                                                    color = Color(0xFF2A2D32),
                                                    shape = RoundedCornerShape(size = 100.dp)
                                                ),
                                            shape = RoundedCornerShape(100.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.onPrimary.copy(
                                                    alpha = 0.05f
                                                ),
                                                contentColor = MaterialTheme.colorScheme.onPrimary.copy(
                                                    alpha = 0.5f
                                                )
                                            )
                                        ) {
                                            Text("건너뛰기")
                                        }
                                        Button(
                                            onClick = {
                                                showSituationModal = false

                                                if(!isAllEssentialPermissionsGranted(context)){

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
                                                val situationLabel = selectedSituation.mapNotNull { id ->
                                                    allOptions.find { it.id == id }?.label
                                                }.joinToString("_").ifEmpty { "normal" }

                                                // 2. 워치 깨우기 (전선 연결! + 상황 라벨 전달)
                                                alarmViewModel.startSleepTracking(triggerTime, situationLabel)
                                                // 3. 눈으로 확인하기 위한 토스트 메시지 (추가)
                                                android.widget.Toast.makeText(context, "워치 연결 시도 중...", android.widget.Toast.LENGTH_SHORT).show()

                                                // 여기서 알람 정보를 디스크에 저장
                                                alarmPrefs.saveAlarm(alarmViewModel.alarm)

                                                onClickConfirm()
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp),
                                            shape = RoundedCornerShape(100.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            // 선택한 아이템이 없다면 비활성화.
                                            enabled = selectedSituation.isNotEmpty()
                                        ) {
                                            Text("완료")
                                        }
                                    }
                                }

                                // 직접 추가시 모달
                                else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .imePadding()              // ✅ 키보드 올라오면 자동으로 위로 밀림
                                            .navigationBarsPadding()   // ✅ 하단 제스처바/네비바 고려
                                    ) {
                                        Spacer(Modifier.height(10.dp))

                                        OutlinedTextField(
                                            value = customText,
                                            onValueChange = { customText = it },
                                            modifier = Modifier
                                                .fillMaxWidth(),
                                            placeholder = { Text("어떤 상황인지 작성해주세요.", color = Color.White.copy(alpha = 0.35f)) },
                                            singleLine = false,
                                            minLines = 4,
                                            maxLines = 6
                                        )

                                        Spacer(Modifier.height(14.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Checkbox(
                                                checked = customChecked,
                                                onCheckedChange = { customChecked = it },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = Color.White,
                                                    uncheckedColor = Color.White.copy(alpha = 0.6f),
                                                    checkmarkColor = Color(0xFF050C16)
                                                )
                                            )
                                            Text("추가", color = Color.White.copy(alpha = 0.85f))
                                        }

                                        Spacer(Modifier.height(12.dp))

                                        Button(
                                            onClick = {
                                                // ✅ "추가"가 체크되어 있고, 텍스트가 비어있지 않으면 그리드 아이템으로 추가
                                                val trimmed = customText.trim()
                                                if (customChecked && trimmed.isNotEmpty()) {
                                                    val saved = customSituationPrefs.add(trimmed) // ✅ prefs에 저장 + 새 item 반환

                                                    customOptions = customOptions + SituationOption(
                                                        id = saved.id,
                                                        label = saved.label,
                                                        iconRes = null
                                                    )
                                                    selectedSituation = selectedSituation + saved.id

                                                }

                                                if(!isAllEssentialPermissionsGranted(context)){

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


                                                if(!customChecked){
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
                                                    val situationLabel = selectedSituation.mapNotNull { id ->
                                                        allOptions.find { it.id == id }?.label
                                                    }.joinToString("_").ifEmpty { "normal" }

                                                    // 2. 워치 깨우기 (전선 연결! + 상황 라벨 전달)
                                                    alarmViewModel.startSleepTracking(triggerTime, situationLabel)
                                                    // 3. 눈으로 확인하기 위한 토스트 메시지 (추가)
                                                    android.widget.Toast.makeText(context, "워치 연결 시도 중...", android.widget.Toast.LENGTH_SHORT).show()

                                                    // 여기서 알람 정보를 디스크에 저장
                                                    alarmPrefs.saveAlarm(alarmViewModel.alarm)

                                                    onClickConfirm()
                                                }
                                                // 모드 종료
                                                isCustomMode = false
                                                customChecked = false


                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp),
                                            shape = RoundedCornerShape(100.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        ) {
                                            Text("완료")
                                        }
                                    }
                                }
                            }

                        }
                    }
                //여기 glass 바텀 쓋
                }

            }
        }
        Spacer(Modifier.weight(32f))
    }
}


/* 여기 glass 바텀 쓋

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        GlassBottomSheet(
                            onDismissRequest = { showSituationModal = false },
                            sheetState = sheetState,
                        ) {
                            // 내용
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .padding(bottom = 20.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "특별한 상황이 있나요?",
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontSize = 20.sp
                                        )
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {

                                        // ✅ 커스텀 옵션이 있을 때만 보이게 (+ 직접추가 입력모드 아닐 때만)
                                        if (customOptions.isNotEmpty() && !isCustomMode) {

                                            Text(
                                                text = if (!isEditMode) "편집" else "삭제",
                                                color = if (!isEditMode) Color.White.copy(alpha = 0.7f) else Color(
                                                    0xFFFF5A5A
                                                ),
                                                modifier = Modifier
                                                    .padding(end = 8.dp)
                                                    .clickable {
                                                        if (!isEditMode) {
                                                            // 편집 시작
                                                            isEditMode = true
                                                            selectedCustomForDelete = emptySet()
                                                        } else {
                                                            // ✅ 삭제 실행
                                                            if (selectedCustomForDelete.isNotEmpty()) {
                                                                val updated =
                                                                    customOptions.filterNot { it.id in selectedCustomForDelete }

                                                                customOptions = updated
                                                                selectedSituation =
                                                                    selectedSituation - selectedCustomForDelete

                                                                customSituationPrefs.save(
                                                                    updated.map { option ->
                                                                        CustomSituationItem(
                                                                            id = option.id,
                                                                            label = option.label
                                                                        )
                                                                    }
                                                                )
                                                            }

                                                            // 편집 종료 + 원래 선택창으로
                                                            isEditMode = false
                                                            selectedCustomForDelete = emptySet()
                                                        }
                                                    }
                                            )
                                            IconButton(onClick = {
                                                // 닫을 때 편집모드도 같이 종료
                                                isEditMode = false
                                                selectedCustomForDelete = emptySet()
                                                isCustomMode = false
                                                showSituationModal = false
                                            }) {
                                                Text("✕", color = Color.White)
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(16.dp))

                                // 커스텀 메모 모드인지 아닌지에 따른 UI 분기
                                if (!isCustomMode) {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(2),
                                        modifier = Modifier.height(gridHeight),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(allOptions) { option ->
                                            val isCustom = option.id in customIdSet

                                            // 일반 선택 상태(기존)
                                            val isSelected = selectedSituation.contains(option.id)

                                            // 편집(삭제선택) 상태
                                            val isMarkedForDelete =
                                                selectedCustomForDelete.contains(option.id)

                                            // ✅ 편집모드면 커스텀만 클릭 가능
                                            val enabledClick = !isEditMode || isCustom

                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(88.dp)
                                                    .clickable(enabled = enabledClick) {
                                                        when {
                                                            // 편집 모드: 커스텀만 삭제 선택 토글
                                                            isEditMode && isCustom -> {
                                                                selectedCustomForDelete =
                                                                    if (isMarkedForDelete) selectedCustomForDelete - option.id
                                                                    else selectedCustomForDelete + option.id
                                                            }

                                                            // 일반 모드: 직접추가면 입력 모드로
                                                            !isEditMode && option.id == "custom" -> {
                                                                isCustomMode = true
                                                                customText = ""
                                                                customChecked = false
                                                            }

                                                            // 일반 모드: 선택 토글(기본+커스텀 모두 가능)
                                                            else -> {
                                                                selectedSituation =
                                                                    if (isSelected) selectedSituation - option.id
                                                                    else selectedSituation + option.id
                                                            }
                                                        }
                                                    },
                                                shape = RoundedCornerShape(20.dp),

                                                // ✅ 편집모드에서 기본 옵션은 흐리게 보여주기
                                                color = when {
                                                    isEditMode && !isCustom -> Color(0xFF121A26).copy(
                                                        alpha = 0.35f
                                                    )

                                                    isEditMode && isCustom -> Color(0xFF121A26)
                                                    isSelected -> Color(0xFFAFF4F9)
                                                    else -> Color(0xFFF1F4F9)
                                                },

                                                // ✅ 테두리: 편집모드에서 삭제 선택되면 빨간 border, 일반 선택은 흰 border(원하면)
                                                border = when {
                                                    isEditMode && isCustom && isMarkedForDelete -> BorderStroke(
                                                        1.dp,
                                                        Color(0xFFFF5A5A)
                                                    )

                                                    (!isEditMode && isSelected) -> BorderStroke(
                                                        1.dp,
                                                        Color.White
                                                    ) // 네가 원한 선택 테두리
                                                    else -> null
                                                }
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxSize(),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    if (option.iconRes != null) {
                                                        Image(
                                                            painter = painterResource(id = option.iconRes),
                                                            contentDescription = option.label,
                                                            modifier = Modifier.size(48.dp),
                                                        )

                                                        //Spacer(Modifier.height(4.dp))
                                                    }

                                                    Text(
                                                        text = option.label,
                                                        color = Color(0xFF050C16),
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            color = Color(0xFF050C16),
                                                            fontSize = 14.sp
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(18.dp))

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
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
                                                    selectedVolume
                                                )
                                                // scheduler.schedule(alarmViewModel.alarm)

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
                                                .height(48.dp)
                                                .border(
                                                    width = 1.dp,
                                                    color = Color(0xFF2A2D32),
                                                    shape = RoundedCornerShape(size = 100.dp)
                                                ),
                                            shape = RoundedCornerShape(100.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.onPrimary.copy(
                                                    alpha = 0.05f
                                                ),
                                                contentColor = MaterialTheme.colorScheme.onPrimary.copy(
                                                    alpha = 0.5f
                                                )
                                            )
                                        ) {
                                            Text("건너뛰기")
                                        }
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
                                                    selectedVolume
                                                )
                                                // scheduler.schedule(alarmViewModel.alarm)

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
                                                .height(48.dp),
                                            shape = RoundedCornerShape(100.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            // 선택한 아이템이 없다면 비활성화.
                                            enabled = selectedSituation.isNotEmpty()
                                        ) {
                                            Text("완료")
                                        }
                                    }
                                }

                                // 직접 추가시 모달
                                else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .imePadding()              // ✅ 키보드 올라오면 자동으로 위로 밀림
                                            .navigationBarsPadding()   // ✅ 하단 제스처바/네비바 고려
                                    ) {
                                        Spacer(Modifier.height(10.dp))

                                        OutlinedTextField(
                                            value = customText,
                                            onValueChange = { customText = it },
                                            modifier = Modifier
                                                .fillMaxWidth(),
                                            placeholder = {
                                                Text(
                                                    "어떤 상황인지 작성해주세요.",
                                                    color = Color.White.copy(alpha = 0.35f)
                                                )
                                            },
                                            singleLine = false,
                                            minLines = 4,
                                            maxLines = 6
                                        )

                                        Spacer(Modifier.height(14.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Checkbox(
                                                checked = customChecked,
                                                onCheckedChange = { customChecked = it },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = Color.White,
                                                    uncheckedColor = Color.White.copy(alpha = 0.6f),
                                                    checkmarkColor = Color(0xFF050C16)
                                                )
                                            )
                                            Text("추가", color = Color.White.copy(alpha = 0.85f))
                                        }

                                        Spacer(Modifier.height(12.dp))

                                        Button(
                                            onClick = {
                                                // ✅ "추가"가 체크되어 있고, 텍스트가 비어있지 않으면 그리드 아이템으로 추가
                                                val trimmed = customText.trim()
                                                if (customChecked && trimmed.isNotEmpty()) {
                                                    val saved =
                                                        customSituationPrefs.add(trimmed) // ✅ prefs에 저장 + 새 item 반환

                                                    customOptions = customOptions + SituationOption(
                                                        id = saved.id,
                                                        label = saved.label,
                                                        iconRes = null
                                                    )
                                                    selectedSituation = selectedSituation + saved.id

                                                }

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


                                                if (!customChecked) {
                                                    // 알람정보 뷰모델로 저장하고 스케쥴러에 등록하고 다음 화면으로

                                                    alarmViewModel.saveAlarm(
                                                        selectedHour,
                                                        selectedMinute,
                                                        selectedIsAm,
                                                        selectedRingtoneUri,
                                                        selectedVibrationEnabled,
                                                        selectedVolume
                                                    )
                                                    // scheduler.schedule(alarmViewModel.alarm)

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
                                                }
                                                // 모드 종료
                                                isCustomMode = false
                                                customChecked = false


                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp),
                                            shape = RoundedCornerShape(100.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        ) {
                                            Text("완료")
                                        }
                                    }
                                }
                            }

                        }
                    }
 */