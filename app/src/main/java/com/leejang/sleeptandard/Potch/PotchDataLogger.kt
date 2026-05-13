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

    // 로그 저장 여부
    private var isLogging = false

    // 로그 시작 시간
    private var startTimeMillis: Long = 0L

    // CSV에 들어갈 행들을 임시로 저장
    private val rows = mutableListOf<String>()

    // 마지막으로 저장된 파일 경로
    var lastSavedFilePath: String? = null
        private set

    /**
     * 로깅 시작.
     * 팟치 연결 성공 시 호출.
     */
    fun start() {
        isLogging = true
        startTimeMillis = System.currentTimeMillis()
        rows.clear()
        lastSavedFilePath = null

        rows.add(
            listOf(
                "elapsed_ms",
                "system_time_ms",
                "packet_size",
                "hex"
            ).joinToString(",")
        )
    }

    /**
     * BLE notification으로 받은 raw ByteArray를 CSV 한 줄로 저장.
     */
    fun logPacket(data: ByteArray) {
        if (!isLogging) return

        val now = System.currentTimeMillis()
        val elapsed = now - startTimeMillis
        val hex = data.joinToString(" ") { byte ->
            "%02X".format(byte.toInt() and 0xFF)
        }

        val row = listOf(
            elapsed.toString(),
            now.toString(),
            data.size.toString(),
            "\"$hex\""
        ).joinToString(",")

        rows.add(row)
    }

    /**
     * 현재까지 쌓인 로그를 CSV 파일로 저장하고 로깅 종료.
     * 팟치 연결 해제 시 호출.
     */
    fun stopAndSave(): String? {
        if (!isLogging && rows.size <= 1) return null

        isLogging = false

        val timestamp = SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.getDefault()
        ).format(Date())

        val fileName = "potch_raw_log_$timestamp.csv"
        val csvText = rows.joinToString("\n")

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToDownloadsApi29AndAbove(fileName, csvText)
        } else {
            saveToDownloadsBelowApi29(fileName, csvText)
        }
    }


    /**
     * 저장하지 않고 로그 초기화.
     */
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

        return "Download/PotchLogs/$fileName"
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

        return file.absolutePath
    }
}