package com.leejang.sleeptandard.Component

/** 홈 화면에 들어가는 대부분의 컴포넌트 모음
 *
 * 시발 모르겠다~
 *
 */

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.leejang.sleeptandard.ui.theme.AppIcons
import com.leejang.sleeptandard.ui.theme.Key
import com.leejang.sleeptandard.ui.theme.SkyBlue
import com.leejang.sleeptandard.ui.theme.WRed
import com.leejang.sleeptandard.ClassFile.AlarmScheduler
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay
import java.time.format.TextStyle

enum class PotchConnectionState {
    NOTHING,
    CONNECTING,
    CONNECTED,
    FAILED
}

@Composable
fun OptionsSection(
        modifier: Modifier = Modifier,
        onSoundClick: () -> Unit,
        onVibrationClick: () -> Unit,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        alarmName: String,
        isSystemVibrationOn: Boolean,
        showBluetoothOffMessage: Boolean,
        potchState: PotchConnectionState,
        tryPotchConnecting: () -> Unit
) {
    val isNone = alarmName == "소리 없음"

    val textColor =
            if (isNone) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            else MaterialTheme.colorScheme.onSurface

    val entireHeight = 128.dp
    var vibTogglechecked = checked
    var vibToggleEnabled = true

    if (!isSystemVibrationOn) {
        vibTogglechecked = false
        vibToggleEnabled = false
    }

    Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
                modifier = Modifier.height(entireHeight).weight(2f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 1. 소리 설정 박스
            Box(
                    modifier =
                            Modifier.zIndex(2f)
                                    .fillMaxWidth()
                                    .aspectRatio(3.7f)
                                    //.height(56.dp)
                                    .neumorphicBackground(
                                            highlightColor = Color(0xFFB9C8DF).copy(alpha = 0.1f),
                                    )
                                    .innerShadow(
                                            shape = RoundedCornerShape(30.dp),
                                            shadow =
                                                    Shadow(
                                                            radius = 25.dp,
                                                            spread = (-12).dp,
                                                            color = Color(0xFF030E1E).copy(0.8f),
                                                            offset = DpOffset(x = 5.dp, 6.dp)
                                                    )
                                    )
                                    .clickable { onSoundClick() }
            ) {
                Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                            painter = painterResource(AppIcons.HomeVolume),
                            contentDescription = "알람음 설정",
                            tint = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                            modifier = Modifier.weight(1f).padding(end = 10.dp),
                            text = alarmName,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 15.sp),
                            textAlign = TextAlign.End,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                            painter = painterResource(AppIcons.HomeArrowRight),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 2. 진동 설정 박스

            Box(
                    modifier =
                            Modifier.zIndex(1f)
                                    .fillMaxWidth()
                                    .aspectRatio(3.7f)
                                    //.height(56.dp)
                                    .neumorphicBackground(
                                            highlightColor = Color(0xFFB9C8DF).copy(alpha = 0.1f),
                                    )
                                    .innerShadow(
                                            shape = RoundedCornerShape(30.dp),
                                            shadow =
                                                    Shadow(
                                                            radius = 25.dp,
                                                            spread = (-12).dp,
                                                            color = Color(0xFF030E1E).copy(0.8f),
                                                            offset = DpOffset(x = 5.dp, 6.dp)
                                                    )
                                    )
                                    .clickable { onVibrationClick() }
            ) {
                Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                    painter =
                                            painterResource(
                                                    if (vibTogglechecked) AppIcons.HomeVibrate
                                                    else AppIcons.HomeNoVibrate
                                            ),
                                    contentDescription = "진동 설정",
                                    tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Switch(
                                modifier = Modifier.scale(37f / 52f),
                                colors =
                                        SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = Color(0xFFB1F7FC),
                                                uncheckedThumbColor = Color.White,
                                                uncheckedTrackColor = Color(0xFF858585),
                                        ),
                                checked = vibTogglechecked,
                                onCheckedChange = onCheckedChange,
                                enabled = vibToggleEnabled
                        )
                    }
                    if (!isSystemVibrationOn) {
                        Text(
                                text = "※ 시스템 알림 진동세기가 0으로 설정되어 있어 진동이 울리지 않아요!",
                                style =
                                        MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.sp,
                                                color = Color(0xFFEB3737)
                                        ),
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        // 팟치 연결 버튼
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier =
                    Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .zIndex(3f)
                    .neumorphicBackground(
                        highlightColor = Color(0xFFB9C8DF).copy(alpha = 0.1f),
                        blurRadius1 = 20.dp,
                    )
                    // Inner shadow
                    .innerShadow(
                        shape = RoundedCornerShape(28.dp),
                        shadow =
                            Shadow(
                                radius = 25.dp,
                                spread = (-12).dp,
                                color = Color(0xFF030E1E).copy(0.8f),
                                offset = DpOffset(x = 5.dp, 6.dp)
                            )
                    )
            ) {
                Box(
                    modifier =
                        Modifier.clip(RoundedCornerShape(28.dp)).fillMaxSize()
                            .clickable {
                                tryPotchConnecting()
                            },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                painter = painterResource(AppIcons.HomePotch),
                                contentDescription = "팟치 아이콘",
                                tint = Color.Unspecified
                            )
                            Text(
                                text = "팟치 연결",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
                            )
                        }

                        when (potchState) {
                            PotchConnectionState.CONNECTING ->
                                Text(
                                    "연결 중...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SkyBlue
                                )

                            PotchConnectionState.CONNECTED ->
                                Text(
                                    "연결 성공!",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SkyBlue
                                )

                            PotchConnectionState.FAILED ->
                                Text(
                                    "연결 실패",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = WRed
                                )

                            PotchConnectionState.NOTHING -> Unit
                        }
                    }
                }
            }

            if (showBluetoothOffMessage) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "블루투스가 꺼져있어요",
                    modifier = Modifier.wrapContentWidth(unbounded = true).zIndex(4f),
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        color = Color.White
                    )
                )
            }
        }
    }
}

