package com.leejang.sleeptandard_mvp.Screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
fun RollingTextNoDisappear(
    text: String,
    modifier: Modifier = Modifier,
    durationMs: Int = 350
) {
    var prev by remember { mutableStateOf(text) }         // 이전 텍스트
    val anim = remember { Animatable(1f) }               // 0 -> 1 진행도

    LaunchedEffect(text) {
        // 이전 텍스트(prev)는 그대로 둔 상태에서 새 텍스트(text)로 전환 애니메이션
        anim.snapTo(0f)
        anim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMs, easing = FastOutSlowInEasing)
        )
        // ✅ 애니메이션이 끝난 뒤에야 prev를 새 텍스트로 갱신
        prev = text
    }

    val t = anim.value

    val shift = 18.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    val shiftPx = with(density) { shift.toPx() }

    val outY = -shiftPx * t 
    val inY = shiftPx * (1f - t)

    val outScale = 1f - 0.08f * t
    val inScale = 0.92f + 0.08f * t

    // "완전 사라지지" 않게 최소 알파를 유지
    val outAlpha = 1f - 0.15f * t      // 1 -> 0.85
    val inAlpha = 0.85f + 0.15f * t    // 0.85 -> 1

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // ✅ 이전 텍스트: prev
        Text(
            text = prev,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.graphicsLayer {
                translationY = outY
                scaleX = outScale
                scaleY = outScale
                alpha = outAlpha
            }
        )

        // ✅ 새 텍스트: text
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.graphicsLayer {
                translationY = inY
                scaleX = inScale
                scaleY = inScale
                alpha = inAlpha
            }
        )
    }
}

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

@Composable
fun StackedRollingTextsOnly(
    texts: List<String>,
    modifier: Modifier = Modifier,
    stayMs: Long = 1200L,
    moveMs: Int = 320,
    shift: Dp = 18.dp,
    maxLines: Int = 2,          // 여기서는 2번/3번만이라 2가 딱 좋음
) {
    require(texts.isNotEmpty())

    var index by remember { mutableIntStateOf(0) }
    var stack by remember { mutableStateOf(listOf(texts.first())) }

    val anim = remember { Animatable(0f) }
    val shiftPx = with(LocalDensity.current) { shift.toPx() }

    LaunchedEffect(Unit) {
        // 마지막 텍스트가 중앙에 오면 멈춤
        while (index < texts.lastIndex) {
            delay(stayMs)

            anim.snapTo(0f)
            anim.animateTo(
                targetValue = 1f,
                animationSpec = tween(moveMs, easing = FastOutSlowInEasing)
            )

            index += 1
            stack = (stack + texts[index]).takeLast(maxLines)

            anim.snapTo(0f)
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        stack.forEachIndexed { i, s ->
            val fromBottom = stack.lastIndex - i
            val baseY = -shiftPx * fromBottom
            val animY = -shiftPx * anim.value

            // ✅ 위로 밀릴 때 작아지는 효과(스케일)
            val effectiveLevel = fromBottom + anim.value
            val minScale = 0.82f
            val perLevelShrink = 0.10f
            val scale = (1f - perLevelShrink * effectiveLevel).coerceIn(minScale, 1f)

            Text(
                text = s,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.graphicsLayer {
                    translationY = baseY + animY
                    scaleX = scale
                    scaleY = scale
                    alpha = (1f - 0.15f * effectiveLevel).coerceIn(0.6f, 1f)
                }
            )
        }
    }
}

@Composable
fun ExperimentScreen() {

    val list = listOf("알람을 설정해볼까요?", "오늘도 화이팅!", "기상 시간을 지켜드릴게요")
    var i by remember { mutableIntStateOf(0) }
/*
    LaunchedEffect(Unit) {
        while (true) {
            delay(1800)
            i = (i + 1) % list.size
        }
    }
*/
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(100.dp))

        StackedRollingText(
            texts = list,
            modifier = Modifier.fillMaxWidth(),
            stayMs = 1200L,
            moveMs = 320,
            shift = 18.dp,
            maxLines = 3
        )
        /*
        RollingTextNoDisappear(
            text = list[i],
            modifier = Modifier.fillMaxWidth()
        )
        */
    }

}