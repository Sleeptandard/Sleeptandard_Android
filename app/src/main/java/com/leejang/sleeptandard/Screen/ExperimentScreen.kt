package com.leejang.sleeptandard.Screen

import android.util.Log
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leejang.sleeptandard.Potch.ArousalState
import com.leejang.sleeptandard.Potch.HeartRateFusionSource
import com.leejang.sleeptandard.Potch.HeartRateGraphData
import com.leejang.sleeptandard.Potch.HeartRateProcessingState
import com.leejang.sleeptandard.Potch.HeartRatePeakPolarity
import com.leejang.sleeptandard.Potch.MetricCalculationState
import com.leejang.sleeptandard.Potch.MetricCalculationStatus
import com.leejang.sleeptandard.Potch.PacketErrorLog
import com.leejang.sleeptandard.Potch.PotchBleState
import com.leejang.sleeptandard.Potch.PotchBleViewModel
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.foundation.clickable
import androidx.compose.material3.Slider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.leejang.sleeptandard.Potch.InternalPotchLogFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExperimentScreen(
    viewModel: PotchBleViewModel = viewModel()
) {
    var microLowCutHz by rememberSaveable { mutableFloatStateOf(0.5f) }
    var microHighCutHz by rememberSaveable { mutableFloatStateOf(5.0f) }

    val bleState by viewModel.bleState.collectAsState()
    val processorState by viewModel.processorState.collectAsState()

    LaunchedEffect(microLowCutHz, microHighCutHz, bleState.isConnected) {
        if (!bleState.isConnected) return@LaunchedEffect

        kotlinx.coroutines.delay(250)

        if (microLowCutHz < microHighCutHz) {
            viewModel.updateMicroMovementBandPass(
                lowCutHz = microLowCutHz.toDouble(),
                highCutHz = microHighCutHz.toDouble()
            )
        }
    }

    val sensorData = processorState.lastParsedData
    val arousalState = processorState.arousalState

    val temperatureText =
        sensorData?.let { "%.1f°C".format(it.ntcCelsius) } ?: "--°C"

    val heartRateText =
        processorState.heartRateBpm?.let { "$it bpm" } ?: "-- bpm"

    val batteryPercent =
        sensorData?.batteryVoltage?.let { voltageToBatteryPercent(it) }

    val batteryText =
        batteryPercent?.let { "$it%" } ?: "--%"

    val movementSummary =
        remember(sensorData?.timestamp) {
            sensorData?.imuData?.let { calculateImuSummary(it) }
        }

    val movementText =
        movementSummary?.movementLabel ?: "--"

    val scrollState = rememberScrollState()

    val internalLogFiles by viewModel.internalLogFiles.collectAsState()
    val lastExportMessage by viewModel.lastExportMessage.collectAsState()

    val selectedLogFileNames = remember {
        mutableStateListOf<String>()
    }

    val ppgIrSamples = remember {
        mutableStateListOf<Int>()
    }
    val ppgRedSamples = remember {
        mutableStateListOf<Int>()
    }
    var latestPpgFrameSummary by remember {
        mutableStateOf(PpgFrameSummary.empty())
    }

    var latestImuPose by remember {
        mutableStateOf(ImuPose.empty())
    }

    LaunchedEffect(sensorData?.timestamp) {
        val ppgData = sensorData?.ppgData ?: return@LaunchedEffect
        val frameSamples = extractPpgFrameSamples(ppgData)

        if (frameSamples.ir.isNotEmpty()) {
            ppgIrSamples.appendAndTrim(
                values = frameSamples.ir,
                maxSize = PPG_GRAPH_MAX_SAMPLES
            )
            ppgRedSamples.appendAndTrim(
                values = frameSamples.red,
                maxSize = PPG_GRAPH_MAX_SAMPLES
            )
            latestPpgFrameSummary = PpgFrameSummary.from(
                ir = frameSamples.ir,
                red = frameSamples.red
            )
        }

        val imuData = sensorData?.imuData
        if (imuData != null) {
            val nextPose = calculateImuPose(imuData)
            latestImuPose = if (latestImuPose.sampleCount == 0) {
                nextPose
            } else {
                nextPose.smoothWith(previous = latestImuPose, alpha = 0.35)
            }
        }
    }

    LaunchedEffect(processorState.parsedSuperFrameCount) {
        if (processorState.parsedSuperFrameCount == 0) {
            ppgIrSamples.clear()
            ppgRedSamples.clear()
            latestPpgFrameSummary = PpgFrameSummary.empty()
            latestImuPose = ImuPose.empty()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshInternalLogFiles()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111722))
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .padding(top = 48.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SensorCard(
                modifier = Modifier.weight(1f),
                icon = "♨",
                iconColor = Color(0xFFFF8A22),
                iconBackground = Color(0xFF4A332A),
                title = "체온",
                value = temperatureText
            )

            SensorCard(
                modifier = Modifier.weight(1f),
                icon = "♥",
                iconColor = Color(0xFFFF4B55),
                iconBackground = Color(0xFF4A2A35),
                title = "심박수",
                value = heartRateText
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SensorCard(
                modifier = Modifier.weight(1f),
                icon = "⌁",
                iconColor = Color(0xFFB9F7FF),
                iconBackground = Color(0xFF334554),
                title = "움직임",
                value = movementText
            )

            SensorCard(
                modifier = Modifier.weight(1f),
                icon = "▰",
                iconColor = Color(0xFF2CFF70),
                iconBackground = Color(0xFF214D34),
                title = "배터리",
                value = batteryText
            )
        }

        PpgRealtimeGraphCard(
            irSamples = ppgIrSamples,
            redSamples = ppgRedSamples,
            frameSummary = latestPpgFrameSummary,
            isConnected = bleState.isConnected
        )

        HeartRateProcessedPpgGraphCard(
            graphData = processorState.heartRateGraphData,
            isConnected = bleState.isConnected
        )

        /*
        HeartRateFilteredPpgGraphCard(
            filteredIrSamples = buildHeartRateFilteredIrSamples(ppgIrSamples),
            isConnected = bleState.isConnected
        )
*/
        ImuRealtimeModelCard(
            imuPose = latestImuPose,
            isConnected = bleState.isConnected
        )

        Text(
            text = "Micro Movement BPF",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Low Cut: %.2f Hz".format(microLowCutHz),
            color = Color.White.copy(alpha = 0.75f)
        )

        Slider(
            value = microLowCutHz,
            onValueChange = { value ->
                microLowCutHz = value.coerceAtMost(microHighCutHz - 0.1f)
            },
            valueRange = 0.1f..2.0f
        )

        Text(
            text = "High Cut: %.2f Hz".format(microHighCutHz),
            color = Color.White.copy(alpha = 0.75f)
        )

        Slider(
            value = microHighCutHz,
            onValueChange = { value ->
                microHighCutHz = value.coerceAtLeast(microLowCutHz + 0.1f)
            },
            valueRange = 1.0f..10.0f
        )

        ArousalCalculationStatusCard(
            arousalState = arousalState
        )

        ArousalStateCard(
            arousalState = arousalState
        )

        DeveloperCard(
            timestamp = sensorData?.timestamp,
            packetLossCount = processorState.crcErrorCount,
            sequenceErrorCount = processorState.missingSequenceErrors,
            batteryVoltage = sensorData?.batteryVoltage,
            batteryRaw = sensorData?.batteryRaw,
            imuSummary = movementSummary,
            lastLog = processorState.lastLog,
            bleLog = bleState.lastLog,
            isConnected = bleState.isConnected,
            deviceName = bleState.deviceName,
            mtu = bleState.mtu,
            onReset = { viewModel.resetProcessor() },
            bleState = bleState,

            totalMiniPackets = processorState.totalMiniPackets,
            validMiniPackets = processorState.validMiniPackets,
            damagedPacketCount = processorState.damagedPacketCount,
            estimatedLostPacketCount = processorState.estimatedLostPacketCount,
            parsedSuperFrameCount = processorState.parsedSuperFrameCount,
            lastFragCounter = processorState.lastFragCounter,
            expectedFragCounter = processorState.expectedFragCounter,
            recentPacketErrors = processorState.recentPacketErrors,

            onTestLengthError = { viewModel.debugTestLengthError() },
            onTestMiniHeaderError = { viewModel.debugTestMiniHeaderError() },
            onTestSequenceLoss = { viewModel.debugTestSequenceLoss() },
            onTestSuperHeaderError = { viewModel.debugTestSuperHeaderError() },
            onTestCrcError = { viewModel.debugTestCrcError() },
            onTestCounterWrapAround = { viewModel.debugTestCounterWrapAround() }
        )

        InternalLogFileExportCard(
            files = internalLogFiles,
            selectedFileNames = selectedLogFileNames,
            lastExportMessage = lastExportMessage,
            onRefresh = {
                viewModel.refreshInternalLogFiles()
            },
            onToggleSelect = { fileName ->
                if (selectedLogFileNames.contains(fileName)) {
                    selectedLogFileNames.remove(fileName)
                } else {
                    selectedLogFileNames.add(fileName)
                }
            },
            onExportSelected = {
                viewModel.exportSelectedInternalLogFiles(selectedLogFileNames.toList())
                selectedLogFileNames.clear()
            },
            onDeleteLogs = {
                viewModel.deleteInternalLogFiles(selectedLogFileNames.toList())
                selectedLogFileNames.clear()
            }
        )

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = when {
                    bleState.isConnected -> Color(0xFFD83A40)
                    bleState.isReconnecting -> Color(0xFFFF8A22)
                    else -> Color(0xFF2F8CFF)
                }
            ),
            onClick = {
                when {
                    bleState.isConnected -> {
                        viewModel.disconnect()
                    }

                    bleState.isReconnecting -> {
                        viewModel.stopReconnectAndSaveLog()
                    }

                    else -> {
                        viewModel.startScan()
                    }
                }
            }
        ) {
            Text(
                text = when {
                    bleState.isConnected -> "종료 및 저장"
                    bleState.isReconnecting -> "종료하기"
                    bleState.isScanning -> "스캔 중..."
                    else -> "Potch 연결"
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SensorCard(
    modifier: Modifier = Modifier,
    icon: String,
    iconColor: Color,
    iconBackground: Color,
    title: String,
    value: String
) {
    Box(
        modifier = modifier
            .aspectRatio(1.58f)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF252A34))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.04f),
                shape = RoundedCornerShape(28.dp)
            )
            .padding(18.dp)
    ) {
        Box(
            modifier = Modifier
                .size(45.dp)
                .clip(CircleShape)
                .background(iconBackground),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = icon,
                color = iconColor,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = value,
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 34.sp
            )
        }
    }
}


