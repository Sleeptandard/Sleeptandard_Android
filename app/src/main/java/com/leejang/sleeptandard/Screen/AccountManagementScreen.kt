 package com.leejang.sleeptandard.Screen

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leejang.sleeptandard.Component.BirthDatePicker
import com.leejang.sleeptandard.Component.GenderRadioButton
import com.leejang.sleeptandard.ViewModel.AuthViewModel
import com.leejang.sleeptandard.ui.theme.AppIcons
import com.leejang.sleeptandard.ui.theme.Key
import com.leejang.sleeptandard.ui.theme.Neon

 sealed class AccountMenu(val title: String) {
     object AMMain : AccountMenu("계정 관리")
     // 메인 메뉴
     object PersonalInfo : AccountMenu("개인 정보")
     object Email : AccountMenu("이메일")
     object Password : AccountMenu("비밀번호")
     object AuthAction : AccountMenu("로그아웃/탈퇴")

     // 하위 메뉴 (개인 정보 섹션)
     object Nickname : AccountMenu("닉네임")
     object Gender : AccountMenu("성별")
     object Birthdate : AccountMenu("생년월일")

     // 하위 메뉴 (탈퇴 섹션)
     object Logout : AccountMenu("로그아웃")
     object DeleteAccount : AccountMenu("계정탈퇴")
 }

