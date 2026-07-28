package com.leejang.sleeptandard.Potch

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class MetricCalculationState {
    VALID,
    COLLECTING,
    REJECTED
}

data class MetricCalculationStatus(
    val state: MetricCalculationState = MetricCalculationState.COLLECTING,
    val message: String = "데이터 수집 중"
)

enum class RespirationPeakPolarity {
    POSITIVE,
    NEGATIVE,
    NONE
}

enum class RrFusionSource {
    NONE,
    GREEN_PPG,
    IMU,
    WEIGHTED
}

enum class RrvSource {
    NONE,
    GREEN_PPG,
    IMU
}

data class PpgRespirationGraphData(
    val selectedPolarity: RespirationPeakPolarity = RespirationPeakPolarity.NONE,
    val processingState: MetricCalculationState = MetricCalculationState.COLLECTING,
    val samples: List<Double> = emptyList(),
    val sampleRateHz: Double = 8.0,
    val windowSeconds: Double = 0.0,
    val minimumWindowSeconds: Double = 25.0,
    val detectedPeakSampleIndices: List<Int> = emptyList(),
    val acceptedPeakSampleIndices: List<Int> = emptyList(),
    val rejectedPeakSampleIndices: List<Int> = emptyList(),
    val calculatedRrBpm: Double? = null,
    val qualityScore: Double? = null,
    val description: String = "Green PPG 호흡 신호 수집 중"
)

data class ArousalState(
    val microMovementVariance: Double? = null,
    val microMovementScore: Double? = null,

    val rrFromPpg: Double? = null,
    val rrFromImu: Double? = null,
    val rrFinal: Double? = null,
    val rrScore: Double? = null,
    val rrRawScore: Double? = null,
    val rrFusionSource: RrFusionSource = RrFusionSource.NONE,
    val rrFusionConfidence: Double = 0.0,
    val rrFusionLog: String? = null,
    val rrCalculationStatus: MetricCalculationStatus = MetricCalculationStatus(),

    val rrAnalysisSegmentId: Long = 0L,
    val ppgRespPeakSamplePositions: List<Long> = emptyList(),
    val ppgRespIntervalsSec: List<Double> = emptyList(),
    val imuRespPeakSamplePositions: List<Long> = emptyList(),
    val imuRespIntervalsSec: List<Double> = emptyList(),
    val ppgRespirationGraphData: PpgRespirationGraphData = PpgRespirationGraphData(),

    val rrvRmssd: Double? = null,
    val rrvRmssdMs: Double? = null,
    val rrvScore: Double? = null,
    val rrvSource: RrvSource = RrvSource.NONE,
    val rrvQuality: Double = 0.0,
    val rrvFromPpgRmssdSec: Double? = null,
    val rrvFromImuRmssdSec: Double? = null,
    val rrvPpgIntervalCount: Int = 0,
    val rrvImuIntervalCount: Int = 0,
    val rrvPpgQuality: Double = 0.0,
    val rrvImuQuality: Double = 0.0,
    val rrvCalculationStatus: MetricCalculationStatus = MetricCalculationStatus(),

    val hrBpm: Int? = null,
    val hrGradient: Double? = null,
    val hrScore: Double? = null,
    val hrCalculationStatus: MetricCalculationStatus = MetricCalculationStatus(),

    val hrvRmssd: Double? = null,
    val hrvRmssdMs: Double? = null,
    val hrvLf: Double? = null,
    val hrvHf: Double? = null,
    val hrvLfHf: Double? = null,
    val hrvScore: Double? = null,
    val hrvQuality: Double = 0.0,
    val hrvLog: String? = null,
    val hrvCalculationStatus: MetricCalculationStatus = MetricCalculationStatus(),

    val skinTemperatureCelsius: Double? = null,
    val skinTemperatureGradient: Double? = null,
    val skinTemperatureScore: Double? = null,

    val finalWakeScore: Double = 0.0,
    val isWakeTimingCandidate: Boolean = false,
    val lastLog: String = "No arousal data yet"
)

data class ArousalConfig(
    val ppgSampleRateHz: Double = 128.0,
    val imuSampleRateHz: Double = 64.0,
    var microLowCutHz: Double = 0.5,
    var microHighCutHz: Double = 5.0,
    val microWindowSeconds: Int = 5,
    val microMinWindowSeconds: Int = 3,
    val ppgRespWindowSeconds: Int = 45,
    val ppgRespMinWindowSeconds: Int = 25,
    val imuRespWindowSeconds: Int = 45,
    val imuRespMinWindowSeconds: Int = 25,
    val rrMinBpm: Double = 6.0,
    val rrMaxBpm: Double = 30.0,
    val wakeCandidateScore: Double = 60.0
)

