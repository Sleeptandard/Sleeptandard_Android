package com.leejang.sleeptandard.Screen

import android.widget.Toast
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush.Companion.linearGradient
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.leejang.sleeptandard.ClassFile.User
import com.leejang.sleeptandard.Component.AuthStepIndicator
import com.leejang.sleeptandard.Component.BirthDatePicker
import com.leejang.sleeptandard.Component.GenderRadioButton
import com.leejang.sleeptandard.ViewModel.AuthViewModel
import com.leejang.sleeptandard.ui.theme.AppIcons


// 유저의 로그인/회원가입 진행 단계 정의 및 email + 비번 저장 클래스
sealed class AuthStep {
    object EmailInput : AuthStep()                          // 1단계: 이메일 입력
    data class LoginPassword(val email: String) : AuthStep() // 2단계(경로A): 로그인 비밀번호
    data class FindPassword(val email: String) : AuthStep() // 비밀번호 찾기
    data class PasswordReset(val email: String) : AuthStep()    // 비밀번호 재설정
    data class SignupPassword(val email: String) : AuthStep() // 2단계(경로B): 회원가입 비밀번호
    data class SignupNickname(val email: String, val pw: String) : AuthStep() // 3단계: 닉네임

    data class SignupGenderBirth(val email: String, val pw: String, val nickname: String) : AuthStep() // 4단계: 성별 + 생년월일
    data class Completed(val nickname: String) : AuthStep()
}


