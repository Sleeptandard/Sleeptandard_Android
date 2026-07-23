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
 * 3. potch_arousal_state_log_yyyyMMdd_HHmmss.csv
 *    - 각 Super Frame마다 계산된 ArousalState 저장
 *
 * 4. potch_hr_diagnostic_log_yyyyMMdd_HHmmss.csv
 *    - HR 계산 성공/실패 사유와 PPG/IMU 품질값 저장
 *
 * 종료 및 저장 시:
 * - 네 파일을 모두 Download/PotchLogs 폴더로 복사한다.
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

    // ArousalState 전용 CSV 로그 파일
    private var workingArousalLogFile: File? = null

    // HR 계산 상세 진단 전용 CSV 로그 파일
    private var workingHeartRateDiagnosticLogFile: File? = null

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
        workingArousalLogFile = File(dir, "potch_arousal_state_log_$timestamp.csv")
        workingHeartRateDiagnosticLogFile =
            File(dir, "potch_hr_diagnostic_log_$timestamp.csv")

        workingDebugLogFile?.writeText(
            text = "Potch debug log started at $timestamp\n",
            charset = Charsets.UTF_8
        )

        workingLogFile?.writeText(
            text = UTF8_BOM + listOf(
                "phone_time",
                "timestamp",
                "super_frame_hex",
                "complete",
                "miss_packet_num",
                "error_log"
            ).joinToString(",") + "\n",
            charset = Charsets.UTF_8
        )

        workingArousalLogFile?.writeText(
            text = UTF8_BOM + listOf(
                "phone_time",
                "timestamp",

                "final_wake_score",
                "is_wake_timing_candidate",

                "micro_movement_variance",
                "micro_movement_score",

                "rr_from_ppg",
                "rr_from_imu",
                "rr_final",
                "rr_score",
                "rr_raw_score",
                "rr_fusion_source",
                "rr_fusion_confidence",
                "rr_fusion_log",

                "rr_analysis_segment_id",
                "ppg_resp_peak_sample_positions",
                "ppg_resp_intervals_sec",
                "imu_resp_peak_sample_positions",
                "imu_resp_intervals_sec",

                "rrv_rmssd_sec",
                "rrv_rmssd_ms",
                "rrv_score",
                "rrv_source",
                "rrv_quality",
                "rrv_from_ppg_rmssd_sec",
                "rrv_from_imu_rmssd_sec",
                "rrv_ppg_interval_count",
                "rrv_imu_interval_count",
                "rrv_ppg_quality",
                "rrv_imu_quality",

                "hr_bpm",
                "hr_gradient",
                "hr_score",

                "hrv_rmssd_sec",
                "hrv_rmssd_ms",
                "hrv_lf",
                "hrv_hf",
                "hrv_lf_hf",
                "hrv_score",
                "hrv_quality",
                "hrv_log",

                "skin_temperature_celsius",
                "skin_temperature_gradient",
                "skin_temperature_score",

                "complete",
                "miss_packet_num",
                "error_log",
                "last_log"
            ).joinToString(",") + "\n",
            charset = Charsets.UTF_8
        )

        workingHeartRateDiagnosticLogFile?.writeText(
            text = UTF8_BOM + listOf(
                "phone_time",
                "timestamp",
                "analysis_segment_id",
                "processing_state",
                "underlying_failure_reason",
                "message",
                "heart_rate_fresh",
                "heart_rate_age_ms",

                "fusion_source",
                "fusion_log",

                "ir_processing_state",
                "ir_calculated_bpm",
                "ir_quality_score",
                "ir_accepted_interval_ratio",
                "ir_raw_sdsd_ms",

                "red_processing_state",
                "red_calculated_bpm",
                "red_quality_score",
                "red_accepted_interval_ratio",
                "red_raw_sdsd_ms",

                "combined_processing_state",
                "combined_calculated_bpm",
                "combined_quality_score",
                "combined_accepted_interval_ratio",
                "combined_raw_sdsd_ms",

                "window_sample_count",
                "window_seconds",
                "ir_dc_mean",
                "ir_min",
                "ir_max",
                "ac_robust_amplitude",
                "amplitude_cv",
                "spectral_concentration",
                "spectral_entropy",
                "abrupt_change_ratio",
                "selected_peak_threshold",
                "selected_threshold_percent",
                "selected_polarity",
                "detected_peak_count",
                "raw_ibi_count",
                "valid_ibi_count",
                "accepted_interval_ratio",
                "raw_sdsd_ms",
                "raw_ibi_cv",
                "physiological_interval_ratio",
                "raw_interval_quality_score",
                "sdsd_ms",
                "quality_score",
                "calculated_bpm",
                "displayed_bpm",
                "imu_max_delta_g",
                "max_raw_sample_delta",
                "crc_error_count",
                "sequence_loss_count",
                "estimated_lost_packet_count"
            ).joinToString(",") + "\n",
            charset = Charsets.UTF_8
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

        file.appendText(line + "\n", Charsets.UTF_8)
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

        file.appendText(row + "\n", Charsets.UTF_8)
    }

    /**
     * HR 계산 진단값을 전용 CSV에 append한다.
     *
     * 새 BPM이 없더라도 COLLECTING/NO_CONTACT/MOTION_ARTIFACT 같은 상태와
     * 마지막 값 유지 여부를 매 SuperFrame마다 기록한다.
     */
    fun logHeartRateDiagnostics(
        phoneTimeMillis: Long,
        timestamp: Long?,
        diagnostics: HeartRateDiagnostics
    ) {
        if (!isLogging) return

        val file = workingHeartRateDiagnosticLogFile ?: return

        val phoneTimeText = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss.SSS",
            Locale.getDefault()
        ).format(Date(phoneTimeMillis))

        val row = listOf(
            escapeCsv(phoneTimeText),
            timestamp?.toString() ?: "",
            diagnostics.analysisSegmentId.toString(),
            escapeCsv(diagnostics.processingState.name),
            escapeCsv(diagnostics.underlyingFailureReason?.name.orEmpty()),
            escapeCsv(diagnostics.message),
            diagnostics.heartRateFresh.toString(),
            diagnostics.heartRateAgeMillis?.toString() ?: "",

            escapeCsv(diagnostics.fusionSource.name),
            escapeCsv(diagnostics.fusionLog.orEmpty()),

            escapeCsv(diagnostics.irProcessingState?.name.orEmpty()),
            diagnostics.irCalculatedBpm?.toString() ?: "",
            formatDouble(diagnostics.irQualityScore, digits = 6),
            formatDouble(diagnostics.irAcceptedIntervalRatio, digits = 6),
            formatDouble(diagnostics.irRawSdsdMs, digits = 3),

            escapeCsv(diagnostics.redProcessingState?.name.orEmpty()),
            diagnostics.redCalculatedBpm?.toString() ?: "",
            formatDouble(diagnostics.redQualityScore, digits = 6),
            formatDouble(diagnostics.redAcceptedIntervalRatio, digits = 6),
            formatDouble(diagnostics.redRawSdsdMs, digits = 3),

            escapeCsv(diagnostics.combinedProcessingState?.name.orEmpty()),
            diagnostics.combinedCalculatedBpm?.toString() ?: "",
            formatDouble(diagnostics.combinedQualityScore, digits = 6),
            formatDouble(diagnostics.combinedAcceptedIntervalRatio, digits = 6),
            formatDouble(diagnostics.combinedRawSdsdMs, digits = 3),

            diagnostics.windowSampleCount.toString(),
            formatDouble(diagnostics.windowSeconds, digits = 3),
            formatDouble(diagnostics.irDcMean, digits = 3),
            formatDouble(diagnostics.irMin, digits = 3),
            formatDouble(diagnostics.irMax, digits = 3),
            formatDouble(diagnostics.acRobustAmplitude, digits = 6),
            formatDouble(diagnostics.amplitudeCoefficientOfVariation, digits = 6),
            formatDouble(diagnostics.spectralConcentration, digits = 6),
            formatDouble(diagnostics.spectralEntropy, digits = 6),
            formatDouble(diagnostics.abruptChangeRatio, digits = 6),
            formatDouble(diagnostics.selectedPeakThreshold, digits = 6),
            formatDouble(diagnostics.selectedThresholdPercent, digits = 3),
            escapeCsv(diagnostics.selectedPolarity.name),
            diagnostics.detectedPeakCount.toString(),
            diagnostics.rawIbiCount.toString(),
            diagnostics.validIbiCount.toString(),
            formatDouble(diagnostics.acceptedIntervalRatio, digits = 6),
            formatDouble(diagnostics.rawSdsdMs, digits = 3),
            formatDouble(diagnostics.rawIbiCv, digits = 6),
            formatDouble(diagnostics.physiologicalIntervalRatio, digits = 6),
            formatDouble(diagnostics.rawIntervalQualityScore, digits = 6),
            formatDouble(diagnostics.sdsdMs, digits = 3),
            formatDouble(diagnostics.qualityScore, digits = 6),
            diagnostics.calculatedBpm?.toString() ?: "",
            diagnostics.displayedBpm?.toString() ?: "",
            formatDouble(diagnostics.imuMaxDeltaG, digits = 8),
            formatDouble(diagnostics.maxRawSampleDelta, digits = 6),
            diagnostics.crcErrorCount.toString(),
            diagnostics.sequenceLossCount.toString(),
            diagnostics.estimatedLostPacketCount.toString()
        ).joinToString(",")

        file.appendText(row + "\n", Charsets.UTF_8)
    }

    /**
     * 각성지표 연산 결과를 ArousalState 전용 CSV 파일에 즉시 append한다.
     *
     * Super Frame CSV와 같은 phone_time/timestamp를 사용하면,
     * 나중에 raw frame 로그와 각성지표 로그를 쉽게 대조할 수 있다.
     */
    fun logArousalState(
        phoneTimeMillis: Long,
        timestamp: Long?,
        arousalState: ArousalState,
        complete: String,
        missPacketNum: String,
        errorLog: String
    ) {
        if (!isLogging) return

        val file = workingArousalLogFile ?: return

        val phoneTimeText = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss.SSS",
            Locale.getDefault()
        ).format(Date(phoneTimeMillis))

        val row = listOf(
            escapeCsv(phoneTimeText),
            timestamp?.toString() ?: "",

            formatDouble(arousalState.finalWakeScore),
            arousalState.isWakeTimingCandidate.toString(),

            formatDouble(arousalState.microMovementVariance, digits = 10),
            formatDouble(arousalState.microMovementScore),

            formatDouble(arousalState.rrFromPpg),
            formatDouble(arousalState.rrFromImu),
            formatDouble(arousalState.rrFinal),
            formatDouble(arousalState.rrScore),
            formatDouble(arousalState.rrRawScore),
            escapeCsv(arousalState.rrFusionSource.name),
            formatDouble(arousalState.rrFusionConfidence),
            escapeCsv(arousalState.rrFusionLog.orEmpty()),

            arousalState.rrAnalysisSegmentId.toString(),
            formatLongList(arousalState.ppgRespPeakSamplePositions),
            formatDoubleList(arousalState.ppgRespIntervalsSec),
            formatLongList(arousalState.imuRespPeakSamplePositions),
            formatDoubleList(arousalState.imuRespIntervalsSec),

            formatDouble(arousalState.rrvRmssd),
            formatDouble(arousalState.rrvRmssdMs),
            formatDouble(arousalState.rrvScore),
            escapeCsv(arousalState.rrvSource.name),
            formatDouble(arousalState.rrvQuality),
            formatDouble(arousalState.rrvFromPpgRmssdSec),
            formatDouble(arousalState.rrvFromImuRmssdSec),
            arousalState.rrvPpgIntervalCount.toString(),
            arousalState.rrvImuIntervalCount.toString(),
            formatDouble(arousalState.rrvPpgQuality),
            formatDouble(arousalState.rrvImuQuality),

            arousalState.hrBpm?.toString() ?: "",
            formatDouble(arousalState.hrGradient),
            formatDouble(arousalState.hrScore),

            formatDouble(arousalState.hrvRmssd),
            formatDouble(arousalState.hrvRmssdMs),
            formatDouble(arousalState.hrvLf),
            formatDouble(arousalState.hrvHf),
            formatDouble(arousalState.hrvLfHf),
            formatDouble(arousalState.hrvScore),
            formatDouble(arousalState.hrvQuality),
            escapeCsv(arousalState.hrvLog.orEmpty()),

            formatDouble(arousalState.skinTemperatureCelsius),
            formatDouble(arousalState.skinTemperatureGradient),
            formatDouble(arousalState.skinTemperatureScore),

            escapeCsv(complete),
            escapeCsv(missPacketNum),
            escapeCsv(errorLog),
            escapeCsv(arousalState.lastLog)
        ).joinToString(",")

        file.appendText(row + "\n", Charsets.UTF_8)
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

        file.appendText(row + "\n", Charsets.UTF_8)
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
        val arousalFile = workingArousalLogFile
        val heartRateDiagnosticFile = workingHeartRateDiagnosticLogFile

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

        if (arousalFile != null && arousalFile.exists() && arousalFile.length() > 0L) {
            copyInternalFileToDownloads(
                context = appContext,
                sourceFile = arousalFile
            )
        }

        if (
            heartRateDiagnosticFile != null &&
            heartRateDiagnosticFile.exists() &&
            heartRateDiagnosticFile.length() > 0L
        ) {
            copyInternalFileToDownloads(
                context = appContext,
                sourceFile = heartRateDiagnosticFile
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
     * 현재 작업 중인 내부 ArousalState CSV 파일 경로를 확인할 때 사용한다.
     */
    fun getWorkingArousalLogPath(): String? {
        return workingArousalLogFile?.absolutePath
    }

    /**
     * 현재 작업 중인 내부 HR 진단 CSV 파일 경로를 확인할 때 사용한다.
     */
    fun getWorkingHeartRateDiagnosticLogPath(): String? {
        return workingHeartRateDiagnosticLogFile?.absolutePath
    }

    /**
     * 저장하지 않고 현재 로그 상태를 초기화한다.
     */
    fun clear() {
        isLogging = false
        workingLogFile = null
        workingDebugLogFile = null
        workingArousalLogFile = null
        workingHeartRateDiagnosticLogFile = null
        lastSavedFilePath = null
    }

    private fun formatDouble(
        value: Double?,
        digits: Int = 6
    ): String {
        if (value == null) return ""
        return String.format(Locale.US, "%.${digits}f", value)
    }

    private fun formatLongList(
        values: List<Long>
    ): String {
        return escapeCsv(
            values.joinToString(";")
        )
    }

    private fun formatDoubleList(
        values: List<Double>,
        digits: Int = 4
    ): String {
        return escapeCsv(
            values.joinToString(";") { value ->
                String.format(Locale.US, "%.${digits}f", value)
            }
        )
    }

    private fun escapeCsv(value: String): String {
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    companion object {
        // Windows Excel이 CSV를 UTF-8로 자동 인식하도록 파일 맨 앞에 기록한다.
        private const val UTF8_BOM = "\uFEFF"
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
