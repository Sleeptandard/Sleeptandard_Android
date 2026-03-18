package com.leejang.sleeptandard.Screen

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch


// 유저의 로그인/회원가입 진행 단계 정의 및 email + 비번 저장 클래스
sealed class AuthStep {
    object EmailInput : AuthStep()                          // 1단계: 이메일 입력
    data class LoginPassword(val email: String) : AuthStep() // 2단계(경로A): 로그인 비밀번호
    data class SignupPassword(val email: String) : AuthStep() // 2단계(경로B): 회원가입 비밀번호
    data class SignupNickname(val email: String, val pw: String) : AuthStep() // 3단계: 닉네임
    data class Completed(val nickname: String) : AuthStep()
}

/**** 1.이메일 체크, 2. 이메일 비번 (로그인)검증, 3.회원가입 처리 더미 로직 ****/
// TODO: 여기서 정보 뺴가야될듯?
// 로그인/회원가입 진행을 맡는 역할
class AuthViewModel : ViewModel() {
    var currentStep by mutableStateOf<AuthStep>(AuthStep.EmailInput)
        private set

    // 이메일 확인 API 호출 로직
    // 1단계: 이메일 확인 로직
    fun checkEmail(
        email: String
        // exist: Boolean
    ) {
        viewModelScope.launch {
            // 더미 서버에서 확인
            val exists = AuthRepository.isEmailExists(email)
            currentStep = if (exists) AuthStep.LoginPassword(email)
            else AuthStep.SignupPassword(email)
        }

        //TODO: 백엔드 통신받은 사인으로 분기
        /*
        cureentStep = if (exist) AuthStep.LoginPassword(email)
            else AuthStep.SignupPassword(email)
         */
    }

    // 2단계(경로A): 로그인 실행
    fun performLogin(email: String, pw: String, onSuccess: (String) -> Unit, onError: () -> Unit) {
        val user = AuthRepository.verifyLogin(email, pw)
        if (user != null) {
            onSuccess(user.nickname)
        } else {
            onError() // 비밀번호 틀림
        }
    }

    // 2단계(경로B): 회원가입 비번 설정 후 이동
    fun setSignupPassword(email: String, pw: String) {
        currentStep = AuthStep.SignupNickname(email, pw)
    }

    // 3단계: 회원가입 완료 및 가입 처리
    fun completeSignup(email: String, pw: String, nickname: String, onComplete: (String) -> Unit) {
        val newUser = User(email, pw, nickname)
        AuthRepository.addUser(newUser)
        onComplete(nickname)
        currentStep = AuthStep.Completed(nickname)
    }

    fun goToNicknameStep(email: String, pw: String) {
        currentStep = AuthStep.SignupNickname(email, pw)
    }

    fun backToEmail() { currentStep = AuthStep.EmailInput }
}

@Composable
fun LoginDemoScreen(
    authViewModel: AuthViewModel = viewModel(),
    onConfirm: (String) -> Unit
){

    val context = LocalContext.current

    val BarGradient = Brush.horizontalGradient(
        colors = listOf(
            Color(0xFF437AC7),
            Color(0xFFAFF4F9)
        )
    )

    val linearGradation = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color(0xFF050C16),
            1f to Color(0xFF1C447C)
        )
    )


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = linearGradation)
            .padding(horizontal = 20.dp)
