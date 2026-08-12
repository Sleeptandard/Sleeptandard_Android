package com.leejang.sleeptandard.Potch

import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/** 안정점수 및 개인 기준선 계산의 초기 조정값. */
data class StabilityConfig(
    // HRV 안정점수 구조가 변경되어 이전 후보/기준선과 섞이지 않도록 version을 올린다.
    val algorithmVersion: Int = 3,

    val minimumMetricQuality: Double = 0.35,
    val minimumDomainCount: Int = 3,
    val minimumPhysiologicalDomainCount: Int = 2,

    val entryScore: Double = 75.0,
    val maintainScore: Double = 65.0,
    val entryDurationMillis: Long = 120_000L,
    val exitDurationMillis: Long = 30_000L,

    val movementDomainWeight: Double = 0.20,
    val respiratoryDomainWeight: Double = 0.35,
    val cardiacDomainWeight: Double = 0.35,
    val temperatureDomainWeight: Double = 0.10,

    val rrMetricWeight: Double = 0.65,
    val rrvMetricWeight: Double = 0.35,
    val hrMetricWeight: Double = 0.55,
    val hrvMetricWeight: Double = 0.45,

    // HRV 영역 내부 고정 가중치. 사용 불가 구성요소는 0점/0품질 몫으로 남긴다.
    val hrvFrequencyStabilityWeight: Double = 0.70,
    val hrvRmssdStabilityWeight: Double = 0.30,

    val fourDomainCoveragePenalty: Double = 1.00,
    val threeDomainCoveragePenalty: Double = 0.90,

    val maxCandidatesPerSession: Int = 5,
    val minimumCandidateCountForBaseline: Int = 20,
    val matureCandidateCount: Int = 50,
    val baselineUpdateStep: Int = 5,
    val denseRegionFraction: Double = 0.40,
    val maxStoredCandidates: Int = 300,
    val maxCandidateAgeDays: Int = 90,
    val episodeSeparationMillis: Long = 30L * 60L * 1000L,

    // 값의 단위에 맞춘 초기 MAD/slope 정규화 크기. 실제 착용 로그로 재조정해야 한다.
    val rrVariationScaleBpm: Double = 1.5,
    val rrSlopeScalePerMinute: Double = 1.0,
    val rrvVariationScaleSec: Double = 0.20,
    val rrvSlopeScaleSecPerMinute: Double = 0.10,
    val hrVariationScaleBpm: Double = 6.0,
    val hrSlopeScalePerMinute: Double = 1.0,
    val hrvVariationScaleSec: Double = 0.040,
    val hrvSlopeScaleSecPerMinute: Double = 0.020,
    val hrvLfHfVariationScale: Double = 0.75,
    val hrvLfHfSlopeScalePerMinute: Double = 0.50,
    val temperatureVariationScaleCelsius: Double = 0.15,
    val temperatureSlopeScalePerMinute: Double = 0.03,

    val rrMinimumBaselineScale: Double = 0.50,
    val rrvMinimumBaselineScale: Double = 0.05,
    val hrMinimumBaselineScale: Double = 2.0,
    val hrvMinimumBaselineScale: Double = 0.01,
    val hrvLfHfMinimumBaselineScale: Double = 0.15,
    val temperatureMinimumBaselineScale: Double = 0.05,

    val rrConsistencyWindowMillis: Long = 60_000L,
    val rrSlopeWindowMillis: Long = 120_000L,
    val rrvConsistencyWindowMillis: Long = 60_000L,
    val rrvSlopeWindowMillis: Long = 120_000L,
    val hrConsistencyWindowMillis: Long = 60_000L,
    val hrSlopeWindowMillis: Long = 180_000L,
    val hrvConsistencyWindowMillis: Long = 60_000L,
    val hrvSlopeWindowMillis: Long = 120_000L,
    val hrvLfHfConsistencyWindowMillis: Long = 120_000L,
    val hrvLfHfSlopeWindowMillis: Long = 120_000L,
    val temperatureConsistencyWindowMillis: Long = 120_000L,
    val temperatureSlopeWindowMillis: Long = 300_000L,

    val minimumHistoryDurationMillis: Long = 45_000L,
    val minimumHistorySampleCount: Int = 20,
    val maxHistoryMillis: Long = 10L * 60L * 1000L
)

enum class StabilityEpisodePhase {
    IDLE,
    ENTERING,
    STABLE
}

data class MetricStabilityScore(
    val metricType: BaselineMetricType,
    val value: Double,
    val score: Double,
    val quality: Double,
    val initialScore: Double,
    val personalScore: Double?,
    val baselineProximity: Double?,
    val withinWindowConsistency: Double,
    val slopeStability: Double,
    val signalConsistency: Double,
    val baselineState: BaselineLifecycleState
)

data class DomainStabilityScore(
    val score: Double,
    val quality: Double
)

data class StabilityState(
    val hardGatePassed: Boolean = false,
    val hardGateReason: String = "안정점수 계산 대기",
    val phase: StabilityEpisodePhase = StabilityEpisodePhase.IDLE,
    val overallStabilityScore: Double? = null,
    val movementStabilityScore: Double? = null,
    val respiratoryStabilityScore: Double? = null,
    val cardiacStabilityScore: Double? = null,
    val temperatureStabilityScore: Double? = null,
    val rrStabilityScore: Double? = null,
    val rrvStabilityScore: Double? = null,
    val hrStabilityScore: Double? = null,
    val hrvStabilityScore: Double? = null,
    val hrvRmssdStabilityScore: Double? = null,
    val hrvLfHfStabilityScore: Double? = null,
    val hrvFrequencyUsable: Boolean = false,
    val hrvFrequencyRejectionReasons: String? = null,
    val usedDomainCount: Int = 0,
    val enteringDurationSec: Int = 0,
    val activeEpisodeDurationSec: Int = 0,
    val sessionCandidateCount: Int = 0,
    /** 현재 수면 세션 시작 시 고정되어 실제 계산에 사용 중인 개인 기준선. */
    val activeBaselines: Map<BaselineMetricType, PersonalBaselineRecord> = emptyMap(),
    val baselineStates: Map<BaselineMetricType, BaselineLifecycleState> = emptyMap(),
    val lastLog: String = "No stability data yet"
)

data class StabilityFrameInput(
    val phoneTimeMillis: Long,
    val sensorTimestamp: Long,
    val arousalState: ArousalState,
    val heartRateDiagnostics: HeartRateDiagnostics,
    val analysisSegmentId: Long,
    val continuityBreakCount: Int,
    val crcErrorCount: Int,
    val sequenceLossCount: Int,
    val estimatedLostPacketCount: Int
)

data class StabilitySessionSummary(
    val sessionId: String?,
    val detectedEpisodeCount: Int,
    val savedCandidateCount: Int,
    val baselineUpdateCount: Int,
    val totalStoredCandidateCount: Int
)

private data class TimedMetricValue(
    val timestampMillis: Long,
    val value: Double,
    val quality: Double
)

private data class StabilityFrameSample(
    val timestampMillis: Long,
    val sensorTimestamp: Long,
    val analysisSegmentId: Long,
    val rr: Double?,
    val rrv: Double?,
    val hr: Double?,
    val hrvRmssd: Double?,
    val hrvLf: Double?,
    val hrvHf: Double?,
    val hrvLfHf: Double?,
    val hrvFrequencyUsable: Boolean,
    val hrvFrequencyRejectionReasons: String?,
    val temperature: Double?,
    val temperatureSlope: Double?,
    val rrQuality: Double?,
    val rrvQuality: Double?,
    val hrQuality: Double?,
    val hrvQuality: Double?,
    val hrvRmssdQuality: Double?,
    val hrvFrequencyQuality: Double?,
    val temperatureQuality: Double?,
    val movementScore: Double?,
    val respiratoryScore: Double?,
    val cardiacScore: Double?,
    val hrvRmssdStabilityScore: Double?,
    val hrvLfHfStabilityScore: Double?,
    val temperatureScore: Double?,
    val overallScore: Double,
    val usedDomainCount: Int,
    val reconnectCount: Int,
    val continuityBreakCount: Int,
    val packetLossCount: Int
)

