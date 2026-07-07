package com.leejang.sleeptandard.Potch

import android.util.Log
import com.leejang.sleeptandard.Potch.PowerBin
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// 계산 결과 상태
data class ArousalState(
    // 1. Micro Movement
    val microMovementVariance: Double? = null,
    val microMovementScore: Double? = null,

    // 2. Respiratory Rate
    val rrFromPpg: Double? = null,
    val rrFromImu: Double? = null,
    val rrFinal: Double? = null,
    val rrScore: Double? = null,    // confidence 곱한 값
    val rrRawScore: Double? = null, // confidence 곱하기 전
    val rrFusionSource: RrFusionSource = RrFusionSource.NONE,
    val rrFusionConfidence: Double = 0.0,
    val rrFusionLog: String? = null,

    // 3. Respiratory Rate Variability
    val rrvRmssd: Double? = null,        // seconds 기준 RMSSD
    val rrvRmssdMs: Double? = null,      // 로그/UI 확인용 ms
    val rrvScore: Double? = null,
    val rrvSource: RrvSource = RrvSource.NONE,
    val rrvQuality: Double = 0.0,

    // 4. Heart Rate
    val hrBpm: Int? = null,
    val hrGradient: Double? = null,
    val hrScore: Double? = null,

    // 5. Heart Rate Variability
    val hrvRmssd: Double? = null,
    val hrvRmssdMs: Double? = null,
    val hrvLf: Double? = null,
    val hrvHf: Double? = null,
    val hrvLfHf: Double? = null,
    val hrvScore: Double? = null,
    val hrvQuality: Double = 0.0,
    val hrvLog: String? = null,

    // 6. Skin Temperature
    val skinTemperatureCelsius: Double? = null,
    val skinTemperatureGradient: Double? = null,
    val skinTemperatureScore: Double? = null,

    // Final
    val finalWakeScore: Double = 0.0,
    val isWakeTimingCandidate: Boolean = false,
    val lastLog: String = "No arousal data yet"
)

// 임계치 조절 클래스
data class ArousalConfig(
    val sampleRateHz: Double = 100.0,

    // Micro Movement
    val microLowCutHz: Double = 0.5,
    val microHighCutHz: Double = 5.0,
    val microWindowSeconds: Int = 5,
    val microMinWindowSeconds: Int = 3,

    // 초기값. 실제 Potch 로그 보고 조정해야 함.
    val microRmsWeakThresholdG: Double = 0.003,
    val microRmsDetectedThresholdG: Double = 0.010,
    val macroMovementThresholdG: Double = 0.030,
    val microVarianceThreshold: Double = 0.0001,

    // Respiration
    val respLowCutHz: Double = 0.1,
    val respHighCutHz: Double = 0.5,
    val rrMinBpm: Double = 6.0,
    val rrMaxBpm: Double = 30.0,

    // PPG 기반 RR 계산용
    val ppgRespWindowSeconds: Int = 45,
    val ppgRespMinWindowSeconds: Int = 25,

    // BPF 후 peak-to-peak amplitude가 너무 작으면 호흡 파형이 약하다고 판단
    // 실제 로그 보고 조정 필요
    val ppgRespMinPeakToPeakAmplitude: Double = 30.0,

    // interval 튄 값 제거 기준
    val ppgRespIntervalOutlierTolerance: Double = 0.40,

    // IMU 기반 RR 계산용
    val imuRespWindowSeconds: Int = 45,
    val imuRespMinWindowSeconds: Int = 25,

    // BPF 후 IMU 호흡 파형의 최소 peak-to-peak 진폭.
    // 단위는 g.
    // 실제 착용 로그 보고 조정 필요.
    val imuRespMinPeakToPeakAmplitudeG: Double = 0.002,

    // interval 튄 값 제거 기준
    val imuRespIntervalOutlierTolerance: Double = 0.40,

    // RR Fusion
    val rrFusionAgreeDiffBpm: Double = 3.0,
    val rrFusionStrongDisagreeDiffBpm: Double = 6.0,

    // 기본적으로 IMU를 더 신뢰
    val rrFusionImuBaseWeight: Double = 0.7,
    val rrFusionPpgBaseWeight: Double = 0.3,

    // quality가 이 값보다 낮으면 신뢰도 낮은 RR로 판단
    val rrFusionMinUsableQuality: Double = 0.35,

    // RR Score
    // 수면 중 호흡수가 이 값 이하이면 RR 자체만으로는 각성 신호로 거의 보지 않음
    val rrScoreLowBpm: Double = 16.0,

    // 이 값 이상이면 RR 절대값만으로는 높은 각성 신호로 봄
    val rrScoreHighBpm: Double = 24.0,

    // 최근 RR이 baseline보다 이 정도 이상 증가하면 높은 각성 신호로 봄
    val rrRiseThresholdBpm: Double = 3.0,

    // 최근 RR 변화량 계산용 window
    val rrScoreWindowMillis: Long = 3 * 60 * 1000L,
    val rrScoreMinWindowMillis: Long = 60 * 1000L,
    val rrScoreMinSampleCount: Int = 5,

    // RR buffer에 저장할 최소 신뢰도
    val rrScoreMinUsableConfidence: Double = 0.35,

    // RR 튐 제거 기준
    val rrScoreOutlierToleranceBpm: Double = 5.0,

    // raw RR score 조합 비율
    val rrAbsoluteScoreWeight: Double = 0.4,
    val rrRiseScoreWeight: Double = 0.6,

    // RR history 보관 시간
    val rrHistoryWindowMillis: Long = 10 * 60 * 1000L,

    // RRV
    val rrvMinIntervalCount: Int = 3,
    val rrvIntervalOutlierTolerance: Double = 0.40,

    // 임시 score 기준.
    // 나중에는 N3/deep sleep baseline 대비 변화량으로 바꾸는 게 좋음.
    val rrvRmssdScoreThresholdSec: Double = 0.8,

    // 이 quality보다 낮은 호흡 interval은 RRV 계산에 쓰지 않음
    val rrvMinUsableQuality: Double = 0.35,

    // HR
    val hrGradientThreshold: Double = 3.0,

    // HR 각성지표 계산용
    val hrGradientWindowMillis: Long = 3 * 60 * 1000L,   // 최근 3분
    val hrGradientMinWindowMillis: Long = 60 * 1000L,    // 최소 1분 이상 쌓여야 계산
    val hrMinSampleCount: Int = 5,

    // 비정상 튐 제거용
    val hrMaxReasonableBpm: Int = 220,
    val hrMinReasonableBpm: Int = 30,
    val hrOutlierToleranceBpm: Double = 25.0,

    // HRV
    val hrvWindowSeconds: Int = 60,
    val hrvMinIbiCount: Int = 8,
    val hrvIbiOutlierTolerance: Double = 0.30,
    val hrvMinEstimateQuality: Double = 0.35,

    // 임시 score 기준. 나중에는 개인 baseline 기반으로 바꾸는 게 좋음.
    val hrvRmssdScoreThresholdMs: Double = 80.0,

    // HRV LF/HF
    /*
    hrvFrequencyWindowSeconds를 120초로 둔 이유는 LF 대역이 0.04Hz부터 시작해서 너무 짧은 창에서는 안정적으로 보기 어렵기 때문이야. 개발 초기에는 60초도 가능하지만, 가능하면 120초 이상이 낫다.
     */
    val hrvFrequencyWindowSeconds: Int = 120,

    // HRV spectral analysis는 최소 IBI가 어느 정도 있어야 의미 있음
    val hrvSpectralMinIbiCount: Int = 20,

    // IBI를 등간격 시계열로 바꿀 때 사용할 resampling rate
    // HRV에서는 보통 4Hz 정도를 많이 사용
    val hrvResampleRateHz: Double = 4.0,

    // LF/HF frequency band
    val hrvLfLowHz: Double = 0.04,
    val hrvLfHighHz: Double = 0.15,
    val hrvHfLowHz: Double = 0.15,
    val hrvHfHighHz: Double = 0.40,

    // LF/HF ratio가 이 값 이상이면 HRV 관점에서 각성 점수 높게 봄
    // 실제 개인 baseline 생기면 baseline 대비로 바꿀 것
    val hrvLfHfScoreThreshold: Double = 2.0,

    // Skin Temperature
    val skinTempGradientWindowMillis: Long = 5 * 60 * 1000L,   // 최근 5분
    val skinTempGradientMinWindowMillis: Long = 2 * 60 * 1000L, // 최소 2분 이상 필요
    val skinTempMinSampleCount: Int = 5,

    // 최근 window에서 이 정도 이상 올라가면 의미 있는 상승으로 본다.
    // 실제 Potch 착용 로그를 보고 조정 필요.
    val skinTempRiseThresholdCelsius: Double = 0.15,

    // 갑자기 튄 온도 제거 기준
    val skinTempOutlierToleranceCelsius: Double = 1.5,

    // 센서 접촉/환경 변화로 보기 어려운 1회성 급변 제거용
    val skinTempMaxSingleJumpCelsius: Double = 1.0,

    // Final score
    val finalWakeThreshold: Double = 0.65,
    // 기본 각성 점수는 최대 0.8이 되도록 설계.
    // micro + rr + rrv + hr + hrv weight 합 = 0.8
    val mmScoreWeight: Double = 0.20,
    val rrScoreWeight: Double = 0.15,
    val rrvScoreWeight: Double = 0.15,
    val hrScoreWeight: Double = 0.20,
    val hrvScoreWeight: Double = 0.10,
    // skin temperature는 더하는 지표가 아니라 multiplier로 사용.
    // multiplier = 1.0 ~ 1.25
    val tempScoreWeight: Double = 0.25,

    // PPG/IMU는 100Hz 기준으로 최근 60초 보관
    val ppgWindowSeconds: Int = 60,
    val imuWindowSeconds: Int = 60,

    // 체온/심박은 1초마다 1개 정도 들어온다고 보고 최근 10분 보관
    val temperatureWindowMillis: Long = 10 * 60 * 1000L,
    val heartRateWindowMillis: Long = 10 * 60 * 1000L,

    // BMA400 ±2g, 12-bit 기준이면 보통 1024 LSB/g 근처.
    // 실제 펌웨어 설정이 다르면 이 값은 나중에 수정해야 함.
    val imuLsbPerG: Double = 1024.0,

)

/**
 * Micro Movement 계산 결과를 사람이 이해하기 쉬운 단계로 분류한다.
 *
 * STABLE: 거의 움직임 없음
 * WEAK: 약한 미세 움직임 후보
 * MICRO_MOVEMENT: 각성 후보로 볼 수 있는 미세 움직임
 * MACRO_MOVEMENT: 자세 변경/큰 움직임에 가까운 상태
 */
enum class MicroMovementLevel {
    STABLE,
    WEAK,
    MICRO_MOVEMENT,
    MACRO_MOVEMENT
}

/**
 * IMU 기반 micro movement 계산 결과.
 *
 * BPF를 통과한 IMU g-magnitude 신호에서 RMS, variance, score를 계산하고
 * 이 값들을 바탕으로 현재 움직임 단계를 판정한다.
 */
data class MicroMovementResult(
    val sampleCount: Int,
    val windowSeconds: Double,

    // BPF 통과 후 미세떨림 파형의 RMS
    val rmsG: Double,

    // BPF 통과 후 미세떨림 파형의 분산
    val varianceG: Double,

    // detected threshold 대비 몇 배인지
    val score: Double,

    val level: MicroMovementLevel,
    val isMicroMovementDetected: Boolean,
    val isMacroMovementLike: Boolean
)

/**
 * PPG 기반 호흡 추정에 사용한 광학 채널.
 *
 * 기본은 IR을 우선 사용하고, IR에서 호흡 파형 검출이 실패하면 RED를 백업으로 사용한다.
 */
enum class PpgRespirationChannel {
    IR,
    RED
}

/**
 * PPG에서 추출한 호흡수 계산 결과.
 *
 * 원본 PPG에서 호흡 대역(0.1~0.5Hz)을 남긴 뒤 peak 간격을 계산하여
 * RR bpm, interval 목록, 품질 점수를 함께 보관한다.
 */
data class PpgRespirationResult(
    val channel: PpgRespirationChannel,
    val rrBpm: Double,
    val peakCount: Int,
    val intervalCount: Int,
    val averageIntervalSec: Double,
    val peakToPeakAmplitude: Double,
    val qualityScore: Double,
    val intervalsSec: List<Double>
)

/**
 * IMU g-magnitude에서 추출한 호흡수 계산 결과.
 */
data class ImuRespirationResult(
    val rrBpm: Double,
    val peakCount: Int,
    val intervalCount: Int,
    val averageIntervalSec: Double,
    val peakToPeakAmplitudeG: Double,
    val qualityScore: Double,
    val inverted: Boolean,
    val intervalsSec: List<Double>
)
/**
 * 최종 RR이 어떤 센서 조합으로 결정되었는지 나타낸다.
 *
 * PPG와 IMU가 모두 유효하면 weighted fusion을 사용하고,
 * 한쪽만 유효하거나 서로 강하게 불일치하면 source로 그 판단 근거를 남긴다.
 */
enum class RrFusionSource {
    BOTH_WEIGHTED,
    IMU_ONLY,
    PPG_ONLY,
    IMU_PREFERRED_DISAGREE,
    PPG_PREFERRED_DISAGREE,
    NONE
}

/**
 * PPG RR과 IMU RR을 합성한 최종 호흡수 결과.
 *
 * 최종 RR bpm뿐 아니라 각 센서의 원래 RR, 품질, 차이, confidence, 로그를 남겨
 * 추후 디버깅과 threshold 튜닝에 사용할 수 있게 한다.
 */
