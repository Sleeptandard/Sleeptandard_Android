package com.leejang.sleeptandard.Screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- 데이터 모델 ---
data class PotchLog(
    val date: String,
    val day: String,
    val sleepTime: String,
    val score: Int,
    val temp: String,
    val battery: String
)

// --- 더미 데이터 (사진 기반) ---
val dummyLogs = listOf(
    PotchLog("JAN 29", "SAT", "8h 12m", 88, "36.5°C", "92%"),
    PotchLog("JAN 28", "SUN", "8h 12m", 89, "36.5°C", "92%"),
    PotchLog("JAN 27", "MON", "8h 12m", 99, "36.5°C", "92%"),
    PotchLog("JAN 26", "TUE", "8h 12m", 88, "36.5°C", "92%"),
    PotchLog("JAN 25", "WED", "8h 12m", 75, "36.5°C", "92%")
)

@Composable
fun ExperimentScreen() {
    // 배경색: 사진의 어두운 남색 계열 (#111723)
    val backgroundColor = Color(0xFF111723)
    val cardColor = Color.White.copy(alpha = 0.08f)
    val accentColor = Color(0xFFB9E5EA)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(16.dp)
    ) {
        Text(
            text = "POTCH DATA JOURNAL",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Weekly Summary Card
            item {
                WeeklySummaryCard(cardColor, accentColor)
            }

            // 2. Sleep Chart Card
            item {
                SleepChartCard(cardColor, accentColor)
            }

            // 3. Data Log Section
            item {
                Text(
                    text = "DATA LOG",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(dummyLogs) { log ->
                DataLogItem(log, cardColor, accentColor)
            }
        }
    }
}

@Composable
fun WeeklySummaryCard(backgroundColor: Color, accentColor: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("JAN 23 - JAN 29", color = Color.Gray, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                SummaryItem(Icons.Default.NightsStay, "avg. sleep", "8h 12m", accentColor)
                SummaryItem(Icons.Default.DeviceThermostat, "avg. temp", "36.5°C", Color.Magenta)
                SummaryItem(Icons.Default.BatteryFull, "avg. battery", "92%", Color.Green)
            }
        }
    }
}

@Composable
fun SummaryItem(icon: ImageVector, label: String, value: String, iconColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
        Text(label, color = Color.Gray, fontSize = 10.sp)
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SleepChartCard(backgroundColor: Color, accentColor: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().height(200.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            // 차트 로직은 복잡하므로 텍스트로 대체하거나 간단한 박스로 표현 가능
            Text("Sleep Trend Chart (Bar)", color = Color.Gray)
            // 여기에 나중에 Canvas나 Chart Library를 붙이시면 됩니다.
        }
    }
}

@Composable
fun DataLogItem(log: PotchLog, backgroundColor: Color, accentColor: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(log.date, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(log.day, color = Color.Gray, fontSize = 10.sp)
            }

            Text(log.sleepTime, color = Color.White, fontSize = 14.sp)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${log.score}", color = accentColor, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text("SCORE", color = Color.Gray, fontSize = 8.sp)
            }

            Column {
                Text("TEMP", color = Color.Gray, fontSize = 9.sp)
                Text(log.temp, color = Color.White, fontSize = 12.sp)
            }

            Column {
                Text("BATTERY", color = Color.Gray, fontSize = 9.sp)
                Text(log.battery, color = Color.White, fontSize = 12.sp)
            }
        }
    }
}