/**
 * 홈 화면의 '완료' 버튼
 */
@Composable
fun ConfirmButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
            modifier = modifier.height(56.dp).fillMaxWidth().neumorphicBackground(),
            shape = RoundedCornerShape(100.dp),
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F4F9))
    ) {
        Text(
                text = "완료",
                style =
                        MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 19.sp,
                                color = Key,
                        )
        )
    }
}

/**
 * 기상 윈도우에 들어가는 슬라이더
 */
@Composable
fun DiamondStepSlider(
        value: Int,
        onValueChange: (Int) -> Unit,
        modifier: Modifier = Modifier,
        valueRange: IntRange = 10..30,
        step: Int = 1,
        showIndicator: Boolean = true,
        enabled: Boolean = true
) {
    val steps = remember { valueRange.step(step).toList() }
    val density = LocalDensity.current

    // ✅ 양옆 터치 여유 공간 (20dp 정도 주면 아주 넉넉합니다)
    val sideMarginPx = with(density) { 15.dp.toPx() }

    BoxWithConstraints(
        modifier =
            modifier.fillMaxWidth()
                .height(48.dp) // 터치 높이도 조금 더 확보
                .pointerInput(Unit) {
                    if (enabled) {
                        detectTapGestures { offset ->
                            // ✅ 터치 좌표에서 여유 공간을 뺀 값을 기준으로 비율 계산
                            val usableWidth = size.width - (2 * sideMarginPx)
                            val ratio =
                                ((offset.x - sideMarginPx) / usableWidth).coerceIn(
                                    0f,
                                    1f
                                )
                            val rawValue =
                                valueRange.first +
                                        (valueRange.last - valueRange.first) * ratio
                            val snappedValue =
                                steps.minByOrNull { abs(it - rawValue) } ?: value
                            onValueChange(snappedValue)
                        }
                    }
                }
                .pointerInput(Unit) {
                    if (enabled) {
                        detectDragGestures { change, _ ->
                            val usableWidth = size.width - (2 * sideMarginPx)
                            val ratio =
                                ((change.position.x - sideMarginPx) / usableWidth)
                                    .coerceIn(0f, 1f)
                            val rawValue =
                                valueRange.first +
                                        (valueRange.last - valueRange.first) * ratio
                            val snappedValue =
                                steps.minByOrNull { abs(it - rawValue) } ?: value
                            onValueChange(snappedValue)
                        }
                    }
                }
    ) {
        val fullWidth = constraints.maxWidth.toFloat()
        val usableWidth = fullWidth - (2 * sideMarginPx)

        val fraction = (value - valueRange.first).toFloat() / (valueRange.last - valueRange.first)
        // ✅ 손잡이 중심점이 sideMarginPx부터 시작하도록 설정
        val thumbCenterX = sideMarginPx + (usableWidth * fraction)

        // 1. 전체 트랙 (배경) - 양옆 여백 적용
        Box(
            modifier =
                Modifier.align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp) // ✅ sideMargin과 동일한 패딩
                    .height(6.dp)
                    .background(
                        Color.White.copy(alpha = 0.1f),
                        RoundedCornerShape(100.dp)
                    )
        )

        // 2. 활성 트랙 (색칠되는 부분)
        Box(
            modifier =
                Modifier.align(Alignment.CenterStart)
                    .offset { IntOffset(sideMarginPx.toInt(), 0) } // ✅ 시작점 보정
                    .width(with(density) { (thumbCenterX - sideMarginPx).toDp() })
                    .height(6.dp)
                    .background(
                        brush =
                            Brush.horizontalGradient(
                                colors =
                                    listOf(
                                        Color(0xFFAAEDF2)
                                            .copy(alpha = 0.8f),
                                        Color(0xFFAAEDF2)
                                    )
                            ),
                        shape = RoundedCornerShape(100.dp)
                    )
        )

        // 3. 마름모 손잡이
        Box(
            modifier =
                Modifier.offset { IntOffset(thumbCenterX.toInt() - 9.dp.toPx().toInt(), 0) }
                    .align(Alignment.CenterStart)
                    .drawBehind {
                        // 검은색 그림자
                        val highlightColor2 = Color(0xFF020710).copy(alpha = 0.9f)
                        val blurRadius2 = 15.dp.toPx()
                        val offsetX2 = (-5).dp.toPx()
                        val offsetY2 = (0).dp.toPx()

                        drawIntoCanvas { canvas ->
                            val paint =
                                Paint().asFrameworkPaint().apply {
                                    color = highlightColor2.toArgb()
                                    maskFilter =
                                        BlurMaskFilter(
                                            blurRadius2,
                                            BlurMaskFilter.Blur.NORMAL
                                        )
                                }

                            canvas.nativeCanvas.drawRoundRect(
                                offsetX2,
                                offsetY2,
                                size.width + offsetX2,
                                size.height + offsetY2,
                                15.dp.toPx(),
                                15.dp.toPx(),
                                paint
                            )
                        }
                    }
                    .size(18.dp)
                    .graphicsLayer(rotationZ = 45f)
                    .background(Color.White, RoundedCornerShape(2.dp))
        )

        // 4. indicator (말풍선)
        if (showIndicator) {
            Box(
                modifier =
                    Modifier.offset {
                        IntOffset(
                            thumbCenterX.toInt() - 42.dp.toPx().toInt(),
                            (-(32)).dp.toPx().toInt()
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    modifier = Modifier.size(84.dp, 45.dp),
                    painter = painterResource(AppIcons.HomeWindowIndicator),
                    contentDescription = "windowIndicator",
                )
                Text(
                    modifier = Modifier.offset(y = (-3).dp),
                    text = String.format(Locale.getDefault(),"%d분 전", value),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                )
            }
        }
    }
}

