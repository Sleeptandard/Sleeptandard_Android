package com.example.sleeptandard_mvp_demo.ClassFile


import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.sleeptandard_mvp_demo.AlarmRingActivity
import com.example.sleeptandard_mvp_demo.R
import androidx.core.net.toUri

private const val ALARM_CHANNEL_ID = "alarm_channel"

// 소리/진동을 Activity에서도 끌 수 있도록 전역으로 관리하는 객체
object AlarmPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    fun start(context: Context, ringtoneUriString: String?, vibrationEnabled: Boolean, volume: Int) {

        // AlarmReceiver.kt 의 AlarmPlayer.start 내부
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // 시스템의 알람 볼륨을 적절한 수준(예: 최대의 70%)으로 먼저 맞춘 뒤 재생
        val systemMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, systemMax, 0)

        stop() // ✅ 기존에 울리고 있다면 중지하고 새로 시작

        // 1. 🔔 소리 재생 로직 수정
        // ringtoneUriString이 비어있지 않을 때만 MediaPlayer를 초기화하고 재생합니다.
        if (!ringtoneUriString.isNullOrEmpty()) {
            try {
                val uri = Uri.parse(ringtoneUriString)
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(context, uri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()

                    val volumeRatio = volume.toFloat() / 15f
                    setVolume(volumeRatio, volumeRatio)
                    start()
                }
            } catch (e: Exception) {
                Log.e("AlarmPlayer", "알람음 재생 실패: ${e.message}")
                // 예외 발생 시에도 기본음을 울리지 않으려면 여기서 아무것도 하지 않습니다.
            }
        } else {
            Log.d("AlarmPlayer", "무음 설정됨: 소리 재생을 건너뜁니다.")
        }

        // 2. 📳 진동 (기존 로직 유지)
        // AlarmPlayer.start 내부의 진동 로직 수정
        if (vibrationEnabled) {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(VibratorManager::class.java)
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            // 알람 전용 속성 설정
            val alarmAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM) // 알람 용도로 명시
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(
                    longArrayOf(0, 1000, 500, 1000),
                    intArrayOf(0, 255, 0, 255),
                    0 // 반복
                )
                // attributes를 함께 전달하여 시스템 설정을 우회
                vibrator?.vibrate(effect, alarmAttributes)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 600, 400), 0)
            }
        }
    }

    fun stop() {
        // ✅ 소리 정지 및 메모리 해제
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        // ✅ 진동 정지 및 초기화
        vibrator?.cancel()
        vibrator = null
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 알람 정보 (없으면 기본값 사용)
        val label = intent.getStringExtra("label") ?: "알람"
        val ringtoneUriString = intent.getStringExtra("ringtoneUri")
        val vibrationEnabled = intent.getBooleanExtra("vibrationEnabled", true)
        val alarmId = intent.getIntExtra("alarmId", 0)
        val volume = intent.getIntExtra("volume", 10)

        // 1) 소리/진동 시작 (Activity가 안 떠도 최소한 울리게)
        AlarmPlayer.start(context, ringtoneUriString, vibrationEnabled, volume)

        // 2) 알람 채널 생성
        createAlarmChannel(context)

        // 3) 전체화면으로 띄울 Activity 인텐트
        val fullScreenIntent = Intent(context, AlarmRingActivity::class.java).apply {
            putExtra("alarmId", alarmId)
            putExtra("label", label)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            alarmId,
            fullScreenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 4) 사용자가 알림을 탭했을 때 열리는 contentIntent 도 같이 설정
        val contentPendingIntent = fullScreenPendingIntent

        // 5) Notification 빌드 (ALARM 카테고리 + HIGH / fullScreenIntent)
        val notification = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)  // 프로젝트 아이콘으로 바꿔도 됨
            .setContentTitle(label)
            .setContentText("알람이 울리고 있어요")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true) // 스와이프로 안 없애지게

            // ✅ 알림음 무음 및 기본 설정(소리) 제거
            .setSound(null)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE) // 진동만 기본값 사용 혹은 0으로 설정

            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPendingIntent)
            // 🔥 여기서 full-screen 요청 (USE_FULL_SCREEN_INTENT + 사용자 설정 ON일 때 동작)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(alarmId, notification)
    }

    private fun createAlarmChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                ALARM_CHANNEL_ID,
                "알람 채널",
                NotificationManager.IMPORTANCE_HIGH   // 🔥 HIGH 채널
            ).apply {
                description = "알람이 울릴 때 사용하는 채널입니다."
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC

                // ✅ 알림음 무음 처리
                setSound(null, null)

                // ✅ 채널 자체에 진동 활성화 및 패턴 설정
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 400)

                // ✅ 방해 금지 모드 우회 설정
                setBypassDnd(true)
            }
            nm.createNotificationChannel(channel)
        }
    }
}