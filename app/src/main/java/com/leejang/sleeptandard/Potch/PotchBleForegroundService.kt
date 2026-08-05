package com.leejang.sleeptandard.Potch

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.leejang.sleeptandard.MainActivity
import com.leejang.sleeptandard.Potch.PotchBleManager
import com.leejang.sleeptandard.Potch.PotchDataLogger
import com.leejang.sleeptandard.Potch.PotchDataProcessor
import com.leejang.sleeptandard.Potch.PotchServiceStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import androidx.core.content.edit
import java.util.UUID

/**
 * Potch BLE 수신을 백그라운드에서도 유지하기 위한 ForegroundService.
 *
 * 이 Service의 핵심 역할:
 * 1. Potch BLE 수신 작업을 화면 생명주기와 분리한다.
 * 2. 앱이 백그라운드에 있어도 BLE 연결/수신을 유지하려고 한다.
 * 3. Foreground notification을 표시해서 Android가 장시간 작업으로 인식하게 한다.
 * 4. PotchBleManager, PotchDataProcessor, PotchDataLogger를 생성하고 연결한다.
 * 5. 알림 또는 ExperimentScreen에서 종료 요청이 오면 로그를 저장하고 Service를 종료한다.
 *
 * 전체 흐름:
 * ExperimentScreen
 * → PotchBleViewModel.startScan()
 * → PotchBleForegroundService ACTION_START
 * → PotchBleManager.startScan()
 * → Potch 연결 및 데이터 수신
 * → PotchDataProcessor 파싱
 * → PotchDataLogger 파일 저장
 */
class PotchBleForegroundService : Service() {

    companion object {
        const val ACTION_UPDATE_MICRO_BPF =
            "com.leejang.sleeptandard.Potch.ACTION_UPDATE_MICRO_BPF"

        const val EXTRA_MICRO_LOW_CUT = "extra_micro_low_cut"
        const val EXTRA_MICRO_HIGH_CUT = "extra_micro_high_cut"

        /** Potch510 Data Characteristic에 raw command payload를 전달한다. */
        const val ACTION_WRITE_COMMAND =
            "com.leejang.sleeptandard.Potch.ACTION_WRITE_COMMAND"
        const val EXTRA_COMMAND_PAYLOAD = "extra_command_payload"
        const val EXTRA_COMMAND_WITHOUT_RESPONSE = "extra_command_without_response"

        /** ble(3).c 기반 LED/Vibe 트리거 명령(0x01) 전송. */
        const val ACTION_TRIGGER_LED_FLASH =
            "com.leejang.sleeptandard.Potch.ACTION_TRIGGER_LED_FLASH"
        /**
         * Potch 수신 시작 명령.
         *
         * ViewModel에서 startForegroundService()로 이 action을 담아 보내면,
         * Service가 BLE 스캔/연결을 시작한다.
         */
        const val ACTION_START = "com.leejang.sleeptandard.Potch.ACTION_START"

        /**
         * Potch 수신 종료 및 로그 저장 명령.
         *
         * ExperimentScreen의 종료 버튼 또는 알림의 "종료 및 저장" 버튼에서 사용한다.
         */
        const val ACTION_STOP_AND_SAVE = "com.leejang.sleeptandard.Potch.ACTION_STOP_AND_SAVE"

        /**
         * Android NotificationChannel ID.
         *
         * Android 8.0 이상에서는 알림을 띄우려면 반드시 NotificationChannel이 필요하다.
         */
        private const val CHANNEL_ID = "potch_ble_channel"

        /**
         * ForegroundService 알림 ID.
         *
         * 같은 ID로 notify를 호출하면 기존 알림 내용이 갱신된다.
         */
        private const val NOTIFICATION_ID = 3001

        /**
         * 로그 확인용 태그
         */
        private const val TAG = "PotchBleFgService"
    }

