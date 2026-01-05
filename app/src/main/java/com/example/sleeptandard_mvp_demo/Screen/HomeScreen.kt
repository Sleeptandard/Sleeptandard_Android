package com.example.sleeptandard_mvp_demo.Screen

import android.annotation.SuppressLint
import android.app.Activity
import android.media.RingtoneManager
import android.net.Uri
import android.content.Intent

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri

import com.example.sleeptandard_mvp_demo.ClassFile.AlarmScheduler
import com.example.sleeptandard_mvp_demo.Component.ConfirmButton
import com.example.sleeptandard_mvp_demo.Component.CustomTimePicker
import com.example.sleeptandard_mvp_demo.Component.OptionsSection
import com.example.sleeptandard_mvp_demo.Prefs.AlarmPreferences
import com.example.sleeptandard_mvp_demo.ViewModel.AlarmViewModel
import com.example.sleeptandard_mvp_demo.ui.theme.AppIcons

// 메모창
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Checkbox
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.CheckboxDefaults
import com.example.sleeptandard_mvp_demo.Prefs.CustomSituationItem
import com.example.sleeptandard_mvp_demo.Prefs.CustomSituationPreferences

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    alarmViewModel: AlarmViewModel,
    scheduler: AlarmScheduler,
    onSoundClick: ()-> Unit,
    onClickConfirm: ()-> Unit,
) {
    val context = LocalContext.current

    var selectedHour by remember { mutableIntStateOf(8) }
    var selectedMinute by remember { mutableIntStateOf(30) }
    var selectedIsAm by remember { mutableStateOf(true) }
    var selectedRingtoneUri by remember { mutableStateOf("") }
    var selectedVibrationEnabled by remember { mutableStateOf(true) }
    // 알람음 이름
    var alarmName by remember { mutableStateOf("") }
    // 타임피커 멈춤 신호
    var stopSignal by remember { mutableIntStateOf(0) }


    // 모달창 띄우는지 여부
    var showModal by remember { mutableStateOf(false) }
    // ✅ 모달에서 선택한 상태(여러 개 토글 가능)
    var selectedSituation by remember { mutableStateOf(setOf<String>()) }
    // 상태 종류
    data class SituationOption(
        val id: String,
        val label: String,
        val iconRes: Int?
    )

    var situationOptions = listOf(
        SituationOption("custom", "직접 추가", AppIcons.MemoPencil),
        SituationOption("sick", "아픔", AppIcons.MemoAid),
        SituationOption("drink", "과음", AppIcons.MemoDrink),
        SituationOption("nap", "낮잠", AppIcons.MemoNap),
        SituationOption("eat", "과식", AppIcons.MemoHamburger),
        SituationOption("pill", "수면제", AppIcons.MemoPill),
    )

    // "직접추가" 모드인지
    var isCustomMode by remember { mutableStateOf(false) }

    // 입력 텍스트
    var customText by remember { mutableStateOf("") }

    // 체크박스 상태 ("추가")
    var customChecked by remember { mutableStateOf(false) }

    // 커스텀으로 추가된 옵션들
    var customOptions by remember { mutableStateOf(listOf<SituationOption>()) }
    val customIdSet = remember(customOptions) { customOptions.map { it.id }.toSet() }

    // 편집(삭제 선택) 모드
    var isEditMode by remember { mutableStateOf(false) }

    // 삭제 대상으로 체크한 커스텀 옵션 id들
    var selectedCustomForDelete by remember { mutableStateOf(setOf<String>()) }


    val customPrefs = remember { CustomSituationPreferences(context) }

    val hasCustom = customOptions.isNotEmpty()
    val visibleRows = if (hasCustom) 4 else 3

    val itemHeight = 88.dp
    val spacing = 12.dp
    val gridHeight = itemHeight * visibleRows + spacing * (visibleRows - 1)

    LaunchedEffect(Unit) {
        val loaded = customPrefs.load()
        customOptions = loaded.map {
            SituationOption(
                id = it.id,
                label = it.label,
                iconRes = null
            )
        }
    }


    LaunchedEffect(alarmViewModel.alarm.ringtoneUri) {
        val uriStr = alarmViewModel.alarm.ringtoneUri
        if (uriStr.isNotBlank()) {
            val uri = uriStr.toUri()
            val ringtone = RingtoneManager.getRingtone(context, uri)
            alarmName = ringtone?.getTitle(context) ?: "소리 없음"
            selectedRingtoneUri = uriStr
        } else {
            alarmName = "소리 없음"
        }
    }

    // 알림음 설정 화면 Activity의 Result 받았을 때 로직
    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri =
                result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                selectedRingtoneUri = uri.toString()   // state에 저장
                // 표시할 이름 업데이트
                val ringtone = RingtoneManager.getRingtone(context, uri)
                alarmName = ringtone?.getTitle(context) ?: "소리 없음"
            } else {
                // 사용자가 '없음' 선택했거나 취소 케이스 대응
                selectedRingtoneUri = ""
                alarmName = "소리 없음"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(
            modifier = Modifier.height(171.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .height(186.dp)
                .width(255.dp)
        ) {
            CustomTimePicker(
                defaultHour12 = alarmViewModel.alarm.hour,
                defaultMinute = alarmViewModel.alarm.minute,
                defaultIsAm = alarmViewModel.alarm.isAm,
                stopSignal = stopSignal,
                onTimeChange = { hour12, minute, isAm ->
                    selectedHour = hour12
                    selectedMinute = minute
                    selectedIsAm = isAm
                },
            )
        }

        Spacer(Modifier.height(93.dp))

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

                OptionsSection(
                    modifier = Modifier
                        .fillMaxWidth(),

                    // 링톤 설정
                    onSoundClick = {
                        onSoundClick()
                        /* 원래 기본 알람음 설정 창
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .apply {
                                // 추가적으로 설정합니다
                                putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_TYPE,
                                    RingtoneManager.TYPE_ALARM
                                )   // 링톤 타입 = 알람
                                putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_TITLE,
                                    "알람음 선택"
                                )                // 링톤 설정창 제목
                                putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,    // 기존 선택 알람 설정
                                    selectedRingtoneUri.toUri()
                                )
                            }
                        ringtonePickerLauncher.launch(intent)

                         */
                    },

                    // 진동 토글
                    onVibrationClick = { selectedVibrationEnabled = !selectedVibrationEnabled },
                    checked = selectedVibrationEnabled,
                    onCheckedChange = { selectedVibrationEnabled = it },
                    alarmName = alarmName
                )

                Spacer(modifier = Modifier.height(24.dp))

                ConfirmButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    onClick = { showModal = true }
                )

                /*** 바텀 모달 ***/
                if (showModal) {

                    val sheetState = rememberModalBottomSheetState(
                        skipPartiallyExpanded = true
                    )

                    val allOptions = situationOptions + customOptions


                    ModalBottomSheet(
                        onDismissRequest = { showModal = false },
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
                                                            val updated = customOptions.filterNot { it.id in selectedCustomForDelete }

                                                            customOptions = updated
                                                            selectedSituation = selectedSituation - selectedCustomForDelete

                                                            customPrefs.save(
                                                                updated.map { option ->
                                                                    CustomSituationItem(id = option.id, label = option.label)
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
                                        showModal = false
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
                                            showModal = false

                                            // 알람정보 뷰모델로 저장하고 스케쥴러에 등록하고 다음 화면으로
                                            alarmViewModel.saveAlarm(
                                                selectedHour,
                                                selectedMinute,
                                                selectedIsAm,
                                                selectedRingtoneUri,
                                                selectedVibrationEnabled
                                            )
                                            scheduler.schedule(alarmViewModel.alarm)

                                            val triggerTime = scheduler.getTriggerTime()

                                            // 알람뷰모델에 triggerTime 보내기
                                            alarmViewModel.startSleepTracking(triggerTime)

                                            // 여기서 알람 정보를 디스크에 저장
                                            val alarmPrefs = AlarmPreferences(context)
                                            alarmPrefs.saveAlarm(alarmViewModel.alarm)

                                            onClickConfirm()
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .border(width = 1.dp, color = Color(0xFF2A2D32), shape = RoundedCornerShape(size = 100.dp)),
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
                                            showModal = false

                                            // 알람정보 뷰모델로 저장하고 스케쥴러에 등록하고 다음 화면으로

                                            alarmViewModel.saveAlarm(
                                                selectedHour,
                                                selectedMinute,
                                                selectedIsAm,
                                                selectedRingtoneUri,
                                                selectedVibrationEnabled
                                            )
                                            scheduler.schedule(alarmViewModel.alarm)

                                            val triggerTime = scheduler.getTriggerTime()

                                            // 알람뷰모델에 triggerTime 보내기
                                            alarmViewModel.startSleepTracking(triggerTime)

                                            // 여기서 알람 정보를 디스크에 저장
                                            val alarmPrefs = AlarmPreferences(context)
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
                                        )
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
                                                val saved = customPrefs.add(trimmed) // ✅ prefs에 저장 + 새 item 반환

                                                customOptions = customOptions + SituationOption(
                                                    id = saved.id,
                                                    label = saved.label,
                                                    iconRes = null
                                                )
                                                selectedSituation = selectedSituation + saved.id
                                            }
                                            // 모드 종료 -> 원래 그리드로 복귀
                                            isCustomMode = false

                                            // 알람정보 뷰모델로 저장하고 스케쥴러에 등록하고 다음 화면으로

                                            alarmViewModel.saveAlarm(
                                                selectedHour,
                                                selectedMinute,
                                                selectedIsAm,
                                                selectedRingtoneUri,
                                                selectedVibrationEnabled
                                            )
                                            scheduler.schedule(alarmViewModel.alarm)

                                            val triggerTime = scheduler.getTriggerTime()

                                            // 알람뷰모델에 triggerTime 보내기
                                            alarmViewModel.startSleepTracking(triggerTime)

                                            // 여기서 알람 정보를 디스크에 저장
                                            val alarmPrefs = AlarmPreferences(context)
                                            alarmPrefs.saveAlarm(alarmViewModel.alarm)

                                            onClickConfirm()
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
            }
        }
    }
}
/* 잠깐 빼놓을게요 (확인버튼 액션)
onClickConfirm()
alarmViewModel.saveAlarm(
selectedHour,
selectedMinute,
selectedIsAm,
selectedRingtoneUri,
selectedVibrationEnabled
)
scheduler.schedule(alarmViewModel.alarm)

val triggerTime = scheduler.getTriggerTime()

// 알람뷰모델에 triggerTime 보내기
alarmViewModel.startSleepTracking(triggerTime)

// 여기서 알람 정보를 디스크에 저장
val alarmPrefs = AlarmPreferences(context)
alarmPrefs.saveAlarm(alarmViewModel.alarm)

 */


