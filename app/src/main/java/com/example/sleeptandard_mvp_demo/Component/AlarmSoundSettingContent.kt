package com.example.sleeptandard_mvp_demo.Component


import android.content.Context
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.example.sleeptandard_mvp_demo.ClassFile.Alarm
import com.example.sleeptandard_mvp_demo.ui.theme.AppIcons
import com.example.sleeptandard_mvp_demo.ui.theme.DarkBackground
import kotlinx.coroutines.delay

private data class SystemTone(
    val title: String,
    val uri: Uri
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmSoundSettingContent(
    currentUriString: String,
    onClose: () -> Unit,
    onSelectUriString: (String) -> Unit,
    currentAlarm: Alarm, // ✅ 알람 객체를 직접 받거나 volume 값을 받도록 수정
    onVolumeChange: (Int) -> Unit, // ✅ 볼륨 변경 콜백 추가
) {
    val context = LocalContext.current

    // currentUriString → Uri?
    val initialUri = remember(currentUriString) {
        currentUriString.takeIf { it.isNotBlank() }?.toUri()
    }

    var soundEnabled by remember { mutableStateOf(currentUriString.isNotBlank()) }
    var selectedUri by remember { mutableStateOf<Uri?>(initialUri) }

    // 시스템 알람음 목록
    val tones by remember { mutableStateOf(loadAlarmTones(context)) }

    // 미리듣기 ringtone 핸들
    var playingRingtone by remember { mutableStateOf<Ringtone?>(null) }
    var previewToken by remember { mutableIntStateOf(0) }

    val maxVol = 15
    var vol by remember { mutableIntStateOf(currentAlarm?.volume ?: 10) }

    fun stopPreview() {
        try {
            playingRingtone?.stop()
        } catch (_: Throwable) {
        }
        playingRingtone = null
    }

    fun playPreview(uri: Uri) {
        stopPreview()
        val r = RingtoneManager.getRingtone(context, uri) ?: return

        // ✅ API 28 이상에서 미리보기 링톤 볼륨 조절
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            r.volume = vol.toFloat() / 15f
        }

        r.streamType = AudioManager.STREAM_ALARM
        playingRingtone = r
        try {
            r.play()
        } catch (_: Throwable) {
        }
        previewToken++
    }

    /*
    // 2초 자동 정지
    LaunchedEffect(previewToken) {
        if (previewToken == 0) return@LaunchedEffect
        delay(2000)
        stopPreview()
    }

    */

    // 시트 닫힐 때 미리듣기 정지
    DisposableEffect(Unit) {
        onDispose { stopPreview() }
    }

    val card = Color(0x26F1F1F1)

    val sliderHeight = 95.dp   // 슬라이더 영역 높이(대충)
    val sliderPaddingBottom = 12.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(54f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                modifier = Modifier
                    .size(32.dp),
                onClick = {
                    stopPreview()
                    onClose()
                }) {
                Icon(
                    painter = painterResource(AppIcons.QnAArrowBack),
                    contentDescription = "저장하고 뒤로가기"
                )
            }
        }

        Spacer(Modifier.weight(24f))

        // 상단 토글 바
        Surface(
            modifier = Modifier
                .fillMaxWidth(5 / 6f)
                .height(50.dp),
            shape = RoundedCornerShape(100.dp),
            color = card,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 30.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (soundEnabled) "소리 ON" else "소리 OFF",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    modifier = Modifier
                        .scale(37f/52f),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFB1F7FC),
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFF858585),
                    ),
                    checked = soundEnabled,
                    onCheckedChange = { enabled ->
                        soundEnabled = enabled
                        if (!enabled) {
                            stopPreview()
                            selectedUri = null
                            onSelectUriString("") // ✅ 무음 저장
                        } else {
                            // ON으로 켰을 때 기존 선택이 없으면 첫 번째 톤을 기본 선택(원치 않으면 제거 가능)
                            if (selectedUri == null && tones.isNotEmpty()) {
                                selectedUri = tones.first().uri
                                onSelectUriString(tones.first().uri.toString())
                            }
                        }
                    }
                )
            }
        }

        Spacer(Modifier.weight(26f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .weight(620f) // ✅ 남은 공간을 전부 차지하게
        ) {


            if (soundEnabled) {
                // ✅ 리스트가 토글 밑으로 쫙 깔리게
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(40.dp),
                    color = card,
                    tonalElevation = 0.dp
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = 10.dp,
                            bottom = sliderHeight + sliderPaddingBottom + 10.dp
                            // ✅ 맨 아래 슬라이더가 덮어쓰는 만큼 "리스트가 가려지지 않게" 패딩 확보
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(tones) { tone ->
                            ToneRow(
                                title = tone.title,
                                selected = (selectedUri == tone.uri),
                                onClick = {
                                    selectedUri = tone.uri
                                    onSelectUriString(tone.uri.toString())
                                    playPreview(tone.uri)
                                }
                            )
                        }
                    }
                }
            } else {
                // OFF일 땐 리스트 자리는 유지 (필요하면 안내문구)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {

                }
            }


            // ✅ 슬라이더를 맨 밑에 고정
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter), // 바닥에 붙임
                color = Color(0xFF060D17)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // ✅ 2. 내비게이션 바 패딩을 Column에 적용하여 배경색은 바닥까지 채워지게 합니다.
                        .navigationBarsPadding()
                ) {
                    // ✅ 3. 고정 높이 대신 내부 Row의 수직 패딩(vertical)으로 높이를 조절합니다.
                    // 이렇게 하면 기기에 상관없이 슬라이더의 물리적 크기가 일정하게 유지됩니다.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 30.dp, vertical = 28.dp), // 충분한 터치 영역 확보
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(AppIcons.HomeVolume),
                            contentDescription = "볼륨",
                            tint = if (soundEnabled) Color.White else Color(0x66FFFFFF),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))

                        CustomVolumeSlider(
                            value = vol.toFloat(),
                            onValueChange = { v ->
                                val newVol = v.toInt().coerceIn(0, maxVol)
                                vol = newVol
                                onVolumeChange(newVol) // 실제 볼륨 변경 적용
                            },
                            enabled = soundEnabled,
                            valueRange = 0f..maxVol.toFloat(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun ToneRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val line = Color(0xFFD4DCE4)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                modifier = Modifier.scale(1.25f),
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors().copy(
                    selectedColor = Color(0xFFAAEDF2),
                    unselectedColor = Color(0xFFD4DCE4)
                )
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 18.sp
                ),
            )
        }
        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 52.dp, end = 10.dp),
            thickness = 0.6.dp,
            color = line
        )
    }
}

