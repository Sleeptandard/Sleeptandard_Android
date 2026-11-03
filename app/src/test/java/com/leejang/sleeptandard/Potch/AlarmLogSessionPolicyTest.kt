package com.leejang.sleeptandard.Potch

import com.leejang.sleeptandard.backend.PotchRawFileContract
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AlarmLogSessionPolicyTest {
    private val start = 1_000L
    private fun scheduled() = AlarmLogSessionPolicy.schedule(emptyList(), 7, 900_000L, start, "a")

    @Test fun alarmSettingOpensRawAndStabilityWithoutConnectionState() {
        val session = scheduled().single()
        assertTrue(session.recordsRaw(start))
        assertTrue(session.recordsStability)
        assertFalse(session.recordsRaw(start - 1))
    }

    @Test fun alarmEditPreservesFileIdentityAndStartTime() {
        val edited = AlarmLogSessionPolicy.schedule(scheduled(), 7, 1_800_000L, 2_000L, "unused").single()
        assertEquals("a", edited.id)
        assertEquals(start, edited.startedAtMillis)
        assertEquals(1_800_000L, edited.targetTimeMillis)
    }

    @Test fun cancelStopsBothAndStaleCancelCannotStopEditedAlarm() {
        val edited = AlarmLogSessionPolicy.schedule(scheduled(), 7, 1_800_000L, 2_000L, "unused")
        assertEquals(edited, AlarmLogSessionPolicy.cancel(edited, 900_000L, 3_000L))
        val canceled = AlarmLogSessionPolicy.cancel(edited, 1_800_000L, 3_000L).single()
        assertFalse(canceled.recordsRaw(3_000L))
        assertFalse(canceled.recordsStability)
    }

    @Test fun ringingIsNotCancellation() {
        val ringing = AlarmLogSessionPolicy.ring(scheduled(), 7, 900_000L).single()
        assertEquals(AlarmLogPhase.RINGING, ringing.phase)
        assertTrue(ringing.recordsRaw(901_000L))
        assertTrue(ringing.recordsStability)
    }

    @Test fun dismissalStopsStabilityNowAndRawExactlyFiveMinutesLater() {
        val ringing = AlarmLogSessionPolicy.ring(scheduled(), 7, 900_000L)
        val stopped = AlarmLogSessionPolicy.dismiss(ringing, "a", 910_000L).single()
        assertFalse(stopped.recordsStability)
        assertEquals(910_000L, stopped.stabilityStopAtMillis)
        assertTrue(stopped.recordsRaw(1_209_999L))
        assertFalse(stopped.recordsRaw(1_210_000L))
    }

    @Test fun duplicateDismissDoesNotExtendFiveMinuteDeadline() {
        val ringing = AlarmLogSessionPolicy.ring(scheduled(), 7, 900_000L)
        val stopped = AlarmLogSessionPolicy.dismiss(ringing, "a", 910_000L)
        assertEquals(stopped, AlarmLogSessionPolicy.dismiss(stopped, "a", 950_000L))
    }

    @Test fun oldDeadlineCannotDisconnectNewAlarmAndBothRawWindowsCanOverlap() {
        val tail = AlarmLogSessionPolicy.dismiss(AlarmLogSessionPolicy.ring(scheduled(), 7, 900_000L), "a", 910_000L)
        val next = AlarmLogSessionPolicy.schedule(tail, 7, 2_000_000L, 920_000L, "b")
        assertEquals(2, next.count { it.recordsRaw(930_000L) })
        assertEquals(1, next.count { it.recordsStability })
        assertFalse(AlarmLogSessionPolicy.shouldDisconnectAfterTail(next, "a", 1_210_000L))
        assertTrue(AlarmLogSessionPolicy.shouldDisconnectAfterTail(tail, "a", 1_210_000L))
    }

    @Test fun newAlarmAfterCancellationCreatesNewFiles() {
        val canceled = AlarmLogSessionPolicy.cancel(scheduled(), 900_000L, 2_000L)
        val next = AlarmLogSessionPolicy.schedule(canceled, 7, 950_000L, 3_000L, "b")
        assertEquals("b", next.single { it.recordsStability }.id)
    }

    @Test fun serverSessionIdBecomesRawUploadPathIdentity() {
        val serverSessionId = "550e8400-e29b-41d4-a716-446655440000"
        val session = AlarmLogSessionPolicy.schedule(
            emptyList(),
            7,
            900_000L,
            start,
            serverSessionId
        ).single()
        val rawFile = File(
            "potch_packet_raw_data_20261001_220000_000_${session.id}.bin"
        )

        assertEquals(serverSessionId, session.id)
        assertEquals(serverSessionId, PotchRawFileContract.sessionId(rawFile))
    }

    @Test fun staleRingAndDismissAreIgnored() {
        assertEquals(scheduled(), AlarmLogSessionPolicy.ring(scheduled(), 7, 123L))
        assertEquals(scheduled(), AlarmLogSessionPolicy.dismiss(scheduled(), "a", 123L))
        assertFalse(AlarmLogSessionPolicy.shouldDisconnectAfterTail(scheduled(), "missing", Long.MAX_VALUE))
    }
}
