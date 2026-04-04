package com.leejang.sleeptandard.Component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens

@Composable
fun LiquidGlassBox(
    modifier:Modifier = Modifier,
    backdrop: Backdrop,
    content: @Composable ()-> Unit
){
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,

    ){
        Box(
            modifier = Modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        lens(16f.dp.toPx(), 32f.dp.toPx())
                    }
                )
                .matchParentSize()
        )

        content()
    }
}