package com.leejang.sleeptandard.Potch

import com.leejang.sleeptandard.Potch.SensorData
import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * PotchDataProcessor가 현재까지 처리한 데이터 상태를 담는 데이터 클래스.
 *
 * BLE 통신으로 들어온 raw byte를 파싱한 결과와,
 * 패킷 오류 정보, 마지막 로그 등을 UI에서 볼 수 있게 저장한다.
 */
data class DataProcessorState(
    // 마지막으로 정상 파싱된 센서 데이터
    // 아직 수신된 데이터가 없거나 파싱 전이면 null
    val lastParsedData: SensorData? = null,

    // CRC 검증 실패 횟수
    // 패킷 데이터가 손상되었을 가능성을 확인하기 위한 누적 카운트
    val packetLossCount: Int = 0,

    // Fragment 순서가 예상과 다르게 들어온 횟수
    // BLE notification 누락 또는 순서 꼬임을 감지하기 위한 누적 카운트
    val missingSequenceErrors: Int = 0,

    // 마지막 처리 상태를 사람이 읽을 수 있게 저장하는 로그 메시지
    // 예: "CRC OK!", "Length Drop", "Seq Drop" 등
    val lastLog: String = "No data yet"
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
        // 마지막 수신 길이를 로그로 기록
        updateLog("Rcv length: ${data.size}")

        // Potch protocol상 fragment는 반드시 204 bytes여야 한다.
        // 길이가 다르면 잘못된 notification으로 보고 버린다.
        if (data.size != fragmentSize) {
            updateLog("Length Drop: expected $fragmentSize, got ${data.size}")
            return
        }

        /**
         * 1. Mini Header Parsing
         *
         * data[0], data[1] 두 바이트가 mini header.
         * Swift 코드와 동일하게 big endian으로 해석한다.
         *
         * 예:
         * data[0] = 0x50
         * data[1] = 0x01
         * miniHeader = 0x5001
         */
        val miniHeader =
            ((data[0].toInt() and 0xFF) shl 8) or
                    (data[1].toInt() and 0xFF)

        /**
         * miniHeader 상위 4비트.
         *
         * Potch protocol에서는 이 값이 0x5여야 정상 fragment로 판단한다.
         */
        val headerPrefix = (miniHeader shr 12) and 0xF

        // headerPrefix가 0x5가 아니면 잘못된 fragment이므로 buffer를 비우고 중단
        if (headerPrefix != 0x5) {
            updateLog("Header Prefix Drop: $headerPrefix")
            buffer.clear()
            return
        }

        /**
         * miniHeader 하위 12비트.
         *
         * Fragment 순서를 나타내는 counter 값이다.
         * 0x000 ~ 0xFFF 범위를 순환한다.
         */
        val fragCounter = miniHeader and 0x0FFF

        /**
         * 2. Sequence Validation
         *
         * 이전 fragment를 기준으로 다음에 와야 할 counter와
         * 실제 들어온 counter를 비교한다.
         */
        expectedFragCounter?.let { expected ->
            if (fragCounter != expected) {
                // 예상한 counter와 다르면 fragment가 누락되었거나 순서가 꼬인 것
                _state.update {
                    it.copy(
                        missingSequenceErrors = it.missingSequenceErrors + 1,
                        lastLog = "Seq Drop. Exp: $expected, Got: $fragCounter"
                    )
                }

                // 순서가 깨졌으므로 지금까지 모아둔 payload는 신뢰할 수 없어 비운다.
                buffer.clear()
            }
        }

        /**
         * 다음 fragment에서 기대할 counter 값 갱신.
         *
         * 12-bit counter이므로 0xFFF 다음에는 0x000으로 돌아가야 한다.
         * 그래서 & 0x0FFF를 적용한다.
         */
        expectedFragCounter = (fragCounter + 1) and 0x0FFF

        /**
         * 3. Append Payload
         *
         * 앞의 2 bytes는 mini header이므로 제외하고,
         * 나머지 202 bytes만 실제 Super Frame payload로 사용한다.
         */
        val payload = data.copyOfRange(2, data.size)

        /**
         * Super Frame 동기화 확인.
         *
         * buffer가 비어 있다는 것은 새 Super Frame의 시작을 기다리는 상태다.
         * 이때 첫 payload의 시작이 0xAA 0xAA여야 정상 프레임 시작으로 인정한다.
         */
        if (buffer.isEmpty()) {
            if (
                payload.size < 2 ||
                (payload[0].toInt() and 0xFF) != 0xAA ||
                (payload[1].toInt() and 0xFF) != 0xAA
            ) {
                // 아직 Super Frame 시작점을 못 찾은 상태
                updateLog("Syncing... waiting for start of frame")
                return
            }
        }

        // 정상 payload라면 buffer 뒤에 누적한다.
        payload.forEach { buffer.addLast(it) }

        /**
         * 4. If buffer is full, parse the Super Frame
         *
         * buffer에 1212 bytes 이상 쌓이면 하나의 Super Frame이 완성된 것이다.
         */
        if (buffer.size >= superFrameSize) {
            // buffer 앞에서부터 1212 bytes를 꺼내 Super Frame으로 만든다.
            val superFrame = ByteArray(superFrameSize) {
                buffer.removeFirst()
            }

            // 완성된 Super Frame 파싱
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
            updateLog(
                "Super Header Drop: 0x%02X%02X".format(
                    data[0].toInt() and 0xFF,
                    data[1].toInt() and 0xFF
                )
            )

            // 프레임 시작이 잘못되었으므로 동기화를 다시 맞추기 위해 buffer를 비운다.
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
                    packetLossCount = it.packetLossCount + 1,
                    lastLog = logMsg
                )
            }

            // Swift 코드처럼 CRC가 틀려도 일단 데이터는 표시하도록 return 하지 않음
            // 안정성을 우선하려면 여기에서 return 하도록 바꿀 수도 있음.
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
            it.copy(lastParsedData = parsed)
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
}