package com.leejang.sleeptandard.Screen

import android.widget.Toast
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leejang.sleeptandard.Component.BirthDatePicker
import com.leejang.sleeptandard.ui.theme.AppIcons
import kotlinx.coroutines.launch
import java.util.Locale


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

/**** 1.이메일 체크, 2. 이메일 비번 (로그인)검증, 3.회원가입 처리 더미 로직 ****/
// TODO: 여기서 정보 뺴가야될듯?
// 로그인/회원가입 진행을 맡는 역할
class AuthViewModel : ViewModel() {
    var currentStep by mutableStateOf<AuthStep>(AuthStep.EmailInput)
        private set

    // ✅ 1. 모달 표시 상태 (true일 때만 화면에 Dialog가 뜸)
    var showDatePickerModal by mutableStateOf(false)
        private set
    // ✅ 2. 휠 피커(다이얼)에서 현재 돌아가고 있는 임시 값들
    var pickerYear by mutableIntStateOf(2000)
    var pickerMonth by mutableIntStateOf(1)
    var pickerDay by mutableIntStateOf(1)


    // 상태 변수들을 ViewModel로 이동
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var passwordConfirm by mutableStateOf("") // 회원가입용
    var nickname by mutableStateOf("")
    var gender by mutableStateOf("")    // 추가: "남자" or "여자"
    var birthdate by mutableStateOf("") // 추가: "YYYY.MM.DD"

    /** 유효성 검사 **/
    // 1. 이메일 유효성 검사용 정규표현식
    private val emailPattern = Regex(
        "^(([\\w-]+\\.)+[\\w-]+|([a-zA-Z]{1}|[\\w-]{2,}))@"
                + "((([0-1]?[0-2]?[0-9]{1,2}\\.){3}[0-1]?[0-2]?[0-9]{1,2})|"
                + "([a-zA-Z]+[\\w-]+\\.)+[a-zA-Z]{2,4})$"
    )

    // 2. 실시간 유효성 상태 (computed property)
    val isEmailValid: Boolean
        get() = email.matches(emailPattern)

    // 조건 1: 8자리 이상인지 검사
    val isPasswordLengthValid: Boolean
        get() = password.length >= 8

    // 조건 2: 허용된 문자(영문, 숫자, 특수문자)만 포함되었는지 검사
    // ^[A-Za-z\d@$!%*?&]*$ -> 빈 문자열이거나 허용된 문자로만 구성됨을 의미
    private val allowedCharsPattern = Regex("^[A-Za-z\\d@$!%*?&]*$")

    val isPasswordCharsValid: Boolean
        get() = password.matches(allowedCharsPattern)

    // ✅ 전체 유효성: 두 조건이 모두 참이어야 함
    val isPasswordValid: Boolean
        get() = isPasswordLengthValid && isPasswordCharsValid

    // 1. 특수문자 제외 (유니코드 문자+숫자 허용)
    private val nicknamePattern = Regex("^[\\p{L}\\p{N}]{1,15}$")

    // 2. 실시간 닉네임 유효성 상태
    val isNicknameValid: Boolean
        get() = nickname.matches(nicknamePattern)

    val isNicknameLengthValid: Boolean
        get() = (0 < nickname.length) && (nickname.length <= 15)

    // 입력값 업데이트 함수들
    // ✅ 이메일 업데이트: 모든 공백 문자 제거
    fun updateEmail(input: String) {
        email = input.filter { !it.isWhitespace() }
    }
    // ✅ 비밀번호 업데이트: 모든 공백 문자 제거
    fun updatePassword(input: String) {
        password = input.filter { !it.isWhitespace() }
    }
    // ✅ 비밀번호 확인 업데이트: 모든 공백 문자 제거
    fun updatePasswordConfirm(input: String) {
        passwordConfirm = input.filter { !it.isWhitespace() }
    }
    fun updateNickname(input: String) { nickname = input }
    fun updateGender(input: String) { gender = input }
    fun updateBirthdate(input: String) { birthdate = input }



