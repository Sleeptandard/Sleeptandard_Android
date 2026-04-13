package com.leejang.sleeptandard.Component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun LiquidGlassBox(
    modifier:Modifier = Modifier,
    content: @Composable ()-> Unit
){
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,

    ){
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(color = Color(0x66C7DCFF), shape = CircleShape)
        )

        content()
    }
}