/** 기상 윈도우
 *
 * 최소/최대 설정 시간 + 다이아몬드 슬라이더 + 알람 시간범위 구성
 *
 * enabled: 홈화면에서 쓰냐 / 튜토리얼에서 쓰냐 의 차이
 *
 */
@Composable
fun WakeUpWindow(
        modifier: Modifier = Modifier,
        selectedHour: Int,
        selectedMinute: Int,
        selectedIsAm: Boolean,
        enabled: Boolean = true
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Potch 각성점수 모니터링",
            style = MaterialTheme.typography.bodySmall.copy(
                color = Color(0xFFAFF4F9),
                fontSize = 14.sp
            )
        )
        Spacer(Modifier.height(8.dp))

        if (enabled) {
            Text(
                text =
                    calculateWakeUpRangeText(
                        selectedHour,
                        selectedMinute,
                        selectedIsAm,
                        AlarmScheduler.MONITORING_WINDOW_MINUTES
                    ),
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                    )
            )
        }else{
            Spacer(Modifier.height((15.sp).value.dp))
            Spacer(Modifier.height(8.dp))
        }

    }
}

@Composable
fun ShowWakeUpRange(hour: Int, minute: Int, isAm: Boolean, earlyMinutes: Int = 15,
                    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        color = SkyBlue
                    )
){
    Text(
        text = calculateWakeUpRangeText(
            hour = hour,
            minute = minute,
            isAm = isAm,
            earlyMinutes = earlyMinutes
        ),
        style = style
    )
}

/** 튜토리얼을 끝내고 홈 화면으로 들어오면 나오는 기상윈도우 설명창
 *
 * 다이아몬드 슬라이더를 홈 화면과 정확히 일치시키게 구성하는것 때문에 복잡해진 버러지 창
 *
 */
