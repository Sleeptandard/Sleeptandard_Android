package com.leejang.sleeptandard.Component.journal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leejang.sleeptandard.ui.theme.Key
import com.leejang.sleeptandard.ui.theme.SkyBlue
import com.leejang.sleeptandard.ui.theme.Sleeptandard_MVP_DemoTheme
import com.leejang.sleeptandard.ui.theme.White
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 내부 스크롤 없이 부모가 제공한 화면 높이를 세 섹션이 나눠 사용한다. */
@Composable
fun JournalMainContent(
    modifier: Modifier = Modifier,
    data: JournalPreviewData = JournalPreviewData(),
    today: LocalDate = LocalDate.now(),
    onCalendar: () -> Unit = {},
    onStatistics: () -> Unit = {},
) {
    BoxWithConstraints(modifier.fillMaxSize().background(Key), contentAlignment = Alignment.TopCenter) {
        val compact = maxHeight < 640.dp
        val gap = if (compact) 8.dp else 12.dp
        val cardPadding = if (compact) 10.dp else 18.dp
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxSize().padding(horizontal = 20.dp, vertical = if (compact) 8.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            JournalHeader("수면 리포트", today.format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)), compact, onStatistics)
            JournalPanel(Modifier.weight(0.36f), hero = true, padding = cardPadding) {
                JournalLabel("오늘의 수면 평가", color = SkyBlue)
                Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                        Text("${data.overallScore}", color = White, style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = if (compact) 44.sp else 60.sp, lineHeight = if (compact) 48.sp else 64.sp, fontWeight = FontWeight.Medium,
                        ))
                        JournalLabel("점", Modifier.padding(start = 5.dp, bottom = 7.dp))
                    }
                    MoonFace(data.overallScore, Modifier.fillMaxHeight(0.94f).widthIn(max = 104.dp).aspectRatio(1f, matchHeightConstraintsFirst = true))
                }
                Text(when (journalMood(data.overallScore)) {
                    JournalMood.HAPPY -> "편안하게 잘 잤어요"
                    JournalMood.NEUTRAL -> "조금 더 쉬어가도 좋아요"
                    JournalMood.LOW -> "오늘은 푹 쉬어주세요"
                }, style = MaterialTheme.typography.bodyMedium.copy(fontSize = if (compact) 14.sp else 16.sp), color = White)
                JournalLabel("수면 단계와 수면 시간을 함께 살펴봤어요", size = 10)
            }
            JournalPanel(Modifier.weight(0.34f), padding = cardPadding) {
                JournalScoreHeading("수면 단계 비율", data.stageScore, JournalLavender)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    StageDonut(Modifier.weight(0.44f).fillMaxHeight())
                    Column(Modifier.weight(0.56f), verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
                        listOf(23, 49, 23, 5).forEachIndexed { index, percent ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(6.dp).background(JournalStageColors[index], CircleShape))
                                Spacer(Modifier.width(6.dp))
                                JournalLabel(JournalStageNames[index], Modifier.weight(1f), size = 11)
                                JournalLabel("$percent%", size = 12, color = White)
                            }
                        }
                    }
                }
            }
            JournalPanel(Modifier.weight(0.30f), padding = cardPadding) {
                JournalScoreHeading("총 수면 시간", data.durationScore)
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text("${data.sleepMinutes / 60}", color = White, style = MaterialTheme.typography.titleLarge.copy(fontSize = if (compact) 28.sp else 34.sp))
                    JournalLabel(" 시간  ", size = 12)
                    Text("${data.sleepMinutes % 60}", color = White, style = MaterialTheme.typography.titleLarge.copy(fontSize = if (compact) 28.sp else 34.sp))
                    JournalLabel(" 분", size = 12)
                    Spacer(Modifier.weight(1f))
                    JournalLabel("목표 8시간", size = 10)
                }
                Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color(0xFF20334A))) {
                    Box(Modifier.fillMaxWidth((data.sleepMinutes / 480f).coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(Color(0xFF6387B0), SkyBlue))))
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    JournalLabel("취침 ${data.bedtime}", size = 11)
                    JournalLabel("기상 ${data.wakeTime}", size = 11)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                JournalLabel("예시 데이터", Modifier.weight(1f), size = 10)
                TextButton(onClick = onCalendar, modifier = Modifier.height(40.dp)) {
                    JournalLabel("올려서 캘린더 보기", color = SkyBlue, size = 11)
                    Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.size(18.dp), tint = SkyBlue)
                }
            }
        }
    }
}

