package com.example.sleeptandard_mvp_demo.Screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.sp
import com.example.sleeptandard_mvp_demo.ClassFile.QnAItem
import com.example.sleeptandard_mvp_demo.ui.theme.AppIcons


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QnADetailScreen(
    item: QnAItem,
    onBack: () -> Unit,
    onClickAskDeveloper: () -> Unit = {}
) {
    Scaffold(
        containerColor = Color(0xFF0B111A),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(AppIcons.QnAArrowBack),
                            contentDescription = "뒤로가기",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(12.dp))

            // 제목 (질문 타이틀)
            Text(
                text = item.title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp)
            )

            Spacer(Modifier.height(50.dp))

            // 질문 본문
            Text(
                text = item.question,
                color = Color(0xCCF1F1F1),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(51.dp))

            // 담당자 답변 섹션(구분선/블록)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        "담당자 답변",
                        color = Color(0xE5F1F1F1),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = item.answer,
                        color = Color(0xE5F1F1F1),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(Modifier.height(64.dp))

            // 하단 버튼(바텀바와 별개로 위에 뜨는 버튼)
            Button(
                onClick = onClickAskDeveloper,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(bottom = 16.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1B2432),
                    contentColor = Color.White
                )
            ) {
                Text("개발자에게 1:1 문의하기")
            }
        }
    }
}