package com.leejang.sleeptandard.Potch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class PpgLowerEnvelopeExtractorTest {
    @Test
    fun rollingUpperDuplicatesPreferHigherRawPeakAndYieldOneTroughPerBeat() {
        val raw = MutableList(320) { 1_000.0 }
        raw[0] = 2_000.0
        raw[12] = 2_500.0
        raw[128] = 2_100.0
        raw[140] = 2_600.0
        raw[268] = 2_400.0
        raw[70] = 100.0
        raw[80] = 50.0
        raw[200] = 40.0

        val intervals = listOf(
            ibi(start = 0.0, end = 128.0),
            ibi(start = 12.0, end = 140.0),
            ibi(start = 140.0, end = 268.0)
        )

        val result = PpgLowerEnvelopeExtractor.extract(
            rawSamples = raw,
            windowStartSamplePosition = 0L,
            acceptedIntervals = intervals,
            segmentId = SEGMENT_ID,
            sampleRateHz = SAMPLE_RATE_HZ
        )

        assertEquals(listOf(80L, 200L), result.map { it.samplePosition })
        assertEquals(listOf(50.0, 40.0), result.map { it.rawValue })
        assertTrue(result.all { it.segmentId == SEGMENT_ID })
    }

    @Test
    fun nonPhysiologicalHeartIntervalDoesNotSeedEnvelope() {
        val raw = MutableList(400) { 1_000.0 }
        raw[44] = 2_000.0
        raw[300] = 2_000.0

        val result = PpgLowerEnvelopeExtractor.extract(
            rawSamples = raw,
            windowStartSamplePosition = 0L,
            acceptedIntervals = listOf(ibi(start = 44.0, end = 300.0)),
            segmentId = SEGMENT_ID,
            sampleRateHz = SAMPLE_RATE_HZ
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun adjacentIntervalsCannotCreateTwoTroughsInsideRefractoryWindow() {
        val raw = MutableList(160) { 1_000.0 }
        raw[0] = 2_000.0
        raw[64] = 2_000.0
        raw[128] = 2_000.0
        raw[62] = 10.0
        raw[66] = 5.0

        val result = PpgLowerEnvelopeExtractor.extract(
            rawSamples = raw,
            windowStartSamplePosition = 0L,
            acceptedIntervals = listOf(
                ibi(start = 0.0, end = 64.0),
                ibi(start = 64.0, end = 128.0)
            ),
            segmentId = SEGMENT_ID,
            sampleRateHz = SAMPLE_RATE_HZ
        )

        assertEquals(listOf(62L), result.map { it.samplePosition })
    }

    @Test
    fun calculatorUsesLowerEnvelopeAsTheOnlyFinalRespirationSource() {
        val calculator = PotchArousalCalculator()
        var state = ArousalState()

        repeat(40) { second ->
            val snapshotStartSecond = (second - 11).coerceAtLeast(0)
            val lowerPoints = (snapshotStartSecond..second).map { beatSecond ->
                val position = beatSecond * SAMPLE_RATE_HZ.toLong() + 64L
                PpgLowerEnvelopeSample(
                    samplePosition = position,
                    rawValue = 20_000.0 +
                            2_000.0 * sin(2.0 * PI * beatSecond / 5.0),
                    segmentId = 0L
                )
            }
            val estimate = HeartRateEstimate(
                bpm = 60,
                ibiIntervals = emptyList(),
                peakCount = lowerPoints.size,
                intervalCount = 0,
                averageIntervalSec = 1.0,
                qualityScore = 1.0,
                lowerEnvelopeSamples = lowerPoints,
                lowerEnvelopeReplacementStartSamplePosition =
                    lowerPoints.firstOrNull()?.samplePosition
            )

            state = calculator.processBurst(
                sensorData = sensorData(second),
                heartRateEstimate = estimate,
                heartRateStatus = MetricCalculationStatus(
                    state = MetricCalculationState.VALID,
                    message = "synthetic HR"
                ),
                analysisSegmentId = 0L
            )
        }

        assertNotNull(state.rrFinal)
        assertTrue(state.rrFinal!! in 10.0..14.0)
        assertEquals(state.rrFinal, state.rrFromPpg)
        assertNull(state.rrFromImu)
        assertEquals(RrFusionSource.GREEN_PPG_ONLY, state.rrFusionSource)
        assertEquals(4.0, state.ppgRespirationGraphData.sampleRateHz, 0.0)
        assertTrue(state.rrFusionLog.orEmpty().contains("lower-envelope"))
    }

    @Test
    fun rollingRealExtractorSnapshotsAccumulateBeyondTheHeartRateWindow() {
        val calculator = PotchArousalCalculator()
        var state = ArousalState()

        repeat(42) { second ->
            val snapshotStartSecond = (second - 11).coerceAtLeast(0)
            val rawStart = snapshotStartSecond * SAMPLE_RATE_HZ.toLong()
            val rawEndExclusive = (second + 1) * SAMPLE_RATE_HZ.toLong()
            val raw = MutableList((rawEndExclusive - rawStart).toInt()) { 22_000.0 }

            fun setRaw(position: Long, value: Double) {
                val index = position - rawStart
                if (index >= 0L && index < raw.size.toLong()) raw[index.toInt()] = value
            }

            for (beatSecond in snapshotStartSecond..second) {
                val beat = beatSecond * SAMPLE_RATE_HZ.toLong()
                setRaw(beat, 30_000.0)
                setRaw(
                    beat + 64L,
                    18_000.0 + 2_500.0 * sin(2.0 * PI * beatSecond / 5.0)
                )
            }

            // 첫 usable interval을 window 시작 한 박동 뒤에서 시작시켜, 실제 rolling
            // 분석처럼 첫 lower point가 교체 경계보다 늦게 생기는 조건을 만든다.
            val intervals = ((snapshotStartSecond + 2)..second).map { endSecond ->
                IbiInterval(
                    intervalSec = 1.0,
                    endSampleIndex = endSecond * SAMPLE_RATE_HZ.toLong(),
                    segmentId = 0L,
                    endSamplePosition = endSecond * SAMPLE_RATE_HZ
                )
            }
            val lower = PpgLowerEnvelopeExtractor.extract(
                rawSamples = raw,
                windowStartSamplePosition = rawStart,
                acceptedIntervals = intervals,
                segmentId = 0L,
                sampleRateHz = SAMPLE_RATE_HZ
            )
            val estimate = HeartRateEstimate(
                bpm = 60,
                ibiIntervals = intervals,
                peakCount = intervals.size + 1,
                intervalCount = intervals.size,
                averageIntervalSec = 1.0,
                qualityScore = 1.0,
                lowerEnvelopeSamples = lower,
                lowerEnvelopeReplacementStartSamplePosition = lower.firstOrNull()?.samplePosition
            )
            state = calculator.processBurst(
                sensorData = sensorData(second),
                heartRateEstimate = estimate,
                heartRateStatus = MetricCalculationStatus(
                    state = MetricCalculationState.VALID,
                    message = "synthetic rolling HR"
                ),
                analysisSegmentId = 0L
            )
        }

        assertEquals(MetricCalculationState.VALID, state.rrCalculationStatus.state)
        assertTrue(state.ppgRespirationGraphData.rawWindowSeconds >= 25.0)
        assertTrue(state.rrFinal!! in 10.0..14.0)
    }

    @Test
    fun revisingEnvelopeTailWithdrawsStaleRrvIntervals() {
        val calculator = PotchArousalCalculator()
        var state = ArousalState()

        repeat(71) { second ->
            state = calculator.processBurst(
                sensorData = sensorData(second),
                heartRateEstimate = envelopeEstimate(second) { beatSecond ->
                    20_000.0 + 2_000.0 * sin(2.0 * PI * beatSecond / 5.0)
                },
                heartRateStatus = MetricCalculationStatus(
                    state = MetricCalculationState.VALID,
                    message = "synthetic HR"
                ),
                analysisSegmentId = 0L
            )
        }
        val beforeRevision = state.rrvPpgIntervalCount
        assertTrue(beforeRevision >= 8)

        state = calculator.processBurst(
            sensorData = sensorData(71),
            // 최신 HR window가 과거 tail의 호흡 crest를 철회한 상황.
            heartRateEstimate = envelopeEstimate(71) { 20_000.0 },
            heartRateStatus = MetricCalculationStatus(
                state = MetricCalculationState.VALID,
                message = "revised synthetic HR"
            ),
            analysisSegmentId = 0L
        )

        assertTrue(state.rrvPpgIntervalCount < beforeRevision)
    }

    private fun envelopeEstimate(
        second: Int,
        valueAtBeat: (Int) -> Double
    ): HeartRateEstimate {
        val snapshotStartSecond = (second - 11).coerceAtLeast(0)
        val lowerPoints = (snapshotStartSecond..second).map { beatSecond ->
            PpgLowerEnvelopeSample(
                samplePosition = beatSecond * SAMPLE_RATE_HZ.toLong() + 64L,
                rawValue = valueAtBeat(beatSecond),
                segmentId = 0L
            )
        }
        return HeartRateEstimate(
            bpm = 60,
            ibiIntervals = emptyList(),
            peakCount = lowerPoints.size,
            intervalCount = 0,
            averageIntervalSec = 1.0,
            qualityScore = 1.0,
            lowerEnvelopeSamples = lowerPoints,
            lowerEnvelopeReplacementStartSamplePosition =
                lowerPoints.firstOrNull()?.samplePosition
        )
    }

    private fun ibi(start: Double, end: Double): IbiInterval = IbiInterval(
        intervalSec = (end - start) / SAMPLE_RATE_HZ,
        endSampleIndex = end.toLong(),
        segmentId = SEGMENT_ID,
        endSamplePosition = end
    )

    private fun sensorData(second: Int): SensorData {
        val ppg = ByteArray(SAMPLE_RATE_HZ.toInt() * 2)
        repeat(SAMPLE_RATE_HZ.toInt()) { index ->
            val value = 20_000
            ppg[index * 2] = (value and 0xFF).toByte()
            ppg[index * 2 + 1] = ((value ushr 8) and 0xFF).toByte()
        }
        return SensorData(
            timestamp = second.toLong(),
            sequenceStart = second * 8,
            sequenceEnd = second * 8 + 7,
            packetCount = 8,
            ntcRaw = 0,
            batteryRaw = 0,
            ppgData = ppg,
            imuData = ByteArray(64 * 6 * 2)
        )
    }

    companion object {
        private const val SAMPLE_RATE_HZ = 128.0
        private const val SEGMENT_ID = 7L
    }
}
