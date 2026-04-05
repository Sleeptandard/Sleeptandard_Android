package com.leejang.sleeptandard.Screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Brush.Companion.horizontalGradient
import androidx.compose.ui.graphics.Brush.Companion.linearGradient
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leejang.sleeptandard.Component.CustomTimePicker
import com.leejang.sleeptandard.Component.DiamondStepSlider
import com.leejang.sleeptandard.ui.theme.AppIcons
import com.leejang.sleeptandard.ui.theme.Pretandard

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

    val circleGradation = Brush.radialGradient(
        colors = listOf(
            Color(0x00F1F1F1),
            Color(0x1AF1F1F1)
        )
    )

    val buttonGradient = linearGradient(
        listOf(Color(0xFF437AC7),
            Color(0xFFAFF4F9))
    )

    var currentPage by remember { mutableIntStateOf(0) }
    val maxPage = 4 // 0: 시작, 1: 알람설정, 2: 취침, 3: 피드백, 4: 절전 상태 해제


    // ✅ 뒤로가기 제어 로직 추가
    // currentPage가 0보다 클 때만 이 핸들러가 동작합니다.
    BackHandler(enabled = currentPage > 0) {
        currentPage -= 1
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(linearGradation),
        horizontalAlignment = Alignment.CenterHorizontally
    ){

        Spacer(Modifier.height(60.dp))

        if(currentPage in 1..3){
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(55.dp)
                    .background(color = Color(0x1AFFFFFF), shape = RoundedCornerShape(50.dp))
                    .padding(4.dp)
            ){
                Row(modifier = Modifier.fillMaxSize()) {
                    listOf("알람설정", "취침", "피드백").forEachIndexed { index, title ->
                        val pageNum = index + 1
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50.dp))
                                // 현재 페이지일 때만 배경 브러시 적용
                                .background(
                                    if (currentPage == pageNum) circleGradation else Brush.linearGradient(
                                        listOf(Color.Transparent, Color.Transparent)
                                    )
                                )
                                .clickable { currentPage = pageNum },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 15.sp,
                                    color = if (currentPage == pageNum) Color.White else Color.White.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(40 .dp))

        // 2. 페이지 내용 (애니메이션 적용 영역)
        Box(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    fadeIn(tween(500)).togetherWith(fadeOut(tween(500))) },
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
                    currentPage += 1
                } else {
                    onFinish() // 마지막 페이지에서 누르면 홈으로 이동
                }
            },
            contentPadding = PaddingValues(0.dp)
        ){
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .fillMaxWidth(),
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
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(24.dp)) {

            val targetX = maxWidth * (51f / 72f) - 9.dp
            val targetY = maxHeight * (1f / 2f) - 6.dp

            // 1. 깜빡임(Pulse)을 위한 무한 애니메이션 설정
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f, // 가장 흐릴 때
                targetValue = 0.9f,  // 가장 선명할 때
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = LinearEasing), // 1.5초 간격
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha"
            )
            // 2. 드롭 쉐도우와 원을 겹치기 위한 Box
            Box(
                modifier = Modifier
                    .offset(x = targetX, y = targetY),
                contentAlignment = Alignment.Center) {

                // ✅ 레이어 1: 깜빡이는 드롭 쉐도우 (광원 효과)
                Box(
                    modifier = Modifier
                        .size(16.dp) // 원보다 약간 크게 설정하여 빛이 퍼지게 함
                        .graphicsLayer {
                            this.alpha = alpha // 애니메이션되는 투명도 적용
                            compositingStrategy = CompositingStrategy.Offscreen // 블러 성능 최적화
                        }
                        .blur(radius = 25.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                        .background(
                            // "알람의 정석" 메인 테마인 민트/블루 그라데이션
                            brush = Brush.radialGradient(listOf(Color(0xFF437AC7), Color(0xFFAAEDF2))),
                            shape = CircleShape
                        )
                        /*
                        .dropShadow(
                            shape = CircleShape,
                            shadow = Shadow(
                                radius = 12.dp,
                                spread = 1.dp,
                                color = Color(0xFFAFF4F9).copy(alpha),
                                offset = DpOffset(x = 0.dp, y = 0.dp)
                            )
                        )
                         */
                )

                // ✅ 레이어 2: 실제 12.dp 크기의 흰색 원
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color.White, CircleShape)
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
                .height(150.dp),
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
                durationMillis = 4000
                0f at 0 using LinearEasing
                1f at animationDuration using FastOutSlowInEasing
                1f at 4000
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
                durationMillis = 4000
                0f at 0
                0f at animationDuration + 1000
                1f at animationDuration + 1000 + animationDuration using FastOutSlowInEasing
                1f at 4000
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