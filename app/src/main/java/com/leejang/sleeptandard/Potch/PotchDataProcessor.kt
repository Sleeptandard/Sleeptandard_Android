package com.leejang.sleeptandard.Potch

import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * PotchDataProcessor가 현재까지 처리한 데이터 상태를 담는 데이터 클래스.
 *
 * BLE 통신으로 들어온 raw byte를 파싱한 결과와,
 * 패킷 오류 정보, 마지막 로그 등을 UI에서 볼 수 있게 저장한다.
 */

data class PacketErrorLog(
    val type: String,
    val message: String,
    val fragCounter: Int? = null,
    val timestampMs: Long = System.currentTimeMillis()
)

data class DataProcessorState(
    // 마지막으로 정상 파싱된 센서 데이터
    // 아직 수신된 데이터가 없거나 파싱 전이면 null
    val lastParsedData: SensorData? = null,

    // CRC 검증 실패 횟수
    // 패킷 데이터가 손상되었을 가능성을 확인하기 위한 누적 카운트
    val crcErrorCount: Int = 0,

    // Fragment 순서가 예상과 다르게 들어온 횟수
    // BLE notification 누락 또는 순서 꼬임을 감지하기 위한 누적 카운트
    val missingSequenceErrors: Int = 0,

    // 마지막 처리 상태를 사람이 읽을 수 있게 저장하는 로그 메시지
    // 예: "CRC OK!", "Length Drop", "Seq Drop" 등
    val lastLog: String = "No data yet",

    // 전체 수신된 BLE mini packet 수
    val totalMiniPackets: Int = 0,

    // 정상 형식으로 처리된 mini packet 수
    val validMiniPackets: Int = 0,

    // 손상된 mini packet 또는 super frame 수
    val damagedPacketCount: Int = 0,

    // counter 차이로 추정한 손실 mini packet 수
    val estimatedLostPacketCount: Int = 0,

    // 완성되어 파싱된 super frame 수
    val parsedSuperFrameCount: Int = 0,

    // 최근 패킷 오류 내역
    val recentPacketErrors: List<PacketErrorLog> = emptyList(),

    // 마지막으로 수신한 fragment counter
    val lastFragCounter: Int? = null,

    // 다음에 기대하는 fragment counter
    val expectedFragCounter: Int? = null
)
/**
 * Potch BLE 기기에서 들어오는 raw byte 데이터를 실제 센서 데이터로 변환하는 클래스.
 *
 * Potch 기기는 센서 데이터를 한 번에 1212 bytes짜리 Super Frame으로 구성하지만,
 * BLE notification으로는 204 bytes 단위 Fragment로 나눠서 보낸다.
 *
 * 이 클래스의 역할:
 * 1. 204 bytes Fragment를 수신한다.
 * 2. Fragment header를 검사한다.
 * 3. Fragment 순서를 확인한다.
 * 4. Payload를 buffer에 누적한다.
 * 5. 1212 bytes Super Frame이 완성되면 파싱한다.
 * 6. NTC, timestamp, battery, IMU, CRC 정보를 추출한다.
 */
class PotchDataProcessor {

    /**
     * 내부에서 수정 가능한 상태 값.
     *
     * UI에서는 직접 수정하면 안 되기 때문에 private으로 숨기고,
     * 외부에는 아래 state만 공개한다.
     */
    private val _state = MutableStateFlow(DataProcessorState())

    /**
     * 외부에서 관찰할 수 있는 읽기 전용 상태.
     *
     * Compose 화면이나 ViewModel에서 collectAsState()로 받아서
     * 센서값, 오류 횟수, 로그를 표시할 수 있다.
     */
    val state: StateFlow<DataProcessorState> = _state

    /**
     * Fragment payload를 임시로 쌓아두는 버퍼.
     *
     * BLE로 들어오는 데이터는 204 bytes 단위이고,
     * 앞의 2 bytes는 mini header이므로 실제 payload는 202 bytes다.
     *
     * payload를 계속 모아서 1212 bytes가 되면 하나의 Super Frame으로 파싱한다.
     */
    private val buffer = ArrayDeque<Byte>()

