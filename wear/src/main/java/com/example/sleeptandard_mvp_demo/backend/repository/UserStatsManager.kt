package com.example.sleeptandard_mvp_demo.backend.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlin.math.sqrt

class UserStatsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("user_hr_stats", Context.MODE_PRIVATE)
    
    // Welford 알고리즘 상태 (HR)
    private var count: Long = 0
    private var mean: Double = 0.0
    private var m2: Double = 0.0

    // Welford 알고리즘 상태 (RMSSD)
    private var rmssdCount: Long = 0
    private var rmssdMean: Double = 0.0
    private var rmssdM2: Double = 0.0

    // [핵심] RMSSD 베이스라인 고정 상태 (초반 30분 = 60개 샘플 수집 후 고정)
    private var rmssdBaselineFrozen: Boolean = false
    private var rmssdBaselineMean: Double = 0.0  // 고정된 평균
    private var rmssdBaselineStd: Double = 0.0   // 고정된 표준편차
    private val BASELINE_SAMPLES = 60L // 30초 간격 * 60회 = 30분

    private val MIN_HR_BPM = 30f
    private val MAX_HR_BPM = 220f
    private val MIN_RMSSD = 0f
    private val MAX_RMSSD = 500f // 일반적인 RMSSD 범위

    // [중요] 비트 단위 저장을 위한 키 (HR)
    private val KEY_COUNT = "KEY_COUNT"
    private val KEY_MEAN_BITS = "KEY_MEAN_BITS"
    private val KEY_M2_BITS = "KEY_M2_BITS"
    
    // [중요] 비트 단위 저장을 위한 키 (RMSSD)
    private val KEY_RMSSD_COUNT = "KEY_RMSSD_COUNT"
    private val KEY_RMSSD_MEAN_BITS = "KEY_RMSSD_MEAN_BITS"
    private val KEY_RMSSD_M2_BITS = "KEY_RMSSD_M2_BITS"
    
    // [핵심] 고정된 베이스라인 저장을 위한 키
    private val KEY_RMSSD_BASELINE_FROZEN = "KEY_RMSSD_BASELINE_FROZEN"
    private val KEY_RMSSD_BASELINE_MEAN_BITS = "KEY_RMSSD_BASELINE_MEAN_BITS"
    private val KEY_RMSSD_BASELINE_STD_BITS = "KEY_RMSSD_BASELINE_STD_BITS"
    
    // 레거시 호환용 키 (마이그레이션 대비)
    private val KEY_MEAN_LEGACY = "KEY_MEAN"
    
    companion object {
        private const val TAG = "UserStatsManager"
    }

    init {
        loadStats()
    }

    @Synchronized
    fun update(hrValue: Float) {
        // [안정성] 이상치 및 무한대 값 방어
        if (!hrValue.isFinite() || hrValue < MIN_HR_BPM || hrValue > MAX_HR_BPM) return

        count++
        val delta = hrValue - mean
        mean += delta / count
        val delta2 = hrValue - mean
        m2 += delta * delta2
        
        saveStats()
    }

    @Synchronized
    fun getUserMean(): Float = if (count > 0) mean.toFloat() else 65.0f

    @Synchronized
    fun getUserStd(): Float {
        if (count < 2) return 10.0f
        val variance = m2 / (count - 1)
        return sqrt(variance).toFloat()
    }

    @Synchronized
    fun updateRmssd(rmssdValue: Float) {
        // [안정성] 이상치 및 무한대 값 방어
        if (!rmssdValue.isFinite() || rmssdValue < MIN_RMSSD || rmssdValue > MAX_RMSSD) return

        // [핵심] 베이스라인이 이미 고정되었으면 더 이상 업데이트하지 않음
        if (rmssdBaselineFrozen) return

        rmssdCount++
        val delta = rmssdValue - rmssdMean
        rmssdMean += delta / rmssdCount
        val delta2 = rmssdValue - rmssdMean
        rmssdM2 += delta * delta2
        
        // [핵심] 베이스라인 수집 완료 시 고정 (60개 샘플 = 30분)
        if (rmssdCount >= BASELINE_SAMPLES) {
            freezeRmssdBaseline()
        }
        
        saveStats()
    }
    
    @Synchronized
    private fun freezeRmssdBaseline() {
        if (rmssdBaselineFrozen) return
        
        rmssdBaselineFrozen = true
        rmssdBaselineMean = rmssdMean
        
        // 표준편차 계산
        if (rmssdCount >= 2) {
            val variance = rmssdM2 / (rmssdCount - 1)
            rmssdBaselineStd = sqrt(variance)
        } else {
            rmssdBaselineStd = 15.0 // 기본값
        }
        
        Log.d(TAG, "RMSSD Baseline Frozen: mean=${rmssdBaselineMean}, std=${rmssdBaselineStd}, samples=$rmssdCount")
        saveStats()
    }

    @Synchronized
    fun getUserBaseRmssd(): Float {
        // [핵심] 베이스라인이 고정되었으면 고정된 값 반환, 아니면 현재 평균 반환
        return if (rmssdBaselineFrozen) {
            rmssdBaselineMean.toFloat()
        } else {
            if (rmssdCount > 0) rmssdMean.toFloat() else 30.0f // 기본값: 일반적인 RMSSD 평균
        }
    }

    @Synchronized
    fun getUserStdRmssd(): Float {
        // [핵심] 베이스라인이 고정되었으면 고정된 값 반환, 아니면 현재 표준편차 반환
        return if (rmssdBaselineFrozen) {
            rmssdBaselineStd.toFloat()
        } else {
            if (rmssdCount < 2) return 15.0f // 기본값: 일반적인 RMSSD 표준편차
            val variance = rmssdM2 / (rmssdCount - 1)
            sqrt(variance).toFloat()
        }
    }

    private fun saveStats() {
        prefs.edit().apply {
            // HR 통계 저장
            putLong(KEY_COUNT, count)
            // [정밀도] Double의 비트 패턴을 그대로 저장하여 정밀도 유지
            putLong(KEY_MEAN_BITS, mean.toBits())
            putLong(KEY_M2_BITS, m2.toBits())
            
            // RMSSD 통계 저장
            putLong(KEY_RMSSD_COUNT, rmssdCount)
            putLong(KEY_RMSSD_MEAN_BITS, rmssdMean.toBits())
            putLong(KEY_RMSSD_M2_BITS, rmssdM2.toBits())
            
            // [핵심] 고정된 베이스라인 저장
            putBoolean(KEY_RMSSD_BASELINE_FROZEN, rmssdBaselineFrozen)
            if (rmssdBaselineFrozen) {
                putLong(KEY_RMSSD_BASELINE_MEAN_BITS, rmssdBaselineMean.toBits())
                putLong(KEY_RMSSD_BASELINE_STD_BITS, rmssdBaselineStd.toBits())
            }
            
            apply()
        }
    }

    private fun loadStats() {
        // HR 통계 로드
        count = prefs.getLong(KEY_COUNT, 0)
        
        // [호환성] 신규 키가 없으면 레거시(Float) 키에서 복구 시도
        mean = if (prefs.contains(KEY_MEAN_BITS)) {
            Double.fromBits(prefs.getLong(KEY_MEAN_BITS, 0))
        } else {
            prefs.getFloat(KEY_MEAN_LEGACY, 0f).toDouble()
        }

        m2 = if (prefs.contains(KEY_M2_BITS)) {
            Double.fromBits(prefs.getLong(KEY_M2_BITS, 0))
        } else {
            prefs.getFloat("KEY_M2", 0f).toDouble()
        }
        
        // RMSSD 통계 로드
        rmssdCount = prefs.getLong(KEY_RMSSD_COUNT, 0)
        rmssdMean = if (prefs.contains(KEY_RMSSD_MEAN_BITS)) {
            Double.fromBits(prefs.getLong(KEY_RMSSD_MEAN_BITS, 0))
        } else {
            30.0 // 기본값
        }
        
        rmssdM2 = if (prefs.contains(KEY_RMSSD_M2_BITS)) {
            Double.fromBits(prefs.getLong(KEY_RMSSD_M2_BITS, 0))
        } else {
            0.0
        }
        
        // [핵심] 고정된 베이스라인 로드
        rmssdBaselineFrozen = prefs.getBoolean(KEY_RMSSD_BASELINE_FROZEN, false)
        if (rmssdBaselineFrozen) {
            rmssdBaselineMean = if (prefs.contains(KEY_RMSSD_BASELINE_MEAN_BITS)) {
                Double.fromBits(prefs.getLong(KEY_RMSSD_BASELINE_MEAN_BITS, 0))
            } else {
                rmssdMean // 폴백: 현재 평균 사용
            }
            
            rmssdBaselineStd = if (prefs.contains(KEY_RMSSD_BASELINE_STD_BITS)) {
                Double.fromBits(prefs.getLong(KEY_RMSSD_BASELINE_STD_BITS, 0))
            } else {
                // 폴백: 현재 통계로부터 계산
                if (rmssdCount >= 2) {
                    val variance = rmssdM2 / (rmssdCount - 1)
                    sqrt(variance)
                } else {
                    15.0 // 기본값
                }
            }
            
            Log.d(TAG, "RMSSD Baseline Loaded: mean=${rmssdBaselineMean}, std=${rmssdBaselineStd}")
        }
    }
}