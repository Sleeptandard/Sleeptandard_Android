package com.leejang.sleeptandard.Screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.sp
import com.leejang.sleeptandard.ui.theme.AppIcons



@Composable
fun AccountManagementScreen(
    user: User = User("jjy@jjy.com","12345678", "WTF", "male", "2000.01.01"),
    onBack: () -> Unit,

){
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            verticalAlignment = Alignment.CenterVertically,
        ){
            IconButton(
                modifier = Modifier.size(32.dp),
                onClick = onBack
            ) {
                Icon(
                    modifier = Modifier.size(32.dp),
                    painter = painterResource(AppIcons.QnAArrowBack),
                    contentDescription = "뒤로가기",
                )
            }

            Spacer(Modifier.weight(1f))
            // 제목 (질문 타이틀)
            Text(
                text = "계정관리",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 20.sp,
                    )
            )

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(32.dp))
        }

        Spacer(Modifier.height(102.dp))

        Column(
            modifier = Modifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AMElement("개인 정보", "닉네임/성별/생년월일", onClick = {})
            AMElement("이메일", user.email, onClick = {})
            AMElement("비밀번호", "비밀번호 변경", onClick = {})
            AMElement("로그아웃/탈퇴", "", onClick = {})
        }
    }




}

@Composable
fun AMElement(
    title:String,
    contents: String,
    onClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clickable{
                onClick()
            },
        verticalAlignment = Alignment.CenterVertically,

    ) {
        Text(
            modifier = Modifier.padding(start = 10.dp),
            text = title,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 16.sp,
            )
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = contents,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        )
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ){
            Icon(
                modifier = Modifier.size(16.dp),
                painter = painterResource(AppIcons.HomeArrowRight),
                contentDescription = ">",
            )
        }
    }

}