package com.leejang.sleeptandard_mvp.Screen

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D1117))) {
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

        // [중앙 카드]
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GlassCard(modifier = Modifier.width(300.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Seoul", color = Color.White, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("24°C", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Bold)
                    Text("Partly Cloudy", color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}