package com.leejang.sleeptandard_mvp.Screen

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.leejang.sleeptandard_mvp.ui.theme.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@Composable
fun SendingDataScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var logFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var totalSizeMB by remember { mutableStateOf(0.0) }
    var pendingFilesToDelete by remember { mutableStateOf<List<File>>(emptyList()) }

    // 공유 완료 후 파일 삭제를 위한 ActivityResultLauncher
    val shareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // 공유 인텐트가 종료된 후 실행
        if (pendingFilesToDelete.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                var deletedCount = 0
                pendingFilesToDelete.forEach { file ->
                    if (file.exists() && file.delete()) {
                        deletedCount++
                    }
                }
                
                withContext(Dispatchers.Main) {
                    if (deletedCount > 0) {
                        Toast.makeText(
                            context,
                            "✅ $deletedCount 개 파일 삭제 완료",
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        // 파일 목록 새로고침
                        isLoading = true
                    }
                }
                
                // 파일 목록 다시 로드
                logFiles = context.filesDir.listFiles { file ->
                    file.name.startsWith("received_") && file.name.endsWith(".csv")
                }?.sortedByDescending { it.lastModified() } ?: emptyList()
                
                totalSizeMB = logFiles.sumOf { it.length() } / (1024.0 * 1024.0)
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
            pendingFilesToDelete = emptyList()
        }
    }

    // 파일 목록 로드
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            logFiles = context.filesDir.listFiles { file ->
                file.name.startsWith("received_") && file.name.endsWith(".csv")
            }?.sortedByDescending { it.lastModified() } ?: emptyList()
            
            totalSizeMB = logFiles.sumOf { it.length() } / (1024.0 * 1024.0)
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // 헤더
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically

        ) {
            IconButton(
                modifier = Modifier.size(40.dp),
                onClick = onBack
            ) {
                Icon(
                    painter = painterResource(AppIcons.QnAArrowBack),
                    contentDescription = "뒤로 가기"
                )
            }
            
            Spacer(Modifier.weight(1f))
            
            Text(
                "수면 데이터 보내기",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp)
            )
            
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(40.dp)) // 대칭을 위한 공간
        }

        Spacer(Modifier.height(16.dp))

        if (isLoading) {
            // 로딩 중
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (logFiles.isEmpty()) {
            // 파일 없음
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "전송 가능한 로그 파일이 없습니다",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0x99F1F1F1)
                    )
                    Text(
                        "워치에서 알람을 사용한 후\n데이터를 전송해주세요",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0x66F1F1F1),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // 파일 목록 및 전송 버튼
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 통계 정보
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x26F1F1F1)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "파일 개수",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0x99F1F1F1)
                            )
                            Text(
                                "${logFiles.size}개",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "전체 크기",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0x99F1F1F1)
                            )
                            Text(
                                "%.2f MB".format(totalSizeMB),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 파일 목록
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(logFiles) { file ->
                        FileItemCard(file)
                    }
                }

                Spacer(Modifier.height(24.dp))

                // 전송 버튼
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .background(color = Color(0xFF465467), shape = CircleShape)
                                .clickable {
                                    // 공유 전 삭제 대상 파일 저장
                                    pendingFilesToDelete = logFiles
                                    // 공유 인텐트 실행
                                    shareLogFilesWithResult(context, logFiles, shareLauncher)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(AppIcons.SendingDataSend),
                                contentDescription = "데이터 보내기",
                                tint = Color.White
                            )
                        }
                        Text(
                            "공유하기",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun FileItemCard(file: File) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0x1AF1F1F1)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = file.name.removePrefix("received_"),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatFileInfo(file),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = Color(0x99F1F1F1)
                )
            }
            
            Text(
                text = "%.1f MB".format(file.length() / (1024.0 * 1024.0)),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = Color(0xFFE0F5FD)
            )
        }
    }
}

private fun formatFileInfo(file: File): String {
    val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
    val date = dateFormat.format(Date(file.lastModified()))
    return "수신: $date"
}

private fun shareLogFilesWithResult(
    context: android.content.Context, 
    files: List<File>,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    try {
        if (files.isEmpty()) {
            Toast.makeText(context, "공유할 파일이 없습니다", Toast.LENGTH_SHORT).show()
            return
        }

        val uris = files.map { file ->
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/csv"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            putExtra(Intent.EXTRA_SUBJECT, "Sleep Log Data from SleepTandard")
            putExtra(
                Intent.EXTRA_TEXT,
                "수면 데이터 로그 파일입니다.\n파일 개수: ${files.size}\n전체 크기: %.2f MB".format(
                    files.sumOf { it.length() } / (1024.0 * 1024.0)
                )
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "로그 파일 공유 (공유 후 자동 삭제됨)")
        launcher.launch(chooser)
        
    } catch (e: Exception) {
        Toast.makeText(context, "공유 실패: ${e.message}", Toast.LENGTH_LONG).show()
    }
}