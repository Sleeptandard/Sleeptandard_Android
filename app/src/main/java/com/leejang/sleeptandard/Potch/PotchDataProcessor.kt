package com.leejang.sleeptandard.Potch

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
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

/**
 * 한 번의 HR 분석 시도가 어떤 상태로 끝났는지 구분한다.
 *
 * HELD_PREVIOUS는 새 HR 계산에는 실패했지만 화면에는 제한 시간 동안
 * 마지막 정상값을 유지하고 있음을 뜻한다. 실제 실패 원인은
 * [HeartRateDiagnostics.underlyingFailureReason]에 별도로 남긴다.
 */
enum class HeartRateProcessingState {
    COLLECTING,
    NO_CONTACT,
    SIGNAL_TOO_WEAK,
    SIGNAL_SATURATED,
    MOTION_ARTIFACT,
    LOW_SPECTRAL_CONCENTRATION,
    HIGH_SPECTRAL_ENTROPY,
    AMPLITUDE_UNSTABLE,
    ABRUPT_SIGNAL_CHANGE,
    INSUFFICIENT_PEAKS,
    INVALID_IBI,
    BPM_OUT_OF_RANGE,
    PACKET_LOSS,
    VALID,
    HELD_PREVIOUS
}

enum class HeartRatePeakPolarity {
    POSITIVE,
    NEGATIVE,
    NONE
}

/**
 * 매 SuperFrame마다 HR 계산에 사용된 신호 특징과 성공/실패 사유를 저장한다.
 *
 * 이 객체는 UI 상태와 potch_hr_diagnostic_log CSV에 그대로 사용된다.
 */
