package com.leejang.sleeptandard.ViewModel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leejang.sleeptandard.ClassFile.AuthRepository
import com.leejang.sleeptandard.ClassFile.User
import com.leejang.sleeptandard.Screen.AuthStep
import com.leejang.sleeptandard.backend.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.Locale

// profiles 테이블에 INSERT/SELECT 할 때 사용하는 데이터 클래스
@Serializable
data class ProfileInsert(
        val id: String,
        val nickname: String,
        val email: String,
        val gender: String? = null,
        val birthdate: String? = null
)

// 이메일 존재 여부 조회용
@Serializable 
data class ProfileEmail(val email: String)

// 로그인/회원가입 진행을 맡는 역할
class AuthViewModel : ViewModel() {
    var currentStep by mutableStateOf<AuthStep>(AuthStep.EmailInput)
        private set

    private val supabase = SupabaseClientProvider.client

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
    var gender by mutableStateOf("") // 추가: "남자" or "여자"
    var birthdate by mutableStateOf("") // 추가: "YYYY.MM.DD"

    /** 유효성 검사 */
    // 1. 이메일 유효성 검사용 정규표현식
    private val emailPattern =
            Regex(
                    "^(([\\w-]+\\.)+[\\w-]+|([a-zA-Z]{1}|[\\w-]{2,}))@" +
                            "((([0-1]?[0-2]?[0-9]{1,2}\\.){3}[0-1]?[0-2]?[0-9]{1,2})|" +
                            "([a-zA-Z]+[\\w-]+\\.)+[a-zA-Z]{2,4})$"
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
    fun updateNickname(input: String) {
        nickname = input
    }
    fun updateGender(input: String) {
        gender = input
    }
    fun updateBirthdate(input: String) {
        birthdate = input
    }

    // 이메일 확인 API 호출 로직
    // 1단계: 이메일 확인 로직
    fun checkEmail() {
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
                            AuthStep.LoginPassword(email)
                        } else {
                            AuthStep.SignupPassword(email)
                        }
            } catch (e: Exception) {
                Log.e("AuthVM", "checkEmail 실패: ${e.message}", e)
                currentStep = AuthStep.LoginPassword(email)
            }
        }
    }

    fun findingPassword() {
        viewModelScope.launch {
            try {
                supabase.auth.resetPasswordForEmail(email)
                Log.d("AuthVM", "비밀번호 재설정 메일 발송 완료: $email")
            } catch (e: Exception) {
                Log.e("AuthVM", "비밀번호 재설정 메일 발송 실패: ${e.message}", e)
            } finally {
                // 메일 발송 성공/실패 여부와 관계없이 안내 화면으로 이동
                currentStep = AuthStep.FindPassword(email)
            }
        }
    }

    // 외부(예: 딥링크)에서 비밀번호 재설정 화면으로 바로 진입할 때 호출
    fun goToPasswordReset() {
        // 이메일을 알고 있으면 좋겠지만, 딥링크 진입 시에는 빈 값일 수 있습니다.
        currentStep = AuthStep.PasswordReset(email)
    }

    // 비밀번호 재설정 완료 처리 (딥링크로 앱이 열린 후 새 비밀번호 적용)
    fun resetPassword(newPassword: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                supabase.auth.updateUser { password = newPassword }
                Log.d("AuthVM", "비밀번호 변경 완료")
                onSuccess()
                currentStep = AuthStep.EmailInput
            } catch (e: Exception) {
                Log.e("AuthVM", "비밀번호 변경 실패: ${e.message}", e)
                onError(e.message ?: "비밀번호 변경 중 오류가 발생했습니다.")
            }
        }
    }

    // 2단계(경로A): 로그인 실행
    fun performLogin(onSuccess: (User) -> Unit, onError: () -> Unit) {
        viewModelScope.launch {
            try {
                supabase.auth.signInWith(Email) {
                    this.email = this@AuthViewModel.email
                    this.password = this@AuthViewModel.password
                }
                val uid = supabase.auth.currentUserOrNull()?.id ?: ""
                val profile =
                        supabase.postgrest["profiles"]
                                .select { filter { eq("id", uid) } }
                                .decodeSingle<ProfileInsert>()
                val returnedUser = User(
                    email = profile.email,
                    pw = password,
                    nickname = profile.nickname,
                    gender = profile.gender ?: "",
                    birthdate = profile.birthdate ?: ""
                )
                onSuccess(returnedUser)
            } catch (e: Exception) {
                onError()
            }
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
    fun completeSignup(onComplete: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                // 1) Supabase Auth 가입
                supabase.auth.signUpWith(Email) {
                    this.email = this@AuthViewModel.email
                    this.password = this@AuthViewModel.password
                }
                // 2) 발급된 UID 가져오기
                val uid = supabase.auth.currentUserOrNull()?.id ?: ""
                // 3) profiles 테이블에 닉네임 + 이메일 + 성별 + 생년월일 저장
                supabase.postgrest["profiles"].insert(
                        ProfileInsert(
                                id = uid,
                                nickname = nickname,
                                email = email,
                                gender = gender,
                                birthdate = birthdate
                        )
                )

                onComplete(nickname)
                currentStep = AuthStep.Completed(nickname)
            } catch (e: Exception) {
                Log.e("AuthVM", "completeSignup 실패: ${e.message}", e)
                onError(e.message ?: "회원가입 처리 중 오류가 발생했습니다.")
            }
        }
    }

    fun goToNicknameStep(email: String, pw: String) {
        currentStep = AuthStep.SignupNickname(email, pw)
    }

    fun backToEmail() {
        currentStep = AuthStep.EmailInput
    }

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

    // 프로필 정보(닉네임, 성별, 생년월일)를 Supabase profiles 테이블에 업데이트
    fun saveProfileUpdate(onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val uid = supabase.auth.currentUserOrNull()?.id ?: throw Exception("로그인된 사용자가 없습니다.")
                supabase.postgrest["profiles"].update(
                    {
                        set("nickname", nickname)
                        set("gender", gender)
                        set("birthdate", birthdate)
                    }
                ) {
                    filter { eq("id", uid) }
                }
                Log.d("AuthVM", "프로필 업데이트 성공")
                onSuccess()
            } catch (e: Exception) {
                Log.e("AuthVM", "프로필 업데이트 실패: ${e.message}", e)
                onError(e.message ?: "프로필 업데이트 중 오류가 발생했습니다.")
            }
        }
    }

    // 이메일 변경
    fun updateUserEmail(newEmail: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                supabase.auth.updateUser {
                    email = newEmail
                }
                // profiles 테이블의 email 컬럼도 업데이트 (동기화 목적)
                val uid = supabase.auth.currentUserOrNull()?.id ?: throw Exception("로그인된 사용자가 없습니다.")
                supabase.postgrest["profiles"].update(
                    { set("email", newEmail) }
                ) {
                    filter { eq("id", uid) }
                }
                
                email = newEmail
                Log.d("AuthVM", "이메일 변경 요청 완료 (인증 메일 확인 필요)")
                onSuccess()
            } catch (e: Exception) {
                Log.e("AuthVM", "계정 탈퇴 실패: ${e.message}", e)
                onError(e.message ?: "계정 탈퇴 중 오류가 발생했습니다.")
            }
        }
    }

    // 비밀번호 변경
    fun updateUserPassword(newPassword: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                supabase.auth.updateUser {
                    password = newPassword
                }
                password = newPassword
                Log.d("AuthVM", "비밀번호 변경 성공")
                onSuccess()
            } catch (e: Exception) {
                Log.e("AuthVM", "비밀번호 변경 실패: ${e.message}", e)
                onError(e.message ?: "비밀번호 변경 중 오류가 발생했습니다.")
            }
        }
    }

    // 계정 탈퇴 (로그아웃 처리 및 프로필 데이터 삭제)
    fun deleteUserAccount(onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val uid = supabase.auth.currentUserOrNull()?.id ?: throw Exception("로그인된 사용자가 없습니다.")
                
                // 1) profiles 테이블 데이터 삭제
                supabase.postgrest["profiles"].delete {
                    filter { eq("id", uid) }
                }
                
                // 2) 로그아웃 처리
                supabase.auth.signOut()
                clearUserInfo()
                
                Log.d("AuthVM", "계정 탈퇴 처리 완료")
                onSuccess()
            } catch (e: Exception) {
                Log.e("AuthVM", "계정 탈퇴 실패: ${e.message}", e)
                onError(e.message ?: "계정 탈퇴 중 오류가 발생했습니다.")
            }
        }
    }

    // 완전한 로그아웃 (Supabase 세션 종료 포함)
    fun logoutUser(onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                supabase.auth.signOut()
                clearUserInfo()
                Log.d("AuthVM", "로그아웃 처리 완료")
                onSuccess()
            } catch (e: Exception) {
                Log.e("AuthVM", "로그아웃 실패: ${e.message}", e)
                // 만약 에러가 나더라도 클라이언트측 로그아웃은 진행
                clearUserInfo()
                onSuccess()
            }
        }
    }

    fun getUserInfo(user: User){
        email = user.email
        password = user.pw
        nickname = user.nickname
        gender = user.gender
        birthdate = user.birthdate
    }

    // 해당 뷰모델의 User 정보를 반환하는 함수
    fun loadUserInfo(): User{
        return User(email, password, nickname, gender, birthdate)
    }

    /***********  작동 확인용 서버통신로직  **********/
    // 서버에 해당 이메일이 존재하는지 확인
    suspend fun isEmailExistAsync(): Boolean {
        return try {
            val result = supabase.postgrest["profiles"]
                .select(columns = Columns.list("email")) {
                    filter { eq("email", email) }
                }
                .decodeList<ProfileEmail>()
            result.isNotEmpty()
        } catch (e: Exception) {
            Log.e("AuthVM", "이메일 중복 확인 실패: ${e.message}", e)
            false
        }
    }
    
    // 이메일 존재 여부 확인 (기존 함수 유지하되 내부 로직 변경 고려 - 다만 브로킹 호출이라 가급적 Async 권장)
    fun isEmailExist(): Boolean {
        // Note: 이 함수는 UI에서 동기적으로 호출되고 있음. 
        // 실제로는 코루틴 내에서 처리하는 것이 좋으나, 기존 UI 코드 호환성을 위해 우선 dummy 유지하거나 
        // runBlocking을 피하기 위해 UI 코드 수정을 제안해야 함.
        // 현재는 구현 계획에 따라 백엔드 연동을 우선함.
        return AuthRepository.isEmailExists(email)
    }

    // 입력받은 pw 정보가 현재 사용자의 비밀번호와 일치하는지 확인 (재인증 시도)
    suspend fun isPasswordCorrectAsync(pw: String): Boolean {
        return try {
            supabase.auth.signInWith(Email) {
                this.email = this@AuthViewModel.email
                this.password = pw
            }
            true
        } catch (e: Exception) {
            Log.e("AuthVM", "비밀번호 검증 실패: ${e.message}", e)
            false
        }
    }

    // 기존 함수 (UI 호환용)
    fun isPasswordCorret(pw: String): Boolean {
        return AuthRepository.verifyLogin(email, pw) != null
    }

    fun clearUserInfo(){
        email = ""
        password = ""
        nickname = ""
        gender = ""
        birthdate = ""
    }
}
