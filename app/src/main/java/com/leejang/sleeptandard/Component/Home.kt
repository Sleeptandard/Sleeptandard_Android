package com.leejang.sleeptandard.Component

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.leejang.sleeptandard.ui.theme.AppIcons
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
fun OptionsSection(
    modifier: Modifier = Modifier,
    onSoundClick: ()->Unit,
    onVibrationClick: ()->Unit,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    alarmName: String,
    isSystemVibrationOn: Boolean,
    isRem: Boolean,
    onRemCheckedChange: (Boolean) -> Unit
) {
    val isNone = alarmName == "소리 없음"

    val textColor =
        if (isNone) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        else MaterialTheme.colorScheme.onSurface

    var entireHeight = 164.dp
    var vibSurfaceHeight = 54.dp
    var vibTogglechecked = checked
    var vibToggleEnabled = true

    if (!isSystemVibrationOn) {
        entireHeight += 16.dp
        vibSurfaceHeight = 70.dp
        vibTogglechecked = false
        vibToggleEnabled = false
    }


    LazyColumn(
        modifier = modifier
            .height(entireHeight)
            .background(
                color = Color.Transparent
            )
            .fillMaxWidth()
        ,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 30.dp, bottom = 10.dp) // 그림자 잘림 방지
    ) {
        // 1. 소리 설정 박스
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth(95f/100f)
                    .height(56.dp)
                    //.size(320.dp, 56.dp)
                    .drawBehind {
                        // 흰색 그림자
                        val highlightColor1 = Color(0xFFB9C8DF).copy(alpha = 0.15f)
                        val blurRadius1 = 20.dp.toPx()
                        val offsetX1 = (-5).dp.toPx()
                        val offsetY1 = (-5).dp.toPx()

                        drawIntoCanvas { canvas ->
                            val paint = Paint().asFrameworkPaint().apply {
                                color = highlightColor1.toArgb()
                                maskFilter = BlurMaskFilter(blurRadius1, BlurMaskFilter.Blur.NORMAL)
                            }

                            canvas.nativeCanvas.drawRoundRect(
                                offsetX1, offsetY1,
                                size.width + offsetX1, size.height + offsetY1,
                                // 여기
                                100.dp.toPx(), 100.dp.toPx(),
                                paint
                            )
                        }

                        // 검은색 그림자
                        // 여기
                        val highlightColor2 = Color(0xFF020710).copy(alpha = 0.7f)
                        val blurRadius2 = 15.dp.toPx()
                        val offsetX2 = (8).dp.toPx()
                        val offsetY2 = (8).dp.toPx()

                        drawIntoCanvas { canvas ->
                            val paint = Paint().asFrameworkPaint().apply {
                                color = highlightColor2.toArgb()
                                maskFilter = BlurMaskFilter(blurRadius2, BlurMaskFilter.Blur.NORMAL)
                            }

                            canvas.nativeCanvas.drawRoundRect(
                                offsetX2, offsetY2,
                                size.width + offsetX2, size.height + offsetY2,
                                // 여기
                                100.dp.toPx(), 100.dp.toPx(),
                                paint
                            )
                        }

                        val gradient = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF07101E),
                                Color(0xFF101A2A)
                            ),
                            // 시작점을 박스의 정중앙(Center)으로 설정
                            start = Offset(size.width / 2, size.height / 2),
                            // 끝점을 박스의 우측 상단으로부터 2/3 지점 설정
                            end = Offset(size.width, size.height * 2 / 3)
                        )
                        drawRoundRect(
                            brush = gradient,
                            cornerRadius = CornerRadius(30.dp.toPx(), 30.dp.toPx()) // 30dp만큼 둥글게
                        )
                    }
                    // Inner shadow
                    .innerShadow(
                        shape = RoundedCornerShape(30.dp),
                        shadow = Shadow(
                            radius = 25.dp,
                            spread = (-12).dp,
                            color = Color(0xFF030E1E).copy(0.8f),
                            offset = DpOffset(x = 5.dp, 6.dp)
                        )
                    )
                    .clickable {
                        onSoundClick()
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(AppIcons.HomeVolume),
                        contentDescription = "알람음 설정",
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        modifier = Modifier
                            .weight(1f),
                        text = alarmName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 15.sp
                        ),
                        textAlign = TextAlign.End,
                        color = textColor
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        // 2. 진동 설정 박스
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth(95f/100f)
                    .height(56.dp)
                    //.size(320.dp, 56.dp)
                    .drawBehind {
                        // 흰색 그림자
                        val highlightColor1 = Color(0xFFB9C8DF).copy(alpha = 0.15f)
                        val blurRadius1 = 20.dp.toPx()
                        val offsetX1 = (-5).dp.toPx()
                        val offsetY1 = (-5).dp.toPx()

                        drawIntoCanvas { canvas ->
                            val paint = Paint().asFrameworkPaint().apply {
                                color = highlightColor1.toArgb()
                                maskFilter = BlurMaskFilter(blurRadius1, BlurMaskFilter.Blur.NORMAL)
                            }

                            canvas.nativeCanvas.drawRoundRect(
                                offsetX1, offsetY1,
                                size.width + offsetX1, size.height + offsetY1,
                                // 여기
                                100.dp.toPx(), 100.dp.toPx(),
                                paint
                            )
                        }

                        // 검은색 그림자
                        // 여기
                        val highlightColor2 = Color(0xFF020710).copy(alpha = 0.7f)
                        val blurRadius2 = 15.dp.toPx()
                        val offsetX2 = (8).dp.toPx()
                        val offsetY2 = (8).dp.toPx()

                        drawIntoCanvas { canvas ->
                            val paint = Paint().asFrameworkPaint().apply {
                                color = highlightColor2.toArgb()
                                maskFilter = BlurMaskFilter(blurRadius2, BlurMaskFilter.Blur.NORMAL)
                            }

                            canvas.nativeCanvas.drawRoundRect(
                                offsetX2, offsetY2,
                                size.width + offsetX2, size.height + offsetY2,
                                // 여기
                                100.dp.toPx(), 100.dp.toPx(),
                                paint
                            )
                        }

                        val gradient = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF07101E),
                                Color(0xFF101A2A)
                            ),
                            // 시작점을 박스의 정중앙(Center)으로 설정
                            start = Offset(size.width / 2, size.height / 2),
                            // 끝점을 박스의 우측 상단으로부터 2/3 지점 설정
                            end = Offset(size.width, size.height * 2 / 3)
                        )
                        drawRoundRect(
                            brush = gradient,
                            cornerRadius = CornerRadius(30.dp.toPx(), 30.dp.toPx()) // 30dp만큼 둥글게
                        )
                    }
                    // Inner shadow
                    .innerShadow(
                        shape = RoundedCornerShape(30.dp),
                        shadow = Shadow(
                            radius = 25.dp,
                            spread = (-12).dp,
                            color = Color(0xFF030E1E).copy(0.8f),
                            offset = DpOffset(x = 5.dp, 6.dp)
                        )
                    )
                    .clickable {
                        onVibrationClick()
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(
                            painter = painterResource(AppIcons.HomeVibrate),
                            contentDescription = "진동 설정",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(Modifier.weight(1f))
                        Switch(
                            modifier = Modifier
                                .scale(37f / 52f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFB1F7FC),
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFF858585),
                            ),
                            checked = vibTogglechecked,
                            onCheckedChange = onCheckedChange,
                            enabled = vibToggleEnabled
                        )
                    }
                    if (!isSystemVibrationOn) {
                        Text(
                            text = "※ 시스템 알림 진동세기가 0으로 설정되어 있어 진동이 울리지 않아요!",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = Color(0xFFEB3737)
                            ),
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }

            }
        }

        // 3. 추가 박스 (현재 코드에 있는 중복 박스 유지)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth(95f/100f)
                    .height(56.dp)
                    //.size(320.dp, 56.dp)
                    .drawBehind {
                        // 흰색 그림자
                        val highlightColor1 = Color(0xFFB9C8DF).copy(alpha = 0.15f)
                        val blurRadius1 = 20.dp.toPx()
                        val offsetX1 = (-5).dp.toPx()
                        val offsetY1 = (-5).dp.toPx()

                        drawIntoCanvas { canvas ->
                            val paint = Paint().asFrameworkPaint().apply {
                                color = highlightColor1.toArgb()
                                maskFilter = BlurMaskFilter(blurRadius1, BlurMaskFilter.Blur.NORMAL)
                            }

                            canvas.nativeCanvas.drawRoundRect(
                                offsetX1, offsetY1,
                                size.width + offsetX1, size.height + offsetY1,
                                // 여기
                                100.dp.toPx(), 100.dp.toPx(),
                                paint
                            )
                        }

                        // 검은색 그림자
                        // 여기
                        val highlightColor2 = Color(0xFF020710).copy(alpha = 0.7f)
                        val blurRadius2 = 15.dp.toPx()
                        val offsetX2 = (8).dp.toPx()
                        val offsetY2 = (8).dp.toPx()

                        drawIntoCanvas { canvas ->
                            val paint = Paint().asFrameworkPaint().apply {
                                color = highlightColor2.toArgb()
                                maskFilter = BlurMaskFilter(blurRadius2, BlurMaskFilter.Blur.NORMAL)
                            }

                            canvas.nativeCanvas.drawRoundRect(
                                offsetX2, offsetY2,
                                size.width + offsetX2, size.height + offsetY2,
                                // 여기
                                100.dp.toPx(), 100.dp.toPx(),
                                paint
                            )
                        }

                        val gradient = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF07101E),
                                Color(0xFF101A2A)
                            ),
                            // 시작점을 박스의 정중앙(Center)으로 설정
                            start = Offset(size.width / 2, size.height / 2),
                            // 끝점을 박스의 우측 상단으로부터 2/3 지점 설정
                            end = Offset(size.width, size.height * 2 / 3)
                        )
                        drawRoundRect(
                            brush = gradient,
                            cornerRadius = CornerRadius(30.dp.toPx(), 30.dp.toPx()) // 30dp만큼 둥글게
                        )
                    }
                    // Inner shadow
                    .innerShadow(
                        shape = RoundedCornerShape(30.dp),
                        shadow = Shadow(
                            radius = 25.dp,
                            spread = (-12).dp,
                            color = Color(0xFF030E1E).copy(0.8f),
                            offset = DpOffset(x = 5.dp, 6.dp)
                        )
                    )
                    .clickable {
                        onVibrationClick()
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        /*
                        Icon(
                            painter = painterResource(AppIcons.HomeVibrate),
                            contentDescription = "진동 설정",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                         */
                        Text(
                            text = if(isRem) "REM" else "N1",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 15.sp
                            ),
                        )
                        Spacer(Modifier.weight(1f))
                        Switch(
                            modifier = Modifier
                                .scale(37f / 52f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFB1F7FC),
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFF858585),
                            ),
                            checked = isRem,
                            onCheckedChange = onRemCheckedChange,
                        )
                    }
                }

            }
        }

        /*
        Box(
            modifier = Modifier
                .fillMaxWidth(95f/100f)
                .height(56.dp)
                //.size(320.dp, 56.dp)
                .drawBehind {
                    // 흰색 그림자
                    val highlightColor1 = Color(0xFFB9C8DF).copy(alpha = 0.15f)
                    val blurRadius1 = 20.dp.toPx()
                    val offsetX1 = (-5).dp.toPx()
                    val offsetY1 = (-5).dp.toPx()

                    drawIntoCanvas { canvas ->
                        val paint = Paint().asFrameworkPaint().apply {
                            color = highlightColor1.toArgb()
                            maskFilter = BlurMaskFilter(blurRadius1, BlurMaskFilter.Blur.NORMAL)
                        }

                        canvas.nativeCanvas.drawRoundRect(
                            offsetX1, offsetY1,
                            size.width + offsetX1, size.height + offsetY1,
                            // 여기
                            100.dp.toPx(), 100.dp.toPx(),
                            paint
                        )
                    }

                    // 검은색 그림자
                    // 여기
                    val highlightColor2 = Color(0xFF020710).copy(alpha = 0.7f)
                    val blurRadius2 = 15.dp.toPx()
                    val offsetX2 = (8).dp.toPx()
                    val offsetY2 = (8).dp.toPx()

                    drawIntoCanvas { canvas ->
                        val paint = Paint().asFrameworkPaint().apply {
                            color = highlightColor2.toArgb()
                            maskFilter = BlurMaskFilter(blurRadius2, BlurMaskFilter.Blur.NORMAL)
                        }

                        canvas.nativeCanvas.drawRoundRect(
                            offsetX2, offsetY2,
                            size.width + offsetX2, size.height + offsetY2,
                            // 여기
                            100.dp.toPx(), 100.dp.toPx(),
                            paint
                        )
                    }

                    val gradient = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF07101E),
                            Color(0xFF101A2A)
                        ),
                        // 시작점을 박스의 정중앙(Center)으로 설정
                        start = Offset(size.width / 2, size.height / 2),
                        // 끝점을 박스의 우측 상단으로부터 2/3 지점 설정
                        end = Offset(size.width, size.height * 2 / 3)
                    )
                    drawRoundRect(
                        brush = gradient,
                        cornerRadius = CornerRadius(30.dp.toPx(), 30.dp.toPx()) // 30dp만큼 둥글게
                    )
                }
                // Inner shadow
                .innerShadow(
                    shape = RoundedCornerShape(30.dp),
                    shadow = Shadow(
                        radius = 25.dp,
                        spread = (-12).dp,
                        color = Color(0xFF030E1E).copy(0.8f),
                        offset = DpOffset(x = 5.dp, 6.dp)
                    )
                )
                .clickable {
                    onSoundClick()
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(AppIcons.HomeVolume),
                    contentDescription = "알람음 설정",
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    modifier = Modifier
                        .weight(1f),
                    text = alarmName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp
                    ),
                    textAlign = TextAlign.End,
                    color = textColor
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth(95f/100f)
                .height(56.dp)
                //.size(320.dp, 56.dp)
                .drawBehind {
                    // 흰색 그림자
                    val highlightColor1 = Color(0xFFB9C8DF).copy(alpha = 0.15f)
                    val blurRadius1 = 20.dp.toPx()
                    val offsetX1 = (-5).dp.toPx()
                    val offsetY1 = (-5).dp.toPx()

                    drawIntoCanvas { canvas ->
                        val paint = Paint().asFrameworkPaint().apply {
                            color = highlightColor1.toArgb()
                            maskFilter = BlurMaskFilter(blurRadius1, BlurMaskFilter.Blur.NORMAL)
                        }

                        canvas.nativeCanvas.drawRoundRect(
                            offsetX1, offsetY1,
                            size.width + offsetX1, size.height + offsetY1,
                            // 여기
                            100.dp.toPx(), 100.dp.toPx(),
                            paint
                        )
                    }

                    // 검은색 그림자
                    // 여기
                    val highlightColor2 = Color(0xFF020710).copy(alpha = 0.7f)
                    val blurRadius2 = 15.dp.toPx()
                    val offsetX2 = (8).dp.toPx()
                    val offsetY2 = (8).dp.toPx()

                    drawIntoCanvas { canvas ->
                        val paint = Paint().asFrameworkPaint().apply {
                            color = highlightColor2.toArgb()
                            maskFilter = BlurMaskFilter(blurRadius2, BlurMaskFilter.Blur.NORMAL)
                        }

                        canvas.nativeCanvas.drawRoundRect(
                            offsetX2, offsetY2,
                            size.width + offsetX2, size.height + offsetY2,
                            // 여기
                            100.dp.toPx(), 100.dp.toPx(),
                            paint
                        )
                    }

                    val gradient = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF07101E),
                            Color(0xFF101A2A)
                        ),
                        // 시작점을 박스의 정중앙(Center)으로 설정
                        start = Offset(size.width / 2, size.height / 2),
                        // 끝점을 박스의 우측 상단으로부터 2/3 지점 설정
                        end = Offset(size.width, size.height * 2 / 3)
                    )
                    drawRoundRect(
                        brush = gradient,
                        cornerRadius = CornerRadius(30.dp.toPx(), 30.dp.toPx()) // 30dp만큼 둥글게
                    )
                }
                // Inner shadow
                .innerShadow(
                    shape = RoundedCornerShape(30.dp),
                    shadow = Shadow(
                        radius = 25.dp,
                        spread = (-12).dp,
                        color = Color(0xFF030E1E).copy(0.8f),
                        offset = DpOffset(x = 5.dp, 6.dp)
                    )
                )
                .clickable {
                    onVibrationClick()
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        painter = painterResource(AppIcons.HomeVibrate),
                        contentDescription = "진동 설정",
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(Modifier.weight(1f))
                    Switch(
                        modifier = Modifier
                            .scale(37f / 52f),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFB1F7FC),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFF858585),
                        ),
                        checked = vibTogglechecked,
                        onCheckedChange = onCheckedChange,
                        enabled = vibToggleEnabled
                    )
                }
                if (!isSystemVibrationOn) {
                    Text(
                        text = "※ 시스템 알림 진동세기가 0으로 설정되어 있어 진동이 울리지 않아요!",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = Color(0xFFEB3737)
                        ),
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }

        }

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth(95f/100f)
                .height(56.dp)
                //.size(320.dp, 56.dp)
                .drawBehind {
                    // 흰색 그림자
                    val highlightColor1 = Color(0xFFB9C8DF).copy(alpha = 0.15f)
                    val blurRadius1 = 20.dp.toPx()
                    val offsetX1 = (-5).dp.toPx()
                    val offsetY1 = (-5).dp.toPx()

                    drawIntoCanvas { canvas ->
                        val paint = Paint().asFrameworkPaint().apply {
                            color = highlightColor1.toArgb()
                            maskFilter = BlurMaskFilter(blurRadius1, BlurMaskFilter.Blur.NORMAL)
                        }

                        canvas.nativeCanvas.drawRoundRect(
                            offsetX1, offsetY1,
                            size.width + offsetX1, size.height + offsetY1,
                            // 여기
                            100.dp.toPx(), 100.dp.toPx(),
                            paint
                        )
                    }

                    // 검은색 그림자
                    // 여기
                    val highlightColor2 = Color(0xFF020710).copy(alpha = 0.7f)
                    val blurRadius2 = 15.dp.toPx()
                    val offsetX2 = (8).dp.toPx()
                    val offsetY2 = (8).dp.toPx()

                    drawIntoCanvas { canvas ->
                        val paint = Paint().asFrameworkPaint().apply {
                            color = highlightColor2.toArgb()
                            maskFilter = BlurMaskFilter(blurRadius2, BlurMaskFilter.Blur.NORMAL)
                        }

                        canvas.nativeCanvas.drawRoundRect(
                            offsetX2, offsetY2,
                            size.width + offsetX2, size.height + offsetY2,
                            // 여기
                            100.dp.toPx(), 100.dp.toPx(),
                            paint
                        )
                    }

                    val gradient = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF07101E),
                            Color(0xFF101A2A)
                        ),
                        // 시작점을 박스의 정중앙(Center)으로 설정
                        start = Offset(size.width / 2, size.height / 2),
                        // 끝점을 박스의 우측 상단으로부터 2/3 지점 설정
                        end = Offset(size.width, size.height * 2 / 3)
                    )
                    drawRoundRect(
                        brush = gradient,
                        cornerRadius = CornerRadius(30.dp.toPx(), 30.dp.toPx()) // 30dp만큼 둥글게
                    )
                }
                // Inner shadow
                .innerShadow(
                    shape = RoundedCornerShape(30.dp),
                    shadow = Shadow(
                        radius = 25.dp,
                        spread = (-12).dp,
                        color = Color(0xFF030E1E).copy(0.8f),
                        offset = DpOffset(x = 5.dp, 6.dp)
                    )
                )
                .clickable {
                    onVibrationClick()
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        painter = painterResource(AppIcons.HomeVibrate),
                        contentDescription = "진동 설정",
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(Modifier.weight(1f))
                    Switch(
                        modifier = Modifier
                            .scale(37f / 52f),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFB1F7FC),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFF858585),
                        ),
                        checked = vibTogglechecked,
                        onCheckedChange = onCheckedChange,
                        enabled = vibToggleEnabled
                    )
                }
                if (!isSystemVibrationOn) {
                    Text(
                        text = "※ 시스템 알림 진동세기가 0으로 설정되어 있어 진동이 울리지 않아요!",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = Color(0xFFEB3737)
                        ),
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }

        }

         */
    }
}

