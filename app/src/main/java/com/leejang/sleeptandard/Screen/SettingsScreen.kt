package com.leejang.sleeptandard.Screen

import android.graphics.BlurMaskFilter
import android.util.Log
import androidx.compose.foundation.Image
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

data class SettingElements(
    val id: Int,
    val name: String,
    val iconID: Int,
    val onClick: () -> Unit
)

@Composable
fun SettingsScreen(
    onClickQnA: ()-> Unit,
    onClickPermission: ()-> Unit,
    onClickTutorial: ()-> Unit,
    onClickSendingData: ()-> Unit,
    onClickAccount: () -> Unit = {}
){
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),

    ){

        Spacer(Modifier.height(32.dp))

        Text("설정",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 20.sp
            ),
            modifier = Modifier
                .padding(start = 12.dp)
        )

        Spacer(Modifier.height(32.dp))

        /****  계정 설정 비활성화  ****/
        /*
        SettingSection(
            title = "계정",
            elementList = listOf(
                SettingElements(1, "계정관리", AppIcons.SettingsAccountManagement, {})
            )
        )

         */

        Spacer(Modifier.height(30.dp))

        SettingSection(
            title = "앱 설정",
            elementList = listOf(
                SettingElements(id = 1, name = "시스템 접근권한", iconID = AppIcons.SettingsSysAccessibility, onClick = onClickPermission),
            )
        )

        Spacer(Modifier.height(30.dp))

        SettingSection(
            title = "도움",
            elementList = listOf(
                SettingElements(1, "튜토리얼", AppIcons.SettingsTutorial, onClickTutorial),
                SettingElements(2, "고객지원", AppIcons.SettingsCustomerService, onClickQnA)
            )
        )

        Spacer(Modifier.height(30.dp))

        SettingSection(
            title = "데이터",
            elementList = listOf(
                SettingElements(id = 1, name = "수면데이터 보내기", iconID = AppIcons.SettingsDataSending, onClick = onClickSendingData)
            )
        )
    }
}



@Composable
fun SettingSection(
    title: String,
    elementList: List<SettingElements>
){
    // UI 요소 변수

    // 섹션 높이
    val sectionHeight = elementList.size * 60

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
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp)),
                verticalArrangement = Arrangement.Center
            ) {
                elementList.forEach { l ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable{
                                l.onClick()
                            },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ){
                            Image(
                                modifier = Modifier.size(25.dp),
                                painter = painterResource(l.iconID),
                                contentDescription = "icon",
                            )

                            Text(
                                modifier = Modifier.padding(start = 16.dp),
                                text = l.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                            )
                        }

                    }
                    if (elementList.size > l.id){
                        HorizontalDivider(
                            modifier = Modifier.height(0.dp).padding(horizontal = 16.dp),
                            color = Color(0xFF2A3240))
                    }
                }

            }

        }
    }

}