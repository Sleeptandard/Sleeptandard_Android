package com.example.sleeptandard_mvp_demo.Screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.example.sleeptandard_mvp_demo.ui.theme.AppIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InquireScreen(
    onBack: () -> Unit = {},
    onPickImage: () -> Unit = {},   // ✅ 사진 첨부 클릭 시(기능은 보류)
    onSubmit: (title: String, body: String) -> Unit = { _, _ -> } // ✅ 제출 클릭 시(기능은 보류)
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    val fieldBg = Color(0x40F1F1F1)     // 아주 옅은 회색(알파)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(AppIcons.QnAArrowBack),
                            contentDescription = "뒤로가기",
                            tint = Color.White
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
                .padding(horizontal = 26.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // ---- 제목 ----
            Text(
                text = "제목",
                modifier = Modifier.padding(8.dp),
                color = Color(0xCCF1F1F1),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp)
            )
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= 30) title = it }, // ✅ 30자 제한
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                placeholder = {
                    Text(
                        "30자 이내로 입력해주세요.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    color = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = fieldBg,
                    unfocusedContainerColor = fieldBg,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = Color.White,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(Modifier.height(22.dp))

            // ---- 내용 ----
            Text(
                "내용",
                color = Color(0xCCF1F1F1),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp)
            )
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                placeholder = {
                    Text(
                        "질문할 내용을 작성해주세요.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp
                        )
                    )
                },
                singleLine = false,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = fieldBg,
                    unfocusedContainerColor = fieldBg,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = Color.White,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(Modifier.height(22.dp))

            // ---- 사진 첨부 ----
            Text(
                "사진 첨부",
                color = Color(0xCCF1F1F1),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp)
            )
            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(fieldBg, RoundedCornerShape(12.dp))
                    .clickable { onPickImage() }, // ✅ 기능은 콜백만
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "+",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            Spacer(Modifier.height(60.dp))

            // ---- 제출 버튼 (하단 고정 느낌) ----
            Button(
                onClick = {
                    // TODO: 서버/이메일/관리자에게 전송 기능 연결 예정
                    onSubmit(title, body)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(100.dp),

                enabled = title.isNotBlank() && body.isNotBlank() // ✅ 입력 없으면 비활성
            ) {
                Text("제출",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 18.sp))
            }
        }
    }
}