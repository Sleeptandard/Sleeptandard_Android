package com.leejang.sleeptandard.Potch

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Potch BLE 연결 상태를 UI에 전달하기 위한 상태 데이터 클래스.
 *
 * Compose 화면에서는 이 값을 collectAsState()로 관찰해서
 * "스캔 중", "연결됨", "오류 메시지", "MTU 값" 등을 표시할 수 있다.
 */
data class PotchBleState(
    // 현재 BLE 스캔을 진행 중인지 여부
    val isScanning: Boolean = false,

    // Potch 기기와 GATT 연결이 완료되었는지 여부
    val isConnected: Boolean = false,

    // 연결된 BLE 기기의 이름
    val deviceName: String? = null,

    // 스마트폰 블루투스가 켜져 있는지 여부
    val bluetoothEnabled: Boolean = false,

    // 현재 BLE 통신에서 사용 중인 MTU 크기
    // 기본값은 Android BLE 기본 MTU인 23
    val mtu: Int = 23,

    // 마지막으로 발생한 오류 메시지
    val lastError: String? = null,

    // BLE 동작 상황을 표시하기 위한 마지막 로그 메시지
    val lastLog: String = "BLE idle",

    // 마지막으로 저장된 로그 파일 경로
    val lastSavedLogPath: String? = null
)

/**
 * Potch 전용 BLE 통신을 담당하는 클래스.
 *
 * 주요 역할:
 * 1. Potch 기기 스캔
 * 2. Potch 기기 연결
 * 3. Service / Characteristic 탐색
 * 4. Notify 구독
 * 5. 수신된 ByteArray 데이터를 PotchDataProcessor로 전달
 */