data class RrFusionResult(
    val rrBpm: Double?,
    val source: RrFusionSource,
    val ppgRrBpm: Double?,
    val imuRrBpm: Double?,
    val ppgQuality: Double?,
    val imuQuality: Double?,
    val diffBpm: Double?,
    val confidence: Double,
    val log: String
)

data class RespiratoryRateArousalResult(
    val currentRrBpm: Double,
    val baselineRrBpm: Double?,
    val recentRrBpm: Double?,
    val riseBpm: Double?,
    val absoluteScore: Double,
    val riseScore: Double?,
    val rawScore: Double,
    val score: Double,
    val confidence: Double,
    val sampleCount: Int,
    val windowSeconds: Double?,
    val log: String
)

/**
 * RRV 계산에 사용된 호흡 interval의 출처.
 *
 * RR fusion 결과를 기준으로 IMU 또는 PPG interval 중 더 신뢰할 수 있는 쪽을 선택한다.
 */
enum class RrvSource {
    IMU,
    PPG,
    NONE
}

/**
 * 호흡 interval variability 계산 결과.
 */
data class RrvResult(
    val rmssdSec: Double,
    val rmssdMs: Double,
    val source: RrvSource,
    val intervalCount: Int,
    val meanIntervalSec: Double,
    val score: Double,
    val qualityScore: Double,
    val log: String
)

/**
 * HR 증가율 기반 각성 지표 결과.
 *
 * 최근 window의 초반 평균 심박과 후반 평균 심박을 비교해
 * 심박이 상승하고 있는지, 각성 후보로 볼 수 있는지 계산한다.
 */
data class HeartRateArousalResult(
    val currentBpm: Int,
    val baselineBpm: Double,
    val recentBpm: Double,

    // 최근 window에서 증가한 bpm
    val gradientBpm: Double,

    // 분당 증가량
    val gradientBpmPerMinute: Double,

    val sampleCount: Int,
    val windowSeconds: Double,
    val score: Double,
    val isIncreasing: Boolean,
    val log: String
)
/**
 * 시간 영역 HRV 결과.
 *
 * PPG peak interval에서 얻은 IBI 리스트를 이용해 RMSSD를 계산한다.
 * LF/HF 같은 주파수 영역 지표와 별도로 빠르게 계산 가능한 HRV 지표다.
 */
data class HeartRateVariabilityResult(
    val rmssdSec: Double,
    val rmssdMs: Double,
    val ibiCount: Int,
    val meanIbiSec: Double,
    val score: Double,
    val qualityScore: Double,
    val log: String
)
/**
 * 주파수 영역 HRV 결과.
 *
 * IBI를 등간격 시계열로 resampling한 뒤 FFT power spectrum에서
 * LF, HF power와 LF/HF ratio를 계산한 결과를 보관한다.
 */
data class HrvFrequencyResult(
    val lfPower: Double,
    val hfPower: Double,
    val lfHfRatio: Double,
    val resampledCount: Int,
    val ibiCount: Int,
    val score: Double,
    val qualityScore: Double,
    val log: String
)
/**
 * 최근 skin temperature 변화 방향.
 *
 * WARMING은 체온 상승, COOLING은 하강, STABLE은 의미 있는 변화가 없는 상태다.
 */
enum class SkinTemperatureTrend {
    WARMING,
    COOLING,
    STABLE
}
/**
 * 피부온도 기반 각성 보조 지표 결과.
 *
 * 최근 window의 초반 평균 온도와 후반 평균 온도를 비교해
 * 상승/하강/안정 추세와 score, 품질 점수를 계산한다.
 */
data class SkinTemperatureResult(
    val currentCelsius: Double,
    val baselineCelsius: Double,
    val recentCelsius: Double,

    // 최근 window에서 변화한 온도
    val gradientCelsius: Double,

    // 분당 변화량
    val gradientCelsiusPerMinute: Double,

    val sampleCount: Int,
    val windowSeconds: Double,
    val trend: SkinTemperatureTrend,
    val score: Double,
    val qualityScore: Double,
    val log: String
)
/**
 * 디버깅용 buffer 상태 스냅샷.
 *
 * 각 rolling buffer에 현재 데이터가 얼마나 쌓였는지 UI나 로그에서 확인하기 위해 사용한다.
 */
data class ArousalBufferSnapshot(
    val ppgIrSampleCount: Int,
    val imuGSampleCount: Int,
    val temperatureSampleCount: Int,
    val heartRateSampleCount: Int,
    val latestTemperatureCelsius: Double?,
    val latestHeartRateBpm: Int?
)

/**
 * Potch 센서 데이터에서 각성 관련 지표를 종합 계산하는 클래스.
 *
 * SuperFrame 단위로 들어오는 PPG, IMU, NTC, HR estimate를 rolling buffer에 누적하고
 * Micro Movement, RR, RRV, HR, HRV, Skin Temperature, final wake score를 계산한다.
 */
