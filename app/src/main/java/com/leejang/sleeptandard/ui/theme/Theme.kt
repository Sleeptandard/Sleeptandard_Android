package com.leejang.sleeptandard.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = WhiteFont,
    secondary = DarkSecondary,
    tertiary = DarkTertiary,
    surface = DarkSurface,
    onSurface = WhiteFont,
    background = DarkBackground
)

private val LightColorScheme = lightColorScheme(
    primary = DarkPrimary,
    onPrimary = WhiteFont,
    secondary = DarkSecondary,
    tertiary = DarkTertiary,
    surface = DarkSurface,
    onSurface = WhiteFont,


    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun Sleeptandard_MVP_DemoTheme(
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // 배경이 어두우므로 아이콘은 밝게(Light) 유지해야 합니다.
            // 아래 함수는 '아이콘을 어둡게 할 것인가'를 묻는 것이므로 false를 줍니다.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}