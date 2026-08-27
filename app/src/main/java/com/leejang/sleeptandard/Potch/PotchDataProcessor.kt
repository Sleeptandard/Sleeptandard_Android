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

data class PacketErrorLog(
    val type: String,
    val message: String,
    val fragCounter: Int? = null,
    val timestampMs: Long = System.currentTimeMillis()
)

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

enum class HeartRateSource {
    NONE,
    GREEN
}

data class HeartRateDiagnostics(
    val processingState: HeartRateProcessingState = HeartRateProcessingState.COLLECTING,
    val underlyingFailureReason: HeartRateProcessingState? = null,
    val message: String = "Green PPG 심박 신호 수집 중",
    val analysisSegmentId: Long = 0L,
    val windowSampleCount: Int = 0,
    val windowSeconds: Double = 0.0,

    val greenDcMean: Double? = null,
    val greenMin: Double? = null,
    val greenMax: Double? = null,
    val acRobustAmplitude: Double? = null,

    val amplitudeCoefficientOfVariation: Double? = null,
    val spectralConcentration: Double? = null,
    val spectralEntropy: Double? = null,
    val abruptChangeRatio: Double? = null,

    val selectedPeakThreshold: Double? = null,
    val selectedThresholdPercent: Double? = null,
    val selectedPolarity: HeartRatePeakPolarity = HeartRatePeakPolarity.NONE,

    val detectedPeakSamplePositions: List<Double> = emptyList(),
    val acceptedIbiEndSamplePositions: List<Double> = emptyList(),
    val rejectedIbiEndSamplePositions: List<Double> = emptyList(),
    val referencePeakSamplePosition: Double? = null,

    val detectedPeakCount: Int = 0,
    val rawIbiCount: Int = 0,
    val validIbiCount: Int = 0,
    val acceptedIntervalRatio: Double? = null,

    val rawSdsdMs: Double? = null,
    val rawIbiCv: Double? = null,
    val physiologicalIntervalRatio: Double? = null,
    val rawIntervalQualityScore: Double? = null,
    val sdsdMs: Double? = null,
    val qualityScore: Double? = null,

    val calculatedBpm: Int? = null,
    val displayedBpm: Int? = null,
    val heartRateFresh: Boolean = false,
    val heartRateAgeMillis: Long? = null,
    val source: HeartRateSource = HeartRateSource.NONE,
    val sourceLog: String? = null,

    val meanPeakInterpolationOffsetMs: Double? = null,
    val maxPeakInterpolationOffsetMs: Double? = null,

    val imuMaxDeltaG: Double? = null,
    val imuP95DeltaG: Double? = null,
    val imuMotionExceedanceRatio: Double? = null,

    val retainedBufferSampleCount: Int = 0,
    val cleanSegmentSampleCount: Int = 0,
    val invalidMaskedSampleCount: Int = 0,
    val motionMaskedSampleCount: Int = 0,
    val interpolatedSampleCount: Int = 0,
    val excludedPeakSampleCount: Int = 0,
    val longestInterpolatedRun: Int = 0,
    val motionTolerated: Boolean = false,

    val maxRawSampleDelta: Double? = null,
    val crcErrorCount: Int = 0,
    val sequenceLossCount: Int = 0,
    val estimatedLostPacketCount: Int = 0
)

data class IbiInterval(
    val intervalSec: Double,
    val endSampleIndex: Long,
    val segmentId: Long = 0L,
    val endSamplePosition: Double = endSampleIndex.toDouble()
)

/**
 * HR에서 채택한 두 박동 peak 사이 raw Green PPG 최저점.
 *
 * 같은 심장 박동 구간에서 하나의 점만 만들고, 이 점들을 시간 순서로 이으면
 * PPG lower envelope가 된다. RR/RRV는 이 envelope의 느린 호흡 변조에서 계산한다.
 */
data class PpgLowerEnvelopeSample(
    val samplePosition: Long,
    val rawValue: Double,
    val segmentId: Long
)

data class HeartRateEstimate(
    val bpm: Int,
    val ibiIntervals: List<IbiInterval>,
    val peakCount: Int,
    val intervalCount: Int,
    val averageIntervalSec: Double,
    val qualityScore: Double,
    val source: HeartRateSource = HeartRateSource.GREEN,
    val sourceLog: String? = "Green PPG 단일 채널",

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

    val detectedPeakSamplePositions: List<Double> = emptyList(),
    val acceptedIbiEndSamplePositions: List<Double> =
        ibiIntervals.map { it.endSamplePosition },
    val rejectedIbiEndSamplePositions: List<Double> = emptyList(),
    val referencePeakSamplePosition: Double? =
        detectedPeakSamplePositions.firstOrNull(),

    val meanPeakInterpolationOffsetMs: Double = 0.0,
    val maxPeakInterpolationOffsetMs: Double = 0.0,

    // 최종 HR IBI에 포함된 upper peak 쌍마다 하나씩 뽑은 raw PPG lower point.
    val lowerEnvelopeSamples: List<PpgLowerEnvelopeSample> = emptyList(),

    // rolling snapshot 교체 시작점. 이 위치 이후의 과거 lower point를 새 snapshot으로 대체한다.
    val lowerEnvelopeReplacementStartSamplePosition: Long? = null
)

/**
 * 최종 HR interval을 PPG lower-envelope 표본으로 바꾸는 순수 변환기.
 *
 * rolling HR window가 같은 박동 peak를 조금 다른 위치에서 반복 검출하므로 먼저
 * 0.20초 안의 upper peak를 합치고 raw 값이 더 큰 위치를 유지한다. 그 뒤 생리적인
 * HR 간격을 이루는 인접 upper peak 사이에서 raw 최저점을 하나만 선택한다.
 */
internal object PpgLowerEnvelopeExtractor {
    fun extract(
        rawSamples: List<Double>,
        windowStartSamplePosition: Long,
        acceptedIntervals: List<IbiInterval>,
        segmentId: Long,
        sampleRateHz: Double
    ): List<PpgLowerEnvelopeSample> {
        if (rawSamples.size < 3 || acceptedIntervals.isEmpty() || sampleRateHz <= 0.0) {
            return emptyList()
        }

        data class UpperPeak(
            val samplePosition: Long,
            val rawValue: Double
        )

        fun rawValueAt(samplePosition: Long): Double? {
            val index = samplePosition - windowStartSamplePosition
            if (index !in 0L until rawSamples.size.toLong()) return null
            return rawSamples[index.toInt()].takeIf { it.isFinite() }
        }

        val mergeSamples = (sampleRateHz * 0.20).roundToLong().coerceAtLeast(1L)
        val refractorySamples = (sampleRateHz * 0.25).roundToLong().coerceAtLeast(1L)
        val minimumHeartIntervalSamples =
            (sampleRateHz * 60.0 / 180.0).roundToLong().coerceAtLeast(1L)
        val maximumHeartIntervalSamples =
            (sampleRateHz * 60.0 / 40.0).roundToLong().coerceAtLeast(minimumHeartIntervalSamples)

        val upperCandidates = acceptedIntervals
            .asSequence()
            .filter {
                it.segmentId == segmentId &&
                        it.intervalSec.isFinite() &&
                        it.intervalSec > 0.0 &&
                        it.endSamplePosition.isFinite()
            }
            .flatMap { interval ->
                val end = interval.endSamplePosition.roundToLong()
                val start = (
                        interval.endSamplePosition - interval.intervalSec * sampleRateHz
                        ).roundToLong()
                sequenceOf(start, end)
            }
            .distinct()
            .sorted()
            .mapNotNull { position ->
                rawValueAt(position)?.let { UpperPeak(position, it) }
            }
            .toList()

        if (upperCandidates.size < 2) return emptyList()

        val mergedUpperPeaks = mutableListOf<UpperPeak>()
        for (candidate in upperCandidates) {
            val previous = mergedUpperPeaks.lastOrNull()
            if (
                previous != null &&
                candidate.samplePosition - previous.samplePosition <= mergeSamples
            ) {
                if (candidate.rawValue > previous.rawValue) {
                    mergedUpperPeaks[mergedUpperPeaks.lastIndex] = candidate
                }
            } else {
                mergedUpperPeaks += candidate
            }
        }

        val lowerSamples = mutableListOf<PpgLowerEnvelopeSample>()
        for (index in 1 until mergedUpperPeaks.size) {
            val left = mergedUpperPeaks[index - 1].samplePosition
            val right = mergedUpperPeaks[index].samplePosition
            val intervalSamples = right - left
            if (intervalSamples !in minimumHeartIntervalSamples..maximumHeartIntervalSamples) {
                continue
            }

            // HR upper peak 자체와 경계 보간 영향을 피하도록 양 끝을 제외한다.
            val searchStartPosition = left + 2L
            val searchEndExclusive = right - 1L
            if (searchEndExclusive - searchStartPosition < 3L) continue

            var bestPosition: Long? = null
            var bestValue = Double.POSITIVE_INFINITY
            var position = searchStartPosition
            while (position < searchEndExclusive) {
                val rawValue = rawValueAt(position)
                if (rawValue != null && rawValue < bestValue) {
                    bestValue = rawValue
                    bestPosition = position
                }
                position += 1L
            }

            val selectedPosition = bestPosition ?: continue
            if (
                lowerSamples.isNotEmpty() &&
                selectedPosition - lowerSamples.last().samplePosition < refractorySamples
            ) {
                continue
            }

            lowerSamples += PpgLowerEnvelopeSample(
                samplePosition = selectedPosition,
                rawValue = bestValue,
                segmentId = segmentId
            )
        }

        return lowerSamples
    }
}

