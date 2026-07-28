package com.leejang.sleeptandard.Potch

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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

/** Potch510 Green PPG/IMU 수신 및 분석 로그를 관리한다. */
class PotchDataLogger(context: Context) {
    private val appContext = context.applicationContext
    private var isLogging = false
    private var workingBurstFile: File? = null
    private var workingDebugFile: File? = null
    private var workingArousalFile: File? = null
    private var workingHeartRateFile: File? = null

    var lastSavedFilePath: String? = null
        private set

    @Synchronized
    fun start() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val directory = File(appContext.filesDir, INTERNAL_LOG_DIR_NAME).apply { mkdirs() }

        workingBurstFile = File(directory, "potch_burst_log_$timestamp.csv")
        workingDebugFile = File(directory, "potch_debug_log_$timestamp.txt")
        workingArousalFile = File(directory, "potch_arousal_state_log_$timestamp.csv")
        workingHeartRateFile = File(directory, "potch_hr_diagnostic_log_$timestamp.csv")

        workingBurstFile?.writeText(
            UTF8_BOM + listOf(
                "phone_time",
                "timestamp",
                "sequence_start",
                "sequence_end",
                "packet_count",
                "burst_hex",
                "complete",
                "miss_packet_num",
                "error_log"
            ).joinToString(",") + "\n",
            Charsets.UTF_8
        )

        workingDebugFile?.writeText(
            "Potch debug log started at $timestamp\n",
            Charsets.UTF_8
        )

        workingArousalFile?.writeText(
            UTF8_BOM + listOf(
                "phone_time",
                "timestamp",
                "final_wake_score",
                "is_wake_timing_candidate",
                "micro_movement_variance",
                "micro_movement_score",
                "rr_from_green_ppg",
                "rr_from_imu",
                "rr_final",
                "rr_score",
                "rr_raw_score",
                "rr_source",
                "rr_confidence",
                "rr_log",
                "rrv_rmssd_sec",
                "rrv_score",
                "rrv_source",
                "hr_bpm",
                "hr_gradient",
                "hr_score",
                "hrv_rmssd_sec",
                "hrv_lf",
                "hrv_hf",
                "hrv_lf_hf",
                "hrv_score",
                "hrv_quality",
                "skin_temperature_celsius",
                "skin_temperature_gradient",
                "skin_temperature_score",
                "complete",
                "miss_packet_num",
                "error_log",
                "last_log"
            ).joinToString(",") + "\n",
            Charsets.UTF_8
        )

        workingHeartRateFile?.writeText(
            UTF8_BOM + listOf(
                "phone_time",
                "timestamp",
                "analysis_segment_id",
                "processing_state",
                "underlying_failure_reason",
                "message",
                "heart_rate_fresh",
                "heart_rate_age_ms",
                "source",
                "source_log",
                "green_dc_mean",
                "green_min",
                "green_max",
                "ac_robust_amplitude",
                "selected_peak_threshold",
                "selected_polarity",
                "detected_peak_count",
                "raw_ibi_count",
                "valid_ibi_count",
                "accepted_interval_ratio",
                "raw_sdsd_ms",
                "quality_score",
                "calculated_bpm",
                "displayed_bpm",
                "window_sample_count",
                "window_seconds",
                "imu_max_delta_g",
                "imu_p95_delta_g",
                "imu_motion_exceedance_ratio",
                "max_raw_sample_delta",
                "crc_error_count",
                "sequence_loss_count",
                "estimated_lost_packet_count"
            ).joinToString(",") + "\n",
            Charsets.UTF_8
        )

