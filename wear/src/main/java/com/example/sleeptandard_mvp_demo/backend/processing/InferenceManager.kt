package com.example.sleeptandard_mvp_demo.backend.processing

import android.content.Context
import android.util.Log
import com.example.sleeptandard_mvp_demo.backend.model.SleepStage
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class InferenceManager(private val context: Context) {

    private var module: Module? = null
    private val MODEL_NAME = "model_plan_b_prime.ptl"

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            // 1. Assets에 있는 모델 파일을 내부 저장소로 강제 복사 (필수)
            val modelPath = forceCopyAssetToFile(context, MODEL_NAME)

            // 2. PyTorch Lite 모듈 로드
            module = LiteModuleLoader.load(modelPath)
            Log.i(TAG, "✅ PyTorch Model loaded successfully from $modelPath")

        } catch (e: Exception) {
            // 모델 로드 실패 시 예외를 던지거나 로그를 남김 (Mock으로 전환하지 않음)
            Log.e(TAG, "❌ FATAL: Failed to load PyTorch model.", e)
            module = null
        }
    }

    // [필수] Assets -> 내부 저장소 복사 함수 (이게 없으면 bytecode.pkl 오류 남)
    @Throws(IOException::class)
    private fun forceCopyAssetToFile(context: Context, assetName: String): String {
        val file = File(context.filesDir, assetName)

        // 항상 최신 파일을 쓰기 위해 기존 파일이 있으면 삭제하고 다시 복사
        if (file.exists()) {
            file.delete()
        }

        context.assets.open(assetName).use { inputStream ->
            FileOutputStream(file).use { outputStream ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                }
                outputStream.flush()
            }
        }
        return file.absolutePath
    }

    fun predict(accBuffer: List<Triple<Float, Float, Float>>, hrFeatures: FloatArray): SleepStage {
        // 모델이 없으면 추론 불가 -> UNKNOWN 반환 (가짜 로직 없음)
        val model = module ?: run {
            Log.e(TAG, "Skipping inference: Model is not loaded.")
            return SleepStage.UNKNOWN
        }

        try {
            // 1. Prepare ACC Tensor [1, 750, 3] -> Flattened
            val flatAcc = FloatArray(accBuffer.size * 3)
            for (i in accBuffer.indices) {
                flatAcc[i * 3] = accBuffer[i].first
                flatAcc[i * 3 + 1] = accBuffer[i].second
                flatAcc[i * 3 + 2] = accBuffer[i].third
            }
            val accTensor = Tensor.fromBlob(flatAcc, longArrayOf(1, 750, 3))

            // 2. Prepare HR Tensor [1, 5]
            val hrTensor = Tensor.fromBlob(hrFeatures, longArrayOf(1, 5))

            // 3. Forward Pass (Real Inference)
            val outputTensor = model.forward(IValue.from(accTensor), IValue.from(hrTensor)).toTensor()
            val scores = outputTensor.dataAsFloatArray

            // 4. ArgMax
            val maxScoreIndex = scores.indices.maxByOrNull { scores[it] } ?: 0

            val resultStage = when (maxScoreIndex) {
                0 -> SleepStage.WAKE
                1 -> SleepStage.LIGHT
                2 -> SleepStage.DEEP
                3 -> SleepStage.REM
                else -> SleepStage.UNKNOWN
            }

            Log.d(TAG, "Predict: $resultStage (Raw scores: ${scores.contentToString()})")
            return resultStage

        } catch (e: Exception) {
            Log.e(TAG, "Inference Calculation Error", e)
            return SleepStage.UNKNOWN
        }
    }

    companion object {
        private const val TAG = "InferenceManager"
    }
}