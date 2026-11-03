package com.leejang.sleeptandard.Potch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LowerTailBaselinePolicyTest {
    @Test
    fun selectsRequestedLowerTailFractionsFromWholeSessionValues() {
        val thousand = (1..1_000).map { sample(it.toLong(), it.toDouble()) }
        val hundred = (1..100).map { sample(it.toLong(), it.toDouble()) }

        assertEquals(
            listOf(1.0, 2.0),
            LowerTailBaselinePolicy.select(
                thousand,
                BaselineMetricType.HRV_RMSSD
            ).map { it.value }
        )
        assertEquals(
            (1..10).map(Int::toDouble),
            LowerTailBaselinePolicy.select(
                hundred,
                BaselineMetricType.HRV_LF_HF
            ).map { it.value }
        )
        assertEquals(
            (1..15).map(Int::toDouble),
            LowerTailBaselinePolicy.select(
                hundred,
                BaselineMetricType.RRV
            ).map { it.value }
        )
    }

    @Test
    fun denseRegionChoosesTheClusterInsideAccumulatedCandidates() {
        val dense = (0 until 12).map { 1.00 + it * 0.001 }
        val scattered = listOf(0.1, 0.4, 2.0, 3.5, 8.0, 13.0, 21.0, 34.0)

        val selected = LowerTailBaselinePolicy.densestRegion(
            values = dense + scattered,
            fraction = 0.40
        )

        assertEquals(8, selected.size)
        assertTrue(selected.all { it in 1.0..1.011 })
    }

    @Test
    fun hrvShiftedHillMatchesPythonTargets() {
        assertEquals(0.0, shiftedScore(1.30, 1.30, 1.30 * 100.0 / 75.0), 0.0)
        assertEquals(0.95, shiftedScore(1.30 * 100.0 / 75.0, 1.30, 1.30 * 100.0 / 75.0), 1e-12)

        assertEquals(0.0, shiftedScore(3.00, 3.00, 4.00, fullAtTarget = true), 0.0)
        assertEquals(1.0, shiftedScore(4.00, 3.00, 4.00, fullAtTarget = true), 0.0)
    }

    @Test
    fun rrvThresholdKeepsTwoSecondFloor() {
        assertEquals(2.0, RrvWakeThresholdPolicy.threshold(0.8, 2.0, 1.35), 0.0)
        assertEquals(2.7, RrvWakeThresholdPolicy.threshold(2.0, 2.0, 1.35), 1e-12)
        assertEquals(2.0, RrvWakeThresholdPolicy.threshold(null, 2.0, 1.35), 0.0)
    }

    private fun sample(timestamp: Long, value: Double) = TimedMetricValue(
        timestampMillis = timestamp,
        value = value,
        quality = 1.0
    )

    private fun shiftedScore(
        value: Double,
        start: Double,
        target: Double,
        fullAtTarget: Boolean = false
    ): Double = ShiftedHillScorePolicy.score(
        value = value,
        start = start,
        target = target,
        fullAtTarget = fullAtTarget
    )
}
