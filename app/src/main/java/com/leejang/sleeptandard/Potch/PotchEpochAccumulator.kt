package com.leejang.sleeptandard.Potch

import android.util.Log

/**
 * Potch SuperFrame 스트림을 30초 단위 에포크로 묶는 누적기.
 *
 * ## 동작 원리
 * - Potch BMA400 기준: **100Hz** 설정 → SuperFrame 1개 = ACC 100 샘플 = 1초
 * - 30초 에포크 = **30 SuperFrame** = 3000 ACC raw 샘플
 * - 모델 입력은 **50Hz 기준 1500샘플**이므로 2배 다운샘플링(짝수 인덱스만) 적용
 *
 * ## BMA400 샘플 파싱
 * - 600 bytes / 6 bytes per sample = 100 samples
 * - 각 축: Little-Endian 2바이트 → 12-bit signed int → g 단위 변환
 * - 민감도 기본값: ±2g (BMA400 기본 레인지), 1 LSB = 1/512 g
 *
 * @param onEpochReady 에포크가 완성될 때마다 호출되는 콜백
 */
class PotchEpochAccumulator(
    private val onEpochReady: (EpochData) -> Unit
) {
    companion object {
        private const val TAG = "PotchEpochAccumulator"

        /** SuperFrame당 ACC 샘플 수 (BMA400 FIFO: 600 bytes / 6 bytes) */
        private const val SAMPLES_PER_FRAME = 100

        /**
         * 에포크 완성에 필요한 SuperFrame 수.
         * BMA400 100Hz 기준: 30초 × 100 sample/s ÷ 100 sample/frame = 30
         */
        private const val FRAMES_PER_EPOCH = 30

        /** raw 에포크당 총 ACC 샘플 수 (100Hz × 30s = 3000) */
        private const val RAW_SAMPLES_PER_EPOCH = SAMPLES_PER_FRAME * FRAMES_PER_EPOCH // 3000

        /**
         * 다운샘플링 비율 (100Hz → 50Hz).
         * 짝수 인덱스(0, 2, 4, …)만 취하여 절반으로 줄인다.
         */
        private const val DOWNSAMPLE_FACTOR = 2

        /** 모델 입력 ACC 샘플 수 (50Hz × 30s = 1500) */
        private const val MODEL_SAMPLES_PER_EPOCH = RAW_SAMPLES_PER_EPOCH / DOWNSAMPLE_FACTOR // 1500

        /**
         * BMA400 ±2g 레인지에서 1 LSB = 1/512 g.
         * 레인지가 다르면 이 값을 교체하세요:
         *   ±2g  → 1.0f / 512.0f
         *   ±4g  → 1.0f / 256.0f
         *   ±8g  → 1.0f / 128.0f
         *   ±16g → 1.0f / 64.0f
         */
        private const val BMA400_SENSITIVITY = 1.0f / 512.0f
    }

    /** 현재 에포크에 누적 중인 ACC 샘플 (100Hz raw) */
    private val accBuffer = mutableListOf<Triple<Float, Float, Float>>()

    /** 현재 에포크 동안 수집된 NTC 온도 값들 */
    private val tempBuffer = mutableListOf<Float>()

    /** 누적된 SuperFrame 수 (디버그용) */
    private var frameCount = 0

    /**
     * SensorData 하나를 처리한다.
     *
     * PotchDataProcessor에서 파싱 완료된 [SensorData]가 들어올 때마다 호출한다.
     * 에포크가 완성되면 [onEpochReady]를 호출하고 버퍼를 초기화한다.
     */
    fun process(data: SensorData) {
        // 1. IMU 600 bytes → ACC 100 샘플 파싱
        val parsedSamples = parseImuBytes(data.imuData)
        accBuffer.addAll(parsedSamples)

        // 2. 온도 누적
        val tempC = data.ntcCelsius.toFloat()
        if (tempC > -900f) { // 파싱 실패(-999)는 제외
            tempBuffer.add(tempC)
        }

        frameCount++

        Log.d(TAG, "Frame $frameCount/$FRAMES_PER_EPOCH | raw ACC: ${accBuffer.size}/$RAW_SAMPLES_PER_EPOCH")

        // 3. 에포크 완성 확인 (30 SuperFrame = 3000 raw 샘플)
        if (accBuffer.size >= RAW_SAMPLES_PER_EPOCH) {
            val rawEpoch = accBuffer.take(RAW_SAMPLES_PER_EPOCH)

            // 4. 100Hz → 50Hz 다운샘플링 (짝수 인덱스만 취함 → 1500샘플)
            val downsampledAcc = rawEpoch.filterIndexed { idx, _ -> idx % DOWNSAMPLE_FACTOR == 0 }

            val avgTemp = if (tempBuffer.isNotEmpty()) tempBuffer.average().toFloat() else 0f

            Log.i(TAG, "✅ Epoch ready! raw: ${rawEpoch.size} → downsampled: ${downsampledAcc.size}, AvgTemp: $avgTemp°C")

            onEpochReady(
                EpochData(
                    accSamples = downsampledAcc, // 1500 샘플 (50Hz)
                    avgTemp = avgTemp,
                    timestampMs = System.currentTimeMillis()
                )
            )

            // 버퍼 초기화 (남은 샘플은 다음 에포크로 이월)
            val remaining = accBuffer.drop(RAW_SAMPLES_PER_EPOCH)
            accBuffer.clear()
            accBuffer.addAll(remaining)
            tempBuffer.clear()
            frameCount = 0
        }
    }

    /**
     * BMA400 FIFO raw bytes → ACC 샘플 리스트 파싱.
     *
     * 구조: [X_LSB, X_MSB, Y_LSB, Y_MSB, Z_LSB, Z_MSB] × 100
     * - 2바이트 Little-Endian → 16-bit signed int
     * - 상위 12비트만 유효 → arithmetic right shift 4
     * - BMA400_SENSITIVITY 곱해 g 단위로 변환
     */
    private fun parseImuBytes(imuData: ByteArray): List<Triple<Float, Float, Float>> {
        val samples = mutableListOf<Triple<Float, Float, Float>>()
        val bytesPerSample = 6
        val maxSamples = imuData.size / bytesPerSample

        for (i in 0 until maxSamples) {
            val offset = i * bytesPerSample

            val rawX = ((imuData[offset + 1].toInt() shl 8) or
                    (imuData[offset].toInt() and 0xFF)).toShort().toInt() shr 4
            val rawY = ((imuData[offset + 3].toInt() shl 8) or
                    (imuData[offset + 2].toInt() and 0xFF)).toShort().toInt() shr 4
            val rawZ = ((imuData[offset + 5].toInt() shl 8) or
                    (imuData[offset + 4].toInt() and 0xFF)).toShort().toInt() shr 4

            val gX = rawX * BMA400_SENSITIVITY
            val gY = rawY * BMA400_SENSITIVITY
            val gZ = rawZ * BMA400_SENSITIVITY

            samples.add(Triple(gX, gY, gZ))
        }

        return samples
    }

    /** 버퍼를 초기화한다 (연결 해제 시 등). */
    fun reset() {
        accBuffer.clear()
        tempBuffer.clear()
        frameCount = 0
        Log.d(TAG, "Accumulator reset.")
    }
}