@Composable
fun LoginDemoScreen(
    authViewModel: AuthViewModel,
    onConfirm: (User) -> Unit
){
    val context = LocalContext.current

    val linearGradation = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color(0xFF050C16),
            1f to Color(0xFF1C447C)
        )
    )

    val barGradient = linearGradient(
        listOf(Color(0xFF437AC7),
            Color(0xFFAFF4F9))
    )

    val backgroundColor = Color.White

    // 리퀴드 글래스 드가자
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }

    val barVisibility = when(authViewModel.currentStep){

        is AuthStep.EmailInput -> true
        is AuthStep.FindPassword -> true
        is AuthStep.LoginPassword -> true
        is AuthStep.SignupGenderBirth -> true
        is AuthStep.SignupNickname -> true
        is AuthStep.SignupPassword -> true

        /****** 인디케이터 레일 안보임 ******/
        is AuthStep.Completed -> false
        is AuthStep.PasswordReset -> false
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .layerBackdrop(backdrop)
    ){
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = linearGradation)
                .padding(horizontal = 20.dp)
            ,
            horizontalAlignment = Alignment.CenterHorizontally
        ){
            Spacer(Modifier.height(50.dp))

            if(barVisibility){
                // Rail
                Box(
                    modifier = Modifier
                        .height(52.dp)
                        .padding(top = 9.dp),
                    contentAlignment = Alignment.Center
                ){
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(brush = barGradient, shape = RoundedCornerShape(10.dp))
                    )
                }
            }else{
                Spacer(Modifier.height(52.dp))
            }


            Spacer(Modifier.height(6.dp))

            // 현재 스텝에 따른 UI 출력 with animation
            AnimatedContent(
                targetState = authViewModel.currentStep,
                transitionSpec = {
                    fadeIn(animationSpec = tween(400)).togetherWith(fadeOut(animationSpec = tween(400)))
                },
                label = "AuthStepTransition"
            ) { step ->
                when (step) {

                    is AuthStep.EmailInput -> EmailInputStep(
                        viewModel = authViewModel,

                        // TODO: 이메일 탐색 백엔드 통신
                        // AuthViewModel의 이메일 탐색 더미 로직
                        onEmailCheck = {authViewModel.checkEmail()},
                        )

                    is AuthStep.LoginPassword -> LoginPasswordStep(
                        viewModel = authViewModel,
                        onLoginSend = {
                            // TODO: 백엔드 로그인 처리
                            // AuthViewModel의 로그인 처리 더미 로직
                            authViewModel.performLogin(
                                onSuccess = {onConfirm(it)},
                                onError = {Toast.makeText(context, "비밀번호가 틀렸습니다.", Toast.LENGTH_SHORT).show()}
                            )
                        },
                        onPwChange = {
                            // TODO: 비밀번호 재설정 메일 송신 로직
                            authViewModel.findingPassword()
                        },
                        onBack = {authViewModel.backToEmail()}
                    )

                    is AuthStep.FindPassword -> PasswordChangeStep(
                        viewModel = authViewModel,
                        onBackToEmail = {
                            /** 확인버튼 필요 없지 않음? **/
                            // 일단 재설정 창 확인용으로만 존재
                            // authViewModel.resetPassword()
                            authViewModel.backToEmail()
                        },
                        onResend = {
                            // TODO: 비밀번호 재설정 메일 송신 로직
                        },
                        onBack = {
                            authViewModel.backToLoginPassword()
                        }
                    )

                    is AuthStep.PasswordReset -> PasswordResetStep(
                        viewModel = authViewModel,
                        onReset = {
                            // TODO: 비밀번호 재설정 로직 백엔드 연결 필요
                            authViewModel.backToEmail()
                        }
                    )

                    is AuthStep.SignupPassword -> SignupPasswordStep(
                        viewModel = authViewModel,
                        onSubmit = {
                            // 다음 단계(닉네임 설정단계)로 진행
                            authViewModel.setSignupPassword()
                        },
                        onBack = {
                            authViewModel.backToEmail()
                        }
                        )

                    is AuthStep.SignupNickname -> NicknameStep(
                        viewModel = authViewModel,
                        onSubmit = {
                            // 다음 단계(성별,생년월일 설정단계)로 진행
                            authViewModel.setSignupGenderBirth()
                        },
                        onBack = {
                            authViewModel.backToSignupPassword()
                        }
                    )

                    is AuthStep.SignupGenderBirth -> GenderBirthStep(
                        viewModel = authViewModel,
                        onSubmit = {
                            // TODO: 백엔드 회원가입 처리 로직
                            // AuthViewModel의 최종 회원가입 처리 더미 로직
                            authViewModel.completeSignup { nickname ->
                                Log.d("Signup", "$nickname 가입 완료")
                            }
                        },
                        onBack = {
                            authViewModel.backToNickname()
                        }
                    )

                    is AuthStep.Completed -> CompletedStep(
                        viewModel = authViewModel,
                        onTermsOfUse = {},
                        onPrivatePolicy = {},
                        onConfirm = {
                            // 회원가입을 마치고 홈 화면으로 진입
                            onConfirm(it)
                        }
                    )
                }
            }

            Spacer(Modifier.weight(1f))
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        Spacer(Modifier.height(50.dp))

        AuthStepIndicator(
            modifier = Modifier,
            currentStep = authViewModel.currentStep,
            backdrop = backdrop,
        )
    }

}

