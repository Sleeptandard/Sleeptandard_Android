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
    private var workingArousalFile: File? = null
    private var workingHeartRateFile: File? = null
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
        workingArousalFile = File(directory, "potch_arousal_state_log_$timestamp.csv")
        workingHeartRateFile = File(directory, "potch_hr_diagnostic_log_$timestamp.csv")
        workingStabilityEpisodeFile =
            File(directory, "potch_stability_episode_log_$timestamp.csv")

        workingDebugFile?.writeText(
            "Potch debug log started at $timestamp\n",
            Charsets.UTF_8
        )

        workingArousalFile?.writeText(
            UTF8_BOM + listOf(
                "phone_time",
                "timestamp",
                "final_wake_score",
                "final_wake_confidence",
                "final_wake_coverage",
                "used_arousal_domain_count",
                "movement_domain_score",
                "movement_domain_confidence",
                "movement_domain_coverage",
                "movement_domain_usable",
                "movement_domain_composition",
                "respiratory_domain_score",
                "respiratory_domain_confidence",
                "respiratory_domain_coverage",
                "respiratory_domain_usable",
                "respiratory_domain_composition",
                "cardiac_domain_score",
                "cardiac_domain_confidence",
                "cardiac_domain_coverage",
                "cardiac_domain_usable",
                "cardiac_domain_composition",
                "temperature_domain_score",
                "temperature_domain_confidence",
                "temperature_domain_coverage",
                "temperature_domain_usable",
                "temperature_domain_composition",
                "wake_candidate_hold_seconds",
                "wake_current_condition_passed",
                "wake_persistence_window_seconds",
                "wake_persistence_required_pass_seconds",
                "wake_persistence_observed_seconds",
                "wake_persistence_passed_seconds",
                "wake_persistence_failed_seconds",
                "wake_persistence_pass_ratio_percent",
                "wake_decision_reason",
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
                "hrv_score_composition",
                "hrv_rmssd_score",
                "hrv_rmssd_quality",
                "hrv_rmssd_ibi_count",
                "hrv_frequency_score",
                "hrv_frequency_quality",
                "hrv_frequency_ibi_count",
                "hrv_frequency_usable",
                "hrv_frequency_status",
                "hrv_frequency_rejection_reasons",
                "hrv_frequency_observed_seconds",
                "hrv_frequency_raw_ibi_count",
                "hrv_frequency_cleaned_ibi_count",
                "hrv_frequency_resampled_count",
                "hrv_frequency_ppg_signal_quality",
                "hrv_frequency_rr_bpm",
                "skin_temperature_celsius",
                "skin_temperature_gradient",
                "skin_temperature_score",
                "micro_evidence_score",
                "micro_evidence_confidence",
                "micro_evidence_coverage",
                "micro_evidence_usable",
                "micro_baseline_source",
                "micro_baseline_center",
                "micro_baseline_spread",
                "micro_signed_distance",
                "micro_normalized_distance",
                "micro_baseline_score",
                "micro_trend_score",
                "micro_signal_quality",
                "micro_evidence_reasons",
                "micro_evidence_log",
                "rr_evidence_score",
                "rr_evidence_confidence",
                "rr_evidence_coverage",
                "rr_evidence_usable",
                "rr_baseline_source",
                "rr_baseline_center",
                "rr_baseline_spread",
                "rr_signed_distance",
                "rr_normalized_distance",
                "rr_baseline_score",
                "rr_trend_score",
                "rr_signal_quality",
                "rr_evidence_reasons",
                "rr_evidence_log",
                "rrv_evidence_score",
                "rrv_evidence_confidence",
                "rrv_evidence_coverage",
                "rrv_evidence_usable",
                "rrv_baseline_source",
                "rrv_baseline_center",
                "rrv_baseline_spread",
                "rrv_signed_distance",
                "rrv_normalized_distance",
                "rrv_baseline_score",
                "rrv_trend_score",
                "rrv_signal_quality",
                "rrv_evidence_reasons",
                "rrv_evidence_log",
                "hr_evidence_score",
                "hr_evidence_confidence",
                "hr_evidence_coverage",
                "hr_evidence_usable",
                "hr_baseline_source",
                "hr_baseline_center",
                "hr_baseline_spread",
                "hr_signed_distance",
                "hr_normalized_distance",
                "hr_baseline_score",
                "hr_trend_score",
                "hr_signal_quality",
                "hr_evidence_reasons",
                "hr_evidence_log",
                "hrv_evidence_score",
                "hrv_evidence_confidence",
                "hrv_evidence_coverage",
                "hrv_evidence_usable",
                "hrv_baseline_source",
                "hrv_baseline_center",
                "hrv_baseline_spread",
                "hrv_signed_distance",
                "hrv_normalized_distance",
                "hrv_baseline_score",
                "hrv_trend_score",
                "hrv_signal_quality",
                "hrv_evidence_reasons",
                "hrv_evidence_log",
                "temperature_evidence_score",
                "temperature_evidence_confidence",
                "temperature_evidence_coverage",
                "temperature_evidence_usable",
                "temperature_baseline_source",
                "temperature_baseline_center",
                "temperature_baseline_spread",
                "temperature_signed_distance",
                "temperature_normalized_distance",
                "temperature_baseline_score",
                "temperature_trend_score",
                "temperature_signal_quality",
                "temperature_evidence_reasons",
                "temperature_evidence_log",
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
            arousalState.finalWakeConfidence,
            arousalState.finalWakeCoverage,
            arousalState.usedArousalDomainCount,
            *arousalState.movementDomainEvidence.toCsvValues(),
            *arousalState.respiratoryDomainEvidence.toCsvValues(),
            *arousalState.cardiacDomainEvidence.toCsvValues(),
            *arousalState.temperatureDomainEvidence.toCsvValues(),
            arousalState.wakeCandidateHoldSeconds,
            arousalState.wakeCurrentConditionPassed,
            arousalState.wakePersistenceWindowSeconds,
            arousalState.wakePersistenceRequiredPassSeconds,
            arousalState.wakePersistenceObservedSeconds,
            arousalState.wakePersistencePassedSeconds,
            arousalState.wakePersistenceFailedSeconds,
            arousalState.wakePersistencePassRatio,
            arousalState.wakeDecisionReason,
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
            arousalState.hrvScoreComposition,
            arousalState.hrvRmssdScore,
            arousalState.hrvRmssdQuality,
            arousalState.hrvRmssdIbiCount,
            arousalState.hrvFrequencyScore,
            arousalState.hrvFrequencyQuality,
            arousalState.hrvFrequencyIbiCount,
            arousalState.hrvFrequencyUsable,
            arousalState.hrvFrequencyStatus.state,
            arousalState.hrvFrequencyRejectionReasons,
            arousalState.hrvFrequencyObservedSeconds,
            arousalState.hrvFrequencyRawIbiCount,
            arousalState.hrvFrequencyCleanedIbiCount,
            arousalState.hrvFrequencyResampledCount,
            arousalState.hrvFrequencyPpgSignalQuality,
            arousalState.hrvFrequencyRespiratoryRateBpm,
            arousalState.skinTemperatureCelsius,
            arousalState.skinTemperatureGradient,
            arousalState.skinTemperatureScore,
            *arousalState.microEvidence.toCsvValues(),
            *arousalState.rrEvidence.toCsvValues(),
            *arousalState.rrvEvidence.toCsvValues(),
            *arousalState.hrEvidence.toCsvValues(),
            *arousalState.hrvEvidence.toCsvValues(),
            *arousalState.temperatureEvidence.toCsvValues(),
            complete,
            missPacketNum,
            errorLog,
            arousalState.lastLog
        )
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
            workingArousalFile,
            workingHeartRateFile,
            workingStabilityEpisodeFile
        ).filter { it.exists() }

        val exported = files.mapNotNull { exportFileToDownloads(appContext, it) }
        lastSavedFilePath = exported.firstOrNull()
        clearWorkingReferences()
        return lastSavedFilePath
    }

    fun getWorkingLogPath(): String? = workingPacketRawFile?.absolutePath
    fun getWorkingDebugLogPath(): String? = workingDebugFile?.absolutePath
    fun getWorkingArousalLogPath(): String? = workingArousalFile?.absolutePath
    fun getWorkingHeartRateDiagnosticLogPath(): String? = workingHeartRateFile?.absolutePath
    fun getWorkingStabilityEpisodeLogPath(): String? =
        workingStabilityEpisodeFile?.absolutePath

    @Synchronized
    fun clear() {
        isLogging = false
        closePacketRawOutput()
        listOfNotNull(
            workingPacketRawFile,
            workingDebugFile,
            workingArousalFile,
            workingHeartRateFile,
            workingStabilityEpisodeFile
        ).forEach { runCatching { it.delete() } }
        clearWorkingReferences()
        lastSavedFilePath = null
    }

    private fun clearWorkingReferences() {
        closePacketRawOutput()
        workingPacketRawFile = null
        workingDebugFile = null
        workingArousalFile = null
        workingHeartRateFile = null
        workingStabilityEpisodeFile = null
    }

    private fun closePacketRawOutput() {
        runCatching { packetRawOutput?.flush() }
        runCatching { packetRawOutput?.close() }
        packetRawOutput = null
    }

    private fun DomainEvidence.toCsvValues(): Array<Any?> = arrayOf(
        score,
        confidence,
        coverage,
        usable,
        composition
    )

    private fun MetricEvidence.toCsvValues(): Array<Any?> = arrayOf(
        score,
        confidence,
        coverage,
        usable,
        baselineSource,
        baselineCenter,
        baselineSpread,
        signedDistance,
        normalizedDistance,
        baselineScore,
        trendScore,
        signalQuality,
        reasons,
        log
    )

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