package com.leejang.sleeptandard.Potch

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


data class InternalPotchLogFile(
    val name: String,
    val absolutePath: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long
)

/**
 * Potch Super Frame 로그와 디버그 로그를 내부 저장소에 실시간 append로 저장하는 클래스.
 *
 * 저장 파일:
 * 1. potch_super_frame_log_yyyyMMdd_HHmmss.csv
 *    - 실제 Potch Super Frame 데이터 저장
 *
 * 2. potch_debug_log_yyyyMMdd_HHmmss.txt
 *    - BLE 상태, Service 상태, 재연결 상태 같은 디버그 로그 저장
 *
 * 종료 및 저장 시:
 * - 두 파일을 모두 Download/PotchLogs 폴더로 복사한다.
 */
class PotchDataLogger(
    context: Context
) {
    private val appContext = context.applicationContext

    // 현재 로그 기록 중인지 여부
    private var isLogging = false

    // Super Frame CSV 로그 파일
    private var workingLogFile: File? = null

    // TAG 기반 디버그 TXT 로그 파일
    private var workingDebugLogFile: File? = null

    // 마지막으로 Downloads에 저장/복사된 CSV 경로
    var lastSavedFilePath: String? = null
        private set

    /**
     * 새 로그 세션을 시작한다.
     *
     * 이 함수는 항상 새 파일을 만든다.
     * 재연결 상황에서는 새 파일을 만들지 않도록 startIfNeeded()를 사용해야 한다.
     */
    fun start() {
        isLogging = true
        lastSavedFilePath = null

        val timestamp = SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.getDefault()
        ).format(Date())

        val dir = File(appContext.filesDir, INTERNAL_LOG_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }

        workingLogFile = File(dir, "potch_super_frame_log_$timestamp.csv")
        workingDebugLogFile = File(dir, "potch_debug_log_$timestamp.txt")

        workingDebugLogFile?.writeText(
            "Potch debug log started at $timestamp\n"
        )

        workingLogFile?.writeText(
            listOf(
                "phone_time",
                "timestamp",
                "super_frame_hex",
                "complete",
                "miss_packet_num",
                "error_log"
            ).joinToString(",") + "\n"
        )
    }

    /**
     * 이미 로깅 중이면 기존 파일에 이어서 쓰고,
     * 로깅 중이 아니면 새 로그 파일을 만든다.
     *
     * 자동 재연결 시에는 반드시 이 함수를 써야 로그가 끊기지 않는다.
     */
    fun startIfNeeded() {
        if (isLogging && workingLogFile != null) return
        start()
    }

    /**
     * Logcat에 찍던 주요 상태 로그를 내부 TXT 파일에도 저장한다.
     *
     * 저장 예:
     * 2026-06-06 23:33:55.123 I/PotchBleManager: Found Potch again
     */
    fun logDebug(
        tag: String,
        message: String,
        level: String = "D"
    ) {
        if (!isLogging) return

        val file = workingDebugLogFile ?: return

        val phoneTimeText = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss.SSS",
            Locale.getDefault()
        ).format(Date(System.currentTimeMillis()))

        val line = "$phoneTimeText $level/$tag: $message"

        file.appendText(line + "\n")
    }

    /**
     * 하나의 Super Frame 로그를 CSV 파일에 즉시 append한다.
     *
     * 저장 형식:
     * phone_time,timestamp,super_frame_hex,complete,miss_packet_num,error_log
     */
    fun logSuperFrame(
        phoneTimeMillis: Long,
        timestamp: Long?,
        superFrame: ByteArray,
        complete: String,
        missPacketNum: String,
        errorLog: String
    ) {
        if (!isLogging) return

        val file = workingLogFile ?: return

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
            escapeCsv(complete),
            escapeCsv(missPacketNum),
            escapeCsv(errorLog)
        ).joinToString(",")

        file.appendText(row + "\n")
    }

    /**
     * 연결 끊김, 재연결, 종료 같은 이벤트를 Super Frame CSV에도 한 줄로 기록한다.
     *
     * 예:
     * complete 컬럼에 disconnected / reconnect_scan_attempt / finished 등을 기록한다.
     */
    fun logConnectionEvent(
        event: String,
        message: String
    ) {
        if (!isLogging) return

        val file = workingLogFile ?: return

        val phoneTimeText = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss.SSS",
            Locale.getDefault()
        ).format(Date(System.currentTimeMillis()))

        val row = listOf(
            escapeCsv(phoneTimeText),
            "",
            "",
            escapeCsv(event),
            "",
            escapeCsv(message)
        ).joinToString(",")

        file.appendText(row + "\n")
    }

    /**
     * 로깅을 종료하고, 현재까지 append된 파일들을 Download/PotchLogs로 복사한다.
     *
     * 복사 대상:
     * - Super Frame CSV
     * - Debug TXT
     *
     * 반환값:
     * - CSV 파일이 저장된 경로
     */
    fun stopAndSave(): String? {
        val sourceFile = workingLogFile ?: return null
        val debugFile = workingDebugLogFile

        isLogging = false

        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            return null
        }

        val savedCsvPath = copyInternalFileToDownloads(
            context = appContext,
            sourceFile = sourceFile
        )

        if (debugFile != null && debugFile.exists() && debugFile.length() > 0L) {
            copyInternalFileToDownloads(
                context = appContext,
                sourceFile = debugFile
            )
        }

        lastSavedFilePath = savedCsvPath

        return savedCsvPath
    }

    /**
     * 현재 작업 중인 내부 CSV 파일 경로를 확인할 때 사용한다.
     */
    fun getWorkingLogPath(): String? {
        return workingLogFile?.absolutePath
    }

    /**
     * 현재 작업 중인 내부 디버그 TXT 파일 경로를 확인할 때 사용한다.
     */
    fun getWorkingDebugLogPath(): String? {
        return workingDebugLogFile?.absolutePath
    }

    /**
     * 저장하지 않고 현재 로그 상태를 초기화한다.
     */
    fun clear() {
        isLogging = false
        workingLogFile = null
        workingDebugLogFile = null
        lastSavedFilePath = null
    }

    private fun escapeCsv(value: String): String {
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    companion object {
        private const val INTERNAL_LOG_DIR_NAME = "PotchLogs"
        private const val DOWNLOAD_LOG_DIR_NAME = "PotchLogs"

        /**
         * 앱 내부 저장소에 남아 있는 Potch 로그 파일 목록을 가져온다.
         *
         * CSV와 TXT를 모두 보여준다.
         */
        fun listInternalLogFiles(context: Context): List<InternalPotchLogFile> {
            val dir = File(context.applicationContext.filesDir, INTERNAL_LOG_DIR_NAME)

            if (!dir.exists()) return emptyList()

            return dir.listFiles()
                ?.filter { file ->
                    file.isFile && isSupportedLogFile(file)
                }
                ?.sortedByDescending { it.lastModified() }
                ?.map { file ->
                    InternalPotchLogFile(
                        name = file.name,
                        absolutePath = file.absolutePath,
                        sizeBytes = file.length(),
                        lastModifiedMillis = file.lastModified()
                    )
                }
                ?: emptyList()
        }

        /**
         * 내부 저장소의 선택된 로그 파일들을 Download/PotchLogs로 내보낸다.
         *
         * CSV와 TXT를 모두 내보낼 수 있다.
         */
        fun exportInternalLogFilesToDownloads(
            context: Context,
            fileNames: List<String>
        ): List<String> {
            val appContext = context.applicationContext
            val dir = File(appContext.filesDir, INTERNAL_LOG_DIR_NAME)

            if (!dir.exists()) return emptyList()

            val exportedPaths = mutableListOf<String>()

            fileNames.distinct().forEach { fileName ->
                val sourceFile = File(dir, fileName)

                if (!sourceFile.exists() || !sourceFile.isFile) return@forEach
                if (!isSupportedLogFile(sourceFile)) return@forEach

                val exportedPath = copyInternalFileToDownloads(
                    context = appContext,
                    sourceFile = sourceFile
                )

                if (exportedPath != null) {
                    exportedPaths.add(exportedPath)
                }
            }

            return exportedPaths
        }

        /**
         * CSV / TXT 로그 파일만 허용한다.
         */
        private fun isSupportedLogFile(file: File): Boolean {
            val ext = file.extension.lowercase(Locale.ROOT)
            return ext == "csv" || ext == "txt"
        }

        /**
         * 내부 파일을 Download/PotchLogs로 복사한다.
         *
         * Android 10 이상:
         * - MediaStore 사용
         *
         * Android 9 이하:
         * - public Downloads 폴더에 직접 파일 복사
         */
        private fun copyInternalFileToDownloads(
            context: Context,
            sourceFile: File
        ): String? {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                copyFileToDownloadsApi29AndAbove(
                    context = context,
                    sourceFile = sourceFile
                )
            } else {
                copyFileToDownloadsBelowApi29(
                    sourceFile = sourceFile
                )
            }
        }

        /**
         * Android 10 이상에서 MediaStore를 이용해 Downloads/PotchLogs로 복사한다.
         *
         * 중요:
         * - CSV는 text/csv
         * - TXT는 text/plain
         *
         * 이렇게 확장자별 MIME type을 맞춰야 debug txt가 txt.csv처럼 저장되는 문제를 줄일 수 있다.
         */
        @RequiresApi(Build.VERSION_CODES.Q)
        private fun copyFileToDownloadsApi29AndAbove(
            context: Context,
            sourceFile: File
        ): String? {
            val resolver = context.contentResolver
            val fileName = sourceFile.name

            val mimeType =
                when (sourceFile.extension.lowercase(Locale.ROOT)) {
                    "csv" -> "text/csv"
                    "txt" -> "text/plain"
                    else -> "application/octet-stream"
                }

            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/$DOWNLOAD_LOG_DIR_NAME"
                )
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return null

            resolver.openOutputStream(uri)?.use { output ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(output, bufferSize = 1024 * 1024)
                }
            }

            return "Download/$DOWNLOAD_LOG_DIR_NAME/$fileName"
        }

        /**
         * Android 9 이하에서 Downloads/PotchLogs로 파일을 복사한다.
         */
        private fun copyFileToDownloadsBelowApi29(
            sourceFile: File
        ): String? {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )

            val potchDir = File(downloadsDir, DOWNLOAD_LOG_DIR_NAME)
            if (!potchDir.exists()) {
                potchDir.mkdirs()
            }

            val targetFile = File(potchDir, sourceFile.name)

            FileInputStream(sourceFile).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output, bufferSize = 1024 * 1024)
                }
            }

            return targetFile.absolutePath
        }
    }
}
