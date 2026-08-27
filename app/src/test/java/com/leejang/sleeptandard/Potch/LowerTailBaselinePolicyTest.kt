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
    fun arousalMultiplierRangesMatchRequestedBoundaries() {
        assertEquals(0.0, score(1.49, 1.50, 2.00), 0.0)
        assertEquals(0.5, score(1.75, 1.50, 2.00), 1e-12)
        assertEquals(1.0, score(2.00, 1.50, 2.00), 0.0)

        assertEquals(0.0, score(2.99, 3.00, 4.00), 0.0)
        assertEquals(0.5, score(3.50, 3.00, 4.00), 1e-12)
        assertEquals(1.0, score(4.00, 3.00, 4.00), 0.0)

        assertEquals(0.0, score(1.349, 1.35, 1.35), 0.0)
        assertEquals(1.0, score(1.35, 1.35, 1.35), 0.0)
        assertEquals(1.0, score(2.00, 1.35, 1.35), 0.0)
    }

    private fun sample(timestamp: Long, value: Double) = TimedMetricValue(
        timestampMillis = timestamp,
        value = value,
        quality = 1.0
    )

    private fun score(
        ratio: Double,
        start: Double,
        full: Double
    ): Double = BaselineMultiplierScorePolicy.score(
        value = ratio,
        baseline = 1.0,
        startMultiplier = start,
        fullMultiplier = full
    )
}
