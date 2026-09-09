package com.leejang.sleeptandard.Potch

import android.util.Log
import kotlin.math.sqrt

/**
 * ACC 데이터 로컬 Z-Score 정규화 유틸리티.
 *
 * ## 학습 방식과의 일치
 * 모델은 환자 1명의 하룻밤 데이터에 대해 `fit_transform`(로컬 표준화)을 적용하여 학습됨.
 * → 앱에서도 고정 상수가 아닌 **현재 윈도우(2.5분, 7500 raw 샘플) 데이터 자체의
 *   평균/표준편차**를 계산해 Z-Score를 적용해야 모델이 제 성능을 냄.
 *
 * ## 정규화 공식
 * ```
 * z = (x - mean) / std     (std ≈ 0이면 z = 0으로 처리)
 * ```
 *
 * ## 사용 방법
 * 5-에포크 윈도우가 완성될 때 [fitTransform]에 전체 샘플을 넘기면
 * 정규화된 샘플 리스트를 반환한다.
 */
object AccScaler {

    private const val TAG = "AccScaler"
    private const val EPS = 1e-8f  // std ≈ 0 방지용 소수

    /**
     * 주어진 ACC 샘플 배열 자체의 통계로 Z-Score 정규화를 수행한다.
     *
     * 학습의 `StandardScaler.fit_transform()`과 동일한 동작:
     * 1. 입력 배열 전체로 축별 mean/std 계산 (fit)
     * 2. 계산된 통계로 정규화 (transform)
     *
     * @param samples 정규화할 ACC 샘플 리스트 (윈도우 전체 — 7500개 권장)
     * @return 정규화된 샘플 리스트 (입력과 동일한 크기)
     */
    fun fitTransform(
        samples: List<Triple<Float, Float, Float>>
    ): List<Triple<Float, Float, Float>> {
        if (samples.isEmpty()) return emptyList()

        // ── 1. fit: 축별 평균 계산 ─────────────────────────────────────
        var sumX = 0.0; var sumY = 0.0; var sumZ = 0.0
        for ((x, y, z) in samples) { sumX += x; sumY += y; sumZ += z }
        val n = samples.size.toDouble()
        val meanX = (sumX / n).toFloat()
        val meanY = (sumY / n).toFloat()
        val meanZ = (sumZ / n).toFloat()

        // ── 2. fit: 축별 표준편차 계산 (population std) ────────────────
        var varX = 0.0; var varY = 0.0; var varZ = 0.0
        for ((x, y, z) in samples) {
            varX += (x - meanX) * (x - meanX)
            varY += (y - meanY) * (y - meanY)
            varZ += (z - meanZ) * (z - meanZ)
        }
        val stdX = sqrt(varX / n).toFloat().coerceAtLeast(EPS)
        val stdY = sqrt(varY / n).toFloat().coerceAtLeast(EPS)
        val stdZ = sqrt(varZ / n).toFloat().coerceAtLeast(EPS)

        Log.d(TAG, "Local stats — " +
                "X: mean=${"%.4f".format(meanX)} std=${"%.4f".format(stdX)} | " +
                "Y: mean=${"%.4f".format(meanY)} std=${"%.4f".format(stdY)} | " +
                "Z: mean=${"%.4f".format(meanZ)} std=${"%.4f".format(stdZ)}")

        // ── 3. transform: Z-Score 적용 ────────────────────────────────
        return samples.map { (x, y, z) ->
            Triple(
                (x - meanX) / stdX,
                (y - meanY) / stdY,
                (z - meanZ) / stdZ
            )
        }
    }
}

