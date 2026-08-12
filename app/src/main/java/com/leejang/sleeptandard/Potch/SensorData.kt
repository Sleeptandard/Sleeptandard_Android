package com.leejang.sleeptandard.Potch

import kotlin.math.ln

/**
 * Potch510의 1초 Burst(142B 패킷 8개)를 합쳐 만든 센서 데이터.
 *
 * - Green PPG: uint16 LE 16 samples/packet × 8 = 128 samples (128 Hz)
 * - IMU: int16 LE 6축 × 8 samples/packet × 8 = 64 samples (64 Hz)
 */
data class SensorData(
    val timestamp: Long,
    val sequenceStart: Int,
    val sequenceEnd: Int,
    val packetCount: Int,
    val ntcRaw: Int,
    val batteryRaw: Int,
    val ppgData: ByteArray,
    val imuData: ByteArray
) {
    val hasValidBatteryAdc: Boolean
        get() = batteryRaw in 1..ADC_MAX_RAW

    val hasValidNtcAdc: Boolean
        get() = ntcRaw in 1..ADC_MAX_RAW

    /**
     * 12-bit ADC(3.6V full scale)와 1MΩ + 510kΩ 분압 회로를 반영한 배터리 전압.
     * ADC 값이 0이거나 범위를 벗어나면 NaN을 반환한다.
     */
    val batteryVoltage: Double
        get() {
            if (!hasValidBatteryAdc) return Double.NaN

            val adcVoltage = batteryRaw * ADC_FULL_SCALE_VOLTAGE / ADC_MAX_RAW.toDouble()
            val dividerRatio = (BATTERY_TOP_RESISTOR_OHM + BATTERY_BOTTOM_RESISTOR_OHM) /
                    BATTERY_BOTTOM_RESISTOR_OHM
            return adcVoltage * dividerRatio
        }

    /**
     * 104JT-025(100kΩ @ 25°C, B=4390K) 서미스터의 온도.
     * 회로는 3.3V -> 100kΩ 고정저항 -> ADC node -> NTC -> GND이다.
     */
    val ntcCelsius: Double
        get() {
            if (!hasValidNtcAdc) return Double.NaN

            val vIn = ntcRaw * ADC_FULL_SCALE_VOLTAGE / ADC_MAX_RAW.toDouble()
            // iOS DataProcessor와 동일한 유효 전압 범위.
            if (vIn <= NTC_MIN_VALID_VOLTAGE || vIn >= NTC_MAX_VALID_VOLTAGE) {
                return Double.NaN
            }

            val rNtc = NTC_FIXED_RESISTOR_OHM * vIn / (NTC_SUPPLY_VOLTAGE - vIn)
            if (!rNtc.isFinite() || rNtc <= 0.0) return Double.NaN

            val tKelvin = 1.0 / (
                    (1.0 / NTC_REFERENCE_TEMPERATURE_K) +
                            (1.0 / NTC_BETA_K) * ln(rNtc / NTC_REFERENCE_RESISTANCE_OHM)
                    )

            return tKelvin - 273.15
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SensorData) return false

        return timestamp == other.timestamp &&
                sequenceStart == other.sequenceStart &&
                sequenceEnd == other.sequenceEnd &&
                packetCount == other.packetCount &&
                ntcRaw == other.ntcRaw &&
                batteryRaw == other.batteryRaw &&
                ppgData.contentEquals(other.ppgData) &&
                imuData.contentEquals(other.imuData)
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + sequenceStart
        result = 31 * result + sequenceEnd
        result = 31 * result + packetCount
        result = 31 * result + ntcRaw
        result = 31 * result + batteryRaw
        result = 31 * result + ppgData.contentHashCode()
        result = 31 * result + imuData.contentHashCode()
        return result
    }

    companion object {
        private const val ADC_MAX_RAW = 4095
        private const val ADC_FULL_SCALE_VOLTAGE = 3.6

        private const val BATTERY_TOP_RESISTOR_OHM = 1_000_000.0
        private const val BATTERY_BOTTOM_RESISTOR_OHM = 510_000.0

        private const val NTC_SUPPLY_VOLTAGE = 3.3
        private const val NTC_MIN_VALID_VOLTAGE = 0.05
        private const val NTC_MAX_VALID_VOLTAGE = 3.25
        private const val NTC_FIXED_RESISTOR_OHM = 100_000.0
        private const val NTC_REFERENCE_RESISTANCE_OHM = 100_000.0
        private const val NTC_REFERENCE_TEMPERATURE_K = 298.15
        private const val NTC_BETA_K = 4390.0
    }
}
