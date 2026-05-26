package com.leejang.sleeptandard.Potch

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.leejang.sleeptandard.Potch.PotchBleForegroundService

class PotchBleViewModel(
    application: Application
) : AndroidViewModel(application) {

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
}