data class HeartRateGraphData(
    val source: HeartRateSource = HeartRateSource.NONE,
    val processingState: HeartRateProcessingState = HeartRateProcessingState.COLLECTING,
    val selectedPolarity: HeartRatePeakPolarity = HeartRatePeakPolarity.NONE,
    val label: String = "GREEN",
    val samples: List<Double> = emptyList(),

    val peakSampleIndices: List<Int> = emptyList(),
    val detectedPeakSampleIndices: List<Int> = emptyList(),
    val acceptedPeakSampleIndices: List<Int> = emptyList(),
    val rejectedPeakSampleIndices: List<Int> = emptyList(),
    val referencePeakSampleIndex: Int? = null,

    val retainedBufferSampleCount: Int = 0,
    val cleanSegmentSampleCount: Int = 0,
    val interpolatedSampleCount: Int = 0,
    val excludedPeakSampleCount: Int = 0,

    val calculatedBpm: Int? = null,
    val qualityScore: Double? = null,
    val description: String = "Green PPG 심박 분석 데이터 수집 중"
)

data class DataProcessorState(
    val lastParsedData: SensorData? = null,
    val heartRateBpm: Int? = null,
    val heartRateQuality: Double? = null,
    val heartRateFresh: Boolean = false,
    val heartRateAgeMillis: Long? = null,
    val heartRateDiagnostics: HeartRateDiagnostics = HeartRateDiagnostics(),
    val heartRateCalculationStatus: MetricCalculationStatus = MetricCalculationStatus(),
    val heartRateGraphData: HeartRateGraphData = HeartRateGraphData(),
    val crcErrorCount: Int = 0,
    val missingSequenceErrors: Int = 0,
    val lastLog: String = "No data yet",
    val totalMiniPackets: Int = 0,
    val validMiniPackets: Int = 0,
    val damagedPacketCount: Int = 0,
    val estimatedLostPacketCount: Int = 0,
    val parsedSuperFrameCount: Int = 0,
    val recentPacketErrors: List<PacketErrorLog> = emptyList(),
    val lastFragCounter: Int? = null,
    val expectedFragCounter: Int? = null,
    val analysisSegmentId: Long = 0L,
    val continuityBreakCount: Int = 0,
    val lastContinuityBreakReason: String? = null,
    val lastGreenMax: Double = 0.0,
    val int2EventReceived: Boolean = false,
    val arousalState: ArousalState = ArousalState(),
    val stabilityState: StabilityState = StabilityState()
)
class PotchDataProcessor(
    private val dataLogger: PotchDataLogger? = null,
    private val arousalCalculator: PotchArousalCalculator = PotchArousalCalculator(),
    private val stabilityCalculator: PotchStabilityCalculator? = null
) {
    private data class ParsedPacket(
        val sequence: Int,
        val timestamp: Long,
        val batteryRaw: Int,
        val ntcRaw: Int,
        val imuData: ByteArray,
        val ppgData: ByteArray
    )

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

    private data class ImuMotionSummary(
        val maxDeltaG: Double,
        val p95DeltaG: Double,
        val exceedanceRatio: Double,
        val deltaCount: Int
    ) {
        val isStrongMotion: Boolean
            get() =
                maxDeltaG >= HEART_RATE_MOTION_SINGLE_SPIKE_HARD_G ||
                        p95DeltaG >= HEART_RATE_MOTION_P95_THRESHOLD_G ||
                        exceedanceRatio >= HEART_RATE_MOTION_MIN_EXCEEDANCE_RATIO
    }

    private data class HeartRateSignalWindow(
        val signal: List<Double>,
        val startSamplePosition: Long,
        val retainedBufferSampleCount: Int,
        val cleanSegmentSampleCount: Int,
        val invalidMaskedSampleCount: Int,
        val motionMaskedSampleCount: Int
    )

    private data class PpgPreprocessResult(
        val bandPassed: DoubleArray,
        val excludedPeakMask: BooleanArray,
        val interpolatedSampleCount: Int,
        val excludedPeakSampleCount: Int,
        val longestInterpolatedRun: Int,
        val longArtifactSampleCount: Int
    )

    private data class PpgArtifactRepairStats(
        val excludedPeakMask: BooleanArray,
        val interpolatedSampleCount: Int,
        val excludedPeakSampleCount: Int,
        val longestRun: Int,
        val longArtifactSampleCount: Int
    )

    private data class HeartRateAnalysisResult(
        val estimate: HeartRateEstimate?,
        val diagnostics: HeartRateDiagnostics
    )

    private val _state = MutableStateFlow(DataProcessorState())
    val state: StateFlow<DataProcessorState> = _state

    private val burstPackets = ArrayList<ParsedPacket>(PACKETS_PER_BURST)
    private val greenPpgBuffer = ArrayDeque<Int>()
    private val heartRateSamplePositionBuffer = ArrayDeque<Long>()
    private val heartRateSampleSegmentBuffer = ArrayDeque<Long>()
    private val heartRateSampleUsableBuffer = ArrayDeque<Boolean>()
    private val heartRateSampleMotionMaskedBuffer = ArrayDeque<Boolean>()

    private var expectedSequence: Int? = null
    private var analysisSegmentId = 0L
    private var totalHeartRateSamples = 0L
    private var lastValidHeartRate: HeartRateEstimate? = null
    private var lastValidHeartRateAt: Long? = null

    private var activePolarity = HeartRatePeakPolarity.POSITIVE
    private var positiveFailureStreak = 0
    private var positiveRecoveryStreak = 0
    private var activePolarityFailureStreak = 0
    private var pendingPolarity: HeartRatePeakPolarity? = null
    private var pendingPolaritySuccessStreak = 0
    private var pendingPolarityLastBpm: Int? = null

    fun updateMicroMovementBandPass(lowCutHz: Double, highCutHz: Double) {
        arousalCalculator.updateMicroMovementBandPass(lowCutHz, highCutHz)
    }

    /** Service에서 세션 기준선을 고정한 직후 UI에 최신 안정 상태를 반영한다. */
    @Synchronized
    fun refreshStabilityState() {
        _state.update {
            it.copy(
                stabilityState = stabilityCalculator?.currentState() ?: StabilityState()
            )
        }
    }

    @Synchronized
    fun processIncomingData(data: ByteArray) {
        val receivedAtMillis = System.currentTimeMillis()
        _state.update { it.copy(totalMiniPackets = it.totalMiniPackets + 1) }

        if (data.size != PACKET_SIZE) {
            val message = "Length Drop: expected $PACKET_SIZE, got ${data.size}"
            registerPacketError("LENGTH", message)
            breakContinuity(message)
            return
        }

        // RawDataAnalyzer/parser.py 호환 형식:
        // 각 record를 [phone time 8B little-endian][raw BLE packet 142B]로 기록한다.
        // 헤더/CRC 검증 전에 저장해야 손상 패킷도 사후 분석할 수 있다.
        dataLogger?.logRawPacket(
            phoneTimeMillis = receivedAtMillis,
            rawPacket = data
        )

        if (data[0] != HEADER_0 || data[1] != HEADER_1) {
            val message = "Header Drop: %02X %02X".format(
                data[0].toInt() and 0xFF,
                data[1].toInt() and 0xFF
            )
            registerPacketError("HEADER", message)
            breakContinuity(message)
            return
        }

        val sequence = readUInt16(data, 2)
        val receivedCrc = readUInt16(data, CRC_OFFSET)
        val calculatedCrc = crc16CcittFalse(data, 0, CRC_OFFSET)

        if (receivedCrc != calculatedCrc) {
            val message = "CRC Drop seq=$sequence: received=%04X calculated=%04X"
                .format(receivedCrc, calculatedCrc)
            registerPacketError("CRC", message, sequence, crc = true)
            breakContinuity(message)
            return
        }

        expectedSequence?.let { expected ->
            if (sequence != expected) {
                val distance = (sequence - expected) and 0xFFFF
                val lost = if (distance in 1..0x7FFF) distance else 1

                _state.update {
                    it.copy(
                        missingSequenceErrors = it.missingSequenceErrors + 1,
                        estimatedLostPacketCount = it.estimatedLostPacketCount + lost
                    )
                }

                val message =
                    "Seq Drop: expected=$expected actual=$sequence lost=$lost"
                registerPacketError("SEQUENCE", message, sequence)
                breakContinuity(message)
            }
        }
        expectedSequence = (sequence + 1) and 0xFFFF

        val packet = ParsedPacket(
            sequence = sequence,
            timestamp = readUInt32(data, 4),
            batteryRaw = readUInt16(data, 8),
            ntcRaw = readUInt16(data, 10),
            imuData = data.copyOfRange(IMU_OFFSET, PPG_OFFSET),
            ppgData = data.copyOfRange(PPG_OFFSET, CRC_OFFSET)
        )

        _state.update {
            it.copy(
                validMiniPackets = it.validMiniPackets + 1,
                lastFragCounter = sequence,
                expectedFragCounter = expectedSequence,
                lastLog = "Valid Potch packet seq=$sequence"
            )
        }

        appendToBurst(packet)
    }

    @Synchronized
    fun reset() {
        burstPackets.clear()
        greenPpgBuffer.clear()
        heartRateSamplePositionBuffer.clear()
        heartRateSampleSegmentBuffer.clear()
        heartRateSampleUsableBuffer.clear()
        heartRateSampleMotionMaskedBuffer.clear()

        expectedSequence = null
        totalHeartRateSamples = 0L
        lastValidHeartRate = null
        lastValidHeartRateAt = null
        analysisSegmentId += 1L
        resetPolaritySelection()
        arousalCalculator.reset(initialSegmentId = analysisSegmentId)
        stabilityCalculator?.onContinuityBreak(
            reason = "processor reset",
            newSegmentId = analysisSegmentId
        )

        _state.value = DataProcessorState(
            analysisSegmentId = analysisSegmentId
        )
    }

    private fun appendToBurst(packet: ParsedPacket) {
        val slot = packet.sequence % PACKETS_PER_BURST

        if (slot == 0) {
            burstPackets.clear()
        } else if (burstPackets.isEmpty()) {
            _state.update {
                it.copy(lastLog = "Burst boundary 대기 중: seq=${packet.sequence}")
            }
            return
        }

        val expectedSlot = burstPackets.size
        if (slot != expectedSlot) {
            breakContinuity(
                "Burst slot 불일치 expected=$expectedSlot actual=$slot"
            )
            return
        }

        burstPackets += packet

        if (slot == PACKETS_PER_BURST - 1) {
            if (burstPackets.size == PACKETS_PER_BURST) {
                processBurst(burstPackets.toList())
            }
            burstPackets.clear()
        }
    }
    private fun appendGreenSamples(samples: IntArray) {
        for (sample in samples) {
            greenPpgBuffer.add(sample)
            heartRateSamplePositionBuffer.add(totalHeartRateSamples)
            heartRateSampleSegmentBuffer.add(analysisSegmentId)
            heartRateSampleUsableBuffer.add(true)
            heartRateSampleMotionMaskedBuffer.add(false)
            totalHeartRateSamples += 1L
            trimHeartRateBuffers()
        }
    }

    private fun trimHeartRateBuffers() {
        while (greenPpgBuffer.size > MAX_HR_BUFFER_SAMPLES) {
            greenPpgBuffer.removeFirst()
            heartRateSamplePositionBuffer.removeFirst()
            heartRateSampleSegmentBuffer.removeFirst()
            heartRateSampleUsableBuffer.removeFirst()
            heartRateSampleMotionMaskedBuffer.removeFirst()
        }
    }

    private fun buildLatestCleanHeartRateWindow(): HeartRateSignalWindow {
        val signal = greenPpgBuffer.toList()
        val positions = heartRateSamplePositionBuffer.toList()
        val segments = heartRateSampleSegmentBuffer.toList()
        val usable = heartRateSampleUsableBuffer.toList()
        val motionMasked = heartRateSampleMotionMaskedBuffer.toList()

        val commonSize = minOf(
            signal.size,
            positions.size,
            segments.size,
            usable.size,
            motionMasked.size
        )

        if (commonSize <= 0) {
            return HeartRateSignalWindow(
                signal = emptyList(),
                startSamplePosition = totalHeartRateSamples,
                retainedBufferSampleCount = 0,
                cleanSegmentSampleCount = 0,
                invalidMaskedSampleCount = 0,
                motionMaskedSampleCount = 0
            )
        }

        val signalOffset = signal.size - commonSize
        var startIndex = commonSize

        for (i in commonSize - 1 downTo 0) {
            if (segments[i] != analysisSegmentId || !usable[i]) break
            startIndex = i
        }

        val invalidCount = usable.take(commonSize).count { !it }
        val motionCount = motionMasked.take(commonSize).count { it }

        if (startIndex >= commonSize) {
            return HeartRateSignalWindow(
                signal = emptyList(),
                startSamplePosition = totalHeartRateSamples,
                retainedBufferSampleCount = commonSize,
                cleanSegmentSampleCount = 0,
                invalidMaskedSampleCount = invalidCount,
                motionMaskedSampleCount = motionCount
            )
        }

        val available = commonSize - startIndex
        val takeCount = minOf(available, HR_WINDOW_SAMPLES)
        val windowStart = commonSize - takeCount
        val absoluteStart = positions[windowStart]

        return HeartRateSignalWindow(
            signal = (windowStart until commonSize)
                .map { signal[signalOffset + it].toDouble() },
            startSamplePosition = absoluteStart,
            retainedBufferSampleCount = commonSize,
            cleanSegmentSampleCount = available,
            invalidMaskedSampleCount = invalidCount,
            motionMaskedSampleCount = motionCount
        )
    }

    private fun maskRecentHeartRateSamplesForMotion(sampleCount: Int) {
        if (sampleCount <= 0 || heartRateSampleUsableBuffer.isEmpty()) return

        val usable = heartRateSampleUsableBuffer.toMutableList()
        val motion = heartRateSampleMotionMaskedBuffer.toMutableList()
        val segments = heartRateSampleSegmentBuffer.toList()
        val count = minOf(sampleCount, usable.size)

        for (i in usable.lastIndex downTo usable.size - count) {
            if (segments[i] == analysisSegmentId) {
                usable[i] = false
                motion[i] = true
            }
        }

        heartRateSampleUsableBuffer.clear()
        heartRateSampleUsableBuffer.addAll(usable)
        heartRateSampleMotionMaskedBuffer.clear()
        heartRateSampleMotionMaskedBuffer.addAll(motion)
    }
    private fun processBurst(packets: List<ParsedPacket>) {
        val imuBytes = ByteArray(packets.sumOf { it.imuData.size })
        val ppgBytes = ByteArray(packets.sumOf { it.ppgData.size })
        var imuOffset = 0
        var ppgOffset = 0

        packets.forEach { packet ->
            packet.imuData.copyInto(imuBytes, imuOffset)
            imuOffset += packet.imuData.size
            packet.ppgData.copyInto(ppgBytes, ppgOffset)
            ppgOffset += packet.ppgData.size
        }

        val sensorData = SensorData(
            timestamp = packets.last().timestamp,
            sequenceStart = packets.first().sequence,
            sequenceEnd = packets.last().sequence,
            packetCount = packets.size,
            ntcRaw = packets.asReversed()
                .firstOrNull { it.ntcRaw != 0 }?.ntcRaw ?: 0,
            batteryRaw = packets.asReversed()
                .firstOrNull { it.batteryRaw != 0 }?.batteryRaw ?: 0,
            ppgData = ppgBytes,
            imuData = imuBytes
        )

        val greenSamples = decodeGreenPpg(ppgBytes)
        appendGreenSamples(greenSamples)

        val now = System.currentTimeMillis()
        val motion = calculateImuMotionSummary(imuBytes)
        val rawAnalysis = analyzeStableGreenHeartRate(
            motion = motion,
            currentFrameSampleCount = greenSamples.size
        )
        val freshEstimate = rawAnalysis.estimate

        if (freshEstimate != null) {
            lastValidHeartRate = freshEstimate
            lastValidHeartRateAt = now
        }

        val heldAge = lastValidHeartRateAt?.let { now - it }
        val displayed = freshEstimate ?: lastValidHeartRate?.takeIf {
            heldAge != null && heldAge <= HEART_RATE_HOLD_MILLIS
        }

        val processingState = when {
            freshEstimate != null -> HeartRateProcessingState.VALID
            displayed != null -> HeartRateProcessingState.HELD_PREVIOUS
            else -> rawAnalysis.diagnostics.processingState
        }

        val status = MetricCalculationStatus(
            state = when (processingState) {
                HeartRateProcessingState.VALID ->
                    MetricCalculationState.VALID
                HeartRateProcessingState.COLLECTING ->
                    MetricCalculationState.COLLECTING
                HeartRateProcessingState.HELD_PREVIOUS ->
                    MetricCalculationState.REJECTED
                else ->
                    MetricCalculationState.REJECTED
            },
            message = if (processingState == HeartRateProcessingState.HELD_PREVIOUS) {
                "새 심박 계산 실패 · 이전 정상값 유지"
            } else {
                rawAnalysis.diagnostics.message
            }
        )

        val counters = _state.value
        val diagnostics = rawAnalysis.diagnostics.copy(
            processingState = processingState,
            underlyingFailureReason =
                if (processingState == HeartRateProcessingState.HELD_PREVIOUS) {
                    rawAnalysis.diagnostics.processingState
                } else {
                    null
                },
            message = status.message,
            calculatedBpm = freshEstimate?.bpm,
            displayedBpm = displayed?.bpm,
            heartRateFresh = freshEstimate != null,
            heartRateAgeMillis = heldAge,
            source = if (displayed != null) {
                HeartRateSource.GREEN
            } else {
                HeartRateSource.NONE
            },
            sourceLog = displayed?.sourceLog,
            crcErrorCount = counters.crcErrorCount,
            sequenceLossCount = counters.missingSequenceErrors,
            estimatedLostPacketCount = counters.estimatedLostPacketCount
        )

        val graphData = buildHeartRateGraphData(
            estimate = freshEstimate,
            diagnostics = diagnostics
        )

        stabilityCalculator?.activeBaselinesSnapshot()?.let { baselines ->
            arousalCalculator.updatePersonalBaselines(baselines)
        }

        val arousalState =
            arousalCalculator.processBurst(
                sensorData = sensorData,
                heartRateEstimate = freshEstimate,
                heartRateStatus = status,
                analysisSegmentId = analysisSegmentId
            )
        val stabilityState = stabilityCalculator?.processFrame(
            StabilityFrameInput(
                phoneTimeMillis = now,
                sensorTimestamp = sensorData.timestamp,
                arousalState = arousalState,
                heartRateDiagnostics = diagnostics,
                analysisSegmentId = analysisSegmentId,
                continuityBreakCount = counters.continuityBreakCount,
                crcErrorCount = counters.crcErrorCount,
                sequenceLossCount = counters.missingSequenceErrors,
                estimatedLostPacketCount = counters.estimatedLostPacketCount
            )
        ) ?: StabilityState()
        val greenMax = greenSamples.maxOrNull()?.toDouble() ?: 0.0

        dataLogger?.logHeartRateDiagnostics(
            now,
            sensorData.timestamp,
            diagnostics
        )
        dataLogger?.logArousalState(
            phoneTimeMillis = now,
            timestamp = sensorData.timestamp,
            arousalState = arousalState,
            complete = "true",
            missPacketNum = counters.estimatedLostPacketCount.toString(),
            errorLog = ""
        )

        _state.update { current ->
            current.copy(
                lastParsedData = sensorData,
                heartRateBpm = displayed?.bpm,
                heartRateQuality = displayed?.qualityScore,
                heartRateFresh = freshEstimate != null,
                heartRateAgeMillis = heldAge,
                heartRateDiagnostics = diagnostics,
                heartRateCalculationStatus = status,
                heartRateGraphData = graphData,
                parsedSuperFrameCount =
                    current.parsedSuperFrameCount + 1,
                analysisSegmentId = analysisSegmentId,
                lastGreenMax = greenMax,
                arousalState = arousalState,
                stabilityState = stabilityState,
                lastLog =
                    "Burst ${sensorData.sequenceStart}-${sensorData.sequenceEnd} 처리 완료"
            )
        }
    }
    private fun analyzeStableGreenHeartRate(
        motion: ImuMotionSummary?,
        currentFrameSampleCount: Int
    ): HeartRateAnalysisResult {
        if (motion?.isStrongMotion == true) {
            maskRecentHeartRateSamplesForMotion(currentFrameSampleCount)

            val clean = buildLatestCleanHeartRateWindow()
            return HeartRateAnalysisResult(
                estimate = null,
                diagnostics = HeartRateDiagnostics(
                    processingState = HeartRateProcessingState.MOTION_ARTIFACT,
                    message = "지속 움직임으로 현재 1초 Green PPG를 HR 분석에서 제외",
                    analysisSegmentId = analysisSegmentId,
                    windowSampleCount = clean.signal.size,
                    windowSeconds =
                        clean.signal.size / POTCH_PPG_SAMPLE_RATE_HZ,
                    greenDcMean =
                        clean.signal.takeIf { it.isNotEmpty() }?.average(),
                    greenMin = clean.signal.minOrNull(),
                    greenMax = clean.signal.maxOrNull(),
                    imuMaxDeltaG = motion.maxDeltaG,
                    imuP95DeltaG = motion.p95DeltaG,
                    imuMotionExceedanceRatio = motion.exceedanceRatio,
                    retainedBufferSampleCount =
                        clean.retainedBufferSampleCount,
                    cleanSegmentSampleCount =
                        clean.cleanSegmentSampleCount,
                    invalidMaskedSampleCount =
                        clean.invalidMaskedSampleCount,
                    motionMaskedSampleCount =
                        clean.motionMaskedSampleCount,
                    source = HeartRateSource.GREEN,
                    sourceLog = "Green PPG · motion mask"
                )
            )
        }

        val positive = analyzeGreenHeartRate(
            requiredPolarity = HeartRatePeakPolarity.POSITIVE,
            imuMotion = motion
        )

        if (activePolarity == HeartRatePeakPolarity.POSITIVE) {
            val positiveEstimate = positive.estimate

            if (
                positiveEstimate == null &&
                positive.diagnostics.processingState ==
                HeartRateProcessingState.COLLECTING
            ) {
                return positive
            }

            if (positiveEstimate != null) {
                positiveFailureStreak = 0
                activePolarityFailureStreak = 0
                positiveRecoveryStreak = 0
                clearPendingPolarity()
                return withPathLog(
                    positive,
                    "Green positive 경로 유지"
                )
            }

            positiveFailureStreak += 1

            if (positiveFailureStreak < POLARITY_FAILURES_BEFORE_FALLBACK) {
                return waitForPolarity(
                    positive,
                    "Green positive 실패 $positiveFailureStreak/" +
                            "$POLARITY_FAILURES_BEFORE_FALLBACK; " +
                            "즉시 polarity를 바꾸지 않음"
                )
            }

            val negative = analyzeGreenHeartRate(
                requiredPolarity = HeartRatePeakPolarity.NEGATIVE,
                imuMotion = motion
            )

            val negativeEstimate = negative.estimate
            if (negativeEstimate != null) {
                val confirmed = confirmPendingPolarity(
                    HeartRatePeakPolarity.NEGATIVE,
                    negativeEstimate.bpm
                )

                if (confirmed) {
                    activePolarity = HeartRatePeakPolarity.NEGATIVE
                    activePolarityFailureStreak = 0
                    positiveRecoveryStreak = 0
                    return withPathLog(
                        negative,
                        "Green positive 연속 실패 후 negative " +
                                "$POLARITY_CONFIRM_FRAMES 회 확인, 전환"
                    )
                }

                return waitForPolarity(
                    negative,
                    "Green negative 전환 확인 중 " +
                            "$pendingPolaritySuccessStreak/" +
                            "$POLARITY_CONFIRM_FRAMES"
                )
            }

            clearPendingPolarity()
            return selectMoreInformativeFailure(positive, negative)
        }

        val negative = analyzeGreenHeartRate(
            requiredPolarity = HeartRatePeakPolarity.NEGATIVE,
            imuMotion = motion
        )

        if (
            negative.estimate == null &&
            negative.diagnostics.processingState ==
            HeartRateProcessingState.COLLECTING &&
            positive.estimate == null
        ) {
            return negative
        }

        if (positive.estimate != null) {
            positiveRecoveryStreak += 1
            if (positiveRecoveryStreak >= POLARITY_RECOVERY_CONFIRM_FRAMES) {
                activePolarity = HeartRatePeakPolarity.POSITIVE
                positiveFailureStreak = 0
                activePolarityFailureStreak = 0
                positiveRecoveryStreak = 0
                clearPendingPolarity()
                return withPathLog(
                    positive,
                    "Green positive $POLARITY_RECOVERY_CONFIRM_FRAMES 회 확인, 기본 경로 복귀"
                )
            }
        } else {
            positiveRecoveryStreak = 0
        }

        if (negative.estimate != null) {
            activePolarityFailureStreak = 0
            return withPathLog(
                negative,
                if (positiveRecoveryStreak > 0) {
                    "Green negative 유지 · positive 복귀 확인 중 " +
                            "$positiveRecoveryStreak/" +
                            "$POLARITY_RECOVERY_CONFIRM_FRAMES"
                } else {
                    "Green negative fallback 유지"
                }
            )
        }

        activePolarityFailureStreak += 1
        return if (
            positive.estimate != null ||
            activePolarityFailureStreak < POLARITY_FAILURES_BEFORE_FALLBACK
        ) {
            waitForPolarity(
                negative,
                "Green negative 실패 $activePolarityFailureStreak/" +
                        "$POLARITY_FAILURES_BEFORE_FALLBACK; " +
                        "positive 복귀를 확인 중"
            )
        } else {
            selectMoreInformativeFailure(positive, negative)
        }
    }

    private fun withPathLog(
        analysis: HeartRateAnalysisResult,
        log: String
    ): HeartRateAnalysisResult {
        val estimate = analysis.estimate?.copy(sourceLog = log)
        return analysis.copy(
            estimate = estimate,
            diagnostics = analysis.diagnostics.copy(
                message = log,
                source = HeartRateSource.GREEN,
                sourceLog = log
            )
        )
    }

    private fun waitForPolarity(
        analysis: HeartRateAnalysisResult,
        log: String
    ): HeartRateAnalysisResult {
        return HeartRateAnalysisResult(
            estimate = null,
            diagnostics = analysis.diagnostics.copy(
                processingState = HeartRateProcessingState.COLLECTING,
                message = log,
                calculatedBpm = null,
                source = HeartRateSource.GREEN,
                sourceLog = log
            )
        )
    }

    private fun confirmPendingPolarity(
        polarity: HeartRatePeakPolarity,
        bpm: Int
    ): Boolean {
        val samePolarity = pendingPolarity == polarity
        val bpmConsistent = pendingPolarityLastBpm?.let {
            abs(it - bpm) <= POLARITY_PENDING_BPM_TOLERANCE
        } ?: true

        if (samePolarity && bpmConsistent) {
            pendingPolaritySuccessStreak += 1
        } else {
            pendingPolarity = polarity
            pendingPolaritySuccessStreak = 1
        }

        pendingPolarityLastBpm = bpm

        if (pendingPolaritySuccessStreak >= POLARITY_CONFIRM_FRAMES) {
            clearPendingPolarity()
            return true
        }

        return false
    }

    private fun clearPendingPolarity() {
        pendingPolarity = null
        pendingPolaritySuccessStreak = 0
        pendingPolarityLastBpm = null
    }

    private fun resetPolaritySelection() {
        activePolarity = HeartRatePeakPolarity.POSITIVE
        positiveFailureStreak = 0
        positiveRecoveryStreak = 0
        activePolarityFailureStreak = 0
        clearPendingPolarity()
    }

    private fun selectMoreInformativeFailure(
        first: HeartRateAnalysisResult,
        second: HeartRateAnalysisResult
    ): HeartRateAnalysisResult {
        return listOf(first, second).maxWithOrNull(
            compareBy<HeartRateAnalysisResult> {
                failureInformationRank(it.diagnostics.processingState)
            }.thenBy {
                it.diagnostics.validIbiCount
            }
        ) ?: first
    }

    private fun failureInformationRank(
        state: HeartRateProcessingState
    ): Int {
        return when (state) {
            HeartRateProcessingState.MOTION_ARTIFACT -> 100
            HeartRateProcessingState.SIGNAL_SATURATED -> 95
            HeartRateProcessingState.NO_CONTACT -> 90
            HeartRateProcessingState.ABRUPT_SIGNAL_CHANGE -> 85
            HeartRateProcessingState.AMPLITUDE_UNSTABLE -> 80
            HeartRateProcessingState.HIGH_SPECTRAL_ENTROPY -> 75
            HeartRateProcessingState.LOW_SPECTRAL_CONCENTRATION -> 70
            HeartRateProcessingState.INVALID_IBI -> 65
            HeartRateProcessingState.BPM_OUT_OF_RANGE -> 60
            HeartRateProcessingState.INSUFFICIENT_PEAKS -> 50
            HeartRateProcessingState.SIGNAL_TOO_WEAK -> 40
            HeartRateProcessingState.PACKET_LOSS -> 30
            HeartRateProcessingState.COLLECTING -> 20
            HeartRateProcessingState.HELD_PREVIOUS -> 10
            HeartRateProcessingState.VALID -> 0
        }
    }
    private fun analyzeGreenHeartRate(
        requiredPolarity: HeartRatePeakPolarity,
        imuMotion: ImuMotionSummary?
    ): HeartRateAnalysisResult {
        val sampleRateHz = POTCH_PPG_SAMPLE_RATE_HZ
        val cleanWindow = buildLatestCleanHeartRateWindow()
        val signal = cleanWindow.signal
        val contactSignal = signal

        val greenDcMean = contactSignal.takeIf { it.isNotEmpty() }?.average()
        val greenMin = contactSignal.minOrNull()
        val greenMax = contactSignal.maxOrNull()
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
            validIbiCount: Int = 0,
            preprocess: PpgPreprocessResult? = null
        ): HeartRateDiagnostics {
            val detectedPeakSamplePositions =
                selectedCandidate
                    ?.peakPositions
                    ?.map { relative ->
                        cleanWindow.startSamplePosition.toDouble() + relative
                    }
                    ?: emptyList()

            val acceptedIbiEndSamplePositions =
                selectedCandidate
                    ?.usedIntervals
                    ?.map { it.endSamplePosition }
                    ?: emptyList()

            val acceptedEndPositionSet =
                acceptedIbiEndSamplePositions.toSet()

            val rejectedIbiEndSamplePositions =
                selectedCandidate
                    ?.rawIntervals
                    ?.map { it.endSamplePosition }
                    ?.filterNot { it in acceptedEndPositionSet }
                    ?.distinct()
                    ?: emptyList()

            return HeartRateDiagnostics(
                processingState = state,
                message = message,
                analysisSegmentId = analysisSegmentId,
                windowSampleCount = signal.size,
                windowSeconds = signal.size / sampleRateHz,
                greenDcMean = greenDcMean,
                greenMin = greenMin,
                greenMax = greenMax,
                acRobustAmplitude = acRobustAmplitude,
                amplitudeCoefficientOfVariation = amplitudeCoefficientOfVariation,
                spectralConcentration = spectralConcentration,
                spectralEntropy = spectralEntropy,
                abruptChangeRatio = abruptChangeRatio,
                selectedPeakThreshold = selectedCandidate?.thresholdOffset,
                selectedThresholdPercent = selectedCandidate?.thresholdPercent,
                selectedPolarity = selectedCandidate?.polarity
                    ?: HeartRatePeakPolarity.NONE,
                detectedPeakSamplePositions = detectedPeakSamplePositions,
                acceptedIbiEndSamplePositions = acceptedIbiEndSamplePositions,
                rejectedIbiEndSamplePositions = rejectedIbiEndSamplePositions,
                referencePeakSamplePosition =
                    detectedPeakSamplePositions.firstOrNull(),
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
                source = HeartRateSource.GREEN,
                sourceLog = "Green PPG 단일 채널",
                meanPeakInterpolationOffsetMs = selectedCandidate?.meanInterpolationOffsetMs,
                maxPeakInterpolationOffsetMs = selectedCandidate?.maxInterpolationOffsetMs,
                imuMaxDeltaG = imuMotion?.maxDeltaG,
                imuP95DeltaG = imuMotion?.p95DeltaG,
                imuMotionExceedanceRatio = imuMotion?.exceedanceRatio,
                retainedBufferSampleCount = cleanWindow.retainedBufferSampleCount,
                cleanSegmentSampleCount = cleanWindow.cleanSegmentSampleCount,
                invalidMaskedSampleCount = cleanWindow.invalidMaskedSampleCount,
                motionMaskedSampleCount = cleanWindow.motionMaskedSampleCount,
                interpolatedSampleCount = preprocess?.interpolatedSampleCount ?: 0,
                excludedPeakSampleCount = preprocess?.excludedPeakSampleCount ?: 0,
                longestInterpolatedRun = preprocess?.longestInterpolatedRun ?: 0,
                maxRawSampleDelta = maxRawSampleDelta
            )
        }

        if (signal.isEmpty() || contactSignal.isEmpty()) {
            return HeartRateAnalysisResult(
                estimate = null,
                diagnostics = diagnostics(
                    state = HeartRateProcessingState.COLLECTING,
                    message = "PPG 심박 신호 수집 전"
                )
            )
        }

        if (
            greenDcMean == null ||
            greenDcMean < HEART_RATE_CONTACT_DC_MIN ||
            (greenMax ?: 0.0) < HEART_RATE_CONTACT_DC_MIN
        ) {
            return HeartRateAnalysisResult(
                estimate = null,
                diagnostics = diagnostics(
                    state = HeartRateProcessingState.NO_CONTACT,
                    message = "Green PPG 접촉 신호 없음/약함: DC=${greenDcMean?.let { "%.1f".format(it) } ?: "-"}"
                )
            )
        }

        val saturatedCount = contactSignal.count {
            it >= HEART_RATE_SATURATION_HIGH ||
                    it >= HEART_RATE_PPG_ADC_MAX
        }
        val saturationRatio =
            saturatedCount.toDouble() / contactSignal.size.toDouble()

        if (saturationRatio >= 0.02) {
            return HeartRateAnalysisResult(
                estimate = null,
                diagnostics = diagnostics(
                    state = HeartRateProcessingState.SIGNAL_SATURATED,
                    message = "Green PPG 포화: ${(saturationRatio * 100.0).let { "%.1f".format(it) }}%"
                )
            )
        }

        if (signal.size < MIN_HR_SAMPLES) {
            return HeartRateAnalysisResult(
                estimate = null,
                diagnostics = diagnostics(
                    state = HeartRateProcessingState.COLLECTING,
                    message = "Green PPG 심박 신호 수집 중: ${signal.size}/$MIN_HR_SAMPLES samples"
                )
            )
        }

        val preprocessResult = preprocessPpgForHeartRate(signal)
        val bandPassed = preprocessResult.bandPassed
        val acRobustAmplitude = calculateRobustAmplitude(bandPassed)

        if (
            !acRobustAmplitude.isFinite() ||
            acRobustAmplitude < HEART_RATE_MIN_ROBUST_AC_AMPLITUDE
        ) {
            return HeartRateAnalysisResult(
                estimate = null,
                diagnostics = diagnostics(
                    state = HeartRateProcessingState.SIGNAL_TOO_WEAK,
                    message = "Green PPG AC 진폭 부족: robust amplitude=${"%.2f".format(acRobustAmplitude)}",
                    acRobustAmplitude = acRobustAmplitude,
                    preprocess = preprocessResult
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

        val robustRawSampleDelta =
            calculateConsecutiveDifferencePercentile(
                values = signal,
                percentile = HEART_RATE_ABRUPT_CHANGE_PERCENTILE
            )

        val abruptChangeRatio =
            robustRawSampleDelta?.div(acRobustAmplitude)

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
                    abruptChangeRatio = abruptChangeRatio,
                    preprocess = preprocessResult
                )
            )
        }

        val excludedPeakRatio =
            if (signal.isNotEmpty()) {
                preprocessResult.excludedPeakSampleCount.toDouble() / signal.size.toDouble()
            } else {
                0.0
            }

        val longArtifactRatio =
            if (signal.isNotEmpty()) {
                preprocessResult.longArtifactSampleCount.toDouble() / signal.size.toDouble()
            } else {
                0.0
            }

        // 짧은 spike가 여러 번 발견됐다는 이유만으로 window 전체를 버리지는 않는다.
        // 200ms를 넘는 연속 artifact 또는 전체의 10%를 넘는 긴 artifact만 hard reject한다.
        if (
            preprocessResult.longestInterpolatedRun > 20 ||
            longArtifactRatio > 0.10
        ) {
            return spectralDiagnostics(
                state = HeartRateProcessingState.ABRUPT_SIGNAL_CHANGE,
                message = "Green PPG 장시간 artifact 과다: " +
                        "longest=${preprocessResult.longestInterpolatedRun}, " +
                        "ratio=${"%.3f".format(longArtifactRatio)}"
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
                message = "BVP 연속 sample 변화 p99 과다: normalized=${"%.4f".format(abruptChangeRatio)} " +
                        "> $HEART_RATE_MAX_ABRUPT_CHANGE_RATIO"
            )
        }

        val positiveTop = DoubleArray(bandPassed.size) { i ->
            bandPassed[i].coerceAtLeast(0.0)
        }
        val negativeTop = DoubleArray(bandPassed.size) { i ->
            (-bandPassed[i]).coerceAtLeast(0.0)
        }

        val bufferStartSampleIndex = cleanWindow.startSamplePosition

        val positiveSearch =
            if (
                requiredPolarity == HeartRatePeakPolarity.POSITIVE
            ) {
                findBestHeartRatePeakFit(
                    signal = positiveTop,
                    sampleRateHz = sampleRateHz,
                    bufferStartSampleIndex = bufferStartSampleIndex,
                    segmentId = analysisSegmentId,
                    polarity = HeartRatePeakPolarity.POSITIVE,
                    excludedPeakMask = preprocessResult.excludedPeakMask
                )
            } else {
                null
            }

        val negativeSearch =
            if (
                requiredPolarity == HeartRatePeakPolarity.NEGATIVE
            ) {
                findBestHeartRatePeakFit(
                    signal = negativeTop,
                    sampleRateHz = sampleRateHz,
                    bufferStartSampleIndex = bufferStartSampleIndex,
                    segmentId = analysisSegmentId,
                    polarity = HeartRatePeakPolarity.NEGATIVE,
                    excludedPeakMask = preprocessResult.excludedPeakMask
                )
            } else {
                null
            }

        val candidateComparator =
            compareBy<HeartRatePeakFitCandidate> { it.selectionScore }
                .thenByDescending { it.rawIntervalQualityScore }
                .thenByDescending { it.usedIntervals.size }
                .thenByDescending { it.acceptedIntervalRatio }

        val bestFit = listOfNotNull(
            positiveSearch?.bestCandidate,
            negativeSearch?.bestCandidate
        ).minWithOrNull(candidateComparator)

        val bestRejectedFit = listOfNotNull(
            positiveSearch?.bestRejectedCandidate,
            negativeSearch?.bestRejectedCandidate
        ).minWithOrNull(candidateComparator)

        val maxDetectedPeakCount = maxOf(
            positiveSearch?.maxDetectedPeakCount ?: 0,
            negativeSearch?.maxDetectedPeakCount ?: 0
        )
        val maxRawIbiCount = maxOf(
            positiveSearch?.maxRawIntervalCount ?: 0,
            negativeSearch?.maxRawIntervalCount ?: 0
        )
        val maxValidIbiCount = maxOf(
            positiveSearch?.maxValidIntervalCount ?: 0,
            negativeSearch?.maxValidIntervalCount ?: 0
        )

        if (bestFit == null) {
            val failureState = when {
                // 3초부터 계산을 시도하지만, 6초 이전 실패는 데이터 부족 가능성이 커서
                // 확정 실패가 아니라 COLLECTING으로 남긴다.
                signal.size < ADAPTIVE_FIT_PREFERRED_SAMPLES ->
                    HeartRateProcessingState.COLLECTING

                maxDetectedPeakCount < 4 ->
                    HeartRateProcessingState.INSUFFICIENT_PEAKS

                // 정상 범위 BPM 후보가 hard reject된 경우 실제 원인은 BPM 범위가 아니라 IBI 품질이다.
                bestRejectedFit != null ||
                        positiveSearch?.sawInvalidIbi == true ||
                        negativeSearch?.sawInvalidIbi == true ||
                        maxRawIbiCount > 0 ->
                    HeartRateProcessingState.INVALID_IBI

                positiveSearch?.sawBpmOutOfRange == true ||
                        negativeSearch?.sawBpmOutOfRange == true ->
                    HeartRateProcessingState.BPM_OUT_OF_RANGE

                else ->
                    HeartRateProcessingState.INSUFFICIENT_PEAKS
            }

            val message = when (failureState) {
                HeartRateProcessingState.COLLECTING ->
                    "adaptive peak fitting용 IBI 추가 수집 중: ${signal.size}/$ADAPTIVE_FIT_PREFERRED_SAMPLES samples"

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
                    validIbiCount = maxValidIbiCount,
                    preprocess = preprocessResult
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
                    selectedCandidate = bestFit,
                    preprocess = preprocessResult
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
                    selectedCandidate = bestFit,
                    preprocess = preprocessResult
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
                    (abruptChangeRatio ?: HEART_RATE_ABRUPT_CHANGE_SCORE_ZERO_RATIO) /
                    HEART_RATE_ABRUPT_CHANGE_SCORE_ZERO_RATIO)
                .coerceIn(0.0, 1.0)

        val bvpSignalQuality =
            (spectralConcentrationScore * 0.35 +
                    spectralEntropyScore * 0.30 +
                    amplitudeStabilityScore * 0.20 +
                    abruptChangeScore * 0.15)
                .coerceIn(0.0, 1.0)

        val artifactRetentionScore =
            (1.0 - excludedPeakRatio / HEART_RATE_MAX_EXCLUDED_SAMPLE_RATIO)
                .coerceIn(0.0, 1.0)

        // 정제된 IBI와 raw interval 품질에 더해 보간/제외 비율을 품질에 반영한다.
        val qualityScore =
            ((baseQuality * 0.35 +
                    bestFit.rawIntervalQualityScore * 0.45 +
                    bvpSignalQuality * 0.20) *
                    (0.70 + artifactRetentionScore * 0.30))
                .coerceIn(0.0, 1.0)

        val detectedPeakSamplePositions =
            bestFit.peakPositions.map { relative ->
                bufferStartSampleIndex.toDouble() + relative
            }

        val acceptedIbiEndSamplePositions =
            bestFit.usedIntervals.map { it.endSamplePosition }

        val acceptedEndPositionSet =
            acceptedIbiEndSamplePositions.toSet()

        val rejectedIbiEndSamplePositions =
            bestFit.rawIntervals
                .map { it.endSamplePosition }
                .filterNot { it in acceptedEndPositionSet }
                .distinct()

        val lowerEnvelopeSamples = PpgLowerEnvelopeExtractor.extract(
            rawSamples = cleanWindow.signal,
            windowStartSamplePosition = cleanWindow.startSamplePosition,
            acceptedIntervals = bestFit.usedIntervals,
            segmentId = analysisSegmentId,
            sampleRateHz = sampleRateHz
        )

        val estimate = HeartRateEstimate(
            bpm = bpm,
            ibiIntervals = bestFit.usedIntervals,
            peakCount = bestFit.peakPositions.size,
            intervalCount = bestFit.usedIntervals.size,
            averageIntervalSec = avgInterval,
            qualityScore = qualityScore,
            source = HeartRateSource.GREEN,
            sourceLog = "Green 단일 채널 HR 분석",
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
            detectedPeakSamplePositions = detectedPeakSamplePositions,
            acceptedIbiEndSamplePositions = acceptedIbiEndSamplePositions,
            rejectedIbiEndSamplePositions = rejectedIbiEndSamplePositions,
            referencePeakSamplePosition =
                detectedPeakSamplePositions.firstOrNull(),
            meanPeakInterpolationOffsetMs = bestFit.meanInterpolationOffsetMs,
            maxPeakInterpolationOffsetMs = bestFit.maxInterpolationOffsetMs,
            lowerEnvelopeSamples = lowerEnvelopeSamples,
            // 첫 재계산 가능 lower point부터만 교체한다. window 시작점에 걸친
            // 박동 쌍은 이번 snapshot에서 복원할 수 없으므로 이전 값을 보존한다.
            lowerEnvelopeReplacementStartSamplePosition =
                lowerEnvelopeSamples.firstOrNull()?.samplePosition
        )

        return HeartRateAnalysisResult(
            estimate = estimate,
            diagnostics = diagnostics(
                state = HeartRateProcessingState.VALID,
                message = "Green PPG 심박수 정상 검출: $bpm bpm",
                acRobustAmplitude = acRobustAmplitude,
                amplitudeCoefficientOfVariation = amplitudeCoefficientOfVariation,
                spectralConcentration = spectralQuality?.concentration,
                spectralEntropy = spectralQuality?.entropy,
                abruptChangeRatio = abruptChangeRatio,
                selectedCandidate = bestFit,
                qualityScore = qualityScore,
                preprocess = preprocessResult
            ).copy(
                calculatedBpm = bpm
            )
        )

    }
    private fun preprocessPpgForHeartRate(
        rawSignal: List<Double>
    ): PpgPreprocessResult {
        val sampleRateHz = POTCH_PPG_SAMPLE_RATE_HZ

        if (rawSignal.isEmpty()) {
            return PpgPreprocessResult(
                bandPassed = doubleArrayOf(),
                excludedPeakMask = booleanArrayOf(),
                interpolatedSampleCount = 0,
                excludedPeakSampleCount = 0,
                longestInterpolatedRun = 0,
                longArtifactSampleCount = 0
            )
        }

        // 1) DC 제거. raw 값 자체의 offset은 HR peak 검출에 필요 없다.
        val mean = rawSignal.average()
        val acSignal = DoubleArray(rawSignal.size) { i ->
            rawSignal[i] - mean
        }

        // 2) Hampel 기반으로 짧은 spike run을 찾고, 필터 연속성을 위해 선형 보간한다.
        //    보간 영역과 주변 margin은 peak 후보에서 제외해 가짜 peak 생성을 막는다.
        val repaired = interpolateShortPpgArtifacts(
            data = acSignal,
            halfWindowSamples = 3,
            thresholdScale = 3.0,
            maxShortRunSamples = HEART_RATE_SPIKE_INTERPOLATION_MAX_SAMPLES,
            exclusionMarginSamples =
                HEART_RATE_SPIKE_PEAK_EXCLUSION_MARGIN_SAMPLES
        )

        // 3) HeartPy filter_signal(..., cutoff=[0.75, 3.5], filtertype="bandpass")에 해당.
        //    0.75~3.5Hz = 약 45~210bpm 대역만 남긴다.
        val bandPassed = forwardBackwardBandPass(
            data = repaired.first,
            sampleRateHz = sampleRateHz,
            lowCutHz = 0.75,
            highCutHz = 3.5
        )

        val stats = repaired.second

        return PpgPreprocessResult(
            bandPassed = bandPassed,
            excludedPeakMask = stats.excludedPeakMask,
            interpolatedSampleCount = stats.interpolatedSampleCount,
            excludedPeakSampleCount = stats.excludedPeakSampleCount,
            longestInterpolatedRun = stats.longestRun,
            longArtifactSampleCount = stats.longArtifactSampleCount
        )
    }

    private fun interpolateShortPpgArtifacts(
        data: DoubleArray,
        halfWindowSamples: Int,
        thresholdScale: Double,
        maxShortRunSamples: Int,
        exclusionMarginSamples: Int
    ): Pair<DoubleArray, PpgArtifactRepairStats> {
        if (data.isEmpty()) {
            return data to PpgArtifactRepairStats(
                excludedPeakMask = booleanArrayOf(),
                interpolatedSampleCount = 0,
                excludedPeakSampleCount = 0,
                longestRun = 0,
                longArtifactSampleCount = 0
            )
        }

        val outlierMask = BooleanArray(data.size)

        for (i in data.indices) {
            val from = (i - halfWindowSamples).coerceAtLeast(0)
            val toExclusive = (i + halfWindowSamples + 1).coerceAtMost(data.size)
            val window = data.copyOfRange(from, toExclusive)
            val windowMedian = median(window.toList())
            val deviations = DoubleArray(window.size) { idx ->
                abs(window[idx] - windowMedian)
            }
            val mad = median(deviations.toList())

            if (mad <= 0.0) continue

            val threshold = thresholdScale * 1.4826 * mad
            outlierMask[i] = abs(data[i] - windowMedian) > threshold
        }

        val repaired = data.copyOf()
        val excludedPeakMask = BooleanArray(data.size)

        var interpolatedSampleCount = 0
        var longestRun = 0
        var longArtifactSampleCount = 0
        var index = 0

        while (index < data.size) {
            if (!outlierMask[index]) {
                index += 1
                continue
            }

            val runStart = index
            while (index + 1 < data.size && outlierMask[index + 1]) {
                index += 1
            }
            val runEnd = index
            val runLength = runEnd - runStart + 1
            longestRun = maxOf(longestRun, runLength)

            if (runLength > maxShortRunSamples) {
                longArtifactSampleCount += runLength
            }

            val leftIndex = runStart - 1
            val rightIndex = runEnd + 1

            when {
                leftIndex >= 0 && rightIndex < data.size -> {
                    val leftValue = data[leftIndex]
                    val rightValue = data[rightIndex]
                    val denominator = (rightIndex - leftIndex).toDouble()

                    for (j in runStart..runEnd) {
                        val fraction = (j - leftIndex).toDouble() / denominator
                        repaired[j] =
                            leftValue + (rightValue - leftValue) * fraction
                    }
                }

                leftIndex >= 0 -> {
                    for (j in runStart..runEnd) {
                        repaired[j] = data[leftIndex]
                    }
                }

                rightIndex < data.size -> {
                    for (j in runStart..runEnd) {
                        repaired[j] = data[rightIndex]
                    }
                }
            }

            interpolatedSampleCount += runLength

            val exclusionStart =
                (runStart - exclusionMarginSamples).coerceAtLeast(0)
            val exclusionEnd =
                (runEnd + exclusionMarginSamples).coerceAtMost(data.lastIndex)

            for (j in exclusionStart..exclusionEnd) {
                excludedPeakMask[j] = true
            }

            index += 1
        }

        val excludedPeakSampleCount = excludedPeakMask.count { it }

        return repaired to PpgArtifactRepairStats(
            excludedPeakMask = excludedPeakMask,
            interpolatedSampleCount = interpolatedSampleCount,
            excludedPeakSampleCount = excludedPeakSampleCount,
            longestRun = longestRun,
            longArtifactSampleCount = longArtifactSampleCount
        )
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

    private fun findBestHeartRatePeakFit(
        signal: DoubleArray,
        sampleRateHz: Double,
        bufferStartSampleIndex: Long,
        segmentId: Long,
        polarity: HeartRatePeakPolarity,
        excludedPeakMask: BooleanArray
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
                minPeakDistanceSamples = minPeakDistanceSamples,
                excludedPeakMask = excludedPeakMask
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

    private fun detectHeartRatePeaksByRoi(
        signal: DoubleArray,
        movingAverage: DoubleArray,
        thresholdOffset: Double,
        minPeakDistanceSamples: Int,
        excludedPeakMask: BooleanArray
    ): List<Int> {
        if (signal.size != movingAverage.size || signal.size < 3) {
            return emptyList()
        }

        val roiPeaks = mutableListOf<Int>()
        var roiStart = -1

        for (i in signal.indices) {
            val threshold = movingAverage[i] + thresholdOffset
            val excluded = excludedPeakMask.getOrElse(i) { false }
            val aboveThreshold = !excluded && signal[i] > threshold

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
        sampleRateHz: Double = POTCH_PPG_SAMPLE_RATE_HZ,
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
        if (intervals.size < 3) return intervals

        // Quotient filter는 제거한다.
        // 인접 IBI의 비율만으로 앞쪽 interval을 일방적으로 제거하면
        // 정상 IBI까지 연쇄적으로 탈락할 수 있기 때문이다.
        // 현재는 생리 범위 필터를 통과한 IBI에 대해 median 기반 outlier만 제거한다.
        val intervalMedian = median(intervals.map { it.intervalSec })

        if (!intervalMedian.isFinite() || intervalMedian <= 0.0) {
            return intervals
        }

        val medianFiltered = intervals.filter { interval ->
            abs(interval.intervalSec - intervalMedian) / intervalMedian < 0.40
        }

        // 필터 결과가 2개 미만이면 HR 계산에 필요한 정보가 지나치게 줄어드므로
        // 생리 범위를 통과한 원본 interval을 그대로 유지한다.
        return if (medianFiltered.size >= 2) {
            medianFiltered
        } else {
            intervals
        }
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

    private fun calculateConsecutiveDifferencePercentile(
        values: List<Double>,
        percentile: Double
    ): Double? {
        if (values.size < 2) return null

        val deltas = DoubleArray(values.size - 1)
        for (i in 1 until values.size) {
            deltas[i - 1] = abs(values[i] - values[i - 1])
        }

        deltas.sort()

        val clampedPercentile = percentile.coerceIn(0.0, 1.0)
        val position = clampedPercentile * deltas.lastIndex.toDouble()
        val lowerIndex = floor(position).toInt()
        val upperIndex = ceil(position).toInt()

        if (lowerIndex == upperIndex) {
            return deltas[lowerIndex]
        }

        val fraction = position - lowerIndex.toDouble()
        return deltas[lowerIndex] * (1.0 - fraction) +
                deltas[upperIndex] * fraction
    }

    private fun calculateImuMotionSummary(
        imuData: ByteArray
    ): ImuMotionSummary? {
        if (imuData.size < 12) return null

        var previousMagnitude: Double? = null
        val deltas = mutableListOf<Double>()

        for (i in imuData.indices step 12) {
            if (i + 11 >= imuData.size) break

            val xRaw = readInt16LittleEndian(imuData, i)
            val yRaw = readInt16LittleEndian(imuData, i + 2)
            val zRaw = readInt16LittleEndian(imuData, i + 4)

            val xG = xRaw / IMU_LSB_PER_G
            val yG = yRaw / IMU_LSB_PER_G
            val zG = zRaw / IMU_LSB_PER_G

            val magnitude = sqrt(
                xG * xG +
                        yG * yG +
                        zG * zG
            )

            val previous = previousMagnitude
            if (previous != null) {
                deltas += abs(magnitude - previous)
            }

            previousMagnitude = magnitude
        }

        if (deltas.isEmpty()) return null

        val sorted = deltas.sorted()
        val p95Position = (sorted.lastIndex * 0.95).coerceAtLeast(0.0)
        val lower = floor(p95Position).toInt().coerceIn(sorted.indices)
        val upper = ceil(p95Position).toInt().coerceIn(sorted.indices)
        val fraction = p95Position - lower.toDouble()
        val p95 =
            sorted[lower] * (1.0 - fraction) +
                    sorted[upper] * fraction

        val exceedanceRatio =
            deltas.count { it >= HEART_RATE_MOTION_DELTA_THRESHOLD_G }
                .toDouble() / deltas.size.toDouble()

        return ImuMotionSummary(
            maxDeltaG = sorted.last(),
            p95DeltaG = p95,
            exceedanceRatio = exceedanceRatio,
            deltaCount = deltas.size
        )
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
    private fun buildHeartRateGraphData(
        estimate: HeartRateEstimate?,
        diagnostics: HeartRateDiagnostics
    ): HeartRateGraphData {
        val clean = buildLatestCleanHeartRateWindow()
        if (clean.signal.isEmpty()) {
            return HeartRateGraphData(
                processingState = diagnostics.processingState,
                retainedBufferSampleCount =
                    clean.retainedBufferSampleCount,
                cleanSegmentSampleCount =
                    clean.cleanSegmentSampleCount,
                description = diagnostics.message
            )
        }

        val preprocess = preprocessPpgForHeartRate(clean.signal)
        val start = clean.startSamplePosition.toDouble()

        fun toRelativeIndex(position: Double): Int? {
            val value = (position - start).roundToInt()
            return value.takeIf { it in preprocess.bandPassed.indices }
        }

        val detected = estimate?.detectedPeakSamplePositions
            ?.mapNotNull(::toRelativeIndex)
            .orEmpty()
        val accepted = estimate?.acceptedIbiEndSamplePositions
            ?.mapNotNull(::toRelativeIndex)
            .orEmpty()
        val rejected = estimate?.rejectedIbiEndSamplePositions
            ?.mapNotNull(::toRelativeIndex)
            .orEmpty()
        val reference = estimate?.referencePeakSamplePosition
            ?.let(::toRelativeIndex)

        return HeartRateGraphData(
            source =
                if (estimate != null) HeartRateSource.GREEN
                else HeartRateSource.NONE,
            processingState = diagnostics.processingState,
            selectedPolarity =
                estimate?.selectedPolarity
                    ?: diagnostics.selectedPolarity,
            samples = preprocess.bandPassed.toList(),
            peakSampleIndices = accepted,
            detectedPeakSampleIndices = detected,
            acceptedPeakSampleIndices = accepted,
            rejectedPeakSampleIndices = rejected,
            referencePeakSampleIndex = reference,
            retainedBufferSampleCount =
                clean.retainedBufferSampleCount,
            cleanSegmentSampleCount =
                clean.cleanSegmentSampleCount,
            interpolatedSampleCount =
                preprocess.interpolatedSampleCount,
            excludedPeakSampleCount =
                preprocess.excludedPeakSampleCount,
            calculatedBpm = estimate?.bpm,
            qualityScore =
                estimate?.qualityScore ?: diagnostics.qualityScore,
            description = diagnostics.message
        )
    }

    private fun breakContinuity(reason: String) {
        burstPackets.clear()
        analysisSegmentId += 1L
        greenPpgBuffer.clear()
        heartRateSamplePositionBuffer.clear()
        heartRateSampleSegmentBuffer.clear()
        heartRateSampleUsableBuffer.clear()
        heartRateSampleMotionMaskedBuffer.clear()
        totalHeartRateSamples = 0L
        lastValidHeartRate = null
        lastValidHeartRateAt = null
        resetPolaritySelection()
        arousalCalculator.reset(initialSegmentId = analysisSegmentId)
        stabilityCalculator?.onContinuityBreak(
            reason = reason,
            newSegmentId = analysisSegmentId
        )

        _state.update {
            it.copy(
                continuityBreakCount =
                    it.continuityBreakCount + 1,
                analysisSegmentId = analysisSegmentId,
                lastContinuityBreakReason = reason,
                lastLog = reason
            )
        }
    }

    private fun registerPacketError(
        type: String,
        message: String,
        sequence: Int? = null,
        crc: Boolean = false
    ) {
        Log.w(TAG, message)
        dataLogger?.logDebug(TAG, message, "W")

        _state.update { current ->
            current.copy(
                crcErrorCount =
                    current.crcErrorCount + if (crc) 1 else 0,
                damagedPacketCount =
                    current.damagedPacketCount + 1,
                recentPacketErrors =
                    (current.recentPacketErrors +
                            PacketErrorLog(type, message, sequence))
                        .takeLast(20),
                lastLog = message
            )
        }
    }

    private fun decodeGreenPpg(data: ByteArray): IntArray {
        return IntArray(data.size / 2) { index ->
            readUInt16(data, index * 2)
        }
    }

    private fun readUInt16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8)

    private fun readUInt32(data: ByteArray, offset: Int): Long =
        (data[offset].toLong() and 0xFF) or
                ((data[offset + 1].toLong() and 0xFF) shl 8) or
                ((data[offset + 2].toLong() and 0xFF) shl 16) or
                ((data[offset + 3].toLong() and 0xFF) shl 24)

    private fun crc16CcittFalse(
        data: ByteArray,
        offset: Int,
        length: Int
    ): Int {
        var crc = 0xFFFF
        for (index in offset until offset + length) {
            crc = crc xor ((data[index].toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) {
                    ((crc shl 1) xor 0x1021) and 0xFFFF
                } else {
                    (crc shl 1) and 0xFFFF
                }
            }
        }
        return crc
    }

    companion object {
        private const val TAG = "PotchDataProcessor"

        private const val PACKET_SIZE = 142
        private const val PACKETS_PER_BURST = 8
        private const val CRC_OFFSET = 140
        private const val IMU_OFFSET = 12
        private const val PPG_OFFSET = 108
        private const val HEADER_0: Byte = 0xA5.toByte()
        private const val HEADER_1: Byte = 0x5A.toByte()

        private const val POTCH_PPG_SAMPLE_RATE_HZ = 128.0
        private const val IMU_LSB_PER_G = 8192.0

        // Green PPG 128 Hz 기준 최대 12초 분석창.
        private const val HR_WINDOW_SAMPLES = 1536
        // 12초 분석창보다 3초 더 보관해 continuity/정리 과정의 여유를 둔다.
        private const val MAX_HR_BUFFER_SAMPLES = 1920
        // 초기 계산 시작 조건은 기존과 동일하게 약 3초를 유지한다.
        private const val MIN_HR_SAMPLES = 384
        private const val ADAPTIVE_FIT_PREFERRED_SAMPLES = 768

        private const val HEART_RATE_MIN_BPM = 40
        private const val HEART_RATE_MAX_BPM = 180
        private const val HEART_RATE_MOVING_AVERAGE_SECONDS = 1.5

        private const val HEART_RATE_MIN_USED_INTERVAL_COUNT = 4
        private const val HEART_RATE_MIN_ACCEPTED_INTERVAL_RATIO = 0.60
        private const val HEART_RATE_MAX_RAW_SDSD_SEC = 0.200
        private const val HEART_RATE_MIN_PHYSIOLOGICAL_INTERVAL_RATIO = 0.75
        private const val HEART_RATE_RAW_IBI_CV_ZERO_SCORE = 0.30
        private const val HEART_RATE_PREFERRED_USED_INTERVAL_COUNT = 8

        private const val HEART_RATE_MIN_SPECTRAL_CONCENTRATION = 0.12
        private const val HEART_RATE_MAX_SPECTRAL_ENTROPY = 0.82
        private const val HEART_RATE_MAX_AMPLITUDE_CV = 0.50

        private const val HEART_RATE_ABRUPT_CHANGE_PERCENTILE = 0.99
        private const val HEART_RATE_ABRUPT_CHANGE_SCORE_ZERO_RATIO = 1.00
        private const val HEART_RATE_MAX_ABRUPT_CHANGE_RATIO = 1.50

        // Green uint16 전용. 실제 착용 로그에 따라 조정 가능하다.
        private const val HEART_RATE_PPG_ADC_MAX = 65535.0
        private const val HEART_RATE_CONTACT_DC_MIN = 100.0
        private const val HEART_RATE_SATURATION_HIGH = 65000.0
        private const val HEART_RATE_MIN_ROBUST_AC_AMPLITUDE = 10.0

        private const val HEART_RATE_MOTION_DELTA_THRESHOLD_G = 0.15
        private const val HEART_RATE_MOTION_P95_THRESHOLD_G = 0.15
        private const val HEART_RATE_MOTION_MIN_EXCEEDANCE_RATIO = 0.05
        private const val HEART_RATE_MOTION_SINGLE_SPIKE_HARD_G = 0.40

        private const val HEART_RATE_SPIKE_INTERPOLATION_MAX_SAMPLES = 10
        private const val HEART_RATE_SPIKE_PEAK_EXCLUSION_MARGIN_SAMPLES = 2
        private const val HEART_RATE_MAX_EXCLUDED_SAMPLE_RATIO = 0.40

        private const val HEART_RATE_COUNT_PENALTY_SEC = 0.020
        private const val HEART_RATE_REJECTION_PENALTY_SEC = 0.050

        private const val POLARITY_FAILURES_BEFORE_FALLBACK = 3
        private const val POLARITY_CONFIRM_FRAMES = 3
        private const val POLARITY_RECOVERY_CONFIRM_FRAMES = 3
        private const val POLARITY_PENDING_BPM_TOLERANCE = 8

        private const val HEART_RATE_HOLD_MILLIS = 10_000L

        private val HEART_RATE_THRESHOLD_PERCENT_CANDIDATES =
            doubleArrayOf(
                5.0, 10.0, 15.0, 20.0, 25.0, 30.0,
                40.0, 50.0, 75.0, 100.0, 150.0, 200.0, 300.0
            )
    }
}