// ✅ 중복되는 그림자 및 배경 로직을 위한 공통 컴포넌트
@Composable
fun OptionBox(
    height: androidx.compose.ui.unit.Dp = 56.dp,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .height(height)
            .drawBehind {
                // 그림자 및 그라데이션 로직 (기존 코드와 동일)
                // ... (생략: 기존의 drawBehind 및 innerShadow 로직)
            }
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun ConfirmButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        modifier = modifier
            .height(56.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(100.dp),
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFF1F4F9)
        )
    ) {
        Text(
            text = "완료",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 18.sp,
                color = Color(0xFF050C16),
                fontWeight = FontWeight(600)
            )
        )
    }
}

@Composable
fun DiamondStepSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: IntRange = 10..30,
    step: Int = 1
) {
    val steps = remember { valueRange.step(step).toList() }
    val density = LocalDensity.current

    // ✅ 양옆 터치 여유 공간 (20dp 정도 주면 아주 넉넉합니다)
    val sideMarginPx = with(density) { 20.dp.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp) // 터치 높이도 조금 더 확보
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    // ✅ 터치 좌표에서 여유 공간을 뺀 값을 기준으로 비율 계산
                    val usableWidth = size.width - (2 * sideMarginPx)
                    val ratio = ((offset.x - sideMarginPx) / usableWidth).coerceIn(0f, 1f)
                    val rawValue = valueRange.first + (valueRange.last - valueRange.first) * ratio
                    val snappedValue = steps.minByOrNull { abs(it - rawValue) } ?: value
                    onValueChange(snappedValue)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val usableWidth = size.width - (2 * sideMarginPx)
                    val ratio = ((change.position.x - sideMarginPx) / usableWidth).coerceIn(0f, 1f)
                    val rawValue = valueRange.first + (valueRange.last - valueRange.first) * ratio
                    val snappedValue = steps.minByOrNull { abs(it - rawValue) } ?: value
                    onValueChange(snappedValue)
                }
            }
    ) {
        val fullWidth = constraints.maxWidth.toFloat()
        val usableWidth = fullWidth - (2 * sideMarginPx)

        val fraction = (value - valueRange.first).toFloat() / (valueRange.last - valueRange.first)
        // ✅ 손잡이 중심점이 sideMarginPx부터 시작하도록 설정
        val thumbCenterX = sideMarginPx + (usableWidth * fraction)

        // 1. 전체 트랙 (배경) - 양옆 여백 적용
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 20.dp) // ✅ sideMargin과 동일한 패딩
                .height(6.dp)
                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(100.dp))
        )

        // 2. 활성 트랙 (색칠되는 부분)
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(sideMarginPx.toInt(), 0) } // ✅ 시작점 보정
                .width(with(density) { (thumbCenterX - sideMarginPx).toDp() })
                .height(6.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFFAAEDF2).copy(alpha = 0.8f), Color(0xFFAAEDF2))
                    ),
                    shape = RoundedCornerShape(100.dp)
                )
        )

        // 3. 마름모 손잡이
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbCenterX.toInt() - 9.dp.toPx().toInt(), 0) }
                .align(Alignment.CenterStart)
                .size(18.dp)
                .graphicsLayer(rotationZ = 45f)
                .background(Color.White, RoundedCornerShape(2.dp))
        )
    }
}

