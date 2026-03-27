package com.leejang.sleeptandard.Screen

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.sp
import com.leejang.sleeptandard.ClassFile.QnAItem
import com.leejang.sleeptandard.ui.theme.AppIcons


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QnADetailScreen(
    item: QnAItem,
    onBack: () -> Unit,
    onClickAskDeveloper: () -> Unit = {}
) {
    // 1. 스크롤 상태 기억
    val scrollState = androidx.compose.foundation.rememberScrollState()
    val scrollState2 = androidx.compose.foundation.rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .verticalScroll(scrollState), // ✅ 스크롤 활성화
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(0.11f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            verticalAlignment = Alignment.CenterVertically,
        ){
            IconButton(
                modifier = Modifier.size(32.dp),
                onClick = onBack
            ) {
                Icon(
                    modifier = Modifier.size(32.dp),
                    painter = painterResource(AppIcons.QnAArrowBack),
                    contentDescription = "뒤로가기",
                )
            }

            Spacer(Modifier.width(10.dp))
            // 제목 (질문 타이틀)
            Text(
                text = item.title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp)
            )
        }

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 41.5f.dp)
        ){
            // 질문 본문
            Text(
                text = item.question,
                color = Color(0xCCF1F1F1),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 16.sp
                ),
            )
        }

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(260.dp)
                .drawBehind {
                    // 둥글기
                    val cornerRadius = 28.dp

                    // 흰색 그림자
                    val highlightColor1 = Color(0xFFB9C8DF).copy(alpha = 0.15f)
                    val blurRadius1 = 20.dp.toPx()
                    val offsetX1 = (-5).dp.toPx()
                    val offsetY1 = (-5).dp.toPx()

                    drawIntoCanvas { canvas ->
                        val paint = Paint().asFrameworkPaint().apply {
                            color = highlightColor1.toArgb()
                            maskFilter = BlurMaskFilter(blurRadius1, BlurMaskFilter.Blur.NORMAL)
                        }

                        canvas.nativeCanvas.drawRoundRect(
                            offsetX1, offsetY1,
                            size.width + offsetX1, size.height + offsetY1,
                            cornerRadius.toPx(), cornerRadius.toPx(),
                            paint
                        )
                    }

                    // 검은색 그림자
                    val highlightColor2 = Color(0xFF020710).copy(alpha = 0.9f)
                    val blurRadius2 = 15.dp.toPx()
                    val offsetX2 = (8).dp.toPx()
                    val offsetY2 = (8).dp.toPx()

                    drawIntoCanvas { canvas ->
                        val paint = Paint().asFrameworkPaint().apply {
                            color = highlightColor2.toArgb()
                            maskFilter = BlurMaskFilter(blurRadius2, BlurMaskFilter.Blur.NORMAL)
                        }

                        canvas.nativeCanvas.drawRoundRect(
                            offsetX2, offsetY2,
                            size.width + offsetX2, size.height + offsetY2,
                            cornerRadius.toPx(), cornerRadius.toPx(),
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
                        // 끝점을 박스의 우측 하단(BottomEnd)으로 설정
                        end = Offset(size.width, size.height * 2 / 3)
                    )
                    drawRoundRect(
                        brush = gradient,
                        cornerRadius = CornerRadius(
                            cornerRadius.toPx(),
                            cornerRadius.toPx()
                        ) // 30dp만큼 둥글게
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
                ),

        ){
            Column(modifier = Modifier
                .padding(21.dp)
                .verticalScroll(scrollState2)
            ) {
                Text(
                    "담당자 답변",
                    color = Color(0xE5F1F1F1),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp)
                )
                Spacer(Modifier.height(20.dp))

                Text(
                    text = item.answer,
                    color = Color(0xE5F1F1F1),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(Modifier.weight(1f))

    }

}