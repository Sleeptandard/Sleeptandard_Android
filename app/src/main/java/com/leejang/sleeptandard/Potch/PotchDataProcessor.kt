package com.leejang.sleeptandard.Potch

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * PotchDataProcessor가 현재까지 처리한 데이터 상태를 담는 데이터 클래스.
 *
 * BLE 통신으로 들어온 raw byte를 파싱한 결과와,
 * 패킷 오류 정보, 마지막 로그 등을 UI에서 볼 수 있게 저장한다.
 */

data class PacketErrorLog(
    val type: String,
    val message: String,
    val fragCounter: Int? = null,
    val timestampMs: Long = System.currentTimeMillis()
)

data class IbiInterval(
    val intervalSec: Double,

    // 기존 정수 sample index는 로그/호환용으로 유지한다.
    val endSampleIndex: Long,

    // 패킷 누락/CRC 오류 전후의 IBI가 서로 연결되지 않도록 하는 연속 구간 ID.
    val segmentId: Long = 0L,

    // 포물선 보간으로 추정한 실제 peak 종료 위치.
    // 100Hz에서도 1 sample(10ms) 단위가 아닌 sub-sample 위치를 보존한다.
    val endSamplePosition: Double = endSampleIndex.toDouble()
)

data class HeartRateEstimate(
    val bpm: Int,
    val ibiIntervals: List<IbiInterval>,
    val peakCount: Int,
    val intervalCount: Int,
    val averageIntervalSec: Double,
    val qualityScore: Double,

    // HeartPy-style adaptive peak fitting debug values.
    // 어떤 이동평균 상승률 후보가 선택됐는지와 해당 후보의 SDSD를 남긴다.
    val selectedThresholdPercent: Double? = null,
    val peakFitSdsdMs: Double? = null,
    val rawIntervalCount: Int = intervalCount,
    val acceptedIntervalRatio: Double = 1.0,

    // 정수 peak index에서 포물선 보간 위치까지 이동한 크기.
    // 측정 정확도 자체를 뜻하지 않고 10ms grid 보정량을 디버깅하기 위한 값이다.
    val meanPeakInterpolationOffsetMs: Double = 0.0,
    val maxPeakInterpolationOffsetMs: Double = 0.0
)

data class DataProcessorState(
    // 마지막으로 정상 파싱된 센서 데이터
    // 아직 수신된 데이터가 없거나 파싱 전이면 null
    val lastParsedData: SensorData? = null,

    // IR과 RED raw sample을 평균낸 합산 PPG 신호 기반 심박수.
    // 화면 표시와 각성지표 HR/HRV 입력에 모두 이 값을 사용한다.
    val heartRateBpm: Int? = null,

    // 현재 심박수 추정의 품질 점수. 0.0~1.0, 값이 클수록 peak 간격이 안정적이다.
    val heartRateQuality: Double? = null,

    // 현재 프레임에서 합산 PPG 심박수 추출이 가능한지와 실패 이유.
    val heartRateCalculationStatus: MetricCalculationStatus = MetricCalculationStatus(
        state = MetricCalculationState.COLLECTING,
        message = "합산 PPG 심박 신호 수집 중"
    ),

    // CRC 검증 실패 횟수
    // 패킷 데이터가 손상되었을 가능성을 확인하기 위한 누적 카운트
    val crcErrorCount: Int = 0,

    // Fragment 순서가 예상과 다르게 들어온 횟수
    // BLE notification 누락 또는 순서 꼬임을 감지하기 위한 누적 카운트
    val missingSequenceErrors: Int = 0,

    // 마지막 처리 상태를 사람이 읽을 수 있게 저장하는 로그 메시지
    // 예: "CRC OK!", "Length Drop", "Seq Drop" 등
    val lastLog: String = "No data yet",

    // 전체 수신된 BLE mini packet 수
    val totalMiniPackets: Int = 0,

    // 정상 형식으로 처리된 mini packet 수
    val validMiniPackets: Int = 0,

    // 손상된 mini packet 또는 super frame 수
    val damagedPacketCount: Int = 0,

    // counter 차이로 추정한 손실 mini packet 수
    val estimatedLostPacketCount: Int = 0,

    // 완성되어 파싱된 super frame 수
    val parsedSuperFrameCount: Int = 0,

    // 최근 패킷 오류 내역
    val recentPacketErrors: List<PacketErrorLog> = emptyList(),

    // 마지막으로 수신한 fragment counter
    val lastFragCounter: Int? = null,

    // 다음에 기대하는 fragment counter
    val expectedFragCounter: Int? = null,

    // 현재 분석 연속 구간 ID. 패킷 누락/CRC 오류마다 증가한다.
    val analysisSegmentId: Long = 0L,

    // 분석 연속성이 끊긴 누적 횟수와 마지막 원인.
    val continuityBreakCount: Int = 0,
    val lastContinuityBreakReason: String? = null,

    // 가장 최근 프레임의 IR 최댓값.
    // 손가락/피부 접촉이 있으면 보통 10000 이상, 강하면 50000 이상.
    // 0이면 PPG 데이터가 안 들어오거나 sleep mode에서 0으로 채워졌을 가능성이 큼.
    val lastIrMax: Double = 0.0,

    // INT2 비동기 이벤트 수신 여부
    val int2EventReceived: Boolean = false,

    // 각성지표 state
    val arousalState: ArousalState = ArousalState(),
)
/**
 * Potch BLE 기기에서 들어오는 raw byte 데이터를 실제 센서 데이터로 변환하는 클래스.
 *
 * Potch 기기는 센서 데이터를 한 번에 1212 bytes짜리 Super Frame으로 구성하지만,
 * BLE notification으로는 204 bytes 단위 Fragment로 나눠서 보낸다.
 *
 * 이 클래스의 역할:
 * 1. 204 bytes Fragment를 수신한다.
 * 2. Fragment header를 검사한다.
 * 3. Fragment 순서를 확인한다.
 * 4. Payload를 buffer에 누적한다.
 * 5. 1212 bytes Super Frame이 완성되면 파싱한다.
 * 6. NTC, timestamp, battery, IMU, CRC 정보를 추출한다.
 */
