package com.leejang.sleeptandard.Component.journal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leejang.sleeptandard.ui.theme.SkyBlue
import com.leejang.sleeptandard.ui.theme.White
import java.time.LocalDate

/** 캘린더 일일 탭의 통계 본문. 날짜와 메뉴는 부모가 관리한다. */
@Composable
internal fun JournalDailyContent(today: LocalDate, date: LocalDate, compact: Boolean, modifier: Modifier = Modifier) {
    val day = remember(date, today) { previewDay(date, today) }
    val gap = if (compact) 6.dp else 12.dp
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(Modifier.fillMaxWidth().weight(1.18f), horizontalArrangement = Arrangement.spacedBy(gap)) {
            StatisticsValueCard("수면 시간", day?.report?.sleepMinutes?.let { "${it / 60}시간 ${it % 60}분" } ?: "—", "",
                Modifier.weight(1f).fillMaxSize(), compact, smallValue = true)
            StatisticsStageCard("수면 비율", day?.stagePercents, Modifier.weight(1f).fillMaxSize(), compact)
        }
        Row(Modifier.fillMaxWidth().weight(0.91f), horizontalArrangement = Arrangement.spacedBy(gap)) {
            StatisticsValueCard("HR", day?.heartRate?.toString() ?: "—", "bpm · 심박수", Modifier.weight(1f).fillMaxSize(), compact, accent = JournalWarm)
            StatisticsValueCard("RR", day?.respirationRate?.toString() ?: "—", "회/분 · 호흡수", Modifier.weight(1f).fillMaxSize(), compact, accent = JournalLavender)
        }
        Row(Modifier.fillMaxWidth().weight(0.91f), horizontalArrangement = Arrangement.spacedBy(gap)) {
            StatisticsValueCard("입면 시간", day?.latencyMinutes?.toString() ?: "—", "분", Modifier.weight(1f).fillMaxSize(), compact)
            StatisticsValueCard("수면 점수", day?.report?.overallScore?.toString() ?: "—", "점 / 100", Modifier.weight(1f).fillMaxSize(), compact,
                accent = day?.report?.overallScore?.let { scoreColor(it.toFloat()) } ?: SkyBlue, hero = true)
        }
        TextButton(
            onClick = {}, // 요청대로 수면 리듬 이벤트는 비워 둔다.
            modifier = Modifier.fillMaxWidth().height(36.dp).border(1.dp, SkyBlue.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
        ) {
            Icon(Icons.AutoMirrored.Outlined.ShowChart, null, Modifier.size(18.dp), tint = SkyBlue)
            Spacer(Modifier.width(7.dp))
            JournalLabel("수면 리듬", color = SkyBlue)
        }
    }
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
    val valueSize = if (smallValue) { if (compact) 19 else 23 } else { if (compact) 22 else 36 }
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