    /**
     * Service 내부에서 사용할 CoroutineScope.
     *
     * SupervisorJob:
     * - 하나의 coroutine이 실패해도 전체 scope가 같이 취소되지 않게 한다.
     *
     * Dispatchers.Main:
     * - BLE 상태 collect, 알림 업데이트 등 기본 작업은 메인 스레드에서 시작한다.
     * - 대용량 파일 저장 같은 작업은 별도로 Dispatchers.IO로 넘긴다.
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Potch 수신 데이터를 CSV 파일로 저장하는 객체.
     *
     * 완성된 1초 Burst가 들어올 때마다 내부 저장소 파일에 append한다.
     */
    private var dataLogger: PotchDataLogger? = null

    /**
     * BLE로 들어온 raw byte packet을 실제 SensorData로 파싱하는 객체.
     *
     * 142B 서브 패킷 8개를 모아 1초 Burst로 만들고,
     * CRC-16 CCITT-FALSE / uint16 sequence / header 검사를 수행한다.
     */
    private var dataProcessor: PotchDataProcessor? = null

    /** 안정점수, 안정 episode, 개인 기준선 생명주기를 관리한다. */
    private var stabilityCalculator: PotchStabilityCalculator? = null

    /**
     * 실제 BLE 스캔, 연결, GATT, notify 구독, 재연결을 담당하는 객체.
     */
    private var bleManager: PotchBleManager? = null

    private var isStoppingService = false

    /**
     * Service가 처음 생성될 때 호출된다.
     *
     * 여기서 반드시 빠르게 startForeground()를 호출해야 한다.
     * ForegroundService는 시작 직후 알림을 띄우지 않으면 Android가 오류로 종료시킬 수 있다.
     */
    override fun onCreate() {
        super.onCreate()

        Log.i(TAG, "onCreate() - ForegroundService created")
        dataLogger?.logDebug(TAG, "onCreate() - ForegroundService created", "I")

        // ForegroundService 알림을 표시하기 위한 채널 생성
        createNotificationChannel()

        // Service를 foreground 상태로 올림
        // 이 알림이 떠 있어야 Android가 장시간 백그라운드 작업으로 인정해준다.
        startForeground(
            NOTIFICATION_ID,
            buildNotification("Potch 수신 준비 중")
        )

        // Logger / Processor / BLE Manager 생성
        initializePotchObjects()
    }

