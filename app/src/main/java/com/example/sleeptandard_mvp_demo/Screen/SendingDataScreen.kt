package com.example.sleeptandard_mvp_demo.Screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.sleeptandard_mvp_demo.ui.theme.AppIcons

@Composable
fun SendingDataScreen(){
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        IconButton(
            modifier = Modifier
                .size(40.dp),
            onClick = {}
        ) {
            Icon(
                painter = painterResource(AppIcons.QnAArrowBack),
                contentDescription = "뒤로 가기"
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ){
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .background(color = Color(0xFF465467), shape = CircleShape)
                    .clickable{},
                contentAlignment = Alignment.Center
            ){
                Icon(
                    painterResource(AppIcons.SendingDataSend),
                    contentDescription = "데이터 보내기"
                )
            }
            Spacer(Modifier.height(18.dp))
            Text("수면 데이터 보내기",
                style = MaterialTheme.typography.bodyMedium
            )
        }

    }



}