class PotchDataProcessor(
    private val dataLogger: PotchDataLogger? = null
) {
    private val TAG = "PotchDataProcessor"

    companion object {
        private const val HEART_RATE_MIN_BPM = 40
        private const val HEART_RATE_MAX_BPM = 180
        private const val HEART_RATE_MOVING_AVERAGE_SECONDS = 1.5
        private const val HEART_RATE_MIN_ACCEPTED_INTERVAL_RATIO = 0.50
        private const val HEART_RATE_MIN_PHYSIOLOGICAL_INTERVAL_RATIO = 0.75

        // candidate 비교에서 IBI 개수 부족과 reject 비율에 주는 작은 penalty.
        private const val HEART_RATE_COUNT_PENALTY_SEC = 0.020
        private const val HEART_RATE_REJECTION_PENALTY_SEC = 0.050

        // HeartPy의 ma_perc 후보 범위를 참고한 moving-average 상승률 목록.
        private val HEART_RATE_THRESHOLD_PERCENT_CANDIDATES = doubleArrayOf(
            5.0, 10.0, 15.0, 20.0, 25.0, 30.0,
            40.0, 50.0, 75.0, 100.0, 150.0, 200.0, 300.0
        )
    }

    private data class HeartRatePeakFitCandidate(
        val thresholdPercent: Double,
        val thresholdOffset: Double,
        val peakIndices: List<Int>,
        val peakPositions: List<Double>,
        val rawIntervals: List<IbiInterval>,
        val usedIntervals: List<IbiInterval>,
        val rawBpm: Double,
        val finalBpm: Double,
        val sdsdSec: Double,
        val acceptedIntervalRatio: Double,
        val selectionScore: Double,
        val meanInterpolationOffsetMs: Double,
        val maxInterpolationOffsetMs: Double
    )

    // 각성지표 연산기
    private val arousalCalculator = PotchArousalCalculator()

    private val currentFrameErrors = mutableListOf<String>()
    private val currentFrameMissPacketNums = mutableListOf<Int>()

    /**
     * 내부에서 수정 가능한 상태 값.
     *
     * UI에서는 직접 수정하면 안 되기 때문에 private으로 숨기고,
     * 외부에는 아래 state만 공개한다.
     */
    private val _state = MutableStateFlow(DataProcessorState())

    /**
     * 외부에서 관찰할 수 있는 읽기 전용 상태.
     *
     * Compose 화면이나 ViewModel에서 collectAsState()로 받아서
     * 센서값, 오류 횟수, 로그를 표시할 수 있다.
     */
    val state: StateFlow<DataProcessorState> = _state

    /**
     * Fragment payload를 임시로 쌓아두는 버퍼.
     *
     * BLE로 들어오는 데이터는 204 bytes 단위이고,
     * 앞의 2 bytes는 mini header이므로 실제 payload는 202 bytes다.
     *
     * payload를 계속 모아서 1212 bytes가 되면 하나의 Super Frame으로 파싱한다.
     */
    private val buffer = ArrayDeque<Byte>()

    /**
     * 다음에 들어와야 할 Fragment counter 값.
     *
     * Fragment mini header 안에는 12-bit counter가 들어있다.
     * 이 값을 이용해서 패킷 누락 또는 순서 꼬임을 감지한다.
     *
     * null이면 아직 기준 counter가 없는 초기 상태다.
     */
    private var expectedFragCounter: Int? = null

    /**
     * 현재 분석 연속 구간 ID.
     *
     * Fragment 누락, CRC 오류, Super Header 오류가 발생하면 증가한다.
     * 이후 생성되는 IBI에 이 값을 기록해 서로 다른 구간의 RR을 연결하지 않는다.
     */
    private var currentAnalysisSegmentId: Long = 0L

    /**
     * 심박수 추정을 위한 PPG 합산 샘플 누적 버퍼.
     *
     * 한 Super Frame에는 1초(100 샘플)치 PPG 데이터만 들어있어서
     * 그 안에서 피크 검출을 하면 비트 수가 너무 적어 (60bpm 기준 1개) 불안정하다.
     * 그래서 최근 몇 초 분량을 rolling buffer로 누적한 뒤 그 위에서 피크를 검출한다.
     *
     * 합산 샘플은 같은 index의 IR raw와 RED raw를 평균낸 값이다.
     * CRC가 정상인 프레임의 샘플만 누적한다 (손상된 프레임은 HR 추정에 사용하지 않음).
     */
    private val heartRateCombinedBuffer = ArrayDeque<Int>()

    private var totalHeartRateCombinedSampleCount: Long = 0L

    /** HR 버퍼에 보관할 최대 샘플 수. 100Hz 기준 8초 = 800 샘플. */
    private val heartRateBufferMaxSamples = 800

    /** HR 계산을 시도하기 위한 최소 누적 샘플 수. 100Hz 기준 3초 = 300 샘플. */
    private val heartRateMinSamples = 300

    /**
     * adaptive peak fitting이 실패해도 초기 수집 중으로 표시할 권장 길이.
     * SDSD에는 최소 4개 peak가 필요하므로 저심박까지 고려해 6초를 확보한다.
     */
    private val heartRateAdaptiveFitPreferredSamples = 600

    // CRC 정상 PPG 입력과 유효 HR 결과의 마지막 휴대폰 시각.
    private var lastValidHeartRateInputTimestampMillis: Long? = null
    private var lastValidHeartRateEstimateTimestampMillis: Long? = null

    // CRC 정상 PPG가 끊긴 채 과거 raw buffer를 반복 계산하지 않도록 하는 제한.
    private val heartRateInputStaleTimeoutMillis = 3 * 1000L

    // 화면에서 마지막 정상 BPM을 잠깐 유지할 수 있는 최대 시간. 이후에는 null로 내린다.
    private val heartRateDisplayStaleTimeoutMillis = 10 * 1000L

    /**
     * Potch 기기에서 한 번에 보내는 BLE Fragment 크기.
     *
     * 구조:
     * - 2 bytes: mini header
     * - 202 bytes: payload
     *
     * 총 204 bytes
     */
    private val fragmentSize = 204

    /**
     * 하나의 완성된 센서 데이터 프레임 크기.
     *
     * Fragment payload 202 bytes × 6개 = 1212 bytes
     *
     * 이 크기만큼 buffer에 모이면 parseSuperFrame()을 호출한다.
     */
    private val superFrameSize = 1212

    /**
     * BLE notification으로 들어온 raw byte 배열을 처리하는 함수.
     *
     * PotchBleManager의 onCharacteristicChanged()에서 호출된다.
     *
     * 처리 흐름:
     * 1. 수신 길이 확인
     * 2. mini header 파싱
     * 3. header prefix 확인
     * 4. fragment counter 순서 확인
     * 5. payload 추출
     * 6. frame 시작점 0xAA 0xAA 확인
     * 7. buffer에 payload 누적
     * 8. 1212 bytes가 모이면 Super Frame 파싱
     */
    @Synchronized
    fun processIncomingData(data: ByteArray) {
        Log.d(TAG, "Rcv length=${data.size}")
        updateLog("Rcv length: ${data.size}")

        _state.update {
            it.copy(totalMiniPackets = it.totalMiniPackets + 1)
        }

        if (data.size != fragmentSize) {
            val msg = "Length Drop: expected $fragmentSize, got ${data.size}"

            _state.update {
                it.copy(damagedPacketCount = it.damagedPacketCount + 1)
            }

            addPacketError(
                type = "LENGTH",
                message = msg
            )

            logMissFrameAndClear(
                reason = msg,
                missPacketNum = currentMiniPacketIndexInFrame()
            )

            return
        }

        val miniHeader =
            ((data[0].toInt() and 0xFF) shl 8) or
                    (data[1].toInt() and 0xFF)

        val headerPrefix = (miniHeader shr 12) and 0xF

        // 비동기 INT2 이벤트 처리.
        // 펌웨어 ble_send_int2_signal()은 prefix 0xE, eventType 0x001로 보냄.
        if (headerPrefix == 0xE) {
            val eventType = miniHeader and 0x0FFF

            if (eventType == 0x001) {
                val msg = "Asynchronous INT2 Event Received!"

                Log.i(TAG, msg)
                dataLogger?.logDebug(TAG, msg, "I")

                _state.update {
                    it.copy(
                        int2EventReceived = true,
                        lastLog = msg
                    )
                }
            }

            return
        }

        if (headerPrefix != 0x5) {
            val msg = "Header Prefix Drop: expected 0x5, got 0x${headerPrefix.toString(16)}"

            _state.update {
                it.copy(damagedPacketCount = it.damagedPacketCount + 1)
            }

            addPacketError(
                type = "MINI_HEADER",
                message = msg
            )

            logMissFrameAndClear(
                reason = msg,
                missPacketNum = currentMiniPacketIndexInFrame()
            )

            return
        }

        val fragCounter = miniHeader and 0x0FFF

        expectedFragCounter?.let { expected ->
            if (fragCounter != expected) {
                val distance = counterDistance(expected, fragCounter)
                val lostCount = if (distance in 1..4095) distance else 1

                val startMissIndex = currentMiniPacketIndexInFrame()
                val missNums = (startMissIndex until startMissIndex + lostCount)
                    .map { ((it - 1) % 6) + 1 }

                val msg = "Seq Drop. Exp: $expected, Got: $fragCounter, Lost≈$lostCount"

                _state.update {
                    it.copy(
                        missingSequenceErrors = it.missingSequenceErrors + 1,
                        estimatedLostPacketCount = it.estimatedLostPacketCount + lostCount
                    )
                }

                addPacketError(
                    type = "SEQUENCE",
                    message = msg,
                    fragCounter = fragCounter
                )

                currentFrameErrors.add(msg)
                currentFrameMissPacketNums.addAll(missNums)

                logMissFrameAndClear(
                    reason = msg,
                    missPacketNum = startMissIndex
                )
            }
        }

        expectedFragCounter = (fragCounter + 1) and 0x0FFF

        _state.update {
            it.copy(
                validMiniPackets = it.validMiniPackets + 1,
                lastFragCounter = fragCounter,
                expectedFragCounter = expectedFragCounter
            )
        }

        val payload = data.copyOfRange(2, data.size)

        if (buffer.isEmpty()) {
            if (
                payload.size < 2 ||
                (payload[0].toInt() and 0xFF) != 0xAA ||
                (payload[1].toInt() and 0xFF) != 0xAA
            ) {
                val msg = "Syncing... waiting for Super Header 0xAAAA"

                addPacketError(
                    type = "SYNC",
                    message = msg,
                    fragCounter = fragCounter
                )

                return
            }
        }

        payload.forEach { buffer.addLast(it) }

        if (buffer.size >= superFrameSize) {
            val superFrame = ByteArray(superFrameSize) {
                buffer.removeFirst()
            }

            parseSuperFrame(superFrame)
        }
    }

    /**
     * 데이터 파서 상태를 초기화한다.
     *
     * 사용 예:
     * - 개발자 화면에서 "수신 데이터 초기화" 버튼을 누를 때
     * - 연결이 완전히 새로 시작될 때
     */
    @Synchronized
    fun reset() {
        buffer.clear()
        expectedFragCounter = null
        currentFrameErrors.clear()
        currentFrameMissPacketNums.clear()

        currentAnalysisSegmentId = 0L

        heartRateCombinedBuffer.clear()
        totalHeartRateCombinedSampleCount = 0L
        lastValidHeartRateInputTimestampMillis = null
        lastValidHeartRateEstimateTimestampMillis = null

        arousalCalculator.reset(initialSegmentId = currentAnalysisSegmentId)

        _state.value = DataProcessorState(
            analysisSegmentId = currentAnalysisSegmentId
        )
    }

    /**
     * 1212 bytes짜리 Super Frame을 실제 센서 데이터로 파싱한다.
     *
     * Super Frame 구조 (firmware main.c의 struct super_frame과 동일):
     * - [0..1]    : Super Header, 0xAA 0xAA
     * - [2..3]    : NTC raw
     * - [4..7]    : Timestamp, little endian
     * - [8..9]    : Battery raw
     * - [10..11]  : CRC
     * - [12..611] : PPG data, 600 bytes (RED/IR, 6 bytes/sample x 100 sample)
     * - [612..1211]: IMU data, 600 bytes
     */
    private fun parseSuperFrame(data: ByteArray) {
        var frameComplete = true
        val frameErrors = mutableListOf<String>()
        val missNums = mutableListOf<Int>()

        if (
            (data[0].toInt() and 0xFF) != 0xAA ||
            (data[1].toInt() and 0xFF) != 0xAA
        ) {
            val msg = "Super Header Drop: 0x%02X%02X".format(
                data[0].toInt() and 0xFF,
                data[1].toInt() and 0xFF
            )

            frameComplete = false
            frameErrors.add(msg)
            missNums.add(1)

            _state.update {
                it.copy(damagedPacketCount = it.damagedPacketCount + 1)
            }

            addPacketError(
                type = "SUPER_HEADER",
                message = msg
            )
        }

        val ntcRaw =
            ((data[2].toInt() and 0x0F) shl 8) or
                    (data[3].toInt() and 0xFF)

        val timestamp =
            ((data[4].toLong() and 0xFFL)) or
                    ((data[5].toLong() and 0xFFL) shl 8) or
                    ((data[6].toLong() and 0xFFL) shl 16) or
                    ((data[7].toLong() and 0xFFL) shl 24)

        val batteryRaw =
            ((data[8].toInt() and 0x0F) shl 8) or
                    (data[9].toInt() and 0xFF)

        val receivedCrc =
            ((data[10].toInt() and 0xFF) shl 8) or
                    (data[11].toInt() and 0xFF)

        val crcData = data.copyOf()
        crcData[10] = 0x00
        crcData[11] = 0x00

        val calculatedCrc = zephyrCrc16(crcData)

        if (receivedCrc != calculatedCrc) {
            val logMsg = "CRC! Rcv:%04X Calc:%04X".format(receivedCrc, calculatedCrc)

            frameComplete = false
            frameErrors.add(logMsg)

            // CRC는 특정 미니 패킷 번호를 단정하기 어려우므로 all로 표시
            currentFrameMissPacketNums.addAll(listOf(1, 2, 3, 4, 5, 6))

            _state.update {
                it.copy(
                    crcErrorCount = it.crcErrorCount + 1,
                    damagedPacketCount = it.damagedPacketCount + 1,
                    lastLog = logMsg
                )
            }

            addPacketError(
                type = "CRC",
                message = logMsg
            )
        } else {
            updateLog("CRC OK!")
        }

        val ppgData = data.copyOfRange(12, 612)
        val imuData = data.copyOfRange(612, 1212)

        val irSamples = extractIrSamples(ppgData)
        val redSamples = extractRedSamples(ppgData)
        val avgSamples = buildAveragePpgSamples(
            irSamples = irSamples,
            redSamples = redSamples
        )

        val frameIrMax = irSamples.maxOrNull()?.toDouble() ?: 0.0

        val phoneTimeMillis = System.currentTimeMillis()
        val isCrcValid = receivedCrc == calculatedCrc

        // CRC뿐 아니라 Super Header까지 정상인 프레임만 모든 분석에 사용한다.
        // 손상 프레임은 PPG/IMU/체온/HR/HRV 어느 buffer에도 넣지 않는다.
        val isFrameUsableForAnalysis =
            frameComplete && isCrcValid

        if (!isFrameUsableForAnalysis) {
            val discontinuityReasons = buildList {
                if (!frameComplete) add("invalid super header")
                if (!isCrcValid) add("CRC mismatch")
            }

            advanceAnalysisSegment(
                "SuperFrame excluded from analysis: " +
                        discontinuityReasons.joinToString(", ")
            )
        } else {
            appendPpgSamplesToHrBuffer(
                buffer = heartRateCombinedBuffer,
                samples = avgSamples
            ) {
                totalHeartRateCombinedSampleCount += 1L
            }
            lastValidHeartRateInputTimestampMillis = phoneTimeMillis
        }

        val heartRateEstimate = if (isFrameUsableForAnalysis) {
            estimateHeartRate()
        } else {
            null
        }

        if (heartRateEstimate != null) {
            lastValidHeartRateEstimateTimestampMillis = phoneTimeMillis
        }

        val estimatedHeartRate = heartRateEstimate?.bpm
        val isDisplayedHeartRateFresh = isTimestampFresh(
            lastTimestampMillis = lastValidHeartRateEstimateTimestampMillis,
            nowMillis = phoneTimeMillis,
            timeoutMillis = heartRateDisplayStaleTimeoutMillis
        )

        val heartRateCalculationStatus = buildHeartRateCalculationStatus(
            estimate = heartRateEstimate,
            isCurrentFrameCrcValid = isFrameUsableForAnalysis,
            nowMillis = phoneTimeMillis
        )

        Log.d(
            TAG,
            "SuperFrame parsed timestamp=$timestamp, segment=$currentAnalysisSegmentId, " +
                    "analysisValid=$isFrameUsableForAnalysis, irMax=$frameIrMax, " +
                    "bpmCombined=${heartRateEstimate?.bpm}, " +
                    "ibiCombined=${heartRateEstimate?.intervalCount}, " +
                    "qCombined=${heartRateEstimate?.qualityScore}, " +
                    "maPerc=${heartRateEstimate?.selectedThresholdPercent}, " +
                    "sdsdMs=${heartRateEstimate?.peakFitSdsdMs}, " +
                    "ibiAccept=${heartRateEstimate?.acceptedIntervalRatio}, " +
                    "peakOffsetMeanMs=${heartRateEstimate?.meanPeakInterpolationOffsetMs}, " +
                    "peakOffsetMaxMs=${heartRateEstimate?.maxPeakInterpolationOffsetMs}"
        )

        val parsed = SensorData(
            timestamp = timestamp,
            ntcRaw = ntcRaw,
            batteryRaw = batteryRaw,
            ppgData = ppgData,
            imuData = imuData
        )



        val arousalBufferSnapshot = arousalCalculator.getBufferSnapshot()

        Log.d(
            TAG,
            "ArousalBuffer: ppg=${arousalBufferSnapshot.ppgIrSampleCount}, " +
                    "imu=${arousalBufferSnapshot.imuGSampleCount}, " +
                    "temp=${arousalBufferSnapshot.temperatureSampleCount}, " +
                    "hr=${arousalBufferSnapshot.heartRateSampleCount}"
        )

        dataLogger?.logDebug(
            TAG,
            "ArousalBuffer: ppg=${arousalBufferSnapshot.ppgIrSampleCount}, " +
                    "imu=${arousalBufferSnapshot.imuGSampleCount}, " +
                    "temp=${arousalBufferSnapshot.temperatureSampleCount}, " +
                    "hr=${arousalBufferSnapshot.heartRateSampleCount}"
        )

        val arousalState =
            if (isFrameUsableForAnalysis) {
                arousalCalculator.process(
                    sensorData = parsed,
                    heartRateEstimate = heartRateEstimate,
                    heartRateSignalStatus = heartRateCalculationStatus,
                    analysisSegmentId = currentAnalysisSegmentId
                )
            } else {
                // advanceAnalysisSegment()가 만든 불연속 상태를 사용한다.
                // 손상된 parsed 값은 어떤 분석 buffer에도 append하지 않는다.
                arousalCalculator.getLastState()
            }

        val allErrors = currentFrameErrors + frameErrors
        val allMissNums = (currentFrameMissPacketNums + missNums)
            .distinct()
            .sorted()

        val completeText =
            if (frameComplete && allErrors.isEmpty()) "complete" else "miss"

        val missPacketNumText = allMissNums.joinToString("|")
        val errorLogText = allErrors.joinToString(" / ")

        dataLogger?.logSuperFrame(
            phoneTimeMillis = phoneTimeMillis,
            timestamp = timestamp,
            superFrame = data,
            complete = completeText,
            missPacketNum = missPacketNumText,
            errorLog = errorLogText
        )

        dataLogger?.logArousalState(
            phoneTimeMillis = phoneTimeMillis,
            timestamp = timestamp,
            arousalState = arousalState,
            complete = completeText,
            missPacketNum = missPacketNumText,
            errorLog = errorLogText
        )

        currentFrameErrors.clear()
        currentFrameMissPacketNums.clear()

        _state.update { current ->
            current.copy(
                // CRC/Super Header가 정상인 마지막 프레임만 노출한다.
                lastParsedData =
                    if (isFrameUsableForAnalysis) parsed else current.lastParsedData,

                // 마지막 정상값은 최대 10초까지만 유지하고, 그 뒤에는 stale 값 대신 null을 표시한다.
                // heartRateBpm은 IR/RED 평균 합산 PPG 기반 값을 사용한다.
                heartRateBpm = when {
                    estimatedHeartRate != null -> estimatedHeartRate
                    isDisplayedHeartRateFresh -> current.heartRateBpm
                    else -> null
                },
                heartRateQuality = when {
                    heartRateEstimate != null -> heartRateEstimate.qualityScore
                    isDisplayedHeartRateFresh -> current.heartRateQuality
                    else -> null
                },
                heartRateCalculationStatus = heartRateCalculationStatus,

                arousalState = arousalState,
                lastIrMax =
                    if (isFrameUsableForAnalysis) frameIrMax else current.lastIrMax,
                analysisSegmentId = currentAnalysisSegmentId,
                parsedSuperFrameCount = current.parsedSuperFrameCount + 1
            )
        }

        /********************* Micro Movement ********************/

        val microMovement = arousalCalculator.calculateMicroMovement()

        if (microMovement != null) {
            Log.d(
                "MicroMovement",
                "MicroMovement: " +
                        "rms=${"%.6f".format(microMovement.rmsG)}g, " +
                        "var=${"%.8f".format(microMovement.varianceG)}, " +
                        "score=${"%.2f".format(microMovement.score)}, " +
                        "level=${microMovement.level}"
            )

            dataLogger?.logDebug(
                "MicroMovement",
                "MicroMovement: " +
                        "rms=${"%.6f".format(microMovement.rmsG)}g, " +
                        "var=${"%.8f".format(microMovement.varianceG)}, " +
                        "score=${"%.2f".format(microMovement.score)}, " +
                        "level=${microMovement.level}"
            )
        }
        /********************* //Micro Movement ********************/
    }

    /**
     * PPG payload(600 bytes, 100Hz x 100 sample, [Red 3B + IR 3B] 구조, Data Packet.md 참조)에서
     * IR 채널 값만 18-bit로 복원한다.
     *
     * firmware ppg.c의 FIFO 저장 형식과 동일:
     *   byte[3] = IR[17:16] (하위 2bit만 유효), byte[4] = IR[15:8], byte[5] = IR[7:0]
     */
    private fun extractIrSamples(ppgData: ByteArray): IntArray {
        val sampleCount = ppgData.size / 6
        return IntArray(sampleCount) { i ->
            val base = i * 6
            ((ppgData[base + 3].toInt() and 0x03) shl 16) or
                    ((ppgData[base + 4].toInt() and 0xFF) shl 8) or
                    (ppgData[base + 5].toInt() and 0xFF)
        }
    }

    /**
     * PPG payload(600 bytes, 100Hz x 100 sample, [Red 3B + IR 3B] 구조)에서
     * RED 채널 값만 18-bit로 복원한다.
     */
    private fun extractRedSamples(ppgData: ByteArray): IntArray {
        val sampleCount = ppgData.size / 6
        return IntArray(sampleCount) { i ->
            val base = i * 6
            ((ppgData[base].toInt() and 0x03) shl 16) or
                    ((ppgData[base + 1].toInt() and 0xFF) shl 8) or
                    (ppgData[base + 2].toInt() and 0xFF)
        }
    }

    /**
     * IR과 RED raw sample을 sample index 기준으로 평균낸 합성 PPG 신호를 만든다.
     *
     * 이 합산 PPG 신호를 심박수 표시, HR 각성지표, HRV 계산에 공통으로 사용한다.
     */
    private fun buildAveragePpgSamples(
        irSamples: IntArray,
        redSamples: IntArray
    ): IntArray {
        val sampleCount = minOf(irSamples.size, redSamples.size)

        return IntArray(sampleCount) { i ->
            ((irSamples[i].toLong() + redSamples[i].toLong()) / 2L).toInt()
        }
    }

    /**
     * 새로 들어온 PPG IR 샘플을 rolling buffer에 누적한다.
     *
     * Super Frame 한 개에는 1초(100 샘플)치 PPG만 들어있어서,
     * 그 안에서만 피크를 찾으면 60bpm 기준 피크가 1개뿐이라 추정이 매우 불안정하다.
     * 그래서 최근 [heartRateBufferMaxSamples]개(최대 8초)를 누적해 두고
     * 그 위에서 심박수를 추정한다.
     */
    private fun appendPpgSamplesToHrBuffer(
        buffer: ArrayDeque<Int>,
        samples: IntArray,
        onSampleAdded: () -> Unit
    ) {
        for (sample in samples) {
            buffer.addLast(sample)
            onSampleAdded()

            if (buffer.size > heartRateBufferMaxSamples) {
                buffer.removeFirst()
            }
        }
    }

    private fun buildHeartRateCalculationStatus(
        estimate: HeartRateEstimate?,
        isCurrentFrameCrcValid: Boolean,
        nowMillis: Long
    ): MetricCalculationStatus {
        if (!isCurrentFrameCrcValid) {
            val rawInputIsStale = !isTimestampFresh(
                lastTimestampMillis = lastValidHeartRateInputTimestampMillis,
                nowMillis = nowMillis,
                timeoutMillis = heartRateInputStaleTimeoutMillis
            )

            return MetricCalculationStatus(
                state = MetricCalculationState.REJECTED,
                message = if (rawInputIsStale) {
                    "CRC 정상 PPG가 ${heartRateInputStaleTimeoutMillis / 1000}초 이상 없어 " +
                            "과거 HR raw buffer를 초기화했습니다"
                } else {
                    "현재 SuperFrame CRC 오류: 이 프레임은 HR/HRV 계산에 사용하지 않습니다"
                }
            )
        }

        if (estimate != null) {
            return MetricCalculationStatus(
                state = MetricCalculationState.VALID,
                message = "합산 PPG 심박수 정상 검출: ${estimate.bpm} bpm, " +
                        "quality=${"%.2f".format(estimate.qualityScore)}, " +
                        "ma=${estimate.selectedThresholdPercent?.let { "%.0f%%".format(it) } ?: "-"}, " +
                        "SDSD=${estimate.peakFitSdsdMs?.let { "%.1fms".format(it) } ?: "-"}, " +
                        "peak보정=${"%.2fms".format(estimate.meanPeakInterpolationOffsetMs)}"
            )
        }

        if (heartRateCombinedBuffer.size < heartRateMinSamples) {
            return MetricCalculationStatus(
                state = MetricCalculationState.COLLECTING,
                message = "합산 PPG 심박 신호 수집 중: " +
                        "${heartRateCombinedBuffer.size}/$heartRateMinSamples samples"
            )
        }

        if (heartRateCombinedBuffer.size < heartRateAdaptiveFitPreferredSamples) {
            return MetricCalculationStatus(
                state = MetricCalculationState.COLLECTING,
                message = "이동평균/SDSD peak fitting용 IBI 추가 수집 중: " +
                        "${heartRateCombinedBuffer.size}/$heartRateAdaptiveFitPreferredSamples samples"
            )
        }

        val maxRaw = heartRateCombinedBuffer.maxOrNull()?.toDouble() ?: 0.0

        if (maxRaw <= 10000.0) {
            return MetricCalculationStatus(
                state = MetricCalculationState.REJECTED,
                message = "PPG 접촉 신호가 약함: 합산 raw 최대값=${maxRaw.toInt()} " +
                        "(접촉 불량 또는 센서 이탈 가능)"
            )
        }

        return MetricCalculationStatus(
            state = MetricCalculationState.REJECTED,
            message = "유효 peak/IBI 부족: 움직임 잡음, 파형 왜곡 또는 이상치 필터링 가능"
        )
    }

    private fun isTimestampFresh(
        lastTimestampMillis: Long?,
        nowMillis: Long,
        timeoutMillis: Long
    ): Boolean {
        if (lastTimestampMillis == null) return false
        return nowMillis - lastTimestampMillis <= timeoutMillis
    }

    private fun hasTimestampExpired(
        lastTimestampMillis: Long?,
        nowMillis: Long,
        timeoutMillis: Long
    ): Boolean {
        if (lastTimestampMillis == null) return false
        return nowMillis - lastTimestampMillis > timeoutMillis
    }

    private fun estimateHeartRate(): HeartRateEstimate? {
        return estimateHeartRateFromBuffer(
            buffer = heartRateCombinedBuffer,
            totalSampleCount = totalHeartRateCombinedSampleCount
        )
    }

    /**
     * HeartPy의 핵심 아이디어를 Android/Kotlin 실시간 처리 구조에 맞게 적용한 HR 추정 함수.
     *
     * raw PPG
     * -> Hampel-style spike suppression
     * -> forward-backward one-pole bandpass(0.75~3.5Hz)
     * -> return_top처럼 양수 성분만 사용
     * -> 1.5초 이동평균 계산
     * -> 여러 moving-average 상승률 후보에서 ROI별 peak 검출
     * -> 각 peak 주변 3점을 이용한 포물선 보간으로 sub-sample 위치 추정
     * -> 후보별 BPM / SDSD / IBI 유지율 평가
     * -> 최적 후보 선택
     * -> 기존 quotient filter + median outlier filter 유지
     * -> bpm 계산
     */
    private fun estimateHeartRateFromBuffer(
        buffer: ArrayDeque<Int>,
        totalSampleCount: Long
    ): HeartRateEstimate? {
        if (buffer.size < heartRateMinSamples) return null

        val sampleRateHz = 100.0
        val signal = buffer.map { it.toDouble() }
        val n = signal.size

        if (n < 200) return null

        val maxRaw = signal.maxOrNull() ?: 0.0
        if (maxRaw <= 10000.0) return null

        val filtered = preprocessPpgForHeartRate(signal)

        val peakAmplitude = filtered.maxOrNull() ?: 0.0
        if (peakAmplitude <= 80.0) return null

        val bufferStartSampleIndex = totalSampleCount - buffer.size

        val bestFit = findBestHeartRatePeakFit(
            signal = filtered,
            sampleRateHz = sampleRateHz,
            bufferStartSampleIndex = bufferStartSampleIndex,
            segmentId = currentAnalysisSegmentId
        ) ?: return null

        val avgInterval = bestFit.usedIntervals
            .map { it.intervalSec }
            .average()

        if (avgInterval <= 0.0) return null

        val bpm = (60.0 / avgInterval).roundToInt()
        if (bpm !in HEART_RATE_MIN_BPM..HEART_RATE_MAX_BPM) return null

        val baseQuality = calculateHeartRateEstimateQuality(
            intervals = bestFit.usedIntervals,
            peakAmplitude = peakAmplitude
        )

        // 기존 quality에 peak fitting 자체의 신뢰도를 조금 반영한다.
        // SDSD 80ms 이상이면 fitting 신뢰도를 거의 0으로 보고,
        // raw interval 중 실제로 유지된 비율도 함께 반영한다.
        val sdsdQuality =
            (1.0 - bestFit.sdsdSec / 0.080).coerceIn(0.0, 1.0)

        val fitQuality =
            (sdsdQuality * 0.65 + bestFit.acceptedIntervalRatio * 0.35)
                .coerceIn(0.0, 1.0)

        val qualityScore =
            (baseQuality * 0.75 + fitQuality * 0.25)
                .coerceIn(0.0, 1.0)

        return HeartRateEstimate(
            bpm = bpm,
            ibiIntervals = bestFit.usedIntervals,
            peakCount = bestFit.peakPositions.size,
            intervalCount = bestFit.usedIntervals.size,
            averageIntervalSec = avgInterval,
            qualityScore = qualityScore,
            selectedThresholdPercent = bestFit.thresholdPercent,
            peakFitSdsdMs = bestFit.sdsdSec * 1000.0,
            rawIntervalCount = bestFit.rawIntervals.size,
            acceptedIntervalRatio = bestFit.acceptedIntervalRatio,
            meanPeakInterpolationOffsetMs = bestFit.meanInterpolationOffsetMs,
            maxPeakInterpolationOffsetMs = bestFit.maxInterpolationOffsetMs
        )
    }

    private fun preprocessPpgForHeartRate(
        rawSignal: List<Double>
    ): DoubleArray {
        val sampleRateHz = 100.0

        // 1) DC 제거. raw 값 자체의 offset은 HR peak 검출에 필요 없다.
        val mean = rawSignal.average()
        val acSignal = DoubleArray(rawSignal.size) { i ->
            rawSignal[i] - mean
        }

        // 2) HeartPy의 Hampel filter 아이디어: 순간적으로 튄 값을 주변 median으로 눌러준다.
        //    원본 HeartPy는 median + 3*MAD보다 큰 값을 교정한다.
        //    여기서는 PPG 착용 흔들림을 고려해 양/음 방향 모두 교정한다.
        val spikeSuppressed = hampelFilterSymmetric(
            data = acSignal,
            halfWindowSamples = 3,
            thresholdScale = 3.0
        )

        // 3) HeartPy filter_signal(..., cutoff=[0.75, 3.5], filtertype="bandpass")에 해당.
        //    0.75~3.5Hz = 약 45~210bpm 대역만 남긴다.
        //    scipy.filtfilt 대신 rolling buffer에 forward-backward one-pole bandpass를 적용한다.
        val bandPassed = forwardBackwardBandPass(
            data = spikeSuppressed,
            sampleRateHz = sampleRateHz,
            lowCutHz = 0.75,
            highCutHz = 3.5
        )

        // 4) PPG 신호 극성이 뒤집혀 들어올 수 있으므로, 양수 peak와 음수 peak 중 더 강한 쪽을 선택한다.
        //    HeartPy의 return_top=True처럼 peak 검출에 필요한 위쪽 성분만 남긴다.
        val positiveTop = DoubleArray(bandPassed.size) { i ->
            if (bandPassed[i] > 0.0) bandPassed[i] else 0.0
        }
        val negativeTop = DoubleArray(bandPassed.size) { i ->
            val inverted = -bandPassed[i]
            if (inverted > 0.0) inverted else 0.0
        }

        val positiveMax = positiveTop.maxOrNull() ?: 0.0
        val negativeMax = negativeTop.maxOrNull() ?: 0.0

        return if (positiveMax >= negativeMax) positiveTop else negativeTop
    }

    private fun hampelFilterSymmetric(
        data: DoubleArray,
        halfWindowSamples: Int,
        thresholdScale: Double
    ): DoubleArray {
        if (data.isEmpty()) return data

        val output = data.copyOf()

        for (i in data.indices) {
            val from = (i - halfWindowSamples).coerceAtLeast(0)
            val toExclusive = (i + halfWindowSamples + 1).coerceAtMost(data.size)
            val window = data.copyOfRange(from, toExclusive)

            val median = median(window.toList())
            val deviations = DoubleArray(window.size) { idx ->
                abs(window[idx] - median)
            }
            val mad = median(deviations.toList())

            // MAD가 0이면 주변 값이 거의 같은 것이므로 교정하지 않는다.
            if (mad <= 0.0) continue

            // 1.4826은 MAD를 표준편차에 가깝게 보정하는 계수.
            val threshold = thresholdScale * 1.4826 * mad

            if (abs(data[i] - median) > threshold) {
                output[i] = median
            }
        }

        return output
    }

    private fun forwardBackwardBandPass(
        data: DoubleArray,
        sampleRateHz: Double,
        lowCutHz: Double,
        highCutHz: Double
    ): DoubleArray {
        if (data.isEmpty()) return data

        val forward = onePoleBandPass(
            data = data,
            sampleRateHz = sampleRateHz,
            lowCutHz = lowCutHz,
            highCutHz = highCutHz
        )

        val backward = onePoleBandPass(
            data = forward.reversedArray(),
            sampleRateHz = sampleRateHz,
            lowCutHz = lowCutHz,
            highCutHz = highCutHz
        )

        return backward.reversedArray()
    }

    private fun onePoleBandPass(
        data: DoubleArray,
        sampleRateHz: Double,
        lowCutHz: Double,
        highCutHz: Double
    ): DoubleArray {
        val highPassed = onePoleHighPass(
            data = data,
            sampleRateHz = sampleRateHz,
            cutoffHz = lowCutHz
        )

        return onePoleLowPass(
            data = highPassed,
            sampleRateHz = sampleRateHz,
            cutoffHz = highCutHz
        )
    }

    private fun onePoleHighPass(
        data: DoubleArray,
        sampleRateHz: Double,
        cutoffHz: Double
    ): DoubleArray {
        if (data.isEmpty()) return data

        val output = DoubleArray(data.size)
        val dt = 1.0 / sampleRateHz
        val rc = 1.0 / (2.0 * Math.PI * cutoffHz)
        val alpha = rc / (rc + dt)

        output[0] = 0.0

        for (i in 1 until data.size) {
            output[i] = alpha * (output[i - 1] + data[i] - data[i - 1])
        }

        return output
    }

    private fun onePoleLowPass(
        data: DoubleArray,
        sampleRateHz: Double,
        cutoffHz: Double
    ): DoubleArray {
        if (data.isEmpty()) return data

        val output = DoubleArray(data.size)
        val dt = 1.0 / sampleRateHz
        val rc = 1.0 / (2.0 * Math.PI * cutoffHz)
        val alpha = dt / (rc + dt)

        output[0] = data[0]

        for (i in 1 until data.size) {
            output[i] = output[i - 1] + alpha * (data[i] - output[i - 1])
        }

        return output
    }

    /**
     * 여러 이동평균 임계값 후보를 시험하고 가장 신뢰할 수 있는 peak fitting 결과를 선택한다.
     *
     * 선택 기준:
     * 1. 최종 BPM이 40~180 범위
     * 2. raw/정제 IBI가 충분함
     * 3. SDSD가 작음
     * 4. IBI 후처리에서 너무 많은 interval이 제거되지 않음
     * 5. 비슷한 결과라면 더 많은 interval을 유지한 후보를 우선함
     */
    private fun findBestHeartRatePeakFit(
        signal: DoubleArray,
        sampleRateHz: Double,
        bufferStartSampleIndex: Long,
        segmentId: Long
    ): HeartRatePeakFitCandidate? {
        if (signal.size < 3 || sampleRateHz <= 0.0) return null

        val movingAverageWindowSamples =
            (sampleRateHz * HEART_RATE_MOVING_AVERAGE_SECONDS)
                .roundToInt()
                .coerceAtLeast(3)

        val movingAverage = calculateCenteredMovingAverage(
            signal = signal,
            windowSamples = movingAverageWindowSamples
        )

        val movingAverageMean = movingAverage.average()
        if (movingAverageMean <= 0.0) return null

        val minPeakDistanceSamples =
            (sampleRateHz * 60.0 / HEART_RATE_MAX_BPM)
                .roundToInt()
                .coerceAtLeast(1)

        val candidates = mutableListOf<HeartRatePeakFitCandidate>()

        for (thresholdPercent in HEART_RATE_THRESHOLD_PERCENT_CANDIDATES) {
            // HeartPy의 rol_mean + ma_perc 방식과 비슷하게,
            // 이동평균 전체의 평균값에 대한 일정 비율을 offset으로 더한다.
            val thresholdOffset =
                movingAverageMean * thresholdPercent / 100.0

            val peakIndices = detectHeartRatePeaksByRoi(
                signal = signal,
                movingAverage = movingAverage,
                thresholdOffset = thresholdOffset,
                minPeakDistanceSamples = minPeakDistanceSamples
            )

            // SDSD를 평가하려면 최소 4개 peak = 3개 IBI가 필요하다.
            if (peakIndices.size < 4) continue

            // 100Hz 정수 index를 그대로 사용하면 peak timing이 10ms 격자에 고정된다.
            // 각 peak와 좌우 sample의 포물선을 적합해 정수 index 사이의 peak 위치를 추정한다.
            val peakPositions = refineHeartRatePeakPositions(
                signal = signal,
                peakIndices = peakIndices
            )

            if (peakPositions.size < 4) continue

            val interpolationOffsetsMs = peakPositions.indices.map { i ->
                abs(peakPositions[i] - peakIndices[i].toDouble()) /
                        sampleRateHz * 1000.0
            }

            val meanInterpolationOffsetMs =
                interpolationOffsetsMs.average()

            val maxInterpolationOffsetMs =
                interpolationOffsetsMs.maxOrNull() ?: 0.0

            // 후보 fitting 평가에서는 생리 범위 밖 interval도 우선 보존한다.
            // 먼저 버리면 짧은 가짜 peak가 만든 interval이 사라져 나쁜 후보가 좋아 보일 수 있다.
            val rawIntervals = buildHeartRateIntervals(
                peakPositions = peakPositions,
                bufferStartSampleIndex = bufferStartSampleIndex,
                sampleRateHz = sampleRateHz,
                segmentId = segmentId,
                enforcePhysiologicalRange = false
            )

            if (rawIntervals.size < 3) continue

            val rawAverageInterval =
                rawIntervals.map { it.intervalSec }.average()

            if (rawAverageInterval <= 0.0) continue

            val rawBpm = 60.0 / rawAverageInterval
            if (rawBpm !in HEART_RATE_MIN_BPM.toDouble()..HEART_RATE_MAX_BPM.toDouble()) {
                continue
            }

            // threshold fitting 자체는 생리 범위 제거 및 quotient/median 후처리 전 SDSD로 평가한다.
            // 그래야 짧은 가짜 IBI나 긴 누락 IBI가 후보 점수에 그대로 불이익을 준다.
            val sdsdSec = calculateSdsd(
                rawIntervals.map { it.intervalSec }
            ) ?: continue

            val physiologicalIntervals = rawIntervals.filter { interval ->
                interval.intervalSec in
                        (60.0 / HEART_RATE_MAX_BPM)..(60.0 / HEART_RATE_MIN_BPM)
            }

            val physiologicalIntervalRatio =
                physiologicalIntervals.size.toDouble() / rawIntervals.size.toDouble()

            if (physiologicalIntervalRatio < HEART_RATE_MIN_PHYSIOLOGICAL_INTERVAL_RATIO) {
                continue
            }

            val usedIntervals = filterHeartRateIntervals(
                physiologicalIntervals.toMutableList()
            )

            if (usedIntervals.size < 2) continue

            val usedAverageInterval =
                usedIntervals.map { it.intervalSec }.average()

            if (usedAverageInterval <= 0.0) continue

            val finalBpm = 60.0 / usedAverageInterval
            if (finalBpm !in HEART_RATE_MIN_BPM.toDouble()..HEART_RATE_MAX_BPM.toDouble()) {
                continue
            }

            val acceptedIntervalRatio =
                usedIntervals.size.toDouble() / rawIntervals.size.toDouble()

            // 후처리에서 절반 이상 버려지는 후보는 peak fitting 자체가 불안정하다고 판단한다.
            if (acceptedIntervalRatio < HEART_RATE_MIN_ACCEPTED_INTERVAL_RATIO) {
                continue
            }

            // SDSD가 가장 중요한 평가값이다.
            // 단, IBI가 적거나 제거 비율이 높은 후보가 우연히 유리해지지 않도록 작은 penalty를 더한다.
            val countPenaltySec =
                HEART_RATE_COUNT_PENALTY_SEC / sqrt(rawIntervals.size.toDouble())

            val rejectionPenaltySec =
                (1.0 - acceptedIntervalRatio) * HEART_RATE_REJECTION_PENALTY_SEC

            val selectionScore =
                sdsdSec + countPenaltySec + rejectionPenaltySec

            candidates += HeartRatePeakFitCandidate(
                thresholdPercent = thresholdPercent,
                thresholdOffset = thresholdOffset,
                peakIndices = peakIndices,
                peakPositions = peakPositions,
                rawIntervals = rawIntervals,
                usedIntervals = usedIntervals,
                rawBpm = rawBpm,
                finalBpm = finalBpm,
                sdsdSec = sdsdSec,
                acceptedIntervalRatio = acceptedIntervalRatio,
                selectionScore = selectionScore,
                meanInterpolationOffsetMs = meanInterpolationOffsetMs,
                maxInterpolationOffsetMs = maxInterpolationOffsetMs
            )
        }

        return candidates.minWithOrNull(
            compareBy<HeartRatePeakFitCandidate> { it.selectionScore }
                .thenByDescending { it.usedIntervals.size }
                .thenByDescending { it.acceptedIntervalRatio }
        )
    }

    /**
     * 중심 정렬 이동평균을 O(n) prefix-sum 방식으로 계산한다.
     * 양 끝에서는 사용할 수 있는 실제 구간 길이만으로 평균을 낸다.
     */
    private fun calculateCenteredMovingAverage(
        signal: DoubleArray,
        windowSamples: Int
    ): DoubleArray {
        if (signal.isEmpty()) return signal

        val safeWindow = windowSamples
            .coerceAtLeast(1)
            .coerceAtMost(signal.size)

        val leftHalf = safeWindow / 2
        val rightHalf = safeWindow - leftHalf

        val prefixSum = DoubleArray(signal.size + 1)
        for (i in signal.indices) {
            prefixSum[i + 1] = prefixSum[i] + signal[i]
        }

        return DoubleArray(signal.size) { i ->
            val from = (i - leftHalf).coerceAtLeast(0)
            val toExclusive = (i + rightHalf).coerceAtMost(signal.size)
            val count = (toExclusive - from).coerceAtLeast(1)
            (prefixSum[toExclusive] - prefixSum[from]) / count
        }
    }

    /**
     * signal > movingAverage + offset인 연속 구간을 ROI로 보고,
     * 각 ROI 내부에서 가장 높은 sample 하나만 peak 후보로 선택한다.
     *
     * ROI가 너무 가까이 붙어 있으면 생리적 최소 peak 거리 안에서 더 높은 peak만 유지한다.
     */
    private fun detectHeartRatePeaksByRoi(
        signal: DoubleArray,
        movingAverage: DoubleArray,
        thresholdOffset: Double,
        minPeakDistanceSamples: Int
    ): List<Int> {
        if (signal.size != movingAverage.size || signal.size < 3) {
            return emptyList()
        }

        val roiPeaks = mutableListOf<Int>()
        var roiStart = -1

        for (i in signal.indices) {
            val threshold = movingAverage[i] + thresholdOffset
            val aboveThreshold = signal[i] > threshold

            if (aboveThreshold && roiStart < 0) {
                roiStart = i
            }

            val roiEnds =
                roiStart >= 0 && (!aboveThreshold || i == signal.lastIndex)

            if (!roiEnds) continue

            val roiEnd =
                if (aboveThreshold && i == signal.lastIndex) i else i - 1

            if (roiEnd >= roiStart) {
                var maxIndex = roiStart

                for (j in roiStart + 1..roiEnd) {
                    if (signal[j] > signal[maxIndex]) {
                        maxIndex = j
                    }
                }

                // 가장자리 sample은 온전한 local peak인지 확인하기 어려우므로 제외한다.
                if (maxIndex in 1 until signal.lastIndex) {
                    roiPeaks += maxIndex
                }
            }

            roiStart = -1
        }

        if (roiPeaks.isEmpty()) return emptyList()

        val distanceFiltered = mutableListOf<Int>()

        for (candidate in roiPeaks) {
            if (distanceFiltered.isEmpty()) {
                distanceFiltered += candidate
                continue
            }

            val previous = distanceFiltered.last()
            val distance = candidate - previous

            if (distance >= minPeakDistanceSamples) {
                distanceFiltered += candidate
            } else if (signal[candidate] > signal[previous]) {
                // 동일 박동 안에서 두 ROI가 생겼다면 더 높은 peak로 교체한다.
                distanceFiltered[distanceFiltered.lastIndex] = candidate
            }
        }

        return distanceFiltered
    }

    /**
     * 정수 sample index로 검출된 peak를 3점 포물선 보간으로 보정한다.
     *
     * y[-1], y[0], y[+1]을 지나는 포물선의 꼭짓점 위치를 구하며,
     * 검출된 중심 sample을 기준으로 최대 ±0.5 sample까지만 이동시킨다.
     * 100Hz에서는 최대 ±5ms 보정이며, 새로운 센서 정보를 만드는 것이 아니라
     * 이산 sampling으로 생긴 10ms 격자 오차를 줄이는 추정이다.
     */
    private fun refineHeartRatePeakPositions(
        signal: DoubleArray,
        peakIndices: List<Int>
    ): List<Double> {
        return peakIndices.map { peakIndex ->
            refineHeartRatePeakPositionQuadratic(
                signal = signal,
                peakIndex = peakIndex
            )
        }
    }

    private fun refineHeartRatePeakPositionQuadratic(
        signal: DoubleArray,
        peakIndex: Int
    ): Double {
        if (peakIndex <= 0 || peakIndex >= signal.lastIndex) {
            return peakIndex.toDouble()
        }

        val left = signal[peakIndex - 1]
        val center = signal[peakIndex]
        val right = signal[peakIndex + 1]

        if (!left.isFinite() || !center.isFinite() || !right.isFinite()) {
            return peakIndex.toDouble()
        }

        // ROI 최댓값이 실제 local maximum이 아닌 경우에는 보간하지 않는다.
        if (center < left || center < right) {
            return peakIndex.toDouble()
        }

        val denominator = left - 2.0 * center + right

        // 평평한 꼭대기나 거의 직선인 경우 꼭짓점 위치가 불안정하므로 정수 위치를 유지한다.
        val scale = maxOf(abs(left), abs(center), abs(right), 1.0)
        if (abs(denominator) <= scale * 1e-12) {
            return peakIndex.toDouble()
        }

        val rawOffset =
            0.5 * (left - right) / denominator

        if (!rawOffset.isFinite()) {
            return peakIndex.toDouble()
        }

        val boundedOffset =
            rawOffset.coerceIn(-0.5, 0.5)

        return peakIndex.toDouble() + boundedOffset
    }

    /**
     * IBI successive difference의 표준편차(SDSD)를 초 단위로 계산한다.
     * 최소 3개 IBI가 있어야 두 개 이상의 successive difference를 만들 수 있다.
     */
    private fun calculateSdsd(
        intervalsSec: List<Double>
    ): Double? {
        if (intervalsSec.size < 3) return null

        val successiveDiffs = DoubleArray(intervalsSec.size - 1) { i ->
            intervalsSec[i + 1] - intervalsSec[i]
        }

        if (successiveDiffs.size < 2) return null

        val meanDiff = successiveDiffs.average()
        var sumSquaredDeviation = 0.0

        for (diff in successiveDiffs) {
            val deviation = diff - meanDiff
            sumSquaredDeviation += deviation * deviation
        }

        val sdsd = sqrt(sumSquaredDeviation / successiveDiffs.size)
        return if (sdsd.isFinite()) sdsd else null
    }

    private fun buildHeartRateIntervals(
        peakPositions: List<Double>,
        bufferStartSampleIndex: Long,
        sampleRateHz: Double = 100.0,
        segmentId: Long,
        enforcePhysiologicalRange: Boolean = true
    ): MutableList<IbiInterval> {
        val intervals = mutableListOf<IbiInterval>()

        if (sampleRateHz <= 0.0) return intervals

        val minIntervalSec = 60.0 / HEART_RATE_MAX_BPM
        val maxIntervalSec = 60.0 / HEART_RATE_MIN_BPM

        for (i in 1 until peakPositions.size) {
            val diffSamples =
                peakPositions[i] - peakPositions[i - 1]

            val intervalSec =
                diffSamples / sampleRateHz

            if (!intervalSec.isFinite() || intervalSec <= 0.0) continue

            if (
                !enforcePhysiologicalRange ||
                intervalSec in minIntervalSec..maxIntervalSec
            ) {
                val endSamplePosition =
                    bufferStartSampleIndex.toDouble() + peakPositions[i]

                intervals.add(
                    IbiInterval(
                        intervalSec = intervalSec,
                        endSampleIndex = endSamplePosition.roundToLong(),
                        segmentId = segmentId,
                        endSamplePosition = endSamplePosition
                    )
                )
            }
        }

        return intervals
    }

    private fun filterHeartRateIntervals(
        intervals: MutableList<IbiInterval>
    ): List<IbiInterval> {
        if (intervals.size < 2) return intervals

        // HeartPy quotient_filter 아이디어:
        // 연속 RR interval의 비율이 0.8~1.2 범위를 벗어나면 튄 interval로 본다.
        var quotientFiltered = intervals.toList()

        repeat(2) {
            if (quotientFiltered.size < 2) return@repeat

            val reject = BooleanArray(quotientFiltered.size)

            for (i in 0 until quotientFiltered.size - 1) {
                val current = quotientFiltered[i].intervalSec
                val next = quotientFiltered[i + 1].intervalSec

                if (current <= 0.0 || next <= 0.0) {
                    reject[i] = true
                    continue
                }

                val ratio = current / next

                if (ratio < 0.8 || ratio > 1.2) {
                    reject[i] = true
                }
            }

            val nextFiltered = quotientFiltered.filterIndexed { index, _ ->
                !reject[index]
            }

            // 너무 많이 버리면 원래 interval을 유지한다.
            if (nextFiltered.size >= 2) {
                quotientFiltered = nextFiltered
            }
        }

        // 기존 코드의 median 40% outlier filter도 유지한다.
        if (quotientFiltered.size >= 3) {
            val median = median(quotientFiltered.map { it.intervalSec })

            val medianFiltered = quotientFiltered.filter {
                median > 0.0 && abs(it.intervalSec - median) / median < 0.40
            }

            if (medianFiltered.size >= 2) {
                return medianFiltered
            }
        }

        return quotientFiltered
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0

        val sorted = values.sorted()
        val mid = sorted.size / 2

        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        }
    }

    private fun calculateHeartRateEstimateQuality(
        intervals: List<IbiInterval>,
        peakAmplitude: Double
    ): Double {
        if (intervals.size < 2) return 0.0

        val intervalValues = intervals.map { it.intervalSec }
        val mean = intervalValues.average()

        if (mean <= 0.0) return 0.0

        var sumSquaredDiff = 0.0

        for (interval in intervalValues) {
            val diff = interval - mean
            sumSquaredDiff += diff * diff
        }

        val std = sqrt(sumSquaredDiff / intervalValues.size)
        val cv = std / mean

        val regularityScore =
            (1.0 - cv).coerceIn(0.0, 1.0)

        val intervalCountScore =
            (intervals.size / 6.0).coerceIn(0.0, 1.0)

        val amplitudeScore =
            (peakAmplitude / 1500.0).coerceIn(0.0, 1.0)

        return (
                regularityScore * 0.60 +
                        intervalCountScore * 0.25 +
                        amplitudeScore * 0.15
                ).coerceIn(0.0, 1.0)
    }

    /**
     * Zephyr의 crc16_ccitt 방식과 맞춘 CRC16 계산 함수.
     *
     * 특징:
     * - 초기값: 0xFFFF
     * - LSB-first
     * - reflected 방식
     *
     * 펌웨어에서 계산한 CRC와 앱에서 계산한 CRC가 같아야
     * Super Frame이 정상적으로 수신되었다고 볼 수 있다.
     */
    private fun zephyrCrc16(data: ByteArray): Int {
        // CRC 초기값
        var crc = 0xFFFF

        // 모든 바이트를 순회하면서 CRC 갱신
        for (byte in data) {
            /**
             * e = seed ^ *src
             *
             * 현재 CRC의 하위 8비트와 입력 byte를 XOR한다.
             */
            val e = (crc and 0xFF) xor (byte.toInt() and 0xFF)

            /**
             * f = e ^ (e << 4)
             *
             * Zephyr CRC 구현에 맞춘 중간값 계산.
             */
            val f = e xor ((e shl 4) and 0xFF)

            /**
             * seed = (seed >> 8) ^ (f << 8) ^ (f << 3) ^ (f >> 4)
             *
             * 계산 결과는 16-bit 범위로 유지해야 하므로 마지막에 & 0xFFFF를 적용한다.
             */
            crc =
                ((crc shr 8) xor
                        ((f shl 8) and 0xFFFF) xor
                        ((f shl 3) and 0xFFFF) xor
                        (f shr 4)) and 0xFFFF
        }

        // 최종 CRC16 값 반환
        return crc
    }

    /**
     * lastLog만 간단히 갱신하는 보조 함수.
     *
     * 매번 _state.update { it.copy(lastLog = ...) }를 반복하지 않기 위해 사용한다.
     */
    private fun updateLog(message: String) {
        _state.update {
            it.copy(lastLog = message)
        }
    }

    private fun addPacketError(
        type: String,
        message: String,
        fragCounter: Int? = null
    ) {
        _state.update { current ->
            val updatedErrors = listOf(
                PacketErrorLog(
                    type = type,
                    message = message,
                    fragCounter = fragCounter
                )
            ) + current.recentPacketErrors

            current.copy(
                lastLog = message,
                recentPacketErrors = updatedErrors.take(8)
            )
        }
    }

    private fun counterDistance(expected: Int, actual: Int): Int {
        // counter는 12bit라서 0~4095 순환
        return (actual - expected) and 0x0FFF
    }

    /**
     * 테스트용 정상 Mini Packet 생성 함수.
     *
     * 실제 BLE에서 들어온 것처럼 204 bytes packet을 만든다.
     * - 앞 2 bytes: Mini Header
     * - 나머지 202 bytes: Payload
     */
    private fun makeDebugMiniPacket(
        counter: Int,
        prefix: Int = 0x5,
        payloadStartWithSuperHeader: Boolean = true
    ): ByteArray {
        val packet = ByteArray(fragmentSize) { 0x00 }

        val miniHeader = ((prefix and 0xF) shl 12) or (counter and 0x0FFF)

        packet[0] = ((miniHeader shr 8) and 0xFF).toByte()
        packet[1] = (miniHeader and 0xFF).toByte()

        if (payloadStartWithSuperHeader) {
            packet[2] = 0xAA.toByte()
            packet[3] = 0xAA.toByte()
        }

        return packet
    }

    /**
     * 1. 길이 오류 테스트.
     *
     * Mini Packet은 반드시 204 bytes여야 하는데,
     * 일부러 197 bytes짜리 packet을 넣어서 Length Drop이 뜨는지 확인한다.
     */
    fun debugTestLengthError() {
        val wrongLengthPacket = ByteArray(197) { 0x00 }
        processIncomingData(wrongLengthPacket)
    }

    /**
     * 2. Mini Header prefix 오류 테스트.
     *
     * Mini Header 상위 4bit는 반드시 0x5여야 한다.
     * 일부러 0x3으로 만들어 Header Prefix Drop이 뜨는지 확인한다.
     */
    fun debugTestMiniHeaderError() {
        val wrongHeaderPacket = makeDebugMiniPacket(
            counter = 1,
            prefix = 0x3,
            payloadStartWithSuperHeader = true
        )

        processIncomingData(wrongHeaderPacket)
    }

    /**
     * 3. Sequence 손실 테스트.
     *
     * 다음에 counter 10이 와야 하는 상황을 만든 뒤,
     * 실제로는 counter 12를 넣어서 10, 11번 packet이 손실된 것처럼 만든다.
     */
    fun debugTestSequenceLoss() {
        buffer.clear()
        expectedFragCounter = 10

        val skippedPacket = makeDebugMiniPacket(
            counter = 12,
            prefix = 0x5,
            payloadStartWithSuperHeader = true
        )

        processIncomingData(skippedPacket)
    }

    /**
     * 4. Super Header 오류 테스트.
     *
     * 새 Super Frame의 시작 payload는 0xAA 0xAA여야 한다.
     * 일부러 0xBB 0xBB로 시작하게 해서 Sync/Super Header 오류를 확인한다.
     */
    fun debugTestSuperHeaderError() {
        buffer.clear()
        expectedFragCounter = null

        val packet = makeDebugMiniPacket(
            counter = 20,
            prefix = 0x5,
            payloadStartWithSuperHeader = false
        )

        packet[2] = 0xBB.toByte()
        packet[3] = 0xBB.toByte()

        processIncomingData(packet)
    }

    /**
     * 5. CRC 오류 테스트.
     *
     * 1212 bytes짜리 Super Frame을 직접 만들고,
     * CRC 값을 일부러 틀리게 넣어서 CRC 오류가 뜨는지 확인한다.
     */
    fun debugTestCrcError() {
        buffer.clear()
        expectedFragCounter = null

        val superFrame = ByteArray(superFrameSize) { 0x00 }

        // Super Header
        superFrame[0] = 0xAA.toByte()
        superFrame[1] = 0xAA.toByte()

        // NTC raw 예시값
        superFrame[2] = 0x05
        superFrame[3] = 0xDC.toByte()

        // Timestamp 예시값, little endian
        superFrame[4] = 0x01
        superFrame[5] = 0x02
        superFrame[6] = 0x03
        superFrame[7] = 0x04

        // Battery raw 예시값
        superFrame[8] = 0x0A
        superFrame[9] = 0xAF.toByte()

        // CRC를 일부러 틀리게 넣음
        superFrame[10] = 0x12
        superFrame[11] = 0x34

        parseSuperFrame(superFrame)
    }

    /**
     * 6. Counter wrap-around 테스트.
     *
     * counter는 4095 다음에 0으로 돌아가야 한다.
     * 4095 → 0 순서가 정상으로 처리되는지 확인한다.
     */
    fun debugTestCounterWrapAround() {
        reset()

        val packet4095 = makeDebugMiniPacket(
            counter = 4095,
            prefix = 0x5,
            payloadStartWithSuperHeader = true
        )

        val packet0 = makeDebugMiniPacket(
            counter = 0,
            prefix = 0x5,
            payloadStartWithSuperHeader = false
        )

        processIncomingData(packet4095)
        processIncomingData(packet0)

        updateLog("DEBUG: Counter wrap-around test completed. 4095 → 0")
    }

    /**
     * 분석 데이터의 연속성이 끊겼음을 기록하고 새 segment를 시작한다.
     *
     * 심박 raw window를 즉시 비워 누락 전 peak와 누락 후 peak가 하나의 IBI로
     * 연결되는 것을 막는다. ArousalCalculator에도 같은 segment ID를 전달해
     * PPG/IMU raw window와 HRV 계산을 분리한다.
     */
    private fun advanceAnalysisSegment(reason: String) {
        currentAnalysisSegmentId += 1L

        heartRateCombinedBuffer.clear()
        lastValidHeartRateInputTimestampMillis = null
        lastValidHeartRateEstimateTimestampMillis = null

        val gapState = arousalCalculator.onDataDiscontinuity(
            newSegmentId = currentAnalysisSegmentId,
            reason = reason
        )

        Log.w(
            TAG,
            "Analysis continuity break: segment=$currentAnalysisSegmentId, reason=$reason"
        )
        dataLogger?.logDebug(
            TAG,
            "Analysis continuity break: segment=$currentAnalysisSegmentId, reason=$reason",
            "W"
        )

        _state.update { current ->
            current.copy(
                heartRateBpm = null,
                heartRateQuality = null,
                heartRateCalculationStatus = MetricCalculationStatus(
                    state = MetricCalculationState.REJECTED,
                    message = "데이터 연속성 중단: $reason"
                ),
                arousalState = gapState,
                analysisSegmentId = currentAnalysisSegmentId,
                continuityBreakCount = current.continuityBreakCount + 1,
                lastContinuityBreakReason = reason
            )
        }
    }

    private fun currentMiniPacketIndexInFrame(): Int {
        // payload 202B 단위로 몇 개 쌓였는지 계산
        // 다음에 들어올 패킷 번호이므로 +1
        return (buffer.size / 202) + 1
    }

    private fun logMissFrameAndClear(reason: String, missPacketNum: Int?) {
        // Fragment 길이/헤더/순서 오류는 샘플 연속성이 끊긴 사건이다.
        advanceAnalysisSegment(reason)

        currentFrameErrors.add(reason)

        if (missPacketNum != null) {
            currentFrameMissPacketNums.add(missPacketNum.coerceIn(1, 6))
        }

        val partialFrame = buffer.toByteArray()

        dataLogger?.logSuperFrame(
            phoneTimeMillis = System.currentTimeMillis(),
            timestamp = null,
            superFrame = partialFrame,
            complete = "miss",
            missPacketNum = currentFrameMissPacketNums
                .distinct()
                .sorted()
                .joinToString("|"),
            errorLog = currentFrameErrors.joinToString(" / ")
        )

        buffer.clear()
        currentFrameErrors.clear()
        currentFrameMissPacketNums.clear()
    }
    @Synchronized
    fun updateMicroMovementBandPass(
        lowCutHz: Double,
        highCutHz: Double
    ) {
        arousalCalculator.updateMicroMovementBandPass(
            lowCutHz = lowCutHz,
            highCutHz = highCutHz
        )

        _state.update {
            it.copy(
                lastLog = "Micro BPF updated: %.2f~%.2fHz".format(lowCutHz, highCutHz)
            )
        }

        dataLogger?.logDebug(
            TAG,
            "Micro BPF updated: low=$lowCutHz, high=$highCutHz"
        )
    }

}