,
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        Spacer(Modifier.height(50.dp))

        /*
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ){
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ){
                Text(
                    text = "메일입력",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Text(
                    text = "회원가입",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Text(
                    text = "로그인",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(43.dp),
                contentAlignment = Alignment.Center
            ){
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(brush = BarGradient)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ){
                    Box(
                        modifier = Modifier
                            .size(105.dp, 43.dp),
                        contentAlignment = Alignment.Center
                    ){
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .blur(30.dp) // 유리 뒤를 흐리게
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.15f), // 위쪽 하이라이트
                                            Color.White.copy(alpha = 0.05f)  // 아래쪽 그림자
                                        )
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.4f), // 테두리 위쪽 (빛남)
                                            Color.Transparent,             // 테두리 중간 (투명)
                                            Color.White.copy(alpha = 0.1f)  // 테두리 아래쪽 (은은함)
                                        )
                                    ),
                                    shape = RoundedCornerShape(24.dp)
                                )
                        ){}

                        Text(
                            text = "메일입력",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFAFF4F9)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(105.dp, 43.dp),
                        contentAlignment = Alignment.Center
                    ){
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .blur(30.dp) // 유리 뒤를 흐리게
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.15f), // 위쪽 하이라이트
                                            Color.White.copy(alpha = 0.05f)  // 아래쪽 그림자
                                        )
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.4f), // 테두리 위쪽 (빛남)
                                            Color.Transparent,             // 테두리 중간 (투명)
                                            Color.White.copy(alpha = 0.1f)  // 테두리 아래쪽 (은은함)
                                        )
                                    ),
                                    shape = RoundedCornerShape(24.dp)
                                )
                        ){}

                        Text(
                            text = "회원가입",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFAFF4F9)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(105.dp, 43.dp),
                        contentAlignment = Alignment.Center
                    ){
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .blur(30.dp) // 유리 뒤를 흐리게
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.15f), // 위쪽 하이라이트
                                            Color.White.copy(alpha = 0.05f)  // 아래쪽 그림자
                                        )
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.4f), // 테두리 위쪽 (빛남)
                                            Color.Transparent,             // 테두리 중간 (투명)
                                            Color.White.copy(alpha = 0.1f)  // 테두리 아래쪽 (은은함)
                                        )
                                    ),
                                    shape = RoundedCornerShape(24.dp)
                                )
                        ){}

                        Text(
                            text = "로그인",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFAFF4F9)
                        )
                    }
                }
            }
        }

         */

        // ✅ 상단에 단계 인디케이터 배치
        AuthStepIndicator(
            currentStep = authViewModel.currentStep,
            modifier = Modifier.padding(vertical = 24.dp)
        )


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
                    // TODO: 이메일 탐색 백엔드 통신
                    // AuthViewModel의 이메일 탐색 더미 로직
                    onConfirm = { authViewModel.checkEmail(it) })

                is AuthStep.LoginPassword -> LoginPasswordStep(
                    email = step.email,
                    // TODO: 로그인 검증 백엔드 통신
                    // AuthViewModel의 로그인 검증 더미 로직
                    onLogin = { pw ->
                        authViewModel.performLogin(
                            email = step.email,
                            pw = pw,
                            onSuccess = { nickname -> onConfirm("$nickname 님, 환영합니다!") },
                            onError = { Toast.makeText(context, "비밀번호가 틀렸습니다.", Toast.LENGTH_SHORT).show() }
                        )
                    }
                )

                is AuthStep.SignupPassword -> SignupPasswordStep(
                    email = step.email,
                    onNext = { pw -> authViewModel.setSignupPassword(step.email, pw) }
                )

                is AuthStep.SignupNickname -> NicknameStep(
                    email = step.email,
                    pw = step.pw,
                    // TODO: 회원가입 백엔드 통신
                    // AuthViewModel의 회원가입 더미 로직
                    onComplete = { nickname ->
                        authViewModel.completeSignup(step.email, step.pw, nickname) {
                        }
                    }
                )

                is AuthStep.Completed -> CompletedStep(
                    nickname = step.nickname,
                    onConfirm = {nickname ->
                        onConfirm("$nickname 님, 가입을 축하합니다!")
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    // ✅ 기존에 정의한 innerShadow 적용
                    .background(Color.White.copy(0.05f), RoundedCornerShape(30.dp))
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

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp)) {
        Text(
            text = "이메일을 입력해주세요 🌇", //
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )
        Spacer(Modifier.height(40.dp))

        GlassyTextField(
            value = email,
            onValueChange = { email = it },
            placeholder = "이메일" //
        )

        Spacer(Modifier.height(100.dp)) // 버튼을 하단으로 밀어냄

        Button(
            enabled = email.contains("@"), // 간단한 유효성 검사
            onClick = { onConfirm(email) }
        ){
            Text("확인")
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun LoginPasswordStep(email: String, onLogin: (String) -> Unit) {
    var password by remember { mutableStateOf("") }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp)) {
        Text(
            text = "비밀번호를 입력해주세요 🔒", //
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )
        Spacer(Modifier.height(40.dp))

        GlassyTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = "8자리 이상 입력해주세요", //
            visualTransformation = PasswordVisualTransformation()
        )

        // 비밀번호 찾기
        Text(
            text = "비밀번호 찾기",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .clickable { /* 찾기 로직 */ },
            textAlign = TextAlign.Center,
            color = Color.White.copy(alpha = 0.6f),
            textDecoration = TextDecoration.Underline,
            fontSize = 14.sp
        )

        Spacer(Modifier.weight(1f))

        Button(

            onClick = { onLogin(password) }
        ){
            Text("확인")
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun SignupPasswordStep(email: String, onNext: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp)) {
        Text(
            text = "비밀번호를 설정해주세요 🔐", //
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )
        Spacer(Modifier.height(40.dp))

        GlassyTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = "8자리 이상 입력해주세요", //
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(Modifier.height(16.dp))
        GlassyTextField(
            value = passwordConfirm,
            onValueChange = { passwordConfirm = it },
            placeholder = "비밀번호 확인", //
            visualTransformation = PasswordVisualTransformation()
        )

        Spacer(Modifier.weight(1f))

        Button(
            enabled = password.length >= 8 && password == passwordConfirm,
            onClick = { onNext(password) }
        ){
            Text("확인")
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun NicknameStep(email: String, pw: String, onComplete: (String) -> Unit) {
    var nickname by remember { mutableStateOf("") }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp)) {
        Text(
            text = "닉네임을 설정해주세요 🌙", //
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )
        Spacer(Modifier.height(40.dp))

        GlassyTextField(
            value = nickname,
            onValueChange = { nickname = it },
            placeholder = "닉네임" //
        )

        Spacer(Modifier.weight(1f))

        Button(
            enabled = nickname.isNotBlank(),
            onClick = { onComplete(nickname) }
        ){
            Text("확인")
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun CompletedStep(
    nickname: String,
    onConfirm: (String) -> Unit
){
    Column(
        modifier = Modifier
            .fillMaxSize()
    )
    {
        Text(
            text = "환영합니다, " + nickname + "님!",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 28.sp,
                color = Color.White
            )
        )
        Spacer(Modifier.height(50.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(Color.White)
                .clickable{
                    onConfirm(nickname)
                },
            contentAlignment = Alignment.Center
        ){
            Text(
                text = "확인",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 18.sp,
                    color = Color.Black
                )
            )
        }
    }



}

@Composable
fun AuthStepIndicator(
    currentStep: AuthStep,
    modifier: Modifier = Modifier
) {
    // 1. 표시할 텍스트 리스트 (로그인/회원가입 공통 단계로 구성)
    val stepLabels = listOf("메일 입력", "회원가입", "로그인")

    // 2. ✅ 핵심 수정: 현재 상태(AuthStep)를 인덱스 번호로 매핑합니다.
    // SignupPassword와 LoginPassword를 모두 '비밀번호 입력' 단계(인덱스 1)로 묶어줍니다.
    val currentIndex = when (currentStep) {
        is AuthStep.EmailInput -> 0
        is AuthStep.SignupPassword -> 1
        is AuthStep.SignupNickname -> 1
        is AuthStep.LoginPassword -> 2
        is AuthStep.Completed -> 3
    }

    val numberOfSteps = stepLabels.size
    val containerColor = Color(0xFF1B2432)
    val highlightColor = Color(0xFFAAEDF2)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(containerColor, RoundedCornerShape(100.dp))
            .padding(4.dp)
    ) {
        val stepWidth = (maxWidth - 8.dp) / numberOfSteps
        val targetOffset = stepWidth * currentIndex

        // 위치 이동 애니메이션
        val animatedOffset by animateDpAsState(
            targetValue = targetOffset,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            label = "step_highlight_move"
        )

        // 하이라이트 박스
        Box(
            modifier = Modifier
                .width(stepWidth)
                .fillMaxHeight()
                .offset(x = animatedOffset)
                .background(highlightColor, RoundedCornerShape(100.dp))
        )

        // 텍스트 레이어
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            stepLabels.forEachIndexed { index, label ->
                val isSelected = index == currentIndex
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color(0xFF111111) else Color.White.copy(alpha = 0.6f)
                        )
                    )
                }
            }
        }
    }
}

/******* 백엔드가 처다볼 필요도 없는 테스트용 더미 서버, 리포지토리 ******/

// 사용자 정보를 담는 데이터 클래스
data class User(
    val email: String,
    val pw: String,
    val nickname: String
)

// 서버 DB 역할을 하는 싱글톤 객체
object AuthRepository {
    // 더미 사용자 리스트
    private val dummyUsers = mutableListOf(
        User("test@test.com", "12345678", "테스터"),
        User("admin@sleeptandard.com", "admin123", "관리자")
    )

    // 1. 이메일 존재 여부 확인 (api.checkEmail 역할)
    fun isEmailExists(email: String): Boolean {
        return dummyUsers.any { it.email == email }
    }

    // 2. 로그인 확인 (onLogin 역할)
    fun verifyLogin(email: String, pw: String): User? {
        return dummyUsers.find { it.email == email && it.pw == pw }
    }

    // 3. 회원가입 완료 및 정보 추가 (onComplete 역할)
    fun addUser(user: User) {
        dummyUsers.add(user)
        println("📦 새로운 사용자 추가됨: $user") // 로그 확인용
    }
}