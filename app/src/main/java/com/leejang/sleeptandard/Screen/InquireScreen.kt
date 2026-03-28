package com.leejang.sleeptandard.Screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.leejang.sleeptandard.ui.theme.AppIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InquireScreen(
    onBack: () -> Unit = {},
    onSubmit: (title: String, body: String, imageUris: List<Uri>) -> Unit = { _, _, _ -> }
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    val fieldBg = Color(0x40F1F1F1)     // 아주 옅은 회색(알파)

    // 1. 선택된 이미지들의 URI 리스트를 저장할 상태로 변경
    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // 2. 여러 장을 가져올 수 있는 GetMultipleContents 계약 사용
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        // 새로 선택한 사진들을 기존 리스트에 추가 (교체를 원하면 selectedImageUris = uris)
        selectedImageUris = selectedImageUris + uris
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(AppIcons.QnAArrowBack),
                contentDescription = "뒤로가기",
                tint = Color.White
            )
        }

        Spacer(Modifier.weight(16f))

        // ---- 제목 ----
        Text(
            text = "제목",
            modifier = Modifier.padding(8.dp),
            color = Color(0xCCF1F1F1),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp)
        )
        Spacer(Modifier.weight(10f))

        OutlinedTextField(
            value = title,
            onValueChange = { if (it.length <= 30) title = it }, // ✅ 30자 제한
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            placeholder = {
                Text(
                    "30자 이내로 입력해주세요.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                lineHeight = 18.sp,
                color = Color.White
            ),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = fieldBg,
                unfocusedContainerColor = fieldBg,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                cursorColor = Color.White,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Spacer(Modifier.weight(22f))

        // ---- 내용 ----
        Text(
            "내용",
            color = Color(0xCCF1F1F1),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp)
        )
        Spacer(Modifier.weight(10f))

        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            placeholder = {
                Text(
                    "질문할 내용을 작성해주세요.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp
                    )
                )
            },
            singleLine = false,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = Color.White
            ),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = fieldBg,
                unfocusedContainerColor = fieldBg,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                cursorColor = Color.White,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Spacer(Modifier.weight(22f))

        // ---- 사진 첨부 ----
        Text(
            "사진 첨부",
            color = Color(0xCCF1F1F1),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp)
        )
        Spacer(Modifier.weight(10f))

        // 3. 사진 미리보기 및 추가 영역 (가로 스크롤 가능)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 선택된 이미지들 표시
            items(selectedImageUris) { uri ->
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    AsyncImage(
                        model = uri,
                        contentDescription = "선택된 이미지",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // (옵션) 여기에 삭제 버튼 'X'를 추가하면 더 좋습니다.
                }
            }

            // 4. 사진 추가 버튼 (항상 마지막에 위치)
            item {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(fieldBg, RoundedCornerShape(12.dp))
                        .clickable {
                            // 이미지 타입을 처리할 수 있는 앱 선택창을 띄웁니다.
                            photoPickerLauncher.launch("image/*")
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "+",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }
        }

        Spacer(Modifier.weight(60f))

        // ---- 제출 버튼
        Button(
            onClick = {
                // 5. 제출 시 리스트 전체를 전달
                onSubmit(title, body, selectedImageUris)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(100.dp),
            enabled = title.isNotBlank() && body.isNotBlank()
        ) {
            Text("제출", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 18.sp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InquireModalBottomSheet(
    onDismiss: () -> Unit = {},
    onSubmit: (title: String, body: String, imageUris: List<Uri>) -> Unit = { _, _, _ -> }
) {

    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    val fieldBg = Color(0x40F1F1F1)     // 아주 옅은 회색(알파)

    // 1. 선택된 이미지들의 URI 리스트를 저장할 상태로 변경
    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // 2. 여러 장을 가져올 수 있는 GetMultipleContents 계약 사용
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        // 새로 선택한 사진들을 기존 리스트에 추가 (교체를 원하면 selectedImageUris = uris)
        selectedImageUris = selectedImageUris + uris
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // ✅ 1. 기본 배경과 그림자를 완전히 제거합니다.
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = null // 핸들을 수동으로 그리거나 제거하여 더 깔끔하게 구성
    ) {

        // ✅ 2. 실제 글래스 재질을 담당하는 컨테이너
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 1 .dp) // 시트 양옆에 살짝 여백을 주면 더 입체적입니다
                // 배경: 반투명한 어두운 블루 톤
                .background(
                    color = Color(0x991A1C29),
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                )
                // 테두리: 얇은 흰색 투명 선으로 '유리 가장자리' 효과
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.3f), Color.Transparent)
                    ),
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding() // 키보드 대응
            ) {
                // 상단 드래그 핸들 (커스텀)
                Box(
                    Modifier
                        .size(40.dp, 4.dp)
                        .background(Color.White.copy(0.2f), RoundedCornerShape(100.dp))
                        .align(Alignment.CenterHorizontally)
                )

                Spacer(Modifier.height(24.dp))

                Text(
                    text = "제목",
                    modifier = Modifier.padding(8.dp),
                    color = Color(0xCCF1F1F1),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp)
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 20) title = it }, // ✅ 30자 제한
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    placeholder = {
                        Text(
                            "20자 이내로 입력해주세요.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        color = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = fieldBg,
                        unfocusedContainerColor = fieldBg,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = Color.White,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(Modifier.height(38.dp))

                // ---- 내용 ----
                Text(
                    "내용",
                    color = Color(0xCCF1F1F1),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp)
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(136.dp),
                    placeholder = {
                        Text(
                            "질문할 내용을 작성해주세요.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp
                            )
                        )
                    },
                    singleLine = false,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = fieldBg,
                        unfocusedContainerColor = fieldBg,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = Color.White,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(Modifier.height(38.dp))

                // ---- 사진 첨부 ----
                Text(
                    "사진 첨부",
                    color = Color(0xCCF1F1F1),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp)
                )
                Spacer(Modifier.height(10.dp))

                // 3. 사진 미리보기 및 추가 영역 (가로 스크롤 가능)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 선택된 이미지들 표시
                    items(selectedImageUris) { uri ->
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            AsyncImage(
                                model = uri,
                                contentDescription = "선택된 이미지",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            // (옵션) 여기에 삭제 버튼 'X'를 추가하면 더 좋습니다.
                        }
                    }

                    // 4. 사진 추가 버튼 (항상 마지막에 위치)
                    item {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .background(fieldBg, RoundedCornerShape(12.dp))
                                .clickable {
                                    // 이미지 타입을 처리할 수 있는 앱 선택창을 띄웁니다.
                                    photoPickerLauncher.launch("image/*")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "+",
                                color = Color.White,
                                style = MaterialTheme.typography.headlineMedium
                            )
                        }
                    }
                }

                Spacer(Modifier.height(40.dp))

                // ---- 제출 버튼
                Button(
                    onClick = {
                        // 5. 제출 시 리스트 전체를 전달
                        // onSubmit(title, body, selectedImageUris)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(100.dp),
                    enabled = title.isNotBlank() && body.isNotBlank()
                ) {
                    Text("제출", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 18.sp))
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
