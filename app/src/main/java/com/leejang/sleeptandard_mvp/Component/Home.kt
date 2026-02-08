package com.leejang.sleeptandard_mvp.Component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.leejang.sleeptandard_mvp.ui.theme.AppIcons
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp

@Composable
fun OptionsSection(
    modifier: Modifier = Modifier,
    onSoundClick: ()->Unit,
    onVibrationClick: ()->Unit,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    alarmName: String,
    isSystemVibrationOn: Boolean,
) {
    val isNone = alarmName == "소리 없음"

    val textColor =
        if (isNone) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        else MaterialTheme.colorScheme.onSurface

    var entireHeight = 140.dp
    var vibSurfaceHeight = 54.dp
    var vibTogglechecked = checked
    var vibToggleEnabled = checked

    if (!isSystemVibrationOn) {
        entireHeight = 156.dp
        vibSurfaceHeight = 70.dp
        vibTogglechecked = false
        vibToggleEnabled = false
    }


    Column(
        modifier = modifier
            .height(entireHeight)
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(size = 26.dp)
            )
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            color = Color.Transparent,
            onClick = onSoundClick
        ) {
            Row(
                modifier = Modifier
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(AppIcons.HomeVolume),
                    contentDescription = "알람음 설정",
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    modifier = Modifier
                        .weight(1f),
                    text = alarmName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp
                    ),
                    textAlign = TextAlign.End,
                    color = textColor
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
        }
        HorizontalDivider(Modifier
            .fillMaxWidth()
            .height(0.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(vibSurfaceHeight),
            color = Color.Transparent,
            onClick = onVibrationClick
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        painter = painterResource(AppIcons.HomeVibrate),
                        contentDescription = "진동 설정",
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(Modifier.weight(1f))
                    Switch(
                        modifier = Modifier
                            .scale(37f/52f),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFB1F7FC),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFF858585),
                        ),
                        checked = vibTogglechecked,
                        onCheckedChange = onCheckedChange,
                        enabled = vibToggleEnabled
                    )
                }
                if (!isSystemVibrationOn){
                    Text(
                        text = "※ 시스템 알림 진동세기가 0으로 설정되어 있어 진동이 울리지 않아요!",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = Color(0xFFEB3737)
                        ),
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }

        }
    }
}

@Composable
fun ConfirmButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        modifier = modifier
            .height(48.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(100.dp),
        onClick = onClick,
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
    ) {
        Text(
            text = "완료",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 18.sp
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewOptionsSection(){
    OptionsSection(
        onSoundClick = {},
        onVibrationClick = {},
        checked = true,
        onCheckedChange = {},
        alarmName = "Indigo Puff",
        isSystemVibrationOn = false
    )
}