package com.leejang.sleeptandard_mvp.Screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.leejang.sleeptandard_mvp.ui.theme.AppIcons

@Composable
fun JournalScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ){
            Text("아직 준비 중인 기능입니다",
                style = MaterialTheme.typography.bodyLarge)
            Icon(
                painter = painterResource(AppIcons.JournalSmile),
                contentDescription = "스마일"
            )
        }

    }
}