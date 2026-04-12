package com.leejang.sleeptandard.Prefs

import android.content.Context
import com.leejang.sleeptandard.Screen.User
import androidx.core.content.edit

class UserInfoPreferences(private val context: Context) {

    private val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    fun saveUserInfo(user: User){
        prefs.edit {
            putBoolean("isLogined", true)
            .putString("email", user.email)
                .putString("password", user.pw)
                .putString("nickname", user.nickname)
                .putString("gender", user.gender)
                .putString("birthdate", user.birthdate)
        }
    }

    fun loadUserInfo(): User{
        return User(
            email = prefs.getString("email", "") ?: "",
            pw = prefs.getString("password","") ?: "",
            nickname = prefs.getString("nickname", "") ?: "",
            gender = prefs.getString("gender", "") ?: "",
            birthdate = prefs.getString("birthdate", "") ?: "",
        )
    }

    fun clearUserInfo(){
        prefs.edit {
            clear()
        }
    }

    fun isLogined(): Boolean{
        return prefs.getBoolean("isLogined", false)
    }
}