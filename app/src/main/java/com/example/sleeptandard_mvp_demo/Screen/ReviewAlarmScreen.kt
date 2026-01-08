package com.example.sleeptandard_mvp_demo.Screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
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

    val linearGradation = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color(0xFF050C16),
            1f to Color(0xFF1C447C)
        )
    )

    var feedback1 by remember { mutableIntStateOf(-1) } // 무응답 상태
    var feedback2 by remember { mutableIntStateOf(-1) }
    var feedback3 by remember { mutableIntStateOf(-1) }
    val answerList1 = listOf<String>("멍함", "보통", "선명함")
    val answerList2 = listOf<String>("무거움", "보통", "가벼움")
    val answerList3 = listOf<String>("많이졸림", "보통", "안졸림")


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(linearGradation),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(112.dp))

        Text(
            text = "1. 지금 정신이 얼마나 또렷한가요?",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 20.sp,
                color = Color.White
            ),
            modifier = Modifier.padding(bottom = 20.dp)
        )

        Spacer(modifier = Modifier.height(26.dp))



        DifficultySelectorCustomDraggable(
            value = feedback1,
            onValueChange = { feedback1 = it },
            answerList = answerList1
        )

        Spacer(modifier = Modifier.height(65.dp))

        if(feedback1 != -1) {

            Text("2. 지금 몸 상태는 어떤가요?", color = Color.White)

            Spacer(modifier = Modifier.height(26.dp))



            DifficultySelectorCustomDraggable(
                value = feedback2,
                onValueChange = { feedback2 = it },
                answerList = answerList2
            )

            Spacer(modifier = Modifier.height(65.dp))
        }

        if(feedback2 != -1) {

            Text("3. 지금 졸린 정도는 어떤가요?", color = Color.White)

            Spacer(modifier = Modifier.height(26.dp))


            DifficultySelectorCustomDraggable(
                value = feedback3,
                onValueChange = { feedback3 = it },
                answerList = answerList3
            )

            Spacer(Modifier.height(67.dp))
        }

        if(feedback3 != -1) {

            // 제출 버튼
            Button(
                onClick = onSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 25.dp),
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors().copy(
                    containerColor = Color(0x0DFFFFFF),
                    contentColor = Color(0xFFF2F6FA)
                )
            ) {
                Text("제출")
            }
        }
    }

}

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
                        .size(18.dp)
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