class PotchArousalCalculator(
    private val config: ArousalConfig = ArousalConfig()
) {
    /**
     * PPG IR raw sample rolling buffer.
     *
     * 용도:
     * - PPG 기반 RR 계산
     * - HR 계산 보조
     * - HRV LF/HF 계산용 IBI 생성의 기반 데이터
     */
    private val ppgIrBuffer = ArrayDeque<Double>()
    private val ppgRedBuffer = ArrayDeque<Double>()

    /**
     * RR 각성 점수 계산용 rolling buffer.
     *
     * Pair<timestampMillis, rrBpm>
     *
     * rrFinal이 계산되고 confidence가 충분한 경우에만 저장한다.
     */
    private val respirationRateBuffer = ArrayDeque<Pair<Long, Double>>()

    /**
     * IMU G magnitude rolling buffer.
     *
     * 용도:
     * - Micro Movement variance 계산
     * - IMU 기반 흉부 호흡 peak 계산
     */
    private val imuGBuffer = ArrayDeque<Double>()
    /**
     * NTC 기반 피부온도 rolling buffer.
     *
     * Pair<timestampMillis, celsius>
     *
     * 용도:
     * - 직전 n분간 체온 변화량 계산
     *
     * 지금 temperatureBuffer는 timestampMillis = sensorData.timestamp를 기준으로 window를 자르고 있어. 만약 펌웨어 timestamp가 밀리초가 아니라 초 단위거나 tick 단위라면 5 * 60 * 1000L 같은 window 계산이 틀어져.
     */
    private val temperatureBuffer = ArrayDeque<Pair<Long, Double>>()
    /**
     * Heart Rate rolling buffer.
     *
     * Pair<timestampMillis, bpm>
     *
     * 용도:
     * - HR 증가율 계산
     */
    private val heartRateBuffer = ArrayDeque<Pair<Long, Int>>()

    /**
     * HRV 계산용 IBI rolling buffer.
     *
     * DataProcessor에서 PPG peak 간격을 계산해서 넘겨주면,
     * 여기서는 중복되지 않은 IBI만 저장한다.
     */
    private val hrvIbiBuffer = ArrayDeque<IbiInterval>()

    private var lastAcceptedHrvIbiEndSampleIndex: Long = Long.MIN_VALUE

    private val maxPpgSamples =
        (config.sampleRateHz * config.ppgWindowSeconds).toInt()

    private val maxImuSamples =
        (config.sampleRateHz * config.imuWindowSeconds).toInt()

    private var lastState = ArousalState()

    /**
     * 새 SuperFrame 하나를 받아 모든 각성 지표를 갱신한다.
     *
     * 1초분 센서 데이터를 rolling buffer에 append한 뒤,
     * micro movement, RR, RRV, HR, HRV, skin temperature를 순서대로 계산하고
     * 마지막으로 final wake score를 만들어 ArousalState로 반환한다.
     */
    fun process(
        sensorData: SensorData,
        heartRateEstimate: HeartRateEstimate?
    ): ArousalState {
        appendPpg(sensorData.ppgData)
        appendImu(sensorData.imuData)
        appendTemperature(sensorData.timestamp, sensorData.ntcCelsius)

        if (heartRateEstimate != null) {
            appendHeartRate(
                timestampMillis = sensorData.timestamp,
                bpm = heartRateEstimate.bpm
            )

            appendHeartRateEstimateToHrvBuffer(heartRateEstimate)
        }

        val microMovement = calculateMicroMovement()
        val microVariance = microMovement?.varianceG

        val ppgRespiration = calculatePpgRespiration()
        val imuRespiration = calculateImuRespiration()

        val rrFusion = fuseRespiration(
            ppg = ppgRespiration,
            imu = imuRespiration
        )
        val rrFromPpg = ppgRespiration?.rrBpm
        val rrFromImu = imuRespiration?.rrBpm
        val rrFinal = rrFusion.rrBpm

        if (rrFinal != null) {
            appendRespirationRate(
                timestampMillis = sensorData.timestamp,
                rrBpm = rrFinal,
                confidence = rrFusion.confidence
            )
        }

        val rrArousalResult = calculateRespiratoryRateArousal(rrFusion)

        val rrvResult = calculateRrvRmssd(
            ppg = ppgRespiration,
            imu = imuRespiration,
            rrFusion = rrFusion
        )
        val rrvRmssd = rrvResult?.rmssdSec

        val hrResult = calculateHeartRateArousal()
        val hrGradient = hrResult?.gradientBpm

        val skinTempResult = calculateSkinTemperatureArousal()
        val tempGradient = skinTempResult?.gradientCelsius

        val hrvResult = calculateHeartRateVariability()
        val hrvFrequencyResult = calculateHrvFrequencyDomain()

        val hrvLfHf = hrvFrequencyResult?.lfHfRatio

        val finalScore = calculateFinalWakeScore(
            microScore = microMovement?.score?.coerceIn(0.0, 1.0),
            rrScore = rrArousalResult?.score,
            rrvScore = rrvResult?.score,
            hrScore = hrResult?.score,
            hrvScore = hrvFrequencyResult?.score ?: hrvResult?.score,
            tempScore = skinTempResult?.score
        )

        lastState = ArousalState(
            microMovementVariance = microMovement?.varianceG,
            microMovementScore = microMovement?.score,

            rrFromPpg = rrFromPpg,
            rrFromImu = rrFromImu,
            rrFinal = rrFinal,
            rrScore = rrArousalResult?.score,
            rrRawScore = rrArousalResult?.rawScore,
            rrFusionSource = rrFusion.source,
            rrFusionConfidence = rrFusion.confidence,
            rrFusionLog = rrFusion.log,

            rrvRmssd = rrvResult?.rmssdSec,
            rrvRmssdMs = rrvResult?.rmssdMs,
            rrvScore = rrvResult?.score,
            rrvSource = rrvResult?.source ?: RrvSource.NONE,
            rrvQuality = rrvResult?.qualityScore ?: 0.0,

            hrBpm = hrResult?.currentBpm ?: heartRateEstimate?.bpm ?: lastState.hrBpm,
            hrGradient = hrGradient,
            hrScore = hrResult?.score,

            hrvRmssd = hrvResult?.rmssdSec,
            hrvRmssdMs = hrvResult?.rmssdMs,
            hrvLf = hrvFrequencyResult?.lfPower,
            hrvHf = hrvFrequencyResult?.hfPower,
            hrvLfHf = hrvFrequencyResult?.lfHfRatio,
            hrvScore = hrvFrequencyResult?.score ?: hrvResult?.score,
            hrvQuality = hrvFrequencyResult?.qualityScore ?: hrvResult?.qualityScore ?: 0.0,
            hrvLog = hrvFrequencyResult?.log ?: hrvResult?.log,

            skinTemperatureCelsius = skinTempResult?.currentCelsius ?: sensorData.ntcCelsius,
            skinTemperatureGradient = tempGradient,
            skinTemperatureScore = skinTempResult?.score,

            finalWakeScore = finalScore,
            isWakeTimingCandidate = finalScore >= config.finalWakeThreshold,
            lastLog = "Arousal score=$finalScore, " +
                    "micro=${microMovement?.level}, " +
                    "rr=${rrFusion.log}, rrScore=${rrArousalResult?.log}, " +
                    "rrv=${rrvResult?.log}, " +
                    "hr=${hrResult?.log}, " +
                    "hrv=${hrvFrequencyResult?.log ?: hrvResult?.log}"+
                    "skin=${skinTempResult?.log}"
        )

        return lastState
    }
    /**
     * 최종 기상 후보 점수를 계산한다.
     *
     * 설계 의도:
     *
     * 1. micro, RR, RRV, HR, HRV는 기본 각성 점수로 더한다.
     *    이 기본 점수는 최대 0.8까지만 허용한다.
     *
     * 2. skin temperature는 단독으로 점수를 더하지 않고 multiplier로 사용한다.
     *    체온 상승은 다른 각성 신호를 보조적으로 강화하는 역할이다.
     *
     * 3. 최종 점수는 항상 0.0 ~ 1.0 범위로 제한한다.
     *
     * 공식:
     *
     * baseScore =
     *     microScore * w1 +
     *     rrScore    * w2 +
     *     rrvScore   * w3 +
     *     hrScore    * w4 +
     *     hrvScore   * w5
     *
     * skinMultiplier =
     *     1.0 + tempScore * tempScoreWeight
     *
     * finalWakeScore =
     *     baseScore * skinMultiplier
     */
    private fun calculateFinalWakeScore(
        microScore: Double?,
        rrScore: Double?,
        rrvScore: Double?,
        hrScore: Double?,
        hrvScore: Double?,
        tempScore: Double?
    ): Double {
        val micro = normalizeWakeScore(microScore)
        val rr = normalizeWakeScore(rrScore)
        val rrv = normalizeWakeScore(rrvScore)
        val hr = normalizeWakeScore(hrScore)
        val hrv = normalizeWakeScore(hrvScore)
        val temp = normalizeWakeScore(tempScore)

        val baseWeightSum =
            config.mmScoreWeight +
                    config.rrScoreWeight +
                    config.rrvScoreWeight +
                    config.hrScoreWeight +
                    config.hrvScoreWeight

        if (abs(baseWeightSum - 0.8) > 0.000001) {
            Log.w(
                "PotchArousalCalculator",
                "Base score weight sum should be 0.8, but was $baseWeightSum"
            )
        }

        val baseScoreRaw =
            micro * config.mmScoreWeight +
                    rr * config.rrScoreWeight +
                    rrv * config.rrvScoreWeight +
                    hr * config.hrScoreWeight +
                    hrv * config.hrvScoreWeight

        // 네 설계대로 기본 생체/움직임 점수는 최대 0.8까지만 허용.
        val baseScore = baseScoreRaw.coerceIn(0.0, 0.8)

        // temp = 0이면 multiplier 1.0
        // temp = 1이면 multiplier 1.25
        val skinTemperatureMultiplier =
            (1.0 + temp * config.tempScoreWeight)
                .coerceIn(1.0, 1.25)

        val finalScore =
            (baseScore * skinTemperatureMultiplier)
                .coerceIn(0.0, 1.0)

        return finalScore
    }

    /**
     * 각 지표 점수를 final score 계산에 사용할 수 있도록 정규화한다.
     *
     * null, NaN, Infinity는 계산에 쓰지 않고 0점으로 처리한다.
     * 1.0을 넘는 값은 1.0으로 자른다.
     */
    private fun normalizeWakeScore(
        score: Double?
    ): Double {
        if (score == null) return 0.0
        if (score.isNaN()) return 0.0
        if (!score.isFinite()) return 0.0

        return score.coerceIn(0.0, 1.0)
    }

    /**
     * PPG 600 bytes에서 IR sample 100개를 추출해서 ppgIrBuffer에 저장한다.
     *
     * Potch PPG 구조:
     * 1 sample = RED 3B + IR 3B
     * 1초 = 100 samples = 600 bytes
     */
    private fun appendPpg(ppgData: ByteArray) {
        val redSamples = extractRedSamples(ppgData)
        val irSamples = extractIrSamples(ppgData)

        for (red in redSamples) {
            ppgRedBuffer.addLast(red.toDouble())

            if (ppgRedBuffer.size > maxPpgSamples) {
                ppgRedBuffer.removeFirst()
            }
        }

        for (ir in irSamples) {
            ppgIrBuffer.addLast(ir.toDouble())

            if (ppgIrBuffer.size > maxPpgSamples) {
                ppgIrBuffer.removeFirst()
            }
        }
    }

    /**
     * IMU 600 bytes에서 x, y, z sample 100개를 추출하고,
     * 각 sample을 g magnitude로 변환해서 imuGBuffer에 저장한다.
     *
     * Potch IMU 구조:
     * 1 sample = X 2B + Y 2B + Z 2B
     * 1초 = 100 samples = 600 bytes
     */
    private fun appendImu(imuData: ByteArray) {
        for (i in imuData.indices step 6) {
            if (i + 5 >= imuData.size) break

            val xRaw = readInt16LE(imuData, i)
            val yRaw = readInt16LE(imuData, i + 2)
            val zRaw = readInt16LE(imuData, i + 4)

            val xG = xRaw / config.imuLsbPerG
            val yG = yRaw / config.imuLsbPerG
            val zG = zRaw / config.imuLsbPerG

            val gMagnitude = sqrt(
                xG * xG +
                        yG * yG +
                        zG * zG
            )

            imuGBuffer.addLast(gMagnitude)

            if (imuGBuffer.size > maxImuSamples) {
                imuGBuffer.removeFirst()
            }

            val microFiltered = microBpf.filter(gMagnitude)

            microFilteredBuffer.addLast(microFiltered)

            if (microFilteredBuffer.size > maxImuSamples) {
                microFilteredBuffer.removeFirst()
            }
        }
    }

    /**
     * NTC에서 계산된 피부온도를 시간과 함께 저장한다.
     *
     * SensorData.ntcCelsius가 -999.0이면 계산 불가능한 값으로 보고 버린다.
     */
    private fun appendTemperature(
        timestampMillis: Long,
        celsius: Double
    ) {
        if (celsius == -999.0) return
        if (celsius.isNaN()) return
        if (celsius < 0.0 || celsius > 60.0) return

        temperatureBuffer.addLast(timestampMillis to celsius)

        trimTimeBuffer(
            buffer = temperatureBuffer,
            nowMillis = timestampMillis,
            windowMillis = config.temperatureWindowMillis
        )
    }

    /**
     * 기존 estimateHeartRate()에서 계산된 bpm을 시간과 함께 저장한다.
     */
    private fun appendHeartRate(
        timestampMillis: Long,
        bpm: Int
    ) {
        if (bpm !in 30..220) return

        heartRateBuffer.addLast(timestampMillis to bpm)

        trimTimeBuffer(
            buffer = heartRateBuffer,
            nowMillis = timestampMillis,
            windowMillis = config.heartRateWindowMillis
        )
    }

    private fun appendRespirationRate(
        timestampMillis: Long,
        rrBpm: Double,
        confidence: Double
    ) {
        if (rrBpm !in config.rrMinBpm..config.rrMaxBpm) return
        if (confidence < config.rrScoreMinUsableConfidence) return

        respirationRateBuffer.addLast(timestampMillis to rrBpm)

        trimTimeBuffer(
            buffer = respirationRateBuffer,
            nowMillis = timestampMillis,
            windowMillis = config.rrHistoryWindowMillis
        )
    }

    /**
     * 디버깅용.
     * 현재 각 buffer에 데이터가 얼마나 쌓였는지 확인할 수 있다.
     */
    fun getBufferSnapshot(): ArousalBufferSnapshot {
        return ArousalBufferSnapshot(
            ppgIrSampleCount = ppgIrBuffer.size,
            imuGSampleCount = imuGBuffer.size,
            temperatureSampleCount = temperatureBuffer.size,
            heartRateSampleCount = heartRateBuffer.size,
            latestTemperatureCelsius = temperatureBuffer.lastOrNull()?.second,
            latestHeartRateBpm = heartRateBuffer.lastOrNull()?.second
        )
    }

    /**
     * 나중에 calculateMicroMovementVariance() 같은 함수에서 사용할 수 있도록
     * 최근 IMU G buffer를 복사해서 반환한다.
     */
    fun getImuGBufferCopy(): DoubleArray {
        return imuGBuffer.toDoubleArray()
    }

    /**
     * 나중에 calculateRrFromPpg(), calculateHrvLfHf() 같은 함수에서 사용할 수 있도록
     * 최근 PPG IR buffer를 복사해서 반환한다.
     */
    fun getPpgIrBufferCopy(): DoubleArray {
        return ppgIrBuffer.toDoubleArray()
    }

    /**
     * 나중에 calculateTemperatureGradient()에서 사용할 수 있도록
     * 최근 온도 buffer를 복사해서 반환한다.
     */
    fun getTemperatureBufferCopy(): List<Pair<Long, Double>> {
        return temperatureBuffer.toList()
    }

    /**
     * 나중에 calculateHrGradient()에서 사용할 수 있도록
     * 최근 HR buffer를 복사해서 반환한다.
     */
    fun getHeartRateBufferCopy(): List<Pair<Long, Int>> {
        return heartRateBuffer.toList()
    }
    /**
     * PPG payload에서 IR 채널 18-bit sample을 추출한다.
     *
     * Potch PPG sample은 RED 3바이트 + IR 3바이트 구조이므로
     * 각 6바이트 묶음의 뒤쪽 3바이트를 IR 값으로 복원한다.
     */
    private fun extractIrSamples(ppgData: ByteArray): IntArray {
        val sampleCount = ppgData.size / 6

        return IntArray(sampleCount) { index ->
            val base = index * 6

            ((ppgData[base + 3].toInt() and 0x03) shl 16) or
                    ((ppgData[base + 4].toInt() and 0xFF) shl 8) or
                    (ppgData[base + 5].toInt() and 0xFF)
        }
    }
    /**
     * PPG payload에서 RED 채널 18-bit sample을 추출한다.
     *
     * RED는 각 6바이트 sample 묶음의 앞쪽 3바이트에 저장되어 있으며,
     * IR 기반 계산이 실패했을 때 호흡 추정 백업 채널로 사용할 수 있다.
     */
    private fun extractRedSamples(ppgData: ByteArray): IntArray {
        val sampleCount = ppgData.size / 6

        return IntArray(sampleCount) { index ->
            val base = index * 6

            ((ppgData[base].toInt() and 0x03) shl 16) or
                    ((ppgData[base + 1].toInt() and 0xFF) shl 8) or
                    (ppgData[base + 2].toInt() and 0xFF)
        }
    }

    /**
     * little-endian 2바이트 signed integer를 Int로 변환한다.
     *
     * IMU X/Y/Z raw 값은 2바이트 little-endian signed 값이므로
     * ByteArray에서 읽은 뒤 Short로 sign extension하여 사용한다.
     */
    private fun readInt16LE(
        bytes: ByteArray,
        index: Int
    ): Int {
        return ((bytes[index].toInt() and 0xFF) or
                ((bytes[index + 1].toInt() and 0xFF) shl 8))
            .toShort()
            .toInt()
    }

    /**
     * 시간 기반 rolling buffer에서 window 밖의 오래된 값을 제거한다.
     *
     * temperatureBuffer, heartRateBuffer처럼 timestamp를 함께 저장하는 buffer에서
     * 최근 n분 데이터만 유지하기 위해 공통으로 사용한다.
     */
    private fun <T> trimTimeBuffer(
        buffer: ArrayDeque<Pair<Long, T>>,
        nowMillis: Long,
        windowMillis: Long
    ) {
        while (buffer.isNotEmpty()) {
            val oldestTime = buffer.first().first

            if (nowMillis - oldestTime <= windowMillis) {
                break
            }

            buffer.removeFirst()
        }
    }

    /**
     * 계산기 내부의 모든 rolling buffer와 상태형 필터를 초기화한다.
     *
     * BLE 연결을 새로 시작하거나 실험을 재시작할 때 이전 데이터가
     * 새 계산에 섞이지 않도록 호출한다.
     */
    fun reset() {
        ppgIrBuffer.clear()
        ppgRedBuffer.clear()
        imuGBuffer.clear()
        microFilteredBuffer.clear()
        temperatureBuffer.clear()
        heartRateBuffer.clear()
        respirationRateBuffer.clear()
        hrvIbiBuffer.clear()
        lastAcceptedHrvIbiEndSampleIndex = Long.MIN_VALUE

        microBpf.reset()
    }

    /************************** Micro Movement ****************************/

    private val microBpf = SimpleBandPassFilter(
        sampleRateHz = config.sampleRateHz,
        lowCutHz = config.microLowCutHz,
        highCutHz = config.microHighCutHz
    )

    /**
     * BPF를 통과한 micro movement 후보 신호.
     *
     * 원본 imuGBuffer:
     * - 중력
     * - 자세 변화
     * - 호흡
     * - 미세 움직임
     * - 노이즈
     *
     * microFilteredBuffer:
     * - 0.5~5Hz 대역의 미세 흔들림 성분
     */
    private val microFilteredBuffer = ArrayDeque<Double>()

    /**
     * 간단한 1차 high-pass + low-pass 조합의 band-pass filter.
     *
     * 정교한 Biquad 필터는 아니지만, 개발 초기 단계에서
     * micro movement와 respiration 대역을 분리하는 용도로 사용한다.
     */

    private class SimpleBandPassFilter(
        sampleRateHz: Double,
        lowCutHz: Double,
        highCutHz: Double
    ) {
        private val highPass = OnePoleHighPassFilter(
            sampleRateHz = sampleRateHz,
            cutoffHz = lowCutHz
        )

        private val lowPass = OnePoleLowPassFilter(
            sampleRateHz = sampleRateHz,
            cutoffHz = highCutHz
        )

        fun filter(x: Double): Double {
            val highPassed = highPass.filter(x)
            return lowPass.filter(highPassed)
        }

        fun reset() {
            highPass.reset()
            lowPass.reset()
        }
    }

    /**
     * 1차 IIR low-pass filter.
     *
     * cutoffHz보다 높은 빠른 변화를 줄이고 낮은 주파수 성분을 부드럽게 통과시킨다.
     */
    private class OnePoleLowPassFilter(
        sampleRateHz: Double,
        cutoffHz: Double
    ) {
        private val dt = 1.0 / sampleRateHz
        private val rc = 1.0 / (2.0 * Math.PI * cutoffHz)
        private val alpha = dt / (rc + dt)

        private var y = 0.0
        private var initialized = false

        fun filter(x: Double): Double {
            if (!initialized) {
                y = x
                initialized = true
                return y
            }

            y += alpha * (x - y)
            return y
        }

        fun reset() {
            y = 0.0
            initialized = false
        }
    }

    /**
     * 1차 IIR high-pass filter.
     *
     * cutoffHz보다 낮은 DC/느린 추세 성분을 줄이고 빠른 변화 성분을 통과시킨다.
     */
    private class OnePoleHighPassFilter(
        sampleRateHz: Double,
        cutoffHz: Double
    ) {
        private val dt = 1.0 / sampleRateHz
        private val rc = 1.0 / (2.0 * Math.PI * cutoffHz)
        private val alpha = rc / (rc + dt)

        private var prevX = 0.0
        private var prevY = 0.0
        private var initialized = false

        fun filter(x: Double): Double {
            if (!initialized) {
                prevX = x
                prevY = 0.0
                initialized = true
                return 0.0
            }

            val y = alpha * (prevY + x - prevX)

            prevX = x
            prevY = y

            return y
        }

        fun reset() {
            prevX = 0.0
            prevY = 0.0
            initialized = false
        }
    }

    /**
     * IMU 기반 micro movement 지표를 계산한다.
     *
     * 0.5~5Hz 대역으로 필터링된 g-magnitude window에서 RMS와 variance를 구하고,
     * threshold와 비교해 stable/weak/micro/macro movement 단계로 분류한다.
     */
    fun calculateMicroMovement(): MicroMovementResult? {
        val windowSampleCount =
            (config.sampleRateHz * config.microWindowSeconds).toInt()

        val minSampleCount =
            (config.sampleRateHz * config.microMinWindowSeconds).toInt()

        if (microFilteredBuffer.size < minSampleCount) {
            return null
        }

        val windowValues =
            if (microFilteredBuffer.size > windowSampleCount) {
                microFilteredBuffer.takeLast(windowSampleCount)
            } else {
                microFilteredBuffer.toList()
            }

        if (windowValues.isEmpty()) return null

        val mean = windowValues.average()

        var sumSquaredDiff = 0.0
        var sumSquared = 0.0

        for (v in windowValues) {
            val diff = v - mean
            sumSquaredDiff += diff * diff
            sumSquared += v * v
        }

        val variance = sumSquaredDiff / windowValues.size
        val rms = sqrt(sumSquared / windowValues.size)

        val score =
            if (config.microRmsDetectedThresholdG <= 0.0) {
                0.0
            } else {
                rms / config.microRmsDetectedThresholdG
            }

        val isMacroMovementLike = rms >= config.macroMovementThresholdG
        val isMicroMovementDetected =
            rms >= config.microRmsDetectedThresholdG &&
                    !isMacroMovementLike

        val level = when {
            isMacroMovementLike -> MicroMovementLevel.MACRO_MOVEMENT
            rms >= config.microRmsDetectedThresholdG -> MicroMovementLevel.MICRO_MOVEMENT
            rms >= config.microRmsWeakThresholdG -> MicroMovementLevel.WEAK
            else -> MicroMovementLevel.STABLE
        }

        return MicroMovementResult(
            sampleCount = windowValues.size,
            windowSeconds = windowValues.size / config.sampleRateHz,
            rmsG = rms,
            varianceG = variance,
            score = score,
            level = level,
            isMicroMovementDetected = isMicroMovementDetected,
            isMacroMovementLike = isMacroMovementLike
        )
    }

    /**
     * micro movement BPF를 통과한 최근 IMU 신호를 복사해서 반환한다.
     *
     * 디버깅, 그래프 표시, threshold 튜닝용으로 원본 buffer를 직접 노출하지 않고 복사본을 제공한다.
     */
    fun getMicroFilteredBufferCopy(): DoubleArray {
        return microFilteredBuffer.toDoubleArray()
    }

    /********************* //Micro Movement ********************/

    /********************* RR from PPG ********************/

    /**
     * PPG 기반 호흡수를 계산한다.
     *
     * IR 채널을 우선 사용하고, IR에서 유효한 호흡 파형을 찾지 못하면
     * RED 채널을 백업으로 사용한다.
     */
    fun calculatePpgRespiration(): PpgRespirationResult? {
        val irResult = calculatePpgRespirationFromBuffer(
            channel = PpgRespirationChannel.IR,
            buffer = ppgIrBuffer
        )

        if (irResult != null) {
            return irResult
        }

        // IR에서 호흡 파형 검출이 실패하면 RED를 백업으로 사용
        return calculatePpgRespirationFromBuffer(
            channel = PpgRespirationChannel.RED,
            buffer = ppgRedBuffer
        )
    }

    /**
     * PPG 기반 RR bpm만 간단히 얻기 위한 helper.
     *
     * 상세 품질 정보가 필요한 경우에는 calculatePpgRespiration() 결과를 직접 사용한다.
     */
    private fun calculateRrFromPpg(): Double? {
        return calculatePpgRespiration()?.rrBpm
    }

    /**
     * 지정된 PPG 채널 buffer에서 호흡 파형을 추출한다.
     *
     * 최근 window를 가져와 DC 제거 후 0.1~0.5Hz BPF를 적용하고,
     * 양/음 peak 방향을 모두 시도해 더 품질이 좋은 결과를 선택한다.
     */
    private fun calculatePpgRespirationFromBuffer(
        channel: PpgRespirationChannel,
        buffer: ArrayDeque<Double>
    ): PpgRespirationResult? {
        val windowSampleCount =
            (config.sampleRateHz * config.ppgRespWindowSeconds).toInt()

        val minSampleCount =
            (config.sampleRateHz * config.ppgRespMinWindowSeconds).toInt()

        if (buffer.size < minSampleCount) {
            return null
        }

        val rawWindow =
            if (buffer.size > windowSampleCount) {
                buffer.takeLast(windowSampleCount)
            } else {
                buffer.toList()
            }

        if (rawWindow.size < minSampleCount) {
            return null
        }

        // 1. DC 제거
        val mean = rawWindow.average()
        val acSignal = DoubleArray(rawWindow.size) { i ->
            rawWindow[i] - mean
        }

        // 2. 호흡 대역 BPF: 0.1~0.5Hz
        // 주의: 계산할 때마다 window 전체를 새로 필터링한다.
        // append 시점에 상태형 필터를 계속 적용하는 방식은 나중에 최적화 가능.
        val respBpf = SimpleBandPassFilter(
            sampleRateHz = config.sampleRateHz,
            lowCutHz = config.respLowCutHz,
            highCutHz = config.respHighCutHz
        )

        val filtered = DoubleArray(acSignal.size) { i ->
            respBpf.filter(acSignal[i])
        }

        // 3. 필터 초기 구간은 안정화 전이라 버림
        val warmupSamples = (config.sampleRateHz * 2.0).toInt()
        if (filtered.size <= warmupSamples + 10) {
            return null
        }

        val usableStartIndex = warmupSamples
        val usableValues = filtered.drop(usableStartIndex)

        val maxValue = usableValues.maxOrNull() ?: return null
        val minValue = usableValues.minOrNull() ?: return null
        val peakToPeakAmplitude = maxValue - minValue

        // 4. 호흡 파형 진폭이 너무 작으면 실패
        if (peakToPeakAmplitude < config.ppgRespMinPeakToPeakAmplitude) {
            return null
        }

        // 5. 양의 peak와 음의 peak 둘 다 시도
        // PPG 호흡 파형은 부착/압박/개인차에 따라 방향이 뒤집혀 보일 수 있음.
        val positiveResult = calculateRrFromRespWave(
            channel = channel,
            filtered = filtered,
            usableStartIndex = usableStartIndex,
            peakToPeakAmplitude = peakToPeakAmplitude,
            invert = false
        )

        val negativeResult = calculateRrFromRespWave(
            channel = channel,
            filtered = filtered,
            usableStartIndex = usableStartIndex,
            peakToPeakAmplitude = peakToPeakAmplitude,
            invert = true
        )

        return chooseBetterRespirationResult(
            positiveResult,
            negativeResult
        )
    }

    /**
     * 필터링된 PPG 호흡 후보 파형에서 peak interval 기반 RR을 계산한다.
     *
     * peak threshold, 최소 peak 간격, 생리적 호흡수 범위를 적용해
     * 노이즈 peak를 줄이고 RR bpm과 interval 리스트를 산출한다.
     */
    private fun calculateRrFromRespWave(
        channel: PpgRespirationChannel,
        filtered: DoubleArray,
        usableStartIndex: Int,
        peakToPeakAmplitude: Double,
        invert: Boolean
    ): PpgRespirationResult? {
        val wave = if (invert) {
            DoubleArray(filtered.size) { i -> -filtered[i] }
        } else {
            filtered
        }

        val usable = wave.drop(usableStartIndex)
        if (usable.isEmpty()) return null

        val maxValue = usable.maxOrNull() ?: return null
        val minValue = usable.minOrNull() ?: return null
        val threshold = minValue + (maxValue - minValue) * 0.55

        val minPeakDistanceSamples =
            (config.sampleRateHz * (60.0 / config.rrMaxBpm)).toInt()

        val maxPeakDistanceSamples =
            (config.sampleRateHz * (60.0 / config.rrMinBpm)).toInt()

        val peakIndices = mutableListOf<Int>()
        var lastPeakIndex = -minPeakDistanceSamples

        for (i in usableStartIndex + 1 until wave.size - 1) {
            val isPeak =
                wave[i] > wave[i - 1] &&
                        wave[i] > wave[i + 1] &&
                        wave[i] > threshold

            if (!isPeak) continue

            val distance = i - lastPeakIndex

            if (distance >= minPeakDistanceSamples) {
                peakIndices.add(i)
                lastPeakIndex = i
            } else if (peakIndices.isNotEmpty()) {
                val last = peakIndices.last()

                // 너무 가까운 peak가 여러 개 잡히면 더 높은 peak로 교체
                if (wave[i] > wave[last]) {
                    peakIndices[peakIndices.lastIndex] = i
                    lastPeakIndex = i
                }
            }
        }

        if (peakIndices.size < 3) {
            return null
        }

        val intervals = mutableListOf<Double>()

        for (i in 1 until peakIndices.size) {
            val diffSamples = peakIndices[i] - peakIndices[i - 1]
            val intervalSec = diffSamples / config.sampleRateHz

            val minIntervalSec = 60.0 / config.rrMaxBpm
            val maxIntervalSec = 60.0 / config.rrMinBpm

            if (intervalSec in minIntervalSec..maxIntervalSec) {
                intervals.add(intervalSec)
            }
        }

        if (intervals.size < 2) {
            return null
        }

        val usedIntervals = removeRespIntervalOutliers(intervals)

        if (usedIntervals.size < 2) {
            return null
        }

        val averageIntervalSec = usedIntervals.average()
        if (averageIntervalSec <= 0.0) {
            return null
        }

        val rrBpm = 60.0 / averageIntervalSec

        if (rrBpm !in config.rrMinBpm..config.rrMaxBpm) {
            return null
        }

        val intervalRegularityScore = calculateIntervalRegularityScore(usedIntervals)
        val amplitudeScore =
            (peakToPeakAmplitude / config.ppgRespMinPeakToPeakAmplitude)
                .coerceIn(0.0, 3.0) / 3.0

        val qualityScore =
            (intervalRegularityScore * 0.7 + amplitudeScore * 0.3)
                .coerceIn(0.0, 1.0)

        return PpgRespirationResult(
            channel = channel,
            rrBpm = rrBpm,
            peakCount = peakIndices.size,
            intervalCount = usedIntervals.size,
            averageIntervalSec = averageIntervalSec,
            peakToPeakAmplitude = peakToPeakAmplitude,
            qualityScore = qualityScore,
            intervalsSec = usedIntervals
        )
    }

    /**
     * PPG 호흡 interval 리스트에서 튄 값을 제거한다.
     *
     * 중앙값 대비 허용 비율을 벗어난 interval을 버려
     * 잘못 검출된 peak가 RR/RRV 계산에 주는 영향을 줄인다.
     */
    private fun removeRespIntervalOutliers(
        intervals: List<Double>
    ): List<Double> {
        if (intervals.size < 3) {
            return intervals
        }

        val sorted = intervals.sorted()
        val median = sorted[sorted.size / 2]

        if (median <= 0.0) {
            return intervals
        }

        val filtered = intervals.filter { interval ->
            abs(interval - median) / median <= config.ppgRespIntervalOutlierTolerance
        }

        return filtered.ifEmpty { intervals }
    }

    /**
     * PPG 호흡 interval의 규칙성을 0~1 점수로 계산한다.
     *
     * interval의 변동계수(CV)가 낮을수록 규칙적인 호흡 파형으로 판단한다.
     */
    private fun calculateIntervalRegularityScore(
        intervals: List<Double>
    ): Double {
        if (intervals.size < 2) {
            return 0.0
        }

        val mean = intervals.average()
        if (mean <= 0.0) {
            return 0.0
        }

        var sumSquaredDiff = 0.0

        for (interval in intervals) {
            val diff = interval - mean
            sumSquaredDiff += diff * diff
        }

        val std = kotlin.math.sqrt(sumSquaredDiff / intervals.size)

        // 변동계수 CV = 표준편차 / 평균
        // 호흡 interval이 일정할수록 CV가 낮음.
        val cv = std / mean

        return (1.0 - cv).coerceIn(0.0, 1.0)
    }

    /**
     * 양의 peak와 음의 peak 중 더 신뢰할 수 있는 PPG 호흡 결과를 선택한다.
     *
     * 부착 상태에 따라 PPG 호흡 파형 방향이 뒤집힐 수 있으므로
     * 두 방향을 모두 계산한 뒤 qualityScore가 높은 쪽을 사용한다.
     */
    private fun chooseBetterRespirationResult(
        a: PpgRespirationResult?,
        b: PpgRespirationResult?
    ): PpgRespirationResult? {
        if (a == null) return b
        if (b == null) return a

        return if (a.qualityScore >= b.qualityScore) a else b
    }

    /**
     * 최근 PPG RED buffer를 복사해서 반환한다.
     *
     * RED 채널 호흡 백업 계산이나 디버깅 그래프에 사용할 수 있다.
     */
    fun getPpgRedBufferCopy(): DoubleArray {
        return ppgRedBuffer.toDoubleArray()
    }

    /********************* //RR from PPG ********************/

    /********************* RR from IMU ********************/

    /**
     * IMU 기반 호흡수를 계산한다.
     *
     * g-magnitude에서 DC/자세 성분을 제거한 뒤 0.1~0.5Hz 호흡 대역만 남기고,
     * peak 간격으로 RR bpm을 추정한다.
     */
    fun calculateImuRespiration(): ImuRespirationResult? {
        val windowSampleCount =
            (config.sampleRateHz * config.imuRespWindowSeconds).toInt()

        val minSampleCount =
            (config.sampleRateHz * config.imuRespMinWindowSeconds).toInt()

        if (imuGBuffer.size < minSampleCount) {
            return null
        }

        val rawWindow =
            if (imuGBuffer.size > windowSampleCount) {
                imuGBuffer.takeLast(windowSampleCount)
            } else {
                imuGBuffer.toList()
            }

        if (rawWindow.size < minSampleCount) {
            return null
        }

        // 1. DC 제거
        // gMagnitude에는 중력/자세 성분이 크게 들어 있으므로 평균을 빼서 중심을 0 근처로 맞춘다.
        val mean = rawWindow.average()
        val acSignal = DoubleArray(rawWindow.size) { i ->
            rawWindow[i] - mean
        }

        // 2. 호흡 대역 BPF: 0.1~0.5Hz
        // 6~30 breaths/min 정도만 남긴다.
        val respBpf = SimpleBandPassFilter(
            sampleRateHz = config.sampleRateHz,
            lowCutHz = config.respLowCutHz,
            highCutHz = config.respHighCutHz
        )

        val filtered = DoubleArray(acSignal.size) { i ->
            respBpf.filter(acSignal[i])
        }

        // 3. 필터 안정화 전 구간 버림
        val warmupSamples = (config.sampleRateHz * 2.0).toInt()

        if (filtered.size <= warmupSamples + 10) {
            return null
        }

        val usableValues = filtered.drop(warmupSamples)

        val maxValue = usableValues.maxOrNull() ?: return null
        val minValue = usableValues.minOrNull() ?: return null
        val peakToPeakAmplitudeG = maxValue - minValue

        // 4. 호흡성 움직임 진폭이 너무 작으면 실패 처리
        if (peakToPeakAmplitudeG < config.imuRespMinPeakToPeakAmplitudeG) {
            return null
        }

        // 5. 양의 peak / 음의 peak 둘 다 시도
        val positiveResult = calculateRrFromImuRespWave(
            filtered = filtered,
            usableStartIndex = warmupSamples,
            peakToPeakAmplitudeG = peakToPeakAmplitudeG,
            invert = false
        )

        val negativeResult = calculateRrFromImuRespWave(
            filtered = filtered,
            usableStartIndex = warmupSamples,
            peakToPeakAmplitudeG = peakToPeakAmplitudeG,
            invert = true
        )

        return chooseBetterImuRespirationResult(
            positiveResult,
            negativeResult
        )
    }
    /**
     * IMU 기반 RR bpm만 간단히 얻기 위한 helper.
     *
     * 상세 품질 정보가 필요한 경우에는 calculateImuRespiration() 결과를 직접 사용한다.
     */
    private fun calculateRrFromImu(): Double? {
        return calculateImuRespiration()?.rrBpm
    }

    /**
     * 필터링된 IMU 호흡 후보 파형에서 peak interval 기반 RR을 계산한다.
     *
     * 호흡성 흉부 움직임 peak를 검출하고, 생리적 범위와 outlier 제거를 거쳐
     * RR bpm, interval 리스트, 품질 점수를 산출한다.
     */
    private fun calculateRrFromImuRespWave(
        filtered: DoubleArray,
        usableStartIndex: Int,
        peakToPeakAmplitudeG: Double,
        invert: Boolean
    ): ImuRespirationResult? {
        val wave =
            if (invert) {
                DoubleArray(filtered.size) { i -> -filtered[i] }
            } else {
                filtered
            }

        val usable = wave.drop(usableStartIndex)

        if (usable.isEmpty()) {
            return null
        }

        val maxValue = usable.maxOrNull() ?: return null
        val minValue = usable.minOrNull() ?: return null

        // peak threshold.
        // 파형 전체 범위 중 상위 45% 정도만 peak 후보로 본다.
        val threshold = minValue + (maxValue - minValue) * 0.55

        val minPeakDistanceSamples =
            (config.sampleRateHz * (60.0 / config.rrMaxBpm)).toInt()

        val peakIndices = mutableListOf<Int>()
        var lastPeakIndex = -minPeakDistanceSamples

        for (i in usableStartIndex + 1 until wave.size - 1) {
            val isPeak =
                wave[i] > wave[i - 1] &&
                        wave[i] > wave[i + 1] &&
                        wave[i] > threshold

            if (!isPeak) continue

            val distance = i - lastPeakIndex

            if (distance >= minPeakDistanceSamples) {
                peakIndices.add(i)
                lastPeakIndex = i
            } else if (peakIndices.isNotEmpty()) {
                val last = peakIndices.last()

                // 너무 가까운 peak가 여러 개 잡히면 더 높은 peak로 교체
                if (wave[i] > wave[last]) {
                    peakIndices[peakIndices.lastIndex] = i
                    lastPeakIndex = i
                }
            }
        }

        // 호흡수 계산에는 최소 3개 peak 정도는 있어야 안정적이다.
        if (peakIndices.size < 3) {
            return null
        }

        val intervals = mutableListOf<Double>()

        val minIntervalSec = 60.0 / config.rrMaxBpm
        val maxIntervalSec = 60.0 / config.rrMinBpm

        for (i in 1 until peakIndices.size) {
            val diffSamples = peakIndices[i] - peakIndices[i - 1]
            val intervalSec = diffSamples / config.sampleRateHz

            if (intervalSec in minIntervalSec..maxIntervalSec) {
                intervals.add(intervalSec)
            }
        }

        if (intervals.size < 2) {
            return null
        }

        val usedIntervals = removeImuRespIntervalOutliers(intervals)

        if (usedIntervals.size < 2) {
            return null
        }

        val averageIntervalSec = usedIntervals.average()

        if (averageIntervalSec <= 0.0) {
            return null
        }

        val rrBpm = 60.0 / averageIntervalSec

        if (rrBpm !in config.rrMinBpm..config.rrMaxBpm) {
            return null
        }

        val intervalRegularityScore =
            calculateImuIntervalRegularityScore(usedIntervals)

        val amplitudeScore =
            (peakToPeakAmplitudeG / config.imuRespMinPeakToPeakAmplitudeG)
                .coerceIn(0.0, 3.0) / 3.0

        val qualityScore =
            (intervalRegularityScore * 0.75 + amplitudeScore * 0.25)
                .coerceIn(0.0, 1.0)

        return ImuRespirationResult(
            rrBpm = rrBpm,
            peakCount = peakIndices.size,
            intervalCount = usedIntervals.size,
            averageIntervalSec = averageIntervalSec,
            peakToPeakAmplitudeG = peakToPeakAmplitudeG,
            qualityScore = qualityScore,
            inverted = invert,
            intervalsSec = usedIntervals
        )
    }

    /**
     * IMU 호흡 interval 리스트에서 튄 값을 제거한다.
     *
     * 중앙값 대비 허용 비율을 벗어난 interval을 제외해
     * 큰 움직임이나 잘못 잡힌 peak의 영향을 줄인다.
     */
    private fun removeImuRespIntervalOutliers(
        intervals: List<Double>
    ): List<Double> {
        if (intervals.size < 3) {
            return intervals
        }

        val sorted = intervals.sorted()
        val median = sorted[sorted.size / 2]

        if (median <= 0.0) {
            return intervals
        }

        val filtered = intervals.filter { interval ->
            kotlin.math.abs(interval - median) / median <=
                    config.imuRespIntervalOutlierTolerance
        }

        return filtered.ifEmpty { intervals }
    }

    /**
     * IMU 호흡 interval의 규칙성을 0~1 점수로 계산한다.
     *
     * interval 변동계수가 낮을수록 안정적인 호흡 peak 검출로 판단한다.
     */
    private fun calculateImuIntervalRegularityScore(
        intervals: List<Double>
    ): Double {
        if (intervals.size < 2) {
            return 0.0
        }

        val mean = intervals.average()

        if (mean <= 0.0) {
            return 0.0
        }

        var sumSquaredDiff = 0.0

        for (interval in intervals) {
            val diff = interval - mean
            sumSquaredDiff += diff * diff
        }

        val std = sqrt(sumSquaredDiff / intervals.size)

        // CV = 표준편차 / 평균
        // 호흡 interval이 규칙적일수록 CV가 낮다.
        val cv = std / mean

        return (1.0 - cv).coerceIn(0.0, 1.0)
    }

    /**
     * 양의 peak와 음의 peak 중 더 신뢰할 수 있는 IMU 호흡 결과를 선택한다.
     *
     * 센서 부착 방향이나 자세에 따라 호흡 파형 부호가 바뀔 수 있어
     * 두 방향 중 qualityScore가 높은 결과를 사용한다.
     */
    private fun chooseBetterImuRespirationResult(
        a: ImuRespirationResult?,
        b: ImuRespirationResult?
    ): ImuRespirationResult? {
        if (a == null) return b
        if (b == null) return a

        return if (a.qualityScore >= b.qualityScore) a else b
    }

    /********************* //RR from IMU ********************/

    /********************* Fusion RR data from PPG & IMU ********************/

    /**
     * PPG RR과 IMU RR을 합성해 최종 RR을 결정한다.
     *
     * 두 센서가 비슷하면 품질 기반 가중 평균을 사용하고,
     * 한쪽만 유효하거나 서로 크게 다르면 IMU 우선 정책과 quality를 기준으로 선택한다.
     */
    fun fuseRespiration(
        ppg: PpgRespirationResult?,
        imu: ImuRespirationResult?
    ): RrFusionResult {
        val ppgValid = ppg?.rrBpm?.let { isValidRr(it) } == true
        val imuValid = imu?.rrBpm?.let { isValidRr(it) } == true

        if (!ppgValid && !imuValid) {
            return RrFusionResult(
                rrBpm = null,
                source = RrFusionSource.NONE,
                ppgRrBpm = ppg?.rrBpm,
                imuRrBpm = imu?.rrBpm,
                ppgQuality = ppg?.qualityScore,
                imuQuality = imu?.qualityScore,
                diffBpm = null,
                confidence = 0.0,
                log = "RR fusion failed: no valid PPG/IMU RR"
            )
        }

        if (imuValid && !ppgValid) {
            return RrFusionResult(
                rrBpm = imu!!.rrBpm,
                source = RrFusionSource.IMU_ONLY,
                ppgRrBpm = ppg?.rrBpm,
                imuRrBpm = imu.rrBpm,
                ppgQuality = ppg?.qualityScore,
                imuQuality = imu.qualityScore,
                diffBpm = null,
                confidence = imu.qualityScore.coerceIn(0.0, 1.0),
                log = "RR fusion: IMU only"
            )
        }

        if (!imuValid && ppgValid) {
            return RrFusionResult(
                rrBpm = ppg!!.rrBpm,
                source = RrFusionSource.PPG_ONLY,
                ppgRrBpm = ppg.rrBpm,
                imuRrBpm = imu?.rrBpm,
                ppgQuality = ppg.qualityScore,
                imuQuality = imu?.qualityScore,
                diffBpm = null,
                confidence = (ppg.qualityScore * 0.8).coerceIn(0.0, 1.0),
                log = "RR fusion: PPG only"
            )
        }

        val ppgRr = ppg!!.rrBpm
        val imuRr = imu!!.rrBpm
        val diff = abs(ppgRr - imuRr)

        val ppgQuality = ppg.qualityScore.coerceIn(0.0, 1.0)
        val imuQuality = imu.qualityScore.coerceIn(0.0, 1.0)

        // 1. 둘이 충분히 비슷하면 가중 평균
        if (diff <= config.rrFusionAgreeDiffBpm) {
            val imuWeight =
                config.rrFusionImuBaseWeight * (0.5 + imuQuality)

            val ppgWeight =
                config.rrFusionPpgBaseWeight * (0.5 + ppgQuality)

            val totalWeight = imuWeight + ppgWeight

            val fusedRr =
                if (totalWeight <= 0.0) {
                    imuRr
                } else {
                    (imuRr * imuWeight + ppgRr * ppgWeight) / totalWeight
                }

            val agreementScore =
                (1.0 - diff / config.rrFusionAgreeDiffBpm)
                    .coerceIn(0.0, 1.0)

            val confidence =
                (agreementScore * 0.5 +
                        imuQuality * 0.35 +
                        ppgQuality * 0.15)
                    .coerceIn(0.0, 1.0)

            return RrFusionResult(
                rrBpm = fusedRr,
                source = RrFusionSource.BOTH_WEIGHTED,
                ppgRrBpm = ppgRr,
                imuRrBpm = imuRr,
                ppgQuality = ppgQuality,
                imuQuality = imuQuality,
                diffBpm = diff,
                confidence = confidence,
                log = "RR fusion: weighted, diff=${"%.2f".format(diff)}"
            )
        }

        // 2. 차이가 크면 기본적으로 IMU 우선
        // 단, IMU quality가 낮고 PPG quality가 높으면 PPG 사용
        val imuUsable = imuQuality >= config.rrFusionMinUsableQuality
        val ppgUsable = ppgQuality >= config.rrFusionMinUsableQuality

        if (!imuUsable && ppgUsable) {
            return RrFusionResult(
                rrBpm = ppgRr,
                source = RrFusionSource.PPG_PREFERRED_DISAGREE,
                ppgRrBpm = ppgRr,
                imuRrBpm = imuRr,
                ppgQuality = ppgQuality,
                imuQuality = imuQuality,
                diffBpm = diff,
                confidence = (ppgQuality * 0.7).coerceIn(0.0, 1.0),
                log = "RR fusion: disagree, PPG preferred because IMU quality is low"
            )
        }

        return RrFusionResult(
            rrBpm = imuRr,
            source = RrFusionSource.IMU_PREFERRED_DISAGREE,
            ppgRrBpm = ppgRr,
            imuRrBpm = imuRr,
            ppgQuality = ppgQuality,
            imuQuality = imuQuality,
            diffBpm = diff,
            confidence = (imuQuality * 0.8).coerceIn(0.0, 1.0),
            log = "RR fusion: disagree, IMU preferred"
        )
    }

    /**
     * RR bpm이 설정된 생리적 허용 범위 안에 있는지 확인한다.
     */
    private fun isValidRr(rrBpm: Double): Boolean {
        return rrBpm in config.rrMinBpm..config.rrMaxBpm
    }

    /********************* //Fusion RR data from PPG & IMU ********************/

    /********************* RR Arousal Score ********************/

    fun calculateRespiratoryRateArousal(
        rrFusion: RrFusionResult
    ): RespiratoryRateArousalResult? {
        val currentRr = rrFusion.rrBpm ?: return null

        if (currentRr !in config.rrMinBpm..config.rrMaxBpm) {
            return null
        }

        val confidence = rrFusion.confidence.coerceIn(0.0, 1.0)

        val absoluteScore = scoreRespiratoryRateAbsolute(currentRr)

        val latestTime = respirationRateBuffer.lastOrNull()?.first

        if (latestTime == null || respirationRateBuffer.size < config.rrScoreMinSampleCount) {
            val rawScore = absoluteScore
            val finalScore = rawScore * confidence

            return RespiratoryRateArousalResult(
                currentRrBpm = currentRr,
                baselineRrBpm = null,
                recentRrBpm = null,
                riseBpm = null,
                absoluteScore = absoluteScore,
                riseScore = null,
                rawScore = rawScore,
                score = finalScore,
                confidence = confidence,
                sampleCount = respirationRateBuffer.size,
                windowSeconds = null,
                log = "RR Score: current=${"%.1f".format(currentRr)}, abs=${"%.2f".format(absoluteScore)}, conf=${"%.2f".format(confidence)}, score=${"%.2f".format(finalScore)}"
            )
        }

        val windowValues = respirationRateBuffer
            .filter { (timestamp, rrBpm) ->
                latestTime - timestamp <= config.rrScoreWindowMillis &&
                        rrBpm in config.rrMinBpm..config.rrMaxBpm
            }

        if (windowValues.size < config.rrScoreMinSampleCount) {
            val rawScore = absoluteScore
            val finalScore = rawScore * confidence

            return RespiratoryRateArousalResult(
                currentRrBpm = currentRr,
                baselineRrBpm = null,
                recentRrBpm = null,
                riseBpm = null,
                absoluteScore = absoluteScore,
                riseScore = null,
                rawScore = rawScore,
                score = finalScore,
                confidence = confidence,
                sampleCount = windowValues.size,
                windowSeconds = null,
                log = "RR Score: current=${"%.1f".format(currentRr)}, abs=${"%.2f".format(absoluteScore)}, conf=${"%.2f".format(confidence)}, score=${"%.2f".format(finalScore)}"
            )
        }

        val windowDurationMillis =
            windowValues.last().first - windowValues.first().first

        if (windowDurationMillis < config.rrScoreMinWindowMillis) {
            val rawScore = absoluteScore
            val finalScore = rawScore * confidence

            return RespiratoryRateArousalResult(
                currentRrBpm = currentRr,
                baselineRrBpm = null,
                recentRrBpm = null,
                riseBpm = null,
                absoluteScore = absoluteScore,
                riseScore = null,
                rawScore = rawScore,
                score = finalScore,
                confidence = confidence,
                sampleCount = windowValues.size,
                windowSeconds = windowDurationMillis / 1000.0,
                log = "RR Score: current=${"%.1f".format(currentRr)}, abs=${"%.2f".format(absoluteScore)}, conf=${"%.2f".format(confidence)}, score=${"%.2f".format(finalScore)}"
            )
        }

        val cleanedValues = removeRespiratoryRateOutliers(windowValues)

        if (cleanedValues.size < config.rrScoreMinSampleCount) {
            val rawScore = absoluteScore
            val finalScore = rawScore * confidence

            return RespiratoryRateArousalResult(
                currentRrBpm = currentRr,
                baselineRrBpm = null,
                recentRrBpm = null,
                riseBpm = null,
                absoluteScore = absoluteScore,
                riseScore = null,
                rawScore = rawScore,
                score = finalScore,
                confidence = confidence,
                sampleCount = cleanedValues.size,
                windowSeconds = windowDurationMillis / 1000.0,
                log = "RR Score: current=${"%.1f".format(currentRr)}, abs=${"%.2f".format(absoluteScore)}, conf=${"%.2f".format(confidence)}, score=${"%.2f".format(finalScore)}"
            )
        }

        val splitSize = (cleanedValues.size / 3).coerceAtLeast(1)

        val baselinePart = cleanedValues.take(splitSize)
        val recentPart = cleanedValues.takeLast(splitSize)

        val baselineRr = baselinePart.map { it.second }.average()
        val recentRr = recentPart.map { it.second }.average()

        val riseBpm = recentRr - baselineRr
        val riseScore = scoreRespiratoryRateRise(riseBpm)

        val rawScore =
            (
                    absoluteScore * config.rrAbsoluteScoreWeight +
                            riseScore * config.rrRiseScoreWeight
                    )
                .coerceIn(0.0, 1.0)

        val finalScore =
            (rawScore * confidence).coerceIn(0.0, 1.0)

        val windowSeconds =
            (cleanedValues.last().first - cleanedValues.first().first) / 1000.0

        return RespiratoryRateArousalResult(
            currentRrBpm = currentRr,
            baselineRrBpm = baselineRr,
            recentRrBpm = recentRr,
            riseBpm = riseBpm,
            absoluteScore = absoluteScore,
            riseScore = riseScore,
            rawScore = rawScore,
            score = finalScore,
            confidence = confidence,
            sampleCount = cleanedValues.size,
            windowSeconds = windowSeconds,
            log = "RR Score: current=${"%.1f".format(currentRr)}, " +
                    "base=${"%.1f".format(baselineRr)}, " +
                    "recent=${"%.1f".format(recentRr)}, " +
                    "rise=${"%.1f".format(riseBpm)}, " +
                    "abs=${"%.2f".format(absoluteScore)}, " +
                    "riseScore=${"%.2f".format(riseScore)}, " +
                    "raw=${"%.2f".format(rawScore)}, " +
                    "conf=${"%.2f".format(confidence)}, " +
                    "score=${"%.2f".format(finalScore)}"
        )
    }
    private fun removeRespiratoryRateOutliers(
        values: List<Pair<Long, Double>>
    ): List<Pair<Long, Double>> {
        if (values.size < 3) {
            return values
        }

        val sortedRr = values.map { it.second }.sorted()
        val median = sortedRr[sortedRr.size / 2]

        val filtered = values.filter { (_, rrBpm) ->
            abs(rrBpm - median) <= config.rrScoreOutlierToleranceBpm
        }

        return filtered.ifEmpty { values }
    }

    private fun scoreRespiratoryRateAbsolute(
        rrBpm: Double
    ): Double {
        if (config.rrScoreHighBpm <= config.rrScoreLowBpm) {
            return 0.0
        }

        return ((rrBpm - config.rrScoreLowBpm) /
                (config.rrScoreHighBpm - config.rrScoreLowBpm))
            .coerceIn(0.0, 1.0)
    }

    private fun scoreRespiratoryRateRise(
        riseBpm: Double
    ): Double {
        if (riseBpm <= 0.0) {
            return 0.0
        }

        if (config.rrRiseThresholdBpm <= 0.0) {
            return 0.0
        }

        return (riseBpm / config.rrRiseThresholdBpm)
            .coerceIn(0.0, 1.0)
    }

    /********************* //RR Arousal Score ********************/

    /********************* RRV from RR intervals ********************/

    /**
     * RR interval 리스트를 이용해 RRV RMSSD를 계산한다.
     *
     * RR fusion source에 맞춰 IMU/PPG interval 중 더 적절한 쪽을 선택하고,
     * 호흡 interval의 연속 차이를 이용해 RMSSD를 산출한다.
     */
    private fun calculateRrvRmssd(
        ppg: PpgRespirationResult?,
        imu: ImuRespirationResult?,
        rrFusion: RrFusionResult
    ): RrvResult? {
        val imuRrv = buildRrvResultFromIntervals(
            source = RrvSource.IMU,
            intervalsSec = imu?.intervalsSec,
            respirationQuality = imu?.qualityScore
        )

        val ppgRrv = buildRrvResultFromIntervals(
            source = RrvSource.PPG,
            intervalsSec = ppg?.intervalsSec,
            respirationQuality = ppg?.qualityScore
        )

        return when (rrFusion.source) {
            RrFusionSource.BOTH_WEIGHTED,
            RrFusionSource.IMU_ONLY,
            RrFusionSource.IMU_PREFERRED_DISAGREE -> {
                imuRrv ?: ppgRrv
            }

            RrFusionSource.PPG_ONLY,
            RrFusionSource.PPG_PREFERRED_DISAGREE -> {
                ppgRrv ?: imuRrv
            }

            RrFusionSource.NONE -> {
                chooseBetterRrvResult(imuRrv, ppgRrv)
            }
        }
    }

    /**
     * 선택된 호흡 interval 리스트 하나를 RRV 결과로 변환한다.
     *
     * interval 품질, 최소 개수, outlier 제거를 거친 뒤
     * RMSSD, score, qualityScore를 계산한다.
     */
    private fun buildRrvResultFromIntervals(
        source: RrvSource,
        intervalsSec: List<Double>?,
        respirationQuality: Double?
    ): RrvResult? {
        if (intervalsSec == null) return null
        if (respirationQuality == null) return null

        if (respirationQuality < config.rrvMinUsableQuality) {
            return null
        }

        val cleanedIntervals = removeRrvIntervalOutliers(intervalsSec)

        if (cleanedIntervals.size < config.rrvMinIntervalCount) {
            return null
        }

        val successiveDiffs = mutableListOf<Double>()

        for (i in 1 until cleanedIntervals.size) {
            val diff = cleanedIntervals[i] - cleanedIntervals[i - 1]
            successiveDiffs.add(diff)
        }

        if (successiveDiffs.isEmpty()) {
            return null
        }

        var sumSquaredDiff = 0.0

        for (diff in successiveDiffs) {
            sumSquaredDiff += diff * diff
        }

        val rmssdSec = sqrt(sumSquaredDiff / successiveDiffs.size)
        val rmssdMs = rmssdSec * 1000.0
        val meanIntervalSec = cleanedIntervals.average()

        val score = scoreRrvRmssd(rmssdSec)

        val intervalCountScore =
            (cleanedIntervals.size / 8.0).coerceIn(0.0, 1.0)

        val qualityScore =
            (respirationQuality * 0.7 + intervalCountScore * 0.3)
                .coerceIn(0.0, 1.0)

        return RrvResult(
            rmssdSec = rmssdSec,
            rmssdMs = rmssdMs,
            source = source,
            intervalCount = cleanedIntervals.size,
            meanIntervalSec = meanIntervalSec,
            score = score,
            qualityScore = qualityScore,
            log = "RRV ${source.name}: rmssd=${"%.3f".format(rmssdSec)}s, intervals=${cleanedIntervals.size}"
        )
    }

    /**
     * RRV 계산 전 호흡 interval outlier를 제거한다.
     *
     * 갑자기 잘못 잡힌 peak interval이 RMSSD를 과도하게 키우지 않도록
     * 중앙값 기준 허용 범위 밖의 값을 제외한다.
     */
    private fun removeRrvIntervalOutliers(
        intervals: List<Double>
    ): List<Double> {
        if (intervals.size < 3) {
            return intervals
        }

        val sorted = intervals.sorted()
        val median = sorted[sorted.size / 2]

        if (median <= 0.0) {
            return intervals
        }

        val filtered = intervals.filter { interval ->
            abs(interval - median) / median <= config.rrvIntervalOutlierTolerance
        }

        return filtered.ifEmpty { intervals }
    }

    /**
     * RRV RMSSD 값을 0~1 각성 보조 점수로 정규화한다.
     *
     * 현재는 임시 threshold 기반이며, 추후 개인 deep sleep baseline 대비 방식으로 바꿀 수 있다.
     */
    private fun scoreRrvRmssd(
        rmssdSec: Double
    ): Double {
        if (config.rrvRmssdScoreThresholdSec <= 0.0) {
            return 0.0
        }

        return (rmssdSec / config.rrvRmssdScoreThresholdSec)
            .coerceIn(0.0, 1.0)
    }

    /**
     * 두 RRV 후보 중 qualityScore가 높은 결과를 선택한다.
     */
    private fun chooseBetterRrvResult(
        a: RrvResult?,
        b: RrvResult?
    ): RrvResult? {
        if (a == null) return b
        if (b == null) return a

        return if (a.qualityScore >= b.qualityScore) a else b
    }

    /**
     * 여기서 중요한 점은 RRV는 rrFinal = 14.8 bpm 같은 최종 호흡수 하나로 계산하는 게 아니라, 4.1초, 4.4초, 3.9초... 같은 호흡 interval들의 흔들림으로 계산한다는 거야.
     *
     * 지금은 baseline이 없으니까 rrvScore는 임시로 RMSSD 크기를 기준으로 정규화했어. 나중에 N3/deep sleep baseline을 만들면 이 부분은 이렇게 바꾸면 돼.
     */
    /********************* //RRV from RR intervals ********************/

    /********************* HR Arousal ********************/

    /**
     * HR 증가율 기반 각성 지표를 계산한다.
     *
     * 최근 HR window를 초반/후반 구간으로 나누어 평균 BPM 차이를 구하고,
     * 심박 상승량을 score로 변환한다.
     */
    fun calculateHeartRateArousal(): HeartRateArousalResult? {
        if (heartRateBuffer.size < config.hrMinSampleCount) {
            return null
        }

        val latestTime = heartRateBuffer.last().first

        val windowValues = heartRateBuffer
            .filter { (timestamp, bpm) ->
                latestTime - timestamp <= config.hrGradientWindowMillis &&
                        bpm in config.hrMinReasonableBpm..config.hrMaxReasonableBpm
            }

        if (windowValues.size < config.hrMinSampleCount) {
            return null
        }

        val windowDurationMillis =
            windowValues.last().first - windowValues.first().first

        if (windowDurationMillis < config.hrGradientMinWindowMillis) {
            return null
        }

        val cleanedValues = removeHeartRateOutliers(windowValues)

        if (cleanedValues.size < config.hrMinSampleCount) {
            return null
        }

        val splitSize = (cleanedValues.size / 3).coerceAtLeast(1)

        val baselinePart = cleanedValues.take(splitSize)
        val recentPart = cleanedValues.takeLast(splitSize)

        val baselineBpm = baselinePart.map { it.second }.average()
        val recentBpm = recentPart.map { it.second }.average()

        val gradientBpm = recentBpm - baselineBpm

        val windowMinutes =
            (cleanedValues.last().first - cleanedValues.first().first) / 60000.0

        if (windowMinutes <= 0.0) {
            return null
        }

        val gradientBpmPerMinute = gradientBpm / windowMinutes

        val score = scoreHeartRateGradient(gradientBpm)

        val currentBpm = cleanedValues.last().second

        return HeartRateArousalResult(
            currentBpm = currentBpm,
            baselineBpm = baselineBpm,
            recentBpm = recentBpm,
            gradientBpm = gradientBpm,
            gradientBpmPerMinute = gradientBpmPerMinute,
            sampleCount = cleanedValues.size,
            windowSeconds = windowMinutes * 60.0,
            score = score,
            isIncreasing = gradientBpm > 0.0,
            log = "HR: current=$currentBpm, base=${"%.1f".format(baselineBpm)}, recent=${"%.1f".format(recentBpm)}, gradient=${"%.1f".format(gradientBpm)}"
        )
    }

    /**
     * HR 증가량만 필요한 경우를 위한 helper.
     *
     * calculateHeartRateArousal()의 상세 결과 중 gradientBpm만 반환한다.
     */
    private fun calculateHrGradient(): Double? {
        return calculateHeartRateArousal()?.gradientBpm
    }

    /**
     * HR buffer에서 비정상적으로 튄 BPM 값을 제거한다.
     *
     * 중앙값 기준 허용 bpm 범위를 벗어난 값은 센서 노이즈나 peak 오검출로 보고 제외한다.
     */
    private fun removeHeartRateOutliers(
        values: List<Pair<Long, Int>>
    ): List<Pair<Long, Int>> {
        if (values.size < 3) {
            return values
        }

        val bpmValues = values.map { it.second }.sorted()
        val median = bpmValues[bpmValues.size / 2]

        val filtered = values.filter { (_, bpm) ->
            abs(bpm - median) <= config.hrOutlierToleranceBpm
        }

        return filtered.ifEmpty { values }
    }

    /**
     * HR 상승량을 0~1 각성 점수로 변환한다.
     *
     * 상승량이 threshold 이상이면 HR 관점에서는 강한 각성 후보로 본다.
     */
    private fun scoreHeartRateGradient(
        gradientBpm: Double
    ): Double {
        if (gradientBpm <= 0.0) {
            return 0.0
        }

        if (config.hrGradientThreshold <= 0.0) {
            return 0.0
        }

        return (gradientBpm / config.hrGradientThreshold)
            .coerceIn(0.0, 1.0)
    }

    /********************* //HR Arousal ********************/

    /********************* HRV from PPG IBI ********************/

    /**
     * DataProcessor에서 넘어온 HeartRateEstimate의 IBI들을 HRV buffer에 저장한다.
     *
     * rolling buffer 재계산 때문에 같은 IBI가 반복 전달될 수 있으므로
     * endSampleIndex를 이용해 이미 저장한 interval은 건너뛴다.
     */
    private fun appendHeartRateEstimateToHrvBuffer(
        estimate: HeartRateEstimate
    ) {
        if (estimate.qualityScore < config.hrvMinEstimateQuality) {
            return
        }

        val sortedIntervals = estimate.ibiIntervals
            .sortedBy { it.endSampleIndex }

        for (ibi in sortedIntervals) {
            if (ibi.endSampleIndex <= lastAcceptedHrvIbiEndSampleIndex) {
                continue
            }

            if (ibi.intervalSec !in 0.333..1.5) {
                continue
            }

            hrvIbiBuffer.addLast(ibi)
            lastAcceptedHrvIbiEndSampleIndex = ibi.endSampleIndex
        }

        trimHrvIbiBuffer()
    }

    /**
     * HRV IBI rolling buffer에서 오래된 interval을 제거한다.
     *
     * RMSSD 계산용 window 길이 안의 IBI만 유지한다.
     */
    private fun trimHrvIbiBuffer() {
        if (hrvIbiBuffer.isEmpty()) return

        val newestSampleIndex = hrvIbiBuffer.last().endSampleIndex
        val windowSamples =
            (config.sampleRateHz * config.hrvWindowSeconds).toLong()

        val minSampleIndex = newestSampleIndex - windowSamples

        while (hrvIbiBuffer.isNotEmpty()) {
            if (hrvIbiBuffer.first().endSampleIndex >= minSampleIndex) {
                break
            }

            hrvIbiBuffer.removeFirst()
        }
    }

    /**
     * IBI 기반 시간 영역 HRV RMSSD를 계산한다.
     *
     * PPG peak-to-peak interval에서 얻은 IBI 리스트의 연속 차이를 이용해
     * RMSSD(ms)를 구하고 HRV score와 품질 점수를 함께 반환한다.
     */
    fun calculateHeartRateVariability(): HeartRateVariabilityResult? {
        if (hrvIbiBuffer.size < config.hrvMinIbiCount) {
            return null
        }

        val cleanedIbis = removeHrvIbiOutliers(hrvIbiBuffer.toList())

        if (cleanedIbis.size < config.hrvMinIbiCount) {
            return null
        }

        val intervalsSec = cleanedIbis.map { it.intervalSec }

        val successiveDiffs = mutableListOf<Double>()

        for (i in 1 until intervalsSec.size) {
            val diff = intervalsSec[i] - intervalsSec[i - 1]
            successiveDiffs.add(diff)
        }

        if (successiveDiffs.isEmpty()) {
            return null
        }

        var sumSquaredDiff = 0.0

        for (diff in successiveDiffs) {
            sumSquaredDiff += diff * diff
        }

        val rmssdSec = sqrt(sumSquaredDiff / successiveDiffs.size)
        val rmssdMs = rmssdSec * 1000.0
        val meanIbiSec = intervalsSec.average()

        val score = scoreHrvRmssd(rmssdMs)

        val countScore =
            (cleanedIbis.size / 20.0).coerceIn(0.0, 1.0)

        val regularityScore =
            calculateHrvRegularityScore(intervalsSec)

        val qualityScore =
            (countScore * 0.45 + regularityScore * 0.55)
                .coerceIn(0.0, 1.0)

        return HeartRateVariabilityResult(
            rmssdSec = rmssdSec,
            rmssdMs = rmssdMs,
            ibiCount = cleanedIbis.size,
            meanIbiSec = meanIbiSec,
            score = score,
            qualityScore = qualityScore,
            log = "HRV: rmssd=${"%.1f".format(rmssdMs)}ms, ibi=${cleanedIbis.size}, q=${"%.2f".format(qualityScore)}"
        )
    }

    /**
     * HRV 계산 전 IBI outlier를 제거한다.
     *
     * 중앙값 대비 너무 벗어난 IBI는 peak 오검출 가능성이 높으므로 제외한다.
     */
    private fun removeHrvIbiOutliers(
        ibis: List<IbiInterval>
    ): List<IbiInterval> {
        if (ibis.size < 3) {
            return ibis
        }

        val sorted = ibis.map { it.intervalSec }.sorted()
        val median = sorted[sorted.size / 2]

        if (median <= 0.0) {
            return ibis
        }

        val filtered = ibis.filter { ibi ->
            abs(ibi.intervalSec - median) / median <= config.hrvIbiOutlierTolerance
        }

        return filtered.ifEmpty { ibis }
    }

    /**
     * IBI 리스트의 규칙성을 0~1 품질 점수로 계산한다.
     *
     * 변동계수(CV)가 너무 크면 peak 검출이 불안정하거나 motion artifact가 섞였다고 본다.
     */
    private fun calculateHrvRegularityScore(
        intervalsSec: List<Double>
    ): Double {
        if (intervalsSec.size < 2) {
            return 0.0
        }

        val mean = intervalsSec.average()

        if (mean <= 0.0) {
            return 0.0
        }

        var sumSquaredDiff = 0.0

        for (interval in intervalsSec) {
            val diff = interval - mean
            sumSquaredDiff += diff * diff
        }

        val std = sqrt(sumSquaredDiff / intervalsSec.size)
        val cv = std / mean

        return (1.0 - cv).coerceIn(0.0, 1.0)
    }

    /**
     * HRV RMSSD(ms)를 0~1 점수로 정규화한다.
     *
     * 현재는 임시 threshold 기반이며, 추후 개인 baseline 대비 점수로 바꾸는 것이 좋다.
     */
    private fun scoreHrvRmssd(
        rmssdMs: Double
    ): Double {
        if (config.hrvRmssdScoreThresholdMs <= 0.0) {
            return 0.0
        }

        return (rmssdMs / config.hrvRmssdScoreThresholdMs)
            .coerceIn(0.0, 1.0)
    }

    /********************* //HRV from PPG IBI ********************/

    /********************* HRV LF/HF from PPG IBI ********************/

    /**
     * HRV 주파수 영역 지표인 LF/HF ratio를 계산한다.
     *
     * IBI를 최근 window로 자르고, 등간격 시계열로 resampling한 뒤
     * 평균 제거, Hamming window, FFT power spectrum을 거쳐 LF/HF power를 구한다.
     */
    fun calculateHrvFrequencyDomain(): HrvFrequencyResult? {
        if (hrvIbiBuffer.size < config.hrvSpectralMinIbiCount) {
            return null
        }

        val cleanedIbis = removeHrvIbiOutliers(hrvIbiBuffer.toList())

        if (cleanedIbis.size < config.hrvSpectralMinIbiCount) {
            return null
        }

        val recentIbis = takeRecentIbisForFrequencyAnalysis(cleanedIbis)

        if (recentIbis.size < config.hrvSpectralMinIbiCount) {
            return null
        }

        val resampled = resampleIbiToEvenTimeSeries(
            ibis = recentIbis,
            resampleRateHz = config.hrvResampleRateHz
        ) ?: return null

        if (resampled.size < 32) {
            return null
        }

        val detrended = removeMean(resampled)
        val windowed = applyHammingWindow(detrended)

        val powerSpectrum = calculatePowerSpectrumFft(
            signal = windowed,
            sampleRateHz = config.hrvResampleRateHz
        )

        val lfPower = integrateBandPower(
            powerSpectrum = powerSpectrum,
            lowHz = config.hrvLfLowHz,
            highHz = config.hrvLfHighHz
        )

        val hfPower = integrateBandPower(
            powerSpectrum = powerSpectrum,
            lowHz = config.hrvHfLowHz,
            highHz = config.hrvHfHighHz
        )

        if (lfPower <= 0.0 && hfPower <= 0.0) {
            return null
        }

        val lfHfRatio =
            if (hfPower <= 1e-12) {
                Double.POSITIVE_INFINITY
            } else {
                lfPower / hfPower
            }

        val score = scoreHrvLfHf(lfHfRatio)

        val countScore =
            (recentIbis.size / 40.0).coerceIn(0.0, 1.0)

        val finiteRatioScore =
            if (lfHfRatio.isFinite()) 1.0 else 0.5

        val qualityScore =
            (countScore * 0.7 + finiteRatioScore * 0.3)
                .coerceIn(0.0, 1.0)

        return HrvFrequencyResult(
            lfPower = lfPower,
            hfPower = hfPower,
            lfHfRatio = lfHfRatio,
            resampledCount = resampled.size,
            ibiCount = recentIbis.size,
            score = score,
            qualityScore = qualityScore,
            log = "HRV LF/HF: lf=${"%.6f".format(lfPower)}, " +
                    "hf=${"%.6f".format(hfPower)}, " +
                    "ratio=${formatDoubleSafe(lfHfRatio)}, " +
                    "ibi=${recentIbis.size}, q=${"%.2f".format(qualityScore)}"
        )
    }

    /**
     * LF/HF 분석에 사용할 최근 IBI만 선택한다.
     *
     * LF 대역 분석은 긴 window가 필요하므로 RMSSD window와 별도로
     * hrvFrequencyWindowSeconds 범위를 사용한다.
     */
    private fun takeRecentIbisForFrequencyAnalysis(
        ibis: List<IbiInterval>
    ): List<IbiInterval> {
        if (ibis.isEmpty()) return emptyList()

        val newestSampleIndex = ibis.last().endSampleIndex
        val windowSamples =
            (config.sampleRateHz * config.hrvFrequencyWindowSeconds).toLong()

        val minSampleIndex = newestSampleIndex - windowSamples

        return ibis.filter { it.endSampleIndex >= minSampleIndex }
    }

    /**
     * 불규칙한 IBI 시계열을 등간격 시계열로 변환한다.
     *
     * FFT는 일정한 sampling interval이 필요하므로
     * IBI 발생 시점 사이를 선형 보간해 4Hz 같은 고정 rate로 resampling한다.
     */
    private fun resampleIbiToEvenTimeSeries(
        ibis: List<IbiInterval>,
        resampleRateHz: Double
    ): DoubleArray? {
        if (ibis.size < 2) return null
        if (resampleRateHz <= 0.0) return null

        val points = ibis
            .sortedBy { it.endSampleIndex }
            .map { ibi ->
                val timeSec = ibi.endSampleIndex / config.sampleRateHz
                timeSec to ibi.intervalSec
            }

        val startTime = points.first().first
        val endTime = points.last().first

        if (endTime <= startTime) return null

        val dt = 1.0 / resampleRateHz
        val count = ((endTime - startTime) / dt).toInt() + 1

        if (count < 16) return null

        val result = DoubleArray(count)

        var segmentIndex = 0

        for (i in 0 until count) {
            val t = startTime + i * dt

            while (
                segmentIndex < points.size - 2 &&
                points[segmentIndex + 1].first < t
            ) {
                segmentIndex++
            }

            val p0 = points[segmentIndex]
            val p1 = points[(segmentIndex + 1).coerceAtMost(points.lastIndex)]

            val t0 = p0.first
            val v0 = p0.second
            val t1 = p1.first
            val v1 = p1.second

            result[i] =
                if (t1 <= t0) {
                    v0
                } else {
                    val alpha = ((t - t0) / (t1 - t0)).coerceIn(0.0, 1.0)
                    v0 + alpha * (v1 - v0)
                }
        }

        return result
    }

    /**
     * 시계열의 평균값을 제거해 DC 성분을 줄인다.
     *
     * FFT에서 0Hz 근처 성분이 LF/HF 계산에 영향을 주지 않도록 전처리한다.
     */
    private fun removeMean(
        values: DoubleArray
    ): DoubleArray {
        if (values.isEmpty()) return values

        val mean = values.average()

        return DoubleArray(values.size) { i ->
            values[i] - mean
        }
    }

    /**
     * Hamming window를 적용해 FFT spectral leakage를 줄인다.
     */
    private fun applyHammingWindow(
        values: DoubleArray
    ): DoubleArray {
        val n = values.size

        if (n <= 1) return values.copyOf()

        return DoubleArray(n) { i ->
            val w = 0.54 - 0.46 * cos(2.0 * PI * i / (n - 1))
            values[i] * w
        }
    }

    /**
     * FFT power spectrum의 한 frequency bin.
     *
     * frequencyHz는 bin의 중심 주파수, power는 해당 주파수 성분의 에너지 크기다.
     */
    data class PowerBin(
        val frequencyHz: Double,
        val power: Double
    )

    /**
     * 등간격 시계열의 FFT power spectrum을 계산한다.
     *
     * 입력 길이를 next power-of-two로 zero padding한 뒤 FFT를 수행하고,
     * Nyquist 주파수까지의 power bin 목록을 반환한다.
     */
    fun calculatePowerSpectrumFft(
        signal: DoubleArray,
        sampleRateHz: Double
    ): List<PowerBin> {
        if (signal.size < 2) {
            return emptyList()
        }

        val fftSize = nextPowerOfTwo(signal.size)

        val complexInput = Array(fftSize) { index ->
            if (index < signal.size) {
                Complex(signal[index], 0.0)
            } else {
                Complex(0.0, 0.0)
            }
        }

        val fftResult = fft(complexInput)

        val bins = mutableListOf<PowerBin>()

        val maxK = fftSize / 2

        for (k in 1..maxK) {
            val frequency = k * sampleRateHz / fftSize

            // windowing/zero-padding 후 비교용 PSD.
            // 절대값보다 LF/HF 비율이 중요하므로 단순 정규화로 충분.
            val power = fftResult[k].power() / fftSize

            bins.add(
                PowerBin(
                    frequencyHz = frequency,
                    power = power
                )
            )
        }

        return bins
    }

    /**
     * power spectrum에서 특정 주파수 대역의 power를 합산한다.
     *
     * LF/HF 계산에서 LF band와 HF band의 총 power를 구할 때 사용한다.
     */
    private fun integrateBandPower(
        powerSpectrum: List<PowerBin>,
        lowHz: Double,
        highHz: Double
    ): Double {
        if (powerSpectrum.isEmpty()) return 0.0

        return powerSpectrum
            .filter { it.frequencyHz >= lowHz && it.frequencyHz < highHz }
            .sumOf { it.power }
    }

    /**
     * LF/HF ratio를 0~1 HRV 각성 점수로 정규화한다.
     *
     * ratio가 높을수록 교감신경 우세/각성 가능성이 높다고 보는 임시 기준이다.
     */
    private fun scoreHrvLfHf(
        lfHfRatio: Double
    ): Double {
        if (!lfHfRatio.isFinite()) {
            return 1.0
        }

        if (config.hrvLfHfScoreThreshold <= 0.0) {
            return 0.0
        }

        return (lfHfRatio / config.hrvLfHfScoreThreshold)
            .coerceIn(0.0, 1.0)
    }

    /**
     * 로그 출력용 Double formatter.
     *
     * LF/HF ratio가 무한대인 경우에도 로그가 깨지지 않도록 INF 문자열로 변환한다.
     */
    private fun formatDoubleSafe(
        value: Double
    ): String {
        return if (value.isFinite()) {
            "%.3f".format(value)
        } else {
            "INF"
        }
    }

    /********************* //HRV LF/HF from PPG IBI ********************/

    /********************* Skin Temperature Arousal ********************/

    /**
     * 피부온도 변화 기반 각성 보조 지표를 계산한다.
     *
     * 최근 window의 초반 평균 온도와 후반 평균 온도를 비교하여
     * warming/cooling/stable 추세, gradient, score, quality를 산출한다.
     */
    fun calculateSkinTemperatureArousal(): SkinTemperatureResult? {
        if (temperatureBuffer.size < config.skinTempMinSampleCount) {
            return null
        }

        val latestTime = temperatureBuffer.last().first

        val windowValues = temperatureBuffer
            .filter { (timestamp, celsius) ->
                latestTime - timestamp <= config.skinTempGradientWindowMillis &&
                        celsius.isFinite() &&
                        celsius in 0.0..60.0
            }

        if (windowValues.size < config.skinTempMinSampleCount) {
            return null
        }

        val windowDurationMillis =
            windowValues.last().first - windowValues.first().first

        if (windowDurationMillis < config.skinTempGradientMinWindowMillis) {
            return null
        }

        val cleanedValues = removeSkinTemperatureOutliers(windowValues)

        if (cleanedValues.size < config.skinTempMinSampleCount) {
            return null
        }

        val jumpFilteredValues = removeSkinTemperatureSingleJumps(cleanedValues)

        if (jumpFilteredValues.size < config.skinTempMinSampleCount) {
            return null
        }

        val splitSize = (jumpFilteredValues.size / 3).coerceAtLeast(1)

        val baselinePart = jumpFilteredValues.take(splitSize)
        val recentPart = jumpFilteredValues.takeLast(splitSize)

        val baselineCelsius = baselinePart.map { it.second }.average()
        val recentCelsius = recentPart.map { it.second }.average()

        val gradientCelsius = recentCelsius - baselineCelsius

        val windowMinutes =
            (jumpFilteredValues.last().first - jumpFilteredValues.first().first) / 60000.0

        if (windowMinutes <= 0.0) {
            return null
        }

        val gradientCelsiusPerMinute = gradientCelsius / windowMinutes

        val trend = when {
            gradientCelsius >= config.skinTempRiseThresholdCelsius ->
                SkinTemperatureTrend.WARMING

            gradientCelsius <= -config.skinTempRiseThresholdCelsius ->
                SkinTemperatureTrend.COOLING

            else ->
                SkinTemperatureTrend.STABLE
        }

        val score = scoreSkinTemperatureGradient(gradientCelsius)

        val durationScore =
            (windowDurationMillis.toDouble() / config.skinTempGradientWindowMillis)
                .coerceIn(0.0, 1.0)

        val sampleScore =
            (jumpFilteredValues.size / 20.0).coerceIn(0.0, 1.0)

        val stabilityScore =
            calculateSkinTemperatureStabilityScore(jumpFilteredValues)

        val qualityScore =
            (durationScore * 0.35 +
                    sampleScore * 0.25 +
                    stabilityScore * 0.40)
                .coerceIn(0.0, 1.0)

        val currentCelsius = jumpFilteredValues.last().second

        return SkinTemperatureResult(
            currentCelsius = currentCelsius,
            baselineCelsius = baselineCelsius,
            recentCelsius = recentCelsius,
            gradientCelsius = gradientCelsius,
            gradientCelsiusPerMinute = gradientCelsiusPerMinute,
            sampleCount = jumpFilteredValues.size,
            windowSeconds = windowMinutes * 60.0,
            trend = trend,
            score = score,
            qualityScore = qualityScore,
            log = "SkinTemp: current=${"%.2f".format(currentCelsius)}℃, " +
                    "base=${"%.2f".format(baselineCelsius)}℃, " +
                    "recent=${"%.2f".format(recentCelsius)}℃, " +
                    "grad=${"%.3f".format(gradientCelsius)}℃, " +
                    "trend=$trend, q=${"%.2f".format(qualityScore)}"
        )
    }

    /**
     * 피부온도 window에서 주변값과 크게 다른 outlier를 제거한다.
     *
     * 센서 접촉 불량이나 순간적인 ADC 튐이 gradient 계산에 영향을 주지 않도록 한다.
     */
    private fun removeSkinTemperatureOutliers(
        values: List<Pair<Long, Double>>
    ): List<Pair<Long, Double>> {
        if (values.size < 3) {
            return values
        }

        val sortedTemps = values.map { it.second }.sorted()
        val median = sortedTemps[sortedTemps.size / 2]

        val filtered = values.filter { (_, celsius) ->
            abs(celsius - median) <= config.skinTempOutlierToleranceCelsius
        }

        return filtered.ifEmpty { values }
    }

    /**
     * 연속 샘플 사이의 비현실적인 단발성 온도 급변을 제거한다.
     *
     * 실제 피부온도는 급격히 변하기 어렵기 때문에 큰 jump는 노이즈로 판단한다.
     */
    private fun removeSkinTemperatureSingleJumps(
        values: List<Pair<Long, Double>>
    ): List<Pair<Long, Double>> {
        if (values.size < 3) {
            return values
        }

        val result = mutableListOf<Pair<Long, Double>>()
        result.add(values.first())

        for (i in 1 until values.size) {
            val prev = result.last().second
            val current = values[i].second

            val jump = abs(current - prev)

            if (jump <= config.skinTempMaxSingleJumpCelsius) {
                result.add(values[i])
            }
        }

        return result.ifEmpty { values }
    }

    /**
     * 피부온도 window의 안정성을 0~1 quality 점수로 계산한다.
     *
     * 표준편차가 낮을수록 센서 접촉과 측정 환경이 안정적이라고 본다.
     */
    private fun calculateSkinTemperatureStabilityScore(
        values: List<Pair<Long, Double>>
    ): Double {
        if (values.size < 2) {
            return 0.0
        }

        val temps = values.map { it.second }
        val mean = temps.average()

        var sumSquaredDiff = 0.0

        for (temp in temps) {
            val diff = temp - mean
            sumSquaredDiff += diff * diff
        }

        val std = sqrt(sumSquaredDiff / temps.size)

        // 0.3℃ 이내로 안정적이면 높은 품질.
        return (1.0 - std / 0.3).coerceIn(0.0, 1.0)
    }

    /**
     * 피부온도 상승량을 0~1 각성 보조 점수로 변환한다.
     *
     * 하강은 각성 신호로 보지 않고, 상승량이 threshold에 가까울수록 점수를 높인다.
     */
    private fun scoreSkinTemperatureGradient(
        gradientCelsius: Double
    ): Double {
        if (gradientCelsius <= 0.0) {
            return 0.0
        }

        if (config.skinTempRiseThresholdCelsius <= 0.0) {
            return 0.0
        }

        return (gradientCelsius / config.skinTempRiseThresholdCelsius)
            .coerceIn(0.0, 1.0)
    }

    /********************* //Skin Temperature Arousal ********************/
}

