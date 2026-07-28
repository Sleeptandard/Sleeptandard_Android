package com.leejang.sleeptandard.Potch

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.roundToInt
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
    val selectedPeakThreshold: Double? = null,
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
    val sdsdMs: Double? = null,
    val qualityScore: Double? = null,
    val calculatedBpm: Int? = null,
    val displayedBpm: Int? = null,
    val heartRateFresh: Boolean = false,
    val heartRateAgeMillis: Long? = null,
    val source: HeartRateSource = HeartRateSource.NONE,
    val sourceLog: String? = null,
    val imuMaxDeltaG: Double? = null,
    val imuP95DeltaG: Double? = null,
    val imuMotionExceedanceRatio: Double? = null,
    val retainedBufferSampleCount: Int = 0,
    val cleanSegmentSampleCount: Int = 0,
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

data class HeartRateEstimate(
    val bpm: Int,
    val ibiIntervals: List<IbiInterval>,
    val peakCount: Int,
    val intervalCount: Int,
    val averageIntervalSec: Double,
    val qualityScore: Double,
    val source: HeartRateSource = HeartRateSource.GREEN,
    val sourceLog: String? = "Green PPG 단일 채널",
    val selectedPeakThreshold: Double? = null,
    val selectedPolarity: HeartRatePeakPolarity = HeartRatePeakPolarity.NONE,
    val rawIntervalCount: Int = intervalCount,
    val acceptedIntervalRatio: Double = 1.0,
    val detectedPeakSamplePositions: List<Double> = emptyList(),
    val acceptedIbiEndSamplePositions: List<Double> = ibiIntervals.map { it.endSamplePosition },
    val rejectedIbiEndSamplePositions: List<Double> = emptyList(),
    val referencePeakSamplePosition: Double? = detectedPeakSamplePositions.firstOrNull()
)

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
    val arousalState: ArousalState = ArousalState()
)