private const val PPG_SAMPLE_RATE_HZ = 100
private const val PPG_GRAPH_WINDOW_SECONDS = 10
private const val PPG_GRAPH_MAX_SAMPLES = PPG_SAMPLE_RATE_HZ * PPG_GRAPH_WINDOW_SECONDS

private data class PpgFrameSamples(
    val red: List<Int>,
    val ir: List<Int>
)

private data class PpgFrameSummary(
    val sampleCount: Int,
    val irMin: Int?,
    val irMax: Int?,
    val redMin: Int?,
    val redMax: Int?
) {
    companion object {
        fun empty(): PpgFrameSummary = PpgFrameSummary(
            sampleCount = 0,
            irMin = null,
            irMax = null,
            redMin = null,
            redMax = null
        )

        fun from(
            ir: List<Int>,
            red: List<Int>
        ): PpgFrameSummary = PpgFrameSummary(
            sampleCount = maxOf(ir.size, red.size),
            irMin = ir.minOrNull(),
            irMax = ir.maxOrNull(),
            redMin = red.minOrNull(),
            redMax = red.maxOrNull()
        )
    }
}

@Composable
private fun PpgRealtimeGraphCard(
    irSamples: List<Int>,
    redSamples: List<Int>,
    frameSummary: PpgFrameSummary,
    isConnected: Boolean
) {
    val bufferedSeconds =
        if (irSamples.isEmpty()) 0.0
        else irSamples.size.toDouble() / PPG_SAMPLE_RATE_HZ

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF1E1E25))
            .border(
                width = 1.dp,
                color = Color(0xFF4CD3FF).copy(alpha = 0.45f),
                shape = RoundedCornerShape(26.dp)
            )
            .padding(18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📈 PPG 실시간 그래프",
                color = Color(0xFF4CD3FF),
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = if (isConnected) "LIVE" else "대기",
                color = if (isConnected) Color(0xFF3DFF78) else Color.White.copy(alpha = 0.45f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = "최근 ${"%.1f".format(bufferedSeconds)}초 / 최대 ${PPG_GRAPH_WINDOW_SECONDS}초 · IR/RED 채널별 정규화 표시",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 13.sp
        )

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = 0.24f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(10.dp)
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                drawPpgGrid()

                if (irSamples.size >= 2) {
                    drawPpgPath(
                        samples = irSamples,
                        color = Color(0xFF4CD3FF),
                        strokeWidthPx = 2.5f
                    )
                }


                if (redSamples.size >= 2) {
                    drawPpgPath(
                        samples = redSamples,
                        color = Color(0xFFFF4B55),
                        strokeWidthPx = 2.0f
                    )
                }
            }

            if (irSamples.isEmpty()) {
                Text(
                    text = "PPG 데이터 수신 대기 중",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PpgLegendChip(
                modifier = Modifier.weight(1f),
                title = "IR",
                value = ppgRangeText(frameSummary.irMin, frameSummary.irMax),
                color = Color(0xFF4CD3FF)
            )

            PpgLegendChip(
                modifier = Modifier.weight(1f),
                title = "RED",
                value = ppgRangeText(frameSummary.redMin, frameSummary.redMax),
                color = Color(0xFFFF4B55)
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "frame=${frameSummary.sampleCount} samples · buffer=${irSamples.size} samples",
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun HeartRateProcessedPpgGraphCard(
    graphData: HeartRateGraphData,
    isConnected: Boolean
) {
    val primaryColor = when (graphData.source) {
        HeartRateFusionSource.IR -> Color(0xFF4CD3FF)
        HeartRateFusionSource.RED -> Color(0xFFFF4B55)
        HeartRateFusionSource.FUSED_IR_RED -> Color(0xFF4CD3FF)
        HeartRateFusionSource.COMBINED_FALLBACK -> Color(0xFFB7A7FF)
        HeartRateFusionSource.NONE -> Color(0xFF4CD3FF)
    }

    val secondaryColor = Color(0xFFFF4B55)

    val statusColor = when (graphData.processingState) {
        HeartRateProcessingState.VALID -> Color(0xFF3DFF78)
        HeartRateProcessingState.COLLECTING -> Color(0xFFFFD166)
        HeartRateProcessingState.HELD_PREVIOUS -> Color(0xFFFFC46B)
        else -> Color(0xFFFF5C68)
    }

    val sourceText = when (graphData.source) {
        HeartRateFusionSource.IR -> "IR"
        HeartRateFusionSource.RED -> "RED"
        HeartRateFusionSource.FUSED_IR_RED -> "IR + RED LATE FUSION"
        HeartRateFusionSource.COMBINED_FALLBACK -> "AVERAGE FALLBACK"
        HeartRateFusionSource.NONE -> "NO FINAL SOURCE"
    }

    val polarityText = when (graphData.selectedPolarity) {
        HeartRatePeakPolarity.POSITIVE -> "POSITIVE"
        HeartRatePeakPolarity.NEGATIVE -> "NEGATIVE"
        HeartRatePeakPolarity.NONE -> "NONE"
    }

    val statusText = when (graphData.processingState) {
        HeartRateProcessingState.VALID -> "VALID"
        HeartRateProcessingState.COLLECTING -> "수집 중"
        HeartRateProcessingState.HELD_PREVIOUS -> "이전값 유지"
        else -> "분석 불가"
    }

    val allSamples =
        if (graphData.secondarySamples.isEmpty()) {
            graphData.primarySamples
        } else {
            graphData.primarySamples + graphData.secondarySamples
        }

    val graphMin = allSamples.minOrNull()
    val graphMax = allSamples.maxOrNull()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF1E1E25))
            .border(
                width = 1.dp,
                color = Color(0xFFB7A7FF).copy(alpha = 0.45f),
                shape = RoundedCornerShape(26.dp)
            )
            .padding(18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🫀 HR 분석 PPG 결과",
                color = Color(0xFFB7A7FF),
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = if (isConnected) statusText else "대기",
                color = if (isConnected) statusColor
                else Color.White.copy(alpha = 0.45f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(
                        if (isConnected) statusColor.copy(alpha = 0.14f)
                        else Color.White.copy(alpha = 0.07f)
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = graphData.description,
            color = Color.White.copy(alpha = 0.58f),
            fontSize = 13.sp,
            lineHeight = 18.sp
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "DataProcessor clean tail · spike 보간 · 0.75~3.5Hz BPF · peak/IBI fitting",
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = 0.24f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(10.dp)
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                drawPpgGrid()

                if (graphData.primarySamples.size >= 2) {
                    drawProcessedPpgPath(
                        samples = graphData.primarySamples,
                        color = primaryColor,
                        strokeWidthPx = 2.6f,
                        sharedMin = graphMin,
                        sharedMax = graphMax
                    )
                }

                if (graphData.secondarySamples.size >= 2) {
                    drawProcessedPpgPath(
                        samples = graphData.secondarySamples,
                        color = secondaryColor,
                        strokeWidthPx = 2.1f,
                        sharedMin = graphMin,
                        sharedMax = graphMax
                    )
                }

                if (
                    graphData.primarySamples.size >= 2 &&
                    graphData.peakSampleIndices.isNotEmpty()
                ) {
                    drawProcessedPpgPeakMarkers(
                        samples = graphData.primarySamples,
                        peakIndices = graphData.peakSampleIndices,
                        color = Color(0xFFFFD166),
                        sharedMin = graphMin,
                        sharedMax = graphMax
                    )
                }
            }

            if (graphData.primarySamples.isEmpty()) {
                Text(
                    text = "HR 분석용 clean PPG 수집 대기 중",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PpgLegendChip(
                modifier = Modifier.weight(1f),
                title = graphData.primaryLabel,
                value = processedRangeText(graphData.primarySamples),
                color = primaryColor
            )

            val secondaryLabel = graphData.secondaryLabel
            if (
                secondaryLabel != null &&
                graphData.secondarySamples.isNotEmpty()
            ) {
                PpgLegendChip(
                    modifier = Modifier.weight(1f),
                    title = secondaryLabel,
                    value = processedRangeText(graphData.secondarySamples),
                    color = secondaryColor
                )
            } else {
                PpgLegendChip(
                    modifier = Modifier.weight(1f),
                    title = "SOURCE",
                    value = sourceText,
                    color = Color(0xFFB7A7FF)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = buildString {
                append("source=$sourceText")
                append(" · polarity=$polarityText")
                append(" · clean=${graphData.cleanSegmentSampleCount}")
                append(" / retained=${graphData.retainedBufferSampleCount} samples")
                append(" · interpolated=${graphData.interpolatedSampleCount}")
                append(" · peak-excluded=${graphData.excludedPeakSampleCount}")
                graphData.calculatedBpm?.let { append(" · bpm=$it") }
                graphData.qualityScore?.let {
                    append(" · quality=${"%.3f".format(it)}")
                }
            },
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun DrawScope.drawProcessedPpgPath(
    samples: List<Double>,
    color: Color,
    strokeWidthPx: Float,
    sharedMin: Double?,
    sharedMax: Double?
) {
    if (samples.size < 2) return

    val minValue = sharedMin ?: samples.minOrNull() ?: return
    val maxValue = sharedMax ?: samples.maxOrNull() ?: return
    val range = (maxValue - minValue).takeIf { it > 0.0 } ?: 1.0

    val path = Path()

    samples.forEachIndexed { index, value ->
        val x =
            size.width * index.toFloat() /
                    (samples.size - 1).toFloat()

        val normalized =
            ((value - minValue) / range)
                .coerceIn(0.0, 1.0)
                .toFloat()

        val y = size.height - normalized * size.height

        if (index == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidthPx)
    )
}

private fun DrawScope.drawProcessedPpgPeakMarkers(
    samples: List<Double>,
    peakIndices: List<Int>,
    color: Color,
    sharedMin: Double?,
    sharedMax: Double?
) {
    if (samples.size < 2) return

    val minValue = sharedMin ?: samples.minOrNull() ?: return
    val maxValue = sharedMax ?: samples.maxOrNull() ?: return
    val range = (maxValue - minValue).takeIf { it > 0.0 } ?: 1.0

    peakIndices.forEach { index ->
        if (index !in samples.indices) return@forEach

        val x =
            size.width * index.toFloat() /
                    (samples.size - 1).toFloat()

        val normalized =
            ((samples[index] - minValue) / range)
                .coerceIn(0.0, 1.0)
                .toFloat()

        val y = size.height - normalized * size.height

        drawCircle(
            color = color,
            radius = 4.5f,
            center = Offset(x, y)
        )
    }
}

private fun processedRangeText(
    samples: List<Double>
): String {
    if (samples.isEmpty()) return "-"

    val minValue = samples.minOrNull() ?: return "-"
    val maxValue = samples.maxOrNull() ?: return "-"

    return "${"%.1f".format(minValue)} ~ ${"%.1f".format(maxValue)}"
}

@Composable
private fun PpgLegendChip(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    color: Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = title,
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        Spacer(Modifier.height(3.dp))

        Text(
            text = value,
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun DrawScope.drawPpgGrid() {
    val gridColor = Color.White.copy(alpha = 0.08f)

    for (i in 0..4) {
        val y = size.height * i / 4f
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f
        )
    }

    for (i in 0..5) {
        val x = size.width * i / 5f
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 1f
        )
    }
}

private fun DrawScope.drawPpgPath(
    samples: List<Int>,
    color: Color,
    strokeWidthPx: Float
) {
    if (samples.size < 2) return

    val minValue = samples.minOrNull() ?: return
    val maxValue = samples.maxOrNull() ?: return
    val range = (maxValue - minValue).takeIf { it > 0 } ?: 1

    val path = Path()

    samples.forEachIndexed { index, value ->
        val x = size.width * index.toFloat() / (samples.size - 1).toFloat()
        val normalized = (value - minValue).toFloat() / range.toFloat()
        val y = size.height - normalized * size.height

        if (index == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidthPx)
    )
}

private fun MutableList<Int>.appendAndTrim(
    values: List<Int>,
    maxSize: Int
) {
    addAll(values)

    val overflow = size - maxSize
    if (overflow > 0) {
        repeat(overflow) {
            removeAt(0)
        }
    }
}

private fun extractPpgFrameSamples(ppgData: ByteArray): PpgFrameSamples {
    val sampleCount = ppgData.size / 6
    if (sampleCount <= 0) {
        return PpgFrameSamples(
            red = emptyList(),
            ir = emptyList()
        )
    }

    val red = ArrayList<Int>(sampleCount)
    val ir = ArrayList<Int>(sampleCount)

    for (i in 0 until sampleCount) {
        val base = i * 6

        // PPG sample = RED 3 bytes + IR 3 bytes, each 18-bit unsigned.
        val redSample = readPpg18Bit(ppgData, base)
        val irSample = readPpg18Bit(ppgData, base + 3)

        red.add(redSample)
        ir.add(irSample)
    }

    return PpgFrameSamples(
        red = red,
        ir = ir
    )
}

private fun readPpg18Bit(
    data: ByteArray,
    index: Int
): Int {
    if (index + 2 >= data.size) return 0

    return ((data[index].toInt() and 0x03) shl 16) or
            ((data[index + 1].toInt() and 0xFF) shl 8) or
            (data[index + 2].toInt() and 0xFF)
}

private fun ppgRangeText(
    minValue: Int?,
    maxValue: Int?
): String {
    if (minValue == null || maxValue == null) return "-"
    return "${"%,d".format(minValue)} ~ ${"%,d".format(maxValue)}"
}


private const val IMU_LSB_PER_G = 1024.0

private data class ImuPose(
    val sampleCount: Int,
    val avgX: Double,
    val avgY: Double,
    val avgZ: Double,
    val magnitudeG: Double,
    val pitchDeg: Double,
    val rollDeg: Double,
    val movementScore: Double
) {
    companion object {
        fun empty(): ImuPose = ImuPose(
            sampleCount = 0,
            avgX = 0.0,
            avgY = 0.0,
            avgZ = 0.0,
            magnitudeG = 0.0,
            pitchDeg = 0.0,
            rollDeg = 0.0,
            movementScore = 0.0
        )
    }

    val movementLabel: String
        get() = when {
            sampleCount == 0 -> "--"
            movementScore < 15.0 -> "안정적"
            movementScore < 80.0 -> "약한 움직임"
            movementScore < 220.0 -> "움직임"
            else -> "큰 움직임"
        }

    fun smoothWith(
        previous: ImuPose,
        alpha: Double
    ): ImuPose {
        if (sampleCount == 0) return this
        if (previous.sampleCount == 0) return this

        fun lerp(old: Double, new: Double): Double = old + (new - old) * alpha

        return copy(
            avgX = lerp(previous.avgX, avgX),
            avgY = lerp(previous.avgY, avgY),
            avgZ = lerp(previous.avgZ, avgZ),
            magnitudeG = lerp(previous.magnitudeG, magnitudeG),
            pitchDeg = lerp(previous.pitchDeg, pitchDeg),
            rollDeg = lerp(previous.rollDeg, rollDeg),
            movementScore = lerp(previous.movementScore, movementScore)
        )
    }
}

private data class Vec3(
    val x: Double,
    val y: Double,
    val z: Double
)

private data class Face3d(
    val indices: List<Int>,
    val color: Color,
    val depth: Double
)

@Composable
private fun ImuRealtimeModelCard(
    imuPose: ImuPose,
    isConnected: Boolean
) {
    val statusColor = when {
        !isConnected -> Color(0xFFFF4B55)
        imuPose.sampleCount == 0 -> Color(0xFFFFD166)
        imuPose.movementScore >= 220.0 -> Color(0xFFFF922E)
        else -> Color(0xFF3DFF78)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF1E1E25))
            .border(
                width = 1.dp,
                color = Color(0xFF3DFF78).copy(alpha = 0.35f),
                shape = RoundedCornerShape(26.dp)
            )
            .padding(18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🧊 IMU 3D 움직임",
                color = Color(0xFF3DFF78),
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = imuPose.movementLabel,
                color = statusColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(statusColor.copy(alpha = 0.14f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "가속도 평균 벡터로 pitch/roll을 추정해서 Potch 기울기를 보여줍니다. yaw/절대 위치는 IMU 가속도만으로는 정확히 알 수 없습니다.",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 13.sp,
            lineHeight = 18.sp
        )

        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF121821))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(22.dp)
                )
                .padding(8.dp)
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                drawImuModel(imuPose)
            }
        }

        Spacer(Modifier.height(14.dp))
        DividerLine()
        Spacer(Modifier.height(12.dp))

        DevRow(
            label = "샘플 수",
            value = "${imuPose.sampleCount}개"
        )
        DevRow(
            label = "Pitch / Roll",
            value = if (imuPose.sampleCount == 0) "-" else {
                "${formatOneDecimal(imuPose.pitchDeg)}° / ${formatOneDecimal(imuPose.rollDeg)}°"
            }
        )
        DevRow(
            label = "가속도 크기",
            value = if (imuPose.sampleCount == 0) "-" else "${formatOneDecimal(imuPose.magnitudeG)} g"
        )
        DevRow(
            label = "평균 X / Y / Z",
            value = if (imuPose.sampleCount == 0) "-" else {
                "${imuPose.avgX.roundToInt()} / ${imuPose.avgY.roundToInt()} / ${imuPose.avgZ.roundToInt()}"
            }
        )
        DevRow(
            label = "움직임 score",
            value = if (imuPose.sampleCount == 0) "-" else formatOneDecimal(imuPose.movementScore),
            valueColor = statusColor
        )
    }
}

private fun DrawScope.drawImuModel(
    imuPose: ImuPose
) {
    drawImuGrid()

    val center = Offset(size.width / 2f, size.height / 2f)

    if (imuPose.sampleCount == 0) {
        drawLine(
            color = Color.White.copy(alpha = 0.20f),
            start = Offset(center.x - 70f, center.y),
            end = Offset(center.x + 70f, center.y),
            strokeWidth = 5f
        )
        drawLine(
            color = Color.White.copy(alpha = 0.20f),
            start = Offset(center.x, center.y - 45f),
            end = Offset(center.x, center.y + 45f),
            strokeWidth = 5f
        )
        return
    }

    val pitchRad = imuPose.pitchDeg * PI / 180.0
    val rollRad = imuPose.rollDeg * PI / 180.0

    val width = 1.45
    val height = 0.52
    val depth = 0.88

    val vertices = listOf(
        Vec3(-width, -height, -depth),
        Vec3(width, -height, -depth),
        Vec3(width, height, -depth),
        Vec3(-width, height, -depth),
        Vec3(-width, -height, depth),
        Vec3(width, -height, depth),
        Vec3(width, height, depth),
        Vec3(-width, height, depth)
    )

    val rotated = vertices.map { vertex ->
        rotateVec3(
            v = vertex,
            pitchRad = pitchRad,
            rollRad = rollRad
        )
    }

    val projected = rotated.map { projectVec3(it, center) }

    val faces = listOf(
        listOf(0, 1, 2, 3) to Color(0xFF4CD3FF),
        listOf(4, 5, 6, 7) to Color(0xFF2F8CFF),
        listOf(0, 1, 5, 4) to Color(0xFF3DFF78),
        listOf(2, 3, 7, 6) to Color(0xFFFFD166),
        listOf(1, 2, 6, 5) to Color(0xFFFF922E),
        listOf(0, 3, 7, 4) to Color(0xFFFF4B55)
    ).map { (indices, color) ->
        Face3d(
            indices = indices,
            color = color,
            depth = indices.map { rotated[it].z }.average()
        )
    }.sortedBy { it.depth }

    faces.forEach { face ->
        val path = Path().apply {
            val first = projected[face.indices.first()]
            moveTo(first.x, first.y)
            face.indices.drop(1).forEach { index ->
                val point = projected[index]
                lineTo(point.x, point.y)
            }
            close()
        }

        drawPath(
            path = path,
            color = face.color.copy(alpha = 0.34f)
        )
        drawPath(
            path = path,
            color = Color.White.copy(alpha = 0.30f),
            style = Stroke(width = 1.7f)
        )
    }

    drawGravityVector(imuPose, center)
}

private fun DrawScope.drawImuGrid() {
    val gridColor = Color.White.copy(alpha = 0.07f)

    for (i in 0..4) {
        val y = size.height * i / 4f
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f
        )
    }

    for (i in 0..4) {
        val x = size.width * i / 4f
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 1f
        )
    }
}

private fun DrawScope.drawGravityVector(
    imuPose: ImuPose,
    center: Offset
) {
    val vectorLength = 72f
    val mag = sqrt(
        imuPose.avgX * imuPose.avgX +
                imuPose.avgY * imuPose.avgY +
                imuPose.avgZ * imuPose.avgZ
    ).takeIf { it > 0.0 } ?: return

    val nx = imuPose.avgX / mag
    val ny = imuPose.avgY / mag

    val end = Offset(
        x = center.x + (nx * vectorLength).toFloat(),
        y = center.y - (ny * vectorLength).toFloat()
    )

    drawLine(
        color = Color.White.copy(alpha = 0.85f),
        start = center,
        end = end,
        strokeWidth = 4f
    )

    drawCircle(
        color = Color.White.copy(alpha = 0.90f),
        radius = 6f,
        center = end
    )
}

private fun rotateVec3(
    v: Vec3,
    pitchRad: Double,
    rollRad: Double
): Vec3 {
    // Roll around X axis.
    val cosRoll = cos(rollRad)
    val sinRoll = sin(rollRad)

    val y1 = v.y * cosRoll - v.z * sinRoll
    val z1 = v.y * sinRoll + v.z * cosRoll

    // Pitch around Y axis.
    val cosPitch = cos(pitchRad)
    val sinPitch = sin(pitchRad)

    val x2 = v.x * cosPitch + z1 * sinPitch
    val z2 = -v.x * sinPitch + z1 * cosPitch

    return Vec3(
        x = x2,
        y = y1,
        z = z2
    )
}

private fun DrawScope.projectVec3(
    v: Vec3,
    center: Offset
): Offset {
    val scale = minOf(size.width, size.height) * 0.28f

    // Simple isometric-like projection. Enough for a live debugging model.
    val x = center.x + ((v.x - v.z * 0.42) * scale).toFloat()
    val y = center.y + ((v.y + v.z * 0.24) * scale).toFloat()

    return Offset(x, y)
}

private fun calculateImuPose(
    imuData: ByteArray
): ImuPose {
    val sampleCount = imuData.size / 6
    if (sampleCount <= 0) return ImuPose.empty()

    val xs = ArrayList<Int>(sampleCount)
    val ys = ArrayList<Int>(sampleCount)
    val zs = ArrayList<Int>(sampleCount)

    for (i in 0 until sampleCount) {
        val base = i * 6
        xs.add(readInt16LittleEndian(imuData, base))
        ys.add(readInt16LittleEndian(imuData, base + 2))
        zs.add(readInt16LittleEndian(imuData, base + 4))
    }

    val avgX = xs.average()
    val avgY = ys.average()
    val avgZ = zs.average()

    val magnitudeRaw = sqrt(avgX * avgX + avgY * avgY + avgZ * avgZ)
    val magnitudeG = magnitudeRaw / IMU_LSB_PER_G

    val pitchDeg = atan2(
        -avgX,
        sqrt(avgY * avgY + avgZ * avgZ)
    ) * 180.0 / PI

    val rollDeg = atan2(avgY, avgZ) * 180.0 / PI

    val movementScore =
        averageAbsDiff(xs) + averageAbsDiff(ys) + averageAbsDiff(zs)

    return ImuPose(
        sampleCount = sampleCount,
        avgX = avgX,
        avgY = avgY,
        avgZ = avgZ,
        magnitudeG = magnitudeG,
        pitchDeg = pitchDeg,
        rollDeg = rollDeg,
        movementScore = movementScore
    )
}

private fun formatOneDecimal(value: Double): String {
    return "%.1f".format(value)
}

@Composable
private fun ArousalCalculationStatusCard(
    arousalState: ArousalState
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF1E1E25))
            .border(
                width = 1.dp,
                color = Color(0xFFFFB74D).copy(alpha = 0.42f),
                shape = RoundedCornerShape(26.dp)
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "📡 생체지표 계산 상태",
            color = Color(0xFFFFC46B),
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "데이터가 부족하거나 접촉·움직임 잡음으로 값이 필터링되면 계산 불가 이유를 표시합니다.",
            color = Color.White.copy(alpha = 0.58f),
            fontSize = 13.sp,
            lineHeight = 18.sp
        )

        Spacer(Modifier.height(2.dp))

        CalculationStatusItem(
            label = "RR",
            status = arousalState.rrCalculationStatus
        )
        CalculationStatusItem(
            label = "RRV",
            status = arousalState.rrvCalculationStatus
        )
        CalculationStatusItem(
            label = "HR",
            status = arousalState.hrCalculationStatus
        )
        CalculationStatusItem(
            label = "HRV",
            status = arousalState.hrvCalculationStatus
        )
    }
}

