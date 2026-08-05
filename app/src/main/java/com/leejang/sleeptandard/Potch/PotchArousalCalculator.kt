package com.leejang.sleeptandard.Potch

import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow


/**
 * 각 지표의 현재 계산 가능 상태.
 *
 * VALID: 현재 유효한 결과가 계산됨
 * COLLECTING: 최소 window/sample/interval을 모으는 중
 * REJECTED: 데이터는 충분하지만 품질, 진폭, 이상치 필터 등의 이유로 계산 결과가 거부됨
 */
enum class MetricCalculationState {
    VALID,
    COLLECTING,
    REJECTED
}

data class MetricCalculationStatus(
    val state: MetricCalculationState = MetricCalculationState.COLLECTING,
    val message: String = "데이터 수집 중"
)

/**
 * 각성 점수와 신뢰도를 분리해서 전달하는 공통 구조.
 *
 * score는 관측된 생체 변화의 각성 정도이고 confidence는 그 점수를 믿을 수 있는 정도다.
 * 사용 불가능한 지표는 0점으로 대입하지 않고 usable=false로 제외한다.
 */
data class MetricEvidence(
    val score: Double? = null,              // 0~100
    val confidence: Double = 0.0,           // 0~1
    val coverage: Double = 0.0,             // 설계상 확보된 증거 비율 0~1
    val usable: Boolean = false,
    val baselineSource: String = "NONE",    // PERSONAL / FALLBACK / NONE
    val baselineCenter: Double? = null,
    val baselineSpread: Double? = null,
    val signedDistance: Double? = null,
    val normalizedDistance: Double? = null,
    val baselineScore: Double? = null,      // 0~100
    val trendScore: Double? = null,         // 0~100
    val signalQuality: Double = 0.0,
    val reasons: String? = null,
    val log: String? = null
)

/** 영역 단위로 결합된 각성 증거. */
data class DomainEvidence(
    val score: Double? = null,       // 0~100
    val confidence: Double = 0.0,    // 0~1
    val coverage: Double = 0.0,      // 0~1
    val usable: Boolean = false,
    val composition: String = "NONE"
)

// 계산 결과 상태. 공개 score 필드는 0~100 범위다.
data class ArousalState(
    // 1. Micro Movement
    val microMovementVariance: Double? = null,
    val microMovementScore: Double? = null,
    val microMovementRmsG: Double? = null,
    val microMovementLevel: MicroMovementLevel? = null,
    val isMacroMovementLike: Boolean = false,

    // 2. Respiratory Rate
    val rrFromPpg: Double? = null,
    val rrFromImu: Double? = null,
    val rrFinal: Double? = null,
    val rrScore: Double? = null,    // evidence score 0~100 (confidence와 분리)
    val rrRawScore: Double? = null, // 이전 threshold 기반 RR raw score, 디버깅 호환용
    val rrFusionSource: RrFusionSource = RrFusionSource.NONE,
    val rrFusionConfidence: Double = 0.0,
    val rrFusionLog: String? = null,
    val rrCalculationStatus: MetricCalculationStatus = MetricCalculationStatus(),

    // RR 디버깅/검증용: 현재 45초 분석창에서 검출된 peak와 사용 interval.
    // sample position은 현재 analysis segment 시작점을 0으로 하는 절대 sample index다.
    val rrAnalysisSegmentId: Long = 0L,
    val ppgRespPeakSamplePositions: List<Long> = emptyList(),
    val ppgRespIntervalsSec: List<Double> = emptyList(),
    val imuRespPeakSamplePositions: List<Long> = emptyList(),
    val imuRespIntervalsSec: List<Double> = emptyList(),

    // ExperimentScreen에서 실제 RR 전처리 파형과 peak 분류를 시각화하기 위한 snapshot.
    val ppgRespirationGraphData: PpgRespirationGraphData = PpgRespirationGraphData(),

    // 3. Respiratory Rate Variability
    val rrvRmssd: Double? = null,        // 최종 선택된 source의 seconds 기준 RMSSD
    val rrvRmssdMs: Double? = null,      // 로그/UI 확인용 ms
    val rrvScore: Double? = null,
    val rrvSource: RrvSource = RrvSource.NONE,
    val rrvQuality: Double = 0.0,
    val rrvIntervalCount: Int = 0,

    // PPG와 IMU RRV를 따로 기록하여 source별 정확도를 비교할 수 있게 한다.
    val rrvFromPpgRmssdSec: Double? = null,
    val rrvFromImuRmssdSec: Double? = null,
    val rrvPpgIntervalCount: Int = 0,
    val rrvImuIntervalCount: Int = 0,
    val rrvPpgQuality: Double = 0.0,
    val rrvImuQuality: Double = 0.0,
    val rrvCalculationStatus: MetricCalculationStatus = MetricCalculationStatus(),

    // 4. Heart Rate
    val hrBpm: Int? = null,
    val hrGradient: Double? = null,
    val hrScore: Double? = null,
    val hrCalculationStatus: MetricCalculationStatus = MetricCalculationStatus(),

    // 5. Heart Rate Variability
    val hrvRmssd: Double? = null,
    val hrvRmssdMs: Double? = null,
    val hrvLf: Double? = null,
    val hrvHf: Double? = null,
    val hrvLfHf: Double? = null,

    // HRV 점수의 설계 비율은 LF/HF 70% + RMSSD 30%다.
    // 사용할 수 없는 구성요소는 점수 분모에서 제외하고, coverage/confidence만 낮춘다.
    val hrvScore: Double? = null,
    val hrvQuality: Double = 0.0,
    val hrvIbiCount: Int = 0,

    // 시간영역(RMSSD)과 주파수영역(LF/HF)을 분리해 로그/안정점수에서 사용한다.
    val hrvRmssdScore: Double? = null,
    val hrvRmssdQuality: Double = 0.0,
    val hrvRmssdIbiCount: Int = 0,
    val hrvFrequencyScore: Double? = null,
    val hrvFrequencyQuality: Double = 0.0,
    val hrvFrequencyIbiCount: Int = 0,
    val hrvFrequencyUsable: Boolean = false,
    val hrvFrequencyStatus: MetricCalculationStatus = MetricCalculationStatus(),
    val hrvFrequencyRejectionReasons: String? = null,
    val hrvFrequencyObservedSeconds: Double = 0.0,
    val hrvFrequencyRawIbiCount: Int = 0,
    val hrvFrequencyCleanedIbiCount: Int = 0,
    val hrvFrequencyResampledCount: Int = 0,
    val hrvFrequencyPpgSignalQuality: Double? = null,
    val hrvFrequencyRespiratoryRateBpm: Double? = null,
    val hrvScoreComposition: String = "NONE",

    val hrvLog: String? = null,
    val hrvCalculationStatus: MetricCalculationStatus = MetricCalculationStatus(),

    // 6. Skin Temperature
    val skinTemperatureCelsius: Double? = null,
    val skinTemperatureGradient: Double? = null,
    val skinTemperatureScore: Double? = null,
    val skinTemperatureQuality: Double = 0.0,
    val skinTemperatureSampleCount: Int = 0,

    // 점수와 신뢰도를 분리한 지표별 evidence.
    val microEvidence: MetricEvidence = MetricEvidence(),
    val rrEvidence: MetricEvidence = MetricEvidence(),
    val rrvEvidence: MetricEvidence = MetricEvidence(),
    val hrEvidence: MetricEvidence = MetricEvidence(),
    val hrvEvidence: MetricEvidence = MetricEvidence(),
    val temperatureEvidence: MetricEvidence = MetricEvidence(),

    // 중복 신호를 줄이기 위해 먼저 결합한 영역별 evidence.
    val movementDomainEvidence: DomainEvidence = DomainEvidence(),
    val respiratoryDomainEvidence: DomainEvidence = DomainEvidence(),
    val cardiacDomainEvidence: DomainEvidence = DomainEvidence(),
    val temperatureDomainEvidence: DomainEvidence = DomainEvidence(),

    // Final
    val finalWakeScore: Double = 0.0,       // confidence로 감산하지 않은 0~100 점수
    val finalWakeConfidence: Double = 0.0,  // 0~100
    val finalWakeCoverage: Double = 0.0,    // 0~100
    val usedArousalDomainCount: Int = 0,

    // Tolerant persistence: 최근 window 안에서 통과한 초의 개수로 기상 후보를 판정한다.
    // wakeCandidateHoldSeconds는 기존 UI/로그 호환을 위해 현재 window의 통과 초와 동일하게 유지한다.
    val wakeCandidateHoldSeconds: Int = 0,
    val wakeCurrentConditionPassed: Boolean = false,
    val wakePersistenceWindowSeconds: Int = 30,
    val wakePersistenceRequiredPassSeconds: Int = 24,
    val wakePersistenceObservedSeconds: Int = 0,
    val wakePersistencePassedSeconds: Int = 0,
    val wakePersistenceFailedSeconds: Int = 0,
    val wakePersistencePassRatio: Double = 0.0,

    val wakeDecisionReason: String = "각성 증거 수집 중",
    val isWakeTimingCandidate: Boolean = false,
    val lastLog: String = "No arousal data yet"
)

/**
 * 개인 기준선 기반 evidence scoring 전용 설정.
 * ArousalConfig의 JVM 생성자 인자 수가 과도하게 커지는 것을 피하기 위해 별도 설정으로 분리한다.
 */
data class EvidenceScoringConfig(
    // Hill 함수: z^n / (k^n + z^n), dead zone 이후 이탈량을 사용한다.
    val evidenceDeadZoneZ: Double = 0.50,
    val evidenceHalfSaturationZ: Double = 2.0,
    val evidenceHillExponent: Double = 3.0,
    val fallbackBaselineConfidence: Double = 0.45,

    // 개인 MAD가 지나치게 작거나 0일 때 사용할 최소 scale.
    val rrEvidenceMinimumScaleBpm: Double = 0.50,
    val rrvEvidenceMinimumScaleSec: Double = 0.05,
    val hrEvidenceMinimumScaleBpm: Double = 2.0,
    val hrvRmssdEvidenceMinimumScaleSec: Double = 0.01,
    val hrvLfHfEvidenceMinimumScaleLogRatio: Double = 0.15,
    val temperatureEvidenceMinimumScaleCelsius: Double = 0.05,

    // 지표 내부 baseline/trend 증거 가중치.
    val rrBaselineEvidenceWeight: Double = 0.70,
    val rrTrendEvidenceWeight: Double = 0.30,
    val rrvBaselineEvidenceWeight: Double = 0.70,
    val rrvTrendEvidenceWeight: Double = 0.30,
    val hrBaselineEvidenceWeight: Double = 0.60,
    val hrTrendEvidenceWeight: Double = 0.40,
    val temperatureBaselineEvidenceWeight: Double = 0.70,
    val temperatureTrendEvidenceWeight: Double = 0.30,

    // HRV 내부 설계 비율. 사용 불가 구성요소는 점수 분모에서 제외하고 coverage/confidence를 낮춘다.
    val hrvFrequencyEvidenceWeight: Double = 0.70,
    val hrvRmssdEvidenceWeight: Double = 0.30,

    // 중복 신호를 줄이기 위한 영역/지표 가중치.
    val movementArousalDomainWeight: Double = 0.20,
    val respiratoryArousalDomainWeight: Double = 0.35,
    val cardiacArousalDomainWeight: Double = 0.35,
    val temperatureArousalDomainWeight: Double = 0.10,
    val rrArousalMetricWeight: Double = 0.65,
    val rrvArousalMetricWeight: Double = 0.35,
    val hrArousalMetricWeight: Double = 0.55,
    val hrvArousalMetricWeight: Double = 0.45,

    // 최종 후보는 점수, confidence, 사용 영역 조건을 최근 30초 중 24초 이상 만족해야 한다.
    val wakeCandidateMinConfidence: Double = 0.55,
    val wakePersistenceWindowSeconds: Int = 30,
    val wakePersistenceRequiredPassSeconds: Int = 24,
    val wakeCandidateMinimumDomainCount: Int = 2,

    // RRV 변화 추세용 값 history.
    val rrvTrendWindowMillis: Long = 3 * 60 * 1000L,
    val rrvTrendMinWindowMillis: Long = 60 * 1000L,
    val rrvTrendMinSampleCount: Int = 5
)

// 임계치 조절 클래스
data class ArousalConfig(
    val ppgSampleRateHz: Double = 128.0,
    val imuSampleRateHz: Double = 64.0,

    // Micro Movement
    var microLowCutHz: Double = 0.5,
    var microHighCutHz: Double = 5.0,
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

    // PPG RR 접촉/gap-aware 전처리.
    // 0 또는 접촉 임계값 미만 샘플을 호흡 파형으로 그대로 연결하지 않는다.
    val ppgRespContactMinValue: Double = 10_000.0,
    val ppgRespSaturationHighValue: Double = 65_000.0,

    // 짧은 gap은 선형 보간하고, 중간 gap은 BPF 연속성만 위해 보간하되
    // gap을 가로지르는 호흡 interval은 최종 RR에서 제외한다.
    val ppgRespShortGapMaxSeconds: Double = 0.20,
    val ppgRespMediumGapMaxSeconds: Double = 0.50,

    // gap 전후 local DC 수준 차이가 이 비율보다 크면 접촉 변화로 보고 보간하지 않는다.
    val ppgRespGapEdgeMaxRelativeDifference: Double = 0.15,

    // 보간 구간 및 주변에서는 인공 peak가 검출되지 않도록 제외한다.
    val ppgRespPeakExclusionMarginSeconds: Double = 0.20,

    // leading invalid 또는 긴 contact gap 뒤 첫 1초는 센서 LED/접촉 안정화 구간으로 제외한다.
    val ppgRespContactSettleSeconds: Double = 1.0,

    // 한 개의 큰 transient가 threshold와 진폭을 지배하지 않도록 robust percentile 사용.
    val ppgRespRobustLowPercentile: Double = 0.05,
    val ppgRespRobustHighPercentile: Double = 0.95,

    // IMU 기반 RR 계산용
    val imuRespWindowSeconds: Int = 45,
    val imuRespMinWindowSeconds: Int = 25,

    // BPF 후 IMU 호흡 파형의 최소 peak-to-peak 진폭.
    // 단위는 g.
    // 실제 착용 로그 보고 조정 필요.
    val imuRespMinPeakToPeakAmplitudeG: Double = 0.002,

    // interval 튄 값 제거 기준
    val imuRespIntervalOutlierTolerance: Double = 0.40,

    // RR peak 경로 안정화.
    // PPG는 Green positive, IMU는 positive를 primary로 유지하고
    // 일시적인 품질 차이만으로 source/polarity를 바꾸지 않는다.
    val rrPathFailuresBeforeFallback: Int = 5,
    val rrPathConfirmFrames: Int = 5,
    val rrPathRecoveryConfirmFrames: Int = 5,
    val rrPathPendingBpmTolerance: Double = 2.0,
    val rrPathMinHoldMillis: Long = 10 * 1000L,

    // RR Fusion
    // 두 센서가 1 bpm 이내로 일치할 때만 weighted fusion을 허용한다.
    val rrFusionAgreeDiffBpm: Double = 1.0,
    val rrFusionStrongDisagreeDiffBpm: Double = 6.0,

    // 기본적으로 PPG를 더 신뢰
    val rrFusionImuBaseWeight: Double = 0.3,
    val rrFusionPpgBaseWeight: Double = 0.7,

    // 한 센서를 단독 후보로 사용할 수 있는 최소 quality.
    val rrFusionMinUsableQuality: Double = 0.35,

    // IMU가 weighted fusion에 실제로 참여하기 위한 더 엄격한 quality 기준.
    // 이번 paced-breathing 로그에서는 IMU가 PPG보다 오차가 컸으므로 보수적으로 둔다.
    val rrFusionMinImuQualityForWeighting: Double = 0.70,

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

    // 신뢰 가능한 RR이 이 시간 동안 새로 들어오지 않으면 과거 RR history를 폐기한다.
    val rrFreshnessTimeoutMillis: Long = 30 * 1000L,

    // RRV
    // RR은 45초 창으로 빠르게 계산하지만 RRV는 최근 3분 interval을 별도로 누적한다.
    val rrvWindowSeconds: Int = 180,

    // RMSSD를 계산하기 위한 최소 유효 호흡 interval 수.
    val rrvMinIntervalCount: Int = 8,

    // interval 개수 품질 점수가 최대가 되는 권장 개수.
    val rrvPreferredIntervalCount: Int = 20,

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

    // 유효 HR이 이 시간 동안 새로 들어오지 않으면 과거 HR history를 폐기한다.
    val hrFreshnessTimeoutMillis: Long = 10 * 1000L,

    // HRV
    val hrvWindowSeconds: Int = 60,
    val hrvMinIbiCount: Int = 8,
    val hrvIbiOutlierTolerance: Double = 0.30,
    val hrvMinEstimateQuality: Double = 0.35,

    // 새 유효 IBI가 이 시간 동안 없으면 과거 HRV IBI buffer를 폐기한다.
    val hrvFreshnessTimeoutMillis: Long = 10 * 1000L,

    // 임시 score 기준. 나중에는 개인 baseline 기반으로 바꾸는 게 좋음.
    val hrvRmssdScoreThresholdMs: Double = 80.0,

    // HRV LF/HF
    /*
    hrvFrequencyWindowSeconds를 120초로 둔 이유는 LF 대역이 0.04Hz부터 시작해서 너무 짧은 창에서는 안정적으로 보기 어렵기 때문이야. 개발 초기에는 60초도 가능하지만, 가능하면 120초 이상이 낫다.
     */
    val hrvFrequencyWindowSeconds: Int = 120,

    // LF 최저 주파수 0.04Hz의 주기를 충분히 관찰하기 위한 초기 제한값.
    // 0.04Hz 한 주기는 25초이며, 기본값은 최소 4주기(100초)를 요구한다.
    val hrvSpectralMinObservedLfCycles: Double = 4.0,
    val hrvSpectralMinObservedSeconds: Double = 100.0,

    // 120초 창에서 최소한 확보할 유효 IBI 수. 실착 로그로 재조정 가능한 설정값이다.
    val hrvSpectralMinIbiCount: Int = 60,

    // LF/HF 사용 허용을 위한 품질 제한.
    val hrvFrequencyMinQualityScore: Double = 0.65,
    val hrvFrequencyMinPpgSignalQuality: Double = 0.60,

    // FFT 구현의 power scale은 IBI(seconds) 기반이다. 아래 절대값은 보수적인 초기값이며
    // 실제 로그의 분포를 보고 조정해야 한다.
    val hrvFrequencyMinLfPower: Double = 1e-10,
    val hrvFrequencyMinHfPower: Double = 1e-10,

    // 비정상적으로 큰 비율 또는 무한대는 사용하지 않는다.
    val hrvFrequencyMaxLfHfRatio: Double = 10.0,

    // 기존 threshold 기반 HRV 디버깅 점수 구성 비율. 실제 최종 각성점수는 evidence 결합을 사용한다.
    val hrvFrequencyScoreWeight: Double = 0.70,
    val hrvRmssdScoreWeight: Double = 0.30,

    // IBI를 등간격 시계열로 바꿀 때 사용할 resampling rate
    // HRV에서는 보통 4Hz 정도를 많이 사용
    val hrvResampleRateHz: Double = 4.0,

    // LF/HF frequency band
    val hrvLfLowHz: Double = 0.04,
    val hrvLfHighHz: Double = 0.15,
    val hrvHfLowHz: Double = 0.15,
    val hrvHfHighHz: Double = 0.40,

    // LF/HF ratio가 이 값 이상이면 HRV 관점에서 각성 점수 높게 봄
    // 개인 기준선이 아직 없을 때만 fallback 점수에 사용한다.
    val hrvLfHfScoreThreshold: Double = 2.0,

    // 개인 기준선 기반 evidence scoring 설정.
    val evidenceScoring: EvidenceScoringConfig = EvidenceScoringConfig(),

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

    // 유효 피부온도가 이 시간 동안 새로 들어오지 않으면 과거 온도 history를 폐기한다.
    val skinTempFreshnessTimeoutMillis: Long = 30 * 1000L,

    // Final evidence score
    val wakeCandidateScore: Double = 60.0,
    // 아래 legacy weight는 과거 calculateFinalWakeScore 호환용이며 새 evidence 경로에서는 사용하지 않는다.
    val mmScoreWeight: Double = 0.20,
    val rrScoreWeight: Double = 0.15,
    val rrvScoreWeight: Double = 0.15,
    val hrScoreWeight: Double = 0.20,
    val hrvScoreWeight: Double = 0.10,
    // legacy 경로에서는 skin temperature를 multiplier로 사용했다.
    val tempScoreWeight: Double = 0.25,

    // Green PPG 128Hz / IMU 64Hz 기준으로 최근 60초 보관
    val ppgWindowSeconds: Int = 60,
    val imuWindowSeconds: Int = 60,

    // 체온/심박은 1초마다 1개 정도 들어온다고 보고 최근 10분 보관
    val temperatureWindowMillis: Long = 10 * 60 * 1000L,
    val heartRateWindowMillis: Long = 10 * 60 * 1000L,

    // Potch510 LSM6DS3TR-C: ±4g, 1g = 8192 LSB.
    // iOS DataProcessor와 같은 스케일을 사용한다.
    val imuLsbPerG: Double = 8192.0,

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
 * 호흡 peak 두 개 사이의 interval.
 *
 * sample position은 현재 analysis segment 안에서의 절대 위치이므로,
 * rolling window를 매초 다시 계산해도 endSamplePosition으로 중복 제거할 수 있다.
 */
data class RespirationInterval(
    val intervalSec: Double,
    val startSamplePosition: Long,
    val endSamplePosition: Long,

    // packet/CRC discontinuity로 나뉘는 상위 분석 segment.
    val segmentId: Long,

    // 같은 packet segment 안에서도 PPG 접촉이 길게 끊기면 별도 continuity group으로 나눈다.
    // RR은 group 내부 interval만 만들고, RRV도 group 경계를 넘는 연속 차이를 계산하지 않는다.
    val continuityGroupId: Long = segmentId,

    // 0.2~0.5초 중간 gap을 가로지르는 interval은 파형 표시에는 남겨도 RR 평균에는 쓰지 않는다.
    val crossesInvalidGap: Boolean = false
)

private data class BufferedRespirationInterval(
    val interval: RespirationInterval,
    val qualityScore: Double
)

data class RrvCalculationBundle(
    val ppg: RrvResult?,
    val imu: RrvResult?,
    val selected: RrvResult?
)

/** Green PPG 단일 채널. */
enum class PpgRespirationChannel {
    GREEN
}

/** RR 호흡 파형에서 현재 사용하는 peak 방향. */
enum class RespirationPeakPolarity {
    POSITIVE,
    NEGATIVE,
    NONE
}

/**
 * ExperimentScreen에 노출하는 PPG 기반 RR 분석 파형 snapshot.
 *
 * samples는 실제 RR 계산과 동일하게 DC 제거 -> 0.1~0.5Hz BPF -> 2초 warm-up 제거를
 * 적용한 뒤, 선택된 polarity가 위쪽 peak가 되도록 정렬한 파형이다.
 */
data class PpgRespirationGraphData(
    val channel: PpgRespirationChannel? = null,
    val selectedPolarity: RespirationPeakPolarity = RespirationPeakPolarity.NONE,
    val processingState: MetricCalculationState = MetricCalculationState.COLLECTING,

    val samples: List<Double> = emptyList(),
    val sampleRateHz: Double = 128.0,
    val windowSeconds: Double = 0.0,
    val minimumWindowSeconds: Int = 25,

    // 현재 graph window 기준 marker index.
    // detected: threshold를 넘은 전체 호흡 peak
    // accepted: 최종 RR 평균에 사용된 interval의 종료 peak
    // rejected: 생리 범위 또는 median outlier filter에서 탈락한 interval의 종료 peak
    // reference: 첫 번째 검출 peak이며 첫 호흡 interval의 시작 기준점
    val detectedPeakSampleIndices: List<Int> = emptyList(),
    val acceptedPeakSampleIndices: List<Int> = emptyList(),
    val rejectedPeakSampleIndices: List<Int> = emptyList(),
    val referencePeakSampleIndex: Int? = null,

    // 여러 contact segment를 한 카드에 이어 보여줄 때 각 segment의 첫 peak와 경계를 표시한다.
    val referencePeakSampleIndices: List<Int> = emptyList(),
    val segmentBreakSampleIndices: List<Int> = emptyList(),

    // 보간된 sample은 peak 후보에서는 제외되며 디버깅용 위치만 노출한다.
    val interpolatedSampleIndices: List<Int> = emptyList(),

    // window 품질/구성 진단.
    val rawWindowSeconds: Double = 0.0,
    val validOriginalSampleCount: Int = 0,
    val invalidSampleCount: Int = 0,
    val interpolatedSampleCount: Int = 0,
    val settlingDiscardedSampleCount: Int = 0,
    val segmentCount: Int = 0,
    val shortGapCount: Int = 0,
    val mediumGapCount: Int = 0,
    val longGapCount: Int = 0,

    val detectedPeakCount: Int = 0,
    val rawIntervalCount: Int = 0,
    val acceptedIntervalCount: Int = 0,
    val rejectedIntervalCount: Int = 0,

    val peakThreshold: Double? = null,
    val peakToPeakAmplitude: Double? = null,
    val calculatedRrBpm: Double? = null,
    val qualityScore: Double? = null,
    val description: String = "RR 분석용 PPG 수집 중"
)

/**
 * Green PPG RR peak 방향 선택 경로.
 *
 * positive를 기본 경로로 유지하고, 지속 실패한 경우에만 negative 경로를
 * 여러 frame 확인한 뒤 전환한다.
 */
private enum class PpgRespirationDetectionPath(
    val channel: PpgRespirationChannel,
    val inverted: Boolean,
    val label: String
) {
    GREEN_POSITIVE(PpgRespirationChannel.GREEN, false, "GREEN positive"),
    GREEN_NEGATIVE(PpgRespirationChannel.GREEN, true, "GREEN negative")
}

/** IMU RR은 positive를 primary, negative를 fallback으로 유지한다. */
private enum class ImuRespirationDetectionPath(
    val inverted: Boolean,
    val label: String
) {
    POSITIVE(false, "IMU positive"),
    NEGATIVE(true, "IMU negative")
}

private data class PpgRespirationCandidateAnalysis(
    val result: PpgRespirationResult?,
    val graphData: PpgRespirationGraphData
)

private data class PpgRespirationCandidates(
    val positive: PpgRespirationCandidateAnalysis,
    val negative: PpgRespirationCandidateAnalysis
)


/**
 * 최근 RR window에서 접촉 불량/0값 gap을 정리한 하나의 연속 PPG segment.
 */
private data class GapAwarePpgRespirationSegment(
    val startSamplePosition: Long,
    val continuityGroupId: Long,
    val values: DoubleArray,
    val interpolatedMask: BooleanArray,
    val peakExcludedMask: BooleanArray,
    val mediumGapMask: BooleanArray
)

private data class GapAwarePpgRespirationWindow(
    val segments: List<GapAwarePpgRespirationSegment>,
    val rawWindowSampleCount: Int,
    val validOriginalSampleCount: Int,
    val invalidSampleCount: Int,
    val interpolatedSampleCount: Int,
    val settlingDiscardedSampleCount: Int,
    val shortGapCount: Int,
    val mediumGapCount: Int,
    val longGapCount: Int
)

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
    val inverted: Boolean,
    val peakSamplePositions: List<Long>,
    val intervals: List<RespirationInterval>
) {
    val intervalsSec: List<Double>
        get() = intervals.map { it.intervalSec }
}

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
    val peakSamplePositions: List<Long>,
    val intervals: List<RespirationInterval>
) {
    val intervalsSec: List<Double>
        get() = intervals.map { it.intervalSec }
}
/**
 * 최종 RR이 어떤 센서 조합으로 결정되었는지 나타낸다.
 *
 * PPG와 IMU가 모두 유효하면 weighted fusion을 사용하고,
 * 한쪽만 유효하거나 서로 강하게 불일치하면 source로 그 판단 근거를 남긴다.
 */