@Composable
fun WhiteTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    isEmailInput: Boolean = false,
    isPasswordInput: Boolean = false,
    isNicknameInput: Boolean = false,
    isNicknameValid: Boolean = true
) {
    // 1. 비밀번호 가리기/보이기 상태 관리
    var passwordVisible by remember { mutableStateOf(false) }

    // 2. 현재 필드 타입에 따른 최종 VisualTransformation 결정
    val actualTransformation = if (isPasswordInput) {
        if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation()
    } else {
        visualTransformation
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(color = Color.Black, fontSize = 16.sp),
        visualTransformation = actualTransformation,
        singleLine = true, // ✅ 한 줄 입력만 허용 (엔터 키로 줄바꿈 방지)
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Next, // 엔터 키를 '다음' 버튼으로 변경
            keyboardType = if (isEmailInput) KeyboardType.Email else KeyboardType.Unspecified// 이메일 전용 키보드 레이아웃 제공
        ),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(Color.White, RoundedCornerShape(30.dp))
                ,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 텍스트 영역
                Box(modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
                    .padding(start = 24.dp),
                    contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFF050C16).copy(0.3f),
                                fontSize = 16.sp
                            )
                        )
                    }
                    innerTextField()
                }

                if(isPasswordInput){
                    // ✅ 아이콘 영역 (오른쪽 배치)
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {

                        // 기능 1: 비밀번호 가리기/보이기 (비밀번호 필드일 때만 표시)
                        if (value.isNotEmpty()) {
                            Icon(
                                painter = if (passwordVisible) painterResource(AppIcons.RegisterInvisible) else painterResource(AppIcons.RegisterVisible),
                                contentDescription = "toggle password visibility",
                                tint = Color.Black,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable { passwordVisible = !passwordVisible }
                            )
                        }

                        // 기능 2: 전체 지우기 (X 버튼, 값이 있을 때만 표시)
                        if (value.isNotEmpty()) {
                            Image(
                                painter = painterResource(AppIcons.RegisterCancel), // 원형 X 아이콘
                                contentDescription = "clear text",
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable { onValueChange("") } // ✅ 클릭 시 빈 문자열로 초기화
                            )
                        }
                    }
                }
                else if(isNicknameInput && value.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 20.dp),
                    ) {
                        // 기능 1: 비밀번호 가리기/보이기 (비밀번호 필드일 때만 표시)
                        Image(
                            painter = if (isNicknameValid) painterResource(AppIcons.RegisterOK) else painterResource(
                                AppIcons.RegisterWarning
                            ),
                            contentDescription = "toggle password visibility",
                            modifier = Modifier
                                .size(24.dp)
                        )
                    }
                }
            }
        }
    )

}

// 이메일 입력 화면
@Composable
fun EmailInputStep(
    viewModel: AuthViewModel,
    onEmailCheck: () -> Unit,
) {

    val buttonGradient = linearGradient(
        listOf(Color(0xFF437AC7),
            Color(0xFFAFF4F9))
    )

    Column(modifier = Modifier
        .fillMaxSize()
    ){
        Spacer(Modifier.height(92.dp))

        Text(
            modifier = Modifier.padding(start = 10.dp),
            text = "이메일을 입력해주세요", //
            style = MaterialTheme.typography.bodyLarge.copy(
                color = Color.White,
                fontSize = 22.sp
            )

        )

        Spacer(Modifier.height(10.dp))

        Text(
            modifier = Modifier.padding(start = 10.dp),
            text = "이메일에 따라 가입 또는 로그인으로 진행됩니다.",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )

        )

        Spacer(Modifier.height(60.dp))

        WhiteTextField(
            value = viewModel.email,
            onValueChange = { viewModel.updateEmail(it) },
            placeholder = "이메일",
            isEmailInput = true
        )

        Spacer(Modifier.height(60.dp)) // 버튼을 하단으로 밀어냄


        Button(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp)),
            enabled = viewModel.isEmailValid,
            onClick = {
                onEmailCheck()
            },
            contentPadding = PaddingValues(0.dp)
        ){
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ){
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(brush = buttonGradient)
                        .blur(30.dp)
                        .border(
                            width = 2.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.4f), // 테두리 위쪽 (빛남)
                                    Color.Transparent,             // 테두리 중간 (투명)
                                    Color.White.copy(alpha = 0.1f)  // 테두리 아래쪽 (은은함)
                                )
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ),
                    contentAlignment = Alignment.Center
                ){

                }

                Text(
                    text = "제출",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 18.sp,

                        color =
                            if(viewModel.isEmailValid) {
                                Color.White
                            }
                        else
                            Color.Black.copy(alpha = 0.5f)
                    )
                )
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