fun calculateWakeUpRangeText(hour: Int, minute: Int, isAm: Boolean, earlyMinutes: Int): String {
    val calendar = java.util.Calendar.getInstance().apply {
        var h = hour % 12
        if (!isAm) h += 12
        set(java.util.Calendar.HOUR_OF_DAY, h)
        set(java.util.Calendar.MINUTE, minute)
    }

    val endTime = String.format("%s %d:%02d", if (isAm) "오전" else "오후", hour, minute)

    calendar.add(java.util.Calendar.MINUTE, -earlyMinutes)
    val startHour = if (calendar.get(java.util.Calendar.HOUR) == 0) 12 else calendar.get(java.util.Calendar.HOUR)
    val startIsAm = calendar.get(java.util.Calendar.AM_PM) == java.util.Calendar.AM
    val startTime = String.format("%s %d:%02d", if (startIsAm) "오전" else "오후", startHour, calendar.get(java.util.Calendar.MINUTE))

    return "$startTime ~ $endTime 사이 알람"
}

@Preview(showBackground = true)
@Composable
fun PreviewOptionsSection(){
    OptionsSection(
        onSoundClick = {},
        onVibrationClick = {},
        checked = true,
        onCheckedChange = {},
        alarmName = "Indigo Puff",
        isSystemVibrationOn = false,
        isRem = true,
        onRemCheckedChange = {}
    )
}