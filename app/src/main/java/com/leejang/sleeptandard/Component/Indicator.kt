package com.leejang.sleeptandard.Component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leejang.sleeptandard.Screen.AuthStep

@Composable
fun AuthStepIndicator(
    currentStep: AuthStep,
    modifier: Modifier = Modifier
) {
    // 1. 표시할 텍스트 리스트 (로그인/회원가입 공통 단계로 구성)
    val stepLabels = listOf("메일 입력", "회원가입", "로그인")

    // 2. ✅ 핵심 수정: 현재 상태(AuthStep)를 인덱스 번호로 매핑합니다.
    // SignupPassword와 LoginPassword를 모두 '비밀번호 입력' 단계(인덱스 1)로 묶어줍니다.
    val currentIndex = when (currentStep) {
        is AuthStep.EmailInput -> 0         // 메일입력
        is AuthStep.SignupPassword -> 1     // 회원가입
        is AuthStep.SignupNickname -> 1
        is AuthStep.SignupGenderBirth -> 1
        is AuthStep.LoginPassword -> 2      // 로그인
        is AuthStep.FindPassword -> 2
        is AuthStep.PasswordReset -> 3      // 없음
        is AuthStep.Completed -> 3
    }

    val numberOfSteps = stepLabels.size

    if(currentIndex < 3){
        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            val glassWidth = 105.dp
            val stepWidth = (maxWidth) / numberOfSteps
            val diff = (stepWidth - glassWidth)/2
            val targetOffset = stepWidth * currentIndex + diff * currentIndex

            // 위치 이동 애니메이션
            val animatedOffset by animateDpAsState(
                targetValue = targetOffset,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                label = "step_highlight_move"
            )




            Box(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .fillMaxSize()
            ){
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ){
                    stepLabels.forEachIndexed { index, step ->

                        val isSelected = index == currentIndex

                        // 1. 투명도 애니메이션 (선택되면 0, 아니면 1)
                        val textAlpha by animateFloatAsState(
                            targetValue = if (isSelected) 0f else 1f,
                            animationSpec = tween(durationMillis = 300),
                            label = "text_alpha"
                        )

                        // 2. 수직 이동 애니메이션 (선택되면 아래로 20dp 이동)
                        val textOffset by animateDpAsState(
                            targetValue = if (isSelected) 20.dp else 0.dp,
                            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                            label = "text_offset"
                        )

                        Box(
                            modifier = Modifier.weight(1f), // 화면을 정확히 1:1:1로 나눕니다.
                            contentAlignment = when (index) {
                                0 -> Alignment.CenterStart // 첫 번째: 왼쪽 정렬
                                1 -> Alignment.Center      // 두 번째: 무조건 중앙 정렬
                                else -> Alignment.CenterEnd // 세 번째: 오른쪽 정렬ㅅ
                            }
                        ) {
                            Text(
                                text = step,
                                modifier = Modifier
                                    .graphicsLayer {
                                        alpha = textAlpha            // 투명도 적용
                                        translationY = textOffset.toPx() // 아래로 사라지는 이동 적용
                                    },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            )
                        }
                    }
                }
            }

            LiquidGlassBox(
                modifier = Modifier
                    .width(glassWidth)
                    .padding(top = 9.dp)
                    .fillMaxHeight()
                    .offset(x = animatedOffset),
            ) {
                Text(
                    text = stepLabels[currentIndex],
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFFAFF4F9),
                        fontSize = 16.sp
                    )
                )
            }
        }
    }


}