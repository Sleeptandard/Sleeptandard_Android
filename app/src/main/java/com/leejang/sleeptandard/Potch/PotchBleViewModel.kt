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
}