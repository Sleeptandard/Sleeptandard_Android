package com.leejang.sleeptandard.Screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.leejang.sleeptandard.Component.journal.JournalCalendarContent
import com.leejang.sleeptandard.Component.journal.JournalCalendarMode
import com.leejang.sleeptandard.Component.journal.JournalMainContent
import com.leejang.sleeptandard.Component.journal.JournalMetric
import com.leejang.sleeptandard.Component.journal.JournalStatisticsContent
import com.leejang.sleeptandard.Component.journal.JournalStatisticsPeriod
import java.time.LocalDate
import kotlinx.coroutines.launch

/** 공통 네비바 안에서 메인·캘린더·통계를 전환하고 선택 상태를 보관한다. */
@Composable
fun JournalScreen() {
    val today = LocalDate.now()
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    // Pager의 화면이 composition에서 빠져도 날짜·메뉴 선택을 유지한다.
    // AppNav의 SaveableStateProvider가 네비바 메뉴 이동 후 이 상태를 복원한다.
    var selectedEpochDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var mode by rememberSaveable { mutableStateOf(JournalCalendarMode.WEEK) }
    var metric by rememberSaveable { mutableStateOf(JournalMetric.SCORE) }
    var showStatistics by rememberSaveable { mutableStateOf(false) }
    var statisticsEpochDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var statisticsPeriod by rememberSaveable { mutableStateOf(JournalStatisticsPeriod.DAY) }
    val openStatistics: (LocalDate, JournalStatisticsPeriod) -> Unit = { date, period ->
        statisticsEpochDay = date.toEpochDay()
        statisticsPeriod = period
        showStatistics = true
    }
    val goMain: () -> Unit = { scope.launch { pager.animateScrollToPage(0, animationSpec = tween(380)) } }
    BackHandler(enabled = showStatistics || pager.currentPage == 1) {
        if (showStatistics) showStatistics = false else goMain()
    }
    AnimatedContent(
        targetState = showStatistics,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val direction = if (targetState) 1 else -1
            (slideInHorizontally(tween(280)) { it * direction / 5 } + fadeIn(tween(220))) togetherWith
                (slideOutHorizontally(tween(280)) { -it * direction / 5 } + fadeOut(tween(160)))
        },
        label = "journalStatisticsTransition",
    ) { statisticsVisible ->
        if (statisticsVisible) {
            JournalStatisticsContent(
                today = today,
                date = LocalDate.ofEpochDay(statisticsEpochDay),
                period = statisticsPeriod,
                onDate = { statisticsEpochDay = it.toEpochDay() },
                onPeriod = { statisticsPeriod = it },
                onBack = { showStatistics = false },
            )
        } else {
            VerticalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                when (page) {
                    0 -> JournalMainContent(
                        today = today,
                        onCalendar = { scope.launch { pager.animateScrollToPage(1, animationSpec = tween(380)) } },
                        onStatistics = { openStatistics(today, JournalStatisticsPeriod.DAY) },
                    )
                    1 -> JournalCalendarContent(
                        today = today,
                        selectedDate = LocalDate.ofEpochDay(selectedEpochDay),
                        mode = mode,
                        metric = metric,
                        onDate = { selectedEpochDay = it.toEpochDay() },
                        onMode = { mode = it },
                        onMetric = { metric = it },
                        onMain = goMain,
                        onStatistics = {
                            openStatistics(
                                LocalDate.ofEpochDay(selectedEpochDay),
                                if (mode == JournalCalendarMode.WEEK) JournalStatisticsPeriod.WEEK else JournalStatisticsPeriod.MONTH,
                            )
                        },
                        onDayStatistics = {
                            selectedEpochDay = it.toEpochDay()
                            openStatistics(it, JournalStatisticsPeriod.DAY)
                        },
                    )
                }
            }
        }
    }
}
