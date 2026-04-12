package com.leejang.sleeptandard.ClassFile


/******* 백엔드가 처다볼 필요도 없는 테스트용 더미 서버, 유저 정보 리포지토리 ******/
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