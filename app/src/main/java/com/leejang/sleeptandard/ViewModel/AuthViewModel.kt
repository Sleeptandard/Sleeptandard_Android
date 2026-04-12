package com.leejang.sleeptandard.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leejang.sleeptandard.ClassFile.AuthRepository
import com.leejang.sleeptandard.ClassFile.User
import com.leejang.sleeptandard.Screen.AuthStep
import kotlinx.coroutines.launch
import java.util.Locale

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

        //TODO: 서버의 유저정보에서 확인

    }

    fun findingPassword(){
        currentStep = AuthStep.FindPassword(email)
    }

    fun resetPassword(){
        currentStep = AuthStep.PasswordReset(email)
    }

    // 2단계(경로A): 로그인 실행
    fun performLogin(onSuccess: (User) -> Unit, onError: () -> Unit) {
        // 더미 서버에서 확인
        val user = AuthRepository.verifyLogin(email, password)
        if (user != null) {
            onSuccess(user)
        } else {
            onError()
        }

        //TODO: 서버의 유저정보에서 확인
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
        // TODO: 서버에 유저정보 등록
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

    /***********  작동 확인용 서버통신로직 임시 대체 함수  **********/
    // 더미 서버에 해당 이메일이 존재하는지 확인
    fun isEmailExist(): Boolean{
            // 더미 서버에서 확인
            return AuthRepository.isEmailExists(email)
    }
    // 입력받은 pw 정보가 현재 더미서버의 User의 email - password 와 일치하는지 반환하는 함수
    fun isPasswordCorret(pw: String): Boolean{
        return AuthRepository.verifyLogin(email, pw) != null
    }

    // UserInfoPrefs로 부터 해당 뷰모델에 정보를 가져오는 함수
    fun getUserInfo(user: User){
        email = user.email
        password = user.pw
        nickname = user.nickname
        gender = user.nickname
        birthdate = user.birthdate
    }

    // 해당 뷰모델의 User 정보를 반환하는 함수
    fun loadUserInfo(): User{
        return User(email, password, nickname, gender, birthdate)
    }

    fun clearUserInfo(){
        email = ""
        password = ""
        nickname = ""
        gender = ""
        birthdate = ""
    }
}
