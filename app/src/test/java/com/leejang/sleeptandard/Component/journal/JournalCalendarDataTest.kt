package com.leejang.sleeptandard.Component.journal

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalCalendarDataTest {
    @Test
    fun weeksStartOnSundayAndCrossYearBoundaries() {
        val dates = weekDates(LocalDate.of(2027, 1, 1))
        assertEquals(LocalDate.of(2026, 12, 27), dates.first())
        assertEquals(LocalDate.of(2027, 1, 2), dates.last())
        assertEquals(DayOfWeek.SUNDAY, dates.first().dayOfWeek)
        assertEquals(dates, weekDates(dates.first()))
    }

    @Test
    fun monthGridCoversEveryDateExactlyOnceIncludingLeapDay() {
        for (year in 2024..2028) {
            for (monthNumber in 1..12) {
                val month = YearMonth.of(year, monthNumber)
                val weeks = monthWeeks(month)
                val cells = weeks.flatten()
                assertTrue(weeks.size in 4..6)
                assertTrue(weeks.all { it.size == 7 && it.first().dayOfWeek == DayOfWeek.SUNDAY && it.last().dayOfWeek == DayOfWeek.SATURDAY })
                assertEquals((1..month.lengthOfMonth()).map(month::atDay), cells.filter { YearMonth.from(it) == month })
                assertEquals(cells.size, cells.distinct().size)
                assertTrue(cells.zipWithNext().all { (a, b) -> a.plusDays(1) == b })
            }
        }
        assertEquals(4, monthWeeks(YearMonth.of(2026, 2)).size)
        assertEquals(6, monthWeeks(YearMonth.of(2026, 8)).size)
    }

    @Test
    fun moodBoundariesMatchTheProvidedSpecification() {
        listOf(0, 50).forEach { assertEquals(JournalMood.LOW, journalMood(it)) }
        listOf(51, 75).forEach { assertEquals(JournalMood.NEUTRAL, journalMood(it)) }
        listOf(76, 100).forEach { assertEquals(JournalMood.HAPPY, journalMood(it)) }
    }

    @Test
    fun scoresUseComponentSumAndConfigurableWeights() {
        val data = JournalPreviewData()
        assertEquals(88, data.stageScore)
        assertEquals(84, data.overallScore)
        assertEquals(86, data.copy(stageWeight = 0.75f, durationWeight = 0.25f).overallScore)
    }

    @Test
    fun mockDataIsStableHasValidRatiosAndDoesNotInventFutureRecords() {
        val today = LocalDate.of(2026, 11, 16)
        assertNull(previewDay(today.plusDays(1), today))
        assertEquals(JournalPreviewData(), previewDay(today, today)!!.report)
        repeat(366) { offset ->
            val date = today.minusDays(offset.toLong())
            val day = previewDay(date, today)!!
            assertEquals(day, previewDay(date, today))
            assertEquals(100, day.stagePercents.sum())
            assertTrue(day.stagePercents.all { it in 0..100 })
            JournalMetric.entries.forEach { metric -> assertTrue(metric.value(day) in metric.min..metric.max) }
        }
    }
}
