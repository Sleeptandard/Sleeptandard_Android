package com.leejang.sleeptandard.Component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun InSettingFrame(
    modifier: Modifier,
    onBack: () -> Unit,
    onButton: () -> Unit,
    content: @Composable () -> Unit,
){
    Column(

    ) {
        content
    }


}