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
import com.leejang.sleeptandard.Potch.PotchBleState
import com.leejang.sleeptandard.Potch.PotchBleViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

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
            packetLossCount = processorState.packetLossCount,
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
            bleState = bleState
        )

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor =
                    if (bleState.isConnected) Color(0xFFD83A40)
                    else Color(0xFF2F8CFF)
            ),
            onClick = {
                if (bleState.isConnected) {
                    viewModel.disconnect()
                } else {
                    viewModel.startScan()
                }
            }
        ) {
            Text(
                text = when {
                    bleState.isConnected -> "연결 해제"
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
    bleState: PotchBleState
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