    /**
     * 다음에 들어와야 할 Fragment counter 값.
     *
     * Fragment mini header 안에는 12-bit counter가 들어있다.
     * 이 값을 이용해서 패킷 누락 또는 순서 꼬임을 감지한다.
     *
     * null이면 아직 기준 counter가 없는 초기 상태다.
     */
    private var expectedFragCounter: Int? = null

    /**
     * Potch 기기에서 한 번에 보내는 BLE Fragment 크기.
     *
     * 구조:
     * - 2 bytes: mini header
     * - 202 bytes: payload
     *
     * 총 204 bytes
     */
    private val fragmentSize = 204

    /**
     * 하나의 완성된 센서 데이터 프레임 크기.
     *
     * Fragment payload 202 bytes × 6개 = 1212 bytes
     *
     * 이 크기만큼 buffer에 모이면 parseSuperFrame()을 호출한다.
     */
    private val superFrameSize = 1212

    /**
     * BLE notification으로 들어온 raw byte 배열을 처리하는 함수.
     *
     * PotchBleManager의 onCharacteristicChanged()에서 호출된다.
     *
     * 처리 흐름:
     * 1. 수신 길이 확인
     * 2. mini header 파싱
     * 3. header prefix 확인
     * 4. fragment counter 순서 확인
     * 5. payload 추출
     * 6. frame 시작점 0xAA 0xAA 확인
     * 7. buffer에 payload 누적
     * 8. 1212 bytes가 모이면 Super Frame 파싱
     */
    @Synchronized
    fun processIncomingData(data: ByteArray) {
        updateLog("Rcv length: ${data.size}")

        _state.update {
            it.copy(totalMiniPackets = it.totalMiniPackets + 1)
        }

        if (data.size != fragmentSize) {
            val msg = "Length Drop: expected $fragmentSize, got ${data.size}"

            _state.update {
                it.copy(damagedPacketCount = it.damagedPacketCount + 1)
            }

            addPacketError(
                type = "LENGTH",
                message = msg
            )
            return
        }

        val miniHeader =
            ((data[0].toInt() and 0xFF) shl 8) or
                    (data[1].toInt() and 0xFF)

        val headerPrefix = (miniHeader shr 12) and 0xF

        if (headerPrefix != 0x5) {
            val msg = "Header Prefix Drop: expected 0x5, got 0x${headerPrefix.toString(16)}"

            _state.update {
                it.copy(damagedPacketCount = it.damagedPacketCount + 1)
            }

            addPacketError(
                type = "MINI_HEADER",
                message = msg
            )

            buffer.clear()
            return
        }

        val fragCounter = miniHeader and 0x0FFF

        expectedFragCounter?.let { expected ->
            if (fragCounter != expected) {
                val distance = counterDistance(expected, fragCounter)

                // expected 다음에 actual이 왔다면 distance만큼 counter가 건너뛴 것.
                // 예: expected=10, actual=13이면 10,11,12가 안 왔다고 보고 3개 손실 추정.
                val lostCount = if (distance in 1..4095) distance else 1

                val msg = "Seq Drop. Exp: $expected, Got: $fragCounter, Lost≈$lostCount"

                _state.update {
                    it.copy(
                        missingSequenceErrors = it.missingSequenceErrors + 1,
                        estimatedLostPacketCount = it.estimatedLostPacketCount + lostCount
                    )
                }

                addPacketError(
                    type = "SEQUENCE",
                    message = msg,
                    fragCounter = fragCounter
                )

                buffer.clear()
            }
        }

        expectedFragCounter = (fragCounter + 1) and 0x0FFF

        _state.update {
            it.copy(
                validMiniPackets = it.validMiniPackets + 1,
                lastFragCounter = fragCounter,
                expectedFragCounter = expectedFragCounter
            )
        }

        val payload = data.copyOfRange(2, data.size)

        if (buffer.isEmpty()) {
            if (
                payload.size < 2 ||
                (payload[0].toInt() and 0xFF) != 0xAA ||
                (payload[1].toInt() and 0xFF) != 0xAA
            ) {
                val msg = "Syncing... waiting for Super Header 0xAAAA"

                addPacketError(
                    type = "SYNC",
                    message = msg,
                    fragCounter = fragCounter
                )

                return
            }
        }

        payload.forEach { buffer.addLast(it) }

        if (buffer.size >= superFrameSize) {
            val superFrame = ByteArray(superFrameSize) {
                buffer.removeFirst()
            }

            parseSuperFrame(superFrame)
        }
    }

