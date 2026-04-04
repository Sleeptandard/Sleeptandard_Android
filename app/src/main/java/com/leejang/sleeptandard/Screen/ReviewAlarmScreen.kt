package com.leejang.sleeptandard.Screen

import android.graphics.BlurMaskFilter
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Brush.Companion.horizontalGradient
import androidx.compose.ui.graphics.Brush.Companion.linearGradient
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leejang.sleeptandard.ui.theme.AppIcons
import kotlin.math.abs
import kotlin.math.log
import kotlin.math.roundToInt

@Composable
fun ReviewAlarmScreen(
    onSubmit: () -> Unit = {}   // 선택값 전달 콜백
) {

    val linearGradation = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color(0xFF050C16),
            1f to Color(0xFF1C447C)
        )
    )

    val buttonGradient = linearGradient(
        listOf(Color(0xFF437AC7),
            Color(0xFFAFF4F9))
    )

    var feedbackScore by remember { mutableFloatStateOf(0.5f) } // 0.0f ~ 1.0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(linearGradation)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {


        Spacer(Modifier.weight(100f))

        Text(
            text = "오늘 기상 점수는 몇 점인가요?",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 25.sp,
                color = Color.White
            )
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = "얼마나 개운하게 일어났는지 알려주세요",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        )

        Spacer(Modifier.weight(104f))

        SemiCircularSlider(
            value = feedbackScore,
            onValueChange = { score -> feedbackScore = score }
        )

        Spacer(Modifier.weight(104f))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .clickable{
                    // TODO: 백엔드 연결 로직
                    onSubmit()
                },
            contentAlignment = Alignment.Center
        ){
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(brush = buttonGradient)
                    .blur(30.dp)
                    .border(
                        width = 2.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.4f), // 테두리 위쪽 (빛남)
                                Color.Transparent,             // 테두리 중간 (투명)
                                Color.White.copy(alpha = 0.1f)  // 테두리 아래쪽 (은은함)
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ){

            }

            Text(
                text = "제출",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 18.sp,
                    color = Color.White
                )
            )

        }


        Spacer(Modifier.height(90.dp))

    }

}

