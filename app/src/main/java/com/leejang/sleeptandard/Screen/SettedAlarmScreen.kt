package com.leejang.sleeptandard.Screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leejang.sleeptandard.ClassFile.Alarm

import com.leejang.sleeptandard.ClassFile.AlarmScheduler
import com.leejang.sleeptandard.Component.calculateWakeUpRangeText
import com.leejang.sleeptandard.Component.neumorphicBackground
import com.leejang.sleeptandard.Potch.PotchBleViewModel
import com.leejang.sleeptandard.Prefs.AlarmPreferences
import com.leejang.sleeptandard.ViewModel.AlarmViewModel
import com.leejang.sleeptandard.ui.theme.AppIcons
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettedAlarmScreen(
    alarmViewModel: AlarmViewModel,
    scheduler: AlarmScheduler,
    onTurnAlarmOff : ()-> Unit,
    potchViewModel: PotchBleViewModel = viewModel(),
) {
    val context = LocalContext.current
    val alarm = alarmViewModel.alarm
    val processorState by potchViewModel.processorState.collectAsState()
    val isPotchBatteryLow = processorState.lastParsedData
        ?.batteryVoltage
        ?.takeIf { it.isFinite() }
        ?.let(::voltageToBatteryPercent)
        ?.let { it <= 40 }
        ?: false

    val fontAnimatable = remember { Animatable(48f) }
    val fontSpaceAnimatable = remember { Animatable(8f) }
    val topSpaceAnimatable = remember { Animatable(200f) }
    var popUp by remember { mutableStateOf(false) }

    LaunchedEffect(Unit){
        delay(1000) // 사용자가 화면을 인식할 수 있도록 아주 잠깐 대기합니다.
        popUp = !popUp
        // [왕복 1단계] 30분 -> 10분으로 이동 (2초간 부드럽게)
        fontAnimatable.animateTo(
            targetValue = 40f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
    }
    LaunchedEffect(Unit) {
        delay(1000) // 사용자가 화면을 인식할 수 있도록 아주 잠깐 대기합니다.
        fontSpaceAnimatable.animateTo(
            targetValue = 18f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
    }

    LaunchedEffect(Unit) {
        delay(1000) // 사용자가 화면을 인식할 수 있도록 아주 잠깐 대기합니다.
        topSpaceAnimatable.animateTo(
            targetValue = 90f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        Spacer(Modifier.height(topSpaceAnimatable.value.toInt().dp))

        Column(modifier = Modifier.weight(1f)){
            AnimatedContent(
                targetState = popUp,
                transitionSpec = {fadeIn(animationSpec = tween(400)).togetherWith(fadeOut(animationSpec = tween(400)))},
                label = ""
            ){popUp ->

                if(!popUp){
                    Column(
                        modifier= Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ShowWakeUpTime(
                            alarm = alarm,
                            fontSize = fontAnimatable.value,
                            space = fontSpaceAnimatable.value,
                            showLowBatteryWarning = isPotchBatteryLow
                        )
                        Spacer(Modifier.weight(1f))
                    }
                }else{
                    Column(
                        modifier= Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ShowWakeUpTime(
                            alarm = alarm,
                            fontSize = fontAnimatable.value,
                            space = fontSpaceAnimatable.value,
                            showLowBatteryWarning = isPotchBatteryLow
                        )
                        Spacer(Modifier.weight(45f))
                        SensingStartInfo(
                            heartRateBpm = processorState.heartRateBpm,
                            temperatureCelsius = processorState.lastParsedData
                                ?.ntcCelsius
                                ?.takeIf { it.isFinite() }
                        )
                        Spacer(Modifier.weight(51f))
                        ActivityAnimation(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        )
                        Spacer(Modifier.weight(92f))
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .size(320.dp, 60.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(color = Color.White)
                .clickable {
                    // 1) 알람 스케줄 취소
                    scheduler.cancel(alarmViewModel.alarm)

                    // 2) SharedPreferences 플래그/값 삭제
                    val alarmPrefs = AlarmPreferences(context)
                    alarmPrefs.clearAlarm()

                    // 3) 네비게이션 처리
                    onTurnAlarmOff()
                },
            contentAlignment = Alignment.Center
        ){
            Text(
                text = "알람 중지",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 18.sp,
                    color = Color.Black
                )
            )
        }

        Spacer(Modifier.height(80.dp))

        /*
        Button(
            modifier = Modifier
                .size(320.dp, 56.dp),
            onClick = {
                // 1) 알람 스케줄 취소
                scheduler.cancel(alarmViewModel.alarm)

                // 2) 워치에 수면 추적 중지 명령 전송
                alarmViewModel.stopSleepTracking()

                // 3) SharedPreferences 플래그/값 삭제
                val alarmPrefs = AlarmPreferences(context)
                alarmPrefs.clearAlarm()

                // 4) 네비게이션 처리
                onTurnAlarmOff()
            }
        ){
            Text("알람 중지")
        }

         */
    }
}

@Composable
private fun ShowWakeUpTime(
    alarm: Alarm,
    fontSize: Float,
    space: Float,
    showLowBatteryWarning: Boolean,
){
    Column(
        modifier = Modifier
    ) {
        Text(
            text = earlyWakeUpText(
                alarm.hour,
                alarm.minute,
                alarm.isAm,
                AlarmScheduler.MONITORING_WINDOW_MINUTES
            ),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 16.sp,
                color = Color(0xCCF1F4F9)
            )
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier,
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(space.dp)
        ) {
            Text(
                text = "~",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = fontSize.sp,
                    color = Color(0xCCF1F4F9),
                )
            )
            Text(
                text = String.format(Locale.getDefault(), "%d : %02d", alarm.hour, alarm.minute),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = fontSize.sp,
                    color = Color(0xCCF1F4F9),
                )
            )
            Text(
                text = "사이 알람",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 16.sp,
                    color = Color(0xCCF1F4F9),
                    baselineShift = BaselineShift(0.5f)
                )
            )
        }

        if (showLowBatteryWarning) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier,
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "팟치 배터리가 꺼지면 정시에 알람이 울려요",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.sp,
                        color = Color(0xFFCA4752)
                    )
                )
            }
        }


    }
}

