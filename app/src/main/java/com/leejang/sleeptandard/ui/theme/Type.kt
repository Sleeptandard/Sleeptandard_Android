package com.leejang.sleeptandard.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.leejang.sleeptandard.R


val Pretandard = FontFamily(
    Font(R.font.pretendard_bold, FontWeight.Bold),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold)
)

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = Pretandard,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = (-0.025).em
    ),
    bodyMedium = TextStyle(
        fontFamily = Pretandard,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        letterSpacing = (-0.025).em
    ),
    bodySmall = TextStyle(
        fontFamily = Pretandard,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        letterSpacing = (-0.025).em
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)
