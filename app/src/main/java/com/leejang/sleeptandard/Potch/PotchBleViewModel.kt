package com.leejang.sleeptandard.Potch

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.leejang.sleeptandard.Potch.PotchBleForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PotchBleViewModel(
    application: Application
) : AndroidViewModel(application) {
    fun updateMicroMovementBandPass(
        lowCutHz: Double,
        highCutHz: Double
    ) {
        val context = getApplication<Application>().applicationContext

        val intent = Intent(context, PotchBleForegroundService::class.java).apply {
            action = PotchBleForegroundService.ACTION_UPDATE_MICRO_BPF
            putExtra(PotchBleForegroundService.EXTRA_MICRO_LOW_CUT, lowCutHz)
            putExtra(PotchBleForegroundService.EXTRA_MICRO_HIGH_CUT, highCutHz)
        }

        ContextCompat.startForegroundService(context, intent)
    }

    val bleState = PotchServiceStateHolder.bleState
    val processorState = PotchServiceStateHolder.processorState

    /** 가장 최근 수면 단계 추론 결과 — UI에서 collectAsState()로 관찰 */
    val sleepStage = PotchServiceStateHolder.sleepStage

    /**
     * ble(3).c 및 iOS 구현과 동일하게 0x01 명령을 Write Without Response로 전송한다.
     * 펌웨어에서는 이 Write를 받으면 trigger_led_flash()를 실행한다.
     */
    fun triggerLedFlash() {
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, PotchBleForegroundService::class.java).apply {
            action = PotchBleForegroundService.ACTION_TRIGGER_LED_FLASH
        }
        ContextCompat.startForegroundService(context, intent)
    }

    /**
     * Potch510 Data Characteristic으로 raw command payload를 보낸다.
     * 실제 opcode/payload 규칙은 최신 펌웨어 명령 명세가 확정된 뒤 호출부에서 구성한다.
     */
    fun writePotchCommand(
        payload: ByteArray,
        withoutResponse: Boolean = true
    ) {
        require(payload.isNotEmpty()) { "Potch command payload must not be empty" }

        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, PotchBleForegroundService::class.java).apply {
            action = PotchBleForegroundService.ACTION_WRITE_COMMAND
            putExtra(PotchBleForegroundService.EXTRA_COMMAND_PAYLOAD, payload)
            putExtra(
                PotchBleForegroundService.EXTRA_COMMAND_WITHOUT_RESPONSE,
                withoutResponse
            )
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun startScan() {
        val context = getApplication<Application>().applicationContext

        val intent = Intent(context, PotchBleForegroundService::class.java).apply {
            action = PotchBleForegroundService.ACTION_START
        }

        ContextCompat.startForegroundService(context, intent)
    }

    fun disconnect() {
        stopReconnectAndSaveLog()
    }

    fun stopReconnectAndSaveLog() {
        val context = getApplication<Application>().applicationContext

        val intent = Intent(context, PotchBleForegroundService::class.java).apply {
            action = PotchBleForegroundService.ACTION_STOP_AND_SAVE
        }

        ContextCompat.startForegroundService(context, intent)
    }

    fun resetProcessor() {
        PotchServiceStateHolder.reset()
    }

    fun debugTestLengthError() {
        // 서비스 구조에서는 일단 비워둠.
        // 디버그 테스트는 나중에 Service에 action으로 전달하는 방식으로 확장 가능.
    }

    fun debugTestMiniHeaderError() {}

    fun debugTestSequenceLoss() {}

    fun debugTestSuperHeaderError() {}

    fun debugTestCrcError() {}

    fun debugTestCounterWrapAround() {}

    private val _internalLogFiles = MutableStateFlow<List<InternalPotchLogFile>>(emptyList())
    val internalLogFiles: StateFlow<List<InternalPotchLogFile>> = _internalLogFiles

    private val _lastExportMessage = MutableStateFlow<String?>(null)
    val lastExportMessage: StateFlow<String?> = _lastExportMessage

    fun refreshInternalLogFiles() {
        val context = getApplication<Application>().applicationContext

        viewModelScope.launch {
            val files = withContext(Dispatchers.IO) {
                PotchDataLogger.listInternalLogFiles(context)
            }

            _internalLogFiles.value = files
        }
    }

    fun exportSelectedInternalLogFiles(fileNames: List<String>) {
        val context = getApplication<Application>().applicationContext

        viewModelScope.launch {
            _lastExportMessage.value = "파일 내보내는 중..."

            val exportedPaths = withContext(Dispatchers.IO) {
                PotchDataLogger.exportInternalLogFilesToDownloads(
                    context = context,
                    fileNames = fileNames
                )
            }

            _lastExportMessage.value =
                if (exportedPaths.isEmpty()) {
                    "내보낸 파일이 없습니다."
                } else {
                    "${exportedPaths.size}개 파일을 Download/PotchLogs로 내보냈습니다."
                }

            refreshInternalLogFiles()
        }
    }
    fun deleteInternalLogFiles(fileNames: List<String>) {
        val context = getApplication<Application>().applicationContext

        viewModelScope.launch {
            val isDeleteAll = fileNames.isEmpty()

            _lastExportMessage.value =
                if (isDeleteAll) {
                    "전체 로그 파일 삭제 중..."
                } else {
                    "선택한 로그 파일 삭제 중..."
                }

            val deletedCount = withContext(Dispatchers.IO) {
                val dir = File(context.filesDir, "PotchLogs")

                if (!dir.exists() || !dir.isDirectory) {
                    return@withContext 0
                }

                val targets = if (isDeleteAll) {
                    dir.listFiles()
                        ?.filter { file ->
                            file.isFile && isSupportedInternalLogFile(file)
                        }
                        ?: emptyList()
                } else {
                    fileNames.distinct().mapNotNull { fileName ->
                        val file = File(dir, fileName)

                        val isInsideLogDir = runCatching {
                            file.canonicalFile.parentFile == dir.canonicalFile
                        }.getOrDefault(false)

                        if (isInsideLogDir && file.exists() && file.isFile && isSupportedInternalLogFile(file)) {
                            file
                        } else {
                            null
                        }
                    }
                }

                targets.count { file ->
                    file.delete()
                }
            }

            _lastExportMessage.value =
                if (isDeleteAll) {
                    if (deletedCount == 0) {
                        "삭제할 내부 로그 파일이 없습니다."
                    } else {
                        "내부 로그 파일 ${deletedCount}개를 삭제했습니다."
                    }
                } else {
                    if (deletedCount == 0) {
                        "선택한 로그 파일 중 삭제된 파일이 없습니다."
                    } else {
                        "선택한 로그 파일 ${deletedCount}개를 삭제했습니다."
                    }
                }

            refreshInternalLogFiles()
        }
    }

    private fun isSupportedInternalLogFile(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext == "bin" || ext == "csv" || ext == "txt"
    }
}