// 로그인 비밀번호 입력 화면
@Composable
fun LoginPasswordStep(
    viewModel: AuthViewModel,
    onLoginSend: () -> Unit,
    //onLoginSuccess: (User) -> Unit,
    //onLoginError: () -> Unit,
    onPwChange: () -> Unit,
    onBack: () -> Unit,
) {
    val buttonGradient = linearGradient(
        listOf(Color(0xFF437AC7),
            Color(0xFFAFF4F9))
    )


    Column(modifier = Modifier
        .fillMaxSize()
        ) {

        Column(
            modifier = Modifier
                .height(92.dp)
                .fillMaxWidth()

        ){
            IconButton(
                modifier = Modifier
                    .size(32.dp),
                onClick = onBack
            ) {
                Icon(
                    modifier = Modifier.size(32.dp),
                    painter = painterResource(AppIcons.QnAArrowBack),
                    contentDescription = "뒤로 가기"
                )
            }
        }

        Text(
            modifier = Modifier.padding(start = 10.dp),
            text = "비밀번호를 입력해주세요 ", //
            style = MaterialTheme.typography.bodyLarge.copy(
                color = Color.White,
                fontSize = 22.sp
            )
        )
        Spacer(Modifier.height(40.dp))

        WhiteTextField(
            value = viewModel.password,
            onValueChange = { viewModel.updatePassword(it) },
            placeholder = "8자리 이상 입력해주세요",
            // visualTransformation = PasswordVisualTransformation(),
            isPasswordInput = true
        )

        Column(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ){
            // 비밀번호 찾기
            Text(
                text = "비밀번호 찾기",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .clickable {
                        onPwChange()
                    },
                textAlign = TextAlign.Center,
                color = Color.White,
                textDecoration = TextDecoration.Underline,
                fontSize = 14.sp
            )
        }


        Spacer(Modifier.height(16.dp))

        Button(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp)),
            enabled = (viewModel.isPasswordValid),
            onClick = {
                onLoginSend()
            },
            contentPadding = PaddingValues(0.dp)
        ){
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ){
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(brush = buttonGradient)
                        .blur(30.dp)
                        .border(
                            width = 2.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.4f), // 테두리 위쪽 (빛남)
                                    Color.Transparent,             // 테두리 중간 (투명)
                                    Color.White.copy(alpha = 0.1f)  // 테두리 아래쪽 (은은함)
                                )
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ),
                    contentAlignment = Alignment.Center
                ){

                }

                Text(
                    text = "확인",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 18.sp,

                        color =
                            if(viewModel.isPasswordValid) {
                                Color.White
                            }
                            else
                                Color.Black.copy(alpha = 0.5f)
                    )
                )
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun PasswordChangeStep(
    viewModel: AuthViewModel,
    onBackToEmail: () -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit
) {

    val buttonGradient = linearGradient(
        listOf(Color(0xFF437AC7),
            Color(0xFFAFF4F9))
    )

    Column(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                modifier = Modifier
                    .size(32.dp),
                onClick = onBack
            ) {
                Icon(
                    modifier = Modifier.size(32.dp),
                    painter = painterResource(AppIcons.QnAArrowBack),
                    contentDescription = "뒤로 가기"
                )
            }
        }


        Spacer(Modifier.height(42.dp))

        Icon(
            modifier = Modifier.size(120.dp),
            painter = painterResource(AppIcons.RegisterMail),
            contentDescription = "메일 발송 아이콘"
        )

        Spacer(Modifier.height(36.dp))

        Text(
            text = viewModel.email,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "비밀번호 재설정 메일이 발송됐어요",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 22.sp,
                color = Color.White
            )
        )

        Spacer(Modifier.height(40.dp))

        Button(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp)),
            onClick = {
                onBackToEmail()
            },
            contentPadding = PaddingValues(0.dp)
        ){
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ){
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(brush = buttonGradient)
                        .blur(30.dp)
                        .border(
                            width = 2.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.4f), // 테두리 위쪽 (빛남)
                                    Color.Transparent,             // 테두리 중간 (투명)
                                    Color.White.copy(alpha = 0.1f)  // 테두리 아래쪽 (은은함)
                                )
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ),
                    contentAlignment = Alignment.Center
                ){

                }

                Text(
                    text = "로그인 화면으로",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 18.sp,
                        color = Color.White
                    )
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            modifier = Modifier.clickable {
                onResend()
            },
            text = "재발송",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = Color.White,
                fontSize = 14.sp
            ),
            textDecoration = TextDecoration.Underline
        )

    }
}