    // 이메일 확인 API 호출 로직
    // 1단계: 이메일 확인 로직
    fun checkEmail(
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

    fun findingPassword(){
        currentStep = AuthStep.FindPassword(email)
    }

    fun resetPassword(){
        currentStep = AuthStep.PasswordReset(email)
    }

    // 2단계(경로A): 로그인 실행
    fun performLogin(onSuccess: (String) -> Unit, onError: () -> Unit) {
        val user = AuthRepository.verifyLogin(email, password)
        if (user != null) {
            onSuccess(user.nickname)
        } else {
            onError()
        }
    }

    // 2단계(경로B): 회원가입 비번 설정 후 이동
    fun setSignupPassword() {
        currentStep = AuthStep.SignupNickname(email, password)
    }
    // 2단계: 나이, 성별 설정
    fun setSignupGenderBirth() {
        currentStep = AuthStep.SignupGenderBirth(email, password, nickname)
    }

    // 3단계: 회원가입 완료 및 가입 처리
    fun completeSignup(onComplete: (String) -> Unit) {
        val newUser = User(email, password, nickname, gender, birthdate)
        AuthRepository.addUser(newUser)
        onComplete(nickname)
        currentStep = AuthStep.Completed(nickname)
    }

    fun goToNicknameStep(email: String, pw: String) {
        currentStep = AuthStep.SignupNickname(email, pw)
    }

    fun backToEmail() { currentStep = AuthStep.EmailInput }

    // ✅ 3. 모달을 여는 함수 (TextField 클릭 시 호출)
    fun openDatePicker() {
        // 이미 입력된 날짜가 있다면 해당 날짜로 휠 위치를 초기화합니다.
        if (birthdate.isNotEmpty()) {
            val parts = birthdate.split(".")
            if (parts.size == 3) {
                pickerYear = parts[0].toIntOrNull() ?: 2000
                pickerMonth = parts[1].toIntOrNull() ?: 1
                pickerDay = parts[2].toIntOrNull() ?: 1
            }
        }
        showDatePickerModal = true // 모달 표시 활성화
    }
    // ✅ 4. 모달을 닫는 함수 (취소 버튼 또는 배경 클릭 시 호출)
    fun closeDatePicker() {
        showDatePickerModal = false // 모달 표시 비활성화
    }
    // ✅ 5. 휠을 돌릴 때마다 실시간으로 임시 값을 업데이트하는 함수
    fun updatePickerValues(year: Int, month: Int, day: Int) {
        pickerYear = year
        pickerMonth = month
        pickerDay = day
    }
    // ✅ 6. '확인' 버튼 클릭 시 호출: 임시 값을 최종 결과에 반영하고 닫기
    fun confirmDatePickerSelection() {
        // "YYYY.MM.DD" 형식으로 포맷팅하여 저장합니다.
        birthdate = String.format(Locale.KOREA, "%d.%02d.%02d", pickerYear, pickerMonth, pickerDay)
        closeDatePicker() // 저장 후 모달 닫기
    }
}

@Composable
fun LoginDemoScreen(
    authViewModel: AuthViewModel = viewModel(),
    onConfirm: (String) -> Unit
){
    val context = LocalContext.current

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
                    viewModel = authViewModel)

                is AuthStep.LoginPassword -> LoginPasswordStep(
                    viewModel = authViewModel,
                    onLoginSuccess = { nickname ->
                        onConfirm("$nickname 님, 환영합니다!")
                    },
                    onLoginError = {
                        Toast.makeText(context, "비밀번호가 틀렸습니다.", Toast.LENGTH_SHORT).show()
                    },
                    onPwChange = {
                        // TODO: 비밀번호 재설정 메일 송신 로직 넣어야함
                        authViewModel.findingPassword()
                    }
                )

                is AuthStep.FindPassword -> PasswordChangeStep(
                    viewModel = authViewModel,
                    onConfirm = {
                        authViewModel.resetPassword()
                    },
                    onResend = {
                        // TODO: 비밀번호 재설정 메일 송신 로직 넣어야함
                    }
                )

                is AuthStep.PasswordReset -> PasswordResetStep(
                    viewModel = authViewModel,
                    onReset = {
                        // TODO: 비밀번호 재설정 로직 백엔드 연결 필요
                        authViewModel.backToEmail()
                    }
                )

                is AuthStep.SignupPassword -> SignupPasswordStep(viewModel = authViewModel)

                is AuthStep.SignupNickname -> NicknameStep(
                    viewModel = authViewModel
                )

                is AuthStep.SignupGenderBirth -> GenderBirthStep(
                    viewModel = authViewModel
                )

