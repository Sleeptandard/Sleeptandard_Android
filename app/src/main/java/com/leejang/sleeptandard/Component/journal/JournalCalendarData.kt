package com.leejang.sleeptandard.Component.journal

import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.roundToInt

/** 예시 데이터 전용. 실제 SSE 분석이나 건강 지표의 평가 기준이 아니다. */
data class JournalPreviewData(
    val wakeScore: Int = 28,
    val remScore: Int = 30,
    val n3Score: Int = 30,
    val durationScore: Int = 80,
    val sleepMinutes: Int = 432,
    val bedtime: String = "23:48",
    val wakeTime: String = "07:00",
    val stageWeight: Float = 0.5f,
    val durationWeight: Float = 0.5f,
) {
    val stageScore: Int get() = wakeScore + remScore + n3Score
    val overallScore: Int get() = (stageScore * stageWeight + durationScore * durationWeight).roundToInt()
}

internal enum class JournalMood { LOW, NEUTRAL, HAPPY }

internal fun journalMood(score: Int): JournalMood = when {
    score <= 50 -> JournalMood.LOW
    score <= 75 -> JournalMood.NEUTRAL
    else -> JournalMood.HAPPY
}

internal fun weekDates(date: LocalDate): List<LocalDate> {
    val sunday = date.minusDays((date.dayOfWeek.value % 7).toLong())
    return List(7) { sunday.plusDays(it.toLong()) }
}

/** 일요일 시작, 마지막 토요일까지 포함하는 4~6주 달력. */
internal fun monthWeeks(month: YearMonth): List<List<LocalDate>> {
    val start = weekDates(month.atDay(1)).first()
    val cellCount = (month.atDay(1).dayOfWeek.value % 7 + month.lengthOfMonth() + 6) / 7 * 7
    return List(cellCount) { start.plusDays(it.toLong()) }.chunked(7)
}

internal data class JournalDayPreview(
    val report: JournalPreviewData,
    val efficiency: Int,
    val latencyMinutes: Int,
    val heartRate: Int,
    val respirationRate: Int,
    // 깊은 수면(N3), 얕은 수면(N1/N2), REM, Wake 순서. 항상 합계 100%.
    val stagePercents: List<Int>,
)

// 전체 통계의 범위를 정의하기 위한 최근 2년의 예시 기록.
internal fun previewHistoryStart(today: LocalDate): LocalDate = today.minusYears(2).plusDays(1)

internal fun previewDay(date: LocalDate, today: LocalDate): JournalDayPreview? {
    if (date.isAfter(today) || date.isBefore(previewHistoryStart(today))) return null
    val seed = Math.floorMod(date.toEpochDay(), 31)
    val deep = 18 + seed % 9
    val rem = 20 + seed % 6
    val wake = 5 + seed % 5
    val report = if (date == today) JournalPreviewData() else JournalPreviewData(
        wakeScore = 13 + seed % 17,
        remScore = 15 + seed * 3 % 16,
        n3Score = 14 + seed * 7 % 17,
        durationScore = 38 + seed * 11 % 60,
        sleepMinutes = 300 + seed * 13 % 220,
    )
    return JournalDayPreview(
        report = report,
        efficiency = 78 + seed % 20,
        latencyMinutes = 8 + seed * 3 % 38,
        heartRate = 52 + seed * 7 % 20,
        respirationRate = 12 + seed % 7,
        stagePercents = if (date == today) listOf(23, 49, 23, 5) else listOf(deep, 100 - deep - rem - wake, rem, wake),
    )
}

internal enum class JournalMetric(val title: String, val unit: String, val min: Float, val max: Float) {
    SCORE("종합 점수", "점", 0f, 100f),
    DURATION("수면 시간", "시간", 0f, 10f),
    EFFICIENCY("수면 효율", "%", 0f, 100f),
    LATENCY("수면 진입 시간", "분", 0f, 60f),
    HR("HR · 심박수", "bpm", 40f, 100f),
    RR("RR · 호흡수", "회/분", 8f, 24f);

    fun value(day: JournalDayPreview): Float = when (this) {
        SCORE -> day.report.overallScore.toFloat()
        DURATION -> day.report.sleepMinutes / 60f
        EFFICIENCY -> day.efficiency.toFloat()
        LATENCY -> day.latencyMinutes.toFloat()
        HR -> day.heartRate.toFloat()
        RR -> day.respirationRate.toFloat()
    }

    fun format(value: Float): String = if (this == DURATION) String.format(Locale.KOREAN, "%.1f", value) else value.roundToInt().toString()
}

/** 주간/월간에서 공유하는 선택 항목. 비율 분포는 숫자 하나인 지표와 구분한다. */
internal enum class JournalChartMetric(val numeric: JournalMetric?) {
    SCORE(JournalMetric.SCORE), DURATION(JournalMetric.DURATION),
    EFFICIENCY(JournalMetric.EFFICIENCY), LATENCY(JournalMetric.LATENCY),
    HR(JournalMetric.HR), RR(JournalMetric.RR), STAGES(null);

    val title: String get() = numeric?.title ?: "수면 단계 비율"
}
