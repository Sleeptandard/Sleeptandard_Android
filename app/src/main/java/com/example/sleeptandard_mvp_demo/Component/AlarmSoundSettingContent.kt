package com.example.sleeptandard_mvp_demo.Component


import android.content.Context
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.sleeptandard_mvp_demo.ui.theme.AppIcons
import kotlinx.coroutines.delay

private data class SystemTone(
    val title: String,
    val uri: Uri
)

@Composable
fun AlarmSoundSettingContent(
    currentUriString: String,
    onClose: () -> Unit,
    onSelectUriString: (String) -> Unit
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

    // 볼륨 (시스템 ALARM 스트림 볼륨)
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVol = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM) }
    var vol by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_ALARM)) }

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
        r.streamType = AudioManager.STREAM_ALARM
        playingRingtone = r
        try {
            r.play()
        } catch (_: Throwable) {
        }
        previewToken++
    }

    // 2초 자동 정지
    LaunchedEffect(previewToken) {
        if (previewToken == 0) return@LaunchedEffect
        delay(2000)
        stopPreview()
    }

    // 시트 닫힐 때 미리듣기 정지
    DisposableEffect(Unit) {
        onDispose { stopPreview() }
    }

    val card = Color(0x1AF1F1F1)

    val sliderHeight = 95.dp   // 슬라이더 영역 높이(대충)
    val sliderPaddingBottom = 12.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 54.dp),  // ✅ 이거 추가 (핵심)
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 10.dp, start = 16.dp),
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

        // 상단 토글 바
        Surface(
            modifier = Modifier
                .fillMaxWidth(5 / 6f)
                .height(56.dp),
            shape = RoundedCornerShape(100.dp),
            color = card,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (soundEnabled) "소리 ON" else "소리 OFF",
                    color = Color.White
                )
                Switch(
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

        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // ✅ 남은 공간을 전부 차지하게
        ) {


            if (soundEnabled) {
                // ✅ 리스트가 토글 밑으로 쫙 깔리게
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(22.dp),
                    color = card,
                    tonalElevation = 0.dp
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = 10.dp,
                            bottom = sliderHeight + sliderPaddingBottom + 10.dp
                            // ✅ 맨 아래 슬라이더가 덮어쓰는 만큼 "리스트가 가려지지 않게" 패딩 확보
                        )
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


            // ✅ 슬라이더를 맨 밑에 고정 + 리스트 위에 덮어쓰기
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sliderHeight)
                    .align(Alignment.BottomCenter),
                color = Color(0xFF060D17) // 배경 깔고 싶으면 card.copy(alpha=0.9f) 같은걸로
            ) {
                // 슬라이더 Row 그대로(지금 네 코드)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeDown,
                        contentDescription = "볼륨",
                        tint = if (soundEnabled) Color.White else Color(0x66FFFFFF)
                    )
                    Spacer(Modifier.width(12.dp))
                    Slider(
                        value = vol.toFloat(),
                        onValueChange = { v ->
                            val newVol = v.toInt().coerceIn(0, maxVol)
                            vol = newVol
                            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, newVol, 0)
                        },
                        valueRange = 0f..maxVol.toFloat(),
                        enabled = soundEnabled,
                        modifier = Modifier.weight(1f)
                    )
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
    val line = Color(0x1AF1F1F1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = title,
                color = Color.White
            )
        }
        Divider(color = line, thickness = 1.dp)
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