                is AuthStep.Completed -> CompletedStep(
                    nickname = step.nickname,
                    onConfirm = { onConfirm(it) }
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
fun EmailInputStep(viewModel: AuthViewModel) {

    val buttonGradient = linearGradient(
        listOf(Color(0xFF437AC7),
            Color(0xFFAFF4F9))
    )

    Column(modifier = Modifier
        .fillMaxSize()
    ){
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

        GlassyTextField(
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
                viewModel.checkEmail()
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
    onLoginSuccess: (String) -> Unit,
    onLoginError: () -> Unit,
    onPwChange: () -> Unit,
) {
    val buttonGradient = linearGradient(
        listOf(Color(0xFF437AC7),
            Color(0xFFAFF4F9))
    )


    Column(modifier = Modifier
        .fillMaxSize()
        ) {
        Spacer(Modifier.height(92.dp))
        Text(
            modifier = Modifier.padding(start = 10.dp),
            text = "비밀번호를 입력해주세요 ", //
            style = MaterialTheme.typography.bodyLarge.copy(
                color = Color.White,
                fontSize = 22.sp
            )
        )
        Spacer(Modifier.height(40.dp))

        GlassyTextField(
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
                // ✅ 이메일과 비번이 이미 VM에 있으므로 파라미터 없이 로그인을 시도합니다.
                viewModel.performLogin(
                    onSuccess = onLoginSuccess,
                    onError = onLoginError
                )
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
    onConfirm: () -> Unit,
    onResend: () -> Unit,
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
                onConfirm()
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

        GlassyTextField(
            value = viewModel.password,
            onValueChange = { viewModel.updatePassword(it) },
            placeholder = "8자리 이상 입력해주세요", //
            visualTransformation = PasswordVisualTransformation(),
            isPasswordInput = true
        )

        Spacer(Modifier.height(16.dp))

        GlassyTextField(
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
fun SignupPasswordStep(viewModel: AuthViewModel) {

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
        Text(
            text = "비밀번호를 설정해주세요", //
            style = MaterialTheme.typography.bodyLarge.copy(
                color = Color.White,
                fontSize = 22.sp
            )
        )
        Spacer(Modifier.height(40.dp))

        GlassyTextField(
            value = viewModel.password,
            onValueChange = { viewModel.updatePassword(it) },
            placeholder = "8자리 이상 입력해주세요", //
            visualTransformation = PasswordVisualTransformation(),
            isPasswordInput = true
        )

        Spacer(Modifier.height(16.dp))

        GlassyTextField(
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
            onClick = { viewModel.setSignupPassword() },
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
                            if((viewModel.isPasswordValid) && (viewModel.password == viewModel.passwordConfirm)) {
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
    viewModel: AuthViewModel) {

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
        Text(
            text = "닉네임을 설정해주세요", //
            style = MaterialTheme.typography.bodyLarge.copy(
                color = Color.White,
                fontSize = 22.sp
            )
        )

        Spacer(Modifier.height(40.dp))

        GlassyTextField(
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
            onClick = {
                viewModel.setSignupGenderBirth()
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
fun GenderBirthStep(viewModel: AuthViewModel) {
    val buttonGradient = Brush.linearGradient(
        listOf(Color(0xFF437AC7), Color(0xFFAFF4F9))
    )

    Column(
        modifier = Modifier.fillMaxSize()
    ) {

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
                                    viewModel.updateGender(text) },
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
                .clickable{
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
            onClick = {
                // ✅ 최종 회원가입 실행
                viewModel.completeSignup { nickname ->
                    Log.d("Signup", "$nickname 가입 완료")
                }
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
    nickname: String,
    onConfirm: (String) -> Unit
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
            text = "환영합니다, ${nickname}님!",
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
                        onConfirm("이용약관")
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
                        onConfirm("개인정보처리방침")
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
                .clickable{
                    onConfirm("홈")
                },
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

@Composable
fun IndicatorGlassBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val backgroundColor = Color(0xFFF1F4F9).copy(alpha = 0.3f)
    val borderColor = Color.White.copy(alpha = 0.2f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50.dp)), // 카드 모양
        contentAlignment = Alignment.Center
    ) {
        // [Layer 1] 배경 블러 & 그라데이션
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(100.dp) // 유리 뒤를 흐리게
                .background( color = backgroundColor
                    /*
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.15f), // 위쪽 하이라이트
                            Color.White.copy(alpha = 0.05f)  // 아래쪽 그림자
                        )
                    )
                     */
                )
                .border(
                    width = 3.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.4f), // 테두리 위쪽 (빛남)
                            Color.Transparent,             // 테두리 중간 (투명)
                            Color.White.copy(alpha = 0.1f)  // 테두리 아래쪽 (은은함)
                        )
                    ),
                    shape = RoundedCornerShape(50.dp)
                )
        )

        // [Layer 2] 실제 내용물 (선명함 유지)
        Box(modifier = Modifier) {
            content()
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
        is AuthStep.EmailInput -> 0         // 메일입력
        is AuthStep.SignupPassword -> 1     // 회원가입
        is AuthStep.SignupNickname -> 1
        is AuthStep.SignupGenderBirth -> 1
        is AuthStep.LoginPassword -> 2      // 로그인
        is AuthStep.FindPassword -> 2
        is AuthStep.PasswordReset -> 3      // 없음
        is AuthStep.Completed -> 3
    }

    val numberOfSteps = stepLabels.size
    val containerColor = Color(0xFF1B2432)
    val highlightColor = Color(0xFFAAEDF2)

    val barGradient = linearGradient(
        listOf(Color(0xFF437AC7),
            Color(0xFFAFF4F9))
    )

    if(currentIndex < 3){
        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {

            val stepWidth = (maxWidth - 8.dp) / numberOfSteps
            val targetOffset = stepWidth * currentIndex

            // 위치 이동 애니메이션
            val animatedOffset by animateDpAsState(
                targetValue = targetOffset,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                label = "step_highlight_move"
            )

            Column(
                modifier = Modifier
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ){
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(brush = barGradient)
                )
            }

            IndicatorGlassBox(
                modifier = Modifier
                    .width(105.dp)
                    .fillMaxHeight()
                    .offset(x = animatedOffset),
            ) {
                Text(
                    text = stepLabels[currentIndex],
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFFAFF4F9),
                        fontSize = 16.sp
                    )
                )
            }

            /*
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

             */
        }
    }


}

@Composable
fun GenderRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
){
    val dotRadius =
        animateDpAsState(
            targetValue = if (selected) 12.dp / 2 else 0.dp,
            // TODO Load the motionScheme tokens from the component tokens file
            // animationSpec = MotionSchemeKeyTokens.FastSpatial.value(),
        )
    val radioColor = Color.White
    val selectableModifier =
        if (onClick != null) {
            Modifier.selectable(
                selected = selected,
                onClick = onClick,
                enabled = enabled,
                role = Role.RadioButton,
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = 40.dp / 2),
            )
        } else {
            Modifier
        }
    Canvas(
        modifier
            .then(
                if (onClick != null) {
                    Modifier.minimumInteractiveComponentSize()
                } else {
                    Modifier
                }
            )
            .then(selectableModifier)
            .wrapContentSize(Alignment.Center)
            .padding(2.dp)
            .requiredSize(20.dp)
    ) {
        // Draw the radio button
        val strokeWidth = if(selected) 8.dp.toPx() else (1.5).dp.toPx()
        drawCircle(
            color = radioColor,
            radius = (20.dp / 2).toPx() - strokeWidth / 2,
            style = Stroke(strokeWidth),
        )
        if (dotRadius.value > 0.dp) {
            drawCircle(Color.Black, dotRadius.value.toPx(), style = Fill)
        }
    }
}

@Composable
fun BirthDatePicker(
    viewModel: AuthViewModel
) {

    Column(
        modifier = Modifier
            .size(281.dp, 334.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(color = Color.White),
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "생년월일",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 16.sp,
                    color = Color.Black
                )
            )
        }


        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.dp,
            color = Color(0xFF050C16).copy(alpha = 0.5f)
        )

        BirthDatePicker(
            modifier = Modifier
                .weight(1f),
            onDateChange = {y, m, d ->
                // ✅ 휠을 돌릴 때마다 VM의 임시 값 업데이트
                viewModel.updatePickerValues(y, m, d)
            },
            defaultYear = viewModel.pickerYear,
            defaultMonth = viewModel.pickerMonth,
            defaultDay = viewModel.pickerDay,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ){
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        color = Color(0xFFE0E0E0)
                    )
                    .clickable{
                        viewModel.closeDatePicker()
                    },
                contentAlignment = Alignment.Center
            ){
                Text(
                    text = "취소",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 16.sp,
                        color = Color.Black
                    )
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        color = Color(0xFFAFF4F9)
                    )
                    .clickable{
                        viewModel.confirmDatePickerSelection() // ✅ 최종 값 확정 및 모달 닫기
                    },
                contentAlignment = Alignment.Center
            ){
                Text(
                    text = "확인",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 16.sp,
                        color = Color.Black
                    )
                )
            }
        }
    }
}

/******* 백엔드가 처다볼 필요도 없는 테스트용 더미 서버, 리포지토리 ******/

// 사용자 정보를 담는 데이터 클래스
data class User(
    val email: String,
    val pw: String,
    val nickname: String,
    val gender: String, // "Male" or "Female"
    val birthdate: String // "YYYY.MM.DD"
)

// 서버 DB 역할을 하는 싱글톤 객체
object AuthRepository {
    // 더미 사용자 리스트
    private val dummyUsers = mutableListOf(
        User("test@test.com", "12345678", "테스터", "male", "2000.01.01"),
        User("admin@sleeptandard.com", "admin123", "관리자","male", "2000.01.01")
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