enum class RrFusionSource {
    BOTH_WEIGHTED,
    IMU_ONLY,
    GREEN_PPG_ONLY,
    IMU_PREFERRED_DISAGREE,
    GREEN_PPG_PREFERRED_DISAGREE,
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
    GREEN_PPG,
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
    val observedDurationSec: Double,
    val score: Double,
    val qualityScore: Double,
    val log: String
)

/** LF/HF 결과를 사용하지 못한 구체적인 사유. CSV에는 code를 저장한다. */
enum class HrvFrequencyRejectionReason(val code: String) {
    HRV_NOT_FRESH("HRV_NOT_FRESH"),
    INSUFFICIENT_IBI_COUNT("INSUFFICIENT_IBI_COUNT"),
    TOO_MANY_IBI_OUTLIERS("TOO_MANY_IBI_OUTLIERS"),
    INSUFFICIENT_OBSERVED_DURATION("INSUFFICIENT_OBSERVED_DURATION"),
    RESAMPLING_FAILED("RESAMPLING_FAILED"),
    RESAMPLED_SAMPLE_COUNT_TOO_LOW("RESAMPLED_SAMPLE_COUNT_TOO_LOW"),
    PPG_SIGNAL_STATUS_NOT_VALID("PPG_SIGNAL_STATUS_NOT_VALID"),
    PPG_SIGNAL_QUALITY_TOO_LOW("PPG_SIGNAL_QUALITY_TOO_LOW"),
    RESPIRATORY_RATE_UNAVAILABLE("RESPIRATORY_RATE_UNAVAILABLE"),
    RESPIRATORY_RATE_OUTSIDE_HF_BAND("RESPIRATORY_RATE_OUTSIDE_HF_BAND"),
    NO_SPECTRAL_POWER("NO_SPECTRAL_POWER"),
    LF_POWER_TOO_LOW("LF_POWER_TOO_LOW"),
    HF_POWER_TOO_LOW("HF_POWER_TOO_LOW"),
    LF_HF_NOT_FINITE("LF_HF_NOT_FINITE"),
    LF_HF_OUT_OF_RANGE("LF_HF_OUT_OF_RANGE"),
    SPECTRAL_QUALITY_TOO_LOW("SPECTRAL_QUALITY_TOO_LOW")
}

/** LF/HF 후보값과 사용 가능 여부를 함께 전달해 실패 사유가 유실되지 않게 한다. */
data class HrvFrequencyAssessment(
    val candidate: HrvFrequencyResult? = null,
    val usable: Boolean = false,
    val status: MetricCalculationStatus = MetricCalculationStatus(),
    val rejectionReasons: List<HrvFrequencyRejectionReason> = emptyList(),
    val rejectionDetails: String? = null,
    val rawIbiCount: Int = 0,
    val cleanedIbiCount: Int = 0,
    val recentIbiCount: Int = 0,
    val observedDurationSec: Double = 0.0,
    val resampledCount: Int = 0,
    val ppgSignalQuality: Double? = null,
    val respiratoryRateBpm: Double? = null
) {
    val rejectionCodeString: String?
        get() = rejectionReasons.takeIf { it.isNotEmpty() }
            ?.joinToString("|") { it.code }
}

private data class HrvCombinedScore(
    val score: Double?,
    val quality: Double,
    val composition: String,
    val log: String
)

private enum class EvidenceDirection {
    HIGHER_ONLY,
    LOWER_ONLY,
    TWO_SIDED
}

private data class EvidenceComponent(
    val name: String,
    val score: Double,
    val confidence: Double,
    val baseWeight: Double,
    val usable: Boolean,
    val reason: String? = null
)

private data class EvidenceCombination(
    val score: Double?,
    val confidence: Double,
    val coverage: Double,
    val usable: Boolean,
    val composition: String
)

private data class BaselineEvidenceDetail(
    val component: EvidenceComponent?,
    val source: String,
    val center: Double?,
    val spread: Double?,
    val signedDistance: Double?,
    val normalizedDistance: Double?,
    val reason: String?
)

private data class HrvEvidenceBundle(
    val evidence: MetricEvidence,
    val frequencyScore: Double?,
    val rmssdScore: Double?,
    val composition: String,
    val log: String
)

private data class FinalArousalResult(
    val score: Double,
    val confidence: Double,
    val coverage: Double,
    val movementDomain: DomainEvidence,
    val respiratoryDomain: DomainEvidence,
    val cardiacDomain: DomainEvidence,
    val temperatureDomain: DomainEvidence,
    val usedDomainCount: Int,
    val currentConditionPassed: Boolean,
    val persistenceWindowSeconds: Int,
    val persistenceRequiredPassSeconds: Int,
    val persistenceObservedSeconds: Int,
    val persistencePassedSeconds: Int,
    val persistenceFailedSeconds: Int,
    val persistencePassRatio: Double,
    val candidate: Boolean,
    val reason: String,
    val log: String
)

private data class WakePersistenceSample(
    val secondBucket: Long,
    val passed: Boolean
)

private data class WakePersistenceSummary(
    val windowSeconds: Int,
    val requiredPassSeconds: Int,
    val observedSeconds: Int,
    val passedSeconds: Int,
    val failedSeconds: Int,
    val passRatio: Double,
    val windowComplete: Boolean,
    val candidate: Boolean
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
    val greenPpgSampleCount: Int,
    val imuGSampleCount: Int,
    val temperatureSampleCount: Int,
    val heartRateSampleCount: Int,
    val ppgRrvIntervalCount: Int,
    val imuRrvIntervalCount: Int,
    val latestTemperatureCelsius: Double?,
    val latestHeartRateBpm: Int?
)

/**
 * Potch 센서 데이터에서 각성 관련 지표를 종합 계산하는 클래스.
 *
 * 1초 Burst 단위로 들어오는 Green PPG, 6축 IMU, NTC, HR estimate를 rolling buffer에 누적하고
 * Micro Movement, RR, RRV, HR, HRV, Skin Temperature, final wake score를 계산한다.
 */
