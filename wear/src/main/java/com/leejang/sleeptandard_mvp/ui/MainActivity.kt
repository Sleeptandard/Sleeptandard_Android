package com.leejang.sleeptandard_mvp.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.wear.compose.material.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.wear.compose.material.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Icon
import androidx.wear.tooling.preview.devices.WearDevices
import com.leejang.sleeptandard_mvp.wear.R


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearHomeScreen()
        }
    }
}

// 이미지에서 추출한 색상 정의
val DarkBackgroundColor = Color(0xFF0D1117) // 매우 어두운 남색 배경
val BlueAccentColor = Color(0xFF336699) // 파란색 텍스트 색상

@Composable
fun WearHomeScreen() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackgroundColor), // 배경색 설정
            contentAlignment = Alignment.Center // 내용을 중앙에 배치
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(130f))

                Icon(
                    painter = painterResource(id = R.drawable.branding_watch), // 실제 아이콘 사용 시 주석 해제
                    contentDescription = "앱 아이콘",
                    tint = Color.Unspecified
                )

                Spacer(modifier = Modifier.weight(54f))

                // 하단: 안내 메시지
                Text(
                    text = "폰을 확인하세요",
                    color = Color.White,
                    fontSize = 16.sp
                )

                Spacer(Modifier.weight(98f))
            }
        }
    }
}

// 안드로이드 스튜디오 미리보기용
@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
fun WearHomeScreenPreview() {
    WearHomeScreen()
}