private fun voltageToBatteryPercent(voltage: Double): Int =
    (((voltage - 3.2) / (4.2 - 3.2)) * 100.0).roundToInt().coerceIn(0, 100)

@Composable
private fun SensingStartInfo(
    heartRateBpm: Int?,
    temperatureCelsius: Double?,
){
    Box(
        modifier = Modifier
            .size(320.dp, 112.dp)
            .neumorphicBackground()
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

    ){
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(
                space = 28.dp,
                alignment = Alignment.CenterHorizontally
            )
        ){
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ){
                Text(
                    text = "심박수",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 16.sp,
                        color = Color(0xCCF1F4F9)
                    )
                )
                Text(
                    text = heartRateBpm?.let { "$it bpm" } ?: "-- bpm",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 18.sp,
                        color = Color(0xFFAFF4F9),
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ){
                Text(
                    text = "체온",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 16.sp,
                        color = Color(0xCCF1F4F9)
                    )
                )
                Text(
                    text = temperatureCelsius
                        ?.let { String.format(Locale.getDefault(), "%.0f °C", it) }
                        ?: "-- °C",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 18.sp,
                        color = Color(0xFFAFF4F9),
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }

    }
}

@Composable
fun ActivityAnimation(
    modifier: Modifier = Modifier
){
    val graphIcon: ImageVector = ImageVector.vectorResource(id = AppIcons.SettedActivityGraph)
    val animationDuration: Int = 2500 // 그려지는 시간 (ms)

    // 1. 애니메이션 상태 관리 (0.0f ~ 1.0f)
    val infiniteTransition = rememberInfiniteTransition(label = "graph_reveal")
    // 1. 그리기 진행률 (0f -> 1f)
    val drawProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 6000 // 전체 사이클 (그리기 2s + 대기 1s + 지우기 2s)
                0f at 0 using FastOutSlowInEasing
                1f at animationDuration using FastOutSlowInEasing // 2초간 그리기
                1f at 6000 // 나머지 시간 동안 1 유지
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "draw_progress"
    )

    // 2. 지우기 진행률 (0f -> 1f)
    val eraseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 6000
                0f at 0 // 그리는 동안은 0 유지
                0f at animationDuration + 1000 using FastOutSlowInEasing// 대기 시간(1s)까지 0 유지
                1f at animationDuration + 1000 + animationDuration using FastOutSlowInEasing // 2초간 지우기
                1f at 6000 // 끝까지 1 유지
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "erase_progress"
    )

    // 벡터 이미지를 그리기 위한 페인터
    val graphPainter = rememberVectorPainter(image = graphIcon)

    val graphTintColor = Color(0xFFAAEDF2) // 민트색 그래프 라인

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    val width = size.width
                    val drawEdge = width * drawProgress
                    val eraseEdge = width * eraseProgress

                    // ✅ 핵심: 왼쪽(eraseEdge)부터 오른쪽(drawEdge)까지만 보이도록 클리핑
                    clipRect(
                        left = eraseEdge,
                        right = drawEdge
                    ) {
                        this@drawWithContent.drawContent()
                    }
                }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 12.dp) // ✅ 상하단에 블러가 퍼질 12dp의 여유 공간 확보
                    .blur(radius = 5.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            ){
                with(graphPainter) {
                    draw(
                        size = size,
                        colorFilter = ColorFilter.tint(graphTintColor)
                    )
                }
            }
            Canvas(modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp) // ✅ 상하단에 블러가 퍼질 12dp의 여유 공간 확보
            ) {
                with(graphPainter) {
                    draw(
                        size = size,
                        colorFilter = ColorFilter.tint(graphTintColor)
                    )
                }

            }
        }
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

