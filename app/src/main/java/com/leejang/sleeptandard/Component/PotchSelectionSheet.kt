package com.leejang.sleeptandard.Component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leejang.sleeptandard.Potch.DiscoveredPotch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PotchSelectionSheet(
    devices: List<DiscoveredPotch>,
    isScanning: Boolean,
    errorMessage: String?,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF07111E),
        scrimColor = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        contentWindowInsets = { WindowInsets(0,0,0,0)}
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "팟치 선택",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(18.dp))

            when {
                devices.isNotEmpty() -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                    ) {
                        items(devices, key = { it.address }) { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(device.address) }
                                    .padding(vertical = 15.dp, horizontal = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = device.name,
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = device.address
                                        .replace(":", "")
                                        .takeLast(8)
                                        .uppercase(),
                                    color = Color(0xFF8E9AAA),
                                    fontSize = 12.sp
                                )
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        }
                    }
                }

                isScanning -> {
                    Row(
                        modifier = Modifier.padding(vertical = 36.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color(0xFF9BB9C7),
                            strokeWidth = 2.dp
                        )
                        Text("연결 가능한 팟치를 찾는 중입니다.", color = Color(0xFFAAB5C3))
                    }
                }

                else -> {
                    Text(
                        text = errorMessage ?: "연결 가능한 팟치를 찾지 못했습니다.",
                        modifier = Modifier.padding(vertical = 36.dp),
                        color = Color(0xFFFF8A8A),
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Button(
                onClick = if (isScanning) onDismiss else onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF91AAB5),
                    contentColor = Color(0xFF07111E)
                ),
                shape = RoundedCornerShape(26.dp)
            ) {
                Text(
                    text = if (isScanning) "닫기" else "다시 검색",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
