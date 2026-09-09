package com.leejang.sleeptandard.ClassFile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PotchAlarmTriggerPolicyTest {
    private val target = 2_000_000L
    private val windowStart = target - AlarmScheduler.MONITORING_WINDOW_MILLIS

    @Test
    fun `score must be strictly greater than 80`() {
        assertFalse(PotchAlarmTriggerPolicy.shouldTrigger(80.0, windowStart, target))
        assertTrue(PotchAlarmTriggerPolicy.shouldTrigger(80.01, windowStart, target))
    }

    @Test
    fun `score is ignored before the 15 minute window`() {
        assertFalse(PotchAlarmTriggerPolicy.shouldTrigger(100.0, windowStart - 1L, target))
        assertTrue(PotchAlarmTriggerPolicy.shouldTrigger(100.0, windowStart, target))
    }

    @Test
    fun `target time is handled by fallback alarm instead of early trigger`() {
        assertTrue(PotchAlarmTriggerPolicy.shouldTrigger(100.0, target - 1L, target))
        assertFalse(PotchAlarmTriggerPolicy.shouldTrigger(100.0, target, target))
    }

    @Test
    fun `invalid scores never trigger`() {
        assertFalse(PotchAlarmTriggerPolicy.shouldTrigger(Double.NaN, windowStart, target))
        assertFalse(PotchAlarmTriggerPolicy.shouldTrigger(Double.POSITIVE_INFINITY, windowStart, target))
    }
}