@Composable
fun SemiCircularSlider(
    value: Float, // 0.0f ~ 1.0f
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val strokeWidth = 24.dp
    val density = LocalDensity.current

    // 점수에 따른 광원 색상
    val glowColor = when {
        value < 0.33f -> Color(0xFFFF5967)
        value < 0.66f -> Color(0xFFFFE359)
        else -> Color(0xFF59FF85)
    }

    val sliderGradient = horizontalGradient(
        listOf(
            Color(0xFF1C447C),
            Color(0xFF050C16),

            )
    )

    // ✅ 1. 'Stale Closure' 방지를 위해 최신 상태를 담는 홀더를 만듭니다.
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    // ✅ 상태 관리 변수
    var accumulatedDelta by remember { mutableFloatStateOf(0f) } // x: 각도의 변화량
    var startValueAtDrag by remember { mutableFloatStateOf(0f) } // y를 구하기 위한 시작 시점의 value
    var lastAngle by remember { mutableFloatStateOf(0f) }        // 이전 프레임의 각도


    Box(
        modifier = Modifier
            .size(300.dp)
            .clip(shape = CircleShape)
            .background(color = Color.White)
        ,
        contentAlignment = Alignment.Center
    ){
        BoxWithConstraints(
            modifier = modifier.size(265.dp),
            contentAlignment = Alignment.Center
        ) {
            val width = constraints.maxWidth.toFloat()
            val strokeWidthPx = with(density) { strokeWidth.toPx() }

            // ✅ 1. 조작 가능한 실제 가로 길이 및 마진 계산
            // 트랙의 두께 절반 지점부터 반대쪽 두께 절반 지점까지를 100% 범위로 잡습니다.
            val sideMarginPx = strokeWidthPx / 2
            // val usableWidth = width - (2 * sideMarginPx)

            // ✅ 2. 트랙 중앙선 반지름 계산 (손잡이 정렬용)
            val centerRadius = (width - strokeWidthPx) / 2f
            val centerOffset = Offset(width / 2, width / 2)
            val centerX = width / 2f
            val centerY = width / 2f // 원의 중심

            // 9시 방향을 0도로 계산하기 위한 헬퍼 함수
            fun getAngleFrom9OClock(offset: Offset): Float {
                val rawAngle = Math.toDegrees(
                    Math.atan2((offset.y - centerY).toDouble(), (offset.x - centerX).toDouble())
                ).toFloat()
                // atan2는 9시 방향이 -180도, 3시 방향이 0도이므로 180을 더해 0~180 범위로 만듭니다.
                return rawAngle + 180f
            }

            // C. ✅ 손잡이 좌표: 중앙선 반지름(centerRadius)을 기준으로 계산
            val thumbAngle = Math.toRadians(180.0 + (180.0 * value))
            val thumbX = centerOffset.x + centerRadius * kotlin.math.cos(thumbAngle).toFloat()
            val thumbY = centerOffset.y + centerRadius * kotlin.math.sin(thumbAngle).toFloat()


            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                lastAngle = getAngleFrom9OClock(offset)

                                // ✅ 2. 0.5f 고정값이 아닌, 업데이트된 최신 값을 시작점으로 잡습니다.
                                startValueAtDrag = currentValue

                                accumulatedDelta = 0f
                            },
                            onDrag = { change, _ ->
                                val currentAngle = getAngleFrom9OClock(change.position)

                                var delta = currentAngle - lastAngle
                                if (delta > 180f) delta -= 360f
                                else if (delta < -180f) delta += 360f

                                accumulatedDelta += delta
                                lastAngle = currentAngle

                                val x = accumulatedDelta
                                val y = startValueAtDrag * 180f

                                val newNormalized = ((x + y).coerceIn(0f, 180f)) / 180f

                                // ✅ 3. 콜백도 최신 상태를 유지하도록 호출합니다.
                                currentOnValueChange(newNormalized)
                            }
                        )
                    }
            ) {
                // A. 배경 트랙
                drawArc(
                    color = Color(0xFF050C16).copy(alpha = 0.1f),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(sideMarginPx, sideMarginPx),
                    size = Size(centerRadius * 2, centerRadius * 2),
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )

                // B. 활성 트랙 (애니메이션 없이 즉각 반응)
                drawArc(
                    brush = sliderGradient,
                    startAngle = 180f,
                    sweepAngle = 180f * value,
                    useCenter = false,
                    topLeft = Offset(sideMarginPx, sideMarginPx),
                    size = Size(centerRadius * 2, centerRadius * 2),
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )


                // 손잡이 그림자
                drawIntoCanvas { canvas ->
                    val shadowPaint = Paint().asFrameworkPaint().apply {
                        color = Color.Black.copy(alpha = 0.3f).toArgb()
                        maskFilter = BlurMaskFilter(8.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
                    }
                    canvas.nativeCanvas.drawCircle(thumbX, thumbY, 24.dp.toPx(), shadowPaint)
                }

                // 손잡이 본체
                drawCircle(
                    color = Color.White,
                    radius = 20.dp.toPx(),
                    center = Offset(thumbX, thumbY)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 50.dp, start = 5.dp, end = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ){
                Text(
                    text = "-",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF1C447C),
                        fontSize = 25.sp
                    )
                )

                Text(
                    text = "+",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF1C447C),
                        fontSize = 25.sp
                    )
                )
            }



            // 5. 중앙 텍스트 (0 ~ 100점)
            Row(
                verticalAlignment = Alignment.Bottom) {
                Text(
                    modifier = Modifier.alignByBaseline(),
                    text = "${(value * 100).toInt()}",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 50.sp,
                        color = Color.Black,
                    ),

                    )
                Text(
                    modifier = Modifier.alignByBaseline(),
                    text = "점",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.Black,
                        fontSize = 25.sp,

                        ))
            }
        }
        // 4. 하단 광원 효과 (Glow)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(60.dp)
                .blur(10.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, glowColor),
                    )
                )
        )
    }
}