    /**
     * startForegroundService()로 Service에 명령이 들어올 때 호출된다.
     *
     * intent.action에 따라:
     * - ACTION_START: Potch 수신 시작
     * - ACTION_STOP_AND_SAVE: 수신 종료 및 로그 저장
     * - null: Android가 START_STICKY Service를 복구한 상황일 수 있음
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {


        Log.i(
            TAG,
            "onStartCommand() action=${intent?.action}, flags=$flags, startId=$startId, sessionRunning=${isSessionRunning()}"
        )
        dataLogger?.logDebug(TAG, "onStartCommand() action=${intent?.action}, flags=$flags, startId=$startId, sessionRunning=${isSessionRunning()}", "I")

        when (intent?.action) {
            ACTION_UPDATE_MICRO_BPF -> {
                val low = intent.getDoubleExtra(EXTRA_MICRO_LOW_CUT, 0.5)
                val high = intent.getDoubleExtra(EXTRA_MICRO_HIGH_CUT, 5.0)

                dataProcessor?.updateMicroMovementBandPass(
                    lowCutHz = low,
                    highCutHz = high
                )

                dataLogger?.logDebug(
                    TAG,
                    "ACTION_UPDATE_MICRO_BPF low=$low high=$high",
                    "I"
                )
            }
            ACTION_TRIGGER_LED_FLASH -> {
                val started = bleManager?.triggerLedFlash() ?: false
                dataLogger?.logDebug(
                    TAG,
                    "ACTION_TRIGGER_LED_FLASH command=0x01, started=$started",
                    if (started) "I" else "E"
                )
            }

            ACTION_WRITE_COMMAND -> {
                val payload = intent.getByteArrayExtra(EXTRA_COMMAND_PAYLOAD)
                val withoutResponse = intent.getBooleanExtra(
                    EXTRA_COMMAND_WITHOUT_RESPONSE,
                    true
                )

                if (payload == null || payload.isEmpty()) {
                    dataLogger?.logDebug(
                        TAG,
                        "ACTION_WRITE_COMMAND ignored: payload is null or empty",
                        "W"
                    )
                } else {
                    // 위 null/empty 검사 이후 payload는 ByteArray로 smart cast된다.
                    val started = bleManager?.writeCommand(
                        payload = payload,
                        withoutResponse = withoutResponse
                    ) ?: false
                    dataLogger?.logDebug(
                        TAG,
                        "ACTION_WRITE_COMMAND bytes=${payload.size}, " +
                                "withoutResponse=$withoutResponse, started=$started",
                        if (started) "I" else "E"
                    )
                }
            }
            ACTION_START -> {
                isStoppingService = false

                Log.i(TAG, "ACTION_START received")
                dataLogger?.logDebug(TAG, "ACTION_START received", "I")
                // "현재 Potch 수신 세션이 진행 중"이라는 표시를 저장한다.
                // Service가 죽었다가 재시작될 때 복구 판단에 사용한다.
                markSessionRunning(true)

                // BLE 스캔/연결 시작
                startPotchReceiving()
            }

            ACTION_STOP_AND_SAVE -> {
                isStoppingService = true

                Log.i(TAG, "ACTION_STOP_AND_SAVE received")
                dataLogger?.logDebug(TAG, "ACTION_STOP_AND_SAVE received", "I")
                // 사용자가 정상적으로 종료한 것이므로 세션 진행 상태를 false로 저장한다.
                markSessionRunning(false)

                // BLE 수신을 멈추고 로그를 Download/PotchLogs로 내보낸 뒤 Service 종료
                stopPotchReceivingAndSave()
            }

            null -> {
                Log.w(TAG, "onStartCommand() intent is null. START_STICKY restart suspected.")
                dataLogger?.logDebug(TAG, "onStartCommand() intent is null. START_STICKY restart suspected.", "W")
                /**
                 * START_STICKY Service가 Android에 의해 재생성되면
                 * intent가 null로 들어올 수 있다.
                 *
                 * 이때 SharedPreferences에 session_running=true가 남아 있으면
                 * "이전에는 수신 중이었다"고 보고 다시 Potch 수신을 시작한다.
                 */
                if (isSessionRunning()) {
                    startPotchReceiving()
                    Log.w(TAG, "session_running=true. Restarting Potch receiving.")
                    dataLogger?.logDebug(TAG, "session_running=true. Restarting Potch receiving.", "W")
                }else {
                    Log.w(TAG, "session_running=false. Service will not restart receiving.")
                    dataLogger?.logDebug(TAG, "session_running=false. Service will not restart receiving.", "W")
                }
            }
        }

        /**
         * START_STICKY:
         * Service가 시스템에 의해 죽은 경우,
         * Android가 여건이 되면 Service를 다시 생성하려고 한다.
         *
         * 단, 죽어 있던 동안의 BLE 데이터는 복구할 수 없다.
         */
        return START_STICKY
    }

    /**
     * Potch BLE 수신에 필요한 핵심 객체들을 초기화한다.
     *
     * 생성되는 객체:
     * - PotchDataLogger: CSV 로그 저장
     * - PotchDataProcessor: raw packet 파싱
     * - PotchBleManager: BLE 연결/수신 담당
     *
     * 이미 생성되어 있다면 중복 생성하지 않는다.
     */
    private fun initializePotchObjects() {
        if (
            dataLogger != null &&
            dataProcessor != null &&
            stabilityCalculator != null &&
            bleManager != null
        ) {
            Log.d(TAG, "initializePotchObjects() skipped - objects already initialized")
            dataLogger?.logDebug(TAG, "initializePotchObjects() skipped - objects already initialized")
            return
        }

        Log.i(TAG, "initializePotchObjects() - creating Logger, Processor, BleManager")
        dataLogger?.logDebug(TAG, "initializePotchObjects() - creating Logger, Processor, BleManager", "I")

        // CSV 로그 저장 담당
        val logger = PotchDataLogger(applicationContext)

        // 안정 episode 후보와 개인 기준선을 앱 내부 SQLite에 저장한다.
        val stableCandidateTable = StableCandidateTable(applicationContext)
        val personalBaselineTable = PersonalBaselineTable(applicationContext)
        val stability = PotchStabilityCalculator(
            stableCandidateTable = stableCandidateTable,
            personalBaselineTable = personalBaselineTable,
            dataLogger = logger
        )

        // BLE raw byte를 SensorData로 파싱하고 안정점수 계산기로 전달한다.
        val processor = PotchDataProcessor(
            dataLogger = logger,
            stabilityCalculator = stability
        )

        // BLE 스캔/연결/notify 수신 담당
        val manager = PotchBleManager(
            context = applicationContext,
            dataProcessor = processor,
            dataLogger = logger
        )

        dataLogger = logger
        dataProcessor = processor
        stabilityCalculator = stability
        bleManager = manager

        Log.i(TAG, "Potch objects initialized")
        dataLogger?.logDebug(TAG, "Potch objects initialized", "I")

        /**
         * BLE 상태를 계속 관찰한다.
         *
         * manager.state:
         * - isScanning
         * - isConnected
         * - isReconnecting
         * - lastError
         * - lastLog
         *
         * 이 상태를 PotchServiceStateHolder에 전달하면
         * ExperimentScreen이 collectAsState()로 화면에 표시할 수 있다.
         */
        serviceScope.launch {
            manager.state.collect { state ->

                Log.d(
                    TAG,
                    "BLE state: connected=${state.isConnected}, scanning=${state.isScanning}, reconnecting=${state.isReconnecting}, device=${state.deviceName}, log=${state.lastLog}, error=${state.lastError}"
                )
                dataLogger?.logDebug(TAG, "BLE state: connected=${state.isConnected}, scanning=${state.isScanning}, reconnecting=${state.isReconnecting}, device=${state.deviceName}, log=${state.lastLog}, error=${state.lastError}")

                // Service 내부 상태를 UI에서 볼 수 있게 공용 StateHolder에 전달
                PotchServiceStateHolder.updateBleState(state)
                stabilityCalculator?.onBleConnectionState(state.isConnected)

                // BLE 상태에 따라 foreground notification 문구 변경
                val text = when {
                    state.isConnected -> "Potch 연결됨 · 데이터 수신 중"
                    state.isReconnecting -> "Potch 재연결 시도 중"
                    state.isScanning -> "Potch 검색 중"
                    else -> "Potch 대기 중"
                }

                if (!isStoppingService) {
                    updateNotification(text)
                } else {
                    Log.d(TAG, "Skip notification update because service is stopping. text=$text")
                }
            }
        }

        /**
         * 데이터 파서 상태를 계속 관찰한다.
         *
         * processor.state:
         * - 마지막 SensorData
         * - CRC 오류 수
         * - sequence 누락 수
         * - parsed super frame 수
         * - 최근 packet error 목록
         *
         * 이 상태 역시 UI에서 확인할 수 있도록 StateHolder에 전달한다.
         */
        var lastLoggedParsedCount = -1
        var lastLoggedSeqErr = 0
        var lastLoggedCrcErr = 0

        serviceScope.launch {
            processor.state.collect { state ->
                PotchServiceStateHolder.updateProcessorState(state)

                val hasNewError =
                    state.missingSequenceErrors != lastLoggedSeqErr ||
                            state.crcErrorCount != lastLoggedCrcErr

                val shouldLogPeriodic =
                    state.parsedSuperFrameCount > 0 &&
                            state.parsedSuperFrameCount != lastLoggedParsedCount &&
                            state.parsedSuperFrameCount % 10 == 0

                if (hasNewError || shouldLogPeriodic) {
                    val msg =
                        "Processor state: parsed=${state.parsedSuperFrameCount}, " +
                                "totalMini=${state.totalMiniPackets}, " +
                                "validMini=${state.validMiniPackets}, " +
                                "crcErr=${state.crcErrorCount}, " +
                                "seqErr=${state.missingSequenceErrors}, " +
                                "lastLog=${state.lastLog}"

                    Log.d(TAG, msg)
                    dataLogger?.logDebug(
                        TAG,
                        msg,
                        if (hasNewError) "E" else "D"
                    )

                    lastLoggedParsedCount = state.parsedSuperFrameCount
                    lastLoggedSeqErr = state.missingSequenceErrors
                    lastLoggedCrcErr = state.crcErrorCount
                }
            }
        }
    }

    /**
     * Potch BLE 수신 시작.
     *
     * 실제로는:
     * 1. 권한 확인
     * 2. 객체 초기화 확인
     * 3. bleManager.startScan() 호출
     *
     * startScan() 이후 Potch가 발견되면 BLE 연결과 notify 구독이 이어진다.
     */
    private fun startPotchReceiving() {
        Log.i(TAG, "startPotchReceiving() called")

        initializePotchObjects()

        dataLogger?.startIfNeeded()
        dataLogger?.logDebug(TAG, "startPotchReceiving() called", "I")

        if (!hasRequiredPermissions()) {
            Log.e(TAG, "startPotchReceiving() blocked - missing permissions")
            dataLogger?.logDebug(TAG, "startPotchReceiving() blocked - missing permissions", "E")
            updateNotification("Potch 권한이 부족합니다")
            return
        }

        Log.i(TAG, "Permissions OK. Starting BLE scan.")
        dataLogger?.logDebug(TAG, "Permissions OK. Starting BLE scan.", "I")

        // 앱 프로세스가 START_STICKY로 재생성되어도 동일 수면 session id를 재사용한다.
        stabilityCalculator?.startSession(getOrCreateStabilitySessionId())
        dataProcessor?.refreshStabilityState()
        bleManager?.startScan()
    }

    /**
     * Potch 수신 종료 및 로그 저장.
     *
     * 중요한 점:
     * - BLE 연결/스캔/재연결 중단은 먼저 처리한다.
     * - CSV 파일 내보내기는 파일 크기가 클 수 있으므로 Dispatchers.IO에서 수행한다.
     * - 저장 완료 후 foreground 알림을 내리고 Service를 종료한다.
     */
    private fun stopPotchReceivingAndSave() {
        Log.i(TAG, "stopPotchReceivingAndSave() called")
        dataLogger?.logDebug(TAG, "stopPotchReceivingAndSave() called", "I")
        val manager = bleManager

        if (manager == null) {
            Log.w(TAG, "stopPotchReceivingAndSave() - manager is null. Stop service only.")
            dataLogger?.logDebug(TAG, "stopPotchReceivingAndSave() - manager is null. Stop service only.", "W")
            stabilityCalculator?.endSession()
            clearStabilitySessionId()
            stopForeground(STOP_FOREGROUND_REMOVE)

            val notificationManager =
                getSystemService(NotificationManager::class.java)
            notificationManager.cancel(NOTIFICATION_ID)

            stopSelf()
            return
        }

        updateNotification("Potch 로그 저장 중...")

        Log.i(TAG, "Stopping BLE reconnect/scan/gatt before saving log")
        dataLogger?.logDebug(TAG, "Stopping BLE reconnect/scan/gatt before saving log", "I")

        // BLE 연결, 스캔, 재연결 시도를 중지한다.
        // 이 함수는 파일 저장까지 하지 않고 BLE 정리만 먼저 한다.
        manager.stopReconnectOnly()

        // 대용량 CSV 복사는 메인 스레드에서 하면 ANR/강제 종료 위험이 있으므로 IO 스레드에서 실행
        serviceScope.launch {

            Log.i(TAG, "Saving log on Dispatchers.IO")
            dataLogger?.logDebug(TAG, "Saving log on Dispatchers.IO", "I")

            val savedPath = kotlinx.coroutines.withContext(Dispatchers.IO) {
                val stabilitySummary = stabilityCalculator?.endSession()
                dataLogger?.logDebug(
                    TAG,
                    "Stability session summary=$stabilitySummary",
                    "I"
                )
                clearStabilitySessionId()
                manager.saveCurrentLog()
            }

            // 저장 결과를 BLE 상태에 반영해서 UI에서 볼 수 있게 한다.
            manager.updateLogSavedState(savedPath)

            updateNotification(
                if (savedPath != null) {
                    "로그 저장 완료"
                } else {
                    "저장할 로그가 없습니다"
                }
            )

            Log.i(TAG, "Stopping foreground service after save")
            dataLogger?.logDebug(TAG, "Stopping foreground service after save", "I")

            // ForegroundService 알림까지 완전히 제거
            stopForeground(STOP_FOREGROUND_REMOVE)

            val notificationManager =
                getSystemService(NotificationManager::class.java)
            notificationManager.cancel(NOTIFICATION_ID)

            // Service 종료
            stopSelf()
        }
    }

    /**
     * Service가 종료될 때 호출된다.
     *
     * 여기서 BLE 연결과 coroutine을 정리한다.
     *
     * 주의:
     * 정상 종료뿐만 아니라 시스템에 의해 Service가 종료될 때도 호출될 수 있다.
     */
    override fun onDestroy() {
        Log.w(TAG, "onDestroy() - ForegroundService destroyed")
        dataLogger?.logDebug(TAG, "onDestroy() - ForegroundService destroyed", "W")
        // 정상 종료 경로에서 이미 호출됐다면 endSession()은 no-op summary를 반환한다.
        // 시스템 종료 경로에서는 현재까지 완성된 안정 episode를 가능한 범위에서 보존한다.
        runCatching { stabilityCalculator?.endSession() }

        // BLE 스캔/연결/GATT 자원 정리
        bleManager?.close()

        // Service에서 돌던 coroutine 취소
        serviceScope.cancel()

        super.onDestroy()
    }

    /**
     * Bound Service를 사용하지 않으므로 null 반환.
     *
     * 현재 구조는 bindService가 아니라 startForegroundService 방식이다.
     */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * ForegroundService에 표시할 notification 생성.
     *
     * 기능:
     * 1. 알림 클릭 시 MainActivity 실행
     * 2. MainActivity에 open_screen="experiment" extra 전달
     * 3. 알림 안에 "종료 및 저장" 버튼 제공
     * 4. 버튼 클릭 시 ACTION_STOP_AND_SAVE를 이 Service에 전달
     */
    private fun buildNotification(contentText: String): Notification {
        /**
         * 알림 본문을 눌렀을 때 앱을 여는 Intent.
         *
         * open_screen="experiment"를 넣어두면,
         * MainActivity/AppNav 쪽에서 이 값을 보고 ExperimentScreen으로 이동할 수 있다.
         */
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_screen", "experiment")
        }

        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            3001,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        /**
         * 알림의 "종료 및 저장" 버튼을 눌렀을 때 실행될 Intent.
         *
         * Activity를 여는 것이 아니라,
         * 이 ForegroundService에 ACTION_STOP_AND_SAVE 명령을 보낸다.
         */
        val stopIntent = Intent(this, PotchBleForegroundService::class.java).apply {
            action = ACTION_STOP_AND_SAVE
        }

        val stopPendingIntent = PendingIntent.getService(
            this,
            3002,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("알람의 정석")
            .setContentText(contentText)

            // 알림 클릭 시 앱 열기
            .setContentIntent(openAppPendingIntent)

            // 사용자가 직접 종료하기 전까지 계속 떠 있는 알림
            .setOngoing(true)

            // 알림 내용 갱신 시 매번 소리/진동이 나지 않게 함
            .setOnlyAlertOnce(true)

            // 알림 안의 종료 버튼
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "종료 및 저장",
                stopPendingIntent
            )
            .build()
    }

    /**
     * foreground notification의 문구를 갱신한다.
     *
     * 같은 NOTIFICATION_ID로 notify하면 기존 알림이 새 내용으로 업데이트된다.
     */
    private fun updateNotification(contentText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    /**
     * NotificationChannel 생성.
     *
     * Android 8.0 이상에서는 NotificationChannel이 없으면 알림이 표시되지 않는다.
     *
     * IMPORTANCE_LOW:
     * - 지속 알림은 표시하되, 너무 방해되지 않게 낮은 중요도로 설정한다.
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Potch BLE 수신",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Potch 수면 데이터 수신 상태"
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    /**
     * Potch BLE 수신에 필요한 권한이 모두 허용되었는지 확인한다.
     *
     * Android 12 이상:
     * - BLUETOOTH_SCAN
     * - BLUETOOTH_CONNECT
     *
     * Android 11 이하:
     * - ACCESS_FINE_LOCATION
     *
     * Android 13 이상:
     * - POST_NOTIFICATIONS
     *
     * 이 권한들은 Manifest에 선언하는 것뿐 아니라,
     * 런타임에서 사용자가 허용해야 실제로 granted 상태가 된다.
     */
    private fun hasRequiredPermissions(): Boolean {
        val hasBluetoothPermissions =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            }

        val hasNotificationPermission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

        Log.d(
            TAG,
            "Permission check: bluetooth=$hasBluetoothPermissions, notification=$hasNotificationPermission"
        )
        dataLogger?.logDebug(TAG, "Permission check: bluetooth=$hasBluetoothPermissions, notification=$hasNotificationPermission")

        return hasBluetoothPermissions && hasNotificationPermission
    }

    private fun getOrCreateStabilitySessionId(): String {
        val preferences = getSharedPreferences("potch_service", MODE_PRIVATE)
        val existing = preferences.getString("stability_session_id", null)
        if (!existing.isNullOrBlank()) return existing

        val created = UUID.randomUUID().toString()
        preferences.edit { putString("stability_session_id", created) }
        return created
    }

    private fun clearStabilitySessionId() {
        getSharedPreferences("potch_service", MODE_PRIVATE)
            .edit { remove("stability_session_id") }
    }

    /**
     * 현재 Potch 수신 세션이 진행 중인지 SharedPreferences에 저장한다.
     *
     * 이 값은 Service가 시스템에 의해 죽었다가 START_STICKY로 재시작될 때 사용한다.
     *
     * running = true:
     * - 사용자가 Potch 연결을 시작한 상태
     *
     * running = false:
     * - 사용자가 종료 및 저장을 누른 상태
     */
    private fun markSessionRunning(running: Boolean) {
        getSharedPreferences("potch_service", MODE_PRIVATE)
            .edit {
                putBoolean("session_running", running)
            }
    }

    /**
     * 이전에 Potch 수신 세션이 진행 중이었는지 확인한다.
     *
     * onStartCommand(intent = null) 상황에서 이 값이 true이면,
     * Android가 Service를 복구한 것으로 보고 다시 Potch 수신을 시작한다.
     */
    private fun isSessionRunning(): Boolean {
        return getSharedPreferences("potch_service", MODE_PRIVATE)
            .getBoolean("session_running", false)
    }
}