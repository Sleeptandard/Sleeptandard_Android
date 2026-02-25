package com.leejang.sleeptandard.backend.model

import kotlinx.serialization.Serializable

enum class SleepStage { WAKE, LIGHT, DEEP, REM, UNKNOWN }
enum class SensorType { ACC, HR }

// [복원] 외부(UI, 분석 모듈)에서 사용할 수 있도록 데이터 모델 유지
data class RawSensorData(
    val timestamp: Long,
    val type: SensorType,
    val x: Float, val y: Float, val z: Float
)

data class InferenceResult(
    val timestamp: Long,
    val stage: SleepStage,
    val confidence: Float,
    val features: String
)

/**
 * 폰으로 전송할 수면 데이터 결과
 * (Processed Results Only - 파일 전송 없음)
 */
@Serializable
data class SleepSessionResult(
    val startTime: Long,
    val endTime: Long,
    val stageHistory: List<StageEntry>
)

@Serializable
data class StageEntry(
    val timestamp: Long,
    val stage: String  // "WAKE", "LIGHT", "DEEP", "REM", "UNKNOWN"
)