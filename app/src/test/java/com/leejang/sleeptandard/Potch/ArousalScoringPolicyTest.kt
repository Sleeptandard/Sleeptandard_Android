package com.leejang.sleeptandard.Potch

import org.junit.Assert.assertEquals
import org.junit.Test

class ArousalScoringPolicyTest {
    @Test
    fun rrRiseHillStartsAtOneAndReachesNinetyFiveAtOnePointFive() {
        fun score(rise: Double) = RrRiseHillScorePolicy.score(
            riseBpm = rise,
            startBpm = 1.0,
            ninetyFiveBpm = 1.5,
            exponent = 3.0
        )

        assertEquals(0.0, score(0.99), 0.0)
        assertEquals(0.0, score(1.0), 0.0)
        assertEquals(0.95, score(1.5), 1e-12)
        assertEquals(1.0, score(100.0), 1e-6)
    }

    @Test
    fun peakHoldKeepsAndExtendsOnlyWhenMaximumIncreases() {
        val hold = PeakScoreHold(durationMillis = 30_000L)

        assertEquals(0.60, hold.update(0.60, 1_000L)!!, 0.0)
        assertEquals(0.60, hold.update(0.20, 20_000L)!!, 0.0)
        assertEquals(0.80, hold.update(0.80, 25_000L)!!, 0.0)
        assertEquals(0.80, hold.update(null, 54_999L)!!, 0.0)
        assertEquals(null, hold.update(null, 55_000L))
    }

    @Test
    fun binaryHoldRefreshesWheneverThresholdIsDetectedAgain() {
        val hold = BinaryScoreHold(durationMillis = 60_000L)

        assertEquals(1.0, hold.update(true, 1_000L), 0.0)
        assertEquals(1.0, hold.update(false, 40_000L), 0.0)
        assertEquals(1.0, hold.update(true, 50_000L), 0.0)
        assertEquals(1.0, hold.update(false, 109_999L), 0.0)
        assertEquals(0.0, hold.update(false, 110_000L), 0.0)
    }

    @Test
    fun finalScoreUsesFixedWeightsAndTemperatureMultiplierOnly() {
        val withoutTemperature = FinalWakeScorePolicy.score(
            microScore = 0.5,
            hrScore = 0.5,
            hrvScore = 0.5,
            rrScore = 0.5,
            rrvScore = 0.5,
            temperatureActive = false
        )
        val withTemperature = FinalWakeScorePolicy.score(
            microScore = 0.5,
            hrScore = 0.5,
            hrvScore = 0.5,
            rrScore = 0.5,
            rrvScore = 0.5,
            temperatureActive = true
        )

        assertEquals(0.575, withoutTemperature, 1e-12)
        assertEquals(0.69, withTemperature, 1e-12)
    }
}
