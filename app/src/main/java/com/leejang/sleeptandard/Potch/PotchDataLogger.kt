package com.leejang.sleeptandard.Potch

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.BufferedOutputStream
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
 * 안정 episode 감사(audit) 로그 한 행.
 *
 * 검출된 모든 episode를 기록하며, 세션당 최대 5개 선별에서 제외된 episode도 남긴다.
 */
data class StabilityEpisodeLogRecord(
    val candidate: StableCandidateRecord,
    val detectedIndex: Int,
    val detectedEpisodeCount: Int,
    val selectedForCandidateTable: Boolean,
    val storedInCandidateTable: Boolean,
    val selectionRank: Int?,
    val selectionReason: String,
    val candidateAverageQuality: Double,
    val activeBaselines: Map<BaselineMetricType, PersonalBaselineRecord>
)

/** Potch510 Green PPG/IMU 수신 및 분석 로그를 관리한다. */
class PotchDataLogger(context: Context) {
    private val appContext = context.applicationContext
    private var isLogging = false
    private var workingPacketRawFile: File? = null
    private var packetRawOutput: BufferedOutputStream? = null
    private var workingDebugFile: File? = null
    private var workingStabilityEpisodeFile: File? = null

    var lastSavedFilePath: String? = null
        private set

    @Synchronized
    fun start() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val directory = File(appContext.filesDir, INTERNAL_LOG_DIR_NAME).apply { mkdirs() }

        closePacketRawOutput()
        workingPacketRawFile =
            File(directory, "potch_packet_raw_data_$timestamp.bin")
        packetRawOutput = FileOutputStream(workingPacketRawFile, false).buffered()

        workingDebugFile = File(directory, "potch_debug_log_$timestamp.txt")
        workingStabilityEpisodeFile =
            File(directory, "potch_stability_episode_log_$timestamp.csv")

        workingDebugFile?.writeText(
            "Potch debug log started at $timestamp\n",
            Charsets.UTF_8
        )

        workingStabilityEpisodeFile?.writeText(
            UTF8_BOM + listOf(
                "logged_at",
                "sleep_session_id",
                "episode_id",
                "detected_index",
                "detected_episode_count",
                "started_at",
                "ended_at",
                "duration_sec",
                "frame_sample_count",

                "selected_for_candidate_table",
                "stored_in_candidate_table",
                "selection_rank",
                "selection_reason",
                "candidate_average_quality",

                "rr_median_bpm",
                "rr_mean_bpm",
                "rr_sample_count",
                "rrv_median_sec",
                "rrv_mean_sec",
                "rrv_sample_count",
                "hr_median_bpm",
                "hr_mean_bpm",
                "hr_sample_count",
                "hrv_rmssd_median_sec",
                "hrv_rmssd_mean_sec",
                "hrv_sample_count",
                "hrv_lf_median",
                "hrv_hf_median",
                "hrv_lf_hf_median",
                "hrv_lf_hf_mean",
                "hrv_frequency_usable_frame_count",
                "hrv_frequency_rejected_frame_count",
                "hrv_frequency_usable_ratio",
                "hrv_frequency_rejection_summary",
                "hrv_rmssd_quality_median",
                "hrv_frequency_quality_median",
                "hrv_rmssd_stability_median",
                "hrv_lf_hf_stability_median",
                "temperature_median_c",
                "temperature_mean_c",
                "temperature_sample_count",
                "temperature_slope_median",

                "rr_quality",
                "rrv_quality",
                "hr_quality",
                "hrv_quality",
                "temperature_quality",

                "movement_stability_score",
                "respiratory_stability_score",
                "cardiac_stability_score",
                "temperature_stability_score",
                "overall_stability_score",
                "used_domain_count",

                "analysis_segment_id",
                "reconnect_count",
                "continuity_break_count",
                "packet_loss_count",
                "algorithm_version",
                "candidate_created_at",

                "rr_baseline_center",
                "rr_baseline_spread_mad",
                "rr_baseline_state",
                "rr_baseline_candidate_count",
                "rr_baseline_confidence",
                "rr_baseline_distribution_version",

                "rrv_baseline_center",
                "rrv_baseline_spread_mad",
                "rrv_baseline_state",
                "rrv_baseline_candidate_count",
                "rrv_baseline_confidence",
                "rrv_baseline_distribution_version",

                "hr_baseline_center",
                "hr_baseline_spread_mad",
                "hr_baseline_state",
                "hr_baseline_candidate_count",
                "hr_baseline_confidence",
                "hr_baseline_distribution_version",

                "hrv_baseline_center",
                "hrv_baseline_spread_mad",
                "hrv_baseline_state",
                "hrv_baseline_candidate_count",
                "hrv_baseline_confidence",
                "hrv_baseline_distribution_version",

                "hrv_lf_hf_baseline_center",
                "hrv_lf_hf_baseline_spread_mad",
                "hrv_lf_hf_baseline_state",
                "hrv_lf_hf_baseline_candidate_count",
                "hrv_lf_hf_baseline_confidence",
                "hrv_lf_hf_baseline_distribution_version",

                "temperature_baseline_center",
                "temperature_baseline_spread_mad",
                "temperature_baseline_state",
                "temperature_baseline_candidate_count",
                "temperature_baseline_confidence",
                "temperature_baseline_distribution_version"
            ).joinToString(",") + "\n",
            Charsets.UTF_8
        )

