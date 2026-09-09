package com.leejang.sleeptandard.Potch

import android.content.Context
import android.util.Log
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.sqrt

/**
 * 수면 단계 레이블 (모델 argmax 인덱스와 매핑).
 *
 * 인덱스 순서는 모델 학습 시 클래스 정의와 반드시 일치해야 한다.
 *   0 → WAKE
 *   1 → LIGHT (N1/N2)
 *   2 → DEEP  (N3)
 *   3 → REM
 */
enum class SleepStagePotch {
    WAKE, LIGHT, DEEP, REM, UNKNOWN
}

/**
 * Potch 기반 온디바이스 수면 단계 추론 관리자.
 *
 * ## 모델 입력 텐서 (Float32)
 * | 텐서   | Shape        | 설명                                      |
 * |--------|--------------|-------------------------------------------|
 * | x_cnn  | [1,5,4,1500] | BVP·ACC_X·ACC_Y·ACC_Z 채널, 5 에포크      |
 * | x_dsp  | [1,5,5]      | DSP feature 5개, 5 에포크                 |
 * | temp   | [1,5,1]      | 체온 스칼라, 5 에포크                     |
 *
 * ## PPG 센서 불가 처리
 * PPG가 복구될 때까지 BVP 채널과 PPG 기반 feature는 0.0으로 패딩한다.
 * - x_cnn channel 0 (BVP) → 0.0
 * - x_dsp: mean_hr, ibi_std, mean_sao2, lf_hf_ratio → 0.0
 * - x_dsp: acc_var → ACC 데이터로 직접 계산
 *
 * ## 모델 파일 위치
 * `app/src/main/assets/SleepModel_v1.ptl` 에 넣어야 한다.
 */
class PotchInferenceManager(private val context: Context) {

    companion object {
        private const val TAG = "PotchInferenceManager"

        /** assets 안의 모델 파일 이름 */
        private const val MODEL_FILE = "SleepModel_v1.ptl"

        /** 윈도우 크기 (에포크 수) */
        private const val CONTEXT_SIZE = 5

        /** 에포크당 샘플 수 */
        private const val SAMPLES_PER_EPOCH = 1500

        /** x_cnn 채널 수 (BVP, ACC_X, ACC_Y, ACC_Z) */
        private const val CNN_CHANNELS = 4

        /** x_dsp feature 수 (mean_hr, ibi_std, acc_var, mean_sao2, lf_hf_ratio) */
        private const val DSP_FEATURES = 5
    }

    private var module: Module? = null

    init {
        loadModel()
    }

    // ── 모델 로드 ──────────────────────────────────────────────────────

    private fun loadModel() {
        try {
            val modelPath = copyAssetToFile(MODEL_FILE)
            module = LiteModuleLoader.load(modelPath)
            Log.i(TAG, "✅ Model loaded: $modelPath")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to load model — inference will return UNKNOWN", e)
            module = null
        }
    }

