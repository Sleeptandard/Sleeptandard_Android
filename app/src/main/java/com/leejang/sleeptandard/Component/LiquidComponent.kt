package com.leejang.sleeptandard.Component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.InnerShadow

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
                        vibrancy()
                        blur(4f.dp.toPx())
                        lens(16f.dp.toPx(), 32f.dp.toPx())
                    },
                    innerShadow = {
                        InnerShadow(
                            radius = 15.dp,
                            offset = DpOffset(x = (-5).dp, y = (-5).dp),
                            color = Color.Black,
                            alpha = 1f
                        )
                        InnerShadow(
                            radius = 15.dp,
                            offset = DpOffset(5.dp, 5.dp),
                            color = Color.Black,
                            alpha = 1f
                        )
                    },
                    onDrawSurface = {
                        val tint = Color(0xFFC7DCFF)
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = 0.4f))
                    },




                    )
                .matchParentSize()
        )

        content()
    }
}