package com.leejang.sleeptandard.ClassFile

/** Pure decision rule used by the Potch foreground service and JVM tests. */
object PotchAlarmTriggerPolicy {
    fun shouldTrigger(score: Double, nowMillis: Long, targetTimeMillis: Long): Boolean {
        if (!score.isFinite() || targetTimeMillis <= 0L) return false
        val monitoringStart = targetTimeMillis - AlarmScheduler.MONITORING_WINDOW_MILLIS
        return nowMillis >= monitoringStart &&
            nowMillis < targetTimeMillis &&
            score > AlarmScheduler.POTCH_SCORE_THRESHOLD
    }
}