@Composable
fun PasswordResetStep(
    viewModel: AuthViewModel,
    onReset: () -> Unit
) {
    val buttonGradient = linearGradient(
        listOf(Color(0xFF437AC7),
            Color(0xFFAFF4F9))
    )

    val pwInvalidMessage = if(!viewModel.isPasswordCharsValid) "영어, 숫자, 특수기호(@,\$,!,%,*,?,&)만 가능합니다"
    else if(!viewModel.isPasswordLengthValid) "8자리 이상 입력해주세요"
    else "비밀번호를 다시 확인해주세요"

    Column(
        modifier = Modifier
            .fillMaxWidth()
    ){
        Text(
            modifier = Modifier.padding(10.dp),
            text = "비밀번호 재설정",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 22.sp,
                color = Color.White
            )
        )

        WhiteTextField(
            value = viewModel.password,
            onValueChange = { viewModel.updatePassword(it) },
            placeholder = "8자리 이상 입력해주세요", //
            visualTransformation = PasswordVisualTransformation(),
            isPasswordInput = true
        )

        Spacer(Modifier.height(16.dp))

        WhiteTextField(
            value = viewModel.passwordConfirm,
            onValueChange = { viewModel.updatePasswordConfirm(it) },
            placeholder = "비밀번호 확인", //
            //visualTransformation = PasswordVisualTransformation(),
            isPasswordInput = true
        )

        if((viewModel.password.isNotEmpty() && !viewModel.isPasswordValid) || ((viewModel.password != viewModel.passwordConfirm) && viewModel.passwordConfirm.isNotEmpty())) {

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(AppIcons.RegisterWarning),
                    contentDescription = "비밀번호 경고"
                )
                Text(
                    modifier = Modifier.padding(start = 6.dp),
                    text = pwInvalidMessage,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        color = Color(0xFFEF4444)
                    ),
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
            Spacer(Modifier.height(12.dp))
        }else {
            Spacer(Modifier.height(52.dp)) // 12.dp
        }

        Button(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp)),
            enabled = (viewModel.password.length >= 8) && (viewModel.password == viewModel.passwordConfirm),
            onClick = onReset,
            contentPadding = PaddingValues(0.dp)
        ){
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ){
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(brush = buttonGradient)
                        .blur(30.dp)
                        .border(
                            width = 2.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.4f), // 테두리 위쪽 (빛남)
                                    Color.Transparent,             // 테두리 중간 (투명)
                                    Color.White.copy(alpha = 0.1f)  // 테두리 아래쪽 (은은함)
                                )
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ),
                    contentAlignment = Alignment.Center
                ){

                }

                Text(
                    text = "변경하기",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 18.sp,

                        color =
                            if((viewModel.isPasswordValid) && (viewModel.password == viewModel.passwordConfirm)) {
                                Color.White
                            }
                            else
                                Color.Black.copy(alpha = 0.5f)
                    )
                )
            }
        }

    }
}

