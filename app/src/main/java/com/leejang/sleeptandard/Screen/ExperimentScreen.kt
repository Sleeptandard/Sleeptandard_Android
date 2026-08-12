package com.leejang.sleeptandard.Screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leejang.sleeptandard.Potch.ArousalState
import com.leejang.sleeptandard.Potch.BaselineLifecycleState
import com.leejang.sleeptandard.Potch.BaselineMetricType
import com.leejang.sleeptandard.Potch.DomainEvidence
import com.leejang.sleeptandard.Potch.HeartRateGraphData
import com.leejang.sleeptandard.Potch.HeartRatePeakPolarity
import com.leejang.sleeptandard.Potch.InternalPotchLogFile
import com.leejang.sleeptandard.Potch.MetricCalculationState
import com.leejang.sleeptandard.Potch.MetricCalculationStatus
import com.leejang.sleeptandard.Potch.MetricEvidence
import com.leejang.sleeptandard.Potch.PpgRespirationGraphData
import com.leejang.sleeptandard.Potch.PotchBleState
import com.leejang.sleeptandard.Potch.PotchBleViewModel
import com.leejang.sleeptandard.Potch.SensorData
import com.leejang.sleeptandard.Potch.SleepStagePotch
import com.leejang.sleeptandard.Potch.StabilityEpisodePhase
import com.leejang.sleeptandard.Potch.StabilityState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun ExperimentScreen(
    viewModel: PotchBleViewModel = viewModel()
) {
    val bleState by viewModel.bleState.collectAsState()
    val processorState by viewModel.processorState.collectAsState()
    val internalLogFiles by viewModel.internalLogFiles.collectAsState()
    val lastExportMessage by viewModel.lastExportMessage.collectAsState()

    val selectedLogNames = remember { mutableStateListOf<String>() }
    val greenSamples = remember { mutableStateListOf<Int>() }

    val sensorData = processorState.lastParsedData
    val arousalState = processorState.arousalState
    val stabilityState = processorState.stabilityState

    val sleepStage by viewModel.sleepStage.collectAsState()
    // ── 수면 단계 추론 정보 가공 ────────────────────────────────────
    val rawFrames = processorState.parsedSuperFrameCount
    val currentEpochs = (rawFrames / 30).coerceAtMost(5) // 30 SuperFrame = 1 Epoch (100Hz)
    val isWarmingUp = sleepStage == SleepStagePotch.UNKNOWN && currentEpochs < 5

    // 단계별 UI 스타일 (네온 컬러 테마)
    val stageColor = when (sleepStage) {
        SleepStagePotch.WAKE -> Color(0xFFFF922E)      // 오렌지
        SleepStagePotch.LIGHT -> Color(0xFF33B3FF)     // 스카이블루
        SleepStagePotch.DEEP -> Color(0xFF9E4BFF)      // 바이올렛
        SleepStagePotch.REM -> Color(0xFF00E676)       // 그린
        else -> Color(0xFF88888F)                      // 그레이 (UNKNOWN)
    }

    val stageBackground = when (sleepStage) {
        SleepStagePotch.WAKE -> Color(0xFF422E1A)
        SleepStagePotch.LIGHT -> Color(0xFF1E3245)
        SleepStagePotch.DEEP -> Color(0xFF2E1E45)
        SleepStagePotch.REM -> Color(0xFF1B3D2B)
        else -> Color(0xFF22222A)
    }

    val stageName = when (sleepStage) {
        SleepStagePotch.WAKE -> "깨어남 (WAKE)"
        SleepStagePotch.LIGHT -> "얕은 수면 (LIGHT)"
        SleepStagePotch.DEEP -> "깊은 수면 (DEEP)"
        SleepStagePotch.REM -> "꿈 수면 (REM)"
        else -> if (isWarmingUp) "추론 준비 중" else "측정 대기"
    }

    val stageDesc = when (sleepStage) {
        SleepStagePotch.WAKE -> "뒤척임이 관찰되거나 깨어있는 상태입니다."
        SleepStagePotch.LIGHT -> "피로를 회복하는 얕은 잠 단계입니다."
        SleepStagePotch.DEEP -> "뇌와 신체가 깊게 휴식하는 수면 단계입니다."
        SleepStagePotch.REM -> "기억을 정리하는 꿈 수면 단계입니다."
        else -> if (isWarmingUp) {
            "2.5분(5 에포크) 버퍼가 채워진 후 추론이 시작됩니다.\n현재 진행도: $currentEpochs/5 에포크 (${rawFrames % 30}/30)"
        } else {
            "센서 연결을 완료하면 수면 추론이 시작됩니다."
        }
    }
    // ──────────────────────────────────────────────────────────────


    LaunchedEffect(sensorData?.timestamp, sensorData?.sequenceEnd) {
        val samples = sensorData?.ppgData?.let(::decodeGreenPpg) ?: IntArray(0)
        if (samples.isNotEmpty()) {
            greenSamples.addAll(samples.toList())
            while (greenSamples.size > GREEN_GRAPH_MAX_SAMPLES) {
                greenSamples.removeAt(0)
            }
        }
    }

    LaunchedEffect(processorState.parsedSuperFrameCount) {
        if (processorState.parsedSuperFrameCount == 0) greenSamples.clear()
    }

    LaunchedEffect(Unit) {
        viewModel.refreshInternalLogFiles()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C121D))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 38.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 🌟 실시간 수면 단계 추론 결과 메인 카드
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(stageBackground)
                .border(
                    width = 1.5.dp,
                    color = stageColor.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(32.dp)
                )
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 활성화 인디케이터 펄스 효과 대용 닷
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(stageColor)
                )

                Spacer(Modifier.width(10.dp))

                Text(
                    text = "실시간 수면 단계 추론 (AI)",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = stageName,
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = stageDesc,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium
            )

            // 준비 상태 게이지 바
            if (isWarmingUp) {
                Spacer(Modifier.height(16.dp))
                val progress = (rawFrames.toFloat() / 150f).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(6.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(stageColor)
                    )
                }
            }
        }

        Text(
            text = "Potch510 Experiment",
            color = Color.White,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Green PPG 128 Hz · 6축 IMU 64 Hz · 142 B × 8 Burst",
            color = Color(0xFFA9B5C7),
            fontSize = 13.sp
        )

        ConnectionSection(
            bleState = bleState,
            onConnect = viewModel::startScan,
            onDisconnect = viewModel::stopReconnectAndSaveLog,
            onTrigger = viewModel::triggerLedFlash,
            onReset = viewModel::resetProcessor
        )

        SensorSummarySection(
            sensorData = sensorData,
            heartRateBpm = processorState.heartRateBpm,
            heartRateFresh = processorState.heartRateFresh,
            movementSummary = sensorData?.imuData?.let(::calculateImuSummary),
            greenMax = processorState.lastGreenMax
        )

        PacketSection(
            sensorData = sensorData,
            parsedBurstCount = processorState.parsedSuperFrameCount,
            totalPacketCount = processorState.totalMiniPackets,
            validPacketCount = processorState.validMiniPackets,
            crcErrorCount = processorState.crcErrorCount,
            sequenceErrorCount = processorState.missingSequenceErrors,
            estimatedLostCount = processorState.estimatedLostPacketCount,
            continuityBreakCount = processorState.continuityBreakCount,
            lastLog = processorState.lastLog
        )

        GreenPpgGraphCard(
            title = "Green PPG 실시간 원신호",
            subtitle = "최근 ${"%.1f".format(greenSamples.size / GREEN_SAMPLE_RATE_HZ)}초 · ${greenSamples.size} samples",
            samples = greenSamples
        )

        HeartRateGraphCard(processorState.heartRateGraphData)
        RespirationGraphCard(arousalState.ppgRespirationGraphData)

        MetricStatusCard(
            title = "심박 계산 상태",
            status = processorState.heartRateCalculationStatus,
            details = buildString {
                append("bpm=${processorState.heartRateBpm ?: "--"}")
                append(" · quality=${formatNullable(processorState.heartRateQuality, 3)}")
                append(" · window=${"%.1f".format(processorState.heartRateDiagnostics.windowSeconds)}/12.0 sec")
                append(" (${processorState.heartRateDiagnostics.windowSampleCount} samples)")
                append(" · age=${processorState.heartRateAgeMillis?.let { "$it ms" } ?: "--"}")
            }
        )

        ArousalSection(arousalState)
        StabilitySection(stabilityState)
        PersonalBaselineSection(stabilityState)


        LogFileSection(
            files = internalLogFiles,
            selectedNames = selectedLogNames,
            message = lastExportMessage,
            onRefresh = viewModel::refreshInternalLogFiles,
            onExport = { viewModel.exportSelectedInternalLogFiles(selectedLogNames.toList()) },
            onDelete = {
                viewModel.deleteInternalLogFiles(selectedLogNames.toList())
                selectedLogNames.clear()
            }
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ConnectionSection(
    bleState: PotchBleState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onTrigger: () -> Unit,
    onReset: () -> Unit
) {
    SectionCard("BLE 연결") {
        StatusLine("상태", when {
            bleState.isConnected -> "연결됨"
            bleState.isReconnecting -> "재연결 중"
            bleState.isScanning -> "검색 중"
            else -> "대기"
        })
        StatusLine("기기", bleState.deviceName ?: "--")
        StatusLine("Bond", bondStateLabel(bleState.bondState))
        StatusLine("MTU", bleState.mtu.toString())
        StatusLine(
            "PHY",
            "TX=${phyLabel(bleState.txPhy)} · RX=${phyLabel(bleState.rxPhy)}"
        )
        StatusLine(
            "Characteristic",
            "NotifyReady=${bleState.isNotificationReady} · Write=${bleState.supportsWrite} · WriteNR=${bleState.supportsWriteWithoutResponse}"
        )
        StatusLine("마지막 로그", bleState.lastLog)
        bleState.lastError?.let { StatusLine("오류", it, Color(0xFFFF7777)) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionButton("연결", onConnect, Modifier.weight(1f))
            ActionButton("종료·저장", onDisconnect, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionButton(
                label = "LED/Vibe 미지원",
                onClick = onTrigger,
                modifier = Modifier.weight(1f),
                enabled = bleState.isConnected && bleState.supportsLedTrigger
            )
            ActionButton("분석 초기화", onReset, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SensorSummarySection(
    sensorData: SensorData?,
    heartRateBpm: Int?,
    heartRateFresh: Boolean,
    movementSummary: ImuSummary?,
    greenMax: Double
) {
    val temperature = sensorData?.ntcCelsius?.takeIf { it.isFinite() }
    val voltage = sensorData?.batteryVoltage?.takeIf { it.isFinite() }
    val batteryPercent = voltage?.let(::voltageToBatteryPercent)

    SectionCard("센서 요약") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ValueTile("체온", temperature?.let { "%.2f°C".format(it) } ?: "--", Modifier.weight(1f))
            ValueTile(
                "심박",
                heartRateBpm?.let { "$it bpm${if (heartRateFresh) "" else " · held"}" } ?: "--",
                Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ValueTile(
                "배터리",
                voltage?.let { "%.2f V · %d%%".format(it, batteryPercent ?: 0) } ?: "--",
                Modifier.weight(1f)
            )
            ValueTile("Green 최대", greenMax.roundToInt().toString(), Modifier.weight(1f))
        }
        StatusLine("IMU 움직임", movementSummary?.label ?: "--")
        StatusLine(
            "가속도 평균",
            movementSummary?.let { "x=${"%.3f".format(it.meanX)}g · y=${"%.3f".format(it.meanY)}g · z=${"%.3f".format(it.meanZ)}g" }
                ?: "--"
        )
        StatusLine("IMU 최대 Δ", movementSummary?.let { "%.4f g".format(it.maxDeltaG) } ?: "--")
    }
}

@Composable
private fun PacketSection(
    sensorData: SensorData?,
    parsedBurstCount: Int,
    totalPacketCount: Int,
    validPacketCount: Int,
    crcErrorCount: Int,
    sequenceErrorCount: Int,
    estimatedLostCount: Int,
    continuityBreakCount: Int,
    lastLog: String
) {
    SectionCard("패킷 / Burst") {
        StatusLine(
            "최근 Burst",
            sensorData?.let { "seq ${it.sequenceStart}~${it.sequenceEnd} · ${it.packetCount}/8 packets" } ?: "--"
        )
        StatusLine("MCU timestamp", sensorData?.timestamp?.let { "$it ms" } ?: "--")
        StatusLine("완료 Burst", parsedBurstCount.toString())
        StatusLine("수신 패킷", "$validPacketCount / $totalPacketCount valid")
        StatusLine("CRC 오류", crcErrorCount.toString())
        StatusLine("시퀀스 오류", "$sequenceErrorCount · 추정 유실 $estimatedLostCount")
        StatusLine("연속성 초기화", continuityBreakCount.toString())
        StatusLine("Processor", lastLog)
    }
}

@Composable
private fun GreenPpgGraphCard(
    title: String,
    subtitle: String,
    samples: List<Int>
) {
    SectionCard(title) {
        Text(subtitle, color = Color(0xFFA9B5C7), fontSize = 12.sp)
        SignalCanvas(
            samples = samples.map(Int::toDouble),
            detected = emptyList(),
            accepted = emptyList(),
            rejected = emptyList(),
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        )
        if (samples.isNotEmpty()) {
            StatusLine("범위", "${samples.minOrNull()} ~ ${samples.maxOrNull()}")
        }
    }
}

@Composable
private fun HeartRateGraphCard(data: HeartRateGraphData) {
    SectionCard("Green PPG 심박 전처리") {
        Text(
            "${data.label} · polarity=${data.selectedPolarity.label()} · ${data.description}",
            color = Color(0xFFA9B5C7),
            fontSize = 12.sp
        )
        SignalCanvas(
            samples = data.samples,
            detected = data.detectedPeakSampleIndices,
            accepted = data.acceptedPeakSampleIndices,
            rejected = data.rejectedPeakSampleIndices,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        )
        StatusLine("계산 BPM", data.calculatedBpm?.toString() ?: "--")
        StatusLine("품질", formatNullable(data.qualityScore, 3))
        StatusLine(
            "Peak",
            "detected=${data.detectedPeakSampleIndices.size} · accepted=${data.acceptedPeakSampleIndices.size} · rejected=${data.rejectedPeakSampleIndices.size}"
        )
    }
}

@Composable
private fun RespirationGraphCard(data: PpgRespirationGraphData) {
    SectionCard("Green PPG 호흡 전처리") {
        Text(
            "${data.description} · polarity=${data.selectedPolarity.label()}",
            color = Color(0xFFA9B5C7),
            fontSize = 12.sp
        )
        SignalCanvas(
            samples = data.samples,
            detected = data.detectedPeakSampleIndices,
            accepted = data.acceptedPeakSampleIndices,
            rejected = data.rejectedPeakSampleIndices,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        )
        StatusLine(
            "분석 구간",
            "%.1f / %.1f sec".format(
                data.windowSeconds,
                data.minimumWindowSeconds.toDouble()
            )
        )
        StatusLine("호흡수", data.calculatedRrBpm?.let { "%.1f bpm".format(it) } ?: "--")
        StatusLine("품질", formatNullable(data.qualityScore, 3))
    }
}

@Composable
private fun ArousalSection(state: ArousalState) {
    SectionCard("각성도 분석 · Evidence Scoring") {
        ValueTile(
            title = "최종 Wake Score",
            value = "%.1f / 100 · confidence %.1f%%".format(
                state.finalWakeScore,
                state.finalWakeConfidence
            ),
            modifier = Modifier.fillMaxWidth()
        )
        StatusLine(
            "판정",
            if (state.isWakeTimingCandidate) "기상 후보" else state.wakeDecisionReason,
            if (state.isWakeTimingCandidate) Color(0xFFFF7777) else Color(0xFFA9B5C7)
        )
        StatusLine(
            "증거 coverage",
            "%.1f%% · 사용 영역 %d/4".format(
                state.finalWakeCoverage,
                state.usedArousalDomainCount
            )
        )
        StatusLine(
            "현재 게이트",
            if (state.wakeCurrentConditionPassed) "통과" else "실패",
            if (state.wakeCurrentConditionPassed) Color(0xFF7BE0A3) else Color(0xFFFFB66E)
        )
        StatusLine(
            "Tolerant persistence",
            "최근 ${state.wakePersistenceWindowSeconds}초 중 " +
                    "${state.wakePersistencePassedSeconds}초 통과 / " +
                    "필요 ${state.wakePersistenceRequiredPassSeconds}초"
        )
        StatusLine(
            "Persistence 관측",
            "${state.wakePersistenceObservedSeconds}/${state.wakePersistenceWindowSeconds}초 · " +
                    "실패 ${state.wakePersistenceFailedSeconds}초 · " +
                    "통과율 ${"%.1f".format(state.wakePersistencePassRatio)}%"
        )

        Text(
            text = "영역별 결합 결과",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        DomainEvidenceRow("움직임", state.movementDomainEvidence)
        DomainEvidenceRow("호흡", state.respiratoryDomainEvidence)
        DomainEvidenceRow("심장", state.cardiacDomainEvidence)
        DomainEvidenceRow("온도", state.temperatureDomainEvidence)

        MetricStatusRow("현재 RR", state.rrFinal, state.rrCalculationStatus, " bpm")
        MetricStatusRow("현재 RRV RMSSD", state.rrvRmssdMs, state.rrvCalculationStatus, " ms")
        MetricStatusRow("현재 HR", state.hrBpm?.toDouble(), state.hrCalculationStatus, " bpm")
        MetricStatusRow("현재 HRV LF/HF", state.hrvLfHf, state.hrvFrequencyStatus)
        MetricStatusRow("현재 HRV RMSSD", state.hrvRmssdMs, null, " ms")
        MetricStatusRow("현재 피부온도", state.skinTemperatureCelsius, null, "°C")

        EvidenceDetailCard(
            label = "1. Micro movement",
            currentValue = state.microMovementScore?.let { "%.1f / 100".format(it) } ?: "--",
            evidence = state.microEvidence
        )
        EvidenceDetailCard(
            label = "2. Respiratory rate",
            currentValue = state.rrFinal?.let { "%.2f bpm".format(it) } ?: "--",
            evidence = state.rrEvidence,
            baselineScale = 1.0,
            baselineUnit = " bpm"
        )
        EvidenceDetailCard(
            label = "3. Respiratory variability",
            currentValue = state.rrvRmssdMs?.let { "%.2f ms".format(it) } ?: "--",
            evidence = state.rrvEvidence,
            baselineScale = 1000.0,
            baselineUnit = " ms"
        )
        EvidenceDetailCard(
            label = "4. Heart rate",
            currentValue = state.hrBpm?.let { "$it bpm" } ?: "--",
            evidence = state.hrEvidence,
            baselineScale = 1.0,
            baselineUnit = " bpm"
        )
        EvidenceDetailCard(
            label = "5. HRV · LF/HF 70% + RMSSD 30%",
            currentValue = "LF/HF=${formatNullable(state.hrvLfHf, 3)} · " +
                    "RMSSD=${formatNullable(state.hrvRmssdMs, 1)} ms",
            evidence = state.hrvEvidence,
            baselineScale = 1.0,
            baselineUnit = " ratio",
            primaryComponentLabel = "LF/HF",
            secondaryComponentLabel = "RMSSD"
        )
        StatusLine("HRV 구성", state.hrvScoreComposition)
        StatusLine(
            "LF/HF 사용 제한",
            if (state.hrvFrequencyUsable) {
                "통과 · q=${"%.2f".format(state.hrvFrequencyQuality)} · " +
                        "${"%.1f".format(state.hrvFrequencyObservedSeconds)}초 · " +
                        "IBI=${state.hrvFrequencyIbiCount}"
            } else {
                "제외 · ${state.hrvFrequencyRejectionReasons ?: state.hrvFrequencyStatus.message}"
            },
            if (state.hrvFrequencyUsable) Color(0xFF54E2A0) else Color(0xFFFFB35C)
        )
        StatusLine(
            "HRV 구성요소 점수",
            "LF/HF=${formatNullable(state.hrvFrequencyScore, 1)} · " +
                    "RMSSD=${formatNullable(state.hrvRmssdScore, 1)}"
        )

        EvidenceDetailCard(
            label = "6. Skin temperature",
            currentValue = state.skinTemperatureCelsius?.let { "%.3f°C".format(it) } ?: "--",
            evidence = state.temperatureEvidence,
            baselineScale = 1.0,
            baselineUnit = "°C"
        )

        StatusLine(
            "RR 센서 결합",
            "${state.rrFusionSource} · signal confidence=${"%.2f".format(state.rrFusionConfidence)}"
        )
        StatusLine("RR detail", state.rrFusionLog ?: "--")
        StatusLine("마지막 분석", state.lastLog)
    }
}

@Composable
private fun DomainEvidenceRow(
    label: String,
    evidence: DomainEvidence
) {
    val value = if (evidence.usable && evidence.score != null) {
        "score=${"%.1f".format(evidence.score)} · " +
                "confidence=${"%.1f".format(evidence.confidence * 100.0)}% · " +
                "coverage=${"%.1f".format(evidence.coverage * 100.0)}%"
    } else {
        "사용 불가 · confidence=${"%.1f".format(evidence.confidence * 100.0)}% · " +
                "coverage=${"%.1f".format(evidence.coverage * 100.0)}%"
    }
    StatusLine(
        label,
        value,
        if (evidence.usable) Color(0xFF54E2A0) else Color(0xFFFFB35C)
    )
    Text(
        text = evidence.composition,
        color = Color(0xFF7F8EA3),
        fontSize = 10.sp
    )
}

@Composable
private fun EvidenceDetailCard(
    label: String,
    currentValue: String,
    evidence: MetricEvidence,
    baselineScale: Double = 1.0,
    baselineUnit: String = "",
    primaryComponentLabel: String = "baseline",
    secondaryComponentLabel: String = "trend"
) {
    val scoreText = evidence.score?.let { "%.1f".format(it) } ?: "--"
    val centerText = evidence.baselineCenter?.takeIf { it.isFinite() }?.let {
        "%.3f%s".format(it * baselineScale, baselineUnit)
    } ?: "--"
    val spreadText = evidence.baselineSpread?.takeIf { it.isFinite() }?.let {
        "%.3f%s".format(it * baselineScale, baselineUnit)
    } ?: "--"
    val normalizedText = evidence.normalizedDistance?.takeIf { it.isFinite() }?.let {
        "%.3f z".format(it)
    } ?: "--"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF141D2A), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF27364B), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            StatusLine("현재값", currentValue)
            StatusLine(
                "evidence",
                "score=$scoreText · confidence=${"%.1f".format(evidence.confidence * 100.0)}% · " +
                        "coverage=${"%.1f".format(evidence.coverage * 100.0)}%",
                if (evidence.usable) Color(0xFF54E2A0) else Color(0xFFFF7777)
            )
            StatusLine("사용", if (evidence.usable) "YES" else "NO")
            StatusLine("기준선", "${evidence.baselineSource} · center=$centerText · MAD=$spreadText")
            StatusLine("기준선 거리", normalizedText)
            StatusLine(
                "구성 점수",
                "$primaryComponentLabel=${formatNullable(evidence.baselineScore, 1)} · " +
                        "$secondaryComponentLabel=${formatNullable(evidence.trendScore, 1)}"
            )
            StatusLine("signal quality", "%.3f".format(evidence.signalQuality))
            evidence.reasons?.let {
                StatusLine("제외/감점 사유", it, Color(0xFFFFB35C))
            }
            evidence.log?.let {
                Text(
                    text = it,
                    color = Color(0xFF7F8EA3),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun StabilitySection(state: StabilityState) {
    SectionCard("안정점수 분석") {
        val phaseLabel = when (state.phase) {
            StabilityEpisodePhase.IDLE -> "대기"
            StabilityEpisodePhase.ENTERING -> "진입 판정 중"
            StabilityEpisodePhase.STABLE -> "안정 episode 진행 중"
        }

        ValueTile(
            title = "전체 안정점수",
            value = state.overallStabilityScore
                ?.let { "%.1f / 100".format(it) }
                ?: "--",
            modifier = Modifier.fillMaxWidth()
        )

        StatusLine(
            "Hard gate",
            if (state.hardGatePassed) "통과" else state.hardGateReason,
            if (state.hardGatePassed) Color(0xFF54E2A0) else Color(0xFFFF7777)
        )
        StatusLine("상태", phaseLabel)
        StatusLine("사용 영역", "${state.usedDomainCount} / 4")
        StatusLine("진입 지속", "${state.enteringDurationSec}초")
        StatusLine("episode 지속", "${state.activeEpisodeDurationSec}초")
        StatusLine("이번 세션 검출 episode", state.sessionCandidateCount.toString())

        MetricStatusRow("움직임 안정점수", state.movementStabilityScore, null)
        MetricStatusRow("호흡 영역 안정점수", state.respiratoryStabilityScore, null)
        MetricStatusRow("심장 영역 안정점수", state.cardiacStabilityScore, null)
        MetricStatusRow("온도 영역 안정점수", state.temperatureStabilityScore, null)

        MetricStatusRow("RR 안정점수", state.rrStabilityScore, null)
        MetricStatusRow("RRV 안정점수", state.rrvStabilityScore, null)
        MetricStatusRow("HR 안정점수", state.hrStabilityScore, null)
        MetricStatusRow("HRV 안정점수 (설계 7:3)", state.hrvStabilityScore, null)
        MetricStatusRow("LF/HF 안정점수 (70%)", state.hrvLfHfStabilityScore, null)
        MetricStatusRow("RMSSD 안정점수 (30%)", state.hrvRmssdStabilityScore, null)
        StatusLine(
            "안정지표 LF/HF",
            if (state.hrvFrequencyUsable) "사용" else "제외 · ${state.hrvFrequencyRejectionReasons ?: "사유 없음"}",
            if (state.hrvFrequencyUsable) Color(0xFF54E2A0) else Color(0xFFFFB35C)
        )

        StatusLine("마지막 안정 분석", state.lastLog)
    }
}

@Composable
private fun PersonalBaselineSection(state: StabilityState) {
    SectionCard("현재 사용 중인 개인 안정값") {
        Text(
            text = "수면 세션 시작 시 고정된 기준선입니다. 같은 세션 중 새 episode가 생겨도 종료 전에는 변경되지 않습니다.",
            color = Color(0xFFA9B5C7),
            fontSize = 11.sp
        )

        PersonalBaselineRow(
            label = "RR",
            metricType = BaselineMetricType.RR,
            state = state,
            unit = " bpm",
            displayScale = 1.0
        )
        PersonalBaselineRow(
            label = "RRV RMSSD",
            metricType = BaselineMetricType.RRV,
            state = state,
            unit = " ms",
            displayScale = 1000.0
        )
        PersonalBaselineRow(
            label = "HR",
            metricType = BaselineMetricType.HR,
            state = state,
            unit = " bpm",
            displayScale = 1.0
        )
        PersonalBaselineRow(
            label = "HRV RMSSD",
            metricType = BaselineMetricType.HRV_RMSSD,
            state = state,
            unit = " ms",
            displayScale = 1000.0
        )
        PersonalBaselineRow(
            label = "HRV LF/HF",
            metricType = BaselineMetricType.HRV_LF_HF,
            state = state,
            unit = " ratio",
            displayScale = 1.0
        )
        PersonalBaselineRow(
            label = "피부온도",
            metricType = BaselineMetricType.TEMPERATURE,
            state = state,
            unit = "°C",
            displayScale = 1.0
        )
    }
}

@Composable
private fun PersonalBaselineRow(
    label: String,
    metricType: BaselineMetricType,
    state: StabilityState,
    unit: String,
    displayScale: Double
) {
    val baseline = state.activeBaselines[metricType]
    val lifecycle = baseline?.lifecycleState
        ?: state.baselineStates[metricType]
        ?: BaselineLifecycleState.EMPTY

    val center = baseline?.center
    val spread = baseline?.spread
    val centerText = center?.takeIf { it.isFinite() }?.let {
        "%.3f%s".format(it * displayScale, unit)
    } ?: "--"
    val spreadText = spread?.takeIf { it.isFinite() }?.let {
        "%.3f%s".format(it * displayScale, unit)
    } ?: "--"
    val count = baseline?.candidateCount ?: 0
    val confidence = baseline?.confidence ?: 0.0
    val version = baseline?.distributionVersion ?: 0

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        StatusLine(label, "$centerText · MAD $spreadText")
        Text(
            text = "${lifecycle.name} · 후보 ${count}개 · confidence=${"%.2f".format(confidence)} · 분포 v$version",
            color = baselineStateColor(lifecycle),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun MetricStatusRow(
    name: String,
    value: Double?,
    status: MetricCalculationStatus?,
    suffix: String = ""
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        StatusLine(name, value?.let { "%.3f%s".format(it, suffix) } ?: "--")
        status?.let {
            Text(
                text = "${it.state}: ${it.message}",
                color = statusColor(it.state),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun MetricStatusCard(
    title: String,
    status: MetricCalculationStatus,
    details: String
) {
    SectionCard(title) {
        Text(status.state.name, color = statusColor(status.state), fontWeight = FontWeight.Bold)
        Text(status.message, color = Color.White, fontSize = 13.sp)
        Text(details, color = Color(0xFFA9B5C7), fontSize = 12.sp)
    }
}



@Composable
private fun LogFileSection(
    files: List<InternalPotchLogFile>,
    selectedNames: MutableList<String>,
    message: String?,
    onRefresh: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    SectionCard("내부 Potch 로그") {
        if (files.isEmpty()) {
            Text("저장된 내부 로그가 없습니다.", color = Color(0xFFA9B5C7))
        } else {
            files.take(20).forEach { file ->
                val selected = file.name in selectedNames
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (selected) selectedNames.remove(file.name) else selectedNames.add(file.name)
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = {
                            if (it) selectedNames.add(file.name) else selectedNames.remove(file.name)
                        }
                    )
                    Column(Modifier.weight(1f)) {
                        Text(file.name, color = Color.White, fontSize = 12.sp)
                        Text(
                            "${formatFileSize(file.sizeBytes)} · ${formatTime(file.lastModifiedMillis)}",
                            color = Color(0xFF8995A8),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
        message?.let { Text(it, color = Color(0xFF71D8A2), fontSize = 12.sp) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionButton("새로고침", onRefresh, Modifier.weight(1f))
            ActionButton("선택 내보내기", onExport, Modifier.weight(1f), files.isNotEmpty())
            ActionButton("선택 삭제", onDelete, Modifier.weight(1f), files.isNotEmpty())
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF151E2C), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFF26344A), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun ValueTile(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color(0xFF202C3E), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(title, color = Color(0xFF9EABC0), fontSize = 11.sp)
        Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusLine(label: String, value: String, valueColor: Color = Color.White) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(label, color = Color(0xFF9EABC0), fontSize = 12.sp, modifier = Modifier.weight(0.35f))
        Text(value, color = valueColor, fontSize = 12.sp, modifier = Modifier.weight(0.65f))
    }
}

@Composable
private fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF285A92),
            disabledContainerColor = Color(0xFF273244)
        )
    ) {
        Text(label, fontSize = 11.sp)
    }
}

@Composable
private fun SignalCanvas(
    samples: List<Double>,
    detected: List<Int>,
    accepted: List<Int>,
    rejected: List<Int>,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xFF0B111A), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF243247), RoundedCornerShape(10.dp))
    ) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            drawGrid()
            if (samples.size < 2) return@Canvas
            val min = samples.minOrNull() ?: return@Canvas
            val max = samples.maxOrNull() ?: return@Canvas
            val range = (max - min).takeIf { abs(it) > 1e-9 } ?: 1.0

            val path = Path()
            samples.forEachIndexed { index, value ->
                val point = sampleOffset(index, value, samples.size, min, range)
                if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            drawPath(path, Color(0xFF54E2A0), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))

            detected.distinct().forEach { index ->
                marker(index, samples, min, range, Color(0xFFFFD166), 3.5f)
            }
            accepted.distinct().forEach { index ->
                marker(index, samples, min, range, Color(0xFF52A7FF), 5f)
            }
            rejected.distinct().forEach { index ->
                marker(index, samples, min, range, Color(0xFFFF7777), 4f)
            }
        }
    }
}

private fun DrawScope.drawGrid() {
    val line = Color(0xFF1C2A3D)
    for (i in 1..3) {
        val y = size.height * i / 4f
        drawLine(line, Offset(0f, y), Offset(size.width, y), 1f)
    }
    for (i in 1..4) {
        val x = size.width * i / 5f
        drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f)
    }
}

private fun DrawScope.marker(
    index: Int,
    samples: List<Double>,
    min: Double,
    range: Double,
    color: Color,
    radius: Float
) {
    if (index !in samples.indices) return
    val point = sampleOffset(index, samples[index], samples.size, min, range)
    drawCircle(color, radius.dp.toPx(), point)
}

private fun DrawScope.sampleOffset(
    index: Int,
    value: Double,
    count: Int,
    min: Double,
    range: Double
): Offset {
    val x = if (count <= 1) 0f else size.width * index / (count - 1).toFloat()
    val normalized = ((value - min) / range).coerceIn(0.0, 1.0)
    val y = size.height * (1.0 - normalized).toFloat()
    return Offset(x, y)
}

private data class ImuSummary(
    val meanX: Double,
    val meanY: Double,
    val meanZ: Double,
    val maxDeltaG: Double,
    val label: String
)

private fun calculateImuSummary(data: ByteArray): ImuSummary? {
    val samples = mutableListOf<Triple<Double, Double, Double>>()
    var offset = 0
    while (offset + 11 < data.size) {
        val x = readInt16(data, offset) / ACCEL_LSB_PER_G
        val y = readInt16(data, offset + 2) / ACCEL_LSB_PER_G
        val z = readInt16(data, offset + 4) / ACCEL_LSB_PER_G
        samples += Triple(x, y, z)
        offset += 12
    }
    if (samples.isEmpty()) return null
    val magnitudes = samples.map { (x, y, z) -> sqrt(x * x + y * y + z * z) }
    val maxDelta = magnitudes.zipWithNext { a, b -> abs(b - a) }.maxOrNull() ?: 0.0
    val label = when {
        maxDelta >= 0.5 -> "큰 움직임"
        maxDelta >= 0.08 -> "미세 움직임"
        else -> "안정"
    }
    return ImuSummary(
        meanX = samples.map { it.first }.average(),
        meanY = samples.map { it.second }.average(),
        meanZ = samples.map { it.third }.average(),
        maxDeltaG = maxDelta,
        label = label
    )
}

private fun decodeGreenPpg(data: ByteArray): IntArray =
    IntArray(data.size / 2) { index ->
        val offset = index * 2
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
    }

private fun readInt16(data: ByteArray, offset: Int): Double {
    val raw = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
    return raw.toShort().toDouble()
}

private fun voltageToBatteryPercent(voltage: Double): Int =
    (((voltage - 3.2) / (4.2 - 3.2)) * 100.0).roundToInt().coerceIn(0, 100)

private fun HeartRatePeakPolarity.label(): String = when (this) {
    HeartRatePeakPolarity.POSITIVE -> "POSITIVE"
    HeartRatePeakPolarity.NEGATIVE -> "NEGATIVE"
    HeartRatePeakPolarity.NONE -> "NONE"
}

private fun com.leejang.sleeptandard.Potch.RespirationPeakPolarity.label(): String = when (this) {
    com.leejang.sleeptandard.Potch.RespirationPeakPolarity.POSITIVE -> "POSITIVE"
    com.leejang.sleeptandard.Potch.RespirationPeakPolarity.NEGATIVE -> "NEGATIVE"
    com.leejang.sleeptandard.Potch.RespirationPeakPolarity.NONE -> "NONE"
}

private fun baselineStateColor(state: BaselineLifecycleState): Color = when (state) {
    BaselineLifecycleState.EMPTY -> Color(0xFF8995A8)
    BaselineLifecycleState.COLLECTING -> Color(0xFFFFD166)
    BaselineLifecycleState.PROVISIONAL -> Color(0xFF52A7FF)
    BaselineLifecycleState.MATURE -> Color(0xFF54E2A0)
}

private fun bondStateLabel(state: Int): String = when (state) {
    android.bluetooth.BluetoothDevice.BOND_NONE -> "NONE"
    android.bluetooth.BluetoothDevice.BOND_BONDING -> "BONDING"
    android.bluetooth.BluetoothDevice.BOND_BONDED -> "BONDED"
    else -> "UNKNOWN($state)"
}

private fun phyLabel(phy: Int?): String = when (phy) {
    null -> "--"
    android.bluetooth.BluetoothDevice.PHY_LE_1M -> "1M"
    android.bluetooth.BluetoothDevice.PHY_LE_2M -> "2M"
    android.bluetooth.BluetoothDevice.PHY_LE_CODED -> "CODED"
    else -> "UNKNOWN($phy)"
}

private fun statusColor(state: MetricCalculationState): Color = when (state) {
    MetricCalculationState.VALID -> Color(0xFF54E2A0)
    MetricCalculationState.COLLECTING -> Color(0xFFFFD166)
    MetricCalculationState.REJECTED -> Color(0xFFFF7777)
}

private fun formatNullable(value: Double?, digits: Int): String {
    if (value == null || !value.isFinite()) return "--"
    return String.format(Locale.US, "%.${digits}f", value)
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))

private const val GREEN_SAMPLE_RATE_HZ = 128.0
private const val GREEN_GRAPH_MAX_SAMPLES = 1280
private const val ACCEL_LSB_PER_G = 8192.0
