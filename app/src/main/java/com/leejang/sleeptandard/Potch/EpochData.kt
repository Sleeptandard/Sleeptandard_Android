package com.leejang.sleeptandard.Potch

/**
 * 30초 단위 에포크(Epoch) 데이터.
 *
 * Potch BMA400 기준 50Hz × 30초 = 1500 샘플이 모이면 생성된다.
 * (BMA400 샘플링 레이트가 100Hz인 경우 슈퍼프레임을 30개 모아야 함)
 *
 * @param accSamples   ACC X/Y/Z 샘플 리스트 (크기 = 1500)
 * @param avgTemp      에포크 동안 수집된 NTC 온도 평균 (°C)
 * @param bvpSamples   BVP(PPG) 샘플 리스트 — PPG 센서 복구 전까지 빈 리스트
 * @param timestampMs  에포크 완성 시점 Unix 밀리초
 */
data class EpochData(
    val accSamples: List<Triple<Float, Float, Float>>,
    val avgTemp: Float,
    val bvpSamples: List<Float> = emptyList(),
    val timestampMs: Long = System.currentTimeMillis()
)