/************************* FFT ************************/
private data class Complex(
    val re: Double,
    val im: Double
) {
    operator fun plus(other: Complex): Complex {
        return Complex(
            re = re + other.re,
            im = im + other.im
        )
    }

    operator fun minus(other: Complex): Complex {
        return Complex(
            re = re - other.re,
            im = im - other.im
        )
    }

    operator fun times(other: Complex): Complex {
        return Complex(
            re = re * other.re - im * other.im,
            im = re * other.im + im * other.re
        )
    }

    fun power(): Double {
        return re * re + im * im
    }
}

data class PowerBin(
    val frequencyHz: Double,
    val power: Double
)

private fun fft(input: Array<Complex>): Array<Complex> {
    val n = input.size

    if (n == 1) {
        return arrayOf(input[0])
    }

    require(n and (n - 1) == 0) {
        "FFT input size must be power of two"
    }

    val even = Array(n / 2) { i ->
        input[i * 2]
    }

    val odd = Array(n / 2) { i ->
        input[i * 2 + 1]
    }

    val evenFft = fft(even)
    val oddFft = fft(odd)

    val result = Array(n) {
        Complex(0.0, 0.0)
    }

    for (k in 0 until n / 2) {
        val angle = -2.0 * Math.PI * k / n

        val twiddle = Complex(
            re = kotlin.math.cos(angle),
            im = kotlin.math.sin(angle)
        )

        val t = twiddle * oddFft[k]

        result[k] = evenFft[k] + t
        result[k + n / 2] = evenFft[k] - t
    }

    return result
}

private fun nextPowerOfTwo(value: Int): Int {
    var n = 1

    while (n < value) {
        n = n shl 1
    }

    return n
}

/************************* //FFT ************************/