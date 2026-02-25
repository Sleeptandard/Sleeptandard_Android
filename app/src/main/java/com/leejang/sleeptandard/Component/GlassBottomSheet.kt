package com.leejang.sleeptandard.Component

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider

// 안드로이드 12(S) 이상에서만 Window Blur가 지원됨을 명시
@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    content: @Composable ColumnScope.() -> Unit
) {
    // 1. 기본 시트의 배경색을 완전 투명하게 날려버립니다.
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color(0xFF4D5999).copy(0.15f), // 핵심: 컨테이너 투명화
        dragHandle = null, // 드래그 핸들도 커스텀 디자인을 위해 제거 (원하면 추가)
        // 2. 스크림(뒷배경 어두워지는 것)
        scrimColor = Color.Black.copy(alpha = 0.55f)
    ) {
        // 3. 현재 다이얼로그의 Window 객체를 찾아 블러 효과를 적용합니다.
        val view = LocalView.current
        SideEffect {
            applyWindowBlurEffect(view)
        }


        // 4. 실제 글래스모피즘 디자인이 적용된 시트 본체
        GlassSheetContent {
            content()
        }
    }
}

// 실제로 뒤 화면을 흐리게 만드는 시스템 명령 함수
private fun applyWindowBlurEffect(view: View) {
    // 뷰의 부모가 다이얼로그 윈도우인지 확인
    val parent = view.parent
    if (parent is View && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val windowProvider = parent as? DialogWindowProvider
        val window: Window? = windowProvider?.window

        window?.let {
            // FLAG_BLUR_BEHIND: "내 뒤를 흐리게 하라"는 플래그 설정
            it.setFlags(
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            )
            // 블러 강도 설정 (높을수록 많이 흐려짐)
            it.attributes.blurBehindRadius = 30 // 피그마 느낌 나게 강하게!
            // 배경 어두움 정도 설정 (0.0f ~ 1.0f)
            it.setDimAmount(0.15f)
            it.attributes = it.attributes
        }
    }
}

// 시트 자체의 "서리 낀 유리" 질감 디자인
@Composable
fun GlassSheetContent(content: @Composable ColumnScope.() -> Unit) {
    val glassShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(glassShape)
            // [유리 질감 레이어 1] 반투명한 그라데이션 배경
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.2f), // 상단: 약간 밝음
                        Color.Transparent,
                        Color.White.copy(alpha = 0.05f) // 하단: 거의 투명
                    )
                )
            )
            // [유리 질감 레이어 2] 빛나는 테두리 (Rim Light)
            /*
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.4f), // 상단 테두리 빛남
                        Color.Transparent // 하단 테두리는 안 보임
                    )
                ),
                shape = glassShape
            )

             */
            .border(
                width = 1.dp,
                // 피그마의 inset 0.08 효과를 대신하는 빛나는 테두리
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.2f), // 왼쪽 위 쨍한 빛
                        Color.Transparent,             // 중간 투명
                        Color.White.copy(alpha = 0.1f)  // 오른쪽 아래 은은한 반사광
                    )
                ),
                shape = glassShape
            )
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp) // 내부 콘텐츠 여백
        ) {
            // 드래그 핸들바 대신 사용할 작은 디자인 요소
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally

            ){
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .width(126.dp)
                        .height(4.dp)
                        .background(Color(0xFF5F646A))
                    ,

                    )
                Spacer(Modifier.height(16.dp))
            }
            content()
        }
    }
}