    /**
     * 데이터 파서 상태를 초기화한다.
     *
     * 사용 예:
     * - 개발자 화면에서 "수신 데이터 초기화" 버튼을 누를 때
     * - 연결이 완전히 새로 시작될 때
     */
    @Synchronized
    fun reset() {
        // 누적 중이던 payload 제거
        buffer.clear()

        // fragment counter 기준 초기화
        expectedFragCounter = null

        // UI 상태도 초기값으로 되돌림
        _state.value = DataProcessorState()
    }

    /**
     * 1212 bytes짜리 Super Frame을 실제 센서 데이터로 파싱한다.
     *
     * Super Frame 구조:
     * - [0..1]    : Super Header, 0xAA 0xAA
     * - [2..3]    : NTC raw
     * - [4..7]    : Timestamp, little endian
     * - [8..9]    : Battery raw
     * - [10..11]  : CRC
     * - [612..1211]: IMU data, 600 bytes
     */
    private fun parseSuperFrame(data: ByteArray) {
        /**
         * Super Header 검사.
         *
         * 완성된 프레임의 시작은 반드시 0xAA 0xAA여야 한다.
         */
        if (
            (data[0].toInt() and 0xFF) != 0xAA ||
            (data[1].toInt() and 0xFF) != 0xAA
        ) {
            val msg = "Super Header Drop: 0x%02X%02X".format(
                data[0].toInt() and 0xFF,
                data[1].toInt() and 0xFF
            )

            _state.update {
                it.copy(damagedPacketCount = it.damagedPacketCount + 1)
            }

            addPacketError(
                type = "SUPER_HEADER",
                message = msg
            )

            buffer.clear()
            return
        }

        /**
         * 2. NTC, Index 2~3
         *
         * NTC 온도 센서의 raw ADC 값.
         *
         * data[2]의 하위 4비트와 data[3] 전체를 합쳐 12-bit 값으로 만든다.
         */
        val ntcRaw =
            ((data[2].toInt() and 0x0F) shl 8) or
                    (data[3].toInt() and 0xFF)

        /**
         * 3. Timestamp, Index 4~7
         *
         * 펌웨어에서 보낸 시간 값.
         * little endian으로 저장되어 있으므로 낮은 바이트부터 조립한다.
         */
        val timestamp =
            ((data[4].toLong() and 0xFFL)) or
                    ((data[5].toLong() and 0xFFL) shl 8) or
                    ((data[6].toLong() and 0xFFL) shl 16) or
                    ((data[7].toLong() and 0xFFL) shl 24)

        /**
         * 4. Battery, Index 8~9
         *
         * 배터리 전압 측정용 raw ADC 값.
         *
         * NTC와 마찬가지로 data[8]의 하위 4비트와 data[9]를 합쳐
         * 12-bit 값으로 만든다.
         */
        val batteryRaw =
            ((data[8].toInt() and 0x0F) shl 8) or
                    (data[9].toInt() and 0xFF)

        /**
         * 5. CRC Verification, Index 10~11
         *
         * 수신된 CRC 값.
         * 여기서는 big endian으로 조립한다.
         */
        val receivedCrc =
            ((data[10].toInt() and 0xFF) shl 8) or
                    (data[11].toInt() and 0xFF)

        /**
         * CRC 계산용 데이터 복사본.
         *
         * CRC 필드 자체는 계산에서 제외해야 하므로
         * data[10], data[11]을 0으로 만든 뒤 CRC를 계산한다.
         */
        val crcData = data.copyOf()
        crcData[10] = 0x00
        crcData[11] = 0x00

        // Zephyr 방식 CRC16 계산
        val calculatedCrc = zephyrCrc16(crcData)

        // 수신된 CRC와 계산된 CRC가 다르면 데이터 손상 가능성이 있음
        if (receivedCrc != calculatedCrc) {
            val logMsg = "CRC! Rcv:%04X Calc:%04X".format(receivedCrc, calculatedCrc)

            _state.update {
                it.copy(
                    crcErrorCount = it.crcErrorCount + 1,
                    damagedPacketCount = it.damagedPacketCount + 1,
                    lastLog = logMsg
                )
            }

            addPacketError(
                type = "CRC",
                message = logMsg
            )

            // 기존 Swift 코드처럼 CRC가 틀려도 데이터는 표시하도록 return 하지 않음
        } else {
            updateLog("CRC OK!")
        }

        /**
         * 6. IMU Data, Index 612~1211
         *
         * BMA400 IMU 센서 데이터.
         * 총 600 bytes.
         *
         * 보통 6 bytes 단위로 X/Y/Z축 16-bit 값이 들어있다고 가정하면
         * 100개의 샘플을 만들 수 있다.
         */
        val imuData = data.copyOfRange(612, 1212)

        /**
         * 파싱된 raw 값들을 SensorData 객체로 변환.
         *
         * SensorData 내부에서 ntcRaw를 섭씨 온도로 바꾸거나,
         * batteryRaw를 전압으로 바꾸는 계산을 할 수 있다.
         */
        val parsed = SensorData(
            timestamp = timestamp,
            ntcRaw = ntcRaw,
            batteryRaw = batteryRaw,
            imuData = imuData
        )

        // 마지막 파싱 결과를 상태에 반영해서 UI가 갱신되도록 한다.
        _state.update {
            it.copy(
                lastParsedData = parsed,
                parsedSuperFrameCount = it.parsedSuperFrameCount + 1
            )
        }
    }

