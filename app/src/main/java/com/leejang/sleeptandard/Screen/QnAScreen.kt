package com.leejang.sleeptandard.Screen

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface

import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leejang.sleeptandard.ClassFile.QnARepository
import com.leejang.sleeptandard.ui.theme.AppIcons


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QnAScreen(
    onBack: () -> Unit = {},
    onClickAsk: () -> Unit = {},
    onClickItem: (String) -> Unit = {}
) {
    // 사용자가 입력하는 값
    var query by remember { mutableStateOf("") }

    // FAQ 리스트
    val allItems = remember {
        QnARepository.items
    }

    // 필터링 하는 곳
    val filtered = remember(query, allItems) {
        val q = query.trim()
        if (q.isEmpty()) allItems
        else allItems.filter { it.title.contains(q, ignoreCase = true) }
    }

    // 흰색 그림자
    val highlightColor1 = Color(0xFFB9C8DF).copy(alpha = 0.07f)
    val blurRadius1 = 25.dp
    val offsetX1 = (-5).dp
    val offsetY1 = (-5).dp
    // 검은색 그림자
    val highlightColor2 = Color(0xFF020710).copy(alpha = 0.7f)
    val blurRadius2 = 15.dp
    val offsetX2 = (8).dp
    val offsetY2 = (8).dp

    Scaffold(
        containerColor = Color(0xFF0B111A),
        topBar = {
            TopAppBar(
                modifier = Modifier.padding(20.dp),
                title = {},
                navigationIcon = {
                    IconButton(
                        modifier = Modifier.size(32.dp),
                        onClick = onBack) {
                        Icon(
                            modifier = Modifier
                                .size(32.dp),
                            painter = painterResource(AppIcons.QnAArrowBack),
                            contentDescription = "뒤로가기",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(30.dp))
            // 검색창
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .drawBehind {
                        // 흰색 그림자
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
                        // 검은색 그림자
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
                ,
                placeholder = {
                    Text(
                        "키워드를 검색해보세요.",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },

                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,        // ✅ 입력 텍스트 크기
                    lineHeight = 18.sp       // ✅ 중요: height보다 작게
                ),

                leadingIcon = {
                    Icon(
                        painter = painterResource(AppIcons.QnASearch),
                        contentDescription = "검색",
                        tint = Color.White
                    )
                },

                singleLine = true,

                shape = RoundedCornerShape(10.dp),

                colors = OutlinedTextFieldDefaults.colors(
                    //focusedContainerColor = Color(0x26F1F1F1),
                    //unfocusedContainerColor = Color(0x26F1F1F1),
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = Color.White,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Column(
                modifier = Modifier.padding(horizontal = 10.dp)
            ){
                Spacer(Modifier.height(39.dp))

                Text(
                    "자주 묻는 질문",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 16.sp,
                    )
                )

                Spacer(Modifier.height(24.dp))

                // 리스트
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(filtered) { item ->
                        QnAListItem(
                            text = item.title,
                            onClick = { onClickItem(item.id) } // ✅ id 넘김
                        )
                    }
                }
            }

            Spacer(Modifier.height(100.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Button(
                    onClick = onClickAsk,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF1F4F9),
                        contentColor = Color.Black
                    )
                ) {
                    Text(
                        text = "문의하기",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.Black,
                            fontSize = 18.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun QnAListItem(
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 왼쪽 Q 아이콘(텍스트로 표현)
        Surface(
            color = Color.Transparent
        ) {
            Icon(
                modifier = Modifier.size(17.dp, 20.dp),
                painter = painterResource(AppIcons.QnAQ),
                contentDescription = "Q"
            )
        }

        Spacer(Modifier.width(14.dp))

        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 16.sp,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}