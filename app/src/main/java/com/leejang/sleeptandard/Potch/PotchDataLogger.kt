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
 * Potch Super Frame 로그를 CSV 파일에 실시간 append로 저장하는 클래스.
 *
 * 기존 방식:
 * - rows 리스트에 메모리로 쌓음
 * - 종료 시 한 번에 파일 저장
 *
 * 변경 방식:
 * - 로그 시작 시 앱 내부 저장소에 CSV 파일 생성
 * - Super Frame이 들어올 때마다 즉시 append
 * - 앱이 중간에 죽어도 이미 append된 데이터는 파일에 남음
 * - 종료 시 Downloads/PotchLogs로 복사해서 사용자가 확인 가능하게 함
 */
class PotchDataLogger(
    context: Context
) {
    private var workingDebugLogFile: File? = null
    private val appContext = context.applicationContext

    // 현재 로그 기록 중인지 여부
    private var isLogging = false

    // 실시간 append 대상 파일
    private var workingLogFile: File? = null

    // 마지막으로 Downloads에 저장/복사된 경로
    var lastSavedFilePath: String? = null
        private set

    /**
     * 새 로그 세션을 시작한다.
     *
     * 주의:
     * 이 함수는 항상 새 파일을 만들기 때문에,
     * 재연결 때마다 호출하면 로그가 끊길 수 있다.
     *
     * 재연결 상황에서는 startIfNeeded()를 사용해야 한다.
     */
    fun start() {
        isLogging = true
        lastSavedFilePath = null

        val timestamp = SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.getDefault()
        ).format(Date())

        val dir = File(appContext.filesDir, "PotchLogs")
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
     * 자동 재연결 시에는 반드시 이 함수를 써야 한다.
     */
    fun startIfNeeded() {
        if (isLogging && workingLogFile != null) return
        start()
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
     * 연결 끊김, 종료 같은 이벤트를 CSV에 한 줄로 기록한다.
     *
     * 예:
     * complete 컬럼에 disconnected / finished 같은 이벤트명을 기록
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
     * 로깅을 종료하고, 현재까지 append된 파일을 Downloads/PotchLogs로 복사한다.
     *
     * appContext.filesDir 내부 파일은 이미 실시간으로 저장되어 있고,
     * 이 함수는 사용자가 파일 앱에서 쉽게 볼 수 있도록 Downloads로 내보내는 역할이다.
     */
    fun stopAndSave(): String? {
        val sourceFile = workingLogFile ?: return null
        val debugFile = workingDebugLogFile

        isLogging = false

        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            return null
        }

        val savedCsvPath = copyFileToDownloads(sourceFile)

        if (debugFile != null && debugFile.exists() && debugFile.length() > 0L) {
            copyFileToDownloads(debugFile)
        }

        return savedCsvPath
    }

    /**
     * 로그 파일 경로만 반환한다.
     *
     * 디버깅용으로 현재 작업 중인 내부 파일 위치를 확인하고 싶을 때 사용 가능.
     */
    fun getWorkingLogPath(): String? {
        return workingLogFile?.absolutePath
    }

    /**
     * 저장하지 않고 현재 로그 상태를 초기화한다.
     */
    fun clear() {
        isLogging = false
        workingLogFile = null
        lastSavedFilePath = null
    }

    /*
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun copyToDownloadsApi29AndAbove(
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

    private fun copyToDownloadsBelowApi29(
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

     */

    private fun escapeCsv(value: String): String {
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    companion object {
        private const val INTERNAL_LOG_DIR_NAME = "PotchLogs"
        private const val DOWNLOAD_LOG_DIR_NAME = "PotchLogs"

        fun listInternalLogFiles(context: Context): List<InternalPotchLogFile> {
            val dir = File(context.applicationContext.filesDir, INTERNAL_LOG_DIR_NAME)

            if (!dir.exists()) return emptyList()

            return dir.listFiles()
                ?.filter { file ->
                    file.extension.equals("csv", ignoreCase = true) ||
                            file.extension.equals("txt", ignoreCase = true)
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
                if (!sourceFile.extension.equals("csv", ignoreCase = true)) return@forEach

                val exportedPath =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        copyFileToDownloadsApi29AndAbove(
                            context = appContext,
                            sourceFile = sourceFile
                        )
                    } else {
                        copyFileToDownloadsBelowApi29(
                            sourceFile = sourceFile
                        )
                    }

                if (exportedPath != null) {
                    exportedPaths.add(exportedPath)
                }
            }

            return exportedPaths
        }
        @RequiresApi(Build.VERSION_CODES.Q)
        private fun copyFileToDownloadsApi29AndAbove(
            context: Context,
            sourceFile: File
        ): String? {
            val resolver = context.contentResolver
            val fileName = sourceFile.name

            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
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

    private fun copyFileToDownloads(sourceFile: File): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            copyFileToDownloadsApi29AndAbove(sourceFile)
        } else {
            copyFileToDownloadsBelowApi29(sourceFile)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun copyFileToDownloadsApi29AndAbove(sourceFile: File): String? {
        val resolver = appContext.contentResolver
        val fileName = sourceFile.name

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
            FileInputStream(sourceFile).use { input ->
                input.copyTo(output, bufferSize = 1024 * 1024)
            }
        }

        val path = "Download/PotchLogs/$fileName"
        lastSavedFilePath = path
        return path
    }

    private fun copyFileToDownloadsBelowApi29(sourceFile: File): String? {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )

        val potchDir = File(downloadsDir, "PotchLogs")
        if (!potchDir.exists()) {
            potchDir.mkdirs()
        }

        val targetFile = File(potchDir, sourceFile.name)

        FileInputStream(sourceFile).use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output, bufferSize = 1024 * 1024)
            }
        }

        lastSavedFilePath = targetFile.absolutePath
        return targetFile.absolutePath
    }

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

}