    /**
     * Zephyr의 crc16_ccitt 방식과 맞춘 CRC16 계산 함수.
     *
     * 특징:
     * - 초기값: 0xFFFF
     * - LSB-first
     * - reflected 방식
     *
     * 펌웨어에서 계산한 CRC와 앱에서 계산한 CRC가 같아야
     * Super Frame이 정상적으로 수신되었다고 볼 수 있다.
     */
    private fun zephyrCrc16(data: ByteArray): Int {
        // CRC 초기값
        var crc = 0xFFFF

        // 모든 바이트를 순회하면서 CRC 갱신
        for (byte in data) {
            /**
             * e = seed ^ *src
             *
             * 현재 CRC의 하위 8비트와 입력 byte를 XOR한다.
             */
            val e = (crc and 0xFF) xor (byte.toInt() and 0xFF)

            /**
             * f = e ^ (e << 4)
             *
             * Zephyr CRC 구현에 맞춘 중간값 계산.
             */
            val f = e xor ((e shl 4) and 0xFF)

            /**
             * seed = (seed >> 8) ^ (f << 8) ^ (f << 3) ^ (f >> 4)
             *
             * 계산 결과는 16-bit 범위로 유지해야 하므로 마지막에 & 0xFFFF를 적용한다.
             */
            crc =
                ((crc shr 8) xor
                        ((f shl 8) and 0xFFFF) xor
                        ((f shl 3) and 0xFFFF) xor
                        (f shr 4)) and 0xFFFF
        }

        // 최종 CRC16 값 반환
        return crc
    }

    /**
     * lastLog만 간단히 갱신하는 보조 함수.
     *
     * 매번 _state.update { it.copy(lastLog = ...) }를 반복하지 않기 위해 사용한다.
     */
    private fun updateLog(message: String) {
        _state.update {
            it.copy(lastLog = message)
        }
    }

    private fun addPacketError(
        type: String,
        message: String,
        fragCounter: Int? = null
    ) {
        _state.update { current ->
            val updatedErrors = listOf(
                PacketErrorLog(
                    type = type,
                    message = message,
                    fragCounter = fragCounter
                )
            ) + current.recentPacketErrors

            current.copy(
                lastLog = message,
                recentPacketErrors = updatedErrors.take(8)
            )
        }
    }

    private fun counterDistance(expected: Int, actual: Int): Int {
        // counter는 12bit라서 0~4095 순환
        return (actual - expected) and 0x0FFF
    }

    /**
     * 테스트용 정상 Mini Packet 생성 함수.
     *
     * 실제 BLE에서 들어온 것처럼 204 bytes packet을 만든다.
     * - 앞 2 bytes: Mini Header
     * - 나머지 202 bytes: Payload
     */
    private fun makeDebugMiniPacket(
        counter: Int,
        prefix: Int = 0x5,
        payloadStartWithSuperHeader: Boolean = true
    ): ByteArray {
        val packet = ByteArray(fragmentSize) { 0x00 }

        val miniHeader = ((prefix and 0xF) shl 12) or (counter and 0x0FFF)

        packet[0] = ((miniHeader shr 8) and 0xFF).toByte()
        packet[1] = (miniHeader and 0xFF).toByte()

        if (payloadStartWithSuperHeader) {
            packet[2] = 0xAA.toByte()
            packet[3] = 0xAA.toByte()
        }

        return packet
    }