private data class EpisodeAccumulator(
    val episodeId: String,
    val startedAt: Long,
    val analysisSegmentId: Long,
    val startReconnectCount: Int,
    val startContinuityBreakCount: Int,
    val startPacketLossCount: Int,
    val samples: MutableList<StabilityFrameSample>
)

private data class SessionCandidateDecision(
    val candidate: StableCandidateRecord,
    val detectedIndex: Int,
    val selectedForCandidateTable: Boolean,
    val selectionRank: Int?,
    val selectionReason: String
)

/**
 * 각성점수와 별도로 안정점수를 계산하고, 안정 episode 후보와 개인 기준선을 관리한다.
 *
 * 생명주기:
 * 1. Service 수신 세션 시작 시 startSession() - 기존 기준선을 메모리에 고정한다.
 * 2. 완성된 1초 Burst마다 processFrame() - 하드게이트/지표/영역/전체 안정점수를 계산한다.
 * 3. 75점 이상 120초 지속 시 안정 episode에 진입한다.
 * 4. 65점 미만 30초 또는 hard gate 실패 시 episode를 종료한다.
 * 5. Service 정상 종료 시 endSession() - 최대 5개 후보를 DB에 저장하고 기준선을 재계산한다.
 */
class PotchStabilityCalculator(
    private val stableCandidateTable: StableCandidateTable,
    private val personalBaselineTable: PersonalBaselineTable,
    private val dataLogger: PotchDataLogger? = null,
    private val config: StabilityConfig = StabilityConfig()
) {
    private val histories = BaselineMetricType.values().associateWith { ArrayDeque<TimedMetricValue>() }
    private val hrvLfHfHistory = ArrayDeque<TimedMetricValue>()

    private var sessionId: String? = null
    private var sessionActive = false
    private var frozenBaselines: Map<BaselineMetricType, PersonalBaselineRecord> = emptyMap()

    private var phase = StabilityEpisodePhase.IDLE
    private var entryStartedAt: Long? = null
    private var belowMaintainStartedAt: Long? = null
    private val enteringSamples = mutableListOf<StabilityFrameSample>()
    private var activeEpisode: EpisodeAccumulator? = null
    private val sessionCandidates = mutableListOf<StableCandidateRecord>()

    private var lastAnalysisSegmentId: Long? = null
    private var lastFrameTimestampMillis: Long? = null
    private var currentReconnectCount = 0
    private var hasEverConnected = false
    private var lastBleConnected = false

    private var lastState = StabilityState()

    @Synchronized
    fun startSession(requestedSessionId: String = UUID.randomUUID().toString()): String {
        if (sessionActive) return sessionId ?: requestedSessionId

        sessionId = requestedSessionId
        sessionActive = true
        frozenBaselines = personalBaselineTable.loadAll(config.algorithmVersion)

        clearTransientState(clearSessionCandidates = true)
        log(
            "stability session started: id=$requestedSessionId, " +
                    "baselines=${frozenBaselines.mapValues { it.value.lifecycleState }}"
        )
        return requestedSessionId
    }

    /** BLE 재연결 횟수만 별도로 보존하기 위한 상태 알림. */
    @Synchronized
    fun onBleConnectionState(isConnected: Boolean) {
        if (isConnected && !lastBleConnected) {
            if (hasEverConnected) currentReconnectCount += 1
            hasEverConnected = true
        }
        lastBleConnected = isConnected
    }

    /** 패킷 누락, CRC 오류, 연결 reset 등 연속성 경계에서 즉시 episode를 종료한다. */
    @Synchronized
    fun onContinuityBreak(reason: String, newSegmentId: Long) {
        if (!sessionActive) return

        val endTime = lastFrameTimestampMillis ?: System.currentTimeMillis()
        finalizeActiveEpisode(endTime, "continuity break: $reason")
        resetEntryState()
        histories.values.forEach { it.clear() }
        hrvLfHfHistory.clear()
        lastAnalysisSegmentId = newSegmentId

        lastState = lastState.copy(
            hardGatePassed = false,
            hardGateReason = "데이터 연속성 중단: $reason",
            phase = StabilityEpisodePhase.IDLE,
            overallStabilityScore = null,
            enteringDurationSec = 0,
            activeEpisodeDurationSec = 0,
            lastLog = "Stability reset at segment=$newSegmentId: $reason"
        )
        log(lastState.lastLog, "W")
    }

    @Synchronized
    fun processFrame(input: StabilityFrameInput): StabilityState {
        if (!sessionActive) startSession()

        if (lastAnalysisSegmentId != null && lastAnalysisSegmentId != input.analysisSegmentId) {
            onContinuityBreak(
                reason = "analysis segment changed ${lastAnalysisSegmentId}->${input.analysisSegmentId}",
                newSegmentId = input.analysisSegmentId
            )
        }
        lastAnalysisSegmentId = input.analysisSegmentId
        lastFrameTimestampMillis = input.phoneTimeMillis

        val hardGateFailure = hardGateFailure(input)
        if (hardGateFailure != null) {
            handleHardGateFailure(input.phoneTimeMillis, hardGateFailure)
            return lastState
        }

        val state = input.arousalState
        val diagnostics = input.heartRateDiagnostics

        val rrQuality = state.rrFusionConfidence.coerceIn(0.0, 1.0)
        val rrvQuality = state.rrvQuality.coerceIn(0.0, 1.0)
        val hrQuality = (diagnostics.qualityScore ?: 0.0).coerceIn(0.0, 1.0)
        val hrvRmssdQuality = state.hrvRmssdQuality.coerceIn(0.0, 1.0)
        val hrvFrequencyQuality = state.hrvFrequencyQuality.coerceIn(0.0, 1.0)
        val temperatureQuality = state.skinTemperatureQuality.coerceIn(0.0, 1.0)

        val rrSignalConsistency = calculateRrSignalConsistency(state)
        val rrvSignalConsistency = calculateRrvSignalConsistency(state)
        val hrSignalConsistency = calculateHrSignalConsistency(diagnostics)
        val hrvRmssdSignalConsistency = calculateHrvRmssdSignalConsistency(state)
        val hrvFrequencySignalConsistency = calculateHrvFrequencySignalConsistency(state)
        val temperatureSignalConsistency = calculateTemperatureSignalConsistency(state)

        val rrScore = addAndCalculateMetric(
            metricType = BaselineMetricType.RR,
            value = state.rrFinal,
            quality = rrQuality,
            signalConsistency = rrSignalConsistency,
            nowMillis = input.phoneTimeMillis
        )
        val rrvScore = addAndCalculateMetric(
            metricType = BaselineMetricType.RRV,
            value = state.rrvRmssd,
            quality = rrvQuality,
            signalConsistency = rrvSignalConsistency,
            nowMillis = input.phoneTimeMillis
        )
        val hrScore = addAndCalculateMetric(
            metricType = BaselineMetricType.HR,
            value = state.hrBpm?.toDouble(),
            quality = hrQuality,
            signalConsistency = hrSignalConsistency,
            nowMillis = input.phoneTimeMillis
        )
        val hrvRmssdScore = addAndCalculateMetric(
            metricType = BaselineMetricType.HRV_RMSSD,
            value = state.hrvRmssd,
            quality = hrvRmssdQuality,
            signalConsistency = hrvRmssdSignalConsistency,
            nowMillis = input.phoneTimeMillis
        )
        val hrvLfHfScore = addAndCalculateHrvFrequencyMetric(
            state = state,
            quality = hrvFrequencyQuality,
            signalConsistency = hrvFrequencySignalConsistency,
            nowMillis = input.phoneTimeMillis
        )
        val hrvScore = combineHrvStability(
            frequency = hrvLfHfScore,
            rmssd = hrvRmssdScore
        )
        val temperatureScore = addAndCalculateMetric(
            metricType = BaselineMetricType.TEMPERATURE,
            value = state.skinTemperatureCelsius,
            quality = temperatureQuality,
            signalConsistency = temperatureSignalConsistency,
            nowMillis = input.phoneTimeMillis
        )

        val movementDomain = calculateMovementDomain(state)
        val respiratoryDomain = combineMetrics(
            listOf(
                rrScore to config.rrMetricWeight,
                rrvScore to config.rrvMetricWeight
            )
        )
        val cardiacDomain = combineCardiacMetrics(
            hr = hrScore,
            hrv = hrvScore
        )
        val temperatureDomain = temperatureScore?.let {
            DomainStabilityScore(it.score, it.quality)
        }

        val domains = listOfNotNull(
            movementDomain?.let { Triple(it, config.movementDomainWeight, "movement") },
            respiratoryDomain?.let { Triple(it, config.respiratoryDomainWeight, "respiratory") },
            cardiacDomain?.let { Triple(it, config.cardiacDomainWeight, "cardiac") },
            temperatureDomain?.let { Triple(it, config.temperatureDomainWeight, "temperature") }
        )

        val physiologicalDomainCount = listOfNotNull(
            respiratoryDomain,
            cardiacDomain,
            temperatureDomain
        ).size
        val usedDomainCount = domains.size

        val overallScore = if (
            movementDomain != null &&
            physiologicalDomainCount >= config.minimumPhysiologicalDomainCount &&
            usedDomainCount >= config.minimumDomainCount
        ) {
            val weightedDenominator = domains.sumOf { (domain, baseWeight, _) ->
                baseWeight * domain.quality
            }
            if (weightedDenominator <= 0.0) {
                null
            } else {
                val weightedScore = domains.sumOf { (domain, baseWeight, _) ->
                    domain.score * baseWeight * domain.quality
                } / weightedDenominator

                val coveragePenalty = when (usedDomainCount) {
                    4 -> config.fourDomainCoveragePenalty
                    3 -> config.threeDomainCoveragePenalty
                    else -> 0.0
                }
                (weightedScore * coveragePenalty).coerceIn(0.0, 100.0)
            }
        } else {
            null
        }

        val frameSample = overallScore?.let {
            StabilityFrameSample(
                timestampMillis = input.phoneTimeMillis,
                sensorTimestamp = input.sensorTimestamp,
                analysisSegmentId = input.analysisSegmentId,
                rr = rrScore?.value,
                rrv = rrvScore?.value,
                hr = hrScore?.value,
                hrvRmssd = hrvRmssdScore?.value,
                // 사용 제한을 통과한 주파수 값만 안정 episode 개인 기준선 후보에 포함한다.
                hrvLf = state.hrvLf.takeIf { state.hrvFrequencyUsable },
                hrvHf = state.hrvHf.takeIf { state.hrvFrequencyUsable },
                hrvLfHf = state.hrvLfHf.takeIf { state.hrvFrequencyUsable },
                hrvFrequencyUsable = state.hrvFrequencyUsable,
                hrvFrequencyRejectionReasons = state.hrvFrequencyRejectionReasons,
                temperature = temperatureScore?.value,
                temperatureSlope = state.skinTemperatureGradient,
                rrQuality = rrScore?.quality,
                rrvQuality = rrvScore?.quality,
                hrQuality = hrScore?.quality,
                hrvQuality = hrvScore?.quality,
                hrvRmssdQuality = hrvRmssdScore?.quality,
                hrvFrequencyQuality = hrvLfHfScore?.quality ?: state.hrvFrequencyQuality,
                temperatureQuality = temperatureScore?.quality,
                movementScore = movementDomain?.score,
                respiratoryScore = respiratoryDomain?.score,
                cardiacScore = cardiacDomain?.score,
                hrvRmssdStabilityScore = hrvRmssdScore?.score,
                hrvLfHfStabilityScore = hrvLfHfScore?.score,
                temperatureScore = temperatureDomain?.score,
                overallScore = it,
                usedDomainCount = usedDomainCount,
                reconnectCount = currentReconnectCount,
                continuityBreakCount = input.continuityBreakCount,
                packetLossCount = input.estimatedLostPacketCount
            )
        }

        updateEpisodeState(input.phoneTimeMillis, overallScore, frameSample)

        val enteringDuration = entryStartedAt?.let {
            ((input.phoneTimeMillis - it).coerceAtLeast(0L) / 1000L).toInt()
        } ?: 0
        val activeDuration = activeEpisode?.let {
            ((input.phoneTimeMillis - it.startedAt).coerceAtLeast(0L) / 1000L).toInt()
        } ?: 0

        lastState = StabilityState(
            hardGatePassed = true,
            hardGateReason = "통과",
            phase = phase,
            overallStabilityScore = overallScore,
            movementStabilityScore = movementDomain?.score,
            respiratoryStabilityScore = respiratoryDomain?.score,
            cardiacStabilityScore = cardiacDomain?.score,
            temperatureStabilityScore = temperatureDomain?.score,
            rrStabilityScore = rrScore?.score,
            rrvStabilityScore = rrvScore?.score,
            hrStabilityScore = hrScore?.score,
            hrvStabilityScore = hrvScore?.score,
            hrvRmssdStabilityScore = hrvRmssdScore?.score,
            hrvLfHfStabilityScore = hrvLfHfScore?.score,
            hrvFrequencyUsable = state.hrvFrequencyUsable,
            hrvFrequencyRejectionReasons = state.hrvFrequencyRejectionReasons,
            usedDomainCount = usedDomainCount,
            enteringDurationSec = enteringDuration,
            activeEpisodeDurationSec = activeDuration,
            sessionCandidateCount = sessionCandidates.size,
            activeBaselines = frozenBaselines,
            baselineStates = baselineStateMap(),
            lastLog = buildString {
                append("stability=")
                append(overallScore?.let { "%.1f".format(it) } ?: "N/A")
                append(", phase=$phase, domains=$usedDomainCount")
                append(", rr=${rrScore?.score?.let { "%.1f".format(it) }}")
                append(", rrv=${rrvScore?.score?.let { "%.1f".format(it) }}")
                append(", hr=${hrScore?.score?.let { "%.1f".format(it) }}")
                append(", hrv=${hrvScore?.score?.let { "%.1f".format(it) }}")
                append("(lfhf=${hrvLfHfScore?.score?.let { "%.1f".format(it) }},")
                append("rmssd=${hrvRmssdScore?.score?.let { "%.1f".format(it) }})")
                if (!state.hrvFrequencyUsable) {
                    append(", lfhfRejected=${state.hrvFrequencyRejectionReasons ?: "UNKNOWN"}")
                }
            }
        )

        return lastState
    }

    @Synchronized
    fun currentState(): StabilityState = lastState

    /**
     * 현재 수면 세션 시작 시 고정한 개인 기준선 snapshot.
     * ArousalCalculator가 같은 기준선을 사용하도록 복사본을 반환한다.
     */
    @Synchronized
    fun activeBaselinesSnapshot(): Map<BaselineMetricType, PersonalBaselineRecord> =
        frozenBaselines.toMap()

    /**
     * 현재 수면 세션을 종료하고 안정 후보를 저장한 뒤 개인 기준선을 갱신한다.
     * 같은 세션 도중에는 frozenBaselines를 바꾸지 않으므로 자기참조 학습이 발생하지 않는다.
     */
    @Synchronized
    fun endSession(nowMillis: Long = System.currentTimeMillis()): StabilitySessionSummary {
        if (!sessionActive) {
            return StabilitySessionSummary(
                sessionId = sessionId,
                detectedEpisodeCount = sessionCandidates.size,
                savedCandidateCount = 0,
                baselineUpdateCount = 0,
                totalStoredCandidateCount = stableCandidateTable.countAll()
            )
        }

        finalizeActiveEpisode(nowMillis, "session end")
        resetEntryState()

        val decisions = selectSessionCandidateDecisions(sessionCandidates)
        val selectedCandidates = decisions
            .filter { it.selectedForCandidateTable }
            .map { it.candidate }

        stableCandidateTable.insertAll(selectedCandidates)
        stableCandidateTable.prune(
            nowMillis = nowMillis,
            maxAgeDays = config.maxCandidateAgeDays,
            maxRecordCount = config.maxStoredCandidates
        )

        // INSERT IGNORE, START_STICKY 동일 세션 재개, DB 수준 최대 5개 trim까지 반영한
        // 최종 후보 테이블 잔존 여부를 다시 조회한다.
        val currentSessionId = sessionId
        val storedEpisodeIds = currentSessionId
            ?.let(stableCandidateTable::loadEpisodeIds)
            .orEmpty()

        decisions.forEach { decision ->
            val stored = decision.candidate.episodeId in storedEpisodeIds
            val finalReason = when {
                !decision.selectedForCandidateTable -> decision.selectionReason
                stored -> decision.selectionReason
                else -> "${decision.selectionReason}; DB_NOT_RETAINED_OR_INSERT_CONFLICT"
            }
            dataLogger?.logStabilityEpisode(
                StabilityEpisodeLogRecord(
                    candidate = decision.candidate,
                    detectedIndex = decision.detectedIndex,
                    detectedEpisodeCount = sessionCandidates.size,
                    selectedForCandidateTable = decision.selectedForCandidateTable,
                    storedInCandidateTable = stored,
                    selectionRank = decision.selectionRank,
                    selectionReason = finalReason,
                    candidateAverageQuality = candidateAverageQuality(decision.candidate),
                    activeBaselines = frozenBaselines
                )
            )
        }

        val updatedBaselineCount = recalculatePersonalBaselines(nowMillis)
        val totalStored = stableCandidateTable.countAll()
        val retainedCurrentSessionCount = decisions.count {
            it.selectedForCandidateTable && it.candidate.episodeId in storedEpisodeIds
        }

        val summary = StabilitySessionSummary(
            sessionId = sessionId,
            detectedEpisodeCount = sessionCandidates.size,
            savedCandidateCount = retainedCurrentSessionCount,
            baselineUpdateCount = updatedBaselineCount,
            totalStoredCandidateCount = totalStored
        )

        log(
            "stability session ended: id=${sessionId}, detected=${sessionCandidates.size}, " +
                    "retained=$retainedCurrentSessionCount, baselineUpdated=$updatedBaselineCount, total=$totalStored"
        )

        sessionActive = false
        clearTransientState(clearSessionCandidates = true)
        return summary
    }

    private fun hardGateFailure(input: StabilityFrameInput): String? {
        val state = input.arousalState
        val diagnostics = input.heartRateDiagnostics

        if (state.microMovementScore == null || state.microMovementLevel == null) {
            return "움직임 영역 수집 중"
        }
        if (state.isMacroMovementLike) return "Macro movement 감지"

        if (
            diagnostics.processingState == HeartRateProcessingState.NO_CONTACT ||
            diagnostics.processingState == HeartRateProcessingState.SIGNAL_SATURATED
        ) {
            return "PPG 센서 접촉/포화 이상"
        }

        if (input.analysisSegmentId < 0L) return "유효하지 않은 analysis segment"
        return null
    }

    private fun handleHardGateFailure(nowMillis: Long, reason: String) {
        finalizeActiveEpisode(nowMillis, "hard gate: $reason")
        resetEntryState()
        lastState = lastState.copy(
            hardGatePassed = false,
            hardGateReason = reason,
            phase = StabilityEpisodePhase.IDLE,
            overallStabilityScore = null,
            enteringDurationSec = 0,
            activeEpisodeDurationSec = 0,
            sessionCandidateCount = sessionCandidates.size,
            activeBaselines = frozenBaselines,
            baselineStates = baselineStateMap(),
            lastLog = "Hard gate rejected: $reason"
        )
    }

    private fun addAndCalculateMetric(
        metricType: BaselineMetricType,
        value: Double?,
        quality: Double,
        signalConsistency: Double,
        nowMillis: Long
    ): MetricStabilityScore? {
        if (value == null || !value.isFinite()) return null
        if (quality < config.minimumMetricQuality) return null

        val history = histories.getValue(metricType)
        history.add(TimedMetricValue(nowMillis, value, quality))
        trimHistory(history, nowMillis)

        val consistencyWindow = consistencyWindow(metricType)
        val slopeWindow = slopeWindow(metricType)
        val consistency = calculateWithinWindowConsistency(
            history = history,
            nowMillis = nowMillis,
            windowMillis = consistencyWindow,
            variationScale = variationScale(metricType)
        ) ?: return null
        val slopeStability = calculateSlopeStability(
            history = history,
            nowMillis = nowMillis,
            windowMillis = slopeWindow,
            slopeScalePerMinute = slopeScale(metricType)
        ) ?: return null

        val initialScore = 100.0 * (
                consistency * 0.50 +
                        slopeStability * 0.30 +
                        signalConsistency.coerceIn(0.0, 1.0) * 0.20
                )

        val baseline = frozenBaselines[metricType]
        val proximity = baseline?.takeIf { it.isUsable }?.let {
            calculateBaselineProximity(metricType, value, it)
        }
        val personalScore = proximity?.let {
            100.0 * (
                    it * 0.50 +
                            consistency * 0.30 +
                            slopeStability * 0.20
                    )
        }

        val finalScore = when (baseline?.lifecycleState) {
            BaselineLifecycleState.PROVISIONAL -> {
                initialScore * 0.60 + (personalScore ?: initialScore) * 0.40
            }
            BaselineLifecycleState.MATURE -> {
                initialScore * 0.20 + (personalScore ?: initialScore) * 0.80
            }
            else -> initialScore
        }.coerceIn(0.0, 100.0)

        return MetricStabilityScore(
            metricType = metricType,
            value = value,
            score = finalScore,
            quality = quality.coerceIn(0.0, 1.0),
            initialScore = initialScore.coerceIn(0.0, 100.0),
            personalScore = personalScore?.coerceIn(0.0, 100.0),
            baselineProximity = proximity,
            withinWindowConsistency = consistency,
            slopeStability = slopeStability,
            signalConsistency = signalConsistency.coerceIn(0.0, 1.0),
            baselineState = baseline?.lifecycleState ?: BaselineLifecycleState.EMPTY
        )
    }


    /**
     * LF/HF도 안정 episode에서 만든 개인 기준선과 비교한다.
     * 주파수영역 사용 제한을 통과한 frame만 history와 안정점수에 반영한다.
     */
    private fun addAndCalculateHrvFrequencyMetric(
        state: ArousalState,
        quality: Double,
        signalConsistency: Double,
        nowMillis: Long
    ): DomainStabilityScore? {
        if (!state.hrvFrequencyUsable) return null
        val metric = addAndCalculateMetric(
            metricType = BaselineMetricType.HRV_LF_HF,
            value = state.hrvLfHf,
            quality = quality,
            signalConsistency = signalConsistency,
            nowMillis = nowMillis
        ) ?: return null
        return DomainStabilityScore(
            score = metric.score,
            quality = metric.quality
        )
    }

    /**
     * LF/HF 70%, RMSSD 30%를 기본 비율로 사용한다.
     * 사용할 수 없는 구성요소는 점수 분모에서 제외하되 전체 quality는 그 몫만큼 낮아진다.
     */
    private fun combineHrvStability(
        frequency: DomainStabilityScore?,
        rmssd: MetricStabilityScore?
    ): DomainStabilityScore? {
        if (frequency == null && rmssd == null) return null

        val parts = listOfNotNull(
            frequency?.let {
                Triple(it.score, it.quality, config.hrvFrequencyStabilityWeight)
            },
            rmssd?.let {
                Triple(it.score, it.quality, config.hrvRmssdStabilityWeight)
            }
        )
        val effectiveWeight = parts.sumOf { (_, quality, baseWeight) ->
            quality.coerceIn(0.0, 1.0) * baseWeight
        }
        if (effectiveWeight <= 0.0) return null

        val score = parts.sumOf { (score, quality, baseWeight) ->
            score * quality.coerceIn(0.0, 1.0) * baseWeight
        } / effectiveWeight

        val totalDesignedWeight =
            config.hrvFrequencyStabilityWeight + config.hrvRmssdStabilityWeight
        val quality = parts.sumOf { (_, q, baseWeight) ->
            q.coerceIn(0.0, 1.0) * baseWeight
        } / totalDesignedWeight.coerceAtLeast(1e-9)

        return DomainStabilityScore(
            score = score.coerceIn(0.0, 100.0),
            quality = quality.coerceIn(0.0, 1.0)
        )
    }

    private fun combineCardiacMetrics(
        hr: MetricStabilityScore?,
        hrv: DomainStabilityScore?
    ): DomainStabilityScore? {
        if (hr == null && hrv == null) return null
        val weighted = listOfNotNull(
            hr?.let { Triple(it.score, it.quality, config.hrMetricWeight) },
            hrv?.let { Triple(it.score, it.quality, config.hrvMetricWeight) }
        )
        val denominator = weighted.sumOf { (_, quality, weight) -> quality * weight }
        if (denominator <= 0.0) return null
        val score = weighted.sumOf { (score, quality, weight) -> score * quality * weight } / denominator
        val baseWeightSum = weighted.sumOf { it.third }
        val quality = weighted.sumOf { (_, q, w) -> q * w } / baseWeightSum.coerceAtLeast(1e-9)
        return DomainStabilityScore(score.coerceIn(0.0, 100.0), quality.coerceIn(0.0, 1.0))
    }

    private fun calculateMovementDomain(state: ArousalState): DomainStabilityScore? {
        val rawMovementScore = state.microMovementScore ?: return null
        if (state.isMacroMovementLike) return null

        val stability = (100.0 - rawMovementScore.coerceIn(0.0, 100.0))
            .coerceIn(0.0, 100.0)
        return DomainStabilityScore(score = stability, quality = 1.0)
    }

    private fun combineMetrics(
        metrics: List<Pair<MetricStabilityScore?, Double>>
    ): DomainStabilityScore? {
        val available = metrics.mapNotNull { (metric, baseWeight) ->
            metric?.let { Triple(it, baseWeight, baseWeight * it.quality) }
        }
        if (available.isEmpty()) return null

        val effectiveWeightSum = available.sumOf { it.third }
        if (effectiveWeightSum <= 0.0) return null

        val score = available.sumOf { (metric, _, effectiveWeight) ->
            metric.score * effectiveWeight
        } / effectiveWeightSum

        val baseWeightSum = available.sumOf { it.second }
        val quality = if (baseWeightSum <= 0.0) 0.0 else {
            available.sumOf { (metric, baseWeight, _) -> metric.quality * baseWeight } /
                    baseWeightSum
        }

        return DomainStabilityScore(
            score = score.coerceIn(0.0, 100.0),
            quality = quality.coerceIn(0.0, 1.0)
        )
    }

    private fun updateEpisodeState(
        nowMillis: Long,
        overallScore: Double?,
        frameSample: StabilityFrameSample?
    ) {
        when (phase) {
            StabilityEpisodePhase.IDLE -> {
                if (overallScore != null && overallScore >= config.entryScore && frameSample != null) {
                    phase = StabilityEpisodePhase.ENTERING
                    entryStartedAt = nowMillis
                    enteringSamples.clear()
                    enteringSamples += frameSample
                }
            }

            StabilityEpisodePhase.ENTERING -> {
                if (overallScore != null && overallScore >= config.entryScore && frameSample != null) {
                    enteringSamples += frameSample
                    val start = entryStartedAt ?: nowMillis
                    if (nowMillis - start >= config.entryDurationMillis) {
                        phase = StabilityEpisodePhase.STABLE
                        activeEpisode = EpisodeAccumulator(
                            episodeId = UUID.randomUUID().toString(),
                            startedAt = start,
                            analysisSegmentId = frameSample.analysisSegmentId,
                            startReconnectCount = frameSample.reconnectCount,
                            startContinuityBreakCount = frameSample.continuityBreakCount,
                            startPacketLossCount = frameSample.packetLossCount,
                            samples = enteringSamples.toMutableList()
                        )
                        enteringSamples.clear()
                        belowMaintainStartedAt = null
                        log("stable episode entered: start=$start, score=${"%.1f".format(overallScore)}")
                    }
                } else {
                    resetEntryState()
                }
            }

            StabilityEpisodePhase.STABLE -> {
                if (overallScore != null && overallScore >= config.maintainScore && frameSample != null) {
                    belowMaintainStartedAt = null
                    activeEpisode?.samples?.add(frameSample)
                } else {
                    val belowStart = belowMaintainStartedAt ?: nowMillis.also {
                        belowMaintainStartedAt = it
                    }
                    if (nowMillis - belowStart >= config.exitDurationMillis) {
                        finalizeActiveEpisode(belowStart, "maintain score timeout")
                        resetEntryState()
                    }
                }
            }
        }
    }

    private fun finalizeActiveEpisode(endTimeMillis: Long, reason: String) {
        val episode = activeEpisode ?: return
        activeEpisode = null
        phase = StabilityEpisodePhase.IDLE
        belowMaintainStartedAt = null

        val candidate = buildCandidate(episode, endTimeMillis) ?: return
        sessionCandidates += candidate
        log(
            "stable episode finalized: reason=$reason, duration=${candidate.durationSec}s, " +
                    "score=${"%.1f".format(candidate.overallStabilityScore)}, domains=${candidate.usedDomainCount}"
        )
    }

    private fun buildCandidate(
        episode: EpisodeAccumulator,
        requestedEndTimeMillis: Long
    ): StableCandidateRecord? {
        if (episode.samples.isEmpty()) return null

        val finalEnd = minOf(
            requestedEndTimeMillis,
            episode.samples.last().timestampMillis
        )
        val durationSec = ((finalEnd - episode.startedAt).coerceAtLeast(0L) / 1000L).toInt()
        if (durationSec * 1000L < config.entryDurationMillis) return null

        val samples = episode.samples.filter { it.timestampMillis <= finalEnd }
        if (samples.isEmpty()) return null

        val session = sessionId ?: return null
        val last = samples.last()

        return StableCandidateRecord(
            sleepSessionId = session,
            episodeId = episode.episodeId,
            startedAt = episode.startedAt,
            endedAt = finalEnd,
            durationSec = durationSec,

            rrMedian = medianOrNull(samples.mapNotNull { it.rr }),
            rrvMedian = medianOrNull(samples.mapNotNull { it.rrv }),
            hrMedian = medianOrNull(samples.mapNotNull { it.hr }),
            hrvRmssdMedian = medianOrNull(samples.mapNotNull { it.hrvRmssd }),
            hrvLfMedian = medianOrNull(samples.mapNotNull { it.hrvLf }),
            hrvHfMedian = medianOrNull(samples.mapNotNull { it.hrvHf }),
            temperatureMedian = medianOrNull(samples.mapNotNull { it.temperature }),
            temperatureSlopeMedian = medianOrNull(samples.mapNotNull { it.temperatureSlope }),

            rrQuality = medianOrNull(samples.mapNotNull { it.rrQuality }),
            rrvQuality = medianOrNull(samples.mapNotNull { it.rrvQuality }),
            hrQuality = medianOrNull(samples.mapNotNull { it.hrQuality }),
            hrvQuality = medianOrNull(samples.mapNotNull { it.hrvQuality }),
            temperatureQuality = medianOrNull(samples.mapNotNull { it.temperatureQuality }),

            movementStabilityScore = medianOrNull(samples.mapNotNull { it.movementScore }),
            respiratoryStabilityScore = medianOrNull(samples.mapNotNull { it.respiratoryScore }),
            cardiacStabilityScore = medianOrNull(samples.mapNotNull { it.cardiacScore }),
            temperatureStabilityScore = medianOrNull(samples.mapNotNull { it.temperatureScore }),
            overallStabilityScore = medianOrNull(samples.map { it.overallScore }) ?: return null,

            usedDomainCount = medianOrNull(samples.map { it.usedDomainCount.toDouble() })
                ?.toInt()
                ?.coerceIn(0, 4)
                ?: 0,
            analysisSegmentId = episode.analysisSegmentId,
            reconnectCount = (last.reconnectCount - episode.startReconnectCount).coerceAtLeast(0),
            continuityBreakCount =
                (last.continuityBreakCount - episode.startContinuityBreakCount).coerceAtLeast(0),
            packetLossCount =
                (last.packetLossCount - episode.startPacketLossCount).coerceAtLeast(0),
            algorithmVersion = config.algorithmVersion,
            createdAt = System.currentTimeMillis(),

            // 아래 값들은 episode CSV 감사(audit) 로그용이며 후보 DB 스키마에는 저장하지 않는다.
            frameSampleCount = samples.size,
            rrSampleCount = samples.count { it.rr != null },
            rrvSampleCount = samples.count { it.rrv != null },
            hrSampleCount = samples.count { it.hr != null },
            hrvSampleCount = samples.count { it.hrvRmssd != null },
            hrvFrequencyUsableFrameCount = samples.count { it.hrvFrequencyUsable },
            hrvFrequencyRejectedFrameCount = samples.count { !it.hrvFrequencyUsable },
            hrvFrequencyRejectionSummary = summarizeFrequencyRejections(samples),
            hrvLfHfMedian = medianOrNull(samples.mapNotNull { it.hrvLfHf }),
            hrvLfHfMean = samples.mapNotNull { it.hrvLfHf }.averageOrNull(),
            hrvRmssdQualityMedian = medianOrNull(samples.mapNotNull { it.hrvRmssdQuality }),
            hrvFrequencyQualityMedian = medianOrNull(samples.mapNotNull { it.hrvFrequencyQuality }),
            hrvRmssdStabilityMedian = medianOrNull(samples.mapNotNull { it.hrvRmssdStabilityScore }),
            hrvLfHfStabilityMedian = medianOrNull(samples.mapNotNull { it.hrvLfHfStabilityScore }),
            temperatureSampleCount = samples.count { it.temperature != null },
            rrMean = samples.mapNotNull { it.rr }.averageOrNull(),
            rrvMean = samples.mapNotNull { it.rrv }.averageOrNull(),
            hrMean = samples.mapNotNull { it.hr }.averageOrNull(),
            hrvRmssdMean = samples.mapNotNull { it.hrvRmssd }.averageOrNull(),
            temperatureMean = samples.mapNotNull { it.temperature }.averageOrNull()
        )
    }

    private fun summarizeFrequencyRejections(
        samples: List<StabilityFrameSample>
    ): String? {
        val counts = mutableMapOf<String, Int>()
        samples.asSequence()
            .filter { !it.hrvFrequencyUsable }
            .mapNotNull { it.hrvFrequencyRejectionReasons }
            .flatMap { it.substringBefore(';').split('|').asSequence() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { reason -> counts[reason] = (counts[reason] ?: 0) + 1 }
        return counts.entries
            .sortedByDescending { it.value }
            .joinToString("|") { "${it.key}:${it.value}" }
            .takeIf { it.isNotEmpty() }
    }

    /**
     * 검출된 모든 episode에 대해 후보 테이블 선택 여부와 사유를 반환한다.
     *
     * 5개를 넘는 경우:
     * 1. quality / 영역 수 / 안정점수 / 지속시간 순으로 우선순위를 정한다.
     * 2. 먼저 30분 이상 떨어진 episode를 선택한다.
     * 3. 자리가 남으면 우선순위 순으로 채운다.
     * 4. 선택되지 않은 episode도 CSV 로그에는 반드시 남긴다.
     */
    private fun selectSessionCandidateDecisions(
        candidates: List<StableCandidateRecord>
    ): List<SessionCandidateDecision> {
        if (candidates.isEmpty()) return emptyList()

        val detectedIndexById = candidates
            .sortedBy { it.startedAt }
            .mapIndexed { index, candidate -> candidate.episodeId to (index + 1) }
            .toMap()

        val sorted = candidates.sortedWith(
            compareByDescending<StableCandidateRecord> { candidateAverageQuality(it) }
                .thenByDescending { it.usedDomainCount }
                .thenByDescending { it.overallStabilityScore }
                .thenByDescending { it.durationSec }
                .thenBy { it.startedAt }
        )

        val selected = mutableListOf<StableCandidateRecord>()
        val reasonByEpisodeId = mutableMapOf<String, String>()

        if (candidates.size <= config.maxCandidatesPerSession) {
            sorted.forEach {
                selected += it
                reasonByEpisodeId[it.episodeId] = "SELECTED_WITHIN_SESSION_LIMIT"
            }
        } else {
            sorted.forEach { candidate ->
                if (selected.size >= config.maxCandidatesPerSession) return@forEach
                val sufficientlySeparated = selected.all {
                    abs(it.startedAt - candidate.startedAt) >= config.episodeSeparationMillis
                }
                if (sufficientlySeparated) {
                    selected += candidate
                    reasonByEpisodeId[candidate.episodeId] =
                        "SELECTED_TIME_SEPARATED_PRIORITY"
                }
            }

            if (selected.size < config.maxCandidatesPerSession) {
                sorted.forEach { candidate ->
                    if (selected.size >= config.maxCandidatesPerSession) return@forEach
                    if (candidate !in selected) {
                        selected += candidate
                        reasonByEpisodeId[candidate.episodeId] =
                            "SELECTED_PRIORITY_FILL"
                    }
                }
            }
        }

        val rankById = selected
            .mapIndexed { index, candidate -> candidate.episodeId to (index + 1) }
            .toMap()

        return candidates.sortedBy { it.startedAt }.map { candidate ->
            val selectedForTable = candidate.episodeId in rankById
            SessionCandidateDecision(
                candidate = candidate,
                detectedIndex = detectedIndexById.getValue(candidate.episodeId),
                selectedForCandidateTable = selectedForTable,
                selectionRank = rankById[candidate.episodeId],
                selectionReason = if (selectedForTable) {
                    reasonByEpisodeId[candidate.episodeId]
                        ?: "SELECTED"
                } else {
                    "EXCLUDED_SESSION_MAX_${config.maxCandidatesPerSession}_LOWER_PRIORITY"
                }
            )
        }
    }

    private fun candidateAverageQuality(candidate: StableCandidateRecord): Double {
        return listOfNotNull(
            candidate.rrQuality,
            candidate.rrvQuality,
            candidate.hrQuality,
            candidate.hrvQuality,
            candidate.temperatureQuality
        ).averageOrNull() ?: 0.0
    }

    private fun recalculatePersonalBaselines(nowMillis: Long): Int {
        var updateCount = 0

        BaselineMetricType.values().forEach { metricType ->
            val values = stableCandidateTable.loadMetricValues(
                metricType = metricType,
                algorithmVersion = config.algorithmVersion,
                nowMillis = nowMillis,
                maxAgeDays = config.maxCandidateAgeDays,
                limit = config.maxStoredCandidates
            )
            val count = values.size
            val existing = personalBaselineTable.load(metricType, config.algorithmVersion)
            val lifecycleState = lifecycleStateFor(count)

            if (count < config.minimumCandidateCountForBaseline) {
                val record = PersonalBaselineRecord(
                    metricType = metricType,
                    center = null,
                    spread = null,
                    candidateCount = count,
                    lifecycleState = lifecycleState,
                    confidence = 0.0,
                    lastCandidateId = values.maxOfOrNull { it.candidateId },
                    updatedAt = nowMillis,
                    distributionVersion = existing?.distributionVersion ?: 0,
                    algorithmVersion = config.algorithmVersion
                )
                personalBaselineTable.upsert(record)
                updateCount += 1
                return@forEach
            }

            val crossedMatureBoundary =
                lifecycleState == BaselineLifecycleState.MATURE &&
                        existing?.lifecycleState != BaselineLifecycleState.MATURE
            val candidateCountMovedBackwards =
                existing != null && count < existing.candidateCount
            val shouldRecalculate =
                existing?.center == null ||
                        existing.spread == null ||
                        count - existing.candidateCount >= config.baselineUpdateStep ||
                        crossedMatureBoundary ||
                        candidateCountMovedBackwards

            if (!shouldRecalculate) return@forEach

            val denseValues = densestRegion(
                values = values.map { it.value },
                fraction = config.denseRegionFraction
            )
            if (denseValues.isEmpty()) return@forEach

            val center = medianOrNull(denseValues) ?: return@forEach
            val spread = medianAbsoluteDeviation(denseValues, center)
            val denseRange = (denseValues.maxOrNull() ?: center) -
                    (denseValues.minOrNull() ?: center)
            val concentration = 1.0 / (
                    1.0 + denseRange / (minimumBaselineScale(metricType) * 4.0)
                    )
            val confidence = (
                    (count.toDouble() / config.matureCandidateCount).coerceIn(0.0, 1.0) *
                            concentration.coerceIn(0.0, 1.0)
                    ).coerceIn(0.0, 1.0)

            personalBaselineTable.upsert(
                PersonalBaselineRecord(
                    metricType = metricType,
                    center = center,
                    spread = spread,
                    candidateCount = count,
                    lifecycleState = lifecycleState,
                    confidence = confidence,
                    lastCandidateId = values.maxOfOrNull { it.candidateId },
                    updatedAt = nowMillis,
                    distributionVersion = (existing?.distributionVersion ?: 0) + 1,
                    algorithmVersion = config.algorithmVersion
                )
            )
            updateCount += 1
        }

        return updateCount
    }

    private fun lifecycleStateFor(candidateCount: Int): BaselineLifecycleState {
        return when {
            candidateCount < 5 -> BaselineLifecycleState.EMPTY
            candidateCount < config.minimumCandidateCountForBaseline -> BaselineLifecycleState.COLLECTING
            candidateCount < config.matureCandidateCount -> BaselineLifecycleState.PROVISIONAL
            else -> BaselineLifecycleState.MATURE
        }
    }

    private fun densestRegion(values: List<Double>, fraction: Double): List<Double> {
        val sorted = values.filter { it.isFinite() }.sorted()
        if (sorted.isEmpty()) return emptyList()

        val windowSize = ceil(sorted.size * fraction.coerceIn(0.1, 1.0))
            .toInt()
            .coerceAtLeast(minOf(8, sorted.size))
            .coerceAtMost(sorted.size)

        var bestStart = 0
        var bestWidth = Double.POSITIVE_INFINITY
        for (start in 0..sorted.size - windowSize) {
            val end = start + windowSize - 1
            val width = sorted[end] - sorted[start]
            if (width < bestWidth) {
                bestWidth = width
                bestStart = start
            }
        }
        return sorted.subList(bestStart, bestStart + windowSize)
    }

    private fun calculateBaselineProximity(
        metricType: BaselineMetricType,
        value: Double,
        baseline: PersonalBaselineRecord
    ): Double? {
        val center = baseline.center ?: return null
        val mad = baseline.spread ?: return null
        val robustScale = max(1.4826 * mad, minimumBaselineScale(metricType))
        val z = abs(value - center) / robustScale
        return exp(-0.5 * z.pow(2.0)).coerceIn(0.0, 1.0)
    }

    private fun calculateWithinWindowConsistency(
        history: ArrayDeque<TimedMetricValue>,
        nowMillis: Long,
        windowMillis: Long,
        variationScale: Double
    ): Double? {
        val values = history.filter { nowMillis - it.timestampMillis <= windowMillis }
        if (values.size < config.minimumHistorySampleCount) return null
        val duration = values.last().timestampMillis - values.first().timestampMillis
        if (duration < minOf(config.minimumHistoryDurationMillis, windowMillis)) return null

        val median = medianOrNull(values.map { it.value }) ?: return null
        val mad = medianAbsoluteDeviation(values.map { it.value }, median)
        val ratio = mad / max(variationScale, 1e-9)
        return (1.0 / (1.0 + ratio * ratio)).coerceIn(0.0, 1.0)
    }

    private fun calculateSlopeStability(
        history: ArrayDeque<TimedMetricValue>,
        nowMillis: Long,
        windowMillis: Long,
        slopeScalePerMinute: Double
    ): Double? {
        val values = history.filter { nowMillis - it.timestampMillis <= windowMillis }
        if (values.size < config.minimumHistorySampleCount) return null
        val duration = values.last().timestampMillis - values.first().timestampMillis
        if (duration < minOf(config.minimumHistoryDurationMillis, windowMillis)) return null

        val slopePerMinute = linearSlopePerMinute(values) ?: return null
        val ratio = abs(slopePerMinute) / max(slopeScalePerMinute, 1e-9)
        return (1.0 / (1.0 + ratio * ratio)).coerceIn(0.0, 1.0)
    }

    private fun linearSlopePerMinute(values: List<TimedMetricValue>): Double? {
        if (values.size < 2) return null
        val origin = values.first().timestampMillis
        val x = values.map { (it.timestampMillis - origin) / 60_000.0 }
        val y = values.map { it.value }
        val meanX = x.average()
        val meanY = y.average()

        var numerator = 0.0
        var denominator = 0.0
        for (i in x.indices) {
            val dx = x[i] - meanX
            numerator += dx * (y[i] - meanY)
            denominator += dx * dx
        }
        if (denominator <= 0.0) return null
        return numerator / denominator
    }

    private fun calculateRrSignalConsistency(state: ArousalState): Double {
        val ppg = state.rrFromPpg
        val imu = state.rrFromImu
        return when {
            ppg != null && imu != null -> {
                exp(-abs(ppg - imu) / 1.0) * state.rrFusionConfidence
            }
            state.rrFinal != null -> state.rrFusionConfidence * 0.80
            else -> 0.0
        }.coerceIn(0.0, 1.0)
    }

    private fun calculateRrvSignalConsistency(state: ArousalState): Double {
        val intervalScore = (state.rrvIntervalCount / 20.0).coerceIn(0.0, 1.0)
        val sourceAgreement = if (
            state.rrvFromPpgRmssdSec != null &&
            state.rrvFromImuRmssdSec != null
        ) {
            exp(-abs(state.rrvFromPpgRmssdSec - state.rrvFromImuRmssdSec) / 0.20)
        } else {
            0.80
        }
        return (
                state.rrvQuality * 0.45 +
                        intervalScore * 0.30 +
                        state.rrFusionConfidence * 0.15 +
                        sourceAgreement * 0.10
                ).coerceIn(0.0, 1.0)
    }

    private fun calculateHrSignalConsistency(diagnostics: HeartRateDiagnostics): Double {
        val accepted = diagnostics.acceptedIntervalRatio ?: 0.0
        val physiological = diagnostics.physiologicalIntervalRatio ?: 0.0
        val rawQuality = diagnostics.rawIntervalQualityScore ?: diagnostics.qualityScore ?: 0.0
        val countScore = (diagnostics.validIbiCount / 8.0).coerceIn(0.0, 1.0)
        return (
                accepted * 0.30 +
                        physiological * 0.25 +
                        rawQuality * 0.30 +
                        countScore * 0.15
                ).coerceIn(0.0, 1.0)
    }

    private fun calculateHrvRmssdSignalConsistency(state: ArousalState): Double {
        val countScore = (state.hrvRmssdIbiCount / 20.0).coerceIn(0.0, 1.0)
        return (state.hrvRmssdQuality * 0.75 + countScore * 0.25)
            .coerceIn(0.0, 1.0)
    }

    private fun calculateHrvFrequencySignalConsistency(state: ArousalState): Double {
        if (!state.hrvFrequencyUsable) return 0.0
        val countScore = (state.hrvFrequencyIbiCount / 80.0).coerceIn(0.0, 1.0)
        val durationScore = (state.hrvFrequencyObservedSeconds / 120.0).coerceIn(0.0, 1.0)
        return (
                state.hrvFrequencyQuality * 0.55 +
                        countScore * 0.20 +
                        durationScore * 0.15 +
                        (state.hrvFrequencyPpgSignalQuality ?: 0.0) * 0.10
                ).coerceIn(0.0, 1.0)
    }

    private fun calculateTemperatureSignalConsistency(state: ArousalState): Double {
        val current = state.skinTemperatureCelsius ?: return 0.0
        val history = histories.getValue(BaselineMetricType.TEMPERATURE)
        val previous = history.lastOrNull()?.value
        val noAbruptJump = if (previous == null) 1.0 else {
            (1.0 - abs(current - previous) / 1.0).coerceIn(0.0, 1.0)
        }
        val sampleScore = (state.skinTemperatureSampleCount / 20.0).coerceIn(0.0, 1.0)
        return (
                state.skinTemperatureQuality * 0.60 +
                        noAbruptJump * 0.25 +
                        sampleScore * 0.15
                ).coerceIn(0.0, 1.0)
    }

    private fun trimHistory(history: ArrayDeque<TimedMetricValue>, nowMillis: Long) {
        while (history.isNotEmpty() && nowMillis - history.first().timestampMillis > config.maxHistoryMillis) {
            history.removeFirst()
        }
    }

    private fun consistencyWindow(metricType: BaselineMetricType): Long = when (metricType) {
        BaselineMetricType.RR -> config.rrConsistencyWindowMillis
        BaselineMetricType.RRV -> config.rrvConsistencyWindowMillis
        BaselineMetricType.HR -> config.hrConsistencyWindowMillis
        BaselineMetricType.HRV_RMSSD -> config.hrvConsistencyWindowMillis
        BaselineMetricType.HRV_LF_HF -> config.hrvLfHfConsistencyWindowMillis
        BaselineMetricType.TEMPERATURE -> config.temperatureConsistencyWindowMillis
    }

    private fun slopeWindow(metricType: BaselineMetricType): Long = when (metricType) {
        BaselineMetricType.RR -> config.rrSlopeWindowMillis
        BaselineMetricType.RRV -> config.rrvSlopeWindowMillis
        BaselineMetricType.HR -> config.hrSlopeWindowMillis
        BaselineMetricType.HRV_RMSSD -> config.hrvSlopeWindowMillis
        BaselineMetricType.HRV_LF_HF -> config.hrvLfHfSlopeWindowMillis
        BaselineMetricType.TEMPERATURE -> config.temperatureSlopeWindowMillis
    }

    private fun variationScale(metricType: BaselineMetricType): Double = when (metricType) {
        BaselineMetricType.RR -> config.rrVariationScaleBpm
        BaselineMetricType.RRV -> config.rrvVariationScaleSec
        BaselineMetricType.HR -> config.hrVariationScaleBpm
        BaselineMetricType.HRV_RMSSD -> config.hrvVariationScaleSec
        BaselineMetricType.HRV_LF_HF -> config.hrvLfHfVariationScale
        BaselineMetricType.TEMPERATURE -> config.temperatureVariationScaleCelsius
    }

    private fun slopeScale(metricType: BaselineMetricType): Double = when (metricType) {
        BaselineMetricType.RR -> config.rrSlopeScalePerMinute
        BaselineMetricType.RRV -> config.rrvSlopeScaleSecPerMinute
        BaselineMetricType.HR -> config.hrSlopeScalePerMinute
        BaselineMetricType.HRV_RMSSD -> config.hrvSlopeScaleSecPerMinute
        BaselineMetricType.HRV_LF_HF -> config.hrvLfHfSlopeScalePerMinute
        BaselineMetricType.TEMPERATURE -> config.temperatureSlopeScalePerMinute
    }

    private fun minimumBaselineScale(metricType: BaselineMetricType): Double = when (metricType) {
        BaselineMetricType.RR -> config.rrMinimumBaselineScale
        BaselineMetricType.RRV -> config.rrvMinimumBaselineScale
        BaselineMetricType.HR -> config.hrMinimumBaselineScale
        BaselineMetricType.HRV_RMSSD -> config.hrvMinimumBaselineScale
        BaselineMetricType.HRV_LF_HF -> config.hrvLfHfMinimumBaselineScale
        BaselineMetricType.TEMPERATURE -> config.temperatureMinimumBaselineScale
    }

    private fun baselineStateMap(): Map<BaselineMetricType, BaselineLifecycleState> {
        return BaselineMetricType.values().associateWith {
            frozenBaselines[it]?.lifecycleState ?: BaselineLifecycleState.EMPTY
        }
    }

    private fun clearTransientState(clearSessionCandidates: Boolean) {
        histories.values.forEach { it.clear() }
        hrvLfHfHistory.clear()
        resetEntryState()
        activeEpisode = null
        lastAnalysisSegmentId = null
        lastFrameTimestampMillis = null
        currentReconnectCount = 0
        hasEverConnected = false
        lastBleConnected = false
        lastState = StabilityState(
            activeBaselines = frozenBaselines,
            baselineStates = baselineStateMap()
        )
        if (clearSessionCandidates) sessionCandidates.clear()
    }

    private fun resetEntryState() {
        phase = StabilityEpisodePhase.IDLE
        entryStartedAt = null
        belowMaintainStartedAt = null
        enteringSamples.clear()
    }

    private fun medianAbsoluteDeviation(values: List<Double>, center: Double): Double {
        return medianOrNull(values.map { abs(it - center) }) ?: 0.0
    }

    private fun medianOrNull(values: List<Double>): Double? {
        val sorted = values.filter { it.isFinite() }.sorted()
        if (sorted.isEmpty()) return null
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        } else {
            sorted[middle]
        }
    }

    private fun List<Double>.averageOrNull(): Double? {
        return if (isEmpty()) null else average()
    }

    private fun log(message: String, level: String = "I") {
        dataLogger?.logDebug("PotchStability", message, level)
    }
}
