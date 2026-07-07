package com.leejang.sleeptandard.Potch

import kotlin.math.ln

data class SensorData(
    val timestamp: Long,
    val ntcRaw: Int,
    val batteryRaw: Int,
    val ppgData: ByteArray, // 600 bytes, MAX30102
    val imuData: ByteArray // 600 bytes, BMA400
) {
    val batteryVoltage: Double
        get() {
            // iOS DataProcessor.swift와 동일한 계산식.
            //
            // SAADC: Vref=0.6V, Gain=1/6 -> full-scale = 3.6V
            // Battery channel = VDDHDIV5 -> ADC에는 VBAT / 5가 들어옴
            //
            // Vadc  = raw * 3.6 / 4096
            // Vbatt = Vadc * 5
            //       = raw * 18.0 / 4096
            return batteryRaw * 18.0 / 4096.0
        }

    val ntcCelsius: Double
        get() {
            // iOS DataProcessor.swift와 동일한 계산식.
            //
            // SAADC: Vref=0.6V, Gain=1/6 -> full-scale = 3.6V
            val vIn = ntcRaw * 3.6 / 4096.0

            // 회로:
            // 1.8V -> 100k 고정저항 -> AIN -> NTC -> GND
            //
            // vIn = 1.8 * R_ntc / (100k + R_ntc)
            // R_ntc = 100k * vIn / (1.8 - vIn)
            if (vIn <= 0.0 || vIn >= 1.8) return -999.0

            val rNtc = 100000.0 * vIn / (1.8 - vIn)

            // B-parameter equation
            val t0 = 298.15 // 25°C in Kelvin
            val r0 = 100000.0 // 100k at 25°C
            val b = 4390.0

            val tKelvin =
                1.0 / ((1.0 / t0) + (1.0 / b) * ln(rNtc / r0))

            return tKelvin - 273.15
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SensorData) return false

        return timestamp == other.timestamp &&
                ntcRaw == other.ntcRaw &&
                batteryRaw == other.batteryRaw &&
                ppgData.contentEquals(other.ppgData) &&
                imuData.contentEquals(other.imuData)
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + ntcRaw
        result = 31 * result + batteryRaw
        result = 31 * result + ppgData.contentHashCode()
        result = 31 * result + imuData.contentHashCode()
        return result
    }
}