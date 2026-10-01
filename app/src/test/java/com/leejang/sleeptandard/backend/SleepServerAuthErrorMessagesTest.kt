package com.leejang.sleeptandard.backend

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepServerAuthErrorMessagesTest {
    @Test
    fun duplicateEmailHasClearKoreanMessage() {
        assertEquals(
            "이미 가입된 이메일입니다.",
            SleepServerAuthErrorMessages.fromServer(
                code = "EMAIL_ALREADY_EXISTS",
                serverMessage = "Email already exists",
                httpStatus = 409
            )
        )
    }

    @Test
    fun invalidPasswordDoesNotRevealWhichCredentialWasWrong() {
        assertEquals(
            "이메일 또는 비밀번호가 올바르지 않습니다.",
            SleepServerAuthErrorMessages.fromServer(
                code = "INVALID_LOGIN_CREDENTIALS",
                serverMessage = "Invalid email or password",
                httpStatus = 401
            )
        )
    }

    @Test
    fun expiredTokenRequestsLoginAgain() {
        assertEquals(
            "로그인이 만료되었습니다. 다시 로그인해주세요.",
            SleepServerAuthErrorMessages.fromServer(
                code = "INVALID_ACCESS_TOKEN",
                serverMessage = "Invalid or expired access token",
                httpStatus = 401
            )
        )
    }

    @Test
    fun unknownServerErrorKeepsServerMessage() {
        assertEquals(
            "Temporary error",
            SleepServerAuthErrorMessages.fromServer(
                code = "UNKNOWN_ERROR",
                serverMessage = "Temporary error",
                httpStatus = 503
            )
        )
    }
}
