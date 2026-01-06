package com.example.sleeptandard_mvp_demo.Screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sleeptandard_mvp_demo.ui.theme.AlarmBackground
import com.example.sleeptandard_mvp_demo.ui.theme.AppIcons
import com.example.sleeptandard_mvp_demo.ui.theme.LightBackground
import kotlin.math.abs
import kotlin.math.roundToInt

enum class WakeCondition { BAD, SOSO, GOOD }

@Composable
fun ReviewAlarmScreen(
    onSubmit: () -> Unit = {}   // 선택값 전달 콜백
) {
    var selectedOption1 by remember { mutableStateOf<String?>(null) }
    var selectedOption2 by remember {mutableStateOf<String?>(null)}


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AlarmBackground),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(180.dp))

        var selectedCondition by remember { mutableStateOf<WakeCondition?>(null) }

        Text(
            text = "1. 기상 후 컨디션은?",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 20.sp,
                color = Color.White
            ),
            modifier = Modifier.padding(bottom = 20.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConditionFaceButton(
                selected = selectedCondition == WakeCondition.BAD,
                onClick = { selectedCondition = WakeCondition.BAD },
                iconRes = AppIcons.ReviewBadFace,   // 너 리소스 이름에 맞춰 바꿔
                contentDescription = "bad"
            )
            ConditionFaceButton(
                selected = selectedCondition == WakeCondition.SOSO,
                onClick = { selectedCondition = WakeCondition.SOSO },
                iconRes = AppIcons.ReviewSosoFace,
                contentDescription = "soso"
            )
            ConditionFaceButton(
                selected = selectedCondition == WakeCondition.GOOD,
                onClick = { selectedCondition = WakeCondition.GOOD },
                iconRes = AppIcons.ReviewGoodFace,
                contentDescription = "good"
            )
        }

        Spacer(modifier = Modifier.height(30.dp))


        Text("2. 기상 난이도는?", color = Color.White)

        var difficulty by remember { mutableIntStateOf(1) } // 보통

        DifficultySelectorCustomDraggable(
            value = difficulty,
            onValueChange = { difficulty = it }
        )

        Spacer(Modifier.height(50.dp))

        // 제출 버튼
        Button(
            onClick = onSubmit ,
            modifier = Modifier
                .width(194.dp)
                .height(67.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors().copy(
                containerColor = LightBackground,
                contentColor = Color.Black
            )
        ) {
            Text("제출하기")
        }
    }
}

@Composable
fun ConditionFaceButton(
    selected: Boolean,
    onClick: () -> Unit,
    iconRes: Int,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) Color.White else Color.Transparent
    val tint = if (selected) AlarmBackground else Color.White

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
        )
    }
}

@Composable
fun DifficultySelectorCustomDraggable(
    value: Int, // 0=쉬움, 1=보통, 2=어려움
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = listOf("쉬움", "보통", "어려움")

    var trackWidthPx by remember { mutableIntStateOf(0) }
    val steps = 3
    val lastIndex = steps - 1

    val faceResId = listOf(AppIcons.ReviewBad, AppIcons.ReviewMeh, AppIcons.ReviewSmile)

    // 각 점의 x 위치(px)를 계산
    val thumbRadiusPx = with(LocalDensity.current) { 9.dp.toPx() }
    val usableWidth = (trackWidthPx.toFloat() - 2 * thumbRadiusPx).coerceAtLeast(0f)

    fun anchorX(index: Int): Float {
        val step = usableWidth / lastIndex
        return thumbRadiusPx + step * index
    }

    // 현재 value에 해당하는 thumb 위치
    val targetX = anchorX(value)
    val animatedX by animateFloatAsState(targetValue = targetX, label = "thumbX")

    // 드래그 중일 때는 pointer 위치 기반으로 index를 선택
    fun nearestIndex(x: Float): Int {
        if (trackWidthPx <= 0) return value
        val candidates = (0..lastIndex).toList()
        return candidates.minBy { idx -> abs(anchorX(idx) - x) }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // === 트랙 영역(드래그 받는 곳) ===
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(28.dp)
                .onSizeChanged { trackWidthPx = it.width }
                .pointerInput(trackWidthPx) {
                    var dragX = 0f

                    detectDragGestures(
                        onDragStart = { start ->
                            dragX = start.x
                            onValueChange(nearestIndex(dragX))
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragX = (dragX + dragAmount.x).coerceIn(0f, trackWidthPx.toFloat())
                            val idx = nearestIndex(dragX)
                            if (idx != value) onValueChange(idx)
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // 트랙 라인
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.Center)
                    .background(Color.White.copy(alpha = 0.4f))
            )

            // 고정 점(3개)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterStart),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(steps) { idx ->
                    val selected = idx == value
                    val scale by animateFloatAsState(
                        targetValue = if (selected) 1.25f else 1f,
                        label = "dotScale"
                    )
                    val alpha by animateFloatAsState(
                        targetValue = if (selected) 1f else 0.55f,
                        label = "dotAlpha"
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                this.alpha = alpha
                            }
                            .background(Color.White, CircleShape)
                    )
                }
            }

            // ✅ 움직이는 Thumb(가장 위 레이어)
            // 중앙 정렬을 위해 반지름만큼 좌측으로 오프셋(-r)
            val rPx = with(LocalDensity.current) { 9.dp.toPx() }

            Box(
                modifier = Modifier
                    .offset { IntOffset((animatedX - rPx).roundToInt(), 0) }
                    .align(Alignment.CenterStart)
                    .size(18.dp),
                contentAlignment = Alignment.Center
            ) {
                // 1) Glow (살짝 큰 원 + 반투명 + blur 느낌)
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .graphicsLayer {
                            alpha = 0.35f
                            // 약간 퍼진 느낌: scale
                            scaleX = 1.1f
                            scaleY = 1.1f
                            // (선택) shadow로 더 퍼진 느낌
                            shadowElevation = 18.dp.toPx()
                            shape = CircleShape
                            clip = false
                        }
                        .background(Color.White, CircleShape)
                )

                // 2) Thumb 본체
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .shadow(
                            elevation = 6.dp,
                            shape = CircleShape,
                            ambientColor = Color.White.copy(alpha = 0.25f),
                            spotColor = Color.White.copy(alpha = 0.25f)
                        )
                        .background(Color.White, CircleShape)
                )

                Icon(
                    painter = painterResource(faceResId[value]),
                    contentDescription = "얼굴표정",
                    tint = Color.Unspecified
                )
            }
        }

        Spacer(Modifier.height(5.dp))

        // 라벨(선택된 것만 밝게)
        Row(
            modifier = Modifier.fillMaxWidth(0.8f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            labels.forEachIndexed { idx, text ->
                val selected = idx == value
                val alpha by animateFloatAsState(
                    targetValue = if (selected) 1f else 0.55f,
                    label = "labelAlpha"
                )
                Text(
                    text = text,
                    color = Color.White.copy(alpha = alpha),
                    modifier = Modifier
                        .clickable { onValueChange(idx) }
                )
            }
        }
    }
}


@Preview
@Composable
fun ReviewAlarmScreenPreview(){
    ReviewAlarmScreen({})
}