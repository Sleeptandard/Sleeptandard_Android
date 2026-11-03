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
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
)

val Paperlogy = FontFamily(
    Font(R.font.paperlogy_bold, FontWeight.Bold),
    Font(R.font.paperlogy_medium, FontWeight.Medium),
    Font(R.font.paperlogy_semibold, FontWeight.SemiBold)
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
    ),
    titleLarge = TextStyle(
        fontFamily = Paperlogy,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = 0.em
    ),
    titleMedium = TextStyle(
        fontFamily = Paperlogy,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        letterSpacing = 0.em
    ),
    titleSmall = TextStyle(
        fontFamily = Paperlogy,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        letterSpacing = 0.em
    ),
    labelSmall = TextStyle(
        fontFamily = Pretandard,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        letterSpacing = (-0.025).em
    )

)
