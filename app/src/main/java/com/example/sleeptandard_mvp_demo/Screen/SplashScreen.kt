package com.example.sleeptandard_mvp_demo.Screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sleeptandard_mvp_demo.ui.theme.AppIcons

@Preview
@Composable
fun SplashScreen(){

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFD4DCE4))
            .padding(horizontal = 31.dp)
    ) {
        Column {

            Spacer(Modifier.height(233.dp))

            Text(
                text = "수면의",
                color = Color(0xFF0B111A),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 30.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight(500),
                    color = Color(0xFF000000),
                    letterSpacing = 0.25.sp,
                )
            )
            Text(
                text = "패러다임을 바꾸다.",
                color = Color(0xFF0B111A),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 30.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight(500),
                    color = Color(0xFF000000),
                    letterSpacing = 0.25.sp,
                    )
            )

            Spacer(Modifier.height(62.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {

                Spacer(Modifier.width(55.dp))

                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .background(Color(0xFFFFFFFF), shape = RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(AppIcons.SplashLogo),
                        contentDescription = "로고",
                        Modifier.size(35.dp)
                    )
                }


                Spacer(Modifier.width(20.dp))

                Text(
                    text = "알람의 정석",
                    color = Color(0xFF0B111A),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 24.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight(500),
                        color = Color(0xFF000000),
                        letterSpacing = 0.25.sp,
                    )
                )


            }
        }
    }
}