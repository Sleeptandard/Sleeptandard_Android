package com.leejang.sleeptandard.Screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.leejang.sleeptandard.Component.neumorphicBackground
import com.leejang.sleeptandard.Potch.DiscoveredPotch
import com.leejang.sleeptandard.Potch.PotchBleViewModel
import com.leejang.sleeptandard.ui.theme.AppIcons
import com.leejang.sleeptandard.ui.theme.Key
import com.leejang.sleeptandard.ui.theme.SkyBlue
import com.leejang.sleeptandard.ui.theme.White
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun PotchScreen(
    potchViewModel: PotchBleViewModel = viewModel()
) {
    val context = LocalContext.current
    val bleState by potchViewModel.bleState.collectAsState()
    val processorState by potchViewModel.processorState.collectAsState()
    val latestData = processorState.lastParsedData
    var showConnectionSheet by remember { mutableStateOf(false) }
    var selectedAddress by remember { mutableStateOf<String?>(null) }
    var displayedDevices by remember { mutableStateOf<List<DiscoveredPotch>>(emptyList()) }
    var permissionDenied by remember { mutableStateOf(false) }
    val isPotchConnected = bleState.isConnected || bleState.isNotificationReady

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val allGranted = requiredPotchPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED
        }
        permissionDenied = !allGranted
        if (allGranted && showConnectionSheet) {
            potchViewModel.startDeviceDiscovery()
        }
    }

    LaunchedEffect(showConnectionSheet, selectedAddress, bleState.discoveredDevices) {
        if (showConnectionSheet && selectedAddress == null) {
            displayedDevices = bleState.discoveredDevices
        }
    }

    val heartRateText = processorState.heartRateBpm?.let { "$it bpm" } ?: "-- bpm"
    val temperatureText = latestData
        ?.ntcCelsius
        ?.takeIf { it.isFinite() }
        ?.let { String.format(Locale.getDefault(), "%.1f °C", it) }
        ?: "-- °C"
    val batteryText = latestData
        ?.batteryVoltage
        ?.takeIf { it.isFinite() }
        ?.let(::voltageToPotchBatteryPercent)
        ?.let { "$it%" }
        ?: "--%"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
    ) {
        Spacer(Modifier.height(32.dp))

        Text(
            text = "팟치",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 20.sp,
                color = White
            )
        )

        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Row(
                modifier = Modifier
                    .width(160.dp)
                    .height(48.dp)
                    .neumorphicBackground(
                        highlightColor = Color(0xFFB9C8DF).copy(alpha = 0.1f)
                    )
                    .innerShadow(
                        shape = RoundedCornerShape(30.dp),
                        shadow = Shadow(
                            radius = 25.dp,
                            spread = (-12).dp,
                            color = Color(0xFF030E1E).copy(alpha = 0.8f),
                            offset = DpOffset(x = 5.dp, y = 6.dp)
                        )
                    )
                    .clickable { },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "업데이트 하러가기",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 14.sp,
                        color = White
                    )
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    painter = painterResource(AppIcons.HomeArrowRight),
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = White
                )
            }
        }

        Spacer(Modifier.height(54.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PotchStatusCard(
                    modifier = Modifier.weight(1f),
                    iconRes = AppIcons.PotchHeartBeat,
                    label = "심박수",
                    value = heartRateText
                )
                PotchStatusCard(
                    modifier = Modifier.weight(1f),
                    iconRes = AppIcons.PotchTemp,
                    label = "체온",
                    value = temperatureText
                )
                PotchStatusCard(
                    modifier = Modifier.weight(1f),
                    iconRes = AppIcons.PotchBattery,
                    label = "배터리",
                    value = batteryText
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(SkyBlue)
                .clickable {
                    if (isPotchConnected) {
                        potchViewModel.disconnect()
                    } else {
                        showConnectionSheet = true
                        selectedAddress = null
                        displayedDevices = emptyList()
                        permissionDenied = false

                        val missingPermissions = requiredPotchPermissions().filter { permission ->
                            ContextCompat.checkSelfPermission(context, permission) !=
                                    PackageManager.PERMISSION_GRANTED
                        }
                        if (missingPermissions.isEmpty()) {
                            potchViewModel.startDeviceDiscovery()
                        } else {
                            permissionLauncher.launch(missingPermissions.toTypedArray())
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isPotchConnected) "연결 해제" else "연결하기",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 16.sp,
                    color = Key
                )
            )
        }

        Spacer(Modifier.height(20.dp))
    }

    if (showConnectionSheet) {
        val selectionStatus = when {
            selectedAddress == null -> null
            bleState.isNotificationReady -> PotchSelectionStatus.COMPLETED
            bleState.lastError != null && !bleState.isConnecting ->
                PotchSelectionStatus.FAILED
            else -> PotchSelectionStatus.CONNECTING
        }

        fun cancelConnectionAttempt() {
            showConnectionSheet = false
            if (bleState.isNotificationReady) {
                selectedAddress = null
                displayedDevices = emptyList()
                return
            }

            if (selectedAddress != null || bleState.isConnecting ||
                bleState.isConnected || bleState.isReconnecting
            ) {
                potchViewModel.disconnect()
            } else {
                potchViewModel.cancelDeviceDiscovery()
            }
        }

        PotchConnectionSheet(
            devices = displayedDevices,
            selectedAddress = selectedAddress,
            status = selectionStatus,
            isSearchFailed = permissionDenied ||
                    (bleState.lastError != null && !bleState.isScanning),
            onSelect = { address ->
                selectedAddress = address
                potchViewModel.selectPotch(address)
            },
            onComplete = {
                showConnectionSheet = false
                selectedAddress = null
                displayedDevices = emptyList()
            },
            onDismiss = ::cancelConnectionAttempt
        )
    }
}

