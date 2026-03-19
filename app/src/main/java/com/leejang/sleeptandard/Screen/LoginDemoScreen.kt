package com.leejang.sleeptandard.Screen

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leejang.sleeptandard.backend.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

// 유저의 로그인/회원가입 진행 단계 정의 및 email + 비번 저장 클래스
sealed class AuthStep {
    object EmailInput : AuthStep() // 1단계: 이메일 입력
    data class LoginPassword(val email: String) : AuthStep() // 2단계(경로A): 로그인 비밀번호
    data class SignupPassword(val email: String) : AuthStep() // 2단계(경로B): 회원가입 비밀번호
    data class SignupNickname(val email: String, val pw: String) : AuthStep() // 3단계: 닉네임
}

// profiles 테이블에 INSERT/SELECT 할 때 사용하는 데이터 클래스
@Serializable data class ProfileInsert(val id: String, val nickname: String, val email: String)

// 이메일 존재 여부 조회용
@Serializable data class ProfileEmail(val email: String)

// Supabase Auth 실제 연동 ViewModel
class AuthViewModel : ViewModel() {
    var currentStep by mutableStateOf<AuthStep>(AuthStep.EmailInput)
        private set

    private val supabase = SupabaseClientProvider.client

    // 1단계: profiles 테이블에서 이메일 조회 → 신규/기존 유저 자동 분기
    fun checkEmail(email: String) {
        viewModelScope.launch {
            try {
                // email 컬럼만 요청해서 역직렬화 문제 방지
                val result =
                        supabase.postgrest["profiles"]
                                .select(columns = Columns.list("email")) {
                                    filter { eq("email", email) }
                                }
                                .decodeList<ProfileEmail>()
                currentStep =
                        if (result.isNotEmpty()) {
                            AuthStep.LoginPassword(email) // 기존 유저 → 로그인
                        } else {
                            AuthStep.SignupPassword(email) // 신규 유저 → 회원가입
                        }
            } catch (e: Exception) {
                Log.e("AuthVM", "checkEmail 실패: ${e.message}", e)
                // 네트워크 오류 등 → 로그인 화면으로 fallback
                currentStep = AuthStep.LoginPassword(email)
            }
        }
    }

    // 2단계(경로A): 실제 로그인
    fun performLogin(email: String, pw: String, onSuccess: (String) -> Unit, onError: () -> Unit) {
        viewModelScope.launch {
            try {
                supabase.auth.signInWith(Email) {
                    this.email = email
                    this.password = pw
                }
                // 로그인 성공 → profiles 테이블에서 닉네임 조회
                val uid = supabase.auth.currentUserOrNull()?.id ?: ""
                val profile =
                        supabase.postgrest["profiles"]
                                .select { filter { eq("id", uid) } }
                                .decodeSingle<ProfileInsert>()
                onSuccess(profile.nickname)
            } catch (e: Exception) {
                onError()
            }
        }
    }

    // 2단계(경로B): 회원가입 비번 설정 후 닉네임 단계로 이동
    fun setSignupPassword(email: String, pw: String) {
        currentStep = AuthStep.SignupNickname(email, pw)
    }

    // 3단계: 회원가입 완료 (Supabase Auth signUp + profiles 테이블 INSERT)
    fun completeSignup(email: String, pw: String, nickname: String, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            try {
                // 1) Supabase Auth 가입
                supabase.auth.signUpWith(Email) {
                    this.email = email
                    this.password = pw
                }
                // 2) 방금 발급된 UID 가져오기
                val uid = supabase.auth.currentUserOrNull()?.id ?: ""
                // 3) profiles 테이블에 닉네임 + 이메일 저장
                supabase.postgrest["profiles"].insert(
                        ProfileInsert(id = uid, nickname = nickname, email = email)
                )
                onComplete(nickname)
            } catch (e: Exception) {
                // 이미 가입된 이메일인 경우 등 예외 처리
                onComplete("오류: ${e.message}")
            }
        }
    }

    fun goToNicknameStep(email: String, pw: String) {
        currentStep = AuthStep.SignupNickname(email, pw)
    }

    fun backToEmail() {
        currentStep = AuthStep.EmailInput
    }
}

