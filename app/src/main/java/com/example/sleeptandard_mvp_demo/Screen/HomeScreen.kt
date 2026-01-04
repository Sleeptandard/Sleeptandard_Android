package com.example.sleeptandard_mvp_demo.Screen

import android.annotation.SuppressLint
import android.app.Activity
import android.media.RingtoneManager
import android.net.Uri
import android.content.Intent

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri

import com.example.sleeptandard_mvp_demo.ClassFile.AlarmScheduler
import com.example.sleeptandard_mvp_demo.Component.ConfirmButton
import com.example.sleeptandard_mvp_demo.Component.CustomTimePicker
import com.example.sleeptandard_mvp_demo.Component.OptionsSection
import com.example.sleeptandard_mvp_demo.Prefs.AlarmPreferences
import com.example.sleeptandard_mvp_demo.ViewModel.AlarmViewModel

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
    var situationOptions = remember {
        listOf(
            "직접 추가", "아픔", "과음", "낮잠", "과식", "수면제"
        )
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

                                // 닫기(X)
                                IconButton(onClick = { showModal = false }) {
                                    Text("✕", color = Color.White)
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            // 2열 그리드 토글 버튼들
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.heightIn(max = 340.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(situationOptions) { item ->
                                    val isOn = selectedSituation.contains(item)

                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(72.dp)
                                            .clickable {
                                                selectedSituation =
                                                    if (isOn) selectedSituation - item else selectedSituation + item
                                            },
                                        shape = RoundedCornerShape(18.dp),
                                        color = if (isOn) Color(0xFF2D3B52) else Color(0xFF121A26),
                                        tonalElevation = 0.dp,
                                        shadowElevation = 0.dp
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize().padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = item, color = Color.White)
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(18.dp))

                            Button(
                                onClick = {
                                    // 여기서 selected를 ViewModel로 넘기거나 저장하면 됨
                                    showModal = false
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2D3B52),
                                    contentColor = Color.White
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