@Composable
private fun PotchStatusCard(
    @DrawableRes iconRes: Int,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(132.dp)
            .neumorphicBackground(
                highlightColor = Color(0xFFB9C8DF).copy(alpha = 0.1f)
            )
            .innerShadow(
                shape = RoundedCornerShape(30.dp),
                shadow = Shadow(
                    radius = 25.dp,
                    spread = (-12).dp,
                    color = Color(0xFF030E1E).copy(alpha = 0.8f),
                    offset = DpOffset(x = 5.dp, y = 6.dp)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = Color.Unspecified
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    color = White
                )
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 14.sp,
                    color = SkyBlue
                )
            )
        }
    }
}

private enum class PotchSelectionStatus {
    CONNECTING,
    COMPLETED,
    FAILED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PotchConnectionSheet(
    devices: List<DiscoveredPotch>,
    selectedAddress: String?,
    status: PotchSelectionStatus?,
    isSearchFailed: Boolean,
    onSelect: (String) -> Unit,
    onComplete: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val connectionCompleted = status == PotchSelectionStatus.COMPLETED
    val selectionLocked = status != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF07111E),
        scrimColor = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.62f)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "팟치 선택",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 16.sp,
                        color = White
                    )
                )

                status?.let {
                    Text(
                        text = when (it) {
                            PotchSelectionStatus.CONNECTING -> "연결 중..."
                            PotchSelectionStatus.COMPLETED -> "연결 완료!"
                            PotchSelectionStatus.FAILED -> "연결 실패"
                        },
                        modifier = Modifier.align(Alignment.CenterEnd),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = when (it) {
                                PotchSelectionStatus.COMPLETED -> SkyBlue
                                PotchSelectionStatus.FAILED -> Color(0xFFE05A5A)
                                PotchSelectionStatus.CONNECTING -> White
                            }
                        )
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            when {
                devices.isNotEmpty() -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 250.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(devices, key = { it.address }) { device ->
                            val isSelected = device.address == selectedAddress
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isSelected) Color(0xFF9CB4BE).copy(alpha = 0.45f)
                                        else Color.Transparent
                                    )
                                    .clickable(enabled = !selectionLocked) {
                                        onSelect(device.address)
                                    }
                                    .padding(horizontal = 6.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = device.name,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 13.sp,
                                        color = White
                                    )
                                )
                                Text(
                                    text = device.address.toPotchUniqueNumber(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        color = White.copy(alpha = 0.75f)
                                    )
                                )
                            }
                            HorizontalDivider(color = White.copy(alpha = 0.04f))
                        }
                    }
                }

                isSearchFailed -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "연결 실패",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 14.sp,
                                color = Color(0xFFE05A5A)
                            )
                        )
                    }
                }

                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "기기를 찾는 중...",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 14.sp,
                                color = White
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = onComplete,
                enabled = connectionCompleted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SkyBlue,
                    contentColor = Key,
                    disabledContainerColor = Color(0xFF91AAB5),
                    disabledContentColor = Key.copy(alpha = 0.7f)
                )
            ) {
                Text(
                    text = "완료",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp)
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

private fun String.toPotchUniqueNumber(): String =
    replace(":", "").takeLast(8).uppercase(Locale.getDefault())

private fun requiredPotchPermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}.toTypedArray()

private fun voltageToPotchBatteryPercent(voltage: Double): Int =
    (((voltage - 3.2) / (4.2 - 3.2)) * 100.0).roundToInt().coerceIn(0, 100)