@Composable
fun CustomVolumeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean = true, // ✅ 비활성화 여부 추가
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    activeColor: Color = Color(0xFFB9E0E3),
    inactiveColor: Color = Color(0xFF858585)
) {
    // ✅ 비활성화 시 색상 계산 (투명도 조절)
    val currentActiveColor = if (enabled) activeColor else activeColor.copy(alpha = 0.3f)
    val currentInactiveColor = if (enabled) inactiveColor else inactiveColor.copy(alpha = 0.3f)
    val thumbColor = if (enabled) Color.White else Color(0xFF424242)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .pointerInput(enabled, valueRange) { // ✅ enabled가 바뀔 때마다 재설정
                if (!enabled) return@pointerInput // 비활성화 시 터치 무시
                detectTapGestures { offset ->
                    val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                    onValueChange(valueRange.start + (valueRange.endInclusive - valueRange.start) * ratio)
                }
            }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput // 비활성화 시 드래그 무시
                detectDragGestures { change, _ ->
                    val ratio = (change.position.x / size.width).coerceIn(0f, 1f)
                    onValueChange(valueRange.start + (valueRange.endInclusive - valueRange.start) * ratio)
                }
            }
    ) {
        val width = constraints.maxWidth.toFloat()
        val fraction = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
        val thumbCenterX = width * fraction

        // 1. 트랙 (비활성 배경)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(14.dp)
                .background(currentInactiveColor, RoundedCornerShape(100.dp))
        )

        // 2. 트랙 (활성 - 색칠되는 부분)
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(with(LocalDensity.current) { (thumbCenterX).toDp() })
                .height(14.dp)
                .background(currentActiveColor, RoundedCornerShape(100.dp))
        )

        // 3. Thumb (동그라미)
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbCenterX.toInt() - 13.dp.toPx().toInt(), 0) }
                .align(Alignment.CenterStart)
                .size(26.dp)
                // 비활성화 시 그림자 제거 혹은 축소
                .shadow(if (enabled) 4.dp else 0.dp, CircleShape)
                .background(thumbColor, CircleShape)
        )
    }
}

private fun loadAlarmTones(context: Context): List<SystemTone> {
    val rm = RingtoneManager(context).apply { setType(RingtoneManager.TYPE_ALARM) }
    val cursor = rm.cursor ?: return emptyList()

    val list = mutableListOf<SystemTone>()
    cursor.use {
        while (it.moveToNext()) {
            val title = it.getString(RingtoneManager.TITLE_COLUMN_INDEX)
            val uri = rm.getRingtoneUri(it.position)
            list.add(SystemTone(title = title, uri = uri))
        }
    }
    return list
}