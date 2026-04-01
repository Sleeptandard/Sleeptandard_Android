package com.leejang.sleeptandard.backend

import android.util.Log
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

/**
 * CsvUploadManager
 *
 * 워치로부터 받은 CSV 파일을 Supabase Storage에 업로드합니다.
 *
 * 업로드 경로: sleep-logs/{userId}/{날짜}/파일명
 * ex) sleep-logs/user-uuid-1234/2026-04-01/received_sensor_log_xxx.csv
 *
 * - 로그인 상태: userId = Supabase Auth UID
 * - 비로그인 상태: userId = "anonymous" (폴백)
 */
object CsvUploadManager {

    private const val TAG = "CsvUploadManager"
    private const val BUCKET_NAME = "sleep-logs"

    /**
     * CSV 파일을 Supabase Storage에 업로드합니다.
     * 서비스 종료 시 코루틴이 취소되지 않도록 GlobalScope를 사용합니다.
     *
     * @param file 업로드할 CSV 파일
     */
    fun uploadCsvFile(file: File) {
        // [수정] 서비스가 파괴되어도 업로드는 끝까지 진행되도록 GlobalScope 사용
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val client = SupabaseClientProvider.client

                // 현재 로그인된 유저 UID 가져오기 (없으면 "anonymous" 사용)
                val userId = client.auth.currentUserOrNull()?.id ?: "anonymous"

                // 오늘 날짜 폴더 (YYYY-MM-DD)
                val dateFolder = LocalDate.now().toString()

                // 최종 Storage 경로: {userId}/{날짜}/{파일명}
                val storagePath = "$userId/$dateFolder/${file.name}"

                Log.i(TAG, "📤 Starting upload: $storagePath (${file.length() / 1024}KB)")

                // 파일을 바이트 배열로 읽어서 업로드
                val fileBytes = file.readBytes()

                client.storage.from(BUCKET_NAME).upload(
                    path = storagePath,
                    data = fileBytes
                ) {
                    upsert = true   // 동일 경로 파일 존재 시 덮어쓰기
                }

                Log.i(TAG, "✅ CSV uploaded to Supabase: $BUCKET_NAME/$storagePath")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to upload CSV: ${file.name}", e)
            }
        }
    }
}
