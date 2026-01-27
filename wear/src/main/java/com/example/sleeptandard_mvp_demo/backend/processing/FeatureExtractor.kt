package com.example.sleeptandard_mvp_demo.backend.processing

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class FeatureExtractor {

    companion object {
        private const val STD_EPSILON = 1e-3f // 분모가 0이 되는 것을 방지
    }

    fun calculateApproxRmssd(hrList: List<Float>): Float {
        // [안정성] 유효한 값만 필터링
        val validHr = hrList.filter { it.isFinite() && it > 0 }
        if (validHr.size < 2) return 0.0f

        val ibiValues = validHr.map { 60000f / it }
        
        var sumSquaredDiff = 0.0
        for (i in 0 until ibiValues.size - 1) {
            val diff = ibiValues[i + 1] - ibiValues[i]
            sumSquaredDiff += diff.pow(2)
        }

        val meanSquaredDiff = sumSquaredDiff / (ibiValues.size - 1)
        return sqrt(meanSquaredDiff).toFloat()
    }

    fun getFeatures(
        hrBuffer: List<Float>, 
        userMean: Float, 
        userStd: Float,
        userBaseRmssd: Float,
        userStdRmssd: Float
    ): FloatArray {
        if (hrBuffer.isEmpty()) return FloatArray(5) { 0f }
        if (!userMean.isFinite()) return FloatArray(5) { 0f }

        // [안정성] 표준편차가 0에 가까우면 EPSILON 사용 (Divide by Zero 방지)
        val safeStd = if (!userStd.isFinite() || abs(userStd) < STD_EPSILON) STD_EPSILON else userStd
        val safeStdRmssd = if (!userStdRmssd.isFinite() || abs(userStdRmssd) < STD_EPSILON) STD_EPSILON else userStdRmssd

        // RMSSD 계산 (Raw 값)
        val rmssdRaw = calculateApproxRmssd(hrBuffer)
        
        // RMSSD Z-Score 정규화: (raw - mean) / std
        val rmssd = if (rmssdRaw.isFinite() && userBaseRmssd.isFinite()) {
            (rmssdRaw - userBaseRmssd) / safeStdRmssd
        } else {
            0f
        }

        // HR 정규화 및 통계 (Safe Std 사용)
        val normBuffer = hrBuffer.map { 
            if (it.isFinite()) (it - userMean) / safeStd else 0f 
        }
        
        val mean = normBuffer.average().toFloat()
        val std = sqrt(normBuffer.map { (it - mean).pow(2) }.average()).toFloat()
        val max = normBuffer.maxOrNull() ?: 0f
        val min = normBuffer.minOrNull() ?: 0f

        return floatArrayOf(mean, std, max, min, rmssd)
    }
}