package com.leejang.sleeptandard_mvp.backend.manager

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient

/**
 * Supabase 클라이언트 싱글톤.
 *
 * ▶ URL과 Anon Key 입력 방법:
 *   1. Supabase Dashboard (https://supabase.com/dashboard) 로그인
 *   2. 해당 프로젝트 선택 → 왼쪽 사이드바 "Project Settings" → "API" 탭
 *   3. "Project URL" 과 "anon public" 키를 아래에 붙여넣기
 */
object SupabaseManager {

    // ⬇️ 여기에 본인의 Supabase URL과 Anon Key를 입력하세요.
    private const val SUPABASE_URL = "https://wxmksoyicxjaoufbxqhm.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Ind4bWtzb3lpY3hqYW91ZmJ4cWhtIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzE0ODg5MzMsImV4cCI6MjA4NzA2NDkzM30.2MiE0fG8XbZhZ0G0YU_GGUiug6SHei6Uf9uqpBi15oE"

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        install(Auth)
    }
}
