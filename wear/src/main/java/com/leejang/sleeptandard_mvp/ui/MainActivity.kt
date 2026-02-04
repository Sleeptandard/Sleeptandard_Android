package com.leejang.sleeptandard_mvp.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Icon
import androidx.wear.tooling.preview.devices.WearDevices
import com.leejang.sleeptandard_mvp.backend.manager.LogFileTransferManager
import com.leejang.sleeptandard_mvp.wear.R
import kotlinx.coroutines.launch


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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
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

                Spacer(Modifier.weight(30f))
                
                // [참고] 알람 종료 시 자동 전송되므로 이 버튼은 재전송/수동 전송용
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 로그 재전송 버튼 (수동)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFF336699), shape = CircleShape)
                            .clickable {
                                scope.launch {
                                    Toast.makeText(context, "로그 재전송 중...", Toast.LENGTH_SHORT).show()
                                    
                                    val transferManager = LogFileTransferManager(context)
                                    val result = transferManager.sendLatestLogsToPhone()
                                    
                                    result.onSuccess { count ->
                                        Toast.makeText(
                                            context,
                                            "✅ $count 개 파일 재전송 완료",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }.onFailure { error ->
                                        Toast.makeText(
                                            context,
                                            "❌ 재전송 실패: ${error.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = "로그 재전송 (수동)",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    
                    // 로그 상태 확인 버튼
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFF555555), shape = CircleShape)
                            .clickable {
                                val transferManager = LogFileTransferManager(context)
                                val stats = transferManager.getLogFileStats()
                                
                                if (stats.fileCount == 0) {
                                    Toast.makeText(
                                        context,
                                        "로그 파일 없음\n(알람 종료 시 자동 전송됨)",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "📊 ${stats.fileCount}개 파일 (%.1f MB)".format(stats.totalSizeMB),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_info_details),
                            contentDescription = "로그 정보",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(Modifier.weight(68f))
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