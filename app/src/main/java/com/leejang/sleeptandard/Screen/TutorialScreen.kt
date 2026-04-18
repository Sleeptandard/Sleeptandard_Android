package com.leejang.sleeptandard.Screen

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.DurationBasedAnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Brush.Companion.horizontalGradient
import androidx.compose.ui.graphics.Brush.Companion.linearGradient
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RadialGradient
import androidx.compose.ui.graphics.drawscope.DrawScope.Companion.DefaultBlendMode
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

import com.leejang.sleeptandard.Component.CustomTimePicker
import com.leejang.sleeptandard.Component.DiamondStepSlider
import com.leejang.sleeptandard.Component.LiquidGlassBox
import com.leejang.sleeptandard.ui.theme.AppIcons
import com.leejang.sleeptandard.ui.theme.Neon

@Composable
fun TutorialScreen(
    onFinish: () -> Unit
){

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

    val barGradient = linearGradient(
        listOf(Color(0xFF437AC7),
            Color(0xFFAFF4F9))
    )

    // 리퀴드 글래스 드가자
    val backdrop = rememberLayerBackdrop {
        drawRect(Color.White)
        drawContent()
    }

    val backgroundColor = Color.White

    var currentPage by remember { mutableIntStateOf(0) }
    val maxPage = 4 // 0: 시작, 1: 알람설정, 2: 취침, 3: 피드백, 4: 절전 상태 해제


    // ✅ 뒤로가기 제어 로직 추가
    // currentPage가 0보다 클 때만 이 핸들러가 동작합니다.
    BackHandler(enabled = currentPage > 0) {
        currentPage -= 1
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .layerBackdrop(backdrop)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(linearGradation),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(Modifier.height(60.dp))

            if(currentPage in 1..3) {
                // Rail
                Box(
                    modifier = Modifier.height(52.dp).padding(top = 9.dp, start = 20.dp, end = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(brush = barGradient, shape = RoundedCornerShape(10.dp))
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            // 2. 페이지 내용 (애니메이션 적용 영역)
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = currentPage,
                    transitionSpec = {
                        fadeIn(tween(500)).togetherWith(fadeOut(tween(500)))
                    },
                    label = "TutorialPageTransition"
                ) { targetPage ->
                    // targetPage 상태에 따라 화면을 그립니다.
                    when (targetPage) {
                        0 -> StartPage()
                        1 -> AlarmSettingPart()
                        2 -> AlarmSettedPart()
                        3 -> FeedbackPart()
                        4 -> WatchPowerSavingPage()
                    }
                }
            }

            Button(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(100.dp)),
                onClick = {
                    if (currentPage < maxPage) {
                        Log.d("wtf", "currentPage: ${currentPage}")
                        currentPage += 1
                    } else {
                        onFinish() // 마지막 페이지에서 누르면 홈으로 이동
                    }
                },
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
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
                    ) {

                    }

                    Text(
                        text = if (currentPage < maxPage) "다음" else "시작하기",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 18.sp,
                            color = Color.White

                        )
                    )
                }
            }

            Spacer(Modifier.height(118.dp))
        }
    }

    // 인디케이터
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        Spacer(Modifier.height(60.dp))

        TutorialIndicator(
            modifier = Modifier,
            currentPage = currentPage,
            backdrop = backdrop,
        )
    }
}
@Composable
fun TutorialIndicator(
    backdrop : Backdrop,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    // 1. 표시할 텍스트 리스트 (로그인/회원가입 공통 단계로 구성)
    val stepLabels = listOf("알람설정", "취침", "피드백")

    val numberOfSteps = stepLabels.size

    if(currentPage in 1..3){
        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            val glassWidth = 105.dp
            val stepWidth = (maxWidth) / numberOfSteps
            val diff = (stepWidth - glassWidth)/2
            val targetOffset = stepWidth * (currentPage - 1) + diff * (currentPage - 1)

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

                        val isSelected = index == (currentPage - 1)

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
                backdrop = backdrop,
            ) {
                Text(
                    text = stepLabels[currentPage-1],
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFFAFF4F9),
                        fontSize = 16.sp
                    )
                )
            }

        }
    }


}

