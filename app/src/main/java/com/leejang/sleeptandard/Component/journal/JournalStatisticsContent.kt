package com.leejang.sleeptandard.Component.journal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leejang.sleeptandard.ui.theme.Key
import com.leejang.sleeptandard.ui.theme.SkyBlue
import com.leejang.sleeptandard.ui.theme.Sleeptandard_MVP_DemoTheme
import com.leejang.sleeptandard.ui.theme.White
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun JournalStatisticsContent(
    today: LocalDate,
    date: LocalDate,
    period: JournalStatisticsPeriod,
    onDate: (LocalDate) -> Unit,
    onPeriod: (JournalStatisticsPeriod) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val range = remember(period, date, today) { statisticsRange(period, date, today) }
    val summary = remember(range, today) { previewStatistics(range, today) }
    BoxWithConstraints(modifier.fillMaxSize().background(Key), contentAlignment = Alignment.TopCenter) {
        val compact = maxHeight < 640.dp
        val gap = if (compact) 8.dp else 12.dp
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxSize().padding(horizontal = 20.dp, vertical = if (compact) 8.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "통계 닫기", Modifier.size(21.dp), tint = SkyBlue)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("수면 통계", color = White, style = MaterialTheme.typography.titleLarge.copy(fontSize = if (compact) 20.sp else 22.sp))
                    JournalLabel("나의 수면을 한눈에", size = 11)
                }
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF101F32)).padding(3.dp).selectableGroup()) {
                JournalStatisticsPeriod.entries.forEach { item ->
                    Box(
                        Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(11.dp))
                            .background(if (period == item) Color(0xFF263F56) else Color.Transparent)
                            .selectable(period == item, role = Role.Tab, onClick = { onPeriod(item) }),
                        contentAlignment = Alignment.Center,
                    ) { JournalLabel(item.title, size = 11, color = if (period == item) SkyBlue else JournalMuted) }
                }
            }
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onDate(period.shift(date, -1)) }, enabled = period != JournalStatisticsPeriod.ALL) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "이전 통계 기간", tint = if (period == JournalStatisticsPeriod.ALL) JournalMuted.copy(alpha = 0.25f) else JournalMuted)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    JournalLabel(statisticsDateTitle(period, date), size = 14, color = White)
                    if (period != JournalStatisticsPeriod.DAY) {
                        JournalLabel("${range.start.format(StatisticsDateFormat)} – ${range.end.format(StatisticsDateFormat)}", size = 10)
                    }
                }
                IconButton(onClick = { onDate(period.shift(date, 1)) }, enabled = period != JournalStatisticsPeriod.ALL) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "다음 통계 기간", tint = if (period == JournalStatisticsPeriod.ALL) JournalMuted.copy(alpha = 0.25f) else JournalMuted)
                }
            }
            JournalLabel(
                if (summary.recordCount == 0) "이 기간에는 기록이 없어요" else "예시 데이터 · ${summary.recordCount}일의 기록",
                Modifier.align(Alignment.CenterHorizontally), size = 10,
            )
            Row(Modifier.fillMaxWidth().weight(1.18f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                StatisticsValueCard(
                    period.label("수면 시간"), summary.sleepMinutes?.let { "${it / 60}시간 ${it % 60}분" } ?: "—", "",
                    Modifier.weight(1f).fillMaxSize(), compact = compact, smallValue = true,
                )
                StatisticsStageCard(period.label("수면 비율"), summary.stagePercents, Modifier.weight(1f).fillMaxSize(), compact)
            }
            Row(Modifier.fillMaxWidth().weight(0.91f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                StatisticsValueCard(period.label("HR"), summary.heartRate?.toString() ?: "—", "bpm · 심박수", Modifier.weight(1f).fillMaxSize(), compact, accent = JournalWarm)
                StatisticsValueCard(period.label("RR"), summary.respirationRate?.toString() ?: "—", "회/분 · 호흡수", Modifier.weight(1f).fillMaxSize(), compact, accent = JournalLavender)
            }
            Row(Modifier.fillMaxWidth().weight(0.91f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                StatisticsValueCard(period.label("입면 시간"), summary.latencyMinutes?.toString() ?: "—", "분", Modifier.weight(1f).fillMaxSize(), compact)
                StatisticsValueCard(period.label("수면 점수"), summary.score?.toString() ?: "—", "점 / 100", Modifier.weight(1f).fillMaxSize(), compact, accent = summary.score?.let { scoreColor(it.toFloat()) } ?: SkyBlue, hero = true)
            }
            TextButton(
                onClick = {}, // 수면 리듬 기능은 다음 구현 범위. 요청대로 빈 이벤트를 유지한다.
                modifier = Modifier.fillMaxWidth().height(40.dp).border(1.dp, SkyBlue.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
            ) {
                Icon(Icons.AutoMirrored.Outlined.ShowChart, null, Modifier.size(18.dp), tint = SkyBlue)
                Spacer(Modifier.width(7.dp))
                JournalLabel("수면 리듬", color = SkyBlue)
            }
        }
    }
}

private val StatisticsDateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd")

internal fun statisticsDateTitle(period: JournalStatisticsPeriod, date: LocalDate): String = when (period) {
    JournalStatisticsPeriod.DAY -> "${date.year}년 ${date.monthValue}월 ${date.dayOfMonth}일"
    JournalStatisticsPeriod.WEEK -> "일주일 통계"
    JournalStatisticsPeriod.MONTH -> "${date.year}년 ${date.monthValue}월"
    JournalStatisticsPeriod.YEAR -> "${date.year}년"
    JournalStatisticsPeriod.ALL -> "전체 기록"
}

@Composable
private fun StatisticsValueCard(
    title: String,
    value: String,
    unit: String,
    modifier: Modifier,
    compact: Boolean,
    accent: Color = SkyBlue,
    smallValue: Boolean = false,
    hero: Boolean = false,
) {
    val valueSize = if (smallValue) { if (compact) 19 else 23 } else { if (compact) 24 else 36 }
    JournalPanel(modifier, hero = hero, padding = if (compact) 8.dp else 16.dp) {
        JournalLabel(title, size = 12)
        Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center) {
            Text(value, color = accent, style = MaterialTheme.typography.titleLarge.copy(
                fontSize = valueSize.sp,
                lineHeight = (valueSize + 4).sp,
                fontWeight = FontWeight.Medium,
            ))
            if (unit.isNotEmpty()) JournalLabel(unit, size = if (compact) 9 else 10)
        }
    }
}