data class HeartRateDiagnostics(
    val processingState: HeartRateProcessingState = HeartRateProcessingState.COLLECTING,
    val underlyingFailureReason: HeartRateProcessingState? = null,
    val message: String = "PPG 심박 신호 수집 중",

    val analysisSegmentId: Long = 0L,
    val windowSampleCount: Int = 0,
    val windowSeconds: Double = 0.0,

    val irDcMean: Double? = null,
    val irMin: Double? = null,
    val irMax: Double? = null,

    // band-pass 출력의 5~95 percentile 폭. 극단 spike 한두 개에 덜 민감하다.
    val acRobustAmplitude: Double? = null,

    // 최근 window를 1초 단위로 나눈 AC 진폭들의 변동계수.
    // 자세/접촉 변화로 pulse amplitude가 크게 출렁이는지 확인한다.
    val amplitudeCoefficientOfVariation: Double? = null,

    // HR 허용 주파수 대역에서 가장 강한 bin이 차지하는 power 비율과
    // 정규화 spectral entropy. 집중도는 높을수록, entropy는 낮을수록 좋다.
    val spectralConcentration: Double? = null,
    val spectralEntropy: Double? = null,

    // raw PPG의 최대 연속 sample 변화량을 robust amplitude로 나눈 값.
    // 센서 raw scale이 달라도 갑작스러운 spike를 비교할 수 있도록 정규화한다.
    val abruptChangeRatio: Double? = null,

    // 선택된 moving-average threshold의 실제 offset과 백분율.
    val selectedPeakThreshold: Double? = null,
    val selectedThresholdPercent: Double? = null,
    val selectedPolarity: HeartRatePeakPolarity = HeartRatePeakPolarity.NONE,

    val detectedPeakCount: Int = 0,
    val rawIbiCount: Int = 0,
    val validIbiCount: Int = 0,
    val acceptedIntervalRatio: Double? = null,

    // 이상치 제거 전 raw IBI의 품질값.
    val rawSdsdMs: Double? = null,
    val rawIbiCv: Double? = null,
    val physiologicalIntervalRatio: Double? = null,
    val rawIntervalQualityScore: Double? = null,

    // 이전 CSV 호환용. rawSdsdMs와 같은 값이다.
    val sdsdMs: Double? = null,

    // 최종 HR estimate quality. raw interval 품질을 강하게 반영한다.
    val qualityScore: Double? = null,

    // 이번 window에서 새로 계산한 BPM과 실제 화면 표시 BPM을 분리한다.
    val calculatedBpm: Int? = null,
    val displayedBpm: Int? = null,
    val heartRateFresh: Boolean = false,
    val heartRateAgeMillis: Long? = null,

    // 현재 1초 IMU frame의 g-magnitude 연속 변화 최대값.
    val imuMaxDeltaG: Double? = null,

    // HR rolling window의 raw PPG 연속 sample 변화 최대값.
    val maxRawSampleDelta: Double? = null,

    val crcErrorCount: Int = 0,
    val sequenceLossCount: Int = 0,
    val estimatedLostPacketCount: Int = 0
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
    val selectedPeakThreshold: Double? = null,
    val selectedPolarity: HeartRatePeakPolarity = HeartRatePeakPolarity.NONE,
    val peakFitSdsdMs: Double? = null,
    val rawIntervalCount: Int = intervalCount,
    val acceptedIntervalRatio: Double = 1.0,
    val rawIbiCv: Double = 0.0,
    val physiologicalIntervalRatio: Double = 1.0,
    val rawIntervalQualityScore: Double = 1.0,
    val spectralConcentration: Double? = null,
    val spectralEntropy: Double? = null,
    val amplitudeCoefficientOfVariation: Double? = null,
    val abruptChangeRatio: Double? = null,

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

    // 이번 SuperFrame에서 새 HR이 계산됐는지와 마지막 정상 HR의 나이.
    val heartRateFresh: Boolean = false,
    val heartRateAgeMillis: Long? = null,

    // 매초 기록되는 상세 HR 분석 상태.
    val heartRateDiagnostics: HeartRateDiagnostics = HeartRateDiagnostics(),

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

        // 후보 hard reject 기준.
        // 정제 후 IBI 4개, 원본 대비 70% 유지, raw SDSD 200ms 이하를 모두 만족해야 한다.
        private const val HEART_RATE_MIN_USED_INTERVAL_COUNT = 4
        private const val HEART_RATE_MIN_ACCEPTED_INTERVAL_RATIO = 0.70
        private const val HEART_RATE_MAX_RAW_SDSD_SEC = 0.200
        private const val HEART_RATE_MIN_PHYSIOLOGICAL_INTERVAL_RATIO = 0.75

        // raw interval quality 점수 기준.
        private const val HEART_RATE_RAW_IBI_CV_ZERO_SCORE = 0.30
        private const val HEART_RATE_PREFERRED_USED_INTERVAL_COUNT = 8

        // BVP 주파수/안정성 gate의 초기 기준.
        private const val HEART_RATE_MIN_SPECTRAL_CONCENTRATION = 0.12
        private const val HEART_RATE_MAX_SPECTRAL_ENTROPY = 0.82
        private const val HEART_RATE_MAX_AMPLITUDE_CV = 0.50
        private const val HEART_RATE_MAX_ABRUPT_CHANGE_RATIO = 1.00

        // Potch MAX3010x 계열 PPG는 18-bit 범위를 사용한다.
        private const val HEART_RATE_PPG_ADC_MAX = 262143.0
        private const val HEART_RATE_CONTACT_DC_MIN = 10000.0
        private const val HEART_RATE_SATURATION_HIGH = 260000.0
        private const val HEART_RATE_MIN_ROBUST_AC_AMPLITUDE = 80.0

        // 1초 IMU frame에서 이보다 큰 연속 g-magnitude 변화가 있으면
        // HR peak fitting 성공 여부와 무관하게 강한 움직임 artifact로 거부한다.
        private const val HEART_RATE_MOTION_MAX_DELTA_G = 0.15

        private const val HEART_RATE_IMU_LSB_PER_G = 1024.0

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
        val polarity: HeartRatePeakPolarity,
        val thresholdPercent: Double,
        val thresholdOffset: Double,
        val peakIndices: List<Int>,
        val peakPositions: List<Double>,
        val rawIntervals: List<IbiInterval>,
        val usedIntervals: List<IbiInterval>,
        val rawBpm: Double,
        val finalBpm: Double,

        // 모두 이상치 제거 전 raw IBI에서 계산한다.
        val sdsdSec: Double,
        val rawIbiCv: Double,
        val physiologicalIntervalRatio: Double,
        val acceptedIntervalRatio: Double,
        val rawIntervalQualityScore: Double,
        val selectionScore: Double,
        val meanInterpolationOffsetMs: Double,
        val maxInterpolationOffsetMs: Double
    )

    private data class HeartRatePeakFitSearchResult(
        val bestCandidate: HeartRatePeakFitCandidate?,

        // hard reject된 후보 중 가장 나았던 후보. 실패 CSV에도 raw 품질을 남기기 위해 보존한다.
        val bestRejectedCandidate: HeartRatePeakFitCandidate?,
        val maxDetectedPeakCount: Int,
        val maxRawIntervalCount: Int,
        val maxValidIntervalCount: Int,
        val sawInvalidIbi: Boolean,
        val sawBpmOutOfRange: Boolean
    )

    private data class HeartRateSpectralQuality(
        val concentration: Double,
        val entropy: Double,
        val evaluatedBinCount: Int
    )

    private data class HeartRateAnalysisResult(
        val estimate: HeartRateEstimate?,
        val diagnostics: HeartRateDiagnostics
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
    private val heartRateIrBuffer = ArrayDeque<Int>()

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
    private var lastValidHeartRateEstimate: HeartRateEstimate? = null

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
        heartRateIrBuffer.clear()
        totalHeartRateCombinedSampleCount = 0L
        lastValidHeartRateInputTimestampMillis = null
        lastValidHeartRateEstimateTimestampMillis = null
        lastValidHeartRateEstimate = null

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
        val frameImuMaxDeltaG = calculateImuMaxDeltaG(imuData)

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
            appendPpgSamplesToHrBuffers(
                irSamples = irSamples,
                combinedSamples = avgSamples
            )
            lastValidHeartRateInputTimestampMillis = phoneTimeMillis
        }

        val rawHeartRateAnalysis =
            if (isFrameUsableForAnalysis) {
                estimateHeartRate(
                    imuMaxDeltaG = frameImuMaxDeltaG
                )
            } else {
                HeartRateAnalysisResult(
                    estimate = null,
                    diagnostics = HeartRateDiagnostics(
                        processingState = HeartRateProcessingState.PACKET_LOSS,
                        message = "현재 SuperFrame이 CRC 또는 header 오류로 HR 분석에서 제외됨",
                        analysisSegmentId = currentAnalysisSegmentId,
                        windowSampleCount = heartRateCombinedBuffer.size,
                        windowSeconds = heartRateCombinedBuffer.size / 100.0,
                        imuMaxDeltaG = frameImuMaxDeltaG
                    )
                )
            }

        val heartRateEstimate = rawHeartRateAnalysis.estimate

        if (heartRateEstimate != null) {
            lastValidHeartRateEstimateTimestampMillis = phoneTimeMillis
            lastValidHeartRateEstimate = heartRateEstimate
        }

        val heartRateAgeMillis =
            lastValidHeartRateEstimateTimestampMillis?.let { timestampMillis ->
                (phoneTimeMillis - timestampMillis).coerceAtLeast(0L)
            }

        val canHoldPrevious =
            heartRateEstimate == null &&
                    lastValidHeartRateEstimate != null &&
                    heartRateAgeMillis != null &&
                    heartRateAgeMillis <= heartRateDisplayStaleTimeoutMillis

        val displayedHeartRateEstimate =
            heartRateEstimate ?: if (canHoldPrevious) lastValidHeartRateEstimate else null

        val packetCounters = _state.value

        val heartRateDiagnostics =
            if (heartRateEstimate != null) {
                rawHeartRateAnalysis.diagnostics.copy(
                    processingState = HeartRateProcessingState.VALID,
                    underlyingFailureReason = null,
                    calculatedBpm = heartRateEstimate.bpm,
                    displayedBpm = heartRateEstimate.bpm,
                    heartRateFresh = true,
                    heartRateAgeMillis = 0L,
                    crcErrorCount = packetCounters.crcErrorCount,
                    sequenceLossCount = packetCounters.missingSequenceErrors,
                    estimatedLostPacketCount = packetCounters.estimatedLostPacketCount
                )
            } else if (canHoldPrevious) {
                rawHeartRateAnalysis.diagnostics.copy(
                    processingState = HeartRateProcessingState.HELD_PREVIOUS,
                    underlyingFailureReason = rawHeartRateAnalysis.diagnostics.processingState,
                    message = "새 HR 계산 실패(${rawHeartRateAnalysis.diagnostics.processingState.name}); " +
                            "마지막 정상값을 ${heartRateAgeMillis}ms 동안 유지",
                    calculatedBpm = null,
                    displayedBpm = displayedHeartRateEstimate?.bpm,
                    heartRateFresh = false,
                    heartRateAgeMillis = heartRateAgeMillis,
                    crcErrorCount = packetCounters.crcErrorCount,
                    sequenceLossCount = packetCounters.missingSequenceErrors,
                    estimatedLostPacketCount = packetCounters.estimatedLostPacketCount
                )
            } else {
                rawHeartRateAnalysis.diagnostics.copy(
                    displayedBpm = null,
                    heartRateFresh = false,
                    heartRateAgeMillis = heartRateAgeMillis,
                    crcErrorCount = packetCounters.crcErrorCount,
                    sequenceLossCount = packetCounters.missingSequenceErrors,
                    estimatedLostPacketCount = packetCounters.estimatedLostPacketCount
                )
            }

        val estimatedHeartRate = displayedHeartRateEstimate?.bpm

        val heartRateCalculationStatus =
            buildHeartRateCalculationStatus(heartRateDiagnostics)

        Log.d(
            TAG,
            "SuperFrame parsed timestamp=$timestamp, segment=$currentAnalysisSegmentId, " +
                    "analysisValid=$isFrameUsableForAnalysis, irMax=$frameIrMax, " +
                    "bpmCombined=${heartRateEstimate?.bpm}, " +
                    "displayBpm=${displayedHeartRateEstimate?.bpm}, " +
                    "hrState=${heartRateDiagnostics.processingState}, " +
                    "ibiCombined=${heartRateEstimate?.intervalCount}, " +
                    "qCombined=${heartRateEstimate?.qualityScore}, " +
                    "polarity=${heartRateEstimate?.selectedPolarity}, " +
                    "maPerc=${heartRateEstimate?.selectedThresholdPercent}, " +
                    "sdsdMs=${heartRateEstimate?.peakFitSdsdMs}, " +
                    "rawIbiCv=${heartRateEstimate?.rawIbiCv}, " +
                    "ibiAccept=${heartRateEstimate?.acceptedIntervalRatio}, " +
                    "physRatio=${heartRateEstimate?.physiologicalIntervalRatio}, " +
                    "rawIbiQ=${heartRateEstimate?.rawIntervalQualityScore}, " +
                    "spectralConcentration=${heartRateEstimate?.spectralConcentration}, " +
                    "spectralEntropy=${heartRateEstimate?.spectralEntropy}, " +
                    "amplitudeCv=${heartRateEstimate?.amplitudeCoefficientOfVariation}, " +
                    "abruptRatio=${heartRateEstimate?.abruptChangeRatio}, " +
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

        dataLogger?.logHeartRateDiagnostics(
            phoneTimeMillis = phoneTimeMillis,
            timestamp = timestamp,
            diagnostics = heartRateDiagnostics
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

                // 새 결과와 화면 표시값을 분리한다.
                // 새 계산에 실패해도 최대 10초 동안 마지막 정상값을 표시하되,
                // heartRateFresh=false와 age를 통해 stale/held 상태를 명시한다.
                heartRateBpm = estimatedHeartRate,
                heartRateQuality = displayedHeartRateEstimate?.qualityScore,
                heartRateFresh = heartRateEstimate != null,
                heartRateAgeMillis = heartRateDiagnostics.heartRateAgeMillis,
                heartRateDiagnostics = heartRateDiagnostics,
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
     * 새로 들어온 PPG IR/합산 sample을 동일한 위치로 rolling buffer에 누적한다.
     *
     * HR 계산은 IR/RED 평균 신호를 사용하고, 접촉·포화·DC 진단은 IR 원신호를 사용한다.
     * 두 buffer는 항상 같은 sample 수를 유지한다.
     */
    private fun appendPpgSamplesToHrBuffers(
        irSamples: IntArray,
        combinedSamples: IntArray
    ) {
        val sampleCount = minOf(irSamples.size, combinedSamples.size)

        for (i in 0 until sampleCount) {
            heartRateIrBuffer.addLast(irSamples[i])
            heartRateCombinedBuffer.addLast(combinedSamples[i])
            totalHeartRateCombinedSampleCount += 1L

            if (heartRateIrBuffer.size > heartRateBufferMaxSamples) {
                heartRateIrBuffer.removeFirst()
            }

            if (heartRateCombinedBuffer.size > heartRateBufferMaxSamples) {
                heartRateCombinedBuffer.removeFirst()
            }
        }
    }

    private fun buildHeartRateCalculationStatus(
        diagnostics: HeartRateDiagnostics
    ): MetricCalculationStatus {
        val metricState = when (diagnostics.processingState) {
            HeartRateProcessingState.VALID ->
                MetricCalculationState.VALID

            HeartRateProcessingState.COLLECTING ->
                MetricCalculationState.COLLECTING

            HeartRateProcessingState.HELD_PREVIOUS,
            HeartRateProcessingState.NO_CONTACT,
            HeartRateProcessingState.SIGNAL_TOO_WEAK,
            HeartRateProcessingState.SIGNAL_SATURATED,
            HeartRateProcessingState.MOTION_ARTIFACT,
            HeartRateProcessingState.LOW_SPECTRAL_CONCENTRATION,
            HeartRateProcessingState.HIGH_SPECTRAL_ENTROPY,
            HeartRateProcessingState.AMPLITUDE_UNSTABLE,
            HeartRateProcessingState.ABRUPT_SIGNAL_CHANGE,
            HeartRateProcessingState.INSUFFICIENT_PEAKS,
            HeartRateProcessingState.INVALID_IBI,
            HeartRateProcessingState.BPM_OUT_OF_RANGE,
            HeartRateProcessingState.PACKET_LOSS ->
                MetricCalculationState.REJECTED
        }

        return MetricCalculationStatus(
            state = metricState,
            message = diagnostics.message
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

    private fun estimateHeartRate(
        imuMaxDeltaG: Double?
    ): HeartRateAnalysisResult {
        return estimateHeartRateFromBuffer(
            combinedBuffer = heartRateCombinedBuffer,
            irBuffer = heartRateIrBuffer,
            totalSampleCount = totalHeartRateCombinedSampleCount,
            imuMaxDeltaG = imuMaxDeltaG
        )
    }

    /**
     * 최근 최대 8초 PPG window에서 HR과 상세 실패 사유를 함께 계산한다.
     *
     * 양/음 방향 peak를 각각 모든 threshold 후보로 평가하고,
     * SDSD + IBI 유지율이 가장 좋은 방향/threshold 조합을 선택한다.
     */
    private fun estimateHeartRateFromBuffer(
        combinedBuffer: ArrayDeque<Int>,
        irBuffer: ArrayDeque<Int>,
        totalSampleCount: Long,
        imuMaxDeltaG: Double?
    ): HeartRateAnalysisResult {
        val sampleRateHz = 100.0
        val signal = combinedBuffer.map { it.toDouble() }
        val irSignal = irBuffer.map { it.toDouble() }

        val irDcMean = irSignal.takeIf { it.isNotEmpty() }?.average()
        val irMin = irSignal.minOrNull()
        val irMax = irSignal.maxOrNull()
        val maxRawSampleDelta = calculateMaxConsecutiveDifference(signal)

        fun diagnostics(
            state: HeartRateProcessingState,
            message: String,
            acRobustAmplitude: Double? = null,
            amplitudeCoefficientOfVariation: Double? = null,
            spectralConcentration: Double? = null,
            spectralEntropy: Double? = null,
            abruptChangeRatio: Double? = null,
            selectedCandidate: HeartRatePeakFitCandidate? = null,
            qualityScore: Double? = null,
            detectedPeakCount: Int = 0,
            rawIbiCount: Int = 0,
            validIbiCount: Int = 0
        ): HeartRateDiagnostics {
            return HeartRateDiagnostics(
                processingState = state,
                message = message,
                analysisSegmentId = currentAnalysisSegmentId,
                windowSampleCount = signal.size,
                windowSeconds = signal.size / sampleRateHz,
                irDcMean = irDcMean,
                irMin = irMin,
                irMax = irMax,
                acRobustAmplitude = acRobustAmplitude,
                amplitudeCoefficientOfVariation = amplitudeCoefficientOfVariation,
                spectralConcentration = spectralConcentration,
                spectralEntropy = spectralEntropy,
                abruptChangeRatio = abruptChangeRatio,
                selectedPeakThreshold = selectedCandidate?.thresholdOffset,
                selectedThresholdPercent = selectedCandidate?.thresholdPercent,
                selectedPolarity = selectedCandidate?.polarity
                    ?: HeartRatePeakPolarity.NONE,
                detectedPeakCount = selectedCandidate?.peakPositions?.size
                    ?: detectedPeakCount,
                rawIbiCount = selectedCandidate?.rawIntervals?.size
                    ?: rawIbiCount,
                validIbiCount = selectedCandidate?.usedIntervals?.size
                    ?: validIbiCount,
                acceptedIntervalRatio = selectedCandidate?.acceptedIntervalRatio,
                rawSdsdMs = selectedCandidate?.sdsdSec?.times(1000.0),
                rawIbiCv = selectedCandidate?.rawIbiCv,
                physiologicalIntervalRatio = selectedCandidate?.physiologicalIntervalRatio,
                rawIntervalQualityScore = selectedCandidate?.rawIntervalQualityScore,
                sdsdMs = selectedCandidate?.sdsdSec?.times(1000.0),
                qualityScore = qualityScore,
                calculatedBpm = selectedCandidate?.finalBpm?.roundToInt(),
                imuMaxDeltaG = imuMaxDeltaG,
                maxRawSampleDelta = maxRawSampleDelta
            )
        }

        if (signal.isEmpty() || irSignal.isEmpty()) {
            return HeartRateAnalysisResult(
                estimate = null,
                diagnostics = diagnostics(
                    state = HeartRateProcessingState.COLLECTING,
                    message = "PPG 심박 신호 수집 전"
                )
            )
        }

        if (
            irDcMean == null ||
            irDcMean < HEART_RATE_CONTACT_DC_MIN ||
            (irMax ?: 0.0) < HEART_RATE_CONTACT_DC_MIN
        ) {
            return HeartRateAnalysisResult(
                estimate = null,
                diagnostics = diagnostics(
                    state = HeartRateProcessingState.NO_CONTACT,
                    message = "PPG 접촉 신호 없음/약함: IR DC=${irDcMean?.let { "%.1f".format(it) } ?: "-"}"
                )
            )
        }

        val saturatedCount = irSignal.count {
            it >= HEART_RATE_SATURATION_HIGH ||
                    it >= HEART_RATE_PPG_ADC_MAX
        }
        val saturationRatio =
            saturatedCount.toDouble() / irSignal.size.toDouble()

        if (saturationRatio >= 0.02) {
            return HeartRateAnalysisResult(
                estimate = null,
                diagnostics = diagnostics(
                    state = HeartRateProcessingState.SIGNAL_SATURATED,
                    message = "PPG IR 포화: ${(saturationRatio * 100.0).let { "%.1f".format(it) }}%"
                )
            )
        }

        if (signal.size < heartRateMinSamples) {
            return HeartRateAnalysisResult(
                estimate = null,
                diagnostics = diagnostics(
                    state = HeartRateProcessingState.COLLECTING,
                    message = "PPG 심박 신호 수집 중: ${signal.size}/$heartRateMinSamples samples"
                )
            )
        }

        val bandPassed = preprocessPpgForHeartRate(signal)
        val acRobustAmplitude = calculateRobustAmplitude(bandPassed)

        if (
            !acRobustAmplitude.isFinite() ||
            acRobustAmplitude < HEART_RATE_MIN_ROBUST_AC_AMPLITUDE
        ) {
            return HeartRateAnalysisResult(
                estimate = null,
                diagnostics = diagnostics(
                    state = HeartRateProcessingState.SIGNAL_TOO_WEAK,
                    message = "PPG AC 진폭 부족: robust amplitude=${"%.2f".format(acRobustAmplitude)}",
                    acRobustAmplitude = acRobustAmplitude
                )
            )
        }

        val amplitudeCoefficientOfVariation =
            calculateWindowAmplitudeCoefficientOfVariation(
                signal = bandPassed,
                sampleRateHz = sampleRateHz
            )

        val spectralQuality = calculateHeartRateSpectralQuality(
            signal = bandPassed,
            sampleRateHz = sampleRateHz
        )

        val abruptChangeRatio =
            calculateMaxConsecutiveDifference(signal)
                ?.div(acRobustAmplitude)

        fun spectralDiagnostics(
            state: HeartRateProcessingState,
            message: String
        ): HeartRateAnalysisResult {
            return HeartRateAnalysisResult(
                estimate = null,
                diagnostics = diagnostics(
                    state = state,
                    message = message,
                    acRobustAmplitude = acRobustAmplitude,
                    amplitudeCoefficientOfVariation = amplitudeCoefficientOfVariation,
                    spectralConcentration = spectralQuality?.concentration,
                    spectralEntropy = spectralQuality?.entropy,
                    abruptChangeRatio = abruptChangeRatio
                )
            )
        }

        if (
            spectralQuality != null &&
            spectralQuality.concentration < HEART_RATE_MIN_SPECTRAL_CONCENTRATION
        ) {
            return spectralDiagnostics(
                state = HeartRateProcessingState.LOW_SPECTRAL_CONCENTRATION,
                message = "BVP 주파수 집중도 부족: ${"%.4f".format(spectralQuality.concentration)} " +
                        "< $HEART_RATE_MIN_SPECTRAL_CONCENTRATION"
            )
        }

        if (
            spectralQuality != null &&
            spectralQuality.entropy > HEART_RATE_MAX_SPECTRAL_ENTROPY
        ) {
            return spectralDiagnostics(
                state = HeartRateProcessingState.HIGH_SPECTRAL_ENTROPY,
                message = "BVP spectral entropy 과다: ${"%.4f".format(spectralQuality.entropy)} " +
                        "> $HEART_RATE_MAX_SPECTRAL_ENTROPY"
            )
        }

        if (
            amplitudeCoefficientOfVariation != null &&
            amplitudeCoefficientOfVariation > HEART_RATE_MAX_AMPLITUDE_CV
        ) {
            return spectralDiagnostics(
                state = HeartRateProcessingState.AMPLITUDE_UNSTABLE,
                message = "BVP 1초별 진폭 변동 과다: CV=${"%.4f".format(amplitudeCoefficientOfVariation)} " +
                        "> $HEART_RATE_MAX_AMPLITUDE_CV"
            )
        }

        if (
            abruptChangeRatio != null &&
            abruptChangeRatio > HEART_RATE_MAX_ABRUPT_CHANGE_RATIO
        ) {
            return spectralDiagnostics(
                state = HeartRateProcessingState.ABRUPT_SIGNAL_CHANGE,
                message = "BVP 순간 sample 변화 과다: normalized=${"%.4f".format(abruptChangeRatio)} " +
                        "> $HEART_RATE_MAX_ABRUPT_CHANGE_RATIO"
            )
        }

        val positiveTop = DoubleArray(bandPassed.size) { i ->
            bandPassed[i].coerceAtLeast(0.0)
        }
        val negativeTop = DoubleArray(bandPassed.size) { i ->
            (-bandPassed[i]).coerceAtLeast(0.0)
        }

        val bufferStartSampleIndex = totalSampleCount - combinedBuffer.size

        val positiveSearch = findBestHeartRatePeakFit(
            signal = positiveTop,
            sampleRateHz = sampleRateHz,
            bufferStartSampleIndex = bufferStartSampleIndex,
            segmentId = currentAnalysisSegmentId,
            polarity = HeartRatePeakPolarity.POSITIVE
        )

        val negativeSearch = findBestHeartRatePeakFit(
            signal = negativeTop,
            sampleRateHz = sampleRateHz,
            bufferStartSampleIndex = bufferStartSampleIndex,
            segmentId = currentAnalysisSegmentId,
            polarity = HeartRatePeakPolarity.NEGATIVE
        )

        val candidateComparator =
            compareBy<HeartRatePeakFitCandidate> { it.selectionScore }
                .thenByDescending { it.rawIntervalQualityScore }
                .thenByDescending { it.usedIntervals.size }
                .thenByDescending { it.acceptedIntervalRatio }

        val bestFit = listOfNotNull(
            positiveSearch.bestCandidate,
            negativeSearch.bestCandidate
        ).minWithOrNull(candidateComparator)

        val bestRejectedFit = listOfNotNull(
            positiveSearch.bestRejectedCandidate,
            negativeSearch.bestRejectedCandidate
        ).minWithOrNull(candidateComparator)

        val maxDetectedPeakCount = maxOf(
            positiveSearch.maxDetectedPeakCount,
            negativeSearch.maxDetectedPeakCount
        )
        val maxRawIbiCount = maxOf(
            positiveSearch.maxRawIntervalCount,
            negativeSearch.maxRawIntervalCount
        )
        val maxValidIbiCount = maxOf(
            positiveSearch.maxValidIntervalCount,
            negativeSearch.maxValidIntervalCount
        )

        if (
            imuMaxDeltaG != null &&
            imuMaxDeltaG >= HEART_RATE_MOTION_MAX_DELTA_G
        ) {
            return HeartRateAnalysisResult(
                estimate = null,
                diagnostics = diagnostics(
                    state = HeartRateProcessingState.MOTION_ARTIFACT,
                    message = "강한 움직임 감지: IMU maxDelta=${"%.4f".format(imuMaxDeltaG)}g",
                    acRobustAmplitude = acRobustAmplitude,
                    amplitudeCoefficientOfVariation = amplitudeCoefficientOfVariation,
                    spectralConcentration = spectralQuality?.concentration,
                    spectralEntropy = spectralQuality?.entropy,
                    abruptChangeRatio = abruptChangeRatio,
                    selectedCandidate = bestFit ?: bestRejectedFit,
                    detectedPeakCount = maxDetectedPeakCount,
                    rawIbiCount = maxRawIbiCount,
                    validIbiCount = maxValidIbiCount
                )
            )
        }

        if (bestFit == null) {
            val failureState = when {
                // 3초부터 계산을 시도하지만, 6초 이전 실패는 데이터 부족 가능성이 커서
                // 확정 실패가 아니라 COLLECTING으로 남긴다.
                signal.size < heartRateAdaptiveFitPreferredSamples ->
                    HeartRateProcessingState.COLLECTING

                maxDetectedPeakCount < 4 ->
                    HeartRateProcessingState.INSUFFICIENT_PEAKS

                positiveSearch.sawBpmOutOfRange ||
                        negativeSearch.sawBpmOutOfRange ->
                    HeartRateProcessingState.BPM_OUT_OF_RANGE

                positiveSearch.sawInvalidIbi ||
                        negativeSearch.sawInvalidIbi ||
                        maxRawIbiCount > 0 ->
                    HeartRateProcessingState.INVALID_IBI

                else ->
                    HeartRateProcessingState.INSUFFICIENT_PEAKS
            }

            val message = when (failureState) {
                HeartRateProcessingState.COLLECTING ->
                    "adaptive peak fitting용 IBI 추가 수집 중: ${signal.size}/$heartRateAdaptiveFitPreferredSamples samples"

                HeartRateProcessingState.INSUFFICIENT_PEAKS ->
                    "유효 peak 부족: 최대 ${maxDetectedPeakCount}개 검출"

                HeartRateProcessingState.INVALID_IBI -> {
                    val rejected = bestRejectedFit
                    if (rejected != null) {
                        "HR 후보 hard reject: valid=${rejected.usedIntervals.size}, " +
                                "accept=${"%.3f".format(rejected.acceptedIntervalRatio)}, " +
                                "rawSDSD=${"%.1f".format(rejected.sdsdSec * 1000.0)}ms, " +
                                "rawCV=${"%.3f".format(rejected.rawIbiCv)}, " +
                                "phys=${"%.3f".format(rejected.physiologicalIntervalRatio)}"
                    } else {
                        "peak는 검출됐지만 유효 IBI 부족: raw=$maxRawIbiCount, valid=$maxValidIbiCount"
                    }
                }

                HeartRateProcessingState.BPM_OUT_OF_RANGE ->
                    "검출 후보 BPM이 허용 범위 ${HEART_RATE_MIN_BPM}~${HEART_RATE_MAX_BPM} 밖"

                else -> "HR peak fitting 실패"
            }

            return HeartRateAnalysisResult(
                estimate = null,
                diagnostics = diagnostics(
                    state = failureState,
                    message = message,
                    acRobustAmplitude = acRobustAmplitude,
                    amplitudeCoefficientOfVariation = amplitudeCoefficientOfVariation,
                    spectralConcentration = spectralQuality?.concentration,
                    spectralEntropy = spectralQuality?.entropy,
                    abruptChangeRatio = abruptChangeRatio,
                    selectedCandidate = bestRejectedFit,
                    detectedPeakCount = maxDetectedPeakCount,
                    rawIbiCount = maxRawIbiCount,
                    validIbiCount = maxValidIbiCount
                )
            )
        }

        val avgInterval = bestFit.usedIntervals
            .map { it.intervalSec }
            .average()

        if (!avgInterval.isFinite() || avgInterval <= 0.0) {
            return HeartRateAnalysisResult(
                estimate = null,
                diagnostics = diagnostics(
                    state = HeartRateProcessingState.INVALID_IBI,
                    message = "평균 IBI가 유효하지 않음",
                    acRobustAmplitude = acRobustAmplitude,
                    amplitudeCoefficientOfVariation = amplitudeCoefficientOfVariation,
                    spectralConcentration = spectralQuality?.concentration,
                    spectralEntropy = spectralQuality?.entropy,
                    abruptChangeRatio = abruptChangeRatio,
                    selectedCandidate = bestFit
                )
            )
        }

        val bpm = (60.0 / avgInterval).roundToInt()

        if (bpm !in HEART_RATE_MIN_BPM..HEART_RATE_MAX_BPM) {
            return HeartRateAnalysisResult(
                estimate = null,
                diagnostics = diagnostics(
                    state = HeartRateProcessingState.BPM_OUT_OF_RANGE,
                    message = "최종 BPM $bpm 이 허용 범위 밖",
                    acRobustAmplitude = acRobustAmplitude,
                    amplitudeCoefficientOfVariation = amplitudeCoefficientOfVariation,
                    spectralConcentration = spectralQuality?.concentration,
                    spectralEntropy = spectralQuality?.entropy,
                    abruptChangeRatio = abruptChangeRatio,
                    selectedCandidate = bestFit
                )
            )
        }

        val selectedSignal =
            if (bestFit.polarity == HeartRatePeakPolarity.POSITIVE) {
                positiveTop
            } else {
                negativeTop
            }

        val peakAmplitude = selectedSignal.maxOrNull() ?: 0.0

        val baseQuality = calculateHeartRateEstimateQuality(
            intervals = bestFit.usedIntervals,
            peakAmplitude = peakAmplitude
        )

        val spectralConcentrationScore =
            (((spectralQuality?.concentration ?: 0.0) -
                    HEART_RATE_MIN_SPECTRAL_CONCENTRATION) /
                    (0.60 - HEART_RATE_MIN_SPECTRAL_CONCENTRATION))
                .coerceIn(0.0, 1.0)

        val spectralEntropyScore =
            ((HEART_RATE_MAX_SPECTRAL_ENTROPY -
                    (spectralQuality?.entropy ?: HEART_RATE_MAX_SPECTRAL_ENTROPY)) /
                    HEART_RATE_MAX_SPECTRAL_ENTROPY)
                .coerceIn(0.0, 1.0)

        val amplitudeStabilityScore =
            (1.0 -
                    (amplitudeCoefficientOfVariation ?: HEART_RATE_MAX_AMPLITUDE_CV) /
                    HEART_RATE_MAX_AMPLITUDE_CV)
                .coerceIn(0.0, 1.0)

        val abruptChangeScore =
            (1.0 -
                    (abruptChangeRatio ?: HEART_RATE_MAX_ABRUPT_CHANGE_RATIO) /
                    HEART_RATE_MAX_ABRUPT_CHANGE_RATIO)
                .coerceIn(0.0, 1.0)

        val bvpSignalQuality =
            (spectralConcentrationScore * 0.35 +
                    spectralEntropyScore * 0.30 +
                    amplitudeStabilityScore * 0.20 +
                    abruptChangeScore * 0.15)
                .coerceIn(0.0, 1.0)

        // 정제된 IBI만 보는 base quality보다 raw interval quality에 더 큰 비중을 둔다.
        val qualityScore =
            (baseQuality * 0.35 +
                    bestFit.rawIntervalQualityScore * 0.45 +
                    bvpSignalQuality * 0.20)
                .coerceIn(0.0, 1.0)

        val estimate = HeartRateEstimate(
            bpm = bpm,
            ibiIntervals = bestFit.usedIntervals,
            peakCount = bestFit.peakPositions.size,
            intervalCount = bestFit.usedIntervals.size,
            averageIntervalSec = avgInterval,
            qualityScore = qualityScore,
            selectedThresholdPercent = bestFit.thresholdPercent,
            selectedPeakThreshold = bestFit.thresholdOffset,
            selectedPolarity = bestFit.polarity,
            peakFitSdsdMs = bestFit.sdsdSec * 1000.0,
            rawIntervalCount = bestFit.rawIntervals.size,
            acceptedIntervalRatio = bestFit.acceptedIntervalRatio,
            rawIbiCv = bestFit.rawIbiCv,
            physiologicalIntervalRatio = bestFit.physiologicalIntervalRatio,
            rawIntervalQualityScore = bestFit.rawIntervalQualityScore,
            spectralConcentration = spectralQuality?.concentration,
            spectralEntropy = spectralQuality?.entropy,
            amplitudeCoefficientOfVariation = amplitudeCoefficientOfVariation,
            abruptChangeRatio = abruptChangeRatio,
            meanPeakInterpolationOffsetMs = bestFit.meanInterpolationOffsetMs,
            maxPeakInterpolationOffsetMs = bestFit.maxInterpolationOffsetMs
        )

        return HeartRateAnalysisResult(
            estimate = estimate,
            diagnostics = diagnostics(
                state = HeartRateProcessingState.VALID,
                message = "합산 PPG 심박수 정상 검출: $bpm bpm",
                acRobustAmplitude = acRobustAmplitude,
                amplitudeCoefficientOfVariation = amplitudeCoefficientOfVariation,
                spectralConcentration = spectralQuality?.concentration,
                spectralEntropy = spectralQuality?.entropy,
                abruptChangeRatio = abruptChangeRatio,
                selectedCandidate = bestFit,
                qualityScore = qualityScore
            ).copy(
                calculatedBpm = bpm
            )
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

        // 극성 선택은 여기서 max 진폭으로 결정하지 않는다.
        // estimateHeartRateFromBuffer()에서 양/음 방향을 각각 peak fitting한 뒤
        // SDSD와 IBI 유지율이 더 좋은 방향을 선택한다.
        return bandPassed
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
        segmentId: Long,
        polarity: HeartRatePeakPolarity
    ): HeartRatePeakFitSearchResult {
        if (signal.size < 3 || sampleRateHz <= 0.0) {
            return HeartRatePeakFitSearchResult(
                bestCandidate = null,
                bestRejectedCandidate = null,
                maxDetectedPeakCount = 0,
                maxRawIntervalCount = 0,
                maxValidIntervalCount = 0,
                sawInvalidIbi = false,
                sawBpmOutOfRange = false
            )
        }

        val movingAverageWindowSamples =
            (sampleRateHz * HEART_RATE_MOVING_AVERAGE_SECONDS)
                .roundToInt()
                .coerceAtLeast(3)

        val movingAverage = calculateCenteredMovingAverage(
            signal = signal,
            windowSamples = movingAverageWindowSamples
        )

        val movingAverageMean = movingAverage.average()
        if (movingAverageMean <= 0.0) {
            return HeartRatePeakFitSearchResult(
                bestCandidate = null,
                bestRejectedCandidate = null,
                maxDetectedPeakCount = 0,
                maxRawIntervalCount = 0,
                maxValidIntervalCount = 0,
                sawInvalidIbi = false,
                sawBpmOutOfRange = false
            )
        }

        val minPeakDistanceSamples =
            (sampleRateHz * 60.0 / HEART_RATE_MAX_BPM)
                .roundToInt()
                .coerceAtLeast(1)

        val acceptedCandidates = mutableListOf<HeartRatePeakFitCandidate>()
        val rejectedCandidates = mutableListOf<HeartRatePeakFitCandidate>()

        var maxDetectedPeakCount = 0
        var maxRawIntervalCount = 0
        var maxValidIntervalCount = 0
        var sawInvalidIbi = false
        var sawBpmOutOfRange = false

        for (thresholdPercent in HEART_RATE_THRESHOLD_PERCENT_CANDIDATES) {
            val thresholdOffset =
                movingAverageMean * thresholdPercent / 100.0

            val peakIndices = detectHeartRatePeaksByRoi(
                signal = signal,
                movingAverage = movingAverage,
                thresholdOffset = thresholdOffset,
                minPeakDistanceSamples = minPeakDistanceSamples
            )

            maxDetectedPeakCount = maxOf(
                maxDetectedPeakCount,
                peakIndices.size
            )

            // raw SDSD 계산에는 최소 4개 peak = 3개 IBI가 필요하다.
            if (peakIndices.size < 4) continue

            val peakPositions = refineHeartRatePeakPositions(
                signal = signal,
                peakIndices = peakIndices
            )

            if (peakPositions.size < 4) continue

            val interpolationOffsetsMs = peakPositions.indices.map { i ->
                abs(peakPositions[i] - peakIndices[i].toDouble()) /
                        sampleRateHz * 1000.0
            }

            val meanInterpolationOffsetMs = interpolationOffsetsMs.average()
            val maxInterpolationOffsetMs =
                interpolationOffsetsMs.maxOrNull() ?: 0.0

            // 먼저 모든 raw interval을 보존해야 가짜 peak와 누락 peak가
            // SDSD/CV/생리범위 비율에 그대로 불이익으로 반영된다.
            val rawIntervals = buildHeartRateIntervals(
                peakPositions = peakPositions,
                bufferStartSampleIndex = bufferStartSampleIndex,
                sampleRateHz = sampleRateHz,
                segmentId = segmentId,
                enforcePhysiologicalRange = false
            )

            maxRawIntervalCount = maxOf(
                maxRawIntervalCount,
                rawIntervals.size
            )

            if (rawIntervals.size < 3) {
                if (rawIntervals.isNotEmpty()) sawInvalidIbi = true
                continue
            }

            val rawIntervalValues = rawIntervals.map { it.intervalSec }
            val rawAverageInterval = rawIntervalValues.average()

            if (!rawAverageInterval.isFinite() || rawAverageInterval <= 0.0) {
                sawInvalidIbi = true
                continue
            }

            val rawBpm = 60.0 / rawAverageInterval
            if (rawBpm !in HEART_RATE_MIN_BPM.toDouble()..HEART_RATE_MAX_BPM.toDouble()) {
                sawBpmOutOfRange = true
                continue
            }

            val rawSdsdSec = calculateSdsd(rawIntervalValues) ?: continue
            val rawIbiCv = calculateCoefficientOfVariation(rawIntervalValues)
                ?: continue

            val physiologicalIntervals = rawIntervals.filter { interval ->
                interval.intervalSec in
                        (60.0 / HEART_RATE_MAX_BPM)..(60.0 / HEART_RATE_MIN_BPM)
            }

            val physiologicalIntervalRatio =
                physiologicalIntervals.size.toDouble() / rawIntervals.size.toDouble()

            val usedIntervals = filterHeartRateIntervals(
                physiologicalIntervals.toMutableList()
            )

            maxValidIntervalCount = maxOf(
                maxValidIntervalCount,
                usedIntervals.size
            )

            if (usedIntervals.isEmpty()) {
                sawInvalidIbi = true
                continue
            }

            val usedAverageInterval =
                usedIntervals.map { it.intervalSec }.average()

            if (!usedAverageInterval.isFinite() || usedAverageInterval <= 0.0) {
                sawInvalidIbi = true
                continue
            }

            val finalBpm = 60.0 / usedAverageInterval
            if (finalBpm !in HEART_RATE_MIN_BPM.toDouble()..HEART_RATE_MAX_BPM.toDouble()) {
                sawBpmOutOfRange = true
                continue
            }

            val acceptedIntervalRatio =
                usedIntervals.size.toDouble() / rawIntervals.size.toDouble()

            val rawIntervalQualityScore = calculateRawIntervalQualityScore(
                rawSdsdSec = rawSdsdSec,
                rawIbiCv = rawIbiCv,
                acceptedIntervalRatio = acceptedIntervalRatio,
                usedIntervalCount = usedIntervals.size,
                physiologicalIntervalRatio = physiologicalIntervalRatio
            )

            val countPenaltySec =
                HEART_RATE_COUNT_PENALTY_SEC / sqrt(rawIntervals.size.toDouble())

            val rejectionPenaltySec =
                (1.0 - acceptedIntervalRatio) * HEART_RATE_REJECTION_PENALTY_SEC

            // raw interval quality가 낮은 후보는 SDSD만 우연히 좋아도 선택되지 않도록 한다.
            val rawQualityPenaltySec =
                (1.0 - rawIntervalQualityScore) * 0.100

            val selectionScore =
                rawSdsdSec +
                        countPenaltySec +
                        rejectionPenaltySec +
                        rawQualityPenaltySec

            val candidate = HeartRatePeakFitCandidate(
                polarity = polarity,
                thresholdPercent = thresholdPercent,
                thresholdOffset = thresholdOffset,
                peakIndices = peakIndices,
                peakPositions = peakPositions,
                rawIntervals = rawIntervals,
                usedIntervals = usedIntervals,
                rawBpm = rawBpm,
                finalBpm = finalBpm,
                sdsdSec = rawSdsdSec,
                rawIbiCv = rawIbiCv,
                physiologicalIntervalRatio = physiologicalIntervalRatio,
                acceptedIntervalRatio = acceptedIntervalRatio,
                rawIntervalQualityScore = rawIntervalQualityScore,
                selectionScore = selectionScore,
                meanInterpolationOffsetMs = meanInterpolationOffsetMs,
                maxInterpolationOffsetMs = maxInterpolationOffsetMs
            )

            val hardRejected =
                usedIntervals.size < HEART_RATE_MIN_USED_INTERVAL_COUNT ||
                        acceptedIntervalRatio < HEART_RATE_MIN_ACCEPTED_INTERVAL_RATIO ||
                        rawSdsdSec > HEART_RATE_MAX_RAW_SDSD_SEC ||
                        physiologicalIntervalRatio < HEART_RATE_MIN_PHYSIOLOGICAL_INTERVAL_RATIO

            if (hardRejected) {
                sawInvalidIbi = true
                rejectedCandidates += candidate
                continue
            }

            acceptedCandidates += candidate
        }

        val comparator =
            compareBy<HeartRatePeakFitCandidate> { it.selectionScore }
                .thenByDescending { it.rawIntervalQualityScore }
                .thenByDescending { it.usedIntervals.size }
                .thenByDescending { it.acceptedIntervalRatio }

        return HeartRatePeakFitSearchResult(
            bestCandidate = acceptedCandidates.minWithOrNull(comparator),
            bestRejectedCandidate = rejectedCandidates.minWithOrNull(comparator),
            maxDetectedPeakCount = maxDetectedPeakCount,
            maxRawIntervalCount = maxRawIntervalCount,
            maxValidIntervalCount = maxValidIntervalCount,
            sawInvalidIbi = sawInvalidIbi,
            sawBpmOutOfRange = sawBpmOutOfRange
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

    private fun calculateCoefficientOfVariation(
        values: List<Double>
    ): Double? {
        if (values.size < 2) return null

        val mean = values.average()
        if (!mean.isFinite() || mean <= 0.0) return null

        var sumSquaredDeviation = 0.0
        for (value in values) {
            val deviation = value - mean
            sumSquaredDeviation += deviation * deviation
        }

        val std = sqrt(sumSquaredDeviation / values.size.toDouble())
        val cv = std / mean

        return if (cv.isFinite()) cv else null
    }

    /**
     * 이상치 제거 전 raw IBI 품질을 0~1로 정규화한다.
     *
     * 한두 개의 나쁜 IBI를 제거한 뒤 남은 interval만 규칙적인 후보가
     * 높은 quality를 받지 않도록 raw SDSD/CV/유지율/개수/생리범위를 함께 사용한다.
     */
    private fun calculateRawIntervalQualityScore(
        rawSdsdSec: Double,
        rawIbiCv: Double,
        acceptedIntervalRatio: Double,
        usedIntervalCount: Int,
        physiologicalIntervalRatio: Double
    ): Double {
        val sdsdScore =
            (1.0 - rawSdsdSec / HEART_RATE_MAX_RAW_SDSD_SEC)
                .coerceIn(0.0, 1.0)

        val rawCvScore =
            (1.0 - rawIbiCv / HEART_RATE_RAW_IBI_CV_ZERO_SCORE)
                .coerceIn(0.0, 1.0)

        val acceptedRatioScore =
            ((acceptedIntervalRatio - HEART_RATE_MIN_ACCEPTED_INTERVAL_RATIO) /
                    (1.0 - HEART_RATE_MIN_ACCEPTED_INTERVAL_RATIO))
                .coerceIn(0.0, 1.0)

        val intervalCountScore =
            ((usedIntervalCount - HEART_RATE_MIN_USED_INTERVAL_COUNT).toDouble() /
                    (HEART_RATE_PREFERRED_USED_INTERVAL_COUNT -
                            HEART_RATE_MIN_USED_INTERVAL_COUNT).toDouble())
                .coerceIn(0.0, 1.0)

        val physiologicalRatioScore =
            ((physiologicalIntervalRatio - HEART_RATE_MIN_PHYSIOLOGICAL_INTERVAL_RATIO) /
                    (1.0 - HEART_RATE_MIN_PHYSIOLOGICAL_INTERVAL_RATIO))
                .coerceIn(0.0, 1.0)

        return (
                sdsdScore * 0.30 +
                        rawCvScore * 0.20 +
                        acceptedRatioScore * 0.20 +
                        intervalCountScore * 0.15 +
                        physiologicalRatioScore * 0.15
                ).coerceIn(0.0, 1.0)
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

    private fun calculateRobustAmplitude(
        values: DoubleArray
    ): Double {
        if (values.isEmpty()) return 0.0

        val sorted = values.sorted()
        val low = percentileFromSorted(sorted, 0.05)
        val high = percentileFromSorted(sorted, 0.95)

        return (high - low).coerceAtLeast(0.0)
    }

    private fun percentileFromSorted(
        sorted: List<Double>,
        quantile: Double
    ): Double {
        if (sorted.isEmpty()) return 0.0

        val q = quantile.coerceIn(0.0, 1.0)
        val position = q * (sorted.size - 1)
        val lower = position.toInt()
        val upper = (lower + 1).coerceAtMost(sorted.lastIndex)
        val fraction = position - lower

        return sorted[lower] * (1.0 - fraction) +
                sorted[upper] * fraction
    }

    /**
     * 최근 window를 1초 블록으로 나눠 각 블록의 robust AC amplitude를 구한 뒤
     * 그 amplitude들의 CV를 계산한다. 센서 압력/접촉이 초마다 크게 달라지면 커진다.
     */
    private fun calculateWindowAmplitudeCoefficientOfVariation(
        signal: DoubleArray,
        sampleRateHz: Double
    ): Double? {
        if (signal.isEmpty() || sampleRateHz <= 0.0) return null

        val blockSize = sampleRateHz.roundToInt().coerceAtLeast(1)
        if (signal.size < blockSize * 3) return null

        val amplitudes = mutableListOf<Double>()
        var start = 0

        while (start + blockSize <= signal.size) {
            val block = signal.copyOfRange(start, start + blockSize)
            val amplitude = calculateRobustAmplitude(block)

            if (amplitude.isFinite() && amplitude > 0.0) {
                amplitudes += amplitude
            }

            start += blockSize
        }

        return calculateCoefficientOfVariation(amplitudes)
    }

    /**
     * 8초 이하의 band-pass BVP에서 HR 허용 대역의 간단한 periodogram을 계산한다.
     *
     * concentration = 가장 강한 주파수 bin power / 전체 HR 대역 power
     * entropy       = power 분포의 정규화 Shannon entropy
     */
    private fun calculateHeartRateSpectralQuality(
        signal: DoubleArray,
        sampleRateHz: Double
    ): HeartRateSpectralQuality? {
        if (signal.size < 3 || sampleRateHz <= 0.0) return null

        val n = signal.size
        val minFrequencyHz = HEART_RATE_MIN_BPM / 60.0
        val maxFrequencyHz = HEART_RATE_MAX_BPM / 60.0

        val minBin = ceil(minFrequencyHz * n / sampleRateHz)
            .toInt()
            .coerceAtLeast(1)

        val maxBin = floor(maxFrequencyHz * n / sampleRateHz)
            .toInt()
            .coerceAtMost(n / 2)

        if (maxBin < minBin) return null

        val mean = signal.average()
        val powers = mutableListOf<Double>()

        for (bin in minBin..maxBin) {
            var real = 0.0
            var imaginary = 0.0

            for (i in signal.indices) {
                val hamming = if (n == 1) {
                    1.0
                } else {
                    0.54 - 0.46 * cos(2.0 * PI * i / (n - 1).toDouble())
                }

                val sample = (signal[i] - mean) * hamming
                val angle = 2.0 * PI * bin * i / n.toDouble()

                real += sample * cos(angle)
                imaginary -= sample * sin(angle)
            }

            val power = real * real + imaginary * imaginary
            powers += power.coerceAtLeast(0.0)
        }

        val totalPower = powers.sum()
        if (!totalPower.isFinite() || totalPower <= 1e-12) return null

        val concentration =
            (powers.maxOrNull() ?: 0.0) / totalPower

        var entropy = 0.0
        for (power in powers) {
            val probability = power / totalPower
            if (probability > 0.0) {
                entropy -= probability * ln(probability)
            }
        }

        val normalizedEntropy = if (powers.size > 1) {
            entropy / ln(powers.size.toDouble())
        } else {
            0.0
        }

        if (!concentration.isFinite() || !normalizedEntropy.isFinite()) {
            return null
        }

        return HeartRateSpectralQuality(
            concentration = concentration.coerceIn(0.0, 1.0),
            entropy = normalizedEntropy.coerceIn(0.0, 1.0),
            evaluatedBinCount = powers.size
        )
    }

    private fun calculateMaxConsecutiveDifference(
        values: List<Double>
    ): Double? {
        if (values.size < 2) return null

        var maxDelta = 0.0

        for (i in 1 until values.size) {
            val delta = abs(values[i] - values[i - 1])
            if (delta > maxDelta) {
                maxDelta = delta
            }
        }

        return maxDelta
    }

    private fun calculateImuMaxDeltaG(
        imuData: ByteArray
    ): Double? {
        if (imuData.size < 12) return null

        var previousMagnitude: Double? = null
        var maxDelta = 0.0
        var validSampleCount = 0

        for (i in imuData.indices step 6) {
            if (i + 5 >= imuData.size) break

            val xRaw = readInt16LittleEndian(imuData, i)
            val yRaw = readInt16LittleEndian(imuData, i + 2)
            val zRaw = readInt16LittleEndian(imuData, i + 4)

            val xG = xRaw / HEART_RATE_IMU_LSB_PER_G
            val yG = yRaw / HEART_RATE_IMU_LSB_PER_G
            val zG = zRaw / HEART_RATE_IMU_LSB_PER_G

            val magnitude = sqrt(
                xG * xG +
                        yG * yG +
                        zG * zG
            )

            val previous = previousMagnitude
            if (previous != null) {
                maxDelta = maxOf(
                    maxDelta,
                    abs(magnitude - previous)
                )
            }

            previousMagnitude = magnitude
            validSampleCount += 1
        }

        return if (validSampleCount >= 2) maxDelta else null
    }

    private fun readInt16LittleEndian(
        data: ByteArray,
        offset: Int
    ): Int {
        if (offset < 0 || offset + 1 >= data.size) return 0

        val value =
            (data[offset].toInt() and 0xFF) or
                    ((data[offset + 1].toInt() and 0xFF) shl 8)

        return if ((value and 0x8000) != 0) {
            value - 0x10000
        } else {
            value
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
        heartRateIrBuffer.clear()
        lastValidHeartRateInputTimestampMillis = null

        // 마지막 정상 HR과 timestamp는 화면 표시용으로만 최대 10초 유지한다.
        // raw/IBI buffer는 즉시 초기화되므로 새 segment의 HR/HRV 계산에는 섞이지 않는다.

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

        val nowMillis = System.currentTimeMillis()
        val ageMillis =
            lastValidHeartRateEstimateTimestampMillis?.let {
                (nowMillis - it).coerceAtLeast(0L)
            }
        val canHoldPrevious =
            lastValidHeartRateEstimate != null &&
                    ageMillis != null &&
                    ageMillis <= heartRateDisplayStaleTimeoutMillis

        val packetLossDiagnostics = HeartRateDiagnostics(
            processingState =
                if (canHoldPrevious) {
                    HeartRateProcessingState.HELD_PREVIOUS
                } else {
                    HeartRateProcessingState.PACKET_LOSS
                },
            underlyingFailureReason =
                if (canHoldPrevious) {
                    HeartRateProcessingState.PACKET_LOSS
                } else {
                    null
                },
            message =
                if (canHoldPrevious) {
                    "데이터 연속성 중단($reason); 마지막 정상 HR을 ${ageMillis}ms 동안 유지"
                } else {
                    "데이터 연속성 중단: $reason"
                },
            analysisSegmentId = currentAnalysisSegmentId,
            displayedBpm =
                if (canHoldPrevious) lastValidHeartRateEstimate?.bpm else null,
            heartRateFresh = false,
            heartRateAgeMillis = ageMillis,
            crcErrorCount = _state.value.crcErrorCount,
            sequenceLossCount = _state.value.missingSequenceErrors,
            estimatedLostPacketCount = _state.value.estimatedLostPacketCount
        )

        _state.update { current ->
            current.copy(
                heartRateBpm =
                    if (canHoldPrevious) lastValidHeartRateEstimate?.bpm else null,
                heartRateQuality =
                    if (canHoldPrevious) lastValidHeartRateEstimate?.qualityScore else null,
                heartRateFresh = false,
                heartRateAgeMillis = ageMillis,
                heartRateDiagnostics = packetLossDiagnostics,
                heartRateCalculationStatus = MetricCalculationStatus(
                    state = MetricCalculationState.REJECTED,
                    message = packetLossDiagnostics.message
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

        val missEventTimeMillis = System.currentTimeMillis()

        dataLogger?.logSuperFrame(
            phoneTimeMillis = missEventTimeMillis,
            timestamp = null,
            superFrame = partialFrame,
            complete = "miss",
            missPacketNum = currentFrameMissPacketNums
                .distinct()
                .sorted()
                .joinToString("|"),
            errorLog = currentFrameErrors.joinToString(" / ")
        )

        dataLogger?.logHeartRateDiagnostics(
            phoneTimeMillis = missEventTimeMillis,
            timestamp = null,
            diagnostics = _state.value.heartRateDiagnostics
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