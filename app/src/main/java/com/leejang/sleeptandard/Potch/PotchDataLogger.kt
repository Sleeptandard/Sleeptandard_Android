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

    private val rows = mutableListOf<String>()

    var lastSavedFilePath: String? = null
        private set

    fun start() {
        isLogging = true
        rows.clear()
        lastSavedFilePath = null

        rows.add(
            listOf(
                "phone_time",
                "timestamp",
                "super_frame_hex",
                "complete",
                "miss_packet_num",
                "error_log"
            ).joinToString(",")
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

        val hex = superFrame.joinToString(" ") { byte ->
            "%02X".format(byte.toInt() and 0xFF)
        }

        val row = listOf(
            escapeCsv(phoneTimeText),
            timestamp?.toString() ?: "",
            escapeCsv(hex),
            complete,
            escapeCsv(missPacketNum),
            escapeCsv(errorLog)
        ).joinToString(",")

        rows.add(row)
    }

    fun stopAndSave(): String? {
        if (!isLogging && rows.size <= 1) return null

        isLogging = false

        val timestamp = SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.getDefault()
        ).format(Date())

        val fileName = "potch_super_frame_log_$timestamp.csv"
        val csvText = rows.joinToString("\n")

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToDownloadsApi29AndAbove(fileName, csvText)
        } else {
            saveToDownloadsBelowApi29(fileName, csvText)
        }
    }

    fun clear() {
        isLogging = false
        rows.clear()
        lastSavedFilePath = null
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToDownloadsApi29AndAbove(
        fileName: String,
        csvText: String
    ): String? {
        val resolver = appContext.contentResolver

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/PotchLogs"
            )
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return null

        resolver.openOutputStream(uri)?.use { output ->
            output.write(csvText.toByteArray())
        }

        val path = "Download/PotchLogs/$fileName"
        lastSavedFilePath = path
        return path
    }

    private fun saveToDownloadsBelowApi29(
        fileName: String,
        csvText: String
    ): String? {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )

        val potchDir = File(downloadsDir, "PotchLogs")
        if (!potchDir.exists()) {
            potchDir.mkdirs()
        }

        val file = File(potchDir, fileName)
        file.writeText(csvText)

        lastSavedFilePath = file.absolutePath
        return file.absolutePath
    }

    private fun escapeCsv(value: String): String {
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    fun startIfNeeded() {
        if (isLogging) return
        start()
    }

    fun logConnectionEvent(
        event: String,
        message: String
    ) {
        if (!isLogging) return

        val phoneTimeText = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss.SSS",
            Locale.getDefault()
        ).format(Date(System.currentTimeMillis()))

        val row = listOf(
            escapeCsv(phoneTimeText),
            "",
            "",
            event,
            "",
            escapeCsv(message)
        ).joinToString(",")

        rows.add(row)
    }
}