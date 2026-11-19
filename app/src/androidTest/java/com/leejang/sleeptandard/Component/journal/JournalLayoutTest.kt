package com.leejang.sleeptandard.Component.journal

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.leejang.sleeptandard.Screen.JournalScreen
import com.leejang.sleeptandard.ui.theme.Sleeptandard_MVP_DemoTheme
import java.io.File
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

class JournalLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun mainFitsSmallViewportWithoutStatisticsButton() {
        compose.setContent {
            Sleeptandard_MVP_DemoTheme {
                Box(Modifier.requiredSize(320.dp, 480.dp).testTag("viewport")) {
                    JournalMainContent(today = LocalDate.of(2026, 11, 17))
                }
            }
        }
        listOf("오늘의 수면 평가", "수면 단계 비율", "총 수면 시간", "기상 07:00", "올려서 캘린더 보기").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onAllNodesWithText("통계").assertCountEquals(0)
        savePreview("main-small")
    }

    @Test
    fun dailyStatisticsFitInsideCalendarWithoutAverageLabels() {
        compose.setContent {
            Sleeptandard_MVP_DemoTheme {
                Box(Modifier.requiredSize(320.dp, 480.dp).testTag("viewport")) {
                    JournalCalendarContent(LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 17), JournalCalendarMode.DAY,
                        JournalChartMetric.SCORE, {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithText("일일").assertIsSelected()
        listOf("수면 시간", "수면 비율", "HR", "RR", "입면 시간", "수면 점수", "수면 리듬").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onAllNodesWithText("평균", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("통계").assertCountEquals(0)
        compose.onNodeWithText("수면 리듬").performClick()
        compose.onNodeWithText("일일").assertIsSelected()
        savePreview("daily-small")
    }

    @Test
    fun weeklyAndMonthlyShareAllMetricsInExactlyOneChart() {
        compose.setContent {
            var mode by remember { mutableStateOf(JournalCalendarMode.WEEK) }
            var metric by remember { mutableStateOf(JournalChartMetric.SCORE) }
            Sleeptandard_MVP_DemoTheme {
                Box(Modifier.requiredSize(320.dp, 480.dp).testTag("viewport")) {
                    JournalCalendarContent(LocalDate.of(2026, 11, 17), LocalDate.of(2026, 8, 16), mode,
                        metric, {}, { mode = it }, { metric = it }, {})
                }
            }
        }
        savePreview("week-small")
        for (mode in listOf(JournalCalendarMode.WEEK, JournalCalendarMode.MONTH)) {
            compose.onNodeWithText(mode.title).performClick()
            val choices = if (mode == JournalCalendarMode.WEEK) JournalChartMetric.entries.drop(1) else JournalChartMetric.entries
            for (metric in choices) {
                compose.onNodeWithContentDescription("그래프 항목 선택").performClick()
                compose.onNodeWithText(metric.title).performClick()
                compose.onNodeWithText(metric.title).assertIsDisplayed()
                compose.onAllNodesWithTag("journal-period-chart").assertCountEquals(1)
                compose.onNodeWithTag(if (metric == JournalChartMetric.STAGES) "journal-stage-plot" else "journal-line-plot").assertIsDisplayed()
                if (mode == JournalCalendarMode.MONTH && metric == JournalChartMetric.SCORE) savePreview("month-six-weeks-small")
            }
            savePreview(if (mode == JournalCalendarMode.WEEK) "week-stages-small" else "month-stages-small")
        }
        compose.onNodeWithContentDescription("8월 1일 토요일").assertIsDisplayed()
        compose.onNodeWithContentDescription("8월 31일 월요일").assertIsDisplayed()
        compose.onNodeWithText("오늘의 리포트").assertIsDisplayed()
    }

    @Test
    fun datesOpenDailyTabAndSelectionsSurviveRestorationAndPaging() {
        val restore = StateRestorationTester(compose)
        restore.setContent {
            Sleeptandard_MVP_DemoTheme {
                Box(Modifier.requiredSize(360.dp, 580.dp).testTag("viewport")) { JournalScreen() }
            }
        }
        compose.onNodeWithTag("viewport").performTouchInput { swipeUp() }
        compose.onNodeWithText("수면 캘린더").assertIsDisplayed()
        val date = weekDates(LocalDate.now()).first()
        compose.onNodeWithContentDescription("${date.monthValue}월 ${date.dayOfMonth}일 일요일", substring = true).performClick()
        compose.onNodeWithText("일일").assertIsSelected()
        compose.onNodeWithText("${date.year}년 ${date.monthValue}월 ${date.dayOfMonth}일").assertIsDisplayed()
        compose.onNodeWithText("월간").performClick()
        compose.onNodeWithContentDescription("그래프 항목 선택").performClick()
        compose.onNodeWithText("RR · 호흡수").performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("월간").assertIsSelected()
        compose.onNodeWithText("RR · 호흡수").assertIsDisplayed()
        val first = date.withDayOfMonth(1)
        compose.onNodeWithContentDescription("${first.monthValue}월 1일 ${JournalWeekdays[first.dayOfWeek.value % 7]}요일", substring = true).performClick()
        compose.onNodeWithText("일일").assertIsSelected()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("${first.year}년 ${first.monthValue}월 1일").assertIsDisplayed()
        compose.onNodeWithTag("viewport").performTouchInput { swipeDown() }
        compose.onNodeWithText("수면 리포트").assertIsDisplayed()
        compose.onNodeWithText("올려서 캘린더 보기").performClick()
        compose.onNodeWithText("일일").assertIsSelected()
        compose.onNodeWithText("주간").performClick()
        compose.onNodeWithText("RR · 호흡수").assertIsDisplayed()
    }

    private fun savePreview(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.getExternalFilesDir(null), "journal-review").apply { mkdirs() }
        val bitmap = compose.onNodeWithTag("viewport").captureToImage().asAndroidBitmap()
        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
