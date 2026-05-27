package com.leejang.sleeptandard.Potch

import android.util.Log

/**
 * 슬라이딩 윈도우 — 최근 5 에포크를 유지한다.
 *
 * 에포크가 추가될 때마다 오래된 것을 버리고 최신 5개만 유지한다.
 * 5개가 갖춰지면 [onWindowReady]를 호출한다.
 *
 * @param windowSize    유지할 에포크 수 (기본 5 = 과거 2.5분)
 * @param onWindowReady 윈도우가 완성될 때마다 호출되는 콜백
 */
class PotchWindowBuffer(
    private val windowSize: Int = 5,
    private val onWindowReady: (List<EpochData>) -> Unit
) {
    companion object {
        private const val TAG = "PotchWindowBuffer"
    }

    private val buffer = ArrayDeque<EpochData>(windowSize)

    /**
     * 새 에포크를 윈도우에 추가한다.
     *
     * - 윈도우가 가득 차면 가장 오래된 에포크를 제거
     * - 추가 후 윈도우가 [windowSize]개이면 [onWindowReady] 호출
     */
    fun addEpoch(epoch: EpochData) {
        if (buffer.size >= windowSize) {
            buffer.removeFirst()
        }
        buffer.addLast(epoch)

        Log.d(TAG, "Window: ${buffer.size}/$windowSize epochs")

        if (buffer.size == windowSize) {
            Log.i(TAG, "✅ Window full! Triggering inference.")
            onWindowReady(buffer.toList())
        }
    }

    /** 버퍼를 초기화한다. */
    fun reset() {
        buffer.clear()
        Log.d(TAG, "WindowBuffer reset.")
    }

    /** 현재 버퍼에 쌓인 에포크 수. */
    val currentSize: Int get() = buffer.size
}