class PotchDataProcessor(
    private val dataLogger: PotchDataLogger? = null,
    private val arousalCalculator: PotchArousalCalculator = PotchArousalCalculator()
) {
    private data class ParsedPacket(
        val raw: ByteArray,
        val sequence: Int,
        val timestamp: Long,
        val batteryRaw: Int,
        val ntcRaw: Int,
        val imuData: ByteArray,
        val ppgData: ByteArray
    )

    private data class PeakResult(
        val estimate: HeartRateEstimate?,
        val polarity: HeartRatePeakPolarity,
        val filtered: List<Double>,
        val peaks: List<Int>,
        val rejected: List<Int>,
        val threshold: Double?,
        val failure: HeartRateProcessingState,
        val message: String,
        val quality: Double
    )

    private val _state = MutableStateFlow(DataProcessorState())
    val state: StateFlow<DataProcessorState> = _state

    private val burstPackets = ArrayList<ParsedPacket>(PACKETS_PER_BURST)
    private val greenPpgBuffer = ArrayDeque<Int>()
    private var expectedSequence: Int? = null
    private var analysisSegmentId = 0L
    private var totalHeartRateSamples = 0L
    private var lastValidHeartRate: HeartRateEstimate? = null
    private var lastValidHeartRateAt: Long? = null

    fun updateMicroMovementBandPass(lowCutHz: Double, highCutHz: Double) {
        arousalCalculator.updateMicroMovementBandPass(lowCutHz, highCutHz)
    }

    fun processIncomingData(data: ByteArray) {
        _state.update { it.copy(totalMiniPackets = it.totalMiniPackets + 1) }

        if (data.size != PACKET_SIZE) {
            registerPacketError("LENGTH", "Length Drop: expected $PACKET_SIZE, got ${data.size}")
            return
        }
        if (data[0] != HEADER_0 || data[1] != HEADER_1) {
            registerPacketError(
                "HEADER",
                "Header Drop: %02X %02X".format(data[0].toInt() and 0xFF, data[1].toInt() and 0xFF)
            )
            return
        }

        val sequence = readUInt16(data, 2)
        val receivedCrc = readUInt16(data, CRC_OFFSET)
        val calculatedCrc = crc16CcittFalse(data, 0, CRC_OFFSET)
        if (receivedCrc != calculatedCrc) {
            registerPacketError(
                "CRC",
                "CRC Drop seq=$sequence: received=%04X calculated=%04X".format(receivedCrc, calculatedCrc),
                sequence,
                crc = true
            )
            breakContinuity("CRC 오류 seq=$sequence")
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
                registerPacketError("SEQUENCE", "Seq Drop: expected=$expected actual=$sequence lost=$lost", sequence)
                breakContinuity("시퀀스 불연속 expected=$expected actual=$sequence")
            }
        }
        expectedSequence = (sequence + 1) and 0xFFFF

        val packet = ParsedPacket(
            raw = data.copyOf(),
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

    fun reset() {
        burstPackets.clear()
        greenPpgBuffer.clear()
        expectedSequence = null
        totalHeartRateSamples = 0L
        lastValidHeartRate = null
        lastValidHeartRateAt = null
        analysisSegmentId += 1L
        arousalCalculator.reset()
        _state.value = DataProcessorState(analysisSegmentId = analysisSegmentId)
    }

    private fun appendToBurst(packet: ParsedPacket) {
        val slot = packet.sequence % PACKETS_PER_BURST
        if (slot == 0) {
            burstPackets.clear()
        } else if (burstPackets.isEmpty()) {
            _state.update { it.copy(lastLog = "Burst boundary 대기 중: seq=${packet.sequence}") }
            return
        }

        val expectedSlot = burstPackets.size
        if (slot != expectedSlot) {
            breakContinuity("Burst slot 불일치 expected=$expectedSlot actual=$slot")
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
            ntcRaw = packets.asReversed().firstOrNull { it.ntcRaw != 0 }?.ntcRaw ?: 0,
            batteryRaw = packets.asReversed().firstOrNull { it.batteryRaw != 0 }?.batteryRaw ?: 0,
            ppgData = ppgBytes,
            imuData = imuBytes
        )

        val greenSamples = decodeGreenPpg(ppgBytes)
        greenSamples.forEach { sample ->
            greenPpgBuffer.add(sample)
            totalHeartRateSamples += 1L
        }
        while (greenPpgBuffer.size > MAX_HR_BUFFER_SAMPLES) greenPpgBuffer.removeFirst()

        val now = System.currentTimeMillis()
        val motion = calculateImuMotion(imuBytes)
        val peakResult = estimateHeartRate(greenPpgBuffer.toList(), motion)
        val freshEstimate = peakResult.estimate
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
            else -> peakResult.failure
        }
        val status = MetricCalculationStatus(
            state = when (processingState) {
                HeartRateProcessingState.VALID -> MetricCalculationState.VALID
                HeartRateProcessingState.COLLECTING -> MetricCalculationState.COLLECTING
                else -> MetricCalculationState.REJECTED
            },
            message = if (processingState == HeartRateProcessingState.HELD_PREVIOUS) {
                "새 심박 계산 실패 · 이전 정상값 유지"
            } else peakResult.message
        )

        val diagnostics = HeartRateDiagnostics(
            processingState = processingState,
            underlyingFailureReason = if (processingState == HeartRateProcessingState.HELD_PREVIOUS) peakResult.failure else null,
            message = status.message,
            analysisSegmentId = analysisSegmentId,
            windowSampleCount = greenPpgBuffer.size,
            windowSeconds = greenPpgBuffer.size / PPG_SAMPLE_RATE_HZ,
            greenDcMean = greenPpgBuffer.takeIf { it.isNotEmpty() }?.average(),
            greenMin = greenPpgBuffer.minOrNull()?.toDouble(),
            greenMax = greenPpgBuffer.maxOrNull()?.toDouble(),
            acRobustAmplitude = robustAmplitude(greenPpgBuffer.map(Int::toDouble)),
            selectedPeakThreshold = peakResult.threshold,
            selectedPolarity = peakResult.polarity,
            detectedPeakSamplePositions = peakResult.peaks.map(Int::toDouble),
            acceptedIbiEndSamplePositions = freshEstimate?.ibiIntervals?.map { it.endSamplePosition }.orEmpty(),
            rejectedIbiEndSamplePositions = peakResult.rejected.map(Int::toDouble),
            referencePeakSamplePosition = peakResult.peaks.firstOrNull()?.toDouble(),
            detectedPeakCount = peakResult.peaks.size,
            rawIbiCount = (peakResult.peaks.size - 1).coerceAtLeast(0),
            validIbiCount = freshEstimate?.ibiIntervals?.size ?: 0,
            acceptedIntervalRatio = freshEstimate?.acceptedIntervalRatio,
            rawSdsdMs = freshEstimate?.ibiIntervals?.map { it.intervalSec }?.let(::sdsd)?.times(1000.0),
            sdsdMs = freshEstimate?.ibiIntervals?.map { it.intervalSec }?.let(::sdsd)?.times(1000.0),
            qualityScore = freshEstimate?.qualityScore ?: displayed?.qualityScore,
            calculatedBpm = freshEstimate?.bpm,
            displayedBpm = displayed?.bpm,
            heartRateFresh = freshEstimate != null,
            heartRateAgeMillis = heldAge,
            source = if (displayed != null) HeartRateSource.GREEN else HeartRateSource.NONE,
            sourceLog = if (displayed != null) "Green PPG 단일 채널" else null,
            imuMaxDeltaG = motion.maxDeltaG,
            imuP95DeltaG = motion.p95DeltaG,
            imuMotionExceedanceRatio = motion.exceedanceRatio,
            retainedBufferSampleCount = greenPpgBuffer.size,
            cleanSegmentSampleCount = greenPpgBuffer.size,
            maxRawSampleDelta = maxRawDelta(greenPpgBuffer.toList()),
            crcErrorCount = _state.value.crcErrorCount,
            sequenceLossCount = _state.value.missingSequenceErrors,
            estimatedLostPacketCount = _state.value.estimatedLostPacketCount
        )

        val graphData = HeartRateGraphData(
            source = if (displayed != null) HeartRateSource.GREEN else HeartRateSource.NONE,
            processingState = processingState,
            selectedPolarity = peakResult.polarity,
            samples = peakResult.filtered,
            peakSampleIndices = freshEstimate?.ibiIntervals?.map { it.endSampleIndex.toInt() }.orEmpty(),
            detectedPeakSampleIndices = peakResult.peaks,
            acceptedPeakSampleIndices = freshEstimate?.ibiIntervals?.map { it.endSampleIndex.toInt() }.orEmpty(),
            rejectedPeakSampleIndices = peakResult.rejected,
            referencePeakSampleIndex = peakResult.peaks.firstOrNull(),
            retainedBufferSampleCount = greenPpgBuffer.size,
            cleanSegmentSampleCount = greenPpgBuffer.size,
            calculatedBpm = freshEstimate?.bpm,
            qualityScore = freshEstimate?.qualityScore ?: displayed?.qualityScore,
            description = status.message
        )

        val arousalState = arousalCalculator.processBurst(sensorData, freshEstimate, status)
        val greenMax = greenSamples.maxOrNull()?.toDouble() ?: 0.0

        dataLogger?.logSuperFrame(
            phoneTimeMillis = now,
            timestamp = sensorData.timestamp,
            sequenceStart = sensorData.sequenceStart,
            sequenceEnd = sensorData.sequenceEnd,
            packetCount = sensorData.packetCount,
            burstHex = packets.joinToString("") { it.raw.toHex() },
            complete = "true",
            missPacketNum = _state.value.estimatedLostPacketCount.toString(),
            errorLog = ""
        )
        dataLogger?.logHeartRateDiagnostics(now, sensorData.timestamp, diagnostics)
        dataLogger?.logArousalState(
            phoneTimeMillis = now,
            timestamp = sensorData.timestamp,
            arousalState = arousalState,
            complete = "true",
            missPacketNum = _state.value.estimatedLostPacketCount.toString(),
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
                parsedSuperFrameCount = current.parsedSuperFrameCount + 1,
                analysisSegmentId = analysisSegmentId,
                lastGreenMax = greenMax,
                arousalState = arousalState,
                lastLog = "Burst ${sensorData.sequenceStart}-${sensorData.sequenceEnd} 처리 완료"
            )
        }
    }

    private fun estimateHeartRate(samples: List<Int>, motion: ImuMotion): PeakResult {
        if (samples.size < MIN_HR_SAMPLES) {
            return PeakResult(
                null,
                HeartRatePeakPolarity.NONE,
                emptyList(),
                emptyList(),
                emptyList(),
                null,
                HeartRateProcessingState.COLLECTING,
                "Green PPG 심박 데이터 수집 중 (${samples.size}/$MIN_HR_SAMPLES)",
                0.0
            )
        }

        val window = samples.takeLast(HR_WINDOW_SAMPLES).map(Int::toDouble)
        val maxRaw = window.maxOrNull() ?: 0.0
        val minRaw = window.minOrNull() ?: 0.0
        if (maxRaw <= CONTACT_MIN_VALUE) {
            return PeakResult(null, HeartRatePeakPolarity.NONE, emptyList(), emptyList(), emptyList(), null,
                HeartRateProcessingState.NO_CONTACT, "Green PPG 접촉 신호 없음", 0.0)
        }
        if (maxRaw >= SATURATION_VALUE && minRaw >= SATURATION_VALUE * 0.95) {
            return PeakResult(null, HeartRatePeakPolarity.NONE, emptyList(), emptyList(), emptyList(), null,
                HeartRateProcessingState.SIGNAL_SATURATED, "Green PPG 신호 포화", 0.0)
        }

        val mean = window.average()
        val centered = window.map { it - mean }
        val filtered = centeredMovingAverage(centered, 17)
        val robust = robustAmplitude(filtered)
        if (!robust.isFinite() || robust < MIN_AC_AMPLITUDE) {
            return PeakResult(null, HeartRatePeakPolarity.NONE, filtered, emptyList(), emptyList(), null,
                HeartRateProcessingState.SIGNAL_TOO_WEAK, "Green PPG AC 진폭 부족", 0.0)
        }

        val positive = analyzePolarity(filtered, false)
        val negative = analyzePolarity(filtered, true)
        val selected = listOf(positive, negative).maxByOrNull { it.quality } ?: positive

        if (motion.isStrong && selected.estimate == null) {
            return selected.copy(
                failure = HeartRateProcessingState.MOTION_ARTIFACT,
                message = "움직임으로 Green PPG 심박 계산 보류"
            )
        }
        return selected
    }

    private fun analyzePolarity(filtered: List<Double>, invert: Boolean): PeakResult {
        val signal = if (invert) filtered.map { -it } else filtered
        val maxValue = signal.maxOrNull() ?: 0.0
        val threshold = maxValue * 0.30
        val minDistance = (PPG_SAMPLE_RATE_HZ * 0.35).roundToInt()
        val peaks = mutableListOf<Int>()

        for (i in 1 until signal.lastIndex) {
            if (signal[i] <= signal[i - 1] || signal[i] < signal[i + 1] || signal[i] <= threshold) continue
            if (peaks.isEmpty() || i - peaks.last() >= minDistance) {
                peaks += i
            } else if (signal[i] > signal[peaks.last()]) {
                peaks[peaks.lastIndex] = i
            }
        }

        if (peaks.size < 3) {
            return PeakResult(null, polarity(invert), filtered, peaks, emptyList(), threshold,
                HeartRateProcessingState.INSUFFICIENT_PEAKS, "Green PPG peak 부족", peaks.size / 10.0)
        }

        val rawIntervals = peaks.zipWithNext { _, end -> end }.mapIndexed { index, end ->
            val start = peaks[index]
            IbiInterval(
                intervalSec = (end - start) / PPG_SAMPLE_RATE_HZ,
                endSampleIndex = end.toLong(),
                segmentId = analysisSegmentId,
                endSamplePosition = end.toDouble()
            )
        }
        val physiological = rawIntervals.filter { it.intervalSec in 0.333..1.5 }
        if (physiological.size < 2) {
            return PeakResult(null, polarity(invert), filtered, peaks, peaks.drop(1), threshold,
                HeartRateProcessingState.INVALID_IBI, "생리 범위의 IBI 부족", 0.0)
        }
        val median = physiological.map { it.intervalSec }.sorted()[physiological.size / 2]
        val accepted = physiological.filter { abs(it.intervalSec - median) / median < 0.40 }
        val rejected = rawIntervals.filterNot { candidate -> accepted.any { it.endSampleIndex == candidate.endSampleIndex } }
        if (accepted.size < 2) {
            return PeakResult(null, polarity(invert), filtered, peaks, rejected.map { it.endSampleIndex.toInt() }, threshold,
                HeartRateProcessingState.INVALID_IBI, "IBI 이상치 제거 후 데이터 부족", 0.0)
        }

        val average = accepted.map { it.intervalSec }.average()
        val bpm = (60.0 / average).roundToInt()
        if (bpm !in MIN_BPM..MAX_BPM) {
            return PeakResult(null, polarity(invert), filtered, peaks, rejected.map { it.endSampleIndex.toInt() }, threshold,
                HeartRateProcessingState.BPM_OUT_OF_RANGE, "심박 범위 초과: $bpm bpm", 0.0)
        }

        val acceptedRatio = accepted.size.toDouble() / rawIntervals.size
        val cv = stdDev(accepted.map { it.intervalSec }) / average
        val quality = ((1.0 - cv).coerceIn(0.0, 1.0) * 0.7 + acceptedRatio * 0.3)
        val estimate = HeartRateEstimate(
            bpm = bpm,
            ibiIntervals = accepted,
            peakCount = peaks.size,
            intervalCount = accepted.size,
            averageIntervalSec = average,
            qualityScore = quality,
            selectedPeakThreshold = threshold,
            selectedPolarity = polarity(invert),
            rawIntervalCount = rawIntervals.size,
            acceptedIntervalRatio = acceptedRatio,
            detectedPeakSamplePositions = peaks.map(Int::toDouble),
            rejectedIbiEndSamplePositions = rejected.map { it.endSamplePosition },
            referencePeakSamplePosition = peaks.firstOrNull()?.toDouble()
        )
        return PeakResult(
            estimate = estimate,
            polarity = polarity(invert),
            filtered = filtered,
            peaks = peaks,
            rejected = rejected.map { it.endSampleIndex.toInt() },
            threshold = threshold,
            failure = HeartRateProcessingState.VALID,
            message = "Green PPG 심박 $bpm bpm",
            quality = quality
        )
    }

    private fun breakContinuity(reason: String) {
        burstPackets.clear()
        analysisSegmentId += 1L
        arousalCalculator.reset()
        _state.update {
            it.copy(
                continuityBreakCount = it.continuityBreakCount + 1,
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
                crcErrorCount = current.crcErrorCount + if (crc) 1 else 0,
                damagedPacketCount = current.damagedPacketCount + 1,
                recentPacketErrors = (current.recentPacketErrors + PacketErrorLog(type, message, sequence)).takeLast(20),
                lastLog = message
            )
        }
    }

    private data class ImuMotion(
        val maxDeltaG: Double,
        val p95DeltaG: Double,
        val exceedanceRatio: Double,
        val isStrong: Boolean
    )

    private fun calculateImuMotion(data: ByteArray): ImuMotion {
        val magnitudes = mutableListOf<Double>()
        var offset = 0
        while (offset + 11 < data.size) {
            val x = readInt16(data, offset) / ACCEL_LSB_PER_G
            val y = readInt16(data, offset + 2) / ACCEL_LSB_PER_G
            val z = readInt16(data, offset + 4) / ACCEL_LSB_PER_G
            magnitudes += sqrt(x * x + y * y + z * z)
            offset += 12
        }
        val deltas = magnitudes.zipWithNext { a, b -> abs(b - a) }
        if (deltas.isEmpty()) return ImuMotion(0.0, 0.0, 0.0, false)
        val sorted = deltas.sorted()
        val p95 = sorted[((sorted.lastIndex) * 0.95).roundToInt()]
        val exceedance = deltas.count { it >= MOTION_DELTA_G }.toDouble() / deltas.size
        return ImuMotion(
            maxDeltaG = deltas.maxOrNull() ?: 0.0,
            p95DeltaG = p95,
            exceedanceRatio = exceedance,
            isStrong = p95 >= MOTION_DELTA_G || exceedance >= 0.15
        )
    }

    private fun decodeGreenPpg(data: ByteArray): IntArray {
        return IntArray(data.size / 2) { index -> readUInt16(data, index * 2) }
    }

    private fun centeredMovingAverage(values: List<Double>, window: Int): List<Double> {
        val radius = window / 2
        return values.indices.map { index ->
            val start = (index - radius).coerceAtLeast(0)
            val end = (index + radius).coerceAtMost(values.lastIndex)
            var sum = 0.0
            for (i in start..end) sum += values[i]
            sum / (end - start + 1)
        }
    }

    private fun robustAmplitude(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val low = sorted[((sorted.lastIndex) * 0.05).roundToInt()]
        val high = sorted[((sorted.lastIndex) * 0.95).roundToInt()]
        return high - low
    }

    private fun maxRawDelta(values: List<Int>): Double? {
        if (values.size < 2) return null
        return values.zipWithNext { a, b -> abs(b - a).toDouble() }.maxOrNull()
    }

    private fun sdsd(values: List<Double>): Double? {
        if (values.size < 3) return null
        return stdDev(values.zipWithNext { a, b -> b - a })
    }

    private fun stdDev(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }

    private fun polarity(invert: Boolean): HeartRatePeakPolarity =
        if (invert) HeartRatePeakPolarity.NEGATIVE else HeartRatePeakPolarity.POSITIVE

    private fun readUInt16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

    private fun readUInt32(data: ByteArray, offset: Int): Long =
        (data[offset].toLong() and 0xFF) or
                ((data[offset + 1].toLong() and 0xFF) shl 8) or
                ((data[offset + 2].toLong() and 0xFF) shl 16) or
                ((data[offset + 3].toLong() and 0xFF) shl 24)

    private fun readInt16(data: ByteArray, offset: Int): Double =
        readUInt16(data, offset).toShort().toDouble()

    private fun crc16CcittFalse(data: ByteArray, offset: Int, length: Int): Int {
        var crc = 0xFFFF
        for (i in offset until offset + length) {
            crc = crc xor ((data[i].toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if ((crc and 0x8000) != 0) {
                    ((crc shl 1) xor 0x1021) and 0xFFFF
                } else {
                    (crc shl 1) and 0xFFFF
                }
            }
        }
        return crc
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it.toInt() and 0xFF) }

    companion object {
        private const val TAG = "PotchDataProcessor"
        private const val PACKET_SIZE = 142
        private const val PACKETS_PER_BURST = 8
        private const val CRC_OFFSET = 140
        private const val IMU_OFFSET = 12
        private const val PPG_OFFSET = 108
        private const val HEADER_0: Byte = 0xA5.toByte()
        private const val HEADER_1: Byte = 0x5A.toByte()
        private const val PPG_SAMPLE_RATE_HZ = 128.0
        private const val MIN_HR_SAMPLES = 384
        private const val HR_WINDOW_SAMPLES = 640
        private const val MAX_HR_BUFFER_SAMPLES = 1280
        private const val CONTACT_MIN_VALUE = 100.0
        private const val SATURATION_VALUE = 65_000.0
        private const val MIN_AC_AMPLITUDE = 10.0
        private const val MIN_BPM = 40
        private const val MAX_BPM = 180
        private const val HEART_RATE_HOLD_MILLIS = 10_000L
        private const val ACCEL_LSB_PER_G = 8192.0
        private const val MOTION_DELTA_G = 0.50
    }
}
