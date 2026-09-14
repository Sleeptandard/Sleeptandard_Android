package com.leejang.sleeptandard.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PotchRawFileContractTest {
    private val sessionId = "550e8400-e29b-41d4-a716-446655440000"

    @Test
    fun validFileHasSessionIdAndWholeRecords() {
        withRawFile(ByteArray(300)) { file ->
            assertEquals(sessionId, PotchRawFileContract.sessionId(file))
            assertNull(PotchRawFileContract.validationError(file))
        }
    }

    @Test
    fun partialRecordIsRejected() {
        withRawFile(ByteArray(299)) { file ->
            assertNotNull(PotchRawFileContract.validationError(file))
        }
    }

    @Test
    fun crc32cUsesS3Base64BigEndianEncoding() {
        withRawFile("123456789".encodeToByteArray()) { file ->
            assertEquals("4waSgw==", PotchRawFileContract.crc32cBase64(file, 0, 9))
        }
    }

    private fun withRawFile(bytes: ByteArray, block: (File) -> Unit) {
        val directory = Files.createTempDirectory("potch-raw-contract").toFile()
        val file = File(
            directory,
            "potch_packet_raw_data_20260911_183104_123_$sessionId.bin"
        )
        try {
            file.writeBytes(bytes)
            block(file)
        } finally {
            directory.deleteRecursively()
        }
    }
}
