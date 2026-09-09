package com.leejang.sleeptandard.Potch

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class PotchLogLifecycleTest {
    private lateinit var context: Context
    private lateinit var directory: File
    private val loggers = mutableListOf<PotchDataLogger>()
    private val exported = mutableSetOf<String>()
    private val preferences = mutableListOf<SharedPreferences>()
    private val session = AlarmLogSession("alarm-test", 1, 900_000L, 1_000L)
    private val packet = ByteArray(142) { it.toByte() }

    @Before fun setUp() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "log-test-${UUID.randomUUID()}"
        directory = File(base.cacheDir, prefix).apply { mkdirs() }
        context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = directory
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                base.getSharedPreferences("$prefix-$name", mode).also { preferences.add(it) }
        }
    }

    private fun logger() = PotchDataLogger(context) { exported.add(it.name) }.also(loggers::add)

    @After fun cleanUp() {
        loggers.forEach { it.closeHandlesForRecovery() }
        preferences.forEach { it.edit().clear().commit() }
        // Only the unique test-owned cache directory is removed.
        directory.deleteRecursively()
    }

    @Test fun bleOnlyDoesNotCreateAlarmFiles() {
        val logger = logger()
        logger.startBleLogging()
        logger.logRawPacket(2_000L, packet)
        assertNotNull(logger.getWorkingDebugLogPath())
        assertNull(logger.getWorkingLogPath())
        assertNull(logger.getWorkingStabilityEpisodeLogPath())
    }

    @Test fun alarmFilesResumeAtSamePathAndKeepTimestampGapAfterRecovery() {
        val first = logger()
        first.syncAlarmFiles(listOf(session), 1_000L)
        first.logRawPacket(2_000L, packet)
        val path = first.getWorkingLogPath()!!
        first.closeHandlesForRecovery()
        val restored = logger()
        restored.syncAlarmFiles(listOf(session), 10_000L)
        restored.logRawPacket(10_000L, packet)
        assertEquals(path, restored.getWorkingLogPath())
        val bytes = File(path).readBytes()
        assertEquals(300, bytes.size)
        assertEquals(2_000L, ByteBuffer.wrap(bytes, 0, 8).order(ByteOrder.LITTLE_ENDIAN).long)
        assertEquals(10_000L, ByteBuffer.wrap(bytes, 150, 8).order(ByteOrder.LITTLE_ENDIAN).long)
        assertArrayEquals(packet, bytes.copyOfRange(8, 150))
        assertArrayEquals(packet, bytes.copyOfRange(158, 300))
    }

    @Test fun manualBleStopDoesNotCloseRawOrStability() {
        val logger = logger()
        logger.syncAlarmFiles(listOf(session), 1_000L)
        logger.startBleLogging()
        val raw = logger.getWorkingLogPath()
        val stability = logger.getWorkingStabilityEpisodeLogPath()
        logger.stopBleAndSave("test manual stop")
        assertNull(logger.getWorkingDebugLogPath())
        assertEquals(raw, logger.getWorkingLogPath())
        assertEquals(stability, logger.getWorkingStabilityEpisodeLogPath())
        assertEquals(1, exported.size)
    }

    @Test fun cancellationClosesAlarmFilesButKeepsBle() {
        val logger = logger()
        logger.syncAlarmFiles(listOf(session), 1_000L)
        logger.startBleLogging()
        val canceled = session.copy(phase = AlarmLogPhase.CLOSED)
        logger.syncAlarmFiles(listOf(canceled), 2_000L)
        assertNull(logger.getWorkingLogPath())
        assertNull(logger.getWorkingStabilityEpisodeLogPath())
        assertNotNull(logger.getWorkingDebugLogPath())
        assertEquals(2, exported.size)
    }

    @Test fun bleIdentitySurvivesLoggerRecreation() {
        val first = logger()
        first.startBleLogging()
        val path = first.getWorkingDebugLogPath()
        first.closeHandlesForRecovery()
        val restored = logger()
        restored.startBleLogging()
        assertEquals(path, restored.getWorkingDebugLogPath())
        assertTrue(exported.isEmpty())
    }

    @Test fun sessionMetadataAndStabilityIdentitySurviveRecreation() {
        val store = AlarmLogSessionStore(context)
        val active = store.schedule(1, System.currentTimeMillis() + 3_600_000L).last()
        val first = logger()
        first.syncAlarmFiles(listOf(active))
        val path = first.getWorkingStabilityEpisodeLogPath()
        first.closeHandlesForRecovery()
        store.ring(active.alarmId, active.targetTimeMillis)
        val dismissed = store.dismiss(active.id)!!
        assertEquals(dismissed, AlarmLogSessionStore(context).load().last())
        val restored = logger()
        assertEquals(path, restored.getWorkingStabilityEpisodeLogPath())
        restored.syncAlarmFiles(listOf(dismissed))
        assertNull(restored.getWorkingStabilityEpisodeLogPath())
        assertTrue(File(path!!).name in exported)
        assertNotNull(restored.getWorkingLogPath())
    }

    @Test fun recoveryDropsOnlyIncompleteTrailingRecord() {
        val first = logger()
        first.syncAlarmFiles(listOf(session), 1_000L)
        first.logRawPacket(2_000L, packet)
        val raw = File(first.getWorkingLogPath()!!)
        first.closeHandlesForRecovery()
        raw.appendBytes(byteArrayOf(1, 2, 3))
        val restored = logger()
        restored.syncAlarmFiles(listOf(session), 3_000L)
        restored.logRawPacket(3_000L, packet)
        assertEquals(300L, raw.length())
        assertArrayEquals(packet, raw.readBytes().copyOfRange(158, 300))
    }

    @Test fun dismissClosesStabilityAndOldDeadlineDoesNotCloseNewRawFile() {
        val logger = logger()
        logger.syncAlarmFiles(listOf(session), 1_000L)
        val originalRaw = File(logger.getWorkingLogPath()!!)
        val tail = session.copy(phase = AlarmLogPhase.RINGING).dismiss(2_000L)
        logger.syncAlarmFiles(listOf(tail), 2_000L)
        assertNull(logger.getWorkingStabilityEpisodeLogPath())
        assertEquals(1, exported.size)
        val next = AlarmLogSession("next-test", 1, 1_500_000L, 3_000L)
        logger.syncAlarmFiles(listOf(tail, next), 3_000L)
        logger.logRawPacket(4_000L, packet)
        assertEquals(150L, originalRaw.length())
        logger.logRawPacket(tail.rawStopAtMillis, packet)
        assertEquals(150L, originalRaw.length())
        logger.syncAlarmFiles(listOf(tail, next), tail.rawStopAtMillis)
        assertTrue(originalRaw.name in exported)
        assertEquals(300L, File(logger.getWorkingLogPath()!!).length())
        assertNotNull(logger.getWorkingStabilityEpisodeLogPath())
    }
}
