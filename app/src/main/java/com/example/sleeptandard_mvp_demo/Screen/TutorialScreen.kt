package com.example.sleeptandard_mvp_demo.Screen

import android.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sleeptandard_mvp_demo.Component.CustomTimePicker
import com.example.sleeptandard_mvp_demo.ui.theme.AppIcons
import com.example.sleeptandard_mvp_demo.ui.theme.Pretandard

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
    val transparentBrush = Brush.radialGradient(
        colors = listOf(
            Color.Transparent
        )
    )

    var currentPage by remember { mutableIntStateOf(0) }
    val maxPage = 3 // 0: 시작, 1: 알람설정, 2: 취침, 3: 피드백


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(linearGradation),
        horizontalAlignment = Alignment.CenterHorizontally
    ){

        Spacer(Modifier.height(60.dp))

        if(currentPage != 0){
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
        Spacer(Modifier.height(40.dp))

        // 2. 페이지 내용 (currentPage에 따라 다른 컴포저블 호출)
        Box(modifier = Modifier.weight(1f)) {
            when (currentPage) {
                0 -> StartPage()
                1 -> AlarmSettingPart()
                2 -> AlarmSettedPart()
                3 -> FeedbackPart()
            }
        }


        // 3. 하단 버튼
        Button(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(56.dp)
                .padding(bottom = 8.dp),
            shape = RoundedCornerShape(100.dp),
            // Material 3 버튼은 background 수정자 대신 colors 파라미터를 사용해야 안전합니다.
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0x1AFFFFFF),
                contentColor = Color.White
            ),
            onClick = {
                if (currentPage < maxPage) {
                    currentPage += 1
                } else {
                    onFinish() // 마지막 페이지에서 누르면 홈으로 이동
                }
            }
        ) {
            Text(
                text = if (currentPage < maxPage) "다음" else "시작하기",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 18.sp)
            )
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
                    fontFamily = Pretandard,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
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
        Image(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            painter = painterResource(AppIcons.TutorialGraph),
            contentDescription = "그래프",
            contentScale = ContentScale.Fit
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(150f)
        ) {
            Spacer(Modifier.height(110.dp))
            Text(
                modifier = Modifier
                    .padding(start = 32.dp),
                text = "당신의 가장 얕은 수면을 감지하여",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                modifier = Modifier
                    .padding(start = 32.dp),
                text = "가볍게 깨워드려요",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(60.dp))
        }
    }




}
@Composable
fun AlarmSettingPart(){
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        Spacer(Modifier.weight(3f))

        Text("몇 시 이전에는 꼭 일어나야 하나요?",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 16.sp
            ))

        Spacer(Modifier.weight(8f))

        CustomTimePicker(
            onTimeChange = {h,m,ampm->{}},
            scrollEnable = false,
            itemHeight = 68.dp,
            itemHeightAmPm = 52.dp,
            defaultHour12 = 6,
            defaultIsAm = true,
            defaultMinute = 0,
        )
        
        Spacer(Modifier.weight(14f))

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
            Row {
                Text(
                    text = "설정 시간내 ",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 16.sp
                    ))
                Text(
                    text ="가장 얕은 수면",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF8DF1E2),
                        fontSize = 18.sp
                    ))
                Text(
                    text = "에서 깨워드려요.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 16.sp
                    )
                )
            }
            Row{
                Text(
                    text = "수면 측정을 위해, ",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 16.sp
                    )
                )
                Text(
                    text = "워치",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 18.sp
                    )
                )
                Text(
                    text = "를 착용해주세요.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 16.sp
                    )
                )

            }

        }
        
        Spacer(Modifier.weight(75f))

        Image(
            modifier = Modifier.padding(horizontal = 78.dp),
            painter = painterResource(AppIcons.Tutorial30Min),
            contentDescription = "30분 전부터 깨워준다는 그림",
            contentScale = ContentScale.Fit
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
             Row{
                 Text(
                     text = "당신의 ",
                     style = MaterialTheme.typography.bodyMedium.copy(
                         fontSize = 16.sp
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
                         fontSize = 16.sp
                     )
                 )
             }
             Text("맞춤형 알고리즘이 생성돼요.")
         }
         
         Spacer(Modifier.weight(40f))
         
         Row(
             modifier = Modifier.fillMaxWidth()
         ) {
             Spacer(Modifier.weight(75f))
             Image(
                 modifier = Modifier.weight(210f),
                 painter = painterResource(AppIcons.TutorialFeedback),
                 contentDescription = "피드백 이미지",
                 contentScale = ContentScale.Fit
             )
             Spacer(Modifier.weight(75f))
         }
         
         
         
         Spacer(Modifier.weight(60f))
     }
}