@Composable
fun StartPage(){

    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(modifier = Modifier
            .padding(start = 32.dp)
            .weight(170f)
        ) {
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    "알람의 정석",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 24.sp,
                        color = Color(0xFFAFF4F9)
                    )
                )
                Text(
                    "은",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 22.sp
                    )
                )
            }
            Text(
                "어떤 알람인가요?",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 22.sp
                )
            )

            Spacer(Modifier.height(110.dp))
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(24.dp).zIndex(2f)) {

            val targetX = maxWidth * (51f / 72f) - 12.dp
            val targetY = maxHeight * (1f / 2f) - 8.dp

            // 1. 깜빡임(Pulse)을 위한 무한 애니메이션 설정
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")

            val spread by infiniteTransition.animateFloat(
                initialValue = 1f, // 가장 흐릴 때
                targetValue = 6f,  // 가장 선명할 때
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 3000 // 전체 사이클 (그리기 2s + 대기 1s + 지우기 2s)
                        1f at 0 using LinearEasing
                        6f at 300 using LinearEasing // 2초간 그리기
                        6f at 1500 using LinearEasing
                        1f at 1800 using LinearEasing
                        1f at 3000 using LinearEasing// 나머지 시간 동안 1 유지
                    },
                        // tween(1500, easing = CubicBezierEasing(0f,1f,1f,0f)), // 1.5초 간격
                    repeatMode = RepeatMode.Restart
                ),
                label = "spread"
            )

            // 2. 드롭 쉐도우와 원을 겹치기 위한 Box
            Box(
                modifier = Modifier
                    .offset(x = targetX, y = targetY)
                    .size(20.dp),
                contentAlignment = Alignment.Center) {

                Box(
                    modifier = Modifier
                        .background(Color.White, CircleShape)
                        .size(12.dp)
                        .dropShadow(
                            shape = CircleShape,
                            shadow = Shadow(
                                radius = 12.dp,
                                spread = spread.dp,
                                color = Neon,
                                blendMode = BlendMode.Screen
                            )
                        )
                        .innerShadow(
                            shape = CircleShape,
                            shadow = Shadow(
                                radius = 3.dp,
                                color = Neon,
                                spread = 3.dp,
                                alpha = 0.8f,
                            )
                        )

                )
            }

            /*
            Box(
                modifier = Modifier
                    .offset(x = targetX, y = targetY) // 계산된 위치만큼 옆으로 밀기
                    .size(12.dp)
                    .background(Color(0xFFD9D9D9), CircleShape)
                    .border(width = 1.dp, color = Color(0xFFAFF4F9), shape = CircleShape)
                    .innerShadow(
                        shape = CircleShape,
                        shadow = Shadow(radius = 4.dp, color = Color(0xFFAFF4F9), spread = 3.dp)
                    )
                    .dropShadow(
                        shape = CircleShape,
                        shadow = Shadow(radius = 12.dp, color = Color(0xFFAFF4F9), spread = 1.dp)
                    )
            )

             */
        }
        Image(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .zIndex(1f),
            painter = painterResource(AppIcons.TutorialGraph2),
            contentDescription = "그래프",
            contentScale = ContentScale.Fit
        )
        Column(
            modifier = Modifier
                .padding(start = 45.dp)
                .weight(150f)
        ) {
            Spacer(Modifier.height(64.dp))
            Row(
              modifier = Modifier
                  .fillMaxWidth()
            ) {
                Text(
                    text = "당신의 ",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 16.sp
                    )
                )
                Text(
                    text = "가장 얕은 수면",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 16.sp,
                        color = Color(0xFFAFF4F9)
                    )
                )
                Text(
                    text = "을 감지하여",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 16.sp
                    )
                )
            }

            Text(
                text = "가볍게 깨워드려요",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 16.sp
                )
            )

            Spacer(Modifier.height(60.dp))
        }
    }




}
@Composable
fun AlarmSettingPart(){

    val buttonGradient = horizontalGradient(
        listOf(Color(0xFF437AC7),
            Color(0xFF83B5B9)
        )
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        Spacer(Modifier.weight(50f))

        Text("몇 시 이전에는 꼭 일어나야 하나요?",
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 18.sp
            ))
        Row(
            modifier = Modifier.fillMaxWidth().height(24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ){
            Text(
                text = "알람",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFFAFF4F9),
                    fontSize = 14.sp
                )
            )
            Text("과 ",style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp
            ))
            Text(
                text = "기상 윈도우",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFFAFF4F9),
                    fontSize = 14.sp
                )
            )
            Text("를 설정해주세요",style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp
            ))

        }

        Spacer(Modifier.weight(56f))

        Box(
            modifier = Modifier,
            contentAlignment = Alignment.Center
        ){
            Box(
                modifier = Modifier
                    .size(300.dp, 240.dp)
                    .background(brush = buttonGradient,RoundedCornerShape(24.dp))
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
            ){}
            CustomTimePicker(
                defaultHour12 = 6,
                defaultMinute = 0,
                defaultIsAm = true,
                onTimeChange = { h, m, ampm -> },
                scrollEnable = false,
                itemHeight = 60.dp,
                itemHeightAmPm = 45.dp,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 32.sp
                ),
                fadedTextStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 30.sp
                ),
                ampmTextStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp
                ),
                ampmFadedTextStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 14.sp
                ),
            )
        }

        Spacer(Modifier.weight(24f))


        Text(
            modifier = Modifier.fillMaxWidth().padding(end = 30.dp, bottom = 8.dp),
            text = "20분 전",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                color = Color(0xFFAFF4F9),
                textAlign = TextAlign.End
            )
        )

        DiamondStepSlider(
            modifier = Modifier.padding(horizontal = 10.dp),
            value = 25,
            onValueChange = {},
            showIndicator = false
        )

        Text(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 30.dp),
            text = "오전 5:40 ~ 6:00 사이 알람",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 16.sp,
                textAlign = TextAlign.Start
            )
        )

        Spacer(Modifier.weight(79f))

    }
}
@Composable
fun AlarmSettedPart(){
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        Spacer(Modifier.weight(60f))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(7.dp ),
            horizontalAlignment = Alignment.CenterHorizontally
        ){
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "설정 시간내 ",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 18.sp
                    ))
                Text(
                    text = "기상 골든 타임",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF8DF1E2),
                        fontSize = 18.sp
                    ))
                Text(
                    text = "에서 깨워드려요.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 18.sp
                    )
                )
            }
            Row(
                verticalAlignment = Alignment.Bottom
            ){
                Text(
                    text = "수면 측정을 위해, ",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 15.sp
                    )
                )
                Text(
                    text = "워치",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF8DF1E2),
                        fontSize = 15.sp
                    )
                )
                Text(
                    text = "를 착용해주세요.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 15.sp
                    )
                )

            }

        }
        
        Spacer(Modifier.weight(75f))

        GraphAnimation(
            modifier = Modifier.height(130.dp).padding(end = 50.dp)
        )

        Spacer(Modifier.weight(190f))
    }
}
@Composable
fun FeedbackPart(){
     Column(
         modifier = Modifier.fillMaxSize(),
         horizontalAlignment = Alignment.CenterHorizontally
     ) {

         Spacer(Modifier.weight(20f))
         
         Column(
             modifier = Modifier.fillMaxWidth(),
             horizontalAlignment = Alignment.CenterHorizontally
         ){
             Row(
                 verticalAlignment = Alignment.Bottom
             ){
                 Text(
                     text = "당신의 ",
                     style = MaterialTheme.typography.bodyMedium.copy(
                         fontSize = 18.sp
                     )
                 )
                 Text(
                     text = "피드백",
                     style = MaterialTheme.typography.bodyMedium.copy(
                         fontSize = 18.sp,
                         color = Color(0xFF8DF1E2)
                     )
                 )
                 Text(
                     text = "으로",
                     style = MaterialTheme.typography.bodyMedium.copy(
                         fontSize = 18.sp
                     )
                 )
             }
             Text(
                 text = "알고리즘이 개인 맞춤형으로 진화해요",
                     style = MaterialTheme.typography.bodyMedium.copy(
                     fontSize = 18.sp
                     )
             )
         }
         
         Spacer(Modifier.weight(40f))


         Image(

             painter = painterResource(AppIcons.TutorialFeedback2),
             contentDescription = "피드백"
         )


         Spacer(Modifier.weight(60f))
     }
}

