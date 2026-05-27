package com.leejang.sleeptandard.Screen

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
import com.leejang.sleeptandard.Potch.PacketErrorLog
import com.leejang.sleeptandard.Potch.PotchBleState
import com.leejang.sleeptandard.Potch.PotchBleViewModel
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import com.leejang.sleeptandard.Potch.InternalPotchLogFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExperimentScreen(
    viewModel: PotchBleViewModel = viewModel()
) {
    val bleState by viewModel.bleState.collectAsState()
    val processorState by viewModel.processorState.collectAsState()

    val sensorData = processorState.lastParsedData

    val temperatureText =
        sensorData?.let { "%.1f°C".format(it.ntcCelsius) } ?: "--°C"

    // 현재 Swift 로직에는 심박수 파싱이 없어서 아직 "-- bpm"으로 표시
    val heartRateText = "-- bpm"

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
    onExportSelected: () -> Unit
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
            onClick = onExportSelected
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