@Composable
fun LoginDemoScreen(authViewModel: AuthViewModel = viewModel(), onComplete: (String) -> Unit) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))

        // 현재 스텝에 따른 UI 출력 with animation
        AnimatedContent(
                targetState = authViewModel.currentStep,
                transitionSpec = {
                    fadeIn(animationSpec = tween(400))
                            .togetherWith(fadeOut(animationSpec = tween(400)))
                },
                label = "AuthStepTransition"
        ) { step ->
            when (step) {
                is AuthStep.EmailInput ->
                        EmailInputStep(onConfirm = { authViewModel.checkEmail(it) })
                is AuthStep.LoginPassword ->
                        LoginPasswordStep(
                                email = step.email,
                                onLogin = { pw ->
                                    authViewModel.performLogin(
                                            email = step.email,
                                            pw = pw,
                                            onSuccess = { nickname ->
                                                onComplete("$nickname 님, 환영합니다!")
                                            },
                                            onError = {
                                                Toast.makeText(
                                                                context,
                                                                "비밀번호가 틀렸습니다.",
                                                                Toast.LENGTH_SHORT
                                                        )
                                                        .show()
                                            }
                                    )
                                }
                        )
                is AuthStep.SignupPassword ->
                        SignupPasswordStep(
                                email = step.email,
                                onNext = { pw -> authViewModel.setSignupPassword(step.email, pw) }
                        )
                is AuthStep.SignupNickname ->
                        NicknameStep(
                                email = step.email,
                                pw = step.pw,
                                onComplete = { nickname ->
                                    authViewModel.completeSignup(step.email, step.pw, nickname) { resultMsg ->
                                        if (resultMsg.startsWith("오류")) {
                                            onComplete(resultMsg) // 실패 시 실제 에러 메시지 팝업
                                        } else {
                                            onComplete("$nickname 님, 가입을 축하합니다!")
                                        }
                                    }
                                }
                        )
            }
        }

        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun GlassyTextField(
        value: String,
        onValueChange: (String) -> Unit,
        placeholder: String,
        visualTransformation: VisualTransformation = VisualTransformation.None
) {
    BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            visualTransformation = visualTransformation,
            decorationBox = { innerTextField ->
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .height(60.dp)
                                        .background(
                                                Color.White.copy(0.05f),
                                                RoundedCornerShape(30.dp)
                                        )
                                        .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) Text(placeholder, color = Color.White.copy(0.3f))
                    innerTextField()
                }
            }
    )
}

@Composable
fun EmailInputStep(onConfirm: (String) -> Unit) {
    var email by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Text(
                text = "이메일을 입력해주세요 🌇",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
        )
        Spacer(Modifier.height(40.dp))

        GlassyTextField(value = email, onValueChange = { email = it }, placeholder = "이메일")

        Spacer(Modifier.height(100.dp))

        Button(enabled = email.contains("@"), onClick = { onConfirm(email) }) { Text("확인") }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun LoginPasswordStep(email: String, onLogin: (String) -> Unit) {
    var password by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Text(
                text = "비밀번호를 입력해주세요 🔒",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
        )
        Spacer(Modifier.height(40.dp))

        GlassyTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = "8자리 이상 입력해주세요",
                visualTransformation = PasswordVisualTransformation()
        )

        Text(
                text = "비밀번호 찾기",
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).clickable { /* 찾기 로직 */},
                textAlign = TextAlign.Center,
                color = Color.White.copy(alpha = 0.6f),
                textDecoration = TextDecoration.Underline,
                fontSize = 14.sp
        )

        Spacer(Modifier.weight(1f))

        Button(onClick = { onLogin(password) }) { Text("확인") }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun SignupPasswordStep(email: String, onNext: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Text(
                text = "비밀번호를 설정해주세요 🔐",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
        )
        Spacer(Modifier.height(40.dp))

        GlassyTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = "8자리 이상 입력해주세요",
                visualTransformation = PasswordVisualTransformation()
        )
        Spacer(Modifier.height(16.dp))
        GlassyTextField(
                value = passwordConfirm,
                onValueChange = { passwordConfirm = it },
                placeholder = "비밀번호 확인",
                visualTransformation = PasswordVisualTransformation()
        )

        Spacer(Modifier.weight(1f))

        Button(
                enabled = password.length >= 8 && password == passwordConfirm,
                onClick = { onNext(password) }
        ) { Text("확인") }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun NicknameStep(email: String, pw: String, onComplete: (String) -> Unit) {
    var nickname by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Text(
                text = "닉네임을 설정해주세요 🌙",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
        )
        Spacer(Modifier.height(40.dp))

        GlassyTextField(value = nickname, onValueChange = { nickname = it }, placeholder = "닉네임")

        Spacer(Modifier.weight(1f))

        Button(enabled = nickname.isNotBlank(), onClick = { onComplete(nickname) }) { Text("확인") }
        Spacer(Modifier.height(20.dp))
    }
}