        isLogging = true
        lastSavedFilePath = null
    }

    @Synchronized
    fun startIfNeeded() {
        if (!isLogging || workingPacketRawFile == null || packetRawOutput == null) start()
    }

    @Synchronized
    fun logDebug(tag: String, message: String, level: String = "D") {
        if (!isLogging) return
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        workingDebugFile?.appendText("$time $level/$tag: $message\n", Charsets.UTF_8)
    }

    /**
     * 수신한 142-byte BLE 패킷을 앱 수신 시각과 함께 고정 길이 binary record로 기록한다.
     *
     * Record format (150 bytes):
     * - [0..7]   phone time, Unix epoch milliseconds, little-endian unsigned 64-bit
     * - [8..149] raw BLE packet, 142 bytes
     *
     * 길이가 다른 notification은 record alignment를 깨므로 이 함수에서 기록하지 않는다.
     */
    @Synchronized
    fun logRawPacket(phoneTimeMillis: Long, rawPacket: ByteArray) {
        if (!isLogging) return
        if (rawPacket.size != PACKET_SIZE_BYTES) {
            workingDebugFile?.appendText(
                "${phoneTime(phoneTimeMillis)} W/PotchDataLogger: " +
                        "raw packet 크기 불일치: " +
                        "expected=$PACKET_SIZE_BYTES actual=${rawPacket.size}\n",
                Charsets.UTF_8
            )
            return
        }

        runCatching {
            val record = ByteArray(RAW_RECORD_SIZE_BYTES)
            for (byteIndex in 0 until PHONE_TIME_SIZE_BYTES) {
                record[byteIndex] =
                    ((phoneTimeMillis ushr (byteIndex * 8)) and 0xFFL).toByte()
            }
            rawPacket.copyInto(record, destinationOffset = PHONE_TIME_SIZE_BYTES)

            packetRawOutput?.apply {
                write(record)
                // 최근 패킷이 프로세스 종료로 유실되지 않도록 즉시 flush한다.
                flush()
            }
        }.onFailure { error ->
            workingDebugFile?.appendText(
                "${phoneTime(System.currentTimeMillis())} E/PotchDataLogger: " +
                        "raw packet binary 기록 실패: ${error.message}\n",
                Charsets.UTF_8
            )
        }
    }

    /**
     * 검출된 안정 episode의 대표값과 후보 선별 결과를 새 CSV에 기록한다.
     *
     * 이 함수는 세션 종료 시 호출되므로 5개 초과 episode의 최종 선별 여부까지 기록할 수 있다.
     */
    @Synchronized
    fun logStabilityEpisode(record: StabilityEpisodeLogRecord) {
        if (!isLogging) return

        val candidate = record.candidate
        val rrBaseline = record.activeBaselines[BaselineMetricType.RR]
        val rrvBaseline = record.activeBaselines[BaselineMetricType.RRV]
        val hrBaseline = record.activeBaselines[BaselineMetricType.HR]
        val hrvBaseline = record.activeBaselines[BaselineMetricType.HRV_RMSSD]
        val hrvLfHfBaseline = record.activeBaselines[BaselineMetricType.HRV_LF_HF]
        val temperatureBaseline = record.activeBaselines[BaselineMetricType.TEMPERATURE]

        appendCsv(
            workingStabilityEpisodeFile,
            phoneTime(System.currentTimeMillis()),
            candidate.sleepSessionId,
            candidate.episodeId,
            record.detectedIndex,
            record.detectedEpisodeCount,
            phoneTime(candidate.startedAt),
            phoneTime(candidate.endedAt),
            candidate.durationSec,
            candidate.frameSampleCount,

            record.selectedForCandidateTable,
            record.storedInCandidateTable,
            record.selectionRank,
            record.selectionReason,
            record.candidateAverageQuality,

            candidate.rrMedian,
            candidate.rrMean,
            candidate.rrSampleCount,
            candidate.rrvMedian,
            candidate.rrvMean,
            candidate.rrvSampleCount,
            candidate.hrMedian,
            candidate.hrMean,
            candidate.hrSampleCount,
            candidate.hrvRmssdMedian,
            candidate.hrvRmssdMean,
            candidate.hrvSampleCount,
            candidate.hrvLfMedian,
            candidate.hrvHfMedian,
            candidate.hrvLfHfMedian,
            candidate.hrvLfHfMean,
            candidate.hrvFrequencyUsableFrameCount,
            candidate.hrvFrequencyRejectedFrameCount,
            if (candidate.frameSampleCount > 0) {
                candidate.hrvFrequencyUsableFrameCount.toDouble() / candidate.frameSampleCount.toDouble()
            } else {
                null
            },
            candidate.hrvFrequencyRejectionSummary,
            candidate.hrvRmssdQualityMedian,
            candidate.hrvFrequencyQualityMedian,
            candidate.hrvRmssdStabilityMedian,
            candidate.hrvLfHfStabilityMedian,
            candidate.temperatureMedian,
            candidate.temperatureMean,
            candidate.temperatureSampleCount,
            candidate.temperatureSlopeMedian,

            candidate.rrQuality,
            candidate.rrvQuality,
            candidate.hrQuality,
            candidate.hrvQuality,
            candidate.temperatureQuality,

            candidate.movementStabilityScore,
            candidate.respiratoryStabilityScore,
            candidate.cardiacStabilityScore,
            candidate.temperatureStabilityScore,
            candidate.overallStabilityScore,
            candidate.usedDomainCount,

            candidate.analysisSegmentId,
            candidate.reconnectCount,
            candidate.continuityBreakCount,
            candidate.packetLossCount,
            candidate.algorithmVersion,
            phoneTime(candidate.createdAt),

            rrBaseline?.center,
            rrBaseline?.spread,
            rrBaseline?.lifecycleState,
            rrBaseline?.candidateCount,
            rrBaseline?.confidence,
            rrBaseline?.distributionVersion,

            rrvBaseline?.center,
            rrvBaseline?.spread,
            rrvBaseline?.lifecycleState,
            rrvBaseline?.candidateCount,
            rrvBaseline?.confidence,
            rrvBaseline?.distributionVersion,

            hrBaseline?.center,
            hrBaseline?.spread,
            hrBaseline?.lifecycleState,
            hrBaseline?.candidateCount,
            hrBaseline?.confidence,
            hrBaseline?.distributionVersion,

            hrvBaseline?.center,
            hrvBaseline?.spread,
            hrvBaseline?.lifecycleState,
            hrvBaseline?.candidateCount,
            hrvBaseline?.confidence,
            hrvBaseline?.distributionVersion,

            hrvLfHfBaseline?.center,
            hrvLfHfBaseline?.spread,
            hrvLfHfBaseline?.lifecycleState,
            hrvLfHfBaseline?.candidateCount,
            hrvLfHfBaseline?.confidence,
            hrvLfHfBaseline?.distributionVersion,

            temperatureBaseline?.center,
            temperatureBaseline?.spread,
            temperatureBaseline?.lifecycleState,
            temperatureBaseline?.candidateCount,
            temperatureBaseline?.confidence,
            temperatureBaseline?.distributionVersion
        )
    }

    fun logConnectionEvent(event: String, message: String) {
        logDebug("PotchConnection", "event=$event, message=$message", "I")
    }

    @Synchronized
    fun stopAndSave(): String? {
        if (!isLogging) return lastSavedFilePath
        isLogging = false
        closePacketRawOutput()

        val files = listOfNotNull(
            workingPacketRawFile,
            workingDebugFile,
            workingStabilityEpisodeFile
        ).filter { it.exists() }

        val exported = files.mapNotNull { exportFileToDownloads(appContext, it) }
        lastSavedFilePath = exported.firstOrNull()
        clearWorkingReferences()
        return lastSavedFilePath
    }

    fun getWorkingLogPath(): String? = workingPacketRawFile?.absolutePath
    fun getWorkingDebugLogPath(): String? = workingDebugFile?.absolutePath
    fun getWorkingStabilityEpisodeLogPath(): String? =
        workingStabilityEpisodeFile?.absolutePath

    @Synchronized
    fun clear() {
        isLogging = false
        closePacketRawOutput()
        listOfNotNull(
            workingPacketRawFile,
            workingDebugFile,
            workingStabilityEpisodeFile
        ).forEach { runCatching { it.delete() } }
        clearWorkingReferences()
        lastSavedFilePath = null
    }

    private fun clearWorkingReferences() {
        closePacketRawOutput()
        workingPacketRawFile = null
        workingDebugFile = null
        workingStabilityEpisodeFile = null
    }

    private fun closePacketRawOutput() {
        runCatching { packetRawOutput?.flush() }
        runCatching { packetRawOutput?.close() }
        packetRawOutput = null
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
        private const val PHONE_TIME_SIZE_BYTES = Long.SIZE_BYTES
        private const val PACKET_SIZE_BYTES = 142
        private const val RAW_RECORD_SIZE_BYTES = PHONE_TIME_SIZE_BYTES + PACKET_SIZE_BYTES

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
            file.extension.lowercase() in setOf("bin", "csv", "txt")

        private fun mimeType(file: File): String = when (file.extension.lowercase()) {
            "bin" -> "application/octet-stream"
            "csv" -> "text/csv"
            else -> "text/plain"
        }
    }
}
