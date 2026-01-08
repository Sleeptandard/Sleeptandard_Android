package com.example.sleeptandard_mvp_demo.Screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.sleeptandard_mvp_demo.ui.theme.LightBackground

@Composable
fun JournalScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("아직 준비 중인 기능입니다 ☺",
            style = MaterialTheme.typography.bodyLarge)
    }
}