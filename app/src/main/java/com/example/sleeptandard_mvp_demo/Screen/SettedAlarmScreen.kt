package com.example.sleeptandard_mvp_demo.Screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

import com.example.sleeptandard_mvp_demo.ClassFile.Alarm
import com.example.sleeptandard_mvp_demo.ClassFile.AlarmScheduler
import com.example.sleeptandard_mvp_demo.Component.AlarmBottomNavBar
import com.example.sleeptandard_mvp_demo.Prefs.AlarmPreferences
import com.example.sleeptandard_mvp_demo.ViewModel.AlarmViewModel
import com.example.sleeptandard_mvp_demo.ui.theme.AppIcons
import com.example.sleeptandard_mvp_demo.ui.theme.BlackFont
import com.example.sleeptandard_mvp_demo.ui.theme.LightSurface
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun SettedAlarmScreen(
    alarmViewModel: AlarmViewModel,
    scheduler: AlarmScheduler,
    onTurnAlarmOff : ()-> Unit
) {
    val context = LocalContext.current   // ✨ 추가

    val alarm: Alarm = alarmViewModel.alarm

    var selectedIndex by remember { mutableStateOf(0) }

    // ✅ 타이밍/거리 조절 값 (취향대로 조절)
    val stayMs = 1200L
    val moveMs = 320
    val shift = 27.dp

// 1번이 위로 올라가기 시작하는 타이밍 = 1번이 중앙에 머무는 시간
    val liftStartDelay = stayMs
// 2번 텍스트가 등장하는 타이밍 = 1번 이동이 끝난 다음
    val textStartDelay = stayMs + moveMs.toLong()

    // 그라데이션 배경에 쓸 값들
    val shape = RoundedCornerShape(12.dp)
    val centerGlow = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.Transparent,
            0.5f to Color.White.copy(alpha = 0.06f), // 그라데이션 배경 알파값 조절(0.06~0.14 추천)
            1f to Color.Transparent
        )
    )

    Column(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.weight(5f))
        Spacer(Modifier.height(54.dp))

        LiftUpTwice(
            startDelayMs = liftStartDelay,
            moveMs = moveMs,
            lift = shift,
            betweenDelayMs = stayMs // “한 번 더 딜레이 후”의 딜레이
        ) { liftedModifier ->
            Column(
                modifier = liftedModifier.padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Surface(
                    modifier = Modifier.height(52.dp).fillMaxWidth().clip(shape),
                    shape = shape,
                    color = Color.Transparent,
                    tonalElevation = 1.dp,
                ) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(shape)
                                .background(centerGlow)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ){
                            Text(
                                text = getIsAm(alarm.isAm),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 16.sp
                                )
                            )
                            Text(
                                getWakeUpTimeRange(alarm.hour, alarm.minute),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }

                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "사이에 깨워드립니다",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }


        Spacer(modifier = Modifier.height(34.5.dp))

        // ✅ 2번/3번: 1번이 위로 이동 끝난 다음에 나타나고, 텍스트만 위로 밀리며 3번까지 나오면 멈춤
        DelayedContentReserveSpace(
            delayMs = textStartDelay,
            reservedHeight = 65.dp // ✅ 텍스트 2줄 영역 높이(너 UI에 맞게 조절)
        ){
            StackedRollingText(
                texts = listOf("데이터 센싱 시작", "워치를 착용해주세요"),
                modifier = Modifier.fillMaxWidth(),
                stayMs = stayMs,
                moveMs = moveMs,
                shift = shift,
                maxLines = 2
            )
        }

        Spacer(modifier = Modifier.height(33.dp))

        Icon(
            painterResource(AppIcons.SettedActivityDark),
            contentDescription = "찌릿찌릿"
        )
        /*
        Image(
            modifier = Modifier
                .width(50.dp)
                .height(24.dp),
            painter = painterResource(AppIcons.SettedActivity),
            contentDescription = "찌릿찌릿",
            contentScale = ContentScale.FillBounds
        )
        */

        Spacer(modifier = Modifier.weight(4f))

        Button(
            modifier = Modifier
                .fillMaxWidth(193f / 350f)
                .height(67.dp),
            onClick = {
                onTurnAlarmOff()
                scheduler.cancel(alarmViewModel.alarm)

                // 2) SharedPreferences 플래그/값 삭제
                val alarmPrefs = AlarmPreferences(context)
                alarmPrefs.clearAlarm()

                // 3) 네비게이션 처리
                onTurnAlarmOff()
            }
        ) { Text("알람중지") }

        Spacer(Modifier.height(55.dp))
    }
}

@Composable
private fun LiftUpTwice(
    startDelayMs: Long,
    moveMs: Int,
    lift: Dp,
    betweenDelayMs: Long, // ✅ 첫 번째 올라간 뒤, 두 번째 올라가기 전 대기
    content: @Composable (Modifier) -> Unit
) {
    val anim = remember { Animatable(0f) } // 0 -> 1 -> 2
    val liftPx = with(LocalDensity.current) { lift.toPx() }

    LaunchedEffect(Unit) {
        delay(startDelayMs)

        // 1차 상승 (0 -> 1)
        anim.animateTo(
            1f,
            animationSpec = tween(durationMillis = moveMs, easing = FastOutSlowInEasing)
        )

        // 중간 대기
        delay(betweenDelayMs)

        // 2차 상승 (1 -> 2)
        anim.animateTo(
            2f,
            animationSpec = tween(durationMillis = moveMs, easing = FastOutSlowInEasing)
        )
    }

    val modifier = Modifier.graphicsLayer {
        translationY = -liftPx * anim.value   // ✅ 1이면 1칸, 2면 2칸 상승
    }

    content(modifier)
}

@Composable
private fun DelayedContent(
    delayMs: Long,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        visible = true
    }
    if (visible) content()
}
@Composable
private fun DelayedContentReserveSpace(
    delayMs: Long,
    reservedHeight: Dp,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        visible = true
    }

    Box(Modifier.height(reservedHeight)) { // ✅ 여기서 공간을 항상 확보
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            content()
        }
    }
}

fun getWakeUpTimeRange(hour:Int, minute: Int): String{
    var earlyTotalMinute: Int = (hour * 60 + minute) - 30

    if(earlyTotalMinute < 0) earlyTotalMinute += 12 * 60

    return String.format(
        Locale.getDefault(),
        "%d : %02d ~ %d : %02d",
        earlyTotalMinute/60, earlyTotalMinute%60, hour, minute)
}

fun getIsAm(isAm: Boolean): String{
    if(isAm) return "오전" else return "오후"
}
