package com.leejang.sleeptandard.Component.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowUp
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leejang.sleeptandard.ui.theme.Key
import com.leejang.sleeptandard.ui.theme.SkyBlue
import com.leejang.sleeptandard.ui.theme.Sleeptandard_MVP_DemoTheme
import com.leejang.sleeptandard.ui.theme.White
import java.time.LocalDate
import java.time.YearMonth

internal enum class JournalCalendarMode(val title: String) { DAY("일일"), WEEK("주간"), MONTH("월간") }

@Composable
internal fun JournalCalendarContent(
    today: LocalDate,
    selectedDate: LocalDate,
    mode: JournalCalendarMode,
    metric: JournalChartMetric,
    onDate: (LocalDate) -> Unit,
    onMode: (JournalCalendarMode) -> Unit,
    onMetric: (JournalChartMetric) -> Unit,
    onMain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val month = YearMonth.from(selectedDate)
    val week = remember(selectedDate) { weekDates(selectedDate) }
    val weeks = remember(month) { monthWeeks(month) }
    val days = remember(week, today) { week.map { previewDay(it, today) } }
    val monthDates = remember(month) { (1..month.lengthOfMonth()).map(month::atDay) }
    val monthDays = remember(monthDates, today) { monthDates.map { previewDay(it, today) } }
    val openDay: (LocalDate) -> Unit = { onDate(it); onMode(JournalCalendarMode.DAY) }
    val shiftDate: (Long) -> LocalDate = { direction ->
        when (mode) {
            JournalCalendarMode.DAY -> selectedDate.plusDays(direction)
            JournalCalendarMode.WEEK -> selectedDate.plusWeeks(direction)
            JournalCalendarMode.MONTH -> selectedDate.plusMonths(direction)
        }
    }
    BoxWithConstraints(modifier.fillMaxSize().background(Key), contentAlignment = Alignment.TopCenter) {
        val compact = maxHeight < 640.dp
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxSize().padding(horizontal = 20.dp, vertical = if (compact) 8.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 10.dp),
        ) {
            JournalHeader("수면 캘린더", "하루하루 쌓이는 나의 수면 기록", compact)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color(0xFF101F32)).padding(3.dp).selectableGroup()) {
                    JournalCalendarMode.entries.forEach { item ->
                        Box(
                            Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(11.dp))
                                .background(if (item == mode) Color(0xFF263F56) else Color.Transparent)
                                .selectable(item == mode, role = Role.Tab, onClick = { onMode(item) }),
                            contentAlignment = Alignment.Center,
                        ) { JournalLabel(item.title, color = if (item == mode) SkyBlue else JournalMuted) }
                    }
                }
                TextButton(onClick = { onDate(today) }, modifier = Modifier.height(38.dp)) { JournalLabel("오늘", color = SkyBlue) }
            }
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onDate(shiftDate(-1)) }) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, when (mode) { JournalCalendarMode.DAY -> "이전 날"; JournalCalendarMode.WEEK -> "이전 주"; JournalCalendarMode.MONTH -> "이전 달" }, tint = JournalMuted)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (mode == JournalCalendarMode.DAY) "${month.year}년 ${month.monthValue}월 ${selectedDate.dayOfMonth}일" else "${month.year}년 ${month.monthValue}월", color = White, style = MaterialTheme.typography.bodyMedium)
                    if (mode == JournalCalendarMode.WEEK) JournalLabel("${week.first().monthValue}.${week.first().dayOfMonth} – ${week.last().monthValue}.${week.last().dayOfMonth}", size = 10)
                }
                IconButton(onClick = { onDate(shiftDate(1)) }) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, when (mode) { JournalCalendarMode.DAY -> "다음 날"; JournalCalendarMode.WEEK -> "다음 주"; JournalCalendarMode.MONTH -> "다음 달" }, tint = JournalMuted)
                }
            }
            if (mode != JournalCalendarMode.DAY) Row(Modifier.fillMaxWidth()) {
                JournalWeekdays.forEachIndexed { index, label ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        JournalLabel(label, size = 11, color = weekdayColor(index))
                    }
                }
            }
            if (mode == JournalCalendarMode.DAY) {
                JournalDailyContent(today, selectedDate, compact, Modifier.fillMaxWidth().weight(1f))
            } else if (mode == JournalCalendarMode.WEEK) {
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        CalendarDay(
                            date, today, selectedDate, true, openDay,
                            Modifier.weight(1f).height(if (compact) 64.dp else 80.dp),
                            expanded = true,
                        )
                    }
                }
                JournalPeriodChart(week, days, selectedDate, metric, onMetric, Modifier.weight(1f))
            } else {
                // 달력과 연속 추이를 분리해 6주짜리 달도 작은 화면에서 점수와 선이 겹치지 않는다.
                Column(Modifier.weight(0.46f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    weeks.forEach { dates ->
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            dates.forEach { date ->
                                CalendarDay(date, today, selectedDate, YearMonth.from(date) == month, openDay, Modifier.weight(1f).fillMaxSize())
                            }
                        }
                    }
                }
                JournalPeriodChart(monthDates, monthDays, selectedDate, metric, onMetric, Modifier.weight(0.54f))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                JournalLabel(if (mode == JournalCalendarMode.DAY && previewDay(selectedDate, today) == null) "기록 없음 · 예시 데이터" else "예시 데이터", Modifier.weight(1f), size = 10)
                TextButton(onClick = onMain, modifier = Modifier.height(36.dp)) {
                    Icon(Icons.Outlined.KeyboardArrowUp, null, Modifier.size(18.dp), tint = SkyBlue)
                    JournalLabel("오늘의 리포트", color = SkyBlue, size = 11)
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(
    date: LocalDate,
    today: LocalDate,
    selectedDate: LocalDate,
    inMonth: Boolean,
    onDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
) {
    val selected = selectedDate == date
    Box(
        modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = { onDate(date) })
            .semantics { contentDescription = "${date.monthValue}월 ${date.dayOfMonth}일 ${JournalWeekdays[date.dayOfWeek.value % 7]}요일${if (date == today) ", 오늘" else ""}${if (selected) ", 선택됨" else ""}" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(if (expanded) 36.dp else 26.dp).background(if (selected) SkyBlue.copy(alpha = 0.18f) else Color.Transparent, CircleShape)
                .border(if (date == today) 1.dp else 0.dp, if (date == today) SkyBlue.copy(alpha = 0.65f) else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            JournalLabel("${date.dayOfMonth}", size = if (expanded) 14 else 11, color = when {
                !inMonth -> JournalMuted.copy(alpha = 0.35f)
                selected -> SkyBlue
                else -> weekdayColor(date.dayOfWeek.value % 7)
            })
        }
    }
}

private fun weekdayColor(index: Int): Color = when (index) {
    0 -> JournalLow
    6 -> Color(0xFF8CAED1)
    else -> JournalMuted
}

@Preview(name = "주간 · 작은 화면", widthDp = 320, heightDp = 480)
@Composable
private fun WeeklyPreview() {
    Sleeptandard_MVP_DemoTheme {
        JournalCalendarContent(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 16), JournalCalendarMode.WEEK, JournalChartMetric.SCORE, {}, {}, {}, {})
    }
}

@Preview(name = "월간 · 6주 작은 화면", widthDp = 320, heightDp = 480)
@Composable
private fun MonthlyPreview() {
    Sleeptandard_MVP_DemoTheme {
        JournalCalendarContent(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 8, 16), JournalCalendarMode.MONTH, JournalChartMetric.SCORE, {}, {}, {}, {})
    }
}
