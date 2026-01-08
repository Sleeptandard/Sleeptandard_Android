package com.example.sleeptandard_mvp_demo.Screen

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sleeptandard_mvp_demo.ClassFile.QnARepository
import com.example.sleeptandard_mvp_demo.ui.theme.AppIcons


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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Button(
                    onClick = onClickAsk,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1B2432),
                        contentColor = Color.White
                    )
                ) {
                    Text("문의하기")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 30.dp)
        ) {
            // 검색창
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                placeholder = {
                    Text(
                        "키워드를 검색해보세요.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            color = Color(0xCCF1F1F1),
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
                        tint = Color(0xCCF1F1F1)
                    )
                },

                singleLine = true,

                shape = MaterialTheme.shapes.large,

                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0x26F1F1F1),
                    unfocusedContainerColor = Color(0x26F1F1F1),
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = Color.White,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(Modifier.height(39.dp))

            Text(
                "자주 묻는 질문",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                )
            )

            Spacer(Modifier.height(24.dp))

            // 리스트
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(filtered) { item ->
                    QnAListItem(
                        text = item.title,
                        onClick = { onClickItem(item.id) } // ✅ id 넘김
                    )
                    Spacer(Modifier.height(28.dp))
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
            .height(20.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 왼쪽 Q 아이콘(텍스트로 표현)
        Surface(
            color = Color.Transparent
        ) {
            Icon(
                painter = painterResource(AppIcons.QnAQ),
                contentDescription = "Q"
            )
        }

        Spacer(Modifier.width(14.dp))

        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 15.sp,
                lineHeight = 20.sp,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}