@Composable
private fun StatisticsStageCard(title: String, percents: List<Int>?, modifier: Modifier, compact: Boolean) {
    JournalPanel(modifier, padding = if (compact) 8.dp else 16.dp) {
        JournalLabel(title, size = 12)
        Row(Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.weight(0.46f).fillMaxSize().semantics {
                contentDescription = percents?.mapIndexed { index, percent -> "${JournalStageNames[index]} $percent%" }?.joinToString(", ") ?: "수면 비율 기록 없음"
            }) {
                val diameter = size.minDimension.coerceAtMost(100.dp.toPx())
                val stroke = diameter * 0.15f
                val origin = Offset((size.width - diameter + stroke) / 2, (size.height - diameter + stroke) / 2)
                val arcSize = Size(diameter - stroke, diameter - stroke)
                if (percents == null) drawArc(JournalMuted.copy(alpha = 0.15f), 0f, 360f, false, origin, arcSize, style = Stroke(stroke))
                else {
                    var start = -90f
                    percents.forEachIndexed { index, percent ->
                        val sweep = percent * 3.6f
                        if (sweep > 2f) drawArc(JournalStageColors[index], start + 1f, sweep - 2f, false, origin, arcSize, style = Stroke(stroke))
                        start += sweep
                    }
                }
            }
            Column(Modifier.weight(0.54f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                JournalStageNames.forEachIndexed { index, name ->
                    JournalLabel("$name ${percents?.let { "${it[index]}%" } ?: "—"}", size = 9, color = JournalStageColors[index])
                }
            }
        }
    }
}

@Preview(name = "하루 통계 · 작은 화면", widthDp = 320, heightDp = 480)
@Composable
private fun StatisticsDayPreview() {
    Sleeptandard_MVP_DemoTheme {
        JournalStatisticsContent(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 16), JournalStatisticsPeriod.DAY, {}, {}, {})
    }
}

@Preview(name = "한 달 통계", widthDp = 390, heightDp = 680)
@Composable
private fun StatisticsMonthPreview() {
    Sleeptandard_MVP_DemoTheme {
        JournalStatisticsContent(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 16), JournalStatisticsPeriod.MONTH, {}, {}, {})
    }
}