@Composable
fun WindowTutorial(
        modifier: Modifier = Modifier,
        onDismiss: (Boolean) -> Unit,
) {
    var isChecked by remember { mutableStateOf(true) }

    val checkBackground = if (isChecked) Color(0xFF050C16) else Color.White

    Box(modifier = modifier.fillMaxSize()) {

        Column(modifier = Modifier.fillMaxSize()) {

            Box(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(200.dp))

                    Text(
                        text = "Potch 스마트 알람",
                        style =
                            MaterialTheme.typography.titleMedium.copy(
                                color = Color(0xFFBCD8FF),
                                fontSize = 24.sp
                            )
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "설정 시각 15분 전부터\n각성점수를 실시간으로 확인해요",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = "각성점수가 80점을 넘으면 즉시 알람이 울리고,\n그렇지 않으면 설정 시각에 울려요.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = Color(0xFFBCD8FF),
                        fontSize = 18.sp
                    )
                )
                Spacer(Modifier.weight(2f))
                Spacer(Modifier.height(56.dp))
                Spacer(Modifier.height(25.dp))

            }

            // 네비게이션 바텀바와 같은 크기
            Row(
                modifier =
                    Modifier.background(color = Color.White)
                        .height(80.dp)  // 네비바의 최소 높이
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth(),
                // .align(Alignment.BottomCenter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier.padding(5.dp)
                            .size(23.dp)
                            .background(
                                color = checkBackground,
                                shape = RoundedCornerShape(5.dp)
                            )
                            .border(
                                width = 2.dp,
                                color = Color(0xFF050C16),
                                shape = RoundedCornerShape(5.dp)
                            )
                            .clickable { isChecked = !isChecked },
                    contentAlignment = Alignment.Center
                ) {
                    if (isChecked) {
                        Icon(
                            painter = painterResource(AppIcons.HomeCheck),
                            contentDescription = "췤",
                            tint = Color.White
                        )
                    }
                }

                Text(
                    modifier = Modifier.padding(start = 10.dp),
                    text = "다시보지 않기",
                    style =
                        MaterialTheme.typography.bodyLarge.copy(
                            color = Color.Black,
                            fontSize = 16.sp
                        )
                )

                Spacer(Modifier.weight(1f))

                Icon(
                    modifier = Modifier.clickable { onDismiss(isChecked) },
                    painter = painterResource(AppIcons.HomeX),
                    contentDescription = "x",
                    tint = Color(0xFF050C16)
                )
            }


        }
    }
}

@Composable
fun Modifier.neumorphicBackground(
    highlightColor: Color = Color(0x1AC2E4E9).copy(alpha = 0.10f),
    blurRadius1: Dp = 20.dp,
    offsetX1: Dp = (-5).dp,
    offsetY1: Dp = (-5).dp,
    shadowColor: Color = Color(0xFF020710).copy(alpha = 0.9f),
    blurRadius2: Dp = 15.dp,
    offsetX2: Dp = 8.dp,
    offsetY2: Dp = 8.dp,
    cornerRadius: Dp = 30.dp
) =
        this.drawBehind() {
            drawIntoCanvas { canvas ->
                val paint =
                        Paint().asFrameworkPaint().apply {
                            color = highlightColor.toArgb()
                            maskFilter =
                                    BlurMaskFilter(blurRadius1.toPx(), BlurMaskFilter.Blur.NORMAL)
                        }

                val offX = offsetX1.toPx()
                val offY = offsetY1.toPx()
                canvas.nativeCanvas.drawRoundRect(
                        offX,
                        offY,
                        size.width + offX,
                        size.height + offY,
                        cornerRadius.toPx(),
                        cornerRadius.toPx(),
                        paint
                )
            }
            drawIntoCanvas { canvas ->
                val paint =
                        Paint().asFrameworkPaint().apply {
                            color = shadowColor.toArgb()
                            maskFilter =
                                    BlurMaskFilter(blurRadius2.toPx(), BlurMaskFilter.Blur.NORMAL)
                        }
                val offX = offsetX2.toPx()
                val offY = offsetY2.toPx()
                canvas.nativeCanvas.drawRoundRect(
                        offX,
                        offY,
                        size.width + offX,
                        size.height + offY,
                        cornerRadius.toPx(),
                        cornerRadius.toPx(),
                        paint
                )
            }
            // 3. 메인 배경 그라데이션
            val gradient =
                    Brush.linearGradient(
                            colors = listOf(Color(0xFF07101E), Color(0xFF101A2A)),
                            start = Offset(size.width / 2, size.height / 2),
                            end = Offset(size.width, size.height * 2 / 3)
                    )

            drawRoundRect(
                    brush = gradient,
                    cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
            )
        }

fun calculateWakeUpRangeText(hour: Int, minute: Int, isAm: Boolean, earlyMinutes: Int): String {
    val calendar =
            Calendar.getInstance().apply {
                var h = hour % 12
                if (!isAm) h += 12
                set(Calendar.HOUR_OF_DAY, h)
                set(Calendar.MINUTE, minute)
            }

    val endTime = String.format(Locale.getDefault(), "%d:%02d", hour, minute)

    calendar.add(Calendar.MINUTE, -earlyMinutes)
    val startHour = if (calendar.get(Calendar.HOUR) == 0) 12 else calendar.get(Calendar.HOUR)
    val startIsAm = calendar.get(Calendar.AM_PM) == Calendar.AM
    val startTime =
            String.format(
                    Locale.getDefault(),
                    "%s %d:%02d",
                    if (startIsAm) "오전" else "오후",
                    startHour,
                    calendar.get(Calendar.MINUTE)
            )

    return "$startTime ~ $endTime 사이 알람"
}
