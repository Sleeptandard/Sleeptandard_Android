package com.leejang.sleeptandard.backend

import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.CRC32C

/**
 * S3에 업로드하는 Potch 원본 파일 규격.
 *
 * 파일은 헤더/푸터 없이 150-byte record가 연속된 binary이다.
 * 자세한 byte layout과 FastAPI API 계약은 docs/potch-raw-upload-api.md를 참조한다.
 */
object PotchRawFileContract {
    const val FORMAT_VERSION = "potch-raw-v1"
    const val CONTENT_TYPE = "application/octet-stream"
    const val RECORD_SIZE_BYTES = 150L
    const val DEFAULT_PART_SIZE_BYTES = 8L * 1024L * 1024L
    const val MIN_NON_FINAL_PART_SIZE_BYTES = 5L * 1024L * 1024L

    private val fileNamePattern = Regex(
        "^potch_packet_raw_data_\\d{8}_\\d{6}_\\d{3}_" +
            "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-" +
            "[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\\.bin$"
    )

    fun sessionId(file: File): String? =
        fileNamePattern.matchEntire(file.name)?.groupValues?.get(1)

    fun validationError(file: File): String? = when {
        !file.isFile -> "파일이 존재하지 않음"
        sessionId(file) == null -> "Potch raw 파일명 규격이 아님"
        file.length() == 0L -> "빈 raw 파일"
        file.length() % RECORD_SIZE_BYTES != 0L ->
            "파일 크기가 ${RECORD_SIZE_BYTES}B record 단위와 맞지 않음"
        else -> null
    }

    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** S3 x-amz-checksum-crc32c에 사용할 big-endian uint32 Base64 값. */
    fun crc32cBase64(file: File, offset: Long, byteCount: Long): String {
        require(offset >= 0L && byteCount >= 0L && offset + byteCount <= file.length())
        val checksum = CRC32C()
        RandomAccessFile(file, "r").use { input ->
            input.seek(offset)
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var remaining = byteCount
            while (remaining > 0L) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                check(read > 0) { "CRC32C 계산 중 파일이 조기에 끝남" }
                checksum.update(buffer, 0, read)
                remaining -= read
            }
        }

        val value = checksum.value
        val bytes = byteArrayOf(
            ((value ushr 24) and 0xFF).toByte(),
            ((value ushr 16) and 0xFF).toByte(),
            ((value ushr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte()
        )
        return Base64.getEncoder().encodeToString(bytes)
    }
}
