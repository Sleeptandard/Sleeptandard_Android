package com.leejang.sleeptandard.backend

import android.content.Context

/** FastAPI 인증이 확정되면 백그라운드 Worker가 사용할 토큰을 제공한다. */
object SleepServerAuthProvider {
    fun bearerToken(context: Context): String? {
        @Suppress("UNUSED_VARIABLE")
        val appContext = context.applicationContext

        // TODO(App/Auth): FastAPI 로그인 구현 후 암호화된 세션 저장소에서
        // access token을 읽어 반환한다. Supabase token을 새 서버에 재사용하지 않는다.
        return null
    }
}