@Composable
fun WatchPowerSavingPage(){

    // 1. 스냅 및 스크롤 상태 관리
    val lazyListState = rememberLazyListState()
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = lazyListState)

    // 2. 화면 너비를 계산하여 양옆 패딩 설정 (아이템 너비가 250.dp일 때)
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val itemWidth = 250.dp
    val horizontalPadding = (screenWidth - itemWidth) / 2

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(5f))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "수면 측정이 중단되지 않도록",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 16.sp
                )
            )
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "워치앱에서 ",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 18.sp,
                        color = Color(0xFF8DF1E2)
                    )
                )
                Text(
                    text = "절전 상태를 ",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 16.sp
                    )
                )
                Text(
                    text = "해제",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 18.sp,
                        color = Color(0xFFFF3A3A)
                    )
                )
                Text(
                    text = "해 주세요.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 16.sp
                    )
                )
            }
        }

        Spacer(Modifier.weight(6f))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "[설정] ➡\uFE0E [배터리] ➡\uFE0E [절전 상태 앱]",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    color = Color(0xFFD4D4D4)
                )
            )
            Text(
                text = "알람의 정석 제거",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    color = Color(0xFFD4D4D4)
                )
            )
        }

        Spacer(Modifier.weight(3.6f))

        // 3. LazyRow 이미지 영역 (잘려 있는 부분)
        LazyRow(
            state = lazyListState, // 상태 연결
            flingBehavior = snapFlingBehavior, // 스냅 동작 연결
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            contentPadding = PaddingValues(horizontal = horizontalPadding), // 계산된 패딩 적용
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 가이드 이미지 1
            item {
                Box(
                    modifier = Modifier
                        .size(itemWidth) // 변수 사용
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = AppIcons.TutorialPowerSaving1),
                        contentDescription = "Step 1",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            // 가이드 이미지 2
            item {
                Box(
                    modifier = Modifier
                        .size(itemWidth)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = AppIcons.TutorialPowerSaving2),
                        contentDescription = "Step 2",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        Spacer(Modifier.weight(6f)) // 하단 버튼과의 간격 조절





    }
}

@Composable
fun GraphAnimation(
    modifier: Modifier = Modifier
) {
    // 1. PNG 이미지용 페인터 생성 (AppIcons에 PNG 리소스 ID가 등록되어 있어야 함)
    val graphPainter = painterResource(id = AppIcons.TutorialGraph3)
    val animationDuration = 2000

    val infiniteTransition = rememberInfiniteTransition(label = "graph_reveal")

    // 그리기 및 지우기 진행률 로직은 기존과 동일
    val drawProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 6000
                0f at 0 using LinearEasing
                1f at animationDuration using FastOutSlowInEasing
                1f at 6000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "draw_progress"
    )

    val eraseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 6000
                0f at 0
                0f at animationDuration + 1000
                1f at animationDuration + 1000 + animationDuration using FastOutSlowInEasing
                1f at 6000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "erase_progress"
    )

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    val width = size.width
                    val drawEdge = width * drawProgress
                    val eraseEdge = width * eraseProgress

                    // ✅ 클리핑 로직: PNG 이미지도 이 영역 안에서만 그려짐
                    clipRect(
                        left = eraseEdge,
                        right = drawEdge
                    ) {
                        this@drawWithContent.drawContent()
                    }
                }
        ) {
            // ✅ 메인 레이어 (PNG 이미지)
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                with(graphPainter) {
                    draw(size = size)
                }
            }
        }
    }
}