package com.leejang.sleeptandard.Component.journal

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalStatisticsDataTest {
    private val today = LocalDate.of(2026, 11, 16)

    @Test
    fun periodsFollowCalendarBoundariesIncludingLeapYears() {
        val leap = LocalDate.of(2024, 2, 29)
        assertEquals(JournalStatisticsRange(LocalDate.of(2024, 2, 1), leap), statisticsRange(JournalStatisticsPeriod.MONTH, leap, today))
        assertEquals(JournalStatisticsRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)), statisticsRange(JournalStatisticsPeriod.YEAR, leap, today))
        assertEquals(JournalStatisticsRange(LocalDate.of(2026, 12, 27), LocalDate.of(2027, 1, 2)), statisticsRange(JournalStatisticsPeriod.WEEK, LocalDate.of(2027, 1, 1), today))
        assertEquals(LocalDate.of(2026, 2, 28), JournalStatisticsPeriod.MONTH.shift(LocalDate.of(2026, 1, 31), 1))
    }

    @Test
    fun dayMatchesCalendarAndOnlyDayOmitsAverageLabels() {
        val result = previewStatistics(statisticsRange(JournalStatisticsPeriod.DAY, today, today), today)
        val day = previewDay(today, today)!!
        assertEquals(1, result.recordCount)
        assertEquals(day.report.sleepMinutes, result.sleepMinutes)
        assertEquals(day.report.overallScore, result.score)
        assertEquals(day.stagePercents, result.stagePercents)
        assertEquals("수면 시간", JournalStatisticsPeriod.DAY.label("수면 시간"))
        JournalStatisticsPeriod.entries.filter { it != JournalStatisticsPeriod.DAY }.forEach {
            assertEquals("평균 수면 시간", it.label("수면 시간"))
        }
    }

    @Test
    fun aggregateUsesRecordedDaysAndFutureDatesAreEmpty() {
        val week = statisticsRange(JournalStatisticsPeriod.WEEK, today, today)
        assertEquals(2, previewStatistics(week, today).recordCount)
        val month = previewStatistics(statisticsRange(JournalStatisticsPeriod.MONTH, today, today), today)
        assertEquals(16, month.recordCount)
        assertEquals(100, month.stagePercents!!.sum())
        assertTrue(month.stagePercents.all { it >= 0 })
        val expected = (1..16).map { previewDay(today.withDayOfMonth(it), today)!!.heartRate }.average()
        assertEquals(expected, month.heartRate!!.toDouble(), 0.5)
        val future = previewStatistics(statisticsRange(JournalStatisticsPeriod.DAY, today.plusDays(1), today), today)
        assertEquals(0, future.recordCount)
        assertNull(future.score)
        assertNull(future.stagePercents)
    }

    @Test
    fun allUsesFullDemoHistoryAndDoesNotDependOnSelectedDate() {
        val range = statisticsRange(JournalStatisticsPeriod.ALL, today.minusMonths(4), today)
        assertEquals(previewHistoryStart(today), range.start)
        assertEquals(today, range.end)
        assertEquals(730, previewStatistics(range, today).recordCount)
        assertNull(previewDay(range.start.minusDays(1), today))
        assertEquals(today, JournalStatisticsPeriod.ALL.shift(today, 1))
    }
}
