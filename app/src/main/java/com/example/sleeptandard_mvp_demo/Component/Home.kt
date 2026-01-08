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
fun AlarmBottomNavBar(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            StandaloneBottomItem(
                selected = selectedIndex == 0,
                iconRes = AppIcons.NavAlarm,
                label = "알람",
                onClick = { onSelect(0) }
            )
            StandaloneBottomItem(
                selected = selectedIndex == 1,
                iconRes = AppIcons.NavJournal,
                label = "일지",
                onClick = { onSelect(1) }
            )
            StandaloneBottomItem(
                selected = selectedIndex == 2,
                iconRes = AppIcons.NavSettings,
                label = "설정",
                onClick = { onSelect(2) }
            )
        }
    }
}



@Composable
fun StandaloneBottomItem(
    selected: Boolean,
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab
            )
            .padding(0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            // ✅ 선택: 원본색 유지 / 비선택: 회색 틴트
            tint = if (selected) Color(0xFFE0F5FD)
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )

        if (selected) {
            Spacer(Modifier.height(7.dp))
            // ✅ 점 표시
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = Color(0xFFE0F5FD),
                        shape = CircleShape
                    )
            )
        } else {
            // ✅ 텍스트 표시
            Text(
                text = label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
    }
}


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


@Preview
@Composable
fun OptionSectionPreview(
){

    // 임시 지정
    val alarmName = "Indigo Puff"

    val isNone = alarmName == "소리 없음"

    val textColor =
        if (isNone) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        else MaterialTheme.colorScheme.onSurface

    var checked = true

    Column(
        modifier = Modifier
            .width(313.dp)
            .height(140.dp)
            .background(color = DarkSurface, shape = RoundedCornerShape(size = 26.dp))
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            color = Color(0x0DFFFFFF),
            onClick = {}
        ) {
        Row(modifier = Modifier
                .fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ){
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
            color = Color(0x0DFFFFFF),
            onClick = {}
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                verticalAlignment = Alignment.CenterVertically,
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
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color(0xFF858585)
                    ),
                    checked = checked,
                    onCheckedChange = { no -> checked = no }
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



/* 밝은 버전의 UI 시절 코드
@Composable
fun AlarmBottomNavBar(
    onHomeClick: () -> Unit,
    onScheduleClick: () -> Unit,
    onSettingsClick: () -> Unit,
){
    NavigationBar() {
        NavigationBarItem(
            selected = true,
            onClick = onHomeClick,
            icon = {
                Icon(
                    painter = painterResource(AppIcons.NavAlarm),
                    contentDescription = "알람"
                )
            },
            label = {Text("알람")}
        )
        NavigationBarItem(
            selected = false,
            onClick = onScheduleClick,
            icon = {
                Icon(
                    painter = painterResource(AppIcons.NavJournal),
                    contentDescription = "일지") },
            label = { Text("일지") }
        )
        NavigationBarItem(
            selected = false,
            onClick = onSettingsClick,
            icon = {
                Icon(
                    painter = painterResource(AppIcons.NavSettings),
                    contentDescription = "설정") },
            label = { Text("설정") }
        )
    }
}
 */

/* 다크 모드 적용 전 옵션 섹션 UI
@Composable
fun OptionsSection(
    modifier: Modifier = Modifier,
    onSoundClick: ()->Unit,
    onVibrationClick: ()->Unit,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    alarmName: String
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 사운드 선택 영역 (더 넓게)
        SoundOptionCard(
            modifier = Modifier.weight(2f),
            onClick = onSoundClick,
            alarmName = alarmName,
        )

        // 진동 토글 영역 (조금 좁게)
        VibrationOptionCard(
            modifier = Modifier.weight(1f),
            onClick = onVibrationClick,
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
 */
/*
@Composable
fun SoundOptionCard(
    modifier: Modifier = Modifier,
    onClick: ()->Unit,
    alarmName: String
) {
    val isNone = alarmName == "소리 없음"

    val containerColor =
        if (isNone) MaterialTheme.colorScheme.surfaceDim
        else MaterialTheme.colorScheme.surface

    // ✅ 아이콘/텍스트 색도 상태에 따라 변경
    val iconTint =
        if (isNone) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.onSurface

    val textColor =
        if (isNone) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = modifier
            .height(68.dp)
            .width(123.dp),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        tonalElevation = 1.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                painter = painterResource(AppIcons.HomeVolume),
                contentDescription = "알람음 설정",
                tint = iconTint
            )
            Text(
                text = alarmName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp
                ),
                color = textColor
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = iconTint
            )
        }
    }
}

@Composable
fun VibrationOptionCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
    ) {
    val isNone = !checked

    val containerColor =
        if (isNone) MaterialTheme.colorScheme.surfaceDim
        else MaterialTheme.colorScheme.surface

    // ✅ 아이콘/텍스트 색도 상태에 따라 변경
    val iconTint =
        if (isNone) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = modifier
            .height(68.dp)
            .width(123.dp),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                painter = painterResource(AppIcons.HomeVibrate),
                contentDescription = "진동 설정",
                tint = iconTint
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
 */