@Composable
private fun CalculationStatusItem(
    label: String,
    status: MetricCalculationStatus
) {
    val stateColor = when (status.state) {
        MetricCalculationState.VALID -> Color(0xFF3DFF78)
        MetricCalculationState.COLLECTING -> Color(0xFFFFD166)
        MetricCalculationState.REJECTED -> Color(0xFFFF5C68)
    }

    val stateText = when (status.state) {
        MetricCalculationState.VALID -> "정상"
        MetricCalculationState.COLLECTING -> "수집 중"
        MetricCalculationState.REJECTED -> "계산 불가"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .border(
                width = 1.dp,
                color = stateColor.copy(alpha = 0.28f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = stateText,
                color = stateColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(5.dp))

        Text(
            text = status.message,
            color = Color.White.copy(alpha = 0.68f),
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun ArousalStateCard(
    arousalState: ArousalState
) {
    val wakeCandidateColor =
        if (arousalState.isWakeTimingCandidate) Color(0xFF3DFF78)
        else Color(0xFFFFD166)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF1E1E25))
            .border(
                width = 1.dp,
                color = Color(0xFF8D7BFF).copy(alpha = 0.45f),
                shape = RoundedCornerShape(26.dp)
            )
            .padding(18.dp)
    ) {
        Text(
            text = "🧠 각성지표 상태",
            color = Color(0xFFB7A7FF),
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(14.dp))
        DividerLine()
        Spacer(Modifier.height(12.dp))

        DevRow(
            label = "Final Wake Score",
            value = formatArousalValue(arousalState.finalWakeScore, 3),
            valueColor = wakeCandidateColor
        )

        DevRow(
            label = "기상 후보",
            value = if (arousalState.isWakeTimingCandidate) "YES" else "NO",
            valueColor = wakeCandidateColor
        )

        Spacer(Modifier.height(10.dp))
        DividerLine()
        Spacer(Modifier.height(12.dp))

        ArousalSectionTitle("1. Micro Movement")

        DevRow(
            label = "variance",
            value = formatArousalValue(arousalState.microMovementVariance, 8)
        )
        DevRow(
            label = "score",
            value = formatArousalValue(arousalState.microMovementScore, 3)
        )

        Spacer(Modifier.height(10.dp))
        DividerLine()
        Spacer(Modifier.height(12.dp))

        ArousalSectionTitle("2. Respiratory Rate")
        DevRow(
            label = "RR final",
            value = arousalState.rrFinal?.let { "${formatArousalValue(it, 1)} bpm" } ?: "-"
        )
        DevRow(
            label = "RR PPG / IMU",
            value = "${formatArousalValue(arousalState.rrFromPpg, 1)} / ${formatArousalValue(arousalState.rrFromImu, 1)}"
        )
        DevRow(
            label = "RR source",
            value = arousalState.rrFusionSource.name
        )
        DevRow(
            label = "RR confidence",
            value = formatArousalValue(arousalState.rrFusionConfidence, 3)
        )
        DevRow(
            label = "RR score / raw",
            value = "${formatArousalValue(arousalState.rrScore, 3)} / ${formatArousalValue(arousalState.rrRawScore, 3)}"
        )
        ArousalLogBlock(
            title = "RR fusion log",
            value = arousalState.rrFusionLog
        )

        Spacer(Modifier.height(10.dp))
        DividerLine()
        Spacer(Modifier.height(12.dp))

        ArousalSectionTitle("3. Respiratory Rate Variability")
        DevRow(
            label = "RRV RMSSD",
            value = arousalState.rrvRmssdMs?.let { "${formatArousalValue(it, 1)} ms" }
                ?: arousalState.rrvRmssd?.let { "${formatArousalValue(it, 3)} sec" }
                ?: "-"
        )
        DevRow(
            label = "RRV source",
            value = arousalState.rrvSource.name
        )
        DevRow(
            label = "RRV quality",
            value = formatArousalValue(arousalState.rrvQuality, 3)
        )
        DevRow(
            label = "RRV score",
            value = formatArousalValue(arousalState.rrvScore, 3)
        )

        Spacer(Modifier.height(10.dp))
        DividerLine()
        Spacer(Modifier.height(12.dp))

        ArousalSectionTitle("4. Heart Rate")
        DevRow(
            label = "HR bpm",
            value = arousalState.hrBpm?.let { "$it bpm" } ?: "-"
        )
        DevRow(
            label = "HR gradient",
            value = arousalState.hrGradient?.let { "${formatArousalValue(it, 2)} bpm" } ?: "-"
        )
        DevRow(
            label = "HR score",
            value = formatArousalValue(arousalState.hrScore, 3)
        )

        Spacer(Modifier.height(10.dp))
        DividerLine()
        Spacer(Modifier.height(12.dp))

        ArousalSectionTitle("5. Heart Rate Variability")
        DevRow(
            label = "HRV RMSSD",
            value = arousalState.hrvRmssdMs?.let { "${formatArousalValue(it, 1)} ms" }
                ?: arousalState.hrvRmssd?.let { "${formatArousalValue(it, 3)} sec" }
                ?: "-"
        )
        DevRow(
            label = "LF / HF",
            value = "${formatArousalValue(arousalState.hrvLf, 3)} / ${formatArousalValue(arousalState.hrvHf, 3)}"
        )
        DevRow(
            label = "LF/HF ratio",
            value = formatArousalValue(arousalState.hrvLfHf, 3)
        )
        DevRow(
            label = "HRV score",
            value = formatArousalValue(arousalState.hrvScore, 3)
        )
        DevRow(
            label = "HRV quality",
            value = formatArousalValue(arousalState.hrvQuality, 3)
        )
        ArousalLogBlock(
            title = "HRV log",
            value = arousalState.hrvLog
        )

        Spacer(Modifier.height(10.dp))
        DividerLine()
        Spacer(Modifier.height(12.dp))

        ArousalSectionTitle("6. Skin Temperature")
        DevRow(
            label = "skin temp",
            value = arousalState.skinTemperatureCelsius?.let { "${formatArousalValue(it, 2)} °C" } ?: "-"
        )
        DevRow(
            label = "temp gradient",
            value = arousalState.skinTemperatureGradient?.let { "${formatArousalValue(it, 3)} °C" } ?: "-"
        )
        DevRow(
            label = "temp score",
            value = formatArousalValue(arousalState.skinTemperatureScore, 3)
        )

        Spacer(Modifier.height(10.dp))
        DividerLine()
        Spacer(Modifier.height(12.dp))

        ArousalLogBlock(
            title = "Arousal last log",
            value = arousalState.lastLog
        )
    }
}

@Composable
private fun ArousalSectionTitle(
    title: String
) {
    Text(
        text = title,
        color = Color(0xFFB7A7FF),
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold
    )

    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ArousalLogBlock(
    title: String,
    value: String?
) {
    Spacer(Modifier.height(6.dp))

    Text(
        text = title,
        color = Color.White.copy(alpha = 0.55f),
        fontSize = 14.sp
    )

    Spacer(Modifier.height(4.dp))

    Text(
        text = value ?: "-",
        color = Color.White.copy(alpha = 0.75f),
        fontSize = 12.sp,
        lineHeight = 17.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(10.dp)
    )
}

@Composable
private fun DeveloperCard(
    timestamp: Long?,
    packetLossCount: Int,
    sequenceErrorCount: Int,
    batteryVoltage: Double?,
    batteryRaw: Int?,
    imuSummary: ImuSummary?,
    lastLog: String,
    bleLog: String,
    isConnected: Boolean,
    deviceName: String?,
    mtu: Int,
    onReset: () -> Unit,
    bleState: PotchBleState,
    totalMiniPackets: Int,
    validMiniPackets: Int,
    damagedPacketCount: Int,
    estimatedLostPacketCount: Int,
    parsedSuperFrameCount: Int,
    lastFragCounter: Int?,
    expectedFragCounter: Int?,
    recentPacketErrors: List<PacketErrorLog>,

    onTestLengthError: () -> Unit,
    onTestMiniHeaderError: () -> Unit,
    onTestSequenceLoss: () -> Unit,
    onTestSuperHeaderError: () -> Unit,
    onTestCrcError: () -> Unit,
    onTestCounterWrapAround: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF1E1E25))
            .border(
                width = 1.dp,
                color = Color(0xFFFF8A22).copy(alpha = 0.45f),
                shape = RoundedCornerShape(26.dp)
            )
            .padding(18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🛠 개발자 모드",
                color = Color(0xFFFF922E),
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = "초기화",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        Spacer(Modifier.height(14.dp))
        DividerLine()

        DevRow(
            label = "연결 상태",
            value = if (isConnected) "연결됨" else "연결 안 됨",
            valueColor = if (isConnected) Color(0xFF3DFF78) else Color(0xFFFF4B55)
        )

        DevRow(
            label = "기기 이름",
            value = deviceName ?: "-"
        )

        DevRow(
            label = "MTU",
            value = "$mtu"
        )

        DevRow(
            label = "Timestamp",
            value = timestamp?.let { "$it ms (${formatMillis(it)})" } ?: "-"
        )

        DevRow(
            label = "CRC 오류 (누적)",
            value = "${packetLossCount}건",
            valueColor = if (packetLossCount == 0) Color(0xFF3DFF78) else Color(0xFFFF4B55)
        )

        DevRow(
            label = "Seq 오류 (누적)",
            value = "${sequenceErrorCount}건",
            valueColor = if (sequenceErrorCount == 0) Color(0xFF3DFF78) else Color(0xFFFF4B55)
        )
        DevRow(
            label = "수신 Mini Packet",
            value = "$validMiniPackets / $totalMiniPackets"
        )

        DevRow(
            label = "완성 Super Frame",
            value = "${parsedSuperFrameCount}개"
        )

        DevRow(
            label = "손상 패킷",
            value = "${damagedPacketCount}건",
            valueColor = if (damagedPacketCount == 0) Color(0xFF3DFF78) else Color(0xFFFF4B55)
        )

        DevRow(
            label = "손실 추정 Packet",
            value = "${estimatedLostPacketCount}개",
            valueColor = if (estimatedLostPacketCount == 0) Color(0xFF3DFF78) else Color(0xFFFF4B55)
        )

        DevRow(
            label = "최근 Counter",
            value = "${lastFragCounter ?: "-"} → 다음 ${expectedFragCounter ?: "-"}"
        )


        DevRow(
            label = "배터리 전압",
            value = if (batteryVoltage != null && batteryRaw != null) {
                "%.3fV (raw: $batteryRaw)".format(batteryVoltage)
            } else {
                "-"
            }
        )

        Spacer(Modifier.height(10.dp))
        DividerLine()
        Spacer(Modifier.height(12.dp))

        Text(
            text = "IMU 데이터 요약",
            color = Color(0xFFFF922E),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "샘플 수: ${imuSummary?.sampleCount ?: 0}개",
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 15.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AxisColumn(
                axis = "X축",
                color = Color(0xFFFF4B55),
                stat = imuSummary?.x
            )

            AxisColumn(
                axis = "Y축",
                color = Color(0xFF3DFF78),
                stat = imuSummary?.y
            )

            AxisColumn(
                axis = "Z축",
                color = Color(0xFF4CD3FF),
                stat = imuSummary?.z
            )
        }

        Spacer(Modifier.height(14.dp))
        DividerLine()
        Spacer(Modifier.height(12.dp))

        DevRow(
            label = "BLE 로그",
            value = bleLog
        )

        DevRow(
            label = "마지막 로그",
            value = lastLog
        )
        if (bleState.lastSavedLogPath != null) {
            DevRow(
                label = "저장된 로그 파일",
                value = bleState.lastSavedLogPath
            )
        }
        Spacer(Modifier.height(14.dp))
        DividerLine()
        Spacer(Modifier.height(12.dp))

        Text(
            text = "최근 패킷 이상 내역",
            color = Color(0xFFFF922E),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        if (recentPacketErrors.isEmpty()) {
            Text(
                text = "감지된 손상/손실 패킷이 없습니다.",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 14.sp
            )
        } else {
            recentPacketErrors.forEach { error ->
                PacketErrorRow(error)
            }
        }


        Spacer(Modifier.height(14.dp))
        DividerLine()
        Spacer(Modifier.height(12.dp))

        Text(
            text = "패킷 검증 테스트",
            color = Color(0xFFFF922E),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(10.dp))

        PacketTestButton(
            text = "길이 오류 테스트",
            onClick = { onTestLengthError() }
        )

        PacketTestButton(
            text = "Mini Header 오류 테스트",
            onClick = { onTestMiniHeaderError() }
        )

        PacketTestButton(
            text = "Sequence 손실 테스트",
            onClick = { onTestSequenceLoss() }
        )

        PacketTestButton(
            text = "Super Header 오류 테스트",
            onClick = { onTestSuperHeaderError() }
        )

        PacketTestButton(
            text = "CRC 오류 테스트",
            onClick = { onTestCrcError() }
        )

        PacketTestButton(
            text = "Counter 4095→0 테스트",
            onClick = { onTestCounterWrapAround() }
        )

    }
}

@Composable
private fun DevRow(
    label: String,
    value: String,
    valueColor: Color = Color.White
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 16.sp
        )

        Spacer(Modifier.weight(1f))

        Text(
            text = value,
            color = valueColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun AxisColumn(
    axis: String,
    color: Color,
    stat: AxisStat?
) {
    Column {
        Text(
            text = axis,
            color = color,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "avg: ${stat?.avg ?: "-"}",
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        )

        Text(
            text = "min: ${stat?.min ?: "-"}",
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        )

        Text(
            text = "max: ${stat?.max ?: "-"}",
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun DividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.09f))
    )
}

private data class AxisStat(
    val avg: String,
    val min: String,
    val max: String
)

private data class ImuSummary(
    val sampleCount: Int,
    val x: AxisStat,
    val y: AxisStat,
    val z: AxisStat,
    val movementLabel: String
)

private fun calculateImuSummary(imuData: ByteArray): ImuSummary {
    // Swift 코드 기준 IMU Data는 600 bytes.
    // 일반적인 6 bytes/sample 구조라고 보고
    // X/Y/Z = Int16 little endian으로 해석.
    val sampleCount = imuData.size / 6

    if (sampleCount <= 0) {
        return ImuSummary(
            sampleCount = 0,
            x = AxisStat("-", "-", "-"),
            y = AxisStat("-", "-", "-"),
            z = AxisStat("-", "-", "-"),
            movementLabel = "--"
        )
    }

    val xs = ArrayList<Int>(sampleCount)
    val ys = ArrayList<Int>(sampleCount)
    val zs = ArrayList<Int>(sampleCount)

    for (i in 0 until sampleCount) {
        val base = i * 6

        val x = readInt16LittleEndian(imuData, base)
        val y = readInt16LittleEndian(imuData, base + 2)
        val z = readInt16LittleEndian(imuData, base + 4)

        xs.add(x)
        ys.add(y)
        zs.add(z)
    }

    val movementScore =
        averageAbsDiff(xs) +
                averageAbsDiff(ys) +
                averageAbsDiff(zs)

    val movementLabel = when {
        movementScore < 15.0 -> "안정적"
        movementScore < 80.0 -> "약한 움직임"
        movementScore < 220.0 -> "움직임"
        else -> "큰 움직임"
    }

    return ImuSummary(
        sampleCount = sampleCount,
        x = xs.toAxisStat(),
        y = ys.toAxisStat(),
        z = zs.toAxisStat(),
        movementLabel = movementLabel
    )
}

private fun readInt16LittleEndian(data: ByteArray, index: Int): Int {
    val low = data[index].toInt() and 0xFF
    val high = data[index + 1].toInt()
    return ((high shl 8) or low).toShort().toInt()
}

private fun List<Int>.toAxisStat(): AxisStat {
    if (isEmpty()) return AxisStat("-", "-", "-")

    val avg = average().roundToInt()
    val min = minOrNull() ?: 0
    val max = maxOrNull() ?: 0

    return AxisStat(
        avg = "%,d".format(avg),
        min = "%,d".format(min),
        max = "%,d".format(max)
    )
}

private fun averageAbsDiff(values: List<Int>): Double {
    if (values.size < 2) return 0.0

    var sum = 0.0

    for (i in 1 until values.size) {
        sum += abs(values[i] - values[i - 1])
    }

    return sum / (values.size - 1)
}

private fun voltageToBatteryPercent(voltage: Double): Int {
    // 1셀 LiPo 기준 대략 매핑.
    // 실제 배터리 특성에 맞게 나중에 보정하면 됨.
    val minVoltage = 3.30
    val maxVoltage = 4.20

    return (((voltage - minVoltage) / (maxVoltage - minVoltage)) * 100.0)
        .roundToInt()
        .coerceIn(0, 100)
}

private fun formatMillis(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

@Composable
private fun PacketErrorRow(
    error: PacketErrorLog
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = error.type,
                color = when (error.type) {
                    "CRC" -> Color(0xFFFF4B55)
                    "SEQUENCE" -> Color(0xFFFFD166)
                    "LENGTH" -> Color(0xFFFF922E)
                    else -> Color(0xFF4CD3FF)
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = error.fragCounter?.let { "counter=$it" } ?: "",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = error.message,
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun PacketTestButton(
    text: String,
    onClick: () -> Unit
) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White.copy(alpha = 0.08f),
            contentColor = Color.White
        ),
        onClick = onClick
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun InternalLogFileExportCard(
    files: List<InternalPotchLogFile>,
    selectedFileNames: List<String>,
    lastExportMessage: String?,
    onRefresh: () -> Unit,
    onToggleSelect: (String) -> Unit,
    onExportSelected: () -> Unit,
    onDeleteLogs: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF1E1E25))
            .border(
                width = 1.dp,
                color = Color(0xFF4CD3FF).copy(alpha = 0.35f),
                shape = RoundedCornerShape(26.dp)
            )
            .padding(18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📁 내부 로그 파일",
                color = Color(0xFF4CD3FF),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = "새로고침",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable { onRefresh() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "앱 내부 저장소에 실시간 append된 CSV 파일 목록입니다. 선택 후 Download/PotchLogs로 내보낼 수 있습니다.",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 13.sp,
            lineHeight = 18.sp
        )

        Spacer(Modifier.height(14.dp))
        DividerLine()
        Spacer(Modifier.height(12.dp))

        if (files.isEmpty()) {
            Text(
                text = "내부 저장소에 로그 파일이 없습니다.",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 14.sp
            )
        } else {
            files.forEach { file ->
                InternalLogFileRow(
                    file = file,
                    selected = selectedFileNames.contains(file.name),
                    onClick = {
                        onToggleSelect(file.name)
                    }
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(18.dp),
            enabled = selectedFileNames.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2F8CFF),
                contentColor = Color.White,
                disabledContainerColor = Color.White.copy(alpha = 0.08f),
                disabledContentColor = Color.White.copy(alpha = 0.35f)
            ),
            onClick = {
                try{
                    onExportSelected()
                }catch(e:Exception){
                    Log.e("export", "Fucking error: $e")
                }

            }
        ) {
            Text(
                text = if (selectedFileNames.isEmpty()) {
                    "내보낼 파일 선택"
                } else {
                    "선택한 ${selectedFileNames.size}개 파일 내보내기"
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(10.dp))

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(18.dp),
            enabled = files.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFD83A40),
                contentColor = Color.White,
                disabledContainerColor = Color.White.copy(alpha = 0.08f),
                disabledContentColor = Color.White.copy(alpha = 0.35f)
            ),
            onClick = {
                try {
                    onDeleteLogs()
                } catch (e: Exception) {
                    Log.e("InternalLogDelete", "delete error: $e")
                }
            }
        ) {
            Text(
                text = if (selectedFileNames.isEmpty()) {
                    "전체 삭제"
                } else {
                    "선택한 파일 삭제"
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (lastExportMessage != null) {
            Spacer(Modifier.height(10.dp))

            Text(
                text = lastExportMessage,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun InternalLogFileRow(
    file: InternalPotchLogFile,
    selected: Boolean,
    onClick: () -> Unit
) {
    val modifiedText = remember(file.lastModifiedMillis) {
        SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            Locale.getDefault()
        ).format(Date(file.lastModifiedMillis))
    }

    val sizeText = remember(file.sizeBytes) {
        formatFileSize(file.sizeBytes)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) Color(0xFF2F8CFF).copy(alpha = 0.20f)
                else Color.White.copy(alpha = 0.05f)
            )
            .border(
                width = 1.dp,
                color =
                    if (selected) Color(0xFF2F8CFF).copy(alpha = 0.85f)
                    else Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (selected) "☑" else "☐",
                color = if (selected) Color(0xFF4CD3FF) else Color.White.copy(alpha = 0.5f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.padding(horizontal = 4.dp))

            Text(
                text = file.name,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = "$sizeText · $modifiedText",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun formatArousalValue(
    value: Double?,
    digits: Int
): String {
    return value?.let {
        String.format(Locale.US, "%.${digits}f", it)
    } ?: "-"
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024L -> {
            "%.2f MB".format(bytes / (1024.0 * 1024.0))
        }

        bytes >= 1024L -> {
            "%.1f KB".format(bytes / 1024.0)
        }

        else -> {
            "$bytes B"
        }
    }
}

private fun buildHeartRateFilteredIrSamples(
    rawIrSamples: List<Int>
): List<Double> {
    if (rawIrSamples.size < 2) return emptyList()

    val signal = rawIrSamples.map { it.toDouble() }
    val mean = signal.average()

    val acSignal = DoubleArray(signal.size) { i ->
        signal[i] - mean
    }

    val halfWin = 7
    val filtered = ArrayList<Double>(signal.size)

    for (i in acSignal.indices) {
        val lo = (i - halfWin).coerceAtLeast(0)
        val hi = (i + halfWin).coerceAtMost(acSignal.lastIndex)

        var sum = 0.0
        for (j in lo..hi) {
            sum += acSignal[j]
        }

        filtered.add(sum / (hi - lo + 1))
    }

    return filtered
}

@Composable
private fun HeartRateFilteredPpgGraphCard(
    filteredIrSamples: List<Double>,
    isConnected: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF1E1E25))
            .border(
                width = 1.dp,
                color = Color(0xFFFFD166).copy(alpha = 0.45f),
                shape = RoundedCornerShape(26.dp)
            )
            .padding(18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "💓 HR 계산용 필터링 PPG",
                color = Color(0xFFFFD166),
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = if (isConnected) "FILTERED" else "대기",
                color = if (isConnected) Color(0xFF3DFF78) else Color.White.copy(alpha = 0.45f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = "IR raw 평균 제거 + halfWin=7 이동평균 smoothing. 심박 peak 검출에 들어가는 형태와 유사한 파형입니다.",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 13.sp,
            lineHeight = 18.sp
        )

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = 0.24f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(10.dp)
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                drawPpgGrid()

                if (filteredIrSamples.size >= 2) {
                    drawDoublePpgPath(
                        samples = filteredIrSamples,
                        color = Color(0xFFFFD166),
                        strokeWidthPx = 2.5f
                    )
                }
            }

            if (filteredIrSamples.isEmpty()) {
                Text(
                    text = "필터링된 PPG 데이터 대기 중",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "buffer=${filteredIrSamples.size} samples",
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun DrawScope.drawDoublePpgPath(
    samples: List<Double>,
    color: Color,
    strokeWidthPx: Float
) {
    if (samples.size < 2) return

    val minValue = samples.minOrNull() ?: return
    val maxValue = samples.maxOrNull() ?: return
    val range = (maxValue - minValue).takeIf { it > 0.0 } ?: 1.0

    val path = Path()

    samples.forEachIndexed { index, value ->
        val x = size.width * index.toFloat() / (samples.size - 1).toFloat()
        val normalized = ((value - minValue) / range).toFloat()
        val y = size.height - normalized * size.height

        if (index == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidthPx)
    )
}