package com.leejang.sleeptandard.Screen

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leejang.sleeptandard.ui.theme.AppIcons
import java.util.Dictionary

@Composable
fun SettingsScreen(
    onClickQnA: ()-> Unit,
    onClickPermission: ()-> Unit,
    onClickTutorial: ()-> Unit,
    onClickSendingData: ()-> Unit
){

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)

    ){

        Spacer(Modifier.height(32.dp))

        Text("설정",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 20.sp
            ),
            modifier = Modifier
                .padding(start = 12.dp)
        )

        Spacer(Modifier.height(34.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth( )
                .height(132.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0x26F1F1F1),   // ✅ 여기로 이동
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ){
            Column(
                modifier = Modifier
                    .background(Color.Transparent)
                    .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onClickQnA() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(color = Color(0xFF465467), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            modifier = Modifier
                                .size(18.dp),
                            painter = painterResource(AppIcons.SettingsMail),
                            contentDescription = "고객 지원"
                        )
                    }

                    Spacer(Modifier.width(16.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        Text(
                            "고객 지원",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(Modifier.height(2.dp))

                        Text(
                            "문의하기",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 11.sp,
                                lineHeight = 20.sp,
                                color = Color(0x99F1F1F1),
                            )
                        )

                    }
                }


                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(modifier = Modifier.width(44.dp))
                    HorizontalDivider()
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onClickPermission() },
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(color = Color(0xFF465467), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            modifier = Modifier
                                .size(18.dp),
                            painter = painterResource(AppIcons.SettingsTool),
                            contentDescription = "시스템 접근권한"
                        )
                    }

                    Spacer(Modifier.width(16.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        Text(
                            "시스템 접근권한",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(Modifier.height(2.dp))

                        Text(
                            "설정",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 11.sp,
                                lineHeight = 20.sp,
                                color = Color(0x99F1F1F1),
                            )
                        )

                    }

                }

            }

        }

        Surface(
            modifier = Modifier
                .fillMaxWidth( )
                .height(71.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0x26F1F1F1),   // ✅ 여기로 이동
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(vertical = 16.dp, horizontal = 20.dp)
                    .clickable { onClickTutorial() }
            ) {
                Column(
                    modifier = Modifier
                        .padding(vertical = 5.dp)
                        .fillMaxHeight()
                        .background(Color.Transparent),
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(color = Color(0xFF465467), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            modifier = Modifier
                                .size(15.dp),
                            painter = painterResource(AppIcons.SettingsQuestion),
                            contentDescription = "튜토리얼"
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Text(
                        "튜토리얼",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(Modifier.height(2.dp))

                    Text(
                        "튜토리얼",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 11.sp,
                            lineHeight = 20.sp,
                            color = Color(0x99F1F1F1),
                        )
                    )
                }
            }

        }

        Surface(
            modifier = Modifier
                .fillMaxWidth( )
                .height(71.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0x26F1F1F1),   // ✅ 여기로 이동
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {

            Row(
                modifier = Modifier
                    .padding(vertical = 16.dp, horizontal = 20.dp)
                    .clickable { onClickSendingData() }
            ) {
                Column(
                    modifier = Modifier
                        .padding(vertical = 5.dp)
                        .fillMaxHeight()
                        .background(Color.Transparent),
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(color = Color(0xFF465467), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            modifier = Modifier
                                .size(18.dp),
                            painter = painterResource(AppIcons.SettingsActivity),
                            contentDescription = "수면데이터"
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Text(
                        "수면데이터 보내기",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(Modifier.height(2.dp))

                    Text(
                        "수면데이터 보내기",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 11.sp,
                            lineHeight = 20.sp,
                            color = Color(0x99F1F1F1),
                        )
                    )
                }
            }

        }

    }
}

@Composable
fun SettingSection(
    title: String,
    elementMap: Map<String, Int>
){

    // UI 요소 변수

    // 섹션 높이
    val sectionHeight = elementMap.size * 60

    // 흰색 그림자
    val highlightColor1 = Color(0xFFB9C8DF).copy(alpha = 0.15f)
    val blurRadius1 = 20.dp
    val offsetX1 = (-5).dp
    val offsetY1 = (-5).dp
    // 검은색 그림자
    val highlightColor2 = Color(0xFF020710).copy(alpha = 0.7f)
    val blurRadius2 = 15.dp
    val offsetX2 = (8).dp
    val offsetY2 = (8).dp

    var index by remember{ mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
    ){
        Text(
            modifier = Modifier.padding(10.dp),
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 16.sp,
                color = Color.White
            )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(sectionHeight.dp)
                .drawBehind {


                    drawIntoCanvas { canvas ->
                        val paint = Paint().asFrameworkPaint().apply {
                            color = highlightColor1.toArgb()
                            maskFilter = BlurMaskFilter(blurRadius1.toPx(), BlurMaskFilter.Blur.NORMAL)
                        }

                        canvas.nativeCanvas.drawRoundRect(
                            offsetX1.toPx(), offsetY1.toPx(),
                            size.width + offsetX1.toPx(), size.height + offsetY1.toPx(),
                            28.dp.toPx(), 28.dp.toPx(),
                            paint
                        )
                    }



                    drawIntoCanvas { canvas ->
                        val paint = Paint().asFrameworkPaint().apply {
                            color = highlightColor2.toArgb()
                            maskFilter = BlurMaskFilter(blurRadius2.toPx(), BlurMaskFilter.Blur.NORMAL)
                        }

                        canvas.nativeCanvas.drawRoundRect(
                            offsetX2.toPx(), offsetY2.toPx(),
                            size.width + offsetX2.toPx(), size.height + offsetY2.toPx(),
                            // 여기
                            28.dp.toPx(), 28.dp.toPx(),
                            paint
                        )
                    }

                    val gradient = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF07101E),
                            Color(0xFF101A2A)
                        ),
                        // 시작점을 박스의 정중앙(Center)으로 설정
                        start = Offset(size.width / 2, size.height / 2),
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
                    shape = RoundedCornerShape(28.dp),
                    shadow = Shadow(
                        radius = 25.dp,
                        spread = (-12).dp,
                        color = Color(0xFF030E1E).copy(0.8f),
                        offset = DpOffset(x = 5.dp, 6.dp)
                    )
                )
        ){
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center
            ) {
                elementMap.forEach { m ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Icon(
                            modifier = Modifier.size(25.dp),
                            painter = painterResource(m.value),
                            contentDescription = "icon",
                        )

                        Text(
                            modifier = Modifier.padding(start = 16.dp),
                            text = m.key,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 16.sp,
                                color = Color.White
                            )
                        )
                    }
                    if (elementMap.size > index){
                        HorizontalDivider(
                            modifier = Modifier.height(0.dp),
                            color = Color(0xFF2A3240))
                    }
                    index++
                }

            }

        }
    }

}