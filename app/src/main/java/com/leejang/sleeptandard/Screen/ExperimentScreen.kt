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
import androidx.compose.foundation.rememberScrollState
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
import com.leejang.sleeptandard.Potch.HeartRateGraphData
import com.leejang.sleeptandard.Potch.HeartRatePeakPolarity
import com.leejang.sleeptandard.Potch.InternalPotchLogFile
import com.leejang.sleeptandard.Potch.MetricCalculationState
import com.leejang.sleeptandard.Potch.MetricCalculationStatus
import com.leejang.sleeptandard.Potch.PpgRespirationGraphData
import com.leejang.sleeptandard.Potch.PotchBleState
import com.leejang.sleeptandard.Potch.PotchBleViewModel
import com.leejang.sleeptandard.Potch.SensorData
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

    var microLowCutHz by rememberSaveable { mutableFloatStateOf(0.5f) }
    var microHighCutHz by rememberSaveable { mutableFloatStateOf(5.0f) }
    val selectedLogNames = remember { mutableStateListOf<String>() }
    val greenSamples = remember { mutableStateListOf<Int>() }

    val sensorData = processorState.lastParsedData
    val arousalState = processorState.arousalState
    val stabilityState = processorState.stabilityState

    LaunchedEffect(microLowCutHz, microHighCutHz, bleState.isConnected) {
        if (!bleState.isConnected || microLowCutHz >= microHighCutHz) return@LaunchedEffect
        kotlinx.coroutines.delay(250)
        viewModel.updateMicroMovementBandPass(
            lowCutHz = microLowCutHz.toDouble(),
            highCutHz = microHighCutHz.toDouble()
        )
    }

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
                append(" · age=${processorState.heartRateAgeMillis?.let { "$it ms" } ?: "--"}")
            }
        )

        ArousalSection(arousalState)
        StabilitySection(stabilityState)
        PersonalBaselineSection(stabilityState)

        BandPassControl(
            lowCutHz = microLowCutHz,
            highCutHz = microHighCutHz,
            onLowChange = { microLowCutHz = it.coerceAtMost(microHighCutHz - 0.1f) },
            onHighChange = { microHighCutHz = it.coerceAtLeast(microLowCutHz + 0.1f) }
        )

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
    SectionCard("각성도 분석") {
        ValueTile(
            title = "최종 Wake Score",
            value = "%.1f / 100 · %s".format(
                state.finalWakeScore,
                if (state.isWakeTimingCandidate) "기상 후보" else "대기"
            ),
            modifier = Modifier.fillMaxWidth()
        )
        MetricStatusRow("Micro movement", state.microMovementScore, null)
        MetricStatusRow("Respiratory rate", state.rrFinal, state.rrCalculationStatus, " bpm")
        MetricStatusRow("Respiratory variability", state.rrvRmssdMs, state.rrvCalculationStatus, " ms")
        MetricStatusRow("Heart rate", state.hrBpm?.toDouble(), state.hrCalculationStatus, " bpm")
        MetricStatusRow("Heart rate variability", state.hrvRmssdMs, state.hrvCalculationStatus, " ms")
        MetricStatusRow("Skin temperature", state.skinTemperatureCelsius, null, "°C")
        StatusLine("RR source", "${state.rrFusionSource} · confidence=${"%.2f".format(state.rrFusionConfidence)}")
        StatusLine("RR detail", state.rrFusionLog ?: "--")
        StatusLine("마지막 분석", state.lastLog)
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
        MetricStatusRow("HRV 안정점수", state.hrvStabilityScore, null)

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
private fun BandPassControl(
    lowCutHz: Float,
    highCutHz: Float,
    onLowChange: (Float) -> Unit,
    onHighChange: (Float) -> Unit
) {
    SectionCard("Micro movement Band-pass") {
        Text("Low cut ${"%.1f".format(lowCutHz)} Hz", color = Color.White)
        Slider(value = lowCutHz, onValueChange = onLowChange, valueRange = 0.1f..4.5f)
        Text("High cut ${"%.1f".format(highCutHz)} Hz", color = Color.White)
        Slider(value = highCutHz, onValueChange = onHighChange, valueRange = 0.5f..10.0f)
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
