package com.leejang.sleeptandard.Screen

import android.graphics.BlurMaskFilter
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
import androidx.compose.runtime.remember
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
            val usableWidth = width - (2 * sideMarginPx)

            // ✅ 2. 트랙 중앙선 반지름 계산 (손잡이 정렬용)
            val centerRadius = (width - strokeWidthPx) / 2f
            val centerOffset = Offset(width / 2, width / 2)

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            // ✅ 3. x축 변화값만 사용하여 비율 계산
                            val touchX = change.position.x
                            val normalized =
                                ((touchX - sideMarginPx) / usableWidth).coerceIn(0f, 1f)
                            onValueChange(normalized)
                        }
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

                // C. ✅ 손잡이 좌표: 중앙선 반지름(centerRadius)을 기준으로 계산
                val thumbAngle = Math.toRadians(180.0 + (180.0 * value))
                val thumbX = centerOffset.x + centerRadius * kotlin.math.cos(thumbAngle).toFloat()
                val thumbY = centerOffset.y + centerRadius * kotlin.math.sin(thumbAngle).toFloat()

                // 손잡이 그림자
                drawIntoCanvas { canvas ->
                    val shadowPaint = Paint().asFrameworkPaint().apply {
                        color = Color.Black.copy(alpha = 0.3f).toArgb()
                        maskFilter = BlurMaskFilter(8.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
                    }
                    canvas.nativeCanvas.drawCircle(thumbX, thumbY, 14.dp.toPx(), shadowPaint)
                }

                // 손잡이 본체
                drawCircle(
                    color = Color.White,
                    radius = 12.dp.toPx(),
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

/* R.I.P
@Composable
fun DifficultySelectorCustomDraggable(
    value: Int, // -1=무응답 0=쉬움, 1=보통, 2=어려움
    onValueChange: (Int) -> Unit,
    answerList: List<String>,
    modifier: Modifier = Modifier,
) {
    var trackWidthPx by remember { mutableIntStateOf(0) }
    val steps = answerList.size
    val lastIndex = steps - 1

    val faceResId = listOf(AppIcons.ReviewBad, AppIcons.ReviewMeh, AppIcons.ReviewSmile)

    // 각 점의 x 위치(px)를 계산
    val thumbRadiusPx = with(LocalDensity.current) { 9.dp.toPx() }

    // 2. 각 인덱스의 정확한 X 좌표 계산 함수
    fun getXForIndex(index: Int, width: Int): Float {
        if (width <= 0) return 0f
        // 인덱스가 -1인 초기 상태라면 중앙이나 첫번째 위치에 둠
        val safeIndex = if (index == -1) 0 else index
        val usableWidth = (width - 2 * thumbRadiusPx).coerceAtLeast(0f)
        val step = usableWidth / lastIndex
        return thumbRadiusPx + (step * safeIndex)
    }

    // 3. 현재 좌표를 기반으로 가장 가까운 인덱스 찾기
    fun getIndexForX(x: Float, width: Int): Int {
        if (width <= 0) return 0
        val usableWidth = (width - 2 * thumbRadiusPx).coerceAtLeast(0f)
        val step = usableWidth / lastIndex

        // 픽셀 위치를 인덱스 범위(0..2)로 변환 후 반올림
        val relativeX = (x - thumbRadiusPx).coerceIn(0f, usableWidth)
        return (relativeX / step).roundToInt().coerceIn(0, lastIndex)
    }



    val usableWidth = (trackWidthPx.toFloat() - 2 * thumbRadiusPx).coerceAtLeast(0f)

    // 점과 텍스트의 중심이 될 X 좌표 계산 함수 (일관성 유지)
    fun anchorX(index: Int, totalWidth: Int): Float {
        if (totalWidth <= 0) return 0f
        val usableWidth = (totalWidth.toFloat() - 2 * thumbRadiusPx).coerceAtLeast(0f)
        val step = usableWidth / lastIndex
        // index가 -1일 경우 첫 번째 위치(0)를 기본값으로 보여줌
        val safeIndex = if (index == -1) 0 else index
        return thumbRadiusPx + (step * safeIndex)
    }

    // 현재 value에 해당하는 thumb 위치
    val targetX = getXForIndex(value, trackWidthPx)

    val animatedX by animateFloatAsState(
        targetValue = targetX,
        label = "thumbX",
        // 드래그 중일 때는 애니메이션을 끄거나 아주 빠르게 설정해야 반응성이 좋습니다.
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // === 트랙 영역(드래그 받는 곳) ===
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(48.dp) // 터치 영역 확보를 위해 높이 조절
                .onSizeChanged { trackWidthPx = it.width }
                .pointerInput(trackWidthPx) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            onValueChange(getIndexForX(offset.x, trackWidthPx))
                        },
                        onDrag = { change, _ ->
                            // change.position.x는 절대 좌표를 제공하므로 누적 계산이 필요 없음
                            val newIdx = getIndexForX(change.position.x, trackWidthPx)
                            if (newIdx != value) onValueChange(newIdx)
                        }
                    )
                }
                // 클릭 시에도 이동하도록 추가
                .pointerInput(trackWidthPx) {
                    detectTapGestures { offset ->
                        onValueChange(getIndexForX(offset.x, trackWidthPx))
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // 트랙 라인
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 2.dp)
                    .height(2.dp)
                    // .align(Alignment.Center)
                    .background(Color.White.copy(alpha = 0.4f))
            )

            // 고정 점(3개)
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                repeat(steps) { idx ->
                    val selected = idx == value

                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .graphicsLayer {
                                scaleX = if (selected) 1.2f else 1f
                            }
                            .background(Color(0xFFCBCBCB), CircleShape)
                    )
                }
            }

            // Thumb
            if (value != -1) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset((animatedX - thumbRadiusPx).roundToInt(), 0) }
                        .size(26.dp)
                        .shadow(12.dp, CircleShape)
                        .background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(faceResId[value]),
                        contentDescription = null,
                        tint = Color.Unspecified
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // === 라벨 영역 (수정된 부분) ===
        // 트랙과 똑같은 너비를 가지도록 설정 (0.72f)
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(24.dp)
        ) {
            answerList.forEachIndexed { idx, text ->
                val selected = idx == value
                val xPos = anchorX(idx, trackWidthPx) // 트랙의 점과 동일한 X 좌표

                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 16.sp,
                        color = if (selected) Color.White else Color.White.copy(alpha = 0.55f)
                    ),
                    modifier = Modifier
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) {
                                // 텍스트의 중심이 xPos에 오도록 (xPos - 너비의 절반) 위치에 배치
                                val xPosition = xPos - (placeable.width / 2f)
                                placeable.placeRelative(xPosition.roundToInt(), 0)
                            }
                        }
                )
            }
        }
    }
}

 */
