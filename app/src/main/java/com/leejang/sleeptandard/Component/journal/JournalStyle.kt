package com.leejang.sleeptandard.Component.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leejang.sleeptandard.ui.theme.SkyBlue
import com.leejang.sleeptandard.ui.theme.White

internal val JournalMuted = Color(0xFF9BAFC5)
internal val JournalLavender = Color(0xFFA6AAD8)
internal val JournalWarm = Color(0xFFDCC6A3)
internal val JournalLow = Color(0xFFD48B88)
internal val JournalStageColors = listOf(Color(0xFF688BB5), SkyBlue, JournalLavender, JournalWarm)
internal val JournalStageNames = listOf("깊은", "얕은", "REM", "각성")
internal val JournalWeekdays = listOf("일", "월", "화", "수", "목", "금", "토")

internal fun scoreColor(score: Float): Color = if (score <= 50) {
    JournalLow
} else if (score <= 75) {
    lerp(JournalLow, JournalWarm, (score - 50) / 25)
} else {
    lerp(SkyBlue, Color(0xFF8ACDBF), (score - 76).coerceIn(0f, 24f) / 24)
}

@Composable
internal fun JournalLabel(text: String, modifier: Modifier = Modifier, size: Int = 12, color: Color = JournalMuted) {
    Text(text, modifier, color = color, style = MaterialTheme.typography.bodySmall.copy(
        fontSize = size.sp, lineHeight = (size + 3).sp, fontWeight = FontWeight.Normal,
    ))
}

@Composable
internal fun JournalPanel(
    modifier: Modifier = Modifier,
    hero: Boolean = false,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(if (hero) listOf(Color(0xFF172E47), Color(0xFF0C192A)) else listOf(Color(0xFF101F32), Color(0xFF0C1726))))
            .border(1.dp, SkyBlue.copy(alpha = if (hero) 0.12f else 0.07f), shape)
            .padding(padding),
        content = content,
    )
}

@Composable
internal fun JournalHeader(title: String, subtitle: String, compact: Boolean = false) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = White, style = MaterialTheme.typography.titleLarge.copy(fontSize = if (compact) 20.sp else 22.sp))
        JournalLabel(subtitle, size = 11)
    }
}

@Composable
internal fun JournalScoreHeading(title: String, score: Int, accent: Color = SkyBlue) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = White, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp))
        Text("${score}점", color = accent, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            modifier = Modifier.background(accent.copy(alpha = 0.09f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp))
    }
}