class PotchBleManager(
    context: Context,

    // BLE characteristic으로 들어온 raw byte 데이터를 실제 센서 데이터로 파싱하는 클래스
    private val dataProcessor: PotchDataProcessor,
    private val dataLogger: PotchDataLogger
) {
    companion object {
        /**
         * Potch 펌웨어에 정의된 BLE Service UUID.
         *
         * Android 앱은 연결 후 이 UUID를 가진 Service를 찾아야 한다.
         */
        val SERVICE_UUID: UUID =
            UUID.fromString("00001234-0000-1000-8000-00805F9B34FB")

        /**
         * Potch 펌웨어에 정의된 BLE Characteristic UUID.
         *
         * 실제 센서 데이터가 notify로 들어오는 통로다.
         */
        val CHAR_UUID: UUID =
            UUID.fromString("00005678-0000-1000-8000-00805F9B34FB")

        /**
         * CCCD(Client Characteristic Configuration Descriptor) UUID.
         *
         * BLE에서 notify/indicate를 활성화할 때 거의 고정적으로 사용하는 descriptor.
         * 이 descriptor에 ENABLE_NOTIFICATION_VALUE를 써야 실제 알림 수신이 시작된다.
         */
        private val CCCD_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

        /**
         * 찾고 싶은 BLE 기기의 이름.
         *
         * 스캔 결과에서 device name 또는 advertised name에 "Potch"가 포함되어 있으면
         * 타겟 기기로 판단한다.
         */
        private const val TARGET_NAME = "Potch"
        /**
         * 요청할 MTU 크기.
         *
         * Potch는 한 번에 204 bytes notification을 보내므로,
         * Android 기본 MTU 23으로는 부족하다.
         *
         * 247 정도로 요청하면 ATT payload를 충분히 크게 받을 수 있다.
         */
        private const val TARGET_MTU = 247
    }

    // 메인 스레드에서 재연결 딜레이 작업을 실행하기 위한 Handler
    private val reconnectHandler = Handler(Looper.getMainLooper())

    // 사용자가 직접 연결 해제를 눌렀는지 구분하는 플래그
    private var manualDisconnect = false

    // 현재 재연결 시도 중인지 여부
    private var isReconnecting = false

    // 재연결 시도 횟수
    private var reconnectAttempt = 0

    // 최대 재연결 시도 횟수
    private val maxReconnectAttempts = 5

    // 재연결 시도 간격
    private val reconnectDelayMs = 1000L

    /**
     * Activity context 대신 applicationContext를 저장한다.
     *
     * BLE 연결은 화면 수명보다 오래 유지될 수 있으므로,
     * Activity context를 잡고 있으면 메모리 누수 위험이 있다.
     */
    private val appContext = context.applicationContext



    /**
     * Android 시스템의 BluetoothManager.
     *
     * BluetoothAdapter를 얻기 위한 진입점이다.
     */
    private val bluetoothManager: BluetoothManager? =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    /**
     * 스마트폰의 BluetoothAdapter.
     *
     * 블루투스가 지원되는지, 켜져 있는지 확인하고
     * BLE scanner를 얻기 위해 사용한다.
     */
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    /**
     * BLE 스캔을 실행하는 객체.
     *
     * bluetoothAdapter가 null이거나 블루투스가 꺼져 있으면 scanner도 사용할 수 없다.
     */
    private val scanner
        get() = bluetoothAdapter?.bluetoothLeScanner

    /**
     * 현재 연결 중인 BluetoothGatt 객체.
     *
     * BLE 연결 이후 Service 탐색, Characteristic 구독,
     * 연결 해제 등에 사용된다.
     */
    private var gatt: BluetoothGatt? = null

    /**
     * 스캔으로 발견한 Potch 기기.
     *
     * connect()를 호출할 때 이 기기에 연결한다.
     */
    private var targetDevice: BluetoothDevice? = null

    /**
     * notify를 구독할 Characteristic.
     *
     * Service 탐색 후 CHAR_UUID에 해당하는 characteristic을 저장한다.
     */
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null

    /**
     * 내부에서 변경 가능한 BLE 상태.
     *
     * UI에는 아래의 state만 공개하고,
     * 이 _state는 PotchBleManager 내부에서만 수정한다.
     */
    private val _state = MutableStateFlow(PotchBleState())

    /**
     * UI에서 관찰할 BLE 상태.
     *
     * Compose 화면에서 collectAsState()로 받아서 사용할 수 있다.
     */
    val state: StateFlow<PotchBleState> = _state

    /**
     * BLE 스캔 결과를 처리하는 콜백.
     *
     * startScan()을 호출하면 Android 시스템이 주변 BLE 기기를 찾고,
     * 발견된 기기마다 onScanResult()가 호출된다.
     */
    private val scanCallback = object : ScanCallback() {

        /**
         * BLE 기기가 하나 발견될 때마다 호출된다.
         */
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            // 발견된 BLE 기기 객체
            val device = result.device

            // 광고 패킷에 포함된 기기 이름
            val advertisedName = result.scanRecord?.deviceName

            // BluetoothDevice에서 얻는 기기 이름
            // 권한이 없으면 SecurityException이 날 수 있어서 try-catch 처리
            val deviceName = try {
                device.name
            } catch (_: SecurityException) {
                null
            }

            // device.name이 있으면 우선 사용하고,
            // 없으면 advertisement name,
            // 그것도 없으면 "Unknown"으로 처리한다.
            val name = deviceName ?: advertisedName ?: "Unknown"

            // 이름에 "Potch"가 포함된 기기를 찾으면 타겟으로 판단한다.
            if (name.contains(TARGET_NAME, ignoreCase = true)) {
                log("Found target peripheral: $name")

                // 나중에 수동 connect()를 할 수 있도록 발견한 기기를 저장한다.
                targetDevice = device

                // 타겟을 찾았으므로 스캔을 멈춘다.
                stopScan()

                // 발견한 기기에 바로 연결한다.
                connect(device)
            }
        }

        /**
         * BLE 스캔 자체가 실패했을 때 호출된다.
         */
        override fun onScanFailed(errorCode: Int) {
            error("Scan failed: $errorCode")
        }
    }

    /**
     * GATT 연결 이후 발생하는 이벤트들을 처리하는 콜백.
     *
     * 연결 성공/실패, MTU 변경, 서비스 발견,
     * characteristic notify 수신 등을 담당한다.
     */
    private val gattCallback = object : BluetoothGattCallback() {

        /**
         * BLE 기기와의 연결 상태가 바뀔 때 호출된다.
         *
         * 예:
         * - 연결 성공
         * - 연결 해제
         * - 연결 오류
         */
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            // status가 GATT_SUCCESS가 아니면 연결 과정에서 오류가 발생한 것
            if (status != BluetoothGatt.GATT_SUCCESS) {
                error("GATT connection error: status=$status")

                // 문제가 생긴 GATT 자원을 정리한다.
                closeGatt()

                // 다시 스캔해서 재연결을 시도한다.
                startScan()
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    // 연결된 기기 이름을 가져온다.
                    val name = getDeviceName(gatt.device)

                    manualDisconnect = false
                    isReconnecting = false
                    reconnectAttempt = 0
                    reconnectHandler.removeCallbacksAndMessages(null)

                    // 팟치 연결 성공 시 raw data 로깅 시작
                    dataLogger.start()

                    // UI 상태를 "연결됨"으로 갱신한다.
                    _state.update {
                        it.copy(
                            isConnected = true,
                            isScanning = false,
                            deviceName = name,
                            lastError = null,
                            lastLog = "Connected to ${name ?: TARGET_NAME}"
                        )
                    }

                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)

                    // Potch는 204 bytes notification을 보내므로 기본 MTU 23으로는 부족하다.
                    // 먼저 MTU를 247로 요청하고, MTU 변경이 완료되면 서비스 탐색을 시작한다.
                    val requested = gatt.requestMtu(TARGET_MTU)

                    // MTU 요청 자체가 실패하면 그냥 바로 서비스 탐색을 진행한다.
                    if (!requested) {
                        log("MTU request failed. Discover services directly.")
                        gatt.discoverServices()
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    // 연결이 끊어진 경우
                    log("Disconnected. Reconnecting...")

                    val name = getDeviceName(gatt.device)

                    // UI 상태를 "연결 안 됨"으로 갱신한다.
                    _state.update {
                        it.copy(
                            isConnected = false,
                            deviceName = name,
                            lastLog = if (manualDisconnect) {
                                "Disconnected by user"
                            } else {
                                "Disconnected unexpectedly. Reconnecting soon..."
                            }
                        )
                    }

                    // 기존 GATT 자원 정리
                    closeGatt()

                    if (!manualDisconnect) {
                        scheduleReconnect()
                    }
                }
            }
        }

        /**
         * requestMtu() 요청 결과가 도착했을 때 호출된다.
         *
         * MTU 변경 성공/실패 여부와 관계없이 이후 Service discovery를 시작한다.
         */
        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            // 현재 MTU 값을 상태에 저장해서 UI에서 확인할 수 있게 한다.
            _state.update {
                it.copy(
                    mtu = mtu,
                    lastLog = "MTU changed: $mtu, status=$status"
                )
            }

            // MTU 설정 후 Potch Service를 찾기 위해 서비스 탐색 시작
            gatt.discoverServices()
        }

        /**
         * BLE 기기의 Service 탐색이 끝났을 때 호출된다.
         */
        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            // 서비스 탐색 실패 처리
            if (status != BluetoothGatt.GATT_SUCCESS) {
                error("Service discovery failed: $status")
                return
            }

            // Potch 펌웨어에 정의된 Service UUID를 찾는다.
            val service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                error("Service not found: $SERVICE_UUID")
                return
            }

            // 해당 Service 안에서 센서 데이터 notify용 Characteristic을 찾는다.
            val characteristic = service.getCharacteristic(CHAR_UUID)
            if (characteristic == null) {
                error("Characteristic not found: $CHAR_UUID")
                return
            }

            // 나중에 참조할 수 있도록 저장
            notifyCharacteristic = characteristic

            // 이 characteristic의 notify를 활성화한다.
            enableNotification(gatt, characteristic)
        }

        /**
         * CCCD descriptor write 결과가 도착했을 때 호출된다.
         *
         * notify 활성화를 위해 descriptor에 값을 쓰면 이 함수가 호출된다.
         */
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            // 우리가 쓴 descriptor가 CCCD인지 확인
            if (descriptor.uuid == CCCD_UUID) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    // notify 구독 성공
                    log("Subscribed to characteristic: $CHAR_UUID")
                } else {
                    // notify 구독 실패
                    error("CCCD write failed: $status")
                }
            }
        }

        /**
         * Android 12 이하 방식의 characteristic notify 수신 콜백.
         *
         * Android 13부터는 아래의 value 파라미터가 있는 오버로드가 권장된다.
         */
        @Deprecated("Deprecated in Android 13, kept for lower API levels")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            // 우리가 구독한 Potch characteristic에서 온 데이터인지 확인
            if (characteristic.uuid == CHAR_UUID) {
                // 수신된 raw byte 데이터
                val value = characteristic.value ?: return


                // 실제 파싱은 PotchDataProcessor에게 맡긴다.
                dataProcessor.processIncomingData(value)
            }
        }

        /**
         * Android 13 이상 방식의 characteristic notify 수신 콜백.
         *
         * Potch 기기에서 센서 데이터가 notify로 들어올 때마다 호출된다.
         */
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            // 우리가 구독한 Potch characteristic에서 온 데이터인지 확인
            if (characteristic.uuid == CHAR_UUID) {


                // 수신된 raw byte 데이터를 파서로 전달
                dataProcessor.processIncomingData(value)
            }
        }
    }

    /**
     * Potch 기기를 찾기 위해 BLE 스캔을 시작한다.
     *
     * 진행 순서:
     * 1. BluetoothAdapter 존재 확인
     * 2. 블루투스 ON 여부 확인
     * 3. BLE 권한 확인
     * 4. 상태 업데이트
     * 5. scanner.startScan()
     */
    @SuppressLint("MissingPermission")
    fun startScan() {
        manualDisconnect = false

        val adapter = bluetoothAdapter

        // 블루투스 기능이 없는 기기이거나 adapter를 얻지 못한 경우
        if (adapter == null) {
            error("Bluetooth adapter not available")
            return
        }

        // 스마트폰 블루투스가 꺼져 있는 경우
        if (!adapter.isEnabled) {
            _state.update {
                it.copy(
                    bluetoothEnabled = false,
                    lastError = "Bluetooth is off",
                    lastLog = "Bluetooth is off"
                )
            }
            return
        }

        // Android 버전에 맞는 BLE 권한이 없는 경우
        if (!hasBlePermissions()) {
            error("Missing Bluetooth permissions")
            return
        }

        // UI 상태를 스캔 중으로 변경
        _state.update {
            it.copy(
                bluetoothEnabled = true,
                isScanning = true,
                lastError = null,
                lastLog = "Started scanning for Potch..."
            )
        }

        // 실제 BLE 스캔 시작
        scanner?.startScan(scanCallback)
    }

    /**
     * 현재 진행 중인 BLE 스캔을 중단한다.
     */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        // 권한이 없으면 stopScan 호출도 보안 예외가 날 수 있으므로 중단
        if (!hasBlePermissions()) return

        // 실제 스캔 중지
        scanner?.stopScan(scanCallback)

        // UI 상태를 스캔 중 아님으로 변경
        _state.update {
            it.copy(isScanning = false)
        }
    }

    /**
     * 이전에 발견해둔 targetDevice에 연결한다.
     *
     * startScan()으로 Potch를 발견하면 targetDevice가 저장된다.
     * 아직 발견된 기기가 없다면 연결할 수 없다.
     */
    @SuppressLint("MissingPermission")
    fun connect() {
        val device = targetDevice

        // 스캔으로 발견한 Potch 기기가 아직 없는 경우
        if (device == null) {
            error("No target device. Start scan first.")
            return
        }

        // 실제 연결 함수 호출
        connect(device)
    }

    /**
     * 특정 BluetoothDevice에 GATT 연결을 시작한다.
     *
     * private 함수인 이유:
     * 외부에서는 connect()만 호출하게 하고,
     * 실제 device 객체를 이용한 연결은 내부에서만 처리하기 위함.
     */
    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice) {
        // 연결에도 BLUETOOTH_CONNECT 권한이 필요하다.
        if (!hasBlePermissions()) {
            error("Missing Bluetooth permissions")
            return
        }

        log("Connecting to ${getDeviceName(device) ?: TARGET_NAME}...")

        // Android M 이상에서는 TRANSPORT_LE를 명시해 BLE 연결임을 알려준다.
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(appContext, false, gattCallback)
        }
    }

    /**
     * 현재 연결된 Potch 기기와 연결을 해제한다.
     */
    @SuppressLint("MissingPermission")
    fun disconnect() {
        if (!hasBlePermissions()) return

        val savedPath = dataLogger.stopAndSave()

        manualDisconnect = true
        reconnectHandler.removeCallbacksAndMessages(null)
        isReconnecting = false
        reconnectAttempt = 0

        gatt?.disconnect()
        closeGatt()

        _state.update {
            it.copy(
                isConnected = false,
                deviceName = null,
                lastSavedLogPath = savedPath,
                lastLog = if (savedPath != null) {
                    "Disconnected. Super frame log saved: $savedPath"
                } else {
                    "Disconnected. No super frame log data to save."
                }
            )
        }
    }

    /**
     * PotchBleManager를 더 이상 사용하지 않을 때 호출한다.
     *
     * ViewModel의 onCleared() 같은 곳에서 호출하면 좋다.
     * 스캔 중지 + 연결 해제를 모두 수행한다.
     */
    @SuppressLint("MissingPermission")
    fun close() {
        manualDisconnect = true
        reconnectHandler.removeCallbacksAndMessages(null)
        stopScan()
        disconnect()
    }

    /**
     * Potch characteristic의 notify를 활성화한다.
     *
     * Android BLE에서 notify를 받으려면 두 단계가 필요하다:
     * 1. gatt.setCharacteristicNotification(characteristic, true)
     * 2. CCCD descriptor에 ENABLE_NOTIFICATION_VALUE 쓰기
     */
    @SuppressLint("MissingPermission")
    private fun enableNotification(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        // Android 로컬 쪽에서 해당 characteristic notify를 받을 준비를 한다.
        val notificationSet = gatt.setCharacteristicNotification(characteristic, true)
        if (!notificationSet) {
            error("setCharacteristicNotification failed")
            return
        }

        // notify 활성화 값을 쓸 CCCD descriptor를 찾는다.
        val descriptor = characteristic.getDescriptor(CCCD_UUID)
        if (descriptor == null) {
            error("CCCD descriptor not found")
            return
        }

        // Android 13 이상에서는 writeDescriptor(descriptor, value) 형태 사용
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(
                descriptor,
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            )
        } else {
            // Android 12 이하에서는 descriptor.value에 먼저 값을 넣고 writeDescriptor 호출
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    /**
     * 현재 GATT 연결 객체를 닫고 관련 참조를 정리한다.
     *
     * disconnect만 호출하면 내부 자원이 남을 수 있으므로,
     * close까지 호출해서 정리하는 것이 좋다.
     */
    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        try {
            gatt?.close()
        } catch (_: Exception) {
            // close 중 예외가 나도 앱이 죽지 않게 무시
        } finally {
            // GATT와 characteristic 참조 제거
            gatt = null
            notifyCharacteristic = null
        }
    }

    /**
     * 현재 Android 버전에 맞는 BLE 권한이 있는지 확인한다.
     *
     * Android 12 이상:
     * - BLUETOOTH_SCAN
     * - BLUETOOTH_CONNECT
     *
     * Android 11 이하:
     * - ACCESS_FINE_LOCATION
     * - ACCESS_COARSE_LOCATION
     *
     * Android 11 이하에서는 BLE 스캔이 위치 권한과 연결되어 있다.
     */
    private fun hasBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED &&
                    appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED &&
                    appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * BluetoothDevice의 이름을 안전하게 가져온다.
     *
     * Android 12 이상에서는 기기 이름 접근에도 BLUETOOTH_CONNECT 권한이 필요할 수 있어서
     * SecurityException을 방지한다.
     */
    @SuppressLint("MissingPermission")
    private fun getDeviceName(device: BluetoothDevice): String? {
        return try {
            device.name
        } catch (_: SecurityException) {
            null
        }
    }

    /**
     * 일반 로그 메시지를 상태에 반영한다.
     *
     * lastError는 null로 초기화해서
     * 이전 오류가 계속 표시되지 않게 한다.
     */
    private fun log(message: String) {
        _state.update {
            it.copy(lastLog = message, lastError = null)
        }
    }

    /**
     * 오류 메시지를 상태에 반영한다.
     *
     * UI에서는 lastError를 보고 오류 색상으로 표시할 수 있다.
     */
    private fun error(message: String) {
        _state.update {
            it.copy(lastError = message, lastLog = message)
        }
    }


    @SuppressLint("MissingPermission")
    private fun scheduleReconnect() {
        if (!hasBlePermissions()) {
            error("Missing Bluetooth permissions")
            return
        }

        val device = targetDevice

        if (device == null) {
            log("No target device for reconnect. Start scanning...")
            startScan()
            return
        }

        if (isReconnecting) return

        isReconnecting = true

        reconnectHandler.postDelayed({
            if (manualDisconnect) {
                isReconnecting = false
                return@postDelayed
            }

            reconnectAttempt++

            _state.update {
                it.copy(
                    lastLog = "Reconnecting to ${getDeviceName(device) ?: TARGET_NAME}... ($reconnectAttempt/$maxReconnectAttempts)"
                )
            }

            closeGatt()

            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(appContext, false, gattCallback)
            }

            isReconnecting = false

            if (reconnectAttempt >= maxReconnectAttempts) {
                reconnectAttempt = 0
                log("Reconnect attempts exceeded. Start scanning again...")
                startScan()
            }
        }, reconnectDelayMs)
    }
}