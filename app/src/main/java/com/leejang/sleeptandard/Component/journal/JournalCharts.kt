package com.leejang.sleeptandard.Component.journal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leejang.sleeptandard.ui.theme.SkyBlue
import com.leejang.sleeptandard.ui.theme.White
import java.time.LocalDate

/** null은 기록이 없는 날이다. 빈 날짜를 가로질러 선을 연결하지 않는다. */
@Composable
internal fun JournalLineChart(
    values: List<Float?>,
    modifier: Modifier = Modifier,
    min: Float = 0f,
    max: Float = 100f,
    scoreColors: Boolean = true,
    selectedIndex: Int = -1,
    showValues: Boolean = false,
    description: String = "수면 점수 추이",
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp)
    Canvas(modifier.semantics { contentDescription = description }) {
        if (size.height <= 0f || values.isEmpty()) return@Canvas
        val inset = if (showValues) 13.dp.toPx().coerceAtMost(size.height * 0.3f) else 5.dp.toPx()
        val plotHeight = (size.height - inset * 2).coerceAtLeast(1f)
        val slot = size.width / values.size
        fun point(index: Int, value: Float) = Offset(slot * (index + 0.5f), inset + (1 - ((value - min) / (max - min)).coerceIn(0f, 1f)) * plotHeight)
        fun tint(value: Float) = if (scoreColors) scoreColor(value) else SkyBlue
        for (fraction in listOf(0f, 0.5f, 1f)) {
            val y = inset + plotHeight * fraction
            drawLine(JournalMuted.copy(alpha = 0.10f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        if (selectedIndex in values.indices) {
            val x = slot * (selectedIndex + 0.5f)
            drawLine(SkyBlue.copy(alpha = 0.12f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
        }
        values.zipWithNext().forEachIndexed { index, (a, b) ->
            if (a != null && b != null) {
                val start = point(index, a)
                val end = point(index + 1, b)
                drawLine(Brush.linearGradient(listOf(tint(a), tint(b)), start, end), start, end, 1.6.dp.toPx(), StrokeCap.Round)
            }
        }
        values.forEachIndexed { index, value ->
            if (value != null) {
                val position = point(index, value)
                if (index == selectedIndex) drawCircle(tint(value).copy(alpha = 0.16f), 6.dp.toPx(), position)
                drawCircle(tint(value), if (index == selectedIndex) 3.dp.toPx() else 2.dp.toPx(), position)
                if (showValues) {
                    val label = textMeasurer.measure(value.toInt().toString(), labelStyle.merge(TextStyle(color = tint(value))))
                    drawText(label, topLeft = Offset(position.x - label.size.width / 2, (position.y - label.size.height - 4.dp.toPx()).coerceAtLeast(0f)))
                }
            }
        }
    }
}

/** 주간/월간 공용 그래프. 수치 지표는 선, 수면 단계 분포는 누적 막대로 전환한다. */
@Composable
internal fun JournalPeriodChart(
    dates: List<LocalDate>,
    days: List<JournalDayPreview?>,
    selectedDate: LocalDate,
    metric: JournalChartMetric,
    onMetric: (JournalChartMetric) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val numeric = metric.numeric
    val selected = dates.indexOf(selectedDate)
    val selectedDay = days.getOrNull(selected)
    val values = days.map { day -> if (day != null && numeric != null) numeric.value(day) else null }
    val selectedValue = values.getOrNull(selected)
    JournalPanel(modifier.testTag("journal-period-chart"), padding = 10.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                TextButton(onClick = { menuOpen = true }, modifier = Modifier.height(32.dp)) {
                    Text(metric.title, color = White, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp))
                    Icon(Icons.Outlined.KeyboardArrowDown, "그래프 항목 선택", Modifier.size(17.dp), tint = JournalMuted)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = Color(0xFF15263A)) {
                    JournalChartMetric.entries.forEach { item ->
                        DropdownMenuItem(text = { JournalLabel(item.title, color = if (item == metric) SkyBlue else White) },
                            onClick = { onMetric(item); menuOpen = false })
                    }
                }
            }
            JournalLabel(when {
                selectedDay == null -> "기록 없음"
                numeric == null -> "${selectedDate.dayOfMonth}일"
                selectedValue != null -> "${numeric.format(selectedValue)} ${numeric.unit}"
                else -> "—"
            }, size = 11, color = if (numeric == JournalMetric.SCORE && selectedValue != null) scoreColor(selectedValue) else SkyBlue)
        }
        Row(Modifier.fillMaxWidth().weight(1f)) {
            val min = numeric?.min ?: 0f
            val max = numeric?.max ?: 100f
            Column(Modifier.width(28.dp).fillMaxSize().padding(vertical = 5.dp), verticalArrangement = Arrangement.SpaceBetween) {
                JournalLabel(max.toInt().toString(), size = 9)
                JournalLabel(((min + max) / 2).toInt().toString(), size = 9)
                JournalLabel(min.toInt().toString(), size = 9)
            }
            if (numeric != null) {
                JournalLineChart(values, Modifier.weight(1f).fillMaxSize().testTag("journal-line-plot"), min, max,
                    scoreColors = numeric == JournalMetric.SCORE, selectedIndex = selected,
                    description = dates.zip(values).joinToString(", ") { (date, value) -> "${date.monthValue}월 ${date.dayOfMonth}일 ${value?.let { numeric.format(it) + numeric.unit } ?: "기록 없음"}" })
            } else {
                StageDistributionPlot(dates, days, selected, Modifier.weight(1f).fillMaxSize())
            }
        }
        ChartDates(dates)
        when {
            numeric == JournalMetric.SCORE -> ScoreLegend()
            numeric != null -> JournalLabel("단위 ${numeric.unit} · 날짜별 예시 측정값", size = 9)
            else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                JournalStageNames.forEachIndexed { index, name ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.size(5.dp).background(JournalStageColors[index], CircleShape))
                        JournalLabel("$name ${selectedDay?.let { "${it.stagePercents[index]}%" } ?: "—"}", size = 9)
                    }
                }
            }
        }
    }
}

