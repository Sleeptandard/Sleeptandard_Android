package com.leejang.sleeptandard.Potch

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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
class PotchDataLogger(context: Context, private val closedFileExporter: ((File) -> Unit)? = null) {
    private val appContext = context.applicationContext
    private data class RawOutput(val session: AlarmLogSession, val file: File, val output: BufferedOutputStream)
    private val rawOutputs = mutableMapOf<String, RawOutput>()
    private val preferences = appContext.getSharedPreferences("potch_log_files", Context.MODE_PRIVATE)
    private var workingDebugFile: File? = null
    private var workingStabilityEpisodeFile: File? = null
    private var stabilityLogSession: AlarmLogSession? = null
    private val stabilityRows = linkedMapOf<String, String>()

    val stabilitySessionId: String? get() = stabilityLogSession?.id
    val stabilityStartedAtMillis: Long? get() = stabilityLogSession?.startedAtMillis

    init {
        preferences.getString("ble_file", null)?.let { name ->
            workingDebugFile = File(logDirectory(), name).takeIf {
                it.exists() && !PotchLogExporter.isClosed(appContext, name)
            }
        }
        val stabilityId = preferences.getString("stability_id", null)
        AlarmLogSessionStore(appContext).load().find { it.id == stabilityId }?.let {
            val file = alarmFile(it, "potch_stability_episode_log", "csv")
            if (file.exists() && !PotchLogExporter.isClosed(appContext, file.name)) openStability(it)
        }
        if (closedFileExporter == null) PotchLogExporter.retryPending(appContext)
    }

    var lastSavedFilePath: String? = null
        private set

    @Synchronized
    fun startBleLogging() {
        if (workingDebugFile != null) return
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
        workingDebugFile = uniqueTarget(logDirectory(), "potch_debug_log_$timestamp.txt")
        workingDebugFile?.writeText("Potch BLE debug log started at $timestamp\n", Charsets.UTF_8)
        check(preferences.edit().putString("ble_file", workingDebugFile!!.name).commit())
    }

    private fun logDirectory() = File(appContext.filesDir, INTERNAL_LOG_DIR_NAME).apply { mkdirs() }

    private fun alarmFile(session: AlarmLogSession, prefix: String, extension: String): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date(session.startedAtMillis))
        return File(logDirectory(), "${prefix}_${timestamp}_${session.id}.$extension")
    }

    private fun openStability(session: AlarmLogSession) {
        stabilityLogSession = session
        workingStabilityEpisodeFile = alarmFile(session, "potch_stability_episode_log", "csv")
        check(preferences.edit().putString("stability_id", session.id).commit())
        stabilityRows.clear()
        if (workingStabilityEpisodeFile!!.length() > 0L) {
            workingStabilityEpisodeFile!!.useLines { lines ->
                lines.drop(1).forEach { line ->
                    line.split(',', limit = 4).getOrNull(2)?.let { stabilityRows[it] = line }
                }
            }
            return
        }
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

    }

    /** Called only for alarm lifecycle changes, never for BLE reconnect or monitoring start. */
    @Synchronized
    fun syncAlarmFiles(sessions: List<AlarmLogSession>, nowMillis: Long = System.currentTimeMillis()) {
        val desiredRaw = sessions.filter { it.recordsRaw(nowMillis) }.associateBy { it.id }
        rawOutputs.keys.toList().filter { it !in desiredRaw }.forEach { id ->
            rawOutputs.remove(id)?.let { it.output.close(); exportClosed(it.file) }
        }
        desiredRaw.forEach { (id, session) ->
            val existing = rawOutputs[id]
            if (existing != null) {
                rawOutputs[id] = existing.copy(session = session)
            } else {
                val file = alarmFile(session, "potch_packet_raw_data", "bin")
                // Append to the recovered session; each record retains its original phone timestamp.
                // A killed process can leave a partial final record; retain every complete 150-byte record.
                if (file.exists() && file.length() % RAW_RECORD_SIZE_BYTES != 0L) {
                    RandomAccessFile(file, "rw").use { raw ->
                        raw.setLength(raw.length() / RAW_RECORD_SIZE_BYTES * RAW_RECORD_SIZE_BYTES)
                    }
                }
                rawOutputs[id] = RawOutput(session, file, FileOutputStream(file, true).buffered())
            }
        }
        val desiredStability = sessions.lastOrNull { it.recordsStability }
        if (stabilityLogSession?.id != desiredStability?.id) {
            workingStabilityEpisodeFile?.let(::exportClosed)
            workingStabilityEpisodeFile = null
            stabilityLogSession = null
            stabilityRows.clear()
            check(preferences.edit().remove("stability_id").commit())
            desiredStability?.let(::openStability)
        }
        // Closed/expired sessions may have been restored with no in-memory file handles.
        sessions.forEach { session ->
            if (!session.recordsRaw(nowMillis)) exportClosed(alarmFile(session, "potch_packet_raw_data", "bin"))
            if (!session.recordsStability) exportClosed(alarmFile(session, "potch_stability_episode_log", "csv"))
        }
    }

    @Synchronized
    fun logDebug(tag: String, message: String, level: String = "D") {
        if (workingDebugFile == null) return
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        runCatching { workingDebugFile?.appendText("$time $level/$tag: $message\n", Charsets.UTF_8) }
            .onFailure { Log.e("PotchDataLogger", "BLE debug log write failed", it) }
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
        if (rawOutputs.isEmpty()) return
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

            rawOutputs.values.forEach { raw ->
                if (raw.session.recordsRaw(phoneTimeMillis)) {
                    raw.output.write(record)
                    raw.output.flush()
                }
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
        val candidate = record.candidate
        val session = stabilityLogSession ?: return
        if (candidate.startedAt < session.startedAtMillis) return
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
    fun stopBleAndSave(reason: String): String? {
        logConnectionEvent("finished", reason)
        val file = workingDebugFile
        workingDebugFile = null
        file?.let(::exportClosed)
        check(preferences.edit().remove("ble_file").commit())
        return lastSavedFilePath
    }

    fun getWorkingLogPath(): String? = rawOutputs.values.lastOrNull()?.file?.absolutePath
    fun getWorkingDebugLogPath(): String? = workingDebugFile?.absolutePath
    fun getWorkingStabilityEpisodeLogPath(): String? =
        workingStabilityEpisodeFile?.absolutePath

    @Synchronized
    fun closeHandlesForRecovery() {
        rawOutputs.values.forEach { runCatching { it.output.close() } }
        rawOutputs.clear()
        // Persistent identities remain: a service restart appends to these same files.
    }

    private fun exportClosed(file: File) {
        if (!file.exists()) return
        lastSavedFilePath = file.absolutePath
        closedFileExporter?.invoke(file) ?: PotchLogExporter.enqueue(appContext, file.name)
    }

    private fun appendCsv(file: File?, vararg values: Any?) {
        if (file == null) return
        // Upsert episode snapshots so process death does not lose all completed episodes,
        // while later candidate selection updates still leave one row per episode.
        stabilityRows[values[2].toString()] = values.joinToString(",") { csv(it) }
        runCatching {
            val header = file.bufferedReader(Charsets.UTF_8).use { it.readLine() }
            val temporary = File(file.parentFile, "${file.name}.pending")
            temporary.writeText(header + "\n" + stabilityRows.values.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.onFailure { Log.e("PotchDataLogger", "Stability snapshot write failed; previous CSV retained", it) }
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
