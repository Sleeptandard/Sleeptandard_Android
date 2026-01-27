package com.example.sleeptandard_mvp_demo.Component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.sleeptandard_mvp_demo.ui.theme.AppIcons
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.example.sleeptandard_mvp_demo.ui.theme.DarkSurface
import com.example.sleeptandard_mvp_demo.ui.theme.LightBackground

@Composable
fun OptionsSection(
    modifier: Modifier = Modifier,
    onSoundClick: ()->Unit,
    onVibrationClick: ()->Unit,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    alarmName: String,
) {
    val isNone = alarmName == "소리 없음"

    val textColor =
        if (isNone) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        else MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier
            .height(140.dp)
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(size = 26.dp)
            )
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
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
                    .fillMaxSize(),
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
                .height(54.dp),
            color = Color.Transparent,
            onClick = onVibrationClick
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize(),
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
                    checked = checked,
                    onCheckedChange = onCheckedChange
                )
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