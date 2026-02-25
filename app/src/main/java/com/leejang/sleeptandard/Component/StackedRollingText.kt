package com.leejang.sleeptandard.Component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun StackedRollingText(
    texts: List<String>,
    modifier: Modifier = Modifier,
    stayMs: Long = 1200L,
    moveMs: Int = 320,
    shift: Dp = 18.dp,
    maxLines: Int = 3
) {
    require(texts.isNotEmpty())

    var index by remember { mutableIntStateOf(0) }

    // 처음엔 1번 텍스트만 중앙
    var stack by remember { mutableStateOf(listOf(texts.first())) }

    val anim = remember { Animatable(0f) }

    val density = LocalDensity.current
    val shiftPx = with(density) { shift.toPx() }

    LaunchedEffect(Unit) {
        // ✅ 마지막 텍스트가 중앙에 올 때까지만 반복
        while (index < texts.lastIndex) {
            delay(stayMs)

            // 1) 기존 텍스트들 위로 이동
            anim.snapTo(0f)
            anim.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = moveMs,
                    easing = FastOutSlowInEasing
                )
            )

            // 2) 다음 텍스트를 중앙에 추가
            index += 1
            stack = (stack + texts[index]).takeLast(maxLines)

            // 3) 오프셋 리셋
            anim.snapTo(0f)
        }
        // 👉 여기 도달하면 3번째 텍스트가 중앙에 있고 그대로 멈춤
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        stack.forEachIndexed { i, s ->
            val fromBottom = stack.lastIndex - i // 최신=0, 이전=1, ...
            val baseY = -shiftPx * fromBottom
            val animY = -shiftPx * anim.value

            // ✅ "현재 줄이 중앙에서 얼마나 멀어졌는지" (0: 중앙, 1: 한 칸 위, 2: 두 칸 위...)
            val effectiveLevel = fromBottom + anim.value

            // ✅ 레벨이 올라갈수록 작아짐 (원하는 만큼 숫자 조절)
            val minScale = 0.78f
            val perLevelShrink = 0.10f // 한 칸 위로 갈 때마다 10%씩 축소
            val scale = (1f - perLevelShrink * effectiveLevel).coerceIn(minScale, 1f)

            // (옵션) 위로 갈수록 옅게
            val alpha = (1f - 0.18f * effectiveLevel).coerceIn(0.55f, 1f)

            Text(
                text = s,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.graphicsLayer {
                    translationY = baseY + animY
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
            )
        }
    }
}