fun getWakeUpTimeRange(hour:Int, minute: Int, isAm: Boolean, earlyWakeUpMinutes: Int): String{
    var earlyTotalMinute: Int = (hour * 60 + minute) - earlyWakeUpMinutes
    val ampm = if(isAm) "오전" else "오후"
    if(earlyTotalMinute < 0) earlyTotalMinute += 12 * 60

    return String.format(
        Locale.getDefault(),
        "%s %d : %02d ~ %d : %02d",
        ampm, earlyTotalMinute/60, earlyTotalMinute%60, hour, minute)
}

fun earlyWakeUpText(hour:Int, minute: Int, isAm: Boolean, earlyWakeUpMinutes: Int = 15): String{
    var earlyTotalMinute: Int = (hour * 60 + minute) - earlyWakeUpMinutes
    val ampm = if(isAm) "오전" else "오후"
    if(earlyTotalMinute < 0) earlyTotalMinute += 12 * 60

    return String.format(
        Locale.getDefault(),
        "%s  %d:%02d",
        ampm, earlyTotalMinute/60, earlyTotalMinute%60
    )
}

/* fuck you 0303 

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
                                text = getIsAm(alarm.hour, alarm.minute, alarm.isAm,alarm.earlyWakeUpMinutes),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 16.sp
                                )
                            )
                            Text(
                                getWakeUpTimeRange(alarm.hour, alarm.minute, alarm.isAm,alarm.earlyWakeUpMinutes),
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
                // 1) 알람 스케줄 취소
                scheduler.cancel(alarmViewModel.alarm)

                // 2) 워치에 수면 추적 중지 명령 전송
                alarmViewModel.stopSleepTracking()

                // 3) SharedPreferences 플래그/값 삭제
                val alarmPrefs = AlarmPreferences(context)
                alarmPrefs.clearAlarm()

                // 4) 네비게이션 처리
                onTurnAlarmOff()
            }
        ) { Text("알람중지") }

        Spacer(Modifier.height(55.dp))
    }
 */