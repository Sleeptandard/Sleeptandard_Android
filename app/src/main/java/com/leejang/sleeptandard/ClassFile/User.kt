package com.leejang.sleeptandard.ClassFile

// 사용자 정보를 담는 데이터 클래스
data class User(
    val email: String = "",
    val pw: String = "",
    val nickname: String = "",
    val gender: String = "", // "Male" or "Female"
    val birthdate: String = "" // "YYYY.MM.DD"
)