    @Throws(IOException::class)
    private fun copyAssetToFile(assetName: String): String {
        val file = File(context.filesDir, assetName)
        if (file.exists()) file.delete() // 항상 최신 버전으로 갱신
        context.assets.open(assetName).use { input ->
            FileOutputStream(file).use { output ->
                val buf = ByteArray(4 * 1024)
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    output.write(buf, 0, read)
                }
                output.flush()
            }
        }
        return file.absolutePath
    }

    // ── 추론 ───────────────────────────────────────────────────────────

    /**
     * 5 에포크 윈도우를 받아 수면 단계를 반환한다.
     *
     * @param window 크기가 정확히 5인 [EpochData] 리스트 (오래된 것 → 최신 순)
     * @return 예측된 [SleepStagePotch]
     */
    fun predict(window: List<EpochData>): SleepStagePotch {
        val model = module ?: run {
            Log.e(TAG, "Model not loaded. Returning UNKNOWN.")
            return SleepStagePotch.UNKNOWN
        }

        if (window.size != CONTEXT_SIZE) {
            Log.e(TAG, "Window size mismatch: expected $CONTEXT_SIZE, got ${window.size}")
            return SleepStagePotch.UNKNOWN
        }

        return try {
            val xCnn  = buildXCnnTensor(window)
            val xDsp  = buildXDspTensor(window)
            val xTemp = buildTempTensor(window)

            val output = model.forward(
                IValue.from(xCnn),
                IValue.from(xDsp),
                IValue.from(xTemp)
            ).toTensor()

            val scores = output.dataAsFloatArray
            val maxIdx = scores.indices.maxByOrNull { scores[it] } ?: 0

            val stage = when (maxIdx) {
                0 -> SleepStagePotch.WAKE
                1 -> SleepStagePotch.LIGHT
                2 -> SleepStagePotch.DEEP
                3 -> SleepStagePotch.REM
                else -> SleepStagePotch.UNKNOWN
            }

            Log.i(TAG, "📊 Inference: $stage | scores: ${scores.contentToString()}")
            stage

        } catch (e: Exception) {
            Log.e(TAG, "Inference error", e)
            SleepStagePotch.UNKNOWN
        }
    }

    // ── 텐서 조립 ──────────────────────────────────────────────────────

    /**
     * x_cnn 텐서 [1, 5, 4, 1500] 조립.
     *
     * channel 순서: BVP(0), ACC_X(1), ACC_Y(2), ACC_Z(3)
     * BVP = 0.0 (PPG 센서 불가)
     *
     * ## 로컬 정규화 (학습 방식과 동일)
     * 학습 시 환자 1명의 하룻밤 데이터 전체로 fit_transform을 적용했으므로,
     * 앱에서도 현재 5-에포크 윈도우(7500 raw 샘플) 전체로 Z-Score를 계산한다.
     * 1. 5 에포크의 ACC 샘플을 모두 합쳐 7500개 배열 생성
     * 2. 해당 배열로 평균/표준편차 계산 (fit)
     * 3. 정규화 결과로 에포크별 슬라이싱 후 텐서 채우기 (transform)
     */
    private fun buildXCnnTensor(window: List<EpochData>): Tensor {
        // ── 1. 전체 윈도우 ACC 샘플 수집 (최대 7500개) ─────────────────
        val allRawSamples = mutableListOf<Triple<Float, Float, Float>>()
        for (epoch in window) {
            val padded = epoch.accSamples.take(SAMPLES_PER_EPOCH).let { s ->
                if (s.size < SAMPLES_PER_EPOCH)
                    s + List(SAMPLES_PER_EPOCH - s.size) { Triple(0f, 0f, 0f) }
                else s
            }
            allRawSamples.addAll(padded)
        }

        // ── 2. 윈도우 전체로 로컬 Z-Score fit_transform ─────────────────
        // 학습의 StandardScaler.fit_transform()과 동일한 동작
        val normalizedAll = AccScaler.fitTransform(allRawSamples)

        // ── 3. 텐서 flat 배열 채우기 [1, 5, 4, 1500] ───────────────────
        val flat = FloatArray(CONTEXT_SIZE * CNN_CHANNELS * SAMPLES_PER_EPOCH)

        for (epochIdx in 0 until CONTEXT_SIZE) {
            val epochStart = epochIdx * SAMPLES_PER_EPOCH
            val epochSamples = normalizedAll.subList(epochStart, epochStart + SAMPLES_PER_EPOCH)

            for (sampleIdx in 0 until SAMPLES_PER_EPOCH) {
                val (nx, ny, nz) = epochSamples[sampleIdx]

                // ch 0 — BVP: PPG 복구 전까지 0.0
                flat[epochIdx * CNN_CHANNELS * SAMPLES_PER_EPOCH +
                        0 * SAMPLES_PER_EPOCH + sampleIdx] = 0f

                // ch 1 — ACC_X (정규화됨)
                flat[epochIdx * CNN_CHANNELS * SAMPLES_PER_EPOCH +
                        1 * SAMPLES_PER_EPOCH + sampleIdx] = nx

                // ch 2 — ACC_Y (정규화됨)
                flat[epochIdx * CNN_CHANNELS * SAMPLES_PER_EPOCH +
                        2 * SAMPLES_PER_EPOCH + sampleIdx] = ny

                // ch 3 — ACC_Z (정규화됨)
                flat[epochIdx * CNN_CHANNELS * SAMPLES_PER_EPOCH +
                        3 * SAMPLES_PER_EPOCH + sampleIdx] = nz
            }
        }

        return Tensor.fromBlob(
            flat,
            longArrayOf(1, CONTEXT_SIZE.toLong(), CNN_CHANNELS.toLong(), SAMPLES_PER_EPOCH.toLong())
        )
    }

    /**
     * x_dsp 텐서 [1, 5, 5] 조립.
     *
     * feature 순서: mean_hr, ibi_std, acc_var, mean_sao2, lf_hf_ratio
     * acc_var만 계산, 나머지(PPG 기반)는 0.0
     */
    private fun buildXDspTensor(window: List<EpochData>): Tensor {
        val flat = FloatArray(CONTEXT_SIZE * DSP_FEATURES)

        for (epochIdx in 0 until CONTEXT_SIZE) {
            val epoch = window[epochIdx]
            val base = epochIdx * DSP_FEATURES

            flat[base + 0] = 0f                              // mean_hr   (PPG 필요)
            flat[base + 1] = 0f                              // ibi_std   (PPG 필요)
            flat[base + 2] = computeAccVar(epoch.accSamples) // acc_var   (계산 가능)
            flat[base + 3] = 0f                              // mean_sao2 (PPG 필요)
            flat[base + 4] = 0f                              // lf_hf_ratio (PPG 필요)
        }

        return Tensor.fromBlob(flat, longArrayOf(1, CONTEXT_SIZE.toLong(), DSP_FEATURES.toLong()))
    }

    /**
     * temp 텐서 [1, 5, 1] 조립.
     */
    private fun buildTempTensor(window: List<EpochData>): Tensor {
        val flat = FloatArray(CONTEXT_SIZE * 1)
        for (epochIdx in 0 until CONTEXT_SIZE) {
            flat[epochIdx] = window[epochIdx].avgTemp
        }
        return Tensor.fromBlob(flat, longArrayOf(1, CONTEXT_SIZE.toLong(), 1))
    }

    // ── DSP feature 계산 ───────────────────────────────────────────────

    /**
     * ACC 벡터 분산(variance)을 계산한다.
     *
     * acc_var = Var(|acc|) where |acc| = sqrt(x² + y² + z²)
     */
    private fun computeAccVar(samples: List<Triple<Float, Float, Float>>): Float {
        if (samples.isEmpty()) return 0f

        val magnitudes = samples.map { (x, y, z) ->
            sqrt(x * x + y * y + z * z)
        }
        val mean = magnitudes.average().toFloat()
        val variance = magnitudes.map { (it - mean) * (it - mean) }.average().toFloat()
        return variance
    }
}
