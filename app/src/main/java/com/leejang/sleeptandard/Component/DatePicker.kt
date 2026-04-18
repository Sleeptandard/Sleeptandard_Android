package com.leejang.sleeptandard.Component

/** 회원가입 / 계정정보 변경에서 사용하는 Date Picker
 *
 * 시발 모르겠다~
 *
 */


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leejang.sleeptandard.ViewModel.AuthViewModel

@Composable
fun BirthDatePicker(
    viewModel: AuthViewModel
) {

    Column(
        modifier = Modifier
            .size(281.dp, 334.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(color = Color.White),
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "생년월일",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 16.sp,
                    color = Color.Black
                )
            )
        }


        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.dp,
            color = Color(0xFF050C16).copy(alpha = 0.5f)
        )

        BirthDatePicker(
            modifier = Modifier
                .weight(1f),
            onDateChange = {y, m, d ->
                // ✅ 휠을 돌릴 때마다 VM의 임시 값 업데이트
                viewModel.updatePickerValues(y, m, d)
            },
            defaultYear = viewModel.pickerYear,
            defaultMonth = viewModel.pickerMonth,
            defaultDay = viewModel.pickerDay,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ){
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        color = Color(0xFFE0E0E0)
                    )
                    .clickable {
                        viewModel.closeDatePicker()
                    },
                contentAlignment = Alignment.Center
            ){
                Text(
                    text = "취소",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 16.sp,
                        color = Color.Black
                    )
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        color = Color(0xFFAFF4F9)
                    )
                    .clickable {
                        viewModel.confirmDatePickerSelection() // ✅ 최종 값 확정 및 모달 닫기
                    },
                contentAlignment = Alignment.Center
            ){
                Text(
                    text = "확인",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 16.sp,
                        color = Color.Black
                    )
                )
            }
        }
    }
}
