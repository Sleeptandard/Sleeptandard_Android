package com.leejang.sleeptandard.Screen

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leejang.sleeptandard.Component.CustomTimePicker


@Composable
fun ExperimentScreen() {

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    )
    {

        var varue by remember { mutableFloatStateOf(0f) }

        SemiCircularSlider(
            value = varue,
            onValueChange = { f -> varue = f }
        )

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

    Box(
        modifier = Modifier
            .size(286.dp)
            .clip(shape = CircleShape)
            .background(color = Color.White)
            ,
        contentAlignment = Alignment.Center
    ){
        BoxWithConstraints(
            modifier = modifier.size(252.dp),
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
                            val normalized = ((touchX - sideMarginPx) / usableWidth).coerceIn(0f, 1f)
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
                    brush = Brush.horizontalGradient(listOf(Color(0xFF1A3D6B), Color(0xFFAAEDF2))),
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
                        colors = listOf(Color.Transparent,glowColor),
                    )
                )
        )
    }


}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val backgroundColor = Color.White.copy(alpha = 0.1f)
    val borderColor = Color.White.copy(alpha = 0.2f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp)) // 카드 모양
    ) {
        // [Layer 1] 배경 블러 & 그라데이션
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(30.dp) // 유리 뒤를 흐리게
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.15f), // 위쪽 하이라이트
                            Color.White.copy(alpha = 0.05f)  // 아래쪽 그림자
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.4f), // 테두리 위쪽 (빛남)
                            Color.Transparent,             // 테두리 중간 (투명)
                            Color.White.copy(alpha = 0.1f)  // 테두리 아래쪽 (은은함)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
        )

        // [Layer 2] 실제 내용물 (선명함 유지)
        Box(modifier = Modifier.padding(24.dp)) {
            content()
        }
    }
}