    /**
     * 1. 길이 오류 테스트.
     *
     * Mini Packet은 반드시 204 bytes여야 하는데,
     * 일부러 197 bytes짜리 packet을 넣어서 Length Drop이 뜨는지 확인한다.
     */
    fun debugTestLengthError() {
        val wrongLengthPacket = ByteArray(197) { 0x00 }
        processIncomingData(wrongLengthPacket)
    }

    /**
     * 2. Mini Header prefix 오류 테스트.
     *
     * Mini Header 상위 4bit는 반드시 0x5여야 한다.
     * 일부러 0x3으로 만들어 Header Prefix Drop이 뜨는지 확인한다.
     */
    fun debugTestMiniHeaderError() {
        val wrongHeaderPacket = makeDebugMiniPacket(
            counter = 1,
            prefix = 0x3,
            payloadStartWithSuperHeader = true
        )

        processIncomingData(wrongHeaderPacket)
    }

    /**
     * 3. Sequence 손실 테스트.
     *
     * 다음에 counter 10이 와야 하는 상황을 만든 뒤,
     * 실제로는 counter 12를 넣어서 10, 11번 packet이 손실된 것처럼 만든다.
     */
    fun debugTestSequenceLoss() {
        buffer.clear()
        expectedFragCounter = 10

        val skippedPacket = makeDebugMiniPacket(
            counter = 12,
            prefix = 0x5,
            payloadStartWithSuperHeader = true
        )

        processIncomingData(skippedPacket)
    }

    /**
     * 4. Super Header 오류 테스트.
     *
     * 새 Super Frame의 시작 payload는 0xAA 0xAA여야 한다.
     * 일부러 0xBB 0xBB로 시작하게 해서 Sync/Super Header 오류를 확인한다.
     */
    fun debugTestSuperHeaderError() {
        buffer.clear()
        expectedFragCounter = null

        val packet = makeDebugMiniPacket(
            counter = 20,
            prefix = 0x5,
            payloadStartWithSuperHeader = false
        )

        packet[2] = 0xBB.toByte()
        packet[3] = 0xBB.toByte()

        processIncomingData(packet)
    }

    /**
     * 5. CRC 오류 테스트.
     *
     * 1212 bytes짜리 Super Frame을 직접 만들고,
     * CRC 값을 일부러 틀리게 넣어서 CRC 오류가 뜨는지 확인한다.
     */
    fun debugTestCrcError() {
        buffer.clear()
        expectedFragCounter = null

        val superFrame = ByteArray(superFrameSize) { 0x00 }

        // Super Header
        superFrame[0] = 0xAA.toByte()
        superFrame[1] = 0xAA.toByte()

        // NTC raw 예시값
        superFrame[2] = 0x05
        superFrame[3] = 0xDC.toByte()

        // Timestamp 예시값, little endian
        superFrame[4] = 0x01
        superFrame[5] = 0x02
        superFrame[6] = 0x03
        superFrame[7] = 0x04

        // Battery raw 예시값
        superFrame[8] = 0x0A
        superFrame[9] = 0xAF.toByte()

        // CRC를 일부러 틀리게 넣음
        superFrame[10] = 0x12
        superFrame[11] = 0x34

        parseSuperFrame(superFrame)
    }

    /**
     * 6. Counter wrap-around 테스트.
     *
     * counter는 4095 다음에 0으로 돌아가야 한다.
     * 4095 → 0 순서가 정상으로 처리되는지 확인한다.
     */
    fun debugTestCounterWrapAround() {
        reset()

        val packet4095 = makeDebugMiniPacket(
            counter = 4095,
            prefix = 0x5,
            payloadStartWithSuperHeader = true
        )

        val packet0 = makeDebugMiniPacket(
            counter = 0,
            prefix = 0x5,
            payloadStartWithSuperHeader = false
        )

        processIncomingData(packet4095)
        processIncomingData(packet0)

        updateLog("DEBUG: Counter wrap-around test completed. 4095 → 0")
    }

}