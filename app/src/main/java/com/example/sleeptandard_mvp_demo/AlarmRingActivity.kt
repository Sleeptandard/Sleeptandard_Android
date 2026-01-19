package com.example.sleeptandard_mvp_demo

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.example.sleeptandard_mvp_demo.ClassFile.AlarmPlayer
import com.example.sleeptandard_mvp_demo.ClassFile.AlarmReceiver
import com.example.sleeptandard_mvp_demo.Prefs.AlarmPreferences
import com.example.sleeptandard_mvp_demo.ViewModel.AlarmViewModel
import com.example.sleeptandard_mvp_demo.ui.theme.Sleeptandard_MVP_DemoTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter

import com.example.sleeptandard_mvp_demo.ui.theme.AlarmBackground
import com.example.sleeptandard_mvp_demo.ui.theme.AppIcons
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class AlarmRingActivity : ComponentActivity() {

    private var alarmId: Int = 0
    private var label: String = "알람"
    private lateinit var alarmViewModel: AlarmViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // ViewModel 초기화
        alarmViewModel = ViewModelProvider(this)[AlarmViewModel::class.java]

        val alarmPrefs = AlarmPreferences(this)
        alarmId = intent.getIntExtra("alarmId", 0)
        label = intent.getStringExtra("label") ?: "알람"

        setContent {
            Sleeptandard_MVP_DemoTheme {
                AlarmRingScreen(
                    label = label,
                    onStop = {
                        stopAlarmAndFinish()
                        try {
                            alarmPrefs.clearAlarm()
                        }catch (e: Exception){
                            Log.d("clearPrefs", "WTF: ${e}")
                        }

                    }
                )
            }
        }
    }

    private fun stopAlarmAndFinish() {
        // 1) 소리/진동 정지
        AlarmPlayer.stop()

        // 2) 알림 제거
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(alarmId)

        // 3) 백업 알람 취소 (스마트 알람이 먼저 울렸을 경우 목표 시각의 백업 알람을 제거)
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                alarmId, // 동일한 requestCode 사용
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            )
            
            // PendingIntent가 존재하면 취소
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                Log.i(TAG, "✅ Backup alarm cancelled for alarmId: $alarmId")
            } else {
                Log.d(TAG, "No pending alarm found for alarmId: $alarmId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel backup alarm", e)
        }

        // 4) 워치에 수면 추적 중지 명령 전송
        alarmViewModel.stopSleepTracking()
        Log.i(TAG, "Stop command sent to Watch")

        // 5) MainActivity로 넘어가면서 알람 리뷰 화면에서 부터 시작하도록 요청
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("startDestination", "reviewAlarm") // Screen.AfterAlarm.route 값
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }
        startActivity(intent)

        // 6) 화면 닫기
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 혹시 남아있을지 모를 소리/진동 정리
        AlarmPlayer.stop()
    }
    
    companion object {
        private const val TAG = "AlarmRingActivity"
    }
}

@Composable
fun AlarmRingScreen(
    label: String,
    sleepStage: String = "N1",
    onStop: () -> Unit
) {
    val currentTime = remember {
        LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    val linearGradation = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color(0xFF050C16),
            1f to Color(0xFF1C447C)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(linearGradation)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(238.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = currentTime,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 80.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight(500),
                    color = Color.White
                )
            )
            Icon(
                painter = painterResource(AppIcons.RingBar),
                contentDescription = "",
                tint = Color.White
            )

            Spacer(Modifier.height(18.dp ))

            Text(
                text = "${sleepStage} 단계에서 깨워드렸어요.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            )
        }

        Spacer(modifier = Modifier.height(250.dp))


        SwipeToStopButton(
            text = "피드백",
            onComplete = {
                onStop() },
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(56.dp)
        )
    }
}

@Composable
fun SwipeToStopButton(
    text: String,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp, // 디자인에 맞춰 조정
    thumbSize: Dp = 48.dp, // 트랙 높이보다 약간 작게 설정하면 예쁩니다
    horizontalPadding: Dp = 4.dp, // 왼쪽 끝과의 간격
    completeThreshold: Float = 0.85f, // 85% 이상 밀면 성공
) {
    val density = LocalDensity.current
    val thumbPx = with(density) { thumbSize.toPx() }
    val padPx = with(density) { horizontalPadding.toPx() }

    var dragX by remember { mutableFloatStateOf(0f) }
    var completed by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier) {
        val trackWidthPx = with(density) { maxWidth.toPx() }
        // 실제 이동 가능한 최대 거리는 (전체 너비 - 썸 너비 - 양쪽 패딩)
        val maxDrag = (trackWidthPx - thumbPx - (padPx * 2)).coerceAtLeast(0f)

        val animatedX by animateFloatAsState(
            targetValue = dragX,
            label = "thumbX",
            // 성공 시 애니메이션 없이 즉시 이동, 실패 시 부드럽게 복귀
            animationSpec = androidx.compose.animation.core.spring()
        )

        // 트랙 (배경)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(Color.White.copy(alpha = 0.18f))
                .pointerInput(maxDrag, completed) {
                    if (completed) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragX >= maxDrag * completeThreshold) {
                                dragX = maxDrag
                                completed = true
                                onComplete()
                            } else {
                                dragX = 0f // 실패 시 왼쪽으로 복귀
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            // 0부터 maxDrag 사이로 드래그 제한
                            dragX = (dragX + dragAmount).coerceIn(0f, maxDrag)
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart // 기본 정렬을 왼쪽 시작으로 고정
        ) {
            // "피드백" 텍스트 (중앙 배치)
            Text(
                text = text,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 18.sp,
                    color = Color.White
                )
            )

            // Thumb (움직이는 흰 원)
            Box(
                modifier = Modifier
                    .padding(start = horizontalPadding) // 초기 고정 위치
                    .offset { IntOffset(animatedX.roundToInt(), 0) } // 드래그 시 이동량
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(AppIcons.ArrowRight),
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
