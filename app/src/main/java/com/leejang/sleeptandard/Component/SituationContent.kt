package com.leejang.sleeptandard.Component

/** 홈 - 시츄에이션 모달에 들어갈 내용들
 *
 * 시발 모르겠다~
 *
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leejang.sleeptandard.ClassFile.AlarmScheduler
import com.leejang.sleeptandard.Permission.isAllEssentialPermissionsGranted
import com.leejang.sleeptandard.Permission.openAppSettings
import com.leejang.sleeptandard.Prefs.AlarmPreferences
import com.leejang.sleeptandard.ViewModel.AlarmViewModel
import com.leejang.sleeptandard.ui.theme.AppIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.text.ifEmpty


data class SituationOption(
    val id: String,
    val label: String,
)

@Composable
fun SituationContent(
    modifier: Modifier = Modifier,

    alarmPrefs: AlarmPreferences,
    alarmViewModel: AlarmViewModel,
    scheduler: AlarmScheduler,

    selectedHour: Int,
    selectedMinute: Int,
    selectedIsAm: Boolean,
    selectedRingtoneUri: String,
    selectedVibrationEnabled: Boolean,
    selectedVolume: Int,
    earlyWakeUpMinutes: Int,
    isRem: Boolean,
    isCustomMode: Boolean,
    situationOptions: List<SituationOption>,
    customText: String,

    onCustomMode: () -> Unit,
    offCustomMode: () -> Unit,
    offSituationModal: () -> Unit,
    onClickConfirm: ()->Unit,
    onCustomTextChange: (String) -> Unit
){
    var selectedSituation by remember { mutableStateOf(setOf<String>()) }    // 메모 모달창에서 선택한 상태(여러 개 토글 가능)
    val context = LocalContext.current
    val scope = rememberCoroutineScope() // 코루틴 스코프 선언
    val allOptions = situationOptions


    // 내용
    Column(
        modifier = modifier
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
                                        selectedSituation = if (isSelected) {
                                            selectedSituation - situation.id
                                        } else {
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
                            onClick = onCustomMode // isCustomMode = true
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
                        offSituationModal() // showSituationModal = false

                        // 알람정보 뷰모델로 저장하고 스케쥴러에 등록하고 다음 화면으로
                        alarmViewModel.saveAlarm(
                            selectedHour,
                            selectedMinute,
                            selectedIsAm,
                            selectedRingtoneUri,
                            selectedVibrationEnabled,
                            selectedVolume,
                            earlyWakeUpMinutes,
                            isRem,
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
                        offSituationModal() // showSituationModal = false

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
                    onValueChange = { onCustomTextChange(it) },
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
                        onClick = offCustomMode,   //isCustomMode = false
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

                            offCustomMode() // 모드 종료 isCustomMode = false
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