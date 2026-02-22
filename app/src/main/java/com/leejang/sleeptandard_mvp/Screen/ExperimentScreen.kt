package com.leejang.sleeptandard_mvp.Screen

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leejang.sleeptandard_mvp.Component.CustomTimePicker
import kotlinx.coroutines.delay

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

@Composable
fun ExperimentScreen() {
// 애니메이션을 위한 무한 반복 상태
    val infiniteTransition = rememberInfiniteTransition(label = "background")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing)),
        label = "offset"
    )

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF050C16))) {


        /*
        // [배경] 움직이는 빛 덩어리들 (Canvas 활용)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFF5A4BFF), Color.Transparent)),
                radius = 400f,
                center = Offset(offset % size.width, size.height * 0.2f)
            )
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFF3A7DFF), Color.Transparent)),
                radius = 600f,
                center = Offset(size.width - (offset % size.width), size.height * 0.7f)
            )
        }

         */


        // [중앙 카드]
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(100.dp))

            Box(
                modifier = Modifier
                    .size(320.dp, 260.dp)
                    .drawBehind {
                        // 흰색 그림자
                        val highlightColor1 = Color(0xFFB9C8DF).copy(alpha = 0.15f)
                        val blurRadius1 = 20.dp.toPx()
                        val offsetX1 = (-5).dp.toPx()
                        val offsetY1 = (-5).dp.toPx()

                        drawIntoCanvas { canvas ->
                            val paint = Paint().asFrameworkPaint().apply {
                                color = highlightColor1.toArgb()
                                maskFilter = BlurMaskFilter(blurRadius1, BlurMaskFilter.Blur.NORMAL)
                            }

                            canvas.nativeCanvas.drawRoundRect(
                                offsetX1, offsetY1,
                                size.width + offsetX1, size.height + offsetY1,
                                30.dp.toPx(), 30.dp.toPx(),
                                paint
                            )
                        }

                        // 검은색 그림자
                        val highlightColor2 = Color(0xFF020710).copy(alpha = 0.9f)
                        val blurRadius2 = 15.dp.toPx()
                        val offsetX2 = (8).dp.toPx()
                        val offsetY2 = (8).dp.toPx()

                        drawIntoCanvas { canvas ->
                            val paint = Paint().asFrameworkPaint().apply {
                                color = highlightColor2.toArgb()
                                maskFilter = BlurMaskFilter(blurRadius2, BlurMaskFilter.Blur.NORMAL)
                            }

                            canvas.nativeCanvas.drawRoundRect(
                                offsetX2, offsetY2,
                                size.width + offsetX2, size.height + offsetY2,
                                30.dp.toPx(), 30.dp.toPx(),
                                paint
                            )
                        }

                        val gradient = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF07101E),
                                Color(0xFF101A2A)
                            ),
                            // 시작점을 박스의 정중앙(Center)으로 설정
                            start = Offset(size.width/2, size.height/2),
                            // 끝점을 박스의 우측 하단(BottomEnd)으로 설정
                            end = Offset(size.width, size.height * 2 / 3)
                        )
                        drawRoundRect(
                            brush = gradient,
                            cornerRadius = CornerRadius(30.dp.toPx(), 30.dp.toPx()) // 30dp만큼 둥글게
                        )
                    }
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
                var h = 0
                var m = 0
                var isAm = true

                CustomTimePicker(
                    onTimeChange = { hour12, minute, isAm1 ->
                        h = hour12
                        m = minute
                        isAm = isAm1},

                )
            }



            Spacer(Modifier.height(30.dp))

            Box(
                modifier = Modifier
                    .size(320.dp, 56.dp)
                    .drawBehind {
                        // 흰색 그림자
                        val highlightColor1 = Color(0xFFB9C8DF).copy(alpha = 0.15f)
                        val blurRadius1 = 20.dp.toPx()
                        val offsetX1 = (-5).dp.toPx()
                        val offsetY1 = (-5).dp.toPx()

                        drawIntoCanvas { canvas ->
                            val paint = Paint().asFrameworkPaint().apply {
                                color = highlightColor1.toArgb()
                                maskFilter = BlurMaskFilter(blurRadius1, BlurMaskFilter.Blur.NORMAL)
                            }

                            canvas.nativeCanvas.drawRoundRect(
                                offsetX1, offsetY1,
                                size.width + offsetX1, size.height + offsetY1,
                                // 여기
                                100.dp.toPx(), 100.dp.toPx(),
                                paint
                            )
                        }

                        // 검은색 그림자
                        // 여기
                        val highlightColor2 = Color(0xFF020710).copy(alpha = 0.7f)
                        val blurRadius2 = 15.dp.toPx()
                        val offsetX2 = (8).dp.toPx()
                        val offsetY2 = (8).dp.toPx()

                        drawIntoCanvas { canvas ->
                            val paint = Paint().asFrameworkPaint().apply {
                                color = highlightColor2.toArgb()
                                maskFilter = BlurMaskFilter(blurRadius2, BlurMaskFilter.Blur.NORMAL)
                            }

                            canvas.nativeCanvas.drawRoundRect(
                                offsetX2, offsetY2,
                                size.width + offsetX2, size.height + offsetY2,
                                // 여기
                                100.dp.toPx(), 100.dp.toPx(),
                                paint
                            )
                        }

                        val gradient = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF07101E),
                                Color(0xFF101A2A)
                            ),
                            // 시작점을 박스의 정중앙(Center)으로 설정
                            start = Offset(size.width/2, size.height/2),
                            // 끝점을 박스의 우측 상단으로부터 2/3 지점 설정
                            end = Offset(size.width, size.height * 2 / 3)
                        )
                        drawRoundRect(
                            brush = gradient,
                            cornerRadius = CornerRadius(30.dp.toPx(), 30.dp.toPx()) // 30dp만큼 둥글게
                        )
                    }
                    // Inner shadow
                    .innerShadow(
                        shape = RoundedCornerShape(30.dp),
                        shadow = Shadow(
                            radius = 25.dp,
                            spread = (-12).dp,
                            color = Color(0xFF030E1E).copy(0.8f),
                            offset = DpOffset(x = 5.dp, 6.dp)
                        )
                    )
            ){

            }
            /* dropShadow는 밤티인듯
            Box(
                modifier = Modifier
                    .size(320.dp, 260.dp)
                    .dropShadow(
                        shape = RoundedCornerShape(30.dp),
                        shadow = Shadow(
                            radius = 15.dp,
                            spread = 5.dp,
                            color = Color(0xFF020710).copy(0.9f),
                            offset = DpOffset(x = 8.dp, 8.dp)
                        )
                    )
                    .dropShadow(
                        shape = RoundedCornerShape(30.dp),
                        shadow = Shadow(
                            radius = 20.dp,
                            spread = 0.dp,
                            color = Color(0xFFB9C8DF).copy(0.15f),
                            offset = DpOffset(x = (-5).dp, (-5).dp)
                        )
                    )
                    .drawBehind {
                        val gradient = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF07101E),
                                Color(0xFF101A2A)
                            ),
                            // 시작점을 박스의 정중앙(Center)으로 설정
                            start = Offset(size.width/2, size.height/2),
                            // 끝점을 박스의 우측 하단(BottomEnd)으로 설정
                            end = Offset(size.width, size.height * 2 / 3)
                        )
                        drawRoundRect(
                            brush = gradient,
                            cornerRadius = CornerRadius(30.dp.toPx(), 30.dp.toPx()) // 30dp만큼 둥글게
                        )
                    }
                    .innerShadow(
                        shape = RoundedCornerShape(30.dp),
                        shadow = Shadow(
                            radius = 25.dp,
                            spread = (-12).dp,
                            color = Color(0xFF030E1E).copy(0.8f),
                            offset = DpOffset(x = 5.dp, 6.dp)
                        )
                    )
            )
             */
        }
    }
}