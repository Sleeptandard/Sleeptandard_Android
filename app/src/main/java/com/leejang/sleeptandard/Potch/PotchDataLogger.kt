package com.leejang.sleeptandard.Potch

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Potch에서 들어오는 BLE raw 데이터를 메모리에 누적했다가
 * 연결 해제 시 CSV 파일로 저장하는 클래스.
 */
class PotchDataLogger(
    context: Context
) {
    private val appContext = context.applicationContext

    private var isLogging = false
    private var logFile: File? = null

    fun startIfNeeded() {
        if (isLogging && logFile != null) return

        isLogging = true

        val timestamp = SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.getDefault()
        ).format(Date())

        val dir = File(appContext.filesDir, "PotchLogs")
        if (!dir.exists()) dir.mkdirs()

        logFile = File(dir, "potch_session_$timestamp.csv")

        logFile?.writeText(
            "phone_time,timestamp,super_frame_hex,complete,miss_packet_num,error_log\n"
        )
    }

    fun logSuperFrame(
        phoneTimeMillis: Long,
        timestamp: Long?,
        superFrame: ByteArray,
        complete: String,
        missPacketNum: String,
        errorLog: String
    ) {
        if (!isLogging) return

        val phoneTimeText = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss.SSS",
            Locale.getDefault()
        ).format(Date(phoneTimeMillis))

        val hex = superFrame.joinToString(" ") {
            "%02X".format(it.toInt() and 0xFF)
        }

        val row = listOf(
            escapeCsv(phoneTimeText),
            timestamp?.toString() ?: "",
            escapeCsv(hex),
            complete,
            escapeCsv(missPacketNum),
            escapeCsv(errorLog)
        ).joinToString(",")

        logFile?.appendText(row + "\n")
    }

    fun logConnectionEvent(
        event: String,
        message: String
    ) {
        if (!isLogging) return

        val phoneTimeText = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss.SSS",
            Locale.getDefault()
        ).format(Date())

        val row = listOf(
            escapeCsv(phoneTimeText),
            "",
            "",
            event,
            "",
            escapeCsv(message)
        ).joinToString(",")

        logFile?.appendText(row + "\n")
    }

    fun stopAndSave(): String? {
        isLogging = false
        return logFile?.absolutePath
    }

    private fun escapeCsv(value: String): String {
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
}