package com.leejang.sleeptandard.Potch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object PotchServiceStateHolder {
    private val _bleState = MutableStateFlow(PotchBleState())
    val bleState: StateFlow<PotchBleState> = _bleState

    private val _processorState = MutableStateFlow(DataProcessorState())
    val processorState: StateFlow<DataProcessorState> = _processorState

    /** 가장 최근 수면 단계 추론 결과 */
    private val _sleepStage = MutableStateFlow(SleepStagePotch.UNKNOWN)
    val sleepStage: StateFlow<SleepStagePotch> = _sleepStage

    fun updateBleState(state: PotchBleState) {
        _bleState.value = state
    }

    fun updateProcessorState(state: DataProcessorState) {
        _processorState.value = state
    }

    fun updateSleepStage(stage: SleepStagePotch) {
        _sleepStage.value = stage
    }

    fun reset() {
        _bleState.value = PotchBleState()
        _processorState.value = DataProcessorState()
        _sleepStage.value = SleepStagePotch.UNKNOWN
    }
}