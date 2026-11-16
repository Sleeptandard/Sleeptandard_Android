package com.leejang.sleeptandard.Component.journal

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.floor
import kotlin.math.roundToInt

internal enum class JournalStatisticsPeriod(val title: String) {
    DAY("하루"), WEEK("일주일"), MONTH("한 달"), YEAR("일 년"), ALL("전체");

    fun label(metric: String): String = if (this == DAY) metric else "평균 $metric"

    fun shift(date: LocalDate, direction: Long): LocalDate = when (this) {
        DAY -> date.plusDays(direction)
        WEEK -> date.plusWeeks(direction)
        MONTH -> date.plusMonths(direction)
        YEAR -> date.plusYears(direction)
        ALL -> date
    }
}

internal data class JournalStatisticsRange(val start: LocalDate, val end: LocalDate)

internal fun statisticsRange(period: JournalStatisticsPeriod, date: LocalDate, today: LocalDate): JournalStatisticsRange = when (period) {
    JournalStatisticsPeriod.DAY -> JournalStatisticsRange(date, date)
    JournalStatisticsPeriod.WEEK -> weekDates(date).let { JournalStatisticsRange(it.first(), it.last()) }
    JournalStatisticsPeriod.MONTH -> YearMonth.from(date).let { JournalStatisticsRange(it.atDay(1), it.atEndOfMonth()) }
    JournalStatisticsPeriod.YEAR -> JournalStatisticsRange(date.withDayOfYear(1), LocalDate.of(date.year, 12, 31))
    JournalStatisticsPeriod.ALL -> JournalStatisticsRange(previewHistoryStart(today), today)
}

internal data class JournalStatisticsSummary(
    val recordCount: Int,
    val sleepMinutes: Int? = null,
    val stagePercents: List<Int>? = null,
    val heartRate: Int? = null,
    val respirationRate: Int? = null,
    val latencyMinutes: Int? = null,
    val score: Int? = null,
)

/** 캘린더와 같은 예시 기록만 사용한다. 실제 SSE/서버 통계와는 연결하지 않는다. */
internal fun previewStatistics(range: JournalStatisticsRange, today: LocalDate): JournalStatisticsSummary {
    val start = maxOf(range.start, previewHistoryStart(today))
    val end = minOf(range.end, today)
    if (start > end) return JournalStatisticsSummary(0)
    val days = (0..ChronoUnit.DAYS.between(start, end)).mapNotNull { previewDay(start.plusDays(it), today) }
    if (days.isEmpty()) return JournalStatisticsSummary(0)
    // 날짜별 비율의 평균. 표시 시 반올림 오차로 합계가 99/101이 되지 않게 배분한다.
    val averages = List(4) { index -> days.map { it.stagePercents[index] }.average() }
    val percents = averages.map { floor(it).toInt() }.toMutableList()
    averages.indices.sortedByDescending { averages[it] - percents[it] }
        .take(100 - percents.sum()).forEach { percents[it]++ }
    return JournalStatisticsSummary(
        recordCount = days.size,
        sleepMinutes = days.map { it.report.sleepMinutes }.average().roundToInt(),
        stagePercents = percents,
        heartRate = days.map { it.heartRate }.average().roundToInt(),
        respirationRate = days.map { it.respirationRate }.average().roundToInt(),
        latencyMinutes = days.map { it.latencyMinutes }.average().roundToInt(),
        score = days.map { it.report.overallScore }.average().roundToInt(),
    )
}