        isLogging = true
        lastSavedFilePath = null
    }

    @Synchronized
    fun startIfNeeded() {
        if (!isLogging || workingBurstFile == null) start()
    }

    @Synchronized
    fun logDebug(tag: String, message: String, level: String = "D") {
        if (!isLogging) return
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        workingDebugFile?.appendText("$time $level/$tag: $message\n", Charsets.UTF_8)
    }

    @Synchronized
    fun logSuperFrame(
        phoneTimeMillis: Long,
        timestamp: Long,
        sequenceStart: Int,
        sequenceEnd: Int,
        packetCount: Int,
        burstHex: String,
        complete: String,
        missPacketNum: String,
        errorLog: String
    ) {
        if (!isLogging) return
        appendCsv(
            workingBurstFile,
            phoneTime(phoneTimeMillis),
            timestamp,
            sequenceStart,
            sequenceEnd,
            packetCount,
            burstHex,
            complete,
            missPacketNum,
            errorLog
        )
    }

    @Synchronized
    fun logHeartRateDiagnostics(
        phoneTimeMillis: Long,
        timestamp: Long,
        diagnostics: HeartRateDiagnostics
    ) {
        if (!isLogging) return
        appendCsv(
            workingHeartRateFile,
            phoneTime(phoneTimeMillis),
            timestamp,
            diagnostics.analysisSegmentId,
            diagnostics.processingState,
            diagnostics.underlyingFailureReason,
            diagnostics.message,
            diagnostics.heartRateFresh,
            diagnostics.heartRateAgeMillis,
            diagnostics.source,
            diagnostics.sourceLog,
            diagnostics.greenDcMean,
            diagnostics.greenMin,
            diagnostics.greenMax,
            diagnostics.acRobustAmplitude,
            diagnostics.selectedPeakThreshold,
            diagnostics.selectedPolarity,
            diagnostics.detectedPeakCount,
            diagnostics.rawIbiCount,
            diagnostics.validIbiCount,
            diagnostics.acceptedIntervalRatio,
            diagnostics.rawSdsdMs,
            diagnostics.qualityScore,
            diagnostics.calculatedBpm,
            diagnostics.displayedBpm,
            diagnostics.windowSampleCount,
            diagnostics.windowSeconds,
            diagnostics.imuMaxDeltaG,
            diagnostics.imuP95DeltaG,
            diagnostics.imuMotionExceedanceRatio,
            diagnostics.maxRawSampleDelta,
            diagnostics.crcErrorCount,
            diagnostics.sequenceLossCount,
            diagnostics.estimatedLostPacketCount
        )
    }

    @Synchronized
    fun logArousalState(
        phoneTimeMillis: Long,
        timestamp: Long,
        arousalState: ArousalState,
        complete: String,
        missPacketNum: String,
        errorLog: String
    ) {
        if (!isLogging) return
        appendCsv(
            workingArousalFile,
            phoneTime(phoneTimeMillis),
            timestamp,
            arousalState.finalWakeScore,
            arousalState.isWakeTimingCandidate,
            arousalState.microMovementVariance,
            arousalState.microMovementScore,
            arousalState.rrFromPpg,
            arousalState.rrFromImu,
            arousalState.rrFinal,
            arousalState.rrScore,
            arousalState.rrRawScore,
            arousalState.rrFusionSource,
            arousalState.rrFusionConfidence,
            arousalState.rrFusionLog,
            arousalState.rrvRmssd,
            arousalState.rrvScore,
            arousalState.rrvSource,
            arousalState.hrBpm,
            arousalState.hrGradient,
            arousalState.hrScore,
            arousalState.hrvRmssd,
            arousalState.hrvLf,
            arousalState.hrvHf,
            arousalState.hrvLfHf,
            arousalState.hrvScore,
            arousalState.hrvQuality,
            arousalState.skinTemperatureCelsius,
            arousalState.skinTemperatureGradient,
            arousalState.skinTemperatureScore,
            complete,
            missPacketNum,
            errorLog,
            arousalState.lastLog
        )
    }

    fun logConnectionEvent(event: String, message: String) {
        logDebug("PotchConnection", "event=$event, message=$message", "I")
    }

    @Synchronized
    fun stopAndSave(): String? {
        if (!isLogging) return lastSavedFilePath
        isLogging = false

        val files = listOfNotNull(
            workingBurstFile,
            workingDebugFile,
            workingArousalFile,
            workingHeartRateFile
        ).filter { it.exists() }

        val exported = files.mapNotNull { exportFileToDownloads(appContext, it) }
        lastSavedFilePath = exported.firstOrNull()
        clearWorkingReferences()
        return lastSavedFilePath
    }

    fun getWorkingLogPath(): String? = workingBurstFile?.absolutePath
    fun getWorkingDebugLogPath(): String? = workingDebugFile?.absolutePath
    fun getWorkingArousalLogPath(): String? = workingArousalFile?.absolutePath
    fun getWorkingHeartRateDiagnosticLogPath(): String? = workingHeartRateFile?.absolutePath

    @Synchronized
    fun clear() {
        isLogging = false
        listOfNotNull(workingBurstFile, workingDebugFile, workingArousalFile, workingHeartRateFile)
            .forEach { runCatching { it.delete() } }
        clearWorkingReferences()
        lastSavedFilePath = null
    }

    private fun clearWorkingReferences() {
        workingBurstFile = null
        workingDebugFile = null
        workingArousalFile = null
        workingHeartRateFile = null
    }

    private fun appendCsv(file: File?, vararg values: Any?) {
        file?.appendText(values.joinToString(",") { csv(it) } + "\n", Charsets.UTF_8)
    }

    private fun csv(value: Any?): String {
        val text = value?.toString() ?: ""
        if (text.none { it == ',' || it == '"' || it == '\n' || it == '\r' }) return text
        return "\"${text.replace("\"", "\"\"")}\""
    }

    private fun phoneTime(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(millis))

    companion object {
        private const val INTERNAL_LOG_DIR_NAME = "PotchLogs"
        private const val DOWNLOAD_SUBDIRECTORY = "PotchLogs"
        private const val UTF8_BOM = "\uFEFF"

        fun listInternalLogFiles(context: Context): List<InternalPotchLogFile> {
            val directory = File(context.applicationContext.filesDir, INTERNAL_LOG_DIR_NAME)
            return directory.listFiles()
                ?.filter { it.isFile && isSupported(it) }
                ?.sortedByDescending { it.lastModified() }
                ?.map {
                    InternalPotchLogFile(
                        name = it.name,
                        absolutePath = it.absolutePath,
                        sizeBytes = it.length(),
                        lastModifiedMillis = it.lastModified()
                    )
                }
                .orEmpty()
        }

        fun exportInternalLogFilesToDownloads(
            context: Context,
            fileNames: List<String>
        ): List<String> {
            val directory = File(context.applicationContext.filesDir, INTERNAL_LOG_DIR_NAME)
            if (!directory.isDirectory) return emptyList()

            val selected = if (fileNames.isEmpty()) {
                directory.listFiles()?.filter { it.isFile && isSupported(it) }.orEmpty()
            } else {
                fileNames.distinct().mapNotNull { name ->
                    val file = File(directory, name)
                    val inside = runCatching { file.canonicalFile.parentFile == directory.canonicalFile }
                        .getOrDefault(false)
                    file.takeIf { inside && it.isFile && isSupported(it) }
                }
            }
            return selected.mapNotNull { exportFileToDownloads(context.applicationContext, it) }
        }

        private fun exportFileToDownloads(context: Context, source: File): String? {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, source.name)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType(source))
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/" + DOWNLOAD_SUBDIRECTORY
                    )
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                try {
                    resolver.openOutputStream(uri)?.use { output ->
                        FileInputStream(source).use { input -> input.copyTo(output) }
                    } ?: return null
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    uri.toString()
                } catch (_: Exception) {
                    resolver.delete(uri, null, null)
                    null
                }
            } else {
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val directory = File(downloads, DOWNLOAD_SUBDIRECTORY).apply { mkdirs() }
                val target = uniqueTarget(directory, source.name)
                runCatching {
                    FileInputStream(source).use { input ->
                        FileOutputStream(target).use { output -> input.copyTo(output) }
                    }
                    target.absolutePath
                }.getOrNull()
            }
        }

        private fun uniqueTarget(directory: File, name: String): File {
            var candidate = File(directory, name)
            if (!candidate.exists()) return candidate
            val base = name.substringBeforeLast('.', name)
            val extension = name.substringAfterLast('.', "")
            var index = 1
            while (candidate.exists()) {
                val nextName = if (extension.isEmpty()) "$base($index)" else "$base($index).$extension"
                candidate = File(directory, nextName)
                index += 1
            }
            return candidate
        }

        private fun isSupported(file: File): Boolean =
            file.extension.lowercase() in setOf("csv", "txt")

        private fun mimeType(file: File): String =
            if (file.extension.equals("csv", true)) "text/csv" else "text/plain"
    }
}