@Composable
private fun StageDistributionPlot(dates: List<LocalDate>, days: List<JournalDayPreview?>, selected: Int, modifier: Modifier) {
    val description = dates.zip(days).joinToString(", ") { (date, day) ->
        "${date.monthValue}월 ${date.dayOfMonth}일 " + (day?.stagePercents?.mapIndexed { index, percent -> "${JournalStageNames[index]} $percent%" }?.joinToString(" ") ?: "기록 없음")
    }
    Canvas(modifier.padding(vertical = 5.dp).testTag("journal-stage-plot").semantics { contentDescription = description }) {
        val slot = size.width / days.size
        for (fraction in listOf(0f, 0.5f, 1f)) {
            drawLine(JournalMuted.copy(alpha = 0.10f), Offset(0f, size.height * fraction), Offset(size.width, size.height * fraction), 1.dp.toPx())
        }
        days.forEachIndexed { index, day ->
            if (day != null) {
                val width = (slot * 0.55f).coerceAtMost(26.dp.toPx())
                var bottom = size.height
                day.stagePercents.forEachIndexed { stage, percent ->
                    val height = size.height * percent / 100
                    drawRect(JournalStageColors[stage].copy(alpha = if (index == selected) 1f else 0.72f),
                        Offset(slot * (index + 0.5f) - width / 2, bottom - height), Size(width, height))
                    bottom -= height
                }
            }
        }
    }
}

@Composable
private fun ChartDates(dates: List<LocalDate>) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = JournalMuted)
    Canvas(Modifier.fillMaxWidth().height(18.dp).padding(start = 28.dp, top = 2.dp)) {
        val ticks = if (dates.size <= 7) dates.indices.toList() else listOf(0, 7, 14, 21, dates.lastIndex).distinct()
        ticks.forEach { index ->
            val label = measurer.measure("${dates[index].dayOfMonth}", style)
            val x = size.width / dates.size * (index + 0.5f) - label.size.width / 2
            drawText(label, topLeft = Offset(x.coerceIn(0f, (size.width - label.size.width).coerceAtLeast(0f)), 0f))
        }
    }
}
@Composable
internal fun ScoreLegend() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("0–50" to JournalLow, "51–75" to JournalWarm, "76–100" to SkyBlue).forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(5.dp).background(color, CircleShape))
                JournalLabel(label, size = 9)
            }
        }
    }
}