/********************** UI 변경 전 **********************/

/*
Surface(modifier = Modifier
    .fillMaxSize()
    .padding(top = 40.dp)) {
    Column(modifier = Modifier.fillMaxWidth()
        .padding(10.dp)
    )
    {
        TimeAmPmPicker(
            defaultHour12 = alarmViewModel.alarm.hour,
            defaultMinute = alarmViewModel.alarm.minute,
            defaultDay =
                if(alarmViewModel.alarm.isAm)
                    AMPMHours.DayTime.AM
                else AMPMHours.DayTime.PM,
            onTimeChange = {hour12, minute, isAm ->
            selectedHour = hour12
            selectedMinute = minute
            selectedIsAm = isAm
            }
        )
        // 알람음 설정
        // Activity Result 결과 받았을 때 로직
        val ringtonePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                if (uri != null) {
                    selectedRingtoneUri = uri.toString()   // state에 저장
                }
            }
        }

        // 알람음 선택 버튼
        Button(onClick = {
            // 링톤 픽커 열기
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .apply{
                    // 추가적으로 설정합니다
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)   // 링톤 타입 = 알람
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "알람음 선택")                // 링톤 설정창 제목
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,                               // 기존 선택 알람 설정
                        selectedRingtoneUri.toUri()
                    )
                }
            ringtonePickerLauncher.launch(intent)
        }) {
            Text("알람음 선택")
        }

        // 진동 선택
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("진동")
            Switch(
                checked = selectedVibrationEnabled,
                onCheckedChange = { selectedVibrationEnabled = it }
            )
        }

        Button(
            onClick = {
                onClickSetting()
                alarmViewModel.saveAlarm(
                    selectedHour, selectedMinute, selectedIsAm, selectedRingtoneUri, selectedVibrationEnabled)
                scheduler.schedule(alarmViewModel.alarm)

                val triggerTime = scheduler.getTriggerTime()

                // TODO: 알람뷰모델에 triggerTime 보내기
                alarmViewModel.startSleepTracking(triggerTime)

                // 여기서 알람 정보를 디스크에 저장
                val alarmPrefs = AlarmPreferences(context)
                alarmPrefs.saveAlarm(alarmViewModel.alarm)
            } )
        {
            Text("GTS")
        }
    }
}
*/