@Composable
fun AccountManagementScreen(
    userViewModel: AuthViewModel,
    onBack: () -> Unit,
    onNicknameUpdate: () -> Unit,
    onGenderUpdate: () -> Unit,
    onBirthdateUpdate: () -> Unit,
    onEmailUpdate: () -> Unit,
    onPasswordUpdate: () -> Unit,
    onLogout: () -> Unit,
    onAccountDelete: () -> Unit
) {
    // 내비게이션 스택 관리 (현재 위치들을 순서대로 저장)
    val navStack = remember { mutableStateListOf<AccountMenu>(AccountMenu.AMMain) }
    val currentMenu = navStack.last() // 현재 화면은 항상 스택의 마지막 아이템

    // 시스템 뒤로가기 버튼 처리
    BackHandler(enabled = navStack.size > 1) {
        navStack.removeAt(navStack.lastIndex) // 스택에서 마지막 요소를 제거하여 이전 단계로 돌아감
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))

        AccountTopBar(
            title = currentMenu.title,
            onBack = {
                if (navStack.size > 1) {
                    navStack.removeAt(navStack.lastIndex) // 여기서 반환되는 아이템 객체를 무시합니다.
                } else {
                    onBack()
                }
            }
        )

        Spacer(Modifier.height(102.dp))

        // 내용
        AnimatedContent(
            targetState = currentMenu,
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith
                        fadeOut(animationSpec = tween(220))
            },
            label = "menu_transition"
        ) { targetMenu ->
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                when (targetMenu) {
                    /****     메인       ****/
                    // 계정 관리 메인화면
                    is AccountMenu.AMMain -> MainScreen(userViewModel, navStack)

                    /****     1층     ****/
                    // 메인 -> 개인정보 화면
                    is AccountMenu.PersonalInfo -> PersonalInfoScreen(userViewModel, navStack)

                    // 메인 -> 이메일 화면
                    is AccountMenu.Email -> EmailEdit(viewModel = userViewModel, onEmailUpdate = onEmailUpdate)

                    // 메인 -> 비밀번호 화면
                    is AccountMenu.Password -> PasswordEdit(viewModel = userViewModel, onPasswordUpdate = onPasswordUpdate)

                    // 메인 -> 로그아웃/탈퇴 화면
                    is AccountMenu.AuthAction -> AuthActionScreen(userViewModel, navStack)

                    /****     2층      ****/
                    // 개인정보 -> 닉네임 화면
                    is AccountMenu.Nickname -> NicknameEdit(viewModel = userViewModel, onNicknameUpdate = onNicknameUpdate)


                    // 개인정보 -> 성별 화면
                    is AccountMenu.Gender -> GenderEdit(viewModel = userViewModel, onGenderUpdate = onGenderUpdate)


                    // 개인정보 -> 생년월일 화면
                    is AccountMenu.Birthdate -> BirthdateEdit(viewModel = userViewModel, onBirthdateUpdate = onBirthdateUpdate)


                    // 로그아웃/탈퇴 -> 로그아웃 화면
                    is AccountMenu.Logout -> LogoutScreen(userViewModel, onLogout, onBack)
                    // 로그아웃/탈퇴 -> 탈퇴 화면
                    is AccountMenu.DeleteAccount -> AccountDeleteScreen(onAccountDelete, onBack)
                }
            }
        }
    }
}

 @Composable
 fun MainScreen(userViewModel: AuthViewModel, navStack: SnapshotStateList<AccountMenu>
 ){
     LazyColumn(
         modifier = Modifier.fillMaxSize(),
         horizontalAlignment = Alignment.CenterHorizontally
     ) {
            item { AMElement("개인 정보", "닉네임/성별/생년월일") { navStack.add(AccountMenu.PersonalInfo) } }
            item { AMElement("이메일", userViewModel.email){ navStack.add(AccountMenu.Email) } }
            item { AMElement("비밀번호", "비밀번호 변경"){ navStack.add(AccountMenu.Password) }}
            item { AMElement("로그아웃/탈퇴", ""){ navStack.add(AccountMenu.AuthAction) } }

     }
 }

 @Composable
 fun PersonalInfoScreen(userViewModel: AuthViewModel, navStack: SnapshotStateList<AccountMenu>) {
     LazyColumn(
         modifier = Modifier.fillMaxSize(),
         horizontalAlignment = Alignment.CenterHorizontally
     ) {
         item {
             AMElement(
                 "닉네임",
                 userViewModel.nickname
             ) { navStack.add(AccountMenu.Nickname) }
         }
         item {
             AMElement(
                 "성별",
                 userViewModel.gender
             ) { navStack.add(AccountMenu.Gender) }
         }
         item {
             AMElement(
                 "생년월일",
                 userViewModel.birthdate
             ) { navStack.add(AccountMenu.Birthdate) }
         }
     }
 }

 @Composable
 fun AuthActionScreen(userViewModel: AuthViewModel, navStack: SnapshotStateList<AccountMenu>) {
     LazyColumn(
         modifier = Modifier.fillMaxSize(),
         horizontalAlignment = Alignment.CenterHorizontally
     ) {

         item { AMElement("로그아웃", "") { navStack.add(AccountMenu.Logout) } }
         item { AMElement("계정탈퇴", "") { navStack.add(AccountMenu.DeleteAccount) } }

     }
 }

 @Composable
 fun NicknameEdit(
     viewModel: AuthViewModel,
     onNicknameUpdate: ()-> Unit
 ) {
     var tmpNickname by remember { mutableStateOf(viewModel.nickname) }

     val nicknameInvalidMessage = if(tmpNickname.length > 15)
         "15자 이하로 작성해주세요"
     else "특수문자는 들어갈 수 없어요"

     // 1. 특수문자 제외 (유니코드 문자+숫자 허용)
     val nicknamePattern = Regex("^[\\p{L}\\p{N} ]{1,15}$")

     // 2. 실시간 닉네임 유효성 상태
     val isNicknameValid = tmpNickname.matches(nicknamePattern)

     Column(
         modifier = Modifier
             .fillMaxSize()
             .imePadding()
     ) {
         WhiteTextField(
             value = tmpNickname,
             onValueChange = { tmpNickname = it },
             placeholder = "ex) 노곤노곤한 카피바라",
         )
         Spacer(Modifier.weight(1f))

         if(!isNicknameValid && tmpNickname.isNotBlank()){

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

         }

         Spacer(Modifier.weight(1f))

         Button(
             modifier = Modifier
                 .clip(RoundedCornerShape(100.dp)),
             enabled = isNicknameValid,
             onClick = {
                 if (isNicknameValid) {
                     viewModel.updateNickname(tmpNickname)
                     onNicknameUpdate()
                 }
             },
             contentPadding = PaddingValues(0.dp)
         ) {
             Box(
                 modifier = Modifier
                     .clip(RoundedCornerShape(100.dp))
                     .height(60.dp)
                     .background(color = Neon)
                     .fillMaxWidth(),
                 contentAlignment = Alignment.Center
             ) {
                 Text(
                     text = "확인",
                     style = MaterialTheme.typography.bodyMedium.copy(
                         fontSize = 18.sp,

                         color =
                             if (isNicknameValid) {
                                 Key
                             } else
                                 Color.Black.copy(alpha = 0.5f)
                     )
                 )
             }
         }
         Spacer(Modifier.height(20.dp))
     }
 }

 @Composable
 fun GenderEdit(
     viewModel: AuthViewModel,
     onGenderUpdate: ()-> Unit
 ) {
     Column(
         modifier = Modifier
             .fillMaxSize(),
     ) {
         // 성별 선택 섹션
         Text(
             modifier = Modifier.padding(start = 10.dp),
             text = "성별",
             style = MaterialTheme.typography.bodyMedium.copy(
                 color = Color.White, fontSize = 16.sp
             )
         )

         Spacer(Modifier.height(12.dp))

         val radioOptions = listOf("남", "여", "선택안함")
         val (selectedOption, onOptionSelected) = remember { mutableStateOf("") }
         Row(
             modifier = Modifier.selectableGroup(),
         ) {
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
         
         Spacer(Modifier.weight(1f))

         Button(
             modifier = Modifier
                 .clip(RoundedCornerShape(100.dp)),
             enabled = selectedOption.isNotEmpty(),
             onClick = {
                    onGenderUpdate()
             },
             contentPadding = PaddingValues(0.dp)
         ) {
             Box(
                 modifier = Modifier
                     .clip(RoundedCornerShape(100.dp))
                     .fillMaxWidth()
                     .height(60.dp)
                     .background(color = Neon),
                 contentAlignment = Alignment.Center
             ) {
                 Text(
                     text = "확인",
                     style = MaterialTheme.typography.bodyMedium.copy(
                         fontSize = 18.sp,

                         color =
                             if (selectedOption.isNotEmpty()) {
                                 Key
                             } else
                                 Color.Black.copy(alpha = 0.5f)
                     )
                 )
             }
         }
         Spacer(Modifier.height(20.dp))
     }
 }

 @Composable
 fun BirthdateEdit(
     viewModel: AuthViewModel,
     onBirthdateUpdate: ()-> Unit
 ) {
     Column(
         modifier= Modifier.fillMaxSize()
     ) {
         Text(
             text = "생년월일",
             style = MaterialTheme.typography.bodyMedium.copy(
                 color = Color.White, fontSize = 16.sp
             )
         )

         Spacer(Modifier.height(12.dp))

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

         // ✅ 1. 모달 표시 상태가 true일 때만 다이얼로그를 띄웁니다.
         if (viewModel.showDatePickerModal) {
             Dialog(
                 onDismissRequest = { viewModel.closeDatePicker() } // 다이얼로그 바깥 터치 시 닫기
             ) {
                 BirthDatePicker(viewModel = viewModel)
             }
         }
         
         Spacer(Modifier.weight(1f))

         Button(
             modifier = Modifier
                 .clip(RoundedCornerShape(100.dp)),
             enabled = viewModel.birthdate.isNotEmpty(),
             onClick = {
                 onBirthdateUpdate()
             },
             contentPadding = PaddingValues(0.dp)
         ) {
             Box(
                 modifier = Modifier
                     .clip(RoundedCornerShape(100.dp))
                     .fillMaxWidth()
                     .height(60.dp)
                     .background(color = Neon),
                 contentAlignment = Alignment.Center
             ) {
                 Text(
                     text = "확인",
                     style = MaterialTheme.typography.bodyMedium.copy(
                         fontSize = 18.sp,

                         color =
                             if (viewModel.birthdate.isNotEmpty()) {
                                 Key
                             } else
                                 Color.Black.copy(alpha = 0.5f)
                     )
                 )
             }
         }
         Spacer(Modifier.height(20.dp))
     }
 }

 @Composable
 fun EmailEdit(
     viewModel: AuthViewModel,
     onEmailUpdate: () -> Unit
 ) {


     var tmpEmail by remember { mutableStateOf(viewModel.email) }
     var isEmailExist by remember { mutableStateOf(false) }

     val emailPattern = Regex(
         "^(([\\w-]+\\.)+[\\w-]+|([a-zA-Z]{1}|[\\w-]{2,}))@"
                 + "((([0-1]?[0-2]?[0-9]{1,2}\\.){3}[0-1]?[0-2]?[0-9]{1,2})|"
                 + "([a-zA-Z]+[\\w-]+\\.)+[a-zA-Z]{2,4})$"
     )

     // 2. 실시간 유효성 상태 (computed property)
     val isEmailValid = tmpEmail.matches(emailPattern)

     Column(
         modifier = Modifier
             .fillMaxSize()
             .imePadding()

     ) {
         WhiteTextField(
             value = tmpEmail,
             onValueChange = { tmpEmail = it },
             placeholder = "이메일",
             isEmailInput = true
         )
         Spacer(Modifier.weight(1f))



         Spacer(Modifier.weight(1f))

         Button(
             modifier = Modifier
                 .clip(RoundedCornerShape(100.dp)),
             enabled = isEmailValid,
             onClick = {
                 isEmailExist = viewModel.isEmailExist()
                 if (!isEmailExist) {
                     viewModel.updateEmail(tmpEmail)
                     onEmailUpdate()
                 }
             },
             contentPadding = PaddingValues(0.dp)
         ) {
             Box(
                 modifier = Modifier
                     .clip(RoundedCornerShape(100.dp))
                     .fillMaxWidth()
                     .height(60.dp)
                     .background(color = Neon),
                 contentAlignment = Alignment.Center
             ) {
                 Text(
                     text = "확인",
                     style = MaterialTheme.typography.bodyMedium.copy(
                         fontSize = 18.sp,

                         color =
                             if (isEmailValid) {
                                 Key
                             } else
                                 Color.Black.copy(alpha = 0.5f)
                     )
                 )
             }
         }
         Spacer(Modifier.height(20.dp))
     }
 }

 @Composable
 fun PasswordEdit(
     viewModel: AuthViewModel,
     onPasswordUpdate: () -> Unit
 ) {
     var isVerified by remember { mutableStateOf(false) }
     var isClicked by remember { mutableStateOf(false) }

     var tmpPw by remember { mutableStateOf("") }
     var tmpNewPw by remember { mutableStateOf("") }
     var tmpNewPwConfirm  by remember { mutableStateOf("") }

     // 조건 1: 8자리 이상인지 검사
     val isPasswordLengthValid = tmpPw.length >= 8

     // 조건 2: 허용된 문자(영문, 숫자, 특수문자)만 포함되었는지 검사
     // ^[A-Za-z\d@$!%*?&]*$ -> 빈 문자열이거나 허용된 문자로만 구성됨을 의미
     val allowedCharsPattern = Regex("^[A-Za-z\\d@$!%*?&]*$")

     // 조건 1: 8자리 이상인지 검사
     val isNewPasswordLengthValid = tmpNewPw.length >= 8
     val isNewPasswordCharsValid = tmpNewPw.matches(allowedCharsPattern)

     // ✅ 전체 유효성: 두 조건이 모두 참이어야 함
     val isNewPasswordValid = isPasswordLengthValid && isNewPasswordCharsValid


     Column(
         modifier = Modifier
             .fillMaxSize()
             .imePadding()

     ) {
         if(!isVerified){
             WhiteTextField(
                 value = tmpPw,
                 onValueChange = { tmpPw = it },
                 placeholder = "현재 비밀번호",
             )
             Spacer(Modifier.weight(1f))

             if(isClicked){
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
                         text = "비밀번호가 틀렸습니다",
                         style = MaterialTheme.typography.bodyMedium.copy(
                             fontSize = 14.sp,
                             color = Color(0xFFEF4444)
                         ),
                         textAlign = TextAlign.Center,
                         lineHeight = 16.sp
                     )
                 }
             }

             Spacer(Modifier.weight(1f))

             Button(
                 modifier = Modifier
                     .clip(RoundedCornerShape(100.dp)),
                 enabled = isPasswordLengthValid,
                 onClick = {
                     isVerified = viewModel.isPasswordCorret(tmpPw)
                     isClicked = true
                 },
                 contentPadding = PaddingValues(0.dp)
             ) {
                 Box(
                     modifier = Modifier
                         .clip(RoundedCornerShape(100.dp))
                         .fillMaxWidth()
                         .height(60.dp)
                         .background(color = Neon),
                     contentAlignment = Alignment.Center
                 ) {
                     Text(
                         text = "다음",
                         style = MaterialTheme.typography.bodyMedium.copy(
                             fontSize = 18.sp,

                             color =
                                 if (isPasswordLengthValid) {
                                     Key
                                 } else
                                     Color.Black.copy(alpha = 0.5f)
                         )
                     )
                 }
             }
             Spacer(Modifier.height(20.dp))
         }
         else{
             val pwInvalidMessage = if(!isNewPasswordCharsValid) "영어, 숫자, 특수기호(@,\$,!,%,*,?,&)만 가능합니다"
             else if(!isNewPasswordLengthValid) "8자리 이상 입력해주세요"
             else "비밀번호를 다시 확인해주세요"

             WhiteTextField(
                 value = tmpNewPw,
                 onValueChange = { tmpNewPw = it },
                 placeholder = "새 비밀번호 (8자리 이상)",
             )

             Spacer(Modifier.height(20.dp))

             WhiteTextField(
                 value = tmpNewPwConfirm,
                 onValueChange = { tmpNewPwConfirm = it },
                 placeholder = "새 비밀번호 확인",
             )

             Spacer(Modifier.weight(1f))

             val isWrong = (tmpNewPw.isNotEmpty() && !isNewPasswordValid) || ((tmpNewPw != tmpNewPwConfirm) && tmpNewPwConfirm.isNotEmpty())

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
             }

             Spacer(Modifier.weight(1f))

             val isOk = isNewPasswordValid && (tmpNewPw == tmpNewPwConfirm)

             Button(
                 modifier = Modifier
                     .clip(RoundedCornerShape(100.dp)),
                 enabled = isOk,
                 onClick = {
                     onPasswordUpdate()
                 },
                 contentPadding = PaddingValues(0.dp)
             ) {
                 Box(
                     modifier = Modifier
                         .clip(RoundedCornerShape(100.dp))
                         .fillMaxWidth()
                         .height(60.dp)
                         .background(color = Neon),
                     contentAlignment = Alignment.Center
                 ) {
                     Text(
                         text = "변경하기",
                         style = MaterialTheme.typography.bodyMedium.copy(
                             fontSize = 18.sp,

                             color =
                                 if (isOk) {
                                     Key
                                 } else
                                     Color.Black.copy(alpha = 0.5f)
                         )
                     )
                 }
             }
             Spacer(Modifier.height(20.dp))
         }

     }
 }

 @Composable
 fun LogoutScreen(
     viewModel: AuthViewModel,
     onLogout: () -> Unit,
     onBack: () -> Unit
 ){
    Column(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "로그아웃하시겠습니까?",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 26.sp
            )
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "현재 계정에서 로그아웃됩니다",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 18.sp
            )
        )
        Text(
            text = "언제든 다시 로그인할 수 있습니다",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 18.sp
            )
        )

        Spacer(Modifier.weight(1f))

        Text(
            text = "알람의 정석을 다시 이용하려면",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 18.sp
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ){
            Text(
                text = viewModel.email,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 18.sp
                )
            )
            Text(
                text = "(으)로 로그인하세요.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 18.sp
                )
            )
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .height(60.dp)
                    .weight(1f),
                onClick = { onBack() },
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .fillMaxSize()
                        .background(color = Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "취소",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 18.sp,
                            color = Key
                        )
                    )
                }
            }

            Spacer(Modifier.width(20.dp))

            Button(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .height(60.dp)
                    .weight(1f),
                onClick = { onLogout() },
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .fillMaxSize()
                        .background(color = Neon),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "로그아웃",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 18.sp,
                            color = Key
                        )
                    )
                }
            }
        }
        Spacer(Modifier.height(65.dp))
    }
 }

 @Composable
 fun AccountDeleteScreen(onAccountDelete: () -> Unit, onBack: () -> Unit) {

     var isChecked by remember { mutableStateOf(false) }
     var showDialog by remember { mutableStateOf(false) }
     val checkBackground = if (isChecked) Color.White else Color.Transparent

     Column(
         modifier = Modifier
             .fillMaxSize(),
         horizontalAlignment = Alignment.CenterHorizontally
     ) {
         Text(
             text = "계정을 탈퇴하면 저장된 수면 기록과 설정",
             style = MaterialTheme.typography.bodyMedium.copy(
                 fontSize = 18.sp
             )
         )
         Row(){
             Text(
                 text = "정보가 ",
                 style = MaterialTheme.typography.bodyMedium.copy(
                     fontSize = 18.sp
                 )
             )
             Text(
                 text = "모두 삭제",
                 style = MaterialTheme.typography.bodyMedium.copy(
                     fontSize = 18.sp,
                     color = Color(0xFFE85D75)
                 )
             )
             Text(
                 text = "되며 복구할 수 없습니다",
                 style = MaterialTheme.typography.bodyMedium.copy(
                     fontSize = 18.sp
                 )
             )
         }



         Spacer(Modifier.weight(1f))

         Text(
             text = "•저장된 수면 기록이 모두 삭제됩니다",
             style = MaterialTheme.typography.bodyMedium.copy(
                 fontSize = 16.sp
             )
         )
         Text(
             text = "•알람 및 설정 정보가 초기화됩니다",
             style = MaterialTheme.typography.bodyMedium.copy(
                 fontSize = 16.sp
             )
         )
         Text(
             text = "•탈퇴 후 동일한 계정으로 복구할 수 없습니다",
             style = MaterialTheme.typography.bodyMedium.copy(
                 fontSize = 16.sp
             )
         )

         Spacer(Modifier.weight(1f))

         Row(
             modifier = Modifier
                 .padding(20.dp)
                 .fillMaxWidth(),
             verticalAlignment = Alignment.CenterVertically
         ) {

             Box(
                 modifier = Modifier
                     .padding(5.dp)
                     .size(23.dp)
                     .background(
                         color = checkBackground,
                         shape = RoundedCornerShape(5.dp)
                     )
                     .border(
                         width = 2.dp,
                         color = Color.White,
                         shape = RoundedCornerShape(5.dp)
                     )
                     .clickable { isChecked = !isChecked },
                 contentAlignment = Alignment.Center
             ) {
                 if (isChecked) {
                     Icon(
                         painter = painterResource(AppIcons.HomeCheck),
                         contentDescription = "췤",
                         tint = Key
                     )
                 }
             }

             Text(
                 modifier = Modifier.padding(start = 10.dp),
                 text = "위 내용을 확인했으며 계정 탈퇴에 동의합니다",
                 style = MaterialTheme.typography.bodyLarge.copy(
                     color = Color.White,
                     fontSize = 14.sp
                 )
             )
         }

         Spacer(Modifier.height(80.dp))

         Row(
             modifier = Modifier.fillMaxWidth(),
         ) {
             Button(
                 modifier = Modifier
                     .clip(RoundedCornerShape(100.dp))
                     .weight(1f)
                     .height(60.dp),
                 onClick = { onBack() },
                 contentPadding = PaddingValues(0.dp)
             ) {
                 Box(
                     modifier = Modifier
                         .clip(RoundedCornerShape(100.dp))
                         .fillMaxSize()
                         .background(color = Color.White),
                     contentAlignment = Alignment.Center
                 ) {
                     Text(
                         text = "취소",
                         style = MaterialTheme.typography.bodyMedium.copy(
                             fontSize = 18.sp,
                             color = Key
                         )
                     )
                 }
             }

             Spacer(Modifier.width(20.dp))

             Button(
                 modifier = Modifier
                     .clip(RoundedCornerShape(100.dp))
                     .height(60.dp)
                     .weight(1f),
                 enabled = isChecked,
                 onClick = { showDialog = true },
                 contentPadding = PaddingValues(0.dp)
             ) {
                 Box(
                     modifier = Modifier
                         .clip(RoundedCornerShape(100.dp))
                         .fillMaxSize()
                         .background(
                             color = if (isChecked) Color(0xFFE85D75) else Color(0xFFE85D75).copy(
                                 alpha = 0.5f
                             )
                         ),
                     contentAlignment = Alignment.Center
                 ) {
                     Text(
                         text = "계정탈퇴",
                         style = MaterialTheme.typography.bodyMedium.copy(
                             fontSize = 18.sp,
                             color = if(isChecked)Key else Color.Black.copy(alpha = 0.5f)
                         )
                     )
                 }
             }
         }
         Spacer(Modifier.height(65.dp))
     }

     if (showDialog) {
         Dialog(
             onDismissRequest = { showDialog = false } // 다이얼로그 바깥 터치 시 닫기
         ) {
             AccountDeleteDialog(onAccountDelete, onCanel = {showDialog = false})
         }
     }

 }

 @Composable
 fun AccountDeleteDialog(
     onAccountDelete: () -> Unit,
     onCanel: ()-> Unit
 ) {

     Column(
         modifier = Modifier
             .fillMaxWidth()
             .height(200.dp)
             .padding(horizontal = 20.dp)
             .clip(RoundedCornerShape(28.dp))
             .background(color = Color.White),
         horizontalAlignment = Alignment.CenterHorizontally
     ){
         Column(
             modifier = Modifier.weight(1f),
             horizontalAlignment = Alignment.CenterHorizontally,
             verticalArrangement = Arrangement.Center
         ){
             Text(
                 text = "정말 탈퇴하시겠습니까?",
                 style = MaterialTheme.typography.bodyMedium.copy(
                     fontSize = 18.sp,
                     color = Key
                 )
             )
             Text(
                 text = "이 작업은 되돌릴 수 없습니다.",
                 style = MaterialTheme.typography.bodyMedium.copy(
                     fontSize = 18.sp,
                     color = Key
                 )
             )
         }


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
                     .clickable {
                         onCanel()
                     },
                 contentAlignment = Alignment.Center
             ){
                 Text(
                     text = "취소",
                     style = MaterialTheme.typography.bodyMedium.copy(
                         fontSize = 16.sp,
                         color = Key
                     )
                 )
             }

             Box(
                 modifier = Modifier
                     .weight(1f)
                     .fillMaxHeight()
                     .background(
                         color = Color(0xFFE85D75)
                     )
                     .clickable {
                         onAccountDelete()
                     },
                 contentAlignment = Alignment.Center
             ){
                 Text(
                     text = "확인",
                     style = MaterialTheme.typography.bodyMedium.copy(
                         fontSize = 16.sp,
                         color = Key
                     )
                 )
             }
         }
     }
 }

 @Composable
 fun AccountTopBar(title: String, onBack: () -> Unit) {
     Row(
         modifier = Modifier
             .fillMaxWidth()
             .height(60.dp),
         verticalAlignment = Alignment.CenterVertically,
     ){
         IconButton(
             modifier = Modifier.size(32.dp),
             onClick = onBack
         ) {
             Icon(
                 modifier = Modifier.size(32.dp),
                 painter = painterResource(AppIcons.QnAArrowBack),
                 contentDescription = "뒤로가기",
             )
         }

         Spacer(Modifier.weight(1f))
         // 제목 (질문 타이틀)
         Text(
             text = title,
             color = Color.White,
             style = MaterialTheme.typography.titleMedium.copy(
                 fontSize = 20.sp,
             )
         )

         Spacer(Modifier.weight(1f))
         Spacer(Modifier.width(32.dp))
     }
 }

 @Composable
 fun AMElement(
     title:String,
     contents: String,
     onClick: () -> Unit
 ) {

     Row(
         modifier = Modifier
             .fillMaxWidth()
             .height(42.dp)
             .clickable {
                 onClick()
             },
         verticalAlignment = Alignment.CenterVertically,

         ) {
         Text(
             modifier = Modifier.padding(start = 10.dp),
             text = title,
             style = MaterialTheme.typography.bodySmall.copy(
                 fontSize = 16.sp,
             )
         )
         Spacer(Modifier.weight(1f))
         Text(
             text = contents,
             style = MaterialTheme.typography.bodySmall.copy(
                 fontSize = 12.sp,
                 color = Color.White.copy(alpha = 0.7f)
             )
         )
         Box(
             modifier = Modifier.size(32.dp),
             contentAlignment = Alignment.Center
         ){
             Icon(
                 modifier = Modifier.size(16.dp),
                 painter = painterResource(AppIcons.HomeArrowRight),
                 contentDescription = ">",
             )
         }
     }

 }