class PotchArousalCalculator(
    private var config: ArousalConfig = ArousalConfig()
) {
    /**
     * Green PPG raw sample rolling buffer.
     *
     * 용도:
     * - PPG 기반 RR 계산
     * - 호흡 interval/RRV 계산
     */
    private val greenPpgBuffer = ArrayDeque<Double>()

    /**
     * RR 각성 점수 계산용 rolling buffer.
     *
     * Pair<timestampMillis, rrBpm>
     *
     * rrFinal이 계산되고 confidence가 충분한 경우에만 저장한다.
     */
    private val respirationRateBuffer = ArrayDeque<Pair<Long, Double>>()

    /**
     * 선택된 RRV RMSSD의 시간 변화 추세를 계산하기 위한 rolling history.
     * 같은 3분 호흡 interval buffer와 별도로, 최종 대표 RMSSD를 1초 단위로 보관한다.
     */
    private val rrvValueBuffer = ArrayDeque<Pair<Long, Double>>()

    // PPG RR 경로 hysteresis state.
    private var activePpgRespirationPath =
        PpgRespirationDetectionPath.GREEN_POSITIVE
    private var activePpgRespirationPathSinceMillis: Long = 0L
    private var ppgRespirationFailureStreak: Int = 0
    private var pendingPpgRespirationPath: PpgRespirationDetectionPath? = null
    private var pendingPpgRespirationSuccessStreak: Int = 0
    private var pendingPpgRespirationLastRr: Double? = null
    private var ppgPrimaryRecoveryStreak: Int = 0
    private var ppgPrimaryRecoveryLastRr: Double? = null

    // UI에는 선택된 PPG RR 경로의 실제 후처리 파형을 매 frame snapshot으로 노출한다.
    private var latestPpgRespirationGraphData = PpgRespirationGraphData(
        minimumWindowSeconds = config.ppgRespMinWindowSeconds
    )

    // IMU RR polarity hysteresis state.
    private var activeImuRespirationPath =
        ImuRespirationDetectionPath.POSITIVE
    private var activeImuRespirationPathSinceMillis: Long = 0L
    private var imuRespirationFailureStreak: Int = 0
    private var pendingImuRespirationPath: ImuRespirationDetectionPath? = null
    private var pendingImuRespirationSuccessStreak: Int = 0
    private var pendingImuRespirationLastRr: Double? = null
    private var imuPrimaryRecoveryStreak: Int = 0
    private var imuPrimaryRecoveryLastRr: Double? = null

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
     * 시간 기준은 휴대폰의 System.currentTimeMillis()를 사용한다.
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

    /**
     * RRV 전용 source별 rolling interval buffer.
     *
     * RR 계산의 45초 창과 분리해 최근 config.rrvWindowSeconds(기본 180초)의
     * 중복되지 않은 호흡 interval을 유지한다.
     */
    private val ppgRrvIntervalBuffer = ArrayDeque<BufferedRespirationInterval>()
    private val imuRrvIntervalBuffer = ArrayDeque<BufferedRespirationInterval>()

    // 현재 segment에서 각 source가 처리한 누적 sample 수.
    private var totalPpgRespSampleCount: Long = 0L
    private var totalImuRespSampleCount: Long = 0L

    // rolling 재계산에서 동일 interval을 다시 넣지 않기 위한 마지막 end position.
    private var lastAcceptedPpgRrvEndSamplePosition: Long = Long.MIN_VALUE
    private var lastAcceptedImuRrvEndSamplePosition: Long = Long.MIN_VALUE

    // 현재 유효한 분석 연속 구간과 HRV 중복 제거 위치.
    private var currentAnalysisSegmentId: Long = 0L
    private var lastAcceptedHrvIbiSegmentId: Long = Long.MIN_VALUE
    private var lastAcceptedHrvIbiEndSamplePosition: Double = Double.NEGATIVE_INFINITY

    // 각 계산 버퍼에 마지막으로 유효한 값이 들어온 휴대폰 시각.
    private var lastValidHeartRateTimestampMillis: Long? = null
    private var lastValidHrvTimestampMillis: Long? = null
    private var lastValidRespirationTimestampMillis: Long? = null
    private var lastValidTemperatureTimestampMillis: Long? = null

    // 긴 품질 불량 구간 뒤 과거 데이터와 새 데이터를 섞지 않기 위한 상태.
    private var heartRateBufferExpiredByGap: Boolean = false
    private var hrvBufferExpiredByGap: Boolean = false
    private var respirationBufferExpiredByGap: Boolean = false
    private var temperatureBufferExpiredByGap: Boolean = false

    private val maxPpgSamples =
        (config.ppgSampleRateHz * config.ppgWindowSeconds).toInt()

    private val maxImuSamples =
        (config.imuSampleRateHz * config.imuWindowSeconds).toInt()

    private var lastState = ArousalState()

    /** 수면 세션 시작 시 PotchStabilityCalculator가 고정한 개인 기준선 snapshot. */
    private var personalBaselines: Map<BaselineMetricType, PersonalBaselineRecord> = emptyMap()

    /** 최근 N초 중 M초 통과 방식의 tolerant persistence 기록. */
    private val wakePersistenceSamples = ArrayDeque<WakePersistenceSample>()

    @Synchronized
    fun updatePersonalBaselines(
        baselines: Map<BaselineMetricType, PersonalBaselineRecord>
    ) {
        personalBaselines = baselines.toMap()
    }

    /**
     * 새 1초 Burst 하나를 받아 모든 각성 지표를 갱신한다.
     *
     * 1초분 센서 데이터를 rolling buffer에 append한 뒤,
     * micro movement, RR, RRV, HR, HRV, skin temperature를 순서대로 계산하고
     * 마지막으로 final wake score를 만들어 ArousalState로 반환한다.
     */
    fun process(
        sensorData: SensorData,
        heartRateEstimate: HeartRateEstimate?,
        heartRateSignalStatus: MetricCalculationStatus = MetricCalculationStatus(
            state = MetricCalculationState.COLLECTING,
            message = "합산 PPG 심박 신호 수집 중"
        ),
        analysisSegmentId: Long = currentAnalysisSegmentId
    ): ArousalState {
        // 호출자가 segment 변경 알림을 먼저 주지 않았더라도 안전하게 경계를 적용한다.
        if (analysisSegmentId != currentAnalysisSegmentId) {
            onDataDiscontinuity(
                newSegmentId = analysisSegmentId,
                reason = "segment changed before valid frame"
            )
        }

        // 펌웨어 timestamp 단위와 무관하게 stale/rolling window를 안정적으로 관리하기 위해
        // 계산 버퍼의 시간 기준은 휴대폰 수신 시각을 사용한다.
        val nowMillis = System.currentTimeMillis()

        // 새 정상값이 없어도 매 프레임 오래된 값을 제거하고,
        // 품질 불량 구간이 timeout을 넘으면 해당 history를 완전히 비운다.
        expireStaleMetricBuffers(nowMillis)
        trimMetricBuffersNow(nowMillis)

        appendPpg(sensorData.ppgData)
        appendImu(sensorData.imuData)
        appendTemperature(nowMillis, sensorData.ntcCelsius)

        if (heartRateEstimate != null) {
            appendHeartRate(
                timestampMillis = nowMillis,
                bpm = heartRateEstimate.bpm
            )

            appendHeartRateEstimateToHrvBuffer(
                estimate = heartRateEstimate,
                acceptedAtMillis = nowMillis
            )
        }

        val microMovement = calculateMicroMovement()

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
                timestampMillis = nowMillis,
                rrBpm = rrFinal,
                confidence = rrFusion.confidence
            )
        }

        val rrArousalResult = calculateRespiratoryRateArousal(rrFusion)

        // 현재 45초 창에서 새로 나타난 interval만 source별 3분 RRV buffer에 누적한다.
        appendRespirationIntervalsForRrv(
            ppg = ppgRespiration,
            imu = imuRespiration
        )
        trimRespirationVariabilityBuffers()

        val rrvBundle = calculateRrvRmssd(
            rrFusion = rrFusion
        )
        val rrvResult = rrvBundle.selected
        appendRrvEvidenceHistory(
            nowMillis = nowMillis,
            result = rrvResult
        )

        val heartRateIsFresh = isFresh(
            lastValidTimestampMillis = lastValidHeartRateTimestampMillis,
            nowMillis = nowMillis,
            timeoutMillis = config.hrFreshnessTimeoutMillis
        )

        val hrvIsFresh = isFresh(
            lastValidTimestampMillis = lastValidHrvTimestampMillis,
            nowMillis = nowMillis,
            timeoutMillis = config.hrvFreshnessTimeoutMillis
        )

        val temperatureIsFresh = isFresh(
            lastValidTimestampMillis = lastValidTemperatureTimestampMillis,
            nowMillis = nowMillis,
            timeoutMillis = config.skinTempFreshnessTimeoutMillis
        )

        val hrResult = if (heartRateIsFresh) {
            calculateHeartRateArousal()
        } else {
            null
        }

        val skinTempResult = if (temperatureIsFresh) {
            calculateSkinTemperatureArousal()
        } else {
            null
        }

        val hrvResult = if (hrvIsFresh) {
            calculateHeartRateVariability()
        } else {
            null
        }

        val hrvFrequencyAssessment = if (hrvIsFresh) {
            evaluateHrvFrequencyDomain(
                respiratoryRateBpm = rrFinal,
                ppgSignalQuality = heartRateEstimate?.qualityScore,
                heartRateSignalStatus = heartRateSignalStatus
            )
        } else {
            HrvFrequencyAssessment(
                usable = false,
                status = MetricCalculationStatus(
                    state = MetricCalculationState.REJECTED,
                    message = "최근 유효 IBI가 없어 LF/HF를 사용할 수 없음"
                ),
                rejectionReasons = listOf(HrvFrequencyRejectionReason.HRV_NOT_FRESH),
                rejectionDetails = "freshnessTimeoutMs=${config.hrvFreshnessTimeoutMillis}",
                ppgSignalQuality = heartRateEstimate?.qualityScore,
                respiratoryRateBpm = rrFinal
            )
        }
        val hrvFrequencyResult = hrvFrequencyAssessment.candidate
        val usableHrvFrequencyResult = hrvFrequencyResult
            ?.takeIf { hrvFrequencyAssessment.usable }

        // 기존 threshold 기반 결합값은 디버깅 호환용으로 남기되,
        // 실제 각성점수는 개인 기준선+trend evidence 구조를 사용한다.
        val hrvCombinedScore = combineHrvScores(
            frequencyResult = usableHrvFrequencyResult,
            rmssdResult = hrvResult,
            frequencyAssessment = hrvFrequencyAssessment
        )

        val microEvidence = buildMicroEvidence(microMovement)
        val rrEvidence = buildRrEvidence(
            result = rrArousalResult,
            fusion = rrFusion
        )
        val rrvEvidence = buildRrvEvidence(rrvResult)
        val hrEvidence = buildHrEvidence(
            result = hrResult,
            heartRateEstimate = heartRateEstimate
        )
        val hrvEvidenceBundle = buildHrvEvidence(
            frequencyResult = usableHrvFrequencyResult,
            rmssdResult = hrvResult,
            assessment = hrvFrequencyAssessment
        )
        val hrvEvidence = hrvEvidenceBundle.evidence
        val temperatureEvidence = buildTemperatureEvidence(skinTempResult)

        val finalArousal = calculateFinalArousalResult(
            nowMillis = nowMillis,
            micro = microEvidence,
            rr = rrEvidence,
            rrv = rrvEvidence,
            hr = hrEvidence,
            hrv = hrvEvidence,
            temperature = temperatureEvidence
        )

        val rrCalculationStatus = buildRrCalculationStatus(
            ppg = ppgRespiration,
            imu = imuRespiration,
            fusion = rrFusion
        )

        val rrvCalculationStatus = buildRrvCalculationStatus(
            result = rrvResult,
            rrStatus = rrCalculationStatus
        )

        val hrCalculationStatus = buildHrCalculationStatus(
            heartRateEstimate = heartRateEstimate,
            heartRateSignalStatus = heartRateSignalStatus,
            result = hrResult,
            isFresh = heartRateIsFresh
        )

        val hrvCalculationStatus = buildHrvCalculationStatus(
            heartRateEstimate = heartRateEstimate,
            heartRateSignalStatus = heartRateSignalStatus,
            timeDomainResult = hrvResult,
            frequencyAssessment = hrvFrequencyAssessment,
            isFresh = hrvIsFresh
        )

        lastState = ArousalState(
            microMovementVariance = microMovement?.varianceG,
            microMovementScore = microMovement?.score?.times(100.0),
            microMovementRmsG = microMovement?.rmsG,
            microMovementLevel = microMovement?.level,
            isMacroMovementLike = microMovement?.isMacroMovementLike ?: false,

            rrFromPpg = rrFromPpg,
            rrFromImu = rrFromImu,
            rrFinal = rrFinal,
            rrScore = rrEvidence.score,
            rrRawScore = rrArousalResult?.rawScore?.times(100.0),
            rrFusionSource = rrFusion.source,
            rrFusionConfidence = rrFusion.confidence,
            rrFusionLog = rrFusion.log,
            rrCalculationStatus = rrCalculationStatus,

            rrAnalysisSegmentId = currentAnalysisSegmentId,
            ppgRespPeakSamplePositions = ppgRespiration?.peakSamplePositions ?: emptyList(),
            ppgRespIntervalsSec = ppgRespiration?.intervalsSec ?: emptyList(),
            imuRespPeakSamplePositions = imuRespiration?.peakSamplePositions ?: emptyList(),
            imuRespIntervalsSec = imuRespiration?.intervalsSec ?: emptyList(),
            ppgRespirationGraphData = latestPpgRespirationGraphData,

            rrvRmssd = rrvResult?.rmssdSec,
            rrvRmssdMs = rrvResult?.rmssdMs,
            rrvScore = rrvEvidence.score,
            rrvSource = rrvResult?.source ?: RrvSource.NONE,
            rrvQuality = rrvResult?.qualityScore ?: 0.0,
            rrvIntervalCount = rrvResult?.intervalCount ?: 0,
            rrvFromPpgRmssdSec = rrvBundle.ppg?.rmssdSec,
            rrvFromImuRmssdSec = rrvBundle.imu?.rmssdSec,
            rrvPpgIntervalCount = rrvBundle.ppg?.intervalCount ?: ppgRrvIntervalBuffer.size,
            rrvImuIntervalCount = rrvBundle.imu?.intervalCount ?: imuRrvIntervalBuffer.size,
            rrvPpgQuality = rrvBundle.ppg?.qualityScore ?: 0.0,
            rrvImuQuality = rrvBundle.imu?.qualityScore ?: 0.0,
            rrvCalculationStatus = rrvCalculationStatus,

            // stale 상태에서는 마지막 정상값을 유지하지 않고 null로 내린다.
            hrBpm = if (heartRateIsFresh) {
                hrResult?.currentBpm ?: heartRateEstimate?.bpm ?: heartRateBuffer.lastOrNull()?.second
            } else {
                null
            },
            hrGradient = hrResult?.gradientBpm,
            hrScore = hrEvidence.score,
            hrCalculationStatus = hrCalculationStatus,

            hrvRmssd = hrvResult?.rmssdSec,
            hrvRmssdMs = hrvResult?.rmssdMs,
            // 후보 LF/HF 값은 사용 제한에 걸려도 분석/튜닝을 위해 로그에 남긴다.
            hrvLf = hrvFrequencyResult?.lfPower,
            hrvHf = hrvFrequencyResult?.hfPower,
            hrvLfHf = hrvFrequencyResult?.lfHfRatio,
            hrvScore = hrvEvidence.score,
            hrvQuality = hrvEvidence.confidence,
            hrvIbiCount = maxOf(
                hrvFrequencyAssessment.recentIbiCount,
                hrvResult?.ibiCount ?: 0
            ),
            hrvRmssdScore = hrvEvidenceBundle.rmssdScore?.times(100.0),
            hrvRmssdQuality = hrvResult?.qualityScore ?: 0.0,
            hrvRmssdIbiCount = hrvResult?.ibiCount ?: 0,
            hrvFrequencyScore = hrvEvidenceBundle.frequencyScore?.times(100.0),
            hrvFrequencyQuality = hrvFrequencyResult?.qualityScore ?: 0.0,
            hrvFrequencyIbiCount = hrvFrequencyAssessment.recentIbiCount,
            hrvFrequencyUsable = hrvFrequencyAssessment.usable,
            hrvFrequencyStatus = hrvFrequencyAssessment.status,
            hrvFrequencyRejectionReasons = buildString {
                hrvFrequencyAssessment.rejectionCodeString?.let { append(it) }
                hrvFrequencyAssessment.rejectionDetails?.let { details ->
                    if (isNotEmpty()) append(";")
                    append(details)
                }
            }.takeIf { it.isNotEmpty() },
            hrvFrequencyObservedSeconds = hrvFrequencyAssessment.observedDurationSec,
            hrvFrequencyRawIbiCount = hrvFrequencyAssessment.rawIbiCount,
            hrvFrequencyCleanedIbiCount = hrvFrequencyAssessment.cleanedIbiCount,
            hrvFrequencyResampledCount = hrvFrequencyAssessment.resampledCount,
            hrvFrequencyPpgSignalQuality = hrvFrequencyAssessment.ppgSignalQuality,
            hrvFrequencyRespiratoryRateBpm = hrvFrequencyAssessment.respiratoryRateBpm,
            hrvScoreComposition = hrvEvidenceBundle.composition,
            hrvLog = hrvEvidenceBundle.log + "; candidate=" + hrvCombinedScore.log,
            hrvCalculationStatus = hrvCalculationStatus,

            skinTemperatureCelsius = if (temperatureIsFresh) {
                skinTempResult?.currentCelsius ?: temperatureBuffer.lastOrNull()?.second
            } else {
                null
            },
            skinTemperatureGradient = skinTempResult?.gradientCelsius,
            skinTemperatureScore = temperatureEvidence.score,
            skinTemperatureQuality = skinTempResult?.qualityScore ?: 0.0,
            skinTemperatureSampleCount = skinTempResult?.sampleCount ?: 0,

            microEvidence = microEvidence,
            rrEvidence = rrEvidence,
            rrvEvidence = rrvEvidence,
            hrEvidence = hrEvidence,
            hrvEvidence = hrvEvidence,
            temperatureEvidence = temperatureEvidence,

            movementDomainEvidence = finalArousal.movementDomain,
            respiratoryDomainEvidence = finalArousal.respiratoryDomain,
            cardiacDomainEvidence = finalArousal.cardiacDomain,
            temperatureDomainEvidence = finalArousal.temperatureDomain,

            finalWakeScore = finalArousal.score * 100.0,
            finalWakeConfidence = finalArousal.confidence * 100.0,
            finalWakeCoverage = finalArousal.coverage * 100.0,
            usedArousalDomainCount = finalArousal.usedDomainCount,
            wakeCandidateHoldSeconds = finalArousal.persistencePassedSeconds,
            wakeCurrentConditionPassed = finalArousal.currentConditionPassed,
            wakePersistenceWindowSeconds = finalArousal.persistenceWindowSeconds,
            wakePersistenceRequiredPassSeconds = finalArousal.persistenceRequiredPassSeconds,
            wakePersistenceObservedSeconds = finalArousal.persistenceObservedSeconds,
            wakePersistencePassedSeconds = finalArousal.persistencePassedSeconds,
            wakePersistenceFailedSeconds = finalArousal.persistenceFailedSeconds,
            wakePersistencePassRatio = finalArousal.persistencePassRatio * 100.0,
            wakeDecisionReason = finalArousal.reason,
            isWakeTimingCandidate = finalArousal.candidate,
            lastLog = finalArousal.log + ", " +
                    "micro=${microEvidence.log}, " +
                    "rr=${rrEvidence.log}, " +
                    "rrv=${rrvEvidence.log}, " +
                    "hr=${hrEvidence.log}, " +
                    "hrv=${hrvEvidence.log}, " +
                    "skin=${temperatureEvidence.log}"
        )

        return lastState
    }

    /** 최신 PotchDataProcessor API 호환 진입점. */
    fun processBurst(
        sensorData: SensorData,
        heartRateEstimate: HeartRateEstimate?,
        heartRateStatus: MetricCalculationStatus
    ): ArousalState {
        return process(
            sensorData = sensorData,
            heartRateEstimate = heartRateEstimate,
            heartRateSignalStatus = heartRateStatus,
            analysisSegmentId = currentAnalysisSegmentId
        )
    }

    /** 최신 화면/ViewModel API 호환 alias. */
    fun currentState(): ArousalState = lastState

    private fun buildRrCalculationStatus(
        ppg: PpgRespirationResult?,
        imu: ImuRespirationResult?,
        fusion: RrFusionResult
    ): MetricCalculationStatus {
        if (fusion.rrBpm != null) {
            return MetricCalculationStatus(
                state = MetricCalculationState.VALID,
                message = "정상 계산 중 (${fusion.source.name}, confidence=${"%.2f".format(fusion.confidence)})"
            )
        }

        if (respirationBufferExpiredByGap) {
            return MetricCalculationStatus(
                state = MetricCalculationState.REJECTED,
                message = "신뢰 가능한 RR이 ${config.rrFreshnessTimeoutMillis / 1000}초 이상 없어 " +
                        "과거 RR history를 초기화했습니다"
            )
        }

        val ppgMinSamples =
            (config.ppgSampleRateHz * config.ppgRespMinWindowSeconds).toInt()
        val imuMinSamples =
            (config.imuSampleRateHz * config.imuRespMinWindowSeconds).toInt()

        // raw buffer 길이가 아니라 contact/gap-aware 전처리 후 실제 유효 PPG sample 수를 사용한다.
        // 연결 직후 0값이나 긴 contact loss가 25초 수집량으로 잘못 계산되지 않게 한다.
        val ppgSampleCount = latestPpgRespirationGraphData.validOriginalSampleCount
        val imuSampleCount = imuGBuffer.size

        val hasEnoughPpg = ppgSampleCount >= ppgMinSamples
        val hasEnoughImu = imuSampleCount >= imuMinSamples

        if (!hasEnoughPpg && !hasEnoughImu) {
            val ppgSeconds = ppgSampleCount / config.ppgSampleRateHz
            val imuSeconds = imuSampleCount / config.imuSampleRateHz

            return MetricCalculationStatus(
                state = MetricCalculationState.COLLECTING,
                message = "호흡 파형 수집 중: PPG ${"%.1f".format(ppgSeconds)}초, " +
                        "IMU ${"%.1f".format(imuSeconds)}초 / 최소 ${config.ppgRespMinWindowSeconds}초"
            )
        }

        val sourceHint = when {
            ppg == null && imu == null -> "PPG/IMU 모두"
            ppg == null -> "PPG"
            imu == null -> "IMU"
            else -> "fusion"
        }

        return MetricCalculationStatus(
            state = MetricCalculationState.REJECTED,
            message = "$sourceHint 유효 호흡 파형 없음: 접촉 불량, 움직임 잡음, " +
                    "낮은 진폭 또는 interval 이상치 필터링 가능"
        )
    }

    private fun buildRrvCalculationStatus(
        result: RrvResult?,
        rrStatus: MetricCalculationStatus
    ): MetricCalculationStatus {
        if (result != null) {
            return MetricCalculationStatus(
                state = MetricCalculationState.VALID,
                message = "정상 계산 중 (${result.source.name}, interval=${result.intervalCount}, " +
                        "quality=${"%.2f".format(result.qualityScore)}, " +
                        "PPG buffer=${ppgRrvIntervalBuffer.size}, " +
                        "IMU buffer=${imuRrvIntervalBuffer.size})"
            )
        }

        val ppgCount = ppgRrvIntervalBuffer.size
        val imuCount = imuRrvIntervalBuffer.size
        val maxCount = maxOf(ppgCount, imuCount)

        if (rrStatus.state == MetricCalculationState.COLLECTING) {
            return MetricCalculationStatus(
                state = MetricCalculationState.COLLECTING,
                message = "RRV 전용 ${config.rrvWindowSeconds}초 buffer 수집 중: " +
                        "PPG=$ppgCount, IMU=$imuCount / 최소 ${config.rrvMinIntervalCount}개"
            )
        }

        if (rrStatus.state == MetricCalculationState.REJECTED) {
            return MetricCalculationStatus(
                state = MetricCalculationState.REJECTED,
                message = "현재 RR 계산 실패로 RRV 출력 보류. " +
                        "누적 buffer: PPG=$ppgCount, IMU=$imuCount"
            )
        }

        return if (maxCount < config.rrvMinIntervalCount) {
            MetricCalculationStatus(
                state = MetricCalculationState.COLLECTING,
                message = "RRV 전용 호흡 interval 수집 중: PPG=$ppgCount, IMU=$imuCount / " +
                        "최소 ${config.rrvMinIntervalCount}개"
            )
        } else {
            MetricCalculationStatus(
                state = MetricCalculationState.REJECTED,
                message = "누적 호흡 interval이 quality 또는 중앙값 이상치 필터에서 제외되어 " +
                        "RRV 계산 불가: PPG=$ppgCount, IMU=$imuCount"
            )
        }
    }

    private fun buildHrCalculationStatus(
        heartRateEstimate: HeartRateEstimate?,
        heartRateSignalStatus: MetricCalculationStatus,
        result: HeartRateArousalResult?,
        isFresh: Boolean
    ): MetricCalculationStatus {
        if (!isFresh && heartRateBufferExpiredByGap) {
            return MetricCalculationStatus(
                state = MetricCalculationState.REJECTED,
                message = "유효 HR이 ${config.hrFreshnessTimeoutMillis / 1000}초 이상 없어 " +
                        "과거 HR history를 초기화했습니다"
            )
        }

        if (result != null && isFresh) {
            return MetricCalculationStatus(
                state = MetricCalculationState.VALID,
                message = "정상 계산 중: 합산 PPG HR 추세 window ${"%.0f".format(result.windowSeconds)}초"
            )
        }

        if (heartRateEstimate == null) {
            return heartRateSignalStatus
        }

        if (heartRateBuffer.size < config.hrMinSampleCount) {
            return MetricCalculationStatus(
                state = MetricCalculationState.COLLECTING,
                message = "합산 PPG HR은 검출됨. 각성 추세용 HR sample 수집 중: " +
                        "${heartRateBuffer.size}/${config.hrMinSampleCount}"
            )
        }

        val durationMillis =
            heartRateBuffer.last().first - heartRateBuffer.first().first

        if (durationMillis < config.hrGradientMinWindowMillis) {
            return MetricCalculationStatus(
                state = MetricCalculationState.COLLECTING,
                message = "합산 PPG HR은 검출됨. HR 추세 계산용 최소 1분 수집 중: " +
                        "${durationMillis / 1000}초/60초"
            )
        }

        return MetricCalculationStatus(
            state = MetricCalculationState.REJECTED,
            message = "HR history의 이상치 제거 후 유효 sample이 부족해 각성 추세 계산 불가"
        )
    }

    private fun buildHrvCalculationStatus(
        heartRateEstimate: HeartRateEstimate?,
        heartRateSignalStatus: MetricCalculationStatus,
        timeDomainResult: HeartRateVariabilityResult?,
        frequencyAssessment: HrvFrequencyAssessment,
        isFresh: Boolean
    ): MetricCalculationStatus {
        val currentSegmentIbiCount = currentSegmentHrvIbis().size

        if (!isFresh && hrvBufferExpiredByGap) {
            return MetricCalculationStatus(
                state = MetricCalculationState.REJECTED,
                message = "새 유효 IBI가 ${config.hrvFreshnessTimeoutMillis / 1000}초 이상 없어 " +
                        "과거 HRV buffer를 초기화했습니다"
            )
        }

        if (frequencyAssessment.usable || timeDomainResult != null) {
            val frequencyMessage = if (frequencyAssessment.usable) {
                "LF/HF 사용 가능"
            } else {
                "LF/HF 사용 불가(${frequencyAssessment.rejectionCodeString ?: frequencyAssessment.status.message})"
            }
            val rmssdMessage = if (timeDomainResult != null) {
                "RMSSD 사용 가능"
            } else {
                "RMSSD 사용 불가"
            }
            return MetricCalculationStatus(
                state = MetricCalculationState.VALID,
                message = "$frequencyMessage · $rmssdMessage · 설계 비율 LF/HF 70%, RMSSD 30% (사용 불가 증거 제외·confidence 감소)"
            )
        }

        if (currentSegmentIbiCount < config.hrvMinIbiCount) {
            if (heartRateEstimate == null &&
                heartRateSignalStatus.state == MetricCalculationState.REJECTED
            ) {
                return MetricCalculationStatus(
                    state = MetricCalculationState.REJECTED,
                    message = "PPG peak/IBI 추출 실패로 HRV 계산 불가: " +
                            heartRateSignalStatus.message
                )
            }

            return MetricCalculationStatus(
                state = MetricCalculationState.COLLECTING,
                message = "유효 IBI 수집 중: ${currentSegmentIbiCount}/${config.hrvMinIbiCount}"
            )
        }

        return frequencyAssessment.status.takeIf {
            it.state != MetricCalculationState.COLLECTING || currentSegmentIbiCount > 0
        } ?: MetricCalculationStatus(
            state = MetricCalculationState.REJECTED,
            message = "IBI가 품질 또는 중앙값 이상치 필터에서 제외되어 HRV 계산 불가"
        )
    }


    private fun hillEvidenceScore(normalizedDistance: Double): Double {
        if (!normalizedDistance.isFinite() || normalizedDistance <= 0.0) return 0.0
        val z = max(0.0, normalizedDistance - config.evidenceScoring.evidenceDeadZoneZ)
        if (z <= 0.0) return 0.0

        val exponent = config.evidenceScoring.evidenceHillExponent.coerceAtLeast(1.0)
        val half = config.evidenceScoring.evidenceHalfSaturationZ.coerceAtLeast(1e-6)
        val numerator = z.pow(exponent)
        val denominator = half.pow(exponent) + numerator
        if (!denominator.isFinite() || denominator <= 0.0) return 0.0
        return (numerator / denominator).coerceIn(0.0, 1.0)
    }

    private fun buildBaselineEvidence(
        metricType: BaselineMetricType,
        value: Double?,
        signalQuality: Double,
        direction: EvidenceDirection,
        minimumScale: Double,
        fallbackScore: Double?,
        componentName: String,
        baseWeight: Double
    ): BaselineEvidenceDetail {
        if (value == null || !value.isFinite()) {
            return BaselineEvidenceDetail(
                component = null,
                source = "NONE",
                center = null,
                spread = null,
                signedDistance = null,
                normalizedDistance = null,
                reason = "CURRENT_VALUE_UNAVAILABLE"
            )
        }

        val quality = signalQuality.coerceIn(0.0, 1.0)
        val baseline = personalBaselines[metricType]
            ?.takeIf {
                it.isUsable &&
                        it.center?.isFinite() == true &&
                        it.spread?.isFinite() == true
            }

        if (baseline != null) {
            val center = baseline.center ?: return BaselineEvidenceDetail(
                null, "NONE", null, null, null, null, "BASELINE_CENTER_UNAVAILABLE"
            )
            val rawSpread = baseline.spread ?: 0.0
            val robustScale = max(1.4826 * rawSpread, minimumScale.coerceAtLeast(1e-9))
            val signedDistance = (value - center) / robustScale
            val directedDistance = when (direction) {
                EvidenceDirection.HIGHER_ONLY -> max(0.0, signedDistance)
                EvidenceDirection.LOWER_ONLY -> max(0.0, -signedDistance)
                EvidenceDirection.TWO_SIDED -> abs(signedDistance)
            }
            val score = hillEvidenceScore(directedDistance)
            val baselineConfidence = baseline.confidence.coerceIn(0.0, 1.0)
            val confidence = (
                    quality * 0.65 +
                            baselineConfidence * 0.35
                    ).coerceIn(0.0, 1.0)

            return BaselineEvidenceDetail(
                component = EvidenceComponent(
                    name = componentName,
                    score = score,
                    confidence = confidence,
                    baseWeight = baseWeight,
                    usable = true
                ),
                source = "PERSONAL",
                center = center,
                spread = rawSpread,
                signedDistance = value - center,
                normalizedDistance = signedDistance,
                reason = null
            )
        }

        val fallback = fallbackScore
            ?.takeIf { it.isFinite() }
            ?.coerceIn(0.0, 1.0)

        if (fallback == null) {
            return BaselineEvidenceDetail(
                component = null,
                source = "NONE",
                center = null,
                spread = null,
                signedDistance = null,
                normalizedDistance = null,
                reason = "PERSONAL_BASELINE_UNAVAILABLE"
            )
        }

        val confidence = (
                quality * 0.70 +
                        config.evidenceScoring.fallbackBaselineConfidence.coerceIn(0.0, 1.0) * 0.30
                ).coerceIn(0.0, 1.0)

        return BaselineEvidenceDetail(
            component = EvidenceComponent(
                name = componentName,
                score = fallback,
                confidence = confidence,
                baseWeight = baseWeight,
                usable = true,
                reason = "PERSONAL_BASELINE_UNAVAILABLE"
            ),
            source = "FALLBACK",
            center = null,
            spread = null,
            signedDistance = null,
            normalizedDistance = null,
            reason = "PERSONAL_BASELINE_UNAVAILABLE"
        )
    }

    private fun combineEvidenceComponents(
        components: List<EvidenceComponent>
    ): EvidenceCombination {
        val totalBaseWeight = components.sumOf { it.baseWeight.coerceAtLeast(0.0) }
        if (totalBaseWeight <= 0.0) {
            return EvidenceCombination(
                score = null,
                confidence = 0.0,
                coverage = 0.0,
                usable = false,
                composition = "NO_COMPONENT_WEIGHT"
            )
        }

        val usable = components.filter {
            it.usable &&
                    it.score.isFinite() &&
                    it.confidence.isFinite() &&
                    it.confidence > 0.0 &&
                    it.baseWeight > 0.0
        }

        if (usable.isEmpty()) {
            return EvidenceCombination(
                score = null,
                confidence = 0.0,
                coverage = 0.0,
                usable = false,
                composition = components.joinToString("|") {
                    "${it.name}=UNAVAILABLE${it.reason?.let { reason -> ":$reason" } ?: ""}"
                }
            )
        }

        val effectiveWeight = usable.sumOf {
            it.baseWeight * it.confidence.coerceIn(0.0, 1.0)
        }
        val score = if (effectiveWeight <= 0.0) {
            null
        } else {
            usable.sumOf {
                it.score.coerceIn(0.0, 1.0) *
                        it.baseWeight *
                        it.confidence.coerceIn(0.0, 1.0)
            } / effectiveWeight
        }

        val confidence = usable.sumOf {
            it.baseWeight * it.confidence.coerceIn(0.0, 1.0)
        } / totalBaseWeight

        val coverage = usable.sumOf { it.baseWeight } / totalBaseWeight

        return EvidenceCombination(
            score = score?.coerceIn(0.0, 1.0),
            confidence = confidence.coerceIn(0.0, 1.0),
            coverage = coverage.coerceIn(0.0, 1.0),
            usable = score != null,
            composition = components.joinToString("|") {
                if (it.usable) {
                    "${it.name}=USED(w=${"%.2f".format(it.baseWeight)},q=${"%.2f".format(it.confidence)})"
                } else {
                    "${it.name}=SKIP${it.reason?.let { reason -> ":$reason" } ?: ""}"
                }
            }
        )
    }

    private fun buildMetricEvidence(
        combination: EvidenceCombination,
        baseline: BaselineEvidenceDetail?,
        trendScore: Double?,
        signalQuality: Double,
        extraReasons: List<String> = emptyList(),
        logPrefix: String
    ): MetricEvidence {
        val reasons = buildList {
            baseline?.reason?.let(::add)
            addAll(extraReasons.filter { it.isNotBlank() })
        }.distinct()

        return MetricEvidence(
            score = combination.score?.times(100.0),
            confidence = combination.confidence,
            coverage = combination.coverage,
            usable = combination.usable,
            baselineSource = baseline?.source ?: "NONE",
            baselineCenter = baseline?.center,
            baselineSpread = baseline?.spread,
            signedDistance = baseline?.signedDistance,
            normalizedDistance = baseline?.normalizedDistance,
            baselineScore = baseline?.component?.score?.times(100.0),
            trendScore = trendScore?.times(100.0),
            signalQuality = signalQuality.coerceIn(0.0, 1.0),
            reasons = reasons.takeIf { it.isNotEmpty() }?.joinToString("|"),
            log = "$logPrefix score=${combination.score?.let { "%.3f".format(it) } ?: "N/A"}, " +
                    "confidence=${"%.3f".format(combination.confidence)}, " +
                    "coverage=${"%.3f".format(combination.coverage)}, " +
                    "composition=${combination.composition}"
        )
    }

    private fun buildMicroEvidence(result: MicroMovementResult?): MetricEvidence {
        if (result == null) {
            return MetricEvidence(
                reasons = "MICRO_MOVEMENT_UNAVAILABLE",
                log = "Micro evidence unavailable"
            )
        }

        val score = result.score.coerceIn(0.0, 1.0)
        return MetricEvidence(
            score = score * 100.0,
            confidence = 1.0,
            coverage = 1.0,
            usable = true,
            baselineSource = "DIRECT",
            baselineScore = score * 100.0,
            signalQuality = 1.0,
            log = "Micro evidence score=${"%.3f".format(score)}, level=${result.level}"
        )
    }

    private fun buildRrEvidence(
        result: RespiratoryRateArousalResult?,
        fusion: RrFusionResult
    ): MetricEvidence {
        val current = result?.currentRrBpm ?: fusion.rrBpm
        val signalQuality = fusion.confidence.coerceIn(0.0, 1.0)
        val baseline = buildBaselineEvidence(
            metricType = BaselineMetricType.RR,
            value = current,
            signalQuality = signalQuality,
            direction = EvidenceDirection.HIGHER_ONLY,
            minimumScale = config.evidenceScoring.rrEvidenceMinimumScaleBpm,
            fallbackScore = result?.absoluteScore,
            componentName = "BASELINE",
            baseWeight = config.evidenceScoring.rrBaselineEvidenceWeight
        )

        val trend = result?.riseScore
            ?.takeIf { it.isFinite() }
            ?.coerceIn(0.0, 1.0)
        val historyCoverage = result?.let {
            val sampleCoverage =
                (it.sampleCount.toDouble() / config.rrScoreMinSampleCount.coerceAtLeast(1))
                    .coerceIn(0.0, 1.0)
            val timeCoverage =
                ((it.windowSeconds ?: 0.0) * 1000.0 / config.rrScoreMinWindowMillis.coerceAtLeast(1L))
                    .coerceIn(0.0, 1.0)
            (sampleCoverage * 0.4 + timeCoverage * 0.6).coerceIn(0.0, 1.0)
        } ?: 0.0

        val trendComponent = EvidenceComponent(
            name = "TREND",
            score = trend ?: 0.0,
            confidence = (signalQuality * 0.6 + historyCoverage * 0.4).coerceIn(0.0, 1.0),
            baseWeight = config.evidenceScoring.rrTrendEvidenceWeight,
            usable = trend != null,
            reason = if (trend == null) "RR_TREND_UNAVAILABLE" else null
        )

        val combination = combineEvidenceComponents(
            listOf(
                baseline.component ?: EvidenceComponent(
                    name = "BASELINE",
                    score = 0.0,
                    confidence = 0.0,
                    baseWeight = config.evidenceScoring.rrBaselineEvidenceWeight,
                    usable = false,
                    reason = baseline.reason
                ),
                trendComponent
            )
        )

        return buildMetricEvidence(
            combination = combination,
            baseline = baseline,
            trendScore = trend,
            signalQuality = signalQuality,
            extraReasons = listOfNotNull(
                if (fusion.rrBpm == null) "RR_FUSION_UNAVAILABLE" else null,
                if (trend == null) "RR_TREND_UNAVAILABLE" else null
            ),
            logPrefix = "RR evidence"
        )
    }

    private fun appendRrvEvidenceHistory(
        nowMillis: Long,
        result: RrvResult?
    ) {
        val value = result?.rmssdSec ?: return
        if (!value.isFinite() || value < 0.0) return
        rrvValueBuffer.add(nowMillis to value)
        while (
            rrvValueBuffer.isNotEmpty() &&
            nowMillis - rrvValueBuffer.first().first > config.evidenceScoring.rrvTrendWindowMillis
        ) {
            rrvValueBuffer.removeFirst()
        }
    }

    private fun calculateRrvTrendEvidence(): Pair<Double, Double>? {
        if (rrvValueBuffer.size < config.evidenceScoring.rrvTrendMinSampleCount) return null
        val duration = rrvValueBuffer.last().first - rrvValueBuffer.first().first
        if (duration < config.evidenceScoring.rrvTrendMinWindowMillis) return null

        val split = (rrvValueBuffer.size / 3).coerceAtLeast(1)
        val previous = rrvValueBuffer.take(split).map { it.second }.average()
        val recent = rrvValueBuffer.takeLast(split).map { it.second }.average()
        if (!previous.isFinite() || !recent.isFinite()) return null

        val scale = max(
            personalBaselines[BaselineMetricType.RRV]
                ?.spread
                ?.takeIf { it.isFinite() && it > 0.0 }
                ?.times(1.4826)
                ?: 0.0,
            config.evidenceScoring.rrvEvidenceMinimumScaleSec
        )
        val normalized = abs(recent - previous) / scale.coerceAtLeast(1e-9)
        val score = hillEvidenceScore(normalized)
        val sampleCoverage =
            (rrvValueBuffer.size.toDouble() / config.evidenceScoring.rrvTrendMinSampleCount.coerceAtLeast(1))
                .coerceIn(0.0, 1.0)
        val timeCoverage =
            (duration.toDouble() / config.evidenceScoring.rrvTrendWindowMillis.coerceAtLeast(1L))
                .coerceIn(0.0, 1.0)
        return score to (sampleCoverage * 0.4 + timeCoverage * 0.6).coerceIn(0.0, 1.0)
    }

    private fun buildRrvEvidence(result: RrvResult?): MetricEvidence {
        val signalQuality = result?.qualityScore?.coerceIn(0.0, 1.0) ?: 0.0
        val baseline = buildBaselineEvidence(
            metricType = BaselineMetricType.RRV,
            value = result?.rmssdSec,
            signalQuality = signalQuality,
            direction = EvidenceDirection.TWO_SIDED,
            minimumScale = config.evidenceScoring.rrvEvidenceMinimumScaleSec,
            fallbackScore = result?.score,
            componentName = "BASELINE",
            baseWeight = config.evidenceScoring.rrvBaselineEvidenceWeight
        )

        val trendPair = calculateRrvTrendEvidence()
        val trend = trendPair?.first
        val trendComponent = EvidenceComponent(
            name = "TREND",
            score = trend ?: 0.0,
            confidence = (
                    signalQuality * 0.60 +
                            (trendPair?.second ?: 0.0) * 0.40
                    ).coerceIn(0.0, 1.0),
            baseWeight = config.evidenceScoring.rrvTrendEvidenceWeight,
            usable = trend != null,
            reason = if (trend == null) "RRV_TREND_UNAVAILABLE" else null
        )
        val combination = combineEvidenceComponents(
            listOf(
                baseline.component ?: EvidenceComponent(
                    name = "BASELINE",
                    score = 0.0,
                    confidence = 0.0,
                    baseWeight = config.evidenceScoring.rrvBaselineEvidenceWeight,
                    usable = false,
                    reason = baseline.reason
                ),
                trendComponent
            )
        )
        return buildMetricEvidence(
            combination = combination,
            baseline = baseline,
            trendScore = trend,
            signalQuality = signalQuality,
            extraReasons = listOfNotNull(
                if (result == null) "RRV_UNAVAILABLE" else null,
                if (trend == null) "RRV_TREND_UNAVAILABLE" else null
            ),
            logPrefix = "RRV evidence"
        )
    }

    private fun buildHrEvidence(
        result: HeartRateArousalResult?,
        heartRateEstimate: HeartRateEstimate?
    ): MetricEvidence {
        val signalQuality = heartRateEstimate?.qualityScore?.coerceIn(0.0, 1.0) ?: 0.0
        val current = result?.currentBpm?.toDouble() ?: heartRateEstimate?.bpm?.toDouble()
        val baseline = buildBaselineEvidence(
            metricType = BaselineMetricType.HR,
            value = current,
            signalQuality = signalQuality,
            direction = EvidenceDirection.HIGHER_ONLY,
            minimumScale = config.evidenceScoring.hrEvidenceMinimumScaleBpm,
            fallbackScore = null,
            componentName = "BASELINE",
            baseWeight = config.evidenceScoring.hrBaselineEvidenceWeight
        )

        val trend = result?.score
            ?.takeIf { it.isFinite() }
            ?.coerceIn(0.0, 1.0)
        val historyCoverage = result?.let {
            val sampleCoverage =
                (it.sampleCount.toDouble() / config.hrMinSampleCount.coerceAtLeast(1))
                    .coerceIn(0.0, 1.0)
            val timeCoverage =
                (it.windowSeconds * 1000.0 / config.hrGradientMinWindowMillis.coerceAtLeast(1L))
                    .coerceIn(0.0, 1.0)
            (sampleCoverage * 0.4 + timeCoverage * 0.6).coerceIn(0.0, 1.0)
        } ?: 0.0
        val trendComponent = EvidenceComponent(
            name = "TREND",
            score = trend ?: 0.0,
            confidence = (
                    signalQuality * 0.60 +
                            historyCoverage * 0.40
                    ).coerceIn(0.0, 1.0),
            baseWeight = config.evidenceScoring.hrTrendEvidenceWeight,
            usable = trend != null,
            reason = if (trend == null) "HR_TREND_UNAVAILABLE" else null
        )
        val combination = combineEvidenceComponents(
            listOf(
                baseline.component ?: EvidenceComponent(
                    name = "BASELINE",
                    score = 0.0,
                    confidence = 0.0,
                    baseWeight = config.evidenceScoring.hrBaselineEvidenceWeight,
                    usable = false,
                    reason = baseline.reason
                ),
                trendComponent
            )
        )
        return buildMetricEvidence(
            combination = combination,
            baseline = baseline,
            trendScore = trend,
            signalQuality = signalQuality,
            extraReasons = listOfNotNull(
                if (current == null) "HR_UNAVAILABLE" else null,
                if (trend == null) "HR_TREND_UNAVAILABLE" else null
            ),
            logPrefix = "HR evidence"
        )
    }

    private fun buildLogRatioBaselineEvidence(
        value: Double?,
        signalQuality: Double,
        fallbackScore: Double?,
        baseWeight: Double
    ): BaselineEvidenceDetail {
        if (value == null || !value.isFinite() || value <= 0.0) {
            return BaselineEvidenceDetail(
                component = null,
                source = "NONE",
                center = null,
                spread = null,
                signedDistance = null,
                normalizedDistance = null,
                reason = "LF_HF_VALUE_INVALID"
            )
        }

        val baseline = personalBaselines[BaselineMetricType.HRV_LF_HF]
            ?.takeIf {
                it.isUsable &&
                        it.center?.isFinite() == true &&
                        it.center > 0.0 &&
                        it.spread?.isFinite() == true
            }

        if (baseline == null) {
            return buildBaselineEvidence(
                metricType = BaselineMetricType.HRV_LF_HF,
                value = value,
                signalQuality = signalQuality,
                direction = EvidenceDirection.HIGHER_ONLY,
                minimumScale = config.evidenceScoring.hrvLfHfEvidenceMinimumScaleLogRatio,
                fallbackScore = fallbackScore,
                componentName = "LF_HF",
                baseWeight = baseWeight
            )
        }

        val center = baseline.center ?: return BaselineEvidenceDetail(
            null, "NONE", null, null, null, null, "LF_HF_BASELINE_INVALID"
        )
        val spread = baseline.spread ?: 0.0
        val logValue = ln(value)
        val logCenter = ln(center)
        val upper = (center + spread).coerceAtLeast(center * 1.000001)
        val approximateLogMad = abs(ln(upper) - logCenter)
        val scale = max(
            1.4826 * approximateLogMad,
            config.evidenceScoring.hrvLfHfEvidenceMinimumScaleLogRatio
        )
        val normalized = (logValue - logCenter) / scale.coerceAtLeast(1e-9)
        val directed = max(0.0, normalized)
        val score = hillEvidenceScore(directed)
        val confidence = (
                signalQuality.coerceIn(0.0, 1.0) * 0.65 +
                        baseline.confidence.coerceIn(0.0, 1.0) * 0.35
                ).coerceIn(0.0, 1.0)

        return BaselineEvidenceDetail(
            component = EvidenceComponent(
                name = "LF_HF",
                score = score,
                confidence = confidence,
                baseWeight = baseWeight,
                usable = true
            ),
            source = "PERSONAL_LOG",
            center = center,
            spread = spread,
            signedDistance = value - center,
            normalizedDistance = normalized,
            reason = null
        )
    }

    private fun buildHrvEvidence(
        frequencyResult: HrvFrequencyResult?,
        rmssdResult: HeartRateVariabilityResult?,
        assessment: HrvFrequencyAssessment
    ): HrvEvidenceBundle {
        val frequencyDetail = if (assessment.usable && frequencyResult != null) {
            buildLogRatioBaselineEvidence(
                value = frequencyResult.lfHfRatio,
                signalQuality = frequencyResult.qualityScore,
                fallbackScore = frequencyResult.score,
                baseWeight = config.evidenceScoring.hrvFrequencyEvidenceWeight
            )
        } else {
            BaselineEvidenceDetail(
                component = null,
                source = "NONE",
                center = null,
                spread = null,
                signedDistance = null,
                normalizedDistance = null,
                reason = assessment.rejectionCodeString ?: "LF_HF_UNAVAILABLE"
            )
        }

        // 개인 기준선이 없을 때는 낮은 RMSSD일수록 각성점수가 커지는 fallback을 사용한다.
        val rmssdFallback = rmssdResult?.let {
            if (config.hrvRmssdScoreThresholdMs <= 0.0) {
                null
            } else {
                ((config.hrvRmssdScoreThresholdMs - it.rmssdMs) /
                        config.hrvRmssdScoreThresholdMs)
                    .coerceIn(0.0, 1.0)
            }
        }
        val rmssdDetail = buildBaselineEvidence(
            metricType = BaselineMetricType.HRV_RMSSD,
            value = rmssdResult?.rmssdSec,
            signalQuality = rmssdResult?.qualityScore ?: 0.0,
            direction = EvidenceDirection.LOWER_ONLY,
            minimumScale = config.evidenceScoring.hrvRmssdEvidenceMinimumScaleSec,
            fallbackScore = rmssdFallback,
            componentName = "RMSSD",
            baseWeight = config.evidenceScoring.hrvRmssdEvidenceWeight
        )

        val components = listOfNotNull(
            frequencyDetail.component,
            rmssdDetail.component
        ) + listOfNotNull(
            if (frequencyDetail.component == null) {
                EvidenceComponent(
                    name = "LF_HF",
                    score = 0.0,
                    confidence = 0.0,
                    baseWeight = config.evidenceScoring.hrvFrequencyEvidenceWeight,
                    usable = false,
                    reason = frequencyDetail.reason
                )
            } else null,
            if (rmssdDetail.component == null) {
                EvidenceComponent(
                    name = "RMSSD",
                    score = 0.0,
                    confidence = 0.0,
                    baseWeight = config.evidenceScoring.hrvRmssdEvidenceWeight,
                    usable = false,
                    reason = rmssdDetail.reason
                )
            } else null
        )

        val combination = combineEvidenceComponents(components)
        val reasons = listOfNotNull(
            frequencyDetail.reason,
            rmssdDetail.reason
        )
        val evidence = MetricEvidence(
            score = combination.score?.times(100.0),
            confidence = combination.confidence,
            coverage = combination.coverage,
            usable = combination.usable,
            baselineSource = "LF_HF=${frequencyDetail.source};RMSSD=${rmssdDetail.source}",
            baselineCenter = frequencyDetail.center,
            baselineSpread = frequencyDetail.spread,
            signedDistance = frequencyDetail.signedDistance,
            normalizedDistance = frequencyDetail.normalizedDistance,
            baselineScore = frequencyDetail.component?.score?.times(100.0),
            trendScore = rmssdDetail.component?.score?.times(100.0),
            signalQuality = (
                    (frequencyResult?.qualityScore ?: 0.0) * config.evidenceScoring.hrvFrequencyEvidenceWeight +
                            (rmssdResult?.qualityScore ?: 0.0) * config.evidenceScoring.hrvRmssdEvidenceWeight
                    ).coerceIn(0.0, 1.0),
            reasons = reasons.takeIf { it.isNotEmpty() }?.joinToString("|"),
            log = "HRV evidence score=${combination.score?.let { "%.3f".format(it) } ?: "N/A"}, " +
                    "confidence=${"%.3f".format(combination.confidence)}, " +
                    "coverage=${"%.3f".format(combination.coverage)}, " +
                    "composition=${combination.composition}"
        )

        return HrvEvidenceBundle(
            evidence = evidence,
            frequencyScore = frequencyDetail.component?.score,
            rmssdScore = rmssdDetail.component?.score,
            composition = combination.composition,
            log = evidence.log ?: "HRV evidence unavailable"
        )
    }

    private fun buildTemperatureEvidence(result: SkinTemperatureResult?): MetricEvidence {
        val signalQuality = result?.qualityScore?.coerceIn(0.0, 1.0) ?: 0.0
        val baseline = buildBaselineEvidence(
            metricType = BaselineMetricType.TEMPERATURE,
            value = result?.currentCelsius,
            signalQuality = signalQuality,
            direction = EvidenceDirection.HIGHER_ONLY,
            minimumScale = config.evidenceScoring.temperatureEvidenceMinimumScaleCelsius,
            fallbackScore = null,
            componentName = "BASELINE",
            baseWeight = config.evidenceScoring.temperatureBaselineEvidenceWeight
        )
        val trend = result?.score
            ?.takeIf { it.isFinite() }
            ?.coerceIn(0.0, 1.0)
        val trendComponent = EvidenceComponent(
            name = "TREND",
            score = trend ?: 0.0,
            confidence = signalQuality,
            baseWeight = config.evidenceScoring.temperatureTrendEvidenceWeight,
            usable = trend != null,
            reason = if (trend == null) "TEMPERATURE_TREND_UNAVAILABLE" else null
        )
        val combination = combineEvidenceComponents(
            listOf(
                baseline.component ?: EvidenceComponent(
                    name = "BASELINE",
                    score = 0.0,
                    confidence = 0.0,
                    baseWeight = config.evidenceScoring.temperatureBaselineEvidenceWeight,
                    usable = false,
                    reason = baseline.reason
                ),
                trendComponent
            )
        )
        return buildMetricEvidence(
            combination = combination,
            baseline = baseline,
            trendScore = trend,
            signalQuality = signalQuality,
            extraReasons = listOfNotNull(
                if (result == null) "TEMPERATURE_UNAVAILABLE" else null,
                if (trend == null) "TEMPERATURE_TREND_UNAVAILABLE" else null
            ),
            logPrefix = "Temperature evidence"
        )
    }

    private fun metricEvidenceComponent(
        name: String,
        evidence: MetricEvidence,
        baseWeight: Double
    ): EvidenceComponent {
        return EvidenceComponent(
            name = name,
            score = (evidence.score ?: 0.0) / 100.0,
            confidence = evidence.confidence.coerceIn(0.0, 1.0),
            baseWeight = baseWeight,
            usable = evidence.usable && evidence.score != null,
            reason = evidence.reasons
        )
    }

    private fun EvidenceCombination.toDomainEvidence(): DomainEvidence = DomainEvidence(
        score = score?.times(100.0),
        confidence = confidence.coerceIn(0.0, 1.0),
        coverage = coverage.coerceIn(0.0, 1.0),
        usable = usable,
        composition = composition
    )

    private fun calculateFinalArousalResult(
        nowMillis: Long,
        micro: MetricEvidence,
        rr: MetricEvidence,
        rrv: MetricEvidence,
        hr: MetricEvidence,
        hrv: MetricEvidence,
        temperature: MetricEvidence
    ): FinalArousalResult {
        val movementDomain = combineEvidenceComponents(
            listOf(metricEvidenceComponent("MICRO", micro, 1.0))
        )
        val respiratoryDomain = combineEvidenceComponents(
            listOf(
                metricEvidenceComponent("RR", rr, config.evidenceScoring.rrArousalMetricWeight),
                metricEvidenceComponent("RRV", rrv, config.evidenceScoring.rrvArousalMetricWeight)
            )
        )
        val cardiacDomain = combineEvidenceComponents(
            listOf(
                metricEvidenceComponent("HR", hr, config.evidenceScoring.hrArousalMetricWeight),
                metricEvidenceComponent("HRV", hrv, config.evidenceScoring.hrvArousalMetricWeight)
            )
        )
        val temperatureDomain = combineEvidenceComponents(
            listOf(metricEvidenceComponent("TEMP", temperature, 1.0))
        )

        val domainComponents = listOf(
            EvidenceComponent(
                "MOVEMENT",
                movementDomain.score ?: 0.0,
                movementDomain.confidence,
                config.evidenceScoring.movementArousalDomainWeight,
                movementDomain.usable,
                if (movementDomain.usable) null else movementDomain.composition
            ),
            EvidenceComponent(
                "RESPIRATORY",
                respiratoryDomain.score ?: 0.0,
                respiratoryDomain.confidence,
                config.evidenceScoring.respiratoryArousalDomainWeight,
                respiratoryDomain.usable,
                if (respiratoryDomain.usable) null else respiratoryDomain.composition
            ),
            EvidenceComponent(
                "CARDIAC",
                cardiacDomain.score ?: 0.0,
                cardiacDomain.confidence,
                config.evidenceScoring.cardiacArousalDomainWeight,
                cardiacDomain.usable,
                if (cardiacDomain.usable) null else cardiacDomain.composition
            ),
            EvidenceComponent(
                "TEMPERATURE",
                temperatureDomain.score ?: 0.0,
                temperatureDomain.confidence,
                config.evidenceScoring.temperatureArousalDomainWeight,
                temperatureDomain.usable,
                if (temperatureDomain.usable) null else temperatureDomain.composition
            )
        )

        val finalCombination = combineEvidenceComponents(domainComponents)
        val score = finalCombination.score ?: 0.0
        val confidence = finalCombination.confidence
        val coverage = finalCombination.coverage
        val usedDomainCount = domainComponents.count { it.usable }

        val scorePassed = score * 100.0 >= config.wakeCandidateScore
        val confidencePassed = confidence >= config.evidenceScoring.wakeCandidateMinConfidence
        val domainPassed = usedDomainCount >= config.evidenceScoring.wakeCandidateMinimumDomainCount
        val currentPassed = scorePassed && confidencePassed && domainPassed

        val persistence = updateWakePersistence(
            nowMillis = nowMillis,
            currentPassed = currentPassed
        )
        val candidate = persistence.candidate

        val currentFailureReason = when {
            !scorePassed ->
                "점수 부족 ${"%.1f".format(score * 100.0)}/${"%.1f".format(config.wakeCandidateScore)}"
            !confidencePassed ->
                "신뢰도 부족 ${"%.1f".format(confidence * 100.0)}/${"%.1f".format(config.evidenceScoring.wakeCandidateMinConfidence * 100.0)}"
            !domainPassed ->
                "사용 영역 부족 $usedDomainCount/${config.evidenceScoring.wakeCandidateMinimumDomainCount}"
            else -> "현재 조건 통과"
        }

        val reason = when {
            !persistence.windowComplete ->
                "Tolerant persistence 수집 중 " +
                        "${persistence.observedSeconds}/${persistence.windowSeconds}초 · " +
                        "통과 ${persistence.passedSeconds}/${persistence.requiredPassSeconds}초 · " +
                        "현재 ${if (currentPassed) "통과" else "실패"}"
            candidate ->
                "최근 ${persistence.windowSeconds}초 중 ${persistence.passedSeconds}초 통과 " +
                        "(필요 ${persistence.requiredPassSeconds}초) · " +
                        "현재 ${if (currentPassed) "통과" else "일시 실패 허용"}"
            else ->
                "최근 ${persistence.windowSeconds}초 통과 부족 " +
                        "${persistence.passedSeconds}/${persistence.requiredPassSeconds}초 · " +
                        "실패 ${persistence.failedSeconds}초 · 현재: $currentFailureReason"
        }

        return FinalArousalResult(
            score = score.coerceIn(0.0, 1.0),
            confidence = confidence.coerceIn(0.0, 1.0),
            coverage = coverage.coerceIn(0.0, 1.0),
            movementDomain = movementDomain.toDomainEvidence(),
            respiratoryDomain = respiratoryDomain.toDomainEvidence(),
            cardiacDomain = cardiacDomain.toDomainEvidence(),
            temperatureDomain = temperatureDomain.toDomainEvidence(),
            usedDomainCount = usedDomainCount,
            currentConditionPassed = currentPassed,
            persistenceWindowSeconds = persistence.windowSeconds,
            persistenceRequiredPassSeconds = persistence.requiredPassSeconds,
            persistenceObservedSeconds = persistence.observedSeconds,
            persistencePassedSeconds = persistence.passedSeconds,
            persistenceFailedSeconds = persistence.failedSeconds,
            persistencePassRatio = persistence.passRatio,
            candidate = candidate,
            reason = reason,
            log = "Final evidence score=${"%.3f".format(score)}, " +
                    "confidence=${"%.3f".format(confidence)}, " +
                    "coverage=${"%.3f".format(coverage)}, domains=$usedDomainCount, " +
                    "currentPassed=$currentPassed, " +
                    "persistence=${persistence.passedSeconds}/${persistence.windowSeconds}, " +
                    "required=${persistence.requiredPassSeconds}, " +
                    "observed=${persistence.observedSeconds}, failed=${persistence.failedSeconds}, " +
                    "candidate=$candidate, reason=$reason"
        )
    }

    /**
     * 같은 초에 여러 번 호출되면 마지막 판정으로 덮어쓰고,
     * 최근 windowSeconds 범위에 실제 관측된 1초 bucket만 유지한다.
     * 누락된 초는 관측 초 수를 채우지 못하므로 후보 판정에 기여하지 않는다.
     */
    private fun updateWakePersistence(
        nowMillis: Long,
        currentPassed: Boolean
    ): WakePersistenceSummary {
        val windowSeconds = config.evidenceScoring.wakePersistenceWindowSeconds.coerceAtLeast(1)
        val requiredPassSeconds = config.evidenceScoring.wakePersistenceRequiredPassSeconds
            .coerceIn(1, windowSeconds)
        val currentSecond = nowMillis / 1000L

        if (wakePersistenceSamples.lastOrNull()?.secondBucket == currentSecond) {
            wakePersistenceSamples.removeLast()
        }
        wakePersistenceSamples.add(
            WakePersistenceSample(
                secondBucket = currentSecond,
                passed = currentPassed
            )
        )

        val minimumSecond = currentSecond - windowSeconds + 1L
        while (
            wakePersistenceSamples.isNotEmpty() &&
            wakePersistenceSamples.first().secondBucket < minimumSecond
        ) {
            wakePersistenceSamples.removeFirst()
        }

        val observedSeconds = wakePersistenceSamples.size
        val passedSeconds = wakePersistenceSamples.count { it.passed }
        val failedSeconds = observedSeconds - passedSeconds
        val passRatio = if (observedSeconds > 0) {
            passedSeconds.toDouble() / observedSeconds.toDouble()
        } else {
            0.0
        }
        val windowComplete = observedSeconds >= windowSeconds
        val candidate = windowComplete && passedSeconds >= requiredPassSeconds

        return WakePersistenceSummary(
            windowSeconds = windowSeconds,
            requiredPassSeconds = requiredPassSeconds,
            observedSeconds = observedSeconds,
            passedSeconds = passedSeconds,
            failedSeconds = failedSeconds,
            passRatio = passRatio.coerceIn(0.0, 1.0),
            windowComplete = windowComplete,
            candidate = candidate
        )
    }

    /**
     * 과거 threshold 기반 최종 점수 계산기.
     * 새 경로에서는 calculateFinalArousalResult()를 사용하며 이 함수는 비교/회귀 확인용으로만 남긴다.
     *
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

    /** Potch510 Green PPG uint16 LE samples를 단일 rolling buffer에 저장한다. */
    private fun appendPpg(ppgData: ByteArray) {
        val greenSamples = extractGreenSamples(ppgData)

        for (green in greenSamples) {
            greenPpgBuffer.add(green.toDouble())
            if (greenPpgBuffer.size > maxPpgSamples) greenPpgBuffer.removeFirst()
        }

        totalPpgRespSampleCount += greenSamples.size.toLong()
    }

    /**
     * Potch510 IMU 6축 sample(12B)에서 앞의 가속도 XYZ를 g-magnitude로 변환한다.
     * 뒤의 gyro XYZ는 현재 각성도 계산에는 사용하지 않지만 packet 정렬을 위해 건너뛴다.
     */
    private fun appendImu(imuData: ByteArray) {
        for (i in imuData.indices step 12) {
            if (i + 11 >= imuData.size) break

            val xRaw = readInt16LE(imuData, i)
            val yRaw = readInt16LE(imuData, i + 2)
            val zRaw = readInt16LE(imuData, i + 4)

            val xG = xRaw / config.imuLsbPerG
            val yG = yRaw / config.imuLsbPerG
            val zG = zRaw / config.imuLsbPerG

            val gMagnitude = sqrt(xG * xG + yG * yG + zG * zG)

            imuGBuffer.add(gMagnitude)
            if (imuGBuffer.size > maxImuSamples) imuGBuffer.removeFirst()

            val microFiltered = microBpf.filter(gMagnitude)
            microFilteredBuffer.add(microFiltered)
            if (microFilteredBuffer.size > maxImuSamples) microFilteredBuffer.removeFirst()

            totalImuRespSampleCount += 1L
        }
    }

    /**
     * NTC에서 계산된 피부온도를 시간과 함께 저장한다.
     *
     * SensorData.ntcCelsius가 NaN/무한대이면 계산 불가능한 값으로 보고 버린다.
     */
    private fun appendTemperature(
        timestampMillis: Long,
        celsius: Double
    ): Boolean {
        if (!celsius.isFinite()) return false
        if (celsius < 0.0 || celsius > 60.0) return false

        temperatureBuffer.add(timestampMillis to celsius)
        lastValidTemperatureTimestampMillis = timestampMillis
        temperatureBufferExpiredByGap = false

        trimTimeBuffer(
            buffer = temperatureBuffer,
            nowMillis = timestampMillis,
            windowMillis = config.temperatureWindowMillis
        )
        return true
    }

    /**
     * 기존 estimateHeartRate()에서 계산된 bpm을 시간과 함께 저장한다.
     */
    private fun appendHeartRate(
        timestampMillis: Long,
        bpm: Int
    ): Boolean {
        if (bpm !in config.hrMinReasonableBpm..config.hrMaxReasonableBpm) return false

        heartRateBuffer.add(timestampMillis to bpm)
        lastValidHeartRateTimestampMillis = timestampMillis
        heartRateBufferExpiredByGap = false

        trimTimeBuffer(
            buffer = heartRateBuffer,
            nowMillis = timestampMillis,
            windowMillis = config.heartRateWindowMillis
        )
        return true
    }

    private fun appendRespirationRate(
        timestampMillis: Long,
        rrBpm: Double,
        confidence: Double
    ): Boolean {
        if (rrBpm !in config.rrMinBpm..config.rrMaxBpm) return false
        if (confidence < config.rrScoreMinUsableConfidence) return false

        respirationRateBuffer.add(timestampMillis to rrBpm)
        lastValidRespirationTimestampMillis = timestampMillis
        respirationBufferExpiredByGap = false

        trimTimeBuffer(
            buffer = respirationRateBuffer,
            nowMillis = timestampMillis,
            windowMillis = config.rrHistoryWindowMillis
        )
        return true
    }

    /** 현재 시각 기준으로 시간형 rolling buffer를 매 프레임 정리한다. */
    private fun trimMetricBuffersNow(nowMillis: Long) {
        trimTimeBuffer(heartRateBuffer, nowMillis, config.heartRateWindowMillis)
        trimTimeBuffer(respirationRateBuffer, nowMillis, config.rrHistoryWindowMillis)
        trimTimeBuffer(temperatureBuffer, nowMillis, config.temperatureWindowMillis)
    }

    /** 마지막 유효값 이후 허용 시간을 넘긴 history는 전부 폐기한다. */
    private fun expireStaleMetricBuffers(nowMillis: Long) {
        if (hasExpired(lastValidHeartRateTimestampMillis, nowMillis, config.hrFreshnessTimeoutMillis)) {
            heartRateBuffer.clear()
            lastValidHeartRateTimestampMillis = null
            heartRateBufferExpiredByGap = true
        }

        if (hasExpired(lastValidHrvTimestampMillis, nowMillis, config.hrvFreshnessTimeoutMillis)) {
            hrvIbiBuffer.clear()
            lastAcceptedHrvIbiSegmentId = Long.MIN_VALUE
            lastAcceptedHrvIbiEndSamplePosition = Double.NEGATIVE_INFINITY
            lastValidHrvTimestampMillis = null
            hrvBufferExpiredByGap = true
        }

        if (hasExpired(lastValidRespirationTimestampMillis, nowMillis, config.rrFreshnessTimeoutMillis)) {
            respirationRateBuffer.clear()
            rrvValueBuffer.clear()
            clearRespirationVariabilityBuffers(resetSampleCounters = false)
            lastValidRespirationTimestampMillis = null
            respirationBufferExpiredByGap = true
        }

        if (hasExpired(lastValidTemperatureTimestampMillis, nowMillis, config.skinTempFreshnessTimeoutMillis)) {
            temperatureBuffer.clear()
            lastValidTemperatureTimestampMillis = null
            temperatureBufferExpiredByGap = true
        }
    }

    private fun hasExpired(
        lastValidTimestampMillis: Long?,
        nowMillis: Long,
        timeoutMillis: Long
    ): Boolean {
        if (lastValidTimestampMillis == null) return false
        return nowMillis - lastValidTimestampMillis > timeoutMillis
    }

    private fun isFresh(
        lastValidTimestampMillis: Long?,
        nowMillis: Long,
        timeoutMillis: Long
    ): Boolean {
        if (lastValidTimestampMillis == null) return false
        return nowMillis - lastValidTimestampMillis <= timeoutMillis
    }

    /**
     * 디버깅용.
     * 현재 각 buffer에 데이터가 얼마나 쌓였는지 확인할 수 있다.
     */
    fun getBufferSnapshot(): ArousalBufferSnapshot {
        return ArousalBufferSnapshot(
            greenPpgSampleCount = greenPpgBuffer.size,
            imuGSampleCount = imuGBuffer.size,
            temperatureSampleCount = temperatureBuffer.size,
            heartRateSampleCount = heartRateBuffer.size,
            ppgRrvIntervalCount = ppgRrvIntervalBuffer.size,
            imuRrvIntervalCount = imuRrvIntervalBuffer.size,
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

    /** 최근 Green PPG buffer 복사본을 반환한다. */
    fun getGreenPpgBufferCopy(): DoubleArray {
        return greenPpgBuffer.toDoubleArray()
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
    /** Green PPG uint16 little-endian samples. */
    private fun extractGreenSamples(ppgData: ByteArray): IntArray {
        val sampleCount = ppgData.size / 2
        return IntArray(sampleCount) { index ->
            val base = index * 2
            (ppgData[base].toInt() and 0xFF) or
                    ((ppgData[base + 1].toInt() and 0xFF) shl 8)
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
     * packet은 정상 수신됐지만 강한 움직임으로 현재 PPG 구간을 HR에서 제외할 때 호출한다.
     *
     * RR/micro movement의 raw buffer는 유지하고, HRV IBI 연속성만 끊는다.
     * 따라서 움직임 전 IBI와 움직임 후 IBI가 RMSSD에서 이웃 값으로 연결되지 않는다.
     */
    fun onHeartRateDiscontinuity(reason: String): ArousalState {
        hrvIbiBuffer.clear()
        wakePersistenceSamples.clear()
        lastAcceptedHrvIbiSegmentId = Long.MIN_VALUE
        lastAcceptedHrvIbiEndSamplePosition = Double.NEGATIVE_INFINITY
        lastValidHrvTimestampMillis = null
        hrvBufferExpiredByGap = true

        lastState = lastState.copy(
            hrvRmssd = null,
            hrvRmssdMs = null,
            hrvLf = null,
            hrvHf = null,
            hrvLfHf = null,
            hrvScore = null,
            hrvQuality = 0.0,
            hrvRmssdScore = null,
            hrvRmssdQuality = 0.0,
            hrvRmssdIbiCount = 0,
            hrvFrequencyScore = null,
            hrvFrequencyQuality = 0.0,
            hrvFrequencyIbiCount = 0,
            hrvFrequencyUsable = false,
            hrvFrequencyStatus = MetricCalculationStatus(
                state = MetricCalculationState.REJECTED,
                message = "HR artifact 이후 LF/HF 재수집 필요"
            ),
            hrvFrequencyRejectionReasons = HrvFrequencyRejectionReason.HRV_NOT_FRESH.code,
            hrvFrequencyObservedSeconds = 0.0,
            hrvFrequencyRawIbiCount = 0,
            hrvFrequencyCleanedIbiCount = 0,
            hrvFrequencyResampledCount = 0,
            hrvFrequencyPpgSignalQuality = null,
            hrvFrequencyRespiratoryRateBpm = null,
            hrvScoreComposition = "NONE",
            hrvEvidence = MetricEvidence(
                usable = false,
                reasons = "HRV_NOT_FRESH",
                log = "HR artifact 구간으로 HRV evidence 중단: $reason"
            ),
            hrvLog = "HR artifact 구간으로 HRV IBI 연속성 중단: $reason",
            hrvCalculationStatus = MetricCalculationStatus(
                state = MetricCalculationState.REJECTED,
                message = "HR artifact 이후 새 IBI를 다시 수집해야 함"
            ),
            wakeCandidateHoldSeconds = 0,
            wakeCurrentConditionPassed = false,
            wakePersistenceObservedSeconds = 0,
            wakePersistencePassedSeconds = 0,
            wakePersistenceFailedSeconds = 0,
            wakePersistencePassRatio = 0.0,
            wakeDecisionReason = "HR artifact로 tolerant persistence 초기화",
            isWakeTimingCandidate = false
        )

        return lastState
    }

    /**
     * 패킷 누락/CRC 오류로 샘플 연속성이 끊겼을 때 호출한다.
     *
     * sample-domain 신호(PPG/IMU/micro filter)는 즉시 비워 누락 전후 파형이
     * 하나의 연속 신호처럼 처리되지 않게 한다. 시간 기반 history는 손상 프레임을
     * 추가하지 않은 채 유지하지만 freshness를 끊어 이전 결과를 현재 결과로 재사용하지 않는다.
     */
    fun onDataDiscontinuity(
        newSegmentId: Long,
        reason: String
    ): ArousalState {
        currentAnalysisSegmentId = newSegmentId

        greenPpgBuffer.clear()
        imuGBuffer.clear()
        microFilteredBuffer.clear()
        microBpf.reset()
        clearRespirationVariabilityBuffers(resetSampleCounters = true)
        rrvValueBuffer.clear()
        wakePersistenceSamples.clear()
        resetRespirationPathSelection()

        // HRV는 한 개의 연속 segment 안에서만 계산한다.
        hrvIbiBuffer.clear()
        lastAcceptedHrvIbiSegmentId = Long.MIN_VALUE
        lastAcceptedHrvIbiEndSamplePosition = Double.NEGATIVE_INFINITY

        lastValidHeartRateTimestampMillis = null
        lastValidHrvTimestampMillis = null
        lastValidRespirationTimestampMillis = null
        lastValidTemperatureTimestampMillis = null

        heartRateBufferExpiredByGap = true
        hrvBufferExpiredByGap = true
        respirationBufferExpiredByGap = true
        temperatureBufferExpiredByGap = true

        lastState = ArousalState(
            rrCalculationStatus = MetricCalculationStatus(
                state = MetricCalculationState.REJECTED,
                message = "데이터 연속성 중단: $reason"
            ),
            rrvCalculationStatus = MetricCalculationStatus(
                state = MetricCalculationState.REJECTED,
                message = "데이터 연속성 중단으로 현재 호흡 interval 사용 불가"
            ),
            hrCalculationStatus = MetricCalculationStatus(
                state = MetricCalculationState.REJECTED,
                message = "데이터 연속성 중단: $reason"
            ),
            hrvCalculationStatus = MetricCalculationStatus(
                state = MetricCalculationState.REJECTED,
                message = "새 segment($newSegmentId)의 IBI를 다시 수집해야 함"
            ),
            finalWakeScore = 0.0,
            isWakeTimingCandidate = false,
            lastLog = "Analysis continuity break: segment=$newSegmentId, reason=$reason"
        )

        return lastState
    }

    fun getLastState(): ArousalState = lastState

    /**
     * 계산기 내부의 모든 rolling buffer와 상태형 필터를 초기화한다.
     *
     * BLE 연결을 새로 시작하거나 실험을 재시작할 때 이전 데이터가
     * 새 계산에 섞이지 않도록 호출한다.
     */
    fun reset(initialSegmentId: Long = 0L) {
        greenPpgBuffer.clear()
        imuGBuffer.clear()
        microFilteredBuffer.clear()
        temperatureBuffer.clear()
        heartRateBuffer.clear()
        respirationRateBuffer.clear()
        rrvValueBuffer.clear()
        hrvIbiBuffer.clear()
        clearRespirationVariabilityBuffers(resetSampleCounters = true)
        resetRespirationPathSelection()

        currentAnalysisSegmentId = initialSegmentId
        lastAcceptedHrvIbiSegmentId = Long.MIN_VALUE
        lastAcceptedHrvIbiEndSamplePosition = Double.NEGATIVE_INFINITY

        lastValidHeartRateTimestampMillis = null
        lastValidHrvTimestampMillis = null
        lastValidRespirationTimestampMillis = null
        lastValidTemperatureTimestampMillis = null

        heartRateBufferExpiredByGap = false
        hrvBufferExpiredByGap = false
        respirationBufferExpiredByGap = false
        temperatureBufferExpiredByGap = false

        wakePersistenceSamples.clear()
        lastState = ArousalState()
        microBpf.reset()
    }

    /************************** Micro Movement ****************************/

    /*
    private val microBpf = SimpleBandPassFilter(
        sampleRateHz = config.imuSampleRateHz,
        lowCutHz = config.microLowCutHz,
        highCutHz = config.microHighCutHz
    )

     */

    private var microBpf = createMicroBandPassFilter()
    private fun createMicroBandPassFilter(): SimpleBandPassFilter {
        return SimpleBandPassFilter(
            sampleRateHz = config.imuSampleRateHz,
            lowCutHz = config.microLowCutHz,
            highCutHz = config.microHighCutHz
        )
    }

    fun updateMicroMovementBandPass(
        lowCutHz: Double,
        highCutHz: Double
    ) {
        if (lowCutHz <= 0.0) return
        if (highCutHz <= lowCutHz) return
        if (highCutHz >= config.imuSampleRateHz / 2.0) return

        config = config.copy(
            microLowCutHz = lowCutHz,
            microHighCutHz = highCutHz
        )

        rebuildMicroFilteredBuffer()
    }

    private fun rebuildMicroFilteredBuffer() {
        microBpf = createMicroBandPassFilter()
        microFilteredBuffer.clear()

        imuGBuffer.forEach { gMagnitude ->
            val filtered = microBpf.filter(gMagnitude)
            microFilteredBuffer.add(filtered)

            if (microFilteredBuffer.size > maxImuSamples) {
                microFilteredBuffer.removeFirst()
            }
        }
    }

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
            (config.imuSampleRateHz * config.microWindowSeconds).toInt()

        val minSampleCount =
            (config.imuSampleRateHz * config.microMinWindowSeconds).toInt()

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
            windowSeconds = windowValues.size / config.imuSampleRateHz,
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
     * Green PPG 기반 호흡수를 계산한다.
     *
     * positive peak를 primary로 유지하고, 지속 실패한 경우에만 negative peak를
     * 확인 후 전환한다. 광학 채널 간 복제·융합·fallback은 사용하지 않는다.
     */
    fun calculatePpgRespiration(): PpgRespirationResult? {
        val minSampleCount =
            (config.ppgSampleRateHz * config.ppgRespMinWindowSeconds).toInt()

        val candidates = calculatePpgRespirationCandidatesFromBuffer(
            channel = PpgRespirationChannel.GREEN,
            buffer = greenPpgBuffer
        )

        fun analysisFor(
            path: PpgRespirationDetectionPath
        ): PpgRespirationCandidateAnalysis? {
            val available = candidates ?: return null
            return if (path.inverted) available.negative else available.positive
        }

        fun candidateFor(
            path: PpgRespirationDetectionPath
        ): PpgRespirationResult? = analysisFor(path)?.result

        val validSampleCount =
            candidates?.positive?.graphData?.validOriginalSampleCount ?: 0
        val enoughWindow = validSampleCount >= minSampleCount

        if (!enoughWindow) {
            latestPpgRespirationGraphData =
                analysisFor(activePpgRespirationPath)?.graphData
                    ?: PpgRespirationGraphData(
                        channel = PpgRespirationChannel.GREEN,
                        selectedPolarity = activePpgRespirationPath.toPublicPolarity(),
                        minimumWindowSeconds = config.ppgRespMinWindowSeconds,
                        description = "유효 Green PPG 접촉 구간 수집 중"
                    )
            return null
        }

        val selectedResult = selectStablePpgRespirationPath(
            candidateFor = ::candidateFor,
            nowMillis = System.currentTimeMillis()
        )

        val graphPath = selectedResult?.let(::ppgRespirationPathOf)
            ?: activePpgRespirationPath

        latestPpgRespirationGraphData =
            analysisFor(graphPath)?.graphData
                ?: PpgRespirationGraphData(
                    channel = PpgRespirationChannel.GREEN,
                    selectedPolarity = graphPath.toPublicPolarity(),
                    processingState = MetricCalculationState.REJECTED,
                    minimumWindowSeconds = config.ppgRespMinWindowSeconds,
                    description = "선택된 Green RR polarity의 gap-aware 파형을 만들 수 없음"
                )

        return selectedResult
    }

    private fun PpgRespirationDetectionPath.toPublicPolarity(): RespirationPeakPolarity {
        return if (inverted) {
            RespirationPeakPolarity.NEGATIVE
        } else {
            RespirationPeakPolarity.POSITIVE
        }
    }

    private fun ppgRespirationPathOf(
        result: PpgRespirationResult
    ): PpgRespirationDetectionPath {
        return if (result.inverted) {
            PpgRespirationDetectionPath.GREEN_NEGATIVE
        } else {
            PpgRespirationDetectionPath.GREEN_POSITIVE
        }
    }

    /**
     * PPG 기반 RR bpm만 간단히 얻기 위한 helper.
     */
    private fun calculateRrFromPpg(): Double? {
        return calculatePpgRespiration()?.rrBpm
    }

    /**
     * 지정 채널의 최근 45초 raw PPG에서 contact/gap-aware segment를 만들고,
     * segment별로 DC 제거 -> 0.1~0.5Hz BPF -> 2초 warm-up 제거를 적용한다.
     */
    private fun calculatePpgRespirationCandidatesFromBuffer(
        channel: PpgRespirationChannel,
        buffer: ArrayDeque<Double>
    ): PpgRespirationCandidates? {
        val windowSampleCount =
            (config.ppgSampleRateHz * config.ppgRespWindowSeconds).toInt()

        val minSampleCount =
            (config.ppgSampleRateHz * config.ppgRespMinWindowSeconds).toInt()

        val rawWindow =
            if (buffer.size > windowSampleCount) {
                buffer.takeLast(windowSampleCount)
            } else {
                buffer.toList()
            }

        if (rawWindow.isEmpty()) {
            return null
        }

        val windowStartSamplePosition =
            totalPpgRespSampleCount - rawWindow.size.toLong()

        val prepared = prepareGapAwarePpgRespirationWindow(
            rawWindow = rawWindow,
            windowStartSamplePosition = windowStartSamplePosition
        )

        val enoughWindow = prepared.validOriginalSampleCount >= minSampleCount

        return PpgRespirationCandidates(
            positive = analyzeGapAwareRrFromRespSegments(
                channel = channel,
                prepared = prepared,
                invert = false,
                enoughWindow = enoughWindow
            ),
            negative = analyzeGapAwareRrFromRespSegments(
                channel = channel,
                prepared = prepared,
                invert = true,
                enoughWindow = enoughWindow
            )
        )
    }

    /**
     * sample 값 자체가 실제 PPG 접촉값으로 사용할 수 있는지 판정한다.
     * 0/저접촉 값과 ADC 상단 포화값은 RR 파형에 직접 넣지 않는다.
     */
    private fun isValidPpgRespirationSample(value: Double): Boolean {
        return value.isFinite() &&
                value >= config.ppgRespContactMinValue &&
                value < config.ppgRespSaturationHighValue
    }

    /**
     * 짧은/중간 invalid gap은 양쪽 local DC가 비슷할 때만 보간하고,
     * 긴 gap 또는 접촉 수준이 달라진 gap은 segment 경계로 남긴다.
     */
    private fun prepareGapAwarePpgRespirationWindow(
        rawWindow: List<Double>,
        windowStartSamplePosition: Long
    ): GapAwarePpgRespirationWindow {
        val size = rawWindow.size
        val values = rawWindow.toDoubleArray()
        val originalValid = BooleanArray(size) { index ->
            isValidPpgRespirationSample(values[index])
        }
        val preparedValid = originalValid.copyOf()
        val interpolatedMask = BooleanArray(size)
        val peakExcludedMask = BooleanArray(size)
        val mediumGapMask = BooleanArray(size)

        val shortGapMaxSamples =
            (config.ppgSampleRateHz * config.ppgRespShortGapMaxSeconds)
                .toInt()
                .coerceAtLeast(1)
        val mediumGapMaxSamples =
            (config.ppgSampleRateHz * config.ppgRespMediumGapMaxSeconds)
                .toInt()
                .coerceAtLeast(shortGapMaxSamples)
        val peakExclusionMarginSamples =
            (config.ppgSampleRateHz * config.ppgRespPeakExclusionMarginSeconds)
                .toInt()
                .coerceAtLeast(0)

        fun localMedian(startInclusive: Int, endExclusive: Int): Double? {
            if (startInclusive >= endExclusive) return null
            val local = mutableListOf<Double>()
            for (index in startInclusive until endExclusive) {
                if (index in values.indices && originalValid[index]) {
                    local.add(values[index])
                }
            }
            if (local.isEmpty()) return null
            local.sort()
            val middle = local.size / 2
            return if (local.size % 2 == 0) {
                (local[middle - 1] + local[middle]) / 2.0
            } else {
                local[middle]
            }
        }

        fun edgeLevelsAreCompatible(
            gapStart: Int,
            gapEndExclusive: Int
        ): Boolean {
            val localSpan = (config.ppgSampleRateHz * 0.20).toInt().coerceAtLeast(3)
            val leftLevel = localMedian(
                startInclusive = (gapStart - localSpan).coerceAtLeast(0),
                endExclusive = gapStart
            ) ?: return false
            val rightLevel = localMedian(
                startInclusive = gapEndExclusive,
                endExclusive = (gapEndExclusive + localSpan).coerceAtMost(size)
            ) ?: return false

            val denominator =
                maxOf(
                    (kotlin.math.abs(leftLevel) + kotlin.math.abs(rightLevel)) / 2.0,
                    config.ppgRespContactMinValue
                )
            val relativeDifference =
                kotlin.math.abs(leftLevel - rightLevel) / denominator

            return relativeDifference <= config.ppgRespGapEdgeMaxRelativeDifference
        }

        var shortGapCount = 0
        var mediumGapCount = 0
        var longGapCount = 0
        var cursor = 0

        while (cursor < size) {
            if (originalValid[cursor]) {
                cursor += 1
                continue
            }

            val gapStart = cursor
            while (cursor < size && !originalValid[cursor]) {
                cursor += 1
            }
            val gapEndExclusive = cursor
            val gapLength = gapEndExclusive - gapStart

            val boundedByValidSamples =
                gapStart > 0 &&
                        gapEndExclusive < size &&
                        originalValid[gapStart - 1] &&
                        originalValid[gapEndExclusive]

            val canBridge =
                boundedByValidSamples &&
                        gapLength <= mediumGapMaxSamples &&
                        edgeLevelsAreCompatible(gapStart, gapEndExclusive)

            if (!canBridge) {
                // leading/trailing invalid는 연결할 양쪽 값이 없으므로 버리고,
                // 내부 unbridgeable gap만 continuity break로 센다.
                if (boundedByValidSamples) {
                    longGapCount += 1
                }
                continue
            }

            val leftValue = values[gapStart - 1]
            val rightValue = values[gapEndExclusive]

            for (offset in 0 until gapLength) {
                val fraction = (offset + 1).toDouble() / (gapLength + 1).toDouble()
                val index = gapStart + offset
                values[index] = leftValue + (rightValue - leftValue) * fraction
                preparedValid[index] = true
                interpolatedMask[index] = true
            }

            val exclusionStart =
                (gapStart - peakExclusionMarginSamples).coerceAtLeast(0)
            val exclusionEnd =
                (gapEndExclusive + peakExclusionMarginSamples).coerceAtMost(size)
            for (index in exclusionStart until exclusionEnd) {
                peakExcludedMask[index] = true
            }

            if (gapLength <= shortGapMaxSamples) {
                shortGapCount += 1
            } else {
                mediumGapCount += 1
                for (index in gapStart until gapEndExclusive) {
                    mediumGapMask[index] = true
                }
            }
        }

        val segments = mutableListOf<GapAwarePpgRespirationSegment>()
        val contactSettleSamples =
            (config.ppgSampleRateHz * config.ppgRespContactSettleSeconds)
                .toInt()
                .coerceAtLeast(0)
        var settlingDiscardedSampleCount = 0
        cursor = 0
        var segmentOrdinal = 0

        while (cursor < size) {
            while (cursor < size && !preparedValid[cursor]) {
                cursor += 1
            }
            if (cursor >= size) break

            val detectedStart = cursor
            while (cursor < size && preparedValid[cursor]) {
                cursor += 1
            }
            val endExclusive = cursor

            // window 안에서 leading invalid 또는 내부 long gap 뒤에 시작한 contact segment는
            // 첫 1초를 센서/접촉 안정화 구간으로 버린다. window 첫 sample부터 이어진 segment는
            // 이미 이전부터 안정적으로 측정 중일 수 있으므로 추가로 자르지 않는다.
            val startsAfterVisibleBreak = detectedStart > 0
            val settleTrim =
                if (startsAfterVisibleBreak) {
                    minOf(contactSettleSamples, endExclusive - detectedStart)
                } else {
                    0
                }
            val start = detectedStart + settleTrim
            settlingDiscardedSampleCount += settleTrim

            val length = endExclusive - start
            if (length <= 0) continue

            val segmentStartSamplePosition =
                windowStartSamplePosition + start.toLong()

            // 첫 segment는 rolling window 시작이 움직여도 current analysis segment ID를 유지한다.
            // 내부 gap 이후 segment는 gap 뒤 실제 절대 sample position을 stable continuity ID로 사용한다.
            val continuityGroupId =
                if (segmentOrdinal == 0) {
                    currentAnalysisSegmentId
                } else {
                    segmentStartSamplePosition
                }

            segments.add(
                GapAwarePpgRespirationSegment(
                    startSamplePosition = segmentStartSamplePosition,
                    continuityGroupId = continuityGroupId,
                    values = DoubleArray(length) { local -> values[start + local] },
                    interpolatedMask = BooleanArray(length) { local ->
                        interpolatedMask[start + local]
                    },
                    peakExcludedMask = BooleanArray(length) { local ->
                        peakExcludedMask[start + local]
                    },
                    mediumGapMask = BooleanArray(length) { local ->
                        mediumGapMask[start + local]
                    }
                )
            )
            segmentOrdinal += 1
        }

        val originalContactValidSampleCount = originalValid.count { it }
        val validOriginalSampleCount =
            (originalContactValidSampleCount - settlingDiscardedSampleCount)
                .coerceAtLeast(0)
        val invalidSampleCount = size - originalContactValidSampleCount
        val interpolatedSampleCount = interpolatedMask.count { it }

        return GapAwarePpgRespirationWindow(
            segments = segments,
            rawWindowSampleCount = size,
            validOriginalSampleCount = validOriginalSampleCount,
            invalidSampleCount = invalidSampleCount,
            interpolatedSampleCount = interpolatedSampleCount,
            settlingDiscardedSampleCount = settlingDiscardedSampleCount,
            shortGapCount = shortGapCount,
            mediumGapCount = mediumGapCount,
            longGapCount = longGapCount
        )
    }

    /**
     * 한 개의 큰 step/spike가 진폭 및 peak threshold 전체를 지배하지 않도록
     * 정렬 percentile을 선형 보간해 반환한다.
     */
    private fun calculateRespirationPercentile(
        values: List<Double>,
        percentile: Double
    ): Double? {
        val finite = values.filter { it.isFinite() }.sorted()
        if (finite.isEmpty()) return null
        if (finite.size == 1) return finite.first()

        val p = percentile.coerceIn(0.0, 1.0)
        val position = p * (finite.size - 1).toDouble()
        val lower = kotlin.math.floor(position).toInt()
        val upper = kotlin.math.ceil(position).toInt()
        if (lower == upper) return finite[lower]

        val fraction = position - lower.toDouble()
        return finite[lower] + (finite[upper] - finite[lower]) * fraction
    }

    /**
     * 여러 contact segment를 독립적으로 필터링하고, 같은 segment 안의 peak interval만 만든다.
     * 짧은 gap은 보간 후 peak에서 제외하고, 중간 gap crossing interval은 rejected 처리한다.
     */
    private fun analyzeGapAwareRrFromRespSegments(
        channel: PpgRespirationChannel,
        prepared: GapAwarePpgRespirationWindow,
        invert: Boolean,
        enoughWindow: Boolean
    ): PpgRespirationCandidateAnalysis {
        val warmupSamples = (config.ppgSampleRateHz * 2.0).toInt()
        val minPeakDistanceSamples =
            (config.ppgSampleRateHz * (60.0 / config.rrMaxBpm)).toInt()

        val graphSamples = mutableListOf<Double>()
        val segmentBreakSampleIndices = mutableListOf<Int>()
        val interpolatedGraphIndices = mutableListOf<Int>()
        val positionToGraphIndex = mutableMapOf<Long, Int>()
        val allPeakSamplePositions = mutableListOf<Long>()
        val referencePeakSamplePositions = mutableListOf<Long>()
        val allIntervals = mutableListOf<RespirationInterval>()
        val weightedThresholds = mutableListOf<Pair<Double, Int>>()
        var analyzedSegmentCount = 0

        for (segment in prepared.segments) {
            if (segment.values.size <= warmupSamples + 10) {
                continue
            }

            val mean = segment.values.average()
            val acSignal = DoubleArray(segment.values.size) { index ->
                segment.values[index] - mean
            }

            // segment 경계마다 BPF state를 새로 시작한다.
            // 접촉 전/후 DC step이 하나의 호흡 파형으로 이어지는 것을 방지한다.
            val respBpf = SimpleBandPassFilter(
                sampleRateHz = config.ppgSampleRateHz,
                lowCutHz = config.respLowCutHz,
                highCutHz = config.respHighCutHz
            )
            val filtered = DoubleArray(acSignal.size) { index ->
                respBpf.filter(acSignal[index])
            }
            val wave = if (invert) {
                DoubleArray(filtered.size) { index -> -filtered[index] }
            } else {
                filtered
            }

            val usableSamples =
                (warmupSamples until wave.size).map { index -> wave[index] }
            if (usableSamples.size < 3) continue

            val robustLow = calculateRespirationPercentile(
                usableSamples,
                config.ppgRespRobustLowPercentile
            ) ?: continue
            val robustHigh = calculateRespirationPercentile(
                usableSamples,
                config.ppgRespRobustHighPercentile
            ) ?: continue
            val robustRange = robustHigh - robustLow
            val threshold = robustLow + robustRange * 0.55

            val graphOffset = graphSamples.size
            if (graphOffset > 0) {
                segmentBreakSampleIndices.add(graphOffset)
            }
            graphSamples.addAll(usableSamples)
            analyzedSegmentCount += 1
            weightedThresholds.add(threshold to usableSamples.size)

            for (localIndex in warmupSamples until segment.values.size) {
                val graphIndex = graphOffset + (localIndex - warmupSamples)
                val absolutePosition =
                    segment.startSamplePosition + localIndex.toLong()
                positionToGraphIndex[absolutePosition] = graphIndex
                if (segment.interpolatedMask[localIndex]) {
                    interpolatedGraphIndices.add(graphIndex)
                }
            }

            val mediumGapPrefix = IntArray(segment.mediumGapMask.size + 1)
            for (index in segment.mediumGapMask.indices) {
                mediumGapPrefix[index + 1] =
                    mediumGapPrefix[index] + if (segment.mediumGapMask[index]) 1 else 0
            }

            fun crossesMediumGap(startIndex: Int, endIndex: Int): Boolean {
                val start = startIndex.coerceIn(0, segment.mediumGapMask.size)
                val endExclusive = (endIndex + 1).coerceIn(0, segment.mediumGapMask.size)
                if (endExclusive <= start) return false
                return mediumGapPrefix[endExclusive] - mediumGapPrefix[start] > 0
            }

            val peakIndices = mutableListOf<Int>()
            var lastPeakIndex = -minPeakDistanceSamples

            if (robustRange.isFinite() && robustRange > 0.0) {
                for (index in warmupSamples + 1 until wave.size - 1) {
                    if (segment.peakExcludedMask[index]) continue

                    val isPeak =
                        wave[index] > wave[index - 1] &&
                                wave[index] > wave[index + 1] &&
                                wave[index] > threshold
                    if (!isPeak) continue

                    val distance = index - lastPeakIndex
                    if (distance >= minPeakDistanceSamples) {
                        peakIndices.add(index)
                        lastPeakIndex = index
                    } else if (peakIndices.isNotEmpty()) {
                        val previousIndex = peakIndices.last()
                        if (wave[index] > wave[previousIndex]) {
                            peakIndices[peakIndices.lastIndex] = index
                            lastPeakIndex = index
                        }
                    }
                }
            }

            val peakSamplePositions = peakIndices.map { index ->
                segment.startSamplePosition + index.toLong()
            }
            allPeakSamplePositions.addAll(peakSamplePositions)
            peakSamplePositions.firstOrNull()?.let(referencePeakSamplePositions::add)

            for (index in 1 until peakIndices.size) {
                val startIndex = peakIndices[index - 1]
                val endIndex = peakIndices[index]
                val startPosition =
                    segment.startSamplePosition + startIndex.toLong()
                val endPosition =
                    segment.startSamplePosition + endIndex.toLong()
                val intervalSec =
                    (endPosition - startPosition) / config.ppgSampleRateHz

                allIntervals.add(
                    RespirationInterval(
                        intervalSec = intervalSec,
                        startSamplePosition = startPosition,
                        endSamplePosition = endPosition,
                        segmentId = currentAnalysisSegmentId,
                        continuityGroupId = segment.continuityGroupId,
                        crossesInvalidGap = crossesMediumGap(startIndex, endIndex)
                    )
                )
            }
        }

        val robustLowAll = calculateRespirationPercentile(
            graphSamples,
            config.ppgRespRobustLowPercentile
        )
        val robustHighAll = calculateRespirationPercentile(
            graphSamples,
            config.ppgRespRobustHighPercentile
        )
        val peakToPeakAmplitude =
            if (robustLowAll != null && robustHighAll != null) {
                robustHighAll - robustLowAll
            } else {
                0.0
            }

        val weightedThreshold =
            weightedThresholds.takeIf { it.isNotEmpty() }?.let { entries ->
                val totalWeight = entries.sumOf { it.second }.coerceAtLeast(1)
                entries.sumOf { (value, weight) -> value * weight } / totalWeight.toDouble()
            }

        val minIntervalSec = 60.0 / config.rrMaxBpm
        val maxIntervalSec = 60.0 / config.rrMinBpm
        val physiologicalIntervals = allIntervals.filter { interval ->
            !interval.crossesInvalidGap &&
                    interval.intervalSec in minIntervalSec..maxIntervalSec
        }
        val usedIntervals = removeRespIntervalOutliers(physiologicalIntervals)

        val usedIntervalValues = usedIntervals.map { it.intervalSec }
        val averageIntervalSec =
            usedIntervalValues.takeIf { it.isNotEmpty() }?.average()
        val rrBpm = averageIntervalSec
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?.let { 60.0 / it }

        val amplitudeValid =
            peakToPeakAmplitude >= config.ppgRespMinPeakToPeakAmplitude
        val peakCountValid = allPeakSamplePositions.size >= 3
        val intervalCountValid = physiologicalIntervals.size >= 2
        val usedIntervalCountValid = usedIntervals.size >= 2
        val rrValid = rrBpm != null && rrBpm in config.rrMinBpm..config.rrMaxBpm

        val intervalRegularityScore =
            if (usedIntervalValues.size >= 2) {
                calculateIntervalRegularityScore(usedIntervalValues)
            } else {
                0.0
            }
        val amplitudeScore =
            (peakToPeakAmplitude / config.ppgRespMinPeakToPeakAmplitude)
                .coerceIn(0.0, 3.0) / 3.0
        val qualityScore =
            (intervalRegularityScore * 0.7 + amplitudeScore * 0.3)
                .coerceIn(0.0, 1.0)

        val result =
            if (
                enoughWindow && amplitudeValid && peakCountValid &&
                intervalCountValid && usedIntervalCountValid && rrValid
            ) {
                PpgRespirationResult(
                    channel = channel,
                    rrBpm = rrBpm!!,
                    peakCount = allPeakSamplePositions.size,
                    intervalCount = usedIntervals.size,
                    averageIntervalSec = averageIntervalSec,
                    peakToPeakAmplitude = peakToPeakAmplitude,
                    qualityScore = qualityScore,
                    inverted = invert,
                    peakSamplePositions = allPeakSamplePositions.sorted(),
                    intervals = usedIntervals.sortedBy { it.endSamplePosition }
                )
            } else {
                null
            }

        val acceptedEndPositions =
            usedIntervals.map { it.endSamplePosition }.toSet()
        val rejectedEndPositions =
            allIntervals
                .map { it.endSamplePosition }
                .filterNot { it in acceptedEndPositions }
                .distinct()

        val detectedPeakSampleIndices =
            allPeakSamplePositions
                .mapNotNull(positionToGraphIndex::get)
                .distinct()
                .sorted()
        val acceptedPeakSampleIndices =
            acceptedEndPositions
                .mapNotNull(positionToGraphIndex::get)
                .distinct()
                .sorted()
        val rejectedPeakSampleIndices =
            rejectedEndPositions
                .mapNotNull(positionToGraphIndex::get)
                .distinct()
                .sorted()
        val referencePeakSampleIndices =
            referencePeakSamplePositions
                .mapNotNull(positionToGraphIndex::get)
                .distinct()
                .sorted()

        val validSeconds =
            prepared.validOriginalSampleCount / config.ppgSampleRateHz
        val rawWindowSeconds =
            prepared.rawWindowSampleCount / config.ppgSampleRateHz

        val state: MetricCalculationState
        val description: String
        when {
            !enoughWindow -> {
                state = MetricCalculationState.COLLECTING
                description = "유효 PPG 수집 중: " +
                        "${"%.1f".format(validSeconds)}초 / " +
                        "최소 ${config.ppgRespMinWindowSeconds}초 · " +
                        "segments=$analyzedSegmentCount · " +
                        "gaps=${prepared.shortGapCount}/" +
                        "${prepared.mediumGapCount}/${prepared.longGapCount}"
            }

            analyzedSegmentCount <= 0 -> {
                state = MetricCalculationState.REJECTED
                description = "접촉 복구 후 2초 BPF warm-up을 통과한 segment가 없음"
            }

            !amplitudeValid -> {
                state = MetricCalculationState.REJECTED
                description = "robust 호흡 진폭 부족: " +
                        "${"%.2f".format(peakToPeakAmplitude)} < " +
                        "${"%.2f".format(config.ppgRespMinPeakToPeakAmplitude)}"
            }

            !peakCountValid -> {
                state = MetricCalculationState.REJECTED
                description = "호흡 peak 부족: ${allPeakSamplePositions.size}개 / 최소 3개"
            }

            !intervalCountValid -> {
                state = MetricCalculationState.REJECTED
                description = "segment 내부 2~10초 호흡 interval 부족: " +
                        "${physiologicalIntervals.size}개"
            }

            !usedIntervalCountValid -> {
                state = MetricCalculationState.REJECTED
                description = "median ±${(config.ppgRespIntervalOutlierTolerance * 100).toInt()}% " +
                        "필터 통과 interval 부족: ${usedIntervals.size}개"
            }

            !rrValid -> {
                state = MetricCalculationState.REJECTED
                description = "RR 범위 초과: " +
                        "${rrBpm?.let { "%.1f".format(it) } ?: "--"} bpm"
            }

            else -> {
                state = MetricCalculationState.VALID
                description = "${channel.name} " +
                        "${if (invert) "negative" else "positive"} · " +
                        "RR ${"%.1f".format(rrBpm)} bpm · " +
                        "valid=${"%.1f".format(validSeconds)}초 · " +
                        "segments=$analyzedSegmentCount"
            }
        }

        return PpgRespirationCandidateAnalysis(
            result = result,
            graphData = PpgRespirationGraphData(
                channel = channel,
                selectedPolarity = if (invert) {
                    RespirationPeakPolarity.NEGATIVE
                } else {
                    RespirationPeakPolarity.POSITIVE
                },
                processingState = state,
                samples = graphSamples,
                windowSeconds = validSeconds,
                minimumWindowSeconds = config.ppgRespMinWindowSeconds,
                detectedPeakSampleIndices = detectedPeakSampleIndices,
                acceptedPeakSampleIndices = acceptedPeakSampleIndices,
                rejectedPeakSampleIndices = rejectedPeakSampleIndices,
                referencePeakSampleIndex = referencePeakSampleIndices.firstOrNull(),
                referencePeakSampleIndices = referencePeakSampleIndices,
                segmentBreakSampleIndices = segmentBreakSampleIndices,
                interpolatedSampleIndices = interpolatedGraphIndices.distinct().sorted(),
                rawWindowSeconds = rawWindowSeconds,
                validOriginalSampleCount = prepared.validOriginalSampleCount,
                invalidSampleCount = prepared.invalidSampleCount,
                interpolatedSampleCount = prepared.interpolatedSampleCount,
                settlingDiscardedSampleCount = prepared.settlingDiscardedSampleCount,
                segmentCount = analyzedSegmentCount,
                shortGapCount = prepared.shortGapCount,
                mediumGapCount = prepared.mediumGapCount,
                longGapCount = prepared.longGapCount,
                detectedPeakCount = allPeakSamplePositions.size,
                rawIntervalCount = allIntervals.size,
                acceptedIntervalCount = usedIntervals.size,
                rejectedIntervalCount = rejectedEndPositions.size,
                peakThreshold = weightedThreshold,
                peakToPeakAmplitude = peakToPeakAmplitude,
                calculatedRrBpm = rrBpm,
                qualityScore = result?.qualityScore ?: qualityScore.takeIf { usedIntervals.isNotEmpty() },
                description = description
            )
        )
    }

    /**
     * PPG 호흡 interval 리스트에서 튄 값을 제거한다.
     *
     * 중앙값 대비 허용 비율을 벗어난 interval을 버려
     * 잘못 검출된 peak가 RR/RRV 계산에 주는 영향을 줄인다.
     *
     * 필터 결과가 부족하거나 비어도 원본을 복구하지 않는다.
     * 빈 결과는 상위 계산 함수가 null/REJECTED로 처리한다.
     */
    private fun removeRespIntervalOutliers(
        intervals: List<RespirationInterval>
    ): List<RespirationInterval> {
        if (intervals.size < 3) {
            return intervals
        }

        val sorted = intervals.map { it.intervalSec }.sorted()
        val median = sorted[sorted.size / 2]

        if (!median.isFinite() || median <= 0.0) {
            // 유효하지 않은 기준값으로는 outlier 판정을 신뢰할 수 없으므로
            // 원본 interval을 되살리지 않고 계산 실패를 상위 함수에 전달한다.
            return emptyList()
        }

        val tolerance = config.ppgRespIntervalOutlierTolerance
        if (!tolerance.isFinite() || tolerance < 0.0) {
            return emptyList()
        }

        return intervals.filter { interval ->
            interval.intervalSec.isFinite() &&
                    interval.intervalSec > 0.0 &&
                    abs(interval.intervalSec - median) / median <= tolerance
        }
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
    private fun selectStablePpgRespirationPath(
        candidateFor: (PpgRespirationDetectionPath) -> PpgRespirationResult?,
        nowMillis: Long
    ): PpgRespirationResult? {
        val primaryPath = PpgRespirationDetectionPath.GREEN_POSITIVE
        val fallbackPath = PpgRespirationDetectionPath.GREEN_NEGATIVE

        // negative 사용 중에는 Green positive가 연속적으로 복구됐을 때만 primary로 복귀한다.
        if (activePpgRespirationPath != primaryPath) {
            val primaryCandidate = candidateFor(primaryPath)

            if (primaryCandidate != null) {
                if (isPendingRrConsistent(
                        previousRr = ppgPrimaryRecoveryLastRr,
                        currentRr = primaryCandidate.rrBpm
                    )) {
                    ppgPrimaryRecoveryStreak += 1
                } else {
                    ppgPrimaryRecoveryStreak = 1
                }
                ppgPrimaryRecoveryLastRr = primaryCandidate.rrBpm

                if (
                    ppgPrimaryRecoveryStreak >= config.rrPathRecoveryConfirmFrames &&
                    canSwitchRespirationPath(
                        activeSinceMillis = activePpgRespirationPathSinceMillis,
                        nowMillis = nowMillis
                    )
                ) {
                    activatePpgRespirationPath(primaryPath, nowMillis)
                    return primaryCandidate
                }
            } else {
                ppgPrimaryRecoveryStreak = 0
                ppgPrimaryRecoveryLastRr = null
            }
        }

        val activeCandidate = candidateFor(activePpgRespirationPath)
        if (activeCandidate != null) {
            ppgRespirationFailureStreak = 0
            clearPendingPpgRespirationPath()
            return activeCandidate
        }

        ppgRespirationFailureStreak += 1
        if (ppgRespirationFailureStreak < config.rrPathFailuresBeforeFallback) {
            return null
        }

        if (fallbackPath == activePpgRespirationPath) {
            return null
        }

        val fallbackCandidate = candidateFor(fallbackPath)
        if (fallbackCandidate == null) {
            clearPendingPpgRespirationPath()
            return null
        }

        registerPendingPpgRespirationCandidate(
            path = fallbackPath,
            rrBpm = fallbackCandidate.rrBpm
        )

        if (
            pendingPpgRespirationSuccessStreak >= config.rrPathConfirmFrames &&
            canSwitchRespirationPath(
                activeSinceMillis = activePpgRespirationPathSinceMillis,
                nowMillis = nowMillis
            )
        ) {
            activatePpgRespirationPath(fallbackPath, nowMillis)
            return fallbackCandidate
        }

        return null
    }

    private fun registerPendingPpgRespirationCandidate(
        path: PpgRespirationDetectionPath,
        rrBpm: Double
    ) {
        val samePath = pendingPpgRespirationPath == path
        val consistent = samePath && isPendingRrConsistent(
            previousRr = pendingPpgRespirationLastRr,
            currentRr = rrBpm
        )

        pendingPpgRespirationSuccessStreak =
            if (consistent) pendingPpgRespirationSuccessStreak + 1 else 1
        pendingPpgRespirationPath = path
        pendingPpgRespirationLastRr = rrBpm
    }

    private fun activatePpgRespirationPath(
        path: PpgRespirationDetectionPath,
        nowMillis: Long
    ) {
        activePpgRespirationPath = path
        activePpgRespirationPathSinceMillis = nowMillis
        ppgRespirationFailureStreak = 0
        ppgPrimaryRecoveryStreak = 0
        ppgPrimaryRecoveryLastRr = null
        clearPendingPpgRespirationPath()
    }

    private fun clearPendingPpgRespirationPath() {
        pendingPpgRespirationPath = null
        pendingPpgRespirationSuccessStreak = 0
        pendingPpgRespirationLastRr = null
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
            (config.imuSampleRateHz * config.imuRespWindowSeconds).toInt()

        val minSampleCount =
            (config.imuSampleRateHz * config.imuRespMinWindowSeconds).toInt()

        if (imuGBuffer.size < minSampleCount) {
            return null
        }

        val rawWindow =
            if (imuGBuffer.size > windowSampleCount) {
                imuGBuffer.takeLast(windowSampleCount)
            } else {
                imuGBuffer.toList()
            }

        val windowStartSamplePosition =
            totalImuRespSampleCount - rawWindow.size.toLong()

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
            sampleRateHz = config.imuSampleRateHz,
            lowCutHz = config.respLowCutHz,
            highCutHz = config.respHighCutHz
        )

        val filtered = DoubleArray(acSignal.size) { i ->
            respBpf.filter(acSignal[i])
        }

        // 3. 필터 안정화 전 구간 버림
        val warmupSamples = (config.imuSampleRateHz * 2.0).toInt()

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

        // 5. 양의 peak / 음의 peak 후보를 만들고 stable polarity selector에 전달
        val positiveResult = calculateRrFromImuRespWave(
            filtered = filtered,
            usableStartIndex = warmupSamples,
            peakToPeakAmplitudeG = peakToPeakAmplitudeG,
            invert = false,
            windowStartSamplePosition = windowStartSamplePosition
        )

        val negativeResult = calculateRrFromImuRespWave(
            filtered = filtered,
            usableStartIndex = warmupSamples,
            peakToPeakAmplitudeG = peakToPeakAmplitudeG,
            invert = true,
            windowStartSamplePosition = windowStartSamplePosition
        )

        return selectStableImuRespirationPath(
            positiveResult = positiveResult,
            negativeResult = negativeResult,
            nowMillis = System.currentTimeMillis()
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
        invert: Boolean,
        windowStartSamplePosition: Long
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
            (config.imuSampleRateHz * (60.0 / config.rrMaxBpm)).toInt()

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

        val peakSamplePositions = peakIndices.map { index ->
            windowStartSamplePosition + index.toLong()
        }

        val intervals = mutableListOf<RespirationInterval>()

        val minIntervalSec = 60.0 / config.rrMaxBpm
        val maxIntervalSec = 60.0 / config.rrMinBpm

        for (i in 1 until peakSamplePositions.size) {
            val startPosition = peakSamplePositions[i - 1]
            val endPosition = peakSamplePositions[i]
            val intervalSec =
                (endPosition - startPosition) / config.imuSampleRateHz

            if (intervalSec in minIntervalSec..maxIntervalSec) {
                intervals.add(
                    RespirationInterval(
                        intervalSec = intervalSec,
                        startSamplePosition = startPosition,
                        endSamplePosition = endPosition,
                        segmentId = currentAnalysisSegmentId
                    )
                )
            }
        }

        if (intervals.size < 2) {
            return null
        }

        val usedIntervals = removeImuRespIntervalOutliers(intervals)

        if (usedIntervals.size < 2) {
            return null
        }

        val usedIntervalValues = usedIntervals.map { it.intervalSec }
        val averageIntervalSec = usedIntervalValues.average()

        if (averageIntervalSec <= 0.0) {
            return null
        }

        val rrBpm = 60.0 / averageIntervalSec

        if (rrBpm !in config.rrMinBpm..config.rrMaxBpm) {
            return null
        }

        val intervalRegularityScore =
            calculateImuIntervalRegularityScore(usedIntervalValues)

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
            peakSamplePositions = peakSamplePositions,
            intervals = usedIntervals
        )
    }

    /**
     * IMU 호흡 interval 리스트에서 튄 값을 제거한다.
     *
     * 중앙값 대비 허용 비율을 벗어난 interval을 제외해
     * 큰 움직임이나 잘못 잡힌 peak의 영향을 줄인다.
     */
    private fun removeImuRespIntervalOutliers(
        intervals: List<RespirationInterval>
    ): List<RespirationInterval> {
        if (intervals.size < 3) {
            return intervals
        }

        val sorted = intervals.map { it.intervalSec }.sorted()
        val median = sorted[sorted.size / 2]

        if (!median.isFinite() || median <= 0.0) {
            return emptyList()
        }

        val tolerance = config.imuRespIntervalOutlierTolerance
        if (!tolerance.isFinite() || tolerance < 0.0) {
            return emptyList()
        }

        return intervals.filter { interval ->
            interval.intervalSec.isFinite() &&
                    interval.intervalSec > 0.0 &&
                    kotlin.math.abs(interval.intervalSec - median) / median <= tolerance
        }
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
     * IMU positive를 primary로 유지하고, 연속 실패와 연속 확인을 거친 경우에만
     * negative fallback으로 전환한다. 순간 quality 차이로는 polarity를 바꾸지 않는다.
     */
    private fun selectStableImuRespirationPath(
        positiveResult: ImuRespirationResult?,
        negativeResult: ImuRespirationResult?,
        nowMillis: Long
    ): ImuRespirationResult? {
        fun candidateFor(path: ImuRespirationDetectionPath): ImuRespirationResult? {
            return if (path.inverted) negativeResult else positiveResult
        }

        val primaryPath = ImuRespirationDetectionPath.POSITIVE

        if (activeImuRespirationPath != primaryPath) {
            val primaryCandidate = candidateFor(primaryPath)

            if (primaryCandidate != null) {
                if (isPendingRrConsistent(
                        previousRr = imuPrimaryRecoveryLastRr,
                        currentRr = primaryCandidate.rrBpm
                    )) {
                    imuPrimaryRecoveryStreak += 1
                } else {
                    imuPrimaryRecoveryStreak = 1
                }
                imuPrimaryRecoveryLastRr = primaryCandidate.rrBpm

                if (
                    imuPrimaryRecoveryStreak >= config.rrPathRecoveryConfirmFrames &&
                    canSwitchRespirationPath(
                        activeSinceMillis = activeImuRespirationPathSinceMillis,
                        nowMillis = nowMillis
                    )
                ) {
                    activateImuRespirationPath(primaryPath, nowMillis)
                    return primaryCandidate
                }
            } else {
                imuPrimaryRecoveryStreak = 0
                imuPrimaryRecoveryLastRr = null
            }
        }

        val activeCandidate = candidateFor(activeImuRespirationPath)
        if (activeCandidate != null) {
            imuRespirationFailureStreak = 0
            clearPendingImuRespirationPath()
            return activeCandidate
        }

        imuRespirationFailureStreak += 1
        if (imuRespirationFailureStreak < config.rrPathFailuresBeforeFallback) {
            return null
        }

        val fallbackPath =
            if (activeImuRespirationPath == ImuRespirationDetectionPath.POSITIVE) {
                ImuRespirationDetectionPath.NEGATIVE
            } else {
                ImuRespirationDetectionPath.POSITIVE
            }

        val fallbackCandidate = candidateFor(fallbackPath)
        if (fallbackCandidate == null) {
            clearPendingImuRespirationPath()
            return null
        }

        registerPendingImuRespirationCandidate(
            path = fallbackPath,
            rrBpm = fallbackCandidate.rrBpm
        )

        if (
            pendingImuRespirationSuccessStreak >= config.rrPathConfirmFrames &&
            canSwitchRespirationPath(
                activeSinceMillis = activeImuRespirationPathSinceMillis,
                nowMillis = nowMillis
            )
        ) {
            activateImuRespirationPath(fallbackPath, nowMillis)
            return fallbackCandidate
        }

        return null
    }

    private fun registerPendingImuRespirationCandidate(
        path: ImuRespirationDetectionPath,
        rrBpm: Double
    ) {
        val samePath = pendingImuRespirationPath == path
        val consistent = samePath && isPendingRrConsistent(
            previousRr = pendingImuRespirationLastRr,
            currentRr = rrBpm
        )

        pendingImuRespirationSuccessStreak =
            if (consistent) pendingImuRespirationSuccessStreak + 1 else 1
        pendingImuRespirationPath = path
        pendingImuRespirationLastRr = rrBpm
    }

    private fun activateImuRespirationPath(
        path: ImuRespirationDetectionPath,
        nowMillis: Long
    ) {
        activeImuRespirationPath = path
        activeImuRespirationPathSinceMillis = nowMillis
        imuRespirationFailureStreak = 0
        imuPrimaryRecoveryStreak = 0
        imuPrimaryRecoveryLastRr = null
        clearPendingImuRespirationPath()
    }

    private fun clearPendingImuRespirationPath() {
        pendingImuRespirationPath = null
        pendingImuRespirationSuccessStreak = 0
        pendingImuRespirationLastRr = null
    }

    private fun isPendingRrConsistent(
        previousRr: Double?,
        currentRr: Double
    ): Boolean {
        return previousRr == null ||
                abs(currentRr - previousRr) <= config.rrPathPendingBpmTolerance
    }

    private fun canSwitchRespirationPath(
        activeSinceMillis: Long,
        nowMillis: Long
    ): Boolean {
        return activeSinceMillis <= 0L ||
                nowMillis - activeSinceMillis >= config.rrPathMinHoldMillis
    }

    private fun resetRespirationPathSelection() {
        activePpgRespirationPath = PpgRespirationDetectionPath.GREEN_POSITIVE
        activePpgRespirationPathSinceMillis = 0L
        ppgRespirationFailureStreak = 0
        ppgPrimaryRecoveryStreak = 0
        ppgPrimaryRecoveryLastRr = null
        clearPendingPpgRespirationPath()
        latestPpgRespirationGraphData = PpgRespirationGraphData(
            minimumWindowSeconds = config.ppgRespMinWindowSeconds
        )

        activeImuRespirationPath = ImuRespirationDetectionPath.POSITIVE
        activeImuRespirationPathSinceMillis = 0L
        imuRespirationFailureStreak = 0
        imuPrimaryRecoveryStreak = 0
        imuPrimaryRecoveryLastRr = null
        clearPendingImuRespirationPath()
    }

    /********************* //RR from IMU ********************/

    /********************* Fusion RR data from PPG & IMU ********************/

    /**
     * PPG RR과 IMU RR을 합성해 최종 RR을 결정한다.
     *
     * 두 센서가 비슷하면 PPG 가중치를 더 크게 둔 품질 기반 가중 평균을 사용하고,
     * 한쪽만 유효하거나 서로 크게 다르면 PPG 우선 정책과 quality를 기준으로 선택한다.
     */
    fun fuseRespiration(
        ppg: PpgRespirationResult?,
        imu: ImuRespirationResult?
    ): RrFusionResult {
        val ppgValid = ppg?.rrBpm?.let { isValidRr(it) } == true
        val imuValid = imu?.rrBpm?.let { isValidRr(it) } == true
        val ppgPathLabel = ppg?.let {
            "${it.channel.name} ${if (it.inverted) "negative" else "positive"}"
        }
        val imuPathLabel = imu?.let {
            if (it.inverted) "IMU negative" else "IMU positive"
        }

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
                log = "RR fusion failed: no valid PPG/IMU RR " +
                        "(ppgPath=$ppgPathLabel, imuPath=$imuPathLabel)"
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
                confidence = (imu.qualityScore * 0.8).coerceIn(0.0, 1.0),
                log = "RR fusion: IMU only (PPG unavailable, path=$imuPathLabel)"
            )
        }

        if (!imuValid && ppgValid) {
            return RrFusionResult(
                rrBpm = ppg!!.rrBpm,
                source = RrFusionSource.GREEN_PPG_ONLY,
                ppgRrBpm = ppg.rrBpm,
                imuRrBpm = imu?.rrBpm,
                ppgQuality = ppg.qualityScore,
                imuQuality = imu?.qualityScore,
                diffBpm = null,
                confidence = ppg.qualityScore.coerceIn(0.0, 1.0),
                log = "RR fusion: PPG only (path=$ppgPathLabel)"
            )
        }

        val ppgRr = ppg!!.rrBpm
        val imuRr = imu!!.rrBpm
        val diff = abs(ppgRr - imuRr)

        val ppgQuality = ppg.qualityScore.coerceIn(0.0, 1.0)
        val imuQuality = imu.qualityScore.coerceIn(0.0, 1.0)

        val ppgUsable =
            ppgQuality >= config.rrFusionMinUsableQuality

        val imuUsable =
            imuQuality >= config.rrFusionMinUsableQuality

        val imuStrongEnoughForWeighting =
            imuQuality >= config.rrFusionMinImuQualityForWeighting

        // 1. 두 값이 1 bpm 이내이고 양쪽 품질이 충분하며,
        // IMU가 엄격한 weighting 품질 기준까지 통과할 때만 가중 평균한다.
        if (
            diff <= config.rrFusionAgreeDiffBpm &&
            ppgUsable &&
            imuStrongEnoughForWeighting
        ) {
            val imuWeight =
                config.rrFusionImuBaseWeight * (0.5 + imuQuality)

            val ppgWeight =
                config.rrFusionPpgBaseWeight * (0.5 + ppgQuality)

            val totalWeight = imuWeight + ppgWeight

            val fusedRr =
                if (totalWeight <= 0.0) {
                    ppgRr
                } else {
                    (imuRr * imuWeight + ppgRr * ppgWeight) / totalWeight
                }

            val agreementScore =
                (1.0 - diff / config.rrFusionAgreeDiffBpm)
                    .coerceIn(0.0, 1.0)

            val confidence =
                (agreementScore * 0.5 +
                        ppgQuality * 0.35 +
                        imuQuality * 0.15)
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
                log = "RR fusion: weighted, diff=${"%.2f".format(diff)}, " +
                        "ppgQ=${"%.2f".format(ppgQuality)}, imuQ=${"%.2f".format(imuQuality)}, " +
                        "ppgPath=$ppgPathLabel, imuPath=$imuPathLabel"
            )
        }

        // 2. weighted fusion 조건을 만족하지 못하면 기본적으로 PPG를 사용한다.
        // PPG가 usable하지 않고 IMU만 usable한 경우에만 IMU를 백업으로 사용한다.
        if (!ppgUsable && imuUsable) {
            return RrFusionResult(
                rrBpm = imuRr,
                source = RrFusionSource.IMU_PREFERRED_DISAGREE,
                ppgRrBpm = ppgRr,
                imuRrBpm = imuRr,
                ppgQuality = ppgQuality,
                imuQuality = imuQuality,
                diffBpm = diff,
                confidence = (imuQuality * 0.7).coerceIn(0.0, 1.0),
                log = "RR fusion: disagree, IMU used because PPG quality is low " +
                        "(path=$imuPathLabel)"
            )
        }

        return RrFusionResult(
            rrBpm = ppgRr,
            source = RrFusionSource.GREEN_PPG_PREFERRED_DISAGREE,
            ppgRrBpm = ppgRr,
            imuRrBpm = imuRr,
            ppgQuality = ppgQuality,
            imuQuality = imuQuality,
            diffBpm = diff,
            confidence = (ppgQuality * 0.8).coerceIn(0.0, 1.0),
            log = "RR fusion: PPG preferred, weighted blocked " +
                    "(diff=${"%.2f".format(diff)}, ppgQ=${"%.2f".format(ppgQuality)}, " +
                    "imuQ=${"%.2f".format(imuQuality)})"
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

        val tolerance = config.rrScoreOutlierToleranceBpm
        if (!median.isFinite() || !tolerance.isFinite() || tolerance < 0.0) {
            return emptyList()
        }

        return values.filter { (_, rrBpm) ->
            rrBpm.isFinite() &&
                    rrBpm > 0.0 &&
                    abs(rrBpm - median) <= tolerance
        }
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
    private fun appendRespirationIntervalsForRrv(
        ppg: PpgRespirationResult?,
        imu: ImuRespirationResult?
    ) {
        if (ppg != null) {
            appendRespirationIntervalsForRrvSource(
                source = RrvSource.GREEN_PPG,
                intervals = ppg.intervals,
                qualityScore = ppg.qualityScore
            )
        }

        if (imu != null) {
            appendRespirationIntervalsForRrvSource(
                source = RrvSource.IMU,
                intervals = imu.intervals,
                qualityScore = imu.qualityScore
            )
        }
    }

    /**
     * rolling RR 계산으로 동일 interval이 매초 다시 전달되므로
     * source별 마지막 endSamplePosition보다 새로운 interval만 추가한다.
     */
    private fun appendRespirationIntervalsForRrvSource(
        source: RrvSource,
        intervals: List<RespirationInterval>,
        qualityScore: Double
    ): Int {
        if (source == RrvSource.NONE) return 0
        if (qualityScore < config.rrvMinUsableQuality) return 0

        val buffer =
            when (source) {
                RrvSource.GREEN_PPG -> ppgRrvIntervalBuffer
                RrvSource.IMU -> imuRrvIntervalBuffer
                RrvSource.NONE -> return 0
            }

        var lastAcceptedEnd =
            when (source) {
                RrvSource.GREEN_PPG -> lastAcceptedPpgRrvEndSamplePosition
                RrvSource.IMU -> lastAcceptedImuRrvEndSamplePosition
                RrvSource.NONE -> Long.MAX_VALUE
            }

        val sourceSampleRateHz = when (source) {
            RrvSource.GREEN_PPG -> config.ppgSampleRateHz
            RrvSource.IMU -> config.imuSampleRateHz
            RrvSource.NONE -> return 0
        }

        // 같은 peak를 rolling window에서 몇 sample 다르게 다시 찾는 경우를 중복으로 보지 않는다.
        // 새 호흡 peak는 최소 허용 호흡 간격의 절반 이상 진행된 뒤에만 받는다.
        val minimumNewPeakAdvanceSamples =
            (sourceSampleRateHz * (60.0 / config.rrMaxBpm) * 0.5)
                .toLong()
                .coerceAtLeast(1L)

        // 양/음 peak 방향이 바뀌면서 이전 interval과 겹치는 후보가 들어오는 것을 막는다.
        val allowedPeakPositionJitterSamples =
            (sourceSampleRateHz * 0.15)
                .toLong()
                .coerceAtLeast(1L)

        var acceptedCount = 0

        intervals
            .asSequence()
            .filter { it.segmentId == currentAnalysisSegmentId }
            .sortedBy { it.endSamplePosition }
            .forEach { interval ->
                if (!interval.intervalSec.isFinite() || interval.intervalSec <= 0.0) {
                    return@forEach
                }

                if (lastAcceptedEnd != Long.MIN_VALUE) {
                    val endAdvance =
                        interval.endSamplePosition - lastAcceptedEnd

                    if (endAdvance < minimumNewPeakAdvanceSamples) {
                        return@forEach
                    }

                    // 이전에 수락한 호흡 peak보다 훨씬 앞에서 시작한 interval은
                    // 반대 polarity에서 나온 겹치는 interval일 가능성이 높다.
                    if (
                        interval.startSamplePosition <
                        lastAcceptedEnd - allowedPeakPositionJitterSamples
                    ) {
                        return@forEach
                    }
                }

                buffer.add(
                    BufferedRespirationInterval(
                        interval = interval,
                        qualityScore = qualityScore.coerceIn(0.0, 1.0)
                    )
                )

                lastAcceptedEnd = interval.endSamplePosition
                acceptedCount += 1
            }

        when (source) {
            RrvSource.GREEN_PPG ->
                lastAcceptedPpgRrvEndSamplePosition = lastAcceptedEnd
            RrvSource.IMU ->
                lastAcceptedImuRrvEndSamplePosition = lastAcceptedEnd
            RrvSource.NONE -> Unit
        }

        return acceptedCount
    }

    /**
     * source별 RRV buffer에서 최근 config.rrvWindowSeconds 밖의 interval을 제거한다.
     */
    private fun trimRespirationVariabilityBuffers() {
        trimRespirationVariabilityBuffer(
            buffer = ppgRrvIntervalBuffer,
            newestSamplePosition = totalPpgRespSampleCount,
            sampleRateHz = config.ppgSampleRateHz
        )

        trimRespirationVariabilityBuffer(
            buffer = imuRrvIntervalBuffer,
            newestSamplePosition = totalImuRespSampleCount,
            sampleRateHz = config.imuSampleRateHz
        )
    }

    private fun trimRespirationVariabilityBuffer(
        buffer: ArrayDeque<BufferedRespirationInterval>,
        newestSamplePosition: Long,
        sampleRateHz: Double
    ) {
        val windowSamples =
            (sampleRateHz * config.rrvWindowSeconds)
                .toLong()
                .coerceAtLeast(1L)

        val minimumEndSamplePosition =
            newestSamplePosition - windowSamples

        while (
            buffer.isNotEmpty() &&
            buffer.first().interval.endSamplePosition < minimumEndSamplePosition
        ) {
            buffer.removeFirst()
        }
    }

    private fun clearRespirationVariabilityBuffers(
        resetSampleCounters: Boolean
    ) {
        ppgRrvIntervalBuffer.clear()
        imuRrvIntervalBuffer.clear()

        lastAcceptedPpgRrvEndSamplePosition = Long.MIN_VALUE
        lastAcceptedImuRrvEndSamplePosition = Long.MIN_VALUE

        if (resetSampleCounters) {
            totalPpgRespSampleCount = 0L
            totalImuRespSampleCount = 0L
        }
    }

    /**
     * PPG/IMU의 최근 3분 interval buffer에서 각각 RRV를 계산한 뒤,
     * 현재 RR fusion source에 맞는 결과를 최종 선택한다.
     */
    private fun calculateRrvRmssd(
        rrFusion: RrFusionResult
    ): RrvCalculationBundle {
        val ppgRrv = buildRrvResultFromIntervals(
            source = RrvSource.GREEN_PPG,
            bufferedIntervals = ppgRrvIntervalBuffer.toList()
        )

        val imuRrv = buildRrvResultFromIntervals(
            source = RrvSource.IMU,
            bufferedIntervals = imuRrvIntervalBuffer.toList()
        )

        val selected =
            when (rrFusion.source) {
                RrFusionSource.BOTH_WEIGHTED,
                RrFusionSource.GREEN_PPG_ONLY,
                RrFusionSource.GREEN_PPG_PREFERRED_DISAGREE -> {
                    ppgRrv ?: imuRrv
                }

                RrFusionSource.IMU_ONLY,
                RrFusionSource.IMU_PREFERRED_DISAGREE -> {
                    imuRrv ?: ppgRrv
                }

                // 현재 프레임에서 RR 자체가 유효하지 않으면 오래된 RRV를 재사용하지 않는다.
                RrFusionSource.NONE -> null
            }

        return RrvCalculationBundle(
            ppg = ppgRrv,
            imu = imuRrv,
            selected = selected
        )
    }

    /**
     * 선택된 source의 RRV 전용 rolling interval buffer로 RMSSD를 계산한다.
     */
    private fun buildRrvResultFromIntervals(
        source: RrvSource,
        bufferedIntervals: List<BufferedRespirationInterval>
    ): RrvResult? {
        if (bufferedIntervals.isEmpty()) return null

        val orderedIntervals =
            bufferedIntervals
                .filter {
                    it.interval.segmentId == currentAnalysisSegmentId &&
                            it.qualityScore >= config.rrvMinUsableQuality
                }
                .sortedBy { it.interval.endSamplePosition }

        val cleanedIntervals =
            removeRrvIntervalOutliers(orderedIntervals)

        if (cleanedIntervals.size < config.rrvMinIntervalCount) {
            return null
        }

        val intervalValues =
            cleanedIntervals.map { it.interval.intervalSec }

        // contact loss로 분리된 PPG continuity group 경계에서는
        // 이전 segment 마지막 interval과 다음 segment 첫 interval의 차이를 RRV로 계산하지 않는다.
        val successiveDiffs =
            cleanedIntervals.zipWithNext().mapNotNull { (previous, current) ->
                if (
                    previous.interval.continuityGroupId ==
                    current.interval.continuityGroupId
                ) {
                    current.interval.intervalSec - previous.interval.intervalSec
                } else {
                    null
                }
            }

        if (successiveDiffs.isEmpty()) {
            return null
        }

        val rmssdSec =
            sqrt(
                successiveDiffs
                    .map { diff -> diff * diff }
                    .average()
            )

        val rmssdMs = rmssdSec * 1000.0
        val meanIntervalSec = intervalValues.average()
        val score = scoreRrvRmssd(rmssdSec)

        val meanRespirationQuality =
            cleanedIntervals
                .map { it.qualityScore }
                .average()
                .coerceIn(0.0, 1.0)

        val preferredCount =
            config.rrvPreferredIntervalCount
                .coerceAtLeast(config.rrvMinIntervalCount)
                .toDouble()

        val intervalCountScore =
            (cleanedIntervals.size / preferredCount)
                .coerceIn(0.0, 1.0)

        val qualityScore =
            (meanRespirationQuality * 0.7 +
                    intervalCountScore * 0.3)
                .coerceIn(0.0, 1.0)

        return RrvResult(
            rmssdSec = rmssdSec,
            rmssdMs = rmssdMs,
            source = source,
            intervalCount = cleanedIntervals.size,
            meanIntervalSec = meanIntervalSec,
            score = score,
            qualityScore = qualityScore,
            log = "RRV ${source.name}: rmssd=${"%.3f".format(rmssdSec)}s, " +
                    "intervals=${cleanedIntervals.size}, " +
                    "window=${config.rrvWindowSeconds}s"
        )
    }

    /**
     * RRV 계산 전 호흡 interval outlier를 제거한다.
     *
     * 갑자기 잘못 잡힌 peak interval이 RMSSD를 과도하게 키우지 않도록
     * 중앙값 기준 허용 범위 밖의 값을 제외한다.
     */
    private fun removeRrvIntervalOutliers(
        intervals: List<BufferedRespirationInterval>
    ): List<BufferedRespirationInterval> {
        if (intervals.size < 3) {
            return intervals
        }

        val sorted =
            intervals.map { it.interval.intervalSec }.sorted()
        val median = sorted[sorted.size / 2]

        if (!median.isFinite() || median <= 0.0) {
            return emptyList()
        }

        val tolerance = config.rrvIntervalOutlierTolerance
        if (!tolerance.isFinite() || tolerance < 0.0) {
            return emptyList()
        }

        return intervals.filter { buffered ->
            val interval = buffered.interval.intervalSec

            interval.isFinite() &&
                    interval > 0.0 &&
                    abs(interval - median) / median <= tolerance
        }
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

        val tolerance = config.hrOutlierToleranceBpm
        if (!tolerance.isFinite() || tolerance < 0.0) {
            return emptyList()
        }

        return values.filter { (_, bpm) ->
            bpm > 0 && abs(bpm - median) <= tolerance
        }
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
     * 포물선 보간된 endSamplePosition을 이용해 이미 저장한 interval은 건너뛴다.
     */
    private fun appendHeartRateEstimateToHrvBuffer(
        estimate: HeartRateEstimate,
        acceptedAtMillis: Long
    ): Int {
        if (estimate.qualityScore < config.hrvMinEstimateQuality) {
            return 0
        }

        var acceptedCount = 0
        val sortedIntervals = estimate.ibiIntervals
            .filter { it.segmentId == currentAnalysisSegmentId }
            .sortedBy { it.endSamplePosition }

        for (ibi in sortedIntervals) {
            if (!ibi.endSamplePosition.isFinite()) {
                continue
            }

            if (ibi.segmentId != lastAcceptedHrvIbiSegmentId) {
                lastAcceptedHrvIbiSegmentId = ibi.segmentId
                lastAcceptedHrvIbiEndSamplePosition = Double.NEGATIVE_INFINITY
            }

            if (ibi.endSamplePosition <= lastAcceptedHrvIbiEndSamplePosition) {
                continue
            }

            if (ibi.intervalSec !in 0.333..1.5) {
                continue
            }

            hrvIbiBuffer.add(ibi)
            lastAcceptedHrvIbiEndSamplePosition = ibi.endSamplePosition
            acceptedCount += 1
        }

        if (acceptedCount > 0) {
            lastValidHrvTimestampMillis = acceptedAtMillis
            hrvBufferExpiredByGap = false
        }

        trimHrvIbiBuffer()
        return acceptedCount
    }

    /**
     * 현재 연속 구간에 속한 IBI만 반환한다.
     * 이전 segment의 IBI는 보관 중이어도 RMSSD/LF-HF 계산에는 사용하지 않는다.
     */
    private fun currentSegmentHrvIbis(): List<IbiInterval> {
        return hrvIbiBuffer.filter {
            it.segmentId == currentAnalysisSegmentId
        }
    }

    /**
     * HRV IBI rolling buffer에서 오래된 interval을 제거한다.
     *
     * RMSSD 계산용 window 길이 안의 IBI만 유지한다.
     */
    private fun trimHrvIbiBuffer() {
        if (hrvIbiBuffer.isEmpty()) return

        // 현재 segment 이전 데이터는 이후 계산에 다시 쓰지 않으므로 제거한다.
        while (
            hrvIbiBuffer.isNotEmpty() &&
            hrvIbiBuffer.first().segmentId < currentAnalysisSegmentId
        ) {
            hrvIbiBuffer.removeFirst()
        }

        val currentIbis = currentSegmentHrvIbis()
        if (currentIbis.isEmpty()) return

        val newestSamplePosition =
            currentIbis.last().endSamplePosition

        // 공용 buffer는 RMSSD(60초)와 LF/HF(120초) 중 더 긴 창까지 보관한다.
        // 각 계산 함수가 자기 분석창으로 다시 잘라 사용한다.
        val retainedWindowSeconds = maxOf(
            config.hrvWindowSeconds,
            config.hrvFrequencyWindowSeconds
        )
        val windowSamples =
            config.ppgSampleRateHz * retainedWindowSeconds

        val minSamplePosition =
            newestSamplePosition - windowSamples

        while (hrvIbiBuffer.isNotEmpty()) {
            val first = hrvIbiBuffer.first()

            if (
                first.segmentId == currentAnalysisSegmentId &&
                first.endSamplePosition >= minSamplePosition
            ) {
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
        // 공용 120초 buffer에서 RMSSD 전용 최근 60초만 선택한다.
        val recentRmssdIbis = takeRecentIbisForWindow(
            ibis = currentSegmentHrvIbis(),
            windowSeconds = config.hrvWindowSeconds
        )

        if (recentRmssdIbis.size < config.hrvMinIbiCount) {
            return null
        }

        val cleanedIbis = removeHrvIbiOutliers(recentRmssdIbis)

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

        if (!median.isFinite() || median <= 0.0) {
            return emptyList()
        }

        val tolerance = config.hrvIbiOutlierTolerance
        if (!tolerance.isFinite() || tolerance < 0.0) {
            return emptyList()
        }

        return ibis.filter { ibi ->
            ibi.intervalSec.isFinite() &&
                    ibi.intervalSec > 0.0 &&
                    abs(ibi.intervalSec - median) / median <= tolerance
        }
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
    /**
     * 기존 외부 API 호환용. 사용 제한을 모두 통과한 LF/HF 결과만 반환한다.
     * process()에서는 실패 사유 보존을 위해 evaluateHrvFrequencyDomain()을 직접 사용한다.
     */
    fun calculateHrvFrequencyDomain(): HrvFrequencyResult? {
        val assessment = evaluateHrvFrequencyDomain(
            respiratoryRateBpm = lastState.rrFinal,
            ppgSignalQuality = lastState.hrvFrequencyPpgSignalQuality,
            heartRateSignalStatus = lastState.hrCalculationStatus
        )
        return assessment.candidate?.takeIf { assessment.usable }
    }

    /**
     * LF/HF 후보값을 계산하고 실제 사용 가능 여부와 모든 제한 사유를 함께 반환한다.
     */
    private fun evaluateHrvFrequencyDomain(
        respiratoryRateBpm: Double?,
        ppgSignalQuality: Double?,
        heartRateSignalStatus: MetricCalculationStatus
    ): HrvFrequencyAssessment {
        val segmentIbis = currentSegmentHrvIbis()
        val rawCount = segmentIbis.size

        if (rawCount < config.hrvSpectralMinIbiCount) {
            return HrvFrequencyAssessment(
                usable = false,
                status = MetricCalculationStatus(
                    state = MetricCalculationState.COLLECTING,
                    message = "LF/HF 유효 IBI 수집 중: $rawCount/${config.hrvSpectralMinIbiCount}"
                ),
                rejectionReasons = listOf(HrvFrequencyRejectionReason.INSUFFICIENT_IBI_COUNT),
                rejectionDetails = "rawIbi=$rawCount,minIbi=${config.hrvSpectralMinIbiCount}",
                rawIbiCount = rawCount,
                ppgSignalQuality = ppgSignalQuality,
                respiratoryRateBpm = respiratoryRateBpm
            )
        }

        val cleanedIbis = removeHrvIbiOutliers(segmentIbis)
        val cleanedCount = cleanedIbis.size
        if (cleanedCount < config.hrvSpectralMinIbiCount) {
            return HrvFrequencyAssessment(
                usable = false,
                status = MetricCalculationStatus(
                    state = MetricCalculationState.REJECTED,
                    message = "LF/HF IBI 이상치 제거 후 유효 개수 부족"
                ),
                rejectionReasons = listOf(HrvFrequencyRejectionReason.TOO_MANY_IBI_OUTLIERS),
                rejectionDetails = "rawIbi=$rawCount,cleanedIbi=$cleanedCount,minIbi=${config.hrvSpectralMinIbiCount}",
                rawIbiCount = rawCount,
                cleanedIbiCount = cleanedCount,
                ppgSignalQuality = ppgSignalQuality,
                respiratoryRateBpm = respiratoryRateBpm
            )
        }

        val recentIbis = takeRecentIbisForFrequencyAnalysis(cleanedIbis)
        val recentCount = recentIbis.size
        val observedDurationSec = observedIbiDurationSec(recentIbis)
        val minimumCycleDurationSec =
            config.hrvSpectralMinObservedLfCycles / config.hrvLfLowHz.coerceAtLeast(1e-9)
        val requiredObservedSeconds = maxOf(
            config.hrvSpectralMinObservedSeconds,
            minimumCycleDurationSec
        )

        if (recentCount < config.hrvSpectralMinIbiCount ||
            observedDurationSec < requiredObservedSeconds
        ) {
            val reasons = buildList {
                if (recentCount < config.hrvSpectralMinIbiCount) {
                    add(HrvFrequencyRejectionReason.INSUFFICIENT_IBI_COUNT)
                }
                if (observedDurationSec < requiredObservedSeconds) {
                    add(HrvFrequencyRejectionReason.INSUFFICIENT_OBSERVED_DURATION)
                }
            }
            return HrvFrequencyAssessment(
                usable = false,
                status = MetricCalculationStatus(
                    state = MetricCalculationState.COLLECTING,
                    message = "LF/HF 관찰 구간 수집 중: ${"%.1f".format(observedDurationSec)}/${"%.1f".format(requiredObservedSeconds)}초, IBI=$recentCount"
                ),
                rejectionReasons = reasons,
                rejectionDetails = "observedSec=${"%.3f".format(observedDurationSec)},requiredSec=${"%.3f".format(requiredObservedSeconds)},recentIbi=$recentCount",
                rawIbiCount = rawCount,
                cleanedIbiCount = cleanedCount,
                recentIbiCount = recentCount,
                observedDurationSec = observedDurationSec,
                ppgSignalQuality = ppgSignalQuality,
                respiratoryRateBpm = respiratoryRateBpm
            )
        }

        val resampled = resampleIbiToEvenTimeSeries(
            ibis = recentIbis,
            resampleRateHz = config.hrvResampleRateHz
        ) ?: return HrvFrequencyAssessment(
            usable = false,
            status = MetricCalculationStatus(
                state = MetricCalculationState.REJECTED,
                message = "LF/HF용 IBI 등간격 보간 실패"
            ),
            rejectionReasons = listOf(HrvFrequencyRejectionReason.RESAMPLING_FAILED),
            rejectionDetails = "recentIbi=$recentCount,observedSec=${"%.3f".format(observedDurationSec)}",
            rawIbiCount = rawCount,
            cleanedIbiCount = cleanedCount,
            recentIbiCount = recentCount,
            observedDurationSec = observedDurationSec,
            ppgSignalQuality = ppgSignalQuality,
            respiratoryRateBpm = respiratoryRateBpm
        )

        val minimumResampledCount =
            (requiredObservedSeconds * config.hrvResampleRateHz).toInt().coerceAtLeast(32)
        if (resampled.size < minimumResampledCount) {
            return HrvFrequencyAssessment(
                usable = false,
                status = MetricCalculationStatus(
                    state = MetricCalculationState.REJECTED,
                    message = "LF/HF 보간 sample 부족"
                ),
                rejectionReasons = listOf(HrvFrequencyRejectionReason.RESAMPLED_SAMPLE_COUNT_TOO_LOW),
                rejectionDetails = "resampled=${resampled.size},required=$minimumResampledCount",
                rawIbiCount = rawCount,
                cleanedIbiCount = cleanedCount,
                recentIbiCount = recentCount,
                observedDurationSec = observedDurationSec,
                resampledCount = resampled.size,
                ppgSignalQuality = ppgSignalQuality,
                respiratoryRateBpm = respiratoryRateBpm
            )
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
        val lfHfRatio = if (hfPower <= 1e-12) {
            Double.POSITIVE_INFINITY
        } else {
            lfPower / hfPower
        }

        val countScore = (recentCount / 80.0).coerceIn(0.0, 1.0)
        val durationScore =
            (observedDurationSec / config.hrvFrequencyWindowSeconds).coerceIn(0.0, 1.0)
        val ppgQualityScore = (ppgSignalQuality ?: 0.0).coerceIn(0.0, 1.0)
        val finiteRatioScore = if (lfHfRatio.isFinite()) 1.0 else 0.0
        val qualityScore = (
                countScore * 0.25 +
                        durationScore * 0.30 +
                        ppgQualityScore * 0.30 +
                        finiteRatioScore * 0.15
                ).coerceIn(0.0, 1.0)

        val candidate = HrvFrequencyResult(
            lfPower = lfPower,
            hfPower = hfPower,
            lfHfRatio = lfHfRatio,
            resampledCount = resampled.size,
            ibiCount = recentCount,
            observedDurationSec = observedDurationSec,
            score = scoreHrvLfHf(lfHfRatio),
            qualityScore = qualityScore,
            log = "HRV LF/HF candidate: lf=${"%.9f".format(lfPower)}, " +
                    "hf=${"%.9f".format(hfPower)}, ratio=${formatDoubleSafe(lfHfRatio)}, " +
                    "ibi=$recentCount, observed=${"%.1f".format(observedDurationSec)}s, " +
                    "ppgQ=${ppgSignalQuality?.let { "%.2f".format(it) } ?: "N/A"}, " +
                    "rr=${respiratoryRateBpm?.let { "%.2f".format(it) } ?: "N/A"}, " +
                    "q=${"%.2f".format(qualityScore)}"
        )

        val reasons = mutableListOf<HrvFrequencyRejectionReason>()
        val details = mutableListOf<String>()

        if (heartRateSignalStatus.state != MetricCalculationState.VALID) {
            reasons += HrvFrequencyRejectionReason.PPG_SIGNAL_STATUS_NOT_VALID
            details += "ppgStatus=${heartRateSignalStatus.state}"
        }
        if (ppgSignalQuality == null ||
            ppgSignalQuality < config.hrvFrequencyMinPpgSignalQuality
        ) {
            reasons += HrvFrequencyRejectionReason.PPG_SIGNAL_QUALITY_TOO_LOW
            details += "ppgQ=${ppgSignalQuality ?: Double.NaN},minPpgQ=${config.hrvFrequencyMinPpgSignalQuality}"
        }

        val hfMinBpm = config.hrvHfLowHz * 60.0
        val hfMaxBpm = config.hrvHfHighHz * 60.0
        if (respiratoryRateBpm == null || !respiratoryRateBpm.isFinite()) {
            reasons += HrvFrequencyRejectionReason.RESPIRATORY_RATE_UNAVAILABLE
            details += "rr=N/A"
        } else if (respiratoryRateBpm !in hfMinBpm..hfMaxBpm) {
            reasons += HrvFrequencyRejectionReason.RESPIRATORY_RATE_OUTSIDE_HF_BAND
            details += "rr=${"%.3f".format(respiratoryRateBpm)},hfBpm=${"%.1f".format(hfMinBpm)}-${"%.1f".format(hfMaxBpm)}"
        }

        if (lfPower <= 0.0 && hfPower <= 0.0) {
            reasons += HrvFrequencyRejectionReason.NO_SPECTRAL_POWER
        }
        if (!lfPower.isFinite() || lfPower < config.hrvFrequencyMinLfPower) {
            reasons += HrvFrequencyRejectionReason.LF_POWER_TOO_LOW
            details += "lf=$lfPower,minLf=${config.hrvFrequencyMinLfPower}"
        }
        if (!hfPower.isFinite() || hfPower < config.hrvFrequencyMinHfPower) {
            reasons += HrvFrequencyRejectionReason.HF_POWER_TOO_LOW
            details += "hf=$hfPower,minHf=${config.hrvFrequencyMinHfPower}"
        }
        if (!lfHfRatio.isFinite()) {
            reasons += HrvFrequencyRejectionReason.LF_HF_NOT_FINITE
            details += "ratio=${formatDoubleSafe(lfHfRatio)}"
        } else if (lfHfRatio <= 0.0 || lfHfRatio > config.hrvFrequencyMaxLfHfRatio) {
            reasons += HrvFrequencyRejectionReason.LF_HF_OUT_OF_RANGE
            details += "ratio=${"%.3f".format(lfHfRatio)},maxRatio=${config.hrvFrequencyMaxLfHfRatio}"
        }
        if (qualityScore < config.hrvFrequencyMinQualityScore) {
            reasons += HrvFrequencyRejectionReason.SPECTRAL_QUALITY_TOO_LOW
            details += "frequencyQ=${"%.3f".format(qualityScore)},minQ=${config.hrvFrequencyMinQualityScore}"
        }

        val usable = reasons.isEmpty()
        val message = if (usable) {
            "LF/HF 사용 가능: ratio=${formatDoubleSafe(lfHfRatio)}, q=${"%.2f".format(qualityScore)}"
        } else {
            "LF/HF 사용 불가: ${reasons.joinToString("|") { it.code }}"
        }

        return HrvFrequencyAssessment(
            candidate = candidate,
            usable = usable,
            status = MetricCalculationStatus(
                state = if (usable) MetricCalculationState.VALID else MetricCalculationState.REJECTED,
                message = message
            ),
            rejectionReasons = reasons.distinct(),
            rejectionDetails = details.joinToString(";").takeIf { it.isNotEmpty() },
            rawIbiCount = rawCount,
            cleanedIbiCount = cleanedCount,
            recentIbiCount = recentCount,
            observedDurationSec = observedDurationSec,
            resampledCount = resampled.size,
            ppgSignalQuality = ppgSignalQuality,
            respiratoryRateBpm = respiratoryRateBpm
        )
    }

    private fun observedIbiDurationSec(ibis: List<IbiInterval>): Double {
        if (ibis.size < 2) return 0.0
        val first = ibis.first().endSamplePosition
        val last = ibis.last().endSamplePosition
        if (!first.isFinite() || !last.isFinite() || last <= first) return 0.0
        return (last - first) / config.ppgSampleRateHz
    }

    private fun takeRecentIbisForWindow(
        ibis: List<IbiInterval>,
        windowSeconds: Int
    ): List<IbiInterval> {
        if (ibis.isEmpty()) return emptyList()
        val newestSamplePosition = ibis.last().endSamplePosition
        val minSamplePosition = newestSamplePosition - config.ppgSampleRateHz * windowSeconds
        return ibis.filter {
            it.endSamplePosition.isFinite() && it.endSamplePosition >= minSamplePosition
        }
    }

    private fun combineHrvScores(
        frequencyResult: HrvFrequencyResult?,
        rmssdResult: HeartRateVariabilityResult?,
        frequencyAssessment: HrvFrequencyAssessment
    ): HrvCombinedScore {
        if (frequencyResult == null && rmssdResult == null) {
            return HrvCombinedScore(
                score = null,
                quality = 0.0,
                composition = "NONE",
                log = "HRV score unavailable; LF/HF=${frequencyAssessment.rejectionCodeString ?: "N/A"}; RMSSD=N/A"
            )
        }

        val frequencyPart =
            (frequencyResult?.score ?: 0.0) * config.hrvFrequencyScoreWeight
        val rmssdPart =
            (rmssdResult?.score ?: 0.0) * config.hrvRmssdScoreWeight
        val combinedScore = (frequencyPart + rmssdPart).coerceIn(0.0, 1.0)

        val combinedQuality = (
                (frequencyResult?.qualityScore ?: 0.0) * config.hrvFrequencyScoreWeight +
                        (rmssdResult?.qualityScore ?: 0.0) * config.hrvRmssdScoreWeight
                ).coerceIn(0.0, 1.0)

        val composition = when {
            frequencyResult != null && rmssdResult != null -> "LF_HF_70_RMSSD_30"
            frequencyResult != null -> "LF_HF_70_ONLY_RMSSD_MISSING"
            else -> "RMSSD_30_ONLY_LF_HF_REJECTED"
        }
        val rejection = frequencyAssessment.rejectionCodeString?.let { ", lfHfRejected=$it" } ?: ""
        val log = "HRV combined: score=${"%.3f".format(combinedScore)}, q=${"%.3f".format(combinedQuality)}, " +
                "composition=$composition, lfHf=${frequencyResult?.log ?: "N/A"}, " +
                "rmssd=${rmssdResult?.log ?: "N/A"}$rejection"

        return HrvCombinedScore(
            score = combinedScore,
            quality = combinedQuality,
            composition = composition,
            log = log
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
        return takeRecentIbisForWindow(
            ibis = ibis,
            windowSeconds = config.hrvFrequencyWindowSeconds
        )
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

        val targetSegmentId = ibis.last().segmentId

        val points = ibis
            .filter {
                it.segmentId == targetSegmentId &&
                        it.endSamplePosition.isFinite() &&
                        it.intervalSec.isFinite() &&
                        it.intervalSec > 0.0
            }
            .sortedBy { it.endSamplePosition }
            .map { ibi ->
                val timeSec =
                    ibi.endSamplePosition / config.ppgSampleRateHz

                timeSec to ibi.intervalSec
            }

        if (points.size < 2) return null

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

        val tolerance = config.skinTempOutlierToleranceCelsius
        if (!median.isFinite() || !tolerance.isFinite() || tolerance < 0.0) {
            return emptyList()
        }

        return values.filter { (_, celsius) ->
            celsius.isFinite() &&
                    abs(celsius - median) <= tolerance
        }
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

        // 첫 번째 값을 항상 넣으므로 정상 입력에서는 비어 있지 않다.
        // 향후 로직이 바뀌더라도 원본 이상치를 되살리지 않도록 결과를 그대로 반환한다.
        return result
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