@Composable
private fun StageDonut(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = "깊은 수면 23%, 얕은 수면 49%, REM 23%, 각성 5%" }) {
            val diameter = size.minDimension.coerceAtMost(120.dp.toPx())
            val stroke = (diameter * 0.12f).coerceAtMost(13.dp.toPx())
            val origin = Offset((size.width - diameter + stroke) / 2, (size.height - diameter + stroke) / 2)
            var angle = -90f
            listOf(23, 49, 23, 5).forEachIndexed { index, percent ->
                val sweep = percent * 3.6f
                drawArc(JournalStageColors[index], angle + 2, sweep - 4, false, origin, Size(diameter - stroke, diameter - stroke), style = Stroke(stroke))
                angle += sweep
            }
        }
        JournalLabel("균형", size = 12, color = White)
    }
}

/** 0~50: 위를 보는 눈, 51~75: 무표정, 76~100: 미소. */
@Composable
private fun MoonFace(score: Int, modifier: Modifier = Modifier) {
    val mood = journalMood(score)
    Canvas(modifier.semantics { contentDescription = when (mood) {
        JournalMood.LOW -> "위를 보는 달"
        JournalMood.NEUTRAL -> "무표정한 달"
        JournalMood.HAPPY -> "웃는 달"
    } }) {
        val unit = size.minDimension
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(Brush.radialGradient(listOf(SkyBlue.copy(alpha = 0.12f), Color.Transparent), center = center), unit / 2, center)
        drawCircle(Brush.linearGradient(listOf(Color(0xFFDBEBDA), Color(0xFF8CAFB9))), unit * 0.36f, center)
        val ink = Color(0xFF254357)
        // 원점도 중앙에 맞춰, 높이가 달라져도 표정이 달 밖으로 밀리지 않는다.
        val origin = center - Offset(unit / 2, unit / 2)
        for (x in listOf(0.4f, 0.6f)) {
            when (mood) {
                JournalMood.HAPPY -> drawArc(ink, 195f, 150f, false, origin + Offset(unit * (x - 0.045f), unit * 0.43f), Size(unit * 0.09f, unit * 0.07f), style = Stroke(unit * 0.025f, cap = StrokeCap.Round))
                JournalMood.NEUTRAL -> drawCircle(ink, unit * 0.018f, origin + Offset(unit * x, unit * 0.46f))
                JournalMood.LOW -> {
                    drawCircle(White, unit * 0.048f, origin + Offset(unit * x, unit * 0.46f))
                    drawCircle(ink, unit * 0.022f, origin + Offset(unit * x, unit * 0.435f))
                }
            }
        }
        val mouth = Path().apply {
            moveTo(origin.x + unit * 0.42f, origin.y + unit * 0.59f)
            quadraticTo(origin.x + unit * 0.5f, origin.y + unit * if (mood == JournalMood.HAPPY) 0.69f else 0.59f,
                origin.x + unit * 0.58f, origin.y + unit * 0.59f)
        }
        drawPath(mouth, ink, style = Stroke(unit * 0.025f, cap = StrokeCap.Round))
        listOf(0.33f, 0.67f).forEach { x -> drawCircle(JournalWarm.copy(alpha = 0.45f), unit * 0.036f, origin + Offset(unit * x, unit * 0.55f)) }
    }
}

@Preview(name = "일반 화면 · 네비바 제외 영역", widthDp = 390, heightDp = 680)
@Preview(name = "작은 화면 · 네비바 제외 영역", widthDp = 320, heightDp = 480)
@Composable
private fun JournalMainPreview() {
    Sleeptandard_MVP_DemoTheme { JournalMainContent() }
}
