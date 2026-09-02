package com.leejang.sleeptandard

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leejang.sleeptandard.ClassFile.AlarmPlayer
import com.leejang.sleeptandard.ClassFile.AlarmScheduler
import com.leejang.sleeptandard.Prefs.AlarmPreferences
import com.leejang.sleeptandard.ui.theme.Sleeptandard_MVP_DemoTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter

import com.leejang.sleeptandard.ui.theme.AppIcons
import kotlin.math.roundToInt

class AlarmRingActivity : ComponentActivity() {

    private var alarmId: Int = 0
    private var isAlarmFinishInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val alarmPrefs = AlarmPreferences(this)
        alarmId = intent.getIntExtra("alarmId", 0)
        Log.i(
            WTF_TAG,
            "AlarmRingActivity.onCreate: alarmId=$alarmId, savedInstanceState=${savedInstanceState != null}, " +
                "hasAlarm=${alarmPrefs.isAlarmSet()}, taskId=$taskId, pid=${android.os.Process.myPid()}"
        )
        // label = intent.getStringExtra("label") ?: "알람"

        setContent {
            Sleeptandard_MVP_DemoTheme {
                AlarmRingScreen(
                    // label = label,
                    onStop = {
                        Log.i(
                            WTF_TAG,
                            "알람 종료 UI 입력: stopAlarmAndFinish 호출 전, " +
                                "hasAlarm=${alarmPrefs.isAlarmSet()}"
                        )
                        stopAlarmAndFinish()
                    }
                )
            }
        }
    }

    private fun stopAlarmAndFinish() {
        if (isAlarmFinishInProgress) return
        isAlarmFinishInProgress = true

        val alarmPrefs = AlarmPreferences(this)
        Log.i(
            WTF_TAG,
            "stopAlarmAndFinish 시작: alarmId=$alarmId, hasAlarm=${alarmPrefs.isAlarmSet()}, " +
                "activity=${System.identityHashCode(this)}"
        )

        // 1) 소리/진동 정지
        AlarmPlayer.stop()
        Log.i(WTF_TAG, "알람이 종료되었음.")

        // 2) 알림 제거
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(alarmId)

        // 3) 정시 알람과 Potch 모니터링 시작 알람을 모두 정리
        try {
            val currentAlarm = alarmPrefs.loadAlarm()
            AlarmScheduler(this).cancel(currentAlarm)
            Log.i(
                WTF_TAG,
                "AlarmScheduler.cancel 완료: alarmId=${currentAlarm.id}, " +
                    "hasAlarm=${alarmPrefs.isAlarmSet()}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel Potch alarm reservations", e)
        }

        // MainActivity가 새 Intent를 처리하기 전에 알람 상태를 먼저 확정한다.
        alarmPrefs.setAlarmRinging(false)
        alarmPrefs.clearAlarm()
        Log.i(
            WTF_TAG,
            "리뷰 화면 이동 전 알람 상태 초기화 완료: " +
                "hasAlarm=${alarmPrefs.isAlarmSet()}, ringing=${alarmPrefs.isAlarmRinging()}"
        )

        // 4) MainActivity로 넘어가면서 알람 리뷰 화면에서 부터 시작하도록 요청
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("open_screen", "reviewAlarm")
            putExtra("startDestination", "reviewAlarm") // 기존 버전 호환
            addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        Log.i(
            WTF_TAG,
            "MainActivity 시작 요청: open_screen=${intent.getStringExtra("open_screen")}, " +
                "flags=${intent.flags}, hasAlarm=${alarmPrefs.isAlarmSet()}, taskId=$taskId"
        )
        startActivity(intent)
        Log.i(WTF_TAG, "MainActivity startActivity 반환, AlarmRingActivity.finish 호출")

        // 5) 화면 닫기
        finish()
    }

    override fun onDestroy() {
        Log.i(
            WTF_TAG,
            "AlarmRingActivity.onDestroy: alarmId=$alarmId, " +
                "hasAlarm=${AlarmPreferences(this).isAlarmSet()}, isFinishing=$isFinishing"
        )
        super.onDestroy()
    }
    
    companion object {
        private const val TAG = "AlarmRingActivity"
        private const val WTF_TAG = "WTF"
    }
}

@Composable
fun AlarmRingScreen(
    // label: String,
    sleepStage: String = "기상 골든타임에서",
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
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(240.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = currentTime,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 100.sp,
                    fontWeight = FontWeight(600),
                    color = Color.White
                )
            )

            Spacer(Modifier.height(18.dp))

            Text(
                text = "${sleepStage} 깨워드렸어요.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 16.sp,
                    color = Color(0xFFAFF4F9)
                )
            )
        }

        Spacer(modifier = Modifier.height(222.dp))


        SwipeToStopButton(
            onComplete = {
                onStop() },
            modifier = Modifier
                .fillMaxWidth()
        )
    }
}

@Composable
fun SwipeToStopButton(
    text: String = "피드백 하러가기",
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 80.dp, // 디자인에 맞춰 조정
    thumbSize: Dp = 72.dp, // 트랙 높이보다 약간 작게 설정하면 예쁩니다
    horizontalPadding: Dp = 4.dp, // 왼쪽 끝과의 간격
    completeThreshold: Float = 0.85f, // 85% 이상 밀면 성공
) {
    val density = LocalDensity.current
    val thumbPx = with(density) { thumbSize.toPx() }
    val padPx = with(density) { horizontalPadding.toPx() }

    var dragX by remember { mutableFloatStateOf(0f) }
    var completed by remember { mutableStateOf(false) }

    val trackGradient = Brush.horizontalGradient(
        listOf(
            Color(0xFF437AC7),
            Color(0xFFAFF4F9)
        )
    )

    val textGradient = Brush.horizontalGradient(
        listOf(
            Color(0xFFFFFFFF),
            Color(0xFF83B1BB)
        )
    )

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

        Box(
            modifier = Modifier
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .clip(RoundedCornerShape(100.dp))
                    .background(trackGradient)
                    .blur(30.dp)
                    .border(
                        width = 2.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.4f), // 테두리 위쪽 (빛남)
                                Color.Transparent,             // 테두리 중간 (투명)
                                Color.White.copy(alpha = 0.1f)  // 테두리 아래쪽 (은은함)
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
            ){}
            // 트랙 (배경)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .clip(RoundedCornerShape(100.dp))
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
                Text(
                    text = text,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 18.sp,
                        brush = textGradient
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
                ) {}
            }
        }




    }
}
