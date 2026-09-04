package com.leejang.sleeptandard.Potch

/** File lifetime is independent of BLE connectivity and the 15-minute scoring window. */
enum class AlarmLogPhase { SCHEDULED, RINGING, POST_ALARM, CLOSED }

data class AlarmLogSession(
    val id: String,
    val alarmId: Int,
    val targetTimeMillis: Long,
    val startedAtMillis: Long,
    val phase: AlarmLogPhase = AlarmLogPhase.SCHEDULED,
    val rawStopAtMillis: Long = 0L,
    val stabilityStopAtMillis: Long = 0L
) {
    fun recordsRaw(nowMillis: Long): Boolean = nowMillis >= startedAtMillis && phase != AlarmLogPhase.CLOSED &&
        (phase != AlarmLogPhase.POST_ALARM || nowMillis < rawStopAtMillis)

    val recordsStability: Boolean
        get() = phase == AlarmLogPhase.SCHEDULED || phase == AlarmLogPhase.RINGING

    fun dismiss(nowMillis: Long): AlarmLogSession =
        if (phase != AlarmLogPhase.RINGING) this else copy(
            phase = AlarmLogPhase.POST_ALARM,
            rawStopAtMillis = nowMillis + POST_ALARM_MILLIS,
            stabilityStopAtMillis = nowMillis
        )

    companion object {
        const val POST_ALARM_MILLIS = 5 * 60_000L
    }
}

object AlarmLogSessionPolicy {
    /** Editing a pending alarm keeps its files; setting one after dismiss/cancel starts new files. */
    fun schedule(
        sessions: List<AlarmLogSession>, alarmId: Int, target: Long, now: Long, newId: String
    ): List<AlarmLogSession> {
        val previous = sessions.lastOrNull { it.phase == AlarmLogPhase.SCHEDULED }
        return if (previous != null) sessions.map {
            if (it.id == previous.id) it.copy(alarmId = alarmId, targetTimeMillis = target) else it
        } else sessions + AlarmLogSession(newId, alarmId, target, now)
    }

    fun cancel(sessions: List<AlarmLogSession>, target: Long, now: Long) = sessions.map {
        if (it.phase == AlarmLogPhase.SCHEDULED && it.targetTimeMillis == target) {
            it.copy(phase = AlarmLogPhase.CLOSED, rawStopAtMillis = now, stabilityStopAtMillis = now)
        } else it
    }

    fun ring(sessions: List<AlarmLogSession>, alarmId: Int, target: Long) = sessions.map {
        if (it.phase == AlarmLogPhase.SCHEDULED && it.alarmId == alarmId &&
            (target == it.targetTimeMillis || target == 0L)) it.copy(phase = AlarmLogPhase.RINGING) else it
    }

    fun dismiss(sessions: List<AlarmLogSession>, id: String, now: Long) = sessions.map {
        if (it.id == id) it.dismiss(now) else it
    }

    fun shouldDisconnectAfterTail(sessions: List<AlarmLogSession>, id: String, now: Long): Boolean {
        val finished = sessions.find { it.id == id } ?: return false
        return finished.phase == AlarmLogPhase.POST_ALARM && now >= finished.rawStopAtMillis &&
            sessions.none { it.recordsRaw(now) }
    }
}
