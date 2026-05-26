package com.leejang.sleeptandard.Potch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object PotchServiceStateHolder {
    private val _bleState = MutableStateFlow(PotchBleState())
    val bleState: StateFlow<PotchBleState> = _bleState

    private val _processorState = MutableStateFlow(DataProcessorState())
    val processorState: StateFlow<DataProcessorState> = _processorState

    fun updateBleState(state: PotchBleState) {
        _bleState.value = state
    }

    fun updateProcessorState(state: DataProcessorState) {
        _processorState.value = state
    }

    fun reset() {
        _bleState.value = PotchBleState()
        _processorState.value = DataProcessorState()
    }
}