package com.leejang.sleeptandard.Potch

import kotlin.math.ln

data class SensorData(
    val timestamp: Long,
    val ntcRaw: Int,
    val batteryRaw: Int,
    val imuData: ByteArray // 600 bytes, BMA400
) {
    val batteryVoltage: Double
        get() = batteryRaw * (3.791 / 2575.0)

    val ntcCelsius: Double
        get() {
            // vIn = raw * (2.4 / 4096)
            val vIn = ntcRaw * 2.4 / 4096.0

            // B타입: 1.8V -> 100k -> AIN5 -> NTC -> GND
            // vIn = 1.8 * R_ntc / (100k + R_ntc)
            // R_ntc = 100k * vIn / (1.8 - vIn)
            if (vIn <= 0.0 || vIn >= 1.8) return -999.0

            val rNtc = 100000.0 * vIn / (1.8 - vIn)

            // B-parameter equation
            val t0 = 298.15 // 25°C
            val r0 = 100000.0 // 104JT, 100k
            val b = 4390.0

            val tKelvin = 1.0 / ((1.0 / t0) + (1.0 / b) * ln(rNtc / r0))
            return tKelvin - 273.15
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SensorData) return false

        return timestamp == other.timestamp &&
                ntcRaw == other.ntcRaw &&
                batteryRaw == other.batteryRaw &&
                imuData.contentEquals(other.imuData)
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + ntcRaw
        result = 31 * result + batteryRaw
        result = 31 * result + imuData.contentHashCode()
        return result
    }
}