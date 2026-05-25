package com.leejang.sleeptandard.Potch

import android.app.Application
import androidx.lifecycle.AndroidViewModel

/**
 * Potch BLE 통신과 데이터 파싱 상태를 화면에 연결해주는 ViewModel.
 *
 * 역할:
 * 1. PotchBleManager 생성 및 관리
 * 2. PotchDataProcessor 생성 및 관리
 * 3. BLE 연결 상태를 UI에 제공
 * 4. 센서 데이터 파싱 상태를 UI에 제공
 * 5. 화면에서 호출할 스캔/연결/해제/초기화 함수를 제공
 *
 * AndroidViewModel을 사용하는 이유:
 * - PotchBleManager가 BluetoothManager를 만들기 위해 Context가 필요함
 * - Activity Context 대신 Application Context를 사용하면 메모리 누수 위험이 적음
 */
class PotchBleViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val dataLogger = PotchDataLogger(application.applicationContext)

    /**
     * BLE로 수신한 raw byte 데이터를 실제 센서 데이터로 변환하는 파서.
     *
     * PotchBleManager가 characteristic notify로 받은 ByteArray를
     * 이 dataProcessor의 processIncomingData()로 넘긴다.
     *
     * 처리 결과:
     * - 마지막 센서 데이터
     * - CRC 오류 수
     * - Sequence 오류 수
     * - 마지막 로그
     *
     * 등이 dataProcessor.state에 저장된다.
     */
    val dataProcessor = PotchDataProcessor(
        dataLogger = dataLogger
    )

    /**
     * Potch BLE 연결을 실제로 담당하는 클래스.
     *
     * 역할:
     * - BLE 스캔
     * - Potch 기기 찾기
     * - GATT 연결
     * - MTU 요청
     * - Service / Characteristic 탐색
     * - Notify 구독
     * - 수신 데이터를 dataProcessor로 전달
     *
     * private인 이유:
     * - UI에서 BLEManager를 직접 만지지 않게 하고
     * - ViewModel의 함수(startScan, disconnect 등)를 통해서만 제어하게 하기 위함
     */
    private val bleManager = PotchBleManager(
        context = application.applicationContext,
        dataProcessor = dataProcessor,
        dataLogger = dataLogger
    )

    /**
     * BLE 연결 상태를 UI에서 관찰하기 위한 StateFlow.
     *
     * Compose 화면에서는 보통 이렇게 사용한다:
     *
     * val bleState by viewModel.bleState.collectAsState()
     *
     * 포함 정보:
     * - 스캔 중인지
     * - 연결됐는지
     * - 기기 이름
     * - MTU 값
     * - 마지막 BLE 로그
     * - 마지막 BLE 오류
     */
    val bleState = bleManager.state

    /**
     * 데이터 파싱 상태를 UI에서 관찰하기 위한 StateFlow.
     *
     * Compose 화면에서는 보통 이렇게 사용한다:
     *
     * val processorState by viewModel.processorState.collectAsState()
     *
     * 포함 정보:
     * - 마지막으로 파싱된 SensorData
     * - CRC 오류 누적 수
     * - Sequence 오류 누적 수
     * - 마지막 파싱 로그
     */
    val processorState = dataProcessor.state

    /**
     * Potch 기기를 찾기 위해 BLE 스캔을 시작한다.
     *
     * 화면에서 "스캔" 버튼을 눌렀을 때 호출하면 된다.
     *
     * 내부 흐름:
     * startScan()
     * → 주변 BLE 기기 검색
     * → 이름에 "Potch"가 포함된 기기 발견
     * → 자동으로 연결 시도
     */
    fun startScan() {
        bleManager.startScan()
    }

    /**
     * 현재 진행 중인 BLE 스캔을 중지한다.
     *
     * 이미 Potch를 찾았거나,
     * 사용자가 스캔을 취소하고 싶을 때 호출할 수 있다.
     */
    fun stopScan() {
        bleManager.stopScan()
    }

    /**
     * 이전에 발견된 Potch 기기에 다시 연결한다.
     *
     * 주의:
     * - targetDevice가 이미 저장되어 있어야 함
     * - 즉, 보통은 startScan()으로 한 번 기기를 찾은 뒤 사용할 수 있음
     *
     * 대부분의 경우 startScan()이 기기를 찾으면 자동으로 connect까지 하기 때문에
     * 수동 연결 버튼이 필요할 때만 사용하면 된다.
     */
    fun connect() {
        bleManager.connect()
    }

    /**
     * 현재 연결된 Potch 기기와 연결을 해제한다.
     *
     * 화면에서 "연결 해제" 버튼을 눌렀을 때 호출하면 된다.
     */
    fun disconnect() {
        bleManager.disconnect()
    }

    /**
     * 데이터 파서 상태를 초기화한다.
     *
     * 초기화되는 값:
     * - 마지막 센서 데이터
     * - CRC 오류 수
     * - Sequence 오류 수
     * - 마지막 로그
     * - 내부 fragment buffer
     * - expected fragment counter
     *
     * 개발자 화면에서 "수신 데이터 초기화" 버튼을 만들 때 사용하면 좋다.
     */
    fun resetProcessor() {
        dataProcessor.reset()
    }

    /**
     * ViewModel이 사라질 때 호출된다.
     *
     * 예:
     * - 화면이 완전히 종료됨
     * - ViewModelStore에서 제거됨
     *
     * 여기서 BLE 스캔과 GATT 연결을 정리하지 않으면
     * 백그라운드에서 스캔이나 연결이 남을 수 있다.
     */
    override fun onCleared() {
        super.onCleared()

        // BLE 스캔 중지 + 연결 해제 + GATT 자원 정리
        bleManager.close()
    }

    /**
     * 길이 오류 검증 버튼용 함수.
     */
    fun debugTestLengthError() {
        dataProcessor.debugTestLengthError()
    }

    /**
     * Mini Header prefix 오류 검증 버튼용 함수.
     */
    fun debugTestMiniHeaderError() {
        dataProcessor.debugTestMiniHeaderError()
    }

    /**
     * Sequence 손실 검증 버튼용 함수.
     */
    fun debugTestSequenceLoss() {
        dataProcessor.debugTestSequenceLoss()
    }

    /**
     * Super Header 오류 검증 버튼용 함수.
     */
    fun debugTestSuperHeaderError() {
        dataProcessor.debugTestSuperHeaderError()
    }

    /**
     * CRC 오류 검증 버튼용 함수.
     */
    fun debugTestCrcError() {
        dataProcessor.debugTestCrcError()
    }

    /**
     * Counter 4095 → 0 순환 검증 버튼용 함수.
     */
    fun debugTestCounterWrapAround() {
        dataProcessor.debugTestCounterWrapAround()
    }

    fun stopReconnectAndSaveLog() {
        bleManager.stopReconnectAndSaveLog()
    }
}