@Composable
fun SignupPasswordStep(
    viewModel: AuthViewModel,
    onSubmit: () -> Unit,
    onBack: () -> Unit
) {

    val buttonGradient = linearGradient(
        listOf(Color(0xFF437AC7),
            Color(0xFFAFF4F9))
    )

    val pwInvalidMessage = if(!viewModel.isPasswordCharsValid) "영어, 숫자, 특수기호(@,\$,!,%,*,?,&)만 가능합니다"
        else if(!viewModel.isPasswordLengthValid) "8자리 이상 입력해주세요"
    else "비밀번호를 다시 확인해주세요"



    Column(modifier = Modifier
        .fillMaxSize()
        ) {

        Column(
            modifier = Modifier
                .height(92.dp)
                .fillMaxWidth()

        ){
            IconButton(
                modifier = Modifier
                    .size(32.dp),
                onClick = onBack
            ) {
                Icon(
                    modifier = Modifier.size(32.dp),
                    painter = painterResource(AppIcons.QnAArrowBack),
                    contentDescription = "뒤로 가기"
                )
            }
        }

        Text(
            text = "비밀번호를 설정해주세요", //
            style = MaterialTheme.typography.bodyLarge.copy(
                color = Color.White,
                fontSize = 22.sp
            )
        )
        Spacer(Modifier.height(40.dp))

        WhiteTextField(
            value = viewModel.password,
            onValueChange = { viewModel.updatePassword(it) },
            placeholder = "8자리 이상 입력해주세요", //
            visualTransformation = PasswordVisualTransformation(),
            isPasswordInput = true
        )

        Spacer(Modifier.height(16.dp))

        WhiteTextField(
            value = viewModel.passwordConfirm,
            onValueChange = { viewModel.updatePasswordConfirm(it) },
            placeholder = "비밀번호 확인", //
            //visualTransformation = PasswordVisualTransformation(),
            isPasswordInput = true
        )
        val isWrong = (viewModel.password.isNotEmpty() && !viewModel.isPasswordValid) || ((viewModel.password != viewModel.passwordConfirm) && viewModel.passwordConfirm.isNotEmpty())
        if(isWrong) {

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(AppIcons.RegisterWarning),
                    contentDescription = "비밀번호 경고"
                )
                Text(
                    modifier = Modifier.padding(start = 6.dp),
                    text = pwInvalidMessage,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        color = Color(0xFFEF4444)
                    ),
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
            Spacer(Modifier.height(12.dp))
        }else {
            Spacer(Modifier.height(52.dp)) // 12.dp
        }

        val isOk = viewModel.isPasswordValid && (viewModel.password == viewModel.passwordConfirm)

        Button(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp)),
            enabled = isOk,
            onClick = onSubmit,
            contentPadding = PaddingValues(0.dp)
        ){
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ){
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(brush = buttonGradient)
                        .blur(30.dp)
                        .border(
                            width = 2.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.4f), // 테두리 위쪽 (빛남)
                                    Color.Transparent,             // 테두리 중간 (투명)
                                    Color.White.copy(alpha = 0.1f)  // 테두리 아래쪽 (은은함)
                                )
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ),
                    contentAlignment = Alignment.Center
                ){

                }

                Text(
                    text = "확인",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 18.sp,

                        color =
                            if(isOk) {
                                Color.White
                            }
                            else
                                Color.Black.copy(alpha = 0.5f)
                    )
                )
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun NicknameStep(
    viewModel: AuthViewModel,
    onSubmit: () -> Unit,
    onBack: () -> Unit
) {

    val buttonGradient = linearGradient(
        listOf(Color(0xFF437AC7),
            Color(0xFFAFF4F9))
    )

    val nicknameInvalidMessage = if(viewModel.nickname.length > 15)
        "15자 이하로 작성해주세요"
    else "특수문자는 들어갈 수 없어요"

    Column(modifier = Modifier
        .fillMaxSize()
        ) {
        Column(
            modifier = Modifier
                .height(92.dp)
                .fillMaxWidth()

        ){
            IconButton(
                modifier = Modifier
                    .size(32.dp),
                onClick = onBack
            ) {
                Icon(
                    modifier = Modifier.size(32.dp),
                    painter = painterResource(AppIcons.QnAArrowBack),
                    contentDescription = "뒤로 가기"
                )
            }
        }
        Text(
            text = "닉네임을 설정해주세요", //
            style = MaterialTheme.typography.bodyLarge.copy(
                color = Color.White,
                fontSize = 22.sp
            )
        )

        Spacer(Modifier.height(40.dp))

        WhiteTextField(
            value = viewModel.nickname,
            onValueChange = { viewModel.updateNickname(it) },
            placeholder = "ex) 노곤노곤한 카피바라", //
            isNicknameInput = true,
            isNicknameValid = viewModel.isNicknameValid,
        )

        if(!viewModel.isNicknameValid && viewModel.nickname.isNotBlank()){

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(AppIcons.RegisterWarning),
                    contentDescription = "닉네임 경고"
                )
                Text(
                    modifier = Modifier.padding(start = 6.dp),
                    text = nicknameInvalidMessage,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        color = Color(0xFFEF4444)
                    ),
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
            Spacer(Modifier.height(12.dp))

        } else{
            Spacer(Modifier.height(60.dp))
        }


        Button(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp)),
            enabled =  viewModel.nickname.isNotBlank() && viewModel.isNicknameValid , // 간단한 유효성 검사
            onClick = onSubmit,
            contentPadding = PaddingValues(0.dp)
        ){
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ){
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(brush = buttonGradient)
                        .blur(30.dp)
                        .border(
                            width = 2.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.4f), // 테두리 위쪽 (빛남)
                                    Color.Transparent,             // 테두리 중간 (투명)
                                    Color.White.copy(alpha = 0.1f)  // 테두리 아래쪽 (은은함)
                                )
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ),
                    contentAlignment = Alignment.Center
                ){

                }

                Text(
                    text = "확인",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 18.sp,

                        color =
                            if( viewModel.nickname.isNotBlank() && viewModel.isNicknameValid) {
                                Color.White
                            }
                            else
                                Color.Black.copy(alpha = 0.5f)
                    )
                )
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun GenderBirthStep(
    viewModel: AuthViewModel,
    onSubmit: () -> Unit,
    onBack: () -> Unit
    ) {
    val buttonGradient = linearGradient(
        listOf(Color(0xFF437AC7), Color(0xFFAFF4F9))
    )

    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        Column(
            modifier = Modifier
                .height(92.dp)
                .fillMaxWidth()

        ){
            IconButton(
                modifier = Modifier
                    .size(32.dp),
                onClick = onBack
            ) {
                Icon(
                    modifier = Modifier.size(32.dp),
                    painter = painterResource(AppIcons.QnAArrowBack),
                    contentDescription = "뒤로 가기"
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp),
        ) {
            // 타이틀
            Text(
                text = "성별과 나이를 알려주세요",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = Color.White, fontSize = 22.sp)
            )
            Text(
                text = "개인 맞춤 수면 분석에 사용돼요",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
            )
            Spacer(Modifier.height(40.dp))

            // 성별 선택 섹션
            Text(
                text = "성별",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White, fontSize = 16.sp)
            )

            Spacer(Modifier.height(12.dp))

            val radioOptions = listOf("남", "여", "선택안함")
            val (selectedOption, onOptionSelected) = remember { mutableStateOf("") }
            Row(
                modifier = Modifier.selectableGroup()) {
                radioOptions.forEach { text ->
                    Row(
                        Modifier
                            .height(44.dp)
                            .selectable(
                                selected = (text == selectedOption),
                                onClick = {
                                    onOptionSelected(text)
                                    viewModel.updateGender(text)
                                },
                                role = Role.RadioButton
                            )
                            .padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GenderRadioButton(
                            selected = (text == selectedOption),
                            onClick = null
                        )
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }


                }
            }

            Spacer(Modifier.height(40.dp))

            // 생년월일 입력 섹션
            Text(
                text = "생년월일",
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontSize = 16.sp)
            )
        }


        Spacer(Modifier.height(12.dp))

        // ✅ 1. 모달 표시 상태가 true일 때만 다이얼로그를 띄웁니다.
        if (viewModel.showDatePickerModal) {
            Dialog(
                onDismissRequest = { viewModel.closeDatePicker() } // 다이얼로그 바깥 터치 시 닫기
            ) {
                BirthDatePicker(viewModel = viewModel)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(Color.White)
                .clickable {
                    viewModel.openDatePicker() // ✅ 클릭 시 모달 오픈 신호 전달
                },
            verticalArrangement = Arrangement.Center,
        ){
            if(viewModel.birthdate.isEmpty()){
                Text(
                    modifier = Modifier.padding(start = 20.dp),
                    text = "YYYY / MM / DD",
                    style =  MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF050C16).copy(alpha = 0.7f), fontSize = 16.sp
                    )
                )
            }
            else{
                Text(
                    modifier = Modifier.padding(start = 20.dp),
                    text = viewModel.birthdate,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.Black, fontSize = 16.sp
                    )
                )
            }
        }

        Spacer(Modifier.height(40.dp))

        // 최종 확인 버튼 (성별 선택 & 비번 10자리 형식일 때만 활성화)
        val isEnabled = viewModel.gender.isNotEmpty() && viewModel.birthdate.length == 10

        Button(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp)),
            enabled =  isEnabled, // 간단한 유효성 검사
            onClick = onSubmit,
            contentPadding = PaddingValues(0.dp)
        ){
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ){
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(brush = buttonGradient)
                        .blur(30.dp)
                        .border(
                            width = 2.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.4f), // 테두리 위쪽 (빛남)
                                    Color.Transparent,             // 테두리 중간 (투명)
                                    Color.White.copy(alpha = 0.1f)  // 테두리 아래쪽 (은은함)
                                )
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ),
                    contentAlignment = Alignment.Center
                ){

                }

                Text(
                    text = "확인",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 18.sp,

                        color =
                            if(isEnabled) {
                                Color.White
                            }
                            else
                                Color.Black.copy(alpha = 0.5f)
                    )
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun CompletedStep(
    viewModel: AuthViewModel,
    onTermsOfUse: ()->Unit,
    onPrivatePolicy: ()->Unit,
    onConfirm: (User) -> Unit
){
    val buttonGradient = linearGradient(
        listOf(Color(0xFF437AC7),
            Color(0xFFAFF4F9))
    )

    Column(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    )
    {
        Icon(
            painter = painterResource(AppIcons.RegisterLogo),
            contentDescription = "로고",
            modifier = Modifier.size(225.dp,64.dp),
            tint = Color(0xFFAFF4F9)
        )

        Spacer(Modifier.height(100.dp))

        Image(
            painter = painterResource(AppIcons.RegisterClock),
            contentDescription = "시계 그림",
            modifier = Modifier.size(200.dp,180.dp),
        )

        Text(
            text = "환영합니다, ${viewModel.nickname}님!",
            style = MaterialTheme.typography.bodyLarge.copy(
                color = Color.White,
                fontSize = 28.sp
            )
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = "이제 더 똑똑한 알람을 경험해보세요",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = Color.White,
                fontSize = 18.sp
            )
        )

        Spacer(Modifier.height(90.dp))

        Text(
            text = "알람 설정하기를 탭함으로써 알람의 정석",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = Color.White,
                fontSize = 14.sp
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ){
            Text(
                modifier = Modifier
                    .clickable{
                        onTermsOfUse()
                    },
                text = "이용약관",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White,
                    fontSize = 14.sp
                ),
                textDecoration = TextDecoration.Underline
            )
            Text(
                text = ",",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White,
                    fontSize = 14.sp
                )
            )
            Text(
                modifier = Modifier
                    .clickable{
                        onPrivatePolicy()
                    },
                text = "개인정보처리방침",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White,
                    fontSize = 14.sp
                ),
                textDecoration = TextDecoration.Underline
            )
            Text(
                text = "에 동의합니다",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White,
                    fontSize = 14.sp
                )
            )
        }

        Spacer(Modifier.height(16.dp))


        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .fillMaxWidth()
                .clickable { onConfirm(viewModel.loadUserInfo()) },
            contentAlignment = Alignment.Center
        ){
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(brush = buttonGradient)
                    .blur(30.dp)
                    .border(
                        width = 2.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.4f), // 테두리 위쪽 (빛남)
                                Color.Transparent,             // 테두리 중간 (투명)
                                Color.White.copy(alpha = 0.1f)  // 테두리 아래쪽 (은은함)
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ){

            }

            Text(
                text = "알람 설정하기",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 18.sp,
                    color = Color.White
                )
            )
        }

    }
    
}