enum class MovementLevel {
    STILL,
    MICRO,
    MACRO
}

data class MicroMovementResult(
    val rmsG: Double,
    val varianceG: Double,
    val score: Double,
    val level: MovementLevel
)

private data class RespirationResult(
    val bpm: Double,
    val intervalsSec: List<Double>,
    val peakIndices: List<Int>,
    val polarity: RespirationPeakPolarity,
    val quality: Double,
    val filtered: List<Double>,
    val sampleRateHz: Double
)

/**
 * Potch510 Green PPG 및 6축 IMU를 이용해 각성 지표를 계산한다.
 *
 * PPG는 단일 Green 채널만 사용하며, 센서 채널 융합 대신 Green PPG와 IMU에서
 * 독립적으로 추정한 호흡수를 품질에 따라 결합한다.
 */
class PotchArousalCalculator(
    private val config: ArousalConfig = ArousalConfig()
) {
    private val greenPpgBuffer = ArrayDeque<Double>()
    private val imuMagnitudeBuffer = ArrayDeque<Double>()
    private val temperatureBuffer = ArrayDeque<Pair<Long, Double>>()
    private val heartRateHistory = ArrayDeque<Pair<Long, Int>>()
    private var latestHeartRateEstimate: HeartRateEstimate? = null
    private var lastState = ArousalState()
    private var analysisSegmentId = 0L

    fun updateMicroMovementBandPass(lowCutHz: Double, highCutHz: Double) {
        require(lowCutHz.isFinite() && highCutHz.isFinite())
        require(lowCutHz >= 0.0 && highCutHz > lowCutHz)
        config.microLowCutHz = lowCutHz
        config.microHighCutHz = highCutHz
    }

    fun processBurst(
        sensorData: SensorData,
        heartRateEstimate: HeartRateEstimate?,
        heartRateStatus: MetricCalculationStatus
    ): ArousalState {
        val now = System.currentTimeMillis()
        appendGreenPpg(sensorData.ppgData)
        appendImu(sensorData.imuData)
        appendTemperature(now, sensorData.ntcCelsius)

        if (heartRateEstimate != null) {
            latestHeartRateEstimate = heartRateEstimate
            heartRateHistory.add(now to heartRateEstimate.bpm)
        }
        trimTimedHistory(now)

        val movement = calculateMicroMovement()
        val ppgRespiration = calculatePpgRespiration()
        val imuRespiration = calculateImuRespiration()
        val respirationFusion = fuseRespiration(ppgRespiration, imuRespiration)

        val ppgRrv = calculateRmssd(ppgRespiration?.intervalsSec.orEmpty())
        val imuRrv = calculateRmssd(imuRespiration?.intervalsSec.orEmpty())
        val selectedRrv = when {
            ppgRrv != null && (imuRrv == null || (ppgRespiration?.quality ?: 0.0) >= (imuRespiration?.quality ?: 0.0)) ->
                Triple(ppgRrv, RrvSource.GREEN_PPG, ppgRespiration?.quality ?: 0.0)
            imuRrv != null -> Triple(imuRrv, RrvSource.IMU, imuRespiration?.quality ?: 0.0)
            else -> null
        }

        val hrGradient = calculateHeartRateGradient()
        val hrScore = hrGradient?.let { scoreRise(it, 0.0, 12.0) }
        val hrvRmssd = latestHeartRateEstimate?.ibiIntervals
            ?.map { it.intervalSec }
            ?.let(::calculateRmssd)
        val hrvQuality = latestHeartRateEstimate?.qualityScore ?: 0.0
        val hrvScore = hrvRmssd?.let { value ->
            // 잠에서 깰수록 일반적으로 beat-to-beat 변동성이 감소하는 방향을 단순 점수화한다.
            ((0.12 - value) / 0.10 * 100.0).coerceIn(0.0, 100.0)
        }

        val temperature = sensorData.ntcCelsius.takeIf { it.isFinite() }
        val temperatureGradient = calculateTemperatureGradient()
        val temperatureScore = temperatureGradient?.let { scoreRise(it, 0.0, 0.8) }

        val rrRawScore = respirationFusion.first?.let { bpm ->
            scoreRise(bpm, 10.0, 24.0)
        }
        val rrScore = rrRawScore?.times(respirationFusion.third)
        val rrvScore = selectedRrv?.first?.let { value ->
            ((0.9 - value) / 0.8 * 100.0).coerceIn(0.0, 100.0)
        }

        val weightedMetrics = buildList {
            movement?.score?.let { add(it to 0.30) }
            rrScore?.let { add(it to 0.20) }
            rrvScore?.let { add(it to 0.10) }
            hrScore?.let { add(it to 0.20) }
            hrvScore?.let { add(it to 0.15) }
            temperatureScore?.let { add(it to 0.05) }
        }
        val finalScore = weightedAverage(weightedMetrics)

        val ppgGraph = buildPpgRespirationGraph(ppgRespiration)
        val rrStatus = when {
            respirationFusion.first != null -> MetricCalculationStatus(
                MetricCalculationState.VALID,
                "호흡수 ${"%.1f".format(respirationFusion.first)} bpm"
            )
            greenPpgBuffer.size < (config.ppgRespMinWindowSeconds * config.ppgSampleRateHz).roundToInt() ->
                MetricCalculationStatus(MetricCalculationState.COLLECTING, "호흡 분석 데이터 수집 중")
            else -> MetricCalculationStatus(MetricCalculationState.REJECTED, "유효한 호흡 peak를 찾지 못함")
        }

        val rrvStatus = when {
            selectedRrv != null -> MetricCalculationStatus(MetricCalculationState.VALID, "RRV 계산 완료")
            respirationFusion.first == null -> MetricCalculationStatus(MetricCalculationState.COLLECTING, "호흡 interval 수집 중")
            else -> MetricCalculationStatus(MetricCalculationState.REJECTED, "RRV interval 부족")
        }

        val hrvStatus = when {
            hrvRmssd != null -> MetricCalculationStatus(MetricCalculationState.VALID, "HRV 계산 완료")
            latestHeartRateEstimate == null -> MetricCalculationStatus(MetricCalculationState.COLLECTING, "심박 interval 수집 중")
            else -> MetricCalculationStatus(MetricCalculationState.REJECTED, "HRV interval 부족")
        }

        lastState = ArousalState(
            microMovementVariance = movement?.varianceG,
            microMovementScore = movement?.score,
            rrFromPpg = ppgRespiration?.bpm,
            rrFromImu = imuRespiration?.bpm,
            rrFinal = respirationFusion.first,
            rrScore = rrScore,
            rrRawScore = rrRawScore,
            rrFusionSource = respirationFusion.second,
            rrFusionConfidence = respirationFusion.third,
            rrFusionLog = respirationFusion.fourth,
            rrCalculationStatus = rrStatus,
            rrAnalysisSegmentId = analysisSegmentId,
            ppgRespPeakSamplePositions = ppgRespiration?.peakIndices?.map(Int::toLong).orEmpty(),
            ppgRespIntervalsSec = ppgRespiration?.intervalsSec.orEmpty(),
            imuRespPeakSamplePositions = imuRespiration?.peakIndices?.map(Int::toLong).orEmpty(),
            imuRespIntervalsSec = imuRespiration?.intervalsSec.orEmpty(),
            ppgRespirationGraphData = ppgGraph,
            rrvRmssd = selectedRrv?.first,
            rrvRmssdMs = selectedRrv?.first?.times(1000.0),
            rrvScore = rrvScore,
            rrvSource = selectedRrv?.second ?: RrvSource.NONE,
            rrvQuality = selectedRrv?.third ?: 0.0,
            rrvFromPpgRmssdSec = ppgRrv,
            rrvFromImuRmssdSec = imuRrv,
            rrvPpgIntervalCount = ppgRespiration?.intervalsSec?.size ?: 0,
            rrvImuIntervalCount = imuRespiration?.intervalsSec?.size ?: 0,
            rrvPpgQuality = ppgRespiration?.quality ?: 0.0,
            rrvImuQuality = imuRespiration?.quality ?: 0.0,
            rrvCalculationStatus = rrvStatus,
            hrBpm = latestHeartRateEstimate?.bpm,
            hrGradient = hrGradient,
            hrScore = hrScore,
            hrCalculationStatus = heartRateStatus,
            hrvRmssd = hrvRmssd,
            hrvRmssdMs = hrvRmssd?.times(1000.0),
            hrvScore = hrvScore,
            hrvQuality = hrvQuality,
            hrvLog = latestHeartRateEstimate?.let { "Green PPG IBI ${it.ibiIntervals.size}개" },
            hrvCalculationStatus = hrvStatus,
            skinTemperatureCelsius = temperature,
            skinTemperatureGradient = temperatureGradient,
            skinTemperatureScore = temperatureScore,
            finalWakeScore = finalScore,
            isWakeTimingCandidate = finalScore >= config.wakeCandidateScore,
            lastLog = "Green PPG/IMU arousal score=${"%.1f".format(finalScore)}"
        )
        return lastState
    }

    fun calculateMicroMovement(): MicroMovementResult? {
        val required = (config.imuSampleRateHz * config.microMinWindowSeconds).roundToInt()
        if (imuMagnitudeBuffer.size < required) return null

        val count = minOf(
            imuMagnitudeBuffer.size,
            (config.imuSampleRateHz * config.microWindowSeconds).roundToInt()
        )
        val values = imuMagnitudeBuffer.toList().takeLast(count)
        if (values.size < 2) return null

        val deltas = values.zipWithNext { a, b -> b - a }
        val mean = deltas.average()
        val variance = deltas.sumOf { (it - mean) * (it - mean) } / deltas.size
        val rms = sqrt(deltas.sumOf { it * it } / deltas.size)
        val score = ((rms - 0.002) / (0.030 - 0.002) * 100.0).coerceIn(0.0, 100.0)
        val level = when {
            rms >= 0.030 -> MovementLevel.MACRO
            rms >= 0.006 -> MovementLevel.MICRO
            else -> MovementLevel.STILL
        }
        return MicroMovementResult(rms, variance, score, level)
    }

    fun reset() {
        greenPpgBuffer.clear()
        imuMagnitudeBuffer.clear()
        temperatureBuffer.clear()
        heartRateHistory.clear()
        latestHeartRateEstimate = null
        analysisSegmentId += 1L
        lastState = ArousalState(rrAnalysisSegmentId = analysisSegmentId)
    }

    fun currentState(): ArousalState = lastState

    private fun appendGreenPpg(data: ByteArray) {
        var offset = 0
        while (offset + 1 < data.size) {
            val sample = (data[offset].toInt() and 0xFF) or
                    ((data[offset + 1].toInt() and 0xFF) shl 8)
            greenPpgBuffer.add(sample.toDouble())
            offset += 2
        }
        val max = (config.ppgSampleRateHz * config.ppgRespWindowSeconds).roundToInt()
        while (greenPpgBuffer.size > max) greenPpgBuffer.removeFirst()
    }

    private fun appendImu(data: ByteArray) {
        var offset = 0
        while (offset + 11 < data.size) {
            val x = readInt16(data, offset).toDouble() / ACCEL_LSB_PER_G
            val y = readInt16(data, offset + 2).toDouble() / ACCEL_LSB_PER_G
            val z = readInt16(data, offset + 4).toDouble() / ACCEL_LSB_PER_G
            imuMagnitudeBuffer.add(sqrt(x * x + y * y + z * z))
            offset += 12
        }
        val max = (config.imuSampleRateHz * config.imuRespWindowSeconds).roundToInt()
        while (imuMagnitudeBuffer.size > max) imuMagnitudeBuffer.removeFirst()
    }

    private fun appendTemperature(now: Long, value: Double) {
        if (!value.isFinite()) return
        temperatureBuffer.add(now to value)
        while (temperatureBuffer.size > 600) temperatureBuffer.removeFirst()
    }

    private fun calculatePpgRespiration(): RespirationResult? {
        val minimum = (config.ppgSampleRateHz * config.ppgRespMinWindowSeconds).roundToInt()
        if (greenPpgBuffer.size < minimum) return null
        val downsampled = blockAverage(greenPpgBuffer.toList(), 16)
        return detectRespiration(downsampled, 8.0)
    }

    private fun calculateImuRespiration(): RespirationResult? {
        val minimum = (config.imuSampleRateHz * config.imuRespMinWindowSeconds).roundToInt()
        if (imuMagnitudeBuffer.size < minimum) return null
        val downsampled = blockAverage(imuMagnitudeBuffer.toList(), 8)
        return detectRespiration(downsampled, 8.0)
    }

    private fun detectRespiration(values: List<Double>, sampleRate: Double): RespirationResult? {
        if (values.size < (sampleRate * 20.0).roundToInt()) return null
        val baseline = centeredMovingAverage(values, (sampleRate * 4.0).roundToInt().coerceAtLeast(3))
        val detrended = values.indices.map { values[it] - baseline[it] }
        val filtered = centeredMovingAverage(detrended, 3)

        val positive = detectRespirationPeaks(filtered, sampleRate, false)
        val negative = detectRespirationPeaks(filtered, sampleRate, true)
        return listOfNotNull(positive, negative).maxByOrNull { it.quality }
    }

    private fun detectRespirationPeaks(
        source: List<Double>,
        sampleRate: Double,
        invert: Boolean
    ): RespirationResult? {
        val signal = if (invert) source.map { -it } else source
        val amplitude = percentile(signal, 0.95) - percentile(signal, 0.05)
        if (!amplitude.isFinite() || amplitude <= 1e-9) return null
        val threshold = percentile(signal, 0.65)
        val minDistance = (sampleRate * 2.0).roundToInt()
        val peaks = mutableListOf<Int>()

        for (i in 1 until signal.lastIndex) {
            if (signal[i] <= signal[i - 1] || signal[i] < signal[i + 1] || signal[i] < threshold) continue
            if (peaks.isEmpty() || i - peaks.last() >= minDistance) {
                peaks += i
            } else if (signal[i] > signal[peaks.last()]) {
                peaks[peaks.lastIndex] = i
            }
        }
        if (peaks.size < 3) return null

        val rawIntervals = peaks.zipWithNext { a, b -> (b - a) / sampleRate }
            .filter { it in 2.0..10.0 }
        if (rawIntervals.size < 2) return null
        val median = rawIntervals.sorted()[rawIntervals.size / 2]
        val intervals = rawIntervals.filter { abs(it - median) / median <= 0.40 }
        if (intervals.size < 2) return null
        val average = intervals.average()
        val bpm = 60.0 / average
        if (bpm !in config.rrMinBpm..config.rrMaxBpm) return null

        val cv = stdDev(intervals) / average
        val acceptedRatio = intervals.size.toDouble() / rawIntervals.size
        val quality = ((1.0 - cv).coerceIn(0.0, 1.0) * 0.7 + acceptedRatio * 0.3)

        return RespirationResult(
            bpm = bpm,
            intervalsSec = intervals,
            peakIndices = peaks,
            polarity = if (invert) RespirationPeakPolarity.NEGATIVE else RespirationPeakPolarity.POSITIVE,
            quality = quality,
            filtered = if (invert) source.map { -it } else source,
            sampleRateHz = sampleRate
        )
    }

    private fun fuseRespiration(
        ppg: RespirationResult?,
        imu: RespirationResult?
    ): Quadruple<Double?, RrFusionSource, Double, String> {
        if (ppg == null && imu == null) {
            return Quadruple(null, RrFusionSource.NONE, 0.0, "유효한 호흡 후보 없음")
        }
        if (ppg != null && imu == null) {
            return Quadruple(ppg.bpm, RrFusionSource.GREEN_PPG, ppg.quality, "Green PPG 호흡수 사용")
        }
        if (ppg == null && imu != null) {
            return Quadruple(imu.bpm, RrFusionSource.IMU, imu.quality, "IMU 호흡수 사용")
        }
        val p = requireNotNull(ppg)
        val i = requireNotNull(imu)
        val difference = abs(p.bpm - i.bpm)
        if (difference <= 3.0) {
            val pWeight = p.quality.coerceAtLeast(0.05)
            val iWeight = i.quality.coerceAtLeast(0.05)
            val value = (p.bpm * pWeight + i.bpm * iWeight) / (pWeight + iWeight)
            return Quadruple(
                value,
                RrFusionSource.WEIGHTED,
                ((p.quality + i.quality) / 2.0).coerceIn(0.0, 1.0),
                "Green PPG와 IMU 호흡수가 일치해 품질 가중 결합"
            )
        }
        return if (p.quality >= i.quality) {
            Quadruple(p.bpm, RrFusionSource.GREEN_PPG, p.quality * 0.8, "센서 불일치로 Green PPG 우선")
        } else {
            Quadruple(i.bpm, RrFusionSource.IMU, i.quality * 0.8, "센서 불일치로 IMU 우선")
        }
    }

    private fun buildPpgRespirationGraph(result: RespirationResult?): PpgRespirationGraphData {
        if (result == null) {
            val seconds = greenPpgBuffer.size / config.ppgSampleRateHz
            return PpgRespirationGraphData(
                windowSeconds = seconds,
                minimumWindowSeconds = config.ppgRespMinWindowSeconds.toDouble(),
                description = "Green PPG 호흡 분석 데이터 수집 중"
            )
        }
        return PpgRespirationGraphData(
            selectedPolarity = result.polarity,
            processingState = MetricCalculationState.VALID,
            samples = result.filtered,
            sampleRateHz = result.sampleRateHz,
            windowSeconds = result.filtered.size / result.sampleRateHz,
            minimumWindowSeconds = config.ppgRespMinWindowSeconds.toDouble(),
            detectedPeakSampleIndices = result.peakIndices,
            acceptedPeakSampleIndices = result.peakIndices,
            calculatedRrBpm = result.bpm,
            qualityScore = result.quality,
            description = "Green PPG 호흡 ${"%.1f".format(result.bpm)} bpm"
        )
    }

    private fun calculateHeartRateGradient(): Double? {
        if (heartRateHistory.size < 2) return null
        val first = heartRateHistory.first()
        val last = heartRateHistory.last()
        val minutes = (last.first - first.first) / 60_000.0
        if (minutes <= 0.0) return null
        return (last.second - first.second) / minutes
    }

    private fun calculateTemperatureGradient(): Double? {
        if (temperatureBuffer.size < 2) return null
        val first = temperatureBuffer.first()
        val last = temperatureBuffer.last()
        val minutes = (last.first - first.first) / 60_000.0
        if (minutes <= 0.0) return null
        return (last.second - first.second) / minutes
    }

    private fun trimTimedHistory(now: Long) {
        while (heartRateHistory.isNotEmpty() && now - heartRateHistory.first().first > 5 * 60_000L) {
            heartRateHistory.removeFirst()
        }
        while (temperatureBuffer.isNotEmpty() && now - temperatureBuffer.first().first > 10 * 60_000L) {
            temperatureBuffer.removeFirst()
        }
    }

    private fun calculateRmssd(intervals: List<Double>): Double? {
        if (intervals.size < 3) return null
        val squared = intervals.zipWithNext { a, b ->
            val diff = b - a
            diff * diff
        }
        return sqrt(squared.average())
    }

    private fun scoreRise(value: Double, low: Double, high: Double): Double {
        if (!value.isFinite() || high <= low) return 0.0
        return ((value - low) / (high - low) * 100.0).coerceIn(0.0, 100.0)
    }

    private fun weightedAverage(values: List<Pair<Double, Double>>): Double {
        if (values.isEmpty()) return 0.0
        val weight = values.sumOf { it.second }
        if (weight <= 0.0) return 0.0
        return values.sumOf { it.first * it.second } / weight
    }

    private fun blockAverage(values: List<Double>, block: Int): List<Double> {
        if (block <= 1) return values
        val result = ArrayList<Double>(values.size / block)
        var index = 0
        while (index + block <= values.size) {
            var sum = 0.0
            for (i in index until index + block) sum += values[i]
            result += sum / block
            index += block
        }
        return result
    }

    private fun centeredMovingAverage(values: List<Double>, window: Int): List<Double> {
        if (values.isEmpty()) return emptyList()
        val radius = (window.coerceAtLeast(1) / 2)
        return values.indices.map { index ->
            val start = (index - radius).coerceAtLeast(0)
            val end = (index + radius).coerceAtMost(values.lastIndex)
            var sum = 0.0
            for (i in start..end) sum += values[i]
            sum / (end - start + 1)
        }
    }

    private fun percentile(values: List<Double>, ratio: Double): Double {
        if (values.isEmpty()) return Double.NaN
        val sorted = values.sorted()
        val index = ((sorted.lastIndex) * ratio.coerceIn(0.0, 1.0)).roundToInt()
        return sorted[index]
    }

    private fun stdDev(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }

    private fun readInt16(data: ByteArray, offset: Int): Short {
        val raw = (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8)
        return raw.toShort()
    }

    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )

    companion object {
        private const val ACCEL_LSB_PER_G = 8192.0
    }
}
