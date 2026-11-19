package com.leejang.sleeptandard.Screen

import androidx.activity.compose.BackHandler
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
import com.leejang.sleeptandard.Component.journal.JournalChartMetric
import com.leejang.sleeptandard.Component.journal.JournalMainContent
import java.time.LocalDate
import kotlinx.coroutines.launch

/** 네비바 안에서 메인과 통합 캘린더를 전환하고 날짜/탭/지표 상태를 보관한다. */
@Composable
fun JournalScreen() {
    val today = LocalDate.now()
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    // AppNav의 SaveableStateProvider가 다른 네비 메뉴를 다녀온 뒤에도 복원한다.
    var selectedEpochDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var mode by rememberSaveable { mutableStateOf(JournalCalendarMode.WEEK) }
    var metric by rememberSaveable { mutableStateOf(JournalChartMetric.SCORE) }
    val goMain: () -> Unit = { scope.launch { pager.animateScrollToPage(0, animationSpec = tween(380)) } }
    BackHandler(enabled = pager.currentPage == 1, onBack = goMain)
    VerticalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
        when (page) {
            0 -> JournalMainContent(
                today = today,
                onCalendar = { scope.launch { pager.animateScrollToPage(1, animationSpec = tween(380)) } },
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
            )
        }
    }
}
