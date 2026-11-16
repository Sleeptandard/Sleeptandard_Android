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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
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
    fun mainFitsSmallViewportWithoutScrolling() {
        compose.setContent {
            Sleeptandard_MVP_DemoTheme {
                Box(Modifier.requiredSize(320.dp, 480.dp).testTag("viewport")) {
                    JournalMainContent(today = LocalDate.of(2026, 11, 16))
                }
            }
        }
        listOf("오늘의 수면 평가", "수면 단계 비율", "총 수면 시간", "기상 07:00", "올려서 캘린더 보기").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        savePreview("main-small")
    }

    @Test
    fun sixWeekMonthFitsSmallViewport() {
        compose.setContent {
            Sleeptandard_MVP_DemoTheme {
                Box(Modifier.requiredSize(320.dp, 480.dp).testTag("viewport")) {
                    JournalCalendarContent(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 8, 16), JournalCalendarMode.MONTH, JournalMetric.SCORE, {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithText("2026년 8월").assertIsDisplayed()
        compose.onNodeWithContentDescription("8월 1일 토요일").assertIsDisplayed()
        compose.onNodeWithContentDescription("8월 31일 월요일").assertIsDisplayed()
        compose.onNodeWithText("오늘의 리포트").assertIsDisplayed()
        savePreview("month-six-weeks-small")
    }

    @Test
    fun weeklyChartsFitSmallViewport() {
        compose.setContent {
            Sleeptandard_MVP_DemoTheme {
                Box(Modifier.requiredSize(320.dp, 480.dp).testTag("viewport")) {
                    JournalCalendarContent(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 9), JournalCalendarMode.WEEK, JournalMetric.SCORE, {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithText("종합 점수").assertIsDisplayed()
        compose.onNodeWithText("수면 단계 비율").assertIsDisplayed()
        compose.onNodeWithText("오늘의 리포트").assertIsDisplayed()
        savePreview("week-small")
    }

    @Test
    fun pagerGesturesMetricSelectionAndSavedCalendarStateWork() {
        val restore = StateRestorationTester(compose)
        restore.setContent {
            Sleeptandard_MVP_DemoTheme {
                Box(Modifier.requiredSize(360.dp, 580.dp).testTag("viewport")) { JournalScreen() }
            }
        }
        compose.onNodeWithTag("viewport").performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.onNodeWithText("수면 캘린더").assertIsDisplayed()
        compose.onNodeWithText("종합 점수").performClick()
        compose.onNodeWithText("수면 효율").performClick()
        compose.onNodeWithText("수면 효율").assertIsDisplayed()
        compose.onNodeWithText("월간").performClick()
        compose.onNodeWithContentDescription("이전 달").performClick()
        val month = LocalDate.now().minusMonths(1)
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("수면 캘린더").assertIsDisplayed()
        compose.onNodeWithText("월간").assertIsSelected()
        compose.onNodeWithText("${month.year}년 ${month.monthValue}월").assertIsDisplayed()
        compose.onNodeWithTag("viewport").performTouchInput { swipeDown() }
        compose.waitForIdle()
        compose.onNodeWithText("수면 리포트").assertIsDisplayed()
        compose.onNodeWithText("올려서 캘린더 보기").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("${month.year}년 ${month.monthValue}월").assertIsDisplayed()
        compose.onNodeWithText("주간").performClick()
        compose.onNodeWithText("수면 효율").assertIsDisplayed()
    }

    private fun savePreview(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.getExternalFilesDir(null), "journal-review").apply { mkdirs() }
        val bitmap = compose.onNodeWithTag("viewport").captureToImage().asAndroidBitmap()
        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun statisticsPeriodsFitSmallViewportAndDayOmitsAverage() {
        compose.setContent {
            var period by remember { mutableStateOf(JournalStatisticsPeriod.DAY) }
            Sleeptandard_MVP_DemoTheme {
                Box(Modifier.requiredSize(320.dp, 480.dp).testTag("viewport")) {
                    JournalStatisticsContent(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 16), period, {}, { period = it }, {})
                }
            }
        }
        val metrics = listOf("수면 시간", "수면 비율", "HR", "RR", "입면 시간", "수면 점수")
        metrics.forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onAllNodesWithText("평균", substring = true).assertCountEquals(0)
        compose.onNodeWithText("수면 리듬").performClick()
        compose.onNodeWithText("수면 통계").assertIsDisplayed()
        savePreview("statistics-day-small")
        listOf("일주일", "한 달", "일 년", "전체").forEach { period ->
            compose.onNodeWithText(period).performClick()
            compose.onNodeWithText(period).assertIsSelected()
            metrics.forEach { compose.onNodeWithText("평균 $it").assertIsDisplayed() }
        }
        savePreview("statistics-all-small")
    }

    @Test
    fun statisticsEntryDateSelectionBackAndRestorationWork() {
        val restore = StateRestorationTester(compose)
        restore.setContent {
            Sleeptandard_MVP_DemoTheme {
                Box(Modifier.requiredSize(360.dp, 580.dp).testTag("viewport")) { JournalScreen() }
            }
        }
        compose.onNodeWithContentDescription("수면 리포트 통계 보기").performClick()
        compose.onNodeWithText("하루").assertIsSelected()
        compose.onNodeWithContentDescription("통계 닫기").performClick()
        compose.onNodeWithText("수면 리포트").assertIsDisplayed()
        compose.onNodeWithText("올려서 캘린더 보기").performClick()
        compose.onNodeWithContentDescription("수면 캘린더 통계 보기").performClick()
        compose.onNodeWithText("일주일").assertIsSelected()
        compose.onNodeWithText("일 년").performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("수면 통계").assertIsDisplayed()
        compose.onNodeWithText("일 년").assertIsSelected()
        compose.onNodeWithContentDescription("통계 닫기").performClick()
        compose.onNodeWithText("수면 캘린더").assertIsDisplayed()

        val today = LocalDate.now()
        val date = weekDates(today).first()
        compose.onNodeWithContentDescription("${date.monthValue}월 ${date.dayOfMonth}일 일요일", substring = true).performClick()
        compose.onNodeWithText("하루").assertIsSelected()
        compose.onNodeWithText(statisticsDateTitle(JournalStatisticsPeriod.DAY, date)).assertIsDisplayed()
        compose.onNodeWithContentDescription("통계 닫기").performClick()
        compose.onNodeWithText("월간").performClick()
        compose.onNodeWithContentDescription("수면 캘린더 통계 보기").performClick()
        compose.onNodeWithText("한 달").assertIsSelected()
        compose.onNodeWithContentDescription("통계 닫기").performClick()
        val first = date.withDayOfMonth(1)
        compose.onNodeWithContentDescription("${first.monthValue}월 1일 ${JournalWeekdays[first.dayOfWeek.value % 7]}요일", substring = true).performClick()
        compose.onNodeWithText("하루").assertIsSelected()
        compose.onNodeWithText(statisticsDateTitle(JournalStatisticsPeriod.DAY, first)).assertIsDisplayed()
    }
}
