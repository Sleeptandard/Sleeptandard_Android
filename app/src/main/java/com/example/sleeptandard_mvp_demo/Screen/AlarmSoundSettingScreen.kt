package com.example.sleeptandard_mvp_demo.Screen

import android.content.Context
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.delay

data class SystemTone(
    val title: String,
    val uri: Uri
)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmSoundSettingScreen(
    initialEnabled: Boolean = true,
    initialSelectedUri: Uri? = null,
    onBack: () -> Unit = {},
    onChangeEnabled: (Boolean) -> Unit = {},
    onSelectTone: (Uri?) -> Unit = {} // null이면 무음
) {
    val context = LocalContext.current

    var soundEnabled by remember { mutableStateOf(initialEnabled) }
    var selectedUri by remember { mutableStateOf(initialSelectedUri) }

    // 시스템 알람음 목록
    val tones by remember {
        mutableStateOf(loadAlarmTones(context))
    }

    // 미리듣기 ringtone 핸들
    var playingRingtone by remember { mutableStateOf<Ringtone?>(null) }

    // 볼륨 (시스템 ALARM 스트림 볼륨을 직접 조절: 스샷처럼 시스템 볼륨 슬라이더 느낌)
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM) }
    var vol by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_ALARM)) }

    // 화면 나갈 때 미리듣기 정지
    DisposableEffect(Unit) {
        onDispose {
            try { playingRingtone?.stop() } catch (_: Throwable) {}
            playingRingtone = null
        }
    }

    fun stopPreview() {
        try { playingRingtone?.stop() } catch (_: Throwable) {}
        playingRingtone = null
    }

    fun playPreview(uri: Uri) {
        stopPreview()
        val r = RingtoneManager.getRingtone(context, uri) ?: return
        // 알람 스트림으로 재생되게
        r.streamType = AudioManager.STREAM_ALARM
        playingRingtone = r
        try { r.play() } catch (_: Throwable) {}

        // 너무 오래 울리면 싫으니까 2초 뒤 자동 정지(원하면 시간 바꿔도 됨)
        // (Ringtone은 직접 volume 제어가 어려워서 시스템 볼륨을 사용)
        /* 컴포저블 함수에서만 사용 가능.
        LaunchedEffect(uri) {
            delay(2000)
            if (playingRingtone == r) stopPreview()
        }

         */
    }

    val bg = Color(0xFF0B111A)
    val card = Color(0x1AF1F1F1)

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = {
                        stopPreview()
                        onBack()
                    }) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 18.dp)
        ) {
            // 상단 토글 바(스샷처럼 둥근 바)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(22.dp),
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
                            onChangeEnabled(enabled)

                            if (!enabled) {
                                // 무음 처리
                                stopPreview()
                                selectedUri = null
                                onSelectTone(null)
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // 알람음 리스트 (ON일 때만 보이기)
            if (soundEnabled) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = card,
                    tonalElevation = 0.dp
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        items(tones) { tone ->
                            ToneRow(
                                title = tone.title,
                                selected = (selectedUri == tone.uri),
                                onClick = {
                                    selectedUri = tone.uri
                                    onSelectTone(tone.uri)
                                    playPreview(tone.uri)
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            } else {
                // OFF면 리스트 자리 비워두고 간격만(원하면 아예 아무것도 안 둬도 됨)
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.weight(1f))

            // 하단 볼륨 슬라이더 (OFF면 비활성)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.VolumeDown,
                    contentDescription = "볼륨",
                    tint = if (soundEnabled) Color.White else Color(0x66FFFFFF)
                )
                Spacer(Modifier.width(12.dp))

                Slider(
                    value = vol.toFloat(),
                    onValueChange = { v ->
                        val newVol = v.toInt().coerceIn(0, maxVol)
                        vol = newVol
                        // 시스템 알람 볼륨 변경 (스샷 느낌)
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

    // 기기에 알람음이 아예 없을 수도 있어서, 그 땐 벨소리라도 대체로 넣고 싶다면:
    // (원하면 추가해줄게)
    return list
}